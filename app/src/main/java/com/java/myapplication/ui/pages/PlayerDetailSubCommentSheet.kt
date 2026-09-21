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

/** 楼中楼 sheet 数据（宿主页面管理状态，sheet 无状态渲染）。sortKey=brightest/latest/earliest */
data class SubCommentSheetData(
    val parent: HupuScoreComment,
    val data: ScoreCommentState?,
    val sortKey: String = "brightest",
    /** 孙评论展开状态（子评论 commentId → 展开态；跨排序切换保留，跨 sheet 关闭清空） */
    val grandMap: Map<String, GrandExpandState> = emptyMap(),
)

/**
 * 评分评论楼中楼：半屏 sheet（贴底 72% 高，顶部圆角，从底滑入）。
 * 母回复置顶（完整评论卡）+ 全量子回复流（subCommentList），上滑加载更多（getMore）。
 */
@Composable
fun SubCommentSheet(
    sheet: SubCommentSheetData,
    /** 1.163: 楼中楼内点作者头像/昵称进入用户主页 */
    onOpenUser: (String) -> Unit = {},
    loading: Boolean,
    loadingMore: Boolean,
    closing: Boolean,
    onBack: () -> Unit,
    onClosed: () -> Unit,
    onLoadMore: () -> Unit,
    onSortChange: (String) -> Unit = {},
    onRetry: () -> Unit = {},
    onExpandGrand: (HupuScoreComment) -> Unit = {},
    onImageClick: (List<String>, Int) -> Unit = { _, _ -> },
    onLightComment: (HupuScoreComment) -> Unit = {},
    lightState: Map<String, Boolean> = emptyMap(),
    /** (root 母评论, target 被回复评论) */
    onReply: (HupuScoreComment, HupuScoreComment) -> Unit = { _, _ -> },
    /** 1.150: 本机刚发送的回复（乐观层）：key = 被回复评论 commentId */
    optimisticReplies: Map<String, List<HupuScoreComment>> = emptyMap(),
    /** 1.146: 回复框拦截——返回 true = 回复框开着已由宿主收起，本层返回手势不再关 sheet */
    replyBoxOpen: Boolean = false,
    onDismissReplyBox: () -> Unit = {},
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(280)) }
    LaunchedEffect(closing) {
        if (closing) {
            progress.animateTo(0f, tween(280))
            onClosed()
        }
    }
    // 系统返回：直接盖出关闭（后组合者优先，自动遮蔽宿主页面的返回手势）
    PredictiveBackHandler { events ->
        if (replyBoxOpen) {
            // 1.146: 回复框开着（盖在 sheet 上）→ 返回手势先收回复框，不关 sheet
            events.collect { } // 吞掉
            onDismissReplyBox()
            return@PredictiveBackHandler
        }
        if (closing) {
            events.collect { } // 吞掉
        } else {
            try {
                events.collect { }
                onBack()
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    val listState = rememberLazyListState()
    val fr = sheet.data
    // 1.62 Crossfade 出场层快照（修切排序闪「加载失败」）：切排序/切母评论瞬间
    // data 被清空、state 转 "loading"，正在淡出的旧 "ok" 层会以最新闭包重组——
    // 直接读活状态 fr（已 null）命中 d==null 兜底渲染 ErrorRetry，表现为整页
    // 先闪「加载失败」再转圈再出内容（实测顺序吻合）。latch 保住最后一次非空
    // 数据：出场层继续渲染旧列表平滑淡出（标准 crossfade 视觉，零闪态）；
    // 当前层 latch 始终同步跟随 fr，翻页/回填的原地更新照常，不触发额外 crossfade
    val lastData = remember { mutableStateOf<ScoreCommentState?>(null) }
    if (fr != null) lastData.value = fr
    val canLoadMore = fr != null && fr.hasMore
    val shouldLoadMore by remember(fr, loadingMore) {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            canLoadMore && !loadingMore && last >= listState.layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && fr != null) onLoadMore()
    }

    Box(
        Modifier
            .fillMaxSize()
            .zIndex(4f)
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
            // 1.58 状态机三分支：无数据+加载中=骨架；无数据+无加载=真失败；
            // 有数据（含加载中——切排序保留旧列表）=列表 + 顶部小 loading 角标
            val state = if (fr != null) "ok" else if (loading) "loading" else "error"
            Crossfade(targetState = state, animationSpec = tween(180), label = "subCommentSwitch") { s ->
                when (s) {
                    "loading" -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                    "error" -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ErrorRetry { onRetry() }
                    }
                    else -> {
                        val d = lastData.value // 出场层快照：淡出期间 fr 已 null，用 latch 渲染旧列表
                        if (d == null) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                ErrorRetry { onBack() }
                            }
                        } else {
                            // 切排序加载中：旧列表顶部叠一条小角标（不打断阅读）
                            if (loading) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                    )
                                }
                            }
                            // 评论关键词过滤（楼中楼子评论）：composable 作用域计算
                            val ckw = remember(HupuPrefs.filterVersion) { HupuPrefs.loadFilterKeywords() }
                            val filteredSubs = remember(d.comments, ckw) {
                                if (ckw.comment.isEmpty()) d.comments
                                else d.comments.filterNot { sub -> HupuFilter.blockedComment(sub.content, ckw) }
                            }
                            // 子回复流 = 服务端数据 ⊕ 本机乐观层（key=母评论 id；按 commentId 去重，
                            // 服务端返回同一条后不会再重复显示）
                            val displaySubs = mergeWithOptimistic(
                                filteredSubs,
                                optimisticReplies[sheet.parent.commentId].orEmpty(),
                            )
                            LazyColumn(
                                state = listState,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
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
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            // 1.164: 母评论作者头像/昵称同样可点 → 用户主页（此前漏了这处）
                                            val canOpenP = sheet.parent.userId.isNotBlank()
                                            AsyncImage(
                                                model = normalizeCover(sheet.parent.userHead),
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(30.dp)
                                                    .clip(CircleShape)
                                                    .clickable(enabled = canOpenP) { onOpenUser(sheet.parent.userId) },
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                sheet.parent.userName,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier
                                                    .weight(1f, fill = false)
                                                    .clickable(enabled = canOpenP) { onOpenUser(sheet.parent.userId) },
                                            )
                                            if (sheet.parent.score > 0) {
                                                Text(
                                                    "${sheet.parent.score}分",
                                                    fontSize = 11.sp,
                                                    color = scoreColor(sheet.parent.score.toString()),
                                                    fontWeight = FontWeight.SemiBold,
                                                )
                                            }
                                        }
                                        Text(
                                            sheet.parent.content,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            lineHeight = 20.sp,
                                        )
                                        sheet.parent.images.forEachIndexed { i, img ->
                                            AsyncImage(
                                                model = normalizeImageUrl(img),
                                                contentDescription = null,
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier
                                                    .widthIn(max = 320.dp)
                                                    .heightIn(max = 320.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .clickable { onImageClick(sheet.parent.images, i) },
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            val meta = listOf(sheet.parent.date, sheet.parent.ipLocation)
                                                .filter { it.isNotBlank() }.joinToString(" · ")
                                            if (meta.isNotBlank()) {
                                                Text(meta, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Spacer(Modifier.weight(1f))
                                            // 1.64 点亮（母楼与主评论同端点）
                                            val pKey = sheet.parent.subjectId.ifEmpty { sheet.parent.commentId } + ":" + sheet.parent.commentId
                                            val pLit = lightState[pKey] ?: sheet.parent.hasLight
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .clickable { onLightComment(sheet.parent) }
                                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                            ) {
                                                Icon(
                                                    if (pLit) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                                    contentDescription = null,
                                                    tint = if (pLit) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(14.dp),
                                                )
                                                Spacer(Modifier.width(3.dp))
                                                Text(
                                                    (if (pLit) sheet.parent.lightCount + 1 else sheet.parent.lightCount).toString(),
                                                    fontSize = 12.sp,
                                                    color = if (pLit) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    }
                                }
                                item(key = "count") {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                                    ) {
                                        Text(
                                            "全部回复 ${maxOf(sheet.parent.subCommentCount, displaySubs.size)}",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f),
                                        )
                                        // 排序 tab（官方楼中楼同款：默认最亮）
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            listOf(
                                                "brightest" to "最亮",
                                                "latest" to "最晚",
                                                "earliest" to "最早",
                                            ).forEach { (key, label) ->
                                                val active = key == sheet.sortKey
                                                Text(
                                                    label,
                                                    fontSize = 12.sp,
                                                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier
                                                        .padding(start = 10.dp)
                                                        .clickable { onSortChange(key) },
                                                )
                                            }
                                        }
                                    }
                                }
                                // 1.150: 空态——母评论暂无子回复时明确提示，不再让用户以为加载失败
                                if (displaySubs.isEmpty()) {
                                    item(key = "sub-empty") {
                                        Box(
                                            Modifier.fillMaxWidth().padding(vertical = 28.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                "还没有回复，来抢第一条～",
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                                items(displaySubs, key = { it.commentId }) { sub ->
                                    val gs = sheet.grandMap[sub.commentId]
                                    SubCommentRow(
                                        sub,
                                        onOpenUser = onOpenUser,
                                        grandState = gs ?: GrandExpandState(),
                                        grandLoading = gs?.loading == true,
                                        onExpandGrand = { onExpandGrand(sub) },
                                        onImageClick = onImageClick,
                                        onLight = { onLightComment(sub) },
                                        onLightGrand = onLightComment,
                                        lightState = lightState,
                                        onReply = { onReply(sheet.parent, sub) },
                                        onReplyGrand = { onReply(sheet.parent, it) },
                                        optimisticGrand = optimisticReplies,
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
