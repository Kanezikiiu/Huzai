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

    /**
     * 1.223 评分评论树：关键词命中的节点**连同其全部后代整棵删除**，未命中者递归剪掉后代里的命中节点。
     * （此前楼中楼只过滤直接子评论，内嵌孙评论成了漏网之鱼）
     */
    fun pruneScoreCommentTree(list: List<HupuScoreComment>, kw: Keywords): List<HupuScoreComment> =
        if (kw.comment.isEmpty()) list else list.mapNotNull { pruneScoreCommentNode(it, kw) }

    private fun pruneScoreCommentNode(c: HupuScoreComment, kw: Keywords): HupuScoreComment? {
        if (blockedComment(c.content, kw)) return null
        if (c.subComments.isEmpty()) return c
        val kept = c.subComments.mapNotNull { pruneScoreCommentNode(it, kw) }
        // 注意：必须按「内容是否变化」判断，不能只比 size——
        // 子节点存活但其孙节点被删时 size 不变，只比 size 会把修改丢掉。
        return if (kept == c.subComments) c else c.copy(subComments = kept)
    }

    /**
     * 1.223 「展开更多回复」拉平后的孙评论（扁平表，靠 parentCommentId 表达父子）：
     * 本人命中 → 删；**祖先链命中** → 也删（避免出现孤儿回复）。语义与黑名单版一致。
     */
    fun pruneScoreCommentFlat(
        list: List<HupuScoreComment>,
        parent: HupuScoreComment?,
        kw: Keywords,
    ): List<HupuScoreComment> {
        if (kw.comment.isEmpty()) return list
        val byId = list.associateBy { it.commentId }
        fun ancestorHit(x: HupuScoreComment, depth: Int = 0): Boolean {
            if (depth > 64) return false
            val pid = x.parentCommentId
            if (pid.isEmpty()) return false
            val p = byId[pid] ?: parent?.takeIf { it.commentId == pid } ?: return false
            if (blockedComment(p.content, kw)) return true
            return ancestorHit(p, depth + 1)
        }
        return list.filterNot { blockedComment(it.content, kw) || ancestorHit(it) }
    }

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
