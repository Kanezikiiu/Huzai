package com.java.myapplication

import com.java.myapplication.data.HupuLinks
import com.java.myapplication.data.HupuLinks.Target
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.224o：用**真机 HTML 里抓到的原样 href** 做回归。
 *
 * 两条样本都要求落到**原生**页面（用户明确要原生页，不要应用内网页）：
 *  · 643004521「点击为双方表现打分>>」= general/live + matchId → 原生对局评分页
 *  · 642986315「前往 Cube 评分页…」= webview 包装（内部 detail.html?outBizType=lol_item）
 *    / 以及 bbsactivity 的 pc-viewer 壳页 → 原生评分对象页
 */
class HupuLinksRealSamplesTest {

    /** 帖子 643004521 正文：点击为双方表现打分 → 原生对局页 */
    @Test
    fun `真样本 general live 落到原生对局页`() {
        val raw = "huputiyu://general/live?competitionType=common_match&matchId=1441778164564991655"
        val t = HupuLinks.resolve(raw)
        assertTrue("实际=$t", t is Target.ScoreMatch)
        assertEquals("1441778164564991655", (t as Target.ScoreMatch).matchId)
    }

    /** 同上但 `&` 写成 `&amp;`（HTML 实体）也要认 */
    @Test
    fun `真样本 general live 带 amp 实体`() {
        val raw = "huputiyu://general/live?competitionType=common_match&amp;matchId=1441778164564991655"
        val t = HupuLinks.resolve(raw)
        assertTrue("实际=$t", t is Target.ScoreMatch)
        assertEquals("1441778164564991655", (t as Target.ScoreMatch).matchId)
    }

    /** 帖子 642986315 正文：前往 Cube 评分页 → 原生评分对象页 */
    @Test
    fun `真样本 webview 包装落到原生评分对象页`() {
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

    /** 用户第二次给的形态：bbsactivity 的 pc-viewer 壳页 */
    @Test
    fun `真样本 pc-viewer 壳页落到原生评分对象页`() {
        val raw = "https://bbsactivity.hupu.com/pc-viewer/index.html?t=" +
            "https%3A%2F%2Foffline-download.hupu.com%2Fonline%2Fprod%2F310016%2Fdetail.html" +
            "%3FoutBizType%3Dlol_item%26outBizNo%3D78651"
        val t = HupuLinks.resolve(raw)
        assertTrue("实际=$t", t is Target.ScoreItem)
        assertEquals("lol_item", (t as Target.ScoreItem).bizType)
        assertEquals("78651", t.bizNo)
    }

    /** 同一帖里的勋章链接（webview 包装，但内部不是评分页）→ 留在应用内网页 */
    @Test
    fun `真样本 勋章 webview 仍走应用内网页`() {
        val raw = "huputiyu://webview/openencodeurl?url=https%3A%2F%2Foffline-download.hupu.com" +
            "%2Fonline%2Fprod%2F310004%2FmedalDetail.html%3FbadgeId%3D12981%26mfullscreen%3D1" +
            "&amp;fullscreen=1"
        val t = HupuLinks.resolve(raw)
        assertTrue("实际=$t", t is Target.Web)
    }

    /** 同一帖里的「@某人」：应落到应用内用户主页 */
    @Test
    fun `真样本 people 落到用户主页`() {
        val t = HupuLinks.resolve("huputiyu://people/93956731")
        assertTrue("实际=$t", t is Target.User)
        assertEquals("93956731", (t as Target.User).uid)
    }
}