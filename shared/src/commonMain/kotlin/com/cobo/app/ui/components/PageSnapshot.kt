package com.cobo.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize

data class PageSnapshot(val image: ImageBitmap, val windowSize: IntSize)

@Composable
fun rememberPageSnapshot(@Suppress("UNUSED_PARAMETER") active: Boolean): PageSnapshot? = null
