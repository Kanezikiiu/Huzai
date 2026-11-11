package com.java.myapplication

import com.java.myapplication.data.HupuParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 1.335：列表页 `desc`（摘要）清洗测试。
 *
 * 治理三类「不该显示」的 desc：
 *  · 机器生成的比赛内链 JSON（`{"team":"football","type":"iframe-match",...}`）
 *  · 纯 `[图片]` 占位
 *  · 带前导空格（会导致摘要行右移）
 * 同时要保证正常长文本**不被误伤**（实测有以数字开头、以「真」「中」开头的正文）。
 */
class ThreadDescCleanTest {

    /** raw string 里 `$` 无法转义，用插值方式拼出 `$$` */
    private val D = "$"

    /**
     * 注意：parseBoardPage 走的是 SSR 通道 —— 它从 HTML 里找 `window.$$data=` 再取 JSON。
     * 所以这里必须把 JSON 包成那个形态，直接喂裸 JSON 会解析失败（返回 null）。
     */
    private fun boardJson(desc: String): String = """
        <script>window.${D}${D}data={"pageData":{"threads":[
          {"tid":"1","title":"标题","desc":${jsonStr(desc)}}
        ]}}</script>
    """.trimIndent()

    private fun jsonStr(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                else -> sb.append(c)
            }
        }
        return sb.append("\"").toString()
    }

    private fun descOf(raw: String): String? =
        HupuParser.parseBoardPage(boardJson(raw))?.threads?.firstOrNull()?.desc

    @Test
    fun `比赛内链 JSON 被丢弃`() {
        val raw = """{"team":"football","type":"iframe-match","url":"https://games.mobileapi.hupu.com/football#/football/football_recap?matchId=1"}"""
        assertNull("内链 JSON 不应显示", descOf(raw))
    }

    @Test
    fun `纯图片占位被丢弃`() {
        assertNull(descOf("[图片]"))
    }

    @Test
    fun `前导空格被清理`() {
        val d = descOf("    [图片]虎扑11月05日讯 数据博主统计了本赛季净效率值")
        assertEquals("[图片]虎扑11月05日讯 数据博主统计了本赛季净效率值", d)
    }

    @Test
    fun `首尾空白都会去掉`() {
        assertEquals("正常摘要", descOf("  正常摘要  "))
    }

    @Test
    fun `空 desc 归一为 null`() {
        assertNull(descOf(""))
        assertNull(descOf("     "))
    }

    @Test
    fun `正常长文本不被误伤`() {
        // 实测存在这几种开头的正常正文，必须原样保留
        val cases = listOf(
            "1月热身赛0比2 上一场0比1 这场1比2，打柬埔寨球队也打不赢",
            "1999山东最后一轮落后辽宁，靠着北京国安血性绝平辽宁拿到队史首冠",
            "5个西协乙，最高的身价已经800万欧",
            "真是一场好球",
            "2026赛季中超联赛本周末将迎来最后一轮",
        )
        for (c in cases) {
            assertEquals("不应误伤：$c", c, descOf(c))
        }
    }

    @Test
    fun `含 type 字样但非 JSON 的正文保留`() {
        val c = "这个 type 分类我不太懂，谁能解释一下"
        assertEquals(c, descOf(c))
    }
}