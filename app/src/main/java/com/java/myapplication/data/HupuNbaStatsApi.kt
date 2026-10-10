package com.java.myapplication.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * 1.200：`nba.hupu.com` 数据统计 / 文字实录页的抓取。
 *
 * 这两页**没有 API**（老版 PC 服务端渲染页），只能抓 HTML 再交给
 * [com.java.myapplication.data.parseNbaBoxScore] / [parseNbaPlayByPlay] 解析。
 * 桌面的 UA 是必须的：带移动 UA 会拿到只认 JS 的壳页。
 *
 * 缓存：走 [HupuCache]（默认 10 分钟）。**进行中的比赛**由调用方传
 * `forceNetwork = true`（配合 30s 轮询），已结束的比赛内容恒定，多命中缓存。
 */
object HupuNbaStatsApi {

    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private const val REFERER = "https://nba.hupu.com/"

    private val client = HupuHttp.client

    /** 日期页：`/games/{yyyy-MM-dd}`——每场含「两队名 + 比分 + gameId」 */
    suspend fun fetchDatePage(date: String, forceNetwork: Boolean = false): String? =
        fetch("nba-stats-date-$date", "https://nba.hupu.com/games/$date", forceNetwork)

    /** 数据统计页 */
    suspend fun fetchBoxScore(gameId: String, forceNetwork: Boolean = false): String? =
        fetch("nba-stats-box-$gameId", "https://nba.hupu.com/games/boxscore/$gameId", forceNetwork)

    /** 文字实录页 */
    suspend fun fetchPlayByPlay(gameId: String, forceNetwork: Boolean = false): String? =
        fetch("nba-stats-pbp-$gameId", "https://nba.hupu.com/games/playbyplay/$gameId", forceNetwork)

    /** 定位本场的 gameId（评分侧 → 日期页 → 按队名 join；详见 [parseNbaGameId]） */
    suspend fun resolveGameId(
        date: String,
        home: String,
        away: String,
        homeScore: String,
        awayScore: String,
        forceNetwork: Boolean = false,
    ): String? {
        if (date.isBlank()) return null
        val html = fetchDatePage(date, forceNetwork) ?: return null
        return parseNbaGameId(html, home, away, homeScore, awayScore)
    }

    private suspend fun fetch(cacheKey: String, url: String, forceNetwork: Boolean): String? =
        withContext(Dispatchers.IO) {
            if (!forceNetwork) {
                HupuCache.get(cacheKey)?.let { return@withContext it }
            }
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Accept", "text/html,application/xhtml+xml,*/*")
                .header("Referer", REFERER)
                .build()
            try {
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val html = resp.body?.string() ?: return@use null
                    // 必须是真页面（错误页 / 空体 / JS 壳页都丢掉，避免把垃圾当成功缓存住）
                    if (!html.contains("<table") || !html.contains("nba.hupu.com")) return@use null
                    HupuCache.put(cacheKey, html)
                    html
                }
            } catch (e: Exception) {
                null
            }
        }
}