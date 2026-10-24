package com.cobo.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.github.suqi8.coui.kmp.basic.AppBarBlurOverlayDefaults

/**
 * The page surface the system apps paint behind their cards: the theme's surface in the light
 * theme, `#303030` in the dark theme. COUI's dark `background` is `#1E1E1E`, which reads a full
 * step darker than the platform pages and leaves every card floating on a darker sheet.
 */
@Composable
fun pageContainerColor(): Color = AppBarBlurOverlayDefaults.containerColor()
