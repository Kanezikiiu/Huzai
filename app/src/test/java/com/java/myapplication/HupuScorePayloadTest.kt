package com.java.myapplication

import com.java.myapplication.data.HupuAccount
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 1.147 评分写端点 payload 单测（纯函数，不发网络）。
 *
 * 覆盖两处实测修正：
 *  - `images` 必须是 URL 字符串数组。传对象元素（commentContentId/commentContent/commentContentType）
 *    服务端直接返回 `JSON parse error: Cannot deserialize value of type java.lang.String from Object value`。
 *    匿名实测：
 *      images=[{"commentContentId":"0","commentContent":"https://x/a.jpg","commentContentType":"IMAGE"}]
 *        → {"code":-1,"type":"COMMON","msg":"JSON parse error: ..."}
 *      images=["https://x/a.jpg"] → {"code":401,"type":"LOGIN"}（通过反序列化，进入登录校验）
 *  - score/save 的 source 为空串（官方 live 调用 `{outBizKey, score, source:""}`）。
 */
class HupuScorePayloadTest {

    @Test
    fun `图片以 URL 字符串数组提交`() {
        val urls = listOf("https://i1.hoopchina.com.cn/a.jpg", "https://i1.hoopchina.com.cn/b.png")
        val body = HupuAccount.buildScorePublishBody(
            bizType = "common_second",
            bizNo = "176621",
            content = "带图回复",
            parentCommentId = "123456",
            subjectId = "sub-1",
            images = urls,
        )
        val arr: JSONArray = body.getJSONArray("images")
        assertEquals(2, arr.length())
        assertEquals(urls[0], arr.getString(0))
        assertEquals(urls[1], arr.getString(1))
        // 元素必须是字符串，不能是对象
        assertFalse(body.toString().contains("commentContentType"))
        assertFalse(body.toString().contains("commentContentId"))
    }

    @Test
    fun `无图片时不带 images 字段`() {
        val body = HupuAccount.buildScorePublishBody(
            bizType = "common_second", bizNo = "176621", content = "纯文字",
            parentCommentId = "123456", subjectId = "sub-1",
        )
        assertFalse(body.has("images"))
    }

    @Test
    fun `回复形态带 parentCommentId 与 subjectId`() {
        val body = HupuAccount.buildScorePublishBody(
            bizType = "lol_item", bizNo = "3711", content = "回复",
            parentCommentId = "999", subjectId = "self-sub",
        )
        assertEquals("999", body.getString("parentCommentId"))
        assertEquals("self-sub", body.getString("subjectId"))
        assertEquals("m", body.getString("source"))
        assertEquals("lol_item", body.getJSONObject("outBizKey").getString("outBizType"))
        assertEquals("3711", body.getJSONObject("outBizKey").getString("outBizNo"))
    }

    @Test
    fun `无 parentCommentId 时不提交该字段`() {
        val body = HupuAccount.buildScorePublishBody(
            bizType = "common_second", bizNo = "176621", content = "顶层",
        )
        assertFalse(body.has("parentCommentId"))
    }

    @Test
    fun `打分体 source 为空串且版本段为 8_2_99`() {
        val body = HupuAccount.buildScoreSaveBody("lol_match", "3711", 8)
        assertEquals("", body.getString("source"))
        assertEquals(8, body.getInt("score"))
        assertEquals("lol_match", body.getJSONObject("outBizKey").getString("outBizType"))
    }

    @Test
    fun `score_save 版本段为 8_2_99`() {
        assertEquals("8.2.99", HupuAccount.GAMES_SCORE_VERSION)
    }
    @Test
    fun `打分随带评论走一级评论体_不带 parentCommentId 且 subjectId 为空`() {
        val body = HupuAccount.buildScorePublishBody(
            bizType = "lol_match", bizNo = "3711", content = "打得不错",
            parentCommentId = "", subjectId = "",
        )
        assertFalse(body.has("parentCommentId"))
        assertEquals("", body.getString("subjectId"))
        assertEquals("打得不错", body.getString("content"))
        assertEquals("m", body.getString("source"))
        assertEquals("lol_match", body.getJSONObject("outBizKey").getString("outBizType"))
    }

    @Test
    fun `发布结果默认带空标识且 error 可为 null`() {
        val ok = HupuAccount.ScorePublishResult(null)
        assertNull(ok.error)
        assertEquals("", ok.commentId)
        assertEquals("", ok.subjectId)
        val bad = HupuAccount.ScorePublishResult("请先在「我的」页登录")
        assertEquals("请先在「我的」页登录", bad.error)
    }
}
