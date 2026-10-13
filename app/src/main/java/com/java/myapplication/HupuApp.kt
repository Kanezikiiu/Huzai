package com.java.myapplication

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.ImageDecoderDecoder
import com.java.myapplication.data.HupuPrefs
import com.java.myapplication.data.HupuCrashLog

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
        // 1.224e：在这里（而不是 MainActivity 首帧路径上）初始化偏好并预热，
        // 把 SharedPreferences 首次读盘 + home_topics JSON 解析挪到「还没有画面」的时候。
        // 否则这些成本会落在首页第一帧上，表现为冷启动明显变慢。
        HupuPrefs.init(this)
        // 1.224n：崩溃取证**正式包也启用**（此前只在 debug）。
        //  · 正式包才能覆盖安装到用户手机上（debug 包签名/包名都不同，得先卸载清数据），
        //    而"视频点播放后闪退"这类问题恰恰只能在出问题的机型上复现；
        //  · 全部只落**本机私有目录**（不再像 1.129 那样往外部共享目录写堆栈），
        //    且已在 backup_rules / data_extraction_rules 中排除云备份与换机迁移；
        //  · 用户在「关于 → 崩溃日志」可查看、一键保存（SAF 自选位置）或清除。
        HupuCrashLog.init(this)
    }
}
