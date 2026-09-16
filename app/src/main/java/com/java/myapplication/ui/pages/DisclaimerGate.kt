package com.java.myapplication.ui.pages

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 1.155 首次启动免责声明门禁。
 *
 * 规则（产品确认）：
 * · 首次使用必须**同意**才能进入应用；同意后持久化（HupuPrefs.disclaimerAccepted），
 *   此后正常启动不再拦截，卸载/清除数据后重新出现；
 * · 不同意 = 点「不同意」按钮或按系统返回键 → 回调 onExit 退出应用；
 * · **点弹窗以外区域不再视为拒绝**（1.154 严格口径误触代价太高，现改为宽容口径：
 *   点窗外只是没反应，弹窗保持不动）；
 * · **必须把声明正文滑动到底部**才允许同意：未到底时同意按钮不可用、文字显示
 *   「请先滑动到底部」；到底后变为主色「同意并继续」并可点。
 *   （正文不足一屏时视作已到底，不会把用户卡死。）
 */
@Composable
fun DisclaimerGate(
    onAgree: () -> Unit,
    onExit: () -> Unit,
) {
    // 轻微入场：缩放 + 淡入（与项目其他弹层手感一致）
    val appear = remember { Animatable(0f) }
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        appear.animateTo(1f, tween(220))
        appeared = true
    }
    // 系统返回 = 不同意
    PredictiveBackHandler { events ->
        events.collect { }
        onExit()
    }
    // 阅读到底判定：maxValue 未测量时是 Int.MAX_VALUE（视为未到底）；
    // 测量后若正文不足一屏 maxValue = 0 → 直接算已到底，避免无从滑动时被卡死。
    // 留 6px 余量（惯性滑动 / 取整误差）。
    val scroll = rememberScrollState()
    val readToBottom by remember {
        derivedStateOf {
            val max = scroll.maxValue
            max != Int.MAX_VALUE && (max <= 0 || scroll.value >= max - 6)
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // 点弹窗以外区域：消耗事件但**不做任何事**（宽容口径，不再视为拒绝）
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) { },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .graphicsLayer {
                    val s = 0.94f + 0.06f * appear.value
                    scaleX = s
                    scaleY = s
                    alpha = appear.value
                }
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                // 阻断遮罩点击：弹窗内部任意点按都不穿透到外层
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { }
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "免责声明",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "使用「虎仔」前请阅读以下内容。滑动到底部后可点击「同意并继续」，"
                        + "表示你已阅读、理解并接受全部条款。",
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 240.dp, max = 420.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(
                    DISCLAIMER_TEXT,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.verticalScroll(scroll),
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .clickable { onExit() }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "不同意",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val canAgree = appeared && readToBottom
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (canAgree) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable(enabled = canAgree) { onAgree() }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (readToBottom) "同意并继续" else "请先滑动到底部",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (canAgree) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                if (readToBottom) "不同意将退出应用，下次启动仍会显示本页"
                else "请将上面的声明滑动到最底部，以确认你已阅读完。",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}