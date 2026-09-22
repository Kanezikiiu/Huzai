package com.java.myapplication.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.java.myapplication.data.HupuPrefs

/**
 * 1.190：「滚动时自动隐藏底栏」。
 *
 * 开启后（[HupuPrefs.loadAutoHideBar]）：
 * - 手指上滑（内容往下看）→ 底栏收缩隐藏；
 * - 手指下滑 → 底栏弹出。
 *
 * 作用对象取决于当前页面：4 大主页收起悬浮 Tab 栏，帖子详情页收起常驻操作条。
 *
 * 挂载方式：把 [autoHideScroll] 挂在**滚动容器的任意祖先**上即可——NestedScroll 会把
 * 子树里所有可滚动组件的滚动事件沿层次链上传到最近连接器，因此每页只需挂一次，
 * 不必逐个列表改造（4 大主页统一挂在 MainActivity 根节点上）。
 *
 * 只响应「跟手拖动」（[NestedScrollSource.UserInput]）：惯性滑动（fling）不翻转可见性，
 * 避免松手后底栏自己又跳出来。
 */
@Stable
class AutoHideBarState {
    var hidden by mutableStateOf(false)
        internal set

    /** 由 [rememberAutoHideBarState] 与设置开关同步；关闭时连接器直接放行 */
    internal var enabled = true

    internal val connection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (!enabled || source != NestedScrollSource.UserInput) return Offset.Zero
            if (available.y < -THRESHOLD_PX) hidden = true
            else if (available.y > THRESHOLD_PX) hidden = false
            return Offset.Zero
        }
    }

    /** 立即恢复显示（切页 / 关闭开关时调用，避免残留隐藏态） */
    fun show() {
        if (hidden) hidden = false
    }

    private companion object {
        /** 触发阈值（px）：小于它视为抖动，不改变可见性 */
        const val THRESHOLD_PX = 6f
    }
}

/** 建立与设置开关同步的 [AutoHideBarState]（订阅 autoHideBarVersion，改动即时生效）。 */
@Composable
fun rememberAutoHideBarState(): AutoHideBarState {
    HupuPrefs.autoHideBarVersion
    val enabled = HupuPrefs.loadAutoHideBar()
    val state = remember { AutoHideBarState() }
    SideEffect {
        state.enabled = enabled
        if (!enabled) state.show()
    }
    return state
}

/** 把「滚动时自动隐藏底栏」接到这个节点的子树滚动上（挂祖先节点即可）。 */
fun Modifier.autoHideScroll(state: AutoHideBarState): Modifier = this.nestedScroll(state.connection)
