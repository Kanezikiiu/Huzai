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

/**
 * 已经**探测失败过**的 url —— 不再重试。
 *
 * 1.224j（真机反馈「MIUI14 / Android13 点播放约 1 秒后闪退」排查）：
 * 详情页是 LazyColumn，主楼滚出视口再滚回来会重新进入组合 → 旧代码会**再抓一次帧**。
 * 一段视频被来回滚动就可能反复触发整帧解码，反复与播放器争抢解码器 / 内存。
 * 失败过就不再试，是最省事也最稳的收敛。
 */
private val failedProbes = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

internal fun cachedFrameInfo(url: String): FrameInfo? = frameCache[url]

/**
 * 抓取视频首帧并检测真实画面范围（结果缓存；失败返回 null，不影响播放）。
 *
 * 使用 MediaMetadataRetriever：**历史实现**（当时渲染面是 SurfaceView，拿不到实时帧）。
 * 1.224n 起渲染面已改为 **TextureView**，其实可以直接用 `TextureView.getBitmap()` 取当前帧，
 * 不必再单开一路解码器 —— 这里保留 MediaMetadataRetriever 只是"暂不动已验证可用的代码"，
 * 后续要简化时按上面思路替换即可。
 *
 * 1.224j：改用 **getScaledFrameAtTime**（API 27+，本项目 minSdk 34）**直接解出 48×48**。
 * 旧写法 `getFrameAtTime` 会先解出一整帧**原分辨率**位图再缩到 48×48 ——
 * 1080p 约 8MB、4K 约 33MB，且正好发生在 ExoPlayer 起播、解码器缓冲分配的同一时间窗里。
 * 个别 ROM（小米等）的解码器在这种内存竞争下会直接崩掉原生进程，
 * 表现为「点了播放、响一秒声音就闪退」。缩略解码不产生大位图，从根上避开这次竞争。
 */
internal suspend fun probeFrameInfo(url: String): FrameInfo? =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        frameCache[url]?.let { return@withContext it }
        if (url in failedProbes) return@withContext null
        // 1.224n：黑匣子记录抓帧的起止 —— 抓帧（MediaMetadataRetriever 另开一路解码）
        // 与 ExoPlayer 起播在时间上重叠，是「点播放约 1 秒后闪退」最可疑的环节，
        // 必须能看出崩溃发生在抓帧中还是抓帧之后。
        com.java.myapplication.data.HupuCrashLog.note(
            "抓帧开始",
            com.java.myapplication.data.HupuCrashLog.safeUrl(url),
        )
        val info = runCatching {
            val r = android.media.MediaMetadataRetriever()
            try {
                // 1.224n：把抓帧内部再细分三段 —— 真机上"抓帧开始"之后没有下文时，
                // 这几行能定位到是**取数据源**还是**解码那一帧**把进程带走的。
                com.java.myapplication.data.HupuCrashLog.note("抓帧·取数据源")
                r.setDataSource(url, mapOf("Referer" to "https://www.hupu.com/"))
                com.java.myapplication.data.HupuCrashLog.note("抓帧·解码中")
                // 1.224j：优先用 **getScaledFrameAtTime**（API 27+）直接解出 48×48 ——
                // 旧写法 getFrameAtTime 会先解出一整帧**原分辨率**位图再缩到 48×48
                // （1080p 约 8MB、4K 约 33MB），且正好落在 ExoPlayer 起播、解码器缓冲分配的
                // 同一时间窗里；个别 ROM（小米等）的解码器会因此崩掉原生进程，
                // 表现为「点了播放、响一秒声音就闪退」。
                // 本项目 minSdk 24：低版本没有这个方法，回落到老写法（这些机型此前无问题）。
                val frame = if (android.os.Build.VERSION.SDK_INT >= 27) {
                    r.getScaledFrameAtTime(
                        0,
                        android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        48,
                        48,
                    )
                } else {
                    r.getFrameAtTime(
                        0,
                        android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    )?.let { full ->
                        android.graphics.Bitmap.createScaledBitmap(full, 48, 48, false).also {
                            full.recycle()
                        }
                    }
                } ?: return@runCatching null
                val px = IntArray(48 * 48)
                frame.getPixels(px, 0, 48, 0, 0, 48, 48)
                frame.recycle()
                // 比例改从元数据取（缩略帧是正方形，拿不到原始宽高）。
                // 仅作 ui.videoAspect 缺失时的兜底，因此这里取不到也不必失败。
                var fw = r.extractMetadata(
                    android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH,
                )?.toIntOrNull() ?: 0
                var fh = r.extractMetadata(
                    android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT,
                )?.toIntOrNull() ?: 0
                val rot = r.extractMetadata(
                    android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION,
                )?.toIntOrNull() ?: 0
                if (rot == 90 || rot == 270) {
                    val t = fw; fw = fh; fh = t
                }
                if (fw <= 0 || fh <= 0) return@runCatching null
                FrameInfo(
                    box = detectContentBox(px, 48, 48),
                    aspect = fw.toFloat() / fh,
                )
            } finally {
                runCatching { r.release() }
            }
        }.getOrNull()
        if (info != null) frameCache[url] = info else failedProbes.add(url)
        val boxText = info?.box?.let {
            "裁 ${(it.width * 100).toInt()}%x${(it.height * 100).toInt()}%"
        } ?: "无"
        com.java.myapplication.data.HupuCrashLog.note(
            if (info != null) "抓帧成功" else "抓帧失败",
            "box=$boxText",
        )
        info
    }