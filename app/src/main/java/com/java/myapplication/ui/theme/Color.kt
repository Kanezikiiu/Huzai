package com.java.myapplication.ui.theme

import androidx.compose.ui.graphics.Color

// ---------- 中性色（所有色彩主题共用）----------
// 1.217：补全一整套「冷调中性」表面色阶，与底/面同源。
// 起因：原先只覆盖了 background/surface/surfaceVariant 等少数几个字段，其余
// （surfaceContainer*、onSurfaceVariant、outline*…）全部回落到 Material3 默认的
// 中性紫灰，因而深色模式观感「发白、没质感」，且动态取色的壁纸中性面也与自定义
// 底色不同源。现在统一收敛到这组冷调取值，深浅两套对称。
val LedgerBackgroundLight = Color(0xFFF7F9FC)
val LedgerSurfaceLight = Color(0xFFFFFFFF)
val LedgerSurfaceVariantLight = Color(0xFFDFE2EB)
val LedgerOnSurfaceLight = Color(0xFF191C20)
val LedgerOnSurfaceVariantLight = Color(0xFF5A6069)
val LedgerSurfaceContainerLowestLight = Color(0xFFFFFFFF)
val LedgerSurfaceContainerLowLight = Color(0xFFF4F6F9)
val LedgerSurfaceContainerLight = Color(0xFFEEF1F5)
val LedgerSurfaceContainerHighLight = Color(0xFFE8ECF1)
val LedgerSurfaceContainerHighestLight = Color(0xFFE1E5EB)
val LedgerSurfaceDimLight = Color(0xFFDADEE4)
val LedgerSurfaceBrightLight = Color(0xFFFFFFFF)
val LedgerOutlineLight = Color(0xFF8A9099)
val LedgerOutlineVariantLight = Color(0xFFC9CDD6)
val LedgerInverseSurfaceLight = Color(0xFF2E3238)
val LedgerInverseOnSurfaceLight = Color(0xFFF2F3F6)

val LedgerBackgroundDark = Color(0xFF111418)
val LedgerSurfaceDark = Color(0xFF1A1D22)
// 1.217：由 #41474D 调暗 —— 原值明显偏亮，是「发白」的主要来源之一
val LedgerSurfaceVariantDark = Color(0xFF2B3036)
val LedgerOnSurfaceDark = Color(0xFFE1E2E8)
val LedgerOnSurfaceVariantDark = Color(0xFFA6ACB6)
val LedgerSurfaceContainerLowestDark = Color(0xFF0C0F12)
val LedgerSurfaceContainerLowDark = Color(0xFF15181D)
val LedgerSurfaceContainerDark = Color(0xFF1C1F24)
val LedgerSurfaceContainerHighDark = Color(0xFF24272D)
val LedgerSurfaceContainerHighestDark = Color(0xFF2C3037)
val LedgerSurfaceDimDark = Color(0xFF0F1216)
val LedgerSurfaceBrightDark = Color(0xFF35393F)
val LedgerOutlineDark = Color(0xFF6E747D)
val LedgerOutlineVariantDark = Color(0xFF3A3F46)
val LedgerInverseSurfaceDark = Color(0xFFE1E2E8)
val LedgerInverseOnSurfaceDark = Color(0xFF2A2E33)

// 语义色（不随色彩主题变化）
val ExpenseRed = Color(0xFFE5484D)
val IncomeGreen = Color(0xFF30A46C)

/** 动态取色（Material You）的伪 id：由壁纸衍生配色，需 Android 12（API 31）+ */
const val ACCENT_DYNAMIC = "dynamic"

/**
 * 1.179: 一套色彩主题 = 一组强调色（primary 系列）的明暗取值。
 *
 * 只覆盖 primary / onPrimary / primaryContainer / onPrimaryContainer（secondary 跟随 primary），
 * 中性底色沿用 [LedgerBackgroundLight] 等——工作量为 8 色/套，且不会破坏现有视觉基调。
 * 取值遵循 Material 3 色调规则：浅色用 tone40/90 + 白字/深字，深色用 tone80/30 + 深字/浅字，
 * 保证对比度。
 */
data class AccentPalette(
    val id: String,
    val label: String,
    /** 设置页色块预览色（取该主题的代表色） */
    val swatch: Color,
    val lightPrimary: Color,
    val lightOnPrimary: Color,
    val lightPrimaryContainer: Color,
    val lightOnPrimaryContainer: Color,
    val darkPrimary: Color,
    val darkOnPrimary: Color,
    val darkPrimaryContainer: Color,
    val darkOnPrimaryContainer: Color,
)

/** 可选色彩主题（顺序即设置页展示顺序）。默认第一套为原品牌蓝。 */
val AccentPalettes: List<AccentPalette> = listOf(
    AccentPalette(
        id = "blue", label = "蓝",
        // 1.187: 原为 iOS 亮蓝 #0A84FF——与本套实际应用的 #00639A 差 ΔE 17.2，
        // 同一颗圆点上像并存两个不同的蓝。现 iOS 亮蓝已单独成一套主题，
        // 这里改回本套的实际色感，做到「色块 ≈ 应用色」。
        swatch = Color(0xFF00639A),
        lightPrimary = Color(0xFF00639A), lightOnPrimary = Color(0xFFFFFFFF),
        lightPrimaryContainer = Color(0xFFCCE5FF), lightOnPrimaryContainer = Color(0xFF001D33),
        darkPrimary = Color(0xFF97CBFF), darkOnPrimary = Color(0xFF003254),
        darkPrimaryContainer = Color(0xFF004A77), darkOnPrimaryContainer = Color(0xFFCCE5FF),
    ),
    AccentPalette(
        id = "ios", label = "iOS蓝",
        // 1.187: iOS 系统蓝。swatch 取 iOS 亮蓝（也是「蓝」套沿用至今的品牌亮色），
        // primary 取同色相的 tone40 档：纯 #0A84FF 用白底小字只有 4.0:1，
        // 压到 #0A6CE0 后对 #F7F9FC 达 4.7:1、对纯白 4.96:1，观感仍是 iOS 蓝。
        swatch = Color(0xFF0A84FF),
        lightPrimary = Color(0xFF0A6CE0), lightOnPrimary = Color(0xFFFFFFFF),
        lightPrimaryContainer = Color(0xFFCCE4FF), lightOnPrimaryContainer = Color(0xFF001B3D),
        darkPrimary = Color(0xFF8CC2FF), darkOnPrimary = Color(0xFF00305F),
        darkPrimaryContainer = Color(0xFF00468A), darkOnPrimaryContainer = Color(0xFFD3E4FF),
    ),
    AccentPalette(
        id = "teal", label = "墨绿",
        swatch = Color(0xFF2E9E75),
        lightPrimary = Color(0xFF1F6B52), lightOnPrimary = Color(0xFFFFFFFF),
        lightPrimaryContainer = Color(0xFFB4F1D6), lightOnPrimaryContainer = Color(0xFF002114),
        darkPrimary = Color(0xFF99D5B8), darkOnPrimary = Color(0xFF003825),
        darkPrimaryContainer = Color(0xFF00513A), darkOnPrimaryContainer = Color(0xFFB4F1D6),
    ),
    AccentPalette(
        id = "purple", label = "紫",
        swatch = Color(0xFF8B6BE0),
        lightPrimary = Color(0xFF6650A4), lightOnPrimary = Color(0xFFFFFFFF),
        lightPrimaryContainer = Color(0xFFEADDFF), lightOnPrimaryContainer = Color(0xFF21005D),
        darkPrimary = Color(0xFFD0BCFF), darkOnPrimary = Color(0xFF381E72),
        darkPrimaryContainer = Color(0xFF4F378B), darkOnPrimaryContainer = Color(0xFFEADDFF),
    ),
    AccentPalette(
        id = "orange", label = "橙",
        swatch = Color(0xFFF59E0B),
        lightPrimary = Color(0xFF8B5000), lightOnPrimary = Color(0xFFFFFFFF),
        lightPrimaryContainer = Color(0xFFFFDDB8), lightOnPrimaryContainer = Color(0xFF2C1700),
        darkPrimary = Color(0xFFFFB870), darkOnPrimary = Color(0xFF4A2800),
        darkPrimaryContainer = Color(0xFF693C00), darkOnPrimaryContainer = Color(0xFFFFDDB8),
    ),
    AccentPalette(
        id = "red", label = "红",
        swatch = Color(0xFFE5484D),
        lightPrimary = Color(0xFFB3261E), lightOnPrimary = Color(0xFFFFFFFF),
        lightPrimaryContainer = Color(0xFFFFDAD6), lightOnPrimaryContainer = Color(0xFF410E0B),
        darkPrimary = Color(0xFFF2B8B5), darkOnPrimary = Color(0xFF601410),
        darkPrimaryContainer = Color(0xFF8C1D18), darkOnPrimaryContainer = Color(0xFFF9DEDC),
    ),
    AccentPalette(
        id = "cyan", label = "青",
        swatch = Color(0xFF00BCD4),
        lightPrimary = Color(0xFF00696F), lightOnPrimary = Color(0xFFFFFFFF),
        lightPrimaryContainer = Color(0xFF9CF1F8), lightOnPrimaryContainer = Color(0xFF002022),
        darkPrimary = Color(0xFF80D4DC), darkOnPrimary = Color(0xFF00363B),
        darkPrimaryContainer = Color(0xFF004F55), darkOnPrimaryContainer = Color(0xFF9CF1F8),
    ),
)

/** 按 id 取调色板；未知 id 回落第一套（蓝），保证永不崩。 */
fun accentPaletteOf(id: String?): AccentPalette =
    AccentPalettes.firstOrNull { it.id == id } ?: AccentPalettes.first()

// 分类色板（统计图表与分类图标着色）
val CategoryColors = listOf(
    Color(0xFF0088FF), // 餐饮-蓝
    Color(0xFFE5484D), // 购物-红
    Color(0xFF30A46C), // 交通-绿
    Color(0xFFF5A524), // 娱乐-橙
    Color(0xFF8E4EC6), // 居家-紫
    Color(0xFF00B4D8), // 医疗-青
    Color(0xFFF76B15), // 学习-橘
    Color(0xFF6E56CF)  // 其他-靛
)