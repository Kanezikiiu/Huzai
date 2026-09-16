package com.java.myapplication

import com.java.myapplication.data.HupuBlocks
import com.java.myapplication.data.HupuDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.117 正文草稿模型单测（方案 B：单文本框 + 占位符 + 附件表）。
 *
 * 覆盖：占位符字符判定/唯一性、正文+附件 → 块顺序、未定位附件排到最后、
 * 块 → 正文+附件 反解析、与 HupuBlocks.serialize 的端到端往返（含投票 format）。
 */
class HupuDraftTest {

    private fun textBlocks(blocks: List<HupuBlocks.Block>): List<String> =
        blocks.filterIsInstance<HupuBlocks.Block.Text>().map { it.text }

    @Test
    fun `占位符字符落在私有区且唯一`() {
        val a = HupuDraft.nextToken()
        val b = HupuDraft.nextToken()
        assertTrue(HupuDraft.isToken(a))
        assertTrue(HupuDraft.isToken(b))
        assertNotEquals(a, b)
        assertFalse(HupuDraft.isToken('a'))
        assertFalse(HupuDraft.isToken('\uFFFD'))
    }

    @Test
    fun `正文里的占位符按位置切成块`() {
        val t1 = HupuDraft.nextToken()
        val t2 = HupuDraft.nextToken()
        val atts = listOf(
            HupuDraft.Attachment.Image(t1, "https://x/a.jpg"),
            HupuDraft.Attachment.Vote(t2, 123, "选谁", listOf("甲", "乙")),
        )
        val text = "开头${t1}中间${t2}结尾"
        val blocks = HupuDraft.toBlocks(text, atts)
        assertEquals(listOf("开头", "中间", "结尾"), textBlocks(blocks))
        assertTrue(blocks[1] is HupuBlocks.Block.Image)
        assertTrue(blocks[3] is HupuBlocks.Block.Vote)
    }

    @Test
    fun `占位符被删的附件排到最后`() {
        val t1 = HupuDraft.nextToken()
        val t2 = HupuDraft.nextToken()
        val atts = listOf(
            HupuDraft.Attachment.Image(t1, "https://x/a.jpg"),
            HupuDraft.Attachment.Image(t2, "https://x/b.jpg"),
        )
        val text = "正文$t2" // t1 的占位符被删掉了
        val ordered = HupuDraft.ordered(text, atts)
        assertEquals(t2, ordered[0].token)
        assertEquals(t1, ordered[1].token)

        val blocks = HupuDraft.toBlocks(text, atts)
        val imgs = blocks.filterIsInstance<HupuBlocks.Block.Image>().map { it.url }
        assertEquals(listOf("https://x/b.jpg", "https://x/a.jpg"), imgs)
    }

    @Test
    fun `块反解析成正文与附件`() {
        val blocks = listOf(
            HupuBlocks.Block.Text(1, "第一段"),
            HupuBlocks.Block.Image(2, "https://x/a.jpg"),
            HupuBlocks.Block.Text(3, "第二段"),
            HupuBlocks.Block.Vote(4, 999, "投票标题", listOf("A", "B"), 1, "radio"),
        )
        val (text, atts) = HupuDraft.fromBlocks(blocks)
        assertEquals(2, atts.size)
        assertTrue(atts[0] is HupuDraft.Attachment.Image)
        assertTrue(atts[1] is HupuDraft.Attachment.Vote)
        assertEquals("第一段" + atts[0].token + "第二段" + atts[1].token, text)
    }

    @Test
    fun `往返序列化保持图文投票契约`() {
        val t = HupuDraft.nextToken()
        val v = HupuDraft.nextToken()
        val text = "看看${t}投票${v}"
        val atts = listOf(
            HupuDraft.Attachment.Image(t, "https://x/a.jpg"),
            HupuDraft.Attachment.Vote(v, 555, "标题", listOf("甲", "乙")),
        )
        val payload = HupuBlocks.serialize(HupuDraft.toBlocks(text, atts))
        assertTrue(payload.contentHtml.contains("<p>看看</p>"))
        assertTrue(payload.contentHtml.contains("<p><img src=\"https://x/a.jpg\"/></p>"))
        assertTrue(payload.contentHtml.contains("<p>[vote]555[/vote]</p>"))
        assertTrue((payload.formatJson ?: "").contains("\"voteId\":555"))
    }

    @Test
    fun `空正文与空附件不产生幽灵块`() {
        val blocks = HupuDraft.toBlocks("", emptyList())
        assertTrue(blocks.all { it is HupuBlocks.Block.Text && it.text.isEmpty() })
    }

    @Test
    fun `占位符序号按正文出现顺序`() {
        val a = HupuDraft.nextToken()
        val b = HupuDraft.nextToken()
        val c = HupuDraft.nextToken()
        val text = "x${a}y${c}z${b}"
        val idx = HupuDraft.indexMap(text)
        assertEquals(1, idx[a])
        assertEquals(2, idx[c])
        assertEquals(3, idx[b])
        assertEquals("〔图片2〕", HupuDraft.labelOf(HupuDraft.Attachment.Image(c, "u"), 2))
        assertEquals("〔投票3〕", HupuDraft.labelOf(HupuDraft.Attachment.Vote(b, 1, "t", listOf("a")), 3))
    }

    @Test
    fun `删掉占位符则附件一并删除`() {
        val a = HupuDraft.nextToken()
        val b = HupuDraft.nextToken()
        val atts = listOf(
            HupuDraft.Attachment.Image(a, "https://x/a.jpg"),
            HupuDraft.Attachment.Image(b, "https://x/b.jpg"),
        )
        // 正文里只剩 b，a 的占位符已被删
        val kept = HupuDraft.pruneAttachments("正文$b", atts)
        assertEquals(1, kept.size)
        assertEquals(b, kept[0].token)

        // 正文里两个都在 → 一个都不删
        assertEquals(2, HupuDraft.pruneAttachments("$a$b", atts).size)
    }
}
