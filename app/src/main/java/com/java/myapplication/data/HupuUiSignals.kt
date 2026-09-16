package com.java.myapplication.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Tab 重选信号（点击已选中 Tab 触发页面刷新）。
 * 页面常驻组合（切 Tab 不重组），不能靠参数传刷新事件——用 object 单例的
 * observable state 传信号（与 HupuPrefs.homeTopicsVersion 同款模式）。
 * MainActivity 的 Tab onClick 检测重选并递增；对应页面 LaunchedEffect 观察并刷新。
 */
object HupuUiSignals {
    /** 首页 Tab 重选（已选中时再点） */
    var homeTabTap by mutableIntStateOf(0)
        private set

    /** 评分 Tab 重选（已选中时再点） */
    var scoreTabTap by mutableIntStateOf(0)
        private set

    /** 评分 Tab 被选中（切入评分页）。页面常驻组合，冷启动首屏加载可能已失败；
     *  切入时据此做一次补偿重试，避免看到残留的失败态。 */
    var scoreTabShown by mutableIntStateOf(0)
        private set

    fun tapHome() {
        homeTabTap++
    }

    fun tapScore() {
        scoreTabTap++
    }

    fun enterScore() {
        scoreTabShown++
    }
}
