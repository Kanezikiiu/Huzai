package com.java.myapplication

import com.java.myapplication.data.HupuAccount
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.148 games 写端点「版本过旧」自愈单测（纯函数）。
 *
 * 背景：服务端会随时间抬高 games 域的最低版本号，命中后返回
 * `{"code":..., "msg":"应用版本过旧，请升级到最新版本"}`。
 * 自愈策略：命中该错误时按阶梯抬高版本段重试，成功的版本持久化（HupuPrefs.gamesVersionOf/saveGamesVersion）。
 */
class HupuGamesVersionTest {

    private fun err(msg: String): JSONObject = JSONObject().put("code", -1).put("msg", msg)

    @Test
    fun `识别服务端版本过旧拦截`() {
        assertTrue(HupuAccount.isVersionTooOld(err("应用版本过旧，请升级到最新版本")))
        assertTrue(HupuAccount.isVersionTooOld(err("请升级到最新版本")))
        assertTrue(
            HupuAccount.isVersionTooOld(JSONObject().put("code", -1).put("message", "应用版本过旧"))
        )
    }

    @Test
    fun `普通失败与成功不算版本过旧`() {
        assertFalse(HupuAccount.isVersionTooOld(null))
        assertFalse(HupuAccount.isVersionTooOld(JSONObject().put("code", 1)))
        assertFalse(HupuAccount.isVersionTooOld(err("网络开小差了，请稍后重试")))
        assertFalse(HupuAccount.isVersionTooOld(JSONObject().put("code", 401).put("type", "LOGIN")))
    }

    @Test
    fun `版本阶梯从 8_2_99 抬高`() {
        val ladder = HupuAccount.versionLadder("8.2.99")
        assertEquals("8.3.99", ladder[0])
        assertEquals("8.8.99", ladder[5])
        assertEquals("9.2.99", ladder[6])
        assertEquals("10.2.99", ladder[7])
        assertFalse(ladder.contains("8.2.99"))
        assertEquals(8, ladder.size)
    }

    @Test
    fun `阶梯保留 patch 段并兼容非标准输入`() {
        assertEquals("8.3.16", HupuAccount.versionLadder("8.2.16")[0])
        assertTrue(HupuAccount.versionLadder("8.2").isEmpty())
        assertTrue(HupuAccount.versionLadder("").isEmpty())
    }
}