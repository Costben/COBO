package com.cobo.app.platform

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.cobo.app.data.CookieVault
import com.cobo.app.ui.components.extractWebViewFallbackUrl
import com.cobo.app.ui.components.isStandardWebScheme

actual object WebCookieBridge {
    private val cached = SynchronizedMap<String, String>()

    private val DOMAIN_URLS = listOf(
        CookieVault.WEIBO_CN to "https://m.weibo.cn",
        CookieVault.WEIBO_COM to "https://weibo.com",
        CookieVault.PASSPORT_CN to "https://passport.weibo.cn",
        CookieVault.PASSPORT_COM to "https://passport.weibo.com",
        CookieVault.SINA_COM_CN to "https://login.sina.com.cn",
    )

    actual fun updateCachedDomains(domains: Map<String, String>) {
        cached.clear()
        cached.putAll(domains)
    }

    actual fun inject(cookies: Map<String, String>) {
        updateCachedDomains(cookies)
        runCatching {
            val cm = CookieManager.getInstance()
            cm.setAcceptCookie(true)
            cm.removeAllCookies {
                writeCookies(cm, cookies)
                cm.flush()
            }
        }
    }

    actual fun plant(cookies: Map<String, String>) {
        if (cookies.isEmpty()) return
        cookies.forEach { (k, v) -> cached[k] = v }
        runCatching {
            val cm = CookieManager.getInstance()
            cm.setAcceptCookie(true)
            writeCookies(cm, cookies)
            cm.flush()
        }
    }

    actual fun readDomains(): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        runCatching {
            val cm = CookieManager.getInstance()
            for ((domain, url) in DOMAIN_URLS) {
                val raw = cm.getCookie(url)?.trim().orEmpty()
                if (raw.isNotBlank()) {
                    val lines = raw.split(';')
                        .map { it.trim() }
                        .filter { it.contains('=') }
                    if (lines.isNotEmpty()) {
                        result[domain] = lines.joinToString("\n")
                    }
                }
            }
        }
        return if (result.isNotEmpty()) result else cached.toMap()
    }

    actual fun clearAll(onDone: (() -> Unit)?) {
        cached.clear()
        val cm = runCatching { CookieManager.getInstance() }.getOrNull()
        if (cm == null) {
            onDone?.invoke()
            return
        }
        cm.removeAllCookies {
            cm.flush()
            onDone?.invoke()
        }
    }

    private fun writeCookies(cm: CookieManager, cookies: Map<String, String>) {
        cookies.forEach { (domain, rawValue) ->
            val cleanHost = domain.trim()
                .removePrefix("https://")
                .removePrefix("http://")
                .substringBefore('/')
                .trimStart('.')
            if (cleanHost.isBlank()) return@forEach
            val targetUrl = "https://$cleanHost"
            rawValue.split('\n')
                .map { it.trim() }
                .filter { it.contains('=') }
                .forEach { line ->
                    val pair = line.substringBefore(';').trim()
                    cm.setCookie(targetUrl, "$pair; Domain=.$cleanHost; Path=/")
                }
        }
    }
}

actual class PlatformWebViewHandle(internal var webView: WebView? = null) {
    private val mainHandler = Handler(Looper.getMainLooper())

    actual val canGoBack: Boolean
        get() = webView?.canGoBack() ?: false

    actual val currentUrl: String
        get() = webView?.url.orEmpty()

    actual fun goBack() {
        mainHandler.post { webView?.goBack() }
    }

    actual fun reload() {
        mainHandler.post { webView?.reload() }
    }

    actual fun loadUrl(url: String) {
        mainHandler.post { webView?.loadUrl(url) }
    }

    actual fun evaluateJavaScript(script: String) {
        mainHandler.post { webView?.evaluateJavascript(script, null) }
    }
}

private const val ANDROID_BRIDGE_INJECT_JS = """
(function() {
  if (!window.WeiboJSBridge) {
    window.WeiboJSBridge = {
      invoke: function(action, params, cb) {
        if (action === 'loginWithALT' && params && params.alt && window.__coboAltBridge) {
          window.__coboAltBridge.onAlt(String(params.alt));
        }
      }
    };
  }
  if (!window.loginWithALT) {
    window.loginWithALT = function(alt) {
      if (alt && window.__coboAltBridge) {
        window.__coboAltBridge.onAlt(String(alt));
      }
    };
  }
})();
"""

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun PlatformWeiboWebView(
    url: String,
    modifier: Modifier,
    useSessionCookie: Boolean,
    clearCookies: Boolean,
    userAgent: String,
    initialCookies: Map<String, String>,
    onCreated: (PlatformWebViewHandle) -> Unit,
    onBackAvailable: (Boolean) -> Unit,
    onUrlLoading: (PlatformWebViewHandle, String) -> Boolean,
    onCustomScheme: (String) -> Boolean,
    onPageStarted: (PlatformWebViewHandle, String) -> Unit,
    onPageCommitVisible: (PlatformWebViewHandle, String) -> Unit,
    onProgressChanged: (PlatformWebViewHandle, Int) -> Unit,
    onPageFinished: (String) -> Unit,
    onCloseWindow: () -> Unit,
    onConsoleMessage: (String) -> Unit,
    onReceivedError: (String, Int, String) -> Unit,
    onJsBridgeAlt: ((String) -> Unit)?,
) {
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val currentOnCreated by rememberUpdatedState(onCreated)
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
    val currentOnJsBridgeAlt by rememberUpdatedState(onJsBridgeAlt)

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

    val handle = remember { PlatformWebViewHandle() }
    var loadedUrl by remember { mutableStateOf(url) }

    fun routePopupTarget(parent: WebView, target: String) {
        if (target.isBlank()) return
        if (currentOnUrlLoading(handle, target)) return
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
            handle.webView = this
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

            addJavascriptInterface(
                object {
                    @JavascriptInterface
                    fun onAlt(alt: String) {
                        if (alt.isNotBlank()) {
                            mainHandler.post { currentOnJsBridgeAlt?.invoke(alt) }
                        }
                    }
                },
                "__coboAltBridge",
            )

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    currentOnProgressChanged(handle, newProgress)
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
                    if (currentOnUrlLoading(handle, target)) return true
                    if (isStandardWebScheme(target)) return false
                    if (currentOnCustomScheme(target)) return true
                    extractWebViewFallbackUrl(target)?.let { view.loadUrl(it) }
                    return true
                }

                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    if (currentOnJsBridgeAlt != null) {
                        view.evaluateJavascript(ANDROID_BRIDGE_INJECT_JS, null)
                    }
                    currentOnPageStarted(handle, url.orEmpty())
                }

                override fun onPageCommitVisible(view: WebView, url: String?) {
                    if (currentOnJsBridgeAlt != null) {
                        view.evaluateJavascript(ANDROID_BRIDGE_INJECT_JS, null)
                    }
                    currentOnPageCommitVisible(handle, url.orEmpty())
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    CookieManager.getInstance().flush()
                    if (currentOnJsBridgeAlt != null) {
                        view.evaluateJavascript(ANDROID_BRIDGE_INJECT_JS, null)
                    }
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

            fun plantCookiesAndLoad() {
                if (initialCookies.isNotEmpty()) {
                    WebCookieBridge.plant(initialCookies)
                } else if (useSessionCookie) {
                    val cookies = com.cobo.app.App.repo.session?.cookies
                    if (!cookies.isNullOrEmpty()) {
                        CookieVault.plant(cookies)
                    } else {
                        com.cobo.app.App.repo.cookie()?.split(";")?.forEach { pair ->
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
        currentOnCreated(handle)
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
            if (handle.webView === webView) handle.webView = null
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
