package com.java.myapplication.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.200：NBA「数据统计 / 文字实录」解析单测。
 *
 * 这两页**没有 API**，只能解析 `nba.hupu.com` 的老版 SSR HTML；虎扑一旦改版，这里会第一时间红。
 * 所有解析都是纯函数，直接喂字符串即可。
 *
 * 夹具（fixture）严格照抄真实页面的记号特征（取自 2026-10-09 实测）：
 *  · 日期页：队名链接是 `…/teams/rockets`（**无尾斜杠**），比分是 `<span class="num red">135</span>`，
 *    并且**每个队先出现一个只含 `<img>` 的 logo 链接**（必须不被当队名抓走）。
 *  · 数据统计：比分行是 `<tr class="away_score">`，球员表是 `<table id="J_away_content">`，
 *    分组标题行是 `<tr class="title bg_a" …><td class="left"><b>首发</b></td><td></td><td>时间</td>…`，
 *    数据行首格是 `<td class="tdw-1 left"><a …>球员名</a></td>`。
 *  · 文字实录：节标记是**中文数字**「第一节开始」，事件行是 `<tr id="13位时间戳">` + 4 个 `<td>`。
 */
class NbaStatsParserTest {

    // =================================================================
    // ① 门禁：NBA / CBA / WNBA 的区分（两者 bizType 相同，只能靠联赛名）
    // =================================================================

    @Test
    fun nbaGate_acceptsNba() {
        assertTrue(isNbaStatsSupported("basketball_match", "NBA季前赛"))
        assertTrue(isNbaStatsSupported("basketball_match", "NBA常规赛"))
        assertTrue(isNbaStatsSupported("basketball_match", "NBA季后赛"))
    }

    @Test
    fun nbaGate_rejectsCba() {
        // CBA 的 outBizType 同样是 basketball_match，但联赛名不含 NBA
        assertFalse(isNbaStatsSupported("basketball_match", "CBA夏季联赛"))
        assertFalse(isNbaStatsSupported("basketball_match", "CBA常规赛"))
    }

    @Test
    fun nbaGate_rejectsWnbaAndOthers() {
        // WNBA 含 "NBA" 子串但属于另一数据源
        assertFalse(isNbaStatsSupported("basketball_match", "WNBA常规赛"))
        assertFalse(isNbaStatsSupported("basketball_match", "wnba季前赛"))
        assertFalse(isNbaStatsSupported("lol_match", "NBA"))
        assertFalse(isNbaStatsSupported(null, "NBA季前赛"))
        assertFalse(isNbaStatsSupported("basketball_match", ""))
    }

    // =================================================================
    // ② 北京时间日期（虎扑日期页按北京时间寻址）
    // =================================================================

    @Test
    fun beijingDate_formatsAndShifts() {
        // 2026-10-09T12:00:00Z = 北京 2026-10-09 20:00
        assertEquals("2026-10-09", nbaBeijingDate(1_791_547_200_000L))
        // 2026-10-09T17:00:00Z = 北京 2026-10-10 01:00 → 跨天
        assertEquals("2026-10-10", nbaBeijingDate(1_791_565_200_000L))
    }

    @Test
    fun beijingDate_blankForInvalid() {
        assertEquals("", nbaBeijingDate(0L))
        assertEquals("", nbaBeijingDate(-1L))
    }

    // =================================================================
    // ③ 日期页解析 + gameId join
    // =================================================================

    /** 真实结构：比赛块 = team_vs_a_1（队1+比分）→ team_vs_a_2（队2+比分）→ team_vs_b → table_choose */
    private fun gameBlock(
        slug1: String, name1: String, score1: String,
        slug2: String, name2: String, score2: String,
        gameId: String,
    ): String {
        val sb = StringBuilder()
        fun line(s: String) { sb.append(s).append('\n') }
        line("<div class=\"game_box\">")
        line("<div class=\"team_vs_a_1 clearfix\">")
        line("<div class=\"img\">")
        line("<a target=\"_blank\" href=\"https://nba.hupu.com/teams/$slug1\">")
        line("<img src=\"https://gdc.hupucdn.com/gdc/nba/team/logo/$slug1.png\" height=\"50\" width=\"50\">")
        line("</a>")
        line("</div>")
        line("<div class=\"txt\">")
        line("<span class=\"num red\">$score1</span>")
        line("<span>")
        line("<a target=\"_blank\" href=\"https://nba.hupu.com/teams/$slug1\">$name1</a>")
        line("</span>")
        line("</div>")
        line("</div>")
        line("<div class=\"team_vs_a_2 clearfix\">")
        line("<div class=\"img\">")
        line("<a target=\"_blank\" href=\"https://nba.hupu.com/teams/$slug2\">")
        line("<img src=\"https://gdc.hupucdn.com/gdc/nba/team/logo/$slug2.png\" height=\"50\" width=\"50\">")
        line("</a>")
        line("</div>")
        line("<div class=\"txt\">")
        line("<span class=\"num \">$score2</span>")
        line("<span>")
        line("<a target=\"_blank\" href=\"https://nba.hupu.com/teams/$slug2\">$name2</a>")
        line("</span>")
        line("</div>")
        line("</div>")
        line("<div class=\"team_vs_b\">")
        line("<span class=\"a\"></span>")
        line("<span class=\"b\">")
        line("已结束</span>")
        line("</div>")
        line("<p class=\"tips\">")
        line("<a  href=\"https://nba.hupu.com/games/recap/$gameId\"></a>")
        line("</p>")
        line("<div class=\"table_choose clearfix\">")
        line("<a target=\"_self\" href=\"https://nba.hupu.com/games/boxscore/$gameId\" class=\"d\"><s></s>数据统计</a>")
        line("<a target=\"_self\" href=\"https://nba.hupu.com/games/playbyplay/$gameId\" class=\"b\"><s></s>文字实录</a>")
        line("</div>")
        line("</div>")
        return sb.toString()
    }

    private val datePage =
        gameBlock("rockets", "火箭", "135", "mavericks", "独行侠", "117", "168952") +
            gameBlock("celtics", "凯尔特人", "124", "cavaliers", "骑士", "113", "168949")

    @Test
    fun datePage_parsesBothGames() {
        val games = parseNbaDatePage(datePage)
        assertEquals(2, games.size)
        assertEquals("168952", games[0].gameId)
        assertEquals("火箭", games[0].teamA)
        assertEquals("独行侠", games[0].teamB)
        assertEquals("135", games[0].scoreA)
        assertEquals("117", games[0].scoreB)
        assertEquals("168949", games[1].gameId)
        assertEquals("凯尔特人", games[1].teamA)
        assertEquals("骑士", games[1].teamB)
        assertEquals("124", games[1].scoreA)
        assertEquals("113", games[1].scoreB)
    }

    @Test
    fun gameId_joinsByTeamNames() {
        // 顺序无关（主客互换也能命中）
        assertEquals("168952", parseNbaGameId(datePage, "火箭", "独行侠", "135", "117"))
        assertEquals("168952", parseNbaGameId(datePage, "独行侠", "火箭", "117", "135"))
        assertEquals("168949", parseNbaGameId(datePage, "凯尔特人", "骑士", "124", "113"))
    }

    @Test
    fun gameId_fallsBackToScores() {
        // 队名有别名差异（带「队」字）→ 队名命不中，退化按两分值集合匹配
        assertEquals("168952", parseNbaGameId(datePage, "火箭队", "独行侠队", "135", "117"))
    }

    @Test
    fun gameId_nullWhenNothingMatches() {
        assertNull(parseNbaGameId(datePage, "甲", "乙", "1", "2"))
        assertNull(parseNbaGameId("<html></html>", "火箭", "独行侠", "135", "117"))
        // 只有一个队名可用、且比分也对不上 → null（不误命中）
        assertNull(parseNbaGameId(datePage, "火箭", "", "7", "9"))
    }

    // =================================================================
    // ④ 数据统计页
    // =================================================================

    private val boxColumns = listOf(
        "时间", "投篮", "3分", "罚球", "前场", "后场", "篮板",
        "助攻", "犯规", "抢断", "失误", "封盖", "得分", "+/-",
    )

    private fun columnsRow() = "<tr class=\"title bg_a\" style=\"background-color: rgb(251, 251, 251);\">" +
        "<td class=\"left\" width=\"150\"><b>首发</b></td>" +
        "<td width=\"23\"></td>" +
        boxColumns.joinToString("") { "<td>$it</td>" } +
        "</tr>"

    private fun benchTitleRow() = "<tr class=\"title bg_a\" style=\"background-color: rgb(251, 251, 251);\">" +
        "<td class=\"left\" width=\"150\"><b>替补</b></td>" +
        "<td width=\"23\"></td>" +
        boxColumns.joinToString("") { "<td>$it</td>" } +
        "</tr>"

    private fun playerRow(name: String, pos: String, values: List<String>) =
        "<tr style=\"background-color: rgb(255, 255, 255);\">" +
            "<td class=\"tdw-1 left\"><a href=\"https://nba.hupu.com/players/$name.html\" target=\"_blank\">$name</a></td>" +
            "<td>$pos</td>" +
            values.joinToString("") { "<td>$it</td>" } +
            "</tr>"

    /** 表尾汇总行（统计 / 命中率）：`class="title"` 但格子里**没有 `<b>`**，首格是汇总名 */
    private fun summaryRow(name: String, values: List<String>) =
        "<tr class=\"title bg_a\" style=\"background-color: rgb(251, 251, 251);\">" +
            "<td width=\"150\" class=\"left\">$name</td>" +
            "<td>&nbsp;</td>" +
            values.joinToString("") { "<td>$it</td>" } +
            "</tr>"

    private val boxHtml: String = run {
        val sb = StringBuilder()
        sb.append("<html><body>\n")
        sb.append("<script>var ad = 1;</script>\n")
        sb.append("<table class=\"itinerary_table itinerary_table-top \">\n<tbody>\n")
        sb.append("<tr class=\"title\"><td width=\"50\" class=\"left\"></td><td>一</td><td>二</td><td>三</td><td>四</td><td>总分</td></tr>\n")
        sb.append("<tr class=\"away_score\"><td>火箭</td>")
        listOf("27", "38", "34", "36").forEachIndexed { i, v ->
            sb.append("<td class=\"item-away-$i\">\n$v\n</td>")
        }
        sb.append("<td class=\"item-away-total\">\n135\n</td></tr>\n")
        sb.append("<tr class=\"home_score\"><td>独行侠</td>")
        listOf("19", "26", "35", "37").forEachIndexed { i, v ->
            sb.append("<td class=\"item-home-$i\">\n$v\n</td>")
        }
        sb.append("<td class=\"item-home-total\">\n117\n</td></tr>\n")
        sb.append("</tbody></table>\n")

        sb.append("<table id=\"J_away_content\">\n<tbody>\n")
        sb.append(columnsRow()).append('\n')
        sb.append(playerRow("弗雷德-范弗利特", "G", listOf("24", "5-12", "5-12", "2-3", "0", "2", "2", "7", "1", "2", "1", "0", "17", "+15"))).append('\n')
        sb.append(playerRow("凯文-杜兰特", "F", listOf("23", "7-12", "1-1", "0-1", "0", "0", "0", "2", "0", "0", "1", "1", "15", "+11"))).append('\n')
        sb.append(benchTitleRow()).append('\n')
        sb.append(playerRow("阿门·汤普森", "G", listOf("24", "4-8", "0-0", "0-0", "3", "4", "7", "2", "2", "1", "6", "1", "8", "+7"))).append('\n')
        // 表尾汇总行：列对齐与球员行一致（时间列留空）
        sb.append(summaryRow("统计", listOf("", "47-86", "18-36", "23-32", "16", "27", "43", "36", "22", "11", "21", "4", "135", ""))).append('\n')
        sb.append(summaryRow("命中率", listOf("", "54.7%", "50.0%", "71.9%", "", "", "", "", "", "", "", "", "", ""))).append('\n')
        sb.append("</tbody></table>\n")

        sb.append("<table id=\"J_home_content\">\n<tbody>\n")
        sb.append(columnsRow()).append('\n')
        sb.append(playerRow("马克斯-克里斯蒂", "G", listOf("24", "5-12", "4-11", "0-0", "1", "1", "2", "2", "1", "1", "0", "0", "14", "-12"))).append('\n')
        sb.append("</tbody></table>\n")
        sb.append("</body></html>")
        sb.toString()
    }

    @Test
    fun boxScore_parsesQuartersAndPlayers() {
        val box = parseNbaBoxScore(boxHtml)
        assertNotNull(box)
        box!!
        assertEquals("火箭", box.awayName)
        assertEquals("独行侠", box.homeName)
        assertEquals(listOf("27", "38", "34", "36"), box.awayQuarters)
        assertEquals("135", box.awayTotal)
        assertEquals(listOf("19", "26", "35", "37"), box.homeQuarters)
        assertEquals("117", box.homeTotal)

        val away = box.away
        assertNotNull(away)
        assertEquals("火箭", away!!.teamName)
        assertEquals(2, away.sections.size)

        val starters = away.sections[0]
        assertEquals("首发", starters.title)
        assertEquals(boxColumns, starters.columns)
        assertEquals(2, starters.rows.size)
        assertEquals("弗雷德-范弗利特", starters.rows[0].name)
        assertEquals("G", starters.rows[0].pos)
        assertEquals(
            listOf("24", "5-12", "5-12", "2-3", "0", "2", "2", "7", "1", "2", "1", "0", "17", "+15"),
            starters.rows[0].values,
        )
        assertEquals("凯文-杜兰特", starters.rows[1].name)

        val bench = away.sections[1]
        assertEquals("替补", bench.title)
        assertEquals(1, bench.rows.size)
        assertEquals("阿门·汤普森", bench.rows[0].name)

        val home = box.home
        assertNotNull(home)
        assertEquals(1, home!!.sections.size)
        assertEquals("马克斯-克里斯蒂", home.sections[0].rows[0].name)
        assertEquals("14", home.sections[0].rows[0].values[12])

        // 1.201：表尾「统计 / 命中率」是汇总行，**不能再当作球员**
        assertEquals(2, away.summaries.size)
        assertEquals("统计", away.summaries[0].name)
        assertEquals("135", away.summaries[0].values[12])      // 得分列
        assertEquals("命中率", away.summaries[1].name)
        assertEquals("54.7%", away.summaries[1].values[1])     // 投篮命中率
        assertTrue(
            away.sections.none { s -> s.rows.any { it.name == "统计" || it.name == "命中率" } },
        )
        // 主队表没有汇总行 → 空
        assertTrue(home.summaries.isEmpty())
    }

    @Test
    fun boxScore_keepsLongPlayerNamesIntact() {
        // 真实数据里有 10 字长名（「波格丹-波格丹诺维奇」）——解析层必须原样保留（截断是渲染层的事）
        val html = "<html><body>" +
            "<table id=\"J_away_content\"><tbody>" +
            columnsRow() +
            playerRow("波格丹-波格丹诺维奇", "G", listOf("24", "5-12", "5-12", "2-3", "0", "2", "2", "7", "1", "2", "1", "0", "17", "+15")) +
            playerRow("塞尔吉奥-德-拉雷亚", "F", listOf("21", "4-9", "1-3", "2-2", "1", "3", "4", "2", "3", "0", "1", "1", "11", "-4")) +
            "</tbody></table></body></html>"
        val box = parseNbaBoxScore(html)!!
        val rows = box.away!!.sections[0].rows
        assertEquals("波格丹-波格丹诺维奇", rows[0].name)
        assertEquals(10, rows[0].name.length)
        assertEquals("塞尔吉奥-德-拉雷亚", rows[1].name)
        assertEquals(10, rows[1].name.length)
    }

    @Test
    fun boxScore_skipsScriptsAndNullOnGarbage() {
        // script 里即使塞了假 table，也不该被当成数据
        assertNull(parseNbaBoxScore("<html><script><table id=\"J_away_content\"></table></script></html>"))
        assertNull(parseNbaBoxScore("<html><body>无数据</body></html>"))
        assertNull(parseNbaBoxScore(""))
    }

    // =================================================================
    // ⑤ 文字实录页
    // =================================================================

    private val pbpHtml: String = """
        <html><body>
        <p class="time_f">开赛：2026年10月09日 20:00</p>
        <p class="consumTime">耗时：02:18</p>
        <p class="arena">球馆：Venetian Arena</p>
        <p class="peopleNum">上座：0人</p>
        <table class="itinerary_table itinerary_table-top ">
        <tr class="away_score"><td>火箭</td><td class="item-away-0">27</td><td class="item-away-1">38</td><td class="item-away-2">34</td><td class="item-away-3">36</td><td class="item-away-total">135</td></tr>
        <tr class="home_score"><td>独行侠</td><td class="item-home-0">19</td><td class="item-home-1">26</td><td class="item-home-2">35</td><td class="item-home-3">37</td><td class="item-home-total">117</td></tr>
        </table>
        <table class="other">
        <tr id="1111111111111"><td>12:00</td><td>火箭</td><td>这是表外事件不该被采集</td><td>0-0</td></tr>
        </table>
        <div class="playbyplay_td table_overflow">
        <table>
        <tr id="1791547983247"><td class="tdw-1 left" width="69">12:00</td><td width="69">
        火箭</td><td width="380" >第一节开始</td><td width="157" align="center">0-0</td></tr>
        <span style="display:none">1</span>
        <tr id="1791548001823"><td class="tdw-1 left" width="69">11:41</td><td width="69">
        火箭</td><td width="380" >弗雷德·范维特失误（出界丢球）</td><td width="157" align="center">0-0</td></tr>
        <span style="display:none">2</span>
        <tr id="1791550000000"><td class="tdw-1 left" width="69">12:00</td><td width="69">
        独行侠</td><td width="380" >第二节开始</td><td width="157" align="center">27-19</td></tr>
        <span style="display:none">3</span>
        <tr id="1791551000000"><td class="tdw-1 left" width="69">05:12</td><td width="69">
        火箭</td><td width="380"  style="font-weight: bold;" >凯文-杜兰特 三分命中</td><td width="157" align="center">45-30</td></tr>
        <span style="display:none">4</span>
        <tr id="1791552000000"><td class="tdw-1 left" width="69">00:00</td><td width="69">
        </td><td width="380" >加时赛开始</td><td width="157" align="center">117-117</td></tr>
        <span style="display:none">5</span>
        <tr id="1791552900000"><td colspan="4" style="font-weight:bold;" align="center">独行侠60秒暂停</td></tr>
        </table>
        </div>
        </body></html>
    """.trimIndent()

    @Test
    fun playByPlay_parsesHeadAndQuarters() {
        val pbp = parseNbaPlayByPlay(pbpHtml)
        assertNotNull(pbp)
        pbp!!
        assertEquals("火箭", pbp.awayName)
        assertEquals("独行侠", pbp.homeName)
        assertEquals("开赛：2026年10月09日 20:00", pbp.startText)
        assertEquals("耗时：02:18", pbp.durationText)
        assertEquals("球馆：Venetian Arena", pbp.arenaText)
        assertEquals("上座：0人", pbp.attendanceText)
        assertEquals("135", pbp.awayTotal)
        assertEquals("117", pbp.homeTotal)
    }

    @Test
    fun playByPlay_parsesEventsOnlyInsideZone() {
        val pbp = parseNbaPlayByPlay(pbpHtml)!!
        // 表外那条（id=1111111111111）不应被采集 → zone 内 5 条普通事件 + 1 条跨列提示
        assertEquals(6, pbp.plays.size)
        assertFalse(pbp.plays.any { it.text.contains("表外事件") })

        assertEquals(1, pbp.plays[0].quarter)
        assertEquals("12:00", pbp.plays[0].time)
        assertEquals("火箭", pbp.plays[0].team)
        assertEquals("第一节开始", pbp.plays[0].text)
        assertEquals("0-0", pbp.plays[0].score)

        // 节推进（中文数字）
        assertEquals(1, pbp.plays[1].quarter)
        assertEquals(2, pbp.plays[2].quarter)
        assertEquals(2, pbp.plays[3].quarter)
        // 加时 → 第 5 节
        assertEquals(5, pbp.plays[4].quarter)

        // 1.201：重要动作加粗（虎扑用事件格的 font-weight:bold 内联样式标记）
        assertFalse(pbp.plays[0].important)   // 「第一节开始」不粗
        assertFalse(pbp.plays[1].important)   // 失误 不粗
        assertTrue(pbp.plays[3].important)    // 「凯文-杜兰特 三分命中」→ 粗

        // 1.201：跨列整行提示（colspan=4）不能被丢掉——暂停/节结束等
        val note = pbp.plays[5]
        assertTrue(note.note)
        assertEquals("独行侠60秒暂停", note.text)
        assertEquals("", note.time)
        assertEquals("", note.team)
        assertEquals("", note.score)
        // 提示行继承当前节（此处已在加时）
        assertEquals(5, note.quarter)
        assertTrue(note.important)
    }

    @Test
    fun playByPlay_boldDetectedFromInlineStyleOrTag() {
        // 三种写法都要能识别：内联 font-weight:bold / 数字字重 / <b> 标签
        val html = "<html><body><div class=\"playbyplay_td table_overflow\"><table>" +
            "<tr id=\"1\"><td>12:00</td><td>火箭</td><td style=\"font-weight: bold;\">投中三分</td><td>3-0</td></tr>" +
            "<tr id=\"2\"><td>12:00</td><td>火箭</td><td style=\"font-weight:700\">扣篮</td><td>5-0</td></tr>" +
            "<tr id=\"3\"><td>12:00</td><td>火箭</td><td><b>三分命中</b></td><td>8-0</td></tr>" +
            "<tr id=\"4\"><td>12:00</td><td>火箭</td><td>暂停</td><td>8-0</td></tr>" +
            "</table></div></body></html>"
        val pbp = parseNbaPlayByPlay(html)!!
        assertEquals(4, pbp.plays.size)
        assertTrue(pbp.plays[0].important)
        assertTrue(pbp.plays[1].important)
        assertTrue(pbp.plays[2].important)
        assertFalse(pbp.plays[3].important)
        // 加粗不污染文本内容
        assertEquals("三分命中", pbp.plays[2].text)
    }

    @Test
    fun playByPlay_supportsArabicQuarterDigits() {
        // 防御性：若虎扑改成「第3节开始」，也要能识别
        val html = "<html><body>" +
            "<div class=\"playbyplay_td table_overflow\"><table>" +
            "<tr id=\"1\"><td>12:00</td><td>火箭</td><td>第一节开始</td><td>0-0</td></tr>" +
            "<tr id=\"2\"><td>12:00</td><td></td><td>第3节开始</td><td>50-50</td></tr>" +
            "</table></div></body></html>"
        val pbp = parseNbaPlayByPlay(html)!!
        assertEquals(1, pbp.plays[0].quarter)
        assertEquals(3, pbp.plays[1].quarter)
    }

    @Test
    fun playByPlay_nullOnGarbage() {
        assertNull(parseNbaPlayByPlay("<html><body>无数据</body></html>"))
        assertNull(parseNbaPlayByPlay(""))
    }

    // =================================================================
    // ⑥ 去标签工具（表格单元格取值统一走它）
    // =================================================================

    @Test
    fun stripTags_decodesEntitiesAndCollapses() {
        assertEquals("火箭 vs 独行侠", nbaStripTags("<b>火箭</b>  vs\n <i>独行侠</i>"))
        assertEquals(
            "弗雷德-范弗利特",
            nbaStripTags("<a href=\"x\">弗雷德-范弗利特</a>"),
        )
        assertEquals("a&b", nbaStripTags("a&amp;b"))
        assertEquals("\"三分\"", nbaStripTags("&" + "quot;三分&" + "quot;"))
        assertEquals("", nbaStripTags(""))
    }
}