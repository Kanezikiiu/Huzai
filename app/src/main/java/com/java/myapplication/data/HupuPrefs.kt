package com.java.myapplication.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/**
 * 轻量用户偏好（SharedPreferences）。
 * 当前：主页频道自定义（「我的」页编辑，首页横滑条消费）。
 */
object HupuPrefs {

    private const val KEY_HOME_TOPICS = "home_topics_v1"
    private const val KEY_HOME_HOT_HIDDEN = "home_hot_hidden_v1"
    private const val KEY_SEARCH_HISTORY = "search_history_v1"
    private const val KEY_HISTORY = "browsing_history_v1"
    private const val KEY_SCORE_GAMES = "score_games_v1"
    private const val KEY_SCORE_COMMON_HIDDEN = "score_common_hidden_v1"
    private const val KEY_FILTER_KEYWORDS = "filter_keywords_v1"
    private const val KEY_FAVORITE_TOPICS = "favorite_topics_v1"
    private const val KEY_REFRESH_MODE = "refresh_mode_v1"

    /** 主页频道数量上限：横滑条是高频快捷入口，超过会滑不过来 */
    const val MAX_HOME_TOPICS = 20

    /** 搜索历史上限 */
    const val MAX_SEARCH_HISTORY = 12

    /** 浏览记录上限：300 条（每条约 200B JSON，总上限约 60KB，SharedPreferences 可承受） */
    const val MAX_HISTORY = 300
    /** 评分页频道（项目）上限：与主页横滑条一致 */
    const val MAX_SCORE_GAMES = 20
    /** 过滤关键词每组上限：足够重度用户沉淀屏蔽词表 */
    const val MAX_FILTER_KEYWORDS = 100
    /** 1.191: 收藏专区上限——横滑条首位，过多会滑不过来 */
    const val MAX_FAVORITE_TOPICS = 50

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences("hupu_prefs", Context.MODE_PRIVATE)
        disclaimerAccepted = prefs.getBoolean(KEY_DISCLAIMER_ACCEPTED, false)
    }

    /**
     * 1.154 免责声明同意状态。false = 必须在「免责声明」门禁页同意后才能使用本应用；
     * 同意后持久化，卸载或清除数据才会重置。UI 读它决定显示门禁还是主界面。
     */
    var disclaimerAccepted by mutableStateOf(false)
        private set

    /** 用户点击「同意并继续」——持久化并放行主界面 */
    fun acceptDisclaimer() {
        prefs.edit().putBoolean(KEY_DISCLAIMER_ACCEPTED, true).apply()
        disclaimerAccepted = true
    }

    /** 版本号：设置页保存后 +1，首页观察到变化即刷新横滑条 */
    var homeTopicsVersion by mutableIntStateOf(0)
        private set

    /** 是否自定义过（区分「没配过=用官方热门」与「配成空=只留热帖」） */
    fun hasCustomHomeTopics(): Boolean = prefs.contains(KEY_HOME_TOPICS)

    fun loadHomeTopics(): List<HupuTopicInfo> {
        val json = prefs.getString(KEY_HOME_TOPICS, null) ?: return emptyList()
        return decodeHomeTopicsJson(json)
    }

    fun saveHomeTopics(list: List<HupuTopicInfo>) {
        prefs.edit().putString(KEY_HOME_TOPICS, encodeHomeTopicsJson(list)).apply()
        homeTopicsVersion++
    }

    /** 恢复默认（清除自定义，首页回到官方热门话题） */
    /** 1.192: 首页「热帖」固定频道是否隐藏（恒保证「热帖 + 已选话题」至少一个可见） */
    fun isHomeHotHidden(): Boolean = prefs.getBoolean(KEY_HOME_HOT_HIDDEN, false)

    fun setHomeHotHidden(hidden: Boolean) {
        prefs.edit().putBoolean(KEY_HOME_HOT_HIDDEN, hidden).apply()
        homeTopicsVersion++
    }

    fun clearHomeTopics() {
        prefs.edit().remove(KEY_HOME_TOPICS).apply()
        homeTopicsVersion++
    }

    /** 1.191: 收藏专区版本号——专区页观察它重算「收藏专区」tab 与网格 */
    var favoriteTopicsVersion by mutableIntStateOf(0)
        private set

    fun loadFavoriteTopics(): List<HupuTopicInfo> {
        val json = prefs.getString(KEY_FAVORITE_TOPICS, null) ?: return emptyList()
        return decodeFavoriteTopicsJson(json)
    }

    fun saveFavoriteTopics(list: List<HupuTopicInfo>) {
        prefs.edit().putString(KEY_FAVORITE_TOPICS, encodeFavoriteTopicsJson(list)).apply()
        favoriteTopicsVersion++
    }

    /** 切换某个专区的收藏状态；返回「切换后是否已收藏」（供提示与图标状态使用） */
    fun toggleFavoriteTopic(t: HupuTopicInfo): Boolean {
        val cur = loadFavoriteTopics()
        val exists = cur.any { it.url == t.url }
        saveFavoriteTopics(toggleFavoriteList(cur, t, MAX_FAVORITE_TOPICS))
        return !exists
    }

    /** 搜索历史：最多 12 条，新搜索置顶去重 */
    fun addSearchHistory(query: String) {
        if (query.isBlank()) return
        val cur = loadSearchHistory().toMutableList()
        cur.removeAll { it == query }
        cur.add(0, query)
        while (cur.size > MAX_SEARCH_HISTORY) cur.removeAt(cur.size - 1)
        prefs.edit().putString(KEY_SEARCH_HISTORY, JSONArray(cur).toString()).apply()
        searchHistoryVersion++
    }

    fun removeSearchHistory(query: String) {
        val cur = loadSearchHistory().toMutableList()
        if (cur.removeAll { it == query }) {
            prefs.edit().putString(KEY_SEARCH_HISTORY, JSONArray(cur).toString()).apply()
            searchHistoryVersion++
        }
    }

    fun loadSearchHistory(): List<String> {
        val json = prefs.getString(KEY_SEARCH_HISTORY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i -> arr.optString(i).takeIf { s -> s.isNotEmpty() } }
        }.getOrDefault(emptyList())
    }

    fun clearSearchHistory() {
        prefs.edit().remove(KEY_SEARCH_HISTORY).apply()
        searchHistoryVersion++
    }
    /** 搜索历史版本号：变化时搜索页历史条刷新 */
    var searchHistoryVersion by mutableIntStateOf(0)
        private set

    // ---------- 浏览记录（帖子详情打开时写入；「我的」页可查看/查找/清空） ----------

    /** 浏览记录版本号：变化时历史页刷新 */
    var historyVersion by mutableIntStateOf(0)
        private set

    /** 记录一次浏览：同 tid 去重置顶，超限尾部裁剪 */
    fun addHistory(e: HupuHistoryEntry) {
        if (e.tid.isBlank()) return
        val cur = loadHistory().toMutableList()
        cur.removeAll { it.tid == e.tid }
        cur.add(0, e)
        while (cur.size > MAX_HISTORY) cur.removeAt(cur.size - 1)
        prefs.edit().putString(KEY_HISTORY, encodeHistoryJson(cur)).apply()
        historyVersion++
    }

    fun loadHistory(): List<HupuHistoryEntry> {
        val json = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return decodeHistoryJson(json)
    }

    fun removeHistory(tid: String) {
        val cur = loadHistory().toMutableList()
        if (cur.removeAll { it.tid == tid }) {
            prefs.edit().putString(KEY_HISTORY, encodeHistoryJson(cur)).apply()
            historyVersion++
        }
    }

    fun clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply()
        historyVersion++
    }

    /** 首页实际使用的频道列表：有自定义用自定义，否则回退官方热门 */
    fun effectiveHomeTopics(fallback: List<HupuTopicInfo>): List<HupuTopicInfo> =
        if (hasCustomHomeTopics()) loadHomeTopics() else fallback

    // ---------- 评分频道自定义（镜像主页频道自定义模式） ----------
    /** 版本号：评分频道保存后 +1，评分页观察到变化即刷新横滑条 */
    var scoreGamesVersion by mutableIntStateOf(0)
        private set

    /** 是否自定义过评分频道（区分「未配置=全量」与「配置成空」） */
    fun hasCustomScoreGames(): Boolean = prefs.contains(KEY_SCORE_GAMES)

    fun loadScoreGames(): List<String> {
        val json = prefs.getString(KEY_SCORE_GAMES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i -> arr.optString(i).takeIf { it.isNotEmpty() } }
        } catch (e: Exception) { emptyList() }
    }

    /** 保存评分频道 id 列表（保存合法 id；不合法 id 丢弃） */
    fun saveScoreGames(list: List<String>) {
        val arr = JSONArray()
        list.filter { it.isNotBlank() }.forEach { arr.put(it) }
        prefs.edit().putString(KEY_SCORE_GAMES, arr.toString()).apply()
        scoreGamesVersion++
    }

    /** 1.192: 评分页「虎扑评分」固定频道是否隐藏（恒保证「虎扑评分 + 已选赛事」至少一个可见） */
    fun isScoreCommonHidden(): Boolean = prefs.getBoolean(KEY_SCORE_COMMON_HIDDEN, false)

    fun setScoreCommonHidden(hidden: Boolean) {
        prefs.edit().putBoolean(KEY_SCORE_COMMON_HIDDEN, hidden).apply()
        scoreGamesVersion++
    }

    fun clearScoreGames() {
        prefs.edit().remove(KEY_SCORE_GAMES).apply()
        scoreGamesVersion++
    }

    /** 评分页实际频道：自定义保留 GAMES 顺序（id 有效性防护），未配置=全量 */
    fun effectiveScoreGames(all: List<Pair<String, String>>): List<Pair<String, String>> {
        if (!hasCustomScoreGames()) return all
        val byId = all.associate { it.first to it }
        return loadScoreGames().mapNotNull { byId[it] }
    }

    // ---------- 浏览流关键词过滤（我的→浏览流设置） ----------
    /** 版本号：关键词保存后 +1，各列表订阅刷新 */
    var filterVersion by mutableIntStateOf(0)
        private set

    private var cachedKeywords: HupuFilter.Keywords? = null

    fun loadFilterKeywords(): HupuFilter.Keywords {
        cachedKeywords?.let { return it }
        val json = prefs.getString(KEY_FILTER_KEYWORDS, null) ?: return HupuFilter.Keywords()
        val kw = decodeFilterKeywordsJson(json)
        cachedKeywords = kw
        return kw
    }

    fun saveFilterKeywords(kw: HupuFilter.Keywords) {
        prefs.edit().putString(KEY_FILTER_KEYWORDS, encodeFilterKeywordsJson(kw)).apply()
        cachedKeywords = kw
        filterVersion++
    }

    fun clearFilterKeywords() {
        prefs.edit().remove(KEY_FILTER_KEYWORDS).apply()
        cachedKeywords = null
        filterVersion++
    }
    // ---------- Reading font size (Profile -> TextSize; thread detail body only) ----------
    /** Scale range of the reading font size */
    const val FONT_SCALE_MIN = 0.8f
    const val FONT_SCALE_MAX = 1.6f
    private const val KEY_FONT_SCALE = "font_scale_v1"
    /** Version: saved font scale bumps this, detail page recomposes live */
    var fontScaleVersion by mutableIntStateOf(0)
    fun loadFontScale(): Float = prefs.getFloat(KEY_FONT_SCALE, 1.0f)
    fun saveFontScale(v: Float) {
        prefs.edit().putFloat(KEY_FONT_SCALE, v.coerceIn(FONT_SCALE_MIN, FONT_SCALE_MAX)).apply()
        fontScaleVersion++
    }

    /** 屏幕刷新率档位：-1=自动（系统默认），>0 为 preferredDisplayModeId */
    fun loadRefreshMode(): Int = prefs.getInt(KEY_REFRESH_MODE, -1)
    fun saveRefreshMode(v: Int) {
        prefs.edit().putInt(KEY_REFRESH_MODE, v).apply()
    }
    // ---------- 1.190 「滚动时自动隐藏底栏」 ----------
    private const val KEY_AUTO_HIDE_BAR = "auto_hide_bar_v1"
    /** 开关变更版本：MainActivity / 帖子详情页观察到即重新生效（无需重启） */
    var autoHideBarVersion by mutableIntStateOf(0)
        private set
    /**
     * 滚动时自动隐藏底栏（**默认开**）。
     * 开启后：手指上滑（内容往下看）→ 底栏收缩隐藏；手指下滑 → 底栏弹出。
     * 作用对象按当前页面而定——4 大主页收起悬浮 Tab 栏，帖子详情页收起常驻操作条。
     */
    fun loadAutoHideBar(): Boolean = prefs.getBoolean(KEY_AUTO_HIDE_BAR, true)
    fun saveAutoHideBar(v: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_HIDE_BAR, v).apply()
        autoHideBarVersion++
    }

    // ---------- 1.130 主题模式（跟随系统 / 浅色 / 深色） ----------
    const val THEME_SYSTEM = "system"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"
    // ---------- 1.148 games 写端点版本号自愈 ----------

    private const val KEY_GAMES_VER = "games_ver_"

    /** games 写端点当前生效的版本段（按端点族分别持久化，空串=未自愈过，用代码内置基线）。
     *  背景：服务端会随时间抬高最低版本号（`应用版本过旧，请升级到最新版本`）；
     *  命中后由 HupuAccount 自愈阶梯试更高版本，成功的值写回这里，后续请求直接使用。
     */
    fun gamesVersionOf(family: String): String =
        prefs.getString(KEY_GAMES_VER + family, "") ?: ""

    fun saveGamesVersion(family: String, version: String) {
        if (version.isBlank()) return
        prefs.edit().putString(KEY_GAMES_VER + family, version).apply()
    }

    fun clearGamesVersion() {
        val ed = prefs.edit()
        for (f in listOf("score", "publish", "light")) ed.remove(KEY_GAMES_VER + f)
        ed.apply()
    }

    private const val KEY_THEME_MODE = "theme_mode_v1"
    /** 1.154：免责声明是否已同意（false = 首次启动，必须同意才能使用） */
    private const val KEY_DISCLAIMER_ACCEPTED = "disclaimer_accepted_v1"

    /** 主题模式变更版本：MainActivity / 主题计算观察到即重组（切换即时生效，无需重启） */
    var themeModeVersion by mutableIntStateOf(0)
        private set

    fun loadThemeMode(): String =
        prefs.getString(KEY_THEME_MODE, THEME_SYSTEM) ?: THEME_SYSTEM

    fun saveThemeMode(mode: String) {
        val m = when (mode) {
            THEME_LIGHT, THEME_DARK -> mode
            else -> THEME_SYSTEM
        }
        prefs.edit().putString(KEY_THEME_MODE, m).apply()
        themeModeVersion++
    }

    // ---------- 1.179 色彩主题（强调色） ----------
    private const val KEY_COLOR_THEME = "color_theme_v1"
    /** 默认色彩主题 id（与 AccentPalettes 第一套一致） */
    const val DEFAULT_COLOR_THEME = "blue"

    // ---------- 1.181 打开软件自动检查更新（每次冷启动检查，10min 去重；有新版才弹窗） ----------
    private const val KEY_LAST_UPDATE_CHECK = "last_update_check_v1"
    /** 自动检查更新的短去重窗口：10 分钟（防异常重启 / 连环重开刷请求） */
    const val AUTO_UPDATE_INTERVAL_MS = 10L * 60 * 1000

    /** 上次自动检查更新的时间戳（0 = 从未检查过） */
    fun lastUpdateCheckAt(): Long = prefs.getLong(KEY_LAST_UPDATE_CHECK, 0L)

    /** 记录一次自动检查（无论成功失败都记，避免网络失败时反复重试） */
    fun markUpdateChecked(at: Long) {
        prefs.edit().putLong(KEY_LAST_UPDATE_CHECK, at).apply()
    }

    /** 是否已过去重窗口（true = 本次冷启动应做一次静默检查） */
    fun shouldAutoCheckUpdate(now: Long): Boolean =
        now - lastUpdateCheckAt() >= AUTO_UPDATE_INTERVAL_MS

    // 1.181: 记住被「忽略此版本」的 versionCode（自动弹窗不再打扰该版本）
    private const val KEY_UPDATE_IGNORED_VERSION = "update_ignored_version_v1"

    /** 被忽略的版本号（0 = 未忽略过任何版本） */
    fun ignoredUpdateVersion(): Int = prefs.getInt(KEY_UPDATE_IGNORED_VERSION, 0)

    /** 忽略某个版本（点「忽略此版本」时调用） */
    fun ignoreUpdateVersion(versionCode: Int) {
        prefs.edit().putInt(KEY_UPDATE_IGNORED_VERSION, versionCode).apply()
    }

    // ---------- 1.182 默认启动页（底部 Tab；下次冷启动生效） ----------
    private const val KEY_START_TAB = "start_tab_v1"

    /** 默认启动的底部 Tab 下标（0 首页 / 1 专区 / 2 评分 / 3 我的）；越界回落首页 */
    fun loadStartTab(): Int = prefs.getInt(KEY_START_TAB, 0).coerceIn(0, 3)

    fun saveStartTab(index: Int) {
        prefs.edit().putInt(KEY_START_TAB, index.coerceIn(0, 3)).apply()
    }

    /** 色彩主题变更版本：设置页点选后整个应用即时重组（无需重启） */
    var colorThemeVersion by mutableIntStateOf(0)
        private set

    /** 当前色彩主题 id；也可能是 ACCENT_DYNAMIC（动态取色）。 */
    fun loadColorTheme(): String =
        prefs.getString(KEY_COLOR_THEME, DEFAULT_COLOR_THEME) ?: DEFAULT_COLOR_THEME

    fun saveColorTheme(id: String) {
        prefs.edit().putString(KEY_COLOR_THEME, id).apply()
        colorThemeVersion++
    }

    // ---------- 1.94 收藏表情包 ----------
    private const val KEY_STICKERS = "stickers_v1"
    /** 收藏上限 */
    const val MAX_STICKERS = 60
    /** 表情包集合变更版本：表情面板观察到即刷新 */
    var stickersVersion by mutableIntStateOf(0)
        private set

    /**
     * 1.170: 表情集合版本自增统一切回主线程。
     * addSticker/removeSticker/importLocalSticker 会在 IO 线程被调用，
     * 直写 Compose 快照虽可用但存在时序不确定性，这里保证在主线程变更。
     */
    private fun bumpStickers() {
        if (Looper.myLooper() == Looper.getMainLooper()) stickersVersion++
        else Handler(Looper.getMainLooper()).post { stickersVersion++ }
    }
    /** 收藏/删除的一次性反馈文案（页面消费后置空） */
    var stickerToast by mutableStateOf<String?>(null)
    /** 1.96: 长按图片的「收藏」气泡——url + 图片在根坐标中的位置（null = 不显示） */
    var stickerBubbleUrl by mutableStateOf<String?>(null)
    var stickerBubbleRect by mutableStateOf(Rect.Zero)

    fun loadStickers(): List<HupuSticker> =
        prefs.getString(KEY_STICKERS, null)?.let { decodeStickersJson(it) } ?: emptyList()

    fun isStickerSaved(url: String): Boolean = loadStickers().any { it.url == url }

    /** 收藏（新的排最前）：已存在返回 false，写入成功返回 true */
    fun addSticker(url: String): Boolean {
        if (url.isEmpty()) return false
        val cur = loadStickers()
        if (cur.any { it.url == url }) return false
        val next = (listOf(HupuSticker(url)) + cur).take(MAX_STICKERS)
        prefs.edit().putString(KEY_STICKERS, encodeStickersJson(next)).apply()
        bumpStickers()
        return true
    }

    fun removeSticker(url: String) {
        // 1.165: 本地导入的表情连同落盘文件一并删除（远程收藏只删记录）
        localStickerFile(url)?.let { runCatching { it.delete() } }
        val next = loadStickers().filterNot { it.url == url }
        prefs.edit().putString(KEY_STICKERS, encodeStickersJson(next)).apply()
        bumpStickers()
    }

    /**
     * 1.168: 从相册导入一张图片为「我的表情」。
     * 文件名 = 内容哈希（+ 魔数嗅探出的扩展名），因此**同一张图必然落到同一个 URL**，
     * 天然复用 `addSticker` 的「URL 已存在则拒绝」检测机制 → 不会重复添加
     * （与收藏表情时的去重口径一致）。
     */
    fun importLocalSticker(context: Context, uri: android.net.Uri): LocalStickerImport {
        // 1.170: 带上限读取（>32MB 或读取失败 → null），避免超大图整段入内存
        val bytes = HupuImage.readCapped(context.contentResolver, uri)
        if (bytes == null || bytes.isEmpty()) return LocalStickerImport.FAILED
        val ext = HupuImage.sniffExtension(bytes) ?: "jpg"
        val dir = java.io.File(context.filesDir, "stickers_local").apply { mkdirs() }
        val f = java.io.File(dir, "sticker_" + stickerContentKey(bytes) + "." + ext)
        return try {
            if (!f.exists()) f.writeBytes(bytes)
            val url = "file://" + f.absolutePath
            if (addSticker(url)) LocalStickerImport.ADDED else LocalStickerImport.DUPLICATE
        } catch (e: Exception) {
            LocalStickerImport.FAILED
        }
    }

    // ---------- 1.191 表情搜索：最近使用 / 最近在搜 ----------
    private const val KEY_STICKER_RECENT = "sticker_recent_v1"
    private const val KEY_STICKER_SEARCH_LOG = "sticker_search_log_v1"

    /** 「最近使用」上限（微信式：一行内容量，多余的滚出） */
    const val MAX_STICKER_RECENT = 16

    /** 「最近在搜」上限 */
    const val MAX_STICKER_SEARCH_LOG = 10

    /** 最近使用 / 最近在搜变更版本：搜索面板观察到即刷新 */
    var stickerRecentVersion by mutableIntStateOf(0)
        private set

    private fun bumpStickerRecent() {
        if (Looper.myLooper() == Looper.getMainLooper()) stickerRecentVersion++
        else Handler(Looper.getMainLooper()).post { stickerRecentVersion++ }
    }

    /** 最近使用过的搜索表情（新的排最前），空表示还没用过 */
    fun loadStickerRecent(): List<HupuSticker> =
        prefs.getString(KEY_STICKER_RECENT, null)?.let { decodeStickersJson(it) } ?: emptyList()

    /** 记一次「用过的表情」：去重后置顶（与收藏的排重口径一致，均以 URL 为准） */
    fun pushStickerRecent(url: String) {
        if (url.isEmpty()) return
        val next = (listOf(HupuSticker(url)) + loadStickerRecent().filterNot { it.url == url })
            .take(MAX_STICKER_RECENT)
        prefs.edit().putString(KEY_STICKER_RECENT, encodeStickersJson(next)).apply()
        bumpStickerRecent()
    }

    fun clearStickerRecent() {
        prefs.edit().remove(KEY_STICKER_RECENT).apply()
        bumpStickerRecent()
    }

    /** 表情搜索历史关键词（新的排最前） */
    fun loadStickerSearchLog(): List<String> =
        prefs.getString(KEY_STICKER_SEARCH_LOG, null)?.let { decodeKeywordsJson(it) } ?: emptyList()

    /** 记一次搜索关键词：去重后置顶 */
    fun pushStickerSearchLog(keyword: String) {
        val k = keyword.trim()
        if (k.isEmpty()) return
        val next = (listOf(k) + loadStickerSearchLog().filterNot { it == k })
            .take(MAX_STICKER_SEARCH_LOG)
        prefs.edit().putString(KEY_STICKER_SEARCH_LOG, encodeKeywordsJson(next)).apply()
        bumpStickerRecent()
    }

    fun clearStickerSearchLog() {
        prefs.edit().remove(KEY_STICKER_SEARCH_LOG).apply()
        bumpStickerRecent()
    }
}

/** 1.168: 本地表情导入结果（用于向上层如实反馈，区分「重复」与「失败」） */
enum class LocalStickerImport { ADDED, DUPLICATE, FAILED }

/**
 * 1.168: 图片内容指纹（SHA-256 前 16 字节的十六进制）——纯函数，可单测。
 * 相同字节 → 相同指纹；用于本地表情的「内容级去重」文件名。
 */
internal fun stickerContentKey(bytes: ByteArray): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
    return digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) }.take(32)
}

/** 浏览记录纯 JSON 编码（便于单测，不依赖 Android） */
internal fun encodeHistoryJson(list: List<HupuHistoryEntry>): String {
    val arr = JSONArray()
    list.forEach { e ->
        val o = JSONObject()
        o.put("tid", e.tid)
        o.put("title", e.title)
        o.put("topicName", e.topicName)
        o.put("lights", e.lights)
        o.put("replies", e.replies)
        o.put("read", e.read)
        o.put("visitedAt", e.visitedAt)
        arr.put(o)
    }
    return arr.toString()
}

/** 浏览记录纯 JSON 解码（便于单测）：坏数据返回空列表 */
internal fun decodeHistoryJson(json: String): List<HupuHistoryEntry> {
    return runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            HupuHistoryEntry(
                tid = o.optString("tid"),
                title = o.optString("title"),
                topicName = o.optString("topicName"),
                lights = o.optInt("lights", 0),
                replies = o.optInt("replies", 0),
                read = o.optInt("read", 0),
                visitedAt = o.optLong("visitedAt", 0L),
            )
        }
    }.getOrDefault(emptyList())
}

/** 纯 JSON 编码（便于单测，不依赖 Android）：[{"topicId","name","url","logo"?}] */
internal fun encodeHomeTopicsJson(list: List<HupuTopicInfo>): String {
    val arr = JSONArray()
    list.forEach { t ->
        val o = JSONObject()
        o.put("topicId", t.topicId)
        o.put("name", t.name)
        o.put("url", t.url)
        t.logo?.let { o.put("logo", it) }
        arr.put(o)
    }
    return arr.toString()
}

/** 纯 JSON 解码（便于单测）：坏数据/空串返回空列表 */
/** 过滤关键词纯 JSON 编解码（便于单测，不依赖 Android） */
internal fun encodeFilterKeywordsJson(kw: HupuFilter.Keywords): String {
    val o = JSONObject()
    o.put("title", JSONArray(kw.title))
    o.put("zone", JSONArray(kw.zone))
    o.put("comment", JSONArray(kw.comment))
    return o.toString()
}

internal fun decodeFilterKeywordsJson(json: String): HupuFilter.Keywords {
    return try {
        val o = JSONObject(json)
        fun arr(k: String): List<String> {
            val a = o.optJSONArray(k) ?: return emptyList()
            return (0 until a.length()).mapNotNull { i -> a.optString(i).takeIf { it.isNotEmpty() } }
        }
        HupuFilter.Keywords(title = arr("title"), zone = arr("zone"), comment = arr("comment"))
    } catch (e: Exception) { HupuFilter.Keywords() }
}

/** 收藏表情包条目：只存图片 URL（虎扑没有专门的表情包通道，发出的表情包就是图片） */
data class HupuSticker(val url: String)

/** 1.165: 本地导入的表情以 file:// 前缀标识（文件存于 filesDir/stickers_local）；纯函数，可单测 */
internal fun isLocalStickerUrl(url: String): Boolean = url.startsWith("file://")

/** 1.165: file:// URL → 本地文件（非本地返回 null）；纯函数，可单测 */
internal fun localStickerFile(url: String): java.io.File? =
    if (url.startsWith("file://")) java.io.File(url.removePrefix("file://")) else null

/** 表情包纯 JSON 编解码（便于单测，不依赖 Android） */
internal fun encodeStickersJson(list: List<HupuSticker>): String {
    val arr = JSONArray()
    list.forEach { s -> arr.put(s.url) }
    return arr.toString()
}

internal fun decodeStickersJson(json: String): List<HupuSticker> {
    return runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            // 兼容 1.94 的 {"url":..,"token":..} 对象形式
            val v = arr.opt(i)
            val u = if (v is JSONObject) v.optString("url") else v?.toString() ?: ""
            if (u.isEmpty()) null else HupuSticker(u)
        }
    }.getOrDefault(emptyList())
}

/** 1.191: 表情搜索历史关键词 ⇄ JSON（纯函数，可单测） */
internal fun encodeKeywordsJson(list: List<String>): String {
    val arr = JSONArray()
    list.forEach { arr.put(it) }
    return arr.toString()
}

internal fun decodeKeywordsJson(json: String): List<String> =
    runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            arr.optString(i).takeIf { it.isNotEmpty() }
        }
    }.getOrDefault(emptyList())

internal fun decodeHomeTopicsJson(json: String): List<HupuTopicInfo> {
    return runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            HupuTopicInfo(
                topicId = o.optString("topicId"),
                name = o.optString("name"),
                url = o.optString("url"),
                logo = o.optString("logo").ifEmpty { null },
            )
        }
    }.getOrDefault(emptyList())
}

/**
 * 1.191: 收藏专区纯 JSON 编解码（便于单测，不依赖 Android）。
 * 比主页频道多存一个 hotText——收藏网格与专区格子同形（logo + 名称 + 热度）。
 */
internal fun encodeFavoriteTopicsJson(list: List<HupuTopicInfo>): String {
    val arr = JSONArray()
    list.forEach { t ->
        val o = JSONObject()
        o.put("topicId", t.topicId)
        o.put("name", t.name)
        o.put("url", t.url)
        if (t.hotText.isNotEmpty()) o.put("hotText", t.hotText)
        t.logo?.let { o.put("logo", it) }
        arr.put(o)
    }
    return arr.toString()
}

/** 纯 JSON 解码：坏数据/空串返回空列表；url 为空的条目丢弃 */
internal fun decodeFavoriteTopicsJson(json: String): List<HupuTopicInfo> {
    return runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val url = o.optString("url")
            if (url.isEmpty()) return@mapNotNull null
            HupuTopicInfo(
                topicId = o.optString("topicId"),
                name = o.optString("name"),
                url = url,
                hotText = o.optString("hotText"),
                logo = o.optString("logo").ifEmpty { null },
            )
        }
    }.getOrDefault(emptyList())
}

/** 1.191: 收藏切换的纯函数（便于单测）：已存在则移除，否则置顶插入并按上限截断 */
internal fun toggleFavoriteList(
    cur: List<HupuTopicInfo>,
    t: HupuTopicInfo,
    max: Int,
): List<HupuTopicInfo> =
    if (cur.any { it.url == t.url }) cur.filterNot { it.url == t.url }
    else (listOf(t) + cur).take(max)
