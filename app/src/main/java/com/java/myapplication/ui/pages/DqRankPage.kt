package com.java.myapplication.ui.pages

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.java.myapplication.data.DQ_COMPETITIONS
import com.java.myapplication.data.DQ_PERSON_METRICS
import com.java.myapplication.data.DQ_TEAM_METRICS
import com.java.myapplication.data.DqBracketStage
import com.java.myapplication.data.DqDiskCache
import com.java.myapplication.data.DqKnockout
import com.java.myapplication.data.DqRankApi
import com.java.myapplication.data.DqRankCache
import com.java.myapplication.data.DqRankParser
import com.java.myapplication.data.DqSection
import com.java.myapplication.data.NbaColWidthCache
import com.java.myapplication.data.NbaColWidthMeasurer
import com.java.myapplication.data.NbaRankTable
import com.java.myapplication.data.SortTab
import com.java.myapplication.ui.components.CenteredTopBar
import com.java.myapplication.ui.components.Chip
import com.java.myapplication.ui.components.ChipAutoCenterRow
import com.java.myapplication.ui.components.ChipSlot
import com.java.myapplication.ui.components.SecondaryPage
import com.java.myapplication.ui.components.SortBar
import com.java.myapplication.ui.components.rememberChipAutoCenter
import com.java.myapplication.ui.glass.FrostedHeaderLayout
import com.java.myapplication.ui.theme.isAppDarkTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * 1.294：**足球数据**（懂球帝数据源）—— 盖入式二级页。
 *
 * 层级：
 * ```
 * 赛事 chip（15 个，选中自动居中）
 *   └ 分段：积分榜 / 球员榜 / 球队榜
 *        ├ 球员榜 → 13 种指标 chip（自动居中）
 *        ├ 球队榜 → 5 种指标 chip
 *        └ 积分榜 → 联赛=一张名次表；杯赛=各阶段**对阵卡** + 小组名次表
 * ```
 *
 * 性能（真机反馈「切 tab 卡」后的三条铁律）：
 *  · 数据再多也**不在动画期间建表**：切换 tab 后先等 chip 动画走完（[SWITCH_SETTLE_MS]）再提交内容；
 *  · 每个 tab 保留一份已构建内容，切回**同帧呈现**；
 *  · 列宽在后台预热（不阻塞渲染）。
 */
private const val SWITCH_SETTLE_MS = 200L

@Composable
internal fun DqRankOverlay(
    title: String = "足球数据",
    onClose: () -> Unit,
    zIndex: Float = 6f,
) {
    DisposableEffect(Unit) {
        SecondaryPage.enter()
        onDispose { SecondaryPage.exit() }
    }
    var closing by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(280)) }
    LaunchedEffect(closing) {
        if (closing) {
            progress.animateTo(0f, tween(280))
            onClose()
        }
    }
    PredictiveBackHandler { events ->
        if (closing) {
            events.collect { }
            return@PredictiveBackHandler
        }
        try {
            events.collect { event -> progress.snapTo(1f - event.progress) }
            progress.animateTo(0f, tween(120))
            closing = true
        } catch (e: CancellationException) {
            withContext(kotlinx.coroutines.NonCancellable) { progress.snapTo(1f) }
            throw e
        }
    }

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var compIdx by remember { mutableIntStateOf(0) }
    var mode by remember { mutableIntStateOf(0) }   // 0 积分榜 / 1 球员榜 / 2 球队榜
    var personMetric by remember { mutableStateOf(DQ_PERSON_METRICS.first().key) }
    var teamMetric by remember { mutableStateOf(DQ_TEAM_METRICS.first().key) }

    val comp = DQ_COMPETITIONS[compIdx]
    val metric = if (mode == 1) personMetric else teamMetric
    // 1.298：积分榜与「球队榜指标」无关，key 里不该带 metric ——
    // 否则在球队榜切过指标后回到积分榜，会被当成新 tab 再拉一次网络。
    val tabKey = "${comp.id}_${mode}" + if (mode == 0) "" else "_$metric"

    val rendered = remember { mutableStateMapOf<String, DqSection>() }
    var lastTabKey by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    // 本次进入页面是否已经出过内容：决定后续切换要不要给 chip 动画让位
    var pageEntered by remember { mutableStateOf(false) }

    /** 提交内容：切换 tab 时先让 chip 动画跑完，再建表（动画期间主线程不干重活） */
    suspend fun commit(section: DqSection) {
        if (pageEntered) delay(SWITCH_SETTLE_MS)
        rendered[tabKey] = section
        if (rendered.size > 2) {
            val keep = setOf(tabKey, lastTabKey)
            rendered.keys.filter { it !in keep }.forEach { rendered.remove(it) }
        }
        lastTabKey = tabKey
        loading = false
        pageEntered = true
    }

    /** 列宽后台预热（不阻塞渲染；命中时组合期零测量） */
    fun warmUp(section: DqSection) {
        val d = density.density
        scope.launch {
            withContext(kotlinx.coroutines.Dispatchers.Default) {
                section.tables.forEach { t ->
                    val k = NbaColWidthCache.key(t)
                    if (NbaColWidthCache.get(k) == null) {
                        NbaColWidthCache.put(k, NbaColWidthMeasurer.computePx(t, 11f, 12f, d))
                    }
                }
            }
        }
    }

    LaunchedEffect(comp.id, mode, metric) {
        // 本次会话内已渲染过 → 同帧切回
        if (rendered.containsKey(tabKey)) {
            loading = false
            return@LaunchedEffect
        }
        failed = false
        // 1.299：**先让「打开动画」跑完，再开始取数据**（与 NBA 数据页一致）。
        // 之前为了抢速度把这一步去掉了，于是网络 + 解析会和盖入动画抢主线程，
        // 冷启动/低端机上有掉帧风险。骨架屏在这 240ms 里顶着，观感反而更顺。
        if (!pageEntered) delay(240)

        // 1.299：解析放到 Default 线程 —— 杯赛（多轮次 + 多组 + 淘汰赛）解析量不小，
        // 留在主线程会和动画/首帧抢时间。
        suspend fun parse(json: String): DqSection =
            withContext(kotlinx.coroutines.Dispatchers.Default) {
                when (mode) {
                    0 -> DqRankParser.parseStandingSection(json)
                    1 -> DqSection(tables = DqRankParser.parsePersonRanking(json))
                    else -> DqSection(tables = DqRankParser.parseTeamRanking(json))
                }
            }

        // ① 内存缓存
        DqRankCache.get(tabKey)?.let {
            warmUp(it)
            commit(it)
            return@LaunchedEffect
        }

        loading = true
        // ② 磁盘缓存：先出内容，再静默刷新
        var shownFromDisk = false
        DqDiskCache.read(tabKey)?.let { old ->
            val parsed = parse(old)
            if (parsed.tables.isNotEmpty() || parsed.stages.isNotEmpty()) {
                warmUp(parsed)
                shownFromDisk = true
                if (!pageEntered) {
                    // 首次进入页面：骨架屏直接换成内容
                    rendered[tabKey] = parsed
                    loading = false
                    pageEntered = true
                } else {
                    loading = false
                }
            }
        }

        // ③ 网络（立刻发起；直连接口约 400ms）
        val json = when (mode) {
            0 -> DqRankApi.standing(comp.id)
            1 -> DqRankApi.personRanking(comp.id, metric)
            else -> DqRankApi.teamRanking(comp.id, metric)
        }
        if (json != null) {
            val parsed = parse(json)
            if (parsed.tables.isNotEmpty() || parsed.stages.isNotEmpty()) {
                DqDiskCache.write(tabKey, json)
                DqRankCache.put(tabKey, parsed)
                warmUp(parsed)
                if (shownFromDisk && rendered[tabKey] != null) {
                    // 磁盘数据已经在屏上：静默替换（不等待、不闪骨架）
                    rendered[tabKey] = parsed
                    loading = false
                } else {
                    commit(parsed)
                }
                return@LaunchedEffect
            }
        }
        if (!shownFromDisk) {
            failed = true
            loading = false
        }
    }

    FrostedHeaderLayout(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(zIndex)
            .graphicsLayer { translationX = (1f - progress.value) * size.width },
        header = { CenteredTopBar(title = title, onBack = { if (!closing) closing = true }) },
    ) { topInset ->
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(top = topInset)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            val compCenter = rememberChipAutoCenter()
            ChipAutoCenterRow(
                center = compCenter,
                selectedIndex = compIdx,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DQ_COMPETITIONS.forEachIndexed { i, c ->
                    ChipSlot(compCenter, i) {
                        Chip(text = c.name, selected = compIdx == i, onClick = { compIdx = i })
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            SortBar(
                sorts = listOf(
                    SortTab(0, "积分榜", "standings"),
                    SortTab(1, "球员榜", "players"),
                    SortTab(2, "球队榜", "teams"),
                ),
                selected = when (mode) {
                    0 -> "standings"
                    1 -> "players"
                    else -> "teams"
                },
                hPad = 0.dp,
                bottomPad = 0.dp,
                onSelect = { key ->
                    mode = when (key) {
                        "standings" -> 0
                        "players" -> 1
                        else -> 2
                    }
                },
            )

            if (mode != 0) {
                val metrics = if (mode == 1) DQ_PERSON_METRICS else DQ_TEAM_METRICS
                val current = if (mode == 1) personMetric else teamMetric
                val mIdx = metrics.indexOfFirst { it.key == current }
                val mCenter = rememberChipAutoCenter()
                Spacer(Modifier.height(8.dp))
                ChipAutoCenterRow(
                    center = mCenter,
                    selectedIndex = mIdx,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    metrics.forEachIndexed { i, m ->
                        ChipSlot(mCenter, i) {
                            Chip(text = m.label, selected = current == m.key, onClick = {
                                if (mode == 1) personMetric = m.key else teamMetric = m.key
                            })
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            Box(Modifier.fillMaxSize()) {
                val current = rendered[tabKey]
                when {
                    // 1.295：先判失败；然后「只要没有内容就一定显示骨架」。
                    // 之前的条件漏了「切 tab 时 loading 已被置回 false、而内容还没提交」这个窗口，
                    // 那一瞬间三个分支都不命中 → 页面一片空白。
                    failed -> FailureView(
                        onRetry = {
                            scope.launch {
                                failed = false
                                loading = true
                                rendered.remove(tabKey)
                                val json = when (mode) {
                                    0 -> DqRankApi.standing(comp.id)
                                    1 -> DqRankApi.personRanking(comp.id, metric)
                                    else -> DqRankApi.teamRanking(comp.id, metric)
                                }
                                val parsed = if (json == null) DqSection() else when (mode) {
                                    0 -> DqRankParser.parseStandingSection(json)
                                    1 -> DqSection(tables = DqRankParser.parsePersonRanking(json))
                                    else -> DqSection(tables = DqRankParser.parseTeamRanking(json))
                                }
                                if (parsed.tables.isNotEmpty() || parsed.stages.isNotEmpty()) {
                                    if (json != null) DqDiskCache.write(tabKey, json)
                                    DqRankCache.put(tabKey, parsed)
                                    warmUp(parsed)
                                    rendered[tabKey] = parsed
                                    loading = false
                                } else {
                                    failed = true
                                    loading = false
                                }
                            }
                        },
                    )
                    current == null -> NbaTableSkeleton()
                    else -> {
                        if (current.stages.isNotEmpty()) {
                            // 杯赛：对阵卡 + 小组名次表，统一在 LazyColumn 里（一屏滚动）
                            DqMixedView(current)
                        } else {
                            NbaTablesView(current.tables, sortable = true)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 杯赛视图：淘汰赛对阵卡（按阶段）+ 小组名次表
// ---------------------------------------------------------------------------
@Composable
private fun DqMixedView(section: DqSection) {
    val tableBg = if (!isAppDarkTheme()) androidx.compose.ui.graphics.Color(0xFFEEF0F3)
    else MaterialTheme.colorScheme.surfaceVariant
    // 1.298：导航条高度算进 contentPadding（而不是给容器加 navigationBarsPadding），
    // 内容可以滚到导航条下面 —— 导航条区域看到的是内容/页面底色本身，不再是色带。
    val navBottom = androidx.compose.foundation.layout.WindowInsets.navigationBars
        .asPaddingValues().calculateBottomPadding()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp + navBottom),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 淘汰赛各阶段：一张卡一个阶段
        items(section.stages.size) { i ->
            val stage = section.stages[i]
            BracketStageCard(stage, tableBg)
        }
        // 小组/分区名次表
        items(section.tables.size) { i ->
            val table = section.tables[i]
            Column {
                if (table.title.isNotBlank()) {
                    Text(
                        table.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                    )
                }
                DqTableCard(table, tableBg)
            }
        }
    }
}

/**
 * 淘汰赛**阶段卡**：标题（决赛/半决赛/四分之一决赛…）+ 每场一行
 * 「主队（右对齐） · 比分 · 客队（左对齐）」，胜者加粗、负者次要色 —— 比塞进表格更符合语义。
 */
@Composable
private fun BracketStageCard(stage: DqBracketStage, bg: androidx.compose.ui.graphics.Color) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg),
    ) {
        Text(
            stage.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 6.dp),
        )
        stage.matches.forEachIndexed { i, m ->
            KnockedRow(m)
            if (i != stage.matches.lastIndex) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .height(0.5.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun KnockedRow(m: DqKnockout) {
    val strong = MaterialTheme.colorScheme.onSurface
    val weak = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            m.home,
            fontSize = 14.sp,
            fontWeight = if (m.homeWin) FontWeight.SemiBold else FontWeight.Normal,
            color = if (m.homeWin || !m.awayWin) strong else weak,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
        Box(
            Modifier.width(84.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                m.score.ifBlank { "–" },
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
        }
        Text(
            m.away,
            fontSize = 14.sp,
            fontWeight = if (m.awayWin) FontWeight.SemiBold else FontWeight.Normal,
            color = if (m.awayWin || !m.homeWin) strong else weak,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 杯赛里的小组名次表（LazyColumn 里的一张卡，可横向滑动） */
@Composable
private fun DqTableCard(table: NbaRankTable, bg: androidx.compose.ui.graphics.Color) {
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val containerW = maxWidth
        val widths = remember(table, containerW) { dqWidths(table, containerW, density.density) }
        val nameIdx = table.headers.indexOfFirst { it == "队名" || it == "球员" || it == "球队" }
            .let { if (it >= 0) it else 1 }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(bg)
                .horizontalScroll(rememberScrollState()),
        ) {
            Column {
                Row(Modifier.height(42.dp), verticalAlignment = Alignment.CenterVertically) {
                    table.headers.forEachIndexed { i, h ->
                        DqCell(
                            text = h,
                            width = widths.getOrElse(i) { 56.dp },
                            alignStart = i == nameIdx,
                            header = true,
                        )
                    }
                }
                table.rows.forEach { row ->
                    Row(Modifier.height(42.dp), verticalAlignment = Alignment.CenterVertically) {
                        row.forEachIndexed { i, c ->
                            DqCell(
                                text = c,
                                width = widths.getOrElse(i) { 56.dp },
                                alignStart = i == nameIdx,
                                bold = i == 0 || i == nameIdx,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DqCell(
    text: String,
    width: Dp,
    alignStart: Boolean,
    header: Boolean = false,
    bold: Boolean = false,
) {
    Text(
        text,
        modifier = Modifier
            .width(width)
            .padding(horizontal = 10.dp),
        fontSize = if (header) 12.sp else 13.sp,
        fontWeight = when {
            header -> FontWeight.Medium
            bold -> FontWeight.SemiBold
            else -> FontWeight.Normal
        },
        color = if (header) MaterialTheme.colorScheme.onSurfaceVariant
        else MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = if (alignStart) TextAlign.Start else TextAlign.Center,
    )
}

/** 列宽：内容实测（后台已预热）→ 加内边距；总宽不足一屏时均分铺满 */
private fun dqWidths(table: NbaRankTable, containerW: Dp, density: Float): List<Dp> {
    if (table.headers.isEmpty()) return emptyList()
    val basePx = NbaColWidthCache.get(NbaColWidthCache.key(table))
        ?: NbaColWidthMeasurer.computePx(table, 11f, 12f, density).also {
            NbaColWidthCache.put(NbaColWidthCache.key(table), it)
        }
    val pads = 20f * density
    var list = basePx.map { ((it + pads) / density).dp }
        .map { it.coerceIn(44.dp, 168.dp) }
    val total = list.fold(0.dp) { a, d -> a + d }
    if (total < containerW && list.isNotEmpty()) {
        val extra = (containerW - total) / list.size
        list = list.map { it + extra }
    }
    return list
}

@Composable
private fun FailureView(onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "这个赛事暂无榜单数据",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "点击重试",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onRetry)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}