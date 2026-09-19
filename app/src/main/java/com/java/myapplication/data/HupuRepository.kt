package com.java.myapplication.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 虎扑数据仓库 —— UI 层唯一入口。
 * 所有方法挂起执行，内部处理缓存/网络/解析。
 * 解析统一跑在 Dispatchers.Default：大 JSON/HTML 在主线程解析会在
 * 盖入动画期间掉帧（赛程 256KB、评分树 222KB 等实测体积）。
 */
class HupuRepository {

    /** 版块热帖页（首页默认：步行街 /all-gambia） */
    suspend fun board(url: String = "/all-gambia", refresh: Boolean = false): HupuBoardPage? {
        val html = HupuApi.fetchHtml(url, forceNetwork = refresh) ?: return null
        return withContext(Dispatchers.Default) { HupuParser.parseBoardPage(html) }
    }

    /** 全站版块/话题导航树（取自任一 /all-* 页） */
    suspend fun categories(refresh: Boolean = false): List<HupuCategory> {
        val html = HupuApi.fetchHtml("/all-gambia", forceNetwork = refresh) ?: return emptyList()
        return withContext(Dispatchers.Default) { HupuParser.parseCategories(html) }
    }

    /** 话题列表页（含分页）。url 如 /topic、/topic-daily；page 从 1 开始 */
    suspend fun topicPage(url: String, page: Int = 1, refresh: Boolean = false): HupuTopicPage? {
        val path = if (page <= 1) url else "$url-$page"
        val html = HupuApi.fetchHtml(path, forceNetwork = refresh) ?: return null
        return withContext(Dispatchers.Default) { HupuParser.parseTopicPage(html) }
    }

    /** 帖子详情（第 1 页回复） */
    suspend fun threadDetail(tid: String, refresh: Boolean = false): HupuThreadDetail? {
        val html = HupuApi.fetchHtml("/$tid.html", forceNetwork = refresh)
            // 1.123: 强刷失败降级磁盘缓存（TTL 内）——断网/服务器抖动时详情页仍可打开
            ?: if (refresh) HupuCache.get("/$tid.html") else null
        if (html == null) return null
        return withContext(Dispatchers.Default) { HupuParser.parseThreadDetail(html) }
    }

    /**
     * 1.123: 只读磁盘缓存（无网络）——详情页每次进入都强制刷新时效性，
     * 但进程重启/跨页首进时先用缓存秒开第一帧，再后台拉最新。
     */
    suspend fun threadDetailCached(tid: String): HupuThreadDetail? {
        val html = HupuCache.get("/$tid.html") ?: return null
        return withContext(Dispatchers.Default) { HupuParser.parseThreadDetail(html) }
    }

    /** 帖子回复翻页 */
    suspend fun threadReplies(tid: String, page: Int): HupuThreadDetail? {
        if (page <= 1) return threadDetail(tid)
        val html = HupuApi.fetchHtml("/$tid-$page.html") ?: return null
        return withContext(Dispatchers.Default) { HupuParser.parseThreadDetail(html) }
    }

    /** 1.185: 方向感知——是否还有更多回复 */
    fun hasMoreReplies(cur: HupuThreadDetail): Boolean =
        if (cur.descReplies) cur.replyPage > 1 else cur.replyPage < cur.replyTotalPages

    /** 1.185: 首屏/切向——desc=true 时从最后一页取（页内反转），正序同 threadDetail */
    suspend fun threadDetailDirected(tid: String, desc: Boolean, refresh: Boolean = false): HupuThreadDetail? {
        val base = threadDetail(tid, refresh) ?: return null
        if (!desc) return base.copy(descReplies = false)
        val total = base.replyTotalPages
        if (total <= 1) return base.copy(replies = base.replies.reversed(), descReplies = true)
        val last = threadReplies(tid, total) ?: return base.copy(descReplies = true)
        return base.copy(replies = last.replies.reversed(), replyPage = total, descReplies = true)
    }

    /** 1.185: 方向感知翻页（倒序递减）。返回 null = 无更多 */
    suspend fun threadRepliesNext(tid: String, cur: HupuThreadDetail): HupuThreadDetail? {
        val total = cur.replyTotalPages
        val next = if (cur.descReplies) cur.replyPage - 1 else cur.replyPage + 1
        if (next < 1 || next > total) return null
        return threadReplies(tid, next)?.let {
            it.copy(replies = if (cur.descReplies) it.replies.reversed() else it.replies, descReplies = cur.descReplies)
        }
    }

    /** 1.185: 仅切换回帖方向——保留主楼与统计（thread），只替换 replies，避免数字抖动 */
    suspend fun repliesDirected(tid: String, base: HupuThreadDetail, desc: Boolean): HupuThreadDetail? {
        if (!desc) {
            val p1 = threadReplies(tid, 1) ?: return null
            return base.copy(replies = p1.replies, replyPage = 1, descReplies = false)
        }
        val total = base.replyTotalPages
        if (total <= 1) return base.copy(replies = base.replies.reversed(), descReplies = true)
        val last = threadReplies(tid, total) ?: return null
        return base.copy(replies = last.replies.reversed(), replyPage = total, descReplies = true)
    }

    /** 用户主页（资料卡 + 主题帖/回帖首屏，SSR 一次请求） */
    // ---------- PC 个人中心（my.hupu.com 契约，登录态） ----------

    /** getUserInfo：全量资料卡（声望/IP/真实计数/是否本人） */
    suspend fun spaceUserInfo(euid: String): HupuUserProfile? {
        val body = HupuApi.fetchSpaceApi("getUserInfo?euid=$euid") ?: return null
        return HupuParser.parseSpaceUserInfo(body)
    }

    /** 发帖列表（page 分页，数组直出） */
    suspend fun spaceThreads(euid: String, page: Int): List<HupuProfileThread> {
        val body = HupuApi.fetchSpaceApi("getThreadList?euid=$euid&page=$page&pageSize=30") ?: return emptyList()
        return try {
            val o = org.json.JSONObject(body)
            if (o.optInt("code") != 1) return emptyList()
            val arr = o.optJSONArray("data") ?: return emptyList()
            val out = mutableListOf<HupuProfileThread>()
            for (i in 0 until arr.length()) {
                val t = arr.optJSONObject(i) ?: continue
                out += HupuProfileThread(
                    tid = t.optString("tid"),
                    title = t.optString("title"),
                    forumName = t.optString("forum_name"),
                    topicName = t.optString("topic_name"),
                    replies = t.optInt("replies", 0),
                    recommendNum = t.optInt("recommend_num", 0),
                    createdAtText = t.optLong("create_time", 0L).takeIf { it > 0 }?.let {
                        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA).format(java.util.Date(it * 1000))
                    } ?: "",
                    summary = t.optString("summary"),
                    cover = t.optJSONArray("pics")?.optString(0)?.takeIf { it.isNotEmpty() },
                )
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 回帖列表（maxTime 游标分页，pageSize=10） */
    suspend fun spaceReplies(euid: String, page: Int, maxTime: Long = 0L): Pair<List<HupuProfileReply>, Long> {
        val body = HupuApi.fetchSpaceApi("getReplyList?euid=$euid&maxTime=$maxTime&page=$page&pageSize=10") ?: return Pair(emptyList(), 0L)
        return try {
            val o = org.json.JSONObject(body)
            if (o.optInt("code") != 1) return Pair(emptyList(), maxTime)
            val d = o.optJSONObject("data") ?: return Pair(emptyList(), maxTime)
            val arr = d.optJSONArray("replyWithQuoteDtoList") ?: return Pair(emptyList(), maxTime)
            val out = mutableListOf<HupuProfileReply>()
            for (i in 0 until arr.length()) {
                val t = arr.optJSONObject(i) ?: continue
                out += HupuProfileReply(
                    pid = t.optString("pid"),
                    tid = t.optString("tid"),
                    content = t.optString("content"),
                    formatTime = t.optString("formatTime"),
                    lights = t.optInt("lightCount", 0),
                    threadTitle = t.optString("title").ifEmpty { t.optString("topicName") },
                    cover = t.optJSONArray("picInfos")?.optJSONObject(0)?.optString("url")?.takeIf { it.isNotEmpty() },
                )
            }
            Pair(out, d.optLong("maxTime", maxTime))
        } catch (e: Exception) {
            Pair(emptyList(), maxTime)
        }
    }

    /** 推荐列表（page 分页，data.content 数组） */
    suspend fun spaceRecommends(euid: String, page: Int): List<HupuProfileThread> {
        val body = HupuApi.fetchSpaceApi("getRecommendList?euid=$euid&page=$page&pageSize=30") ?: return emptyList()
        return try {
            val o = org.json.JSONObject(body)
            if (o.optInt("code") != 1) return emptyList()
            val d = o.optJSONObject("data") ?: return emptyList()
            val arr = d.optJSONArray("content") ?: return emptyList()
            val out = mutableListOf<HupuProfileThread>()
            for (i in 0 until arr.length()) {
                val t = arr.optJSONObject(i) ?: continue
                out += HupuProfileThread(
                    tid = t.optString("tid"),
                    title = t.optString("title"),
                    forumName = t.optString("forum_name"),
                    topicName = t.optString("topic_name"),
                    replies = t.optInt("replies", 0),
                    recommendNum = t.optInt("recommend_num", 0),
                    createdAtText = t.optLong("create_time", 0L).takeIf { it > 0 }?.let {
                        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA).format(java.util.Date(it * 1000))
                    } ?: "",
                    summary = t.optString("summary"),
                    cover = t.optJSONArray("pics")?.optString(0)?.takeIf { it.isNotEmpty() },
                )
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 收藏列表（仅自己可见，page 分页，条目同发帖结构） */
    suspend fun spaceFavorites(euid: String, page: Int): List<HupuProfileThread> {
        val body = HupuApi.fetchSpaceApi("getFavoritesList?euid=$euid&page=$page&pageSize=30") ?: return emptyList()
        return try {
            val o = org.json.JSONObject(body)
            if (o.optInt("code") != 1) return emptyList()
            val d = o.optJSONObject("data") ?: return emptyList()
            val arr = d.optJSONArray("content") ?: return emptyList()
            val out = mutableListOf<HupuProfileThread>()
            for (i in 0 until arr.length()) {
                val t = arr.optJSONObject(i) ?: continue
                out += HupuProfileThread(
                    tid = t.optString("tid"),
                    title = t.optString("title"),
                    forumName = t.optString("forum_name"),
                    topicName = t.optString("topic_name"),
                    replies = t.optInt("replies", 0),
                    recommendNum = t.optInt("recommend_num", 0),
                    createdAtText = t.optLong("create_time", 0L).takeIf { it > 0 }?.let {
                        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA).format(java.util.Date(it * 1000))
                    } ?: "",
                    summary = t.optString("summary"),
                    cover = t.optJSONArray("pics")?.optString(0)?.takeIf { it.isNotEmpty() },
                )
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 关注列表（type=1 TA关注的/我关注的，type=2 关注TA的/关注我的） */
    suspend fun spaceFollows(euid: String, type: Int, page: Int): List<HupuFollowUser> {
        val body = HupuApi.fetchSpaceApi("getUserFollowList?euid=$euid&type=$type&page=$page&pageSize=30") ?: return emptyList()
        return try {
            val o = org.json.JSONObject(body)
            if (o.optInt("code") != 1) return emptyList()
            val arr = o.optJSONArray("data") ?: return emptyList()
            val out = mutableListOf<HupuFollowUser>()
            for (i in 0 until arr.length()) {
                val t = arr.optJSONObject(i) ?: continue
                // \u5b98\u65b9\u5b57\u6bb5\u65b9\u5411\u76f8\u5bf9\uff1atype=1(\u4ed6\u5173\u6ce8\u7684)\u5bf9\u65b9\u6807\u8bc6\u5728 buddyPuid\uff1btype=2(\u5173\u6ce8\u4ed6\u7684)\u5728 uid
                val oid = if (type == 1) t.optLong("buddyPuid", 0L) else t.optLong("uid", 0L)
                out += HupuFollowUser(
                    puid = oid.toString(),
                    name = t.optString("username"),
                    avatar = t.optString("header"),
                    level = t.optInt("level", -1),
                    fansNum = t.optInt("fansNum", 0),
                    joinDaysText = t.optString("time"),
                    threadsNum = t.optInt("threadsNum", 0),
                    repliesNum = t.optInt("repliesNum", 0),
                    isSelf = t.optInt("isSelf", 0) == 1,
                )
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun userProfile(euid: String, refresh: Boolean = false): HupuUserProfile? {
        val html = HupuApi.fetchUserHtml(euid, forceNetwork = refresh) ?: return null
        return withContext(Dispatchers.Default) { HupuParser.parseUserProfile(html) }
    }

    /**
     * 楼中楼：拉取某楼层的子回复（首屏 20 条）。
     * maxpid 非空时翻页。失败返回 null。
     */
    suspend fun floorReplies(tid: String, parent: HupuReply, maxpid: String? = null): HupuFloorReplies? {
        val json = HupuApi.fetchFloorReplies(tid, parent.pid, maxpid) ?: return null
        return withContext(Dispatchers.Default) { HupuParser.parseFloorReplies(json, parent) }
    }

    /** 搜索页（桌面版 SSR，20 条/页；sortby 六种排序；topicId 专区过滤） */
    suspend fun search(query: String, topicId: String? = null, sortby: String = "general", page: Int = 1, refresh: Boolean = false): HupuSearchPage? {
        val html = HupuApi.fetchSearchHtml(query, topicId, sortby, page, forceNetwork = refresh)
            ?: return null
        return withContext(Dispatchers.Default) { HupuParser.parseSearchPage(html) }
    }

    /** 比赛赛程评分（6 大电竞项目）。businessId 如 lol/kog/val/cs2/pubgmobile/pubg */
    suspend fun matchSchedule(businessId: String, refresh: Boolean = false): List<HupuMatchDay> {
        val json = HupuMatchApi.fetchSchedule(businessId, forceNetwork = refresh) ?: return emptyList()
        return withContext(Dispatchers.Default) { HupuMatchParser.parseSchedule(json) }
    }

    /** 评分树：比赛 → 对局[] → 选手[]（一次请求全量，匿名可用）。失败返回 null。 */
    suspend fun scoreTree(bizType: String, bizNo: String, refresh: Boolean = false): HupuScoreTree? {
        val json = HupuMatchApi.fetchScoreTree(bizType, bizNo, forceNetwork = refresh) ?: return null
        return withContext(Dispatchers.Default) { HupuMatchParser.parseScoreTree(json) }
    }

    /** 服务端分组定义（官方分类条数据源，所有赛事通用；flat 结构的「全部/队名/趣评」）。 */
    suspend fun scoreGroups(bizType: String, bizNo: String): List<HupuScoreGroup> {
        val json = HupuMatchApi.fetchSubGroups(bizType, bizNo) ?: return emptyList()
        return withContext(Dispatchers.Default) { HupuMatchParser.parseSubGroups(json) }
    }

    /** 分组成员（groupAndSubNodes?nodeId=rootNodeId）。失败返回空列表。 */
    suspend fun scoreGroupNodes(nodeId: Long): List<HupuScoreItem> {
        val json = HupuMatchApi.fetchGroupNodes(nodeId) ?: return emptyList()
        return withContext(Dispatchers.Default) { HupuMatchParser.parseGroupNodes(json) }
    }

    /** 节点详情（第 4 层选手/对局：评分分布+热评）。失败返回 null。 */
    suspend fun scoreSelf(bizType: String, bizNo: String, refresh: Boolean = false): HupuSelfDetail? {
        val json = HupuMatchApi.fetchSelf(bizType, bizNo, forceNetwork = refresh) ?: return null
        return withContext(Dispatchers.Default) { HupuMatchParser.parseSelfDetail(json) }
    }

    /** 评分区评论（游标分页：cursor=0 取最新页，翻页用上一页返回的 cursor）。失败返回 null。 */
    suspend fun scoreComments(bizType: String, bizNo: String, cursor: Long, refresh: Boolean = false, queryType: String = "latest"): ScoreCommentState? {
        if (queryType == "brightest") {
            // 最亮走独立端点（官方主列表 bright tab 用 hottest，无分页）
            val json = HupuMatchApi.fetchHottestComments(bizType, bizNo, forceNetwork = refresh) ?: return null
            val list = withContext(Dispatchers.Default) { HupuMatchParser.parseHottestComments(json) }
            if (list.isEmpty()) return null
            return ScoreCommentState(comments = list, commentCount = list.size.toLong(), cursor = 0L, hasMore = false)
        }
        val json = HupuMatchApi.fetchComments(bizType, bizNo, cursor, forceNetwork = refresh, queryType = queryType) ?: return null
        return withContext(Dispatchers.Default) { HupuMatchParser.parseComments(json) }
    }

    /** 楼中楼全量子评论（subCommentList；cursor=0 第一页）。失败返回 null。
     *  1.149: `refresh=true` 绕过 HupuCache 强制网络——回复成功后回填真实数据用。
     */
    suspend fun scoreSubComments(bizType: String, bizNo: String, parentCommentId: String, queryType: String = "latest", cursor: Long = 0L, refresh: Boolean = false): ScoreCommentState? {
        val json = HupuMatchApi.fetchSubComments(bizType, bizNo, parentCommentId, queryType, cursor, forceNetwork = refresh) ?: return null
        return withContext(Dispatchers.Default) { HupuMatchParser.parseSubComments(json) }
    }

    /** 楼中楼翻页（getMore；cursor=上一页返回的 publishTime）。失败返回 null。 */
    suspend fun scoreSubCommentsMore(bizType: String, bizNo: String, parentCommentId: String, queryType: String = "latest", cursor: Long): ScoreCommentState? {
        val json = HupuMatchApi.fetchSubCommentsMore(bizType, bizNo, parentCommentId, queryType, cursor) ?: return null
        return withContext(Dispatchers.Default) { HupuMatchParser.parseSubComments(json) }
    }

    /** 通用评分首页（SSR，每次刷新换一批）。失败返回空列表。 */
    suspend fun commonSubjects(refresh: Boolean = false): List<HupuCommonSubject> {
        val html = HupuCommonApi.fetchScoreHome(forceNetwork = refresh) ?: return emptyList()
        return withContext(Dispatchers.Default) { HupuCommonParser.parseScoreHome(html) }
    }

    /** 通用评分主题树（分页）。失败返回 null。 */
    suspend fun commonTree(bizType: String, bizNo: String, page: Int = 1, refresh: Boolean = false): HupuCommonTree? {
        val json = HupuMatchApi.fetchCommonTree(bizType, bizNo, page, forceNetwork = refresh) ?: return null
        return withContext(Dispatchers.Default) { HupuCommonParser.parseCommonTree(json) }
    }

    /**
     * 1.123: 详情换新合并——服务端新数据替换主楼/计数，已加载的多页回复按 pid 保序合并保留。
     * 场景：每次进入详情页都后台强刷（时效性），但不让已翻页加载过的深层回复被第 1 页覆盖砍短。
     */
    fun mergeThreadDetail(old: HupuThreadDetail?, fresh: HupuThreadDetail): HupuThreadDetail {
        if (old == null) return fresh
        if (old.replyPage <= 1) return fresh
        // old 已翻页：保留已加载回复（新数据里已含第 1 页最新回复），翻页游标取新
        val merged = (fresh.replies + old.replies).distinctBy { it.pid }
        return fresh.copy(replies = merged, replyPage = fresh.replyPage)
    }
}