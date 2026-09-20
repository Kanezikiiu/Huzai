package com.java.myapplication.ui.pages

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import com.java.myapplication.ui.components.animateChipCenterTo
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.java.myapplication.data.HupuBoardPage
import com.java.myapplication.data.HupuFloorReplies
import com.java.myapplication.data.HupuReply
import com.java.myapplication.data.HupuThread
import com.java.myapplication.data.HupuThreadDetail
import com.java.myapplication.data.HupuHistoryEntry
import com.java.myapplication.data.HupuPrefs
import com.java.myapplication.data.HupuUiSignals
import com.java.myapplication.data.HupuRepository
import com.java.myapplication.data.HupuTopicInfo
import com.java.myapplication.data.SortTab
import com.java.myapplication.ui.components.*

/** 首页：全站热帖（默认）+ 热门话题流（单选切换、排序、无限滚动） */
@Composable
fun HomePage(modifier: Modifier = Modifier) {
    val repo = remember { HupuRepository() }
    var page by remember { mutableStateOf<HupuBoardPage?>(null) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    suspend fun load(refresh: Boolean = false) {
        if (refresh) refreshing = true else loading = true
        failed = false
        // 1.133: 首屏偶发失败自动重试（递增退避，共 3 次尝试），对齐评分页容错——
        // 避免冷启动网络抖动「一次失败即永久显示失败态」。
        var p = repo.board(refresh = refresh)
        var tries = 0
        while (p == null && tries < 2) {
            tries++
            kotlinx.coroutines.delay(600L * tries)
            p = repo.board(refresh = refresh)
        }
        page = p
        failed = p == null
        loading = false
        refreshing = false
    }

    LaunchedEffect(Unit) { load() }

    // Tab 重选刷新信号：已选中时再点首页 Tab → 强刷热帖流（列表在别处也能立即刷新）
    var tabResetTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(HupuUiSignals.homeTabTap) {
        if (HupuUiSignals.homeTabTap == 0) return@LaunchedEffect
        tabResetTick++
        load(refresh = true)
    }

    val scope = rememberCoroutineScope()
    val data = page
    when {
        data != null -> FeedContent(
            data = data,
            isRefreshing = refreshing,
            onRefresh = { scope.launch { load(refresh = true) } },
            hotResetTick = tabResetTick,
            modifier = modifier,
        )
        loading -> SkeletonHome(modifier)
        else -> ErrorRetry(modifier) { scope.launch { load(refresh = true) } }
    }
}

// ---------- 内容 ----------

@Composable
private fun FeedContent(
    data: HupuBoardPage,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    hotResetTick: Int = 0,
    modifier: Modifier = Modifier,
) {
    val repo = remember { HupuRepository() }
    // 单选流切换："hot"=全站热帖（默认）| 其他=话题 url（如 /topic-daily）
    var selected by remember { mutableStateOf("hot") }
    // 话题内的排序 tab url（null=默认最新回复）
    var selectedSort by remember { mutableStateOf<String?>(null) }
    // 各话题流的累积状态：key = "话题url#排序url"
    val feedStates = remember { mutableStateMapOf<String, TopicFeedState>() }
    // 各话题的排序 tabs（服务器返回）
    val topicSorts = remember { mutableStateMapOf<String, List<SortTab>>() }
    // 正在首屏加载的话题 url（null=无）
    var loadingTopic by remember { mutableStateOf<String?>(null) }
    // 强制刷新标记 + 重载触发器
    var forceRefresh by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    // ---------- 帖子详情二级页（盖入式；状态外置：返回再进不重载） ----------
    var openedThread by remember { mutableStateOf<HupuThread?>(null) }
    var threadClosing by remember { mutableStateOf(false) }
    var threadLoading by remember { mutableStateOf(false) }
    var threadLoadingMore by remember { mutableStateOf(false) }
    var threadSortSeq by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val threadDetails = remember { mutableStateMapOf<String, HupuThreadDetail>() }

    // ---------- 楼中楼三级页（数据状态同样外置缓存） ----------
    val floorStates = remember { mutableStateMapOf<String, HupuFloorReplies>() }
    val floorLoading = remember { mutableStateMapOf<String, Boolean>() }
    val floorLoadingMore = remember { mutableStateMapOf<String, Boolean>() }

    /** 打开楼层：同步置 loading + 错峰网络请求（帖子详情同款策略） */
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

    /** 楼中楼上滑翻页（maxpid=最后一条直接子回复） */
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

    // ---------- 搜索二级页 ----------
    var searchOpen by remember { mutableStateOf(false) }

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
        // 加载态在交互瞬间同步置位（频道页同款）：无缓存→骨架屏第一帧出现
        threadLoading = !threadDetails.containsKey(t.tid)
    }

    fun closeThread() {
        threadClosing = true
    }

    // 打开的帖子：无缓存则拉取（loading 已在打开瞬间置位；网络请求错峰到盖入动画完成后）
    // 1.123: 详情页时效性——每次进入都强刷；有缓存秒开第一帧，新数据到达后无缝替换
    LaunchedEffect(openedThread?.tid) {
        val t = openedThread ?: return@LaunchedEffect
        val tid = t.tid
        val mySeq = threadSortSeq
        if (threadDetails.containsKey(tid)) {
            // 秒开旧数据，后台强刷换新（已翻页过的帖子按 pid 合并保留深层回复）
            val old = threadDetails[tid]
            val fresh = repo.threadDetail(tid, refresh = true)
            if (fresh != null && openedThread?.tid == tid && mySeq == threadSortSeq) {
                threadDetails[tid] = repo.mergeThreadDetail(old, fresh)
            }
        } else {
            // 无内存缓存：先用磁盘缓存秒开（如有），再后台强刷
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
                delay(300) // 盖入动画 280ms + 余量
                if (threadDetails.containsKey(tid)) return@LaunchedEffect
                val d = repo.threadDetail(tid)
                if (d != null && mySeq == threadSortSeq) threadDetails[tid] = d
                if (openedThread?.tid == tid) threadLoading = false
            }
        }
    }

    // 刷新计数：每次成功刷新后 +1，驱动列表回顶
    var refreshVersion by remember { mutableIntStateOf(0) }

    // 主页频道：用户自定义优先，否则官方热门（「我的」页保存后版本号变化自动刷新）
    val effectiveTopics = remember(data.hotTopics, HupuPrefs.homeTopicsVersion) {
        HupuPrefs.effectiveHomeTopics(data.hotTopics)
    }
    // 正在浏览的频道被移除时，退回热帖（否则该流选择器失效）
    LaunchedEffect(effectiveTopics) {
        if (selected != "hot" && effectiveTopics.none { it.url == selected }) {
            selected = "hot"
        }
    }

    val currentSort = if (selected == "hot") null else selectedSort
    val feedKey = if (selected == "hot") "hot" else "$selected#${currentSort ?: selected}"

    // 话题首屏加载：有缓存直接显示，无缓存才进加载态（下拉刷新时绕过缓存）
    LaunchedEffect(feedKey, refreshTick) {
        if (selected == "hot") {
            loadingTopic = null
            return@LaunchedEffect
        }
        val force = forceRefresh
        forceRefresh = false
        if (!force && feedStates.containsKey(feedKey)) {
            loadingTopic = null
            return@LaunchedEffect
        }
        loadingTopic = selected
        val p = repo.topicPage(currentSort ?: selected, refresh = force)
        if (p != null) {
            feedStates[feedKey] = TopicFeedState(
                threads = p.threads,
                page = p.page,
                totalPages = p.totalPages,
            )
            topicSorts[selected] = p.sortTabs
            if (force) refreshVersion++
        } else {
            feedStates[feedKey] = TopicFeedState() // 空列表=失败
        }
        if (loadingTopic == selected) loadingTopic = null
    }

    fun loadMore() {
        val state = feedStates[feedKey] ?: return
        if (state.loadingMore || state.page >= state.totalPages || state.threads.isEmpty()) return
        feedStates[feedKey] = state.copy(loadingMore = true)
        scope.launch {
            val p = repo.topicPage(currentSort ?: selected, page = state.page + 1)
            val cur = feedStates[feedKey]
            if (p != null && cur != null) {
                val merged = (cur.threads + p.threads).distinctBy { it.tid }
                feedStates[feedKey] = cur.copy(
                    threads = merged,
                    page = p.page,
                    totalPages = p.totalPages,
                    loadingMore = false,
                )
            } else if (cur != null) {
                feedStates[feedKey] = cur.copy(loadingMore = false)
            }
        }
    }

    fun forceReload() {
        forceRefresh = true
        refreshTick++
    }

    // 1.186: 顶部 Tab 左右滑动切换（仅本大页面内；顺序与横滑条一致：「热帖」+ 各话题）
    val tabUrls = remember(effectiveTopics) { listOf("hot") + effectiveTopics.map { it.url } }
    fun swipeTab(delta: Int) {
        val cur = tabUrls.indexOf(selected).let { if (it >= 0) it else 0 }
        val ni = cur + delta
        if (ni !in tabUrls.indices) return
        val target = tabUrls[ni]
        if (target == selected) return
        if (target == "hot") {
            selected = "hot"
        } else {
            selected = target
            selectedSort = null
        }
    }

    Box(modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .tabSwipeSwitch(
                onPrevious = { swipeTab(-1) },
                onNext = { swipeTab(1) },
            ),
    ) {
        PageHeader(title = "虎扑") {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { searchOpen = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = "搜索",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        // 话题横滑条：固定在标题栏下方，单选切换内容流（再点已选返回热帖）
        TopicBar(effectiveTopics, selected) {
            if (selected == it) selected = "hot"
            else {
                selected = it
                selectedSort = null
            }
        }
        // Tab 重选刷新信号（话题流侧）：已选中时再点首页 Tab → 强刷当前话题流并回顶
        LaunchedEffect(HupuUiSignals.homeTabTap) {
            if (HupuUiSignals.homeTabTap == 0) return@LaunchedEffect
            forceReload()
        }
        // 排序子 Tab（仅话题流显示）
        if (selected != "hot") {
            val sorts = topicSorts[selected]
            if (sorts != null) SortBar(sorts, currentSort ?: selected) { selectedSort = it }
        }
        val pullState = rememberPullToRefreshState()
        // 已有内容的流刷新时走顶部指示器（内容保持不动）；仅首次加载（无缓存）才显示骨架
        val inPlaceRefreshing = loadingTopic != null && feedStates[feedKey] != null
        PullToRefreshBox(
            isRefreshing = isRefreshing || inPlaceRefreshing,
            onRefresh = { if (selected == "hot") onRefresh() else forceReload() },
            state = pullState,
            modifier = Modifier.fillMaxSize(),
        ) {
            Crossfade(
                targetState = selected,
                animationSpec = tween(180),
                label = "feedSwitch",
            ) { sel ->
                // sel 是本层自己的目标态：淡出中的旧页面内容不会被新选中项污染（解决残留/闪烁）
                val selSort = if (sel == "hot") null else if (sel == selected) selectedSort else null
                when {
                    // 默认：全站热帖（70 条一次性，无分页）
                    sel == "hot" -> FeedList(
                        threads = data.threads,
                        useBigCards = true,
                        resetKey = if (sel == selected) "hot-$refreshVersion-$hotResetTick" else "hot",
                        onOpenThread = { if (sel == selected) openThread(it) },
                    )
                    else -> {
                        val selKey = "$sel#${selSort ?: sel}"
                        val state = feedStates[selKey]
                        when {
                            // 首次加载（无缓存）
                            state == null -> SkeletonHome()
                            // 加载失败/为空
                            state.threads.isEmpty() -> ErrorRetry { forceReload() }
                            // 话题流：无限滚动翻页
                            else -> FeedList(
                                threads = state.threads,
                                useBigCards = true,
                                canLoadMore = state.page < state.totalPages,
                                loadingMore = state.loadingMore,
                                onLoadMore = { if (sel == selected) loadMore() },
                                resetKey = if (sel == selected) "$selKey-$refreshVersion" else selKey,
                                onOpenThread = { if (sel == selected) openThread(it) },
                            )
                    }
                }
            }
        }
    }
    } // Column 关闭（overlay 挂 Box 内、Column 外）

    // ---------- 搜索二级页：盖入式（挂在 Box 内、帖子详情之前——同 zIndex 2 时组合顺序决定层叠，详情页绘制在搜索页之上） ----------
    // 搜索结果打开帖子：不关闭搜索页——详情页直接盖在搜索页之上，
    // 关闭详情后回到搜索页（状态保留、结果流位置不变）
    if (searchOpen) {
        SearchPage(
            onOpenThread = { s ->
                openThread(
                    HupuThread(
                        tid = s.tid,
                        title = s.title,
                        cover = s.picture,
                        replies = s.replies,
                        lights = s.lights,
                    )
                )
            },
            onClose = { searchOpen = false },
        )
    }

    // ---------- 帖子详情二级页：盖入式转场（组合顺序在搜索页之后 → 盖在搜索页之上） ----------
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
    } // Box
}

/** 话题横滑条（固定）：单选切换，选中 chip 主题色高亮 */
@Composable
private fun TopicBar(
    topics: List<HupuTopicInfo>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val barState = rememberLazyListState()
    val selIdx = if (selected == "hot") 0 else 1 + topics.indexOfFirst { it.url == selected }
    LaunchedEffect(selected) { if (selIdx >= 0) barState.animateChipCenterTo(selIdx) }
    LazyRow(
        state = barState,
        modifier = Modifier
            .fillMaxWidth()
            // 可视窗口与下方条目对齐（左右 16dp），越线滚动内容被裁剪覆盖
            .padding(horizontal = 16.dp)
            .clipToBounds()
            .padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 「热帖」chip：默认流（/all-gambia 全站热帖榜）
        item(key = "hot-chip") {
            Chip(text = "热帖", selected = selected == "hot") { onSelect("hot") }
        }
        items(topics, key = { it.topicId + it.url }) { t ->
            Chip(
                text = t.name,
                logoUrl = normalizeCover(t.logo),
                selected = selected == t.url,
            ) { onSelect(t.url) }
        }
    }
}