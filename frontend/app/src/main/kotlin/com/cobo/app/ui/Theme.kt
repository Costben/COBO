package com.cobo.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.cobo.app.App
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.ColorSchemeMode
import io.github.suqi8.coui.kmp.theme.ThemeController
import io.github.suqi8.coui.kmp.theme.lightColorScheme

/**
 * The light page surface. COUI's light scheme pairs a white card with this grey page; leaving the
 * page white made every card invisible and the app look like one flat sheet.
 */
private val LightPage = Color(0xFFF0F1F2)

@Composable
fun isDarkTheme(): Boolean = when (App.settings.darkMode) {
    1 -> false
    2 -> true
    else -> isSystemInDarkTheme()
}

@Composable
fun CoboTheme(content: @Composable () -> Unit) {
    val mode = App.settings.darkMode
    val controller = remember(mode) {
        ThemeController(
            colorSchemeMode = when (mode) {
                1 -> ColorSchemeMode.Light
                2 -> ColorSchemeMode.Dark
                else -> ColorSchemeMode.System
            },
            lightColors = lightColorScheme(background = LightPage),
        )
    }
    val base = LocalDensity.current
    val scale = App.settings.fontScale
    val density = remember(base, scale) { Density(base.density, base.fontScale * scale) }

    COUITheme(controller = controller) {
        CompositionLocalProvider(LocalDensity provides density) {
            content()
        }
    }
}
