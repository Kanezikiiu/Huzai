package com.java.myapplication

import com.java.myapplication.data.HupuUpdate
import com.java.myapplication.data.HupuUpdateParser
import com.java.myapplication.data.HupuUpdateResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.177 检查更新：version.json 解析 + 版本判定（纯逻辑，不触网）。
 */
class HupuUpdateParserTest {

    private val fullJson = """
        {
          "versionCode": 187,
          "versionName": "1.177",
          "apkUrl": "https://github.com/Kanezikiiu/Huzai/releases/download/v1.177/huzai-1.177-release.apk",
          "releaseUrl": "https://github.com/Kanezikiiu/Huzai/releases/tag/v1.177",
          "changelog": "· 新增检查更新\n· 修复若干问题",
          "forceUpdate": true
        }
    """.trimIndent()

    @Test
    fun parseFullFields() {
        val info = HupuUpdateParser.parse(fullJson)
        requireNotNull(info)
        assertEquals(187, info.versionCode)
        assertEquals("1.177", info.versionName)
        assertTrue(info.apkUrl.endsWith("huzai-1.177-release.apk"))
        assertTrue(info.releaseUrl.endsWith("/v1.177"))
        assertTrue(info.changelog.contains("新增检查更新"))
        assertTrue(info.forceUpdate)
    }

    @Test
    fun parseMinimalFields() {
        // 仅版本号 + 名称也可解析；其余字段缺省
        val info = HupuUpdateParser.parse("""{"versionCode":187,"versionName":"1.177"}""")
        requireNotNull(info)
        assertEquals(187, info.versionCode)
        assertEquals("1.177", info.versionName)
        assertEquals("", info.apkUrl)
        assertEquals("", info.releaseUrl)
        assertEquals("", info.changelog)
        assertFalse(info.forceUpdate)
    }

    @Test
    fun parseMissingVersionCodeIsNull() {
        assertNull(HupuUpdateParser.parse("""{"versionName":"1.177"}"""))
    }

    @Test
    fun parseMissingVersionNameIsNull() {
        assertNull(HupuUpdateParser.parse("""{"versionCode":187}"""))
    }

    @Test
    fun parseZeroVersionCodeIsNull() {
        assertNull(HupuUpdateParser.parse("""{"versionCode":0,"versionName":"1.177"}"""))
    }

    @Test
    fun parseInvalidJsonIsNull() {
        assertNull(HupuUpdateParser.parse("<html>404: Not Found</html>"))
    }

    @Test
    fun decideAvailableWhenRemoteNewer() {
        val info = HupuUpdateParser.parse(fullJson)!!
        assertTrue(HupuUpdate.decide(186, info) is HupuUpdateResult.Available)
    }

    @Test
    fun decideLatestWhenRemoteEquals() {
        val info = HupuUpdateParser.parse(fullJson)!!
        assertEquals(HupuUpdateResult.Latest, HupuUpdate.decide(187, info))
    }

    @Test
    fun decideLatestWhenRemoteOlder() {
        val info = HupuUpdateParser.parse(fullJson)!!
        assertEquals(HupuUpdateResult.Latest, HupuUpdate.decide(200, info))
    }

    @Test
    fun changelogLineBreakEscapeBecomesRealNewlines() {
        // version.json 里条目之间的换行按 JSON 规范写成 \n（反斜杠 + n 两个字符）；
        // 解析后必须是「真正的换行符」，Compose 的 Text 才能逐条成行。
        // 这条同时锁定：optString + trim 不会把内部换行吃掉、也不会留下 \n 字面量。
        val info = HupuUpdateParser.parse(fullJson)!!
        assertEquals(
            "应解出 2 条（真换行）",
            2,
            info.changelog.split("\n").size,
        )
        assertFalse("不应残留反斜杠 n 字面量", info.changelog.contains("\\n"))
    }

    @Test
    fun fourItemChangelogDecodesFourLines() {
        // 与线上 version.json（1.187）同形的 4 条更新说明
        val json = """
            {
              "versionCode": 197,
              "versionName": "1.187",
              "changelog": "· 第一条\n· 第二条\n· 第三条\n· 第四条",
              "forceUpdate": false
            }
        """.trimIndent()
        val info = HupuUpdateParser.parse(json)!!
        val lines = info.changelog.split("\n")
        assertEquals("4 条更新说明应解出 4 行", 4, lines.size)
        assertTrue(lines[0].startsWith("· 第一条"))
        assertTrue(lines[3].startsWith("· 第四条"))
    }

    @Test
    fun decideFailedWhenInfoNull() {
        assertEquals(HupuUpdateResult.Failed, HupuUpdate.decide(186, null))
    }

    @Test
    fun changelogArrayJoinsWithNewline() {
        // 1.188: 数组格式（推荐）——源文件一眼可读，无需在 JSON 里写 \n 转义
        val json = """{"versionCode":198,"versionName":"1.188","changelog":["\u00B7 甲","\u00B7 乙","\u00B7 丙"]}"""
        val info = HupuUpdateParser.parse(json)!!
        val lines = info.changelog.split("\n")
        assertEquals("数组 3 条应解出 3 行", 3, lines.size)
        assertTrue(lines[0].endsWith("甲"))
        assertTrue(lines[2].endsWith("丙"))
        assertFalse("不应残留反斜杠 n 字面量", info.changelog.contains("\\n"))
    }

    @Test
    fun changelogArraySkipsBlankItems() {
        // 数组里的空串 / 纯空白条目应被丢弃，不产生空行
        val json = """{"versionCode":198,"versionName":"1.188","changelog":["\u00B7 甲","  ","","\u00B7 乙"]}"""
        val info = HupuUpdateParser.parse(json)!!
        assertEquals(2, info.changelog.split("\n").size)
    }

    @Test
    fun changelogSingleLineBulletSplitFallback() {
        // 历史误写成一整行（只有 · 分隔、没有换行）→ 自动按 · 拆行兜底
        val json = """{"versionCode":198,"versionName":"1.188","changelog":"\u00B7 甲\u00B7 乙\u00B7 丙"}"""
        val info = HupuUpdateParser.parse(json)!!
        val lines = info.changelog.split("\n")
        assertEquals("整行 3 条应被拆成 3 行", 3, lines.size)
        assertTrue(lines[0].endsWith("甲"))
    }

    @Test
    fun changelogJsonNullIsEmpty() {
        // JSON null 不得渲染成 "null" 字面量
        val json = """{"versionCode":198,"versionName":"1.188","changelog":null}"""
        val info = HupuUpdateParser.parse(json)!!
        assertEquals("", info.changelog)
    }

    @Test
    fun changelogMustBeStringForLegacyClients() {
        // 回归守卫：旧客户端（<= 1.187）的解析器只有 optString("changelog")：
        //  - 字符串 → 读到原文（能正确逐行显示）
        //  - 数组   → 读到 JSON 数组文本 ["…","…"]，弹窗会直接把原始 JSON 显示出来（1.188 发版时踩过）
        // 因此线上 version.json 必须保持字符串格式；解析器的数组支持仅是向前兼容能力。
        val strJson = """{"versionCode":198,"versionName":"1.188","changelog":"\u00B7 甲\n\u00B7 乙"}"""
        val arrJson = """{"versionCode":198,"versionName":"1.188","changelog":["\u00B7 甲","\u00B7 乙"]}"""
        val legacyReadsString = org.json.JSONObject(strJson).optString("changelog").trim()
        val legacyReadsArray = org.json.JSONObject(arrJson).optString("changelog").trim()
        assertFalse("字符串格式不应带 JSON 数组外壳", legacyReadsString.startsWith("[\""))
        assertTrue("数组格式在旧客户端会被读成数组文本（这正是要避免的）", legacyReadsArray.startsWith("[\""))
        // 新解析器两种都能正确逐行
        assertEquals(2, HupuUpdateParser.parse(strJson)!!.changelog.split("\n").size)
        assertEquals(2, HupuUpdateParser.parse(arrJson)!!.changelog.split("\n").size)
    }
}