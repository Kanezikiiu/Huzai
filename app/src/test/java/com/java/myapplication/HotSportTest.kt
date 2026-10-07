package com.java.myapplication

import com.java.myapplication.data.HotSport
import com.java.myapplication.data.HupuMatch
import com.java.myapplication.data.HupuMatchDay
import org.junit.Assert.assertEquals
import org.junit.Test

/** 1.199：热门赛事的「项目归类」与筛选行为锁定。 */
class HotSportTest {

    // ---------- 归类（**顺序敏感**：具体词必须先命中） ----------

    @Test
    fun wttIsPingPongNotTennis() {
        // 回归：曾被网球规则里的「大满贯」抢走 → 乒乓球场次少了一半
        assertEquals("乒乓球", HotSport.of("WTT中国大满贯女单"))
    }

    @Test
    fun atpIsTennis() {
        assertEquals("网球", HotSport.of("ATP中网男单"))
    }

    @Test
    fun wtaIsTennis() {
        assertEquals("网球", HotSport.of("WTA新加坡站女单"))
    }

    @Test
    fun snooker() {
        assertEquals("斯诺克", HotSport.of("斯诺克英格兰公开赛半决赛"))
    }

    @Test
    fun f1() {
        assertEquals("赛车/F1", HotSport.of("F1阿塞拜疆站一练"))
    }

    @Test
    fun ufc() {
        assertEquals("综合格斗", HotSport.of("UFC332头条主赛"))
    }

    @Test
    fun volleyball() {
        assertEquals("排球", HotSport.of("排球女子小组赛"))
    }

    @Test
    fun esport() {
        assertEquals("电子竞技", HotSport.of("亚运会王者荣耀A组"))
    }

    @Test
    fun unknownFallsBackToOther() {
        assertEquals(HotSport.OTHER, HotSport.of("某某未知赛事", "决赛"))
    }

    // ---------- 筛选 ----------

    private fun match(id: String, intro: String) = HupuMatch(
        matchId = id,
        statusDesc = "已结束",
        status = "COMPLETED",
        introduction = intro,
        matchName = "",
        startTimeText = "",
        startTimestamp = 0L,
        scoreCountText = "",
        home = null,
        away = null,
        winnerMemberId = null,
        playerScore = null,
        scoreBizType = null,
        scoreBizNo = null,
    )

    @Test
    fun filterDropsExcludedMatchesAndEmptyDays() {
        val d1 = HupuMatchDay("2026-01-01", "1月1日", listOf(
            match("a", "斯诺克英格兰公开赛"),
            match("b", "ATP中网男单"),
        ))
        val d2 = HupuMatchDay("2026-01-02", "1月2日", listOf(match("c", "斯诺克苏格兰公开赛")))
        val kept = HotSport.filterDays(listOf(d1, d2), setOf("斯诺克"))
        // 1月2日被整日筛掉 → 日期块消失（避免定位到空日子）
        assertEquals(1, kept.size)
        assertEquals(1, kept[0].matches.size)
        assertEquals("b", kept[0].matches[0].matchId)
    }

    @Test
    fun filterWithEmptyExcludedKeepsEverything() {
        val d1 = HupuMatchDay("2026-01-01", "1月1日", listOf(match("a", "斯诺克")))
        assertEquals(1, HotSport.filterDays(listOf(d1), emptySet()).size)
    }

    @Test
    fun countsFollowsTableOrder() {
        val d1 = HupuMatchDay("2026-01-01", "1月1日", listOf(
            match("a", "ATP中网男单"),
            match("b", "WTT中国大满贯女单"),
            match("c", "斯诺克英格兰公开赛"),
        ))
        // 顺序 = 关键词表权重顺序：斯诺克 → 乒乓球 → 网球
        assertEquals(listOf("斯诺克" to 1, "乒乓球" to 1, "网球" to 1), HotSport.counts(listOf(d1)))
    }
}