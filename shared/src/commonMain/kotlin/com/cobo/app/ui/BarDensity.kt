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

@Composable
fun PinnedBarDensity(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDensity provides rememberBarDensity(), content = content)
}

@Composable
fun rememberBarDensity(): Density {
    val gallery = rememberGalleryDensity()
    val platform = LocalDensity.current
    return remember(gallery, platform) {
        Density(density = gallery.density, fontScale = platform.fontScale)
    }
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
