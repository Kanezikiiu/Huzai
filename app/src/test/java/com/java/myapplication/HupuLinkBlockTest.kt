package com.java.myapplication

import com.java.myapplication.data.HupuBlocks
import com.java.myapplication.data.HupuDraft
import com.java.myapplication.data.HupuParser
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.226：发帖编辑器的「插入链接」两种形态。
 *
 * 期望格式来自真实帖子（tid 643080532，本人可见）服务端实际存储：
 *  · 行内链接：`<p>文字<a data-type="link" data-name="标题" target="_blank" href="URL">标题</a>文字</p>`
 *  · 卡片链接：块级 `<a data-type="card-link" data-name="标题" ...>标题</a>`
 *  · 两者都必须写进 format.jsonV3（type=link / cardLink），否则服务端会把整段丢掉
 */
class HupuLinkBlockTest {

    private fun link(id: Long, url: String, text: String, card: Boolean) =
        HupuBlocks.Block.Link(id = id, url = url, text = text, card = card)

    @Test
    fun `行内链接与前后文字同段并写入 format`() {
        val payload = HupuBlocks.serialize(
            listOf(
                HupuBlocks.Block.Text(1L, "慢羊羊，"),
                link(2L, "https://example.com/a", "点我", card = false),
                HupuBlocks.Block.Text(3L, "已为你开好"),
            ),
        )
        val html = payload.contentHtml
        assertTrue(html.startsWith("<p>慢羊羊，"))
        assertTrue(html.contains("data-type=\"link\""))
        assertTrue(html.contains("data-name=\"点我\""))
        assertTrue(html.contains("href=\"https://example.com/a\""))
        assertTrue(html.contains("</a>已为你开好</p>"))
        // format 必须存在，且 jsonV3 里有 link 节点
        val fmt = payload.formatJson
        assertNotNull(fmt)
        val v3 = JSONObject(fmt!!).getJSONObject("jsonV3")
        assertTrue(v3.toString().contains("\"type\":\"link\""))
        assertTrue(v3.toString().contains("\"href\":\"https://example.com/a\""))
    }

    @Test
    fun `卡片链接是块级且排在末尾`() {
        val blocks = HupuDraft.toBlocks(
            text = "正文第一段\n" + "\uE000",
            attachments = listOf(
                HupuDraft.Attachment.Link('\uE000', "https://example.com/b", "卡片标题", card = true),
            ),
        )
        val payload = HupuBlocks.serialize(blocks)
        val html = payload.contentHtml
        assertTrue(html.contains("data-type=\"card-link\""))
        assertTrue(html.indexOf("正文第一段") < html.indexOf("卡片标题"))
        // 卡片是块级元素，不包在 <p> 里
        assertTrue(html.contains("<a data-type=\"card-link\""))
        val v3 = JSONObject(payload.formatJson!!).getJSONObject("jsonV3")
        assertTrue(v3.toString().contains("\"type\":\"cardLink\""))
    }

    @Test
    fun `显示文字为空时回退成 URL`() {
        val payload = HupuBlocks.serialize(listOf(link(1L, "https://example.com/c", "", true)))
        assertTrue(payload.contentHtml.contains(">https://example.com/c</a>"))
        assertTrue(payload.contentHtml.contains("data-name=\"https://example.com/c\""))
    }

    @Test
    fun `只有文字和图片时不产生 format`() {
        assertNull(HupuBlocks.serialize(listOf(HupuBlocks.Block.Text(1L, "纯文字"))).formatJson)
        assertNull(
            HupuBlocks.serialize(listOf(HupuBlocks.Block.Image(1L, "https://i/1.jpg"))).formatJson,
        )
    }

    @Test
    fun `编辑已有帖子时链接能还原回草稿`() {
        val (text, atts) = HupuDraft.fromBlocks(
            listOf(link(1L, "https://example.com/d", "标题D", card = true)),
        )
        assertEquals(1, text.length)
        val l = atts.single() as HupuDraft.Attachment.Link
        assertEquals("https://example.com/d", l.url)
        assertEquals("标题D", l.text)
        assertTrue(l.card)
    }
}
/** 1.226：URL 规范化 + 链接卡片解析 */
class HupuLinkUrlTest {

    @Test
    fun `缺少协议时补 https`() {
        assertEquals("https://www.baidu.com", HupuDraft.normalizeUrl("www.baidu.com"))
        assertEquals("https://bbs.hupu.com/1.html", HupuDraft.normalizeUrl("bbs.hupu.com/1.html"))
        // 已有协议 / 协议相对 / 其它 scheme 保持原样
        assertEquals("http://a.com", HupuDraft.normalizeUrl("http://a.com"))
        assertEquals("https://a.com", HupuDraft.normalizeUrl("//a.com"))
        assertEquals("mailto:a@b.com", HupuDraft.normalizeUrl("mailto:a@b.com"))
    }

    @Test
    fun `card-link 解析成链接卡片 token`() {
        val html = """<a data-type="card-link" data-name="回到羊村" target="_blank" href="https://www.bing.com">回到羊村</a>"""
        val tok = HupuParser.contentTokens(html).filterIsInstance<HupuParser.ContentToken.LinkCard>().single()
        assertEquals("https://www.bing.com", tok.url)
        assertEquals("回到羊村", tok.title)
    }

    @Test
    fun `普通行内 link 不会被当成卡片`() {
        val html = """<p>x<a data-type="link" data-name="点我" href="https://a.com">点我</a></p>"""
        assertTrue(HupuParser.contentTokens(html).none { it is HupuParser.ContentToken.LinkCard })
    }
}
