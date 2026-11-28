package com.cobo.app.screen

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect as AndroidRect
import android.os.Build
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.BitmapImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.cobo.app.data.Api
import com.cobo.app.platform.AndroidPlatformHolder
import com.cobo.app.platform.PlatformFile
import com.cobo.app.platform.keepScreenOn
import com.cobo.app.platform.openExternalUrl
import com.cobo.app.platform.sha256Hex
import com.cobo.app.ui.CoboIcons
import com.cobo.app.ui.NavState
import com.cobo.app.ui.components.downloadMediaToFile
import com.cobo.app.ui.resolveWeiboLink
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Close
import io.github.suqi8.coui.kmp.icon.extended.Download
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

private const val RegionTilePx = 2048
private const val MaxCachedRegionTiles = 16
private const val AndroidLongImageAspect = 0.45f

private data class TileKey(val col: Int, val row: Int, val sampleSize: Int)

internal actual suspend fun platformTopBandIsLight(
    url: String,
    screenWidthDp: Int,
    screenHeightDp: Int,
): Boolean = runCatching {
    val context = AndroidPlatformHolder.appContext ?: return false
    val request = ImageRequest.Builder(context)
        .data(url)
        .size(72)
        .build()
    val sampled = ((context.imageLoader.execute(request) as? SuccessResult)?.image as? BitmapImage)
        ?.bitmap ?: return false
    val bitmap = if (sampled.config == Bitmap.Config.HARDWARE) {
        sampled.copy(Bitmap.Config.ARGB_8888, false) ?: return false
    } else {
        sampled
    }
    val width = bitmap.width
    val height = bitmap.height
    if (width <= 0 || height <= 0) return false
    val fit = minOf(screenWidthDp.toFloat() / width, screenHeightDp.toFloat() / height)
    val top = (screenHeightDp - height * fit) / 2f
    val band = screenHeightDp * 0.11f
    if (top >= band) return false
    val y0 = ((0f - top) / fit).toInt().coerceIn(0, height - 1)
    val y1 = ((band - top) / fit).toInt().coerceIn(0, height - 1)
    var sum = 0.0
    var count = 0
    for (y in y0..y1) {
        for (x in 0 until width) {
            val color = bitmap.getPixel(x, y)
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            sum += (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
            count++
        }
    }
    count > 0 && sum / count > 0.62
}.getOrDefault(false)

/**
 * 超过 GPU 单张纹理上限（4096px）的超长图 / 超大图分块渲染层：
 * 用 [BitmapRegionDecoder] 仅按可视区解码 2048px 切片，避免 Coil 压缩变糊或爆纹理上限。
 */
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
) {
    val context = LocalContext.current
    var cachedFile by remember(url) { mutableStateOf<File?>(null) }

    LaunchedEffect(url, original) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            val local = File(url.removePrefix("file://"))
            if (local.isFile && local.length() > 0L) cachedFile = local
            return@LaunchedEffect
        }
        val file = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "long_img").apply { mkdirs() }
            val dest = File(dir, "${sha256Hex(url)}.img")
            if (dest.isFile && dest.length() > 0L) return@withContext dest
            val fromDisk = runCatching {
                context.imageLoader.diskCache?.openSnapshot(url)?.use { snap ->
                    val src = snap.data.toFile()
                    if (src.isFile && src.length() > 0L) {
                        src.copyTo(dest, overwrite = true)
                        dest
                    } else null
                }
            }.getOrNull()
            if (fromDisk != null) return@withContext fromDisk
            downloadMediaToFile(url, PlatformFile(dest.path))?.let { File(it.path) }
        }
        cachedFile = file
    }

    val file = cachedFile ?: return
    var bounds by remember(file) { mutableStateOf<Pair<Int, Int>?>(null) }
    LaunchedEffect(file) {
        val measured = withContext(Dispatchers.IO) {
            runCatching {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, opts)
                if (opts.outWidth > 0 && opts.outHeight > 0) opts.outWidth to opts.outHeight else null
            }.getOrNull()
        }
        bounds = measured
        if (measured != null) {
            onAspect(measured.first.toFloat() / measured.second.toFloat())
        }
    }

    val (imgW, imgH) = bounds ?: return
    val isLong = imgW.toFloat() / imgH < AndroidLongImageAspect
    val needsTiling = (imgH > 4096 || imgW > 4096) && (original || isLong)
    if (!needsTiling) return

    val sampleSize = remember(imgW, original, screenWidthPx) {
        if (original) {
            1
        } else {
            var s = 1
            val targetW = screenWidthPx.coerceAtLeast(1)
            while (imgW / (s * 2) >= targetW) s *= 2
            s
        }
    }

    val decoderHolder = remember(file) { arrayOfNulls<BitmapRegionDecoder>(1) }
    var decoderReady by remember(file) { mutableStateOf(false) }
    val decoderMutex = remember(file) { Mutex() }
    val tiles = remember(file, sampleSize) { mutableStateMapOf<TileKey, ImageBitmap>() }
    val lruOrder = remember(file, sampleSize) { ArrayList<TileKey>() }

    DisposableEffect(file) {
        onDispose {
            decoderHolder[0]?.recycle()
            decoderHolder[0] = null
        }
    }

    LaunchedEffect(file) {
        val dec = withContext(Dispatchers.IO) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    file.inputStream().use { BitmapRegionDecoder.newInstance(it) }
                } else {
                    @Suppress("DEPRECATION")
                    file.inputStream().use { BitmapRegionDecoder.newInstance(it, false) }
                }
            }.getOrNull()
        }
        decoderHolder[0] = dec
        decoderReady = dec != null
    }

    if (!decoderReady || rest.width <= 0f || rest.height <= 0f) return

    val cols = (imgW + RegionTilePx - 1) / RegionTilePx
    val rows = (imgH + RegionTilePx - 1) / RegionTilePx
    val visLeft = (0f - box.width / 2f - offset.x) / scale + rest.width / 2f
    val visRight = (box.width - box.width / 2f - offset.x) / scale + rest.width / 2f
    val visTop = (0f - box.height / 2f - offset.y) / scale + rest.height / 2f
    val visBottom = (box.height - box.height / 2f - offset.y) / scale + rest.height / 2f

    val srcLeft = (visLeft / rest.width * imgW).toInt().coerceIn(0, imgW)
    val srcRight = (visRight / rest.width * imgW).toInt().coerceIn(0, imgW)
    val srcTop = (visTop / rest.height * imgH).toInt().coerceIn(0, imgH)
    val srcBottom = (visBottom / rest.height * imgH).toInt().coerceIn(0, imgH)

    val minCol = (srcLeft / RegionTilePx - 1).coerceIn(0, cols - 1)
    val maxCol = (srcRight / RegionTilePx + 1).coerceIn(0, cols - 1)
    val minRow = (srcTop / RegionTilePx - 1).coerceIn(0, rows - 1)
    val maxRow = (srcBottom / RegionTilePx + 1).coerceIn(0, rows - 1)

    val needed = remember(minCol, maxCol, minRow, maxRow, sampleSize) {
        buildList {
            for (r in minRow..maxRow) {
                for (c in minCol..maxCol) {
                    add(TileKey(c, r, sampleSize))
                }
            }
        }
    }

    LaunchedEffect(decoderReady, needed, sampleSize) {
        val decoder = decoderHolder[0] ?: return@LaunchedEffect
        for (key in needed) {
            if (key in tiles) {
                lruOrder.remove(key)
                lruOrder.add(key)
                continue
            }
            val decoded = withContext(Dispatchers.IO) {
                decoderMutex.withLock {
                    if (decoder.isRecycled) return@withLock null
                    runCatching {
                        val sx0 = key.col * RegionTilePx
                        val sy0 = key.row * RegionTilePx
                        val sx1 = minOf(imgW, sx0 + RegionTilePx)
                        val sy1 = minOf(imgH, sy0 + RegionTilePx)
                        val opts = BitmapFactory.Options().apply {
                            inSampleSize = key.sampleSize
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                        }
                        decoder.decodeRegion(AndroidRect(sx0, sy0, sx1, sy1), opts)?.asImageBitmap()
                    }.getOrNull()
                }
            }
            if (decoded != null) {
                tiles[key] = decoded
                lruOrder.remove(key)
                lruOrder.add(key)
                while (lruOrder.size > MaxCachedRegionTiles) {
                    val oldest = lruOrder.removeAt(0)
                    if (oldest !in needed) tiles.remove(oldest)
                }
            }
        }
    }

    Canvas(Modifier.fillMaxSize()) {
        val aspect = imgW.toFloat() / imgH.toFloat()
        val fitScale = minOf(size.width / aspect, size.height)
        val fittedW = aspect * fitScale
        val fittedH = fitScale
        val left0 = (size.width - fittedW) / 2f
        val top0 = (size.height - fittedH) / 2f
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val bmp = tiles[TileKey(c, r, sampleSize)] ?: continue
                val sx0 = c * RegionTilePx
                val sy0 = r * RegionTilePx
                val sx1 = minOf(imgW, sx0 + RegionTilePx)
                val sy1 = minOf(imgH, sy0 + RegionTilePx)
                val dx0 = (left0 + sx0.toFloat() / imgW * fittedW).roundToInt()
                val dy0 = (top0 + sy0.toFloat() / imgH * fittedH).roundToInt()
                val dx1 = (left0 + sx1.toFloat() / imgW * fittedW).roundToInt()
                val dy1 = (top0 + sy1.toFloat() / imgH * fittedH).roundToInt()
                if (dx1 > dx0 && dy1 > dy0) {
                    drawImage(
                        image = bmp,
                        dstOffset = IntOffset(dx0, dy0),
                        dstSize = IntSize(dx1 - dx0, dy1 - dy0),
                    )
                }
            }
        }
    }
}

private const val VideoSnifferJs = """
(function() {
  if (window.__coboSnifferInstalled) return;
  window.__coboSnifferInstalled = true;
  function report(u) {
    if (u && typeof u === 'string' && window.CoboVideoSniffer) {
      window.CoboVideoSniffer.onMediaUrl(u);
    }
  }
  function bindVideo(v) {
    if (!v || v.__coboBound) return;
    v.__coboBound = true;
    if (v.currentSrc) report(v.currentSrc);
    if (v.src) report(v.src);
    v.addEventListener('play', function() {
      if (v.currentSrc) report(v.currentSrc);
      if (window.CoboVideoSniffer) window.CoboVideoSniffer.onPlaybackState(true);
    });
    v.addEventListener('pause', function() {
      if (window.CoboVideoSniffer) window.CoboVideoSniffer.onPlaybackState(false);
    });
    v.addEventListener('ended', function() {
      if (window.CoboVideoSniffer) window.CoboVideoSniffer.onPlaybackState(false);
    });
  }
  document.querySelectorAll('video').forEach(bindVideo);
  new MutationObserver(function() {
    document.querySelectorAll('video').forEach(bindVideo);
  }).observe(document.documentElement, { childList: true, subtree: true });
})();
"""

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
internal actual fun PlatformWebVideoPlayer(
    nav: NavState,
    pageUrl: String,
    title: String,
    onDownload: (String) -> Unit,
    onFlash: (String) -> Unit,
) {
    var sniffedMp4 by remember(pageUrl) { mutableStateOf<String?>(null) }
    var sniffedM3u8 by remember(pageUrl) { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var pageTitle by remember(pageUrl) { mutableStateOf(title.ifBlank { "视频" }) }
    var customView by remember { mutableStateOf<View?>(null) }
    var customCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var webVideoPlaying by remember(pageUrl) { mutableStateOf(false) }

    LaunchedEffect(webVideoPlaying, customView != null) {
        keepScreenOn(webVideoPlaying || customView != null)
    }
    DisposableEffect(Unit) {
        onDispose { keepScreenOn(false) }
    }

    fun recordMedia(candidate: String) {
        if (!isSniffableMediaUrl(candidate)) return
        if (isM3u8Stream(candidate)) {
            if (sniffedM3u8 == null) sniffedM3u8 = candidate
        } else {
            sniffedMp4 = candidate
        }
    }

    BackHandler {
        when {
            customView != null -> customCallback?.onCustomViewHidden()
            canGoBack -> webView?.goBack()
            else -> nav.pop()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF141414))
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HighlightedIconButton(onClick = { nav.pop() }, size = 38.dp) {
                    Icon(COUIIcons.Close, "关闭", Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    pageTitle,
                    color = Color.White,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 10.dp),
                )
                HighlightedIconButton(
                    size = 38.dp,
                    onClick = {
                        val direct = sniffedMp4 ?: sniffedM3u8
                        if (direct != null) {
                            onDownload(direct)
                        } else {
                            val view = webView
                            if (view == null) {
                                onFlash("未检测到可下载的视频流")
                            } else {
                                view.evaluateJavascript(
                                    "(() => { const v = document.querySelector('video'); return v ? (v.currentSrc || v.src || '') : ''; })()",
                                ) { raw ->
                                    val cleaned = raw.trim('"').replace("\\/", "/")
                                    if (cleaned.startsWith("http://", true) || cleaned.startsWith("https://", true)) {
                                        onDownload(cleaned)
                                    } else {
                                        onFlash("未检测到可下载的视频流，请先点击播放后再试")
                                    }
                                }
                            }
                        }
                    },
                ) {
                    Icon(COUIIcons.Download, "下载视频", Modifier.size(18.dp))
                }
                Spacer(Modifier.width(6.dp))
                HighlightedIconButton(
                    size = 38.dp,
                    onClick = {
                        val liveUrl = webView?.url?.takeIf {
                            it.startsWith("http://", true) || it.startsWith("https://", true)
                        } ?: pageUrl
                        openExternalUrl(liveUrl)
                    },
                ) {
                    Icon(CoboIcons.Globe, "用浏览器打开", Modifier.size(18.dp))
                }
            }

            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        val wv = this
                        CookieManager.getInstance().apply {
                            setAcceptCookie(true)
                            setAcceptThirdPartyCookies(wv, true)
                        }
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        settings.userAgentString = Api.WEB_UA
                        addJavascriptInterface(
                            object {
                                @JavascriptInterface
                                fun onMediaUrl(url: String) {
                                    post { recordMedia(url) }
                                }

                                @JavascriptInterface
                                fun onPlaybackState(playing: Boolean) {
                                    post { webVideoPlaying = playing }
                                }
                            },
                            "CoboVideoSniffer",
                        )
                        webChromeClient = object : WebChromeClient() {
                            override fun onReceivedTitle(view: WebView?, t: String?) {
                                if (!t.isNullOrBlank()) pageTitle = t
                            }

                            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                                if (customView != null) {
                                    callback?.onCustomViewHidden()
                                    return
                                }
                                customView = view
                                customCallback = callback
                            }

                            override fun onHideCustomView() {
                                customView = null
                                customCallback = null
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest,
                            ): Boolean {
                                val target = request.url?.toString().orEmpty()
                                val host = request.url?.host?.lowercase().orEmpty()
                                if (host == "app.iqiyi.com" || host == "d.iqiyi.com" || host == "mbdapp.iqiyi.com") {
                                    return true
                                }
                                if (target.startsWith("http://", true) || target.startsWith("https://", true)) {
                                    return false
                                }
                                resolveWeiboLink(target)?.let { view.loadUrl(it) }
                                return true
                            }

                            override fun onLoadResource(view: WebView?, url: String?) {
                                if (!url.isNullOrBlank()) recordMedia(url)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                canGoBack = view?.canGoBack() == true
                                view?.evaluateJavascript(VideoSnifferJs, null)
                            }
                        }
                        webView = this
                        loadUrl(pageUrl)
                    }
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                onRelease = { view ->
                    view.stopLoading()
                    view.destroy()
                    if (webView === view) webView = null
                },
            )
        }

        customView?.let { fullView ->
            AndroidView(
                factory = { fullView },
                modifier = Modifier.fillMaxSize().background(Color.Black),
            )
        }
    }
}
