@file:OptIn(ExperimentalForeignApi::class)

package com.cobo.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import com.cobo.app.data.CookieVault
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSError
import platform.Foundation.NSHTTPCookie
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.WebKit.WKFrameInfo
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUIDelegateProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKUserScript
import platform.WebKit.WKUserScriptInjectionTime
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKWindowFeatures
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

actual class PlatformWebViewHandle(internal var webView: WKWebView? = null) {
    actual val canGoBack: Boolean
        get() = webView?.canGoBack ?: false

    actual val currentUrl: String
        get() = webView?.URL?.absoluteString.orEmpty()

    actual fun goBack() {
        dispatch_async(dispatch_get_main_queue()) {
            webView?.goBack()
        }
    }

    actual fun reload() {
        dispatch_async(dispatch_get_main_queue()) {
            webView?.reload()
        }
    }

    actual fun loadUrl(url: String) {
        val nsUrl = NSURL.URLWithString(url) ?: return
        dispatch_async(dispatch_get_main_queue()) {
            webView?.loadRequest(NSURLRequest.requestWithURL(nsUrl))
        }
    }

    actual fun evaluateJavaScript(script: String) {
        dispatch_async(dispatch_get_main_queue()) {
            webView?.evaluateJavaScript(script, completionHandler = null)
        }
    }
}

private const val BRIDGE_INJECT_JS = """
(function() {
  if (!window.WeiboJSBridge) {
    window.WeiboJSBridge = {
      invoke: function(action, params, cb) {
        if (action === 'loginWithALT' && params && params.alt) {
          window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.coboAlt &&
            window.webkit.messageHandlers.coboAlt.postMessage(String(params.alt));
        }
      }
    };
  }
  if (!window.loginWithALT) {
    window.loginWithALT = function(alt) {
      if (alt && window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.coboAlt) {
        window.webkit.messageHandlers.coboAlt.postMessage(String(alt));
      }
    };
  }
})();
"""

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
    val currentOnCreated by rememberUpdatedState(onCreated)
    val currentOnBackAvailable by rememberUpdatedState(onBackAvailable)
    val currentOnUrlLoading by rememberUpdatedState(onUrlLoading)
    val currentOnCustomScheme by rememberUpdatedState(onCustomScheme)
    val currentOnPageStarted by rememberUpdatedState(onPageStarted)
    val currentOnPageCommitVisible by rememberUpdatedState(onPageCommitVisible)
    val currentOnProgressChanged by rememberUpdatedState(onProgressChanged)
    val currentOnPageFinished by rememberUpdatedState(onPageFinished)
    val currentOnCloseWindow by rememberUpdatedState(onCloseWindow)
    val currentOnReceivedError by rememberUpdatedState(onReceivedError)
    val currentOnJsBridgeAlt by rememberUpdatedState(onJsBridgeAlt)

    val handle = remember { PlatformWebViewHandle() }
    var loadedUrl by remember { mutableStateOf(url) }

    fun syncCookiesFromWebView(wv: WKWebView, onSynced: () -> Unit) {
        wv.configuration.websiteDataStore.httpCookieStore.getAllCookies { list ->
            val domainMap = LinkedHashMap<String, MutableList<String>>()
            list?.forEach { raw ->
                val cookie = raw as? NSHTTPCookie ?: return@forEach
                val domain = cookie.domain.lowercase()
                val bucket = when {
                    domain.endsWith("passport.weibo.cn") -> CookieVault.PASSPORT_CN
                    domain.endsWith("passport.weibo.com") -> CookieVault.PASSPORT_COM
                    domain.endsWith("weibo.cn") -> CookieVault.WEIBO_CN
                    domain.endsWith("weibo.com") -> CookieVault.WEIBO_COM
                    domain.endsWith("sina.com.cn") -> CookieVault.SINA_COM_CN
                    else -> null
                } ?: return@forEach
                val pairs = domainMap.getOrPut(bucket) { ArrayList() }
                pairs.removeAll { it.substringBefore('=') == cookie.name }
                pairs.add("${cookie.name}=${cookie.value}")
            }
            val merged = domainMap.mapValues { (_, v) -> v.joinToString("\n") }
            if (merged.isNotEmpty()) {
                WebCookieBridge.updateCachedDomains(merged)
            }
            dispatch_async(dispatch_get_main_queue()) { onSynced() }
        }
    }

    val navDelegate = remember {
        object : NSObject(), WKNavigationDelegateProtocol {
            override fun webView(
                webView: WKWebView,
                decidePolicyForNavigationAction: WKNavigationAction,
                decisionHandler: (WKNavigationActionPolicy) -> Unit,
            ) {
                val target = decidePolicyForNavigationAction.request.URL?.absoluteString.orEmpty()
                if (target.isNotBlank()) {
                    if (currentOnUrlLoading(handle, target)) {
                        decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
                        return
                    }
                    val lower = target.lowercase()
                    val isStandard = lower.startsWith("http://") ||
                        lower.startsWith("https://") ||
                        lower.startsWith("about:") ||
                        lower.startsWith("javascript:") ||
                        lower.startsWith("data:") ||
                        lower.startsWith("blob:")
                    if (!isStandard) {
                        if (!currentOnCustomScheme(target)) {
                            com.cobo.app.ui.components.extractWebViewFallbackUrl(target)?.let { fallback ->
                                handle.loadUrl(fallback)
                            }
                        }
                        decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
                        return
                    }
                }
                decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyAllow)
            }

            @ObjCSignatureOverride
            override fun webView(webView: WKWebView, didStartProvisionalNavigation: WKNavigation?) {
                val cur = webView.URL?.absoluteString.orEmpty()
                currentOnProgressChanged(handle, 25)
                currentOnPageStarted(handle, cur)
            }

            @ObjCSignatureOverride
            override fun webView(webView: WKWebView, didCommitNavigation: WKNavigation?) {
                val cur = webView.URL?.absoluteString.orEmpty()
                currentOnProgressChanged(handle, 65)
                syncCookiesFromWebView(webView) {
                    currentOnBackAvailable(webView.canGoBack)
                    currentOnPageCommitVisible(handle, cur)
                }
            }

            @ObjCSignatureOverride
            override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
                val cur = webView.URL?.absoluteString.orEmpty()
                currentOnProgressChanged(handle, 100)
                syncCookiesFromWebView(webView) {
                    currentOnBackAvailable(webView.canGoBack)
                    currentOnPageFinished(cur)
                }
            }

            @ObjCSignatureOverride
            override fun webView(
                webView: WKWebView,
                didFailProvisionalNavigation: WKNavigation?,
                withError: NSError,
            ) {
                if (withError.code == -999L) return
                currentOnProgressChanged(handle, 100)
                currentOnReceivedError(
                    webView.URL?.absoluteString.orEmpty(),
                    withError.code.toInt(),
                    withError.localizedDescription,
                )
            }

            @ObjCSignatureOverride
            override fun webView(
                webView: WKWebView,
                didFailNavigation: WKNavigation?,
                withError: NSError,
            ) {
                if (withError.code == -999L) return
                currentOnProgressChanged(handle, 100)
                currentOnReceivedError(
                    webView.URL?.absoluteString.orEmpty(),
                    withError.code.toInt(),
                    withError.localizedDescription,
                )
            }
        }
    }

    val uiDelegate = remember {
        object : NSObject(), WKUIDelegateProtocol {
            override fun webView(
                webView: WKWebView,
                createWebViewWithConfiguration: WKWebViewConfiguration,
                forNavigationAction: WKNavigationAction,
                windowFeatures: WKWindowFeatures,
            ): WKWebView? {
                val target = forNavigationAction.request.URL?.absoluteString.orEmpty()
                if (target.isNotBlank() && !currentOnUrlLoading(handle, target)) {
                    handle.loadUrl(target)
                }
                return null
            }

            override fun webViewDidClose(webView: WKWebView) {
                currentOnCloseWindow()
            }
        }
    }

    val scriptHandler = remember {
        object : NSObject(), WKScriptMessageHandlerProtocol {
            override fun userContentController(
                userContentController: WKUserContentController,
                didReceiveScriptMessage: WKScriptMessage,
            ) {
                val alt = (didReceiveScriptMessage.body as? String)?.trim().orEmpty()
                if (alt.isNotBlank()) currentOnJsBridgeAlt?.invoke(alt)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            handle.webView?.stopLoading()
            handle.webView?.configuration?.userContentController?.removeScriptMessageHandlerForName("coboAlt")
            handle.webView = null
        }
    }

    UIKitView(
        factory = {
            val contentController = WKUserContentController().apply {
                addUserScript(
                    WKUserScript(
                        source = BRIDGE_INJECT_JS,
                        injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart,
                        forMainFrameOnly = false,
                    ),
                )
                addScriptMessageHandler(scriptHandler, name = "coboAlt")
            }
            val config = WKWebViewConfiguration().apply {
                userContentController = contentController
                allowsInlineMediaPlayback = true
                allowsPictureInPictureMediaPlayback = true
            }
            val wv = WKWebView(frame = CGRectZero.readValue(), configuration = config).apply {
                customUserAgent = userAgent
                navigationDelegate = navDelegate
                UIDelegate = uiDelegate
                allowsBackForwardNavigationGestures = true
            }
            handle.webView = wv
            currentOnCreated(handle)

            fun startLoad() {
                val target = NSURL.URLWithString(url) ?: return
                wv.loadRequest(NSURLRequest.requestWithURL(target))
            }

            val cookieStore = config.websiteDataStore.httpCookieStore
            if (clearCookies) {
                WebCookieBridge.clearAll {
                    dispatch_async(dispatch_get_main_queue()) {
                        if (useSessionCookie && initialCookies.isNotEmpty()) {
                            WebCookieBridge.writeCookiesToStore(cookieStore, initialCookies) {
                                dispatch_async(dispatch_get_main_queue()) { startLoad() }
                            }
                        } else {
                            startLoad()
                        }
                    }
                }
            } else if (useSessionCookie && initialCookies.isNotEmpty()) {
                WebCookieBridge.writeCookiesToStore(cookieStore, initialCookies) {
                    dispatch_async(dispatch_get_main_queue()) { startLoad() }
                }
            } else {
                startLoad()
            }
            wv
        },
        update = { wv ->
            if (loadedUrl != url) {
                loadedUrl = url
                NSURL.URLWithString(url)?.let { wv.loadRequest(NSURLRequest.requestWithURL(it)) }
            }
        },
        modifier = modifier,
    )
}
