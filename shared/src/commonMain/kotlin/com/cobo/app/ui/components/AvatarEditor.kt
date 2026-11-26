package com.cobo.app.ui.components

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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cobo.app.platform.PickedMediaRegistry
import com.cobo.app.platform.PlatformUri
import com.cobo.app.platform.cropAndEncodeJpeg
import com.cobo.app.platform.decodeImageBitmap
import com.cobo.app.platform.ioDispatcher
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

internal const val AVATAR_OUTPUT_SIZE = 720
internal const val COVER_OUTPUT_WIDTH = 1080
internal const val COVER_OUTPUT_HEIGHT = 1080

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

@Composable
fun AvatarCropDialog(
    sourceUri: PlatformUri,
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
        uploading = uploading,
        onDismiss = onDismiss,
        onConfirm = { bytes -> onConfirm(bytes, AVATAR_OUTPUT_SIZE) },
    )
}

@Composable
fun CoverCropDialog(
    sourceUri: PlatformUri,
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
        uploading = uploading,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}

@Composable
private fun ImageCropDialog(
    sourceUri: PlatformUri,
    title: String,
    summary: String,
    confirmText: String,
    confirmDialogTitle: String = "确认更换头像",
    confirmDialogMessage: String = "确定将裁剪后的图片设为新的个人头像吗？",
    aspectRatio: Float,
    circleMask: Boolean,
    outWidth: Int,
    uploading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (bytes: ByteArray) -> Unit,
) {
    var show by remember(sourceUri) { mutableStateOf(false) }
    var settled by remember(sourceUri) { mutableStateOf(false) }
    var rawBytes by remember(sourceUri) { mutableStateOf<ByteArray?>(null) }
    var bitmap by remember(sourceUri) { mutableStateOf<ImageBitmap?>(null) }
    var decodeError by remember(sourceUri) { mutableStateOf<String?>(null) }
    var scale by remember(sourceUri) { mutableFloatStateOf(1f) }
    var offsetX by remember(sourceUri) { mutableFloatStateOf(0f) }
    var offsetY by remember(sourceUri) { mutableFloatStateOf(0f) }
    var boxSize by remember(sourceUri) { mutableStateOf(IntSize.Zero) }
    var pendingUploadBytes by remember(sourceUri) { mutableStateOf<ByteArray?>(null) }

    LaunchedEffect(sourceUri) {
        show = true
        decodeError = null
        val loaded = withContext(ioDispatcher) {
            val bytes = PickedMediaRegistry.readBytes(sourceUri) ?: return@withContext null
            val bmp = decodeImageBitmap(bytes) ?: return@withContext null
            bytes to bmp
        }
        if (loaded != null) {
            rawBytes = loaded.first
            bitmap = loaded.second
        } else {
            decodeError = "图片加载失败"
        }
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
                    .aspectRatio(aspectRatio)
                    .clip(RoundedCornerShape(14.dp))
                    .clipToBounds()
                    .background(Color.Black)
                    .onSizeChanged { boxSize = it }
                    .pointerInput(bitmap, boxSize) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val bmp = bitmap ?: return@detectTransformGestures
                            val nextScale = (scale * zoom).coerceIn(1f, 5f)
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
                    decodeError != null -> Text(
                        text = decodeError!!,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                    )
                    bmp == null -> Text(
                        text = "正在加载图片…",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                    )
                    else -> {
                        Image(
                            bitmap = bmp,
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
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
                        ) {
                            if (circleMask) {
                                drawRect(Color.Black.copy(alpha = 0.45f))
                                val radius = min(size.width, size.height) / 2f
                                drawCircle(
                                    color = Color.Transparent,
                                    radius = radius,
                                    center = Offset(size.width / 2f, size.height / 2f),
                                    blendMode = BlendMode.Clear,
                                )
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.85f),
                                    radius = radius,
                                    center = Offset(size.width / 2f, size.height / 2f),
                                    style = Stroke(width = 1.5.dp.toPx()),
                                )
                            } else {
                                drawRoundRect(
                                    color = Color.White.copy(alpha = 0.75f),
                                    cornerRadius = CornerRadius(14.dp.toPx()),
                                    style = Stroke(width = 1.5.dp.toPx()),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            DialogButtonBar(
                negative = DialogButtonBarAction(
                    text = "取消",
                    onClick = { if (!uploading) show = false },
                ),
                positive = DialogButtonBarAction(
                    text = if (uploading) "上传中…" else confirmText,
                    onClick = {
                        if (uploading) return@DialogButtonBarAction
                        val srcBytes = rawBytes ?: return@DialogButtonBarAction
                        val bmp = bitmap ?: return@DialogButtonBarAction
                        val safeW = boxSize.width.toFloat().coerceAtLeast(1f)
                        val safeH = boxSize.height.toFloat().coerceAtLeast(1f)
                        val baseScale = max(safeW / bmp.width.toFloat(), safeH / bmp.height.toFloat())
                        val totalScale = baseScale * scale
                        val visibleW = safeW / totalScale
                        val visibleH = safeH / totalScale
                        val centerX = bmp.width / 2f - offsetX / totalScale
                        val centerY = bmp.height / 2f - offsetY / totalScale
                        val leftFrac = ((centerX - visibleW / 2f) / bmp.width.toFloat()).coerceIn(0f, 1f)
                        val topFrac = ((centerY - visibleH / 2f) / bmp.height.toFloat()).coerceIn(0f, 1f)
                        val sizeFrac = (min(visibleW, visibleH) / min(bmp.width, bmp.height).toFloat()).coerceIn(0.05f, 1f)
                        val jpeg = cropAndEncodeJpeg(srcBytes, leftFrac, topFrac, sizeFrac, outWidth)
                        if (jpeg != null) {
                            pendingUploadBytes = jpeg
                        }
                    },
                ),
            )
        }
    }

    val pending = pendingUploadBytes
    if (pending != null) {
        OverlayDialog(
            show = true,
            title = confirmDialogTitle,
            summary = confirmDialogMessage,
            onDismissRequest = { if (!uploading) pendingUploadBytes = null },
        ) {
            DialogButtonBar(
                negative = DialogButtonBarAction(
                    text = "取消",
                    onClick = { if (!uploading) pendingUploadBytes = null },
                ),
                positive = DialogButtonBarAction(
                    text = if (uploading) "上传中…" else "确认上传",
                    onClick = {
                        if (!uploading) {
                            val data = pendingUploadBytes ?: return@DialogButtonBarAction
                            pendingUploadBytes = null
                            onConfirm(data)
                        }
                    },
                ),
            )
        }
    }
}
