package com.java.myapplication.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 1.223z：带**自动重试**的图片。
 *
 * 背景：Coil 默认「失败即静默放弃」——图片直接不显示、没有任何提示或重试，
 * 全屏看图 / 正文大图遇到瞬时网络抖动只能退出重进。
 *
 * 这里在 `onError` 时最多重试 [maxRetry] 次（第 1 次 700ms、第 2 次 1400ms 退避），
 * 通过更换 memoryCacheKey 让 Coil 重新走一次取数（失败多为网络抖动，重试一次基本能挡住）。
 */
/**
 * 1.223z：构造「带重试键」的 model。
 *
 * ⚠️ **attempt = 0 时必须原样透传**：调用点传进来的 model 常常已经是 `ImageRequest`
 * （如正文图片带 crossfade），再 `.data(it)` 套一层会让 Coil 拿到「ImageRequest 作为 data」
 * 而解码失败 —— 表现为所有图片显示「图片加载失败」。
 */
internal fun retryModelOf(ctx: android.content.Context, model: Any?, attempt: Int): Any? {
    if (attempt <= 0) return model
    return when (model) {
        is ImageRequest -> model.newBuilder()
            .memoryCacheKey("$model#retry$attempt")
            .build()
        else -> ImageRequest.Builder(ctx)
            .data(model)
            .memoryCacheKey("$model#retry$attempt")
            .build()
    }
}

/**
 * 1.223ac：永久性失败（HTTP 4xx）**不重试** —— 只对网络抖动类错误重试，避免白跑两次。
 * Coil 的 HttpException 消息形如 "HTTP404 Not Found"，从中取状态码判断。
 */
internal fun isRetryableImageError(t: Throwable?): Boolean {
    var e = t
    var depth = 0
    while (e != null && depth < 8) {
        val code = Regex("HTTP (\\d{3})").find(e.message ?: "")
            ?.groupValues?.get(1)?.toIntOrNull()
        if (code != null && code in 400..499) return false
        e = e.cause
        depth++
    }
    return true
}

@Composable
internal fun RetryAsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    maxRetry: Int = 2,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    // model 变化（换图）时计数归零
    var attempt by remember(model) { mutableIntStateOf(0) }
    val req = retryModelOf(ctx, model, attempt)
    AsyncImage(
        model = req,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
        onError = { state ->
            if (attempt < maxRetry && isRetryableImageError(state.result.throwable)) {
                val next = attempt + 1
                scope.launch {
                    delay(700L * next)
                    attempt = next
                }
            }
        },
    )
}
