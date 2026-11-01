package com.java.myapplication

import com.java.myapplication.data.DqRankParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.291：足球数据（懂球帝）解析测试。
 *
 * 样本按真实接口的 JSON 结构手写（字段取自实测响应）：
 *  · 联赛：rounds[].content.data 直接是排名行；
 *  · 杯赛/分区：data[] 的每个元素自己是 {name, header, data}；
 *  · 淘汰赛轮次：data[] 里是 TeamA/TeamB 对阵 → 必须被跳过。
 */
class DqRankParserTest {

    /** 扁平表（中超/英超这种联赛） */
    private val flatStanding = """
    {"template":"team_point_ranking","content":{"rounds":[
      {"template":"team_point_ranking_regular","content":{
        "name":"联赛",
        "header":["球队","赛","胜","平","负","进/失","积分"],
        "data":[
          {"rank":"1","team_id":"1","team_name":"成都蓉城","matches_total":"29","matches_won":"15",
           "matches_draw":"7","matches_lost":"7","goals_pro":"51","goals_against":"35","points":"52"},
          {"rank":"2","team_id":"2","team_name":"大连英博","matches_total":"29","matches_won":"15",
           "matches_draw":"4","matches_lost":"10","goals_pro":"46","goals_against":"43","points":"49"}
        ]}}
    ]}}
    """.trimIndent()

    /** 分组表（欧洲杯小组赛）+ 淘汰赛轮次（应被跳过） */
    private val groupedStanding = """
    {"template":"team_point_ranking","content":{"rounds":[
      {"content":{"name":"决赛圈","data":[
        {"TeamA":{"id":"1","name":"德国"},"TeamB":{"id":"2","name":"法国"},"scores":[1,2],"winner":"2"},
        {"TeamA":{"id":"3","name":"西班牙"},"TeamB":{"id":"4","name":"意大利"},"scores":[3,0],"winner":"3"}
      ]}},
      {"content":{"name":"小组赛","data":[
        {"name":"A组","header":["球队","赛","胜","平","负","进/失","积分"],"data":[
          {"rank":"1","team_name":"德国","matches_total":"3","matches_won":"2","matches_draw":"1",
           "matches_lost":"0","goals_pro":"8","goals_against":"2","points":"7"},
          {"rank":"2","team_name":"瑞士","matches_total":"3","matches_won":"1","matches_draw":"2",
           "matches_lost":"0","goals_pro":"5","goals_against":"3","points":"5"}
        ]},
        {"name":"B组","header":["球队","赛","胜","平","负","进/失","积分"],"data":[
          {"rank":"1","team_name":"西班牙","matches_total":"3","matches_won":"3","matches_draw":"0",
           "matches_lost":"0","goals_pro":"9","goals_against":"1","points":"9"}
        ]}
      ]}}
    ]}}
    """.trimIndent()

    /** 球员榜 */
    private val personRanking = """
    {"template":"person_ranking","content":{"header":["球员","球队","进球"],"data":[
      {"rank":"1","count":"21","person_name":"卡迪斯","team_name":"武汉三镇"},
      {"rank":"2","count":"21(1)","person_name":"奥斯卡","team_name":"云南玉昆"}
    ]}}
    """.trimIndent()

    /** 球队榜 */
    private val teamRanking = """
    {"template":"team_statistic_ranking","content":{"header":["球队","进球"],"data":[
      {"count":"63","rank":"1","team_name":"上海申花"},
      {"count":"57","rank":"2","team_name":"北京国安"}
    ]}}
    """.trimIndent()

    @Test
    fun `扁平积分榜 表头补排名 列按标签映射`() {
        val tables = DqRankParser.parseStanding(flatStanding)
        assertEquals(1, tables.size)
        val t = tables[0]
        // 「联赛」这种通用轮次名不作为标题
        assertEquals("", t.title)
        assertEquals(listOf("排名", "球队", "赛", "胜", "平", "负", "进/失", "积分"), t.headers)
        assertEquals(2, t.rows.size)
        assertEquals(
            listOf("1", "成都蓉城", "29", "15", "7", "7", "51/35", "52"),
            t.rows[0],
        )
        assertEquals(listOf("2", "大连英博", "29", "15", "4", "10", "46/43", "49"), t.rows[1])
    }

    @Test
    fun `分组积分榜 每组一张表 淘汰赛走对阵卡`() {
        val section = DqRankParser.parseStandingSection(groupedStanding)
        // 名次表只有 A/B 两个小组（淘汰赛不进表格）
        assertEquals(2, section.tables.size)
        assertEquals("小组赛 A组", section.tables[0].title)
        assertEquals("小组赛 B组", section.tables[1].title)
        assertEquals("德国", section.tables[0].rows[0][1])
        assertTrue(section.tables.all { it.headers.first() == "排名" })
        // 淘汰赛轮次 → 一个阶段（样本里两场都没有 round_id → 各自成组）
        assertTrue(section.stages.isNotEmpty())
    }

    @Test
    fun `球员榜 表头与行长度一致`() {
        val tables = DqRankParser.parsePersonRanking(personRanking)
        assertEquals(1, tables.size)
        val t = tables[0]
        assertEquals(listOf("排名", "球员", "球队", "进球"), t.headers)
        assertEquals(listOf("1", "卡迪斯", "武汉三镇", "21"), t.rows[0])
        // 带括号的进球数（进球(点球)）原样保留
        assertEquals("21(1)", t.rows[1][3])
        assertTrue(t.rows.all { it.size == t.headers.size })
    }

    @Test
    fun `球队榜 指标名取官方表头最后一列`() {
        val tables = DqRankParser.parseTeamRanking(teamRanking)
        assertEquals(1, tables.size)
        assertEquals(listOf("排名", "球队", "进球"), tables[0].headers)
        assertEquals(listOf("1", "上海申花", "63"), tables[0].rows[0])
    }


    /** 淘汰赛：bracket 节点（TeamA/TeamB + matches，round_id 区分阶段） */
    private val bracketStanding = """
    {"content":{"rounds":[
      {"content":{"name":"淘汰赛","display":"","data":[
        {"TeamA":{"id":"1","name":"西班牙"},"TeamB":{"id":"2","name":"英格兰"},
         "total_score":"2 - 1",
         "matches":[{"round_id":"101","fs_A":"2","fs_B":"1","ps_A":"","ps_B":""}]},
        {"TeamA":{"id":"3","name":"法国"},"TeamB":{"id":"4","name":"德国"},
         "total_score":"1 - 1",
         "matches":[{"round_id":"100","fs_A":"1","fs_B":"1","ps_A":"3","ps_B":"4"}]},
        {"TeamA":{"id":"5","name":"巴西"},"TeamB":{"id":"6","name":"阿根廷"},
         "total_score":"3 - 3",
         "matches":[{"round_id":"100","fs_A":"2","fs_B":"0","ps_A":"","ps_B":""},
                    {"round_id":"100","fs_A":"1","fs_B":"3","ps_A":"","ps_B":""}]}
      ]}},
      {"content":{"name":"三四名决赛","data":[
        {"match_id":"9","team_A_name":"加拿大","team_B_name":"乌拉圭",
         "fs_A":"2","fs_B":"2","ps_A":"3","ps_B":"4"}
      ]}}
    ]}}
    """.trimIndent()

    /** 淘汰赛按阶段分开，且比分/点球/两回合都正确 */
    @Test
    fun `淘汰赛按阶段分组 决赛在前 比分与点球正确`() {
        val section = DqRankParser.parseStandingSection(bracketStanding)
        // 淘汰赛不进表格
        assertEquals(0, section.tables.size)
        assertEquals(3, section.stages.size)

        val final = section.stages[0]
        assertEquals("决赛", final.title)
        assertEquals(1, final.matches.size)
        assertEquals("西班牙", final.matches[0].home)
        assertEquals("2 - 1", final.matches[0].score)
        assertTrue(final.matches[0].homeWin)

        val semi = section.stages[1]
        assertEquals("半决赛", semi.title)
        assertEquals(2, semi.matches.size)
        // 点球另附括号
        assertEquals("1 - 1 (3:4)", semi.matches[0].score)
        assertTrue(semi.matches[0].awayWin)
        // 两回合用 / 连接
        assertEquals("2 - 0 / 1 - 3", semi.matches[1].score)

        val third = section.stages[2]
        assertEquals("三四名决赛", third.title)
        assertEquals("2 - 2 (3:4)", third.matches[0].score)
    }

    @Test
    fun `脏数据不崩 返回空`() {
        assertEquals(0, DqRankParser.parseStanding("not json").size)
        assertEquals(0, DqRankParser.parsePersonRanking("{}").size)
        assertEquals(0, DqRankParser.parseTeamRanking("""{"content":{"data":[]}}""").size)
    }
}