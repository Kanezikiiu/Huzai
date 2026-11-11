package com.java.myapplication

import com.java.myapplication.data.HupuParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.32x：首页热帖新数据源（步行街推荐流）解析测试。
 *
 * 覆盖三件容易踩的事：
 * 1. 封面来自 `picList[0].url`，视频帖回退到 `video.img`
 * 2. Android `org.json` 会把 JSON null 强转成字符串 "null" → 必须收敛成空串
 * 3. `nextPage` 缺省时用「列表非空」兜底，避免无限加载直接失效
 */
class WalkingStreetParserTest {

    private fun wrap(threadListJson: String, nextPage: String = "true") = """
        {
          "code": 200,
          "msg": "success",
          "data": {
            "nextPage": $nextPage,
            "cursor": "1793858375",
            "threadList": [ $threadListJson ]
          }
        }
    """.trimIndent()

    @Test
    fun `普通图文帖字段映射正确`() {
        val json = wrap(
            """
            {
              "fid": 34,
              "tid": 643600466,
              "title": "房子车子票子都有了",
              "userName": "kululu",
              "puid": 15461211,
              "recommendCount": 1052,
              "replies": 457,
              "time": "昨天 18:00",
              "topicName": "步行街主干道",
              "topicId": 1,
              "videoThread": false,
              "video": null,
              "picList": [
                {"url": "https://i5.hoopchina.com.cn/a.png"},
                {"url": "https://i5.hoopchina.com.cn/b.png"}
              ]
            }
            """.trimIndent()
        )
        val page = HupuParser.parseWalkingStreet(json)!!
        assertEquals(1, page.threads.size)
        assertEquals("1793858375", page.cursor)
        assertTrue(page.hasMore)

        val t = page.threads.first()
        assertEquals("643600466", t.tid)
        assertEquals("房子车子票子都有了", t.title)
        assertEquals("https://i5.hoopchina.com.cn/a.png", t.cover)
        assertEquals(1052, t.lights)
        assertEquals(457, t.replies)
        assertEquals("昨天 18:00", t.createdAtText)
        assertEquals("步行街主干道", t.topic?.name)
        assertEquals("kululu", t.author?.name)
        assertEquals("15461211", t.author?.puid)
        assertEquals(false, t.hasVideo)
        assertEquals("/643600466.html", t.url)
    }

    @Test
    fun `视频帖用视频封面兜底并带出直链`() {
        val json = wrap(
            """
            {
              "tid": 643662016,
              "title": "水果的正确切法",
              "videoThread": true,
              "picList": [],
              "video": {
                "url": "https://v.hoopchina.com.cn/x.mp4?auth_key=1",
                "img": "https://i1.hoopchina.com.cn/x.jpg"
              }
            }
            """.trimIndent()
        )
        val t = HupuParser.parseWalkingStreet(json)!!.threads.first()
        assertEquals("https://i1.hoopchina.com.cn/x.jpg", t.cover)
        assertEquals("https://i1.hoopchina.com.cn/x.jpg", t.videoCover)
        assertEquals("https://v.hoopchina.com.cn/x.mp4?auth_key=1", t.video)
        assertTrue(t.hasVideo)
    }

    @Test
    fun `JSON null 不会变成字符串 null`() {
        val json = wrap(
            """
            {
              "tid": 1,
              "title": "t",
              "userName": null,
              "puid": null,
              "time": null,
              "topicName": null,
              "video": null,
              "picList": null
            }
            """.trimIndent()
        )
        val t = HupuParser.parseWalkingStreet(json)!!.threads.first()
        assertEquals("", t.author?.name)
        assertEquals("", t.author?.puid)
        assertEquals("", t.createdAtText)
        assertEquals("", t.topic?.name)
        assertNull(t.cover)
        assertEquals(false, t.hasVideo)
    }

    @Test
    fun `没有 tid 的条目被丢弃`() {
        val json = wrap(
            """
            {"title": "无 id"},
            {"tid": 2, "title": "有 id"}
            """.trimIndent()
        )
        val page = HupuParser.parseWalkingStreet(json)!!
        assertEquals(1, page.threads.size)
        assertEquals("2", page.threads.first().tid)
    }

    @Test
    fun `空列表与 code 异常`() {
        val empty = HupuParser.parseWalkingStreet(wrap("", "false"))!!
        assertTrue(empty.threads.isEmpty())
        assertEquals(false, empty.hasMore)

        assertNull(HupuParser.parseWalkingStreet("""{"code":0,"msg":"x"}"""))
        assertNull(HupuParser.parseWalkingStreet("not json"))
    }
}