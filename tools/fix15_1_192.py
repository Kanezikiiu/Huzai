#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
1.192 第十五批（真机反馈 3 条）

#1 Chip 按压：撤掉「按下变暗」，恢复纯 spring 缩放（更跟手 + 更明显）
#2 信息流设置页：关键词 chip 材质升级为玻璃（扁平半透明 + 极淡描边）
#3 阅读字号页：Material3 Slider → LiquidSlider（移植 Kyant0/AndroidLiquidGlass 同源组件）
"""
import io, re, sys

W = "/data/user/0/com.ai.assistance.operit/files/workspace/2dab7fe4-ceff-4d94-99a0-8e839ec2f3da"
P = W + "/app/src/main/java/com/java/myapplication/ui/pages/"
FEED = W + "/app/src/main/java/com/java/myapplication/ui/components/FeedUi.kt"
TEXT = P + "TextSizeSettingsPage.kt"
FILTER = P + "FilterSettingsPage.kt"


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


# ==================================================== #1 FeedUi（撤变暗，回 spring）
FEED_OLD_PRESS = """    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // 1.192c（真机反馈）：按压必须「按下瞬间即见」——轻点也要亮。
    // 只靠 spring 缩放起势太慢，轻点几乎看不见；改为「按下立即变暗 + 松手后保暗 90ms」，
    // 并配一个 70ms 的快速缩放。held 同时驱动变暗与缩放。
    var held by remember { mutableStateOf(false) }
    LaunchedEffect(pressed) {
        if (pressed) {
            held = true
        } else {
            delay(90)
            held = false
        }
    }
    val scale by animateFloatAsState(
        targetValue = if (held) 0.94f else 1f,
        animationSpec = tween(70),
        label = "chipPress",
    )
"""
FEED_NEW_PRESS = """    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // 1.192d（真机反馈）：按压只用 spring 缩放，不做变暗。
    // 相比最初那版：StiffnessHigh 让按下那一帧就起步（不再是慢起势），
    // 深度加大到 0.90，快速轻点也看得见；松手仍靠 spring 弹性回弹。
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh,
        ),
        label = "chipPress",
    )
"""

FEED_OLD_CONTENT = """    // 按下遮罩：浅色变暗、深色提亮（与底部 Tab 栏同款语言），不做动画 → 立即生效
    val pressOverlay =
        if (held) (if (dark) Color.White.copy(0.14f) else Color.Black.copy(0.12f))
        else Color.Transparent
    val contentColor =
"""
FEED_NEW_CONTENT = """    val contentColor =
"""

FEED_OLD_BG = """            .clip(shape)
            .background(bg)
            .background(pressOverlay)
            .border(0.6.dp, edge, shape)
"""
FEED_NEW_BG = """            .clip(shape)
            .background(bg)
            .border(0.6.dp, edge, shape)
"""

# ==================================================== #2 FilterSettingsPage
FILTER_IMPORT_BORDER = ("import androidx.compose.foundation.background\n",
                        "import androidx.compose.foundation.border\n", "L1 border")
FILTER_IMPORT_COLOR = ("import androidx.compose.ui.graphics.graphicsLayer\n",
                       "import androidx.compose.ui.graphics.Color\n", "L2 Color")
FILTER_IMPORT_THEME = ("import com.java.myapplication.ui.components.tapGuard\n",
                       "import com.java.myapplication.ui.theme.isAppDarkTheme\n", "L3 theme")

FILTER_OLD_COL = """    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
"""
FILTER_NEW_COL = """    val dark = isAppDarkTheme()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
"""

FILTER_OLD_CHIP = """                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { onRemove(kind, w) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
"""
FILTER_NEW_CHIP = """                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    // 1.192d: 材质升级为与顶部玻璃胶囊同源（扁平半透明 + 极淡描边）
                                    .background(
                                        if (dark) Color.White.copy(0.075f)
                                        else Color.Black.copy(0.045f)
                                    )
                                    .border(
                                        0.6.dp,
                                        if (dark) Color.White.copy(0.06f)
                                        else Color.Black.copy(0.03f),
                                        RoundedCornerShape(16.dp),
                                    )
                                    .clickable { onRemove(kind, w) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
"""

# ==================================================== #3 TextSizeSettingsPage
TEXT_IMPORT = ("import com.java.myapplication.ui.components.tapGuard\n",
               "import com.java.myapplication.ui.glass.LiquidSlider\n"
               "import com.kyant.backdrop.backdrops.rememberCanvasBackdrop\n", "T1 imports")

TEXT_OLD_SLIDER_IMPORT = "import androidx.compose.material3.Slider\n"

TEXT_OLD_BOX = """    var scale by remember { mutableFloatStateOf(HupuPrefs.loadFontScale()) }
    Box(
"""
TEXT_NEW_BOX = """    var scale by remember { mutableFloatStateOf(HupuPrefs.loadFontScale()) }
    // 1.192d: 滑条拇指的玻璃采样源。滑条位于 surface 卡片上，用卡片底色做「画布背景层」即可
    // （与上游 catalog 内联滑条的用法一致）；用页面级 LayerBackdrop 会采样到滑条自身。
    val sliderSurface = MaterialTheme.colorScheme.surface
    val sliderBackdrop = rememberCanvasBackdrop { drawRect(sliderSurface) }
    Box(
"""

TEXT_OLD_SLIDER = """                    Slider(
                        value = scale,
                        onValueChange = { scale = it },
                        onValueChangeFinished = { HupuPrefs.saveFontScale(scale) },
                        valueRange = HupuPrefs.FONT_SCALE_MIN..HupuPrefs.FONT_SCALE_MAX,
                    )
"""
TEXT_NEW_SLIDER = """                    // 1.192d: 材质升级——液态玻璃滑条（LiquidSlider，Kyant0/AndroidLiquidGlass 同源组件）
                    LiquidSlider(
                        value = { scale },
                        onValueChange = { scale = it },
                        valueRange = HupuPrefs.FONT_SCALE_MIN..HupuPrefs.FONT_SCALE_MAX,
                        backdrop = sliderBackdrop,
                        onValueChangeFinished = { HupuPrefs.saveFontScale(scale) },
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
"""


def main():
    # ---------------- #1
    s = read(FEED)
    s = rep(s, FEED_OLD_PRESS, FEED_NEW_PRESS, "F1 Chip 回 spring 缩放")
    s = rep(s, FEED_OLD_CONTENT, FEED_NEW_CONTENT, "F2 撤按下遮罩")
    s = rep(s, FEED_OLD_BG, FEED_NEW_BG, "F3 撤遮罩落层")
    write(FEED, s)

    # ---------------- #2
    s = read(FILTER)
    for anchor, line, tag in (FILTER_IMPORT_BORDER, FILTER_IMPORT_COLOR, FILTER_IMPORT_THEME):
        s = add_after(s, anchor, line, tag)
    s = rep(s, FILTER_OLD_COL, FILTER_NEW_COL, "L4 dark 声明")
    s = rep(s, FILTER_OLD_CHIP, FILTER_NEW_CHIP, "L5 chip 玻璃化")
    write(FILTER, s)

    # ---------------- #3
    s = read(TEXT)
    s = rep(s, TEXT_OLD_SLIDER_IMPORT, "", "T0 移除 M3 Slider import")
    s = add_after(s, TEXT_IMPORT[0], TEXT_IMPORT[1], TEXT_IMPORT[2])
    s = rep(s, TEXT_OLD_BOX, TEXT_NEW_BOX, "T2 加采样层")
    s = rep(s, TEXT_OLD_SLIDER, TEXT_NEW_SLIDER, "T3 换 LiquidSlider")
    write(TEXT, s)

    print("FIX15 1.192 OK")


main()