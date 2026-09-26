#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""1.192 第十三批：给「仅 closing 出场、无 onDispose 兜底」的二级页加安全网
   问题：这些页 enter 在 LaunchedEffect(Unit)；若被「重挂载」（epoch 键变，如连点同一条目）
         → 旧实例销毁却不 exit → 计数泄漏 → Tab 栏永久隐藏。
   修法（对齐 HistoryPage 既有范式）：
     · 叶子页：保留 closing 提前回收（保 Tab 栏与页面同步滑出）+ 新增 onDispose 兜底
     · 嵌套页（AboutSubPage / EmbedWebPage）：改「纯 onDispose 回收」，去掉 closing 的 exit
   原子式：先全部校验命中数，再统一写盘。"""
import io

EDITS = {}
def P(path, old, new, expect):
    EDITS.setdefault(path, []).append((old, new, expect))

B = "app/src/main/java/com/java/myapplication/ui/pages/"
DISP = ("    // 1.192: 计数兜底——页面被任何路径销毁（如被重挂载）都会 onDispose 回收，防 Tab 栏计数泄漏\n"
        "    androidx.compose.runtime.DisposableEffect(Unit) {\n"
        "        onDispose { SecondaryPage.exit() }\n"
        "    }\n")
BLOCK = "    LaunchedEffect(Unit) {\n        SecondaryPage.enter()\n        progress.animateTo(1f, tween(280))\n    }\n"

# ---- 叶子页：插入兜底（保留 enter / closing-exit）----
for f in ["TopicPickerPage.kt", "SearchPage.kt", "ScorePickerPage.kt", "FilterSettingsPage.kt",
          "TextSizeSettingsPage.kt", "RefreshRateSettingsPage.kt", "ThemeSettingsPage.kt"]:
    P(B+f, BLOCK, BLOCK + DISP, 1)

# ---- LoginPage：enter 块含延迟挂载逻辑，锚点取块尾 ----
P(B+"LoginPage.kt", "        webMounted = true\n    }\n", "        webMounted = true\n    }\n" + DISP, 1)

# ---- EmbedWebPage：嵌套（在帖子详情之上）→ 加兜底 + 去掉 closing 的 exit ----
P(B+"EmbedWebPage.kt", BLOCK, BLOCK + DISP, 1)
P(B+"EmbedWebPage.kt",
  "    LaunchedEffect(closing) {\n        if (closing) {\n            SecondaryPage.exit()\n",
  "    LaunchedEffect(closing) {\n        if (closing) {\n", 1)

# ---------- 应用（普通文件）----------
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

# ---------- AboutPage.kt（两个函数分别处理）----------
ap = B + "AboutPage.kt"
with io.open(ap, encoding='utf-8') as f:
    s = f.read()
# AboutPage（叶子）：函数头 + BLOCK → 追加兜底
head = "fun AboutPage(onClose: () -> Unit) {\n    val context = LocalContext.current\n    val progress = remember { Animatable(0f) }\n" + BLOCK
assert s.count(head) == 1, "AboutPage head+block count=%d" % s.count(head)
s = s.replace(head, head + DISP)
# AboutSubPage（嵌套）：剩余唯一 BLOCK → 追加兜底
assert s.count(BLOCK) == 1, "AboutSubPage block count=%d" % s.count(BLOCK)
s = s.replace(BLOCK, BLOCK + DISP, 1)
# AboutSubPage：去掉其 closing 里的 exit（用函数头定位，保证只改它）
marker = "private fun AboutSubPage(kind: AboutSub, context: Context, onClose: () -> Unit) {"
idx = s.index(marker)
head_s, tail = s[:idx], s[idx:]
old_exit = "        if (closing) {\n            SecondaryPage.exit()\n"
assert tail.count(old_exit) >= 1, "AboutSubPage closing exit not found"
tail = tail.replace(old_exit, "        if (closing) {\n", 1)
s = head_s + tail
with io.open(ap, 'w', encoding='utf-8') as f:
    f.write(s)
total += 1
print("OK %s (AboutPage + AboutSubPage)" % ap)
print("FIX11 1.192 OK, %d replacements" % total)