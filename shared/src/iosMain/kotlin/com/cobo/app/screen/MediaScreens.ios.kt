package com.cobo.app.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.unit.IntSize
import com.cobo.app.ui.NavState

internal actual suspend fun platformTopBandIsLight(
    url: String,
    screenWidthDp: Int,
    screenHeightDp: Int,
): Boolean = false

@Composable
internal actual fun PlatformTiledLongImageOverlay(
    url: String,
    original: Boolean,
    screenWidthPx: Int,
    box: IntSize,
    scale: Float,
    offset: Offset,
    rest: ComposeSize,
    onAspect: (Float) -> Unit,
) = Unit

@Composable
internal actual fun PlatformWebVideoPlayer(
    nav: NavState,
    pageUrl: String,
    title: String,
    onDownload: (String) -> Unit,
    onFlash: (String) -> Unit,
) {
    WebScreen(nav = nav, url = pageUrl, title = title.ifBlank { "视频" })
}
