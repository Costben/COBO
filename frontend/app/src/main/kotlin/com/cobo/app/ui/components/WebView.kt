package com.cobo.app.ui.components

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
    onCreated: (WebView) -> Unit = {},
    onBackAvailable: (Boolean) -> Unit = {},
    onPageFinished: (String) -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentOnPageFinished by rememberUpdatedState(onPageFinished)
    val currentOnBackAvailable by rememberUpdatedState(onBackAvailable)
    val webView = remember {
        if (clearCookies) {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = false
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            settings.mediaPlaybackRequiresUserGesture = false
            settings.userAgentString = Api.WEB_UA
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webChromeClient = WebChromeClient()
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
                    // 拦截跳端 scheme（intent://、iqiyi://、sinaweibo:// 等），防止 WebView 报 ERR_UNKNOWN_URL_SCHEME。
                    extractIntentFallbackUrl(target)?.let { view.loadUrl(it) }
                    return true
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    currentOnBackAvailable(view.canGoBack())
                    currentOnPageFinished(url.orEmpty())
                }
            }
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
            if (view.url != url) view.loadUrl(url)
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
