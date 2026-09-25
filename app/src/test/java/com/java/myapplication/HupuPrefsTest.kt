package com.java.myapplication

import com.java.myapplication.data.decodeHomeTopicsJson
import com.java.myapplication.data.encodeHomeTopicsJson
import com.java.myapplication.data.decodeHistoryJson
import com.java.myapplication.data.encodeHistoryJson
import com.java.myapplication.data.HupuHistoryEntry
import com.java.myapplication.data.HupuTopicInfo
import com.java.myapplication.data.decodeFavoriteTopicsJson
import com.java.myapplication.data.encodeFavoriteTopicsJson
import com.java.myapplication.data.toggleFavoriteList
import com.java.myapplication.data.HupuSticker
import com.java.myapplication.data.encodeStickersJson
import com.java.myapplication.data.decodeStickersJson
import com.java.myapplication.data.encodeKeywordsJson
import com.java.myapplication.data.decodeKeywordsJson
import com.java.myapplication.data.isLocalStickerUrl
import com.java.myapplication.data.localStickerFile
import com.java.myapplication.data.stickerContentKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 主页频道自定义的 JSON 编解码测试（纯 JVM，无 Android 依赖） */
class HupuPrefsTest {

    private fun t(id: String, name: String, url: String, logo: String? = null) =
        HupuTopicInfo(topicId = id, name = name, url = url, logo = logo)

    @Test
    fun encodeDecode_roundTrip() {
        val list = listOf(
            t("1", "步行街主干道", "/topic-daily", "//i1.hupu.com/a.png"),
            t("2", "英雄联盟", "/lol"),
        )
        val json = encodeHomeTopicsJson(list)
        val out = decodeHomeTopicsJson(json)
        assertEquals(list, out)
    }

    @Test
    fun decode_badJson_returnsEmpty() {
        assertTrue(decodeHomeTopicsJson("not json").isEmpty())
        assertTrue(decodeHomeTopicsJson("").isEmpty())
        assertTrue(decodeHomeTopicsJson("[{bad}").isEmpty())
    }

    @Test
    fun decode_emptyArray_returnsEmpty() {
        val out = decodeHomeTopicsJson("[]")
        assertTrue(out.isEmpty())
    }

    @Test
    fun decode_skipsNonObjects() {
        val out = decodeHomeTopicsJson("""["str",{"topicId":"1","name":"a","url":"/a"}]""")
        assertEquals(1, out.size)
        assertEquals("/a", out[0].url)
    }

    /** encode 忠实保存任何长度（上限拦截在 UI 层做） */
    @Test
    fun encode_moreThanLimit_preserved() {
        val list = (1..25).map { t("$it", "版块$it", "/t$it") }
        val json = encodeHomeTopicsJson(list)
        assertEquals(25, decodeHomeTopicsJson(json).size)
    }

    // ---------- 浏览记录编解码 ----------

    private fun h(tid: String, title: String = "标题$tid", topic: String = "步行街", at: Long = 1700000000000L) =
        HupuHistoryEntry(tid = tid, title = title, topicName = topic, lights = 5, replies = 3, read = 100, visitedAt = at)

    @Test
    fun history_roundTrip() {
        val list = listOf(h("100"), h("101", topic = "英雄联盟"), h("102", at = 0L))
        val json = encodeHistoryJson(list)
        val out = decodeHistoryJson(json)
        assertEquals(list, out)
    }

    @Test
    fun history_badJson_returnsEmpty() {
        assertTrue(decodeHistoryJson("not json").isEmpty())
        assertTrue(decodeHistoryJson("").isEmpty())
        assertTrue(decodeHistoryJson("[{bad").isEmpty())
    }

    @Test
    fun history_decode_preservesOrderAndFields() {
        val json = """[
            {"tid":"7","title":"七","topicName":"排球","lights":9,"replies":8,"read":7,"visitedAt":6},
            {"tid":"1","title":"一","topicName":"","lights":2,"replies":1,"read":0,"visitedAt":3}
        ]"""
        val out = decodeHistoryJson(json)
        assertEquals(2, out.size)
        assertEquals("7", out[0].tid)
        assertEquals("排球", out[0].topicName)
        assertEquals(9, out[0].lights)
        assertEquals(8, out[0].replies)
        assertEquals(7, out[0].read)
        assertEquals(6L, out[0].visitedAt)
        assertEquals("", out[1].topicName)
        assertEquals(3L, out[1].visitedAt)
    }

    @Test
    fun stickers_encodeDecode_roundTrip() {
        val list = listOf(
            HupuSticker("https://i1.hupu.com/custom.gif"),
            HupuSticker("https://i1.hupu.com/a.png"),
        )
        assertEquals(list, decodeStickersJson(encodeStickersJson(list)))
    }

    @Test
    fun stickers_decode_legacyObjectForm_and_badJson() {
        // 1.94 存的 {"url":..,"token":..} 对象形式仍可读
        val legacy = "[{\"url\":\"https://i1.hupu.com/x.png\",\"token\":\"[tui]\"}]"
        assertEquals(1, decodeStickersJson(legacy).size)
        assertEquals("https://i1.hupu.com/x.png", decodeStickersJson(legacy)[0].url)
        assertTrue(decodeStickersJson("not-json").isEmpty())
        assertTrue(decodeStickersJson("[]").isEmpty())
    }

    @Test
    fun stickerKeywords_encodeDecode_roundTrip_keepsOrder() {
        // 1.191: 「最近在搜」按最近优先排序，编解码必须保序（首项 = 最新）
        val list = listOf("猫", "熊猫头", "你好")
        assertEquals(list, decodeKeywordsJson(encodeKeywordsJson(list)))
    }

    @Test
    fun stickerKeywords_decode_badJson_and_emptyEntries() {
        assertTrue(decodeKeywordsJson("not-json").isEmpty())
        assertTrue(decodeKeywordsJson("[]").isEmpty())
        // 空串条目会被剔除（避免历史里出现「点了没反应」的空 chip）
        assertEquals(listOf("a"), decodeKeywordsJson("[\"a\",\"\"]"))
    }

    @Test
    fun stickers_localFileUrl_roundTrip_andDetection() {
        // 1.165: 本地导入的表情用 file:// 标识，须与远程 URL 一同持久化往返
        val local = "file:///data/user/0/com.huzai.app/files/stickers_local/sticker_1_2.png"
        val list = listOf(HupuSticker("https://i1.hupu.com/a.png"), HupuSticker(local))
        assertEquals(list, decodeStickersJson(encodeStickersJson(list)))
        assertEquals(local, decodeStickersJson(encodeStickersJson(list))[1].url)
        assertTrue(isLocalStickerUrl(local))
        assertTrue(!isLocalStickerUrl("https://i1.hupu.com/a.png"))
        assertEquals("/data/user/0/com.huzai.app/files/stickers_local/sticker_1_2.png",
            localStickerFile(local)?.path)
        assertEquals(null, localStickerFile("https://i1.hupu.com/a.png"))
    }

    @Test
    fun stickerContentKey_dedup_basis() {
        // 1.168: 本地表情以「内容指纹」作为文件名 → 同一张图必落到同一 URL，
        //        从而复用 addSticker 的「URL 已存在则拒绝」检测（不重复添加）
        val a = byteArrayOf(10, 20, 30, 40, -1, 0)
        val same = byteArrayOf(10, 20, 30, 40, -1, 0)
        val diff = byteArrayOf(10, 20, 30, 40, -2, 0)
        assertEquals(stickerContentKey(a), stickerContentKey(same))
        assertTrue(stickerContentKey(a) != stickerContentKey(diff))
        assertEquals(32, stickerContentKey(a).length)
        // 指纹入文件名后，相同内容 → 相同 URL → 被 addSticker 判重
        val url1 = "file:///x/stickers_local/sticker_" + stickerContentKey(a) + ".png"
        val url2 = "file:///x/stickers_local/sticker_" + stickerContentKey(same) + ".png"
        assertEquals(url1, url2)
    }

    @Test
    fun favoriteTopicsJson_roundTrip_keepsHotTextAndLogo() {
        val list = listOf(
            HupuTopicInfo(
                topicId = "1",
                name = "步行街",
                url = "https://bbs.hupu.com/bxj",
                hotText = "1.2万",
                logo = "https://i1.hupu.com/a.png",
            ),
            HupuTopicInfo(topicId = "2", name = "CBA", url = "https://bbs.hupu.com/cba"),
        )
        val back = decodeFavoriteTopicsJson(encodeFavoriteTopicsJson(list))
        assertEquals(2, back.size)
        assertEquals("步行街", back[0].name)
        assertEquals("1.2万", back[0].hotText)
        assertEquals("https://i1.hupu.com/a.png", back[0].logo)
        // 没有热度 / logo 的条目解码后为空（不是字符串 "null"）
        assertEquals("", back[1].hotText)
        assertEquals(null, back[1].logo)
    }

    @Test
    fun favoriteTopicsJson_badInput_returnsEmpty() {
        assertEquals(0, decodeFavoriteTopicsJson("").size)
        assertEquals(0, decodeFavoriteTopicsJson("not json").size)
        assertEquals(0, decodeFavoriteTopicsJson("{\"a\":1}").size)
        // url 为空的条目丢弃（没有 url 无法再次打开该专区）
        assertEquals(0, decodeFavoriteTopicsJson("[{\"name\":\"x\"}]").size)
    }

    @Test
    fun toggleFavoriteList_addTop_remove_capAtMax() {
        val a = HupuTopicInfo(topicId = "1", name = "A", url = "u1")
        val b = HupuTopicInfo(topicId = "2", name = "B", url = "u2")
        val c = HupuTopicInfo(topicId = "3", name = "C", url = "u3")
        // 新收藏置顶
        assertEquals(listOf("u2", "u1"), toggleFavoriteList(listOf(a), b, 50).map { it.url })
        // 再点一次 = 取消
        assertEquals(listOf("u1"), toggleFavoriteList(listOf(b, a), b, 50).map { it.url })
        // 上限：新收藏置顶，尾部被挤出
        assertEquals(listOf("u3", "u1"), toggleFavoriteList(listOf(a, b), c, 2).map { it.url })
    }
}