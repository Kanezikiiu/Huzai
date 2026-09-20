package com.java.myapplication.ui.components

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * 1.186: 顶部 Tab 左右滑动切换。
 * - 只消费横向滑动：纵向滑动交给子级列表（LazyColumn / LazyVerticalGrid）滚动，两者互不干扰
 * - 拖动距离超过阈值后「松手」才触发，避免与纵向滚动、点击产生误判
 * - 仅作用于传入的容器（即单个大页面的内容区），不跨大页面
 *
 * @param onPrevious 向右滑（内容右移）→ 上一个 Tab
 * @param onNext 向左滑 → 下一个 Tab
 */
@Composable
fun Modifier.tabSwipeSwitch(
    onPrevious: () -> Unit,
    onNext: () -> Unit,
): Modifier {
    val prev by rememberUpdatedState(onPrevious)
    val next by rememberUpdatedState(onNext)
    val threshold = with(LocalDensity.current) { 56.dp.toPx() }
    return this.pointerInput(Unit) {
        var total = 0f
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = {
                if (total <= -threshold) next() else if (total >= threshold) prev()
                total = 0f
            },
            onDragCancel = { total = 0f },
        ) { _, dragAmount ->
            total += dragAmount
        }
    }
}