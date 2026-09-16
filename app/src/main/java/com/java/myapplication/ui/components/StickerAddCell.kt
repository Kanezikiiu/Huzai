package com.java.myapplication.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.java.myapplication.data.HupuPrefs
import com.java.myapplication.data.LocalStickerImport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 1.165: 「我的表情」网格首位的本地添加按钮（微信式）——
 * 一个 44dp 圆角小格，点击后从相册选图（可多选），导入到 filesDir/stickers_local
 * 并收藏为「我的表情」。导入过程在 IO 线程执行，格内转圈，完成后给出 toast 反馈。
 *
 * @param size 格子边长（与表情格同尺寸，默认 44dp）
 * @param corner 圆角
 */
@Composable
fun StickerAddCell(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    corner: Dp = 8.dp,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents(),
    ) { uris ->
        if (uris.isEmpty() || importing) return@rememberLauncherForActivityResult
        importing = true
        HupuPrefs.stickerToast = "正在导入…"
        scope.launch {
            var added = 0
            var dup = 0
            var fail = 0
            uris.forEach { u ->
                // 1.168: 导入结果区分「新增 / 重复 / 失败」，重复不再静默
                when (withContext(Dispatchers.IO) { HupuPrefs.importLocalSticker(ctx, u) }) {
                    LocalStickerImport.ADDED -> added++
                    LocalStickerImport.DUPLICATE -> dup++
                    LocalStickerImport.FAILED -> fail++
                }
            }
            importing = false
            val parts = buildList {
                if (added > 0) add("已添加 $added 个")
                if (dup > 0) add("$dup 个已存在")
                if (fail > 0) add("$fail 个失败")
            }
            HupuPrefs.stickerToast = if (added == 0 && dup > 0 && fail == 0) {
                "表情已存在，未重复添加"
            } else {
                parts.joinToString("，").ifEmpty { "导入失败" }
            }
        }
    }
    val shape = RoundedCornerShape(corner)
    // 1.167: 外层占满单元格宽度并居中，内层用 requiredSize 固定为正方形。
    // 原因：LazyVerticalGrid 给 item 的是「精确单元格宽度」约束，Modifier.size() 会被父约束
    // 撑满 → 加号格变成与单元格等宽的长条（平板上尤为夸张）。requiredSize 忽略父约束，
    // 保证永远是 size×size 的正方形，与表情图（Fit 居中后同样约 44dp）对齐。
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .requiredSize(size)
                .clip(shape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.09f))
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f), shape)
                .clickable(enabled = !importing) { pick.launch("image/*") },
            contentAlignment = Alignment.Center,
        ) {
            if (importing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(size * 0.42f),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "添加本地图片",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(size * 0.52f),
                )
            }
        }
    }
}
