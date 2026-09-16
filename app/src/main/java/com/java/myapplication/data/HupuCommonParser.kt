package com.java.myapplication.data

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
                    .find(it.optString("url"))
                    ?: continue
                // detail.items：首页预置的部分子项（一般 3 个，字段名与树接口不同，需映射）
                val items = mutableListOf<HupuScoreItem>()
                x.optJSONObject("detail")?.optJSONArray("items")?.let { di ->
                    for (j in 0 until di.length()) {
                        val d = di.optJSONObject(j) ?: continue
                        val nm = d.optString("name")
                        if (nm.isEmpty()) continue
                        items += HupuScoreItem(
                            bizId = d.optString("itemBizId"),
                            bizType = d.optString("itemBizType").ifEmpty { "common_second" },
                            name = nm,
                            image = d.optString("cover").ifEmpty { null },
                            scoreAvg = d.optString("score").ifEmpty { "-" },
                            scorePersonCount = d.optLong("scoreCountNum", 0L),
                            hotComment = d.optString("hottestComment").ifEmpty { "" },
                        )
                    }
                }
                out += HupuCommonSubject(
                    bizType = mm.groupValues[1],
                    bizNo = mm.groupValues[2],
                    name = it.optString("name").ifEmpty { subj.optString("label") },
                    score = it.optString("score").ifEmpty { "" },
                    scoreCountNum = it.optLong("scoreCountNum", 0L),
                    desc = it.optString("desc").ifEmpty { "" },
                    bgColorDay = it.optString("bgColorDay").ifEmpty { null },
                    scoreColorDay = it.optString("scoreColorDay").ifEmpty { null },
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
            val name = selfNode.optString("name")
            if (name.isEmpty()) return null
            val pr = data.optJSONObject("pageResult")
            val items = mutableListOf<HupuScoreItem>()
            pr?.optJSONArray("data")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val n = arr.optJSONObject(i)?.optJSONObject("node") ?: continue
                    val nm = n.optString("name")
                    if (nm.isEmpty()) continue
                    items += HupuScoreItem(
                        bizId = n.optString("bizId"),
                        bizType = n.optString("bizType").ifEmpty { "common_second" },
                        name = nm,
                        image = n.optJSONArray("image")?.optString(0)?.ifEmpty { null },
                        scoreAvg = formatAvg(n.optDouble("scoreAvg", 0.0)),
                        scorePersonCount = n.optLong("summedScorePersonCount", n.optLong("scorePersonCount", 0L)),
                        hotComment = n.optJSONArray("hotCommentModels")
                            ?.optJSONObject(0)?.optString("commentContent")?.ifEmpty { null }
                            ?: "",
                    )
                }
            }
            HupuCommonTree(
                name = name,
                image = selfNode.optJSONArray("image")?.optString(0)?.ifEmpty { null },
                desc = selfNode.optJSONObject("infoJson")?.optJSONArray("desc")
                    ?.optString(0)?.ifEmpty { "" } ?: "",
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