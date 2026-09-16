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
            replies += HupuReply(
                pid = o.optString("pid"),
                contentHtml = o.optString("content"),
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
            )
        }

        val thread = threadFrom(th)
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
            subs += HupuSubReply(
                pid = o.optString("pid"),
                contentHtml = o.optString("content"),
                lights = o.optInt("lights", o.optInt("allLightCount", 0)),
                nestedCount = o.optString("replies").toIntOrNull() ?: 0,
                createdAtText = user?.optString("createDt") ?: "",
                isStarter = o.optBoolean("lz", false),
                location = o.optString("location"),
                author = subAuthorFrom(user),
                quoteUser = quote?.optString("username") ?: "",
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

    private fun threadFrom(o: JSONObject): HupuThread = HupuThread(
        tid = o.optString("tid"),
        title = o.optString("title"),
        fid = o.optString("fid"),
        cover = o.optString("cover").ifEmpty { null },
        desc = o.optString("desc").ifEmpty { null },
        lights = o.optInt("recommend", o.optInt("lights", 0)),
        replies = o.optInt("replies", 0),
        read = o.optInt("read", 0),
        createdAt = o.optLong("createdAt", 0L),
        createdAtText = o.optString("createdAtFormat"),
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
                locationStr = u.optString("location_str"),
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
                isSelf = u.optInt("is_self", 0) == 1,
            )
        } catch (e: Exception) {
            null
        }
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
                cover = o.optJSONArray("pics")?.optString(0)?.takeIf { it.isNotEmpty() },
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
                cover = o.optJSONArray("picInfos")?.optJSONObject(0)?.optString("url")?.takeIf { it.isNotEmpty() },
            )
        }
        val rep = u.optJSONObject("reputation")
        return HupuUserProfile(
            // 该页未下发 euid（仅 puid）；m.hupu.com/user/{puid} 实测同样返回完整 SSR，直接用 puid
            euid = pp.optString("euid").ifEmpty { u.optString("euid") }.ifEmpty { puid },
            puid = puid,
            name = name,
            avatar = u.optString("header"),
            locationStr = u.optString("location_str"),
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

    // ---------- 正文 HTML 顺序切分（1.122 内联投票） ----------

    /** 正文 HTML 顺序 token：普通文本片段 / 图片 / 投票占位 */
    sealed class ContentToken {
        data class Text(val html: String) : ContentToken()
        data class Image(val url: String) : ContentToken()
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
            """(<img[^>]*?src\s*=\s*["']([^"']+)["'][^>]*?>)|(<span[^>]*?data-type\s*=\s*["']vote["'][^>]*?>)""",
            RegexOption.IGNORE_CASE,
        )
        val voteIdRe = Regex("""data-vote-id\s*=\s*["'](\d+)["']""", RegexOption.IGNORE_CASE)
        var cursor = 0
        for (m in tokenRe.findAll(html)) {
            if (m.range.first > cursor) out += ContentToken.Text(html.substring(cursor, m.range.first))
            cursor = m.range.last + 1
            val imgSrc = m.groups[2]?.value
            if (imgSrc != null) {
                out += ContentToken.Image(imgSrc)
            } else {
                val vid = voteIdRe.find(m.value)?.groupValues?.get(1)?.toIntOrNull()
                if (vid != null && vid > 0) out += ContentToken.Vote(vid)
            }
        }
        if (cursor < html.length) out += ContentToken.Text(html.substring(cursor))
        return out
    }
}