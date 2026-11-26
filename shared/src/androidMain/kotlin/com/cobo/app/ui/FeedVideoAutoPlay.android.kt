@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.cobo.app.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.view.LayoutInflater
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.cobo.app.data.Api
import com.cobo.app.screen.defaultRefererFor
import com.cobo.app.shared.R

private fun isWifiNetwork(context: Context): Boolean = runCatching {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return@runCatching false
    val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return@runCatching false
    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
}.getOrDefault(false)

@Composable
internal actual fun rememberPlatformAutoPlayEnvironmentState(): Pair<Boolean, Boolean> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

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

    return isWifi to foreground
}

@Composable
internal actual fun PlatformInlineVideoPreview(
    initialUrl: String,
    referer: String?,
    fallbackUrl: String?,
    muted: Boolean,
    onFirstFrame: () -> Unit,
    onFailed: () -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
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
