package com.java.myapplication.ui.glass

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.lerp
import com.java.myapplication.ui.theme.isAppDarkTheme
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
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
 * 1.198：**通用开关控件** —— 官方 LiquidToggle 的「无图标」版本，供设置页复用。
 *
 * 原版（Kyant0/AndroidLiquidGlass 示例组件 `LiquidToggle`）的形态已核对：
 *   · 轨道 64×28dp，滑块 40×24dp，左右内缩 2dp，滑动行程 20dp；
 *   · 轨道色 = `lerp(关态灰, 强调色, fraction)` —— 关态中性灰、开态强调色；
 *   · 滑块恒为白色；按下时白色面层渐隐，露出**毛玻璃折射**（blur + lens + 色散）；
 *   · 按下放大 1.5×，拖动时按速度做各向异性形变（iOS 手感）；
 *   · 轻点 = 直接翻转；拖动 = 越过中线定夺。
 *
 * ## 折射源为什么要这么拼（踩坑记录）
 *
 * 1.198b（真机反馈：两个频道页**点开即闪退**）—— 上一版把「所在页面的内容层」当折射源传进来，
 * 而这些开关本身**就画在那个层里面**，于是变成「层读取自己」的自采样递归，第一帧即崩。
 * 1.198c（真机反馈：修完闪退后按压**完全没有玻璃感**）—— 那次我把整个 drawBackdrop 砍掉了，
 * 滑块退回实心白块（等于没有玻璃）。
 * 1.198d（真机反馈：按下效果不对）—— 只采「轨道层」也不行：轨道是**半透明**的，
 * 采到的几乎是一片透明，按下去就变成「在轨道上抠了个洞」，而不是一块玻璃。
 *
 * 最终与官方示例同构、且**绝不递归**的写法（本项目 `LiquidSlider` + DisplaySettingsPage 已在用）：
 * **合成背景层 `rememberCanvasBackdrop { drawRect(区域底色) }` + 自己的轨道层**。
 *   · 画布层是「画」出来的纯色，不采样任何真实图层 → 拿去给同层子孙采样也永远不会自引用；
 *   · 轨道层与滑块是兄弟节点（先记录、后采样）→ 同样安全。
 * 与官方唯一的差别：官方采的是「整屏内容」，这里采的是「开关所在区域的底色 + 轨道」——
 * 因为这些设置行背后本来就只有纯色，采真图层除了闪退不会有任何额外像素。
 *
 * 渲染管线（与官方逐行一致）：白面层随按压渐隐 → blur + lens + 色散 → Ambient 高光 →
 * 内外阴影 → 1.5× 放大与速度形变。
 *
 * @param checked 当前状态
 * @param onCheckedChange 状态变更回调（轻点 / 拖动越过中线各触发一次）
 * @param enabled 置灰不可点（如「至少保留一个频道」时的固定频道开关）
 */
@Composable
internal fun GlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val dark = isAppDarkTheme()
    val accentColor = MaterialTheme.colorScheme.primary
    // 关态轨道：与官方 / 本项目 LiquidSlider 取同一组值（浅 #787878@20%，深 #787880@36%）
    val trackColor =
        if (dark) Color(0xFF787880).copy(alpha = 0.36f)
        else Color(0xFF787878).copy(alpha = 0.20f)
    // 玻璃的「背景层」：开关所在区域的底色（画布 backdrop，不采样任何真实图层）
    // —— 与 DisplaySettingsPage 给 LiquidSlider 传 sliderSurface 的做法完全一致
    val surfaceColor = MaterialTheme.colorScheme.background

    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val dragWidth = with(density) { 20f.dp.toPx() }

    val animationScope = rememberCoroutineScope()
    var didDrag by remember { mutableStateOf(false) }
    var fraction by remember { mutableFloatStateOf(if (checked) 1f else 0f) }

    // 用 rememberUpdatedState 兜住「本组件记忆在首次组合」带来的闭包过期问题：
    // DampedDragAnimation 的回调只创建一次，若直接捕获 checked / onCheckedChange，
    // 轻点翻转时读到的会是首次组合的旧值（表现为「点一下没反应」）。
    val currentChecked by rememberUpdatedState(checked)
    val currentOnChange by rememberUpdatedState(onCheckedChange)

    val dampedDragAnimation = remember(animationScope) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = fraction,
            valueRange = 0f..1f,
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            // 按下时滑块放大 1.5×（iOS 手感）
            pressedScale = 1.5f,
            onDragStarted = {},
            onDragStopped = {
                if (didDrag) {
                    fraction = if (targetValue >= 0.5f) 1f else 0f
                    currentOnChange(fraction == 1f)
                    didDrag = false
                } else {
                    // 未拖动 = 轻点 → 直接翻转
                    fraction = if (currentChecked) 0f else 1f
                    currentOnChange(fraction == 1f)
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
    // 外部状态变化（其它入口改了同一个设置）→ 动画跟上
    LaunchedEffect(Unit) {
        snapshotFlow { currentChecked }
            .collectLatest { isChecked ->
                val target = if (isChecked) 1f else 0f
                if (target != fraction) {
                    fraction = target
                    dampedDragAnimation.animateToValue(target)
                }
            }
    }

    // 折射源 = 【所在区域底色（画布层）】+【本开关自己的轨道层】：
    //   · 画布层：合成出来的纯色背景，**不采样任何真实图层** → 绝无自采样递归；
    //   · 轨道层：与滑块是兄弟节点（先记录、后采样）→ 同样安全。
    // 这正是官方示例（背景层 + 轨道层）与官方在项目内的既有移植（LiquidSlider）同构的写法，
    // 唯一区别是「整屏内容」换成了「所在区域底色」——因为这些开关背后本来就只有纯色。
    val surfaceBackdrop = rememberCanvasBackdrop { drawRect(surfaceColor) }
    val trackBackdrop = rememberLayerBackdrop()
    Box(
        // 不可点：整体淡出（与 Material Switch 的灰化语义一致）
        modifier.alpha(if (enabled) 1f else 0.45f),
        contentAlignment = Alignment.CenterStart,
    ) {
        // 轨道：关态灰 → 开态强调色；整条轨道自己作为折射层
        Box(Modifier.layerBackdrop(trackBackdrop)) {
            Box(
                Modifier
                    .clip(Capsule())
                    .drawBehind {
                        drawRect(lerp(trackColor, accentColor, dampedDragAnimation.value))
                    }
                    .size(64f.dp, 28f.dp),
            )
        }
        // 滑块：官方原版渲染 —— 毛玻璃折射 + 渐隐白面 + 高光/内外阴影 + 放大/速度形变
        Box(
            Modifier
                .graphicsLayer {
                    val padding = 2f.dp.toPx()
                    translationX =
                        if (isLtr) lerp(padding, padding + dragWidth, dampedDragAnimation.value)
                        else lerp(-padding, -(padding + dragWidth), dampedDragAnimation.value)
                }
                .semantics {
                    role = Role.Switch
                    if (!enabled) disabled()
                }
                // 置灰时不接手势
                .then(
                    if (enabled) dampedDragAnimation.modifier.consumeToggleTouches()
                    else Modifier
                )
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(
                        surfaceBackdrop,
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
                        // 内缩 2dp，光晕收在 1.5dp 内 → 绘制轮廓不外溢轨道（28dp）
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
                        // 纯白滑块：按下时面层渐隐，露出下面的玻璃折射
                        drawRect(Color.White.copy(alpha = 1f - progress))
                    }
                )
                .size(40f.dp, 24f.dp),
        )
    }
}

/**
 * 1.198：吞掉「按下 / 抬起」两个事件（**刻意不动中间的移动事件**）。
 *
 * 开关常嵌在「整行可点」的设置行里（`FixedChannelRow`、显示与阅读页的
 * 「滚动时自动隐藏底栏」行）。DampedDragAnimation 本身不消费指针事件，
 * 于是同一次轻点会被父级 clickable 也收到 —— 开关翻一次、父行再翻一次，
 * 净效果就是「点了没反应」（本项目此前在书源启停开关上踩过同类坑）。
 *
 * 为什么不整段消费：DampedDragAnimation 的拖动循环里有 `if (change.isConsumed) return null`，
 * 一旦把中间的移动事件也吞掉，拖动会立刻被判成取消。因此只消费 down / up：
 *   · 轻点 —— 父级 clickable 因事件被消费而取消，开关自己翻一次（唯一一次）；
 *   · 拖动 —— 移动事件照常送达，阻尼拖动与「越过中线定夺」全部保留。
 */
private fun Modifier.consumeToggleTouches(): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.fastFirstOrNull { it.id == down.id } ?: break
            if (!change.pressed) {
                change.consume()
                break
            }
        }
    }
}