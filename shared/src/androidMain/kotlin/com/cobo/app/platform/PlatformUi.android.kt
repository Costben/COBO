package com.cobo.app.platform

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.location.LocationManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.view.WindowManager
import android.webkit.WebSettings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.cobo.app.data.Api
import com.cobo.app.data.MotionPhotoParser
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

actual fun copyToClipboard(text: String, label: String) {
    val ctx = AndroidPlatformHolder.appContext ?: return
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText(label.ifBlank { "Cobo" }, text))
}

actual fun openExternalUrl(url: String) {
    if (url.isBlank()) return
    val ctx = AndroidPlatformHolder.currentActivity() ?: AndroidPlatformHolder.appContext ?: return
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            if (ctx !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
    }
}

actual fun shareText(text: String, title: String) {
    val ctx = AndroidPlatformHolder.currentActivity() ?: AndroidPlatformHolder.appContext ?: return
    runCatching {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(send, title.ifBlank { "分享" }).apply {
            if (ctx !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(chooser)
    }
}

actual fun shareImageBytes(bytes: ByteArray, fileName: String, mime: String) {
    val ctx = AndroidPlatformHolder.currentActivity() ?: AndroidPlatformHolder.appContext ?: return
    runCatching {
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        val safeName = fileName.ifBlank { "cobo_share_${System.currentTimeMillis()}.jpg" }
        val file = File(dir, safeName)
        file.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime.ifBlank { "image/jpeg" }
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "分享图片").apply {
            if (ctx !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(chooser)
    }.onFailure { logWarn("CoboShare", "分享图片失败", it) }
}

actual fun generateQrCodeBitmap(content: String, sizePx: Int): ImageBitmap? = runCatching {
    val safeSize = sizePx.coerceAtLeast(64)
    val hints = mapOf(
        EncodeHintType.CHARACTER_SET to "UTF-8",
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to 1,
    )
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, safeSize, safeSize, hints)
    val pixels = IntArray(safeSize * safeSize)
    for (y in 0 until safeSize) {
        val row = y * safeSize
        for (x in 0 until safeSize) {
            pixels[row + x] = if (matrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE
        }
    }
    Bitmap.createBitmap(pixels, safeSize, safeSize, Bitmap.Config.ARGB_8888).asImageBitmap()
}.getOrNull()

actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? = runCatching {
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
}.getOrNull()

actual fun encodeImageBitmapToPng(bitmap: ImageBitmap): ByteArray? = runCatching {
    val out = ByteArrayOutputStream()
    bitmap.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, out)
    out.toByteArray()
}.getOrNull()

actual fun decodeImageDimensions(bytes: ByteArray): Pair<Int, Int>? = runCatching {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    if (opts.outWidth > 0 && opts.outHeight > 0) opts.outWidth to opts.outHeight else null
}.getOrNull()

actual fun cropAndEncodeJpeg(
    bytes: ByteArray,
    leftFrac: Float,
    topFrac: Float,
    sizeFrac: Float,
    outputPx: Int,
    quality: Int,
): ByteArray? = runCatching {
    val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    try {
        val w = src.width
        val h = src.height
        val cropSize = (minOf(w, h) * sizeFrac.coerceIn(0.05f, 1f)).roundToInt().coerceAtLeast(1)
        val left = (w * leftFrac).roundToInt().coerceIn(0, (w - cropSize).coerceAtLeast(0))
        val top = (h * topFrac).roundToInt().coerceIn(0, (h - cropSize).coerceAtLeast(0))
        val safeW = cropSize.coerceAtMost(w - left).coerceAtLeast(1)
        val safeH = cropSize.coerceAtMost(h - top).coerceAtLeast(1)
        val outSize = outputPx.coerceAtLeast(64)
        val out = Bitmap.createBitmap(outSize, outSize, Bitmap.Config.ARGB_8888)
        try {
            val canvas = AndroidCanvas(out)
            canvas.drawColor(AndroidColor.BLACK)
            val matrix = Matrix().apply {
                postTranslate(-left.toFloat(), -top.toFloat())
                val scale = max(outSize.toFloat() / safeW, outSize.toFloat() / safeH)
                postScale(scale, scale)
            }
            canvas.drawBitmap(src, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            val bos = ByteArrayOutputStream()
            out.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(50, 100), bos)
            bos.toByteArray()
        } finally {
            out.recycle()
        }
    } finally {
        src.recycle()
    }
}.getOrNull()

actual suspend fun saveImageBytesToGallery(bytes: ByteArray, fileName: String, mime: String): Result<Unit> =
    withContext(ioDispatcher) {
        runCatching {
            val ctx = AndroidPlatformHolder.requireContext()
            val resolver = ctx.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, mime.ifBlank { "image/jpeg" })
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Cobo")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("无法创建相册条目")
            try {
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: throw IllegalStateException("无法打开相册输出流")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
            } catch (t: Throwable) {
                runCatching { resolver.delete(uri, null, null) }
                throw t
            }
        }
    }

actual suspend fun saveVideoBytesToGallery(bytes: ByteArray, fileName: String): Result<Unit> =
    withContext(ioDispatcher) {
        runCatching {
            val ctx = AndroidPlatformHolder.requireContext()
            val resolver = ctx.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName.ifBlank { "cobo_${currentTimeMillis()}.mp4" })
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Cobo")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("无法创建视频相册条目")
            try {
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: throw IllegalStateException("无法打开视频输出流")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
            } catch (t: Throwable) {
                runCatching { resolver.delete(uri, null, null) }
                throw t
            }
        }
    }

actual suspend fun saveLivePhotoToGallery(imageBytes: ByteArray, videoBytes: ByteArray): Result<Unit> =
    withContext(ioDispatcher) {
        val combined = com.cobo.app.data.exportAndroidMotionPhotoBytes(imageBytes, videoBytes)
        if (combined != null) {
            saveImageBytesToGallery(combined, "cobo_live_${currentTimeMillis()}.jpg", "image/jpeg")
        } else {
            saveImageBytesToGallery(imageBytes, "cobo_${currentTimeMillis()}.jpg", "image/jpeg")
        }
    }

@Composable
actual fun rememberPhotoPicker(
    maxSelection: Int,
    onPicked: (List<PickedMedia>) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnPicked by rememberUpdatedState(onPicked)

    fun readPickedList(uris: List<Uri>) {
        if (uris.isEmpty()) return
        scope.launch {
            val result = withContext(ioDispatcher) {
                uris.mapNotNull { uri -> readPickedMediaFromUri(context, uri) }
            }
            if (result.isNotEmpty()) currentOnPicked(result)
        }
    }

    val singleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) readPickedList(listOf(uri)) }

    val multiLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxSelection.coerceAtLeast(2)),
    ) { uris -> readPickedList(uris) }

    return remember(maxSelection, singleLauncher, multiLauncher) {
        {
            val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            if (maxSelection <= 1) {
                singleLauncher.launch(request)
            } else {
                multiLauncher.launch(request)
            }
        }
    }
}

private fun readPickedMediaFromUri(context: Context, uri: Uri): PickedMedia? = runCatching {
    val resolver = context.contentResolver
    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    if (bytes.isEmpty()) return null
    val mime = resolver.getType(uri).orEmpty().ifBlank { "image/jpeg" }
    val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }.orEmpty().ifBlank { "upload_${randomUuid().take(8)}.jpg" }
    val motion = MotionPhotoParser.extract(bytes)
    PickedMedia(
        id = randomUuid(),
        fileName = displayName,
        mime = mime,
        bytes = motion?.stillBytes ?: bytes,
        isVideo = mime.startsWith("video/"),
        liveVideoBytes = motion?.videoBytes,
        liveVideoMime = motion?.videoMime ?: "video/mp4",
        liveVideoFileName = if (motion != null) displayName.substringBeforeLast('.') + ".mp4" else null,
    )
}.getOrNull()

actual fun setScreenOrientation(orientation: PlatformOrientation) {
    val activity = AndroidPlatformHolder.currentActivity() ?: return
    val requested = when (orientation) {
        PlatformOrientation.Portrait -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        PlatformOrientation.Landscape -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        PlatformOrientation.LandscapeLeft -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        PlatformOrientation.LandscapeRight -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        PlatformOrientation.Unspecified -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    activity.runOnUiThread {
        activity.requestedOrientation = requested
    }
}

actual fun setSystemBarsVisible(visible: Boolean) {
    val activity = AndroidPlatformHolder.currentActivity() ?: return
    activity.runOnUiThread {
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (visible) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}

actual fun setStatusBarDarkIcons(darkIcons: Boolean?) {
    val activity = AndroidPlatformHolder.currentActivity() ?: return
    if (darkIcons == null) return
    activity.runOnUiThread {
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        controller.isAppearanceLightStatusBars = darkIcons
    }
}

actual fun keepScreenOn(enabled: Boolean) {
    val activity = AndroidPlatformHolder.currentActivity() ?: return
    activity.runOnUiThread {
        if (enabled) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

actual fun readScreenBrightness(): Float {
    val activity = AndroidPlatformHolder.currentActivity() ?: return 0.5f
    val winBrightness = activity.window.attributes.screenBrightness
    if (winBrightness >= 0f) return winBrightness.coerceIn(0f, 1f)
    return runCatching {
        android.provider.Settings.System.getInt(
            activity.contentResolver,
            android.provider.Settings.System.SCREEN_BRIGHTNESS,
        ) / 255f
    }.getOrDefault(0.5f).coerceIn(0f, 1f)
}

actual fun writeScreenBrightness(value: Float) {
    val activity = AndroidPlatformHolder.currentActivity() ?: return
    val clamped = value.coerceIn(0.01f, 1f)
    activity.runOnUiThread {
        val lp = activity.window.attributes
        lp.screenBrightness = clamped
        activity.window.attributes = lp
    }
}

actual fun readSystemVolume(): Float {
    val ctx = AndroidPlatformHolder.appContext ?: return 0.5f
    val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return 0.5f
    val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    val curVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
    return (curVol.toFloat() / maxVol.toFloat()).coerceIn(0f, 1f)
}

actual fun writeSystemVolume(value: Float) {
    val ctx = AndroidPlatformHolder.appContext ?: return
    val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
    val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    val target = (value.coerceIn(0f, 1f) * maxVol).roundToInt().coerceIn(0, maxVol)
    runCatching { am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0) }
}

@Composable
actual fun PlatformBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) {
    val routeIsTop = LocalBackRouteIsTop.current
    BackHandler(enabled = enabled && routeIsTop, onBack = onBack)
}

@SuppressLint("MissingPermission")
actual suspend fun platformDeviceCoords(): Pair<Double, Double>? = withContext(ioDispatcher) {
    val ctx = AndroidPlatformHolder.appContext ?: return@withContext null
    val manager = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        ?: return@withContext null
    listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
        LocationManager.PASSIVE_PROVIDER,
    ).mapNotNull { provider ->
        runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
    }.maxByOrNull { it.time }?.let { it.latitude to it.longitude }
}

@Composable
actual fun rememberLocationPermissionRequester(onResult: (Boolean) -> Unit): () -> Unit {
    val currentOnResult by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        currentOnResult(granted)
    }
    return remember(launcher) {
        {
            launcher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }
}

actual fun hasLocationPermission(): Boolean {
    val ctx = AndroidPlatformHolder.appContext ?: return false
    return ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
}

actual fun hsvToArgb(hue: Float, saturation: Float, value: Float): Int =
    AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value))

private val ChipTabCausticDark = Color(0x33FFFFFF)
private val ChipTabCausticBottomOffset = 4.299988.dp
private val ChipTabCausticSdfWidth = 9.22998.dp

private const val CHIP_TAB_CAUSTIC_SHADER = """
uniform float2 in_half;
uniform float in_corner;
uniform float in_bottom;
uniform float in_sdf;
uniform float4 in_color;
uniform float3 in_background;
float RBox(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0))) - r;
}
half4 main(float2 pos) {
    float2 pos_optics = pos - in_half - float2(0.0, in_bottom * 0.5);
    float2 halfSizeOptics = in_half + float2(0.0, in_bottom * 0.5);
    float sdf = min(in_sdf, min(in_half.x, in_half.y));
    if (in_color.a <= 0.001 || sdf <= 0.0 || any(greaterThan(abs(pos_optics), halfSizeOptics))) {
        return half4(0.0);
    }
    halfSizeOptics = max(halfSizeOptics - sdf, float2(0.0));
    float cornerOptics = min(in_corner, min(halfSizeOptics.x, halfSizeOptics.y));
    float opticsSdf = RBox(pos_optics, halfSizeOptics, cornerOptics);
    float value = smoothstep(sdf, 0.0, opticsSdf);
    value *= pow(smoothstep(halfSizeOptics.y + sdf, -halfSizeOptics.y - sdf, -pos_optics.y), 18.0);
    value = pow(value, 0.6) * 0.6 + 0.4 * value;
    float gray = clamp((dot(float3(0.299, 0.587, 0.114), in_background) - 0.45) * 0.6, 0.0, 1.0);
    float alpha = in_color.a * value * (1.0 - gray) * (1.0 - gray);
    return half4(in_color.rgb * alpha, alpha);
}
"""

@Composable
actual fun Modifier.platformCausticLight(): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return this
    val shaderState = remember {
        runCatching {
            val shader = RuntimeShader(CHIP_TAB_CAUSTIC_SHADER)
            shader to ShaderBrush(shader)
        }.getOrNull()
    } ?: return this
    val background = COUITheme.colorScheme.background
    return drawBehind {
        val (shader, brush) = shaderState
        val halfW = size.width / 2f
        val halfH = size.height / 2f
        if (halfW <= 0f || halfH <= 0f) return@drawBehind
        val bottom = ChipTabCausticBottomOffset.toPx().toInt().toFloat()
        val sdf = ChipTabCausticSdfWidth.toPx().toInt().toFloat()
        val cornerPx = (size.minDimension / 2f) * 1.41421356f
        shader.setFloatUniform("in_half", halfW, halfH)
        shader.setFloatUniform("in_corner", cornerPx)
        shader.setFloatUniform("in_bottom", bottom)
        shader.setFloatUniform("in_sdf", sdf)
        shader.setFloatUniform(
            "in_color",
            ChipTabCausticDark.red,
            ChipTabCausticDark.green,
            ChipTabCausticDark.blue,
            ChipTabCausticDark.alpha,
        )
        shader.setFloatUniform("in_background", background.red, background.green, background.blue)
        drawRect(
            brush = brush,
            size = Size(size.width, size.height + bottom),
            blendMode = BlendMode.Plus,
        )
    }
}

actual fun defaultWebUserAgent(): String {
    val ctx = AndroidPlatformHolder.appContext
    val raw = ctx?.let { runCatching { WebSettings.getDefaultUserAgent(it) }.getOrNull() }.orEmpty().trim()
    if (raw.isBlank()) return Api.WEB_UA
    return raw
        .replace(Regex(";\\s*wv(?=\\))", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\bVersion/\\S+\\s+", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s{2,}"), " ")
        .trim()
}

@Composable
actual fun rememberJsonExportLauncher(
    onResult: (Result<String>?) -> Unit,
): (suggestedFileName: String, content: String, summaryLabel: String) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnResult = rememberUpdatedState(onResult)
    val pending = remember {
        object {
            var content: String = ""
            var summary: String = ""
        }
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) {
            currentOnResult.value(null)
            return@rememberLauncherForActivityResult
        }
        val text = pending.content
        val summary = pending.summary
        scope.launch {
            val result = runCatching {
                withContext(ioDispatcher) {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(text.toByteArray(Charsets.UTF_8))
                        stream.flush()
                    } ?: error("无法写入目标文件")
                }
                summary
            }
            currentOnResult.value(result)
        }
    }
    return { suggestedFileName, content, summaryLabel ->
        pending.content = content
        pending.summary = summaryLabel
        launcher.launch(suggestedFileName)
    }
}

@Composable
actual fun rememberJsonImportLauncher(
    maxBytes: Long,
    onResult: (Result<String>?) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnResult = rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) {
            currentOnResult.value(null)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val result = runCatching {
                withContext(ioDispatcher) {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val buffer = ByteArray(8192)
                        val out = java.io.ByteArrayOutputStream()
                        var total = 0L
                        while (true) {
                            val read = stream.read(buffer)
                            if (read <= 0) break
                            total += read
                            if (total > maxBytes) {
                                throw IllegalArgumentException("备份文件过大，已取消读取")
                            }
                            out.write(buffer, 0, read)
                        }
                        out.toString(Charsets.UTF_8.name())
                    } ?: error("无法读取所选文件")
                }
            }
            currentOnResult.value(result)
        }
    }
    return {
        launcher.launch(arrayOf("application/json", "text/plain", "*/*"))
    }
}
