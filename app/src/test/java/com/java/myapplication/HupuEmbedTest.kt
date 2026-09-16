package com.java.myapplication

import com.java.myapplication.data.HupuEmbedParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 1.178 结构化正文（HupuEmbed）解析：真实样本 + 边界 + 反例（普通 HTML 帖不得误判）。
 */
class HupuEmbedTest {

    // 真实样本（帖子 642451842，PC SSR 与手机端 content 完全一致）
    private val realContent =
        """{"team":"football","type":"iframe-match","url":"https://games.mobileapi.hupu.com/football#/football/football_recap?matchId=3867210-BATTLE_REPORT","matchId":"3867210-BATTLE_REPORT"}"""

    @Test
    fun parseRealIframeMatch() {
        val e = HupuEmbedParser.parse(realContent)
        requireNotNull(e)
        assertEquals("iframe-match", e.type)
        assertEquals("3867210-BATTLE_REPORT", e.matchId)
        assertEquals("比赛战报", e.title)
        assertEquals("战报", e.badge)
        assert(e.url.startsWith("https://games.mobileapi.hupu.com/football#/"))
    }

    @Test
    fun parseWithWhitespaceAndParagraphWrapper() {
        // 兼容首尾空白 / <p> 包裹（官方历史形态可能不同）
        assertNotNull(HupuEmbedParser.parse("  $realContent  "))
        assertNotNull(HupuEmbedParser.parse("<p>$realContent</p>"))
    }

    @Test
    fun unknownTypeStillParsesWithFallbackLabel() {
        val e = HupuEmbedParser.parse("""{"type":"iframe-video","url":"https://x/y"}""")
        requireNotNull(e)
        assertEquals("嵌入内容", e.title)
        assertEquals("嵌入", e.badge)
        assertEquals("", e.matchId)
    }

    @Test
    fun plainHtmlContentIsNotEmbed() {
        assertNull(HupuEmbedParser.parse("<p>今天天气不错</p>"))
        assertNull(HupuEmbedParser.parse("<p><img src=\"https://a/b.jpg\"/></p>"))
        assertNull(HupuEmbedParser.parse("<p>[vote]123[/vote]</p>"))
    }

    @Test
    fun jsonWithoutTypeOrUrlIsNotEmbed() {
        assertNull(HupuEmbedParser.parse("""{"team":"football"}"""))
        assertNull(HupuEmbedParser.parse("""{"type":"iframe-match"}"""))
        assertNull(HupuEmbedParser.parse("""{"url":"https://x/y"}"""))
        // JSON null 不得被当成字符串 "null"
        assertNull(HupuEmbedParser.parse("""{"type":null,"url":null}"""))
    }

    @Test
    fun blankAndMalformedReturnNull() {
        assertNull(HupuEmbedParser.parse(null))
        assertNull(HupuEmbedParser.parse(""))
        assertNull(HupuEmbedParser.parse("   "))
        assertNull(HupuEmbedParser.parse("{not json}"))
        assertNull(HupuEmbedParser.parse("{}"))
    }
}