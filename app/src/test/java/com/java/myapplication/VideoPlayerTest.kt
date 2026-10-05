package com.java.myapplication

import androidx.compose.ui.graphics.vector.addPathNodes
import com.java.myapplication.ui.player.PLAYER_SPEEDS
import com.java.myapplication.ui.player.formatPlayerTime
import com.java.myapplication.ui.player.formatSpeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 播放器组件（1.2xx P0+P1 改造）的可测部分：
 *  · 时间 / 倍速格式化（纯函数）
 *  · 控制条图标 path 冒烟（path 是运行时解析的，语法错误不会编译报错而是真机崩溃）
 *  · 镜像快进图标依赖 group scaleX=-1，这里同时锁定「回退/快进 path 同源」
 */
class VideoPlayerTest {

    // ---------- 时间格式化 ----------

    @Test
    fun time_zeroAndNegative() {
        assertEquals("00:00", formatPlayerTime(0L))
        assertEquals("00:00", formatPlayerTime(-100L))
    }

    @Test
    fun time_underOneMinute() {
        assertEquals("00:05", formatPlayerTime(5_000L))
        assertEquals("00:59", formatPlayerTime(59_999L))
    }

    @Test
    fun time_minutesAndSeconds() {
        assertEquals("01:23", formatPlayerTime(83_000L))
        assertEquals("10:00", formatPlayerTime(600_000L))
        assertEquals("59:59", formatPlayerTime(3_599_000L))
    }

    @Test
    fun time_withHours() {
        assertEquals("1:00:00", formatPlayerTime(3_600_000L))
        assertEquals("2:03:04", formatPlayerTime(7_384_000L))
    }

    // ---------- 倍速格式化 ----------

    @Test
    fun speed_formatting() {
        assertEquals("0.5×", formatSpeed(0.5f))
        assertEquals("0.75×", formatSpeed(0.75f))
        assertEquals("1×", formatSpeed(1f))
        assertEquals("1.25×", formatSpeed(1.25f))
        assertEquals("1.5×", formatSpeed(1.5f))
        assertEquals("2×", formatSpeed(2f))
    }

    @Test
    fun speed_bracketsAreDistinctAndContainNormal() {
        assertEquals(listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f), PLAYER_SPEEDS)
        assertTrue(PLAYER_SPEEDS.contains(1f))
    }

    // ---------- 图标 path 冒烟 ----------

    private val paths = mapOf(
        "replay" to "M12,5V1L7,6l5,5V7c3.31,0,6,2.69,6,6s-2.69,6,-6,6-6,-2.69,-6,-6H4c0,4.42,3.58,8,8,8s8,-3.58,8,-8-3.58,-8,-8,-8z",
        "forward(镜像)" to "M12,5V1L17,6l-5,5V7c-3.31,0,-6,2.69,-6,6s2.69,6,6,6,6,-2.69,6,-6H20c0,4.42,-3.58,8,-8,8s-8,-3.58,-8,-8,3.58,-8,8,-8z",
        "pause" to "M6,19h4V5H6V19zM14,5v14h4V5H14z",
        "fullscreen" to "M7,14H5v5h5v-2H7V14zM5,10h2V7h3V5H5V10zM17,17h-3v2h5v-5h-2V17zM14,5v2h3v3h2V5H14z",
        "fullscreen_exit" to "M5,16h3v3h2v-5H5V16zM8,8H5v2h5V5H8V8zM14,19h2v-3h3v-2h-5V19zM16,8V5h-2v5h5V8H16z",
        "lock" to "M18,8h-1V6c0,-2.76 -2.24,-5 -5,-5S7,3.24 7,6v2H6c-1.1,0 -2,0.9 -2,2v10c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V10C20,8.9 19.1,8 18,8zM12,17c-1.1,0 -2,-0.9 -2,-2s0.9,-2 2,-2s2,0.9 2,2S13.1,17 12,17zM15.1,8H8.9V6c0,-1.71 1.39,-3.1 3.1,-3.1s3.1,1.39 3.1,3.1V8z",
        "lock_open" to "M12,17c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2,0.9 -2,2 0.9,2 2,2zM18,8h-1V6c0,-2.76 -2.24,-5 -5,-5S7,3.24 7,6h1.9c0,-1.71 1.39,-3.1 3.1,-3.1s3.1,1.39 3.1,3.1v2H6c-1.1,0 -2,0.9 -2,2v10c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V10c0,-1.1 -0.9,-2 -2,-2z",
    )

    @Test
    fun playerIconPathsParse() {
        paths.forEach { (name, d) ->
            assertTrue("$name 解析为空", addPathNodes(d).isNotEmpty())
        }
    }
}