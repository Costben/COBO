package com.cobo.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max

internal const val AVATAR_OUTPUT_SIZE = 720
internal const val COVER_OUTPUT_WIDTH = 1080
internal const val COVER_OUTPUT_HEIGHT = 1080

internal suspend fun decodeCropSourceBitmap(
    context: Context,
    uri: Uri,
    maxDim: Int = 2048,
): Bitmap = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val raw = if (Build.VERSION.SDK_INT >= 28) {
        val source = ImageDecoder.createSource(resolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val w = info.size.width
            val h = info.size.height
            val longest = max(w, h)
            if (longest > maxDim && longest > 0) {
                val ratio = maxDim.toFloat() / longest
                decoder.setTargetSize(
                    (w * ratio).toInt().coerceAtLeast(1),
                    (h * ratio).toInt().coerceAtLeast(1),
                )
            }
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > maxDim) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw IllegalStateException("无法解码图片")
    }
    if (raw.config == Bitmap.Config.HARDWARE) {
        raw.copy(Bitmap.Config.ARGB_8888, false)
            ?: throw IllegalStateException("无法转换图片格式")
    } else {
        raw
    }
}

internal fun clampCropOffset(
    offsetX: Float,
    offsetY: Float,
    bmpWidth: Int,
    bmpHeight: Int,
    cropWidthPx: Float,
    cropHeightPx: Float,
    scale: Float,
): Pair<Float, Float> {
    if (cropWidthPx <= 0f || cropHeightPx <= 0f || bmpWidth <= 0 || bmpHeight <= 0) return 0f to 0f
    val baseScale = max(cropWidthPx / bmpWidth.toFloat(), cropHeightPx / bmpHeight.toFloat())
    val maxDx = ((bmpWidth * baseScale * scale - cropWidthPx) / 2f).coerceAtLeast(0f)
    val maxDy = ((bmpHeight * baseScale * scale - cropHeightPx) / 2f).coerceAtLeast(0f)
    return offsetX.coerceIn(-maxDx, maxDx) to offsetY.coerceIn(-maxDy, maxDy)
}

internal fun cropBitmapToJpeg(
    source: Bitmap,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    cropWidthPx: Float,
    cropHeightPx: Float,
    outWidth: Int,
    outHeight: Int,
): ByteArray {
    val safeW = cropWidthPx.coerceAtLeast(1f)
    val safeH = cropHeightPx.coerceAtLeast(1f)
    val bmpW = source.width.toFloat().coerceAtLeast(1f)
    val bmpH = source.height.toFloat().coerceAtLeast(1f)
    val baseScale = max(safeW / bmpW, safeH / bmpH)
    val totalScale = baseScale * scale
    val outRatio = max(outWidth / safeW, outHeight / safeH)

    val out = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(out)
    val matrix = Matrix().apply {
        postTranslate(-bmpW / 2f, -bmpH / 2f)
        postScale(totalScale * outRatio, totalScale * outRatio)
        postTranslate(outWidth / 2f + offsetX * outRatio, outHeight / 2f + offsetY * outRatio)
    }
    canvas.drawColor(android.graphics.Color.BLACK)
    canvas.drawBitmap(source, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))

    val stream = ByteArrayOutputStream()
    out.compress(Bitmap.CompressFormat.JPEG, 92, stream)
    out.recycle()
    return stream.toByteArray()
}

/** 头像裁剪弹窗：1:1 裁剪框配圆形预览遮罩，确认后输出正方形 JPEG。 */
@Composable
fun AvatarCropDialog(
    sourceUri: Uri,
    uploading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (bytes: ByteArray, cropWidth: Int) -> Unit,
) {
    ImageCropDialog(
        sourceUri = sourceUri,
        title = "裁剪头像",
        summary = "拖动或双指缩放调整头像位置",
        confirmText = "上传头像",
        aspectRatio = 1f,
        circleMask = true,
        outWidth = AVATAR_OUTPUT_SIZE,
        outHeight = AVATAR_OUTPUT_SIZE,
        uploading = uploading,
        onDismiss = onDismiss,
        onConfirm = { bytes -> onConfirm(bytes, AVATAR_OUTPUT_SIZE) },
    )
}

/** 主页背景图裁剪弹窗：1:1 正方形取景（与微博封面原图一致），拖动和缩放后经二次确认上传。 */
@Composable
fun CoverCropDialog(
    sourceUri: Uri,
    uploading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (bytes: ByteArray) -> Unit,
) {
    ImageCropDialog(
        sourceUri = sourceUri,
        title = "裁剪背景图",
        summary = "拖动或双指缩放（1:1 封面，中间条带默认显示在主页顶部）",
        confirmText = "上传背景图",
        confirmDialogTitle = "确认更换背景图",
        confirmDialogMessage = "确定将裁剪后的图片设为新的个人主页背景图吗？",
        aspectRatio = 1f,
        circleMask = false,
        outWidth = COVER_OUTPUT_WIDTH,
        outHeight = COVER_OUTPUT_HEIGHT,
        uploading = uploading,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}

@Composable
private fun ImageCropDialog(
    sourceUri: Uri,
    title: String,
    summary: String,
    confirmText: String,
    confirmDialogTitle: String = "确认更换头像",
    confirmDialogMessage: String = "确定将裁剪后的图片设为新的个人头像吗？",
    aspectRatio: Float,
    circleMask: Boolean,
    outWidth: Int,
    outHeight: Int,
    uploading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (bytes: ByteArray) -> Unit,
) {
    val context = LocalContext.current
    var show by remember(sourceUri) { mutableStateOf(false) }
    var settled by remember(sourceUri) { mutableStateOf(false) }
    var bitmap by remember(sourceUri) { mutableStateOf<Bitmap?>(null) }
    var decodeError by remember(sourceUri) { mutableStateOf<String?>(null) }
    var scale by remember(sourceUri) { mutableFloatStateOf(1f) }
    var offsetX by remember(sourceUri) { mutableFloatStateOf(0f) }
    var offsetY by remember(sourceUri) { mutableFloatStateOf(0f) }
    var boxSize by remember(sourceUri) { mutableStateOf(IntSize.Zero) }
    var pendingUploadBytes by remember(sourceUri) { mutableStateOf<ByteArray?>(null) }

    LaunchedEffect(sourceUri) {
        show = true
        decodeError = null
        runCatching { decodeCropSourceBitmap(context, sourceUri) }
            .onSuccess { bitmap = it }
            .onFailure { decodeError = it.message ?: "图片加载失败" }
    }

    OverlayDialog(
        show = show,
        title = title,
        summary = summary,
        outsideMargin = DpSize(16.dp, 8.dp),
        onDismissRequest = { if (!uploading) show = false },
        onDismissFinished = {
            if (!settled) {
                settled = true
                onDismiss()
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .aspectRatio(aspectRatio)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF141414))
                    .clipToBounds()
                    .onSizeChanged { boxSize = it }
                    .pointerInput(bitmap, uploading) {
                        val bmp = bitmap ?: return@pointerInput
                        if (uploading) return@pointerInput
                        detectTransformGestures { _, pan, zoom, _ ->
                            val nextScale = (scale * zoom).coerceIn(1f, 4f)
                            val (clampedX, clampedY) = clampCropOffset(
                                offsetX = offsetX + pan.x,
                                offsetY = offsetY + pan.y,
                                bmpWidth = bmp.width,
                                bmpHeight = bmp.height,
                                cropWidthPx = boxSize.width.toFloat(),
                                cropHeightPx = boxSize.height.toFloat(),
                                scale = nextScale,
                            )
                            scale = nextScale
                            offsetX = clampedX
                            offsetY = clampedY
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                val bmp = bitmap
                when {
                    bmp != null -> {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    translationX = offsetX
                                    translationY = offsetY
                                },
                        )
                        if (circleMask) {
                            Canvas(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
                            ) {
                                drawRect(Color.Black.copy(alpha = 0.48f))
                                val radius = size.minDimension / 2f
                                drawCircle(
                                    color = Color.Transparent,
                                    radius = radius,
                                    blendMode = BlendMode.Clear,
                                )
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.85f),
                                    radius = radius - 1.dp.toPx(),
                                    style = Stroke(width = 1.5.dp.toPx()),
                                )
                            }
                        } else {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val topBand = size.height * 0.30f
                                val bottomBand = size.height * 0.70f
                                drawRect(
                                    color = Color.Black.copy(alpha = 0.24f),
                                    topLeft = Offset.Zero,
                                    size = androidx.compose.ui.geometry.Size(size.width, topBand),
                                )
                                drawRect(
                                    color = Color.Black.copy(alpha = 0.24f),
                                    topLeft = Offset(0f, bottomBand),
                                    size = androidx.compose.ui.geometry.Size(size.width, size.height - bottomBand),
                                )
                                drawLine(
                                    color = Color.White.copy(alpha = 0.55f),
                                    start = Offset(0f, topBand),
                                    end = Offset(size.width, topBand),
                                    strokeWidth = 1.dp.toPx(),
                                )
                                drawLine(
                                    color = Color.White.copy(alpha = 0.55f),
                                    start = Offset(0f, bottomBand),
                                    end = Offset(size.width, bottomBand),
                                    strokeWidth = 1.dp.toPx(),
                                )
                                val radiusPx = 14.dp.toPx()
                                drawRoundRect(
                                    color = Color.White.copy(alpha = 0.85f),
                                    cornerRadius = CornerRadius(radiusPx, radiusPx),
                                    style = Stroke(width = 1.5.dp.toPx()),
                                )
                            }
                        }
                    }
                    decodeError != null -> {
                        Text(
                            text = decodeError.orEmpty(),
                            color = COUITheme.colorScheme.error,
                            fontSize = 13.sp,
                        )
                    }
                    else -> {
                        CoboRefreshSpinner(size = 24.dp, strokeWidth = 2.4.dp)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            DialogButtonBar(
                negative = DialogButtonBarAction(
                    text = "取消",
                    onClick = { if (!uploading) show = false },
                ),
                positive = DialogButtonBarAction(
                    text = if (uploading) "上传中…" else confirmText,
                    recommended = true,
                    onClick = {
                        val bmp = bitmap ?: return@DialogButtonBarAction
                        if (uploading) return@DialogButtonBarAction
                        val boxW = boxSize.width.toFloat().coerceAtLeast(1f)
                        val boxH = boxSize.height.toFloat().coerceAtLeast(1f)
                        val jpeg = cropBitmapToJpeg(
                            source = bmp,
                            scale = scale,
                            offsetX = offsetX,
                            offsetY = offsetY,
                            cropWidthPx = boxW,
                            cropHeightPx = boxH,
                            outWidth = outWidth,
                            outHeight = outHeight,
                        )
                        pendingUploadBytes = jpeg
                    },
                ),
            )
        }
    }

    pendingUploadBytes?.let { bytes ->
        ConfirmDialog(
            title = confirmDialogTitle,
            message = confirmDialogMessage,
            confirmText = "确认上传",
            onDismiss = { pendingUploadBytes = null },
            onConfirm = {
                pendingUploadBytes = null
                onConfirm(bytes)
            },
        )
    }
}
