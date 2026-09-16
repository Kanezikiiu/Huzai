package com.java.myapplication.data

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 虎扑通用评分（非赛事体系）数据通道。
 *
 * 数据源：
 * - 首页列表：m.hupu.com/score-home 的 SSR HTML 内嵌 __NEXT_DATA__（pageProps.list，30 张主题卡，
 *   每次刷新服务端随机换一批）。该页是 Next.js SSR，必须桌面 UA 才返回完整数据。
 * - 主题树/子项详情/评论：复用 HupuMatchApi 的 bplcommentapi 通道（common_first/common_second
 *   与 lol_match/lol_item 走同一套 score_tree 接口，全部实测匿名可用）。
 */
object HupuCommonApi {
    private const val SCORE_HOME_URL = "https://m.hupu.com/score-home"
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    // 1.169: 共享 client（HupuHttp）——连接池/线程池复用
    private val client: OkHttpClient = HupuHttp.client

    /**
     * 拉取通用评分首页（SSR HTML 文本）。失败返回 null。
     * 注意：不使用 HupuCache——官方语义就是「每次刷新换一批」，缓存会破坏该行为。
     */
    suspend fun fetchScoreHome(forceNetwork: Boolean = false): String? {
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(SCORE_HOME_URL)
                .header("User-Agent", UA)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Referer", "https://m.hupu.com/")
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val html = response.body?.string() ?: return@use null
                    if (!html.contains("__NEXT_DATA__")) return@use null
                    html
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}
