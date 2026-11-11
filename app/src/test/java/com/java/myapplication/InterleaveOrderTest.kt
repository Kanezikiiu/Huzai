package com.java.myapplication

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.359：板块帖子的**交错节奏不应固定**。
 *
 * ## 真机疑问
 * 「抽到的 3 个板块，是不是每次都有固定先后顺序？」
 *
 * 实测：抽取本身是均匀随机的（每个板块落在第 1/2/3 位的概率都≈1/10）。
 * 真正固定的是 `interleave` 的**节拍** —— 旧实现是
 *
 *     A0 B0 C0 | A1 B1 C1 | A2 B2 C2 …
 *
 * 「第 1、4、7…条永远来自 A」，看起来就像板块有固定轮次。
 * 现在每轮三条的顺序也洗牌：`C0 A0 B0 | B1 C1 A1 | …`
 *
 * 这里验证两件事：
 *  ① 每个板块在每轮里**恰好出现一次**（均匀性没被破坏）；
 *  ② 同一板块不再固定落在同一位置（节拍被打散）。
 */
class InterleaveOrderTest {

    /** 与 HupuRepository.interleave 相同的算法（用板块编号代表帖子） */
    private fun interleave(lists: List<List<String>>, random: Random): List<String> {
        val out = ArrayList<String>()
        val maxLen = lists.maxOfOrNull { it.size } ?: 0
        val indices = lists.indices.toList()
        for (i in 0 until maxLen) {
            val order = if (lists.size <= 1) indices else indices.shuffled(random)
            for (idx in order) {
                val l = lists[idx]
                if (i < l.size) out += l[i]
            }
        }
        return out
    }

    private fun sample() = listOf(
        (0 until 4).map { "A$it" },
        (0 until 4).map { "B$it" },
        (0 until 4).map { "C$it" },
    )

    @Test
    fun `每轮三个板块各出现一次`() {
        val out = interleave(sample(), Random(1))
        assertEquals(12, out.size)
        // 分 4 轮，每轮恰好 A/B/C 各一条
        out.chunked(3).forEachIndexed { round, chunk ->
            assertEquals("第 ${round + 1} 轮应有 3 条", 3, chunk.size)
            assertEquals(
                "第 ${round + 1} 轮应覆盖 A/B/C 各一条",
                setOf("A$round", "B$round", "C$round"),
                chunk.toSet(),
            )
        }
    }

    @Test
    fun `同一板块不再固定在同一位置`() {
        // 多次交错，统计 A 落在第 1 位（索引 0）的次数
        val firstPos = HashMap<Int, Int>()
        repeat(60) { seed ->
            val out = interleave(sample(), Random(seed))
            val head = out.first()
            val which = head[0].code - 'A'.code
            firstPos[which] = (firstPos[which] ?: 0) + 1
        }
        // 三个板块都当过"第一条"，说明节拍不再固定为 A 打头
        assertEquals("三个板块都应有打头的机会", 3, firstPos.size)
        firstPos.values.forEach { assertTrue("每个板块打头次数应>0", it > 0) }
    }

    @Test
    fun `单个板块时保持原顺序`() {
        val one = listOf(listOf("A0", "A1", "A2"))
        val out = interleave(one, Random(5))
        assertEquals(listOf("A0", "A1", "A2"), out)
    }

    @Test
    fun `长度不齐时不丢帖子也不重复`() {
        val lists = listOf(
            listOf("A0", "A1", "A2", "A3"),
            listOf("B0"),
            listOf("C0", "C1"),
        )
        val out = interleave(lists, Random(3))
        assertEquals(7, out.size)
        assertEquals("不应重复", 7, out.toSet().size)
        assertEquals(
            "所有原始帖子都要在",
            setOf("A0", "A1", "A2", "A3", "B0", "C0", "C1"),
            out.toSet(),
        )
    }
}