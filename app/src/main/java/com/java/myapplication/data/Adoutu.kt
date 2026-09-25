package com.java.myapplication.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * 1.191: adoutu 表情包搜索（第三方站点，公开 SSR 搜索页）。
 *
 * 合规边界（已实测确认）：
 *  · 该站 robots.txt 明确 `Disallow: /api/` → **不碰它的任何 API**，只取公开搜索页 HTML；
 *  · 只由用户主动点「搜索」触发，一次一条请求，走 [adoutuThrottle] 限速，无后台轮询；
 *  · 图片有防盗链：无 Referer 或换其它 Referer 一律 403 → 展示与插图都必须带 [REFERER]。
 *
 * 站点结构（2026-09 实测）：搜索页 SSR 直出，每页 40 条，
 * 条目形如 `<a href="/picture/{id}"><img alt="…" src="https://img.adoutu.com/picture/…"/></a>`。
 */
internal object Adoutu {
    /** 图片防盗链必需的来源；同时作为搜索页请求头（无害） */
    const val REFERER = "https://www.adoutu.com/"

    /** 实测每页 40 条（用于「是否还有下一页」判定） */
    const val PAGE_SIZE = 40

    private const val UA =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0 Mobile Safari/537.36"

    /** 一条搜索结果（id 用于去重与列表 key；title 取自图片 alt，可能为空） */
    data class Sticker(val id: String, val url: String, val title: String)

    /**
     * 搜索页 URL。`type=1` 锁定「表情包」分类——站点另一个分类 `type=2` 是「套图合集」，
     * 不是表情包，必须显式排除。
     */
    fun searchUrl(keyword: String, page: Int): String {
        val q = runCatching { java.net.URLEncoder.encode(keyword, "UTF-8") }
            .getOrDefault(keyword)
            .replace("+", "%20")
        return "https://www.adoutu.com/search?type=1&keyword=$q&page=$page"
    }

    /**
     * 无结果页判定：官方文案「没有找到与「x」相关的结果」。
     * 注意该页下方还挂着 8 条「随机推荐表情」——它们不是搜索结果，必须整体丢弃，
     * 否则「搜不到」会变成「搜到 8 条不相干的」。
     */
    fun isEmptyPage(html: String): Boolean = html.contains("没有找到与")

    private val ANCHOR = Regex(
        """<a\b[^>]*href="/picture/(\d+)"[^>]*>(.*?)</a>""",
        RegexOption.DOT_MATCHES_ALL,
    )
    private val IMG = Regex("""src="(https://img\.adoutu\.com/[^"]+)"""")
    private val ALT = Regex("""alt="([^"]*)"""")

    /** 纯函数解析（可单测）：从 SSR HTML 抽出结果条目，按 id / url 去重并保持页面顺序 */
    fun parseSearch(html: String): List<Sticker> {
        if (isEmptyPage(html)) return emptyList()
        val out = LinkedHashMap<String, Sticker>()
        for (m in ANCHOR.findAll(html)) {
            val id = m.groupValues[1]
            val inner = m.groupValues[2]
            val url = IMG.find(inner)?.groupValues?.get(1)?.let(::unescapeHtml) ?: continue
            if (!url.startsWith("https://img.adoutu.com/picture/")) continue
            if (out.containsKey(id) || out.values.any { it.url == url }) continue
            val title = ALT.find(inner)?.groupValues?.get(1)?.let(::unescapeHtml).orEmpty()
            out[id] = Sticker(id, url, title)
        }
        return out.values.toList()
    }

    /**
     * 搜索一页。返回 **null 表示网络/HTTP 失败**（与「成功但没有结果」= emptyList 区分开），
     * 这样界面只在真实失败后才显示失败态，而不是一上来就闪失败。
     */
    suspend fun search(keyword: String, page: Int): List<Sticker>? = withContext(Dispatchers.IO) {
        adoutuThrottle.acquire()
        runCatching {
            val req = Request.Builder()
                .url(searchUrl(keyword, page))
                .header("User-Agent", UA)
                .header("Referer", REFERER)
                .build()
            HupuHttp.client.newCall(req).execute().use { r ->
                if (r.isSuccessful) parseSearch(r.body?.string().orEmpty()) else null
            }
        }.getOrNull()
    }
}

/** 第三方站点的搜索限速：1 次/秒（不给对方站点压力，也不触发风控） */
internal val adoutuThrottle = RequestThrottle(1000L)

/**
 * 表情 URL → 抓图时需要的 Referer。
 * adoutu 图片有防盗链（无 Referer 403）；虎扑图床不需要 → 其余一律返回 null，
 * 让下载请求保持原样（零行为变化）。
 */
internal fun stickerReferer(url: String): String? =
    if (url.contains("img.adoutu.com")) Adoutu.REFERER else null

/** 最小 HTML 实体解码（alt / src 里可能带 &amp; 等）——纯函数，可单测 */
internal fun unescapeHtml(s: String): String = s
    .replace("\u0026amp;", "&")
    .replace("\u0026quot;", "\"")
    .replace("\u0026#39;", "\u0027")
    .replace("\u0026lt;", "<")
    .replace("\u0026gt;", ">")
