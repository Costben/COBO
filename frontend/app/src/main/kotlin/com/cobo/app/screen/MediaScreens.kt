package com.cobo.app.screen

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect as AndroidRect
import android.media.AudioManager
import android.os.Build
import android.os.Environment
import java.io.File
import java.security.MessageDigest
import android.provider.MediaStore
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as colorLerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.Shape
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.cobo.app.ui.FloatingVideoRequest
import com.cobo.app.ui.FloatingVideoSession
import com.cobo.app.ui.LocalFloatingVideoController
import com.cobo.app.ui.VideoCenterGestureAction
import com.cobo.app.ui.VideoCenterGestureAutoDismissMillis
import com.cobo.app.ui.VideoCenterGestureDirection
import com.cobo.app.ui.VideoCenterGestureSnapshot
import com.cobo.app.ui.VideoVerticalZone
import com.cobo.app.ui.classifyVideoVerticalZone
import com.cobo.app.ui.evaluateVideoCenterGesture
import com.cobo.app.ui.findActivity
import com.cobo.app.ui.isDarkTheme
import com.cobo.app.ui.shouldAutoCloseVideoOnComplete
import com.cobo.app.ui.shouldAutoFloatVideoOnBack
import com.cobo.app.ui.videoCenterGestureHintText
import io.github.suqi8.coui.kmp.theme.COUITheme
import coil3.BitmapImage
import coil3.compose.AsyncImage
import coil3.decode.DataSource
import coil3.imageLoader
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.maxBitmapSize
import coil3.size.Size
import com.cobo.app.App
import com.cobo.app.data.Api
import com.cobo.app.data.ImageBrowseQuality
import com.cobo.app.data.Pic
import com.cobo.app.data.ProfileWebFallbackException
import com.cobo.app.data.WEIBO_PROFILE_EDIT_URL
import com.cobo.app.ui.CoboIcons
import com.cobo.app.ui.CoboPredictiveBackHandler
import com.cobo.app.ui.ExpandEasing
import com.cobo.app.ui.ImageOrigins
import com.cobo.app.ui.LocalImageOrigins
import com.cobo.app.ui.LocalRouteTransition
import com.cobo.app.ui.LongImageAspect
import com.cobo.app.ui.NavState
import com.cobo.app.ui.Route
import com.cobo.app.ui.blurEffectsEnabled
import com.cobo.app.ui.isWeiboLiveLink
import com.cobo.app.ui.layerBackdropIf
import com.cobo.app.ui.openExternalUrl
import com.cobo.app.ui.resolveWeiboLink
import com.cobo.app.ui.ThumbBounds
import com.cobo.app.ui.unwrapWeiboLink
import com.cobo.app.ui.unwrapSinaUrl
import com.cobo.app.ui.components.AvatarCropDialog
import com.cobo.app.ui.components.CoboRefreshSpinner
import com.cobo.app.ui.components.ConfirmDialog
import com.cobo.app.ui.components.SaveHud
import com.cobo.app.ui.components.SaveImageItem
import com.cobo.app.ui.components.SaveImagesMenu
import com.cobo.app.ui.components.downloadMediaToFile
import com.cobo.app.ui.components.extractIntentFallbackUrl
import com.cobo.app.ui.components.liveMediaCacheFile
import com.cobo.app.ui.components.originalSize
import com.cobo.app.ui.components.rememberImageSaveState
import io.github.suqi8.coui.kmp.basic.BottomFloatingToolbarDefaults
import io.github.suqi8.coui.kmp.basic.CapsuleOutlineShape
import io.github.suqi8.coui.kmp.squircle.isSquircleEnabled
import io.github.suqi8.coui.kmp.basic.DropdownEntry
import io.github.suqi8.coui.kmp.basic.DropdownImpl
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton
import io.github.suqi8.coui.kmp.basic.HighlightedIconButtonDefaults
import io.github.suqi8.coui.kmp.basic.IntentSlider
import io.github.suqi8.coui.kmp.basic.IntentSliderDefaults
import io.github.suqi8.coui.kmp.basic.IntentSliderPlaybackState
import io.github.suqi8.coui.kmp.basic.IntentSliderSize
import io.github.suqi8.coui.kmp.basic.ListPopupColumn
import io.github.suqi8.coui.kmp.basic.ListPopupDefaults
import io.github.suqi8.coui.kmp.basic.MetaBallParticle
import io.github.suqi8.coui.kmp.basic.MetaBallSurface
import io.github.suqi8.coui.kmp.basic.SpotLightDefaults
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.couiTouchMotion
import io.github.suqi8.coui.kmp.basic.rememberCOUITouchMotionState
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor
import io.github.suqi8.coui.kmp.basic.rememberPreciseClickState
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.layerBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.icon.extended.Lock
import io.github.suqi8.coui.kmp.overlay.OverlayCascadingListPopup
import io.github.suqi8.coui.kmp.overlay.OverlayListPopup
import io.github.suqi8.coui.kmp.theme.LocalContentColor
import io.github.suqi8.coui.kmp.theme.LocalDismissState
import io.github.suqi8.coui.kmp.utils.CouiHapticEffect
import io.github.suqi8.coui.kmp.utils.rememberCouiHaptic
import io.github.suqi8.coui.kmp.icon.extended.Close
import io.github.suqi8.coui.kmp.icon.extended.Download
import io.github.suqi8.coui.kmp.icon.extended.Edit
import io.github.suqi8.coui.kmp.icon.extended.Pause
import io.github.suqi8.coui.kmp.icon.extended.Play
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private const val MaxZoom = 5f
private const val DoubleTapZoom = 2.5f

// A flick down a long strip carries on after the finger is lifted; below this speed it is just a
// slow drag and stops where it is.
private const val FlingMinVelocity = 350f

// Pinching below the resting size is allowed so the picture does not feel walled in, but only by
// this much and only at this fraction of the pinch's pace; letting go springs it back.
private const val MinZoom = 0.5f
private const val ReboundResistance = 0.5f

// Ceiling on a long strip's decoded pixels, so that a very long one cannot exhaust the heap.
private const val MaxDecodePixels = 12_000_000

// Below this the picture counts as unzoomed, so float residue from pinching never blocks a swipe.
private const val ZoomEpsilon = 0.01f

// Double tap and toggle zoom animation duration.
private const val ZoomMillis = 220

// 看图页与视频播放页控件统一使用与视频页相同的 36dp 高度与 HighlightedIconButton 材质体系。
private val MediaControlHeight = 36.dp

/**
 * The ramp the viewer's chrome sits on. A photo can be white right up to the status bar, and the
 * chips alone cannot darken the strip behind them, so the top of the picture gets the same
 * treatment as the reference viewer's gradient app bar: black at the screen edge, still present
 * under the bar, and gone by the time the fade band below the controls ends.
 */
private val ChromeScrim = Brush.verticalGradient(
    0f to Color.Black.copy(alpha = 0.55f),
    0.62f to Color.Black.copy(alpha = 0.32f),
    1f to Color.Transparent,
)

/** How far the scrim keeps fading below the controls before it reaches transparent. */
private val ScrimTail = 34.dp

/**
 * Samples the strip of the picture the top bar sits on. A letterboxed wide shot leaves the bar on
 * pure black, so the answer there is "dark backdrop, light glyphs"; a bright photo needs the
 * opposite, or the close button and the page counter disappear into it.
 */
private suspend fun topBandIsLight(
    context: Context,
    url: String,
    screenWidthDp: Int,
    screenHeightDp: Int,
): Boolean = runCatching {
    // Same key the viewer itself requests, so the sample rides the bitmap Coil already has.
    val request = ImageRequest.Builder(context)
        .data(url)
        .size(72)
        .build()
    val sampled = ((context.imageLoader.execute(request) as? SuccessResult)?.image as? BitmapImage)
        ?.bitmap ?: return false
    // A hardware bitmap refuses getPixel, so the strip is copied back to the CPU first.
    val bitmap = if (sampled.config == Bitmap.Config.HARDWARE) {
        sampled.copy(Bitmap.Config.ARGB_8888, false) ?: return false
    } else {
        sampled
    }
    val width = bitmap.width
    val height = bitmap.height
    if (width <= 0 || height <= 0) return false

    // Fit scaling in dp: a wide picture gets letterboxed and the bar lands on black.
    val fit = minOf(screenWidthDp.toFloat() / width, screenHeightDp.toFloat() / height)
    val top = (screenHeightDp - height * fit) / 2f
    val band = screenHeightDp * 0.11f
    if (top >= band) return false

    val y0 = ((0f - top) / fit).toInt().coerceIn(0, height - 1)
    val y1 = ((band - top) / fit).toInt().coerceIn(0, height - 1)
    var sum = 0.0
    var count = 0
    for (y in y0..y1) {
        for (x in 0 until width) {
            val color = bitmap.getPixel(x, y)
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            sum += (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
            count++
        }
    }
    count > 0 && sum / count > 0.62
}.getOrDefault(false)

/**
 * Viewer system bars.
 *
 * The gallery keeps the status bar up while the controls are up and drops it together with them, so
 * "hide the chrome" is one gesture that hides everything. Entering the viewer no longer hides the
 * bar behind the feed's back, which is what made the bar slide in on the way out.
 */
@Composable
private fun ViewerSystemBars(visible: Boolean) {
    val context = LocalContext.current
    val darkTheme = isDarkTheme()
    val window = remember(context) { context.findActivity()?.window }
    val controller = remember(window) {
        window?.let { WindowCompat.getInsetsController(it, it.decorView) }
    }
    LaunchedEffect(controller, visible) {
        controller ?: return@LaunchedEffect
        // The scrim is black whatever the photo does, so the bar's glyphs stay light instead of
        // following the picture's top edge.
        controller.isAppearanceLightStatusBars = false
        if (visible) {
            controller.show(WindowInsetsCompat.Type.statusBars())
        } else {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.statusBars())
        }
    }
    DisposableEffect(controller, darkTheme) {
        onDispose {
            // Hand the bar back exactly as the activity's edge-to-edge style would have it.
            controller?.let {
                it.isAppearanceLightStatusBars = !darkTheme
                it.show(WindowInsetsCompat.Type.statusBars())
            }
        }
    }
}

/**
 * 与视频页 [HighlightedIconButton] 完全统一材质的胶囊控件：复用 [HighlightedIconButtonDefaults]
 * 的填充色、边缘高光、暗色焦散/亮色投影、[couiTouchMotion] 聚光与按压缩放，用于图片页与视频页的胶囊按钮及提示。
 */
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
    val urls = effectivePics.map { it.large }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
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
    // 这条曲线只在宿主自己驱动进场时才有；没有它（比如直接渲染）就当作已经落定。
    val progress: () -> Float = remember(transition) { transition?.progress ?: { 1f } }
    val transitionOffset: () -> Offset = remember(transition) { transition?.offset ?: { Offset.Zero } }

    var chrome by remember { mutableStateOf(true) }
    var original by remember { mutableStateOf(App.settings.imageBrowseQuality == ImageBrowseQuality.Original) }
    var menu by remember { mutableStateOf(false) }
    var avatarMoreOpen by remember { mutableStateOf(false) }
    var pendingCropUri by remember { mutableStateOf<Uri?>(null) }
    var uploadingAvatar by remember { mutableStateOf(false) }
    var avatarFallbackMessage by remember { mutableStateOf<String?>(null) }
    val avatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) pendingCropUri = uri
    }
    val precise = rememberPreciseClickState()
    // 保存原图 / 一键保存原图与信息流共用同一套队列，HUD 和权限也一起管。
    val saver = rememberImageSaveState()

    val start = index.coerceIn(0, (urls.size - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(initialPage = start) { urls.size }
    val current = urls.getOrNull(pagerState.currentPage).orEmpty()

    val currentPic = effectivePics.getOrNull(pagerState.currentPage)
    val viewerOwner = remember { Any() }

    // 记下正在看的是哪一张：被它盖住的那枚缩略图角标要等这一页撤走再淡回来，别的缩略图不动。
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
    DisposableEffect(viewerOwner) { onDispose { origins.endViewing(viewerOwner) } }

    // Kept across page turns so the chrome does not flicker while the next sample lands.
    var light by remember { mutableStateOf(false) }
    LaunchedEffect(current) {
        light = withContext(Dispatchers.IO) {
            topBandIsLight(context, current, configuration.screenWidthDp, configuration.screenHeightDp)
        }
    }
    val ink = if (light) Color(0xFF1A1A1A) else Color.White
    val plate = if (light) Color.White else Color(0xFF141414)
    val plateAlpha = if (light) 0.62f else 0.46f

    ViewerSystemBars(visible = chrome)

    // 长按菜单打开时，优先拦截返回键以收起菜单
    BackHandler(enabled = menu) {
        menu = false
    }

    // 拖拽退出动画播放期间拦截返回键，防止重复退栈。
    BackHandler(enabled = !menu && frame.dismiss.exiting) { }

    // 菜单未展开且未处于拖拽退出时，受设置中「预测性返回手势」开关控制：
    // 开启时跟手随侧滑进度实时缩放退出，关闭时按普通返回抬手退出。
    if (!menu && !frame.dismiss.exiting) {
        CoboPredictiveBackHandler(nav)
    }

    val backdrop = rememberLayerBackdrop()

    Box(
        Modifier
            .fillMaxSize()
            .onPlaced { frame.host = it }
            .onSizeChanged { frame.dismiss.boxSize = it },
    ) {
        // 下面那一页要一直看得见，黑色底随进度或拖拽距离变化：画面是从缩略图长出来的，
        // 拖拽时也能透出背后的原页面。
        Box(
            Modifier
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
            modifier = Modifier
                .fillMaxSize()
                .layerBackdropIf(blurEffectsEnabled(), backdrop),
        ) { page ->
            // Only the visible page decodes at full resolution; preloading neighbours that way
            // would hold several full-size bitmaps at once.
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
                    videoFid = if (pagePic.isVideo) pagePic.videoFid else "",
                    videoStatusId = if (pagePic.isVideo) pagePic.videoStatusId else "",
                    videoTitle = if (pagePic.isVideo) pagePic.videoTitle else "",
                    thumbModel = origins.modelOf(urls[page]),
                    thumbCacheKey = origins.cacheKeyOf(urls[page]),
                    original = original,
                    preserveUrl = pagePic.preserveUrl,
                    isCurrent = pagerState.currentPage == page,
                    isSettledElsewhere = pagerState.settledPage != page,
                    canPage = urls.size > 1,
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
                    chromeVisible = chrome && !frame.dismiss.dragging && !frame.dismiss.exiting,
                    onTap = { if (!frame.dismiss.exiting) chrome = !chrome },
                    onLongPress = {
                        if (!pagePic.isVideo && !saver.saving && !frame.dismiss.exiting && !frame.dismiss.active) {
                            menu = true
                            chrome = true
                        }
                    },
                    onFeedback = saver::flash,
                    onFallbackVideoRoute = {
                        nav.push(
                            Route.Video(
                                url = pagePic.videoUrl,
                                title = pagePic.videoTitle,
                                isLive = isWeiboLiveLink(pagePic.videoUrl),
                                fid = pagePic.videoFid,
                                statusId = pagePic.videoStatusId,
                            ),
                        )
                    },
                    onDismissRelease = { velocity, _, _ ->
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

        if (chrome) {
            CompositionLocalProvider(LocalBackdrop provides if (blurEffectsEnabled()) backdrop else null) {
                // The bar is up now, so its inset is real; the cutout still has to be unioned in
                // for landscape and for devices that report them separately.
                val topInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout)
                val dismissChromeAlpha by animateFloatAsState(
                    targetValue = if (frame.dismiss.dragging || frame.dismiss.exiting) 0f else 1f,
                    animationSpec = tween(150),
                    label = "dismissChrome",
                )
                // 顶栏只在进场后半段出现、退场开头就消失，不会跟着画面一起缩小。
                // 底下的实况标记也走这条曲线，否则返回时它会留到动画结束才不见。
                val chromeFade: () -> Float = {
                    ((progress() - ChromeFadeStart) / (1f - ChromeFadeStart)).coerceIn(0f, 1f) * dismissChromeAlpha
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = chromeFade() }
                        .align(Alignment.TopCenter)
                        // Drawn before the insets and the bar's own padding, so the ramp starts at
                        // the very top of the screen and runs [ScrimTail] past the controls.
                        .background(ChromeScrim)
                        .windowInsetsPadding(topInsets)
                        .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = ScrimTail),
                ) {
                    Box(Modifier.align(Alignment.CenterStart)) {
                        HighlightedIconButton(
                            size = MediaControlHeight,
                            onClick = {
                                if (!frame.dismiss.exiting && !frame.dismiss.dragging) {
                                    frame.dismiss.cancelActive()
                                    nav.pop()
                                }
                            },
                        ) {
                            Icon(COUIIcons.Close, "关闭", Modifier.size(18.dp))
                        }
                    }

                    // Centred on the screen rather than tucked against the close button.
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

                    if (currentPic?.isVideo != true) {
                        Row(
                            modifier = Modifier.align(Alignment.CenterEnd),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MediaMaterialPill(
                                onClick = {
                                    if (!frame.dismiss.exiting && !frame.dismiss.dragging) {
                                        original = !original
                                        saver.flash(if (original) "已切换到原图" else "已切回标准尺寸")
                                    }
                                },
                            ) {
                                Text(
                                    "原图",
                                    color = if (original) COUITheme.colorScheme.primary else LocalContentColor.current,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            if (editableAvatar) {
                                val menuScheme = COUITheme.colorScheme
                                Box {
                                    HighlightedIconButton(
                                        size = MediaControlHeight,
                                        onClick = {
                                            if (!frame.dismiss.exiting && !frame.dismiss.dragging) {
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
                                                            avatarPicker.launch(
                                                                PickVisualMediaRequest(
                                                                    ActivityResultContracts.PickVisualMedia.ImageOnly,
                                                                ),
                                                            )
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
                                                            saver.saveOne(
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
                                HighlightedIconButton(
                                    size = MediaControlHeight,
                                    onClick = {
                                        if (!frame.dismiss.exiting && !frame.dismiss.dragging) {
                                            saver.saveOne(
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
                        }
                    }
                }

                // 实况标记（左下角）与声音开关（右下角）：与顶部控件共用同一套 HighlightedIconButton 胶囊材质。
                if (currentPic?.isLive == true) {
                    val liveSound = App.settings.livePhotoSound
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .graphicsLayer { alpha = chromeFade() }
                            .navigationBarsPadding()
                            .padding(start = 12.dp, bottom = 12.dp),
                    ) {
                        LivePill()
                    }
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .graphicsLayer { alpha = chromeFade() }
                            .navigationBarsPadding()
                            .padding(end = 12.dp, bottom = 12.dp),
                    ) {
                        HighlightedIconButton(
                            size = MediaControlHeight,
                            modifier = Modifier.testTag("live_photo_sound_toggle"),
                            onClick = {
                                if (!frame.dismiss.exiting && !frame.dismiss.dragging) {
                                    val next = !liveSound
                                    App.settings.applyLivePhotoSound(next)
                                    saver.flash(if (next) "已开启实况声音" else "已静音实况")
                                }
                            },
                        ) {
                            Icon(
                                imageVector = if (liveSound) CoboIcons.VolumeOn else CoboIcons.VolumeOff,
                                contentDescription = if (liveSound) "实况静音" else "开启实况声音",
                                modifier = Modifier.size(18.dp),
                            )
                        }
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

        // 长按呼出的就是 COUI 那个窗口下拉菜单，按手指位置弹在看图页之上。
        SaveImagesMenu(
            items = if (menu) saveItems else null,
            index = saveIndex,
            precise = precise,
            onDismiss = { menu = false },
            state = saver,
        )

        // 看图页自身的保存 / 切原图 / 静音提示浮字。
        SaveHud(saver)

        pendingCropUri?.let { cropUri ->
            AvatarCropDialog(
                sourceUri = cropUri,
                uploading = uploadingAvatar,
                onDismiss = { if (!uploadingAvatar) pendingCropUri = null },
                onConfirm = { bytes, cropWidth ->
                    if (!uploadingAvatar) {
                        uploadingAvatar = true
                        scope.launch {
                            runCatching {
                                App.repo.updateAvatar(bytes = bytes, cropWidth = cropWidth)
                            }.onSuccess { updated ->
                                pendingCropUri = null
                                if (updated.avatar.isNotBlank()) avatarOverride = updated.avatar
                                App.toast("头像已更新")
                            }.onFailure { err ->
                                if (err is ProfileWebFallbackException) {
                                    pendingCropUri = null
                                    avatarFallbackMessage = err.message
                                } else {
                                    App.toast(err.message ?: "更换头像失败")
                                }
                            }
                            uploadingAvatar = false
                        }
                    }
                },
            )
        }

        avatarFallbackMessage?.let { msg ->
            ConfirmDialog(
                title = "未能直接上传头像",
                message = "$msg。是否打开微博网页版编辑资料？",
                confirmText = "打开网页版",
                onDismiss = { avatarFallbackMessage = null },
                onConfirm = {
                    avatarFallbackMessage = null
                    nav.pop()
                    nav.push(Route.Web(WEIBO_PROFILE_EDIT_URL, "编辑资料"))
                },
            )
        }
    }
}

/** 看图页停住时顶栏左下角那颗「实况」胶囊，也是退场那枚标记的起点。 */
@Composable
private fun LivePill() {
    MediaMaterialPill {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(com.cobo.app.R.drawable.ic_live_photo),
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

/** 看图页打开时顺手下的静图和实况视频，与保存链路共用同一缓存路径，清缓存时一起删。 */
private fun mediaCacheFile(context: Context, url: String, ext: String): File =
    liveMediaCacheFile(context, url, ext)

private suspend fun cacheMedia(context: Context, url: String, dest: File): File? =
    downloadMediaToFile(url, dest)

private suspend fun cacheImageFile(context: Context, url: String): File? = withContext(Dispatchers.IO) {
    if (!url.startsWith("http://") && !url.startsWith("https://")) {
        return@withContext File(url).takeIf { it.exists() && it.length() > 0L }
    }
    val dest = mediaCacheFile(context, url, "img")
    if (dest.length() > 0L) return@withContext dest
    val fromDiskCache = runCatching {
        context.imageLoader.diskCache?.openSnapshot(url)?.use { snapshot ->
            val src = snapshot.data.toFile()
            if (src.length() > 0L) {
                val tmp = File(dest.parentFile, dest.name + ".part")
                src.copyTo(tmp, overwrite = true)
                if (!tmp.renameTo(dest)) tmp.copyTo(dest, overwrite = true).also { tmp.delete() }
                dest
            } else null
        }
    }.getOrNull()
    if (fromDiskCache != null) return@withContext fromDiskCache
    cacheMedia(context, url, dest)
}

@Composable
private fun ZoomableImage(
    url: String,
    live: String,
    videoUrl: String = "",
    videoFid: String = "",
    videoStatusId: String = "",
    videoTitle: String = "",
    thumbModel: String?,
    thumbCacheKey: MemoryCache.Key?,
    original: Boolean,
    preserveUrl: Boolean,
    isCurrent: Boolean,
    isSettledElsewhere: Boolean,
    canPage: Boolean,
    isPagerScrolling: () -> Boolean,
    canStartDismiss: () -> Boolean,
    progress: () -> Float,
    transitionOffset: () -> Offset,
    origins: ImageOrigins,
    frame: ViewerFrame,
    chromeVisible: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onFeedback: (String) -> Unit = {},
    onFallbackVideoRoute: () -> Unit = {},
    onDismissRelease: (Velocity, ComposeSize, Offset) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val currentCanPage by rememberUpdatedState(canPage)
    val currentIsPagerScrolling by rememberUpdatedState(isPagerScrolling)
    val currentCanStartDismiss by rememberUpdatedState(canStartDismiss)
    val currentOnDismissRelease by rememberUpdatedState(onDismissRelease)
    var box by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    // Whether the picture has been put at its resting position yet; until the box and the shape are
    // both known there is nothing to place it against.
    var placed by remember(url) { mutableStateOf(false) }
    // The animation running after the fingers leave: a flick's carry-on, or a rebound from a pinch
    // that went under the resting size.
    val glide = remember { mutableStateOf<Job?>(null) }
    val remote = url.startsWith("http://") || url.startsWith("https://")
    val targetUrl = if (original && !preserveUrl) originalSize(url) else url
    var loaded by remember(url) { mutableStateOf(false) }
    // 保留上一张已解码的 Painter 与缓存键，切原图或重解码时垫在下面，不出现空白闪烁。
    var settledPainter by remember(url) { mutableStateOf<Painter?>(null) }
    var lastCacheKey by remember(url) { mutableStateOf<MemoryCache.Key?>(null) }
    // 首帧同步从 Coil 内存缓存取缩略图 Bitmap，避免 AsyncImage 等布局测量导致 1~2 帧白/黑闪。
    val syncThumbPainter = remember(url, thumbCacheKey, context) {
        thumbCacheKey?.let { key ->
            val image = context.imageLoader.memoryCache?.get(key)?.image
            if (image is BitmapImage) BitmapPainter(image.bitmap.asImageBitmap()) else null
        }
    }
    var localImageFile by remember(url, targetUrl) {
        val pre = if (!remote) File(targetUrl).takeIf { it.exists() && it.length() > 0L }
        else mediaCacheFile(context, targetUrl, "img").takeIf { it.length() > 0L }
        mutableStateOf<File?>(pre)
    }
    // 开启自动播放时停稳后自动播一次并收起；播完或关闭开关时仅在手指长按时播，放大后手势归缩放层。
    val playback = remember(live) { LivePhotoPlaybackState() }
    val liveAutoPlay = App.settings.livePhotoAutoPlay
    val liveSound = App.settings.livePhotoSound
    val lifecycleOwner = LocalLifecycleOwner.current
    val transition = LocalRouteTransition.current
    // 第一帧出来之前不盖静图：PlayerView 默认是黑底，地址播不出来时整页会变成黑屏。
    var liveReady by remember(live) { mutableStateOf(false) }
    var liveFailed by remember(live) { mutableStateOf(false) }
    // 打开这一页就把静图和实况视频一起下进缓存；播的时候优先用本地文件。
    var liveFile by remember(live) { mutableStateOf<File?>(null) }
    LaunchedEffect(url, targetUrl, live, isCurrent) {
        if (!isCurrent) return@LaunchedEffect
        launch {
            localImageFile = cacheImageFile(context, targetUrl)
        }
        if (live.isNotBlank()) {
            liveFile = cacheMedia(context, live, mediaCacheFile(context, live, "mov"))
        }
    }
    val canLivePlay = liveFile != null &&
        !liveFailed &&
        isCurrent &&
        scale <= 1f + ZoomEpsilon &&
        !frame.dismiss.active &&
        !frame.dismiss.exiting &&
        transition?.exiting?.invoke() != true
    val playing = canLivePlay && playback.isPlaying
    val livePlayer = remember(liveFile) {
        val file = liveFile
        if (file == null) {
            null
        } else {
            ExoPlayer.Builder(context).build().apply {
                // 不主动抢占系统音频焦点，避免滑过实况图时打断后台音乐；仅按开关控制音量。
                volume = if (App.settings.livePhotoSound) 1f else 0f
                repeatMode = Player.REPEAT_MODE_OFF
                playWhenReady = false
                setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                addListener(object : Player.Listener {
                    override fun onRenderedFirstFrame() {
                        liveReady = true
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) {
                            playback.onPlaybackEnded()
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        liveFailed = true
                        playback.onInterrupted()
                        Log.e("CoboLive", "${error.errorCodeName} ${file.name}", error)
                    }
                })
                prepare()
            }
        }
    }
    LaunchedEffect(livePlayer, liveSound) {
        livePlayer?.volume = if (liveSound) 1f else 0f
    }
    DisposableEffect(livePlayer) {
        onDispose { livePlayer?.release() }
    }
    DisposableEffect(lifecycleOwner, playback) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                playback.onInterrupted()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(isCurrent) {
        if (!isCurrent) {
            playback.onLeftPage()
        }
    }
    LaunchedEffect(canLivePlay) {
        if (!canLivePlay) {
            playback.onInterrupted()
        }
    }

    // 混排图集里的视频条目：点击直接在当前看图页播放，不跳转新页面；
    // 播放按钮与视频 TextureView 同在视觉变换层内，拖拽返回时跟着预览图一起缩放归位。
    val hasInlineVideo = videoUrl.isNotBlank()
    var inlineVideoActive by remember(url, videoUrl) { mutableStateOf(false) }
    var inlineIsLive by remember(url, videoUrl) { mutableStateOf(hasInlineVideo && isWeiboLiveLink(videoUrl)) }
    var inlineResolved by remember(url, videoUrl, videoFid, videoStatusId) {
        mutableStateOf<ResolvedVideo?>(
            if (
                hasInlineVideo &&
                !inlineIsLive &&
                isDirectVideoStream(videoUrl) &&
                !shouldRefreshDirectStreamBeforePlay(videoUrl, videoFid, videoStatusId, false)
            ) {
                ResolvedVideo.DirectStream(videoUrl, referer = defaultRefererFor(videoUrl), isLive = false)
            } else {
                null
            },
        )
    }
    var inlineVideoPlaying by remember(url, videoUrl) { mutableStateOf(false) }
    var inlineVideoBuffering by remember(url, videoUrl) { mutableStateOf(false) }
    var inlinePlaybackState by remember(url, videoUrl) { mutableIntStateOf(Player.STATE_IDLE) }
    var inlinePlayWhenReady by remember(url, videoUrl) { mutableStateOf(false) }
    var inlineVideoReady by remember(url, videoUrl) { mutableStateOf(false) }
    var inlineVideoMuted by remember(url, videoUrl) { mutableStateOf(false) }
    var inlineVideoDurationMs by remember(url, videoUrl) { mutableLongStateOf(0L) }
    var inlineVideoPosMs by remember(url, videoUrl) { mutableLongStateOf(0L) }
    var inlineScrubbing by remember(url, videoUrl) { mutableStateOf(false) }
    var inlineScrubMs by remember(url, videoUrl) { mutableLongStateOf(0L) }
    var inlineRefreshedOnError by remember(url, videoUrl, videoFid, videoStatusId) { mutableStateOf(false) }
    var inlineRefreshingStream by remember(url, videoUrl, videoFid, videoStatusId) { mutableStateOf(false) }
    var inlinePendingSeekMs by remember(url, videoUrl, videoFid, videoStatusId) { mutableLongStateOf(0L) }
    val activeInlineStream = (inlineResolved as? ResolvedVideo.DirectStream)?.url.orEmpty()
    var currentInlineStreamUrl by remember(url, activeInlineStream) { mutableStateOf(activeInlineStream) }

    LaunchedEffect(hasInlineVideo, inlineVideoActive, videoUrl, videoFid, videoStatusId) {
        if (!hasInlineVideo || !inlineVideoActive || inlineResolved != null) return@LaunchedEffect
        val resolved = resolvePlayableVideo(
            rawUrl = videoUrl,
            cacheDir = context.cacheDir,
            isLiveHint = inlineIsLive,
            fid = videoFid,
            statusId = videoStatusId,
            titleHint = videoTitle,
        )
        when (resolved) {
            is ResolvedVideo.DirectStream -> {
                inlineIsLive = inlineIsLive || resolved.isLive
                inlineResolved = resolved
            }
            is ResolvedVideo.WebPage -> {
                inlineVideoActive = false
                onFallbackVideoRoute()
            }
            is ResolvedVideo.Unavailable -> {
                inlineVideoActive = false
                onFeedback(resolved.message)
            }
        }
    }

    val inlineFallbackUrl = (inlineResolved as? ResolvedVideo.DirectStream)?.fallbackUrl
    val inlineReferer = (inlineResolved as? ResolvedVideo.DirectStream)?.referer
    val inlinePlayer = remember(hasInlineVideo, inlineVideoActive, currentInlineStreamUrl, inlineReferer) {
        if (!hasInlineVideo || !inlineVideoActive || currentInlineStreamUrl.isBlank()) {
            null
        } else {
            val effectiveReferer = inlineReferer?.takeIf { it.isNotBlank() } ?: defaultRefererFor(currentInlineStreamUrl)
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
                    volume = if (inlineVideoMuted) 0f else 1f
                    repeatMode = Player.REPEAT_MODE_OFF
                    playWhenReady = true
                    setMediaItem(MediaItem.fromUri(currentInlineStreamUrl))
                    if (inlinePendingSeekMs > 0L && !inlineIsLive) {
                        seekTo(inlinePendingSeekMs)
                        inlinePendingSeekMs = 0L
                    }
                    prepare()
                }
        }
    }
    LaunchedEffect(inlinePlayer, inlineVideoMuted) {
        inlinePlayer?.volume = if (inlineVideoMuted) 0f else 1f
    }
    DisposableEffect(inlinePlayer, inlineFallbackUrl) {
        val player = inlinePlayer ?: run {
            inlinePlaybackState = Player.STATE_IDLE
            inlinePlayWhenReady = false
            inlineVideoPlaying = false
            inlineVideoBuffering = false
            return@DisposableEffect onDispose {}
        }
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                inlineVideoReady = true
            }

            override fun onIsPlayingChanged(playingNow: Boolean) {
                inlineVideoPlaying = playingNow
                inlinePlayWhenReady = player.playWhenReady
                inlinePlaybackState = player.playbackState
            }

            override fun onPlayWhenReadyChanged(playWhenReadyNow: Boolean, reason: Int) {
                inlinePlayWhenReady = playWhenReadyNow
                inlinePlaybackState = player.playbackState
                inlineVideoPlaying = player.isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                inlinePlaybackState = playbackState
                inlinePlayWhenReady = player.playWhenReady
                inlineVideoBuffering = playbackState == Player.STATE_BUFFERING
                inlineVideoPlaying = player.isPlaying
                inlineVideoDurationMs = player.duration.coerceAtLeast(0L)
                if (player.isCurrentMediaItemLive) inlineIsLive = true
                if (playbackState == Player.STATE_ENDED) {
                    player.pause()
                    player.seekTo(0L)
                    inlinePlayWhenReady = false
                    inlineVideoPlaying = false
                    inlineVideoPosMs = 0L
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val fallback = inlineFallbackUrl?.takeIf { it.isNotBlank() && it != currentInlineStreamUrl }
                if (fallback != null) {
                    currentInlineStreamUrl = fallback
                    return
                }
                if (
                    !inlineRefreshedOnError &&
                    !inlineRefreshingStream &&
                    shouldAttemptStreamRefreshOnError(error.errorCode) &&
                    canRefreshVideoSource(videoUrl, videoFid, videoStatusId, inlineIsLive)
                ) {
                    inlineRefreshedOnError = true
                    inlineRefreshingStream = true
                    inlineVideoBuffering = true
                    inlinePendingSeekMs = player.currentPosition.coerceAtLeast(0L)
                    scope.launch {
                        val fresh = refreshPlayableVideo(
                            rawUrl = videoUrl,
                            fid = videoFid,
                            statusId = videoStatusId,
                            titleHint = videoTitle,
                            cacheDir = context.cacheDir,
                            isLiveHint = inlineIsLive,
                        )
                        inlineRefreshingStream = false
                        if (fresh is ResolvedVideo.DirectStream && fresh.url.isNotBlank() && fresh.url != currentInlineStreamUrl) {
                            inlineIsLive = inlineIsLive || fresh.isLive
                            inlineResolved = fresh
                            currentInlineStreamUrl = fresh.url
                        } else {
                            inlinePendingSeekMs = 0L
                            inlineVideoActive = false
                            inlineVideoReady = false
                            inlinePlaybackState = Player.STATE_IDLE
                            inlinePlayWhenReady = false
                            inlineVideoPlaying = false
                            inlineVideoBuffering = false
                            onFeedback("视频播放失败（${error.errorCodeName}）")
                        }
                    }
                    return
                }
                inlineVideoActive = false
                inlineVideoReady = false
                inlinePlaybackState = Player.STATE_IDLE
                inlinePlayWhenReady = false
                inlineVideoPlaying = false
                inlineVideoBuffering = false
                onFeedback("视频播放失败（${error.errorCodeName}）")
            }
        }
        player.addListener(listener)
        inlinePlaybackState = player.playbackState
        inlinePlayWhenReady = player.playWhenReady
        inlineVideoPlaying = player.isPlaying
        inlineVideoBuffering = player.playbackState == Player.STATE_BUFFERING
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(inlinePlayer, inlineVideoActive) {
        val player = inlinePlayer ?: return@LaunchedEffect
        while (inlineVideoActive) {
            inlinePlaybackState = player.playbackState
            inlinePlayWhenReady = player.playWhenReady
            inlineVideoPlaying = player.isPlaying
            inlineVideoBuffering = player.playbackState == Player.STATE_BUFFERING
            inlineVideoDurationMs = player.duration.coerceAtLeast(0L)
            if (player.isCurrentMediaItemLive) inlineIsLive = true
            if (!inlineScrubbing) {
                inlineVideoPosMs = player.currentPosition.coerceAtLeast(0L)
            }
            delay(200)
        }
    }
    LaunchedEffect(isCurrent, inlinePlayer) {
        if (!isCurrent && inlinePlayer != null) {
            inlinePlayer.pause()
            inlineVideoPlaying = false
        }
    }
    DisposableEffect(lifecycleOwner, inlinePlayer) {
        val player = inlinePlayer ?: return@DisposableEffect onDispose {}
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                player.pause()
                inlineVideoPlaying = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val dismissingNow = frame.dismiss.active || frame.dismiss.exiting || transition?.exiting?.invoke() == true
    LaunchedEffect(dismissingNow, inlinePlayer) {
        if (dismissingNow && inlinePlayer != null && inlinePlayer.isPlaying) {
            inlinePlayer.pause()
            inlineVideoPlaying = false
        }
    }
    fun handleVideoPageTap() {
        val player = inlinePlayer
        when {
            !inlineVideoActive -> inlineVideoActive = true
            player == null -> onTap()
            inlineIsLive -> onTap()
            player.isPlaying -> {
                player.pause()
                inlineVideoPlaying = false
            }
            else -> {
                if (player.playbackState == Player.STATE_ENDED) player.seekTo(0L)
                player.play()
                inlineVideoPlaying = true
            }
        }
    }
    LaunchedEffect(
        live,
        liveAutoPlay,
        livePlayer,
        liveFailed,
        isCurrent,
        isSettledElsewhere,
        playback.autoPlayedForVisit,
    ) {
        if (live.isBlank() ||
            !liveAutoPlay ||
            livePlayer == null ||
            liveFailed ||
            !isCurrent ||
            isSettledElsewhere ||
            playback.autoPlayedForVisit
        ) {
            return@LaunchedEffect
        }
        snapshotFlow {
            progress() >= 0.98f &&
                !isPagerScrolling() &&
                !frame.dismiss.active &&
                !frame.dismiss.exiting &&
                transition?.exiting?.invoke() != true &&
                scale <= 1f + ZoomEpsilon
        }.first { it }
        playback.tryAutoPlay(enabled = true)
    }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val couiHaptic = rememberCouiHaptic()
    var edgeHit by remember(url) { mutableIntStateOf(0) }
    LaunchedEffect(livePlayer, playing, playback.holdSession) {
        val player = livePlayer ?: return@LaunchedEffect
        if (playing) {
            if (playback.holding) {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            }
            player.seekTo(0)
            player.play()
        } else {
            // 停在松手或播完这一帧上淡回静图。先跳回开头再淡出，第一帧会在过渡里闪一下。
            player.pause()
            delay(LiveFadeMillis.toLong())
            if (!playing) player.seekTo(0)
        }
    }
    // 整图自己的不透明度，让它盖着缩略图淡进来，而不是硬切一下。
    var reveal by remember(url) { mutableFloatStateOf(if (syncThumbPainter != null) 0f else 1f) }
    var skipNextFade by remember(url) { mutableStateOf(false) }

    val effectiveAspect = frame.aspects[url] ?: origins.aspectOf(url)
    // A long strip is decoded at the screen's width rather than fitted to it, so zooming in shows
    // detail instead of a stretched handful of pixels.
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }
    val decodeSize = effectiveAspect?.takeIf { it < LongImageAspect }?.let { aspect ->
        val width = screenWidthPx.coerceAtLeast(1)
        val height = (width / aspect).toInt().coerceAtLeast(1)
        coil3.size.Size(width, minOf(height, (MaxDecodePixels / width).coerceAtLeast(1)))
    }

    val model: Any = remember(url, targetUrl, original, context, decodeSize) {
        ImageRequest.Builder(context)
            .data(targetUrl.takeIf { remote } ?: targetUrl)
            .apply {
                (lastCacheKey ?: thumbCacheKey)?.let { placeholderMemoryCacheKey(it) }
                if (original) {
                    size(coil3.size.Size.ORIGINAL)
                    maxBitmapSize(coil3.size.Size(8192, 8192))
                } else if (decodeSize != null) {
                    size(decodeSize)
                    maxBitmapSize(coil3.size.Size(decodeSize.width, 8192))
                }
            }
            .build()
    }

    // The picture as laid out at rest; a picture whose shape is not known yet fills the box.
    fun shown(): ComposeSize {
        val aspect = effectiveAspect ?: return ComposeSize(box.width.toFloat(), box.height.toFloat())
        return restSize(box.width.toFloat(), box.height.toFloat(), aspect)
    }

    // How far the picture may be dragged from centre: what overhangs the box, or nothing. A long
    // strip overhangs the box above and below, which is what lets it scroll.
    fun limit(at: Float): Offset {
        val size = shown()
        return Offset(
            max(0f, (size.width * at - box.width) / 2f),
            max(0f, (size.height * at - box.height) / 2f),
        )
    }

    fun clamp(value: Offset, at: Float): Offset {
        val max = limit(at)
        return Offset(value.x.coerceIn(-max.x, max.x), value.y.coerceIn(-max.y, max.y))
    }

    // A long picture rests showing its top edge, so it is read from the start; every other picture
    // rests centred, with nothing to scroll.
    fun restPosition(): Offset = Offset(0f, limit(1f).y)

    // Zooming by [by] about [focus] keeps the point under it where it is.
    fun zoomedOffset(from: Offset, by: Float, focus: Offset): Offset {
        val centre = Offset(box.width / 2f, box.height / 2f)
        return from * by + (focus - centre) * (1f - by)
    }

    fun toggleZoom(focus: Offset) {
        val startScale = scale
        val startOffset = offset
        val targetScale = if (startScale > 1f + ZoomEpsilon) 1f else DoubleTapZoom
        // Coming back to rest keeps a long strip where it was scrolled to instead of jumping to
        // its top; for everything else there is only the centre to return to.
        val targetOffset = if (targetScale == 1f) {
            clamp(startOffset, 1f)
        } else {
            clamp(zoomedOffset(startOffset, targetScale / startScale, focus), targetScale)
        }
        scope.launch {
            animate(0f, 1f, animationSpec = tween(ZoomMillis)) { t, _ ->
                scale = lerp(startScale, targetScale, t)
                offset = Offset(lerp(startOffset.x, targetOffset.x, t), lerp(startOffset.y, targetOffset.y, t))
            }
        }
    }

    // A flick runs on after the finger, the way a scroll view's does; a slow drag stops where it is
    // let go.
    fun flingBy(velocity: Float) {
        val max = limit(scale).y
        if (max <= 0f || abs(velocity) < FlingMinVelocity) return
        if ((offset.y <= -max + 0.5f && velocity < 0f) || (offset.y >= max - 0.5f && velocity > 0f)) return
        glide.value?.cancel()
        glide.value = scope.launch {
            AnimationState(initialValue = offset.y, initialVelocity = velocity)
                .animateDecay(exponentialDecay(frictionMultiplier = 1.2f)) {
                    offset = Offset(offset.x, value.coerceIn(-max, max))
                    when {
                        value <= -max -> {
                            if (edgeHit != -1) {
                                edgeHit = -1
                                couiHaptic(CouiHapticEffect.EdgeList)
                            }
                            cancelAnimation()
                        }
                        value >= max -> {
                            if (edgeHit != 1) {
                                edgeHit = 1
                                couiHaptic(CouiHapticEffect.EdgeList)
                            }
                            cancelAnimation()
                        }
                        value > -max + 2f && value < max - 2f -> edgeHit = 0
                    }
                }
        }
    }

    // Pinched under the resting size, the picture goes straight back to it once the fingers leave:
    // a spring, but a critically damped one, so it never overshoots and comes back a second time.
    fun rebound() {
        val from = scale
        if (from >= 1f - ZoomEpsilon) return
        glide.value?.cancel()
        glide.value = scope.launch {
            val settleBack = spring<Float>(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium,
            )
            animate(from, 1f, animationSpec = settleBack) { value, _ ->
                scale = value
                offset = clamp(offset, value)
            }
        }
    }

    // The picture is put at its resting position as soon as its shape and the box are both known,
    // and pushed back inside the box whenever the shape the decoder reports refines the one the
    // address carried.
    LaunchedEffect(isCurrent, box, effectiveAspect) {
        if (box == IntSize.Zero || effectiveAspect == null) return@LaunchedEffect
        if (isCurrent && !placed) {
            offset = restPosition()
            placed = true
        } else if (placed) {
            offset = clamp(offset, scale)
        }
    }

    // Swiping to another picture puts this one back at rest. The page being swiped to is current
    // before it is settled too, so it is left alone until it is the one being left behind.
    LaunchedEffect(isCurrent, isSettledElsewhere) {
        if (isSettledElsewhere && !isCurrent) {
            glide.value?.cancel()
            scale = 1f
            offset = Offset.Zero
            placed = false
        }
    }

    // 只有屏幕上的这一页说了算：放大之后不再把画面映射回缩略图。
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
                    },
            ) {
                // The picture rests at the size it shows at. A long strip is taller than this box and
                // overhangs it, which is what the gesture layer below scrolls; every other picture
                // rests fitted inside the box.
                val resting = if (rest.height > box.height + 0.5f) {
                    Modifier.requiredSize(
                        with(density) { rest.width.toDp() },
                        with(density) { rest.height.toDp() },
                    )
                } else {
                    Modifier.fillMaxSize()
                }
                Box(Modifier.align(Alignment.Center).then(resting)) {
                    // 信息流里那张图先顶上：优先同步画出内存缓存里的缩略图，首帧零空白；
                    // 整图到了再盖上去，切原图时也用 settledPainter 垫底，不出现闪烁。
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
                            val picture = state.painter.intrinsicSize
                            if (picture.width > 0f && picture.height > 0f && url !in frame.aspects) {
                                frame.aspects[url] = picture.width / picture.height
                            }
                        },
                    )
                    val file = localImageFile
                    if (file != null && box != IntSize.Zero) {
                        TiledLongImageOverlay(
                            file = file,
                            original = original,
                            screenWidthPx = screenWidthPx,
                            box = box,
                            scale = scale,
                            offset = offset,
                            rest = rest,
                            onAspect = { aspect ->
                                if (url !in frame.aspects) frame.aspects[url] = aspect
                            },
                        )
                    }
                }
            }

            if (livePlayer != null) {
                val liveAlpha by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (playing && liveReady) 1f else 0f,
                    animationSpec = tween(LiveFadeMillis),
                    label = "live",
                )
                FittedVideoTextureOverlay(
                    player = livePlayer,
                    alpha = liveAlpha,
                    rest = shown(),
                    box = box,
                    scale = scale,
                    offset = offset,
                    keepScreenOn = playing,
                )
            }

            if (inlinePlayer != null) {
                val videoAlpha by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (inlineVideoActive && inlineVideoReady) 1f else 0f,
                    animationSpec = tween(LiveFadeMillis),
                    label = "inlineVideo",
                )
                val inlineKeepScreenOn = isCurrent &&
                    inlineVideoActive &&
                    !dismissingNow &&
                    shouldKeepVideoScreenOn(
                        isPlaying = inlineVideoPlaying,
                        playWhenReady = inlinePlayWhenReady,
                        playbackState = inlinePlaybackState,
                        refreshingStream = inlineRefreshingStream,
                    )
                FittedVideoTextureOverlay(
                    player = inlinePlayer,
                    alpha = videoAlpha,
                    rest = shown(),
                    box = box,
                    scale = scale,
                    offset = offset,
                    keepScreenOn = inlineKeepScreenOn,
                )
            }

            if (hasInlineVideo) {
                val showSpinner = inlineVideoActive && (!inlineVideoReady || inlineVideoBuffering)
                val showPlayBadge = !showSpinner && !inlineVideoPlaying
                if (showSpinner || showPlayBadge) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (showSpinner) {
                            CoboRefreshSpinner(size = 24.dp, strokeWidth = 2.5.dp, color = Color.White)
                        } else {
                            Icon(
                                imageVector = COUIIcons.Play,
                                contentDescription = "播放视频",
                                modifier = Modifier.size(34.dp),
                                tint = Color.White,
                            )
                        }
                    }
                }
            }
        }

        // 常态由顶栏那枚「实况」标记表达，这里只报下载和失败这两种临时状态。
        val liveStatus = if (live.isBlank()) {
            ""
        } else {
            when {
                liveFailed -> "实况播放失败"
                liveFile == null -> "实况下载中"
                else -> ""
            }
        }
        if (liveStatus.isNotEmpty() && chromeVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp)
                    .height(MediaControlHeight),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = liveStatus,
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }

        // Gestures sit on an untransformed full-screen layer so screen-space drag and fling velocity stay exact.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(live, hasInlineVideo, inlineVideoActive, inlineIsLive, inlinePlayer) {
                    if (live.isBlank()) {
                        detectTapGestures(
                            onTap = {
                                if (!frame.dismiss.active && !frame.dismiss.exiting) {
                                    if (hasInlineVideo) handleVideoPageTap() else onTap()
                                }
                            },
                            onDoubleTap = {
                                if (!frame.dismiss.active && !frame.dismiss.exiting && !hasInlineVideo) {
                                    toggleZoom(it)
                                }
                            },
                            onLongPress = { if (!frame.dismiss.active && !frame.dismiss.exiting) onLongPress() },
                        )
                    } else {
                        detectLivePress(
                            onTap = { if (!frame.dismiss.active && !frame.dismiss.exiting) onTap() },
                            onDoubleTap = { if (!frame.dismiss.active && !frame.dismiss.exiting) toggleZoom(it) },
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
                            // 手指已经在滑了，这次按住不算实况。
                            playback.onLongPressEnd()
                            if (frame.dismiss.active && !frame.dismiss.exiting) {
                                frame.dismiss.animJob?.cancel()
                            }
                        },
                        onRelease = { velocity, taken ->
                            if (mode == GestureMode.DISMISS || (frame.dismiss.active && !frame.dismiss.exiting)) {
                                frame.dismiss.pendingImageOffset = offset
                                currentOnDismissRelease(velocity, shown(), offset)
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
                                // 回弹中途被手指再次接住，按当前落点重算锚点，避免画面跳变。
                                mode = GestureMode.DISMISS
                                frame.dismiss.reanchor(focus)
                                frame.dismiss.dragging = true
                            } else if (pointers >= 2 || scale > 1f + ZoomEpsilon) {
                                mode = GestureMode.ZOOM_PAN
                            } else {
                                val moved = clamp(zoomedOffset(offset, 1f, focus) + pan, scale)
                                val longStripScrolls = abs(direction.y) > abs(direction.x) && moved.y != offset.y
                                if (longStripScrolls) {
                                    mode = GestureMode.ZOOM_PAN
                                } else if (
                                    currentCanPage &&
                                    (currentIsPagerScrolling() || abs(direction.x) > abs(direction.y) * 1.25f)
                                ) {
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
                                // Shrinking past the resting size only creeps along, so the picture can go
                                // under the screen's width but feels held; letting go springs it back.
                                val next = if (target < current && current <= 1f) {
                                    current + (target - current) * ReboundResistance
                                } else {
                                    target
                                }
                                val moved = clamp(zoomedOffset(offset, next / current, focus) + pan, next)
                                val maxY = limit(next).y
                                if (maxY > 0f) {
                                    when {
                                        moved.y >= maxY - 0.5f && pan.y > 0f -> {
                                            if (edgeHit != 1) {
                                                edgeHit = 1
                                                couiHaptic(CouiHapticEffect.EdgeList)
                                            }
                                        }
                                        moved.y <= -maxY + 0.5f && pan.y < 0f -> {
                                            if (edgeHit != -1) {
                                                edgeHit = -1
                                                couiHaptic(CouiHapticEffect.EdgeList)
                                            }
                                        }
                                        moved.y > -maxY + 2f && moved.y < maxY - 2f -> edgeHit = 0
                                    }
                                }
                                // A one-finger drag belongs to the picture only where it can actually
                                // travel — up and down a long strip, or around a zoomed one. Across a
                                // picture with nothing to show there, the swipe is the pager's.
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

        if (hasInlineVideo && inlineVideoActive && inlinePlayer != null && chromeVisible && !frame.dismiss.dragging && !frame.dismiss.exiting) {
            val effectiveDur = inlineVideoDurationMs.coerceAtLeast(1L)
            val displayPos = if (inlineScrubbing) inlineScrubMs else inlineVideoPosMs
            val intentState = when {
                inlineVideoDurationMs <= 0L -> IntentSliderPlaybackState.LoadingInitial
                inlineVideoBuffering -> IntentSliderPlaybackState.LoadingBuffering
                inlineVideoPlaying -> IntentSliderPlaybackState.Playing
                else -> IntentSliderPlaybackState.Paused
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HighlightedIconButton(
                    size = MediaControlHeight,
                    onClick = { inlineVideoMuted = !inlineVideoMuted },
                ) {
                    Icon(
                        imageVector = if (inlineVideoMuted) CoboIcons.VolumeOff else CoboIcons.VolumeOn,
                        contentDescription = if (inlineVideoMuted) "取消静音" else "静音",
                        modifier = Modifier.size(18.dp),
                    )
                }
                if (!inlineIsLive) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = formatVideoTime(displayPos),
                        color = Color.White,
                        fontSize = 12.sp,
                    )
                    IntentSlider(
                        value = displayPos.coerceIn(0L, effectiveDur).toFloat(),
                        onValueChange = { next ->
                            inlineScrubbing = true
                            inlineScrubMs = next.roundToLong()
                        },
                        onValueChangeFinished = {
                            inlinePlayer.seekTo(inlineScrubMs)
                            inlineVideoPosMs = inlineScrubMs
                            inlineScrubbing = false
                        },
                        valueRange = 0f..effectiveDur.toFloat(),
                        enabled = inlineVideoDurationMs > 0L,
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
                        text = formatVideoTime(inlineVideoDurationMs),
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

/**
 * 实况与混排内联视频共用的 TextureView 浮层：跟随视觉层一起缩放与拖拽，自身不抢触摸，
 * 按静态封面尺寸居中裁切并随退场缩放动画一起缩回原缩略图。
 */
@Composable
private fun BoxScope.FittedVideoTextureOverlay(
    player: ExoPlayer,
    alpha: Float,
    rest: ComposeSize,
    box: IntSize,
    scale: Float,
    offset: Offset,
    keepScreenOn: Boolean = false,
) {
    val density = LocalDensity.current
    val fitted = if (rest.width > 0f && rest.height > 0f && box != IntSize.Zero) {
        Modifier
            .requiredSize(
                with(density) { rest.width.toDp() },
                with(density) { rest.height.toDp() },
            )
            .align(Alignment.Center)
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
            .clipToBounds()
    } else {
        Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha }
    }
    AndroidView(
        factory = { ctx ->
            playerView(ctx, player, controls = false).apply {
                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                this.keepScreenOn = keepScreenOn
            }
        },
        modifier = fitted,
        update = { view ->
            view.player = player
            if (view.keepScreenOn != keepScreenOn) {
                view.keepScreenOn = keepScreenOn
            }
            val format = player.videoFormat
            if (format != null && format.height > 0) {
                val turned = format.rotationDegrees % 180 != 0
                view.findViewById<androidx.media3.ui.AspectRatioFrameLayout>(
                    androidx.media3.ui.R.id.exo_content_frame,
                )?.setAspectRatio(
                    if (turned) format.height.toFloat() / format.width
                    else format.width.toFloat() / format.height,
                )
            }
        },
        onRelease = { view ->
            view.keepScreenOn = false
            view.player = null
        },
    )
}

private const val RegionTilePx = 2048
private const val MaxCachedRegionTiles = 16

private data class TileKey(val col: Int, val row: Int, val sampleSize: Int)

/**
 * 超过 GPU 单张纹理上限（4096px）的超长图 / 超大图分块渲染层：
 * 用 [BitmapRegionDecoder] 只把当前可视区域附近的 2048px 切片按原图或屏幕宽度解码出来，
 * 既不会受 Coil 默认 4096 上限压缩变糊，也不会爆硬件加速纹理尺寸或内存。
 */
@Composable
private fun TiledLongImageOverlay(
    file: File,
    original: Boolean,
    screenWidthPx: Int,
    box: IntSize,
    scale: Float,
    offset: Offset,
    rest: ComposeSize,
    onAspect: (Float) -> Unit,
) {
    var bounds by remember(file) { mutableStateOf<Pair<Int, Int>?>(null) }
    LaunchedEffect(file) {
        val measured = withContext(Dispatchers.IO) {
            runCatching {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, opts)
                if (opts.outWidth > 0 && opts.outHeight > 0) opts.outWidth to opts.outHeight else null
            }.getOrNull()
        }
        bounds = measured
        if (measured != null) {
            onAspect(measured.first.toFloat() / measured.second.toFloat())
        }
    }

    val (imgW, imgH) = bounds ?: return
    val isLong = imgW.toFloat() / imgH < LongImageAspect
    val needsTiling = (imgH > 4096 || imgW > 4096) && (original || isLong)
    if (!needsTiling) return

    val sampleSize = remember(imgW, original, screenWidthPx) {
        if (original) {
            1
        } else {
            var s = 1
            val targetW = screenWidthPx.coerceAtLeast(1)
            while (imgW / (s * 2) >= targetW) s *= 2
            s
        }
    }

    val decoderHolder = remember(file) { arrayOfNulls<BitmapRegionDecoder>(1) }
    var decoderReady by remember(file) { mutableStateOf(false) }
    val decoderMutex = remember(file) { Mutex() }
    val tiles = remember(file, sampleSize) { mutableStateMapOf<TileKey, androidx.compose.ui.graphics.ImageBitmap>() }
    val lruOrder = remember(file, sampleSize) { ArrayList<TileKey>() }

    DisposableEffect(file) {
        onDispose {
            decoderHolder[0]?.recycle()
            decoderHolder[0] = null
        }
    }

    LaunchedEffect(file) {
        val dec = withContext(Dispatchers.IO) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    file.inputStream().use { BitmapRegionDecoder.newInstance(it) }
                } else {
                    @Suppress("DEPRECATION")
                    file.inputStream().use { BitmapRegionDecoder.newInstance(it, false) }
                }
            }.getOrNull()
        }
        decoderHolder[0] = dec
        decoderReady = dec != null
    }

    if (!decoderReady || rest.width <= 0f || rest.height <= 0f) return

    val cols = (imgW + RegionTilePx - 1) / RegionTilePx
    val rows = (imgH + RegionTilePx - 1) / RegionTilePx
    val visLeft = (0f - box.width / 2f - offset.x) / scale + rest.width / 2f
    val visRight = (box.width - box.width / 2f - offset.x) / scale + rest.width / 2f
    val visTop = (0f - box.height / 2f - offset.y) / scale + rest.height / 2f
    val visBottom = (box.height - box.height / 2f - offset.y) / scale + rest.height / 2f

    val srcLeft = (visLeft / rest.width * imgW).toInt().coerceIn(0, imgW)
    val srcRight = (visRight / rest.width * imgW).toInt().coerceIn(0, imgW)
    val srcTop = (visTop / rest.height * imgH).toInt().coerceIn(0, imgH)
    val srcBottom = (visBottom / rest.height * imgH).toInt().coerceIn(0, imgH)

    val minCol = (srcLeft / RegionTilePx - 1).coerceIn(0, cols - 1)
    val maxCol = (srcRight / RegionTilePx + 1).coerceIn(0, cols - 1)
    val minRow = (srcTop / RegionTilePx - 1).coerceIn(0, rows - 1)
    val maxRow = (srcBottom / RegionTilePx + 1).coerceIn(0, rows - 1)

    val needed = remember(minCol, maxCol, minRow, maxRow, sampleSize) {
        buildList {
            for (r in minRow..maxRow) {
                for (c in minCol..maxCol) {
                    add(TileKey(c, r, sampleSize))
                }
            }
        }
    }

    LaunchedEffect(decoderReady, needed, sampleSize) {
        val decoder = decoderHolder[0] ?: return@LaunchedEffect
        for (key in needed) {
            if (key in tiles) {
                lruOrder.remove(key)
                lruOrder.add(key)
                continue
            }
            val decoded = withContext(Dispatchers.IO) {
                decoderMutex.withLock {
                    if (decoder.isRecycled) return@withLock null
                    runCatching {
                        val sx0 = key.col * RegionTilePx
                        val sy0 = key.row * RegionTilePx
                        val sx1 = minOf(imgW, sx0 + RegionTilePx)
                        val sy1 = minOf(imgH, sy0 + RegionTilePx)
                        val opts = BitmapFactory.Options().apply {
                            inSampleSize = key.sampleSize
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                        }
                        decoder.decodeRegion(AndroidRect(sx0, sy0, sx1, sy1), opts)?.asImageBitmap()
                    }.getOrNull()
                }
            }
            if (decoded != null) {
                tiles[key] = decoded
                lruOrder.remove(key)
                lruOrder.add(key)
                while (lruOrder.size > MaxCachedRegionTiles) {
                    val oldest = lruOrder.removeAt(0)
                    if (oldest !in needed) tiles.remove(oldest)
                }
            }
        }
    }

    Canvas(Modifier.fillMaxSize()) {
        val fitted = fitSize(size.width, size.height, imgW.toFloat() / imgH.toFloat())
        val left0 = (size.width - fitted.width) / 2f
        val top0 = (size.height - fitted.height) / 2f
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val bmp = tiles[TileKey(c, r, sampleSize)] ?: continue
                val sx0 = c * RegionTilePx
                val sy0 = r * RegionTilePx
                val sx1 = minOf(imgW, sx0 + RegionTilePx)
                val sy1 = minOf(imgH, sy0 + RegionTilePx)
                val dx0 = (left0 + sx0.toFloat() / imgW * fitted.width).roundToInt()
                val dy0 = (top0 + sy0.toFloat() / imgH * fitted.height).roundToInt()
                val dx1 = (left0 + sx1.toFloat() / imgW * fitted.width).roundToInt()
                val dy1 = (top0 + sy1.toFloat() / imgH * fitted.height).roundToInt()
                drawImage(
                    image = bmp,
                    dstOffset = IntOffset(dx0, dy0),
                    dstSize = IntSize((dx1 - dx0).coerceAtLeast(1), (dy1 - dy0).coerceAtLeast(1)),
                )
            }
        }
    }
}

/**
 * Pinch and drag detection that only consumes what it uses. [onGesture] gets the centroid, the pan,
 * the zoom factor and the number of fingers down, and returns whether it took the movement; when it
 * did not, the pointer events stay free for a parent such as the pager. [onStart] runs as the
 * fingers land; [onRelease] once they are all up, with the speed the last one left at and whether
 * the gesture took anything at all.
 */
/**
 * 单张实况图在看图页当次访问中的播放状态机：
 * - 开启自动播放时，页面停稳后触发一次播放，播完自动收起且不再循环；
 * - 播完（或被打断）后留在当前页只能靠长按重新触发；
 * - 划离当前页后重置访问标记，再次划回可重新自动播放一次。
 */
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

/** 按住这么久、期间手指没滑开，才算要播实况。短于左右滑的判断，长于一次点按。 */
private const val LiveHoldMillis = 280L

/** 实况盖上静图、以及松手回到静图，两边用同一段淡入淡出。 */
private const val LiveFadeMillis = 600

/**
 * 实况图的点按：点一下切换顶栏，连点两下放大。按住 [LiveHoldMillis] 且手指没离开、
 * 也没滑过触摸阈值，才回调 [onPlay]；一松手或一开始滑就 [onStop]。
 */
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
            // 很快抬起来的是点击。两次落在双击间隔里，交给放大。
            val second = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
                val next = awaitFirstDown(requireUnconsumed = false)
                next.position
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

/** 顶栏进场只在最后这一段出现，退场开头就消失，不跟着画面一起缩。 */
private const val ChromeFadeStart = 0.6f

/** 整图淡入、缩略图淡出用掉的时长，两边都不会硬切。 */
private const val LoadFadeMillis = 200

/** 拖拽退出时的最小缩放比例（对齐相册下拉退出的 0.7×）。 */
private const val DragMinScale = 0.7f

/** 拖拽缩放曲线：跟手即刻开始缩小，越拖越柔和（对齐相册 PathInterpolator(0, 0, 0.4, 1)）。 */
private val DragScaleEasing = CubicBezierEasing(0f, 0f, 0.4f, 1f)

private const val LongImageScrollEpsilon = 8f
private const val LongImageScrollEnd = 0.72f
private const val LongImageShrinkStart = 0.18f

/**
 * 长图退场（[exitProgress] 0→1）滚动阶段进度：平滑滚动到缩略图对应的中心裁切位置，
 * 与外层动画曲线保持一致不额外叠加缓动，避免在起始段跑得过快。
 */
internal fun longImageScrollFactor(exitProgress: Float, needsScroll: Boolean): Float {
    if (!needsScroll) return 1f
    return (exitProgress / LongImageScrollEnd).coerceIn(0f, 1f)
}

/**
 * 长图退场（[exitProgress] 0→1）缩放阶段进度：滚动到位前后平滑接力缩小，无视口拉伸。
 */
internal fun longImageShrinkFactor(exitProgress: Float, needsScroll: Boolean): Float {
    if (!needsScroll) return exitProgress.coerceIn(0f, 1f)
    return ((exitProgress - LongImageShrinkStart) / (1f - LongImageShrinkStart)).coerceIn(0f, 1f)
}

/** 拖拽退出时手势与背景状态：支持任意方向拖动与甩出退出，取消时平滑回弹、退出时接力回到原缩略图位置。 */
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
            // 无原缩略图或不可见原位时直接飞出淡出，此时恢复底层缩略图避免淡出时出现空洞。
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

/** 一页图画自己的进场要用到、又不属于任何一页的东西。 */
@Stable
private class ViewerFrame {
    var host: LayoutCoordinates? = null

    /** 屏幕上这张图是否已经放大。 */
    var zoomed by mutableStateOf(false)

    /** 拖拽/甩出退出的共享状态。 */
    val dismiss = DragDismissState()

    /** 每张图自己的宽高比，等它解码出来才知道。 */
    val aspects = mutableStateMapOf<String, Float>()

    /** 每张缩略图最后一次出现的位置，页面不再组合时（比如滚走了）退场还要用它。 */
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

/**
 * 把整层从缩略图摆到整屏。普通图缩略图居中裁切，长图缩略图从顶部对齐裁切，
 * 所以退场滚回顶部即可直接与长图顶部缩略图无缝重合；进场时从顶部缩略图直接展开。
 * 没有缩略图或还不知道图片本身的比例时，只淡入并轻微放大，绝不从错误的位置长出来。
 */
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

/** 一张比例为 [aspect]（宽比高）的图放在盒子里、完整放得下时的尺寸。 */
private fun fitSize(boxWidth: Float, boxHeight: Float, aspect: Float): ComposeSize =
    if (aspect > boxWidth / boxHeight) {
        ComposeSize(boxWidth, boxWidth / aspect)
    } else {
        ComposeSize(boxHeight * aspect, boxHeight)
    }

private fun restSize(boxWidth: Float, boxHeight: Float, aspect: Float): ComposeSize =
    if (aspect < LongImageAspect) ComposeSize(boxWidth, boxWidth / aspect) else fitSize(boxWidth, boxHeight, aspect)

/**
 * 两种播放器：[controls] 为真是普通视频，底下带进度条和播放控件；
 * 为假是实况，只有画面。
 *
 * 实况不能只在建好之后再关掉：播放器一切到可播，PlayerView 会自己把控件叫出来再淡掉，
 * 就是按住时闪的那一下。所以无控件的那套默认就藏住，并且不再自动弹出。
 */
private fun playerView(context: Context, player: ExoPlayer, controls: Boolean): PlayerView =
    (if (controls) {
        PlayerView(context)
    } else {
        // 代码里建出来的是 SurfaceView，不吃父层透明度，淡入淡出会变成整块闪现。
        // 实况走布局里指定的 TextureView，透明度才能一帧帧变。
        android.view.LayoutInflater.from(context)
            .inflate(com.cobo.app.R.layout.live_player_view, null) as PlayerView
    }).apply {
        if (!controls) {
            useController = false
            controllerAutoShow = false
            setControllerShowTimeoutMs(0)
            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
            hideController()
            setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
            setEnableComposeSurfaceSyncWorkaround(true)
        }
        this.player = player
        if (controls) {
            useController = true
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
        } else {
            hideController()
            player.addListener(object : androidx.media3.common.Player.Listener {
                override fun onEvents(
                    player: androidx.media3.common.Player,
                    events: androidx.media3.common.Player.Events,
                ) {
                    hideController()
                }
            })
        }
    }

private val VideoTopBarButtonHeight = MediaControlHeight

internal const val VideoControlsAutoHideDelayMillis = 3_000L

private val VideoBarEnterEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
private val VideoBarExitEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

internal fun shouldAutoHideVideoControls(
    controlsVisible: Boolean,
    isPlaying: Boolean,
    speedMenu: Boolean,
    isScrubbing: Boolean,
    systemPipActive: Boolean,
): Boolean = controlsVisible && isPlaying && !speedMenu && !isScrubbing && !systemPipActive

private const val SeekStepMs = 5_000L

private val Speeds = listOf(1f, 1.25f, 1.5f, 2f, 3f)

internal fun formatVideoTime(ms: Long): String {
    val totalSec = ms.coerceAtLeast(0L) / 1000L
    val h = totalSec / 3600L
    val m = (totalSec % 3600L) / 60L
    val s = totalSec % 60L
    return if (h > 0L) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

internal fun calculateHorizontalScrubTargetMs(
    startPosMs: Long,
    durationMs: Long,
    draggedPx: Float,
    containerWidthPx: Float,
): Long {
    if (durationMs <= 0L || containerWidthPx <= 0f) return startPosMs.coerceAtLeast(0L)
    val spanMs = minOf(durationMs, 90_000L)
    val deltaMs = (draggedPx / containerWidthPx * spanMs).roundToLong()
    return (startPosMs + deltaMs).coerceIn(0L, durationMs)
}

private fun speedLabel(value: Float): String = when (value) {
    1f -> "1.0×"
    1.25f -> "1.25×"
    1.5f -> "1.5×"
    2f -> "2.0×"
    else -> "3.0×"
}

/** 与 [HighlightedIconButton] 共用同一套 [MediaMaterialPill] 材质的胶囊按钮，用于视频倍速切换。 */
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

@Composable
fun VideoScreen(
    nav: NavState,
    url: String,
    title: String,
    isLive: Boolean = false,
    fid: String = "",
    statusId: String = "",
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val floatingController = LocalFloatingVideoController.current
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
    val canRefresh = remember(url, fid, statusId, liveRequested) {
        canRefreshVideoSource(url, fid, statusId, liveRequested)
    }
    var downloading by remember { mutableStateOf(false) }
    var pendingDownloadAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var hud by remember { mutableStateOf("") }
    var hudTick by remember { mutableIntStateOf(0) }

    fun flash(text: String) {
        hud = text
        hudTick++
    }

    LaunchedEffect(hudTick, downloading) {
        if (hud.isNotEmpty() && !downloading) {
            delay(1400)
            hud = ""
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val action = pendingDownloadAction
        pendingDownloadAction = null
        if (granted) action?.invoke() else flash("没有存储权限")
    }

    fun runWithStoragePermission(action: () -> Unit) {
        val needsLegacyGrant = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) != PackageManager.PERMISSION_GRANTED
        if (needsLegacyGrant) {
            pendingDownloadAction = action
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            action()
        }
    }

    fun startVideoDownload(videoUrl: String, referer: String?) {
        if (downloading) return
        if (videoUrl.isBlank()) {
            flash("未检测到可下载的视频流，请先点击播放后再试")
            return
        }
        runWithStoragePermission {
            scope.launch {
                downloading = true
                var downloadUrl = videoUrl
                var downloadReferer = referer
                if (isSignedVideoUrlExpired(downloadUrl) && canRefresh) {
                    hud = "正在刷新视频链接…"
                    val fresh = refreshPlayableVideo(
                        rawUrl = url,
                        fid = fid,
                        statusId = statusId,
                        titleHint = title,
                        cacheDir = context.cacheDir,
                        isLiveHint = liveRequested,
                    )
                    if (fresh is ResolvedVideo.DirectStream && fresh.url.isNotBlank()) {
                        downloadUrl = fresh.url
                        downloadReferer = fresh.referer
                        resolved = fresh
                    }
                }
                val result = downloadVideoToGallery(context, downloadUrl, downloadReferer) { statusText ->
                    hud = statusText
                }
                downloading = false
                result.onSuccess { flash("已保存视频到相册") }
                    .onFailure {
                        Log.e("CoboVideo", "download failed url=$downloadUrl", it)
                        flash(it.message ?: "视频下载失败")
                    }
            }
        }
    }

    LaunchedEffect(url, liveRequested, fid, statusId) {
        if (resolved == null) {
            resolved = resolvePlayableVideo(
                rawUrl = url,
                cacheDir = context.cacheDir,
                isLiveHint = liveRequested,
                fid = fid,
                statusId = statusId,
                titleHint = title,
            )
        }
    }

    when (val current = resolved) {
        null -> {
            CoboPredictiveBackHandler(nav)
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CoboRefreshSpinner()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (liveRequested) "正在连接直播间…" else "正在解析视频…",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HighlightedIconButton(onClick = { nav.pop() }, size = VideoTopBarButtonHeight) {
                        Icon(COUIIcons.Close, "关闭", Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        title.ifBlank { if (liveRequested) "直播" else "视频" },
                        color = Color.White,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                    )
                    if (browserFallbackUrl.startsWith("http://", true) || browserFallbackUrl.startsWith("https://", true)) {
                        HighlightedIconButton(
                            onClick = { openExternalUrl(context, browserFallbackUrl) },
                            size = VideoTopBarButtonHeight,
                        ) {
                            Icon(CoboIcons.Globe, "用浏览器打开", Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        is ResolvedVideo.Unavailable -> {
            CoboPredictiveBackHandler(nav)
            val externalTarget = current.webUrl?.takeIf {
                it.startsWith("http://", true) || it.startsWith("https://", true)
            } ?: browserFallbackUrl.takeIf {
                it.startsWith("http://", true) || it.startsWith("https://", true)
            }
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = current.message,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                    )
                    if (externalTarget != null) {
                        Spacer(Modifier.height(14.dp))
                        MediaMaterialPill(onClick = { openExternalUrl(context, externalTarget) }) {
                            Text("用浏览器打开", color = Color.White, fontSize = 13.sp)
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HighlightedIconButton(onClick = { nav.pop() }, size = VideoTopBarButtonHeight) {
                        Icon(COUIIcons.Close, "关闭", Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        current.title?.takeIf { it.isNotBlank() } ?: title.ifBlank { "直播" },
                        color = Color.White,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                    )
                    if (externalTarget != null) {
                        HighlightedIconButton(
                            onClick = { openExternalUrl(context, externalTarget) },
                            size = VideoTopBarButtonHeight,
                        ) {
                            Icon(CoboIcons.Globe, "用浏览器打开", Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        is ResolvedVideo.DirectStream -> {
            val effectiveTitle = current.title?.takeIf { it.isNotBlank() }
                ?: title.ifBlank { if (current.isLive || liveRequested) "直播" else "" }
            val externalTarget = when {
                browserFallbackUrl.startsWith("http://", true) || browserFallbackUrl.startsWith("https://", true) -> browserFallbackUrl
                else -> current.url
            }
            DirectVideoPlayer(
                nav = nav,
                rawUrl = url,
                fid = fid,
                statusId = statusId,
                streamUrl = current.url,
                fallbackStreamUrl = current.fallbackUrl,
                referer = current.referer,
                title = effectiveTitle,
                isLive = current.isLive,
                hud = hud,
                flash = ::flash,
                onRefreshStream = if (canRefresh) {
                    {
                        val fresh = refreshPlayableVideo(
                            rawUrl = url,
                            fid = fid,
                            statusId = statusId,
                            titleHint = effectiveTitle,
                            cacheDir = context.cacheDir,
                            isLiveHint = liveRequested || current.isLive,
                        )
                        (fresh as? ResolvedVideo.DirectStream)?.also { resolved = it }
                    }
                } else {
                    null
                },
                onDownload = { startVideoDownload(current.url, current.referer) },
                onOpenInBrowser = { openExternalUrl(context, externalTarget) },
            )
        }

        is ResolvedVideo.WebPage -> {
            WebVideoPlayer(
                nav = nav,
                pageUrl = current.url,
                title = current.title?.takeIf { it.isNotBlank() }
                    ?: title.ifBlank { if (current.isLive || liveRequested) "直播" else "视频" },
                hud = hud,
                flash = ::flash,
                onDownload = { detectedUrl -> startVideoDownload(detectedUrl, current.url) },
                onOpenInBrowser = { targetUrl -> openExternalUrl(context, targetUrl) },
            )
        }
    }
}

@Composable
private fun DirectVideoPlayer(
    nav: NavState,
    rawUrl: String,
    fid: String,
    statusId: String,
    streamUrl: String,
    fallbackStreamUrl: String?,
    referer: String?,
    title: String,
    isLive: Boolean,
    hud: String,
    flash: (String) -> Unit,
    onRefreshStream: (suspend () -> ResolvedVideo.DirectStream?)? = null,
    onDownload: () -> Unit,
    onOpenInBrowser: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val activity = remember(context) { context.findActivity() }
    val floatingController = LocalFloatingVideoController.current
    val systemPipActive = floatingController.systemPipActive
    val audio = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    var activeStreamUrl by remember(streamUrl) { mutableStateOf(streamUrl) }
    var activeReferer by remember(streamUrl, referer) { mutableStateOf(referer) }
    var refreshingStream by remember { mutableStateOf(false) }
    var refreshedOnError by remember { mutableStateOf(false) }
    var pendingSeekMs by remember { mutableLongStateOf(0L) }
    val currentOnRefreshStream by rememberUpdatedState(onRefreshStream)
    val claimedSession = remember(rawUrl, activeStreamUrl) {
        floatingController.claimForFullscreen(rawUrl, activeStreamUrl)
    }
    val player = remember(activeStreamUrl, activeReferer, claimedSession) {
        if (claimedSession != null) {
            claimedSession.player
        } else {
            floatingController.dismissFloating()
            val effectiveReferer = activeReferer?.takeIf { it.isNotBlank() } ?: defaultRefererFor(activeStreamUrl)
            val httpFactory = DefaultHttpDataSource.Factory().apply {
                setUserAgent(Api.WEB_UA)
                setAllowCrossProtocolRedirects(true)
                if (!effectiveReferer.isNullOrBlank()) {
                    setDefaultRequestProperties(mapOf("Referer" to effectiveReferer))
                }
            }
            val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
            ExoPlayer.Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
                .build()
                .apply {
                    addListener(
                        object : Player.Listener {
                            override fun onPlayerError(error: PlaybackException) {
                                Log.e("CoboVideo", "ExoPlayer error for $activeStreamUrl: ${error.errorCodeName}", error)
                                val fallback = fallbackStreamUrl?.takeIf { it.isNotBlank() && it != activeStreamUrl }
                                if (fallback != null) {
                                    activeStreamUrl = fallback
                                    return
                                }
                                val refresher = currentOnRefreshStream
                                if (
                                    refresher != null &&
                                    !refreshedOnError &&
                                    !refreshingStream &&
                                    shouldAttemptStreamRefreshOnError(error.errorCode)
                                ) {
                                    refreshedOnError = true
                                    refreshingStream = true
                                    pendingSeekMs = currentPosition.coerceAtLeast(0L)
                                    scope.launch {
                                        val fresh = runCatching { refresher() }.getOrNull()
                                        refreshingStream = false
                                        if (fresh != null && fresh.url.isNotBlank() && fresh.url != activeStreamUrl) {
                                            activeReferer = fresh.referer
                                            activeStreamUrl = fresh.url
                                        } else {
                                            pendingSeekMs = 0L
                                            flash(if (isLive) "直播加载失败（${error.errorCodeName}）" else "视频播放失败（${error.errorCodeName}）")
                                        }
                                    }
                                    return
                                }
                                flash(if (isLive) "直播加载失败（${error.errorCodeName}）" else "视频播放失败（${error.errorCodeName}）")
                            }
                        },
                    )
                    setMediaItem(MediaItem.fromUri(activeStreamUrl))
                    if (pendingSeekMs > 0L && !isLive) {
                        seekTo(pendingSeekMs)
                        pendingSeekMs = 0L
                    }
                    prepare()
                    playWhenReady = true
                }
        }
    }

    var handedOffToFloating by remember(player) { mutableStateOf(false) }
    var videoAspectRatio by remember(player) {
        mutableFloatStateOf(claimedSession?.aspectRatio ?: (16f / 9f))
    }
    var controlsVisible by remember { mutableStateOf(true) }
    var controlsInteractionTick by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(player.isPlaying || player.playWhenReady) }
    var isBuffering by remember { mutableStateOf(player.playbackState == Player.STATE_BUFFERING) }
    var playbackState by remember(player) { mutableIntStateOf(player.playbackState) }
    var playWhenReady by remember(player) { mutableStateOf(player.playWhenReady) }
    var hasStartedPlaying by remember(player) {
        mutableStateOf(player.isPlaying || player.playbackState == Player.STATE_READY)
    }
    var autoCloseTriggered by remember(player) { mutableStateOf(false) }
    var playerIsLive by remember(activeStreamUrl) { mutableStateOf(false) }
    val liveMode = isLive || playerIsLive
    val currentLiveMode by rememberUpdatedState(liveMode)
    var durationMs by remember { mutableLongStateOf(0L) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var bufferedPositionMs by remember { mutableLongStateOf(0L) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableLongStateOf(0L) }
    var speed by remember(player) { mutableFloatStateOf(claimedSession?.request?.playbackSpeed ?: 1f) }
    var speedMenu by remember { mutableStateOf(false) }
    var longPressSpeedActive by remember { mutableStateOf(false) }
    var longPressSpeedLocked by remember { mutableStateOf(false) }
    val effectivePlaybackSpeed = if (liveMode) {
        1f
    } else if (longPressSpeedActive || longPressSpeedLocked) {
        3f
    } else {
        speed
    }

    LaunchedEffect(player, effectivePlaybackSpeed) {
        player.setPlaybackSpeed(effectivePlaybackSpeed)
    }

    LaunchedEffect(player, liveMode) {
        if (liveMode) {
            speedMenu = false
            longPressSpeedActive = false
            longPressSpeedLocked = false
            isScrubbing = false
            if (!player.isPlaying && player.playbackState != Player.STATE_ENDED) {
                player.playWhenReady = true
                player.play()
            }
        }
    }

    fun syncPlayerState() {
        playbackState = player.playbackState
        playWhenReady = player.playWhenReady
        isPlaying = player.isPlaying
        isBuffering = playbackState == Player.STATE_BUFFERING
        if (isPlaying || playbackState == Player.STATE_READY) {
            hasStartedPlaying = true
        }
        if (player.isCurrentMediaItemLive) {
            playerIsLive = true
        }
        durationMs = player.duration.coerceAtLeast(0L)
        bufferedPositionMs = player.bufferedPosition.coerceAtLeast(0L)
        if (!isScrubbing) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
        }
    }

    fun seekBy(deltaMs: Long) {
        if (currentLiveMode) return
        val raw = (player.currentPosition + deltaMs).coerceAtLeast(0L)
        val dur = player.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
        val target = raw.coerceAtMost(dur)
        player.seekTo(target)
        positionMs = target
        flash(if (deltaMs < 0L) "快退 5 秒" else "快进 5 秒")
    }

    fun togglePlayPause() {
        if (currentLiveMode) return
        if (player.isPlaying) {
            player.pause()
            flash("已暂停")
        } else {
            if (player.playbackState == Player.STATE_ENDED) {
                player.seekTo(0L)
            }
            player.play()
            flash("继续播放")
        }
        syncPlayerState()
    }

    val fullscreenSession = remember(player, rawUrl, activeStreamUrl, title, liveMode) {
        FloatingVideoSession(
            request = FloatingVideoRequest(
                rawUrl = rawUrl,
                streamUrl = activeStreamUrl,
                fallbackStreamUrl = fallbackStreamUrl,
                referer = activeReferer,
                title = title,
                isLive = liveMode,
                fid = fid,
                statusId = statusId,
                playbackSpeed = speed,
            ),
            player = player,
            onRefreshStream = currentOnRefreshStream,
            initialAspectRatio = videoAspectRatio,
        )
    }

    LaunchedEffect(fullscreenSession, isPlaying, isBuffering, videoAspectRatio) {
        fullscreenSession.isPlaying = isPlaying
        fullscreenSession.isBuffering = isBuffering
        fullscreenSession.aspectRatio = videoAspectRatio
    }

    DisposableEffect(player, fullscreenSession) {
        floatingController.fullscreenSession = fullscreenSession
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                playWhenReady = player.playWhenReady
                playbackState = player.playbackState
                if (playing) hasStartedPlaying = true
            }

            override fun onPlayWhenReadyChanged(playWhenReadyNow: Boolean, reason: Int) {
                playWhenReady = playWhenReadyNow
                playbackState = player.playbackState
                isPlaying = player.isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                syncPlayerState()
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    val pixelRatio = videoSize.pixelWidthHeightRatio.takeIf { it > 0f } ?: 1f
                    videoAspectRatio = (videoSize.width * pixelRatio) / videoSize.height.toFloat()
                }
            }
        }
        player.addListener(listener)
        syncPlayerState()
        onDispose {
            player.removeListener(listener)
            if (floatingController.fullscreenSession === fullscreenSession) {
                floatingController.fullscreenSession = null
            }
            if (!handedOffToFloating) {
                player.release()
            }
        }
    }

    LaunchedEffect(player, controlsVisible, isScrubbing) {
        if (!controlsVisible) return@LaunchedEffect
        while (true) {
            syncPlayerState()
            delay(250)
        }
    }

    LaunchedEffect(
        controlsVisible,
        controlsInteractionTick,
        isPlaying,
        speedMenu,
        isScrubbing,
        systemPipActive,
    ) {
        if (
            shouldAutoHideVideoControls(
                controlsVisible = controlsVisible,
                isPlaying = isPlaying,
                speedMenu = speedMenu,
                isScrubbing = isScrubbing,
                systemPipActive = systemPipActive,
            )
        ) {
            delay(VideoControlsAutoHideDelayMillis)
            controlsVisible = false
        }
    }

    LaunchedEffect(
        playbackState,
        hasStartedPlaying,
        isScrubbing,
        liveMode,
        handedOffToFloating,
        App.settings.videoAutoCloseOnComplete,
    ) {
        if (
            !autoCloseTriggered &&
            shouldAutoCloseVideoOnComplete(
                autoCloseEnabled = App.settings.videoAutoCloseOnComplete,
                playbackState = playbackState,
                hasStartedPlaying = hasStartedPlaying,
                isLive = liveMode,
                isScrubbing = isScrubbing,
                handedOffToFloating = handedOffToFloating,
            )
        ) {
            delay(250L)
            if (
                !autoCloseTriggered &&
                player.playbackState == Player.STATE_ENDED &&
                !isScrubbing &&
                !handedOffToFloating &&
                !currentLiveMode &&
                App.settings.videoAutoCloseOnComplete
            ) {
                autoCloseTriggered = true
                nav.pop()
            }
        }
    }

    var centerGestureSnapshot by remember { mutableStateOf(VideoCenterGestureSnapshot()) }
    var centerGestureAnchorYPx by remember { mutableFloatStateOf(0f) }
    var centerGestureDismissJob by remember { mutableStateOf<Job?>(null) }
    val centerSwipeEnabled = App.settings.videoCenterSwipeActions
    val currentCenterSwipeEnabled by rememberUpdatedState(centerSwipeEnabled)
    LaunchedEffect(centerSwipeEnabled) {
        if (!centerSwipeEnabled) {
            centerGestureDismissJob?.cancel()
            centerGestureDismissJob = null
            centerGestureSnapshot = VideoCenterGestureSnapshot()
        }
    }

    fun windowBrightness(): Float {
        val attr = activity?.window?.attributes?.screenBrightness ?: -1f
        if (attr in 0.01f..1f) return attr
        return runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
        }.getOrDefault(0.5f)
    }

    fun applyBrightness(value: Float) {
        val window = activity?.window ?: return
        val params = window.attributes
        params.screenBrightness = value
        window.attributes = params
    }

    val keepScreenOn = shouldKeepVideoScreenOn(
        isPlaying = isPlaying,
        playWhenReady = playWhenReady,
        playbackState = playbackState,
        refreshingStream = refreshingStream,
    )

    val enterInAppFloating: () -> Unit = {
        if (!handedOffToFloating) {
            handedOffToFloating = true
            val session = FloatingVideoSession(
                request = FloatingVideoRequest(
                    rawUrl = rawUrl,
                    streamUrl = activeStreamUrl,
                    fallbackStreamUrl = fallbackStreamUrl,
                    referer = activeReferer,
                    title = title,
                    isLive = liveMode,
                    fid = fid,
                    statusId = statusId,
                    playbackSpeed = speed,
                ),
                player = player,
                onRefreshStream = currentOnRefreshStream,
                initialAspectRatio = videoAspectRatio,
            )
            if (floatingController.fullscreenSession?.player === player) {
                floatingController.fullscreenSession = null
            }
            floatingController.startFloating(session)
            nav.pop()
        }
    }

    val currentEnterInAppFloating by rememberUpdatedState(enterInAppFloating)
    val currentRequestSystemPip by rememberUpdatedState({
        if (!floatingController.requestSystemPip(videoAspectRatio)) {
            flash("当前设备不支持画中画")
        }
    })
    val currentCloseVideo by rememberUpdatedState({ nav.pop() })

    ViewerSystemBars(visible = !systemPipActive && (controlsVisible || speedMenu))

    val autoFloatOnBack = shouldAutoFloatVideoOnBack(
        autoFloatEnabled = App.settings.videoAutoFloatOnBack,
        isPlaying = isPlaying,
        isLive = liveMode,
    )
    if (speedMenu) {
        BackHandler(enabled = true) { speedMenu = false }
    } else if (autoFloatOnBack) {
        BackHandler(enabled = true) { enterInAppFloating() }
    } else {
        CoboPredictiveBackHandler(nav)
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    controllerAutoShow = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    this.keepScreenOn = keepScreenOn
                    this.player = player
                }
            },
            update = { view ->
                if (view.player !== player) {
                    view.player = player
                }
                if (view.keepScreenOn != keepScreenOn) {
                    view.keepScreenOn = keepScreenOn
                }
            },
            onRelease = { view ->
                view.keepScreenOn = false
                view.player = null
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (isBuffering || refreshingStream) {
            CoboRefreshSpinner(
                modifier = Modifier.align(Alignment.Center),
                size = 30.dp,
            )
        }

        // 手势层铺在底部与顶部控件下面：单点切换整套控件显隐，左/中/右双击快退5秒/暂停播放/快进5秒，
        // 左右拖动联动底部进度条快进/快退；纵向滑动按左/中/右分区分别调节亮度、触发中区悬浮或关闭、调节音量；
        // 长按临时 3× 倍速并支持下滑锁定。直播模式下禁用倍速、进度拖动、快进/快退与暂停。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(liveMode) {
                    if (currentLiveMode) return@pointerInput
                    var startPos = 0L
                    var dur = 0L
                    var draggedX = 0f
                    var active = false
                    detectHorizontalDragGestures(
                        onDragStart = {
                            if (currentLiveMode || longPressSpeedActive) {
                                active = false
                                return@detectHorizontalDragGestures
                            }
                            if (speedMenu) speedMenu = false
                            dur = player.duration.takeIf { it > 0L } ?: 0L
                            active = dur > 0L
                            if (active) {
                                startPos = player.currentPosition.coerceIn(0L, dur)
                                draggedX = 0f
                                isScrubbing = true
                                scrubPositionMs = startPos
                                controlsVisible = true
                            }
                        },
                        onHorizontalDrag = { change, dx ->
                            if (!active || currentLiveMode || longPressSpeedActive) return@detectHorizontalDragGestures
                            change.consume()
                            draggedX += dx
                            val target = calculateHorizontalScrubTargetMs(
                                startPosMs = startPos,
                                durationMs = dur,
                                draggedPx = draggedX,
                                containerWidthPx = size.width.toFloat(),
                            )
                            scrubPositionMs = target
                            flash("${formatVideoTime(target)} / ${formatVideoTime(dur)}")
                        },
                        onDragEnd = {
                            if (!active) return@detectHorizontalDragGestures
                            player.seekTo(scrubPositionMs)
                            positionMs = scrubPositionMs
                            isScrubbing = false
                            active = false
                        },
                        onDragCancel = {
                            if (!active) return@detectHorizontalDragGestures
                            isScrubbing = false
                            active = false
                        },
                    )
                }
                .pointerInput(Unit) {
                    val screenDensity = this.density
                    var zone = VideoVerticalZone.Brightness
                    var startBrightness = 0.5f
                    var startVolume = 0
                    var maxVolume = 1
                    var dragged = 0f
                    var verticalActive = false
                    var lastArmed = VideoCenterGestureAction.None
                    detectVerticalDragGestures(
                        onDragStart = { position ->
                            if (longPressSpeedActive) {
                                verticalActive = false
                                return@detectVerticalDragGestures
                            }
                            verticalActive = true
                            zone = classifyVideoVerticalZone(
                                xPx = position.x,
                                containerWidthPx = size.width.toFloat(),
                                centerSwipeEnabled = currentCenterSwipeEnabled,
                            )
                            dragged = 0f
                            when (zone) {
                                VideoVerticalZone.Brightness -> {
                                    startBrightness = windowBrightness()
                                }
                                VideoVerticalZone.Volume -> {
                                    startVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                                    maxVolume = audio
                                        .getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                        .coerceAtLeast(1)
                                }
                                VideoVerticalZone.CenterActions -> {
                                    centerGestureDismissJob?.cancel()
                                    centerGestureDismissJob = null
                                    centerGestureAnchorYPx = position.y
                                    centerGestureSnapshot = VideoCenterGestureSnapshot()
                                    lastArmed = VideoCenterGestureAction.None
                                }
                            }
                        },
                        onVerticalDrag = { change, dy ->
                            if (!verticalActive || longPressSpeedActive) return@detectVerticalDragGestures
                            change.consume()
                            dragged += dy
                            when (zone) {
                                VideoVerticalZone.Brightness -> {
                                    val ratio = -dragged / size.height
                                    val value = (startBrightness + ratio).coerceIn(0.02f, 1f)
                                    applyBrightness(value)
                                    flash("亮度 ${(value * 100).roundToInt()}%")
                                }
                                VideoVerticalZone.Volume -> {
                                    val ratio = -dragged / size.height
                                    val value = (startVolume + ratio * maxVolume)
                                        .roundToInt()
                                        .coerceIn(0, maxVolume)
                                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0)
                                    flash("音量 ${value * 100 / maxVolume}%")
                                }
                                VideoVerticalZone.CenterActions -> {
                                    val next = evaluateVideoCenterGesture(dragged, screenDensity)
                                    if (
                                        next.armedAction != lastArmed &&
                                        next.armedAction != VideoCenterGestureAction.None
                                    ) {
                                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                    }
                                    lastArmed = next.armedAction
                                    centerGestureSnapshot = next
                                }
                            }
                        },
                        onDragEnd = {
                            if (!verticalActive) return@detectVerticalDragGestures
                            verticalActive = false
                            if (zone == VideoVerticalZone.CenterActions) {
                                val armed = centerGestureSnapshot.armedAction
                                val dir = centerGestureSnapshot.direction
                                when (armed) {
                                    VideoCenterGestureAction.InAppFloating -> {
                                        centerGestureSnapshot = VideoCenterGestureSnapshot()
                                        currentEnterInAppFloating()
                                    }
                                    VideoCenterGestureAction.SystemPip -> {
                                        centerGestureSnapshot = VideoCenterGestureSnapshot()
                                        currentRequestSystemPip()
                                    }
                                    VideoCenterGestureAction.Close -> {
                                        centerGestureSnapshot = VideoCenterGestureSnapshot()
                                        currentCloseVideo()
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
                            }
                        },
                        onDragCancel = {
                            if (!verticalActive) return@detectVerticalDragGestures
                            verticalActive = false
                            if (
                                zone == VideoVerticalZone.CenterActions &&
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
                        onDoubleTap = { offset ->
                            if (speedMenu) {
                                speedMenu = false
                                return@detectTapGestures
                            }
                            if (currentLiveMode) return@detectTapGestures
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            when {
                                offset.x < w / 3f -> seekBy(-SeekStepMs)
                                offset.x > w * 2f / 3f -> seekBy(SeekStepMs)
                                else -> togglePlayPause()
                            }
                        },
                        onLongPress = { /* 长按 3× 倍速与锁定解锁由下方 awaitEachGesture 接管 */ },
                    )
                }
                .pointerInput(liveMode) {
                    if (currentLiveMode) return@pointerInput
                    val lockDragThresholdPx = with(this) { 48.dp.toPx() }
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (currentLiveMode) return@awaitEachGesture
                        var lastPos = down.position
                        val movedOrLifted = withTimeoutOrNull(
                            viewConfiguration.longPressTimeoutMillis
                        ) {
                            var early = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null || !change.pressed) {
                                    early = true
                                    break
                                }
                                lastPos = change.position
                                if ((change.position - down.position).getDistance() >
                                    viewConfiguration.touchSlop
                                ) {
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

        AnimatedVisibility(
            visible = !systemPipActive && (controlsVisible || speedMenu),
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
                HighlightedIconButton(onClick = { nav.pop() }, size = VideoTopBarButtonHeight) {
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
                    title,
                    color = Color.White,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                )
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
                                Speeds.forEachIndexed { index, value ->
                                    DropdownImpl(
                                        item = DropdownItem(text = speedLabel(value)),
                                        optionSize = Speeds.size,
                                        isSelected = !longPressSpeedLocked && speed == value,
                                        index = index,
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
                    Spacer(Modifier.width(6.dp))
                    HighlightedIconButton(
                        onClick = {
                            controlsInteractionTick++
                            onDownload()
                        },
                        size = VideoTopBarButtonHeight,
                    ) {
                        Icon(COUIIcons.Download, "下载视频", Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                }
                if (!centerSwipeEnabled) {
                    HighlightedIconButton(
                        onClick = {
                            controlsInteractionTick++
                            enterInAppFloating()
                        },
                        size = VideoTopBarButtonHeight,
                    ) {
                        Icon(CoboIcons.FloatingWindow, "小窗播放", Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    HighlightedIconButton(
                        onClick = {
                            controlsInteractionTick++
                            if (!floatingController.requestSystemPip(videoAspectRatio)) {
                                flash("当前设备不支持画中画")
                            }
                        },
                        size = VideoTopBarButtonHeight,
                    ) {
                        Icon(CoboIcons.PictureInPicture, "画中画", Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                }
                HighlightedIconButton(
                    onClick = {
                        controlsInteractionTick++
                        onOpenInBrowser()
                    },
                    size = VideoTopBarButtonHeight,
                ) {
                    Icon(CoboIcons.Globe, "用浏览器打开", Modifier.size(18.dp))
                }
            }
        }

        AnimatedVisibility(
            visible = !systemPipActive && !liveMode && (controlsVisible || speedMenu),
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
                            imageVector = if (isPlaying) COUIIcons.Pause else COUIIcons.Play,
                            contentDescription = if (isPlaying) "暂停" else "播放",
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
                    val displayPos = if (isScrubbing) scrubPositionMs else positionMs
                    val effectiveDur = durationMs.coerceAtLeast(1L)
                    Text(
                        text = formatVideoTime(displayPos),
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.widthIn(min = 44.dp),
                    )
                    val intentState = when {
                        durationMs <= 0L -> IntentSliderPlaybackState.LoadingInitial
                        isBuffering -> IntentSliderPlaybackState.LoadingBuffering
                        isPlaying -> IntentSliderPlaybackState.Playing
                        else -> IntentSliderPlaybackState.Paused
                    }
                    IntentSlider(
                        value = displayPos.coerceIn(0L, effectiveDur).toFloat(),
                        onValueChange = { next ->
                            isScrubbing = true
                            scrubPositionMs = next.roundToLong()
                        },
                        secondaryValue = bufferedPositionMs.coerceIn(0L, effectiveDur).toFloat(),
                        onValueChangeFinished = {
                            player.seekTo(scrubPositionMs)
                            positionMs = scrubPositionMs
                            isScrubbing = false
                        },
                        valueRange = 0f..effectiveDur.toFloat(),
                        enabled = durationMs > 0L,
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
                        text = formatVideoTime(durationMs),
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        modifier = Modifier.widthIn(min = 44.dp),
                        textAlign = TextAlign.End,
                    )
                }
            }
        }

        val topToastInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout)
        val activeGestureHint = if (
            !systemPipActive &&
            centerSwipeEnabled &&
            centerGestureSnapshot.direction != VideoCenterGestureDirection.None
        ) {
            videoCenterGestureHintText(centerGestureSnapshot.direction, centerGestureSnapshot.armedAction)
        } else {
            ""
        }
        val activeSpeedLockToast = !systemPipActive && !liveMode && (longPressSpeedActive || longPressSpeedLocked)
        val activeTopToastText = when {
            activeGestureHint.isNotEmpty() -> activeGestureHint
            activeSpeedLockToast -> if (longPressSpeedLocked) "3.0× 已锁定，长按解锁" else "3.0× 快进中，下滑锁定"
            !systemPipActive && hud.isNotEmpty() -> hud
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
                AnimatedContent(
                    targetState = displayTopToastText to displayTopToastLocked,
                    transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
                    label = "videoUnifiedTopToast",
                ) { (text, locked) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (locked) {
                            Icon(
                                imageVector = COUIIcons.Lock,
                                contentDescription = "已锁定倍速",
                                tint = COUITheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(5.dp))
                        }
                        Text(
                            text = text,
                            color = if (locked) COUITheme.colorScheme.primary else LocalContentColor.current,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        if (!systemPipActive && centerSwipeEnabled) {
            VideoCenterGestureOverlay(
                snapshot = centerGestureSnapshot,
                anchorYPx = centerGestureAnchorYPx,
                modifier = Modifier.fillMaxSize(),
            )
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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

private const val VideoSnifferJs = """
(function() {
  if (window.__coboSnifferInstalled) return;
  window.__coboSnifferInstalled = true;
  function report(u) {
    if (u && typeof u === 'string' && !u.startsWith('blob:')) {
      try { CoboVideoSniffer.onMediaUrl(u); } catch (e) {}
    }
  }
  var lastPlaying = null;
  function reportPlayback() {
    var playing = false;
    var vids = document.querySelectorAll('video');
    for (var i = 0; i < vids.length; i++) {
      var v = vids[i];
      if (!v.paused && !v.ended && v.readyState > 0) {
        playing = true;
        break;
      }
    }
    if (playing !== lastPlaying) {
      lastPlaying = playing;
      try { CoboVideoSniffer.onPlaybackState(playing); } catch (e) {}
    }
  }
  function scanVideos() {
    var vids = document.querySelectorAll('video, source');
    for (var i = 0; i < vids.length; i++) {
      report(vids[i].currentSrc || vids[i].src || vids[i].getAttribute('src'));
    }
    reportPlayback();
  }
  document.addEventListener('play', reportPlayback, true);
  document.addEventListener('playing', reportPlayback, true);
  document.addEventListener('pause', reportPlayback, true);
  document.addEventListener('ended', reportPlayback, true);
  document.addEventListener('emptied', reportPlayback, true);
  var origFetch = window.fetch;
  if (origFetch) {
    window.fetch = function(input) {
      try { report(typeof input === 'string' ? input : input.url); } catch (e) {}
      return origFetch.apply(this, arguments);
    };
  }
  var origOpen = XMLHttpRequest.prototype.open;
  XMLHttpRequest.prototype.open = function(method, url) {
    try { report(url); } catch (e) {}
    return origOpen.apply(this, arguments);
  };
  scanVideos();
  setInterval(scanVideos, 1500);
})();
"""

@android.annotation.SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
private fun WebVideoPlayer(
    nav: NavState,
    pageUrl: String,
    title: String,
    hud: String,
    flash: (String) -> Unit,
    onDownload: (String) -> Unit,
    onOpenInBrowser: (String) -> Unit,
) {
    var sniffedMp4 by remember(pageUrl) { mutableStateOf<String?>(null) }
    var sniffedM3u8 by remember(pageUrl) { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var pageTitle by remember(pageUrl) { mutableStateOf(title) }
    var customView by remember { mutableStateOf<View?>(null) }
    var customCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var webVideoPlaying by remember(pageUrl) { mutableStateOf(false) }
    val keepScreenOn = webVideoPlaying || customView != null

    fun recordMedia(candidate: String) {
        if (!isSniffableMediaUrl(candidate)) return
        if (isM3u8Stream(candidate)) {
            if (sniffedM3u8 == null) sniffedM3u8 = candidate
        } else {
            sniffedMp4 = candidate
        }
    }

    BackHandler {
        when {
            customView != null -> customCallback?.onCustomViewHidden()
            canGoBack -> webView?.goBack()
            else -> nav.pop()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF141414))
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HighlightedIconButton(onClick = { nav.pop() }, size = VideoTopBarButtonHeight) {
                    Icon(COUIIcons.Close, "关闭", Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    pageTitle,
                    color = Color.White,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 10.dp),
                )
                HighlightedIconButton(
                    size = VideoTopBarButtonHeight,
                    onClick = {
                        val direct = sniffedMp4 ?: sniffedM3u8
                        if (direct != null) {
                            onDownload(direct)
                        } else {
                            val view = webView
                            if (view == null) {
                                flash("未检测到可下载的视频流")
                            } else {
                                view.evaluateJavascript(
                                    "(() => { const v = document.querySelector('video'); return v ? (v.currentSrc || v.src || '') : ''; })()",
                                ) { raw ->
                                    val cleaned = raw.trim('"').replace("\\/", "/")
                                    if (cleaned.startsWith("http://", true) || cleaned.startsWith("https://", true)) {
                                        onDownload(cleaned)
                                    } else {
                                        flash("未检测到可下载的视频流，请先点击播放后再试")
                                    }
                                }
                            }
                        }
                    },
                ) {
                    Icon(COUIIcons.Download, "下载视频", Modifier.size(18.dp))
                }
                Spacer(Modifier.width(6.dp))
                HighlightedIconButton(
                    size = VideoTopBarButtonHeight,
                    onClick = {
                        val liveUrl = webView?.url?.takeIf {
                            it.startsWith("http://", true) || it.startsWith("https://", true)
                        } ?: pageUrl
                        onOpenInBrowser(liveUrl)
                    },
                ) {
                    Icon(CoboIcons.Globe, "用浏览器打开", Modifier.size(18.dp))
                }
            }

            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        val wv = this
                        CookieManager.getInstance().apply {
                            setAcceptCookie(true)
                            setAcceptThirdPartyCookies(wv, true)
                        }
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        settings.userAgentString = Api.WEB_UA
                        addJavascriptInterface(
                            object {
                                @JavascriptInterface
                                fun onMediaUrl(url: String) {
                                    post { recordMedia(url) }
                                }

                                @JavascriptInterface
                                fun onPlaybackState(playing: Boolean) {
                                    post { webVideoPlaying = playing }
                                }
                            },
                            "CoboVideoSniffer",
                        )
                        webChromeClient = object : WebChromeClient() {
                            override fun onReceivedTitle(view: WebView?, t: String?) {
                                if (!t.isNullOrBlank()) pageTitle = t
                            }

                            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                                if (customView != null) {
                                    callback?.onCustomViewHidden()
                                    return
                                }
                                customView = view
                                customCallback = callback
                            }

                            override fun onHideCustomView() {
                                customView = null
                                customCallback = null
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest,
                            ): Boolean {
                                val target = request.url?.toString().orEmpty()
                                val host = request.url?.host?.lowercase().orEmpty()
                                if (host == "app.iqiyi.com" || host == "d.iqiyi.com" || host == "mbdapp.iqiyi.com") {
                                    return true
                                }
                                if (target.startsWith("http://", true) || target.startsWith("https://", true)) {
                                    return false
                                }
                                val resolvedPrivate = resolveWeiboLink(target)
                                if (resolvedPrivate != null && resolvedPrivate != view.url) {
                                    view.loadUrl(resolvedPrivate)
                                } else {
                                    extractIntentFallbackUrl(target)?.let { view.loadUrl(it) }
                                }
                                return true
                            }

                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): WebResourceResponse? {
                                val reqUrl = request?.url?.toString().orEmpty()
                                if (isSniffableMediaUrl(reqUrl)) {
                                    post { recordMedia(reqUrl) }
                                }
                                return super.shouldInterceptRequest(view, request)
                            }

                            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                                webVideoPlaying = false
                                super.onPageStarted(view, url, favicon)
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                canGoBack = view.canGoBack()
                                view.evaluateJavascript(VideoSnifferJs, null)
                            }
                        }
                        this.keepScreenOn = keepScreenOn
                        loadUrl(pageUrl)
                        webView = this
                    }
                },
                modifier = Modifier.fillMaxSize().weight(1f),
                update = { view ->
                    if (view.keepScreenOn != keepScreenOn) {
                        view.keepScreenOn = keepScreenOn
                    }
                },
                onRelease = { view ->
                    view.keepScreenOn = false
                    view.stopLoading()
                    view.destroy()
                },
            )
        }

        customView?.let { fsView ->
            AndroidView(
                factory = { fsView.apply { this.keepScreenOn = true } },
                update = { view ->
                    if (!view.keepScreenOn) {
                        view.keepScreenOn = true
                    }
                },
                onRelease = { view ->
                    view.keepScreenOn = false
                },
                modifier = Modifier.fillMaxSize().background(Color.Black),
            )
        }

        val topToastInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout)
        var lastWebHud by remember { mutableStateOf("") }
        if (hud.isNotEmpty()) lastWebHud = hud
        AnimatedVisibility(
            visible = hud.isNotEmpty(),
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(240, easing = VideoBarExitEasing)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(topToastInsets)
                .padding(top = 56.dp),
        ) {
            MediaMaterialPill(height = 34.dp, horizontalPadding = 14.dp) {
                Text(
                    text = if (hud.isNotEmpty()) hud else lastWebHud,
                    color = LocalContentColor.current,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}
