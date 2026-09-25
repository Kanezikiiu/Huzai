#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""1.191 第 3 批：
A. 弹窗「取消」按钮底色太浅（白 20% 叠在浅色毛玻璃上几乎不可见）→ onSurface 12%/16%
B. 专区页新增「收藏专区」tab（置顶 + 默认选中 + 有收藏才出现）
C. 具体专区页右上角「刷新」按钮 → 「收藏」按钮
"""
import io
import os

ROOT = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "..", "app", "src", "main", "java", "com", "java", "myapplication",
)
PAGES = os.path.join(ROOT, "ui", "pages")
COMPONENTS = os.path.join(ROOT, "ui", "components")
DATA = os.path.join(ROOT, "data")
TESTDIR = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "..", "app", "src", "test", "java", "com", "java", "myapplication",
)


def load(p):
    with io.open(p, encoding="utf-8") as f:
        return f.read()


def save(p, s):
    with io.open(p, "w", encoding="utf-8") as f:
        f.write(s)


def sub(s, old, new, where, n_expect=1):
    n = s.count(old)
    assert n == n_expect, "命中 %d 次（期望 %d）: %s :: %r" % (n, n_expect, where, old[:90])
    return s.replace(old, new)


# ==========================================================================
# A) GlassDialog.kt —— 取消按钮底色
# ==========================================================================
p = os.path.join(ROOT, "ui", "glass", "GlassDialog.kt")
s = load(p)
s = sub(
    s,
    "    val isLight = !isAppDarkTheme()\n"
    "    val containerColor =\n"
    "        if (isLight) Color(0xFFFAFAFA).copy(0.6f)\n"
    "        else Color(0xFF121212).copy(0.4f)\n"
    "    Row(\n"
    "        modifier\n"
    "            .clip(Capsule())\n"
    "            .background(if (accent) MaterialTheme.colorScheme.primary else containerColor.copy(0.2f))\n",
    "    val isLight = !isAppDarkTheme()\n"
    "    // 1.191: 非 accent（取消）按钮原为「白 20%」——叠在浅色毛玻璃卡上几乎看不见。\n"
    "    // 改成 onSurface 的 12%（深色档 16%）：既是「灰按钮」的语义，胶囊形状也清晰可辨。\n"
    "    val idleColor = MaterialTheme.colorScheme.onSurface\n"
    "        .copy(alpha = if (isLight) 0.12f else 0.16f)\n"
    "    Row(\n"
    "        modifier\n"
    "            .clip(Capsule())\n"
    "            .background(if (accent) MaterialTheme.colorScheme.primary else idleColor)\n",
    "GlassDialog.idleButton",
)
save(p, s)

# ==========================================================================
# B1) HupuPrefs.kt —— 收藏专区存储 + 纯函数
# ==========================================================================
p = os.path.join(DATA, "HupuPrefs.kt")
s = load(p)
s = sub(
    s,
    '    private const val KEY_FILTER_KEYWORDS = "filter_keywords_v1"\n',
    '    private const val KEY_FILTER_KEYWORDS = "filter_keywords_v1"\n'
    '    private const val KEY_FAVORITE_TOPICS = "favorite_topics_v1"\n',
    "Prefs.key",
)
s = sub(
    s,
    "    /** 过滤关键词每组上限：足够重度用户沉淀屏蔽词表 */\n"
    "    const val MAX_FILTER_KEYWORDS = 100\n",
    "    /** 过滤关键词每组上限：足够重度用户沉淀屏蔽词表 */\n"
    "    const val MAX_FILTER_KEYWORDS = 100\n"
    "    /** 1.191: 收藏专区上限——横滑条首位，过多会滑不过来 */\n"
    "    const val MAX_FAVORITE_TOPICS = 50\n",
    "Prefs.max",
)
s = sub(
    s,
    "    /** 恢复默认（清除自定义，首页回到官方热门话题） */\n"
    "    fun clearHomeTopics() {\n"
    "        prefs.edit().remove(KEY_HOME_TOPICS).apply()\n"
    "        homeTopicsVersion++\n"
    "    }\n",
    "    /** 恢复默认（清除自定义，首页回到官方热门话题） */\n"
    "    fun clearHomeTopics() {\n"
    "        prefs.edit().remove(KEY_HOME_TOPICS).apply()\n"
    "        homeTopicsVersion++\n"
    "    }\n"
    "\n"
    "    /** 1.191: 收藏专区版本号——专区页观察它重算「收藏专区」tab 与网格 */\n"
    "    var favoriteTopicsVersion by mutableIntStateOf(0)\n"
    "        private set\n"
    "\n"
    "    fun loadFavoriteTopics(): List<HupuTopicInfo> {\n"
    "        val json = prefs.getString(KEY_FAVORITE_TOPICS, null) ?: return emptyList()\n"
    "        return decodeFavoriteTopicsJson(json)\n"
    "    }\n"
    "\n"
    "    fun saveFavoriteTopics(list: List<HupuTopicInfo>) {\n"
    "        prefs.edit().putString(KEY_FAVORITE_TOPICS, encodeFavoriteTopicsJson(list)).apply()\n"
    "        favoriteTopicsVersion++\n"
    "    }\n"
    "\n"
    "    /** 切换某个专区的收藏状态；返回「切换后是否已收藏」（供提示与图标状态使用） */\n"
    "    fun toggleFavoriteTopic(t: HupuTopicInfo): Boolean {\n"
    "        val cur = loadFavoriteTopics()\n"
    "        val exists = cur.any { it.url == t.url }\n"
    "        saveFavoriteTopics(toggleFavoriteList(cur, t, MAX_FAVORITE_TOPICS))\n"
    "        return !exists\n"
    "    }\n",
    "Prefs.favApi",
)

# 纯编解码 + 纯切换，挂在 decodeHomeTopicsJson 之后
old = (
    "internal fun decodeHomeTopicsJson(json: String): List<HupuTopicInfo> {\n"
    "    return runCatching {\n"
    "        val arr = JSONArray(json)\n"
    "        (0 until arr.length()).mapNotNull { i ->\n"
    "            val o = arr.optJSONObject(i) ?: return@mapNotNull null\n"
    "            HupuTopicInfo(\n"
    "                topicId = o.optString(\"topicId\"),\n"
    "                name = o.optString(\"name\"),\n"
    "                url = o.optString(\"url\"),\n"
    "                logo = o.optString(\"logo\").ifEmpty { null },\n"
    "            )\n"
    "        }\n"
    "    }.getOrDefault(emptyList())\n"
    "}\n"
)
new = old + (
    "\n"
    "/**\n"
    " * 1.191: 收藏专区纯 JSON 编解码（便于单测，不依赖 Android）。\n"
    " * 比主页频道多存一个 hotText——收藏网格与专区格子同形（logo + 名称 + 热度）。\n"
    " */\n"
    "internal fun encodeFavoriteTopicsJson(list: List<HupuTopicInfo>): String {\n"
    "    val arr = JSONArray()\n"
    "    list.forEach { t ->\n"
    "        val o = JSONObject()\n"
    "        o.put(\"topicId\", t.topicId)\n"
    "        o.put(\"name\", t.name)\n"
    "        o.put(\"url\", t.url)\n"
    "        if (t.hotText.isNotEmpty()) o.put(\"hotText\", t.hotText)\n"
    "        t.logo?.let { o.put(\"logo\", it) }\n"
    "        arr.put(o)\n"
    "    }\n"
    "    return arr.toString()\n"
    "}\n"
    "\n"
    "/** 纯 JSON 解码：坏数据/空串返回空列表；url 为空的条目丢弃 */\n"
    "internal fun decodeFavoriteTopicsJson(json: String): List<HupuTopicInfo> {\n"
    "    return runCatching {\n"
    "        val arr = JSONArray(json)\n"
    "        (0 until arr.length()).mapNotNull { i ->\n"
    "            val o = arr.optJSONObject(i) ?: return@mapNotNull null\n"
    "            val url = o.optString(\"url\")\n"
    "            if (url.isEmpty()) return@mapNotNull null\n"
    "            HupuTopicInfo(\n"
    "                topicId = o.optString(\"topicId\"),\n"
    "                name = o.optString(\"name\"),\n"
    "                url = url,\n"
    "                hotText = o.optString(\"hotText\"),\n"
    "                logo = o.optString(\"logo\").ifEmpty { null },\n"
    "            )\n"
    "        }\n"
    "    }.getOrDefault(emptyList())\n"
    "}\n"
    "\n"
    "/** 1.191: 收藏切换的纯函数（便于单测）：已存在则移除，否则置顶插入并按上限截断 */\n"
    "internal fun toggleFavoriteList(\n"
    "    cur: List<HupuTopicInfo>,\n"
    "    t: HupuTopicInfo,\n"
    "    max: Int,\n"
    "): List<HupuTopicInfo> =\n"
    "    if (cur.any { it.url == t.url }) cur.filterNot { it.url == t.url }\n"
    "    else (listOf(t) + cur).take(max)\n"
)
s = sub(s, old, new, "Prefs.favCodec")
save(p, s)

# ==========================================================================
# B2) FeedUi.kt —— Chip 支持矢量图标
# ==========================================================================
p = os.path.join(COMPONENTS, "FeedUi.kt")
s = load(p)
s = sub(
    s,
    "import androidx.compose.material3.CircularProgressIndicator\n",
    "import androidx.compose.material3.CircularProgressIndicator\n"
    "import androidx.compose.material3.Icon\n",
    "FeedUi.import.Icon",
)
s = sub(
    s,
    "import androidx.compose.ui.draw.clip\n",
    "import androidx.compose.ui.draw.clip\n"
    "import androidx.compose.ui.graphics.vector.ImageVector\n",
    "FeedUi.import.ImageVector",
)
s = sub(
    s,
    "/** 胶囊 chip（横滑条条目：文字 + 可选圆形 logo） */\n"
    "@Composable\n"
    "internal fun Chip(\n"
    "    text: String,\n"
    "    logoUrl: String? = null,\n"
    "    selected: Boolean,\n"
    "    onClick: () -> Unit,\n"
    ") {\n",
    "/** 胶囊 chip（横滑条条目：文字 + 可选圆形 logo 或矢量图标） */\n"
    "@Composable\n"
    "internal fun Chip(\n"
    "    text: String,\n"
    "    logoUrl: String? = null,\n"
    "    /** 1.191: 可选矢量图标（如「收藏专区」的星标）；有 logoUrl 时以 logo 优先 */\n"
    "    leadingIcon: ImageVector? = null,\n"
    "    selected: Boolean,\n"
    "    onClick: () -> Unit,\n"
    ") {\n",
    "FeedUi.Chip.sig",
)
s = sub(
    s,
    "        logoUrl?.let {\n"
    "            AsyncImage(\n"
    "                model = it,\n"
    "                contentDescription = null,\n"
    "                contentScale = ContentScale.Crop,\n"
    "                modifier = Modifier.size(20.dp).clip(CircleShape),\n"
    "            )\n"
    "            Spacer(Modifier.width(6.dp))\n"
    "        }\n",
    "        logoUrl?.let {\n"
    "            AsyncImage(\n"
    "                model = it,\n"
    "                contentDescription = null,\n"
    "                contentScale = ContentScale.Crop,\n"
    "                modifier = Modifier.size(20.dp).clip(CircleShape),\n"
    "            )\n"
    "            Spacer(Modifier.width(6.dp))\n"
    "        }\n"
    "        // 1.191: 无 logo 时可显示矢量图标（与文字同色系）\n"
    "        if (logoUrl == null && leadingIcon != null) {\n"
    "            Icon(\n"
    "                leadingIcon,\n"
    "                contentDescription = null,\n"
    "                tint = if (selected) MaterialTheme.colorScheme.onPrimary\n"
    "                       else MaterialTheme.colorScheme.onSurfaceVariant,\n"
    "                modifier = Modifier.size(18.dp),\n"
    "            )\n"
    "            Spacer(Modifier.width(6.dp))\n"
    "        }\n",
    "FeedUi.Chip.icon",
)
save(p, s)

# ==========================================================================
# B3/C) ZonePage.kt —— 收藏专区 tab + 右上角收藏按钮 + 反馈提示
# ==========================================================================
p = os.path.join(PAGES, "ZonePage.kt")
s = load(p)
s = sub(
    s,
    "import com.java.myapplication.ui.components.PageHeader\n",
    "import com.java.myapplication.ui.components.HupuIcons\n"
    "import com.java.myapplication.ui.components.PageHeader\n",
    "Zone.import",
)

# 虚拟 tab id 常量
s = sub(
    s,
    "/** 大类横滑条：单选，16dp 限宽裁剪（与首页一致） */\n",
    "/** 1.191: 「收藏专区」虚拟 tab 的 id（不是真实大类，仅本页内使用） */\n"
    "private const val FAV_CATE_ID = \"__fav_topics__\"\n"
    "\n"
    "/** 大类横滑条：单选，16dp 限宽裁剪（与首页一致） */\n",
    "Zone.const",
)

# 轻提示状态
s = sub(
    s,
    "    var cateRefreshTick by remember { mutableIntStateOf(0) }\n",
    "    var cateRefreshTick by remember { mutableIntStateOf(0) }\n"
    "    // 1.191: 收藏专区操作的轻提示（顶部胶囊，点击立即消失）\n"
    "    var zoneToast by remember { mutableStateOf<String?>(null) }\n",
    "Zone.toastState",
)

# 默认选中：有收藏 → 收藏专区
s = sub(
    s,
    "        if (list.isNotEmpty()) {\n"
    "            categories = list\n"
    "            if (selectedCateId.isEmpty() || list.none { it.cateId == selectedCateId }) {\n"
    "                selectedCateId = list.first().cateId\n"
    "            }\n"
    "        }\n",
    "        if (list.isNotEmpty()) {\n"
    "            categories = list\n"
    "            // 1.191: 有收藏专区 → 默认选中「收藏专区」；否则保持/回落到第一个大类\n"
    "            val stillValid = (selectedCateId == FAV_CATE_ID && hasFav) ||\n"
    "                list.any { it.cateId == selectedCateId }\n"
    "            if (!stillValid) {\n"
    "                selectedCateId = if (hasFav) FAV_CATE_ID else list.first().cateId\n"
    "            }\n"
    "        }\n",
    "Zone.default",
)

# 收藏列表 + cateIds
s = sub(
    s,
    "    // 1.186: 顶部大类 Tab 左右滑动切换（仅本大页面内）\n"
    "    val cateIds = remember(categories) { categories?.map { it.cateId } ?: emptyList() }\n",
    "    // 1.191: 收藏专区——有收藏时在横滑条最前面插入「收藏专区」tab（默认选中）\n"
    "    val favTopics = remember(HupuPrefs.favoriteTopicsVersion) { HupuPrefs.loadFavoriteTopics() }\n"
    "    val hasFav = favTopics.isNotEmpty()\n"
    "    // 1.186: 顶部大类 Tab 左右滑动切换（仅本大页面内）\n"
    "    val cateIds = remember(categories, hasFav) {\n"
    "        buildList {\n"
    "            if (hasFav) add(FAV_CATE_ID)\n"
    "            categories?.forEach { add(it.cateId) }\n"
    "        }\n"
    "    }\n",
    "Zone.cateIds",
)

# 收藏清空后的回落
s = sub(
    s,
    "        selectedCateId = cateIds[ni]\n"
    "    }\n"
    "\n"
    "    Box(modifier.fillMaxSize()) {\n",
    "        selectedCateId = cateIds[ni]\n"
    "    }\n"
    "\n"
    "    // 1.191: 收藏被全部移除时，若正停留在「收藏专区」→ 回落到第一个大类（该 tab 已消失）\n"
    "    LaunchedEffect(hasFav, categories) {\n"
    "        if (!hasFav && selectedCateId == FAV_CATE_ID) {\n"
    "            categories?.firstOrNull()?.let { selectedCateId = it.cateId }\n"
    "        }\n"
    "    }\n"
    "\n"
    "    Box(modifier.fillMaxSize()) {\n",
    "Zone.fallback",
)

# CateBar 调用 + 网格数据源
s = sub(
    s,
    "                else -> {\n"
    "                    CateBar(categories!!, selectedCateId) { selectedCateId = it }\n"
    "                    val topics = current?.topics.orEmpty()\n"
    "                    if (topics.isEmpty()) {\n"
    "                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {\n"
    "                            Text(\n"
    "                                \"该分类暂无版块\",\n",
    "                else -> {\n"
    "                    CateBar(\n"
    "                        categories = categories!!,\n"
    "                        selected = selectedCateId,\n"
    "                        onSelect = { selectedCateId = it },\n"
    "                        showFavorites = hasFav,\n"
    "                        onSelectFavorites = { selectedCateId = FAV_CATE_ID },\n"
    "                    )\n"
    "                    // 1.191: 选中「收藏专区」时网格数据来自收藏列表，其余走大类版块\n"
    "                    val favTab = selectedCateId == FAV_CATE_ID\n"
    "                    val topics = if (favTab) favTopics else current?.topics.orEmpty()\n"
    "                    if (topics.isEmpty()) {\n"
    "                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {\n"
    "                            Text(\n"
    "                                if (favTab) \"还没有收藏专区\" else \"该分类暂无版块\",\n",
    "Zone.cateBarCall",
)

# TopicFeedOverlay 调用：传收藏态
s = sub(
    s,
    "            TopicFeedOverlay(\n"
    "                topic = opened,\n"
    "                closing = overlayClosing,\n",
    "            TopicFeedOverlay(\n"
    "                topic = opened,\n"
    "                isFavorite = favTopics.any { it.url == opened.url },\n"
    "                onToggleFavorite = {\n"
    "                    val nowFav = HupuPrefs.toggleFavoriteTopic(opened)\n"
    "                    zoneToast = if (nowFav) \"已收藏「${opened.name}」\" else \"已取消收藏\"\n"
    "                },\n"
    "                closing = overlayClosing,\n",
    "Zone.overlayCall",
)

# 提示条：插在根 Box 内、一级页 Column 之后（二级页注释之前），zIndex 9 盖在二级页之上
s = sub(
    s,
    "// ---------- 二级页：版块话题流（盖入式转场） ----------",
    "// 1.191: 收藏专区操作反馈（顶部胶囊，点击立即消失）\n"
    "        zoneToast?.let { msg ->\n"
    "            Box(\n"
    "                Modifier\n"
    "                    .align(Alignment.TopCenter)\n"
    "                    .zIndex(9f)\n"
    "                    .statusBarsPadding()\n"
    "                    .padding(top = 64.dp),\n"
    "            ) {\n"
    "                Text(\n"
    "                    msg,\n"
    "                    fontSize = 13.sp,\n"
    "                    color = MaterialTheme.colorScheme.onSurface,\n"
    "                    modifier = Modifier\n"
    "                        .clip(RoundedCornerShape(20.dp))\n"
    "                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f))\n"
    "                        .clickable { zoneToast = null }\n"
    "                        .padding(horizontal = 16.dp, vertical = 8.dp),\n"
    "                )\n"
    "            }\n"
    "        }\n"
    "        // ---------- 二级页：版块话题流（盖入式转场） ----------",
    "Zone.toastUi",
)

# TopicFeedOverlay 签名
s = sub(
    s,
    "private fun TopicFeedOverlay(\n"
    "    topic: HupuTopicInfo,\n"
    "    closing: Boolean,\n",
    "private fun TopicFeedOverlay(\n"
    "    topic: HupuTopicInfo,\n"
    "    /** 1.191: 该专区是否已收藏（右上角收藏按钮的选中态） */\n"
    "    isFavorite: Boolean,\n"
    "    onToggleFavorite: () -> Unit,\n"
    "    closing: Boolean,\n",
    "Zone.overlaySig",
)

# 刷新按钮 → 收藏按钮
s = sub(
    s,
    "                // 刷新按钮（已在内容时手动刷新）\n"
    "                Box(\n"
    "                    Modifier\n"
    "                        .size(40.dp)\n"
    "                        .clip(CircleShape)\n"
    "                        .clickable { onRefresh() },\n"
    "                    contentAlignment = Alignment.Center,\n"
    "                ) {\n"
    "                    Icon(\n"
    "                        Icons.Rounded.Refresh,\n"
    "                        contentDescription = \"刷新\",\n"
    "                        tint = MaterialTheme.colorScheme.onSurface,\n"
    "                    )\n"
    "                }\n",
    "                // 1.191: 收藏该专区（原「刷新」按钮——刷新已由下拉刷新覆盖）\n"
    "                Box(\n"
    "                    Modifier\n"
    "                        .size(40.dp)\n"
    "                        .clip(CircleShape)\n"
    "                        .clickable { onToggleFavorite() },\n"
    "                    contentAlignment = Alignment.Center,\n"
    "                ) {\n"
    "                    Icon(\n"
    "                        HupuIcons.StarRate,\n"
    "                        contentDescription = if (isFavorite) \"取消收藏\" else \"收藏专区\",\n"
    "                        tint = if (isFavorite) MaterialTheme.colorScheme.primary\n"
    "                               else MaterialTheme.colorScheme.onSurfaceVariant,\n"
    "                    )\n"
    "                }\n",
    "Zone.favButton",
)

# CateBar 定义
s = sub(
    s,
    "private fun CateBar(\n"
    "    categories: List<HupuCategory>,\n"
    "    selected: String,\n"
    "    onSelect: (String) -> Unit,\n"
    ") {\n"
    "    val barState = rememberLazyListState()\n"
    "    val selIdx = categories.indexOfFirst { it.cateId == selected }\n"
    "    LaunchedEffect(selected) { if (selIdx >= 0) barState.animateChipCenterTo(selIdx) }\n",
    "private fun CateBar(\n"
    "    categories: List<HupuCategory>,\n"
    "    selected: String,\n"
    "    onSelect: (String) -> Unit,\n"
    "    /** 1.191: 有收藏专区时，最前面插一个「收藏专区」tab（图标 + 默认选中） */\n"
    "    showFavorites: Boolean = false,\n"
    "    onSelectFavorites: () -> Unit = {},\n"
    ") {\n"
    "    val barState = rememberLazyListState()\n"
    "    val allIds = if (showFavorites) listOf(FAV_CATE_ID) + categories.map { it.cateId }\n"
    "                 else categories.map { it.cateId }\n"
    "    val selIdx = allIds.indexOf(selected)\n"
    "    LaunchedEffect(selected, showFavorites) { if (selIdx >= 0) barState.animateChipCenterTo(selIdx) }\n",
    "Zone.cateBarSig",
)
s = sub(
    s,
    "        horizontalArrangement = Arrangement.spacedBy(10.dp),\n"
    "    ) {\n"
    "        items(categories, key = { it.cateId }) { c ->\n",
    "        horizontalArrangement = Arrangement.spacedBy(10.dp),\n"
    "    ) {\n"
    "        // 1.191: 「收藏专区」永远排在最前面（图标用与帖子「收藏」同源的星标）\n"
    "        if (showFavorites) {\n"
    "            item(key = FAV_CATE_ID) {\n"
    "                Chip(\n"
    "                    text = \"收藏专区\",\n"
    "                    leadingIcon = HupuIcons.StarRate,\n"
    "                    selected = selected == FAV_CATE_ID,\n"
    "                    onClick = onSelectFavorites,\n"
    "                )\n"
    "            }\n"
    "        }\n"
    "        items(categories, key = { it.cateId }) { c ->\n",
    "Zone.cateBarBody",
)
save(p, s)

# ==========================================================================
# D) 单测：收藏专区编解码 + 切换语义
# ==========================================================================
p = os.path.join(TESTDIR, "HupuPrefsTest.kt")
s = load(p)
s = sub(
    s,
    "import com.java.myapplication.data.HupuTopicInfo\n",
    "import com.java.myapplication.data.HupuTopicInfo\n"
    "import com.java.myapplication.data.decodeFavoriteTopicsJson\n"
    "import com.java.myapplication.data.encodeFavoriteTopicsJson\n"
    "import com.java.myapplication.data.toggleFavoriteList\n",
    "Test.imports",
)
old_tail = (
    "        val url2 = \"file:///x/stickers_local/sticker_\" + stickerContentKey(same) + \".png\"\n"
    "        assertEquals(url1, url2)\n"
    "    }\n"
    "}\n"
)
new_tail = (
    "        val url2 = \"file:///x/stickers_local/sticker_\" + stickerContentKey(same) + \".png\"\n"
    "        assertEquals(url1, url2)\n"
    "    }\n"
    "\n"
    "    @Test\n"
    "    fun favoriteTopicsJson_roundTrip_keepsHotTextAndLogo() {\n"
    "        val list = listOf(\n"
    "            HupuTopicInfo(\n"
    "                topicId = \"1\",\n"
    "                name = \"步行街\",\n"
    "                url = \"https://bbs.hupu.com/bxj\",\n"
    "                hotText = \"1.2万\",\n"
    "                logo = \"https://i1.hupu.com/a.png\",\n"
    "            ),\n"
    "            HupuTopicInfo(topicId = \"2\", name = \"CBA\", url = \"https://bbs.hupu.com/cba\"),\n"
    "        )\n"
    "        val back = decodeFavoriteTopicsJson(encodeFavoriteTopicsJson(list))\n"
    "        assertEquals(2, back.size)\n"
    "        assertEquals(\"步行街\", back[0].name)\n"
    "        assertEquals(\"1.2万\", back[0].hotText)\n"
    "        assertEquals(\"https://i1.hupu.com/a.png\", back[0].logo)\n"
    "        // 没有热度/logo 的条目解码后为空（不是字符串 \"null\"）\n"
    "        assertEquals(\"\", back[1].hotText)\n"
    "        assertEquals(null, back[1].logo)\n"
    "    }\n"
    "\n"
    "    @Test\n"
    "    fun favoriteTopicsJson_badInput_returnsEmpty() {\n"
    "        assertEquals(0, decodeFavoriteTopicsJson(\"\").size)\n"
    "        assertEquals(0, decodeFavoriteTopicsJson(\"not json\").size)\n"
    "        assertEquals(0, decodeFavoriteTopicsJson(\"{\\\"a\\\":1}\").size)\n"
    "        // url 为空的条目丢弃（没有 url 无法再次打开该专区）\n"
    "        assertEquals(0, decodeFavoriteTopicsJson(\"[{\\\"name\\\":\\\"x\\\"}]\").size)\n"
    "    }\n"
    "\n"
    "    @Test\n"
    "    fun toggleFavoriteList_addTop_remove_capAtMax() {\n"
    "        val a = HupuTopicInfo(topicId = \"1\", name = \"A\", url = \"u1\")\n"
    "        val b = HupuTopicInfo(topicId = \"2\", name = \"B\", url = \"u2\")\n"
    "        val c = HupuTopicInfo(topicId = \"3\", name = \"C\", url = \"u3\")\n"
    "        // 新收藏置顶\n"
    "        assertEquals(listOf(\"u2\", \"u1\"), toggleFavoriteList(listOf(a), b, 50).map { it.url })\n"
    "        // 再点一次 = 取消\n"
    "        assertEquals(listOf(\"u1\"), toggleFavoriteList(listOf(b, a), b, 50).map { it.url })\n"
    "        // 上限：新收藏置顶，尾部被挤出\n"
    "        assertEquals(listOf(\"u3\", \"u1\"), toggleFavoriteList(listOf(a, b), c, 2).map { it.url })\n"
    "    }\n"
    "}\n"
)
s = sub(s, old_tail, new_tail, "Test.tail")
save(p, s)

print("BATCH3 OK")