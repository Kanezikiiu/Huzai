package com.java.myapplication

import androidx.compose.ui.graphics.vector.addPathNodes
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.191: 图标 path 是**运行时**由 addPathNodes 解析的（编译器不校验），
 * 语法错误不会编译报错，而是首次访问该 ImageVector 时抛异常崩溃。
 * 这里对该次新增/改写的 path 做一次解析冒烟，防止「编译通过、真机崩溃」。
 */
class HupuIconPathTest {

    /** 重绘后的评论气泡（弧线 flag 用空格分隔） */
    @Test
    fun commentBubblePathParses() {
        val nodes = addPathNodes("M6,4H18A4,4 0 0 1 22,8V16A4,4 0 0 1 18,20H6L2,24V8A4,4 0 0 1 6,4Z")
        assertTrue(nodes.isNotEmpty())
    }

    /** 弧线的另一种紧凑写法（无空格，flag 粘连）也应能被解析——用于回归 PathParser 行为 */
    @Test
    fun arcFlagsWithoutSeparator() {
        val nodes = addPathNodes("M6,4H18A4,400122,8V16A4,400118,20H6L2,24V8A4,40016,4Z")
        assertTrue(nodes.isNotEmpty())
    }
}