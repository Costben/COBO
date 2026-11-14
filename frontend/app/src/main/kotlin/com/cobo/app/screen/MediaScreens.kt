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
import java.io.File
import java.security.MessageDigest
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
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
import com.cobo.app.data.Pic
import com.cobo.app.ui.CoboPredictiveBackHandler
import com.cobo.app.ui.ExpandEasing
import com.cobo.app.ui.ImageOrigins
import com.cobo.app.ui.LocalImageOrigins
import com.cobo.app.ui.LocalRouteTransition
import com.cobo.app.ui.NavState
import com.cobo.app.ui.blurEffectsEnabled
import com.cobo.app.ui.layerBackdropIf
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
import io.github.suqi8.coui.kmp.utils.CouiHapticEffect
import io.github.suqi8.coui.kmp.utils.rememberCouiHaptic
import io.github.suqi8.coui.kmp.icon.extended.Close
import io.github.suqi8.coui.kmp.icon.extended.Download
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
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
fun ImageViewerScreen(nav: NavState, pics: List<Pic>, index: Int) {
    val urls = pics.map { it.large }
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

    val currentPic = pics.getOrNull(pagerState.currentPage)

    // 记下正在看的是哪一张：被它盖住的那枚缩略图角标要等这一页撤走再淡回来，别的缩略图不动。
    SideEffect { origins.viewing = current }
    DisposableEffect(Unit) { onDispose { origins.viewing = null } }

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
                ZoomableImage(
                    url = urls[page],
                    live = pics[page].live,
                    thumbModel = origins.modelOf(urls[page]),
                    original = original && page == pagerState.currentPage,
                    isCurrent = pagerState.currentPage == page,
                    isSettledElsewhere = pagerState.settledPage != page,
                    canPage = urls.size > 1,
                    isPagerScrolling = { abs(pagerState.currentPageOffsetFraction) > 0.01f },
                    canStartDismiss = {
                        transition?.exiting?.invoke() != true && progress() >= 0.98f
                    },
                    progress = progress,
                    origins = origins,
                    frame = frame,
                    chromeVisible = chrome && !frame.dismiss.dragging && !frame.dismiss.exiting,
                    onTap = { if (!frame.dismiss.exiting) chrome = !chrome },
                    onLongPress = {
                        if (!saver.saving && !frame.dismiss.exiting && !frame.dismiss.active) {
                            menu = true
                            chrome = true
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
                        ViewerGlyph(
                            plate = plate,
                            alpha = plateAlpha,
                            onClick = {
                                if (!frame.dismiss.exiting && !frame.dismiss.dragging) {
                                    frame.dismiss.cancelActive()
                                    nav.pop()
                                }
                            },
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
                                    if (!frame.dismiss.exiting && !frame.dismiss.dragging) {
                                        original = !original
                                        saver.flash(if (original) "已切换到原图" else "已切回标准尺寸")
                                    }
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
                            onClick = {
                                if (!frame.dismiss.exiting && !frame.dismiss.dragging) {
                                    saver.saveOne(current)
                                }
                            },
                        ) {
                            Icon(COUIIcons.Download, "保存原图", Modifier.size(19.dp), tint = ink)
                        }
                    }
                }

                // 实况标记：跟顶栏那些胶囊共用一套玻璃底，图标取自相册的实况图标。
                if (currentPic?.isLive == true) {
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .graphicsLayer { alpha = chromeFade() }
                            .navigationBarsPadding()
                            .padding(start = 12.dp, bottom = 12.dp),
                    ) {
                        LivePill(plate = plate, plateAlpha = plateAlpha, ink = ink)
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

/** 看图页停住时顶栏左下角那颗「实况」胶囊，也是退场那枚标记的起点。 */
@Composable
private fun LivePill(
    plate: Color,
    plateAlpha: Float,
    ink: Color,
) {
    Row(
        // 玻璃底会把挂在它前面的修饰符整条丢掉，尺寸和留白只能排在它后面。
        modifier = Modifier
            .viewerGlass(RoundedCornerShape(BarRadius), plate, plateAlpha)
            .height(BarHeight)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(com.cobo.app.R.drawable.ic_live_photo),
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = ink,
        )
        Spacer(Modifier.width(6.dp))
        Text("实况", color = ink, fontSize = 13.sp)
    }
}

private val MediaCacheClient by lazy { OkHttpClient.Builder().followRedirects(true).build() }

/** 看图页打开时顺手下的静图和实况视频，按地址哈希落在应用缓存里，清缓存时一起删。 */
private fun mediaCacheDir(context: Context): File =
    File(context.cacheDir, "live").apply { mkdirs() }

private fun mediaCacheFile(context: Context, url: String, ext: String): File {
    val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
    val name = digest.joinToString("") { "%02x".format(it) }
    return File(mediaCacheDir(context), "$name.$ext")
}

/**
 * 把 [url] 下到 [dest]。已有且不是空文件就直接用；下载写到临时文件，完整了再换名，
 * 避免按住时读到写了一半的视频。
 */
private suspend fun cacheMedia(context: Context, url: String, dest: File): File? =
    withContext(Dispatchers.IO) {
        if (dest.length() > 0L) return@withContext dest
        runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .header("Referer", "https://m.weibo.cn/")
                .build()
            MediaCacheClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body ?: return@use null
                val tmp = File(dest.parentFile, dest.name + ".part")
                tmp.outputStream().use { out -> body.byteStream().copyTo(out) }
                if (tmp.length() <= 0L) {
                    tmp.delete()
                    return@use null
                }
                if (!tmp.renameTo(dest)) tmp.copyTo(dest, overwrite = true).also { tmp.delete() }
                dest
            }
        }.getOrNull()
    }

@Composable
private fun ZoomableImage(
    url: String,
    live: String,
    thumbModel: String?,
    original: Boolean,
    isCurrent: Boolean,
    isSettledElsewhere: Boolean,
    canPage: Boolean,
    isPagerScrolling: () -> Boolean,
    canStartDismiss: () -> Boolean,
    progress: () -> Float,
    origins: ImageOrigins,
    frame: ViewerFrame,
    chromeVisible: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDismissRelease: (Velocity) -> Unit,
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
    var loaded by remember(url) { mutableStateOf(false) }
    // 实况只在手指按住时播，松手回到静图。放大之后手势归缩放层，不再触发播放。
    var holding by remember { mutableStateOf(false) }
    // 第一帧出来之前不盖静图：PlayerView 默认是黑底，地址播不出来时整页会变成黑屏。
    var liveReady by remember(live) { mutableStateOf(false) }
    var liveFailed by remember(live) { mutableStateOf(false) }
    // 打开这一页就把静图和实况视频一起下进缓存；播的时候优先用本地文件。
    var liveFile by remember(live) { mutableStateOf<File?>(null) }
    LaunchedEffect(url, live, isCurrent) {
        if (!isCurrent) return@LaunchedEffect
        if (remote) {
            launch { cacheMedia(context, url, mediaCacheFile(context, url, "img")) }
        }
        if (live.isNotBlank()) {
            liveFile = cacheMedia(context, live, mediaCacheFile(context, live, "mov"))
        }
    }
    val playing = holding && liveFile != null && isCurrent && scale <= 1f + ZoomEpsilon
    val livePlayer = remember(liveFile) {
        val file = liveFile
        if (file == null) {
            null
        } else {
            ExoPlayer.Builder(context).build().apply {
                volume = 0f
                repeatMode = androidx.media3.common.Player.REPEAT_MODE_OFF
                playWhenReady = false
                setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(file)))
                addListener(object : androidx.media3.common.Player.Listener {
                    override fun onRenderedFirstFrame() {
                        liveReady = true
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        liveFailed = true
                        android.util.Log.e("CoboLive", "${error.errorCodeName} ${file.name}", error)
                    }
                })
                prepare()
            }
        }
    }
    DisposableEffect(livePlayer) {
        onDispose { livePlayer?.release() }
    }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val couiHaptic = rememberCouiHaptic()
    var edgeHit by remember(url) { mutableIntStateOf(0) }
    LaunchedEffect(playing) {
        val player = livePlayer ?: return@LaunchedEffect
        if (playing) {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            player.seekTo(0)
            player.play()
        } else {
            // 停在松手这一帧上淡回静图。先跳回开头再淡出，第一帧会在过渡里闪一下。
            player.pause()
            delay(LiveFadeMillis.toLong())
            if (!playing) player.seekTo(0)
        }
    }
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
                                openFrame(p, thumb, frame.aspects[url] ?: thumb?.aspect)
                            }
                        }
                    }
                },
        ) {
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

            // 实况视频跟随视觉层一起缩放与拖拽；自身不接管触摸，手势由外层静态手势层处理。
            // 实况视频的像素尺寸和旋转跟静图不一样，铺满整屏会拉变形。按静图的比例裁切。
            // 层本身一直在，进出只改透明度，避免整块视图插进来时闪一下。
            if (livePlayer != null) {
                val liveAlpha by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (playing) 1f else 0f,
                    animationSpec = tween(LiveFadeMillis),
                    label = "live",
                )
                val rest = shown()
                val fitted = if (rest.width > 0f && rest.height > 0f && box != IntSize.Zero) {
                    Modifier
                        .requiredSize(
                            with(density) { rest.width.toDp() },
                            with(density) { rest.height.toDp() },
                        )
                        .align(Alignment.Center)
                        .graphicsLayer {
                            alpha = liveAlpha
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        }
                        .clipToBounds()
                } else {
                    Modifier.fillMaxSize().graphicsLayer { alpha = liveAlpha }
                }
                AndroidView(
                    factory = { ctx ->
                        playerView(ctx, livePlayer, controls = false).apply {
                            resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        }
                    },
                    modifier = fitted,
                    update = { view ->
                        view.player = livePlayer
                        val format = livePlayer.videoFormat
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
                )
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
            Text(
                text = liveStatus,
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 28.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }

        // Gestures sit on an untransformed full-screen layer so screen-space drag and fling velocity stay exact.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(live) {
                    if (live.isBlank()) {
                        detectTapGestures(
                            onTap = { if (!frame.dismiss.active && !frame.dismiss.exiting) onTap() },
                            onDoubleTap = { if (!frame.dismiss.active && !frame.dismiss.exiting) toggleZoom(it) },
                            onLongPress = { if (!frame.dismiss.active && !frame.dismiss.exiting) onLongPress() },
                        )
                    } else {
                        detectLivePress(
                            onTap = { if (!frame.dismiss.active && !frame.dismiss.exiting) onTap() },
                            onDoubleTap = { if (!frame.dismiss.active && !frame.dismiss.exiting) toggleZoom(it) },
                            onPlay = { if (!frame.dismiss.active && !frame.dismiss.exiting) holding = true },
                            onStop = { holding = false },
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
                            holding = false
                            if (frame.dismiss.active && !frame.dismiss.exiting) {
                                frame.dismiss.animJob?.cancel()
                            }
                        },
                        onRelease = { velocity, taken ->
                            if (mode == GestureMode.DISMISS || (frame.dismiss.active && !frame.dismiss.exiting)) {
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
                            holding = false
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
                                    holding = false
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
    }
}

/**
 * Pinch and drag detection that only consumes what it uses. [onGesture] gets the centroid, the pan,
 * the zoom factor and the number of fingers down, and returns whether it took the movement; when it
 * did not, the pointer events stay free for a parent such as the pager. [onStart] runs as the
 * fingers land; [onRelease] once they are all up, with the speed the last one left at and whether
 * the gesture took anything at all.
 */
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
    val hasThumb = thumb != null && rest != null && rest.height <= h + 0.5f

    dismiss.exitStartScale = startScale
    dismiss.exitStartTranslation = startTranslation

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

            coroutineScope {
                launch {
                    dismiss.exitBgAlpha.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
                }
                launch {
                    dismiss.exitProgress.animateTo(1f, tween(320, easing = ExpandEasing))
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
            // 无原缩略图（如长图/不可见原位）直接飞出淡出，此时恢复底层缩略图避免淡出时出现空洞。
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
    clip = false
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
    val cover = max(source.width / rest.width, source.height / rest.height)
    val curScale = lerp(d.exitStartScale, cover, p)
    val trans = d.exitTranslation.value
    alpha = 1f
    transformOrigin = TransformOrigin.Center
    scaleX = curScale
    scaleY = curScale
    translationX = trans.x
    translationY = trans.y
    shape = CenteredWindow(
        width = lerp(size.width * d.exitStartScale, source.width, p) / curScale,
        height = lerp(size.height * d.exitStartScale, source.height, p) / curScale,
        radius = lerp(0f, thumb.cornerRadius, p) / curScale,
    )
    clip = true
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
                playerView(ctx, player, controls = true).also { playerView = it }
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
