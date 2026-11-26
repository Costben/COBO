package com.cobo.app.ui

import androidx.compose.ui.graphics.Color
import io.github.suqi8.coui.kmp.theme.darkColorScheme
import io.github.suqi8.coui.kmp.theme.lightColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThemeAccentTest {

    @Test
    fun parsesAndFormatsHexColors() {
        assertEquals(0xFFFF8200.toInt(), parseHexColor("#FF8200"))
        assertEquals(0xFFFF8200.toInt(), parseHexColor("ff8200"))
        assertEquals(0xFF0080FF.toInt(), parseHexColor(" #0080ff "))
        assertNull(parseHexColor("#FF820"))
        assertNull(parseHexColor("#GGGGGG"))
        assertNull(parseHexColor(""))

        assertEquals("#FF8200", formatHexColor(0xFFFF8200.toInt()))
        assertEquals("#0080FF", formatHexColor(0x000080FF))
    }

    @Test
    fun formatsAccentSummaryForDefaultPresetAndCustom() {
        assertEquals("默认", accentColorSummary(0))
        assertEquals("Jeb_ · 色相缓慢循环", accentColorSummary(AccentHueCycle))
        assertEquals("微博橙 (#FF8200)", accentColorSummary(0xFFFF8200.toInt()))
        assertEquals("自定义 (#123456)", accentColorSummary(0xFF123456.toInt()))
    }

    @Test
    fun hueCycleWrapsAcrossAngles() {
        assertEquals("Jeb_", AccentPresets.last().label)
        assertEquals(AccentHueCycle, AccentPresets.last().argb)
        val baseDark = darkColorScheme()
        val cycled = baseDark.withAccent(0xFF123456.toInt(), dark = true, keepFocusOutlineStatic = true)
        assertEquals(baseDark.focusOutline, cycled.focusOutline)
    }

    @Test
    fun withAccentOverridesPrimaryWhileKeepingSurfaces() {
        val baseLight = lightColorScheme(background = Color(0xFFF0F1F2))
        assertEquals(baseLight, baseLight.withAccent(0, dark = false))

        val orange = 0xFFFF8200.toInt()
        val themedLight = baseLight.withAccent(orange, dark = false)
        assertEquals(Color(orange), themedLight.primary)
        assertEquals(Color(orange), themedLight.containerTheme)
        assertEquals(baseLight.background, themedLight.background)
        assertEquals(baseLight.surfaceContainer, themedLight.surfaceContainer)

        val baseDark = darkColorScheme()
        val deepBlue = 0xFF050A30.toInt()
        val themedDark = baseDark.withAccent(deepBlue, dark = true)
        assertNotEquals(Color(deepBlue), themedDark.primary)
        assertEquals(baseDark.background, themedDark.background)
    }
}
