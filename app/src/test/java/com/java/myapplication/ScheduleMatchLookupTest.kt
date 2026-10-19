package com.java.myapplication

import com.java.myapplication.data.HupuMatchParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * 1.224o：用**真实赛程 JSON**（热门赛事那一份，含目标比赛）跑一遍解析器。
 *
 * 目的：正文深链 `huputiyu://general/live?matchId=…` 要靠"赛程里找到这场 + 拿到评分钥匙"
 * 才能原生打开；如果解析器把这场丢掉了，`find()` 永远返回 null，表现就是"点了没反应/没跳转"。
 * 这里把这条链路钉死。
 */
class ScheduleMatchLookupTest {

    private fun loadSample(): String =
        javaClass.classLoader!!.getResourceAsStream("schedule_hot_sample.json")!!
            .bufferedReader(Charsets.UTF_8).use { it.readText() }

    private val targetMatchId = "1441778164564991655"

    @Test
    fun `真实赛程能被解析出天列表`() {
        val days = HupuMatchParser.parseSchedule(loadSample())
        assertEquals(1, days.size)
        assertEquals(4, days.first().matches.size)
    }

    @Test
    fun `目标比赛在赛程里且带评分钥匙`() {
        val days = HupuMatchParser.parseSchedule(loadSample())
        val m = days.flatMap { it.matches }.firstOrNull { it.matchId == targetMatchId }
        assertNotNull("解析结果里没有目标比赛（深链会因此打不开）", m)
        assertEquals("common_sports_first", m!!.scoreBizType)
        assertEquals("34572", m.scoreBizNo)
        // 记分板需要的两队名字也要在
        assertNotNull(m.home?.name)
        assertNotNull(m.away?.name)
    }
}