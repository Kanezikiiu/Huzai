package com.java.myapplication

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.ImageDecoderDecoder

class HupuApp : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                // ImageDecoder (API 28+): GIF + animated WebP, hardware accelerated
                add(ImageDecoderDecoder.Factory())
            }
            .respectCacheHeaders(false)
            .crossfade(true)
            .build()

    override fun onCreate() {
        super.onCreate()
        // 1.133: 崩溃取证只在 debug 构建启用。这是 1.129 为排查偶发闪退临时加的取证代码，
        // 会把异常堆栈写入外部共享目录——正式包不应向共享存储落盘堆栈（隐私/体积）。
        if (BuildConfig.DEBUG) installCrashLogger()
    }

    /**
     * 1.129: 偶发闪退取证——未捕获异常堆栈落盘（对用户无感）。
     * 位置：Android/data/com.java.myapplication/files/crash_last.txt（文件管理器可见）；
     * 兜底再写一份到内部私有目录 files/crash_last.txt。
     */
    private fun installCrashLogger() {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sw = java.io.StringWriter()
                e.printStackTrace(java.io.PrintWriter(sw))
                val text = buildString {
                    append("time=")
                    append(
                        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                            .format(java.util.Date())
                    )
                    append("\nthread=").append(t.name)
                    append("\n\n").append(sw.toString())
                }
                for (dir in listOfNotNull(getExternalFilesDir(null), filesDir)) {
                    try {
                        java.io.File(dir, "crash_last.txt").writeText(text)
                    } catch (_: Throwable) {
                    }
                }
            } catch (_: Throwable) {
            }
            prev?.uncaughtException(t, e)
        }
    }
}
