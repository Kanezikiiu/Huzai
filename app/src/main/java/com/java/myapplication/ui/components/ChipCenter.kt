package com.java.myapplication.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.withFrameNanos
import kotlin.math.abs

/**
 * NoBouncy spring: velocity-continuous easing, feels smoother than tween for
 * scroll glides; stiffness ~500 gives a natural glide that scales with distance.
 */
private val CenterSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = 500f,
)

/**
 * Horizontally center the chip at [index] (official-app style), smoothly.
 * - Visible: one spring glide to viewport center.
 * - Off-screen: a single continuous animated pass THROUGH intermediate chips
 *   (animateScrollToItem, no teleport), then measure and micro-glide to center.
 * Call from LaunchedEffect(selectedValue).
 */
suspend fun LazyListState.animateChipCenterTo(index: Int) {
    if (index < 0) return
    val info = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    if (info == null) {
        animateScrollToItem(index)
        withFrameNanos { }
        val i2 = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
        if (i2 != null) {
            val viewport = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
            val delta = i2.offset + i2.size / 2f - viewport / 2f
            if (abs(delta) > 1f) animateScrollBy(delta, CenterSpring)
        }
        return
    }
    val viewport = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
    val delta = info.offset + info.size / 2f - viewport / 2f
    if (abs(delta) > 1f) {
        animateScrollBy(delta, CenterSpring)
    }
}