package com.cobo.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

@OptIn(ExperimentalComposeUiApi::class)
fun MainViewController(): UIViewController {
    App.ensureInitialized()
    return ComposeUIViewController(
        configure = {
            // 避免 Compose 后台线程并行编码 Metal 与 UIKit AVPlayerLayer / WKWebView 竞态闪烁。
            parallelRendering = false
        },
    ) {
        CoboAppRoot()
    }
}

fun handleIncomingUrl(url: String) {
    App.enqueueDeepLink(url)
}
