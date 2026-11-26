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
import com.cobo.app.platform.AndroidPlatformHolder
import com.cobo.app.platform.PlatformContext
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
        AndroidPlatformHolder.initApplication(applicationContext)
        AndroidPlatformHolder.attachActivity(this)
        App.init(PlatformContext())
        App.startRoute = intent?.getStringExtra("cobo.route")
        handleDeepLinkIntent(intent)
        super.onCreate(savedInstanceState)
        applyOrientation()
        enableEdgeToEdge()
        AndroidPlatformHolder.onRequestSystemPip = { ratio -> enterVideoPictureInPicture(ratio) }
        floatingVideoController.requestSystemPipCallback = { ratio -> enterVideoPictureInPicture(ratio) }

        setContent {
            CoboTheme {
                val dark = isDarkTheme()
                // 设置里改「允许横屏」时立刻生效，不用等下次冷启动。
                LaunchedEffect(App.settings.allowLandscape) { applyOrientation() }
                DisposableEffect(dark) {
                    window.setBackgroundDrawable(
                        ColorDrawable(
                            if (dark) Color.rgb(0x30, 0x30, 0x30)
                            else Color.rgb(0xF0, 0xF1, 0xF2),
                        ),
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

    override fun onResume() {
        super.onResume()
        AndroidPlatformHolder.attachActivity(this)
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
        AndroidPlatformHolder.notifySystemPipChanged(isInPictureInPictureMode)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            val session = floatingVideoController.activePipSession()
            val shouldEnter = shouldAutoEnterSystemPip(
                settingEnabled = App.settings.videoAutoFloatOnBack,
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
        AndroidPlatformHolder.detachActivity(this)
        AndroidPlatformHolder.onRequestSystemPip = null
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

    private fun applyOrientation() {
        requestedOrientation = if (App.settings.allowLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }
}
