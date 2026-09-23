package com.java.myapplication.data

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * 虎扑网页版 HTTP 封装
 * 关键点：必须使用桌面 UA（移动 UA 会返回 CSR 空壳页）
 * 详见 docs/hupu_web_probe.md
 */
object HupuApi {

    private const val BASE = "https://bbs.hupu.com"

    const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    // 移动 UA：用户主页 SSR 仅在移动形态下返回完整 NEXT_DATA（实测 probe_user8/9）
    const val MOBILE_UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    // 1.169: 使用全局共享 client（见 HupuHttp）——不再各处独立 new，连接池/线程池可复用
    private val client: OkHttpClient = HupuHttp.client

    /** 1.169: 全局请求节流（原子时间片，替代原先复制多份的「先检查后设置 + sleep」） */
    private const val MIN_INTERVAL_MS = 1000L
    private val throttle = RequestThrottle(MIN_INTERVAL_MS)

    /**
     * 拉取楼中楼 JSON（m.hupu.com/api/v2/bbs-reply-detail/{tid}-{pid}）。
     * 注意：此接口在 bbs.hupu.com 域名下 404，必须走 m.hupu.com。
     * maxpid 为翻页游标（上一页最后一条子回复的 pid），首页不传。
     */
    suspend fun fetchFloorReplies(tid: String, pid: String, maxpid: String? = null): String? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            throttle.acquire()

            val url = StringBuilder("https://m.hupu.com/api/v2/bbs-reply-detail/$tid-$pid")
            if (!maxpid.isNullOrEmpty()) url.append("?maxpid=").append(maxpid)

            val request = Request.Builder()
                .url(url.toString())
                .header("User-Agent", DESKTOP_UA)
                .header("Accept", "application/json, text/plain, */*")
                .header("Referer", "https://m.hupu.com/bbs/$tid.html")
                .apply {
                    // 1.111: 私密帖的楼中楼同样需要登录态才可读
                    if (HupuAccount.isLoggedIn) {
                        header("Cookie", HupuAccount.loadCookieHeaderPublic())
                    }
                }
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    response.body?.string()
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * 用户主页 HTML（m.hupu.com/user/{euid}，移动 UA）。
     * 用户页 SSR 仅在移动形态下完整；小体积壳页保护改为检查 __NEXT_DATA__ 载体。
     * 缓存与 bbs 页共用一套磁盘（path 已含域语义，不会与 /tid.html 冲突）。
     */
    /**
     * PC 个人中心 API（bbs.hupu.com/pcmapi/pc/space/v1 端点家族）。
     * 需要登录 cookie（bbs 域 SSO），未登录时端点返回 code=0 / data=null。
     * 实测：getUserInfo/getThreadList/getReplyList/getRecommendList/getFavoritesList/getUserFollowList。
     */
    suspend fun fetchSpaceApi(path: String, fast: Boolean = false): String? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // 1.190: fast=true 走导航快车道（200ms），用于「用户主页资料卡」这类用户主动触发的请求
                if (fast) HupuHttp.navThrottle.acquire() else throttle.acquire()
                val request = okhttp3.Request.Builder()
                    .url("https://bbs.hupu.com/pcmapi/pc/space/v1/$path")
                    .header("User-Agent", DESKTOP_UA)
                    .header("Cookie", HupuAccount.loadCookieHeaderPublic())
                    .header("Referer", "https://my.hupu.com/")
                    .header("Accept", "application/json, text/plain, */*")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    response.body?.string()
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * 1.126: 关注列表 JSON（官方 H5 通道 bbs.mobileapi.hupu.com）。
     * GET /1/8.0.0/user/getUserFollow?puid={短puid}&client=&page=N
     * 返回 {"status":200,"result":{"nextPage":bool,"puid":"<当前登录者>","author_puid":"<被查者>","list":[{puid,username,header,level}]}}
     * 注意：puid 必须传短 puid（93956731 这种）；传 euid 不报错但静默返回空列表。
     * 每页固定 20 条，pageSize 无效，翻页看 nextPage。
     */
    suspend fun fetchUserFollow(puid: String, page: Int): String? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                throttle.acquire()
                val request = Request.Builder()
                    .url("https://bbs.mobileapi.hupu.com/1/8.0.0/user/getUserFollow?puid=$puid&client=&page=$page")
                    .header("User-Agent", MOBILE_UA)
                    .header("Cookie", HupuAccount.loadCookieHeaderPublic())
                    .header("Referer", "https://m.hupu.com/")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Accept", "application/json, text/plain, */*")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    response.body?.string()
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * 1.99: PC 个人中心 JSON POST 通道（私信 pm/getPmList / pm/getPmDetail / sendPm）。
     * body 为已序列化的 JSON 字符串。
     */
    suspend fun postSpaceApiJson(path: String, jsonBody: String): String? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                throttle.acquire()
                val media = "application/json; charset=utf-8".toMediaTypeOrNull()
                val request = okhttp3.Request.Builder()
                    .url("https://bbs.hupu.com/pcmapi/pc/space/v1/$path")
                    .post(jsonBody.toRequestBody(media))
                    .header("User-Agent", DESKTOP_UA)
                    .header("Cookie", HupuAccount.loadCookieHeaderPublic())
                    .header("Referer", "https://my.hupu.com/personalMessage")
                    .header("Accept", "application/json, text/plain, */*")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    response.body?.string()
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * 1.107: 发帖 JSON POST 通道（bbs.hupu.com/pcmapi/pc/bbs/v1 端点家族）。
     * 实测：createThread（发帖）/ editThread（编辑）/ delete/threads/{tid}（删除）。
     * 注意 Referer 必须是 newpost 页，服务端会据此校验来源。
     */
    suspend fun postBbsApiJson(path: String, jsonBody: String): String? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                throttle.acquire()
                val media = "application/json; charset=utf-8".toMediaTypeOrNull()
                val request = okhttp3.Request.Builder()
                    .url("https://bbs.hupu.com/pcmapi/pc/bbs/v1/$path")
                    .post(jsonBody.toRequestBody(media))
                    .header("User-Agent", DESKTOP_UA)
                    .header("Cookie", HupuAccount.loadCookieHeaderPublic())
                    .header("Referer", "https://bbs.hupu.com/newpost")
                    .header("Accept", "application/json, text/plain, */*")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    response.body?.string()
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * 1.113: 创建投票（POST https://bbs.hupu.com/api/v1/votes）。
     * 官方编辑器配置 voteUrl="/api/v1/votes"（相对当前页 bbs.hupu.com）。
     * 注意：成功码是 **200**（不是发帖家族的 1），voteId 在 data.voteId。
     */
    suspend fun postVotesJson(jsonBody: String): String? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                throttle.acquire()
                val media = "application/json; charset=utf-8".toMediaTypeOrNull()
                val request = okhttp3.Request.Builder()
                    .url("$BASE/api/v1/votes")
                    .post(jsonBody.toRequestBody(media))
                    .header("User-Agent", DESKTOP_UA)
                    .header("Cookie", HupuAccount.loadCookieHeaderPublic())
                    .header("Referer", "https://bbs.hupu.com/newpost")
                    .header("Accept", "application/json, text/plain, */*")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    response.body?.string()
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * 1.107: 发帖辅助 GET 通道（topic/cates、topic/search、topicZone）。
     * pathWithQuery 以 / 开头，如 "/pcmapi/pc/bbs/v1/topic/cates"。
     */
    suspend fun getBbsJson(pathWithQuery: String): String? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                throttle.acquire()
                val request = okhttp3.Request.Builder()
                    .url("https://bbs.hupu.com$pathWithQuery")
                    .header("User-Agent", DESKTOP_UA)
                    .header("Cookie", HupuAccount.loadCookieHeaderPublic())
                    .header("Referer", "https://bbs.hupu.com/newpost")
                    .header("Accept", "application/json, text/plain, */*")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    response.body?.string()
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun fetchUserHtml(euid: String, forceNetwork: Boolean = false): String? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            throttle.acquire()
            val path = "/user/$euid"
            if (!forceNetwork) {
                HupuCache.get(path)?.let { return@withContext it }
            }
            val request = Request.Builder()
                .url("https://m.hupu.com$path")
                .header("User-Agent", MOBILE_UA)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .apply {
                    if (HupuAccount.isLoggedIn) {
                        header("Cookie", HupuAccount.loadCookieHeaderPublic())
                    }
                }
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val html = response.body?.string() ?: return@use null
                    if (!html.contains("__NEXT_DATA__")) return@use null
                    HupuCache.put(path, html)
                    html
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * 拉取页面 HTML。返回 null 表示失败。
     * forceNetwork = true 时跳过缓存直接请求（下拉刷新用）
     */
    suspend fun fetchHtml(path: String, forceNetwork: Boolean = false): String? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            // 节流
            throttle.acquire()

            // 先查缓存
            if (!forceNetwork) {
                HupuCache.get(path)?.let { return@withContext it }
            }

            val request = Request.Builder()
                .url("$BASE$path")
                .header("User-Agent", DESKTOP_UA)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .header("Referer", "$BASE/")
                .apply {
                    // 1.111: 登录态下带上 Cookie。
                    // 必要场景：「仅自己可见」的帖子匿名请求会被服务端挡成壳页/无权页，
                    // App 详情页因此一直「加载失败」，而网页（带登录态）能正常打开。
                    // 公开内容带上 cookie 无副作用（实测体积仅多几百字节的登录态字段，SSR 数据一致）。
                    if (HupuAccount.isLoggedIn) {
                        header("Cookie", HupuAccount.loadCookieHeaderPublic())
                    }
                }
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val html = response.body?.string() ?: return@use null
                    if (html.length < 50_000) {
                        // 壳页/错误页保护——但低互动帖子（正文短+回复少）详情页天然只有 40~50KB：
                        // 小体积不再直接拒绝，而是检查是否带 SSR 数据载体（__NEXT_DATA__ / $$data），
                        // 有数据就放行（实测 replies=3 的帖子 45KB 数据完整可解析），无数据才是壳页
                        if (!html.contains("__NEXT_DATA__") && !html.contains("window.\$\$data=")) {
                            return@use null
                        }
                    }
                    HupuCache.put(path, html)
                    html
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * 搜索页 HTML（bbs.hupu.com/search?q=...&topicId=...&sortby=...&page=N）。
     * 与其他 SSR 页共用桌面 UA 与缓存通道。
     */
    suspend fun fetchSearchHtml(query: String, topicId: String? = null, sortby: String = "general", page: Int = 1, forceNetwork: Boolean = false): String? {
        val q = java.net.URLEncoder.encode(query, "UTF-8")
        val tid = if (topicId.isNullOrEmpty()) "" else "&topicId=$topicId"
        // q 放最后：磁盘缓存 key 截断 120 字符，长查询时保证 sortby/page 仍可区分
        return fetchHtml("/search?sortby=$sortby&page=$page$tid&q=$q", forceNetwork = forceNetwork)
    }

    /** 供图片加载参考的基础地址 */
    const val HOST = "bbs.hupu.com"
}