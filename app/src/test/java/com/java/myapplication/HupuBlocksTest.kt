package com.java.myapplication

import com.java.myapplication.data.HupuBlocks
import com.java.myapplication.data.HupuPostApi
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.113 正文块模型单测（纯函数，不发网络）。
 *
 * 覆盖：块序列化（文字/图片/投票 → content + format）、format 仅在含投票时生成、
 * jsonV3 投票节点 schema、htmlV3 投票块形态、编辑反解析（format 优先 / HTML 回退）、
 * 以及投票帖 payload 透传 format。
 */
class HupuBlocksTest {

    private fun voteDoc(voteId: Int = 11333513, choices: List<String> = listOf("甲", "乙")): String =
        JSONObject()
            .put("htmlV3", "")
            .put(
                "jsonV3",
                JSONObject()
                    .put("type", "doc")
                    .put(
                        "content",
                        JSONArray().put(
                            JSONObject()
                                .put("type", "vote")
                                .put(
                                    "attrs",
                                    JSONObject()
                                        .put("voteId", voteId)
                                        .put("limit", 1)
                                        .put("title", "你喜欢谁")
                                        .put("type", "radio")
                                        .put("choices", JSONArray(choices)),
                                ),
                        ),
                    ),
            )
            .put("imgList", JSONArray())
            .toString()

    // ---------- 序列化 ----------

    @Test
    fun `纯文字块序列化为 p 段落且不生成 format`() {
        val p = HupuBlocks.serialize(listOf(HupuBlocks.Block.Text(1, "第一行\n第二行")))
        assertEquals("<p>第一行</p><p>第二行</p>", p.contentHtml)
        assertNull(p.formatJson)
    }

    @Test
    fun `图片块序列化为 p img 且不带 format`() {
        val p = HupuBlocks.serialize(listOf(HupuBlocks.Block.Image(1, "https://i/1.jpg")))
        assertEquals("<p><img src=\"https://i/1.jpg\"/></p>", p.contentHtml)
        assertNull(p.formatJson)
    }

    @Test
    fun `块顺序决定正文顺序（文字-投票-图片）`() {
        val p = HupuBlocks.serialize(
            listOf(
                HupuBlocks.Block.Text(1, "前言"),
                HupuBlocks.Block.Vote(2, 11333513, "你喜欢谁", listOf("甲", "乙")),
                HupuBlocks.Block.Image(3, "https://i/1.jpg"),
            ),
        )
        assertEquals(
            "<p>前言</p><p>[vote]11333513[/vote]</p><p><img src=\"https://i/1.jpg\"/></p>",
            p.contentHtml,
        )
    }

    @Test
    fun `投票短代码形态与官方一致`() {
        val p = HupuBlocks.serialize(listOf(HupuBlocks.Block.Vote(1, 11333513, "t", listOf("甲", "乙"))))
        assertEquals("<p>[vote]11333513[/vote]</p>", p.contentHtml)
    }

    @Test
    fun `含投票时 format 的 jsonV3 为 doc + vote 节点`() {
        val p = HupuBlocks.serialize(
            listOf(
                HupuBlocks.Block.Text(1, "前言"),
                HupuBlocks.Block.Vote(2, 11333513, "你喜欢谁", listOf("甲", "乙")),
            ),
        )
        val fmt = JSONObject(p.formatJson!!)
        val doc = fmt.getJSONObject("jsonV3")
        assertEquals("doc", doc.getString("type"))
        val nodes = doc.getJSONArray("content")
        assertEquals(2, nodes.length())
        assertEquals("paragraph", nodes.getJSONObject(0).getString("type"))
        val vote = nodes.getJSONObject(1)
        assertEquals("vote", vote.getString("type"))
        val attrs = vote.getJSONObject("attrs")
        assertEquals(11333513, attrs.getInt("voteId"))
        assertEquals(1, attrs.getInt("limit"))
        assertEquals("你喜欢谁", attrs.getString("title"))
        assertEquals("radio", attrs.getString("type"))
        assertEquals(2, attrs.getJSONArray("choices").length())
        assertTrue(fmt.has("htmlV3"))
        assertTrue(fmt.has("imgList"))
    }

    @Test
    fun `htmlV3 投票块为 data-hupu-node=vote 且属性已实体转义`() {
        val p = HupuBlocks.serialize(listOf(HupuBlocks.Block.Vote(1, 11333513, "t", listOf("甲"))))
        val html = JSONObject(p.formatJson!!).getString("htmlV3")
        assertTrue(html.contains("data-hupu-node="))
        val q = "&" + "quot;"
        assertTrue(html.contains("vote"))
        assertTrue(html.contains(q))
    }

    // ---------- 编辑反解析 ----------

    @Test
    fun `编辑反解析 format 优先还原投票块`() {
        val blocks = HupuBlocks.fromEdit("<p>[vote]11333513[/vote]</p>", voteDoc())
        assertEquals(1, blocks.size)
        val v = blocks[0] as HupuBlocks.Block.Vote
        assertEquals(11333513, v.voteId)
        assertEquals("radio", v.voteType)
        assertEquals(listOf("甲", "乙"), v.choices)
        assertEquals("你喜欢谁", v.title)
    }

    @Test
    fun `编辑反解析把连续段落合并为同一文本块`() {
        val textDoc = JSONObject()
            .put("htmlV3", "")
            .put(
                "jsonV3",
                JSONObject()
                    .put("type", "doc")
                    .put(
                        "content",
                        JSONArray()
                            .put(JSONObject().put("type", "paragraph").put("content", JSONArray().put(JSONObject().put("type", "text").put("text", "第一行"))))
                            .put(JSONObject().put("type", "paragraph").put("content", JSONArray().put(JSONObject().put("type", "text").put("text", "第二行")))),
                    ),
            )
            .put("imgList", JSONArray())
            .toString()
        val blocks = HupuBlocks.fromEdit("<p>第一行</p><p>第二行</p>", textDoc)
        assertEquals(1, blocks.size)
        assertEquals("第一行\n第二行", (blocks[0] as HupuBlocks.Block.Text).text)
    }

    @Test
    fun `无 format 的老帖回退 HTML 拆分`() {
        val blocks = HupuBlocks.fromEdit(
            "<p>文字</p><p><img src=\"https://i/1.jpg\"/></p>",
            null,
        )
        assertTrue(blocks.any { it is HupuBlocks.Block.Text && it.text == "文字" })
        assertTrue(blocks.any { it is HupuBlocks.Block.Image && it.url == "https://i/1.jpg" })
    }

    @Test
    fun `往返 序列化后反解析仍保留投票`() {
        val src = listOf(
            HupuBlocks.Block.Text(1, "前言"),
            HupuBlocks.Block.Vote(2, 998877, "标题", listOf("A", "B", "C")),
            HupuBlocks.Block.Text(3, "后记"),
        )
        val p = HupuBlocks.serialize(src)
        val back = HupuBlocks.fromEdit(p.contentHtml, p.formatJson)
        val v = back.filterIsInstance<HupuBlocks.Block.Vote>().single()
        assertEquals(998877, v.voteId)
        assertEquals(listOf("A", "B", "C"), v.choices)
        assertEquals("前言", back.filterIsInstance<HupuBlocks.Block.Text>().first().text)
    }

    // ---------- payload 透传 ----------

    @Test
    fun `投票帖 payload 带 format`() {
        val fmt = HupuBlocks.serialize(listOf(HupuBlocks.Block.Vote(1, 1, "t", listOf("甲", "乙")))).formatJson
        val b = HupuPostApi.buildThreadPayload(
            title = "标题四个字",
            contentHtml = "<p>[vote]1[/vote]</p>",
            topicId = 1,
            zoneId = 0,
            creationType = "NORMAL",
            format = fmt,
        )
        assertTrue(b.has("format"))
        assertEquals("NORMAL", b.getString("creationType"))
    }

    @Test
    fun `非投票帖 payload 不带 format`() {
        val b = HupuPostApi.buildThreadPayload(
            title = "标题四个字",
            contentHtml = "<p>纯文字</p>",
            topicId = 1,
            zoneId = 0,
        )
        assertFalse(b.has("format"))
    }

    @Test
    fun `视频帖 format 优先于投票 format 参数`() {
        val b = HupuPostApi.buildThreadPayload(
            title = "标题四个字",
            contentHtml = "",
            topicId = 1,
            zoneId = 0,
            videoUrl = "https://v/x.mp4",
            videoCover = "https://img/c.jpg",
            contentText = "",
            format = "{\"ignored\":true}",
        )
        val f = JSONObject(b.getString("format"))
        assertTrue(f.has("videoInfo"))
        assertFalse(f.has("ignored"))
    }

    // ---------- 1.114 按光标锚点插入 ----------

    private class IdSeq(private var v: Long = 100L) {
        fun next(): Long = ++v
    }

    @Test
    fun `光标在中间插入投票会切开文本块`() {
        val ids = IdSeq()
        val vote = HupuBlocks.Block.Vote(2, 9, "t", listOf("甲", "乙"))
        val r = HupuBlocks.insertAtAnchor(
            blocks = listOf(HupuBlocks.Block.Text(1, "ABCD")),
            anchorId = 1,
            offset = 2,
            newBlocks = listOf(vote),
            idFactory = { ids.next() },
        )
        assertEquals(3, r.blocks.size)
        assertEquals("AB", (r.blocks[0] as HupuBlocks.Block.Text).text)
        assertTrue(r.blocks[1] is HupuBlocks.Block.Vote)
        assertEquals("CD", (r.blocks[2] as HupuBlocks.Block.Text).text)
        // 下一个锚点指向后半块开头，便于连续插入
        assertEquals(r.blocks[2].id, r.nextAnchorId)
        assertEquals(0, r.nextAnchorOffset)
    }

    @Test
    fun `光标在末尾插入不产生空的后半块`() {
        val ids = IdSeq()
        val r = HupuBlocks.insertAtAnchor(
            blocks = listOf(HupuBlocks.Block.Text(1, "ABCD")),
            anchorId = 1,
            offset = 4,
            newBlocks = listOf(HupuBlocks.Block.Image(2, "u")),
            idFactory = { ids.next() },
        )
        assertEquals(2, r.blocks.size)
        assertEquals("ABCD", (r.blocks[0] as HupuBlocks.Block.Text).text)
        assertTrue(r.blocks[1] is HupuBlocks.Block.Image)
        assertNull(r.nextAnchorId)
    }

    @Test
    fun `光标在开头插入不产生空的前半块`() {
        val ids = IdSeq()
        val r = HupuBlocks.insertAtAnchor(
            blocks = listOf(HupuBlocks.Block.Text(1, "ABCD")),
            anchorId = 1,
            offset = 0,
            newBlocks = listOf(HupuBlocks.Block.Image(2, "u")),
            idFactory = { ids.next() },
        )
        assertEquals(2, r.blocks.size)
        assertTrue(r.blocks[0] is HupuBlocks.Block.Image)
        assertEquals("ABCD", (r.blocks[1] as HupuBlocks.Block.Text).text)
        assertEquals(r.blocks[1].id, r.nextAnchorId)
    }

    @Test
    fun `无锚点时追加到末尾`() {
        val ids = IdSeq()
        val r = HupuBlocks.insertAtAnchor(
            blocks = listOf(HupuBlocks.Block.Text(1, "A")),
            anchorId = null,
            offset = 0,
            newBlocks = listOf(HupuBlocks.Block.Image(2, "u")),
            idFactory = { ids.next() },
        )
        assertEquals(2, r.blocks.size)
        assertEquals("A", (r.blocks[0] as HupuBlocks.Block.Text).text)
        assertTrue(r.blocks[1] is HupuBlocks.Block.Image)
        assertNull(r.nextAnchorId)
    }

    @Test
    fun `锚点失效时追加到末尾`() {
        val ids = IdSeq()
        val r = HupuBlocks.insertAtAnchor(
            blocks = listOf(HupuBlocks.Block.Text(1, "A")),
            anchorId = 999,
            offset = 0,
            newBlocks = listOf(HupuBlocks.Block.Image(2, "u")),
            idFactory = { ids.next() },
        )
        assertEquals(2, r.blocks.size)
        assertTrue(r.blocks[1] is HupuBlocks.Block.Image)
    }

    @Test
    fun `一次插多张图按选择顺序连续摆放`() {
        val ids = IdSeq()
        var cur: List<HupuBlocks.Block> = listOf(HupuBlocks.Block.Text(1, "AB"))
        var anchor: Long? = 1
        var off = 1
        listOf("u1", "u2").forEachIndexed { i, u ->
            val r = HupuBlocks.insertAtAnchor(
                blocks = cur,
                anchorId = anchor,
                offset = off,
                newBlocks = listOf(HupuBlocks.Block.Image(10L + i, u)),
                idFactory = { ids.next() },
            )
            cur = r.blocks
            anchor = r.nextAnchorId
            off = r.nextAnchorOffset
        }
        // [Text("A"), Image(u1), Image(u2), Text("B")]
        assertEquals(4, cur.size)
        assertEquals("A", (cur[0] as HupuBlocks.Block.Text).text)
        assertEquals("u1", (cur[1] as HupuBlocks.Block.Image).url)
        assertEquals("u2", (cur[2] as HupuBlocks.Block.Image).url)
        assertEquals("B", (cur[3] as HupuBlocks.Block.Text).text)
    }

    @Test
    fun `插入不改动传入的块列表`() {
        val ids = IdSeq()
        val src = listOf<HupuBlocks.Block>(HupuBlocks.Block.Text(1, "AB"))
        HupuBlocks.insertAtAnchor(src, 1, 1, listOf(HupuBlocks.Block.Image(2, "u")), { ids.next() })
        assertEquals(1, src.size)
        assertEquals("AB", (src[0] as HupuBlocks.Block.Text).text)
    }

    @Test
    fun `空文本框处插入会保留输入框`() {
        val ids = IdSeq()
        val r = HupuBlocks.insertAtAnchor(
            blocks = listOf(HupuBlocks.Block.Text(1, "")),
            anchorId = 1,
            offset = 0,
            newBlocks = listOf(HupuBlocks.Block.Image(2, "u")),
            idFactory = { ids.next() },
        )
        // 保留原空文本块（同一个 id，输入框不会消失），图片在其后
        assertEquals(2, r.blocks.size)
        assertEquals(1L, r.blocks[0].id)
        assertEquals("", (r.blocks[0] as HupuBlocks.Block.Text).text)
        assertTrue(r.blocks[1] is HupuBlocks.Block.Image)
        // 空文本框不参与序列化，发出正文只有图片
        assertEquals("<p><img src=\"u\"/></p>", HupuBlocks.serialize(r.blocks).contentHtml)
    }

    @Test
    fun `空文本框连续插两张图仍保留输入框且顺序正确`() {
        val ids = IdSeq()
        var cur: List<HupuBlocks.Block> = listOf(HupuBlocks.Block.Text(1, ""))
        var anchor: Long? = 1
        var off = 0
        listOf("u1", "u2").forEachIndexed { i, u ->
            val r = HupuBlocks.insertAtAnchor(
                blocks = cur,
                anchorId = anchor,
                offset = off,
                newBlocks = listOf(HupuBlocks.Block.Image(10L + i, u)),
                idFactory = { ids.next() },
            )
            cur = r.blocks
            anchor = r.nextAnchorId
            off = r.nextAnchorOffset
        }
        assertEquals(3, cur.size)
        assertEquals("", (cur[0] as HupuBlocks.Block.Text).text)
        assertEquals("u1", (cur[1] as HupuBlocks.Block.Image).url)
        assertEquals("u2", (cur[2] as HupuBlocks.Block.Image).url)
    }

    @Test
    fun `插入到中间时序列化顺序与块顺序一致`() {
        val ids = IdSeq()
        val vote = HupuBlocks.Block.Vote(2, 11333513, "t", listOf("甲", "乙"))
        val r = HupuBlocks.insertAtAnchor(
            blocks = listOf(HupuBlocks.Block.Text(1, "前言后记")),
            anchorId = 1,
            offset = 2,
            newBlocks = listOf(vote),
            idFactory = { ids.next() },
        )
        val p = HupuBlocks.serialize(r.blocks)
        assertEquals("<p>前言</p><p>[vote]11333513[/vote]</p><p>后记</p>", p.contentHtml)
        assertTrue(p.formatJson != null)
    }
}
