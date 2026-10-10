package com.java.myapplication.ui.pages

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
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
 * 1.201：NBA 数据/实录的加载状态。
 *
 * 由比赛详情页 `remember(query)` 持有（**不随 tab 切换销毁**），这样：
 *  · 「评分 → 数据统计 → 评分 → 数据统计」来回切**不会重复解析、不会闪骨架**；
 *  · 换一场比赛时只有 query 变化才整体重置（[resolvedQuery] 指纹）。
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

    /** 已解析过的 query（换场才重置；纯内存比较，不触发重组） */
    var resolvedQuery: NbaStatsQuery? = null
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
    LaunchedEffect(query) {
        if (state.resolvedQuery == query) return@LaunchedEffect
        state.resolvedQuery = query
        state.resolving.value = true
        state.gameId.value = null
        state.box.value = null
        state.pbp.value = null
        state.boxFailed.value = false
        state.pbpFailed.value = false
        state.boxServed = -1
        state.pbpServed = -1
        state.gameId.value = HupuNbaStatsApi.resolveGameId(
            date = query.date,
            home = query.home,
            away = query.away,
            homeScore = query.homeScore,
            awayScore = query.awayScore,
        )
        state.resolving.value = false
    }

    // ---------- ② 进行中：30s 静默重拉（本 tab 可见时才轮询） ----------
    LaunchedEffect(query, state.gameId.value) {
        if (!query.live || state.gameId.value == null) return@LaunchedEffect
        while (true) {
            delay(30_000)
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
                    NbaStatsTab.BOX -> state.box.value?.let { NbaBoxScoreContent(it) }
                    NbaStatsTab.PLAY -> state.pbp.value?.let { NbaPlayByPlayContent(it, live = query.live) }
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
private fun NbaBoxScoreContent(box: NbaBoxScore) {
    var team by remember { mutableStateOf(0) }   // 0 = 客队, 1 = 主队
    val selected: NbaTeamStats? = if (team == 0) box.away else box.home

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

/**
 * 球员数据表。
 *
 * 1.201 修正：
 *  · **长名字不截断**：球员名列由 116dp 提到 132dp，并允许换行到 2 行
 *    （真实数据里最长 10 字：「波格丹-波格丹诺维奇」「塞尔吉奥-德-拉雷亚」）。
 *
 * 1.224 修正（真机反馈「居中后两侧看起来很空」）：
 *  · 平板/宽屏**不再居中留白**，而是把列按比例撑开、**正好铺满整行** —— 表格左右都不再有余白；
 *  · 字号只按放大幅度的一半增长，并封顶 1.7×，避免大屏上字大得夸张；
 *  · 窄屏（手机）仍维持最小列宽 + 横向滚动。
 */
@Composable
private fun NbaPlayerTable(stats: NbaTeamStats) {
    val columns = stats.sections.firstOrNull()?.columns ?: emptyList()
    val minNameW = 132.dp
    // 数值列统一 42dp：容得下 "12-21"/"+15" 这类最长串
    val minCellW = 42.dp
    val cellCount = columns.size + 1   // +「位置」列
    val hScroll = rememberScrollState()

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val avail = maxWidth
        val minTotal = minNameW + minCellW * cellCount
        // 够宽 → 等比撑开铺满整行；不够宽 → 维持最小列宽靠横向滚动
        val fits = avail >= minTotal
        val scale = if (fits) avail / minTotal else 1f
        val nameW = if (fits) minNameW * scale else minNameW
        // 用「剩余宽度 / 列数」直接算数值列宽，保证各列之和精确 = avail（不留 1px 级空隙）
        val cellW = if (fits) (avail - nameW) / cellCount else minCellW
        val tableW = if (fits) avail else minTotal
        // 字号同比放大「一半」幅度，封顶 1.7×
        val f = (1f + (scale - 1f) * 0.5f).coerceAtMost(1.7f)
        val nameFs = (13f * f).sp
        val headFs = (12f * f).sp
        val cellFs = (12f * f).sp

        val boxMod = if (fits) Modifier.fillMaxWidth()
            else Modifier.fillMaxWidth().horizontalScroll(hScroll)
        Box(boxMod) {
            NbaPlayerTableBody(stats, columns, nameW, cellW, tableW, nameFs, headFs, cellFs)
        }
    }
}

@Composable
private fun NbaPlayerTableBody(
    stats: NbaTeamStats,
    columns: List<String>,
    nameW: Dp,
    cellW: Dp,
    tableW: Dp,
    nameFs: TextUnit,
    headFs: TextUnit,
    cellFs: TextUnit,
) {
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
            NbaCell("", cellW, bold = true, fontSize = headFs)
            columns.forEach { NbaCell(it, cellW, bold = true, fontSize = headFs) }
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
                NbaPlayerRowLine(row, nameW, cellW, nameFs, cellFs, section.columns.size)
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
                NbaPlayerRowLine(row, nameW, cellW, nameFs, cellFs, columns.size, bold = true)
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
    cellW: Dp,
    nameFs: TextUnit,
    cellFs: TextUnit,
    columnCount: Int,
    bold: Boolean = false,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        NbaNameCell(row.name, nameW, nameFs, bold = bold)
        NbaCell(row.pos, cellW, bold = bold, fontSize = cellFs)
        for (i in 0 until columnCount) {
            NbaCell(row.values.getOrElse(i) { "" }, cellW, bold = bold, fontSize = cellFs)
        }
    }
    Spacer(Modifier.height(2.dp))
}

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
        overflow = TextOverflow.Ellipsis,
        color = if (bold) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ---------------------------------------------------------------------------
// 文字实录
// ---------------------------------------------------------------------------

@Composable
private fun NbaPlayByPlayContent(pbp: NbaPlayByPlay, live: Boolean) {
    val info = listOf(pbp.startText, pbp.durationText, pbp.arenaText, pbp.attendanceText)
        .filter { it.isNotBlank() }

    val listState = rememberLazyListState()

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

    // ---------- 1.201：直播跟随 ----------
    // 进行中的比赛：新事件到达后自动滚到最新一条（「文字实录直播」手感）。
    // 用 [countAtLastUpdate] 区分「纯滚动」与「末尾追加」——只在条目数未变的滚动事件里
    // 更新 atBottom，否则刚追加完就会被误判为「已离底」而不再跟随。
    // 用户手动上翻浏览时（atBottom=false）自动跟随暂停，不打扰。
    var countAtLastUpdate by remember { mutableIntStateOf(0) }
    var atBottom by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.layoutInfo.totalItemsCount to
                (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1)
        }.collect { (total, lastIdx) ->
            if (total == countAtLastUpdate) atBottom = lastIdx >= total - 2
        }
    }
    LaunchedEffect(rows.size) {
        val grew = rows.size > countAtLastUpdate
        countAtLastUpdate = rows.size
        if (live && grew && rows.isNotEmpty() && atBottom) {
            listState.animateScrollToItem(rows.lastIndex)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (info.isNotEmpty()) {
            item(key = "__info__") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    info.forEach {
                        Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        // 1.201：按需求去掉「分节比分卡」（上下比分一眼可见，实录页只保留事件流）

        // 不设 key：同一时刻可能出现字面完全相同的事件（key 重复会直接崩）
        items(rows) { item ->
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
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            play.time,
            modifier = Modifier.width(44.dp),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            play.team,
            modifier = Modifier.width(40.dp),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
        )
        Text(
            play.text,
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            fontSize = 13.sp,
            lineHeight = 19.sp,
            // 1.201：与虎扑原页一致——「重要动作」（得分/盖帽/抢断等）加粗
            fontWeight = if (play.important) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            play.score,
            modifier = Modifier.width(52.dp),
            fontSize = 12.sp,
            // 比分同步：重要动作的比分也加粗，与事件同一重量
            fontWeight = if (play.important) FontWeight.Bold else FontWeight.SemiBold,
            textAlign = TextAlign.End,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
