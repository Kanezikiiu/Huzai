#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""1.192 第十四批：
   Part A：纠正上一批（t12）在「我的」9 个二级页引入的回归——
           无条件 onDispose + closing 里的 exit 会在【两个二级页叠加】时连减两次，
           导致「顶层关闭后、下层还在」时 Tab 栏提前冒出。
           改为 flag 门控（open 记一次、closing 提前回收、onDispose 兜底，三者互斥）。
   Part B：「我的」页二级页互斥——连点多个条目只打开最先的一个（对齐首页/专区/评分「只开一个」）。
   原子式：先全部校验命中数，再统一写盘。"""
import io

EDITS = {}
def P(path, old, new, expect):
    EDITS.setdefault(path, []).append((old, new, expect))

B = "app/src/main/java/com/java/myapplication/ui/pages/"

# ---------------- Part A：9 个二级页改 flag 门控 ----------------
OLD_TOP = ("    val progress = remember { Animatable(0f) }\n"
           "    LaunchedEffect(Unit) {\n"
           "        SecondaryPage.enter()\n"
           "        progress.animateTo(1f, tween(280))\n"
           "    }\n"
           "    // 1.192: 计数兜底——页面被任何路径销毁（如被重挂载）都会 onDispose 回收，防 Tab 栏计数泄漏\n"
           "    androidx.compose.runtime.DisposableEffect(Unit) {\n"
           "        onDispose { SecondaryPage.exit() }\n"
           "    }\n")
NEW_TOP = ("    val progress = remember { Animatable(0f) }\n"
           "    // 1.192: 计数 flag 门控——多页叠加 / 重挂载时不会多减，离开组合时兜底回收\n"
           "    var pageEntered by remember { mutableStateOf(false) }\n"
           "    LaunchedEffect(Unit) {\n"
           "        pageEntered = true\n"
           "        SecondaryPage.enter()\n"
           "        progress.animateTo(1f, tween(280))\n"
           "    }\n"
           "    androidx.compose.runtime.DisposableEffect(Unit) {\n"
           "        onDispose { if (pageEntered) { pageEntered = false; SecondaryPage.exit() } }\n"
           "    }\n")
OLD_CLOSE = ("        if (closing) {\n"
             "            SecondaryPage.exit()\n"
             "            progress.animateTo(0f, tween(280))\n")
NEW_CLOSE = ("        if (closing) {\n"
             "            if (pageEntered) { pageEntered = false; SecondaryPage.exit() }\n"
             "            progress.animateTo(0f, tween(280))\n")

for f in ["TopicPickerPage.kt", "SearchPage.kt", "ScorePickerPage.kt", "FilterSettingsPage.kt",
          "TextSizeSettingsPage.kt", "RefreshRateSettingsPage.kt", "ThemeSettingsPage.kt"]:
    P(B+f, OLD_TOP, NEW_TOP, 1)
    P(B+f, OLD_CLOSE, NEW_CLOSE, 1)

# AboutPage.kt：仅 AboutPage（叶子）——用 LocalContext 行消歧（AboutSubPage 无此行）
P(B+"AboutPage.kt", "    val context = LocalContext.current\n" + OLD_TOP,
  "    val context = LocalContext.current\n" + NEW_TOP, 1)
P(B+"AboutPage.kt", OLD_CLOSE, NEW_CLOSE, 1)   # AboutSubPage 已无 closing-exit，唯一命中 AboutPage

# LoginPage：enter 块特殊
P(B+"LoginPage.kt",
  "    androidx.compose.runtime.LaunchedEffect(Unit) {\n        SecondaryPage.enter()\n",
  "    // 1.192: 计数 flag 门控——多页叠加 / 重挂载时不会多减，离开组合时兜底回收\n"
  "    var pageEntered by remember { mutableStateOf(false) }\n"
  "    androidx.compose.runtime.LaunchedEffect(Unit) {\n        pageEntered = true\n        SecondaryPage.enter()\n", 1)
P(B+"LoginPage.kt",
  "    // 1.192: 计数兜底——页面被任何路径销毁（如被重挂载）都会 onDispose 回收，防 Tab 栏计数泄漏\n"
  "    androidx.compose.runtime.DisposableEffect(Unit) {\n"
  "        onDispose { SecondaryPage.exit() }\n"
  "    }\n",
  "    androidx.compose.runtime.DisposableEffect(Unit) {\n"
  "        onDispose { if (pageEntered) { pageEntered = false; SecondaryPage.exit() } }\n"
  "    }\n", 1)
P(B+"LoginPage.kt",
  "        if (closing) {\n            SecondaryPage.exit()\n            progress.animateTo(0f, androidx.compose.animation.core.tween(280))\n",
  "        if (closing) {\n            if (pageEntered) { pageEntered = false; SecondaryPage.exit() }\n            progress.animateTo(0f, androidx.compose.animation.core.tween(280))\n", 1)

# ---------------- Part B：Profile 页二级页互斥 ----------------
PP = B + "ProfilePage.kt"
P(PP, "    var postEpoch by remember { mutableIntStateOf(0) }\n",
  "    var postEpoch by remember { mutableIntStateOf(0) }\n"
  "    // 1.192: 二级页互斥——同一时刻只打开一个「我的」二级页（连点多个条目只保留最先的），\n"
  "    // 避免多页叠加：顶层关闭后下层还在、Tab 栏却提前冒出\n"
  "    fun profilePageOpen(): Boolean =\n"
  "        showPicker || loginEpoch > 0 || postEpoch > 0 || msgEpoch > 0 ||\n"
  "            historyEpoch > 0 || scorePickerEpoch > 0 || filterEpoch > 0 ||\n"
  "            textSizeEpoch > 0 || refreshEpoch > 0 || themeEpoch > 0 ||\n"
  "            aboutEpoch > 0 || userPageStack.isNotEmpty()\n", 1)
P(PP, "                        else postEpoch++\n",
      "                        else if (!profilePageOpen()) postEpoch++\n", 1)
P(PP, "                    IconButton(onClick = { msgEpoch++ }) {\n",
      "                    IconButton(onClick = { if (!profilePageOpen()) msgEpoch++ }) {\n", 1)
P(PP, "                        .clickable {\n                            if (prof == null) {\n",
      "                        .clickable {\n"
      "                            if (profilePageOpen()) return@clickable\n"
      "                            if (prof == null) {\n", 1)
P(PP, "                        onClick = { showPicker = true },\n",
      "                        onClick = { if (!profilePageOpen()) showPicker = true },\n", 1)
P(PP, "                        onClick = { scorePickerEpoch++ },\n",
      "                        onClick = { if (!profilePageOpen()) scorePickerEpoch++ },\n", 1)
P(PP, "                        onClick = { historyEpoch++ },\n",
      "                        onClick = { if (!profilePageOpen()) historyEpoch++ },\n", 1)
P(PP, "                        onClick = { filterEpoch++ },\n",
      "                        onClick = { if (!profilePageOpen()) filterEpoch++ },\n", 1)
P(PP, "                        onClick = { textSizeEpoch++ },\n",
      "                        onClick = { if (!profilePageOpen()) textSizeEpoch++ },\n", 1)
P(PP, "                        onClick = { refreshEpoch++ },\n",
      "                        onClick = { if (!profilePageOpen()) refreshEpoch++ },\n", 1)
P(PP, "                        onClick = { themeEpoch++ },\n",
      "                        onClick = { if (!profilePageOpen()) themeEpoch++ },\n", 1)
P(PP, "                        onClick = { aboutEpoch++ },\n",
      "                        onClick = { if (!profilePageOpen()) aboutEpoch++ },\n", 1)

# ---------- 原子应用 ----------
total = 0
for path, edits in EDITS.items():
    with io.open(path, encoding='utf-8') as f:
        s = f.read()
    for old, new, expect in edits:
        n = s.count(old)
        assert n == expect, "%s: expect %d of %r, got %d" % (path, expect, old[:80], n)
        s = s.replace(old, new)
        total += n
    with io.open(path, 'w', encoding='utf-8') as f:
        f.write(s)
    print("OK %s (%d edits)" % (path, len(edits)))
print("FIX12 1.192 OK, %d replacements" % total)