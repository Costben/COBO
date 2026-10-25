package com.cobo.app.screen

import android.webkit.CookieManager
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cobo.app.App
import com.cobo.app.ui.NavState
import com.cobo.app.ui.pageContainerColor
import com.cobo.app.ui.Route
import com.cobo.app.ui.components.BackIconButton
import com.cobo.app.ui.components.WeiboWebView
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.launch

@Composable
fun WebScreen(nav: NavState, url: String, title: String) {
    var canGoBack by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    BackHandler {
        if (canGoBack) webView?.goBack() else nav.pop()
    }

    Scaffold(
        containerColor = pageContainerColor(),
        topBar = {
            SmallTopAppBar(
                color = pageContainerColor(),
                title = title,
                navigationIcon = {
                    BackIconButton {
                        if (canGoBack) webView?.goBack() else nav.pop()
                    }
                },
            )
        },
    ) { padding ->
        WeiboWebView(
            url = url,
            modifier = Modifier.fillMaxSize().padding(padding),
            onCreated = { webView = it },
            onBackAvailable = { canGoBack = it },
        )
    }
}

@Composable
fun LoginScreen(nav: NavState, addAccount: Boolean, fresh: Boolean = false) {
    val scope = rememberCoroutineScope()
    var canGoBack by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var saving by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf("登录完成后会自动保存会话") }

    BackHandler(enabled = canGoBack || nav.stack.size > 1) {
        if (canGoBack) webView?.goBack()
        else if (nav.stack.size > 1) nav.pop()
    }

    Scaffold(
        containerColor = pageContainerColor(),
        topBar = {
            SmallTopAppBar(
                color = pageContainerColor(),
                title = if (addAccount) "添加账号" else "登录",
                navigationIcon = {
                    BackIconButton {
                        if (canGoBack) webView?.goBack()
                        else if (nav.stack.size > 1) nav.pop()
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            WeiboWebView(
                url = "https://passport.weibo.cn/signin/login?entry=wapsso&r=https%3A%2F%2Fm.weibo.cn%2F",
                modifier = Modifier.fillMaxSize(),
                useSessionCookie = !fresh,
                clearCookies = fresh,
                onCreated = { webView = it },
                onBackAvailable = { canGoBack = it },
                onPageFinished = {
                    if (saving) return@WeiboWebView
                    val cookie = CookieManager.getInstance().getCookie("https://m.weibo.cn")
                    if (cookie.isNullOrBlank() || !cookie.contains("SUB=")) return@WeiboWebView
                    saving = true
                    hint = "登录成功，正在保存…"
                    scope.launch {
                        val account = runCatching { App.repo.loginWithCookie(cookie) }.getOrNull()
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
                },
            )
            Text(
                text = hint,
                fontSize = 12.sp,
                color = COUITheme.colorScheme.onSurfaceSecondary,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .background(COUITheme.colorScheme.background.copy(alpha = 0.92f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}
