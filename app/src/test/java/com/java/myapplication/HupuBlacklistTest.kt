package com.java.myapplication

import com.java.myapplication.data.HupuAuthor
import com.java.myapplication.data.HupuBlacklist
import com.java.myapplication.data.HupuBlacklistEntry
import com.java.myapplication.data.HupuReply
import com.java.myapplication.data.HupuScoreComment
import com.java.myapplication.data.HupuSubReply
import com.java.myapplication.data.HupuThread
import com.java.myapplication.data.blacklistProfileId
import com.java.myapplication.data.decodeBlacklistEntriesJson
import com.java.myapplication.data.decodeBlacklistJson
import com.java.myapplication.data.decodeThreadAuthorsJson
import com.java.myapplication.data.encodeBlacklistEntriesJson
import com.java.myapplication.data.encodeBlacklistJson
import com.java.myapplication.data.encodeThreadAuthorsJson
import com.java.myapplication.data.formatBlacklistTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 本地黑名单（1.221）单测：
 *  - 判定只认 id（puid / euid / commentUserId），空 id 恒不命中；
 *  - 帖子/回复/楼中楼：按人删节点；
 *  - 评分评论：命中者「整棵」删除（含全部后代），未命中者的后代命中则只剪后代；
 *  - 拉平的孙评论：祖先链命中也要删（避免孤儿回复）。
 */
class HupuBlacklistTest {

    private fun author(puid: String, euid: String = "", name: String = "u$puid") =
        HupuAuthor(puid = puid, name = name, euid = euid)

    private fun thread(tid: String, a: HupuAuthor?) = HupuThread(tid = tid, title = "t$tid", author = a)

    private fun reply(pid: String, a: HupuAuthor?) = HupuReply(pid = pid, author = a)

    private fun subReply(pid: String, a: HupuAuthor?) = HupuSubReply(pid = pid, author = a)

    private fun scoreComment(
        id: String,
        userId: String,
        parentId: String = "",
        subs: List<HupuScoreComment> = emptyList(),
    ) = HupuScoreComment(
        commentId = id,
        userName = "n$id",
        userHead = null,
        userId = userId,
        content = "c$id",
        score = 0,
        lightCount = 0L,
        date = "",
        ipLocation = "",
        subComments = subs,
        parentCommentId = parentId,
    )

    // ---------- 判定 ----------

    @Test
    fun blockedId_空表恒不命中() {
        assertFalse(HupuBlacklist.blockedId("100", emptyMap()))
    }

    @Test
    fun blockedId_空id不命中() {
        val bl = mapOf("100" to "甲")
        assertFalse(HupuBlacklist.blockedId("", bl))
        assertFalse(HupuBlacklist.blockedId("   ", bl))
    }

    @Test
    fun blockedAuthor_命中puid或euid都算() {
        val byPuid = mapOf("100" to "甲")
        assertTrue(HupuBlacklist.blockedAuthor(author("100", euid = "900"), byPuid))
        val byEuid = mapOf("900" to "甲")
        assertTrue(HupuBlacklist.blockedAuthor(author("100", euid = "900"), byEuid))
        assertFalse(HupuBlacklist.blockedAuthor(null, byPuid))
        assertFalse(HupuBlacklist.blockedAuthor(author("200"), byPuid))
    }

    // ---------- 帖子 / 回复 ----------

    @Test
    fun pruneThreads_只删被拉黑者的帖子() {
        val bl = mapOf("100" to "甲")
        val list = listOf(thread("1", author("100")), thread("2", author("200")), thread("3", null))
        val kept = HupuBlacklist.pruneThreads(list, bl)
        assertEquals(listOf("2", "3"), kept.map { it.tid })
    }

    @Test
    fun pruneReplies_与SubReplies同理() {
        val bl = mapOf("100" to "甲")
        val replies = listOf(reply("p1", author("100")), reply("p2", author("200")))
        assertEquals(listOf("p2"), HupuBlacklist.pruneReplies(replies, bl).map { it.pid })
        val subs = listOf(subReply("s1", author("100")), subReply("s2", null))
        assertEquals(listOf("s2"), HupuBlacklist.pruneSubReplies(subs, bl).map { it.pid })
    }

    @Test
    fun pruneThreads_空表原样返回() {
        val list = listOf(thread("1", null))
        assertEquals(list, HupuBlacklist.pruneThreads(list, emptyMap()))
    }

    // ---------- 评分评论：整棵剪枝 ----------

    @Test
    fun pruneScoreNode_本人命中则整棵删除() {
        val bl = mapOf("100" to "甲")
        val node = scoreComment("a", "100", subs = listOf(scoreComment("a1", "200")))
        assertNull(HupuBlacklist.pruneScoreNode(node, bl))
    }

    @Test
    fun pruneScoreNode_后代命中只剪后代() {
        val bl = mapOf("200" to "乙")
        val node = scoreComment(
            "a", "100",
            subs = listOf(scoreComment("a1", "200"), scoreComment("a2", "300")),
        )
        val kept = HupuBlacklist.pruneScoreNode(node, bl)
        assertNotNull(kept)
        assertEquals(listOf("a2"), kept!!.subComments.map { it.commentId })
    }

    @Test
    fun pruneScoreComments_顶层命中者连同其子树整体消失() {
        val bl = mapOf("100" to "甲")
        val list = listOf(
            scoreComment("a", "100", subs = listOf(scoreComment("a1", "300"))),
            scoreComment("b", "200"),
        )
        assertEquals(listOf("b"), HupuBlacklist.pruneScoreComments(list, bl).map { it.commentId })
    }

    @Test
    fun pruneScoreFlat_本人命中删_祖先链命中也要删() {
        val bl = mapOf("100" to "甲")
        // g2 的祖先是 g1（同样在列表里），g1 命中 → g2 一起删（整棵隐藏，不留孤儿）
        val flat = listOf(
            scoreComment("g1", "100"),
            scoreComment("g2", "300", parentId = "g1"),
            scoreComment("g3", "400"),
        )
        assertEquals(listOf("g3"), HupuBlacklist.pruneScoreFlat(flat, null, bl).map { it.commentId })
    }

    @Test
    fun pruneScoreFlat_祖先在传入的parent上也要识别() {
        val bl = mapOf("100" to "甲")
        val parent = scoreComment("p", "100")
        val flat = listOf(scoreComment("g1", "300", parentId = "p"))
        assertTrue(HupuBlacklist.pruneScoreFlat(flat, parent, bl).isEmpty())
    }

    @Test
    fun pruneScoreFlat_空黑名单原样返回() {
        val flat = listOf(scoreComment("g1", "100"))
        assertEquals(flat, HupuBlacklist.pruneScoreFlat(flat, null, emptyMap()))
    }

    // ---------- 持久化编解码 ----------

    @Test
    fun blacklistJson_往返一致() {
        val m = linkedMapOf("100" to "甲", "900" to "甲", "200" to "乙")
        assertEquals(m, decodeBlacklistJson(encodeBlacklistJson(m)))
    }

    @Test
    fun blacklistJson_坏数据返回空表() {
        assertTrue(decodeBlacklistJson("not json").isEmpty())
        assertTrue(decodeBlacklistJson("[]").isEmpty())
    }

    @Test
    fun blacklistJson_无id条目被丢弃() {
        val json = """[{"id":"","name":"甲"},{"id":"100","name":"乙"}]"""
        assertEquals(mapOf("100" to "乙"), decodeBlacklistJson(json))
    }

    // ---------- 1.222：按「人」存条目（多 id + 拉黑时间 + 头像） ----------

    @Test
    fun entriesJson_往返一致_保留多id与时间头像() {
        val list = listOf(
            HupuBlacklistEntry(listOf("100", "900"), "甲", "https://a.png", 1730000000000L),
            HupuBlacklistEntry(listOf("200"), "乙"),
        )
        val back = decodeBlacklistEntriesJson(encodeBlacklistEntriesJson(list))
        assertEquals(list, back)
    }

    @Test
    fun entriesJson_兼容旧格式_无时间头像() {
        val json = """[{"id":"100","name":"甲"}]"""
        val back = decodeBlacklistEntriesJson(json)
        assertEquals(1, back.size)
        assertEquals(listOf("100"), back[0].ids)
        assertEquals("甲", back[0].name)
        assertEquals(0L, back[0].at)
        assertEquals("", back[0].avatar)
    }

    @Test
    fun entriesJson_坏数据返回空表() {
        assertTrue(decodeBlacklistEntriesJson("not json").isEmpty())
        assertTrue(decodeBlacklistEntriesJson("[]").isEmpty())
    }

    @Test
    fun entriesJson_ids为空的人被丢弃() {
        val json = """[{"ids":[],"name":"甲"},{"ids":["","  "],"name":"乙"},{"ids":["300"],"name":"丙"}]"""
        val back = decodeBlacklistEntriesJson(json)
        assertEquals(1, back.size)
        assertEquals("丙", back[0].name)
    }

    @Test
    fun profileId_优先取长数字euid() {
        assertEquals("133888524780491", blacklistProfileId(HupuBlacklistEntry(listOf("4249140", "133888524780491"), "甲")))
        // 只有短 puid 时退回第一把钥匙
        assertEquals("4249140", blacklistProfileId(HupuBlacklistEntry(listOf("4249140"), "甲")))
        assertEquals("", blacklistProfileId(HupuBlacklistEntry(emptyList(), "甲")))
    }

    @Test
    fun formatTime_零值视为未知() {
        assertEquals("拉黑时间未知", formatBlacklistTime(0L))
        assertEquals("拉黑时间未知", formatBlacklistTime(-1L))
        assertTrue(formatBlacklistTime(1730000000000L).startsWith("拉黑于 "))
    }

    // ---------- 1.222：热帖作者缓存编解码 ----------

    @Test
    fun threadAuthors_往返一致() {
        val m = linkedMapOf("642656351" to "27724706|192905810078628", "642663154" to "100|")
        assertEquals(m, decodeThreadAuthorsJson(encodeThreadAuthorsJson(m)))
    }

    @Test
    fun threadAuthors_坏数据返回空表() {
        assertTrue(decodeThreadAuthorsJson("not json").isEmpty())
        assertTrue(decodeThreadAuthorsJson("{}").isEmpty())
    }
}