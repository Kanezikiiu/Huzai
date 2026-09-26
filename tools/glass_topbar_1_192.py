#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
1.192 第十二批：顶部 Tab「iOS 玻璃质感」改造
  Part A  FeedUi.kt  —— 新增必要 import
  Part B  FeedUi.kt  —— Chip 重绘为玻璃胶囊（选中=抬升磨砂 + 主题色文字 + 投影；未选=极淡半透明）
  Part C  FeedUi.kt  —— SortBar 重绘为 iOS 分段控件（玻璃容器 + 滑动选中胶囊 + spring）
  Part D  MatchDetailPage.kt —— 赛事评分二级页「分类条」文字下划线 → 玻璃胶囊横滑条
全部按唯一锚点整段替换，并带命中数断言（原子式）。
"""
import io, re, sys

W = "/data/user/0/com.ai.assistance.operit/files/workspace/2dab7fe4-ceff-4d94-99a0-8e839ec2f3da"
FEED = W + "/app/src/main/java/com/java/myapplication/ui/components/FeedUi.kt"
MATCH = W + "/app/src/main/java/com/java/myapplication/ui/pages/MatchDetailPage.kt"


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


# ---------------------------------------------------------------- Part A
IMPORTS_ANCHOR = "import androidx.compose.foundation.shape.RoundedCornerShape\n"
IMPORTS_NEW = IMPORTS_ANCHOR + """import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import com.java.myapplication.ui.theme.isAppDarkTheme
import kotlin.math.roundToInt
"""

# ---------------------------------------------------------------- Part B
NEW_CHIP = '''/**
 * 胶囊 chip（横滑条条目：文字 + 可选圆形 logo 或矢量图标）
 *
 * 1.192：iOS 玻璃质感重绘——
 * · 选中 = 抬升的磨砂胶囊（近实心玻璃）：顶部高光边 + 柔和投影 + 主题色文字
 * · 未选 = 极淡半透明胶囊（平铺，无投影），文字取 onSurfaceVariant
 * · 按压有 Q 弹缩放反馈（spring，按下 0.93 → 松手回弹）
 * 尺寸与旧版完全一致（12dp / 7dp 内边距），不改变各页横滑条布局高度。
 */
@Composable
internal fun Chip(
    text: String,
    logoUrl: String? = null,
    /** 1.191: 可选矢量图标（如「收藏专区」的星标）；有 logoUrl 时以 logo 优先 */
    leadingIcon: ImageVector? = null,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val dark = isAppDarkTheme()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "chipPress",
    )
    val shape = RoundedCornerShape(50)
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
    val contentColor =
        if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (selected) Modifier.shadow(
                    elevation = 5.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = Color.Black.copy(0.5f),
                    spotColor = Color.Black.copy(0.5f),
                ) else Modifier
            )
            .clip(shape)
            .background(bg)
            .border(0.6.dp, edge, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        logoUrl?.let {
            AsyncImage(
                model = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(20.dp).clip(CircleShape),
            )
            Spacer(Modifier.width(6.dp))
        }
        // 1.191: 无 logo 时可显示矢量图标（与文字同色系）
        if (logoUrl == null && leadingIcon != null) {
            Icon(
                leadingIcon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = contentColor,
            maxLines = 1,
        )
    }
}
'''

# ---------------------------------------------------------------- Part C
NEW_SORTBAR = '''/**
 * 排序子 Tab（最新回复 / 最新发布 / 24小时榜）
 *
 * 1.192：由「文字 + 下划线」改为 iOS 分段控件——
 * 玻璃容器（半透明底 + 细描边）+ 等宽分段 + 滑动选中胶囊（spring Q 弹）。
 * 与顶部的话题/赛事玻璃胶囊同源质感，但形态更「轻」，构成二级层级。
 */
@Composable
internal fun SortBar(
    sorts: List<SortTab>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    if (sorts.isEmpty()) return
    val dark = isAppDarkTheme()
    val count = sorts.size
    val idx = sorts.indexOfFirst { it.url == selected }.coerceAtLeast(0)
    val density = LocalDensity.current
    val shape = RoundedCornerShape(10.dp)
    val segShape = RoundedCornerShape(8.dp)
    val slide = remember { Animatable(idx.toFloat()) }
    LaunchedEffect(idx) {
        slide.animateTo(
            idx.toFloat(),
            spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
        )
    }
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        val segW = maxWidth / count
        val segWpx = with(density) { segW.toPx() }
        val inset = 3.dp
        val insetPx = with(density) { inset.toPx() }
        val barH = 34.dp
        Box(
            Modifier
                .fillMaxWidth()
                .height(barH)
                .clip(shape)
                .background(if (dark) Color.White.copy(0.07f) else Color.Black.copy(0.05f))
                .border(
                    0.6.dp,
                    if (dark) Color.White.copy(0.08f) else Color.White.copy(0.55f),
                    shape,
                ),
        ) {
            // 滑动选中胶囊
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            (slide.value * segWpx + insetPx).roundToInt(),
                            insetPx.roundToInt(),
                        )
                    }
                    .width(segW - inset * 2)
                    .height(barH - inset * 2)
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
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(barH),
            ) {
                sorts.forEach { s ->
                    val active = s.url == selected
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { onSelect(s.url) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            s.title,
                            fontSize = 13.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (active) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
'''

# ---------------------------------------------------------------- Part D
MATCH_NEW_BAR = '''                // 分类条可横向滚动（和平精英等战队多的赛事 Tab 超宽会挤爆固定 Row）
                // 1.192: 由「文字 + 下划线」改为玻璃胶囊横滑条（与三大页顶部横滑条同款质感）
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clipToBounds(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items(tabs, key = { it.first }) { (key, title) ->
                        Chip(text = title, selected = key == selected) { onGroupChange(key) }
                    }
                }
'''


def main():
    feed = read(FEED)

    # ---- Part A : imports
    assert IMPORTS_ANCHOR in feed, "imports anchor missing"
    feed = sub_once(feed, re.escape(IMPORTS_ANCHOR), IMPORTS_NEW, "A imports")

    # ---- Part B/C : replace SortBar + Chip by whole-span anchors
    sbar_anchor = "/** 排序子 Tab（最新回复/最新发布/24小时榜） */"
    chip_anchor = "/** 胶囊 chip（横滑条条目：文字 + 可选圆形 logo 或矢量图标） */"
    page_anchor = "/** 页面顶部标题栏（各页共用）：状态栏避让 + 左标题 + 右侧 40dp 槽位（保证各页顶部高度一致） */"
    assert sbar_anchor in feed, "SortBar anchor missing"
    assert chip_anchor in feed, "Chip anchor missing"
    assert page_anchor in feed, "PageHeader anchor missing"

    feed = sub_once(
        feed,
        r"(?s)" + re.escape(sbar_anchor) + r".*?" + re.escape(chip_anchor),
        NEW_SORTBAR + "\n" + chip_anchor,
        "B/C SortBar+Chip",
    )
    feed = sub_once(
        feed,
        r"(?s)" + re.escape(chip_anchor) + r".*?" + re.escape(page_anchor),
        NEW_CHIP + "\n" + page_anchor,
        "C Chip body",
    )
    write(FEED, feed)

    # ---- Part D : MatchDetail 分类条
    match = read(MATCH)
    pat = (
        r"(?s)                // 分类条可横向滚动（和平精英等战队多的赛事 Tab 超宽会挤爆固定 Row）\n"
        r".*?\n                }\n"
        r"                Spacer\(Modifier\.height\(6\.dp\)\)"
    )
    match = sub_once(
        match,
        pat,
        MATCH_NEW_BAR + "                Spacer(Modifier.height(6.dp))",
        "D MatchDetail 分类条",
    )
    write(MATCH, match)

    print("GLASS-TOPBAR 1.192 OK")


main()
