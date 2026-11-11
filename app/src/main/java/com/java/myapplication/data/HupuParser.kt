package com.java.myapplication.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * 从虎扑 SSR HTML 中提取内嵌 JSON 并映射为数据模型。
 *
 * 两条通道（均实测验证，见 docs/hupu_web_probe.md）：
 * 1. window.$$data = {...};</script>   — 版块/话题列表页
 * 2. <script id="__NEXT_DATA__">...</script> — 帖子详情页
 *
 * 提取策略：平衡括号扫描（正确处理字符串内的引号/转义/嵌套），
 * 比非贪婪正则更稳健。
 */
object HupuParser {

    // ---------- JSON 提取 ----------

    /** 从 html 中定位 startKey 后，用平衡括号法取出完整 JSON 文本 */
    private fun extractBalancedJson(html: String, startKey: String): String? {
        val keyIdx = html.indexOf(startKey)
        if (keyIdx < 0) return null
        val braceStart = html.indexOf('{', keyIdx)
        if (braceStart < 0) return null

        var depth = 0
        var inString = false
        var escaped = false
        for (i in braceStart until html.length) {
            val c = html[i]
            if (escaped) { escaped = false; continue }
            when {
                c == '\\' && inString -> escaped = true
                c == '"' -> inString = !inString
                !inString && c == '{' -> depth++
                !inString && c == '}' -> {
                    depth--
                    if (depth == 0) return html.substring(braceStart, i + 1)
                }
            }
        }
        return null
    }

    private fun String?.toObj(): JSONObject? = this?.let {
        try { JSONObject(it) } catch (e: Exception) { null }
    }

    fun extractDollarData(html: String): JSONObject? =
        extractBalancedJson(html, "window.\$\$data=").toObj()

    fun extractNextData(html: String): JSONObject? {
        val key = "<script id=\"__NEXT_DATA__\" type=\"application/json\">"
        val start = html.indexOf(key)
        if (start < 0) return null
        val contentStart = start + key.length
        val end = html.indexOf("</script>", contentStart)
        if (end < 0) return null
        return try { JSONObject(html.substring(contentStart, end)) } catch (e: Exception) { null }
    }

    // ---------- 列表页：版块热帖 /all-* ----------

    fun parseBoardPage(html: String): HupuBoardPage? {
        val root = extractDollarData(html) ?: return null
        val pd = root.optJSONObject("pageData") ?: return null
        val threadsJson = pd.optJSONArray("threads") ?: return null

        val catJson = pd.optJSONObject("category")
        val category = catJson?.let {
            HupuTopicInfo(
                topicId = it.optString("topicId"),
                name = it.optString("name"),
                url = it.optString("url"),
                logo = it.optString("logo").ifEmpty { null },
                desc = it.optString("desc"),
            )
        }

        val hot = mutableListOf<HupuTopicInfo>()
        pd.optJSONArray("hot")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                hot += HupuTopicInfo(
                    topicId = o.optString("topicId"),
                    name = o.optString("name"),
                    url = o.optString("url"),
                    logo = o.optString("logo").ifEmpty { null },
                    hotText = o.optString("countText"),
                )
            }
        }

        val rec = mutableListOf<HupuThread>()
        pd.optJSONArray("recommend")?.let { arr ->
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { rec += threadFrom(it) }
            }
        }

        return HupuBoardPage(
            category = category,
            threads = threadsJson.mapNotNull { o -> o?.let { threadFrom(it) } },
            hotTopics = hot,
            recommendations = rec,
        )
    }

    /** 全站版块/话题导航树（来自任一 /all-* 页的 pageData.categories） */
    fun parseCategories(html: String): List<HupuCategory> {
        val root = extractDollarData(html) ?: return emptyList()
        val arr = root.optJSONObject("pageData")?.optJSONArray("categories") ?: return emptyList()
        val result = mutableListOf<HupuCategory>()
        for (i in 0 until arr.length()) {
            val c = arr.optJSONObject(i) ?: continue
            val topics = mutableListOf<HupuTopicInfo>()
            c.optJSONArray("topics")?.let { ts ->
                for (j in 0 until ts.length()) {
                    val t = ts.optJSONObject(j) ?: continue
                    topics += HupuTopicInfo(
                        topicId = t.optString("topicId"),
                        name = t.optString("name"),
                        url = t.optString("url"),
                        logo = t.optString("logo").ifEmpty { null },
                        hotText = t.optString("countText"),
                    )
                }
            }
            result += HupuCategory(
                cateId = c.optString("cateId"),
                name = c.optString("name"),
                logo = c.optString("logo").ifEmpty { null },
                topics = topics,
            )
        }
        return result
    }

    // ---------- 列表页：具体话题 /topic-{n} ----------

    fun parseTopicPage(html: String): HupuTopicPage? {
        val root = extractDollarData(html) ?: return null
        val t = root.optJSONObject("topic") ?: return null
        val topicJson = t.optJSONObject("topic") ?: return null
        val tl = t.optJSONObject("threads") ?: return null
        val listJson = tl.optJSONArray("list") ?: return null

        val tabs = mutableListOf<SortTab>()
        t.optJSONArray("tabs")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                tabs += SortTab(id = o.optInt("id"), title = o.optString("title"), url = o.optString("url"))
            }
        }

        return HupuTopicPage(
            topic = HupuTopicInfo(
                topicId = topicJson.optString("topicId"),
                name = topicJson.optString("name"),
                url = topicJson.optString("url"),
                desc = topicJson.optString("desc"),
                hotText = topicJson.optString("countText"),
                logo = topicJson.optString("logo").ifEmpty { null },
            ),
            threads = listJson.mapNotNull { o -> o?.let { threadFrom(it) } },
            page = t.optInt("page", 1),
            totalPages = tl.optInt("total", 1),
            sortTabs = tabs,
        )
    }

    // ---------- 详情页 /{tid}.html ----------

    fun parseThreadDetail(html: String): HupuThreadDetail? {
        val next = extractNextData(html) ?: return null
        val detail = next.optJSONObject("props")
            ?.optJSONObject("pageProps")
            ?.optJSONObject("detail") ?: return null

        val errCode = detail.optJSONObject("detailErrorInfo")?.optInt("code") ?: 200
        if (errCode != 200) return null

        val th = detail.optJSONObject("thread") ?: return null
        // 0 回复帖子的 replies/list 可能缺失：回退空列表（帖子本体仍可展示）
        val rep = detail.optJSONObject("replies")
        val repList = rep?.optJSONArray("list")
        val page = rep?.optInt("current", 1) ?: 1

        val replies = mutableListOf<HupuReply>()
        for (i in 0 until (repList?.length() ?: 0)) {
            val o = repList?.optJSONObject(i) ?: continue
            // 1.224p：语音附件先取出来，正文里的占位文案据此决定是否剥掉
            val audio = audioFrom(o.optJSONArray("ancillaryContents"))
            val video = videoFrom(o.optJSONArray("ancillaryContents"))
            // content 可能为空，真正的正文/视频在 dealContent 里
            val rawHtml = o.optString("content").ifBlank { o.optString("dealContent") }
            replies += HupuReply(
                pid = o.optString("pid"),
                contentHtml = if (audio.first.isNotBlank()) stripAudioPlaceholder(rawHtml) else rawHtml,
                floor = (page - 1) * 20 + i + 1,
                lights = o.optInt("allLightCount", 0),
                // 子回复数：replyNum（实测与 bbs-reply-detail API 的实际子回复数一致）。
                // 注意：count 不是回复数而是点亮数（= allLightCount，实测 642258234 逐楼比对），
                // 若误用会出现「回复数=点赞数」且点开楼中楼为空
                totalReplies = o.optInt("replyNum", 0).coerceAtLeast(0),
                createdAtText = o.optString("createdAtFormat"),
                isStarter = o.optBoolean("isStarter", false),
                location = o.optString("location"),
                author = authorFrom(o.optJSONObject("author")),
                // 1.128: quote.pid 非空 = 该条实为「回复某条评论」的子回复（真一级评论无 quote 键）
                quotePid = o.optJSONObject("quote")
                    ?.optString("pid")
                    ?.takeIf { it.isNotEmpty() && it != "" }
                    ?: "",
                // 1.224p：评论视频/语音（官方页面同样把视频放在独立字段，content 为空）
                videoUrl = o.optString("video").takeIf { it.isNotBlank() }
                    ?: video.first,
                videoCover = o.optString("videoCover").takeIf { it.isNotBlank() }
                    ?: video.second,
                audioUrl = audio.first,
                audioDurationSec = audio.second,
                // 1.232：标记这条回复属于哪一页（严谨页码用）
                page = page,
            )
        }

        val thread = threadFrom(th)
        // 1.226g/i：楼中楼接口不带视频字段，但帖子 SSR 的 replies.list / lights 里
        // 同一 pid 带着 video/videoCover —— 建索引（合并式）供楼中楼加载后按 pid 回填。
        HupuReplyMediaIndex.put(thread.tid, replyVideoIndex(detail, replies))
        return HupuThreadDetail(
            thread = thread,
            contentHtml = th.optString("content"),
            replies = replies,
            replyCount = rep?.optInt("count", thread.replies) ?: thread.replies,
            replyPage = page,
            replyTotalPages = rep?.optInt("total", 1) ?: 1,
            isLocked = th.optBoolean("isLock", false),
            // 1.119: 投票从帖子 format 的 jsonV3 vote 节点解析（单选 radio / 多选 checkbox）
            vote = voteFromFormat(th.optString("format")),
        )
    }

    /**
     * 1.119: 从帖子 format 里取投票节点。
     * format = {htmlV3, jsonV3:{type:doc,content:[{type:"vote",attrs:{voteId,limit,title,type,choices}}]}}
     * 无投票返回 null。
     */
    internal fun voteFromFormat(fmt: String?): HupuVote? {
        if (fmt.isNullOrBlank()) return null
        return try {
            val doc = JSONObject(fmt).optJSONObject("jsonV3") ?: return null
            val arr = doc.optJSONArray("content") ?: return null
            for (i in 0 until arr.length()) {
                val n = arr.optJSONObject(i) ?: continue
                if (n.optString("type") != "vote") continue
                val a = n.optJSONObject("attrs") ?: continue
                val vid = a.optInt("voteId", 0)
                if (vid <= 0) continue
                val choices = a.optJSONArray("choices")?.let { ja ->
                    (0 until ja.length()).map { ja.optString(it, "") }
                } ?: emptyList()
                return HupuVote(
                    voteId = vid,
                    title = a.optString("title", ""),
                    choices = choices,
                    limit = a.optInt("limit", 1),
                    type = a.optString("type", "radio").ifEmpty { "radio" },
                )
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /** 1.226i：从 SSR detail 里抽出「pid → 视频/封面」索引 */
    internal fun replyVideoIndex(detail: JSONObject, replies: List<HupuReply>): Map<String, Pair<String, String>> {
        val idx = HashMap<String, Pair<String, String>>()
        runCatching {
            detail.optJSONArray("lights")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val pid = o.optString("pid").trim()
                    val v = o.optString("video").trim()
                    if (pid.isNotEmpty() && v.isNotEmpty() && v != "null") {
                        idx[pid] = v to o.optString("videoCover").trim()
                    }
                }
            }
        }
        replies.forEach { r ->
            if (r.videoUrl.isNotBlank()) idx[r.pid] = r.videoUrl to r.videoCover
        }
        return idx
    }

    /** 1.226i：直接从帖子 HTML 建索引（接口层兜底用，不依赖页面是否解析过） */
    internal fun replyVideoIndexFromHtml(html: String): Map<String, Pair<String, String>> {
        val next = extractNextData(html) ?: return emptyMap()
        val detail = next.optJSONObject("props")?.optJSONObject("pageProps")
            ?.optJSONObject("detail") ?: return emptyMap()
        val list = detail.optJSONObject("replies")?.optJSONArray("list")
        val replies = mutableListOf<HupuReply>()
        if (list != null) {
            for (i in 0 until list.length()) {
                val o = list.optJSONObject(i) ?: continue
                val v = o.optString("video").trim()
                if (v.isNotEmpty() && v != "null") {
                    replies += HupuReply(pid = o.optString("pid"), videoUrl = v, videoCover = o.optString("videoCover"))
                }
            }
        }
        return replyVideoIndex(detail, replies)
    }

    // ---------- 楼中楼（m.hupu.com/api/v2/bbs-reply-detail/{tid}-{pid}） ----------

    /**
     * 解析楼中楼响应。parentPostJson 为当前回复（父楼层）对象，
     * 用于补全 API 响应里缺失的楼层/时间/位置信息。
     */
    fun parseFloorReplies(json: String, parent: HupuReply): HupuFloorReplies? {
        val root = try { JSONObject(json) } catch (e: Exception) { return null }
        val data = root.optJSONObject("data") ?: return null

        val post = data.optJSONObject("post")
        val repArr = data.optJSONArray("replies") ?: return null

        val subs = mutableListOf<HupuSubReply>()
        for (i in 0 until repArr.length()) {
            val o = repArr.optJSONObject(i) ?: continue
            val user = o.optJSONObject("user")
            val quote = o.optJSONObject("quote")
            val audio = audioFrom(o.optJSONArray("ancillaryContents"))
            val video = videoFrom(o.optJSONArray("ancillaryContents"))
            // content 可能为空，真正的正文/视频在 dealContent 里
            val rawHtml = o.optString("content").ifBlank { o.optString("dealContent") }
            subs += HupuSubReply(
                pid = o.optString("pid"),
                contentHtml = if (audio.first.isNotBlank()) stripAudioPlaceholder(rawHtml) else rawHtml,
                lights = o.optInt("lights", o.optInt("allLightCount", 0)),
                nestedCount = o.optString("replies").toIntOrNull() ?: 0,
                createdAtText = user?.optString("createDt") ?: "",
                isStarter = o.optBoolean("lz", false),
                location = o.optString("location"),
                author = subAuthorFrom(user),
                quoteUser = quote?.optString("username") ?: "",
                // 1.224p：楼中楼的语音附件
                audioUrl = audio.first,
                audioDurationSec = audio.second,
                // 1.226e：楼中楼视频——content 里可能是空串，视频在独立字段
                videoUrl = o.optString("video").takeIf { it.isNotBlank() }
                    ?: o.optString("videoUrl").takeIf { it.isNotBlank() }
                    ?: video.first,
                videoCover = o.optString("videoCover").takeIf { it.isNotBlank() }
                    ?: video.second,
            )
        }

        // post 可能带更全的父楼层信息（亮数等），有则更新父楼层
        val parentUpdated = post?.let { p ->
            val lights = p.optInt("lights", p.optInt("allLightCount", parent.lights))
            parent.copy(lights = lights)
        } ?: parent

        return HupuFloorReplies(
            parent = parentUpdated,
            subReplies = subs,
            hasMore = data.optInt("nextPage", 0) > 0,
        )
    }

    // ---------- 搜索页 /search ----------

    /** 剥除搜索高亮标签（<font color='#c01e2f'>词</font> → 词）并压空白 */
    private fun stripHighlight(html: String): String =
        html.replace(Regex("""<[^>]+>"""), "").replace(Regex("""\s+"""), " ").trim()

    fun parseSearchPage(html: String): HupuSearchPage? {
        val root = extractDollarData(html) ?: return null
        val sr = root.optJSONObject("searchRes") ?: return null
        val arr = sr.optJSONArray("data") ?: return null

        val items = mutableListOf<HupuSearchItem>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            items += HupuSearchItem(
                tid = o.optString("id"),
                title = stripHighlight(o.optString("title")),
                desc = stripHighlight(o.optString("content")),
                picture = o.optString("picture").ifEmpty { null },
                replies = o.optString("replies").toIntOrNull() ?: 0,
                lights = o.optString("lights").toIntOrNull() ?: 0,
                addtime = o.optString("addtime").toLongOrNull() ?: 0L,
                addTimeDisplay = o.optString("addTimeDisplay"),
                fid = o.optString("fid"),
                forumName = o.optString("forum_name"),
                username = o.optString("username"),
            )
        }

        val pageEcho = root.optJSONObject("query")?.optString("page")?.toIntOrNull() ?: 1
        return HupuSearchPage(
            items = items,
            page = pageEcho,
            totalPages = sr.optInt("totalPage", 0),
            count = sr.optLong("count", 0L),
        )
    }

    private fun subAuthorFrom(o: JSONObject?): HupuAuthor? {
        o ?: return null
        val name = o.optString("username").ifEmpty { o.optString("puname") }
        if (name.isEmpty()) return null
        val puid = o.optString("puid")
        return HupuAuthor(
            puid = puid,
            name = name,
            url = o.optString("header"),
            // bbs-reply-detail 的 user 不带 euid（实测 2025-11），但 puid 可直接作为
            // 用户主页跳转键（m.hupu.com/user/{puid} 与 pcmapi getUserInfo 均可达）
            euid = puid,
        )
    }

    // ---------- 映射工具 ----------

    // ---------- 步行街推荐流（m.hupu.com/api/v2/bbs/walkingStreet/threads） ----------
    /**
     * 1.32x：解析步行街推荐流 JSON（首页热帖新数据源）。
     *
     * 与 SSR 版块页的差异（详见 [HupuStreetPage] 注释）：
     *  - 列表在 `data.threadList`，每页 10 条
     *  - 封面 = `picList[0].url`；视频帖还有 `video.img/url`
     *  - **自带作者**（puid + userName）→ 黑名单按人过滤不再需要逐条补请求
     *  - 翻页要 `page` + `data.cursor` 一起传
     */
    fun parseWalkingStreet(json: String): HupuStreetPage? {
        val root = try { JSONObject(json) } catch (e: Exception) { null } ?: return null
        if (root.optInt("code") != 200) return null
        val data = root.optJSONObject("data") ?: return null
        val arr = data.optJSONArray("threadList") ?: return null
        val list = ArrayList<HupuThread>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val tid = strOf(o, "tid")
            if (tid.isEmpty()) continue
            val cover = o.optJSONArray("picList")?.optJSONObject(0)?.let { strOf(it, "url") }
            val videoObj = o.optJSONObject("video")
            val videoUrl = videoObj?.let { strOf(it, "url") } ?: ""
            val videoImg = videoObj?.let { strOf(it, "img") } ?: ""
            val isVideo = o.optBoolean("videoThread", false) || videoUrl.isNotEmpty()
            list += HupuThread(
                tid = tid,
                title = strOf(o, "title"),
                fid = strOf(o, "fid"),
                // 视频帖用视频封面兜底，避免卡片缺图
                cover = cover?.ifEmpty { null } ?: videoImg.ifEmpty { null },
                lights = o.optInt("recommendCount", 0),
                replies = o.optInt("replies", 0),
                createdAtText = strOf(o, "time"),
                hasVideo = isVideo,
                video = videoUrl,
                videoCover = videoImg,
                author = HupuAuthor(puid = strOf(o, "puid"), name = strOf(o, "userName")),
                topic = HupuTopic(topicId = strOf(o, "topicId"), name = strOf(o, "topicName")),
                url = "/$tid.html",
            )
        }
        return HupuStreetPage(
            threads = list,
            cursor = strOf(data, "cursor"),
            hasMore = data.optBoolean("nextPage", list.isNotEmpty()),
        )
    }

    /**
     * Android org.json 的 `optString` 会把 JSON null 强转成字符串 "null"，
     * 这里的读法把 null / "null" 统一收敛为空串（与 HupuMatchParser.clean 同口径）。
     */
    private fun strOf(o: JSONObject, key: String): String {
        if (o.isNull(key)) return ""
        val v = o.optString(key)
        return if (v == "null") "" else v
    }

    /**
     * 1.335：列表页 `desc`（摘要）清洗。
     *
     * 官方这份数据里混了三种「不该原样显示」的内容，治理后统一收敛为空（= 不显示摘要行）：
     *
     * 1. **缩进式前导空格**：实测 65/70 条 desc 前带 4 个半角空格（0x20），
     *    会让信息流里摘要那行整体右移，看起来像没对齐 → `trim()`。
     * 2. **机器生成的比赛内链 JSON**：形如
     *    `{"team":"football","type":"iframe-match","url":"https://games.mobileapi.hupu.com/..."}`
     *    它是给详情页塞 iframe 用的配置，被当普通文本渲染出来就是「正文一串花括号 JSON」
     *    （真机截图里那两条战报卡片）。这类没有可读文本可提取 → 整条丢弃：
     *    标题本身已写清比分（「亚精英赛：泰港7-4国安…」），少一行反而干净。
     * 3. **纯 `[图片]` 占位**：场景实测 205 条。卡片右侧已有缩略图，再显示一行「图片」
     *    纯属噪音；且专区页同一帖的 desc 为空（不显示），丢弃后两处表现一致。
     *
     * 注意：**不能**笼统地「以 `{` 或 `[` 开头就丢」——实测存在以数字开头的正常长文本
     * （如「2026赛季…」「1999山东最后一轮…」），`[` 开头也全部是 `[图片]` 而无其他形态。
     */
    private fun cleanDesc(raw: String): String? {
        val d = raw.trim()
        if (d.isEmpty()) return null
        // 机器内链 JSON：特征足够明确（team + type 前缀），不会误伤用户正文
        if (d.startsWith("{\"team\"")) return null
        // 纯图片占位：没有实际文字
        if (d == "[图片]") return null
        return d
    }

    private fun threadFrom(o: JSONObject): HupuThread = HupuThread(
        tid = o.optString("tid"),
        title = o.optString("title"),
        fid = o.optString("fid"),
        cover = o.optString("cover").ifEmpty { null },
        desc = cleanDesc(o.optString("desc")),
        lights = o.optInt("recommend", o.optInt("lights", 0)),
        replies = o.optInt("replies", 0),
        read = o.optInt("read", 0),
        createdAt = o.optLong("createdAt", 0L),
        createdAtText = o.optString("createdAtFormat"),
        // 1.189: 主楼发布地（实测 detail JSON thread.location，如「上海」；部分帖为空）
        location = o.optString("location"),
        hasVideo = o.optBoolean("hasVideo", false),
        video = o.optString("video"),
        videoCover = o.optString("videoCover"),
        author = authorFrom(o.optJSONObject("author")),
        topic = o.optJSONObject("topic")?.let {
            HupuTopic(
                topicId = it.optString("topicId"),
                name = it.optString("name"),
                url = it.optString("url"),
            )
        },
        url = o.optString("url"),
    )

    /**
     * PC 个人中心 getUserInfo JSON → HupuUserProfile（不含列表，列表走分页端点）。
     */
    fun parseSpaceUserInfo(json: String): HupuUserProfile? {
        return try {
            val o = org.json.JSONObject(json)
            if (o.optInt("code") != 1) return null
            val u = o.optJSONObject("data") ?: return null
            val euid = u.optLong("euid", 0L)
            HupuUserProfile(
                euid = if (euid != 0L) euid.toString() else u.optLong("puid", 0L).toString(),
                puid = u.optLong("puid", 0L).toString(),
                name = u.optString("nickname"),
                avatar = u.optString("header"),
                // 1.190: PC getUserInfo 也可能只下发 location（location_str 为空串）→ 回退读取
                locationStr = u.optString("location_str").ifEmpty { u.optString("location") },
                regTimeStr = u.optString("reg_time_str"),
                level = u.optString("bbsUserLevel"),
                levelDesc = u.optString("bbsUserLevelDesc"),
                levelColor = u.optString("bbsUserLevelColor"),
                levelScore = u.optLong("bbsUserLevelScore", 0L),
                nextLevelScore = u.optLong("bbsUserNextLevelScore", 0L),
                levelPercent = u.optDouble("bbsUserLevelPercent", 0.0),
                followers = u.optInt("be_follow_count", 0),
                following = u.optInt("follow_count", 0),
                beLightCount = u.optInt("be_light_count", 0),
                beRecommendCount = u.optInt("be_recommend_count", 0),
                msgCount = u.optInt("bbs_msg_count", 0),
                postCount = u.optInt("bbs_post_count", 0),
                recommendCount = u.optInt("bbs_recommend_count", 0),
                favoriteCount = u.optInt("bbs_favorite_count", 0),
                // 1.190: PC getUserInfo 下发 reputation 对象（{detail_url, value}），此前未读取，
                // 导致用户主页「声望」恒为 0 而被整段隐藏（网页有、App 没有）
                reputation = u.optJSONObject("reputation")?.optInt("value", 0) ?: 0,
                isSelf = u.optInt("is_self", 0) == 1,
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 1.190: 帖子/回帖封面统一解析（用户主页发帖/回帖/推荐/收藏四类列表共用）。
     *
     * 实测数据源里 `pics` 是**对象数组**：`[{"url":"https://…","width":1256,"height":686,
     * "is_gif":0,"type":"common"}]`；旧代码用 `optString(0)` 取第一项，拿到的是整个对象的
     * `toString()`（形如 `{"url":…}`）而不是 URL —— Coil 加载失败 → 列表右侧一直留一块
     * 84×60 的空洞，把标题压窄导致「还有很大空间就换行」，且缩略图从不显示。
     *
     * 这里同时兼容对象数组与字符串数组两种形态，并只接受 http(s) 真实地址，
     * 避免把脏值喂给图片加载器。
     */
    fun coverFrom(o: JSONObject?): String? {
        o ?: return null
        val arr = o.optJSONArray("pics") ?: o.optJSONArray("picInfos")
        if (arr != null && arr.length() > 0) {
            val url = arr.optJSONObject(0)?.optString("url").orEmpty().ifEmpty { arr.optString(0) }
            if (url.startsWith("http")) return url
        }
        // 兜底：首页信息流用的封面字段名是 cover（列表接口若换字段名也能接住）
        return o.optString("cover").takeIf { it.startsWith("http") }
    }

    private fun authorFrom(o: JSONObject?): HupuAuthor? {
        o ?: return null
        val name = o.optString("puname").ifEmpty { o.optString("username") }
        if (name.isEmpty()) return null
        // url 字段承载头像地址（详情页 author.header）；euid 承载加密用户 id（用户主页跳转用）
        return HupuAuthor(
            puid = o.optString("puid"),
            name = name,
            url = o.optString("header"),
            euid = o.optString("euid"),
        )
    }

    /**
     * 1.222: `/api/v2/threads?tid=` 详情接口里的作者。
     * 首页热帖列表不带 author，黑名单过滤需要按 tid 补取作者时用这里。
     */
    fun parseThreadAuthor(json: String): HupuAuthor? {
        return try {
            val o = JSONObject(json)
            if (o.optInt("code", 0) != 200) return null
            val t = o.optJSONObject("data")?.optJSONObject("thread") ?: return null
            authorFrom(t.optJSONObject("author"))
        } catch (e: Exception) {
            null
        }
    }

    private inline fun <T> JSONArray.mapNotNull(transform: (JSONObject?) -> T?): List<T> {
        val out = ArrayList<T>(length())
        for (i in 0 until length()) {
            transform(optJSONObject(i))?.let { out += it }
        }
        return out
    }

    // ---------- 用户主页（m.hupu.com/user/{euid}，移动 UA SSR） ----------
    /**
     * 解析用户主页。NEXT_DATA pageProps 一次给出：
     * userInfoData（资料卡全字段）+ threadList（TA 的主题帖 20 条）+ replyList（TA 的回帖 20 条）。
     * 官方移动网页同为 20+20 预览口径（?/?page/?type 均被忽略，实测），翻页留待后续版本。
     */
    fun parseUserProfile(html: String): HupuUserProfile? {
        val next = extractNextData(html) ?: return null
        val pp = next.optJSONObject("props")?.optJSONObject("pageProps") ?: return null
        val u = pp.optJSONObject("userInfoData") ?: return null
        val puid = u.optString("puid")
        val name = u.optString("nickname")
        if (name.isEmpty() && puid.isEmpty()) return null
        val threads = mutableListOf<HupuProfileThread>()
        val tl = pp.optJSONArray("threadList")
        for (i in 0 until (tl?.length() ?: 0)) {
            val o = tl?.optJSONObject(i) ?: continue
            val tid = o.optString("tid")
            if (tid.isEmpty()) continue
            threads += HupuProfileThread(
                tid = tid,
                title = o.optString("title"),
                forumName = o.optString("forum_name"),
                topicName = o.optString("topic_name"),
                replies = o.optInt("replies", 0),
                recommendNum = o.optInt("recommend_num", 0),
                createdAtText = o.optString("lastpost_time_text").ifEmpty { o.optString("create_time") },
                summary = o.optString("summary"),
                // 1.190: pics 为对象数组，需取 url（旧写法 optString(0) 拿到的是对象 toString）
                cover = coverFrom(o),
            )
        }
        val replies = mutableListOf<HupuProfileReply>()
        val rl = pp.optJSONArray("replyList")
        for (i in 0 until (rl?.length() ?: 0)) {
            val o = rl?.optJSONObject(i) ?: continue
            val pid = o.optString("pid")
            if (pid.isEmpty()) continue
            replies += HupuProfileReply(
                pid = pid,
                tid = o.optString("tid"),
                content = o.optString("content"),
                formatTime = o.optString("formatTime"),
                lights = o.optInt("allLightCount", 0),
                threadTitle = o.optString("title"),
                // 1.190: 回帖封面同样在 pics/picInfos 里，且是对象数组
                cover = coverFrom(o),
            )
        }
        val rep = u.optJSONObject("reputation")
        return HupuUserProfile(
            // 该页未下发 euid（仅 puid）；m.hupu.com/user/{puid} 实测同样返回完整 SSR，直接用 puid
            euid = pp.optString("euid").ifEmpty { u.optString("euid") }.ifEmpty { puid },
            puid = puid,
            name = name,
            avatar = u.optString("header"),
            // 1.190: 移动版 SSR 的 userInfoData 里 location_str 常为空串，真正的 IP 属地在
            // location（实测 m.hupu.com/user/119424238：location="山西" / location_str=""）
            // → 回退读取，否则用户主页会缺「IP 属地」（网页有、App 没有）。
            locationStr = u.optString("location_str").ifEmpty { u.optString("location") },
            regTimeStr = u.optString("reg_time_str"),
            level = u.optString("bbsUserLevel"),
            levelDesc = u.optString("bbsUserLevelDesc"),
            levelColor = u.optString("bbsUserLevelColor"),
            levelScore = u.optLong("bbsUserLevelScore", 0L),
            nextLevelScore = u.optLong("bbsUserNextLevelScore", 0L),
            levelPercent = u.optDouble("bbsUserLevelPercent", 0.0),
            followers = u.optInt("be_follow_count", 0),
            following = u.optInt("follow_count", 0),
            beLightCount = u.optInt("be_light_count", 0),
            beRecommendCount = u.optInt("be_recommend_count", 0),
            msgCount = u.optInt("bbs_msg_count", 0),
            postCount = u.optInt("bbs_post_count", 0),
            reputation = rep?.optInt("value", 0) ?: 0,
            threads = threads,
            replies = replies,
        )
    }

    /**
 * 1.224p：从附件数组里取「语音」。
 *
 * 两套 schema 同时支持（字段名不同、语义一致）：
 *  - 帖子评论：`ancillaryContents[]`，类型键 `contentType`
 *  - 评分评论：`commentAncillaryContents[]`，类型键 `commentContentType`
 * URL 在 `content` / `commentContent`，时长在 `durationInSec`。
 *
 * @return (语音地址, 时长秒)；无语音返回 ("", 0)
 */
internal fun audioFrom(arr: org.json.JSONArray?): Pair<String, Int> {
    if (arr == null) return "" to 0
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        val type = (o.optString("contentType").ifBlank { o.optString("commentContentType") }).uppercase()
        val url = listOf(
            "content", "commentContent", "url", "audioUrl", "voiceUrl", "voice", "audio", "mediaUrl",
        ).map { o.optString(it) }.firstOrNull { it.startsWith("http") } ?: continue
        // 类型字段缺失时，只要地址像音频也认（语音附件在部分接口里没有 type 字段）
        val looksAudio = url.contains(".mp3", true) || url.contains(".m4a", true) ||
            url.contains(".aac", true) || url.contains("audio", true)
        if (type != "AUDIO" && !(type.isEmpty() && looksAudio)) continue
        return url to o.optInt("durationInSec", o.optInt("duration", 0))
    }
    return "" to 0
}

/**
 * 1.226f：从附件数组里取**视频**（`ancillaryContents` 里 contentType=VIDEO 的项）。
 *
 * 楼中楼/评论的视频不一定在 `content` 的 `<video>` 标签里，也不一定有顶层 `video` 字段：
 * 移动端接口常把媒体统一塞进 `ancillaryContents`（IMAGE / AUDIO / VIDEO）。
 *
 * @return (视频地址, 封面)；无视频返回 ("", "")
 */
internal fun videoFrom(arr: org.json.JSONArray?): Pair<String, String> {
    if (arr == null) return "" to ""
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        val type = (o.optString("contentType").ifBlank { o.optString("commentContentType") }).uppercase()
        val url = listOf("content", "commentContent", "url", "videoUrl", "mediaUrl")
            .map { o.optString(it) }
            .firstOrNull { it.startsWith("http") } ?: continue
        val looksVideo = url.contains(".mp4", true) || url.contains(".m3u8", true) ||
            url.contains("video", true)
        if (type != "VIDEO" && !(type.isEmpty() && looksVideo)) continue
        val cover = listOf("cover", "poster", "videoCover", "img")
            .map { o.optString(it) }
            .firstOrNull { it.startsWith("http") } ?: ""
        return url to cover
    }
    return "" to ""
}

/**
 * 1.225j：从**一条评论对象**里取语音（评分评论的楼中楼有两种键名）。
 *
 * 主评论用 `commentAncillaryContents`，而楼中楼的子评论在部分响应里只有
 * `ancillaryContents`；还有的直接给 `audioUrl` / `voiceUrl` / `audio:{url}`。
 */
internal fun audioFromComment(o: org.json.JSONObject): Pair<String, Int> {
    for (key in listOf("commentAncillaryContents", "ancillaryContents")) {
        val arr = o.optJSONArray(key) ?: continue
        val r = audioFrom(arr)
        if (r.first.isNotBlank()) return r
    }
    val direct = listOf("audioUrl", "voiceUrl", "voice", "audio", "mediaUrl")
        .map { o.optString(it) }
        .firstOrNull { it.startsWith("http") }
    if (direct != null) {
        return direct to o.optInt("durationInSec", o.optInt("duration", 0))
    }
    val obj = o.optJSONObject("audio") ?: return "" to 0
    val url = listOf("url", "src", "audioUrl").map { obj.optString(it) }
        .firstOrNull { it.startsWith("http") } ?: return "" to 0
    return url to obj.optInt("durationInSec", obj.optInt("duration", 0))
}

/** 语音评论正文里的占位文案（官方模板，不是用户内容） */
private val AUDIO_PLACEHOLDERS = listOf("[语音]", "升级新版本后体验")

/**
 * 1.224p：剥掉语音评论正文里的占位文字。
 *
 * 官方语音评论的 `content` 形如 `[语音]升级新版本后体验`（升级提示也是模板的一部分），
 * 语音条已单独渲染，这些字留着就是重复噪音。
 */
internal fun stripAudioPlaceholder(s: String): String {
    var out = s
    for (p in AUDIO_PLACEHOLDERS) out = out.replace(p, "")
    return out.trim()
}

// ---------- 正文 HTML 顺序切分（1.122 内联投票） ----------

    /** 正文 HTML 顺序 token：普通文本片段 / 图片 / 视频 / 投票占位 */
    sealed class ContentToken {
        data class Text(val html: String) : ContentToken()
        data class Image(val url: String) : ContentToken()
        /** 1.224p：正文里的 `<video src poster>`（楼中楼 API 的评论视频就是以标签形式内嵌） */
        data class Video(val url: String, val cover: String) : ContentToken()
        /** 1.225h：正文里的 `<audio src>`（语音评论的另一种承载形态） */
        data class Audio(val url: String) : ContentToken()
        /**
         * 1.226：**链接卡片**（`<a data-type="card-link" data-name="标题" href="…">`）。
         * 官方 App 把它渲染成一张卡片；网页端只当普通超链接。
         */
        data class LinkCard(val url: String, val title: String) : ContentToken()
        data class Vote(val voteId: Int) : ContentToken()
    }

    /**
     * 按出现顺序把正文 HTML 切成 文本 / 图片 / 投票 三类 token。
     * 图片 `<img src>` 与投票占位 `<span data-type="vote" data-vote-id="N">` 各成一个 token，
     * 其余原样作为 Text token（由调用方再按段落细分）。
     *
     * 实测 content 形态：
     *   `<p>你最喜欢谁呢</p><p><span data-type="vote" data-vote-id="11333655"></span></p><p>做出你的选择吧</p>`
     */
    fun contentTokens(html: String?): List<ContentToken> {
        val out = mutableListOf<ContentToken>()
        if (html.isNullOrBlank()) return out
        val tokenRe = Regex(
            """(<img[^>]*?src\s*=\s*["']([^"']+)["'][^>]*?>)|(<video[^>]*?>)|(<audio[^>]*?>)|(<span[^>]*?data-type\s*=\s*["']vote["'][^>]*?>)|(<a[^>]*?data-type\s*=\s*["']card-link["'][^>]*?>(.*?)</a>)""",
            RegexOption.IGNORE_CASE,
        )
        val voteIdRe = Regex("""data-vote-id\s*=\s*["'](\d+)["']""", RegexOption.IGNORE_CASE)
        val videoSrcRe = Regex("""\bsrc\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        val hrefRe = Regex("""\bhref\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        val dataNameRe = Regex("""\bdata-name\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
        val videoPosterRe = Regex("""\bposter\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
        var cursor = 0
        for (m in tokenRe.findAll(html)) {
            if (m.range.first > cursor) out += ContentToken.Text(html.substring(cursor, m.range.first))
            cursor = m.range.last + 1
            val imgSrc = m.groups[2]?.value
            when {
                imgSrc != null -> out += ContentToken.Image(imgSrc)
                m.value.startsWith("<video", ignoreCase = true) -> {
                    val src = videoSrcRe.find(m.value)?.groupValues?.get(1)
                    if (!src.isNullOrBlank()) {
                        out += ContentToken.Video(
                            url = src,
                            cover = videoPosterRe.find(m.value)?.groupValues?.get(1).orEmpty(),
                        )
                    }
                    // `<video>…</video>` 内部是浏览器的降级提示文案（"视频无法播放…"），
                    // 整段跳过，否则会当成正文显示出来。
                    val close = html.indexOf("</video>", cursor, ignoreCase = true)
                    if (close > 0) cursor = close + "</video>".length
                }
                m.groups[6] != null -> {
                    // 链接卡片
                    val href = hrefRe.find(m.value)?.groupValues?.get(1)
                    if (!href.isNullOrBlank()) {
                        val name = dataNameRe.find(m.value)?.groupValues?.get(1)
                            ?.takeIf { it.isNotBlank() }
                        val inner = m.groups[7]?.value
                            ?.replace(Regex("<[^>]+>"), "")
                            ?.trim()
                            .orEmpty()
                        out += ContentToken.LinkCard(href, name ?: inner)
                    }
                }
                m.value.startsWith("<audio", ignoreCase = true) -> {
                    val src = videoSrcRe.find(m.value)?.groupValues?.get(1)
                    if (!src.isNullOrBlank()) out += ContentToken.Audio(src)
                    val close = html.indexOf("</audio>", cursor, ignoreCase = true)
                    if (close > 0) cursor = close + "</audio>".length
                }
                else -> {
                    val vid = voteIdRe.find(m.value)?.groupValues?.get(1)?.toIntOrNull()
                    if (vid != null && vid > 0) out += ContentToken.Vote(vid)
                }
            }
        }
        if (cursor < html.length) out += ContentToken.Text(html.substring(cursor))
        return out
    }
}