package com.java.myapplication.ui.glass

import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.java.myapplication.ui.components.HupuIcons
import com.java.myapplication.ui.theme.isAppDarkTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule

/**
 * 1.337：**独立的**底栏搜索按钮（不并进 Tab 胶囊内部）。
 *
 * 按设置摆在底部 Tab 胶囊的左侧或右侧，与胶囊之间留 8dp 间距。
 * 材质刻意与 Tab 栏保持同源，避免"两个控件不像一套"：
 *  · 高度 64dp —— 与 Tab 栏外层一致（栏本体 64dp，内部条目 56dp）
 *  · 底色 —— 与 Tab 栏同一个 containerColor（浅色 #FAFAFA α0.4 / 深色 #121212 α0.4）
 *  · 效果 —— vibrancy + blur(8dp) + lens(24dp)，与 Tab 栏外层完全一致
 *  · 形状 —— Capsule，宽高相等（正圆）
 */
@Composable
fun GlassSearchFab(
    backdrop: Backdrop,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val light = !isAppDarkTheme()
    val containerColor =
        if (light) Color(0xFFFAFAFA).copy(0.4f)
        else Color(0xFF121212).copy(0.4f)

    // 1.339：点击效果换成项目里**已验证的按钮手感** —— 与顶栏返回键 / 搜索键完全同源：
    //  `liquidPressTransform`（4dp「长大」+ 按点方向的各向异性缩放）+ `liquidElevation`（落影）
    //  + `InteractiveHighlight`（按下高光）。
    //
    // 为什么不用上一版那套「整体缩到 0.90 + 图标变淡」：
    //  · 整体等比缩小是 **Tab 栏** 的手感（GlassTab / DampedDragAnimation），按钮用它会"不伦不类"；
    //  · 液态形变才是本项目的「按钮」语义 —— 底栏搜索钮虽然摆在 Tab 栏旁边，
    //    但它是一个**动作按钮**，不是 Tab。
    val dark = !light
    val highlight = rememberLiquidHighlight()

    Box(
        modifier
            .size(64.dp)
            // ① 液态按压形变（与「关注」/返回键同源）
            .liquidPressTransform(highlight)
            // ② 落影：从底栏「浮」起来，与顶栏按钮同一套影高
            .liquidElevation(CircleShape, dark)
            // ③ 玻璃底：仍是底栏那套 backdrop 毛玻璃（外观与 Tab 胶囊一致）
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(8f.dp.toPx())
                    lens(24f.dp.toPx(), 24f.dp.toPx())
                },
                onDrawSurface = { drawRect(containerColor) },
            )
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            // ④ 按下高光（会超出按钮，必须放在 clip 之后单独裁）
            .then(highlight.modifier)
            .then(highlight.gestureModifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            HupuIcons.SearchOutlined,
            contentDescription = "搜索",
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}