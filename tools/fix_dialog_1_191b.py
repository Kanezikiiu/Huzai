#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""1.191 修正批：弹窗透明度统一 / 层级压过表情面板 / 平板限宽。

1) 记录层统一铺一层不透明页面底色（对齐 ThreadDetailPage 的 actionBackdrop），
   否则记录层是透明的 → 卡片看起来「非常透明、眼花缭乱」；
   同时把卡片底色 alpha 从 0.6 降到 0.5（深色 0.4 → 0.34）——比原来更透一点点。
2) LiquidGlassCard/Dialog 默认 zIndex 40 → 1100（全局最高值是表情输入行 1000），
   保证弹窗永远盖在表情面板之上。
3) 卡片加 widthIn(max = 400.dp)，平板不再铺满整屏。
"""
import io
import os

ROOT = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "..", "app", "src", "main", "java", "com", "java", "myapplication",
)
PAGES = os.path.join(ROOT, "ui", "pages")
COMPONENTS = os.path.join(ROOT, "ui", "components")
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
# 1) GlassDialog.kt —— 透明度微调 + 限宽 + zIndex 抬到最高
# ==========================================================================
s = load(GLASS)
s = sub(
    s,
    "import androidx.compose.foundation.layout.padding\n",
    "import androidx.compose.foundation.layout.padding\nimport androidx.compose.foundation.layout.widthIn\n",
    "GlassDialog.import.widthIn",
)

# 卡片底色：比 ThreadDetailPage 现状再透一点点
s = sub(
    s,
    "    val containerColor =\n"
    "        if (isLight) Color(0xFFFAFAFA).copy(0.6f)\n"
    "        else Color(0xFF121212).copy(0.4f)\n"
    "    val dimColor =\n",
    "    val containerColor =\n"
    "        if (isLight) Color(0xFFFAFAFA).copy(0.5f)\n"
    "        else Color(0xFF121212).copy(0.34f)\n"
    "    val dimColor =\n",
    "GlassDialog.containerColor",
)

# 默认层级：全局最高的兄弟节点是表情输入行 zIndex 1000
s = sub(s, "    zIndex: Float = 40f,\n    onDismiss: () -> Unit,",
        "    zIndex: Float = 1100f,\n    onDismiss: () -> Unit,",
        "GlassDialog.card.zIndex")
s = sub(s, "    modifier: Modifier = Modifier,\n    zIndex: Float = 40f,\n)",
        "    modifier: Modifier = Modifier,\n    zIndex: Float = 1100f,\n)",
        "GlassDialog.dialog.zIndex")

# 限宽：手机上可用宽度本来就小于 400dp，行为不变；平板上一律收到 400dp
s = sub(
    s,
    "                .padding(horizontal = 40f.dp, vertical = 24f.dp)\n"
    "                .graphicsLayer {\n",
    "                .padding(horizontal = 40f.dp, vertical = 24f.dp)\n"
    "                .graphicsLayer {\n",
    "GlassDialog.noop",
)
s = sub(
    s,
    "                .drawBackdrop(\n"
    "                    backdrop = backdrop,\n"
    "                    shape = { RoundedRectangle(48f.dp) },",
    "                // 1.191: 平板/横屏下限制卡片宽度（手机上可用宽度本就 < 400dp，无影响）\n"
    "                .widthIn(max = 400f.dp)\n"
    "                .drawBackdrop(\n"
    "                    backdrop = backdrop,\n"
    "                    shape = { RoundedRectangle(48f.dp) },",
    "GlassDialog.widthIn",
)
save(GLASS, s)

# ==========================================================================
# 2) ThreadDetailPage.kt —— 弹窗压过表情面板（zIndex 40 → 1100）
# ==========================================================================
p = os.path.join(PAGES, "ThreadDetailPage.kt")
s = load(p)
s = sub(s, "                zIndex = 40f,\n", "                zIndex = 1100f,\n",
        "ThreadDetailPage.zIndex", n_expect=2)
save(p, s)

# ==========================================================================
# 3) 记录层统一铺不透明页面底色
#    页面：HistoryPage / SearchPage / TopicPickerPage / ScorePickerPage /
#          ScorePage / ProfilePage / AboutPage；组件：StickerSearch(sheet)
# ==========================================================================
TARGETS = [
    os.path.join(PAGES, "HistoryPage.kt"),
    os.path.join(PAGES, "SearchPage.kt"),
    os.path.join(PAGES, "TopicPickerPage.kt"),
    os.path.join(PAGES, "ScorePickerPage.kt"),
    os.path.join(PAGES, "ScorePage.kt"),
    os.path.join(PAGES, "ProfilePage.kt"),
    os.path.join(PAGES, "AboutPage.kt"),
    os.path.join(COMPONENTS, "StickerSearch.kt"),
]

for p in TARGETS:
    name = os.path.basename(p)
    s = load(p)
    assert "dialogBg" not in s, name
    if name == "StickerSearch.kt":
        old = "    val sheetBackdrop = rememberLayerBackdrop()\n"
        new = (
            "    // 1.191: 先铺一层不透明页面底色再画内容——记录层若是透明的，\n"
            "    // 卡片就只剩半透明白浮在页面上，视觉上会「花」\n"
            "    val dialogBg = MaterialTheme.colorScheme.background\n"
            "    val sheetBackdrop = rememberLayerBackdrop {\n"
            "        drawRect(dialogBg)\n"
            "        drawContent()\n"
            "    }\n"
        )
        tag = "StickerSearch.backdrop"
    else:
        old = "    val backdrop = rememberLayerBackdrop()\n"
        new = (
            "    // 1.191: 先铺一层不透明页面底色再画内容——与 ThreadDetailPage 的\n"
            "    // actionBackdrop 同一套做法。记录层若透明，卡片会显得「非常透明」\n"
            "    val dialogBg = MaterialTheme.colorScheme.background\n"
            "    val backdrop = rememberLayerBackdrop {\n"
            "        drawRect(dialogBg)\n"
            "        drawContent()\n"
            "    }\n"
        )
        tag = name + ".backdrop"
    s = sub(s, old, new, tag)
    save(p, s)

print("FIX BATCH OK")