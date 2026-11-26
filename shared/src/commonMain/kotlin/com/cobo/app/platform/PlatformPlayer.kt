package com.cobo.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import coil3.Image

@Stable
expect class PlatformVideoPlayer() {
    val isPlaying: Boolean
    val isBuffering: Boolean
    val isEnded: Boolean
    val hasRenderedFirstFrame: Boolean
    val isPosterReady: Boolean
    val isLayerReady: Boolean
    val currentPositionMs: Long
    val durationMs: Long
    val bufferedPositionMs: Long
    val videoWidth: Int
    val videoHeight: Int
    val errorMessage: String?
    val isSystemPipActive: Boolean

    fun setStillImage(image: Image?)

    fun configureSystemMedia(
        title: String,
        isLive: Boolean,
        enabled: Boolean = true,
    )

    fun setSystemPipStateListener(listener: ((Boolean) -> Unit)?)
    fun startSystemPictureInPicture(): Boolean
    fun stopSystemPictureInPicture()

    fun setMedia(
        url: String,
        headers: Map<String, String> = emptyMap(),
        looping: Boolean = false,
        muted: Boolean = false,
        autoPlay: Boolean = true,
    )

    fun setLocalMedia(
        filePath: String,
        looping: Boolean = true,
        muted: Boolean = false,
        autoPlay: Boolean = true,
    )

    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setSpeed(speed: Float)
    fun setMuted(muted: Boolean)
    fun setVolume(volume: Float)
    fun setLooping(looping: Boolean)
    fun stop()
    fun release()
}

@Composable
expect fun PlatformVideoSurface(
    player: PlatformVideoPlayer,
    fillCrop: Boolean = false,
    modifier: Modifier = Modifier,
    transparentBackground: Boolean = false,
    alpha: Float = 1f,
)
