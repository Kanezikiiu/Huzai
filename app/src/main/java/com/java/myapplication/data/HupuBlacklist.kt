package com.java.myapplication.data

/**
 * 本地黑名单（我的 → 黑名单）——拉黑的是「人」，不是关键词。
 *
 * 为什么按「一组 id」而不是「一个人」判定：
 *  同一用户在虎扑不同接口里的标识并不统一——帖子/回复给 `puid`（短数字），
 *  评分评论只给 `commentUserId`（数字，形同 euid）。这两者**不保证同值**。
 *  因此加入黑名单时把能拿到的 id 全部记下（用户主页同时有 puid 与 euid），
 *  判定时任一命中即视为同一人，避免「同一个人，帖子被过滤、评分评论却漏了」。
 *
 * 生效范围（与产品确认 2026-09，纯本地过滤，不改服务端分页）：
 *  - 首页信息流 / 专区信息流：该人发的帖子不显示
 *  - 帖子详情·一级回复：该人的回复不显示
 *  - 帖子详情·楼中楼：该人的回复不显示（深层回复在新 sheet 里展开，删节点即整棵不可达）
 *  - 评分页评论 / 评分楼中楼（含孙评论）：该人的评论连同其整棵子树不显示
 *  - 帖子**主楼不过滤** —— 被拉黑者发的帖子仍可点进去阅读（产品明确要求）
 *
 * 纯 Kotlin（不依赖 Android），可单测。
 */
object HupuBlacklist {
    /** 黑名单条目上限（按「人」计；一个人内部可能挂多把 id 钥匙：puid / euid） */
    const val MAX_ENTRIES = 500

    /**
     * 黑名单表：id（puid / euid / commentUserId） → 显示用昵称。
     * 同一个人若有多个 id，会写入多条、共用同一个昵称。
     */
    typealias Entries = Map<String, String>

    /** 昵称归一化（去首尾空白） */
    fun normalizeName(s: String): String = s.trim()

    /** 单个 id 判定（id 为空恒不命中） */
    fun blockedId(id: String, entries: Entries): Boolean {
        if (entries.isEmpty()) return false
        val v = id.trim()
        return v.isNotEmpty() && entries.containsKey(v)
    }

    /** 多 id 任一命中即视为同一人 */
    fun blockedAny(ids: List<String>, entries: Entries): Boolean =
        entries.isNotEmpty() && ids.any { blockedId(it, entries) }

    /** 帖子/回复作者：puid 与 euid 都试（euid 常缺，puid 恒有） */
    fun blockedAuthor(a: HupuAuthor?, entries: Entries): Boolean =
        a != null && blockedAny(listOf(a.puid, a.euid), entries)

    /** 帖子条目（首页 / 专区信息流） */
    fun pruneThreads(list: List<HupuThread>, entries: Entries): List<HupuThread> =
        if (entries.isEmpty()) list else list.filterNot { blockedAuthor(it.author, entries) }

    /** 帖子详情·一级回复 */
    fun pruneReplies(list: List<HupuReply>, entries: Entries): List<HupuReply> =
        if (entries.isEmpty()) list else list.filterNot { blockedAuthor(it.author, entries) }

    /** 帖子详情·楼中楼子回复（同层删节点；深层在新 sheet 展开，故删节点即整棵隐藏） */
    fun pruneSubReplies(list: List<HupuSubReply>, entries: Entries): List<HupuSubReply> =
        if (entries.isEmpty()) list else list.filterNot { blockedAuthor(it.author, entries) }

    /** 评分评论：单点判定用 commentUserId */
    fun blockedScoreComment(c: HupuScoreComment, entries: Entries): Boolean =
        blockedId(c.userId, entries)

    /**
     * 评分评论列表：命中者**整棵删除**（含其全部后代），未命中者递归剪掉后代里的命中节点。
     */
    fun pruneScoreComments(list: List<HupuScoreComment>, entries: Entries): List<HupuScoreComment> =
        if (entries.isEmpty()) list else list.mapNotNull { pruneScoreNode(it, entries) }

    /** 评分评论单节点递归剪枝。返回 null = 本人命中，整棵删除。 */
    fun pruneScoreNode(c: HupuScoreComment, entries: Entries): HupuScoreComment? {
        if (entries.isEmpty()) return c
        if (blockedScoreComment(c, entries)) return null
        if (c.subComments.isEmpty()) return c
        val kept = c.subComments.mapNotNull { pruneScoreNode(it, entries) }
        return if (kept.size == c.subComments.size) c else c.copy(subComments = kept)
    }

    /**
     * 「展开更多回复」拉平后的孙评论列表（扁平表，靠 parentCommentId 表达父子）。
     * 整棵隐藏语义 = 本人命中 → 删；**祖先链上有人命中** → 也删（否则会出现孤儿回复）。
     *
     * @param parent 这批孙评论可能挂靠的上一层节点（母评论 / 子评论），用于补全祖先链
     */
    fun pruneScoreFlat(
        list: List<HupuScoreComment>,
        parent: HupuScoreComment?,
        entries: Entries,
    ): List<HupuScoreComment> {
        if (entries.isEmpty()) return list
        val byId = list.associateBy { it.commentId }
        fun ancestorHit(x: HupuScoreComment, depth: Int = 0): Boolean {
            if (depth > 64) return false
            val pid = x.parentCommentId
            if (pid.isEmpty()) return false
            val p = byId[pid] ?: parent?.takeIf { it.commentId == pid } ?: return false
            if (blockedScoreComment(p, entries)) return true
            return ancestorHit(p, depth + 1)
        }
        return list.filterNot { blockedScoreComment(it, entries) || ancestorHit(it) }
    }
}