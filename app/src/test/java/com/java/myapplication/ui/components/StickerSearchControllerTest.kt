package com.java.myapplication.ui.components

import com.java.myapplication.data.Adoutu
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.191: 表情搜索状态机单测。
 *
 * 锁住三条容易退化的语义（都直接对应界面表现）：
 *  · 「网络失败」与「搜到 0 条」必须分开 —— 否则一进面板就闪「搜索失败」；
 *  · 翻页失败不能把已有结果清成失败态；
 *  · 翻页不满一页即终止 —— 否则会无限请求下一页。
 *
 * 搜索实现可注入，用例不碰网络（确定性、无 flaky）。
 */
class StickerSearchControllerTest {

    private fun sticker(n: Int) = Adoutu.Sticker(
        id = n.toString(),
        url = "https://img.adoutu.com/picture/$n.jpg",
        title = "t$n",
    )

    private fun page(from: Int, count: Int) = (from until from + count).map { sticker(it) }

    @Test
    fun submit_success_fillsItems_andClearsStates() = runBlocking {
        val c = StickerSearchController(this) { _, _ -> page(1, Adoutu.PAGE_SIZE) }
        c.submit("  猫  ")
        yield()
        assertEquals("关键词去空白后生效", "猫", c.keyword)
        assertEquals(Adoutu.PAGE_SIZE, c.items.size)
        assertFalse(c.loading)
        assertFalse(c.failed)
        assertFalse(c.empty)
        assertFalse("满页 → 还有下一页", c.noMore)
    }

    @Test
    fun submit_emptyResult_marksEmpty_notFailed() = runBlocking {
        val c = StickerSearchController(this) { _, _ -> emptyList() }
        c.submit("zzz")
        yield()
        assertTrue(c.empty)
        assertTrue(c.noMore)
        assertFalse("空结果不是失败", c.failed)
        assertFalse(c.loading)
    }

    @Test
    fun submit_networkFailure_marksFailedOnly() = runBlocking {
        val c = StickerSearchController(this) { _, _ -> null }
        c.submit("猫")
        yield()
        assertTrue(c.failed)
        assertFalse(c.loading)
        assertFalse(c.empty)
        assertTrue(c.items.isEmpty())
    }

    @Test
    fun blankKeyword_isIgnored() = runBlocking {
        var calls = 0
        val c = StickerSearchController(this) { _, _ -> calls++; emptyList() }
        c.submit("   ")
        yield()
        assertEquals(0, calls)
        assertFalse(c.loading)
        assertFalse(c.failed)
    }

    @Test
    fun loadMore_pagesThrough_andStopsOnShortPage() = runBlocking {
        val c = StickerSearchController(this) { _, p ->
            when (p) {
                1 -> page(1, Adoutu.PAGE_SIZE)
                2 -> page(41, Adoutu.PAGE_SIZE)
                else -> page(81, 3)
            }
        }
        c.submit("猫")
        yield()
        c.loadMore()
        yield()
        assertEquals(80, c.items.size)
        assertFalse(c.noMore)
        c.loadMore()
        yield()
        assertEquals(83, c.items.size)
        assertTrue("不满一页 → 到底了", c.noMore)
    }

    @Test
    fun loadMore_dedupesOverlappingResults() = runBlocking {
        // 站点分页发生重叠（第二页 ids 36..45 与第一页 1..40 重叠 5 条）→ 去重后只多 5 条
        val c = StickerSearchController(this) { _, p ->
            if (p == 1) page(1, Adoutu.PAGE_SIZE) else page(36, 10)
        }
        c.submit("猫")
        yield()
        c.loadMore()
        yield()
        assertEquals(45, c.items.size)
        assertEquals(45, c.items.map { it.url }.distinct().size)
    }

    @Test
    fun loadMore_failure_keepsItems_andNeedsExplicitRetry() = runBlocking {
        var page2Calls = 0
        val c = StickerSearchController(this) { _, p ->
            if (p == 1) page(1, Adoutu.PAGE_SIZE) else {
                page2Calls++
                null
            }
        }
        c.submit("猫")
        yield()
        c.loadMore()
        yield()
        assertTrue(c.moreFailed)
        assertEquals("翻页失败不能清空已有结果", Adoutu.PAGE_SIZE, c.items.size)
        assertFalse("也不能整面板变失败态", c.failed)

        c.loadMore() // 未 force → 不自动重试（避免无限重试打站点）
        yield()
        assertEquals(1, page2Calls)

        c.loadMore(force = true)
        yield()
        assertEquals(2, page2Calls)
    }

    @Test
    fun submit_resetsPreviousFailure() = runBlocking {
        var fail = true
        val c = StickerSearchController(this) { _, _ -> if (fail) null else page(1, 5) }
        c.submit("猫")
        yield()
        assertTrue(c.failed)
        fail = false
        c.submit("狗")
        yield()
        assertFalse(c.failed)
        assertEquals(5, c.items.size)
        assertTrue(c.noMore)
    }
}