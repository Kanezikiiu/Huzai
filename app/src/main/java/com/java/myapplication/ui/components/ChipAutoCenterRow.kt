package com.java.myapplication.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * 1.267：**选中项自动居中的横滑 chip 条**（首页话题条 / 专区排序条同款手感）。
 *
 * 为什么不用 `LazyRow + animateChipCenterTo`：
 * 那个实现在「目标不可见」时会走**两段**（先 animateScrollToItem 冲过去，再微调居中），
 * 观感是一顿一顿的。这里改为自己按 chip 的实测中心点算目标偏移，
 * 用「当前 → 目标」的**单段**弹簧滑行（NoBouncy + stiffness 500，与 ChipCenter 里同参数）。
 *
 * 用法：
 * ```
 * val center = rememberChipAutoCenter()
 * ChipAutoCenterRow(center = center, selectedIndex = selIdx) {
 *     tabs.forEachIndexed { i, t -> ChipSlot(center, i) { Chip(t, selected = ..., onClick = ...) } }
 * }
 * ```
 */
private val ChipGlideSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = 500f,
)

@Stable
internal class ChipAutoCenter {
    val scroll = ScrollState(0)
    var viewport by mutableIntStateOf(0)
    private val centers = mutableStateMapOf<Int, Float>()

    fun onPlaced(index: Int, coords: LayoutCoordinates) {
        centers[index] = coords.positionInParent().x + coords.size.width / 2f
    }

    suspend fun centerOn(index: Int) {
        if (index < 0 || viewport <= 0) return
        val c = centers[index] ?: return
        val target = (c - viewport / 2f).coerceIn(0f, scroll.maxValue.toFloat())
        val delta = target - scroll.value.toFloat()
        if (abs(delta) > 1f) scroll.animateScrollBy(delta, ChipGlideSpring)
    }
}

@Composable
internal fun rememberChipAutoCenter(): ChipAutoCenter = remember { ChipAutoCenter() }

/** 横滑容器：内容由调用方用 [ChipSlot] 包住每个 chip。 */
@Composable
internal fun ChipAutoCenterRow(
    center: ChipAutoCenter,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    horizontalArrangement: androidx.compose.foundation.layout.Arrangement.Horizontal =
        androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    content: @Composable RowScope.() -> Unit,
) {
    LaunchedEffect(selectedIndex, center.viewport) { center.centerOn(selectedIndex) }
    Row(
        modifier
            .fillMaxWidth()
            // 先量视口宽（必须在 horizontalScroll 之前，否则量到的是内容宽）
            .onSizeChanged { center.viewport = it.width }
            .horizontalScroll(center.scroll),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = verticalAlignment,
        content = content,
    )
}

/** 单个 chip 的插槽：把自身中心点报给 [center]。 */
@Composable
internal fun ChipSlot(center: ChipAutoCenter, index: Int, content: @Composable () -> Unit) {
    Box(Modifier.onGloballyPositioned { center.onPlaced(index, it) }) { content() }
}