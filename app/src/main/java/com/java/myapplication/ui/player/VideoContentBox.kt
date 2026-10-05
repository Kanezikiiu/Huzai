package com.java.myapplication.ui.player

/**
 * 视频画面「自带黑边」检测。
 *
 * 1.2xx（真机反馈：点播放后画面仍然很小）：
 * 虎扑不少视频是**转码/翻录产物**——真正的画面只占视频画布的一小块，四周是压进去的黑边
 * （实测某帖画面只占画布的 48%×41%）。这种黑边属于视频文件内容，**调容器比例毫无用处**，
 * 只能：采样实际渲染出来的画面帧 → 找出内容包围盒 → 把画面放大裁掉外圈。
 *
 * 这里只做纯计算（不依赖 Android 图形），便于单测。
 */

/** 归一化 0..1 的包围盒（相对整帧） */
internal data class ContentBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = (right - left).coerceAtLeast(0.001f)
    val height: Float get() = (bottom - top).coerceAtLeast(0.001f)
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    /** 是否存在明显留边（任一边 ≥ 6%） */
    val hasPadding: Boolean
        get() = left > 0.06f || top > 0.06f || right < 0.94f || bottom < 0.94f
}

/**
 * 用**相对亮度**找出内容的包围盒：
 * 先以中心 1/3 区域估画面亮度，再把「明显暗于中心」的最外圈行/列裁掉（每边最多 35%）。
 * 这样深灰/渐变留边也能识别（只认"纯黑"会漏掉大多数转码黑边）。
 *
 * @return 包围盒；整帧几乎全黑（无法判断）返回 null
 */
internal fun detectContentBox(pixels: IntArray, w: Int, h: Int): ContentBox? {
    if (w <= 0 || h <= 0 || pixels.size < w * h) return null

    fun lum(p: Int): Int {
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        return (r * 30 + g * 59 + b * 11) / 100
    }

    var sum = 0L
    var n = 0
    for (y in h / 3 until h * 2 / 3) {
        for (x in w / 3 until w * 2 / 3) {
            sum += lum(pixels[y * w + x])
            n++
        }
    }
    val center = if (n > 0) sum.toDouble() / n else 0.0
    if (center < 8.0) return null

    val darkLine = (center * 0.45).coerceAtLeast(6.0)

    fun rowMean(y: Int): Double {
        var s = 0L
        for (x in 0 until w) s += lum(pixels[y * w + x])
        return s.toDouble() / w
    }

    val maxTrimY = (h * 0.35).toInt().coerceAtLeast(1)
    val maxTrimX = (w * 0.35).toInt().coerceAtLeast(1)

    // ① 先定上下边界（行均值明显暗于中心即视为留边）
    var top = 0
    while (top < maxTrimY && rowMean(top) < darkLine) top++
    var bottom = h - 1
    while (bottom > h - 1 - maxTrimY && rowMean(bottom) < darkLine) bottom--

    // ② 再在「已确认的内容行范围」内定左右边界 —— 否则整列会把留边与画面一起平均，
    //    上下留边一大，整列均值就被压到阈值以下，导致误裁整张图
    fun colMeanInRows(x: Int): Double {
        var s = 0L
        var n = 0
        for (y in top..bottom) {
            s += lum(pixels[y * w + x])
            n++
        }
        return if (n > 0) s.toDouble() / n else 0.0
    }

    var left = 0
    while (left < maxTrimX && colMeanInRows(left) < darkLine) left++
    var right = w - 1
    while (right > w - 1 - maxTrimX && colMeanInRows(right) < darkLine) right--

    if (right <= left || bottom <= top) return null
    return ContentBox(
        left = left.toFloat() / w,
        top = top.toFloat() / h,
        right = (right + 1).toFloat() / w,
        bottom = (bottom + 1).toFloat() / h,
    )
}

/**
 * 由包围盒算出「把内容放大到尽可能大」的缩放倍数与平移修正（供 graphicsLayer 使用）。
 *
 * 取 `min(1/内容宽占比, 1/内容高占比)`：保证放大后**任何一边都不会超出容器**
 * （即"不超过上下宽高的前提下尽可能大"），剩下的一边仍会有一点留白。
 *
 * @return Triple(scale, dxFraction, dyFraction)：dx/dy 为内容中心相对画面中心的偏移（归一化，
 *         供调用方乘容器尺寸得到像素平移；居中画面为 0）
 */
internal fun contentZoom(box: ContentBox, maxScale: Float = 2.6f): Triple<Float, Float, Float> {
    val k = minOf(1f / box.width, 1f / box.height).coerceIn(1f, maxScale)
    return Triple(k, 0.5f - box.centerX, 0.5f - box.centerY)
}

/** 视频帧坐标 → 容器坐标：PlayerView 以 FIT 居中显示整帧，这里做同样的映射换算 */
internal fun toViewBox(
    v: ContentBox,
    viewW: Float,
    viewH: Float,
    videoW: Float,
    videoH: Float,
): ContentBox {
    val s = minOf(viewW / videoW, viewH / videoH)
    val dw = videoW * s
    val dh = videoH * s
    val ox = (viewW - dw) / 2f
    val oy = (viewH - dh) / 2f
    fun fx(x: Float) = (ox + x * dw) / viewW
    fun fy(y: Float) = (oy + y * dh) / viewH
    return ContentBox(fx(v.left), fy(v.top), fx(v.right), fy(v.bottom))
}

/** 探针结果：画面包围盒 + 该帧的显示比例（用于把帧坐标换算到容器坐标，不依赖播放器状态） */
internal data class FrameInfo(val box: ContentBox?, val aspect: Float)

/** 探针缓存（url → FrameInfo）；同步可读，供"重新进入组合"时立刻复用，避免二次等待 */
private val frameCache = java.util.concurrent.ConcurrentHashMap<String, FrameInfo>()

internal fun cachedFrameInfo(url: String): FrameInfo? = frameCache[url]

/**
 * 抓取视频首帧并检测真实画面范围（结果缓存；失败返回 box=null 但仍带上帧比例）。
 *
 * 使用 MediaMetadataRetriever：media3 1.8 的 PlayerView 已不再支持 TextureView，
 * 拿不到实时帧，只能单独取一帧（只取一次、结果缓存；失败不影响播放）。
 */
internal suspend fun probeFrameInfo(url: String): FrameInfo? =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        frameCache[url]?.let { return@withContext it }
        val info = runCatching {
            val r = android.media.MediaMetadataRetriever()
            try {
                r.setDataSource(url, mapOf("Referer" to "https://www.hupu.com/"))
                val frame = r.getFrameAtTime(
                    0,
                    android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                ) ?: return@runCatching null
                val fw = frame.width
                val fh = frame.height
                if (fw <= 0 || fh <= 0) {
                    frame.recycle()
                    return@runCatching null
                }
                val small = android.graphics.Bitmap.createScaledBitmap(frame, 48, 48, false)
                val px = IntArray(48 * 48)
                small.getPixels(px, 0, 48, 0, 0, 48, 48)
                small.recycle()
                frame.recycle()
                FrameInfo(
                    box = detectContentBox(px, 48, 48),
                    aspect = fw.toFloat() / fh,
                )
            } finally {
                runCatching { r.release() }
            }
        }.getOrNull()
        if (info != null) frameCache[url] = info
        info
    }