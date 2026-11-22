package com.cobo.app.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cobo.app.App
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
import com.cobo.app.ui.components.navBottomPadding
import com.cobo.app.ui.expandOrigin
import com.cobo.app.ui.glassBarContentTop
import com.cobo.app.ui.layerBackdropIf
import com.cobo.app.ui.pageContainerColor
import com.cobo.app.ui.pinnedHeight
import com.cobo.app.ui.rememberExpandOrigin
import com.cobo.app.ui.reportTopBarBottom
import io.github.suqi8.coui.kmp.basic.AppBarBlurOverlayDefaults
import io.github.suqi8.coui.kmp.basic.BindScrollToTop
import io.github.suqi8.coui.kmp.basic.ChipTabData
import io.github.suqi8.coui.kmp.basic.ChipTabGroup
import io.github.suqi8.coui.kmp.basic.FullPagePullToRefresh
import io.github.suqi8.coui.kmp.basic.LocalEdgeToEdge
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.basic.TwoRowTopAppBar
import io.github.suqi8.coui.kmp.basic.TwoRowTopAppBarDefaults
import io.github.suqi8.coui.kmp.basic.fullPagePullToRefreshColors
import io.github.suqi8.coui.kmp.basic.rememberFullPagePullToRefreshState
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
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
        )
        return
    }

    val scope = rememberCoroutineScope()
    val cloudStore = remember {
        FeedStore("cloud_favorites") { cursor, force -> App.repo.cloudFavorites(cursor, force) }
    }
    val tabs = remember { listOf("微博收藏", "本地收藏") }
    val chipTabs = remember(tabs) { tabs.map { ChipTabData(label = it) } }
    val stores = remember(cloudStore, localStore) { listOf(cloudStore, localStore) }
    val listStates = remember { listOf(LazyListState(), LazyListState()) }
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { tabs.size })
    val backdrop = rememberLayerBackdrop()
    val blur = blurEffectsEnabled()
    val topBarBounds = remember { TopBarBounds() }
    val topBarHeight = pinnedHeight(
        TwoRowTopAppBarDefaults.barHeight(
            firstRowHeight = TwoRowTopAppBarDefaults.FirstRowHeight,
            secondRowHeight = TwoRowTopAppBarDefaults.SecondRowHeight,
        ),
    )

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
                            emptyText = if (page == 0) "微博账号里还没有收藏" else "还没有本地收藏",
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
                val horizontalPadding = searchHorizontalPadding()
                val selectedIdx = pagerState.currentPage.coerceIn(0, tabs.lastIndex)
                TwoRowTopAppBar(
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().reportTopBarBottom(topBarBounds),
                    firstRowHeight = TwoRowTopAppBarDefaults.FirstRowHeight,
                    secondRowHeight = TwoRowTopAppBarDefaults.SecondRowHeight,
                    containerColor = pageContainerColor(),
                    secondRow = {
                        ChipTabGroup(
                            tabs = chipTabs,
                            selectedIndex = selectedIdx,
                            onSelectedChange = { idx ->
                                scope.launch { pagerState.animateScrollToPage(idx) }
                            },
                            contentPadding = PaddingValues(horizontal = horizontalPadding),
                            backdrop = LocalBackdrop.current,
                        )
                    },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(TwoRowTopAppBarDefaults.FirstRowHeight)
                            .padding(horizontal = horizontalPadding),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BackIconButton { nav.pop() }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "收藏",
                            color = COUITheme.colorScheme.onSurface,
                            maxLines = 1,
                            fontSize = with(LocalDensity.current) { TopAppBarDefaults.ToolbarTitleTextSize.toSp() },
                            fontWeight = FontWeight(550),
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false,
                        )
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
) {
    val listState = rememberLazyListState()
    BindScrollToTop(listState)
    val backdrop = rememberLayerBackdrop()
    val blur = blurEffectsEnabled()
    val density = LocalDensity.current
    val topBarBounds = remember { TopBarBounds() }
    val topBarHeight = pinnedHeight(TopAppBarDefaults.SmallTopAppBarCenterHeight)

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
) {
    val scope = rememberCoroutineScope()
    val pullState = rememberFullPagePullToRefreshState()

    SyncDeletedStatuses(store)
    LaunchedEffect(store) {
        if (!store.loaded) store.loadInitial()
    }
    LaunchedEffect(listState, store) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { last ->
                if (last >= store.items.size - 2 && store.items.isNotEmpty()) store.loadMore()
            }
    }

    FullPagePullToRefresh(
        isRefreshing = store.refreshing,
        onRefresh = { scope.launch { store.refresh() } },
        modifier = Modifier.fillMaxSize(),
        state = pullState,
        contentPadding = PaddingValues(top = topInset),
        colors = fullPagePullToRefreshColors(backgroundColor = pageContainerColor()),
        refreshTexts = FullPageRefreshTexts,
        completeHoldDurationMillis = FullPageRefreshHoldMillis,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().scrollEndHaptic().overScrollVertical(),
            contentPadding = PaddingValues(
                start = 12.dp,
                top = glassBarContentTop(topInset, 4.dp),
                end = 12.dp,
                bottom = 16.dp + navBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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
            }
            itemsIndexed(store.items, key = { _, s -> s.id }) { index, status ->
                val origin = rememberExpandOrigin()
                val topBar = LocalTopBarBounds.current
                SideEffect {
                    origin.listState = listState
                    origin.rowKey = status.id
                    origin.topBar = topBar
                }
                StatusCard(
                    status = status,
                    modifier = Modifier.expandOrigin(origin),
                    onOpen = { nav.push(Route.Detail(status.id, status.mid, store.readOnly), origin) },
                    onUser = { nav.push(Route.Profile(it.id)) },
                    onImage = { pics, i -> nav.push(Route.Images(pics, i)) },
                    onVideo = { nav.push(Route.Video(it.url, it.title)) },
                    onLike = { scope.launch { store.like(index) } },
                    onRepost = { nav.push(Route.Detail(status.id, status.mid, store.readOnly), origin) },
                    onComment = { nav.push(Route.Detail(status.id, status.mid, store.readOnly), origin) },
                    actionsEnabled = !store.readOnly,
                    onOpenQuoted = { quoted, quotedOrigin ->
                        quotedOrigin.attachToRow(listState, status.id, topBar)
                        nav.push(Route.Detail(quoted.id, quoted.mid, store.readOnly), quotedOrigin)
                    },
                    keepFrame = App.settings.feedCardFrame,
                )
            }
            if (store.items.isNotEmpty()) {
                item {
                    if (store.loading) {
                        LoadingState()
                    } else if (store.end) {
                        Text(
                            text = "没有更多了",
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
