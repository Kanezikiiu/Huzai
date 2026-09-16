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
import com.java.myapplication.data.HupuEmbedParser
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

/** 顶栏：返回＋居中专区名＋（本人帖的编辑）＋分享 */
@Composable
internal fun ThreadHeader(
    forumName: String,
    onBack: () -> Unit,
    shareText: String = "",
    shareUrl: String = "",
    /** 1.112: 非空时在分享左侧显示编辑入口（仅本人帖传入） */
    onEdit: (() -> Unit)? = null,
    /** 1.119: 非空时在编辑左侧显示删除入口（仅本人帖传入） */
    onDelete: (() -> Unit)? = null,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 8.dp, bottom = 10.dp),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .size(40.dp)
                .clip(CircleShape)
                .clickable { onBack() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "返回",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            forumName,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = when {
                    onDelete != null -> 144.dp
                    onEdit != null -> 96.dp
                    else -> 48.dp
                }),
        )
        // 1.119: 删除入口（仅本人帖）：放在编辑左侧
        if (onDelete != null) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 88.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { onDelete() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = "删除帖子",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        // 编辑入口（仅本人帖）：放在分享左侧
        if (onEdit != null) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 48.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { onEdit() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    HupuIcons.Edit,
                    contentDescription = "编辑帖子",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (shareUrl.isNotEmpty()) {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable {
                        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                android.content.Intent.EXTRA_TEXT,
                                if (shareText.isNotEmpty()) "$shareText $shareUrl" else shareUrl,
                            )
                        }
                        ctx.startActivity(android.content.Intent.createChooser(send, "分享帖子"))
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    HupuIcons.IosShare,
                    contentDescription = "分享",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** 主楼：作者行 + 正文 + 视频 + 元信息 */
@Composable
internal fun MainPost(
    d: HupuThreadDetail,
    host: VideoHost,
    isFullscreen: Boolean,
    onToggleFullscreen: (Boolean) -> Unit,
    onImageClick: (String) -> Unit = {},
    isRecommended: Boolean = false,
    recommendCount: Int = 0,
    onRecommend: (Boolean) -> Unit = {},
    isCollected: Boolean = false,
    onCollect: (Boolean) -> Unit = {},
    onReplyClick: () -> Unit = {},
    onOpenUser: (String) -> Unit = {},
    /** 1.121: 投票交互（未登录/提交失败）提示出口 */
    onToast: (String) -> Unit = {},
    /** 1.178: 结构化正文（赛事战报等）点击出口 */
    onOpenEmbed: (com.java.myapplication.data.HupuEmbed) -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val fs = remember(HupuPrefs.fontScaleVersion) { HupuPrefs.loadFontScale() }
        d.thread.author?.let { a ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .then(if (a.euid.isNotEmpty()) Modifier.clickable { onOpenUser(a.euid) } else Modifier),
            ) {
                // 作者头像（对齐评分页形态；无头像时隐藏）
                normalizeCover(a.url)?.let { avatar ->
                    AsyncImage(
                        model = avatar,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    a.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                if (d.thread.createdAtText.isNotBlank()) {
                    Text(
                        d.thread.createdAtText,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
                Text(
            d.thread.title,
            fontSize = (17 * fs).sp,
            fontWeight = FontWeight.Bold,
            lineHeight = (24 * fs).sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        // 1.178: 结构化正文（赛事战报等）——官方存的是 JSON 描述符而非 HTML，
        // 识别后渲染成卡片并「点开进 WebView」，避免把这段 JSON 当文本原样显示
        val embed = remember(d.contentHtml) { HupuEmbedParser.parse(d.contentHtml) }
        if (embed != null) {
            EmbedCard(embed) { onOpenEmbed(embed) }
        } else {
            // 1.122: 投票占位符在正文中内联渲染（与图片同机制）；正文无占位符（旧数据）时兜底挂末尾
            val voteInline = remember(d.contentHtml) {
                HupuParser.contentTokens(d.contentHtml).any { it is HupuParser.ContentToken.Vote }
            }
            HtmlContent(
                d.contentHtml,
                onImageClick = onImageClick,
                vote = d.vote,
                onToast = onToast,
            )
            if (d.vote != null && !voteInline) VoteCard(d.vote, onToast)
        }
        if (d.thread.hasVideo && d.thread.video.isNotBlank()) {
            VideoPlayer(
                url = d.thread.video,
                cover = d.thread.videoCover,
                host = host,
                isFullscreen = isFullscreen,
                onToggleFullscreen = onToggleFullscreen,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onRecommend(!isRecommended) }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                Icon(
                    if (isRecommended) Icons.Rounded.Favorite else Icons.Rounded.ThumbUp,
                    contentDescription = null,
                    tint = if (isRecommended) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    formatCount(recommendCount),
                    fontSize = 12.sp,
                    color = if (isRecommended) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(14.dp))
            // 1.67 云端收藏：星形（金色=已收藏，灰=未收藏），点击乐观翻转
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onCollect(!isCollected) }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                Icon(
                    Icons.Rounded.Star,
                    contentDescription = null,
                    tint = if (isCollected) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    if (isCollected) "已收藏" else "收藏",
                    fontSize = 12.sp,
                    color = if (isCollected) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(14.dp))
            Text(
                "浏览 ${formatCount(d.thread.read)}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onReplyClick() }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                Icon(
                    Icons.Rounded.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    "回复",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
