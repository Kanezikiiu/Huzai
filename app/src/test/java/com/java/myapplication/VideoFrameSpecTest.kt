package com.java.myapplication

import com.java.myapplication.ui.player.videoFrameSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 小窗尺寸规则（重写后全组件唯一一条）行为锁定。 */
class VideoFrameSpecTest {

    @Test
    fun ratioAlwaysEqualsContent() {
        val s = videoFrameSpec(360f, 1.78f, 500f)
        assertEquals(1.78f, s.ratio, 0.001f)
        assertEquals(202.2f, s.heightDp, 0.5f)
        assertFalse(s.capped)
    }

    @Test
    fun tallContentIsCappedByMaxHeight() {
        // 竖版内容：自然高度 640dp > 上限 400dp → 截断 400dp，标记 capped（此时应改用 Crop）
        val s = videoFrameSpec(360f, 0.5625f, 400f)
        assertTrue(s.capped)
        assertEquals(400f, s.heightDp, 0.01f)
    }

    @Test
    fun ratioIsClampedToSaneRange() {
        assertEquals(0.4f, videoFrameSpec(100f, 0.05f, 1000f).ratio, 0.001f)
        assertEquals(2.6f, videoFrameSpec(100f, 9f, 1000f).ratio, 0.001f)
    }

    @Test
    fun zeroWidthGivesZeroHeight() {
        assertEquals(0f, videoFrameSpec(0f, 1f, 100f).heightDp, 0.001f)
    }
}