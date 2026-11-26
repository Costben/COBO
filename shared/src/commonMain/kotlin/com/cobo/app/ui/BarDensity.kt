package com.cobo.app.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import io.github.suqi8.coui.kmp.basic.AppBarBlurOverlayDefaults
import io.github.suqi8.coui.kmp.utils.rememberGalleryDensity

private const val PhoneMaxShortEdgeDp = 600f
private const val TargetBarShortEdgeDp = 360f
private const val BaselineDpi = 160f

internal data class CurrentDisplayInfo(
    val shortEdgePx: Int,
    val density: Float,
    val smallestWidthDp: Float,
    val isDefaultDisplay: Boolean,
)

@Composable
internal expect fun rememberCurrentDisplayInfo(): CurrentDisplayInfo?

@Composable
fun PinnedBarDensity(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDensity provides rememberBarDensity(), content = content)
}

@Composable
fun rememberBarDensity(): Density {
    val gallery = rememberGalleryDensity()
    val platform = LocalDensity.current
    val info = rememberCurrentDisplayInfo()
    return remember(gallery, platform, info) {
        if (info == null) {
            Density(density = gallery.density, fontScale = platform.fontScale)
        } else {
            computeBarDensity(
                galleryDensity = gallery,
                platformDensity = platform,
                shortEdgePx = info.shortEdgePx,
                displayDensity = info.density,
                smallestWidthDp = info.smallestWidthDp,
                isDefaultDisplay = info.isDefaultDisplay,
            )
        }
    }
}

internal fun computeBarDensity(
    galleryDensity: Density,
    platformDensity: Density,
    shortEdgePx: Int,
    displayDensity: Float,
    smallestWidthDp: Float,
    isDefaultDisplay: Boolean,
): Density {
    if (shortEdgePx <= 0 || smallestWidthDp >= PhoneMaxShortEdgeDp) {
        val target = if (isDefaultDisplay) {
            galleryDensity.density
        } else {
            platformDensity.density
        }
        return Density(density = target, fontScale = platformDensity.fontScale)
    }
    val shortEdge360Dpi = (shortEdgePx / TargetBarShortEdgeDp * BaselineDpi).toInt()
    val shortEdge360Density = shortEdge360Dpi / BaselineDpi
    val targetDensity = if (isDefaultDisplay) {
        maxOf(galleryDensity.density, shortEdge360Density)
    } else {
        shortEdge360Density
    }
    return Density(density = targetDensity, fontScale = platformDensity.fontScale)
}

@Composable
fun pinnedHeight(row: Dp): Dp = pinnedHeight(listOf(row))

@Composable
fun pinnedHeight(row1: Dp, row2: Dp): Dp = pinnedHeight(listOf(row1, row2))

@Composable
fun pinnedRowsHeight(rows: List<Dp>): Dp {
    val local = LocalDensity.current
    val rowsPx = with(rememberBarDensity()) { rows.fold(0f) { acc, row -> acc + row.toPx() } }
    return with(local) { rowsPx.toDp() }
}

@Composable
private fun pinnedHeight(rows: List<Dp>): Dp {
    val local = LocalDensity.current
    val statusPx = WindowInsets.statusBars.getTop(local)
    val rowsPx = with(rememberBarDensity()) { rows.fold(0f) { acc, row -> acc + row.toPx() } }
    return with(local) { (statusPx + rowsPx).toDp() }
}

fun glassBarContentTop(topBarHeight: Dp, gap: Dp): Dp =
    topBarHeight + AppBarBlurOverlayDefaults.ExtraHeight + gap
