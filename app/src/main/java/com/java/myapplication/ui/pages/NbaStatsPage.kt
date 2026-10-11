package com.java.myapplication.ui.pages

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.java.myapplication.data.HupuNbaStatsApi
import com.java.myapplication.data.NbaBoxScore
import com.java.myapplication.data.NbaPlay
import com.java.myapplication.data.NbaPlayByPlay
import com.java.myapplication.data.NbaPlayerRow
import com.java.myapplication.data.NbaTableSection
import com.java.myapplication.data.NbaTeamStats
import com.java.myapplication.data.SortTab
import com.java.myapplication.data.parseNbaBoxScore
import com.java.myapplication.data.parseNbaPlayByPlay
import com.java.myapplication.ui.components.ErrorRetry
import com.java.myapplication.ui.components.SkeletonHome
import com.java.myapplication.ui.components.SortBar
import kotlinx.coroutines.delay

/**
 * 1.200/1.201：NBA「数据统计 / 文字实录」。
 *
 * 数据链路（详见 [com.java.myapplication.data.HupuNbaStats] 的注释）：
 *   评分侧（basketball_match/outBizNo）
 *     → 按 date 抓 nba.hupu.com/games/{date}，用「两队名」join 出 gameId
 *     → 抓 boxscore / playbyplay 老版 SSR 页 → 本地解析 → 纯 Compose 渲染（无 WebView）。
 *
 * 1.201 结构调整：本页**不再是盖入式三级页**，而是比赛详情页里的一个「同级 tab 正文」
 * （见 [NbaStatsBody]）；因此这里只负责「抓 + 解析 + 渲染」，顶栏 / 返回 / 转场 / tab 条
 * 全部由 [MatchDetailOverlay] 持有。加载状态放在 [NbaStatsState]（页面级 remember），
 * 切 tab 不销毁 → 不重拉、不闪骨架。
 */

internal enum class NbaStatsTab(val label: String) {
    BOX("数据统计"),
    PLAY("文字实录"),
}

/**
 * 1.201：进行中比赛的轮询间隔。
 *
 * 起初按「实录约 10s 一条动作」定了 10s，但真机对照发现**原网页自己的刷新间隔比 10s 久**
 * （多一条动作的时间常常更松），10s 又略勤于源站。折中取 15s：比原来的 30s 实时一倍，
 * 又不会明显勤于虎扑自己的更新节奏。
 *
 * 代价可控：轮询只在**比赛进行中且用户停在本 tab** 时进行（离开页面即随 effect 取消），
 * 即每分钟 4 次；boxscore 页约 70KB、playbyplay 约 140KB（服务端 gzip 后远小于此）。
 * 想更省流量就把它改成 20_000 —— 只影响数据页，评分侧仍走 30s。
 */
private const val NbaLivePollMs = 15_000L

/**
 * 1.224e：单次「柔化滑动」最多允许跨多少条。
 *
 * 超过这个量级说明不是「来了几条新事件」，而是「离开很久 / 一节打完回来一次补了很多」——
 * 那种距离下滑动会横跨好几屏，既拖帧又看不出内容，不如直接瞬移到底。
 * 一屏大约 10 条上下，这里取约两三屏。
 */
private const val NbaMaxAnimatedItems = 24

/** 评分页据赛程卡构造的 NBA 上下文（唯一能把两套 id 接起来的东西） */
data class NbaStatsQuery(
    /** 北京时间日期 `yyyy-MM-dd` */
    val date: String,
    val home: String,
    val away: String,
    val homeScore: String,
    val awayScore: String,
    /** 进行中 → 30s 轮询 + 强制走网络 */
    val live: Boolean,
)

/**
 * 1.201：数据/实录的「比赛身份指纹」——**只含稳定身份**（日期 + 两队名）。
 *
 * 刻意**不含比分与 live**：这两个字段每 30s 会随直播轮询变化，而 [NbaStatsQuery] 是
 * 每次重组新建的 data class。若拿整个对象当 key/判等依据，**每次比分变动都会被判成
 * 「换了一场」**→ 状态被清空、重新 resolve gameId → 整页闪一下骨架屏
 * （这正是「数据更新时整页闪烁」的根因）。
 */
internal fun nbaStatsKey(q: NbaStatsQuery?): String? =
    q?.let { "${it.date}|${it.home}|${it.away}" }

/**
 * 1.201：NBA 数据/实录的加载状态。
 *
 * 由比赛详情页 `remember(query)` 持有（**不随 tab 切换销毁**），这样：
 *  · 「评分 → 数据统计 → 评分 → 数据统计」来回切**不会重复解析、不会闪骨架**；
 *  · 换一场比赛时只有 query 变化才整体重置（[resolvedKey] 指纹）。
 */
internal class NbaStatsState {
    val gameId = mutableStateOf<String?>(null)
    val resolving = mutableStateOf(true)
    val box = mutableStateOf<NbaBoxScore?>(null)
    val pbp = mutableStateOf<NbaPlayByPlay?>(null)
    /** 失败标记**按 tab 分开**：否则数据统计失败会把文字实录也画成错误态 */
    val boxFailed = mutableStateOf(false)
    val pbpFailed = mutableStateOf(false)
    val loading = mutableStateOf(false)
    val refreshKey = mutableIntStateOf(0)

    /**
     * 1.201：本次刷新是否「用户主动下拉」。
     * 自动轮询（30s 静默刷新）**不该弹下拉指示器**，否则每次轮询都会闪一下刷新圈。
     */
    val userPull = mutableStateOf(false)

    /** 每个 tab 已服务过的 refreshKey（避免来回切 tab 重复走网络） */
    var boxServed: Int = -1
    var pbpServed: Int = -1

    /** 已解析过的**身份指纹**（换场才重置；纯内存比较，不触发重组） */
    var resolvedKey: String? = null

    /**
     * 1.201：文字实录的列表状态**提到页面级**持有。
     *
     * 之前它建在内容 composable 里，而正文是被 Crossfade 包着的——切到「数据统计」再切回来时
     * 内容会被重建，滚动位置就丢了：直播中切回来会先停在顶部（一批旧事件），
     * 要等下一次刷新才被跟随带回底部。放到这里后切 tab 原地保留位置、也不丢跟随状态。
     */
    val playList = LazyListState()

    /**
     * 1.224e：文字实录是否**已经定位过一次**（首次呈现 / 换场后才需要重新定位）。
     *
     * 直播场次的内容动辄几百条，若首次进入时播放「从顶部一路滑到底」，
     * 几秒内要组合/布局全部条目 → 真机上就是明显掉帧（「切过来下滑很卡」）。
     * 而这个场景用户并不需要"看它滚"，直接呈现最新即可 —— 所以首次**瞬移**到底，
     * 之后到达的新事件才走柔化滑动（那时增量小，滑动才顺）。
     */
    var playPositioned = false

    // ---------- 1.224j：球员 PK 换人弹层（状态上提到页面级） ----------
    // 此前 pkAway/pkHome/pickSide 都建在「数据统计正文」内部，于是弹层只能挂在 tab 内容区里
    // —— 打开时遮罩只压暗 tab 内容，**顶栏与记分板依旧亮着**，看着很怪。
    // 上提到页面级后，弹层改由 [MatchDetailOverlay] 的根节点挂载，遮罩即可盖住整屏。
    /** 左侧（客队）选中球员；空串 = 尚未落到「得分王」默认值 */
    var pkAway by mutableStateOf("")
    /** 右侧（主队）选中球员 */
    var pkHome by mutableStateOf("")
    /** 换人弹层目标：0 = 左（客队），1 = 右（主队），-1 = 关闭 */
    var pickSide by mutableIntStateOf(-1)
    /** 为让退场动画播完（260ms）保留最后一次参数——否则 pickSide 归 -1 的瞬间整块被卸载 */
    var lastPickSheet by mutableStateOf<Triple<NbaTeamStats, String, String>?>(null)
}

/**
 * 1.201：数据统计 / 文字实录的**正文**（嵌入比赛详情页，与「评分」同级）。
 *
 * 本身不含顶栏、返回、转场与背景——这些由父级 [MatchDetailOverlay] 提供。
 */
@Composable
internal fun NbaStatsBody(
    state: NbaStatsState,
    query: NbaStatsQuery,
    tab: NbaStatsTab,
) {
    // ---------- ① 定位 gameId（评分侧 → 日期页 join）——同一场只解析一次 ----------
    // key 用**身份指纹**（日期 + 两队名），不含比分/live：直播轮询改的是比分，
    // 不该把「同一场比赛的数据更新」误判成「换了一场」而清空状态闪骨架。
    val statsKey = nbaStatsKey(query)
    LaunchedEffect(statsKey) {
        if (state.resolvedKey == statsKey) return@LaunchedEffect
        state.resolvedKey = statsKey
        state.resolving.value = true
        state.gameId.value = null
        state.box.value = null
        state.pbp.value = null
        state.boxFailed.value = false
        state.pbpFailed.value = false
        state.boxServed = -1
        state.pbpServed = -1
        // 1.224e：换场后必须重新定位 —— 否则新比赛会停在列表顶部（旧的 playPositioned 会拦住首次瞬移）
        state.playPositioned = false
        state.gameId.value = HupuNbaStatsApi.resolveGameId(
            date = query.date,
            home = query.home,
            away = query.away,
            homeScore = query.homeScore,
            awayScore = query.awayScore,
        )
        state.resolving.value = false
    }

    // ---------- ② 进行中：静默重拉（本 tab 可见时才轮询） ----------
    // key 只取 live 与 gameId：若拿整个 query 当 key，比分一变 effect 就重启，
    // 倒计时会被反复重置，直播反而永远等不到下一次刷新。
    LaunchedEffect(query.live, state.gameId.value) {
        if (!query.live || state.gameId.value == null) return@LaunchedEffect
        while (true) {
            delay(NbaLivePollMs)
            state.refreshKey.intValue++
        }
    }

    // ---------- ③ 按 tab 懒加载（换场清空；已服务过同一个 refreshKey 则跳过） ----------
    val refreshKey = state.refreshKey.intValue
    LaunchedEffect(state.gameId.value, tab, refreshKey) {
        val id = state.gameId.value ?: return@LaunchedEffect
        val force = query.live || refreshKey > 0
        if (tab == NbaStatsTab.BOX) {
            if (state.box.value != null && state.boxServed == refreshKey) return@LaunchedEffect
            state.loading.value = true
            state.boxFailed.value = false
            val parsed = HupuNbaStatsApi.fetchBoxScore(id, forceNetwork = force)?.let { parseNbaBoxScore(it) }
            if (parsed != null) {
                state.box.value = parsed
                state.boxServed = refreshKey
            } else {
                state.boxFailed.value = state.box.value == null
            }
            state.loading.value = false
            state.userPull.value = false
        } else {
            if (state.pbp.value != null && state.pbpServed == refreshKey) return@LaunchedEffect
            state.loading.value = true
            state.pbpFailed.value = false
            val parsed = HupuNbaStatsApi.fetchPlayByPlay(id, forceNetwork = force)?.let { parseNbaPlayByPlay(it) }
            if (parsed != null) {
                state.pbp.value = parsed
                state.pbpServed = refreshKey
            } else {
                state.pbpFailed.value = state.pbp.value == null
            }
            state.loading.value = false
            state.userPull.value = false
        }
    }

    // ---------- ④ 渲染 ----------
    val hasData = if (tab == NbaStatsTab.BOX) state.box.value != null else state.pbp.value != null
    val failed = if (tab == NbaStatsTab.BOX) state.boxFailed.value else state.pbpFailed.value
    val phase = when {
        // 尚未定位到 gameId：解析中 = 骨架；解析完仍为空 = 空态
        state.gameId.value == null -> if (state.resolving.value) "loading" else "empty"
        hasData -> "ok"
        failed -> "error"
        // 还没加载完（含「刚切过来、本 tab 首次加载」）→ 骨架，避免空态一闪
        else -> "loading"
    }

    val pullState = rememberPullToRefreshState()
    PullToRefreshBox(
        // 只有「用户主动下拉」才显示刷新指示器；30s 轮询是静默刷新，不弹圈、不闪
        isRefreshing = state.userPull.value && state.loading.value && hasData,
        onRefresh = {
            state.userPull.value = true
            state.refreshKey.intValue++
        },
        state = pullState,
        modifier = Modifier.fillMaxSize(),
    ) {
        Crossfade(targetState = phase, animationSpec = tween(180), label = "nbaStatsSwitch") { p ->
            when (p) {
                "loading" -> SkeletonHome()
                "error" -> ErrorRetry { state.refreshKey.intValue++ }
                "empty" -> NbaEmptyHint(tab)
                else -> when (tab) {
                    NbaStatsTab.BOX -> state.box.value?.let {
                        NbaBoxScoreContent(it, identity = statsKey, state = state)
                    }
                    NbaStatsTab.PLAY -> state.pbp.value?.let {
                        NbaPlayByPlayContent(it, live = query.live, state = state)
                    }
                }
            }
        }
    }
}

/** 无数据（比赛未开始 / 虎扑未生成 / 结构改版） */
@Composable
private fun NbaEmptyHint(tab: NbaStatsTab) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            when (tab) {
                NbaStatsTab.BOX -> "这场还没有数据统计"
                NbaStatsTab.PLAY -> "这场还没有文字实录"
            } + "\n（比赛未开始，或虎扑暂未生成）",
            fontSize = 13.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------------
// 数据统计
// ---------------------------------------------------------------------------

@Composable
private fun NbaBoxScoreContent(box: NbaBoxScore, identity: String?, state: NbaStatsState) {
    var team by remember { mutableStateOf(0) }   // 0 = 客队, 1 = 主队
    val selected: NbaTeamStats? = if (team == 0) box.away else box.home

    // 1.224d：球员 PK 的两名球员 —— 默认两队各自的得分王；用户换人后在同一场比赛内保持。
    // 1.224j：状态已上提到页面级 [NbaStatsState]（换人弹层挂载点随之提升到页面根）。
    // 只在「用户尚未选过」时填默认值 —— 直播轮询每 30s 会换一个新的 box 对象，
    // 若无条件重填，用户选的球员会被打回得分王（看着像整页被刷新）。
    // 换场时 nbaState 实例整体重建（remember(nbaKey)），这两个值自然回到空串、重新落到得分王。
    LaunchedEffect(box, identity) {
        if (state.pkAway.isBlank()) state.pkAway = nbaTopScorer(box.away)
        if (state.pkHome.isBlank()) state.pkHome = nbaTopScorer(box.home)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "__quarters__") { NbaQuarterCard(box) }

        if (box.away != null && box.home != null) {
            item(key = "__team_tabs__") {
                // 1.224（真机反馈）：球队切换改为与「最新回复 / 最新发布 / 24小时榜」**同款的二级
                // 分段控件**（SortBar 全宽模式）——玻璃容器 + 等宽分段 + 滑动选中胶囊，
                // 与全站二级 tab 语汇统一。
                // hPad/bottomPad 传 0：本页 LazyColumn 已给 16dp 内容边距，避免叠加造成
                // 控件比下方卡片窄 16dp 的错位。
                SortBar(
                    sorts = listOf(
                        SortTab(0, box.awayName.ifBlank { "客队" }, "0"),
                        SortTab(1, box.homeName.ifBlank { "主队" }, "1"),
                    ),
                    selected = team.toString(),
                    hPad = 0.dp,
                    bottomPad = 0.dp,
                    onSelect = { team = it.toIntOrNull() ?: 0 },
                )
            }
        }

        val stats = selected
        if (stats == null || stats.sections.isEmpty()) {
            item(key = "__no_players__") { NbaEmptyHint(NbaStatsTab.BOX) }
        } else {
            item(key = "__table__") { NbaPlayerTable(stats) }
        }

        // 1.224d：表下方两块 —— 球员 PK / 本场数据帝。
        // 两者都是「两队对比」，与上面的球队 tab 无关，所以固定显示、不再随 tab 切换；
        // 数据同源（都取自上方的球员数据表），因此与表格数值必然一致。
        val away = box.away
        val home = box.home
        if (away != null && home != null && away.sections.isNotEmpty() && home.sections.isNotEmpty()) {
            item(key = "__pk__") {
                NbaPlayerPkCard(
                    away = away,
                    home = home,
                    awayName = state.pkAway,
                    homeName = state.pkHome,
                    onPickAway = { state.pickSide = 0 },
                    onPickHome = { state.pickSide = 1 },
                )
            }
            item(key = "__kings__") { NbaStatKingsCard(away, home) }
                // 1.224h：球队统计（球队合计对比）——放在本场数据帝下方
                item(key = "__teamstats__") {
                    NbaTeamStatsCard(away, home, box.awayName, box.homeName)
                }
        }
    }
}

/**
 * 1.224j：球员 PK 换人弹层 —— 挂在**页面根节点**（[MatchDetailOverlay] 的外层 Box）而不是
 * tab 内容区。这样遮罩能盖住整屏（含顶栏 / 记分板）；此前挂在内容区时，弹层打开只有下半屏变暗，
 * 上半屏依旧亮着，看着很怪。
 *
 * 参数读的是页面级 [NbaStatsState]，因此与「数据统计」正文解耦 —— 切到别的 tab 也不会把弹层带走。
 */
@Composable
internal fun NbaPkPickerHost(state: NbaStatsState) {
    val box = state.box.value ?: return
    val pickSheet = when (state.pickSide) {
        0 -> box.away?.let { Triple(it, it.teamName.ifBlank { "客队" }, state.pkAway) }
        1 -> box.home?.let { Triple(it, it.teamName.ifBlank { "主队" }, state.pkHome) }
        else -> null
    }
    // 1.224f：换人面板用**自研半屏面板**。为让退场动画播完（260ms），保留最后一次参数——
    // 否则 pickSide 归 -1 的瞬间整块被卸载，面板会「啪」地消失。
    LaunchedEffect(pickSheet) { if (pickSheet != null) state.lastPickSheet = pickSheet }
    (pickSheet ?: state.lastPickSheet)?.let { (pickStats, pickTitle, pickSelected) ->
        NbaPlayerPickerSheet(
            visible = pickSheet != null,
            title = pickTitle,
            stats = pickStats,
            selected = pickSelected,
            onSelect = { name ->
                if (state.pickSide == 0) state.pkAway = name else state.pkHome = name
                state.pickSide = -1
            },
            onDismiss = { state.pickSide = -1 },
        )
    }
}

/** 分节比分卡（数据统计 / 文字实录两页共用） */
@Composable
private fun NbaQuarterCard(
    awayName: String,
    homeName: String,
    awayQuarters: List<String>,
    awayTotal: String,
    homeQuarters: List<String>,
    homeTotal: String,
) {
    val quarterCount = maxOf(awayQuarters.size, homeQuarters.size)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            for (i in 0 until quarterCount) {
                Text(
                    "${i + 1}",
                    modifier = Modifier.width(30.dp),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "总分",
                modifier = Modifier.width(44.dp),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        NbaQuarterLine(awayName, awayQuarters, awayTotal, quarterCount)
        NbaQuarterLine(homeName, homeQuarters, homeTotal, quarterCount)
    }
}

@Composable
private fun NbaQuarterLine(
    name: String,
    quarters: List<String>,
    total: String,
    quarterCount: Int,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            name,
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
        )
        for (i in 0 until quarterCount) {
            Text(
                quarters.getOrElse(i) { "" },
                modifier = Modifier.width(30.dp),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            total,
            modifier = Modifier.width(44.dp),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun NbaQuarterCard(box: NbaBoxScore) = NbaQuarterCard(
    awayName = box.awayName,
    homeName = box.homeName,
    awayQuarters = box.awayQuarters,
    awayTotal = box.awayTotal,
    homeQuarters = box.homeQuarters,
    homeTotal = box.homeTotal,
)

/** 数值列最低列宽：容得下 "12-21"/"+15" 这类短串，也是窄屏与超大字体下的下限 */
private val NbaMinCellW = 42.dp
/** 球员名列基准宽：最长 10 字（「波格丹-波格丹诺维奇」）换行到 2 行可完整显示 */
private val NbaMinNameW = 132.dp
/** 单元格左右内边距 + 安全余量：避免「测量宽度」与「实际排版」差 1px 就出省略号 */
private val NbaCellPad = 8.dp

/**
 * 表格几何（纯计算，与 Compose 无关，便于单测）。
 *
 * 把「各列内容所需宽度」分配到可用宽度上：
 *  · 装得下 → 余量**平均分给名称列与所有数值列**，整表宽度精确 = [avail]（左右不留白）；
 *  · 装不下 → 各列保持内容所需宽度，由外层横向滚动兜底（**绝不压缩到换行/省略**）。
 *
 * @return [Layout.nameW] 名称列宽度、[Layout.cols] 数值列宽度、[Layout.scroll] 是否横向滚动
 */
internal data class NbaTableLayout(val nameW: Dp, val cols: List<Dp>, val scroll: Boolean)

internal fun layoutNbaTable(avail: Dp, colWs: List<Dp>, minNameW: Dp): NbaTableLayout {
    val contentW = minNameW + colWs.fold(0.dp) { acc, w -> acc + w }
    val fits = avail >= contentW
    val extra = if (fits) (avail - contentW) / (colWs.size + 1) else 0.dp
    return NbaTableLayout(nameW = minNameW + extra, cols = colWs.map { it + extra }, scroll = !fits)
}

/**
 * 球员数据表。
 *
 * 1.201 修正：
 *  · **长名字不截断**：球员名列由 116dp 提到 132dp，并允许换行到 2 行
 *    （真实数据里最长 10 字：「波格丹-波格丹诺维奇」「塞尔吉奥-德-拉雷亚」）。
 *
 * 1.224 修正（真机反馈「居中后两侧看起来很空」）：
 *  · 平板/宽屏**不再居中留白**，而是把列撑开、**正好铺满整行**；窄屏横向滚动。
 *
 * 1.224b 修正（真机反馈「系统字体调大后数据会挤」）：
 *  · 数值列改为**按内容自适应列宽** —— 用 [rememberTextMeasurer] 按当前字号量出每列
 *    （表头 + 全部球员值 + 汇总值）的最宽文本，列宽随内容与水涨船高，
 *    大字体下数据既**不换行**、也不会被省略号截断；
 *  · 同时保留 [NbaMinCellW] 作为最低列宽（用户确认这个宽度好看），防止列被压得过窄；
 *  · 名称列不参与自适应（长名换行本来就是预期行为），仍是 [NbaMinNameW]；
 *  · 整表有富余宽度时，把余量**平均分给所有列**，保持「铺满、左右不留白」。
 */
@Composable
private fun NbaPlayerTable(stats: NbaTeamStats) {
    val columns = stats.sections.firstOrNull()?.columns ?: emptyList()
    val cellCount = columns.size + 1   // +「位置」列
    val hScroll = rememberScrollState()
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val avail = maxWidth
        val minTotal = NbaMinNameW + NbaMinCellW * cellCount
        // 字号同比放大「一半」幅度，封顶 1.7×（超大字体由列宽自适应兜底，不靠缩字）
        val f = (1f + ((avail / minTotal) - 1f) * 0.5f).coerceIn(1f, 1.7f)
        val nameFs = (13f * f).sp
        val headFs = (12f * f).sp
        val cellFs = (12f * f).sp

        // ---------- 按内容量出每一列的宽度（下标 0 =「位置」列） ----------
        // ⚠️ 测量必须**以 LocalTextStyle 为基准**：App 的 bodyLarge 带 letterSpacing = 0.5.sp，
        // Text() 渲染时会带上它，若用裸 TextStyle（字间距 0）去量，会量窄约 15% ——
        // 表现为「统计 / 命中率」这类最长串（47-86 / 54.7%）恰好被省略号截断。
        val baseStyle = LocalTextStyle.current
        val headStyle = baseStyle.merge(TextStyle(fontSize = headFs, fontWeight = FontWeight.SemiBold))
        val cellStyle = baseStyle.merge(TextStyle(fontSize = cellFs, fontWeight = FontWeight.Normal))
        // 汇总行以 bold 渲染，单独按半粗体量
        val boldCellStyle = baseStyle.merge(TextStyle(fontSize = cellFs, fontWeight = FontWeight.SemiBold))
        val padPx = with(density) { NbaCellPad.toPx() }
        val colWs: List<Dp> = (0 until cellCount).map { idx ->
            val normal = ArrayList<String>()
            val bold = ArrayList<String>()
            if (idx == 0) {
                // 「位置」列：只有球员行有值（G / F / F-C）
                stats.sections.forEach { s -> s.rows.forEach { normal += it.pos } }
            } else {
                val ci = idx - 1
                normal += columns.getOrElse(ci) { "" }
                stats.sections.forEach { s -> s.rows.forEach { normal += it.values.getOrElse(ci) { "" } } }
                stats.summaries.forEach { bold += it.values.getOrElse(ci) { "" } }
            }
            val widestPx = normal.filter { it.isNotEmpty() }
                .maxOfOrNull { measurer.measure(it, cellStyle).size.width.toFloat() } ?: 0f
            val boldPx = if (idx == 0) 0f else bold.filter { it.isNotEmpty() }
                .maxOfOrNull { measurer.measure(it, boldCellStyle).size.width.toFloat() } ?: 0f
            // 表头用半粗体，单独量一次与两者取大
            val headPx = if (idx == 0) 0f
                else measurer.measure(columns.getOrElse(idx - 1) { "" }, headStyle).size.width.toFloat()
            val needPx = maxOf(widestPx, boldPx, headPx) + padPx
            maxOf(NbaMinCellW, with(density) { needPx.toDp() })
        }

        // 装得下 → 余量平均分给所有列，整表正好铺满；装不下 → 保持内容宽度并横向滚动
        val layout = layoutNbaTable(avail, colWs, NbaMinNameW)
        val tableW = if (layout.scroll) colWs.fold(NbaMinNameW) { acc, w -> acc + w } else avail

        val boxMod = if (layout.scroll) Modifier.fillMaxWidth().horizontalScroll(hScroll)
            else Modifier.fillMaxWidth()
        Box(boxMod) {
            NbaPlayerTableBody(stats, columns, layout.nameW, layout.cols, tableW, nameFs, headFs, cellFs)
        }
    }
}

@Composable
private fun NbaPlayerTableBody(
    stats: NbaTeamStats,
    columns: List<String>,
    nameW: Dp,
    colWs: List<Dp>,
    tableW: Dp,
    nameFs: TextUnit,
    headFs: TextUnit,
    cellFs: TextUnit,
) {
    fun colW(i: Int) = colWs.getOrElse(i) { NbaMinCellW }
    Column(
        Modifier
            .width(tableW)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
            .padding(vertical = 10.dp),
    ) {
        // 表头（与数据行同一套列宽与左内边距，避免首列错位）
        Row(verticalAlignment = Alignment.CenterVertically) {
            NbaNameCell(stats.teamName, nameW, nameFs, bold = true)
            NbaCell("", colW(0), bold = true, fontSize = headFs)
            columns.forEachIndexed { i, text -> NbaCell(text, colW(i + 1), bold = true, fontSize = headFs) }
        }
        stats.sections.forEach { section ->
            if (section.title.isNotBlank()) {
                Text(
                    section.title,
                    modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 2.dp),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            section.rows.forEach { row ->
                NbaPlayerRowLine(row, nameW, colWs, nameFs, cellFs, section.columns.size)
            }
        }
        // 1.201：表尾「统计 / 命中率」汇总行（虎扑用 class="title" 且不含 <b> 的行表示）
        if (stats.summaries.isNotEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
            )
            stats.summaries.forEach { row ->
                NbaPlayerRowLine(row, nameW, colWs, nameFs, cellFs, columns.size, bold = true)
            }
        }
    }
}

/** 球员名列（左侧固定列）：长名允许换行到 2 行，保证完整显示 */
@Composable
private fun NbaNameCell(name: String, nameW: Dp, fontSize: TextUnit, bold: Boolean = false) {
    Column(Modifier.width(nameW).padding(start = 12.dp, end = 4.dp)) {
        Text(
            name,
            fontSize = fontSize,
            lineHeight = fontSize * 1.12f,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            // 2 行足够覆盖真实最长名（10 字）；Ellipsis 仅作极端兜底
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun NbaPlayerRowLine(
    row: NbaPlayerRow,
    nameW: Dp,
    colWs: List<Dp>,
    nameFs: TextUnit,
    cellFs: TextUnit,
    columnCount: Int,
    bold: Boolean = false,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        NbaNameCell(row.name, nameW, nameFs, bold = bold)
        NbaCell(row.pos, colWs.getOrElse(0) { NbaMinCellW }, bold = bold, fontSize = cellFs)
        for (i in 0 until columnCount) {
            NbaCell(
                row.values.getOrElse(i) { "" },
                colWs.getOrElse(i + 1) { NbaMinCellW },
                bold = bold,
                fontSize = cellFs,
            )
        }
    }
    Spacer(Modifier.height(2.dp))
}

/**
 * 数据/表头单元格。
 *
 * 1.224b：`softWrap = false` —— 即使列宽被极端压缩也**绝不换行**（宁可省略号），
 * 与「列宽按内容自适应」配套，保证大字体下表格始终是规整的单行。
 */
@Composable
private fun NbaCell(
    text: String,
    width: Dp,
    bold: Boolean = false,
    align: TextAlign = TextAlign.Center,
    fontSize: TextUnit = 12.sp,
) {
    Text(
        text,
        modifier = Modifier.width(width).padding(horizontal = 2.dp),
        fontSize = fontSize,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        textAlign = align,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        color = if (bold) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ---------------------------------------------------------------------------
// 文字实录
// ---------------------------------------------------------------------------

@Composable
private fun NbaPlayByPlayContent(pbp: NbaPlayByPlay, live: Boolean, state: NbaStatsState) {
    // 列表状态与「是否已定位」都放在页面级 state 里：切 tab 来回不丢位置、也不会重复定位
    val listState = state.playList
    val infoLines = listOf(pbp.startText, pbp.durationText, pbp.arenaText, pbp.attendanceText)
        .filter { it.isNotBlank() }

    // 按节分组（事件流本身按时间正序，最新的一条在**末尾**）
    var lastQuarter = -1
    val rows = ArrayList<Any>()
    pbp.plays.forEach { p ->
        if (p.quarter != lastQuarter) {
            lastQuarter = p.quarter
            rows += p.quarter
        }
        rows += p
    }

    // 1.201：LazyColumn 里 rows 之前还压着「信息卡」这一项（见下方 item("__info__")），
    // 所以**列表下标 = headOffset + rows 下标**。
    // 之前一直直接拿 rows.lastIndex 当列表索引用，目标位置整整少算了一条 ——
    // 这正是「一次更新多条时总差一点、滑不到位」的隐藏来源之一。
    val headOffset = if (infoLines.isNotEmpty()) 1 else 0

    // ---------- 1.201：直播跟随 ----------
    // 进行中的比赛：新事件到达后自动滚到最新一条（「文字实录直播」手感）。
    // 用户手动上翻浏览时（atBottom=false）自动跟随暂停，不打扰。
    var countAtLastUpdate by remember { mutableIntStateOf(0) }
    var atBottom by remember { mutableStateOf(true) }

    // 1.201（真机反馈）：**仅供「离底判定」使用的基准长度**。
    // ⚠️ 不能拿 countAtLastUpdate 当基准：它会被下面那个 effect 在数据到达的**当帧**
    // 立刻推进到新长度，于是判定条件同样成立，接着就用**新长度**去算
    // `lastIdx >= total - 2` —— 一次来了很多条时必然为 false，被当成「用户翻上去看旧消息」，
    // 跟随直接被关掉。表现就是「新事件一多，列表完全不滚、也不跟着走」。
    // 正确做法：**列表长度一变就跳过这次判定**（视口没动、用户也没动，只是下面多了东西），
    // 只把基准推进到新长度；等下一次「长度不变的纯滚动」才重新判定是否贴底。
    var baselineCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.layoutInfo.totalItemsCount to
                (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1)
        }.collect { (total, lastIdx) ->
            // 口径统一：total 是**列表**条目数（含信息卡），rows 长度 = total - headOffset
            val count = total - headOffset
            if (count == baselineCount) {
                // 长度没变 = 纯滚动（用户拖动）→ 只有这里才更新「是否贴底」
                atBottom = lastIdx >= total - 2
            } else {
                // 长度变了 = 末尾插入/删减 → 视口与用户都没动，跳过判定
                baselineCount = count
            }
        }
    }
    LaunchedEffect(rows.size) {
        val grew = rows.size > countAtLastUpdate
        countAtLastUpdate = rows.size
        if (rows.isEmpty() || !atBottom) return@LaunchedEffect
        val target = headOffset + rows.lastIndex

        // 1.224e：**首次呈现**（或换场后）→ 直接瞬移到底，不播滑动动画。
        // 直播场次动辄几百条，「从顶部一路滑到底」要在几百毫秒里组合/布局全部条目 ——
        // 真机上就是明显掉帧（「切到实录页下滑很卡」），而且还看不清。
        // 这个场景用户要的是「立刻看到最新」，并不需要"看它滑"，所以直接呈现；
        // 之后新到达的事件才走下面的柔化滑动（那时增量小，滑得动也看得清）。
        if (!state.playPositioned) {
            state.playPositioned = true
            if (live) {
                listState.scrollToItem(target)
                atBottom = true
            }
            return@LaunchedEffect
        }

        if (!live || !grew) return@LaunchedEffect

        // 1.201：用户一上手往回翻（上翻看历史）就**立刻让路**。
        // 既尊重手势，也避免自动滚动与用户拖动互相「抢」列表（真机上表现为抖动、来回弹）。
        if (listState.lastScrolledBackward) {
            atBottom = false
            return@LaunchedEffect
        }

        val li0 = listState.layoutInfo
        val lastVisible = li0.visibleItemsInfo.lastOrNull()?.index ?: return@LaunchedEffect
        if (lastVisible >= target) return@LaunchedEffect

        // 1.224e：增量过大（用户离开很久、或一节打完回来一次补很多条）→ 同样瞬移。
        // 这种量级下柔化滑动会跨很多屏，「滑」既拖帧又看不出内容，直接到底更干净。
        if (target - lastVisible > NbaMaxAnimatedItems) {
            listState.scrollToItem(target)
            atBottom = true
            return@LaunchedEffect
        }

        // 1.201（真机反馈）：分「柔化拍 + 补齐拍」，兼得「柔和」与「一定到底」。
        //  ① 柔化拍：按估算滑一段，460ms 缓出。估算宁可偏大——多滚的部分会被列表底部
        //     边界吃掉（滚不过内容末尾），所以「宁多勿少」是零风险的。
        //  ② 补齐拍：用**实测布局**算还差多少，每轮固定 240ms。
        //     ⚠️ 这里**不能用 animateScrollToItem**：它没有时长参数、时长随距离自适应，
        //     短距离时几乎是瞬移，真机上手感就是「嘣一下」（1.201 真机反馈）。
        //     也不能「滑一次超大距离」了事：距离/时长 会变成极高速度，同样像瞬移。
        //     所以只能老老实实测量、小步补，通常 1 轮就够（①已估了 1.6 倍余量）。
        val avgH0 = li0.visibleItemsInfo.map { it.size }.average().toFloat()
        if (avgH0 <= 0f) return@LaunchedEffect
        listState.animateScrollBy(
            (target - lastVisible) * avgH0 * 1.6f,
            tween(durationMillis = 460, easing = FastOutSlowInEasing),
        )

        repeat(5) {
            withFrameNanos { }
            // 用户中途上手 → 立刻停手让路（同上，避免与拖动对抗）
            if (listState.lastScrolledBackward) {
                atBottom = false
                return@LaunchedEffect
            }
            val li = listState.layoutInfo
            val lastInfo = li.visibleItemsInfo.lastOrNull() ?: return@repeat
            val clipped = (lastInfo.offset + lastInfo.size) - li.viewportEndOffset
            // 到位判据：最后一条已经进入视口，且底部没有被裁掉
            if (lastInfo.index >= target && clipped <= 2) return@repeat
            val avgH = li.visibleItemsInfo.map { it.size }.average().toFloat().takeIf { it > 0f } ?: 60f
            val delta = if (lastInfo.index < target) {
                // 最后一条还没露面 → 先把差的那几条估着带进来
                avgH * (target - lastInfo.index)
            } else {
                // 已露面但底部被裁 → 精确补齐裁切量
                clipped.toFloat()
            }
            if (delta <= 1f) return@repeat
            listState.animateScrollBy(delta, tween(durationMillis = 240, easing = FastOutSlowInEasing))
        }
        // 跟到了最新 → 明确回到「贴底」状态。
        // 否则一旦上面某一步的判定受干扰（例如极端时序下 atBottom 被误置 false），
        // 下一轮就会因为 atBottom=false 而彻底不再跟随，且再也不会自愈。
        atBottom = true
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (infoLines.isNotEmpty()) {
            item(key = "__info__") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    infoLines.forEach {
                        Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        // 1.201：按需求去掉「分节比分卡」（上下比分一眼可见，实录页只保留事件流）

        // 1.201：稳定 key —— 事件用虎扑毫秒时间戳（解析层保证唯一），节标题用 "q+节号"。
        // 有了 key，直播刷新时 Compose 只认「末尾多了一条」，已有条目**零重排**；
        // 否则 items 只能按位置 diff，新事件一到达就可能整屏重绘（真机观感 = 闪一下）。
        items(rows, key = { item -> if (item is NbaPlay) "p${item.id}" else "q$item" }) { item ->
            if (item is Int) {
                Text(
                    if (item <= 4) "第 $item 节" else "加时",
                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else if (item is NbaPlay) {
                if (item.note) NbaPlayNoteLine(item) else NbaPlayLine(item)
            }
        }
    }
}

/** 跨列整行提示（暂停 / 节结束 / 半场结束）——原页为居中加粗，这里同款 */
@Composable
private fun NbaPlayNoteLine(play: NbaPlay) {
    Text(
        play.text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun NbaPlayLine(play: NbaPlay) {
    // 1.201（真机反馈）：四列按**首行文本基线**对齐（alignByBaseline），不再用 Alignment.Top。
    // 原因是事件文本 13sp、时间/球队/比分 12sp，顶部对齐时大字号那格看起来"浮"在上面，
    // 一行里几列的视觉基准不统一。按基线对齐后，同一行不管字号异同都落在同一条水平线上；
    // 事件文本换到第二行时也不影响（对齐用的是 first baseline）。
    Row(Modifier.fillMaxWidth()) {
        Text(
            play.time,
            modifier = Modifier.width(44.dp).alignByBaseline(),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            play.team,
            modifier = Modifier.width(40.dp).alignByBaseline(),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
        )
        Text(
            play.text,
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp).alignByBaseline(),
            fontSize = 13.sp,
            lineHeight = 19.sp,
            // 1.201：与虎扑原页一致——「重要动作」（得分/盖帽/抢断等）加粗
            fontWeight = if (play.important) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            play.score,
            modifier = Modifier.width(52.dp).alignByBaseline(),
            fontSize = 12.sp,
            // 比分同步：重要动作的比分也加粗，与事件同一重量
            fontWeight = if (play.important) FontWeight.Bold else FontWeight.SemiBold,
            textAlign = TextAlign.End,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
