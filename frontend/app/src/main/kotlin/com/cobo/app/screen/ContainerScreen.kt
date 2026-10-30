package com.cobo.app.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cobo.app.App
import com.cobo.app.ui.NavState
import com.cobo.app.ui.PinnedBarDensity
import com.cobo.app.ui.pageContainerColor
import com.cobo.app.ui.rememberExpandOrigin
import com.cobo.app.ui.Route
import com.cobo.app.ui.expandOrigin
import com.cobo.app.ui.components.BackIconButton
import com.cobo.app.ui.components.EmptyState
import com.cobo.app.ui.components.ErrorState
import com.cobo.app.ui.components.FullPageRefreshHoldMillis
import com.cobo.app.ui.components.FullPageRefreshTexts
import com.cobo.app.ui.components.LoadingState
import com.cobo.app.ui.components.StatusCard
import com.cobo.app.ui.components.navBottomPadding
import io.github.suqi8.coui.kmp.basic.FullPagePullToRefresh
import io.github.suqi8.coui.kmp.basic.fullPagePullToRefreshColors
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.launch

@Composable
fun ContainerScreen(nav: NavState, cid: String, title: String) {
    val scope = rememberCoroutineScope()
    val store = remember(cid) { FeedStore(cid) }
    val listState = rememberLazyListState()

    LaunchedEffect(cid) { store.loadInitial() }
    LaunchedEffect(listState, store) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { last ->
                if (last >= store.items.size - 2 && store.items.isNotEmpty()) store.loadMore()
            }
    }

    Scaffold(
        containerColor = pageContainerColor(),
        topBar = {
            PinnedBarDensity {
                SmallTopAppBar(
                    color = pageContainerColor(),
                    title = title,
                    navigationIcon = { BackIconButton { nav.pop() } },
                )
            }
        },
    ) { padding ->
        FullPagePullToRefresh(
            isRefreshing = store.refreshing,
            onRefresh = { scope.launch { store.refresh() } },
            modifier = Modifier.fillMaxSize().padding(padding),
            colors = fullPagePullToRefreshColors(backgroundColor = pageContainerColor()),
            refreshTexts = FullPageRefreshTexts,
            completeHoldDurationMillis = FullPageRefreshHoldMillis,
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 8.dp + navBottomPadding()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (store.items.isEmpty()) {
                    item {
                        when {
                            store.refreshing -> LoadingState()
                            store.error != null -> ErrorState(store.error!!) {
                                scope.launch { store.refresh() }
                            }
                            else -> EmptyState("这里还没有内容")
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
                        onImage = { urls, i -> nav.push(Route.Images(urls, i)) },
                        onVideo = { nav.push(Route.Video(it.url, it.title)) },
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
