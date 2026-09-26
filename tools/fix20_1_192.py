#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
1.192 第二十一批（真机反馈 3 条）

#1 「只看楼主 / 排序 / 搜索历史按钮」稍微增大
#2 搜索历史由单行横滑 → 多行自适应换行（FlowRow）
#3 「写评论」按钮透明度与所在功能栏对齐（不再是不透明实心板）
"""
import io, re, sys

W = "/data/user/0/com.ai.assistance.operit/files/workspace/2dab7fe4-ceff-4d94-99a0-8e839ec2f3da"
P = W + "/app/src/main/java/com/java/myapplication/ui/pages/"
DETAIL = P + "ThreadDetailPage.kt"
SEARCH = P + "SearchPage.kt"
ACTION = P + "ThreadActionBar.kt"

C = {}
FAILS = []


def load(p):
    if p not in C:
        with io.open(p, "r", encoding="utf-8") as f:
            C[p] = f.read()
    return C[p]


def unesc(s):
    return re.sub(r"\\u([0-9a-fA-F]{4})", lambda m: chr(int(m.group(1), 16)), s)


def rep(p, old, new, tag, count=1):
    text = load(p)
    got = text.count(old)
    if got != count:
        FAILS.append("[%s] found %d, expected %d" % (tag, got, count))
        print("  FAIL %s (found %d, expected %d)" % (tag, got, count))
        return
    C[p] = text.replace(old, new, count)
    print("  ok   %s (%d)" % (tag, count))


def rep_any(p, olds, new, tag):
    text = load(p)
    for i, o in enumerate(olds):
        if text.count(o) == 1:
            C[p] = text.replace(o, new, 1)
            print("  ok   %s (variant %d)" % (tag, i))
            return
    FAILS.append("[%s] no variant matched; counts=%s" % (tag, [text.count(o) for o in olds]))
    print("  FAIL %s (counts=%s)" % (tag, [text.count(o) for o in olds]))


# ============================================================ #1 帖子详情两按钮增大
DETAIL_OLD_ONLYOP = """                                        LiquidButton(
                                            onClick = { onlyOp = !onlyOp },
                                            fill = if (onlyOp) {
                                                buttonAccent(MaterialTheme.colorScheme.primary, dark)
                                            } else {
                                                buttonFill(dark)
                                            },
                                            border = buttonBorder(dark),
                                            height = 30.dp,
                                            contentPadding = 12.dp,
                                            arrangement = Arrangement.Center,
                                        ) {
                                            Text(
                                                "\\u53ea\\u770b\\u697c\\u4e3b",
                                                fontSize = 12.sp,
"""
DETAIL_NEW_ONLYOP = """                                        LiquidButton(
                                            onClick = { onlyOp = !onlyOp },
                                            fill = if (onlyOp) {
                                                buttonAccent(MaterialTheme.colorScheme.primary, dark)
                                            } else {
                                                buttonFill(dark)
                                            },
                                            border = buttonBorder(dark),
                                            // 1.192i: 稍微增大（30 → 34dp，内边距 12 → 14，字 12 → 13sp）
                                            height = 34.dp,
                                            contentPadding = 14.dp,
                                            arrangement = Arrangement.Center,
                                        ) {
                                            Text(
                                                "\\u53ea\\u770b\\u697c\\u4e3b",
                                                fontSize = 13.sp,
"""

DETAIL_OLD_SORT = """                                        LiquidButton(
                                            onClick = { sortMode = (sortMode + 1) % 3 },
                                            fill = buttonFill(dark),
                                            border = buttonBorder(dark),
                                            height = 30.dp,
                                            contentPadding = 12.dp,
                                            arrangement = Arrangement.Center,
                                        ) {
                                            if (sortSwitching) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(12.dp),
                                                    strokeWidth = 1.5.dp,
                                                    color = MaterialTheme.colorScheme.primary,
                                                )
                                                Spacer(Modifier.width(6.dp))
                                            }
                                            Text(
                                                when (sortMode) { 0 -> "\\u9ed8\\u8ba4\\u987a\\u5e8f"; 1 -> "\\u6700\\u65b0"; else -> "\\u6700\\u70ed" },
                                                fontSize = 12.sp,
"""
DETAIL_NEW_SORT = """                                        LiquidButton(
                                            onClick = { sortMode = (sortMode + 1) % 3 },
                                            fill = buttonFill(dark),
                                            border = buttonBorder(dark),
                                            // 1.192i: 稍微增大（30 → 34dp，内边距 12 → 14，字 12 → 13sp）
                                            height = 34.dp,
                                            contentPadding = 14.dp,
                                            arrangement = Arrangement.Center,
                                        ) {
                                            if (sortSwitching) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(12.dp),
                                                    strokeWidth = 1.5.dp,
                                                    color = MaterialTheme.colorScheme.primary,
                                                )
                                                Spacer(Modifier.width(6.dp))
                                            }
                                            Text(
                                                when (sortMode) { 0 -> "\\u9ed8\\u8ba4\\u987a\\u5e8f"; 1 -> "\\u6700\\u65b0"; else -> "\\u6700\\u70ed" },
                                                fontSize = 13.sp,
"""

# ============================================================ #2 搜索历史多行
SEARCH_OLD_IMPORTS = """import androidx.compose.foundation.layout.Arrangement
"""
SEARCH_NEW_IMPORTS = """import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.widthIn
"""

SEARCH_OLD_HEAD = """            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(history) { h ->
                    // 1.192h: 「搜索历史条目」是按钮语义（点击执行一次搜索）→ LiquidButton
                    LiquidButton(
                        onClick = { onPick(h) },
                        shape = RoundedCornerShape(50),
                        fill = buttonFill(dark),
                        border = buttonBorder(dark),
                        height = 32.dp,
                        contentPadding = 12.dp,
                        arrangement = Arrangement.Start,
                    ) {
                        Text(h, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
"""
SEARCH_NEW_HEAD = """            // 1.192i: 搜索历史改**多行自适应换行**（这一页除搜索框外就是它，空间足够）；
            // 单条限宽，避免超长关键词把整行顶出屏幕
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                history.forEach { h ->
                    // 1.192h: 「搜索历史条目」是按钮语义（点击执行一次搜索）→ LiquidButton
                    LiquidButton(
                        onClick = { onPick(h) },
                        modifier = Modifier.widthIn(max = 260.dp),
                        shape = RoundedCornerShape(50),
                        fill = buttonFill(dark),
                        border = buttonBorder(dark),
                        // 1.192i: 稍微增大（32 → 36dp，内边距 12 → 14，字 13 → 14sp）
                        height = 36.dp,
                        contentPadding = 14.dp,
                        arrangement = Arrangement.Start,
                    ) {
                        Text(h, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
"""

# ============================================================ #3 写评论透明度
ACTION_OLD_PILL = """    // 1.192h: 深色模式改实体深灰（白 16% 叠在毛玻璃上仍然发灰）
    val writePillColor =
        if (isLight) Color.White.copy(alpha = 0.78f) else Color(0xFF3A3A3C)
"""
ACTION_NEW_PILL = """    // 1.192i: 与同栏透明度对齐（功能栏 = 0.38）——此前浅色 0.78 / 深色实体色都太实，
    // 贴在这条毛玻璃上像一块实心板
    val writePillColor =
        if (isLight) Color.White.copy(alpha = 0.62f) else Color.White.copy(alpha = 0.38f)
"""


def main():
    print("== #1 帖子详情两按钮增大 ==")
    rep_any(DETAIL, [DETAIL_OLD_ONLYOP, unesc(DETAIL_OLD_ONLYOP)], DETAIL_NEW_ONLYOP, "D1 只看楼主")
    rep_any(DETAIL, [DETAIL_OLD_SORT, unesc(DETAIL_OLD_SORT)], DETAIL_NEW_SORT, "D2 排序状态")

    print("== #2 搜索历史多行 ==")
    rep(SEARCH, SEARCH_OLD_IMPORTS, SEARCH_NEW_IMPORTS, "Q1 imports")
    rep(SEARCH, SEARCH_OLD_HEAD, SEARCH_NEW_HEAD, "Q2 LazyRow → FlowRow")

    print("== #3 写评论透明度 ==")
    rep(ACTION, ACTION_OLD_PILL, ACTION_NEW_PILL, "A1 writePillColor")

    if FAILS:
        print("\n!!! ABORT, nothing written. %d failures:" % len(FAILS))
        for f in FAILS:
            print("   " + f)
        sys.exit(1)

    for path in [DETAIL, SEARCH, ACTION]:
        with io.open(path, "w", encoding="utf-8") as f:
            f.write(C[path])
        print("  wrote %s" % path.split("/")[-1])
    print("FIX20 1.192 OK")


main()