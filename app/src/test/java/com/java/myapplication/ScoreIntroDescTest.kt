package com.java.myapplication

import com.java.myapplication.data.scoreIntroFromDesc
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 1.224k：评分对象「说明文本」（`infoJson.desc[0]`）的收敛规则单测。
 *
 * 背景（真机反馈）：说明文本为空时，二级页名称下方显示了 `[""]` 字样。
 * 根因是解析层「先试数组取第 0 项、为空再回落取整个 desc 字段」，而 desc 本身就是
 * 一个数组 → 回落把数组本体序列化成了字符串。
 *
 * 口径：**只把「空」和「JSON 字面量」收敛成空**，其余一律原样显示 ——
 * 短文本（含 KDA 串）也是要显示的，不能按长度/形态过滤。
 */
class ScoreIntroDescTest {

    @Test
    fun `JSON 数组字面量按空处理——不得显示为方括号字样`() {
        assertEquals("", scoreIntroFromDesc("[\"\"]"))
        assertEquals("", scoreIntroFromDesc("[]"))
        assertEquals("", scoreIntroFromDesc("[\" \"]"))
        assertEquals("", scoreIntroFromDesc("{}"))
    }

    @Test
    fun `null 与空串按空处理`() {
        assertEquals("", scoreIntroFromDesc(null))
        assertEquals("", scoreIntroFromDesc(""))
        assertEquals("", scoreIntroFromDesc("   "))
    }

    @Test
    fun `短文本必须显示——含 KDA 串`() {
        assertEquals("K/D/A:1/3/20", scoreIntroFromDesc("K/D/A:1/3/20"))
        assertEquals("KDA 10/2/5", scoreIntroFromDesc("KDA 10/2/5"))
        assertEquals("1/3/20", scoreIntroFromDesc("1/3/20"))
        assertEquals("中单", scoreIntroFromDesc("中单"))
        assertEquals("热血战斗番", scoreIntroFromDesc("热血战斗番"))
    }

    @Test
    fun `长文本原样返回且去掉首尾空白`() {
        val text = "这部作品讲的是一个少年在赛场上一步步成长的故事"
        assertEquals(text, scoreIntroFromDesc(text))
        assertEquals(text, scoreIntroFromDesc("  $text  "))
    }
}
