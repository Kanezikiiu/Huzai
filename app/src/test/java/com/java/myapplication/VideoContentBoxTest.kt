package com.java.myapplication

import com.java.myapplication.ui.player.ContentBox
import com.java.myapplication.ui.player.contentZoom
import com.java.myapplication.ui.player.detectContentBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 视频画面自带黑边检测（纯函数）：
 * 虎扑不少视频把真实画面压在一块更大的画布里（实测某帖画面仅占 48%×41%），
 * 这类黑边属于视频内容，必须靠抓帧检测 + 放大裁掉。
 */
class VideoContentBoxTest {

    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()

    /** 造一帧：外圈 [padX]/[padY] 比例的留边，中心为亮画面 */
    private fun framed(w: Int, h: Int, padX: Int, padY: Int, pad: Int = black): IntArray {
        val px = IntArray(w * h) { pad }
        for (y in padY until h - padY) {
            for (x in padX until w - padX) px[y * w + x] = white
        }
        return px
    }

    // ---------- detectContentBox ----------

    @Test
    fun fullBleedFrameHasNoPadding() {
        val w = 40
        val h = 24
        val box = detectContentBox(IntArray(w * h) { white }, w, h)!!
        assertEquals(0f, box.left, 0.001f)
        assertEquals(1f, box.bottom, 0.001f)
        assertFalse(box.hasPadding)
    }

    @Test
    fun letterboxDetected() {
        val w = 40
        val h = 40
        val box = detectContentBox(framed(w, h, 0, 10), w, h)!!
        assertEquals(0.25f, box.top, 0.02f)
        assertEquals(0.75f, box.bottom, 0.02f)
        assertTrue(box.hasPadding)
    }

    @Test
    fun fourSidePaddingDetected() {
        // 真机案例形态：四周都有留边（画面只占中间一小块）
        val w = 40
        val h = 40
        val box = detectContentBox(framed(w, h, 10, 12), w, h)!!
        assertEquals(0.25f, box.left, 0.03f)
        assertEquals(0.75f, box.right, 0.03f)
        assertEquals(0.30f, box.top, 0.03f)
        assertEquals(0.70f, box.bottom, 0.03f)
        assertTrue(box.hasPadding)
    }

    @Test
    fun darkGreyPaddingAlsoDetected() {
        // 转码黑边常是深灰/渐变，不是纯黑
        val w = 40
        val h = 40
        val box = detectContentBox(framed(w, h, 0, 10, 0xFF303030.toInt()), w, h)!!
        assertEquals(0.25f, box.top, 0.02f)
        assertTrue(box.hasPadding)
    }

    @Test
    fun uniformDimFrameIsKept() {
        val box = detectContentBox(IntArray(32 * 32) { 0xFF1A1A1A.toInt() }, 32, 32)!!
        assertFalse(box.hasPadding)
    }

    @Test
    fun allBlackReturnsNull() {
        assertNull(detectContentBox(IntArray(16 * 16) { black }, 16, 16))
    }

    // ---------- contentZoom ----------

    @Test
    fun zoomMakesContentAsLargeAsPossibleWithoutOverflow() {
        // 内容占 48%×41%（真机实测形态）
        val box = ContentBox(0.26f, 0.30f, 0.74f, 0.71f)
        val (k, dx, dy) = contentZoom(box)
        // 取 min(1/0.48, 1/0.41) = 2.06 → 放大后宽度正好铺满、高度不超
        assertEquals(2.06f, k, 0.05f)
        assertEquals(0.5f, box.centerX, 0.01f)
        assertEquals(0.0f, dx, 0.01f)
        assertEquals(0.0f, dy, 0.01f)
    }

    @Test
    fun zoomNeverExceedsBounds() {
        val box = ContentBox(0.1f, 0.1f, 0.9f, 0.5f) // 宽 80%、高 40%
        val (k, _, _) = contentZoom(box)
        assertEquals(1.25f, k, 0.001f) // min(1/0.8, 1/0.4) = 1.25（取小者，保证不超出）
        assertTrue(box.width * k <= 1.0001f)
        assertTrue(box.height * k <= 1.0001f)
    }

    @Test
    fun zoomIsClamped() {
        val box = ContentBox(0.45f, 0.45f, 0.55f, 0.55f) // 极小内容
        val (k, _, _) = contentZoom(box)
        assertTrue(k <= 2.6f)
    }
}