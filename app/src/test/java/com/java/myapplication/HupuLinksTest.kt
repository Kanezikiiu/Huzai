package com.java.myapplication

import com.java.myapplication.data.HupuLinks
import com.java.myapplication.data.HupuLinks.Target
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.224o：帖子正文超链接 → 应用内落点（帖子 / 原生评分页 / 用户主页 / 网页）的解析 */
class HupuLinksTest {

    // ---------- 帖子 ----------

    @Test
    fun `bbs 帖子页解析出 tid`() {
        assertEquals("642968180", HupuLinks.threadTid("https://bbs.hupu.com/642968180.html"))
    }

    @Test
    fun `m 站与 bbs 前缀路径都能解析`() {
        assertEquals("642968180", HupuLinks.threadTid("https://m.hupu.com/bbs/642968180.html"))
        assertEquals("1234567", HupuLinks.threadTid("https://www.hupu.com/bbs/1234567"))
    }

    @Test
    fun `带查询串与锚点不影响解析`() {
        assertEquals("123", HupuLinks.threadTid("http://bbs.hupu.com/123.html?from=x#reply"))
    }

    @Test
    fun `站内自定义 scheme 解析出 tid`() {
        assertEquals("999", HupuLinks.threadTid("huputiyu://bbs/topic/999"))
        assertEquals("888", HupuLinks.threadTid("hupu://bbs/topic/888?x=1"))
    }

    @Test
    fun `空与空白安全`() {
        assertNull(HupuLinks.threadTid(null))
        assertNull(HupuLinks.threadTid(""))
        assertNull(HupuLinks.threadTid("   "))
    }

    @Test
    fun `主域名判定`() {
        assertTrue(HupuLinks.isHupu("https://bbs.hupu.com/1.html"))
        assertTrue(HupuLinks.isHupu("hupu://bbs/topic/1"))
        assertTrue(HupuLinks.isHupu("https://hupu.com"))
        assertFalse(HupuLinks.isHupu("https://hupu.com.evil.com/1.html"))
        assertFalse(HupuLinks.isHupu("https://not-hupu.com/1.html"))
        assertFalse(HupuLinks.isHupu(null))
    }

    // ---------- 落点解析 ----------

    @Test
    fun `帖子链接落点是应用内开帖`() {
        val t = HupuLinks.resolve("https://bbs.hupu.com/642968180.html")
        assertTrue(t is Target.Thread)
        assertEquals("642968180", (t as Target.Thread).tid)
    }

    /** 官方深链：评分对象详情 → **原生**选手评分页 */
    @Test
    fun `score detail 深链落到原生评分对象页`() {
        val t = HupuLinks.resolve("huputiyu://score/detail?outBizType=lol_item&outBizNo=78651")
        assertTrue("实际=$t", t is Target.ScoreItem)
        assertEquals("lol_item", (t as Target.ScoreItem).bizType)
        assertEquals("78651", t.bizNo)
    }

    /** 官方深链：对局评分 → **原生**对局页（matchId 交给评分页换算） */
    @Test
    fun `general live 深链落到原生对局页`() {
        val t = HupuLinks.resolve(
            "huputiyu://general/live?competitionType=common_match&matchId=1441778164564991655"
        )
        assertTrue("实际=$t", t is Target.ScoreMatch)
        assertEquals("1441778164564991655", (t as Target.ScoreMatch).matchId)
    }

    /** 官方「网页外链」包装：解出内部评分详情地址 → 仍然走原生 */
    @Test
    fun `webview 包装解出内部评分详情仍走原生`() {
        val raw = "huputiyu://webview/openencodeurl?fullscreen=1" +
            "&url=https%3A%2F%2Foffline-download.hupu.com%2Fonline%2Fprod%2F310016%2Fdetail.html" +
            "%3FoutBizType%3Dlol_item%26outBizNo%3D78651" +
            "&offlineSupport=0" +
            "&nativeSchema=huputiyu%3A%2F%2Fscore%2Fdetail" +
            "%3FoutBizType%3Dlol_item%26outBizNo%3D78651"
        val t = HupuLinks.resolve(raw)
        assertTrue("实际=$t", t is Target.ScoreItem)
        assertEquals("lol_item", (t as Target.ScoreItem).bizType)
        assertEquals("78651", t.bizNo)
    }

    /** 官方「壳页」pc-viewer?t=… → 解一层后落到原生 */
    @Test
    fun `pc-viewer 壳页解出 t 后走原生`() {
        val raw = "https://bbsactivity.hupu.com/pc-viewer/index.html?t=" +
            "https%3A%2F%2Foffline-download.hupu.com%2Fonline%2Fprod%2F310016%2Fdetail.html" +
            "%3FoutBizType%3Dlol_item%26outBizNo%3D78651"
        val t = HupuLinks.resolve(raw)
        assertTrue("实际=$t", t is Target.ScoreItem)
        assertEquals("78651", (t as Target.ScoreItem).bizNo)
    }

    /** 任何带 outBizType+outBizNo 的地址都是评分对象详情 */
    @Test
    fun `带评分参数的普通地址也走原生`() {
        val t = HupuLinks.resolve(
            "https://offline-download.hupu.com/online/prod/310016/detail.html?outBizType=lol_item&outBizNo=78651"
        )
        assertTrue("实际=$t", t is Target.ScoreItem)
        assertEquals("lol_item", (t as Target.ScoreItem).bizType)
    }

    @Test
    fun `webview 没有 url 时沿 nativeSchema 再解一层`() {
        val raw = "huputiyu://webview/openencodeurl?fullscreen=1" +
            "&nativeSchema=huputiyu%3A%2F%2Fscore%2Fdetail" +
            "%3FoutBizType%3Dlol_item%26outBizNo%3D78651"
        val t = HupuLinks.resolve(raw)
        assertTrue("实际=$t", t is Target.ScoreItem)
        assertEquals("78651", (t as Target.ScoreItem).bizNo)
    }

    /**
     * 1.224o：**官方深链一律按 id 识别**（不按 host）。
     * 官方给过的几种写法：
     *  · `huputiyu://general/live?competitionType=common_match&matchId=…`（电竞对局）
     *  · `huputiyu://soccerleagues/liga/live/3866492?…`（足球对局，id 在路径）
     *  · `huputiyu://lol/lol/1677567214387200?defaultCategory=live_score&bo=4`（LoL 对局，id 在路径）
     */
    @Test
    fun `三种对局深链都落到同一个对局目标`() {
        val cases = listOf(
            "huputiyu://general/live?competitionType=common_match&matchId=1441778164564991655" to "1441778164564991655",
            "huputiyu://soccerleagues/liga/live/3866492?matchId=3866492&defaultTab=live_score" to "3866492",
            "huputiyu://lol/lol/1677567214387200?defaultCategory=live_score&bo=4" to "1677567214387200",
        )
        for ((raw, id) in cases) {
            val t = HupuLinks.resolve(raw)
            assertTrue("$raw 实际=$t", t is Target.ScoreMatch)
            assertEquals(id, (t as Target.ScoreMatch).matchId)
        }
    }

    /** 只给路径、没有 matchId 参数时也要能取出 */
    @Test
    fun `对局深链缺 query 时取路径末段`() {
        val t = HupuLinks.resolve("huputiyu://soccerleagues/liga/live/3866492")
        assertTrue("实际=$t", t is Target.ScoreMatch)
        assertEquals("3866492", (t as Target.ScoreMatch).matchId)
    }

    /** 正文里的「@某人」→ 应用内用户主页 */
    @Test
    fun `people 链接落到用户主页`() {
        val t = HupuLinks.resolve("huputiyu://people/93956731")
        assertTrue("实际=$t", t is Target.User)
        assertEquals("93956731", (t as Target.User).uid)
    }

    /** 其它虎扑网页 → 应用内 WebView */
    @Test
    fun `虎扑普通网页在应用内打开`() {
        val t = HupuLinks.resolve("https://bbs.hupu.com/gambia")
        assertTrue("实际=$t", t is Target.Web)
        assertEquals(HupuLinks.EMBED_SCORE, (t as Target.Web).embedType)
    }

    @Test
    fun `非虎扑外链与不认识的深链交给系统`() {
        assertNull(HupuLinks.resolve("https://www.baidu.com/s?wd=hupu"))
        assertNull(HupuLinks.resolve("huputiyu://unknown/route?x=1"))
        assertNull(HupuLinks.resolve(null))
        assertNull(HupuLinks.resolve("   "))
    }

    @Test
    fun `深链缺参数时不误判`() {
        assertNull(HupuLinks.resolve("huputiyu://score/detail?outBizType=lol_item"))
        assertNull(HupuLinks.resolve("huputiyu://general/live?competitionType=common_match"))
        // 参数不全的虎扑网页：不走原生评分页，但仍留在应用内（网页承载）
        assertTrue(
            HupuLinks.resolve("https://offline-download.hupu.com/x/detail.html?outBizType=lol_item")
                is Target.Web,
        )
    }
}