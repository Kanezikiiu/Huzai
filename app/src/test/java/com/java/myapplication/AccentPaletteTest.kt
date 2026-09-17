package com.java.myapplication

import androidx.compose.ui.graphics.Color
import com.java.myapplication.data.HupuPrefs
import com.java.myapplication.ui.theme.ACCENT_DYNAMIC
import com.java.myapplication.ui.theme.AccentPalettes
import com.java.myapplication.ui.theme.accentPaletteOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.179 色彩主题表完整性：id/名称唯一、默认值与偏好一致、未知 id 回落、颜色不透明。
 */
class AccentPaletteTest {

    @Test
    fun palettesAreUniqueAndWellFormed() {
        assertTrue(AccentPalettes.isNotEmpty())
        assertEquals(AccentPalettes.size, AccentPalettes.map { it.id }.toSet().size)
        assertEquals(AccentPalettes.size, AccentPalettes.map { it.label }.toSet().size)
        AccentPalettes.forEach {
            assertTrue("id 不能为空", it.id.isNotBlank())
            assertTrue("label 不能为空", it.label.isNotBlank())
            assertFalse("固定调色板 id 不得与动态取色伪 id 冲突", it.id == ACCENT_DYNAMIC)
        }
    }

    @Test
    fun defaultMatchesPrefsAndFirstPalette() {
        // 偏好层默认值必须存在且与首套一致（否则首次启动会静默回落到别的颜色）
        assertEquals("blue", HupuPrefs.DEFAULT_COLOR_THEME)
        assertEquals(HupuPrefs.DEFAULT_COLOR_THEME, AccentPalettes.first().id)
    }

    @Test
    fun lookupByIdAndFallback() {
        assertEquals("teal", accentPaletteOf("teal").id)
        // 未知 / null / 动态伪 id 一律回落首套，保证永不崩
        assertEquals(AccentPalettes.first().id, accentPaletteOf(null).id)
        assertEquals(AccentPalettes.first().id, accentPaletteOf("no-such-id").id)
        assertEquals(AccentPalettes.first().id, accentPaletteOf(ACCENT_DYNAMIC).id)
    }

    @Test
    fun allPaletteColorsAreOpaque() {
        val colors: List<Color> = AccentPalettes.flatMap {
            listOf(
                it.swatch,
                it.lightPrimary, it.lightOnPrimary,
                it.lightPrimaryContainer, it.lightOnPrimaryContainer,
                it.darkPrimary, it.darkOnPrimary,
                it.darkPrimaryContainer, it.darkOnPrimaryContainer,
            )
        }
        assertEquals(AccentPalettes.size * 9, colors.size)
        colors.forEach { assertEquals("颜色必须不透明（0xAARRGGBB）", 1.0f, it.alpha, 0.001f) }
    }
}