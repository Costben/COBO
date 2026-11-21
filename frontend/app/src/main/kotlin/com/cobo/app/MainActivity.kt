package com.cobo.app

import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import com.cobo.app.ui.CoboRoot
import com.cobo.app.ui.CoboTheme
import com.cobo.app.ui.isDarkTheme
import io.github.suqi8.coui.kmp.basic.ScrollToTopHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        App.init(this)
        App.startRoute = intent?.getStringExtra("cobo.route")
        handleDeepLinkIntent(intent)
        super.onCreate(savedInstanceState)
        applyOrientation()
        enableEdgeToEdge()

        setContent {
            CoboTheme {
                val dark = isDarkTheme()
                // 设置里改「允许横屏」时立刻生效，不用等下次冷启动。
                LaunchedEffect(App.settings.allowLandscape) { applyOrientation() }
                DisposableEffect(dark) {
                    // The window background is what shows during activity transitions and
                    // before the first Compose frame. Follow the app's own theme instead of
                    // the system's, or a dark app on a light system flashes white.
                    window.setBackgroundDrawable(
                        ColorDrawable(
                            if (dark) Color.rgb(0x30, 0x30, 0x30)
                            else Color.rgb(0xF0, 0xF1, 0xF2)
                        )
                    )
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                        navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        window.isNavigationBarContrastEnforced = false
                    }
                    onDispose {}
                }
                ScrollToTopHost {
                    CoboRoot()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLinkIntent(intent)
    }

    private fun handleDeepLinkIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val data = intent.dataString ?: return
        App.enqueueDeepLink(data)
    }

    /**
     * 关掉「允许横屏」就把 Activity 钉成竖屏，系统怎么转都不进横屏，也就不会进左右双页。
     * 开着的时候交回系统（UNSPECIFIED），跟随传感器和系统自己的旋转锁。
     */
    private fun applyOrientation() {
        requestedOrientation = if (App.settings.allowLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }
}
