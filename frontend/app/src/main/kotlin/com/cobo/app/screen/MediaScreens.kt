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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.cobo.app.ui.NavState
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.math.roundToInt

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

    BackHandler {
        if (menu) menu = false else nav.pop()
    }

    val backdrop = rememberLayerBackdrop()

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().layerBackdrop(backdrop),
        ) { page ->
            // Only the visible page decodes at full resolution; preloading neighbours that way
            // would hold several full-size bitmaps at once.
            Box(Modifier.fillMaxSize().preciseClickAnchor(precise)) {
                ZoomableImage(
                    url = urls[page],
                    original = original && page == pagerState.currentPage,
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
    original: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    val context = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Coil decodes at layout size by default, which is why a pinch looks soft. Asking for the
    // full bitmap is what makes zooming actually reveal detail.
    val model: Any = remember(url, original, context) {
        if (original) {
            ImageRequest.Builder(context)
                .data(originalSize(url))
                .size(Size.ORIGINAL)
                .build()
        } else {
            url
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onLongPress = { onLongPress() },
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.2f
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                // Only pinch and zoomed panning consume touches. An unconsumed horizontal drag at
                // 1x has to reach the pager, or the gallery can never change page.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        if (pressed >= 2) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            scale = (scale * zoom).coerceIn(1f, 4f)
                            offset = if (scale > 1f) offset + pan else Offset.Zero
                            event.changes.forEach { it.consume() }
                        } else if (pressed == 1 && scale > 1f) {
                            offset += event.calculatePan()
                            event.changes.forEach { it.consume() }
                        }
                        if (event.changes.none { it.pressed }) break
                    }
                }
            },
    ) {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
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

    BackHandler { nav.pop() }

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
