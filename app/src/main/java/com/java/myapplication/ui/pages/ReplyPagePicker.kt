package com.java.myapplication.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.java.myapplication.ui.glass.LiquidButton
import com.java.myapplication.ui.glass.LiquidGlassButton
import com.java.myapplication.ui.glass.LiquidGlassCard
import com.java.myapplication.ui.glass.liquidPressTransform
import com.java.myapplication.ui.glass.rememberLiquidHighlight
import com.java.myapplication.ui.theme.isAppDarkTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy

/**
 * 1.227: 评论区「跳转页码」弹窗。
 *
 * 为什么不是纯网格页码：虎扑长帖动辄几百上千页（`replyTotalPages` 实测可到四位数），
 * 网格既放不下也没法点。所以这里是**三件套**：
 *
 *  1. 候选页码条（可横滑）：首页 - 当前 ±1 - 末页，中间用「…」表示跳过；
 *  2. 手动输入框（数字键盘）：任意页直达；
 *  3. 取消 / 跳转。
 *
 * 交互与视觉完全复用「退出登录」那款弹窗（`LiquidGlassCard`：弹性入场 + 淡出出场），
 * 页码 chip 与「写评论」同源（`LiquidButton`），保证手感一致。
 */
@Composable
internal fun ReplyPagePicker(
    backdrop: Backdrop,
    pageNow: Int,
    pageTotal: Int,
    onJump: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val total = pageTotal.coerceAtLeast(1)
    val current = pageNow.coerceIn(1, total)
    val pages = remember(current, total) { pageCandidates(current, total) }
    var input by remember { mutableStateOf("") }
    val typed = input.trim().toIntOrNull()?.coerceIn(1, total)

    val onSurface = MaterialTheme.colorScheme.onSurface
    val primary = MaterialTheme.colorScheme.primary
    val chipIdle = onSurface.copy(alpha = 0.08f)

    LiquidGlassCard(backdrop = backdrop, onDismiss = onDismiss) { close ->
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp)) {
            // 标题 / 副标题都居左（与全站弹窗一致）
            Text(
                "跳转页码",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = onSurface,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "第 $current 页 · 共 $total 页",
                fontSize = 12.sp,
                color = onSurface.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start,
            )
            Spacer(Modifier.height(16.dp))

            // ---------- 候选页码 ----------
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                pages.forEach { p ->
                    if (p == null) {
                        Text(
                            "…",
                            fontSize = 13.sp,
                            color = onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.width(18.dp),
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        val selected = p == current
                        LiquidButton(
                            onClick = { onJump(p) },
                            fill = if (selected) primary else chipIdle,
                            shape = RoundedCornerShape(50),
                            height = 38.dp,
                            contentPadding = 15.dp,
                            arrangement = Arrangement.Center,
                        ) {
                            Text(
                                "$p",
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else onSurface,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            // 1.234：上一页 / 下一页快捷 —— 也是为了让面板不至于只有两三个 chip 显得空荡
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StepButton("‹ 上一页", enabled = current > 1, modifier = Modifier.weight(1f)) {
                    onJump(current - 1)
                }
                StepButton("下一页 ›", enabled = current < total, modifier = Modifier.weight(1f)) {
                    onJump(current + 1)
                }
            }
            Spacer(Modifier.height(14.dp))

            // ---------- 手动输入 ----------
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(50))
                    .background(onSurface.copy(alpha = 0.07f))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = input,
                    onValueChange = { s -> input = s.filter { it.isDigit() }.take(6) },
                    singleLine = true,
                    // 1.239：光标偏上 —— 系统字体的上下留白（includeFontPadding）会把基线顶上去，
                    // 单行输入框在 44dp 胶囊里垂直居中后会显得偏上。关掉它并锁定 lineHeight，
                    // 光标/文字与胶囊中线对齐。
                    textStyle = TextStyle(
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = onSurface,
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.None,
                        ),
                    ),
                    cursorBrush = SolidColor(primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (input.isEmpty()) {
                            Text(
                                "输入页码直达",
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                color = onSurface.copy(alpha = 0.45f),
                            )
                        }
                        inner()
                    },
                )
            }
            Spacer(Modifier.height(16.dp))

            // ---------- 取消 / 跳转 ----------
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LiquidGlassButton("取消", accent = false, modifier = Modifier.weight(1f)) { close() }
                LiquidGlassButton("跳转", accent = true, modifier = Modifier.weight(1f)) {
                    if (typed != null) {
                        close()
                        onJump(typed)
                    }
                }
            }
        }
    }
}

/**
 * 1.228c: 评论区**独立的页码圆钮**。
 *
 * 玻璃参数**直接对齐底部工具栏**（面层 α 0.38 + `vibrancy` + `blur(10dp)`），
 * 手感复用「写评论」（`LiquidButton`）那一套按压形变 + 流体高光。
 *
 * 注意修饰符顺序：`clip` 必须写在 `drawBackdrop` **之前**——
 * Compose 的 Modifier 从左到右应用，clip 只约束它后面的绘制；
 * 若写在后面，玻璃面会按矩形画出来（真机上就是那个「方形容器」）。
 */
@Composable
internal fun ReplyPageFab(
    backdrop: Backdrop,
    pageNow: Int,
    pageTotal: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLight = !isAppDarkTheme()
    // 1.228d：浅色下圆钮要**比工具栏更白一点**才能看起来一致。
    // 原因不是参数抄错了，而是**采样面积**：工具栏宽、模糊到的多是页面浅色底；
    // 48dp 的圆采样到的常是卡片/文字边缘 → 平均下来偏灰。
    // 所以浅色把面层提高到 White 58%（深色维持 121212 38%，真机上两者观感一致）。
    val glassColor =
        if (isLight) Color.White.copy(alpha = 0.58f) else Color(0xFF121212).copy(alpha = 0.38f)
    val onSurface = MaterialTheme.colorScheme.onSurface
    val highlight = rememberLiquidHighlight()

    Box(
        modifier
            // 1.231: 按压形变必须放在**最外层**（与 `LiquidButton` 一致：pressTransform → clip →
            // background）。之前放在 clip / drawBackdrop 之后，它只作用于「后面的绘制」，
            // 于是按下时只有文字在动、玻璃面纹丝不动（真机反馈：只有字体有反馈）。
            .liquidPressTransform(highlight)
            // 1.230: **不加落影**。工具栏（ThreadActionBar）是裸磨砂条、没有 elevation，
            // 之前这里多了 8dp shadow → 圆钮比工具栏更「浮」，两者不像同一层。
            .clip(CircleShape)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { CircleShape },
                effects = {
                    vibrancy()
                    blur(10f.dp.toPx())
                },
                onDrawSurface = { drawRect(glassColor) },
            )
            .clickable(
                interactionSource = null,
                indication = null,
                onClick = onClick,
            )
            .then(highlight.modifier)
            .then(highlight.gestureModifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$pageNow/$pageTotal",
            fontSize = 11.sp,
            lineHeight = 12.sp,
            color = onSurface,
            maxLines = 1,
            softWrap = false,
            letterSpacing = (-0.4).sp,
        )
    }
}
/** 1.234：上一页 / 下一页快捷按钮 —— 禁用时半透明且不响应 */
@Composable
private fun StepButton(
    text: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    LiquidButton(
        onClick = { if (enabled) onClick() },
        fill = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
        shape = RoundedCornerShape(50),
        modifier = modifier.alpha(if (enabled) 1f else 0.38f),
        height = 40.dp,
        contentPadding = 0.dp,
        isInteractive = enabled,
        arrangement = Arrangement.Center,
    ) {
        Text(
            text,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

/**
 * 1.234：页码候选改为「首 / 当前 ±2 / 末」，跨度大时省略；页数少（≤7）时全部列出。
 * 纯函数，便于单测。
 */
internal fun pageCandidates(current: Int, total: Int): List<Int?> {
    if (total <= 1) return listOf(1)
    // 页数不多时直接全列（避免「只有三个页码」的空荡感）
    if (total <= 7) return (1..total).toList()
    val pages = sortedSetOf(1, total)
    for (p in current - 2..current + 2) if (p in 1..total) pages += p
    val out = mutableListOf<Int?>()
    var prev = 0
    for (p in pages) {
        if (prev != 0 && p - prev > 1) {
            // 只差一页时补上真实页码，不留空洞的省略号
            if (p - prev == 2) out += prev + 1 else out += null
        }
        out += p
        prev = p
    }
    return out
}
