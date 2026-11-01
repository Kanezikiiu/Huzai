package com.java.myapplication.ui.pages

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.java.myapplication.data.HupuRepository
import com.java.myapplication.data.NBA_PLAYER_METRICS
import com.java.myapplication.data.NbaColWidthCache
import com.java.myapplication.data.NbaColWidthMeasurer
import com.java.myapplication.data.NbaRankParser
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 1.277：**NBA 数据**二级页（战绩排名 + 数据排名）。
 *
 * 结构大改（解决「切换卡顿」+「旧表瞬间位移」）：
 *
 *  · **头部固定**：标题栏 / 主分段 / 二级排序条 / 指标条都不再随内容滚动；
 *  · **表格区改成 LazyColumn（行虚拟化）**：行数再多也只组合可见的 ~10 行，
 *    切换表格时不再一次性重建 600 个单元格 → 这是此前卡顿的根因；
 *  · **横向滚动只有一个容器**：外层 `horizontalScroll` 包住整个 LazyColumn，
 *    避免"每行各自滚动"的发涩手感，也避免"表格容器参与 weight"导致宽度为 0；
 *  · **表头吸顶**：用 `stickyHeader`，纵向滚动时列名始终可见；
 *  · 切换 tab 时列表**立刻归位到顶部**，新旧表格不会互相"顶一下"。
 */
@Composable
internal fun NbaRankOverlay(
    repo: HupuRepository,
    title: String = "NBA 数据",
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
            withContext(NonCancellable) { progress.snapTo(1f) }
            throw e
        }
    }

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var mode by remember { mutableIntStateOf(0) }        // 0 战绩排名 / 1 数据排名
    var standKey by remember { mutableStateOf("east") }  // east / west / division
    var rankKey by remember { mutableStateOf("players") } // players / teams
    var metric by remember { mutableStateOf(NBA_PLAYER_METRICS.first().key) }

    // 1.281：**每个 tab 各自保留一份已加载数据**（最多 2 份：当前 + 上一次）。
    // 切换时内容**在同一帧就换过去**（数据本来就在内存里），不再"tab 切了、表格稍后才变"，
    // 也不会因为清空-重建而闪一下。首次进入某个 tab 仍会走一次加载。
    val rendered = remember { androidx.compose.runtime.mutableStateMapOf<String, List<NbaRankTable>>() }
    var lastTabKey by remember { mutableStateOf("") }
    val tabKey = "${mode}_${standKey}_${rankKey}_$metric"

    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var entered by remember { mutableStateOf(false) }


    LaunchedEffect(mode, standKey, rankKey, metric) {
        // 本页第一次加载（从评分页进来）：用户此刻看的是骨架，直接建表即可
        val firstLoadOfPage = !entered
        if (firstLoadOfPage) {
            delay(240)
            entered = true
        }
        failed = false
        // 这个 tab 已经加载过 → 直接显示，无需 loading / 无骨架
        if (rendered.containsKey(tabKey)) {
            loading = false
            return@LaunchedEffect
        }
        loading = true

        val loaded: List<NbaRankTable> = when {
            mode == 0 -> {
                val all = repo.nbaStandings(standKey == "division")
                if (standKey == "division") all
                else all.filter {
                    it.title.startsWith(if (standKey == "east") "东部" else "西部")
                }.ifEmpty { all }
            }
            rankKey == "players" -> listOfNotNull(repo.nbaPlayerStats(metric))
            else -> listOfNotNull(repo.nbaTeamStats())
        }
        // 后台预热列宽（Paint 测量），界面组合时零测量
        if (loaded.isNotEmpty()) {
            val d = density.density
            withContext(kotlinx.coroutines.Dispatchers.Default) {
                loaded.forEach { t ->
                    val k = NbaColWidthCache.key(t)
                    if (NbaColWidthCache.get(k) == null) {
                        NbaColWidthCache.put(
                            k,
                            NbaColWidthMeasurer.computePx(t, 11f, 12f, d),
                        )
                    }
                }
            }
        }
        // 1.282：**切 tab 时把"建表"推迟到胶囊动画结束之后**。
        // 数据请求与列宽预热已经在后台完成，这里只推迟「把数据交给 UI 去组合」的时刻，
        // 于是动画期间主线程没有重活（掉帧被藏进骨架屏那段），切换手感就顺了。
        if (!firstLoadOfPage) delay(260)

        // 写入缓存：只保留「当前 + 上一次」两个 tab 的数据，控制内存
        rendered[tabKey] = loaded
        if (rendered.size > 2) {
            val keep = setOf(tabKey, lastTabKey)
            rendered.keys.filter { it !in keep }.forEach { rendered.remove(it) }
        }
        lastTabKey = tabKey
        loading = false
        if (loaded.isEmpty()) failed = true
    }


    FrostedHeaderLayout(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(zIndex)
            .graphicsLayer { translationX = (1f - progress.value) * size.width },
        header = {
            CenteredTopBar(title = title, onBack = { if (!closing) closing = true })
        },
    ) { topInset ->
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(top = topInset)
                .padding(horizontal = 16.dp),
        ) {
            // ---------- 固定头部 ----------
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Chip(text = "战绩排名", selected = mode == 0, onClick = { mode = 0 })
                Chip(text = "数据排名", selected = mode == 1, onClick = { mode = 1 })
            }
            Spacer(Modifier.height(8.dp))
            if (mode == 0) {
                SortBar(
                    sorts = listOf(
                        SortTab(0, "东部", "east"),
                        SortTab(1, "西部", "west"),
                        SortTab(2, "按赛区", "division"),
                    ),
                    selected = standKey,
                    hPad = 0.dp,
                    bottomPad = 0.dp,
                    onSelect = { standKey = it },
                )
            } else {
                SortBar(
                    sorts = listOf(
                        SortTab(0, "球员", "players"),
                        SortTab(1, "球队", "teams"),
                    ),
                    selected = rankKey,
                    hPad = 0.dp,
                    bottomPad = 0.dp,
                    onSelect = { rankKey = it },
                )
                if (rankKey == "players") {
                    Spacer(Modifier.height(8.dp))
                    val metricCenter = rememberChipAutoCenter()
                    val metricIdx = NBA_PLAYER_METRICS.indexOfFirst { it.key == metric }
                    ChipAutoCenterRow(
                        center = metricCenter,
                        selectedIndex = metricIdx,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        NBA_PLAYER_METRICS.forEachIndexed { i, m ->
                            ChipSlot(metricCenter, i) {
                                Chip(
                                    text = m.label,
                                    selected = metric == m.key,
                                    onClick = { metric = m.key },
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            // ---------- 表格区（骨架 / 失败 / 虚拟化表格） ----------
            Box(Modifier.fillMaxSize()) {
                when {
                    loading && !rendered.containsKey(tabKey) -> NbaTableSkeleton()
                    failed -> Column(
                        Modifier.fillMaxWidth().padding(top = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("加载失败", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "点击重试",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable {
                                    scope.launch {
                                        failed = false
                                        loading = true
                                        val retried = when {
                                            mode == 0 -> repo.nbaStandings(standKey == "division")
                                            rankKey == "players" -> listOfNotNull(repo.nbaPlayerStats(metric))
                                            else -> listOfNotNull(repo.nbaTeamStats())
                                        }
                                        rendered[tabKey] = retried
                                        loading = false
                                        if (retried.isEmpty()) failed = true
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    else -> {
                        // 已缓存的 tab 全部参与组合（保留其滚动/排序状态），
                        // 非当前的只跳过绘制 —— 于是切换是"同一帧换画面"，零构建成本。
                        rendered.forEach { (k, list) ->
                            val visible = k == tabKey
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .zIndex(if (visible) 1f else 0f)
                                    .drawWithContent { if (visible) drawContent() },
                            ) {
                                NbaTablesView(list, sortable = k.startsWith("1_"))
                            }
                        }
                    }
                }
            }
        }
    }
}

private val RANK_W: Dp = 34.dp
private val NAME_W: Dp = 96.dp
private val NUM_W: Dp = 62.dp
private val HEADER_H: Dp = 44.dp
private val ROW_H: Dp = 44.dp

/**
 * 1.277：**虚拟化表格**。
 *
 * - 外层**一个** `horizontalScroll` 包住整个 `LazyColumn`（横向只有一个手势消费方）；
 * - `LazyColumn` 只组合可见行（行数再多也不卡）；
 * - 每张表的表头用 `stickyHeader` 吸顶。
 */
@Composable
internal fun NbaTablesView(
    tables: List<NbaRankTable>,
    sortable: Boolean,
) {
    if (tables.isEmpty()) return
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val containerW = maxWidth
        val widthMap = remember(tables, containerW) {
            tables.map { t -> computeWidths(t, containerW, density.density) }
        }
        val totalW = widthMap.maxOfOrNull { ws -> ws.fold(0.dp) { a, d -> a + d } } ?: containerW
        // 1.284：加深一档、保持冷中性（既不泛黄，又能看清表格轮廓）
        val tableBg = if (!isAppDarkTheme()) androidx.compose.ui.graphics.Color(0xFFEEF0F3)
        else MaterialTheme.colorScheme.surfaceVariant

        // 1.280：**整页纵向滚动 + 单一横向滚动容器**。
        // 每张表各自一张卡片（圆角 + 独立间距），「按赛区」的 6 张表因此是分开的，
        // 而不是拼成一张大表。
        // 1.298：底部不再用 navigationBarsPadding（那会在导航条上方硬切一刀，
        // 看起来像导航条后面垫了一块不透明的色带）。改为把导航条高度算进
        // **内容底部留白**：表格可以滚到导航条下面，最后一行仍然清得开。
        val navBottom = androidx.compose.foundation.layout.WindowInsets.navigationBars
            .asPaddingValues().calculateBottomPadding()
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 20.dp + navBottom),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            tables.forEachIndexed { ti, table ->
                // 1.284：**每张表各自的横向滚动状态**。
                // 之前所有表共用一个 ScrollState → 滑动任意一张，其他表跟着动。
                val hScroll = remember(ti) { ScrollState(0) }
                val widths = widthMap[ti]
                val nameIdx = table.headers.indexOfFirst {
                    it == "队名" || it == "球员" || it == "球队"
                }.let { if (it >= 0) it else 1}

                var sortCol by remember(ti) { mutableIntStateOf(-1) }
                var sortDesc by remember(ti) { mutableStateOf(true) }
                val rows = remember(table.rows, sortCol, sortDesc) {
                    if (sortCol < 0) table.rows
                    else table.rows.sortedBy { row ->
                        val v = row.getOrNull(sortCol)?.let { NbaRankParser.numeric(it) }
                            ?: Double.NEGATIVE_INFINITY
                        if (sortDesc) -v else v
                    }
                }
                val arrowW = if (sortCol < 0) 0.dp else 12.dp

                Column {
                    // 标题：只有多表（按赛区）时才显示，东部/西部单表不显示
                    if (tables.size > 1 && table.title.isNotBlank()) {
                        Text(
                            table.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                        )
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(tableBg)
                            .horizontalScroll(hScroll),
                    ) {
                        Column {
                            Row(Modifier.height(HEADER_H), verticalAlignment = Alignment.CenterVertically) {
                                table.headers.forEachIndexed { i, h ->
                                    HeadCell(
                                        text = h,
                                        width = widths.getOrElse(i) { NUM_W } +
                                            if (i == sortCol) arrowW else 0.dp,
                                        alignStart = i == nameIdx,
                                        clickable = sortable && table.sortable && i != 0 && i != nameIdx,
                                        active = sortable && sortCol == i,
                                        desc = sortDesc,
                                    ) {
                                        if (sortCol == i) sortDesc = !sortDesc else {
                                            sortCol = i
                                            sortDesc = true
                                        }
                                    }
                                }
                            }
                            rows.forEachIndexed { ri, row ->
                                Row(
                                    Modifier.height(ROW_H),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    row.forEachIndexed { i, c ->
                                        BodyCell(
                                            text = c,
                                            width = widths.getOrElse(i) { NUM_W } +
                                                if (i == sortCol && sortable && table.sortable) arrowW else 0.dp,
                                            alignStart = i == nameIdx,
                                            bold = i == 0 || i == nameIdx,
                                        )
                                    }
                                }
                                if (ri == rows.lastIndex) Spacer(Modifier.height(2.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 列宽：内容基础宽度（后台已预热）→ 加内边距 → 铺满均分 */
private fun computeWidths(table: NbaRankTable, containerW: Dp, density: Float): List<Dp> {
    if (table.headers.isEmpty()) return emptyList()
    val nameIdx = table.headers.indexOfFirst {
        it == "队名" || it == "球员" || it == "球队"
    }.let { if (it >= 0) it else 1 }
    val basePx = NbaColWidthCache.get(NbaColWidthCache.key(table))
        ?: NbaColWidthMeasurer.computePx(table, 11f, 12f, density).also {
            NbaColWidthCache.put(NbaColWidthCache.key(table), it)
        }
    val base = table.headers.indices.map { i ->
        val needDp = (basePx.getOrElse(i) { 0f } / density).dp
        (needDp + 14.dp).coerceIn(
            if (i == 0) 30.dp else if (i == nameIdx) 56.dp else 44.dp,
            if (i == nameIdx) 128.dp else 96.dp,
        )
    }
    val total = base.fold(0.dp) { acc, d -> acc + d }
    return if (containerW > total && base.isNotEmpty()) {
        val extra = (containerW - total) / base.size
        base.map { it + extra }
    } else {
        base
    }
}

@Composable
private fun HeadCell(
    text: String,
    width: Dp,
    alignStart: Boolean = false,
    clickable: Boolean = false,
    active: Boolean = false,
    desc: Boolean = true,
    onClick: () -> Unit = {},
) {
    val color = if (active) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant
    BasicText(
        text = if (active) text + if (desc) " ↓" else " ↑" else text,
        style = TextStyle(
            fontSize = 11.sp,
            color = color,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = if (alignStart) TextAlign.Start else TextAlign.Center,
        ),
        maxLines = 1,
        modifier = Modifier
            .width(width)
            .then(if (clickable) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 2.dp, vertical = 6.dp),
    )
}

@Composable
private fun BodyCell(
    text: String,
    width: Dp,
    alignStart: Boolean = false,
    bold: Boolean = false,
) {
    BasicText(
        text = text,
        style = TextStyle(
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = if (bold) FontWeight.Medium else FontWeight.Normal,
            textAlign = if (alignStart) TextAlign.Start else TextAlign.Center,
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(width).padding(horizontal = 2.dp),
    )
}

/**
 * 表格式骨架：与真实表格同底色、同行高（替换时不跳动），
 * 表头不再整行加深（避免"屏幕花一下"的观感）。
 */
@Composable
internal fun NbaTableSkeleton(rows: Int = 10) {
    val c = MaterialTheme.colorScheme.onSurface
    Column(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (!isAppDarkTheme()) androidx.compose.ui.graphics.Color(0xFFEEF0F3)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                ),
        ) {
            Row(
                Modifier.fillMaxWidth().height(HEADER_H).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Bar(44.dp, 13.dp, c.copy(alpha = 0.10f))
                Spacer(Modifier.weight(1f))
                Bar(52.dp, 13.dp, c.copy(alpha = 0.10f))
            }
            repeat(rows) {
                Row(
                    Modifier.fillMaxWidth().height(ROW_H).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Bar(64.dp, 13.dp, c.copy(alpha = 0.08f))
                    Spacer(Modifier.weight(1f))
                    Bar(34.dp, 13.dp, c.copy(alpha = 0.06f))
                }
            }
        }
    }
}

@Composable
private fun Bar(w: Dp, h: Dp, color: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier
            .width(w)
            .height(h)
            .clip(RoundedCornerShape(h / 2))
            .background(color),
    )
}