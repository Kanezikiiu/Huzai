package com.java.myapplication

import com.java.myapplication.data.HupuMatch
import com.java.myapplication.data.HupuMatchDay
import com.java.myapplication.data.HupuMatchTeam
import com.java.myapplication.data.HotSport
import com.java.myapplication.ui.pages.TeamFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.243：赛事频道「队伍筛选」的纯逻辑（面板与过滤都不依赖网络，可直接测）。
 */
class TeamFilterTest {

    private fun team(id: String, name: String) = HupuMatchTeam(
        memberId = id,
        name = name,
        logo = null,
        baseScore = "0",
        extraScore = null,
        bigScore = null,
    )

    private fun match(id: String, home: HupuMatchTeam?, away: HupuMatchTeam?) = HupuMatch(
        matchId = id,
        statusDesc = "未开始",
        status = "NOTSTARTED",
        introduction = "常规赛",
        matchName = "常规赛",
        startTimeText = "10月22日 20:00",
        startTimestamp = 0L,
        scoreCountText = "0人评分",
        home = home,
        away = away,
        winnerMemberId = null,
        playerScore = null,
        scoreBizType = "nba_match",
        scoreBizNo = id,
    )

    private val days = listOf(
        HupuMatchDay(
            dayTime = "2026-10-22",
            dateBlock = "10月22日",
            matches = listOf(
                match("m1", team("t1", "湖人"), team("t2", "勇士")),
                match("m2", team("t3", "凯尔特人"), team("t4", "热火")),
            ),
        ),
        HupuMatchDay(
            dayTime = "2026-10-23",
            dateBlock = "10月23日",
            matches = listOf(match("m3", team("t1", "湖人"), team("t3", "凯尔特人"))),
        ),
        HupuMatchDay(
            dayTime = "2026-10-24",
            dateBlock = "10月24日",
            matches = listOf(match("m4", team("t5", "太阳"), team("t6", "国王"))),
        ),
    )

    @Test
    fun `编码解码往返`() {
        val raw = TeamFilter.encode("t1", "湖人")
        assertEquals("t1" to "湖人", TeamFilter.decode(raw))
        assertNull(TeamFilter.decode("没有分隔符"))
        assertNull(TeamFilter.decode("\t只有名字"))
    }

    @Test
    fun `未选择任何队伍时原样返回`() {
        assertEquals(days, TeamFilter.filterDays(days, emptySet()))
    }

    @Test
    fun `只保留选中队伍的比赛，空日期块被丢弃`() {
        val sel = setOf(TeamFilter.encode("t1", "湖人"))
        val out = TeamFilter.filterDays(days, sel)
        // 22 日两场里只有 m1（含湖人）；23 日 m3（含湖人）；24 日没有湖人 → 整块丢弃
        assertEquals(2, out.size)
        assertEquals(listOf("m1"), out[0].matches.map { it.matchId })
        assertEquals(listOf("m3"), out[1].matches.map { it.matchId })
    }

    @Test
    fun `多选任一命中即保留`() {
        val sel = setOf(
            TeamFilter.encode("t5", "太阳"),
            TeamFilter.encode("t2", "勇士"),
        )
        val out = TeamFilter.filterDays(days, sel)
        assertEquals(2, out.size)
        assertEquals(listOf("m1"), out[0].matches.map { it.matchId })
        assertEquals(listOf("m4"), out[1].matches.map { it.matchId })
    }

    @Test
    fun `面板清单按场次排序，并补上已选但今天没比赛的队伍`() {
        val sel = setOf(TeamFilter.encode("t9", "已解散队"))
        val items = TeamFilter.items(days, sel)
        // 场次多的排前面：湖人(t1) 与凯尔特人(t3) 各 2 场，占前两位（同场次按名称排序）
        assertEquals(2, items.first().count)
        assertEquals(setOf("湖人", "凯尔特人"), items.take(2).map { it.name }.toSet())
        val ghost = items.first { it.id == "t9" }
        assertEquals(0, ghost.count)
        assertEquals("已解散队", ghost.name)
    }

    @Test
    fun `项目排除与队伍筛选可以叠加（热门赛事）`() {
        // 热门赛事：先按项目排除，再按队伍包含
        val cats = HotSport.counts(days)
        assertEquals(1, cats.size)
        val afterCat = HotSport.filterDays(days, setOf(cats.first().first))
        assertTrue(afterCat.isEmpty())
        // 再叠加队伍筛选仍然为空（顺序无关，结果都是空集）
        assertTrue(TeamFilter.filterDays(afterCat, setOf(TeamFilter.encode("t1", "湖人"))).isEmpty())
    }

    @Test
    fun `项目筛选语义统一：排除集与选中集互为补集`() {
        val all = listOf("篮球", "足球", "斯诺克", "其他")
        // 旧数据：排除了「篮球」→ 新语义下应相当于「选中其余三项」
        assertEquals(
            setOf("足球", "斯诺克", "其他"),
            HotSport.selectedFromExcluded(all, setOf("篮球")),
        )
        // 没有排除项 = 不筛选 = 没有任何选中项
        assertEquals(emptySet<String>(), HotSport.selectedFromExcluded(all, emptySet()))
        // 写回：选中一项 → 排除其余
        assertEquals(
            setOf("篮球"),
            HotSport.excludedFromSelected(all, setOf("足球", "斯诺克", "其他")),
        )
        // 「一个都没选」与「全选」都等价于不筛选 → 都写空集
        assertEquals(emptySet<String>(), HotSport.excludedFromSelected(all, emptySet()))
        assertEquals(emptySet<String>(), HotSport.excludedFromSelected(all, all.toSet()))
        // 往返一致（旧数据 → 选中 → 写回）
        val round = HotSport.excludedFromSelected(all, HotSport.selectedFromExcluded(all, setOf("足球")))
        assertEquals(setOf("足球"), round)
    }

    @Test
    fun `反选只翻转可见项，被搜索隐藏的项保持原状`() {
        // 已选「篮球」，当前可见只有「足球 / 斯诺克」→ 反选后：篮球仍在，足球与斯诺克被选上
        assertEquals(
            setOf("篮球", "足球", "斯诺克"),
            TeamFilter.invert(setOf("篮球"), setOf("足球", "斯诺克")),
        )
        // 可见项里已选的会被取消
        assertEquals(
            setOf("足球"),
            TeamFilter.invert(setOf("篮球", "斯诺克"), setOf("篮球", "斯诺克", "足球")),
        )
        // 空选中 + 全可见 → 全选（结果仍是"显示全部"，但复选框状态不同）
        assertEquals(setOf("篮球", "足球"), TeamFilter.invert(emptySet(), setOf("篮球", "足球")))
        // 全选中 + 全可见 → 清空（= 不筛选）
        assertEquals(emptySet<String>(), TeamFilter.invert(setOf("篮球", "足球"), setOf("篮球", "足球")))
    }

    @Test
    fun `选中集合里的脏数据不会崩`() {
        val sel = setOf("脏数据", TeamFilter.encode("t1", "湖人"))
        val out = TeamFilter.filterDays(days, sel)
        assertTrue(out.isNotEmpty())
        assertEquals(setOf("t1"), TeamFilter.selectedIds(sel))
    }
}