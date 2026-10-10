package com.java.myapplication.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 1.200：**NBA 数据统计 / 文字实录**（`nba.hupu.com` 老版 SSR 页 → 本地解析）。
 *
 * 背景（踩坑记录 / 为什么这么做）：
 *  · 官方把「数据统计」「文字实录」放在 **nba.hupu.com** 的老版 PC 页，而评分在另一套
 *    `basketball_match/outBizNo` 体系里，**两套 id 空间互不相通**（实测：评分侧
 *    `infoJson.matchId=1537445621412134912`、`gdcId=20073352` 在 nba 页里 0 处；
 *    nba 页的 `window.GM.match_id=168952` 在评分接口里 0 处）。
 *  · 唯一的桥梁是 **按日期寻址的列表页** `https://nba.hupu.com/games/{yyyy-MM-dd}`：
 *    它服务端渲染出每场比赛的「两队名 + 比分 + 状态 + 数据统计/文字实录链接（含 gameId）」，
 *    于是可用「日期 + 两队队名」做 join（NBA 同一天不会有两场同样的对阵 → 唯一）。
 *  · 这两个页面**没有 API**，只能解析 HTML；好处是体积小（数据统计 ~38KB、文字实录 ~114KB），
 *    比开 WebView 轻得多，也不需要登录态。
 *  · 所有解析都是**纯函数**（无 IO），便于单测覆盖虎扑改版导致的失效。
 */

// ---------------------------------------------------------------------------
// 模型
// ---------------------------------------------------------------------------

/** 日期页里的一场比赛（团队名 + 比分 + gameId） */
data class NbaGameRef(
    val gameId: String,
    val teamA: String,
    val teamB: String,
    val scoreA: String,
    val scoreB: String,
)

/** 一名球员的一行数据：位置标记（G/F/C）+ 与 [NbaTableSection.columns] 一一对应的数值 */
data class NbaPlayerRow(
    val name: String,
    val pos: String,
    val values: List<String>,
)

/** 一个分组（首发 / 替补），自带列名 */
data class NbaTableSection(
    val title: String,
    val columns: List<String>,
    val rows: List<NbaPlayerRow>,
)

/** 单队数据统计（球队名 + 首发/替补分组 + 表尾汇总行） */
data class NbaTeamStats(
    val teamName: String,
    val sections: List<NbaTableSection>,
    /**
     * 1.201：表尾「统计 / 命中率」两张汇总行。
     *
     * 虎扑用 `<tr class="title bg_a">` 表示它们，但**格子里没有 `<b>`**，
     * 所以之前被当成普通数据行 → 页面上会多出两个假球员「统计」「命中率」。
     * 现在单独收在这里，列对齐方式与球员行完全一致（先空一格「位置」）。
     */
    val summaries: List<NbaPlayerRow> = emptyList(),
)

/** 数据统计页 */
data class NbaBoxScore(
    val awayName: String,
    val homeName: String,
    val awayQuarters: List<String>,
    val awayTotal: String,
    val homeQuarters: List<String>,
    val homeTotal: String,
    val away: NbaTeamStats?,
    val home: NbaTeamStats?,
)

/** 文字实录的一条事件 */
data class NbaPlay(
    /** 1..4（加时按 5 起） */
    val quarter: Int,
    val time: String,   // "12:00"
    val team: String,   // "火箭"（可能为空）
    val text: String,
    val score: String,  // "0-0"
    /**
     * 1.201：是否「重要动作」。
     *
     * 虎扑老版页**不用标签**加粗，而是在事件格上写内联样式：
     * `<td width="380"  style="font-weight: bold;" >…投中三分…</td>`
     * （实测 405 条事件里 125 条带该样式）。所以必须读属性，不能只看标签。
     */
    val important: Boolean = false,
    /**
     * 1.201：是否是「整行提示」（虎扑用 `colspan="4"` 的单格行表示）。
     *
     * 实测 405 条事件里有 11 条是这种行：`<td colspan="4" style="font-weight:bold;" align="center">独行侠60秒暂停</td>`
     * —— 暂停 / 第一节结束。/ 上半场结束。 等。它们**没有时间/球队/比分**，
     * 之前的解析器按「至少 3 格」过滤会把这些内容整条丢掉。
     */
    val note: Boolean = false,
)

/** 文字实录页 */
data class NbaPlayByPlay(
    val awayName: String,
    val homeName: String,
    /** "开赛：2026年10月09日 20:00"（原始文案，可能为空） */
    val startText: String,
    /** "耗时：02:18" */
    val durationText: String,
    /** "球馆：Venetian Arena" */
    val arenaText: String,
    /** "上座：0人" */
    val attendanceText: String,
    val awayQuarters: List<String>,
    val awayTotal: String,
    val homeQuarters: List<String>,
    val homeTotal: String,
    val plays: List<NbaPlay>,
)

// ---------------------------------------------------------------------------
// 工具
// ---------------------------------------------------------------------------

private val TAG_RE = Regex("<[^>]+>")
private val WS_RE = Regex("\\s+")

/** 去标签 + 解实体 + 折叠空白（HTML 单元格取值统一走这里） */
internal fun nbaStripTags(raw: String): String {
    if (raw.isEmpty()) return ""
    val noTags = raw.replace(TAG_RE, " ")
    val decoded = noTags
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        // 「引号」实体：拆开书写，避免写文件链路把它当转义序列吃掉
        .replace("&" + "quot;", "\"")
        .replace("&#39;", "'")
    return decoded.replace(WS_RE, " ").trim()
}

/** 去掉 <script>…</script>（老版页面里塞了大量统计/广告脚本，解析前先剥掉） */
private fun nbaDropScripts(html: String): String = html.replace(Regex("<script[\\s\\S]*?</script>"), "")
/** 时间戳 → 北京时间日期 `yyyy-MM-dd`（虎扑日期页用的就是北京时间） */
fun nbaBeijingDate(timestampMs: Long): String {
    if (timestampMs <= 0L) return ""
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
    fmt.timeZone = TimeZone.getTimeZone("Asia/Shanghai")
    return fmt.format(Date(timestampMs))
}

/**
 * 是否是「能在 nba.hupu.com 老版页找到数据」的比赛。
 *
 * NBA 与 CBA 的 `bizType` 都是 `basketball_match`（`scoreItemKey.outBizType` 相同），
 * **只能靠联赛名（`matchIntroduction` → introduction）区分**：NBA 为「NBA季前赛/常规赛」，
 * CBA 为「CBA夏季联赛」。WNBA 也含 "NBA" 子串，但属于另一数据源，排除。
 */
fun isNbaStatsSupported(bizType: String?, introduction: String): Boolean =
    bizType == "basketball_match" &&
        introduction.contains("NBA") &&
        !introduction.uppercase().contains("WNBA")


/** 单元格集合（`<td>…</td>` → 文本列表） */
private fun tds(html: String): List<String> =
    Regex("<td[^>]*>([\\s\\S]*?)</td>").findAll(html).map { nbaStripTags(it.groupValues[1]) }.toList()

/** 加粗判定：`font-weight:bold|bolder|700~900`（虎扑用内联样式标记重要动作） */
private val BOLD_ATTR_RE = Regex("font-weight\\s*:\\s*(bold|bolder|[7-9]00)", RegexOption.IGNORE_CASE)

/** 加粗判定（兜底）：单元格里直接包了 `<b>` / `<strong>` */
private val BOLD_TAG_RE = Regex("<(?:b|strong)[\\s>]", RegexOption.IGNORE_CASE)

/** 带属性的单元格（事件行需要知道某一格是否加粗，纯文本 [tds] 会丢属性） */
private class RawCell(val text: String, val bold: Boolean)

private fun rawTds(html: String): List<RawCell> =
    Regex("<td([^>]*)>([\\s\\S]*?)</td>").findAll(html).map { m ->
        val attrs = m.groupValues[1]
        val inner = m.groupValues[2]
        RawCell(
            text = nbaStripTags(inner),
            bold = BOLD_ATTR_RE.containsMatchIn(attrs) || BOLD_TAG_RE.containsMatchIn(inner),
        )
    }.toList()

// ---------------------------------------------------------------------------
// ① 日期页
// ---------------------------------------------------------------------------

/**
 * 解析 `nba.hupu.com/games/{date}`：每场以 `class="team_vs_a_1` 起块，块内含
 * 「team_vs_a_1（队1名+比分）→ team_vs_a_2（队2名+比分）→ team_vs_b（状态）→
 * table_choose（数据统计/文字实录链接，含 gameId）」。
 */
fun parseNbaDatePage(html: String): List<NbaGameRef> {
    val starts = Regex("class=\"team_vs_a_1").findAll(html).map { it.range.first }.toList()
    if (starts.isEmpty()) return emptyList()
    val out = ArrayList<NbaGameRef>(starts.size)
    for ((i, start) in starts.withIndex()) {
        val end = if (i + 1 < starts.size) starts[i + 1] else html.length
        if (end <= start) continue
        val seg = html.substring(start, end)
        val gid = Regex("/games/boxscore/(\\d+)").find(seg)?.groupValues?.get(1) ?: continue
        // 队名：块内 `…/teams/xxx">名字</a>`（每队出现两次：图片链接后紧跟 `<img` 不命中，
        // 只有文本链接命中）。真实页里 href 是 `https://nba.hupu.com/teams/celtics`（无尾斜杠），
        // 这里顺带兼容尾斜杠写法。
        val names = Regex("/teams/[A-Za-z0-9_\\-/]+\">([^<]{1,14})</a>")
            .findAll(seg)
            .map { nbaStripTags(it.groupValues[1]) }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
        // 比分：`class="num …">124<`（未开赛为空串）
        val nums = Regex("class=\"num[^\"]*\">\\s*(\\d*)\\s*<")
            .findAll(seg)
            .map { it.groupValues[1] }
            .toList()
        out += NbaGameRef(
            gameId = gid,
            teamA = names.getOrElse(0) { "" },
            teamB = names.getOrElse(1) { "" },
            scoreA = nums.getOrElse(0) { "" },
            scoreB = nums.getOrElse(1) { "" },
        )
    }
    return out
}

/**
 * 在日期页里定位本场比赛的 gameId：
 *  ① 优先「两队队名」唯一匹配（最可靠）；
 *  ② 队名缺失/有别名差异时，退化用「两分值集合」匹配兜底。
 * 两者都命不中 → null（页面显示「暂无数据」，不崩）。
 */
fun parseNbaGameId(
    datePageHtml: String,
    home: String,
    away: String,
    homeScore: String,
    awayScore: String,
): String? {
    val games = parseNbaDatePage(datePageHtml)
    if (games.isEmpty()) return null
    val names = setOf(home.trim(), away.trim()).filter { it.isNotEmpty() }
    if (names.size == 2) {
        games.firstOrNull { g -> names.all { it == g.teamA || it == g.teamB } }?.let { return it.gameId }
    }
    // 注意：必须 toSet()——Kotlin 里 Set.equals(List) 恒为 false（filter 返回的是 List），
    // 直接写 setOf(...) == scores 会让兜底永远失效。
    val scores = setOf(homeScore.trim(), awayScore.trim()).filter { it.isNotEmpty() }.toSet()
    if (scores.size == 2) {
        games.firstOrNull { g -> setOf(g.scoreA, g.scoreB) == scores }?.let { return it.gameId }
    }
    return null
}

// ---------------------------------------------------------------------------
// ② 分节比分表（两页共用）
// ---------------------------------------------------------------------------

private class QuarterRow(val name: String, val quarters: List<String>, val total: String)

private fun parseQuarterRow(html: String, cls: String): QuarterRow? {
    val m = Regex("<tr class=\"" + cls + "\">([\\s\\S]*?)</tr>").find(html) ?: return null
    val cells = tds(m.groupValues[1])
    if (cells.isEmpty()) return null
    val name = cells.first()
    val total = cells.last()
    val quarters = if (cells.size > 2) cells.subList(1, cells.size - 1) else emptyList()
    return QuarterRow(name, quarters, total)
}

// ---------------------------------------------------------------------------
// ③ 数据统计页
// ---------------------------------------------------------------------------

/**
 * 解析一支球队的球员表（`#J_away_content` / `#J_home_content`）。
 *
 * 行分三类：
 *  ① `<tr class="title …">` 且**含 `<b>`** → 分组标题行（首发 / 替补），自带列名；
 *  ② `<tr class="title …">` 且**不含 `<b>`** → 表尾汇总行（统计 / 命中率），收到 [NbaTeamStats.summaries]；
 *  ③ 其余 → 球员数据行。
 */
private fun parsePlayerTable(html: String, tableId: String): Pair<List<NbaTableSection>, List<NbaPlayerRow>> {
    val table = Regex("<table id=\"" + tableId + "\">([\\s\\S]*?)</table>").find(html)?.groupValues?.get(1)
        ?: return emptyList<NbaTableSection>() to emptyList()
    val sections = ArrayList<NbaTableSection>()
    val summaries = ArrayList<NbaPlayerRow>()
    var title = ""
    var columns = emptyList<String>()
    var rows = ArrayList<NbaPlayerRow>()
    fun flush() {
        if (rows.isNotEmpty() || title.isNotEmpty()) sections += NbaTableSection(title, columns, rows.toList())
        rows = ArrayList()
    }
    for (tr in Regex("<tr([^>]*)>([\\s\\S]*?)</tr>").findAll(table)) {
        val attrs = tr.groupValues[1]
        val seg = tr.groupValues[2]
        val isTitleRow = attrs.contains("class=\"title")
        if (isTitleRow && seg.contains("<b>")) {
            // ① 分组标题：首格 =「首发」/「替补」，第 2 格是宽度占位，其余才是列名
            flush()
            title = nbaStripTags(Regex("<b>([\\s\\S]*?)</b>").find(seg)?.groupValues?.get(1).orEmpty())
            columns = tds(seg).drop(2)
        } else {
            val cells = tds(seg)
            if (cells.size >= 3) {
                val row = NbaPlayerRow(name = cells[0], pos = cells[1], values = cells.drop(2))
                if (isTitleRow) summaries += row else rows += row
            }
        }
    }
    flush()
    return sections to summaries
}

/** 解析数据统计页；结构不符（改版）→ null */
fun parseNbaBoxScore(html: String): NbaBoxScore? {
    val body = nbaDropScripts(html)
    val away = parseQuarterRow(body, "away_score")
    val home = parseQuarterRow(body, "home_score")
    val (awaySections, awaySummaries) = parsePlayerTable(body, "J_away_content")
    val (homeSections, homeSummaries) = parsePlayerTable(body, "J_home_content")
    if (away == null && home == null && awaySections.isEmpty() && homeSections.isEmpty()) return null
    return NbaBoxScore(
        awayName = away?.name.orEmpty(),
        homeName = home?.name.orEmpty(),
        awayQuarters = away?.quarters ?: emptyList(),
        awayTotal = away?.total.orEmpty(),
        homeQuarters = home?.quarters ?: emptyList(),
        homeTotal = home?.total.orEmpty(),
        away = if (awaySections.isEmpty()) null else NbaTeamStats(away?.name.orEmpty(), awaySections, awaySummaries),
        home = if (homeSections.isEmpty()) null else NbaTeamStats(home?.name.orEmpty(), homeSections, homeSummaries),
    )
}

// ---------------------------------------------------------------------------
// ④ 文字实录页
// ---------------------------------------------------------------------------

private val CN_NUM = mapOf(
    '一' to 1, '二' to 2, '三' to 3, '四' to 4, '五' to 5,
    '六' to 6, '七' to 7, '八' to 8, '九' to 9, '十' to 10,
)

private fun quarterOf(text: String): Int? {
    // 真实页里是「第一节开始」（中文数字）；顺带兼容阿拉伯数字写法
    val m = Regex("第([一二三四五六七八九十]|\\d{1,2})节").find(text) ?: return null
    val s = m.groupValues[1]
    return CN_NUM[s[0]] ?: s.toIntOrNull()
}

/** 解析文字实录页；结构不符（改版）→ null */
fun parseNbaPlayByPlay(html: String): NbaPlayByPlay? {
    val body = nbaDropScripts(html)
    val away = parseQuarterRow(body, "away_score")
    val home = parseQuarterRow(body, "home_score")

    fun p(className: String): String =
        Regex("<p class=\"" + className + "\">([\\s\\S]*?)</p>").find(body)?.groupValues?.get(1)?.let { nbaStripTags(it) }.orEmpty()

    // 事件流：只在 table_overflow 之后的区域里找 <tr id="时间戳">
    val zoneStart = body.indexOf("playbyplay_td table_overflow")
    val zone = if (zoneStart >= 0) body.substring(zoneStart) else body
    val plays = ArrayList<NbaPlay>()
    var quarter = 1
    for (tr in Regex("<tr id=\"\\d+\">([\\s\\S]*?)</tr>").findAll(zone)) {
        val cells = rawTds(tr.groupValues[1])
        if (cells.isEmpty()) continue

        // 1.201：跨列整行提示（colspan=4 的单格行）——暂停 / 第一节结束。/ 上半场结束。
        // 只有 1 个单元格、没有时间/球队/比分，单独成一条 note。
        if (cells.size == 1) {
            val text = cells[0].text
            if (text.isEmpty()) continue
            quarterOf(text)?.let { q -> if (text.contains("开始")) quarter = q }
            plays += NbaPlay(
                quarter = quarter,
                time = "",
                team = "",
                text = text,
                score = "",
                important = cells[0].bold,
                note = true,
            )
            continue
        }

        if (cells.size < 3) continue
        val text = cells[2].text
        quarterOf(text)?.let { q -> if (text.contains("开始")) quarter = q }
        if (text.contains("加时") && quarter < 5) quarter = 5
        plays += NbaPlay(
            quarter = quarter,
            time = cells[0].text,
            team = cells[1].text,
            text = text,
            score = cells.getOrNull(3)?.text.orEmpty(),
            // 虎扑用「事件格内联 font-weight:bold」标记重要动作（得分/盖帽/抢断等）
            important = cells[2].bold,
        )
    }
    if (plays.isEmpty() && away == null && home == null) return null
    return NbaPlayByPlay(
        awayName = away?.name.orEmpty(),
        homeName = home?.name.orEmpty(),
        startText = p("time_f"),
        durationText = p("consumTime"),
        arenaText = p("arena"),
        attendanceText = p("peopleNum"),
        awayQuarters = away?.quarters ?: emptyList(),
        awayTotal = away?.total.orEmpty(),
        homeQuarters = home?.quarters ?: emptyList(),
        homeTotal = home?.total.orEmpty(),
        plays = plays,
    )
}
