package com.cobo.app.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cobo.app.App
import com.cobo.app.R
import com.cobo.app.data.FeedPage
import com.cobo.app.data.FeedTabItem
import com.cobo.app.data.FeedTabSelection
import com.cobo.app.data.FollowGroup
import com.cobo.app.data.SearchPanelAnimation
import com.cobo.app.data.feedTabOptions
import com.cobo.app.data.nearbyCoords
import com.cobo.app.screen.BindSearchSuggestionsEffect
import com.cobo.app.screen.FeedFilter
import com.cobo.app.screen.FeedStore
import com.cobo.app.screen.LocalHome
import com.cobo.app.screen.LocalMessagesPlaceholder
import com.cobo.app.screen.MessagesScreen
import com.cobo.app.screen.ProfileContent
import com.cobo.app.screen.SearchDiscoverState
import com.cobo.app.screen.SearchFilterMenu
import com.cobo.app.screen.SearchResultsState
import com.cobo.app.screen.SearchTab
import com.cobo.app.screen.TimelineScreen
import com.cobo.app.screen.dismissHistoryEditingOnTap
import com.cobo.app.screen.rememberSearchDiscoverState
import com.cobo.app.screen.rememberSearchFilterTabs
import com.cobo.app.screen.rememberSearchResultsState
import com.cobo.app.screen.searchDiscoverItems
import com.cobo.app.screen.searchHorizontalPadding
import com.cobo.app.screen.searchResultsItems
import com.cobo.app.ui.components.navBottomPadding
import io.github.suqi8.coui.kmp.anim.COUIInEase
import io.github.suqi8.coui.kmp.anim.COUIOutEase
import io.github.suqi8.coui.kmp.basic.BottomFloatingAccessoryNavigationBar
import io.github.suqi8.coui.kmp.basic.BottomFloatingNavigationBarItem
import io.github.suqi8.coui.kmp.basic.ChipTabDefaults
import io.github.suqi8.coui.kmp.basic.DropdownEntry
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.ListPopupDefaults
import io.github.suqi8.coui.kmp.basic.LocalEdgeToEdge
import io.github.suqi8.coui.kmp.basic.SearchBarActionButton
import io.github.suqi8.coui.kmp.basic.SearchFilterTopAppBar
import io.github.suqi8.coui.kmp.basic.TabRowDefaults
import io.github.suqi8.coui.kmp.basic.TabRowWithContour
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.basic.TopAppBarSearchBar
import io.github.suqi8.coui.kmp.basic.TwoRowTopAppBarDefaults
import io.github.suqi8.coui.kmp.basic.couiTopAppBarActionItem
import io.github.suqi8.coui.kmp.blur.LayerBackdrop
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.isBlurEnabled
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.basic.ExpandableIndicator
import io.github.suqi8.coui.kmp.icon.extended.Add
import io.github.suqi8.coui.kmp.icon.extended.GridView
import io.github.suqi8.coui.kmp.icon.extended.Messages
import io.github.suqi8.coui.kmp.icon.extended.Refresh
import io.github.suqi8.coui.kmp.icon.extended.Settings
import io.github.suqi8.coui.kmp.overlay.OverlayCascadingListPopup
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.rememberGalleryDensity
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

internal sealed class FeedTabSpec {
    abstract val key: String
    abstract val label: String
    abstract val tag: String
    abstract val emptyText: String
    abstract val isFollow: Boolean

    /** 只有中间栏带「更多」菜单：它列的就是「微博中间栏内容」那份选项。 */
    abstract val isCenter: Boolean

    data class Custom(val selection: FeedTabSelection, val position: String, val guest: Boolean) : FeedTabSpec() {
        override val key: String get() = "${position}_${selection.key}"
        override val label: String get() = selection.label
        override val tag: String get() = if (selection.isGroup) {
            "feed_tab_group_${selection.group?.gid.orEmpty()}"
        } else {
            selection.item.tag
        }
        override val isFollow: Boolean get() = false
        override val isCenter: Boolean get() = position == "center"
        override val emptyText: String get() = when {
            selection.isGroup -> "「${selection.label}」还没有内容"
            else -> when (selection.item) {
                FeedTabItem.Follow -> if (guest) "还没有本地关注的人\n在别人主页点「本地关注」，他的微博会出现在这里" else "还没有关注的人"
                FeedTabItem.Circle -> "好友圈还没有内容"
                FeedTabItem.Recommend -> "这里还没有微博"
                FeedTabItem.Local -> if (guest) "还没有本地关注的人\n在别人主页点「本地关注」，他的微博会出现在这里" else "还没有本地关注"
                FeedTabItem.Original -> "还没有原创微博"
                FeedTabItem.Special -> "特别关注还没有内容"
                FeedTabItem.Secret -> "还没有悄悄关注的人"
                FeedTabItem.Favorites -> "还没有收藏的微博"
                FeedTabItem.Nearby -> "附近需要定位\n到设置的「附近」里打开定位，或选一座城市"
                FeedTabItem.Group -> "在顶栏的「更多」里选一个关注分组"
                FeedTabItem.None -> "这里还没有微博"
            }
        }
    }

    data class Follow(val filter: FeedFilter, val guest: Boolean, val position: String = "center") : FeedTabSpec() {
        override val key: String get() = if (position == "center") "follow" else "${position}_follow"
        override val label: String get() = if (guest) "本地关注" else filter.label
        override val tag: String get() = "feed_tab_follow"
        override val isFollow: Boolean get() = true
        override val isCenter: Boolean get() = position == "center"
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
    val searchBackdrop = rememberLayerBackdrop()
    val blur = blurEffectsEnabled()
    val scope = rememberCoroutineScope()
    // The bar floats over all three pages, so the line it ends at is reported once here and read
    // by whatever has to be placed clear of it.
    val topBarBounds = remember { TopBarBounds() }

    // 纯本地模式：没有账号可读关注流，首页只剩「本地关注」和「推荐」两段，筛选菜单也收起来。
    // 登录 / 退出都会 reset 导航栈，这个值在整个 MainShell 生命周期里不会变。
    val guest = remember { App.repo.guest }
    val leftTab = App.settings.feedLeftTab
    val centerTab = App.settings.feedCenterTab
    val rightTab = App.settings.feedRightTab

    // 中间栏看哪条流由「中间栏内容」决定，菜单只是把这一项搬到页面上。原来的「筛选」只剩
    // 一份好友流，显示时统一叫「全部」；纯本地模式没有好友流，仍按「本地关注」走。
    val filter = if (guest) FeedFilter.Local else FeedFilter.All
    val friendsStore = remember {
        FeedStore("friends", resumable = true) { cursor, force -> App.repo.friends(cursor, force) }
    }
    val filterStores = remember {
        mapOf(
            FeedFilter.All to friendsStore,
            FeedFilter.Following to friendsStore,
            FeedFilter.Original to FeedStore("original", resumable = true) { cursor, force ->
                originalFeed(cursor, force)
            },
            FeedFilter.Unread to FeedStore("unread") { _, _ -> FeedPage(emptyList(), null) },
            FeedFilter.Favorites to FeedStore("favorites", resumable = true) { _, force ->
                App.repo.favorites(force)
            },
            FeedFilter.Special to FeedStore("special", resumable = true) { cursor, force ->
                App.repo.special(cursor, force)
            },
            FeedFilter.Circle to FeedStore("circle", resumable = true) { cursor, force ->
                App.repo.circle(cursor, force)
            },
            FeedFilter.Secret to FeedStore("secret", resumable = true) { cursor, force ->
                App.repo.secret(cursor, force)
            },
            FeedFilter.Local to FeedStore("local", readOnly = true, resumable = true) { cursor, force ->
                App.repo.localFollows(cursor, force)
            },
        )
    }
    val recommendStore = remember { FeedStore("102803", readOnly = guest, resumable = true) }
    val circleStore = filterStores.getValue(FeedFilter.Circle)
    val followStore = filterStores.getValue(filter)

    // 关注分组：分组是账号里的数据，gid 不同就是不同的一条流，一组一个 store。
    val groupStores = remember { HashMap<String, FeedStore>() }

    // 附近：坐标来自设备定位或用户选的城市，换位置之后这条流要整条重来。
    val nearbyStore = remember {
        FeedStore("nearby", resumable = true) { cursor, force ->
            val coords = nearbyCoords(App.context)
            if (coords == null) {
                FeedPage(emptyList(), null)
            } else {
                App.repo.nearby(coords.first.toString(), coords.second.toString(), cursor, force)
            }
        }
    }
    val nearbyLocationKey = "${App.settings.nearbyCustom}:${App.settings.nearbyPlaceName}"
    var lastNearbyLocationKey by remember { mutableStateOf(nearbyLocationKey) }
    LaunchedEffect(nearbyLocationKey) {
        if (nearbyLocationKey != lastNearbyLocationKey) {
            lastNearbyLocationKey = nearbyLocationKey
            nearbyStore.refresh()
        }
    }

    fun storeFor(selection: FeedTabSelection): FeedStore = when (selection.item) {
        FeedTabItem.Follow -> followStore
        FeedTabItem.Circle -> circleStore
        FeedTabItem.Recommend -> recommendStore
        FeedTabItem.Local -> filterStores.getValue(FeedFilter.Local)
        FeedTabItem.Original -> filterStores.getValue(FeedFilter.Original)
        FeedTabItem.Special -> filterStores.getValue(FeedFilter.Special)
        FeedTabItem.Secret -> filterStores.getValue(FeedFilter.Secret)
        FeedTabItem.Favorites -> filterStores.getValue(FeedFilter.Favorites)
        FeedTabItem.Nearby -> nearbyStore
        FeedTabItem.Group -> selection.group?.let { group ->
            groupStores.getOrPut(group.gid) {
                FeedStore("group_${group.gid}", resumable = true) { cursor, _ ->
                    App.repo.groupFeed(group.gid, cursor)
                }
            }
        } ?: friendsStore
        FeedTabItem.None -> friendsStore
    }

    // 动态构建主页 Tab 列表：左栏（若非隐藏） + 中间栏（不可隐藏） + 右栏（若非隐藏）
    val feedTabs: List<FeedTabSpec> = remember(leftTab, centerTab, rightTab, filter, guest) {
        buildList {
            // 关注分组是账号里的数据：纯本地模式读不到，这一栏退回「本地关注」。
            fun addColumn(selection: FeedTabSelection, position: String) {
                val effective = if (guest && selection.isGroup) FeedTabSelection(FeedTabItem.Follow) else selection
                when (effective.item) {
                    FeedTabItem.None -> Unit
                    FeedTabItem.Follow -> add(FeedTabSpec.Follow(filter, guest, position))
                    else -> add(FeedTabSpec.Custom(effective, position, guest))
                }
            }
            addColumn(leftTab, "left")
            addColumn(centerTab, "center")
            addColumn(rightTab, "right")
        }
    }

    val feedStores = feedTabs.map { spec ->
        when (spec) {
            is FeedTabSpec.Follow -> followStore
            is FeedTabSpec.Custom -> storeFor(spec.selection)
        }
    }
    val feedEmptyTexts = feedTabs.map { it.emptyText }

    // 「更多」菜单里的关注分组。分组列表在网页版，只在打开菜单时取一次（Repo 里还有一层缓存）；
    // 拉不到就只显示内置流，不影响其它栏。
    var feedGroups by remember { mutableStateOf<List<FollowGroup>>(emptyList()) }
    val loadFeedGroups: () -> Unit = {
        if (!guest) {
            scope.launch {
                runCatching { App.repo.followGroups() }
                    .onSuccess { feedGroups = it }
            }
        }
    }
    LaunchedEffect(guest) { loadFeedGroups() }
    val centerOptions = feedTabOptions(feedGroups, allowNone = false, current = centerTab)

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
                currentTab is FeedTabSpec.Custom && currentTab.selection.item == FeedTabItem.Circle -> 0
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
    val galleryDensity = rememberGalleryDensity()
    val barTitleHeightPx = with(galleryDensity) { FeedTitleRowHeight.toPx() }
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
    var feedSearchQuery by remember { mutableStateOf("") }
    var feedSearchExpanded by remember { mutableStateOf(false) }
    val panelAnimation = App.settings.searchPanelAnimation
    val panelProgress = remember { Animatable(0f) }
    val panelAlpha = remember { Animatable(0f) }
    val panelMounted by remember {
        derivedStateOf {
            feedSearchExpanded || panelProgress.value > 0.001f || panelAlpha.value > 0.001f
        }
    }
    val discoverState = rememberSearchDiscoverState()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // 首页搜索展开期间锁定标题行不折叠，防止部分折叠或软键盘弹出时挤压/裁切搜索胶囊；收起后恢复跟踪列表滚动。
    val rawCollapseTarget = (feedCollapse / barTitleHeightPx).coerceIn(0f, 1f)
    val collapseTarget = if (tab == 0 && feedSearchExpanded) 0f else rawCollapseTarget
    val collapseShown = remember { Animatable(collapseTarget) }
    var wasSearchExpanded by remember { mutableStateOf(feedSearchExpanded) }
    LaunchedEffect(collapseTarget, feedSearchExpanded) {
        val searchTransition = (wasSearchExpanded != feedSearchExpanded) || (feedSearchExpanded && collapseShown.value > 0f)
        wasSearchExpanded = feedSearchExpanded
        if (abs(collapseTarget - collapseShown.value) > 0.6f || searchTransition) {
            collapseShown.animateTo(collapseTarget, tween(MainTabFadeMillis))
        } else {
            collapseShown.snapTo(collapseTarget)
        }
    }
    val collapsed = collapseShown.value

    val homeResultsState = rememberSearchResultsState(scope)

    // 搜索面板的两种中间态：关键词还没提交（「建议中」，底下仍是热搜/历史），以及已有结果在显示。
    // 顶栏的筛选行与面板列表的上沿留白都读同一份判断，栏和内容才不会差一格。
    val searchSuggesting =
        feedSearchQuery.isNotBlank() && feedSearchQuery.trim() != homeResultsState.submitted
    val searchShowingResults =
        homeResultsState.submitted.isNotBlank() && !searchSuggesting && feedSearchQuery.isNotBlank()
    var searchFilterMenuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(searchShowingResults) {
        if (!searchShowingResults) searchFilterMenuOpen = false
    }

    val openFeedSearch: () -> Unit = {
        discoverState.clearEditing()
        homeResultsState.clearResults()
        feedSearchQuery = ""
        feedSearchExpanded = true
    }
    val cancelFeedSearch: () -> Unit = {
        keyboard?.hide()
        focusManager.clearFocus()
        discoverState.clearEditing()
        feedSearchExpanded = false
    }
    val closeFeedSearchImmediately: suspend () -> Unit = {
        keyboard?.hide()
        focusManager.clearFocus()
        discoverState.clearEditing()
        homeResultsState.clearResults()
        feedSearchQuery = ""
        feedSearchExpanded = false
        panelProgress.snapTo(0f)
        panelAlpha.snapTo(0f)
    }
    val submitHomeSearch: (String) -> Unit = { raw ->
        val keyword = raw.trim()
        if (keyword.isNotEmpty()) {
            feedSearchQuery = keyword
            keyboard?.hide()
            focusManager.clearFocus()
            discoverState.clearEditing()
            homeResultsState.runSearch(keyword, discoverState)
        }
    }

    LaunchedEffect(feedSearchExpanded) {
        if (feedSearchExpanded) {
            scope.launch { discoverState.ensureHotLoaded() }
            when (panelAnimation) {
                // 面板从搜索栏下沿往下长出来。
                SearchPanelAnimation.Vertical -> coroutineScope {
                    launch {
                        panelAlpha.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(durationMillis = SearchPanelFadeMillis, easing = COUIInEase),
                        )
                    }
                    launch {
                        panelProgress.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = SearchSpringDamping,
                                stiffness = SearchSpringStiffnessExpand,
                                visibilityThreshold = 0.001f,
                            ),
                        )
                    }
                }
                // 整块面板淡入，没有几何变化。
                SearchPanelAnimation.Fade -> {
                    panelProgress.snapTo(1f)
                    panelAlpha.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = SearchPanelFadeMillis, easing = COUIInEase),
                    )
                }
            }
        } else if (panelProgress.value > 0f || panelAlpha.value > 0f) {
            when (panelAnimation) {
                SearchPanelAnimation.Vertical -> coroutineScope {
                    launch {
                        panelAlpha.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(
                                durationMillis = SearchPanelFadeMillis,
                                delayMillis = 200,
                                easing = COUIOutEase,
                            ),
                        )
                    }
                    launch {
                        panelProgress.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = SearchSpringDamping,
                                stiffness = SearchSpringStiffnessCollapse,
                                visibilityThreshold = 0.001f,
                            ),
                        )
                    }
                }
                SearchPanelAnimation.Fade -> panelAlpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = SearchPanelFadeMillis, easing = COUIOutEase),
                )
            }
            panelProgress.snapTo(0f)
            if (!feedSearchExpanded) {
                homeResultsState.clearResults()
                feedSearchQuery = ""
                discoverState.clearEditing()
            }
        }
    }

    LaunchedEffect(tab) {
        if (tab != 0 && (feedSearchExpanded || feedSearchQuery.isNotEmpty() ||
                panelProgress.value > 0f || panelAlpha.value > 0f)
        ) {
            closeFeedSearchImmediately()
        }
    }

    BindSearchSuggestionsEffect(
        state = discoverState,
        query = feedSearchQuery,
        active = tab == 0 && feedSearchExpanded,
        submitted = homeResultsState.submitted,
    )

    val feedPanelCovering = tab == 0 && panelMounted

    Box(Modifier.fillMaxSize()) {
        // The bar floats over the page, so the line it ends at is reported from the bar itself and
        // read by anything that has to be placed clear of it.
        CompositionLocalProvider(LocalTopBarBounds provides topBarBounds) {
            Box(
                Modifier
                    .fillMaxSize()
                    .layerBackdropIf(blur, backdrop)
                    .background(pageContainerColor())
                    .then(if (feedPanelCovering) Modifier.clearAndSetSemantics {} else Modifier),
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

        // Layer 2: 顶栏模糊材质层 + 首页分段标签行（在搜索面板之下，展开时会被面板盖住）。
        CompositionLocalProvider(
            LocalBackdrop provides if (blur) backdrop else null,
            LocalEdgeToEdge provides true,
        ) {
            PinnedBarDensity {
                MainBarUnderlay(
                    tab = tab,
                    feedPager = feedPager,
                    feedTabs = feedTabs,
                    guest = guest,
                    centerTab = centerTab,
                    centerOptions = centerOptions,
                    collapsed = collapsed,
                    titleHeightPx = barTitleHeightPx,
                    searchExpanded = feedSearchExpanded,
                    panelCovering = feedPanelCovering,
                    bounds = topBarBounds,
                    onMenuOpened = loadFeedGroups,
                    onCenterSelected = { selection ->
                        App.settings.applyFeedCenterTab(selection)
                        // 只补一次「还没拉到过 / 上次失败」的流：已经加载好的分组和它的续看位置
                        // 留给 TimelinePage，不要在切回来的时候冲掉。
                        val target = storeFor(selection)
                        if (target.error != null) scope.launch { target.refresh() }
                        val targetPage = if (leftTab.item != FeedTabItem.None) 1 else 0
                        scope.launch { feedPager.animateScrollToPage(targetPage) }
                    },
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
                )
            }
        }

        // Layer 3: 悬浮底部导航栏（在面板之下，展开时被面板盖住）。
        CompositionLocalProvider(LocalBackdrop provides if (blur) backdrop else null) {
            PinnedBarDensity {
                FloatingBottomBar(
                    tab = tab,
                    onTab = { target -> if (!feedPanelCovering) nav.setTab(target) },
                    // 刷新键跟随当前页：消息页刷消息，其余仍走微博回顶刷新。
                    onRefresh = {
                        if (!feedPanelCovering) {
                            if (tab == 1) App.requestMessagesTop() else App.requestFeedTop()
                        }
                    },
                    onFeedTop = { if (!feedPanelCovering) App.requestFeedTop() },
                    onPublish = {
                        if (!feedPanelCovering) {
                            if (guest) App.toast("纯本地模式不能发微博，登录后可用") else nav.push(Route.Publish())
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .then(if (feedPanelCovering) Modifier.clearAndSetSemantics {} else Modifier),
                )
            }
        }

        // Layer 4: 首页搜索向下展开的整屏面板（从屏幕顶 0 铺满到屏幕底部，位于顶栏渐变材质之下、底栏之上）。
        if (tab == 0 && panelMounted) {
            HomeSearchOverlay(
                topBarHeight = topBarHeight,
                blur = blur,
                searchBackdrop = searchBackdrop,
                searchExpanded = feedSearchExpanded,
                panelAnimation = panelAnimation,
                panelProgress = { panelProgress.value },
                panelAlpha = { panelAlpha.value },
                searchQuery = feedSearchQuery,
                showingResults = searchShowingResults,
                discoverState = discoverState,
                resultsState = homeResultsState,
                nav = nav,
                scope = scope,
                onSelectKeyword = submitHomeSearch,
                onRetryHot = { scope.launch { discoverState.loadHot(force = true) } },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Layer 5: 标题行与展开后的 TopAppBarSearchBar（在最顶层，确保搜索胶囊和“取消”按钮不被面板遮挡）。
        CompositionLocalProvider(
            LocalBackdrop provides if (blur) {
                if (tab == 0 && panelMounted) searchBackdrop else backdrop
            } else {
                null
            },
            LocalEdgeToEdge provides true,
        ) {
            PinnedBarDensity {
                MainTitleRow(
                    tab = tab,
                    nav = nav,
                    guest = guest,
                    collapsed = collapsed,
                    titleHeightPx = barTitleHeightPx,
                    searchQuery = feedSearchQuery,
                    onSearchQueryChange = {
                        if (it != feedSearchQuery) discoverState.clearEditing()
                        feedSearchQuery = it
                        if (it.isBlank()) homeResultsState.clearResults()
                    },
                    searchExpanded = feedSearchExpanded,
                    onSearchOpen = openFeedSearch,
                    onSearchCancel = cancelFeedSearch,
                    onSearchSubmit = submitHomeSearch,
                    showingResults = searchShowingResults,
                    resultsState = homeResultsState,
                    panelAlpha = { panelAlpha.value },
                    onFilterMenuOpen = { searchFilterMenuOpen = true },
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
                )
                // 筛选行左侧四宫格圆钮的菜单：菜单渲染在根 Scaffold，锚点按筛选行的行高与
                // 间距落在那颗圆钮上，栏的几何一改这里跟着走，不用两边各写一份坐标。
                if (searchShowingResults) {
                    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = statusBarTop + FeedTitleRowHeight + SearchChipRowGap),
                    ) {
                        SearchFilterMenu(
                            state = homeResultsState,
                            expanded = searchFilterMenuOpen,
                            onExpandedChange = { searchFilterMenuOpen = it },
                            horizontalPadding = searchHorizontalPadding(),
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = searchHorizontalPadding())
                                .size(ChipTabDefaults.Height),
                        )
                    }
                }
            }
        }
    }

    val inHomeSearchResults =
        nav.stack.size == 1 && tab == 0 && feedSearchExpanded && homeResultsState.submitted.isNotEmpty()
    if (inHomeSearchResults) {
        BackHandler {
            homeResultsState.clearResults()
            feedSearchQuery = ""
            discoverState.clearEditing()
            scope.launch { discoverState.ensureHotLoaded() }
        }
    }
    key(feedSearchExpanded, inHomeSearchResults) { CoboPredictiveBackHandler(nav) }
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

@Composable
private fun MainBarUnderlay(
    tab: Int,
    feedPager: PagerState,
    feedTabs: List<FeedTabSpec>,
    guest: Boolean,
    centerTab: FeedTabSelection,
    centerOptions: List<FeedTabSelection>,
    collapsed: Float,
    titleHeightPx: Float,
    searchExpanded: Boolean,
    panelCovering: Boolean,
    bounds: TopBarBounds,
    onMenuOpened: () -> Unit,
    onCenterSelected: (FeedTabSelection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val pageBarHeight = statusBarTop + FeedTitleRowHeight
    val feedHasTabs = feedTabs.size > 1
    val feedBarHeight = if (feedHasTabs) {
        pageBarHeight + TopAppBarDefaults.SmallTopAppBarCenterHeight
    } else {
        pageBarHeight
    }
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
    var filterMenuExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(searchExpanded, tab) {
        if (searchExpanded || tab != 0) filterMenuExpanded = false
    }
    // 打开菜单时补一次分组列表：设置里改过分组、或者在网页版新加了分组，这里跟着更新。
    LaunchedEffect(filterMenuExpanded) {
        if (filterMenuExpanded) onMenuOpened()
    }

    Box(modifier) {
        AppBarSurface(
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
        AnimatedVisibility(
            visible = tab == 0 && feedHasTabs,
            enter = fadeIn(tween(MainTabFadeMillis)),
            exit = fadeOut(tween(MainTabFadeMillis)),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = statusBarTop)
                    .then(if (panelCovering) Modifier.clearAndSetSemantics {} else Modifier),
            ) {
                Spacer(Modifier.height(FeedTitleRowHeight))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(TopAppBarDefaults.SmallTopAppBarCenterHeight)
                        .graphicsLayer { translationY = -collapsed * titleHeightPx },
                ) {
                    FeedTabs(
                        pager = feedPager,
                        feedTabs = feedTabs,
                        filterMenu = !guest,
                        centerTab = centerTab,
                        centerOptions = centerOptions,
                        filterMenuExpanded = filterMenuExpanded,
                        onFilterMenuDismiss = { filterMenuExpanded = false },
                        onCenterSelected = {
                            filterMenuExpanded = false
                            onCenterSelected(it)
                        },
                        onSelect = { page -> scope.launch { feedPager.animateScrollToPage(page) } },
                        onFilterClick = { filterMenuExpanded = true },
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }
    }
}

private const val SearchPanelFadeMillis = 220

private const val SearchSpringDamping = 0.8f
private val SearchSpringStiffnessExpand = ((2.0 * Math.PI / 0.55) * (2.0 * Math.PI / 0.55)).toFloat()
private val SearchSpringStiffnessCollapse = ((2.0 * Math.PI / 0.45) * (2.0 * Math.PI / 0.45)).toFloat()

/** 上下扩展：从搜索栏下沿往下放，裁掉还没展开的部分。 */
private class SearchPanelClipShape(
    private val progress: Float,
    private val topBarHeightPx: Float,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val p = progress.coerceIn(0f, 1f)
        val bottom = (topBarHeightPx + (size.height - topBarHeightPx) * p)
            .coerceIn(0f, size.height)
        return Outline.Rectangle(Rect(0f, 0f, size.width, bottom))
    }

    override fun equals(other: Any?): Boolean =
        other is SearchPanelClipShape &&
            other.progress == progress &&
            other.topBarHeightPx == topBarHeightPx

    override fun hashCode(): Int = 31 * progress.hashCode() + topBarHeightPx.hashCode()
}

@Composable
private fun HomeSearchOverlay(
    topBarHeight: Dp,
    blur: Boolean,
    searchBackdrop: LayerBackdrop,
    searchExpanded: Boolean,
    panelAnimation: SearchPanelAnimation,
    panelProgress: () -> Float,
    panelAlpha: () -> Float,
    searchQuery: String,
    showingResults: Boolean,
    discoverState: SearchDiscoverState,
    resultsState: SearchResultsState,
    nav: NavState,
    scope: CoroutineScope,
    onSelectKeyword: (String) -> Unit,
    onRetryHot: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val surfaceColor = pageContainerColor()
    val listState = rememberLazyListState()
    val horizontalPadding = searchHorizontalPadding()
    val density = LocalDensity.current
    val topBarHeightPx = with(density) { topBarHeight.toPx() }

    LaunchedEffect(listState, resultsState.submitted, resultsState.tab, resultsState.stateFor(resultsState.tab).count) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { last ->
                if (resultsState.submitted.isBlank()) return@collect
                val tabState = resultsState.stateFor(resultsState.tab)
                if (!tabState.started || tabState.end || tabState.count == 0) return@collect
                if (last >= tabState.count - 2) resultsState.load(resultsState.tab, reset = false, force = false)
            }
    }

    val suggesting = searchQuery.isNotBlank() && !showingResults

    LaunchedEffect(showingResults, resultsState.submitted, suggesting, resultsState.tab) {
        listState.scrollToItem(0)
    }

    val activeSelectKeyword: (String) -> Unit = { keyword ->
        if (searchExpanded) onSelectKeyword(keyword)
    }

    Box(modifier) {
        val history = App.settings.searchHistory
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = panelProgress()
                    alpha = panelAlpha().coerceIn(0f, 1f)
                    if (panelAnimation != SearchPanelAnimation.Fade && p < 0.999f) {
                        clip = true
                        shape = SearchPanelClipShape(p, topBarHeightPx)
                    } else {
                        clip = false
                    }
                }
                .layerBackdropIf(blur, searchBackdrop)
                .background(surfaceColor)
                .then(
                    if (searchExpanded) {
                        Modifier
                            .dismissHistoryEditingOnTap(discoverState)
                            .testTag("home_search_panel")
                    } else {
                        Modifier.pointerInput(Unit) {}
                    },
                ),
        ) {
            CompositionLocalProvider(LocalBackdrop provides if (blur) searchBackdrop else null) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 0.dp,
                        // 结果态的栏多一行筛选胶囊，列表上沿让过这一行（间距 + 胶囊行 + 栏底留白），
                        // 否则第一张卡会压在胶囊行底下。
                        top = glassBarContentTop(topBarHeight, 8.dp) + if (showingResults) {
                            pinnedRowsHeight(
                                listOf(
                                    SearchChipRowGap,
                                    ChipTabDefaults.Height,
                                    TwoRowTopAppBarDefaults.SecondRowBottom,
                                ),
                            )
                        } else {
                            0.dp
                        },
                        end = 0.dp,
                        bottom = 16.dp + navBottomPadding(),
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!showingResults) {
                        searchDiscoverItems(
                            state = discoverState,
                            suggesting = suggesting,
                            history = history.toList(),
                            onSelectKeyword = activeSelectKeyword,
                            onRetryHot = onRetryHot,
                        )
                    } else {
                        searchResultsItems(
                            state = resultsState,
                            nav = nav,
                            horizontalPadding = horizontalPadding,
                            scope = scope,
                            onSelectKeyword = activeSelectKeyword,
                        )
                    }
                }
            }
        }

    }
}

@Composable
private fun MainTitleRow(
    tab: Int,
    nav: NavState,
    guest: Boolean,
    collapsed: Float,
    titleHeightPx: Float,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchExpanded: Boolean,
    onSearchOpen: () -> Unit,
    onSearchCancel: () -> Unit,
    onSearchSubmit: (String) -> Unit,
    showingResults: Boolean,
    resultsState: SearchResultsState,
    panelAlpha: () -> Float,
    onFilterMenuOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Crossfade(
        targetState = tab,
        animationSpec = tween(MainTabFadeMillis),
        modifier = modifier,
        label = "mainTitleRow",
    ) { page ->
        when (page) {
            0 -> FeedTitleRow(
                collapsed = collapsed,
                titleHeightPx = titleHeightPx,
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                searchExpanded = searchExpanded,
                onSearchOpen = onSearchOpen,
                onSearchCancel = onSearchCancel,
                onSearchSubmit = onSearchSubmit,
                showingResults = showingResults,
                resultsState = resultsState,
                panelAlpha = panelAlpha,
                onFilterMenuOpen = onFilterMenuOpen,
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
 * 搜索结果态下搜索框与筛选胶囊行之间的间距：便签两行顶栏的实测视觉距离是 17dp，
 * 44dp 的标题行里搜索框下方留 2dp，再空 15dp 正好对齐。
 */
internal val SearchChipRowGap = 15.dp

@Composable
private fun FeedTitleRow(
    collapsed: Float,
    titleHeightPx: Float,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchExpanded: Boolean,
    onSearchOpen: () -> Unit,
    onSearchCancel: () -> Unit,
    onSearchSubmit: (String) -> Unit,
    showingResults: Boolean,
    resultsState: SearchResultsState,
    panelAlpha: () -> Float,
    onFilterMenuOpen: () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    val blur = blurEffectsEnabled()

    // 材质、两行布局与胶囊采样的顺序由 coui 的 SearchFilterTopAppBar 统一处理：材质先录进
    // 栏自己的层，两行画在录制之外，胶囊采的是「模糊提白之后」的画面。这里只负责搜索框本身
    // 与那一行筛选胶囊的内容。
    SearchFilterTopAppBar(
        filters = if (showingResults) rememberSearchFilterTabs() else null,
        selectedFilter = resultsState.tab.ordinal,
        onFilterSelected = { index ->
            SearchTab.entries.getOrNull(index)?.let(resultsState::selectTab)
        },
        modifier = Modifier.fillMaxWidth(),
        firstRowHeight = FeedTitleRowHeight,
        rowGap = SearchChipRowGap,
        leadingIcon = if (showingResults) COUIIcons.GridView else null,
        onLeadingIconClick = onFilterMenuOpen,
        filterContentPadding = PaddingValues(horizontal = searchHorizontalPadding()),
        containerColor = pageContainerColor(),
        scrollFraction = panelAlpha,
    ) {
        Box(Modifier.fillMaxWidth().height(FeedTitleRowHeight)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        // 按钮居中在标题行里，行高不变时折一半才是原来那条轨迹。
                        translationY = -collapsed * titleHeightPx / 2f
                        alpha = 1f - collapsed
                    },
            ) {
                TopAppBarSearchBar(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    expanded = searchExpanded,
                    onExpandedChange = { isExpanded ->
                        if (isExpanded) onSearchOpen() else onSearchCancel()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onPreviewKeyEvent { event ->
                            val enter = event.key == Key.Enter ||
                                event.key == Key.NumPadEnter ||
                                event.key == Key.Search
                            if (searchExpanded && event.type == KeyEventType.KeyDown && enter) {
                                onSearchSubmit(searchQuery)
                                true
                            } else {
                                false
                            }
                        },
                    placeholder = "搜索微博 / 用户 / 话题",
                    cancelText = "取消",
                    onSearch = onSearchSubmit,
                    onCancel = onSearchCancel,
                    enabled = searchExpanded || collapsed < 0.95f,
                    searchButtonContentDescription = "搜索",
                    gradientColor = Color.Transparent,
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            val clearTint = scheme.onSurface.copy(alpha = 0.28f)
                            Canvas(
                                modifier = Modifier
                                    .size(36.dp)
                                    .semantics { contentDescription = "清除" }
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        role = Role.Button,
                                        onClick = { onSearchQueryChange("") },
                                    ),
                            ) {
                                val unit = size.width / 36f
                                drawCircle(clearTint, radius = 9f * unit)
                                val path = Path().apply {
                                    moveTo(18f, 16.868f)
                                    lineTo(15.031f, 13.898f)
                                    lineTo(13.899f, 15.03f)
                                    lineTo(16.869f, 18f)
                                    lineTo(13.898f, 20.97f)
                                    lineTo(15.03f, 22.101f)
                                    lineTo(18f, 19.131f)
                                    lineTo(20.97f, 22.101f)
                                    lineTo(22.102f, 20.969f)
                                    lineTo(19.132f, 18f)
                                    lineTo(22.101f, 15.03f)
                                    lineTo(20.969f, 13.899f)
                                    close()
                                }
                                withTransform({ scale(unit, unit, Offset.Zero) }) {
                                    drawPath(path, Color.White)
                                }
                            }
                        }
                    },
                    titleContent = {
                        // Only the title folds away under the status bar; clipping the whole row
                        // cuts the search button's halo and shared transition overlay off.
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(FeedTitleRowHeight)
                                .clipToBounds(),
                        ) {
                            Text(
                                stringResource(R.string.app_name),
                                color = scheme.onSurface,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(bottom = 6.dp)
                                    .graphicsLayer {
                                        // 外层已移动 -collapsed * titleHeightPx / 2f，这里补齐另一半以保持原轨迹。
                                        translationY = -collapsed * titleHeightPx / 2f
                                    },
                            )
                        }
                    },
                )
            }
            if (!searchExpanded && collapsed < 0.95f) {
                val endInset = if (Build.VERSION.SDK_INT >= 34 && blur) 20.dp else 16.dp
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = endInset)
                        .graphicsLayer { translationY = -collapsed * titleHeightPx / 2f },
                ) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .semantics {
                                role = Role.Button
                                onClick {
                                    onSearchOpen()
                                    true
                                }
                            }
                            .testTag("top_search"),
                    )
                }
            }
        }
    }
}

/**
 * 首页顶栏的分段选择器。
 *
 * 外观与交互全部来自 `../coui` 的 [TabRowWithContour]（胶囊玻璃材质 + 选中胶囊落影 + 跟手高光），
 * 这里只负责三件事：把页面尺寸固定成三格等宽、把中间那格接上内置流与关注分组的二级菜单、
 * 把点击与左右拖动都换算成翻页。
 */
@Composable
private fun FeedTabs(
    pager: PagerState,
    feedTabs: List<FeedTabSpec>,
    filterMenu: Boolean,
    centerTab: FeedTabSelection,
    centerOptions: List<FeedTabSelection>,
    filterMenuExpanded: Boolean,
    onFilterMenuDismiss: () -> Unit,
    onCenterSelected: (FeedTabSelection) -> Unit,
    onSelect: (Int) -> Unit,
    onFilterClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = COUITheme.colorScheme
    val backdrop = LocalBackdrop.current
    // 透明模糊那档要 API 34+（原生 RuntimeShader）且用户没关模糊，否则退回不透明胶囊。
    // 这里的判断和库内一致，颜色也跟着换，免得玻璃档还挂着一层不透明底色。
    val glass = backdrop != null && isBlurEnabled() && Build.VERSION.SDK_INT >= 34
    val labels = feedTabs.map { it.label }

    // 三格同宽，按「4 个字 + 箭头」预留：分组名是 2/3/4 个字时胶囊和格子都不动。
    // 用 sp 折算，系统字体放大时格子跟着变宽；先落到 px 再折算回去，容器宽度与库内
    // 「最小宽度 × 格数 + 两侧内边距」严格相等，不会因为取整差 1px 被判定溢出而退回纯色行。
    val density = LocalDensity.current
    val segmentWidthPx = with(density) { ((4 * 14).sp.toDp() + 2.dp + 10.dp + 22.dp).roundToPx() }
    val contourPadPx = with(density) { TabRowDefaults.TabRowWithContourPadding.roundToPx() }
    val segmentWidth = with(density) { segmentWidthPx.toDp() }
    val containerWidth = with(density) { (segmentWidthPx * feedTabs.size + contourPadPx * 2).toDp() }

    // 点一下先把指示器挪过去、再让列表跟上；等 pager 停稳就把临时值交还给 pager。
    // 不跟 pager 的中间页走，长距离切换时指示器不会一格一格地过。
    var pendingPage by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(pager.currentPage, pager.isScrollInProgress) {
        if (!pager.isScrollInProgress) pendingPage = null
    }
    val selectedIndex = (pendingPage ?: pager.targetPage).coerceIn(0, max(0, feedTabs.lastIndex))

    TabRowWithContour(
        tabs = labels,
        selectedTabIndex = selectedIndex,
        onTabSelected = { index ->
            if (index != pager.currentPage) pendingPage = index
            onSelect(index)
        },
        // 中间那格同时是「关注」和分组菜单的入口：点它切过来的同时把菜单弹出来。
        onTabClick = { index ->
            if (filterMenu && feedTabs.getOrNull(index)?.isCenter == true) onFilterClick()
        },
        modifier = modifier.width(containerWidth),
        colors = if (glass) {
            TabRowDefaults.tabRowColors(
                contentColor = scheme.onSurface,
                selectedContentColor = scheme.onSurface,
            )
        } else {
            TabRowDefaults.opaqueTabRowColors(
                contentColor = scheme.onSurface,
                selectedContentColor = scheme.onSurface,
            )
        },
        minWidth = segmentWidth,
        maxWidth = segmentWidth,
        segmentPadding = 8.dp,
        backdrop = backdrop,
        commitOnRelease = true,
        dragSelectionEnabled = true,
        touchMotionEnabled = true,
        tabModifier = { index -> Modifier.testTag(feedTabs[index].tag) },
        tabContent = { index, label, active ->
            val menu = filterMenu && feedTabs.getOrNull(index)?.isCenter == true
            // 两态同色，和系统 SegmentButton 一致：靠滑块和字重区分选中，不靠灰一档。
            val labelColor = scheme.onSurface
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        label,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = labelColor,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        // 箭头固定占一格，文字让出这一段：长分组名不会把箭头挤出格子。
                        modifier = Modifier.widthIn(max = segmentWidth - 12.dp - if (menu) 12.dp else 0.dp),
                    )
                    if (menu) {
                        Spacer(Modifier.width(2.dp))
                        Icon(
                            COUIIcons.Basic.ExpandableIndicator,
                            contentDescription = null,
                            tint = labelColor,
                            modifier = Modifier.size(10.dp),
                        )
                    }
                }
                if (menu) {
                    // 内置流是一级菜单；关注分组是账号里的数据、组数不可控，收进二级菜单，
                    // 不让它把一级菜单撑成一长条。当前选中的分组挂在「关注分组」这一行的
                    // 第二行小字上，进了二级也能看出选中在哪一组。
                    val builtinOptions = centerOptions.filterNot { it.isGroup }
                    val groupOptions = centerOptions.filter { it.isGroup }
                    val currentGroupTitle = centerTab.group
                        ?.takeIf { centerTab.isGroup }
                        ?.title
                        ?.takeIf { it.isNotBlank() }
                    OverlayCascadingListPopup(
                        show = filterMenuExpanded,
                        entries = buildList {
                            builtinOptions.firstOrNull { it.item == FeedTabItem.Follow }?.let { follow ->
                                add(
                                    DropdownEntry(
                                        items = listOf(
                                            DropdownItem(
                                                text = "全部",
                                                selected = follow.key == centerTab.key,
                                                onClick = { onCenterSelected(follow) },
                                            )
                                        )
                                    )
                                )
                            }
                            val filters = builtinOptions
                                .filterNot { it.item == FeedTabItem.Follow }
                                .map { option ->
                                    DropdownItem(
                                        text = option.label,
                                        selected = option.key == centerTab.key,
                                        onClick = { onCenterSelected(option) },
                                    )
                                }
                            if (filters.isNotEmpty()) add(DropdownEntry(items = filters))
                            if (groupOptions.isNotEmpty()) {
                                add(
                                    DropdownEntry(
                                        items = listOf(
                                            DropdownItem(
                                                text = FeedTabItem.Group.label,
                                                selected = centerTab.isGroup,
                                                summary = currentGroupTitle,
                                                children = groupOptions.map { option ->
                                                    DropdownItem(
                                                        text = option.label,
                                                        selected = option.key == centerTab.key,
                                                        onClick = { onCenterSelected(option) },
                                                    )
                                                },
                                            )
                                        )
                                    )
                                )
                            }
                        },
                        popupPositionProvider = ListPopupDefaults.DropdownPositionProvider,
                        onDismissRequest = onFilterMenuDismiss,
                    )
                }
            }
        },
    )
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
