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
import androidx.compose.foundation.layout.widthIn
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

/** HTML 内容块 */
internal sealed class HtmlBlock {
    data class Text(val text: String, val spans: List<HtmlSpan>) : HtmlBlock()
    data class Image(val url: String) : HtmlBlock()
    /** 1.122: 正文里的投票占位（<span data-type="vote" data-vote-id="...">），内联渲染投票卡 */
    data class Vote(val voteId: Int) : HtmlBlock()
}

/** 文本内联样式区间（剥标签后坐标） */
internal data class HtmlSpan(val start: Int, val end: Int, val bold: Boolean, val link: String?)

/**
 * HTML → 块列表（保序）：<img> 与投票占位 <span data-type="vote"> 提为独立块，
 * 其余按 </p>/<br> 分段。每段计算加粗/链接 span（剥标签后坐标）。
 * 顺序切分逻辑下沉到 HupuParser.contentTokens（可单测）。
 */
internal fun parseHtmlBlocks(html: String): List<HtmlBlock> {
    val blocks = mutableListOf<HtmlBlock>()
    for (t in HupuParser.contentTokens(html)) {
        when (t) {
            is HupuParser.ContentToken.Text -> appendTextBlocks(blocks, t.html)
            is HupuParser.ContentToken.Image -> blocks += HtmlBlock.Image(t.url)
            is HupuParser.ContentToken.Vote -> blocks += HtmlBlock.Vote(t.voteId)
        }
    }
    return blocks
}

/**
 * 从虎扑 CDN 图片 URL 解析固有像素尺寸。
 * 两种标记：`…_o_w_550_h_550_…`（官方表情）与 `…_w_866_h_865_…`（用户上传图）。
 * w/h 为 0 或无标记 → null（调用方走全宽兜底）。
 */
internal fun intrinsicSizeOfUrl(url: String): Pair<Float, Float>? {
    val m = Regex("""_o_w_(\d+)_h_(\d+)_""").find(url)
        ?: Regex("""_w_(\d+)_h_(\d+)_""").find(url)
        ?: return null
    val w = m.groupValues[1].toFloatOrNull() ?: return null
    val h = m.groupValues[2].toFloatOrNull() ?: return null
    return if (w > 0f && h > 0f) w to h else null
}

/** 一段含标签文本 → 若干 Text 块（</p>/<br> 分段） */
internal fun appendTextBlocks(blocks: MutableList<HtmlBlock>, s: String) {
    if (s.isBlank()) return
    val parts = s.split(Regex("</p>|<br\\s*/?>|</div>"), limit = 0)
    for (p in parts) {
        val parsed = parseTextSegment(p)
        if (parsed != null) blocks += parsed
    }
}

/**
 * 一段文本 → (纯文本, spans)。
 * 逐字符扫描：剥除标签，记录 <strong>/<b> 与 <a href> 区间（坐标=输出文本索引）。
 */
internal fun parseTextSegment(seg: String): HtmlBlock.Text? {
    val out = StringBuilder()
    val spans = mutableListOf<HtmlSpan>()
    var i = 0
    while (i < seg.length) {
        if (seg[i] == '<') {
            val close = seg.indexOf('>', i)
            if (close < 0) { i++ ; continue }
            val tag = seg.substring(i + 1, close).trim().lowercase()
            when {
                tag.startsWith("strong") || tag == "b" -> {
                    // 找闭合标签
                    val closeIdx = seg.lowercase().indexOf("</strong>", close).let {
                        if (it >= 0) it else seg.lowercase().indexOf("</b>", close)
                    }
                    if (closeIdx >= 0) {
                        val inner = parseTextSegment(seg.substring(close + 1, closeIdx))
                        if (inner != null) {
                            val start = out.length
                            out.append(inner.text)
                            spans += inner.spans.map { it.copy(start = it.start + start, end = it.end + start) }
                            spans += HtmlSpan(start, out.length, bold = true, link = null)
                            i = seg.lowercase().indexOf('>', closeIdx) + 1
                            continue
                        }
                    }
                    i = close + 1
                }
                tag.startsWith("a ") || tag == "a" -> {
                    val hrefMatch = Regex("""href\s*=\s*["']([^"']+)["']""").find(seg.substring(i, close + 1))
                    val closeIdx = seg.lowercase().indexOf("</a>", close)
                    if (closeIdx >= 0 && hrefMatch != null) {
                        val inner = parseTextSegment(seg.substring(close + 1, closeIdx))
                        if (inner != null) {
                            val start = out.length
                            out.append(inner.text)
                            spans += inner.spans.map { it.copy(start = it.start + start, end = it.end + start) }
                            spans += HtmlSpan(start, out.length, bold = false, link = hrefMatch.groupValues[1])
                            i = closeIdx + 4
                            continue
                        }
                    }
                    i = close + 1
                }
                else -> i = close + 1
            }
        } else {
            // HTML 实体解码（简化常见 6 种）
            when {
                seg.startsWith("&nbsp;", i) -> { out.append(' '); i += 6 }
                seg.startsWith("&amp;", i) -> { out.append('&'); i += 5 }
                seg.startsWith("&lt;", i) -> { out.append('<'); i += 4 }
                seg.startsWith("&gt;", i) -> { out.append('>'); i += 4 }
                seg.startsWith("&quo" + "t;", i) -> { out.append('"'); i += 6 }
                seg.startsWith("&#39;", i) -> { out.append('\''); i += 5 }
                else -> { out.append(seg[i]); i++ }
            }
        }
    }
    val text = out.toString().trim()
    if (text.isEmpty()) return null
    // 修正因 trim 引起的坐标偏移：只保留与最终文本有交集的 span 并裁剪
    val leadWs = out.length - out.toString().trimStart().length
    val trimmed = mutableListOf<HtmlSpan>()
    for (sp in spans) {
        val s2 = (sp.start - leadWs).coerceAtLeast(0)
        val e2 = (sp.end - leadWs).coerceAtMost(text.length)
        if (e2 > s2) trimmed += HtmlSpan(s2, e2, sp.bold, sp.link)
    }
    return HtmlBlock.Text(text, trimmed)
}

/** 1.152: 气泡底部小箭头尾巴（up=true 朝上，用于气泡被放到图片下方的情形） */
@Composable
internal fun BubbleTail(up: Boolean) {
    val tailColor = Color(0xFF303030).copy(alpha = 0.94f)
    Canvas(Modifier.size(width = 11.dp, height = 5.dp)) {
        val p = Path()
        if (up) {
            p.moveTo(size.width / 2f, 0f)
            p.lineTo(size.width, size.height)
            p.lineTo(0f, size.height)
        } else {
            p.moveTo(0f, 0f)
            p.lineTo(size.width, 0f)
            p.lineTo(size.width / 2f, size.height)
        }
        p.close()
        drawPath(p, tailColor)
    }
}

/** 1.95: 收藏表情包（长按图片 → 气泡「收藏」触发；仅图片，官方表情不收藏） */
internal fun collectSticker(url: String) {
    if (url.isEmpty()) return
    if (HupuPrefs.isStickerSaved(url)) {
        HupuPrefs.stickerToast = "该图片已在表情包中"
        return
    }
    if (HupuPrefs.addSticker(url)) HupuPrefs.stickerToast = "已收藏到表情包"
    else HupuPrefs.stickerToast = "收藏失败"
}

/** 位置持有器(非 state: 仅长按时读取, 不触发重组) */
internal class RectRef {
    var value: Rect = Rect.Zero
}

/** 1.96: 评论图片——长按上报位置(页面级气泡: 点其他区域可消失), 点图片看大图 */
@Composable
internal fun CommentImage(
    model: Any,
    url: String,
    imageModifier: Modifier,
    contentScale: ContentScale,
    onClick: () -> Unit,
) {
    val rectRef = remember { RectRef() }
    Box {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = contentScale,
            modifier = imageModifier
                .onGloballyPositioned { rectRef.value = it.boundsInRoot() }
                .combinedClickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        HupuPrefs.stickerBubbleUrl = null
                        onClick()
                    },
                    onLongClick = {
                        // 1.96: 长按 → 页面级气泡(点其他区域/3 秒后消失)
                        HupuPrefs.stickerBubbleRect = rectRef.value
                        HupuPrefs.stickerBubbleUrl = url
                    },
                ),
        )
    }
}

/** HTML 内容渲染：文本块 AnnotatedString + 图片块 AsyncImage（保序）；图片可点击全屏查看 */
@Composable
internal fun HtmlContent(
    html: String,
    modifier: Modifier = Modifier,
    onImageClick: (String) -> Unit = {},
    /** 1.122: 正文内联投票（主楼传入；回复 HTML 不传） */
    vote: HupuVote? = null,
    onToast: (String) -> Unit = {},
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val primary = MaterialTheme.colorScheme.primary
    val fs = remember(HupuPrefs.fontScaleVersion) { HupuPrefs.loadFontScale() }
    val blocks = remember(html) { parseHtmlBlocks(html) }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { b ->
            when (b) {
                is HtmlBlock.Image -> {
                    // 图片自适应：虎扑 CDN 图片 URL 自带真实像素尺寸（…_o_w_107_h_132_…），
                    // 直接从 URL 解析出固有宽高（无需等待网络加载），按内容宽度等比缩放：
                    // - 小图/表情包按真实尺寸显示（不放大到全宽）
                    // - 大图等比缩小至内容宽，高度封顶 320dp（超长截图截断预览）
                    val intrinsic = remember(b.url) { intrinsicSizeOfUrl(b.url) }
                    if (intrinsic != null) {
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val density = LocalDensity.current
                            val scale = minOf(
                                1f,
                                with(density) { maxWidth.toPx() } / intrinsic.first,
                                with(density) { 320.dp.toPx() } / intrinsic.second,
                            )
                            CommentImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(b.url)
                                    .crossfade(true)
                                    .build(),
                                imageModifier = Modifier
                                    .width(with(density) { (intrinsic.first * scale).toDp() })
                                    .height(with(density) { (intrinsic.second * scale).toDp() })
                                    .clip(RoundedCornerShape(8.dp)),
                                url = b.url,
                                contentScale = ContentScale.Fit,
                                onClick = { onImageClick(b.url) },
                            )
                        }
                    } else {
                        // 1.174: URL 无尺寸信息（多为本机新上传的图，虎扑 CDN 尺寸参数需服务端重写后才带）。
                        // 原为「强制全宽 + Crop」→ 会被拉伸/裁切成"占满行宽"。改为与评分评价页一致：
                        // 以内容宽度与 320dp 为上界 + Fit，小图保持原始尺寸不放大，大图等比缩小。
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val maxW = maxWidth
                            CommentImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(b.url)
                                    .crossfade(true)
                                    .build(),
                                imageModifier = Modifier
                                    .widthIn(max = maxW)
                                    .heightIn(max = 320.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                url = b.url,
                                contentScale = ContentScale.Fit,
                                onClick = { onImageClick(b.url) },
                            )
                        }
                    }
                }
                is HtmlBlock.Vote -> {
                    // 1.122: 投票占位符位置内联渲染投票卡（仅主楼传入 vote 时）
                    val v = vote
                    if (v != null && v.voteId == b.voteId) VoteCard(v, onToast)
                }
                is HtmlBlock.Text -> {
                    val emojiSegs = remember(b.text) { splitEmoji(b.text) }
                    if (emojiSegs.none { it.isEmoji }) {
                        val annotated = remember(b.text, b.spans) {
                            buildAnnotatedString {
                                append(b.text)
                                for (sp in b.spans) {
                                    val style = SpanStyle(
                                        fontWeight = if (sp.bold) FontWeight.Bold else null,
                                        color = if (sp.link != null) primary else onSurface,
                                    )
                                    addStyle(style, sp.start, sp.end)
                                }
                            }
                        }
                        Text(annotated, fontSize = (15 * fs).sp, lineHeight = (24 * fs).sp)
                    } else {
                        // Hupu emoji tokens: plain runs keep their spans (coords shifted to
                        // segment-local), tokens become inline image content (20sp square).
                        val annotated = remember(b, emojiSegs) {
                            buildAnnotatedString {
                                for (seg in emojiSegs) {
                                    if (seg.isEmoji) {
                                        appendInlineContent(seg.emojiUrl!!, seg.text)
                                    } else {
                                        val base = seg.oldStart
                                        append(seg.text)
                                        for (sp in b.spans) {
                                            val s2 = (sp.start - base).coerceAtLeast(0)
                                            val e2 = (sp.end - base).coerceAtMost(seg.text.length)
                                            if (e2 > s2) {
                                                addStyle(
                                                    SpanStyle(
                                                        fontWeight = if (sp.bold) FontWeight.Bold else null,
                                                        color = if (sp.link != null) primary else onSurface,
                                                    ),
                                                    s2, e2,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        val inline = rememberEmojiInline(emojiSegs, 20f * fs)
                        Text(annotated, fontSize = (15 * fs).sp, lineHeight = (24 * fs).sp, inlineContent = inline)
                    }
                }
            }
        }
    }
}

// ---------- 视频播放器 ----------
