#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
1.192 第二十三批（真机反馈 2 条）

#1 专区选择面板下方的大类 chips 条缺「选中项居中」便捷特性
   → 补 rememberLazyListState + LaunchedEffect(animateChipCenterTo)，与三大页顶部横滑条同一套
#2 面板里「所有专区」行比搜索框宽 8dp、圆角也不同
   → 左右边距 12 → 16（与搜索框一致）、圆角 12 → 22（与搜索框 shape 一致）
"""
import io, re, sys

W = "/data/user/0/com.ai.assistance.operit/files/workspace/2dab7fe4-ceff-4d94-99a0-8e839ec2f3da"
SEARCH = W + "/app/src/main/java/com/java/myapplication/ui/pages/SearchPage.kt"

C = {}
FAILS = []


def load(p):
    if p not in C:
        with io.open(p, "r", encoding="utf-8") as f:
            C[p] = f.read()
    return C[p]


def rep(p, old, new, tag, count=1):
    text = load(p)
    got = text.count(old)
    if got != count:
        FAILS.append("[%s] found %d, expected %d" % (tag, got, count))
        print("  FAIL %s (found %d, expected %d)" % (tag, got, count))
        return
    C[p] = text.replace(old, new, count)
    print("  ok   %s (%d)" % (tag, count))


# ---------------------------------------------------------------- #1 选中项居中
OLD_STATE = """    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var selectedCate by remember { mutableStateOf<String?>(null) }
"""
NEW_STATE = """    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var selectedCate by remember { mutableStateOf<String?>(null) }
    // 1.192k: 大类 chips 条补「选中项居中」便捷特性（与三大页顶部横滑条同一套）。
    // 序号 0 是「全部」，大类从 1 开始。
    val cateBarState = rememberLazyListState()
    val cateSelIdx =
        if (selectedCate == null) 0 else 1 + categories.indexOfFirst { it.cateId == selectedCate }
    LaunchedEffect(cateSelIdx) {
        if (cateSelIdx >= 0) cateBarState.animateChipCenterTo(cateSelIdx)
    }
"""

OLD_LAZYROW = """            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clipToBounds()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "cate-all") {
"""
NEW_LAZYROW = """            LazyRow(
                state = cateBarState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clipToBounds()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "cate-all") {
"""

# ---------------------------------------------------------------- #2 与搜索框对齐
OLD_ALL = """            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (allSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else Color.Transparent)
                    .clickable { onSelect(null) }
"""
NEW_ALL = """            // 1.192k: 与下方搜索框对齐 —— 左右边距 12 → 16（此前比搜索框宽 8dp），
            // 圆角 12 → 22（与搜索框的 shape 保持一致）
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (allSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else Color.Transparent)
                    .clickable { onSelect(null) }
"""


def main():
    print("== #1 大类 chips 条：选中项居中 ==")
    rep(SEARCH, OLD_STATE, NEW_STATE, "S1 state + LaunchedEffect")
    rep(SEARCH, OLD_LAZYROW, NEW_LAZYROW, "S2 LazyRow state 绑定")

    print("== #2 「所有专区」与搜索框对齐 ==")
    rep(SEARCH, OLD_ALL, NEW_ALL, "S3 边距 + 圆角")

    if FAILS:
        print("\n!!! ABORT, nothing written. %d failures:" % len(FAILS))
        for f in FAILS:
            print("   " + f)
        sys.exit(1)

    with io.open(SEARCH, "w", encoding="utf-8") as f:
        f.write(C[SEARCH])
    print("  wrote SearchPage.kt")
    print("FIX22 1.192 OK")


main()