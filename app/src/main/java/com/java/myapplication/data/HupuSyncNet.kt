package com.java.myapplication.data

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

/** 局域网内发现到的对端设备 */
data class DiscoveredDevice(val ip: String, val port: Int, val name: String)

/**
 * 局域网数据同步的网络层：**裸 TCP**（4 字节大端长度 + UTF-8 JSON）+ **UDP 广播发现**。
 *
 * 为什么不用 HTTP：`network_security_config` 全局禁止明文，而它只约束平台的 HTTP 栈
 * （OkHttp / HttpURLConnection / WebView）；裸 `Socket` / `ServerSocket` 不受该策略限制。
 * 因此走裸 TCP 既无需放宽安全配置、也不引入任何第三方依赖。
 */
internal object HupuSyncNet {

    /** TCP 起始端口；被占用则依次 +1 重试 */
    const val PORT_BASE = 8765
    private const val PORT_TRIES = 11

    /** UDP 发现端口：固定值，与 TCP 实际端口解耦（TCP 顺延后仍能被发现） */
    const val UDP_PORT = 8765

    /** 单帧上限 8MB：防异常长度导致 OOM */
    private const val MAX_FRAME = 8 * 1024 * 1024
    private const val UDP_DISCOVER = "HZSYNC?1"
    private const val UDP_HERE = "HZSYNC!1"

    private var appContext: Context? = null

    /** MainActivity 启动时注入（获取 MulticastLock 用） */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * 1.2xx：部分国产 ROM（实测 ColorOS）会过滤入站广播包 —— 表现为
     * 「本机能扫描到别人，却收不到别人的广播、连自己的广播回环都收不到」。
     * 持有 WifiManager.MulticastLock 是标准解法。缺少权限 / WiFi 关闭时静默降级
     * （此时仍可靠「单播扫描」这条兜底路径发现设备）。
     */
    private fun acquireMulticastLock(): WifiManager.MulticastLock? {
        val ctx = appContext ?: return null
        return runCatching {
            val wm = ctx.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wm.createMulticastLock("hupu-sync").apply {
                setReferenceCounted(false)
                acquire()
            }
        }.getOrNull()
    }

    private fun releaseMulticastLock(lock: WifiManager.MulticastLock?) {
        if (lock == null) return
        runCatching { if (lock.isHeld) lock.release() }
    }

    fun deviceName(): String {
        val m = Build.MODEL
        return if (m.isNullOrBlank()) "Android设备" else m
    }

    /** 本机局域网 IPv4（wlan / eth 优先），可能为空（未连 WiFi） */
    fun localIpv4(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && it.name != null }
            .sortedByDescending { it.name.startsWith("wlan") || it.name.startsWith("eth") }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .mapNotNull { it.hostAddress }
            .filter { it.isNotEmpty() && !it.startsWith("127.") }
            .distinct()
    }.getOrDefault(emptyList())

    fun localIpv4Primary(): String = localIpv4().firstOrNull() ?: ""

    // ---------------- 帧编解码（长度前缀，大端） ----------------

    private fun writeFrame(out: DataOutputStream, text: String) {
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        out.writeInt(bytes.size)
        out.write(bytes)
        out.flush()
    }

    private fun readFrame(input: DataInputStream): String {
        val len = input.readInt()
        if (len <= 0 || len > MAX_FRAME) throw IllegalStateException("数据长度非法：$len")
        val buf = ByteArray(len)
        input.readFully(buf)
        return String(buf, StandardCharsets.UTF_8)
    }

    // ---------------- 发送 ----------------

    /**
     * 发送一次载荷并阻塞等待回执（返回回执 JSON 文本）。
     * 连接 8s 超时；等待对方确认用 [timeoutMs]（默认 90s，足够对方看清确认弹窗）。
     */
    suspend fun send(host: String, port: Int, payload: String, timeoutMs: Int = 90_000): String =
        withContext(Dispatchers.IO) {
            val sock = Socket()
            try {
                sock.connect(InetSocketAddress(host, port), 8000)
                sock.soTimeout = timeoutMs
                writeFrame(DataOutputStream(BufferedOutputStream(sock.getOutputStream())), payload)
                readFrame(DataInputStream(BufferedInputStream(sock.getInputStream())))
            } finally {
                runCatching { sock.close() }
            }
        }

    // ---------------- 接收 ----------------

    /**
     * 接收端：绑定 TCP 端口（顺延）+ UDP 发现端口，循环接受同步请求。
     * [onPayload] 在 IO 协程内挂起等待 UI 决策，返回值即回执 JSON；
     * 回调（onStarted / onError）统一切回主线程，调用方可直接改 Compose 状态。
     */
    class Receiver(
        private val scope: CoroutineScope,
        private val onPayload: suspend (String) -> String,
        private val onStarted: (port: Int) -> Unit,
        private val onError: (String) -> Unit,
    ) {
        private var server: ServerSocket? = null
        private var udp: DatagramSocket? = null
        private var acceptJob: Job? = null
        private var udpJob: Job? = null
        private var multicastLock: WifiManager.MulticastLock? = null
        private val running = AtomicBoolean(false)

        val isRunning: Boolean get() = running.get()

        fun start() {
            if (running.getAndSet(true)) return
            scope.launch(Dispatchers.IO) {
                val ss = (0 until PORT_TRIES).firstNotNullOfOrNull { i ->
                    runCatching { ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(PORT_BASE + i)) } }
                        .getOrNull()
                }
                if (ss == null) {
                    running.set(false)
                    scope.launch(Dispatchers.Main) { onError("端口被占用，无法开启接收") }
                    return@launch
                }
                server = ss
                // 1.2xx：监听期间持有 MulticastLock —— 否则部分 ROM 收不到对方的广播探测，
                // 表现为「对方扫描不到本机」
                multicastLock = acquireMulticastLock()
                scope.launch(Dispatchers.Main) { onStarted(ss.localPort) }
                startUdp()
                acceptJob = scope.launch(Dispatchers.IO) {
                    while (running.get() && !ss.isClosed) {
                        val sock = try {
                            ss.accept()
                        } catch (e: Exception) {
                            break
                        }
                        // 每条连接独立处理（不占用 accept 循环），可并发等待 UI 决策
                        scope.launch(Dispatchers.IO) { handle(sock) }
                    }
                }
            }
        }

        fun stop() {
            if (!running.getAndSet(false)) return
            runCatching { udp?.close() }
            runCatching { server?.close() }
            acceptJob?.cancel()
            udpJob?.cancel()
            releaseMulticastLock(multicastLock)
            multicastLock = null
            udp = null
            server = null
            acceptJob = null
            udpJob = null
        }

        private suspend fun handle(sock: Socket) {
            sock.use { s ->
                try {
                    s.soTimeout = 120_000
                    val payload = readFrame(DataInputStream(BufferedInputStream(s.getInputStream())))
                    val ack = onPayload(payload)
                    writeFrame(DataOutputStream(BufferedOutputStream(s.getOutputStream())), ack)
                } catch (e: Exception) {
                    scope.launch(Dispatchers.Main) { onError("接收失败：${e.message ?: "连接中断"}") }
                }
            }
        }

        private fun startUdp() {
            val us = runCatching {
                DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    bind(InetSocketAddress(UDP_PORT))
                }
            }.getOrNull() ?: return
            udp = us
            udpJob = scope.launch(Dispatchers.IO) {
                // 本机 IP：自己发出去的探测/单播扫描会回环到自己，必须忽略，否则「自己发现自己」
                val selfIps = localIpv4().toHashSet()
                val buf = ByteArray(64)
                while (running.get() && !us.isClosed) {
                    val pkt = DatagramPacket(buf, buf.size)
                    try {
                        us.receive(pkt)
                    } catch (e: Exception) {
                        break
                    }
                    val msg = String(pkt.data, 0, pkt.length, StandardCharsets.UTF_8)
                    val from = pkt.address?.hostAddress
                    if (msg == UDP_DISCOVER && from != null && from !in selfIps) {
                        val reply = "$UDP_HERE ${deviceName()} ${server?.localPort ?: PORT_BASE}"
                        val bytes = reply.toByteArray(StandardCharsets.UTF_8)
                        runCatching { us.send(DatagramPacket(bytes, bytes.size, pkt.address, pkt.port)) }
                    }
                }
            }
        }
    }

    // ---------------- 发现 ----------------

    /**
     * 发现局域网内的接收端。两条路径，先广播、收不到再退化为同网段**单播扫描**：
     *  · 广播：一次性覆盖整个网段，最快；但部分 ROM（实测 ColorOS）会过滤入站广播，
     *    表现为「扫描不到别人」；
     *  · 单播扫描：给同网段 254 个地址各发一份探测（点对点，不受广播过滤影响），
     *    慢一点但可靠 —— 这是广播被过滤时的兜底。
     * 全程持有 MulticastLock，进一步降低广播被过滤的概率。
     */
    suspend fun discover(timeoutMs: Int = 1600): List<DiscoveredDevice> {
        val lock = acquireMulticastLock()
        try {
            val viaBroadcast = probe(broadcastTargets(), timeoutMs)
            if (viaBroadcast.isNotEmpty()) return viaBroadcast
            return probe(subnetTargets(), 1800)
        } finally {
            releaseMulticastLock(lock)
        }
    }

    /** 向 [targets] 各发一份 DISCOVER，收集 [timeoutMs] 内的 HERE 应答（按 IP 去重，剔除本机） */
    private suspend fun probe(targets: List<InetAddress>, timeoutMs: Int): List<DiscoveredDevice> =
        withContext(Dispatchers.IO) {
            if (targets.isEmpty()) return@withContext emptyList()
            val found = LinkedHashMap<String, DiscoveredDevice>()
            val self = localIpv4().toHashSet()
            val sock = runCatching { DatagramSocket().apply { broadcast = true; soTimeout = 250 } }.getOrNull()
                ?: return@withContext emptyList()
            try {
                val msg = UDP_DISCOVER.toByteArray(StandardCharsets.UTF_8)
                for (t in targets) runCatching { sock.send(DatagramPacket(msg, msg.size, t, UDP_PORT)) }

                val deadline = System.currentTimeMillis() + timeoutMs
                val buf = ByteArray(128)
                while (System.currentTimeMillis() < deadline) {
                    val pkt = DatagramPacket(buf, buf.size)
                    try {
                        sock.receive(pkt)
                    } catch (e: SocketTimeoutException) {
                        continue
                    }
                    val text = String(pkt.data, 0, pkt.length, StandardCharsets.UTF_8)
                    if (!text.startsWith(UDP_HERE)) continue
                    val ip = pkt.address?.hostAddress ?: continue
                    if (ip in self) continue // 不把自己列为可同步设备
                    val parts = text.split(" ")
                    val name = parts.getOrNull(1)?.ifBlank { "Android设备" } ?: "Android设备"
                    val port = parts.getOrNull(2)?.toIntOrNull() ?: PORT_BASE
                    found[ip] = DiscoveredDevice(ip, port, name)
                }
            } catch (_: Exception) {
                // 发送/接收失败按「没发现设备」处理，调用方仍可手输地址
            } finally {
                runCatching { sock.close() }
            }
            found.values.toList()
        }

    /** 广播目标：全局广播 + 每张网卡的真实广播地址（从掩码取，不再猜 /24） */
    private fun broadcastTargets(): List<InetAddress> {
        val out = LinkedHashSet<InetAddress>()
        runCatching { out.add(InetAddress.getByName("255.255.255.255")) }
        runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.interfaceAddresses }
                .mapNotNull { it.broadcast }
                .filterIsInstance<Inet4Address>()
                .forEach { out.add(it) }
        }
        // 兜底：部分 ROM 取不到 broadcast 字段时按 /24 猜
        localIpv4().forEach { ip -> subnetBroadcast(ip)?.let { out.add(it) } }
        return out.toList()
    }

    /** 同网段单播目标（广播被过滤时的兜底路径）：x.y.z.1 ~ x.y.z.254 */
    private fun subnetTargets(): List<InetAddress> {
        val base = localIpv4().firstOrNull()?.split(".") ?: return emptyList()
        if (base.size != 4) return emptyList()
        return (1..254).mapNotNull { i ->
            runCatching { InetAddress.getByName("${base[0]}.${base[1]}.${base[2]}.$i") }.getOrNull()
        }
    }

    /** /24 子网广播地址（掩码取不到时的兜底猜测） */
    private fun subnetBroadcast(ip: String): InetAddress? = runCatching {
        val p = ip.split(".")
        if (p.size != 4) null else InetAddress.getByName("${p[0]}.${p[1]}.${p[2]}.255")
    }.getOrNull()
}