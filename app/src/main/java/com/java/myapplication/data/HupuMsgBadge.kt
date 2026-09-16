package com.java.myapplication.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * 1.106: 消息未读角标。
 * 数据源分工（实测定案，规避官方"幽灵未读"）：
 *  - 三类通知（提到/评论/亮了）：官方消息页 SSR 的 unread_count（msg_type=1/2/3），实测准确。
 *  - 私信：getPmList 各会话 unread 求和，不用 SSR 的 msg_type=null 计数
 *    （服务端计数器与可见会话存在恒定差值"幽灵未读"，列表字段才是真实口径）。
 *
 * 拉取时机：进入「我的」页 / 打开消息页 / 关闭子页与聊天页 / 前台每 2 分钟轮询。
 */
object HupuMsgBadge {
    var mention by androidx.compose.runtime.mutableIntStateOf(0)
    var reply by androidx.compose.runtime.mutableIntStateOf(0)
    var light by androidx.compose.runtime.mutableIntStateOf(0)
    var pm by androidx.compose.runtime.mutableIntStateOf(0)

    val total: Int get() = mention + reply + light + pm

    @Volatile
    var fetching = false

    /**
     * 1.169: 应用级协程作用域（替代原先的 GlobalScope）。
     * 显式持有、可整段取消（见 [cancelAll]），行为与 GlobalScope 等价但受我们控制；
     * 仍配合 [fetching] 做重入保护。
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 1.169: 取消所有在途角标拉取（可在进程/页面销毁钩子中调用）。 */
    fun cancelAll() {
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancelChildren()
    }

    /** 拉取一次（静默，失败维持原值）。pmRefetch 提供私信会话 unread 求和（复用消息页已拉的数据）。 */
    fun refresh(pmSumOverride: Int? = null, onDone: (() -> Unit)? = null) {
        if (fetching) { onDone?.invoke(); return }
        fetching = true
        scope.launch {
            try {
                val html = fetchMessageSsr()
                if (html != null) parseSsrBadge(html)?.let { (m, r, l) ->
                    mention = m; reply = r; light = l
                }
                val pmSum = pmSumOverride ?: fetchPmUnreadSum()
                if (pmSum != null) pm = pmSum
            } catch (_: Exception) {
            } finally {
                fetching = false
                onDone?.invoke()
            }
        }
    }

    /** my.hupu.com/message?tabKey=1 SSR 拉取（带 Cookie，桌面 UA，不带缓存）。 */
    private suspend fun fetchMessageSsr(): String? = try {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val req = okhttp3.Request.Builder()
                .url("https://my.hupu.com/message?tabKey=1")
                .header("User-Agent", HupuApi.DESKTOP_UA)
                .header("Cookie", HupuAccount.loadCookieHeaderPublic())
                .header("Referer", "https://my.hupu.com/")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()
            // 1.169: 复用共享 client（原为每次调用 new 一个 OkHttpClient）
            HupuHttp.client.newCall(req).execute().use { r ->
                if (r.isSuccessful) r.body?.string() else null
            }
        }
    } catch (_: Exception) {
        null
    }

    /** SSR $$data 中 msg_type=1/2/3 的 unread_count → (mention, reply, light)。 */
    internal fun parseSsrBadge(html: String): Triple<Int, Int, Int>? {
        return try {
            val m = Regex("\"msg_type\":(\\d+),\"unread_count\":(\\d+)").findAll(html)
            var mention = -1; var reply = -1; var light = -1
            for (x in m) {
                when (x.groupValues[1].toInt()) {
                    1 -> mention = x.groupValues[2].toInt()
                    2 -> reply = x.groupValues[2].toInt()
                    3 -> light = x.groupValues[2].toInt()
                }
            }
            if (mention >= 0 && reply >= 0 && light >= 0) Triple(mention, reply, light) else null
        } catch (_: Exception) {
            null
        }
    }

    /** 私信真实未读：getPmList 第一页各会话 unread 求和。失败返回 null（维持原值）。 */
    private suspend fun fetchPmUnreadSum(): Int? {
        val body = JSONObject()
            .put("unreadList", 0)
            .put("page", JSONObject().put("pageNum", 1).put("pageSize", 20))
        val json = HupuApi.postSpaceApiJson("pm/getPmList", body.toString()) ?: return null
        return try {
            val arr = JSONObject(json).optJSONObject("data")?.optJSONArray("dataList") ?: return null
            var sum = 0
            for (i in 0 until arr.length()) {
                sum += arr.optJSONObject(i)?.optInt("unread", 0) ?: 0
            }
            sum
        } catch (_: Exception) {
            null
        }
    }
}
