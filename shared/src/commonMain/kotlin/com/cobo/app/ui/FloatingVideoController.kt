package com.cobo.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
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
import com.cobo.app.App
import com.cobo.app.platform.PlatformVideoPlayer
import com.cobo.app.platform.PlatformVideoSurface
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
    VideoCenterGestureDirection.None -> ""
    VideoCenterGestureDirection.Up -> when (armedAction) {
        VideoCenterGestureAction.InAppFloating -> "松手进入小窗播放"
        VideoCenterGestureAction.SystemPip -> "松手进入画中画"
        else -> "上滑进入小窗"
    }
    VideoCenterGestureDirection.Down -> when (armedAction) {
        VideoCenterGestureAction.Close -> "松手关闭视频"
        else -> "下滑关闭视频"
    }
}

internal fun shouldAutoCloseVideoOnComplete(
    isLive: Boolean,
    autoCloseSetting: Boolean,
    hasPlayed: Boolean,
): Boolean = !isLive && autoCloseSetting && hasPlayed

fun clampPipAspectRatio(rawRatio: Float): Float {
    if (!rawRatio.isFinite() || rawRatio <= 0f) return 16f / 9f
    return rawRatio.coerceIn(MinPipAspectRatio, MaxPipAspectRatio)
}

internal fun shouldAutoFloatVideoOnBack(
    settingEnabled: Boolean,
    isPlaying: Boolean,
    hasError: Boolean,
): Boolean = settingEnabled && isPlaying && !hasError

fun shouldAutoEnterSystemPip(
    settingEnabled: Boolean,
    hasActiveVideo: Boolean,
    isPlaying: Boolean,
    isLive: Boolean = false,
): Boolean = settingEnabled && hasActiveVideo && isPlaying && !isLive

internal fun computeFloatingMiniSizeDp(aspectRatio: Float): Pair<Float, Float> {
    val safe = aspectRatio.takeIf { it.isFinite() && it > 0.2f } ?: (16f / 9f)
    return if (safe >= 1f) {
        val w = 186f
        val h = (w / safe).coerceIn(98f, 142f)
        w to h
    } else {
        val w = 124f
        val h = (w / safe).coerceIn(148f, 210f)
        w to h
    }
}

internal fun snapFloatingWindowX(
    currentX: Float,
    minX: Float,
    maxX: Float,
): Float = if (currentX <= (minX + maxX) / 2f) minX else maxX

data class FloatingVideoRequest(
    val rawUrl: String,
    val streamUrl: String,
    val fallbackStreamUrl: String = "",
    val referer: String = "",
    val title: String = "",
    val isLive: Boolean = false,
    val fid: String = "",
    val statusId: String = "",
    val playbackSpeed: Float = 1f,
)

@Stable
class FloatingVideoSession(
    val request: FloatingVideoRequest,
    val player: PlatformVideoPlayer,
    initialAspectRatio: Float = 16f / 9f,
) {
    var aspectRatio by mutableFloatStateOf(clampPipAspectRatio(initialAspectRatio))
    var isPlaying by mutableStateOf(player.isPlaying)
    var isBuffering by mutableStateOf(player.isBuffering)
}

@Stable
class FloatingVideoController {
    var activeSession by mutableStateOf<FloatingVideoSession?>(null)
        private set

    var fullscreenSession by mutableStateOf<FloatingVideoSession?>(null)

    var systemPipActive by mutableStateOf(false)

    var requestSystemPipCallback: ((Float) -> Boolean)? = null

    fun activePipSession(): FloatingVideoSession? = fullscreenSession ?: activeSession

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

    internal fun startFloating(session: FloatingVideoSession) {
        val prev = activeSession
        if (prev != null && prev.player !== session.player) {
            prev.player.release()
        }
        session.isPlaying = session.player.isPlaying
        session.isBuffering = session.player.isBuffering
        activeSession = session
    }

    internal fun peekMatching(rawUrl: String): FloatingVideoSession? {
        val current = activeSession ?: return null
        return current.takeIf { it.request.rawUrl == rawUrl }
    }

    internal fun takeMatching(rawUrl: String): FloatingVideoSession? {
        val current = activeSession ?: return null
        if (current.request.rawUrl != rawUrl) return null
        activeSession = null
        return current
    }

    fun dismiss() {
        activeSession?.player?.release()
        activeSession = null
    }

    fun requestSystemPip(aspectRatio: Float): Boolean =
        requestSystemPipCallback?.invoke(clampPipAspectRatio(aspectRatio)) == true
}

val LocalFloatingVideoController = staticCompositionLocalOf { FloatingVideoController() }

@Composable
fun FloatingVideoOverlay(
    controller: FloatingVideoController,
    nav: NavState,
    modifier: Modifier = Modifier,
) {
    val session = controller.activeSession ?: return
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val player = session.player
    LaunchedEffect(player, session.request.title, session.request.isLive) {
        player.configureSystemMedia(
            title = session.request.title,
            isLive = session.request.isLive,
            enabled = true,
        )
    }
    androidx.compose.runtime.DisposableEffect(player, controller) {
        val pipCallback: (Float) -> Boolean = { player.startSystemPictureInPicture() }
        if (controller.requestSystemPipCallback == null) {
            controller.requestSystemPipCallback = pipCallback
        }
        player.setSystemPipStateListener { active ->
            controller.systemPipActive = active
        }
        onDispose {
            if (controller.requestSystemPipCallback === pipCallback) {
                controller.requestSystemPipCallback = null
            }
            if (!player.isSystemPipActive) {
                controller.systemPipActive = false
            }
            player.setSystemPipStateListener(null)
        }
    }
    LaunchedEffect(player.isPlaying, player.isBuffering, player.isEnded, player.videoWidth, player.videoHeight) {
        session.isPlaying = player.isPlaying
        session.isBuffering = player.isBuffering
        if (player.videoWidth > 0 && player.videoHeight > 0) {
            session.aspectRatio = clampPipAspectRatio(player.videoWidth.toFloat() / player.videoHeight.toFloat())
        }
        if (shouldAutoCloseVideoOnComplete(session.request.isLive, App.settings.videoAutoCloseOnComplete, player.hasRenderedFirstFrame) && player.isEnded) {
            controller.dismiss()
        }
    }

    val (widthDp, heightDp) = remember(session.aspectRatio) { computeFloatingMiniSizeDp(session.aspectRatio) }
    if (controller.systemPipActive) {
        Box(modifier.fillMaxSize().background(Color.Black)) {
            PlatformVideoSurface(
                player = player,
                fillCrop = false,
                modifier = Modifier.fillMaxSize(),
            )
        }
        return
    }
    var controlsVisible by remember(session) { mutableStateOf(true) }
    LaunchedEffect(controlsVisible, session.isPlaying) {
        if (controlsVisible && session.isPlaying) {
            delay(FloatingMiniAutoHideMillis)
            controlsVisible = false
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val statusTopPx = with(density) { WindowInsets.statusBars.asPaddingValues().calculateTopPadding().toPx() }
        val navBottomPx = with(density) { WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().toPx() }
        val marginPx = with(density) { 12.dp.toPx() }
        val widthPx = with(density) { widthDp.dp.toPx() }
        val heightPx = with(density) { heightDp.dp.toPx() }
        val minX = marginPx
        val maxX = (constraints.maxWidth - widthPx - marginPx).coerceAtLeast(minX)
        val minY = statusTopPx + marginPx
        val maxY = (constraints.maxHeight - heightPx - navBottomPx - marginPx).coerceAtLeast(minY)

        val offsetX = remember(session) { Animatable(maxX) }
        val offsetY = remember(session) { Animatable((maxY - with(density) { 96.dp.toPx() }).coerceIn(minY, maxY)) }

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
                .size(widthDp.dp, heightDp.dp)
                .shadow(12.dp, SquircleShape(14.dp))
                .clip(SquircleShape(14.dp))
                .background(Color.Black)
                .border(1.dp, Color.White.copy(alpha = 0.2f), SquircleShape(14.dp))
                .pointerInput(session, minX, maxX, minY, maxY) {
                    detectDragGestures(
                        onDragEnd = {
                            val targetX = snapFloatingWindowX(offsetX.value, minX, maxX)
                            scope.launch {
                                offsetX.animateTo(targetX, spring(stiffness = Spring.StiffnessMediumLow))
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                offsetX.snapTo((offsetX.value + dragAmount.x).coerceIn(minX, maxX))
                                offsetY.snapTo((offsetY.value + dragAmount.y).coerceIn(minY, maxY))
                            }
                        },
                    )
                }
                .pointerInput(session) {
                    detectTapGestures(
                        onTap = { controlsVisible = !controlsVisible },
                        onDoubleTap = {
                            nav.push(
                                Route.Video(
                                    url = session.request.rawUrl,
                                    title = session.request.title,
                                    isLive = session.request.isLive,
                                    fid = session.request.fid,
                                    statusId = session.request.statusId,
                                ),
                            )
                        },
                    )
                },
        ) {
            PlatformVideoSurface(
                player = player,
                fillCrop = false,
                modifier = Modifier.fillMaxSize(),
            )
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(150)),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.28f))
                        .padding(6.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HighlightedIconButton(
                            onClick = { controller.dismiss() },
                            size = 28.dp,
                        ) {
                            Icon(COUIIcons.Close, "关闭小窗", Modifier.size(14.dp))
                        }
                        HighlightedIconButton(
                            onClick = {
                                if (!controller.requestSystemPip(session.aspectRatio)) {
                                    App.toast("当前设备不支持画中画")
                                }
                            },
                            size = 28.dp,
                        ) {
                            Icon(CoboIcons.PictureInPicture, "画中画", Modifier.size(14.dp))
                        }
                    }
                    if (!session.request.isLive) {
                        HighlightedIconButton(
                            onClick = {
                                if (player.isPlaying) {
                                    player.pause()
                                } else {
                                    if (player.isEnded) player.seekTo(0L)
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
                        onClick = {
                            nav.push(
                                Route.Video(
                                    url = session.request.rawUrl,
                                    title = session.request.title,
                                    isLive = session.request.isLive,
                                    fid = session.request.fid,
                                    statusId = session.request.statusId,
                                ),
                            )
                        },
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
