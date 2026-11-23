package com.cobo.app.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cobo.app.App
import com.cobo.app.data.TopicChannel
import com.cobo.app.ui.CoboIcons
import com.cobo.app.ui.NavState
import com.cobo.app.ui.PinnedBarDensity
import com.cobo.app.ui.Route
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
import com.cobo.app.ui.layerBackdropIf
import com.cobo.app.ui.pageContainerColor
import com.cobo.app.ui.pinnedHeight
import com.cobo.app.ui.rememberExpandOrigin
import io.github.suqi8.coui.kmp.basic.BindScrollToTop
import io.github.suqi8.coui.kmp.basic.ChipTabData
import io.github.suqi8.coui.kmp.basic.ChipTabDefaults
import io.github.suqi8.coui.kmp.basic.DropdownEntry
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.FullPagePullToRefresh
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.ListPopupDefaults
import io.github.suqi8.coui.kmp.basic.LocalEdgeToEdge
import io.github.suqi8.coui.kmp.basic.PopupPositionProvider
import io.github.suqi8.coui.kmp.basic.SearchFilterTopAppBar
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.basic.TwoRowTopAppBarDefaults
import io.github.suqi8.coui.kmp.basic.fullPagePullToRefreshColors
import io.github.suqi8.coui.kmp.basic.rememberFullPagePullToRefreshState
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.All
import io.github.suqi8.coui.kmp.icon.extended.Contacts
import io.github.suqi8.coui.kmp.icon.extended.GridView
import io.github.suqi8.coui.kmp.icon.extended.Image
import io.github.suqi8.coui.kmp.icon.extended.Play
import io.github.suqi8.coui.kmp.icon.extended.Promotions
import io.github.suqi8.coui.kmp.icon.extended.Recent
import io.github.suqi8.coui.kmp.icon.extended.Refresh
import io.github.suqi8.coui.kmp.overlay.OverlayCascadingListPopup
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic
import kotlinx.coroutines.launch
import java.net.URLEncoder

@Composable
fun TopicFeedScreen(nav: NavState, topic: String) {
    val channelTabs = rememberTopicChannelTabs()
    val channels = TopicChannel.entries
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { channels.size })
    val scope = rememberCoroutineScope()
    val stores = remember(topic) {
        channels.map { channel ->
            FeedStore("topic_${channel.type}_$topic") { cursor, force ->
                App.repo.topicChannelFeed(topic, channel, cursor, force)
            }
        }
    }
    val listStates = remember(topic) { List(channels.size) { LazyListState() } }
    val backdrop = rememberLayerBackdrop()
    val blur = blurEffectsEnabled()
    val density = LocalDensity.current
    val horizontalPadding = searchHorizontalPadding()
    var filterMenuOpen by remember { mutableStateOf(false) }
    val fallbackTopHeight = pinnedHeight(
        TwoRowTopAppBarDefaults.barHeight(
            firstRowHeight = TwoRowTopAppBarDefaults.FirstRowHeight,
            secondRowHeight = TwoRowTopAppBarDefaults.SecondRowHeight,
        ),
    )
    var barHeightPx by remember { mutableIntStateOf(0) }
    val topBarHeight = if (barHeightPx > 0) {
        with(density) { barHeightPx.toDp() }
    } else {
        fallbackTopHeight
    }

    Box(Modifier.fillMaxSize().background(pageContainerColor()).testTag("topic_feed_screen")) {
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
            ) { pageIndex ->
                val channel = channels[pageIndex]
                val store = stores[pageIndex]
                val listState = listStates[pageIndex]
                BindScrollToTop(listState, enabled = pageIndex == pagerState.settledPage)
                val pullState = rememberFullPagePullToRefreshState()

                SyncDeletedStatuses(store)
                LaunchedEffect(store) {
                    if (!store.loaded) store.loadInitial()
                }
                LaunchedEffect(listState, store) {
                    snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
                        .collect { last ->
                            if (last >= store.items.size - 2 && store.items.isNotEmpty() && store.error == null) {
                                store.loadMore()
                            }
                        }
                }

                FullPagePullToRefresh(
                    isRefreshing = store.refreshing,
                    onRefresh = { scope.launch { store.refresh() } },
                    modifier = Modifier.fillMaxSize().testTag("topic_page_${channel.type}"),
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
                            item(key = "state") {
                                when {
                                    store.refreshing || (!store.loaded && store.loading) -> LoadingState()
                                    store.error != null -> ErrorState(store.error!!) {
                                        scope.launch { store.refresh() }
                                    }
                                    channel == TopicChannel.Following && App.repo.guest ->
                                        EmptyState("登录后可查看关注中的结果")
                                    else -> EmptyState("没有找到相关微博")
                                }
                            }
                        }
                        itemsIndexed(store.items, key = { _, s -> "${channel.type}_${s.id}" }) { index, status ->
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
                            item(key = "footer") {
                                when {
                                    store.loading -> LoadingState()
                                    store.end || store.sinceId == null -> Text(
                                        text = "没有更多了",
                                        color = COUITheme.colorScheme.onSurfaceSecondary,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(vertical = 16.dp),
                                    )
                                }
                            }
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
                val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                SearchFilterTopAppBar(
                    filters = channelTabs,
                    selectedFilter = pagerState.currentPage,
                    onFilterSelected = { idx ->
                        scope.launch { pagerState.animateScrollToPage(idx) }
                    },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .onSizeChanged { barHeightPx = it.height },
                    firstRowHeight = TwoRowTopAppBarDefaults.FirstRowHeight,
                    filterRowHeight = TwoRowTopAppBarDefaults.SecondRowHeight,
                    rowGap = TwoRowTopAppBarDefaults.SecondRowGap,
                    leadingIcon = COUIIcons.GridView,
                    onLeadingIconClick = { filterMenuOpen = true },
                    filterContentPadding = PaddingValues(horizontal = horizontalPadding),
                    containerColor = pageContainerColor(),
                    scrollFraction = { 1f },
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
                            text = topic,
                            color = COUITheme.colorScheme.onSurface,
                            maxLines = 1,
                            fontSize = with(LocalDensity.current) { TopAppBarDefaults.ToolbarTitleTextSize.toSp() },
                            fontWeight = FontWeight(550),
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            top = statusBarTop +
                                TwoRowTopAppBarDefaults.FirstRowHeight +
                                TwoRowTopAppBarDefaults.SecondRowGap,
                        ),
                ) {
                    TopicFilterMenu(
                        channels = channels,
                        currentChannel = channels[pagerState.currentPage],
                        expanded = filterMenuOpen,
                        onExpandedChange = { filterMenuOpen = it },
                        horizontalPadding = horizontalPadding,
                        onSelectChannel = { idx ->
                            scope.launch { pagerState.animateScrollToPage(idx) }
                        },
                        onRefresh = {
                            scope.launch { stores[pagerState.currentPage].refresh() }
                        },
                        onOpenWeb = {
                            val ch = channels[pagerState.currentPage]
                            val url = "https://m.weibo.cn/search?containerid=" +
                                URLEncoder.encode("100103type=${ch.type}&q=$topic", "UTF-8")
                            nav.push(Route.Web(url, topic))
                        },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = horizontalPadding)
                            .size(ChipTabDefaults.Height),
                    )
                }
            }
        }
    }
}

@Composable
private fun TopicFilterMenu(
    channels: List<TopicChannel>,
    currentChannel: TopicChannel,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    horizontalPadding: Dp,
    onSelectChannel: (Int) -> Unit,
    onRefresh: () -> Unit,
    onOpenWeb: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val iconTint = COUITheme.colorScheme.onSurfaceContainer
    val selectedTint = COUITheme.colorScheme.primary
    val dropdownPositionProvider = remember(horizontalPadding) {
        ListPopupDefaults.dropdownPositionProvider(horizontalMargin = horizontalPadding)
    }
    val menuEntries = remember(channels, currentChannel, hapticFeedback, iconTint, selectedTint) {
        val categoryItems = channels.mapIndexed { index, channel ->
            val selected = channel == currentChannel
            val tint = if (selected) selectedTint else iconTint
            DropdownItem(
                text = channel.label,
                selected = selected,
                icon = filterMenuItemIcon(channel.icon(), tint),
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    onSelectChannel(index)
                },
            )
        }
        val actionItems = listOf(
            DropdownItem(
                text = "刷新当前分类",
                icon = filterMenuItemIcon(COUIIcons.Refresh, iconTint),
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    onRefresh()
                },
            ),
            DropdownItem(
                text = "在网页中打开",
                icon = filterMenuItemIcon(CoboIcons.Globe, iconTint),
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    onOpenWeb()
                },
            ),
        )
        listOf(
            DropdownEntry(items = categoryItems),
            DropdownEntry(items = actionItems),
        )
    }

    Box(modifier) {
        OverlayCascadingListPopup(
            show = expanded,
            entries = menuEntries,
            onDismissRequest = { onExpandedChange(false) },
            popupPositionProvider = dropdownPositionProvider,
            alignment = PopupPositionProvider.Align.Start,
            collapseOnSelection = true,
        )
    }
}

internal fun filterMenuItemIcon(
    imageVector: ImageVector,
    tint: Color,
): @Composable (Modifier) -> Unit = { iconModifier ->
    Box(iconModifier, contentAlignment = Alignment.Center) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = tint,
        )
    }
}

private fun TopicChannel.icon(): ImageVector = when (this) {
    TopicChannel.Comprehensive -> COUIIcons.All
    TopicChannel.Latest -> COUIIcons.Recent
    TopicChannel.Pictures -> COUIIcons.Image
    TopicChannel.Videos -> COUIIcons.Play
    TopicChannel.Following -> COUIIcons.Contacts
    TopicChannel.Hot -> COUIIcons.Promotions
}

@Composable
private fun rememberTopicChannelTabs(): List<ChipTabData> = remember {
    TopicChannel.entries.map { channel ->
        ChipTabData(label = channel.label, icon = channel.icon())
    }
}
