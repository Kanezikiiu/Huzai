#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
1.192 第十九批（真机反馈 1 条）

「只看楼主 / 排序状态」在语义上是**按钮**（独立开关 / 循环切换），不是 tab 条
（tab 条 = 一组互斥选项 + 选中项位置概念）。上一批误用了 glassPress（tab 手感），
本批改为 kyant LiquidButton（流体高光 + 按压形变）。

注意：这两个控件位于 `.layerBackdrop(actionBackdrop)` 内容层内部，
不能采样 actionBackdrop（自采样会糊），故走与「私信 / 关注」同款的纯色 backdrop。
"""
import io, re, sys

W = "/data/user/0/com.ai.assistance.operit/files/workspace/2dab7fe4-ceff-4d94-99a0-8e839ec2f3da"
DETAIL = W + "/app/src/main/java/com/java/myapplication/ui/pages/ThreadDetailPage.kt"

C = {}
FAILS = []


def load():
    if DETAIL not in C:
        with io.open(DETAIL, "r", encoding="utf-8") as f:
            C[DETAIL] = f.read()
    return C[DETAIL]


def unesc(s):
    """把源码里的 \\uXXXX 还原成真实字符，用于兼容另一种写法"""
    return re.sub(r"\\u([0-9a-fA-F]{4})", lambda m: chr(int(m.group(1), 16)), s)


def rep_any(olds, new, tag):
    text = load()
    for i, o in enumerate(olds):
        if text.count(o) == 1:
            C[DETAIL] = text.replace(o, new, 1)
            print("  ok   %s (variant %d)" % (tag, i))
            return
    FAILS.append("[%s] no variant matched; counts=%s" % (tag, [text.count(o) for o in olds]))
    print("  FAIL %s (counts=%s)" % (tag, [text.count(o) for o in olds]))


# ---------------- R1 imports
OLD_IMPORTS = """import com.java.myapplication.ui.components.glassBorder
import com.java.myapplication.ui.components.glassFill
import com.java.myapplication.ui.components.glassPress
import com.java.myapplication.ui.theme.isAppDarkTheme
"""
NEW_IMPORTS = """// 1.192g: 「只看楼主 / 排序状态」= 按钮 → kyant LiquidButton（不再走 tab 条的 glassPress）
import com.java.myapplication.ui.glass.LiquidButton
import com.java.myapplication.ui.glass.rememberSolidBackdrop
"""

# ---------------- R2 纯色 backdrop
OLD_BACKDROP = """    val actionBackdrop = rememberLayerBackdrop {
        drawRect(actionBarBg)
        drawContent()
    }
"""
NEW_BACKDROP = """    val actionBackdrop = rememberLayerBackdrop {
        drawRect(actionBarBg)
        drawContent()
    }
    // 1.192g: 「只看楼主 / 排序状态」两个按钮的玻璃采样源。
    // 它们位于上面这一层的**内部**（会画进 actionBackdrop），采样 actionBackdrop 会自采样糊掉，
    // 因此用纯色 backdrop —— 与用户主页「私信 / 关注」按钮同款。
    val toolsBackdrop = rememberSolidBackdrop(MaterialTheme.colorScheme.surface)
"""

# ---------------- R3 r-tools 整块重写
OLD_TOOLS = r'''                                item(key = "r-tools") {
                                    val dark = isAppDarkTheme()
                                    Row(
                                        Modifier.fillMaxWidth().padding(top = 2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            "\u53ea\u770b\u697c\u4e3b",
                                            fontSize = 12.sp,
                                            fontWeight = if (onlyOp) FontWeight.SemiBold else FontWeight.Medium,
                                            color = if (onlyOp) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                // 1.192f: 玻璃胶囊 + 与主页 tab 同款 spring 按压（去掉 ripple 压暗）
                                                .glassPress(
                                                    shape = RoundedCornerShape(999.dp),
                                                    fill = glassFill(dark, onlyOp),
                                                    border = glassBorder(dark, onlyOp),
                                                    onClick = { onlyOp = !onlyOp },
                                                )
                                                .padding(horizontal = 12.dp, vertical = 6.dp),
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                // 1.192f: 玻璃胶囊 + 与主页 tab 同款 spring 按压
                                                .glassPress(
                                                    shape = RoundedCornerShape(999.dp),
                                                    fill = glassFill(dark),
                                                    border = glassBorder(dark),
                                                    onClick = { sortMode = (sortMode + 1) % 3 },
                                                )
                                                .padding(horizontal = 12.dp, vertical = 6.dp),
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
                                                when (sortMode) { 0 -> "\u9ed8\u8ba4\u987a\u5e8f"; 1 -> "\u6700\u65b0"; else -> "\u6700\u70ed" },
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
'''

NEW_TOOLS = r'''                                item(key = "r-tools") {
                                    Row(
                                        Modifier.fillMaxWidth().padding(top = 2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        // 1.192g: 「只看楼主 / 排序状态」是**按钮**而不是 tab 条 ——
                                        // tab 条 = 一组互斥选项 + 选中项位置概念；这两个是「独立开关」与「循环切换」，
                                        // 所以用 kyant LiquidButton（液态玻璃 + 按下处流体高光 + 按压形变），
                                        // 与主页横滑条 tab 的 spring 缩放手感明确区分。
                                        LiquidButton(
                                            onClick = { onlyOp = !onlyOp },
                                            backdrop = toolsBackdrop,
                                            height = 30.dp,
                                            contentPadding = 12.dp,
                                            arrangement = Arrangement.Center,
                                            surfaceColor = if (onlyOp) {
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                            } else {
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f)
                                            },
                                        ) {
                                            Text(
                                                "\u53ea\u770b\u697c\u4e3b",
                                                fontSize = 12.sp,
                                                fontWeight = if (onlyOp) FontWeight.SemiBold else FontWeight.Medium,
                                                color = if (onlyOp) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        LiquidButton(
                                            onClick = { sortMode = (sortMode + 1) % 3 },
                                            backdrop = toolsBackdrop,
                                            height = 30.dp,
                                            contentPadding = 12.dp,
                                            arrangement = Arrangement.Center,
                                            surfaceColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f),
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
                                                when (sortMode) { 0 -> "\u9ed8\u8ba4\u987a\u5e8f"; 1 -> "\u6700\u65b0"; else -> "\u6700\u70ed" },
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
'''


def main():
    print("== R1 imports ==")
    rep_any([OLD_IMPORTS], NEW_IMPORTS, "R1 换 LiquidButton import")

    print("== R2 纯色 backdrop ==")
    rep_any([OLD_BACKDROP], NEW_BACKDROP, "R2 toolsBackdrop")

    print("== R3 r-tools 两个按钮 ==")
    rep_any([OLD_TOOLS, unesc(OLD_TOOLS)], NEW_TOOLS, "R3 LiquidButton 化")

    if FAILS:
        print("\n!!! ABORT, nothing written. %d failures:" % len(FAILS))
        for f in FAILS:
            print("   " + f)
        sys.exit(1)

    with io.open(DETAIL, "w", encoding="utf-8") as f:
        f.write(C[DETAIL])
    print("  wrote ThreadDetailPage.kt")
    print("FIX18 1.192 OK")


main()