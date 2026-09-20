package com.java.myapplication.ui.pages

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import kotlin.coroutines.cancellation.CancellationException
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import com.java.myapplication.ui.components.animateChipCenterTo
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.java.myapplication.data.HupuCategory
import com.java.myapplication.data.HupuHistoryEntry
import com.java.myapplication.data.HupuPrefs
import com.java.myapplication.data.HupuFloorReplies
import com.java.myapplication.data.HupuReply
import com.java.myapplication.data.HupuThread
import com.java.myapplication.data.HupuThreadDetail
import com.java.myapplication.data.HupuRepository
import com.java.myapplication.data.HupuTopicInfo
import com.java.myapplication.data.SortTab
import com.java.myapplication.ui.components.Chip
import com.java.myapplication.ui.components.ErrorRetry
import com.java.myapplication.ui.components.FeedList
import com.java.myapplication.ui.components.PageHeader
import com.java.myapplication.ui.components.SecondaryPage
import com.java.myapplication.ui.components.SortBar
import com.java.myapplication.ui.components.SkeletonHome
import com.java.myapplication.ui.components.tapGuard
import com.java.myapplication.ui.components.TopicFeedState
import com.java.myapplication.ui.components.normalizeCover
import com.java.myapplication.ui.components.tabSwipeSwitch

/**
 * 专区页：13 大类 → 版块网格（3 列）→ 版块话题流（盖入式二级页）。
 * 二级页数据外置持有，返回再进不重载。
 */
@Composable
fun ZonePage(modifier: Modifier = Modifier) {
    val repo = remember { HupuRepository() }

    // ---------- 一级页状态 ----------
    var categories by remember { mutableStateOf<List<HupuCategory>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var cateRefreshTick by remember { mutableIntStateOf(0) }
    // 当前选中大类（默认第 1 个）
    var selectedCateId by remember { mutableStateOf("") }

    // ---------- 二级页状态（外置，关闭再进不重载） ----------
    var openedTopic by remember { mutableStateOf<HupuTopicInfo?>(null) }
    var overlayClosing by remember { mutableStateOf(false) }
    val feedStates = remember { mutableStateMapOf<String, TopicFeedState>() }
    val topicSorts = remember { mutableStateMapOf<String, List<SortTab>>() }
    var loadingTopicKey by remember { mutableStateOf<String?>(null) }
    var refreshVersion by remember { mutableIntStateOf(0) }
    var selectedSort by remember { mutableStateOf<String?>(null) }

    // 大类加载（与首页共享 /all-gambia 缓存，进过首页后秒开）
    LaunchedEffect(cateRefreshTick) {
        loading = cateRefreshTick == 0
        // 1.133: 与首页/评分页一致的容错——空结果递增退避重试，避免首屏偶发失败后长期空态
        var list = repo.categories(refresh = cateRefreshTick > 0)
        var tries = 0
        while (list.isEmpty() && tries < 2) {
            tries++
            kotlinx.coroutines.delay(600L * tries)
            list = repo.categories(refresh = cateRefreshTick > 0)
        }
        if (list.isNotEmpty()) {
            categories = list
            if (selectedCateId.isEmpty() || list.none { it.cateId == selectedCateId }) {
                selectedCateId = list.first().cateId
            }
        }
        loading = false
    }

    fun openTopic(t: HupuTopicInfo) {
        selectedSort = null
        overlayClosing = false
        SecondaryPage.enter()
        openedTopic = t
    }

    fun closeTopic() {
        // 先播返回动画（overlay 滑回右侧），动画结束后真正移除
        overlayClosing = true
    }

    // ---------- 帖子详情三级页（盖入式；从话题流内点帖进入） ----------
    var openedThread by remember { mutableStateOf<HupuThread?>(null) }
    var threadClosing by remember { mutableStateOf(false) }
    var threadLoading by remember { mutableStateOf(false) }
    var threadLoadingMore by remember { mutableStateOf(false) }
    var threadSortSeq by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val threadDetails = remember { mutableStateMapOf<String, HupuThreadDetail>() }

    // ---------- 楼中楼（数据外置缓存；打开瞬间同步置 loading） ----------
    val floorStates = remember { mutableStateMapOf<String, HupuFloorReplies>() }
    val floorLoading = remember { mutableStateMapOf<String, Boolean>() }
    val floorLoadingMore = remember { mutableStateMapOf<String, Boolean>() }

    fun openThread(t: HupuThread) {
        HupuPrefs.addHistory(
            HupuHistoryEntry(
                tid = t.tid,
                title = t.title,
                topicName = t.topic?.name ?: "",
                lights = t.lights,
                replies = t.replies,
                read = t.read,
                visitedAt = System.currentTimeMillis(),
            )
        )
        threadClosing = false
        SecondaryPage.enter()
        openedThread = t
        // 同步置位（频道页同款）：无缓存→骨架屏第一帧
        threadLoading = !threadDetails.containsKey(t.tid)
    }

    fun closeThread() {
        threadClosing = true
    }

    // 打开的帖子：无缓存则拉取（错峰到盖入动画完成后）
    // 1.123: 详情页时效性——每次进入都强刷；有缓存秒开第一帧，新数据到达后无缝替换
    LaunchedEffect(openedThread?.tid) {
        val t = openedThread ?: return@LaunchedEffect
        val tid = t.tid
        val mySeq = threadSortSeq
        if (threadDetails.containsKey(tid)) {
            val old = threadDetails[tid]
            val fresh = repo.threadDetail(tid, refresh = true)
            if (fresh != null && openedThread?.tid == tid && mySeq == threadSortSeq) {
                threadDetails[tid] = repo.mergeThreadDetail(old, fresh)
            }
        } else {
            threadLoading = true
            val cached = repo.threadDetailCached(tid)
            if (cached != null && openedThread?.tid == tid && mySeq == threadSortSeq) {
                threadDetails[tid] = cached
                threadLoading = false
                val fresh = repo.threadDetail(tid, refresh = true)
                if (fresh != null && openedThread?.tid == tid && mySeq == threadSortSeq) {
                    threadDetails[tid] = repo.mergeThreadDetail(threadDetails[tid], fresh)
                }
            } else {
                delay(300)
                if (threadDetails.containsKey(tid)) return@LaunchedEffect
                val d = repo.threadDetail(tid)
                if (d != null && mySeq == threadSortSeq) threadDetails[tid] = d
                if (openedThread?.tid == tid) threadLoading = false
            }
        }
    }

    val scope = rememberCoroutineScope()
    val current = categories?.find { it.cateId == selectedCateId }

    fun openFloor(tid: String, r: HupuReply) {
        if (floorStates.containsKey(r.pid)) return
        floorLoading[r.pid] = true
        scope.launch {
            delay(300)
            if (floorStates.containsKey(r.pid)) {
                floorLoading[r.pid] = false
                return@launch
            }
            val fr = repo.floorReplies(tid, r)
            if (fr != null) floorStates[r.pid] = fr
            floorLoading[r.pid] = false
        }
    }

    fun loadFloorMore(tid: String, pid: String, fr: HupuFloorReplies) {
        if (floorLoadingMore[pid] == true || !fr.hasMore) return
        val lastPid = fr.subReplies.lastOrNull { it.depth == 0 }?.pid ?: return
        floorLoadingMore[pid] = true
        scope.launch {
            val next = repo.floorReplies(tid, HupuReply(pid = pid), maxpid = lastPid)
            val cur = floorStates[pid]
            if (next != null && cur != null) {
                val merged = (cur.subReplies + next.subReplies).distinctBy { it.pid }
                floorStates[pid] = cur.copy(subReplies = merged, hasMore = next.hasMore)
            }
            floorLoadingMore[pid] = false
        }
    }
    val opened = openedTopic

    // 1.186: 顶部大类 Tab 左右滑动切换（仅本大页面内）
    val cateIds = remember(categories) { categories?.map { it.cateId } ?: emptyList() }
    fun swipeCate(delta: Int) {
        val cur = cateIds.indexOf(selectedCateId)
        if (cur < 0) return
        val ni = cur + delta
        if (ni !in cateIds.indices) return
        selectedCateId = cateIds[ni]
    }

    Box(modifier.fillMaxSize()) {
        // ---------- 一级页 ----------
        Column(
            Modifier
                .fillMaxSize()
                .tabSwipeSwitch(
                    onPrevious = { swipeCate(-1) },
                    onNext = { swipeCate(1) },
                ),
        ) {
            PageHeader(title = "专区")
            when {
                categories == null && loading -> SkeletonHome()
                categories == null -> ErrorRetry { cateRefreshTick++ }
                categories!!.isEmpty() -> ErrorRetry { cateRefreshTick++ }
                else -> {
                    CateBar(categories!!, selectedCateId) { selectedCateId = it }
                    val topics = current?.topics.orEmpty()
                    if (topics.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "该分类暂无版块",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 140.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            gridItems(topics, key = { it.topicId + it.url }) { t ->
                                TopicCell(t) { openTopic(t) }
                            }
                        }
                    }
                }
            }
        }

        // ---------- 二级页：版块话题流（盖入式转场） ----------
        if (opened != null) {
            TopicFeedOverlay(
                topic = opened,
                closing = overlayClosing,
                feedStates = feedStates,
                topicSorts = topicSorts,
                loadingTopicKey = loadingTopicKey,
                refreshVersion = refreshVersion,
                selectedSort = selectedSort,
                onSelectSort = { selectedSort = it },
                onBack = { closeTopic() },
                onExitStart = { SecondaryPage.exit() },
                onClosed = {
                    overlayClosing = false
                    selectedSort = null
                    openedTopic = null
                },
                onFirstLoad = { url, sort ->
                    // 首次进入或切排序：无缓存才加载
                    val key = "$url#${sort ?: url}"
                    if (feedStates.containsKey(key)) return@TopicFeedOverlay
                    scope.launch {
                        loadingTopicKey = key
                        val p = repo.topicPage(sort ?: url)
                        if (p != null) {
                            feedStates[key] = TopicFeedState(
                                threads = p.threads,
                                page = p.page,
                                totalPages = p.totalPages,
                            )
                            topicSorts[url] = p.sortTabs
                        } else {
                            feedStates[key] = TopicFeedState() // 空=失败
                        }
                        loadingTopicKey = null
                    }
                },
                onRefresh = {
                    val url = opened.url
                    val sort = selectedSort
                    val key = "$url#${sort ?: url}"
                    scope.launch {
                        loadingTopicKey = key
                        val p = repo.topicPage(sort ?: url, refresh = true)
                        if (p != null) {
                            feedStates[key] = TopicFeedState(
                                threads = p.threads,
                                page = p.page,
                                totalPages = p.totalPages,
                            )
                            topicSorts[url] = p.sortTabs
                            refreshVersion++
                        }
                        loadingTopicKey = null
                    }
                },
                onLoadMore = { url, sort, page ->
                    scope.launch {
                        val key = "$url#${sort ?: url}"
                        val state = feedStates[key] ?: return@launch
                        if (state.loadingMore || state.page >= state.totalPages) return@launch
                        feedStates[key] = state.copy(loadingMore = true)
                        val p = repo.topicPage(sort ?: url, page = state.page + 1)
                        val cur = feedStates[key]
                        if (p != null && cur != null) {
                            val merged = (cur.threads + p.threads).distinctBy { it.tid }
                            feedStates[key] = cur.copy(
                                threads = merged,
                                page = p.page,
                                totalPages = p.totalPages,
                                loadingMore = false,
                            )
                        } else if (cur != null) {
                            feedStates[key] = cur.copy(loadingMore = false)
                        }
                    }
                },
                onOpenThread = { openThread(it) },
            )
        }

        // ---------- 帖子详情三级页：盖入式转场（盖过话题流二级页） ----------
        val ot = openedThread
        if (ot != null) {
            val d = threadDetails[ot.tid]
            ThreadDetailOverlay(
                tid = ot.tid,
                titleText = ot.title,
                detail = d,
                loading = threadLoading,
                loadingMore = threadLoadingMore,
                closing = threadClosing,
                onBack = { closeThread() },
                onExitStart = { SecondaryPage.exit() },
                onClosed = {
                    threadClosing = false
                    openedThread = null
                },
                onRefresh = {
                    val mySeq = threadSortSeq
                    scope.launch {
                        threadLoading = true
                        val cur = threadDetails[ot.tid]
                        val desc = cur?.descReplies == true
                        val nd = repo.threadDetailDirected(ot.tid, desc, refresh = true)
                        if (nd != null && mySeq == threadSortSeq && openedThread?.tid == ot.tid) threadDetails[ot.tid] = if (desc) nd else repo.mergeThreadDetail(cur, nd)
                        threadLoading = false
                    }
                },
                onLoadMore = {
                    val cur = threadDetails[ot.tid] ?: return@ThreadDetailOverlay
                    if (threadLoadingMore || !repo.hasMoreReplies(cur)) return@ThreadDetailOverlay
                    val mySeq = threadSortSeq
                    scope.launch {
                        threadLoadingMore = true
                        val next = repo.threadRepliesNext(ot.tid, cur)
                        if (next != null && mySeq == threadSortSeq && openedThread?.tid == ot.tid) {
                            val merged = (cur.replies + next.replies).distinctBy { it.pid }
                            threadDetails[ot.tid] = next.copy(replies = merged)
                        }
                        threadLoadingMore = false
                    }
                },
                onSortChanged = { desc ->
                    // 1.185c: 每次意图推进序号——迟到的旧响应据此被丢弃（连点不再来回闪）
                    val mySeq = ++threadSortSeq
                    val cur = threadDetails[ot.tid]
                    if (cur != null && cur.descReplies != desc) {
                        scope.launch {
                            val d = repo.repliesDirected(ot.tid, cur, desc)
                            if (d != null && mySeq == threadSortSeq && openedThread?.tid == ot.tid) threadDetails[ot.tid] = d
                        }
                    }
                },
                floorStates = floorStates,
                floorLoading = floorLoading.filterValues { it }.keys,
                floorLoadingMore = floorLoadingMore.filterValues { it }.keys,
                 onOpenFloor = { r -> openFloor(ot.tid, r) },
                 onFloorLoadMore = { pid, fr -> loadFloorMore(ot.tid, pid, fr) },
             )
        }
    }
}

/**
 * 二级页：版块话题流。
 * 转场：盖入式——进入时从右侧滑入（下层完全静止），返回时旧页滑回右侧后移除。
 */
@Composable
private fun TopicFeedOverlay(
    topic: HupuTopicInfo,
    closing: Boolean,
    feedStates: Map<String, TopicFeedState>,
    topicSorts: Map<String, List<SortTab>>,
    loadingTopicKey: String?,
    refreshVersion: Int,
    selectedSort: String?,
    onSelectSort: (String) -> Unit,
    onBack: () -> Unit,
    /** 1.183: 退场动画开始即回调——父级据此同步减二级页计数，使 Tab 栏与页面同步 Q 弹回归（与「我的」页二级页同款手感） */
    onExitStart: () -> Unit = {},
    onClosed: () -> Unit,
    onFirstLoad: (url: String, sort: String?) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: (url: String, sort: String?, page: Int) -> Unit,
    onOpenThread: (HupuThread) -> Unit,
) {
    // 盖入动画：进入 0→1；返回动画结束后由父级移除本组件
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(280))
    }
    LaunchedEffect(closing) {
        if (closing) {
            onExitStart()
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

    val key = "${topic.url}#${selectedSort ?: topic.url}"
    val state = feedStates[key]
    val sorts = topicSorts[topic.url]

    Box(
        Modifier
            .fillMaxSize()
            .zIndex(2f)
            .graphicsLayer {
                translationX = (1f - progress.value) * size.width
            }
            .background(MaterialTheme.colorScheme.background)
            // 穿透守卫：排序条空隙/失败态空白点击由本层兜底消费，不落穿到下层
            .tapGuard(),
    ) {
        Column(Modifier.fillMaxSize()) {
            // 顶栏：返回 + 版块名 + 热度
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
                        topic.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (topic.hotText.isNotBlank()) {
                        Text(
                            "${topic.hotText} 热度",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // 刷新按钮（已在内容时手动刷新）
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { onRefresh() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = "刷新",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            // 排序子 Tab（服务器返回）
            if (sorts != null && sorts.isNotEmpty()) {
                SortBar(sorts, selectedSort ?: topic.url) { onSelectSort(it) }
            }
            // 列表区：排序切换用 Crossfade 防闪（每层用自己的目标态，不被污染）
            androidx.compose.animation.Crossfade(
                targetState = selectedSort,
                animationSpec = tween(180),
                label = "zoneFeedSwitch",
            ) { sel ->
                val selKey = "${topic.url}#${sel ?: topic.url}"
                val selState = feedStates[selKey]
                val pullState = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = loadingTopicKey == selKey,
                    onRefresh = onRefresh,
                    state = pullState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    when {
                        selState == null -> SkeletonHome()
                        selState.threads.isEmpty() -> ErrorRetry { onRefresh() }
                        else -> FeedList(
                            threads = selState.threads,
                            useBigCards = true,
                            canLoadMore = selState.page < selState.totalPages,
                            loadingMore = selState.loadingMore,
                            onLoadMore = { onLoadMore(topic.url, sel, selState.page) },
                            resetKey = "$selKey-$refreshVersion",
                            onOpenThread = onOpenThread,
                        )
                    }
                }
            }
        }
    }
    // 首次进入 / 切换排序：无缓存才加载
    LaunchedEffect(topic.url, selectedSort) {
        val key = "${topic.url}#${selectedSort ?: topic.url}"
        if (!feedStates.containsKey(key)) onFirstLoad(topic.url, selectedSort)
    }
}

/** 大类横滑条：单选，16dp 限宽裁剪（与首页一致） */
@Composable
private fun CateBar(
    categories: List<HupuCategory>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val barState = rememberLazyListState()
    val selIdx = categories.indexOfFirst { it.cateId == selected }
    LaunchedEffect(selected) { if (selIdx >= 0) barState.animateChipCenterTo(selIdx) }
    LazyRow(
        state = barState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clipToBounds()
            .padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(categories, key = { it.cateId }) { c ->
            Chip(
                text = c.name,
                logoUrl = normalizeCover(c.logo),
                selected = selected == c.cateId,
            ) { onSelect(c.cateId) }
        }
    }
}

/** 版块格子：圆形 logo + 名称 + 热度 */
@Composable
private fun TopicCell(t: HupuTopicInfo, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AsyncImage(
            model = normalizeCover(t.logo),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(56.dp).clip(CircleShape),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            t.name,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (t.hotText.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                "${t.hotText} 热度",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}