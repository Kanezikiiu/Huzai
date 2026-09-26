#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
1.192 第二十批（真机反馈 2 条 + 2 处按钮化）

#1 「按钮有一圈亮亮的边」→ 根因是 `drawBackdrop` 的**默认参数**
   `highlight = DefaultHighlight`（Highlight.Default = 0.5dp 贴边高光描边）
   与 `shadow = DefaultShadow`（投影）。对纯色 backdrop 来说，采样/blur/lens
   全都没有视觉产出，这两样是唯一可见的东西。
#2 「深色模式依旧灰蒙蒙」→ 按钮底色用了 onSurface@0.09（白 9%），
   白低透明度叠在深底上就是一层面纱。改为**实体深灰** + 明确描边。

因此把 LiquidButton 改成「不采样 backdrop 的平面玻璃按钮」：
  · 去掉 drawBackdrop（连带 Highlight/Shadow、vibrancy/blur/lens）
  · 保留 kyant 的按压语言：InteractiveHighlight 流体高光 + 图层形变
  · 配色走新增的 buttonFill / buttonBorder / buttonAccent

顺带把用户确认可以按钮化的两处改掉：
  · 搜索历史条目（SearchPage）
  · 信息流设置关键词 chip（FilterSettingsPage）
"""
import io, re, sys

W = "/data/user/0/com.ai.assistance.operit/files/workspace/2dab7fe4-ceff-4d94-99a0-8e839ec2f3da"
P = W + "/app/src/main/java/com/java/myapplication/ui/pages/"
BTN = W + "/app/src/main/java/com/java/myapplication/ui/glass/LiquidButton.kt"
DETAIL = P + "ThreadDetailPage.kt"
USER = P + "UserProfilePage.kt"
SEARCH = P + "SearchPage.kt"
FILTER = P + "FilterSettingsPage.kt"
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


# ============================================================ 新的 LiquidButton.kt
NEW_BTN = '''package com.java.myapplication.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

/**
 * 1.192h: 按钮的「中性底」。
 *
 * 深色模式用**实体深灰**，而不是低透明度白叠加——白色 9% 叠在深底上会糊成一层
 * 灰蒙蒙的面纱（真机反馈确认）；实体色 + 明确描边才有「有边界的物件」的感觉。
 */
internal fun buttonFill(dark: Boolean): Color =
    if (dark) Color(0xFF2C2C2E) else Color.Black.copy(alpha = 0.06f)

/** 1.192h: 按钮描边 —— 定义边缘，避免灰底与页面底糊在一起 */
internal fun buttonBorder(dark: Boolean): Color =
    if (dark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.05f)

/** 1.192h: 强调态按钮底（如「只看楼主」选中）。深色需要更高不透明度才不发灰 */
internal fun buttonAccent(primary: Color, dark: Boolean): Color =
    primary.copy(alpha = if (dark) 0.30f else 0.16f)

/**
 * 液态玻璃按钮（项目变体）。
 *
 * 与上游 Kyant0/AndroidLiquidGlass 的 `LiquidButton` 相比做了两处**必要适配**：
 *
 * 1. **不采样 backdrop**。上游用 `drawBackdrop` 采样身后的内容，但该 modifier 的默认参数
 *    `highlight = Highlight.Default` / `shadow = Shadow.Default` 会额外画一条 0.5dp 的
 *    **贴边高光**与一层投影——在纯色 backdrop 或自采样场景下，这两样是唯一可见的产出，
 *    表现为「按钮一圈亮边」。而 `vibrancy()` 只是饱和度 ×1.5、`blur`/`lens` 对纯色采样
 *    没有任何产出，采样本身没有视觉收益。
 *    因此这里直接绘制平面玻璃底面 + 描边，配色走 buttonFill / buttonBorder / buttonAccent。
 * 2. **保留 kyant 的按压语言**：`InteractiveHighlight`（按下处流体高光）
 *    + 图层形变（位移 / 各向异性缩放）——这是它区别于 tab 条「整体等比缩放」的地方。
 *
 * @param fill   底面（必填，来自 buttonFill / buttonAccent / 主题色）
 * @param border 描边；不想画时传 Color.Transparent
 */
@Composable
fun LiquidButton(
    onClick: () -> Unit,
    fill: Color,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(50),
    border: Color = Color.Transparent,
    isInteractive: Boolean = true,
    height: Dp = 48.dp,
    contentPadding: Dp = 16.dp,
    arrangement: Arrangement.Horizontal = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    content: @Composable RowScope.() -> Unit
) {
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope = animationScope)
    }
    Row(
        modifier
            .graphicsLayer {
                if (isInteractive && size.height > 0f && size.maxDimension > 0f) {
                    val progress = interactiveHighlight.pressProgress
                    val base = lerp(1f, 1f + 4f.dp.toPx() / size.height, progress)
                    val maxOffset = size.minDimension
                    val offset = interactiveHighlight.offset
                    val initialDerivative = 0.05f
                    translationX = maxOffset * tanh(initialDerivative * offset.x / maxOffset)
                    translationY = maxOffset * tanh(initialDerivative * offset.y / maxOffset)
                    val maxDragScale = 4f.dp.toPx() / size.height
                    val offsetAngle = atan2(offset.y, offset.x)
                    scaleX =
                        base +
                                maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) *
                                (size.width / size.height).fastCoerceAtMost(1f)
                    scaleY =
                        base +
                                maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) *
                                (size.height / size.width).fastCoerceAtMost(1f)
                }
            }
            .clip(shape)
            .background(fill)
            .then(
                if (border.isSpecified && border.alpha > 0f) Modifier.border(0.6.dp, border, shape)
                else Modifier
            )
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .then(
                if (isInteractive) {
                    Modifier
                        .then(interactiveHighlight.modifier)
                        .then(interactiveHighlight.gestureModifier)
                } else {
                    Modifier
                }
            )
            .height(height)
            .padding(horizontal = contentPadding),
        horizontalArrangement = arrangement,
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}
'''

# ============================================================ #2/#3 用户主页
USER_OLD_IMPORTS = """import com.java.myapplication.ui.glass.LiquidButton
import com.java.myapplication.ui.glass.rememberSolidBackdrop
"""
USER_NEW_IMPORTS = """import com.java.myapplication.ui.glass.LiquidButton
import com.java.myapplication.ui.glass.buttonBorder
import com.java.myapplication.ui.glass.buttonFill
"""

USER_OLD_PM = """            if (onPm != null) {
                Spacer(Modifier.width(8.dp))
                // 1.192e: 私信按钮 → 液态玻璃按钮（kyant LiquidButton）
                LiquidButton(
                    onClick = onPm,
                    backdrop = rememberSolidBackdrop(MaterialTheme.colorScheme.surface),
                    surfaceColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f),
                    height = 38.dp,
                    contentPadding = 15.dp,
                ) {
"""
USER_NEW_PM = """            if (onPm != null) {
                val dark = isAppDarkTheme()
                Spacer(Modifier.width(8.dp))
                // 1.192e: 私信按钮 → 液态玻璃按钮（项目变体，不采样 backdrop）
                LiquidButton(
                    onClick = onPm,
                    fill = buttonFill(dark),
                    border = buttonBorder(dark),
                    height = 38.dp,
                    contentPadding = 15.dp,
                ) {
"""

USER_OLD_FOLLOW_HEAD = """private fun FollowButton(followed: Boolean, busy: Boolean, onClick: () -> Unit) {
    val bg = if (followed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    else MaterialTheme.colorScheme.primary
"""
USER_NEW_FOLLOW_HEAD = """private fun FollowButton(followed: Boolean, busy: Boolean, onClick: () -> Unit) {
    val dark = isAppDarkTheme()
    val bg = buttonFill(dark)
"""

USER_OLD_FOLLOW_BTN = """    LiquidButton(
        onClick = { if (!busy) onClick() },
        backdrop = rememberSolidBackdrop(MaterialTheme.colorScheme.surface),
        isInteractive = !busy,
        // 1.192f: 未关注不再用 tint（tint 走 Hue 混合 + 75% 叠色，会泛出「一层薄雾」），
        // 直接给实心主题色底面 —— 颜色更实、更接近官方「关注」按钮
        surfaceColor = if (followed) bg else MaterialTheme.colorScheme.primary,
        height = 38.dp,
"""
USER_NEW_FOLLOW_BTN = """    LiquidButton(
        onClick = { if (!busy) onClick() },
        isInteractive = !busy,
        // 未关注 = 实心主题色；已关注 = 按钮中性底（深色为实体深灰）
        fill = if (followed) bg else MaterialTheme.colorScheme.primary,
        border = if (followed) buttonBorder(dark) else Color.Transparent,
        height = 38.dp,
"""

# ============================================================ #4 写评论
ACTION_OLD_PILL = """    // 1.192f: 提高不透明度（原 0.55 偏透，字看着发灰）
    val writePillColor =
        if (isLight) Color.White.copy(alpha = 0.78f) else Color.White.copy(alpha = 0.16f)
"""
ACTION_NEW_PILL = """    // 1.192h: 深色模式改实体深灰（白 16% 叠在毛玻璃上仍然发灰）
    val writePillColor =
        if (isLight) Color.White.copy(alpha = 0.78f) else Color(0xFF3A3A3C)
"""

ACTION_OLD_BTN = """        // 1.192e: 「写评论」→ 液态玻璃按钮（复用页面真 backdrop，内容保持左对齐）
        LiquidButton(
            onClick = onWrite,
            backdrop = backdrop,
            modifier = Modifier.weight(1f),
            height = 40.dp,
            surfaceColor = writePillColor,
"""
ACTION_NEW_BTN = """        // 1.192e: 「写评论」→ 液态玻璃按钮（内容保持左对齐）
        LiquidButton(
            onClick = onWrite,
            fill = writePillColor,
            modifier = Modifier.weight(1f),
            height = 40.dp,
"""

# ============================================================ #1 帖子详情
DETAIL_OLD_IMPORTS = """// 1.192g: 「只看楼主 / 排序状态」= 按钮 → kyant LiquidButton（不再走 tab 条的 glassPress）
import com.java.myapplication.ui.glass.LiquidButton
import com.java.myapplication.ui.glass.rememberSolidBackdrop
"""
DETAIL_NEW_IMPORTS = """// 1.192g: 「只看楼主 / 排序状态」= 按钮 → LiquidButton（不再走 tab 条的 glassPress）
import com.java.myapplication.ui.glass.LiquidButton
import com.java.myapplication.ui.glass.buttonAccent
import com.java.myapplication.ui.glass.buttonBorder
import com.java.myapplication.ui.glass.buttonFill
import com.java.myapplication.ui.theme.isAppDarkTheme
"""

DETAIL_OLD_BACKDROP = """    // 1.192g: 「只看楼主 / 排序状态」两个按钮的玻璃采样源。
    // 它们位于上面这一层的**内部**（会画进 actionBackdrop），采样 actionBackdrop 会自采样糊掉，
    // 因此用纯色 backdrop —— 与用户主页「私信 / 关注」按钮同款。
    val toolsBackdrop = rememberSolidBackdrop(MaterialTheme.colorScheme.surface)
"""
DETAIL_NEW_BACKDROP = """"""

DETAIL_OLD_TOOLS_HEAD = """                                item(key = "r-tools") {
                                    Row(
"""
DETAIL_NEW_TOOLS_HEAD = """                                item(key = "r-tools") {
                                    val dark = isAppDarkTheme()
                                    Row(
"""

DETAIL_OLD_ONLYOP = """                                        // 1.192g: 「只看楼主 / 排序状态」是**按钮**而不是 tab 条 ——
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
"""
DETAIL_NEW_ONLYOP = """                                        // 1.192g: 「只看楼主 / 排序状态」是**按钮**而不是 tab 条 ——
                                        // tab 条 = 一组互斥选项 + 选中项位置概念；这两个是「独立开关」与「循环切换」，
                                        // 所以用 LiquidButton（流体高光 + 按压形变），与 tab 的 spring 缩放区分。
                                        // 1.192h: 不再采样 backdrop——drawBackdrop 默认带的 Highlight/Shadow
                                        // 就是那圈「亮边」；配色改走 buttonFill / buttonAccent。
                                        LiquidButton(
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
"""

DETAIL_OLD_SORT = """                                        LiquidButton(
                                            onClick = { sortMode = (sortMode + 1) % 3 },
                                            backdrop = toolsBackdrop,
                                            height = 30.dp,
                                            contentPadding = 12.dp,
                                            arrangement = Arrangement.Center,
                                            surfaceColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f),
                                        ) {
"""
DETAIL_NEW_SORT = """                                        LiquidButton(
                                            onClick = { sortMode = (sortMode + 1) % 3 },
                                            fill = buttonFill(dark),
                                            border = buttonBorder(dark),
                                            height = 30.dp,
                                            contentPadding = 12.dp,
                                            arrangement = Arrangement.Center,
                                        ) {
"""

# ============================================================ 搜索历史条目 → 按钮
SEARCH_OLD_IMPORTS = """import com.java.myapplication.ui.glass.LiquidGlassDialog
"""
SEARCH_NEW_IMPORTS = """import com.java.myapplication.ui.glass.LiquidButton
import com.java.myapplication.ui.glass.LiquidGlassDialog
import com.java.myapplication.ui.glass.buttonBorder
import com.java.myapplication.ui.glass.buttonFill
"""

SEARCH_OLD_CHIP = """                    Row(
                        Modifier
                            // 1.192f: 统一走 glassPress（spring 缩放，无 ripple）
                            .glassPress(
                                shape = RoundedCornerShape(50),
                                fill = glassFill(dark),
                                border = glassBorder(dark),
                                onClick = { onPick(h) },
                            )
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
"""
SEARCH_NEW_CHIP = """                    // 1.192h: 「搜索历史条目」是按钮语义（点击执行一次搜索）→ LiquidButton
                    LiquidButton(
                        onClick = { onPick(h) },
                        shape = RoundedCornerShape(50),
                        fill = buttonFill(dark),
                        border = buttonBorder(dark),
                        height = 32.dp,
                        contentPadding = 12.dp,
                        arrangement = Arrangement.Start,
                    ) {
"""

# ============================================================ 信息流设置关键词 → 按钮
FILTER_OLD_IMPORTS = """import com.java.myapplication.ui.components.glassBorder
import com.java.myapplication.ui.components.glassFill
import com.java.myapplication.ui.components.glassPress
"""
FILTER_NEW_IMPORTS = """import com.java.myapplication.ui.glass.LiquidButton
import com.java.myapplication.ui.glass.buttonBorder
import com.java.myapplication.ui.glass.buttonFill
"""

FILTER_OLD_CHIP = """                            Row(
                                Modifier
                                    // 1.192f: 统一走 glassPress（spring 缩放，无 ripple）
                                    .glassPress(
                                        shape = RoundedCornerShape(16.dp),
                                        fill = glassFill(dark),
                                        border = glassBorder(dark),
                                        onClick = { onRemove(kind, w) },
                                    )
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
"""
FILTER_NEW_CHIP = """                            // 1.192h: 关键词 chip 是按钮语义（点击删除该词）→ LiquidButton
                            LiquidButton(
                                onClick = { onRemove(kind, w) },
                                shape = RoundedCornerShape(16.dp),
                                fill = buttonFill(dark),
                                border = buttonBorder(dark),
                                height = 30.dp,
                                contentPadding = 12.dp,
                                arrangement = Arrangement.Start,
                            ) {
"""


def main():
    print("== LiquidButton 重写（去 backdrop 采样） ==")
    with io.open(BTN, "w", encoding="utf-8") as f:
        f.write(NEW_BTN)
    print("  ok   LiquidButton.kt rewritten (%d bytes)" % len(NEW_BTN))

    print("== #2/#3 用户主页 ==")
    rep(USER, USER_OLD_IMPORTS, USER_NEW_IMPORTS, "U1 imports")
    rep(USER, USER_OLD_PM, USER_NEW_PM, "U2 私信")
    rep(USER, USER_OLD_FOLLOW_HEAD, USER_NEW_FOLLOW_HEAD, "U3 FollowButton 头")
    rep(USER, USER_OLD_FOLLOW_BTN, USER_NEW_FOLLOW_BTN, "U4 FollowButton 按钮")

    print("== #4 写评论 ==")
    rep(ACTION, ACTION_OLD_PILL, ACTION_NEW_PILL, "A1 深色实体深灰")
    rep(ACTION, ACTION_OLD_BTN, ACTION_NEW_BTN, "A2 去 backdrop")

    print("== #1 帖子详情 ==")
    rep(DETAIL, DETAIL_OLD_IMPORTS, DETAIL_NEW_IMPORTS, "D1 imports")
    rep(DETAIL, DETAIL_OLD_BACKDROP, DETAIL_NEW_BACKDROP, "D2 去 toolsBackdrop")
    rep(DETAIL, DETAIL_OLD_TOOLS_HEAD, DETAIL_NEW_TOOLS_HEAD, "D3 dark 声明")
    rep(DETAIL, DETAIL_OLD_ONLYOP, DETAIL_NEW_ONLYOP, "D4 只看楼主")
    rep(DETAIL, DETAIL_OLD_SORT, DETAIL_NEW_SORT, "D5 排序状态")

    print("== 搜索历史条目 → 按钮 ==")
    rep(SEARCH, SEARCH_OLD_IMPORTS, SEARCH_NEW_IMPORTS, "Q1 imports")
    rep(SEARCH, SEARCH_OLD_CHIP, SEARCH_NEW_CHIP, "Q2 历史条目")

    print("== 信息流设置关键词 → 按钮 ==")
    rep(FILTER, FILTER_OLD_IMPORTS, FILTER_NEW_IMPORTS, "L1 imports")
    rep(FILTER, FILTER_OLD_CHIP, FILTER_NEW_CHIP, "L2 chip")

    if FAILS:
        print("\n!!! ABORT, nothing written. %d failures:" % len(FAILS))
        for f in FAILS:
            print("   " + f)
        sys.exit(1)

    for path in [USER, ACTION, DETAIL, SEARCH, FILTER]:
        with io.open(path, "w", encoding="utf-8") as f:
            f.write(C[path])
        print("  wrote %s" % path.split("/")[-1])
    print("FIX19 1.192 OK")


main()