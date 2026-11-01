package com.java.myapplication.ui.pages

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
import com.java.myapplication.ui.components.SheetTopBar
import com.java.myapplication.ui.components.sheetTopCornerShape
import com.java.myapplication.ui.components.StickerAddCell
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.java.myapplication.data.HupuBlacklist
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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
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

/** 楼中楼单层：半屏 sheet（贴底 62% 高，顶部圆角，从底滑入；母回复置顶 + 子回复流） */
@Composable
internal fun FloorSheet(
    parent: HupuReply,
    depth: Int,
    data: HupuFloorReplies?,
    loading: Boolean,
    loadingMore: Boolean,
    closing: Boolean,
    onBack: () -> Unit,
    onClosed: () -> Unit,
    onLoadMore: (HupuFloorReplies) -> Unit,
    onOpenSub: (HupuSubReply) -> Unit,
    onImageClick: (List<String>, Int) -> Unit = { _, _ -> },
    onOpenUser: (String) -> Unit = {},
    onQuoteReply: (String, String) -> Unit = { _, _ -> },
    onLightSub: (String, String) -> Unit = { _, _ -> },
    subLightState: Map<String, Boolean> = emptyMap(),
    /**
     * 1.252：要定位到的**子回复 pid**（从消息通知进来、且目标在楼中楼里时传入）。
     * sheet 打开后会滚到该条并短暂高亮；若它还没加载进来，会自动继续翻页直到找到/穷尽。
     */
    focusPid: String = "",
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(280)) }
    LaunchedEffect(closing) {
        if (closing) {
            progress.animateTo(0f, tween(280))
            onClosed()
        }
    }

    val listState = rememberLazyListState()
    // 1.252：定位目标的高亮（约 1.2 秒后淡出）
    var hlPid by remember { mutableStateOf("") }
    val canLoadMore = data != null && data.hasMore
    val shouldLoadMore by remember(data, loadingMore) {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            canLoadMore && !loadingMore && last >= listState.layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && data != null) onLoadMore(data)
    }

    Box(
        Modifier
            .fillMaxSize()
            .zIndex(3f + depth)
            // 半屏遮罩：depth 越深越暗（每层叠 0.25 黑）
            .background(Color.Black.copy(alpha = 0.25f))
            .clickable(
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
            ) { onBack() },
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.86f)
                .graphicsLayer { translationY = (1f - progress.value) * size.height }
                .clip(sheetTopCornerShape())
                .background(MaterialTheme.colorScheme.background)
                .tapGuard(),
        ) {
            // 1.206b（真机反馈）：抓手条 + 左标题 + 右「kyant 毛玻璃圆形关闭按钮」
            // —— 去掉了分隔线与右侧计数，定义见 components/SheetChrome.kt
            SheetTopBar(
                title = "楼中楼",
                onClose = onBack,
            )
            val state = if (data != null) "ok" else if (loading) "loading" else "error"
            Crossfade(targetState = state, animationSpec = tween(180), label = "floorSwitch") { s ->
                when (s) {
                    "loading" -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                    "error" -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ErrorRetry { onBack() }
                    }
                    else -> {
                        val fr = data
                        if (fr == null) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                ErrorRetry { onBack() }
                            }
                        } else {
                            // 评论关键词过滤（楼中楼子回复）：composable 作用域计算
                            val fkw = remember(HupuPrefs.filterVersion) { HupuPrefs.loadFilterKeywords() }
                            // 1.221：本地黑名单——被拉黑者的子回复不显示；其深层回复只在该节点的
                            // 「展开 N 条回复」里可达（新压一层 sheet），节点被删即整棵不可达。
                            val floorFilteredSubs = remember(fr.subReplies, fkw, HupuPrefs.blacklistVersion) {
                                val base = if (fkw.comment.isEmpty()) fr.subReplies
                                else fr.subReplies.filterNot { sub -> HupuFilter.blockedComment(HupuFilter.stripHtml(sub.contentHtml), fkw) }
                                // 1.129 防闪退：子回复列表同样以 pid 为 key，兜底去重
                                HupuBlacklist.pruneSubReplies(base, HupuPrefs.loadBlacklist()).distinctBy { it.pid }
                            }
                            // 1.252：定位到目标子回复；没加载到就**继续翻页**（翻到找到或 hasMore=false 为止）。
                            LaunchedEffect(floorFilteredSubs, focusPid, loadingMore) {
                                if (focusPid.isBlank()) return@LaunchedEffect
                                val idx = floorFilteredSubs.indexOfFirst { it.pid == focusPid }
                                if (idx >= 0) {
                                    // 等 sheet 入场动画结束再滚（否则会被 animateScrollToItem 打断）
                                    delay(300)
                                    // 列表前 2 项是「母楼」「回复 N」，所以真实下标要 +2
                                    runCatching { listState.animateScrollToItem(idx + 2) }
                                    hlPid = focusPid
                                    delay(1200)
                                    if (hlPid == focusPid) hlPid = ""
                                    return@LaunchedEffect
                                }
                                val fr = data ?: return@LaunchedEffect
                                // 已经加载到但被「关键词过滤/黑名单」挡住了 → 停止翻页，避免无意义请求
                                if (fr.subReplies.any { it.pid == focusPid }) return@LaunchedEffect
                                if (fr.hasMore && !loadingMore) {
                                    // 还没加载完 → 继续翻页（完整翻到穷尽，不设页数上限）
                                    delay(160)
                                    onLoadMore(fr)
                                    return@LaunchedEffect
                                }
                            }
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(
                                    start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp,
                                ),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                item(key = "parent") {
                                    Column(
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(MaterialTheme.colorScheme.surface)
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically,
                                            modifier = parent.author?.takeIf { it.euid.isNotEmpty() }
                                                ?.let { a -> Modifier.clickable { onOpenUser(a.euid) } } ?: Modifier,
                                        ) {
                                            parent.author?.let { a ->
                                                normalizeCover(a.url)?.let { avatar ->
                                                    AsyncImage(
                                                        model = avatar,
                                                        contentDescription = null,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier
                                                            .size(28.dp)
                                                            .clip(CircleShape),
                                                    )
                                                }
                                                Spacer(Modifier.width(8.dp))
                                            }
                                            Text(
                                                parent.author?.name ?: "虎扑JR",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                            if (parent.isStarter) {
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                    "楼主",
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                                        .padding(horizontal = 5.dp, vertical = 1.dp),
                                                )
                                            }
                                            Spacer(Modifier.weight(1f))
                                            if (depth > 0) {
                                                Text(
                                                    "${parent.floor}楼 · 第${depth + 1}层",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            } else {
                                                Text(
                                                    "${parent.floor}楼 · 楼中楼",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                        // 1.226k：母楼同样按 pid 兜底（母楼本身可能就是视频回复）
                                        val parentFallback = remember(parent.pid, parent.videoUrl) {
                                            if (parent.videoUrl.isNotBlank()) null
                                            else com.java.myapplication.data.HupuReplyMediaIndex.videoOf(parent.pid)
                                        }
                                        // 1.233：母评论在楼中楼 sheet 里是**独立展示的一段**，没有整卡点击，
// 所以长按可以直接选字复制。
                                        SelectionContainer {
                                            HtmlContent(
                                                parent.contentHtml,
                                                onImageClick = onImageClick,
                                                videoUrl = parent.videoUrl.ifBlank { parentFallback?.first.orEmpty() },
                                                videoCover = parent.videoCover.ifBlank { parentFallback?.second.orEmpty() },
                                                audioUrl = parent.audioUrl,
                                                audioDurationSec = parent.audioDurationSec,
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                "全部回复 ${parent.totalReplies}",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Medium,
                                            )
                                            if (fr.hasMore) {
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    "上滑加载更多",
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                            Spacer(Modifier.weight(1f))
                                            // 点亮母楼（1.57）：与子回复/主列表同形态
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .clickable { onLightSub(parent.pid, parent.author?.puid ?: "") }
                                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                            ) {
                                                Icon(
                                                    if (subLightState[parent.pid] == true) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                                    contentDescription = null,
                                                    tint = if (subLightState[parent.pid] == true) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(13.dp),
                                                )
                                                Spacer(Modifier.width(3.dp))
                                                Text(
                                                    formatCount(if (subLightState[parent.pid] == true) parent.lights + 1 else parent.lights),
                                                    fontSize = 11.sp,
                                                    color = if (subLightState[parent.pid] == true) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                            Spacer(Modifier.width(10.dp))
                                            Text(
                                                "回复",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable { onQuoteReply(parent.pid, parent.author?.name ?: "") }
                                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                            )
                                        }
                                    }
                                }
                                item(key = "count") {
                                    Text(
                                        "回复 ${floorFilteredSubs.size}" + if (fr.hasMore) "，上滑加载更多" else "",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                                    )
                                }
                                items(floorFilteredSubs, key = { it.pid }) { sub ->
                                    SubReplyRow(
                                        sub,
                                        highlight = hlPid == sub.pid,
                                        onExpand = { onOpenSub(sub) },
                                        onImageClick = onImageClick,
                                        onOpenUser = onOpenUser,
                                        onQuoteReply = { onQuoteReply(sub.pid, sub.author?.name ?: "") },
                                        onLight = { onLightSub(sub.pid, sub.author?.puid ?: "") },
                                        isLit = subLightState[sub.pid] == true,
                                    )
                                }
                                if (canLoadMore) {
                                    item(key = "sub-more") {
                                        Box(
                                            Modifier.fillMaxWidth().padding(vertical = 14.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (loadingMore) {
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
    }
}

/** 楼中楼一条子回复（nestedCount>0 时整卡可点，压栈展开深层） */
@Composable
internal fun SubReplyRow(
    sub: HupuSubReply,
    onExpand: () -> Unit,
    onImageClick: (List<String>, Int) -> Unit = { _, _ -> },
    onOpenUser: (String) -> Unit = {},
    onQuoteReply: () -> Unit = {},
    onLight: () -> Unit = {},
    isLit: Boolean = false,
    /** 1.252：从通知定位进来时，目标子回复短暂高亮 */
    highlight: Boolean = false,
) {
    // 高亮底色淡入淡出（与一级评论同一套观感）
    val hlAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (highlight) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(if (highlight) 160 else 380),
        label = "subReplyHighlight",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f * hlAlpha))
            .then(
                if (sub.nestedCount > 0) Modifier.clickable { onExpand() } else Modifier
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = sub.author?.takeIf { it.euid.isNotEmpty() }
                ?.let { a -> Modifier.clickable { onOpenUser(a.euid) } } ?: Modifier,
        ) {
                // 头像（无头像时隐藏不占位）
                sub.author?.let { a ->
                    normalizeCover(a.url)?.let { avatar ->
                        AsyncImage(
                            model = avatar,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape),
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                }
            Text(
                sub.author?.name ?: "虎扑JR",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (sub.isStarter) {
                Spacer(Modifier.width(4.dp))
                Text(
                    "楼主",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
        }
        if (sub.quoteUser.isNotBlank()) {
            Text(
                "回复 @${sub.quoteUser}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        // 1.226k：渲染时**按 pid 兜底**。楼中楼接口不带 video，索引里却有；
        // 这样无论楼层数据来自哪条加载路径/哪份旧状态，都能显示出来。
        val fallbackVideo = remember(sub.pid, sub.videoUrl) {
            if (sub.videoUrl.isNotBlank()) null
            else com.java.myapplication.data.HupuReplyMediaIndex.videoOf(sub.pid)
        }
        // 1.232：与一级评论同一条规则 —— 有「展开孙评论」时整块可点，不启用选中；
        // 没有可点操作时才允许长按选字。
        val selectableSub = sub.nestedCount <= 0
        val contentBlock: @Composable () -> Unit = {
            HtmlContent(
                sub.contentHtml,
                onImageClick = onImageClick,
                videoUrl = sub.videoUrl.ifBlank { fallbackVideo?.first.orEmpty() },
                videoCover = sub.videoCover.ifBlank { fallbackVideo?.second.orEmpty() },
                audioUrl = sub.audioUrl,
                audioDurationSec = sub.audioDurationSec,
            )
        }
        if (selectableSub) SelectionContainer { contentBlock() } else contentBlock()
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (sub.createdAtText.isNotBlank()) {
                Text(sub.createdAtText, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (sub.location.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                Text(sub.location, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.weight(1f))
            // 点亮（1.57）：与主列表同形态——红心实/空（虎扑点亮官方语义），可点
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onLight() }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                Icon(
                    if (isLit) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = null,
                    tint = if (isLit) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    formatCount(if (isLit) sub.lights + 1 else sub.lights),
                    fontSize = 11.sp,
                    color = if (isLit) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(10.dp))
            // 回复该子回复（1.55）
            Text(
                "回复",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onQuoteReply() }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
        if (sub.nestedCount > 0) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "展开子回复",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    "展开 ${sub.nestedCount} 条回复",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
