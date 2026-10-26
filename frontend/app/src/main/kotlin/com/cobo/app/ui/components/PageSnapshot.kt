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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 一帧页面快照，给独立窗口里的菜单当背景用。
 *
 * @param image 已经模糊过的页面，按 [windowSize] 的 [SNAPSHOT_SCALE] 分之一存。
 * @param windowSize 截下来的窗口尺寸（像素），菜单按它把快照放回原位。
 */
data class PageSnapshot(val image: ImageBitmap, val windowSize: IntSize)

/** 快照的下采样比例：模糊半径按这个比例一起缩，画的时候再插值放大回去。 */
private const val SNAPSHOT_SCALE = 1f / 6f

/**
 * 模糊半径（dp）。
 *
 * 盒式模糊的等效高斯 sigma 约等于盒宽的一半，三遍下来 sigma ≈ 1.7 × 半径：这里取 10dp，
 * 在 3.32 密度上落到 sigma ≈ 33px，透过 66% 白之后是「看得出色块、看不清内容」的毛玻璃，
 * 再大就糊成一块平色，再小照片细节会从白层里透出来变成「白纱」。
 */
private const val SNAPSHOT_BLUR_DP = 10f

/** 三遍盒式模糊的等效高斯半径，按 [SNAPSHOT_SCALE] 缩过。 */
private const val BOX_BLUR_PASSES = 3

/**
 * 菜单弹出瞬间抓一帧当前页面，模糊后交给独立窗口里的菜单当玻璃底。
 *
 * 独立窗口没有页面可采样，跨窗口的图层也读不到，所以只能自己截：PixelCopy 抓的是本 Activity
 * 的窗口表面，菜单自己那扇窗不在里面，抓到的就是菜单弹出前的页面，位置和视觉完全对得上。
 * 抓不到（窗口还没画、或者设备拒绝）就返回 null，菜单退回实心表面。
 *
 * 收起时不清空：留着上一帧，下次弹出头两帧就有底图，不必等这次抓完才从纯白跳成玻璃；
 * 同一页上再弹，新旧两帧几乎一样，看不出替换。
 *
 * @param active 菜单是否已经弹出。
 */
@Composable
fun rememberPageSnapshot(active: Boolean): PageSnapshot? {
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
        // 半径按缩小后的像素算：10dp 在原图上约 33px，缩到 1/6 就是五六个像素。
        val radius = (SNAPSHOT_BLUR_DP * decor.resources.displayMetrics.density * SNAPSHOT_SCALE)
            .roundToInt()
            .coerceAtLeast(1)
        boxBlur(small, radius)
        small.asImageBitmap()
    }
    return PageSnapshot(image = image, windowSize = IntSize(width, height))
}

/**
 * 就地三遍盒式模糊。缩小之后的图只有几万像素，三遍走下来是个位数的毫秒，
 * 比挂 RenderEffect 图层省事，也不用为菜单动画每帧维护一个全屏图层。
 */
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

/**
 * 一遍盒式模糊。[source] 读、[target] 写，两个方向分开走，这样每个像素只算一次加法和一次减法。
 */
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
