package com.java.myapplication

import com.java.myapplication.data.HupuPostApi
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.110 发帖 payload 组装单测（纯函数，不发网络）。
 *
 * 覆盖：视频帖四个字段（videoUrl / videoSnapshotUrl / videoSource / format）、
 * format.videoInfo 与 slateValue 结构、视频帖空正文占位、图文帖不带视频字段、
 * tagIdList 逗号结尾格式。
 */
class HupuPostPayloadTest {

    private fun payload(
        contentHtml: String = "<p>hi</p>",
        videoUrl: String? = null,
        videoCover: String? = null,
    ): JSONObject = HupuPostApi.buildThreadPayload(
        title = "标题四个字",
        contentHtml = contentHtml,
        topicId = 2557,
        zoneId = 0,
        tagIds = listOf(11, 22),
        creationType = "ORIGINAL",
        containsAi = 0,
        visibleRange = "ALL_SEE",
        videoUrl = videoUrl,
        videoCover = videoCover,
        videoBaseName = "clip.mp4",
        contentText = "正文",
    )

    @Test
    fun `图文帖不带视频字段`() {
        val b = payload()
        assertEquals("<p>hi</p>", b.getString("content"))
        assertFalse(b.has("videoUrl"))
        assertFalse(b.has("videoSnapshotUrl"))
        assertFalse(b.has("videoSource"))
        assertFalse(b.has("format"))
    }

    @Test
    fun `tagIdList 为逗号结尾字符串`() {
        assertEquals("11,22,", payload().getString("tagIdList"))
    }

    @Test
    fun `视频帖四个字段齐全`() {
        val b = payload(videoUrl = "https://v.hoopchina.com.cn/x.mp4", videoCover = "https://img/c.jpg")
        assertEquals("https://v.hoopchina.com.cn/x.mp4", b.getString("videoUrl"))
        assertEquals("https://img/c.jpg", b.getString("videoSnapshotUrl"))
        assertEquals("", b.getString("videoSource"))
        assertTrue(b.has("format"))
    }

    @Test
    fun `format videoInfo 结构与官方一致`() {
        val b = payload(videoUrl = "https://v/x.mp4", videoCover = "https://img/c.jpg")
        val f = JSONObject(b.getString("format"))
        val info = f.getJSONObject("videoInfo")
        assertEquals("https://v/x.mp4", info.getString("remoteUrl"))
        assertEquals("https://img/c.jpg", info.getString("coverUrl"))
        assertTrue(info.getString("key").isNotEmpty())
        // slateValue: [{type:paragraph, children:[{text:...}]}]
        val slate = f.getJSONArray("slateValue")
        assertEquals(1, slate.length())
        val para = slate.getJSONObject(0)
        assertEquals("paragraph", para.getString("type"))
        assertEquals("正文", para.getJSONArray("children").getJSONObject(0).getString("text"))
    }

    @Test
    fun `视频帖无封面时 coverUrl 为空串`() {
        val b = payload(videoUrl = "https://v/x.mp4", videoCover = null)
        val info = JSONObject(b.getString("format")).getJSONObject("videoInfo")
        assertEquals("", info.getString("coverUrl"))
        assertEquals("", b.getString("videoSnapshotUrl"))
    }

    @Test
    fun `视频帖空正文用隐藏 span 占位`() {
        val b = payload(contentHtml = "", videoUrl = "https://v/x.mp4")
        val c = b.getString("content")
        assertTrue(c.startsWith("<span data-time="))
        assertTrue(c.contains("display:none"))
    }

    @Test
    fun `图文帖空正文保持空串不占位`() {
        assertEquals("", payload(contentHtml = "").getString("content"))
    }

    @Test
    fun `buildContent 图片附在正文末尾`() {
        val html = HupuPostApi.buildContent("a\nb", listOf("https://i/1.jpg"))
        assertEquals("<p>a</p><p>b</p><p><img src=\"https://i/1.jpg\"/></p>", html)
    }

    @Test
    fun `buildContent 转义尖括号与与号`() {
        assertEquals("<p>&lt;b&gt;&amp;</p>", HupuPostApi.buildContent("<b>&", emptyList()))
    }

    // ---------- 1.112 编辑相关 ----------

    @Test
    fun `编辑 payload 带 tid`() {
        val b = HupuPostApi.buildThreadPayload(
            title = "标题四个字",
            contentHtml = "<p>hi</p>",
            topicId = 1,
            zoneId = 0,
            tid = "642382804",
        )
        assertEquals("642382804", b.getString("tid"))
    }

    @Test
    fun `发帖 payload 不带 tid`() {
        assertFalse(payload().has("tid"))
    }

    @Test
    fun `stripHtml 段落转换行并去标签`() {
        assertEquals("a\nb", HupuPostApi.stripHtml("<p>a</p><p>b</p>"))
        assertEquals("x", HupuPostApi.stripHtml("<div><span>x</span></div>"))
        assertEquals("l1\nl2", HupuPostApi.stripHtml("l1<br/>l2"))
    }

    @Test
    fun `stripHtml 丢弃隐藏占位与图片`() {
        // 视频帖的空正文占位
        assertEquals(
            "",
            HupuPostApi.stripHtml("<span data-time=\"1\" style=\"display:none\"></span>"),
        )
        // 图片节点不进文本域
        assertEquals("文字", HupuPostApi.stripHtml("<p>文字</p><p><img src=\"https://i/1.jpg\"/></p>"))
    }

    @Test
    fun `stripHtml 还原实体`() {
        // 实体字面量拆开拼接，避免与工具转义冲突
        val amp = "&"
        val input = "<p>a" + amp + "lt;b" + amp + "gt;c" + amp + "quot;d" + amp + "amp;e</p>"
        assertEquals("a<b>c\"d" + amp + "e", HupuPostApi.stripHtml(input))
    }

    @Test
    fun `extractImages 抽出全部图片地址`() {
        val html = "<p>t</p><p><img src=\"https://i/1.jpg\"/></p><p><img src='https://i/2.png'></p>"
        assertEquals(listOf("https://i/1.jpg", "https://i/2.png"), HupuPostApi.extractImages(html))
    }

    @Test
    fun `extractImages 无图返回空且过滤 data uri`() {
        assertTrue(HupuPostApi.extractImages("<p>纯文本</p>").isEmpty())
        assertTrue(HupuPostApi.extractImages("<img src=\"data:image/png;base64,AAA\"/>").isEmpty())
    }

    @Test
    fun `编辑视频帖保留视频字段`() {
        val b = HupuPostApi.buildThreadPayload(
            title = "标题四个字",
            contentHtml = "",
            topicId = 1,
            zoneId = 0,
            videoUrl = "https://v/x.mp4",
            videoCover = "https://img/c.jpg",
            contentText = "",
            tid = "642382804",
        )
        assertEquals("642382804", b.getString("tid"))
        assertEquals("https://v/x.mp4", b.getString("videoUrl"))
        assertTrue(b.has("format"))
    }
}
