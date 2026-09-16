package com.java.myapplication.data

/**
 * 信息流关键词过滤器（我的→信息流设置）：
 * - 标题关键词：命中则整条帖子不显示（首页/专区/搜索/浏览记录）
 * - 分区关键词：命中则整条帖子不显示（同上）
 * - 评论关键词：命中则该条评论不显示（帖子回复/楼中楼/评分评论/孙评论）
 *
 * 纯 Kotlin 判定（不依赖 Android，可单测）；持久化在 HupuPrefs.filterKeywords_v1。
 */
object HupuFilter {
    /** 每组关键词上限 */
    const val MAX_KEYWORDS = 100

    data class Keywords(
        val title: List<String> = emptyList(),
        val zone: List<String> = emptyList(),
        val comment: List<String> = emptyList(),
    ) {
        val isEmpty: Boolean get() = title.isEmpty() && zone.isEmpty() && comment.isEmpty()
    }

    /** 关键词归一化：去空白、忽略大小写比较（中文不受影响） */
    fun normalize(k: String): String = k.trim().lowercase()

    fun valid(k: String): Boolean = normalize(k).isNotEmpty()

    /** 单条标题/分区判定：任一关键词命中 → 过滤 */
    fun blocked(text: String, keywords: List<String>): Boolean {
        if (keywords.isEmpty() || text.isBlank()) return false
        val t = text.lowercase()
        return keywords.any { it.isNotEmpty() && t.contains(it.lowercase()) }
    }

    /** 帖子条目级判定：标题或分区命中 → 不显示（zone 名可空） */
    fun blockedThread(title: String, zoneName: String?, kw: Keywords): Boolean =
        blocked(title, kw.title) || blocked(zoneName ?: "", kw.zone)

    /** 评论内容判定：命中评论关键词 → 该条评论不显示 */
    fun blockedComment(content: String, kw: Keywords): Boolean =
        blocked(content, kw.comment)

    /** 剥 HTML 标签（评论内容是 HTML 片段；截前 2000 字符防超长） */
    fun stripHtml(html: String): String {
        val s = if (html.length > 2000) html.take(2000) else html
        var out = s.replace(Regex("<[^>]*>"), "")
        out = out.replace("&nbsp;", " ")
        out = out.replace("&amp;", "&")
        out = out.replace("&lt;", "<")
        out = out.replace("&gt;", ">")
        out = out.replace("&#39;", "'")
        val sb = StringBuilder(out.length)
        var i = 0
        while (i < out.length) {
            if (out.startsWith("&quot;", i)) { sb.append('"'); i += 6 }
            else { sb.append(out[i]); i += 1 }
        }
        return sb.toString()
    }
}
