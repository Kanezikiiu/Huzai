package com.java.myapplication.ui.components

import android.os.Build
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.java.myapplication.ui.glass.buttonBorder
import com.java.myapplication.ui.glass.buttonFill
import com.java.myapplication.ui.glass.liquidElevation
import com.java.myapplication.ui.glass.liquidPressTransform
import com.java.myapplication.ui.glass.rememberLiquidHighlight
import com.java.myapplication.ui.theme.isAppDarkTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule

/**
 * 1.206（真机反馈）：半屏 sheet 的顶部 chrome 统一定义。
 *
 * 演变：
 *  · 原来顶部**只有一根 36×4dp 的抓手条，且抓手与内容之间没有留白** —— 像"没做完"；
 *  · 1.206 先加了标题行 +「共 N 条回复」+ 细分隔线；
 *  · 1.206b（真机反馈）：**去掉分隔线与右侧计数**，计数位置改成一颗
 *    **kyant 毛玻璃圆形关闭按钮**（要悬浮感）—— 抓手 + 左标题 + 右关闭是 iOS 的标准形态。
 *
 * 楼中楼（帖子详情 [com.java.myapplication.ui.pages.FloorSheet]）与
 * 孙评论（选手评分 [com.java.myapplication.ui.pages.SubCommentSheet]）共用本组件，
 * 避免两处各写一份后逐渐走样。
 */
@Composable
internal fun SheetGrabber() {
    Box(
        Modifier
            .width(40.dp)
            .height(5.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)),
    )
}

/**
 * sheet 顶部整块：抓手条 + （左标题 / 右毛玻璃关闭按钮）。
 *
 * 关闭按钮外观与「返回按钮」[LiquidBackButton] 完全同源（同一组 buttonFill / buttonBorder），
 * 不再单独做毛玻璃采样 —— 这样深色/浅色下两者观感一致。
 */
@Composable
internal fun SheetTopBar(
    title: String,
    onClose: () -> Unit,
) {
    // 1.208: 标题行的顶端距面板顶边约 25dp（抓手 10dp 留白 + 5dp 高 + 10dp 上间距）。
    // 大圆角机型上按此深度加大左右留白，标题 / 关闭按钮始终落在圆角内侧的直边区。
    val side = rememberSheetSideInset(depth = 25.dp)
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            SheetGrabber()
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(
                    start = maxOf(16.dp, side),
                    end = maxOf(12.dp, side),
                    top = 10.dp,
                    bottom = 8.dp,
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            GlassCloseButton(onClick = onClose)
        }
    }
}

/**
 * 圆形关闭按钮（直径 30dp）——外观与「返回按钮」一致（扁平玻璃底 + 描边 + 液体按压手感）。
 *
 * 玻璃（悬浮感）复用 kyant 引擎：
 *  · blur + lens（深度折射）+ colorControls（提亮底层，玻璃才"亮"得起来）
 *  · Highlight.Plain（顶部环境高光）+ Shadow（外投影 = 离面感）+ InnerShadow（内缘厚度）
 *  · 面层 = 白 @62%（浅）/ 白 @14%（深），细描边定边界
 *
 * 按压手感（1.208 真机反馈修正）：**改用与「关注」按钮（[LiquidButton]）同源的
 * [liquidPressTransform] + [rememberLiquidHighlight]** —— 按下处流体高光 + 常量 4dp 的
 * "长大" + 按点方向的各向异性形变。
 * 之前这里是自己写的「spring 缩到 0.90」，那恰好是 Tab 栏的手感（整体等比缩小），
 * 用户反馈"不像按钮，像顶部 tab 条"——现已统一，且形变实现只有一份（抽在 LiquidButton 里）。
 */
@Composable
internal fun GlassCloseButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = isAppDarkTheme()
    val highlight = rememberLiquidHighlight()

    Box(
        modifier
            .size(30.dp)
            // 与「返回按钮」/「关注」完全同源的液体按压形变
            .liquidPressTransform(highlight)
            // 1.223j: 补落影——与返回按钮同源同参数（此前只有玻璃底、没有浮起感）
            .liquidElevation(CircleShape, dark)
            .clip(CircleShape)
            .background(buttonFill(dark))
            .border(0.6.dp, buttonBorder(dark), CircleShape)
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Button,
            ) { onClick() }
            // 只裁「按下高光」（会超出按钮）
            .then(highlight.modifier)
            .then(highlight.gestureModifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            HupuIcons.Close,
            contentDescription = "关闭",
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            modifier = Modifier.size(16.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// 半屏面板顶部圆角：与「屏幕物理圆角」保持一致
// ---------------------------------------------------------------------------

/** 设备未上报屏幕圆角时的回退值（保持与改造前一致，避免出现方正角）。 */
private val DefaultSheetCorner = 20.dp

/**
 * 1.207（真机反馈）：读「屏幕物理圆角」半径，让半屏面板的顶部圆角**总是与屏幕一致**。
 *
 * 之前三处半屏面板（楼中楼 ×2、表情搜索）都写死 `RoundedCornerShape(topStart = 20.dp)`——
 * 在圆角更大的机型上，面板的小圆角与屏幕大圆角"不同心"，一眼能看出是两套曲线。
 * 现在改为运行时读取：
 *
 *  · 取值 = `WindowInsets.getRoundedCorner(pos).radius`（API 31+），四个位置取**最大值**
 *    —— 各 API 版本对 position 的枚举顺序有过不一致的记载，取最大可完全规避；
 *  · 半径单位是 px，按当前 density 换算成 dp 后直接用作顶部圆角，圆心与屏幕圆角同心；
 *  · 设备没上报（平板 / 模拟器 / 折叠屏外屏等返回 0）→ 回退 [DefaultSheetCorner]，
 *    不会退化成直角；
 *  · `minSdk = 24`，所以有 SDK 31 判断；低版本走回退值。
 */
@Composable
internal fun rememberScreenCornerRadius(): Dp {
    val view = LocalView.current
    val density = LocalDensity.current
    // 首帧同步读一次（面板都是用户交互后才组合，此时窗口早已 attach，正常能读到）
    var radiusPx by remember(view) { mutableFloatStateOf(readScreenCornerPx(view)) }
    // 极少数情况首帧 rootWindowInsets 仍为 null → 等一帧补读一次
    LaunchedEffect(view) {
        if (radiusPx <= 0f) {
            withFrameNanos { }
            radiusPx = readScreenCornerPx(view)
        }
    }
    val fallbackPx = with(density) { DefaultSheetCorner.toPx() }
    val effective = if (radiusPx > 0f) radiusPx else fallbackPx
    return with(density) { effective.toDp() }
}

/** 半屏面板的顶部圆角形状：左上 / 右上 = 屏幕物理圆角。 */
@Composable
internal fun sheetTopCornerShape(): Shape {
    val r = rememberScreenCornerRadius()
    return remember(r) { RoundedCornerShape(topStart = r, topEnd = r) }
}

/**
 * 1.208（真机反馈）：面板顶部两角是半径 R 的四分之一圆。顶部组件（标题 / 关闭按钮 /
 * 搜索框 / 取消）原先按固定小留白贴边摆放——圆角适中时没问题，但**大圆角机型**上
 * 曲线会压到这些组件（右侧「取消」尤甚），看着「格格不入」。
 *
 * 这里给出「距面板顶边 [depth] 处」曲线相对侧边的**水平内缩量**，供顶部行据此加大
 * 左右留白，使组件始终落在圆角内侧的直边区（另加 [extra] 作视觉余量）。
 * 圆角适中 / 深度已超过半径时结果为 0，即顶部留白保持原样。
 */
internal fun sheetCornerInset(radius: Dp, depth: Dp, extra: Dp = 6.dp): Dp {
    if (radius <= 0.dp || depth >= radius) return 0.dp
    val r = radius.value
    val y = depth.value
    val dx = r - kotlin.math.sqrt((r * r - (r - y) * (r - y)).coerceAtLeast(0f))
    return (dx + extra.value).dp
}

/** 便捷：按「屏幕物理圆角」算距面板顶边 [depth] 处所需的侧向留白（0 表示无需额外留白）。 */
@Composable
internal fun rememberSheetSideInset(depth: Dp): Dp {
    val r = rememberScreenCornerRadius()
    return remember(r, depth) { sheetCornerInset(r, depth) }
}

/**
 * 读屏幕圆角半径（px）。读不到 / 低版本 / 上报 0 → 返回 0f（由调用方回退）。
 *
 * 注意 `getRoundedCorner` 需要 API 31；本项目 minSdk 24，故必须做版本判断。
 */
private fun readScreenCornerPx(view: View): Float {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return 0f
    val insets = view.rootWindowInsets ?: return 0f
    var max = 0f
    for (pos in 0..3) {
        val c = runCatching { insets.getRoundedCorner(pos) }.getOrNull() ?: continue
        if (c.radius > max) max = c.radius.toFloat()
    }
    return max
}