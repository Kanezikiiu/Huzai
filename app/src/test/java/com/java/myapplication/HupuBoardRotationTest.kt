package com.java.myapplication

import com.java.myapplication.data.HupuBoardRotation
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.358：首页热帖「随机抽板块」的随机性。
 *
 * ## 真机反馈的问题
 * 用户感觉"随机很伪，每次抽的专区像有顺序"。根因是旧实现用了**不放回抽样**：
 * 抽过的板块从剩余池移除，于是前几次刷新**必定**各不相同（像在按顺序遍历），
 * 且第 4 次剩余池只剩 1 个板块 → 只取到 1 个（内容量骤减）。
 *
 * 现在改为**有放回抽样**：每次都从完整池重新洗牌，允许与历史重复，
 * 只保证不与「上一次」完全相同。下面把这些不变量固化。
 */
class HupuBoardRotationTest {

    private val pool = HupuBoardRotation.POOL

    @Test
    fun `抽取数量恒等于 n 不会因为池子枯竭而变少`() {
        // 连续抽 30 次（远超池子大小 10），每次都必须拿到 3 个
        var last: List<String> = emptyList()
        repeat(30) {
            val got = HupuBoardRotation.draw(last, 3, Random(it))
            assertEquals("第 ${it + 1} 次应取满 3 个板块", 3, got.size)
            assertEquals("同一次内不应重复", 3, got.toSet().size)
            assertTrue(got.all { it in pool })
            last = got
        }
    }

    @Test
    fun `相邻两次不会完全相同`() {
        var last: List<String> = emptyList()
        repeat(20) { i ->
            val got = HupuBoardRotation.draw(last, 3, Random(i * 31 + 7))
            if (last.isNotEmpty()) {
                assertNotEquals(
                    "连续两次抽到同一组合（用户会觉得刷了没反应）",
                    last.toSet(),
                    got.toSet(),
                )
            }
            last = got
        }
    }

    @Test
    fun `允许与历史重复 不再像遍历`() {
        // 有放回抽样的关键特征：在多次抽取中，同一个板块会重复出现。
        // 旧的不放回实现里，池子耗尽前每个板块最多出现一次（这正是"有顺序"的观感来源）。
        val seenCount = HashMap<String, Int>()
        var last: List<String> = emptyList()
        repeat(12) { i ->
            val got = HupuBoardRotation.draw(last, 3, Random(i + 100))
            got.forEach { seenCount[it] = (seenCount[it] ?: 0) + 1 }
            last = got
        }
        val repeated = seenCount.values.count { it > 1 }
        assertTrue(
            "12 次抽取（共 36 个槽位 / 池子 10 个）必然出现重复，实际重复板块数=$repeated",
            repeated > 0,
        )
    }

    @Test
    fun `固定 seed 可复现`() {
        val a = HupuBoardRotation.draw(emptyList(), 3, Random(42))
        val b = HupuBoardRotation.draw(emptyList(), 3, Random(42))
        assertEquals(a, b)
    }

    @Test
    fun `上滑续页优先取本轮没用过的板块`() {
        val used = mutableSetOf<String>()
        val first = HupuBoardRotation.drawUnused(used, 1, Random(1))
        assertEquals(1, first.size)
        assertTrue(first.all { it in used })

        // 再续 9 次 → 正好覆盖整池，且每次都不是已用过的
        val all = first.toMutableList()
        repeat(9) {
            val got = HupuBoardRotation.drawUnused(used, 1, Random(it + 2))
            assertEquals(1, got.size)
            assertTrue("续页不应重复已用板块", got.first() !in all)
            all += got
        }
        assertEquals("9+1 次应覆盖整池", pool.size, all.toSet().size)

        // 池子用完后继续请求 → 自动重置，不会枯竭返回空
        val after = HupuBoardRotation.drawUnused(used, 1, Random(99))
        assertEquals(1, after.size)
    }
}