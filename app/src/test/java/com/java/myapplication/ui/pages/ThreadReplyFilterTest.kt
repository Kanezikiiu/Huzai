package com.java.myapplication.ui.pages

import com.java.myapplication.data.HupuReply
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.128：最外层评论流过滤 + ①B 自动补齐判定（纯逻辑）。 */
class ThreadReplyFilterTest {

    private fun reply(pid: String, quotePid: String = "") =
        HupuReply(pid = pid, quotePid = quotePid)

    @Test
    fun `topLevelReplies drops sub-replies and keeps true top-level`() {
        val list = listOf(
            reply("100"),
            reply("101", quotePid = "100"),
            reply("102"),
            reply("103", quotePid = "101"),
        )
        val top = topLevelReplies(list)
        assertEquals(listOf("100", "102"), top.map { it.pid })
        assertTrue(top.none { it.isSubReply })
    }

    @Test
    fun `topLevelReplies keeps order and tolerates empty`() {
        assertEquals(emptyList<String>(), topLevelReplies(emptyList()).map { it.pid })
        assertEquals(listOf("a", "b"), topLevelReplies(listOf(reply("a"), reply("b"))).map { it.pid })
    }

    @Test
    fun `auto fill triggers only when sparse and more pages remain`() {
        // 稀疏（13<15）、可加载、不在加载中、非只看楼主 → 触发
        assertTrue(needAutoFillTop(13, canLoadMore = true, loadingMore = false, onlyOp = false))
        // 充足（>=15）→ 不触发
        assertFalse(needAutoFillTop(15, canLoadMore = true, loadingMore = false, onlyOp = false))
        assertFalse(needAutoFillTop(20, canLoadMore = true, loadingMore = false, onlyOp = false))
        // 没有下一页 → 不触发
        assertFalse(needAutoFillTop(3, canLoadMore = false, loadingMore = false, onlyOp = false))
        // 正在加载 → 不触发（防抖）
        assertFalse(needAutoFillTop(3, canLoadMore = true, loadingMore = true, onlyOp = false))
        // 只看楼主自带全量翻页链 → 不触发
        assertFalse(needAutoFillTop(3, canLoadMore = true, loadingMore = false, onlyOp = true))
    }

    @Test
    fun `auto fill threshold matches constant`() {
        assertTrue(needAutoFillTop(TOP_FILL_MIN - 1, true, false, false))
        assertFalse(needAutoFillTop(TOP_FILL_MIN, true, false, false))
    }

    @Test
    fun `mergeFloorSubs keeps optimistic reply and dedups by pid`() {
        val server = listOf(
            com.java.myapplication.data.HupuSubReply(pid = "s1"),
            com.java.myapplication.data.HupuSubReply(pid = "s2"),
        )
        // 本机刚发的乐观回复：服务端还没有 → 必须保留
        val mine = com.java.myapplication.data.HupuSubReply(pid = "s3", contentHtml = "我的评论")
        val merged = mergeFloorSubs(server, listOf(mine))
        assertEquals(listOf("s1", "s2", "s3"), merged.map { it.pid })
        assertTrue(merged.any { it.pid == "s3" })
    }

    @Test
    fun `mergeFloorSubs drops optimistic duplicate once server has real pid`() {
        val server = listOf(com.java.myapplication.data.HupuSubReply(pid = "s3", contentHtml = "服务端版"))
        val mine = com.java.myapplication.data.HupuSubReply(pid = "s3", contentHtml = "乐观版")
        val merged = mergeFloorSubs(server, listOf(mine))
        assertEquals(1, merged.size)
        // 服务端在前 → 保留服务端版本
        assertEquals("服务端版", merged.first().contentHtml)
    }

    @Test
    fun `mergeFloorSubs handles empty base`() {
        val mine = com.java.myapplication.data.HupuSubReply(pid = "x", contentHtml = "c")
        assertEquals(listOf("x"), mergeFloorSubs(emptyList(), listOf(mine)).map { it.pid })
        assertEquals(emptyList<String>(), mergeFloorSubs(emptyList(), emptyList()).map { it.pid })
    }
}