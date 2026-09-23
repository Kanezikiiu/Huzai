package com.java.myapplication.data

import android.content.Context
import android.content.SharedPreferences
import android.webkit.CookieManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.java.myapplication.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** 1.133: 上传链路调试日志——仅在 debug 构建输出，正式包静默（不污染 logcat）。 */
private inline fun logUpload(msg: String) {
    if (BuildConfig.DEBUG) android.util.Log.w("HupuUpload", msg)
}

/**
 * 登录会话 + 写端点（点亮/推荐/发评论）。
 *
 * 鉴权体系（2026-09 实测结论，详见聊天探测记录）：
 * 纯 cookie 会话、无签名层、无 CSRF 头要求。写端点：
 * - 推荐   POST /pcmapi/pc/bbs/v1/thread/recommend  {tid, recommendStatus, fid}
 * - 点亮   POST /api/v2/light                        {tid, pid}
 * - 取消点亮 POST /pcmapi/pc/bbs/v1/reply/cancelLight {tid, pid, puid, fid}
 * - 回复   POST /pcmapi/pc/bbs/v1/createReply         {topicId, content, tid}（数美指纹非强制）
 *
 * cookie 从 WebView 登录页（LoginPage）提取，存于应用私有 SharedPreferences，
 * 全程不经手密码。euid/uid 仅用于展示与接口调用。
 */
object HupuAccount {

    data class Profile(val name: String, val avatar: String, val euid: String)

    /** 会话信号：登录态变化时自增，UI 以 remember(sessionVersion) 订阅 */
    var sessionVersion by mutableIntStateOf(0)
        private set

    /** 当前登录用户（null = 未登录） */
    var profile by mutableStateOf<Profile?>(null)
        private set

    val isLoggedIn: Boolean get() = profile != null

    private lateinit var store: SharedPreferences
    // 1.169: 共享 client（HupuHttp）；上传 client 由其派生 → 共用连接池，仅放宽读写超时
    private val client: OkHttpClient = HupuHttp.client
    /** 1.75: 上传专用（大图 PUT 慢，60s 读写超时） */
    private val uploadClient: OkHttpClient = HupuHttp.uploadClient

    /** 发评论最小间隔：写通道信任环境，勿灌水（也防手抖双击重复发） */
    private const val REPLY_MIN_INTERVAL_MS = 3000L
    // 1.169: 回复节流（原子时间片）
    private val replyThrottle = RequestThrottle(REPLY_MIN_INTERVAL_MS)

    private const val BASE = "https://bbs.hupu.com"

    fun init(context: Context) {
        store = context.getSharedPreferences("hupu_account", Context.MODE_PRIVATE)
        // 恢复上次会话的用户信息展示；cookie 若已过期，首次写操作会自然带回错误提示
        val p = store.getString("profile", null)
        if (p != null && loadCookieHeader().isNotEmpty()) {
            runCatching {
                val o = JSONObject(p)
                profile = Profile(o.optString("name"), o.optString("avatar"), o.optString("euid"))
            }
        }
    }

    // ---------- cookie 摄取 / 退出 ----------

    /** 从 WebView 的 CookieManager 取 bbs.hupu.com 域 cookie 并尝试登录 */
    suspend fun ingestFromWebView(): String? {
        val ck = CookieManager.getInstance().getCookie("https://bbs.hupu.com")
            ?: return "未取到登录凭据，请先在页面完成登录"
        return ingestCookie(ck)
    }

    /**
     * 摄取 cookie 字符串。成功返回 null，失败返回原因。
     * 含 u= 与 us= 才初步认定登录态，再经 getReddot 实测校验。
     */
    suspend fun ingestCookie(header: String): String? {
        if (!looksLikeLoginCookie(header)) return "未取到登录凭据，请先在页面完成登录"
        saveCookie(header.trim())
        val prof = fetchProfile()
        if (prof == null) {
            clear()
            return "登录校验失败（cookie 无效或网络异常）"
        }
        profile = prof
        // 1.111: 登录态变化后，此前匿名缓存的 HTML 可能缺登录态字段（点亮、可见性等），清掉重取
        HupuCache.clear()
        // 1.190: 资料卡缓存里有 isSelf 等与会话相关的字段，换账号后必须失效
        hupuUserInfoCache.clear()
        store.edit().putString(
            "profile",
            JSONObject().apply {
                put("name", prof.name)
                put("avatar", prof.avatar)
                put("euid", prof.euid)
            }.toString(),
        ).apply()
        sessionVersion++
        return null
    }

    fun logout() {
        clear()
        profile = null
        sessionVersion++
        // 1.111: 清掉含登录态的内容（私密帖详情等），避免退出后仍能从磁盘缓存读到
        HupuCache.clear()
        // 1.190: 资料卡缓存同样含会话相关字段（isSelf / 私信入口），退出即失效
        hupuUserInfoCache.clear()
        // 清除 WebView 全局 CookieManager 中的虎扑凭据（bbs 会话 + passport SSO）。
        // 不清的话：下次进登录页会被轮询秒命中旧 cookie，且官方页
        // 靠 SSO 自动登回已退出的账号。removeAllCookies 会带上第三方
        // cookie（数美等设备指纹）一并清掉，对本 App 无影响。
        CookieManager.getInstance().apply {
            removeAllCookies(null)
            removeSessionCookies(null)
            flush()
        }
    }

    private fun clear() {
        if (::store.isInitialized) store.edit().clear().apply()
    }

    private fun saveCookie(header: String) {
        store.edit().putString("cookie", header).apply()
    }

    /** 供 HupuApi 直接携带会话 cookie（PC 个人中心 API 需要） */
    fun loadCookieHeaderPublic(): String = if (::store.isInitialized) store.getString("cookie", "") ?: "" else ""

    private fun loadCookieHeader(): String =
        if (::store.isInitialized) store.getString("cookie", "") ?: "" else ""

    /** 纯函数：cookie 串是否带登录凭据（u= 与 us=，单测覆盖） */
    fun looksLikeLoginCookie(header: String): Boolean {
        if (header.isBlank()) return false
        val keys = header.split(';').mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null else it.substring(0, i).trim()
        }
        return keys.any { it == "u" } && keys.any { it == "us" }
    }

    /** 纯函数：评论文本 → 虎扑接受的 HTML 段落（每行一个 <p>，单测覆盖） */
    fun buildReplyContent(text: String): String {
        val paras = text.trim().split('\n').filter { it.isNotBlank() }
        if (paras.isEmpty()) return ""
        return paras.joinToString("") { "<p>${it.trim()}</p>" }
    }

    // ---------- 会话校验 ----------

    private suspend fun fetchProfile(): Profile? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url("$BASE/pcmapi/pc/space/v1/getReddot")
                    .header("User-Agent", HupuApi.DESKTOP_UA)
                    .header("Cookie", loadCookieHeader())
                    .header("Referer", "$BASE/")
                    .header("Accept", "application/json, text/plain, */*")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val o = JSONObject(resp.body?.string() ?: return@use null)
                    if (o.optInt("code") != 1) return@use null
                    val data = o.optJSONObject("data") ?: return@use null
                    val u = data.optJSONObject("userInfo") ?: return@use null
                    Profile(
                        name = u.optString("username"),
                        avatar = u.optString("header"),
                        euid = data.optString("euid"),
                    )
                }
            } catch (e: Exception) {
                null
            }
        }

    // ---------- 写端点（返回 null = 成功，否则为服务端错误信息） ----------

    private fun postJson(url: String, body: JSONObject): JSONObject? {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", HupuApi.DESKTOP_UA)
                .header("Cookie", loadCookieHeader())
                .header("Referer", "$BASE/")
                .header("Origin", "https://bbs.hupu.com")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                JSONObject(resp.body?.string() ?: return@use null)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun errOf(o: JSONObject?): String? {
        if (o == null) return "网络异常，请稍后再试"
        val code = o.optInt("code", -1)
        if (code == 1 || code == 200) return null
        // Android org.json 的 optString 会把 JSON null 强转字符串 "null"（1.7 赛程卡同款坑）；
        // 部分错误响应只有 message（如 light 5003）或两者皆 null（如 PC090003），clean 后拼接、码表兜底
        val msg = cleanStr(o.optString("msg"))
        val message = cleanStr(o.optString("message"))
        return listOf(msg, message).firstOrNull { it.isNotEmpty() } ?: errTextOf(o.optString("internalCode"), code)
    }
    private fun cleanStr(s: String): String = if (s == "null" || s == "NULL") "" else s.trim()
    /** 错误码表（实测 2026-09）：服务端 msg 常为 null，码表兜底 */
    private fun errTextOf(internalCode: String, code: Int): String = when (internalCode) {
        "PC090003" -> "该回复暂无可取消的点亮"
        "PC022002" -> "puid不能为空（数据异常）"
        "AS021999" -> "内容数据出现异常，请稍后再试试"
        "RE012010" -> "请输入回帖内容"
        "PC022003" -> "请先登录"
        "RH020000" -> "回复被屏蔽，请检查内容"
        else -> "操作失败（code=$code)"
    }

    // ---------- 1.126 关注/取关（bbs.mobileapi.hupu.com H5 通道，实测 2026-09） ----------

    /** 当前登录用户的短 puid（cookie 的 u= 首段，形如 u=38357905|...） */
    fun myPuid(): String = puidFromCookie(loadCookieHeader())

    /** 纯函数：从 cookie 串解析登录者短 puid（单测覆盖） */
    fun puidFromCookie(header: String): String {
        if (header.isBlank()) return ""
        val seg = header.split(';')
            .map { it.trim() }
            .firstOrNull { it.startsWith("u=") }
            ?.substringAfter('=')
            ?.substringBefore('|')
            ?: return ""
        return seg.trim()
    }

    private fun postForm(url: String, form: String): JSONObject? {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", HupuApi.MOBILE_UA)
                .header("Cookie", loadCookieHeader())
                .header("Referer", "https://m.hupu.com/")
                .header("X-Requested-With", "XMLHttpRequest")
                .post(form.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                JSONObject(resp.body?.string() ?: return@use null)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 关注类接口统一判定：status=200 且 error 为空视为成功（返回 null）。
     * 成功时 result：2=已关注，1=互相关注，null=已是关注态；取关为 -1。
     * 纯函数，单测覆盖。
     */
    fun followErrOf(o: JSONObject?): String? {
        if (o == null) return "网络异常，请稍后再试"
        val err = cleanStr(o.optString("error"))
        if (err.isNotEmpty()) return err
        if (o.optInt("status", -1) != 200) return "操作失败，请稍后再试"
        return null
    }

    /** 关注用户（成功返回 null）。target 必须是短 puid（euid 无效） */
    suspend fun followUser(target: String): String? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            if (target.isBlank()) return@withContext "用户信息不完整"
            if (!isLoggedIn) return@withContext "请先在「我的」页登录"
            val my = myPuid()
            if (my.isEmpty()) return@withContext "登录已过期，请在「我的」页重新登录"
            followErrOf(
                postForm(
                    "https://bbs.mobileapi.hupu.com/1/8.0.0/user/addFollow?puid=$my&client=",
                    "addPuid=" + java.net.URLEncoder.encode(target, "UTF-8"),
                )
            )
        }

    /** 取消关注（成功返回 null）。target 必须是短 puid */
    suspend fun unfollowUser(target: String): String? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            if (target.isBlank()) return@withContext "用户信息不完整"
            if (!isLoggedIn) return@withContext "请先在「我的」页登录"
            val my = myPuid()
            if (my.isEmpty()) return@withContext "登录已过期，请在「我的」页重新登录"
            followErrOf(
                postForm(
                    "https://bbs.mobileapi.hupu.com/1/8.0.0/user/delFollow?puid=$my&client=",
                    "delPuid=" + java.net.URLEncoder.encode(target, "UTF-8"),
                )
            )
        }

    /** 推荐/取消推荐帖子（on=true 推荐） */
    suspend fun recommend(tid: String, fid: String, on: Boolean): String? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val body = JSONObject()
                .put("tid", tid.toLongOrNull() ?: 0L)
                .put("recommendStatus", if (on) 1 else 0)
                .put("fid", fid.toLongOrNull() ?: 0L)
            errOf(postJson("$BASE/pcmapi/pc/bbs/v1/thread/recommend", body))
        }

    /** 点亮评论。成功或 5003(已点亮过) 时 outState 写入 "lit"，供 UI 自愈本地态；失败返回原因 */
    suspend fun lightReply(tid: String, pid: String, outState: ArrayList<String>? = null): String? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val body = JSONObject()
                .put("tid", tid.toLongOrNull() ?: 0L)
                .put("pid", pid)
            val resp = postJson("$BASE/api/v2/light", body)
            val err = errOf(resp)
            if (err == null || resp?.optInt("code", -1) == 5003) {
                outState?.add("lit")
                null
            } else err
        }
    // ---------- 1.64 games bplcommentapi 写端点（评分体系：打分/取消评分；评分评论点亮/取消点亮） ----------
    // 官方 bundle（bbs-genericscore-opg）：score/save {outBizKey:{outBizType,outBizNo}, score, source:""}（live 实测）；
    // user/record/comment/delete {outBizType, outBizNo, type:"score"}；comment/light & cancelLight {commentKey:{subjectId, commentId}}。
    // 成功码 code==1；匿名返回 401 LOGIN 未登录。subjectId 实测 ≠ outBizNo，必须用评论自带 commentKey.subjectId
    /** 1.147: score/save 的版本段。官方 qU 实例 + 请求拦截器拼 `/{1|4}/{HupuBridge.nainfo.version ?? "8.2.99"}`，
     *  浏览器环境（无 HupuBridge）取默认 8.2.99；写死 8.0.99 会被服务端判「应用版本过旧，请升级到最新版本」。
     */
    internal const val GAMES_SCORE_VERSION = "8.2.99"
    // 1.147: 版本段按官方 live bundle 分实例对齐——publish 走 ZP 固定实例 `/1/8.0.99`、
    // light/cancelLight 走 em() `/9/8.0.99`（保持原值，实测可发）；score/save 见 GAMES_SCORE_VERSION。
    private fun gamesPost(path: String, body: JSONObject, prefix: Int = 1, version: String = "8.0.99"): JSONObject? {
        return try {
            val req = Request.Builder()
                .url("https://games.mobileapi.hupu.com/$prefix/$version/bplcommentapi" + path)
                .header("User-Agent", HupuApi.DESKTOP_UA)
                .header("Cookie", loadCookieHeader())
                .header("Referer", "https://m.hupu.com/score/detail.html")
                .header("Origin", "https://m.hupu.com")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                JSONObject(resp.body?.string() ?: return@use null)
            }
        } catch (e: Exception) {
            null
        }
    }
    private fun gamesErrOf(o: JSONObject?): String? {
        if (o == null) return "网络异常，请稍后再试"
        val code = o.optInt("code", -1)
        if (code == 1 || code == 200) return null
        val t = o.optString("type")
        if (t == "LOGIN" || code == 401) return "请先在「我的」页登录"
        val msg = cleanStr(o.optString("msg"))
        val message = cleanStr(o.optString("message"))
        return listOf(msg, message).firstOrNull { it.isNotEmpty() } ?: "操作失败（code=$code)"
    }

    /** 1.148: 是否命中服务端「应用版本过旧」拦截。 */
    internal fun isVersionTooOld(o: JSONObject?): Boolean {
        if (o == null) return false
        val text = cleanStr(o.optString("msg")) + cleanStr(o.optString("message"))
        return text.contains("版本过旧") || text.contains("升级到最新版本")
    }

    /** 1.148: 版本阶梯——服务端抬高最低版本号时的候选值（不含 base）。
     *  `8.2.99 -> 8.3.99 ... 8.8.99 -> 9.2.99 -> 10.2.99`（按官方习惯：minor 抬高、patch 用 99）。
     */
    internal fun versionLadder(base: String): List<String> {
        val parts = base.split(".")
        if (parts.size != 3) return emptyList()
        val major = parts[0].toIntOrNull() ?: return emptyList()
        val minor = parts[1].toIntOrNull() ?: return emptyList()
        val patch = parts[2]
        val out = ArrayList<String>(8)
        for (i in 1..6) out.add("$major.${minor + i}.$patch")
        out.add("${major + 1}.$minor.$patch")
        out.add("${major + 2}.$minor.$patch")
        return out
    }

    /** 1.148: games 写端点「版本过旧」自愈。
     *  起点 = 本地持久化的版本（若已自愈过）否则代码内置基线；命中拦截后按 [versionLadder] 逐个抬高重试，
     *  首个不再被判过旧的响应即写回本地并返回。由于「版本过旧」发生时请求未被受理（无副作用），重试安全。
     *  body 每次重建（JSONObject 不可复用）。
     */
    private fun gamesWriteHealing(
        path: String,
        prefix: Int,
        family: String,
        baseVersion: String,
        bodyProvider: () -> JSONObject,
    ): JSONObject? {
        val stored = HupuPrefs.gamesVersionOf(family)
        val start = if (stored.isNotEmpty()) stored else baseVersion
        var o = gamesPost(path, bodyProvider(), prefix, start)
        if (!isVersionTooOld(o)) return o
        for (v in versionLadder(start)) {
            val r = gamesPost(path, bodyProvider(), prefix, v)
            if (r != null && !isVersionTooOld(r)) {
                HupuPrefs.saveGamesVersion(family, v)
                return r
            }
            o = r
        }
        return o
    }
    /** 1.147: score/save 请求体（纯函数，便于单测）。
     *  官方 live：`{outBizKey:{outBizType,outBizNo}, score, source:""}`（source 为空串）。
     */
    internal fun buildScoreSaveBody(bizType: String, bizNo: String, score: Int): JSONObject =
        JSONObject()
            .put("outBizKey", JSONObject().put("outBizType", bizType).put("outBizNo", bizNo))
            .put("score", score)
            .put("source", "")

    /** 1.147: comment/m/publish（评分评论回复）请求体（纯函数，便于单测）。
     *  images 必须是 URL 字符串数组：传对象元素服务端会返回
     *  `JSON parse error: Cannot deserialize value of type java.lang.String from Object value`。
     */
    internal fun buildScorePublishBody(
        bizType: String,
        bizNo: String,
        content: String,
        parentCommentId: String = "",
        subjectId: String = "",
        images: List<String> = emptyList(),
    ): JSONObject {
        val body = JSONObject()
            .put("content", content)
            .put("outBizKey", JSONObject().put("outBizType", bizType).put("outBizNo", bizNo))
            .put("subjectId", subjectId)
            .put("source", "m")
        if (parentCommentId.isNotEmpty()) body.put("parentCommentId", parentCommentId)
        if (images.isNotEmpty()) {
            val arr = JSONArray()
            images.forEach { u -> arr.put(u) }
            body.put("images", arr)
        }
        return body
    }

    /** 评分对象打分（10 制整数 1..10）。成功返回 null
     *  1.147: 对齐官方 live 调用——实例 qU（动态版本 8.2.99）、payload `{outBizKey, score, source:""}`
     *  （原 source="games_front" + 固定 8.0.99 会触发「应用版本过旧，请升级到最新版本」）。
     */
    suspend fun scoreSave(bizType: String, bizNo: String, score: Int): String? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            gamesErrOf(
                gamesWriteHealing("/bpl/score/save", 1, "score", GAMES_SCORE_VERSION) {
                    buildScoreSaveBody(bizType, bizNo, score)
                }
            )
        }
    /** 取消评分（移除当前用户对该评分对象的评分）。成功返回 null */
    suspend fun scoreDelete(bizType: String, bizNo: String): String? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            gamesErrOf(
                gamesWriteHealing("/bpl/user/record/comment/delete", 1, "score", "8.0.99") {
                    JSONObject().put("outBizType", bizType).put("outBizNo", bizNo).put("type", "score")
                }
            )
        }
    /** 评分评论发布/回复（bplcommentapi comment/m/publish）。
     * 回复评论（母/子/孙）：parentCommentId = 被回复评论 commentId、subjectId = 该评论自带 subjectId
     * （官方实测必须用评论自身的 commentKey.subjectId，≠ outBizNo）。
     * images：已上传图片 URL 列表（1.147 实测修正：服务端期望 **字符串数组**，
     * 传对象元素会直接 `JSON parse error: Cannot deserialize value of type java.lang.String from Object value`；
     * 官方 bundle 里 commentContentImages.map(->{commentContentId,commentContent,commentContentType}) 只是本地乐观渲染对象，
     * 不是请求体。URL 来自 uploadReplyImage 上传链路）。
     * 成功返回 null，失败返回提示文案。
     */
    suspend fun publishScoreComment(
        bizType: String,
        bizNo: String,
        content: String,
        parentCommentId: String = "",
        subjectId: String = "",
        images: List<String> = emptyList(),
    ): ScorePublishResult =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val body = buildScorePublishBody(bizType, bizNo, content, parentCommentId, subjectId, images)
            val o = gamesWriteHealing("/bpl/comment/m/publish", 1, "publish", "8.0.99") { body }
            val err = gamesErrOf(o)
            if (err != null) return@withContext ScorePublishResult(err)
            // 1.150: 官方回包 data.commentId / data.subjectId（bundle: C=g.data; I.commentId / I.subjectId）——
            // 取真实 id 建乐观条目，服务端稍后返回同一条时按 id 去重，不再出现「两条我的回复」
            val data = o?.optJSONObject("data")
            ScorePublishResult(
                error = null,
                commentId = data?.optString("commentId").orEmpty(),
                subjectId = data?.optString("subjectId").orEmpty(),
            )
        }

    /** 评分评论发布结果：error==null 即成功；commentId/subjectId 为服务端返回的新评论标识（可能为空串）。 */
    data class ScorePublishResult(
        val error: String?,
        val commentId: String = "",
        val subjectId: String = "",
    )
    /** 评分评论点亮/取消（subjectId = 评论 commentKey.subjectId）。成功返回 null，outState 写 lit/unlit 自愈 */
    suspend fun scoreLight(subjectId: String, commentId: String, on: Boolean, outState: ArrayList<String>? = null): String? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val key = JSONObject().put("subjectId", subjectId).put("commentId", commentId)
            val body = JSONObject().put("commentKey", key)
            // 1.65 修复：官方浏览器环境（isHupu=false）下 light/cancelLight 走 /9/ 版本前缀
            // （bundle em()：ed = isHupu ? 1 : 9）；score/save 实例固定 /1/，故 1.64 打分成功而点亮失败
            val err = gamesErrOf(
                gamesWriteHealing(
                    if (on) "/bpl/comment/light" else "/bpl/comment/cancelLight",
                    9, "light", "8.0.99"
                ) { body }
            )
            if (err == null) {
                outState?.add(if (on) "lit" else "unlit")
                null
            } else err
        }

    // ---------- 1.67 帖子云端收藏（官方 PC 端点：POST/DELETE/GET /api/v2/threads/{tid}/collect） ----------
    // ---------- 1.68 收藏态本地持久化（官方 PC 无查询接口；本地 tid 集为真源，登出即清） ----------
    private fun collectSet(): MutableSet<String> {
        if (!::store.isInitialized) return mutableSetOf()
        return store.getStringSet("collected_tids", emptySet())?.toMutableSet() ?: mutableSetOf()
    }
    /** 本地是否已收藏（打开帖子秒显，无网络） */
    fun isCollectedLocal(tid: String): Boolean = collectSet().contains(tid)
    private fun saveCollectSet(s: Set<String>) {
        if (!::store.isInitialized) return
        store.edit().putStringSet("collected_tids", s).apply()
    }
    /** 收藏/取消成功后同步本地持久集 */
    fun setCollectedLocal(tid: String, on: Boolean) {
        val s = collectSet()
        if (on) s.add(tid) else s.remove(tid)
        saveCollectSet(s)
    }
    /** 查询当前用户对该帖子的收藏态。未登录/失败一律返回 false（静默，不提示） */
    /** 三态: true=云端已收藏, false=云端未收藏, null=查询失败/未知(调用方应保留本地态) */
    suspend fun collectState(tid: String): Boolean? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            if (!isLoggedIn) return@withContext null
            val o = try {
                val req = Request.Builder()
                    .url("$BASE/api/v2/threads/$tid/collect")
                    .header("User-Agent", HupuApi.DESKTOP_UA)
                    .header("Cookie", loadCookieHeader())
                    .header("Referer", "$BASE/")
                    .header("Origin", "https://bbs.hupu.com")
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    JSONObject(resp.body?.string() ?: return@use null)
                }
            } catch (e: Exception) { null }
            val code = o?.optInt("code", -1) ?: -1
            // 官方判定：code==200 || code==1 为成功；data 可能是 {isFavor} / true / null
            // 官方 PC 组件从不预查(w=useState(false))，GET 返回体不可靠 → 无法解析时一律返回 null(未知)
            if (code != 1 && code != 200) return@withContext null
            val data = o?.opt("data")
            when (data) {
                is Boolean -> data
                is JSONObject -> if (data.has("isFavor") || data.has("collected"))
                    data.optBoolean("isFavor", data.optBoolean("collected", false)) else null
                else -> null  // 成功但无 data 字段：视为未知，不覆盖本地
            }
        }
    /** 收藏/取消收藏帖子（on=true 收藏）。成功返回 null */
    suspend fun threadCollect(tid: String, on: Boolean): String? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val url = "$BASE/api/v2/threads/$tid/collect"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", HupuApi.DESKTOP_UA)
                .header("Cookie", loadCookieHeader())
                .header("Referer", "$BASE/")
                .header("Origin", "https://bbs.hupu.com")
                .apply {
                    if (on) post(JSONObject().toString().toRequestBody("application/json".toMediaType()))
                    else delete()
                }
                .build()
            val o = try {
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    JSONObject(resp.body?.string() ?: return@use null)
                }
            } catch (e: Exception) { null }
            val err = errOf(o)
            if (err == null) setCollectedLocal(tid, on)  // 云端成功 → 本地持久集同步
            err
        }
    /** 取消点亮（puid = 被点亮楼层作者的 uid）。成功或 PC090003(本就未点亮) 时 outState 写入 "unlit" 自愈 */
    suspend fun cancelLightReply(tid: String, pid: String, puid: String, fid: String, outState: ArrayList<String>? = null): String? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val body = JSONObject()
                .put("tid", tid.toLongOrNull() ?: 0L)
                .put("pid", pid)
                .put("puid", puid)
                .put("fid", fid.toLongOrNull() ?: 0L)
            val resp = postJson("$BASE/pcmapi/pc/bbs/v1/reply/cancelLight", body)
            val err = errOf(resp)
            if (err == null || resp?.optString("internalCode") == "PC090003") {
                outState?.add("unlit")
                null
            } else err
        }

    /** 回复帖子（纯文本，自动包成 <p> 段落）。成功返回 null，失败返回原因 */
    /**
     * 发回复。返回 null = 成功，新楼层 pid 写入 outPid（供乐观插入与 pid
     * 去重用）；否则返回错误信息。
     */
    /**
     * 发回复。quoteId=0 回复主楼；传楼层/子回复 pid 即回复该楼（官方楼中楼实现，
     * 实测 2025-09：quoteId 引用后回复挂到被引用楼的楼中楼，quote 为空）。
     * 返回 null = 成功，新 pid 写入 outPid；否则返回错误信息。
     */
    // ---------- 1.73 HSS 图片上传（官方 PC SDK chunk384/95146 协议） ----------
    private const val HSS_BASE = "https://hss.hupu.com"
    private const val HSS_APP_ID = "sHCGmnf6Q22giqt5BD8dvZY8lB4="
    private const val HSS_SK = "tsB7gwSsXPo9UTtSYFcPdtfckis="
    private const val HSS_MODULE = "reply-oss"
    private const val HSS_PATH = "/reply"

    private fun md5Hex(data: ByteArray): String {
        val dig = java.security.MessageDigest.getInstance("MD5").digest(data)
        return dig.joinToString("") { "%02x".format(it) }
    }
    private fun hmacSha1B64Url(data: String, key: String): String {
        val mac = javax.crypto.Mac.getInstance("HmacSHA1")
        mac.init(javax.crypto.spec.SecretKeySpec(key.toByteArray(), "HmacSHA1"))
        val raw = mac.doFinal(data.toByteArray())
        val b64 = android.util.Base64.encodeToString(raw, android.util.Base64.NO_WRAP)
        return b64.replace('+', '-').replace('/', '_')
    }

    /**
     * 上传一张图片，成功返回 CDN URL，失败返回 null。
     * 官方链路: fileHash(MD5) -> GET credentials(7字段签名, width/height附加) ->
     *           OSS PUT(objectKey, bytes) -> POST uploadStatus{fileHash} -> fileSrc
     */
    /** 1.75: 上传结果——url 成功；error 失败原因(步骤+HTTP码)，直接进 toast */
    data class UploadResult(val url: String?, val error: String? = null)
    /** 1.94: 下载网络图片字节（收藏表情包 -> 作为图片随回复上传） */
    suspend fun downloadImageBytes(url: String): ByteArray? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                    if (r.isSuccessful) r.body?.bytes() else null
                }
            }.getOrNull()
        }

    // 1.162: 动图「发送后探测 / 自适应交付」整段已按产品决定移除
    // （服务端转不出 GIF，该路径无收益，且发送时多做两次网络请求不划算）。
    // 上传侧的动图保真（HupuImage.prepareForUpload / isAnimated）保持不变。


    suspend fun uploadReplyImage(data: ByteArray, ext: String, width: Int, height: Int): UploadResult =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val fileHash = md5Hex(data)
                // 签名集(官方7字段): action/appId/[extension]/fileHash/module/path/timestamp
                val sp = LinkedHashMap<String, String>()
                sp["action"] = "1"
                sp["appId"] = HSS_APP_ID
                if (ext == "jpeg" || ext == "png" || ext == "gif" || ext == "webp") sp["extension"] = ext
                sp["fileHash"] = fileHash
                sp["module"] = HSS_MODULE
                sp["path"] = HSS_PATH
                sp["timestamp"] = System.currentTimeMillis().toString()
                val signStr = sp.entries.sortedBy { it.key }.joinToString("&") { it.key + "=" + it.value }
                val hssSign = hmacSha1B64Url(signStr, HSS_SK)
                // query: 签名字段 + 附加 width/height + hss_sign（官方是 params merge dims）
                val qp = LinkedHashMap<String, String>(sp)
                if (width > 0) qp["width"] = width.toString()
                if (height > 0) qp["height"] = height.toString()
                qp["hss_sign"] = hssSign
                val q = qp.entries.joinToString("&") {
                    java.net.URLEncoder.encode(it.key, "UTF-8") + "=" + java.net.URLEncoder.encode(it.value, "UTF-8")
                }
                val credReq = Request.Builder()
                    .url(HSS_BASE + "/kaleido/hss/app/file/credentials?" + q)
                    .header("User-Agent", HupuApi.DESKTOP_UA)
                    .header("Cookie", loadCookieHeader())
                    .header("Referer", "https://bbs.hupu.com/")
                    .get().build()
                val cred = uploadClient.newCall(credReq).execute().use { r ->
                    logUpload("cred http=" + r.code)
                    if (!r.isSuccessful) {
                        if (r.code == 401) return@withContext UploadResult(null, "登录已过期，请在「我的」页重新登录")
                        return@withContext UploadResult(null, "凭证请求失败 HTTP " + r.code)
                    }
                    JSONObject(r.body?.string() ?: return@use null)
                } ?: return@withContext UploadResult(null, "凭证响应异常")
                val d = cred.optJSONObject("data") ?: return@withContext UploadResult(null, "凭证响应缺 data")
                // 1.77 对齐官方 SDK: status != "processing" 一律是捷径响应(秒传/复用),
                // 直接采用 fileSrc, 不 PUT、不调 uploadStatus。仅 processing 才需要 objectKey/STS。
                val cSt = d.optString("status")
                if (cSt != "processing") {
                    val ex = d.optString("fileSrc").takeIf { it.isNotEmpty() }
                    if (ex != null) {
                        logUpload("fast path, status=" + cSt)
                        return@withContext UploadResult(ex)
                    }
                    return@withContext UploadResult(null, "捷径响应缺地址: status=" + cSt)
                }
                logUpload("cred data ok, status=" + d.optString("status"))
                val objectKey = d.optString("objectKey")
                if (objectKey.isEmpty()) {
                    val cKeys = d.keys().asSequence().joinToString(",")
                    return@withContext UploadResult(null, "凭证缺 objectKey: status=" + cSt + " keys=[" + cKeys + "]")
                }
                // OSS PUT (v1 签名: PUT/n/n/n Date x-oss-security-token /bucket/key)
                val media = ("image/" + ext).toMediaType()
                // 1.76 根修(RequestTimeTooSkewed): Date 必须真 GMT——
                // 此前按设备本地时区(UTC+8)格式化却拼 'GMT' 字面, 比真实 GMT 快 8h,
                // OSS ±15min 校验必拒。显式设 GMT 时区。
                val gmtFmt = java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", java.util.Locale.US)
                gmtFmt.timeZone = java.util.TimeZone.getTimeZone("GMT")
                val date = gmtFmt.format(java.util.Date())
                // 1.74 根修: Content-Type 必须参与签名（OkHttp 会随 body 发出该头, 签名串缺它必 403）
                val ossStr = "PUT" + "\n" + "\n" + media.toString() + "\n" + date + "\nx-oss-security-token:" + d.optString("token") +
                    "\n/" + d.optString("bucket") + "/" + objectKey
                val mac = javax.crypto.Mac.getInstance("HmacSHA1")
                mac.init(javax.crypto.spec.SecretKeySpec(d.optString("secretKey").toByteArray(), "HmacSHA1"))
                val sig = android.util.Base64.encodeToString(mac.doFinal(ossStr.toByteArray()), android.util.Base64.NO_WRAP)
                val putUrl = "https://" + d.optString("bucket") + "." + d.optString("region") + ".aliyuncs.com/" + objectKey
                val putReq = Request.Builder()
                    .url(putUrl)
                    .header("Date", date)
                    .header("x-oss-security-token", d.optString("token"))
                    .header("Authorization", "OSS " + d.optString("accessKey") + ":" + sig)
                    .put(data.toRequestBody(media))
                    .build()
                uploadClient.newCall(putReq).execute().use { r ->
                    logUpload("put http=" + r.code)
                    if (!r.isSuccessful) {
                        val errBody = runCatching { r.body?.string()?.take(400) }.getOrNull() ?: ""
                        logUpload("put body: " + errBody)
                        val ossErr = Regex("<Code>([^<]+)</Code>").find(errBody)?.groupValues?.get(1)
                            ?: ("HTTP " + r.code)
                        return@withContext UploadResult(null, "OSS上传失败: " + ossErr)
                    }
                }
                // uploadStatus -> fileSrc
                val stBody = JSONObject().put("fileHash", fileHash)
                val stReq = Request.Builder()
                    .url(HSS_BASE + "/kaleido/hss/uploadStatus")
                    .header("User-Agent", HupuApi.DESKTOP_UA)
                    .header("Cookie", loadCookieHeader())
                    .header("Referer", "https://bbs.hupu.com/")
                    .post(stBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                val st = uploadClient.newCall(stReq).execute().use { r ->
                    logUpload("status http=" + r.code)
                    if (!r.isSuccessful) return@withContext UploadResult(null, "登记失败 HTTP " + r.code)
                    JSONObject(r.body?.string() ?: return@use null)
                }
                val dd = st?.optJSONObject("data")
                val src = (dd?.optJSONObject("data")?.optString("fileSrc") ?: dd?.optString("fileSrc"))?.takeIf { it.isNotEmpty() }
                logUpload("status fileSrc=" + (src ?: "null"))
                if (src == null) return@withContext UploadResult(null, "未获取到图片地址")
                UploadResult(src)
            } catch (e: Exception) {
                logUpload("ex: " + e.message)
                UploadResult(null, "上传异常: " + (e.message ?: e.javaClass.simpleName))
            }
        }

    /** 1.73 回复 content 组装: 文本(含表情token)按行 <p> 包裹 + 图片 <img> 列表 */
    fun buildReplyContentRich(text: String, imageUrls: List<String>): String {
        val paras = text.trim().split("\n").filter { it.isNotBlank() }
        if (paras.isEmpty() && imageUrls.isEmpty()) return ""
        return paras.joinToString("") { "<p>" + it.trim() + "</p>" } +
            imageUrls.joinToString("") { "<img src=\"" + it + "\" />" }
    }

    suspend fun createReply(tid: String, topicId: String, fid: String, text: String, outPid: ArrayList<String>, quoteId: String = "0", imageUrls: List<String> = emptyList()): String? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val html = buildReplyContentRich(text, imageUrls)
            if (html.isEmpty()) return@withContext "内容不能为空"
            replyThrottle.acquire()
            val body = JSONObject()
                .put("topicId", topicId)
                .put("fid", fid.toLongOrNull() ?: 0L)
                .put("quoteId", quoteId.toLongOrNull() ?: 0L)
                .put("content", html)
                .put("shumeiId", "")
                .put("deviceid", "")
                .put("tid", tid)
            val resp = postJson("$BASE/pcmapi/pc/bbs/v1/createReply", body)
            val err = errOf(resp)
            if (err == null) {
                val pid = resp?.optJSONObject("data")?.optString("pid")?.takeIf { it.isNotEmpty() }
                if (pid != null) outPid.add(pid)
            }
            err
        }

    // ---------- 1.110 视频上传（module=editor-video-oss / path=/editor，官方仅支持 mp4） ----------
    private const val HSS_VIDEO_MODULE = "editor-video-oss"
    private const val HSS_VIDEO_PATH = "/editor"

    /** 流式请求体：从 content Uri 边读边发（视频几十 MB 不进内存），带进度回调 */
    private class UriRequestBody(
        private val resolver: android.content.ContentResolver,
        private val uri: android.net.Uri,
        private val mediaType: okhttp3.MediaType,
        private val size: Long,
        private val onProgress: ((Long, Long) -> Unit)?,
    ) : okhttp3.RequestBody() {
        override fun contentType(): okhttp3.MediaType = mediaType

        override fun contentLength(): Long = size

        override fun writeTo(sink: okio.BufferedSink) {
            val ins = resolver.openInputStream(uri) ?: throw java.io.IOException("无法读取视频文件")
            ins.use { input ->
                val buf = ByteArray(256 * 1024)
                var written = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    sink.write(buf, 0, n)
                    written += n
                    if (size > 0) onProgress?.invoke(written, size)
                }
            }
        }
    }

    /**
     * 1.110: 上传视频（mp4），返回播放地址。
     *
     * 链路与图片完全同源（fileHash -> credentials -> OSS PUT -> uploadStatus -> fileSrc），
     * 仅 module/path 换成 editor-video-oss；实测凭证桶为 hupu-v-dump、objectKey 形如 editor/xxx.mp4。
     * onProgress(已上传字节, 总字节)。
     */
    suspend fun uploadVideo(
        context: android.content.Context,
        uri: android.net.Uri,
        onProgress: ((Long, Long) -> Unit)? = null,
    ): UploadResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val size = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
            // 1) fileHash = 文件 MD5（流式，避免整段入内存）
            val digest = java.security.MessageDigest.getInstance("MD5")
            resolver.openInputStream(uri)?.use { ins ->
                val buf = ByteArray(256 * 1024)
                while (true) {
                    val n = ins.read(buf)
                    if (n <= 0) break
                    digest.update(buf, 0, n)
                }
            } ?: return@withContext UploadResult(null, "无法读取视频文件")
            val fileHash = digest.digest().joinToString("") { "%02x".format(it) }
            // 2) credentials（与图片同款 7 字段签名）
            val sp = LinkedHashMap<String, String>()
            sp["action"] = "1"
            sp["appId"] = HSS_APP_ID
            sp["extension"] = "mp4"
            sp["fileHash"] = fileHash
            sp["module"] = HSS_VIDEO_MODULE
            sp["path"] = HSS_VIDEO_PATH
            sp["timestamp"] = System.currentTimeMillis().toString()
            val signStr = sp.entries.sortedBy { it.key }.joinToString("&") { it.key + "=" + it.value }
            val qp = LinkedHashMap<String, String>(sp)
            qp["hss_sign"] = hmacSha1B64Url(signStr, HSS_SK)
            val q = qp.entries.joinToString("&") {
                java.net.URLEncoder.encode(it.key, "UTF-8") + "=" + java.net.URLEncoder.encode(it.value, "UTF-8")
            }
            val credReq = Request.Builder()
                .url(HSS_BASE + "/kaleido/hss/app/file/credentials?" + q)
                .header("User-Agent", HupuApi.DESKTOP_UA)
                .header("Cookie", loadCookieHeader())
                .header("Referer", "https://bbs.hupu.com/newpost")
                .get().build()
            val cred = uploadClient.newCall(credReq).execute().use { r ->
                if (!r.isSuccessful) return@withContext UploadResult(null, "视频凭证请求失败 HTTP " + r.code)
                JSONObject(r.body?.string() ?: return@use null)
            } ?: return@withContext UploadResult(null, "视频凭证响应异常")
            val d = cred.optJSONObject("data") ?: return@withContext UploadResult(null, "视频凭证缺 data")
            d.optString("fileSrc").takeIf { it.isNotEmpty() }?.let { return@withContext UploadResult(it) }
            val objectKey = d.optString("objectKey")
            if (objectKey.isEmpty()) return@withContext UploadResult(null, "视频凭证缺 objectKey")
            // 3) OSS PUT（v1 签名；Date 必须真 GMT；Content-Type 参与签名）
            val media = "video/mp4".toMediaType()
            val gmtFmt = java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", java.util.Locale.US)
            gmtFmt.timeZone = java.util.TimeZone.getTimeZone("GMT")
            val date = gmtFmt.format(java.util.Date())
            val ossStr = "PUT\n\n" + media.toString() + "\n" + date +
                "\nx-oss-security-token:" + d.optString("token") +
                "\n/" + d.optString("bucket") + "/" + objectKey
            val mac = javax.crypto.Mac.getInstance("HmacSHA1")
            mac.init(javax.crypto.spec.SecretKeySpec(d.optString("secretKey").toByteArray(), "HmacSHA1"))
            val sig = android.util.Base64.encodeToString(mac.doFinal(ossStr.toByteArray()), android.util.Base64.NO_WRAP)
            val putUrl = "https://" + d.optString("bucket") + "." + d.optString("region") + ".aliyuncs.com/" + objectKey
            val putReq = Request.Builder()
                .url(putUrl)
                .header("Date", date)
                .header("x-oss-security-token", d.optString("token"))
                .header("Authorization", "OSS " + d.optString("accessKey") + ":" + sig)
                .put(UriRequestBody(resolver, uri, media, size, onProgress))
                .build()
            uploadClient.newCall(putReq).execute().use { r ->
                if (!r.isSuccessful) {
                    val errBody = runCatching { r.body?.string()?.take(300) }.getOrNull() ?: ""
                    val ossErr = Regex("<Code>([^<]+)</Code>").find(errBody)?.groupValues?.get(1)
                        ?: ("HTTP " + r.code)
                    return@withContext UploadResult(null, "视频上传失败: " + ossErr)
                }
            }
            // 4) uploadStatus -> fileSrc（视频桶可能要等处理，最多轮询 6 次）
            var src: String? = null
            for (attempt in 0 until 6) {
                val stReq = Request.Builder()
                    .url(HSS_BASE + "/kaleido/hss/uploadStatus")
                    .header("User-Agent", HupuApi.DESKTOP_UA)
                    .header("Cookie", loadCookieHeader())
                    .header("Referer", "https://bbs.hupu.com/newpost")
                    .post(JSONObject().put("fileHash", fileHash).toString().toRequestBody("application/json".toMediaType()))
                    .build()
                val st = uploadClient.newCall(stReq).execute().use { r ->
                    if (!r.isSuccessful) return@withContext UploadResult(null, "视频登记失败 HTTP " + r.code)
                    JSONObject(r.body?.string() ?: return@use null)
                }
                val dd = st?.optJSONObject("data")
                src = dd?.optJSONObject("data")?.optString("fileSrc")?.takeIf { it.isNotEmpty() }
                    ?: dd?.optString("fileSrc")?.takeIf { it.isNotEmpty() }
                    ?: dd?.optString("videoUrl")?.takeIf { it.isNotEmpty() }
                if (src != null) break
                // 1.169: 轮询等待改用挂起 delay（不占用线程），语义与原先的 sleep 一致
                kotlinx.coroutines.delay(1500)
            }
            if (src == null) return@withContext UploadResult(null, "未获取到视频地址（可能仍在转码）")
            UploadResult(src)
        } catch (e: Exception) {
            UploadResult(null, "视频上传异常: " + (e.message ?: e.javaClass.simpleName))
        }
    }
}