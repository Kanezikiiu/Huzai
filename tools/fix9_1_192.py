#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""1.192 第十批：修复「连点多个条目 → 返回后底部 Tab 栏永久消失」
   根因：Home/Zone/Score 三个页面的 openX() 里手动 SecondaryPage.enter()，
        但每个 overlay 只有一份；连点 N 次 → enter N 次、只 exit 一次 → 计数泄漏。
   修法：enter/exit 与 overlay 的组合生命周期绑定（DisposableEffect，key = 打开的对象）：
        · 连点只会让「同一 overlay 槽位」重建，计数进出成对；
        · overlay 被任何路径移除（含退场中被替换/打断）都会 onDispose → exit（安全网）。
        保留 onExitStart 的提前归位（Tab 栏与页面同步滑出），exit 自带 count>0 守卫，双 exit 无害。
   原子式：先全部校验命中数，再统一写盘。"""
import io

EDITS = {}

def patch(path, old, new, expect):
    EDITS.setdefault(path, []).append((old, new, expect))

B = "app/src/main/java/com/java/myapplication/ui/pages/"
HP, ZP, SP = B + "HomePage.kt", B + "ZonePage.kt", B + "ScorePage.kt"

_CMT = "            // 1.192: 二级页计数与 overlay 组合生命周期绑定——连点多个条目也只 enter 一次\n"
_CMT4 = "        // 1.192: 二级页计数与 overlay 组合生命周期绑定——连点多个条目也只 enter 一次\n"

# ================= HomePage =================
patch(HP,
      "        threadClosing = false\n        SecondaryPage.enter()\n        openedThread = t\n",
      "        threadClosing = false\n        openedThread = t\n", 1)
patch(HP,
      "    val ot = openedThread\n    if (ot != null) {\n"
      "        val d = threadDetails[ot.tid]\n        ThreadDetailOverlay(\n",
      "    val ot = openedThread\n    if (ot != null) {\n"
      + _CMT4 +
      "        androidx.compose.runtime.DisposableEffect(ot) {\n"
      "            SecondaryPage.enter()\n"
      "            onDispose { SecondaryPage.exit() }\n"
      "        }\n"
      "        val d = threadDetails[ot.tid]\n        ThreadDetailOverlay(\n", 1)

# ================= ZonePage =================
patch(ZP,
      "        selectedSort = null\n        overlayClosing = false\n        SecondaryPage.enter()\n        openedTopic = t\n",
      "        selectedSort = null\n        overlayClosing = false\n        openedTopic = t\n", 1)
patch(ZP,
      "        threadClosing = false\n        SecondaryPage.enter()\n        openedThread = t\n",
      "        threadClosing = false\n        openedThread = t\n", 1)
patch(ZP,
      "        if (opened != null) {\n            TopicFeedOverlay(\n",
      "        if (opened != null) {\n"
      + _CMT +
      "            androidx.compose.runtime.DisposableEffect(opened) {\n"
      "                SecondaryPage.enter()\n"
      "                onDispose { SecondaryPage.exit() }\n"
      "            }\n"
      "            TopicFeedOverlay(\n", 1)
patch(ZP,
      "        val ot = openedThread\n        if (ot != null) {\n"
      "            val d = threadDetails[ot.tid]\n            ThreadDetailOverlay(\n",
      "        val ot = openedThread\n        if (ot != null) {\n"
      + _CMT +
      "            androidx.compose.runtime.DisposableEffect(ot) {\n"
      "                SecondaryPage.enter()\n"
      "                onDispose { SecondaryPage.exit() }\n"
      "            }\n"
      "            val d = threadDetails[ot.tid]\n            ThreadDetailOverlay(\n", 1)

# ================= ScorePage =================
patch(SP,
      "        detailClosing = false\n        SecondaryPage.enter()\n        openedMatch = m\n",
      "        detailClosing = false\n        openedMatch = m\n", 1)
patch(SP,
      "        playerClosing = false\n        SecondaryPage.enter()\n",
      "        playerClosing = false\n", 1)
patch(SP,
      "        commonClosing = false\n        SecondaryPage.enter()\n        openedCommon = s\n",
      "        commonClosing = false\n        openedCommon = s\n", 1)
patch(SP,
      "        val om = openedMatch\n        if (om != null) {\n"
      "            val key = \"${om.scoreBizType}-${om.scoreBizNo}\"\n",
      "        val om = openedMatch\n        if (om != null) {\n"
      + _CMT +
      "            androidx.compose.runtime.DisposableEffect(om) {\n"
      "                SecondaryPage.enter()\n"
      "                onDispose { SecondaryPage.exit() }\n"
      "            }\n"
      "            val key = \"${om.scoreBizType}-${om.scoreBizNo}\"\n", 1)
patch(SP,
      "        val oc = openedCommon\n        if (oc != null) {\n"
      "            val cKey = \"${oc.bizType}-${oc.bizNo}\"\n",
      "        val oc = openedCommon\n        if (oc != null) {\n"
      + _CMT +
      "            androidx.compose.runtime.DisposableEffect(oc) {\n"
      "                SecondaryPage.enter()\n"
      "                onDispose { SecondaryPage.exit() }\n"
      "            }\n"
      "            val cKey = \"${oc.bizType}-${oc.bizNo}\"\n", 1)
patch(SP,
      "        val op = openedPlayer\n        if (op != null) {\n"
      "            val pKey = \"${op.bizType}-${op.bizId}\"\n",
      "        val op = openedPlayer\n        if (op != null) {\n"
      + _CMT +
      "            androidx.compose.runtime.DisposableEffect(op) {\n"
      "                SecondaryPage.enter()\n"
      "                onDispose { SecondaryPage.exit() }\n"
      "            }\n"
      "            val pKey = \"${op.bizType}-${op.bizId}\"\n", 1)

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
print("FIX9 1.192 OK, %d replacements" % total)