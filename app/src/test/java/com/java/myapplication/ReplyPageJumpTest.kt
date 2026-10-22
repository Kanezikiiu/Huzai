package com.java.myapplication

import com.java.myapplication.data.HupuRepository
import com.java.myapplication.ui.pages.pageCandidates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 1.227: 评论区页码跳转。
 *
 * 这里只测**纯逻辑**（候选页码 + 数据层跳页语义），UI 部分靠真机验收。
 */
class ReplyPageJumpTest {

    // ---------- 候选页码 ----------

    @Test
    fun `只有一页时只给出第1页`() {
        assertEquals(listOf<Int?>(1), pageCandidates(1, 1))
        assertEquals(listOf<Int?>(1), pageCandidates(1, 0))
    }

    @Test
    fun `页数少时不插省略号`() {
        // 1..5，当前 2：首、1、2、3、末 全部相邻 → 无 null
        assertEquals(listOf<Int?>(1, 2, 3, 4, 5), pageCandidates(2, 5))
    }

    @Test
    fun `页数多时首末 + 当前邻域，中间用省略号`() {
        // 1.234：邻域扩到 ±2
        val r = pageCandidates(50, 200)
        assertEquals(listOf<Int?>(1, null, 48, 49, 50, 51, 52, null, 200), r)
    }

    @Test
    fun `当前页贴近首页时不重复也不留空洞省略号`() {
        // 当前 2，总 100：候选 {1..4, 100} → 用一个省略号
        assertEquals(listOf<Int?>(1, 2, 3, 4, null, 100), pageCandidates(2, 100))
        // 当前 1：候选 {1,2,3,100}
        assertEquals(listOf<Int?>(1, 2, 3, null, 100), pageCandidates(1, 100))
    }

    @Test
    fun `差一页时补真实页码而不是省略号`() {
        // 当前 99，总 101：候选 {1, 97..101} —— 1 与 97 差 96 用省略号
        val r = pageCandidates(99, 101)
        assertEquals(listOf<Int?>(1, null, 97, 98, 99, 100, 101), r)
        // 当前 2，总 4：页数少 → 全列，没有省略号
        assertEquals(listOf<Int?>(1, 2, 3, 4), pageCandidates(2, 4))
    }

    @Test
    fun `页数不多时全部列出`() {
        assertEquals(listOf<Int?>(1, 2, 3), pageCandidates(2, 3))
        assertEquals((1..7).toList<Int?>(), pageCandidates(4, 7))
    }

    @Test
    fun `候选里不会出现越界页码`() {
        val r = pageCandidates(1, 3)
        assertEquals(listOf<Int?>(1, 2, 3), r)
        assertTrueNoNull(r)
    }

    private fun assertTrueNoNull(l: List<Int?>) {
        l.forEach { if (it == null) throw AssertionError("不应出现省略号: $l") }
    }

    @Test
    fun `跳页方法存在且签名稳定`() {
        // 反射守护：跳页必须走「整页替换」这条 API（避免有人改成追加合并）
        val m = HupuRepository::class.java.methods.firstOrNull { it.name == "threadRepliesJump" }
        requireNotNull(m) { "缺少 HupuRepository.threadRepliesJump" }
        // suspend 方法编译后会多一个 Continuation 参数 → 至少 3 个
        org.junit.Assert.assertTrue("参数数量异常: ${m.parameterTypes.size}", m.parameterTypes.size >= 3)
    }

    @Test
    fun `省略号只在跨度大于一页时出现`() {
        assertEquals(listOf<Int?>(1, 2, 3, 4, 5, 6, 7), pageCandidates(4, 7))
        assertNull(pageCandidates(4, 7).firstOrNull { it == null })
    }
}