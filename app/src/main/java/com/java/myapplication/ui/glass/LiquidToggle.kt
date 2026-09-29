package com.java.myapplication.ui.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import com.java.myapplication.ui.theme.isAppDarkTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.flow.collectLatest

/**
 * 1.204：官方 LiquidToggle「完整」移植（Kyant0/AndroidLiquidGlass 示例组件）。
 *
 * 原版：https://github.com/Kyant0/AndroidLiquidGlass
 *   app/src/commonMain/kotlin/com/kyant/backdrop/catalog/components/LiquidToggle.kt
 *
 * 1.204a（真机反馈修正）：先前为规避 drawBackdrop 的默认 Highlight/Shadow，
 * 我把滑块的毛玻璃折射与速度形变一并砍掉了 —— 结果「按下」与「拖动」的观感都和官方不同。
 * 现恢复完整实现，这些正是官方版的核心：
 *   · 滑块平时 = 纯白；按下 progress ↑ → 白色渐隐，露出「毛玻璃折射」（onDrawSurface）
 *   · 拖动时按「速度」做横向拉伸/纵向压缩（layerBlock 里的 velocity 项）
 *   · 按下时的 blur / lens / chromaticAberration 折射与 Highlight / Shadow / InnerShadow
 * 唯一保留的适配：强调色由 iOS 系统绿（#34C759）改为 colorScheme.primary（跟随主题）。
 *
 * 1.220：**当前暂无调用点** —— 帖子详情的「只看楼主」已改为与排序条同款的
 * 分段控件（SortBar「全部 / 楼主」），本组件按需保留备用（液体玻璃手感较有价值，
 * 设置页类开关将来可直接复用）。若长期无人使用，可整体删除。
 *
 * @param backdrop 折射源。帖子详情页传它自己的 actionBackdrop（采样列表内容那一层）。
 */
@Composable
internal fun LiquidToggle(
    selected: () -> Boolean,
    onSelect: (Boolean) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    val dark = isAppDarkTheme()
    val accentColor = MaterialTheme.colorScheme.primary
    // 1.204d：滑块上「人形图标」的关态色 —— 与页面里次要文字同色。
    // 1.219（方案 A）：开启时过渡到**白色**（此时滑块已变成主题色）。
    val glyphOffColor = MaterialTheme.colorScheme.onSurfaceVariant
    // 轨道底色：与右侧「排序条」容器取**完全同一个 token**（FeedUi.kt:443
    // `if (dark) Color.White.copy(0.07f) else Color.Black.copy(0.05f)`），
    // 两种模式下叠底亮度都严格同档 —— 这是「开关与排序 tab 透明度一致」的唯一可靠做法。
    //
    // 1.204f（真机截图亮度实测）：浅色原为 @20%，叠底后 224，而排序条容器是 236
    //   —— 同高度（40.00dp vs 39.98dp）下一块暗 26 级、一块暗 14 级，前者显得更"重"。
    // 1.205（真机反馈）：浅色 @12% 仍差 3~4 级；深色更严重 —— 原来写死
    //   `#787880@36%`（叠底 ≈ 54）比排序条容器（White@7%，叠底 ≈ 34）**亮 20 级**，
    //   深色下开关像一块"没适配深色"的浅灰亮块，且与右侧排序条明显不同档。
    //   现改为与排序条容器同 token：深色叠底 ≈ 34（↓20 级）、浅色叠底 = 排序条同值。
    val trackColor =
        if (dark) Color.White.copy(0.07f)
        else Color.Black.copy(0.05f)

    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val dragWidth = with(density) { 20f.dp.toPx() }

    val animationScope = rememberCoroutineScope()
    var didDrag by remember { mutableStateOf(false) }
    var fraction by remember { mutableFloatStateOf(if (selected()) 1f else 0f) }

    val dampedDragAnimation = remember(animationScope) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = fraction,
            valueRange = 0f..1f,
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            // 按下时滑块横向拉伸 1.5×（iOS 手感）
            pressedScale = 1.5f,
            onDragStarted = {},
            onDragStopped = {
                if (didDrag) {
                    fraction = if (targetValue >= 0.5f) 1f else 0f
                    onSelect(fraction == 1f)
                    didDrag = false
                } else {
                    // 未拖动 = 轻点 → 直接翻转
                    fraction = if (selected()) 0f else 1f
                    onSelect(fraction == 1f)
                }
            },
            onDrag = { _, dragAmount ->
                if (!didDrag) {
                    didDrag = dragAmount.x != 0f
                }
                val delta = dragAmount.x / dragWidth
                fraction =
                    if (isLtr) (fraction + delta).fastCoerceIn(0f, 1f)
                    else (fraction - delta).fastCoerceIn(0f, 1f)
            },
        )
    }
    LaunchedEffect(dampedDragAnimation) {
        snapshotFlow { fraction }
            .collectLatest { f ->
                dampedDragAnimation.updateValue(f)
            }
    }
    LaunchedEffect(selected) {
        snapshotFlow { selected() }
            .collectLatest { isSelected ->
                val target = if (isSelected) 1f else 0f
                if (target != fraction) {
                    fraction = target
                    dampedDragAnimation.animateToValue(target)
                }
            }
    }

    val trackBackdrop = rememberLayerBackdrop()
    Box(
        modifier,
        contentAlignment = Alignment.CenterStart,
    ) {
        // 轨道：纯色 lerp（关 → 开 = 灰 → 主题色）；自身作为内层折射源
        Box(
            Modifier
                .layerBackdrop(trackBackdrop)
                .clip(Capsule())
                // 1.219（方案 A）：轨道恒为中性玻璃（与排序条容器同 token），
                // 主题色改由「滑块」承载 —— 与排序条「中性容器 + 选中胶囊」同语汇，
                // 消除 on 态「整条实心强调色」与右侧排序条并排的割裂感。
                .drawBehind {
                    drawRect(trackColor)
                }
                .size(76f.dp, 40f.dp),
        )
        // 滑块：毛玻璃折射（官方原版实现）
        Box(
            Modifier
                // 1.204g（真机反馈）：内缩 4dp → 3dp，与右侧排序条选中胶囊完全对齐
                // （FeedUi.kt:434 inset = 3.dp）。这样滑块高度 = 40 - 3*2 = 34dp、
                // 圆角 = 半高 17dp，与排序条胶囊的 50×34 / RoundedCornerShape(17.dp) 一致；
                // 轨道宽度仍是 3 + 50 + 20 + 3 = 76dp，行布局不变。
                .graphicsLayer {
                    val padding = 3f.dp.toPx()
                    translationX =
                        if (isLtr) lerp(padding, padding + dragWidth, dampedDragAnimation.value)
                        else lerp(-padding, -(padding + dragWidth), dampedDragAnimation.value)
                }
                .semantics {
                    role = Role.Switch
                }
                .then(dampedDragAnimation.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(
                        backdrop,
                        rememberBackdrop(trackBackdrop) { drawBackdrop ->
                            val progress = dampedDragAnimation.pressProgress
                            val scaleX = lerp(2f / 3f, 0.75f, progress)
                            val scaleY = lerp(0f, 0.75f, progress)
                            scale(scaleX, scaleY) {
                                drawBackdrop()
                            }
                        }
                    ),
                    shape = { Capsule() },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        blur(8f.dp.toPx() * (1f - progress))
                        lens(
                            5f.dp.toPx() * progress,
                            10f.dp.toPx() * progress,
                            chromaticAberration = true
                        )
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Ambient.copy(
                            width = Highlight.Ambient.width / 1.5f,
                            blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                            alpha = progress
                        )
                    },
                    shadow = {
                        // 1.204e（真机截图实测）：原来是官方原值的 Shadow(4.dp)。滑块内缩只有
                        // 4dp，而 4dp 光晕会往外扩约 5dp —— 多出的 ~1dp 溢出轨道上下缘，
                        // 把开关的**绘制轮廓**从 40dp 撑到 42.1dp（截图实测 79px vs
                        // 排序条 75px = 40dp），于是「开关看起来比排序高」。
                        // 1.204g：内缩改为 3dp（对齐排序条胶囊）后同步收到 1.5dp，
                        // 扩张 ≈1.9dp < 3dp 内缩 → 不可能再溢出，轮廓严格 = 轨道 40dp；
                        // 按下时的层次由 highlight / innerShadow / 1.5× 拉伸承担，不受影响。
                        Shadow(
                            radius = 1.5.dp,
                            color = Color.Black.copy(alpha = 0.05f)
                        )
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(
                            radius = 4f.dp * progress,
                            alpha = progress
                        )
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 50f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        // 1.211（真机反馈）：深色下纯白滑块偏亮，与返回/关闭按钮的暗色语言不一致
                        // → 深色把滑块压到 @80%（叠在深色轨道上 ≈ 柔灰 #D2D2D6），浅色仍纯白。
                        val thumbBase = if (dark) 0.80f else 1f
                        // 1.219（方案 A）：滑块面层随开关在「白 → 主题色」之间过渡 ——
                        // on 态 = 中性轨道里的一个主题色胶囊（与排序条选中胶囊同语汇）。
                        val face = lerp(
                            Color.White.copy(alpha = thumbBase),
                            accentColor,
                            dampedDragAnimation.value,
                        )
                        drawRect(face.copy(alpha = face.alpha * (1f - progress)))
                        // 1.204d：「只看楼主」的人形图标——画在滑块自己的这一层面里，
                        // 因此会跟着按下/拖动的形变一起缩放，而不是浮在玻璃外面；
                        // 按下时白色面层渐隐，图标仍保持清晰。
                        // 颜色随滑块位置在「中性灰 → 主题色」之间过渡，状态一眼可读。
                        val t = 18f.dp.toPx()
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        // 1.219（方案 A）：图标随滑块底色反转 —— 滑块白 → 灰图标；滑块主题色 → 白图标
                        val glyphColor =
                            lerp(glyphOffColor, Color.White, dampedDragAnimation.value)
                        // 头
                        drawCircle(
                            color = glyphColor,
                            radius = t * 0.21f,
                            center = Offset(cx, cy - t * 0.29f)
                        )
                        // 肩：半圆，弦落在下半部，与头一起构成「人形」
                        drawArc(
                            color = glyphColor,
                            startAngle = 180f,
                            sweepAngle = 180f,
                            useCenter = true,
                            topLeft = Offset(cx - t * 0.35f, cy - t * 0.05f),
                            size = Size(t * 0.70f, t * 1.10f)
                        )
                    }
                )
                .size(50f.dp, 34f.dp)
        )
    }
}