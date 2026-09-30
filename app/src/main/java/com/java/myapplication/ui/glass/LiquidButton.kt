package com.java.myapplication.ui.glass

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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import com.java.myapplication.ui.theme.isAppDarkTheme
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

/**
 * 1.192j: 按钮的「中性底」。
 *
 * 深色模式用**实体深灰**——低透明度白叠在深底上会糊成一层灰蒙蒙的面纱（真机反馈确认）。
 * 浅色模式用**纯白**——半透明黑会发灰、显脏（真机反馈确认：那两个小按钮看着「灰底」）；
 * 白底在浅色页面上立不住，所以靠稍强一点的描边（见 buttonBorder）来定义边界。
 */
internal fun buttonFill(dark: Boolean): Color =
    if (dark) Color(0xFF2C2C2E) else Color.White

/**
 * 1.192j: 按钮描边 —— 白底在浅色页面上完全靠它立住，故浅色由 0.05 提到 0.08。
 */
internal fun buttonBorder(dark: Boolean): Color =
    if (dark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f)

/**
 * 1.208：创建「液态按钮」的按下高光状态（内部自带 spring 动画 scope）。
 *
 * 与 [LiquidButton] 同源——任何想拥有「按钮手感」的控件都该用它，而不是自己写等比缩放。
 */
@Composable
internal fun rememberLiquidHighlight(): InteractiveHighlight {
    val animationScope = rememberCoroutineScope()
    return remember(animationScope) {
        InteractiveHighlight(animationScope = animationScope)
    }
}
/**
 * 1.223：液态按钮的**落影（悬浮感）**。
 *
 * 真机反馈：返回键 / 三点键 / 私信 / 关注这些按钮只有「平面玻璃底 + 描边」，
 * 贴在纯色页面上时看不出层级，像印在纸上的色块；加一层柔和落影后才从页面上「浮」起来
 * （iOS 上这类按钮都是这个观感）。
 *
 * 颜色策略：
 *  · 浅色主题——黑色 12%/16%（过重会显脏，这也是项目里其它投影问题的通用结论）；
 *  · 深色主题——黑色 55%（深底上的黑影需要更重才看得见）。
 *
 * @param shape 与按钮同形（圆形按钮传 CircleShape）
 * @param elevation 影高，默认 6dp（够「浮」但不至于像卡片）
 */
internal fun Modifier.liquidElevation(
    shape: Shape,
    dark: Boolean,
    elevation: Dp = 8.dp,
): Modifier = shadow(
    elevation = elevation,
    shape = shape,
    clip = false,
    ambientColor = Color.Black.copy(alpha = if (dark) 0.60f else 0.15f),
    spotColor = Color.Black.copy(alpha = if (dark) 0.60f else 0.20f),
)

/**
 * 1.208：液态按钮的**按压形变** —— 常量 4dp 的「长大」+ 按点方向的各向异性缩放 + 位移。
 *
 * 抽到 Modifier 上供其它控件复用（如半屏面板的圆形关闭按钮），并让 [LiquidButton] 自身
 * 也改用本函数，保证「按钮手感」只有一份实现、两处永不走样。
 *
 * 注意：这是「液体」形变，**不是**整体等比缩小 —— 后者是 Tab 栏的手感
 * （见 GlassTab / DampedDragAnimation 的 pressScale），两者观感完全不同。
 */
internal fun Modifier.liquidPressTransform(
    highlight: InteractiveHighlight,
    enabled: Boolean = true,
): Modifier = graphicsLayer {
    if (enabled && size.height > 0f && size.maxDimension > 0f) {
        val progress = highlight.pressProgress
        val base = lerp(1f, 1f + 4f.dp.toPx() / size.height, progress)
        val maxOffset = size.minDimension
        val offset = highlight.offset
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
 *    因此这里直接绘制平面玻璃底面 + 描边，配色走 buttonFill / buttonBorder（或主题色）。
 * 2. **保留 kyant 的按压语言**：`InteractiveHighlight`（按下处流体高光）
 *    + 图层形变（位移 / 各向异性缩放）——这是它区别于 tab 条「整体等比缩放」的地方。
 *    （1.208：形变已抽为 [liquidPressTransform]，供其它按钮同源复用。）
 *
 * @param fill   底面（必填，来自 buttonFill 或主题色）
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
    val interactiveHighlight = rememberLiquidHighlight()
    Row(
        modifier
            .liquidPressTransform(interactiveHighlight, enabled = isInteractive)
            // 1.223: 落影 —— 按钮从页面「浮」起来（真机反馈：此前像印在纸上的色块）
            .liquidElevation(shape, isAppDarkTheme())
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
