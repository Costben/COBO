package com.cobo.app.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.roundToInt

private const val SNAPSHOT_SCALE = 1f / 6f
private const val SNAPSHOT_BLUR_DP = 10f
private const val BOX_BLUR_PASSES = 3

@Composable
actual fun rememberPageSnapshot(active: Boolean): PageSnapshot? {
    val view = LocalView.current
    var snapshot by remember { mutableStateOf<PageSnapshot?>(null) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        val captured = capturePage(view) ?: return@LaunchedEffect
        snapshot = captured
    }
    return snapshot
}

private suspend fun capturePage(view: View): PageSnapshot? {
    val window = view.context.findActivity()?.window ?: return null
    val decor = window.decorView
    val width = decor.width
    val height = decor.height
    if (width <= 0 || height <= 0) return null

    val full = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val copied = suspendCancellableCoroutine { continuation ->
        PixelCopy.request(
            window,
            full,
            { result -> continuation.resume(result == PixelCopy.SUCCESS) },
            Handler(Looper.getMainLooper()),
        )
    }
    if (!copied) {
        full.recycle()
        return null
    }

    val image = withContext(Dispatchers.Default) {
        val smallWidth = max(1, (width * SNAPSHOT_SCALE).roundToInt())
        val smallHeight = max(1, (height * SNAPSHOT_SCALE).roundToInt())
        val small = Bitmap.createScaledBitmap(full, smallWidth, smallHeight, true)
        full.recycle()
        val radius = (SNAPSHOT_BLUR_DP * decor.resources.displayMetrics.density * SNAPSHOT_SCALE)
            .roundToInt()
            .coerceAtLeast(1)
        boxBlur(small, radius)
        small.asImageBitmap()
    }
    return PageSnapshot(image = image, windowSize = IntSize(width, height))
}

private fun boxBlur(bitmap: Bitmap, radius: Int) {
    if (radius <= 0) return
    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    val scratch = IntArray(pixels.size)
    repeat(BOX_BLUR_PASSES) {
        blurPass(pixels, scratch, width, height, radius, horizontal = true)
        blurPass(scratch, pixels, width, height, radius, horizontal = false)
    }
    bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
}

private fun blurPass(
    source: IntArray,
    target: IntArray,
    width: Int,
    height: Int,
    radius: Int,
    horizontal: Boolean,
) {
    val lineLength = if (horizontal) width else height
    val lineCount = if (horizontal) height else width
    val window = radius * 2 + 1
    for (line in 0 until lineCount) {
        var a = 0
        var r = 0
        var g = 0
        var b = 0
        for (offset in -radius..radius) {
            val index = lineIndex(line, offset.coerceIn(0, lineLength - 1), width, horizontal)
            val pixel = source[index]
            a += pixel ushr 24 and 0xFF
            r += pixel ushr 16 and 0xFF
            g += pixel ushr 8 and 0xFF
            b += pixel and 0xFF
        }
        for (position in 0 until lineLength) {
            target[lineIndex(line, position, width, horizontal)] =
                (a / window shl 24) or (r / window shl 16) or (g / window shl 8) or (b / window)
            val outgoing = source[lineIndex(line, (position - radius).coerceIn(0, lineLength - 1), width, horizontal)]
            val incoming = source[lineIndex(line, (position + radius + 1).coerceIn(0, lineLength - 1), width, horizontal)]
            a += (incoming ushr 24 and 0xFF) - (outgoing ushr 24 and 0xFF)
            r += (incoming ushr 16 and 0xFF) - (outgoing ushr 16 and 0xFF)
            g += (incoming ushr 8 and 0xFF) - (outgoing ushr 8 and 0xFF)
            b += (incoming and 0xFF) - (outgoing and 0xFF)
        }
    }
}

private fun lineIndex(line: Int, position: Int, width: Int, horizontal: Boolean): Int =
    if (horizontal) line * width + position else position * width + line

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
