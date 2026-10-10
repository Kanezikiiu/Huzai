package com.java.myapplication.ui.components

import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.java.myapplication.ui.glass.LiquidGlassCard
import com.java.myapplication.ui.glass.LiquidGlassButton
import com.java.myapplication.ui.theme.isAppDarkTheme
import com.java.myapplication.R
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
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
    // 1.199b：下载状态 —— downloading=进行中；downloadQueue=待下载列表（非空即开始）
    var downloading by remember { mutableStateOf(false) }
    var downloadQueue by remember { mutableStateOf<List<String>>(emptyList()) }
    // 多图时点「下载」→ 先弹选择框（这一张 / 全部 N 张）
    var downloadAsk by remember { mutableStateOf(false) }
    // 图片字节数缓存（HEAD 拿 Content-Length），键 = 真实请求的 URL —— 供「查看原图」标签与下载弹窗展示
    val originalSizes = remember { androidx.compose.runtime.mutableStateMapOf<String, Long>() }
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
        // 1.199c：**记录层** —— 查看器自身的真实内容（黑底 + 图片 + 底部操作条 + 页码）供下载弹窗采样。
        // 与「退出登录」弹窗同一套做法：采样真实内容层，折射出来才有层次（纯色画布 = 一块死板色块）。
        // 弹窗必须是本记录层节点的**后续兄弟**（画在它之后），否则会采到自己 -> 自采样递归闪退。
        val viewerBackdrop = rememberLayerBackdrop {
            drawRect(Color.Black.copy(alpha = 0.96f))
            drawContent()
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.96f))
                .layerBackdrop(viewerBackdrop),
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

        // 1.199b：**底部操作条**（原顶部条改版）
        //   · 左：查看原图（附原图大小）—— 一次性动作，点过即隐藏
        //   · 右：下载 / 复制图片链接
        //   · 1.199d：下载一律取**原图**（去掉 CDN 的 x-oss-process 压缩参数），不再跟随「是否已切原图」
        //   · 去掉关闭按钮（单击图片、返回手势都能退出）
        //   · 按钮统一「深色半透明玻璃 + 细描边 + 白色图标」——
        //     无论图片明暗都看得清（原先白底@15% 在浅色图上几乎看不见）
        Row(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!originalMode) {
                val sizeLabel = originalSizes[originalImageUrl(currentUrl)]
                    ?.takeIf { it > 0 }?.let { formatBytes(it) }.orEmpty()
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(overlayBtnFill)
                        .border(0.6.dp, overlayBtnBorder, RoundedCornerShape(50))
                        .clickable {
                            originalMode = true
                            viewerOriginalUrls.add(currentUrl)
                        }
                        // 1.199d：体积文字到达 / 换页导致长度变化时，**宽度走动画**
                        // （原来瞬时跳变，真机上就是「闪一下」）
                        .animateContentSize(
                            alignment = Alignment.CenterStart,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("查看原图", fontSize = 13.sp, color = Color.White)
                    // 体积文字淡入（避免「先蹦出按钮、再蹦出文字」的二次闪烁）
                    AnimatedVisibility(
                        visible = sizeLabel.isNotEmpty(),
                        enter = fadeIn(tween(160)),
                        exit = fadeOut(tween(120)),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.width(6.dp))
                            Text(sizeLabel, fontSize = 11.sp, color = Color.White.copy(alpha = 0.75f))
                        }
                    }
                }
            } else {
                Spacer(Modifier.width(1.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(overlayBtnFill)
                        .border(0.6.dp, overlayBtnBorder, CircleShape)
                        .clickable(enabled = !downloading) {
                            if (pages.size > 1) downloadAsk = true
                            else downloadQueue = listOf(originalImageUrl(currentUrl))
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
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(overlayBtnFill)
                        .border(0.6.dp, overlayBtnBorder, CircleShape)
                        .clickable {
                            val cb = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                as android.content.ClipboardManager
                            cb.setPrimaryClip(android.content.ClipData.newPlainText("图片地址", currentUrl))
                            com.java.myapplication.ui.components.HuzaiToast.show("已复制图片链接")
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        HupuIcons.ContentCopy,
                        contentDescription = "复制图片链接",
                        tint = Color.White,
                    )
                }
            }
        }

        // 1.199b：原图大小预取（HEAD）——点开就对「查看原图」显示体积
        // 1.199d：不再因「已切原图」跳过（下载始终取原图，体积标签随时都要准）
        LaunchedEffect(currentUrl) {
            val u = originalImageUrl(currentUrl)
            if (originalSizes.containsKey(u)) return@LaunchedEffect
            val n = withContext(Dispatchers.IO) { fetchContentLength(u) }
            if (n > 0) originalSizes[u] = n
        }
        // 弹窗打开时补齐「全部」所需的各页大小（拿不到就只显示张数）
        LaunchedEffect(downloadAsk) {
            if (!downloadAsk) return@LaunchedEffect
            for (p0 in pages) {
                val target = originalImageUrl(p0)
                if (originalSizes.containsKey(target)) continue
                val n = withContext(Dispatchers.IO) { fetchContentLength(target) }
                if (n > 0) originalSizes[target] = n
            }
        }

        // 1.186: 多图时显示页码（明确当前在整条消息的第几张，左右滑动切换更直观）
        if (pages.size > 1) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 76.dp)
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

        }

        // 1.199b：多图下载选择
        if (downloadAsk) {
            DownloadChoiceDialog(
                backdrop = viewerBackdrop,
                singleLabel = originalSizes[originalImageUrl(currentUrl)]
                    ?.takeIf { it > 0 }?.let { formatBytes(it) }.orEmpty(),
                allCount = pages.size,
                allLabel = pages.map { p0 -> originalSizes[originalImageUrl(p0)] }
                    .takeIf { list -> list.size == pages.size && list.all { it != null && it > 0 } }
                    ?.filterNotNull()?.sum()?.let { formatBytes(it) }.orEmpty(),
                onSingle = {
                    downloadAsk = false
                    downloadQueue = listOf(originalImageUrl(currentUrl))
                },
                onAll = {
                    downloadAsk = false
                    downloadQueue = pages.map { p0 -> originalImageUrl(p0) }
                },
                onDismiss = { downloadAsk = false },
            )
        }

        // 1.199b：下载（队列 —— 支持一次保存整条消息的多张）
        LaunchedEffect(downloadQueue) {
            val q = downloadQueue
            if (q.isEmpty()) return@LaunchedEffect
            downloading = true
            var ok = 0
            var fail = 0
            for (u in q) {
                val msg = withContext(Dispatchers.IO) { downloadImage(context, u) }
                if (msg.startsWith("已保存")) ok++ else fail++
            }
            downloading = false
            downloadQueue = emptyList()
            com.java.myapplication.ui.components.HuzaiToast.show(
                when {
                    fail == 0 && ok == 1 -> "已保存到相册 Pictures/hupu/"
                    fail == 0 -> "已保存 $ok 张到相册 Pictures/hupu/"
                    else -> "已保存 $ok 张，$fail 张失败"
                }
            )
        }
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


// ---------------------------------------------------------------------------
// 1.199b：全屏查看器的叠层按钮配色 + 体积工具 + 多图下载选择
// ---------------------------------------------------------------------------

/** 叠层按钮底：深色半透明（图片明暗都能看清；配白图标 + 细描边） */
private val overlayBtnFill = Color.Black.copy(alpha = 0.42f)

/** 叠层按钮描边：在纯黑图片上也能勾出边界 */
private val overlayBtnBorder = Color.White.copy(alpha = 0.16f)

/** 字节数 →「1.2 MB / 860 KB」（拿不到返回空串） */
internal fun formatBytes(n: Long): String = when {
    n <= 0L -> ""
    n < 1024L -> "$n B"
    n < 1024L * 1024L -> "%.0f KB".format(n / 1024f)
    else -> "%.1f MB".format(n / (1024f * 1024f))
}

/** HEAD 取文件字节数（失败/无 Content-Length 返回 0，用于「查看原图 1.2MB」） */
private suspend fun fetchContentLength(url: String): Long = withContext(Dispatchers.IO) {
    runCatching {
        val req = Request.Builder().url(url).head().header("User-Agent", "Mozilla/5.0").build()
        com.java.myapplication.data.HupuHttp.client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return@use 0L
            r.header("Content-Length")?.toLongOrNull() ?: 0L
        }
    }.getOrDefault(0L)
}

/**
 * 1.199c（真机反馈）：多图下载选择改用 **kyant 玻璃对话框**（[LiquidGlassCard]）。
 *
 * 排版与全站其它弹窗（清空书源 / 重置频道…）完全同源：
 * 标题 24sp Medium 居左、正文 15sp @68% 说明体积、两颗 48dp 胶囊按钮（右侧 accent = 主题色）。
 * 背景源 = **查看器自身的真实内容层**（`viewerBackdrop`：黑底 + 图片 + 底部按钮条 + 页码），
 * 与「退出登录」弹窗同一套采样方式 —— 折射出来才有层次；弹窗画在该记录层之后（后续兄弟），不会自采样。
 */
@Composable
private fun DownloadChoiceDialog(
    /** 1.199c：查看器内容层的记录层 —— 与「退出登录」弹窗同一来源（真实内容，非纯色画布） */
    backdrop: Backdrop,
    singleLabel: String,
    allCount: Int,
    allLabel: String,
    onSingle: () -> Unit,
    onAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isLight = !isAppDarkTheme()
    val contentColor = if (isLight) Color.Black else Color.White

    LiquidGlassCard(
        backdrop = backdrop,
        zIndex = 1100f,
        onDismiss = onDismiss,
    ) { close ->
        BasicText(
            "下载图片",
            Modifier.padding(24f.dp, 24f.dp, 24f.dp, 12f.dp),
            style = TextStyle(contentColor, 24f.sp, FontWeight.Medium),
        )
        BasicText(
            buildString {
                append("这一张")
                if (singleLabel.isNotEmpty()) append(" $singleLabel")
                append(" · 共 $allCount 张")
                if (allLabel.isNotEmpty()) append("（合计 $allLabel）")
            },
            Modifier
                .then(
                    // 深色档文字需要「加亮」而非普通叠加（与上游实现一致）
                    if (isLight) Modifier
                    else Modifier.graphicsLayer(blendMode = androidx.compose.ui.graphics.BlendMode.Plus)
                )
                .padding(24f.dp, 12f.dp, 24f.dp, 12f.dp),
            style = TextStyle(contentColor.copy(0.68f), 15f.sp),
            maxLines = 5,
        )
        Row(
            Modifier
                .padding(24f.dp, 12f.dp, 24f.dp, 24f.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16f.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LiquidGlassButton(
                text = "这一张",
                accent = false,
                modifier = Modifier.weight(1f),
                contentColor = contentColor,
                onClick = {
                    onSingle()
                    close()
                },
            )
            LiquidGlassButton(
                text = "全部 $allCount 张",
                accent = true,
                modifier = Modifier.weight(1f),
                onClick = {
                    onAll()
                    close()
                },
            )
        }
    }
}
