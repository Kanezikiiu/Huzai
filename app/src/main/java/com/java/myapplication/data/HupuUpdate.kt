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
 *   "versionCode": 198,
 *   "versionName": "1.188",
 *   "apkUrl": "https://github.com/Kanezikiiu/Huzai/releases/download/v1.188/huzai-1.188-release.apk",
 *   "releaseUrl": "https://github.com/Kanezikiiu/Huzai/releases/tag/v1.188",
 *   "changelog": ["· 第一条更新说明", "· 第二条更新说明"],
 *   "forceUpdate": false
 * }
 * ```
 * `changelog` 自 1.188 起为**数组**（源文件一眼可读、无需转义换行）；旧版字符串写法仍兼容。
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
    /** 更新说明条目的前缀圆点（用转义写，避免多字节字面量在管道里被归一化） */
    private const val BULLET = '\u00B7'

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
                changelog = readChangelog(o),
                forceUpdate = o.optBoolean("forceUpdate", false),
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 1.188: 读取更新说明，统一归一化为「逐条成行」的字符串。
     *
     * 1) 数组格式（推荐）：`"changelog": ["· 第一条", "· 第二条"]`
     *    —— 源文件一眼可读，不需要在 JSON 里写 `\n` 转义（历史上就是这里踩过坑）。
     * 2) 字符串格式（保留兼容）：`"changelog": "· 第一条\n· 第二条"`，内部换行原样保留。
     * 3) 兜底：字符串里只有 `·` 分隔却没有换行（历史误写成一整行），按 `·` 自动拆行。
     * JSON null / 字段缺失 → 空串。
     */
    private fun readChangelog(o: JSONObject): String {
        o.optJSONArray("changelog")?.let { arr ->
            val items = (0 until arr.length())
                .mapNotNull { i -> arr.optString(i).trim().takeIf { it.isNotEmpty() } }
            if (items.isNotEmpty()) return items.joinToString("\n")
        }
        val rawVal = o.opt("changelog") ?: return ""
        if (rawVal == JSONObject.NULL) return ""
        val raw = rawVal.toString().trim()
        if (raw.isEmpty()) return ""
        if (!raw.contains('\n') && raw.contains(BULLET)) {
            return raw.split(BULLET)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString("\n") { "$BULLET $it" }
        }
        return raw
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
