package com.java.myapplication.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.java.myapplication.ui.glass.buttonBorder
import com.java.myapplication.ui.glass.buttonFill
import com.java.myapplication.ui.glass.liquidElevation
import com.java.myapplication.ui.glass.liquidPressTransform
import com.java.myapplication.ui.glass.rememberLiquidHighlight
import com.java.myapplication.ui.theme.isAppDarkTheme

/**
 * 1.209：项目统一的「返回按钮」——液态玻璃圆底 + 与「关注」按钮同源的液体按压手感。
 *
 * 背景：项目里原来有 **22 处** 各自手写的返回键（两种形态混用）：
 *   · `IconButton { Icon(ArrowBack) }`（Material，48dp 命中区）；
 *   · `Box(Modifier.size(40.dp).clip(CircleShape).clickable{...}) { Icon(ArrowBack) }`（自绘 40dp 圆）。
 * 两者都只是**裸图标**、无可见底，也没有按压反馈。本组件把它们收口成一份实现，避免继续走样。
 *
 * 手感来源（与 [com.java.myapplication.ui.components.GlassCloseButton]、
 * [com.java.myapplication.ui.glass.LiquidButton] **完全同源**）：
 *   · [rememberLiquidHighlight] → 按下处流体高光（半径 1.5×最小边，BlendMode.Plus）；
 *   · [liquidPressTransform] → 常量 4dp 的「长大」+ 按点方向的各向异性缩放 + 跟随位移。
 *   —— 注意这是「液体」形变，**不是** Tab 栏的整体等比缩放。
 *
 * 底面不采样 backdrop：这些返回键都坐在纯色页面背景上（顶栏），采样没有视觉收益，
 * 反而会引入自采样递归风险。故与 [LiquidButton] 一样只画平面玻璃底 + 描边
 * （[buttonFill] / [buttonBorder]）。
 *
 * @param onClick 返回动作
 * @param modifier 外层修饰（例如 [Box] 内的 `.align(...)`）；尺寸由本组件负责
 * @param size 圆直径，默认 40dp（与改造前自绘命中区一致，保持各页顶栏等高）
 * @param iconSize 箭头图标尺寸，默认 20dp
 */
@Composable
internal fun LiquidIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
) {
    val dark = isAppDarkTheme()
    val highlight = rememberLiquidHighlight()
    Box(
        modifier
            .size(size)
            // ① 液体按压形变（与「关注」同源）
            .liquidPressTransform(highlight)
            // 1.223: 落影 —— 顶栏按钮从页面「浮」起来（真机反馈：此前只是平面玻璃底，显扁）
            .liquidElevation(CircleShape, dark)
            .clip(CircleShape)
            .background(buttonFill(dark))
            .border(0.6.dp, buttonBorder(dark), CircleShape)
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            // 只裁「按下高光」（会超出按钮）
            .then(highlight.modifier)
            .then(highlight.gestureModifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(iconSize),
        )
    }
}

/** 返回按钮：即 [LiquidIconButton] 的「返回箭头」快捷形态（全项目 22 处顶栏共用）。 */
@Composable
internal fun LiquidBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
) = LiquidIconButton(
    icon = Icons.AutoMirrored.Rounded.ArrowBack,
    contentDescription = "返回",
    onClick = onClick,
    modifier = modifier,
    size = size,
    iconSize = iconSize,
)
