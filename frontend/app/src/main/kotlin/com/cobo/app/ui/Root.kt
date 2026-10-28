package com.cobo.app.ui

import android.app.Activity
import android.os.Build
import android.view.RoundedCorner
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import com.cobo.app.App
import com.cobo.app.data.UpdateCheckInterval
import com.cobo.app.data.Updater
import com.cobo.app.screen.AccountsScreen
import com.cobo.app.screen.ChatScreen
import com.cobo.app.screen.ContainerScreen
import com.cobo.app.screen.DetailScreen
import com.cobo.app.screen.DraftsScreen
import com.cobo.app.screen.EditHistoryScreen
import com.cobo.app.screen.HistoryScreen
import com.cobo.app.screen.ImageViewerScreen
import com.cobo.app.screen.LoginScreen
import com.cobo.app.screen.NearbyScreen
import com.cobo.app.screen.ProfileScreen
import com.cobo.app.screen.PublishScreen
import com.cobo.app.screen.SearchScreen
import com.cobo.app.screen.SettingsScreen
import com.cobo.app.screen.TopicScreen
import com.cobo.app.screen.VideoScreen
import com.cobo.app.screen.WebScreen
import com.cobo.app.ui.components.ImageSaver
import com.cobo.app.ui.components.LocalImageSaver
import com.cobo.app.ui.components.SaveHud
import com.cobo.app.ui.components.SaveImagesMenu
import com.cobo.app.ui.components.UpdateDialog
import com.cobo.app.ui.components.rememberImageSaveState
import io.github.suqi8.coui.kmp.basic.PreciseClickState
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.layerBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.squircle.addSquircleRect
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.COUIPopupUtils.Companion.COUIPopupHost
import io.github.suqi8.coui.kmp.utils.rememberGalleryDensity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * How far a back gesture must pull before the page sits back on its card. A release from there
 * returns for good: the frame is already home, so springing the page open again reads as a bug.
 */
private const val LandedProgress = 0.2f

@Composable
fun CoboRoot() {
    val nav = remember { NavState() }
    val scope = rememberCoroutineScope()
    SideEffect { nav.attach(scope) }

    // 启动时静默查一次更新：开关打开、且距上次检查超过节流窗口才真的发请求。
    LaunchedEffect(Unit) {
        if (!App.settings.autoCheckUpdate) return@LaunchedEffect
        val elapsed = System.currentTimeMillis() - App.settings.updateCheckedAt
        if (elapsed < UpdateCheckInterval) return@LaunchedEffect
        App.checkUpdate(manual = false)
    }

    // The search field keeps focus after its result opens a page, and the IME's candidate bar
    // then floats over whatever is on top. Drop focus on every navigation.
    val focusManager = LocalFocusManager.current
    LaunchedEffect(nav.stack.size) { focusManager.clearFocus(force = true) }

    CoboPredictiveBackHandler(nav)

    val linkHandler = remember(nav, scope) {
        { raw: String ->
            // 「评论配图」是 weibo.cn/sinaurl?u=<图片直链>，先拆出真实地址再决定去哪。
            val url = unwrapSinaUrl(raw) ?: raw
            val statusId = weiboStatusId(url)
            val uid = weiboUserId(url)
            val mention = weiboMentionName(url)
            when {
                isImageLink(url) -> nav.push(Route.Images(listOf(url), 0))
                statusId != null -> nav.push(Route.Detail(statusId, ""))
                uid != null -> nav.push(Route.Profile(uid))
                // @ 只有昵称，得先换成 uid；换不到才退回内置 WebView。
                mention != null -> scope.launch {
                    val target = runCatching { App.repo.uidForName(mention) }.getOrNull()
                    if (target != null) {
                        nav.push(Route.Profile(target))
                    } else {
                        resolveWeiboLink(raw)?.let { nav.push(Route.Web(it, mention)) }
                    }
                }
                else -> resolveWeiboLink(url)?.let { nav.push(Route.Web(it, "网页")) }
            }
            Unit
        }
    }

    // 长按缩略图弹「保存原图」的菜单挂在根节点，任何页面都不用自己搭浮层。
    var saveTargets by remember { mutableStateOf<List<String>?>(null) }
    var saveIndex by remember { mutableIntStateOf(0) }
    var savePrecise by remember { mutableStateOf<PreciseClickState?>(null) }
    val saveState = rememberImageSaveState()
    val imageSaver = remember {
        ImageSaver { urls, index, precise ->
            saveTargets = urls
            saveIndex = index
            savePrecise = precise
        }
    }
    // 玻璃菜单要有一层能采样的底：把整摞路由录进 backdrop，菜单浮在上面做模糊和外发光。
    // 录制节点和读 backdrop 的节点必须是兄弟，否则菜单会把自己模糊过的样子再采一遍。
    val rootBackdrop = rememberLayerBackdrop { drawContent() }

    // ColorOS 自家应用（相册、设置、桌面）都把窗口钉在 360dp 短边，因此不受系统「显示大小」影响。
    // 想和它们逐像素一致只有一条路：整棵树也用这个密度（见 coui docs/coui-density-spec.md）。
    // 顶栏按钮 40dp、底栏条 56dp / 项 48dp / 圆钮 56dp 都会因此落到 141 / 198 / 170 / 198px。
    val galleryDensity = rememberGalleryDensity()
    CompositionLocalProvider(
        LocalDensity provides galleryDensity,
        LocalLinkHandler provides linkHandler,
        LocalImageSaver provides imageSaver,
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .semantics { testTagsAsResourceId = true }
        ) {
            val widthPx = constraints.maxWidth.toFloat()
            val screenCorner = rememberScreenCornerRadius()
            Box(
                Modifier
                    .fillMaxSize()
                    .layerBackdrop(rootBackdrop)
            ) {
                nav.stack.forEachIndexed { index, entry ->
                    key(entry.id) {
                        RouteLayer(
                            entry = entry,
                            index = index,
                            lastIndex = nav.stack.lastIndex,
                            widthPx = widthPx,
                            screenCorner = screenCorner,
                            nav = nav,
                        )
                    }
                }
            }

            // HUD 独立于菜单，菜单收起之后它还要自己活 1.6 秒。
            Box(Modifier.fillMaxSize().zIndex(10f)) { SaveHud(saveState) }

            // 菜单走独立窗口宿主（见 SaveImagesMenu）：收起一开始就把触摸交还给页面，不必等它
            // 完全收回去才能滑屏。独立窗口采不到页面内容，菜单弹出时自己抓一帧页面当玻璃底；
            // 这块 backdrop 只给本窗口里别的玻璃控件用。
            Box(
                Modifier
                    .fillMaxSize()
                    .zIndex(20f)
            ) {
                CompositionLocalProvider(LocalBackdrop provides rootBackdrop) {
                    SaveImagesMenu(
                        urls = saveTargets,
                        index = saveIndex,
                        precise = savePrecise,
                        onDismiss = { saveTargets = null },
                        state = saveState,
                    )
                }
                COUIPopupHost()
            }

            // 更新提示挂在根节点：自动检查与设置页的手动检查都往 App.pendingUpdate 里写。
            UpdatePrompt()
        }
    }
}

/** 单独一层，只让它读 [App.pendingUpdate]，免得更新状态一变就重组整摞路由。 */
@Composable
private fun UpdatePrompt() {
    val info = App.pendingUpdate ?: return
    UpdateDialog(
        info = info,
        onDismiss = { App.dismissUpdate() },
        onSkip = {
            App.settings.applySkippedUpdate(info.version)
            App.dismissUpdate()
        },
        onDownload = {
            Updater.openDownload(info)
            App.dismissUpdate()
        },
    )
}

/**
 * Drives the page-follow back animation. Routes with their own nested back handler (the search
 * page's SearchBar) call this again after that handler so the predictive gesture reaches the
 * navigator instead of collapsing the route without an animation.
 */
@Composable
fun CoboPredictiveBackHandler(nav: NavState) {
    val context = LocalContext.current
    PredictiveBackHandler(enabled = true) { events ->
        val entry = nav.stack.lastOrNull()
        if (entry == null || nav.stack.size <= 1) {
            // Nothing to reveal. Consume the gesture so the system does not run its own
            // scale-and-dim preview, then leave the app when the finger lifts.
            events.collect { }
            (context as? Activity)?.moveTaskToBack(true)
            return@PredictiveBackHandler
        }
        if (entry.exiting) {
            events.collect { }
            return@PredictiveBackHandler
        }

        var lastProgress = 1f
        var moved = false
        entry.previewing = true
        // The snapshot was recorded when the card was tapped; re-record it now so the page
        // hands back the card as it looks today, not as it looked under the finger.
        entry.origin?.let { it.refreshKey++ }
        try {
            events.collect { event ->
                val value = (1f - event.progress).coerceIn(0f, 1f)
                if (value < 0.98f) moved = true
                lastProgress = value
                entry.progress.snapTo(value)
                if (entry.expand) entry.fade.snapTo(value)
            }
            // Released past the threshold: finish from wherever the finger left the page.
            if (lastProgress <= 0.02f && moved) {
                nav.completePredictivePop(entry)
            } else {
                nav.pop()
            }
        } catch (cancelled: CancellationException) {
            // The page has already collapsed onto its card: the system calls that a cancelled
            // gesture, but there is nothing left to preview, so it goes instead of springing
            // back open under the finger.
            if (lastProgress <= LandedProgress && moved) {
                nav.completePredictivePop(entry)
                return@PredictiveBackHandler
            }
            // Released early: ease the page back under the finger.
            withContext(NonCancellable) {
                coroutineScope {
                    launch {
                        entry.progress.animateTo(1f, tween(180, easing = ExpandEasing))
                    }
                    if (entry.expand) {
                        launch {
                            entry.fade.animateTo(1f, tween(180, easing = LinearEasing))
                        }
                    }
                }
                entry.previewing = false
            }
            throw cancelled
        }
    }
}

@Composable
private fun RouteLayer(
    entry: NavEntry,
    index: Int,
    lastIndex: Int,
    widthPx: Float,
    screenCorner: Dp,
    nav: NavState,
) {
    // Read in layer and draw blocks only, so a running gesture invalidates the layer
    // instead of recomposing the whole route.
    val progress: () -> Float = { entry.progress.value }
    val isTop = index == lastIndex
    val belowTop = index == lastIndex - 1
    val top = nav.stack.lastOrNull()
    val topProgress: () -> Float = { top?.progress?.value ?: 1f }
    val slide = top != null && !top.expand
    // ColorOS draws the screen corners as an Lp superellipse (hwui "G2", n≈2.5), not a circular
    // arc. A RoundedCornerShape parts from that curve by 0.0718 * radius along the diagonal —
    // 11px at this device's 150px corner — so the four corners showed a crescent of window
    // background. SquircleShape traces the same silhouette.
    val cornerShape = remember(screenCorner) { SquircleShape(screenCorner) }

    val layer = Modifier
        .fillMaxSize()
        .zIndex(if (isTop) 2f else 1f)
        .then(
            if (isTop) {
                // Consume taps on empty areas so they cannot fall through to the page
                // below, but let drags reach the scrollable content.
                Modifier.pointerInput(Unit) { detectTapGestures { } }
            } else {
                Modifier
            }
        )
        .graphicsLayer {
            if (slide) {
                translationX = when {
                    isTop -> (1f - progress()) * widthPx
                    belowTop -> -topProgress() * widthPx / RouteParallaxDivisor
                    else -> 0f
                }
                shape = cornerShape
                clip = true
            }
        }

    Box(layer) {
        if (entry.expand) {
            ExpandLayer(entry, screenCorner, progress, { entry.fade.value }) {
                RouteContent(entry.route, nav)
            }
        } else {
            RouteContent(entry.route, nav)
        }
    }
}

@Composable
private fun ExpandLayer(
    entry: NavEntry,
    screenCorner: Dp,
    progress: () -> Float,
    fade: () -> Float,
    content: @Composable () -> Unit,
) {
    val origin = entry.origin ?: return
    val bounds = origin.bounds ?: return
    val density = LocalDensity.current
    val statusBarTop = with(density) {
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding().toPx()
    }
    val surface = pageContainerColor()
    // A gesture pulling the page away is an exit too: the frame has to hand back to the card
    // under the finger instead of waiting for the pop.
    val exiting = entry.exiting || entry.previewing
    // On the way out the frame lands on the card this early in the gesture, so a finger held
    // near the end of a back swipe already shows the page exactly as the release will leave it.
    fun frameProgress(p: Float): Float =
        if (exiting) ((p - LandedProgress) / (1f - LandedProgress)).coerceIn(0f, 1f) else p

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                // Only while the page grows over the one it came from. On the way out the
                // page underneath is exactly what the user is returning to, and dimming it
                // made the reveal land on a brighter screen than the one the gesture showed.
                if (!exiting) {
                    drawRect(Color.Black, alpha = 0.3f * progress())
                }
            }
            .graphicsLayer {
                val p = frameProgress(progress())
                val left = lerp(bounds.left, 0f, p)
                val top = lerp(bounds.top, 0f, p)
                val width = lerp(bounds.width, size.width, p)
                val height = lerp(bounds.height, size.height, p)
                val scale = if (size.width > 0f && width > 0f) width / size.width else 1f
                transformOrigin = TransformOrigin(0f, 0f)
                translationX = left
                translationY = top
                scaleX = scale
                scaleY = scale
                val radius = lerp(
                    origin.cornerRadius.toPx(),
                    screenCorner.toPx(),
                    p,
                ) / scale
                shape = ExpandShape(height / scale, radius)
                clip = true
            }
            // Drawn inside the frame's layer, so the card is clipped to the frame and travels
            // with it instead of being painted over the list around it.
            .drawWithContent {
                val p = frameProgress(progress())
                // On the way out the card fades back in over the page between these two points,
                // cross-fading instead of leaving a gap: a back gesture can hold the frame
                // anywhere, and a gap would show the bare fill for as long as the finger stays.
                val cardAlpha = if (exiting) {
                    ((0.4f - p) / 0.32f).coerceIn(0f, 1f)
                } else {
                    1f - (fade() / 0.25f).coerceIn(0f, 1f)
                }
                drawContent()
                val layerSize = origin.layer.size
                if (cardAlpha > 0f && layerSize.width > 0 && layerSize.height > 0 && bounds.width > 0f) {
                    // Scaled by width alone and anchored at the frame's top-left: the card keeps
                    // its own proportions, and a snapshot taller than the card is cropped by the
                    // frame rather than squashed into it.
                    val k = size.width / bounds.width
                    withTransform({ scale(k, k, pivot = Offset.Zero) }) {
                        clipRect(0f, 0f, bounds.width, bounds.height) {
                            drawIntoCanvas { canvas ->
                                canvas.saveLayer(
                                    Rect(0f, 0f, bounds.width, bounds.height),
                                    Paint().apply { alpha = cardAlpha },
                                )
                                // The card's surface is a translucent tint that the snapshot carries
                                // without a base; filling the page tone behind it keeps the page from
                                // showing through and keeps the card the same tone as in the list.
                                drawRect(surface, size = bounds.size)
                                drawLayer(origin.layer)
                                canvas.restore()
                            }
                        }
                    }
                }
            }
    ) {
        Box(
            Modifier
                .fillMaxSize()
                // The frame keeps the page's own tone whatever the page is doing: on the way out
                // the list underneath would otherwise show through the shrinking frame.
                .background(surface)
                .graphicsLayer {
                    // Give way to the card as it fades back in, so one of the two always covers
                    // the frame and there is never a bare fill on screen.
                    val p = frameProgress(progress())
                    alpha = if (exiting) {
                        1f - ((0.4f - p) / 0.32f).coerceIn(0f, 1f)
                    } else {
                        1f
                    }
                }
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val f = fade()
                        val p = frameProgress(progress())
                        alpha = if (exiting) 1f else ((f - 0.25f) / 0.3f).coerceIn(0f, 1f)
                        translationY = -statusBarTop * (1f - p)
                    }
            ) {
                content()
            }
        }
    }
}

@Composable
private fun RouteContent(route: Route, nav: NavState) {
    when (route) {
        Route.Main -> MainShell(nav)
        is Route.Login -> LoginScreen(nav, route.addAccount, route.fresh)
        is Route.Detail -> DetailScreen(nav, route.id, route.mid, route.readOnly || App.repo.guest)
        is Route.EditHistory -> EditHistoryScreen(nav, route.mid)
        is Route.Profile -> ProfileScreen(nav, route.uid)
        Route.Search -> SearchScreen(nav)
        is Route.Publish -> PublishScreen(nav, route.draft)
        is Route.Chat -> ChatScreen(nav, route.uid, route.name)
        is Route.Web -> WebScreen(nav, route.url, route.title)
        is Route.Container -> ContainerScreen(nav, route.cid, route.title)
        Route.Drafts -> DraftsScreen(nav)
        Route.History -> HistoryScreen(nav)
        Route.Accounts -> AccountsScreen(nav)
        Route.Settings -> SettingsScreen(nav)
        Route.Nearby -> NearbyScreen(nav)
        is Route.Topic -> TopicScreen(nav, route.name)
        is Route.Images -> ImageViewerScreen(nav, route.urls, route.index)
        is Route.Video -> VideoScreen(nav, route.url, route.title)
    }
}

private class ExpandShape(
    private val heightPx: Float,
    private val radiusPx: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val height = heightPx.coerceIn(0f, size.height)
        // Same smooth-corner silhouette as the resting frame: a circular corner here would
        // pull away from the screen's squircle for the last stretch of the growth.
        val path = Path()
        path.addSquircleRect(
            width = size.width,
            height = height,
            cornerRadius = radiusPx,
        )
        return Outline.Generic(path)
    }
}

@Composable
private fun rememberScreenCornerRadius(): Dp {
    val context = LocalContext.current
    val density = LocalDensity.current
    return remember(context, density) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                val radius = context.display
                    ?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)
                    ?.radius ?: 0
                with(density) { radius.toDp() }
            }.getOrDefault(0.dp)
        } else {
            0.dp
        }
    }
}
