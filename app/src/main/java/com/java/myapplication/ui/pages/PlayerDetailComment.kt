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

/** 一条评论 */
@Composable
internal fun CommentRow(
    c: HupuScoreComment,
    /** 1.163: 点作者头像/昵称进入用户主页（userId 为空时不响应） */
    onOpenUser: (String) -> Unit = {},
    onOpenSub: (HupuScoreComment) -> Unit = {},
    onImageClick: (List<String>, Int) -> Unit = { _, _ -> },
    onLight: () -> Unit = {},
    lightState: Map<String, Boolean> = emptyMap(),
    onReply: () -> Unit = {},
    /** 1.150: 本机刚发送的回复（乐观层）：合并进楼中楼预览，按 commentId 与服务端数据去重 */
    optimistic: List<HupuScoreComment> = emptyList(),
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 1.163: 头像 / 昵称可点 → 用户主页（无 userId 时不响应）
            val canOpen = c.userId.isNotBlank()
            AsyncImage(
                model = normalizeCover(c.userHead),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable(enabled = canOpen) { onOpenUser(c.userId) },
            )
            Spacer(Modifier.width(10.dp))
            Text(
                c.userName,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .clickable(enabled = canOpen) { onOpenUser(c.userId) },
            )
            Spacer(Modifier.width(6.dp))
            if (c.score > 0) {
                Text(
                    "${c.score}分",
                    fontSize = 11.sp,
                    color = scoreColor(c.score.toString()),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (c.content.isNotBlank()) {
            EmojiText(
                c.content,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        c.images.forEachIndexed { i, img ->
            CommentAsyncImageCompact(
                model = normalizeImageUrl(img),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .heightIn(max = 320.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onImageClick(c.images, i) },
            )
            Spacer(Modifier.height(6.dp))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val meta = listOf(c.date, c.ipLocation).filter { it.isNotBlank() }.joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    meta,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            // 1.145 回复入口（回复嵌套进楼中楼，非帖子式新楼层）
            Text(
                "回复",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onReply() }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
            Spacer(Modifier.width(6.dp))
            // 1.64 点亮：可点击红心（登录态，乐观翻转+自愈；与帖子详情页同形态）
            val litKey = c.subjectId.ifEmpty { c.commentId } + ":" + c.commentId
            val isLit = lightState[litKey] ?: c.hasLight
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
                    (if (isLit) c.lightCount + 1 else c.lightCount).toString(),
                    fontSize = 12.sp,
                    color = if (isLit) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // 楼中楼：官方主响应内嵌子回复（匿名可见）。不缩进（平铺卡片内），点击展开半屏 sheet
        val previewSubs = mergeWithOptimistic(c.subComments, optimistic)
        if (previewSubs.isNotEmpty() || c.subCommentCount > 0) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .clickable { onOpenSub(c) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                previewSubs.forEach { sub ->
                    // 名字+内容合并为单个富文本：长内容换行从行首开始，
                    // 避免「名字占位+内容跟随」的悬挂缩进观感
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)) {
                                append(sub.userName)
                            }
                            append("：${sub.content}")
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 17.sp,
                    )
                    // 1.151: 带图子回复在预览里给缩略图——此前预览只渲染文本，
                    // 用户刚发完带图回复时在楼层列表里看不到图，误以为图片没发出去。
                    // 缩略图不可点（整块点击仍是展开楼中楼），避免与父级点击冲突。
                    if (sub.images.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            sub.images.take(3).forEach { img ->
                                CommentAsyncImage(
                                    model = normalizeImageUrl(img),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(6.dp)),
                                )
                            }
                            if (sub.images.size > 3) {
                                Text(
                                    "+${sub.images.size - 3}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.align(Alignment.CenterVertically),
                                )
                            }
                        }
                    }
                }
                if (c.subCommentCount + optimistic.size > previewSubs.size) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "展开全部 ${c.subCommentCount + optimistic.size} 条回复",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                        Icon(
                            Icons.Rounded.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 楼中楼 sheet 里的一条子回复（无缩进，浅色卡片） */
@Composable
internal fun SubCommentRow(
    sub: HupuScoreComment,
    /** 1.163: 点作者头像/昵称进入用户主页 */
    onOpenUser: (String) -> Unit = {},
    grandState: GrandExpandState = GrandExpandState(),
    grandLoading: Boolean = false,
    onExpandGrand: () -> Unit = {},
    onImageClick: (List<String>, Int) -> Unit = { _, _ -> },
    onLight: () -> Unit = {},
    onLightGrand: (HupuScoreComment) -> Unit = {},
    lightState: Map<String, Boolean> = emptyMap(),
    onReply: () -> Unit = {},
    onReplyGrand: (HupuScoreComment) -> Unit = {},
    /** 1.150: 本机刚发送的回复（乐观层）：key = 被回复评论 commentId */
    optimisticGrand: Map<String, List<HupuScoreComment>> = emptyMap(),
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 1.163: 头像 / 昵称可点 → 用户主页
            val canOpen = sub.userId.isNotBlank()
            AsyncImage(
                model = normalizeCover(sub.userHead),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(enabled = canOpen) { onOpenUser(sub.userId) },
            )
            Spacer(Modifier.width(8.dp))
            Text(
                sub.userName,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .clickable(enabled = canOpen) { onOpenUser(sub.userId) },
            )
        }
        EmojiText(
            sub.content,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        sub.images.forEachIndexed { i, img ->
            CommentAsyncImageCompact(
                model = normalizeImageUrl(img),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .heightIn(max = 300.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onImageClick(sub.images, i) },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val meta = listOf(sub.date, sub.ipLocation).filter { it.isNotBlank() }.joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(meta, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.weight(1f))
            // 1.145 回复入口（子回复）
            Text(
                "回复",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onReply() }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
            Spacer(Modifier.width(6.dp))
            // 1.64 点亮（子回复）
            val sKey = sub.subjectId.ifEmpty { sub.commentId } + ":" + sub.commentId
            val sLit = lightState[sKey] ?: sub.hasLight
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onLight() }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                Icon(
                    if (sLit) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = null,
                    tint = if (sLit) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    (if (sLit) sub.lightCount + 1 else sub.lightCount).toString(),
                    fontSize = 11.sp,
                    color = if (sLit) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // 回复的回复（孙评论）：官方缩进挂在子回复下方（descendantCount>0 时内嵌 subCommentList）
        // 展示集 = 内嵌 direct 孙 + 已展开追加的孙评论（ScorePage 已拉平），按 commentId 去重保序。
        // 官方语义：descendantCount 是全后代数；初始只显示 direct 层（parentCommentId=本子评论），
        // 深层（曾孙等）由「展开更多回复」端点分页拉平后追加——按钮随 descendantCount > 已显示数出现
        val grandTotal = sub.descendantCount
        val inlineDirect = sub.subComments.filter { it.parentCommentId == sub.commentId }
        val merged = mergeWithOptimistic(
            inlineDirect + grandState.comments,
            optimisticGrand[sub.commentId].orEmpty(),
        )
        merged.forEach { grand ->
            Row(
                Modifier
                    .padding(top = 2.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            ) {
                // 1.163: 孙评论头像 / 昵称同样可点 → 用户主页
                val canOpenG = grand.userId.isNotBlank()
                AsyncImage(
                    model = normalizeCover(grand.userHead),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .clickable(enabled = canOpenG) { onOpenUser(grand.userId) },
                )
                Spacer(Modifier.width(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            grand.userName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .clickable(enabled = canOpenG) { onOpenUser(grand.userId) },
                        )
                        // 深层（曾孙+）：回复对象不是本子评论时，标注「回复 @作者」提供上下文
                        if (grand.parentCommentId != sub.commentId) {
                            val target = merged.firstOrNull { it.commentId == grand.parentCommentId }
                            if (target != null && target.userName.isNotBlank()) {
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "回复 @${target.userName}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    Text(
                        grand.content,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 17.sp,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val gmeta = listOf(grand.date, grand.ipLocation).filter { it.isNotBlank() }.joinToString(" · ")
                        if (gmeta.isNotBlank()) {
                            Text(gmeta, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.weight(1f))
                        // 1.145 回复入口（孙评论）
                        Text(
                            "回复",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onReplyGrand(grand) }
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        // 1.64 点亮（孙评论）
                        val gKey = grand.subjectId.ifEmpty { grand.commentId } + ":" + grand.commentId
                        val gLit = lightState[gKey] ?: grand.hasLight
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onLightGrand(grand) }
                                .padding(horizontal = 3.dp, vertical = 1.dp),
                        ) {
                            Icon(
                                if (gLit) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = null,
                                tint = if (gLit) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(11.dp),
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(
                                (if (gLit) grand.lightCount + 1 else grand.lightCount).toString(),
                                fontSize = 10.sp,
                                color = if (gLit) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        // 展开更多回复按钮：descendantCount > 已显示数且未确认尽头时显示（官方同款交互，可多次点击分页追加）
        if (grandTotal > merged.size && !grandState.attempted) {
            Text(
                if (grandLoading) "展开中…" else "展开更多回复",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .clickable { onExpandGrand() },
            )
        }
    }
}
