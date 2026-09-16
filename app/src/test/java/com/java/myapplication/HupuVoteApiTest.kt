package com.java.myapplication

import com.java.myapplication.data.HupuVoteApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import com.java.myapplication.data.HupuReply
import com.java.myapplication.data.HupuRepository
import com.java.myapplication.data.HupuThread
import com.java.myapplication.data.HupuThreadDetail
import org.junit.Test

/**
 * 1.121 投票接口响应解析单测。
 *
 * fixture 取自 2026-09 实测响应：
 *  - 读取：GET bbs.mobileapi.hupu.com/3/8.0.80/bbsintapi/vote/v1/getVoteInfo?voteId=11333655
 *  - 提交：POST bbs.hupu.com/pcmapi/pc/bbs/v1/vote {voteId, sortList:[1,4,7]} → code:1
 *  - 重复：code:0, internalCode:PC090004, msg:"暂无剩余票数"
 */
class HupuVoteApiTest {

    /** 读取响应（多选帖，未投票：canVote=true、userVoteRecordList=null） */
    private val readJson = """
        {"code":200,"data":{
          "voteId":11333655,"title":"《三体》中最喜欢的三个人物",
          "userOptionLimit":3,"userCount":1,"voteCount":3,"puid":0,"canVote":true,
          "voteDetailList":[
            {"sort":1,"content":"汪淼","optionVoteCount":1,"attachment":"null"},
            {"sort":2,"content":"史强","optionVoteCount":1,"attachment":"null"},
            {"sort":3,"content":"常伟思","optionVoteCount":1,"attachment":"null"},
            {"sort":4,"content":"叶文洁","optionVoteCount":0,"attachment":"null"}
          ],
          "userVoteRecordList":null,"userVoteRecordMap":[],"end":false}}
    """.trimIndent()

    /** 提交成功响应（投票后状态：canVote=false、userVoteRecordList=[1,4,7]） */
    private val submitJson = """
        {"code":1,"internalCode":"PC000000","msg":"success","data":{
          "voteId":11333655,"title":"《三体》中最喜欢的三个人物",
          "userOptionLimit":3,"userCount":2,"voteCount":6,"canVote":false,
          "voteDetailList":[
            {"sort":1,"content":"汪淼","optionVoteCount":2},
            {"sort":4,"content":"叶文洁","optionVoteCount":1},
            {"sort":7,"content":"丁仪","optionVoteCount":1}
          ],
          "userVoteRecordList":[1,4,7],"end":false}}
    """.trimIndent()

    @Test
    fun `读取响应解析（未投票 多选）`() {
        val r = HupuVoteApi.parseVoteResult(readJson)
        assertNotNull(r)
        assertEquals(11333655, r!!.voteId)
        assertEquals("《三体》中最喜欢的三个人物", r.title)
        assertEquals(3, r.limit)
        assertTrue(r.canVote)
        assertFalse(r.ended)
        assertEquals(1, r.userCount)
        assertEquals(3, r.totalVotes)
        assertEquals(4, r.options.size)
        assertEquals("汪淼", r.options.first().content)
        assertEquals(1, r.options.first().voteCount)
        // userVoteRecordList 为 JSON null → 空列表（Android org.json 坑）
        assertTrue(r.myChoices.isEmpty())
    }

    @Test
    fun `提交响应解析（已投票 code=1）`() {
        val r = HupuVoteApi.parseVoteResult(submitJson)
        assertNotNull(r)
        assertFalse(r!!.canVote)
        assertEquals(listOf(1, 4, 7), r.myChoices)
        assertEquals(6, r.totalVotes)
        assertEquals(2, r.userCount)
        assertEquals(3, r.options.size)
    }

    @Test
    fun `重复投票错误码不产生结果`() {
        val err = """
            {"code":0,"internalCode":"PC090004","msg":"暂无剩余票数","data":null}
        """.trimIndent()
        assertNull(HupuVoteApi.parseVoteResult(err))
    }

    @Test
    fun `缺 data 的成功码也返回 null`() {
        assertNull(HupuVoteApi.parseVoteResult("""{"code":200,"data":null}"""))
        assertNull(HupuVoteApi.parseVoteResult("not json"))
        assertNull(HupuVoteApi.parseVoteResult("""{"code":1}"""))
    }

    // ---------- 1.123: 详情换新合并（mergeThreadDetail） ----------

    private fun detail(page: Int, pids: List<String>, read: Int = 100): HupuThreadDetail =
        HupuThreadDetail(
            thread = HupuThread(tid = "77", title = "t", read = read),
            contentHtml = "<p>x</p>",
            replies = pids.map { HupuReply(pid = it) },
            replyCount = pids.size,
            replyPage = page,
            replyTotalPages = 5,
        )

    @Test
    fun `merge 未翻页时新数据直接替换`() {
        val repo = HupuRepository()
        val old = detail(1, listOf("a", "b"), read = 10)
        val fresh = detail(1, listOf("a", "b2", "c"), read = 99)
        val merged = repo.mergeThreadDetail(old, fresh)
        // 第 1 页：fresh 全量替换（计数、正文、回复都取新）
        assertEquals(99, merged.thread.read)
        assertEquals(listOf("a", "b2", "c"), merged.replies.map { it.pid })
    }

    @Test
    fun `merge 已翻页时保留深层回复并去重`() {
        val repo = HupuRepository()
        val old = detail(3, listOf("a", "b", "c", "d", "e"))   // 已翻 3 页
        val fresh = detail(1, listOf("a", "b", "new"), read = 200)
        val merged = repo.mergeThreadDetail(old, fresh)
        // 已加载页不砍短：deep replies 保留，新回复在最前，重复 pid 去重
        assertEquals(listOf("a", "b", "new", "c", "d", "e"), merged.replies.map { it.pid })
        assertEquals(200, merged.thread.read)
        assertEquals(1, merged.replyPage) // 翻页游标取新（第 1 页）
    }

    @Test
    fun `merge old 为 null 直接返回新`() {
        val repo = HupuRepository()
        val fresh = detail(1, listOf("a"))
        assertEquals(fresh, repo.mergeThreadDetail(null, fresh))
    }

    // ---------- 1.124: 投票结果会话缓存 ----------

    @Test
    fun `voteCache 写入后可读取`() {
        val r = HupuVoteApi.parseVoteResult(
            """{"code":200,"data":{"voteId":998,"title":"t","voteCount":3,"userCount":2,
            "userOptionLimit":1,"canVote":false,"end":true,
            "voteDetailList":[{"sort":1,"content":"A","optionVoteCount":2},{"sort":2,"content":"B","optionVoteCount":1}],
            "userVoteRecordList":[1]}}"""
        )
        assertNotNull(r)
        HupuVoteApi.cacheVoteResult(r!!)
        // 同 voteId 读回；未缓存的 id 返回 null
        assertEquals(r, HupuVoteApi.cachedVoteInfo(998))
        assertNull(HupuVoteApi.cachedVoteInfo(-1))
    }
}