package com.cobo.app.ui

import android.os.Build
import android.view.RoundedCorner
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
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
import com.cobo.app.data.Pic
import com.cobo.app.data.Updater
import com.cobo.app.data.superTopicId
import com.cobo.app.screen.AboutScreen
import com.cobo.app.screen.AccountsScreen
import com.cobo.app.screen.ChangelogScreen
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
import com.cobo.app.screen.RelationsScreen
import com.cobo.app.screen.SettingsScreen
import com.cobo.app.screen.SmsLoginScreen
import com.cobo.app.screen.TopicScreen
import com.cobo.app.screen.SuperTopicScreen
import com.cobo.app.screen.VideoScreen
import com.cobo.app.screen.VoteScreen
import com.cobo.app.screen.WebScreen
import com.cobo.app.ui.components.ConfirmDialog
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

    // 启动时顺手续一次登录态：客户端会话换 cookie，网页会话按浏览器的样子走一趟拿新 cookie。
    LaunchedEffect(Unit) { App.repo.refreshClientSessionIfStale() }

    // 启动时静默查一次更新：开关打开后每次启动应用自动发请求。
    LaunchedEffect(Unit) {
        if (!App.settings.autoCheckUpdate) return@LaunchedEffect
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
            val voteId = weiboVoteId(url)
            val statusId = weiboStatusId(url)
            val uid = weiboUserId(url)
            val mention = weiboMentionName(url)
            val topicId = superTopicId(url)
            val current = nav.stack.lastOrNull()?.route
            val readOnly = current is Route.SuperTopic || (current is Route.Detail && current.readOnly)
            when {
                topicId != null -> nav.push(Route.SuperTopic(topicId, resolveWeiboLink(url)
                    ?: "https://m.weibo.cn/p/index?containerid=$topicId"))
                voteId != null -> nav.push(if (readOnly) {
                    Route.Web("https://vote.weibo.com/h5/index/index?vote_id=$voteId", "投票")
                } else Route.Vote(voteId))
                isImageLink(url) -> nav.push(Route.Images(listOf(Pic(thumb = url, large = url)), 0))
                statusId != null -> nav.push(Route.Detail(statusId, "", readOnly))
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
                isWeiboShortLink(url) -> scope.launch {
                    val resolved = App.repo.resolveShortUrl(url)
                    val vid = weiboVoteId(resolved)
                    val resolvedTopicId = superTopicId(resolved)
                    if (resolvedTopicId != null) {
                        nav.push(Route.SuperTopic(resolvedTopicId, resolveWeiboLink(resolved)
                            ?: "https://m.weibo.cn/p/index?containerid=$resolvedTopicId"))
                    } else if (vid != null) {
                        nav.push(if (readOnly) {
                            Route.Web("https://vote.weibo.com/h5/index/index?vote_id=$vid", "投票")
                        } else Route.Vote(vid))
                    } else {
                        val sid = weiboStatusId(resolved)
                        if (sid != null) {
                            nav.push(Route.Detail(sid, "", readOnly))
                        } else {
                            resolveWeiboLink(resolved)?.let { nav.push(Route.Web(it, "网页")) }
                        }
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
    val blur = blurEffectsEnabled()
    // 缩略图把自己的屏幕位置登记在这里，看图页才能从被点的那张长出来、再缩回去。
    val imageOrigins = remember { ImageOrigins() }

    // 顶栏和底栏在各自的节点里钉密度（见 [PinnedBarDensity]），页面内容跟随系统「显示大小」。
    // 以前这里把整棵树都钉在 360dp 短边，结果正文字号也跟着缩了一档。
    CompositionLocalProvider(
        LocalLinkHandler provides linkHandler,
        LocalImageSaver provides imageSaver,
        LocalImageOrigins provides imageOrigins,
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
                    .layerBackdropIf(blur, rootBackdrop)
            ) {
                // 每页都按屏幕超级椭圆裁角，裁掉的那一圈是透明的。窗口占满屏幕时它正好落在屏幕
                // 之外，看不见；下拉通知中心、控制中心会让窗口整块下沉缩小，那一圈就翻到屏幕里
                // 侧来了，而栈里每一页裁的是同一个角，于是四角一路漏到下面那一层路由上去。
                val cornerShape = remember(screenCorner) { SquircleShape(screenCorner) }
                val pageSurface = pageContainerColor()
                Box(Modifier.fillMaxSize().background(pageSurface))
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
                // 所以四角补一层页面底色：只画在超级椭圆外侧，页面本身一个像素都不盖，窗口怎么
                // 缩、缩到哪个大小，漏出来的都还是这一页的调子。
                Box(
                    Modifier
                        .fillMaxSize()
                        .zIndex(3f)
                        .drawBehind {
                            // 页面静止时上面那一页不透明，四角本来就被盖住，这里什么都不用画。
                            // 只有转场（含返回手势预览）时页面才被裁掉角，才需要补这一层。
                            if (nav.stack.all { it.progress.value >= 1f }) return@drawBehind
                            val cut = when (val outline = cornerShape.createOutline(size, layoutDirection, this)) {
                                is Outline.Generic -> outline.path
                                is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
                                else -> return@drawBehind
                            }
                            clipPath(cut, ClipOp.Difference) { drawRect(pageSurface) }
                        },
                )
            }

            // HUD 独立于菜单，菜单收起之后它还要自己活 1.6 秒。
            Box(Modifier.fillMaxSize().zIndex(10f)) { SaveHud(saveState) }

            // 保存图片菜单与弹窗宿主均挂在根节点：遵循系统连续曲率超椭圆圆角与边缘光。
            Box(
                Modifier
                    .fillMaxSize()
                    .zIndex(20f)
            ) {
                CompositionLocalProvider(LocalBackdrop provides if (blur) rootBackdrop else null) {
                    SaveImagesMenu(
                        urls = saveTargets,
                        index = saveIndex,
                        precise = savePrecise,
                        onDismiss = { saveTargets = null },
                        state = saveState,
                    )
                    // 点赞前确认也挂在根节点：任何页面的爱心都走 App.confirmLike，由这里统一问一句。
                    LikeConfirmPrompt()
                }
                // 弹层宿主不提供 rootBackdrop：rootBackdrop 平时没有消费者、处于停止录制状态，
                // 弹层第一个读到它就会让它开始录制，整棵树重录一帧，压在下面的底栏玻璃会闪一下。
                // 让它自己回落到打开处的 backdrop（也就是用户正在看的那一页），既不再触发重录，
                // 弹层模糊的也还是它背后真实的内容。
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
        preview = App.previewingUpdate,
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
 * 点赞前确认。设置里打开后，爱心被点中的那一刻先在这里问一句，答复才回到
 * [App.confirmLike] 挂起的那个动作上。关掉弹窗、或者动作所在的页面被销毁，
 * 都会按「不赞」处理。取消赞不走这里，点了就直接生效。
 */
@Composable
private fun LikeConfirmPrompt() {
    val gate = App.pendingLikeConfirm ?: return
    // 连点两下时上一次的弹窗可能还在收；换一次确认就换一个实例，别复用上一次的 show 状态。
    key(gate) {
        ConfirmDialog(
            title = "点赞",
            message = "确定要点赞吗？",
            confirmText = "点赞",
            centered = true,
            onDismiss = { App.answerLikeConfirm(false) },
            onConfirm = { App.answerLikeConfirm(true) },
        )
    }
}

/**
 * Drives the page-follow back animation. Routes with their own nested back handler (the search
 * page's SearchBar) call this again after that handler so the predictive gesture reaches the
 * navigator instead of collapsing the route without an animation.
 *
 * 设置里关掉预测性返回后，这里换成普通的 [BackHandler]：返回键照常能用，只是页面不再跟着
 * 手指往回收，抬手才走。
 */
@Composable
fun CoboPredictiveBackHandler(nav: NavState) {
    // 根页面不注册返回回调：交给系统处理，才会播预测性返回退回桌面的动画。
    val hasPage = nav.stack.size > 1
    if (!App.settings.predictiveBack) {
        BackHandler(enabled = hasPage) {
            if (nav.stack.lastOrNull()?.exiting != true) nav.pop()
        }
        return
    }
    PredictiveBackHandler(enabled = hasPage) { events ->
        val entry = nav.stack.lastOrNull()
        if (entry == null || nav.stack.size <= 1) {
            // 手势进行到一半页面已回到根：吃完事件，避免系统再叠一次返回。
            events.collect { }
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
    // The viewer draws its own growth out of the thumbnail, so the host must leave it in place
    // instead of sliding it in from the right.
    val slide = top != null && !top.expand && !top.growsFromImage
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
                // SquircleShape 是路径裁剪，每帧都要光栅化一张裁切蒙版。只有页面真的在移动时
                // 才需要它：静止时上面那一页整屏不透明，裁与不裁看不出区别。
                if (progress() < 1f || topProgress() < 1f) {
                    shape = cornerShape
                    clip = true
                } else {
                    clip = false
                }
            }
        }

    // The viewer reads its own entrance curve through this; everything else is moved by the host.
    val transition = remember(entry) {
        RouteTransition(
            progress = progress,
            exiting = { entry.exiting || entry.previewing },
        )
    }

    CompositionLocalProvider(LocalRouteTransition provides transition) {
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
                // 和 RouteLayer 一样：路径裁剪只在页面真的在缩放时才需要。铺满屏幕之后
                // 这一页整屏不透明，四角落在物理屏幕的圆角之外，裁与不裁看不出区别。
                if (p < 1f) {
                    shape = ExpandShape(height / scale, radius)
                    clip = true
                } else {
                    clip = false
                }
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
        is Route.Login -> LoginScreen(nav, route.addAccount, route.fresh, route.relogin)
        is Route.SmsLogin -> SmsLoginScreen(nav, route.addAccount, route.relogin)
        is Route.Detail -> DetailScreen(nav, route.id, route.mid, route.readOnly || App.repo.guest)
        is Route.EditHistory -> EditHistoryScreen(nav, route.mid)
        is Route.Profile -> ProfileScreen(nav, route.uid)
        is Route.Relations -> RelationsScreen(nav, route.uid, route.kind)
        is Route.Publish -> PublishScreen(nav, route.draft)
        is Route.Chat -> ChatScreen(nav, route.uid, route.name)
        is Route.Web -> WebScreen(nav, route.url, route.title)
        is Route.Container -> ContainerScreen(nav, route.cid, route.title)
        is Route.SuperTopic -> SuperTopicScreen(nav, route.containerId, route.fallbackUrl)
        Route.Drafts -> DraftsScreen(nav)
        Route.History -> HistoryScreen(nav)
        Route.Accounts -> AccountsScreen(nav)
        Route.Settings -> SettingsScreen(nav)
        Route.About -> AboutScreen(nav)
        Route.Changelog -> ChangelogScreen(nav)
        Route.Nearby -> NearbyScreen(nav)
        is Route.Topic -> TopicScreen(nav, route.name)
        is Route.Images -> ImageViewerScreen(nav, route.pics, route.index)
        is Route.Video -> VideoScreen(nav, route.url, route.title)
        is Route.Vote -> VoteScreen(nav, route.voteId)
        Route.TabLab -> TabBarLabScreen()
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
