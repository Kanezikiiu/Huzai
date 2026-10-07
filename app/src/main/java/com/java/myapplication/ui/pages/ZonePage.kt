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
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import com.java.myapplication.data.pickTopicSortKey
import com.java.myapplication.data.SortTab
import com.java.myapplication.ui.components.Chip
import com.java.myapplication.ui.components.ErrorRetry
import com.java.myapplication.ui.components.FeedList
import com.java.myapplication.ui.components.HupuIcons
import com.java.myapplication.ui.components.PageHeader
import com.java.myapplication.ui.glass.FrostedHeaderLayout
import com.java.myapplication.ui.components.SecondaryPage
import com.java.myapplication.ui.components.SortBar
import com.java.myapplication.ui.components.SORT_BAR_TOTAL_HEIGHT
import com.java.myapplication.ui.components.SkeletonHome
import com.java.myapplication.ui.components.tapGuard
import com.java.myapplication.ui.components.TopicFeedState
import com.java.myapplication.ui.components.normalizeCover
import com.java.myapplication.ui.components.tabSwipeSwitch
import com.java.myapplication.ui.components.CenteredTopBar
import com.java.myapplication.ui.components.LiquidBackButton
import com.java.myapplication.ui.components.LiquidIconButton
import com.java.myapplication.ui.components.chipBarClip
import com.java.myapplication.ui.components.HuzaiToast

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
    // 1.191: 收藏专区操作的轻提示（顶部胶囊，点击立即消失）
    // 当前选中大类（默认第 1 个；1.191 起有收藏专区时默认选中「收藏专区」）
    var selectedCateId by remember { mutableStateOf("") }
    // 1.191: 收藏专区——有收藏时在横滑条最前面插入「收藏专区」tab（默认选中）。
    // 必须声明在下方那个决定「默认选中项」的 LaunchedEffect 之前（Kotlin 局部变量先声明后使用）。
    val favTopics = remember(HupuPrefs.favoriteTopicsVersion) { HupuPrefs.loadFavoriteTopics() }
    val hasFav = favTopics.isNotEmpty()

    // ---------- 二级页状态（外置，关闭再进不重载） ----------
    var openedTopic by remember { mutableStateOf<HupuTopicInfo?>(null) }
    // 1.192: 本层「已计数」标记——连点多个条目时 enter 只发生一次，防 Tab 栏计数泄漏
    var topicEntered by remember { mutableStateOf(false) }
    var overlayClosing by remember { mutableStateOf(false) }
    val feedStates = remember { mutableStateMapOf<String, TopicFeedState>() }
    // 各专区的排序 tabs（服务器返回）；1.2xx：用跨会话缓存预热，避免排序条"晚一拍"出现
    val topicSorts = remember {
        mutableStateMapOf<String, List<SortTab>>().apply {
            putAll(HupuPrefs.loadTopicSortTabs())
        }
    }
    var loadingTopicKey by remember { mutableStateOf<String?>(null) }
    /**
     * 1.2xx：最近一次真正展示过的排序条 —— 新话题 tab 未知时先借用它。
     * 多数话题排序分类相同，借来的往往就是最终那一套；真实 tab 到位若相同则排序条不动。
     */
    var lastSortBar by remember { mutableStateOf<List<SortTab>>(emptyList()) }
    LaunchedEffect(openedTopic?.url, openedTopic?.url?.let { topicSorts[it] }) {
        val t = openedTopic?.url?.let { topicSorts[it] }
        if (!t.isNullOrEmpty()) lastSortBar = t
    }
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
            // 1.191: 有收藏专区 → 默认选中「收藏专区」；否则保持/回落到第一个大类
            val stillValid = (selectedCateId == FAV_CATE_ID && hasFav) ||
                list.any { it.cateId == selectedCateId }
            if (!stillValid) {
                selectedCateId = if (hasFav) FAV_CATE_ID else list.first().cateId
            }
        }
        loading = false
    }

    fun openTopic(t: HupuTopicInfo) {
        // 1.2xx：应用「专区默认排序」（按标题匹配已缓存的排序 tab；未配置/未命中 → 服务器默认）
        selectedSort = pickTopicSortKey(
            topicSorts[t.url] ?: emptyList(),
            HupuPrefs.loadDefaultTopicSortTitle(),
        )
        overlayClosing = false
        if (!topicEntered) { topicEntered = true; SecondaryPage.enter() }
        openedTopic = t
    }

    fun closeTopic() {
        // 先播返回动画（overlay 滑回右侧），动画结束后真正移除
        overlayClosing = true
    }

    // ---------- 帖子详情三级页（盖入式；从话题流内点帖进入） ----------
    var openedThread by remember { mutableStateOf<HupuThread?>(null) }
    // 1.192: 本层「已计数」标记——连点多个条目时 enter 只发生一次，防 Tab 栏计数泄漏
    var threadEntered by remember { mutableStateOf(false) }
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
        if (!threadEntered) { threadEntered = true; SecondaryPage.enter() }
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
    val cateIds = remember(categories, hasFav) {
        buildList {
            if (hasFav) add(FAV_CATE_ID)
            categories?.forEach { add(it.cateId) }
        }
    }
    fun swipeCate(delta: Int) {
        val cur = cateIds.indexOf(selectedCateId)
        if (cur < 0) return
        val ni = cur + delta
        if (ni !in cateIds.indices) return
        selectedCateId = cateIds[ni]
    }

    // 1.191: 收藏被全部移除时，若正停留在「收藏专区」→ 回落到第一个大类（该 tab 已消失）
    LaunchedEffect(hasFav, categories) {
        if (!hasFav && selectedCateId == FAV_CATE_ID) {
            categories?.firstOrNull()?.let { selectedCateId = it.cateId }
        }
    }

    Box(modifier.fillMaxSize()) {
        // ---------- 一级页 ----------
        // 1.198：顶栏浮空（可选效果，默认关；关闭时退回原形态，零额外开销）
        FrostedHeaderLayout(
            modifier = Modifier
                .fillMaxSize()
                .tabSwipeSwitch(
                    onPrevious = { swipeCate(-1) },
                    onNext = { swipeCate(1) },
                ),
            header = {
                PageHeader(title = "专区")
                // 大类横滑条同属顶栏：浮空时与标题一起压在内容之上
                if (categories != null && categories!!.isNotEmpty()) {
                    CateBar(
                        categories = categories!!,
                        selected = selectedCateId,
                        onSelect = { selectedCateId = it },
                        showFavorites = hasFav,
                        onSelectFavorites = { selectedCateId = FAV_CATE_ID },
                    )
                }
            },
        ) { topInset ->
            when {
                // 1.198：骨架也从浮空栏下开始（否则首行骨架块会被顶栏盖住）
                categories == null && loading -> SkeletonHome(Modifier.padding(top = topInset))
                categories == null -> ErrorRetry { cateRefreshTick++ }
                categories!!.isEmpty() -> ErrorRetry { cateRefreshTick++ }
                else -> {
                    // 1.191: 选中「收藏专区」时网格数据来自收藏列表，其余走大类版块
                    val favTab = selectedCateId == FAV_CATE_ID
                    val topics = if (favTab) favTopics else current?.topics.orEmpty()
                    if (topics.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                if (favTab) "还没有收藏专区" else "该分类暂无版块",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            // 1.199：上方留白交给横滑条自身的 bottom padding，这里不再叠加
                            // 1.198：浮空顶栏模式下 topInset = 实测栏高（含横滑条的 bottom padding）
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topInset, bottom = 140.dp),
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

        // 1.191: 收藏专区操作反馈（顶部胶囊，点击立即消失）
        // ---------- 二级页：版块话题流（盖入式转场） ----------
        if (opened != null) {
            // 1.192: 本层离开组合时兜底回收计数（正常退场已由 onExitStart 提前回收）
            androidx.compose.runtime.DisposableEffect(Unit) {
                onDispose { if (topicEntered) { topicEntered = false; SecondaryPage.exit() } }
            }
            TopicFeedOverlay(
                topic = opened,
                isFavorite = favTopics.any { it.url == opened.url },
                onToggleFavorite = {
                    val r = HupuPrefs.toggleFavoriteTopic(opened)
                    com.java.myapplication.ui.components.HuzaiToast.show(
                        when (r) {
                            HupuPrefs.FavoriteToggle.ADDED -> "已收藏「${opened.name}」"
                            HupuPrefs.FavoriteToggle.REMOVED -> "已取消收藏"
                            HupuPrefs.FavoriteToggle.LIMIT ->
                                "收藏专区已达上限（${HupuPrefs.MAX_FAVORITE_TOPICS}）"
                        }
                    )
                },
                closing = overlayClosing,
                feedStates = feedStates,
                topicSorts = topicSorts,
                fallbackSorts = lastSortBar,
                loadingTopicKey = loadingTopicKey,
                refreshVersion = refreshVersion,
                selectedSort = selectedSort,
                onSelectSort = { selectedSort = it },
                onBack = { closeTopic() },
                onExitStart = { if (topicEntered) { topicEntered = false; SecondaryPage.exit() } },
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
                            // 1.2xx：发现「有默认排序要用」时**不落地**这份默认排序的内容 ——
                            // 否则 Crossfade 会拿它当退场层，先闪一下「最新回复」列表再切成目标排序。
                            val want = if (selectedSort == null) {
                                pickTopicSortKey(p.sortTabs, HupuPrefs.loadDefaultTopicSortTitle())
                            } else {
                                null
                            }
                            if (want != null) {
                                topicSorts[url] = p.sortTabs
                                HupuPrefs.saveTopicSortTabs(url, p.sortTabs)
                                loadingTopicKey = null
                                selectedSort = want // 键变化 → 触发目标排序自己加载
                                return@launch
                            }
                            feedStates[key] = TopicFeedState(
                                threads = p.threads,
                                page = p.page,
                                totalPages = p.totalPages,
                            )
                            topicSorts[url] = p.sortTabs
                            HupuPrefs.saveTopicSortTabs(url, p.sortTabs)
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
            // 1.192: 本层离开组合时兜底回收计数（正常退场已由 onExitStart 提前回收）
            androidx.compose.runtime.DisposableEffect(Unit) {
                onDispose { if (threadEntered) { threadEntered = false; SecondaryPage.exit() } }
            }
            val d = threadDetails[ot.tid]
            ThreadDetailOverlay(
                tid = ot.tid,
                titleText = ot.title,
                detail = d,
                loading = threadLoading,
                loadingMore = threadLoadingMore,
                closing = threadClosing,
                onBack = { closeThread() },
                onExitStart = { if (threadEntered) { threadEntered = false; SecondaryPage.exit() } },
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
    /** 1.191: 该专区是否已收藏（右上角收藏按钮的选中态） */
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    closing: Boolean,
    feedStates: Map<String, TopicFeedState>,
    topicSorts: Map<String, List<SortTab>>,
    /** 1.2xx：本话题排序 tab 未知时借用的「上一个话题的排序条」（多数话题分类相同） */
    fallbackSorts: List<SortTab>,
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
    // 1.223q: 入场动画按**话题 url** 重播 —— 实例被复用（A 退场中点开 B）时不重播，
    // progress 会停在退场中途的接近 0 值，新话题被画成几乎不可见（表现为「点了没反应」）。
    LaunchedEffect(closing) {
    if (closing) {
            onExitStart()
            progress.animateTo(0f, tween(280))
            onClosed()
        } else {
        // 1.223r: 退场途中再次打开（含同一帖子）→ 重播入场，避免「看不见」
        progress.snapTo(0f)
        progress.animateTo(1f, tween(280))
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
        // 1.198：顶栏浮空（可选效果，默认关；关闭时退回原有观感）
        FrostedHeaderLayout(
            modifier = Modifier.fillMaxSize(),
            // ---------- 顶栏（浮空：标题行 + 排序条） ----------
            header = {
            // 顶栏：返回 + 版块名 + 热度
            CenteredTopBar(
                title = topic.name,
                onBack = { onBack() },
                // 副标题随热度有无变化；没有热度时传 null（组件会自动省略副标题）
                subtitle = if (topic.hotText.isNotBlank()) "${topic.hotText}热度" else null,
                actions = {
                    // 1.191: 收藏该专区
                    LiquidIconButton(
                        icon = HupuIcons.StarRate,
                        contentDescription = if (isFavorite) "取消收藏" else "收藏专区",
                        onClick = { onToggleFavorite() },
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
            // 排序子 Tab（服务器返回）
            // 1.2xx：真实 tab 未知时先借用上一个话题的排序条（多数话题分类相同 → 几乎零跳变）；
            // 借用期忽略点击（借来的 url 属于上一个话题，用了会请求错页面）
            val realTabs = sorts?.takeIf { it.isNotEmpty() }
            val barTabs = realTabs ?: fallbackSorts
            if (barTabs.isNotEmpty()) {
                val barSel = if (realTabs != null) {
                    selectedSort ?: topic.url
                } else {
                    pickTopicSortKey(barTabs, HupuPrefs.loadDefaultTopicSortTitle())
                        ?: barTabs.firstOrNull()?.url.orEmpty()
                }
                SortBar(barTabs, barSel) { if (realTabs != null) onSelectSort(it) }
            } else {
                // 无任何可借用的排序条 → 占位同高，避免整行消失导致列表上移再弹回
                Spacer(Modifier.height(SORT_BAR_TOTAL_HEIGHT))
            }
            },
        ) { topInset ->
        Column(Modifier.fillMaxSize()) {
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
                    // 1.198：指示器落在浮空顶栏下方（关闭该效果时为 0，位置不变）
                    indicator = {
                        PullToRefreshDefaults.Indicator(
                            state = pullState,
                            isRefreshing = loadingTopicKey == selKey,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = topInset),
                        )
                    },
                ) {
                    when {
                        selState == null -> SkeletonHome(Modifier.padding(top = topInset))
                        selState.threads.isEmpty() -> ErrorRetry { onRefresh() }
                        else -> FeedList(
                            threads = selState.threads,
                            useBigCards = true,
                            canLoadMore = selState.page < selState.totalPages,
                            loadingMore = selState.loadingMore,
                            onLoadMore = { onLoadMore(topic.url, sel, selState.page) },
                            resetKey = "$selKey-$refreshVersion",
                            topInset = topInset,
                            onOpenThread = onOpenThread,
                        )
                    }
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

/** 1.191: 「收藏专区」虚拟 tab 的 id（不是真实大类，仅本页内使用） */
private const val FAV_CATE_ID = "__fav_topics__"

/** 大类横滑条：单选，16dp 限宽裁剪（与首页一致） */
@Composable
private fun CateBar(
    categories: List<HupuCategory>,
    selected: String,
    onSelect: (String) -> Unit,
    /** 1.191: 有收藏专区时，最前面插一个「收藏专区」tab（图标 + 默认选中） */
    showFavorites: Boolean = false,
    onSelectFavorites: () -> Unit = {},
) {
    val barState = rememberLazyListState()
    val allIds = if (showFavorites) listOf(FAV_CATE_ID) + categories.map { it.cateId }
                 else categories.map { it.cateId }
    val selIdx = allIds.indexOf(selected)
    LaunchedEffect(selected, showFavorites) { if (selIdx >= 0) barState.animateChipCenterTo(selIdx) }
    LazyRow(
        state = barState,
        modifier = Modifier
            .fillMaxWidth()
            // 1.197：裁剪边界退到 8dp，给首个/末个 chip 的浮起阴影留空间（内容仍从 16dp 起）
            .chipBarClip()
            .padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 1.191: 「收藏专区」永远排在最前面（图标用与帖子「收藏」同源的星标）
        if (showFavorites) {
            item(key = FAV_CATE_ID) {
                Chip(
                    text = "收藏专区",
                    leadingIcon = HupuIcons.StarRate,
                    selected = selected == FAV_CATE_ID,
                    onClick = onSelectFavorites,
                )
            }
        }
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