package com.java.myapplication.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 二级页统一顶栏：**返回键在左、标题与副标题居中**、右侧可放动作。
 *
 * 实现：内容作为一个整体 Row 居中（左右各留 56dp 给按钮），
 * 中间列内部再水平居中 —— 标题与副标题共享同一中心线。
 *
 * @param subtitle 副标题（如「N 人评分」「热度」），同样居中
 * @param leading  标题左侧的附加内容（如头像）
 * @param actions  右侧动作区（图标按钮、计数等）
 */
@Composable
internal fun CenteredTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Box(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        LiquidBackButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        Row(
            // 左右各留 56dp 给返回键/动作区，避免长标题与它们重叠
            Modifier
                .align(Alignment.Center)
                .padding(horizontal = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading?.invoke()
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        if (actions != null) {
            Row(
                Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}