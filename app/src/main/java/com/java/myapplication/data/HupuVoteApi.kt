package com.java.myapplication.data

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 1.121: 投票读 / 写接口（官方前端实测契约，2026-09）。
 *
 * - 读取：GET https://bbs.mobileapi.hupu.com/3/8.0.80/bbsintapi/vote/v1/getVoteInfo?voteId=
 *   **免登录**即可读到汇总票数；带登录 cookie 时额外返回 userVoteRecordList（我投了哪几项）。
 * - 提交：POST https://bbs.hupu.com/pcmapi/pc/bbs/v1/vote
 *   body {"voteId":Int,"sortList":[Int...]}（选项序号数组，int）。
 *   需登录 cookie；成功码 1 或 200；**响应体 data 即投票后的完整状态**（无需二次请求）。
 *
 * 说明：官方提交前会调 `Ea.Xx()`，实测它只是登录门禁（读 ua cookie，未登录跳登录页），
 * 不是风控 token、不进 body；请求无 csrf 头，纯靠 Cookie 会话。
 */
object HupuVoteApi {

    private const val READ_HOST = "https://bbs.mobileapi.hupu.com"
    private const val WRITE_HOST = "https://bbs.hupu.com"
    private const val READ_PATH = "/3/8.0.80/bbsintapi/vote/v1/getVoteInfo"
    private const val WRITE_PATH = "/pcmapi/pc/bbs/v1/vote"

    // 1.169: 共享 client（HupuHttp）——连接池/线程池复用
    private val client: OkHttpClient = HupuHttp.client

    /**
     * 1.124: 会话级投票结果缓存。同帖重进详情页直接以终态票数条首帧渲染
     * （零 loading 跳变）；后台强刷仅在数据变化时写回（data class equals 做 diff）。
     * 断网重进也可用缓存秒开。
     */
    private val voteCache = java.util.concurrent.ConcurrentHashMap<Int, HupuVoteResult>()

    fun cachedVoteInfo(voteId: Int): HupuVoteResult? = voteCache[voteId]

    /** 缓存一次投票结果（读取/提交成功后调用；单测可直接验证）。 */
    fun cacheVoteResult(r: HupuVoteResult) {
        voteCache[r.voteId] = r
    }

    /** 读取投票详情（成功后写入会话缓存）。失败返回 null（不抛）。 */
    suspend fun getVoteInfo(voteId: Int): HupuVoteResult? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url("$READ_HOST$READ_PATH?voteId=$voteId")
                    .header("User-Agent", HupuApi.DESKTOP_UA)
                    .header("Referer", "$WRITE_HOST/")
                    .header("Accept", "application/json, text/plain, */*")
                    .apply {
                        // 汇总数据免登录；带 cookie 才能拿到「我投了哪几项」
                        if (HupuAccount.isLoggedIn) header("Cookie", HupuAccount.loadCookieHeaderPublic())
                    }
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val body = resp.body?.string() ?: return@use null
                    parseVoteResult(body)?.also { cacheVoteResult(it) }
                }
            } catch (e: Exception) {
                null
            }
        }

    /**
     * 提交投票。成功返回 null，并把投票后的最新状态写入 out.first；
     * 失败返回可直接展示的错误文案。
     */
    suspend fun submit(voteId: Int, sortList: List<Int>, out: ArrayList<HupuVoteResult>? = null): String? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val arr = JSONArray()
                sortList.forEach { arr.put(it) }
                val body = JSONObject().put("voteId", voteId).put("sortList", arr)
                val req = Request.Builder()
                    .url("$WRITE_HOST$WRITE_PATH")
                    .header("User-Agent", HupuApi.DESKTOP_UA)
                    .header("Cookie", HupuAccount.loadCookieHeaderPublic())
                    .header("Referer", "$WRITE_HOST/")
                    .header("Origin", WRITE_HOST)
                    .post(body.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                    .build()
                client.newCall(req).execute().use { resp ->
                    val text = resp.body?.string() ?: return@use "网络异常，请稍后再试"
                    val o = try { JSONObject(text) } catch (e: Exception) { return@use "操作失败" }
                    val code = o.optInt("code", -1)
                    if (code == 1 || code == 200) {
                        parseVoteResult(text)?.let { out?.add(it) }
                        null
                    } else {
                        val msg = clean(o.optString("msg"))
                        val message = clean(o.optString("message"))
                        listOf(msg, message).firstOrNull { it.isNotEmpty() }
                            ?: voteErrText(o.optString("internalCode"), code)
                    }
                }
            } catch (e: Exception) {
                "网络异常，请稍后再试"
            }
        }

    private fun clean(s: String): String = if (s == "null" || s == "NULL") "" else s.trim()

    /** 错误码表（实测 2026-09：PC090004="暂无剩余票数"，即已投过） */
    private fun voteErrText(internalCode: String, code: Int): String = when (internalCode) {
        "PC090004" -> "你已经投过票了"
        "PC022003" -> "请先在「我的」页登录"
        else -> "操作失败（code=$code)"
    }

    /**
     * 解析读取 / 提交响应体。要求 code==1|200 且 data 有 voteId，否则返回 null。
     * userVoteRecordList 为 JSON null 时按空列表处理（Android org.json 坑）。
     */
    fun parseVoteResult(json: String): HupuVoteResult? {
        return try {
            val root = JSONObject(json)
            val code = root.optInt("code", -1)
            if (code != 1 && code != 200) return null
            val d = root.optJSONObject("data") ?: return null
            val vid = d.optInt("voteId", 0)
            if (vid <= 0) return null

            val opts = mutableListOf<HupuVoteOption>()
            d.optJSONArray("voteDetailList")?.let { ja ->
                for (i in 0 until ja.length()) {
                    val o = ja.optJSONObject(i) ?: continue
                    opts += HupuVoteOption(
                        sort = o.optInt("sort", i + 1),
                        content = o.optString("content", ""),
                        voteCount = o.optInt("optionVoteCount", 0),
                    )
                }
            }
            val mine = mutableListOf<Int>()
            d.optJSONArray("userVoteRecordList")?.let { ja ->
                for (i in 0 until ja.length()) mine += ja.optInt(i, 0)
            }
            HupuVoteResult(
                voteId = vid,
                title = d.optString("title", ""),
                options = opts,
                totalVotes = d.optInt("voteCount", opts.sumOf { it.voteCount }),
                userCount = d.optInt("userCount", 0),
                limit = d.optInt("userOptionLimit", 1),
                canVote = d.optBoolean("canVote", false),
                ended = d.optBoolean("end", false),
                myChoices = mine,
            )
        } catch (e: Exception) {
            null
        }
    }
}
