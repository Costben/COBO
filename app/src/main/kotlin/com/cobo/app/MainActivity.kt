package com.cobo.app

import android.app.PictureInPictureParams
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
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
import com.cobo.app.data.IncomingShare
import com.cobo.app.data.combineSharedText
import com.cobo.app.data.extractWeiboLinkFromText
import com.cobo.app.data.stageSharedImageUris
import com.cobo.app.platform.AndroidPlatformHolder
import com.cobo.app.platform.PlatformContext
import com.cobo.app.ui.CoboRoot
import com.cobo.app.ui.CoboTheme
import com.cobo.app.ui.FloatingVideoController
import com.cobo.app.ui.clampPipAspectRatio
import com.cobo.app.ui.isDarkTheme
import com.cobo.app.ui.shouldAutoEnterSystemPip
import io.github.suqi8.coui.kmp.basic.ScrollToTopHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private val floatingVideoController = FloatingVideoController()
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var inPictureInPicture by mutableStateOf(false)
    private var lastCheckedClipTimestamp: Long = -1L
    private var pendingClipboardCheck: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidPlatformHolder.initApplication(applicationContext)
        AndroidPlatformHolder.attachActivity(this)
        App.init(PlatformContext())
        App.startRoute = intent?.getStringExtra("cobo.route")
        handleIncomingIntent(intent)
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
        handleIncomingIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        pendingClipboardCheck = true
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && pendingClipboardCheck) {
            pendingClipboardCheck = false
            checkClipboardForWeiboLink()
        }
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

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                val data = intent.dataString ?: return
                App.enqueueDeepLink(data)
            }
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> {
                val combinedText = combineSharedText(
                    intent.getStringExtra(Intent.EXTRA_SUBJECT),
                    intent.getStringExtra(Intent.EXTRA_TEXT),
                )
                val uris = extractShareImageUris(intent)
                if (uris.isEmpty()) {
                    App.enqueueShare(IncomingShare(text = combinedText))
                } else {
                    val appContext = applicationContext
                    activityScope.launch {
                        val staged = stageSharedImageUris(appContext, uris)
                        withContext(Dispatchers.Main) {
                            App.enqueueShare(IncomingShare(text = combinedText, imageUris = staged))
                        }
                    }
                }
            }
        }
    }

    private fun extractShareImageUris(intent: Intent): List<Uri> {
        val result = ArrayList<Uri>()
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val single = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                if (single != null) result.add(single)
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val multiple = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                }
                if (multiple != null) result.addAll(multiple)
            }
        }
        val clip = intent.clipData
        if (clip != null) {
            for (i in 0 until clip.itemCount) {
                clip.getItemAt(i)?.uri?.let(result::add)
            }
        }
        return result.distinct()
    }

    private fun checkClipboardForWeiboLink() {
        if (!App.settings.clipboardLinkDetect || inPictureInPicture) return
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        // 先读元信息再决定是否读取正文，且对同一时间戳的剪贴内容只读一次，避免 Android 12+ 重复弹出粘贴提示。
        val desc = clipboard.primaryClipDescription ?: return
        val hasText = desc.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) ||
            desc.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)
        if (!hasText) return
        val ts = desc.timestamp
        if (ts > 0L && ts == lastCheckedClipTimestamp) return
        if (ts > 0L) lastCheckedClipTimestamp = ts

        val clipText = runCatching {
            clipboard.primaryClip
                ?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
                ?.coerceToText(this)
                ?.toString()
        }.getOrNull().orEmpty()
        if (clipText.isBlank()) return

        val detected = extractWeiboLinkFromText(clipText) ?: return
        val signature = "${if (ts > 0L) ts else clipText.hashCode()}:${detected.resolvedUrl}"
        App.offerClipboardLink(signature, detected)
    }

    private fun applyOrientation() {
        requestedOrientation = if (App.settings.allowLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }
}
