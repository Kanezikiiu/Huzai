#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""1.191: 把剩余 5 处 material3 AlertDialog 升级为 Liquid Glass 弹窗。

统一做法（与 ThreadDetailPage 一致）：页面根 Box 内给「内容层」挂
`.layerBackdrop(backdrop)`，弹窗作为内容层的**后续兄弟**画在同一个 Box 里，
毛玻璃才能采到页面像素；且弹窗绝不能被记录层包含（否则自采样）。
"""
import io
import os

BASE = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "..", "app", "src", "main", "java", "com", "java", "myapplication", "ui", "pages",
)

IMPORTS = (
    "import com.kyant.backdrop.backdrops.layerBackdrop\n"
    "import com.kyant.backdrop.backdrops.rememberLayerBackdrop\n"
    "import com.java.myapplication.ui.glass.LiquidGlassDialog\n"
)


def load(name):
    with io.open(os.path.join(BASE, name), encoding="utf-8") as f:
        return f.read()


def save(name, s):
    with io.open(os.path.join(BASE, name), "w", encoding="utf-8") as f:
        f.write(s)


def sub(s, old, new, where):
    n = s.count(old)
    assert n == 1, "命中 %d 次（期望 1）: %s :: %r" % (n, where, old[:80])
    return s.replace(old, new)


def drop_import(s, line, where):
    n = s.count(line)
    assert n == 1, "%s: import 命中 %d 次" % (where, n)
    return s.replace(line, "")


# --------------------------------------------------------------------------
# 1) HistoryPage.kt —— 清空浏览记录
# --------------------------------------------------------------------------
s = load("HistoryPage.kt")
s = sub(s, "import androidx.compose.material3.AlertDialog\n", IMPORTS, "HistoryPage.import")
s = drop_import(s, "import androidx.compose.material3.TextButton\n", "HistoryPage.TextButton")

s = sub(
    s,
    '    val repo = remember { HupuRepository() }\n'
    '    var query by remember { mutableStateOf("") }',
    '    val repo = remember { HupuRepository() }\n'
    '    // 1.191: 记录层——供 Liquid Glass 弹窗采样背后像素（挂在内容层上，弹窗在其后）\n'
    '    val backdrop = rememberLayerBackdrop()\n'
    '    var query by remember { mutableStateOf("") }',
    "HistoryPage.backdrop",
)

s = sub(
    s,
    "        Column(Modifier.fillMaxSize()) {\n"
    "            // 顶栏：与主页频道自定义页同款",
    "        Column(Modifier.fillMaxSize().layerBackdrop(backdrop)) {\n"
    "            // 顶栏：与主页频道自定义页同款",
    "HistoryPage.column",
)

old_tail = (
    '        }\n'
    '    }\n'
    '\n'
    '    // 清空确认弹窗（iOS 风格：标题/正文居左、取消浅灰、确认蓝色）\n'
    '    if (showClearDialog) {\n'
    '        AlertDialog(\n'
    '            onDismissRequest = { showClearDialog = false },\n'
    '            title = { Text("清空浏览记录") },\n'
    '            text = { Text("将删除全部 $clearDialogCount 条浏览记录，此操作不可恢复。") },\n'
    '            confirmButton = {\n'
    '                TextButton(onClick = {\n'
    '                    HupuPrefs.clearHistory()\n'
    '                    showClearDialog = false\n'
    '                }) { Text("清空", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) }\n'
    '            },\n'
    '            dismissButton = {\n'
    '                TextButton(onClick = { showClearDialog = false }) { Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant) }\n'
    '            },\n'
    '        )\n'
    '    }\n'
    '}\n'
)
new_tail = (
    '        }\n'
    '        // 1.191: 清空浏览记录确认弹窗（Liquid Glass 同款外观 + Q 弹出入场）\n'
    '        // 放在根 Box 内、内容层之后（兄弟顺序在记录层之后）——毛玻璃才能采到页面像素\n'
    '        if (showClearDialog) {\n'
    '            LiquidGlassDialog(\n'
    '                backdrop = backdrop,\n'
    '                title = "清空浏览记录",\n'
    '                message = "将删除全部 ${clearDialogCount} 条浏览记录，此操作不可恢复。",\n'
    '                confirmText = "清空",\n'
    '                dismissText = "取消",\n'
    '                onConfirm = { HupuPrefs.clearHistory() },\n'
    '                onDismiss = { showClearDialog = false },\n'
    '            )\n'
    '        }\n'
    '    }\n'
    '}\n'
)
s = sub(s, old_tail, new_tail, "HistoryPage.dialog")
save("HistoryPage.kt", s)

# --------------------------------------------------------------------------
# 2) SearchPage.kt —— 清空搜索历史（状态上提到页面级）
# --------------------------------------------------------------------------
s = load("SearchPage.kt")
s = sub(s, "import androidx.compose.material3.AlertDialog\n", IMPORTS, "SearchPage.import")
s = drop_import(s, "import androidx.compose.material3.TextButton\n", "SearchPage.TextButton")

s = sub(
    s,
    "    val repo = remember { HupuRepository() }\n"
    "    val scope = rememberCoroutineScope()\n",
    "    val repo = remember { HupuRepository() }\n"
    "    val scope = rememberCoroutineScope()\n"
    "    // 1.191: 记录层——供 Liquid Glass 弹窗采样背后像素（挂在内容层上，弹窗在其后）\n"
    "    val backdrop = rememberLayerBackdrop()\n",
    "SearchPage.backdrop",
)

s = sub(
    s,
    '    var searched by remember { mutableStateOf(false) }     // 是否至少搜过一次（区分初始态）\n',
    '    var searched by remember { mutableStateOf(false) }     // 是否至少搜过一次（区分初始态）\n'
    '    // 1.191: 「清空搜索历史」确认弹窗状态（从 HistoryPanel 上提到页面级——\n'
    '    // 弹窗必须画在页面根 Box 里内容层之后的兄弟位置，毛玻璃才采得到页面像素）\n'
    '    var clearHistoryAsk by remember { mutableStateOf(false) }\n'
    '    var clearHistoryCount by remember { mutableIntStateOf(0) }\n',
    "SearchPage.state",
)

s = sub(
    s,
    "    ) {\n"
    "        Column(Modifier.fillMaxSize()) {\n"
    "            SearchHeader(query, query.isNotBlank(), { query = it }, { submit(query) }, { closing = true })",
    "    ) {\n"
    "        Column(Modifier.fillMaxSize().layerBackdrop(backdrop)) {\n"
    "            SearchHeader(query, query.isNotBlank(), { query = it }, { submit(query) }, { closing = true })",
    "SearchPage.column",
)

s = sub(
    s,
    '                "history" -> HistoryPanel(\n'
    '                    onPick = { submit(it) },\n'
    '                    modifier = Modifier.fillMaxSize(),\n'
    '                )',
    '                "history" -> HistoryPanel(\n'
    '                    onPick = { submit(it) },\n'
    '                    onClearAsk = {\n'
    '                        clearHistoryCount = HupuPrefs.loadSearchHistory().size\n'
    '                        clearHistoryAsk = true\n'
    '                    },\n'
    '                    modifier = Modifier.fillMaxSize(),\n'
    '                )',
    "SearchPage.call",
)

s = sub(
    s,
    "        // 专区筛选：半屏弹出菜单（可上拉全屏）\n"
    "        if (forumPickerOpen) {\n"
    "            ForumPickerSheet(\n"
    "                categories = categories,\n"
    "                selectedTopicId = topicId,\n"
    "                onSelect = { changeForum(it); forumPickerOpen = false },\n"
    "                onDismiss = { forumPickerOpen = false },\n"
    "            )\n"
    "        }\n"
    "    }\n"
    "}",
    "        // 专区筛选：半屏弹出菜单（可上拉全屏）\n"
    "        if (forumPickerOpen) {\n"
    "            ForumPickerSheet(\n"
    "                categories = categories,\n"
    "                selectedTopicId = topicId,\n"
    "                onSelect = { changeForum(it); forumPickerOpen = false },\n"
    "                onDismiss = { forumPickerOpen = false },\n"
    "            )\n"
    "        }\n"
    "        // 1.191: 清空搜索历史确认弹窗（Liquid Glass 同款外观 + Q 弹出入场）\n"
    "        if (clearHistoryAsk) {\n"
    "            LiquidGlassDialog(\n"
    "                backdrop = backdrop,\n"
    "                title = \"清空搜索历史\",\n"
    "                message = \"将删除全部 ${clearHistoryCount} 条搜索历史，此操作不可恢复。\",\n"
    "                confirmText = \"清空\",\n"
    "                dismissText = \"取消\",\n"
    "                onConfirm = { HupuPrefs.clearSearchHistory() },\n"
    "                onDismiss = { clearHistoryAsk = false },\n"
    "            )\n"
    "        }\n"
    "    }\n"
    "}",
    "SearchPage.dialog",
)

s = sub(
    s,
    'private fun HistoryPanel(onPick: (String) -> Unit, modifier: Modifier = Modifier) {\n'
    '    var historyTick by remember { mutableIntStateOf(0) }\n'
    '    var clearAsk by remember { mutableStateOf(false) }\n',
    'private fun HistoryPanel(\n'
    '    onPick: (String) -> Unit,\n'
    '    modifier: Modifier = Modifier,\n'
    '    onClearAsk: () -> Unit,\n'
    ') {\n'
    '    var historyTick by remember { mutableIntStateOf(0) }\n',
    "SearchPage.historyPanelHead",
)

s = sub(
    s,
    "                IconButton(onClick = { clearAsk = true }) {\n"
    "                    Icon(Icons.Rounded.Delete, contentDescription = \"清空历史\", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))\n"
    "                }\n"
    "            }\n"
    "            if (clearAsk) {\n"
    "                AlertDialog(\n"
    "                    onDismissRequest = { clearAsk = false },\n"
    "                    title = { Text(\"清空搜索历史\") },\n"
    "                    text = { Text(\"将删除全部 ${history.size} 条搜索历史，此操作不可恢复。\") },\n"
    "                    confirmButton = {\n"
    "                        TextButton(onClick = { HupuPrefs.clearSearchHistory(); clearAsk = false }) {\n"
    "                            Text(\"清空\", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)\n"
    "                        }\n"
    "                    },\n"
    "                    dismissButton = {\n"
    "                        TextButton(onClick = { clearAsk = false }) { Text(\"取消\", color = MaterialTheme.colorScheme.onSurfaceVariant) }\n"
    "                    },\n"
    "                )\n"
    "            }\n"
    "            Spacer(Modifier.height(4.dp))",
    "                IconButton(onClick = onClearAsk) {\n"
    "                    Icon(Icons.Rounded.Delete, contentDescription = \"清空历史\", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))\n"
    "                }\n"
    "            }\n"
    "            Spacer(Modifier.height(4.dp))",
    "SearchPage.historyPanelBody",
)
save("SearchPage.kt", s)

# --------------------------------------------------------------------------
# 3) TopicPickerPage.kt —— 清空首页频道
# --------------------------------------------------------------------------
s = load("TopicPickerPage.kt")
s = sub(s, "import androidx.compose.material3.AlertDialog\n", IMPORTS, "TopicPickerPage.import")
s = drop_import(s, "import androidx.compose.material3.TextButton\n", "TopicPickerPage.TextButton")

s = sub(
    s,
    "    var resetAsk by remember { mutableStateOf(false) }\n"
    "    fun resetDefault() {\n"
    "        HupuPrefs.clearHomeTopics()",
    "    var resetAsk by remember { mutableStateOf(false) }\n"
    "    // 1.191: 记录层——供 Liquid Glass 弹窗采样背后像素（挂在内容层上，弹窗在其后）\n"
    "    val backdrop = rememberLayerBackdrop()\n"
    "    fun resetDefault() {\n"
    "        HupuPrefs.clearHomeTopics()",
    "TopicPickerPage.backdrop",
)

s = sub(
    s,
    "        Column(Modifier.fillMaxSize()) {\n"
    "            // 顶栏：返回 + 标题 + 计数 + 恢复默认",
    "        Column(Modifier.fillMaxSize().layerBackdrop(backdrop)) {\n"
    "            // 顶栏：返回 + 标题 + 计数 + 恢复默认",
    "TopicPickerPage.column",
)

s = sub(
    s,
    "            if (resetAsk) {\n"
    "                AlertDialog(\n"
    "                    onDismissRequest = { resetAsk = false },\n"
    "                    title = { Text(\"清空首页频道\") },\n"
    "                    text = { Text(\"将清除全部已选首页频道，主页恢复显示默认频道。此操作不可恢复。\") },\n"
    "                    confirmButton = {\n"
    "                        TextButton(onClick = { resetDefault(); resetAsk = false }) {\n"
    "                            Text(\"清空\", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)\n"
    "                        }\n"
    "                    },\n"
    "                    dismissButton = {\n"
    "                        TextButton(onClick = { resetAsk = false }) { Text(\"取消\", color = MaterialTheme.colorScheme.onSurfaceVariant) }\n"
    "                    },\n"
    "                )\n"
    "            }\n"
    "\n"
    "            when {",
    "            when {",
    "TopicPickerPage.removeInlineDialog",
)

s = sub(
    s,
    "                        items(filtered, key = { it.url }) { t ->\n"
    "                            val isSelected = selected.any { it.url == t.url }\n"
    "                            TopicPickRow(topic = t, isSelected = isSelected, onToggle = { toggle(t) })\n"
    "                        }\n"
    "                    }\n"
    "                }\n"
    "            }\n"
    "        }\n"
    "    }\n"
    "}",
    "                        items(filtered, key = { it.url }) { t ->\n"
    "                            val isSelected = selected.any { it.url == t.url }\n"
    "                            TopicPickRow(topic = t, isSelected = isSelected, onToggle = { toggle(t) })\n"
    "                        }\n"
    "                    }\n"
    "                }\n"
    "            }\n"
    "        }\n"
    "        // 1.191: 清空首页频道确认弹窗（Liquid Glass 同款外观 + Q 弹出入场）\n"
    "        // 放在根 Box 内、内容层之后（兄弟顺序在记录层之后）——毛玻璃才能采到页面像素\n"
    "        if (resetAsk) {\n"
    "            LiquidGlassDialog(\n"
    "                backdrop = backdrop,\n"
    "                title = \"清空首页频道\",\n"
    "                message = \"将清除全部已选首页频道，主页恢复显示默认频道。此操作不可恢复。\",\n"
    "                confirmText = \"清空\",\n"
    "                dismissText = \"取消\",\n"
    "                onConfirm = { resetDefault() },\n"
    "                onDismiss = { resetAsk = false },\n"
    "            )\n"
    "        }\n"
    "    }\n"
    "}",
    "TopicPickerPage.addDialog",
)
save("TopicPickerPage.kt", s)

# --------------------------------------------------------------------------
# 4) ScorePickerPage.kt —— 清空评分频道
# --------------------------------------------------------------------------
s = load("ScorePickerPage.kt")
s = sub(s, "import androidx.compose.material3.AlertDialog\n", IMPORTS, "ScorePickerPage.import")
s = drop_import(s, "import androidx.compose.material3.TextButton\n", "ScorePickerPage.TextButton")

s = sub(
    s,
    "    var resetAsk by remember { mutableStateOf(false) }\n"
    "    fun resetDefault() {\n"
    "        HupuPrefs.clearScoreGames()",
    "    var resetAsk by remember { mutableStateOf(false) }\n"
    "    // 1.191: 记录层——供 Liquid Glass 弹窗采样背后像素（挂在内容层上，弹窗在其后）\n"
    "    val backdrop = rememberLayerBackdrop()\n"
    "    fun resetDefault() {\n"
    "        HupuPrefs.clearScoreGames()",
    "ScorePickerPage.backdrop",
)

s = sub(
    s,
    "        Column(Modifier.fillMaxSize()) {\n"
    "            // 顶栏：返回 + 标题 + 计数 + 恢复默认",
    "        Column(Modifier.fillMaxSize().layerBackdrop(backdrop)) {\n"
    "            // 顶栏：返回 + 标题 + 计数 + 恢复默认",
    "ScorePickerPage.column",
)

s = sub(
    s,
    "            if (resetAsk) {\n"
    "                AlertDialog(\n"
    "                    onDismissRequest = { resetAsk = false },\n"
    "                    title = { Text(\"清空评分频道\") },\n"
    "                    text = { Text(\"将清除全部已选评分频道，评分页恢复显示全部项目。此操作不可恢复。\") },\n"
    "                    confirmButton = {\n"
    "                        TextButton(onClick = { resetDefault(); resetAsk = false }) {\n"
    "                            Text(\"清空\", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)\n"
    "                        }\n"
    "                    },\n"
    "                    dismissButton = {\n"
    "                        TextButton(onClick = { resetAsk = false }) { Text(\"取消\", color = MaterialTheme.colorScheme.onSurfaceVariant) }\n"
    "                    },\n"
    "                )\n"
    "            }\n"
    "            // 已选条（长按拖动排序 / 点按移除）",
    "            // 已选条（长按拖动排序 / 点按移除）",
    "ScorePickerPage.removeInlineDialog",
)

s = sub(
    s,
    "                // 底部留白（等价于旧网格 contentPadding 的 bottom = 140.dp）\n"
    "                Spacer(Modifier.height(140.dp))\n"
    "            }\n"
    "        }\n"
    "    }\n"
    "}",
    "                // 底部留白（等价于旧网格 contentPadding 的 bottom = 140.dp）\n"
    "                Spacer(Modifier.height(140.dp))\n"
    "            }\n"
    "        }\n"
    "        // 1.191: 清空评分频道确认弹窗（Liquid Glass 同款外观 + Q 弹出入场）\n"
    "        // 放在根 Box 内、内容层之后（兄弟顺序在记录层之后）——毛玻璃才能采到页面像素\n"
    "        if (resetAsk) {\n"
    "            LiquidGlassDialog(\n"
    "                backdrop = backdrop,\n"
    "                title = \"清空评分频道\",\n"
    "                message = \"将清除全部已选评分频道，评分页恢复显示全部项目。此操作不可恢复。\",\n"
    "                confirmText = \"清空\",\n"
    "                dismissText = \"取消\",\n"
    "                onConfirm = { resetDefault() },\n"
    "                onDismiss = { resetAsk = false },\n"
    "            )\n"
    "        }\n"
    "    }\n"
    "}",
    "ScorePickerPage.addDialog",
)
save("ScorePickerPage.kt", s)

# --------------------------------------------------------------------------
# 5) ScorePage.kt —— 删除收藏表情
# --------------------------------------------------------------------------
s = load("ScorePage.kt")
s = sub(
    s,
    "import androidx.compose.material3.MaterialTheme\n",
    "import androidx.compose.material3.MaterialTheme\n" + IMPORTS,
    "ScorePage.import",
)

s = sub(
    s,
    "    val repo = remember { HupuRepository() }\n"
    "    // 评分频道（即时生效）",
    "    val repo = remember { HupuRepository() }\n"
    "    // 1.191: 记录层——供 Liquid Glass 弹窗采样背后像素（挂在内容层上，弹窗在其后）\n"
    "    val backdrop = rememberLayerBackdrop()\n"
    "    // 评分频道（即时生效）",
    "ScorePage.backdrop",
)

s = sub(
    s,
    "                .tabSwipeSwitch(\n"
    "                    onPrevious = { swipeScoreTab(-1) },\n"
    "                    onNext = { swipeScoreTab(1) },\n"
    "                ),\n"
    "        ) {",
    "                .tabSwipeSwitch(\n"
    "                    onPrevious = { swipeScoreTab(-1) },\n"
    "                    onNext = { swipeScoreTab(1) },\n"
    "                )\n"
    "                // 1.191: 内容层挂记录层——弹窗画在根 Box 里本层之后的兄弟位置\n"
    "                .layerBackdrop(backdrop),\n"
    "        ) {",
    "ScorePage.column",
)

old_dialog = (
    "        // 1.146: 删除收藏表情确认弹窗（评分回复框「我的表情」长按触发）\n"
    "        scoreStickerToDelete?.let { st ->\n"
    "            androidx.compose.material3.AlertDialog(\n"
    "                onDismissRequest = { scoreStickerToDelete = null },\n"
    "                title = { androidx.compose.material3.Text(\"删除表情\") },\n"
    "                text = { androidx.compose.material3.Text(\"确定从「我的表情」中移除这个表情吗？\") },\n"
    "                confirmButton = {\n"
    "                    androidx.compose.material3.TextButton(onClick = {\n"
    "                        HupuPrefs.removeSticker(st.url)\n"
    "                        HupuPrefs.stickerToast = \"已删除\"\n"
    "                        scoreStickerToDelete = null\n"
    "                    }) { androidx.compose.material3.Text(\"删除\", color = MaterialTheme.colorScheme.error) }\n"
    "                },\n"
    "                dismissButton = {\n"
    "                    androidx.compose.material3.TextButton(onClick = { scoreStickerToDelete = null }) {\n"
    "                        androidx.compose.material3.Text(\"取消\")\n"
    "                    }\n"
    "                },\n"
    "            )\n"
    "        }\n"
)
new_dialog = (
    "        // 1.146: 删除收藏表情确认弹窗（评分回复框「我的表情」长按触发）\n"
    "        // 1.191: 换成 Liquid Glass 同款外观（Q 弹出入场）\n"
    "        scoreStickerToDelete?.let { st ->\n"
    "            LiquidGlassDialog(\n"
    "                backdrop = backdrop,\n"
    "                title = \"删除表情\",\n"
    "                message = \"确定从「我的表情」中移除这个表情吗？\",\n"
    "                confirmText = \"删除\",\n"
    "                dismissText = \"取消\",\n"
    "                onConfirm = {\n"
    "                    HupuPrefs.removeSticker(st.url)\n"
    "                    HupuPrefs.stickerToast = \"已删除\"\n"
    "                },\n"
    "                onDismiss = { scoreStickerToDelete = null },\n"
    "            )\n"
    "        }\n"
)
s = sub(s, old_dialog, new_dialog, "ScorePage.dialog")
save("ScorePage.kt", s)

print("ALL 5 FILES UPGRADED OK")
