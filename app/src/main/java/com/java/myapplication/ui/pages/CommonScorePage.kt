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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.java.myapplication.data.HupuCommonSubject
import com.java.myapplication.data.HupuCommonTree
import com.java.myapplication.data.HupuScoreItem
import com.java.myapplication.ui.components.ErrorRetry
import com.java.myapplication.ui.components.SkeletonHome
import com.java.myapplication.ui.components.normalizeCover
import com.java.myapplication.ui.components.tapGuard

/**
 * 虎扑通用评分（非赛事体系）：
 * - 首页：m.hupu.com/score-home SSR 主题卡流（每次刷新换一批）
 * - 主题详情：盖入式 overlay，子项流（分页），点子项进叶子详情（复用 PlayerDetailOverlay）
 * - 返回：预测性返回（跟手滑出/弹回），与赛事评分各页同构
 */

/** 通用评分卡片流（评分页「虎扑评分」chip 内容区） */
@Composable
internal fun CommonSubjectsFeed(
    subjects: List<HupuCommonSubject>,
    onOpenSubject: (HupuCommonSubject) -> Unit,
    onOpenItem: (HupuScoreItem) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp, top = 8.dp, bottom = 140.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(
            subjects,
            key = { "${it.bizType}-${it.bizNo}" },
        ) { s ->
            CommonSubjectCard(s, onOpen = onOpenSubject, onOpenItem = onOpenItem)
        }
    }
}

/** 主题卡：名称 + 均分 + 评分人数 + 简介 + 预置子项行 */
@Composable
private fun CommonSubjectCard(
    s: HupuCommonSubject,
    onOpen: (HupuCommonSubject) -> Unit,
    onOpenItem: (HupuScoreItem) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onOpen(s) }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    s.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${formatCount(s.scoreCountNum)}人评分",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (s.desc.isNotBlank()) {
            Text(
                s.desc,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp,
            )
        }
        // 预置子项行（最多 3 个，点击直接进该子项的叶子详情）
        if (s.items.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                s.items.take(3).forEach { item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .clickable { onOpenItem(item) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        if (item.image != null) {
                            AsyncImage(
                                model = normalizeCover(item.image),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)),
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            item.name,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** 通用评分主题详情页（盖入式 overlay：主题头 + 子项流，分页续拉） */
@Composable
internal fun CommonDetailOverlay(
    subject: HupuCommonSubject,
    tree: HupuCommonTree?,
    loading: Boolean,
    loadingMore: Boolean,
    closing: Boolean,
    onBack: () -> Unit,
    onClosed: () -> Unit,
    onRefresh: () -> Unit,
    onOpenItem: (HupuScoreItem) -> Unit,
    onLoadMore: () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(280)) }
    LaunchedEffect(closing) {
        if (closing) {
            progress.animateTo(0f, tween(280))
            onClosed()
        }
    }
    // 系统返回手势：预测性返回（单一 handler——多个 handler 共存时后组合者永远优先）。
    // - 退场动画播放中：吞掉手势（防止漏到系统直接退出应用）
    // - 其余：页面跟随手指实时滑出（progress: 0→1 跟手），松手未过阈值弹回、过阈值退出
    PredictiveBackHandler { events ->
        if (closing) {
            events.collect { } // 吞掉
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
    // 滚动到底部附近自动加载下一页（页码分页）
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && !loading && !loadingMore) onLoadMore()
    }
    Box(
        Modifier
            .fillMaxSize()
            .zIndex(2f)
            .graphicsLayer { translationX = (1f - progress.value) * size.width }
            .background(MaterialTheme.colorScheme.background)
            // 穿透守卫：失败态/加载态空白点击由本层兜底，不落穿到下层页面
            .tapGuard(),
    ) {
        val state = if (tree != null) "ok" else if (loading) "loading" else "error"
        Crossfade(targetState = state, animationSpec = tween(180), label = "commonDetailSwitch") { st ->
            when (st) {
                "loading" -> SkeletonHome()
                "error" -> ErrorRetry { onRefresh() }
                else -> {
                    val t = tree
                    if (t == null) {
                        ErrorRetry { onRefresh() }
                    } else {
                        // 与赛事侧（MatchDetailPage）同构：顶栏固定在页面顶部、贴屏幕左缘，
                        // 不随子项列表滚动；滚动区只含子项流
                        Column(Modifier.fillMaxSize()) {
                            CommonHeader(t, onBack)
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(
                                    start = 16.dp, end = 16.dp, top = 8.dp, bottom = 140.dp,
                                ),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                items(
                                    t.items,
                                    key = { it.bizId },
                                ) { item ->
                                    CommonItemRow(item, onClick = { onOpenItem(item) })
                                }
                                if (loadingMore) {
                                    item(key = "loading-more") {
                                        Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                            Text(
                                                "加载中…",
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
        }
    }
}

/** 主题详情顶栏：与赛事侧（MatchDetailPage）同构——返回 + 标题 + 「N人评分」 */
@Composable
private fun CommonHeader(t: HupuCommonTree, onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
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
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(
                t.name,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${formatCount(t.scorePersonCount)}人评分",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (t.desc.isNotBlank()) {
        Text(
            t.desc,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 18.sp,
            // 顶栏移出列表后不再吃 contentPadding，水平边距需自带（与子项流 16dp 左缘对齐）
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp),
        )
    }
}

/** 子项行：头像 + 名称 + 人数 + 均分 */
@Composable
private fun CommonItemRow(item: HupuScoreItem, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        if (item.image != null) {
            AsyncImage(
                model = normalizeCover(item.image),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)),
            )
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                item.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                formatCount(item.scorePersonCount),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            item.scoreAvg,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = scoreColor(item.scoreAvg),
        )
    }
}

/** 官方分数色（首页卡带的日间色，深色模式回落主题色） */
private fun officialColor(hex: String?): Color? {
    if (hex == null || !hex.matches(Regex("#[0-9A-Fa-f]{6}"))) return null
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        null
    }
}
