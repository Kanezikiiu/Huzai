package com.java.myapplication

import com.java.myapplication.data.HupuMatchParser
import com.java.myapplication.data.HupuParser
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.224p：评论区「视频 / 语音」的**查看**能力（不做发送）。
 *
 * 覆盖三种真实形态：
 *  1) 楼中楼 API：内容 HTML 里直接内嵌 `<video src poster>`（单/双引号都见过）
 *  2) 帖子评论 SSR：视频在 `video` / `videoCover` 独立字段
 *  3) 语音附件：帖子用 `ancillaryContents`，评分用 `commentAncillaryContents`，类型键不同
 */
class HupuReplyMediaTest {

    @Test
    fun `正文内嵌 video 标签切成视频块（双引号）`() {
        val html = "<video preload='none' src=\"https://v.hoopchina.com.cn/a.mp4?auth_key=1\" " +
            "poster=\"https://i1.hoopchina.com.cn/a.jpg\" controls=\"controls\"></video>"
        val toks = HupuParser.contentTokens(html)
        val v = toks.filterIsInstance<HupuParser.ContentToken.Video>().single()
        assertEquals("https://v.hoopchina.com.cn/a.mp4?auth_key=1", v.url)
        assertEquals("https://i1.hoopchina.com.cn/a.jpg", v.cover)
    }

    @Test
    fun `正文内嵌 video 标签切成视频块（单引号）`() {
        val html = "<p>看这个</p><video src='https://v.hoopchina.com.cn/b.mp4' " +
            "poster='https://i5.hoopchina.com.cn/b.jpg'>视频无法播放</video>"
        val toks = HupuParser.contentTokens(html)
        assertTrue(toks.first() is HupuParser.ContentToken.Text)
        val v = toks.filterIsInstance<HupuParser.ContentToken.Video>().single()
        assertEquals("https://v.hoopchina.com.cn/b.mp4", v.url)
        assertEquals("https://i5.hoopchina.com.cn/b.jpg", v.cover)
        // 尾随的「视频无法播放」不应变成正文（它属于 video 标签内部，已被切走）
        assertTrue(toks.none { it is HupuParser.ContentToken.Text && it.html.contains("视频无法播放") })
    }

    @Test
    fun `正文内嵌 audio 标签切成语音块`() {
        val html = "<p>听听这个</p><audio src=\"https://audio.hoopchina.com.cn/x.mp3?a=1\" controls></audio>"
        val toks = HupuParser.contentTokens(html)
        val a = toks.filterIsInstance<HupuParser.ContentToken.Audio>().single()
        assertEquals("https://audio.hoopchina.com.cn/x.mp3?a=1", a.url)
        // audio 标签内部的降级文案不会被当成正文
        assertTrue(toks.none { it is HupuParser.ContentToken.Text && it.html.contains("audio") })
    }

    @Test
    fun `无类型字段但地址像音频也识别为语音`() {
        val arr = JSONArray("""[{"audioUrl":"https://audio.hoopchina.com.cn/y.mp3"}]""")
        assertEquals("https://audio.hoopchina.com.cn/y.mp3" to 0, HupuParser.audioFrom(arr))
        // 图片不会被误判
        val img = JSONArray("""[{"url":"https://i1.hoopchina.com.cn/z.jpg"}]""")
        assertEquals("" to 0, HupuParser.audioFrom(img))
    }

    @Test
    fun `无 src 的 video 标签不产生视频块`() {
        val toks = HupuParser.contentTokens("<video controls></video>")
        assertTrue(toks.none { it is HupuParser.ContentToken.Video })
    }

    @Test
    fun `帖子评论的语音附件从 ancillaryContents 取出`() {
        val arr = JSONArray(
            """[{"content":"https://i1.hoopchina.com.cn/x.jpg","contentType":"IMAGE"},{"content":"https://audio.hoopchina.com.cn/a.mp3","contentType":"AUDIO","durationInSec":51}]""",
        )
        val (url, dur) = HupuParser.audioFrom(arr)
        assertEquals("https://audio.hoopchina.com.cn/a.mp3", url)
        assertEquals(51, dur)
    }

    @Test
    fun `评分评论的语音附件从 commentAncillaryContents 取出`() {
        val arr = JSONArray(
            """[{"commentContent":"https://i11.hoopchina.com.cn/x.heic","commentContentType":"IMAGE","durationInSec":null}]""",
        )
        // 只有图片 → 无语音
        assertEquals("" to 0, HupuParser.audioFrom(arr))

        val arr2 = JSONArray(
            """[{"commentContent":"https://audio.hoopchina.com.cn/b.mp3?auth_key=9","commentContentType":"AUDIO","durationInSec":12}]""",
        )
        assertEquals("https://audio.hoopchina.com.cn/b.mp3?auth_key=9" to 12, HupuParser.audioFrom(arr2))
    }

    @Test
    fun `占位文案剥离同时覆盖语音标记与升级提示`() {
        assertEquals("", HupuParser.stripAudioPlaceholder("[语音]升级新版本后体验"))
        assertEquals("今天不错", HupuParser.stripAudioPlaceholder("[语音]今天不错"))
        // 没有占位时原样保留（只 trim）
        assertEquals("普通评论", HupuParser.stripAudioPlaceholder(" 普通评论 "))
    }

    @Test
    fun `评分楼中楼的语音（ancillaryContents 且正文为空）也能解析`() {
        val json = """
            {"code":1,"data":{"comments":[{"commentId":"1","commentUserName":"A","commentContent":"主评论",
            "commentKey":{"subjectId":"s1","commentId":"1"},
            "subCommentList":[{"commentId":"2","commentUserName":"B","commentContent":"",
              "ancillaryContents":[{"commentContent":"https://audio.hoopchina.com.cn/sub.mp3",
              "commentContentType":"AUDIO","durationInSec":7}],
              "commentKey":{"subjectId":"s1","commentId":"2"}}]}],
            "commentCount":1,"cursor":{"publishTime":1},"hasMore":false}}
        """.trimIndent()
        val st = HupuMatchParser.parseComments(json)!!
        val sub = st.comments.single().subComments.single()
        assertEquals("https://audio.hoopchina.com.cn/sub.mp3", sub.audioUrl)
        assertEquals(7, sub.audioDurationSec)
    }

    @Test
    fun `语音评论解析后剥掉正文里的语音占位`() {
        val json = """
            {"code":1,"data":{"comments":[{"commentId":"3007105055","commentUserName":"虎扑JR1",
            "commentUserId":116847551,"commentContent":"[语音]升级新版本后体验",
            "commentAncillaryContents":[{"commentContent":"https://audio.hoopchina.com.cn/c.mp3",
            "commentContentType":"AUDIO","durationInSec":51}],
            "commentKey":{"subjectId":"375874334","commentId":"3007105055"}}],
            "commentCount":1,"cursor":{"publishTime":1},"hasMore":false}}
        """.trimIndent()
        val st = HupuMatchParser.parseComments(json)!!
        val c = st.comments.single()
        // 语音占位（含官方「升级新版本后体验」模板）整段剥掉 → 正文为空，只剩语音条
        assertEquals("", c.content)
        assertEquals("https://audio.hoopchina.com.cn/c.mp3", c.audioUrl)
        assertEquals(51, c.audioDurationSec)
    }
}