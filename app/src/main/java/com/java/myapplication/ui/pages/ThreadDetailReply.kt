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

/** 一条回复（totalReplies>0 时可点击进楼中楼） */
/** 底部回复框：纯文本输入，发送走 createReply（登录后可发送） */
@Composable
internal fun ThreadReplyBox(
    modifier: Modifier = Modifier,
    text: String,
    onTextChange: (String) -> Unit,
    sending: Boolean,
    loggedIn: Boolean,
    focusRequester: FocusRequester,
    quoteName: String = "",
    onCancelQuote: () -> Unit = {},
    emojiOpen: Boolean = false,
    onToggleEmoji: () -> Unit = {},
    onPanelToKeyboard: () -> Unit = {},
    images: List<Uri> = emptyList(),
    uploading: Boolean = false,
    onAddImages: () -> Unit = {},
    onRemoveImage: (Uri) -> Unit = {},
    onSend: () -> Unit,
    onCollapse: () -> Unit = {},
) {
    val keyboard = LocalSoftwareKeyboardController.current
    // 1.57 重构：顶部一条「引用条 + 收起」操作行 + 底部输入行；
    // 引用条从输入框 decorationBox 上移，输入框回归纯输入（后续表情/图片工具位挂在两行之间）
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            // 1.167: 吞掉输入行内「按钮之间的空隙、四周留白」的点击，
            // 否则这些空隙会把事件穿透到下层「空白收起层」(900) → 误关面板/回复框
            .tapGuard(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (quoteName.isNotBlank()) {
                Text(
                    "回复 @${quoteName}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "取消引用",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onCancelQuote() }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.weight(1f))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            // 1.80: 输入框多行增高时按钮须与输入框垂直居中, 不贴底
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 1.73 表情按钮
            Icon(
                HupuIcons.EmojiMood,
                contentDescription = "表情",
                tint = if (emojiOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    // 1.84: 开面板前先收键盘(微信式: 两者互斥)
                    .clickable {
                        if (!emojiOpen) keyboard?.hide()
                        onToggleEmoji()
                    }
                    .padding(4.dp),
            )
            Spacer(Modifier.width(10.dp))
            // 1.73 图片按钮（数量徽标）
            Box {
                Icon(
                    HupuIcons.ImageIcon,
                    contentDescription = "图片",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(enabled = images.size < 9) { onAddImages() }
                        .padding(4.dp),
                )
                if (images.isNotEmpty()) {
                    Text(
                        "${images.size}",
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
            // 1.82 修正: 无 key remember + 手动同步——tfvState.text != text 即外部改变
            // (表情插入/发送后清空), 重建 value 并把光标放到新文本末尾; 用户编辑时两者
            // 相等不触发同步, 光标保持用户位置(1.81 无 key 稿: tfvState 停留旧值, 表情后空白)
            var tfvState by remember { mutableStateOf(TextFieldValue(text)) }
            if (tfvState.text != text) {
                tfvState = TextFieldValue(text, TextRange(text.length))
            }
            BasicTextField(
                value = tfvState,
                onValueChange = { nv ->
                    tfvState = nv
                    onTextChange(nv.text)
                },
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .focusRequester(focusRequester)
                    // 1.84: 面板开时点输入框→收面板+弹键盘(不消费点击, 光标正常落位)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            // 1.90 镜像: 键盘先升起逐帧盖住面板, 父级等 IME 到峰值
                            // (=面板高)才移除面板 → 输入行全程零位移
                            if (emojiOpen) {
                                keyboard?.show()
                                onPanelToKeyboard()
                            }
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                // 1.82: lineHeight 20sp 时 14sp 默认贴底(leading 全在基线下方),
                // Center 让 14sp 在行内垂直居中——空态光标行/有内容行同高
                textStyle = TextStyle(
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.None,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                maxLines = 4,
                minLines = 1,
                decorationBox = { inner ->
                    // 1.82: CenterStart——垂直居中但水平起点对齐(1.81 用 Center 把文本也水平居中了)
                    Box(contentAlignment = Alignment.CenterStart) {
                        inner()
                        if (text.isEmpty()) {
                            Text(
                                if (loggedIn) "友善回复，理性讨论…" else "登录后即可回复",
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
            )
            Spacer(Modifier.width(8.dp))
            // 1.96: 发送动画——上传/发送期间按钮位置显示转圈(不再像卡住)
            val busy = sending || uploading
            val canSend = (text.isNotBlank() || images.isNotEmpty()) && loggedIn && !busy
            if (busy) {
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
                    color = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(enabled = canSend) {
                            keyboard?.hide()
                            onSend()
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
        // 1.73 图片缩略图行
        if (images.isNotEmpty()) {
            LazyRow(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(images) { uri ->
                    Box {
                        AsyncImage(
                            model = uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(8.dp)),
                        )
                        // 1.96: 上传中——缩略图蒙层 + 转圈(发送动画的一部分)
                        if (uploading) {
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
                            "\u2715",
                            fontSize = 11.sp,
                            color = Color.White,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(18.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(Color.Black.copy(alpha = 0.55f))
                                .clickable { onRemoveImage(uri) },
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }
        }
        // 1.87: 表情面板已上移至挂载区(覆盖式, 键盘揭幕) —— 此处不再渲染面板
    }
}

@Composable
internal fun ReplyRow(
    r: HupuReply,
    onOpenFloor: () -> Unit,
    onImageClick: (List<String>, Int) -> Unit = { _, _ -> },
    onLight: () -> Unit = {},
    isLit: Boolean = false,
    onOpenUser: (String) -> Unit = {},
    onQuoteReply: () -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .then(
                if (r.totalReplies > 0) Modifier.clickable { onOpenFloor() } else Modifier
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = r.author?.takeIf { it.euid.isNotEmpty() }
                ?.let { a -> Modifier.clickable { onOpenUser(a.euid) } } ?: Modifier,
        ) {
                // 头像（无头像时隐藏不占位）
                r.author?.let { a ->
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
                r.author?.name ?: "虎扑JR",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (r.isStarter) {
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
        HtmlContent(r.contentHtml, onImageClick = onImageClick)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (r.location.isNotBlank()) {
                Text(r.location, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
            }
            if (r.createdAtText.isNotBlank()) {
                Text(r.createdAtText, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.weight(1f))
            // 点亮（登录后可点；点亮态红色爱心，计数本地 +1 展示）
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
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    formatCount(if (isLit) r.lights + 1 else r.lights),
                    fontSize = 11.sp,
                    color = if (isLit) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(10.dp))
            // 回复该楼（1.55：调起底部回复框，带 quoteId）
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
        // 楼中楼入口：与 sheet 内子回复同款——另起一行、居左（不与点赞挤同一行）
        if (r.totalReplies > 0) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    ) { onOpenFloor() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "展开回复",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    "展开 ${r.totalReplies} 条回复",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** 1.94: 表情面板 tab 胶囊 */
@Composable
internal fun EmojiTabChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else Color.Transparent,
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** 1.94: 我的表情（收藏表情包）网格：点 = 插入/上传，长按 = 删除 */
@Composable
internal fun StickerPane(
    stickers: List<HupuSticker>,
    onClick: (HupuSticker) -> Unit,
    onLongClick: (HupuSticker) -> Unit,
    bottomPad: Dp,
) {
    if (stickers.isEmpty()) {
        // 1.165: 空态也保留首位「+」——本地添加是「还没有收藏」时唯一的入口，不能藏起来
        Column(
            Modifier.fillMaxSize().padding(top = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StickerAddCell()
            Spacer(Modifier.height(12.dp))
            Text(
                "还没有表情\n点「+」添加本地图片，或长按评论区的表情收藏",
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 10.dp, bottom = bottomPad),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 1.165: 首位「+」添加本地图片（微信式）
        item(key = "__add_sticker__") { StickerAddCell() }
        gridItems(stickers, key = { it.url }) { s ->
            AsyncImage(
                model = s.url,
                contentDescription = null,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .combinedClickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = { onClick(s) },
                        onLongClick = { onLongClick(s) },
                    ),
            )
        }
    }
}

/** 1.93: 面板→键盘翻转判定器: 读 Compose 动画后的 ime(与输入行 padding 同源),
 *  到达面板高(键盘已完全盖住面板)才回调翻转。逐帧重组隔离在本函数内(零尺寸,
 *  不影响布局), 不做整页逐帧重组。 */
@Composable
internal fun PanelToKeyboardWatcher(
    active: Boolean,
    targetPx: Int,
    navPx: Int,
    onReached: () -> Unit,
) {
    if (!active) return
    val density = LocalDensity.current
    // 与输入行 padding 完全同源: Compose 动画插值后的 ime 底边
    val imePx = WindowInsets.ime.getBottom(density)
    val reached = imePx >= maxOf(targetPx, navPx) - 2
    LaunchedEffect(reached) {
        if (reached) onReached()
    }
}

// ---------- 楼中楼：半屏 sheet 栈 ----------
