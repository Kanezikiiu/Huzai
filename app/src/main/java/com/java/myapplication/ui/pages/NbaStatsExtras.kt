package com.java.myapplication.ui.pages

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.java.myapplication.data.NbaPlayerRow
import com.java.myapplication.data.NbaTeamStats
import com.java.myapplication.ui.components.HupuIcons
import com.java.myapplication.ui.components.HuzaiHalfSheet

// ===========================================================================
// 1.224d：球员 PK / 本场数据帝
//
// 两块内容**只从上方球员数据表派生**（同一份 boxscore 解析结果），因此：
//   · 零额外网络请求、离线可用、与表格里的数值必然一致；
//   · 不使用「虎扑评分」与球员头像 —— 数据统计表里本就没有这两项数据。
// ===========================================================================

// 占优侧统一用**主题色**（不再左右分红蓝，避免一屏两种强调色显得花）。
// 实心块底色取 colorScheme.primary、文字取 onPrimary —— 明暗主题与各套配色都自动配对可读。

// ---------------------------------------------------------------------------
// 数据派生（纯函数）
// ---------------------------------------------------------------------------

/** "12" / "+15" / "-3" → 数值；解析失败 → null */
internal fun nbaNum(s: String): Double? = s.trim().replace("+", "").toDoubleOrNull()

/** "5-12" → (命中, 出手)；解析失败 → null */
internal fun nbaMadeAttempted(s: String): Pair<Int, Int>? {
    val parts = s.trim().split('-')
    if (parts.size != 2) return null
    val made = parts[0].trim().toIntOrNull() ?: return null
    val att = parts[1].trim().toIntOrNull() ?: return null
    return made to att
}

/** 投篮类命中率（0..100）；出手为 0 或格式不符 → null */
internal fun nbaShootingPct(s: String): Double? {
    val (made, att) = nbaMadeAttempted(s) ?: return null
    return if (att <= 0) null else made * 100.0 / att
}

/** 一支球队的全部球员行（首发在前、替补在后） */
internal fun nbaAllRows(stats: NbaTeamStats?): List<NbaPlayerRow> =
    stats?.sections?.flatMap { it.rows }.orEmpty()

/** 表头列名（各分组一致） */
internal fun nbaColumns(stats: NbaTeamStats?): List<String> =
    stats?.sections?.firstOrNull()?.columns.orEmpty()

/** 取某球员行中「某一列」的显示文本（按表头名定位下标，避免硬编码列序） */
internal fun nbaCellOf(row: NbaPlayerRow, columns: List<String>, col: String): String {
    val i = columns.indexOf(col)
    return if (i < 0) "" else row.values.getOrElse(i) { "" }
}

/** 得分王（并列取靠前者）；无数据 → "" */
internal fun nbaTopScorer(stats: NbaTeamStats?): String {
    val cols = nbaColumns(stats)
    return nbaAllRows(stats)
        .maxByOrNull { nbaCellOf(it, cols, "得分").toDoubleOrNull() ?: Double.NEGATIVE_INFINITY }
        ?.name.orEmpty()
}

/**
 * 对比项定义。
 *
 * @param label   展示名（可与列名不同，如列名 "3分" → 展示 "三分"）
 * @param col     表头列名（用于在本场表头里定位下标）
 * @param score   比大小的数值；返回 null 表示该项不参与胜负判定
 * @param display 单元格展示文本（默认原样；如「三分命中」只显示命中数）
 */
internal data class NbaStatSpec(
    val label: String,
    val col: String,
    val score: (String) -> Double?,
    val display: (String) -> String = { it },
    /** 柱状条口径（见 [NbaBarMode]） */
    val bar: NbaBarMode = NbaBarMode.SHARE,
)

/**
 * 柱状条口径（左右各占半幅、各自一根条，中间留缝）。
 *
 * · [SHARE]：两值**相加**，各侧显示**自己占二者之和的份额**；
 * · [PCT]：各侧**独立**显示「自己命中数 ÷ 自己出手数」，与对方无关。
 */
internal enum class NbaBarMode { SHARE, PCT }

/**
 * 算出左右两根条的填充比例（0..1，1 = 吃满本侧半条）。
 *
 * SHARE：`自己 /（左 + 右）` —— 两侧之和恒为 1，于是两根条的视觉长度比 = 两数值之比
 * （16 : 21 → 43% : 57%），既不封顶也不夸大。
 * PCT：直接取各自命中率（0..100 → 0..1），互不影响。
 *
 * 两侧相加为 0（两人都是 0，或两侧都拿不到值）→ **两根条都不画**：
 * 没有任何贡献时还各画半条会误导成「并列且占满」，所以统一给 0。
 * 真正并列（5 : 5）仍然各占一半。
 */
internal fun nbaBarFractions(mode: NbaBarMode, lv: Double?, rv: Double?): Pair<Float, Float> =
    when (mode) {
        NbaBarMode.PCT -> {
            val l = ((lv ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
            val r = ((rv ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
            l to r
        }
        NbaBarMode.SHARE -> {
            val l0 = (lv ?: 0.0).coerceAtLeast(0.0)
            val r0 = (rv ?: 0.0).coerceAtLeast(0.0)
            val sum = l0 + r0
            if (sum <= 0.0) 0f to 0f
            else (l0 / sum).toFloat() to (r0 / sum).toFloat()
        }
    }

/** 球员 PK 的对比项（顺序即展示顺序） */
internal val NbaPkSpecs: List<NbaStatSpec> = listOf(
    NbaStatSpec("得分", "得分", ::nbaNum),
    NbaStatSpec("篮板", "篮板", ::nbaNum),
    NbaStatSpec("助攻", "助攻", ::nbaNum),
    NbaStatSpec("抢断", "抢断", ::nbaNum),
    NbaStatSpec("封盖", "封盖", ::nbaNum),
    NbaStatSpec("失误", "失误", ::nbaNum),
    // 投篮类：原样显示 "5-8"，柱长与胜负都按**命中率**（各自命中数 ÷ 各自出手数）
    NbaStatSpec("投篮", "投篮", ::nbaShootingPct, bar = NbaBarMode.PCT),
    NbaStatSpec("三分", "3分", ::nbaShootingPct, bar = NbaBarMode.PCT),
    NbaStatSpec("罚球", "罚球", ::nbaShootingPct, bar = NbaBarMode.PCT),
)

/**
 * 1.224h：球队统计的对比项（顺序即展示顺序）—— 对**球队合计行**做对比，渲染与球员 PK 完全同款。
 *
 * 数据取自表尾汇总行「统计」（见 [NbaTeamStats.summaries]）：计数项直接取，
 * 命中率项用同一格的 `26-59` 现算，所以不依赖「命中率」那张汇总行。
 */
internal val NbaTeamSpecs: List<NbaStatSpec> = listOf(
    NbaStatSpec("得分", "得分", ::nbaNum),
    NbaStatSpec("篮板", "篮板", ::nbaNum),
    NbaStatSpec("助攻", "助攻", ::nbaNum),
    NbaStatSpec("投篮命中率", "投篮", ::nbaShootingPct, ::nbaPctDisplay, bar = NbaBarMode.PCT),
    NbaStatSpec("三分命中率", "3分", ::nbaShootingPct, ::nbaPctDisplay, bar = NbaBarMode.PCT),
    NbaStatSpec("罚球命中率", "罚球", ::nbaShootingPct, ::nbaPctDisplay, bar = NbaBarMode.PCT),
    NbaStatSpec("抢断", "抢断", ::nbaNum),
    NbaStatSpec("封盖", "封盖", ::nbaNum),
    NbaStatSpec("失误", "失误", ::nbaNum),
    NbaStatSpec("犯规", "犯规", ::nbaNum),
)

/** `"26-59"` → `"44.1%(26-59)"`（命中率 + 原始命中/出手）；解析失败或出手为 0 → 原样返回 */
internal fun nbaPctDisplay(s: String): String {
    val (made, att) = nbaMadeAttempted(s) ?: return s
    if (att <= 0) return s
    return String.format(java.util.Locale.US, "%.1f%%(%d-%d)", made * 100.0 / att, made, att)
}

/** 球队合计行（表尾「统计」）；缺失 → null（此时不展示球队统计模块） */
internal fun nbaTeamTotalsRow(stats: NbaTeamStats?): NbaPlayerRow? =
    stats?.summaries?.firstOrNull { it.name == "统计" }

/** 本场数据帝的统计项（两队各取一项的头名） */
internal val NbaKingSpecs: List<NbaStatSpec> = listOf(
    NbaStatSpec("得分", "得分", ::nbaNum),
    NbaStatSpec("篮板", "篮板", ::nbaNum),
    NbaStatSpec("助攻", "助攻", ::nbaNum),
    NbaStatSpec("抢断", "抢断", ::nbaNum),
    NbaStatSpec("封盖", "封盖", ::nbaNum),
    NbaStatSpec(
        "三分", "3分",
        { nbaMadeAttempted(it)?.first?.toDouble() },
        { nbaMadeAttempted(it)?.first?.toString() ?: it },
    ),
)

/** 某项统计在队内的头名；(球员名, 展示值, 数值)；无数据 → null */
internal fun nbaStatLeader(stats: NbaTeamStats?, spec: NbaStatSpec): Triple<String, String, Double>? {
    val cols = nbaColumns(stats)
    val rows = nbaAllRows(stats)
    if (rows.isEmpty()) return null
    var best: NbaPlayerRow? = null
    var bestScore = Double.NEGATIVE_INFINITY
    rows.forEach { r ->
        val sc = spec.score(nbaCellOf(r, cols, spec.col)) ?: return@forEach
        if (sc > bestScore) {
            bestScore = sc
            best = r
        }
    }
    val b = best ?: return null
    return Triple(b.name, spec.display(nbaCellOf(b, cols, spec.col)), bestScore)
}

// ---------------------------------------------------------------------------
// 球员 PK
// ---------------------------------------------------------------------------

/**
 * 球员 PK：两队代表球员逐项对比。
 *
 * · 默认是两队各自的**得分王**，点姓名旁的 ⇄ 可换人；
 * · 每项一根通条、两根柱各自从中间向两侧延伸：占优一侧用**主题色**，落后一侧灰；
 * · 柱长口径见 [NbaBarMode]（计数类按「两值相加后的占比」，投篮类按各自命中率）。
 */
@Composable
internal fun NbaPlayerPkCard(
    away: NbaTeamStats,
    home: NbaTeamStats,
    awayName: String,
    homeName: String,
    onPickAway: () -> Unit,
    onPickHome: () -> Unit,
) {
    val colsA = nbaColumns(away)
    val colsH = nbaColumns(home)
    val rowsA = nbaAllRows(away)
    val rowsH = nbaAllRows(home)
    val left = rowsA.firstOrNull { it.name == awayName } ?: rowsA.firstOrNull()
    val right = rowsH.firstOrNull { it.name == homeName } ?: rowsH.firstOrNull()
    if (left == null || right == null) return

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        NbaSectionTitle("球员 PK")
        Row(verticalAlignment = Alignment.Top) {
            PkPlayerHeader(left.name, away.teamName, Modifier.weight(1f), Alignment.Start, onPickAway)
            PkPlayerHeader(right.name, home.teamName, Modifier.weight(1f), Alignment.End, onPickHome)
        }
        NbaPkSpecs.forEach { spec ->
            val lt = nbaCellOf(left, colsA, spec.col)
            val rt = nbaCellOf(right, colsH, spec.col)
            PkCompareRow(spec, lt, spec.score(lt), rt, spec.score(rt))
        }
    }
}

@Composable
private fun PkPlayerHeader(
    name: String,
    team: String,
    modifier: Modifier,
    align: Alignment.Horizontal,
    onPick: () -> Unit,
) {
    val atStart = align == Alignment.Start
    Column(modifier, horizontalAlignment = align) {
        Text(
            name,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            Modifier
                .padding(top = 2.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onPick)
                // 1.224f（真机反馈）：**不加水平内边距** —— 队名要和上方球员名贴同一条边（左/右）。
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 1.224f（真机反馈）：切换按钮永远朝**内侧**、队名贴**外侧**：
            // 左侧 = [队名 · 按钮]，右侧 = [按钮 · 队名] —— 这样两侧队名分别与两侧球员名对齐。
            if (!atStart) {
                Icon(
                    HupuIcons.SwapHoriz,
                    contentDescription = "换人",
                    modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(3.dp))
            }
            Text(
                team,
                fontSize = 11.sp,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (atStart) {
                Spacer(Modifier.width(3.dp))
                Icon(
                    HupuIcons.SwapHoriz,
                    contentDescription = "换人",
                    modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * 一项对比：数值 + 左右两根**独立**的柱状条（中间留缝，不再连成一根）。
 *
 * 柱长口径见 [NbaBarMode]：
 * · SHARE（得分/篮板/助攻/抢断/封盖/失误）——两值相加，各侧画自己那份占比；
 * · PCT（投篮/三分/罚球）——各侧独立显示「自己命中数 ÷ 自己出手数」。
 */
@Composable
private fun PkCompareRow(spec: NbaStatSpec, lText: String, lv: Double?, rText: String, rv: Double?) {
    val leftWins = lv != null && (rv == null || lv > rv)
    val rightWins = rv != null && (lv == null || rv > lv)
    val (lFill, rFill) = nbaBarFractions(spec.bar, lv, rv)
    val shape = RoundedCornerShape(4.dp)
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    val loseColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
    // 占优侧统一主题色：左右不再分色，整块卡片只剩「主题色 + 灰」两种层次
    val accent = MaterialTheme.colorScheme.primary

    // 1.224f（真机反馈）：柱长变化时补上生长/伸缩动画（沿用评分分布条那套缓动）——此前是硬切，很生硬。
    // 也顺带让首次出现时从 0 撑开（Animatable 初值为 0）。
    val lAnim = remember { Animatable(0f) }
    val rAnim = remember { Animatable(0f) }
    LaunchedEffect(lFill) { lAnim.animateTo(lFill, tween(500, easing = FastOutSlowInEasing)) }
    LaunchedEffect(rFill) { rAnim.animateTo(rFill, tween(500, easing = FastOutSlowInEasing)) }


    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                lText.ifBlank { "-" },
                modifier = Modifier.weight(1f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                color = if (leftWins) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                spec.label,
                fontSize = 12.sp,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                rText.ifBlank { "-" },
                modifier = Modifier.weight(1f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.End,
                color = if (rightWins) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // 一根通条（整条统一圆角，中间无接缝），两根条各自**从中间向自己那一侧延伸**：
        // 左条贴中线向左长、右条贴中线向右长 —— 中线即分界，
        // 因此两根条的长度比可以直接裸眼比对。
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(shape)
                .background(track),
        ) {
            Row(Modifier.fillMaxWidth().fillMaxHeight()) {
                // 左半：条贴中线、向左延伸
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    Box(
                        Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxWidth(lAnim.value)
                            .fillMaxHeight()
                            // 1.224f（真机反馈）：**头（靠外一端）圆角**，与整条两端同半径；
                            // 靠中线一端保持直角 —— 两半并拢才没有缝隙。
                            .clip(RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp))
                            .background(if (leftWins) accent else loseColor),
                    )
                }
                // 右半：条贴中线、向右延伸
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    Box(
                        Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxWidth(rAnim.value)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                            .background(if (rightWins) accent else loseColor),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 本场数据帝
// ---------------------------------------------------------------------------

/** 本场数据帝：每项统计各取两队队内头名并列 */
@Composable
internal fun NbaStatKingsCard(away: NbaTeamStats, home: NbaTeamStats) {
    val rows = NbaKingSpecs.map { spec ->
        Triple(spec.label, nbaStatLeader(away, spec), nbaStatLeader(home, spec))
    }
    if (rows.all { it.second == null || it.third == null }) return

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NbaSectionTitle("本场数据帝")
        rows.forEach { (label, l, r) ->
            if (l != null && r != null) KingRow(label, l.first, l.second, r.first, r.second)
        }
    }
}

/**
 * 数据帝网格常量：左右数值块与中间名称都取**固定宽度**，保证逐行严格对齐；
 * 由于左右两块等宽，中间名称天然落在整行的水平中心。
 */
private val KingChipW = 50.dp
private val KingChipH = 26.dp
private val KingLabelW = 56.dp

@Composable
private fun KingRow(label: String, lName: String, lVal: String, rName: String, rVal: String) {
    val ls = lVal.toDoubleOrNull()
    val rs = rVal.toDoubleOrNull()
    val leftWins = ls != null && (rs == null || ls > rs)
    val rightWins = rs != null && (ls == null || rs > ls)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            lName,
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
        )
        KingChip(lVal, leftWins)
        Text(
            label,
            modifier = Modifier.width(KingLabelW),
            fontSize = 12.sp,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        KingChip(rVal, rightWins)
        Text(
            rName,
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** 数据帝的数值小块：**等宽等高**（对齐用），文字居中；占优一侧主题色实心，另一侧浅底 */
@Composable
private fun KingChip(text: String, highlight: Boolean) {
    Box(
        Modifier
            .width(KingChipW)
            .height(KingChipH)
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (highlight) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
            color = if (highlight) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ---------------------------------------------------------------------------
// 球队统计
// ---------------------------------------------------------------------------

/**
 * 1.224h：球队统计 —— 两队**球队合计**逐项对比（球员 PK 的球队版）。
 *
 * 复用 [PkCompareRow]：同一套「从中间向两侧延伸」的柱状条、同一套生长动画、同一套主题色口径。
 * 数据取自表尾汇总行「统计」；两队任一缺该行 → 整块不展示（宁可不显示，也不显示半份）。
 */
@Composable
internal fun NbaTeamStatsCard(
    away: NbaTeamStats,
    home: NbaTeamStats,
    awayName: String,
    homeName: String,
) {
    val lTotal = nbaTeamTotalsRow(away) ?: return
    val rTotal = nbaTeamTotalsRow(home) ?: return
    val colsA = nbaColumns(away)
    val colsH = nbaColumns(home)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        NbaSectionTitle("球队统计")
        // 表头：左客队 · VS · 右主队（与球员 PK 的左右分侧一致）
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                awayName.ifBlank { "客队" },
                modifier = Modifier.weight(1f),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "VS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Text(
                homeName.ifBlank { "主队" },
                modifier = Modifier.weight(1f),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        NbaTeamSpecs.forEach { spec ->
            val lt = nbaCellOf(lTotal, colsA, spec.col)
            val rt = nbaCellOf(rTotal, colsH, spec.col)
            // 1.224i：命中率项要用 spec.display 转成「44.1%(26-59)」——PkCompareRow 只渲染传入的文本，
            // 直接把原始 "26-59" 传进去就会没有百分比。
            PkCompareRow(spec, spec.display(lt), spec.score(lt), spec.display(rt), spec.score(rt))
        }
    }
}

// ---------------------------------------------------------------------------
// 换人弹层
// ---------------------------------------------------------------------------

/** 卡片小标题（带一段主色短下划线，与截图的分节感对齐） */
@Composable
private fun NbaSectionTitle(text: String) {
    Column {
        Text(
            text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .width(22.dp)
                .height(2.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

/** 换人：列出该队全部球员（按得分从高到低） */
@Composable
internal fun NbaPlayerPickerSheet(
    visible: Boolean,
    title: String,
    stats: NbaTeamStats,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val cols = nbaColumns(stats)
    val rows = nbaAllRows(stats)
        .sortedByDescending { nbaCellOf(it, cols, "得分").toDoubleOrNull() ?: -1.0 }

    HuzaiHalfSheet(visible = visible, onDismiss = onDismiss, fraction = 0.66f) {
        Text(
            "选择球员 · $title",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items(rows, key = { it.name }) { r ->
                val on = r.name == selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                            else Color.Transparent,
                        )
                        .clickable { onSelect(r.name) }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        r.name,
                        modifier = Modifier.weight(1f),
                        fontSize = 15.sp,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (on) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        listOf(r.pos, "${nbaCellOf(r, cols, "得分")}分")
                            .filter { it.isNotBlank() }
                            .joinToString(" · "),
                        fontSize = 12.sp,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
