package com.cobo.app.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.view.LayoutInflater
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.cobo.app.App
import com.cobo.app.R
import com.cobo.app.data.Api
import com.cobo.app.data.FeedVideoCandidate
import com.cobo.app.data.VideoInfo
import com.cobo.app.data.isWifiNetwork
import com.cobo.app.data.pickBestFeedVideoCandidate
import com.cobo.app.data.shouldAutoPlayFeedVideo
import com.cobo.app.screen.ResolvedVideo
import com.cobo.app.screen.defaultRefererFor
import com.cobo.app.screen.isDirectVideoStream
import com.cobo.app.screen.resolvePlayableVideo
import com.cobo.app.screen.shouldRefreshDirectStreamBeforePlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

val LocalFeedVideoAutoPlay = staticCompositionLocalOf<FeedVideoAutoPlayController?> { null }
val LocalFeedVideoRouteActive = staticCompositionLocalOf { false }

private const val AutoPlaySettleDelayMillis = 220L
private const val ActiveKeepVisibleRatio = 0.45f

/**
 * 全局信息流短视频内联预览调度器：同一时刻只允许视口中最居中的一张非直播视频卡片播放。
 */
@Stable
class FeedVideoAutoPlayController {
    private val seq = AtomicLong(0L)
    private val candidates = LinkedHashMap<String, FeedVideoCandidate>()
    private var viewportTopPx = 0f
    private var viewportBottomPx = 0f
    private var scope: CoroutineScope? = null
    private var pendingJob: Job? = null

    var activeKey by mutableStateOf<String?>(null)
        private set

    var environmentAllowed by mutableStateOf(false)
        private set

    fun nextKey(): String = "fv_${seq.incrementAndGet()}"

    fun attach(scope: CoroutineScope) {
        this.scope = scope
    }

    fun updateEnvironment(allowed: Boolean) {
        environmentAllowed = allowed
        if (!allowed) {
            pendingJob?.cancel()
            pendingJob = null
            activeKey = null
        } else {
            scheduleRecompute(immediate = false)
        }
    }

    fun updateCandidate(
        candidate: FeedVideoCandidate,
        viewportTop: Float,
        viewportBottom: Float,
    ) {
        viewportTopPx = viewportTop
        viewportBottomPx = viewportBottom
        candidates[candidate.key] = candidate
        if (!environmentAllowed) {
            if (activeKey != null) activeKey = null
            return
        }
        if (candidate.key == activeKey && candidate.visibleRatio < ActiveKeepVisibleRatio) {
            activeKey = null
        }
        scheduleRecompute(immediate = false)
    }

    fun unregister(key: String) {
        candidates.remove(key)
        if (activeKey == key) {
            pendingJob?.cancel()
            pendingJob = null
            activeKey = null
        }
        scheduleRecompute(immediate = false)
    }

    fun pauseAll() {
        pendingJob?.cancel()
        pendingJob = null
        activeKey = null
    }

    private fun scheduleRecompute(immediate: Boolean) {
        if (!environmentAllowed) {
            pendingJob?.cancel()
            pendingJob = null
            activeKey = null
            return
        }
        val desired = pickBestFeedVideoCandidate(
            candidates = candidates.values,
            viewportTopPx = viewportTopPx,
            viewportBottomPx = viewportBottomPx,
            currentActiveKey = activeKey,
        )
        if (desired == activeKey) {
            pendingJob?.cancel()
            pendingJob = null
            return
        }
        if (desired == null || immediate || scope == null) {
            pendingJob?.cancel()
            pendingJob = null
            activeKey = desired
            return
        }
        pendingJob?.cancel()
        pendingJob = scope?.launch {
            delay(AutoPlaySettleDelayMillis)
            if (!environmentAllowed) {
                activeKey = null
                return@launch
            }
            activeKey = pickBestFeedVideoCandidate(
                candidates = candidates.values,
                viewportTopPx = viewportTopPx,
                viewportBottomPx = viewportBottomPx,
                currentActiveKey = activeKey,
            )
        }
    }
}

/**
 * 监听 Wi-Fi 状态、前后台生命周期、路由焦点与悬浮窗冲突，并同步到 [controller]。
 */
@Composable
fun rememberFeedVideoAutoPlayEnvironment(
    controller: FeedVideoAutoPlayController,
    nav: NavState,
    floatingVideoController: FloatingVideoController,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    SideEffect { controller.attach(scope) }

    var isWifi by remember { mutableStateOf(isWifiNetwork(context)) }
    DisposableEffect(context) {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (manager == null) {
            onDispose { }
        } else {
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    isWifi = isWifiNetwork(context)
                }

                override fun onLost(network: Network) {
                    isWifi = isWifiNetwork(context)
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    isWifi = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                }
            }
            val registered = runCatching {
                manager.registerDefaultNetworkCallback(callback)
                true
            }.getOrDefault(false)
            isWifi = isWifiNetwork(context)
            onDispose {
                if (registered) {
                    runCatching { manager.unregisterNetworkCallback(callback) }
                }
            }
        }
    }

    var foreground by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> foreground = true
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> foreground = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val topRoute = nav.stack.lastOrNull { !it.exiting }?.route
    val routeEligible = when (topRoute) {
        Route.Main -> nav.mainTab == 0
        is Route.Profile,
        Route.Favorites,
        is Route.TopicFeed,
        is Route.SuperTopic,
        Route.Nearby,
        is Route.Container,
        Route.MyLikes,
        is Route.Detail,
        -> true
        else -> false
    }
    val hasFloating = floatingVideoController.activeSession != null ||
        floatingVideoController.fullscreenSession != null
    val settingEnabled = App.settings.feedVideoAutoPlay

    LaunchedEffect(settingEnabled, isWifi, foreground, routeEligible, hasFloating) {
        controller.updateEnvironment(
            shouldAutoPlayFeedVideo(
                settingEnabled = settingEnabled,
                isWifi = isWifi,
                isForeground = foreground,
                isRouteActive = routeEligible,
                isLive = false,
                url = "eligible",
                hasFloatingVideo = hasFloating,
            ),
        )
    }
}

/**
 * 信息流卡片内的轻量短视频预览层：使用 TextureView 避免 SurfaceView 黑块/穿层，
 * 播放前自动补拉过期直链，不主动申请音频焦点以免打断后台音乐。
 */
@OptIn(UnstableApi::class)
@Composable
internal fun FeedInlineVideoPreview(
    video: VideoInfo,
    muted: Boolean,
    onFirstFrame: () -> Unit,
    onFailed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var resolved by remember(video.url, video.fid, video.statusId) {
        mutableStateOf<ResolvedVideo.DirectStream?>(
            if (
                isDirectVideoStream(video.url) &&
                !shouldRefreshDirectStreamBeforePlay(video.url, video.fid, video.statusId, false)
            ) {
                ResolvedVideo.DirectStream(
                    url = video.url,
                    referer = defaultRefererFor(video.url),
                    isLive = false,
                )
            } else {
                null
            },
        )
    }

    LaunchedEffect(video.url, video.fid, video.statusId) {
        if (resolved != null) return@LaunchedEffect
        val result = resolvePlayableVideo(
            rawUrl = video.url,
            cacheDir = context.cacheDir,
            isLiveHint = false,
            fid = video.fid,
            statusId = video.statusId,
            titleHint = video.title,
        )
        if (result is ResolvedVideo.DirectStream && !result.isLive && result.url.isNotBlank()) {
            resolved = result
        } else {
            onFailed()
        }
    }

    val initialUrl = resolved?.url.orEmpty()
    val referer = resolved?.referer
    val fallbackUrl = resolved?.fallbackUrl
    var activeUrl by remember(initialUrl) { mutableStateOf(initialUrl) }
    if (activeUrl.isBlank()) return

    val player = remember(activeUrl, referer) {
        val effectiveReferer = referer?.takeIf { it.isNotBlank() } ?: defaultRefererFor(activeUrl)
        val httpFactory = DefaultHttpDataSource.Factory().apply {
            setUserAgent(Api.WEB_UA)
            setAllowCrossProtocolRedirects(true)
            if (!effectiveReferer.isNullOrBlank()) {
                setDefaultRequestProperties(mapOf("Referer" to effectiveReferer))
            }
        }
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, httpFactory)))
            .build()
            .apply {
                volume = if (muted) 0f else 1f
                repeatMode = Player.REPEAT_MODE_ONE
                playWhenReady = true
                setMediaItem(MediaItem.fromUri(activeUrl))
                prepare()
            }
    }

    LaunchedEffect(player, muted) {
        player.volume = if (muted) 0f else 1f
    }

    DisposableEffect(player, fallbackUrl) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                onFirstFrame()
            }

            override fun onPlayerError(error: PlaybackException) {
                val backup = fallbackUrl?.takeIf { it.isNotBlank() && it != activeUrl }
                if (backup != null) {
                    activeUrl = backup
                } else {
                    onFailed()
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            (LayoutInflater.from(ctx).inflate(R.layout.live_player_view, null) as PlayerView).apply {
                useController = false
                controllerAutoShow = false
                setControllerShowTimeoutMs(0)
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                hideController()
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                setEnableComposeSurfaceSyncWorkaround(true)
                this.player = player
            }
        },
        update = { view ->
            if (view.player !== player) {
                view.player = player
            }
            view.hideController()
        },
        onRelease = { view ->
            view.player = null
        },
        modifier = modifier,
    )
}
