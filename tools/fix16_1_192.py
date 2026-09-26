#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
1.192 第十六批（真机反馈：按钮材质升级，用 kyant LiquidButton）

#A 用户主页：私信按钮 / 关注按钮 → LiquidButton；下方 TabChip(4~5) → 玻璃胶囊
#B 搜索页：搜索历史条目 → 玻璃胶囊
#C 帖子详情：底部操作条「写评论」→ LiquidButton；4 个图标项 → kyant 流体按压高亮
（注意：部分页面源码里中文字面量是 \\uXXXX 转义形式，故一律只替换「不含中文」的
 modifier 链 / 函数签名段，避免匹配失败。）
"""
import io, re, sys

W = "/data/user/0/com.ai.assistance.operit/files/workspace/2dab7fe4-ceff-4d94-99a0-8e839ec2f3da"
P = W + "/app/src/main/java/com/java/myapplication/ui/pages/"
USER = P + "UserProfilePage.kt"
SEARCH = P + "SearchPage.kt"
ACTION = P + "ThreadActionBar.kt"


def read(p):
    with io.open(p, "r", encoding="utf-8") as f:
        return f.read()


def write(p, s):
    with io.open(p, "w", encoding="utf-8") as f:
        f.write(s)


def rep(text, old, new, tag, count=1):
    if text.count(old) != count:
        print("ASSERT FAIL [%s]: found %d, expected %d" % (tag, text.count(old), count))
        sys.exit(1)
    print("  ok %s (%d)" % (tag, count))
    return text.replace(old, new, count)


def add_after(text, anchor, line, tag):
    if text.count(anchor) != 1:
        print("ASSERT FAIL [%s]: anchor count %d" % (tag, text.count(anchor)))
        sys.exit(1)
    print("  ok %s" % tag)
    return text.replace(anchor, anchor + line, 1)


# ===================================================== #A UserProfilePage
USER_IMPORT = ("import com.java.myapplication.ui.components.tapGuard\n",
               "import com.java.myapplication.ui.glass.LiquidButton\n"
               "import com.java.myapplication.ui.glass.rememberSolidBackdrop\n"
               "import com.java.myapplication.ui.theme.isAppDarkTheme\n", "U1 imports")
USER_IMPORT_BORDER = ("import androidx.compose.foundation.background\n",
                      "import androidx.compose.foundation.border\n", "U2 border")

# 私信按钮（只替换开头的 Box 修饰链，内部 Icon 块原样保留）
USER_OLD_PM = """                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                        .clickable { onPm() },
                    contentAlignment = Alignment.Center,
                ) {
"""
USER_NEW_PM = """                // 1.192e: 私信按钮 → 液态玻璃按钮（kyant LiquidButton）
                LiquidButton(
                    onClick = onPm,
                    backdrop = rememberSolidBackdrop(MaterialTheme.colorScheme.surface),
                    surfaceColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                    height = 32.dp,
                    contentPadding = 13.dp,
                ) {
"""

# 关注按钮（同样只替换开头，Text 中的 \\uXXXX 字面量不受影响）
USER_OLD_FOLLOW = """    Row(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable(enabled = !busy, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
"""
USER_NEW_FOLLOW = """    // 1.192e: 关注按钮 → 液态玻璃按钮（未关注 = 主题色调玻璃；已关注 = 浅灰玻璃）
    LiquidButton(
        onClick = { if (!busy) onClick() },
        backdrop = rememberSolidBackdrop(MaterialTheme.colorScheme.surface),
        isInteractive = !busy,
        tint = if (followed) Color.Unspecified else MaterialTheme.colorScheme.primary,
        surfaceColor = if (followed) bg else Color.Unspecified,
        height = 32.dp,
        contentPadding = 14.dp,
        arrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
    ) {
"""

# 下方 4~5 个 TabChip（整函数替换：无中文字面量）
USER_OLD_TAB = """private fun TabChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surface
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
"""
USER_NEW_TAB = """private fun TabChip(text: String, selected: Boolean, onClick: () -> Unit) {
    // 1.192e: 材质升级为与顶部玻璃胶囊同源（选中 = 近实心磨砂 + 主题色文字；未选 = 极淡半透明）
    val dark = isAppDarkTheme()
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (selected) {
                    if (dark) Color(0xFF2E2E30).copy(0.94f) else Color.White.copy(0.92f)
                } else {
                    if (dark) Color.White.copy(0.075f) else Color.Black.copy(0.045f)
                }
            )
            .border(
                0.6.dp,
                if (selected) {
                    if (dark) Color.White.copy(0.10f) else Color.Black.copy(0.05f)
                } else {
                    if (dark) Color.White.copy(0.06f) else Color.Black.copy(0.03f)
                },
                RoundedCornerShape(999.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
"""

# ===================================================== #B SearchPage
SEARCH_OLD_DARK = """    var historyTick by remember { mutableIntStateOf(0) }
    val history = remember(historyTick, HupuPrefs.searchHistoryVersion) { HupuPrefs.loadSearchHistory() }
"""
SEARCH_NEW_DARK = """    var historyTick by remember { mutableIntStateOf(0) }
    val history = remember(historyTick, HupuPrefs.searchHistoryVersion) { HupuPrefs.loadSearchHistory() }
    val dark = isAppDarkTheme()
"""

SEARCH_OLD_CHIP = """                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onPick(h) }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
"""
SEARCH_NEW_CHIP = """                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            // 1.192e: 材质升级为与顶部玻璃胶囊同源
                            .background(if (dark) Color.White.copy(0.075f) else Color.Black.copy(0.045f))
                            .border(
                                0.6.dp,
                                if (dark) Color.White.copy(0.06f) else Color.Black.copy(0.03f),
                                RoundedCornerShape(50),
                            )
                            .clickable { onPick(h) }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
"""

# ===================================================== #C ThreadActionBar
ACTION_IMPORTS = [
    ("import androidx.compose.runtime.Composable\n",
     "import androidx.compose.runtime.remember\nimport androidx.compose.runtime.rememberCoroutineScope\n",
     "A1 runtime"),
    ("import com.java.myapplication.ui.components.formatCount\n",
     "import com.java.myapplication.ui.glass.InteractiveHighlight\n"
     "import com.java.myapplication.ui.glass.LiquidButton\n", "A2 glass"),
]

ACTION_OLD_WRITE = """        Row(
            Modifier
                .weight(1f)
                .height(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(writePillColor)
                .clickable { onWrite() }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
"""
ACTION_NEW_WRITE = """        // 1.192e: 「写评论」→ 液态玻璃按钮（复用页面真 backdrop，内容保持左对齐）
        LiquidButton(
            onClick = onWrite,
            backdrop = backdrop,
            modifier = Modifier.weight(1f),
            height = 40.dp,
            surfaceColor = writePillColor,
            contentPadding = 14.dp,
            arrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
        ) {
"""

ACTION_OLD_ITEM = """    val normal = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)
    val tint = if (active) activeColor else normal
    Column(
        Modifier
            .width(46.dp)
            .height(46.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
"""
ACTION_NEW_ITEM = """    val normal = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)
    val tint = if (active) activeColor else normal
    // 1.192e: 按压反馈改用 kyant 的流体高光（与 LiquidButton / 底部 Tab 栏同一套
    // InteractiveHighlight）——静止时零视觉负担，按下时高光跟随手指
    val scope = rememberCoroutineScope()
    val highlight = remember(scope) { InteractiveHighlight(animationScope = scope) }
    Column(
        Modifier
            .width(46.dp)
            .height(46.dp)
            .clip(RoundedCornerShape(16.dp))
            .then(highlight.modifier)
            .clickable(
                interactionSource = null,
                indication = null,
                onClick = onClick,
            )
            .then(highlight.gestureModifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
"""


def main():
    # ---- #A
    s = read(USER)
    s = add_after(s, USER_IMPORT_BORDER[0], USER_IMPORT_BORDER[1], USER_IMPORT_BORDER[2])
    s = add_after(s, USER_IMPORT[0], USER_IMPORT[1], USER_IMPORT[2])
    s = rep(s, USER_OLD_PM, USER_NEW_PM, "U3 私信按钮")
    s = rep(s, USER_OLD_FOLLOW, USER_NEW_FOLLOW, "U4 关注按钮")
    s = rep(s, USER_OLD_TAB, USER_NEW_TAB, "U5 TabChip 玻璃化")
    write(USER, s)

    # ---- #B
    s = read(SEARCH)
    s = rep(s, SEARCH_OLD_DARK, SEARCH_NEW_DARK, "Q1 HistoryPanel dark")
    s = rep(s, SEARCH_OLD_CHIP, SEARCH_NEW_CHIP, "Q2 历史条目玻璃化")
    write(SEARCH, s)

    # ---- #C
    s = read(ACTION)
    for anchor, line, tag in ACTION_IMPORTS:
        s = add_after(s, anchor, line, tag)
    s = rep(s, ACTION_OLD_WRITE, ACTION_NEW_WRITE, "A3 写评论 LiquidButton")
    s = rep(s, ACTION_OLD_ITEM, ACTION_NEW_ITEM, "A4 ActionItem 流体按压")
    write(ACTION, s)

    print("FIX16 1.192 OK")


main()