package com.java.myapplication.data

import org.json.JSONObject

/**
 * 1.178: 帖子正文的「结构化内容」。
 *
 * 官方对部分帖子（如赛事战报）**不存 HTML**，而是存一段 JSON 描述符，由客户端据此
 * 挂载对应的小组件。实测帖子 642451842 的 `content` 原文：
 * ```json
 * {"team":"football","type":"iframe-match",
 *  "url":"https://games.mobileapi.hupu.com/football#/football/football_recap?matchId=3867210-BATTLE_REPORT",
 *  "matchId":"3867210-BATTLE_REPORT"}
 * ```
 * 官方网页端也是靠 JS 识别 `type` 后动态挂 iframe（SSR 的 HTML 里并没有渲染出该 iframe）；
 * 手机端（m.hupu.com）返回的 `content` 与之完全一致。
 *
 * 若不识别，正文会把这段 JSON 当纯文本原样显示（乱码）。
 */
data class HupuEmbed(
    val type: String,
    val url: String,
    val matchId: String,
) {
    /** 卡片标题：已知类型给中文名，未知类型兜底（避免以后新增类型又变乱码）。 */
    val title: String
        get() = when (type) {
            "iframe-match" -> "比赛战报"
            else -> "嵌入内容"
        }

    /** 卡片副标题（动作提示）。 */
    val subtitle: String
        get() = when (type) {
            "iframe-match" -> "点击查看完整战报"
            else -> "点击查看"
        }

    /** 左侧角标短文字。 */
    val badge: String
        get() = when (type) {
            "iframe-match" -> "战报"
            else -> "嵌入"
        }
}

object HupuEmbedParser {
    /** optString 会把 JSON null 变成字符串 "null"，这里统一按空处理。 */
    private fun str(o: JSONObject, key: String): String {
        val v = o.opt(key) ?: return ""
        if (v == JSONObject.NULL) return ""
        return v.toString().trim()
    }

    /**
     * 识别正文是否为结构化内容描述符；不是则返回 null。
     *
     * 判定条件：去空白（并剥离可能的 `<p>` 包裹）后是 `{...}`，且能解析出**非空的
     * `type` 与 `url`**——普通图文帖正文以 `<p>` 开头，不会误判。
     */
    fun parse(content: String?): HupuEmbed? {
        if (content.isNullOrBlank()) return null
        var s = content.trim()
        if (s.startsWith("<p>") && s.endsWith("</p>")) {
            s = s.removePrefix("<p>").removeSuffix("</p>").trim()
        }
        if (s.length < 2 || !s.startsWith("{") || !s.endsWith("}")) return null
        return try {
            val o = JSONObject(s)
            val type = str(o, "type")
            val url = str(o, "url")
            if (type.isEmpty() || url.isEmpty()) return null
            HupuEmbed(type = type, url = url, matchId = str(o, "matchId"))
        } catch (e: Exception) {
            null
        }
    }
}