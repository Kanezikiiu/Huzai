package com.java.myapplication.ui.pages

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.java.myapplication.BuildConfig
import com.java.myapplication.data.DiscoveredDevice
import com.java.myapplication.data.DEFAULT_ITEM_GROUP
import com.java.myapplication.data.HupuSyncNet
import com.java.myapplication.data.SyncAck
import com.java.myapplication.data.SyncBundle
import com.java.myapplication.data.SyncItem
import com.java.myapplication.data.SyncItemResult
import com.java.myapplication.data.applySyncItem
import com.java.myapplication.data.decodeSyncAck
import com.java.myapplication.data.decodeSyncBundle
import com.java.myapplication.data.encodeSyncAck
import com.java.myapplication.data.encodeSyncBundle
import com.java.myapplication.data.exportSyncItem
import com.java.myapplication.data.syncItemCount
import com.java.myapplication.ui.components.CenteredTopBar
import com.java.myapplication.ui.components.HuzaiToast
import com.java.myapplication.ui.components.SecondaryPage
import com.java.myapplication.ui.components.huzaiFieldColors
import com.java.myapplication.ui.components.tapGuard
import com.java.myapplication.ui.glass.LiquidButton
import com.java.myapplication.ui.glass.LiquidGlassButton
import com.java.myapplication.ui.glass.LiquidGlassCard
import com.java.myapplication.ui.glass.buttonBorder
import com.java.myapplication.ui.glass.buttonFill
import com.java.myapplication.ui.theme.isAppDarkTheme
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 「数据同步」二级页（盖入式）：同一 WiFi 下两台设备互传本地设置。
 *
 * 角色模型：**接收方先开「等待」，发送方填对方地址后推送**。
 *  · 接收方收到连接 → 弹确认弹窗（列出项目 + 标注合并/覆盖）→ 同意才落地；
 *  · 发送方阻塞等待回执，如实显示对方是否接收、落地了几项。
 *  · 设备地址既可手输，也可「扫描」用 UDP 广播自动发现。
 */
@Composable
internal fun SyncPage(onClose: () -> Unit) {
    // ---------- 盖入式入场 / 返回 ----------
    val progress = remember { Animatable(0f) }
    var pageEntered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        pageEntered = true
        SecondaryPage.enter()
        progress.animateTo(1f, tween(280))
    }
    DisposableEffect(Unit) {
        onDispose { if (pageEntered) { pageEntered = false; SecondaryPage.exit() } }
    }
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(closing) {
        if (closing) {
            if (pageEntered) { pageEntered = false; SecondaryPage.exit() }
            progress.animateTo(0f, tween(280))
            onClose()
        }
    }
    PredictiveBackHandler { events ->
        if (closing) {
            events.collect { }
            return@PredictiveBackHandler
        }
        try {
            events.collect { event -> progress.snapTo(1f - event.progress) }
            progress.animateTo(0f, tween(120))
            closing = true
        } catch (e: CancellationException) {
            progress.animateTo(1f, tween(200))
            throw e
        }
    }

    val scope = rememberCoroutineScope()
    // 网络协程作用域：独立于组合，页面销毁时统一取消（socket / 监听一并回收）
    val netScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    DisposableEffect(Unit) { onDispose { netScope.cancel() } }

    // 弹窗采样层：记录本页内容像素（弹窗须为其后续兄弟节点）
    val dialogBg = MaterialTheme.colorScheme.background
    val backdrop = rememberLayerBackdrop {
        drawRect(dialogBg)
        drawContent()
    }

    // ---------- 接收状态 ----------
    var listening by remember { mutableStateOf(false) }
    var listenPort by remember { mutableIntStateOf(0) }
    var localIp by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf("") }
    var pendingPayload by remember { mutableStateOf<String?>(null) }
    var pendingDeferred by remember { mutableStateOf<CompletableDeferred<String>?>(null) }

    val receiver = remember {
        HupuSyncNet.Receiver(
            scope = netScope,
            onPayload = { raw ->
                val d = CompletableDeferred<String>()
                withContext(Dispatchers.Main) {
                    pendingDeferred = d
                    pendingPayload = raw
                }
                d.await()
            },
            onStarted = { port ->
                listenPort = port
                listening = true
                localIp = HupuSyncNet.localIpv4Primary()
                statusText = "正在等待对方连接…"
            },
            onError = { msg ->
                statusText = msg
                HuzaiToast.show(msg)
            },
        )
    }
    DisposableEffect(Unit) { onDispose { receiver.stop() } }

    // ---------- 发送状态 ----------
    var sendAddr by remember { mutableStateOf("") }
    // 1.2xx（真机反馈）：默认全部不勾选，由用户按需选择
    var selected by remember { mutableStateOf(emptySet<SyncItem>()) }
    var sending by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    var devices by remember { mutableStateOf<List<DiscoveredDevice>>(emptyList()) }
    // 1.2xx：同步结束后弹结果对话框（逐项列出成功/未写入，比 Toast 直观）
    var resultDialog by remember { mutableStateOf<SyncResultUi?>(null) }
    // 每次进入页面读一次本机各数据量（发送页计数标签）
    val counts = remember { SyncItem.entries.associateWith { syncItemCount(it) } }

    val dark = isAppDarkTheme()

    fun doSend() {
        val host = sendAddr.substringBefore(":").trim()
        val port = sendAddr.substringAfter(":", "").trim().toIntOrNull() ?: HupuSyncNet.PORT_BASE
        when {
            host.isEmpty() -> HuzaiToast.show("请先填写对方地址")
            selected.isEmpty() -> HuzaiToast.show("请至少选择一项数据")
            else -> {
                sending = true
                scope.launch {
                    val payload = withContext(Dispatchers.Default) {
                        encodeSyncBundle(
                            SyncBundle(
                                versionCode = BuildConfig.VERSION_CODE,
                                versionName = BuildConfig.VERSION_NAME,
                                values = selected.associateWith { exportSyncItem(it) },
                            )
                        )
                    }
                    val r = runCatching { HupuSyncNet.send(host, port, payload) }
                    sending = false
                    r.fold(
                        onSuccess = { ackJson ->
                            val ack = decodeSyncAck(ackJson)
                            if (ack == null) {
                                HuzaiToast.show("已发送，但未收到有效回执")
                            } else {
                                // 结果对话框：逐项列出各数据的落地情况（含未写入条数）
                                resultDialog = SyncResultUi("同步结果", ack)
                            }
                        },
                        onFailure = { e ->
                            HuzaiToast.show("发送失败：${e.message ?: "无法连接对方"}")
                        },
                    )
                }
            }
        }
    }

    Box(Modifier.fillMaxSize().zIndex(2f)) {
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = (1f - progress.value) * size.width }
                .background(MaterialTheme.colorScheme.background)
                .tapGuard()
                .layerBackdrop(backdrop),
        ) {
            CenteredTopBar(title = "数据同步", onBack = { closing = true })
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 4.dp, bottom = 120.dp),
            ) {
                Text(
                    "两台设备需连接同一 WiFi。「合并」把对方内容并入本机（去重、超上限自动截断）；" +
                        "「覆盖」用对方内容替换本机原有设置。登录状态、浏览记录等隐私数据不在同步范围内。",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))

                // ---------- ① 接收 ----------
                SyncSection("接收数据") {
                    if (listening) {
                        Text(
                            if (localIp.isEmpty()) "未检测到 WiFi 地址" else "$localIp:$listenPort",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            statusText.ifEmpty { "正在等待对方连接…" },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            "点「开始等待」后本机进入监听。对方连接并发送数据时，本机将弹出确认框，同意后才写入。",
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    LiquidButton(
                        onClick = {
                            if (listening) {
                                receiver.stop()
                                listening = false
                                statusText = ""
                            } else {
                                receiver.start()
                            }
                        },
                        fill = buttonFill(dark),
                        border = buttonBorder(dark),
                        height = 44.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (listening) "停止等待" else "开始等待",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))

                // ---------- ② 发送 ----------
                SyncSection("发送数据") {
                    OutlinedTextField(
                        colors = huzaiFieldColors(container = false),
                        value = sendAddr,
                        onValueChange = { sendAddr = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("对方地址（如 192.168.1.7:8765）", fontSize = 13.sp) },
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LiquidButton(
                            onClick = {
                                if (!scanning) {
                                    scanning = true
                                    scope.launch {
                                        devices = HupuSyncNet.discover()
                                        scanning = false
                                        if (devices.isEmpty()) HuzaiToast.show("未发现设备，可手动填写地址")
                                    }
                                }
                            },
                            fill = buttonFill(dark),
                            border = buttonBorder(dark),
                            height = 36.dp,
                            contentPadding = 14.dp,
                        ) {
                            Text(
                                if (scanning) "扫描中…" else "扫描局域网",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "对方的接收页需保持打开",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (devices.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        devices.forEach { d ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { sendAddr = "${d.ip}:${d.port}" }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(d.name, Modifier.weight(1f), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                                Text("${d.ip}:${d.port}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Text(
                        "选择要发送的数据",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    var groupRendered = false
                    // 「默认事项」子项是否展开（点父项整行切换）；1.2xx（真机反馈）：默认**收起**，主列表更清爽
                    var groupExpanded by remember { mutableStateOf(false) }
                    SyncItem.entries.forEach { item ->
                        if (item in DEFAULT_ITEM_GROUP) {
                            // 「默认事项」组：父项三态勾选（一次收放四个子项）+ 可折叠的四个子项
                            if (!groupRendered) {
                                groupRendered = true
                                val groupSet = DEFAULT_ITEM_GROUP.toSet()
                                val checkedCount = DEFAULT_ITEM_GROUP.count { it in selected }
                                SyncGroupRow(
                                    checkedState = when (checkedCount) {
                                        DEFAULT_ITEM_GROUP.size -> ToggleableState.On
                                        0 -> ToggleableState.Off
                                        else -> ToggleableState.Indeterminate
                                    },
                                    expanded = groupExpanded,
                                    onToggleAll = { on ->
                                        selected = if (on) selected + groupSet else selected - groupSet
                                    },
                                    onToggleExpand = { groupExpanded = !groupExpanded },
                                )
                                if (groupExpanded) {
                                    DEFAULT_ITEM_GROUP.forEach { child ->
                                        SyncItemRow(
                                            item = child,
                                            count = counts[child] ?: 0,
                                            checked = child in selected,
                                            indent = 16.dp,
                                            onToggle = { on ->
                                                selected = if (on) selected + child else selected - child
                                            },
                                        )
                                    }
                                }
                            }
                        } else {
                            SyncItemRow(
                                item = item,
                                count = counts[item] ?: 0,
                                checked = item in selected,
                                onToggle = { on ->
                                    selected = if (on) selected + item else selected - item
                                },
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    LiquidButton(
                        onClick = { if (!sending) doSend() },
                        fill = buttonFill(dark),
                        border = buttonBorder(dark),
                        height = 44.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (sending) "发送中…" else "发送（已选 ${selected.size} 项）",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    if (SyncItem.START_TAB in selected) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "提示：默认启动页在对方设备下次冷启动后生效。",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // ---------- 接收确认弹窗 ----------
        val payload = pendingPayload
        if (payload != null) {
            val bundle = remember(payload) { decodeSyncBundle(payload) }
            SyncConfirmDialog(
                backdrop = backdrop,
                bundle = bundle,
                onAccept = {
                    val info = bundle
                    val d = pendingDeferred
                    pendingPayload = null
                    pendingDeferred = null
                    scope.launch {
                        val ackJson = if (info == null) {
                            encodeSyncAck(false, "对方数据格式无法识别，已拒收")
                        } else {
                            // 落地在主线程执行（会 bump 各页订阅的版本号，需在主线程写 Compose 状态）
                            val results = info.values.entries.map { (item, raw) -> applySyncItem(item, raw) }
                            encodeSyncAck(
                                true,
                                "已接收 ${results.size} 项",
                                results.map { SyncItemResult(it.item.label, it.summary, it.dropped) },
                            )
                        }
                        d?.complete(ackJson)
                        statusText = "接收完成，可继续等待对方"
                        decodeSyncAck(ackJson)?.let { resultDialog = SyncResultUi("同步结果（接收）", it) }
                    }
                },
                onReject = {
                    val d = pendingDeferred
                    val rejectMsg = if (bundle == null) "对方数据格式无法识别" else "对方取消了本次同步"
                    pendingPayload = null
                    pendingDeferred = null
                    scope.launch { d?.complete(encodeSyncAck(false, rejectMsg)) }
                },
            )
        }

        // ---------- 同步结果对话框（发送端 / 接收端共用） ----------
        resultDialog?.let { data ->
            SyncResultDialog(
                backdrop = backdrop,
                data = data,
                onClose = { resultDialog = null },
            )
        }
    }
}

/** 同步结果对话框的数据载体 */
private data class SyncResultUi(val title: String, val ack: SyncAck)

/** 结果对话框：逐项列出各数据的落地情况，并明确「未写入」条数 */
@Composable
private fun SyncResultDialog(
    backdrop: Backdrop,
    data: SyncResultUi,
    onClose: () -> Unit,
) {
    val isLight = !isAppDarkTheme()
    val contentColor = if (isLight) Color.Black else Color.White
    val ack = data.ack
    val partial = ack.results.count { it.dropped > 0 }
    val headline = when {
        !ack.ok -> ack.message.ifEmpty { "同步未完成" }
        ack.results.isEmpty() -> ack.message.ifEmpty { "同步完成" }
        partial == 0 -> "全部成功（${ack.results.size} 项）"
        else -> "成功 ${ack.results.size - partial} 项 · ${partial} 项有内容未写入"
    }
    LiquidGlassCard(
        backdrop = backdrop,
        onDismiss = onClose,
    ) { _ ->
        Text(
            data.title,
            Modifier.padding(24.dp, 24.dp, 24.dp, 8.dp),
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            color = contentColor,
        )
        Text(
            headline,
            Modifier.padding(24.dp, 4.dp, 24.dp, if (ack.results.isEmpty()) 4.dp else 10.dp),
            fontSize = 14.sp,
            color = if (!ack.ok) MaterialTheme.colorScheme.error else contentColor.copy(alpha = 0.68f),
        )
        if (ack.results.isNotEmpty()) {
            Column(
                Modifier
                    .padding(horizontal = 24.dp)
                    .heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                ack.results.forEach { r ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(r.label, fontSize = 14.sp, color = contentColor)
                            Text(
                                r.summary,
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                color = if (r.dropped > 0) {
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.9f)
                                } else {
                                    contentColor.copy(alpha = 0.55f)
                                },
                            )
                        }
                    }
                }
            }
        }
        Row(
            Modifier
                .padding(24.dp, 16.dp, 24.dp, 24.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LiquidGlassButton(
                text = "知道了",
                accent = true,
                modifier = Modifier.weight(1f),
                contentColor = contentColor,
            ) { onClose() }
        }
    }
}

/** 分组卡片：圆角 + surface 底（与信息流卡片同形） */
@Composable
private fun SyncSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
    ) {
        Text(
            title,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(10.dp))
        content()
    }
}

/**
 * 「默认事项」父项：
 *  · 点**复选框** = 一键全选 / 全不选（三态：全选 / 半选 / 全不选）
 *  · 点**整行**（含右侧箭头）= 展开 / 折叠子项
 */
@Composable
private fun SyncGroupRow(
    checkedState: ToggleableState,
    expanded: Boolean,
    onToggleAll: (Boolean) -> Unit,
    onToggleExpand: () -> Unit,
) {
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(180),
        label = "syncGroupChevron",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onToggleExpand() }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TriStateCheckbox(
            state = checkedState,
            onClick = { onToggleAll(checkedState != ToggleableState.On) },
        )
        Column(Modifier.weight(1f)) {
            Text(
                "默认事项",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "默认启动页 · 专区 / 回复 / 评分排序（覆盖）",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = if (expanded) "折叠" else "展开",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer { rotationZ = chevron },
        )
    }
}

/** 可勾选的数据项行：名称 + 合并/覆盖 + 本机条数 */
@Composable
private fun SyncItemRow(
    item: SyncItem,
    count: Int,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    /** 组内子项的缩进（父项 0，子项一级） */
    indent: Dp = 0.dp,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = indent)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onToggle(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onToggle)
        Column(Modifier.weight(1f)) {
            Text(item.label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(
                (if (item.overwrite) "覆盖" else "合并") + " · 本机 $count 项",
                fontSize = 11.sp,
                color = if (item.overwrite) {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/** 接收确认弹窗：列出对方即将写入的项目，明确标注合并 / 覆盖 */
@Composable
private fun SyncConfirmDialog(
    backdrop: Backdrop,
    bundle: SyncBundle?,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    val isLight = !isAppDarkTheme()
    val contentColor = if (isLight) Color.Black else Color.White
    LiquidGlassCard(
        backdrop = backdrop,
        onDismiss = onReject,
    ) { _ ->
        Text(
            "接收数据",
            Modifier.padding(24.dp, 24.dp, 24.dp, 8.dp),
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            color = contentColor,
        )
        if (bundle == null) {
            Text(
                "对方发来的数据无法识别（协议版本不一致），已拒绝。",
                Modifier.padding(24.dp, 4.dp, 24.dp, 8.dp),
                fontSize = 14.sp,
                color = contentColor.copy(alpha = 0.68f),
            )
        } else {
            val sender = if (bundle.versionName.isBlank()) "未知版本" else "v${bundle.versionName}"
            Text(
                "来自对方设备（$sender）的 ${bundle.values.size} 项数据：",
                Modifier.padding(24.dp, 4.dp, 24.dp, 10.dp),
                fontSize = 14.sp,
                color = contentColor.copy(alpha = 0.68f),
            )
            Column(
                Modifier
                    .padding(horizontal = 24.dp)
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                for ((item, _) in bundle.values) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(item.label, Modifier.weight(1f), fontSize = 14.sp, color = contentColor)
                        Text(
                            if (item.overwrite) "覆盖" else "合并",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (item.overwrite) MaterialTheme.colorScheme.error else contentColor.copy(alpha = 0.55f),
                        )
                    }
                }
            }
            if (bundle.values.keys.any { it.overwrite }) {
                Text(
                    "标注「覆盖」的项目会以对方数据替换本机原有内容。",
                    Modifier.padding(24.dp, 10.dp, 24.dp, 0.dp),
                    fontSize = 12.sp,
                    color = contentColor.copy(alpha = 0.6f),
                )
            }
        }
        Row(
            Modifier
                .padding(24.dp, 16.dp, 24.dp, 24.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LiquidGlassButton(
                text = "取消",
                accent = false,
                modifier = Modifier.weight(1f),
                contentColor = contentColor,
            ) { onReject() }
            if (bundle != null) {
                LiquidGlassButton(
                    text = "接收",
                    accent = true,
                    modifier = Modifier.weight(1f),
                    contentColor = contentColor,
                ) { onAccept() }
            }
        }
    }
}