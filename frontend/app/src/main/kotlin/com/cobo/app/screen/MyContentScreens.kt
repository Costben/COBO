package com.cobo.app.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cobo.app.App
import com.cobo.app.data.MediaWallFilter
import com.cobo.app.data.Status
import com.cobo.app.data.extractMediaWallItems
import com.cobo.app.data.parseWeiboTimeMillis
import com.cobo.app.ui.AppBarSurface
import com.cobo.app.ui.LocalTopBarBounds
import com.cobo.app.ui.NavState
import com.cobo.app.ui.PinnedBarDensity
import com.cobo.app.ui.Route
import com.cobo.app.ui.TopBarBounds
import com.cobo.app.ui.attachToRow
import com.cobo.app.ui.blurEffectsEnabled
import com.cobo.app.ui.components.BackIconButton
import com.cobo.app.ui.components.EmptyState
import com.cobo.app.ui.components.ErrorState
import com.cobo.app.ui.components.FullPageRefreshHoldMillis
import com.cobo.app.ui.components.FullPageRefreshTexts
import com.cobo.app.ui.components.LoadingState
import com.cobo.app.ui.components.StatusCard
import com.cobo.app.ui.components.mediaAlbumGridItems
import com.cobo.app.ui.components.navBottomPadding
import com.cobo.app.ui.expandOrigin
import com.cobo.app.ui.glassBarContentTop
import com.cobo.app.ui.layerBackdropIf
import com.cobo.app.ui.listScrollAlpha
import com.cobo.app.ui.pageContainerColor
import com.cobo.app.ui.pinnedHeight
import com.cobo.app.ui.rememberExpandOrigin
import com.cobo.app.ui.reportTopBarBottom
import io.github.suqi8.coui.kmp.basic.AppBarBlurOverlayDefaults
import io.github.suqi8.coui.kmp.basic.BindScrollToTop
import io.github.suqi8.coui.kmp.basic.DropdownEntry
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.FullPagePullToRefresh
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.InputField
import io.github.suqi8.coui.kmp.basic.ListPopupDefaults
import io.github.suqi8.coui.kmp.basic.LocalEdgeToEdge
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.TabRowDefaults
import io.github.suqi8.coui.kmp.basic.TabRowWithContour
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.basic.couiTopAppBarActionItem
import io.github.suqi8.coui.kmp.basic.fullPagePullToRefreshColors
import io.github.suqi8.coui.kmp.basic.rememberFullPagePullToRefreshState
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.GridView
import io.github.suqi8.coui.kmp.icon.extended.Hide
import io.github.suqi8.coui.kmp.icon.extended.More
import io.github.suqi8.coui.kmp.icon.extended.Notes
import io.github.suqi8.coui.kmp.icon.extended.Search
import io.github.suqi8.coui.kmp.icon.extended.Sort
import io.github.suqi8.coui.kmp.overlay.OverlayCascadingListPopup
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic
import kotlinx.coroutines.launch

/**
 * 收藏页面：已登录账号提供「微博收藏」（云端）与「本地收藏」两栏，纯本地模式直接展示本地收藏。
 */
@Composable
fun FavoritesScreen(nav: NavState) {
    val guest = remember { App.repo.guest }
    val localStore = remember {
        FeedStore("local_favorites") { cursor, force -> App.repo.favorites(cursor, force) }
    }
    val favVersion = App.repo.favoritesVersion
    LaunchedEffect(favVersion) {
        if (favVersion > 0) localStore.refresh()
    }

    if (guest) {
        SimpleStatusFeedScreen(
            nav = nav,
            title = "收藏",
            store = localStore,
            emptyText = "还没有收藏的微博",
            testTag = "favorites_page",
            enableAlbumWall = true,
        )
        return
    }

    val scope = rememberCoroutineScope()
    val cloudStore = remember {
        FeedStore("cloud_favorites") { cursor, force -> App.repo.cloudFavorites(cursor, force) }
    }
    val tabs = remember { listOf("微博收藏", "本地收藏") }
    val stores = remember(cloudStore, localStore) { listOf(cloudStore, localStore) }
    val listStates = remember { listOf(LazyListState(), LazyListState()) }
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { tabs.size })
    val backdrop = rememberLayerBackdrop()
    val blur = blurEffectsEnabled()
    val topBarBounds = remember { TopBarBounds() }
    val topBarHeight = pinnedHeight(TopAppBarDefaults.SmallTopAppBarCenterHeight)

    var menuExpanded by remember { mutableStateOf(false) }
    var albumMode by remember { mutableStateOf(false) }
    var albumFilter by remember { mutableStateOf(MediaWallFilter.All) }
    var sortOrder by remember { mutableStateOf(FavoritesSortOrder.NewestFirst) }
    var hideExpired by remember { mutableStateOf(false) }
    var filterOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var filterRowPx by remember { mutableIntStateOf(0) }
    val filterRowHeight = with(LocalDensity.current) { filterRowPx.toDp() }
    val filterInset by animateDpAsState(
        targetValue = if (filterOpen) filterRowHeight else 0.dp,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "favoritesFilterInset",
    )

    BackHandler(enabled = filterOpen) {
        filterOpen = false
        query = ""
    }

    Box(Modifier.fillMaxSize().background(pageContainerColor()).testTag("favorites_page")) {
        CompositionLocalProvider(LocalTopBarBounds provides topBarBounds) {
            Box(
                Modifier
                    .fillMaxSize()
                    .layerBackdropIf(blur, backdrop)
                    .background(pageContainerColor()),
            ) {
                CompositionLocalProvider(LocalBackdrop provides if (blur) backdrop else null) {
                    HorizontalPager(
                        state = pagerState,
                        beyondViewportPageCount = 1,
                        modifier = Modifier.fillMaxSize(),
                    ) { page ->
                        val store = stores[page]
                        val listState = listStates[page]
                        BindScrollToTop(listState, enabled = page == pagerState.settledPage)
                        StatusFeedList(
                            nav = nav,
                            store = store,
                            listState = listState,
                            topInset = topBarHeight,
                            extraTopInset = filterInset,
                            emptyText = if (page == 0) "微博账号里还没有收藏" else "还没有本地收藏",
                            filteredEmptyText = when {
                                query.isNotBlank() -> "没有匹配的收藏"
                                hideExpired -> "当前列表均为已失效微博"
                                else -> null
                            },
                            albumMode = albumMode,
                            albumFilter = albumFilter,
                            onAlbumFilterChange = { albumFilter = it },
                            transformItems = { items ->
                                filterAndSortFavorites(
                                    items = items,
                                    sortOrder = sortOrder,
                                    hideExpired = hideExpired,
                                    query = query,
                                )
                            },
                        )
                    }
                }
            }
        }

        CompositionLocalProvider(
            LocalBackdrop provides if (blur) backdrop else null,
            LocalEdgeToEdge provides true,
        ) {
            PinnedBarDensity {
                val barDensity = LocalDensity.current
                val menuScheme = COUITheme.colorScheme
                val selectedIdx = pagerState.currentPage.coerceIn(0, tabs.lastIndex)
                Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
                    AppBarSurface(
                        modifier = Modifier.fillMaxWidth().reportTopBarBottom(topBarBounds),
                        containerColor = pageContainerColor(),
                        scrollFraction = {
                            val activeIdx = pagerState.currentPage.coerceIn(0, listStates.lastIndex)
                            listScrollAlpha(listStates[activeIdx], barDensity)
                        },
                    ) {
                        SmallTopAppBar(
                            title = "",
                            color = Color.Transparent,
                            showDivider = false,
                            gradientGlass = false,
                            navigationIcon = { BackIconButton { nav.pop() } },
                            segmentButton = {
                                TabRowWithContour(
                                    tabs = tabs,
                                    selectedTabIndex = selectedIdx,
                                    onTabSelected = { idx ->
                                        scope.launch { pagerState.animateScrollToPage(idx) }
                                    },
                                    colors = if (blur) {
                                        TabRowDefaults.tabRowColors()
                                    } else {
                                        TabRowDefaults.opaqueTabRowColors()
                                    },
                                    height = TopAppBarDefaults.SegmentButtonHeight,
                                    segmentPadding = 12.dp,
                                    backdrop = LocalBackdrop.current,
                                    commitOnRelease = true,
                                    dragSelectionEnabled = true,
                                    touchMotionEnabled = true,
                                )
                            },
                            actions = {
                                Box {
                                    HighlightedIconButton(
                                        onClick = { menuExpanded = true },
                                        modifier = Modifier.couiTopAppBarActionItem().testTag("favorites_more"),
                                    ) {
                                        Icon(COUIIcons.More, "更多", Modifier.size(22.dp))
                                    }
                                    OverlayCascadingListPopup(
                                        show = menuExpanded,
                                        entries = listOf(
                                            DropdownEntry(
                                                items = listOf(
                                                    DropdownItem(
                                                        text = "排序",
                                                        summary = if (sortOrder == FavoritesSortOrder.NewestFirst) {
                                                            "最新优先"
                                                        } else {
                                                            "最早优先"
                                                        },
                                                        icon = { iconModifier ->
                                                            Box(iconModifier, contentAlignment = Alignment.Center) {
                                                                Icon(
                                                                    imageVector = COUIIcons.Sort,
                                                                    contentDescription = null,
                                                                    modifier = Modifier.size(18.dp),
                                                                    tint = menuScheme.onSurfaceContainer,
                                                                )
                                                            }
                                                        },
                                                        children = listOf(
                                                            DropdownItem(
                                                                text = "最新优先",
                                                                selected = sortOrder == FavoritesSortOrder.NewestFirst,
                                                                onClick = {
                                                                    if (sortOrder != FavoritesSortOrder.NewestFirst) {
                                                                        sortOrder = FavoritesSortOrder.NewestFirst
                                                                        scope.launch { listStates[selectedIdx].scrollToItem(0) }
                                                                    }
                                                                },
                                                            ),
                                                            DropdownItem(
                                                                text = "最早优先",
                                                                selected = sortOrder == FavoritesSortOrder.OldestFirst,
                                                                onClick = {
                                                                    if (sortOrder != FavoritesSortOrder.OldestFirst) {
                                                                        sortOrder = FavoritesSortOrder.OldestFirst
                                                                        scope.launch { listStates[selectedIdx].scrollToItem(0) }
                                                                    }
                                                                },
                                                            ),
                                                        ),
                                                    ),
                                                    DropdownItem(
                                                        text = "过滤失效的微博",
                                                        selected = hideExpired,
                                                        icon = { iconModifier ->
                                                            Box(iconModifier, contentAlignment = Alignment.Center) {
                                                                Icon(
                                                                    imageVector = COUIIcons.Hide,
                                                                    contentDescription = null,
                                                                    modifier = Modifier.size(18.dp),
                                                                    tint = menuScheme.onSurfaceContainer,
                                                                )
                                                            }
                                                        },
                                                        onClick = { hideExpired = !hideExpired },
                                                    ),
                                                    DropdownItem(
                                                        text = "相册墙视图",
                                                        selected = albumMode,
                                                        icon = { iconModifier ->
                                                            Box(iconModifier, contentAlignment = Alignment.Center) {
                                                                Icon(
                                                                    imageVector = COUIIcons.GridView,
                                                                    contentDescription = null,
                                                                    modifier = Modifier.size(18.dp),
                                                                    tint = menuScheme.onSurfaceContainer,
                                                                )
                                                            }
                                                        },
                                                        onClick = { albumMode = !albumMode },
                                                    ),
                                                    DropdownItem(
                                                        text = "搜索",
                                                        summary = query.trim().takeIf { it.isNotEmpty() },
                                                        selected = filterOpen,
                                                        icon = { iconModifier ->
                                                            Box(iconModifier, contentAlignment = Alignment.Center) {
                                                                Icon(
                                                                    imageVector = COUIIcons.Search,
                                                                    contentDescription = null,
                                                                    modifier = Modifier.size(18.dp),
                                                                    tint = menuScheme.onSurfaceContainer,
                                                                )
                                                            }
                                                        },
                                                        onClick = {
                                                            filterOpen = !filterOpen
                                                            if (!filterOpen) query = ""
                                                        },
                                                    ),
                                                ),
                                            ),
                                        ),
                                        popupPositionProvider = ListPopupDefaults.DropdownPositionProvider,
                                        onDismissRequest = { menuExpanded = false },
                                    )
                                }
                            },
                        )
                    }
                    if (filterOpen || filterInset > 0.dp) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(filterInset)
                                .clipToBounds(),
                        ) {
                            Column(Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onSizeChanged { filterRowPx = it.height }
                                        .padding(
                                            start = TopAppBarDefaults.NavigationIconPadding,
                                            end = TopAppBarDefaults.ActionIconPadding,
                                            top = 6.dp,
                                            bottom = 6.dp,
                                        ),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    InputField(
                                        query = query,
                                        onQueryChange = { query = it },
                                        onSearch = {},
                                        expanded = filterOpen,
                                        onExpandedChange = {},
                                        modifier = Modifier.weight(1f).testTag("favorites_filter_input"),
                                        label = "搜索收藏内容或作者",
                                        backdrop = LocalBackdrop.current,
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = "取消",
                                        fontSize = 14.sp,
                                        color = menuScheme.primary,
                                        modifier = Modifier
                                            .clickable {
                                                filterOpen = false
                                                query = ""
                                            }
                                            .padding(horizontal = 4.dp, vertical = 6.dp)
                                            .testTag("favorites_filter_cancel"),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 我赞过的微博列表页面。 */
@Composable
fun MyLikesScreen(nav: NavState) {
    val store = remember {
        FeedStore("my_likes") { cursor, force ->
            val uid = App.repo.ownUid()
            App.repo.likedStatuses(uid, cursor, force)
        }
    }
    SimpleStatusFeedScreen(
        nav = nav,
        title = "我赞过的",
        store = store,
        emptyText = "还没有赞过的微博",
        testTag = "my_likes_page",
    )
}

@Composable
private fun SimpleStatusFeedScreen(
    nav: NavState,
    title: String,
    store: FeedStore,
    emptyText: String,
    testTag: String,
    enableAlbumWall: Boolean = false,
) {
    val listState = rememberLazyListState()
    BindScrollToTop(listState)
    val backdrop = rememberLayerBackdrop()
    val blur = blurEffectsEnabled()
    val density = LocalDensity.current
    val topBarBounds = remember { TopBarBounds() }
    val topBarHeight = pinnedHeight(TopAppBarDefaults.SmallTopAppBarCenterHeight)
    var albumMode by remember { mutableStateOf(false) }
    var albumFilter by remember { mutableStateOf(MediaWallFilter.All) }

    Box(Modifier.fillMaxSize().background(pageContainerColor()).testTag(testTag)) {
        CompositionLocalProvider(LocalTopBarBounds provides topBarBounds) {
            Box(
                Modifier
                    .fillMaxSize()
                    .layerBackdropIf(blur, backdrop)
                    .background(pageContainerColor()),
            ) {
                CompositionLocalProvider(LocalBackdrop provides if (blur) backdrop else null) {
                    StatusFeedList(
                        nav = nav,
                        store = store,
                        listState = listState,
                        topInset = topBarHeight,
                        emptyText = emptyText,
                        albumMode = enableAlbumWall && albumMode,
                        albumFilter = albumFilter,
                        onAlbumFilterChange = { albumFilter = it },
                    )
                }
            }
        }

        CompositionLocalProvider(
            LocalBackdrop provides if (blur) backdrop else null,
            LocalEdgeToEdge provides true,
        ) {
            PinnedBarDensity {
                AppBarSurface(
                    modifier = Modifier.align(Alignment.TopCenter).reportTopBarBottom(topBarBounds),
                    containerColor = pageContainerColor(),
                    scrollFraction = {
                        val scrolled = if (listState.firstVisibleItemIndex > 0) Float.MAX_VALUE
                        else listState.firstVisibleItemScrollOffset.toFloat()
                        AppBarBlurOverlayDefaults.scrollAlpha(scrolled, density)
                    },
                ) {
                    SmallTopAppBar(
                        title = title,
                        color = Color.Transparent,
                        showDivider = false,
                        gradientGlass = false,
                        navigationIcon = { BackIconButton { nav.pop() } },
                        actions = {
                            if (enableAlbumWall) {
                                HighlightedIconButton(
                                    onClick = { albumMode = !albumMode },
                                    modifier = Modifier.couiTopAppBarActionItem().testTag("favorites_album_toggle"),
                                ) {
                                    Icon(
                                        imageVector = if (albumMode) COUIIcons.Notes else COUIIcons.GridView,
                                        contentDescription = if (albumMode) "切换列表视图" else "切换相册墙",
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusFeedList(
    nav: NavState,
    store: FeedStore,
    listState: LazyListState,
    topInset: Dp,
    emptyText: String,
    extraTopInset: Dp = 0.dp,
    filteredEmptyText: String? = null,
    albumMode: Boolean = false,
    albumFilter: MediaWallFilter = MediaWallFilter.All,
    onAlbumFilterChange: (MediaWallFilter) -> Unit = {},
    transformItems: (List<Status>) -> List<Status> = { it },
) {
    val scope = rememberCoroutineScope()
    val pullState = rememberFullPagePullToRefreshState()
    val shownItems = transformItems(store.items)

    SyncDeletedStatuses(store)
    LaunchedEffect(store) {
        if (!store.loaded) store.loadInitial()
    }
    LaunchedEffect(listState, store, shownItems.size, albumMode) {
        snapshotFlow {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: -1) to info.totalItemsCount
        }.collect { (last, total) ->
            if (total > 0 && last >= total - 2 && store.items.isNotEmpty()) store.loadMore()
        }
    }

    FullPagePullToRefresh(
        isRefreshing = store.refreshing,
        onRefresh = { scope.launch { store.refresh() } },
        modifier = Modifier.fillMaxSize(),
        state = pullState,
        contentPadding = PaddingValues(top = topInset + extraTopInset),
        colors = fullPagePullToRefreshColors(backgroundColor = pageContainerColor()),
        refreshTexts = FullPageRefreshTexts,
        completeHoldDurationMillis = FullPageRefreshHoldMillis,
    ) {
        val feedCardFrame = App.settings.feedCardFrame
        val wallItems = if (albumMode) {
            extractMediaWallItems(
                statuses = shownItems,
                includeRetweeted = true,
                filter = albumFilter,
            )
        } else {
            emptyList()
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().scrollEndHaptic().overScrollVertical(),
            contentPadding = PaddingValues(
                start = 12.dp,
                top = glassBarContentTop(topInset, 4.dp) + extraTopInset,
                end = 12.dp,
                bottom = 16.dp + navBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(if (albumMode) 3.dp else if (feedCardFrame) 8.dp else 0.dp),
        ) {
            if (albumMode) {
                item(key = "album_filter_tabs") {
                    TabRowWithContour(
                        tabs = MediaWallFilter.entries.map { it.label },
                        selectedTabIndex = albumFilter.ordinal,
                        onTabSelected = { idx -> onAlbumFilterChange(MediaWallFilter.entries[idx]) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 5.dp)
                            .testTag("favorites_album_tabs"),
                        height = TabRowDefaults.TabRowWithContourTinyHeight,
                        cornerRadius = TabRowDefaults.TabRowWithContourTinyCornerRadius,
                        contourPadding = TabRowDefaults.TabRowWithContourTinyPadding,
                        segmentPadding = 6.dp,
                    )
                }
            }
            if (store.items.isEmpty()) {
                item {
                    when {
                        store.refreshing || (!store.loaded && store.error == null) -> LoadingState()
                        store.error != null -> ErrorState(store.error!!) {
                            scope.launch { store.refresh() }
                        }
                        else -> EmptyState(emptyText)
                    }
                }
            } else if (albumMode) {
                if (wallItems.isEmpty()) {
                    item(key = "album_filtered_empty") {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            EmptyState(
                                if (shownItems.isEmpty() && filteredEmptyText != null) filteredEmptyText
                                else "当前收藏中暂无匹配的图片或视频",
                            )
                            if (!store.end && !store.loading) {
                                TextButton(
                                    text = "加载更多",
                                    onClick = { scope.launch { store.loadMore() } },
                                )
                            }
                        }
                    }
                } else {
                    mediaAlbumGridItems(
                        items = wallItems,
                        horizontalPadding = 0.dp,
                        onPhotoClick = { pics, idx -> nav.push(Route.Images(pics, idx)) },
                        onVideoClick = { nav.push(Route.Video(it.url, it.title, it.isLive, it.fid, it.statusId)) },
                        onOpenPost = { nav.push(Route.Detail(it.id, it.mid, store.readOnly)) },
                    )
                }
            } else if (shownItems.isEmpty()) {
                item { EmptyState(filteredEmptyText ?: emptyText) }
            } else {
                itemsIndexed(shownItems, key = { _, s -> s.id }) { index, status ->
                    val origin = rememberExpandOrigin()
                    val topBar = LocalTopBarBounds.current
                    val showDivider = !feedCardFrame && shownItems.drop(index + 1).any { !App.repo.isDeleted(it.id) }
                    SideEffect {
                        origin.listState = listState
                        origin.rowKey = status.id
                        origin.originKey = "card:${status.id}"
                        origin.topBar = topBar
                    }
                    StatusCard(
                        status = status,
                        modifier = Modifier.expandOrigin(origin),
                        onOpen = { nav.push(Route.Detail(status.id, status.mid, store.readOnly), origin) },
                        onUser = { nav.push(Route.Profile(it.id)) },
                        onImage = { pics, i -> nav.push(Route.Images(pics, i)) },
                        onVideo = { nav.push(Route.Video(it.url, it.title, it.isLive, it.fid, it.statusId)) },
                        onLike = {
                            val rawIdx = store.items.indexOfFirst { it.id == status.id }
                            if (rawIdx >= 0) scope.launch { store.like(rawIdx) }
                        },
                        onRepost = { nav.push(Route.Detail(status.id, status.mid, store.readOnly), origin) },
                        onComment = { nav.push(Route.Detail(status.id, status.mid, store.readOnly), origin) },
                        actionsEnabled = !store.readOnly,
                        onOpenQuoted = { quoted, quotedOrigin ->
                            quotedOrigin.attachToRow(listState, status.id, topBar)
                            nav.push(Route.Detail(quoted.id, quoted.mid, store.readOnly), quotedOrigin)
                        },
                        keepFrame = feedCardFrame,
                        showBottomDivider = showDivider,
                    )
                }
            }
            if (shownItems.isNotEmpty() && (!albumMode || wallItems.isNotEmpty())) {
                item {
                    if (store.loading) {
                        LoadingState()
                    } else if (store.end) {
                        Text(
                            text = if (albumMode) "长按任意缩略图可查看原微博" else "没有更多了",
                            color = COUITheme.colorScheme.onSurfaceSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        )
                    }
                }
            }
        }
    }
}

private enum class FavoritesSortOrder { NewestFirst, OldestFirst }

private fun filterAndSortFavorites(
    items: List<Status>,
    sortOrder: FavoritesSortOrder,
    hideExpired: Boolean,
    query: String,
): List<Status> {
    val key = query.trim()
    val filtered = items.filter { status ->
        (!hideExpired || !isExpiredFavorite(status)) &&
            (key.isEmpty() || statusMatchesQuery(status, key))
    }
    return when (sortOrder) {
        FavoritesSortOrder.NewestFirst -> filtered.sortedByDescending(::statusTimestampKey)
        FavoritesSortOrder.OldestFirst -> filtered.sortedBy(::statusTimestampKey)
    }
}

private fun statusTimestampKey(status: Status): Long =
    parseWeiboTimeMillis(status.createdAt).takeIf { it > 0L }
        ?: status.id.toLongOrNull()
        ?: 0L

private fun statusMatchesQuery(status: Status, keyword: String): Boolean {
    if (status.plainText.contains(keyword, ignoreCase = true)) return true
    if (status.user?.name?.contains(keyword, ignoreCase = true) == true) return true
    val quoted = status.retweeted ?: return false
    if (quoted.plainText.contains(keyword, ignoreCase = true)) return true
    return quoted.user?.name?.contains(keyword, ignoreCase = true) == true
}

private fun isExpiredFavorite(status: Status): Boolean {
    if (App.repo.isDeleted(status.id)) return true
    val author = status.user
    if (author == null || (author.id.isBlank() && author.name.isBlank())) return true
    val text = status.plainText.trim()
    val hasMedia = status.pics.isNotEmpty() || status.video != null || status.bigPic != null
    if (!hasMedia && status.retweeted == null && text.isBlank()) return true
    if (!hasMedia && isWeiboExpiredNotice(text)) return true
    val quoted = status.retweeted
    if (!hasMedia && quoted != null && (text.isBlank() || text == "转发微博")) {
        return isExpiredFavorite(quoted)
    }
    return false
}

private val WeiboExpiredNoticeMarkers = listOf(
    "抱歉，此微博已被",
    "此微博已被作者删除",
    "该微博已被删除",
    "该微博已删除",
    "原微博已删除",
    "此条微博已被删除",
    "此微博已不可见",
    "该微博已不可见",
    "暂时没有这条微博的查看权限",
    "由于作者设置",
    "由于博主设置",
    "微博已不存在",
    "该内容暂时无法查看",
    "内容暂不可见",
    "此内容暂不可见",
    "该账号因被投诉违反",
    "根据相关法律法规和政策",
)

private fun isWeiboExpiredNotice(text: String): Boolean =
    text.length <= 80 && WeiboExpiredNoticeMarkers.any { text.contains(it) }
