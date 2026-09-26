#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
1.192 第十三批：顶部 Tab 玻璃质感微调（真机反馈）
  #1 胶囊「凸起/复古」→ 去掉投影 + 去掉上下渐变/白色高光描边，改「平铺半透明玻璃块」
  #2 排序子 Tab「太方」  → 容器 10dp→17dp、选中胶囊 8dp→14dp（等同半高 → 胶囊）
  #3 排序子 Tab 点击方形遮罩 → clickable 改为 indication = null（去掉 ripple）
全部精确文本替换 + 命中数断言。
"""
import io, re, sys

W = "/data/user/0/com.ai.assistance.operit/files/workspace/2dab7fe4-ceff-4d94-99a0-8e839ec2f3da"
FEED = W + "/app/src/main/java/com/java/myapplication/ui/components/FeedUi.kt"


def read(p):
    with io.open(p, "r", encoding="utf-8") as f:
        return f.read()


def write(p, s):
    with io.open(p, "w", encoding="utf-8") as f:
        f.write(s)


def sub_once(text, pattern, repl, tag, count=1):
    new, n = re.subn(pattern, lambda m: repl, text, count=count)
    if n != count:
        print("ASSERT FAIL [%s]: expected %d, got %d" % (tag, count, n))
        sys.exit(1)
    print("  ok %s (%d)" % (tag, n))
    return new


# ---------- #1a Chip：平面化配色（去渐变 / 去白色高光描边） ----------
CHIP_OLD_BG = """    val shape = RoundedCornerShape(50)
    val bg = when {
        selected && dark -> Brush.verticalGradient(
            listOf(Color(0xFF3C3C3E).copy(0.94f), Color(0xFF28282A).copy(0.90f))
        )
        selected -> Brush.verticalGradient(
            listOf(Color.White.copy(0.97f), Color(0xFFF0F0F3).copy(0.92f))
        )
        dark -> Brush.verticalGradient(
            listOf(Color.White.copy(0.10f), Color.White.copy(0.05f))
        )
        else -> Brush.verticalGradient(
            listOf(Color.Black.copy(0.055f), Color.Black.copy(0.028f))
        )
    }
    val edge = when {
        selected && dark -> Color.White.copy(0.16f)
        selected -> Color.White.copy(0.95f)
        dark -> Color.White.copy(0.09f)
        else -> Color.White.copy(0.55f)
    }
"""
CHIP_NEW_BG = """    val shape = RoundedCornerShape(50)
    // 1.192b（真机反馈）：去掉立体感——不再用投影/上下渐变/白色高光描边，
    // 改成「平铺半透明玻璃块 + 一层极淡描边」，接近 iOS 原生 chip 的扁平观感。
    val bg = when {
        selected && dark -> Color(0xFF2E2E30).copy(0.94f)
        selected -> Color.White.copy(0.92f)
        dark -> Color.White.copy(0.075f)
        else -> Color.Black.copy(0.045f)
    }
    val edge = when {
        selected && dark -> Color.White.copy(0.10f)
        selected -> Color.Black.copy(0.05f)
        dark -> Color.White.copy(0.06f)
        else -> Color.Black.copy(0.03f)
    }
"""

# ---------- #1b Chip：移除投影 ----------
CHIP_OLD_SHADOW = """            .then(
                if (selected) Modifier.shadow(
                    elevation = 5.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = Color.Black.copy(0.5f),
                    spotColor = Color.Black.copy(0.5f),
                ) else Modifier
            )
            .clip(shape)
"""
CHIP_NEW_SHADOW = """            .clip(shape)
"""

# ---------- #2 SortBar：圆角增大 ----------
SB_OLD_SHAPE = """    val shape = RoundedCornerShape(10.dp)
    val segShape = RoundedCornerShape(8.dp)
"""
SB_NEW_SHAPE = """    // 1.192b（真机反馈）：圆角增大到「半高 = 胶囊」，不再显方
    val shape = RoundedCornerShape(17.dp)
    val segShape = RoundedCornerShape(14.dp)
"""

# ---------- #2b SortBar：选中胶囊平面化 ----------
SB_OLD_BLOCK = """                    .height(barH - inset * 2)
                    .shadow(
                        4.dp,
                        segShape,
                        clip = false,
                        ambientColor = Color.Black.copy(0.45f),
                        spotColor = Color.Black.copy(0.45f),
                    )
                    .clip(segShape)
                    .background(
                        Brush.verticalGradient(
                            if (dark) listOf(Color(0xFF3C3C3E).copy(0.96f), Color(0xFF29292B).copy(0.94f))
                            else listOf(Color.White, Color(0xFFF0F0F3))
                        )
                    ),
"""
SB_NEW_BLOCK = """                    .height(barH - inset * 2)
                    .clip(segShape)
                    .background(if (dark) Color(0xFF2E2E30).copy(0.96f) else Color.White.copy(0.95f)),
"""

# ---------- #3 SortBar：去掉点击方形遮罩 ----------
SB_OLD_CLICK = """                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { onSelect(s.url) },
"""
SB_NEW_CLICK = """                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            // 1.192b（真机反馈）：去掉默认 ripple 方形遮罩
                            .clickable(
                                interactionSource = null,
                                indication = null,
                            ) { onSelect(s.url) },
"""


def main():
    s = read(FEED)
    s = sub_once(s, re.escape(CHIP_OLD_BG), CHIP_NEW_BG, "#1a Chip 平面配色")
    s = sub_once(s, re.escape(CHIP_OLD_SHADOW), CHIP_NEW_SHADOW, "#1b Chip 去投影")
    s = sub_once(s, re.escape(SB_OLD_SHAPE), SB_NEW_SHAPE, "#2 SortBar 圆角")
    s = sub_once(s, re.escape(SB_OLD_BLOCK), SB_NEW_BLOCK, "#2b SortBar 胶囊平面化")
    s = sub_once(s, re.escape(SB_OLD_CLICK), SB_NEW_CLICK, "#3 SortBar 去方形遮罩")
    write(FEED, s)
    print("FIX13 1.192 OK")


main()