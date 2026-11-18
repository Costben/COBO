package com.cobo.app.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import android.graphics.Bitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.view.WindowCompat
import coil3.BitmapImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.cobo.app.ui.findActivity
import com.cobo.app.ui.isDarkTheme
import com.cobo.app.ui.rememberIsTopRoute
import io.github.suqi8.coui.kmp.theme.LocalContentColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.cobo.app.App
import com.cobo.app.data.FollowTag
import com.cobo.app.data.FollowTags
import com.cobo.app.data.Membership
import com.cobo.app.data.Pic
import com.cobo.app.data.RelationKind
import com.cobo.app.data.Status
import com.cobo.app.data.User
import com.cobo.app.ui.NavState
import com.cobo.app.ui.AppBarSurface
import com.cobo.app.ui.blurEffectsEnabled
import com.cobo.app.ui.layerBackdropIf
import com.cobo.app.ui.PinnedBarDensity
import com.cobo.app.ui.pinnedHeight
import com.cobo.app.ui.pageContainerColor
import com.cobo.app.ui.LocalImageOrigins
import com.cobo.app.ui.LocalTopBarBounds
import com.cobo.app.ui.attachToRow
import com.cobo.app.ui.rememberExpandOrigin
import com.cobo.app.ui.imageOrigin
import com.cobo.app.ui.Route
import com.cobo.app.ui.expandOrigin
import com.cobo.app.ui.components.Avatar
import com.cobo.app.ui.components.BackIconButton
import com.cobo.app.ui.components.EmptyState
import com.cobo.app.ui.components.ErrorState
import com.cobo.app.ui.components.FollowTagBadge
import com.cobo.app.ui.components.LoadingState
import com.cobo.app.ui.components.MemberBadge
import com.cobo.app.ui.components.StatusCard
import com.cobo.app.ui.components.FullPageRefreshHoldMillis
import com.cobo.app.ui.components.FullPageRefreshTexts
import com.cobo.app.ui.components.navBottomPadding
import com.cobo.app.ui.components.fmtCount
import io.github.suqi8.coui.kmp.basic.AppBarBlurOverlayDefaults
import io.github.suqi8.coui.kmp.basic.DropdownImpl
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton
import io.github.suqi8.coui.kmp.basic.ListPopupColumn
import io.github.suqi8.coui.kmp.basic.ListPopupDefaults
import io.github.suqi8.coui.kmp.basic.FullPagePullToRefresh
import io.github.suqi8.coui.kmp.basic.fullPagePullToRefreshColors
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.couiTopAppBarActionItem
import io.github.suqi8.coui.kmp.overlay.OverlayListPopup
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.LocalDismissState
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import io.github.suqi8.coui.kmp.basic.FullPagePullToRefreshState
import io.github.suqi8.coui.kmp.basic.FullPageRefreshState
import io.github.suqi8.coui.kmp.basic.HighlightedIconButtonDefaults
import io.github.suqi8.coui.kmp.basic.LocalEdgeToEdge
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.basic.materialShadowEdge
import io.github.suqi8.coui.kmp.basic.rememberFullPagePullToRefreshState
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.layerBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import kotlin.math.roundToInt

internal data class CoverContrast(
    val useWhiteContent: Boolean = false,
)

/**
 * 判断封面裁切绘制区中 `[bandTopPx, bandBottomPx]` 这一横条叠完顶部 30% 黑色遮罩后是否偏深：
 * 用于个人主页和“我”页未滚动离开封面时，自适应把顶部文字与状态栏图标反色为白色。
 */
internal fun isCoverCropBandDark(
    width: Int,
    height: Int,
    pixelAt: (x: Int, y: Int) -> Int,
    containerWidthPx: Float,
    containerHeightPx: Float,
    bandTopPx: Float,
    bandBottomPx: Float,
    overlayAlpha: Float = 0.3f,
): Boolean {
    if (width <= 0 || height <= 0 || containerWidthPx <= 0f || containerHeightPx <= 0f) return false
    val clampedTop = bandTopPx.coerceIn(0f, containerHeightPx)
    val clampedBottom = bandBottomPx.coerceIn(0f, containerHeightPx)
    if (clampedBottom <= clampedTop) return false

    val scale = maxOf(containerWidthPx / width.toFloat(), containerHeightPx / height.toFloat())
    val drawnW = width * scale
    val drawnH = height * scale
    val offsetX = (containerWidthPx - drawnW) / 2f
    val offsetY = (containerHeightPx - drawnH) / 2f
    val factor = (1f - overlayAlpha).coerceIn(0f, 1f).toDouble()

    val cols = 12
    val rows = 6
    var sum = 0.0
    var count = 0
    for (rIdx in 0 until rows) {
        val sy = clampedTop + (clampedBottom - clampedTop) * ((rIdx + 0.5f) / rows)
        val py = ((sy - offsetY) / scale).toInt().coerceIn(0, height - 1)
        for (cIdx in 0 until cols) {
            val sx = containerWidthPx * ((cIdx + 0.5f) / cols)
            val px = ((sx - offsetX) / scale).toInt().coerceIn(0, width - 1)
            val argb = pixelAt(px, py)
            val red = ((argb ushr 16) and 0xFF) * factor
            val green = ((argb ushr 8) and 0xFF) * factor
            val blue = (argb and 0xFF) * factor
            sum += (0.299 * red + 0.587 * green + 0.114 * blue) / 255.0
            count++
        }
    }
    return count > 0 && (sum / count) < 0.48
}

@Composable
internal fun rememberCoverContrast(
    coverUrl: String?,
    topBarHeight: Dp,
    active: Boolean,
    manageStatusBar: Boolean,
): CoverContrast {
    val context = LocalContext.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val statusBarBottomPx = WindowInsets.statusBars.getTop(density).toFloat()
    val containerWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val topBarBottomPx = with(density) { topBarHeight.toPx() }
    val coverHeightPx = with(density) { (topBarHeight + ProfileAvatarRing * 1.5f).toPx() }

    val sampled by produceState(
        initialValue = CoverContrast(),
        coverUrl,
        containerWidthPx,
        coverHeightPx,
        statusBarBottomPx,
        topBarBottomPx,
    ) {
        if (coverUrl.isNullOrBlank()) {
            value = CoverContrast()
            return@produceState
        }
        value = runCatching {
            val request = ImageRequest.Builder(context)
                .data(coverUrl)
                .size(144)
                .build()
            val raw = ((context.imageLoader.execute(request) as? SuccessResult)?.image as? BitmapImage)
                ?.bitmap ?: return@runCatching CoverContrast()
            val bitmap = if (raw.config == Bitmap.Config.HARDWARE) {
                raw.copy(Bitmap.Config.ARGB_8888, false) ?: return@runCatching CoverContrast()
            } else {
                raw
            }
            val bw = bitmap.width
            val bh = bitmap.height
            // 以“主页”标题所在顶栏条带为准：状态栏与标题颜色统一受同一个判定控制，同步变化。
            val bandTop = if (topBarBottomPx > statusBarBottomPx) statusBarBottomPx else 0f
            val bandDark = isCoverCropBandDark(
                width = bw,
                height = bh,
                pixelAt = { x, y -> bitmap.getPixel(x, y) },
                containerWidthPx = containerWidthPx,
                containerHeightPx = coverHeightPx,
                bandTopPx = bandTop,
                bandBottomPx = topBarBottomPx,
            )
            CoverContrast(useWhiteContent = bandDark)
        }.getOrDefault(CoverContrast())
    }

    val effective = if (active && !coverUrl.isNullOrBlank()) sampled else CoverContrast()
    val darkTheme = isDarkTheme()
    val window = remember(context) { context.findActivity()?.window }
    val controller = remember(window) {
        window?.let { WindowCompat.getInsetsController(it, it.decorView) }
    }
    val lightStatusBars = !(darkTheme || effective.useWhiteContent)
    LaunchedEffect(controller, manageStatusBar, lightStatusBars) {
        if (manageStatusBar) {
            controller?.isAppearanceLightStatusBars = lightStatusBars
        }
    }
    DisposableEffect(controller, manageStatusBar, darkTheme) {
        onDispose {
            if (manageStatusBar) {
                controller?.isAppearanceLightStatusBars = !darkTheme
            }
        }
    }
    return effective
}

@Composable
fun ProfileScreen(nav: NavState, uid: String?) {
    val key = uid.orEmpty()
    val tag = FollowTags[key]
    val backdrop = rememberLayerBackdrop()
    val topBarHeight = pinnedHeight(TopAppBarDefaults.SmallTopAppBarCenterHeight)
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    var activeCoverUrl by remember(uid) { mutableStateOf<String?>(null) }
    val scrollAlpha = remember(listState, density) {
        {
            val scrolled = if (listState.firstVisibleItemIndex > 0) Float.MAX_VALUE
            else listState.firstVisibleItemScrollOffset.toFloat()
            AppBarBlurOverlayDefaults.scrollAlpha(scrolled, density)
        }
    }
    val scrolledPastCover by remember(scrollAlpha) {
        derivedStateOf { scrollAlpha() >= 0.45f }
    }
    val isTopRoute = rememberIsTopRoute(nav)
    val coverContrast = rememberCoverContrast(
        coverUrl = activeCoverUrl,
        topBarHeight = topBarHeight,
        active = !scrolledPastCover && isTopRoute,
        manageStatusBar = isTopRoute,
    )
    val titleColor = if (coverContrast.useWhiteContent) Color.White else COUITheme.colorScheme.onSurface
    Box(Modifier.fillMaxSize().background(pageContainerColor())) {
        // 内容铺到顶栏底下，滚动后顶栏的渐变玻璃才渐入；未滑动时完全透明不模糊封面。
        Box(Modifier.fillMaxSize().layerBackdropIf(blurEffectsEnabled(), backdrop)) {
            CompositionLocalProvider(LocalBackdrop provides if (blurEffectsEnabled()) backdrop else null) {
                ProfileContent(
                    nav = nav,
                    uid = uid,
                    modifier = Modifier.fillMaxSize(),
                    topInset = topBarHeight,
                    listState = listState,
                    onActiveCover = { activeCoverUrl = it },
                )
            }
        }
        CompositionLocalProvider(
            LocalBackdrop provides if (blurEffectsEnabled()) backdrop else null,
            LocalEdgeToEdge provides true,
        ) {
            PinnedBarDensity {
                AppBarSurface(
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
                    containerColor = pageContainerColor(),
                    scrollFraction = scrollAlpha,
                ) {
                    SmallTopAppBar(
                        title = "主页",
                        titleColor = titleColor,
                        color = Color.Transparent,
                        showDivider = false,
                        gradientGlass = false,
                        navigationIcon = { BackIconButton { nav.pop() } },
                        actions = {
                            ProfileMoreAction(tag = tag, onSelect = { picked -> FollowTags.set(key, picked) })
                        },
                    )
                }
            }
        }
    }
}

/**
 * 主页右上角的三点菜单：给这个人挂一个关注标记，三种互斥，再点一次已选中的那项就取消。
 * 用 COUI 的 ListPopup 而不是 Material 的 DropdownMenu，和首页筛选菜单同一套观感。
 */
@Composable
private fun ProfileMoreAction(tag: FollowTag?, onSelect: (FollowTag?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        HighlightedIconButton(
            onClick = { expanded = true },
            modifier = Modifier.couiTopAppBarActionItem().testTag("profile_more"),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.semantics { contentDescription = "更多" },
            ) {
                repeat(3) {
                    Box(
                        Modifier
                            .size(3.5.dp)
                            .clip(CircleShape)
                            .background(LocalContentColor.current)
                    )
                }
            }
        }
        OverlayListPopup(
            show = expanded,
            popupPositionProvider = ListPopupDefaults.DropdownPositionProvider,
            onDismissRequest = { expanded = false },
        ) {
            val dismiss = LocalDismissState.current
            ListPopupColumn {
                FollowTag.entries.forEachIndexed { index, item ->
                    DropdownImpl(
                        item = DropdownItem(text = item.menuLabel),
                        optionSize = FollowTag.entries.size,
                        isSelected = item == tag,
                        index = index,
                        groupKey = "tags",
                        onSelectedIndexChange = {
                            onSelect(if (item == tag) null else item)
                            dismiss?.invoke()
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun ProfileContent(
    nav: NavState,
    uid: String?,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
    extraBottom: Dp = 0.dp,
    listState: LazyListState = rememberLazyListState(),
    onActiveCover: (String?) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var user by remember(uid) { mutableStateOf<User?>(null) }
    val items = remember(uid) { mutableStateListOf<Status>() }
    var sinceId by remember(uid) { mutableStateOf<String?>(null) }
    var loading by remember(uid) { mutableStateOf(false) }
    var refreshing by remember(uid) { mutableStateOf(false) }
    var end by remember(uid) { mutableStateOf(false) }
    var error by remember(uid) { mutableStateOf<String?>(null) }
    var resolvedUid by remember(uid) { mutableStateOf<String?>(uid) }
    val pullState = rememberFullPagePullToRefreshState()

    // showRefresh 只给用户主动刷新用：进入页面时的静默加载不驱动下拉动画。
    suspend fun loadStatuses(reset: Boolean, force: Boolean = false, showRefresh: Boolean = false) {
        val target = resolvedUid ?: return
        if (loading) return
        loading = true
        if (reset && showRefresh) refreshing = true
        try {
            val page = App.repo.userStatuses(target, if (reset) null else sinceId, force = force)
            if (reset) items.clear()
            val known = items.mapTo(HashSet()) { it.id }
            page.items.forEach { if (known.add(it.id)) items.add(it) }
            sinceId = page.sinceId
            if (page.items.isEmpty()) end = true
            error = null
        } catch (t: Throwable) {
            error = t.message ?: "加载失败"
        } finally {
            loading = false
            refreshing = false
        }
    }

    SyncDeletedStatuses(items)
    LaunchedEffect(uid) {
        val target = uid ?: runCatching { App.repo.ownUid() }.getOrNull()
        if (target.isNullOrBlank()) {
            error = "未登录"
            return@LaunchedEffect
        }
        resolvedUid = target
        user = runCatching { App.repo.profile(target) }.getOrNull()
        loadStatuses(true, force = false)
    }

    // 服务端说这个人被设了特别关注、本机又没记过标记，就补一次初值；之后一律以本机为准。
    LaunchedEffect(user?.id, user?.specialFollow) {
        val loaded = user ?: return@LaunchedEffect
        if (loaded.specialFollow && !FollowTags.has(loaded.id)) {
            FollowTags.set(loaded.id, FollowTag.Special)
        }
    }

    LaunchedEffect(listState, items.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { last ->
                if (last >= items.size && items.isNotEmpty() && !end) loadStatuses(false)
            }
    }

    FullPagePullToRefresh(
        isRefreshing = refreshing,
        onRefresh = {
            scope.launch {
                resolvedUid?.let { user = runCatching { App.repo.profile(it, force = true) }.getOrNull() }
                loadStatuses(true, force = true, showRefresh = true)
            }
        },
        modifier = modifier,
        state = pullState,
        contentPadding = PaddingValues(top = topInset),
        // 下拉时不让整页往下走，而是把封面往下拉长；自带的提示会被封面盖住，改由封面自己显示。
        colors = fullPagePullToRefreshColors(
            backgroundColor = pageContainerColor(),
            textColor = Color.Transparent,
            indicatorColor = Color.Transparent,
        ),
        refreshTexts = FullPageRefreshTexts,
        completeHoldDurationMillis = FullPageRefreshHoldMillis,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .scrollEndHaptic()
                .offset { IntOffset(0, -pullState.dragOffset.roundToInt()) },
            // 封面从屏幕最顶上铺起，顶栏的玻璃直接压在封面上；顶栏的高度由封面自己让出来。
            contentPadding = PaddingValues(
                top = 0.dp,
                bottom = 16.dp + navBottomPadding() + extraBottom,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                val tag = FollowTags[user?.id ?: uid]
                ProfileHeader(
                    user = user,
                    tag = tag,
                    topInset = topInset,
                    pullState = pullState,
                    onActiveCover = onActiveCover,
                    onCover = { pics, i -> nav.push(Route.Images(pics, i)) },
                    onRelations = { kind ->
                        val target = user?.id ?: resolvedUid
                        if (!target.isNullOrBlank()) nav.push(Route.Relations(target, kind))
                    },
                    followLabel = when {
                        App.repo.guest -> if (tag == FollowTag.Local) "已本地关注" else "本地关注"
                        user?.following == true -> "已关注"
                        else -> "关注"
                    },
                    onFollow = user?.let { target ->
                        if (App.repo.guest) {
                            // 纯本地模式没有账号可关注，按钮直接切本机的「本地关注」标记。
                            {
                                val on = FollowTags[target.id] == FollowTag.Local
                                FollowTags.set(target.id, if (on) null else FollowTag.Local)
                                App.notifyFollowChanged()
                                App.toast(if (on) "已取消本地关注" else "已加入本地关注")
                            }
                        } else if (target.id == App.repo.session?.uid) {
                            null
                        } else {
                            {
                                scope.launch {
                                    runCatching { App.repo.follow(target.id, !target.following) }
                                        .onSuccess {
                                            user = target.copy(
                                                following = !target.following,
                                                followers = (target.followers +
                                                    if (target.following) -1 else 1).coerceAtLeast(0),
                                            )
                                            App.notifyFollowChanged()
                                            App.toast(if (target.following) "已取消关注" else "已关注")
                                        }
                                        .onFailure { App.toast(it.message ?: "操作失败") }
                                }
                            }
                        }
                    },
                )
            }
            if (items.isEmpty()) {
                item {
                    when {
                        loading || refreshing -> LoadingState()
                        error != null -> ErrorState(error!!) {
                            scope.launch { loadStatuses(true, force = true, showRefresh = true) }
                        }
                        else -> EmptyState("还没有微博")
                    }
                }
            }
            itemsIndexed(items, key = { _, s -> s.id }) { index, status ->
                val origin = rememberExpandOrigin()
                val topBar = LocalTopBarBounds.current
                SideEffect {
                    origin.listState = listState
                    origin.rowKey = status.id
                    origin.topBar = topBar
                }
                StatusCard(
                    status = status,
                    modifier = Modifier.padding(horizontal = 12.dp).expandOrigin(origin),
                    onOpen = { nav.push(Route.Detail(status.id, status.mid), origin) },
                    onUser = { nav.push(Route.Profile(it.id)) },
                    onImage = { pics, i -> nav.push(Route.Images(pics, i)) },
                    onVideo = { nav.push(Route.Video(it.url, it.title)) },
                    onLike = {
                        scope.launch {
                            if (!App.confirmLike(status.liked)) return@launch
                            runCatching { App.repo.like(status.id, status.liked) }.onSuccess {
                                items[index] = status.copy(
                                    liked = !status.liked,
                                    attitudes = (status.attitudes + if (status.liked) -1 else 1)
                                        .coerceAtLeast(0),
                                )
                            }
                        }
                    },
                    onRepost = { nav.push(Route.Detail(status.id, status.mid), origin) },
                    onComment = { nav.push(Route.Detail(status.id, status.mid), origin) },
                    onOpenQuoted = { quoted, quotedOrigin ->
                        quotedOrigin.attachToRow(listState, status.id, topBar)
                        nav.push(Route.Detail(quoted.id, quoted.mid), quotedOrigin)
                    },
                )
            }
            if (items.isNotEmpty()) {
                item {
                    if (loading) LoadingState()
                    else if (end) Text(
                        "没有更多了",
                        fontSize = 12.sp,
                        color = COUITheme.colorScheme.onSurfaceSecondary,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }
            }
        }
    }
}

private val ProfileAvatarRing = 68.dp

/** 封面下沿那道分隔线的粗细。 */
private val CoverDivider = 0.5.dp

/**
 * 关注按钮：顶栏返回键 / 三点键那颗 [HighlightedIconButton] 的材质拉成胶囊 —— 同样的半透明
 * 径向填充、材质描边和按下回弹缩放，只是形状换成能放下文字的胶囊。
 */
@Composable
private fun FollowCapsule(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = COUITheme.colorScheme
    val isDark = scheme.background.luminance() < 0.5f
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = HighlightedIconButtonDefaults.PressSpring,
        label = "followPress",
    )
    val brighten = HighlightedIconButtonDefaults.PressedFillBrighten * press
    Box(
        modifier = modifier
            .height(36.dp)
            .graphicsLayer {
                val scale = 1f - (1f - HighlightedIconButtonDefaults.PressedScale) * press
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to lerp(HighlightedIconButtonDefaults.fillCenter(isDark), Color.White, brighten)
                                .copy(alpha = HighlightedIconButtonDefaults.FillAlpha),
                            HighlightedIconButtonDefaults.FillEdgeStop to lerp(
                                HighlightedIconButtonDefaults.fillEdge(isDark),
                                Color.White,
                                brighten,
                            ).copy(alpha = HighlightedIconButtonDefaults.FillAlpha),
                        ),
                        center = center,
                        radius = size.maxDimension / 2f,
                    ),
                )
            }
            .materialShadowEdge(stroke = HighlightedIconButtonDefaults.stroke(isDark))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = lerp(scheme.onBackground, Color.White, HighlightedIconButtonDefaults.PressedContentBrighten * press),
            maxLines = 1,
        )
    }
}

@Composable
private fun ProfileHeader(
    user: User?,
    onFollow: (() -> Unit)?,
    followLabel: String,
    pullState: FullPagePullToRefreshState,
    tag: FollowTag? = null,
    /** 顶栏（含状态栏）的高度：封面顶到屏幕最上沿，多出这一截藏在顶栏玻璃下面。 */
    topInset: Dp = 0.dp,
    onActiveCover: (String?) -> Unit = {},
    onCover: (List<Pic>, Int) -> Unit = { _, _ -> },
    onRelations: (RelationKind) -> Unit = {},
) {
    val scheme = COUITheme.colorScheme
    val covers = user?.covers.orEmpty()
    val hasCover = covers.isNotEmpty()
    // 头像和昵称压在封面上；有封面图时整体压暗一层，文字统一用白色保证看得清。
    val onCoverColor = if (hasCover) Color.White else scheme.onSurface
    val pagerState = rememberPagerState { covers.size }
    LaunchedEffect(covers, pagerState.currentPage) {
        onActiveCover(covers.getOrNull(pagerState.currentPage))
    }
    // 封面在列表里只露中间一条，真实宽高比要等图片解码完才知道。记下来交给看图页，
    // 点开时才能从这一条长出来，而不是从整张图的居中位置凭空出现。
    val coverAspects = remember(covers) { mutableStateMapOf<Int, Float>() }
    Column(Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // 顶栏下面露出的封面只截中间一条，高度取头像的 1.5 倍，点开再看整张；
                // 往上再多出顶栏那一截垫在玻璃底下；下拉多少就往下拉长多少，
                // 封面是居中裁切，拉长就是把上下被裁掉的部分逐渐露出来。高度在布局阶段读，不逐帧重组。
                .layout { measurable, constraints ->
                    val height = (topInset + ProfileAvatarRing * 1.5f).roundToPx() +
                        pullState.dragOffset.roundToInt()
                    val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, height))
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                },
        ) {
            // 封面下沿是一条直线，再压一道分隔线把边界收实：封面很浅或白底时不至于像没铺满。
            // 上沿在顶栏玻璃底下，由顶栏的渐变接住。
            val dividerColor = scheme.dividerLine
            Box(
                Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        drawContent()
                        val stroke = CoverDivider.toPx()
                        drawLine(
                            color = dividerColor,
                            start = Offset(0f, size.height - stroke / 2),
                            end = Offset(size.width, size.height - stroke / 2),
                            strokeWidth = stroke,
                        )
                    }
                    .background(
                        Brush.linearGradient(listOf(scheme.primaryContainer, scheme.surfaceContainer))
                    ),
            ) {
            if (hasCover) {
                val origins = LocalImageOrigins.current
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize().testTag("profile_cover"),
                    userScrollEnabled = covers.size > 1,
                ) { page ->
                    AsyncImage(
                        model = covers[page],
                        contentDescription = "封面",
                        contentScale = ContentScale.Crop,
                        onSuccess = { state ->
                            origins.putCacheKey(covers[page], state.result.memoryCacheKey)
                            val size = state.painter.intrinsicSize
                            if (size.width > 0f && size.height > 0f) {
                                coverAspects[page] = size.width / size.height
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .imageOrigin(covers[page], 0.dp, aspect = coverAspects[page], model = covers[page])
                            .clickable {
                                onCover(covers.map { Pic(thumb = it, large = it) }, page)
                            },
                    )
                }
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
            }
            }
            if (hasCover) {
                if (covers.size > 1) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        repeat(covers.size) { i ->
                            Box(
                                Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Color.White.copy(
                                            alpha = if (i == pagerState.currentPage) 0.95f else 0.45f
                                        )
                                    )
                            )
                        }
                    }
                }
            }
            val hint = when (pullState.refreshState) {
                FullPageRefreshState.Pulling -> FullPageRefreshTexts.pulling
                FullPageRefreshState.ThresholdReached -> FullPageRefreshTexts.thresholdReached
                FullPageRefreshState.Refreshing -> FullPageRefreshTexts.refreshing
                FullPageRefreshState.RefreshComplete -> FullPageRefreshTexts.complete
                else -> ""
            }
            if (hint.isNotEmpty()) {
                Text(
                    hint,
                    fontSize = 12.sp,
                    color = onCoverColor.copy(alpha = 0.85f),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = topInset + 12.dp)
                        .graphicsLayer { alpha = pullState.pullProgress },
                )
            }
            // 头像一行贴着封面底部：不拉时上下各留 17dp 正好居中，拉长时停在原位，上方露出封面。
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = (ProfileAvatarRing * 0.5f) / 2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(ProfileAvatarRing)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.9f))
                        .padding(2.dp),
                ) {
                    Avatar(user?.avatar, 64.dp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    val membership = user?.membership ?: Membership.None
                    if (tag != null || membership != Membership.None) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (tag != null) {
                                FollowTagBadge(tag)
                            }
                            if (membership != Membership.None) {
                                MemberBadge(membership)
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                    }
                    Text(
                        user?.name ?: "加载中…",
                        color = onCoverColor,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (user?.verified == true) {
                        Text(
                            "已认证",
                            fontSize = 11.sp,
                            color = if (hasCover) Color.White.copy(alpha = 0.85f) else scheme.primary,
                        )
                    }
                }
                if (onFollow != null) {
                    Spacer(Modifier.width(8.dp))
                    FollowCapsule(
                        text = followLabel,
                        onClick = onFollow,
                        modifier = Modifier.testTag("follow_button"),
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
        ) {
            if (!user?.description.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    user.description,
                    fontSize = 13.sp,
                    color = scheme.onSurfaceSecondary,
                    lineHeight = 18.sp,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(26.dp),
            ) {
                Stat("微博", user?.statuses ?: 0)
                Stat("关注", user?.friends ?: 0, testTag = "profile_following") {
                    onRelations(RelationKind.Following)
                }
                Stat("粉丝", user?.followers ?: 0, testTag = "profile_followers") {
                    onRelations(RelationKind.Followers)
                }
            }
        }
    }
}

@Composable
private fun Stat(
    label: String,
    value: Int,
    testTag: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(vertical = 2.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(fmtCount(value), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 12.sp, color = COUITheme.colorScheme.onSurfaceSecondary)
    }
}
