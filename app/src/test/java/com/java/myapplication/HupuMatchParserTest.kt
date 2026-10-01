package com.java.myapplication.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 评分树结构分型单测：使用 /tmp/hupu9 取证产物（真实接口响应）验证。
 * 若环境无该文件则跳过（不判失败）。
 */
class HupuMatchParserTest {

    private fun sample(name: String): String? =
        File("/tmp/hupu9", name).takeIf { it.exists() }?.readText()

    @Test
    fun `cs2 three-layer tree is not misjudged as flat`() {
        // CS2: 比赛(common_sports_first) → 地图(common_sports_second, 带 subNodes=选手) 三层。
        // 旧逻辑按后缀 _second 判 flat，导致地图被当选手直列、无法下钻。
        val json = sample("cs2-tree.json") ?: return
        val tree = HupuMatchParser.parseScoreTree(json)
        assertNotNull(tree)
        tree!!
        assertFalse("CS2 三层结构不应判 flat", tree.flat)
        assertEquals(3, tree.rounds.size)
        // API 倒序（data[0]=最后一局）→ parser 翻转为正序，first=最后抓的地图
        assertEquals("阿努比斯", tree.rounds.first().name)
        assertTrue(tree.rounds.map { it.name }.containsAll(listOf("死城之谜", "荒漠迷城", "阿努比斯")))
        assertTrue("地图层应带选手", tree.rounds.first().players.isNotEmpty())
    }

    @Test
    fun `wnba flat tree still detected as flat`() {
        // WNBA: children 直接是选手(common_sports_second, infoJson.type=player, 无子节点) → flat
        val json = sample("wnba-tree.json") ?: return
        val tree = HupuMatchParser.parseScoreTree(json)
        assertNotNull(tree)
        tree!!
        assertTrue("WNBA 扁平结构应判 flat", tree.flat)
        assertEquals(1, tree.rounds.size)
        assertTrue("选手应合成单一虚拟轮", tree.rounds.first().players.size >= 20)
    }

    @Test
    fun `flat tree with neutral roles coach referee still detected as flat`() {
        // 美国女篮 vs 中国女篮：26 个 children 里混有「教练：宫鲁鸣」「裁判」等无 infoJson.type
        // 字段的中立角色——叶子判定不能依赖 type=player，否则整场被误渲染成对局横滑条
        val json = sample("nbb-tree.json") ?: return
        val tree = HupuMatchParser.parseScoreTree(json)
        assertNotNull(tree)
        tree!!
        assertTrue("含中立角色的扁平结构应判 flat", tree.flat)
        assertEquals(1, tree.rounds.size)
        assertEquals(26, tree.rounds.first().players.size)
        assertTrue(tree.rounds.first().players.any { it.name.startsWith("教练") })
        assertTrue(tree.rounds.first().players.any { it.name == "裁判" })
    }

    @Test
    fun `multi-team placeholder members parsed as null teams`() {
        // 和平精英/绝地求生等整场多队赛事：againstInfo.memberInfos 是两个全空占位
        // （memberName/memberId/logo 全空）→ 必须转 null，让赛程卡走「赛事名卡」形态
        val json = sample("pubgm-sched.json") ?: return
        val days = HupuMatchParser.parseSchedule(json)
        assertTrue("pubgmobile 赛程应可解析", days.isNotEmpty())
        val m = days.first().matches.first()
        assertNull("多队赛事的空占位不应生成 home 队伍", m.home)
        assertNull("多队赛事的空占位不应生成 away 队伍", m.away)
    }

    @Test
    fun `one-vs-one matches keep both teams`() {
        // 对照：CBA 正常 1v1 对局的双方队伍信息保留不受影响
        val json = sample("cba-sched.json") ?: return
        val days = HupuMatchParser.parseSchedule(json)
        assertTrue("CBA 赛程应可解析", days.isNotEmpty())
        val m = days.flatMap { it.matches }.first { it.home != null && it.away != null }
        assertTrue(m.home!!.name.isNotBlank())
        assertTrue(m.away!!.name.isNotBlank())
    }

    @Test
    fun `json-null memberName is treated as placeholder not string-null`() {
        // Android org.json 的 optString 会把 JSON null 值强转成字符串 "null" 返回，
        // 导致 "null".isBlank()==false、空占位过滤失效 → 赛程卡渲染出四个 "null" 字样。
        // 用 JSONObject 手工构造 null 值成员，确保 clean() 修复覆盖该形态。
        val json = """
            {"success":true,"result":{"dayGameData":[{
                "dayTime":"2026-01-01","dateBlock":"01月01日",
                "matchData":[{
                    "matchId":"999001",
                    "matchStatusDesc":"未开赛",
                    "matchStatus":"0",
                    "matchIntroduction":"和平精英PEL春季赛",
                    "matchStartTimeStamp":"1767225600000",
                    "againstInfo":{"memberInfos":[
                        {"memberName":null,"memberId":null,"memberLogo":null,
                         "memberBaseScore":null,"memberExtraScore":null,"memberBigScore":null},
                        {"memberName":null,"memberId":null,"memberLogo":null,
                         "memberBaseScore":null,"memberExtraScore":null,"memberBigScore":null}
                    ]}
                }]
            }]}}
        """.trimIndent()
        val days = HupuMatchParser.parseSchedule(json)
        assertTrue("JSON-null 占位赛程应可解析", days.isNotEmpty())
        val m = days.first().matches.first()
        assertNull("JSON-null 占位成员不应生成 home", m.home)
        assertNull("JSON-null 占位成员不应生成 away", m.away)
    }

    @Test
    fun `lol moba tree keeps three layers`() {
        val json = sample("lol-tree.json") ?: return
        val tree = HupuMatchParser.parseScoreTree(json)
        assertNotNull(tree)
        tree!!
        assertFalse(tree.flat)
        assertTrue(tree.rounds.isNotEmpty())
        assertTrue(tree.rounds.first().players.isNotEmpty())
    }
    @Test
    fun `subcomment list parses nested grandchild replies`() {
        val json = sample("g3bin-2959677635-brightest.json") ?: return
        val st = HupuMatchParser.parseSubComments(json)
        assertNotNull("真实 G3Bin 楼中楼响应应可解析", st)
        st!!
        assertEquals("G3Bin 楼中楼子回复应为多条（实时增长，>10）", true, st.comments.size > 10)
        // 「刚子没问题啊」(id=2959884697) descendantCount=1，内嵌孙评论「刚子没问题，队友geng」
        val chong = st.comments.first { it.commentId == "2959884697" }
        assertEquals("descendantCount 应解析", 1, chong.descendantCount)
        assertEquals("孙评论应递归解析到 subComments", 1, chong.subComments.size)
        assertEquals("孙评论 parentUser 语义（虎扑JR0457539542 的回复）",
            "刚子没问题，队友geng", chong.subComments.first().content)
        assertEquals("孙评论用户名", "虎扑JR0457539542", chong.subComments.first().userName)
        // 官方「全部回复 N」= subCommentCount 本身（direct 子回复数；descendantCount 是后代总数元数据，不相加）
        val chovy = sample("chovy-hottest.json")
        if (chovy != null) {
            val list = HupuMatchParser.parseHottestComments(chovy)
            val g3 = list.first { it.commentId == "2959677635" }
            assertTrue("母评论 subCommentCount 应为正数（比赛进行中实时增长，不硬编码）",
                g3.subCommentCount > 0)
            assertTrue("descendantCount 应独立解析（后代总数）", g3.descendantCount > 0)
        }
    }

    @Test
    fun `grandchild expand parses direct layer and deep descendants`() {
        // 样本：lol_item/67128 子评论 2760070094（desc=3）。
        // 楼中楼 sheet 内嵌形态：subCommentList 是扁平数组（direct 孙 + 曾孙 + 玄孙平级，
        // 层级只靠 parentCommentId 区分）——初始展示只取 direct 层（官方语义：孙评论只显示一条）
        val json = sample("grandinline-2760070094.json") ?: return
        val st = HupuMatchParser.parseSubComments(json)
        assertNotNull("楼中楼内嵌形态应可解析", st)
        st!!
        assertEquals(1, st.comments.size)
        val sub = st.comments.first()
        assertEquals("descendantCount=3（direct 孙 1 + 曾孙 1 + 玄孙 1）", 3, sub.descendantCount)
        assertEquals("内嵌扁平后代数组 3 条（含深层平级）", 3, sub.subComments.size)
        val direct = sub.subComments.filter { it.parentCommentId == sub.commentId }
        assertEquals("初始只显示 direct 层（官方：孙评论只显示一条）", 1, direct.size)
        assertEquals("direct 孙 parentCommentId 为宿主子评论", "2760070094", direct.first().parentCommentId)
        // 展开形态：孙评论端点（subCommentList?parentCommentId=子评论id）只返回 direct 孙，
        // 深层内嵌在 direct 孙自己的 subCommentList 里（可递归拉平）——「展开更多回复」数据源
        val ej = sample("grand-2760070094.json") ?: return
        val est = HupuMatchParser.parseSubComments(ej)
        assertNotNull("孙评论端点响应应可解析", est)
        est!!
        assertEquals("端点只返回 direct 孙", 1, est.comments.size)
        assertEquals("端点 direct 孙 desc=2（曾孙+玄孙在其内嵌树）", 2, est.comments.first().descendantCount)
        val flat = est.comments.flatMap { flattenWithDescendants(it) }
        assertEquals("端点拉平含全后代 3 条", 3, flat.size)
        assertTrue("拉平含曾孙 2761489141", flat.any { it.commentId == "2761489141" })
        assertTrue("拉平含玄孙 2761354945", flat.any { it.commentId == "2761354945" })
        // 合并展示：inline direct + 端点拉平，按 commentId 去重
        val merged = (direct + flat).distinctBy { it.commentId }
        assertEquals("展开后全后代 3 条显示", 3, merged.size)
        assertTrue("显示数达成 descendantCount → 展开按钮退场", merged.size >= sub.descendantCount)
        // 深层条目的回复对象解析（UI「回复 @作者」前缀数据源）
        val deep = merged.first { it.commentId == "2761354945" }
        assertEquals("玄孙回复对象为曾孙", "2761489141", deep.parentCommentId)
    }

    /**
     * 1.163: 评分评论作者 id。
     * 官方 JSON 的 commentUserId 是数字型用户 id，点作者头像/昵称进用户主页要用它当 euid
     * （与帖子回复「无 euid 时用数字 puid 顶替」同一条既有约定）。
     */
    @Test
    fun `score comment carries numeric author id for profile jump`() {
        val json = sample("score-comments-sample.json") ?: return
        val st = HupuMatchParser.parseComments(json)
        assertNotNull("评分评论样本应可解析", st)
        st!!
        assertTrue("样本应有评论", st.comments.isNotEmpty())
        val c = st.comments.first()
        assertTrue("commentUserId 应映射到 userId", c.userId.isNotBlank())
        assertTrue("userId 应为数字型（可直接当 euid 用）", c.userId.all { it.isDigit() })
    }

    /**
     * 1.196: 国际足球赛程（m.hupu.com/soccer/schedule 的 Next.js SSR）。
     * 该页与 match-api 不同源，单独解析；验证：按天分组、状态映射、
     * 比分只在开赛后出现、只有开赛后挂 football_match 类型（可点）。
     */
    @Test
    fun `soccer schedule parsed from next data with status and score gating`() {
        val html = """
            <html><body>
            <script id="__NEXT_DATA__" type="application/json">
            {"props":{"pageProps":{"data":{"games":[
              {"day":"20260925","date_block":"9月25日 周五","data":[
                {"currentMatchId":"3588547","title":"欧国联第1轮",
                 "home":{"teamId":7032,"name":"挪威","logo":"https://x/1.png"},
                 "away":{"teamId":7024,"name":"丹麦","logo":"https://x/2.png"},
                 "home_score":3,"away_score":2,"begin_time":1790275500,
                 "status":{"id":4,"txt":"已结束"},"pv":"8829评分"},
                {"currentMatchId":"3589648","title":"欧国联第2轮",
                 "home":{"teamId":7032,"name":"挪威","logo":null},
                 "away":{"teamId":7024,"name":"丹麦","logo":null},
                 "home_score":0,"away_score":0,"begin_time":1790361900,
                 "status":{"id":1,"txt":"未开始"},"pv":null},
                {"currentMatchId":"3921240","title":"欧国联第3轮",
                 "home":{"teamId":7001,"name":"日本","logo":null},
                 "away":{"teamId":7002,"name":"厄瓜多尔","logo":null},
                 "home_score":1,"away_score":1,"begin_time":1790361900,
                 "status":{"id":2,"txt":"中场"},"pv":null}
              ]},
              {"day":"20260926","date_block":"9月26日 周六","data":[]}
            ]}}}}
            </script></body></html>
        """.trimIndent()
        val days = HupuMatchParser.parseSoccerSchedule(html)
        assertEquals("空天不应产出分组", 1, days.size)
        val day = days.first()
        assertEquals("2026-09-25", day.dayTime) // 与 ScheduleList 判今天格式一致
        assertEquals("9月25日 周五", day.dateBlock)
        assertEquals(3, day.matches.size)

        val done = day.matches.first { it.matchId == "3588547" }
        assertEquals("已结束", done.statusDesc)
        assertEquals("COMPLETED", done.status)
        assertEquals("挪威", done.home?.name)
        assertEquals("丹麦", done.away?.name)
        assertEquals("3", done.home?.baseScore)
        assertEquals("2", done.away?.baseScore)
        assertEquals("1790275500000", done.startTimestamp.toString()) // 秒 → 毫秒
        assertEquals("8829评分", done.scoreCountText)
        assertEquals(HupuMatchApi.FOOTBALL_MATCH, done.scoreBizType) // 开赛后 → 可点、换取钥匙
        assertNull("赛程无 outBizNo", done.scoreBizNo)
        assertEquals("7032", done.winnerMemberId) // 3:2 → 主队赢

        val upcoming = day.matches.first { it.matchId == "3589648" }
        assertEquals("未开始", upcoming.statusDesc)
        assertEquals("NOTSTARTED", upcoming.status) // 与 match-api 同款（无下划线）
        assertNull("未开始不挂类型 → 卡片不可点", upcoming.scoreBizType)
        assertEquals("未开赛比分用 - 占位（卡片显示 - : -，与英超一致）", "-", upcoming.home?.baseScore)
        assertEquals("-", upcoming.away?.baseScore)

        val live = day.matches.first { it.matchId == "3921240" }
        assertEquals("中场", live.statusDesc)
        assertEquals("INPROGRESS", live.status) // 进行中/中场 → 主题色标识
        assertEquals("1", live.home?.baseScore)
        assertEquals("1", live.away?.baseScore)
        assertEquals(HupuMatchApi.FOOTBALL_MATCH, live.scoreBizType)
    }

    /** 1.196: org.json 的 optString 会把 JSON null 变成字面量 "null"，赛程解析须清掉。 */
    @Test
    fun `soccer schedule cleans literal null from optString`() {
        val html = """
            <script id="__NEXT_DATA__" type="application/json">
            {"props":{"pageProps":{"data":{"games":[
              {"day":"20260925","date_block":"9月25日 周五","data":[
                {"currentMatchId":"1","title":"T",
                 "home":{"teamId":1,"name":"A","logo":null},
                 "away":{"teamId":2,"name":"B","logo":null},
                 "home_score":1,"away_score":0,"begin_time":1790275500,
                 "status":{"id":4,"txt":"已结束"},"pv":null}
              ]}
            ]}}}}
            </script>
        """.trimIndent()
        val days = HupuMatchParser.parseSoccerSchedule(html)
        assertEquals(1, days.size)
        val m = days.first().matches.first()
        assertNull("logo 为 null → 不应出现 \"null\" 字样", m.home?.logo)
        assertTrue("pv 为 null → 应清成空串", m.scoreCountText.isEmpty())
    }
}