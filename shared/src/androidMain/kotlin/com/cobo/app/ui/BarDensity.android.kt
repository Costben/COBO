package com.cobo.app.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun rememberCurrentDisplayInfo(): CurrentDisplayInfo? {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    return remember(context, config) { readCurrentDisplayInfo(context, config) }
}

@Suppress("DEPRECATION")
private fun readCurrentDisplayInfo(context: Context, config: Configuration): CurrentDisplayInfo {
    val display = if (Build.VERSION.SDK_INT >= 30) {
        runCatching { context.display }.getOrNull()
    } else {
        null
    } ?: (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay

    val metrics = DisplayMetrics()
    if (display != null) {
        display.getRealMetrics(metrics)
    } else {
        metrics.setTo(context.resources.displayMetrics)
    }
    val shortEdgePx = minOf(metrics.widthPixels, metrics.heightPixels)
    val displayDensity = metrics.density
    val smallestWidthDp = if (shortEdgePx > 0 && displayDensity > 0f) {
        shortEdgePx / displayDensity
    } else {
        config.smallestScreenWidthDp.toFloat()
    }
    val isDefaultDisplay = display == null || display.displayId == Display.DEFAULT_DISPLAY
    return CurrentDisplayInfo(
        shortEdgePx = shortEdgePx,
        density = displayDensity,
        smallestWidthDp = smallestWidthDp,
        isDefaultDisplay = isDefaultDisplay,
    )
}
