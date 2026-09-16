package com.java.myapplication.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

/**
 * 1.177: 检查更新。
 *
 * 数据源为仓库根目录的 `version.json`（匿名可读；仓库转 public 后生效）：
 * ```json
 * {
 *   "versionCode": 187,
 *   "versionName": "1.177",
 *   "apkUrl": "https://github.com/Kanezikiiu/Huzai/releases/download/v1.177/huzai-1.177-release.apk",
 *   "releaseUrl": "https://github.com/Kanezikiiu/Huzai/releases/tag/v1.177",
 *   "changelog": "· 新增检查更新…",
 *   "forceUpdate": false
 * }
 * ```
 *
 * 为什么不用 GitHub API `releases/latest`：该接口**不返回 versionCode**（只有 tag_name），
 * 且匿名限流 60 次/小时；自建 `version.json` 可控、可携带版本号 / 更新说明 / 强制更新标记。
 */
data class HupuUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val releaseUrl: String,
    val changelog: String,
    val forceUpdate: Boolean,
)

/**
 * `version.json` 解析器（纯函数，可单测）。
 * 字段缺失或非法（versionCode<=0 或 versionName 为空）时返回 null，由调用方静默处理。
 */
object HupuUpdateParser {
    fun parse(json: String): HupuUpdateInfo? {
        return try {
            val o = JSONObject(json)
            val code = o.optInt("versionCode", 0)
            val name = o.optString("versionName").trim()
            if (code <= 0 || name.isEmpty()) return null
            HupuUpdateInfo(
                versionCode = code,
                versionName = name,
                apkUrl = o.optString("apkUrl").trim(),
                releaseUrl = o.optString("releaseUrl").trim(),
                changelog = o.optString("changelog").trim(),
                forceUpdate = o.optBoolean("forceUpdate", false),
            )
        } catch (e: Exception) {
            null
        }
    }
}

/** 检查更新结果。 */
sealed interface HupuUpdateResult {
    /** 已是最新（远端版本号 <= 当前） */
    data object Latest : HupuUpdateResult

    /** 发现新版本 */
    data class Available(val info: HupuUpdateInfo) : HupuUpdateResult

    /** 检查失败（网络异常 / 解析失败 / 仓库尚未公开） */
    data object Failed : HupuUpdateResult
}

object HupuUpdate {
    /**
     * version.json 候选地址：主用 GitHub raw，失败回落 jsDelivr CDN
     * （@main 指向默认分支；国内可达性通常更好）。任一成功即返回。
     */
    private val urls = listOf(
        "https://raw.githubusercontent.com/Kanezikiiu/Huzai/main/version.json",
        "https://cdn.jsdelivr.net/gh/Kanezikiiu/Huzai@main/version.json",
    )

    /** 拉取远端最新版本信息；全部候选失败返回 null。 */
    suspend fun fetchLatest(): HupuUpdateInfo? = withContext(Dispatchers.IO) {
        for (u in urls) {
            val info = runCatching {
                val request = Request.Builder()
                    .url(u)
                    .header("Accept", "application/json")
                    .build()
                HupuHttp.client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) null else response.body?.string()?.let(HupuUpdateParser::parse)
                }
            }.getOrNull()
            if (info != null) return@withContext info
        }
        null
    }

    /**
     * 纯判定逻辑（可单测）：远端信息与当前 versionCode 比较。
     * info 为 null 视为检查失败。
     */
    fun decide(currentCode: Int, info: HupuUpdateInfo?): HupuUpdateResult {
        if (info == null) return HupuUpdateResult.Failed
        return if (info.versionCode > currentCode) HupuUpdateResult.Available(info)
        else HupuUpdateResult.Latest
    }

    /** 完整检查流程：拉取 + 判定。 */
    suspend fun check(currentCode: Int): HupuUpdateResult =
        decide(currentCode, fetchLatest())
}
