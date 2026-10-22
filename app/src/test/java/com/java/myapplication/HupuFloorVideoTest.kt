package com.java.myapplication

import com.java.myapplication.data.HupuParser
import com.java.myapplication.data.HupuReply
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 1.226f：楼中楼视频。
 *
 * 移动端接口可能把视频放进 `ancillaryContents`（`contentType=VIDEO`），
 * 此时 `content` 是空串 —— 之前只解析 `<video>` 标签，整条就丢了。
 */
class HupuFloorVideoTest {

    @Test
    fun `附件里的 VIDEO 能取出地址与封面`() {
        val arr = JSONArray(
            """[{"commentContent":"https://v.hoopchina.com.cn/a.mp4","commentContentType":"VIDEO","cover":"https://i1.hoopchina.com.cn/a.jpg"}]""",
        )
        assertEquals(
            "https://v.hoopchina.com.cn/a.mp4" to "https://i1.hoopchina.com.cn/a.jpg",
            HupuParser.videoFrom(arr),
        )
        // 图片不会被当成视频
        assertEquals(
            "" to "",
            HupuParser.videoFrom(JSONArray("""[{"content":"https://x/a.jpg","contentType":"IMAGE"}]""")),
        )
    }

    @Test
    fun `楼中楼子回复：正文为空但视频在 ancillaryContents 里`() {
        val json = """
            {"data":{"post":{"pid":null,"content":"母楼"},"replies":[
              {"pid":300,"content":"","lights":1,"replies":0,"ancillaryContents":[
                 {"commentContent":"https://v.hoopchina.com.cn/b.mp4","commentContentType":"VIDEO",
                  "cover":"https://i1.hoopchina.com.cn/b.jpg"}],
               "user":{"puid":1,"username":"u","header":null,"createDt":"10-18"}}]}}
        """.trimIndent()
        val parent = HupuReply(pid = "200", contentHtml = "母楼", floor = 1)
        val fr = HupuParser.parseFloorReplies(json, parent)!!
        val sub = fr.subReplies.single()
        assertEquals("https://v.hoopchina.com.cn/b.mp4", sub.videoUrl)
        assertEquals("https://i1.hoopchina.com.cn/b.jpg", sub.videoCover)
    }
}