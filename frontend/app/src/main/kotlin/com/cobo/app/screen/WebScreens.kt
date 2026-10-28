package com.cobo.app.screen

import android.webkit.CookieManager
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.platform.testTag
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
import io.github.suqi8.coui.kmp.basic.TextButton
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
                // 纯本地模式手上只有访客 cookie，塞进登录页只会让下面误以为已经登录。
                useSessionCookie = !fresh && App.repo.hasSession(),
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
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .background(COUITheme.colorScheme.background.copy(alpha = 0.92f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = hint,
                    fontSize = 12.sp,
                    color = COUITheme.colorScheme.onSurfaceSecondary,
                )
                // 只在启动就落到登录页时给：从纯本地模式点进来登录的，返回键就能回去。
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
