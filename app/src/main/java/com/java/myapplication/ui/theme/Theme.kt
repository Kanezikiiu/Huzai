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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.java.myapplication.data.HupuPrefs

/** 浅色方案：中性底色 + 指定调色板的强调色。 */
internal fun lightSchemeOf(p: AccentPalette): ColorScheme = lightColorScheme(
    primary = p.lightPrimary,
    onPrimary = p.lightOnPrimary,
    primaryContainer = p.lightPrimaryContainer,
    onPrimaryContainer = p.lightOnPrimaryContainer,
    secondary = p.lightPrimary,
    secondaryContainer = p.lightPrimaryContainer,
    onSecondaryContainer = p.lightOnPrimaryContainer,
    tertiary = p.lightPrimary,
    background = LedgerBackgroundLight,
    onBackground = LedgerOnSurfaceLight,
    surface = LedgerSurfaceLight,
    onSurface = LedgerOnSurfaceLight,
    surfaceVariant = LedgerSurfaceVariantLight,
    onSurfaceVariant = LedgerOnSurfaceVariantLight,
    surfaceContainerLowest = LedgerSurfaceContainerLowestLight,
    surfaceContainerLow = LedgerSurfaceContainerLowLight,
    surfaceContainer = LedgerSurfaceContainerLight,
    surfaceContainerHigh = LedgerSurfaceContainerHighLight,
    surfaceContainerHighest = LedgerSurfaceContainerHighestLight,
    surfaceDim = LedgerSurfaceDimLight,
    surfaceBright = LedgerSurfaceBrightLight,
    outline = LedgerOutlineLight,
    outlineVariant = LedgerOutlineVariantLight,
    inverseSurface = LedgerInverseSurfaceLight,
    inverseOnSurface = LedgerInverseOnSurfaceLight,
    inversePrimary = p.darkPrimary,
    surfaceTint = p.lightPrimary,
    scrim = Color(0xFF000000),
)

/** 深色方案：同上，取调色板的深色档。 */
internal fun darkSchemeOf(p: AccentPalette): ColorScheme = darkColorScheme(
    // 1.218：深色强调色收敛为更浓郁的中调（原 tone80 近白粉彩是「发白」的根因）
    primary = p.darkPrimary.deepenedForDark(),
    onPrimary = p.darkOnPrimary,
    primaryContainer = p.darkPrimaryContainer,
    onPrimaryContainer = p.darkOnPrimaryContainer,
    secondary = p.darkPrimary.deepenedForDark(),
    secondaryContainer = p.darkPrimaryContainer,
    onSecondaryContainer = p.darkOnPrimaryContainer,
    tertiary = p.darkPrimary.deepenedForDark(),
    background = LedgerBackgroundDark,
    onBackground = LedgerOnSurfaceDark,
    surface = LedgerSurfaceDark,
    onSurface = LedgerOnSurfaceDark,
    surfaceVariant = LedgerSurfaceVariantDark,
    onSurfaceVariant = LedgerOnSurfaceVariantDark,
    surfaceContainerLowest = LedgerSurfaceContainerLowestDark,
    surfaceContainerLow = LedgerSurfaceContainerLowDark,
    surfaceContainer = LedgerSurfaceContainerDark,
    surfaceContainerHigh = LedgerSurfaceContainerHighDark,
    surfaceContainerHighest = LedgerSurfaceContainerHighestDark,
    surfaceDim = LedgerSurfaceDimDark,
    surfaceBright = LedgerSurfaceBrightDark,
    outline = LedgerOutlineDark,
    outlineVariant = LedgerOutlineVariantDark,
    inverseSurface = LedgerInverseSurfaceDark,
    inverseOnSurface = LedgerInverseOnSurfaceDark,
    inversePrimary = p.lightPrimary,
    surfaceTint = p.darkPrimary,
    scrim = Color(0xFF000000),
)

/**
 * 1.218：把深色强调色（M3 tone80，近白粉彩）收敛为更浓郁的中调 ——
 * 提高饱和度、略降明度，直接消除「发白」，同时保持与深色底的对比度。
 * 想更浓 / 更淡，只需调这两个系数。
 */
internal fun Color.deepenedForDark(): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(this.toArgb(), hsv)
    hsv[1] = maxOf(hsv[1] * 1.15f, 0.45f).coerceAtMost(1f)
    hsv[2] = (hsv[2] * 0.85f).coerceIn(0.35f, 1f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/** 1.218：整份深色方案（含动态取色）统一收敛强调色。 */
internal fun ColorScheme.deepenedAccents(): ColorScheme = copy(
    primary = primary.deepenedForDark(),
    secondary = secondary.deepenedForDark(),
    tertiary = tertiary.deepenedForDark(),
)

/**
 * 1.217：动态取色只借用壁纸衍生的**强调色**，中性面统一收敛到本应用的冷调色阶。
 * 这样「7 种预设 + 动态取色」的中性层次完全一致，不再出现壁纸中性面导致的发白/不同源。
 */
internal fun ColorScheme.withLedgerNeutrals(dark: Boolean): ColorScheme =
    if (dark) copy(
        background = LedgerBackgroundDark,
        onBackground = LedgerOnSurfaceDark,
        surface = LedgerSurfaceDark,
        onSurface = LedgerOnSurfaceDark,
        surfaceVariant = LedgerSurfaceVariantDark,
        onSurfaceVariant = LedgerOnSurfaceVariantDark,
        surfaceContainerLowest = LedgerSurfaceContainerLowestDark,
        surfaceContainerLow = LedgerSurfaceContainerLowDark,
        surfaceContainer = LedgerSurfaceContainerDark,
        surfaceContainerHigh = LedgerSurfaceContainerHighDark,
        surfaceContainerHighest = LedgerSurfaceContainerHighestDark,
        surfaceDim = LedgerSurfaceDimDark,
        surfaceBright = LedgerSurfaceBrightDark,
        outline = LedgerOutlineDark,
        outlineVariant = LedgerOutlineVariantDark,
        inverseSurface = LedgerInverseSurfaceDark,
        inverseOnSurface = LedgerInverseOnSurfaceDark,
    ) else copy(
        background = LedgerBackgroundLight,
        onBackground = LedgerOnSurfaceLight,
        surface = LedgerSurfaceLight,
        onSurface = LedgerOnSurfaceLight,
        surfaceVariant = LedgerSurfaceVariantLight,
        onSurfaceVariant = LedgerOnSurfaceVariantLight,
        surfaceContainerLowest = LedgerSurfaceContainerLowestLight,
        surfaceContainerLow = LedgerSurfaceContainerLowLight,
        surfaceContainer = LedgerSurfaceContainerLight,
        surfaceContainerHigh = LedgerSurfaceContainerHighLight,
        surfaceContainerHighest = LedgerSurfaceContainerHighestLight,
        surfaceDim = LedgerSurfaceDimLight,
        surfaceBright = LedgerSurfaceBrightLight,
        outline = LedgerOutlineLight,
        outlineVariant = LedgerOutlineVariantLight,
        inverseSurface = LedgerInverseSurfaceLight,
        inverseOnSurface = LedgerInverseOnSurfaceLight,
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
        // 1.217：动态取色只借壁纸强调色，中性面统一收敛到本应用冷调色阶
        accentId == ACCENT_DYNAMIC && isDynamicColorAvailable() ->
            (if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context))
                .let { if (darkTheme) it.deepenedAccents() else it }
                .withLedgerNeutrals(darkTheme)
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