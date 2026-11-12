package com.java.myapplication.ui.pages

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import com.java.myapplication.ui.components.animateChipCenterTo
import com.java.myapplication.ui.glass.FrostedHeaderLayout
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import com.java.myapplication.data.HupuAuthor
import com.java.myapplication.data.HupuBoardRotation
import com.java.myapplication.data.HupuFloorReplies
import com.java.myapplication.data.HupuReply
import com.java.myapplication.data.HupuThread
import com.java.myapplication.data.HupuThreadDetail
import com.java.myapplication.data.HupuHistoryEntry
import com.java.myapplication.data.HupuPrefs
import com.java.myapplication.data.HupuUiSignals
import com.java.myapplication.data.HupuRepository
import com.java.myapplication.data.HupuTopicInfo
import com.java.myapplication.data.pickTopicSortKey
import com.java.myapplication.data.SortTab
import com.java.myapplication.ui.components.*
import androidx.compose.foundation.layout.PaddingValues

/** 首页：全站热帖（默认）+ 热门话题流（单选切换、排序、无限滚动） */
@Composable
fun HomePage(modifier: Modifier = Modifier) {
    val repo = remember { HupuRepository() }
    // 1.332：热帖数据源换成**多板块轮转**。
    //
    // 演进过程（每一步都有真机反馈驱动）：
    //  · 最初 `/all-gambia`：几分钟才动一两条，下拉刷新等于没刷；
    //  · 中间 `walkingStreet` 推荐流：每次确实换一批，但**只有步行街主干道**——来源单一、
    //    水帖多、还混着 10-12/10-30 的旧帖（真机反馈「内容质量有点差」）；
    //  · 现在：虎扑每个 `bbs.hupu.com/all-*` 板块页都带 70 条完整热帖（封面/亮/回/专区齐全），
    //    每次刷新随机抽 3 个板块（含首页）并行抓取 → 交错合并去重；
    //    上滑翻页时再抽 1 个补上（比再抽 3 个快），直到 10 个板块全部抽完。
    // 于是「每次刷新必然不同」由**板块组合**保证，不再依赖服务端那 ~10s 的缓存窗口。
    var hotThreads by remember { mutableStateOf<List<HupuThread>>(emptyList()) }
    // 顶部频道横滑条的数据（官方「热门话题」）仍来自版块页；抽到首页时顺带拿到
    var hotTopics by remember { mutableStateOf<List<HupuTopicInfo>>(emptyList()) }
    // 1.358：上一次抽中的板块组合（刷新时用来避免"刷了等于没刷"）
    var lastPickedBoards by remember { mutableStateOf<List<String>>(emptyList()) }
    // 本轮已加载过的板块（上滑续页时优先取没出现过的；全部用完再回到整池）
    val usedBoards = remember { mutableSetOf<String>() }
    // 是否还能继续上滑加载（板块池抽空后会自动重新洗牌，见 HupuBoardRotation.draw）
    var hotHasMore by remember { mutableStateOf(true) }
    var hotLoadingMore by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // 1.328：刷新成功信号 —— 只用于驱动列表回顶。
    // 为什么需要它：FeedList 的回顶挂在 `resetKey` 变化上，而热帖的 resetKey 原来是
    // "hot-$refreshVersion-$hotResetTick"，其中 refreshVersion 只被**话题流**的 forceReload
    // 自增过，热帖下拉刷新走的是 load(refresh=true)，**从不动 refreshVersion** →
    // resetKey 不变 → 回顶 effect 不重启 → 刷新后停在原位（真机反馈的"有时不回顶"）。
    var hotRefreshTick by remember { mutableIntStateOf(0) }

    suspend fun load(refresh: Boolean = false) {
        if (refresh) refreshing = true else loading = true
        failed = false
        // 1.332：随机抽 3 个板块（含首页）并行抓取。
        // 失败重试：整体为空时退避重试 2 次（沿用旧的容错策略，避免冷启动一次失败就永久失败态）。
        var picked = HupuBoardRotation.draw(lastPickedBoards, HupuBoardRotation.HEAD_SIZE)
        lastPickedBoards = picked
        usedBoards.clear()
        usedBoards.addAll(picked)
        var threads = repo.boardPages(picked)
        var tries = 0
        while (threads.isEmpty() && tries < 2) {
            tries++
            kotlinx.coroutines.delay(600L * tries)
            picked = HupuBoardRotation.draw(lastPickedBoards, HupuBoardRotation.HEAD_SIZE)
            lastPickedBoards = picked
            threads = repo.boardPages(picked)
        }
        if (threads.isNotEmpty()) {
            hotThreads = threads
            hotHasMore = true
            if (refresh) hotRefreshTick++
            loading = false
            refreshing = false
            return
        }
        // 兜底：全部板块都抓失败（断网等）→ 回退到单页热帖，保证不空屏
        val f = repo.board(refresh = refresh)
        if (f != null) {
            hotThreads = f.threads
            hotTopics = f.hotTopics
            hotHasMore = false
        }
        failed = f == null
        loading = false
        refreshing = false
    }
    LaunchedEffect(Unit) { load() }

    // 1.332：频道横滑条的数据（官方「热门话题」）只在首页板块被抽到时顺带取一次。
    // 以前是「冷启动单独补一页 260KB 的版块页」——现在抽到 `/` 时它自带 hotTopics，
    // 抽不到就先用空条（下次刷新抽到再补），省一次固定开销。
    LaunchedEffect(hotThreads) {
        if (hotTopics.isNotEmpty()) return@LaunchedEffect
        if (hotThreads.isEmpty()) return@LaunchedEffect
        val meta = repo.board(refresh = false)
        if (meta != null) hotTopics = meta.hotTopics
    }

    /**
     * 1.332：热帖上滑加载 —— 再随机抽 **1** 个板块续上（比再抽 3 个快，用户要的是「还有新东西」）。
     *
     * 板块池抽完（`hotRemaining` 空）时 [HupuBoardRotation.draw] 会自动重新洗牌，
     * 所以理论上可以一直滑（内容会环回，但 10 个板块 × 70 条本身也够翻很久）。
     * 去重后若一条新增都没有（极端情况：整批都和前文重复），主动停掉加载，避免死循环。
     */
    fun loadMoreHot() {
        if (hotLoadingMore || !hotHasMore) return
        hotLoadingMore = true
        scope.launch {
            val picked = HupuBoardRotation.drawUnused(usedBoards, HupuBoardRotation.MORE_SIZE)
            val more = repo.boardPages(picked)
            val before = hotThreads.size
            val merged = (hotThreads + more).distinctBy { it.tid }
            hotThreads = merged
            // 一条都没新增 → 认为没有更多了（防止滑到底反复请求同一批）
            if (merged.size == before) hotHasMore = false
            hotLoadingMore = false
        }
    }

    // Tab 重选刷新信号：已选中时再点首页 Tab → 强刷热帖流（列表在别处也能立即刷新）
    var tabResetTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(HupuUiSignals.homeTabTap) {
        if (HupuUiSignals.homeTabTap == 0) return@LaunchedEffect
        tabResetTick++
        load(refresh = true)
    }

    // 1.365：详情宿主提到本层 —— 详情 overlay 要与「首页内容（含浮空顶栏）」同父，
    // zIndex(2f) 才比较得到顶栏那一层（之前它在 FeedContent 内，顶栏压不住）。
    val detailHost = rememberThreadDetailHost(repo)

    // 1.365：**外层再包一层 Box**，把「详情页」放在与「首页内容（含浮空顶栏）」**同父**的位置。
    //
    // 之前详情页渲染在 FeedContent 内部（FrostedHeaderLayout 的内容区旁边），
    // 与顶栏不是同一个父布局 → `zIndex(2f)` 比较不到顶栏那一层，
    // 于是详情打开后首页顶栏仍然压在它上面（真机反馈「点进帖子详情，首页顶栏依旧显示」）。
    // 现在两者是同父兄弟：详情 zIndex 2f > 内容层 0 → 一定盖住顶栏。
    Box(modifier.fillMaxSize()) {
        when {
            hotThreads.isNotEmpty() -> FeedContent(
                hotList = hotThreads,
                hotTopics = hotTopics,
                hotHasMore = hotHasMore,
                hotLoadingMore = hotLoadingMore,
                onLoadMoreHot = { loadMoreHot() },
                isRefreshing = refreshing,
                onRefresh = { scope.launch { load(refresh = true) } },
                hotResetTick = tabResetTick,
                hotRefreshTick = hotRefreshTick,
                detailHost = detailHost,
                modifier = Modifier.fillMaxSize(),
            )
            loading -> SkeletonHome(Modifier.fillMaxSize())
            else -> ErrorRetry(Modifier.fillMaxSize()) { scope.launch { load(refresh = true) } }
        }
        // 帖子详情二级页：与上面内容同父，靠 zIndex 盖住顶栏
        RenderThreadDetailOverlay(state = detailHost)
    }
}

// ---------- 内容 ----------

@Composable
private fun FeedContent(
    hotList: List<HupuThread>,
    hotTopics: List<HupuTopicInfo>,
    hotHasMore: Boolean,
    hotLoadingMore: Boolean,
    onLoadMoreHot: () -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    hotResetTick: Int = 0,
    hotRefreshTick: Int = 0,
    /** 1.365：详情宿主由 HomePage 持有并传进来（详情 overlay 渲染在 HomePage 层，才压得住顶栏） */
    detailHost: ThreadDetailHostState,
    modifier: Modifier = Modifier,
) {
    val repo = remember { HupuRepository() }
    // 单选流切换："hot"=全站热帖（默认）| 其他=话题 url（如 /topic-daily）
    // 1.224c：初始值**同步读盘**（热帖隐藏时直接停在自定义频道首页）——否则冷启动会先渲染
    // 一帧热帖再被下面的校正 effect 切走，即真机反馈的「热帖页面一闪而过」。
    var selected by remember { mutableStateOf(HupuPrefs.initialHomeTopicSelection()) }
    // 话题内的排序 tab url（null=默认最新回复）
    var selectedSort by remember { mutableStateOf<String?>(null) }
    // 各话题流的累积状态：key = "话题url#排序url"
    val feedStates = remember { mutableStateMapOf<String, TopicFeedState>() }
    // 各话题的排序 tabs（服务器返回）；1.2xx：用跨会话缓存预热 —— 否则首次进入话题时
    // 排序条要等列表接口回来才出现，整行消失会让下方卡片上移一下再弹回
    val topicSorts = remember {
        mutableStateMapOf<String, List<SortTab>>().apply {
            putAll(HupuPrefs.loadTopicSortTabs())
        }
    }
    // 正在首屏加载的话题 url（null=无）
    var loadingTopic by remember { mutableStateOf<String?>(null) }
    // 强制刷新标记 + 重载触发器
    var forceRefresh by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    // ---------- 帖子详情 ----------
    // 1.365：detailHost 由 HomePage 传入（overlay 渲染在 HomePage 层）

    // ---------- 搜索二级页 ----------

    fun openThread(t: HupuThread) = detailHost.open(t)

    fun closeThread() = detailHost.requestClose()

    // 刷新计数：每次成功刷新后 +1，驱动列表回顶
    var refreshVersion by remember { mutableIntStateOf(0) }

    // 主页频道：用户自定义优先，否则官方热门（「我的」页保存后版本号变化自动刷新）
    val effectiveTopics = remember(hotTopics, HupuPrefs.homeTopicsVersion) {
        HupuPrefs.effectiveHomeTopics(hotTopics)
    }
    // 1.192: 「热帖」隐藏开关——隐藏后横滑条不再显示它
    // （设置页已保证「热帖 + 已选话题」≥1；这里再兜底：话题为空时仍显示热帖，页面不会 0 tab）
    val hotHidden = remember(HupuPrefs.homeTopicsVersion) { HupuPrefs.isHomeHotHidden() }
    val showHot = !hotHidden || effectiveTopics.isEmpty()
    // 正在浏览的频道失效（被移除 / 热帖被隐藏且正停在它上面）时，回退到合法频道
    LaunchedEffect(effectiveTopics, showHot) {
        val valid = (selected == "hot" && showHot) || effectiveTopics.any { it.url == selected }
        if (!valid) {
            selected = if (showHot) "hot" else effectiveTopics.firstOrNull()?.url ?: "hot"
        }
    }

    val currentSort = if (selected == "hot") null else selectedSort
    val feedKey = if (selected == "hot") "hot" else "$selected#${currentSort ?: selected}"

    /**
     * 1.2xx：记录每个话题「离开时」生效的 feed key，供 Crossfade 的**退场层**沿用。
     * 否则退场层会掉回「默认排序」的 key（`url#url`），于是切话题时会先闪一下
     * 当前话题的「最新回复」列表再切走（真机反馈）。
     */
    val lastKeyOf = remember { mutableStateMapOf<String, String>() }

    /**
     * 1.2xx：最近一次**真正展示过**的排序条 —— 下一个话题在排序 tab 未知时先「借用」它。
     * 大部分话题的排序分类是相同的（最新回复 / 最新发布 / 24小时榜），借来的往往就是最终
     * 要显示的那一套；等真实 tab 到位、若与之相同则**排序条完全不动**（零跳变）。
     */
    var lastSortBar by remember { mutableStateOf<List<SortTab>>(emptyList()) }
    LaunchedEffect(selected, topicSorts[selected]) {
        val t = topicSorts[selected]
        if (!t.isNullOrEmpty()) lastSortBar = t
    }
    /** 切换话题（首页横滑条 / 左右滑动共用）：记下离开时的 key + 应用默认排序 */
    fun selectTopic(target: String) {
        if (target == selected) return
        if (selected != "hot") lastKeyOf[selected] = feedKey
        if (target == "hot") {
            selected = "hot"
        } else {
            selected = target
            selectedSort = pickTopicSortKey(
                topicSorts[target] ?: emptyList(),
                HupuPrefs.loadDefaultTopicSortTitle(),
            )
        }
    }

    // ---------- 1.222 热帖作者补齐（黑名单在热帖流生效的前提） ----------
    // 为什么需要：首页热帖数据源（/all-gambia 的 pageData.threads）**整页不含 author**
    //（实测 0 个 puid/puname），FeedList 的按人过滤自然无从命中；话题流/专区流的列表自带
    // author，所以那边一直是正常的。
    // 做法：仅在黑名单非空时，按 tid 从 `/api/v2/threads?tid=` 补取作者（4 条一批 + 批间小睡），
    // 结果按 tid 长期缓存（作者几乎不变）——所以只有第一次会补，之后秒开；没有黑名单则零请求。
    var hotAuthors by remember { mutableStateOf<Map<String, HupuAuthor>>(emptyMap()) }
    LaunchedEffect(hotList, HupuPrefs.blacklistVersion) {
        if (HupuPrefs.loadBlacklist().isEmpty()) {
            hotAuthors = emptyMap()
            return@LaunchedEffect
        }
        val cache = HupuPrefs.loadThreadAuthors()
        val seeded = LinkedHashMap<String, HupuAuthor>()
        hotList.forEach { t ->
            cache[t.tid]?.let { v ->
                val i = v.indexOf('|')
                val p = if (i >= 0) v.substring(0, i) else v
                val e = if (i >= 0) v.substring(i + 1) else ""
                if (p.isNotEmpty() || e.isNotEmpty()) seeded[t.tid] = HupuAuthor(puid = p, name = "", euid = e)
            }
        }
        hotAuthors = seeded
        // 1.32x：新数据源每条都自带 author（puid + userName），只有真缺作者时才补请求
        val missing = hotList
            .filter { it.author?.puid.isNullOrEmpty() && it.author?.euid.isNullOrEmpty() }
            .map { it.tid }
            .filter { it.isNotEmpty() && !cache.containsKey(it) }
        if (missing.isEmpty()) return@LaunchedEffect
        val fresh = LinkedHashMap<String, String>()
        missing.chunked(4).forEach { chunk ->
            val got = coroutineScope {
                chunk.map { tid -> async { tid to repo.threadAuthor(tid) } }.awaitAll()
            }
            got.forEach { (tid, a) ->
                if (a != null && (a.puid.isNotEmpty() || a.euid.isNotEmpty())) {
                    fresh[tid] = "${a.puid}|${a.euid}"
                    hotAuthors = hotAuthors + (tid to a)
                }
            }
            delay(120)
        }
        if (fresh.isNotEmpty()) HupuPrefs.putThreadAuthors(fresh)
    }
    // 把补齐的作者并回列表（未补到的保持原样 = 不过滤，宁可漏杀不可错杀）
    val hotFiltered = remember(hotList, hotAuthors) {
        hotList.map { t ->
            // 1.32x：新数据源自带 author，优先用列表里的
            val own = t.author?.takeIf { it.puid.isNotEmpty() || it.euid.isNotEmpty() }
            if (own != null) t
            else hotAuthors[t.tid]?.let { a -> t.copy(author = a) } ?: t
        }
    }

    // 1.310：**跨「热帖 ↔ 话题」切换时，先给一小段骨架遮罩**。
    //
    // 这两态的顶栏高度不同（热帖没有二级排序条），内容区 topInset 会跟着变。
    // 首页数据都在内存里，同帧就能画出来 → 顶栏变化会立刻显形（"跳一下/闪"）。
    // 参照 NBA 数据页的做法：切换瞬间让内容**先退场**，等布局稳定后再一次性画进去，
    // 位移就发生在"没人看得见的骨架期"。只对跨这两种形态的切换生效，
    // 话题 ↔ 话题（高度相同）仍然同帧直切，不牺牲利索。
    // 1.317：**遮罩必须在组合期就生效**。
    // 之前是在 LaunchedEffect 里置位（effect 在首帧之后才跑），于是切到热帖时会先画出一帧
    // 真实内容（热帖带图，非常显眼）→ 下一帧才变骨架 → 再变内容，看起来"闪两下"。
    // 现在：跨形态切换时，本帧的初始状态就是"骨架"，effect 只负责 180ms 后把它关掉。
    val isHotNow = selected == "hot"
    var lastShownHot by remember { mutableStateOf(isHotNow) }
    val crossFormNow = lastShownHot != isHotNow
    val switchMaskingState = remember(selected) { mutableStateOf(crossFormNow) }
    LaunchedEffect(selected) {
        if (switchMaskingState.value) {
            delay(180)
            switchMaskingState.value = false
        }
        lastShownHot = isHotNow
    }

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
            // 1.2xx：这次响应让我们发现「有默认排序要用」时，**不落地**这份默认排序的内容 ——
            // 否则 Crossfade 会拿它当退场层，先闪一下「最新回复」列表再切成目标排序。
            // 只记下排序 tab 并切过去，让目标排序自己去加载（用户全程只看到骨架）。
            val want = if (selectedSort == null) {
                pickTopicSortKey(p.sortTabs, HupuPrefs.loadDefaultTopicSortTitle())
            } else {
                null
            }
            if (want != null) {
                topicSorts[selected] = p.sortTabs
                HupuPrefs.saveTopicSortTabs(selected, p.sortTabs)
                if (loadingTopic == selected) loadingTopic = null
                selectedSort = want // 键变化会重新触发本 effect → 加载目标排序
                return@LaunchedEffect
            }
            feedStates[feedKey] = TopicFeedState(
                threads = p.threads,
                page = p.page,
                totalPages = p.totalPages,
            )
            topicSorts[selected] = p.sortTabs
            HupuPrefs.saveTopicSortTabs(selected, p.sortTabs)
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
    // 1.192: 热帖被隐藏时，左右滑动顺序里也不含它（与横滑条一致）
    val tabUrls = remember(effectiveTopics, showHot) {
        (if (showHot) listOf("hot") else emptyList()) + effectiveTopics.map { it.url }
    }
    fun swipeTab(delta: Int) {
        val cur = tabUrls.indexOf(selected).let { if (it >= 0) it else 0 }
        val ni = cur + delta
        if (ni !in tabUrls.indices) return
        val target = tabUrls[ni]
        if (target == selected) return
        selectTopic(target)
    }

    Box(modifier.fillMaxSize()) {
    // 1.198：顶栏改为**浮空**（栏背景 = 官方 progressive blur），列表用「实测栏高」作顶部内边距
    // —— 条目滚到栏下不再被硬切一刀，而是越靠上越模糊、并渐隐入页面底色。
    val pullState = rememberPullToRefreshState()
    // 已有内容的流刷新时走顶部指示器（内容保持不动）；仅首次加载（无缓存）才显示骨架
    val inPlaceRefreshing = loadingTopic != null && feedStates[feedKey] != null
    FrostedHeaderLayout(
        modifier = Modifier
            .fillMaxSize()
            .tabSwipeSwitch(
                onPrevious = { swipeTab(-1) },
                onNext = { swipeTab(1) },
            ),
        // ---------- 顶栏（浮空；背景由骨架统一负责） ----------
        header = {
        PageHeader(title = "虎扑") {
            // 1.223：搜索键升级为与返回键同源的液态玻璃按钮（同款落影 + 按压手感）
            // 1.223b：图标放大到 24dp——20dp 时放大镜的「镜圈」偏小，看着像羽毛球拍。
            // 1.336：搜索被用户挪到底栏时，顶栏不再显示这个按钮（避免两处入口重复）。
            if (HupuPrefs.searchPositionVersion >= 0 &&
                HupuPrefs.loadSearchPosition() == 0
            ) {
                LiquidIconButton(
                    icon = HupuIcons.SearchOutlined,
                    contentDescription = "搜索",
                    // 1.341：统一走全局搜索层（不再在首页内自建一套），
                    // 这样顶栏/底栏两个入口打开的是同一个页面、行为完全一致。
                    onClick = { HupuUiSignals.requestSearch() },
                    iconSize = 24.dp,
                )
            }
        }
        // 话题横滑条：固定在标题栏下方，单选切换内容流（再点已选返回热帖）
        TopicBar(effectiveTopics, selected, showHot) {
            if (selected == it) {
                // 1.192: 再点已选 = 回到「热帖」；但热帖被隐藏时该动作无意义 → 保持不动
                if (showHot) selectTopic("hot")
            } else {
                selectTopic(it)
            }
        }
        // Tab 重选刷新信号（话题流侧）：已选中时再点首页 Tab → 强刷当前话题流并回顶
        LaunchedEffect(HupuUiSignals.homeTabTap) {
            if (HupuUiSignals.homeTabTap == 0) return@LaunchedEffect
            forceReload()
        }
        // 1.311：**热帖不显示二级排序条**（产品直觉：热帖就是热帖，没有可切的分法）。
        // 顶栏高度在「热帖 / 话题流」之间因此不同 —— 跨形态切换交给"骨架遮罩"：
        // 内容在 180ms 骨架期退场，等布局稳定后再一次性画进去，位移不可见。
        if (selected != "hot") {
        val barRealTabs = topicSorts[selected]?.takeIf { it.isNotEmpty() }
        val barTabs = barRealTabs ?: lastSortBar
        if (barTabs.isNotEmpty()) {
            val barSel = if (barRealTabs != null) {
                currentSort ?: selected
            } else {
                pickTopicSortKey(barTabs, HupuPrefs.loadDefaultTopicSortTitle())
                    ?: barTabs.firstOrNull()?.url.orEmpty()
            }
            SortBar(barTabs, barSel) { url ->
                // 借用期忽略点击：借用 tab 的 url 属于上一个话题，用了会请求错页面
                if (barRealTabs != null) selectedSort = url
            }
        } else {
            // 话题流的分类还没回来：留出等高占位，保证顶栏高度不抖
            Spacer(Modifier.height(SORT_BAR_TOTAL_HEIGHT))
        }
        }
        },
        // ---------- 内容（列表）：顶部内边距 = 实测栏高 ----------
    ) { topInset ->
        // 排序条已回到顶栏（热帖用等高占位），所以这里 **bar 高度已包含在 topInset 里**，
        // 两态列表顶边天然一致 —— 不需要再自己让位、也不再需要内容层覆盖条。
        Box(Modifier.fillMaxSize()) {
        // 已有内容的流刷新时走顶部指示器（内容保持不动）；仅首次加载（无缓存）才显示骨架
        PullToRefreshBox(
            isRefreshing = isRefreshing || inPlaceRefreshing,
            onRefresh = { if (selected == "hot") onRefresh() else forceReload() },
            state = pullState,
            modifier = Modifier.fillMaxSize(),
            // 1.198：指示器必须落在浮空顶栏**下方**，否则会被栏体挡住看不见
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullState,
                    isRefreshing = isRefreshing || inPlaceRefreshing,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = topInset),
                )
            },
        ) {
            // 1.301：**同帧换画面**（对齐 NBA 数据页的手感，不做交叉淡入）
            val sel = selected
            val selKey = if (sel == "hot") "hot" else "$sel#${selectedSort ?: sel}"
            when {
                // 1.310：跨「热帖 ↔ 话题」的切换：先只画骨架（位移藏在这一帧里）
                switchMaskingState.value -> SkeletonFeedCards(topPadding = topInset + FEED_TOP_GAP)
                // 默认：全站热帖（70 条一次性，无分页）
                sel == "hot" -> FeedList(
                    threads = hotFiltered,
                    useBigCards = true,
                    canLoadMore = hotHasMore,
                    loadingMore = hotLoadingMore,
                    onLoadMore = { onLoadMoreHot() },
                    resetKey = "hot-$refreshVersion-$hotResetTick-$hotRefreshTick",
                    topInset = topInset + FEED_TOP_GAP,
                    onOpenThread = { openThread(it) },
                )
                else -> {
                    val state = feedStates[selKey]
                    when {
                        state == null -> SkeletonFeedCards(topPadding = topInset + FEED_TOP_GAP)
                        state.threads.isEmpty() -> ErrorRetry { forceReload() }
                        else -> FeedList(
                            threads = state.threads,
                            useBigCards = true,
                            canLoadMore = state.page < state.totalPages,
                            loadingMore = state.loadingMore,
                            onLoadMore = { loadMore() },
                            resetKey = "$selKey-$refreshVersion",
                            topInset = topInset + FEED_TOP_GAP,
                            onOpenThread = { openThread(it) },
                        )
                    }
                }
            }
        }
    } // FrostedHeaderLayout 内容块关闭（overlay 挂 Box 内、骨架外）

    } // Box
}
}

/**
 * 1.306：**信息流首条与二级 tab 之间的呼吸间距**。
 *
 * 真机反馈：二级 tab 到一级 tab 的距离 > 首条信息到二级 tab 的距离，看着头重脚轻。
 * 这里给列表顶部多加一段内边距（话题流与热帖一致），把下半部分撑开。
 */
// 一级 tab 底部呼吸是 10dp（TopicBar 的 padding(bottom = 10.dp)），
// 二级 tab 自身底部呼吸是 6dp（SortBar 的 bottomPad）——
// 所以列表再补 4dp，两段间距刚好都是 10dp，视觉上不再头重脚轻。
private val FEED_TOP_GAP = 3.dp


/** 话题横滑条（固定）：单选切换，选中 chip 主题色高亮 */
@Composable
private fun TopicBar(
    topics: List<HupuTopicInfo>,
    selected: String,
    showHot: Boolean = true,
    onSelect: (String) -> Unit,
) {
    val barState = rememberLazyListState()
    // 1.192: 热帖被隐藏时，话题 chip 的下标整体前移 1
    val selIdx = if (selected == "hot") 0
    else (if (showHot) 1 else 0) + topics.indexOfFirst { it.url == selected }
    LaunchedEffect(selected) { if (selIdx >= 0) barState.animateChipCenterTo(selIdx) }
    LazyRow(
        state = barState,
        modifier = Modifier
            .fillMaxWidth()
            // 可视窗口与下方条目对齐（左右 16dp），越线滚动内容被裁剪覆盖
            // 1.197：裁剪边界退到 8dp，给首个/末个 chip 的浮起阴影留空间（内容仍从 16dp 起）
            .chipBarClip()
            .padding(bottom = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 「热帖」chip：默认流（/all-gambia 全站热帖榜）——1.192 起可被设置页隐藏
        if (showHot) {
            item(key = "hot-chip") {
                Chip(text = "热帖", selected = selected == "hot") { onSelect("hot") }
            }
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