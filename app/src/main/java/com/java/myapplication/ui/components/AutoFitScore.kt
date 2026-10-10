package com.java.myapplication.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 1.199b：**自适应字号的比分**（赛程卡 / 记分板共用）。
 *
 * 真机反馈：记分处空间有限（两侧是队名），固定字号在窄屏或长比分（如 `2（VFA） : 7`）时
 * 会被挤到第二行。这里改成「**单行不换行 + 溢出即逐级缩小**」：
 * 每次 ×0.92（下限 11sp），放不下就缩一号再测，直到落进一行；空间富余时仍是原字号。
 *
 * 实现刻意不用 `BasicText(autoSize = ...)`（实验 API、跨版本风险），
 * 而是用 `onTextLayout` 的 `didOverflowWidth` 反馈收敛 —— 最多追几次即稳定。
 *
 * 抽成公共组件的原因：赛程卡（[com.java.myapplication.ui.pages.ScheduleList]）与
 * 对局详情的记分板（[com.java.myapplication.ui.pages.MatchDetailPage]）都要用，
 * 同名私有函数放在同一包内会「Conflicting overloads」。
 */
@Composable
internal fun AutoFitScore(
    text: String,
    maxFontSize: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    var size by remember(text) { mutableFloatStateOf(maxFontSize) }
    Text(
        text = text,
        modifier = modifier,
        fontSize = size.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        maxLines = 1,
        softWrap = false,
        onTextLayout = { r ->
            if (r.didOverflowWidth) size = (size * 0.92f).coerceAtLeast(11f)
        },
    )
}