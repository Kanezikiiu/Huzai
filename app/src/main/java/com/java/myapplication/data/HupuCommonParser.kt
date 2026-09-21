package com.java.myapplication.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * 虎扑通用评分解析器。
 *
 * 与赛事评分（HupuMatchParser）的区别：
 * - 首页是 SSR HTML（__NEXT_DATA__）而非纯 JSON 接口，必须桌面 UA 抓取
 * - 树解析产出 [HupuCommonTree]（无 rounds 概念，第一层就是子项流，支持分页续拉）
 * - 叶子详情/评论复用赛事解析（getSelfByBizKey / primarySingleRow 数据外壳一致）
 */
object HupuCommonParser {

    /**
     * AOSP org.json 的 optString 会把 JSON null 变成 n-u-l-l 四个字符的字面量
     * （1.7 赛程卡的「四个 null」同款坑）。此处用字符拼装、不在源码里写出该字面量，
     * 免得后来人把它看成空串（两者只差一次 String.valueOf）。
     */
    private val NULL_LITERAL = charArrayOf('n', 'u', 'l', 'l').concatToString()

    /**
     * 取字符串字段（JSON null 安全）。
     *
     * Android org.json 的 optString 对 JSON null 会返回 n-u-l-l 字面量
     * （1.7 赛程卡的「四个 null」同款坑）。主题简介 desc 服务端常给 null，
     * 直接进数据模型后卡片会多渲染出一行 n-u-l-l —— 这里统一按空处理。
     */
    private fun str(o: JSONObject?, key: String): String {
        val v = o?.opt(key) ?: return ""
        if (v == JSONObject.NULL) return ""
        val t = v.toString().trim()
        return if (t.isEmpty() || t == NULL_LITERAL) "" else t
    }

    /** 同上，取 JSONArray 第 i 项（图片数组等）。 */
    private fun strAt(arr: JSONArray?, i: Int): String {
        val v = arr?.opt(i) ?: return ""
        if (v == JSONObject.NULL) return ""
        val t = v.toString().trim()
        return if (t.isEmpty() || t == NULL_LITERAL) "" else t
    }

    /** 首页 SSR：__NEXT_DATA__ → pageProps.list[] → subject/item/detail.items */
    fun parseScoreHome(html: String): List<HupuCommonSubject> {
        return try {
            val m = Regex(
                "<script id=\"__NEXT_DATA__\" type=\"application/json\">(.*?)</script>",
                RegexOption.DOT_MATCHES_ALL,
            ).find(html) ?: return emptyList()
            val root = JSONObject(m.groupValues[1])
            val arr = root.optJSONObject("props")
                ?.optJSONObject("pageProps")?.optJSONArray("list") ?: return emptyList()
            val out = mutableListOf<HupuCommonSubject>()
            for (i in 0 until arr.length()) {
                val x = arr.optJSONObject(i) ?: continue
                val subj = x.optJSONObject("subject") ?: continue
                val it = x.optJSONObject("item") ?: continue
                // 出口参数在 url 的编码 query 里：outBizType%3Dcommon_first%26outBizNo%3D1170
                // （= 和 & 均被编码；兼容未编码形态）
                val mm = Regex("outBizType(?:%3D|=)([a-z_]+)(?:%26|&)outBizNo(?:%3D|=)(\\d+)")
                    .find(str(it, "url"))
                    ?: continue
                // detail.items：首页预置的部分子项（一般 3 个，字段名与树接口不同，需映射）
                val items = mutableListOf<HupuScoreItem>()
                x.optJSONObject("detail")?.optJSONArray("items")?.let { di ->
                    for (j in 0 until di.length()) {
                        val d = di.optJSONObject(j) ?: continue
                        val nm = str(d, "name")
                        if (nm.isEmpty()) continue
                        items += HupuScoreItem(
                            bizId = str(d, "itemBizId"),
                            bizType = str(d, "itemBizType").ifEmpty { "common_second" },
                            name = nm,
                            image = str(d, "cover").ifEmpty { null },
                            scoreAvg = str(d, "score").ifEmpty { "-" },
                            scorePersonCount = d.optLong("scoreCountNum", 0L),
                            hotComment = str(d, "hottestComment"),
                        )
                    }
                }
                out += HupuCommonSubject(
                    bizType = mm.groupValues[1],
                    bizNo = mm.groupValues[2],
                    name = str(it, "name").ifEmpty { str(subj, "label") },
                    score = str(it, "score"),
                    scoreCountNum = it.optLong("scoreCountNum", 0L),
                    // 简介：服务端对无简介的主题给 JSON null，optString 会产出 ""字面量，
                    // 卡片上就多出一行 ""（1.187 修复，见 str()）
                    desc = str(it, "desc"),
                    bgColorDay = str(it, "bgColorDay").ifEmpty { null },
                    scoreColorDay = str(it, "scoreColorDay").ifEmpty { null },
                    items = items,
                )
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 主题树（getCurAndSubNodeByBizKey CHILD）：self=主题节点，pageResult.data[]=子项（分页） */
    fun parseCommonTree(json: String): HupuCommonTree? {
        return try {
            val root = JSONObject(json)
            if (root.optInt("code", -1) != 1) return null
            val data = root.optJSONObject("data") ?: return null
            val selfNode = data.optJSONObject("self")?.optJSONObject("node") ?: return null
            val name = str(selfNode, "name")
            if (name.isEmpty()) return null
            val pr = data.optJSONObject("pageResult")
            val items = mutableListOf<HupuScoreItem>()
            pr?.optJSONArray("data")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val n = arr.optJSONObject(i)?.optJSONObject("node") ?: continue
                    val nm = str(n, "name")
                    if (nm.isEmpty()) continue
                    items += HupuScoreItem(
                        bizId = str(n, "bizId"),
                        bizType = str(n, "bizType").ifEmpty { "common_second" },
                        name = nm,
                        image = strAt(n.optJSONArray("image"), 0).ifEmpty { null },
                        scoreAvg = formatAvg(n.optDouble("scoreAvg", 0.0)),
                        scorePersonCount = n.optLong("summedScorePersonCount", n.optLong("scorePersonCount", 0L)),
                        hotComment = str(n.optJSONArray("hotCommentModels")?.optJSONObject(0), "commentContent"),
                    )
                }
            }
            HupuCommonTree(
                name = name,
                image = strAt(selfNode.optJSONArray("image"), 0).ifEmpty { null },
                desc = strAt(selfNode.optJSONObject("infoJson")?.optJSONArray("desc"), 0),
                scoreAvg = formatAvg(selfNode.optDouble("scoreAvg", 0.0)),
                scorePersonCount = selfNode.optLong("summedScorePersonCount", selfNode.optLong("scorePersonCount", 0L)),
                items = items,
                totalCount = pr?.optLong("totalCount", 0L) ?: 0L,
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun formatAvg(v: Double): String =
        if (v <= 0.0) "-" else if (v == v.toLong().toDouble()) v.toLong().toString() else String.format(java.util.Locale.CHINA, "%.1f", v)
}