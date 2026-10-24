package com.cobo.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cobo.app.App
import com.cobo.app.R
import com.cobo.app.data.FeedPage
import com.cobo.app.screen.FeedFilter
import com.cobo.app.screen.FeedStore
import com.cobo.app.screen.MessagesScreen
import com.cobo.app.screen.ProfileContent
import com.cobo.app.screen.TimelineScreen
import io.github.suqi8.coui.kmp.basic.AppBarBlurOverlay
import io.github.suqi8.coui.kmp.basic.BottomFloatingAccessoryNavigationBar
import io.github.suqi8.coui.kmp.basic.BottomFloatingNavigationBarItem
import io.github.suqi8.coui.kmp.basic.DropdownImpl
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.ListPopupColumn
import io.github.suqi8.coui.kmp.basic.ListPopupDefaults
import io.github.suqi8.coui.kmp.basic.SearchBarActionButton
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.basic.couiTopAppBarActionItem
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.layerBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.basic.ExpandableIndicator
import io.github.suqi8.coui.kmp.icon.basic.Search
import io.github.suqi8.coui.kmp.icon.extended.Add
import io.github.suqi8.coui.kmp.icon.extended.ContactsCircle
import io.github.suqi8.coui.kmp.icon.extended.Home
import io.github.suqi8.coui.kmp.icon.extended.Messages
import io.github.suqi8.coui.kmp.icon.extended.Refresh
import io.github.suqi8.coui.kmp.icon.extended.Settings
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.LocalDismissState
import io.github.suqi8.coui.kmp.window.WindowListPopup
import kotlin.math.abs
import kotlinx.coroutines.launch

@Composable
fun MainShell(nav: NavState) {
    val tab = nav.mainTab
    val holder = rememberSaveableStateHolder()
    val backdrop = rememberLayerBackdrop()
    val scope = rememberCoroutineScope()
    // The bar floats over all three pages, so the line it ends at is reported once here and read
    // by whatever has to be placed clear of it.
    val topBarBounds = remember { TopBarBounds() }

    var filterIndex by remember {
        mutableIntStateOf(App.settings.feedFilter.coerceIn(0, FeedFilter.entries.lastIndex))
    }
    val filter = FeedFilter.entries[filterIndex]
    val friendsStore = remember {
        FeedStore("friends") { cursor, force -> App.repo.friends(cursor, force) }
    }
    val filterStores = remember {
        mapOf(
            FeedFilter.All to friendsStore,
            FeedFilter.Following to friendsStore,
            FeedFilter.Original to FeedStore("original") { cursor, force ->
                originalFeed(cursor, force)
            },
            FeedFilter.Unread to FeedStore("unread") { _, _ -> FeedPage(emptyList(), null) },
            FeedFilter.Favorites to FeedStore("favorites") { _, force -> App.repo.favorites(force) },
            FeedFilter.Special to FeedStore("special") { _, _ -> FeedPage(emptyList(), null) },
            FeedFilter.Circle to FeedStore("circle") { cursor, force -> App.repo.circle(cursor, force) },
            FeedFilter.Secret to FeedStore("secret") { _, _ -> FeedPage(emptyList(), null) },
            FeedFilter.Local to FeedStore("local") { _, _ -> FeedPage(emptyList(), null) },
        )
    }
    val recommendStore = remember { FeedStore("102803") }
    val circleStore = filterStores.getValue(FeedFilter.Circle)
    val followStore = filterStores.getValue(filter)

    val feedPager = rememberPagerState(
        initialPage = App.settings.feedTab.coerceIn(CirclePage, RecommendPage),
        pageCount = { RecommendPage + 1 },
    )
    LaunchedEffect(feedPager.currentPage) {
        App.settings.applyFeedTab(feedPager.currentPage)
    }

    // The top bar floats over the lists; the first row starts below it and later rows scroll under.
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // 消息 and 我 wear the feed's title row alone, so their bar is exactly one row tall.
    val topBarHeight = statusBarTop + FeedTitleRowHeight
    // The feed header is two rows: the title row folds away as the list scrolls, the segment row
    // stays pinned under the status bar.
    val feedTopBarHeight = statusBarTop + FeedTitleRowHeight + TopAppBarDefaults.SmallTopAppBarCenterHeight
    var feedCollapse by remember { mutableFloatStateOf(0f) }

    Box(Modifier.fillMaxSize()) {
        // The bar floats over the page, so the line it ends at is reported from the bar itself and
        // read by anything that has to be placed clear of it.
        CompositionLocalProvider(LocalTopBarBounds provides topBarBounds) {
            Box(
                Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop)
                    .background(pageContainerColor())
            ) {
                // The bottom bar only switches on tap, so the page carries the transition itself:
                // both tabs stay composed through the same state holder and cross-fade.
                Crossfade(
                    targetState = tab,
                    animationSpec = tween(MainTabFadeMillis),
                    modifier = Modifier.fillMaxSize(),
                    label = "mainTabPage",
                ) { page ->
                    holder.SaveableStateProvider(page) {
                        when (page) {
                            0 -> TimelineScreen(
                                nav = nav,
                                pagerState = feedPager,
                                circleStore = circleStore,
                                followStore = followStore,
                                recommendStore = recommendStore,
                                topInset = feedTopBarHeight,
                                followEmptyText = filter.emptyText,
                                onScrollOffset = { feedCollapse = it },
                            )
                            1 -> MessagesScreen(nav, topBarHeight)
                            else -> ProfileContent(
                                nav = nav,
                                uid = null,
                                modifier = Modifier.fillMaxSize(),
                                topInset = topBarHeight,
                                extraBottom = FloatingBarContentGap,
                            )
                        }
                    }
                }
            }
        }

        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            // The bar fades with its page; a hard swap would leave the old title sitting over the
            // incoming page for the length of the cross-fade.
            Crossfade(
                targetState = tab,
                animationSpec = tween(MainTabFadeMillis),
                modifier = Modifier.align(Alignment.TopCenter),
                label = "mainTabBar",
            ) { page ->
                MainTopBar(
                    tab = page,
                    nav = nav,
                    feedPager = feedPager,
                    filter = filter,
                    collapsePx = feedCollapse,
                    onFilterSelected = { selected ->
                        filterIndex = selected.ordinal
                        App.settings.applyFeedFilter(selected.ordinal)
                        scope.launch { feedPager.animateScrollToPage(FollowPage) }
                    },
                    modifier = Modifier.reportTopBarBottom(topBarBounds),
                )
            }
        }

        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            FloatingBottomBar(
                tab = tab,
                onTab = nav::setTab,
                // 刷新键跟随当前页：消息页刷消息，其余仍走微博回顶刷新。
                onRefresh = {
                    if (tab == 1) App.requestMessagesTop() else App.requestFeedTop()
                },
                onFeedTop = { App.requestFeedTop() },
                onPublish = { nav.push(Route.Publish()) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/**
 * The "原创微博" filter keeps only posts that are not reposts. Pages with nothing but reposts are
 * skipped so the first screen is not an empty state with more pages behind it.
 */
private suspend fun originalFeed(cursor: String?, force: Boolean): FeedPage {
    var next = cursor
    var first = true
    repeat(5) {
        val page = App.repo.friends(next, force && first)
        val originals = page.items.filter { it.retweeted == null }
        if (originals.isNotEmpty()) return FeedPage(originals, page.sinceId)
        if (page.items.isEmpty() || page.sinceId.isNullOrBlank()) return FeedPage(emptyList(), null)
        next = page.sinceId
        first = false
    }
    return FeedPage(emptyList(), next)
}

private val FloatingBarContentGap = 50.dp

/** Cross-fade length for a bottom-bar tab switch. The page and its bar fade together. */
private const val MainTabFadeMillis = 220

@Composable
private fun MainTopBar(
    tab: Int,
    nav: NavState,
    feedPager: PagerState,
    filter: FeedFilter,
    collapsePx: Float,
    onFilterSelected: (FeedFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (tab) {
        0 -> FeedHeader(
            feedPager = feedPager,
            filter = filter,
            collapsePx = collapsePx,
            onFilterSelected = onFilterSelected,
            onSearch = { nav.push(Route.Search) },
            modifier = modifier,
        )

        1 -> PageTitleBar(
            title = "消息",
            modifier = modifier,
            actions = {
                SearchBarActionButton(
                    text = "通知",
                    onClick = { nav.push(Route.Web("https://m.weibo.cn/message/", "通知")) },
                    modifier = Modifier.testTag("top_notice"),
                )
            },
        )

        else -> PageTitleBar(
            title = "我",
            modifier = modifier,
            actions = {
                HighlightedIconButton(
                    onClick = { nav.push(Route.Settings) },
                    modifier = Modifier.couiTopAppBarActionItem().testTag("top_settings"),
                ) {
                    Icon(COUIIcons.Settings, "设置", Modifier.size(24.dp))
                }
            },
        )
    }
}

/**
 * Single-row page header for the tabs that have no segment row. It repeats the feed's title row
 * exactly — same height, title size and action slot — so the search, 通知 and 设置 buttons all land
 * on the same pixel across the three tabs.
 */
@Composable
private fun PageTitleBar(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    AppBarBlurOverlay(
        modifier = modifier.fillMaxWidth(),
        containerColor = pageContainerColor(),
        scrollFraction = { 1f },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
        ) {
            Box(Modifier.fillMaxWidth().height(FeedTitleRowHeight)) {
                Text(
                    title,
                    color = COUITheme.colorScheme.onSurface,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 6.dp),
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = TopAppBarDefaults.ActionIconPadding),
                    horizontalArrangement = Arrangement.spacedBy(
                        TopAppBarDefaults.ActionMenuItemSpacing,
                        Alignment.End,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions,
                )
            }
        }
    }
}

private const val CirclePage = 0
private const val FollowPage = 1
private const val RecommendPage = 2

private val FeedTabLabels = listOf("好友圈", "关注", "推荐")
private val FeedTabTags = listOf("feed_tab_circle", "feed_tab_follow", "feed_tab_recommend")

/** Height of the feed header's title row; the list folds exactly this much away when it scrolls. */
internal val FeedTitleRowHeight = 44.dp

/**
 * Two-row feed header: the title sits at the top left and folds away as the list scrolls, while
 * the segment row stays pinned under the status bar.
 */
@Composable
private fun FeedHeader(
    feedPager: PagerState,
    filter: FeedFilter,
    collapsePx: Float,
    onFilterSelected: (FeedFilter) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val scheme = COUITheme.colorScheme
    val titleHeightPx = with(LocalDensity.current) { FeedTitleRowHeight.toPx() }
    val collapsed = (collapsePx / titleHeightPx).coerceIn(0f, 1f)
    var filterMenuExpanded by remember { mutableStateOf(false) }

    AppBarBlurOverlay(
        modifier = modifier.fillMaxWidth(),
        containerColor = pageContainerColor(),
        scrollFraction = { collapsed },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
        ) {
            Box(Modifier.fillMaxWidth().height(FeedTitleRowHeight * (1f - collapsed))) {
                // Only the title folds away under the status bar; clipping the whole row cut the
                // search button's halo off in a straight line.
                Box(Modifier.fillMaxSize().clipToBounds()) {
                    Text(
                        stringResource(R.string.app_name),
                        color = scheme.onSurface.copy(alpha = 1f - collapsed),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 16.dp, bottom = 6.dp),
                    )
                }
                HighlightedIconButton(
                    onClick = onSearch,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = TopAppBarDefaults.ActionIconPadding)
                        .couiTopAppBarActionItem()
                        .graphicsLayer { alpha = 1f - collapsed }
                        .testTag("top_search"),
                ) {
                    Icon(COUIIcons.Basic.Search, "搜索", Modifier.size(24.dp))
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(TopAppBarDefaults.SmallTopAppBarCenterHeight),
            ) {
                FeedTabs(
                    pager = feedPager,
                    filter = filter,
                    filterMenuExpanded = filterMenuExpanded,
                    onFilterMenuDismiss = { filterMenuExpanded = false },
                    onFilterSelected = {
                        filterMenuExpanded = false
                        onFilterSelected(it)
                    },
                    onSelect = { page -> scope.launch { feedPager.animateScrollToPage(page) } },
                    onFilterClick = { filterMenuExpanded = true },
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

// COUI's segment control opts out of LocalIndication — COUIIndication paints a rectangular state
// layer over the whole tab bounds, which reads as a square shadow — and scales the pressed
// segment instead (COUIPressFeedbackHelper).
private val FeedTabPressSpring = spring<Float>(dampingRatio = 1f, stiffness = 438.65f)

@Composable
private fun FeedTabs(
    pager: PagerState,
    filter: FeedFilter,
    filterMenuExpanded: Boolean,
    onFilterMenuDismiss: () -> Unit,
    onFilterSelected: (FeedFilter) -> Unit,
    onSelect: (Int) -> Unit,
    onFilterClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = COUITheme.colorScheme
    // ColorOS's opaque segment capsule (COUI `opaqueTabRowColors`): #353535 / #E2E3E4 container,
    // translucent white indicator over it.
    val dark = scheme.background.luminance() < 0.5f
    val containerColor = if (dark) Color(0xFF353535) else Color(0xFFE2E3E4)
    val indicatorColor = if (dark) Color(0x33FFFFFF) else Color(0xB2FFFFFF)
    val page = (pager.currentPage + pager.currentPageOffsetFraction).coerceIn(0f, 2f)
    val interactions = remember { List(FeedTabLabels.size) { MutableInteractionSource() } }
    val pressed = interactions.map { it.collectIsPressedAsState() }
    val indicatorScale by animateFloatAsState(
        targetValue = if (pressed.getOrNull(pager.currentPage)?.value == true) 0.96f else 1f,
        animationSpec = FeedTabPressSpring,
        label = "feedTabIndicatorScale",
    )
    BoxWithConstraints(
        modifier
            // Opaque 40dp capsule, the same height as the search action in the header row.
            .requiredHeight(40.dp)
            .width(200.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(containerColor),
    ) {
        val segmentWidth = (maxWidth - 8.dp) / FeedTabLabels.size
        Box(
            Modifier
                .offset(
                    x = 4.dp + segmentWidth * page,
                    y = 4.dp,
                )
                .width(segmentWidth)
                .height(32.dp)
                .graphicsLayer {
                    scaleX = indicatorScale
                    scaleY = indicatorScale
                }
                .clip(RoundedCornerShape(16.dp))
                .background(indicatorColor),
        )
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FeedTabLabels.forEachIndexed { index, label ->
                FeedTabSegment(
                    label = label,
                    index = index,
                    page = page,
                    interactionSource = interactions[index],
                    pressed = pressed[index].value,
                    modifier = Modifier.weight(1f),
                    showArrow = index == FollowPage,
                    onClick = {
                        onSelect(index)
                        if (index == FollowPage) onFilterClick()
                    },
                ) {
                    if (index == FollowPage) {
                        WindowListPopup(
                            show = filterMenuExpanded,
                            popupPositionProvider = ListPopupDefaults.DropdownPositionProvider,
                            onDismissRequest = onFilterMenuDismiss,
                        ) {
                            val dismiss = LocalDismissState.current
                            ListPopupColumn {
                                FeedFilter.entries.forEachIndexed { filterIndex, item ->
                                    DropdownImpl(
                                        item = DropdownItem(text = item.label),
                                        optionSize = FeedFilter.entries.size,
                                        isSelected = item == filter,
                                        index = filterIndex,
                                        // One divider after "全部"; the filters below stay one group.
                                        groupKey = if (filterIndex == 0) "all" else "filters",
                                        onSelectedIndexChange = {
                                            onFilterSelected(FeedFilter.entries[it])
                                            dismiss?.invoke()
                                        },
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

@Composable
private fun FeedTabSegment(
    label: String,
    index: Int,
    page: Float,
    interactionSource: MutableInteractionSource,
    pressed: Boolean,
    modifier: Modifier = Modifier,
    showArrow: Boolean,
    onClick: () -> Unit,
    popup: @Composable () -> Unit = {},
) {
    val scheme = COUITheme.colorScheme
    val amount = (1f - abs(page - index)).coerceIn(0f, 1f)
    val labelScale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = FeedTabPressSpring,
        label = "feedTabLabelScale",
    )
    Box(
        modifier = modifier
            .fillMaxHeight()
            .graphicsLayer {
                scaleX = labelScale
                scaleY = labelScale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .testTag(FeedTabTags[index]),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                label,
                fontSize = 13.sp,
                fontWeight = if (amount >= 0.5f) FontWeight.Medium else FontWeight.Normal,
                color = lerp(scheme.onSurfaceSecondary, scheme.onSurface, amount),
            )
            if (showArrow) {
                Icon(
                    COUIIcons.Basic.ExpandableIndicator,
                    contentDescription = null,
                    tint = lerp(scheme.onSurfaceSecondary, scheme.onSurface, amount),
                    modifier = Modifier.size(10.dp),
                )
            }
        }
        popup()
    }
}

@Composable
private fun FloatingBottomBar(
    tab: Int,
    onTab: (Int) -> Unit,
    onRefresh: () -> Unit,
    onFeedTop: () -> Unit,
    onPublish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The Gallery bar reads smaller than COUI's 56dp default: shrink the structural dp while
    // keeping label text at its normal physical size.
    val density = LocalDensity.current
    // 双击「微博」= 回到顶部。用时间戳判定而不是 detectTapGestures：底栏 item 内部的
    // combinedClickable 会先消费 up 事件，外层再加 pointerInput 收不到点击。
    // 单击照常切 tab，不延迟；320ms 内的第二下追加一次回到顶部。
    var lastFeedTapAt by remember { mutableLongStateOf(0L) }
    val onFeedTap: () -> Unit = {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastFeedTapAt <= 320L) {
            lastFeedTapAt = 0L
            onFeedTop()
        } else {
            lastFeedTapAt = now
        }
        onTab(0)
    }
    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        CompositionLocalProvider(
            LocalDensity provides Density(
                density = density.density * 0.86f,
                fontScale = density.fontScale / 0.86f,
            ),
        ) {
            BottomFloatingAccessoryNavigationBar(
                modifier = Modifier,
                startAccessory = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("bottom_refresh")
                            .clickable(
                                interactionSource = null,
                                indication = null,
                                role = Role.Button,
                                onClick = onRefresh,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            COUIIcons.Refresh,
                            "刷新",
                            tint = COUITheme.colorScheme.onSurface,
                        )
                    }
                },
                endAccessory = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("bottom_publish")
                            .clickable(
                                interactionSource = null,
                                indication = null,
                                role = Role.Button,
                                onClick = onPublish,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            COUIIcons.Add,
                            "发微博",
                            tint = COUITheme.colorScheme.onSurface,
                        )
                    }
                },
            ) {
                // 位置按用户要求交换：左「消息」、中「微博」、右「我」。tab 值仍沿用
                // 0=微博 / 1=消息 / 2=我，只换视觉顺序，避免影响 nav.mainTab 的持久化语义。
                BottomFloatingNavigationBarItem(
                    selected = tab == 1,
                    onClick = { onTab(1) },
                    icon = CoboIcons.MessagesOutline,
                    selectedIcon = CoboIcons.MessagesFilled,
                    label = "消息",
                    modifier = Modifier.testTag("bottom_tab_messages"),
                )
                BottomFloatingNavigationBarItem(
                    selected = tab == 0,
                    onClick = { onFeedTap() },
                    icon = CoboIcons.FeedOutline,
                    selectedIcon = CoboIcons.FeedFilled,
                    label = "微博",
                    modifier = Modifier.testTag("bottom_tab_feed"),
                )
                BottomFloatingNavigationBarItem(
                    selected = tab == 2,
                    onClick = { onTab(2) },
                    icon = CoboIcons.ProfileOutline,
                    selectedIcon = CoboIcons.ProfileFilled,
                    label = "我",
                    modifier = Modifier.testTag("bottom_tab_profile"),
                )
            }
        }
    }
}
