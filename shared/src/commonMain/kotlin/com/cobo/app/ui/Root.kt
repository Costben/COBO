package com.cobo.app.ui

import com.cobo.app.platform.BackDispatcher
import com.cobo.app.platform.LocalBackDispatcher
import com.cobo.app.platform.LocalBackRouteIsTop
import com.cobo.app.platform.platformScreenSizePx

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import com.cobo.app.App
import com.cobo.app.data.DetectedWeiboLink
import com.cobo.app.data.Pic
import com.cobo.app.data.ShareAction
import com.cobo.app.data.Updater
import com.cobo.app.data.decideShareAction
import com.cobo.app.data.normalizeWeiboMediaUrl
import com.cobo.app.data.superTopicId
import com.cobo.app.screen.AboutScreen
import com.cobo.app.screen.AccountsScreen
import com.cobo.app.screen.ChangelogScreen
import com.cobo.app.screen.ChatScreen
import com.cobo.app.screen.ContainerScreen
import com.cobo.app.screen.ContentFilterSettingsScreen
import com.cobo.app.screen.DetailScreen
import com.cobo.app.screen.DraftsScreen
import com.cobo.app.screen.EditHistoryScreen
import com.cobo.app.screen.FavoritesScreen
import com.cobo.app.screen.HistoryScreen
import com.cobo.app.screen.ImageViewerScreen
import com.cobo.app.screen.LoginScreen
import com.cobo.app.screen.MyLikesScreen
import com.cobo.app.screen.NearbyScreen
import com.cobo.app.screen.NotificationsScreen
import com.cobo.app.screen.MediaTopBarSettingsScreen
import com.cobo.app.screen.PredictiveBackSettingsScreen
import com.cobo.app.screen.ProfileScreen
import com.cobo.app.screen.PublishScreen
import com.cobo.app.screen.ReadLaterScreen
import com.cobo.app.screen.RelationsScreen
import com.cobo.app.screen.SettingsGroupScreen
import com.cobo.app.screen.SettingsSubPageScreen
import com.cobo.app.screen.SettingsScreen
import com.cobo.app.screen.SmsLoginScreen
import com.cobo.app.screen.TopicFeedScreen
import com.cobo.app.screen.TopicScreen
import com.cobo.app.screen.SuperTopicScreen
import com.cobo.app.screen.VideoScreen
import com.cobo.app.screen.VoteScreen
import com.cobo.app.screen.WebScreen
import com.cobo.app.ui.components.ConfirmDialog
import com.cobo.app.ui.components.ImageSaver
import com.cobo.app.ui.components.LocalImageSaver
import com.cobo.app.ui.components.SaveImageItem
import com.cobo.app.ui.components.SaveImagesMenu
import com.cobo.app.ui.components.UpdateDialog
import com.cobo.app.ui.components.inputCapsuleGlass
import com.cobo.app.ui.components.rememberImageSaveState
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.PreciseClickState
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.SnackbarData
import io.github.suqi8.coui.kmp.basic.SnackbarDuration
import io.github.suqi8.coui.kmp.basic.SnackbarHost
import io.github.suqi8.coui.kmp.basic.SnackbarHostState
import io.github.suqi8.coui.kmp.basic.SnackbarResult
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.layerBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Close
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.squircle.addSquircleRect
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.COUIPopupUtils.Companion.COUIPopupHost
import io.github.suqi8.coui.kmp.utils.PredictiveBackDragTracker
import io.github.suqi8.coui.kmp.utils.PredictiveBackMotionDefaults
import io.github.suqi8.coui.kmp.utils.applyPredictiveBackBlur
import io.github.suqi8.coui.kmp.utils.calculatePredictiveBackBlurRadiusPx
import io.github.suqi8.coui.kmp.utils.isPredictiveBackBlurSupported
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
internal const val LandedProgress = 0.2f

/** Width at which the next page is read beside the list instead of over it. */
private const val WideLayoutMinWidthDp = 600

/**
 * The list's share of a wide window. Weights are relative: 0.72 against the pane's 1 leaves the
 * list about 42% — roughly 250dp on a 600dp window, 350dp on a phone in landscape.
 */
private const val ListPaneWeight = 0.72f
private const val DetailPaneWeight = 1f

@Composable
fun CoboRoot(
    floatingVideoController: FloatingVideoController = remember { FloatingVideoController() },
    onUpdateAutoPip: (Boolean, Float) -> Unit = { _, _ -> },
) {
    val nav = remember { NavState() }
    val backDispatcher = remember { BackDispatcher() }
    val scope = rememberCoroutineScope()
    SideEffect { nav.attach(scope) }

    // A wide window reads the next page beside the list instead of over it. The host owns the
    // window measurements, so it tells the navigator once per configuration change.
    val wide = (platformScreenSizePx().first / LocalDensity.current.density) >= WideLayoutMinWidthDp
    SideEffect { nav.wide = wide }

    // The pane belongs to the tab it was opened from: switching tabs closes it, so the left list
    // and the right page never show two different sections.
    var lastMainTab by remember { mutableIntStateOf(nav.mainTab) }
    LaunchedEffect(nav.mainTab) {
        if (nav.mainTab != lastMainTab) {
            lastMainTab = nav.mainTab
            nav.dismissPane()
        }
    }

    // 启动时顺手续一次网页登录态。
    LaunchedEffect(Unit) { App.repo.refreshSessionIfStale() }

    // 启动时静默查一次更新：开关打开后每次启动应用自动发请求。
    LaunchedEffect(Unit) {
        if (!App.settings.autoCheckUpdate) return@LaunchedEffect
        App.checkUpdate(manual = false)
    }

    // The search field keeps focus after its result opens a page, and the IME's candidate bar
    // then floats over whatever is on top. Drop focus on every navigation.
    val focusManager = LocalFocusManager.current
    LaunchedEffect(nav.navigationVersion) { focusManager.clearFocus(force = true) }

    CoboPredictiveBackHandler(nav)

    val context = App.context
    val linkHandler = remember(nav, scope, context) {
        { raw: String ->
            val current = nav.stack.lastOrNull()?.route
            val readOnly = (current is Route.Detail && current.readOnly) || App.repo.guest
            fun openWebPage(target: String) {
                if (!App.settings.inAppBrowser && isExternalWebLink(target)) {
                    openExternalUrl(context, target)
                } else {
                    val title = if (target.contains("service.account.weibo.com/reportspam", ignoreCase = true)) {
                        "举报"
                    } else {
                        "网页"
                    }
                    nav.push(Route.Web(target, title))
                }
            }
            fun openImageViewer(imageUrl: String) {
                val normalized = normalizeWeiboMediaUrl(imageUrl)
                nav.push(Route.Images(listOf(Pic(thumb = normalized, large = normalized)), 0))
            }
            fun dispatchResolved(url: String, allowShortLink: Boolean) {
                val voteId = weiboVoteId(url)
                val statusId = weiboStatusId(url)
                val uid = weiboUserId(url)
                val mention = weiboMentionName(url)
                val topicId = superTopicId(url)
                val topicQuery = weiboTopicQuery(url)
                when {
                    topicId != null -> nav.push(
                        Route.SuperTopic(
                            topicId,
                            resolveWeiboLink(url) ?: "https://m.weibo.cn/p/index?containerid=$topicId",
                        ),
                    )
                    topicQuery != null -> nav.push(Route.TopicFeed(topicQuery))
                    voteId != null -> nav.push(
                        if (readOnly) {
                            Route.Web("https://vote.weibo.com/h5/index/index?vote_id=$voteId", "投票")
                        } else Route.Vote(voteId),
                    )
                    isImageLink(url) -> openImageViewer(url)
                    isWeiboPhotoPageLink(url) -> scope.launch {
                        val resolvedTarget = resolveWeiboLink(url) ?: url
                        val pics = App.repo.resolveWeiboPhotoPics(resolvedTarget)
                        if (pics.isNotEmpty()) {
                            nav.push(Route.Images(pics, 0))
                        } else {
                            openWebPage(resolvedTarget)
                        }
                    }
                    isWeiboLiveLink(url) -> nav.push(Route.Video(resolveWeiboLink(url) ?: url, "直播", isLive = true))
                    isVideoLink(url) -> nav.push(Route.Video(resolveWeiboLink(url) ?: url, "视频"))
                    isWeiboWboxLink(url) ->
                        App.toast("微博小程序（wbox）依赖官方客户端内核，第三方客户端暂不支持打开")
                    url.contains("containerid=231601_", ignoreCase = true) ->
                        App.toast("该微博卡片详情暂不支持在第三方客户端打开，可直接查看上方卡片图")
                    statusId != null -> if (current is Route.Detail &&
                        (current.id == statusId || (current.mid.isNotBlank() && current.mid == statusId))
                    ) {
                        App.requestDetailRefresh(current.id)
                    } else {
                        nav.push(Route.Detail(statusId, "", readOnly))
                    }
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
                    allowShortLink && isWeiboShortLink(url) -> scope.launch {
                        val rawResolved = App.repo.resolveShortUrl(url)
                        val resolved = unwrapWeiboLink(rawResolved)
                        dispatchResolved(resolved, allowShortLink = false)
                    }
                    else -> resolveWeiboLink(url)?.let(::openWebPage)
                }
            }
            // 「评论配图」是 weibo.cn/sinaurl?u=<图片直链>，流体云深链会包一层 sinaweibo://browser?url=<真实地址>，
            // 或 m.weibo.cn/feature/openapp?scheme=...，先统一拆出真实地址再决定去哪。
            dispatchResolved(unwrapWeiboLink(raw), allowShortLink = true)
            Unit
        }
    }

    LaunchedEffect(App.deepLinkVersion) {
        val deepLink = App.consumeDeepLink() ?: return@LaunchedEffect
        val unwrapped = unwrapWeiboBrowserUrl(deepLink) ?: deepLink
        if (!isWeiboHomeLink(unwrapped)) {
            linkHandler(unwrapped)
        }
    }

    var pendingSharePrompt by remember { mutableStateOf<ShareAction.AskWeiboLinkOrCompose?>(null) }
    LaunchedEffect(App.shareVersion) {
        val share = App.consumeShare() ?: return@LaunchedEffect
        when (val action = decideShareAction(share, App.repo.guest)) {
            ShareAction.Empty -> Unit
            is ShareAction.OpenWeiboLink -> linkHandler(action.link.rawUrl)
            is ShareAction.AskWeiboLinkOrCompose -> pendingSharePrompt = action
            is ShareAction.Compose -> {
                if (App.repo.guest) {
                    App.toast("纯本地模式暂不支持发微博，请先登录")
                } else {
                    nav.push(Route.Publish(draft = action.text, initialMediaUris = action.imageUris))
                }
            }
        }
    }

    // 长按缩略图弹「保存原图 / 保存实况图」的菜单挂在根节点，任何页面都不用自己搭浮层。
    var saveTargets by remember { mutableStateOf<List<SaveImageItem>?>(null) }
    var saveIndex by remember { mutableIntStateOf(0) }
    var savePrecise by remember { mutableStateOf<PreciseClickState?>(null) }
    val saveState = rememberImageSaveState()
    val imageSaver = remember {
        ImageSaver { items, index, precise ->
            saveTargets = items
            saveIndex = index
            savePrecise = precise
        }
    }
    // 玻璃菜单要有一层能采样的底：使用单趟默认录制，避免全应用每帧双趟重绘。
    val rootBackdrop = rememberLayerBackdrop()
    val blur = blurEffectsEnabled()
    // 缩略图把自己的屏幕位置登记在这里，看图页才能从被点的那张长出来、再缩回去。
    val imageOrigins = remember { ImageOrigins() }
    val expandOriginVisibility = remember(nav) {
        ExpandOriginVisibility(nav.stack, paneEntry = { nav.paneEntry })
    }

    val autoPipSession = floatingVideoController.activeSession
    val autoPipEnabled = shouldAutoEnterSystemPip(
        settingEnabled = App.settings.videoAutoFloatOnBack,
        hasActiveVideo = autoPipSession != null,
        isPlaying = autoPipSession?.isPlaying == true,
    )
    val autoPipRatio = autoPipSession?.aspectRatio ?: (16f / 9f)
    LaunchedEffect(autoPipEnabled, autoPipRatio) {
        onUpdateAutoPip(autoPipEnabled, autoPipRatio)
    }

    val feedVideoAutoPlay = remember { FeedVideoAutoPlayController() }
    rememberFeedVideoAutoPlayEnvironment(feedVideoAutoPlay, nav, floatingVideoController)

    // 顶栏和底栏在各自的节点里钉密度（见 [PinnedBarDensity]），页面内容跟随系统「显示大小」。
    // 以前这里把整棵树都钉在 360dp 短边，结果正文字号也跟着缩了一档。
    CompositionLocalProvider(
        LocalBackDispatcher provides backDispatcher,
        LocalLinkHandler provides linkHandler,
        LocalStatusEditor provides { target ->
            com.cobo.app.data.StatusMemory.remember(target)
            nav.push(Route.EditStatus(target.id, target.mid))
        },
        LocalImageSaver provides imageSaver,
        LocalImageOrigins provides imageOrigins,
        LocalExpandOriginVisibility provides expandOriginVisibility,
        LocalFloatingVideoController provides floatingVideoController,
        LocalFeedVideoAutoPlay provides feedVideoAutoPlay,
    ) {
        // 根 Scaffold 提供全局 LocalRootDialogStates / LocalRootPopupStates，
        // 让全应用所有对话框（OverlayDialog）、底部面板（OverlayBottomSheet）和菜单（含嵌套 Scaffold 页面）
        // 都落在根节点统一渲染，并读取 rootBackdrop 呈现 ColorOS 17 磨砂玻璃与边缘光。
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0),
            popupHost = {
                CompositionLocalProvider(LocalBackdrop provides if (blur) rootBackdrop else null) {
                    COUIPopupHost()
                    // 使用磨砂玻璃胶囊替换 COUI 默认大半径 dropShadow，避免在 SnackbarHost LazyColumn 底部被裁切出矩形阴影。
                    AppSnackbarHost(
                        nav = nav,
                        onOpenClipboardLink = { link -> linkHandler(link.rawUrl) },
                    )
                }
            },
        ) {
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .platformRootSemantics()
            ) {
            val widthPx = constraints.maxWidth.toFloat()
            val windowWidth = maxWidth
            val screenCorner = rememberScreenCornerRadius()
            val cornerShape = remember(screenCorner) { SquircleShape(screenCorner) }
            val pageSurface = pageContainerColor()
            val gpuWarmup = rememberLayerPipelineWarmup(nav)
            val cornerPx = with(LocalDensity.current) { screenCorner.toPx() }
            val warmupExitLayer = rememberGraphicsLayer()

            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val warming = gpuWarmup.active && size.height > 0f
                        clip = warming
                        shape = if (warming) ExpandShape(size.height, cornerPx) else RectangleShape
                    },
            ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        compositingStrategy = if (gpuWarmup.active) {
                            CompositingStrategy.Offscreen
                        } else {
                            CompositingStrategy.Auto
                        }
                    }
                    .layerBackdropIf(blur, rootBackdrop)
            ) {
                Box(Modifier.fillMaxSize().background(pageSurface))
                val paneEntry = nav.paneEntry
                // A pane entry is still drawn by the same RouteLayer, only inside a narrower box,
                // so the list keeps its composition and scroll position when the pane opens.
                val listPaneWidth = windowWidth * (ListPaneWeight / (ListPaneWeight + DetailPaneWeight))
                val detailPaneWidth = windowWidth - listPaneWidth
                nav.stack.forEachIndexed { index, entry ->
                    key(entry.id) {
                        val inPane = paneEntry != null
                        val isPane = entry === paneEntry
                        val lastIndex = when {
                            !inPane -> nav.stack.lastIndex
                            isPane -> 1
                            else -> 0
                        }
                        Box(
                            Modifier
                                .then(
                                    when {
                                        !inPane -> Modifier.fillMaxSize()
                                        index == 0 -> Modifier
                                            .align(Alignment.CenterStart)
                                            .width(listPaneWidth)
                                            .fillMaxHeight()
                                        else -> Modifier
                                            .align(Alignment.CenterEnd)
                                            .width(detailPaneWidth)
                                            .fillMaxHeight()
                                    }
                                )
                                .zIndex(if (index == nav.stack.lastIndex) 2f else 1f)
                        ) {
                            RouteLayer(
                                entry = entry,
                                index = if (inPane) (if (isPane) 1 else 0) else index,
                                lastIndex = lastIndex,
                                inPane = inPane,
                                widthPx = widthPx,
                                screenCorner = screenCorner,
                                nav = nav,
                            )
                        }
                    }
                }
                if (paneEntry != null) {
                    // The two panes share one surface tone, so a hairline is what tells the reader
                    // where the list ends.
                    Box(
                        Modifier
                            .align(Alignment.CenterStart)
                            .offset(x = listPaneWidth)
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(COUITheme.colorScheme.dividerLine)
                            .zIndex(2.5f)
                    )
                }
                val cornerMaskCache = remember(cornerShape) {
                    object {
                        var size = Size.Zero
                        var layoutDirection: LayoutDirection? = null
                        var path: Path? = null
                    }
                }
                val showCornerMask by remember(nav) {
                    derivedStateOf { nav.stack.any { !it.expand && it.progress.value < 1f } }
                }
                // 横滑转场时补齐四角底色，并缓存超椭圆轮廓避免每帧重复构路。
                Box(
                    Modifier
                        .fillMaxSize()
                        .zIndex(3f)
                        .drawBehind {
                            if (!showCornerMask) return@drawBehind
                            val cached = cornerMaskCache.path?.takeIf {
                                cornerMaskCache.size == size && cornerMaskCache.layoutDirection == layoutDirection
                            }
                            val cut = cached ?: when (val outline = cornerShape.createOutline(size, layoutDirection, this)) {
                                is Outline.Generic -> outline.path
                                is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
                                else -> return@drawBehind
                            }.also {
                                cornerMaskCache.size = size
                                cornerMaskCache.layoutDirection = layoutDirection
                                cornerMaskCache.path = it
                            }
                            clipPath(cut, ClipOp.Difference) { drawRect(pageSurface) }
                        },
                )
            }
            }

            // 预热 ExitSnapshotHost 离屏录制与 Squircle 差集裁切路径（16x16 极低透明度，不可见）。
            if (gpuWarmup.active) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .drawBehind {
                            warmupExitLayer.alpha = 0.004f
                            warmupExitLayer.record(size = IntSize(16, 16)) {
                                drawRect(pageSurface, size = Size(16f, 16f))
                            }
                            drawLayer(warmupExitLayer)
                            val patchSize = Size(16f, 16f)
                            val outline = cornerShape.createOutline(patchSize, layoutDirection, this)
                            val cut = when (outline) {
                                is Outline.Generic -> outline.path
                                is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
                                else -> null
                            }
                            if (cut != null) {
                                clipPath(cut, ClipOp.Difference) {
                                    drawRect(pageSurface.copy(alpha = 0.004f), size = patchSize)
                                }
                            }
                        },
                )
            }

            if (platformUsesCustomEdgeSwipeBack) {
                // 双侧（左边缘向右、右边缘向左）交互式跟手侧滑返回：仅在 iOS 上启用；Android 走系统预测性返回。
                val canSwipeBack by remember(nav, backDispatcher) {
                    derivedStateOf {
                        nav.stack.count { !it.exiting } > 1 || backDispatcher.hasEnabledHandler()
                    }
                }
                if (canSwipeBack) {
                    EdgeSwipeBackOverlay(
                        widthPx = widthPx,
                        nav = nav,
                        backDispatcher = backDispatcher,
                        scope = scope,
                    )
                }
            }

            // 应用内视频悬浮小窗（系统画中画激活时置顶铺满纯画面）。
            FloatingVideoOverlay(
                controller = floatingVideoController,
                nav = nav,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (floatingVideoController.systemPipActive && floatingVideoController.activeSession != null) 30f else 15f),
            )

            // 保存图片菜单与点赞确认挂在根节点，弹层内容由外层根 Scaffold 的 popupHost 统一承载并采样 rootBackdrop。
            Box(
                Modifier
                    .fillMaxSize()
                    .zIndex(20f)
            ) {
                CompositionLocalProvider(LocalBackdrop provides if (blur) rootBackdrop else null) {
                    SaveImagesMenu(
                        items = saveTargets,
                        index = saveIndex,
                        precise = savePrecise,
                        onDismiss = { saveTargets = null },
                        state = saveState,
                    )
                    // 点赞前确认也挂在根节点：任何页面的爱心都走 App.confirmLike，由这里统一问一句。
                    LikeConfirmPrompt()
                    // 混合分享（含文案与微博链接）在此询问是打开链接还是作为发微博草稿。
                    pendingSharePrompt?.let { prompt ->
                        key(prompt) {
                            ShareChoiceDialog(
                                prompt = prompt,
                                onDismiss = { pendingSharePrompt = null },
                                onOpenLink = {
                                    pendingSharePrompt = null
                                    linkHandler(prompt.link.rawUrl)
                                },
                                onCompose = {
                                    pendingSharePrompt = null
                                    nav.push(Route.Publish(draft = prompt.text))
                                },
                            )
                        }
                    }
                }
            }

            // 更新提示挂在根节点：自动检查与设置页的手动检查都往 App.pendingUpdate 里写。
            UpdatePrompt()
            }
        }
    }
}

/** 全局统一提示与剪贴板识别：复用磨砂玻璃胶囊 [GlassToastSnackbar] / [GlassActionSnackbar]。 */
@Composable
private fun AppSnackbarHost(
    nav: NavState,
    onOpenClipboardLink: (DetectedWeiboLink) -> Unit,
) {
    val hostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var activeCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(hostState) {
        App.toastEvents.collect { message ->
            hostState.newestSnackbarData()?.dismiss()
            activeCount++
            launch {
                try {
                    hostState.showSnackbar(
                        message = message,
                        duration = SnackbarDuration.Custom(2200L),
                    )
                } finally {
                    activeCount = (activeCount - 1).coerceAtLeast(0)
                }
            }
        }
    }

    val detected = App.pendingClipboardLink
    val topRoute = nav.stack.lastOrNull { !it.exiting }?.route
    val suppressed = topRoute is Route.Login ||
        topRoute is Route.SmsLogin ||
        topRoute is Route.Images ||
        topRoute is Route.Video ||
        topRoute is Route.Publish ||
        topRoute is Route.EditStatus

    LaunchedEffect(detected) {
        val link = detected ?: return@LaunchedEffect
        App.dismissClipboardLink()
        if (suppressed) return@LaunchedEffect
        activeCount++
        scope.launch {
            try {
                hostState.newestSnackbarData()?.dismiss()
                val result = hostState.showSnackbar(
                    message = "剪贴板发现${link.kind.label}",
                    actionLabel = "打开",
                    withDismissAction = true,
                    duration = SnackbarDuration.Custom(6_000L),
                )
                if (result == SnackbarResult.ActionPerformed) {
                    onOpenClipboardLink(link)
                }
            } finally {
                activeCount = (activeCount - 1).coerceAtLeast(0)
            }
        }
    }

    // 无提示展示时不订阅 WindowInsets.ime，避免搜索栏弹出/收起键盘时每帧触发根部全屏重组与重排。
    if (activeCount == 0) return
    val density = LocalDensity.current
    val imeOpen = WindowInsets.ime.getBottom(density) > 0
    val hasFloatingBottomBar = !imeOpen && (topRoute == Route.Main || topRoute is Route.Detail)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding()
            .padding(bottom = if (hasFloatingBottomBar) 44.dp else 0.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        SnackbarHost(state = hostState) { data ->
            if (data.visuals.actionLabel != null) {
                GlassActionSnackbar(data = data)
            } else {
                GlassToastSnackbar(data = data)
            }
        }
    }
}

@Composable
private fun GlassActionSnackbar(data: SnackbarData) {
    val scheme = COUITheme.colorScheme
    val scope = rememberCoroutineScope()
    val backdrop = LocalBackdrop.current
    val dark = isDarkTheme()
    val actionLabel = data.visuals.actionLabel

    Box(modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 32.dp)) {
        Row(
            modifier = Modifier
                .heightIn(min = 46.dp)
                .inputCapsuleGlass(
                    backdrop = backdrop,
                    isDark = dark,
                    cornerRadius = 23.dp,
                )
                .clickable { scope.launch { data.performAction() } }
                .padding(start = 18.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = data.visuals.message,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (!actionLabel.isNullOrBlank()) {
                Spacer(Modifier.width(12.dp))
                Text(
                    text = actionLabel,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { scope.launch { data.performAction() } }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            if (data.visuals.withDismissAction) {
                Spacer(Modifier.width(2.dp))
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .clickable { scope.launch { data.dismiss() } },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = COUIIcons.Close,
                        contentDescription = "关闭",
                        tint = scheme.onSurfaceSecondary,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ShareChoiceDialog(
    prompt: ShareAction.AskWeiboLinkOrCompose,
    onDismiss: () -> Unit,
    onOpenLink: () -> Unit,
    onCompose: () -> Unit,
) {
    var show by remember { mutableStateOf(false) }
    var choice by remember { mutableStateOf<Int?>(null) }
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { show = true }

    OverlayDialog(
        show = show,
        title = "识别到${prompt.link.kind.label}",
        summary = "分享内容中包含微博链接（${prompt.link.displayText}），请选择处理方式。",
        outsideMargin = DpSize(16.dp, 8.dp),
        onDismissRequest = { show = false },
        onDismissFinished = {
            if (!settled) {
                settled = true
                when (choice) {
                    1 -> onOpenLink()
                    2 -> onCompose()
                    else -> onDismiss()
                }
            }
        },
    ) {
        DialogButtonBar(
            negative = DialogButtonBarAction(
                text = "作为发微博草稿",
                onClick = {
                    choice = 2
                    show = false
                },
            ),
            positive = DialogButtonBarAction(
                text = "打开链接",
                recommended = true,
                onClick = {
                    choice = 1
                    show = false
                },
            ),
        )
    }
}

@Composable
private fun GlassToastSnackbar(data: SnackbarData) {
    // 四周留足阴影余量，确保 SnackbarHost 的 LazyColumn item 不裁切磨砂胶囊柔阴影。
    Box(modifier = Modifier.padding(start = 24.dp, top = 16.dp, end = 24.dp, bottom = 32.dp)) {
        Box(
            modifier = Modifier
                .heightIn(min = 42.dp)
                .inputCapsuleGlass(
                    backdrop = LocalBackdrop.current,
                    isDark = isDarkTheme(),
                    cornerRadius = 21.dp,
                )
                .padding(horizontal = 18.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = data.visuals.message,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = COUITheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
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
internal expect val platformUsesCustomEdgeSwipeBack: Boolean

internal expect fun Modifier.platformRootSemantics(): Modifier

@Composable
expect fun CoboPredictiveBackHandler(nav: NavState)

/**
 * The right-hand page of a wide window. It is already on screen beside the list, so it draws
 * settled: no slide from the edge, no card frame, and no return target to hand back to.
 */
@Composable
private fun RouteLayer(
    entry: NavEntry,
    index: Int,
    lastIndex: Int,
    inPane: Boolean,
    widthPx: Float,
    screenCorner: Dp,
    nav: NavState,
) {
    // Read in layer and draw blocks only, so a running gesture invalidates the layer
    // instead of recomposing the whole route.
    val progress: () -> Float = { entry.progress.value }
    val activeTop = nav.stack.lastOrNull { !it.exiting }
    val isTop = index == lastIndex
    val isActiveTop = entry === activeTop
    val belowTop = index == lastIndex - 1
    val top = nav.stack.lastOrNull()
    val topProgress: () -> Float = { top?.progress?.value ?: 1f }
    // The viewer draws its own growth out of the thumbnail, so the host must leave it in place
    // instead of sliding it in from the right.
    val slide = top != null && !top.expand && !top.growsFromImage
    val cornerShape = remember(screenCorner) { SquircleShape(screenCorner) }
    val backBlurSupported = isPredictiveBackBlurSupported() &&
        blurEffectsEnabled() &&
        App.settings.predictiveBackBlur
    val density = LocalDensity.current
    val maxBlurRadiusPx = with(density) { PredictiveBackMotionDefaults.BackBlurMaxRadius.toPx() }
    val blurOffsetSpanPx = with(density) { PredictiveBackMotionDefaults.BackBlurOffsetSpan.toPx() }

    // 已被上层静止不透明页面完全遮住的底层路由跳过整棵树绘制，节省 GPU 填充与模糊开销。
    val covered by remember(inPane, index, nav.stack.size) {
        derivedStateOf {
            !inPane && index >= 0 && nav.stack.drop(index + 1).any {
                !it.exiting && !it.previewing && !it.growsFromImage && it.progress.value >= 1f
            }
        }
    }

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
            // iOS Skiko/Metal 单线程渲染中，退场首帧对整屏 MainShell 挂载动态半径高斯模糊会造成明显的起手掉帧；
            // 仅保留前景跟手缩放与平移动画，确保 120fps 起手零卡顿。
            applyPredictiveBackBlur(0f)
        }
        .drawWithContent {
            if (!covered) {
                drawContent()
            }
        }

    // The viewer reads its own entrance curve through this; everything else is moved by the host.
    val transition = remember(entry) {
        RouteTransition(
            progress = progress,
            exiting = { entry.exiting || entry.previewing },
            offset = { entry.offset.value },
            onHasReturnTarget = { entry.hasReturnTarget = it },
        )
    }

    // 进场展开 / 退场 / 侧滑预览期间，前景页与底层页均冻结为单张静态 GPU 纹理，
    // 消除转场期间底层 Feed + 顶栏模糊 + 底部熔球玻璃与前景详情页同时逐帧重绘造成的掉帧。
    // 看图页（growsFromImage）由页面自身驱动黑幕淡出与缩回动画，绝不可冻结成静态黑屏快照。
    val feedAutoPlay = LocalFeedVideoAutoPlay.current
    val freezeSelf = !entry.growsFromImage && ((entry.expand && entry.animating) || entry.exiting || entry.previewing)
    val freezeBelow = belowTop && !inPane && top != null && top.belowFrozen
    val freezeSnapshot = freezeSelf || freezeBelow
    val hasActiveNativeSurface = entry.hasNativeInteropView ||
        entry.growsFromImage ||
        feedAutoPlay?.activeKey != null
    // 仅在当前路由为已稳定停靠的栈顶页（无进退场动画、无侧滑预览）时才允许信息流视频自动播放。
    val feedVideoRouteActive = isTop && !entry.animating && !entry.exiting && !entry.previewing

    CompositionLocalProvider(
        LocalRouteTransition provides transition,
        LocalCurrentNavEntry provides entry,
        LocalBackRouteIsTop provides isActiveTop,
        LocalFeedVideoRouteActive provides feedVideoRouteActive,
    ) {
        Box(layer) {
            if (entry.expand) {
                ExpandLayer(entry, screenCorner, progress, { entry.fade.value }, { entry.offset.value }) {
                    ExitSnapshotHost(
                        exiting = freezeSnapshot,
                        hasNativeView = hasActiveNativeSurface,
                    ) {
                        RouteContent(entry.route, nav)
                    }
                }
            } else {
                ExitSnapshotHost(
                    exiting = freezeSnapshot,
                    hasNativeView = hasActiveNativeSurface,
                ) {
                    RouteContent(entry.route, nav)
                }
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
    offset: () -> Offset = { Offset.Zero },
    content: @Composable () -> Unit,
) {
    val origin = entry.origin ?: return
    val bounds = origin.bounds ?: return
    val density = LocalDensity.current
    val statusBarTop = with(density) {
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding().toPx()
    }
    val surface = pageContainerColor()
    val exiting = entry.exiting || entry.previewing
    fun frameProgress(p: Float): Float =
        if (exiting) ((p - LandedProgress) / (1f - LandedProgress)).coerceIn(0f, 1f) else p
    fun contentAlpha(p: Float): Float = if (exiting) {
        // 退场后半段详情页（已冻结为单张 ExitSnapshotHost 纹理）平滑淡出 1→0，
        // 露出底层完整绘制的原卡片快照，零 saveLayer 且由 GPU 硬件 Alpha 混合完成交叉淡入淡出。
        1f - ((0.55f - p) / 0.42f).coerceIn(0f, 1f)
    } else {
        ((fade() - 0.02f) / 0.24f).coerceIn(0f, 1f)
    }

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                if (!exiting && progress() > 0.0005f) {
                    drawRect(Color.Black, alpha = 0.3f * progress())
                }
            }
            .graphicsLayer {
                val p = frameProgress(progress())
                val dragOffset = offset()
                val left = lerp(bounds.left, 0f, p) + dragOffset.x
                val top = lerp(bounds.top, 0f, p) + dragOffset.y
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
                if (p < 1f || dragOffset != Offset.Zero) {
                    shape = ExpandShape(height / scale, radius)
                    clip = true
                } else {
                    clip = false
                }
            }
            .drawWithContent {
                val p = frameProgress(progress())
                if (!exiting && p <= 0.0005f) return@drawWithContent
                drawContent()
            },
    ) {
        // 底层：原卡片快照以正常 alpha=1 绘在下方（绝不修改 origin.layer.alpha，也无需 saveLayer）；
        // 进场初段和退场末段当前景 contentAlpha < 1 时自然透出，前景完全不透明时跳过绘制。
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val p = frameProgress(progress())
                    val ca = contentAlpha(p)
                    if (p < 1f || ca < 0.999f) {
                        drawRect(surface)
                        if (ca < 0.999f) {
                            drawCardSnapshot(origin.layer, bounds)
                        }
                    }
                },
        )
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = frameProgress(progress())
                    alpha = contentAlpha(p)
                    translationY = -statusBarTop * (1f - p)
                },
        ) {
            Box(Modifier.fillMaxSize().background(surface)) { content() }
        }
    }
}

private fun DrawScope.drawCardSnapshot(
    layer: GraphicsLayer,
    bounds: Rect,
) {
    val layerSize = layer.size
    if (layerSize.width <= 0 || layerSize.height <= 0 || bounds.width <= 0f) return
    val k = size.width / bounds.width
    withTransform({ scale(k, k, pivot = Offset.Zero) }) {
        clipRect(0f, 0f, bounds.width, bounds.height) {
            drawLayer(layer)
        }
    }
}

@Composable
private fun RouteContent(route: Route, nav: NavState) {
    when (route) {
        Route.Main -> MainShell(nav)
        is Route.Login -> LoginScreen(nav, route.addAccount, route.fresh, route.relogin)
        is Route.SmsLogin -> SmsLoginScreen(nav, route.addAccount, route.relogin)
        is Route.Detail -> DetailScreen(
            nav,
            route.id,
            route.mid,
            route.readOnly || App.repo.guest,
            route.initialSection,
        )
        is Route.EditHistory -> EditHistoryScreen(nav, route.mid)
        is Route.Profile -> ProfileScreen(nav, route.uid)
        is Route.Relations -> RelationsScreen(nav, route.uid, route.kind)
        is Route.Publish -> PublishScreen(nav, route.draft, initialMediaUris = route.initialMediaUris)
        is Route.EditStatus -> PublishScreen(nav, draft = "", editStatusId = route.id, editStatusMid = route.mid)
        is Route.Chat -> ChatScreen(nav, route.uid, route.name, route.gid)
        is Route.Notifications -> NotificationsScreen(nav, route.initialTab)
        is Route.Web -> WebScreen(nav, route.url, route.title)
        is Route.Container -> if (route.cid == "230267") {
            FavoritesScreen(nav)
        } else {
            ContainerScreen(nav, route.cid, route.title)
        }
        is Route.SuperTopic -> SuperTopicScreen(nav, route.containerId, route.fallbackUrl)
        Route.Drafts -> DraftsScreen(nav)
        Route.History -> HistoryScreen(nav)
        Route.ReadLater -> ReadLaterScreen(nav)
        Route.Accounts -> AccountsScreen(nav)
        Route.Favorites -> FavoritesScreen(nav)
        Route.MyLikes -> MyLikesScreen(nav)
        Route.Settings -> SettingsScreen(nav)
        is Route.SettingsDetail -> SettingsGroupScreen(nav, route.group)
        is Route.SettingsSubDetail -> SettingsSubPageScreen(nav, route.page)
        Route.PredictiveBackSettings -> PredictiveBackSettingsScreen(nav)
        Route.MediaTopBarSettings -> MediaTopBarSettingsScreen(nav)
        Route.ContentFilterSettings -> ContentFilterSettingsScreen(nav)
        Route.About -> AboutScreen(nav)
        Route.Changelog -> ChangelogScreen(nav)
        Route.Nearby -> NearbyScreen(nav)
        is Route.Topic -> TopicScreen(nav, route.name)
        is Route.TopicFeed -> TopicFeedScreen(nav, route.topic)
        is Route.Images -> ImageViewerScreen(nav, route.pics, route.index, route.editableAvatar)
        is Route.Video -> VideoScreen(nav, route.url, route.title, route.isLive, route.fid, route.statusId)
        is Route.Vote -> VoteScreen(nav, route.voteId)
        Route.TabLab -> TabBarLabScreen()
    }
}

internal data class ExpandShape(
    private val heightPx: Float,
    private val radiusPx: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val height = heightPx.coerceIn(0f, size.height)
        val path = Path()
        path.addSquircleRect(
            width = size.width,
            height = height,
            cornerRadius = radiusPx.coerceAtLeast(0f),
        )
        return Outline.Generic(path)
    }
}

@Composable
internal expect fun rememberScreenCornerRadius(): Dp
