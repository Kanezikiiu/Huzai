package com.java.myapplication.ui.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.208：半屏面板顶部组件的「圆角内缩」计算。
 *
 * 目标是两条：
 *  ① 圆角适中（常见 20~28dp 机型）时内缩量小于顶部原有留白 → 布局完全不变；
 *  ② 大圆角机型上内缩量随半径增大 → 组件被推进圆角内侧的直边区。
 */
class SheetCornerInsetTest {

    private fun inset(radius: Float, depth: Float, extra: Float = 6f) =
        sheetCornerInset(radius.dp, depth.dp, extra.dp).value

    @Test
    fun `moderate radius keeps top padding unchanged`() {
        // 20dp 圆角：18dp 深处曲线几乎未内缩（0.1dp）
        assertEquals(6.1f, inset(20f, 18f), 0.05f)
        // 常见 24dp 圆角仍远小于搜索行的 12dp 基础留白 → 布局不变
        assertTrue("24dp 圆角应仍小于 12dp 基础留白", inset(24f, 18f) < 12f)
        // 24dp 圆角、25dp 深处（楼中楼标题行）→ 深度已越过圆角，不产生额外留白
        assertEquals(0f, inset(24f, 25f), 0.01f)
    }

    @Test
    fun `large radius pushes top content inward`() {
        // 48dp 圆角、18dp 深处 → 内部约 10.5dp + 6dp 余量
        assertEquals(16.5f, inset(48f, 18f), 0.1f)
        // 半径越大内缩越多（单调）
        assertTrue(inset(60f, 18f) > inset(48f, 18f))
    }

    @Test
    fun `depth beyond radius needs no inset`() {
        // 深度已越过圆角区域（直边区）→ 无需额外留白
        assertEquals(0f, inset(20f, 25f), 0.001f)
        assertEquals(0f, inset(48f, 60f), 0.001f)
    }

    @Test
    fun `unreported radius yields zero inset`() {
        // 设备未上报（半径 0）→ 调用方用回退圆角，不需要额外留白
        assertEquals(0f, inset(0f, 18f), 0.001f)
    }
}