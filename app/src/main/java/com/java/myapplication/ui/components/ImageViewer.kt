package com.java.myapplication.ui.components

import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.java.myapplication.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全屏图片查看器（盖入式：黑底 + 可缩放图片 + 下载按钮）。
 * - 1.186: 支持多图——同一条消息/回复内的图片可左右滑动切换（HorizontalPager）；
 *   未放大时横向滑动翻页，放大后横向手势用于平移（翻页自动禁用），双击还原后可继续翻页。
 * - 双指捏合缩放 / 双击放大还原 / 单击关闭（含盖入式退场动画 280ms）
 * - 下载：MediaStore API 直写公共相册（Android 10+ 无需存储权限；
 *   Android 9 及以下写入应用私有外部目录并扫描——本 App minSdk 24，但
 *   targetSdk 35 下 WRITE_EXTERNAL_STORAGE 已无效，统一走 MediaStore）。
 *   文件保存到 Pictures/hupu/，系统相册可立即看到。
 *
 * @param urls 同一条消息内的全部图片地址（顺序与正文渲染一致）
 * @param initialIndex 首帧展示的图片下标
 */
/**
 * 1.223：去掉虎扑 CDN 的 `x-oss-process` 处理参数，拿到**原图**地址。
 * 正文/回复里的图片常带 `?x-oss-process=image/resize,w_xxx`（网页端点击后才看原图），
 * 这正是「App 里比网页糊」的原因；查看器里可一键切到原图。
 */
private fun originalImageUrl(url: String): String {
    val q = url.indexOf('?')
    if (q < 0) return url
    val base = url.substring(0, q)
    val kept = url.substring(q + 1)
        .split('&')
        .filter { it.isNotEmpty() && !it.startsWith("x-oss-process=") }
    return if (kept.isEmpty()) base else base + "?" + kept.joinToString("&")
}

/** 1.223ab: 本次进程内已切过「原图」的图片 URL —— 全屏查看器跨页、跨次共用，保证状态一致。 */
private val viewerOriginalUrls = java.util.Collections.synchronizedSet(mutableSetOf<String>())

@Composable
fun ImageViewer(
    urls: List<String>,
    initialIndex: Int,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(280)) }

    // 容错：空列表退化为单张空图（避免 pager 页数 0 崩溃）
    val pages = urls.ifEmpty { listOf("") }
    val startIndex = initialIndex.coerceIn(0, pages.lastIndex)
    val pagerState = rememberPagerState(initialPage = startIndex) { pages.size }

    // 缩放状态：scale + offset（双指捏合 / 双击切换 1x↔2.5x）；仅作用于当前页，切页复位
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    LaunchedEffect(pagerState.currentPage) {
        scale = 1f; offsetX = 0f; offsetY = 0f
    }
    // 下载状态：null=未开始；true=下载中；false=已完成；msg=失败提示
    var downloading by remember { mutableStateOf(false) }
    var downloadMsg by remember { mutableStateOf<String?>(null) }
    val currentUrl = pages.getOrElse(pagerState.currentPage) { pages[0] }
    // 1.223ab: 「原图」按 **URL** 记录（进程级集合）——
    // 同一张图看过原图后，翻页切回来 / 重开全屏仍是原图，且按钮不再出现（状态始终一致）。
    var originalMode by remember(currentUrl) { mutableStateOf(currentUrl in viewerOriginalUrls) }

    // 系统返回手势：吞掉进度、直接盖出退出（无页面级跟手需求，保持简洁）
    PredictiveBackHandler { events ->
        events.collect { }
        onBack()
    }

    Box(
        Modifier
            .fillMaxSize()
            .zIndex(10f)
            .background(Color.Black.copy(alpha = 0.96f))
            // 单击关闭 / 双击放大还原（放在容器上：横向拖动会被翻页或页内缩放消费，不会误触关闭）
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onBack() },
                    onDoubleTap = {
                        // 双击：1x ↔ 2.5x 切换（带还原 offset）
                        if (scale > 1.01f) {
                            scale = 1f; offsetX = 0f; offsetY = 0f
                        } else {
                            scale = 2.5f
                        }
                    },
                )
            },
    ) {
        HorizontalPager(
            state = pagerState,
            // 放大状态下禁用翻页，避免横向平移被翻页抢走；双击还原后自动恢复
            userScrollEnabled = scale <= 1.01f,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            Box(
                Modifier
                    .fillMaxSize()
                    // 页内手势层（pager 的子级，先于 pager 收到事件）：
                    // 仅双指或已放大时消费手势 → 未放大时单指横滑顺畅交给 pager 翻页
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var active = false
                            do {
                                val event = awaitPointerEvent()
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                val multi = event.changes.count { it.pressed } > 1
                                if (multi || zoom != 1f) active = true
                                if (active || scale > 1.01f) {
                                    scale = (scale * zoom).coerceIn(1f, 5f)
                                    offsetX += pan.x
                                    offsetY += pan.y
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    },
            ) {
                RetryAsyncImage(
                    // 1.223u: 默认按控件尺寸解码（省内存，降低低内存机型 OOM/卡死风险）；
                    // 仅当点开「原图」时才切到**原始分辨率**（同时去掉 CDN 压缩参数）。
                    // 1.223z: 改用自带**失败自动重试**的图片组件（全屏看图遇到瞬时抖动不再空白）
                    model = if (originalMode) {
                        coil.request.ImageRequest.Builder(context)
                            .data(originalImageUrl(pages[page]))
                            .size(coil.size.Size.ORIGINAL)
                            .build()
                    } else {
                        pages[page]
                    },
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offsetX
                            translationY = offsetY
                        },
                )
            }
        }

        // 顶部操作条：下载 + 复制地址 + 关闭
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 1.223aa: 「原图」做成**一次性动作**——点过即隐藏。
            // （原本可来回切，但切回「压缩图」实际不生效，且用户没这个需求，索性不再显示。）
            // 翻页看下一张时会重新出现。
            if (!originalMode) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.15f))
                        .clickable {
                            originalMode = true
                            viewerOriginalUrls.add(currentUrl)
                        }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "原图",
                        fontSize = 13.sp,
                        color = Color.White,
                    )
                }
                Spacer(Modifier.width(10.dp))
            }
            // 下载按钮
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
                    .clickable(enabled = !downloading) {
                        downloading = true
                        downloadMsg = null
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (downloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_download),
                        contentDescription = "下载",
                        tint = Color.White,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            // 1.158: 复制图片地址（排查「同一张图两端表现不同」时最直接的证据）
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
                    .clickable {
                        val cb = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                        cb.setPrimaryClip(android.content.ClipData.newPlainText("图片地址", currentUrl))
                        downloadMsg = "已复制图片地址"
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    HupuIcons.ContentCopy,
                    contentDescription = "复制图片地址",
                    tint = Color.White,
                )
            }
            Spacer(Modifier.width(12.dp))
            // 关闭按钮
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "关闭",
                    tint = Color.White,
                )
            }
        }

        // 1.186: 多图时显示页码（明确当前在整条消息的第几张，左右滑动切换更直观）
        if (pages.size > 1) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text(
                    "${pagerState.currentPage + 1} / ${pages.size}",
                    fontSize = 12.sp,
                    color = Color.White,
                )
            }
        }

        // 下载提示
        LaunchedEffect(downloading) {
            if (downloading) {
                val msg = withContext(Dispatchers.IO) { downloadImage(context, currentUrl) }
                downloadMsg = msg
                downloading = false
            }
        }
        LaunchedEffect(downloadMsg) {
            if (downloadMsg != null) {
                delay(2500)
                downloadMsg = null
            }
        }
        LaunchedEffect(downloadMsg) { downloadMsg?.let { com.java.myapplication.ui.components.HuzaiToast.show(it) } }
    }
}

/** 下载图片到公共相册（Pictures/hupu/）。返回结果提示文案。 */
private fun downloadImage(context: android.content.Context, url: String): String {
    return try {
        // 1.169: 复用共享 client（原为每次下载都 new 一个 OkHttpClient）
        val client = com.java.myapplication.data.HupuHttp.client
        val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return "下载失败：HTTP ${resp.code}"
            val body = resp.body ?: return "下载失败：响应为空"
            val bytes = body.bytes()
            // 文件名：时间戳 + URL 尾段去参（保后缀）
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date())
            val ext = url.substringBefore('?').substringAfterLast('.', "jpg").let {
                if (it.length in 2..4 && it.matches(Regex("[a-zA-Z0-9]+"))) it else "jpg"
            }
            val name = "hupu_${ts}.${ext}"
            // Android 10+ (API 29) MediaStore 直写；旧版本走传统文件路径
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/$ext")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/hupu")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return "下载失败：无法创建文件"
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "hupu",
                )
                dir.mkdirs()
                val f = File(dir, name)
                f.outputStream().use { it.write(bytes) }
                // 扫描进相册
                android.media.MediaScannerConnection.scanFile(
                    context, arrayOf(f.absolutePath), arrayOf("image/*"), null,
                )
            }
            "已保存到相册 Pictures/hupu/$name"
        }
    } catch (e: Exception) {
        "下载失败：${e.message ?: "网络错误"}"
    }
}