package com.java.myapplication

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.java.myapplication.ui.pages.layoutNbaTable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NBA 数据表的列宽几何。
 *
 * 1.224b：数值列按内容自适应后，需要守住两条不变式：
 *  ① 装得下 → 各列之和（含名称列）**精确等于可用宽度**（不出现左右留白）；
 *  ② 装不下 → 各列**保持内容所需宽度**并横向滚动（绝不压缩到换行 / 省略号）。
 */
class NbaTableLayoutTest {

    private val minNameW = 132.dp

    private fun cols(n: Int, w: Dp): List<Dp> = List(n) { w }

    private fun sumDp(list: List<Dp>): Dp = list.fold(0.dp) { acc, w -> acc + w }

    /** 平板：15 列 × 42dp + 132dp 名称 = 762dp，可用 1200dp → 铺满且不吃内容宽度 */
    @Test
    fun wideScreen_fillsExactly_andGivesEveryColumnExtra() {
        val l = layoutNbaTable(1200.dp, cols(15, 42.dp), minNameW)

        assertFalse(l.scroll)
        assertEquals(1200f, (l.nameW + sumDp(l.cols)).value, 0.01f)
        // 每一列（含名称列）都分到了余量
        assertTrue(l.nameW.value > 132f)
        assertTrue(l.cols.all { it.value > 42f })
    }

    /** 手机：可用宽度不足以放下内容 → 横向滚动，列宽不被压缩 */
    @Test
    fun narrowScreen_keepsContentWidthAndScrolls() {
        val l = layoutNbaTable(360.dp, cols(15, 42.dp), minNameW)

        assertTrue(l.scroll)
        assertEquals(132f, l.nameW.value, 0.01f)
        assertTrue(l.cols.all { it.value == 42f })
    }

    /** 系统字体调大（内容所需列宽 42 → 60dp）：列跟着变宽去滚动，而不是把数据挤到换行 */
    @Test
    fun largeFont_columnsGrowAndScrollInsteadOfSqueezing() {
        val l = layoutNbaTable(400.dp, cols(15, 60.dp), minNameW)

        assertTrue(l.scroll)
        assertTrue(l.cols.all { it.value == 60f })
    }
}