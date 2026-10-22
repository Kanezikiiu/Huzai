package com.java.myapplication

import com.java.myapplication.data.HupuReplyMediaIndex
import com.java.myapplication.data.HupuSubReply
import org.junit.Assert.assertEquals
import org.junit.Test

/** 1.226g：楼中楼视频回填（SSR lights 索引 → 子回复） */
class HupuReplyMediaIndexTest {

    @Test
    fun `按 pid 回填视频与封面`() {
        HupuReplyMediaIndex.put(
            "643087279",
            mapOf("23931" to ("https://v.hoopchina.com.cn/x.mp4" to "https://i1.hoopchina.com.cn/x.jpg")),
        )
        val subs = listOf(
            HupuSubReply(pid = "1", contentHtml = "普通回复"),
            HupuSubReply(pid = "23931", contentHtml = ""),
        )
        val out = HupuReplyMediaIndex.applyTo("643087279", subs)
        assertEquals("", out[0].videoUrl)
        assertEquals("https://v.hoopchina.com.cn/x.mp4", out[1].videoUrl)
        assertEquals("https://i1.hoopchina.com.cn/x.jpg", out[1].videoCover)
    }

    @Test
    fun `已有视频不覆盖`() {
        HupuReplyMediaIndex.put("t2", mapOf("9" to ("https://new.mp4" to "c")))
        val subs = listOf(HupuSubReply(pid = "9", videoUrl = "https://old.mp4", videoCover = "old"))
        val out = HupuReplyMediaIndex.applyTo("t2", subs)
        assertEquals("https://old.mp4", out.single().videoUrl)
    }

    @Test
    fun `无索引时原样返回`() {
        val subs = listOf(HupuSubReply(pid = "x"))
        assertEquals(subs, HupuReplyMediaIndex.applyTo("not-exist", subs))
    }
}