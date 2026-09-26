#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
1.192 第十七批（真机反馈 4 条）

#1 帖子详情「只看楼主 / 排序状态」两个按钮 → 玻璃胶囊（spring 按压，与主页 tab 一致）
#2 用户主页 私信/关注按钮 → 加高到 38dp；关注按钮去掉 tint 的 hue 混合（消除「薄雾」）
#3 用户主页下方 tab → 按压从「ripple 压暗」改为与主页 Chip 完全一致的 spring 缩放
#4 「写评论」→ 去掉 Arrangement.spacedBy 造成的额外 8dp 间距（文字回左）；底色提高不透明度

基建：把玻璃配色与按压反馈收敛成全站唯一定义
  glassFill / glassBorder / Modifier.glassPress（FeedUi.kt）

两阶段执行：先在内存中完成全部替换并校验命中数，全部通过才落盘。
"""
import io, sys

W = "/data/user/0/com.ai.assistance.operit/files/workspace/2dab7fe4-ceff-4d94-99a0-8e839ec2f3da"
P = W + "/app/src/main/java/com/java/myapplication/ui/pages/"
FEED = W + "/app/src/main/java/com/java/myapplication/ui/components/FeedUi.kt"
DETAIL = P + "ThreadDetailPage.kt"
USER = P + "UserProfilePage.kt"
SEARCH = P + "SearchPage.kt"
FILTER = P + "FilterSettingsPage.kt"
ACTION = P + "ThreadActionBar.kt"

C = {}
FAILS = []


def load(path):
    if path not in C:
        with io.open(path, "r", encoding="utf-8") as f:
            C[path] = f.read()
    return C[path]


def rep(path, old, new, tag, count=1):
    text = load(path)
    got = text.count(old)
    if got != count:
        FAILS.append("[%s] found %d, expected %d" % (tag, got, count))
        print("  FAIL %s (found %d, expected %d)" % (tag, got, count))
        return
    C[path] = text.replace(old, new, count)
    print("  ok   %s (%d)" % (tag, count))


def ins_before(path, anchor, line, tag):
    text = load(path)
    got = text.count(anchor)
    if got != 1:
        FAILS.append("[%s] anchor count %d" % (tag, got))
        print("  FAIL %s (anchor count %d)" % (tag, got))
        return
    C[path] = text.replace(anchor, line + anchor, 1)
    print("  ok   %s" % tag)


def add_after(path, anchor, line, tag):
    text = load(path)
    got = text.count(anchor)
    if got != 1:
        FAILS.append("[%s] anchor count %d" % (tag, got))
        print("  FAIL %s (anchor count %d)" % (tag, got))
        return
    C[path] = text.replace(anchor, anchor + line, 1)
    print("  ok   %s" % tag)


GLASS_HELPERS = '''/** 1.192f: 玻璃胶囊统一配色 —— 全站唯一定义，新增玻璃元素一律复用 */
internal fun glassFill(dark: Boolean, selected: Boolean = false): Color =
    if (selected) (if (dark) Color(0xFF2E2E30).copy(0.94f) else Color.White.copy(0.92f))
    else (if (dark) Color.White.copy(0.075f) else Color.Black.copy(0.045f))

internal fun glassBorder(dark: Boolean, selected: Boolean = false): Color =
    if (selected) (if (dark) Color.White.copy(0.10f) else Color.Black.copy(0.05f))
    else (if (dark) Color.White.copy(0.06f) else Color.Black.copy(0.03f))

/**
 * 1.192f: 玻璃胶囊的通用「按住缩放」反馈 —— 与主页横滑条 Chip 完全同一套
 * （spring + StiffnessHigh，无 ripple）。任何玻璃条/玻璃小按钮都该用它，
 * 避免同一种控件在不同页面出现「压暗」与「缩放」两种手感。
 */
@Composable
internal fun Modifier.glassPress(
    shape: Shape,
    fill: Color,
    border: Color,
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh,
        ),
        label = "glassPress",
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clip(shape)
        .background(fill)
        .border(0.6.dp, border, shape)
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
}

'''

# Chip 文档（t14 拟物版描述，已过期）
CHIP_DOC_OLD = """/**
 * 胶囊 chip（横滑条条目：文字 + 可选圆形 logo 或矢量图标）
 *
 * 1.192：iOS 玻璃质感重绘——
 * · 选中 = 抬升的磨砂胶囊（近实心玻璃）：顶部高光边 + 柔和投影 + 主题色文字
 * · 未选 = 极淡半透明胶囊（平铺，无投影），文字取 onSurfaceVariant
 * · 按压有 Q 弹缩放反馈（spring，按下 0.93 → 松手回弹）
 * 尺寸与旧版完全一致（12dp / 7dp 内边距），不改变各页横滑条布局高度。
 */
"""

CHIP_DOC_NEW = """/**
 * 胶囊 chip（横滑条条目：文字 + 可选圆形 logo 或矢量图标）
 *
 * 1.192：iOS 玻璃质感重绘——
 * · 选中 = 平面化的近实心玻璃胶囊（浅色 = 白 / 深色 = #2E2E30），无投影、无渐变
 * · 未选 = 极淡半透明胶囊，文字取 onSurfaceVariant
 * · 按压 spring 缩放反馈（0.90 / StiffnessHigh），无 ripple 压暗
 * 尺寸与旧版完全一致（12dp / 7dp 内边距），不改变各页横滑条布局高度。
 * 配色与按压统一走 glassFill / glassBorder / Modifier.glassPress。
 */
"""

CHIP_OLD_BG = """    val bg = when {
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
CHIP_NEW_BG = """    // 1.192f: 配色收敛到全站唯一的 glassFill / glassBorder
    val bg = glassFill(dark, selected)
    val edge = glassBorder(dark, selected)
"""

# ---------------- #1 帖子详情：只看楼主 / 排序
DETAIL_OLD_ONLYOP = """                                            modifier = Modifier
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(
                                                    if (onlyOp) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                                    else MaterialTheme.colorScheme.surface
                                                )
                                                .clickable { onlyOp = !onlyOp }
                                                .padding(horizontal = 12.dp, vertical = 6.dp),
"""
DETAIL_NEW_ONLYOP = """                                            modifier = Modifier
                                                // 1.192f: 玻璃胶囊 + 与主页 tab 同款 spring 按压（去掉 ripple 压暗）
                                                .glassPress(
                                                    shape = RoundedCornerShape(999.dp),
                                                    fill = glassFill(dark, onlyOp),
                                                    border = glassBorder(dark, onlyOp),
                                                    onClick = { onlyOp = !onlyOp },
                                                )
                                                .padding(horizontal = 12.dp, vertical = 6.dp),
"""

DETAIL_OLD_SORT = """                                            modifier = Modifier
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(MaterialTheme.colorScheme.surface)
                                                .clickable { sortMode = (sortMode + 1) % 3 }
                                                .padding(horizontal = 12.dp, vertical = 6.dp),
"""
DETAIL_NEW_SORT = """                                            modifier = Modifier
                                                // 1.192f: 玻璃胶囊 + 与主页 tab 同款 spring 按压
                                                .glassPress(
                                                    shape = RoundedCornerShape(999.dp),
                                                    fill = glassFill(dark),
                                                    border = glassBorder(dark),
                                                    onClick = { sortMode = (sortMode + 1) % 3 },
                                                )
                                                .padding(horizontal = 12.dp, vertical = 6.dp),
"""

DETAIL_OLD_TOOLS = """                                item(key = "r-tools") {
                                    Row(
"""
DETAIL_NEW_TOOLS = """                                item(key = "r-tools") {
                                    val dark = isAppDarkTheme()
                                    Row(
"""

# ---------------- #2 用户主页按钮加高 / 去雾
USER_OLD_PM = """                LiquidButton(
                    onClick = onPm,
                    backdrop = rememberSolidBackdrop(MaterialTheme.colorScheme.surface),
                    surfaceColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                    height = 32.dp,
                    contentPadding = 13.dp,
                ) {
"""
USER_NEW_PM = """                LiquidButton(
                    onClick = onPm,
                    backdrop = rememberSolidBackdrop(MaterialTheme.colorScheme.surface),
                    surfaceColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f),
                    height = 38.dp,
                    contentPadding = 15.dp,
                ) {
"""

USER_OLD_FOLLOW = """    LiquidButton(
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
USER_NEW_FOLLOW = """    LiquidButton(
        onClick = { if (!busy) onClick() },
        backdrop = rememberSolidBackdrop(MaterialTheme.colorScheme.surface),
        isInteractive = !busy,
        // 1.192f: 未关注不再用 tint（tint 走 Hue 混合 + 75% 叠色，会泛出「一层薄雾」），
        // 直接给实心主题色底面 —— 颜色更实、更接近官方「关注」按钮
        surfaceColor = if (followed) bg else MaterialTheme.colorScheme.primary,
        height = 38.dp,
        contentPadding = 16.dp,
        arrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
    ) {
"""

# ---------------- #3 TabChip 按压手感对齐
USER_OLD_TAB = """        modifier = Modifier
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
"""
USER_NEW_TAB = """        modifier = Modifier
            // 1.192f: 改走 glassPress —— 与主页横滑条 Chip 完全一致的 spring 缩放
            //（此前用 clickable 的默认 ripple，表现成「压暗」，与主页手感不一致）
            .glassPress(
                shape = RoundedCornerShape(999.dp),
                fill = glassFill(dark, selected),
                border = glassBorder(dark, selected),
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
"""

# ---------------- 搜索历史条目 / 信息流设置 chip 统一
SEARCH_OLD_CHIP = """                    Row(
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
SEARCH_NEW_CHIP = """                    Row(
                        Modifier
                            // 1.192f: 统一走 glassPress（spring 缩放，无 ripple）
                            .glassPress(
                                shape = RoundedCornerShape(50),
                                fill = glassFill(dark),
                                border = glassBorder(dark),
                                onClick = { onPick(h) },
                            )
                            .padding(horizontal = 12.dp, vertical = 7.dp),
"""

FILTER_OLD_CHIP = """                            Row(
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
FILTER_NEW_CHIP = """                            Row(
                                Modifier
                                    // 1.192f: 统一走 glassPress（spring 缩放，无 ripple）
                                    .glassPress(
                                        shape = RoundedCornerShape(16.dp),
                                        fill = glassFill(dark),
                                        border = glassBorder(dark),
                                        onClick = { onRemove(kind, w) },
                                    )
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
"""

# ---------------- #4 写评论
ACTION_OLD_PILL = """    val writePillColor =
        if (isLight) Color.White.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.10f)
"""
ACTION_NEW_PILL = """    // 1.192f: 提高不透明度（原 0.55 偏透，字看着发灰）
    val writePillColor =
        if (isLight) Color.White.copy(alpha = 0.78f) else Color.White.copy(alpha = 0.16f)
"""

ACTION_OLD_ARR = """            arrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
"""
ACTION_NEW_ARR = """            // 1.192f: 内容本身已带 8dp Spacer，这里再用 spacedBy 会多出 8dp → 文字偏右
            arrangement = Arrangement.Start,
"""


def main():
    print("== 基建 FeedUi ==")
    add_after(FEED, "import androidx.compose.ui.graphics.Color\n",
              "import androidx.compose.ui.graphics.Shape\n", "F1 Shape import")
    ins_before(FEED, "@Composable\ninternal fun Chip(", GLASS_HELPERS, "F2 glass 基建")
    rep(FEED, CHIP_DOC_OLD, CHIP_DOC_NEW, "F2b Chip 文档更新")
    rep(FEED, CHIP_OLD_BG, CHIP_NEW_BG, "F3 Chip 配色收敛")

    print("== #1 帖子详情 ==")
    add_after(DETAIL, "import com.java.myapplication.ui.components.normalizeCover\n",
              "import com.java.myapplication.ui.components.glassBorder\n"
              "import com.java.myapplication.ui.components.glassFill\n"
              "import com.java.myapplication.ui.components.glassPress\n"
              "import com.java.myapplication.ui.theme.isAppDarkTheme\n", "D1 imports")
    rep(DETAIL, DETAIL_OLD_TOOLS, DETAIL_NEW_TOOLS, "D2 dark 声明")
    rep(DETAIL, DETAIL_OLD_ONLYOP, DETAIL_NEW_ONLYOP, "D3 只看楼主")
    rep(DETAIL, DETAIL_OLD_SORT, DETAIL_NEW_SORT, "D4 排序状态")

    print("== #2 #3 用户主页 ==")
    add_after(USER, "import com.java.myapplication.ui.components.thumbnailUrl\n",
              "import com.java.myapplication.ui.components.glassBorder\n"
              "import com.java.myapplication.ui.components.glassFill\n"
              "import com.java.myapplication.ui.components.glassPress\n", "U1 imports")
    rep(USER, USER_OLD_PM, USER_NEW_PM, "U2 私信加高")
    rep(USER, USER_OLD_FOLLOW, USER_NEW_FOLLOW, "U3 关注去雾+加高")
    rep(USER, USER_OLD_TAB, USER_NEW_TAB, "U4 TabChip 按压对齐")

    print("== 搜索历史 ==")
    add_after(SEARCH, "import com.java.myapplication.ui.components.normalizeCover\n",
              "import com.java.myapplication.ui.components.glassBorder\n"
              "import com.java.myapplication.ui.components.glassFill\n"
              "import com.java.myapplication.ui.components.glassPress\n", "Q1 imports")
    rep(SEARCH, SEARCH_OLD_CHIP, SEARCH_NEW_CHIP, "Q2 历史条目 glassPress")

    print("== 信息流设置 ==")
    add_after(FILTER, "import com.java.myapplication.ui.components.tapGuard\n",
              "import com.java.myapplication.ui.components.glassBorder\n"
              "import com.java.myapplication.ui.components.glassFill\n"
              "import com.java.myapplication.ui.components.glassPress\n", "L1 imports")
    rep(FILTER, FILTER_OLD_CHIP, FILTER_NEW_CHIP, "L2 chip glassPress")

    print("== #4 写评论 ==")
    rep(ACTION, ACTION_OLD_PILL, ACTION_NEW_PILL, "A1 提高不透明度")
    rep(ACTION, ACTION_OLD_ARR, ACTION_NEW_ARR, "A2 文字回左")

    if FAILS:
        print("\n!!! ABORT, nothing written. %d failures:" % len(FAILS))
        for f in FAILS:
            print("   " + f)
        sys.exit(1)

    for path, text in C.items():
        with io.open(path, "w", encoding="utf-8") as f:
            f.write(text)
        print("  wrote %s" % path.split("/")[-1])
    print("FIX17 1.192 OK")


main()