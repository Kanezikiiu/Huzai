package com.java.myapplication.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.java.myapplication.ui.components.HupuIcons
import com.java.myapplication.ui.components.formatCount
import com.java.myapplication.ui.theme.isAppDarkTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy

/**
 * 1.189: 帖子详情「常驻操作条」。
 *
 * 此前「推荐 / 收藏 / 分享」分别藏在主楼卡片底部与顶栏里，而**两者都是 LazyColumn 的 item**
 * ——往下滚到评论区后就全部不可达。本组件把它们收成一条贴底悬浮条，随时可用：
 *
 *   ✏️ 写评论            💬 67     👍 24     ☆     ⤴️ 转发
 *
 * - 左侧「写评论」占满剩余宽度，点击聚焦底部回复框（未登录由调用方拦截并提示）；
 * - 右侧四个图标项等宽：评论数（点击跳转评论区）/ 推荐数 / 收藏 / 转发；
 * - **收藏不显示计数**——虎扑不返回收藏数（`HupuThread` 只有 lights/replies/read），
 *   改为星形下方显示「收藏 / 已收藏」文字（1.190），按钮固定 46dp 宽，两态不跳动；
 * - 选中态（推荐 / 收藏）统一用**主题色**，跟随「色彩主题」设置（1.190）；
 * - 底为**真毛玻璃**（kyant backdrop：vibrancy + blur），面层透明度压得很低（0.38），
 *   与悬浮 Tab 栏同一套引擎与语言。
 *
 * 布局与层级的注意事项见 `ThreadDetailPage`：zIndex 必须低于表情面板(998)/输入行(1000)，
 * 且在回复框打开时整条移出屏幕。
 */
@Composable
internal fun ThreadActionBar(
    backdrop: Backdrop,
    replyCount: Int,
    recommendCount: Int,
    isRecommended: Boolean,
    isCollected: Boolean,
    onWrite: () -> Unit,
    onJumpComments: () -> Unit,
    onRecommend: () -> Unit,
    onCollect: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLight = !isAppDarkTheme()
    // 面层颜色：透明度刻意压很低（截图同款观感），模糊由 backdrop 引擎负责
    val glassColor =
        if (isLight) Color(0xFFFAFAFA).copy(alpha = 0.38f) else Color(0xFF121212).copy(alpha = 0.38f)
    val writePillColor =
        if (isLight) Color.White.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.10f)
    val onSurface = MaterialTheme.colorScheme.onSurface
    // 1.190: 选中态统一用主题色（推荐 / 收藏）——跟随「色彩主题」设置，不再固定红/金
    val activeColor = MaterialTheme.colorScheme.primary

    Row(
        modifier
            .fillMaxWidth()
            .height(54.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(27.dp) },
                effects = {
                    vibrancy()
                    blur(10f.dp.toPx())
                },
                onDrawSurface = { drawRect(glassColor) },
            )
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 写评论：占满剩余宽度
        Row(
            Modifier
                .weight(1f)
                .height(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(writePillColor)
                .clickable { onWrite() }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                HupuIcons.Edit,
                contentDescription = null,
                tint = onSurface,
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text("写评论", fontSize = 15.sp, color = onSurface)
        }
        Spacer(Modifier.width(2.dp))
        // 1.190: 图标尺寸按「视觉面积」归一化，而不是统一 dp——
        // 各图标在 24dp 画布里的实际内容盒差别很大（实测内容盒 / 占比 / 等面积所需尺寸）：
        //   评论气泡 20x20 (0.69) → 19dp ｜拇指 22x20 (0.76) → 18dp
        //   星形     13.8x16.1 (0.39) → 25dp ｜转发箭头 17x14 (0.41) → 25dp
        // 实心块铺满画布、线性图标留白多，同一 dp 下线性图标会明显显小（真机两轮反馈确认）。
        ActionItem(HupuIcons.Comment, formatCount(replyCount), false, Color.Unspecified, onJumpComments, iconSize = 19.dp)
        ActionItem(HupuIcons.Light, formatCount(recommendCount), isRecommended, activeColor, onRecommend, iconSize = 18.dp)
        // 收藏：官方不返回收藏数 → 不显示计数，改用「收藏 / 已收藏」文字表意；
        // ActionItem 固定 46dp 宽 + 图标/文字居中，两种文案下按钮尺寸完全一致（不跳动）
        ActionItem(
            HupuIcons.StarRate,
            if (isCollected) "已收藏" else "收藏",
            isCollected,
            activeColor,
            onCollect,
            iconSize = 25.dp,
        )
        ActionItem(HupuIcons.IosShare, "转发", false, Color.Unspecified, onShare, iconSize = 25.dp)
    }
}

/** 单个图标项：图标 + 可选小字（计数或「收藏 / 转发」），四等宽 */
@Composable
private fun ActionItem(
    icon: ImageVector,
    caption: String?,
    active: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    /** 图标绘制尺寸：按各图标在 24dp 画布里的实际留白分档，保证四个图标视觉大小一致 */
    iconSize: Dp = 20.dp,
) {
    val normal = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)
    val tint = if (active) activeColor else normal
    Column(
        Modifier
            .width(46.dp)
            .height(46.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // 1.190: 图标统一放进定高「槽」里居中——四个图标尺寸按视觉面积归一化后各不相同
        // （18~25dp），若直接堆叠，图标高度差会把下方文字顶成不同基线（真机看到「名称不齐」）。
        // 槽高取最大图标尺寸，因此「图标 + 名称」整组在 46dp 按钮里也是水平垂直居中的。
        Box(
            Modifier.size(ICON_SLOT),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(iconSize),
            )
        }
        if (caption != null) {
            Spacer(Modifier.height(1.dp))
            // maxLines=1 + softWrap=false：无论「收藏」还是「已收藏」，都只裁剪不换行，
            // 保证按钮高度恒定（46dp 宽下三字 9sp 实测约 27dp，正常不触发裁剪）
            Text(
                caption,
                fontSize = 9.sp,
                lineHeight = 10.sp,
                color = tint,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** 图标槽高：四个按钮共用，取最大图标尺寸，保证「图标 + 名称」的基线在所有按钮间一致 */
private val ICON_SLOT = 25.dp