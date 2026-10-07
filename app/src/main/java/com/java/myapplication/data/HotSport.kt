package com.java.myapplication.data

/**
 * 1.199：**热门赛事**（通用热门体育聚合）里的「项目归类」。
 *
 * 官方数据**没有项目字段** —— 实测该接口返回的全部字段只有自由文本
 * [HupuMatch.introduction] / [HupuMatch.matchName] 能反映项目（如「斯诺克英格兰公开赛半决赛」、
 * 「WTA中网女单」），因此只能由客户端按关键词归类。
 *
 * ⚠️ 表是**有序**的、先具体后宽泛 —— 这里踩过一个坑：
 * 「WTT中国大满贯女单」曾被网球规则里的 `大满贯` 抢先匹配成网球（乒乓球场次少了一半），
 * 所以乒乓球/斯诺克/羽毛球必须排在网球之前，且网球只用 `WTA|ATP|中网|大师赛` 这类精确词。
 */
object HotSport {

    /** 有序关键词表：命中即止；全部不中 → [OTHER] */
    private val TABLE: List<Pair<String, Regex>> = listOf(
        "斯诺克" to Regex("斯诺克|台球"),
        "乒乓球" to Regex("乒乓球|WTT|乒超|国乒"),
        "羽毛球" to Regex("羽毛球|BWF|汤姆斯|尤伯|苏迪曼|汤尤杯"),
        "排球" to Regex("排球|女排|男排|VNL"),
        "网球" to Regex("网球|WTA|ATP|中网|大师赛|比利-简-金杯|温网|法网|澳网|美网"),
        "赛车/F1" to Regex("F1|一级方程式|赛车|拉力|摩托"),
        "三人篮球" to Regex("三人篮球"),
        "篮球" to Regex("篮球|NBA|CBA|WNBA|CUBA"),
        "足球" to Regex("足球|英超|西甲|意甲|德甲|法甲|欧冠|亚冠|世界杯|中超"),
        "电子竞技" to Regex("王者荣耀|英雄联盟|电竞|KPL|LPL|LCK|亚运会"),
        "武术" to Regex("武术|散打|太极"),
        "综合格斗" to Regex("UFC|格斗|拳击|MMA"),
        "体操" to Regex("体操|蹦床"),
        "棒球" to Regex("棒球|垒球"),
        "曲棍球" to Regex("曲棍球|手球|水球|橄榄球"),
        "田径" to Regex("田径|马拉松|竞走"),
        "游泳/跳水" to Regex("游泳|跳水"),
        "冰雪" to Regex("滑雪|滑冰|冰球|冰壶|短道"),
        "射击/射箭" to Regex("射击|射箭|飞碟"),
        "重竞技" to Regex("举重|摔跤|柔道|跆拳道|击剑"),
    )

    /** 未命中任何关键词的兜底类别 */
    const val OTHER = "其他"

    /** 归类：命中第一条规则即返回；全不中 → [OTHER] */
    fun of(introduction: String, matchName: String = ""): String {
        val text = introduction + " " + matchName
        for ((name, re) in TABLE) {
            if (re.containsMatchIn(text)) return name
        }
        return OTHER
    }

    /** 归类（便捷重载） */
    fun of(match: HupuMatch): String = of(match.introduction, match.matchName)

    /**
     * 当前数据里各项目的场次数（只列出实际出现的类别）。
     * 排序：按 [TABLE] 的权重顺序，「其他」垫底 —— 弹窗里从这里渲染 chips 与计数。
     */
    fun counts(days: List<HupuMatchDay>): List<Pair<String, Int>> {
        val counter = LinkedHashMap<String, Int>()
        for (d in days) {
            for (m in d.matches) {
                val name = of(m)
                counter[name] = (counter[name] ?: 0) + 1
            }
        }
        val order = TABLE.map { it.first } + OTHER
        return counter.entries
            .sortedBy { e -> order.indexOf(e.key).let { if (it < 0) order.size else it } }
            .map { it.key to it.value }
    }

    /**
     * 按「被排除的类别」过滤赛程。
     * · [excluded] 为空 → 原样返回（默认全显示）；
     * · 过滤后没有比赛的日子会被丢掉 —— 否则列表里会出现空日期块，
     *   而且 [com.java.myapplication.ui.pages.ScheduleList] 的「定位到最近比赛日」也会落到空日子上。
     */
    fun filterDays(days: List<HupuMatchDay>, excluded: Set<String>): List<HupuMatchDay> {
        if (excluded.isEmpty()) return days
        return days.mapNotNull { d ->
            val kept = d.matches.filterNot { of(it) in excluded }
            if (kept.isEmpty()) null else d.copy(matches = kept)
        }
    }
}