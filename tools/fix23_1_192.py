#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
1.192 第二十四批（真机反馈 1 条）

专区选择面板的版块列表：选中后对勾落在「中间」而不是右侧。

根因：该行同时有
    Text(modifier = Modifier.weight(1f, fill = false))   ← 不填满自己的配额
    Spacer(Modifier.weight(1f))                          ← 填满自己的配额
两个 weight 子项各分到一半剩余宽度；Text 因为 fill = false 只占内容宽度，
而未用掉的配额**不会重新分配**，于是 Spacer 结束位置 ≈ 50% 配额 + 文本实际宽度，
对勾被放在约 70% 处 → 视觉上「在中间」（长版块名时会更明显/更靠右）。

修法：文本直接吃满剩余宽度（weight(1f)，默认真填充），删掉多余的那个 Spacer，
对勾自然贴右。同时不再依赖两个 weight 的隐式分配，长名字也不会把对勾挤出去。
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


OLD = """                        Text(
                            t.name,
                            fontSize = 15.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f, fill = false),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.weight(1f))
                        if (isSelected) {
"""

NEW = """                        Text(
                            t.name,
                            fontSize = 15.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            // 1.192l: 文本直接吃满剩余宽度，对勾才贴右。
                            // 此前是 weight(1f, fill = false) + 后面一个 weight(1f) 的 Spacer：
                            // 两个 weight 各分到一半剩余宽度，而 Text 因 fill = false 只占内容宽度，
                            // 没用掉的配额不会重新分配 → 对勾落在约 70% 处（看着「在中间」）。
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (isSelected) {
"""


def main():
    print("== 版块列表对勾靠右 ==")
    rep(SEARCH, OLD, NEW, "S1 对勾贴右")
    if FAILS:
        print("\n!!! ABORT, nothing written. %d failures:" % len(FAILS))
        for f in FAILS:
            print("   " + f)
        sys.exit(1)
    with io.open(SEARCH, "w", encoding="utf-8") as f:
        f.write(C[SEARCH])
    print("  wrote SearchPage.kt")
    print("FIX23 1.192 OK")


main()