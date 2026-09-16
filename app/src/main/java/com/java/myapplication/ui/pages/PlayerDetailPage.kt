package com.java.myapplication.ui.pages

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.Crossfade
import kotlin.coroutines.cancellation.CancellationException
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.layout.offset
import com.java.myapplication.data.HupuSticker
import com.java.myapplication.ui.components.HUPU_EMOJI
import com.java.myapplication.ui.components.HupuIcons
import com.java.myapplication.ui.components.StickerAddCell
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.java.myapplication.data.GrandExpandState
import com.java.myapplication.data.HupuFilter
import com.java.myapplication.data.HupuPrefs
import com.java.myapplication.data.HupuAccount
import com.java.myapplication.data.HupuScoreComment
import kotlinx.coroutines.delay
import com.java.myapplication.data.mergeWithOptimistic
import com.java.myapplication.data.flattenWithDescendants
import com.java.myapplication.data.HupuSelfDetail
import com.java.myapplication.data.ScoreCommentState
import com.java.myapplication.ui.components.EmojiText
import com.java.myapplication.ui.components.ErrorRetry
import com.java.myapplication.ui.components.HeroBadgeAvatar

import com.java.myapplication.ui.components.SkeletonHome
import com.java.myapplication.ui.components.normalizeImageUrl
import com.java.myapplication.ui.components.normalizeCover
import com.java.myapplication.ui.components.tapGuard

/**
 * 选手详情页（评分第 4 层，盖入式三级页）。
 * 内容：头像/均分/评分人数 + 评分分布条 + 热评 + 评论流（游标无限加载）。
 * 评论支持「最亮/最晚/最早」排序（官方 queryType：brightest 走 hottest 独立端点无分页，
 * latest=desc+now / earliest=asc+0 走 primarySingleRow）；评论的子回复点击展开半屏楼中楼
 * sheet（subCommentList 全量接口）。只做浏览：打分/发布评论需登录，不做。
 */
@Composable
fun PlayerDetailOverlay(
    bizType: String,
    bizNo: String,
    playerName: String,
    self: HupuSelfDetail?,
    comments: List<HupuScoreComment>,
    commentCount: Long,
    hasMore: Boolean,
    loadingMore: Boolean,
    loading: Boolean,
    closing: Boolean,
    onBack: () -> Unit,
    onClosed: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    sortKey: String = "latest",
    switching: Boolean = false,
    onSortChange: (String) -> Unit = {},
    subSheet: SubCommentSheetData? = null,
    onOpenSubSheet: (HupuScoreComment) -> Unit = {},
    subSheetLoading: Boolean = false,
    subSheetLoadingMore: Boolean = false,
    subSheetClosing: Boolean = false,
    onCloseSubSheet: () -> Unit = {},
    onClosedSubSheet: () -> Unit = {},
    onLoadMoreSub: () -> Unit = {},
    onSubSheetSortChange: (String) -> Unit = {},
    onSubSheetRetry: () -> Unit = {},
    onExpandGrand: (HupuScoreComment) -> Unit = {},
    // ---------- 1.64 评分 & 点亮（登录态） ----------
    scorePanelOpen: Boolean = false,
    onScorePanelOpen: (Boolean) -> Unit = {},
    myScore: Int = 0,
    onScoreSubmit: (Int, String) -> Unit = { _, _ -> },
    onScoreDelete: () -> Unit = {},
    scorePanelSubmitting: Boolean = false,
    onLightComment: (HupuScoreComment) -> Unit = {},
    lightState: Map<String, Boolean> = emptyMap(),
    // ---------- 1.145： 评分评论回复（登录态；回复嵌套进楼中楼，非帖子式新楼层） ----------
    /** 被回复评论（null = 未在回复）。母/子/孙均可 */
    replyTarget: HupuScoreComment? = null,
    replyText: String = "",
    replySending: Boolean = false,
    /** 1.150: 本机刚发送的回复（乐观层）：key = 被回复评论 commentId */
    optimisticReplies: Map<String, List<HupuScoreComment>> = emptyMap(),
    /** (root 母评论, target 被回复评论) */
    onReply: (HupuScoreComment, HupuScoreComment) -> Unit = { _, _ -> },
    onReplyTextChange: (String) -> Unit = {},
    onReplyCancel: () -> Unit = {},
    onReplySend: () -> Unit = {},
    // ---------- 1.146 回复框扩展：对齐帖子回复框（表情/表情包/图片） ----------
    /** 图片附件（Uri 列表，最多 9 张；发送时上传换 URL） */
    replyImages: List<android.net.Uri> = emptyList(),
    onAddReplyImages: () -> Unit = {},
    onRemoveReplyImage: (android.net.Uri) -> Unit = {},
    /** 图片上传中（发送按钮转圈 + 缩略图蒙层） */
    replyImageUploading: Boolean = false,
    /** 表情面板开（覆盖式：面板贴屏底，键盘盖其上） */
    replyEmojiOpen: Boolean = false,
    onReplyEmojiToggle: () -> Unit = {},
    /** 表情面板 tab：0=虎扑表情 1=我的表情 */
    replyEmojiTab: Int = 0,
    onReplyEmojiTabChange: (Int) -> Unit = {},
    /** 表情包处理中（防连点） */
    stickerAdding: Boolean = false,
    /** 插入表情包（下载落盘 → 加入图片附件） */
    onInsertSticker: (com.java.myapplication.data.HupuSticker) -> Unit = {},
    /** 长按表情包：删除确认 */
    onStickerLongClick: (com.java.myapplication.data.HupuSticker) -> Unit = {},
    /** 收藏表情包 toast（2s 自动消失，HupuPrefs 全局态） */
    stickerToast: String? = null,
    /** IME 峰值 px（面板高度 = 键盘峰值，微信式覆盖面板） */
    replyLastImePx: Int = 0,
    /** 面板→键盘切换中（点输入框先升键盘后撤面板） */
    replyPanelToKeyboardPending: Boolean = false,
    onReplyPanelToKeyboard: () -> Unit = {},
) {
    // 1.64 打分结果提示条（点击消失；2600ms 自动清除由 ScorePage 侧管理）
    // 图片全屏查看器（盖过页面/楼中楼一切层级；单击/返回键/关闭按钮退出）
    var viewerUrl by remember { mutableStateOf<String?>(null) }
    val openImage: (String) -> Unit = { url -> viewerUrl = url }
    // 1.163: 评分评论作者 → 用户主页（盖入式叠层；未登录给提示，与帖子详情同款交互）
    val userPageStack = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    var userToast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(userToast) {
        if (userToast != null) {
            delay(2000)
            userToast = null
        }
    }
    val openUser: (String) -> Unit = { pu ->
        if (pu.isNotEmpty()) {
            if (HupuAccount.isLoggedIn) userPageStack.add(pu) else userToast = "请先在「我的」页登录"
        }
    }
    // 1.164: 从楼中楼 sheet 里进主页 → 同时收起楼中楼面板（对齐帖子详情 1.131 的做法），
    // 否则返回评分详情后旧面板还残留在屏幕上
    val openUserFromSheet: (String) -> Unit = { pu ->
        if (pu.isNotEmpty()) {
            if (HupuAccount.isLoggedIn) {
                onCloseSubSheet()
                userPageStack.add(pu)
            } else userToast = "请先在「我的」页登录"
        }
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(280)) }
    LaunchedEffect(closing) {
        if (closing) {
            progress.animateTo(0f, tween(280))
            onClosed()
        }
    }
    // 系统返回手势：预测性返回（单一 handler——多个 handler 共存时后组合者永远优先）。
    // - 回复框打开：吞掉手势，先收回复框（不退页面）——1.146
    // - 楼中楼 sheet 打开时：吞掉手势（由 sheet 自身关闭逻辑接管，防止漏到关闭整页）
    // - 退场动画播放中：吞掉手势（防止漏到系统直接退出应用）
    // - 其余：页面跟随手指实时滑出（progress: 0→1 跟手），松手未过阈值弹回、过阈值退出
    PredictiveBackHandler { events ->
        if (replyTarget != null) {
            events.collect { } // 吞掉：先收回复框
            onReplyCancel()
            return@PredictiveBackHandler
        }
        if (closing || subSheet != null) {
            events.collect { } // 吞掉
            return@PredictiveBackHandler
        }
        if (userPageStack.isNotEmpty()) {
            // 1.163: 用户主页栈非空 → 返回手势先弹出栈顶主页（页内动画自治）
            events.collect { }
            userPageStack.removeAt(userPageStack.lastIndex)
            return@PredictiveBackHandler
        }
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

    val listState = rememberLazyListState()
    // 滚动到底部附近自动加载下一页（游标）
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && hasMore && !loadingMore && !loading) onLoadMore()
    }

    Box(
        Modifier
            .fillMaxSize()
            .zIndex(3f)
            .graphicsLayer { translationX = (1f - progress.value) * size.width }
            .background(MaterialTheme.colorScheme.background)
            // 穿透守卫：空白点击不落穿到下层页面
            .tapGuard(),
    ) {
        val state = if (self != null) "ok" else if (loading) "loading" else "error"
        Crossfade(targetState = state, animationSpec = tween(180), label = "playerDetailSwitch") { s ->
            when (s) {
                "loading" -> SkeletonHome()
                "error" -> ErrorRetry { onRefresh() }
                else -> {
                    val d = self
                    if (d == null) {
                        ErrorRetry { onRefresh() }
                    } else {
                        // 浏览流评论关键词过滤：命中的评论不显示（composable 作用域计算）
                        val ckw = remember(HupuPrefs.filterVersion) { HupuPrefs.loadFilterKeywords() }
                        val filteredComments = remember(comments, ckw) {
                            if (ckw.comment.isEmpty()) comments
                            else comments.filterNot { c -> HupuFilter.blockedComment(c.content, ckw) }
                        }
                        LazyColumn(
                            state = listState,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                start = 16.dp, end = 16.dp, top = 8.dp, bottom = 140.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            // 顶栏（吸附列表顶部）
                            item(key = "header") { PlayerHeader(d, onBack) }
                            // 评分对象介绍（通用评分层 infoJson.desc 为长文时；电竞层为空不渲染）
                            if (d.intro.isNotBlank()) {
                                item(key = "intro") { IntroCard(d.intro) }
                            }
                            // 评分分布
                            item(key = "dist") { DistributionCard(d) }
                            // 1.64 我的评分（打分入口；登录态在 ScorePage 判定）
                            item(key = "my-score") { MyScoreCard(myScore) { onScorePanelOpen(true) } }
                            // 热评
                            if (d.hottestComments.isNotEmpty()) {
                                item(key = "hot") { HottestCard(d.hottestComments) }
                            }
                            // 评论
                            item(key = "c-title") {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(
                                        "全部评论 $commentCount",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                                    )
                                    // 排序 tab：最亮 / 最晚 / 最早（对齐官方 ej 配置）
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
                                    ) {
                                        listOf(
                                            "brightest" to "最亮",
                                            "latest" to "最晚",
                                            "earliest" to "最早",
                                        ).forEach { (key, label) ->
                                            val active = key == sortKey
                                            Text(
                                                label,
                                                fontSize = 12.sp,
                                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier
                                                    .padding(end = 14.dp)
                                                    .clickable { onSortChange(key) },
                                            )
                                        }
                                    }
                                }
                            }
                            if (switching) {
                                // 1.59 切排序加载中：排序条下方小转圈（列表留旧数据，不闪「暂无评论」）
                                item(key = "c-switching") {
                                    Box(Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp)) {
                                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                                    }
                                }
                            }
                            if (filteredComments.isEmpty() && !loadingMore) {
                                item(key = "c-empty") {
                                    Text(
                                        "暂无评论",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 24.dp),
                                    )
                                }
                            }
                            items(filteredComments, key = { it.commentId }) { c ->
                                CommentRow(
                                    c,
                                    onOpenSub = { onOpenSubSheet(c) },
                                    onOpenUser = openUser,
                                    onImageClick = openImage,
                                    onLight = { onLightComment(c) },
                                    lightState = lightState,
                                    onReply = { onReply(c, c) },
                                    optimistic = optimisticReplies[c.commentId].orEmpty(),
                                )
                            }
                            if (hasMore) {
                                item(key = "c-more") {
                                    Box(
                                        Modifier.fillMaxWidth().padding(vertical = 14.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            if (loadingMore) "加载中…" else "",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 楼中楼 sheet：盖在详情页之上（zIndex 4；母评论置顶 + 全量子回复流）
        subSheet?.let { sheet ->
            SubCommentSheet(
                sheet = sheet,
                onOpenUser = openUserFromSheet,
                loading = subSheetLoading,
                loadingMore = subSheetLoadingMore,
                closing = subSheetClosing,
                onBack = onCloseSubSheet,
                onClosed = onClosedSubSheet,
                onLoadMore = onLoadMoreSub,
                onSortChange = onSubSheetSortChange,
                onRetry = onSubSheetRetry,
                onExpandGrand = onExpandGrand,
                onImageClick = openImage,
                onLightComment = onLightComment,
                lightState = lightState,
                onReply = onReply,
                optimisticReplies = optimisticReplies,
                // 1.146: sheet 内返回手势优先收回复框（回复框盖在 sheet 之上）
                replyBoxOpen = replyTarget != null,
                onDismissReplyBox = {
                    if (!replySending) onReplyCancel()
                },
            )
        }
        // 1.64 打分面板：盖在详情页/楼中楼之上（zIndex 5；iOS 风格居中卡）
        if (scorePanelOpen) {
            ScorePanelOverlay(
                myScore = myScore,
                submitting = scorePanelSubmitting,
                onSubmit = onScoreSubmit,
                onDelete = onScoreDelete,
                onClose = { onScorePanelOpen(false) },
            )
        }
        // 1.145/1.146 评分评论回复框：盖在楼中楼 sheet 之上。层级设计对齐帖子回复框：
        // 表情面板 7f → 空白收起层 5.9f（面板之下不遮表情点击，其余区域点下即收）→ 输入行 6f。
        // 面板→键盘翻转（PanelToKeyboardWatcher 同款语义）由宿主 ScorePage 驱动。
        replyTarget?.let { t ->
            // 覆盖式表情面板：贴屏底、高度 = 键盘峰值（微信式），键盘作为系统窗口盖其上方
            if (replyEmojiOpen) {
                val density = LocalDensity.current
                val emojiPanelDp = if (replyLastImePx > 0) with(density) { replyLastImePx.toDp() } else 280.dp
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .zIndex(7f)
                        .fillMaxWidth()
                        .height(emojiPanelDp)
                        .background(MaterialTheme.colorScheme.surface)
                        // 1.166: 吞掉面板空白处的点击，避免穿透到下层「空白收起层」(5.9f) 误关面板
                        .tapGuard(),
                ) {
                    Column(Modifier.fillMaxSize()) {
                        // 双 tab：虎扑表情 / 我的表情（收藏表情包）
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 12.dp, top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ScoreEmojiTabChip("虎扑表情", selected = replyEmojiTab == 0) { onReplyEmojiTabChange(0) }
                            Spacer(Modifier.width(8.dp))
                            ScoreEmojiTabChip("我的表情", selected = replyEmojiTab == 1) { onReplyEmojiTabChange(1) }
                            Spacer(Modifier.weight(1f))
                            if (stickerAdding) {
                                Text(
                                    "处理中…",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (replyEmojiTab == 0) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(7),
                                contentPadding = PaddingValues(
                                    start = 10.dp,
                                    end = 10.dp,
                                    bottom = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() } + 8.dp,
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
                                            .clickable { onReplyTextChange(replyText + token) },
                                    )
                                }
                            }
                        } else {
                            val stickers = remember(HupuPrefs.stickersVersion) { HupuPrefs.loadStickers() }
                            ScoreStickerPane(
                                stickers = stickers,
                                onClick = { onInsertSticker(it) },
                                onLongClick = { onStickerLongClick(it) },
                                bottomPad = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() } + 8.dp,
                            )
                        }
                    }
                }
            }
            // 空白收起层：面板(7f)之下、其余内容之上——点回复框外的任意区域即收起回复框
            Box(
                Modifier
                    .matchParentSize()
                    .zIndex(5.9f)
                    .clickable(
                        indication = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    ) { onReplyCancel() },
            )
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(6f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    // 1.167: 吞掉输入行内空隙/四周留白的点击，避免穿透到下层「空白收起层」(5.9f)
                    // 误关面板；面板(7f) 在本层之上，命中优先级更高，不受影响
                    .tapGuard()
                    // 1.146: 输入行支撑 = max(IME, 导航条, 面板高[仅表情开])——布局期 union 取最大，
                    // 面板开→键盘升起的任何时刻零跳变（与帖子回复框同构）
                    .windowInsetsPadding(
                        if (replyEmojiOpen) {
                            val panelPx = if (replyLastImePx > 0) replyLastImePx
                            else with(LocalDensity.current) { 280.dp.roundToPx() }
                            WindowInsets.ime.union(WindowInsets.navigationBars)
                                .union(WindowInsets(bottom = panelPx))
                        } else {
                            WindowInsets.ime.union(WindowInsets.navigationBars)
                        }
                    )
                    .padding(0.dp),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "回复 @${t.userName}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "取消",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = !replySending) { onReplyCancel() }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                val focus = remember { FocusRequester() }
                LaunchedEffect(t.commentId) { focus.requestFocus() }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 1.146 表情按钮：开面板前先收键盘（微信式互斥）
                    val keyboard = LocalSoftwareKeyboardController.current
                    Icon(
                        HupuIcons.EmojiMood,
                        contentDescription = "表情",
                        tint = if (replyEmojiOpen) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                if (!replyEmojiOpen) keyboard?.hide()
                                onReplyEmojiToggle()
                            }
                            .padding(4.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    // 1.146 图片按钮（数量徽标，最多 9 张）
                    Box {
                        Icon(
                            HupuIcons.ImageIcon,
                            contentDescription = "图片",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(enabled = replyImages.size < 9) { onAddReplyImages() }
                                .padding(4.dp),
                        )
                        if (replyImages.isNotEmpty()) {
                            Text(
                                "${replyImages.size}",
                                fontSize = 9.sp,
                                color = Color.White,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 2.dp, y = (-2).dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                                    .padding(horizontal = 3.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    // 1.146: tfvState 同步（对齐帖子回复框 1.82——表情 token 追加后光标落末尾）
                    var tfvState by remember { mutableStateOf(TextFieldValue(replyText)) }
                    if (tfvState.text != replyText) {
                        tfvState = TextFieldValue(replyText, TextRange(replyText.length))
                    }
                    BasicTextField(
                        value = tfvState,
                        onValueChange = { nv ->
                            tfvState = nv
                            onReplyTextChange(nv.text)
                        },
                        textStyle = TextStyle(
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.None,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        maxLines = 4,
                        minLines = 1,
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                inner()
                                if (replyText.isEmpty()) {
                                    Text(
                                        "友善回复，理性讨论…",
                                        style = TextStyle(
                                            fontSize = 14.sp,
                                            lineHeight = 20.sp,
                                            lineHeightStyle = LineHeightStyle(
                                                alignment = LineHeightStyle.Alignment.Center,
                                                trim = LineHeightStyle.Trim.None,
                                            ),
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .focusRequester(focus)
                            // 1.146: 面板开时点输入框 → 升键盘 + 面板→键盘切换（键盘逐帧盖住面板）
                            .pointerInput(replyEmojiOpen) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    if (replyEmojiOpen) {
                                        keyboard?.show()
                                        onReplyPanelToKeyboard()
                                    }
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    // 1.153: 发送按钮参数对齐帖子详情页——纯文字「发送」（14sp SemiBold、
                    // 16dp 圆角、无底色胶囊，不可发送时降到 onSurfaceVariant 50%），
                    // 忙时按钮位置显示同尺寸转圈（不再像卡住）
                    val scoreCanSend = canSendScoreReply(replyText, replyImages, replySending, replyImageUploading)
                    if (replySending || replyImageUploading) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .padding(horizontal = 13.dp, vertical = 7.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else {
                        Text(
                            "发送",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (scoreCanSend) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable(enabled = scoreCanSend) {
                                    keyboard?.hide()
                                    onReplySend()
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
                // 1.146 图片缩略图行（64dp 圆角 + ✕ 移除 + 上传蒙层）
                if (replyImages.isNotEmpty()) {
                    LazyRow(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(replyImages) { uri ->
                            Box {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                )
                                if (replyImageUploading) {
                                    Box(
                                        Modifier
                                            .size(64.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.Black.copy(alpha = 0.38f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = Color.White,
                                        )
                                    }
                                }
                                Text(
                                    "✕",
                                    fontSize = 11.sp,
                                    color = Color.White,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(18.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(Color.Black.copy(alpha = 0.55f))
                                        .clickable { onRemoveReplyImage(uri) },
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }
        // 1.146 收藏表情包 toast（2s 自动消失，宿主管理）
        stickerToast?.let { msg ->
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(30f)
                    .padding(bottom = 120.dp),
            ) {
                Text(
                    msg,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        // 1.163: 用户主页（评分评论作者入口；盖入式叠层，返回手势已在上方分流）
        userPageStack.forEachIndexed { si, subEuid ->
            androidx.compose.runtime.key(si) {
                // 1.164: 提层——楼中楼 sheet 的 zIndex 是 4，用户主页必须盖在它之上
                // （否则 pop 进去后旧 sheet 会压在主页上面；sheet 退场动画期间也由这层遮挡）
                Box(Modifier.fillMaxSize().zIndex(6f)) {
                    UserProfilePage(
                        euid = subEuid,
                        onClose = { userPageStack.removeAt(si) },
                    )
                }
            }
        }
        // 1.163: 用户主页未登录提示（2s 自动消失）
        userToast?.let { msg ->
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(31f)
                    .padding(bottom = 150.dp),
            ) {
                Text(
                    msg,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        // 图片全屏查看器：盖过一切（页面 + 楼中楼）
        viewerUrl?.let { url ->
            com.java.myapplication.ui.components.ImageViewer(url = url) { viewerUrl = null }
        }
    }
}
