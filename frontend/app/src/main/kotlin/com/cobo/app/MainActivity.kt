package com.cobo.app

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cobo.app.ui.CoboRoot
import com.cobo.app.ui.CoboTheme
import com.cobo.app.ui.FloatingVideoController
import com.cobo.app.ui.clampPipAspectRatio
import com.cobo.app.ui.isDarkTheme
import com.cobo.app.ui.shouldAutoEnterSystemPip
import io.github.suqi8.coui.kmp.basic.ScrollToTopHost
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private val floatingVideoController = FloatingVideoController()
    private var inPictureInPicture by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        App.init(this)
        App.startRoute = intent?.getStringExtra("cobo.route")
        handleDeepLinkIntent(intent)
        super.onCreate(savedInstanceState)
        applyOrientation()
        enableEdgeToEdge()
        floatingVideoController.requestSystemPipCallback = { ratio -> enterVideoPictureInPicture(ratio) }

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
                    CoboRoot(
                        floatingVideoController = floatingVideoController,
                        onUpdateAutoPip = ::updateAutoEnterPipParams,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLinkIntent(intent)
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPictureInPicture = isInPictureInPictureMode
        floatingVideoController.systemPipActive = isInPictureInPictureMode
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            val session = floatingVideoController.activePipSession()
            val shouldEnter = shouldAutoEnterSystemPip(
                autoFloatEnabled = App.settings.videoAutoFloatOnBack,
                hasActiveVideo = session != null,
                isPlaying = session?.isPlaying == true,
                isLive = session?.request?.isLive == true,
            )
            if (shouldEnter && session != null) {
                enterVideoPictureInPicture(session.aspectRatio)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isInPictureInPictureMode) {
            floatingVideoController.pauseActiveIfNotPip()
        }
    }

    override fun onDestroy() {
        floatingVideoController.requestSystemPipCallback = null
        if (isFinishing) {
            floatingVideoController.releaseAll()
        }
        super.onDestroy()
    }

    private fun buildPipParams(aspectRatio: Float, autoEnter: Boolean): PictureInPictureParams {
        val clamped = clampPipAspectRatio(aspectRatio)
        val rational = Rational((clamped * 1000f).roundToInt(), 1000)
        val builder = PictureInPictureParams.Builder().setAspectRatio(rational)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(autoEnter)
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    private fun enterVideoPictureInPicture(aspectRatio: Float): Boolean {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return false
        return runCatching {
            enterPictureInPictureMode(buildPipParams(aspectRatio, autoEnter = false))
        }.getOrDefault(false)
    }

    private fun updateAutoEnterPipParams(enabled: Boolean, aspectRatio: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
        runCatching {
            setPictureInPictureParams(buildPipParams(aspectRatio, autoEnter = enabled))
        }
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
