package com.java.myapplication.ui.pages

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.java.myapplication.data.HupuMatchDay
import com.java.myapplication.data.HupuPrefs
import com.java.myapplication.ui.components.Chip
import com.java.myapplication.ui.components.HupuIcons
import com.java.myapplication.ui.components.SecondaryPage
import com.java.myapplication.ui.components.SheetGrabber
import com.java.myapplication.ui.components.rememberSheetSideInset
import com.java.myapplication.ui.components.sheetTopCornerShape
import com.java.myapplication.ui.glass.LiquidButton
import com.java.myapplication.ui.glass.buttonBorder
import com.java.myapplication.ui.glass.buttonFill
import com.java.myapplication.ui.theme.isAppDarkTheme

/**
 * 1.243：赛事频道（NBA / CBA / LOL / KPL…）的**队伍筛选**。
 *
 * 与「热门赛事」的项目筛选不同：热门赛事是多个项目混装，筛的是**项目**；
 * 各赛事频道里只有这一项运动，能筛的就是**参赛队伍**（grouped 按 memberId）。
 *
 * 数据完全来自已加载的赛程（[HupuMatchDay]），无需新接口：
 *  · 队伍清单 = 当前账号下所有比赛里出现过的队；已选中但今天没比赛的队也会保留（count=0）；
 *  · 过滤后为空日期块会被丢掉，避免「定位到最近比赛日」落到空日子上。
 */
internal data class TeamFilterItem(
    val id: String,
    val name: String,
    /** 该队当前出现的场次（0 = 今天没有它的比赛，但仍在筛选集里） */
    val count: Int = 0,
)

internal object TeamFilter {

    /** 持久化编码："memberId\tname" */
    fun encode(id: String, name: String): String = "$id\t$name"

    fun decode(raw: String): Pair<String, String>? {
        val i = raw.indexOf('\t')
        if (i <= 0) return null
        val id = raw.substring(0, i)
        val name = raw.substring(i + 1)
        if (id.isBlank()) return null
        return id to name
    }

    /** 已选中的 memberId 集合 */
    fun selectedIds(selected: Set<String>): Set<String> =
        selected.mapNotNull { decode(it)?.first }.toSet()

    /**
     * 面板数据源：当前出现的队伍（带场次）+ 已选中但今天没出现的队伍（count=0）。
     * 排序：场次多的在前，同场次按名称。
     */
    fun items(days: List<HupuMatchDay>, selected: Set<String>): List<TeamFilterItem> {
        val counts = LinkedHashMap<String, Pair<String, Int>>()
        days.forEach { d ->
            d.matches.forEach { m ->
                listOfNotNull(m.home, m.away).forEach { t ->
                    if (t.memberId.isBlank() || t.name.isBlank()) return@forEach
                    val cur = counts[t.memberId]
                    counts[t.memberId] = t.name to ((cur?.second ?: 0) + 1)
                }
            }
        }
        val out = counts.map { (id, v) -> TeamFilterItem(id, v.first, v.second) }.toMutableList()
        // 已选中但今天没有比赛 → 也要列出来，否则它没有任何入口能取消
        selected.forEach { raw ->
            val (id, name) = decode(raw) ?: return@forEach
            if (counts.keys.none { it == id }) out += TeamFilterItem(id, name, 0)
        }
        return out.sortedWith(compareByDescending<TeamFilterItem> { it.count }.thenBy { it.name })
    }

    /**
     * 1.249：**反选**（只翻转当前可见项，未显示/被搜索过滤掉的项保持原状）。
     * 纯函数，便于单测。
     */
    fun invert(selected: Set<String>, visible: Set<String>): Set<String> =
        (selected - visible) + (visible - selected)

    /** 过滤：只保留「有任一选中队伍参赛」的比赛；变空的日期块整块丢弃 */
    fun filterDays(days: List<HupuMatchDay>, selected: Set<String>): List<HupuMatchDay> {
        val ids = selectedIds(selected)
        if (ids.isEmpty()) return days
        return days.mapNotNull { d ->
            val keep = d.matches.filter { m ->
                (m.home?.memberId ?: "") in ids || (m.away?.memberId ?: "") in ids
            }
            if (keep.isEmpty()) null else d.copy(matches = keep)
        }
    }
}

/**
 * 1.243：队伍筛选半屏面板（chrome 与楼中楼 / 热门筛选面板同源）。
 *
 * 交互取舍：
 *  · **点即生效**（不用「确定」），方便连续勾选看列表变化；底部「完成」只负责关闭；
 *  · 搜索框只过滤**面板显示**，不改变已选集合；
 *  · 右上角「全选 / 全不选」按可见项操作；「清除筛选」= 清空已选（回到显示全部）。
 */
@Composable
internal fun TeamFilterSheet(
    title: String,
    items: List<TeamFilterItem>,
    selected: Set<String>,
    totalMatches: Int,
    onToggle: (TeamFilterItem, String) -> Unit,
    /** 1.249：队伍段「反选」——回传反选后的完整集合（只翻转当前可见项） */
    onInvertTeams: (Set<String>) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    /**
     * 1.246/1.247：**热门赛事**还会传入「项目」维度（null = 只有队伍，单赛事频道就是这种）。
     * 两个维度是叠加关系：先按项目过滤，再按队伍过滤。
     *
     * 1.247：项目也统一成「**选中 = 只显示选中项，空 = 不筛选**」——与队伍一致。
     * （旧版存的是"被排除的项目"，读取时由调用方换算成"选中集合"，存储格式不变，
     * 老用户的筛选结果原样保留。）
     */
    categories: List<Pair<String, Int>>? = null,
    selectedCategories: Set<String> = emptySet(),
    onToggleCategory: (String) -> Unit = {},
    /** 1.249：项目段「反选」——回传反选后的完整集合（只翻转当前可见项） */
    onInvertCategories: (Set<String>) -> Unit = {},
) {
    DisposableEffect(Unit) {
        SecondaryPage.enter()
        onDispose { SecondaryPage.exit() }
    }
    BackHandler { onDismiss() }

    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(220)) }
    val scrimSource = remember { MutableInteractionSource() }
    val panelSource = remember { MutableInteractionSource() }
    val side = rememberSheetSideInset(depth = 25.dp)

    // 0 = 项目；1 = 队伍（没有项目维度时直接落在队伍）
    var segment by remember { mutableStateOf(if (categories == null) 1 else 0) }
    var query by remember(segment) { mutableStateOf("") }

    val catShown = remember(categories, query) {
        val q = query.trim()
        categories.orEmpty().filter { q.isEmpty() || it.first.contains(q, ignoreCase = true) }
    }
    val teamShown = remember(items, query) {
        val q = query.trim()
        if (q.isEmpty()) items else items.filter { it.name.contains(q, ignoreCase = true) }
    }
    val selectedIds = remember(selected) { TeamFilter.selectedIds(selected) }
    val nothingFiltered = selectedCategories.isEmpty() && selectedIds.isEmpty()

    Box(
        Modifier
            .fillMaxSize()
            .zIndex(4f)
            .background(Color.Black.copy(alpha = 0.25f * progress.value))
            .clickable(indication = null, interactionSource = scrimSource) { onDismiss() },
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .graphicsLayer { translationY = (1f - progress.value) * size.height }
                .clip(sheetTopCornerShape())
                .background(MaterialTheme.colorScheme.background)
                .clickable(indication = null, interactionSource = panelSource) {}
                .navigationBarsPadding(),
        ) {
            Box(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                contentAlignment = Alignment.Center,
            ) { SheetGrabber() }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = maxOf(16.dp, side),
                        end = maxOf(12.dp, side),
                        top = 10.dp,
                        bottom = 6.dp,
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                // 1.249：改为「反选」——「全选/全不选」的结果都等于"不筛选"，
                // 与底部「清除筛选」重复；反选在"部分选中"时才有真实意义，
                // 且只翻转**当前可见项**（搜索过滤后不会误伤看不见的项）。
                val invertEnabled = if (segment == 0) catShown.isNotEmpty() else teamShown.isNotEmpty()
                Text(
                    "反选",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .alpha(if (invertEnabled) 1f else 0.4f)
                        .clip(RoundedCornerShape(50))
                        .clickable {
                            if (!invertEnabled) return@clickable
                            if (segment == 0) {
                                val shown = catShown.map { it.first }.toSet()
                                onInvertCategories(TeamFilter.invert(selectedCategories, shown))
                            } else {
                                val shownIds = teamShown.map { it.id }.toSet()
                                // 保留：不可见项 + 可见项里"原本未选"的那些（以 raw 形式加入）
                                val keep = selected.filterNot { TeamFilter.decode(it)?.first in shownIds }
                                val add = teamShown
                                    .filter { it.id !in selectedIds }
                                    .map { TeamFilter.encode(it.id, it.name) }
                                onInvertTeams((keep + add).toSet())
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 两个维度都有值时，用分段切换（热门赛事）
                if (categories != null) {
                    // 1.250：与「信息流设置 → 关键词过滤 / 黑名单」顶部完全同款（Chip）
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Chip(text = "项目", selected = segment == 0, onClick = { segment = 0 })
                        Chip(text = "队伍 / 选手", selected = segment == 1, onClick = { segment = 1 })
                    }
                }
                Text(
                    when {
                        segment == 0 && selectedCategories.isEmpty() ->
                            "当前显示全部 $totalMatches 场比赛 · 勾选后只看所选项目"
                        segment == 0 -> {
                            val allCat = categories != null && categories.isNotEmpty() &&
                                selectedCategories.size >= categories.size
                            (if (allCat) "已选全部项目" else "已选 ${selectedCategories.size} 个项目") +
                                if (selectedIds.isEmpty()) " · 共 $totalMatches 场比赛"
                                else " · 另选了 ${selectedIds.size} 支队伍"
                        }
                        selectedIds.isEmpty() ->
                            "当前显示全部 $totalMatches 场比赛 · 勾选后只看所选队伍/选手"
                        else -> "已选 ${selectedIds.size} 支队伍 · 共 ${teamShown.size} 个可选"
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // 搜索框（两个分段共用，切段时清空）
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            HupuIcons.SearchOutlined,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            // 1.309：**光标偏上**的修复。
                            // Android 字体自带上下留白（includeFontPadding），BasicTextField 在
                            // 固定高度的胶囊里按基线排版时，这块留白会把光标顶偏。
                            // 这里补齐垂直度量：lineHeight 与字号成比例，行高居中，去掉字体留白。
                            textStyle = TextStyle(
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                                    includeFontPadding = false,
                                ),
                                lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                                    alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                                    trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.None,
                                ),
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f),
                            decorationBox = { inner ->
                                if (query.isEmpty()) {
                                    Text(
                                        if (segment == 0) "搜索项目" else "搜索队伍 / 选手",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                    )
                                }
                                inner()
                            },
                        )
                        if (query.isNotEmpty()) {
                            Text(
                                "✕",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable { query = "" }
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                // 列表
                val emptyNow = if (segment == 0) catShown.isEmpty() else teamShown.isEmpty()
                if (emptyNow) {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 28.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (segment == 0) "没有匹配的项目" else "没有匹配的队伍",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 340.dp),
                        contentPadding = PaddingValues(vertical = 2.dp),
                    ) {
                        if (segment == 0) {
                            items(catShown, key = { it.first }) { (name, count) ->
                                // 1.247：与队伍一致 —— 选中 = 只显示该项
                                val on = name in selectedCategories
                                FilterRow(
                                    text = name,
                                    count = count,
                                    on = on,
                                ) { onToggleCategory(name) }
                            }
                        } else {
                            items(teamShown, key = { it.id }) { it ->
                                FilterRow(
                                    text = it.name,
                                    count = it.count,
                                    on = it.id in selectedIds,
                                ) { onToggle(it, TeamFilter.encode(it.id, it.name)) }
                            }
                        }
                    }
                }
                // 底部：清除筛选 + 完成
                val dark = isAppDarkTheme()
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    LiquidButton(
                        onClick = { if (!nothingFiltered) onClear() },
                        fill = buttonFill(dark),
                        border = buttonBorder(dark),
                        modifier = Modifier
                            .weight(1f)
                            .alpha(if (nothingFiltered) 0.45f else 1f),
                        shape = RoundedCornerShape(20.dp),
                        height = 44.dp,
                        isInteractive = !nothingFiltered,
                    ) {
                        Text(
                            "清除筛选",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (nothingFiltered) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.primary,
                        )
                    }
                    LiquidButton(
                        onClick = onDismiss,
                        fill = buttonFill(dark),
                        border = buttonBorder(dark),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp),
                        height = 44.dp,
                    ) {
                        Text(
                            "完成",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

/** 1.246：筛选项的一行（名称 + 场次 + 自绘勾选） */
@Composable
private fun FilterRow(
    text: String,
    count: Int,
    on: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (count > 0) {
            Text(
                "$count 场",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
        }
        SelectDot(on = on)
    }
}

/**
 * 1.244：筛选行的勾选指示器。
 *
 * 不用 `✓` 字符 —— 各字体里它的粗细/居中都不一致，在 18dp 里显得粗糙。这里自绘：
 *  · 未选：1.5dp 描边的空心圆环（无填充）；
 *  · 选中：主题色实心圆 + **圆角对勾**（`StrokeCap/Join = Round`），并带一个轻弹入动画。
 */
@Composable
private fun SelectDot(on: Boolean) {
    val primary = MaterialTheme.colorScheme.primary
    val ring = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.20f)
    val scale by animateFloatAsState(
        targetValue = if (on) 1f else 0.92f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "selectDot",
    )
    Canvas(
        Modifier
            .size(20.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        val d = size.minDimension
        if (on) {
            drawCircle(color = primary)
            val p = Path().apply {
                moveTo(d * 0.28f, d * 0.52f)
                lineTo(d * 0.44f, d * 0.68f)
                lineTo(d * 0.73f, d * 0.34f)
            }
            drawPath(
                path = p,
                color = Color.White,
                style = Stroke(
                    width = d * 0.11f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        } else {
            val stroke = 1.5.dp.toPx()
            drawCircle(
                color = ring,
                radius = d / 2f - stroke / 2f,
                style = Stroke(width = stroke),
            )
        }
    }
}
