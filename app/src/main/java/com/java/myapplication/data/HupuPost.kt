package com.java.myapplication.data

import org.json.JSONObject

/**
 * 1.107: 发帖数据层。
 *
 * 端点（bbs.hupu.com，全部走 JSON）：
 *  - GET  /pcmapi/pc/bbs/v1/topic/cates                        专区分类（15 项）
 *  - GET  /pcmapi/pc/bbs/v1/topic/search?cate_id=&page=1&client=0&type=1  某分类下专区
 *  - GET  /pcmapi/pc/bbs/v1/topic/search?search=&page=1&client=0         关键词搜专区
 *  - GET  /pcmapi/pc/bbs/v1/topicZone?topicId=                 专区的子专区
 *  - POST /pcmapi/pc/bbs/v1/createThread                        发帖
 *
 * 实测结论：createThread 只给 title + content 即可成功（服务端会自动补推荐专区），
 * format(V3 json) 非必填；zoneId/tagIdList 缺省不影响。
 */
object HupuPostApi {
    private const val BBS = "/pcmapi/pc/bbs/v1"

    /** 专区分类（cate_id 0=我的 8=NBA 1=步行街 …） */
    data class Cate(val id: Int, val name: String)

    /** 专区（topicId 即发帖 payload 的 topicId） */
    data class Topic(
        val id: Int,
        val name: String,
        val logo: String?,
        val desc: String?,
        val count: String?,
        val zoneId: Int,
    )

    /** 子专区 */
    data class Zone(val id: Int, val name: String)

    /** 话题标签（tagId 进 payload 的 tagIdList，逗号分隔） */
    data class Tag(val id: Int, val name: String, val icon: String?, val tnum: Int)

    sealed class Result {
        data class Ok(val tid: String, val fid: String, val topicName: String?) : Result()
        data class Err(val msg: String) : Result()
    }

    suspend fun cates(): List<Cate> {
        val raw = HupuApi.getBbsJson("$BBS/topic/cates") ?: return emptyList()
        return try {
            val arr = JSONObject(raw).optJSONArray("data") ?: return emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val name = o.optString("name", "")
                if (name.isEmpty()) null else Cate(o.optInt("cate_id", 0), name)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun topicsByCate(cateId: Int): List<Topic> =
        parseTopics(HupuApi.getBbsJson("$BBS/topic/search?cate_id=$cateId&page=1&client=0&type=1"))

    suspend fun searchTopics(keyword: String): List<Topic> {
        val kw = try {
            java.net.URLEncoder.encode(keyword, "UTF-8")
        } catch (e: Exception) {
            keyword
        }
        return parseTopics(HupuApi.getBbsJson("$BBS/topic/search?search=$kw&page=1&client=0"))
    }

    private fun parseTopics(raw: String?): List<Topic> {
        if (raw == null) return emptyList()
        return try {
            val root = JSONObject(raw)
            val arr = root.optJSONObject("data")?.optJSONArray("list") ?: return emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val name = o.optString("name", "")
                val id = o.optInt("topic_id", 0)
                if (name.isEmpty() || id <= 0) null else Topic(
                    id = id,
                    name = name,
                    logo = o.optString("logo", "").ifEmpty { null },
                    desc = o.optString("desc", "").ifEmpty { null },
                    count = o.optString("count", "").ifEmpty { null },
                    zoneId = o.optInt("zoneId", 0),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun zones(topicId: Int): List<Zone> {
        val raw = HupuApi.getBbsJson("$BBS/topicZone?topicId=$topicId") ?: return emptyList()
        return try {
            val arr = JSONObject(raw).optJSONArray("data") ?: return emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val nm = o.optString("zoneName", "")
                val id = o.optInt("id", 0)
                if (nm.isEmpty() || id <= 0) null else Zone(id, nm)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 话题搜索（tag/search）。
     * 实测返回 data.totalElements + data.content[{tagId,name,icon,aggregationType,tnum}]；
     * keyword 为空时返回该专区默认（热门）话题列表。
     */
    suspend fun tags(topicId: Int, keyword: String): List<Tag> {
        val kw = try {
            java.net.URLEncoder.encode(keyword, "UTF-8")
        } catch (e: Exception) {
            keyword
        }
        val raw = HupuApi.getBbsJson("$BBS/tag/search?topicId=$topicId&name=$kw&pageNum=1&pageSize=50")
            ?: return emptyList()
        return try {
            val arr = JSONObject(raw).optJSONObject("data")?.optJSONArray("content") ?: return emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val nm = o.optString("name", "")
                val id = o.optInt("tagId", 0)
                if (nm.isEmpty() || id <= 0) null else Tag(
                    id = id,
                    name = nm,
                    icon = o.optString("icon", "").ifEmpty { null },
                    tnum = o.optInt("tnum", 0),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 视频封面（GET /api/v1/video/cover?videoUrl=）→ data.videoCover。
     * 官方在上传成功后立刻调它取首帧封面，失败返回 null（帖子仍可发，只是无封面）。
     */
    suspend fun videoCover(videoUrl: String): String? {
        val enc = try {
            java.net.URLEncoder.encode(videoUrl, "UTF-8")
        } catch (e: Exception) {
            videoUrl
        }
        val raw = HupuApi.getBbsJson("/api/v1/video/cover?videoUrl=$enc") ?: return null
        return try {
            JSONObject(raw).optJSONObject("data")?.optString("videoCover", "")?.ifEmpty { null }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 1.112: 编辑态原始数据。
     * 来源 GET /pcmapi/pc/bbs/v1/preCheckEdit?topicId=&tid=（返回原文 + 可编辑性判定）。
     */
    data class EditInfo(
        /** 是否允许编辑（审核中/已锁定等为 false） */
        val editFlag: Boolean,
        /** 不可编辑原因（可直接展示给用户） */
        val editMsg: String,
        val tid: String,
        val title: String,
        val contentHtml: String,
        val topicId: Int,
        val topicName: String,
        val zoneId: Int,
        val tags: List<Tag>,
        val creationType: String,
        val visibleRange: String,
        val containsAi: Int,
        /** 原始 format 字符串（投票帖/视频帖才有）；编辑反解析优先用它还原块结构 */
        val formatJson: String?,
        /** 视频帖才有（从 format.videoInfo 回读） */
        val videoUrl: String?,
        val videoCover: String?,
    )

    /**
     * 编辑前置检查 + 拉取原文。
     * 实测返回 data：{editFlag, editMsg, tid, title, content, format, type, topic{topicId,name},
     * tagList[], zoneId, visibleRange, creationType, containsAi, cardList[], ...}
     * 失败返回 null。
     */
    suspend fun preCheckEdit(tid: String, topicId: Int = 0): EditInfo? {
        val raw = HupuApi.getBbsJson("$BBS/preCheckEdit?topicId=$topicId&tid=$tid") ?: return null
        return try {
            val root = JSONObject(raw)
            if (root.optInt("code", 0) != 1) return null
            val d = root.optJSONObject("data") ?: return null
            // 视频信息藏在 format.videoInfo
            var vUrl: String? = null
            var vCover: String? = null
            val fmt = d.optString("format", "")
            if (fmt.isNotEmpty()) {
                try {
                    val vi = JSONObject(fmt).optJSONObject("videoInfo")
                    vUrl = vi?.optString("remoteUrl", "")?.ifEmpty { null }
                    vCover = vi?.optString("coverUrl", "")?.ifEmpty { null }
                } catch (e: Exception) {
                    // format 非法则视为图文帖
                }
            }
            val tagArr = d.optJSONArray("tagList")
            val tagList = if (tagArr == null) emptyList() else (0 until tagArr.length()).mapNotNull { i ->
                val o = tagArr.optJSONObject(i) ?: return@mapNotNull null
                val nm = o.optString("name", o.optString("tagName", ""))
                val id = o.optInt("tagId", o.optInt("id", 0))
                if (nm.isEmpty() || id <= 0) null else Tag(id, nm, null, 0)
            }
            val topicObj = d.optJSONObject("topic")
            EditInfo(
                editFlag = d.optBoolean("editFlag", false),
                editMsg = d.optString("editMsg", ""),
                tid = d.optString("tid", tid).ifEmpty { tid },
                title = d.optString("title", ""),
                contentHtml = d.optString("content", ""),
                topicId = topicObj?.optInt("topicId", 0) ?: 0,
                topicName = topicObj?.optString("name", "") ?: "",
                zoneId = d.optInt("zoneId", 0),
                tags = tagList,
                creationType = d.optString("creationType", "ORIGINAL").ifEmpty { "ORIGINAL" },
                visibleRange = d.optString("visibleRange", "ALL_SEE").ifEmpty { "ALL_SEE" },
                containsAi = d.optInt("containsAi", 0),
                formatJson = fmt.ifEmpty { null },
                videoUrl = vUrl,
                videoCover = vCover,
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 编辑帖子。tid 走 query（官方 mX(e) 实现：/editThread?tid=），body 与发帖同构并额外带 tid。
     */
    suspend fun editThread(
        tid: String,
        title: String,
        contentHtml: String,
        topicId: Int,
        zoneId: Int,
        tagIds: List<Int> = emptyList(),
        creationType: String = "ORIGINAL",
        containsAi: Int = 0,
        visibleRange: String = "ALL_SEE",
        videoUrl: String? = null,
        videoCover: String? = null,
        videoBaseName: String? = null,
        contentText: String = "",
        format: String? = null,
    ): Result {
        val body = buildThreadPayload(
            title = title,
            contentHtml = contentHtml,
            topicId = topicId,
            zoneId = zoneId,
            tagIds = tagIds,
            creationType = creationType,
            containsAi = containsAi,
            visibleRange = visibleRange,
            videoUrl = videoUrl,
            videoCover = videoCover,
            videoBaseName = videoBaseName,
            contentText = contentText,
            format = format,
            tid = tid,
        )
        val raw = HupuApi.postBbsApiJson("editThread?tid=$tid", body.toString())
            ?: return Result.Err("网络异常，请重试")
        return parseThreadResult(raw, tid)
    }

    /**
     * 1.113: 创建投票（发帖前先建票，拿到 voteId 再挂进正文）。
     * 返回 voteId；失败返回 null。官方单选 ballot：type=radio、limit=1。
     * 注意成功码是 200（非 1）。
     */
    suspend fun createVote(
        title: String,
        choices: List<String>,
        limit: Int = 1,
        type: String = "radio",
    ): Int? {
        val body = JSONObject()
            .put("title", title)
            .put("choices", org.json.JSONArray(choices))
            .put("type", type)
            .put("limit", limit)
        val raw = HupuApi.postVotesJson(body.toString()) ?: return null
        return try {
            val root = JSONObject(raw)
            if (root.optInt("code", 0) != 200) return null
            val vid = root.optJSONObject("data")?.optInt("voteId", 0) ?: 0
            if (vid > 0) vid else null
        } catch (e: Exception) {
            null
        }
    }

    /** 从正文 HTML 提取图片 URL（编辑态预填图片列表用） */
    fun extractImages(html: String): List<String> =
        Regex("<img[^>]*src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
            .findAll(html)
            .map { it.groupValues[1] }
            .filter { it.isNotBlank() && !it.startsWith("data:") }
            .toList()

    /** 正文 HTML → 纯文本（编辑态预填文本域；隐藏占位与图片节点丢弃） */
    fun stripHtml(html: String): String {
        var s = html
        s = s.replace(
            Regex("<span[^>]*display\\s*:\\s*none[^>]*>\\s*</span>", RegexOption.IGNORE_CASE),
            "",
        )
        s = s.replace(Regex("<img[^>]*>", RegexOption.IGNORE_CASE), "")
        s = s.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        s = s.replace(Regex("</(p|div|h[1-6]|li|tr)>", RegexOption.IGNORE_CASE), "\n")
        s = s.replace(Regex("<[^>]+>"), "")
        s = unescape(s)
        return s.split("\n").joinToString("\n") { it.trim() }.trim('\n')
    }

    private fun unescape(s: String): String {
        var r = s
        r = r.replace("&lt;", "<")
        r = r.replace("&gt;", ">")
        r = r.replace("&#39;", "'")
        r = r.replace("&nbsp;", " ")
        r = r.replace("&amp;", "&")
        // 双引号实体：拆开拼接，避免源码里出现完整的实体字面量
        r = r.replace("&" + "quot;", "\"")
        return r
    }

    /**
     * 组装 createThread 请求体（抽出来便于单测，不发起网络）。
     * 视频帖额外带：videoUrl / videoSnapshotUrl / videoSource / format。
     */
    internal fun buildThreadPayload(
        title: String,
        contentHtml: String,
        topicId: Int,
        zoneId: Int,
        tagIds: List<Int> = emptyList(),
        creationType: String = "ORIGINAL",
        containsAi: Int = 0,
        visibleRange: String = "ALL_SEE",
        videoUrl: String? = null,
        videoCover: String? = null,
        videoBaseName: String? = null,
        contentText: String = "",
        /** 1.113: 正文附带 format（仅投票帖需要；视频帖的 format 由 videoUrl 分支生成） */
        format: String? = null,
        /** 编辑态才传（1.112）：body 额外带 tid；发帖为 null */
        tid: String? = null,
    ): JSONObject = JSONObject().apply {
        put("title", title)
        if (!tid.isNullOrBlank()) put("tid", tid)
        // 视频帖正文可为空：官方会用隐藏 span 占位（服务端校验 content 不能为空）
        put(
            "content",
            contentHtml.ifBlank {
                if (!videoUrl.isNullOrBlank())
                    "<span data-time=\"${System.currentTimeMillis()}\" style=\"display:none\"></span>"
                else ""
            },
        )
        if (topicId > 0) put("topicId", topicId)
        put("zoneId", zoneId)
        if (tagIds.isNotEmpty()) put("tagIdList", tagIds.joinToString("") { "$it," })
        put("creationType", creationType.ifEmpty { "ORIGINAL" })
        if (visibleRange.isNotEmpty()) put("visibleRange", visibleRange)
        put("containsAi", containsAi)
        if (!videoUrl.isNullOrBlank()) {
            put("videoUrl", videoUrl)
            put("videoSnapshotUrl", videoCover ?: "")
            put("videoSource", "")
            put("format", buildVideoFormat(videoUrl, videoCover, videoBaseName, contentText))
        } else if (!format.isNullOrBlank()) {
            // 1.113: 投票帖的 format（含 jsonV3 投票节点）
            put("format", format)
        }
    }

    /**
     * 发帖。contentHtml 由 [buildContent] 生成。
     * tagIds 进 tagIdList（官方格式为「id1,id2,」逗号结尾）；creationType 为 ORIGINAL/REPRINT。
     * 失败时返回服务端中文 msg（如「帖子内容不能为空」）。
     *
     * 1.110 视频帖（官方 /newpost?tabkey=2 路径）：除 videoUrl/videoSnapshotUrl/videoSource 帖子级字段外，
     * 官方还会带 format = JSON.stringify({slateValue:[{type:"paragraph",children:[{text:正文}]}],
     * videoInfo:{key,remoteUrl,coverUrl}}) —— 详情页/编辑器从 format.videoInfo 回读播放地址与封面，
     * 故视频帖必须一并提交。videoSource 本地上传时为空串。
     */
    suspend fun createThread(
        title: String,
        contentHtml: String,
        topicId: Int,
        zoneId: Int,
        tagIds: List<Int> = emptyList(),
        creationType: String = "ORIGINAL",
        containsAi: Int = 0,
        visibleRange: String = "ALL_SEE",
        videoUrl: String? = null,
        videoCover: String? = null,
        videoBaseName: String? = null,
        contentText: String = "",
        format: String? = null,
    ): Result {
        val body = buildThreadPayload(
            title = title,
            contentHtml = contentHtml,
            topicId = topicId,
            zoneId = zoneId,
            tagIds = tagIds,
            creationType = creationType,
            containsAi = containsAi,
            visibleRange = visibleRange,
            videoUrl = videoUrl,
            videoCover = videoCover,
            videoBaseName = videoBaseName,
            contentText = contentText,
            format = format,
        )
        val raw = HupuApi.postBbsApiJson("createThread", body.toString())
            ?: return Result.Err("网络异常，请重试")
        return parseThreadResult(raw, "")
    }

    /**
     * 1.119: 删除帖子（POST /pcmapi/pc/bbs/v1/delete/threads/{tid}，body {}）。
     * 依据：官方编辑器分块 c15.js —— `request(`/pcmapi/pc/bbs/v1/delete/threads/${tid}`, {})`，
     * 成功判据 `code === 1 && data`。仅本人帖可删（服务端校验），失败返回中文 msg。
     * @return null 表示成功，否则为错误文案
     */
    suspend fun deleteThread(tid: String): String? {
        val raw = HupuApi.postBbsApiJson("delete/threads/$tid", "{}")
            ?: return "网络异常，请重试"
        return try {
            val root = JSONObject(raw)
            if (root.optInt("code", 0) == 1) null else root.optString("msg", "删除失败")
        } catch (e: Exception) {
            "响应解析失败"
        }
    }

    /** createThread / editThread 的响应解析（code=1 + data.tid/fid/jumpDTO） */
    private fun parseThreadResult(raw: String, fallbackTid: String): Result {
        return try {
            val root = JSONObject(raw)
            if (root.optInt("code", 0) == 1) {
                val d = root.optJSONObject("data")
                Result.Ok(
                    tid = d?.optString("tid", "").orEmpty().ifEmpty { fallbackTid },
                    fid = d?.optString("fid", "").orEmpty(),
                    topicName = d?.optJSONObject("suggestedTopicDto")?.optString("name", "")?.ifEmpty { null }
                        ?: d?.optJSONObject("topic")?.optString("name", "")?.ifEmpty { null },
                )
            } else {
                Result.Err(root.optString("msg", "发布失败"))
            }
        } catch (e: Exception) {
            Result.Err("响应解析失败")
        }
    }

    /**
     * 正文 HTML 构造：段落按空行切分，图片附在正文末尾（各占一段）。
     * 与官方编辑器同构（<p> 段落 + <img> 独立节点），可被网页端与 App 渲染器正确解析。
     */
    fun buildContent(text: String, imageUrls: List<String>): String {
        val sb = StringBuilder()
        text.split("\n").forEach { line ->
            val t = line.trim()
            if (t.isNotEmpty()) sb.append("<p>").append(escape(t)).append("</p>")
        }
        imageUrls.forEach { url ->
            if (url.isNotBlank()) sb.append("<p><img src=\"").append(url).append("\"/></p>")
        }
        return sb.toString()
    }

    private fun escape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    /**
     * 视频帖 format 字段（官方 buildSubmitData 原样结构）：
     * { slateValue:[{type:"paragraph",children:[{text:正文}]}],
     *   videoInfo:{ key, remoteUrl:播放地址, coverUrl:封面 } }
     * key = base64(视频文件名 + 毫秒时间戳)，仅作唯一标识（官方用 btoa(baseVideoName + Date.now())）。
     */
    private fun buildVideoFormat(
        videoUrl: String,
        videoCover: String?,
        videoBaseName: String?,
        contentText: String,
    ): String {
        val key = try {
            val raw = (videoBaseName ?: "video") + System.currentTimeMillis()
            okio.ByteString.of(*raw.toByteArray()).base64()
        } catch (e: Exception) {
            "v" + System.currentTimeMillis()
        }
        val para = JSONObject().apply {
            put("type", "paragraph")
            put(
                "children",
                org.json.JSONArray().put(JSONObject().put("text", contentText)),
            )
        }
        val info = JSONObject().apply {
            put("key", key)
            put("remoteUrl", videoUrl)
            put("coverUrl", videoCover ?: "")
        }
        return JSONObject().apply {
            put("slateValue", org.json.JSONArray().put(para))
            put("videoInfo", info)
        }.toString()
    }
}
