package com.java.myapplication.ui.components

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * 1.224f：自研半屏面板 —— **替代 Material3 `ModalBottomSheet`**。
 *
 * 为什么不用 `ModalBottomSheet`：
 *  · 它是「独立窗口 + 手势拖拽 + 嵌套滚动 + 多段停靠」的重实现，每次开关都要建/拆窗口与手势管线，
 *    真机上打开明显掉帧；
 *  · 而我们的业务只需要「贴底半屏 + 顶部圆角 + 滑入滑出 + 点遮罩关闭」，用不着多段停靠与拖拽。
 *
 * 形态与楼中楼 / 表情搜索那套自研面板完全同构（同一套 [sheetTopCornerShape] + [SheetGrabber] +
 * [tapGuard]）：遮罩 25% 黑随进度淡入淡出；面板贴底、高度 = 屏高 × [fraction]、顶部圆角 =
 * 屏幕物理圆角；入场 300ms / 退场 260ms 缓动平移；系统返回手势优先收面板。
 *
 * ⚠️ 用法：**不要**再写 `if (open) { XxxSheet(...) }` —— 那样面板会被立刻卸载、退场动画播不出来。
 * 直接把 [visible] 传进来即可；面板完全收起时自己什么都不画（不会挡住下层触摸）。
 */
@Composable
internal fun HuzaiHalfSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    fraction: Float,
    zIndex: Float = 1000f,
    showGrabber: Boolean = true,
    /** 面板内容（等价于原 ModalBottomSheet `content` 的 Column 作用域）——必须是最后一个参数 */
    content: @Composable ColumnScope.() -> Unit,
) {
    // 0 = 完全就位；1 = 完全滑出屏底（下方）
    val progress = remember { Animatable(1f) }
    // 退场动画播放期间必须保持挂载（visible 已为 false，但画面还要留在屏上演完）
    var mounted by remember { mutableStateOf(visible) }
    LaunchedEffect(visible) {
        if (visible) {
            mounted = true
            progress.snapTo(1f)
            progress.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
        } else if (mounted) {
            progress.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
            mounted = false
        }
    }

    // 完全收起：什么都不画（否则全屏 Box 会挡住下层触摸）
    if (!mounted && !visible) return

    // 返回手势：面板可见时优先收面板（本组件组合在宿主之后，处理器优先级更高）
    if (visible) {
        PredictiveBackHandler { events ->
            events.collect { }
            onDismiss()
        }
    }

    val p = progress.value
    BoxWithConstraints(Modifier.fillMaxSize().zIndex(zIndex)) {
        // 1.224h（真机反馈）：面板高度按**屏幕高**算，而不是「父容器剩余高」——
        // 数据统计页的面板挂在 tab 内容区里（顶栏 / 记分板 / 分类条之下），容器只剩大半屏，
        // 按容器算 60% 会明显偏矮。这里统一以屏幕高为准，并夹住不超过可用高度。
        val panelH = minOf(LocalConfiguration.current.screenHeightDp.dp * fraction, maxHeight)
        // 遮罩：点面板外任意处收起；随进度淡入淡出
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.25f * (1f - p)))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { onDismiss() },
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(panelH)
                .graphicsLayer { translationY = p * size.height }
                .clip(sheetTopCornerShape())
                .background(MaterialTheme.colorScheme.background)
                .tapGuard()
                .navigationBarsPadding(),
        ) {
            if (showGrabber) {
                // 1.224g（真机反馈）：抓手与标题之间**必须留白** —— 否则标题贴着面板顶边，像没做完。
                // 与 [SheetTopBar] 的观感对齐（抓手 8dp 上留白 + 5dp 条 + 10dp 下留白）。
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 10.dp),
                    contentAlignment = Alignment.Center,
                ) { SheetGrabber() }
            }
            content()
        }
    }
}
