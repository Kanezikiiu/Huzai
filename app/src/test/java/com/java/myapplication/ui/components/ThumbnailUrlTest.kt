package com.java.myapplication.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 1.190: 小图位 CDN 缩略图 URL 单测。
 *
 * 背景：用户主页/信息流/搜索里几十张封面若都下原图（实测 1256px 宽 155KB），
 * 会把带宽打满并抢走同屏其它请求的时间 → 资料卡迟迟不回来（骨架屏久）、滑动发涩。
 * 加 `x-oss-process=image/resize,w_N` 后实测 14KB（约 1/11）。
 */
class ThumbnailUrlTest {

    @Test
    fun appendsResizeParamWhenNoQuery() {
        val u = thumbnailUrl("https://i11.hoopchina.com.cn/a_w_1256_h_686_.jpg", 240)
        assertEquals(
            "https://i11.hoopchina.com.cn/a_w_1256_h_686_.jpg?x-oss-process=image/resize,w_240",
            u
        )
    }

    @Test
    fun usesAmpersandWhenQueryAlreadyPresent() {
        val u = thumbnailUrl("https://i1.hoopchina.com.cn/b.jpg?v=2", 320)
        assertEquals("https://i1.hoopchina.com.cn/b.jpg?v=2&x-oss-process=image/resize,w_320", u)
    }

    @Test
    fun keepsExistingOssProcessUntouched() {
        // 头像已带 m_fill 裁切参数：不能覆盖（覆盖会破坏圆形裁切）
        val avatar = "https://i2.hoopchina.com.cn/user/1/x.jpg?x-oss-process=image/resize,m_fill,w_150,h_150"
        assertEquals(avatar, thumbnailUrl(avatar, 240))
    }

    @Test
    fun skipsGif() {
        // 缩放会丢动图，GIF 原样返回
        val gif = "https://i5.hoopchina.com.cn/a.gif"
        assertEquals(gif, thumbnailUrl(gif, 240))
    }

    @Test
    fun leavesNonHupuHostUntouched() {
        val other = "https://example.com/a.jpg"
        assertEquals(other, thumbnailUrl(other, 240))
    }

    @Test
    fun upgradesHttpAndProtocolRelative() {
        assertEquals(
            "https://i3.hoopchina.com.cn/c.jpg?x-oss-process=image/resize,w_168",
            thumbnailUrl("http://i3.hoopchina.com.cn/c.jpg", 168)
        )
        assertEquals(
            "https://i3.hoopchina.com.cn/c.jpg?x-oss-process=image/resize,w_168",
            thumbnailUrl("//i3.hoopchina.com.cn/c.jpg", 168)
        )
    }

    @Test
    fun nullAndBlankReturnNull() {
        assertNull(thumbnailUrl(null, 240))
        assertNull(thumbnailUrl("", 240))
        assertNull(thumbnailUrl("   ", 240))
    }

    @Test
    fun nonPositiveWidthSkipsResize() {
        assertEquals("https://i3.hoopchina.com.cn/c.jpg", thumbnailUrl("https://i3.hoopchina.com.cn/c.jpg", 0))
    }
}