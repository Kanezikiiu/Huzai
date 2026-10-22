package com.java.myapplication

import com.java.myapplication.data.HupuApi
import com.java.myapplication.data.HupuParser
import com.java.myapplication.data.HupuReply
import com.java.myapplication.data.HupuReplyMediaIndex
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.226i：接口层视频注入全链路。
 *
 * 结构取自真实帖子 643087279 的子回复 pid 23931：
 *  - 帖子 SSR 的 replies.list 里，23931 带 video/videoCover
 *  - 楼中楼接口返回的 23931 没有 video
 */
class HupuInjectVideoTest {

    private fun ssrHtml(): String {
        val reply = JSONObject()
            .put("pid", "23931")
            .put("content", "")
            .put("video", "https://v.hoopchina.com.cn/x.mp4")
            .put("videoCover", "https://i1.hoopchina.com.cn/x.jpg")
            .put("author", JSONObject().put("puid", "1").put("puname", "神机妙算范少伯"))
        val list = JSONArray().put(reply)
        val detail = JSONObject()
            .put("thread", JSONObject().put("tid", "643087279").put("title", "t"))
            .put("replies", JSONObject().put("list", list))
        val root = JSONObject().put(
            "props",
            JSONObject().put("pageProps", JSONObject().put("detail", detail)),
        )
        return """<script id="__NEXT_DATA__" type="application/json">$root</script>"""
    }

    private fun floorJson(): String =
        """{"data":{"post":{"content":"母楼"},"replies":[
             {"pid":"23931","content":"","lights":10,"replies":2,
              "user":{"puid":1,"username":"神机妙算范少伯","createDt":"10-20"}}]}}"""

    @Test
    fun `从 SSR HTML 建索引`() {
        val idx = HupuParser.replyVideoIndexFromHtml(ssrHtml())
        assertEquals("https://v.hoopchina.com.cn/x.mp4", idx["23931"]?.first)
        assertEquals("https://i1.hoopchina.com.cn/x.jpg", idx["23931"]?.second)
    }

    @Test
    fun `接口层注入后解析出视频`() {
        HupuReplyMediaIndex.put("643087279", HupuParser.replyVideoIndexFromHtml(ssrHtml()))
        val injected = HupuApi.injectReplyVideos("643087279", floorJson())!!
        assertTrue(injected.contains("https://v.hoopchina.com.cn/x.mp4"))
        val fr = HupuParser.parseFloorReplies(injected, HupuReply(pid = "200"))!!
        val sub = fr.subReplies.single()
        assertEquals("https://v.hoopchina.com.cn/x.mp4", sub.videoUrl)
        assertEquals("https://i1.hoopchina.com.cn/x.jpg", sub.videoCover)
    }
}