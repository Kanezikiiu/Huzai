package com.java.myapplication.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * 二级页全局状态：任意盖入式二级页（专区话题流、频道自定义等）打开时 +1、关闭时 -1。
 * MainActivity 据此隐藏/显示底部 Tab 栏（计数支持嵌套）。
 */
object SecondaryPage {
    var count by mutableIntStateOf(0)
        private set

    fun enter() {
        count++
    }

    fun exit() {
        if (count > 0) count--
    }

    /**
     * 归零：Activity（重）创建时调用。旋转屏幕等触发 Activity 重建时全部 Compose 状态清零
     * （二级页 overlay 全部消失），但本单例不随重建清零——若不归零，残留 count>0 会导致
     * 底部 Tab 栏永久隐藏（无任何二级页在打开状态却判定为隐藏）。
     */
    fun reset() {
        count = 0
    }
}