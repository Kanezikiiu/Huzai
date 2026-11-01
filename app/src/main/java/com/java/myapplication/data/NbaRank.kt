package com.java.myapplication.data

import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * 1.258：**NBA 排行榜**（战绩 / 数据排名）——数据全部来自 nba.hupu.com 的 SSR 表格，
 * 无需额外接口。
 *
 * 页面与形态（实测）：
 *  · `/standings`            东西部两张表，14 列
 *  · `/standings/division`   6 个赛区表（大西洋/中部/东南/太平洋/西北/西南），列同上
 *  · `/stats/players/{pts,reb,asts,stl,blk,fgp,tpp,ftp}` 每个指标一个 URL，**列数随指标变化**
 *  · `/stats/teams`          单张表，**双层表头 20 列**，官方是前端排序（我们本地排）
 *
 * 解析策略：**按表头文字映射列**，不硬编码列序 —— 官方改版或不同指标列数不同都能吃下。
 */

/** 一张排行表（可能带分区块标题，如「东部」「大西洋赛区 - 东部」） */
data class NbaRankTable(
    val title: String = "",
    val headers: List<String> = emptyList(),
    val rows: List<List<String>> = emptyList(),
    /**
     * 1.292：是否允许点表头排序。
     * 名次/积分这种数值榜应该可排；淘汰赛对阵表（主队/比分/客队）排序没有意义，置 false。
     */
    val sortable: Boolean = true,
)

/** 球员数据的指标（顺序即 UI chip 顺序） */
data class NbaStatMetric(val key: String, val label: String)

val NBA_PLAYER_METRICS = listOf(
    NbaStatMetric("pts", "得分"),
    NbaStatMetric("reb", "篮板"),
    NbaStatMetric("asts", "助攻"),
    NbaStatMetric("stl", "抢断"),
    NbaStatMetric("blk", "盖帽"),
    NbaStatMetric("fgp", "投篮"),
    NbaStatMetric("tpp", "三分"),
    NbaStatMetric("ftp", "罚球"),
)

object NbaRankParser {

    private fun stripTags(s: String): String =
        s.replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace(Regex("\\s+"), " ")
            .trim()

    private val TR = Regex("<tr[^>]*>(.*?)</tr>", RegexOption.DOT_MATCHES_ALL)
    private val TD = Regex("<t[dh][^>]*>(.*?)</t[dh]>", RegexOption.DOT_MATCHES_ALL)

    private fun cells(rowHtml: String): List<String> = TD.findAll(rowHtml).map { stripTags(it.groupValues[1]) }.toList()

    /** 是否为「表头行」：首格是 排名 / 名次，或含「球队」「球员」 */
    private fun isHeaderRow(c: List<String>): Boolean =
        c.isNotEmpty() && (c[0] == "排名" || c[0] == "名次")

    /**
     * 是否为「双层表头的第二行」（球队页专有）：
     * 从第 3 列起**要么是空（官方用 &nbsp; 占位）要么是 命中/出手/命中率**，
     * 不含任何数字 —— 数据行必然有数字，所以不会被误判。
     */
    private fun isSubHeaderRow(c: List<String>): Boolean {
        if (c.size < 3) return false
        val tail = c.drop(2)
        if (tail.isEmpty()) return false
        return tail.all { it.isBlank() || it == "命中" || it == "出手" || it == "命中率" }
    }

    /**
     * 东西部 / 赛区：表格里有多张"分区块"（一个标题行 + 一个表头行 + 若干数据行）。
     * 标题行特征：只有一个单元格且 colspan 很大（解析后 cells.size == 1）。
     */
    fun parseBlockGrouped(html: String): List<NbaRankTable> {
        val out = mutableListOf<NbaRankTable>()
        var title = ""
        var headers: List<String> = emptyList()
        var rows = mutableListOf<List<String>>()
        fun flush() {
            if (headers.isNotEmpty() && rows.isNotEmpty()) {
                out += NbaRankTable(title, headers, rows)
            }
            rows = mutableListOf()
        }
        for (m in TR.findAll(html)) {
            val c = cells(m.groupValues[1])
            if (c.isEmpty()) continue
            when {
                // 分区标题行
                c.size == 1 && !isHeaderRow(c) -> {
                    flush()
                    title = c[0]
                }
                isHeaderRow(c) -> {
                    // 新表头出现：若已有数据则先收尾（多区块连续）
                    if (headers.isNotEmpty() && rows.isNotEmpty()) flush()
                    headers = c
                }
                headers.isNotEmpty() -> rows += c
            }
        }
        flush()
        return out
    }

    /** 球员 /球队：单张表。球队页有两层表头（**分组行在前**、带「排名」的子表头在后）→ 需要合并。 */
    fun parseSingle(html: String): NbaRankTable? {
        val trs = TR.findAll(html).map { it.groupValues[1] }.toList()
        val rowsHtml = trs.map { it to cells(it) }.filter { it.second.isNotEmpty() }
        if (rowsHtml.isEmpty()) return null

        // 表头行 = 首格是「排名 / 名次」的那一行
        val hi = rowsHtml.indexOfFirst { isHeaderRow(it.second) }
        if (hi < 0) return null
        val headerCells = rowsHtml[hi].second

        // 双层表头判定：**上一行比表头短**（投篮/三分/罚球… 的分组行）
        val groupRow = rowsHtml.getOrNull(hi - 1)?.second
            ?.takeIf { it.size in 1 until headerCells.size }
        val headers = if (groupRow != null) mergeTwoLevel(groupRow, headerCells) else headerCells

        val rows = rowsHtml.drop(hi + 1).map { it.second }.filter { it.size >= 2 }
        return NbaRankTable("", headers, rows)
    }

    /**
     * 双层表头合并：分组行形如 ["", "投篮","三分","罚球","篮板","助攻",...]（比子表头少列），
     * 子表头相邻同名（命中率/命中/出手 重复三组）→ 按分组列数依次消费分组名。
     */
    private fun mergeTwoLevel(groupRow: List<String>?, sub: List<String>): List<String> {
        val groups = groupRow?.filter { it.isNotBlank() && it != " " } ?: return sub
        // 子表头前两列（排名/球队）不属于任何分组
        val out = sub.toMutableList()
        var gi = 0
        var si = 2
        // 每个分组在子表头里占几列：由"同名组的重复列数"推断不可靠，
        // 这里按官方结构：投篮/三分/罚球/篮板 各 3 列，其余各 1 列
        val threeColGroups = setOf("投篮", "三分", "罚球", "篮板")
        var countInGroup = 0
        var deep = 3
        while (gi < groups.size && si < out.size) {
            val g = groups[gi]
            val cell = sub[si]
            val label = when {
                // 官方后半段的子表头是 &nbsp;（解析后为空）→ 直接用分组名（助攻/失误/抢断…）
                cell.isBlank() -> g
                // 这三列重复出现（命中率/命中/出手 × 投篮·三分·罚球）→ 加分组前缀
                cell == "命中率" || cell == "命中" || cell == "出手" -> g + cell
                // 其余（总篮板 / 进攻 / 防守…）本身已自明 → 原样
                else -> cell
            }
            out[si] = label
            si++
            countInGroup++
            val target = if (g in threeColGroups) deep else 1
            if (countInGroup >= target) {
                gi++
                countInGroup = 0
            }
        }
        return out
    }

    /** 数值化：用于本地排序（去 % / 空格；"11.20-23.60" 取前者） */
    fun numeric(text: String): Double {
        val t = text.substringBefore('-').replace("%", "").trim()
        return t.toDoubleOrNull() ?: Double.NEGATIVE_INFINITY
    }
}

/**
 * 1.258：NBA 排行数据的**内存缓存**（10 分钟）——这几个页面都是静态统计，
 * 频繁来回切换不必每次打网络。
 */
/**
 * 1.274：**列宽预测量**（后台线程可用）。
 *
 * 之前用 Compose 的 `TextMeasurer` 在**组合期**测量（20 列 × 31 行 ≈ 620 次），
 * 全部压在主线程首帧上 —— 首次打开某 tab 必然掉帧。
 * 这里改用 `android.graphics.Paint`（纯测量，不建布局），可以在**后台线程**先算好，
 * 界面组合时直接命中缓存，首帧只做节点构建。
 *
 * 测量样本：表头 + 前 24 行（数字列宽度很稳定，足够定住列宽）。
 */
object NbaColWidthMeasurer {
    fun computePx(table: NbaRankTable, headSp: Float, bodySp: Float, density: Float): List<Float> {
        if (table.headers.isEmpty()) return emptyList()
        val hp = android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = headSp * density
        }
        val bp = android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = bodySp * density
        }
        return table.headers.indices.map { i ->
            val h = hp.measureText(table.headers[i])
            val b = table.rows.asSequence().take(24)
                .map { bp.measureText(it.getOrNull(i).orEmpty()) }
                .maxOrNull() ?: 0f
            maxOf(h, b)
        }
    }
}

/**
 * 1.272：**列宽测量结果缓存**。
 *
 * 列宽是按「表头 + 所有单元格」的文本实测出来的（球队表 20 列 × 31 行 ≈ 620 次测量），
 * 每次切 tab 重建表格都会重跑一遍 —— 这是切换卡顿的主要来源之一。
 * 这里按（表头 + 行数 + 排序列 + 容器宽）做键缓存，命中就直接用。
 *
 * 键里带上容器宽：平板/横竖屏切换后需要按新宽度重算一次。
 */
object NbaColWidthCache {
    private const val MAX = 24
    private val map = object : LinkedHashMap<String, List<Float>>(MAX, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Float>>): Boolean =
            size > MAX
    }

    /** 只按内容做键：存的是「不含排序箭头、不含铺满」的基础宽度 */
    fun key(table: NbaRankTable): String =
        "${table.headers.hashCode()}_${table.rows.size}"

    @Synchronized
    fun get(k: String): List<Float>? = map[k]

    @Synchronized
    fun put(k: String, v: List<Float>) {
        map[k] = v
    }
}

object NbaRankCache {
    private const val TTL_MS = 10 * 60 * 1000L
    private val map = ConcurrentHashMap<String, Pair<Long, List<NbaRankTable>>>()

    fun get(key: String): List<NbaRankTable>? {
        val e = map[key] ?: return null
        if (System.currentTimeMillis() - e.first > TTL_MS) {
            map.remove(key)
            return null
        }
        return e.second
    }

    fun put(key: String, v: List<NbaRankTable>) {
        map[key] = System.currentTimeMillis() to v
    }
}