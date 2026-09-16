package com.java.myapplication

import com.java.myapplication.data.HupuImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.157 图片格式嗅探单测（纯字节/字符串逻辑，不依赖 Android API）。
 *
 * 背景（真机反馈）：发出的动图官方端显示为静态、我们这边显示为动图。
 * 根因是上传时按 MIME/文件名后缀判定格式，来源不可靠（表情包 CDN 地址常无 .gif 后缀），
 * 于是 GIF 被当成 jpeg 上传，服务端按静态图登记。改为以字节魔数为准。
 */
class HupuImageSniffTest {

    private fun gif() = "GIF89a".toByteArray(Charsets.US_ASCII) + ByteArray(16)
    private fun png() = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte()) + ByteArray(16)
    private fun jpeg() = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + ByteArray(16)

    /** 动画 WebP：RIFF....WEBP + ANIM chunk */
    private fun animatedWebp(): ByteArray {
        val head = ("RIFF" + "\u0000\u0000\u0000\u0000" + "WEBP").toByteArray(Charsets.US_ASCII)
        val anim = ("ANIM").toByteArray(Charsets.US_ASCII) + byteArrayOf(6, 0, 0, 0) + ByteArray(6)
        return head + anim
    }

    /** 静态 WebP：只有 VP8 chunk */
    private fun staticWebp(): ByteArray {
        val head = ("RIFF" + "\u0000\u0000\u0000\u0000" + "WEBP").toByteArray(Charsets.US_ASCII)
        val vp8 = ("VP8 ").toByteArray(Charsets.US_ASCII) + byteArrayOf(4, 0, 0, 0) + ByteArray(4)
        return head + vp8
    }

    @Test
    fun `魔数优先于任何后缀推断`() {
        assertEquals("gif", HupuImage.sniffExtension(gif()))
        assertEquals("png", HupuImage.sniffExtension(png()))
        assertEquals("jpeg", HupuImage.sniffExtension(jpeg()))
        assertEquals("webp", HupuImage.sniffExtension(staticWebp()))
    }

    @Test
    fun `动图识别_GIF 与动画 WebP 为 true_静态格式为 false`() {
        assertTrue(HupuImage.isAnimated(gif()))
        assertTrue(HupuImage.isAnimated(animatedWebp()))
        assertFalse(HupuImage.isAnimated(staticWebp()))
        assertFalse(HupuImage.isAnimated(png()))
        assertFalse(HupuImage.isAnimated(jpeg()))
    }

    @Test
    fun `无法识别的内容返回 null`() {
        assertNull(HupuImage.sniffExtension(ByteArray(0)))
        assertNull(HupuImage.sniffExtension("hello world, not an image".toByteArray()))
    }

    @Test
    fun `后缀与 MIME 仅作兜底`() {
        assertEquals("gif", HupuImage.extensionFromHints("image/gif"))
        assertEquals("jpeg", HupuImage.extensionFromHints("image/jpeg"))
        assertEquals("png", HupuImage.extensionFromHints("", "sticker_1.PNG"))
        assertEquals("webp", HupuImage.extensionFromHints("application/octet-stream", "a.webp"))
        assertNull(HupuImage.extensionFromHints("", "noext"))
        assertNull(HupuImage.extensionFromHints("image/heic", "IMG_0001.HEIC"))
    }

    @Test
    fun `动画 WebP 单列识别_GIF 不算`() {
        assertTrue(HupuImage.isAnimatedWebp(animatedWebp()))
        assertFalse(HupuImage.isAnimatedWebp(staticWebp()))
        // GIF 也是动图，但不是「动画 WebP」——发送提示只针对 WebP 这一种
        assertFalse(HupuImage.isAnimatedWebp(gif()))
        assertFalse(HupuImage.isAnimatedWebp(png()))
    }

    /**
     * 本轮真机问题的回归点：GIF 字节 + 误导性的 .jpg 文件名 / image-jpeg MIME，
     * 必须仍判成 gif —— 这正是「表情包落盘成 .jpg，上传后被服务端当静态图」的现场。
     */
    @Test
    fun `GIF 字节配上 jpg 后缀仍判为 gif`() {
        val bytes = gif()
        assertEquals("gif", HupuImage.sniffExtension(bytes))
        // 旧逻辑会在这里得到 "jpeg"（后缀/MIME 说了算），新逻辑以字节为准
        assertEquals("jpeg", HupuImage.extensionFromHints("image/jpeg", "sticker_123.jpg"))
        assertTrue(HupuImage.isAnimated(bytes))
    }
}
