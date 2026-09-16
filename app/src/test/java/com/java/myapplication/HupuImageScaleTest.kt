package com.java.myapplication

import com.java.myapplication.data.HupuImage
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 1.170 大图降采样单测（纯函数，不依赖 Android API）。
 *
 * 背景（代码审查 M4）：上传/导入超大图时，原实现直接 `BitmapFactory.decodeByteArray`
 * 无采样解码——一张 8000×6000 的照片按 ARGB_8888 需要约 190MB 位图，低内存机型会 OOM。
 * [HupuImage.sampleSizeFor] 按原始尺寸算 2 的幂采样，使长边不超过 2560。
 */
class HupuImageScaleTest {

    @Test
    fun `8000x6000 采样为 4`() {
        // 8000/4=2000、6000/4=1500，均 <= 2560；8000/2=4000 仍偏大 → 需再降一档
        assertEquals(4, HupuImage.sampleSizeFor(8000, 6000))
    }

    @Test
    fun `长边不超过 2560 时不采样`() {
        assertEquals(1, HupuImage.sampleSizeFor(2560, 2560))
        assertEquals(1, HupuImage.sampleSizeFor(2559, 1440))
        assertEquals(1, HupuImage.sampleSizeFor(1, 1))
    }

    @Test
    fun `5000x5000 采样为 2`() {
        assertEquals(2, HupuImage.sampleSizeFor(5000, 5000))
    }

    @Test
    fun `非正尺寸退化为 1 不崩`() {
        assertEquals(1, HupuImage.sampleSizeFor(0, 100))
        assertEquals(1, HupuImage.sampleSizeFor(100, 0))
        assertEquals(1, HupuImage.sampleSizeFor(-1, -1))
    }

    @Test
    fun `超大尺寸封顶 64`() {
        // 100000/64 = 1562 <= 2560 → 恰好落在 64 档（上限）
        assertEquals(64, HupuImage.sampleSizeFor(100000, 100000))
    }
}
