package com.java.myapplication.data

/**
 * 1.117: 正文草稿模型（方案 B）——单文本框 + 原子占位符 + 附件表。
 *
 * 正文只保留一个字符串；图片/投票以「单个 BMP 私有区字符」作为占位符内嵌在正文里，
 * 占位符在正文中的位置就是媒体在帖子里的位置。约定：
 *  - 插入：在当前光标处写入一个占位符字符（单字符 → 退格即整体删除，不会删成半截）。
 *  - 删占位符：附件不丢，只是「未定位」，展示与发布时都排到最后。
 *  - 删附件：把它对应的占位符从正文里摘掉。
 *
 * 序列化仍复用 [HupuBlocks.serialize]（1.113 已验证的投票/图文契约不变）：
 * 正文里出现的占位符按位置生成 image / vote 块，未定位附件追加到末尾。
 */
object HupuDraft {

    /** 占位符字符池：BMP 私有使用区 E000..E7FF（2048 个，足够单帖 9 图 + 1 投票且不重复）。 */
    private const val TOKEN_START = 0xE000
    private const val TOKEN_END = 0xE800
    private var seq = 0

    /** 分配一个新的占位符字符（单调递增，避免与历史字符撞车）。 */
    fun nextToken(): Char {
        val span = TOKEN_END - TOKEN_START
        return (TOKEN_START + (seq++ % span)).toChar()
    }

    /** 是否为占位符字符 */
    fun isToken(c: Char): Boolean = c.code in TOKEN_START until TOKEN_END

    /** 正文里当前存在的占位符集合 */
    fun tokensIn(text: String): Set<Char> {
        val out = HashSet<Char>()
        for (c in text) if (isToken(c)) out.add(c)
        return out
    }

    /** 占位符 → 它在正文中的序号（1 起，按出现顺序）。 */
    fun indexMap(text: String): Map<Char, Int> {
        val out = HashMap<Char, Int>()
        var n = 0
        for (c in text) {
            if (isToken(c) && !out.containsKey(c)) out[c] = ++n
        }
        return out
    }

    /** 占位符在正文里显示成的标签（带序号，和附件区角标一一对应）。 */
    fun labelOf(att: Attachment?, index: Int): String = when (att) {
        is Attachment.Image -> "〔图片$index〕"
        is Attachment.Vote -> "〔投票$index〕"
        null -> "〔附件$index〕"
    }

    /** 附件展示/序列化顺序：按占位符在正文里的位置；找不到位置的排到最后。 */
    fun ordered(text: String, attachments: List<Attachment>): List<Attachment> {
        val idx = indexMap(text)
        val anchored = attachments.filter { idx.containsKey(it.token) }.sortedBy { idx[it.token] }
        val unanchored = attachments.filter { !idx.containsKey(it.token) }
        return anchored + unanchored
    }

    /**
     * 占位符与附件 1:1 绑定：正文里已经没有的占位符，其附件一并删除。
     * （用户删掉正文里的〔图片N〕＝ 删掉这张图）
     */
    fun pruneAttachments(text: String, attachments: List<Attachment>): List<Attachment> {
        val live = tokensIn(text)
        return attachments.filter { it.token in live }
    }

    /** 单个附件（图片 / 投票），一个附件恰好对应正文里的一个占位符字符。 */
    sealed class Attachment {
        abstract val token: Char

        data class Image(override val token: Char, val url: String) : Attachment()

        data class Vote(
            override val token: Char,
            val voteId: Int,
            val title: String,
            val choices: List<String>,
            val limit: Int = 1,
            val voteType: String = "radio",
        ) : Attachment()
    }

    /**
     * 正文文本 + 附件表 → 块列表（供 [HupuBlocks.serialize] 复用）。
     * 正文里出现的占位符按位置生成媒体块；未在正文里出现的附件（占位符被删）追加到末尾。
     */
    fun toBlocks(text: String, attachments: List<Attachment>): List<HupuBlocks.Block> {
        val byToken = attachments.associateBy { it.token }
        val used = HashSet<Char>()
        val out = mutableListOf<HupuBlocks.Block>()
        val buf = StringBuilder()

        fun flush() {
            if (buf.isNotEmpty()) {
                out.add(HupuBlocks.Block.Text(HupuBlocks.nextId(), buf.toString()))
                buf.clear()
            }
        }

        for (c in text) {
            val att = byToken[c]
            if (att != null) {
                flush()
                used.add(c)
                out.add(att.toBlock())
            } else if (isToken(c)) {
                // 未知/失效占位符：丢弃，避免把私有区字符带进正文
            } else {
                buf.append(c)
            }
        }
        flush()

        attachments.filter { it.token !in used }.forEach { out.add(it.toBlock()) }
        if (out.isEmpty()) out.add(HupuBlocks.Block.Text(HupuBlocks.nextId(), ""))
        return out
    }

    /**
     * 块列表 → 正文文本 + 附件表（编辑已有帖子时用）。
     * 连续文本块之间补换行；图片/投票转成占位符字符。
     */
    fun fromBlocks(blocks: List<HupuBlocks.Block>): Pair<String, List<Attachment>> {
        val sb = StringBuilder()
        val atts = mutableListOf<Attachment>()
        var lastWasText = false
        for (b in blocks) {
            when (b) {
                is HupuBlocks.Block.Text -> {
                    if (lastWasText) sb.append("\n")
                    sb.append(b.text)
                    lastWasText = true
                }

                is HupuBlocks.Block.Image -> {
                    val t = nextToken()
                    atts.add(Attachment.Image(t, b.url))
                    sb.append(t)
                    lastWasText = false
                }

                is HupuBlocks.Block.Vote -> {
                    val t = nextToken()
                    atts.add(
                        Attachment.Vote(
                            token = t,
                            voteId = b.voteId,
                            title = b.title,
                            choices = b.choices,
                            limit = b.limit,
                            voteType = b.voteType,
                        ),
                    )
                    sb.append(t)
                    lastWasText = false
                }
            }
        }
        return sb.toString() to atts
    }

    private fun Attachment.toBlock(): HupuBlocks.Block = when (this) {
        is Attachment.Image -> HupuBlocks.Block.Image(HupuBlocks.nextId(), url)
        is Attachment.Vote -> HupuBlocks.Block.Vote(
            id = HupuBlocks.nextId(),
            voteId = voteId,
            title = title,
            choices = choices,
            limit = limit,
            voteType = voteType,
        )
    }
}
