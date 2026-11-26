package com.cobo.app.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cobo.app.App
import com.cobo.app.data.Api
import com.cobo.app.data.CookieVault
import com.cobo.app.platform.PlatformBackHandler
import com.cobo.app.platform.PlatformWebViewHandle
import com.cobo.app.platform.copyToClipboard
import com.cobo.app.platform.defaultWebUserAgent
import com.cobo.app.platform.parseUrlHost
import com.cobo.app.ui.CoboIcons
import com.cobo.app.ui.NavState
import com.cobo.app.ui.PinnedBarDensity
import com.cobo.app.ui.Route
import com.cobo.app.ui.components.BackIconButton
import com.cobo.app.ui.components.WeiboWebView
import com.cobo.app.ui.components.cleanMobileChromeUserAgent
import com.cobo.app.ui.openExternalUrl
import com.cobo.app.ui.pageContainerColor
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.couiTopAppBarActionItem
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Copy
import io.github.suqi8.coui.kmp.icon.extended.Refresh
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.launch

@Composable
fun WebScreen(nav: NavState, url: String, title: String) {
    var canGoBack by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<PlatformWebViewHandle?>(null) }

    fun currentPageUrl(): String =
        webView?.currentUrl
            ?.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
            ?: url

    PlatformBackHandler {
        if (canGoBack) webView?.goBack() else nav.pop()
    }

    Scaffold(
        containerColor = pageContainerColor(),
        topBar = {
            PinnedBarDensity {
                SmallTopAppBar(
                    color = pageContainerColor(),
                    title = title,
                    navigationIcon = {
                        BackIconButton {
                            if (canGoBack) webView?.goBack() else nav.pop()
                        }
                    },
                    actions = {
                        HighlightedIconButton(
                            onClick = {
                                copyToClipboard(currentPageUrl(), "url")
                                App.toast("已复制链接")
                            },
                            modifier = Modifier.couiTopAppBarActionItem().testTag("web_copy_url"),
                        ) {
                            Icon(COUIIcons.Copy, "复制链接", Modifier.size(22.dp))
                        }
                        HighlightedIconButton(
                            onClick = { openExternalUrl(App.context, currentPageUrl()) },
                            modifier = Modifier.couiTopAppBarActionItem().testTag("web_open_external"),
                        ) {
                            Icon(CoboIcons.Globe, "在浏览器中打开", Modifier.size(24.dp))
                        }
                    },
                )
            }
        },
    ) { padding ->
        val webUserAgent = remember(url, App.repo.session?.loginType) {
            val host = parseUrlHost(url)
            val isDesktopWeibo = host == "weibo.com" || host == "www.weibo.com"
            if (App.repo.session?.loginType == com.cobo.app.data.LOGIN_TYPE_CLIENT && isDesktopWeibo) {
                Api.DESKTOP_UA
            } else {
                Api.WEB_UA
            }
        }
        WeiboWebView(
            url = url,
            modifier = Modifier.fillMaxSize().padding(padding),
            userAgent = webUserAgent,
            onCreated = { webView = it },
            onBackAvailable = { canGoBack = it },
        )
    }
}

@Composable
fun LoginScreen(nav: NavState, addAccount: Boolean, fresh: Boolean = false, relogin: String? = null) {
    val scope = rememberCoroutineScope()
    var canGoBack by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<PlatformWebViewHandle?>(null) }
    var saving by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var hint by remember {
        mutableStateOf(
            if (relogin != null) "请重新登录「$relogin」"
            else "",
        )
    }
    val loginUserAgent = remember {
        cleanMobileChromeUserAgent(defaultWebUserAgent())
    }

    fun tryFinishCookieLogin() {
        if (saving) return
        val cookies = CookieVault.readDomains()
        val header = CookieVault.authHeader(cookies, "m.weibo.cn")
        if (header.isBlank() || !header.contains("SUB=")) return
        saving = true
        hint = "登录成功，正在保存…"
        scope.launch {
            val account = runCatching { App.repo.loginWithCookie(header, cookies) }.getOrNull()
            if (account != null) {
                hint = "已登录：${account.name.ifBlank { account.uid }}"
                if (addAccount) {
                    nav.reset(Route.Main)
                    nav.push(Route.Accounts)
                } else {
                    nav.reset(Route.Main)
                }
            } else {
                saving = false
                hint = "读取登录信息失败，请重试"
            }
        }
    }

    PlatformBackHandler(enabled = canGoBack || nav.stack.size > 1) {
        if (canGoBack) webView?.goBack()
        else if (nav.stack.size > 1) nav.pop()
    }

    Scaffold(
        containerColor = pageContainerColor(),
        topBar = {
            PinnedBarDensity {
                SmallTopAppBar(
                    color = pageContainerColor(),
                    title = if (relogin != null) "重新登录" else if (addAccount) "添加账号" else "登录",
                    navigationIcon = {
                        BackIconButton {
                            if (canGoBack) webView?.goBack()
                            else if (nav.stack.size > 1) nav.pop()
                        }
                    },
                    actions = {
                        HighlightedIconButton(
                            onClick = {
                                hint = if (relogin != null) "请重新登录「$relogin」" else ""
                                webView?.reload()
                            },
                            modifier = Modifier.couiTopAppBarActionItem().testTag("login_web_reload"),
                        ) {
                            Icon(COUIIcons.Refresh, "刷新页面", Modifier.size(22.dp))
                        }
                    },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            WeiboWebView(
                url = "https://passport.weibo.cn/signin/login?entry=wapsso&r=https%3A%2F%2Fm.weibo.cn%2F",
                modifier = Modifier.fillMaxSize(),
                useSessionCookie = !fresh && App.repo.hasSession(),
                clearCookies = fresh,
                userAgent = loginUserAgent,
                onCreated = { webView = it },
                onBackAvailable = { canGoBack = it },
                onProgressChanged = { _, p -> progress = p },
                onPageCommitVisible = { _, _ -> tryFinishCookieLogin() },
                onPageFinished = { tryFinishCookieLogin() },
                onReceivedError = { _, code, desc ->
                    if (!saving) {
                        hint = if (desc.isNotBlank()) "页面加载异常（$code）：$desc，可点右上角刷新" else "页面加载异常（$code），可点右上角刷新"
                    }
                },
            )
            if (progress in 1..99) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth(progress / 100f)
                        .height(2.dp)
                        .background(COUITheme.colorScheme.primary),
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .background(COUITheme.colorScheme.background.copy(alpha = 0.92f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (hint.isNotBlank()) {
                    Text(
                        text = hint,
                        fontSize = 12.sp,
                        color = COUITheme.colorScheme.onSurfaceSecondary,
                    )
                }
                TextButton(
                    text = "验证码 / 密码登录",
                    onClick = {
                        val prev = nav.stack.dropLast(1).lastOrNull()?.route
                        if (prev is Route.SmsLogin) nav.pop()
                        else nav.push(Route.SmsLogin(addAccount, relogin))
                    },
                    modifier = Modifier.testTag("login_sms"),
                )
                if (!addAccount && nav.stack.size == 1) {
                    TextButton(
                        text = "不登录，纯本地浏览",
                        onClick = {
                            App.repo.enterLocalMode()
                            nav.reset(Route.Main)
                        },
                        modifier = Modifier.testTag("login_local_mode"),
                    )
                }
            }
        }
    }
}
