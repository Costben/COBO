package com.cobo.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap

/**
 * 供 iOS 双侧边缘返回手势与页内返回处理器协作的返回分发器。
 * 页内浮层（搜索面板、评论详情、弹窗、WebView 后退等）通过 [PlatformBackHandler] 注册；
 * 手势触发时按 LIFO 优先交给栈顶页面内已启用的处理器，未消费时再驱动路由栈跟手返回。
 */
class BackDispatcher {
    private class Entry(
        val isEnabled: () -> Boolean,
        val onBack: () -> Unit,
    )

    private val entries = mutableStateListOf<Entry>()

    fun register(isEnabled: () -> Boolean, onBack: () -> Unit): () -> Unit {
        val entry = Entry(isEnabled, onBack)
        entries += entry
        return { entries.remove(entry) }
    }

    fun hasEnabledHandler(): Boolean = entries.any { it.isEnabled() }

    fun dispatch(): Boolean {
        for (i in entries.lastIndex downTo 0) {
            val entry = entries[i]
            if (entry.isEnabled()) {
                entry.onBack()
                return true
            }
        }
        return false
    }
}

val LocalBackDispatcher = staticCompositionLocalOf<BackDispatcher?> { null }

/** 当前组合子树是否处于导航栈顶路由；非栈顶页注册的 [PlatformBackHandler] 不拦截栈顶手势。 */
val LocalBackRouteIsTop = compositionLocalOf { true }

enum class PlatformOrientation {
    Portrait,
    Landscape,
    LandscapeLeft,
    LandscapeRight,
    Unspecified,
}

data class PickedMedia(
    val id: String,
    val fileName: String,
    val mime: String,
    val bytes: ByteArray,
    val isVideo: Boolean = false,
    val liveVideoBytes: ByteArray? = null,
    val liveVideoMime: String = "video/quicktime",
    val liveVideoFileName: String? = null,
)

expect fun copyToClipboard(text: String, label: String = "")
expect fun openExternalUrl(url: String)
expect fun shareText(text: String, title: String = "")
expect fun shareImageBytes(bytes: ByteArray, fileName: String, mime: String = "image/jpeg")

expect fun generateQrCodeBitmap(content: String, sizePx: Int): ImageBitmap?
expect fun decodeImageBitmap(bytes: ByteArray): ImageBitmap?
expect fun encodeImageBitmapToPng(bitmap: ImageBitmap): ByteArray?
expect fun decodeImageDimensions(bytes: ByteArray): Pair<Int, Int>?
expect fun cropAndEncodeJpeg(
    bytes: ByteArray,
    leftFrac: Float,
    topFrac: Float,
    sizeFrac: Float,
    outputPx: Int,
    quality: Int = 92,
): ByteArray?

expect suspend fun saveImageBytesToGallery(bytes: ByteArray, fileName: String, mime: String = "image/jpeg"): Result<Unit>
expect suspend fun saveVideoBytesToGallery(bytes: ByteArray, fileName: String): Result<Unit>
expect suspend fun saveLivePhotoToGallery(imageBytes: ByteArray, videoBytes: ByteArray): Result<Unit>

@Composable
expect fun rememberPhotoPicker(
    maxSelection: Int = 1,
    onPicked: (List<PickedMedia>) -> Unit,
): () -> Unit

@Composable
expect fun rememberJsonExportLauncher(
    onResult: (Result<String>?) -> Unit,
): (suggestedFileName: String, content: String, summaryLabel: String) -> Unit

@Composable
expect fun rememberJsonImportLauncher(
    maxBytes: Long,
    onResult: (Result<String>?) -> Unit,
): () -> Unit

expect fun setScreenOrientation(orientation: PlatformOrientation)
expect fun setSystemBarsVisible(visible: Boolean)
expect fun setStatusBarDarkIcons(darkIcons: Boolean?)
expect fun keepScreenOn(enabled: Boolean)
expect fun readScreenBrightness(): Float
expect fun writeScreenBrightness(value: Float)
expect fun readSystemVolume(): Float
expect fun writeSystemVolume(value: Float)

@Composable
expect fun PlatformBackHandler(
    enabled: Boolean = true,
    onBack: () -> Unit,
)

expect suspend fun platformDeviceCoords(): Pair<Double, Double>?
@Composable
expect fun rememberLocationPermissionRequester(onResult: (Boolean) -> Unit): () -> Unit
expect fun hasLocationPermission(): Boolean

expect fun hsvToArgb(hue: Float, saturation: Float, value: Float): Int

@Composable
expect fun Modifier.platformCausticLight(): Modifier

expect fun defaultWebUserAgent(): String

expect object WebCookieBridge {
    fun inject(cookies: Map<String, String>)
    fun plant(cookies: Map<String, String>)
    fun readDomains(): Map<String, String>
    fun clearAll(onDone: (() -> Unit)? = null)
    fun updateCachedDomains(domains: Map<String, String>)
}

expect class PlatformWebViewHandle {
    val canGoBack: Boolean
    val currentUrl: String
    fun goBack()
    fun reload()
    fun loadUrl(url: String)
    fun evaluateJavaScript(script: String)
}

@Composable
expect fun PlatformWeiboWebView(
    url: String,
    modifier: Modifier = Modifier,
    useSessionCookie: Boolean = true,
    clearCookies: Boolean = false,
    userAgent: String,
    initialCookies: Map<String, String> = emptyMap(),
    onCreated: (PlatformWebViewHandle) -> Unit = {},
    onBackAvailable: (Boolean) -> Unit = {},
    onUrlLoading: (PlatformWebViewHandle, String) -> Boolean = { _, _ -> false },
    onCustomScheme: (String) -> Boolean = { false },
    onPageStarted: (PlatformWebViewHandle, String) -> Unit = { _, _ -> },
    onPageCommitVisible: (PlatformWebViewHandle, String) -> Unit = { _, _ -> },
    onProgressChanged: (PlatformWebViewHandle, Int) -> Unit = { _, _ -> },
    onPageFinished: (String) -> Unit = {},
    onCloseWindow: () -> Unit = {},
    onConsoleMessage: (String) -> Unit = {},
    onReceivedError: (String, Int, String) -> Unit = { _, _, _ -> },
    onJsBridgeAlt: ((String) -> Unit)? = null,
)
