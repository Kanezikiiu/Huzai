package com.java.myapplication

import com.java.myapplication.data.NbaPlayerRow
import com.java.myapplication.data.NbaTableSection
import com.java.myapplication.data.NbaTeamStats
import com.java.myapplication.ui.pages.NbaBarMode
import com.java.myapplication.ui.pages.NbaKingSpecs
import com.java.myapplication.ui.pages.NbaPkSpecs
import com.java.myapplication.ui.pages.NbaStatsQuery
import com.java.myapplication.ui.pages.NbaTeamSpecs
import com.java.myapplication.ui.pages.nbaBarFractions
import com.java.myapplication.ui.pages.nbaCellOf
import com.java.myapplication.ui.pages.nbaMadeAttempted
import com.java.myapplication.ui.pages.nbaNum
import com.java.myapplication.ui.pages.nbaPctDisplay
import com.java.myapplication.ui.pages.nbaShootingPct
import com.java.myapplication.ui.pages.nbaStatLeader
import com.java.myapplication.ui.pages.nbaStatsKey
import com.java.myapplication.ui.pages.nbaTeamTotalsRow
import com.java.myapplication.ui.pages.nbaTopScorer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.224d：球员 PK / 本场数据帝的数据派生。
 *
 * 两块内容都从球员数据表派生，这里锁住最容易错的三件事：
 *  ① 列名 → 下标的映射（表头顺序变了也不能错位）；
 *  ② 队内头名的选取；
 *  ③ 投篮类「按命中率比大小」的口径。
 */
class NbaStatsExtrasTest {

    private val columns = listOf(
        "时间", "投篮", "3分", "罚球", "前场", "后场", "篮板",
        "助攻", "犯规", "抢断", "失误", "封盖", "得分", "+/-",
    )

    private fun row(name: String, pos: String, values: List<String>) = NbaPlayerRow(name, pos, values)

    /** 左侧球队：杰罗姆 16 分 / 洛佩斯 6 分 · 6 篮板 */
    private val away = NbaTeamStats(
        "灰熊",
        listOf(
            NbaTableSection(
                "首发", columns,
                listOf(
                    row("杰罗姆", "G", listOf("24", "5-8", "1-2", "5-6", "0", "1", "1", "4", "2", "1", "0", "0", "16", "+16")),
                    row("洛佩斯", "C", listOf("20", "3-6", "0-0", "0-0", "1", "5", "6", "1", "3", "0", "2", "1", "6", "+4")),
                ),
            ),
        ),
    )

    /** 右侧球队：威尔逊 21 分 / 米勒 11 分 · 11 篮板 */
    private val home = NbaTeamStats(
        "公牛",
        listOf(
            NbaTableSection(
                "首发", columns,
                listOf(
                    row("威尔逊", "F", listOf("23", "7-17", "1-5", "6-8", "2", "2", "4", "0", "1", "0", "3", "0", "21", "-18")),
                    row("米勒", "F", listOf("21", "4-9", "2-4", "1-2", "1", "10", "11", "2", "0", "1", "1", "2", "11", "-6")),
                ),
            ),
        ),
    )

    // ---------- ① 列映射 ----------

    @Test
    fun columnLookup_matchesHeaderOrder() {
        val jerome = away.sections[0].rows[0]
        assertEquals("16", nbaCellOf(jerome, columns, "得分"))
        assertEquals("1", nbaCellOf(jerome, columns, "篮板"))
        assertEquals("4", nbaCellOf(jerome, columns, "助攻"))
        assertEquals("5-8", nbaCellOf(jerome, columns, "投篮"))
        assertEquals("0", nbaCellOf(jerome, columns, "封盖"))
        assertEquals("+16", nbaCellOf(jerome, columns, "+/-"))
        // 不存在的列 → 空串（不崩、不越界）
        assertEquals("", nbaCellOf(jerome, columns, "不存在的列"))
    }

    // ---------- ② 数值解析 ----------

    @Test
    fun numParsesSignedValues() {
        assertEquals(12.0, nbaNum("12")!!, 0.0)
        assertEquals(15.0, nbaNum("+15")!!, 0.0)
        assertEquals(-3.0, nbaNum("-3")!!, 0.0)
        assertEquals(-18.0, nbaNum("-18")!!, 0.0)
        assertNull(nbaNum(""))
        assertNull(nbaNum("G"))
    }

    @Test
    fun shootingParsesMadeAttemptedAndPct() {
        assertEquals(5 to 8, nbaMadeAttempted("5-8"))
        assertEquals(0 to 0, nbaMadeAttempted("0-0"))
        assertNull(nbaMadeAttempted("12"))   // 单值列（得分/篮板…）不参与命中率解析
        assertEquals(62.5, nbaShootingPct("5-8")!!, 0.01)
        assertEquals(41.18, nbaShootingPct("7-17")!!, 0.01)
        // 出手 0 → 无法算命中率
        assertNull(nbaShootingPct("0-0"))
    }

    // ---------- ③ 头名选取 ----------

    @Test
    fun topScorerPicksHighestScorer() {
        assertEquals("杰罗姆", nbaTopScorer(away))
        assertEquals("威尔逊", nbaTopScorer(home))
    }

    @Test
    fun statLeaderPicksPerTeamBest() {
        val rebounds = NbaKingSpecs.first { it.label == "篮板" }
        assertEquals("洛佩斯", nbaStatLeader(away, rebounds)!!.first)
        assertEquals("米勒", nbaStatLeader(home, rebounds)!!.first)

        // 三分只显示「命中数」，不做成 "1-5" 这种原始串
        val threes = NbaKingSpecs.first { it.label == "三分" }
        assertEquals("米勒", nbaStatLeader(home, threes)!!.first)
        assertEquals("2", nbaStatLeader(home, threes)!!.second)

        assertEquals("威尔逊", nbaStatLeader(home, NbaKingSpecs.first { it.label == "得分" })!!.first)
    }

    // ---------- ④ 投篮项按命中率比大小 ----------

    @Test
    fun pkShootingComparesByPercentageNotVolume() {
        val shot = NbaPkSpecs.first { it.label == "投篮" }
        val jerome = nbaCellOf(away.sections[0].rows[0], columns, "投篮")   // 5-8
        val wilson = nbaCellOf(home.sections[0].rows[0], columns, "投篮")   // 7-17
        // 命中数 5 < 7，但命中率 62.5% > 41.2% → 左侧被判为占优
        assertTrue(shot.score(jerome)!! > shot.score(wilson)!!)
    }

    // ---------- ⑤ 柱状条口径 ----------

    /** 计数类：两值相加，各侧画「自己 / 二者之和」——两根条的长度比 = 两数值之比 */
    @Test
    fun shareBars_sumThenEachSideShare() {
        val (l, r) = nbaBarFractions(NbaBarMode.SHARE, 16.0, 21.0)
        assertEquals(0.432f, l, 0.01f)   // 16 / 37
        assertEquals(0.568f, r, 0.01f)   // 21 / 37
        assertEquals(1f, l + r, 0.001f)  // 两侧之和恒为「半幅」
    }

    @Test
    fun shareBars_handlesZeroTieAndEmpty() {
        // 一边为 0 → 本侧吃满本侧半条，另一侧空
        assertEquals(1.0f to 0.0f, nbaBarFractions(NbaBarMode.SHARE, 4.0, 0.0))
        assertEquals(0.0f to 1.0f, nbaBarFractions(NbaBarMode.SHARE, 0.0, 4.0))
        // 并列 → 各占一半
        assertEquals(0.5f to 0.5f, nbaBarFractions(NbaBarMode.SHARE, 5.0, 5.0))
        // 两人都是 0 → 两根条都不画（各画半条会被误读成「并列且占满」）
        assertEquals(0.0f to 0.0f, nbaBarFractions(NbaBarMode.SHARE, 0.0, 0.0))
        // 两侧都拿不到值 → 同样不画
        assertEquals(0.0f to 0.0f, nbaBarFractions(NbaBarMode.SHARE, null, null))
    }

    /** 投篮类：各侧独立，只取决于自己命中数 ÷ 自己出手数 */
    @Test
    fun pctBars_useOwnPercentageIndependently() {
        val (l, r) = nbaBarFractions(NbaBarMode.PCT, 62.5, 41.18)
        assertEquals(0.625f, l, 0.001f)
        assertEquals(0.4118f, r, 0.001f)
        // 对手的值变化不影响本侧长度
        assertEquals(0.625f, nbaBarFractions(NbaBarMode.PCT, 62.5, 0.0).first, 0.001f)
        // 超出 0..100 的异常值被夹紧
        assertEquals(1.0f, nbaBarFractions(NbaBarMode.PCT, 150.0, -20.0).first, 0.001f)
        assertEquals(0.0f, nbaBarFractions(NbaBarMode.PCT, 150.0, -20.0).second, 0.001f)
    }

    /** 规格表：+/- 已移除；投篮类走 PCT，计数类走 SHARE */
    @Test
    fun pkSpecs_dropPlusMinus_andTagBarModes() {
        assertEquals(listOf("得分", "篮板", "助攻", "抢断", "封盖", "失误", "投篮", "三分", "罚球"), NbaPkSpecs.map { it.label })
        NbaPkSpecs.forEach { spec ->
            val expected = if (spec.label in listOf("投篮", "三分", "罚球")) NbaBarMode.PCT else NbaBarMode.SHARE
            assertEquals("${spec.label} 的柱状条口径", expected, spec.bar)
        }
    }

    // ---------- ⑥ 比赛身份指纹（1.201：修「数据更新时整页闪骨架」） ----------

    /**
     * 直播轮询每 30s 会拿新的 query 实例（比分、live 都可能变）。
     * 指纹**只认「哪一场」**——否则每次进球都会被判成「换了一场」，状态被清空重解析、整页闪骨架。
     */
    @Test
    fun statsKey_ignoresLiveScoreButFollowsMatchIdentity() {
        val a = NbaStatsQuery(
            date = "2026-01-02",
            home = "火箭",
            away = "独行侠",
            homeScore = "100",
            awayScore = "98",
            live = true,
        )
        // 比分跳动 / live 翻转 → 同一场，指纹不变（不重置状态）
        assertEquals(nbaStatsKey(a), nbaStatsKey(a.copy(homeScore = "108", awayScore = "105")))
        assertEquals(nbaStatsKey(a), nbaStatsKey(a.copy(live = false)))
        // 换队或换日期（=真正换一场）→ 指纹必须变，否则会拿上一场的数据渲染
        assertTrue(nbaStatsKey(a) != nbaStatsKey(a.copy(away = "湖人")))
        assertTrue(nbaStatsKey(a) != nbaStatsKey(a.copy(date = "2026-01-03")))
        // 非 NBA（无 query）
        assertNull(nbaStatsKey(null))
    }

    // ---------- ⑦ 球队统计（1.224h） ----------

    /** 表尾「统计」行：两队合计（列序与球员表一致） */
    private val awayTotals = NbaTeamStats(
        "灰熊",
        emptyList(),
        summaries = listOf(
            row("统计", "", listOf("240", "26-59", "8-24", "15-20", "5", "20", "25", "22", "10", "4", "6", "4", "80", "+10")),
            row("命中率", "", listOf("", "44.1%", "33.3%", "75.0%", "", "", "", "", "", "", "", "", "", "")),
        ),
    )

    @Test
    fun teamTotals_picksStatRowNotPctRow() {
        val r = nbaTeamTotalsRow(awayTotals)!!
        assertEquals("统计", r.name)
        // 不能误取「命中率」行（它的得分格是空的）
        assertEquals("80", nbaCellOf(r, columns, "得分"))
    }

    @Test
    fun teamTotals_missingRowReturnsNull() {
        assertNull(nbaTeamTotalsRow(NbaTeamStats("灰熊", emptyList())))
        assertNull(nbaTeamTotalsRow(null))
    }

    @Test
    fun teamSpecs_countsAndPercentDisplay() {
        val r = nbaTeamTotalsRow(awayTotals)!!
        fun cell(col: String) = nbaCellOf(r, columns, col)
        assertEquals(80.0, nbaNum(cell("得分"))!!, 0.0)
        assertEquals(25.0, nbaNum(cell("篮板"))!!, 0.0)
        assertEquals(22.0, nbaNum(cell("助攻"))!!, 0.0)
        // 命中率：柱长/胜负按命中率算，展示为「xx.x%(命中-出手)」
        val fg = NbaTeamSpecs.first { it.label == "投篮命中率" }
        assertEquals(NbaBarMode.PCT, fg.bar)
        assertEquals(26 * 100.0 / 59, fg.score(cell("投篮"))!!, 0.001)
        assertEquals("44.1%(26-59)", fg.display(cell("投篮")))
        assertEquals("33.3%(8-24)", NbaTeamSpecs.first { it.label == "三分命中率" }.display(cell("3分")))
        assertEquals("75.0%(15-20)", NbaTeamSpecs.first { it.label == "罚球命中率" }.display(cell("罚球")))
    }

    @Test
    fun pctDisplay_fallsBackToRawText() {
        assertEquals("0-0", nbaPctDisplay("0-0"))
        assertEquals("-", nbaPctDisplay("-"))
        assertEquals("", nbaPctDisplay(""))
        assertEquals("50.0%(5-10)", nbaPctDisplay("5-10"))
    }

    @Test
    fun teamSpecs_orderFollowsOfficialSection() {
        assertEquals(
            listOf("得分", "篮板", "助攻", "投篮命中率", "三分命中率", "罚球命中率", "抢断", "封盖", "失误", "犯规"),
            NbaTeamSpecs.map { it.label },
        )
    }
}