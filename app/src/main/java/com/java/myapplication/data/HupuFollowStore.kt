package com.java.myapplication.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

/**
 * 1.126 本地关注集合。
 *
 * 服务端没有「我是否已关注 TA」的查询接口（getUserInfo 的 follow_status 恒为 -1，
 * 也没有任何关系查询端点），所以只能本地维护：
 * - 登录后首次需要时，全量拉「我的关注列表」（bbs.mobileapi.../user/getUserFollow，每页 20 条）
 * - 关注 / 取关成功后就地增删
 * - 集合持久化；冷启动先用旧集合渲染，再后台校准
 *
 * 注意：接口只认短 puid（93956731 这种），传 euid 会静默返回空列表，
 * 因此同步时用返回里的 result.puid 做一次「回声校验」，对不上就放弃本次同步（不清空旧集合）。
 */
object HupuFollowStore {
    private const val PREF = "hupu_follow"
    private const val KEY_SET = "set"
    private const val KEY_AT = "at"
    private const val KEY_OWNER = "owner"
    private const val KEY_IDS = "ids"

    /** 单次全量同步页数上限（每页 20 条 → 最多 1000 人），超出视为未完成 */
    private const val PAGE_LIMIT = 50

    /** 同步节流：10 分钟内不重复全量拉 */
    private const val SYNC_TTL_MS = 10 * 60 * 1000L

    private lateinit var prefs: SharedPreferences
    private val followed = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    /** euid → 短 puid 映射（进主页时记录，跨会话保留）：用于第一帧推断关注态 */
    private val idMap = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** 是否已完成一次全量同步（未完成时「未关注」的结论可能不准，UI 可显示加载态） */
    var synced by mutableStateOf(false)
        private set

    /** 同步进行中 */
    var syncing by mutableStateOf(false)
        private set

    /** 集合变化信号：UI 以 remember(version) 订阅 */
    var version by mutableIntStateOf(0)
        private set

    private var lastSyncAt = 0L

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        followed.clear()
        runCatching { followed.addAll(prefs.getStringSet(KEY_SET, emptySet()) ?: emptySet()) }
        idMap.clear()
        runCatching {
            prefs.getStringSet(KEY_IDS, emptySet())?.forEach { kv ->
                val i = kv.indexOf('=')
                if (i > 0) idMap[kv.substring(0, i)] = kv.substring(i + 1)
            }
        }
        lastSyncAt = prefs.getLong(KEY_AT, 0L)
        // 1.127: 曾成功同步过即认为集合有效（跨会话保留），
        // 否则冷启动首帧会把「已关注」误判成「关注」再跳变
        synced = lastSyncAt > 0
    }

    fun isFollowed(puid: String): Boolean = puid.isNotEmpty() && followed.contains(puid)

    /** euid → 短 puid（进过主页就有记录） */
    fun cachedPuid(euid: String): String? = if (euid.isEmpty()) null else idMap[euid]

    /** 记录 euid ↔ puid 映射，供下次第一帧秒判 */
    fun rememberId(euid: String, puid: String) {
        if (euid.isEmpty() || puid.isEmpty() || euid == puid) return
        if (idMap[euid] == puid) return
        if (idMap.size > 600) idMap.clear()
        idMap[euid] = puid
        if (!::prefs.isInitialized) return
        runCatching {
            prefs.edit().putStringSet(KEY_IDS, idMap.entries.map { "${it.key}=${it.value}" }.toSet()).apply()
        }
    }

    /**
     * 快速判定（可第一帧调用）：能确定「已关注」返回 true，否则返回 null（未知）。
     * 绝不返回 false —— 避免 profile / 集合就绪前把「已关注」先渲染成「关注」再跳变。
     */
    fun fastFollowed(id: String): Boolean? = fastFollowedOf(id, followed, idMap)

    /** 纯函数版本（单测覆盖） */
    fun fastFollowedOf(id: String, followed: Set<String>, idMap: Map<String, String>): Boolean? {
        if (id.isEmpty()) return null
        if (followed.contains(id)) return true
        val p = idMap[id] ?: return null
        return if (followed.contains(p)) true else null
    }

    /** 关注 / 取关成功后同步本地集合 */
    fun markFollowed(puid: String, on: Boolean) {
        if (puid.isEmpty() || !::prefs.isInitialized) return
        if (on) followed.add(puid) else followed.remove(puid)
        runCatching { prefs.edit().putStringSet(KEY_SET, followed.toSet()).apply() }
        version++
    }

    /** 换账号 / 退出登录时清空 */
    fun reset() {
        if (!::prefs.isInitialized) return
        followed.clear()
        idMap.clear()
        synced = false
        lastSyncAt = 0L
        runCatching { prefs.edit().clear().apply() }
        version++
    }

    fun shouldSync(): Boolean = !synced || System.currentTimeMillis() - lastSyncAt > SYNC_TTL_MS

    /**
     * 全量同步「我的关注列表」。返回是否成功。
     * 失败（未登录 / 网络异常 / puid 校验不过）时保留旧集合，synced 不变。
     */
    suspend fun sync(force: Boolean = false): Boolean {
        if (!::prefs.isInitialized) return false
        if (syncing) return synced
        if (!force && !shouldSync()) return synced
        val my = HupuAccount.myPuid()
        if (my.isEmpty() || !HupuAccount.isLoggedIn) return false

        syncing = true
        return try {
            val all = mutableSetOf<String>()
            var page = 1
            var more = true
            var ok = false
            while (more && page <= PAGE_LIMIT) {
                val body = HupuApi.fetchUserFollow(my, page) ?: break
                val o = runCatching { JSONObject(body) }.getOrNull() ?: break
                val res = o.optJSONObject("result") ?: break
                // 回声校验：result.puid 应为当前登录者；对不上说明 puid 传错（euid 会静默空列表）
                val echo = res.optString("puid")
                if (echo.isNotEmpty() && echo != my) break
                ok = true
                val arr = res.optJSONArray("list")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val p = arr.optJSONObject(i)?.optString("puid") ?: ""
                        if (p.isNotEmpty() && p != "null") all.add(p)
                    }
                }
                more = res.optBoolean("nextPage", false)
                page++
            }
            if (ok) {
                followed.clear()
                followed.addAll(all)
                lastSyncAt = System.currentTimeMillis()
                synced = true
                runCatching {
                    prefs.edit()
                        .putStringSet(KEY_SET, all)
                        .putLong(KEY_AT, lastSyncAt)
                        .putString(KEY_OWNER, my)
                        .apply()
                }
                version++
            }
            ok
        } catch (e: Exception) {
            false
        } finally {
            syncing = false
        }
    }
}