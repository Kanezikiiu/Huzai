package com.java.myapplication.data

import java.net.URLDecoder

/**
 * 1.224o：帖子正文里「超链接」的解析 —— 决定点下去在**应用内**打开什么。
 *
 * 正文里的链接有好几种形态，都要**在应用内**落到我们自己的页面（以前不认，直接丢给系统 →
 * 被**官方客户端**接管；网页承载又不理想，用户明确要原生页）：
 *
 *  1. `https://bbs.hupu.com/642968180.html`                      → 应用内开帖
 *  2. `huputiyu://score/detail?outBizType=lol_item&outBizNo=78651`  → **原生**选手/条目评分页
 *  3. `huputiyu://general/live?competitionType=…&matchId=…`         → **原生**对局评分页
 *  4. `huputiyu://webview/openencodeurl?url=<编码 https>&nativeSchema=…`
 *     —— 官方"网页外链"包装：**解开后重新分类**（内部常常就是 2 的评分详情）
 *  5. `https://bbsactivity.hupu.com/pc-viewer/index.html?t=<编码 https>`
 *     —— 官方"壳页"：取 `t` 参数解一层再分类
 *  6. 任意带 `outBizType` + `outBizNo` 的地址 → **原生**评分对象详情（覆盖上面两种的最终落点）
 *  7. `huputiyu://people/{id}`（正文「@某人」）                      → 原生用户主页
 *  8. 其它虎扑网页 → 应用内 WebView；非虎扑外链 → null（走系统浏览器）
 *
 * 纯 Kotlin（不依赖 Android），可单测。
 */
object HupuLinks {

    /** 站内帖子：`huputiyu://bbs/topic/123` / `hupu://bbs/topic/123` */
    private val TID_SCHEME =
        Regex("""hupu(?:tiyu)?://bbs/topic/(\d+)""", RegexOption.IGNORE_CASE)

    /** 虎扑域名下的帖子页：`/数字.html` */
    private val TID_WEB =
        Regex(
            """^https?://(?:[a-z0-9-]+\.)*hupu\.com/(?:bbs/)?(\d+)(?:\.html)?(?:[?#].*)?$""",
            RegexOption.IGNORE_CASE,
        )

    /** 评分详情页的网页形态（仅在"确实只能走网页"时使用） */
    private const val SCORE_DETAIL_WEB = "https://m.hupu.com/score/detail.html"

    /** 应用内承载时用的 embed 类型（决定 WebView 页顶栏文案，见 [HupuEmbed]） */
    const val EMBED_SCORE = "score"
    const val EMBED_LINK = "link"

    /** 链接的落点 */
    sealed interface Target {
        /** 站内帖子 → 应用内再盖一篇 */
        data class Thread(val tid: String) : Target

        /** 评分对象详情（选手 / 条目）→ 评分页的原生选手评分页 */
        data class ScoreItem(val bizType: String, val bizNo: String) : Target

        /**
         * 对局评分 → 评分页的原生对局评分页。
         *
         * 1.224o：**按 id 识别**（不按 host 枚举）：query 里的 matchId，或路径里 ≥6 位的数字段。
         * [channel] 只是"在哪个赛程频道里找"的**提示**（深链 host 恰好是频道名时，如 `lol`），
         * 拿它去查**受支持频道列表**，命中就先只拉那一个频道，省掉盲搜 20 个频道的开销。
         */
        data class ScoreMatch(val matchId: String, val channel: String? = null) : Target

        /** 用户主页（`huputiyu://people/{id}`）→ 应用内用户主页 */
        data class User(val uid: String) : Target

        /** 其余虎扑网页 → 应用内 WebView 承载（[embedType] 决定顶栏文案） */
        data class Web(val url: String, val embedType: String) : Target
    }

    /** 从链接取站内帖子 tid；不是帖子链接返回 null。 */
    fun threadTid(url: String?): String? {
        val u = url?.trim().orEmpty()
        if (u.isEmpty()) return null
        TID_SCHEME.find(u)?.let { return it.groupValues[1] }
        return TID_WEB.find(u)?.groupValues?.get(1)
    }

    /** 是否虎扑自家链接（含自定义 scheme）。 */
    fun isHupu(url: String?): Boolean {
        val u = url?.trim()?.lowercase().orEmpty()
        if (u.isEmpty()) return false
        return u.startsWith("hupu://") || u.startsWith("huputiyu://") ||
            Regex("""^https?://(?:[a-z0-9-]+\.)*hupu\.com(?:[/?#]|$)""").containsMatchIn(u)
    }

    /**
     * 把正文里的链接解析成落点；无法在应用内处理时返回 null（调用方走系统浏览器）。
     */
    fun resolve(url: String?): Target? = resolve(url, 0)

    private fun resolve(url: String?, depth: Int): Target? {
        val raw = url?.trim().orEmpty()
        if (raw.isEmpty() || depth > 4) return null

        // 0. 帖子最优先（避免被当成普通网页）
        threadTid(raw)?.let { return Target.Thread(it) }

        val lower = raw.lowercase()

        // 1. 站内自定义 scheme：huputiyu://<host><path>?<query>
        if (lower.startsWith("huputiyu://") || lower.startsWith("hupu://")) {
            // ⚠️ 不用 java.net.URI：它遇到未转义字符（空格、孤立 %）会抛异常 → 只能返回 null → 外跳。
            // 手工切分永不失败。
            val afterScheme = raw.substringAfter("://", "")
            val hostEnd = afterScheme.indexOfFirst { it == '/' || it == '?' || it == '#' }
            val host = (if (hostEnd < 0) afterScheme else afterScheme.substring(0, hostEnd)).lowercase()
            val rest = if (hostEnd < 0) "" else afterScheme.substring(hostEnd)
            val path = rest.substringBefore('?').substringBefore('#').lowercase()
            val q = queryMap(rest.substringAfter('?', "").substringBefore('#'))

            return when {
                // 官方「网页外链」包装：优先取裸 url，再**重新分类**（内部常是评分详情 → 走原生）
                host == "webview" && path.startsWith("/openencodeurl") -> {
                    val inner = q["url"]?.takeIf { it.startsWith("http", true) }
                    if (inner != null) resolve(inner, depth + 1)
                    else resolve(q["nativeschema"], depth + 1)
                }
                // 评分对象详情（选手 / 条目）
                host == "score" && path.startsWith("/detail") ->
                    scoreItemOf(q["outbiztype"], q["outbizno"])
                // 用户主页（正文里的「@某人」）
                host == "people" && path.trim('/').isNotEmpty() ->
                    Target.User(path.trim('/').substringBefore('/'))
                // 对局评分（赛事 / 足球 / 各项目统称）：**按 id 识别**，不按 host
                else -> {
                    // matchId：优先 query，其次路径里 ≥6 位的纯数字段（`/lol/lol/1677567214387200`）
                    val m = q["matchid"]?.takeIf { it.isNotBlank() }
                        ?: path.trim('/').split('/').lastOrNull { seg ->
                            seg.length >= 6 && seg.all(Char::isDigit)
                        }
                    if (m.isNullOrBlank()) null else Target.ScoreMatch(m, channel = host)
                }
            }
        }

        // 2. 官方「壳页」bbsactivity…pc-viewer?t=<编码后的真实地址> → 解一层再分类
        if (lower.contains("pc-viewer")) {
            val t = queryMap(raw.substringAfter('?', "").substringBefore('#')).get("t")
            if (!t.isNullOrBlank()) return resolve(t, depth + 1)
        }

        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            // 3. 任何带 outBizType + outBizNo 的地址 → 评分对象详情（原生）
            val q = queryMap(raw.substringAfter('?', "").substringBefore('#'))
            scoreItemOf(q["outbiztype"], q["outbizno"])?.let { return it }
            // 4. 其它虎扑网页 → 应用内 WebView
            if (isHupu(raw)) return Target.Web(raw, EMBED_SCORE)
            return null
        }
        return null
    }

    /** outBizType/outBizNo 都齐了才算"评分对象详情" */
    private fun scoreItemOf(bizType: String?, bizNo: String?): Target? =
        if (bizType.isNullOrBlank() || bizNo.isNullOrBlank()) null
        else Target.ScoreItem(bizType, bizNo)

    /** query 解析：键统一小写，值做 URL 解码（可能重复，取第一个） */
    private fun queryMap(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        // HTML 属性里的 & 常被写成实体（`&amp;`），不还原会让参数名变成 `amp;matchId`
        val normalized = rawQuery
            .replace("&amp;", "&", ignoreCase = true)
            .replace("&#38;", "&")
            .replace("&#x26;", "&", ignoreCase = true)
        val out = HashMap<String, String>()
        for (pair in normalized.split('&')) {
            if (pair.isBlank()) continue
            val i = pair.indexOf('=')
            val k = if (i < 0) pair else pair.substring(0, i)
            val v = if (i < 0) "" else pair.substring(i + 1)
            val key = k.trim().lowercase()
            if (out.containsKey(key)) continue
            out[key] = runCatching { URLDecoder.decode(v, "UTF-8") }.getOrDefault(v)
        }
        return out
    }

    /** 需要"网页形态"时的兜底构造（评分类链接最终仍需用 WebView 打开时用） */
    fun webScoreDetail(bizType: String, bizNo: String): String =
        "$SCORE_DETAIL_WEB?outBizType=${enc(bizType)}&outBizNo=${enc(bizNo)}"

    /** 参数值回填时做最小转义（避免 & / = 破坏 query） */
    private fun enc(v: String): String = v.replace("&", "%26").replace("=", "%3D").replace(" ", "%20")
}