package com.java.myapplication.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** 刷新率档位列表构建：同刷新率去重（保留高分辨率）、按刷新率降序 */
class HupuRefreshTest {
    private fun m(id: Int, w: Int, h: Int, r: Float) = HupuRefresh.ModeInfo(id, w, h, r)

    @Test
    fun buildModes_dedupsSameRate_keepsHighestRes() {
        val out = HupuRefresh.buildModes(
            listOf(
                m(1, 1080, 2400, 60.0f),
                m(2, 720, 1600, 60.02f),   // 同档（四舍五入 60），低分辨率，应被去重
                m(3, 1080, 2400, 90.0f),
                m(4, 1080, 2400, 120.05f),
                m(5, 1080, 2400, 119.97f), // 同档 120，低分辨率，应被去重
            ),
        )
        assertEquals(listOf(4, 3, 1), out.map { it.modeId })
        assertEquals(listOf(120, 90, 60), out.map { Math.round(it.refresh) })
    }

    @Test
    fun buildModes_emptyAndSingle() {
        assertEquals(0, HupuRefresh.buildModes(emptyList()).size)
        val one = HupuRefresh.buildModes(listOf(m(7, 1440, 3200, 144.0f)))
        assertEquals(1, one.size)
        assertEquals(7, one[0].modeId)
    }
}
