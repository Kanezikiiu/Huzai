package com.java.myapplication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 1.351：图标别名组件的**查找规则**。
 *
 * 踩过的两个坑（都真机复现过）：
 *  ① 包名写死成 namespace（com.java.myapplication），而实际 applicationId 是 com.huzai.app
 *     → ComponentName 指向不存在的组件。
 *  ② 改成运行时包名后仍然找不到 —— 因为清单里的 `.LauncherPeek` 会被 AGP 按 **namespace**
 *     展开成 `com.java.myapplication.LauncherPeek`，而 `context.packageName` 是
 *     `com.huzai.app`，两者拼起来是错的。
 *
 * 结论：**不能靠字符串拼装**，必须从 PackageManager 查出的真实 ActivityInfo.name 里按后缀匹配。
 * 这里固化"后缀匹配"这一规则。
 */
class IconAliasNameTest {

    /** 与 AppIconSwitcher.findAlias 相同的匹配规则 */
    private fun match(installed: List<String>, suffix: String): String? =
        installed.firstOrNull { it.endsWith(suffix) }

    @Test
    fun `按后缀匹配能命中命名空间展开后的名字`() {
        // 真实情况：清单展开后是 namespace 前缀
        val installed = listOf(
            "com.java.myapplication.MainActivity",
            "com.java.myapplication.LauncherPeek",
            "com.java.myapplication.LauncherClassic",
        )
        assertEquals(
            "com.java.myapplication.LauncherPeek",
            match(installed, ".LauncherPeek"),
        )
        assertEquals(
            "com.java.myapplication.LauncherClassic",
            match(installed, ".LauncherClassic"),
        )
    }

    @Test
    fun `不会把 Peek 误判成 Classic`() {
        val installed = listOf("com.java.myapplication.LauncherPeek")
        assertNull(match(installed, ".LauncherClassic"))
    }

    @Test
    fun `没有别名时返回 null 而不是瞎猜`() {
        val installed = listOf("com.java.myapplication.MainActivity")
        assertNull(match(installed, ".LauncherPeek"))
        assertNull(match(installed, ".LauncherClassic"))
    }

    @Test
    fun `拼接方案是错的 不能退回去`() {
        // 记录反例：applicationId 拼出来的名字在清单里并不存在
        val appId = "com.huzai.app"
        val installed = listOf("com.java.myapplication.LauncherPeek")
        assertFalse(installed.contains("$appId.LauncherPeek"))
    }
}