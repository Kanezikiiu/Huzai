package com.java.myapplication.ui.pages

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import kotlin.coroutines.cancellation.CancellationException
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import com.java.myapplication.ui.components.animateChipCenterTo
import com.java.myapplication.ui.theme.isAppDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Search
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.java.myapplication.ui.glass.LiquidButton
import com.java.myapplication.ui.glass.LiquidGlassDialog
import com.java.myapplication.ui.glass.buttonBorder
import com.java.myapplication.ui.glass.buttonFill
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import com.java.myapplication.ui.components.huzaiFieldColors
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.java.myapplication.data.HupuCategory
import com.java.myapplication.data.HupuFilter
import com.java.myapplication.data.HupuPrefs
import com.java.myapplication.data.HupuRepository
import com.java.myapplication.data.HupuSearchItem
import com.java.myapplication.data.HupuTopicInfo
import com.java.myapplication.data.SEARCH_SORTS
import com.java.myapplication.ui.components.Chip
import com.java.myapplication.ui.components.ErrorRetry
import com.java.myapplication.ui.components.SecondaryPage
import com.java.myapplication.ui.components.formatCount
import com.java.myapplication.ui.components.normalizeCover
import com.java.myapplication.ui.components.glassBorder
import com.java.myapplication.ui.components.glassFill
import com.java.myapplication.ui.components.glassPress
import com.java.myapplication.ui.components.thumbnailUrl
import com.java.myapplication.ui.components.tapGuard
import kotlinx.coroutines.launch

/**
 * 搜索页（盖入式二级页，桌面版搜索通道）：
 * - 历史记录条（横滑、可单条删、可清空）
 * - 排序 chips（六种）+ 专区过滤（大类下拉，可后加）
 * - 结果流：FeedList 风格卡片，无限滚动翻页
 * - 点击结果 → 帖子详情（由父页面接 ThreadDetailOverlay）
 */
@Composable
fun SearchPage(
    onOpenThread: (HupuSearchItem) -> Unit,
    onClose: () -> Unit,
) {
    val repo = remember { HupuRepository() }
    val scope = rememberCoroutineScope()
    // 1.191: 记录层——供 Liquid Glass 弹窗采样背后像素（挂在内容层上，弹窗在其后）
    // 1.191: 先铺一层不透明页面底色再画内容——与 ThreadDetailPage 的
    // actionBackdrop 同一套做法。记录层若透明，卡片会显得「非常透明」
    val dialogBg = MaterialTheme.colorScheme.background
    val backdrop = rememberLayerBackdrop {
        drawRect(dialogBg)
        drawContent()
    }

    var query by remember { mutableStateOf("") }
    var activeQuery by remember { mutableStateOf<String?>(null) } // 已提交的查询（null=未搜索）
    var sortby by remember { mutableStateOf("general") }
    var topicId by remember { mutableStateOf<String?>(null) }
    // 专区筛选状态
    var forumPickerOpen by remember { mutableStateOf(false) }
    var categories by remember { mutableStateOf<List<HupuCategory>>(emptyList()) }
    var categoriesLoaded by remember { mutableStateOf(false) }
    // 首次打开选择器时拉取专区树（缓存秒开，无需 loading 态）
    LaunchedEffect(forumPickerOpen) {
        if (forumPickerOpen && !categoriesLoaded) {
            val c = repo.categories()
            if (c.isNotEmpty()) {
                categories = c
                categoriesLoaded = true
            }
        }
    }

    var results by remember { mutableStateOf<List<HupuSearchItem>>(emptyList()) }
    var page by remember { mutableIntStateOf(1) }
    var totalPages by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }      // 首次/换条件加载
    var loadingMore by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }     // 是否至少搜过一次（区分初始态）
    // 1.191: 「清空搜索历史」确认弹窗状态（从 HistoryPanel 上提到页面级——
    // 弹窗必须画在页面根 Box 里内容层之后的兄弟位置，毛玻璃才采得到页面像素）
    var clearHistoryAsk by remember { mutableStateOf(false) }
    var clearHistoryCount by remember { mutableIntStateOf(0) }

    // 盖入动画（TopicPickerPage 同款）
    val progress = remember { Animatable(0f) }
    // 1.192: 计数 flag 门控——多页叠加 / 重挂载时不会多减，离开组合时兜底回收
    var pageEntered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        pageEntered = true
        SecondaryPage.enter()
        progress.animateTo(1f, tween(280))
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { if (pageEntered) { pageEntered = false; SecondaryPage.exit() } }
    }
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(closing) {
        if (closing) {
            if (pageEntered) { pageEntered = false; SecondaryPage.exit() }
            progress.animateTo(0f, tween(280))
            onClose()
        }
}
    // 系统返回手势：预测性返回（单一 handler 内部分流——多个 handler 共存时后组合者
    // 永远优先，会把预测性动画整个吞掉，因此本组件只允许这一个 handler）。
    // - 专区选择器打开：ModalBottomSheet 自带 onDismissRequest 消费返回，这里吞掉避免双消费
    // - 退场动画播放中：吞掉手势（防止漏到系统直接退出应用）
    // - 其余：页面跟随手指实时滑出（progress: 0→1 跟手），松手未过阈值弹回、过阈值退出
    PredictiveBackHandler { events ->
        if (closing || forumPickerOpen) {
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

    fun submit(q: String) {
        val qq = q.trim()
        if (qq.isEmpty()) return
        query = qq
        activeQuery = qq
        searched = true
        HupuPrefs.addSearchHistory(qq)
        page = 1
        results = emptyList()
        loading = true
        failed = false
        scope.launch {
            val p = repo.search(qq, topicId, sortby, 1)
            if (p != null) {
                results = p.items
                totalPages = p.totalPages
                page = p.page
            } else failed = true
            loading = false
        }
    }

    fun loadMore() {
        val q = activeQuery ?: return
        if (loadingMore || loading || failed) return
        if (page >= totalPages) return
        loadingMore = true
        scope.launch {
            val p = repo.search(q, topicId, sortby, page + 1)
            if (p != null) {
                results = (results + p.items).distinctBy { it.tid }
                totalPages = p.totalPages
                page = p.page
            }
            loadingMore = false
        }
    }

    fun changeSort(newSort: String) {
        if (sortby == newSort || activeQuery == null) return
        sortby = newSort
        submit(activeQuery!!)
    }

    /** 切换专区筛选（null=所有专区），立即重搜第 1 页 */
    fun changeForum(newTopicId: String?) {
        if (topicId == newTopicId) return
        val cur = activeQuery ?: return
        topicId = newTopicId
        submit(cur)
    }

    fun retrySearch() {
        activeQuery?.let { submit(it) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .zIndex(2f)
            .graphicsLayer { translationX = (1f - progress.value) * size.width }
            .background(MaterialTheme.colorScheme.background)
            // 穿透守卫：排序条空隙/空白点击不落穿到下层页面
            .tapGuard(),
    ) {
        Column(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            SearchHeader(query, query.isNotBlank(), { query = it }, { submit(query) }, { closing = true })

            // 筛选条：专区按钮 + 排序条（已搜索才显示）
            val dark = isAppDarkTheme()
            if (searched) {
                // 1.192c: 补「选中项居中」便捷特性。
                // 序号 0 是「专区」按钮，排序项从 1 开始；选第 1 个排序项时回到最左，
                // 避免把唯一入口「专区」按钮挤出屏幕左侧。
                val filterBarState = rememberLazyListState()
                val sortSelIdx = 1 + SEARCH_SORTS.indexOfFirst { it.key == sortby }
                LaunchedEffect(sortby) {
                    if (sortSelIdx >= 1) {
                        if (sortSelIdx <= 1) filterBarState.animateScrollToItem(0)
                        else filterBarState.animateChipCenterTo(sortSelIdx)
                    }
                }
                LazyRow(
                    state = filterBarState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clipToBounds()
                        .padding(top = 8.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 专区筛选按钮（与排序 chips 同行，不再单独成行）
                    item {
                        val allTopicsFlat = remember(categories) {
                        categories.flatMap { it.topics }.distinctBy { it.topicId }
                    }
                    val selectedForumName = if (topicId == null) "所有专区"
                        else allTopicsFlat.firstOrNull { it.topicId == topicId }?.name ?: "所有专区"
                    val forumActive = topicId != null
                    val forumTint =
                        if (forumActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            // 1.192c: 材质升级为与顶部玻璃胶囊同源（扁平半透明 + 极淡描边）
                            .background(
                                if (forumActive) {
                                    if (dark) Color(0xFF2E2E30).copy(0.94f) else Color.White.copy(0.92f)
                                } else {
                                    if (dark) Color.White.copy(0.075f) else Color.Black.copy(0.045f)
                                }
                            )
                            .border(
                                0.6.dp,
                                if (forumActive) {
                                    if (dark) Color.White.copy(0.10f) else Color.Black.copy(0.05f)
                                } else {
                                    if (dark) Color.White.copy(0.06f) else Color.Black.copy(0.03f)
                                },
                                RoundedCornerShape(50),
                            )
                            .clickable { forumPickerOpen = true }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            selectedForumName,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = forumTint,
                            maxLines = 1,
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Rounded.ArrowDropDown,
                            contentDescription = "选择专区",
                            tint = forumTint,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    }
                    items(SEARCH_SORTS, key = { it.key }) { s ->
                        Chip(text = s.title, selected = sortby == s.key) { changeSort(s.key) }
                    }
                }
            }

            val state = when {
                activeQuery == null -> "history"
                loading -> "loading"
                failed -> "error"
                results.isEmpty() -> "empty"
                else -> "ok"
            }
            when (state) {
                "history" -> HistoryPanel(
                    onPick = { submit(it) },
                    onClearAsk = {
                        clearHistoryCount = HupuPrefs.loadSearchHistory().size
                        clearHistoryAsk = true
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                "loading" -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                "error" -> ErrorRetry { retrySearch() }
                "empty" -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("没有找到相关帖子", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> SearchResults(results, page < totalPages, loadingMore, ::loadMore) { onOpenThread(it) }
            }
        }

        // 专区筛选：半屏弹出菜单（可上拉全屏）
        if (forumPickerOpen) {
            ForumPickerSheet(
                categories = categories,
                selectedTopicId = topicId,
                onSelect = { changeForum(it); forumPickerOpen = false },
                onDismiss = { forumPickerOpen = false },
            )
        }
        // 1.191: 清空搜索历史确认弹窗（Liquid Glass 同款外观 + Q 弹出入场）
        if (clearHistoryAsk) {
            LiquidGlassDialog(
                backdrop = backdrop,
                title = "清空搜索历史",
                message = "将删除全部 ${clearHistoryCount} 条搜索历史，此操作不可恢复。",
                confirmText = "清空",
                dismissText = "取消",
                onConfirm = { HupuPrefs.clearSearchHistory() },
                onDismiss = { clearHistoryAsk = false },
            )
        }
    }
}
/** 顶栏：返回 + 输入框 + 搜索按钮 */
@Composable
private fun SearchHeader(
    query: String,
    hasText: Boolean,
    onChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
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
                .clickable { onBack() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回", tint = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.width(4.dp))
        OutlinedTextField(
            colors = huzaiFieldColors(),
            value = query,
            onValueChange = onChange,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 2.dp),
            placeholder = { Text("搜索虎扑帖子", fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            trailingIcon = {
                if (hasText) {
                    IconButton(onClick = { onChange("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = "清空", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(22.dp),
            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        )
        Text(
            "搜索",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (hasText) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(enabled = hasText) { onSubmit() }
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

/** 历史记录面板：标题 + 清空 + 横滑条目（点按搜、长按删）
 * 1.63 修复：移除双重 statusBarsPadding（SearchHeader 已做，面板内再叠一次把历史条
 * 硬推下一个状态栏高度——1.54 修间距时未察觉此层，故「修过但没修好」）
 */
@Composable
private fun HistoryPanel(
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onClearAsk: () -> Unit,
) {
    var historyTick by remember { mutableIntStateOf(0) }
    val history = remember(historyTick, HupuPrefs.searchHistoryVersion) { HupuPrefs.loadSearchHistory() }
    val dark = isAppDarkTheme()
    Column(modifier.padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(4.dp))
        if (history.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "输入关键词，搜索虎扑全站帖子",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("搜索历史", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onClearAsk) {
                    Icon(Icons.Rounded.Delete, contentDescription = "清空历史", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            // 1.192i: 搜索历史改**多行自适应换行**（这一页除搜索框外就是它，空间足够）；
            // 单条限宽，避免超长关键词把整行顶出屏幕
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                history.forEach { h ->
                    // 1.192h: 「搜索历史条目」是按钮语义（点击执行一次搜索）→ LiquidButton
                    LiquidButton(
                        onClick = { onPick(h) },
                        modifier = Modifier.widthIn(max = 260.dp),
                        shape = RoundedCornerShape(50),
                        fill = buttonFill(dark),
                        border = buttonBorder(dark),
                        // 1.192i: 稍微增大（32 → 36dp，内边距 12 → 14，字 13 → 14sp）
                        height = 36.dp,
                        contentPadding = 14.dp,
                        arrangement = Arrangement.Start,
                    ) {
                        Text(h, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(14.dp)
                                .clickable { HupuPrefs.removeSearchHistory(h); historyTick++ },
                        )
                    }
                }
            }
        }
    }
}

/** 搜索结果流（FeedList 风格 + 无限滚动） */
@Composable
private fun SearchResults(
    items: List<HupuSearchItem>,
    canLoadMore: Boolean,
    loadingMore: Boolean,
    onLoadMore: () -> Unit,
    onOpen: (HupuSearchItem) -> Unit,
) {
    val listState = rememberLazyListState()
    // 浏览流关键词过滤：标题/分区命中 → 搜索结果不显示
    val filteredItems = remember(items, HupuPrefs.filterVersion) {
        val kw = HupuPrefs.loadFilterKeywords()
        if (kw.isEmpty) items
        else items.filterNot { t -> HupuFilter.blockedThread(t.title, t.forumName, kw) }
    }
    val shouldLoadMore by remember(filteredItems, canLoadMore, loadingMore) {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            canLoadMore && !loadingMore && last >= listState.layoutInfo.totalItemsCount - 5
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(filteredItems, key = { it.tid }) { it ->
            SearchItemRow(it, onOpen)
        }
        if (canLoadMore) {
            item(key = "search-more") {
                Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                }
            }
        }
    }
}

/** 一条搜索结果（左文右图） */
@Composable
private fun SearchItemRow(it: HupuSearchItem, onOpen: (HupuSearchItem) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onOpen(it) }
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                it.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 21.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (it.desc.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    it.desc,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(it.forumName, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(10.dp))
                Text("${formatCount(it.replies)} 回复", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (it.addTimeDisplay.isNotBlank()) {
                    Spacer(Modifier.width(10.dp))
                    Text(it.addTimeDisplay, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
        normalizeCover(it.picture)?.let { pic ->
            AsyncImage(
                // 1.190: 100×76dp 小图位走 CDN 缩略图，避免下原图
                model = thumbnailUrl(pic, 320),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 100.dp, height = 76.dp)
                    .clip(RoundedCornerShape(10.dp)),
            )
        }
    }
}

// ---------- 专区筛选（半屏弹出菜单） ----------

/**
 * 专区选择器（ModalBottomSheet）：
 * - 1.182: 固定 72% 屏高、默认展开（打开即完整展示，不再停在小半屏）
 * - 顶部「所有专区」恢复全站搜索；大类 chips 过滤；搜索框过滤版块名
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ForumPickerSheet(
    categories: List<HupuCategory>,
    selectedTopicId: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    // 1.182: 固定 72% 高度 + 默认展开（skipPartiallyExpanded=true → 打开即 Expanded，不再停在半屏）
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var selectedCate by remember { mutableStateOf<String?>(null) }
    // 1.192k: 大类 chips 条补「选中项居中」便捷特性（与三大页顶部横滑条同一套）。
    // 序号 0 是「全部」，大类从 1 开始。
    val cateBarState = rememberLazyListState()
    val cateSelIdx =
        if (selectedCate == null) 0 else 1 + categories.indexOfFirst { it.cateId == selectedCate }
    LaunchedEffect(cateSelIdx) {
        if (cateSelIdx >= 0) cateBarState.animateChipCenterTo(cateSelIdx)
    }

    // 版块全集（跨大类去重）
    val allTopics = remember(categories) { categories.flatMap { it.topics }.distinctBy { it.topicId } }
    // 过滤：搜索优先于大类过滤
    val filtered = remember(allTopics, query, selectedCate, categories) {
        val base = if (selectedCate == null) allTopics
        else categories.firstOrNull { it.cateId == selectedCate }?.topics ?: allTopics
        if (query.isBlank()) base.distinctBy { it.topicId }
        else allTopics.filter { it.name.contains(query.trim(), ignoreCase = true) }.distinctBy { it.topicId }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.72f)) {
            // 标题
            Text(
                "选择专区",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            // 「所有专区」行（null 筛选 = 全站）
            val allSelected = selectedTopicId == null
            // 1.192k: 与下方搜索框对齐 —— 左右边距 12 → 16（此前比搜索框宽 8dp），
            // 圆角 12 → 22（与搜索框的 shape 保持一致）
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (allSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else Color.Transparent)
                    .clickable { onSelect(null) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "所有专区",
                    fontSize = 15.sp,
                    fontWeight = if (allSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (allSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                if (allSelected) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
            }
            // 搜索框
            OutlinedTextField(
                colors = huzaiFieldColors(),
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
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
            // 大类 chips
            LazyRow(
                state = cateBarState,
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
                    Chip(text = c.name, selected = selectedCate == c.cateId) {
                        selectedCate = if (selectedCate == c.cateId) null else c.cateId
                    }
                }
            }
            // 版块列表（选择即关闭并重搜）
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            ) {
                if (categories.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
                items(filtered, key = { it.topicId }) { t ->
                    val isSelected = t.topicId == selectedTopicId
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else Color.Transparent)
                            .clickable { onSelect(t.topicId) }
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            t.name,
                            fontSize = 15.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            // 1.192l: 文本直接吃满剩余宽度，对勾才贴右。
                            // 此前是 weight(1f, fill = false) + 后面一个 weight(1f) 的 Spacer：
                            // 两个 weight 各分到一半剩余宽度，而 Text 因 fill = false 只占内容宽度，
                            // 没用掉的配额不会重新分配 → 对勾落在约 70% 处（看着「在中间」）。
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (isSelected) {
                            Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}
