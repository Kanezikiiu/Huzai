#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""1.191 修正批 2：
1) LiquidGlassDialog 标题左内边距 28 → 24，与正文/按钮左缘对齐；
2) 评分打分面板 ScorePanelOverlay 限宽 400dp（平板）；
3) 全局 OutlinedTextField 换成「无边框 + 柔和填充」配色（huzaiFieldColors）。
"""
import io
import os
import re

ROOT = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "..", "app", "src", "main", "java", "com", "java", "myapplication",
)
PAGES = os.path.join(ROOT, "ui", "pages")
GLASS = os.path.join(ROOT, "ui", "glass", "GlassDialog.kt")


def load(p):
    with io.open(p, encoding="utf-8") as f:
        return f.read()


def save(p, s):
    with io.open(p, "w", encoding="utf-8") as f:
        f.write(s)


def sub(s, old, new, where, n_expect=1):
    n = s.count(old)
    assert n == n_expect, "命中 %d 次（期望 %d）: %s :: %r" % (n, n_expect, where, old[:80])
    return s.replace(old, new)


# ==========================================================================
# 1) GlassDialog.kt —— 标题与正文左缘对齐（28 → 24）
# ==========================================================================
s = load(GLASS)
s = sub(
    s,
    "            Modifier.padding(28f.dp, 24f.dp, 28f.dp, 12f.dp),\n",
    "            // 1.191: 左内边距与正文/按钮统一 24dp —— 之前 28dp 会让标题比正文右缩 4dp\n"
    "            Modifier.padding(24f.dp, 24f.dp, 24f.dp, 12f.dp),\n",
    "GlassDialog.titlePadding",
)
save(GLASS, s)

# ==========================================================================
# 2) PlayerDetailScore.kt —— 打分面板限宽
# ==========================================================================
p = os.path.join(PAGES, "PlayerDetailScore.kt")
s = load(p)
if "import androidx.compose.foundation.layout.widthIn\n" not in s:
    n = s.count("import androidx.compose.foundation.layout.padding\n")
    assert n == 1, "PlayerDetailScore padding import: %d" % n
    s = s.replace(
        "import androidx.compose.foundation.layout.padding\n",
        "import androidx.compose.foundation.layout.padding\nimport androidx.compose.foundation.layout.widthIn\n",
    )
s = sub(
    s,
    "                .align(Alignment.Center)\n"
    "                .padding(horizontal = 40.dp)\n"
    "                .fillMaxWidth()\n"
    "                .clip(RoundedCornerShape(24.dp))\n",
    "                .align(Alignment.Center)\n"
    "                .padding(horizontal = 40.dp)\n"
    "                // 1.191: 平板/横屏下限制卡片宽度（手机上可用宽度本就 < 400dp，无影响）\n"
    "                .widthIn(max = 400.dp)\n"
    "                .fillMaxWidth()\n"
    "                .clip(RoundedCornerShape(24.dp))\n",
    "ScorePanelOverlay.widthIn",
)
save(p, s)

# ==========================================================================
# 3) 全局 OutlinedTextField 配色统一
# ==========================================================================
TARGETS = [
    "SearchPage.kt",
    "HistoryPage.kt",
    "TopicPickerPage.kt",
    "FilterSettingsPage.kt",
    "NewPostPage.kt",
    "PmPage.kt",
    "NewPostTopicPicker.kt",
    "NewPostVote.kt",
]
CALL = re.compile(r"OutlinedTextField\(\n([ \t]*)")
IMPORT_ANCHOR = "import androidx.compose.material3.OutlinedTextField\n"
NEW_IMPORT = "import com.java.myapplication.ui.components.huzaiFieldColors\n"

total = 0
for name in TARGETS:
    p = os.path.join(PAGES, name)
    s = load(p)
    assert IMPORT_ANCHOR in s, name + " 缺少 OutlinedTextField import"
    if NEW_IMPORT not in s:
        s = s.replace(IMPORT_ANCHOR, IMPORT_ANCHOR + NEW_IMPORT, 1)

    def repl(m):
        global total
        total += 1
        return "OutlinedTextField(\n%scolors = huzaiFieldColors(),\n%s" % (m.group(1), m.group(1))

    s, n = CALL.subn(repl, s)
    assert n >= 1, name + " 没有匹配到 OutlinedTextField 调用"
    save(p, s)
    print("%-26s %d 处" % (name, n))

print("TOTAL %d 处；FIX2 OK" % total)