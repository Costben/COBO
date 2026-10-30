package com.cobo.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cobo.app.App
import com.cobo.app.R
import com.cobo.app.data.FeedPage
import com.cobo.app.data.FeedTabItem
import com.cobo.app.screen.FeedFilter
import com.cobo.app.screen.FeedStore
import com.cobo.app.screen.LocalHome
import com.cobo.app.screen.LocalMessagesPlaceholder
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
import io.github.suqi8.coui.kmp.basic.LocalEdgeToEdge
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
import io.github.suqi8.coui.kmp.overlay.OverlayListPopup
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.LocalDismissState
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

internal sealed class FeedTabSpec {
    abstract val key: String
    abstract val label: String
    abstract val tag: String
    abstract val emptyText: String
    abstract val isFollow: Boolean

    data class Custom(val item: FeedTabItem, val position: String, val guest: Boolean) : FeedTabSpec() {
        override val key: String get() = "${position}_${item.key}"
        override val label: String get() = item.label
        override val tag: String get() = item.tag
        override val isFollow: Boolean get() = false
        override val emptyText: String get() = when (item) {
            FeedTabItem.Circle -> "好友圈还没有内容"
            FeedTabItem.Recommend -> "这里还没有微博"
            FeedTabItem.Local -> if (guest) "还没有本地关注的人\n在别人主页点「本地关注」，他的微博会出现在这里" else "还没有本地关注"
            FeedTabItem.Original -> "还没有原创微博"
            FeedTabItem.Special -> "特别关注还没有内容"
            FeedTabItem.Secret -> "还没有悄悄关注的人"
            FeedTabItem.Favorites -> "还没有收藏的微博"
            FeedTabItem.None -> "这里还没有微博"
        }
    }

    data class Follow(val filter: FeedFilter, val guest: Boolean) : FeedTabSpec() {
        override val key: String get() = "follow"
        override val label: String get() = if (guest) "本地关注" else filter.label
        override val tag: String get() = "feed_tab_follow"
        override val isFollow: Boolean get() = true
        override val emptyText: String get() = if (guest) {
            "还没有本地关注的人\n在别人主页点「本地关注」，他的微博会出现在这里"
        } else {
            filter.emptyText
        }
    }
}

@Composable
fun MainShell(nav: NavState) {
    val tab = nav.mainTab
    val holder = rememberSaveableStateHolder()
    val backdrop = rememberLayerBackdrop()
    val scope = rememberCoroutineScope()
    // The bar floats over all three pages, so the line it ends at is reported once here and read
    // by whatever has to be placed clear of it.
    val topBarBounds = remember { TopBarBounds() }

    // 纯本地模式：没有账号可读关注流，首页只剩「本地关注」和「推荐」两段，筛选菜单也收起来。
    // 登录 / 退出都会 reset 导航栈，这个值在整个 MainShell 生命周期里不会变。
    val guest = remember { App.repo.guest }
    val leftTab = App.settings.feedLeftTab
    val rightTab = App.settings.feedRightTab

    var filterIndex by remember {
        mutableIntStateOf(App.settings.feedFilter.coerceIn(0, FeedFilter.entries.lastIndex))
    }
    val filter = if (guest) FeedFilter.Local else FeedFilter.entries[filterIndex]
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
            FeedFilter.Special to FeedStore("special") { cursor, force ->
                App.repo.special(cursor, force)
            },
            FeedFilter.Circle to FeedStore("circle") { cursor, force -> App.repo.circle(cursor, force) },
            FeedFilter.Secret to FeedStore("secret") { cursor, force ->
                App.repo.secret(cursor, force)
            },
            FeedFilter.Local to FeedStore("local", readOnly = true) { cursor, force ->
                App.repo.localFollows(cursor, force)
            },
        )
    }
    val recommendStore = remember { FeedStore("102803", readOnly = guest) }
    val circleStore = filterStores.getValue(FeedFilter.Circle)
    val followStore = filterStores.getValue(filter)

    fun storeFor(item: FeedTabItem): FeedStore = when (item) {
        FeedTabItem.Circle -> circleStore
        FeedTabItem.Recommend -> recommendStore
        FeedTabItem.Local -> filterStores.getValue(FeedFilter.Local)
        FeedTabItem.Original -> filterStores.getValue(FeedFilter.Original)
        FeedTabItem.Special -> filterStores.getValue(FeedFilter.Special)
        FeedTabItem.Secret -> filterStores.getValue(FeedFilter.Secret)
        FeedTabItem.Favorites -> filterStores.getValue(FeedFilter.Favorites)
        FeedTabItem.None -> friendsStore
    }

    // 动态构建主页 Tab 列表：左栏（若非隐藏） + 中间固定全部关注栏 + 右栏（若非隐藏）
    val feedTabs: List<FeedTabSpec> = remember(leftTab, rightTab, filter, guest) {
        buildList {
            if (leftTab != FeedTabItem.None) {
                add(FeedTabSpec.Custom(leftTab, "left", guest))
            }
            add(FeedTabSpec.Follow(filter, guest))
            if (rightTab != FeedTabItem.None) {
                add(FeedTabSpec.Custom(rightTab, "right", guest))
            }
        }
    }

    val feedStores = feedTabs.map { spec ->
        when (spec) {
            is FeedTabSpec.Follow -> followStore
            is FeedTabSpec.Custom -> storeFor(spec.item)
        }
    }
    val feedEmptyTexts = feedTabs.map { it.emptyText }

    val followIndex = feedTabs.indexOfFirst { it.isFollow }.coerceAtLeast(0)
    val initialPage = feedTabs.indexOfFirst { it.key == App.settings.feedTabKey }.takeIf { it >= 0 }
        ?: followIndex

    val feedPager = rememberPagerState(
        initialPage = initialPage.coerceIn(0, (feedTabs.size - 1).coerceAtLeast(0)),
        pageCount = { feedTabs.size },
    )
    LaunchedEffect(feedPager.currentPage, feedTabs) {
        val currentTab = feedTabs.getOrNull(feedPager.currentPage)
        if (currentTab != null) {
            App.settings.applyFeedTabKey(currentTab.key)
            val legacyKind = when {
                currentTab.isFollow -> 1
                currentTab is FeedTabSpec.Custom && currentTab.item == FeedTabItem.Circle -> 0
                else -> 2
            }
            App.settings.applyFeedTab(legacyKind)
        }
    }

    LaunchedEffect(feedTabs.size) {
        if (feedPager.currentPage >= feedTabs.size) {
            feedPager.scrollToPage(followIndex)
        }
    }

    val feedHasTabs = feedTabs.size > 1
    // The top bar floats over the lists; the first row starts below it and later rows scroll under.
    // 栏钉在自己的密度上，页面按系统密度排版，所以这里要按像素换算回当前密度。
    // 消息 and 我 wear the feed's title row alone, so their bar is exactly one row tall.
    val topBarHeight = pinnedHeight(FeedTitleRowHeight)
    // The feed header is two rows: the title row folds away as the list scrolls, the segment row
    // stays pinned under the status bar.
    val feedTopBarHeight = if (feedHasTabs) {
        pinnedHeight(FeedTitleRowHeight, TopAppBarDefaults.SmallTopAppBarCenterHeight)
    } else {
        topBarHeight
    }
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
                                stores = feedStores,
                                emptyTexts = feedEmptyTexts,
                                followStore = followStore,
                                recommendStore = recommendStore,
                                topInset = feedTopBarHeight,
                                onScrollOffset = { feedCollapse = it },
                            )
                            1 -> if (guest) {
                                LocalMessagesPlaceholder(nav, topBarHeight)
                            } else {
                                MessagesScreen(nav, topBarHeight)
                            }
                            else -> if (guest) LocalHome(
                                nav = nav,
                                topInset = topBarHeight,
                                extraBottom = FloatingBarContentGap,
                            ) else ProfileContent(
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

        // 顶栏材质要靠 LocalEdgeToEdge 才会画：COUI 只在内容真的铺到状态栏下面时才肯采样，
        // 否则把内容第一行抹成一条平色横带（顶栏「糊成一团」）。页面 contentPadding 把 topInset
        // 放在列表自己身上，滚动时内容确实从顶栏底下过，这里直接开。
        CompositionLocalProvider(
            LocalBackdrop provides backdrop,
            LocalEdgeToEdge provides true,
        ) {
            PinnedBarDensity {
                MainTopBar(
                    tab = tab,
                    nav = nav,
                    feedPager = feedPager,
                    feedTabs = feedTabs,
                    guest = guest,
                    filter = filter,
                    collapsePx = feedCollapse,
                    bounds = topBarBounds,
                    onFilterSelected = { selected ->
                        filterIndex = selected.ordinal
                        App.settings.applyFeedFilter(selected.ordinal)
                        // 这几条流按本机标记拼，标记是在主页改的，切过来时重拉一次才看得到。
                        if (selected == FeedFilter.Special || selected == FeedFilter.Secret ||
                            selected == FeedFilter.Local
                        ) {
                            scope.launch { filterStores[selected]?.refresh() }
                        }
                        val targetPage = feedTabs.indexOfFirst { it.isFollow }.coerceAtLeast(0)
                        scope.launch { feedPager.animateScrollToPage(targetPage) }
                    },
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
                )
            }
        }

        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            PinnedBarDensity {
                FloatingBottomBar(
                    tab = tab,
                    onTab = nav::setTab,
                    // 刷新键跟随当前页：消息页刷消息，其余仍走微博回顶刷新。
                    onRefresh = {
                        if (tab == 1) App.requestMessagesTop() else App.requestFeedTop()
                    },
                    onFeedTop = { App.requestFeedTop() },
                    onPublish = {
                        if (guest) App.toast("纯本地模式不能发微博，登录后可用") else nav.push(Route.Publish())
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
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

/** Cross-fade length for a bottom-bar tab switch. */
private const val MainTabFadeMillis = 220

/**
 * The floating bar over the three tabs.
 *
 * The material is one platform backdrop blur for the whole bar, never two. Two of them fading
 * against each other made the incoming one blur the outgoing one's veil as well: that band under
 * the title darkened to more than either tab shows at rest, then snapped back the frame the old
 * layer was dropped — the flash the tab switch used to have. So only the content cross-fades here,
 * and the material's height follows the tab instead (the feed wears an extra segment row).
 */
@Composable
private fun MainTopBar(
    tab: Int,
    nav: NavState,
    feedPager: PagerState,
    feedTabs: List<FeedTabSpec>,
    guest: Boolean,
    filter: FeedFilter,
    collapsePx: Float,
    bounds: TopBarBounds,
    onFilterSelected: (FeedFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val pageBarHeight = statusBarTop + FeedTitleRowHeight
    val feedHasTabs = feedTabs.size > 1
    val feedBarHeight = if (feedHasTabs) {
        pageBarHeight + TopAppBarDefaults.SmallTopAppBarCenterHeight
    } else {
        pageBarHeight
    }
    val titleHeightPx = with(LocalDensity.current) { FeedTitleRowHeight.toPx() }
    val collapsed = (collapsePx / titleHeightPx).coerceIn(0f, 1f)
    // 只有微博页会把标题行折进状态栏。切走时把这个折叠量收回 0，材质才不会停在上一页的位置。
    // 折叠时上移的只有材质：内容的折叠轨迹一直是按「栏顶 = 0」写的（标题往上折、分段行往下贴），
    // 原来材质和内容是一层套一层，两个等量反向的偏移互相抵消；这里内容已经移出材质节点，
    // 就不要再给它补偏移，否则折叠量会被算两遍。
    // 两个动画量都在布局/绘制阶段才读（下面的 lambda 里），否则每一帧都会把整条栏重新组合一遍。
    val feedWeight = animateFloatAsState(
        targetValue = if (tab == 0) 1f else 0f,
        animationSpec = tween(MainTabFadeMillis),
        label = "mainBarFeedWeight",
    )
    val materialShift = { (collapsed * titleHeightPx * feedWeight.value).roundToInt() }
    val materialHeight = animateDpAsState(
        targetValue = if (tab == 0) feedBarHeight else pageBarHeight,
        animationSpec = tween(MainTabFadeMillis),
        label = "mainBarMaterialHeight",
    )
    Box(modifier) {
        AppBarBlurOverlay(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, -materialShift()) }
                .reportTopBarBottom(bounds),
            containerColor = pageContainerColor(),
            scrollFraction = { 1f },
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .layout { measurable, constraints ->
                        val height = materialHeight.value.roundToPx()
                        val placeable = measurable.measure(
                            Constraints.fixed(constraints.maxWidth, height)
                        )
                        layout(placeable.width, height) { placeable.place(0, 0) }
                    }
            )
        }
        Crossfade(
            targetState = tab,
            animationSpec = tween(MainTabFadeMillis),
            label = "mainBarContent",
        ) { page ->
            BarContent(
                tab = page,
                nav = nav,
                feedPager = feedPager,
                feedTabs = feedTabs,
                guest = guest,
                filter = filter,
                collapsed = collapsed,
                onFilterSelected = onFilterSelected,
            )
        }
    }
}

@Composable
private fun BarContent(
    tab: Int,
    nav: NavState,
    feedPager: PagerState,
    feedTabs: List<FeedTabSpec>,
    guest: Boolean,
    filter: FeedFilter,
    collapsed: Float,
    onFilterSelected: (FeedFilter) -> Unit,
) {
    when (tab) {
        0 -> FeedHeader(
            feedPager = feedPager,
            feedTabs = feedTabs,
            filterMenu = !guest,
            filter = filter,
            collapsed = collapsed,
            onFilterSelected = onFilterSelected,
            onSearch = { nav.push(Route.Search) },
        )

        1 -> PageTitleBar(
            title = "消息",
            actions = {
                if (!guest) {
                    SearchBarActionButton(
                        text = "通知",
                        onClick = { nav.push(Route.Web("https://m.weibo.cn/message/", "通知")) },
                        modifier = Modifier.testTag("top_notice"),
                    )
                }
            },
        )

        else -> PageTitleBar(
            title = "我",
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
    actions: @Composable RowScope.() -> Unit = {},
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
    feedTabs: List<FeedTabSpec>,
    filterMenu: Boolean,
    filter: FeedFilter,
    collapsed: Float,
    onFilterSelected: (FeedFilter) -> Unit,
    onSearch: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val scheme = COUITheme.colorScheme
    val titleHeightPx = with(LocalDensity.current) { FeedTitleRowHeight.toPx() }
    var filterMenuExpanded by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
    ) {
        Box(Modifier.fillMaxWidth().height(FeedTitleRowHeight)) {
            // Only the title folds away under the status bar; clipping the whole row cut the
            // search button's halo off in a straight line.
            Box(Modifier.fillMaxSize().clipToBounds()) {
                Box(Modifier.fillMaxSize().graphicsLayer {
                    translationY = -collapsed * titleHeightPx
                }) {
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
            }
            HighlightedIconButton(
                onClick = onSearch,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = TopAppBarDefaults.ActionIconPadding)
                    .couiTopAppBarActionItem()
                    .graphicsLayer {
                        // 按钮居中在标题行里，行高不变时折一半才是原来那条轨迹。
                        translationY = -collapsed * titleHeightPx / 2f
                        alpha = 1f - collapsed
                    }
                    .testTag("top_search"),
            ) {
                Icon(COUIIcons.Basic.Search, "搜索", Modifier.size(24.dp))
            }
        }
        if (feedTabs.size > 1) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(TopAppBarDefaults.SmallTopAppBarCenterHeight)
                    .graphicsLayer { translationY = -collapsed * titleHeightPx },
            ) {
                FeedTabs(
                    pager = feedPager,
                    feedTabs = feedTabs,
                    filterMenu = filterMenu,
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
    feedTabs: List<FeedTabSpec>,
    filterMenu: Boolean,
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
    val page = (pager.currentPage + pager.currentPageOffsetFraction).coerceIn(
        0f,
        (feedTabs.size - 1).coerceAtLeast(0).toFloat()
    )
    val labels = feedTabs.map { it.label }
    val interactions = remember(feedTabs.size) { List(feedTabs.size) { MutableInteractionSource() } }
    val pressed = interactions.map { it.collectIsPressedAsState() }
    val indicatorScale by animateFloatAsState(
        targetValue = if (pressed.getOrNull(pager.currentPage)?.value == true) 0.96f else 1f,
        animationSpec = FeedTabPressSpring,
        label = "feedTabIndicatorScale",
    )
    // 三格同宽，按「4 个字 + 箭头」预留，字数是 2/3/4 时胶囊和位置都不动。
    // 用 sp 折算，系统字体放大时格子跟着变宽，不会被截断。
    val density = LocalDensity.current
    val segmentWidth = with(density) { (4 * 13).sp.toDp() } + 2.dp + 10.dp + 22.dp
    val containerWidth = segmentWidth * feedTabs.size + 8.dp
    BoxWithConstraints(
        modifier
            // Opaque 40dp capsule, the same height as the search action in the header row.
            .requiredHeight(40.dp)
            .width(containerWidth)
            .clip(RoundedCornerShape(20.dp))
            .background(containerColor),
    ) {
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
            feedTabs.forEachIndexed { index, spec ->
                val menu = filterMenu && spec.isFollow
                FeedTabSegment(
                    label = spec.label,
                    index = index,
                    tag = spec.tag,
                    page = page,
                    interactionSource = interactions[index],
                    pressed = pressed[index].value,
                    modifier = Modifier.weight(1f),
                    showArrow = menu,
                    onClick = {
                        onSelect(index)
                        if (menu) onFilterClick()
                    },
                ) {
                    if (menu) {
                        OverlayListPopup(
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
    tag: String,
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
            .testTag(tag),
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
                maxLines = 1,
                softWrap = false,
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
        // 尺寸不再在这里手改：整棵树已在 CoboRoot 钉到相册密度，
        // 底栏条 56dp / 项 48dp / 圆钮 56dp 由组件自己的 token 决定。
        run {
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
