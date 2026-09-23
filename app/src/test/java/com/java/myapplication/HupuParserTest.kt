package com.java.myapplication.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 解析器单测：使用探测阶段保存的真实 HTML（/tmp/hupu2）验证。
 * 若环境无该文件则跳过（不判失败）。
 */
class HupuParserTest {

    private val dir = File("/tmp/hupu2")
    private fun sample(name: String): String? =
        File(dir, name).takeIf { it.exists() }?.readText()

    private val dir5 = File("/tmp/hupu5")
    private fun sample5(name: String): String? =
        File(dir5, name).takeIf { it.exists() }?.readText()

    @Test
    fun `parse search page from real search html`() {
        val html = sample5("search.html") ?: return
        val p = HupuParser.parseSearchPage(html)
        assertNotNull(p)
        p!!
        assertEquals(20, p.items.size)
        assertEquals(50, p.totalPages)
        // 首条：[流言板]雷记调侃快船…（高亮标签已剥除）
        assertTrue(p.items.first().title.startsWith("[流言板]雷记调侃快船"))
        assertTrue("title should have no html tag, got ${p.items.first().title}", !p.items.first().title.contains("<"))
        // 字段映射
        val it0 = p.items.first()
        assertEquals("642247134", it0.tid)
        assertEquals(371, it0.replies)
        assertEquals("篮球资讯", it0.forumName)
        assertTrue(it0.picture != null)
        assertEquals("虎扑篮球资讯", it0.username)
        // 高亮剥除后正文也不含标签
        assertTrue(!it0.desc.contains("<font"))
    }

    @Test
    fun `parse board page from real all-gambia html`() {
        val html = sample("all1.html") ?: return
        val page = HupuParser.parseBoardPage(html)
        assertNotNull(page)
        page!!
        assertTrue("threads should be 70, got ${page.threads.size}", page.threads.size >= 60)
        val t = page.threads.first()
        assertTrue(t.tid.isNotEmpty())
        assertTrue(t.title.isNotEmpty())
        assertTrue("first tid should be 642228608, got ${t.tid}", t.tid == "642228608")
        assertEquals(50, t.lights)
        assertTrue("hot topics should exist", page.hotTopics.isNotEmpty())
    }

    @Test
    fun `parse categories from real html`() {
        val html = sample("all1.html") ?: return
        val cats = HupuParser.parseCategories(html)
        assertEquals(13, cats.size)
        val gambia = cats.first { it.name == "步行街" }
        assertTrue(gambia.topics.isNotEmpty())
        assertTrue(gambia.topics.any { it.name == "步行街主干道" })
    }

    @Test
    fun `parse topic page from real topic html`() {
        val html = sample("topic1.html") ?: return
        val page = HupuParser.parseTopicPage(html)
        assertNotNull(page)
        page!!
        assertEquals("足球话题区", page.topic.name)
        assertEquals(50, page.threads.size)
        assertEquals(1, page.page)
        assertTrue("total pages ~20, got ${page.totalPages}", page.totalPages in 10..30)
        assertEquals(3, page.sortTabs.size)
        assertTrue(page.threads.first().author != null)
    }

    @Test
    fun `parse thread detail from real detail html`() {
        val html = sample("detail.html") ?: return
        val d = HupuParser.parseThreadDetail(html)
        assertNotNull(d)
        d!!
        assertEquals(143, d.replyCount)
        assertEquals(8, d.replyTotalPages)
        assertEquals(20, d.replies.size)
        assertEquals(1, d.replyPage)
        // 第一条回复楼层 = 1
        assertEquals(1, d.replies.first().floor)
        assertTrue(d.replies.first().contentHtml.contains("img") || d.replies.first().contentHtml.isNotEmpty())
        assertTrue(d.thread.title.startsWith("讯飞丑闻"))
    }

    @Test
    fun `parse replies page 2 with correct floor offset`() {
        val html = sample("d2.html") ?: return
        val d = HupuParser.parseThreadDetail(html)
        assertNotNull(d)
        d!!
        assertEquals(2, d.replyPage)
        assertEquals(20, d.replies.size)
        assertEquals(21, d.replies.first().floor)
    }

    @Test
    fun `count -1 floor clamps to zero instead of showing reply -1`() {
        // 实测 642258234 楼 30155：服务端对未返回回复树的楼层标记 count=-1
        val html = File("/tmp/hupu6/t.html").takeIf { it.exists() }?.readText() ?: return
        val d = HupuParser.parseThreadDetail(html)
        assertNotNull(d)
        d!!
        assertTrue(d.replies.none { it.totalReplies < 0 })
    }

    @Test
    fun `balanced json extraction survives nested braces`() {
        val html = "<html><script>window.\$\$data={\"a\":{\"b\":\"包含}花括号\"}};</script>"
        val obj = HupuParser.extractDollarData(html)
        assertNotNull(obj)
        assertEquals("包含}花括号", obj!!.optJSONObject("a")!!.optString("b"))
    }

    @Test
    fun `low-activity thread detail (45KB) parses fully`() {
        // 真实样本：tid=642286755（replies=3、lights=0），HTML 仅 45920 字节——
        // 曾被 fetchHtml 的 <50KB 壳页保护误杀为「加载失败」（低互动帖子天然小体积）。
        // 修复后：小体积但含 __NEXT_DATA__ SSR 数据即放行。此测试验证该页 parser 全链路可解析。
        val html = File("/tmp/hupu9", "low-thread-642286755.html").takeIf { it.exists() }?.readText() ?: return
        assertTrue("样本 HTML 应小于 50KB（触发旧保护阈值的形态）", html.length < 50_000)
        assertTrue("样本应含 SSR 数据载体（修复的放行条件）", html.contains("__NEXT_DATA__"))
        val d = HupuParser.parseThreadDetail(html)
        assertNotNull("低互动帖子详情应可解析（不应因体积被拒）", d)
        val dd = d!!
        assertEquals("thread.tid 应正确解析", "642286755", dd.thread.tid)
        assertEquals("回复数应为 3", 3, dd.replyCount)
        assertEquals("首页应含 3 条回复", 3, dd.replies.size)
        assertTrue("回复楼层应为 1..3", dd.replies.map { it.floor } == listOf(1, 2, 3))
    }

    @Test
    fun `parse user profile from real mobile ssr html`() {
        val html = sample("user_profile.html") ?: return
        val p = HupuParser.parseUserProfile(html)
        assertNotNull(p)
        p!!
        assertTrue("euid (or puid fallback) should be parsed, got ${p.euid}", p.euid.isNotEmpty())
        assertTrue("nickname should be parsed", p.name.isNotEmpty())
        assertTrue("avatar should be parsed", p.avatar.isNotEmpty())
        assertTrue("threads should be 20 (official preview count), got ${p.threads.size}", p.threads.size == 20)
        assertTrue("replies should be 20 (official preview count), got ${p.replies.size}", p.replies.size == 20)
        assertTrue("stats should be non-negative", p.followers >= 0 && p.beRecommendCount >= 0)
        // threadList first item fields
        p.threads.first().let { t ->
            assertTrue(t.tid.isNotEmpty())
            assertTrue(t.title.isNotEmpty())
        }
        // replyList first item fields
        p.replies.first().let { r ->
            assertTrue(r.pid.isNotEmpty())
            assertTrue(r.tid.isNotEmpty())
        }
    }

    @Test
    fun `thread main post parses publish time and location`() {
        // 1.189: 主楼元信息行改为「发布时间 · 发布于地 · 浏览数」，两字段均取自
        // detail JSON 的 thread.createdAtFormat / thread.location（实测 2026-09-22）
        val html = """<html><head><script id="__NEXT_DATA__" type="application/json">""" +
            """{"props":{"pageProps":{"detail":{"thread":""" +
            """{"tid":"642545906","title":"t","createdAtFormat":"5小时前","location":"上海","read":123},""" +
            """"replies":{"current":1,"count":0,"total":1,"list":[]}}}}}</script></head></html>"""
        val d = HupuParser.parseThreadDetail(html)
        assertNotNull(d)
        assertEquals("5小时前", d!!.thread.createdAtText)
        assertEquals("上海", d.thread.location)
    }

    @Test
    fun `thread location defaults to empty when absent`() {
        // 部分帖子无发布地（实测 thread.location 为空串）→ 解析为空，UI 自动隐藏该段
        val html = """<html><head><script id="__NEXT_DATA__" type="application/json">""" +
            """{"props":{"pageProps":{"detail":{"thread":{"tid":"1","title":"t"},""" +
            """"replies":{"current":1,"count":0,"total":1,"list":[]}}}}}</script></head></html>"""
        val d = HupuParser.parseThreadDetail(html)
        assertNotNull(d)
        assertEquals("", d!!.thread.location)
        assertEquals("", d.thread.createdAtText)
    }

    @Test
    fun `user profile ip location falls back to location field`() {
        // 1.190: 移动版用户主页 SSR 的 userInfoData 里 location_str 常为空串，IP 属地在 location
        // （实测 m.hupu.com/user/119424238：location="山西" / location_str=""）
        // 此前只读 location_str → 网页有属地、App 不显示
        val html = """<html><head><script id="__NEXT_DATA__" type="application/json">""" +
            """{"props":{"pageProps":{"userInfoData":{"puid":"119424238","nickname":"t",""" +
            """"location":"山西","location_str":""},"threadList":[],"replyList":[]}}}""" +
            """</script></head></html>"""
        val p = HupuParser.parseUserProfile(html)
        assertNotNull(p)
        assertEquals("山西", p!!.locationStr)
    }

    @Test
    fun `user profile ip location prefers location_str when present`() {
        // location_str 有值时仍以它为准（保持旧行为不被回退覆盖）
        val html = """<html><head><script id="__NEXT_DATA__" type="application/json">""" +
            """{"props":{"pageProps":{"userInfoData":{"puid":"1","nickname":"t",""" +
            """"location":"北京","location_str":"上海"},"threadList":[],"replyList":[]}}}""" +
            """</script></head></html>"""
        val p = HupuParser.parseUserProfile(html)
        assertNotNull(p)
        assertEquals("上海", p!!.locationStr)
    }

    @Test
    fun `pc space user info parses reputation`() {
        // 1.190: PC 个人中心 getUserInfo 的 data.reputation 是对象（{detail_url, value}），
        // 此前未读取 → 用户主页「声望」恒为 0 而整段隐藏（网页有、App 没有）。
        // 实测 bbs.hupu.com/pcmapi/pc/space/v1/getUserInfo?euid=17261817455321 → value=35
        val json = """{"code":1,"internalCode":"PC000000","msg":"success","data":{""" +
            """"euid":17261817455321,"puid":119424238,"nickname":"t","header":"h",""" +
            """"location_str":"","location":"山西","reg_time_str":"加入虎扑6天",""" +
            """"reputation":{"detail_url":"https://x/310013","value":35},"be_light_count":7}}"""
        val p = HupuParser.parseSpaceUserInfo(json)
        assertNotNull(p)
        assertEquals(35L, p!!.reputation.toLong())
        // 顺带锁定同源字段：加入天数与 IP 属地（location_str 空 → 回退 location）
        assertEquals("加入虎扑6天", p.regTimeStr)
        assertEquals("山西", p.locationStr)
    }

    @Test
    fun `pc space user info reputation defaults to zero when absent`() {
        // 老账号/未下发 reputation 时不应崩，声望按 0 处理（UI 自动隐藏该段）
        val json = """{"code":1,"data":{"euid":1,"puid":1,"nickname":"t"}}"""
        val p = HupuParser.parseSpaceUserInfo(json)
        assertNotNull(p)
        assertEquals(0L, p!!.reputation.toLong())
    }

    @Test
    fun `cover parses url from object array`() {
        // 1.190: 实测 pics 是对象数组 [{url,width,height,is_gif,type}]；旧写法 optString(0)
        // 拿到的是整个对象的 toString（形如 {"url":…}，不是 URL）→ Coil 加载失败 →
        // 列表右侧一直留 84×60 空洞，把标题压窄导致「还有很大空间就换行」。
        val o = JSONObject(
            """{"tid":"1","pics":[{"url":"https://i11.hoopchina.com.cn/a.jpg",""" +
                """"width":1256,"height":686,"is_gif":0,"type":"common"}]}"""
        )
        assertEquals("https://i11.hoopchina.com.cn/a.jpg", HupuParser.coverFrom(o))
    }

    @Test
    fun `cover parses url from legacy string array`() {
        // 兼容旧的字符串数组形态，避免以后数据源回退时又看不到封面
        val o = JSONObject("""{"pics":["https://i1.hoopchina.com.cn/b.jpg"]}""")
        assertEquals("https://i1.hoopchina.com.cn/b.jpg", HupuParser.coverFrom(o))
    }

    @Test
    fun `cover is null for empty or dirty pics`() {
        // 空数组 / 无 pics / 对象里没 url / 传 null → 一律 null（不把脏值喂给图片加载器）
        assertNull(HupuParser.coverFrom(JSONObject("""{"pics":[]}""")))
        assertNull(HupuParser.coverFrom(JSONObject("""{"tid":"1"}""")))
        assertNull(HupuParser.coverFrom(JSONObject("""{"pics":[{"width":10}]}""")))
        assertNull(HupuParser.coverFrom(null))
    }

    @Test
    fun `cover falls back to cover field`() {
        // 信息流用的是 cover 字段；列表接口若也走这个字段名，封面照样能出来
        val o = JSONObject("""{"tid":"1","cover":"https://i5.hoopchina.com.cn/d.jpg"}""")
        assertEquals("https://i5.hoopchina.com.cn/d.jpg", HupuParser.coverFrom(o))
    }

    @Test
    fun `user profile thread cover comes from pics url`() {
        // 端到端：移动版 SSR 的 threadList[0].pics[0].url → HupuProfileThread.cover
        val html = """<html><head><script id="__NEXT_DATA__" type="application/json">""" +
            """{"props":{"pageProps":{"userInfoData":{"puid":"1","nickname":"t"},""" +
            """"threadList":[{"tid":"9","title":"t","pics":""" +
            """[{"url":"https://i3.hoopchina.com.cn/c.jpg","width":3024}]}],""" +
            """"replyList":[]}}}</script></head></html>"""
        val p = HupuParser.parseUserProfile(html)
        assertNotNull(p)
        assertEquals(1, p!!.threads.size)
        assertEquals("https://i3.hoopchina.com.cn/c.jpg", p.threads.first().cover)
    }
}