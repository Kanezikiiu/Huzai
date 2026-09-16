package com.java.myapplication.data

import android.app.Activity
import android.content.Context
import android.view.WindowManager

/**
 * 屏幕刷新率控制：枚举设备支持的显示模式（Display.getSupportedModes），
 * 选中模式写入 window.attributes.preferredDisplayModeId——系统公开 API，
 * 窗口前台期间强制运行在该刷新率上，即时生效（无需重启）。
 * 档位列表不是编造的，全部来自设备真实支持的 mode；-1 表示「自动」（跟随系统 LTPO 策略）。
 */
object HupuRefresh {

    data class ModeInfo(val modeId: Int, val width: Int, val height: Int, val refresh: Float)

    /**
     * 构建档位列表：同刷新率去重（同档保留分辨率最高的 mode），按刷新率降序。
     * 纯函数，便于 JVM 单测。
     */
    fun buildModes(raw: List<ModeInfo>): List<ModeInfo> =
        raw.sortedWith(
            compareByDescending<ModeInfo> { Math.round(it.refresh) }
                .thenByDescending { it.width.toLong() * it.height },
        ).distinctBy { Math.round(it.refresh) }

    /** 应用用户选择（modeId<=0 视为「自动」，恢复系统默认策略）。 */
    fun apply(activity: Activity, modeId: Int) {
        if (modeId <= 0) return
        val wm = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        // 1.170: 保留 defaultDisplay —— Context.getDisplay() 需 API30+，本项目 minSdk 更低，
        // 贸然替换会在低版本崩溃；此处置仅抑制废弃告警，运行行为完全不变。
        @Suppress("DEPRECATION")
        val supported = wm.defaultDisplay.supportedModes
        if (supported.none { it.modeId == modeId }) return
        val attrs = activity.window.attributes
        if (attrs.preferredDisplayModeId == modeId) return
        attrs.preferredDisplayModeId = modeId
        activity.window.attributes = attrs
    }
}
