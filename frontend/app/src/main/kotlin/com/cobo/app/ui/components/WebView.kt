package com.cobo.app.ui.components

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
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
    onCustomScheme: (String) -> Boolean = { false },
    onProgressChanged: (WebView, Int) -> Unit = { _, _ -> },
    onPageFinished: (String) -> Unit = {},
    onConsoleMessage: (String) -> Unit = {},
    onReceivedError: (String, Int, String) -> Unit = { _, _, _ -> },
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentOnPageFinished by rememberUpdatedState(onPageFinished)
    val currentOnBackAvailable by rememberUpdatedState(onBackAvailable)
    val currentOnProgressChanged by rememberUpdatedState(onProgressChanged)
    val currentOnConsoleMessage by rememberUpdatedState(onConsoleMessage)
    val currentOnReceivedError by rememberUpdatedState(onReceivedError)
    var pendingFileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
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
    val webView = remember {
        if (clearCookies) {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.textZoom = 100
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
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
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean {
                    val target = request.url?.toString().orEmpty()
                    if (target.startsWith("http://", true) || target.startsWith("https://", true)) {
                        return false
                    }
                    if (onCustomScheme(target)) return true
                    // 拦截跳端 scheme（intent://、iqiyi://、sinaweibo:// 等），防止 WebView 报 ERR_UNKNOWN_URL_SCHEME。
                    extractIntentFallbackUrl(target)?.let { view.loadUrl(it) }
                    return true
                }

                override fun onPageFinished(view: WebView, url: String?) {
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
            }
            onConfigure(this)
            loadUrl(url)
        }
    }

    DisposableEffect(webView) {
        onCreated(webView)
        onDispose {
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

internal fun extractIntentFallbackUrl(raw: String): String? {
    if (!raw.startsWith("intent://", ignoreCase = true)) return null
    val marker = "S.browser_fallback_url="
    val at = raw.indexOf(marker)
    if (at < 0) return null
    val encoded = raw.substring(at + marker.length).substringBefore(';')
    val decoded = runCatching { java.net.URLDecoder.decode(encoded, "UTF-8") }.getOrDefault(encoded)
    return decoded.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
}
