package com.java.myapplication.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 1.191: 全局输入框统一配色。
 *
 * M3 `OutlinedTextField` 的默认描边用的是 `outline`（浅色主题下是一条深灰线），
 * 放在本 App 的中性底色上会显得很「脏」、格格不入。
 * 这里统一改成「**无边框 + 柔和填充**」：底=surfaceVariant@0.6（与 Chip 同色），
 * 描边全透明，光标用主题色。形状由各调用点自己决定（搜索框走 22dp 胶囊，
 * 其余走 12~16dp 圆角），所以只覆盖颜色、不动 shape。
 */
@Composable
fun huzaiFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    focusedBorderColor = Color.Transparent,
    unfocusedBorderColor = Color.Transparent,
    disabledBorderColor = Color.Transparent,
    cursorColor = MaterialTheme.colorScheme.primary,
)
