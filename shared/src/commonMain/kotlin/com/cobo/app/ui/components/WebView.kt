package com.cobo.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cobo.app.App
import com.cobo.app.data.Api
import com.cobo.app.data.CookieVault
import com.cobo.app.platform.PlatformWebViewHandle
import com.cobo.app.platform.PlatformWeiboWebView
import com.cobo.app.platform.urlDecode

@Composable
fun WeiboWebView(
    url: String,
    modifier: Modifier = Modifier,
    useSessionCookie: Boolean = true,
    clearCookies: Boolean = false,
    userAgent: String = Api.WEB_UA,
    onConfigure: (PlatformWebViewHandle) -> Unit = {},
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
) {
    val cookies = if (useSessionCookie && !clearCookies) {
        val sessionCookies = App.repo.session?.cookies
        if (!sessionCookies.isNullOrEmpty()) {
            sessionCookies
        } else {
            App.repo.cookie()?.let { CookieVault.seed(it) }.orEmpty()
        }
    } else {
        emptyMap()
    }
    PlatformWeiboWebView(
        url = url,
        modifier = modifier,
        useSessionCookie = useSessionCookie,
        clearCookies = clearCookies,
        userAgent = userAgent,
        initialCookies = cookies,
        onCreated = { handle ->
            onConfigure(handle)
            onCreated(handle)
        },
        onBackAvailable = onBackAvailable,
        onUrlLoading = onUrlLoading,
        onCustomScheme = onCustomScheme,
        onPageStarted = onPageStarted,
        onPageCommitVisible = onPageCommitVisible,
        onProgressChanged = onProgressChanged,
        onPageFinished = onPageFinished,
        onCloseWindow = onCloseWindow,
        onConsoleMessage = onConsoleMessage,
        onReceivedError = onReceivedError,
        onJsBridgeAlt = onJsBridgeAlt,
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
    val decoded = runCatching { urlDecode(encoded) }.getOrDefault(encoded).trim()
    return decoded.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
}

internal fun extractIntentFallbackUrl(raw: String): String? {
    if (!raw.startsWith("intent://", ignoreCase = true)) return null
    val marker = "S.browser_fallback_url="
    val at = raw.indexOf(marker)
    if (at < 0) return null
    val encoded = raw.substring(at + marker.length).substringBefore(';')
    val decoded = runCatching { urlDecode(encoded) }.getOrDefault(encoded)
    return decoded.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
}
