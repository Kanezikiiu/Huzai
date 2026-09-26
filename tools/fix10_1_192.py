#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""1.192 第十二批：纠正/扩展二级页计数泄漏修复（flag 门控）
   上一批 DisposableEffect(key){enter;onDispose{exit}} 在嵌套 overlay 下会与
   onExitStart 的 exit 叠加 → 多减一次 → Tab 栏提前回归（反向 bug）。
   本批统一改为「flag 门控」：
     openX():     if (!flag) { flag = true; SecondaryPage.enter() }
     onExitStart: if (flag) { flag = false; SecondaryPage.exit() }   ← 提前回收（保手感）
     兜底:         DisposableEffect(Unit) { onDispose { if (flag) { flag=false; exit() } } }
   原子式：先全部校验命中数，再统一写盘。"""
import io

EDITS = {}
def P(path, old, new, expect):
    EDITS.setdefault(path, []).append((old, new, expect))

B = "app/src/main/java/com/java/myapplication/ui/pages/"
HP, ZP, SP, HS, UP = B+"HomePage.kt", B+"ZonePage.kt", B+"ScorePage.kt", B+"HistoryPage.kt", B+"UserProfilePage.kt"
CMT = "// 1.192: 二级页计数与 overlay 组合生命周期绑定——连点多个条目也只 enter 一次"

def decl(path, old, flag):
    P(path, old, old + "    // 1.192: 本层「已计数」标记——连点多个条目时 enter 只发生一次，防 Tab 栏计数泄漏\n"
                        + "    var %s by remember { mutableStateOf(false) }\n" % flag, 1)

def defx(path, key, flag, ind):
    old = ind + CMT + "\n" \
        + ind + "androidx.compose.runtime.DisposableEffect(%s) {\n" % key \
        + ind + "    SecondaryPage.enter()\n" \
        + ind + "    onDispose { SecondaryPage.exit() }\n" \
        + ind + "}\n"
    new = ind + "// 1.192: 本层离开组合时兜底回收计数（正常退场已由 onExitStart 提前回收）\n" \
        + ind + "androidx.compose.runtime.DisposableEffect(Unit) {\n" \
        + ind + "    onDispose { if (%s) { %s = false; SecondaryPage.exit() } }\n" % (flag, flag) \
        + ind + "}\n"
    P(path, old, new, 1)

def exit_start(path, back_line, flag, ind):
    old = back_line + ind + "onExitStart = { SecondaryPage.exit() },\n"
    new = back_line + ind + "onExitStart = { if (%s) { %s = false; SecondaryPage.exit() } },\n" % (flag, flag)
    P(path, old, new, 1)

# ================= HomePage =================
decl(HP, "    var openedThread by remember { mutableStateOf<HupuThread?>(null) }\n", "threadEntered")
P(HP, "        threadClosing = false\n        openedThread = t\n",
      "        threadClosing = false\n        if (!threadEntered) { threadEntered = true; SecondaryPage.enter() }\n        openedThread = t\n", 1)
defx(HP, "ot", "threadEntered", "        ")
exit_start(HP, "            onBack = { closeThread() },\n", "threadEntered", "            ")

# ================= ZonePage =================
decl(ZP, "    var openedTopic by remember { mutableStateOf<HupuTopicInfo?>(null) }\n", "topicEntered")
decl(ZP, "    var openedThread by remember { mutableStateOf<HupuThread?>(null) }\n", "threadEntered")
P(ZP, "        selectedSort = null\n        overlayClosing = false\n        openedTopic = t\n",
      "        selectedSort = null\n        overlayClosing = false\n        if (!topicEntered) { topicEntered = true; SecondaryPage.enter() }\n        openedTopic = t\n", 1)
P(ZP, "        threadClosing = false\n        openedThread = t\n",
      "        threadClosing = false\n        if (!threadEntered) { threadEntered = true; SecondaryPage.enter() }\n        openedThread = t\n", 1)
defx(ZP, "opened", "topicEntered", "            ")
defx(ZP, "ot", "threadEntered", "            ")
exit_start(ZP, "                onBack = { closeTopic() },\n", "topicEntered", "                ")
exit_start(ZP, "                onBack = { closeThread() },\n", "threadEntered", "                ")

# ================= ScorePage =================
decl(SP, "    var openedMatch by remember { mutableStateOf<HupuMatch?>(null) }\n", "matchEntered")
decl(SP, "    var openedPlayer by remember { mutableStateOf<HupuScoreItem?>(null) }\n", "playerEntered")
decl(SP, "    var openedCommon by remember { mutableStateOf<HupuCommonSubject?>(null) }\n", "commonEntered")
P(SP, "        detailClosing = false\n        openedMatch = m\n",
      "        detailClosing = false\n        if (!matchEntered) { matchEntered = true; SecondaryPage.enter() }\n        openedMatch = m\n", 1)
P(SP, "        val key = \"${p.bizType}-${p.bizId}\"\n        playerClosing = false\n",
      "        val key = \"${p.bizType}-${p.bizId}\"\n        playerClosing = false\n        if (!playerEntered) { playerEntered = true; SecondaryPage.enter() }\n", 1)
P(SP, "        commonClosing = false\n        openedCommon = s\n",
      "        commonClosing = false\n        if (!commonEntered) { commonEntered = true; SecondaryPage.enter() }\n        openedCommon = s\n", 1)
defx(SP, "om", "matchEntered", "            ")
defx(SP, "oc", "commonEntered", "            ")
defx(SP, "op", "playerEntered", "            ")
exit_start(SP, "                onBack = { closeMatch() },\n", "matchEntered", "                ")
exit_start(SP, "                onBack = { closeCommon() },\n", "commonEntered", "                ")
exit_start(SP, "                onBack = { closePlayer() },\n", "playerEntered", "                ")

# ================= HistoryPage =================
decl(HS, "    var openedThread by remember { mutableStateOf<HupuThread?>(null) }\n", "histThreadEntered")
P(HS, "        threadClosing = false\n        SecondaryPage.enter()\n",
      "        threadClosing = false\n        if (!histThreadEntered) { histThreadEntered = true; SecondaryPage.enter() }\n", 1)
P(HS, "        val ot = openedThread\n        if (ot != null) {\n",
      "        val ot = openedThread\n        if (ot != null) {\n"
      "            // 1.192: 本层离开组合时兜底回收计数（正常退场已由 onExitStart 提前回收）\n"
      "            androidx.compose.runtime.DisposableEffect(Unit) {\n"
      "                onDispose { if (histThreadEntered) { histThreadEntered = false; SecondaryPage.exit() } }\n"
      "            }\n", 1)
exit_start(HS, "                onBack = { closeDetail() },\n", "histThreadEntered", "                ")

# ================= UserProfilePage =================
decl(UP, "    var openedThread by remember { mutableStateOf<HupuThread?>(null) }\n", "upThreadEntered")
P(UP, "        threadClosing = false\n        SecondaryPage.enter()\n",
      "        threadClosing = false\n        if (!upThreadEntered) { upThreadEntered = true; SecondaryPage.enter() }\n", 1)
P(UP, "        val ot = openedThread\n        if (ot != null) {\n",
      "        val ot = openedThread\n        if (ot != null) {\n"
      "            // 1.192: 本层离开组合时兜底回收计数（正常退场已由 onExitStart 提前回收）\n"
      "            androidx.compose.runtime.DisposableEffect(Unit) {\n"
      "                onDispose { if (upThreadEntered) { upThreadEntered = false; SecondaryPage.exit() } }\n"
      "            }\n", 1)
exit_start(UP, "                onBack = { closeDetail() },\n", "upThreadEntered", "                ")

# ---------- 原子应用 ----------
total = 0
for path, edits in EDITS.items():
    with io.open(path, encoding='utf-8') as f:
        s = f.read()
    for old, new, expect in edits:
        n = s.count(old)
        assert n == expect, "%s: expect %d of %r, got %d" % (path, expect, old[:90], n)
        s = s.replace(old, new)
        total += n
    with io.open(path, 'w', encoding='utf-8') as f:
        f.write(s)
    print("OK %s (%d edits)" % (path, len(edits)))
print("FIX10 1.192 OK, %d replacements" % total)