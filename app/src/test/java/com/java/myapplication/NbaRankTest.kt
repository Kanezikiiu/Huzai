package com.java.myapplication

import com.java.myapplication.data.NbaRankParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.258：NBA 排行页解析（结构取自 nba.hupu.com 的真实表格）。
 * 只测**纯解析**，网络与 UI 靠真机。
 */
class NbaRankTest {

    // ---------- 战绩（东西部 / 赛区）：分区块 ----------
    private val standings = """
        <table class="players_table"><tbody>
          <tr class="linglei"><td colspan="14" class="left">东部</td></tr>
          <tr class="color_font1 bg_a">
            <td>排名</td><td class="left">队名</td><td>胜</td><td>负</td><td>胜率</td><td>胜场差</td>
            <td>主场</td><td>客场</td><td>赛区</td><td>东部</td><td>得分</td><td>失分</td><td>净胜</td><td>连胜/负</td>
          </tr>
          <tr><td><span class="rank_bg">1</span></td><td class="left"><a href="/teams/pistons">活塞</a></td>
              <td>5</td><td>1</td><td>83.3%</td><td>0.0</td><td>2-0</td><td>3-1</td><td>0-0</td><td>5-1</td>
              <td>109.8</td><td>101.8</td><td>8.0</td><td>2连胜</td></tr>
          <tr class="linglei"><td colspan="14" class="left">西部</td></tr>
          <tr class="color_font1 bg_a">
            <td>排名</td><td class="left">队名</td><td>胜</td><td>负</td><td>胜率</td><td>胜场差</td>
            <td>主场</td><td>客场</td><td>赛区</td><td>东部</td><td>得分</td><td>失分</td><td>净胜</td><td>连胜/负</td>
          </tr>
          <tr><td>1</td><td class="left">雷霆</td><td>6</td><td>0</td><td>100%</td><td>0.0</td>
              <td>3-0</td><td>3-0</td><td>0-0</td><td>0-0</td><td>120.1</td><td>105.2</td><td>14.9</td><td>6连胜</td></tr>
        </tbody></table>
    """.trimIndent()

    @Test
    fun `战绩页解析出东西部两张表`() {
        val tables = NbaRankParser.parseBlockGrouped(standings)
        assertEquals(2, tables.size)
        assertEquals("东部", tables[0].title)
        assertEquals("西部", tables[1].title)
        assertEquals(14, tables[0].headers.size)
        assertEquals("队名", tables[0].headers[1])
        assertEquals(1, tables[0].rows.size)
        assertEquals("活塞", tables[0].rows[0][1])
        assertEquals("雷霆", tables[1].rows[0][1])
    }

    // ---------- 球队：双层表头（20 列） ----------
    private val teams = """
        <table class="players_table"><tbody>
          <tr><td></td><td>投篮</td><td>三分</td><td>罚球</td><td>篮板</td><td>助攻</td>
              <td>失误</td><td>抢断</td><td>盖帽</td><td>犯规</td><td>得分</td></tr>
          <tr class="color_font1 bg_a"><td>排名</td><td>球队</td><td>命中率</td><td>命中</td><td>出手</td>
              <td>命中率</td><td>命中</td><td>出手</td><td>命中率</td><td>命中</td><td>出手</td>
              <td>总篮板</td><td>进攻</td><td>防守</td>
              <td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td></tr>
          <tr><td>1</td><td>开拓者</td><td>48.5%</td><td>43.5</td><td>89.8</td><td>35.0%</td><td>15.8</td><td>45.0</td>
              <td>69.6%</td><td>20.0</td><td>28.8</td><td>46.0</td><td>12.3</td><td>33.8</td>
              <td>28.8</td><td>14.00</td><td>7.50</td><td>4.75</td><td>19.00</td><td>122.8</td></tr>
        </tbody></table>
    """.trimIndent()

    @Test
    fun `球队页双层表头合并为可读列名`() {
        val t = NbaRankParser.parseSingle(teams)!!
        assertEquals(20, t.headers.size)
        assertEquals("排名", t.headers[0])
        assertEquals("球队", t.headers[1])
        // 投篮 / 三分 / 罚球 各三列
        assertEquals("投篮命中率", t.headers[2])
        assertEquals("投篮命中", t.headers[3])
        assertEquals("投篮出手", t.headers[4])
        assertEquals("三分命中率", t.headers[5])
        assertEquals("罚球命中率", t.headers[8])
        // 篮板组用子名（总篮板/进攻/防守 已自明）
        assertEquals("总篮板", t.headers[11])
        assertEquals("进攻", t.headers[12])
        // 后半段单列组
        assertEquals("助攻", t.headers[14])
        assertEquals("得分", t.headers[19])
        assertEquals(1, t.rows.size)
        assertEquals(20, t.rows[0].size)
    }

    // ---------- 球员：列数随指标变化（以助攻页为例，8 列） ----------
    private val playersAsts = """
        <table class="players_table"><tbody>
          <tr class="color_font1 bg_a"><td>排名</td><td class="left">球员</td><td>球队</td>
              <td>助攻数</td><td>失误数</td><td>助攻/失误</td><td>场次</td><td>上场时间</td></tr>
          <tr><td>1</td><td class="left">哈利伯顿</td><td>步行者</td>
              <td class="bg_b">11.20</td><td>2.10</td><td>5.33</td><td>5</td><td>34.20</td></tr>
        </tbody></table>
    """.trimIndent()

    @Test
    fun `球员页按表头解析（列数随指标变化）`() {
        val t = NbaRankParser.parseSingle(playersAsts)!!
        assertEquals(8, t.headers.size)
        assertEquals("球员", t.headers[1])
        assertEquals("助攻数", t.headers[3])
        assertEquals(1, t.rows.size)
        assertEquals("哈利伯顿", t.rows[0][1])
        assertEquals("11.20", t.rows[0][3])
    }

    @Test
    fun `数值化支持百分比与命中-出手`() {
        assertEquals(47.5, NbaRankParser.numeric("47.5%"), 0.001)
        assertEquals(11.2, NbaRankParser.numeric("11.20-23.60"), 0.001)
        assertTrue(NbaRankParser.numeric("连胜") < 0)
    }

    @Test
    fun `空表不崩`() {
        assertTrue(NbaRankParser.parseBlockGrouped("<table></table>").isEmpty())
        assertTrue(NbaRankParser.parseSingle("<table></table>") == null)
    }
}