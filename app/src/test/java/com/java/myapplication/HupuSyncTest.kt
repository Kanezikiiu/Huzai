package com.java.myapplication

import com.java.myapplication.data.HupuBlacklistEntry
import com.java.myapplication.data.DEFAULT_ITEM_GROUP
import com.java.myapplication.data.HupuTopicInfo
import com.java.myapplication.data.SyncBundle
import com.java.myapplication.data.SyncItem
import com.java.myapplication.data.SyncItemResult
import com.java.myapplication.data.decodeStringArray
import com.java.myapplication.data.decodeSyncAck
import com.java.myapplication.data.decodeSyncBundle
import com.java.myapplication.data.encodeSyncAck
import com.java.myapplication.data.encodeSyncBundle
import com.java.myapplication.data.filterKnownScoreGames
import com.java.myapplication.data.mergeBlacklist
import com.java.myapplication.data.mergeStringList
import com.java.myapplication.data.mergeTopics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 数据同步的核心逻辑：合并 / 截断 / 跨版本过滤 / 载荷与回执编解码。
 * 纯函数，不依赖 Android 运行时。
 */
class HupuSyncTest {

    // ---------------- 关键词合并 ----------------

    @Test
    fun `关键词合并本地在前追加去重`() {
        val r = mergeStringList(listOf("a", "b"), listOf("b", "c", "a", "d"), 100)
        assertEquals(listOf("a", "b", "c", "d"), r.list)
        assertEquals(2, r.added)
        assertEquals(0, r.dropped)
    }

    @Test
    fun `关键词超上限按本地优先截断并回报丢弃数`() {
        val r = mergeStringList(listOf("a", "b", "c"), listOf("d", "e", "f"), 3)
        assertEquals(listOf("a", "b", "c"), r.list)
        assertEquals(0, r.added)
        assertEquals(3, r.dropped)
    }

    @Test
    fun `关键词部分可加入时正确切分 added与dropped`() {
        val r = mergeStringList(listOf("a"), listOf("b", "c", "d"), 3)
        assertEquals(listOf("a", "b", "c"), r.list)
        assertEquals(2, r.added)
        assertEquals(1, r.dropped)
    }

    @Test
    fun `关键词合并忽略重复与空串`() {
        val r = mergeStringList(emptyList(), listOf("x", "", "x"), 10)
        assertEquals(listOf("x"), r.list)
        assertEquals(1, r.added)
    }

    // ---------------- 收藏专区合并 ----------------

    @Test
    fun `专区合并按 url去重且保留本地版本`() {
        val a = HupuTopicInfo("1", "甲", "u1")
        val b = HupuTopicInfo("2", "乙", "u2")
        val r = mergeTopics(listOf(a), listOf(a.copy(name = "甲改"), b), 50)
        assertEquals(listOf("u1", "u2"), r.list.map { it.url })
        assertEquals("甲", r.list[0].name)
        assertEquals(1, r.added)
    }

    @Test
    fun `专区 url为空的条目丢弃`() {
        val r = mergeTopics(emptyList(), listOf(HupuTopicInfo("1", "甲", "")), 50)
        assertTrue(r.list.isEmpty())
        assertEquals(0, r.added)
    }

    @Test
    fun `专区超上限回报丢弃`() {
        val local = listOf(HupuTopicInfo("1", "甲", "u1"))
        val inc = listOf(HupuTopicInfo("2", "乙", "u2"))
        val r = mergeTopics(local, inc, 1)
        assertEquals(1, r.list.size)
        assertEquals(0, r.added)
        assertEquals(1, r.dropped)
    }

    // ---------------- 黑名单合并 ----------------

    @Test
    fun `黑名单同一个人合并 id并取更早时间`() {
        val local = listOf(HupuBlacklistEntry(listOf("p1"), "甲", "av1", 2000L))
        val inc = listOf(HupuBlacklistEntry(listOf("p1", "e1"), "甲", "", 1000L))
        val r = mergeBlacklist(local, inc, 500)
        assertEquals(1, r.list.size)
        assertEquals(listOf("p1", "e1"), r.list[0].ids)
        assertEquals(1000L, r.list[0].at)
        assertEquals("av1", r.list[0].avatar)
        assertEquals(0, r.added)
    }

    @Test
    fun `黑名单昵称为空时沿用对方昵称`() {
        val local = listOf(HupuBlacklistEntry(listOf("p1"), "", "", 0L))
        val inc = listOf(HupuBlacklistEntry(listOf("p1"), "甲", "av", 500L))
        val r = mergeBlacklist(local, inc, 500)
        assertEquals("甲", r.list[0].name)
        assertEquals("av", r.list[0].avatar)
        assertEquals(500L, r.list[0].at)
    }

    @Test
    fun `黑名单新人追加且超上限回报`() {
        val local = listOf(
            HupuBlacklistEntry(listOf("p1"), "甲"),
            HupuBlacklistEntry(listOf("p2"), "乙"),
        )
        val inc = listOf(
            HupuBlacklistEntry(listOf("p3"), "丙"),
            HupuBlacklistEntry(listOf("p4"), "丁"),
        )
        val r = mergeBlacklist(local, inc, 3)
        assertEquals(3, r.list.size)
        assertEquals(1, r.added)
        assertEquals(1, r.dropped)
    }

    @Test
    fun `黑名单无 id的条目忽略`() {
        val r = mergeBlacklist(emptyList(), listOf(HupuBlacklistEntry(emptyList(), "甲")), 500)
        assertTrue(r.list.isEmpty())
    }

    // ---------------- 评分频道跨版本过滤 ----------------

    @Test
    fun `评分频道未知 id被丢弃并保序去重`() {
        val (kept, unknown) = filterKnownScoreGames(
            listOf("lol", "newgame", "kog", "lol"),
            listOf("lol", "kog", "val"),
        )
        assertEquals(listOf("lol", "kog"), kept)
        assertEquals(listOf("newgame"), unknown)
    }

    @Test
    fun `评分频道全部未知时保留为空`() {
        val (kept, unknown) = filterKnownScoreGames(listOf("a", "b"), listOf("lol"))
        assertTrue(kept.isEmpty())
        assertEquals(2, unknown.size)
    }

    // ---------------- 载荷编解码 ----------------

    @Test
    fun `载荷编解码往返一致`() {
        val b = SyncBundle(
            206,
            "1.196",
            mapOf(
                SyncItem.TITLE_KEYWORDS to "[\"甲\",\"乙\"]",
                SyncItem.START_TAB to "2",
            ),
        )
        val back = decodeSyncBundle(encodeSyncBundle(b))
        assertNotNull(back)
        assertEquals(206, back!!.versionCode)
        assertEquals("1.196", back.versionName)
        assertEquals(2, back.values.size)
        assertEquals("[\"甲\",\"乙\"]", back.values[SyncItem.TITLE_KEYWORDS])
        assertEquals("2", back.values[SyncItem.START_TAB])
    }

    @Test
    fun `黑名单载荷往返保留多把 id`() {
        val raw = com.java.myapplication.data.encodeBlacklistEntriesJson(
            listOf(HupuBlacklistEntry(listOf("p1", "e1"), "甲", "av", 123L))
        )
        val b = SyncBundle(1, "1.0", mapOf(SyncItem.BLACKLIST to raw))
        val back = decodeSyncBundle(encodeSyncBundle(b))!!
        val entries = com.java.myapplication.data.decodeBlacklistEntriesJson(back.values[SyncItem.BLACKLIST]!!)
        assertEquals(1, entries.size)
        assertEquals(listOf("p1", "e1"), entries[0].ids)
        assertEquals(123L, entries[0].at)
    }

    @Test
    fun `载荷 schema不符拒收`() {
        assertNull(decodeSyncBundle("""{"schema":999,"versionCode":1,"versionName":"x","items":{}}"""))
    }

    @Test
    fun `载荷结构损坏返回 null`() {
        assertNull(decodeSyncBundle("not json"))
        assertNull(decodeSyncBundle("{}"))
        assertNull(decodeSyncBundle("""{"schema":1}"""))
    }

    @Test
    fun `载荷忽略未知项只取已知项`() {
        val json = """{"schema":1,"versionCode":9,"versionName":"9.9","items":{"startTab":3,"whatever":[1]}}"""
        val b = decodeSyncBundle(json)!!
        assertEquals(1, b.values.size)
        assertEquals("3", b.values[SyncItem.START_TAB])
    }

    @Test
    fun `字符串数组解码坏数据为空`() {
        assertEquals(listOf("a", "b"), decodeStringArray("[\"a\",\"b\"]"))
        assertTrue(decodeStringArray("oops").isEmpty())
        assertTrue(decodeStringArray("[]").isEmpty())
    }

    @Test
    fun `默认事项组四项编解码往返`() {
        val b = SyncBundle(
            206,
            "1.196",
            mapOf(
                SyncItem.START_TAB to "2",
                SyncItem.TOPIC_SORT to "[\"最新发布\"]",
                SyncItem.REPLY_SORT to "1",
                SyncItem.SCORE_SORT to "[\"latest\"]",
            ),
        )
        val back = decodeSyncBundle(encodeSyncBundle(b))!!
        assertEquals(4, back.values.size)
        assertEquals("2", back.values[SyncItem.START_TAB])
        assertEquals("1", back.values[SyncItem.REPLY_SORT])
        // 单值项包成单元素数组 → 复用 decodeStringArray 解出原值
        assertEquals(listOf("最新发布"), decodeStringArray(back.values[SyncItem.TOPIC_SORT]!!))
        assertEquals(listOf("latest"), decodeStringArray(back.values[SyncItem.SCORE_SORT]!!))
    }

    @Test
    fun `默认事项组定义完整且都是覆盖语义`() {
        assertEquals(4, DEFAULT_ITEM_GROUP.size)
        assertEquals(DEFAULT_ITEM_GROUP.size, DEFAULT_ITEM_GROUP.toSet().size) // 无重复
        assertTrue(DEFAULT_ITEM_GROUP.all { it.overwrite })
        assertTrue(SyncItem.entries.containsAll(DEFAULT_ITEM_GROUP))
    }

    // ---------------- 回执 ----------------

    @Test
    fun `回执编解码`() {
        val a = decodeSyncAck(
            encodeSyncAck(
                true,
                "已同步 3 项",
                listOf(
                    SyncItemResult("标题关键词", "标题关键词 +2", 0),
                    SyncItemResult("黑名单", "黑名单 +1 人", 0),
                    SyncItemResult("收藏专区", "收藏专区 +0 · 4 条超限未加入", 4),
                ),
            )
        )
        assertNotNull(a)
        assertTrue(a!!.ok)
        assertEquals("已同步 3 项", a.message)
        assertEquals(3, a.results.size)
        assertEquals("收藏专区", a.results[2].label)
        assertEquals(4, a.results[2].dropped)
    }

    @Test
    fun `拒绝回执无逐项结果`() {
        val a = decodeSyncAck(encodeSyncAck(false, "对方取消了本次同步"))!!
        assertTrue(!a.ok)
        assertEquals("对方取消了本次同步", a.message)
        assertTrue(a.results.isEmpty())
    }

    @Test
    fun `回执坏数据返回 null`() {
        assertNull(decodeSyncAck("nope"))
    }
}