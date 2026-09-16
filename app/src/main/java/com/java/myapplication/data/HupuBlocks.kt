package com.java.myapplication.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * 1.113: 正文块模型（做法乙）。
 *
 * 依据（实测 2025-09）：
 *  - 服务端 `content` 就是「一串 <p> 块」：文字 `<p>t</p>`、图片 `<p><img src/></p>`、
 *    投票 `<p>[vote]ID[/vote]</p>`（短代码是服务端自己的规范形式，实测原样往返）。
 *  - `format` = {htmlV3, jsonV3, imgList, videoInfo}，jsonV3 各节点 schema：
 *      paragraph: {"type":"paragraph","content":[{"type":"text","text":"..."}]}
 *      image:     {"type":"image","attrs":{"src":URL},"key":URL}
 *      vote:      {"type":"vote","attrs":{"voteId","limit","title","type","choices"}}
 *  - 投票节点只认 voteId；voteId 由 POST /api/v1/votes 先创建取得。
 *
 * 只有正文里存在投票时才需要提交 format（纯文字/图片帖保持原有「不带 format」的行为，
 * 避免动到已经跑通的链路）。
 */
object HupuBlocks {

    sealed class Block {
        abstract val id: Long

        /** 文本块：内部可自由换行，每行序列化为一个 <p> */
        data class Text(override val id: Long, val text: String) : Block()

        data class Image(override val id: Long, val url: String) : Block()

        data class Vote(
            override val id: Long,
            val voteId: Int,
            val title: String,
            val choices: List<String>,
            val limit: Int = 1,
            val voteType: String = "radio",
        ) : Block()
    }

    /** 序列化结果：content（必填） + format（仅含投票时非空） */
    data class Payload(val contentHtml: String, val formatJson: String?)

    /** 按锚点插入的结果：新块列表 + 下一个锚点（一次插多张图时保持顺序） */
    data class InsertResult(val blocks: List<Block>, val nextAnchorId: Long?, val nextAnchorOffset: Int)

    /**
     * 1.114: 在锚点处插入若干块——锚点 = 文本块 [anchorId] 的第 [offset] 个字符处。
     * 插入时把该文本块切成「前 / 新块… / 后」三部分，因此块可落在正文任意位置。
     * 找不到锚点（null / 已被合并）则追加到末尾。新块 id 由 [idFactory] 提供。
     */
    fun insertAtAnchor(
        blocks: List<Block>,
        anchorId: Long?,
        offset: Int,
        newBlocks: List<Block>,
        idFactory: () -> Long,
    ): InsertResult {
        if (newBlocks.isEmpty()) return InsertResult(blocks, anchorId, offset)
        val idx = if (anchorId == null) -1
        else blocks.indexOfFirst { it.id == anchorId && it is Block.Text }
        if (idx < 0) return InsertResult(blocks + newBlocks, null, 0)

        val tb = blocks[idx] as Block.Text
        val off = offset.coerceIn(0, tb.text.length)
        val before = tb.text.substring(0, off)
        val after = tb.text.substring(off)

        val inserted = mutableListOf<Block>()
        if (before.isEmpty() && after.isEmpty()) {
            // 1.116: 光标所在的文本块是空的 → 保留它（否则插一张图后正文输入框就没了）
            inserted.add(tb)
        } else if (before.isNotEmpty()) {
            inserted.add(Block.Text(idFactory(), before))
        }
        inserted.addAll(newBlocks)
        var afterId: Long? = null
        if (after.isNotEmpty()) {
            val nb = Block.Text(idFactory(), after)
            afterId = nb.id
            inserted.add(nb)
        }

        val out = blocks.toMutableList()
        out.removeAt(idx)
        out.addAll(idx, inserted)
        return InsertResult(out, afterId, 0)
    }

    private var seq = 0L
    fun nextId(): Long = ++seq

    /**
     * 块列表 → (content, format)。
     * format 仅在存在投票块时生成；其余情况返回 null（沿用旧行为）。
     */
    fun serialize(blocks: List<Block>): Payload {
        val content = StringBuilder()
        val htmlV3 = StringBuilder()
        val v3 = JSONArray()
        val imgList = JSONArray()
        var hasVote = false

        blocks.forEach { b ->
            when (b) {
                is Block.Text -> {
                    b.text.split("\n")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .forEach { line ->
                            content.append("<p>").append(escape(line)).append("</p>")
                            htmlV3.append("<p>").append(escape(line)).append("</p>")
                            v3.put(
                                JSONObject()
                                    .put("type", "paragraph")
                                    .put(
                                        "content",
                                        JSONArray().put(
                                            JSONObject().put("type", "text").put("text", line),
                                        ),
                                    ),
                            )
                        }
                }

                is Block.Image -> {
                    content.append("<p><img src=\"").append(b.url).append("\"/></p>")
                    htmlV3.append("<p><img src=\"").append(b.url).append("\"/></p>")
                    v3.put(
                        JSONObject()
                            .put("type", "image")
                            .put("attrs", JSONObject().put("src", b.url))
                            .put("key", b.url),
                    )
                    imgList.put(JSONObject().put("remoteUrl", b.url).put("key", b.url))
                }

                is Block.Vote -> {
                    hasVote = true
                    content.append("<p>[vote]").append(b.voteId).append("[/vote]</p>")
                    htmlV3.append("<div data-hupu-node=\"vote\" data-hupu-data=\"")
                        .append(escapeAttr(voteDataJson(b)))
                        .append("\"></div>")
                    v3.put(
                        JSONObject().put("type", "vote").put(
                            "attrs",
                            JSONObject()
                                .put("voteId", b.voteId)
                                .put("limit", b.limit)
                                .put("title", b.title)
                                .put("type", b.voteType)
                                .put("choices", JSONArray(b.choices)),
                        ),
                    )
                }
            }
        }

        if (!hasVote) return Payload(content.toString(), null)

        val format = JSONObject()
            .put("htmlV3", htmlV3.toString())
            .put("jsonV3", JSONObject().put("type", "doc").put("content", v3))
            .put("imgList", imgList)
            .put("videoInfo", JSONObject().put("extra", 1))
            .toString()
        return Payload(content.toString(), format)
    }

    /** htmlV3 里投票块携带的数据（官方同款字段序：choices/limit/title/type） */
    private fun voteDataJson(b: Block.Vote): String = JSONObject()
        .put("choices", JSONArray(b.choices))
        .put("limit", b.limit)
        .put("title", b.title)
        .put("type", b.voteType)
        .toString()

    /**
     * 编辑态反解析：jsonV3 优先（能 1:1 还原投票），无 format 时回退到 content HTML。
     * 连续的 paragraph 节点合并为同一个文本块（换行分隔），保持书写体验。
     */
    fun fromEdit(contentHtml: String, formatJson: String?): List<Block> {
        val doc = formatJson?.let { fmt ->
            try {
                JSONObject(fmt).optJSONObject("jsonV3")
            } catch (e: Exception) {
                null
            }
        }
        if (doc != null) {
            val arr = doc.optJSONArray("content") ?: JSONArray()
            val out = mutableListOf<Block>()
            val textBuf = StringBuilder()
            fun flushText() {
                if (textBuf.isNotEmpty()) {
                    out.add(Block.Text(nextId(), textBuf.toString()))
                    textBuf.clear()
                }
            }
            for (i in 0 until arr.length()) {
                val node = arr.optJSONObject(i) ?: continue
                when (node.optString("type")) {
                    "paragraph" -> {
                        val txt = paragraphText(node)
                        if (textBuf.isNotEmpty()) textBuf.append("\n")
                        textBuf.append(txt)
                    }

                    "image" -> {
                        flushText()
                        val src = node.optJSONObject("attrs")?.optString("src", "").orEmpty()
                        if (src.isNotEmpty()) out.add(Block.Image(nextId(), src))
                    }

                    "vote" -> {
                        flushText()
                        val a = node.optJSONObject("attrs")
                        if (a != null) {
                            val vid = a.optInt("voteId", 0)
                            val choices = a.optJSONArray("choices")?.let { ja ->
                                (0 until ja.length()).map { ja.optString(it, "") }
                            } ?: emptyList()
                            if (vid > 0) {
                                out.add(
                                    Block.Vote(
                                        id = nextId(),
                                        voteId = vid,
                                        title = a.optString("title", ""),
                                        choices = choices,
                                        limit = a.optInt("limit", 1),
                                        voteType = a.optString("type", "radio").ifEmpty { "radio" },
                                    ),
                                )
                            }
                        }
                    }
                }
            }
            flushText()
            if (out.isNotEmpty()) return out
        }
        // 回退：老帖没有 format（我们自己发的图文帖），按 HTML 拆
        val out = mutableListOf<Block>()
        val text = HupuPostApi.stripHtml(contentHtml)
        out.add(Block.Text(nextId(), text))
        HupuPostApi.extractImages(contentHtml).forEach { out.add(Block.Image(nextId(), it)) }
        return out
    }

    private fun paragraphText(node: JSONObject): String {
        val arr = node.optJSONArray("content") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until arr.length()) {
            val c = arr.optJSONObject(i) ?: continue
            sb.append(c.optString("text", ""))
        }
        return sb.toString()
    }

    // ---------- HTML 转义 ----------

    private fun escape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    /** 属性值转义（含双引号实体；实体字面量拆开拼接，避免被工具层解码） */
    private fun escapeAttr(s: String): String =
        escape(s).replace("\"", "&" + "quot;")
}
