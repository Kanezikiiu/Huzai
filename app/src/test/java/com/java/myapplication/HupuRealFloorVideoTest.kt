package com.java.myapplication

import com.java.myapplication.data.HupuApi
import com.java.myapplication.data.HupuParser
import com.java.myapplication.data.HupuReply
import com.java.myapplication.data.HupuReplyMediaIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.226j：用**真实抓取**的两个文件跑全链路：
 *  - thread_643087279.html：帖子 SSR（桌面 UA）
 *  - floor_643087279_23813.json：楼中楼接口原始响应
 *
 * 目标子回复：pid 23931（用户名 神机妙算范少伯）
 */
class HupuRealFloorVideoTest {

    private fun read(name: String): String =
        javaClass.classLoader!!.getResourceAsStream(name)!!.bufferedReader().readText()

    @Test
    fun `真实 SSR 能建出 23931 的视频索引`() {
        val html = read("thread_643087279.html")
        val idx = HupuParser.replyVideoIndexFromHtml(html)
        assertTrue("索引里没有 23931，实际 keys=${idx.keys.take(10)}", idx.containsKey("23931"))
        assertTrue(idx["23931"]!!.first.contains(".mp4"))
    }

    @Test
    fun `子回复在 SSR 平铺条目里带 quotePid（通知定位母楼的数据基础）`() {
        // 1.252：消息通知里的 pid 可能是楼中楼里的子回复。
        // 服务端 `?pid=子pid` 返回的页里，该子回复会以**平铺条目**出现且带 quote.pid，
        // 我们据此就能找到它所属的母楼、自动展开楼中楼。这条测试守住该数据契约。
        val html = read("thread_643087279.html")
        val d = HupuParser.parseThreadDetail(html)!!
        val sub = d.replies.firstOrNull { it.pid == "23931" }
        assertTrue("SSR 里没有子回复 23931", sub != null)
        assertEquals("23813", sub!!.quotePid)
        assertTrue("子回复条目应被识别为 isSubReply", sub.isSubReply)
    }

    @Test
    fun `真实楼中楼 JSON 注入后解析出视频`() {
        val html = read("thread_643087279.html")
        HupuReplyMediaIndex.put("643087279", HupuParser.replyVideoIndexFromHtml(html))
        val raw = read("floor_643087279_23813.json")
        val injected = HupuApi.injectReplyVideos("643087279", raw)!!
        val fr = HupuParser.parseFloorReplies(injected, HupuReply(pid = "23813"))!!
        val sub = fr.subReplies.first { it.pid == "23931" }
        assertTrue("视频地址为空：$sub", sub.videoUrl.contains(".mp4"))
    }
}