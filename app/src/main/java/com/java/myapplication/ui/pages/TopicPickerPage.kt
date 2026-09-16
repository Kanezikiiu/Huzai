package com.java.myapplication.ui.pages

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import kotlin.coroutines.cancellation.CancellationException
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.java.myapplication.data.HupuCategory
import com.java.myapplication.data.HupuPrefs
import com.java.myapplication.data.HupuRepository
import com.java.myapplication.data.HupuTopicInfo
import com.java.myapplication.ui.components.Chip
import com.java.myapplication.ui.components.ErrorRetry
import com.java.myapplication.ui.components.SecondaryPage
import com.java.myapplication.ui.components.normalizeCover
import com.java.myapplication.ui.components.tapGuard
import kotlinx.coroutines.delay

/**
 * 主页频道自定义页（盖入式二级页）：
 * 已选条（长按拖拽排序 / 点按移除）+ 搜索 + 大类过滤 + 版块勾选列表，上限 20 个。
 */
@Composable
fun TopicPickerPage(onClose: () -> Unit) {
    val repo = remember { HupuRepository() }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var loadTick by remember { mutableIntStateOf(0) }
    var hotTopics by remember { mutableStateOf<List<HupuTopicInfo>>(emptyList()) }
    var categories by remember { mutableStateOf<List<HupuCategory>>(emptyList()) }
    // 已选频道（有序），勾选/拖拽实时持久化
    var selected by remember { mutableStateOf<List<HupuTopicInfo>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var selectedCate by remember { mutableStateOf<String?>(null) } // null=全部
    var limitHintTick by remember { mutableIntStateOf(0) }

    // 盖入动画：进入 0→1；返回动画结束后由父级移除本组件
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        SecondaryPage.enter()
        progress.animateTo(1f, tween(280))
    }
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(closing) {
        if (closing) {
            SecondaryPage.exit()
            progress.animateTo(0f, tween(280))
            onClose()
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
            closing = true
        } catch (e: CancellationException) {
            // 未过阈值松手 → 弹回原位
            progress.animateTo(1f, tween(200))
            throw e
        }
    }

    LaunchedEffect(loadTick) {
        loading = true
        failed = false
        val b = repo.board()
        val c = repo.categories()
        if ((b?.hotTopics.isNullOrEmpty()) && c.isEmpty()) {
            failed = true
        } else {
            hotTopics = b?.hotTopics ?: emptyList()
            categories = c
            selected = if (HupuPrefs.hasCustomHomeTopics()) HupuPrefs.loadHomeTopics() else hotTopics
        }
        loading = false
    }

    fun toggle(t: HupuTopicInfo) {
        val cur = selected
        if (cur.any { it.url == t.url }) {
            val next = cur.filterNot { it.url == t.url }
            selected = next
            HupuPrefs.saveHomeTopics(next)
        } else if (cur.size < HupuPrefs.MAX_HOME_TOPICS) {
            val next = cur + t
            selected = next
            HupuPrefs.saveHomeTopics(next)
        } else {
            limitHintTick++
        }
    }

    fun remove(t: HupuTopicInfo) {
        val next = selected.filterNot { it.url == t.url }
        selected = next
        HupuPrefs.saveHomeTopics(next)
    }

    fun reorder(newList: List<HupuTopicInfo>) {
        selected = newList
        HupuPrefs.saveHomeTopics(newList)
    }

    var resetAsk by remember { mutableStateOf(false) }
    fun resetDefault() {
        HupuPrefs.clearHomeTopics()
        selected = emptyList()
    }

    // 版块全集 + 过滤（搜索优先于大类过滤）
    val allTopics = remember(categories) { categories.flatMap { it.topics }.distinctBy { it.url } }
    val filtered = remember(allTopics, query, selectedCate, categories) {
        val base = if (selectedCate == null) allTopics
        else categories.firstOrNull { it.cateId == selectedCate }?.topics ?: allTopics
        if (query.isBlank()) base.distinctBy { it.url }
        else allTopics.filter { it.name.contains(query.trim(), ignoreCase = true) }.distinctBy { it.url }
    }

    // 上限提示闪现
    var limitHintVisible by remember { mutableStateOf(false) }
    LaunchedEffect(limitHintTick) {
        if (limitHintTick > 0) {
            limitHintVisible = true
            delay(1600)
            limitHintVisible = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .zIndex(2f)
            .graphicsLayer { translationX = (1f - progress.value) * size.width }
            .background(MaterialTheme.colorScheme.background)
            // 穿透守卫：空白点击不落穿到下层页面
            .tapGuard(),
    ) {
        Column(Modifier.fillMaxSize()) {
            // 顶栏：返回 + 标题 + 计数 + 恢复默认
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { closing = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回", tint = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    "自定义首页频道",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${selected.size}/${HupuPrefs.MAX_HOME_TOPICS}",
                    fontSize = 14.sp,
                    color = if (selected.size >= HupuPrefs.MAX_HOME_TOPICS) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = { resetAsk = true }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "恢复默认", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (resetAsk) {
                AlertDialog(
                    onDismissRequest = { resetAsk = false },
                    title = { Text("清空首页频道") },
                    text = { Text("将清除全部已选首页频道，主页恢复显示默认频道。此操作不可恢复。") },
                    confirmButton = {
                        TextButton(onClick = { resetDefault(); resetAsk = false }) {
                            Text("清空", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { resetAsk = false }) { Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    },
                )
            }

            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                failed -> ErrorRetry { loadTick++ }
                else -> {
                    // 已选条（长按拖动排序 / 点按移除）
                    if (selected.isEmpty()) {
                        Text(
                            "还没有选择频道，从下方勾选吧",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        )
                    } else {
                        SelectedBar(selected = selected, onReorder = ::reorder, onRemove = ::remove)
                    }
                    Text(
                        "长按拖动排序 · 点按移除",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )

                    // 搜索
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        placeholder = { Text("搜索版块", fontSize = 14.sp) },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "清空", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(22.dp),
                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                    )

                    // 大类过滤
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clipToBounds()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item(key = "cate-all") {
                            Chip(text = "全部", selected = selectedCate == null) { selectedCate = null }
                        }
                        items(categories, key = { it.cateId }) { c ->
                            Chip(
                                text = c.name,
                                selected = selectedCate == c.cateId,
                            ) { selectedCate = if (selectedCate == c.cateId) null else c.cateId }
                        }
                    }

                    // 上限提示
                    AnimatedVisibility(visible = limitHintVisible) {
                        Text(
                            "最多添加 ${HupuPrefs.MAX_HOME_TOPICS} 个频道",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                        )
                    }

                    // 版块勾选列表
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 140.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(filtered, key = { it.url }) { t ->
                            val isSelected = selected.any { it.url == t.url }
                            TopicPickRow(topic = t, isSelected = isSelected, onToggle = { toggle(t) })
                        }
                    }
                }
            }
        }
    }
}

/** 版块勾选行：logo + 名称 + 关注数 + 选中态图标（达到上限时点未选项只弹提示） */
@Composable
private fun TopicPickRow(
    topic: HupuTopicInfo,
    isSelected: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                else Color.Transparent
            )
            .clickable { onToggle() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = normalizeCover(topic.logo),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(36.dp).clip(CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                topic.name,
                fontSize = 15.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (topic.hotText.isNotEmpty()) {
                Text(
                    "${topic.hotText} 关注",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            if (isSelected) Icons.Rounded.Check else Icons.Rounded.Add,
            contentDescription = null,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 已选条：长按拖拽排序（跨位交换式），点按移除 */
@Composable
private fun SelectedBar(
    selected: List<HupuTopicInfo>,
    onReorder: (List<HupuTopicInfo>) -> Unit,
    onRemove: (HupuTopicInfo) -> Unit,
) {
    val listState = rememberLazyListState()
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val currentList by rememberUpdatedState(selected)
    val currentOnReorder by rememberUpdatedState(onReorder)

    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(vertical = 8.dp)
            .clipToBounds()
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { off ->
                        listState.layoutInfo.visibleItemsInfo
                            .firstOrNull { off.x.toInt() in it.offset..(it.offset + it.size) }
                            ?.let { draggingIndex = it.index }
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        val from = draggingIndex ?: return@detectDragGesturesAfterLongPress
                        dragOffset += amount.x
                        val info = listState.layoutInfo.visibleItemsInfo
                            .firstOrNull { it.index == from }
                            ?: return@detectDragGesturesAfterLongPress
                        // 拖动中心越过相邻项中心则交换
                        val center = info.offset + info.size / 2f + dragOffset
                        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { t ->
                            t.index != from && center > t.offset && center < (t.offset + t.size)
                        } ?: return@detectDragGesturesAfterLongPress
                        val list = currentList
                        if (target.index < list.size) {
                            val mutable = list.toMutableList()
                            mutable.add(target.index, mutable.removeAt(from))
                            currentOnReorder(mutable)
                            draggingIndex = target.index
                            dragOffset = 0f
                        }
                    },
                    onDragEnd = { draggingIndex = null; dragOffset = 0f },
                    onDragCancel = { draggingIndex = null; dragOffset = 0f },
                )
            },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(selected, key = { _, t -> t.url }) { index, t ->
            val dragging = index == draggingIndex
            Row(
                Modifier
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer { translationX = if (dragging) dragOffset else 0f }
                    .animateItem()
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (dragging) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { onRemove(t) }
                    .padding(start = 10.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = normalizeCover(t.logo),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(24.dp).clip(CircleShape),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    t.name,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Spacer(Modifier.width(2.dp))
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "移除",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}