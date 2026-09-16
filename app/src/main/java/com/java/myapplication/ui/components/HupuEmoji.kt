package com.java.myapplication.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/**
 * Hupu official BBS emoji pack: "[name]" -> CDN PNG (180x180).
 * Map extracted from official m.hupu.com Next.js chunk (chunks/4558).
 * Rendered inline via Compose InlineTextContent (placeholder char + AsyncImage).
 */
private const val CDN = "https://w1.hoopchina.com.cn/editor/emoji/"

val HUPU_EMOJI: Map<String, String> = mapOf(
    // 官方 28 表情（bbs-pc-web tid chunk bb map 完整对齐, 2026-09）
    "[tui]" to CDN + "tui.png",
    "[\u4f60\u518d\u9a82]" to CDN + "nizaima.png",
    "[\u518d\u89c1]" to CDN + "zaijian.png",
    "[\u52a0\u6cb9]" to CDN + "jiayou.png",
    "[\u53f9\u6c14]" to CDN + "tanqi.png",
    "[\u5403\u74dc]" to CDN + "chigua.png",
    "[\u5927\u54ed]" to CDN + "daku.png",
    "[\u5927\u7b11]" to CDN + "daxiao.png",
    "[\u5978\u7b11]" to CDN + "jianxiao.png",
    "[\u59da\u660e\u7b11]" to CDN + "yaomingxiao.png",
    "[\u5bb3\u7f9e]" to CDN + "haixiu.png",
    "[\u5f3a]" to CDN + "qiang.png",
    "[\u5f97\u610f]" to CDN + "deyi.png",
    "[\u5fae\u7b11]" to CDN + "weixiao.png",
    "[\u60ca\u8bb6]" to CDN + "jingya.png",
    "[\u6123\u4f4f]" to CDN + "lengzhu.png",
    "[\u6293\u72c2]" to CDN + "zhuakuang.png",
    "[\u62a0\u9f3b]" to CDN + "koubi.png",
    "[\u6342\u8138]" to CDN + "wulian.png",
    "[\u64e6\u6c57]" to CDN + "cahan.png",
    "[\u72d7\u5934]" to CDN + "goutou.png",
    "[\u751f\u6c14]" to CDN + "shengqi.png",
    "[\u767d\u773c]" to CDN + "baiyan.png",
    "[\u7834\u9632]" to CDN + "pofang.png",
    "[\u7948\u7977]" to CDN + "qidao.png",
    "[\u88c2\u5f00]" to CDN + "liekai.png",
    "[\u95ee\u53f7\u8138]" to CDN + "wenhaolian.png",
    "[\u9f13\u638c]" to CDN + "guzhang.png",
)

/** Token like "[xx]" (1-8 chars, no brackets inside). */
val EMOJI_TOKEN: Regex = Regex("\\[[^\\[\\]]{1,8}\\]")

/** A split piece of text: either plain text or an emoji token (with resolved url). */
data class EmojiSegment(
    val text: String,
    val emojiUrl: String?,
    val oldStart: Int,
) {
    val isEmoji: Boolean get() = emojiUrl != null
}

/** Split text into plain/emoji segments (only known tokens become emoji). */
fun splitEmoji(text: String): List<EmojiSegment> {
    if (!text.contains('[')) return listOf(EmojiSegment(text, null, 0))
    val out = mutableListOf<EmojiSegment>()
    var cursor = 0
    for (m in EMOJI_TOKEN.findAll(text)) {
        val url = HUPU_EMOJI[m.value] ?: continue
        if (m.range.first > cursor) out += EmojiSegment(text.substring(cursor, m.range.first), null, cursor)
        out += EmojiSegment(m.value, url, m.range.first)
        cursor = m.range.last + 1
    }
    if (cursor < text.length) out += EmojiSegment(text.substring(cursor), null, cursor)
    return if (out.isEmpty()) listOf(EmojiSegment(text, null, 0)) else out
}

/** Build inlineContent map for segments containing emoji (placeholder = square image). */
@Composable
fun rememberEmojiInline(
    segments: List<EmojiSegment>,
    emojiSizeSp: Float = 20f,
): Map<String, InlineTextContent> {
    if (segments.none { it.isEmoji }) return emptyMap()
    return remember(segments, emojiSizeSp) {
        segments.filter { it.isEmoji }.associate { seg ->
            seg.emojiUrl!! to InlineTextContent(
                Placeholder(emojiSizeSp.sp, emojiSizeSp.sp, PlaceholderVerticalAlign.TextCenter),
            ) {
                AsyncImage(
                    model = seg.emojiUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** Plain text with emoji tokens rendered inline (no spans needed). */
@Composable
fun EmojiText(
    text: String,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    color: Color,
    fontWeight: FontWeight? = null,
    maxLines: Int = Int.MAX_VALUE,
    emojiSizeSp: Float = -1f,
) {
    val segments = remember(text) { splitEmoji(text) }
    if (segments.none { it.isEmoji }) {
        Text(text, fontSize = fontSize, lineHeight = lineHeight, color = color,
            fontWeight = fontWeight, maxLines = maxLines)
        return
    }
    val inline = rememberEmojiInline(segments, if (emojiSizeSp > 0f) emojiSizeSp else 20f)
    val annotated = remember(segments) {
        buildAnnotatedString {
            for (seg in segments) {
                if (seg.isEmoji) appendInlineContent(seg.emojiUrl!!, seg.text) else append(seg.text)
            }
        }
    }
    Text(annotated, fontSize = fontSize, lineHeight = lineHeight, color = color,
        fontWeight = fontWeight, maxLines = maxLines, inlineContent = inline)
}
