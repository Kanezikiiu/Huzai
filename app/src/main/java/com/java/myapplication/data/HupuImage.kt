package com.java.myapplication.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * 1.157: 上传图片格式嗅探 + 规范化（单一实现，供帖子回复 / 评分回复 / 私信 / 发帖共用）。
 *
 * 起因（真机反馈）：发出的动图，官方客户端看到的是静态图，而我们自己这边显示为动图。
 * 根因是上传时按「MIME 类型 / 文件名后缀」判定格式：
 *  · 收藏表情包等来源的图片地址不保证以 .gif 结尾（虎扑表情 CDN 常是无后缀路径或带 query），
 *    落盘时按 URL 猜的后缀就变成了 .jpg（字节其实是 GIF）；
 *  · 上传时再按后缀推出 ext="jpeg"，于是 OSS 以 image/jpeg 存、extension 传 jpeg ——
 *    服务端按静态图登记，官方端渲染即静态；
 *  · 而本地显示走 ImageDecoder 的**内容嗅探**，不管后缀一样能识别 GIF，所以这边仍是动图。
 * 两边对同一张图给出不同结果，症状由此而来。
 *
 * 修法：格式判定以**字节魔数**为第一依据（最可靠），MIME / 后缀只作兜底；
 * 且 GIF / 动画 WebP 一律原样上传，绝不做「解码成位图」的处理（那必然丢动画）。
 */
object HupuImage {

    // 1.162: 动画 WebP 的发送提示常量已随「自适应交付」一并移除。

    /** 单张栅格图超过 10MB 才考虑压缩转 JPEG（动图不受此限） */
    private const val MAX_RASTER_BYTES = 10 * 1024 * 1024

    /** 1.170: 单张输入上限（32MB）——超过直接拒绝，避免超大文件整段读进内存 */
    const val MAX_INPUT_BYTES = 32 * 1024 * 1024

    /** 1.170: 解码长边上限（px）——超过按 2 的幂降采样，避免超大图解码 OOM */
    private const val MAX_DECODE_DIM = 2560

    /**
     * 1.170: 带上限的图片读取。超过 [max] 字节立即返回 null（不再继续占用内存）。
     * 代替散落各处的 `openInputStream(uri)?.use { it.readBytes() }`，
     * 是「超大图 OOM」的第一道闸；解码端还有 [decodeScaled] 的第二道闸。
     */
    fun readCapped(
        cr: android.content.ContentResolver,
        uri: android.net.Uri,
        max: Int = MAX_INPUT_BYTES,
    ): ByteArray? {
        return try {
            cr.openInputStream(uri)?.use { ins ->
                val bos = java.io.ByteArrayOutputStream()
                val buf = ByteArray(64 * 1024)
                var total = 0
                while (true) {
                    val n = ins.read(buf)
                    if (n <= 0) break
                    total += n
                    if (total > max) return null
                    bos.write(buf, 0, n)
                }
                bos.toByteArray()
            }
        } catch (e: Exception) {
            null
        }
    }

    /** 1.170: 按原始尺寸算 inSampleSize（2 的幂），使长边不超过 [MAX_DECODE_DIM] */
    internal fun sampleSizeFor(width: Int, height: Int): Int {
        if (width <= 0 || height <= 0) return 1
        var s = 1
        while (s < 64 && (width / s > MAX_DECODE_DIM || height / s > MAX_DECODE_DIM)) {
            s *= 2
        }
        return s
    }

    /**
     * 1.170: 降采样解码。原实现直接 `decodeByteArray(...)` 无采样——
     * 一张 8000×6000 的照片按 ARGB_8888 需要约 190MB，低内存机型会直接 OOM。
     * 仅用于**静态**图 / 未知格式（动图不会走到这里，见 keepAnimationOrShrink）。
     */
    private fun decodeScaled(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    /**
     * 按魔数识别图片格式，返回虎扑白名单扩展名（gif / png / webp / jpeg）。
     * 识别不出返回 null。纯字节逻辑，不依赖 Android API（可单测）。
     */
    fun sniffExtension(bytes: ByteArray): String? {
        if (bytes.size < 12) {
            // 太短：仅够判定常见魔数前缀时也允许（PNG/JPEG 头 4 字节即可）
            if (bytes.size >= 3) {
                if (isGif(bytes)) return "gif"
                if (isJpeg(bytes)) return "jpeg"
            }
            if (bytes.size >= 4 && isPng(bytes)) return "png"
            return null
        }
        return when {
            isGif(bytes) -> "gif"
            isPng(bytes) -> "png"
            isWebp(bytes) -> "webp"
            isJpeg(bytes) -> "jpeg"
            else -> null
        }
    }

    /**
     * 是否为动图（GIF / 动画 WebP）。
     * 用于：上传前判断「绝不能转静态」、以及日志与提示语义。
     */
    fun isAnimated(bytes: ByteArray): Boolean = when (sniffExtension(bytes)) {
        "gif" -> true
        "webp" -> isAnimatedWebp(bytes)
        else -> false
    }

    /**
     * 是否为**动画 WebP**。
     *
     * 用途：上传前判断「绝不能转静态」（GIF / 动画 WebP 一律原样上传）。
     *
     * 注：1.158 曾判定「虎扑官方端不播动画 WebP」，该结论已被证伪（虎扑 CDN 自己就会
     * 分发动画 WebP）。至于「官方端看我们发送的动画 WebP 显示为静态」一事，
     * 1.162 已按产品决定不再处理；上传侧语义不变——动图绝不做「解码成位图」的转码。
     */
    fun isAnimatedWebp(bytes: ByteArray): Boolean =
        sniffExtension(bytes) == "webp" && isAnimatedWebpInternal(bytes)

    /** 由 MIME / 文件名后缀兜底推断扩展名；推断不出返回 null。纯字符串逻辑（可单测）。 */
    fun extensionFromHints(mime: String, name: String = ""): String? {
        when (mime.trim().lowercase()) {
            "image/gif" -> return "gif"
            "image/png" -> return "png"
            "image/webp" -> return "webp"
            "image/jpeg", "image/jpg", "image/pjpeg" -> return "jpeg"
        }
        return when (name.substringAfterLast('.', "").lowercase()) {
            "gif" -> "gif"
            "png" -> "png"
            "webp" -> "webp"
            "jpg", "jpeg" -> "jpeg"
            else -> null
        }
    }

    /** 规范化后的待上传图片 */
    data class Prepared(val bytes: ByteArray, val ext: String, val width: Int, val height: Int)

    /**
     * 上传前规范化一张图片：
     *  1) 魔数嗅探 → 拿到真实格式（GIF 再也不会被误标成 jpeg）；
     *  2) 嗅探失败才退回 MIME / 后缀；
     *  3) 都识别不出（HEIC 等）才解码位图转 JPEG；
     *  4) 超大**静态**图压缩转 JPEG；动图（GIF / 动画 WebP）原样保留。
     *
     * @return null 表示无法识别的格式（调用方提示「不支持的图片格式」）
     */
    fun prepareForUpload(raw: ByteArray, mimeHint: String = "", nameHint: String = ""): Prepared? {
        if (raw.isEmpty()) return null
        val byMagic = sniffExtension(raw)
        if (byMagic != null) return keepAnimationOrShrink(raw, byMagic)
        val byHint = extensionFromHints(mimeHint, nameHint)
        if (byHint != null) return keepAnimationOrShrink(raw, byHint)
        return transcodeToJpeg(raw)
    }

    // ---------- 内部 ----------

    /**
     * 动图（GIF / 动画 WebP）原样返回；超大静态图解码为位图再压成 JPEG（省流量），
     * 与 1.95 起的既有行为一致。
     */
    private fun keepAnimationOrShrink(bytes: ByteArray, ext: String): Prepared {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        if (isAnimated(bytes)) {
            // 动图绝不转静态：转码必然只剩第一帧，官方端就会显示成静态图
            return Prepared(bytes, ext, opts.outWidth, opts.outHeight)
        }
        if (ext == "gif") {
            // 兜底：扩展名是 gif 但嗅探没判成动图（异常样本），同样不动字节
            return Prepared(bytes, ext, opts.outWidth, opts.outHeight)
        }
        if (bytes.size > MAX_RASTER_BYTES) {
            // 1.170: 走降采样解码（不再无采样 decodeByteArray，避免超大图 OOM）
            val bmp = decodeScaled(bytes)
            if (bmp != null) {
                val baos = java.io.ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.JPEG, 88, baos)
                val out = baos.toByteArray()
                val p = Prepared(out, "jpeg", bmp.width, bmp.height)
                bmp.recycle()
                return p
            }
        }
        return Prepared(bytes, ext, opts.outWidth, opts.outHeight)
    }

    /** 解码位图转 JPEG（HEIC 等虎扑白名单之外的格式）；1.170: 同样降采样解码 */
    private fun transcodeToJpeg(bytes: ByteArray): Prepared? {
        val bmp = decodeScaled(bytes) ?: return null
        val baos = java.io.ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 88, baos)
        val out = baos.toByteArray()
        val p = Prepared(out, "jpeg", bmp.width, bmp.height)
        bmp.recycle()
        return p
    }

    private fun isGif(b: ByteArray): Boolean =
        b.size >= 3 && b[0] == 'G'.code.toByte() && b[1] == 'I'.code.toByte() && b[2] == 'F'.code.toByte()

    private fun isPng(b: ByteArray): Boolean =
        b.size >= 4 && b[0] == 0x89.toByte() && b[1] == 'P'.code.toByte() &&
            b[2] == 'N'.code.toByte() && b[3] == 'G'.code.toByte()

    private fun isJpeg(b: ByteArray): Boolean =
        b.size >= 3 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() && b[2] == 0xFF.toByte()

    /** RIFF....WEBP */
    private fun isWebp(b: ByteArray): Boolean =
        b.size >= 12 && b[0] == 'R'.code.toByte() && b[1] == 'I'.code.toByte() &&
            b[2] == 'F'.code.toByte() && b[3] == 'F'.code.toByte() &&
            b[8] == 'W'.code.toByte() && b[9] == 'E'.code.toByte() &&
            b[10] == 'B'.code.toByte() && b[11] == 'P'.code.toByte()

    /**
     * WebP 容器内出现 ANIM chunk 即为动画 WebP。
     * 结构：'RIFF' size 'WEBP' 之后是若干 chunk（4 字节 id + 4 字节小端 size + data + 1 字节对齐填充）。
     */
    private fun isAnimatedWebpInternal(b: ByteArray): Boolean {
        if (!isWebp(b)) return false
        var i = 12
        while (i + 8 <= b.size) {
            if (b[i] == 'A'.code.toByte() && b[i + 1] == 'N'.code.toByte() &&
                b[i + 2] == 'I'.code.toByte() && b[i + 3] == 'M'.code.toByte()
            ) return true
            val size = (b[i + 4].toInt() and 0xFF) or
                ((b[i + 5].toInt() and 0xFF) shl 8) or
                ((b[i + 6].toInt() and 0xFF) shl 16) or
                ((b[i + 7].toInt() and 0xFF) shl 24)
            if (size <= 0 || i + 8 + size > b.size) return false
            i += 8 + size + (size and 1)
        }
        return false
    }
}
