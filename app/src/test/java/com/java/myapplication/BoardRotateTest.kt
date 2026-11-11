package com.java.myapplication

import com.java.myapplication.data.HupuRepository
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.360：板块内帖子的**环形随机旋转**（首屏打散）。
 *
 * ## 真机反馈
 * 「前面的老是熟悉的那几条」——板块虽然随机了，但每个板块返回的都是「热帖 Top70」，
 * 顺序长期稳定，所以列表前几条永远是某板块的 Top1 / Top2，几天不变。
 *
 * 修法：每个板块的列表先做一次环形平移 `drop(off) + take(off)`：
 *  · 不丢帖、集合不变
 *  · 相对热度次序不变（仍是热帖流顺序，只是起点不同）
 *  · off 随机 → 每次刷新"打头"的帖子不同
 */
class BoardRotateTest {

    private val repo: HupuRepository = HupuRepository()

    private fun sample(n: Int = 70) = (1..n).map { "t$it" }

    @Test
    fun `旋转不丢帖 集合与长度不变`() {
        val src = sample()
        repeat(30) { seed ->
            val out = repo.rotateForDisplay(src, random = Random(seed))
            assertEquals("长度必须不变", src.size, out.size)
            assertEquals("集合必须不变（不能丢帖也不能重复）", src.toSet(), out.toSet())
        }
    }

    @Test
    fun `旋转保持相对次序 只是换了起点`() {
        val src = sample(20)
        val out = repo.rotateForDisplay(src, random = Random(3))
        // 在旋转后的列表里找到起点，然后顺时针读应与原列表一致
        val startIdx = src.indexOf(out.first())
        val expected = src.drop(startIdx) + src.take(startIdx)
        assertEquals("应是环形平移，而不是打乱", expected, out)
    }

    @Test
    fun `起点会变 不再固定 Top1`() {
        val src = sample()
        val firsts = HashSet<String>()
        repeat(30) { seed ->
            firsts += repo.rotateForDisplay(src, random = Random(seed)).first()
        }
        assertTrue("多次旋转应有多个不同的打头帖，实际=${firsts.size}", firsts.size > 5)
    }

    @Test
    fun `偏移不会超过上限`() {
        val src = sample(70)
        // 固定上限 18：起点只能在原列表前 19 个位置里挑
        repeat(50) { seed ->
            val out = repo.rotateForDisplay(src, maxOffset = 18, random = Random(seed))
            val startIdx = src.indexOf(out.first())
            assertTrue("起点索引应在 0..18，实际=$startIdx", startIdx in 0..18)
        }
    }

    @Test
    fun `短列表不会越界`() {
        val one = listOf("only")
        assertEquals(one, repo.rotateForDisplay(one, random = Random(1)))
        val two = listOf("a", "b")
        val out = repo.rotateForDisplay(two, random = Random(2))
        assertEquals(setOf("a", "b"), out.toSet())
        assertEquals(2, out.size)
    }

    @Test
    fun `不同 seed 会产生不同起点 不是恒等`() {
        val src = sample()
        val a = repo.rotateForDisplay(src, random = Random(1))
        val b = repo.rotateForDisplay(src, random = Random(2))
        // 绝大多数情况下两者起点不同（不是"永远原样返回"）
        assertTrue(
            "两个不同 seed 至少有一个不是原序（否则说明旋转没生效）",
            a != src || b != src,
        )
        assertNotEquals(0, a.size)
    }
}