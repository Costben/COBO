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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cobo.app.App
import com.cobo.app.data.Membership
import com.cobo.app.data.NotificationItem
import com.cobo.app.data.NotificationTab
import com.cobo.app.data.Status
import com.cobo.app.data.VerifiedKind
import com.cobo.app.data.formatWeiboTime
import com.cobo.app.ui.NavState
import com.cobo.app.ui.PinnedBarDensity
import com.cobo.app.ui.Route
import com.cobo.app.ui.blurEffectsEnabled
import com.cobo.app.ui.components.Avatar
import com.cobo.app.ui.components.BackIconButton
import com.cobo.app.ui.components.ComposerBottomSheet
import com.cobo.app.ui.components.EmptyState
import com.cobo.app.ui.components.ErrorState
import com.cobo.app.ui.components.FullPageRefreshHoldMillis
import com.cobo.app.ui.components.FullPageRefreshTexts
import com.cobo.app.ui.components.LoadingState
import com.cobo.app.ui.components.MemberBadge
import com.cobo.app.ui.components.StatusCard
import com.cobo.app.ui.components.WeiboText
import com.cobo.app.ui.components.navBottomPadding
import com.cobo.app.ui.expandOrigin
import com.cobo.app.ui.glassBarContentTop
import com.cobo.app.ui.layerBackdropIf
import com.cobo.app.ui.twoRowListScrollAlpha
import com.cobo.app.ui.pageContainerColor
import com.cobo.app.ui.pinnedHeight
import com.cobo.app.ui.rememberExpandOrigin
import io.github.suqi8.coui.kmp.basic.BindScrollToTop
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.ChipTabData
import io.github.suqi8.coui.kmp.basic.ChipTabGroup
import io.github.suqi8.coui.kmp.basic.FullPagePullToRefresh
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.LocalEdgeToEdge
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.basic.TwoRowTopAppBar
import io.github.suqi8.coui.kmp.basic.TwoRowTopAppBarDefaults
import io.github.suqi8.coui.kmp.basic.couiTopAppBarActionItem
import io.github.suqi8.coui.kmp.basic.fullPagePullToRefreshColors
import io.github.suqi8.coui.kmp.basic.rememberFullPagePullToRefreshState
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Refresh
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Stable
private class NotificationTabState(private val tab: NotificationTab) {
    val statuses = mutableStateListOf<Status>()
    val items = mutableStateListOf<NotificationItem>()
    var nextCursor by mutableStateOf<String?>(null)
    var loaded by mutableStateOf(false)
    var loading by mutableStateOf(false)
    var refreshing by mutableStateOf(false)
    var end by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var moreError by mutableStateOf<String?>(null)

    private val seenCursors = HashSet<String>()

    suspend fun loadInitial() {
        if (loaded || loading) return
        load(reset = true, showRefresh = false)
    }

    suspend fun refresh() {
        load(reset = true, showRefresh = true)
    }

    suspend fun loadMore() {
        if (loading || end) return
        val cursor = nextCursor ?: return
        if (!seenCursors.add(cursor)) {
            end = true
            return
        }
        load(reset = false, showRefresh = false, cursor = cursor)
    }

    suspend fun toggleStatusLike(index: Int) {
        val current = statuses.getOrNull(index) ?: return
        val updated = App.toggleStatusLike(current) ?: return
        val realIdx = statuses.indexOfFirst { it.id == current.id }
        if (realIdx >= 0) {
            statuses[realIdx] = updated
        }
    }

    private suspend fun load(reset: Boolean, showRefresh: Boolean, cursor: String? = null) {
        if (loading) return
        loading = true
        if (reset && showRefresh) refreshing = true
        try {
            val page = App.repo.notifications(tab, cursor)
            if (reset) {
                statuses.clear()
                items.clear()
                seenCursors.clear()
                error = null
                end = false
            }
            moreError = null
            if (tab == NotificationTab.AtWeibo) {
                val known = statuses.mapTo(HashSet()) { it.id }
                page.statuses.forEach { if (known.add(it.id)) statuses.add(it) }
            } else {
                val known = items.mapTo(HashSet()) { it.id }
                page.notifications.forEach { if (known.add(it.id)) items.add(it) }
            }
            nextCursor = page.nextCursor
            if (page.nextCursor == null || (page.statuses.isEmpty() && page.notifications.isEmpty())) {
                end = true
            }
            loaded = true
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            val message = t.message ?: "加载失败"
            val emptyNow = if (tab == NotificationTab.AtWeibo) statuses.isEmpty() else items.isEmpty()
            if (emptyNow) error = message else moreError = message
        } finally {
            loading = false
            refreshing = false
        }
    }
}

@Composable
fun NotificationsScreen(
    nav: NavState,
    initialTab: NotificationTab = NotificationTab.AtWeibo,
) {
    val scope = rememberCoroutineScope()
    val tabs = remember { NotificationTab.entries }
    val chipTabs = remember(tabs) { tabs.map { ChipTabData(label = it.label) } }
    val initialPage = remember(initialTab, tabs) { tabs.indexOf(initialTab).coerceAtLeast(0) }
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { tabs.size })
    val states = remember { mutableStateMapOf<NotificationTab, NotificationTabState>() }
    val listStates = remember { mutableStateMapOf<NotificationTab, LazyListState>() }
    val backdrop = rememberLayerBackdrop()
    val blur = blurEffectsEnabled()
    val horizontalPadding = searchHorizontalPadding()
    var replyTarget by remember { mutableStateOf<NotificationItem?>(null) }

    fun stateFor(tab: NotificationTab): NotificationTabState =
        states.getOrPut(tab) { NotificationTabState(tab) }

    fun listStateFor(tab: NotificationTab): LazyListState =
        listStates.getOrPut(tab) { LazyListState() }

    val topBarHeight = pinnedHeight(
        TwoRowTopAppBarDefaults.barHeight(
            firstRowHeight = TwoRowTopAppBarDefaults.FirstRowHeight,
            secondRowHeight = TwoRowTopAppBarDefaults.SecondRowHeight,
        ),
    )

    Box(Modifier.fillMaxSize().background(pageContainerColor()).testTag("notifications_page")) {
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
                    val tab = tabs[pageIndex]
                    val state = stateFor(tab)
                    val listState = listStateFor(tab)
                    BindScrollToTop(listState, enabled = pageIndex == pagerState.settledPage)
                    val pullState = rememberFullPagePullToRefreshState()

                    SyncLikedStatuses(state.statuses)
                    LaunchedEffect(state) {
                        state.loadInitial()
                    }
                    LaunchedEffect(listState, state.statuses.size, state.items.size) {
                        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
                            .collect { last ->
                                val count = if (tab == NotificationTab.AtWeibo) state.statuses.size else state.items.size
                                if (count > 0 && last >= count - 2 && !state.end && state.moreError == null) {
                                    state.loadMore()
                                }
                            }
                    }

                    FullPagePullToRefresh(
                        isRefreshing = state.refreshing,
                        onRefresh = { scope.launch { state.refresh() } },
                        modifier = Modifier.fillMaxSize().testTag("notifications_tab_${tab.name}"),
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
                                top = glassBarContentTop(topBarHeight, 8.dp),
                                end = 12.dp,
                                bottom = 16.dp + navBottomPadding(),
                            ),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            val isEmpty = if (tab == NotificationTab.AtWeibo) {
                                state.statuses.isEmpty()
                            } else {
                                state.items.isEmpty()
                            }
                            if (isEmpty) {
                                item(key = "state") {
                                    when {
                                        state.refreshing || (!state.loaded && state.error == null) -> LoadingState()
                                        state.error != null -> ErrorState(state.error!!) {
                                            scope.launch { state.refresh() }
                                        }
                                        else -> EmptyState("暂时没有${tab.label}消息")
                                    }
                                }
                            }
                            if (tab == NotificationTab.AtWeibo) {
                                itemsIndexed(state.statuses, key = { _, status -> status.id }) { index, status ->
                                    val origin = rememberExpandOrigin()
                                    StatusCard(
                                        status = status,
                                        modifier = Modifier.expandOrigin(origin),
                                        onOpen = { nav.push(Route.Detail(status.id, status.mid), origin) },
                                        onOpenQuoted = { quoted, quotedOrigin ->
                                            nav.push(Route.Detail(quoted.id, quoted.mid), quotedOrigin)
                                        },
                                        onUser = { if (it.id.isNotBlank()) nav.push(Route.Profile(it.id)) },
                                        onImage = { pics, idx -> nav.push(Route.Images(pics, idx)) },
                                        onVideo = { nav.push(Route.Video(it.url, it.title, it.isLive)) },
                                        onLike = { scope.launch { state.toggleStatusLike(index) } },
                                        onRepost = { nav.push(Route.Detail(status.id, status.mid), origin) },
                                        onComment = { nav.push(Route.Detail(status.id, status.mid), origin) },
                                    )
                                }
                            } else {
                                val canReply = tab == NotificationTab.AtComment || tab == NotificationTab.Comment
                                itemsIndexed(state.items, key = { index, item -> "${item.id}#$index" }) { _, item ->
                                    NotificationRow(
                                        item = item,
                                        canReply = canReply &&
                                            item.targetStatus?.id?.isNotBlank() == true &&
                                            item.commentId.isNotBlank(),
                                        onUser = { uid -> if (uid.isNotBlank()) nav.push(Route.Profile(uid)) },
                                        onOpenStatus = { status ->
                                            if (status.id.isNotBlank() && status.id != "0") {
                                                nav.push(Route.Detail(status.id, status.mid.ifBlank { status.id }))
                                            } else {
                                                App.toast("原微博不可查看")
                                            }
                                        },
                                        onReply = { replyTarget = item },
                                    )
                                }
                            }
                            if (!isEmpty) {
                                item(key = "footer") {
                                    when {
                                        state.loading && !state.refreshing -> LoadingState()
                                        state.moreError != null -> TextButton(
                                            text = "加载失败，点此重试",
                                            onClick = { scope.launch { state.loadMore() } },
                                        )
                                        state.end -> Text(
                                            "没有更多了",
                                            fontSize = 12.sp,
                                            color = COUITheme.colorScheme.onSurfaceSecondary,
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
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
                val barDensity = LocalDensity.current
                val selectedIdx = pagerState.currentPage.coerceIn(0, tabs.lastIndex)
                val currentTab = tabs[selectedIdx]
                TwoRowTopAppBar(
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
                    firstRowHeight = TwoRowTopAppBarDefaults.FirstRowHeight,
                    secondRowHeight = TwoRowTopAppBarDefaults.SecondRowHeight,
                    containerColor = pageContainerColor(),
                    scrollFraction = {
                        val activeTab = tabs[pagerState.currentPage.coerceIn(0, tabs.lastIndex)]
                        twoRowListScrollAlpha(listStateFor(activeTab), barDensity)
                    },
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
                            text = "通知",
                            color = COUITheme.colorScheme.onSurface,
                            maxLines = 1,
                            fontSize = with(LocalDensity.current) { TopAppBarDefaults.ToolbarTitleTextSize.toSp() },
                            fontWeight = FontWeight(550),
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false,
                            modifier = Modifier.weight(1f),
                        )
                        HighlightedIconButton(
                            onClick = { scope.launch { stateFor(currentTab).refresh() } },
                            modifier = Modifier.couiTopAppBarActionItem().testTag("notifications_refresh"),
                        ) {
                            Icon(COUIIcons.Refresh, "刷新", Modifier.size(22.dp))
                        }
                    }
                }
            }
        }
    }

    replyTarget?.let { target ->
        val status = target.targetStatus
        if (status != null && target.commentId.isNotBlank()) {
            ComposerBottomSheet(
                title = "回复 @${target.user?.name.orEmpty()}",
                hint = "写回复",
                submitText = "回复",
                maxImages = 1,
                onDismiss = { replyTarget = null },
                onSubmit = { text, images, _ ->
                    try {
                        val picIds = images.map { App.repo.uploadImage(it) }
                        App.repo.replyComment(
                            id = status.id,
                            mid = status.mid.ifBlank { status.id },
                            cid = target.commentId,
                            text = text,
                            picIds = picIds,
                        )
                        App.toast("回复成功")
                        true
                    } catch (e: CancellationException) {
                        throw e
                    } catch (t: Throwable) {
                        App.toast(t.message ?: "回复失败")
                        false
                    }
                },
            )
        }
    }
}

@Composable
private fun NotificationRow(
    item: NotificationItem,
    canReply: Boolean,
    onUser: (String) -> Unit,
    onOpenStatus: (Status) -> Unit,
    onReply: () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    val user = item.user
    val target = item.targetStatus
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = CardDefaults.GroupCornerRadius,
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        weight = CardDefaults.GroupWeight,
        onClick = {
            if (target != null) {
                onOpenStatus(target)
            } else if (!user?.id.isNullOrBlank()) {
                onUser(user!!.id)
            }
        },
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Avatar(
                url = user?.avatar,
                size = 38.dp,
                verifiedKind = user?.verifiedKind ?: VerifiedKind.None,
                modifier = Modifier.clickable(enabled = !user?.id.isNullOrBlank()) {
                    onUser(user?.id.orEmpty())
                },
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = user?.name.orEmpty().ifBlank { "微博用户" },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    val membership = user?.membership ?: Membership.None
                    if (membership != Membership.None) {
                        Spacer(Modifier.width(4.dp))
                        MemberBadge(membership, compact = true, rank = user?.mbrank ?: 0)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = formatWeiboTime(item.createdAt),
                        fontSize = 11.sp,
                        color = scheme.onSurfaceSecondary,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = item.actionLabel,
                    fontSize = 12.sp,
                    color = scheme.onSurfaceSecondary,
                )
                if (item.textHtml.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    WeiboText(
                        html = item.textHtml,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                }
                if (item.replyTextHtml.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(pageContainerColor())
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        val prefix = if (item.replyUserName.isNotBlank()) {
                            "@${item.replyUserName}："
                        } else {
                            "我的评论："
                        }
                        Text(
                            text = prefix,
                            fontSize = 12.sp,
                            color = scheme.primary,
                        )
                        Spacer(Modifier.height(2.dp))
                        WeiboText(
                            html = item.replyTextHtml,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            maxLines = 3,
                        )
                    }
                }
                if (target != null) {
                    Spacer(Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(pageContainerColor())
                            .clickable { onOpenStatus(target) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        val owner = target.user?.name.orEmpty()
                        if (owner.isNotBlank()) {
                            Text(
                                text = "@$owner",
                                fontSize = 12.sp,
                                color = scheme.primary,
                            )
                            Spacer(Modifier.height(2.dp))
                        }
                        if (target.textHtml.isNotBlank()) {
                            WeiboText(
                                html = target.textHtml,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                maxLines = 3,
                            )
                        }
                    }
                }
                if (canReply) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Text(
                            text = "回复",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = scheme.onSurface,
                            modifier = Modifier
                                .clip(RoundedCornerShape(11.dp))
                                .background(
                                    scheme.onSurface.copy(
                                        alpha = if (scheme.background.luminance() < 0.5f) 0.16f else 0.08f,
                                    ),
                                )
                                .clickable(onClick = onReply)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
