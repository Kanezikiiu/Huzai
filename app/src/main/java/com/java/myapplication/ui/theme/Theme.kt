package com.java.myapplication.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.java.myapplication.data.HupuPrefs

private val DarkColorScheme = darkColorScheme(
    primary = LedgerPrimaryDark,
    onPrimary = LedgerOnPrimaryDark,
    primaryContainer = LedgerPrimaryContainerDark,
    onPrimaryContainer = LedgerOnPrimaryContainerDark,
    secondary = LedgerPrimaryDark,
    background = LedgerBackgroundDark,
    onBackground = LedgerOnSurfaceDark,
    surface = LedgerSurfaceDark,
    onSurface = LedgerOnSurfaceDark,
    surfaceVariant = LedgerSurfaceVariantDark
)

private val LightColorScheme = lightColorScheme(
    primary = LedgerPrimaryLight,
    onPrimary = LedgerOnPrimaryLight,
    primaryContainer = LedgerPrimaryContainerLight,
    onPrimaryContainer = LedgerOnPrimaryContainerLight,
    secondary = LedgerPrimaryLight,
    background = LedgerBackgroundLight,
    onBackground = LedgerOnSurfaceLight,
    surface = LedgerSurfaceLight,
    onSurface = LedgerOnSurfaceLight,
    surfaceVariant = LedgerSurfaceVariantLight
)

/**
 * 1.130: 按用户主题偏好解析「是否深色」——跟随系统 / 浅色 / 深色。
 * 订阅 HupuPrefs.themeModeVersion：设置页切换后整个应用即时重组（无需重启）。
 */
@Composable
fun isAppDarkTheme(): Boolean {
    val mode = remember(HupuPrefs.themeModeVersion) { HupuPrefs.loadThemeMode() }
    return when (mode) {
        HupuPrefs.THEME_LIGHT -> false
        HupuPrefs.THEME_DARK -> true
        else -> isSystemInDarkTheme()
    }
}

@Composable
fun LedgerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // 动态取色 M3 再接入；M1 固定品牌蓝，保证玻璃栏视觉一致
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}