package com.java.myapplication

import com.java.myapplication.data.HupuAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 登录会话纯函数测试（1.36：cookie 形态判定 + 评论 HTML 组装） */
class HupuAccountTest {

    @Test
    fun loginCookieShape() {
        assertTrue(HupuAccount.looksLikeLoginCookie("a=1; u=38357905|xx|yy; us=abc123"))
        assertTrue(HupuAccount.looksLikeLoginCookie("u=1; us=2"))
        assertFalse(HupuAccount.looksLikeLoginCookie("ua=999; us=abc")) // u 必须是独立键
        assertFalse(HupuAccount.looksLikeLoginCookie("a=1; b=2"))
        assertFalse(HupuAccount.looksLikeLoginCookie(""))
        assertFalse(HupuAccount.looksLikeLoginCookie("   "))
    }

    @Test
    fun replyContentHtml() {
        assertEquals("<p>hi</p>", HupuAccount.buildReplyContent("hi"))
        assertEquals("<p>a</p><p>b</p>", HupuAccount.buildReplyContent("a\nb"))
        assertEquals("", HupuAccount.buildReplyContent("   \n  "))
        // 多余空行被过滤，行内容 trim
        assertEquals("<p>x</p>", HupuAccount.buildReplyContent("\n  x  \n\n"))
    }
}