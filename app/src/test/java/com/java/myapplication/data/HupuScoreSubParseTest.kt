package com.java.myapplication.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.150：楼中楼子评论解析的「成功但空」语义单测。
 *
 * 实测（2026-09，lol_match/3711、common_second/176621 上任意 0 子回复的母评论，
 * queryType=latest/earliest × publishTime=0/now 四种组合）：
 *   GET .../primarySingleRow/subCommentList?...&parentCommentId=2949753302
 *   → {"code":1,"type":"COMMON","msg":"成功","data":null,"success":true}
 *
 * 旧实现 data==null 直接 return null，上层把 null 当网络失败 → 楼中楼显示「加载失败」，
 * 表现就是「刚发完回复 / 切到没有数据的排序时加载失败」。现在只有非成功码/解析异常才算失败。
 */
class HupuScoreSubParseTest {

    /** 真实响应的单条子回复 fixture（common_second/176621 抓取，字段已裁剪） */
    private val oneSub = """
        {"commentKey":{"subjectId":"133431214","commentId":"2949753302","key":"133431214:2949753302"},
         "subjectId":"133431214","commentId":"2949753302","commentUserName":"虎扑JR0892996672",
         "commentUserHeadImg":"https://i2.hoopchina.com.cn/user/default/light1.png",
         "commentContent":"真神","commentContentImages":null,"parentCommentId":"0",
         "commentDate":"08-26","lightCount":0,"score":10,"hasLight":false,
         "ipLocation":"福建","subCommentCount":0,"descendantCount":0}
    """.trimIndent()

    @Test
    fun `data 为 null 视为空状态而不是失败`() {
        val r = HupuMatchParser.parseSubComments(
            """{"code":1,"type":"COMMON","msg":"成功","data":null,"success":true}"""
        )
        assertNotNull(r)
        assertTrue(r!!.comments.isEmpty())
        assertEquals(0L, r.commentCount)
        assertFalse(r.hasMore)
    }

    @Test
    fun `comments 为空数组也是空状态`() {
        val r = HupuMatchParser.parseSubComments(
            """{"code":1,"msg":"成功","data":{"comments":[],"commentCount":0,"cursor":{"publishTime":0},"hasMore":false}}"""
        )
        assertNotNull(r)
        assertTrue(r!!.comments.isEmpty())
    }

    @Test
    fun `非成功码或坏 JSON 才算失败`() {
        assertNull(
            HupuMatchParser.parseSubComments(
                """{"code":-1,"type":"COMMON","msg":"Required request parameter 'order' is not present","data":null}"""
            )
        )
        assertNull(HupuMatchParser.parseSubComments("not-a-json"))
    }

    @Test
    fun `有子回复时正常解析并保留标识`() {
        val r = HupuMatchParser.parseSubComments(
            """{"code":1,"msg":"成功","data":{"comments":[$oneSub],"commentCount":1,"cursor":{"publishTime":1787711586600},"hasMore":true}}"""
        )
        assertNotNull(r)
        assertEquals(1, r!!.comments.size)
        assertEquals("2949753302", r.comments[0].commentId)
        assertEquals("真神", r.comments[0].content)
        assertEquals("福建", r.comments[0].ipLocation)
        assertEquals(1787711586600L, r.cursor)
        assertTrue(r.hasMore)
    }
}