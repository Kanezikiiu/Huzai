package com.java.myapplication.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.java.myapplication.ui.theme.isAppDarkTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle

/**
 * 1.191: Liquid Glass 弹窗（移植自 Kyant0/AndroidLiquidGlass 的 `DialogContent`，与底部
 * Tab 栏同源），并在此之上补了出入场动画：
 *  · 卡片 = `drawBackdrop` 毛玻璃（colorControls 提亮 + blur + lens 深度折射）+ 48dp 大圆角；
 *  · 按钮 = 48dp 高 Capsule 胶囊，取消用低透明白、确认用主题色；
 *  · 遮罩 = 深色半透明，点空白处关闭；
 *  · 入场 = MediumBouncy 弹簧缩放（0.9 → 1，带过冲回弹）+ 遮罩淡入；
 *  · 出场 = 150ms 快速缩回 + 淡出，**动画播完才回调 [onDismiss]**（宿主据此卸载）。
 *
 * **必须是窗口内叠加层**（不要包在 `Dialog()` 里）：背景采样依赖同一窗口的
 * [com.kyant.backdrop.backdrops.LayerBackdrop] 记录层，跨窗口取不到像素。
 * 调用方需为「弹窗背后的内容」挂上 `.layerBackdrop(x)`，并把同一个 x 传进来；
 * 且弹窗必须是那个记录层节点的**后续兄弟**（画在它之后），否则会采到自己。
 */
@Composable
fun LiquidGlassCard(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    zIndex: Float = 1100f,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    val isLight = !isAppDarkTheme()
    val containerColor =
        if (isLight) Color(0xFFFAFAFA).copy(0.5f)
        else Color(0xFF121212).copy(0.5f)
    val dimColor =
        if (isLight) Color(0xFF29293A).copy(0.23f)
        else Color(0xFF121212).copy(0.56f)

    // 入场：卡片走弹性（过冲 = Q弹手感），遮罩只做线性淡入（不跟着过冲）
    val pop = remember { Animatable(0f) }
    val scrim = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        pop.animateTo(
            1f,
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        )
    }
    LaunchedEffect(Unit) { scrim.animateTo(1f, tween(180)) }
    // 出场：先播动画，播完再通知宿主卸载
    LaunchedEffect(closing) {
        if (!closing) return@LaunchedEffect
        pop.animateTo(0f, tween(150))
        scrim.animateTo(0f, tween(150))
        onDismiss()
    }
    val close: () -> Unit = { if (!closing) closing = true }

    Box(modifier.fillMaxSize().zIndex(zIndex)) {
        // 遮罩：点空白 = 关闭
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = scrim.value }
                .background(dimColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { close() },
        )
        Column(
            Modifier
                .align(Alignment.Center)
                .padding(horizontal = 40f.dp, vertical = 24f.dp)
                .graphicsLayer {
                    // 0.9 → 1（弹性过冲到 ≈1.01 再回落），透明度跟着淡入
                    val t = pop.value
                    val s = 0.9f + 0.1f * t
                    scaleX = s
                    scaleY = s
                    alpha = t.coerceIn(0f, 1f)
                }
                // 1.191: 平板/横屏下限制卡片宽度（手机上可用宽度本就 < 400dp，无影响）
                .widthIn(max = 400f.dp)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedRectangle(48f.dp) },
                    effects = {
                        colorControls(
                            brightness = if (isLight) 0.2f else 0f,
                            saturation = 1.5f,
                        )
                        blur(16f.dp.toPx())
                        lens(24f.dp.toPx(), 48f.dp.toPx(), depthEffect = true)
                    },
                    highlight = { Highlight.Plain },
                    onDrawSurface = { drawRect(containerColor) },
                )
                .fillMaxWidth(),
        ) {
            content(close)
        }
    }
}

/** 弹窗按钮：48dp 高 Capsule 胶囊（accent = 主题色填充） */
@Composable
fun LiquidGlassButton(
    text: String,
    accent: Boolean,
    modifier: Modifier = Modifier,
    contentColor: Color = if (isAppDarkTheme()) Color.White else Color.Black,
    onClick: () -> Unit,
) {
    val isLight = !isAppDarkTheme()
    // 1.191: 非 accent（取消）按钮原为「白 20%」——叠在浅色毛玻璃卡上几乎看不见。
    // 改成 onSurface 的 12%（深色档 16%）：既是「灰按钮」的语义，胶囊形状也清晰可辨。
    val idleColor = MaterialTheme.colorScheme.onSurface
        .copy(alpha = if (isLight) 0.12f else 0.16f)
    Row(
        modifier
            .clip(Capsule())
            .background(if (accent) MaterialTheme.colorScheme.primary else idleColor)
            .clickable(onClick = onClick)
            .height(48f.dp)
            .padding(horizontal = 16f.dp),
        horizontalArrangement = Arrangement.spacedBy(4f.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text,
            style = TextStyle(
                if (accent) MaterialTheme.colorScheme.onPrimary else contentColor,
                16f.sp,
                if (accent) FontWeight.Medium else FontWeight.Normal,
            ),
        )
    }
}

/** 确认弹窗（标题 + 正文 + 取消/确认） */
@Composable
fun LiquidGlassDialog(
    backdrop: Backdrop,
    title: String,
    message: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    zIndex: Float = 1100f,
) {
    val isLight = !isAppDarkTheme()
    val contentColor = if (isLight) Color.Black else Color.White

    LiquidGlassCard(
        backdrop = backdrop,
        modifier = modifier,
        zIndex = zIndex,
        onDismiss = onDismiss,
    ) { close ->
        BasicText(
            title,
            // 1.191: 左内边距与正文/按钮统一 24dp —— 之前 28dp 会让标题比正文右缩 4dp
            Modifier.padding(24f.dp, 24f.dp, 24f.dp, 12f.dp),
            style = TextStyle(contentColor, 24f.sp, FontWeight.Medium),
        )
        BasicText(
            message,
            Modifier
                .then(
                    // 深色档文字需要「加亮」而非普通叠加（与上游实现一致）
                    if (isLight) Modifier
                    else Modifier.graphicsLayer(blendMode = BlendMode.Plus)
                )
                .padding(24f.dp, 12f.dp, 24f.dp, 12f.dp),
            style = TextStyle(contentColor.copy(0.68f), 15f.sp),
            maxLines = 5,
        )
        Row(
            Modifier
                .padding(24f.dp, 12f.dp, 24f.dp, 24f.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16f.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LiquidGlassButton(
                text = dismissText,
                accent = false,
                modifier = Modifier.weight(1f),
                contentColor = contentColor,
                onClick = close,
            )
            LiquidGlassButton(
                text = confirmText,
                accent = true,
                modifier = Modifier.weight(1f),
                onClick = {
                    onConfirm()
                    close()
                },
            )
        }
    }
}