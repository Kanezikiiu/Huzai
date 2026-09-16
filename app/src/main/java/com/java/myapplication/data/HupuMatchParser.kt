package com.java.myapplication.data

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 比赛评分 JSON 解析（match-api.hupu.com getScheduleListByTagForH5 响应）
 * 结构：result.dayGameData[] -> matchData[]，字段实测见 docs/hupu_web_probe.md
 */
object HupuMatchParser {

    /** 解析赛程（按日分组）。失败/空数据返回 emptyList()。 */
    fun parseSchedule(json: String): List<HupuMatchDay> {
        return try {
            val root = JSONObject(json)
            if (!root.optBoolean("success", false)) return emptyList()
            val daysArr = root.optJSONObject("result")?.optJSONArray("dayGameData") ?: return emptyList()
            val out = mutableListOf<HupuMatchDay>()
            for (i in 0 until daysArr.length()) {
                val day = daysArr.optJSONObject(i) ?: continue
                val matchesArr = day.optJSONArray("matchData") ?: continue
                val matches = mutableListOf<HupuMatch>()
                for (j in 0 until matchesArr.length()) {
                    val m = matchesArr.optJSONObject(j) ?: continue
                    matchFrom(m)?.let { matches += it }
                }
                if (matches.isNotEmpty()) {
                    out += HupuMatchDay(
                        dayTime = day.optString("dayTime"),
                        dateBlock = day.optString("dateBlock"),
                        matches = matches,
                    )
                }
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun matchFrom(o: JSONObject): HupuMatch? {
        val matchId = o.optString("matchId")
        if (matchId.isEmpty() || matchId == "0") return null
        val members = o.optJSONObject("againstInfo")?.optJSONArray("memberInfos")
        val home = members?.optJSONObject(0)?.let { teamFrom(it) }
        val away = if (members != null && members.length() > 1) members.optJSONObject(1)?.let { teamFrom(it) } else null
        val ts = o.optString("matchStartTimeStamp").toLongOrNull() ?: 0L
        return HupuMatch(
            matchId = matchId,
            statusDesc = o.optString("matchStatusDesc"),
            status = o.optString("matchStatus"),
            introduction = o.optString("matchIntroduction"),
            matchName = o.optString("matchName"),
            startTimeText = formatTime(ts),
            startTimestamp = ts,
            scoreCountText = o.optString("scoreCountText"),
            home = home,
            away = away,
            winnerMemberId = o.optJSONObject("againstInfo")?.optString("winnerMemberId")?.ifEmpty { null },
            playerScore = o.optJSONObject("scoreItemInfo")?.let { s ->
                val name = s.optString("name")
                if (name.isEmpty()) null else HupuPlayerScore(
                    name = name,
                    logo = s.optString("logo").ifEmpty { null },
                    teamLogo = s.optString("teamLogo").ifEmpty { null },
                    scoreNum = s.optString("scoreNum"),
                    scoreCountText = s.optString("scoreCountText"),
                    hotComment = s.optString("hotComment"),
                )
            },
            scoreBizType = o.optJSONObject("scoreItemKey")?.optString("outBizType")?.ifEmpty { null },
            scoreBizNo = o.optJSONObject("scoreItemKey")?.optString("outBizNo")?.ifEmpty { null },
        )
    }

        /**
     * 解析评分树（bplcommentapi getCurAndSubNodeByBizKey?relation=CHILD 响应）。
     * 结构：data.self.node（比赛）+ data.pageResult.data[].node（对局）+ .subNodes[].node（选手）。
     * 对局列表 API 返回倒序（data[0]=最后一局），统一翻转为正序。
     * 失败/无子节点返回 null。
     */
    fun parseScoreTree(json: String): HupuScoreTree? {
        return try {
            val root = JSONObject(json)
            if (!root.optBoolean("success", false) && root.optString("status") != "1" && root.optInt("code", -1) !in 0..1) {
                // 兼容多种响应外壳；任一标识通过即可
                if (root.opt("data") == JSONObject.NULL || root.optJSONObject("data") == null) return null
            }
            val data = root.optJSONObject("data") ?: return null
            val selfNode = data.optJSONObject("self")?.optJSONObject("node")
                ?: data.optJSONObject("pageResult")?.optJSONArray("data")?.optJSONObject(0)?.optJSONObject("node")
                ?: return null

            val roundsJson = data.optJSONObject("pageResult")?.optJSONArray("data")
            val rounds = mutableListOf<HupuScoreRound>()
            var flat = false
            if (roundsJson != null) {
                for (i in 0 until roundsJson.length()) {
                    val item = roundsJson.optJSONObject(i) ?: continue
                    val n = item.optJSONObject("node") ?: continue
                    val players = mutableListOf<HupuScoreItem>()
                    item.optJSONArray("subNodes")?.let { subs ->
                        for (j in 0 until subs.length()) {
                            val sn = subs.optJSONObject(j)?.optJSONObject("node") ?: continue
                            players += playerFrom(sn)
                        }
                    }
                    rounds += HupuScoreRound(
                        bizId = n.optString("bizId"),
                        name = n.optString("name"),
                        scorePersonCount = n.optLong("summedScorePersonCount", n.optLong("scorePersonCount", 0L)),
                        players = players,
                    )
                }
            }
            if (rounds.isEmpty()) return null
            // 结构分型：MOBA 是 比赛→对局(bizType 如 lol_bo，subNodes=选手) 三层；
            // 传统体育(NBA/WNBA/CUBA/奥运会等)的 children 直接就是选手节点
            // （bizType 以 _item/_second 结尾、infoJson.type=player、无 subNodes）→ 打 flat 标记。
            // 注意：CS2 是 比赛→地图(_second，带 subNodes=选手) 三层，后缀虽命中 _second
            // 但并非叶子——判定必须校验 children 是否真为叶子，否则会误判 flat 导致无法下钻。
            fun isLeafPlayer(item: JSONObject): Boolean {
                val n = item.optJSONObject("node") ?: return false
                val t = n.optString("bizType")
                if (t.endsWith("_item")) return true
                if (t.endsWith("_second") || t.endsWith("_third")) {
                    // 叶子判定只看是否真的无子节点。不能要求 infoJson.type=player：
                    // 教练/裁判/解说等中立角色同样直列在扁平列表里但没有 type 字段
                    // （误判会把整场比赛渲染成几十个"对局"），CS2 地图则必带 subNodes/subNodeCount>0
                    val hasChildren = item.optJSONArray("subNodes")?.length() ?: 0 > 0
                    val nodeCount = item.optLong("subNodeCount", n.optLong("subNodeCount", 0L))
                    return !hasChildren && nodeCount <= 0L
                }
                return false
            }
            val childItems = roundsJson?.let { r -> (0 until r.length()).mapNotNull { r.optJSONObject(it) } } ?: emptyList()
            if (childItems.isNotEmpty() && childItems.all { isLeafPlayer(it) }) {
                flat = true
                // 合成单一虚拟轮：children 直接映射为选手列表
                val players = roundsJson!!.let { r ->
                    (0 until r.length()).mapNotNull { r.optJSONObject(it)?.optJSONObject("node")?.let { n -> playerFrom(n) } }
                }
                rounds.clear()
                rounds += HupuScoreRound(bizId = "flat", name = "", scorePersonCount = 0L, players = players)
            }
            HupuScoreTree(
                name = selfNode.optString("name"),
                image = selfNode.optJSONArray("image")?.optString(0)?.ifEmpty { null },
                scorePersonCount = selfNode.optLong("summedScorePersonCount", selfNode.optLong("scorePersonCount", 0L)),
                rounds = rounds.reversed(), // API 倒序（data[0]=最后一局）→ 正序
                flat = flat,
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun playerFrom(n: JSONObject): HupuScoreItem = HupuScoreItem(
        bizId = n.optString("bizId"),
        bizType = n.optString("bizType").ifEmpty { "lol_item" },
        name = n.optString("name"),
        image = n.optJSONArray("image")?.optString(0)?.ifEmpty { null },
        scoreAvg = formatAvg(n.optDouble("scoreAvg", 0.0)),
        scorePersonCount = n.optLong("scorePersonCount", 0L),
        hotComment = n.optJSONArray("hotCommentModels")
            ?.optJSONObject(0)?.optString("commentContent")?.ifEmpty { null }
            ?: "",
        teamId = n.optJSONObject("infoJson")?.optJSONArray("teamId")
            ?.optString(0)?.ifEmpty { null },
        // 所选英雄/角色小图标（MOBA 有值，教练/中立角色为 null）
        heroIcon = n.optJSONObject("infoJson")?.optJSONArray("auxiliaryPic")
            ?.optString(0)?.ifEmpty { null },
    )

    /**
     * 解析节点详情（getSelfByBizKey 响应，第 4 层选手/对局）。
     * 注意：下层节点响应外壳是 data.detail（不是 CHILD 树的 data.self.node）。
     */
    fun parseSelfDetail(json: String): HupuSelfDetail? {
        return try {
            val root = JSONObject(json)
            val det = root.optJSONObject("data")?.optJSONObject("detail") ?: return null
            val name = det.optString("name")
            if (name.isEmpty()) return null

            // 评分分布：key=分数档（"2"/"4"/…/"10"），value=人数；降序（10 分在前）
            val dist = mutableListOf<Pair<String, Long>>()
            det.optJSONObject("scoreDistribution")?.let { d ->
                val keys = mutableListOf<String>()
                for (k in d.keys()) keys += k
                keys.sortedByDescending { it.toDoubleOrNull() ?: 0.0 }.forEach { k ->
                    dist += k to d.optLong(k, 0L)
                }
            }

            // 热评：hottestComments（字符串数组）
            val hot = mutableListOf<String>()
            det.optJSONArray("hottestComments")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val s = arr.optString(i)
                    if (s.isNotBlank()) hot += s
                }
            }

            // infoJson.desc[0]：电竞选手层是 KDA 短串（"K/D/A:1/3/20"）；通用评分层是评分对象介绍长文。
            // 按 KDA 特征分流：含 "K/D/A" 或短串（≤15 字符）→ kda（顶栏副行）；否则 → intro（内容区介绍卡）
            val desc0 = det.optJSONObject("infoJson")?.optJSONArray("desc")?.optString(0)?.trim()?.ifEmpty { "" } ?: ""
            val isKda = desc0.contains("K/D/A") || desc0.length <= 15
            val kda = if (isKda) desc0 else ""
            val intro = if (!isKda) desc0 else ""

            HupuSelfDetail(
                bizType = det.optString("bizType").ifEmpty { "lol_item" },
                bizId = det.optString("bizId"),
                name = name,
                image = det.optJSONArray("image")?.optString(0)?.ifEmpty { null },
                scoreAvg = formatAvg(det.optDouble("scoreAvg", 0.0)),
                scorePersonCount = det.optLong("scorePersonCount", 0L),
                kda = kda,
                intro = intro,
                heroIcon = det.optJSONObject("infoJson")?.optJSONArray("auxiliaryPic")
                    ?.optString(0)?.ifEmpty { null },
                distribution = dist,
                hottestComments = hot,
                userScore = det.optInt("userScore", 0),
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 单条评分评论（含楼中楼：subCommentList 内嵌子回复，匿名可见；仅前 1~3 条）。
     * 防护：optString 对 JSON null 返回 "null" 字面量——userHead/userName 判空需先 clean。
     */
    private fun parseScoreComment(c: JSONObject): HupuScoreComment {
        fun clean(s: String): String = if (s == "null") "" else s
        val subs = mutableListOf<HupuScoreComment>()
        val subArr = c.optJSONArray("subCommentList")
        if (subArr != null) {
            for (k in 0 until subArr.length()) {
                val s = subArr.optJSONObject(k) ?: continue
                val sc = s.optString("commentContent")
                if (sc.isEmpty() || sc == "null") continue
                // 递归：子回复自己的 subCommentList 是「回复的回复」（孙评论，descendantCount>0 时内嵌），
                // 官方楼中楼将其缩进挂在该子回复下方——解析层保留完整层级，渲染层决定展示深度
                subs += parseScoreComment(s)
            }
        }
        // 附件图：commentContentImages[].commentContent 为 URL（commentContentType=IMAGE）
        val imgs = c.optJSONArray("commentContentImages")?.let { arr ->
            (0 until arr.length()).mapNotNull { k ->
                arr.optJSONObject(k)?.optString("commentContent")?.takeIf { it.isNotBlank() && it != "null" }
            }
        } ?: emptyList()
        return HupuScoreComment(
            commentId = clean(c.optString("commentId")),
            userName = clean(c.optString("commentUserName")).ifEmpty { "虎扑JR" },
            userHead = clean(c.optString("commentUserHeadImg")).ifEmpty { null },
            // 1.163: 评论作者数字 id（点作者头像/昵称进用户主页用；官方 JSON 字段 commentUserId）
            userId = clean(c.optString("commentUserId")),
            content = clean(c.optString("commentContent")),
            images = imgs,
            score = c.optInt("score", 0),
            lightCount = c.optLong("lightCount", 0L),
            date = clean(c.optString("commentDate")),
            ipLocation = clean(c.optString("ipLocation")),
            subCommentCount = c.optInt("subCommentCount", 0),
            descendantCount = c.optInt("descendantCount", 0),
            parentCommentId = clean(c.optString("parentCommentId")),
            subjectId = (c.optJSONObject("commentKey")?.optString("subjectId") ?: "")
                .ifEmpty { c.optString("subjectId") }
                .let { clean(it) },
            hasLight = c.optBoolean("hasLight", false),
            subComments = subs,
        )
    }

    /**
     * 解析评论列表（primarySingleRow 响应）。
     * 返回 null=失败；返回 ScoreCommentState（comments/commentCount/cursor/hasMore）。
     */
    fun parseComments(json: String): ScoreCommentState? {
        return try {
            val root = JSONObject(json)
            val data = root.optJSONObject("data") ?: return null
            val arr = data.optJSONArray("comments") ?: return null
            val out = mutableListOf<HupuScoreComment>()
            for (i in 0 until arr.length()) {
                val c = arr.optJSONObject(i) ?: continue
                val content = c.optString("commentContent")
                if (content.isEmpty() && c.optJSONArray("commentContentImages") == null) continue
                out += parseScoreComment(c)
            }
            ScoreCommentState(
                comments = out,
                commentCount = data.optLong("commentCount", 0L),
                cursor = data.optJSONObject("cursor")?.optLong("publishTime", 0L) ?: 0L,
                hasMore = data.optBoolean("hasMore", false) && out.isNotEmpty(),
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 解析最亮评论（primarySingleRow/hottest 响应：data 直接是评论数组，无分页）。
     */
    fun parseHottestComments(json: String): List<HupuScoreComment> {
        return try {
            val root = JSONObject(json)
            val arr = root.optJSONArray("data") ?: return emptyList()
            val out = mutableListOf<HupuScoreComment>()
            for (i in 0 until arr.length()) {
                val c = arr.optJSONObject(i) ?: continue
                if (c.optString("commentContent").isEmpty() && c.optJSONArray("commentContentImages") == null) continue
                out += parseScoreComment(c)
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 解析楼中楼全量子评论（subCommentList / getMore 响应：结构与主列表一致）。
     * 返回 null=失败；返回 ScoreCommentState（cursor=下一页 publishTime，hasMore）。
     */
    fun parseSubComments(json: String): ScoreCommentState? {
        return try {
            val root = JSONObject(json)
            val code = root.optInt("code", 1)
            // 1.150 关键修复：母评论还没有任何子回复时，接口返回
            //   {"code":1,"type":"COMMON","msg":"成功","data":null}
            // 以前 data==null 直接 return null → 上层当成网络失败 → 楼中楼显示「加载失败」。
            // 实测（lol_match/3711、common_second/176621 上 0 子回复的母评论，
            // queryType/publishTime 四种组合都是 data:null）。现按「成功但空」处理：
            // 只有显式带 code 且非成功码才算失败（没有 code 字段的老样本照旧解析）。
            if (root.has("code") && code != 1 && code != 200) return null
            val data = root.optJSONObject("data")
                ?: return ScoreCommentState(emptyList(), 0L, 0L, false)
            val arr = data.optJSONArray("comments")
                ?: return ScoreCommentState(
                    comments = emptyList(),
                    commentCount = data.optLong("commentCount", 0L),
                    cursor = data.optJSONObject("cursor")?.optLong("publishTime", 0L) ?: 0L,
                    hasMore = false,
                )
            val out = mutableListOf<HupuScoreComment>()
            for (i in 0 until arr.length()) {
                val c = arr.optJSONObject(i) ?: continue
                if (c.optString("commentContent").isEmpty() && c.optJSONArray("commentContentImages") == null) continue
                out += parseScoreComment(c)
            }
            ScoreCommentState(
                comments = out,
                commentCount = data.optLong("commentCount", 0L),
                cursor = data.optJSONObject("cursor")?.optLong("publishTime", 0L) ?: 0L,
                hasMore = data.optBoolean("hasMore", false) && out.isNotEmpty(),
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun formatAvg(v: Double): String =
        if (v <= 0.0) "-" else if (v == v.toLong().toDouble()) v.toLong().toString() else String.format(Locale.CHINA, "%.1f", v)

    private fun teamFrom(o: JSONObject): HupuMatchTeam? {
        // 空占位过滤：和平精英/绝地求生等多队赛事的 againstInfo.memberInfos 是
        // 两个全空占位（memberName/memberId/logo 全空）——这不是 1v1 对局，转 null，
        // 让赛程卡走「赛事名卡」形态而不是渲染两个空白队伍槽
        val name = o.optString("memberName")
        val id = o.optString("memberId")
        // Android org.json 的 optString 会把 JSON null 强转成字符串 "null" 返回，
        // 多队赛事（和平精英/绝地求生/奥运团体赛等）占位成员渲染出四个 "null" 字样的根因；
        // "null" 一律按空处理，名字为空即视为占位 → 丢弃，赛程卡走「赛事名卡」形态
        fun clean(s: String): String = if (s == "null") "" else s
        if (clean(name).isBlank()) return null
        return HupuMatchTeam(
            memberId = clean(id),
            name = clean(name),
            logo = o.optString("memberLogo").takeUnless { it.isEmpty() || it == "null" },
            baseScore = clean(o.optString("memberBaseScore")),
            extraScore = o.optString("memberExtraScore").takeUnless { it.isEmpty() || it == "null" },
            bigScore = o.optString("memberBigScore").takeUnless { it.isEmpty() || it == "null" },
        )
    }

    private fun formatTime(ts: Long): String {
        if (ts <= 0) return ""
        return try {
            SimpleDateFormat("MM月dd日 HH:mm", Locale.CHINA).format(Date(ts))
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * 解析服务端分组定义（getSubGroups 响应）。
     * 每项：groupId/sort/groupName/rootNodeId/childCount/groupAttributes(logo)。
     * 失败/无分组返回空列表。
     */
    fun parseSubGroups(json: String): List<HupuScoreGroup> = try {
        val root = JSONObject(json)
        if (!root.optBoolean("success", false)) return emptyList()
        val arr = root.optJSONArray("data") ?: return emptyList()
        (0 until arr.length()).mapNotNull { i ->
            val g = arr.optJSONObject(i) ?: return@mapNotNull null
            val logo = g.optJSONArray("groupAttributes")?.let { attrs ->
                (0 until attrs.length()).firstOrNull { attrs.optJSONObject(it)?.optString("attributeKey") == "logo" }
                    ?.let { attrs.optJSONObject(it)?.optString("attributeValue") }
            }
            HupuScoreGroup(
                groupId = g.optLong("groupId", 0L),
                name = g.optString("groupName"),
                logo = logo?.takeIf { it.isNotBlank() },
                rootNodeId = g.optLong("rootNodeId", 0L),
                childCount = g.optInt("childCount", 0),
            )
        }.filter { it.rootNodeId > 0 && it.name.isNotBlank() }
    } catch (e: Exception) {
        emptyList()
    }

    /**
     * 解析分组成员节点（groupAndSubNodes 响应 data.nodePageResult.data[]，结构与树 children 一致）。
     */
    fun parseGroupNodes(json: String): List<HupuScoreItem> = try {
        val root = JSONObject(json)
        if (!root.optBoolean("success", false)) return emptyList()
        val arr = root.optJSONObject("data")?.optJSONObject("nodePageResult")?.optJSONArray("data")
            ?: return emptyList()
        (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.optJSONObject("node")?.let { playerFrom(it) }
        }
    } catch (e: Exception) {
        emptyList()
    }
}