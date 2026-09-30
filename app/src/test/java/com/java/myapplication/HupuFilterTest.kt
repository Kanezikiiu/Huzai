package com.java.myapplication

import com.java.myapplication.data.HupuFilter
import com.java.myapplication.data.HupuScoreComment
import com.java.myapplication.data.decodeFilterKeywordsJson
import com.java.myapplication.data.encodeFilterKeywordsJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 浏览流关键词过滤纯函数 + JSON 编解码测试（纯 JVM，无 Android 依赖） */
class HupuFilterTest {

    @Test
    fun keywords_roundTrip() {
        val kw = HupuFilter.Keywords(
            title = listOf("詹姆斯", "nba"),
            zone = listOf("湿乎乎"),
            comment = listOf("跪", "库密"),
        )
        val json = encodeFilterKeywordsJson(kw)
        val out = decodeFilterKeywordsJson(json)
        assertEquals(kw, out)
    }

    @Test
    fun keywords_emptyRoundTrip() {
        val json = encodeFilterKeywordsJson(HupuFilter.Keywords())
        assertEquals(HupuFilter.Keywords(), decodeFilterKeywordsJson(json))
    }

    @Test
    fun keywords_corruptJsonReturnsEmpty() {
        assertEquals(HupuFilter.Keywords(), decodeFilterKeywordsJson("{broken"))
    }

    @Test
    fun blocked_caseInsensitive() {
        assertTrue(HupuFilter.blocked("Nba Daily Talk", listOf("nba")))
        assertTrue(HupuFilter.blocked("Nba Daily Talk", listOf("NBA")))
        assertFalse(HupuFilter.blocked("步行街日常", listOf("nba")))
    }

    @Test
    fun blocked_emptyKeywordOrText() {
        assertFalse(HupuFilter.blocked("任何文本", emptyList()))
        assertFalse(HupuFilter.blocked("", listOf("关键词")))
        assertFalse(HupuFilter.blocked("  ", listOf("关键词")))
    }

    @Test
    fun blockedThread_titleOrZoneHit() {
        val kw = HupuFilter.Keywords(title = listOf("转会"), zone = listOf("湿乎乎"))
        assertTrue(HupuFilter.blockedThread("詹姆斯转会湖人", null, kw))
        assertTrue(HupuFilter.blockedThread("任意标题", "湿乎乎的话题", kw))
        assertFalse(HupuFilter.blockedThread("步行街日常", "步行街主干道", kw))
    }

    @Test
    fun blockedComment_hit() {
        val kw = HupuFilter.Keywords(comment = listOf("乐子"))
        assertTrue(HupuFilter.blockedComment("纯乐子人发言", kw))
        assertFalse(HupuFilter.blockedComment("理性讨论", kw))
    }

    @Test
    fun stripHtml_entitiesAndTags() {
        val html = "<p>虎扑&nbsp;社区</p><b>加粗</b>&amp;&lt;&gt;"
        val text = HupuFilter.stripHtml(html)
        assertTrue(text.contains("虎扑 社区"))
        assertTrue(text.contains("加粗"))
        assertTrue(text.contains("&<>"))
        assertFalse(text.contains("<p>"))
    }

    @Test
    fun stripHtml_longInputTruncated() {
        val long = "a".repeat(5000)
        assertEquals(2000, HupuFilter.stripHtml(long).length)
    }

    @Test
    fun normalize_trimsAndLowercases() {
        assertEquals("nba", HupuFilter.normalize("  NBA "))
        assertEquals("", HupuFilter.normalize("   "))
        assertFalse(HupuFilter.valid("  "))
        assertTrue(HupuFilter.valid(" 詹姆斯 "))
    }

    // ---------- 1.223：评分评论「关键词」递归过滤（孙评论此前是漏网之鱼） ----------

    private fun sc(
        id: String,
        content: String,
        parentId: String = "",
        subs: List<HupuScoreComment> = emptyList(),
    ) = HupuScoreComment(
        commentId = id,
        userName = "n$id",
        userHead = null,
        content = content,
        score = 0,
        lightCount = 0L,
        date = "",
        ipLocation = "",
        parentCommentId = parentId,
        subComments = subs,
    )

    @Test
    fun pruneTree_keepsClean_dropsHit() {
        val kw = HupuFilter.Keywords(comment = listOf("bad"))
        val list = listOf(sc("1", "hello"), sc("2", "bad word"))
        assertEquals(listOf("1"), HupuFilter.pruneScoreCommentTree(list, kw).map { it.commentId })
    }

    @Test
    fun pruneTree_prunesNestedGrandchild() {
        val kw = HupuFilter.Keywords(comment = listOf("bad"))
        val sub = sc("2", "ok", subs = listOf(sc("3", "grand bad")))
        val out = HupuFilter.pruneScoreCommentTree(listOf(sc("1", "fine", subs = listOf(sub))), kw)
        val kept = out.single().subComments
        assertEquals(listOf("2"), kept.map { it.commentId })
        assertTrue(kept.single().subComments.isEmpty())
    }

    @Test
    fun pruneTree_hitNodeDropsWholeSubtree() {
        val kw = HupuFilter.Keywords(comment = listOf("bad"))
        val hit = sc("2", "bad", subs = listOf(sc("3", "clean")))
        assertTrue(HupuFilter.pruneScoreCommentTree(listOf(hit), kw).isEmpty())
    }

    @Test
    fun pruneTree_emptyKeywordsNoop() {
        val list = listOf(sc("1", "anything bad"))
        assertEquals(list, HupuFilter.pruneScoreCommentTree(list, HupuFilter.Keywords()))
        assertEquals(list, HupuFilter.pruneScoreCommentFlat(list, null, HupuFilter.Keywords()))
    }

    @Test
    fun pruneFlat_dropsSelfHitAndOrphan() {
        val kw = HupuFilter.Keywords(comment = listOf("bad"))
        // 本人命中 → 删
        assertTrue(HupuFilter.pruneScoreCommentFlat(listOf(sc("10", "bad", parentId = "2")), null, kw).isEmpty())
        // 祖先链命中 → 删（避免孤儿）
        val parent = sc("2", "bad root")
        assertTrue(HupuFilter.pruneScoreCommentFlat(listOf(sc("10", "clean child", parentId = "2")), parent, kw).isEmpty())
    }
}