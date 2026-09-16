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
    fun decideFailedWhenInfoNull() {
        assertEquals(HupuUpdateResult.Failed, HupuUpdate.decide(186, null))
    }
}