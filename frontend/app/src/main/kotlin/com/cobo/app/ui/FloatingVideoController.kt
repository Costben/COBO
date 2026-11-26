package com.cobo.app.ui

import android.view.LayoutInflater
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.cobo.app.App
import com.cobo.app.R
import com.cobo.app.screen.ResolvedVideo
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Close
import io.github.suqi8.coui.kmp.icon.extended.Pause
import io.github.suqi8.coui.kmp.icon.extended.Play
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Android 系统画中画允许的安全宽高比上下限，避开极端比值抛 IllegalArgumentException。 */
internal const val MinPipAspectRatio = 0.42f
internal const val MaxPipAspectRatio = 2.38f

private const val FloatingMiniAutoHideMillis = 3_000L

internal const val VideoGestureLeftZoneFraction = 0.30f
internal const val VideoGestureRightZoneFraction = 0.70f
internal const val VideoCenterGestureAutoDismissMillis = 500L

internal enum class VideoVerticalZone { Brightness, CenterActions, Volume }

internal enum class VideoCenterGestureDirection { None, Up, Down }

internal enum class VideoCenterGestureAction { None, InAppFloating, SystemPip, Close }

internal data class VideoCenterGestureSnapshot(
    val direction: VideoCenterGestureDirection = VideoCenterGestureDirection.None,
    val inAppReveal: Float = 0f,
    val pipReveal: Float = 0f,
    val closeReveal: Float = 0f,
    val armedAction: VideoCenterGestureAction = VideoCenterGestureAction.None,
)

/**
 * 划分全屏视频纵向滑动区域：开启中区手势时按横向 30% / 40% / 30% 分为亮度、中区快捷动作与音量，
 * 关闭后恢复左半调亮度、右半调音量。
 */
internal fun classifyVideoVerticalZone(
    xPx: Float,
    containerWidthPx: Float,
    centerSwipeEnabled: Boolean = true,
): VideoVerticalZone {
    if (!centerSwipeEnabled) {
        return if (containerWidthPx <= 0f || xPx < containerWidthPx / 2f) {
            VideoVerticalZone.Brightness
        } else {
            VideoVerticalZone.Volume
        }
    }
    if (containerWidthPx <= 0f) return VideoVerticalZone.CenterActions
    val ratio = xPx / containerWidthPx
    return when {
        ratio < VideoGestureLeftZoneFraction -> VideoVerticalZone.Brightness
        ratio > VideoGestureRightZoneFraction -> VideoVerticalZone.Volume
        else -> VideoVerticalZone.CenterActions
    }
}

/** 根据中区纵向位移计算按钮依次浮现进度与当前命中动作：上滑依次浮现应用内小窗、系统画中画，下滑浮现关闭。 */
internal fun evaluateVideoCenterGesture(draggedYPx: Float, density: Float): VideoCenterGestureSnapshot {
    val safeDensity = density.takeIf { it.isFinite() && it > 0f } ?: 1f
    val draggedDp = draggedYPx / safeDensity
    val deadzoneDp = 18f
    return when {
        draggedDp < -deadzoneDp -> {
            val upDp = -draggedDp
            val inAppReveal = ((upDp - 18f) / 62f).coerceIn(0f, 1f)
            val pipReveal = ((upDp - 80f) / 72f).coerceIn(0f, 1f)
            val armed = when {
                upDp >= 188f -> VideoCenterGestureAction.SystemPip
                upDp >= 96f -> VideoCenterGestureAction.InAppFloating
                else -> VideoCenterGestureAction.None
            }
            VideoCenterGestureSnapshot(
                direction = VideoCenterGestureDirection.Up,
                inAppReveal = inAppReveal,
                pipReveal = pipReveal,
                closeReveal = 0f,
                armedAction = armed,
            )
        }
        draggedDp > deadzoneDp -> {
            val downDp = draggedDp
            val closeReveal = ((downDp - 18f) / 68f).coerceIn(0f, 1f)
            val armed = if (downDp >= 108f) VideoCenterGestureAction.Close else VideoCenterGestureAction.None
            VideoCenterGestureSnapshot(
                direction = VideoCenterGestureDirection.Down,
                inAppReveal = 0f,
                pipReveal = 0f,
                closeReveal = closeReveal,
                armedAction = armed,
            )
        }
        else -> VideoCenterGestureSnapshot()
    }
}

internal fun videoCenterGestureHintText(
    direction: VideoCenterGestureDirection,
    armedAction: VideoCenterGestureAction,
): String = when (direction) {
    VideoCenterGestureDirection.Up -> when (armedAction) {
        VideoCenterGestureAction.InAppFloating -> "松手进入应用内小窗"
        VideoCenterGestureAction.SystemPip -> "松手进入系统画中画"
        else -> "继续上滑选择小窗或画中画"
    }
    VideoCenterGestureDirection.Down -> when (armedAction) {
        VideoCenterGestureAction.Close -> "松手关闭视频"
        else -> "继续下滑关闭视频"
    }
    VideoCenterGestureDirection.None -> ""
}

internal fun shouldAutoCloseVideoOnComplete(
    autoCloseEnabled: Boolean,
    playbackState: Int,
    hasStartedPlaying: Boolean,
    isLive: Boolean,
    isScrubbing: Boolean,
    handedOffToFloating: Boolean,
): Boolean = autoCloseEnabled &&
    hasStartedPlaying &&
    playbackState == Player.STATE_ENDED &&
    !isLive &&
    !isScrubbing &&
    !handedOffToFloating

internal fun clampPipAspectRatio(aspectRatio: Float): Float {
    if (!aspectRatio.isFinite() || aspectRatio <= 0f) return 16f / 9f
    return aspectRatio.coerceIn(MinPipAspectRatio, MaxPipAspectRatio)
}

internal fun shouldAutoFloatVideoOnBack(
    autoFloatEnabled: Boolean,
    isPlaying: Boolean,
    isLive: Boolean,
): Boolean = autoFloatEnabled && (isPlaying || isLive)

internal fun shouldAutoEnterSystemPip(
    autoFloatEnabled: Boolean,
    hasActiveVideo: Boolean,
    isPlaying: Boolean,
    isLive: Boolean,
): Boolean = autoFloatEnabled && hasActiveVideo && (isPlaying || isLive)

internal fun computeFloatingMiniSizeDp(aspectRatio: Float): Pair<Float, Float> {
    val safe = if (aspectRatio.isFinite() && aspectRatio > 0f) aspectRatio else 16f / 9f
    return if (safe >= 1.1f) {
        val width = 204f
        val height = (width / safe).coerceIn(112f, 148f)
        width to height
    } else {
        val width = 124f
        val height = (width / safe).coerceIn(156f, 210f)
        width to height
    }
}

internal fun snapFloatingWindowX(
    currentX: Float,
    windowWidthPx: Float,
    containerWidthPx: Float,
    marginPx: Float,
): Float {
    val minX = marginPx
    val maxX = (containerWidthPx - windowWidthPx - marginPx).coerceAtLeast(minX)
    val mid = (minX + maxX) / 2f
    return if (currentX <= mid) minX else maxX
}

internal data class FloatingVideoRequest(
    val rawUrl: String,
    val streamUrl: String,
    val fallbackStreamUrl: String?,
    val referer: String?,
    val title: String,
    val isLive: Boolean,
    val fid: String = "",
    val statusId: String = "",
    val playbackSpeed: Float = 1f,
)

@Stable
internal class FloatingVideoSession(
    val request: FloatingVideoRequest,
    val player: ExoPlayer,
    val onRefreshStream: (suspend () -> ResolvedVideo.DirectStream?)? = null,
    initialAspectRatio: Float = 16f / 9f,
) {
    var aspectRatio by mutableFloatStateOf(
        if (initialAspectRatio.isFinite() && initialAspectRatio > 0f) initialAspectRatio else 16f / 9f,
    )
    var isPlaying by mutableStateOf(player.isPlaying)
    var isBuffering by mutableStateOf(player.playbackState == Player.STATE_BUFFERING)
}

/**
 * 托管应用内视频悬浮小窗与系统画中画（PiP）共享播放器生命周期，实现全屏/小窗/画中画无缝接续播放。
 */
@Stable
class FloatingVideoController {
    internal var activeSession by mutableStateOf<FloatingVideoSession?>(null)
        private set

    internal var fullscreenSession by mutableStateOf<FloatingVideoSession?>(null)

    var systemPipActive by mutableStateOf(false)
        internal set

    internal var requestSystemPipCallback: ((Float) -> Boolean)? = null

    internal fun startFloating(session: FloatingVideoSession) {
        val prev = activeSession
        if (prev != null && prev.player !== session.player) {
            prev.player.release()
        }
        session.isPlaying = session.player.isPlaying
        session.isBuffering = session.player.playbackState == Player.STATE_BUFFERING
        activeSession = session
    }

    internal fun peekMatching(rawUrl: String): FloatingVideoSession? {
        val current = activeSession ?: return null
        return current.takeIf { it.request.rawUrl == rawUrl }
    }

    internal fun claimForFullscreen(rawUrl: String, streamUrl: String): FloatingVideoSession? {
        val current = activeSession ?: return null
        if (current.request.rawUrl != rawUrl && current.request.streamUrl != streamUrl) {
            return null
        }
        activeSession = null
        return current
    }

    fun dismissFloating() {
        val current = activeSession ?: return
        activeSession = null
        current.player.release()
    }

    internal fun activePipSession(): FloatingVideoSession? = fullscreenSession ?: activeSession

    fun requestSystemPip(aspectRatio: Float): Boolean =
        requestSystemPipCallback?.invoke(clampPipAspectRatio(aspectRatio)) == true

    fun pauseActiveIfNotPip() {
        if (systemPipActive) return
        activeSession?.player?.let { if (it.isPlaying) it.pause() }
        fullscreenSession?.player?.let { if (it.isPlaying) it.pause() }
    }

    fun releaseAll() {
        activeSession?.player?.release()
        activeSession = null
        fullscreenSession = null
    }
}

val LocalFloatingVideoController = staticCompositionLocalOf<FloatingVideoController> {
    error("FloatingVideoController not provided")
}

/**
 * 挂载在根节点路由层之上的视频悬浮窗：
 * - 系统画中画模式下全窗口纯画面渲染；
 * - 普通应用内小窗模式下支持自由拖动、松手贴边吸附、播控切换、转系统画中画与还原全屏。
 */
@Composable
fun FloatingVideoOverlay(
    controller: FloatingVideoController,
    nav: NavState,
    modifier: Modifier = Modifier,
) {
    val session = controller.activeSession ?: return
    val systemPip = controller.systemPipActive
    val scope = rememberCoroutineScope()

    DisposableEffect(session.player) {
        val player = session.player
        var hasPlayed = player.isPlaying || player.playbackState == Player.STATE_READY
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                session.isPlaying = isPlaying
                if (isPlaying) hasPlayed = true
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                session.isPlaying = player.isPlaying
                session.isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    hasPlayed = true
                }
                if (
                    shouldAutoCloseVideoOnComplete(
                        autoCloseEnabled = App.settings.videoAutoCloseOnComplete,
                        playbackState = playbackState,
                        hasStartedPlaying = hasPlayed,
                        isLive = session.request.isLive,
                        isScrubbing = false,
                        handedOffToFloating = false,
                    )
                ) {
                    controller.dismissFloating()
                }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    val pixelRatio = videoSize.pixelWidthHeightRatio.takeIf { it > 0f } ?: 1f
                    session.aspectRatio = (videoSize.width * pixelRatio) / videoSize.height.toFloat()
                }
            }
        }
        player.addListener(listener)
        session.isPlaying = player.isPlaying
        session.isBuffering = player.playbackState == Player.STATE_BUFFERING
        onDispose { player.removeListener(listener) }
    }

    if (systemPip) {
        Box(modifier.fillMaxSize().background(Color.Black)) {
            FloatingTexturePlayerView(
                player = session.player,
                modifier = Modifier.fillMaxSize(),
            )
        }
        return
    }

    val density = LocalDensity.current
    val (widthDpFloat, heightDpFloat) = computeFloatingMiniSizeDp(session.aspectRatio)
    val windowWidthDp = widthDpFloat.dp
    val windowHeightDp = heightDpFloat.dp

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val marginPx = with(density) { 12.dp.toPx() }
    val minYPx = with(density) { (statusTop + 12.dp).toPx() }
    val bottomReservedPx = with(density) { (navBottom + 88.dp).toPx() }
    val winWPx = with(density) { windowWidthDp.toPx() }
    val winHPx = with(density) { windowHeightDp.toPx() }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val containerWPx = constraints.maxWidth.toFloat()
        val containerHPx = constraints.maxHeight.toFloat()
        val maxXPx = (containerWPx - winWPx - marginPx).coerceAtLeast(marginPx)
        val maxYPx = (containerHPx - winHPx - bottomReservedPx).coerceAtLeast(minYPx)

        val offsetX = remember { Animatable(maxXPx) }
        val offsetY = remember { Animatable(maxYPx) }
        var positioned by remember { mutableStateOf(false) }

        LaunchedEffect(containerWPx, containerHPx, winWPx, winHPx) {
            if (!positioned) {
                offsetX.snapTo(maxXPx)
                offsetY.snapTo(maxYPx)
                positioned = true
            } else {
                val snappedX = snapFloatingWindowX(offsetX.value, winWPx, containerWPx, marginPx)
                val clampedY = offsetY.value.coerceIn(minYPx, maxYPx)
                offsetX.snapTo(snappedX)
                offsetY.snapTo(clampedY)
            }
        }

        var controlsShown by remember(session) { mutableStateOf(true) }
        LaunchedEffect(session, controlsShown, session.isPlaying) {
            if (controlsShown && session.isPlaying) {
                delay(FloatingMiniAutoHideMillis)
                controlsShown = false
            }
        }

        val shape = remember { SquircleShape(16.dp) }
        val openFullscreen = {
            nav.push(
                Route.Video(
                    url = session.request.rawUrl,
                    title = session.request.title,
                    isLive = session.request.isLive,
                    fid = session.request.fid,
                    statusId = session.request.statusId,
                ),
            )
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
                .size(windowWidthDp, windowHeightDp)
                .shadow(12.dp, shape)
                .clip(shape)
                .background(Color.Black)
                .border(1.dp, Color.White.copy(alpha = 0.18f), shape)
                .pointerInput(session) {
                    detectTapGestures(
                        onTap = { controlsShown = !controlsShown },
                        onDoubleTap = { openFullscreen() },
                    )
                }
                .pointerInput(containerWPx, containerHPx, winWPx, winHPx) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                offsetX.snapTo((offsetX.value + dragAmount.x).coerceIn(marginPx, maxXPx))
                                offsetY.snapTo((offsetY.value + dragAmount.y).coerceIn(minYPx, maxYPx))
                            }
                        },
                        onDragEnd = {
                            val targetX = snapFloatingWindowX(offsetX.value, winWPx, containerWPx, marginPx)
                            val targetY = offsetY.value.coerceIn(minYPx, maxYPx)
                            scope.launch {
                                launch {
                                    offsetX.animateTo(
                                        targetX,
                                        spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.85f),
                                    )
                                }
                                launch {
                                    offsetY.animateTo(
                                        targetY,
                                        spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.85f),
                                    )
                                }
                            }
                        },
                    )
                },
        ) {
            FloatingTexturePlayerView(
                player = session.player,
                modifier = Modifier.fillMaxSize(),
            )

            AnimatedVisibility(
                visible = controlsShown,
                enter = fadeIn(tween(260, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f))),
                exit = fadeOut(tween(460, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f))),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.28f))
                        .padding(6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HighlightedIconButton(
                            onClick = { controller.dismissFloating() },
                            size = 28.dp,
                        ) {
                            Icon(COUIIcons.Close, "关闭小窗", Modifier.size(14.dp))
                        }
                        HighlightedIconButton(
                            onClick = { controller.requestSystemPip(session.aspectRatio) },
                            size = 28.dp,
                        ) {
                            Icon(CoboIcons.PictureInPicture, "画中画", Modifier.size(14.dp))
                        }
                    }

                    if (!session.request.isLive) {
                        HighlightedIconButton(
                            onClick = {
                                val player = session.player
                                if (player.isPlaying) {
                                    player.pause()
                                } else {
                                    if (player.playbackState == Player.STATE_ENDED) player.seekTo(0L)
                                    player.play()
                                }
                                session.isPlaying = player.isPlaying
                            },
                            size = 36.dp,
                            modifier = Modifier.align(Alignment.Center),
                        ) {
                            Icon(
                                imageVector = if (session.isPlaying) COUIIcons.Pause else COUIIcons.Play,
                                contentDescription = if (session.isPlaying) "暂停" else "播放",
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    HighlightedIconButton(
                        onClick = openFullscreen,
                        size = 28.dp,
                        modifier = Modifier.align(Alignment.BottomEnd),
                    ) {
                        Icon(CoboIcons.ExpandFullscreen, "展开全屏", Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingTexturePlayerView(
    player: ExoPlayer,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { ctx ->
            (LayoutInflater.from(ctx).inflate(R.layout.live_player_view, null) as PlayerView).apply {
                useController = false
                controllerAutoShow = false
                setControllerShowTimeoutMs(0)
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                hideController()
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                this.player = player
            }
        },
        update = { view ->
            if (view.player !== player) {
                view.player = player
            }
        },
        onRelease = { view ->
            view.player = null
        },
        modifier = modifier,
    )
}
