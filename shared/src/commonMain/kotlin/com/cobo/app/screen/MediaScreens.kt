package com.cobo.app.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as colorLerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.lerp
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.compose.asPainter
import coil3.decode.DataSource
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import com.cobo.app.App
import com.cobo.app.data.Api
import com.cobo.app.data.ImageBrowseQuality
import com.cobo.app.data.ImageTopBarButton
import com.cobo.app.data.Pic
import com.cobo.app.data.VideoTopBarButton
import com.cobo.app.generated.resources.Res
import com.cobo.app.generated.resources.ic_live_photo
import com.cobo.app.platform.PickedMediaRegistry
import com.cobo.app.platform.PlatformBackHandler
import com.cobo.app.platform.PlatformFile
import com.cobo.app.platform.PlatformOrientation
import com.cobo.app.platform.PlatformUri
import com.cobo.app.platform.PlatformVideoPlayer
import com.cobo.app.platform.PlatformVideoSurface
import com.cobo.app.platform.keepScreenOn
import com.cobo.app.platform.openExternalUrl
import com.cobo.app.platform.readScreenBrightness
import com.cobo.app.platform.readSystemVolume
import com.cobo.app.platform.rememberPhotoPicker
import com.cobo.app.platform.setScreenOrientation
import com.cobo.app.platform.setStatusBarDarkIcons
import com.cobo.app.platform.setSystemBarsVisible
import com.cobo.app.platform.shareImageBytes
import com.cobo.app.platform.writeScreenBrightness
import com.cobo.app.platform.writeSystemVolume
import com.cobo.app.ui.CoboIcons
import com.cobo.app.ui.ExpandEasing
import com.cobo.app.ui.FloatingVideoRequest
import com.cobo.app.ui.FloatingVideoSession
import com.cobo.app.ui.ImageOrigins
import com.cobo.app.ui.LocalFloatingVideoController
import com.cobo.app.ui.LocalImageOrigins
import com.cobo.app.ui.LocalRouteTransition
import com.cobo.app.ui.LongImageAspect
import com.cobo.app.ui.NavState
import com.cobo.app.ui.Route
import com.cobo.app.ui.ThumbBounds
import com.cobo.app.ui.VideoCenterGestureAction
import com.cobo.app.ui.VideoCenterGestureDirection
import com.cobo.app.ui.VideoCenterGestureSnapshot
import com.cobo.app.ui.VideoVerticalZone
import com.cobo.app.ui.classifyVideoVerticalZone
import com.cobo.app.ui.components.AvatarCropDialog
import com.cobo.app.ui.components.CoboRefreshSpinner
import com.cobo.app.ui.components.SaveHud
import com.cobo.app.ui.components.SaveImageItem
import com.cobo.app.ui.components.SaveImagesMenu
import com.cobo.app.ui.components.WeiboWebView
import com.cobo.app.ui.components.downloadImage
import com.cobo.app.ui.components.downloadMediaToFile
import com.cobo.app.ui.components.liveMediaCacheFile
import com.cobo.app.ui.components.originalSize
import com.cobo.app.ui.components.rememberImageSaveState
import com.cobo.app.ui.evaluateVideoCenterGesture
import com.cobo.app.ui.isWeiboLiveLink
import com.cobo.app.ui.resolveWeiboLink
import com.cobo.app.ui.shouldAutoCloseVideoOnComplete
import com.cobo.app.ui.shouldAutoFloatVideoOnBack
import com.cobo.app.ui.unwrapWeiboLink
import com.cobo.app.ui.videoCenterGestureHintText
import io.github.suqi8.coui.kmp.basic.BottomFloatingToolbarDefaults
import io.github.suqi8.coui.kmp.basic.CapsuleOutlineShape
import io.github.suqi8.coui.kmp.basic.CircularProgressIndicator
import io.github.suqi8.coui.kmp.basic.DropdownEntry
import io.github.suqi8.coui.kmp.basic.DropdownImpl
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton
import io.github.suqi8.coui.kmp.basic.HighlightedIconButtonDefaults
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IntentSlider
import io.github.suqi8.coui.kmp.basic.IntentSliderDefaults
import io.github.suqi8.coui.kmp.basic.IntentSliderPlaybackState
import io.github.suqi8.coui.kmp.basic.IntentSliderSize
import io.github.suqi8.coui.kmp.basic.ListPopupColumn
import io.github.suqi8.coui.kmp.basic.ListPopupDefaults
import io.github.suqi8.coui.kmp.basic.MetaBallParticle
import io.github.suqi8.coui.kmp.basic.MetaBallSurface
import io.github.suqi8.coui.kmp.basic.SpotLightDefaults
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.couiTouchMotion
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor
import io.github.suqi8.coui.kmp.basic.rememberCOUITouchMotionState
import io.github.suqi8.coui.kmp.basic.rememberPreciseClickState
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Close
import io.github.suqi8.coui.kmp.icon.extended.Download
import io.github.suqi8.coui.kmp.icon.extended.Edit
import io.github.suqi8.coui.kmp.icon.extended.Lock
import io.github.suqi8.coui.kmp.icon.extended.Pause
import io.github.suqi8.coui.kmp.icon.extended.Play
import io.github.suqi8.coui.kmp.icon.extended.Share
import io.github.suqi8.coui.kmp.overlay.OverlayCascadingListPopup
import io.github.suqi8.coui.kmp.overlay.OverlayListPopup
import io.github.suqi8.coui.kmp.squircle.isSquircleEnabled
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.LocalContentColor
import io.github.suqi8.coui.kmp.theme.LocalDismissState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource as kmpPainterResource
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

internal fun shouldAutoHideImageViewerChrome(
    autoHideEnabled: Boolean,
    chromeVisible: Boolean,
    menuOpen: Boolean,
): Boolean = autoHideEnabled && chromeVisible && !menuOpen

internal fun shouldAutoHideVideoControls(
    autoHideEnabled: Boolean,
    controlsVisible: Boolean,
    isPlaying: Boolean,
    scrubbing: Boolean,
): Boolean = autoHideEnabled && controlsVisible && isPlaying && !scrubbing

internal fun formatVideoTime(ms: Long): String {
    val totalSec = (ms.coerceAtLeast(0L) / 1000L).toInt()
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    val mm = m.toString().padStart(2, '0')
    val ss = s.toString().padStart(2, '0')
    return if (h > 0) "$h:$mm:$ss" else "$mm:$ss"
}

internal fun calculateHorizontalScrubTargetMs(
    startMs: Long,
    dragDeltaPx: Float,
    containerWidthPx: Float,
    durationMs: Long,
): Long {
    if (durationMs <= 0L || containerWidthPx <= 0f) return startMs.coerceAtLeast(0L)
    val spanMs = minOf(durationMs.toFloat(), 90_000f)
    val deltaMs = (dragDeltaPx / containerWidthPx) * spanMs
    return (startMs + deltaMs.toLong()).coerceIn(0L, durationMs)
}

private val MediaControlHeight = 36.dp
private val ChromeScrim = Brush.verticalGradient(
    0f to Color.Black.copy(alpha = 0.55f),
    0.62f to Color.Black.copy(alpha = 0.32f),
    1f to Color.Transparent,
)
private val ScrimTail = 34.dp
private val VideoTopBarButtonHeight = MediaControlHeight
private val VideoBarEnterEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
private val VideoBarExitEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
private const val SeekStepMs = 5_000L
private val Speeds = listOf(1f, 1.25f, 1.5f, 2f, 3f)
private const val VideoCenterGestureAutoDismissMillis = 900L

private fun speedLabel(value: Float): String = when (value) {
    1f -> "1.0×"
    1.25f -> "1.25×"
    1.5f -> "1.5×"
    2f -> "2.0×"
    else -> "3.0×"
}

/** 与 [HighlightedIconButton] 完全统一材质的玻璃胶囊控件，用于看图页与视频页的胶囊按钮及提示。 */
@Composable
private fun MediaMaterialPill(
    modifier: Modifier = Modifier,
    height: Dp = MediaControlHeight,
    horizontalPadding: Dp = 14.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val scheme = COUITheme.colorScheme
    val isDark = scheme.background.luminance() < 0.5f
    val density = LocalDensity.current
    val squircleEnabled = isSquircleEnabled()
    val capsuleShape = remember(squircleEnabled) { CapsuleOutlineShape(squircleEnabled = squircleEnabled) }
    val touchMotion = rememberCOUITouchMotionState()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressProgress by animateFloatAsState(
        targetValue = if (onClick != null && pressed) 1f else 0f,
        animationSpec = HighlightedIconButtonDefaults.PressSpring,
        label = "mediaMaterialPillPress",
    )
    val scale = 1f - (1f - HighlightedIconButtonDefaults.PressedScale) * pressProgress
    val brighten = HighlightedIconButtonDefaults.PressedFillBrighten * pressProgress
    val fillColor = colorLerp(
        HighlightedIconButtonDefaults.fillCenter(isDark),
        Color.White,
        brighten,
    ).copy(alpha = HighlightedIconButtonDefaults.FillAlpha)
    val contentColor = colorLerp(
        scheme.onBackground,
        Color.White,
        HighlightedIconButtonDefaults.PressedContentBrighten * pressProgress,
    )
    val currentOnClick by rememberUpdatedState(onClick)
    var pillWidthPx by remember { mutableIntStateOf(0) }
    val pillWidth = with(density) { pillWidthPx.toDp() }
    val clickableModifier = remember(onClick != null, interactionSource) {
        if (onClick != null) {
            Modifier.clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = { currentOnClick?.invoke() },
            )
        } else {
            Modifier
        }
    }

    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Box(
            modifier = modifier
                .height(height)
                .onSizeChanged { pillWidthPx = it.width }
                .couiTouchMotion(
                    enabled = onClick != null,
                    motionState = touchMotion,
                    pressScaleEnabled = false,
                    shape = capsuleShape,
                    lightParams = SpotLightDefaults.circleParams1(isDark),
                    lightEnabled = onClick != null,
                    extraTransform = {
                        scaleX += scale - 1f
                        scaleY += scale - 1f
                    },
                )
                .then(clickableModifier),
            contentAlignment = Alignment.Center,
        ) {
            if (!isDark && pillWidthPx > 0) {
                Box(
                    modifier = Modifier
                        .size(pillWidth, height)
                        .dropShadow(shape = capsuleShape, shadow = BottomFloatingToolbarDefaults.Shadow),
                )
            }
            MetaBallSurface(
                particles = if (pillWidthPx > 0) {
                    listOf(
                        MetaBallParticle(
                            centerX = pillWidth / 2,
                            centerY = height / 2,
                            width = pillWidth,
                            height = height,
                            cornerRadius = height / 2,
                        ),
                    )
                } else {
                    emptyList()
                },
                modifier = Modifier.matchParentSize(),
                color = fillColor,
                backdrop = null,
                rimColor = Color.White.copy(
                    alpha = HighlightedIconButtonDefaults.stroke(isDark).edgeAlpha * (1f - pressProgress).coerceIn(0f, 1f),
                ),
                rimBottomScale = 1f,
                bottomGlowColor = if (isDark) {
                    HighlightedIconButtonDefaults.DarkHaloColor.copy(
                        alpha = HighlightedIconButtonDefaults.DarkHaloColor.alpha * (1f - pressProgress).coerceIn(0f, 1f),
                    )
                } else {
                    null
                },
                continuousCurve = squircleEnabled,
            )
            Box(
                modifier = Modifier
                    .height(height)
                    .padding(horizontal = horizontalPadding),
                contentAlignment = Alignment.Center,
                content = content,
            )
        }
    }
}

@Composable
private fun LivePill() {
    MediaMaterialPill {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = kmpPainterResource(Res.drawable.ic_live_photo),
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = LocalContentColor.current,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "实况",
                color = LocalContentColor.current,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun VideoSpeedPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MediaMaterialPill(
        modifier = modifier,
        height = VideoTopBarButtonHeight,
        onClick = onClick,
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = LocalContentColor.current,
            maxLines = 1,
        )
    }
}

private const val MinZoom = 0.6f
private const val MaxZoom = 5f
private const val DoubleTapZoom = 2.5f
private const val ZoomEpsilon = 0.01f
private const val ZoomMillis = 260
private const val ReboundResistance = 0.35f
private const val FlingMinVelocity = 120f
private const val ImageViewerChromeAutoHideDelayMillis = 3200L
private const val ChromeFadeStart = 0.6f
private const val LoadFadeMillis = 200
private const val DragMinScale = 0.7f
private val DragScaleEasing = CubicBezierEasing(0f, 0f, 0.4f, 1f)
private val ViewerChromeEnterEasing = CubicBezierEasing(0.18f, 0.9f, 0.2f, 1f)
private val ViewerChromeExitEasing = CubicBezierEasing(0.4f, 0f, 0.8f, 1f)
private const val LongImageScrollEpsilon = 8f
private const val LongImageScrollEnd = 0.72f
private const val LongImageShrinkStart = 0.18f
private const val LiveHoldMillis = 280L
private const val LiveFadeMillis = 600

internal expect suspend fun platformTopBandIsLight(
    url: String,
    screenWidthDp: Int,
    screenHeightDp: Int,
): Boolean

@Composable
internal expect fun PlatformTiledLongImageOverlay(
    url: String,
    original: Boolean,
    screenWidthPx: Int,
    box: IntSize,
    scale: Float,
    offset: Offset,
    rest: ComposeSize,
    onAspect: (Float) -> Unit,
)

@Composable
internal expect fun PlatformWebVideoPlayer(
    nav: NavState,
    pageUrl: String,
    title: String,
    onDownload: (String) -> Unit,
    onFlash: (String) -> Unit,
)

@Composable
fun ImageViewerScreen(
    nav: NavState,
    pics: List<Pic>,
    index: Int,
    editableAvatar: Boolean = false,
) {
    var avatarOverride by remember { mutableStateOf<String?>(null) }
    val effectivePics = remember(pics, avatarOverride) {
        val override = avatarOverride
        if (editableAvatar && !override.isNullOrBlank() && pics.isNotEmpty()) {
            listOf(pics[0].copy(thumb = override, large = override)) + pics.drop(1)
        } else {
            pics
        }
    }
    val urls = remember(effectivePics) { effectivePics.map { it.large } }
    val scope = rememberCoroutineScope()
    val transition = LocalRouteTransition.current
    val origins = LocalImageOrigins.current
    val density = LocalDensity.current.density
    val frame = remember { ViewerFrame() }
    remember(effectivePics) {
        effectivePics.forEach { pic ->
            if (pic.width > 0 && pic.height > 0 && pic.large !in frame.aspects) {
                frame.aspects[pic.large] = pic.width.toFloat() / pic.height
            }
        }
        true
    }
    val progress: () -> Float = remember(transition) { transition?.progress ?: { 1f } }
    val transitionOffset: () -> Offset = remember(transition) { transition?.offset ?: { Offset.Zero } }

    val saveState = rememberImageSaveState()
    val precise = rememberPreciseClickState()
    val start = index.coerceIn(0, (urls.size - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(initialPage = start) { urls.size }
    val current = urls.getOrNull(pagerState.currentPage).orEmpty()
    val currentPic = effectivePics.getOrNull(pagerState.currentPage)
    val viewerOwner = remember { Any() }

    var chromeVisible by remember { mutableStateOf(true) }
    var chromeInteractionTick by remember { mutableIntStateOf(0) }
    var original by remember { mutableStateOf(App.settings.imageBrowseQuality == ImageBrowseQuality.Original) }
    var menuOpen by remember { mutableStateOf(false) }
    var avatarMoreOpen by remember { mutableStateOf(false) }
    var liveMuted by remember { mutableStateOf(!App.settings.livePhotoSound) }
    var pendingCropUri by remember { mutableStateOf<PlatformUri?>(null) }
    var uploadingAvatar by remember { mutableStateOf(false) }

    val pickAvatar = rememberPhotoPicker(maxSelection = 1) { picked ->
        picked.firstOrNull()?.let { pendingCropUri = PickedMediaRegistry.register(it) }
    }

    val transitionExiting = transition?.exiting?.invoke() == true
    SideEffect {
        val host = frame.host
        val live = if (host != null && !frame.zoomed) {
            origins.boundsOf(current, host, density)?.also { frame.thumbs[current] = it }
        } else {
            null
        }
        val thumb = live ?: current.takeIf { !frame.zoomed }?.let { frame.thumbs[it] }
        val aspect = frame.aspects[current] ?: thumb?.aspect
        val w = frame.dismiss.boxSize.width.toFloat()
        val h = frame.dismiss.boxSize.height.toFloat()
        val effectiveAspect = aspect ?: thumb?.rect?.let { if (it.height > 0f) it.width / it.height else null }
        val rest = if (w > 0f && h > 0f && effectiveAspect != null) restSize(w, h, effectiveAspect) else null
        val hasThumb = thumb != null && rest != null
        transition?.onHasReturnTarget?.invoke(hasThumb)
        origins.beginViewing(viewerOwner, if (transitionExiting && !hasThumb) null else current)
    }
    DisposableEffect(viewerOwner) {
        setStatusBarDarkIcons(false)
        onDispose {
            origins.endViewing(viewerOwner)
            setStatusBarDarkIcons(null)
        }
    }
    LaunchedEffect(current, frame.dismiss.boxSize) {
        val scaleDensity = density.coerceAtLeast(1f)
        val wDp = (frame.dismiss.boxSize.width / scaleDensity).roundToInt().coerceAtLeast(1)
        val hDp = (frame.dismiss.boxSize.height / scaleDensity).roundToInt().coerceAtLeast(1)
        val lightTop = platformTopBandIsLight(current, wDp, hDp)
        setStatusBarDarkIcons(lightTop)
    }

    LaunchedEffect(
        chromeVisible,
        chromeInteractionTick,
        menuOpen,
        pendingCropUri != null,
        uploadingAvatar,
        saveState.saving,
        frame.dismiss.dragging,
        frame.dismiss.exiting,
    ) {
        if (
            shouldAutoHideImageViewerChrome(App.settings.autoHideFloatingBars, chromeVisible, menuOpen) &&
            pendingCropUri == null &&
            !uploadingAvatar &&
            !saveState.saving &&
            !frame.dismiss.dragging &&
            !frame.dismiss.exiting
        ) {
            delay(ImageViewerChromeAutoHideDelayMillis)
            chromeVisible = false
        }
    }

    PlatformBackHandler(enabled = menuOpen) {
        menuOpen = false
    }
    PlatformBackHandler(enabled = !menuOpen && pendingCropUri != null && !uploadingAvatar) {
        pendingCropUri = null
    }
    PlatformBackHandler(enabled = !menuOpen && pendingCropUri == null && frame.dismiss.exiting) { }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPlaced { frame.host = it }
            .onSizeChanged { frame.dismiss.boxSize = it },
    ) {
        // 透明底 + 随进场进度 / 拖拽距离动态变化的黑色幕布，让下层页面在拖动或缩回时自然透出。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val d = frame.dismiss
                    val bgAlpha = when {
                        d.exiting -> d.exitBgAlpha.value
                        d.active -> d.currentBgAlpha()
                        else -> progress()
                    }
                    if (bgAlpha > 0f) drawRect(Color.Black, alpha = bgAlpha)
                },
        )

        HorizontalPager(
            state = pagerState,
            pageSpacing = 16.dp,
            beyondViewportPageCount = 1,
            userScrollEnabled = urls.size > 1 && !frame.dismiss.active && !frame.dismiss.exiting,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            Box(
                Modifier
                    .fillMaxSize()
                    .preciseClickAnchor(precise),
            ) {
                val pagePic = effectivePics[page]
                ZoomableImage(
                    url = urls[page],
                    live = if (pagePic.isVideo) "" else pagePic.live,
                    videoUrl = if (pagePic.isVideo) pagePic.videoUrl else "",
                    thumbModel = origins.modelOf(urls[page]) ?: pagePic.thumb.takeIf { it.isNotBlank() },
                    thumbCacheKey = origins.cacheKeyOf(urls[page]),
                    original = original,
                    preserveUrl = pagePic.preserveUrl,
                    isCurrent = pagerState.currentPage == page,
                    isSettledElsewhere = pagerState.settledPage != page,
                    pageIndex = page,
                    pageCount = urls.size,
                    isPagerScrolling = { abs(pagerState.currentPageOffsetFraction) > 0.01f },
                    canStartDismiss = {
                        transition?.exiting?.invoke() != true &&
                            progress() >= 0.98f &&
                            transitionOffset() == Offset.Zero
                    },
                    progress = progress,
                    transitionOffset = transitionOffset,
                    origins = origins,
                    frame = frame,
                    liveMuted = liveMuted,
                    onTap = {
                        if (!frame.dismiss.exiting) {
                            if (pagePic.isVideo && pagePic.videoUrl.isNotBlank()) {
                                nav.push(
                                    Route.Video(
                                        url = pagePic.videoUrl,
                                        title = pagePic.videoTitle,
                                        isLive = isWeiboLiveLink(pagePic.videoUrl),
                                        fid = pagePic.videoFid,
                                        statusId = pagePic.videoStatusId,
                                    ),
                                )
                            } else {
                                chromeVisible = !chromeVisible
                            }
                        }
                    },
                    onLongPress = {
                        if (!pagePic.isVideo && !saveState.saving && !frame.dismiss.exiting && !frame.dismiss.active) {
                            menuOpen = true
                            chromeVisible = true
                        }
                    },
                    onDismissRelease = { velocity ->
                        handleDismissRelease(
                            velocity = velocity,
                            dismiss = frame.dismiss,
                            frame = frame,
                            origins = origins,
                            url = urls.getOrNull(pagerState.currentPage),
                            density = density,
                            scope = scope,
                            startImageOffset = frame.dismiss.pendingImageOffset,
                            onPopImmediate = { nav.popImmediate() },
                        )
                    },
                )
            }
        }

        val chromeAlphaState = animateFloatAsState(
            targetValue = if (chromeVisible) 1f else 0f,
            animationSpec = tween(
                durationMillis = if (chromeVisible) 280 else 420,
                easing = if (chromeVisible) ViewerChromeEnterEasing else ViewerChromeExitEasing,
            ),
            label = "imageViewerChromeAlpha",
        )
        val dismissChromeAlphaState = animateFloatAsState(
            targetValue = if (frame.dismiss.dragging || frame.dismiss.exiting) 0f else 1f,
            animationSpec = tween(140),
            label = "dismissChromeAlpha",
        )
        val chromeMounted by remember(chromeAlphaState) {
            derivedStateOf { chromeVisible || chromeAlphaState.value > 0.001f }
        }
        val chromeFade: () -> Float = {
            ((progress() - ChromeFadeStart) / (1f - ChromeFadeStart)).coerceIn(0f, 1f) *
                dismissChromeAlphaState.value *
                chromeAlphaState.value
        }

        if (chromeMounted) {
            val topInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = chromeFade()
                        translationY = -12.dp.toPx() * (1f - chromeAlphaState.value)
                    }
                    .align(Alignment.TopCenter)
                    .background(ChromeScrim)
                    .windowInsetsPadding(topInsets)
                    .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = ScrimTail)
                    .then(if (!chromeVisible) Modifier.clearAndSetSemantics {} else Modifier),
            ) {
                Box(Modifier.align(Alignment.CenterStart)) {
                    HighlightedIconButton(
                        size = MediaControlHeight,
                        enabled = chromeVisible,
                        onClick = {
                            if (chromeVisible && !frame.dismiss.exiting && !frame.dismiss.dragging) {
                                frame.dismiss.cancelActive()
                                nav.pop()
                            }
                        },
                    ) {
                        Icon(COUIIcons.Close, "关闭", Modifier.size(18.dp))
                    }
                }

                // 仅在多图且开启「多图显示页码」时显示，单图不展示多余的 1 / 1。
                if (App.settings.imageTopBarShowCounter && urls.size > 1) {
                    Box(Modifier.align(Alignment.Center)) {
                        MediaMaterialPill {
                            Text(
                                "${pagerState.currentPage + 1} / ${urls.size}",
                                color = LocalContentColor.current,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }

                if (currentPic?.isVideo != true) {
                    val imageTopButtons = App.settings.imageTopBarButtons
                    Row(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (editableAvatar) {
                            if (ImageTopBarButton.Original in imageTopButtons) {
                                MediaMaterialPill(
                                    onClick = if (chromeVisible) {
                                        {
                                            if (!frame.dismiss.exiting && !frame.dismiss.dragging) {
                                                chromeInteractionTick++
                                                original = !original
                                                saveState.flash(if (original) "已切换到原图" else "已切回标准尺寸")
                                            }
                                        }
                                    } else {
                                        null
                                    },
                                ) {
                                    Text(
                                        "原图",
                                        color = if (original) COUITheme.colorScheme.primary else LocalContentColor.current,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                            val menuScheme = COUITheme.colorScheme
                            Box {
                                HighlightedIconButton(
                                    size = MediaControlHeight,
                                    enabled = chromeVisible,
                                    onClick = {
                                        if (chromeVisible && !frame.dismiss.exiting && !frame.dismiss.dragging) {
                                            chromeInteractionTick++
                                            avatarMoreOpen = true
                                        }
                                    },
                                ) {
                                    val dotColor = LocalContentColor.current
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(3.dp),
                                        modifier = Modifier
                                            .semantics { contentDescription = "更多" }
                                            .testTag("image_viewer_more"),
                                    ) {
                                        repeat(3) {
                                            Box(
                                                Modifier
                                                    .size(3.5.dp)
                                                    .clip(CircleShape)
                                                    .background(dotColor),
                                            )
                                        }
                                    }
                                }
                                OverlayCascadingListPopup(
                                    show = avatarMoreOpen,
                                    entries = listOf(
                                        DropdownEntry(
                                            items = listOf(
                                                DropdownItem(
                                                    text = "编辑头像",
                                                    icon = { iconModifier ->
                                                        Box(iconModifier, contentAlignment = Alignment.Center) {
                                                            Icon(
                                                                imageVector = COUIIcons.Edit,
                                                                contentDescription = "编辑",
                                                                modifier = Modifier.size(18.dp),
                                                                tint = menuScheme.onSurfaceContainer,
                                                            )
                                                        }
                                                    },
                                                    onClick = {
                                                        avatarMoreOpen = false
                                                        pickAvatar()
                                                    },
                                                ),
                                                DropdownItem(
                                                    text = "保存原图",
                                                    icon = { iconModifier ->
                                                        Box(iconModifier, contentAlignment = Alignment.Center) {
                                                            Icon(
                                                                imageVector = COUIIcons.Download,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(18.dp),
                                                                tint = menuScheme.onSurfaceContainer,
                                                            )
                                                        }
                                                    },
                                                    onClick = {
                                                        avatarMoreOpen = false
                                                        saveState.saveOne(
                                                            SaveImageItem(
                                                                url = current,
                                                                liveUrl = currentPic?.live.orEmpty(),
                                                                preserveUrl = currentPic?.preserveUrl == true,
                                                            ),
                                                        )
                                                    },
                                                ),
                                            ),
                                        ),
                                    ),
                                    popupPositionProvider = ListPopupDefaults.DropdownPositionProvider,
                                    onDismissRequest = { avatarMoreOpen = false },
                                )
                            }
                        } else {
                            imageTopButtons.forEach { btn ->
                                when (btn) {
                                    ImageTopBarButton.Original -> {
                                        MediaMaterialPill(
                                            onClick = if (chromeVisible) {
                                                {
                                                    if (!frame.dismiss.exiting && !frame.dismiss.dragging) {
                                                        chromeInteractionTick++
                                                        original = !original
                                                        saveState.flash(if (original) "已切换到原图" else "已切回标准尺寸")
                                                    }
                                                }
                                            } else {
                                                null
                                            },
                                        ) {
                                            Text(
                                                "原图",
                                                color = if (original) COUITheme.colorScheme.primary else LocalContentColor.current,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                            )
                                        }
                                    }
                                    ImageTopBarButton.Save -> {
                                        HighlightedIconButton(
                                            size = MediaControlHeight,
                                            enabled = chromeVisible,
                                            onClick = {
                                                if (chromeVisible && !frame.dismiss.exiting && !frame.dismiss.dragging) {
                                                    chromeInteractionTick++
                                                    saveState.saveOne(
                                                        SaveImageItem(
                                                            url = current,
                                                            liveUrl = currentPic?.live.orEmpty(),
                                                            preserveUrl = currentPic?.preserveUrl == true,
                                                        ),
                                                    )
                                                }
                                            },
                                        ) {
                                            Icon(
                                                COUIIcons.Download,
                                                if (currentPic?.isLive == true) "保存实况图" else "保存原图",
                                                Modifier.size(18.dp),
                                            )
                                        }
                                    }
                                    ImageTopBarButton.Share -> {
                                        HighlightedIconButton(
                                            size = MediaControlHeight,
                                            enabled = chromeVisible,
                                            onClick = {
                                                if (chromeVisible && !frame.dismiss.exiting && !frame.dismiss.dragging && currentPic != null) {
                                                    chromeInteractionTick++
                                                    scope.launch {
                                                        val target = if (currentPic.preserveUrl) currentPic.large else originalSize(currentPic.large)
                                                        val bytes = downloadImage(target)
                                                        if (bytes != null) {
                                                            shareImageBytes(bytes, "cobo_share.jpg")
                                                        } else {
                                                            saveState.flash("图片获取失败")
                                                        }
                                                    }
                                                }
                                            },
                                        ) {
                                            Icon(COUIIcons.Share, "分享", Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 仅实况图在左下角显示「实况」胶囊、右下角显示声音开关，与安卓版完全一致。
            if (currentPic?.isLive == true) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .graphicsLayer {
                            alpha = chromeFade()
                            translationY = 10.dp.toPx() * (1f - chromeAlphaState.value)
                        }
                        .navigationBarsPadding()
                        .padding(start = 12.dp, bottom = 12.dp)
                        .then(if (!chromeVisible) Modifier.clearAndSetSemantics {} else Modifier),
                ) {
                    LivePill()
                }
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .graphicsLayer {
                            alpha = chromeFade()
                            translationY = 10.dp.toPx() * (1f - chromeAlphaState.value)
                        }
                        .navigationBarsPadding()
                        .padding(end = 12.dp, bottom = 12.dp)
                        .then(if (!chromeVisible) Modifier.clearAndSetSemantics {} else Modifier),
                ) {
                    HighlightedIconButton(
                        size = MediaControlHeight,
                        enabled = chromeVisible,
                        modifier = Modifier.testTag("live_photo_sound_toggle"),
                        onClick = {
                            if (chromeVisible && !frame.dismiss.exiting && !frame.dismiss.dragging) {
                                chromeInteractionTick++
                                val nextSound = liveMuted
                                liveMuted = !liveMuted
                                App.settings.applyLivePhotoSound(nextSound)
                                saveState.flash(if (nextSound) "已开启实况声音" else "已静音实况")
                            }
                        },
                    ) {
                        Icon(
                            imageVector = if (!liveMuted) CoboIcons.VolumeOn else CoboIcons.VolumeOff,
                            contentDescription = if (!liveMuted) "实况静音" else "开启实况声音",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        val saveItems = remember(effectivePics) {
            effectivePics.filterNot { it.isVideo }.map {
                SaveImageItem(url = it.large, liveUrl = it.live, preserveUrl = it.preserveUrl)
            }
        }
        val saveIndex = effectivePics.take(pagerState.currentPage).count { !it.isVideo }
        SaveImagesMenu(
            items = if (menuOpen) saveItems else null,
            index = saveIndex,
            precise = precise,
            onDismiss = { menuOpen = false },
            state = saveState,
        )

        SaveHud(saveState)
    }

    val cropUri = pendingCropUri
    if (cropUri != null) {
        AvatarCropDialog(
            sourceUri = cropUri,
            uploading = uploadingAvatar,
            onDismiss = { if (!uploadingAvatar) pendingCropUri = null },
            onConfirm = { bytes, cropW ->
                uploadingAvatar = true
                scope.launch {
                    runCatching { App.repo.updateAvatar(bytes = bytes, cropWidth = cropW) }
                        .onSuccess { updated ->
                            avatarOverride = updated.avatar
                            App.notifyProfileUpdated(updated)
                            App.toast("头像已更新")
                            pendingCropUri = null
                        }
                        .onFailure { App.toast(it.message ?: "上传头像失败") }
                    uploadingAvatar = false
                }
            },
        )
    }
}

@Composable
private fun ZoomableImage(
    url: String,
    live: String,
    videoUrl: String,
    thumbModel: String?,
    thumbCacheKey: MemoryCache.Key?,
    original: Boolean,
    preserveUrl: Boolean,
    isCurrent: Boolean,
    isSettledElsewhere: Boolean,
    pageIndex: Int,
    pageCount: Int,
    isPagerScrolling: () -> Boolean,
    canStartDismiss: () -> Boolean,
    progress: () -> Float,
    transitionOffset: () -> Offset,
    origins: ImageOrigins,
    frame: ViewerFrame,
    liveMuted: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDismissRelease: (Velocity) -> Unit,
) {
    val platformContext = LocalPlatformContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val transition = LocalRouteTransition.current
    val currentPageIndex by rememberUpdatedState(pageIndex)
    val currentPageCount by rememberUpdatedState(pageCount)
    val currentIsPagerScrolling by rememberUpdatedState(isPagerScrolling)
    val currentCanStartDismiss by rememberUpdatedState(canStartDismiss)
    val currentOnDismissRelease by rememberUpdatedState(onDismissRelease)
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnLongPress by rememberUpdatedState(onLongPress)

    var box by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var placed by remember(url) { mutableStateOf(false) }
    val glide = remember { mutableStateOf<Job?>(null) }

    val targetUrl = if (original && !preserveUrl) originalSize(url) else url
    var loaded by remember(url) { mutableStateOf(false) }
    var settledPainter by remember(url) { mutableStateOf<Painter?>(null) }
    var lastCacheKey by remember(url) { mutableStateOf<MemoryCache.Key?>(null) }
    val syncThumbPainter = remember(url, thumbCacheKey, platformContext) {
        thumbCacheKey?.let { key ->
            SingletonImageLoader.get(platformContext).memoryCache?.get(key)?.image?.asPainter(platformContext)
        }
    }
    var reveal by remember(url) { mutableFloatStateOf(if (syncThumbPainter != null) 0f else 1f) }
    var skipNextFade by remember(url) { mutableStateOf(false) }

    val playback = remember(live) { LivePhotoPlaybackState() }
    val liveAutoPlay = App.settings.livePhotoAutoPlay
    var liveFile by remember(live) { mutableStateOf<PlatformFile?>(null) }
    val livePlayer = remember(live) {
        if (live.isNotBlank()) PlatformVideoPlayer() else null
    }
    DisposableEffect(livePlayer) {
        onDispose { livePlayer?.release() }
    }
    LaunchedEffect(live, isCurrent) {
        if (isCurrent && live.isNotBlank() && liveFile == null) {
            liveFile = downloadMediaToFile(live, liveMediaCacheFile(App.context, live, "mov"))
        }
    }
    val dismissingNow = frame.dismiss.active || frame.dismiss.exiting || transition?.exiting?.invoke() == true
    val canLivePlay = live.isNotBlank() &&
        liveFile != null &&
        isCurrent &&
        scale <= 1f + ZoomEpsilon &&
        !dismissingNow
    val playingLive = canLivePlay && playback.isPlaying

    LaunchedEffect(isCurrent) {
        if (!isCurrent) playback.onLeftPage()
    }
    LaunchedEffect(canLivePlay) {
        if (!canLivePlay) playback.onInterrupted()
    }
    LaunchedEffect(livePlayer, liveFile, liveMuted) {
        livePlayer?.setMuted(liveMuted)
    }
    LaunchedEffect(live, liveAutoPlay, liveFile, livePlayer, isCurrent, isSettledElsewhere, playback.autoPlayedForVisit) {
        if (live.isBlank() || !liveAutoPlay || liveFile == null || livePlayer == null || !isCurrent || isSettledElsewhere || playback.autoPlayedForVisit) {
            return@LaunchedEffect
        }
        snapshotFlow {
            livePlayer.isPosterReady &&
                progress() >= 0.98f &&
                !isPagerScrolling() &&
                !frame.dismiss.active &&
                !frame.dismiss.exiting &&
                transition?.exiting?.invoke() != true &&
                scale <= 1f + ZoomEpsilon
        }.first { it }
        playback.tryAutoPlay(enabled = true)
    }
    LaunchedEffect(livePlayer, liveFile) {
        val player = livePlayer ?: return@LaunchedEffect
        val file = liveFile ?: return@LaunchedEffect
        // 预先挂载本地实况视频并解码首帧封面，实际播放时再挂载原生视图，避免静图被原生挖洞变黑。
        player.setLocalMedia(
            filePath = file.path,
            looping = false,
            muted = liveMuted,
            autoPlay = false,
        )
    }
    LaunchedEffect(livePlayer, liveFile, playingLive, playback.holdSession) {
        val player = livePlayer ?: return@LaunchedEffect
        if (liveFile == null) return@LaunchedEffect
        if (playingLive) {
            if (playback.holding) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            player.setLooping(playback.holding)
            player.setMuted(liveMuted)
            if (player.currentPositionMs > 50L || player.isEnded) {
                player.seekTo(0L)
            }
            player.play()
            if (!playback.holding) {
                snapshotFlow { player.isEnded || player.errorMessage != null }.first { it }
                playback.onPlaybackEnded()
            }
        } else {
            player.pause()
            delay(LiveFadeMillis.toLong())
            if (!playingLive) {
                player.seekTo(0L)
            }
        }
    }

    val effectiveAspect = frame.aspects[url] ?: origins.aspectOf(url)
    val model: Any = remember(url, targetUrl, original, platformContext, thumbCacheKey, lastCacheKey) {
        ImageRequest.Builder(platformContext)
            .data(targetUrl)
            .apply {
                (lastCacheKey ?: thumbCacheKey)?.let { placeholderMemoryCacheKey(it) }
                if (original) size(coil3.size.Size.ORIGINAL)
            }
            .build()
    }

    fun shown(): ComposeSize {
        val aspect = effectiveAspect ?: return ComposeSize(box.width.toFloat(), box.height.toFloat())
        return restSize(box.width.toFloat(), box.height.toFloat(), aspect)
    }

    fun limit(at: Float): Offset {
        val s = shown()
        return Offset(
            max(0f, (s.width * at - box.width) / 2f),
            max(0f, (s.height * at - box.height) / 2f),
        )
    }

    fun clamp(value: Offset, at: Float): Offset {
        val m = limit(at)
        return Offset(value.x.coerceIn(-m.x, m.x), value.y.coerceIn(-m.y, m.y))
    }

    fun restPosition(): Offset = Offset(0f, limit(1f).y)

    fun zoomedOffset(from: Offset, by: Float, focus: Offset): Offset {
        val centre = Offset(box.width / 2f, box.height / 2f)
        return from * by + (focus - centre) * (1f - by)
    }

    fun toggleZoom(focus: Offset) {
        val startScale = scale
        val startOffset = offset
        val targetScale = if (startScale > 1f + ZoomEpsilon) 1f else DoubleTapZoom
        val targetOffset = if (targetScale == 1f) {
            clamp(startOffset, 1f)
        } else {
            clamp(zoomedOffset(startOffset, targetScale / startScale, focus), targetScale)
        }
        glide.value?.cancel()
        glide.value = scope.launch {
            animate(0f, 1f, animationSpec = tween(ZoomMillis)) { t, _ ->
                scale = lerp(startScale, targetScale, t)
                offset = Offset(lerp(startOffset.x, targetOffset.x, t), lerp(startOffset.y, targetOffset.y, t))
            }
        }
    }

    fun flingBy(velocity: Float) {
        val maxY = limit(scale).y
        if (maxY <= 0f || abs(velocity) < FlingMinVelocity) return
        if ((offset.y <= -maxY + 0.5f && velocity < 0f) || (offset.y >= maxY - 0.5f && velocity > 0f)) return
        glide.value?.cancel()
        glide.value = scope.launch {
            AnimationState(initialValue = offset.y, initialVelocity = velocity)
                .animateDecay(exponentialDecay(frictionMultiplier = 1.2f)) {
                    offset = Offset(offset.x, value.coerceIn(-maxY, maxY))
                    if (value <= -maxY || value >= maxY) cancelAnimation()
                }
        }
    }

    fun rebound() {
        val from = scale
        if (from >= 1f - ZoomEpsilon) return
        glide.value?.cancel()
        glide.value = scope.launch {
            animate(
                initialValue = from,
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            ) { value, _ ->
                scale = value
                offset = clamp(offset, value)
            }
        }
    }

    LaunchedEffect(isCurrent, box, effectiveAspect) {
        if (box == IntSize.Zero || effectiveAspect == null) return@LaunchedEffect
        if (isCurrent && !placed) {
            offset = restPosition()
            placed = true
        } else if (placed) {
            offset = clamp(offset, scale)
        }
    }

    LaunchedEffect(isCurrent, isSettledElsewhere) {
        if (isSettledElsewhere && !isCurrent) {
            glide.value?.cancel()
            scale = 1f
            offset = Offset.Zero
            placed = false
        }
    }

    LaunchedEffect(isCurrent) {
        if (isCurrent) snapshotFlow { scale > 1f + ZoomEpsilon }.collect { frame.zoomed = it }
    }

    LaunchedEffect(loaded, skipNextFade) {
        if (!loaded) return@LaunchedEffect
        if (skipNextFade || reveal >= 1f) {
            reveal = 1f
        } else {
            animate(0f, 1f, animationSpec = tween(LoadFadeMillis)) { value, _ -> reveal = value }
        }
    }

    Box(Modifier.fillMaxSize()) {
        // 视觉层独立做进场与拖拽退出变换；手势层留在原位不跟着移动，保证拖拽距离与甩出速度采样不失真。
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (!isCurrent) {
                        if (frame.dismiss.active || frame.dismiss.exiting || progress() < 1f) {
                            alpha = 0f
                        } else {
                            settle()
                        }
                        return@graphicsLayer
                    }
                    val d = frame.dismiss
                    when {
                        d.exiting -> applyDismissExit(d)
                        d.active -> applyDismissDrag(d)
                        else -> {
                            val p = progress()
                            val off = transitionOffset()
                            if (p >= 1f && off == Offset.Zero) {
                                settle()
                            } else {
                                if (p >= 1f) {
                                    settle()
                                } else {
                                    val host = frame.host
                                    val liveThumb = if (host != null && !frame.zoomed) {
                                        origins.boundsOf(url, host, density.density)?.also { frame.thumbs[url] = it }
                                    } else {
                                        null
                                    }
                                    val thumb = liveThumb ?: url.takeIf { !frame.zoomed }?.let { frame.thumbs[it] }
                                    val rest = shown()
                                    val restPos = if (box != IntSize.Zero && effectiveAspect != null) restPosition() else Offset.Zero
                                    val baseOffset = if (isCurrent && !placed && effectiveAspect != null) restPos else offset
                                    val isExiting = transition?.exiting?.invoke() == true
                                    val needsScroll = isExiting &&
                                        box != IntSize.Zero &&
                                        rest.height > box.height + 0.5f &&
                                        (baseOffset - restPos).getDistance() > LongImageScrollEpsilon
                                    openFrame(
                                        progress = p,
                                        thumb = thumb,
                                        aspect = frame.aspects[url] ?: origins.aspectOf(url) ?: thumb?.aspect,
                                        needsScroll = needsScroll,
                                    )
                                }
                                translationX += off.x
                                translationY += off.y
                            }
                        }
                    }
                },
        ) {
            val liveAlpha by animateFloatAsState(
                targetValue = if (playingLive && livePlayer?.isLayerReady == true) 1f else 0f,
                animationSpec = tween(LiveFadeMillis),
                label = "livePhotoCrossfade",
            )
            val showLiveSurface = livePlayer != null &&
                liveFile != null &&
                (playingLive || liveAlpha > 0.005f) &&
                isCurrent &&
                !dismissingNow &&
                progress() >= 0.98f
            val rest = shown()
            val restPos = if (box != IntSize.Zero && effectiveAspect != null) restPosition() else Offset.Zero
            val baseOffset = if (isCurrent && !placed && effectiveAspect != null) restPos else offset
            Box(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { box = it }
                    .graphicsLayer {
                        val d = frame.dismiss
                        val isLong = rest.height > box.height + 0.5f
                        val displayOffset = when {
                            isCurrent && d.exiting && d.exitHasThumb -> {
                                val startOff = d.exitStartImageOffset
                                val targetOff = d.exitTargetImageOffset
                                val needsScroll = isLong &&
                                    (startOff - targetOff).getDistance() > LongImageScrollEpsilon
                                val tScroll = longImageScrollFactor(d.exitProgress.value, needsScroll)
                                Offset(
                                    lerp(startOff.x, targetOff.x, tScroll),
                                    lerp(startOff.y, targetOff.y, tScroll),
                                )
                            }
                            isCurrent && !d.active && !d.exiting && progress() < 1f && transition?.exiting?.invoke() == true -> {
                                val host = frame.host
                                val hasTargetThumb = !frame.zoomed && (
                                    (host != null && origins.boundsOf(url, host, density.density) != null) ||
                                        frame.thumbs[url] != null
                                    )
                                val needsScroll = box != IntSize.Zero &&
                                    isLong &&
                                    (baseOffset - restPos).getDistance() > LongImageScrollEpsilon
                                if (hasTargetThumb && needsScroll) {
                                    val tScroll = longImageScrollFactor(1f - progress(), needsScroll = true)
                                    Offset(
                                        lerp(baseOffset.x, restPos.x, tScroll),
                                        lerp(baseOffset.y, restPos.y, tScroll),
                                    )
                                } else {
                                    baseOffset
                                }
                            }
                            else -> baseOffset
                        }
                        scaleX = scale
                        scaleY = scale
                        translationX = displayOffset.x
                        translationY = displayOffset.y
                        alpha = if (showLiveSurface) 0f else 1f
                    },
            ) {
                val resting = if (rest.height > box.height + 0.5f) {
                    Modifier.requiredSize(
                        with(density) { rest.width.toDp() },
                        with(density) { rest.height.toDp() },
                    )
                } else {
                    Modifier.fillMaxSize()
                }
                Box(Modifier.align(Alignment.Center).then(resting)) {
                    val thumbScale = if (rest.height > box.height + 0.5f) ContentScale.FillWidth else ContentScale.Fit
                    val thumbAlign = if (rest.height > box.height + 0.5f) Alignment.TopCenter else Alignment.Center
                    if (!loaded || reveal < 1f) {
                        if (syncThumbPainter != null) {
                            Image(
                                painter = syncThumbPainter,
                                contentDescription = null,
                                contentScale = thumbScale,
                                alignment = thumbAlign,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else if (thumbModel != null) {
                            AsyncImage(
                                model = thumbModel,
                                contentDescription = null,
                                contentScale = thumbScale,
                                alignment = thumbAlign,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    settledPainter?.let { prev ->
                        Image(
                            painter = prev,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    AsyncImage(
                        model = model,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().graphicsLayer { alpha = reveal },
                        onSuccess = { state ->
                            if (!loaded && (state.result.dataSource == DataSource.MEMORY_CACHE ||
                                    state.result.dataSource == DataSource.MEMORY)) {
                                skipNextFade = true
                            }
                            settledPainter = state.painter
                            lastCacheKey = state.result.memoryCacheKey
                            loaded = true
                            if (live.isNotBlank()) {
                                livePlayer?.setStillImage(state.result.image)
                            }
                            val picture = state.painter.intrinsicSize
                            if (picture.width > 0f && picture.height > 0f) {
                                frame.aspects[url] = picture.width / picture.height
                            }
                        },
                    )
                    if (isCurrent && !frame.dismiss.active && !frame.dismiss.exiting && live.isBlank() && videoUrl.isBlank()) {
                        PlatformTiledLongImageOverlay(
                            url = targetUrl,
                            original = original,
                            screenWidthPx = box.width,
                            box = box,
                            scale = scale,
                            offset = offset,
                            rest = rest,
                            onAspect = { aspect -> frame.aspects[url] = aspect },
                        )
                    }
                }
            }

            if (showLiveSurface && livePlayer != null) {
                val fitted = if (rest.width > 0f && rest.height > 0f && box != IntSize.Zero) {
                    Modifier
                        .requiredSize(
                            with(density) { rest.width.toDp() },
                            with(density) { rest.height.toDp() },
                        )
                        .align(Alignment.Center)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        }
                        .clipToBounds()
                } else {
                    Modifier.fillMaxSize()
                }
                PlatformVideoSurface(
                    player = livePlayer,
                    fillCrop = true,
                    modifier = fitted,
                    transparentBackground = true,
                    alpha = liveAlpha,
                )
            }

            if (videoUrl.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = COUIIcons.Play,
                        contentDescription = "播放视频",
                        modifier = Modifier.size(34.dp),
                        tint = Color.White,
                    )
                }
            }
        }

        // 未变换的全屏手势层：保证任意方向拖拽、甩出速度和双击缩放落点在屏幕坐标系下完全准确。
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(live, videoUrl) {
                    if (live.isBlank()) {
                        detectTapGestures(
                            onTap = {
                                if (!frame.dismiss.active && !frame.dismiss.exiting) {
                                    currentOnTap()
                                }
                            },
                            onDoubleTap = { pos ->
                                if (!frame.dismiss.active && !frame.dismiss.exiting && videoUrl.isBlank()) {
                                    toggleZoom(pos)
                                }
                            },
                            onLongPress = {
                                if (!frame.dismiss.active && !frame.dismiss.exiting) {
                                    currentOnLongPress()
                                }
                            },
                        )
                    } else {
                        detectLivePress(
                            onTap = {
                                if (!frame.dismiss.active && !frame.dismiss.exiting) {
                                    currentOnTap()
                                }
                            },
                            onDoubleTap = { pos ->
                                if (!frame.dismiss.active && !frame.dismiss.exiting) {
                                    toggleZoom(pos)
                                }
                            },
                            onPlay = {
                                if (!frame.dismiss.active && !frame.dismiss.exiting) {
                                    playback.onLongPressStart()
                                }
                            },
                            onStop = { playback.onLongPressEnd() },
                        )
                    }
                }
                .pointerInput(Unit) {
                    var mode = GestureMode.UNDECIDED
                    detectZoomAndPan(
                        onStart = {
                            mode = GestureMode.UNDECIDED
                            glide.value?.cancel()
                            playback.onLongPressEnd()
                            if (frame.dismiss.active && !frame.dismiss.exiting) {
                                frame.dismiss.animJob?.cancel()
                            }
                        },
                        onRelease = { velocity, taken ->
                            if (mode == GestureMode.DISMISS || (frame.dismiss.active && !frame.dismiss.exiting)) {
                                frame.dismiss.pendingImageOffset = offset
                                currentOnDismissRelease(velocity)
                            } else if (scale < 1f - ZoomEpsilon) {
                                rebound()
                            } else if (taken) {
                                flingBy(velocity.y)
                            }
                        },
                    ) { focus, pan, direction, zoom, pointers ->
                        if (mode == GestureMode.UNDECIDED) {
                            if (frame.dismiss.exiting) {
                                mode = GestureMode.PAGER
                                return@detectZoomAndPan false
                            }
                            playback.onInterrupted()
                            if (frame.dismiss.active) {
                                mode = GestureMode.DISMISS
                                frame.dismiss.reanchor(focus)
                                frame.dismiss.dragging = true
                            } else if (pointers >= 2 || scale > 1f + ZoomEpsilon) {
                                mode = GestureMode.ZOOM_PAN
                            } else {
                                val moved = clamp(zoomedOffset(offset, 1f, focus) + pan, scale)
                                val longStripScrolls = abs(direction.y) > abs(direction.x) && moved.y != offset.y
                                val canPage = currentPageCount > 1
                                val pagerCanConsumeHorizontal = canPage && (
                                    currentIsPagerScrolling() ||
                                        (abs(direction.x) > abs(direction.y) * 1.25f &&
                                            !((currentPageIndex == 0 && direction.x > 0f) ||
                                                (currentPageIndex == currentPageCount - 1 && direction.x < 0f)))
                                    )
                                if (longStripScrolls) {
                                    mode = GestureMode.ZOOM_PAN
                                } else if (pagerCanConsumeHorizontal) {
                                    mode = GestureMode.PAGER
                                    return@detectZoomAndPan false
                                } else if (currentCanStartDismiss()) {
                                    mode = GestureMode.DISMISS
                                    playback.onInterrupted()
                                    frame.dismiss.startDrag(focus)
                                } else {
                                    mode = GestureMode.PAGER
                                    return@detectZoomAndPan false
                                }
                            }
                        }

                        when (mode) {
                            GestureMode.PAGER -> false
                            GestureMode.DISMISS -> {
                                if (pointers == 1) {
                                    frame.dismiss.dragOffset += pan
                                }
                                true
                            }
                            GestureMode.ZOOM_PAN -> {
                                val current = scale
                                val target = (current * zoom).coerceIn(MinZoom, MaxZoom)
                                val next = if (target < current && current <= 1f) {
                                    current + (target - current) * ReboundResistance
                                } else {
                                    target
                                }
                                val moved = clamp(zoomedOffset(offset, next / current, focus) + pan, next)
                                if (pointers == 1) {
                                    val across = abs(pan.x) >= abs(pan.y)
                                    val travels = if (across) moved.x != offset.x else moved.y != offset.y
                                    if (!travels) return@detectZoomAndPan false
                                }
                                scale = next
                                offset = moved
                                true
                            }
                            GestureMode.UNDECIDED -> false
                        }
                    }
                },
        )
    }
}

@Stable
internal class LivePhotoPlaybackState {
    var holding by mutableStateOf(false)
        private set
    var autoPlaying by mutableStateOf(false)
        private set
    var autoPlayedForVisit by mutableStateOf(false)
        private set
    var holdSession by mutableIntStateOf(0)
        private set

    val isPlaying: Boolean
        get() = holding || autoPlaying

    fun tryAutoPlay(enabled: Boolean): Boolean {
        if (!enabled || autoPlayedForVisit || holding) return false
        autoPlayedForVisit = true
        autoPlaying = true
        return true
    }

    fun onPlaybackEnded() {
        autoPlaying = false
    }

    fun onLongPressStart() {
        autoPlaying = false
        autoPlayedForVisit = true
        holding = true
        holdSession++
    }

    fun onLongPressEnd() {
        holding = false
    }

    fun onInterrupted() {
        holding = false
        autoPlaying = false
    }

    fun onLeftPage() {
        holding = false
        autoPlaying = false
        autoPlayedForVisit = false
    }
}

private suspend fun PointerInputScope.detectLivePress(
    onTap: () -> Unit,
    onDoubleTap: (Offset) -> Unit,
    onPlay: () -> Unit,
    onStop: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val slop = viewConfiguration.touchSlop
        var moved = false
        var released = false
        val held = withTimeoutOrNull(LiveHoldMillis) {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null || !change.pressed) {
                    released = true
                    break
                }
                if ((change.position - down.position).getDistance() > slop) {
                    moved = true
                    break
                }
            }
        }
        if (released && !moved) {
            val second = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
                awaitFirstDown(requireUnconsumed = false).position
            }
            if (second != null) onDoubleTap(second) else onTap()
            return@awaitEachGesture
        }
        if (held != null) return@awaitEachGesture
        onPlay()
        try {
            while (true) {
                val event = awaitPointerEvent()
                if (event.changes.none { it.pressed }) break
            }
        } finally {
            onStop()
        }
    }
}

private enum class GestureMode {
    UNDECIDED,
    ZOOM_PAN,
    DISMISS,
    PAGER,
}

private suspend fun PointerInputScope.detectZoomAndPan(
    onStart: () -> Unit = {},
    onRelease: (velocity: Velocity, taken: Boolean) -> Unit = { _, _ -> },
    onGesture: (focus: Offset, pan: Offset, direction: Offset, zoom: Float, pointers: Int) -> Boolean,
) {
    awaitEachGesture {
        var zoom = 1f
        var pan = Offset.Zero
        var pastTouchSlop = false
        var taken = false
        val tracker = VelocityTracker()
        val touchSlop = viewConfiguration.touchSlop
        val down = awaitFirstDown(requireUnconsumed = false)
        var trackedId = down.id
        tracker.addPointerInputChange(down)
        onStart()
        do {
            val event = awaitPointerEvent()
            val tracked = event.changes.firstOrNull { it.id == trackedId } ?: event.changes.firstOrNull()
            if (tracked != null) {
                if (tracked.id != trackedId) {
                    tracker.resetTracking()
                    trackedId = tracked.id
                }
                tracker.addPointerInputChange(tracked)
            }
            val canceled = event.changes.fastAny { it.isConsumed }
            if (!canceled) {
                val zoomChange = event.calculateZoom()
                val panChange = event.calculatePan()
                var direction = panChange
                if (!pastTouchSlop) {
                    zoom *= zoomChange
                    pan += panChange
                    val zoomMotion = abs(1f - zoom) * event.calculateCentroidSize(useCurrent = false)
                    if (zoomMotion > touchSlop || pan.getDistance() > touchSlop) {
                        pastTouchSlop = true
                        direction = pan
                    }
                }
                if (pastTouchSlop && (zoomChange != 1f || panChange != Offset.Zero)) {
                    val pointers = event.changes.count { it.pressed }
                    val focus = event.calculateCentroid(useCurrent = false)
                    if (onGesture(focus, panChange, direction, zoomChange, pointers)) {
                        taken = true
                        event.changes.fastForEach { change: PointerInputChange ->
                            if (change.positionChanged()) change.consume()
                        }
                    }
                }
            }
        } while (!canceled && event.changes.fastAny { it.pressed })
        onRelease(tracker.calculateVelocity(), taken)
    }
}

internal fun longImageScrollFactor(exitProgress: Float, needsScroll: Boolean): Float {
    if (!needsScroll) return 1f
    return (exitProgress / LongImageScrollEnd).coerceIn(0f, 1f)
}

internal fun longImageShrinkFactor(exitProgress: Float, needsScroll: Boolean): Float {
    if (!needsScroll) return exitProgress.coerceIn(0f, 1f)
    return ((exitProgress - LongImageShrinkStart) / (1f - LongImageShrinkStart)).coerceIn(0f, 1f)
}

@Stable
private class DragDismissState {
    var active by mutableStateOf(false)
    var dragging by mutableStateOf(false)
    var exiting by mutableStateOf(false)
    var dragOffset by mutableStateOf(Offset.Zero)
    var pivot by mutableStateOf(Offset.Zero)
    var boxSize by mutableStateOf(IntSize.Zero)

    val exitProgress = Animatable(0f)
    val exitTranslation = Animatable(Offset.Zero, Offset.VectorConverter)
    val exitBgAlpha = Animatable(1f)
    var exitStartScale by mutableFloatStateOf(1f)
    var exitStartTranslation by mutableStateOf(Offset.Zero)
    var exitHasThumb by mutableStateOf(false)
    var exitThumb: ThumbBounds? by mutableStateOf(null)
    var exitAspect: Float? by mutableStateOf(null)
    var exitStartImageOffset by mutableStateOf(Offset.Zero)
    var exitTargetImageOffset by mutableStateOf(Offset.Zero)
    var pendingImageOffset by mutableStateOf(Offset.Zero)
    var exitFlyTarget by mutableStateOf(Offset.Zero)
    var animJob: Job? = null

    private fun refSpan(): Float =
        minOf(boxSize.width, boxSize.height).toFloat().coerceAtLeast(1f)

    fun currentScale(): Float {
        val d = dragOffset.getDistance()
        val scaleRange = refSpan() * 0.9f
        val t = DragScaleEasing.transform((d / scaleRange).coerceIn(0f, 1f))
        return lerp(1f, DragMinScale, t)
    }

    fun currentBgAlpha(): Float {
        val d = dragOffset.getDistance()
        val alphaRange = refSpan() * 0.75f
        return (1f - d / alphaRange).coerceIn(0f, 1f)
    }

    fun currentTranslation(): Offset {
        val s = currentScale()
        return dragOffset + pivot * (1f - s)
    }

    fun startDrag(focus: Offset) {
        animJob?.cancel()
        exitStartImageOffset = Offset.Zero
        exitTargetImageOffset = Offset.Zero
        pivot = focus - Offset(boxSize.width / 2f, boxSize.height / 2f)
        dragOffset = Offset.Zero
        active = true
        dragging = true
    }

    fun reanchor(focus: Offset) {
        val curScale = currentScale()
        val curTrans = currentTranslation()
        val newPivot = focus - Offset(boxSize.width / 2f, boxSize.height / 2f)
        pivot = newPivot
        dragOffset = curTrans - newPivot * (1f - curScale)
    }

    fun cancelActive() {
        animJob?.cancel()
        dragging = false
        active = false
        dragOffset = Offset.Zero
        exitStartImageOffset = Offset.Zero
        exitTargetImageOffset = Offset.Zero
        pendingImageOffset = Offset.Zero
    }
}

private fun handleDismissRelease(
    velocity: Velocity,
    dismiss: DragDismissState,
    frame: ViewerFrame,
    origins: ImageOrigins,
    url: String?,
    density: Float,
    scope: CoroutineScope,
    startImageOffset: Offset = Offset.Zero,
    onPopImmediate: () -> Unit,
) {
    if (dismiss.exiting) return
    dismiss.dragging = false
    val dragPos = dismiss.dragOffset
    val dist = dragPos.getDistance()
    val vel = Offset(velocity.x, velocity.y)
    val speed = vel.getDistance()

    val flingThreshold = 200f * density
    val distanceThreshold = 80f * density
    val backGuard = 36f * density
    val backFlingThreshold = 320f * density
    val dot = dragPos.x * vel.x + dragPos.y * vel.y
    val isFlingBack = dist > backGuard && speed >= backFlingThreshold && dot < -0.6f * dist * speed
    val shouldDismiss = !isFlingBack && (speed >= flingThreshold || dist >= distanceThreshold)

    if (!shouldDismiss) {
        dismiss.animJob?.cancel()
        dismiss.animJob = scope.launch {
            animate(
                typeConverter = Offset.VectorConverter,
                initialValue = dismiss.dragOffset,
                targetValue = Offset.Zero,
                initialVelocity = vel,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = 500f,
                    visibilityThreshold = Offset(0.5f, 0.5f),
                ),
            ) { value, _ ->
                dismiss.dragOffset = value
            }
            dismiss.dragOffset = Offset.Zero
            dismiss.active = false
        }
        return
    }

    dismiss.animJob?.cancel()
    dismiss.exiting = true
    val startScale = dismiss.currentScale()
    val startTranslation = dismiss.currentTranslation()
    val startBgAlpha = dismiss.currentBgAlpha()

    val host = frame.host
    val live = if (url != null && host != null && !frame.zoomed) {
        origins.boundsOf(url, host, density)?.also { frame.thumbs[url] = it }
    } else {
        null
    }
    val thumb = live ?: url?.takeIf { !frame.zoomed }?.let { frame.thumbs[it] }
    val aspect = url?.let { frame.aspects[it] ?: thumb?.aspect }

    val w = dismiss.boxSize.width.toFloat()
    val h = dismiss.boxSize.height.toFloat()
    val effectiveAspect = aspect ?: thumb?.rect?.let { if (it.height > 0f) it.width / it.height else null }
    val rest = if (w > 0f && h > 0f && effectiveAspect != null) restSize(w, h, effectiveAspect) else null
    val longImage = rest != null && rest.height > h + 0.5f
    val hasThumb = thumb != null && rest != null
    val targetImageOffset = if (longImage) Offset(0f, ((rest.height - h) / 2f).coerceAtLeast(0f)) else Offset.Zero
    val needsLongScroll = longImage && (startImageOffset - targetImageOffset).getDistance() > LongImageScrollEpsilon

    dismiss.exitStartScale = startScale
    dismiss.exitStartTranslation = startTranslation
    dismiss.exitStartImageOffset = if (longImage) startImageOffset else Offset.Zero
    dismiss.exitTargetImageOffset = targetImageOffset

    dismiss.animJob = scope.launch {
        dismiss.exitBgAlpha.snapTo(startBgAlpha)
        dismiss.exitProgress.snapTo(0f)

        if (hasThumb) {
            val source = thumb.rect
            val targetTranslation = Offset(source.center.x - w / 2f, source.center.y - h / 2f)
            val maxFlingSpeed = 1200f * density
            val clampedVel = if (speed > maxFlingSpeed && speed > 0f) {
                vel * (maxFlingSpeed / speed)
            } else {
                vel
            }
            dismiss.exitThumb = thumb
            dismiss.exitAspect = effectiveAspect
            dismiss.exitHasThumb = true
            dismiss.exitTranslation.snapTo(startTranslation)

            val exitMillis = if (needsLongScroll) 460 else 320
            coroutineScope {
                launch {
                    dismiss.exitBgAlpha.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
                }
                launch {
                    dismiss.exitProgress.animateTo(1f, tween(exitMillis, easing = ExpandEasing))
                }
                launch {
                    dismiss.exitTranslation.animateTo(
                        targetValue = targetTranslation,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = 700f,
                            visibilityThreshold = Offset(0.5f, 0.5f),
                        ),
                        initialVelocity = clampedVel,
                    )
                }
            }
        } else {
            origins.viewing = null
            val dir = when {
                speed >= flingThreshold && speed > 0f -> vel / speed
                dist > 1f -> dragPos / dist
                else -> Offset(0f, 1f)
            }
            val flyDistance = minOf(w, h).coerceAtLeast(1f) * 0.42f
            dismiss.exitHasThumb = false
            dismiss.exitFlyTarget = startTranslation + dir * flyDistance

            coroutineScope {
                launch {
                    dismiss.exitBgAlpha.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
                }
                launch {
                    dismiss.exitProgress.animateTo(1f, tween(240, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)))
                }
            }
        }
        onPopImmediate()
    }
}

private fun GraphicsLayerScope.applyDismissDrag(d: DragDismissState) {
    val scale = d.currentScale()
    val trans = d.currentTranslation()
    alpha = 1f
    transformOrigin = TransformOrigin.Center
    scaleX = scale
    scaleY = scale
    translationX = trans.x
    translationY = trans.y
    shape = RectangleShape
    clip = true
}

private fun GraphicsLayerScope.applyDismissExit(d: DragDismissState) {
    val p = d.exitProgress.value
    if (!d.exitHasThumb || size.width <= 0f || size.height <= 0f) {
        val curScale = lerp(d.exitStartScale, d.exitStartScale * 0.78f, p)
        alpha = (1f - p).coerceIn(0f, 1f)
        transformOrigin = TransformOrigin.Center
        scaleX = curScale
        scaleY = curScale
        translationX = lerp(d.exitStartTranslation.x, d.exitFlyTarget.x, p)
        translationY = lerp(d.exitStartTranslation.y, d.exitFlyTarget.y, p)
        shape = RectangleShape
        clip = false
        return
    }
    val thumb = d.exitThumb!!
    val source = thumb.rect
    val rest = (d.exitAspect ?: (source.width / source.height)).let {
        restSize(size.width, size.height, it)
    }
    val needsScroll = rest.height > size.height + 0.5f &&
        (d.exitStartImageOffset - d.exitTargetImageOffset).getDistance() > LongImageScrollEpsilon
    val shrink = longImageShrinkFactor(p, needsScroll = needsScroll)
    applyThumbReturnFrame(
        source = source,
        cornerRadius = thumb.cornerRadius,
        rest = rest,
        startScale = d.exitStartScale,
        trans = d.exitTranslation.value,
        shrink = shrink,
    )
}

@Stable
private class ViewerFrame {
    var host: LayoutCoordinates? = null
    var zoomed by mutableStateOf(false)
    val dismiss = DragDismissState()
    val aspects = mutableStateMapOf<String, Float>()
    val thumbs = HashMap<String, ThumbBounds>()
}

private fun GraphicsLayerScope.settle() {
    alpha = 1f
    scaleX = 1f
    scaleY = 1f
    translationX = 0f
    translationY = 0f
    shape = RectangleShape
    clip = false
}

private fun GraphicsLayerScope.openFrame(
    progress: Float,
    thumb: ThumbBounds?,
    aspect: Float?,
    needsScroll: Boolean = false,
) {
    if (size.width <= 0f || size.height <= 0f) {
        settle()
        return
    }
    val source = thumb?.rect
    val rest = (aspect ?: source?.let { it.width / it.height })
        ?.let { restSize(size.width, size.height, it) }
    if (source == null || rest == null) {
        settle()
        alpha = progress
        scaleX = lerp(0.92f, 1f, progress)
        scaleY = scaleX
        return
    }
    val shrink = longImageShrinkFactor(1f - progress, needsScroll = needsScroll)
    val trans = Offset(
        lerp(0f, source.center.x - size.width / 2f, shrink),
        lerp(0f, source.center.y - size.height / 2f, shrink),
    )
    applyThumbReturnFrame(
        source = source,
        cornerRadius = thumb.cornerRadius,
        rest = rest,
        startScale = 1f,
        trans = trans,
        shrink = shrink,
    )
}

private fun GraphicsLayerScope.applyThumbReturnFrame(
    source: Rect,
    cornerRadius: Float,
    rest: ComposeSize,
    startScale: Float,
    trans: Offset,
    shrink: Float,
) {
    val cover = max(source.width / rest.width, source.height / rest.height)
    val curScale = lerp(startScale, cover, shrink).coerceAtLeast(0.001f)
    val destW = lerp(size.width * startScale, source.width, shrink)
    val destH = lerp(size.height * startScale, source.height, shrink)
    val radius = lerp(0f, cornerRadius, shrink) / curScale
    alpha = 1f
    if (rest.height > size.height + 0.5f) {
        val destLeft = size.width / 2f + trans.x - destW / 2f
        val destTop = size.height / 2f + trans.y - destH / 2f
        val winW = destW / curScale
        val winH = destH / curScale
        val winLeft = (size.width - winW) / 2f
        transformOrigin = TransformOrigin(0f, 0f)
        scaleX = curScale
        scaleY = curScale
        translationX = destLeft - curScale * winLeft
        translationY = destTop
        shape = TopWindow(winLeft, winW, winH, radius)
    } else {
        transformOrigin = TransformOrigin.Center
        scaleX = curScale
        scaleY = curScale
        translationX = trans.x
        translationY = trans.y
        shape = CenteredWindow(
            width = destW / curScale,
            height = destH / curScale,
            radius = radius,
        )
    }
    clip = true
}

private class TopWindow(
    private val left: Float,
    private val width: Float,
    private val height: Float,
    private val radius: Float,
) : Shape {
    override fun createOutline(
        size: ComposeSize,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = Outline.Rounded(
        RoundRect(left, 0f, left + width, height, CornerRadius(radius)),
    )
}

private class CenteredWindow(
    private val width: Float,
    private val height: Float,
    private val radius: Float,
) : Shape {
    override fun createOutline(
        size: ComposeSize,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val left = (size.width - width) / 2f
        val top = (size.height - height) / 2f
        return Outline.Rounded(RoundRect(left, top, left + width, top + height, CornerRadius(radius)))
    }
}

private fun fitSize(boxWidth: Float, boxHeight: Float, aspect: Float): ComposeSize =
    if (aspect > boxWidth / boxHeight) {
        ComposeSize(boxWidth, boxWidth / aspect)
    } else {
        ComposeSize(boxHeight * aspect, boxHeight)
    }

private fun restSize(boxWidth: Float, boxHeight: Float, aspect: Float): ComposeSize =
    if (aspect < LongImageAspect) ComposeSize(boxWidth, boxWidth / aspect) else fitSize(boxWidth, boxHeight, aspect)

@Composable
fun VideoScreen(
    nav: NavState,
    url: String,
    title: String,
    isLive: Boolean = false,
    fid: String = "",
    statusId: String = "",
) {
    val scope = rememberCoroutineScope()
    val floatingController = LocalFloatingVideoController.current
    val density = LocalDensity.current.density
    val liveRequested = remember(url, isLive) { isLive || isWeiboLiveLink(url) }
    val browserFallbackUrl = remember(url) {
        val unwrapped = unwrapWeiboLink(url)
        resolveWeiboLink(unwrapped) ?: unwrapped
    }

    var resolved by remember(url, liveRequested, fid, statusId) {
        val floatingMatch = floatingController.peekMatching(url)
        mutableStateOf<ResolvedVideo?>(
            when {
                floatingMatch != null -> ResolvedVideo.DirectStream(
                    url = floatingMatch.request.streamUrl,
                    fallbackUrl = floatingMatch.request.fallbackStreamUrl,
                    referer = floatingMatch.request.referer,
                    title = floatingMatch.request.title,
                    isLive = floatingMatch.request.isLive,
                )
                !liveRequested &&
                    isDirectVideoStream(url) &&
                    !shouldRefreshDirectStreamBeforePlay(url, fid, statusId, false) -> {
                    ResolvedVideo.DirectStream(url, referer = defaultRefererFor(url), isLive = false)
                }
                else -> null
            },
        )
    }

    LaunchedEffect(url, liveRequested, fid, statusId) {
        if (resolved == null) {
            resolved = resolvePlayableVideo(url, isLive = liveRequested, fid = fid, statusId = statusId)
        }
    }

    when (val state = resolved) {
        null -> {
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text("正在解析视频…", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                }
            }
        }
        is ResolvedVideo.WebPage -> {
            PlatformWebVideoPlayer(
                nav = nav,
                pageUrl = state.url,
                title = state.title.ifBlank { title.ifBlank { "视频" } },
                onDownload = { mediaUrl ->
                    scope.launch {
                        App.toast("正在下载视频…")
                        downloadVideoToGallery(App.context, mediaUrl, referer = state.url)
                            .onSuccess { App.toast("视频已保存到相册") }
                            .onFailure { App.toast(it.message ?: "视频保存失败") }
                    }
                },
                onFlash = { msg -> App.toast(msg) },
            )
        }
        is ResolvedVideo.LiveUnavailable -> {
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(24.dp),
                ) {
                    Text(state.message, color = Color.White, fontSize = 16.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(text = "返回", onClick = { nav.pop() })
                        if (state.webUrl.isNotBlank()) {
                            TextButton(text = "网页打开", onClick = { openExternalUrl(state.webUrl) })
                        }
                    }
                }
            }
        }
        is ResolvedVideo.DirectStream -> {
            val haptic = LocalHapticFeedback.current
            var currentStream by remember(state) { mutableStateOf(state) }
            var handedOffToFloating by remember { mutableStateOf(false) }
            val existingFloating = remember(url) { floatingController.takeMatching(url) }
            val player = remember(existingFloating) { existingFloating?.player ?: PlatformVideoPlayer() }
            var controlsVisible by remember { mutableStateOf(true) }
            var controlsInteractionTick by remember { mutableIntStateOf(0) }
            var landscape by remember { mutableStateOf(false) }
            var speed by remember(player) { mutableFloatStateOf(existingFloating?.request?.playbackSpeed ?: 1f) }
            var speedMenu by remember { mutableStateOf(false) }
            var longPressSpeedActive by remember { mutableStateOf(false) }
            var longPressSpeedLocked by remember { mutableStateOf(false) }
            var scrubbing by remember { mutableStateOf(false) }
            var scrubMs by remember { mutableLongStateOf(0L) }
            var errorRefreshCount by remember { mutableIntStateOf(0) }
            var hudText by remember { mutableStateOf("") }
            var hudJob by remember { mutableStateOf<Job?>(null) }
            var centerGestureSnapshot by remember { mutableStateOf(VideoCenterGestureSnapshot()) }
            var centerGestureAnchorYPx by remember { mutableFloatStateOf(0f) }
            var centerGestureDismissJob by remember { mutableStateOf<Job?>(null) }
            var downloading by remember { mutableStateOf(false) }
            val liveMode = currentStream.isLive
            val currentLiveMode by rememberUpdatedState(liveMode)
            val centerSwipeEnabled = App.settings.videoCenterSwipeActions
            val currentCenterSwipeEnabled by rememberUpdatedState(centerSwipeEnabled)
            val effectivePlaybackSpeed = if (liveMode) {
                1f
            } else if (longPressSpeedActive || longPressSpeedLocked) {
                3f
            } else {
                speed
            }

            fun flash(message: String) {
                hudJob?.cancel()
                hudText = message
                hudJob = scope.launch {
                    delay(1400L)
                    if (hudText == message) hudText = ""
                }
            }

            fun seekBy(deltaMs: Long) {
                if (currentLiveMode) return
                val dur = player.durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE
                val target = (player.currentPositionMs + deltaMs).coerceIn(0L, dur)
                player.seekTo(target)
                flash(if (deltaMs < 0L) "快退 5 秒" else "快进 5 秒")
            }

            fun togglePlayPause() {
                if (currentLiveMode) return
                if (player.isPlaying) {
                    player.pause()
                    flash("已暂停")
                } else {
                    if (player.isEnded) player.seekTo(0L)
                    player.play()
                    flash("继续播放")
                }
            }

            LaunchedEffect(player, currentStream.title, title, liveMode) {
                player.configureSystemMedia(
                    title = currentStream.title.ifBlank { title },
                    isLive = liveMode,
                    enabled = true,
                )
            }

            DisposableEffect(player, floatingController) {
                val pipCallback: (Float) -> Boolean = { player.startSystemPictureInPicture() }
                floatingController.requestSystemPipCallback = pipCallback
                player.setSystemPipStateListener { active ->
                    floatingController.systemPipActive = active
                }
                onDispose {
                    if (floatingController.requestSystemPipCallback === pipCallback) {
                        floatingController.requestSystemPipCallback = null
                    }
                    if (!handedOffToFloating && !player.isSystemPipActive) {
                        floatingController.systemPipActive = false
                    }
                    player.setSystemPipStateListener(null)
                }
            }

            LaunchedEffect(player, effectivePlaybackSpeed) {
                player.setSpeed(effectivePlaybackSpeed)
            }

            LaunchedEffect(currentStream.url) {
                if (existingFloating == null) {
                    val headers = buildMap {
                        put("User-Agent", Api.WEB_UA)
                        if (currentStream.referer.isNotBlank()) put("Referer", currentStream.referer)
                    }
                    player.setMedia(
                        url = currentStream.url,
                        headers = headers,
                        looping = false,
                        muted = false,
                        autoPlay = true,
                    )
                }
            }

            LaunchedEffect(player.errorMessage) {
                val err = player.errorMessage ?: return@LaunchedEffect
                if (currentStream.fallbackUrl.isNotBlank() && currentStream.fallbackUrl != currentStream.url) {
                    currentStream = currentStream.copy(url = currentStream.fallbackUrl, fallbackUrl = "")
                } else if (shouldAttemptStreamRefreshOnError(errorRefreshCount, fid, statusId, currentStream.isLive)) {
                    errorRefreshCount++
                    val refreshed = refreshPlayableVideo(url, fid, statusId, currentStream.isLive)
                    if (refreshed is ResolvedVideo.DirectStream && refreshed.url != currentStream.url) {
                        currentStream = refreshed
                    }
                } else {
                    flash(err)
                }
            }

            LaunchedEffect(player.isEnded, player.hasRenderedFirstFrame) {
                if (player.isEnded && shouldAutoCloseVideoOnComplete(currentStream.isLive, App.settings.videoAutoCloseOnComplete, player.hasRenderedFirstFrame)) {
                    nav.pop()
                }
            }

            LaunchedEffect(player.isPlaying, player.isBuffering) {
                keepScreenOn(shouldKeepVideoScreenOn(player.isPlaying, player.isBuffering))
            }

            LaunchedEffect(controlsVisible, controlsInteractionTick, player.isPlaying, speedMenu, scrubbing) {
                if (shouldAutoHideVideoControls(App.settings.autoHideFloatingBars, controlsVisible, player.isPlaying, scrubbing) && !speedMenu) {
                    delay(3000L)
                    controlsVisible = false
                }
            }

            LaunchedEffect(landscape) {
                setScreenOrientation(if (landscape) PlatformOrientation.Landscape else PlatformOrientation.Portrait)
                setSystemBarsVisible(!landscape)
            }

            DisposableEffect(Unit) {
                setStatusBarDarkIcons(false)
                onDispose {
                    keepScreenOn(false)
                    setScreenOrientation(PlatformOrientation.Portrait)
                    setSystemBarsVisible(true)
                    setStatusBarDarkIcons(null)
                    writeScreenBrightness(-1f)
                    if (!handedOffToFloating) {
                        player.release()
                    }
                }
            }

            fun videoAspectRatio(): Float =
                if (player.videoWidth > 0 && player.videoHeight > 0) {
                    player.videoWidth.toFloat() / player.videoHeight.toFloat()
                } else {
                    16f / 9f
                }

            fun enterFloatingAndClose() {
                if (handedOffToFloating) return
                handedOffToFloating = true
                floatingController.startFloating(
                    FloatingVideoSession(
                        request = FloatingVideoRequest(
                            rawUrl = url,
                            streamUrl = currentStream.url,
                            fallbackStreamUrl = currentStream.fallbackUrl,
                            referer = currentStream.referer,
                            title = currentStream.title.ifBlank { title },
                            isLive = currentStream.isLive,
                            fid = fid,
                            statusId = statusId,
                            playbackSpeed = speed,
                        ),
                        player = player,
                        initialAspectRatio = videoAspectRatio(),
                    ),
                )
                nav.pop()
            }

            fun requestPipOrNotify() {
                if (!floatingController.requestSystemPip(videoAspectRatio())) {
                    flash("视频加载完成后即可开启系统画中画")
                }
            }

            fun handleBack() {
                if (speedMenu) {
                    speedMenu = false
                    return
                }
                if (landscape) {
                    landscape = false
                    return
                }
                if (shouldAutoFloatVideoOnBack(App.settings.videoAutoFloatOnBack, player.isPlaying, player.errorMessage != null)) {
                    enterFloatingAndClose()
                } else {
                    nav.pop()
                }
            }

            PlatformBackHandler { handleBack() }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            ) {
                val containerWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                val containerHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)

                PlatformVideoSurface(
                    player = player,
                    fillCrop = false,
                    modifier = Modifier.fillMaxSize(),
                )

                // 全屏手势层：单击显隐、左/中/右双击快退5秒/暂停播放/快进5秒、长按 3× 并下滑锁定、
                // 横向拖动快进退、纵向左亮度/中区悬浮或关闭/右音量。
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(containerWidthPx, liveMode) {
                            if (currentLiveMode) return@pointerInput
                            var startMs = 0L
                            var totalDx = 0f
                            var active = false
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    if (currentLiveMode || longPressSpeedActive) {
                                        active = false
                                        return@detectHorizontalDragGestures
                                    }
                                    if (speedMenu) speedMenu = false
                                    val dur = player.durationMs
                                    active = dur > 0L
                                    if (active) {
                                        scrubbing = true
                                        startMs = player.currentPositionMs.coerceIn(0L, dur)
                                        totalDx = 0f
                                        scrubMs = startMs
                                        controlsVisible = true
                                    }
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    if (!active || currentLiveMode || longPressSpeedActive) return@detectHorizontalDragGestures
                                    change.consume()
                                    totalDx += dragAmount
                                    scrubMs = calculateHorizontalScrubTargetMs(startMs, totalDx, containerWidthPx, player.durationMs)
                                    hudJob?.cancel()
                                    hudText = "${formatVideoTime(scrubMs)} / ${formatVideoTime(player.durationMs)}"
                                },
                                onDragEnd = {
                                    if (!active) return@detectHorizontalDragGestures
                                    player.seekTo(scrubMs)
                                    scrubbing = false
                                    active = false
                                    hudText = ""
                                },
                                onDragCancel = {
                                    if (!active) return@detectHorizontalDragGestures
                                    scrubbing = false
                                    active = false
                                    hudText = ""
                                },
                            )
                        }
                        .pointerInput(containerWidthPx, containerHeightPx) {
                            var startZone = VideoVerticalZone.CenterActions
                            var startBrightness = 0.5f
                            var startVolume = 0.5f
                            var dragYTotal = 0f
                            var verticalActive = false
                            var lastArmed = VideoCenterGestureAction.None
                            detectVerticalDragGestures(
                                onDragStart = { offset ->
                                    if (longPressSpeedActive) {
                                        verticalActive = false
                                        return@detectVerticalDragGestures
                                    }
                                    verticalActive = true
                                    dragYTotal = 0f
                                    startZone = classifyVideoVerticalZone(
                                        offset.x,
                                        containerWidthPx,
                                        currentCenterSwipeEnabled,
                                    )
                                    when (startZone) {
                                        VideoVerticalZone.Brightness -> startBrightness = readScreenBrightness()
                                        VideoVerticalZone.Volume -> startVolume = readSystemVolume()
                                        VideoVerticalZone.CenterActions -> {
                                            centerGestureDismissJob?.cancel()
                                            centerGestureDismissJob = null
                                            centerGestureAnchorYPx = offset.y
                                            centerGestureSnapshot = VideoCenterGestureSnapshot()
                                            lastArmed = VideoCenterGestureAction.None
                                        }
                                    }
                                },
                                onVerticalDrag = { change, dragAmount ->
                                    if (!verticalActive || longPressSpeedActive) return@detectVerticalDragGestures
                                    change.consume()
                                    dragYTotal += dragAmount
                                    when (startZone) {
                                        VideoVerticalZone.Brightness -> {
                                            val next = (startBrightness - dragYTotal / (containerHeightPx * 0.65f)).coerceIn(0.02f, 1f)
                                            writeScreenBrightness(next)
                                            hudJob?.cancel()
                                            hudText = "亮度 ${(next * 100).roundToInt()}%"
                                        }
                                        VideoVerticalZone.Volume -> {
                                            val next = (startVolume - dragYTotal / (containerHeightPx * 0.65f)).coerceIn(0f, 1f)
                                            writeSystemVolume(next)
                                            hudJob?.cancel()
                                            hudText = "音量 ${(next * 100).roundToInt()}%"
                                        }
                                        VideoVerticalZone.CenterActions -> {
                                            val next = evaluateVideoCenterGesture(dragYTotal, density)
                                            if (next.armedAction != lastArmed && next.armedAction != VideoCenterGestureAction.None) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                            lastArmed = next.armedAction
                                            centerGestureSnapshot = next
                                        }
                                    }
                                },
                                onDragEnd = {
                                    if (!verticalActive) return@detectVerticalDragGestures
                                    verticalActive = false
                                    if (startZone == VideoVerticalZone.CenterActions) {
                                        val armed = centerGestureSnapshot.armedAction
                                        val dir = centerGestureSnapshot.direction
                                        when (armed) {
                                            VideoCenterGestureAction.InAppFloating -> {
                                                centerGestureSnapshot = VideoCenterGestureSnapshot()
                                                enterFloatingAndClose()
                                            }
                                            VideoCenterGestureAction.SystemPip -> {
                                                centerGestureSnapshot = VideoCenterGestureSnapshot()
                                                requestPipOrNotify()
                                            }
                                            VideoCenterGestureAction.Close -> {
                                                centerGestureSnapshot = VideoCenterGestureSnapshot()
                                                nav.pop()
                                            }
                                            VideoCenterGestureAction.None -> {
                                                if (dir != VideoCenterGestureDirection.None) {
                                                    centerGestureDismissJob?.cancel()
                                                    centerGestureDismissJob = scope.launch {
                                                        delay(VideoCenterGestureAutoDismissMillis)
                                                        centerGestureSnapshot = VideoCenterGestureSnapshot()
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        hudText = ""
                                    }
                                },
                                onDragCancel = {
                                    if (!verticalActive) return@detectVerticalDragGestures
                                    verticalActive = false
                                    hudText = ""
                                    if (startZone == VideoVerticalZone.CenterActions &&
                                        centerGestureSnapshot.direction != VideoCenterGestureDirection.None
                                    ) {
                                        centerGestureDismissJob?.cancel()
                                        centerGestureDismissJob = scope.launch {
                                            delay(VideoCenterGestureAutoDismissMillis)
                                            centerGestureSnapshot = VideoCenterGestureSnapshot()
                                        }
                                    }
                                },
                            )
                        }
                        .pointerInput(liveMode) {
                            detectTapGestures(
                                onTap = {
                                    if (speedMenu) {
                                        speedMenu = false
                                    } else {
                                        controlsVisible = !controlsVisible
                                    }
                                },
                                onDoubleTap = { pos ->
                                    if (speedMenu) {
                                        speedMenu = false
                                        return@detectTapGestures
                                    }
                                    if (currentLiveMode) return@detectTapGestures
                                    val w = size.width.toFloat().coerceAtLeast(1f)
                                    when {
                                        pos.x < w / 3f -> seekBy(-SeekStepMs)
                                        pos.x > w * 2f / 3f -> seekBy(SeekStepMs)
                                        else -> togglePlayPause()
                                    }
                                },
                                onLongPress = { /* 长按 3× 倍速与锁定由下方 awaitEachGesture 接管 */ },
                            )
                        }
                        .pointerInput(liveMode) {
                            if (currentLiveMode) return@pointerInput
                            val lockDragThresholdPx = with(this) { 48.dp.toPx() }
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                if (currentLiveMode) return@awaitEachGesture
                                var lastPos = down.position
                                val movedOrLifted = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                    var early = false
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id }
                                        if (change == null || !change.pressed) {
                                            early = true
                                            break
                                        }
                                        lastPos = change.position
                                        if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                            early = true
                                            break
                                        }
                                    }
                                    early
                                } ?: false
                                if (movedOrLifted) return@awaitEachGesture

                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (longPressSpeedLocked) {
                                    longPressSpeedLocked = false
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        if (event.changes.none { it.pressed }) break
                                    }
                                    return@awaitEachGesture
                                }

                                longPressSpeedActive = true
                                val anchorY = lastPos.y
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val tracked = event.changes.firstOrNull { it.id == down.id }
                                    if (tracked != null && tracked.pressed && !longPressSpeedLocked) {
                                        if (tracked.position.y - anchorY >= lockDragThresholdPx) {
                                            longPressSpeedLocked = true
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                    }
                                    if (event.changes.none { it.pressed }) break
                                }
                                longPressSpeedActive = false
                            }
                        },
                )

                if (player.isBuffering && player.errorMessage == null) {
                    CoboRefreshSpinner(
                        modifier = Modifier.align(Alignment.Center),
                        size = 30.dp,
                    )
                }

                if (player.errorMessage != null) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.Black.copy(alpha = 0.78f))
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(player.errorMessage ?: "播放失败", color = Color.White, fontSize = 14.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TextButton(
                                text = "重试",
                                onClick = {
                                    val headers = buildMap {
                                        put("User-Agent", Api.WEB_UA)
                                        if (currentStream.referer.isNotBlank()) put("Referer", currentStream.referer)
                                    }
                                    player.setMedia(currentStream.url, headers, autoPlay = true)
                                },
                            )
                            TextButton(
                                text = "浏览器打开",
                                onClick = { openExternalUrl(browserFallbackUrl) },
                            )
                        }
                    }
                }

                // 顶栏（与安卓 COUI 17 完全一致，并按「顶栏」设置渲染右侧按钮与顺序）
                AnimatedVisibility(
                    visible = !floatingController.systemPipActive && (controlsVisible || speedMenu),
                    enter = fadeIn(tween(300, easing = VideoBarEnterEasing)) +
                        slideInVertically(tween(340, easing = VideoBarEnterEasing)) { -it / 4 },
                    exit = fadeOut(tween(480, easing = VideoBarExitEasing)) +
                        slideOutVertically(tween(520, easing = VideoBarExitEasing)) { -it / 5 },
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent),
                                ),
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {}
                            .statusBarsPadding()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HighlightedIconButton(
                            onClick = { nav.pop() },
                            size = VideoTopBarButtonHeight,
                        ) {
                            Icon(COUIIcons.Close, "关闭", Modifier.size(18.dp))
                        }
                        if (liveMode) {
                            val liveSquircleEnabled = isSquircleEnabled()
                            val liveShape = remember(liveSquircleEnabled) {
                                CapsuleOutlineShape(squircleEnabled = liveSquircleEnabled)
                            }
                            Spacer(Modifier.width(8.dp))
                            Row(
                                modifier = Modifier
                                    .clip(liveShape)
                                    .background(Color.White.copy(alpha = 0.14f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFF3B30)),
                                )
                                Text("直播", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = currentStream.title.ifBlank { title },
                            color = Color.White,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                        )
                        val videoTopButtons = App.settings.videoTopBarButtons
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            videoTopButtons.forEach { btn ->
                                when (btn) {
                                    VideoTopBarButton.Speed -> {
                                        if (!liveMode) {
                                            Box {
                                                VideoSpeedPill(
                                                    text = speedLabel(effectivePlaybackSpeed),
                                                    onClick = {
                                                        controlsInteractionTick++
                                                        speedMenu = !speedMenu
                                                    },
                                                )
                                                OverlayListPopup(
                                                    show = speedMenu,
                                                    popupPositionProvider = ListPopupDefaults.DropdownPositionProvider,
                                                    onDismissRequest = { speedMenu = false },
                                                ) {
                                                    val dismiss = LocalDismissState.current
                                                    ListPopupColumn {
                                                        Speeds.forEachIndexed { idx, value ->
                                                            DropdownImpl(
                                                                item = DropdownItem(text = speedLabel(value)),
                                                                optionSize = Speeds.size,
                                                                isSelected = !longPressSpeedLocked && speed == value,
                                                                index = idx,
                                                                groupKey = "video_speed",
                                                                onSelectedIndexChange = {
                                                                    controlsInteractionTick++
                                                                    speed = value
                                                                    longPressSpeedLocked = false
                                                                    flash("${speedLabel(value)} 倍速")
                                                                    dismiss?.invoke()
                                                                },
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    VideoTopBarButton.Download -> {
                                        if (!liveMode && !isM3u8Stream(currentStream.url)) {
                                            HighlightedIconButton(
                                                onClick = {
                                                    controlsInteractionTick++
                                                    if (downloading) return@HighlightedIconButton
                                                    downloading = true
                                                    flash("正在下载视频…")
                                                    scope.launch {
                                                        downloadVideoToGallery(App.context, currentStream.url, currentStream.referer)
                                                            .onSuccess { flash("已保存视频到相册") }
                                                            .onFailure { flash(it.message ?: "保存视频失败") }
                                                        downloading = false
                                                    }
                                                },
                                                size = VideoTopBarButtonHeight,
                                            ) {
                                                Icon(COUIIcons.Download, "下载视频", Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                    VideoTopBarButton.FloatingWindow -> {
                                        HighlightedIconButton(
                                            onClick = {
                                                controlsInteractionTick++
                                                enterFloatingAndClose()
                                            },
                                            size = VideoTopBarButtonHeight,
                                        ) {
                                            Icon(CoboIcons.FloatingWindow, "小窗播放", Modifier.size(18.dp))
                                        }
                                    }
                                    VideoTopBarButton.PictureInPicture -> {
                                        HighlightedIconButton(
                                            onClick = {
                                                controlsInteractionTick++
                                                requestPipOrNotify()
                                            },
                                            size = VideoTopBarButtonHeight,
                                        ) {
                                            Icon(CoboIcons.PictureInPicture, "画中画", Modifier.size(18.dp))
                                        }
                                    }
                                    VideoTopBarButton.Browser -> {
                                        HighlightedIconButton(
                                            onClick = {
                                                controlsInteractionTick++
                                                openExternalUrl(browserFallbackUrl)
                                            },
                                            size = VideoTopBarButtonHeight,
                                        ) {
                                            Icon(CoboIcons.Globe, "用浏览器打开", Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 底部控件：居中 -5s / 暂停播放 / +5s + COUI 17 光感进度条 IntentSlider（与安卓版完全一致）
                AnimatedVisibility(
                    visible = !floatingController.systemPipActive && !liveMode && (controlsVisible || speedMenu),
                    enter = fadeIn(tween(300, easing = VideoBarEnterEasing)) +
                        slideInVertically(tween(340, easing = VideoBarEnterEasing)) { it / 4 },
                    exit = fadeOut(tween(480, easing = VideoBarExitEasing)) +
                        slideOutVertically(tween(520, easing = VideoBarExitEasing)) { it / 5 },
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)),
                                ),
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {}
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            HighlightedIconButton(
                                onClick = {
                                    controlsInteractionTick++
                                    seekBy(-SeekStepMs)
                                },
                                size = VideoTopBarButtonHeight,
                                modifier = Modifier.semantics { contentDescription = "快退 5 秒" },
                            ) {
                                Text(
                                    text = "-5s",
                                    color = LocalContentColor.current,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            HighlightedIconButton(
                                onClick = {
                                    controlsInteractionTick++
                                    togglePlayPause()
                                },
                                size = VideoTopBarButtonHeight,
                            ) {
                                Icon(
                                    imageVector = if (player.isPlaying) COUIIcons.Pause else COUIIcons.Play,
                                    contentDescription = if (player.isPlaying) "暂停" else "播放",
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            HighlightedIconButton(
                                onClick = {
                                    controlsInteractionTick++
                                    seekBy(SeekStepMs)
                                },
                                size = VideoTopBarButtonHeight,
                                modifier = Modifier.semantics { contentDescription = "快进 5 秒" },
                            ) {
                                Text(
                                    text = "+5s",
                                    color = LocalContentColor.current,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val displayPos = if (scrubbing) scrubMs else player.currentPositionMs
                            val durMs = player.durationMs
                            val effectiveDur = durMs.coerceAtLeast(1L)
                            Text(
                                text = formatVideoTime(displayPos),
                                color = Color.White,
                                fontSize = 12.sp,
                                modifier = Modifier.widthIn(min = 44.dp),
                            )
                            val intentState = when {
                                durMs <= 0L -> IntentSliderPlaybackState.LoadingInitial
                                player.isBuffering -> IntentSliderPlaybackState.LoadingBuffering
                                player.isPlaying -> IntentSliderPlaybackState.Playing
                                else -> IntentSliderPlaybackState.Paused
                            }
                            IntentSlider(
                                value = displayPos.coerceIn(0L, effectiveDur).toFloat(),
                                onValueChange = { next ->
                                    scrubbing = true
                                    scrubMs = next.roundToLong()
                                },
                                secondaryValue = player.bufferedPositionMs.coerceIn(0L, effectiveDur).toFloat(),
                                onValueChangeFinished = {
                                    player.seekTo(scrubMs)
                                    scrubbing = false
                                },
                                valueRange = 0f..effectiveDur.toFloat(),
                                enabled = durMs > 0L,
                                followSystemTheme = false,
                                materialBackdrop = false,
                                conicLight = true,
                                showThumb = false,
                                playbackState = intentState,
                                size = IntentSliderSize.Default,
                                colors = IntentSliderDefaults.fixedColors(
                                    conicLightColor = COUITheme.colorScheme.primary,
                                ),
                                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                            )
                            Text(
                                text = formatVideoTime(durMs),
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 12.sp,
                                modifier = Modifier.widthIn(min = 44.dp),
                                textAlign = TextAlign.End,
                            )
                        }
                    }
                }

                // 顶部统一材质胶囊提示（直接原地刷新数字，调节亮度/音量/进度零闪烁）
                val topToastInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout)
                val activeGestureHint = if (
                    centerSwipeEnabled &&
                    centerGestureSnapshot.direction != VideoCenterGestureDirection.None
                ) {
                    videoCenterGestureHintText(centerGestureSnapshot.direction, centerGestureSnapshot.armedAction)
                } else {
                    ""
                }
                val activeSpeedLockToast = !liveMode && (longPressSpeedActive || longPressSpeedLocked)
                val activeTopToastText = when {
                    activeGestureHint.isNotEmpty() -> activeGestureHint
                    activeSpeedLockToast -> if (longPressSpeedLocked) "3.0× 已锁定，长按解锁" else "3.0× 快进中，下滑锁定"
                    hudText.isNotEmpty() -> hudText
                    else -> ""
                }
                val activeTopToastLocked = activeGestureHint.isEmpty() && activeSpeedLockToast && longPressSpeedLocked
                var lastTopToastText by remember { mutableStateOf("") }
                var lastTopToastLocked by remember { mutableStateOf(false) }
                if (activeTopToastText.isNotEmpty()) {
                    lastTopToastText = activeTopToastText
                    lastTopToastLocked = activeTopToastLocked
                }
                val topToastVisible = activeTopToastText.isNotEmpty()
                val displayTopToastText = if (topToastVisible) activeTopToastText else lastTopToastText
                val displayTopToastLocked = if (topToastVisible) activeTopToastLocked else lastTopToastLocked

                AnimatedVisibility(
                    visible = topToastVisible,
                    enter = fadeIn(tween(160)),
                    exit = fadeOut(tween(240, easing = VideoBarExitEasing)),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(topToastInsets)
                        .padding(top = 56.dp),
                ) {
                    MediaMaterialPill(
                        height = 34.dp,
                        horizontalPadding = 14.dp,
                        onClick = if (displayTopToastLocked) {
                            { longPressSpeedLocked = false }
                        } else {
                            null
                        },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (displayTopToastLocked) {
                                Icon(
                                    imageVector = COUIIcons.Lock,
                                    contentDescription = "已锁定倍速",
                                    tint = COUITheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(5.dp))
                            }
                            Text(
                                text = displayTopToastText,
                                color = if (displayTopToastLocked) COUITheme.colorScheme.primary else LocalContentColor.current,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                            )
                        }
                    }
                }

                if (centerSwipeEnabled) {
                    VideoCenterGestureOverlay(
                        snapshot = centerGestureSnapshot,
                        anchorYPx = centerGestureAnchorYPx,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoCenterGestureOverlay(
    snapshot: VideoCenterGestureSnapshot,
    anchorYPx: Float,
    modifier: Modifier = Modifier,
) {
    val isVisible = snapshot.direction != VideoCenterGestureDirection.None
    var lastActiveSnapshot by remember { mutableStateOf(snapshot) }
    if (isVisible) {
        lastActiveSnapshot = snapshot
    }
    val overlayAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (isVisible) 180 else 280,
            easing = if (isVisible) VideoBarEnterEasing else VideoBarExitEasing,
        ),
        label = "videoCenterGestureAlpha",
    )
    if (!isVisible && overlayAlpha <= 0.001f) return

    val display = if (isVisible) snapshot else lastActiveSnapshot
    val density = LocalDensity.current
    val statusTopPx = WindowInsets.statusBars.union(WindowInsets.displayCutout).getTop(density).toFloat()
    val navBottomPx = WindowInsets.navigationBars.getBottom(density).toFloat()
    val primaryColor = COUITheme.colorScheme.primary

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val containerHPx = constraints.maxHeight.toFloat()
        val buttonSizePx = with(density) { 54.dp.toPx() }
        val buttonHalfPx = buttonSizePx / 2f

        when (display.direction) {
            VideoCenterGestureDirection.Up -> {
                val minUpAnchorY = statusTopPx + with(density) { 340.dp.toPx() }
                val maxUpAnchorY = (containerHPx - navBottomPx - with(density) { 48.dp.toPx() })
                    .coerceAtLeast(minUpAnchorY)
                val safeAnchorY = anchorYPx.coerceIn(minUpAnchorY, maxUpAnchorY)

                if (display.pipReveal > 0.001f) {
                    val pipTopPx = safeAnchorY - with(density) { 196.dp.toPx() } - buttonHalfPx
                    VideoGestureCircleButton(
                        icon = CoboIcons.PictureInPicture,
                        contentDescription = "系统画中画",
                        reveal = display.pipReveal,
                        overlayAlpha = overlayAlpha,
                        armed = display.armedAction == VideoCenterGestureAction.SystemPip,
                        armedFillColor = primaryColor,
                        floatOffsetPx = (1f - display.pipReveal) * with(density) { 22.dp.toPx() },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset { IntOffset(0, pipTopPx.roundToInt()) },
                    )
                }

                if (display.inAppReveal > 0.001f) {
                    val inAppTopPx = safeAnchorY - with(density) { 104.dp.toPx() } - buttonHalfPx
                    VideoGestureCircleButton(
                        icon = CoboIcons.FloatingWindow,
                        contentDescription = "应用内小窗",
                        reveal = display.inAppReveal,
                        overlayAlpha = overlayAlpha,
                        armed = display.armedAction == VideoCenterGestureAction.InAppFloating,
                        armedFillColor = primaryColor,
                        floatOffsetPx = (1f - display.inAppReveal) * with(density) { 18.dp.toPx() },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset { IntOffset(0, inAppTopPx.roundToInt()) },
                    )
                }
            }

            VideoCenterGestureDirection.Down -> {
                val minDownAnchorY = statusTopPx + with(density) { 108.dp.toPx() }
                val maxDownAnchorY = (containerHPx - navBottomPx - with(density) { 156.dp.toPx() })
                    .coerceAtLeast(minDownAnchorY)
                val safeAnchorY = anchorYPx.coerceIn(minDownAnchorY, maxDownAnchorY)

                if (display.closeReveal > 0.001f) {
                    val closeTopPx = safeAnchorY + with(density) { 116.dp.toPx() } - buttonHalfPx
                    VideoGestureCircleButton(
                        icon = COUIIcons.Close,
                        contentDescription = "关闭视频",
                        reveal = display.closeReveal,
                        overlayAlpha = overlayAlpha,
                        armed = display.armedAction == VideoCenterGestureAction.Close,
                        armedFillColor = Color(0xFFFF3B30).copy(alpha = 0.88f),
                        floatOffsetPx = -(1f - display.closeReveal) * with(density) { 18.dp.toPx() },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset { IntOffset(0, closeTopPx.roundToInt()) },
                    )
                }
            }

            VideoCenterGestureDirection.None -> Unit
        }
    }
}

@Composable
private fun VideoGestureCircleButton(
    icon: ImageVector,
    contentDescription: String,
    reveal: Float,
    overlayAlpha: Float,
    armed: Boolean,
    armedFillColor: Color,
    floatOffsetPx: Float,
    modifier: Modifier = Modifier,
) {
    val armedScale by animateFloatAsState(
        targetValue = if (armed) 1.18f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.72f),
        label = "videoGestureCircleScale",
    )
    val fillColor by animateColorAsState(
        targetValue = if (armed) armedFillColor else Color(0xFF1C1C1E).copy(alpha = 0.72f),
        animationSpec = tween(160),
        label = "videoGestureCircleFill",
    )
    val borderColor by animateColorAsState(
        targetValue = if (armed) armedFillColor.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.22f),
        animationSpec = tween(160),
        label = "videoGestureCircleBorder",
    )
    val baseScale = 0.72f + 0.28f * reveal.coerceIn(0f, 1f)
    val totalScale = baseScale * armedScale

    Box(
        modifier = modifier
            .size(54.dp)
            .graphicsLayer {
                alpha = reveal.coerceIn(0f, 1f) * overlayAlpha
                scaleX = totalScale
                scaleY = totalScale
                translationY = floatOffsetPx
            }
            .clip(CircleShape)
            .background(fillColor)
            .border(if (armed) 2.dp else 1.dp, borderColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
    }
}
