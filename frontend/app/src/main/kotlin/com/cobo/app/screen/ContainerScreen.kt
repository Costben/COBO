package com.cobo.app.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.cobo.app.App
import com.cobo.app.ui.AppBarSurface
import com.cobo.app.ui.NavState
import com.cobo.app.ui.PinnedBarDensity
import com.cobo.app.ui.Route
import com.cobo.app.ui.blurEffectsEnabled
import com.cobo.app.ui.expandOrigin
import com.cobo.app.ui.layerBackdropIf
import com.cobo.app.ui.pageContainerColor
import com.cobo.app.ui.pinnedHeight
import com.cobo.app.ui.rememberExpandOrigin
import com.cobo.app.ui.components.BackIconButton
import com.cobo.app.ui.components.EmptyState
import com.cobo.app.ui.components.ErrorState
import com.cobo.app.ui.components.FullPageRefreshHoldMillis
import com.cobo.app.ui.components.FullPageRefreshTexts
import com.cobo.app.ui.components.LoadingState
import com.cobo.app.ui.components.StatusCard
import com.cobo.app.ui.components.navBottomPadding
import io.github.suqi8.coui.kmp.basic.AppBarBlurOverlayDefaults
import io.github.suqi8.coui.kmp.basic.BindScrollToTop
import io.github.suqi8.coui.kmp.basic.FullPagePullToRefresh
import io.github.suqi8.coui.kmp.basic.LocalEdgeToEdge
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.basic.fullPagePullToRefreshColors
import io.github.suqi8.coui.kmp.basic.rememberFullPagePullToRefreshState
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.utils.overScrollVertical
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic
import kotlinx.coroutines.launch

@Composable
fun ContainerScreen(nav: NavState, cid: String, title: String) {
    val scope = rememberCoroutineScope()
    val isFavorites = cid == "230267"
    val store = remember(cid) {
        if (isFavorites) {
            FeedStore(cid) { cursor, force -> App.repo.favorites(cursor, force) }
        } else {
            FeedStore(cid)
        }
    }
    val listState = rememberLazyListState()
    BindScrollToTop(listState)
    val pullState = rememberFullPagePullToRefreshState()
    val backdrop = rememberLayerBackdrop()
    val blur = blurEffectsEnabled()
    val density = LocalDensity.current
    val topBarHeight = pinnedHeight(TopAppBarDefaults.SmallTopAppBarCenterHeight)

    SyncDeletedStatuses(store)
    LaunchedEffect(cid) { store.loadInitial() }
    if (isFavorites) {
        val favVersion = App.repo.favoritesVersion
        LaunchedEffect(favVersion) {
            if (favVersion > 0) store.refresh()
        }
    }
    LaunchedEffect(listState, store) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { last ->
                if (last >= store.items.size - 2 && store.items.isNotEmpty()) store.loadMore()
            }
    }

    Box(Modifier.fillMaxSize().background(pageContainerColor())) {
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdropIf(blur, backdrop)
                .background(pageContainerColor()),
        ) {
            CompositionLocalProvider(LocalBackdrop provides if (blur) backdrop else null) {
                FullPagePullToRefresh(
                isRefreshing = store.refreshing,
                onRefresh = { scope.launch { store.refresh() } },
                modifier = Modifier.fillMaxSize(),
                state = pullState,
                contentPadding = PaddingValues(top = topBarHeight),
                colors = fullPagePullToRefreshColors(backgroundColor = pageContainerColor()),
                refreshTexts = FullPageRefreshTexts,
                completeHoldDurationMillis = FullPageRefreshHoldMillis,
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().scrollEndHaptic().overScrollVertical(),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        top = topBarHeight + 8.dp,
                        end = 12.dp,
                        bottom = 12.dp + navBottomPadding(),
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (store.items.isEmpty()) {
                        item {
                            when {
                                store.refreshing -> LoadingState()
                                store.error != null -> ErrorState(store.error!!) {
                                    scope.launch { store.refresh() }
                                }
                                else -> EmptyState(if (isFavorites) "还没有收藏的微博" else "这里还没有内容")
                            }
                        }
                    }
                    itemsIndexed(store.items, key = { _, s -> s.id }) { index, status ->
                        val origin = rememberExpandOrigin()
                        StatusCard(
                            status = status,
                            modifier = Modifier.expandOrigin(origin),
                            onOpen = { nav.push(Route.Detail(status.id, status.mid), origin) },
                            onOpenQuoted = { quoted, quotedOrigin ->
                                nav.push(Route.Detail(quoted.id, quoted.mid), quotedOrigin)
                            },
                            onUser = { nav.push(Route.Profile(it.id)) },
                            onImage = { pics, i -> nav.push(Route.Images(pics, i)) },
                            onVideo = { nav.push(Route.Video(it.url, it.title, it.isLive)) },
                            onLike = { scope.launch { store.like(index) } },
                            onRepost = { nav.push(Route.Detail(status.id, status.mid), origin) },
                            onComment = { nav.push(Route.Detail(status.id, status.mid), origin) },
                        )
                    }
                    if (store.items.isNotEmpty()) {
                        item {
                            if (store.loading) LoadingState()
                        }
                    }
                }
            }
            }
        }

        CompositionLocalProvider(
            LocalBackdrop provides if (blur) backdrop else null,
            LocalEdgeToEdge provides true,
        ) {
            PinnedBarDensity {
                AppBarSurface(
                    modifier = Modifier.align(Alignment.TopCenter),
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
