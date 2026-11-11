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

    /**
     * 1.224o：正文里点中「评分链接」时，**帖子页内嵌**一层原生评分页用的请求类型。
     *
     * 注意：这里只保留类型定义，不再有全局状态/投递 —— 帖子详情页直接把请求
     * 作为 `ScorePage(embedded = …)` 的参数传入，所以不涉及 Tab 切换、也没有全局竞态。
     */
    sealed interface ScoreDeepLink {
        /** 评分对象详情（选手 / 条目） */
        data class Item(val bizType: String, val bizNo: String) : ScoreDeepLink
        /** 对局评分（matchId；[channel] 为"在哪个赛程频道里找"的提示，可空） */
        data class Match(val matchId: String, val channel: String? = null) : ScoreDeepLink
    }

    /**
     * 1.336：底栏搜索按钮被点击。
     *
     * 搜索页挂在 [com.java.myapplication.ui.pages.HomePage] 内部（有它自己的 searchOpen 状态），
     * 而底栏按钮在 MainActivity —— 两者不在同一组合作用域。沿用本项目既有的「全局信号」模式：
     * 底栏自增该计数，首页侧 LaunchedEffect 观察到变化后打开搜索页。
     */
    var searchRequested by mutableIntStateOf(0)
        private set

    fun requestSearch() {
        searchRequested++
    }

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