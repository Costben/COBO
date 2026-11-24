package com.cobo.app.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.cobo.app.App
import com.cobo.app.data.Api

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WeiboWebView(
    url: String,
    modifier: Modifier = Modifier,
    useSessionCookie: Boolean = true,
    clearCookies: Boolean = false,
    userAgent: String = Api.WEB_UA,
    onConfigure: (WebView) -> Unit = {},
    onCreated: (WebView) -> Unit = {},
    onBackAvailable: (Boolean) -> Unit = {},
    onUrlLoading: (WebView, String) -> Boolean = { _, _ -> false },
    onCustomScheme: (String) -> Boolean = { false },
    onPageStarted: (WebView, String) -> Unit = { _, _ -> },
    onPageCommitVisible: (WebView, String) -> Unit = { _, _ -> },
    onProgressChanged: (WebView, Int) -> Unit = { _, _ -> },
    onPageFinished: (String) -> Unit = {},
    onCloseWindow: () -> Unit = {},
    onConsoleMessage: (String) -> Unit = {},
    onReceivedError: (String, Int, String) -> Unit = { _, _, _ -> },
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val currentOnPageFinished by rememberUpdatedState(onPageFinished)
    val currentOnBackAvailable by rememberUpdatedState(onBackAvailable)
    val currentOnUrlLoading by rememberUpdatedState(onUrlLoading)
    val currentOnCustomScheme by rememberUpdatedState(onCustomScheme)
    val currentOnPageStarted by rememberUpdatedState(onPageStarted)
    val currentOnPageCommitVisible by rememberUpdatedState(onPageCommitVisible)
    val currentOnProgressChanged by rememberUpdatedState(onProgressChanged)
    val currentOnCloseWindow by rememberUpdatedState(onCloseWindow)
    val currentOnConsoleMessage by rememberUpdatedState(onConsoleMessage)
    val currentOnReceivedError by rememberUpdatedState(onReceivedError)
    var pendingFileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val pendingPopups = remember { mutableListOf<WebView>() }
    val singleFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        val callback = pendingFileCallback
        pendingFileCallback = null
        callback?.onReceiveValue(if (uri != null) arrayOf(uri) else null)
    }
    val multiFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents(),
    ) { uris ->
        val callback = pendingFileCallback
        pendingFileCallback = null
        callback?.onReceiveValue(if (uris.isNotEmpty()) uris.toTypedArray() else null)
    }
    var loadedUrl by remember { mutableStateOf(url) }

    fun routePopupTarget(parent: WebView, target: String) {
        if (target.isBlank()) return
        if (currentOnUrlLoading(parent, target)) return
        if (isStandardWebScheme(target)) {
            if (!target.equals("about:blank", ignoreCase = true)) {
                parent.loadUrl(target)
            }
            return
        }
        if (currentOnCustomScheme(target)) return
        extractWebViewFallbackUrl(target)?.let { parent.loadUrl(it) }
    }

    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.allowContentAccess = true
            settings.loadsImagesAutomatically = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.textZoom = 100
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.setSupportMultipleWindows(true)
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            settings.mediaPlaybackRequiresUserGesture = false
            settings.userAgentString = userAgent
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    currentOnProgressChanged(view, newProgress)
                }

                override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                    currentOnConsoleMessage("${consoleMessage.messageLevel()}: ${consoleMessage.message()}")
                    return false
                }

                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message?,
                ): Boolean {
                    val parent = view ?: return false
                    val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                    val popupWebView = WebView(parent.context)
                    pendingPopups.add(popupWebView)
                    var routed = false
                    fun routeOnce(popup: WebView, target: String) {
                        if (routed || target.isBlank() || target.equals("about:blank", ignoreCase = true)) return
                        routed = true
                        popup.stopLoading()
                        routePopupTarget(parent, target)
                        mainHandler.post {
                            pendingPopups.remove(popup)
                            popup.destroy()
                        }
                    }
                    popupWebView.settings.javaScriptEnabled = true
                    popupWebView.settings.userAgentString = parent.settings.userAgentString
                    popupWebView.webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            popup: WebView,
                            request: WebResourceRequest,
                        ): Boolean {
                            val target = request.url?.toString().orEmpty()
                            if (target.isBlank() || target.equals("about:blank", ignoreCase = true)) return false
                            routeOnce(popup, target)
                            return true
                        }

                        override fun onPageStarted(popup: WebView, url: String?, favicon: Bitmap?) {
                            routeOnce(popup, url.orEmpty())
                        }
                    }
                    transport.webView = popupWebView
                    resultMsg.sendToTarget()
                    return true
                }

                override fun onCloseWindow(window: WebView?) {
                    currentOnCloseWindow()
                }

                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?,
                ): Boolean {
                    pendingFileCallback?.onReceiveValue(null)
                    pendingFileCallback = filePathCallback
                    val mime = fileChooserParams?.acceptTypes
                        ?.firstOrNull { it.isNotBlank() && it.contains('/') }
                        ?: "*/*"
                    val allowMultiple = fileChooserParams?.mode == FileChooserParams.MODE_OPEN_MULTIPLE
                    runCatching {
                        if (allowMultiple) multiFileLauncher.launch(mime)
                        else singleFileLauncher.launch(mime)
                    }.onFailure {
                        pendingFileCallback = null
                        filePathCallback?.onReceiveValue(null)
                    }
                    return true
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean {
                    val target = request.url?.toString().orEmpty()
                    if (currentOnUrlLoading(view, target)) return true
                    if (isStandardWebScheme(target)) return false
                    if (currentOnCustomScheme(target)) return true
                    // 拦截跳端 scheme（intent://、sinaweibo://browser?url=... 等），拆出内层网页继续在当前 WebView 加载。
                    extractWebViewFallbackUrl(target)?.let { view.loadUrl(it) }
                    return true
                }

                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    currentOnPageStarted(view, url.orEmpty())
                }

                override fun onPageCommitVisible(view: WebView, url: String?) {
                    currentOnPageCommitVisible(view, url.orEmpty())
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    CookieManager.getInstance().flush()
                    currentOnBackAvailable(view.canGoBack())
                    currentOnPageFinished(url.orEmpty())
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError,
                ) {
                    if (request.isForMainFrame) {
                        currentOnReceivedError(
                            request.url?.toString().orEmpty(),
                            error.errorCode,
                            error.description?.toString().orEmpty(),
                        )
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView,
                    request: WebResourceRequest,
                    errorResponse: WebResourceResponse,
                ) {
                    if (request.isForMainFrame) {
                        currentOnReceivedError(
                            request.url?.toString().orEmpty(),
                            errorResponse.statusCode,
                            errorResponse.reasonPhrase.orEmpty(),
                        )
                    }
                }

                override fun onRenderProcessGone(
                    view: WebView,
                    detail: RenderProcessGoneDetail,
                ): Boolean {
                    currentOnReceivedError(
                        view.url.orEmpty(),
                        -1,
                        if (detail.didCrash()) "render_process_crashed" else "render_process_killed",
                    )
                    return true
                }
            }
            onConfigure(this)

            fun plantCookiesAndLoad() {
                if (useSessionCookie) {
                    val cookies = App.repo.session?.cookies
                    if (!cookies.isNullOrEmpty()) {
                        com.cobo.app.data.CookieVault.plant(cookies)
                    } else {
                        App.repo.cookie()?.split(";")?.forEach { pair ->
                            val trimmed = pair.trim()
                            if (trimmed.contains("=")) {
                                CookieManager.getInstance().setCookie("https://m.weibo.cn", trimmed)
                            }
                        }
                    }
                }
                loadUrl(url)
            }

            if (clearCookies) {
                CookieManager.getInstance().removeAllCookies {
                    CookieManager.getInstance().flush()
                    mainHandler.post { plantCookiesAndLoad() }
                }
            } else {
                plantCookiesAndLoad()
            }
        }
    }

    DisposableEffect(webView) {
        webView.onResume()
        onCreated(webView)
        onDispose {
            pendingPopups.forEach { popup ->
                runCatching {
                    popup.stopLoading()
                    popup.destroy()
                }
            }
            pendingPopups.clear()
            webView.onPause()
            webView.stopLoading()
            webView.destroy()
        }
    }

    AndroidView(
        factory = { webView },
        modifier = modifier,
        update = { view ->
            if (loadedUrl != url) {
                loadedUrl = url
                view.loadUrl(url)
            }
        },
    )
}

internal fun isStandardWebScheme(raw: String): Boolean {
    val lower = raw.trim().lowercase()
    return lower.startsWith("http://") ||
        lower.startsWith("https://") ||
        lower.startsWith("about:") ||
        lower.startsWith("javascript:") ||
        lower.startsWith("data:") ||
        lower.startsWith("blob:")
}

internal fun cleanMobileChromeUserAgent(defaultUa: String): String {
    val trimmed = defaultUa.trim()
    if (trimmed.isBlank()) return Api.WEB_UA
    return trimmed
        .replace(Regex(";\\s*wv(?=\\))", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\bVersion/\\S+\\s+", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s{2,}"), " ")
        .trim()
}

internal fun extractWebViewFallbackUrl(raw: String): String? {
    extractIntentFallbackUrl(raw)?.let { return it }
    val trimmed = raw.trim()
    val lower = trimmed.lowercase()
    val isWeiboBrowserScheme = lower.startsWith("sinaweibo://browser") ||
        lower.startsWith("sinaweibo://slidebrowser") ||
        lower.startsWith("weibo://browser")
    if (!isWeiboBrowserScheme) return null
    val query = trimmed.substringAfter('?', "").substringBefore('#')
    if (query.isBlank()) return null
    val encoded = query.split('&')
        .firstOrNull { it.substringBefore('=').equals("url", ignoreCase = true) }
        ?.substringAfter('=', "")
        .orEmpty()
    if (encoded.isBlank()) return null
    val decoded = runCatching { java.net.URLDecoder.decode(encoded, "UTF-8") }.getOrDefault(encoded).trim()
    return decoded.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
}

internal fun extractIntentFallbackUrl(raw: String): String? {
    if (!raw.startsWith("intent://", ignoreCase = true)) return null
    val marker = "S.browser_fallback_url="
    val at = raw.indexOf(marker)
    if (at < 0) return null
    val encoded = raw.substring(at + marker.length).substringBefore(';')
    val decoded = runCatching { java.net.URLDecoder.decode(encoded, "UTF-8") }.getOrDefault(encoded)
    return decoded.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
}
