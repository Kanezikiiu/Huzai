#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
1.192 第二十二批（真机反馈 2 条）

#1 按钮底「灰」→ 白：浅色模式下 buttonFill 由 半透明黑 改为**纯白**
   （白底在浅色页面上需要靠描边立住，故 buttonBorder 浅色 0.05 → 0.08）
   并且「只看楼主 / 排序」由 34dp 再加胖到 38dp（内边距 14 → 16）
#2 「只看楼主」选中态：不再用 primary@0.16 的淡色，改为**实心主题色**，
   字色用 MaterialTheme.colorScheme.onPrimary（各调色板自带的对色，避免与底色相似）
   → buttonAccent 失去唯一用处，一并删除
"""
import io, re, sys

W = "/data/user/0/com.ai.assistance.operit/files/workspace/2dab7fe4-ceff-4d94-99a0-8e839ec2f3da"
P = W + "/app/src/main/java/com/java/myapplication/ui/pages/"
BTN = W + "/app/src/main/java/com/java/myapplication/ui/glass/LiquidButton.kt"
DETAIL = P + "ThreadDetailPage.kt"

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


# ============================================================ #1 白底 + 描边加强，删 buttonAccent
BTN_OLD_FILL = """/**
 * 1.192h: 按钮的「中性底」。
 *
 * 深色模式用**实体深灰**，而不是低透明度白叠加——白色 9% 叠在深底上会糊成一层
 * 灰蒙蒙的面纱（真机反馈确认）；实体色 + 明确描边才有「有边界的物件」的感觉。
 */
internal fun buttonFill(dark: Boolean): Color =
    if (dark) Color(0xFF2C2C2E) else Color.Black.copy(alpha = 0.06f)
"""
BTN_NEW_FILL = """/**
 * 1.192j: 按钮的「中性底」。
 *
 * 深色模式用**实体深灰**——低透明度白叠在深底上会糊成一层灰蒙蒙的面纱（真机反馈确认）。
 * 浅色模式用**纯白**——半透明黑会发灰、显脏（真机反馈确认：那两个小按钮看着「灰底」）；
 * 白底在浅色页面上立不住，所以靠稍强一点的描边（见 buttonBorder）来定义边界。
 */
internal fun buttonFill(dark: Boolean): Color =
    if (dark) Color(0xFF2C2C2E) else Color.White
"""

BTN_OLD_BORDER = """/** 1.192h: 按钮描边 —— 定义边缘，避免灰底与页面底糊在一起 */
internal fun buttonBorder(dark: Boolean): Color =
    if (dark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.05f)

/** 1.192h: 强调态按钮底（如「只看楼主」选中）。深色需要更高不透明度才不发灰 */
internal fun buttonAccent(primary: Color, dark: Boolean): Color =
    primary.copy(alpha = if (dark) 0.30f else 0.16f)
"""
BTN_NEW_BORDER = """/**
 * 1.192j: 按钮描边 —— 白底在浅色页面上完全靠它立住，故浅色由 0.05 提到 0.08。
 */
internal fun buttonBorder(dark: Boolean): Color =
    if (dark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f)
"""

BTN_OLD_DOC = """ *    因此这里直接绘制平面玻璃底面 + 描边，配色走 buttonFill / buttonBorder / buttonAccent。
"""
BTN_NEW_DOC = """ *    因此这里直接绘制平面玻璃底面 + 描边，配色走 buttonFill / buttonBorder（或主题色）。
"""

BTN_OLD_PARAM = """ * @param fill   底面（必填，来自 buttonFill / buttonAccent / 主题色）
"""
BTN_NEW_PARAM = """ * @param fill   底面（必填，来自 buttonFill 或主题色）
"""

# ============================================================ #1/#2 帖子详情两个按钮
DETAIL_OLD_IMPORT = """import com.java.myapplication.ui.glass.buttonAccent
"""
DETAIL_NEW_IMPORT = """"""

DETAIL_OLD_ONLYOP = """                                        // 1.192h: 不再采样 backdrop——drawBackdrop 默认带的 Highlight/Shadow
                                        // 就是那圈「亮边」；配色改走 buttonFill / buttonAccent。
                                        LiquidButton(
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
                                                fontWeight = if (onlyOp) FontWeight.SemiBold else FontWeight.Medium,
                                                color = if (onlyOp) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
"""
DETAIL_NEW_ONLYOP = """                                        // 1.192h: 不再采样 backdrop——drawBackdrop 默认带的 Highlight/Shadow
                                        // 就是那圈「亮边」。
                                        // 1.192j: 选中态改**实心主题色**（原来 primary@0.16 太淡），字色用
                                        // onPrimary——各调色板自带的对色（浅色=白 / 深色=极深色），不会与底同色；
                                        // 未选态白底 + 描边。
                                        LiquidButton(
                                            onClick = { onlyOp = !onlyOp },
                                            fill = if (onlyOp) MaterialTheme.colorScheme.primary else buttonFill(dark),
                                            border = if (onlyOp) Color.Transparent else buttonBorder(dark),
                                            // 1.192i/j: 30 → 34 → 38dp，内边距 12 → 14 → 16，字 12 → 13sp
                                            //（再胖一点，不再细长）
                                            height = 38.dp,
                                            contentPadding = 16.dp,
                                            arrangement = Arrangement.Center,
                                        ) {
                                            Text(
                                                "\\u53ea\\u770b\\u697c\\u4e3b",
                                                fontSize = 13.sp,
                                                fontWeight = if (onlyOp) FontWeight.SemiBold else FontWeight.Medium,
                                                color = if (onlyOp) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
"""

DETAIL_OLD_SORT = """                                            border = buttonBorder(dark),
                                            // 1.192i: 稍微增大（30 → 34dp，内边距 12 → 14，字 12 → 13sp）
                                            height = 34.dp,
                                            contentPadding = 14.dp,
"""
DETAIL_NEW_SORT = """                                            border = buttonBorder(dark),
                                            // 1.192i/j: 30 → 34 → 38dp，内边距 12 → 14 → 16，字 12 → 13sp
                                            height = 38.dp,
                                            contentPadding = 16.dp,
"""


def main():
    print("== 按钮配色：浅色改纯白 ==")
    rep(BTN, BTN_OLD_FILL, BTN_NEW_FILL, "B1 buttonFill")
    rep(BTN, BTN_OLD_BORDER, BTN_NEW_BORDER, "B2 buttonBorder + 删 buttonAccent")
    rep(BTN, BTN_OLD_DOC, BTN_NEW_DOC, "B3 KDoc 1")
    rep(BTN, BTN_OLD_PARAM, BTN_NEW_PARAM, "B4 KDoc 2")

    print("== 帖子详情两个按钮 ==")
    rep(DETAIL, DETAIL_OLD_IMPORT, DETAIL_NEW_IMPORT, "D1 去 buttonAccent import")
    rep_any(DETAIL, [DETAIL_OLD_ONLYOP, unesc(DETAIL_OLD_ONLYOP)], DETAIL_NEW_ONLYOP, "D2 只看楼主")
    rep(DETAIL, DETAIL_OLD_SORT, DETAIL_NEW_SORT, "D3 排序状态")

    print("== 收尾校验 ==")
    if "buttonAccent" in C.get(BTN, ""):
        FAILS.append("[X1] buttonAccent 仍存在于 LiquidButton.kt")
        print("  FAIL X1")
    else:
        print("  ok   X1 buttonAccent 已清理")
    if "buttonAccent" in C.get(DETAIL, ""):
        FAILS.append("[X2] buttonAccent 仍被 ThreadDetailPage 引用")
        print("  FAIL X2")
    else:
        print("  ok   X2 ThreadDetailPage 无 buttonAccent 引用")

    if FAILS:
        print("\n!!! ABORT, nothing written. %d failures:" % len(FAILS))
        for f in FAILS:
            print("   " + f)
        sys.exit(1)

    for path in [BTN, DETAIL]:
        with io.open(path, "w", encoding="utf-8") as f:
            f.write(C[path])
        print("  wrote %s" % path.split("/")[-1])
    print("FIX21 1.192 OK")


main()