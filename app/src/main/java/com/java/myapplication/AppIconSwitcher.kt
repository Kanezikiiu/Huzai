package com.java.myapplication

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * 1.350：应用图标切换（桌面 / 最近任务）。
 *
 * ## 1.350 修掉的核心 bug（真机两轮「只有软件内生效」）
 *
 * 原来包名**写死**成 `com.java.myapplication`，但本项目
 * `applicationId = "com.huzai.app"`（命名空间与 applicationId 故意不同）。
 * 于是 `ComponentName("com.java.myapplication", "...LauncherPeek")` 指向一个**不存在的组件**，
 * `setComponentEnabledSetting` 抛异常 —— 而外层套了 `runCatching`，异常被静默吞掉，
 * 界面上就是「点了没反应，只有软件内变」。
 *
 * 现在：
 *  · 包名一律取 **`context.packageName`**（永远是真实 applicationId）；
 *  · **不再吞异常**，失败原因回传给界面提示；
 *  · 切换前先校验组件真实存在，避免无声失败。
 *
 * ## 能做到什么（平台边界，如实说明）
 *  · 桌面 / 抽屉 / 最近任务 → 跟随当前启用的 alias → ✅
 *  · 本应用内（关于页）    → 我们自己的代码读偏好      → ✅
 *  · 系统设置→应用信息     → 读 `<application android:icon>`（编译期常量） → ❌
 */
object AppIconSwitcher {

    /** 0 = 左侧探头（新，默认）；1 = 经典（旧） */
    const val PEEK = 0
    const val CLASSIC = 1

    /** 切换结果，供界面提示用 */
    sealed interface Result {
        data object Ok : Result
        /** 组件不在清单里（多半是包名 / 别名不匹配） */
        data class NoSuchComponent(val name: String) : Result
        data class Failed(val message: String) : Result
    }

    /**
     * 1.351：**从系统查询真实的别名组件名**，而不是靠字符串拼装。
     *
     * 为什么会拼错（真机报错「找不到启动器组件 com.huzai.app.LauncherClassic」）：
     * 清单里写的是 `android:name=".LauncherPeek"`，AGP 会按 **namespace**（com.java.myapplication）
     * 展开相对名 → 组件全名是 `com.java.myapplication.LauncherPeek`；
     * 而运行时 `context.packageName` 是 **applicationId**（com.huzai.app）。
     * 两者拼出来的 "com.huzai.app.LauncherPeek" 自然不存在。
     *
     * 正确做法：包名用 applicationId（系统认这个），**类名用清单展开后的真实全名** ——
     * 直接从 PackageManager 里按后缀找，杜绝拼装猜测。
     */
    private fun findAlias(context: Context, suffix: String): ComponentName? {
        val pm = context.packageManager
        val pkg = context.packageName
        val info = runCatching {
            pm.getPackageInfo(
                pkg,
                android.content.pm.PackageManager.GET_ACTIVITIES or
                    android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS,
            )
        }.getOrNull() ?: return null
        val hit = info.activities?.firstOrNull { it.name.endsWith(suffix) } ?: return null
        // ⚠️ 包名必须是 applicationId，类名是系统里记录的完整名
        return ComponentName(pkg, hit.name)
    }

    private fun alias(context: Context, icon: Int): ComponentName? =
        findAlias(context, if (icon == CLASSIC) ".LauncherClassic" else ".LauncherPeek")

    /**
     * 应用指定图标。
     *
     * 顺序：**先启用目标、再禁用另一个**（反过来会出现"桌面一个图标都没有"的瞬间）。
     */
    fun apply(context: Context, icon: Int): Result {
        val pm = context.packageManager
        val target = alias(context, icon)
            ?: return Result.NoSuchComponent(
                if (icon == CLASSIC) "LauncherClassic" else "LauncherPeek"
            )
        val other = alias(context, if (icon == CLASSIC) PEEK else CLASSIC)
            ?: return Result.NoSuchComponent(
                if (icon == CLASSIC) "LauncherPeek" else "LauncherClassic"
            )

        // ② 已是目标状态就不再动（反复启停会让部分 ROM 反复增删图标）
        val targetOn = runCatching {
            pm.getComponentEnabledSetting(target) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }.getOrDefault(false)
        val otherOn = runCatching {
            pm.getComponentEnabledSetting(other) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }.getOrDefault(false)
        if (targetOn && !otherOn) return Result.Ok

        // ③ 不吞异常：失败要把原因带回去
        return try {
            pm.setComponentEnabledSetting(
                target,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
            pm.setComponentEnabledSetting(
                other,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
            notifyLauncher(context)
            Result.Ok
        } catch (e: Exception) {
            Result.Failed(e.message ?: e::class.java.simpleName)
        }
    }

    /**
     * 轻量通知（广播）。
     *
     * 只发广播通常不够 —— 国产 ROM 的启动器常忽略它。真正让它们重读的是**进程重启**，
     * 见 [restartApp]。
     */
    private fun notifyLauncher(context: Context) {
        runCatching {
            val intent = android.content.Intent(android.content.Intent.ACTION_PACKAGE_CHANGED)
            intent.data = android.net.Uri.parse("package:${context.packageName}")
            intent.putExtra(android.content.Intent.EXTRA_DATA_REMOVED, false)
            intent.putExtra(android.content.Intent.EXTRA_REPLACING, true)
            context.sendBroadcast(intent)
        }
    }

    /** 冷启动时把清单状态与用户选择对齐（例如用户清除数据后回到默认） */
    fun syncOnStart(context: Context) {
        apply(context, com.java.myapplication.data.HupuPrefs.loadAppIcon())
    }

    /**
     * 切换图标后**主动结束进程**，让系统重新 bind 组件。
     *
     * 进程死亡后桌面才会重读 alias 的 icon、最近任务才会丢掉旧的 ActivityInfo。
     *
     * 1.353：真机要求「先给提示、停一小会儿再退出」—— 之前 150ms 太快，
     * 用户根本看不清 Toast。现在留 [EXIT_DELAY_MS] 让提示可读，
     * 期间界面照常（不主动 finish，避免出现"提示还没看清页面就没了"）。
     */
    private const val EXIT_DELAY_MS = 1_500L

    fun restartApp(context: Context) {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            val activity = findActivity(context)
            runCatching { activity?.finishAffinity() }
            // 再等一帧让 finish 走完，然后杀进程
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                runCatching {
                    android.os.Process.killProcess(android.os.Process.myPid())
                    kotlin.system.exitProcess(0)
                }
            }, 120)
        }, EXIT_DELAY_MS)
    }

    private fun findActivity(context: Context): android.app.Activity? {
        var c: Context? = context
        while (c is android.content.ContextWrapper) {
            if (c is android.app.Activity) return c
            c = c.baseContext
        }
        return null
    }
}