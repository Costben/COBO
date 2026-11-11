package com.cobo.app.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cobo.app.App
import com.cobo.app.data.ApiException
import com.cobo.app.data.FeedPage
import com.cobo.app.data.SuperTopicInfo
import com.cobo.app.data.SuperTopicPage
import com.cobo.app.ui.CoboIcons
import com.cobo.app.ui.NavState
import com.cobo.app.ui.PinnedBarDensity
import com.cobo.app.ui.Route
import com.cobo.app.ui.blurEffectsEnabled
import com.cobo.app.ui.expandOrigin
import com.cobo.app.ui.glassBarContentTop
import com.cobo.app.ui.layerBackdropIf
import com.cobo.app.ui.pageContainerColor
import com.cobo.app.ui.pinnedHeight
import com.cobo.app.ui.rememberExpandOrigin
import com.cobo.app.ui.components.Avatar
import com.cobo.app.ui.components.BackIconButton
import com.cobo.app.ui.components.EmptyState
import com.cobo.app.ui.components.ErrorState
import com.cobo.app.ui.components.FullPageRefreshHoldMillis
import com.cobo.app.ui.components.FullPageRefreshTexts
import com.cobo.app.ui.components.LoadingState
import com.cobo.app.ui.components.StatusCard
import com.cobo.app.ui.components.navBottomPadding
import io.github.suqi8.coui.kmp.basic.FullPagePullToRefresh
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.LocalEdgeToEdge
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.basic.couiTopAppBarActionItem
import io.github.suqi8.coui.kmp.basic.fullPagePullToRefreshColors
import io.github.suqi8.coui.kmp.basic.rememberFullPagePullToRefreshState
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.launch

/** Keeps the existing feed's request serialization, refresh and duplicate filtering. */
class SuperTopicStore(
    containerId: String,
    loader: suspend (String?) -> SuperTopicPage,
) {
    var info by mutableStateOf<SuperTopicInfo?>(null)
        private set
    var lastRequestWasRefresh = true
        private set
    private val cursors = HashSet<String>()
    val feed = FeedStore(containerId, readOnly = true) { cursor, _ ->
        lastRequestWasRefresh = cursor == null
        val page = loader(cursor)
        if (cursor == null) cursors.clear() else cursors.add(cursor)
        if (page.nextCursor != null && page.nextCursor in cursors) {
            throw ApiException("超话分页游标重复，请刷新或网页查看")
        }
        info = page.info
        FeedPage(page.items, page.nextCursor)
    }
}

@Composable
fun SuperTopicScreen(nav: NavState, containerId: String, fallbackUrl: String) {
    val scope = rememberCoroutineScope()
    val store = remember(containerId) {
        SuperTopicStore(containerId) { cursor -> App.repo.superTopic(containerId, cursor) }
    }
    val feed = store.feed
    val listState = rememberLazyListState()
    val pullState = rememberFullPagePullToRefreshState()
    val backdrop = rememberLayerBackdrop()
    val topBarHeight = pinnedHeight(TopAppBarDefaults.SmallTopAppBarCenterHeight)
    val title = store.info?.title ?: "超话"

    LaunchedEffect(store) { feed.loadInitial() }
    LaunchedEffect(listState, feed, feed.items.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { last ->
                if (feed.items.isNotEmpty() && last >= feed.items.size - 1 && feed.error == null) {
                    feed.loadMore()
                }
            }
    }
    LaunchedEffect(feed.generation) {
        if (feed.loaded) listState.scrollToItem(0)
    }

    Box(Modifier.fillMaxSize().background(pageContainerColor())) {
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdropIf(blurEffectsEnabled(), backdrop)
                .background(pageContainerColor())
        ) {
            FullPagePullToRefresh(
                isRefreshing = feed.refreshing,
                onRefresh = { scope.launch { feed.refresh() } },
                modifier = Modifier.fillMaxSize().testTag("supertopic_page"),
                state = pullState,
                contentPadding = PaddingValues(top = topBarHeight),
                colors = fullPagePullToRefreshColors(backgroundColor = pageContainerColor()),
                refreshTexts = FullPageRefreshTexts,
                completeHoldDurationMillis = FullPageRefreshHoldMillis,
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        top = glassBarContentTop(topBarHeight, 8.dp),
                        end = 12.dp,
                        bottom = 8.dp + navBottomPadding(),
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "header") {
                        Column(Modifier.padding(8.dp)) {
                            store.info?.let { info ->
                                Row {
                                    Avatar(info.avatar, 56.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(info.title, fontSize = 20.sp)
                                        Spacer(Modifier.height(4.dp))
                                        val counts = listOf(
                                            info.readCount.takeIf { it.isNotBlank() }?.let { "阅读 $it" },
                                            info.postCount.takeIf { it.isNotBlank() }?.let { "帖子 $it" },
                                            info.fanCount.takeIf { it.isNotBlank() }?.let { "粉丝 $it" },
                                        ).filterNotNull().joinToString(" · ")
                                        if (counts.isNotBlank()) Text(counts, fontSize = 12.sp)
                                    }
                                }
                                if (info.description.isNotBlank()) {
                                    Spacer(Modifier.height(10.dp))
                                    Text(info.description, fontSize = 14.sp)
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                            Text(
                                "赞、评、转暂未验证，当前仅浏览",
                                fontSize = 12.sp,
                                color = COUITheme.colorScheme.onSurfaceSecondary,
                            )
                            if (feed.error?.contains("登录") == true) {
                                TextButton(text = "前往登录", onClick = { nav.push(Route.Login()) })
                            }
                        }
                    }
                    if (feed.items.isEmpty()) {
                        item(key = "state") {
                            when {
                                feed.refreshing || (!feed.loaded && feed.error == null) -> LoadingState()
                                feed.error != null -> ErrorState(feed.error!!) {
                                    scope.launch { feed.refresh() }
                                }
                                else -> EmptyState("这个超话暂时没有帖子")
                            }
                        }
                    }
                    items(feed.items, key = { "status-${it.id}" }) { status ->
                        val origin = rememberExpandOrigin()
                        StatusCard(
                            status = status,
                            modifier = Modifier.expandOrigin(origin),
                            actionsEnabled = false,
                            onOpen = { nav.push(Route.Detail(status.id, status.mid, readOnly = true), origin) },
                            onOpenQuoted = { quoted, quotedOrigin ->
                                nav.push(Route.Detail(quoted.id, quoted.mid, readOnly = true), quotedOrigin)
                            },
                            onUser = { nav.push(Route.Profile(it.id)) },
                            onImage = { pics, index -> nav.push(Route.Images(pics, index)) },
                            onVideo = { nav.push(Route.Video(it.url, it.title)) },
                            onLike = {},
                        )
                    }
                    if (feed.items.isNotEmpty()) {
                        item(key = "pagination") {
                            when {
                                feed.loading || feed.refreshing -> LoadingState()
                                feed.error != null -> ErrorState(feed.error!!) {
                                    scope.launch {
                                        if (store.lastRequestWasRefresh) feed.refresh() else feed.loadMore()
                                    }
                                }
                                feed.end || feed.sinceId == null -> EmptyState("已到最后一页")
                                else -> TextButton(
                                    text = "加载更多",
                                    onClick = { scope.launch { feed.loadMore() } },
                                )
                            }
                        }
                    }
                }
            }
        }

        CompositionLocalProvider(
            LocalBackdrop provides if (blurEffectsEnabled()) backdrop else null,
            LocalEdgeToEdge provides true,
        ) {
            PinnedBarDensity {
                SmallTopAppBar(
                    title = title,
                    modifier = Modifier.align(Alignment.TopCenter),
                    color = pageContainerColor(),
                    showDivider = false,
                    gradientGlass = blurEffectsEnabled(),
                    navigationIcon = { BackIconButton { nav.pop() } },
                    actions = {
                        HighlightedIconButton(
                            onClick = { nav.push(Route.Web(fallbackUrl, title)) },
                            modifier = Modifier.couiTopAppBarActionItem().testTag("supertopic_web"),
                        ) {
                            Icon(CoboIcons.Globe, "网页查看", Modifier.size(24.dp))
                        }
                    },
                )
            }
        }
    }
}
