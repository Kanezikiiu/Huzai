package com.java.myapplication.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.java.myapplication.data.HupuPrefs

/** 浅色方案：中性底色 + 指定调色板的强调色。 */
internal fun lightSchemeOf(p: AccentPalette): ColorScheme = lightColorScheme(
    primary = p.lightPrimary,
    onPrimary = p.lightOnPrimary,
    primaryContainer = p.lightPrimaryContainer,
    onPrimaryContainer = p.lightOnPrimaryContainer,
    secondary = p.lightPrimary,
    background = LedgerBackgroundLight,
    onBackground = LedgerOnSurfaceLight,
    surface = LedgerSurfaceLight,
    onSurface = LedgerOnSurfaceLight,
    surfaceVariant = LedgerSurfaceVariantLight,
)

/** 深色方案：同上，取调色板的深色档。 */
internal fun darkSchemeOf(p: AccentPalette): ColorScheme = darkColorScheme(
    primary = p.darkPrimary,
    onPrimary = p.darkOnPrimary,
    primaryContainer = p.darkPrimaryContainer,
    onPrimaryContainer = p.darkOnPrimaryContainer,
    secondary = p.darkPrimary,
    background = LedgerBackgroundDark,
    onBackground = LedgerOnSurfaceDark,
    surface = LedgerSurfaceDark,
    onSurface = LedgerOnSurfaceDark,
    surfaceVariant = LedgerSurfaceVariantDark,
)

/** 动态取色是否可用（Material You 需 Android 12 / API 31+）。 */
fun isDynamicColorAvailable(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

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

/**
 * 1.179: 主题 = 深浅模式（由 darkTheme 决定）× 色彩主题（由 HupuPrefs 决定）。
 *
 * 色彩主题订阅 [HupuPrefs.colorThemeVersion]，设置页点选后立即重组生效，无需重启。
 * 选「动态取色」且系统为 Android 12+ 时使用壁纸衍生配色（其余情况按 id 查调色板）。
 */
@Composable
fun LedgerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val accentId = remember(HupuPrefs.colorThemeVersion) { HupuPrefs.loadColorTheme() }
    val colorScheme: ColorScheme = when {
        accentId == ACCENT_DYNAMIC && isDynamicColorAvailable() ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else -> {
            val p = accentPaletteOf(accentId)
            if (darkTheme) darkSchemeOf(p) else lightSchemeOf(p)
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}