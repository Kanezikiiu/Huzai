package com.java.myapplication.ui.pages

import com.java.myapplication.TAB_BUTTON_NAV_MIN
import com.java.myapplication.TAB_GAP_BUTTON_EXTRA
import com.java.myapplication.TAB_GAP_GESTURE
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.BackEventCompat
import kotlin.coroutines.cancellation.CancellationException
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Check
import com.java.myapplication.ui.components.HupuIcons
import com.java.myapplication.ui.components.autoHideScroll
import com.java.myapplication.ui.components.rememberAutoHideBarState
import com.java.myapplication.ui.components.StickerAddCell
import com.java.myapplication.ui.components.StickerSearchSheet
import com.java.myapplication.ui.components.StickerSearchEntry
import com.java.myapplication.ui.components.rememberStickerSearchController
import com.java.myapplication.data.stickerReferer
import com.java.myapplication.ui.glass.LiquidGlassDialog
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.platform.LocalView
import android.content.pm.ActivityInfo
import android.app.Activity
import coil.compose.AsyncImage
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.combinedClickable
import com.java.myapplication.ui.components.HUPU_EMOJI
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.java.myapplication.data.HupuFilter
import com.java.myapplication.data.HupuImage
import com.java.myapplication.data.HupuPostApi
import com.java.myapplication.data.HupuPrefs
import com.java.myapplication.data.isLocalStickerUrl
import com.java.myapplication.data.HupuFloorReplies
import com.java.myapplication.data.HupuReply
import com.java.myapplication.data.HupuSubReply
import com.java.myapplication.data.HupuThreadDetail
import com.java.myapplication.data.HupuVote
import com.java.myapplication.data.HupuVoteApi
import com.java.myapplication.data.HupuVoteOption
import com.java.myapplication.data.HupuVoteResult
import com.java.myapplication.data.HupuParser
import androidx.compose.foundation.text.appendInlineContent
import com.java.myapplication.ui.components.EmojiSegment
import com.java.myapplication.ui.components.ErrorRetry
import com.java.myapplication.ui.components.normalizeCover

import com.java.myapplication.ui.components.rememberEmojiInline
import com.java.myapplication.ui.components.splitEmoji

import com.java.myapplication.ui.components.SkeletonHome
import com.java.myapplication.ui.components.formatCount
import com.java.myapplication.ui.components.tapGuard
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.animation.core.animateFloatAsState
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import com.java.myapplication.data.HupuAccount
import com.java.myapplication.data.HupuSticker
import com.java.myapplication.data.HupuAuthor
import kotlinx.coroutines.launch

/**
 * 帖子详情页（盖入式二级页）。
 * - 正文/回复用纯 Compose HTML 渲染（替换 WebView：滑动卡顿+退出后残留的根因）
 * - 视频帖：主楼 ExoPlayer 播放 thread.video（退出组合即 release）
 * - 楼中楼：回复卡点击 → 本组件内部盖入式三级页（父楼层+子回复流，maxpid 翻页），
 *   数据经 onFloorReplies 回调从父页面仓库获取（状态由父页面外置缓存，返回再进秒开）
 */
@Composable
fun ThreadDetailOverlay(
    tid: String,
    titleText: String,
    detail: HupuThreadDetail?,
    loading: Boolean,
    loadingMore: Boolean,
    closing: Boolean,
    onBack: () -> Unit,
    /** 1.183: 退场动画开始即回调——父级据此同步减二级页计数，使 Tab 栏与页面同步 Q 弹回归（与「我的」页二级页同款手感） */
    onExitStart: () -> Unit = {},
    onClosed: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    /** 1.185: 排序方向切换（true=最新在前/倒序），父层据此重新加载 */
    onSortChanged: (Boolean) -> Unit = {},
    /** 点击作者/回复者头像打开用户主页（无 euid 时回调不触发） */
    onOpenUser: (String) -> Unit = {},
    floorStates: MutableMap<String, HupuFloorReplies> = mutableMapOf(),
    floorLoading: Set<String> = emptySet(),
    floorLoadingMore: Set<String> = emptySet(),
    onOpenFloor: (HupuReply) -> Unit = {},
    onFloorLoadMore: (String, HupuFloorReplies) -> Unit = { _, _ -> },
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(280)) }
    LaunchedEffect(closing) {
        if (closing) {
            onExitStart()
            progress.animateTo(0f, tween(280))
            onClosed()
        }
    }

    // 1.112: 编辑页（本人帖顶栏入口触发；非本人帖不显示入口）
    var editingTid by remember { mutableStateOf<String?>(null) }
    // 1.119: 删除帖子确认弹窗（本人帖顶栏入口触发）
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val actionBarBg = MaterialTheme.colorScheme.background
    // 1.189: 常驻操作条的毛玻璃底——采样「列表内容」这一层（不含操作条自身，
    // 避免自采样糊成一团）。与 MainActivity 的全局 backdrop 同一套引擎。
    val actionBackdrop = rememberLayerBackdrop {
        drawRect(actionBarBg)
        drawContent()
    }
    // 翻页哨兵：注意依赖 detail（canLoadMore 不能被 remember 的闭包 stale 捕获）
    // 1.185: 排序三态 0=默认(正序) 1=最新(倒序) 2=最热(本地按点亮降序)；点击循环（声明上提：可加载性依赖它）
    var sortMode by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val sortExpectedDesc = sortMode == 1
    // 切换进行中（数据方向 ≠ 期望方向）时禁止触发翻页，避免旧方向翻页结果与新方向互相覆盖
    val sortPending = detail?.let { it.descReplies != sortExpectedDesc } == true
    // 方向感知（倒序从最后一页往前翻）；sortPending 期间不再触发翻页
    val canLoadMore = detail?.let { !sortPending && (if (it.descReplies) it.replyPage > 1 else it.replyPage < it.replyTotalPages) } == true
    val shouldLoadMore by remember(detail?.replyPage, loadingMore, detail?.replyTotalPages, detail?.descReplies, sortMode) {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            canLoadMore && !loadingMore && last >= listState.layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    // 视频全屏：host 持有 ExoPlayer 实例（小窗/全屏两个 PlayerView 共享，进度无缝）；
    // videoFullscreen 驱动页面级盖入式全屏 overlay（横屏+沉浸式系统栏）
    val videoHost = remember { VideoHost() }
    // 用户主页（盖入式三级页：详情 → 用户主页）；退场动画由页内 closing 流程自治
    // \u7528\u6237\u4e3b\u9875\u6808\uff1a\u5c42\u5c42\u53e0\u52a0\uff08\u6bcf\u5c42\u72ec\u7acb\u7ec4\u5408\uff0c\u8fd4\u56de\u9010\u5c42\u5f39\u51fa\uff09
    val userPageStack = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    var videoFullscreen by remember { mutableStateOf(false) }
    // 楼中楼：sheet 栈（逐层递进：根楼层 → 子回复 → 孙回复…，每层一个半屏 sheet）
    // 数据来自父页面 floorStates（以 pid 为 key，全层级共用一套缓存）
    var floorStack by remember { mutableStateOf<List<HupuReply>>(emptyList()) }
    // 1.129: 本机刚发送的楼中楼回复（乐观覆盖层，key=被回复楼层 pid）。
    // 楼中楼数据来自父页面 floorStates，openFloor 命中缓存会早退、未命中才网络拉取；
    // 服务端有分钟级延迟 → 会出现「发送后我的评论不显示」。这里单独存一份乐观数据，
    // 渲染时与 floorStates 合并（按 pid 去重），保证自己刚发的评论始终可见。
    val optimisticSubs = remember { androidx.compose.runtime.mutableStateMapOf<String, List<HupuSubReply>>() }
    LaunchedEffect(tid) { optimisticSubs.clear() }
    // 正在播放关闭动画的层（pid 集合）：多层可同时关闭；快速连点级联逐层退出。
    // 用 pid 而非 index 标识：快速返回时栈随时变化，index 会漂移导致 subList 越界崩溃。
    var closingPids by remember { mutableStateOf<Set<String>>(emptySet()) }

    // ---------- 登录三件套状态（1.36：点亮/推荐/发评论） ----------
    val scope = rememberCoroutineScope()
    val replyLightState = remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }
    // 乐观插入：发送成功的回复先行上屏（服务端 SSR 对匿名页有
    // 分钟级延迟，不能等刷新）；服务端真 pid 出现后自动顶替本地副本
    var localReplies by remember { mutableStateOf<List<HupuReply>>(emptyList()) }
    // 楼层楼中楼计数乐观增量（1.56）：主列表行「N 条回复」与 totalReplies=0 楼层的
    // 楼中楼入口出现均依赖此表；帖子刷新（detail 换新）时服务端计数已追平 → 清零
    val floorCountDelta = remember { androidx.compose.runtime.mutableStateMapOf<String, Int>() }
    // 清零锚 tid：detail 在翻页累积/下拉刷新时都会换新对象，锚它会让刚发的计数回落；
    // 服务端 replyNum 有分钟级延迟，窗口内偏高 1 无碍，退出/换帖自愈
    LaunchedEffect(tid) { floorCountDelta.clear() }
    // 推荐数乐观增量：+1 / -1，叠加在服务端值上
    var recommendDelta by remember { mutableStateOf(0) }
    var mainRecommended by remember { mutableStateOf(false) }
    // 1.67 云端收藏：打开时 GET 预查（失败静默 false）；点击乐观翻转 + 失败回滚
    var collected by remember { mutableStateOf(false) }
    var collectChecking by remember { mutableStateOf(false) }
    LaunchedEffect(tid) {
        // 1.68 收藏态：本地持久集秒显（无网络、登出即清）；登录态再 GET 云端校验，
        // 云端成功时覆盖本地（多端收藏一致性），GET 不可用/失败时以本地为准（官方 PC 亦无查询接口）
        collected = HupuAccount.isCollectedLocal(tid)
        if (HupuAccount.isLoggedIn) {
            // 云端三态：null=查询失败/未知(官方PC从不预查，GET 不可靠) → 保留本地，绝不覆盖
            when (val remote = HupuAccount.collectState(tid)) {
                null -> {}
                else -> if (remote != collected) {
                    collected = remote
                    HupuAccount.setCollectedLocal(tid, remote)
                }
            }
        }
    }
    var replyBoxOpen by remember { mutableStateOf(false) }
    var replyText by remember { mutableStateOf("") }
    // 1.73 表情面板 + 图片附件（最多9张）
    var emojiOpen by remember { mutableStateOf(false) }
    // 1.87 覆盖式面板: 面板全高恒定贴屏底, 键盘(系统窗口)天然盖其上——切面板只收
    // 键盘, 键盘滑走时逐帧揭幕已就位的面板(1.86 逐帧改高度在真机上更卡, 已回退)。
    // 峰值跟踪收敛在 snapshotFlow 里: 键盘动画期间只在读值自增时写一次状态,
    // emojiPanelDp 仅在峰值变化时重算, 不再整页每帧重组。
    val density = LocalDensity.current
    var lastImePx by remember { mutableStateOf(0) }
    // 1.92: 面板→键盘切换——翻转只由 IME 真实值驱动(监听器内), 不再猜动画时刻
    var panelToKeyboardPending by remember { mutableStateOf(false) }
    // 1.92: 切换目标高(=面板高, 在 tap 时冻结)。不能用 lastImePx 判定——键盘上升
    // 期间 lastImePx 逐帧跟着 ime 增长, 自比较恒真会导致第一帧就翻转(即"下去再上来")
    var panelTargetPx by remember { mutableStateOf(0) }
    // 1.87b: ViewTree insets 监听器(非组合 API)——键盘动画每帧回调里只做一次 int 比较,
    // 峰值自增才写状态(状态写入才触发重组), 键盘动画全程零组合开销。
    val ctx = androidx.compose.ui.platform.LocalContext.current
    DisposableEffect(Unit) {
        val view = (ctx as? android.app.Activity)?.window?.decorView ?: return@DisposableEffect onDispose { }
        val cb = android.view.View.OnApplyWindowInsetsListener { v, insets ->
            val ime = androidx.core.view.WindowInsetsCompat.toWindowInsetsCompat(insets, v)
                .getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom
            if (ime > lastImePx) lastImePx = ime
            // 1.93: 翻转判定移到 PanelToKeyboardWatcher(读 Compose 动画后的 ime,
            // 与输入行 padding 同源)——原始 listener 收到的是"终值, 提前到达",
            // 会先于渲染值触发翻转 → 支撑掉下去再弹上来(即"下去再上来")。
            insets
        }
        view.setOnApplyWindowInsetsListener(cb)
        onDispose {
            view.setOnApplyWindowInsetsListener(null)
            // 1.96: 退出详情页时收起收藏气泡(全局状态, 避免残留)
            HupuPrefs.stickerBubbleUrl = null
        }
    }
    // 1.92: 翻转主路径在 insets 监听器内(IME 到面板高即翻); 这里只做超时兜底:
    // 悬浮键盘等不产生 IME 变化的场景, 或 show() 未生效时, 800ms 后兜底收口。
    LaunchedEffect(panelToKeyboardPending) {
        if (!panelToKeyboardPending) return@LaunchedEffect
        if (lastImePx <= 0) {
            // 无 IME 记录(悬浮键盘): 没有升起的键盘可等, 直接切换
            if (emojiOpen) emojiOpen = false
            panelToKeyboardPending = false
            return@LaunchedEffect
        }
        withTimeoutOrNull(800) {
            snapshotFlow { panelToKeyboardPending }.first { !it }
        }
        if (panelToKeyboardPending && emojiOpen) emojiOpen = false
        panelToKeyboardPending = false
    }
    val navPx = WindowInsets.navigationBars.getBottom(density)
    // 1.89: 面板高度直接 = 键盘峰值(底边到屏幕底, 伸手势条后面——微信式),
    // 输入行支撑 = 同值 → 输入行与面板严丝合缝(1.88 差一个导航条高导致空隙)
    val emojiPanelDp = if (lastImePx > 0) with(density) { lastImePx.toDp() } else 280.dp
    // 1.92: 输入行支撑 = max(IME, 导航条, 面板高[仅 emojiOpen])——布局期 union 取
    // 最大。面板开、键盘升起时 max 恒为面板高(钉死不动); 翻转移除面板高项时
    // IME 已 >= 面板高 → max 不变 → 任何时刻翻转都零跳变。
    val emojiPanelPx = with(density) { emojiPanelDp.roundToPx() }
    val replySupportInsets =
        if (emojiOpen) WindowInsets.ime.union(WindowInsets.navigationBars)
            .union(WindowInsets(bottom = emojiPanelPx))
        else WindowInsets.ime.union(WindowInsets.navigationBars)
    var replyImages by remember { mutableStateOf<List<Uri>>(emptyList()) }
    // 1.95: 收藏表情包——面板 tab 0=虎扑表情 / 1=我的表情；表情包走图片附件途径
    var emojiTab by remember { mutableStateOf(0) }
    // 1.191: 表情搜索半屏面板——open 控制挂载、closing 驱动退场动画（与楼中楼 sheet 同构）
    var stickerSearchOpen by remember { mutableStateOf(false) }
    var stickerSearchClosing by remember { mutableStateOf(false) }
    // 1.191: 表情搜索面板里「清空最近使用 / 最近在搜」的确认弹窗（null = 不显示）
    var stickerClearTarget by remember { mutableStateOf<String?>(null) }
    var stickerToDelete by remember { mutableStateOf<HupuSticker?>(null) }
    /** 表情包下载落盘中(防连点) */
    var stickerAdding by remember { mutableStateOf(false) }
    // 1.191: 表情包搜索（面板第三个 tab）——状态挂在页面，切 tab 不丢结果
    val stickerSearch = rememberStickerSearchController()
    var imageUploading by remember { mutableStateOf(false) }
    val replyCtx = androidx.compose.ui.platform.LocalContext.current
    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) replyImages = (replyImages + uris).distinct().take(9)
    }
    var replySending by remember { mutableStateOf(false) }
    var replyToast by remember { mutableStateOf<String?>(null) }
    var replyTid by remember { mutableStateOf("") }
    var replyTopicId by remember { mutableStateOf("") }
    // ---------- 1.55 楼中楼回复：引用目标（pid + 显示名；pid 空 = 回复主楼） ----------
    var replyQuotePid by remember { mutableStateOf("") }
    var replyQuoteName by remember { mutableStateOf("") }
    var fidOfThread by remember { mutableStateOf("") }
    val replyFocus = remember { FocusRequester() }
    LaunchedEffect(replyBoxOpen) {
        if (replyBoxOpen) {
            replyFocus.requestFocus()
            replyToast = null
        } else {
            // 1.85: 回复框经任意路径关闭(空白点击/发送/返回)统一复位表情面板,
            // 否则下次打开键盘+面板同时出现; 1.90: 一并取消面板→键盘切换等待
            emojiOpen = false
            panelToKeyboardPending = false
        }
    }
    LaunchedEffect(replyToast) {
        if (replyToast != null) {
            delay(2600)
            replyToast = null
        }
    }
    // 1.94: 收藏/删除表情反馈 2 秒自动消失
    LaunchedEffect(HupuPrefs.stickerToast) {
        val t = HupuPrefs.stickerToast
        if (t != null) {
            delay(2000)
            if (HupuPrefs.stickerToast == t) HupuPrefs.stickerToast = null
        }
    }
    // 1.96: 收藏气泡 3 秒无操作自动消失(点其他区域则立即消失, 见气泡层)
    LaunchedEffect(HupuPrefs.stickerBubbleUrl) {
        val b = HupuPrefs.stickerBubbleUrl
        if (b != null) {
            delay(3000)
            if (HupuPrefs.stickerBubbleUrl == b) HupuPrefs.stickerBubbleUrl = null
        }
    }

    fun onLight(reply: HupuReply, tid: String, fid: String) {
        if (!HupuAccount.isLoggedIn) {
            replyToast = "请先在「我的」页登录"
            return
        }
        val cur = replyLightState[reply.pid] ?: false
        val puid = reply.author?.puid ?: ""
        replyLightState[reply.pid] = !cur
        scope.launch {
            // 状态自愈：点亮撞 5003(已点亮)/取消撞 PC090003(本就未点亮) 均视为成功，
            // 服务端真相纠正本地态（跨会话点亮记忆丢失时不再出现「灰心点不动+toast null」死锁）
            val state = ArrayList<String>(1)
            val err = if (!cur) HupuAccount.lightReply(tid, reply.pid, state)
            else HupuAccount.cancelLightReply(tid, reply.pid, puid, fid, state)
            if (err != null) {
                replyLightState[reply.pid] = cur
                replyToast = err
            } else if (state.isNotEmpty()) {
                replyLightState[reply.pid] = state.first() == "lit"
            }
        }
    }
    // 楼中楼点亮（1.57）：目标可以是层内子回复或 sheet 母楼——puid 取目标作者 uid
    fun onLightSub(targetPid: String, targetPuid: String, tid: String, fid: String) {
        if (!HupuAccount.isLoggedIn) {
            replyToast = "请先在「我的」页登录"
            return
        }
        val cur = replyLightState[targetPid] ?: false
        replyLightState[targetPid] = !cur
        scope.launch {
            val state = ArrayList<String>(1)
            val err = if (!cur) HupuAccount.lightReply(tid, targetPid, state)
            else HupuAccount.cancelLightReply(tid, targetPid, targetPuid, fid, state)
            if (err != null) {
                replyLightState[targetPid] = cur
                replyToast = err
            } else if (state.isNotEmpty()) {
                replyLightState[targetPid] = state.first() == "lit"
            }
        }
    }

    fun onRecommend(tid: String, fid: String, on: Boolean) {
        if (!HupuAccount.isLoggedIn) {
            replyToast = "请先在「我的」页登录"
            return
        }
        mainRecommended = on
        recommendDelta = if (on) 1 else 0
        scope.launch {
            val err = HupuAccount.recommend(tid, fid, on)
            if (err != null) {
                mainRecommended = !on
                recommendDelta = 0
                replyToast = err
            }
        }
    }

    /**
     * 1.189: 收藏动作（主楼卡片与常驻操作条共用，避免两份实现漂移）。
     * 乐观翻转 → 失败回滚；未登录只提示不动作。
     */
    fun doCollect(tid: String, on: Boolean) {
        if (!HupuAccount.isLoggedIn) {
            replyToast = "请先在「我的」页登录"
            return
        }
        val prev = collected
        collected = on
        collectChecking = true
        scope.launch {
            val err = HupuAccount.threadCollect(tid, on)
            collectChecking = false
            if (err != null) {
                collected = prev  // 失败回滚
                replyToast = err
            } else {
                replyToast = if (on) "已收藏" else "已取消收藏"
            }
        }
    }

    // 楼中楼乐观插入定位：T=引用目标（任意深度）。返回 T 作为子回复所在的层条目；
    // T 为根楼层（floorStates 键命中）时返回 null——根楼层自己不在任何子列表里
    fun containingFloor(targetPid: String): Pair<String, HupuFloorReplies>? {
        for ((k, fr) in floorStates) {
            if (fr.subReplies.any { it.pid == targetPid }) return k to fr
        }
        return null
    }
    /**
     * 1.129: 楼中楼渲染数据 = 父页面 floorStates（网络/缓存） ⊕ optimisticSubs（本机刚发）。
     * 按 pid 去重、保持顺序；乐观条目在后，服务端真 pid 出现时以服务端为准。
     * floorStates 尚未到达但有乐观条目时，直接给出合成数据 → 第一帧就能看到自己刚发的评论。
     */
    fun floorDataFor(pid: String, parent: HupuReply): HupuFloorReplies? {
        val base = floorStates[pid]
        val extra = optimisticSubs[pid].orEmpty()
        if (extra.isEmpty()) return base
        return HupuFloorReplies(
            parent = base?.parent ?: parent,
            subReplies = mergeFloorSubs(base?.subReplies ?: emptyList(), extra),
            hasMore = base?.hasMore ?: false,
        )
    }
    /** 1.95: 表情包下载落盘为本地图片并加入图片附件（复用图片发送途径） */
    fun insertSticker(s: HupuSticker) {
        if (stickerAdding) return
        stickerAdding = true
        scope.launch {
            HupuPrefs.stickerToast = "表情包处理中…"
            // 1.165: 本地导入的表情直接读盘；远程收藏的表情走下载
            val bytes = if (isLocalStickerUrl(s.url)) {
                runCatching { java.io.File(s.url.removePrefix("file://")).readBytes() }.getOrNull()
            } else {
                // 1.191: adoutu 图片有防盗链，下载必须带 Referer（虎扑图床返回 null → 原样请求）
                HupuAccount.downloadImageBytes(s.url, stickerReferer(s.url))
            }
            if (bytes == null || bytes.isEmpty()) {
                stickerAdding = false
                HupuPrefs.stickerToast = "表情包下载失败"
                return@launch
            }
            val lower = s.url.lowercase()
            val ext = when {
                lower.contains(".gif") -> "gif"
                lower.contains(".png") -> "png"
                lower.contains(".webp") -> "webp"
                else -> "jpg"
            }
            val file = try {
                val dir = java.io.File(replyCtx.cacheDir, "stickers").apply { mkdirs() }
                val f = java.io.File(dir, "sticker_" + System.currentTimeMillis() + "." + ext)
                f.writeBytes(bytes)
                f
            } catch (e: Exception) {
                stickerAdding = false
                HupuPrefs.stickerToast = "表情包写入失败：" + (e.message ?: e.javaClass.simpleName)
                return@launch
            }
            replyImages = replyImages + Uri.fromFile(file)
            stickerAdding = false
            HupuPrefs.stickerToast = "已加入图片，发送时随回复一起上传"
        }
    }

    fun sendReply(tid: String, topicId: String) {
        if (replySending || imageUploading) return
        val text = replyText.trim()
        if (text.isEmpty() && replyImages.isEmpty()) return
        if (!HupuAccount.isLoggedIn) {
            replyToast = "请先在「我的」页登录"
            return
        }
        replySending = true
        val quotePid = replyQuotePid
        scope.launch {
            // 图片先上传拿 URL（顺序上传，失败跳过该张）
            val urls = ArrayList<String>()
            val uploadErrs = ArrayList<String>()
            if (replyImages.isNotEmpty()) {
                imageUploading = true
                val cr = replyCtx.contentResolver
                for (u in replyImages) {
                    try {
                        // 1.170: 带上限读取（>32MB 或读取失败 → null），避免超大图整段入内存
                        val bytes = HupuImage.readCapped(cr, u)
                        if (bytes == null) {
                            uploadErrs.add("图片过大或无法读取")
                            continue
                        }
                        // 1.157: 统一走 HupuImage 的魔数嗅探（MIME/后缀只兜底），
                        // GIF / 动画 WebP 原样上传——不再可能被误标成 jpeg 而丢掉动画
                        val img = HupuImage.prepareForUpload(
                            bytes,
                            cr.getType(u) ?: "",
                            u.lastPathSegment ?: "",
                        )
                        if (img == null) {
                            uploadErrs.add("不支持的图片格式")
                            continue
                        }
                        val r = HupuAccount.uploadReplyImage(img.bytes, img.ext, img.width, img.height)
                        if (r.url != null) {
                            urls.add(r.url)
                        } else uploadErrs.add(r.error ?: "未知错误")
                    } catch (e: Exception) {
                        uploadErrs.add("读取图片失败: " + (e.message ?: e.javaClass.simpleName))
                    }
                }
                imageUploading = false
                if (uploadErrs.any { it.contains("登录已过期") }) {
                    replySending = false
                    replyToast = "登录已过期，请在「我的」页重新登录"
                    return@launch
                }
                if (urls.isEmpty()) {
                    // 1.75: 失败原因直接可见（步骤+HTTP码），不再笼统提示
                    replySending = false
                    val reason = uploadErrs.firstOrNull() ?: "未知错误"
                    replyToast = if (text.isEmpty()) "图片上传失败：$reason" else "图片上传失败($reason)，本次仅发送文字"
                    return@launch
                }
                if (urls.size < replyImages.size) {
                    replyToast = "部分图片上传失败(${uploadErrs.firstOrNull() ?: ""})，已发送${urls.size}张"
                }
            }
            val pidOut = ArrayList<String>(1)
            val err = HupuAccount.createReply(tid, topicId, fidOfThread, text, pidOut, quotePid, urls)
            replySending = false
            if (err == null) {
                // 乐观插入：本地构造立即上屏（不等 SSR 分钟级延迟）；真实 pid 随后出现时去重顶替
                val pf = HupuAccount.profile
                val localPid = pidOut.firstOrNull() ?: "local-${System.currentTimeMillis()}"
                if (quotePid.isEmpty()) {
                    // 主楼回复：追加到楼层列表
                    localReplies = localReplies + HupuReply(
                        pid = localPid,
                        contentHtml = text.split("\n").joinToString("<br>") +
                            urls.joinToString("") { "<img src=\"$it\"/>" },
                        floor = 0,
                        lights = 0,
                        totalReplies = 0,
                        createdAtText = "刚刚",
                        isStarter = false,
                        location = "",
                        author = HupuAuthor(puid = "", name = pf?.name ?: "我", url = pf?.avatar ?: ""),
                    )
                } else {
                    // 楼中楼回复：官方树形语义——quoteId=T 的新回复是 T 的直接子级，
                    // 落在 T 自己的展开层；同层插入 quote=None，不带「回复 @」。
                    // 主列表行「N 条回复」+1（totalReplies=0 楼层由此获得楼中楼入口）
                    floorCountDelta[quotePid] = (floorCountDelta[quotePid] ?: 0) + 1
                    // ① 乐观子回复：写入独立覆盖层（不依赖 floorStates 缓存是否存在），
                    // 渲染时与网络/缓存数据合并 → 保证「我发的评论」立刻且稳定可见。
                    val newSub = HupuSubReply(
                        pid = localPid,
                        contentHtml = text.split("\n").joinToString("<br>") +
                            urls.joinToString("") { "<img src=\"$it\"/>" },
                        lights = 0,
                        nestedCount = 0,
                        createdAtText = "刚刚",
                        isStarter = false,
                        location = "",
                        author = HupuAuthor(puid = "", name = pf?.name ?: "我", url = pf?.avatar ?: ""),
                        quoteUser = "",
                    )
                    optimisticSubs[quotePid] = (optimisticSubs[quotePid] ?: emptyList()) + newSub
                    // ② T 是某层里的子回复 → 该层里 T 的 nestedCount +1（「展开 N+1 条回复」）；
                    // T 的真实子层在展开时由网络带回
                    containingFloor(quotePid)?.let { (k, fr) ->
                        val subs = fr.subReplies.map {
                            if (it.pid == quotePid) it.copy(nestedCount = it.nestedCount + 1) else it
                        }
                        floorStates[k] = fr.copy(subReplies = subs)
                    }
                    // ③ T 的 sheet 正开着（栈上）→ 母楼卡「全部回复 N」+1
                    floorStack = floorStack.map {
                        if (it.pid == quotePid) it.copy(totalReplies = it.totalReplies + 1) else it
                    }
                    // 1.128②A：发送成功后自动展开「被回复楼层」，让用户立刻看到新回复。
                    // 1.129 修正：用「压栈」而非「替换整栈」——回复深层子回复时不再丢掉上层上下文；
                    // 目标若已在栈上则跳过（该层已通过乐观覆盖层显示新回复）。
                    if (floorStack.none { it.pid == quotePid }) {
                        val target = detail?.replies?.firstOrNull { it.pid == quotePid }
                            ?: floorStates.values.asSequence()
                                .flatMap { it.subReplies.asSequence() }
                                .firstOrNull { it.pid == quotePid }
                                ?.let { sub ->
                                    HupuReply(
                                        pid = sub.pid,
                                        contentHtml = sub.contentHtml,
                                        floor = 0,
                                        lights = sub.lights,
                                        totalReplies = sub.nestedCount,
                                        createdAtText = sub.createdAtText,
                                        isStarter = sub.isStarter,
                                        location = sub.location,
                                        author = sub.author,
                                    )
                                }
                        if (target != null) {
                            val withDelta = target.copy(
                                totalReplies = target.totalReplies + (floorCountDelta[quotePid] ?: 0),
                            )
                            floorStack = floorStack + withDelta
                            onOpenFloor(withDelta)
                        }
                    }
                }
                replyText = ""
                replyImages = emptyList()
                emojiOpen = false
                replyBoxOpen = false
                replyQuotePid = ""
                replyQuoteName = ""
                replyToast = "回复成功"
            } else {
                replyToast = err
            }
        }
    }

    // 图片全屏查看器（正文/回复/楼中楼内任意图片点击进入；1.186: 同一条消息内可左右切换）
    var imageViewer by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    val openImage: (List<String>, Int) -> Unit = { urls, i -> imageViewer = urls to i }
    // 1.178: 结构化正文（赛事战报等）承载页
    var embedPage by remember { mutableStateOf<com.java.myapplication.data.HupuEmbed?>(null) }

    // 帖子详情页正在退出：楼中楼 sheet 栈同步折叠清空（防止退场中途残留孤儿层，
    // 其 onClosed 回调在组件销毁后仍操作已失效的栈导致崩溃）
    LaunchedEffect(closing) {
        if (closing) {
            floorStack = emptyList()
            closingPids = emptySet()
        }
    }

    // 系统返回手势：预测性返回（单一 handler 内部分流——多个 BackHandler 共存时后组合者
    // 永远优先，会把预测性动画整个吞掉，因此本组件只允许这一个 handler）。
    // - 楼中楼打开：手势提交后收起最顶层 sheet（sheet 自带 280ms 退场动画，无需页面跟手）
    // - 退场动画播放中：吞掉手势（防止漏到系统直接退出应用）
    // - 其余：页面跟随手指实时滑出（progress: 0→1 跟手），松手未过阈值弹回、过阈值退出
    PredictiveBackHandler { events ->
        if (closing) {
            events.collect { } // 吞掉
            return@PredictiveBackHandler
        }
        if (videoFullscreen) {
            // 全屏视频打开：返回手势仅退出全屏（不收 sheet、不退页面）
            events.collect { }
            videoFullscreen = false
            return@PredictiveBackHandler
        }
        if (stickerClearTarget != null) {
            // 1.191: 清空确认弹窗盖在搜索面板之上 → 返回手势先收弹窗（不动面板）
            events.collect { }
            stickerClearTarget = null
            return@PredictiveBackHandler
        }
        if (stickerSearchOpen) {
            // 1.191: 表情搜索面板在最上层 → 返回手势先收它（不退页面、不收回复框）
            events.collect { }
            if (!stickerSearchClosing) stickerSearchClosing = true
            return@PredictiveBackHandler
        }
        if (replyBoxOpen) {
            // 回复框打开：返回手势先收起回复框（不退页面）
            events.collect { }
            replyBoxOpen = false
            return@PredictiveBackHandler
        }
        if (floorStack.isNotEmpty()) {
            events.collect { } // 吞掉进度（sheet 收起动画自反馈）
            val topPid = floorStack.last().pid
            if (topPid !in closingPids) {
                closingPids = closingPids + topPid
            } else {
                // 顶层层已在退场中：改为关闭其下最近一层（保持连续后退节奏）
                val below = floorStack.lastOrNull { it.pid !in closingPids }
                if (below != null) {
                    closingPids = closingPids + below.pid
                }
            }
        } else if (userPageStack.isNotEmpty()) {
            // \u7528\u6237\u4e3b\u9875\u6808\u975e\u7a7a\uff1a\u8fd4\u56de\u624b\u52bf\u5148\u5f39\u51fa\u6808\u9876\u4e3b\u9875\uff08\u9875\u5185\u52a8\u753b\u81ea\u6cbb\uff09
            events.collect { }
            userPageStack.removeAt(userPageStack.lastIndex)
        } else {
            try {
                events.collect { event ->
                    // 手势进度实时驱动盖出动画（页面跟手右滑）
                    progress.snapTo(1f - event.progress)
                }
                // 越过系统提交阈值 → 播完剩余动画并退出
                progress.animateTo(0f, tween(120))
                onBack()
            } catch (e: CancellationException) {
                // 未过阈值松手 → 弹回原位
                progress.animateTo(1f, tween(200))
                throw e
            }
        }
    }

    // 1.190: 「滚动时自动隐藏底栏」——挂在根节点即可覆盖本页所有滚动（正文/回复列表、
    // 楼中楼 sheet 内的列表）；开启开关后手指上滑收起操作条、下滑弹出
    val autoHideBar = rememberAutoHideBarState()
    // 操作条「栏底到屏幕底」的留白：与悬浮 Tab 栏同一分档（手势区在部分 ROM 虚高到 ≈47dp，
    // 直接用 navigationBarsPadding 会把栏顶得过高）
    val reservedBottomPx = maxOf(
        WindowInsets.navigationBars.getBottom(density),
        WindowInsets.displayCutout.getBottom(density),
    )
    val actionBarBottomPadPx = if (reservedBottomPx >= with(density) { TAB_BUTTON_NAV_MIN.roundToPx() }) {
        (reservedBottomPx + with(density) { TAB_GAP_BUTTON_EXTRA.roundToPx() }).toFloat()
    } else {
        maxOf(with(density) { TAB_GAP_GESTURE.roundToPx() }, reservedBottomPx).toFloat()
    }

    Box(
        Modifier
            .fillMaxSize()
            .zIndex(2f)
            .graphicsLayer { translationX = (1f - progress.value) * size.width }
            .background(MaterialTheme.colorScheme.background)
            // 穿透守卫：失败态/加载态空白点击由本层兜底，不落穿到下层页面
            .tapGuard()
            .autoHideScroll(autoHideBar),
    ) {
        if (videoFullscreen && videoHost.player != null) {
            FullscreenVideo(videoHost.player!!) { videoFullscreen = false }
        }
        val state = if (detail != null) "ok" else if (loading) "loading" else "error"
        Crossfade(targetState = state, animationSpec = tween(180), label = "threadDetailSwitch") { s ->
            when (s) {
                "loading" -> SkeletonHome()
                "error" -> ErrorRetry { onRefresh() }
                else -> {
                    val d = detail
                    if (d == null) {
                        ErrorRetry { onRefresh() }
                    } else {
                        val pullState = rememberPullToRefreshState()
                        // 记录当前帖子的写操作上下文（底部回复框在根层使用）
                        replyTid = d.thread.tid
                        replyTopicId = d.thread.topic?.topicId ?: ""
                        fidOfThread = d.thread.fid
                        PullToRefreshBox(
                            isRefreshing = loading,
                            onRefresh = onRefresh,
                            state = pullState,
                            modifier = Modifier
                                .fillMaxSize()
                                .layerBackdrop(actionBackdrop),
                        ) {
                            // 浏览流评论关键词过滤：命中的回复不显示（内容是 HTML，剥标签后判定）
                            val ckw = remember(HupuPrefs.filterVersion) { HupuPrefs.loadFilterKeywords() }
                            val filteredReplies = remember(d.replies, ckw) {
                                // 1.128：先剔除「回复某条评论」的子回复——官方 SSR 把它平铺进最外层，
                                // 但楼中楼里同样可见，属重复展示；真一级评论无 quote → quotePid 为空。
                                val topOnly = topLevelReplies(d.replies)
                                if (ckw.comment.isEmpty()) topOnly
                                else topOnly.filterNot { r -> HupuFilter.blockedComment(HupuFilter.stripHtml(r.contentHtml), ckw) }
                            }
                            // Reply toolbar state: only-OP filter + hot sort (stable, applied over keyword filter)
                            var onlyOp by remember { mutableStateOf(false) }
                            val hotSort = sortMode == 2
                            // 1.185: 切换排序的加载反馈（否则要等两次请求回来才突变，像卡住）
                            var sortSwitching by remember { mutableStateOf(false) }
                            // 1.185c: 期望方向以 sortMode 为准。每次「期望或数据」变化都上报一次意图，
                            // 父层据此推进序号（丢弃迟到的旧响应）并在方向不一致时才真正加载。
                            // 可自愈：连点来回闪、迟到乱序响应、二次进入残留旧方向。
                            LaunchedEffect(sortMode, detail?.descReplies) {
                                val wantDesc = sortMode == 1
                                onSortChanged(wantDesc)
                                sortSwitching = detail != null && detail.descReplies != wantDesc
                            }
                            // 兜底：加载失败时避免一直转圈
                            LaunchedEffect(sortSwitching) {
                                if (sortSwitching) {
                                    delay(8000)
                                    sortSwitching = false
                                }
                            }
                            // 乐观楼层 + 服务端楼层合并：page 翻页累积时真 pid
                            // 出现即去重顶替本地副本，不会双份
                            val displayReplies = remember(filteredReplies, onlyOp, hotSort, localReplies, floorCountDelta.toMap()) {
                                val realPids = filteredReplies.map { it.pid }.toHashSet()
                                var list = localReplies.filter { it.pid !in realPids } + filteredReplies
                                val dl = floorCountDelta.toMap()
                                if (dl.isNotEmpty()) list = list.map { r -> dl[r.pid]?.let { d -> r.copy(totalReplies = r.totalReplies + d) } ?: r }
                                if (onlyOp) list = list.filter { r -> r.isStarter }
                                if (hotSort) list = list.sortedByDescending { it.lights }
                                // 1.129 防闪退：LazyColumn 用 pid 作 key，任何来源（乐观/翻页合并/刷新）
                                // 万一出现重复 pid 都会抛「Key already used」导致闪退——这里统一兜底去重。
                                list.distinctBy { it.pid }
                            }
                            // Only-OP fast-scan: auto-chain next page (~120ms cadence) so OP
                            // floors stream in one by one instead of a bare spinner; each
                            // landed page recomposes displayReplies via detail.replies
                            LaunchedEffect(onlyOp, canLoadMore, loadingMore) {
                                if (onlyOp && canLoadMore && !loadingMore) {
                                    delay(120)
                                    onLoadMore()
                                }
                            }
                            // 1.128①B：最外层子回复被过滤后，若真一级评论偏少且还有下一页，
                            // 自动补拉下一页——避免「上面写了几十条回复，却只显示稀疏几条」。
                            // 仅按真一级评论数判定（不受关键词过滤影响）。
                            val realTopCount = remember(d.replies) { d.replies.count { r -> r.quotePid.isEmpty() } }
                            LaunchedEffect(realTopCount, canLoadMore, loadingMore, onlyOp) {
                                if (needAutoFillTop(realTopCount, canLoadMore, loadingMore, onlyOp)) {
                                    delay(120)
                                    onLoadMore()
                                }
                            }
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(
                                    start = 16.dp, end = 16.dp, top = 4.dp, bottom = 140.dp,
                                ),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                // 1.190: 列表「视口」整体从状态栏下方开始。此前只有 item("header")
                                // 自带 statusBarsPadding，一旦滚到别处（如点评论数跳到 item(2) 的
                                // 「回复 N」），该 item 会停在屏幕物理顶端被状态栏压住。把避让提到
                                // 视口层后，任何滚动/跳转停下的 item 都不会进入状态栏区域。
                                modifier = Modifier
                                    .fillMaxSize()
                                    .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top)),
                            ) {
                                item(key = "header") {
                                    ThreadHeader(
                                        d.thread.topic?.name.orEmpty(),
                                        onBack,
                                        // 1.119: 本人帖显示删除 + 编辑入口
                                        onDelete = if (
                                            HupuAccount.isLoggedIn &&
                                                !d.thread.author?.euid.isNullOrEmpty() &&
                                                d.thread.author.euid == HupuAccount.profile?.euid
                                        ) {
                                            { showDeleteConfirm = true }
                                        } else {
                                            null
                                        },
                                        onEdit = if (
                                            HupuAccount.isLoggedIn &&
                                                !d.thread.author?.euid.isNullOrEmpty() &&
                                                d.thread.author.euid == HupuAccount.profile?.euid
                                        ) {
                                            { editingTid = d.thread.tid }
                                        } else {
                                            null
                                        },
                                    )
                                }
                                item(key = "main") {
                                    MainPost(
                                        d,
                                        host = videoHost,
                                        isFullscreen = videoFullscreen,
                                        onToggleFullscreen = { videoFullscreen = it },
                                        onImageClick = openImage,
                                        onOpenUser = { pu -> if (pu.isNotEmpty()) { if (HupuAccount.isLoggedIn) userPageStack.add(pu) else replyToast = "请先在「我的」页登录" } },
                                        onToast = { replyToast = it },
                                        onOpenEmbed = { embedPage = it },
                                    )
                                }
                                item(key = "r-count") {
                                    Text(
                                        "回复 ${d.replyCount}",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                                    )
                                }
                                item(key = "r-tools") {
                                    Row(
                                        Modifier.fillMaxWidth().padding(top = 2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            "\u53ea\u770b\u697c\u4e3b",
                                            fontSize = 12.sp,
                                            fontWeight = if (onlyOp) FontWeight.SemiBold else FontWeight.Medium,
                                            color = if (onlyOp) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(
                                                    if (onlyOp) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                                    else MaterialTheme.colorScheme.surface
                                                )
                                                .clickable { onlyOp = !onlyOp }
                                                .padding(horizontal = 12.dp, vertical = 6.dp),
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(MaterialTheme.colorScheme.surface)
                                                .clickable { sortMode = (sortMode + 1) % 3 }
                                                .padding(horizontal = 12.dp, vertical = 6.dp),
                                        ) {
                                            if (sortSwitching) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(12.dp),
                                                    strokeWidth = 1.5.dp,
                                                    color = MaterialTheme.colorScheme.primary,
                                                )
                                                Spacer(Modifier.width(6.dp))
                                            }
                                            Text(
                                                when (sortMode) { 0 -> "\u9ed8\u8ba4\u987a\u5e8f"; 1 -> "\u6700\u65b0"; else -> "\u6700\u70ed" },
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                                items(displayReplies, key = { it.pid }) { r ->
                                    val opened = floorStates[r.pid]
                                    ReplyRow(
                                        r,
                                        onOpenFloor = {
                                            if (r.totalReplies > 0 || opened != null) {
                                                floorStack = listOf(r)
                                                onOpenFloor(r)
                                            }
                                        },
                                        onImageClick = openImage,
                                        onLight = { onLight(r, d.thread.tid, d.thread.fid) },
                                        isLit = replyLightState[r.pid] == true,
                                        onOpenUser = { pu -> if (pu.isNotEmpty()) { if (HupuAccount.isLoggedIn) userPageStack.add(pu) else replyToast = "\u8bf7\u5148\u5728\u300c\u6211\u7684\u300d\u9875\u767b\u5f55" } },
                                        onQuoteReply = {
                                            if (!HupuAccount.isLoggedIn) {
                                                replyToast = "\u8bf7\u5148\u5728\u300c\u6211\u7684\u300d\u9875\u767b\u5f55"
                                            } else {
                                                replyQuotePid = r.pid
                                                replyQuoteName = r.author?.name ?: ""
                                                replyBoxOpen = true
                                            }
                                        },
                                    )
                                }
                                if (displayReplies.isEmpty() && onlyOp && !canLoadMore && !loadingMore) {
                                    item(key = "r-empty-op") {
                                        Box(
                                            Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                "\u697c\u4e3b\u8fd8\u6ca1\u6709\u56de\u590d",
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                                if (canLoadMore) {
                                    item(key = "r-more") {
                                        Column(
                                            Modifier.fillMaxWidth().padding(vertical = 14.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            if (onlyOp) {
                                                androidx.compose.material3.LinearProgressIndicator(
                                                    progress = {
                                                        if (d.replyTotalPages > 0)
                                                            d.replyPage.toFloat() / d.replyTotalPages
                                                        else 0f
                                                    },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 24.dp)
                                                        .height(4.dp),
                                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                                )
                                                Spacer(Modifier.height(6.dp))
                                                Text(
                                                    "\u6b63\u5728\u7ffb\u627e\u697c\u4e3b\u697c\u5c42 " + d.replyPage + "/" + d.replyTotalPages + " \u9875",
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            } else if (loadingMore) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(22.dp),
                                                    strokeWidth = 2.dp,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 楼中楼 sheet 栈：每层一个半屏 sheet（逐层递进，母回复置顶）
        floorStack.forEachIndexed { index, floorReply ->
            FloorSheet(
                parent = floorReply,
                depth = index,
                data = floorDataFor(floorReply.pid, floorReply),
                loading = floorLoading.contains(floorReply.pid),
                loadingMore = floorLoadingMore.contains(floorReply.pid),
                closing = closingPids.contains(floorReply.pid),
                onBack = {
                    // 本层未在关闭中 → 关闭之（自顶向下找到本层之上未关闭的最近层）
                    if (!closingPids.contains(floorReply.pid)) {
                        closingPids = closingPids + floorReply.pid
                    } else {
                        // 本层已在退场：级联关闭——把本层之上的层也一并标记退出
                        val cascadeFrom = floorStack.indexOfFirst { it.pid == floorReply.pid }
                        if (cascadeFrom >= 0) {
                            closingPids = floorStack.drop(cascadeFrom).map { it.pid }.toSet()
                        }
                    }
                },
                onClosed = {
                    // 动画播完：安全移除该层及其之上的所有层（级联一起消失）
                    if (closingPids.contains(floorReply.pid)) {
                        val from = floorStack.indexOfFirst { it.pid == floorReply.pid }
                        if (from >= 0) {
                            val keep = floorStack.take(from)
                            floorStack = keep
                        }
                        // 清掉已不在栈上的孤儿 closing 标记：级联移除会取消其他层的动画协程，
                        // 其 onClosed 不会再触发，pid 若不清理会残留并误伤下一次同 pid 的展开
                        closingPids = closingPids.intersect(floorStack.map { it.pid }.toSet())
                    }
                },
                onLoadMore = { fr -> onFloorLoadMore(floorReply.pid, fr) },
                onImageClick = openImage,
                onOpenUser = { pu ->
                    if (pu.isNotEmpty()) {
                        if (HupuAccount.isLoggedIn) {
                            // 1.131: 从楼中楼进入用户主页时收起楼中楼面板（连同其上各层），
                            // 避免返回详情页后旧 sheet 仍残留在屏幕上
                            closingPids = emptySet()
                            floorStack = emptyList()
                            userPageStack.add(pu)
                        } else replyToast = "\u8bf7\u5148\u5728\u300c\u6211\u7684\u300d\u9875\u767b\u5f55"
                    }
                },
                onQuoteReply = { pid, name ->
                    if (!HupuAccount.isLoggedIn) {
                        replyToast = "\u8bf7\u5148\u5728\u300c\u6211\u7684\u300d\u9875\u767b\u5f55"
                    } else {
                        replyQuotePid = pid
                        replyQuoteName = name
                        replyBoxOpen = true
                    }
                },
                onLightSub = { pid, puid -> onLightSub(pid, puid, replyTid, fidOfThread) },
                subLightState = replyLightState,
                onOpenSub = { sub ->
                    // 子回复转 HupuReply 压栈：其楼层号继承父楼层（引用链同一棵树）
                    val asReply = HupuReply(
                        pid = sub.pid,
                        contentHtml = sub.contentHtml,
                        floor = floorReply.floor,
                        lights = sub.lights,
                        totalReplies = sub.nestedCount,
                        createdAtText = sub.createdAtText,
                        isStarter = sub.isStarter,
                        location = sub.location,
                        author = sub.author,
                    )
                    floorStack = floorStack + asReply
                    onOpenFloor(asReply)
                },
            )
        }

        // 1.189: 常驻操作条——把「写评论 / 评论数 / 推荐 / 收藏 / 分享」从「滚走即不可达」的
        // 主楼卡片与顶栏里提出来，收成一条贴底悬浮条。回复框（键盘/表情面板）打开时整条
        // 以 spring 收缩移出屏幕（与悬浮 Tab 栏同款出入场语言）。
        val barD = detail
        if (barD != null) {
            val barProgress by animateFloatAsState(
                // 1.189: 回复框打开（键盘/表情面板）或楼中楼 sheet 打开时，整条收缩移出屏幕；
                // 楼中楼是贴底半屏 sheet，操作条会与它叠在一起，必须一起隐藏。
                // 1.190: 用户主页（盖入式全屏页）打开时同样隐藏；开启「滚动时自动隐藏底栏」
                // 后，手指上滑（向下浏览）也会收起。
                // 1.190b: 补齐所有「全屏覆盖层」——图片查看器、视频全屏、结构化正文 WebView 页、
                // 编辑页，否则它们盖上来时操作条仍悬浮在底下（真机反馈：全屏看图/看视频时底栏还在）。
                targetValue =
                    if (replyBoxOpen || floorStack.isNotEmpty() || userPageStack.isNotEmpty() ||
                        autoHideBar.hidden || videoFullscreen || imageViewer != null ||
                        embedPage != null || editingTid != null
                    ) 1f else 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                label = "threadActionBar",
            )
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    // zIndex 996：在沉浸底条(997)/表情面板(998)/输入行(1000) 之下，
                    // 又在空白收起层(900) 之上——既不挡面板，也不被列表盖住
                    .zIndex(996f)
                    // 1.190: 位移必须按「含底部留白的整块高度」算——graphicsLayer 放在 padding
                    // 之前，size.height 才会包含留白；此前只算内容高度，三键导航下会露边
                    // （与 Tab 栏同款的「收起不完全」问题）。+30dp 余量兜住 spring 过冲。
                    .graphicsLayer {
                        translationY = (size.height + 30.dp.toPx()) * barProgress
                        alpha = 1f - barProgress
                    }
                    .padding(bottom = with(density) { actionBarBottomPadPx.toDp() })
                    // 1.190: 不再额外加 vertical padding——底边距必须与悬浮 Tab 栏**逐 dp 一致**
                    // （此前多留 8dp，真机看到「详情页底栏比主页 Tab 栏略高」）
                    .padding(horizontal = 12.dp),
            ) {
                ThreadActionBar(
                    backdrop = actionBackdrop,
                    replyCount = barD.replyCount,
                    recommendCount = barD.thread.lights + recommendDelta,
                    isRecommended = mainRecommended,
                    isCollected = collected,
                    onWrite = {
                        if (!HupuAccount.isLoggedIn) {
                            replyToast = "请先在「我的」页登录"
                        } else {
                            replyQuotePid = ""
                            replyQuoteName = ""
                            replyBoxOpen = true
                        }
                    },
                    // 跳到「回复 N」标题——列表第 3 项（header / main / r-count）
                    onJumpComments = {
                        scope.launch { runCatching { listState.animateScrollToItem(2) } }
                    },
                    onRecommend = { onRecommend(barD.thread.tid, barD.thread.fid, !mainRecommended) },
                    onCollect = { doCollect(barD.thread.tid, !collected) },
                    onShare = {
                        shareThreadToSystem(
                            replyCtx,
                            barD.thread.title,
                            "https://bbs.hupu.com/${barD.thread.tid}.html",
                        )
                    },
                )
            }
        }

        // 底部回复框（1.57 重构：盖在一切层级之上——楼中楼 sheet 之上 zIndex 3f+N，
        // 这里用 1000f 确保盖顶；新增收起按钮 + 点击 sheet 外空白收起 + 引用条）
        // 1.93: 面板→键盘翻转判定器——读 Compose 动画后的 ime(与输入行 padding
        // 同源)。原始 listener 的 ime 是终值提前到达, 先于渲染值 → 支撑先掉再弹。
        PanelToKeyboardWatcher(
            active = panelToKeyboardPending && emojiOpen,
            targetPx = panelTargetPx,
            navPx = navPx,
            onReached = {
                if (emojiOpen) emojiOpen = false
                panelToKeyboardPending = false
            },
        )
        if (replyBoxOpen) {
            // 逐帧揭幕; 输入行同帧随 insets 回落, 天然同步
            if (emojiOpen) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .zIndex(998f)
                        .fillMaxWidth()
                        .height(emojiPanelDp)
                        .background(MaterialTheme.colorScheme.surface)
                        // 1.166: 吞掉面板空白处（tab 行右侧、格子间隙、网格未铺满处）的点击，
                        // 避免穿透到下层「空白收起层」(900) 误关面板；格子/按钮自身的点击不受影响
                        .tapGuard(),
                ) {
                    Column(Modifier.fillMaxSize()) {
                        // 1.94: 表情面板双 tab——虎扑表情 / 我的表情(收藏表情包)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 12.dp, top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 1.191: 表情搜索入口（图标按钮，非 tab）——放在 tab 行最左侧；
                            // 点击弹出半屏搜索面板，同时收起表情面板避免两层面板叠加
                            StickerSearchEntry {
                                emojiOpen = false
                                panelToKeyboardPending = false
                                stickerSearchClosing = false
                                stickerSearchOpen = true
                            }
                            Spacer(Modifier.width(2.dp))
                            EmojiTabChip("虎扑表情", selected = emojiTab == 0) { emojiTab = 0 }
                            Spacer(Modifier.width(8.dp))
                            EmojiTabChip("我的表情", selected = emojiTab == 1) { emojiTab = 1 }
                            Spacer(Modifier.weight(1f))
                            if (stickerAdding) {
                                Text(
                                    "处理中…",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (emojiTab == 0) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(7),
                                contentPadding = PaddingValues(
                                    start = 10.dp,
                                    end = 10.dp,
                                    bottom = with(density) { navPx.toDp() } + 8.dp,
                                    top = 8.dp,
                                ),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                gridItems(HUPU_EMOJI.entries.toList()) { (token, url) ->
                                    AsyncImage(
                                        model = url,
                                        contentDescription = token,
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { replyText += token },
                                    )
                                }
                            }
                        } else {
                            val stickers =
                                remember(HupuPrefs.stickersVersion) { HupuPrefs.loadStickers() }
                            StickerPane(
                                stickers = stickers,
                                onClick = { insertSticker(it) },
                                onLongClick = { stickerToDelete = it },
                                bottomPad = with(density) { navPx.toDp() } + 8.dp,
                            )
                        }
                    }
                }
            }
            // 1.88: 空白收起层 zIndex 降到 900——原 999 全屏铺满会盖住面板(998)拦截
            // 表情点击; 现在它在面板之下, 点空白(列表区域)仍收回复框, 面板不受遮盖
            Box(
                Modifier
                    .matchParentSize()
                    .zIndex(900f)
                    .clickable(
                        indication = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    ) { replyBoxOpen = false },
            )
            // 1.152: 沉浸底条——输入框呼出后，窗口最底部（导航条/手势条「小白条」区域）
            // 此前完全没有绘制：系统手势条压在透明区域上，露出的是列表内容，
            // 观感是「空一条」而不是输入条的一部分。这里用输入条同色把这块补齐
            // （zIndex 997：在表情面板 998 / 输入行 1000 之下、空白收起层 900 之上）。
            // 键盘或表情面板自带绘制压在上面时它被自然遮住，不改变原有观感。
            val supportBottomPx = replySupportInsets.getBottom(density)
            if (supportBottomPx > 0) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .zIndex(997f)
                        .fillMaxWidth()
                        .height(with(density) { supportBottomPx.toDp() })
                        .background(MaterialTheme.colorScheme.surface),
                )
            }
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(1000f)
                    // 1.92: 输入行支撑 = union(ime, nav, 面板高[emojiOpen])——布局期取
                    // 最大, 与翻转时机无关: 键盘升起时恒为面板高, 翻转时 max 不变
                    .windowInsetsPadding(replySupportInsets),
            ) {
                ThreadReplyBox(
                    modifier = Modifier.fillMaxWidth(),
                    text = replyText,
                    onTextChange = { replyText = it },
                    sending = replySending,
                    loggedIn = HupuAccount.isLoggedIn,
                    focusRequester = replyFocus,
                    quoteName = replyQuoteName,
                    onCancelQuote = {
                        replyQuotePid = ""
                        replyQuoteName = ""
                    },
                    emojiOpen = emojiOpen,
                    // 1.90: 表情按钮切换取消进行中的面板→键盘等待(竞态收口);
                    // tap 输入框改走 onPanelToKeyboard(先升键盘后撤面板)
                    onToggleEmoji = { panelToKeyboardPending = false; emojiOpen = !emojiOpen },
                    onPanelToKeyboard = { panelTargetPx = maxOf(lastImePx, emojiPanelPx); panelToKeyboardPending = true },
                    images = replyImages,
                    uploading = imageUploading,
                    onAddImages = { pickImages.launch("image/*") },
                    onRemoveImage = { replyImages = replyImages - it },
                    onSend = { sendReply(replyTid, replyTopicId) },
                    onCollapse = { replyBoxOpen = false },
                )
            }
            // 1.191: 表情搜索半屏面板——盖过回复框(1000)/楼中楼 sheet 一切层级；
            // 不避让键盘（键盘作为系统窗口盖在面板下半部分，用户自行收键盘看全）
            if (stickerSearchOpen) {
                StickerSearchSheet(
                    controller = stickerSearch,
                    closing = stickerSearchClosing,
                    onPick = {
                        insertSticker(it)
                        // 1.191: 选中即清空并收起面板（微信式：选完就回到原页面）
                        stickerSearch.clear()
                        if (!stickerSearchClosing) stickerSearchClosing = true
                    },
                    onRequestClose = { if (!stickerSearchClosing) stickerSearchClosing = true },
                    onClosed = {
                        stickerSearchOpen = false
                        stickerSearchClosing = false
                        stickerClearTarget = null
                    },
                    bottomPad = with(density) { navPx.toDp() } + 8.dp,
                    clearTarget = stickerClearTarget,
                    onClearRequest = { stickerClearTarget = it },
                    onClearDismiss = { stickerClearTarget = null },
                )
            }
        }
        // 1.152: 长按图片的收藏气泡——带底部小箭头尾巴、与图片水平居中、贴在图片上方。
        // 结构改动（修「点收藏不执行收藏」）：遮罩与气泡改为**同层兄弟**。
        // 旧结构里气泡是遮罩的子节点，遮罩用 awaitFirstDown(requireUnconsumed = false)
        // 在 Main 阶段会先于子节点 clickable 收到按下，直接把 stickerBubbleUrl 置空
        // → 气泡在 up 之前就被移出组合，clickable 永远等不到抬手，收藏自然不执行。
        // 现在遮罩用默认 requireUnconsumed = true：气泡本体消费掉按下时遮罩不触发，
        // 按到别处才收起。
        val bubbleUrl = HupuPrefs.stickerBubbleUrl
        if (bubbleUrl != null) {
            val rect = HupuPrefs.stickerBubbleRect
            val bd = LocalDensity.current
            val gapPx = with(bd) { 6.dp.toPx() }
            val edgePx = with(bd) { 6.dp.toPx() }
            val minTopPx = with(bd) { 72.dp.toPx() }
            var bubbleSize by remember { mutableStateOf(IntSize.Zero) }
            var rootSize by remember { mutableStateOf(IntSize.Zero) }
            val appear = remember(bubbleUrl) { Animatable(0f) }
            LaunchedEffect(bubbleUrl) {
                appear.animateTo(
                    1f,
                    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                )
            }
            // 遮罩：只在按到气泡以外时收起（气泡自己消费按下事件）
            Box(
                Modifier
                    .matchParentSize()
                    .zIndex(940f)
                    .onSizeChanged { rootSize = it }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown()
                            HupuPrefs.stickerBubbleUrl = null
                        }
                    },
            )
            // 气泡层：整层不挂 pointerInput，只有气泡本体可点，其余区域自动穿透到遮罩
            Box(Modifier.matchParentSize().zIndex(941f)) {
                val bw = bubbleSize.width
                val totalH = bubbleSize.height
                // 上方放不下（贴屏幕顶部的图片）就翻到图片下方，尾巴朝上
                val showAbove = rect.top - totalH - gapPx >= minTopPx
                val by = if (showAbove) rect.top - totalH - gapPx else rect.bottom + gapPx
                val bx = (rect.center.x - bw / 2f)
                    .coerceIn(edgePx, (rootSize.width - bw - edgePx).coerceAtLeast(edgePx))
                val measured = bubbleSize != IntSize.Zero
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .offset { IntOffset(bx.roundToInt(), by.roundToInt()) }
                        .onSizeChanged { bubbleSize = it }
                        .graphicsLayer {
                            // 首帧还没量到尺寸时先不画（避免半宽错位闪一下）
                            alpha = if (measured) appear.value.coerceIn(0f, 1f) else 0f
                            val s = 0.85f + 0.15f * appear.value
                            scaleX = s
                            scaleY = s
                        },
                ) {
                    if (!showAbove) BubbleTail(up = true)
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(Color(0xFF303030).copy(alpha = 0.94f))
                            .clickable {
                                HupuPrefs.stickerBubbleUrl = null
                                collectSticker(bubbleUrl)
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text("收藏", color = Color.White, fontSize = 13.sp)
                    }
                    if (showAbove) BubbleTail(up = false)
                }
            }
        }
        // 提示条（点亮/推荐/发评论/收藏表情反馈；点击立即消失）
        (replyToast ?: HupuPrefs.stickerToast)?.let { msg ->
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(9f)
                    .statusBarsPadding()
                    .padding(top = 64.dp),
            ) {
                Text(
                    msg,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f))
                        .clickable { replyToast = null; HupuPrefs.stickerToast = null }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        // 1.119: 删除帖子确认弹窗（1.191: 换成 Liquid Glass 同款外观）
        if (showDeleteConfirm) {
            LiquidGlassDialog(
                backdrop = actionBackdrop,
                title = "删除帖子",
                message = "删除后无法恢复，确定要删除这篇帖子吗？",
                confirmText = "删除",
                dismissText = "取消",
                onConfirm = {
                    scope.launch {
                        val err = HupuPostApi.deleteThread(tid)
                        if (err == null) {
                            replyToast = "已删除"
                            delay(500)
                            onBack()
                        } else {
                            replyToast = err
                        }
                    }
                },
                onDismiss = { showDeleteConfirm = false },
                zIndex = 1100f,
            )
        }
        // 1.94: 删除收藏表情确认弹窗（1.191: 换成 Liquid Glass 同款外观）
        stickerToDelete?.let { st ->
            LiquidGlassDialog(
                backdrop = actionBackdrop,
                title = "删除表情",
                message = "确定从「我的表情」中移除这个表情吗？",
                confirmText = "删除",
                dismissText = "取消",
                onConfirm = {
                    HupuPrefs.removeSticker(st.url)
                    HupuPrefs.stickerToast = "已删除"
                },
                onDismiss = { stickerToDelete = null },
                zIndex = 1100f,
            )
        }
        // 用户主页（盖入式三级页：详情 → 用户主页；返回手势已在上方分流）
        userPageStack.forEachIndexed { si, subEuid ->
            androidx.compose.runtime.key(si) {
                UserProfilePage(
                    euid = subEuid,
                    onClose = { userPageStack.removeAt(si) },
                )
            }
        }
        // 图片全屏查看器（zIndex 10 盖过一切层级；点击图片任意处进入，单击/返回键/关闭按钮退出）
        imageViewer?.let { (urls, i) ->
            com.java.myapplication.ui.components.ImageViewer(urls = urls, initialIndex = i) { imageViewer = null }
        }
        // 1.178: 结构化正文（赛事战报等）承载页——盖入式二级页，按 url 键控（重开重建 WebView）
        embedPage?.let { e ->
            androidx.compose.runtime.key(e.url) {
                EmbedWebPage(e) { embedPage = null }
            }
        }
        // 1.112: 编辑页（盖入式，与用户主页同级的嵌套二级页）
        editingTid?.let { etid ->
            NewPostPage(
                editTid = etid,
                onClose = { editingTid = null },
                onPosted = {
                    editingTid = null
                    replyToast = "已保存"
                    onRefresh()
                },
            )
        }
    }
}
