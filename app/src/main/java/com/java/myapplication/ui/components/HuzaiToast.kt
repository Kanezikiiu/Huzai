package com.java.myapplication.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.java.myapplication.ui.glass.buttonBorder
import com.java.myapplication.ui.glass.buttonFill
import com.java.myapplication.ui.glass.liquidElevation
import com.java.myapplication.ui.theme.isAppDarkTheme

/**
 * 1.223：**全局统一提示**（toast）。
 *
 * 背景（真机反馈）：全站提示此前各自为政——提示位置有「顶部居中 / 底部居中」两种，
 * 底部距还有 70/90/96/120dp 四种；时长有 1.5 / 2.0 / 2.2 / 2.6s 四种（"收藏专区"那条约 2.6s 偏长）；
 * 而且出入场**没有任何动画**（文案直接出现、直接消失）。
 *
 * 现在统一为：
 *  · 位置：屏幕**顶部居中**（状态栏下方 12dp）——不与悬浮 Tab 栏 / 底部输入框打架；
 *  · 时长：固定 [DURATION_MS]；
 *  · 动效：**Q 弹出场**（spring MediumBouncy：缩放 0.86→1 轻微过冲 + 淡入），退场淡出 + 轻微缩小。
 *
 * 用法：任意位置 `HuzaiToast.show("已收藏")`；宿主 [HuzaiToastHost] 由 App 根层渲染一次。
 */
object HuzaiToast {
    /** 统一展示时长（毫秒） */
    const val DURATION_MS = 1800L

    /** 退场动画时长——清空文案前先等它播完，避免文字先消失 */
    private const val EXIT_MS = 260L

    var message by mutableStateOf<String?>(null)
        private set

    /** 每次展示自增：同一条文案连续触发也能重新入场（Q 弹再来一次） */
    var ticket by mutableIntStateOf(0)
        private set

    fun show(msg: String) {
        if (msg.isBlank()) return
        message = msg
        ticket++
    }

    internal fun clear() {
        message = null
    }
}

/** 全局提示宿主：在 App 根 Box 的**最后一个子节点**渲染，天然盖过所有页面与二级页。 */
@Composable
internal fun HuzaiToastHost() {
    val msg = HuzaiToast.message
    val ticket = HuzaiToast.ticket
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(ticket) {
        if (ticket == 0 || msg == null) return@LaunchedEffect
        visible = true
        kotlinx.coroutines.delay(HuzaiToast.DURATION_MS)
        visible = false
        kotlinx.coroutines.delay(260L)
        HuzaiToast.clear()
    }
    val text = msg ?: return
    val dark = isAppDarkTheme()
    // 1.223n: 深浅两套样式——深色黑底白字；浅色白底黑字（裸白底在浅色页面上立不住，
    // 所以补 0.6dp 描边 + 与按钮同源的柔和落影）。
    val bg = buttonFill(dark)
    val fg = if (dark) Color.White else Color(0xFF1A1A1A)
    // Q 弹：出场 spring 轻微过冲；出场淡出 + 缩小
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.86f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "toastScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(180),
        label = "toastAlpha",
    )
    Box(
        Modifier
            // 1.223n: 必须 fillMaxSize —— 只 fillMaxWidth 时宿主盒高度=内容高、会被放在根 Box 顶部，
            // 「BottomCenter + 距底」就失效跑到了状态栏。纯布局盒（无背景/无手势）不会挡住点击。
            .fillMaxSize()
            // 位置：屏幕中下方（距底 180dp）——不压标题栏、也不与悬浮 Tab 栏重叠
            .padding(bottom = 180.dp, start = 24.dp, end = 24.dp)
            .graphicsLayer {
                // 1.223o: 底部锚点（默认是中心）——出场「从下往上」长出来、退场向底边收回，
                // 与「屏幕中下方」的位置最搭，方向感明确。
                transformOrigin = TransformOrigin(0.5f, 1f)
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Text(
            text,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
            color = fg,
            modifier = Modifier
                .liquidElevation(RoundedCornerShape(50), dark)
                .clip(RoundedCornerShape(50))
                .background(bg)
                .border(0.6.dp, buttonBorder(dark), RoundedCornerShape(50))
                .padding(horizontal = 16.dp, vertical = 9.dp),
        )
    }
}
