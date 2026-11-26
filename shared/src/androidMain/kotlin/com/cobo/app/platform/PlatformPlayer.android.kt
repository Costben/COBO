@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.cobo.app.platform

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.Image
import com.cobo.app.shared.R
import java.io.File

@Stable
actual class PlatformVideoPlayer actual constructor() {
    private val mainHandler = Handler(Looper.getMainLooper())
    internal val exoPlayer: ExoPlayer = ExoPlayer.Builder(AndroidPlatformHolder.requireContext()).build()

    private var _isPlaying by mutableStateOf(false)
    private var _isBuffering by mutableStateOf(false)
    private var _isEnded by mutableStateOf(false)
    private var _hasRenderedFirstFrame by mutableStateOf(false)
    private var _isPosterReady by mutableStateOf(false)
    private var _isLayerReady by mutableStateOf(true)
    private var _currentPositionMs by mutableLongStateOf(0L)
    private var _durationMs by mutableLongStateOf(0L)
    private var _bufferedPositionMs by mutableLongStateOf(0L)
    private var _videoWidth by mutableIntStateOf(0)
    private var _videoHeight by mutableIntStateOf(0)
    private var _errorMessage by mutableStateOf<String?>(null)
    private var _isSystemPipActive by mutableStateOf(false)
    internal var lastErrorCode by mutableIntStateOf(0)
        private set

    private var pipListener: ((Boolean) -> Unit)? = null
    private var released = false

    private val ticker = object : Runnable {
        override fun run() {
            if (released) return
            syncPositions()
            mainHandler.postDelayed(this, 200L)
        }
    }

    private val pipStateObserver: (Boolean) -> Unit = { active ->
        _isSystemPipActive = active
        pipListener?.invoke(active)
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying = isPlaying
            syncPositions()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _isBuffering = playbackState == Player.STATE_BUFFERING
            _isEnded = playbackState == Player.STATE_ENDED
            syncPositions()
        }

        override fun onRenderedFirstFrame() {
            _hasRenderedFirstFrame = true
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.width > 0 && videoSize.height > 0) {
                _videoWidth = videoSize.width
                _videoHeight = videoSize.height
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            lastErrorCode = error.errorCode
            _errorMessage = error.localizedMessage ?: "播放失败（${error.errorCodeName}）"
            _isBuffering = false
            _isPlaying = false
        }
    }

    init {
        exoPlayer.addListener(playerListener)
        AndroidPlatformHolder.addPipListener(pipStateObserver)
        mainHandler.post(ticker)
    }

    private fun syncPositions() {
        runCatching {
            val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
            val dur = exoPlayer.duration.let { if (it > 0L) it else 0L }
            val buf = exoPlayer.bufferedPosition.coerceAtLeast(0L)
            _currentPositionMs = pos
            _durationMs = dur
            _bufferedPositionMs = buf
        }
    }

    actual val isPlaying: Boolean get() = _isPlaying
    actual val isBuffering: Boolean get() = _isBuffering
    actual val isEnded: Boolean get() = _isEnded
    actual val hasRenderedFirstFrame: Boolean get() = _hasRenderedFirstFrame
    actual val isPosterReady: Boolean get() = _isPosterReady
    actual val isLayerReady: Boolean get() = _isLayerReady
    actual val currentPositionMs: Long get() = _currentPositionMs
    actual val durationMs: Long get() = _durationMs
    actual val bufferedPositionMs: Long get() = _bufferedPositionMs
    actual val videoWidth: Int get() = _videoWidth
    actual val videoHeight: Int get() = _videoHeight
    actual val errorMessage: String? get() = _errorMessage
    actual val isSystemPipActive: Boolean get() = _isSystemPipActive

    actual fun setStillImage(image: Image?) {
        _isPosterReady = image != null
    }

    actual fun configureSystemMedia(title: String, isLive: Boolean, enabled: Boolean) {
        // Android system PiP is controlled at Activity level via MainActivity.
    }

    actual fun setSystemPipStateListener(listener: ((Boolean) -> Unit)?) {
        pipListener = listener
    }

    actual fun startSystemPictureInPicture(): Boolean {
        val ratio = if (_videoWidth > 0 && _videoHeight > 0) {
            _videoWidth.toFloat() / _videoHeight.toFloat()
        } else {
            16f / 9f
        }
        return AndroidPlatformHolder.onRequestSystemPip?.invoke(ratio) ?: false
    }

    actual fun stopSystemPictureInPicture() {
        // Android exits PiP via system UI or Activity transition.
    }

    actual fun setMedia(
        url: String,
        headers: Map<String, String>,
        looping: Boolean,
        muted: Boolean,
        autoPlay: Boolean,
    ) {
        if (released) return
        _errorMessage = null
        lastErrorCode = 0
        _hasRenderedFirstFrame = false
        _isEnded = false
        _isBuffering = true
        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(headers)
        val dsFactory = DefaultDataSource.Factory(AndroidPlatformHolder.requireContext(), httpFactory)
        val mediaSource = DefaultMediaSourceFactory(dsFactory).createMediaSource(MediaItem.fromUri(url))
        exoPlayer.repeatMode = if (looping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        exoPlayer.volume = if (muted) 0f else 1f
        exoPlayer.setMediaSource(mediaSource)
        exoPlayer.playWhenReady = autoPlay
        exoPlayer.prepare()
    }

    actual fun setLocalMedia(
        filePath: String,
        looping: Boolean,
        muted: Boolean,
        autoPlay: Boolean,
    ) {
        if (released) return
        _errorMessage = null
        lastErrorCode = 0
        _hasRenderedFirstFrame = false
        _isEnded = false
        _isBuffering = true
        val uri = if (filePath.startsWith("file://") || filePath.startsWith("content://")) {
            Uri.parse(filePath)
        } else {
            Uri.fromFile(File(filePath))
        }
        exoPlayer.repeatMode = if (looping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        exoPlayer.volume = if (muted) 0f else 1f
        exoPlayer.setMediaItem(MediaItem.fromUri(uri))
        exoPlayer.playWhenReady = autoPlay
        exoPlayer.prepare()
    }

    actual fun play() {
        if (released) return
        if (exoPlayer.playbackState == Player.STATE_ENDED) {
            exoPlayer.seekTo(0L)
        }
        exoPlayer.playWhenReady = true
        exoPlayer.play()
    }

    actual fun pause() {
        if (released) return
        exoPlayer.playWhenReady = false
        exoPlayer.pause()
    }

    actual fun seekTo(positionMs: Long) {
        if (released) return
        exoPlayer.seekTo(positionMs.coerceAtLeast(0L))
        syncPositions()
    }

    actual fun setSpeed(speed: Float) {
        if (released) return
        exoPlayer.playbackParameters = PlaybackParameters(speed.coerceIn(0.25f, 4f))
    }

    actual fun setMuted(muted: Boolean) {
        if (released) return
        exoPlayer.volume = if (muted) 0f else 1f
    }

    actual fun setVolume(volume: Float) {
        if (released) return
        exoPlayer.volume = volume.coerceIn(0f, 1f)
    }

    actual fun setLooping(looping: Boolean) {
        if (released) return
        exoPlayer.repeatMode = if (looping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    actual fun stop() {
        if (released) return
        exoPlayer.stop()
        _isPlaying = false
        _isBuffering = false
    }

    actual fun release() {
        if (released) return
        released = true
        mainHandler.removeCallbacks(ticker)
        AndroidPlatformHolder.removePipListener(pipStateObserver)
        exoPlayer.removeListener(playerListener)
        exoPlayer.release()
    }
}

@Composable
actual fun PlatformVideoSurface(
    player: PlatformVideoPlayer,
    fillCrop: Boolean,
    modifier: Modifier,
    transparentBackground: Boolean,
    alpha: Float,
) {
    DisposableEffect(player) {
        onDispose {}
    }
    AndroidView(
        factory = { ctx ->
            (LayoutInflater.from(ctx).inflate(R.layout.live_player_view, null) as PlayerView).apply {
                useController = false
                resizeMode = if (fillCrop) {
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                } else {
                    AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
                setShutterBackgroundColor(
                    if (transparentBackground) android.graphics.Color.TRANSPARENT else android.graphics.Color.BLACK,
                )
                this.alpha = alpha
                this.player = player.exoPlayer
            }
        },
        modifier = modifier,
        update = { view ->
            view.resizeMode = if (fillCrop) {
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            } else {
                AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
            view.setShutterBackgroundColor(
                if (transparentBackground) android.graphics.Color.TRANSPARENT else android.graphics.Color.BLACK,
            )
            view.alpha = alpha
            if (view.player !== player.exoPlayer) {
                view.player = player.exoPlayer
            }
        },
        onRelease = { view ->
            view.player = null
        },
    )
}
