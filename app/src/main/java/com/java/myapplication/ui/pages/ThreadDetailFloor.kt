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
    onImageClick: (String) -> Unit = {},
    onOpenUser: (String) -> Unit = {},
    onQuoteReply: (String, String) -> Unit = { _, _ -> },
    onLightSub: (String, String) -> Unit = { _, _ -> },
    subLightState: Map<String, Boolean> = emptyMap(),
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
                .fillMaxHeight(0.80f)
                .graphicsLayer { translationY = (1f - progress.value) * size.height }
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(MaterialTheme.colorScheme.background)
                .tapGuard(),
        ) {
            // 抓手条
            Box(
                Modifier
                    .padding(top = 8.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                    .align(Alignment.CenterHorizontally),
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
                            val floorFilteredSubs = remember(fr.subReplies, fkw) {
                                val base = if (fkw.comment.isEmpty()) fr.subReplies
                                else fr.subReplies.filterNot { sub -> HupuFilter.blockedComment(HupuFilter.stripHtml(sub.contentHtml), fkw) }
                                // 1.129 防闪退：子回复列表同样以 pid 为 key，兜底去重
                                base.distinctBy { it.pid }
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
                                                color = if (parent.isStarter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
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
                                        HtmlContent(parent.contentHtml, onImageClick = onImageClick)
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
internal fun SubReplyRow(sub: HupuSubReply, onExpand: () -> Unit, onImageClick: (String) -> Unit = {}, onOpenUser: (String) -> Unit = {}, onQuoteReply: () -> Unit = {}, onLight: () -> Unit = {}, isLit: Boolean = false) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
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
                color = if (sub.isStarter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
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
        HtmlContent(sub.contentHtml, onImageClick = onImageClick)
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
