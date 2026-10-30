package com.cobo.app.screen

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.AudioManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
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
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.BitmapImage
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Size
import com.cobo.app.ui.CoboPredictiveBackHandler
import com.cobo.app.ui.LocalImageOrigins
import com.cobo.app.ui.LocalRouteTransition
import com.cobo.app.ui.NavState
import com.cobo.app.ui.ThumbBounds
import com.cobo.app.ui.components.SaveImagesMenu
import com.cobo.app.ui.components.originalSize
import com.cobo.app.ui.components.rememberImageSaveState
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton
import io.github.suqi8.coui.kmp.basic.GlassContainer
import io.github.suqi8.coui.kmp.basic.couiGlassSurface
import io.github.suqi8.coui.kmp.basic.rememberGlassHighlight
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor
import io.github.suqi8.coui.kmp.basic.rememberPreciseClickState
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.layerBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.icon.extended.Close
import io.github.suqi8.coui.kmp.icon.extended.Download
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private const val MaxZoom = 5f
private const val DoubleTapZoom = 2.5f

// A picture narrower than this (width over height) is a long strip. Fitted inside the screen it
// would shrink to a sliver, so it rests at the screen's width and is scrolled up and down, and it
// is decoded at that width so the detail is there when it is zoomed.
private const val LongImageAspect = 9f / 22f

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

// One height for every control in the viewer's top bar: a 38 dp circle next to a 32 dp pill
// reads as a big dot and a small one, which is exactly what it looked like.
private val BarHeight = 38.dp
private val BarRadius = 19.dp

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
    val systemDark = isSystemInDarkTheme()
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
    DisposableEffect(controller, systemDark) {
        onDispose {
            // Hand the bar back exactly as the activity's edge-to-edge style would have it.
            controller?.let {
                it.isAppearanceLightStatusBars = !systemDark
                it.show(WindowInsetsCompat.Type.statusBars())
            }
        }
    }
}

/**
 * A ColorOS glass chip.
 *
 * `couiGlassSurface` returns its own modifier chain and drops anything in front of it, so every
 * caller has to put size/position/padding *after* this call — that is why the callers below wrap
 * their controls in a plain [Box] for placement.
 */
@Composable
private fun Modifier.viewerGlass(
    shape: Shape,
    tint: Color,
    alpha: Float,
    container: GlassContainer = GlassContainer.Small,
): Modifier {
    val backdrop = LocalBackdrop.current
    return couiGlassSurface(
        backdrop = backdrop,
        shape = shape,
        containerColor = tint,
        fallback = Modifier.background(tint.copy(alpha = alpha), shape),
        containerAlpha = alpha,
        highlight = rememberGlassHighlight(container, backdrop),
    )
}

@Composable
private fun ViewerGlyph(
    plate: Color,
    alpha: Float,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .viewerGlass(CircleShape, plate, alpha)
            .size(BarHeight)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
fun ImageViewerScreen(nav: NavState, urls: List<String>, index: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val transition = LocalRouteTransition.current
    val origins = LocalImageOrigins.current
    val density = LocalDensity.current.density
    val frame = remember { ViewerFrame() }
    // 这条曲线只在宿主自己驱动进场时才有；没有它（比如直接渲染）就当作已经落定。
    val progress: () -> Float = remember(transition) { transition?.progress ?: { 1f } }

    var chrome by remember { mutableStateOf(true) }
    var original by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val precise = rememberPreciseClickState()
    // 保存原图 / 一键保存原图与信息流共用同一套队列，HUD 和权限也一起管。
    val saver = rememberImageSaveState()

    val start = index.coerceIn(0, (urls.size - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(initialPage = start) { urls.size }
    val current = urls.getOrNull(pagerState.currentPage).orEmpty()

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

    // 菜单未展开时，受设置中「预测性返回手势」开关控制：
    // 开启时跟手随侧滑进度实时缩放退出，关闭时按普通返回抬手退出。
    if (!menu) {
        CoboPredictiveBackHandler(nav)
    }

    val backdrop = rememberLayerBackdrop()

    Box(Modifier.fillMaxSize().onPlaced { frame.host = it }) {
        // 下面那一页要一直看得见，黑色底随进度一起压上来：画面是从缩略图长出来的，
        // 而不是盖着一整块黑屏直接跳出来。
        Box(Modifier.fillMaxSize().drawBehind { drawRect(Color.Black, alpha = progress()) })

        HorizontalPager(
            state = pagerState,
            pageSpacing = 16.dp,
            beyondViewportPageCount = 1,
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                // 整层按「缩略图那一块」到「整屏」插值：缩放 + 平移 + 裁剪窗口，
                // 于是进度 0 时正好是缩略图的居中裁切，1 时是原样铺满。
                .graphicsLayer {
                    val p = progress()
                    if (p >= 1f) {
                        settle()
                        return@graphicsLayer
                    }
                    val url = urls.getOrNull(pagerState.currentPage)
                    val host = frame.host
                    // 放大之后画面已经不在缩略图那一块里了，这时不再做映射。
                    val live = if (url != null && host != null && !frame.zoomed) {
                        origins.boundsOf(url, host, density)?.also { frame.thumbs[url] = it }
                    } else {
                        null
                    }
                    val thumb = live ?: url?.takeIf { !frame.zoomed }?.let { frame.thumbs[it] }
                    openFrame(p, thumb, url?.let { frame.aspects[it] ?: thumb?.aspect })
                },
        ) { page ->
            // Only the visible page decodes at full resolution; preloading neighbours that way
            // would hold several full-size bitmaps at once.
            Box(Modifier.fillMaxSize().preciseClickAnchor(precise)) {
                ZoomableImage(
                    url = urls[page],
                    thumbModel = origins.modelOf(urls[page]),
                    original = original && page == pagerState.currentPage,
                    isCurrent = pagerState.currentPage == page,
                    isSettledElsewhere = pagerState.settledPage != page,
                    frame = frame,
                    onTap = { chrome = !chrome },
                    onLongPress = {
                        if (!saver.saving) {
                            menu = true
                            chrome = true
                        }
                    },
                )
            }
        }

        if (chrome) {
            CompositionLocalProvider(LocalBackdrop provides backdrop) {
                // The bar is up now, so its inset is real; the cutout still has to be unioned in
                // for landscape and for devices that report them separately.
                val topInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        // 顶栏只在进场后半段出现、退场开头就消失，不会跟着画面一起缩小。
                        .graphicsLayer {
                            alpha = ((progress() - ChromeFadeStart) / (1f - ChromeFadeStart))
                                .coerceIn(0f, 1f)
                        }
                        .align(Alignment.TopCenter)
                        // Drawn before the insets and the bar's own padding, so the ramp starts at
                        // the very top of the screen and runs [ScrimTail] past the controls.
                        .background(ChromeScrim)
                        .windowInsetsPadding(topInsets)
                        .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = ScrimTail),
                ) {
                    Box(Modifier.align(Alignment.CenterStart)) {
                        ViewerGlyph(
                            plate = plate,
                            alpha = plateAlpha,
                            onClick = { nav.pop() },
                        ) {
                            Icon(COUIIcons.Close, "关闭", Modifier, tint = ink)
                        }
                    }

                    // Centred on the screen rather than tucked against the close button.
                    Box(Modifier.align(Alignment.Center)) {
                        Box(
                            modifier = Modifier
                                .viewerGlass(RoundedCornerShape(BarRadius), plate, plateAlpha)
                                .height(BarHeight)
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${pagerState.currentPage + 1} / ${urls.size}",
                                color = ink,
                                fontSize = 13.sp,
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .viewerGlass(RoundedCornerShape(BarRadius), plate, plateAlpha)
                                .height(BarHeight)
                                .clickable {
                                    original = !original
                                    saver.flash(if (original) "已切换到原图" else "已切回标准尺寸")
                                }
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "原图",
                                color = if (original) Color(0xFF1A8CFF) else ink,
                                fontSize = 13.sp,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        ViewerGlyph(
                            plate = plate,
                            alpha = plateAlpha,
                            onClick = { saver.saveOne(current) },
                        ) {
                            Icon(COUIIcons.Download, "保存原图", Modifier.size(19.dp), tint = ink)
                        }
                    }
                }
            }
        }

        // 长按呼出的就是 COUI 那个窗口下拉菜单，按手指位置弹在看图页之上。
        SaveImagesMenu(
            urls = if (menu) urls else null,
            index = pagerState.currentPage,
            precise = precise,
            onDismiss = { menu = false },
            state = saver,
        )
    }
}

@Composable
private fun ZoomableImage(
    url: String,
    thumbModel: String?,
    original: Boolean,
    isCurrent: Boolean,
    isSettledElsewhere: Boolean,
    frame: ViewerFrame,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
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
    var loaded by remember(url) { mutableStateOf(false) }
    // 整图自己的不透明度，让它盖着缩略图淡进来，而不是硬切一下。
    var reveal by remember(url) { mutableFloatStateOf(0f) }

    // A long strip is decoded at the screen's width rather than fitted to it, so zooming in shows
    // detail instead of a stretched handful of pixels.
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }
    val decodeSize = frame.aspects[url]?.takeIf { it < LongImageAspect }?.let { aspect ->
        val width = screenWidthPx.coerceAtLeast(1)
        val height = (width / aspect).toInt().coerceAtLeast(1)
        coil3.size.Size(width, minOf(height, (MaxDecodePixels / width).coerceAtLeast(1)))
    }

    val model: Any = remember(url, original, context, decodeSize) {
        val targetUrl = if (original) originalSize(url) else url
        ImageRequest.Builder(context)
            .data(targetUrl.takeIf { remote } ?: targetUrl)
            .apply {
                if (original) {
                    size(coil3.size.Size.ORIGINAL)
                } else if (decodeSize != null) {
                    size(decodeSize)
                }
            }
            .build()
    }

    // The picture as laid out at rest; a picture whose shape is not known yet fills the box.
    fun shown(): ComposeSize {
        val aspect = frame.aspects[url] ?: return ComposeSize(box.width.toFloat(), box.height.toFloat())
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
        glide.value?.cancel()
        glide.value = scope.launch {
            AnimationState(initialValue = offset.y, initialVelocity = velocity)
                .animateDecay(exponentialDecay(frictionMultiplier = 1.2f)) {
                    offset = Offset(offset.x, value.coerceIn(-max, max))
                    if (value <= -max || value >= max) cancelAnimation()
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
    LaunchedEffect(isCurrent, box, frame.aspects[url]) {
        if (box == IntSize.Zero || frame.aspects[url] == null) return@LaunchedEffect
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
    LaunchedEffect(loaded) {
        if (loaded) animate(0f, 1f, animationSpec = tween(LoadFadeMillis)) { value, _ -> reveal = value }
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .onSizeChanged { box = it }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        ) {
            // The picture rests at the size it shows at. A long strip is taller than this box and
            // overhangs it, which is what the gesture layer below scrolls; every other picture
            // rests fitted inside the box.
            val rest = shown()
            val resting = if (rest.height > box.height + 0.5f) {
                Modifier.requiredSize(
                    with(density) { rest.width.toDp() },
                    with(density) { rest.height.toDp() },
                )
            } else {
                Modifier.fillMaxSize()
            }
            Box(Modifier.align(Alignment.Center).then(resting)) {
                // 信息流里那张图先顶上：从缩略图长出来的那几帧里，看到的必须是这张照片本身，
                // 而不是一块空框。整图到了再盖上去淡入，缩略图就一直垫在下面。
                if (thumbModel != null && (!loaded || reveal < 1f)) {
                    AsyncImage(
                        model = thumbModel,
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
                        loaded = true
                        val picture = state.painter.intrinsicSize
                        if (picture.width > 0f && picture.height > 0f) {
                            frame.aspects[url] = picture.width / picture.height
                        }
                    },
                )
            }
        }

        // Gestures sit above the picture, so neither can swallow a swipe.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onTap() },
                        onDoubleTap = { toggleZoom(it) },
                        onLongPress = { onLongPress() },
                    )
                }
                .pointerInput(Unit) {
                    detectZoomAndPan(
                        onStart = { glide.value?.cancel() },
                        onRelease = { velocity, taken ->
                            if (scale < 1f - ZoomEpsilon) rebound() else if (taken) flingBy(velocity.y)
                        },
                    ) { focus, pan, zoom, pointers ->
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
                },
        )
    }
}

/**
 * Pinch and drag detection that only consumes what it uses. [onGesture] gets the centroid, the pan,
 * the zoom factor and the number of fingers down, and returns whether it took the movement; when it
 * did not, the pointer events stay free for a parent such as the pager. [onStart] runs as the
 * fingers land; [onRelease] once they are all up, with the speed the last one left at and whether
 * the gesture took anything at all.
 */
private suspend fun PointerInputScope.detectZoomAndPan(
    onStart: () -> Unit = {},
    onRelease: (velocity: Velocity, taken: Boolean) -> Unit = { _, _ -> },
    onGesture: (focus: Offset, pan: Offset, zoom: Float, pointers: Int) -> Boolean,
) {
    awaitEachGesture {
        var zoom = 1f
        var pan = Offset.Zero
        var pastTouchSlop = false
        var taken = false
        val tracker = VelocityTracker()
        val touchSlop = viewConfiguration.touchSlop
        val down = awaitFirstDown(requireUnconsumed = false)
        tracker.addPosition(down.uptimeMillis, down.position)
        onStart()
        do {
            val event = awaitPointerEvent()
            event.changes.fastForEach { tracker.addPosition(it.uptimeMillis, it.position) }
            val canceled = event.changes.fastAny { it.isConsumed }
            if (!canceled) {
                val zoomChange = event.calculateZoom()
                val panChange = event.calculatePan()
                if (!pastTouchSlop) {
                    zoom *= zoomChange
                    pan += panChange
                    val zoomMotion = abs(1f - zoom) * event.calculateCentroidSize(useCurrent = false)
                    if (zoomMotion > touchSlop || pan.getDistance() > touchSlop) pastTouchSlop = true
                }
                if (pastTouchSlop && (zoomChange != 1f || panChange != Offset.Zero)) {
                    val pointers = event.changes.count { it.pressed }
                    val focus = event.calculateCentroid(useCurrent = false)
                    if (onGesture(focus, panChange, zoomChange, pointers)) {
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

/** 一页图画自己的进场要用到、又不属于任何一页的东西。 */
@Stable
private class ViewerFrame {
    var host: LayoutCoordinates? = null

    /** 屏幕上这张图是否已经放大。 */
    var zoomed by mutableStateOf(false)

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
 * 把整层从缩略图摆到整屏。画面本来就停在它最终落定的位置（居中、按原比例），
 * 所以绕中心放大到刚好盖住缩略图、再裁成缩略图那一块，得到的正是缩略图的居中裁切；
 * 中间每一帧都是这两步的同一个插值。没有缩略图、或者还不知道图片本身的比例时，
 * 只淡入并轻微放大，绝不从一块错误的位置长出来。
 */
private fun GraphicsLayerScope.openFrame(progress: Float, thumb: ThumbBounds?, aspect: Float?) {
    if (size.width <= 0f || size.height <= 0f) {
        settle()
        return
    }
    val source = thumb?.rect
    val rest = (aspect ?: source?.let { it.width / it.height })
        ?.let { restSize(size.width, size.height, it) }
    if (source == null || rest == null || rest.height > size.height + 0.5f) {
        settle()
        alpha = progress
        scaleX = lerp(0.92f, 1f, progress)
        scaleY = scaleX
        return
    }
    val width = size.width
    val height = size.height
    val cover = max(source.width / rest.width, source.height / rest.height)
    val scale = lerp(cover, 1f, progress)
    alpha = 1f
    transformOrigin = TransformOrigin.Center
    scaleX = scale
    scaleY = scale
    translationX = lerp(source.center.x, width / 2f, progress) - width / 2f
    translationY = lerp(source.center.y, height / 2f, progress) - height / 2f
    // 可见窗口在图层自己的坐标里，是被缩放放大了的，所以要按缩放折算回去。
    shape = CenteredWindow(
        width = lerp(source.width, width, progress) / scale,
        height = lerp(source.height, height, progress) / scale,
        radius = lerp(thumb.cornerRadius, 0f, progress) / scale,
    )
    clip = true
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

/** The control bar PlayerView draws at the bottom; gestures must not cover it. */
private val ControllerStrip = 84.dp

private val Speeds = listOf(1f, 1.25f, 1.5f, 2f, 3f)

private val SpeedPanel = Color(0xFF2A2A2A).copy(alpha = 0.94f)

private fun speedLabel(value: Float): String = when (value) {
    1f -> "1.0×"
    1.25f -> "1.25×"
    1.5f -> "1.5×"
    2f -> "2.0×"
    else -> "3.0×"
}

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return ctx as? Activity
}

@Composable
fun VideoScreen(nav: NavState, url: String, title: String) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val audio = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }

    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    var speed by remember { mutableFloatStateOf(1f) }
    var speedMenu by remember { mutableStateOf(false) }
    var hud by remember { mutableStateOf("") }
    var hudTick by remember { mutableIntStateOf(0) }

    fun flash(text: String) {
        hud = text
        hudTick++
    }

    // Every brightness/volume step restarts the timer, so the readout follows the finger and
    // clears shortly after it stops.
    LaunchedEffect(hudTick) {
        if (hud.isNotEmpty()) {
            delay(900)
            hud = ""
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

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    CoboPredictiveBackHandler(nav)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                }.also { playerView = it }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Gesture surface: left half drags brightness, right half volume, a still finger at 3×,
        // a tap toggles the control bar. The bottom strip stays clear so the bar stays usable.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = ControllerStrip)
                .pointerInput(Unit) {
                    var left = true
                    var startBrightness = 0.5f
                    var startVolume = 0
                    var maxVolume = 1
                    var dragged = 0f
                    detectVerticalDragGestures(
                        onDragStart = { position ->
                            left = position.x < size.width / 2f
                            dragged = 0f
                            if (left) {
                                startBrightness = windowBrightness()
                            } else {
                                startVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                                maxVolume = audio
                                    .getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                    .coerceAtLeast(1)
                            }
                        },
                        onVerticalDrag = { change, dy ->
                            change.consume()
                            dragged += dy
                            val ratio = -dragged / size.height
                            if (left) {
                                val value = (startBrightness + ratio).coerceIn(0.02f, 1f)
                                applyBrightness(value)
                                flash("亮度 ${(value * 100).roundToInt()}%")
                            } else {
                                val value = (startVolume + ratio * maxVolume)
                                    .roundToInt()
                                    .coerceIn(0, maxVolume)
                                audio.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0)
                                flash("音量 ${value * 100 / maxVolume}%")
                            }
                        },
                    )
                }
                .pointerInput(speed, speedMenu) {
                    detectTapGestures(
                        onTap = {
                            if (speedMenu) {
                                speedMenu = false
                            } else {
                                val view = playerView
                                if (view != null) {
                                    if (view.isControllerFullyVisible) view.hideController()
                                    else view.showController()
                                }
                            }
                        },
                    )
                }
                // Long press is timed by hand: the detector above loses the press as soon as the
                // drag gesture in the same node claims movement, and a still finger produces no
                // pointer events at all, so a timeout is the only reliable trigger.
                .pointerInput(speed) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
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

                        player.setPlaybackSpeed(3f)
                        flash("3× 快进中")
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.none { it.pressed }) break
                        }
                        player.setPlaybackSpeed(speed)
                        flash(speedLabel(speed))
                    }
                },
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HighlightedIconButton(onClick = { nav.pop() }) {
                Icon(COUIIcons.Close, "关闭", Modifier, tint = Color.White)
            }
            Text(title, color = Color.White, fontSize = 14.sp, maxLines = 1)
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(8.dp),
            horizontalAlignment = Alignment.End,
        ) {
            // Black-on-black is invisible when the video is dark, so both the button and the
            // menu use a solid ink panel instead of a translucent black.
            Box(
                modifier = Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(SpeedPanel)
                    .clickable { speedMenu = !speedMenu }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = speedLabel(speed), color = Color.White, fontSize = 13.sp)
            }
            if (speedMenu) {
                Spacer(Modifier.height(6.dp))
                Column(
                    modifier = Modifier
                        .width(108.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SpeedPanel)
                        .padding(vertical = 4.dp),
                ) {
                    Speeds.forEach { value ->
                        val selected = speed == value
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    speed = value
                                    player.setPlaybackSpeed(value)
                                    speedMenu = false
                                    flash("${speedLabel(value)} 倍速")
                                }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = speedLabel(value),
                                color = if (selected) Color(0xFF1A8CFF) else Color.White,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            }
        }

        if (hud.isNotEmpty()) {
            Text(
                text = hud,
                color = Color.White,
                fontSize = 15.sp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}
