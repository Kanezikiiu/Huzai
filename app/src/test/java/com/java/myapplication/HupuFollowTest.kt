package com.java.myapplication

import com.java.myapplication.data.HupuAccount
import com.java.myapplication.data.HupuFollowStore
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.126 关注功能纯函数单测。
 *
 * fixture 取自 2026-09 实测响应：
 *  - 关注成功：POST /1/8.0.0/user/addFollow?puid={自己} body addPuid={目标} → {"status":200,"result":2}
 *  - 取关成功：POST /1/8.0.0/user/delFollow?puid={自己} body delPuid={目标} → {"status":200,"result":-1}
 *  - 已是关注态：{"status":200,"result":null}
 *  - 未登录：{"status":200,"error":"登录异常！"}
 *  - 缺参数：{"status":200,"error":"参数错误！"}
 */
class HupuFollowTest {

    @Test
    fun `cookie 解析登录者短 puid`() {
        assertEquals("38357905", HupuAccount.puidFromCookie("csrfToken=x; u=38357905|5oeS576K|cf8c|xx; us=abc"))
        assertEquals("38357905", HupuAccount.puidFromCookie("u=38357905"))
        assertEquals("", HupuAccount.puidFromCookie(""))
        assertEquals("", HupuAccount.puidFromCookie("a=1; b=2"))
        // u 必须是独立键，ua= 不能被误认
        assertEquals("", HupuAccount.puidFromCookie("ua=999; us=abc"))
        // u 值缺失时返回空
        assertEquals("", HupuAccount.puidFromCookie("u=|xx"))
    }

    @Test
    fun `关注返回判定`() {
        assertNull(HupuAccount.followErrOf(JSONObject("""{"status":200,"result":2}""")))
        assertNull(HupuAccount.followErrOf(JSONObject("""{"status":200,"result":1}""")))
        assertNull(HupuAccount.followErrOf(JSONObject("""{"status":200,"result":-1}""")))
        assertNull(HupuAccount.followErrOf(JSONObject("""{"status":200,"result":null}""")))
        // error 优先于 result：登录失效必须报出来
        assertEquals("登录异常！", HupuAccount.followErrOf(JSONObject("""{"status":200,"error":"登录异常！"}""")))
        assertEquals("参数错误！", HupuAccount.followErrOf(JSONObject("""{"status":200,"error":"参数错误！"}""")))
        // JSON null 的 error 经 cleanStr 视为空 → 成功
        assertNull(HupuAccount.followErrOf(JSONObject("""{"status":200,"error":null}""")))
        assertEquals("网络异常，请稍后再试", HupuAccount.followErrOf(null))
        assertEquals("操作失败，请稍后再试", HupuAccount.followErrOf(JSONObject("""{"status":500}""")))
    }

    @Test
    fun `第一帧关注态推断`() {
        val followed = setOf("57686982", "93956731")
        val ids = mapOf("117734933838213" to "93956731")
        // 入参就是短 puid（关注列表入口）→ 命中
        assertTrue(HupuFollowStore.fastFollowedOf("93956731", followed, emptyMap()) == true)
        // 入参是 euid → 经映射后命中
        assertTrue(HupuFollowStore.fastFollowedOf("117734933838213", followed, ids) == true)
        // 映射到的 puid 不在集合里 → 未知（不是 false）
        assertNull(HupuFollowStore.fastFollowedOf("117734933838213", followed, mapOf("117734933838213" to "999")))
        // 纯未知：空 ID、无映射、集合为空
        assertNull(HupuFollowStore.fastFollowedOf("", followed, ids))
        assertNull(HupuFollowStore.fastFollowedOf("123", followed, emptyMap()))
        assertNull(HupuFollowStore.fastFollowedOf("123", emptySet(), emptyMap()))
    }
}