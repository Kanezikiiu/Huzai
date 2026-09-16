package com.java.myapplication.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.128：SSR 一级 replies 的「子回复标记」（quote.pid）解析。
 * 真一级评论没有 quote 键；「回复某条评论」的子回复带 quote（内含母评论 pid）。
 */
class HupuSubReplyParseTest {

    private fun html(listJson: String): String =
        "<html><script id=\"__NEXT_DATA__\" type=\"application/json\">" +
            "{\"props\":{\"pageProps\":{\"detail\":{" +
            "\"thread\":{\"tid\":\"642338969\",\"title\":\"t\",\"fid\":\"1\",\"replies\":4}," +
            "\"replies\":{\"current\":1,\"total\":1,\"count\":4,\"list\":[$listJson]}" +
            "}}}}" +
            "</script></html>"

    @Test
    fun `quote pid marks sub-reply, true top-level has none`() {
        val json = listOf(
            """{"pid":"100","content":"<p>top</p>","allLightCount":1,"replyNum":0,"author":{"puname":"A"}}""",
            """{"pid":"101","content":"<p>sub</p>","allLightCount":0,"replyNum":0,"author":{"puname":"B"},"quote":{"pid":"100"}}""",
            """{"pid":"102","content":"<p>sub2</p>","allLightCount":0,"replyNum":0,"author":{"puname":"C"},"quote":{"pid":"101"}}""",
            """{"pid":"103","content":"<p>qnull</p>","allLightCount":0,"replyNum":0,"author":{"puname":"D"},"quote":null}""",
        ).joinToString(",")

        val d = HupuParser.parseThreadDetail(html(json))
        assertNotNull(d)
        d!!
        assertEquals(4, d.replies.size)

        // 真一级评论：无 quote → quotePid 空 → 不算子回复
        assertEquals("", d.replies[0].quotePid)
        assertFalse(d.replies[0].isSubReply)

        // 回复某条评论：quote.pid = 母评论 pid
        assertEquals("100", d.replies[1].quotePid)
        assertTrue(d.replies[1].isSubReply)
        assertEquals("101", d.replies[2].quotePid)
        assertTrue(d.replies[2].isSubReply)

        // quote 为 JSON null：按无 quote 处理（org.json optString 会把 null 变 "null"，须防）
        assertEquals("", d.replies[3].quotePid)
        assertFalse(d.replies[3].isSubReply)
    }

    @Test
    fun `missing quote key yields empty quotePid`() {
        val d = HupuParser.parseThreadDetail(html("""{"pid":"1","content":"<p>x</p>","author":{"puname":"A"}}"""))
        assertNotNull(d)
        assertEquals("", d!!.replies.first().quotePid)
    }
}