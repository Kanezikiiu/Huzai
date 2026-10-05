package com.java.myapplication.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * 局域网数据同步（1.2xx）：可同步的数据项、载荷编解码、以及**纯合并 / 覆盖逻辑**。
 *
 * 设计要点：
 *  · 合并类（关键词 / 黑名单 / 收藏专区）——本地在前、去重追加、按各自上限截断，超额如实回报；
 *  · 覆盖类（评分频道 / 首页频道 / 默认启动页）——整段替换（空 = 恢复官方默认）；
 *  · 评分频道跨版本（新版 → 旧版）：接收端按本地已知频道 id 白名单过滤，未知频道丢弃并回报。
 *
 * 本文件的编解码与合并函数均为纯函数（仅依赖 org.json / 纯列表），可直接单测。
 */
enum class SyncItem(
    val key: String,
    val label: String,
    /** true = 接收方直接**覆盖**本地；false = 与本地**合并** */
    val overwrite: Boolean,
) {
    TITLE_KEYWORDS("titleKeywords", "标题关键词", false),
    ZONE_KEYWORDS("zoneKeywords", "分区关键词", false),
    COMMENT_KEYWORDS("commentKeywords", "评论关键词", false),
    BLACKLIST("blacklist", "黑名单", false),
    FAVORITE_TOPICS("favoriteTopics", "收藏专区", false),
    SCORE_GAMES("scoreGames", "评分频道自定义", true),
    HOME_TOPICS("homeTopics", "首页频道自定义", true),
    // ── 「默认事项」组（同步页里以父子结构展示，见 DEFAULT_ITEM_GROUP）──
    START_TAB("startTab", "默认启动页", true),
    TOPIC_SORT("topicSort", "专区默认排序", true),
    REPLY_SORT("replySort", "帖子回复默认排序", true),
    SCORE_SORT("scoreSort", "评分评论默认排序", true),
}

/**
 * 「默认事项」分组：这四项语义同族（都是「设置里的默认值」，且都是**覆盖**语义），
 * 在同步页里收成「父项 + 四个子项」，避免把主列表撑得太长。
 */
val DEFAULT_ITEM_GROUP = listOf(
    SyncItem.START_TAB,
    SyncItem.TOPIC_SORT,
    SyncItem.REPLY_SORT,
    SyncItem.SCORE_SORT,
)

/** 载荷协议版本：字段含义变更时 +1，接收端不认则拒收 */
const val SYNC_SCHEMA = 1

/** 一次同步的载荷 */
data class SyncBundle(
    val versionCode: Int,
    val versionName: String,
    /** 只含被勾选的项；value = 该项的原始 JSON 文本 */
    val values: Map<SyncItem, String>,
)

/** 合并结果：结果列表 + 新增条数 + 因超限被丢弃条数 */
data class MergeOutcome<T>(val list: List<T>, val added: Int, val dropped: Int)

// ---------------- 纯合并逻辑 ----------------

/** 字符串列表合并（标题 / 分区 / 评论关键词）：本地在前，追加去重新项，按 max 截断 */
fun mergeStringList(local: List<String>, incoming: List<String>, max: Int): MergeOutcome<String> {
    val out = ArrayList(local.take(max))
    val seen = HashSet(out)
    var added = 0
    var dropped = 0
    for (s in incoming) {
        if (s.isEmpty() || s in seen) continue
        if (out.size >= max) {
            dropped++
            continue
        }
        seen.add(s)
        out.add(s)
        added++
    }
    return MergeOutcome(out, added, dropped)
}

/** 专区列表合并（收藏专区）：按 url 去重，本地在前 */
fun mergeTopics(
    local: List<HupuTopicInfo>,
    incoming: List<HupuTopicInfo>,
    max: Int,
): MergeOutcome<HupuTopicInfo> {
    val out = ArrayList(local.take(max))
    val seen = out.mapNotNull { it.url.takeIf { u -> u.isNotEmpty() } }.toHashSet()
    var added = 0
    var dropped = 0
    for (t in incoming) {
        if (t.url.isEmpty() || t.url in seen) continue
        if (out.size >= max) {
            dropped++
            continue
        }
        seen.add(t.url)
        out.add(t)
        added++
    }
    return MergeOutcome(out, added, dropped)
}

/**
 * 黑名单合并：以「人」为单位——共享任一 id 即视为同一人，合并 id 集、昵称 / 头像取先有值、
 * 拉黑时间取更早的有效值。本地在前，新人追加，按 max 截断。
 */
fun mergeBlacklist(
    local: List<HupuBlacklistEntry>,
    incoming: List<HupuBlacklistEntry>,
    max: Int,
): MergeOutcome<HupuBlacklistEntry> {
    val out = ArrayList(local.take(max))
    val byId = HashMap<String, Int>()
    out.forEachIndexed { i, e -> e.ids.forEach { byId[it] = i } }
    var added = 0
    var dropped = 0
    for (inc in incoming) {
        val keys = inc.ids.filter { it.isNotEmpty() }
        if (keys.isEmpty()) continue
        val hit = keys.firstNotNullOfOrNull { byId[it] }
        if (hit != null) {
            val old = out[hit]
            val mergedIds = LinkedHashMap<String, String>().also { m ->
                old.ids.forEach { m[it] = it }
                keys.forEach { m[it] = it }
            }.keys.toList()
            val name = if (old.name.isNotEmpty()) old.name else inc.name
            val avatar = if (old.avatar.isNotEmpty()) old.avatar else inc.avatar
            val at = when {
                old.at <= 0L -> inc.at
                inc.at <= 0L -> old.at
                else -> minOf(old.at, inc.at)
            }
            if (mergedIds != old.ids || name != old.name || avatar != old.avatar || at != old.at) {
                out[hit] = old.copy(ids = mergedIds, name = name, avatar = avatar, at = at)
                mergedIds.forEach { byId[it] = hit }
            }
        } else {
            if (out.size >= max) {
                dropped++
                continue
            }
            out.add(inc)
            val i = out.size - 1
            keys.forEach { byId[it] = i }
            added++
        }
    }
    return MergeOutcome(out, added, dropped)
}

/** 评分频道跨版本过滤：只保留本地已知的 id（保序、去重），返回 (保留, 未知) */
fun filterKnownScoreGames(
    incoming: List<String>,
    known: List<String>,
): Pair<List<String>, List<String>> {
    val set = known.toHashSet()
    val kept = ArrayList<String>()
    val unknown = ArrayList<String>()
    for (id in incoming) {
        if (id.isEmpty() || id in kept) continue
        if (id in set) kept.add(id) else unknown.add(id)
    }
    return kept to unknown
}

// ---------------- 载荷编解码 ----------------

/** 编码一次同步的载荷为 JSON 文本 */
fun encodeSyncBundle(b: SyncBundle): String {
    val o = JSONObject()
    o.put("schema", SYNC_SCHEMA)
    o.put("versionCode", b.versionCode)
    o.put("versionName", b.versionName)
    val items = JSONObject()
    for ((item, raw) in b.values) {
        // 把「原始 JSON 文本」按 JSON 值内嵌（数组 / 对象 / 数字都支持），避免二次转义
        val v = runCatching { org.json.JSONTokener(raw).nextValue() }.getOrNull() ?: continue
        items.put(item.key, v)
    }
    o.put("items", items)
    return o.toString()
}

/** 解码载荷；schema 不符 / 结构损坏 → null（调用方据此拒收，绝不落地半截数据） */
fun decodeSyncBundle(json: String): SyncBundle? = runCatching {
    val o = JSONObject(json)
    if (o.optInt("schema", 0) != SYNC_SCHEMA) return null
    val items = o.optJSONObject("items") ?: return null
    val values = LinkedHashMap<SyncItem, String>()
    for (item in SyncItem.entries) {
        if (!items.has(item.key)) continue
        val v = items.opt(item.key) ?: continue
        if (v === JSONObject.NULL) continue
        values[item] = v.toString()
    }
    SyncBundle(o.optInt("versionCode", 0), o.optString("versionName"), values)
}.getOrNull()

/** 字符串数组解码（坏数据 → 空表） */
fun decodeStringArray(raw: String): List<String> = runCatching {
    val a = JSONArray(raw)
    (0 until a.length()).mapNotNull { i -> a.optString(i).takeIf { s -> s.isNotEmpty() } }
}.getOrDefault(emptyList())

/** 一项的落地结果（回执里逐项回传，供结果对话框展示） */
data class SyncItemResult(val label: String, val summary: String, val dropped: Int = 0)

/** 回执：ok=false 时 message 说明原因（如「对方取消」「格式不正确」，此时 results 为空） */
data class SyncAck(val ok: Boolean, val message: String, val results: List<SyncItemResult>)

fun encodeSyncAck(ok: Boolean, message: String, results: List<SyncItemResult> = emptyList()): String {
    val o = JSONObject()
    o.put("ok", ok)
    o.put("message", message)
    val arr = JSONArray()
    results.forEach { r ->
        val j = JSONObject()
        j.put("label", r.label)
        j.put("summary", r.summary)
        j.put("dropped", r.dropped)
        arr.put(j)
    }
    o.put("results", arr)
    return o.toString()
}

fun decodeSyncAck(json: String): SyncAck? = runCatching {
    val o = JSONObject(json)
    val results = o.optJSONArray("results")?.let { a ->
        (0 until a.length()).mapNotNull { i ->
            val j = a.optJSONObject(i) ?: return@mapNotNull null
            SyncItemResult(j.optString("label"), j.optString("summary"), j.optInt("dropped", 0))
        }
    } ?: emptyList()
    SyncAck(o.optBoolean("ok", false), o.optString("message"), results)
}.getOrNull()

// ---------------- 与 HupuPrefs 的对接（导出 / 应用） ----------------

/** 本机默认启动页可选名称（与「我的」页一致） */
val SYNC_START_TAB_NAMES = listOf("首页", "专区", "评分", "我的")

/** 某项当前的数据量（发送页勾选项的计数标签） */
fun syncItemCount(item: SyncItem): Int = when (item) {
    SyncItem.TITLE_KEYWORDS -> HupuPrefs.loadFilterKeywords().title.size
    SyncItem.ZONE_KEYWORDS -> HupuPrefs.loadFilterKeywords().zone.size
    SyncItem.COMMENT_KEYWORDS -> HupuPrefs.loadFilterKeywords().comment.size
    SyncItem.BLACKLIST -> HupuPrefs.loadBlacklistEntries().size
    SyncItem.FAVORITE_TOPICS -> HupuPrefs.loadFavoriteTopics().size
    SyncItem.SCORE_GAMES ->
        if (HupuPrefs.hasCustomScoreGames()) HupuPrefs.loadScoreGames().size
        else HupuMatchApi.GAMES.size
    SyncItem.HOME_TOPICS ->
        if (HupuPrefs.hasCustomHomeTopics()) HupuPrefs.loadHomeTopics().size
        else 0
    SyncItem.START_TAB -> 1
    SyncItem.TOPIC_SORT -> 1
    SyncItem.REPLY_SORT -> 1
    SyncItem.SCORE_SORT -> 1
}

/** 发送端：把某项导出为原始 JSON 文本 */
fun exportSyncItem(item: SyncItem): String = when (item) {
    SyncItem.TITLE_KEYWORDS -> JSONArray(HupuPrefs.loadFilterKeywords().title).toString()
    SyncItem.ZONE_KEYWORDS -> JSONArray(HupuPrefs.loadFilterKeywords().zone).toString()
    SyncItem.COMMENT_KEYWORDS -> JSONArray(HupuPrefs.loadFilterKeywords().comment).toString()
    SyncItem.BLACKLIST -> encodeBlacklistEntriesJson(HupuPrefs.loadBlacklistEntries())
    SyncItem.FAVORITE_TOPICS -> encodeFavoriteTopicsJson(HupuPrefs.loadFavoriteTopics())
    SyncItem.SCORE_GAMES -> JSONArray(
        if (HupuPrefs.hasCustomScoreGames()) HupuPrefs.loadScoreGames()
        else HupuMatchApi.GAMES.map { it.first },
    ).toString()
    SyncItem.HOME_TOPICS -> encodeHomeTopicsJson(HupuPrefs.loadHomeTopics())
    SyncItem.START_TAB -> HupuPrefs.loadStartTab().toString()
    // 单值项统一包成单元素数组 → 复用 decodeStringArray，编解码口径一致
    SyncItem.TOPIC_SORT -> JSONArray(listOf(HupuPrefs.loadDefaultTopicSortTitle())).toString()
    SyncItem.REPLY_SORT -> HupuPrefs.loadDefaultReplySort().toString()
    SyncItem.SCORE_SORT -> JSONArray(listOf(HupuPrefs.loadDefaultScoreSort())).toString()
}

/** 一项的应用结果（summary 用于回执展示；dropped = 因上限/本版本不支持而未能写入的条数） */
data class SyncApplyOutcome(val item: SyncItem, val summary: String, val dropped: Int = 0)

private fun overSuffix(dropped: Int): String =
    if (dropped > 0) " · $dropped 条超限未加入" else ""

/**
 * 接收端：把某项原始 JSON 写入本地。
 * @param knownGames 本地已知的评分频道 id（跨版本过滤用）
 */
fun applySyncItem(
    item: SyncItem,
    raw: String,
    knownGames: List<String> = HupuMatchApi.GAMES.map { it.first },
): SyncApplyOutcome = when (item) {
    SyncItem.TITLE_KEYWORDS -> {
        val cur = HupuPrefs.loadFilterKeywords()
        val r = mergeStringList(cur.title, decodeStringArray(raw), HupuPrefs.MAX_FILTER_KEYWORDS)
        HupuPrefs.saveFilterKeywords(cur.copy(title = r.list))
        SyncApplyOutcome(item, "标题关键词 +${r.added}" + overSuffix(r.dropped), r.dropped)
    }
    SyncItem.ZONE_KEYWORDS -> {
        val cur = HupuPrefs.loadFilterKeywords()
        val r = mergeStringList(cur.zone, decodeStringArray(raw), HupuPrefs.MAX_FILTER_KEYWORDS)
        HupuPrefs.saveFilterKeywords(cur.copy(zone = r.list))
        SyncApplyOutcome(item, "分区关键词 +${r.added}" + overSuffix(r.dropped), r.dropped)
    }
    SyncItem.COMMENT_KEYWORDS -> {
        val cur = HupuPrefs.loadFilterKeywords()
        val r = mergeStringList(cur.comment, decodeStringArray(raw), HupuPrefs.MAX_FILTER_KEYWORDS)
        HupuPrefs.saveFilterKeywords(cur.copy(comment = r.list))
        SyncApplyOutcome(item, "评论关键词 +${r.added}" + overSuffix(r.dropped), r.dropped)
    }
    SyncItem.BLACKLIST -> {
        val r = mergeBlacklist(
            HupuPrefs.loadBlacklistEntries(),
            decodeBlacklistEntriesJson(raw),
            HupuBlacklist.MAX_ENTRIES,
        )
        HupuPrefs.replaceBlacklist(r.list)
        SyncApplyOutcome(item, "黑名单 +${r.added} 人" + overSuffix(r.dropped), r.dropped)
    }
    SyncItem.FAVORITE_TOPICS -> {
        val r = mergeTopics(
            HupuPrefs.loadFavoriteTopics(),
            decodeFavoriteTopicsJson(raw),
            HupuPrefs.MAX_FAVORITE_TOPICS,
        )
        HupuPrefs.saveFavoriteTopics(r.list)
        SyncApplyOutcome(item, "收藏专区 +${r.added}" + overSuffix(r.dropped), r.dropped)
    }
    SyncItem.SCORE_GAMES -> {
        val (kept, unknown) = filterKnownScoreGames(decodeStringArray(raw), knownGames)
        if (kept.isEmpty()) HupuPrefs.clearScoreGames() else HupuPrefs.saveScoreGames(kept)
        val extra = if (unknown.isNotEmpty()) " · 跳过 ${unknown.size} 个本版本不支持的频道" else ""
        val head = if (kept.isEmpty()) "评分频道已恢复全量默认" else "评分频道覆盖为 ${kept.size} 个"
        SyncApplyOutcome(item, head + extra, unknown.size)
    }
    SyncItem.HOME_TOPICS -> {
        val list = decodeHomeTopicsJson(raw)
        if (list.isEmpty()) HupuPrefs.clearHomeTopics() else HupuPrefs.saveHomeTopics(list)
        val head = if (list.isEmpty()) "首页频道已恢复官方默认" else "首页频道覆盖为 ${list.size} 个"
        SyncApplyOutcome(item, head)
    }
    SyncItem.START_TAB -> {
        val idx = raw.trim().toIntOrNull()?.coerceIn(0, 3) ?: 0
        HupuPrefs.saveStartTab(idx)
        SyncApplyOutcome(item, "默认启动页设为「${SYNC_START_TAB_NAMES.getOrElse(idx) { "首页" }}」")
    }
    SyncItem.TOPIC_SORT -> {
        val title = decodeStringArray(raw).firstOrNull().orEmpty()
        HupuPrefs.saveDefaultTopicSortTitle(title)
        val head = if (title.isEmpty()) "专区默认排序已恢复服务器默认" else "专区默认排序设为「$title」"
        SyncApplyOutcome(item, head)
    }
    SyncItem.REPLY_SORT -> {
        val m = raw.trim().toIntOrNull()?.coerceIn(0, 2) ?: 0
        HupuPrefs.saveDefaultReplySort(m)
        val label = REPLY_SORT_OPTIONS.firstOrNull { it.first == m }?.second ?: "默认"
        SyncApplyOutcome(item, "帖子回复默认排序设为「$label」")
    }
    SyncItem.SCORE_SORT -> {
        val k = decodeStringArray(raw).firstOrNull().orEmpty().ifEmpty { "brightest" }
        HupuPrefs.saveDefaultScoreSort(k)
        val label = SCORE_SORT_OPTIONS.firstOrNull { it.first == k }?.second ?: "最亮"
        SyncApplyOutcome(item, "评分评论默认排序设为「$label」")
    }
}
