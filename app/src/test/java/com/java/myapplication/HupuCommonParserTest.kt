package com.java.myapplication

import com.java.myapplication.data.HupuCommonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 通用评分（虎扑评分卡片流）解析：JSON null 安全。
 *
 * 背景：Android org.json 的 optString 对 JSON null 返回字符串 "" 字面量
 * （与 1.7 赛程卡「四个 null」同源）。主题无简介时服务端给 "desc": null，
 * 卡片在简介位就会渲染出 "" 字样。此处锁定该形态必须转成空串。
 */
class HupuCommonParserTest {

    /** 把 __NEXT_DATA__ 载荷包进最小 SSR 外壳 */
    private fun ssr(json: String): String =
        "<html><body><script id=\"__NEXT_DATA__\" type=\"application/json\">$json</script></body></html>"

    /** 一张主题卡所需的字段（url 里带 outBizType / outBizNo，解析器据此取出口参数） */
    private fun card(descValue: String, hottestValue: String): String = """
        {"props":{"pageProps":{"list":[
          {
            "subject":{"label":"备用标签"},
            "item":{
              "name":"铁甲威虫之骑刃王评分",
              "score":"9.2",
              "scoreCountNum":141,
              "desc":$descValue,
              "url":"https://m.hupu.com/score/detail.html?t=https%3A%2F%2Fm.hupu.com%2Fscore%3FoutBizType%3Dcommon_first%26outBizNo%3D12855"
            },
            "detail":{"items":[
              {"name":"苗纹纹.","itemBizId":"1001","itemBizType":"common_second",
               "cover":null,"score":"9.5","scoreCountNum":12,
               "hottestComment":$hottestValue}
            ]}
          }
        ]}}}
    """.trimIndent()

    @Test
    fun `json-null desc becomes empty string not literal null`() {
        val list = HupuCommonParser.parseScoreHome(ssr(card("null", "null")))
        assertEquals("应解析出 1 张主题卡", 1, list.size)
        val s = list.first()
        assertEquals("JSON null 简介必须转成空串", "", s.desc)
        assertTrue("空简介不应通过 isNotBlank 守卫（卡片不渲染该行）", !s.desc.isNotBlank())
        assertEquals("主题名不应受污染", "铁甲威虫之骑刃王评分", s.name)
    }

    @Test
    fun `literal string null desc is also cleaned`() {
        // 真实数据里是 "desc":null（实测 SSR 113 处），但 AOSP 的 optString 会把它变成
        // 字符串 "null"、json.org 的 optString 变成 ""；两种形态都必须落回空串，
        // 否则卡片会渲染出一行 "null" 字样。
        val list = HupuCommonParser.parseScoreHome(ssr(card("\"null\"", "\"null\"")))
        assertEquals(1, list.size)
        val s = list.first()
        assertEquals("字面量 null 简介必须转成空串", "", s.desc)
        assertTrue(!s.desc.isNotBlank())
        assertEquals("字面量 null 热评必须转成空串", "", s.items.first().hotComment)
    }

    @Test
    fun `quoted empty string desc is cleaned`() {
        // 对照：服务端若给空串（而非 null），同样不应渲染出空的简介行
        val list = HupuCommonParser.parseScoreHome(ssr(card("\"\"", "\"\"")))
        assertEquals(1, list.size)
        assertEquals("", list.first().desc)
        assertTrue(!list.first().desc.isNotBlank())
    }

    @Test
    fun `normal desc is preserved`() {
        val list = HupuCommonParser.parseScoreHome(ssr(card("\"什么时候才能出第二季\"", "\"热评内容\"")))
        assertEquals(1, list.size)
        assertEquals("什么时候才能出第二季", list.first().desc)
    }

    @Test
    fun `json-null cover and hottestComment are cleaned`() {
        val list = HupuCommonParser.parseScoreHome(ssr(card("\"有简介\"", "null")))
        val item = list.first().items.first()
        assertNull("JSON null 封面应转成 null（Coil 不加载）", item.image)
        assertEquals("JSON null 热评应转成空串", "", item.hotComment)
        assertEquals("子项名应正常", "苗纹纹.", item.name)
    }

    @Test
    fun `card without desc still parses`() {
        // 对照组：简介字段整体缺失（key 不存在）也不能崩、不能产出 ""
        val json = """
            {"props":{"pageProps":{"list":[
              {"subject":{"label":"电影"},
               "item":{"name":"某部电影","score":"8.8","scoreCountNum":10,
                       "url":"https://m.hupu.com/score/detail.html?outBizType=common_first&outBizNo=999"},
               "detail":{"items":[]}}
            ]}}}
        """.trimIndent()
        val list = HupuCommonParser.parseScoreHome(ssr(json))
        assertEquals(1, list.size)
        val s = list.first()
        assertNotNull(s)
        assertEquals("", s.desc)
        assertEquals("999", s.bizNo)
        assertEquals("common_first", s.bizType)
    }
}