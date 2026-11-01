package com.java.myapplication.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * 1.293：**足球数据（懂球帝 · 官方数据域名）**——积分榜 / 球员榜 / 球队榜。
 *
 * 数据源：`sport-data.dongqiudi.com`（www.dongqiudi.com/data 页面内部用的同一套接口）
 *  · `/soccer/biz/data/standing?competition_id=`       积分榜（服务端自动取当前赛季）
 *  · `/soccer/biz/data/person_ranking?...&type=`       球员榜（13 种指标）
 *  · `/soccer/biz/data/team_ranking?...&type=`         球队榜（5 种指标）
 *
 * 积分榜的 `rounds[]` 有三种轮次形态：
 *  · **FLAT**（联赛）：`content.data` 直接是排名行；
 *  · **GROUP**（杯赛小组 / 分区）：`data[]` 每个元素自己是 {name, header, data}；
 *  · **BRACKET**（淘汰赛）：`data[]` 每个元素是 {TeamA, TeamB, matches[], scores}，
 *    顺序是「决赛 → 半决赛 → 1/4 决赛 → 1/8 决赛」，同一阶段的 `matches[].round_id` 相同。
 *    淘汰赛不是积分表 → 解析成 [DqBracketStage]，由「对阵卡」渲染，不塞进表格。
 */
data class DqCompetition(val id: String, val name: String)

/** 赛事列表（id 来自官方 competition/list 精确匹配） */
val DQ_COMPETITIONS: List<DqCompetition> = listOf(
    // 1.296：前六（中超 + 五大联赛）固定不动，其余按热度排列：
    // 欧战 → 国家队大赛 → 有巨星的联赛 → 亚洲近邻联赛 → 关注度较低者
    DqCompetition("43", "中超"),
    DqCompetition("4", "英超"),
    DqCompetition("3", "西甲"),
    DqCompetition("5", "德甲"),
    DqCompetition("9", "意甲"),
    DqCompetition("12", "法甲"),
    DqCompetition("6", "欧冠"),
    DqCompetition("14", "欧联"),
    DqCompetition("18", "欧洲杯"),
    DqCompetition("262", "美洲杯"),
    DqCompetition("225", "亚洲杯"),
    DqCompetition("258", "世俱杯"),
    DqCompetition("197", "沙特联"),
    DqCompetition("26", "美职联"),
    DqCompetition("2442", "欧国联"),
    DqCompetition("97", "日职联"),
    DqCompetition("117", "K联赛"),
    DqCompetition("351", "非洲杯"),
)

data class DqMetric(val key: String, val label: String)

/** 球员榜指标（顺序即 chip 顺序；官方同一套 type） */
val DQ_PERSON_METRICS: List<DqMetric> = listOf(
    DqMetric("goals", "射手"),
    DqMetric("assists", "助攻"),
    DqMetric("shots", "射门"),
    DqMetric("shots_on_target", "射正"),
    DqMetric("rating", "评分"),
    DqMetric("appearances", "出场"),
    DqMetric("saves", "扑救"),
    DqMetric("tackles", "抢断"),
    DqMetric("interceptions", "拦截"),
    DqMetric("clearances", "解围"),
    DqMetric("fouls", "犯规"),
    DqMetric("yellow_cards", "黄牌"),
    DqMetric("red_cards", "红牌"),
)

/** 球队榜指标 */
val DQ_TEAM_METRICS: List<DqMetric> = listOf(
    DqMetric("goals", "进球"),
    DqMetric("assists", "助攻"),
    DqMetric("shots", "射门"),
    DqMetric("shots_on_target", "射正"),
    DqMetric("possession", "控球"),
)

/** 一场淘汰赛对阵（对阵卡用，不进表格） */
data class DqKnockout(
    val home: String,
    val away: String,
    /** 比分：`2 - 1`；两回合 `2 - 1 / 1 - 3`；点球 `1 - 1 (4:3)` */
    val score: String,
    val homeWin: Boolean = false,
    val awayWin: Boolean = false,
)

/** 一个淘汰赛阶段（八分之一决赛 / 四分之一决赛 / 半决赛 / 决赛…） */
data class DqBracketStage(
    val title: String,
    val matches: List<DqKnockout> = emptyList(),
)

/**
 * 一个榜单 tab 的完整内容：
 *  · [tables] 名次表（联赛 / 小组榜 / 球员榜 / 球队榜）
 *  · [stages] 淘汰赛对阵（杯赛才有），与 tables 一起按顺序展示
 */
data class DqSection(
    val tables: List<NbaRankTable> = emptyList(),
    val stages: List<DqBracketStage> = emptyList(),
)

object DqRankParser {

    private fun JSONArray.toStringList(): List<String> =
        (0 until length()).map { optString(it) }

    /** 积分榜行里，按中文表头标签取单元格文本 */
    private fun standingCell(label: String, row: JSONObject): String = when (label) {
        "赛", "场次", "场" -> row.optString("matches_total")
        "胜" -> row.optString("matches_won")
        "平" -> row.optString("matches_draw")
        "负" -> row.optString("matches_lost")
        "进/失" -> {
            val p = row.optString("goals_pro")
            val a = row.optString("goals_against")
            if (p.isBlank() && a.isBlank()) "" else "$p/$a"
        }
        "净胜", "净胜球" -> {
            val p = row.optString("goals_pro").toIntOrNull()
            val a = row.optString("goals_against").toIntOrNull()
            if (p == null || a == null) "" else (p - a).toString()
        }
        "积分", "分" -> row.optString("points")
        else -> row.optString(label)
    }

    /** 一场比赛（可两回合）的比分文本 */
    private fun matchScore(fsA: String, fsB: String, psA: String, psB: String): String {
        if (fsA.isBlank() && fsB.isBlank()) return ""
        val base = "$fsA - $fsB"
        return if (psA.isNotBlank() && psB.isNotBlank()) "$base ($psA:$psB)" else base
    }

    /** 按「组内场次」推断阶段名（官方只给 round_id，不给阶段名） */
    private fun stageNameOf(count: Int): String = when (count) {
        1 -> "决赛"
        2 -> "半决赛"
        4 -> "四分之一决赛"
        8 -> "八分之一决赛"
        16 -> "十六分之一决赛"
        else -> "淘汰赛"
    }

    /** 积分榜（含淘汰赛分区） */
    fun parseStandingSection(json: String): DqSection {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return DqSection()
        val rounds = root.optJSONObject("content")?.optJSONArray("rounds") ?: return DqSection()
        val tables = ArrayList<NbaRankTable>()
        val stages = ArrayList<DqBracketStage>()

        for (i in 0 until rounds.length()) {
            val round = rounds.optJSONObject(i) ?: continue
            val rc = round.optJSONObject("content") ?: continue
            val roundName = rc.optString("name")
            val data = rc.optJSONArray("data") ?: continue
            if (data.length() == 0) continue
            val first = data.optJSONObject(0) ?: continue

            when {
                // ① 分组表（杯赛小组 / 美职联东西部）
                first.has("data") -> {
                    for (j in 0 until data.length()) {
                        val sub = data.optJSONObject(j) ?: continue
                        val subRows = sub.optJSONArray("data") ?: continue
                        if (subRows.length() == 0) continue
                        val header = (sub.optJSONArray("header") ?: rc.optJSONArray("header"))
                            ?.toStringList() ?: continue
                        val title = listOf(roundName, sub.optString("name"))
                            .filter { it.isNotBlank() }.joinToString(" ")
                        tables += buildStanding(title, header, subRows)
                    }
                }
                // ② 扁平表（联赛 / 欧冠联赛阶段）
                first.has("team_name") || first.has("points") -> {
                    val header = rc.optJSONArray("header")?.toStringList() ?: continue
                    val title = if (roundName == "联赛" || roundName == "常规赛") "" else roundName
                    tables += buildStanding(title, header, data)
                }
                // ③ 淘汰赛 bracket：按 round_id 分阶段
                first.has("TeamA") -> stages += buildStages(data)
                // ④ 单场淘汰赛（三四名决赛）
                first.has("team_A_name") -> {
                    val matches = ArrayList<DqKnockout>()
                    for (j in 0 until data.length()) {
                        val m = data.optJSONObject(j) ?: continue
                        val a = m.optString("team_A_name")
                        val b = m.optString("team_B_name")
                        if (a.isBlank() && b.isBlank()) continue
                        val sa = m.optString("fs_A").toIntOrNull()
                        val sb = m.optString("fs_B").toIntOrNull()
                        matches += DqKnockout(
                            home = a,
                            away = b,
                            score = matchScore(
                                m.optString("fs_A"), m.optString("fs_B"),
                                m.optString("ps_A"), m.optString("ps_B"),
                            ),
                            homeWin = sa != null && sb != null && sa > sb,
                            awayWin = sa != null && sb != null && sb > sa,
                        )
                    }
                    if (matches.isNotEmpty()) {
                        stages += DqBracketStage(roundName.ifBlank { "淘汰赛" }, matches)
                    }
                }
                else -> Unit
            }
        }
        return DqSection(tables = tables, stages = stages)
    }

    /** 淘汰赛节点 → 按 round_id 分阶段（保持接口顺序：决赛在前） */
    private fun buildStages(nodes: JSONArray): List<DqBracketStage> {
        val rids = ArrayList<String>()
        val groups = HashMap<String, MutableList<DqKnockout>>()

        for (i in 0 until nodes.length()) {
            val n = nodes.optJSONObject(i) ?: continue
            val a = n.optJSONObject("TeamA") ?: continue
            val b = n.optJSONObject("TeamB") ?: continue
            val aName = a.optString("name")
            val bName = b.optString("name")
            if (aName.isBlank() && bName.isBlank()) continue

            val matches = n.optJSONArray("matches")
            val parts = ArrayList<String>()
            var rid = ""
            var aTotal = 0
            var bTotal = 0
            var aPen = 0
            var bPen = 0
            var hasPen = false
            if (matches != null) {
                for (k in 0 until matches.length()) {
                    val m = matches.optJSONObject(k) ?: continue
                    if (rid.isBlank()) rid = m.optString("round_id")
                    val sa = m.optString("fs_A")
                    val sb = m.optString("fs_B")
                    val pa = m.optString("ps_A")
                    val pb = m.optString("ps_B")
                    val sc = matchScore(sa, sb, pa, pb)
                    if (sc.isNotBlank()) parts += sc
                    aTotal += sa.toIntOrNull() ?: 0
                    bTotal += sb.toIntOrNull() ?: 0
                    if (pa.isNotBlank() && pb.isNotBlank()) {
                        hasPen = true
                        aPen += pa.toIntOrNull() ?: 0
                        bPen += pb.toIntOrNull() ?: 0
                    }
                }
            }
            // 胜者：先看总比分；打平则看点球；再不行看 total_score
            var homeWin = aTotal > bTotal
            var awayWin = bTotal > aTotal
            if (!homeWin && !awayWin && hasPen) {
                homeWin = aPen > bPen
                awayWin = bPen > aPen
            }
            if (!homeWin && !awayWin) {
                val ts = n.optString("total_score")
                if (ts.contains("-")) {
                    val nums = ts.split("-").map { it.trim().toIntOrNull() ?: 0 }
                    if (nums.size == 2) {
                        homeWin = nums[0] > nums[1]
                        awayWin = nums[1] > nums[0]
                    }
                }
            }
            val key = rid.ifBlank { "i$i" }
            if (key !in groups) {
                rids += key
                groups[key] = ArrayList()
            }
            groups.getValue(key) += DqKnockout(
                home = aName,
                away = bName,
                score = parts.joinToString(" / "),
                homeWin = homeWin,
                awayWin = awayWin,
            )
        }
        return rids.map { rid ->
            val ms = groups.getValue(rid)
            DqBracketStage(stageNameOf(ms.size), ms)
        }
    }

    private fun buildStanding(title: String, header: List<String>, rows: JSONArray): NbaRankTable {
        val headers = ArrayList<String>(header.size + 1)
        headers += "排名"
        headers += header
        val body = ArrayList<List<String>>(rows.length())
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            val cells = ArrayList<String>(header.size + 1)
            cells += row.optString("rank")
            if (header.isNotEmpty()) cells += row.optString("team_name")
            for (k in 1 until header.size) cells += standingCell(header[k], row)
            body += cells
        }
        return NbaRankTable(title = title, headers = headers, rows = body)
    }

    /** 兼容入口：只要名次表（淘汰赛阶段用 [parseStandingSection]） */
    fun parseStanding(json: String): List<NbaRankTable> = parseStandingSection(json).tables

    /** 球员榜：一张表（列：排名 / 球员 / 球队 / 指标值） */
    fun parsePersonRanking(json: String): List<NbaRankTable> {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        val content = root.optJSONObject("content") ?: return emptyList()
        val data = content.optJSONArray("data") ?: return emptyList()
        if (data.length() == 0) return emptyList()
        val apiHeader = content.optJSONArray("header")?.toStringList().orEmpty()
        val valueLabel = apiHeader.lastOrNull()?.takeIf { it.isNotBlank() } ?: "总计"
        val headers = listOf("排名", "球员", "球队", valueLabel)
        val rows = ArrayList<List<String>>(data.length())
        for (i in 0 until data.length()) {
            val row = data.optJSONObject(i) ?: continue
            rows += listOf(
                row.optString("rank"),
                row.optString("person_name"),
                row.optString("team_name"),
                row.optString("count"),
            )
        }
        return listOf(NbaRankTable(title = "", headers = headers, rows = rows))
    }

    /** 球队榜：一张表（列：排名 / 球队 / 指标值） */
    fun parseTeamRanking(json: String): List<NbaRankTable> {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        val content = root.optJSONObject("content") ?: return emptyList()
        val data = content.optJSONArray("data") ?: return emptyList()
        if (data.length() == 0) return emptyList()
        val header = content.optJSONArray("header")?.toStringList() ?: listOf("球队", "总计")
        val valueLabel = header.lastOrNull() ?: "总计"
        val rows = ArrayList<List<String>>(data.length())
        for (i in 0 until data.length()) {
            val row = data.optJSONObject(i) ?: continue
            rows += listOf(row.optString("rank"), row.optString("team_name"), row.optString("count"))
        }
        return listOf(NbaRankTable(title = "", headers = listOf("排名", "球队", valueLabel), rows = rows))
    }
}

/**
 * 数据源：**sport-data.dongqiudi.com**（官方数据域名，直连）。
 *
 * 参数与 www.dongqiudi.com/data 页面内部使用的一致（`app=dqd&version=853`），
 * 实测：直连 ~440ms vs 旧代理 ~2900ms；返回 JSON 结构完全相同。
 * `competition_id` 直接可用（服务端自动取当前赛季），不需要先查 season_id。
 */
object DqRankApi {
    private const val SUFFIX =
        "&app=dqd&version=853&platform=ios&language=zh-cn&app_type="

    suspend fun standing(competitionId: String): String? = HupuApi.fetchDongqiuJson(
        "/soccer/biz/data/standing?competition_id=$competitionId$SUFFIX",
    )

    suspend fun personRanking(competitionId: String, metric: String): String? =
        HupuApi.fetchDongqiuJson(
            "/soccer/biz/data/person_ranking?competition_id=$competitionId&type=$metric$SUFFIX",
        )

    suspend fun teamRanking(competitionId: String, metric: String): String? =
        HupuApi.fetchDongqiuJson(
            "/soccer/biz/data/team_ranking?competition_id=$competitionId&type=$metric$SUFFIX",
        )
}

/** 10 分钟内存缓存（页面切换赛事/指标时优先命中，减少请求也避开限流） */
object DqRankCache {
    private const val TTL_MS = 10L * 60 * 1000
    private val map = ConcurrentHashMap<String, Pair<Long, DqSection>>()

    fun get(key: String): DqSection? {
        val hit = map[key] ?: return null
        if (System.currentTimeMillis() - hit.first > TTL_MS) {
            map.remove(key)
            return null
        }
        return hit.second
    }

    fun put(key: String, section: DqSection) {
        if (section.tables.isEmpty() && section.stages.isEmpty()) return
        map[key] = System.currentTimeMillis() to section
    }
}

/**
 * **磁盘缓存**（原始 JSON，6 小时）。
 *
 * 冷启动第一次进某个 tab 总要等一次网络；把原始 JSON 落到 cacheDir 后，
 * 下次进来**立刻有内容**（旧数据先渲染），同时后台刷新覆盖 —— 观感是「秒开」。
 */
object DqDiskCache {
    private const val TTL_MS = 6L * 60 * 60 * 1000
    @Volatile
    private var dir: java.io.File? = null

    fun init(d: java.io.File) {
        dir = d.also { runCatching { it.mkdirs() } }
    }

    private fun fileOf(key: String): java.io.File? {
        val base = dir ?: return null
        val safe = key.replace(Regex("[^A-Za-z0-9_.-]"), "_")
        return java.io.File(base, "dq_$safe.json")
    }

    fun read(key: String): String? {
        val f = fileOf(key) ?: return null
        if (!f.exists()) return null
        if (System.currentTimeMillis() - f.lastModified() > TTL_MS) return null
        return runCatching { f.readText() }.getOrNull()
    }

    fun write(key: String, json: String) {
        val f = fileOf(key) ?: return
        runCatching { f.writeText(json) }
    }
}