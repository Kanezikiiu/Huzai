package com.java.myapplication.data

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 虎扑比赛评分 API（match-api.hupu.com）
 * 来源：官方 H5 赛程评分页 team-schedule-v2 使用的接口，匿名 GET 可用（实测）
 * 注意：参与打分需登录，本客户端只做浏览。
 */
object HupuMatchApi {

    private const val BASE = "https://match-api.hupu.com"
    private const val SCHEDULE_PATH = "/1/8.2.10/matchallapi/bff/standard/getScheduleListByTagForH5"

    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    /** 6 大电竞项目（businessId 与官方 H5 评分页一致，全部实测匿名可用） */
    val GAMES = listOf(
        "lol" to "英雄联盟",
        "kog" to "王者荣耀",
        "val" to "无畏契约",
        "cs2" to "CS2",
        "pubgmobile" to "和平精英",
        "pubg" to "绝地求生",
        // 扩展赛事（同端点换 businessId，2026-09 批量探测确认；下钻 outBizType 各异，parser 透传通用）
        "nba" to "NBA",
        "cba" to "CBA",
        "wnba" to "WNBA",
        "lpl" to "LPL",
        "lck" to "LCK",
        "epl" to "英超",
        "worldcup" to "世界杯",
        "cuba" to "CUBA",
        "olympics" to "奥运会",
        "tabletennis" to "乒乓球",
        "tennis" to "网球",
        "badminton" to "羽毛球",
    )

    // 1.169: 共享 client（HupuHttp）——连接池/线程池复用
    private val client: OkHttpClient = HupuHttp.client

    /** 拉取某项目的赛程评分（JSON 文本）。失败返回 null。 */
    /**
     * 响应体可用性判定（1.151）。
     * 旧实现用「字节长度」猜有效性（`json.length < 100`），但**合法的「成功但空」响应很短**：
     * 母评论还没有子回复时 subCommentList 返回
     * `{"code":1,"type":"COMMON","msg":"成功","data":null,"success":true}`（约 64 字节），
     * 被长度守卫拦掉 → 返回 null → 上层当网络失败 → 楼中楼/评论列表显示「加载失败」。
     * 现改为语义判定：必须是非空、可解析的 JSON 对象/数组（HTML 错误页/空体/截断体仍被拒）。
     */
    internal fun isJsonBody(body: String?): Boolean {
        val t = body?.trim() ?: return false
        if (t.isEmpty()) return false
        if (!t.startsWith("{") && !t.startsWith("[")) return false
        return runCatching { org.json.JSONTokener(t).nextValue() }.isSuccess
    }

    suspend fun fetchSchedule(businessId: String, forceNetwork: Boolean = false): String? {
        val cacheKey = "match-schedule-$businessId"
        return withContext(Dispatchers.IO) {
            if (!forceNetwork) {
                HupuCache.get(cacheKey)?.let { return@withContext it }
            }
            val url = "$BASE$SCHEDULE_PATH?businessType=common&datasource=navigation&businessId=$businessId"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Accept", "application/json, text/plain, */*")
                .header("Referer", "https://bbs.hupu.com/")
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val json = response.body?.string() ?: return@use null
                    if (!isJsonBody(json)) return@use null
                    HupuCache.put(cacheKey, json)
                    json
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * 拉取节点详情（getSelfByBizKey，第 4 层选手/对局，匿名可用）。
     */
    suspend fun fetchSelf(bizType: String, bizNo: String, forceNetwork: Boolean = false): String? {
        // 1.64：登录态读 detail.userScore 回显「我的评分」——缓存按登录态分键（L 后缀），
        // 未登录读匿名主键，互不污染；匿名请求不带空 Cookie 头
        val logged = HupuAccount.isLoggedIn
        val baseKey = "score-self-$bizType-$bizNo"
        val cacheKey = if (logged) "$baseKey-L" else baseKey
        val cookie = if (logged) HupuAccount.loadCookieHeaderPublic() else ""
        return withContext(Dispatchers.IO) {
            if (!forceNetwork) {
                HupuCache.get(cacheKey)?.let { return@withContext it }
            }
            val url = "https://games.mobileapi.hupu.com/1/8.0.99/bplcommentapi/bpl/score_tree/" +
                "getSelfByBizKey?outBizType=$bizType&outBizNo=$bizNo"
            val builder = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Accept", "application/json, text/plain, */*")
                .header("Referer", "https://m.hupu.com/")
            if (cookie.isNotEmpty()) builder.header("Cookie", cookie)
            val request = builder.build()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val json = response.body?.string() ?: return@use null
                    if (!isJsonBody(json)) return@use null
                    HupuCache.put(cacheKey, json)
                    json
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * 拉取通用评分主题树（getCurAndSubNodeByBizKey CHILD，common_first/common_second，匿名可用）。
     * 树分页参数 page/pageSize（pageSize 固定 20）。缓存键带页码。
     */
    suspend fun fetchCommonTree(bizType: String, bizNo: String, page: Int, forceNetwork: Boolean = false): String? {
        val cacheKey = "common-tree-$bizType-$bizNo-$page"
        return withContext(Dispatchers.IO) {
            if (!forceNetwork) {
                HupuCache.get(cacheKey)?.let { return@withContext it }
            }
            val url = "https://games.mobileapi.hupu.com/1/8.0.99/bplcommentapi/bpl/score_tree/" +
                "getCurAndSubNodeByBizKey?outBizType=$bizType&outBizNo=$bizNo&relation=CHILD" +
                "&page=$page&pageSize=20"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Accept", "application/json, text/plain, */*")
                .header("Referer", "https://m.hupu.com/score/detail.html")
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val json = response.body?.string() ?: return@use null
                    if (!isJsonBody(json)) return@use null
                    HupuCache.put(cacheKey, json)
                    json
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * 拉取评分区评论（primarySingleRow，匿名可用，游标分页：cursor=下一页 publishTime 毫秒）。
     * cursor 传 0 表示取最新一页。
     * queryType：latest=最晚（order=desc，publishTime=now）/ earliest=最早（order=asc，publishTime=0）
     * / brightest=最亮（走独立端点 fetchHottestComments，本方法不支持）。
     * 官方 JS 端点/参数（js-8402.4afb81ca.js）：ew={earliest:"asc",latest:"desc",brightest:""}。
     */
    suspend fun fetchComments(bizType: String, bizNo: String, cursor: Long, forceNetwork: Boolean = false, queryType: String = "latest"): String? {
        val cacheKey = "score-comments-$bizType-$bizNo-$cursor-$queryType"
        return withContext(Dispatchers.IO) {
            if (!forceNetwork) {
                HupuCache.get(cacheKey)?.let { return@withContext it }
            }
            val order = if (queryType == "earliest") "asc" else "desc"
            val publishTime = if (cursor > 0) cursor else if (queryType == "earliest") 0L else System.currentTimeMillis()
            val url = "https://games.mobileapi.hupu.com/1/8.0.99/bplcommentapi/bpl/comment/list/primarySingleRow" +
                "?outBizType=$bizType&outBizNo=$bizNo&order=$order&queryType=$queryType&publishTime=$publishTime&page=1&pageSize=20" +
                "&clientCode=&cid="
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Accept", "application/json, text/plain, */*")
                .header("Referer", "https://m.hupu.com/")
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val json = response.body?.string() ?: return@use null
                    if (!isJsonBody(json)) return@use null
                    HupuCache.put(cacheKey, json)
                    json
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * 最亮评论（官方主列表「最亮」tab：primarySingleRow/hottest，data 直接是评论数组，无分页）。
     * 官方 JS：GET /bplcommentapi/bpl/comment/list/primarySingleRow/hottest，params={...outBizKey, clientCode, cid}。
     */
    suspend fun fetchHottestComments(bizType: String, bizNo: String, forceNetwork: Boolean = false): String? {
        val cacheKey = "score-comments-hottest-$bizType-$bizNo"
        return withContext(Dispatchers.IO) {
            if (!forceNetwork) {
                HupuCache.get(cacheKey)?.let { return@withContext it }
            }
            val url = "https://games.mobileapi.hupu.com/1/8.0.99/bplcommentapi/bpl/comment/list/primarySingleRow/hottest" +
                "?outBizType=$bizType&outBizNo=$bizNo&clientCode=&cid="
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Accept", "application/json, text/plain, */*")
                .header("Referer", "https://m.hupu.com/")
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val json = response.body?.string() ?: return@use null
                    if (!isJsonBody(json)) return@use null
                    HupuCache.put(cacheKey, json)
                    json
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * 楼中楼全量子评论（primarySingleRow/subCommentList，匿名可用）。
     * cursor 传 0 取第一页（latest：publishTime=now；earliest：publishTime=0）。翻页走 getMore。
     */
    suspend fun fetchSubComments(bizType: String, bizNo: String, parentCommentId: String, queryType: String = "latest", cursor: Long = 0L, forceNetwork: Boolean = false): String? {
        val cacheKey = "score-subcomments-$bizType-$bizNo-$parentCommentId-$queryType-$cursor"
        return withContext(Dispatchers.IO) {
            if (!forceNetwork) {
                HupuCache.get(cacheKey)?.let { return@withContext it }
            }
            val publishTime = if (cursor > 0) cursor else if (queryType == "earliest") 0L else System.currentTimeMillis()
            val url = "https://games.mobileapi.hupu.com/1/8.0.99/bplcommentapi/bpl/comment/list/primarySingleRow/subCommentList" +
                "?publishTime=$publishTime&outBizType=$bizType&outBizNo=$bizNo&queryType=$queryType" +
                "&parentCommentId=$parentCommentId&pageSize=20&clientCode=&cid="
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Accept", "application/json, text/plain, */*")
                .header("Referer", "https://m.hupu.com/")
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val json = response.body?.string() ?: return@use null
                    if (!isJsonBody(json)) return@use null
                    HupuCache.put(cacheKey, json)
                    json
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * 楼中楼翻页（primarySingleRow/getMore，参数同 subCommentList + publishTime=上一页 cursor）。
     */
    suspend fun fetchSubCommentsMore(bizType: String, bizNo: String, parentCommentId: String, queryType: String = "latest", cursor: Long, forceNetwork: Boolean = false): String? {
        val cacheKey = "score-subcomments-more-$bizType-$bizNo-$parentCommentId-$queryType-$cursor"
        return withContext(Dispatchers.IO) {
            if (!forceNetwork) {
                HupuCache.get(cacheKey)?.let { return@withContext it }
            }
            val url = "https://games.mobileapi.hupu.com/1/8.0.99/bplcommentapi/bpl/comment/list/primarySingleRow/getMore" +
                "?publishTime=$cursor&outBizType=$bizType&outBizNo=$bizNo&queryType=$queryType" +
                "&parentCommentId=$parentCommentId&pageSize=20&clientCode=&cid="
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Accept", "application/json, text/plain, */*")
                .header("Referer", "https://m.hupu.com/")
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val json = response.body?.string() ?: return@use null
                    if (!isJsonBody(json)) return@use null
                    HupuCache.put(cacheKey, json)
                    json
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * 拉取评分树（games.mobileapi.hupu.com bplcommentapi，匿名 GET，实测可用）。
     * 注意：顶层 children 是分页返回（pageResult.totalCount），单请求只给前 N 条
     * （官方 H5 也是先出一部分、滚动再拉下一页）。这里自动翻页聚合全量，
     * 拼接为单份响应后交给 parser——flat 选手列表与 MOBA 对局列表都可能超一页。
     */
    suspend fun fetchScoreTree(bizType: String, bizNo: String, forceNetwork: Boolean = false): String? {
        // v3：v2 缓存可能缺对局 subNodes 补全，换 key 强制重取
        val cacheKey = "score-tree3-$bizType-$bizNo"
        return withContext(Dispatchers.IO) {
            if (!forceNetwork) {
                HupuCache.get(cacheKey)?.let { return@withContext it }
            }
            val json = try {
                val root = JSONObject(fetchTreePage(bizType, bizNo, 1) ?: return@withContext null)
                val pr = root.optJSONObject("data")?.optJSONObject("pageResult")
                val items = pr?.optJSONArray("data")
                val total = pr?.optLong("totalCount", -1L) ?: -1L
                if (items != null && total > items.length()) {
                    // 一页拿不全 → 循环翻页原地追加（最多 9 页兜底，防异常服务端死循环）
                    var page = 2
                    while (items.length() < total && page <= 10) {
                        val next = fetchTreePage(bizType, bizNo, page)?.let { JSONObject(it) } ?: break
                        val nextItems = next.optJSONObject("data")
                            ?.optJSONObject("pageResult")?.optJSONArray("data") ?: break
                        if (nextItems.length() == 0) break
                        for (i in 0 until nextItems.length()) items.put(nextItems.get(i))
                        page++
                    }
                }
                // 第二层（对局）的 subNodes 同样分页截断：如 lol_bo subNodeCount=14 实给 10，
                // 缺替补/教练/BP聚合项。校验不足时以该对局为 root 子请求 CHILD 补全，
                // 替换 subNodes（元素结构与本层一致：{node, subNodes}，parser 透传兼容）
                if (items != null) {
                    for (i in 0 until items.length()) {
                        val itObj = items.optJSONObject(i) ?: continue
                        val node = itObj.optJSONObject("node") ?: continue
                        val subs = itObj.optJSONArray("subNodes") ?: continue
                        val subCount = itObj.optLong("subNodeCount", -1L)
                        if (subCount <= subs.length()) continue
                        val fullJson = fetchTreePage(node.optString("bizType"), node.optString("bizId"), 1) ?: continue
                        val full = try { JSONObject(fullJson) } catch (e: Exception) { continue }
                        val fullItems = full.optJSONObject("data")
                            ?.optJSONObject("pageResult")?.optJSONArray("data") ?: continue
                        if (fullItems.length() <= subs.length()) continue
                        itObj.put("subNodes", fullItems)
                    }
                }
                root.toString()
            } catch (e: Exception) {
                null
            }
            val jsonOk = json ?: return@withContext null
            if (!isJsonBody(jsonOk)) return@withContext null
            HupuCache.put(cacheKey, jsonOk)
            json
        }
    }

    /** 评分树单页请求（page 从 1 起，pageSize=50 常规一页拿全，超额由调用方翻页）。失败返回 null。 */
    private suspend fun fetchTreePage(bizType: String, bizNo: String, page: Int): String? {
        val url = "https://games.mobileapi.hupu.com/1/8.0.99/bplcommentapi/bpl/score_tree/" +
            "getCurAndSubNodeByBizKey?outBizType=$bizType&outBizNo=$bizNo&relation=CHILD&page=$page&pageSize=50"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Accept", "application/json, text/plain, */*")
            .header("Referer", "https://bbs.hupu.com/")
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.body?.string()?.takeIf { it.length >= 100 }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 服务端分组定义（getSubGroups，官方详情页分类条数据源，实测所有赛事通用）。
     * 返回各分组（队伍）：groupId/groupName/logo/rootNodeId/childCount。
     * 无分组（如单人对局）返回空列表。
     */
    suspend fun fetchSubGroups(bizType: String, bizNo: String): String? {
        val cacheKey = "score-groups-$bizType-$bizNo"
        return withContext(Dispatchers.IO) {
            HupuCache.get(cacheKey)?.let { return@withContext it }
            val url = "https://games.mobileapi.hupu.com/1/8.0.99/bplcommentapi/bpl/score_tree/" +
                "getSubGroups?outBizType=$bizType&outBizNo=$bizNo"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Accept", "application/json, text/plain, */*")
                .header("Referer", "https://m.hupu.com/score/detail.html")
                .build()
            val json = try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    response.body?.string()
                }
            } catch (e: Exception) {
                null
            }
            val jsonOk = json ?: return@withContext null
            if (!isJsonBody(jsonOk)) return@withContext null
            HupuCache.put(cacheKey, jsonOk)
            json
        }
    }

    /**
     * 分组成员节点（groupAndSubNodes，入参 nodeId=getSubGroups 的 rootNodeId）。
     * 返回该分组的选手节点列表（结构与树 children 一致）。
     */
    suspend fun fetchGroupNodes(nodeId: Long): String? {
        val cacheKey = "score-group-nodes-$nodeId"
        return withContext(Dispatchers.IO) {
            HupuCache.get(cacheKey)?.let { return@withContext it }
            val url = "https://games.mobileapi.hupu.com/1/8.0.99/bplcommentapi/bff/bpl/score_tree/" +
                "groupAndSubNodes?nodeId=$nodeId&queryType=default&page=1&pageSize=50"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Accept", "application/json, text/plain, */*")
                .header("Referer", "https://m.hupu.com/score/detail.html")
                .build()
            val json = try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    response.body?.string()
                }
            } catch (e: Exception) {
                null
            }
            val jsonOk = json ?: return@withContext null
            if (!isJsonBody(jsonOk)) return@withContext null
            HupuCache.put(cacheKey, jsonOk)
            json
        }
    }
}