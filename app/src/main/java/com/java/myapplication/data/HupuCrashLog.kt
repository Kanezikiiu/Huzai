package com.java.myapplication.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 1.224n：崩溃取证（**正式包也启用**）。
 *
 * 三类证据，全部只落**本机私有目录**（`filesDir`），不上传、不写共享存储：
 *
 * 1. **未捕获异常**（Java 层）→ `crash_last.txt`
 * 2. **播放黑匣子** → `video_blackbox.txt`
 *    播放期间滚动记录最近阶段（起播 / 抓帧 / 视频尺寸 / 首帧 / 播放错误…）。
 *    它存在的意义是**原生崩溃**：MediaCodec / 解码器一旦崩掉进程，
 *    Java 层的 `UncaughtExceptionHandler` 根本不会被调用，日志里什么都留不下 ——
 *    只能靠崩溃前已经落盘的这几行，判断"崩在哪一步、崩的是哪一支视频"。
 * 3. **异常终止检测** → `session.marker`
 *    启动时埋标记、App 退到后台即清除；下次启动若发现标记还在，说明上次是**前台异常终止**
 *    （崩溃或被系统强杀），此时把黑匣子合成一条记录并入崩溃日志。
 *
 * 隐私：视频地址只记 **host + path**（去掉 `auth_key` 等查询参数）；
 * 这些文件已在 `backup_rules.xml` / `data_extraction_rules.xml` 中排除云备份与换机迁移。
 */
object HupuCrashLog {

    private const val F_CRASH = "crash_last.txt"
    private const val F_BLACKBOX = "video_blackbox.txt"
    private const val F_MARKER = "session.marker"

    /** 黑匣子保留行数（约等于「崩溃前最后几步」） */
    private const val BLACKBOX_MAX_LINES = 60

    private var dir: File? = null
    private var appContext: android.content.Context? = null
    /** 1.224n：取证总开关（默认关，见 [HupuPrefs.isCrashLogEnabled]）。关闭时**一行都不写**。 */
    private var enabled = false
    /** 1.224n：本进程的启动时刻 —— 用来区分"标记是本进程写的"还是"上次进程留下的"。 */
    private var processStartMs = 0L
    private var handlerInstalled = false
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    /** App 启动时调用一次（从 Application.onCreate）。只有开启了取证才真正干活。 */
    fun init(context: Context) {
        dir = context.filesDir
        appContext = context.applicationContext
        processStartMs = System.currentTimeMillis()
        enabled = HupuPrefs.isCrashLogEnabled()
        if (enabled) applyEnabled(atStartup = true)
    }

    /** 1.224n：按需开关取证（「关于 → 崩溃日志」页顶部那个开关）。 */
    fun setEnabled(context: Context, on: Boolean) {
        dir = context.filesDir
        appContext = context.applicationContext
        if (processStartMs == 0L) processStartMs = System.currentTimeMillis()
        enabled = on
        HupuPrefs.saveCrashLogEnabled(on)
        if (on) applyEnabled(atStartup = false)
    }

    /**
     * 开启取证时要做的事。
     *
     * ⚠️ **只有冷启动（[atStartup] = true）才判定"上次异常终止"** ——
     * 中途打开开关时，标记文件要么是**本进程刚才自己写的**、要么是上个进程留下的，
     * 在这里判定会凭空报出一条不存在的崩溃（真机复现：「清空日志 → 开启取证 → 重进页面」多出一条）。
     */
    private fun applyEnabled(atStartup: Boolean) {
        if (atStartup) detectAbnormalExit()
        markRunning()
        installHandler()
        note(if (atStartup) "启动" else "开启取证")
        val ctx = appContext ?: return
        runCatching {
            Thread {
                runCatching { collectExitReports(ctx) }
            }.apply { isDaemon = true }.start()
        }
    }

    /**
     * trace 保留上限。
     *
     * 1.224n：**从 8000 提到 600000** —— 小米的崩溃报告是「全线程转储」（这次 527KB），
     * 只截前 8000 字节时，里面全是"空闲线程"的帧（`SynchronousQueue.poll` 之类），
     * **崩溃线程的块在更后面**，等于白拿。完整保留后才能在本地把报告解析出来、
     * 定位到崩溃线程的帧表（库名 + 函数名）。
     * 日志会因此变大（几百 KB），但它只在「关于 → 崩溃日志」里，导出时也是单文件，可接受。
     */
    private const val TRACE_CAP = 600_000

    /**
     * 读取系统的进程退出记录（[android.app.ApplicationExitInfo]，API 30+）。
     *
     * 为什么必须做：**原生崩溃的现场只有系统知道** —— 它会写 tombstone（崩在哪个库/符号、
     * 什么信号、栈回溯）。`getTraceInputStream()` 在 native 崩溃时返回的就是这份 tombstone。
     * 没有它，我们只能"按阶段猜"（前几轮已经猜错两次：先怀疑抓帧，再怀疑厂商硬解）。
     */
    private fun collectExitReports(context: android.content.Context) {
        if (android.os.Build.VERSION.SDK_INT < 30) return
        val am = context.getSystemService(android.content.Context.ACTIVITY_SERVICE)
            as? android.app.ActivityManager ?: return
        val reports = runCatching {
            // packageName = null → 只看本应用自己的进程；pid = 0 → 不限；maxNum 控制在 8 条内
            am.getHistoricalProcessExitReasons(null, 0, 8)
        }.getOrNull() ?: return
        if (reports.isEmpty()) return
        // 首次运行时把"已看到"的基准设为当前时间 —— 避免把安装前的旧记录误报成本次崩溃
        val lastSeen = HupuPrefs.lastSeenExitTs()
        if (lastSeen == 0L) {
            HupuPrefs.saveSeenExitTs(System.currentTimeMillis())
            return
        }
        val fresh = reports.filter { it.timestamp > lastSeen }.maxByOrNull { it.timestamp } ?: return
        val text = buildString {
            append("\n———— 系统进程退出记录 ————\n")
            append("原因: ").append(exitReasonName(fresh.reason))
                .append("（code=").append(fresh.reason).append("）\n")
            append("时间: ").append(fmt.format(java.util.Date(fresh.timestamp))).append("\n")
            append("进程: ").append(fresh.processName).append("\n")
            runCatching { fresh.description?.let { if (it.isNotBlank()) append("描述: ").append(it).append("\n") } }
            val trace = runCatching {
                fresh.traceInputStream?.use { ins ->
                    // 1.224n：用 **ISO-8859-1（Latin-1）** 解码，而不是 UTF-8。
                    // 小米的崩溃报告是 **protobuf**（含二进制字节），UTF-8 遇到无效序列会替换成
                    // U+FFFD（真机反馈的"乱码"就是它），而且**原始字节再也拿不回来**；
                    // Latin-1 是"一个字节 = 一个字符"的**无损**映射 —— ASCII 部分（指纹、信号、
                    // 线程名、库名、寄存器名）照常可读，二进制部分也能在外部按字节还原。
                    String(ins.readBytes(), Charsets.ISO_8859_1)
                }
            }.getOrNull()
            if (!trace.isNullOrBlank()) {
                // 1.224n：先把"崩溃现场"的关键行挑出来放在**最前面** ——
                // 原始 trace 动辄几十万字节，用户截图往往只能拍到尾部（缺信号与回溯）。
                // 摘要放在最前，一张截图就能看到：信号 / Abort 信息 / 回溯帧 / 涉及的 .so。
                val digest = crashDigest(trace)
                if (digest.isNotBlank()) {
                    append("\n— ★ 崩溃关键行（从 trace 中提取）—\n")
                    append(digest).append("\n")
                }
                append("\n— 原始 trace（前 ").append(TRACE_CAP)
                    .append(" 字节，共 ").append(trace.length).append(" 字节）—\n")
                append(trace.take(TRACE_CAP))
                if (trace.length > TRACE_CAP) append("\n…（已截断）")
                append("\n")
            }
        }
        HupuPrefs.saveSeenExitTs(fresh.timestamp)
        note("系统退出记录", exitReasonName(fresh.reason))
        // 追加，而不是覆盖 —— 前面 detectAbnormalExit 写的那条要保留
        writeCrashEntry(readCrash() + text)
    }

    /**
     * 从 trace 里挑出"崩溃现场"的关键行：信号 / Abort 信息 / 回溯帧 / 涉及的 .so。
     *
     * 目的：让**一张截图**就能看到重点（原始 trace 几十万字节，用户往往只拍得到尾部）。
     */
    private fun crashDigest(trace: String): String {
        val keys = listOf(
            "signal ", "fault addr", "Abort message", "backtrace:", ">>> ",
            "pid:", "tid:", "Cmdline:", "Cause:", "Revision:",
            "dereference", "SIGSEGV", "SIGABRT", "SIGBUS", "SIGILL",
        )
        val out = ArrayList<String>()
        for (line in trace.lineSequence()) {
            val t = line.trim()
            val hit = keys.any { t.contains(it) } ||
                t.startsWith("#") ||
                t.contains(".so") ||
                t.contains(".oat")
            if (hit) out.add(t.take(200))
            if (out.size >= 60) break
        }
        // 1.224n：补一段"可读字符串" —— 崩溃报告是 protobuf（二进制），按行看全是乱码；
        // 把其中成串的可打印字符抽出来，才能读到**线程名 / 库名 / 函数名**这些关键线索。
        val printable = ArrayList<String>()
        val sb = StringBuilder()
        fun flush() {
            if (sb.length >= 6) {
                val s = sb.toString()
                if (s.contains(".so") || s.contains("::") || s.contains('(') || s.contains('_')) {
                    if (printable.size < 60) printable.add(s.take(160))
                }
            }
            sb.setLength(0)
        }
        for (c in trace) {
            if (c.code in 32..126) sb.append(c) else flush()
        }
        flush()
        return buildString {
            if (out.isNotEmpty()) append(out.joinToString("\n"))
            if (printable.isNotEmpty()) {
                append("\n— 可读字符串（从二进制报告中抽取）—\n")
                append(printable.joinToString("\n"))
            }
        }
    }

    /** ApplicationExitInfo 的退出原因名 */
    private fun exitReasonName(reason: Int): String = when (reason) {
        1 -> "主动退出 EXIT_SELF"
        2 -> "被信号结束 SIGNALED"
        3 -> "内存不足 LOW_MEMORY"
        4 -> "Java 崩溃 CRASH"
        5 -> "**原生崩溃 CRASH_NATIVE**"
        6 -> "ANR"
        7 -> "初始化失败"
        8 -> "权限变更"
        9 -> "资源使用过度"
        10 -> "用户主动结束"
        11 -> "用户停止"
        12 -> "依赖进程死亡"
        13 -> "其他"
        else -> "未知($reason)"
    }

    /** App 正常退到后台 → 清除标记（之后被系统回收就不再算异常） */
    fun onAppBackground() {
        note("进入后台")
        runCatching { file(F_MARKER)?.delete() }
    }

    /** 未捕获异常处理器（只在取证开启时写盘） */
    private fun installHandler() {
        if (handlerInstalled) return
        handlerInstalled = true
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            if (enabled) runCatching {
                val sw = java.io.StringWriter()
                e.printStackTrace(java.io.PrintWriter(sw))
                writeCrashEntry(
                    buildString {
                        append("【未捕获异常】").append(fmt.format(Date())).append("\n")
                        append(deviceLine()).append("\n")
                        append("thread=").append(t.name).append("\n\n")
                        append(sw.toString())
                        append("\n— 崩溃前的最后阶段（播放黑匣子）—\n")
                        append(readBlackbox().ifBlank { "（无：崩溃前没有播放过视频）" })
                        append("\n")
                    },
                )
            }
            prev?.uncaughtException(t, e)
        }
    }

    private fun markRunning() {
        runCatching { file(F_MARKER)?.writeText(fmt.format(Date())) }
    }

    /**
     * 设备指纹 —— 崩溃报告里**必须有**，否则"设备 × 解码器"的坏组合无法固化成随包发布的规则。
     *
     * FINGERPRINT 里已经含了厂商/机型/版本号/ROM 标签（MIUI 的定制也会体现在里面）。
     */
    private fun deviceLine(): String = runCatching {
        buildString {
            append("设备: ")
            append(android.os.Build.MANUFACTURER).append(' ').append(android.os.Build.MODEL)
            append(" | Android ").append(android.os.Build.VERSION.RELEASE)
            append(" (SDK ").append(android.os.Build.VERSION.SDK_INT).append(')')
            // SOC_MANUFACTURER 是 API 31+，本项目 minSdk 24
            val soc = if (android.os.Build.VERSION.SDK_INT >= 31) {
                android.os.Build.SOC_MANUFACTURER.ifBlank { android.os.Build.HARDWARE }
            } else {
                android.os.Build.HARDWARE
            }
            append(" | SoC ").append(soc)
            append("\nROM: ").append(android.os.Build.DISPLAY)
            append("\n指纹: ").append(android.os.Build.FINGERPRINT)
        }
    }.getOrDefault("设备: （读取失败）")

    /**
     * 上次是否为「前台异常终止」。
     *
     * 注意：进程被系统在后台回收属于正常生命周期，那种情况下标记早已在 `onStop` 被清掉，
     * 所以这里报出来的基本就是真崩溃 / 被强杀。
     */
    private fun detectAbnormalExit() {
        val m = file(F_MARKER) ?: return
        if (!m.exists()) return
        // 1.224n：**双保险** —— 标记若是本进程（或本进程之后的时刻）写的，就不是"上次进程异常终止"，
        // 直接忽略并清掉，避免中途开启取证时凭空上报。
        if (processStartMs > 0L && m.lastModified() >= processStartMs) {
            runCatching { m.delete() }
            return
        }
        val startedAt = runCatching { m.readText() }.getOrDefault("")
        val box = readBlackbox()
        // 1.224n：判断这次异常终止是不是发生在「播放视频」期间（见 diedDuringPlayback）。
        // 若是，下面会把"崩溃时的那个解码器"拉黑 —— 这是**真正的修复方向**：
        // 同一个编码在设备上常有多个解码器候选，崩的往往只是其中一个。
        val diedInPlayback = diedDuringPlayback(box)
        // 1.224n：「崩过的解码器下次跳过」已**撤销** —— 那场崩溃的根因是渲染面（SurfaceView），
        // 与解码器无关；留着只会在个别设备上白白丢掉硬解。
        val badDecoder = ""
        val added = false
        writeCrashEntry(
            buildString {
                append("【异常终止】App 上次没有正常退到后台就结束了（崩溃 / 被系统强杀）。\n")
                append("上次启动: ").append(startedAt).append("\n")
                append("本次启动: ").append(fmt.format(Date())).append("\n")
                append(deviceLine()).append("\n")
                append("\n— 崩溃前的最后阶段（播放黑匣子）—\n")
                append(box.ifBlank { "（无：崩溃前没有播放过视频）" })
                append("\n")
                if (added) {
                    append("\n→ 崩溃发生在播放视频期间；当时用的解码器 **").append(badDecoder)
                        .append("** 已记下：\n")
                    append("   下次播放会自动改用该编码的下一个候选解码器（无需手动操作）。\n")
                } else if (diedInPlayback && badDecoder.isNotBlank()) {
                    append("\n→ 崩溃发生在播放视频期间；解码器 ").append(badDecoder)
                        .append(" 此前已在跳过列表内。\n")
                } else if (diedInPlayback) {
                    append("\n→ 崩溃发生在播放视频期间（黑匣子里没有解码器记录，可能是更早的阶段）。\n")
                }
            },
        )
        runCatching { m.delete() }
        // 已并入崩溃日志，清掉黑匣子避免下次重复上报同一段
        runCatching { file(F_BLACKBOX)?.delete() }
    }

    /**
     * 从黑匣子取出「最后选中的**视频**解码器名」——用于下次跳过它。
     *
     * 行形如：`10-12 18:07:20.783  选解码器  video/avc → c2.qti.avc.decoder（候选 3 / 软解 1 / 跳过 0）`
     */
    private fun lastVideoDecoder(box: String): String {
        val line = box.lines().lastOrNull { it.contains("选解码器") && it.contains("video/") }
            ?: return ""
        val after = line.substringAfter("→ ", "")
        return after.substringBefore("（").trim()
    }

    /**
     * 黑匣子是否显示「上次是在播放视频的过程中死掉的」。
     *
     * 判据：最后几步里出现过 `起播请求` / `抓帧`，且**没有**出现过 `视频可显示`
     * （后者表示画面已经出来过，那么这次崩溃就不是"起播期"的问题）。
     */
    private fun diedDuringPlayback(box: String): Boolean {
        if (box.isBlank()) return false
        val tail = box.lines().filter { it.isNotBlank() }.takeLast(6).joinToString("\n")
        if (tail.contains("视频可显示")) return false
        return tail.contains("起播请求") || tail.contains("抓帧")
    }

    /**
     * 播放侧错误（ExoPlayer 通过 `onPlayerError` 传播出来的，App 通常不会崩）。
     * 单独记一条：这类错误与原生崩溃的特征完全不同，分开更能看出是哪一类。
     */
    fun recordPlaybackError(tag: String, detail: String, throwable: Throwable? = null) {
        if (!enabled) return
        runCatching {
            note(tag, detail)
            val sw = throwable?.let { err ->
                java.io.StringWriter().also { err.printStackTrace(java.io.PrintWriter(it)) }.toString()
            }.orEmpty()
            writeCrashEntry(
                buildString {
                    append("【").append(tag).append("】").append(fmt.format(Date())).append("\n")
                    append(detail).append("\n")
                    if (sw.isNotBlank()) append("\n").append(sw).append("\n")
                },
            )
        }
    }

    /** 黑匣子：滚动记录播放阶段（最新追加在末尾，超出上限丢最旧的） */
    fun note(stage: String, detail: String = "") {
        if (!enabled) return
        runCatching {
            val f = file(F_BLACKBOX) ?: return
            val old = runCatching { f.readText() }.getOrDefault("")
            val line = fmt.format(Date()) + "  " + stage + if (detail.isBlank()) "" else "  $detail"
            val lines = (old.lines().filter { it.isNotBlank() } + line).takeLast(BLACKBOX_MAX_LINES)
            f.writeText(lines.joinToString("\n") + "\n")
        }
    }

    /** 视频地址脱敏：只留 host + path（去掉 auth_key 等查询参数） */
    fun safeUrl(url: String?): String {
        if (url.isNullOrBlank()) return ""
        return runCatching {
            val u = java.net.URI(url)
            val host = u.host
            if (host == null) url.substringBefore('?') else "${u.scheme}://$host${u.path.orEmpty()}"
        }.getOrDefault(url.substringBefore('?'))
    }

    fun readCrash(): String =
        file(F_CRASH)?.let { runCatching { it.readText() }.getOrDefault("") }.orEmpty()

    fun readBlackbox(): String =
        file(F_BLACKBOX)?.let { runCatching { it.readText() }.getOrDefault("") }.orEmpty()

    /** 供「关于 → 崩溃日志」展示 / 保存：崩溃日志 + 附最近播放记录 */
    fun readForDisplay(): String {
        val crash = readCrash()
        val box = readBlackbox()
        return buildString {
            append(crash.ifBlank { "暂无崩溃日志。\n\n（开启崩溃取证后，崩溃 / 异常终止发生时会自动记录：未捕获异常堆栈、播放黑匣子、异常终止标记；可用上方「保存日志」导出。）\n" })
            if (box.isNotBlank()) {
                append("\n\n———— 最近播放记录（黑匣子）————\n")
                append(box)
            }
        }.trimEnd()
    }

    /** 清空全部取证文件（关于页「清除日志」） */
    fun clear() {
        runCatching { file(F_CRASH)?.delete() }
        runCatching { file(F_BLACKBOX)?.delete() }
        // 1.224n：**运行标记也要清** —— 否则"清空日志"后残留的标记会在下次开启取证时
        // 被误判成"上次异常终止"（真机复现）。清完后若取证仍开着，重新记一次本轮运行标记，
        // 这样之后真的崩溃依然能被下一次冷启动识别。
        runCatching { file(F_MARKER)?.delete() }
        if (enabled) markRunning()
    }

    private fun writeCrashEntry(text: String) {
        file(F_CRASH)?.let { runCatching { it.writeText(text) } }
    }

    private fun file(name: String): File? = dir?.let { File(it, name) }
}
