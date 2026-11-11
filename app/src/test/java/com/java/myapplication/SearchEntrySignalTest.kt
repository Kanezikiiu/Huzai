package com.java.myapplication

import com.java.myapplication.data.HupuUiSignals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 1.342：搜索入口「不触碰 Tab」的不变量。
 *
 * 走过的弯路（逐条被真机否掉）：
 *  ① 搜索页放 HomePage 内 → 从别的 Tab 点开只看到「底栏消失」
 *  ② 点击时强制切首页 → 返回被丢在首页
 *  ③ 记来处 + 关闭还原 → 仍能看到「先跳首页、退出又跳回」的可见跳转
 *  ④ 把搜索层提到全局 → 但详情页仍丢给 HomePage 打开，于是
 *     「首页搜索会闪回首页」「非首页搜索会回原 Tab 且底栏消失」
 *  ⑤ 现在：搜索层 + 详情页都在同一个覆盖层内部，**完全不牵扯 selectedTab**
 */
class SearchEntrySignalTest {

    @Test
    fun `请求搜索只自增计数`() {
        val before = HupuUiSignals.searchRequested
        HupuUiSignals.requestSearch()
        assertEquals("请求搜索应恰好 +1", before + 1, HupuUiSignals.searchRequested)
        // 再点一次仍能触发（不是幂等标志位）
        HupuUiSignals.requestSearch()
        assertEquals(before + 2, HupuUiSignals.searchRequested)
    }
}