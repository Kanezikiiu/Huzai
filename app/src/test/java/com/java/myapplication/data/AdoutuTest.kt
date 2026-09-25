package com.java.myapplication.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.191: adoutu 搜索结果解析单测。
 *
 * 样本取自真实搜索页（`/search?type=1&keyword=猫`）的实际 HTML 结构：
 * 结果卡片 = `<a href="/picture/{id}"><img alt="标签" src="https://img.adoutu.com/picture/…"/></a>`。
 * 解析失败会直接表现为「搜索面板空白」，所以这里把结构、去重、无结果页三条都锁住。
 */
class AdoutuTest {

    private val card1 = """
        <a class="group overflow-hidden rounded-xl border border-gray-200 bg-white" href="/picture/111172">
        <div class="relative aspect-square"><img alt="高清猫咪开警车 猫咪出警表情包" loading="lazy"
        src="https://img.adoutu.com/picture/1609683067875.jpg"/></div>
        <div class="p-2"><p class="truncate">高清猫咪开警车 猫咪出警表情包</p></div></a>
    """.trimIndent()

    private val card2 = """
        <a class="group" href="/picture/111167"><div><img alt="非常后悔(猫咪表情包)"
        src="https://img.adoutu.com/picture/1609683067085.gif"/></div></a>
    """.trimIndent()

    @Test
    fun parseSearch_extractsIdUrlTitle_inPageOrder() {
        val html = "<html><body>$card1$card2</body></html>"
        val list = Adoutu.parseSearch(html)
        assertEquals(2, list.size)
        assertEquals("111172", list[0].id)
        assertEquals("https://img.adoutu.com/picture/1609683067875.jpg", list[0].url)
        assertEquals("高清猫咪开警车 猫咪出警表情包", list[0].title)
        assertEquals("111167", list[1].id)
        assertEquals("https://img.adoutu.com/picture/1609683067085.gif", list[1].url)
    }

    @Test
    fun parseSearch_dedupesSameIdOrSameUrl() {
        // 同一条结果在页面里出现两次（列表里出现重复卡片）→ 只保留一条
        val html = "<div>$card1</div><div>$card1</div>"
        assertEquals(1, Adoutu.parseSearch(html).size)
        // 同 URL 不同 id（同一张图挂了两个详情页）→ 也去重
        val sameUrl = card1.replace("/picture/111172", "/picture/999999")
        assertEquals(1, Adoutu.parseSearch("<div>$card1</div><div>$sameUrl</div>").size)
    }

    @Test
    fun parseSearch_skipsForeignImageHost_andCardsWithoutImage() {
        val foreign = """
            <a href="/picture/123"><img alt="x" src="https://cdn.other.com/a.jpg"/></a>
        """.trimIndent()
        val noImg = """<a href="/picture/456"><div>无图卡片</div></a>"""
        assertEquals(0, Adoutu.parseSearch("$foreign$noImg").size)
    }

    @Test
    fun parseSearch_emptyResultPage_dropsRandomRecommendations() {
        // 真实无结果页：文案「没有找到与「x」相关的结果」+ 下方 8 条「随机推荐表情」
        val html = """
            <div><p>没有找到与「zzzqqqxxyy123」相关的结果</p></div>
            <div><h2>随机推荐表情</h2></div>
            <a href="/picture/44926"><img alt="别瞎起哄" src="https://img.adoutu.com/picture/1.jpg"/></a>
            <a href="/picture/42230"><img alt="滑稽抱咸鱼" src="https://img.adoutu.com/picture/2.gif"/></a>
        """.trimIndent()
        assertTrue(Adoutu.isEmptyPage(html))
        // 必须整体丢弃：否则「搜不到」会显示成「搜到 2 条不相干的」
        assertEquals(0, Adoutu.parseSearch(html).size)
    }

    @Test
    fun isEmptyPage_falseForNormalResultPage() {
        assertFalse(Adoutu.isEmptyPage("<html><body>$card1</body></html>"))
    }

    @Test
    fun searchUrl_pinsStickerCategory_andEncodesKeyword() {
        val url = Adoutu.searchUrl("猫", 2)
        assertTrue(url.startsWith("https://www.adoutu.com/search?"))
        assertTrue("必须锁定表情包分类", url.contains("type=1"))
        assertTrue("中文关键词必须转义", url.contains("keyword=%E7%8C%AB"))
        assertTrue(url.contains("page=2"))
        // 关键词里的空格不能变成 +（避免站点把它当字面加号）
        assertTrue(Adoutu.searchUrl("a b", 1).contains("keyword=a%20b"))
    }

    @Test
    fun stickerReferer_onlyForAdoutuImages() {
        assertEquals(Adoutu.REFERER, stickerReferer("https://img.adoutu.com/picture/1.gif"))
        assertNull(stickerReferer("https://i2.hoopchina.com.cn/a.jpg"))
        assertNull(stickerReferer("file:///data/a.jpg"))
    }

    @Test
    fun unescapeHtml_decodesCommonEntities() {
        val input = "A" + "\u0026amp;" + "B" + "\u0026quot;" + "C" + "\u0026#39;" + "D" +
            "\u0026lt;" + "E" + "\u0026gt;"
        assertEquals("A\u0026B\"C'D<E>", unescapeHtml(input))
    }
}