package com.java.myapplication.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage

/**
 * 1.188: 评论 / 回复图片的「加载中」「加载失败」占位。
 *
 * 此前评论图片直接用 AsyncImage，加载期间那块区域是一片空白：
 * - 有固有尺寸的图（帖子详情走 URL 尺寸预解析）会先留出一块**空白矩形**；
 * - 只有 max 约束的图（评分评价页）加载前尺寸为 0，图片像"凭空出现"，观感突兀。
 *
 * 现在统一：加载中显示主题色进度圈，失败显示浅色提示（尺寸过小时只留底色，避免文字被裁切）。
 * 占位**不改变图片加载完成后的尺寸逻辑**——它只是把原本空白的那块填上。
 */

/** 加载中：浅底 + 居中主题色进度圈 */
@Composable
internal fun ImageLoadingBox(modifier: Modifier = Modifier) {
    Box(
        modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** 加载失败：同底色 + 提示文字（小于 96dp 的缩略图只留底色，避免文字被裁切） */
@Composable
internal fun ImageErrorBox(modifier: Modifier = Modifier) {
    Box(
        modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints {
            if (maxWidth >= 96.dp) {
                Text(
                    "图片加载失败",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 紧凑占位：仅用于「加载前没有确定尺寸」的调用点（4:3，宽取内容宽一半） */
internal fun Modifier.commentImageLoadingBox(corner: Dp = 10.dp): Modifier =
    fillMaxWidth(0.5f).aspectRatio(4f / 3f).clip(RoundedCornerShape(corner))

/** 评论图片：调用点已有确定尺寸（固定尺寸 / URL 固有尺寸已算好） */
@Composable
internal fun CommentAsyncImage(
    model: Any?,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Fit,
    modifier: Modifier = Modifier,
) {
    SubcomposeAsyncImage(
        model = model,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
        loading = { ImageLoadingBox(Modifier.fillMaxSize()) },
        error = { ImageErrorBox(Modifier.fillMaxSize()) },
    )
}

/** 评论图片：只有 max 约束、加载前尺寸为 0 的调用点——加载中用紧凑占位把位置撑起来 */
@Composable
internal fun CommentAsyncImageCompact(
    model: Any?,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Fit,
    modifier: Modifier = Modifier,
    corner: Dp = 10.dp,
) {
    SubcomposeAsyncImage(
        model = model,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
        loading = { ImageLoadingBox(Modifier.commentImageLoadingBox(corner)) },
        error = { ImageErrorBox(Modifier.commentImageLoadingBox(corner)) },
    )
}