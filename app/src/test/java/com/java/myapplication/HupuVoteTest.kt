package com.java.myapplication

import com.java.myapplication.data.HupuBlocks
import com.java.myapplication.data.HupuDraft
import com.java.myapplication.data.HupuParser
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.119 多选投票单测。
 *
 * 依据实测帖 642389664（步行街主干道「你最喜欢哪两只羊」）的真实 format：
 * type=checkbox、limit=2、7 个选项、voteId=11333575。
 *
 * 覆盖：format → HupuVote 解析（多选 / 单选 / 无投票）、
 * 块序列化写出 checkbox + limit、草稿往返保留单选多选设置。
 */
class HupuVoteTest {

    /** 复刻 642389664 的真实 format（多选） */
    private fun multiFormat(): String = JSONObject()
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
                                    .put("voteId", 11333575)
                                    .put("limit", 2)
                                    .put("title", "选择两只羊")
                                    .put("type", "checkbox")
                                    .put(
                                        "choices",
                                        JSONArray(
                                            listOf(
                                                "喜羊羊", "美羊羊", "懒羊羊", "慢羊羊",
                                                "暖羊羊", "沸羊羊", "小飞机羊",
                                            ),
                                        ),
                                    ),
                            ),
                    ),
                ),
        )
        .toString()

    @Test
    fun `format 解析多选投票`() {
        val v = HupuParser.voteFromFormat(multiFormat())
        assertNotNull(v)
        assertEquals(11333575, v!!.voteId)
        assertEquals(2, v.limit)
        assertEquals("选择两只羊", v.title)
        assertTrue(v.isMulti)
        assertEquals(7, v.choices.size)
        assertEquals("喜羊羊", v.choices.first())
        assertEquals("小飞机羊", v.choices.last())
    }

    @Test
    fun `format 解析单选投票`() {
        val fmt = JSONObject()
            .put("jsonV3", JSONObject().put("type", "doc").put("content", JSONArray().put(
                JSONObject().put("type", "vote").put(
                    "attrs",
                    JSONObject().put("voteId", 999).put("limit", 1)
                        .put("title", "单选").put("type", "radio")
                        .put("choices", JSONArray(listOf("甲", "乙"))),
                ),
            )))
            .toString()
        val v = HupuParser.voteFromFormat(fmt)
        assertNotNull(v)
        assertFalse(v!!.isMulti)
        assertEquals(1, v.limit)
        assertEquals(2, v.choices.size)
    }

    @Test
    fun `format 解析单选投票（真实帖无 type 字段）`() {
        // 实测 642385783：单选节点没有 type，只有 voteType:"text" + limit:1
        val fmt = JSONObject()
            .put(
                "jsonV3",
                JSONObject().put("type", "doc").put(
                    "content",
                    JSONArray().put(
                        JSONObject().put("type", "vote").put(
                            "attrs",
                            JSONObject().put("voteId", 11333529)
                                .put("voteType", "text")
                                .put("limit", 1)
                                .put("title", "投票你凯喂屎感最重的主场比赛")
                                .put("choices", JSONArray(listOf("12年东决g6死亡之瞳", "23东决g7惨败热火"))),
                        ),
                    ),
                ),
            )
            .toString()
        val v = HupuParser.voteFromFormat(fmt)
        assertNotNull(v)
        assertEquals(11333529, v!!.voteId)
        assertEquals(1, v.limit)
        assertFalse(v.isMulti)
        assertEquals(2, v.choices.size)
    }

    @Test
    fun `正文 content 顺序切分保留投票占位位置`() {
        // 实测 642396934：段落 - 投票 - 段落
        val html = "<p>你最喜欢谁呢</p><p>" +
            "<span data-type=\"vote\" data-vote-id=\"11333655\"></span></p><p>做出你的选择吧</p>"
        val toks = HupuParser.contentTokens(html)
        assertEquals(3, toks.size)
        assertTrue(toks[0] is HupuParser.ContentToken.Text)
        assertTrue(toks[1] is HupuParser.ContentToken.Vote)
        assertEquals(11333655, (toks[1] as HupuParser.ContentToken.Vote).voteId)
        assertTrue(toks[2] is HupuParser.ContentToken.Text)
        assertTrue((toks[0] as HupuParser.ContentToken.Text).html.contains("你最喜欢谁呢"))
        assertTrue((toks[2] as HupuParser.ContentToken.Text).html.contains("做出你的选择吧"))
    }

    @Test
    fun `图片与投票占位按出现顺序切分`() {
        val html = "<p>a</p><img src=\"https://i1.hupu.com/x.jpg\">" +
            "<p>b</p><span data-type=\"vote\" data-vote-id=\"9\"></span><p>c</p>"
        val kinds = HupuParser.contentTokens(html).map {
            when (it) {
                is HupuParser.ContentToken.Text -> "T"
                is HupuParser.ContentToken.Image -> "I"
                is HupuParser.ContentToken.Vote -> "V"
            }
        }
        assertEquals(listOf("T", "I", "T", "V", "T"), kinds)
    }

    @Test
    fun `无投票正文不产生投票 token`() {
        val html = "<p>只有文字</p><img src=\"https://i1.hupu.com/y.png\">"
        assertTrue(
            HupuParser.contentTokens(html).none { it is HupuParser.ContentToken.Vote },
        )
        assertTrue(HupuParser.contentTokens("").isEmpty())
        assertTrue(HupuParser.contentTokens(null).isEmpty())
    }

    @Test
    fun `无投票 format 返回 null`() {
        assertNull(HupuParser.voteFromFormat(null))
        assertNull(HupuParser.voteFromFormat(""))
        assertNull(HupuParser.voteFromFormat("{}"))
        val plain = JSONObject()
            .put("jsonV3", JSONObject().put("type", "doc").put("content", JSONArray().put(
                JSONObject().put("type", "paragraph"),
            )))
            .toString()
        assertNull(HupuParser.voteFromFormat(plain))
    }

    @Test
    fun `序列化多选投票写出 checkbox 与 limit`() {
        val payload = HupuBlocks.serialize(
            listOf(
                HupuBlocks.Block.Vote(
                    id = 1,
                    voteId = 11333575,
                    title = "选择两只羊",
                    choices = listOf("甲", "乙", "丙"),
                    limit = 2,
                    voteType = "checkbox",
                ),
            ),
        )
        assertTrue(payload.contentHtml.contains("[vote]11333575[/vote]"))
        val fmt = payload.formatJson ?: ""
        // jsonV3 投票节点
        val node = JSONObject(fmt).optJSONObject("jsonV3")!!
            .optJSONArray("content")!!.optJSONObject(0)
        assertEquals("vote", node.optString("type"))
        assertEquals(11333575, node.optJSONObject("attrs")!!.optInt("voteId"))
        assertEquals(2, node.optJSONObject("attrs")!!.optInt("limit"))
        assertEquals("checkbox", node.optJSONObject("attrs")!!.optString("type"))
        // htmlV3 里的投票块形态与 data（引号以 " 实体转义，故不直接断言原始引号）
        val htmlV3 = JSONObject(fmt).optString("htmlV3")
        assertTrue(htmlV3.contains("data-hupu-node"))
        assertTrue(htmlV3.contains("checkbox"))
        assertTrue(htmlV3.contains("data-hupu-data"))
    }

    @Test
    fun `草稿往返保留单选多选设置`() {
        val blocks = listOf(
            HupuBlocks.Block.Vote(1, 777, "标题", listOf("a", "b"), 2, "checkbox"),
        )
        val (text, atts) = HupuDraft.fromBlocks(blocks)
        val att = atts.single() as HupuDraft.Attachment.Vote
        assertEquals(2, att.limit)
        assertEquals("checkbox", att.voteType)

        val back = HupuDraft.toBlocks(text, atts)
        val vb = back.filterIsInstance<HupuBlocks.Block.Vote>().single()
        assertEquals(777, vb.voteId)
        assertEquals(2, vb.limit)
        assertEquals("checkbox", vb.voteType)
        assertEquals(listOf("a", "b"), vb.choices)
    }
}