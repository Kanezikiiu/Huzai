#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""1.192 功能：首页「热帖」与评分页「虎扑评分」固定 tab 的隐藏开关。
   设计（用户确认）：固定项不参与排序；采用「置灰 + 说明」方式，恒保证每个页面至少 1 个 tab：
     · 固定项开关：当关掉它会导致 0 tab（自定义列表为空）时置灰，并显示「至少保留一个频道」
     · 自定义项：固定项已隐藏且它是最后一个时，取消/移除被锁定并置灰
     · 「恢复默认」（会清空自定义列表）时，若固定项处于隐藏则自动恢复显示
   本脚本先校验全部命中数，再统一写盘（任一断言失败则不改动任何文件）。"""
import io

EDITS = {}

def patch(path, old, new, expect):
    EDITS.setdefault(path, []).append((old, new, expect))

B = "app/src/main/java/com/java/myapplication/"

# ============ 1) HupuPrefs：两个隐藏开关 ============
patch(B + "data/HupuPrefs.kt",
      'private const val KEY_HOME_TOPICS = "home_topics_v1"\n',
      'private const val KEY_HOME_TOPICS = "home_topics_v1"\n'
      '    private const val KEY_HOME_HOT_HIDDEN = "home_hot_hidden_v1"\n', 1)
patch(B + "data/HupuPrefs.kt",
      'private const val KEY_SCORE_GAMES = "score_games_v1"\n',
      'private const val KEY_SCORE_GAMES = "score_games_v1"\n'
      '    private const val KEY_SCORE_COMMON_HIDDEN = "score_common_hidden_v1"\n', 1)
patch(B + "data/HupuPrefs.kt",
      "fun clearHomeTopics() {",
      "/** 1.192: 首页「热帖」固定频道是否隐藏（恒保证「热帖 + 已选话题」至少一个可见） */\n"
      "    fun isHomeHotHidden(): Boolean = prefs.getBoolean(KEY_HOME_HOT_HIDDEN, false)\n"
      "\n"
      "    fun setHomeHotHidden(hidden: Boolean) {\n"
      "        prefs.edit().putBoolean(KEY_HOME_HOT_HIDDEN, hidden).apply()\n"
      "        homeTopicsVersion++\n"
      "    }\n"
      "\n"
      "    fun clearHomeTopics() {", 1)
patch(B + "data/HupuPrefs.kt",
      "fun clearScoreGames() {",
      "/** 1.192: 评分页「虎扑评分」固定频道是否隐藏（恒保证「虎扑评分 + 已选赛事」至少一个可见） */\n"
      "    fun isScoreCommonHidden(): Boolean = prefs.getBoolean(KEY_SCORE_COMMON_HIDDEN, false)\n"
      "\n"
      "    fun setScoreCommonHidden(hidden: Boolean) {\n"
      "        prefs.edit().putBoolean(KEY_SCORE_COMMON_HIDDEN, hidden).apply()\n"
      "        scoreGamesVersion++\n"
      "    }\n"
      "\n"
      "    fun clearScoreGames() {", 1)

# ============ 2) FeedUi：固定频道开关行 ============
patch(B + "ui/components/FeedUi.kt",
      "internal fun Modifier.tapGuard(): Modifier =",
      "/**\n"
      " * 1.192: 固定频道开关行（首页「热帖」/ 评分页「虎扑评分」）——不参与排序，只有显示/隐藏两态。\n"
      " * [enabled] = false 时置灰，并把 [subtitle] 当作说明（用于「至少保留一个频道」的约束）。\n"
      " */\n"
      "@Composable\n"
      "internal fun FixedChannelRow(\n"
      "    name: String,\n"
      "    subtitle: String,\n"
      "    checked: Boolean,\n"
      "    enabled: Boolean,\n"
      "    onCheckedChange: (Boolean) -> Unit,\n"
      ") {\n"
      "    Row(\n"
      "        Modifier\n"
      "            .fillMaxWidth()\n"
      "            .padding(horizontal = 16.dp, vertical = 4.dp)\n"
      "            .clip(RoundedCornerShape(14.dp))\n"
      "            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))\n"
      "            .padding(horizontal = 14.dp, vertical = 10.dp),\n"
      "        verticalAlignment = Alignment.CenterVertically,\n"
      "    ) {\n"
      "        Column(Modifier.weight(1f)) {\n"
      "            Text(name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)\n"
      "            Spacer(Modifier.height(2.dp))\n"
      "            Text(\n"
      "                subtitle,\n"
      "                fontSize = 11.sp,\n"
      "                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant\n"
      "                else MaterialTheme.colorScheme.error,\n"
      "            )\n"
      "        }\n"
      "        Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)\n"
      "    }\n"
      "}\n"
      "\n"
      "internal fun Modifier.tapGuard(): Modifier =", 1)
patch(B + "ui/components/FeedUi.kt",
      "import androidx.compose.material3.Text\n",
      "import androidx.compose.material3.Switch\nimport androidx.compose.material3.Text\n", 1)

# ============ 3) 首页频道自定义页 ============
patch(B + "ui/pages/TopicPickerPage.kt",
      "import com.java.myapplication.ui.components.Chip\n",
      "import com.java.myapplication.ui.components.Chip\n"
      "import com.java.myapplication.ui.components.FixedChannelRow\n", 1)
patch(B + "ui/pages/TopicPickerPage.kt",
      "    var limitHintTick by remember { mutableIntStateOf(0) }\n",
      "    var limitHintTick by remember { mutableIntStateOf(0) }\n"
      "    // 1.192: 「热帖」固定频道的隐藏开关——恒保证「热帖 + 已选话题」≥ 1 个 tab\n"
      "    var hotHidden by remember { mutableStateOf(HupuPrefs.isHomeHotHidden()) }\n"
      "    val canHideHot = !hotHidden || selected.isNotEmpty()\n"
      "    val onlyOneLeft = hotHidden && selected.size == 1\n", 1)
patch(B + "ui/pages/TopicPickerPage.kt",
      "    fun toggle(t: HupuTopicInfo) {\n",
      "    fun toggle(t: HupuTopicInfo) {\n"
      "        // 1.192: 热帖已隐藏时，不允许取消最后一个频道（否则首页会出现 0 个 tab）\n"
      "        if (hotHidden && selected.size == 1 && selected.any { it.url == t.url }) return\n", 1)
patch(B + "ui/pages/TopicPickerPage.kt",
      "    fun remove(t: HupuTopicInfo) {\n",
      "    fun remove(t: HupuTopicInfo) {\n"
      "        // 1.192: 同上——热帖已隐藏时，最后一个频道不可移除\n"
      "        if (hotHidden && selected.size == 1) return\n", 1)
patch(B + "ui/pages/TopicPickerPage.kt",
      "    fun resetDefault() {\n        HupuPrefs.clearHomeTopics()\n        selected = emptyList()\n    }\n",
      "    fun resetDefault() {\n"
      "        HupuPrefs.clearHomeTopics()\n"
      "        selected = emptyList()\n"
      "        // 1.192: 话题被清空后若热帖仍隐藏，会出现 0 个 tab → 同时恢复显示热帖\n"
      "        if (hotHidden) {\n"
      "            hotHidden = false\n"
      "            HupuPrefs.setHomeHotHidden(false)\n"
      "        }\n"
      "    }\n", 1)
patch(B + "ui/pages/TopicPickerPage.kt",
      "                    // 已选条（长按拖动排序 / 点按移除）\n",
      "                    // 1.192: 「热帖」固定频道开关（不参与排序）\n"
      "                    FixedChannelRow(\n"
      "                        name = \"热帖\",\n"
      "                        subtitle = if (canHideHot) \"首页默认内容流 · 不参与排序\" else \"至少保留一个频道\",\n"
      "                        checked = !hotHidden,\n"
      "                        enabled = canHideHot,\n"
      "                    ) { on ->\n"
      "                        hotHidden = on\n"
      "                        HupuPrefs.setHomeHotHidden(on)\n"
      "                    }\n"
      "                    // 已选条（长按拖动排序 / 点按移除）\n", 1)
patch(B + "ui/pages/TopicPickerPage.kt",
      "SelectedBar(selected = selected, onReorder = ::reorder, onRemove = ::remove)",
      "SelectedBar(selected = selected, onReorder = ::reorder, onRemove = ::remove, removable = !onlyOneLeft)", 1)
patch(B + "ui/pages/TopicPickerPage.kt",
      "    onRemove: (HupuTopicInfo) -> Unit,\n) {",
      "    onRemove: (HupuTopicInfo) -> Unit,\n"
      "    /** 1.192: 仅剩它一个 tab 时置灰移除（避免 0 个 tab） */\n"
      "    removable: Boolean = true,\n"
      ") {", 1)
patch(B + "ui/pages/TopicPickerPage.kt",
      "                    .clickable { onRemove(t) }",
      "                    .clickable(enabled = removable) { onRemove(t) }", 1)
patch(B + "ui/pages/TopicPickerPage.kt",
      "                    contentDescription = \"移除\",\n"
      "                    modifier = Modifier.size(16.dp),\n"
      "                    tint = MaterialTheme.colorScheme.onSurfaceVariant,\n",
      "                    contentDescription = \"移除\",\n"
      "                    modifier = Modifier.size(16.dp),\n"
      "                    tint = if (removable) MaterialTheme.colorScheme.onSurfaceVariant\n"
      "                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),\n", 1)
patch(B + "ui/pages/TopicPickerPage.kt",
      "                            TopicPickRow(topic = t, isSelected = isSelected, onToggle = { toggle(t) })",
      "                            TopicPickRow(\n"
      "                                topic = t,\n"
      "                                isSelected = isSelected,\n"
      "                                locked = onlyOneLeft && isSelected,\n"
      "                                onToggle = { toggle(t) },\n"
      "                            )", 1)
patch(B + "ui/pages/TopicPickerPage.kt",
      "    isSelected: Boolean,\n    onToggle: () -> Unit,\n) {",
      "    isSelected: Boolean,\n"
      "    onToggle: () -> Unit,\n"
      "    /** 1.192: 锁定（不可取消）——热帖已隐藏且它是最后一个频道时 */\n"
      "    locked: Boolean = false,\n"
      ") {", 1)
patch(B + "ui/pages/TopicPickerPage.kt",
      "            .clickable { onToggle() }",
      "            .clickable(enabled = !locked) { onToggle() }", 1)
patch(B + "ui/pages/TopicPickerPage.kt",
      "            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,",
      "            tint = when {\n"
      "                locked -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)\n"
      "                isSelected -> MaterialTheme.colorScheme.primary\n"
      "                else -> MaterialTheme.colorScheme.onSurfaceVariant\n"
      "            },", 1)

# ============ 4) 评分频道自定义页 ============
patch(B + "ui/pages/ScorePickerPage.kt",
      "import com.java.myapplication.ui.components.SecondaryPage\n",
      "import com.java.myapplication.ui.components.FixedChannelRow\n"
      "import com.java.myapplication.ui.components.SecondaryPage\n", 1)
patch(B + "ui/pages/ScorePickerPage.kt",
      "    fun toggle(id: String) {\n",
      "    // 1.192: 「虎扑评分」固定频道的隐藏开关——恒保证「虎扑评分 + 已选赛事」≥ 1 个 tab\n"
      "    var commonHidden by remember { mutableStateOf(HupuPrefs.isScoreCommonHidden()) }\n"
      "    val canHideCommon = !commonHidden || selected.isNotEmpty()\n"
      "    val onlyOneLeft = commonHidden && selected.size == 1\n"
      "    fun toggle(id: String) {\n"
      "        // 1.192: 虎扑评分已隐藏时，不允许取消最后一个赛事（否则评分页会出现 0 个 tab）\n"
      "        if (commonHidden && selected.size == 1 && id in selected) return\n", 1)
patch(B + "ui/pages/ScorePickerPage.kt",
      "    fun remove(id: String) {\n",
      "    fun remove(id: String) {\n"
      "        // 1.192: 同上——虎扑评分已隐藏时，最后一个赛事不可移除\n"
      "        if (commonHidden && selected.size == 1) return\n", 1)
patch(B + "ui/pages/ScorePickerPage.kt",
      "    fun resetDefault() {\n        HupuPrefs.clearScoreGames()\n        selected = emptyList()\n    }\n",
      "    fun resetDefault() {\n"
      "        HupuPrefs.clearScoreGames()\n"
      "        selected = emptyList()\n"
      "        // 1.192: 赛事被清空后若虎扑评分仍隐藏，会出现 0 个 tab → 同时恢复显示\n"
      "        if (commonHidden) {\n"
      "            commonHidden = false\n"
      "            HupuPrefs.setScoreCommonHidden(false)\n"
      "        }\n"
      "    }\n", 1)
patch(B + "ui/pages/ScorePickerPage.kt",
      "            // 已选条（长按拖动排序 / 点按移除）\n",
      "            // 1.192: 「虎扑评分」固定频道开关（不参与排序）\n"
      "            FixedChannelRow(\n"
      "                name = \"虎扑评分\",\n"
      "                subtitle = if (canHideCommon) \"评分页默认内容流 · 不参与排序\" else \"至少保留一个频道\",\n"
      "                checked = !commonHidden,\n"
      "                enabled = canHideCommon,\n"
      "            ) { on ->\n"
      "                commonHidden = on\n"
      "                HupuPrefs.setScoreCommonHidden(on)\n"
      "            }\n"
      "            // 已选条（长按拖动排序 / 点按移除）\n", 1)
patch(B + "ui/pages/ScorePickerPage.kt",
      "                ScoreSelectedBar(selected = selected, names = nameById, onReorder = ::reorder, onRemove = ::remove)",
      "                ScoreSelectedBar(\n"
      "                    selected = selected,\n"
      "                    names = nameById,\n"
      "                    onReorder = ::reorder,\n"
      "                    onRemove = ::remove,\n"
      "                    removable = !onlyOneLeft,\n"
      "                )", 1)
patch(B + "ui/pages/ScorePickerPage.kt",
      "    onRemove: (String) -> Unit,\n) {",
      "    onRemove: (String) -> Unit,\n"
      "    /** 1.192: 仅剩它一个 tab 时置灰移除（避免 0 个 tab） */\n"
      "    removable: Boolean = true,\n"
      ") {", 1)
patch(B + "ui/pages/ScorePickerPage.kt",
      "                    .clickable { onRemove(id) }",
      "                    .clickable(enabled = removable) { onRemove(id) }", 1)
patch(B + "ui/pages/ScorePickerPage.kt",
      "                    contentDescription = \"移除\",\n"
      "                    modifier = Modifier.size(16.dp),\n"
      "                    tint = MaterialTheme.colorScheme.onSurfaceVariant,\n",
      "                    contentDescription = \"移除\",\n"
      "                    modifier = Modifier.size(16.dp),\n"
      "                    tint = if (removable) MaterialTheme.colorScheme.onSurfaceVariant\n"
      "                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),\n", 1)
patch(B + "ui/pages/ScorePickerPage.kt",
      "                        val checked = id in selected\n",
      "                        val checked = id in selected\n"
      "                        // 1.192: 仅剩它一个 tab 时锁定（不可取消）\n"
      "                        val locked = onlyOneLeft && checked\n", 1)
patch(B + "ui/pages/ScorePickerPage.kt",
      "                                .background(if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)",
      "                                .background(\n"
      "                                    when {\n"
      "                                        !checked -> MaterialTheme.colorScheme.surface\n"
      "                                        locked -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)\n"
      "                                        else -> MaterialTheme.colorScheme.primary\n"
      "                                    }\n"
      "                                )", 1)
patch(B + "ui/pages/ScorePickerPage.kt",
      "                                .clickable { toggle(id) }",
      "                                .clickable(enabled = !locked) { toggle(id) }", 1)

# ============ 5) 首页：尊重「热帖」隐藏 ============
patch(B + "ui/pages/HomePage.kt",
      "    val effectiveTopics = remember(data.hotTopics, HupuPrefs.homeTopicsVersion) {\n"
      "        HupuPrefs.effectiveHomeTopics(data.hotTopics)\n"
      "    }\n"
      "    // 正在浏览的频道被移除时，退回热帖（否则该流选择器失效）\n"
      "    LaunchedEffect(effectiveTopics) {\n"
      "        if (selected != \"hot\" && effectiveTopics.none { it.url == selected }) {\n"
      "            selected = \"hot\"\n"
      "        }\n"
      "    }\n",
      "    val effectiveTopics = remember(data.hotTopics, HupuPrefs.homeTopicsVersion) {\n"
      "        HupuPrefs.effectiveHomeTopics(data.hotTopics)\n"
      "    }\n"
      "    // 1.192: 「热帖」隐藏开关——隐藏后横滑条不再显示它\n"
      "    // （设置页已保证「热帖 + 已选话题」≥1；这里再兜底：话题为空时仍显示热帖，页面不会 0 tab）\n"
      "    val hotHidden = remember(HupuPrefs.homeTopicsVersion) { HupuPrefs.isHomeHotHidden() }\n"
      "    val showHot = !hotHidden || effectiveTopics.isEmpty()\n"
      "    // 正在浏览的频道失效（被移除 / 热帖被隐藏且正停在它上面）时，回退到合法频道\n"
      "    LaunchedEffect(effectiveTopics, showHot) {\n"
      "        val valid = (selected == \"hot\" && showHot) || effectiveTopics.any { it.url == selected }\n"
      "        if (!valid) {\n"
      "            selected = if (showHot) \"hot\" else effectiveTopics.firstOrNull()?.url ?: \"hot\"\n"
      "        }\n"
      "    }\n", 1)
patch(B + "ui/pages/HomePage.kt",
      "    val tabUrls = remember(effectiveTopics) { listOf(\"hot\") + effectiveTopics.map { it.url } }",
      "    // 1.192: 热帖被隐藏时，左右滑动顺序里也不含它（与横滑条一致）\n"
      "    val tabUrls = remember(effectiveTopics, showHot) {\n"
      "        (if (showHot) listOf(\"hot\") else emptyList()) + effectiveTopics.map { it.url }\n"
      "    }", 1)
patch(B + "ui/pages/HomePage.kt",
      "        TopicBar(effectiveTopics, selected) {",
      "        TopicBar(effectiveTopics, selected, showHot) {", 1)
patch(B + "ui/pages/HomePage.kt",
      "private fun TopicBar(\n    topics: List<HupuTopicInfo>,\n    selected: String,\n    onSelect: (String) -> Unit,\n) {",
      "private fun TopicBar(\n"
      "    topics: List<HupuTopicInfo>,\n"
      "    selected: String,\n"
      "    showHot: Boolean = true,\n"
      "    onSelect: (String) -> Unit,\n"
      ") {", 1)
patch(B + "ui/pages/HomePage.kt",
      "    val selIdx = if (selected == \"hot\") 0 else 1 + topics.indexOfFirst { it.url == selected }",
      "    // 1.192: 热帖被隐藏时，话题 chip 的下标整体前移 1\n"
      "    val selIdx = if (selected == \"hot\") 0\n"
      "    else (if (showHot) 1 else 0) + topics.indexOfFirst { it.url == selected }", 1)
patch(B + "ui/pages/HomePage.kt",
      "        // 「热帖」chip：默认流（/all-gambia 全站热帖榜）\n"
      "        item(key = \"hot-chip\") {\n"
      "            Chip(text = \"热帖\", selected = selected == \"hot\") { onSelect(\"hot\") }\n"
      "        }\n",
      "        // 「热帖」chip：默认流（/all-gambia 全站热帖榜）——1.192 起可被设置页隐藏\n"
      "        if (showHot) {\n"
      "            item(key = \"hot-chip\") {\n"
      "                Chip(text = \"热帖\", selected = selected == \"hot\") { onSelect(\"hot\") }\n"
      "            }\n"
      "        }\n", 1)

# ============ 6) 评分页：尊重「虎扑评分」隐藏 ============
patch(B + "ui/pages/ScorePage.kt",
      "    val effectiveGames = remember(HupuPrefs.scoreGamesVersion) {\n"
      "        HupuPrefs.effectiveScoreGames(HupuMatchApi.GAMES)\n"
      "    }\n",
      "    val effectiveGames = remember(HupuPrefs.scoreGamesVersion) {\n"
      "        HupuPrefs.effectiveScoreGames(HupuMatchApi.GAMES)\n"
      "    }\n"
      "    // 1.192: 「虎扑评分」隐藏开关——隐藏后横滑条不再显示它\n"
      "    // （设置页已保证「虎扑评分 + 已选赛事」≥1；这里再兜底：赛事为空时仍显示，页面不会 0 tab）\n"
      "    val commonHidden = remember(HupuPrefs.scoreGamesVersion) { HupuPrefs.isScoreCommonHidden() }\n"
      "    val showCommon = !commonHidden || effectiveGames.isEmpty()\n", 1)
patch(B + "ui/pages/ScorePage.kt",
      "    var commonOpen by remember { mutableStateOf(true) }",
      "    // 1.192: 「虎扑评分」被隐藏时，默认直接落在第一个赛事上\n"
      "    var commonOpen by remember { mutableStateOf(!HupuPrefs.isScoreCommonHidden()) }", 1)
patch(B + "ui/pages/ScorePage.kt",
      "    LaunchedEffect(effectiveGames) {\n"
      "        if (commonOpen) return@LaunchedEffect\n"
      "        if (effectiveGames.none { it.first == selectedGame }) commonOpen = true\n"
      "    }\n",
      "    LaunchedEffect(effectiveGames) {\n"
      "        if (commonOpen) return@LaunchedEffect\n"
      "        if (effectiveGames.none { it.first == selectedGame }) commonOpen = true\n"
      "    }\n"
      "    // 1.192: 「虎扑评分」被隐藏后不允许停在它上面 → 回落到第一个赛事\n"
      "    LaunchedEffect(showCommon, effectiveGames) {\n"
      "        if (showCommon || effectiveGames.isEmpty()) return@LaunchedEffect\n"
      "        if (commonOpen) commonOpen = false\n"
      "        if (effectiveGames.none { it.first == selectedGame }) {\n"
      "            selectedGame = effectiveGames.first().first\n"
      "            if (!schedules.containsKey(selectedGame)) loadingGame = selectedGame\n"
      "        }\n"
      "    }\n", 1)
patch(B + "ui/pages/ScorePage.kt",
      "    fun swipeScoreTab(delta: Int) {\n"
      "        val cur = if (commonOpen) 0 else {\n"
      "            val i = effectiveGames.indexOfFirst { it.first == selectedGame }\n"
      "            if (i < 0) 0 else i + 1\n"
      "        }\n"
      "        val ni = cur + delta\n"
      "        if (ni < 0 || ni > effectiveGames.size) return\n"
      "        if (ni == 0) {\n"
      "            if (commonOpen) return\n"
      "            commonOpen = true\n"
      "            if (commonSubjects.isEmpty() && !commonFailed) commonLoading = true\n"
      "            if (commonSubjects.isEmpty()) loadCommonSubjects()\n"
      "        } else {\n"
      "            val id = effectiveGames[ni - 1].first\n"
      "            if (!commonOpen && selectedGame == id) return\n"
      "            commonOpen = false\n"
      "            selectedGame = id\n"
      "            if (!schedules.containsKey(id)) loadingGame = id\n"
      "        }\n"
      "    }\n",
      "    fun swipeScoreTab(delta: Int) {\n"
      "        // 1.192: 偏移随「虎扑评分」是否显示而变（隐藏时第一个赛事就是下标 0）\n"
      "        val offset = if (showCommon) 1 else 0\n"
      "        val cur = if (commonOpen) 0 else {\n"
      "            val i = effectiveGames.indexOfFirst { it.first == selectedGame }\n"
      "            if (i < 0) 0 else i + offset\n"
      "        }\n"
      "        val ni = cur + delta\n"
      "        if (ni < 0 || ni > offset + effectiveGames.size - 1) return\n"
      "        if (ni == 0 && showCommon) {\n"
      "            if (commonOpen) return\n"
      "            commonOpen = true\n"
      "            if (commonSubjects.isEmpty() && !commonFailed) commonLoading = true\n"
      "            if (commonSubjects.isEmpty()) loadCommonSubjects()\n"
      "        } else {\n"
      "            val gi = ni - offset\n"
      "            if (gi !in effectiveGames.indices) return\n"
      "            val id = effectiveGames[gi].first\n"
      "            if (!commonOpen && selectedGame == id) return\n"
      "            commonOpen = false\n"
      "            selectedGame = id\n"
      "            if (!schedules.containsKey(id)) loadingGame = id\n"
      "        }\n"
      "    }\n", 1)
patch(B + "ui/pages/ScorePage.kt",
      "        val selIdx = if (commonOpen) 0 else 1 + effectiveGames.indexOfFirst { it.first == selectedGame }",
      "        // 1.192: 虎扑评分被隐藏时，赛事 chip 的下标整体前移 1\n"
      "        val selIdx = if (commonOpen) 0\n"
      "        else (if (showCommon) 1 else 0) + effectiveGames.indexOfFirst { it.first == selectedGame }", 1)
patch(B + "ui/pages/ScorePage.kt",
      "            item(key = \"common\") {\n",
      "            // 1.192: 「虎扑评分」可在设置页隐藏\n"
      "            if (showCommon) {\n"
      "            item(key = \"common\") {\n", 1)
patch(B + "ui/pages/ScorePage.kt",
      "                    if (commonSubjects.isEmpty()) loadCommonSubjects()\n"
      "                }\n"
      "            }\n"
      "            items(effectiveGames, key = { it.first }) { (id, name) ->",
      "                    if (commonSubjects.isEmpty()) loadCommonSubjects()\n"
      "                }\n"
      "            }\n"
      "            }\n"
      "            items(effectiveGames, key = { it.first }) { (id, name) ->", 1)

# ---------- 原子应用 ----------
total = 0
for path, edits in EDITS.items():
    with io.open(path, encoding='utf-8') as f:
        s = f.read()
    for old, new, expect in edits:
        n = s.count(old)
        assert n == expect, "%s: expect %d of %r, got %d" % (path, expect, old[:70], n)
        s = s.replace(old, new)
        total += n
    with io.open(path, 'w', encoding='utf-8') as f:
        f.write(s)
    print("OK %s (%d edits)" % (path, len(edits)))
print("HIDE-TABS 1.192 OK, %d replacements" % total)