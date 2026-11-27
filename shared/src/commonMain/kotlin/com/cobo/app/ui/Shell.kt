package com.cobo.app.ui

import com.cobo.app.platform.PlatformBackHandler
import com.cobo.app.platform.uptimeMillis

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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cobo.app.App
import com.cobo.app.FeedTopAction
import com.cobo.app.data.Account
import com.cobo.app.data.BottomBarItem
import com.cobo.app.data.BottomLeftAction
import com.cobo.app.data.FeedPage
import com.cobo.app.data.FeedTabItem
import com.cobo.app.data.FeedTabSelection
import com.cobo.app.data.FollowGroup
import com.cobo.app.data.RecommendSeenRetention
import com.cobo.app.data.SearchPanelAnimation
import com.cobo.app.data.WEIBO_PROFILE_EDIT_URL
import com.cobo.app.data.feedTabOptions
import com.cobo.app.data.nearbyCoords
import com.cobo.app.screen.BindSearchSuggestionsEffect
import com.cobo.app.screen.FeedFilter
import com.cobo.app.screen.FeedStore
import com.cobo.app.screen.LocalHome
import com.cobo.app.screen.LocalMessagesPlaceholder
import com.cobo.app.screen.MessagesScreen
import com.cobo.app.screen.MessagesTabState
import com.cobo.app.screen.ProfileContent
import com.cobo.app.screen.ProfileHubContent
import com.cobo.app.screen.ProfileTabState
import com.cobo.app.screen.SearchDiscoverState
import com.cobo.app.screen.SearchFilterMenu
import com.cobo.app.screen.SearchResultsState
import com.cobo.app.screen.SearchTab
import com.cobo.app.screen.TimelineScreen
import com.cobo.app.screen.TimelineScrollHolder
import com.cobo.app.screen.dismissHistoryEditingOnTap
import com.cobo.app.screen.rememberCoverContrast
import com.cobo.app.screen.rememberSearchDiscoverState
import com.cobo.app.screen.rememberSearchFilterTabs
import com.cobo.app.screen.rememberSearchResultsState
import com.cobo.app.screen.searchDiscoverItems
import com.cobo.app.screen.searchHorizontalPadding
import com.cobo.app.screen.searchResultsItems
import com.cobo.app.ui.components.Avatar
import com.cobo.app.ui.components.LoadingState
import com.cobo.app.ui.components.ProfileEditDialog
import com.cobo.app.ui.components.navBottomPadding
import io.github.suqi8.coui.kmp.anim.COUIInEase
import io.github.suqi8.coui.kmp.anim.COUIOutEase
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic
import io.github.suqi8.coui.kmp.basic.AppBarBlurOverlayDefaults
import io.github.suqi8.coui.kmp.basic.BindScrollToTop
import io.github.suqi8.coui.kmp.basic.BottomFloatingAccessoryNavigationBar
import io.github.suqi8.coui.kmp.basic.BottomFloatingNavigationBarDefaults
import io.github.suqi8.coui.kmp.basic.BottomFloatingNavigationBarItem
import io.github.suqi8.coui.kmp.basic.ChipTabDefaults
import io.github.suqi8.coui.kmp.basic.DropdownEntry
import io.github.suqi8.coui.kmp.basic.DropdownImpl
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.ListPopupColumn
import io.github.suqi8.coui.kmp.basic.ListPopupDefaults
import io.github.suqi8.coui.kmp.basic.LocalEdgeToEdge
import io.github.suqi8.coui.kmp.basic.LocalPlatformBottomBarMaterialEnabled
import io.github.suqi8.coui.kmp.basic.NavigationPopupMenuDefaults
import io.github.suqi8.coui.kmp.basic.PopupPositionProvider
import io.github.suqi8.coui.kmp.basic.popupBottomBarrier
import io.github.suqi8.coui.kmp.basic.SearchBarActionButton
import io.github.suqi8.coui.kmp.basic.SearchResultContainer
import io.github.suqi8.coui.kmp.basic.SearchResultsTransitionState
import io.github.suqi8.coui.kmp.basic.TabRowDefaults
import io.github.suqi8.coui.kmp.basic.TabRowWithContour
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.basic.TopAppBarSearchBar
import io.github.suqi8.coui.kmp.basic.TwoRowTopAppBarDefaults
import io.github.suqi8.coui.kmp.basic.couiTopAppBarActionItem
import io.github.suqi8.coui.kmp.basic.rememberSearchResultsTransitionState
import io.github.suqi8.coui.kmp.blur.LayerBackdrop
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.isBlurEnabled
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.basic.ExpandableIndicator
import io.github.suqi8.coui.kmp.icon.extended.Add
import io.github.suqi8.coui.kmp.icon.extended.Edit
import io.github.suqi8.coui.kmp.icon.extended.GridView
import io.github.suqi8.coui.kmp.icon.extended.Messages
import io.github.suqi8.coui.kmp.icon.extended.Refresh
import io.github.suqi8.coui.kmp.icon.extended.Search
import io.github.suqi8.coui.kmp.icon.extended.Settings
import io.github.suqi8.coui.kmp.overlay.OverlayCascadingListPopup
import io.github.suqi8.coui.kmp.overlay.OverlayListPopup
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.LocalContentColor
import io.github.suqi8.coui.kmp.theme.LocalDismissState
import io.github.suqi8.coui.kmp.utils.overScrollVertical
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

internal sealed class FeedTabSpec {
    abstract val key: String
    abstract val position: String
    abstract val label: String
    abstract val tag: String
    abstract val emptyText: String
    abstract val isFollow: Boolean

    /** 只有中间栏带「更多」菜单：它列的就是「微博中间栏内容」那份选项。 */
    abstract val isCenter: Boolean

    data class Custom(val selection: FeedTabSelection, override val position: String, val guest: Boolean) : FeedTabSpec() {
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

    data class Follow(val filter: FeedFilter, val guest: Boolean, override val position: String = "center") : FeedTabSpec() {
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
    // Both bars float over the three pages, so their edges are reported here and read by
    // anything that has to be placed clear of them (return transitions, popups, snackbars).
    val topBarBounds = remember { TopBarBounds() }
    val bottomBarBounds = remember { BottomBarBounds() }

    // 纯本地模式：没有账号可读关注流，首页只剩「本地关注」和「推荐」两段，筛选菜单也收起来。
    // 登录 / 退出都会 reset 导航栈，这个值在整个 MainShell 生命周期里不会变。
    val guest = remember { App.repo.guest }
    val accountOwner = remember { App.repo.session?.uid.orEmpty() }
    LaunchedEffect(Unit) { App.repo.refreshSessionIfStale() }
    val leftTab = App.settings.feedLeftTab
    val centerTab = App.settings.feedCenterTab
    val rightTab = App.settings.feedRightTab

    // 中间栏看哪条流由「中间栏内容」决定，菜单只是把这一项搬到页面上。原来的「筛选」只剩
    // 一份好友流，显示时统一叫「全部」；纯本地模式没有好友流，仍按「本地关注」走。
    val filter = if (guest) FeedFilter.Local else FeedFilter.All
    val friendsStore = remember {
        FeedStore("friends", resumable = true, supportsReverseBrowse = true, snapshotOwner = { accountOwner }) { cursor, force ->
            App.repo.friends(cursor, force)
        }
    }
    val filterStores = remember {
        mapOf(
            FeedFilter.All to friendsStore,
            FeedFilter.Following to friendsStore,
            FeedFilter.Original to FeedStore("original", resumable = true, supportsReverseBrowse = true, snapshotOwner = { accountOwner }) { cursor, force ->
                originalFeed(cursor, force)
            },
            FeedFilter.Unread to FeedStore("unread", snapshotOwner = { accountOwner }) { _, _ -> FeedPage(emptyList(), null) },
            FeedFilter.Favorites to FeedStore("favorites") { cursor, force ->
                App.repo.favorites(cursor, force)
            },
            FeedFilter.Special to FeedStore("special", resumable = true, supportsReverseBrowse = true, snapshotOwner = { accountOwner }) { cursor, force ->
                App.repo.special(cursor, force)
            },
            FeedFilter.Circle to FeedStore("circle", resumable = true, supportsReverseBrowse = true, snapshotOwner = { accountOwner }) { cursor, force ->
                App.repo.circle(cursor, force)
            },
            FeedFilter.Secret to FeedStore("secret", resumable = true, supportsReverseBrowse = true, snapshotOwner = { accountOwner }) { cursor, force ->
                App.repo.secret(cursor, force)
            },
            FeedFilter.Local to FeedStore("local", readOnly = true, resumable = true, supportsReverseBrowse = true, snapshotOwner = { accountOwner }) { cursor, force ->
                App.repo.localFollows(cursor, force)
            },
        )
    }
    val favoritesVersion = App.repo.favoritesVersion
    LaunchedEffect(favoritesVersion) {
        val favStore = filterStores[FeedFilter.Favorites] ?: return@LaunchedEffect
        if (favoritesVersion > 0 && favStore.loaded) favStore.refresh()
    }
    val recommendStore = remember {
        FeedStore(
            key = "102803",
            readOnly = guest,
            resumable = true,
            snapshotOwner = { accountOwner },
            tracksSeen = true,
            filterSeenEnabled = { App.settings.filterSeenRecommend },
            isStatusSeen = { owner, id ->
                App.isRecommendSeenReady() && App.recommendSeen.isSeen(
                    owner,
                    id,
                    RecommendSeenRetention.fromDays(App.settings.recommendSeenRetentionDays).millis,
                )
            },
            onMarkSeen = { owner, ids ->
                if (App.isRecommendSeenReady() && App.settings.filterSeenRecommend) {
                    App.recommendSeen.markSeen(
                        owner,
                        ids,
                        RecommendSeenRetention.fromDays(App.settings.recommendSeenRetentionDays).millis,
                    )
                }
            },
            onAllSeenFiltered = { filtered ->
                App.toast("已过滤 $filtered 条看过的内容，暂无新推荐")
            },
        )
    }
    val circleStore = filterStores.getValue(FeedFilter.Circle)
    val followStore = filterStores.getValue(filter)

    // 关注分组：分组是账号里的数据，gid 不同就是不同的一条流，一组一个 store。
    val groupStores = remember { HashMap<String, FeedStore>() }

    // 附近：坐标来自设备定位或用户选的城市，换位置之后这条流要整条重来。
    val nearbyStore = remember {
        FeedStore("nearby", resumable = true, snapshotOwner = { accountOwner }) { cursor, force ->
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
                FeedStore("group_${group.gid}", resumable = true, supportsReverseBrowse = true, snapshotOwner = { accountOwner }) { cursor, _ ->
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
    val feedSlotKeys = feedTabs.map { it.position }
    val barDensity = rememberBarDensity()
    val barTitleHeightPx = with(barDensity) { FeedTitleRowHeight.toPx() }
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
    val timelineScrollHolder = remember { TimelineScrollHolder() }
    val messagesState = remember { MessagesTabState() }
    val profileTabState = remember { ProfileTabState() }
    var editProfileOpen by remember { mutableStateOf(false) }
    val profileListState = rememberLazyListState()
    var profileActiveCover by remember { mutableStateOf<String?>(null) }
    val profileScrolledPast by remember(profileListState, barDensity) {
        derivedStateOf {
            val scrolled = if (profileListState.firstVisibleItemIndex > 0) Float.MAX_VALUE
            else profileListState.firstVisibleItemScrollOffset.toFloat()
            AppBarBlurOverlayDefaults.scrollAlpha(scrolled, barDensity) >= 0.45f
        }
    }
    val isTopRoute = rememberIsTopRoute(nav)
    val profileCoverContrast = rememberCoverContrast(
        coverUrl = if (guest) null else profileActiveCover,
        topBarHeight = topBarHeight,
        active = tab == 2 && !guest && !profileScrolledPast && isTopRoute,
        manageStatusBar = isTopRoute,
    )
    val meTitleColor = if (profileCoverContrast.useWhiteContent) Color.White else COUITheme.colorScheme.onSurface
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
    // 搜索面板完全展开稳态后才跳过底层主列表绘制，避免弹簧微振导致 backdrop 反复解绑重挂丢模糊。
    var searchSettled by remember { mutableStateOf(false) }
    val hideMainBehindSearch = tab == 0 && searchSettled
    val discoverState = rememberSearchDiscoverState()
    LaunchedEffect(Unit) { discoverState.ensureHotLoaded() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // 首页搜索展开期间锁定标题行不折叠，防止部分折叠或软键盘弹出时挤压/裁切搜索胶囊；收起后恢复跟踪列表滚动。
    // 通过 snapshotFlow 在副作用里监听 feedCollapse，避免列表滚动每像素触发整个 MainShell 顶层重组。
    val collapseShown = remember { Animatable(0f) }
    LaunchedEffect(tab, feedSearchExpanded, barTitleHeightPx) {
        var wasSearchExpanded = feedSearchExpanded
        var initialized = false
        snapshotFlow {
            val raw = (feedCollapse / barTitleHeightPx).coerceIn(0f, 1f)
            if (tab == 0 && feedSearchExpanded) 0f else raw
        }.collect { target ->
            val searchTransition = !initialized && (wasSearchExpanded != feedSearchExpanded || (feedSearchExpanded && collapseShown.value > 0f))
            initialized = true
            wasSearchExpanded = feedSearchExpanded
            if (abs(target - collapseShown.value) > 0.6f || searchTransition) {
                collapseShown.animateTo(target, tween(MainTabFadeMillis))
            } else {
                collapseShown.snapTo(target)
            }
        }
    }
    val collapsed: () -> Float = { collapseShown.value }

    val homeResultsState = rememberSearchResultsState(scope)
    val searchListState = rememberLazyListState()

    // 搜索面板的两种中间态：关键词还没提交（「建议中」，底下仍是热搜/历史），以及已有结果在显示。
    // 顶栏的筛选行与列表区都读同一份判断，避免两处各写一份后漂移。
    val searchSuggesting =
        feedSearchQuery.isNotBlank() && feedSearchQuery.trim() != homeResultsState.submitted
    val searchShowingResults =
        homeResultsState.submitted.isNotBlank() && !searchSuggesting && feedSearchQuery.isNotBlank()
    var searchFilterMenuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(searchShowingResults) {
        if (!searchShowingResults) searchFilterMenuOpen = false
    }

    // 搜索页两种内容的进出场交给 coui 的 SearchResultContainer 统一编排：
    // 提交后搜索结果从底部上浮，加载指示器跟着一起消散；
    // 返回搜索页时结果淡出、再淡入热搜与历史，顶栏搜索框本身保持稳住不闪烁。
    // queryKey 只认提交的关键词：换分类、换排序走的是较轻的「刷新上浮」，换关键词才整段重新上浮。
    val searchTransition = rememberSearchResultsTransitionState(
        showingResults = searchShowingResults,
        loading = homeResultsState.loading,
        queryKey = homeResultsState.submitted,
    )

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
        searchSettled = false
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
            searchSettled = false
            scope.launch { discoverState.ensureHotLoaded() }
            // 首帧留给搜索胶囊 120Hz 弹簧与底层静态快照建立，避免与面板组合撞在同一帧。
            withFrameNanos { }
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
            if (feedSearchExpanded) {
                searchSettled = true
            }
        } else if (panelProgress.value > 0f || panelAlpha.value > 0f) {
            searchSettled = false
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
        } else {
            searchSettled = false
        }
    }

    LaunchedEffect(tab) {
        if (tab != 0) searchSettled = false
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
    val autoHideFloatingBars = App.settings.autoHideFloatingBars
    var feedFilterMenuExpanded by remember { mutableStateOf(false) }
    var bottomBarMenuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(feedSearchExpanded, tab) {
        if (feedSearchExpanded || tab != 0) feedFilterMenuExpanded = false
    }
    val activeMainListState = when (tab) {
        0 -> {
            val slotKey = feedSlotKeys.getOrElse(feedPager.currentPage) { feedPager.currentPage.toString() }
            timelineScrollHolder.listStateFor(slotKey)
        }
        1 -> if (guest) null else messagesState.listState
        else -> profileListState
    }
    val mainTopSafeZone = if (tab == 0) FeedTitleRowHeight + 8.dp else 16.dp
    val mainBarScroll = rememberFloatingBarScrollController(
        enabled = autoHideFloatingBars,
        forceShow = feedSearchExpanded || panelMounted || feedFilterMenuExpanded || bottomBarMenuOpen,
        listState = activeMainListState,
        topSafeZone = mainTopSafeZone,
    )
    LaunchedEffect(tab, feedPager.currentPage) { mainBarScroll.show() }
    val mainBarHideProgress = mainBarScroll.hideProgress
    val bottomBarHidden by remember(mainBarHideProgress) {
        derivedStateOf { mainBarHideProgress.value >= FloatingBarNonInteractiveThreshold }
    }

    val freezeMainBehindSearch by remember {
        derivedStateOf { tab == 0 && panelMounted && !searchSettled }
    }

    Box(Modifier.fillMaxSize()) {
        // 搜索面板展开/收起过渡期间，将底层首页列表 + 顶栏模糊 + 底部熔球导航栏整体冻结为单张静态 GPU 纹理，
        // 避免底层多通道玻璃模糊与顶层搜索胶囊弹簧同时逐帧竞争 Metal 渲染时间。
        ExitSnapshotHost(exiting = freezeMainBehindSearch) {
        Box(Modifier.fillMaxSize()) {
        // Both bars float over the page, so their live edges are reported and read by
        // anything that has to be placed clear of them.
        CompositionLocalProvider(
            LocalTopBarBounds provides topBarBounds,
            LocalBottomBarBounds provides bottomBarBounds,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawWithContent { if (!hideMainBehindSearch) drawContent() }
                    .nestedScroll(mainBarScroll.connection)
                    .layerBackdropIf(blur && !hideMainBehindSearch, backdrop)
                    .background(pageContainerColor())
                    .then(if (feedPanelCovering) Modifier.clearAndSetSemantics {} else Modifier),
            ) {
                CompositionLocalProvider(LocalBackdrop provides if (blur) backdrop else null) {
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
                                    slotKeys = feedSlotKeys,
                                    scrollHolder = timelineScrollHolder,
                                )
                                1 -> if (guest) {
                                    LocalMessagesPlaceholder(nav, topBarHeight)
                                } else {
                                    MessagesScreen(nav, topBarHeight, state = messagesState)
                                }
                                else -> if (!App.settings.profileShowStatuses) ProfileHubContent(
                                    nav = nav,
                                    guest = guest,
                                    modifier = Modifier.fillMaxSize(),
                                    topInset = topBarHeight,
                                    extraBottom = FloatingBarContentGap,
                                    listState = profileListState,
                                    state = profileTabState,
                                    onActiveCover = { profileActiveCover = it },
                                ) else if (guest) LocalHome(
                                    nav = nav,
                                    topInset = topBarHeight,
                                    extraBottom = FloatingBarContentGap,
                                    listState = profileListState,
                                ) else ProfileContent(
                                    nav = nav,
                                    uid = null,
                                    modifier = Modifier.fillMaxSize(),
                                    topInset = topBarHeight,
                                    extraBottom = FloatingBarContentGap,
                                    listState = profileListState,
                                    state = profileTabState,
                                    onActiveCover = { profileActiveCover = it },
                                )
                            }
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
                    feedSlotKeys = feedSlotKeys,
                    guest = guest,
                    centerTab = centerTab,
                    centerOptions = centerOptions,
                    collapsed = collapsed,
                    titleHeightPx = barTitleHeightPx,
                    hideProgress = mainBarHideProgress,
                    filterMenuExpanded = feedFilterMenuExpanded,
                    onFilterMenuExpandedChange = { feedFilterMenuExpanded = it },
                    timelineScrollHolder = timelineScrollHolder,
                    messagesState = messagesState,
                    profileListState = profileListState,
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
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .drawWithContent { if (!hideMainBehindSearch) drawContent() },
                )
            }
        }

        // Layer 3: 悬浮底部导航栏（在面板之下，展开时被面板盖住）。
        val quickSwitchAccount = App.settings.quickSwitchAccount
        val profileTabAvatar = App.settings.profileTabAvatar
        val bottomLeftAction = App.settings.bottomLeftAction
        val feedTabTapToTopAndReturn = App.settings.feedTabTapToTopAndReturn
        val triggerBottomLeftAction: (BottomLeftAction) -> Unit = { action ->
            if (!feedPanelCovering && !bottomBarHidden) {
                when (action) {
                    BottomLeftAction.Refresh -> {
                        if (tab == 1) App.requestMessagesTop()
                        else {
                            if (tab != 0) nav.setTab(0)
                            App.requestFeedTop(FeedTopAction.RefreshFromAccessory)
                        }
                    }
                    BottomLeftAction.Search -> {
                        if (tab != 0) nav.setTab(0)
                        openFeedSearch()
                    }
                    BottomLeftAction.Favorites -> {
                        nav.push(Route.Favorites)
                    }
                }
            }
        }
        val activeUid = App.repo.session?.uid.orEmpty()
        var profileAvatarUrl by remember(activeUid, guest) {
            mutableStateOf(
                if (guest || activeUid.isBlank()) null
                else App.store.accounts().firstOrNull { it.uid == activeUid }?.avatar?.takeIf { it.isNotBlank() },
            )
        }
        LaunchedEffect(activeUid, guest, profileTabAvatar) {
            if (guest || !profileTabAvatar || activeUid.isBlank() || !profileAvatarUrl.isNullOrBlank()) {
                return@LaunchedEffect
            }
            val fetched = runCatching { App.repo.profile(activeUid) }.getOrNull() ?: return@LaunchedEffect
            val avatar = fetched.avatar.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
            profileAvatarUrl = avatar
            App.store.accounts().firstOrNull { it.uid == activeUid }?.let { existing ->
                App.store.saveAccount(
                    existing.copy(
                        name = fetched.name.ifBlank { existing.name },
                        avatar = avatar,
                    ),
                )
            }
        }
        LaunchedEffect(activeUid, guest) {
            if (guest || activeUid.isBlank()) return@LaunchedEffect
            App.profileUpdatedEvents.collect { updated ->
                if (updated.id == activeUid && updated.avatar.isNotBlank()) {
                    profileAvatarUrl = updated.avatar
                }
            }
        }
        CompositionLocalProvider(
            LocalBackdrop provides if (blur) backdrop else null,
            // 窗口级材质在弹层开合引起外层 backdrop 录制切换时会丢帧导致底栏闪烁，
            // 改用 COUI 的 Compose 玻璃引擎，直接采样同一块页面 backdrop。
            LocalPlatformBottomBarMaterialEnabled provides false,
        ) {
            PinnedBarDensity {
                var bottomBarHeightPx by remember { mutableIntStateOf(0) }
                var restingBottomBarTop by remember { mutableFloatStateOf(0f) }
                var restingBottomBarHeight by remember { mutableFloatStateOf(0f) }
                val bottomBarExtraClearancePx = with(LocalDensity.current) { 24.dp.toPx() }
                val bottomBarActive = !feedPanelCovering && !bottomBarHidden
                LaunchedEffect(bottomBarActive, restingBottomBarTop, restingBottomBarHeight) {
                    if (bottomBarActive && restingBottomBarHeight > 0f) {
                        bottomBarBounds.report(restingBottomBarTop, restingBottomBarHeight)
                    } else {
                        bottomBarBounds.clear()
                    }
                }
                // 外层容器不随 graphicsLayer 平移，始终向页面汇报静止态底栏坐标，
                // 避免恢复动画刚起步时将屏幕外的临时位置写进 bottomBarBounds。
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .drawWithContent { if (!hideMainBehindSearch) drawContent() }
                        .onGloballyPositioned { coords ->
                            val barTop = coords.positionInRoot().y
                            val barHeight = coords.size.height.toFloat()
                            if (barHeight > 0f && (restingBottomBarTop != barTop || restingBottomBarHeight != barHeight)) {
                                restingBottomBarTop = barTop
                                restingBottomBarHeight = barHeight
                            }
                        },
                ) {
                    FloatingBottomBar(
                        tab = tab,
                        itemOrder = App.settings.bottomBarOrder,
                        onTab = { target -> if (bottomBarActive) nav.setTab(target) },
                        tapToTopAndReturn = feedTabTapToTopAndReturn,
                        bottomLeftAction = bottomLeftAction,
                        onBottomLeftClick = { triggerBottomLeftAction(bottomLeftAction) },
                        onBottomLeftActionTrigger = { action ->
                            triggerBottomLeftAction(action)
                        },
                        onBottomLeftPinSelect = { selected ->
                            App.settings.applyBottomLeftAction(selected)
                        },
                        onOpenSettings = {
                            if (bottomBarActive) nav.push(Route.Settings)
                        },
                        menuEnabled = bottomBarActive,
                        onMenuExpandedChange = { bottomBarMenuOpen = it },
                        onFeedAction = { action ->
                            if (bottomBarActive) {
                                mainBarScroll.show()
                                App.requestFeedTop(action)
                            }
                        },
                        onPublish = {
                            if (bottomBarActive) {
                                if (guest) App.toast("纯本地模式不能发微博，登录后可用") else nav.push(Route.Publish())
                            }
                        },
                        profileAvatarUrl = if (profileTabAvatar && !guest) profileAvatarUrl else null,
                        profileOverflowMenu = if (quickSwitchAccount && bottomBarActive) {
                            {
                                ProfileQuickSwitchMenu(
                                    guest = guest,
                                    onSwitchAccount = { account ->
                                        App.repo.switchAccount(account)
                                        nav.reset(Route.Main)
                                    },
                                    onAddAccount = { nav.push(Route.SmsLogin(addAccount = true)) },
                                    onManageAccounts = { nav.push(Route.Accounts) },
                                )
                            }
                        } else {
                            null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { bottomBarHeightPx = it.height }
                            .graphicsLayer {
                                val hp = mainBarHideProgress.value.coerceIn(0f, 1f)
                                val travel = if (bottomBarHeightPx > 0) bottomBarHeightPx.toFloat() else bottomBarExtraClearancePx * 5f
                                translationY = hp * (travel + bottomBarExtraClearancePx)
                                alpha = (1f - hp * 1.25f).coerceIn(0f, 1f)
                            }
                            .then(if (!bottomBarActive) Modifier.clearAndSetSemantics {} else Modifier),
                    )
                }
            }
        }
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
                transitionState = searchTransition,
                listState = searchListState,
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
                    meTitleColor = meTitleColor,
                    collapsed = collapsed,
                    titleHeightPx = barTitleHeightPx,
                    searchQuery = feedSearchQuery,
                    onSearchQueryChange = { next ->
                        if (next != feedSearchQuery) discoverState.clearEditing()
                        feedSearchQuery = next
                        if (next.isBlank()) {
                            homeResultsState.clearResults()
                            scope.launch { discoverState.ensureHotLoaded() }
                        }
                    },
                    searchExpanded = feedSearchExpanded,
                    onSearchOpen = openFeedSearch,
                    onSearchCancel = cancelFeedSearch,
                    onSearchSubmit = submitHomeSearch,
                    filtersVisible = searchTransition.filtersVisible,
                    filtersAlpha = { searchTransition.filtersAlpha },
                    resultsState = homeResultsState,
                    searchListState = searchListState,
                    panelAlpha = { panelAlpha.value },
                    onFilterMenuOpen = { searchFilterMenuOpen = true },
                    onEditProfile = { editProfileOpen = true },
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
                )
                // 筛选行左侧四宫格圆钮的菜单：菜单渲染在根 Scaffold，锚点按筛选行的行高与
                // 间距落在那颗圆钮上，栏的几何一改这里跟着走，不用两边各写一份坐标。
                if (feedSearchExpanded && searchShowingResults) {
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

    val inHomeSearch = nav.stack.size == 1 && tab == 0 && feedSearchExpanded
    if (inHomeSearch) {
        PlatformBackHandler { cancelFeedSearch() }
    }
    val inHomeSearchResults =
        nav.stack.size == 1 && tab == 0 && feedSearchExpanded && homeResultsState.submitted.isNotEmpty()
    if (inHomeSearchResults) {
        PlatformBackHandler {
            homeResultsState.clearResults()
            feedSearchQuery = ""
            discoverState.clearEditing()
            scope.launch { discoverState.ensureHotLoaded() }
        }
    }
    key(feedSearchExpanded, inHomeSearchResults) { CoboPredictiveBackHandler(nav) }

    if (editProfileOpen) {
        ProfileEditDialog(
            user = profileTabState.user,
            onOpenWeb = { nav.push(Route.Web(WEIBO_PROFILE_EDIT_URL, "编辑资料")) },
            onDismiss = { editProfileOpen = false },
        )
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

private val FloatingBarContentGap = 80.dp

/** Cross-fade length for a bottom-bar tab switch. */
private const val MainTabFadeMillis = 220

@Composable
private fun MainBarUnderlay(
    tab: Int,
    feedPager: PagerState,
    feedTabs: List<FeedTabSpec>,
    feedSlotKeys: List<String>,
    guest: Boolean,
    centerTab: FeedTabSelection,
    centerOptions: List<FeedTabSelection>,
    collapsed: () -> Float,
    titleHeightPx: Float,
    hideProgress: State<Float>,
    filterMenuExpanded: Boolean,
    onFilterMenuExpandedChange: (Boolean) -> Unit,
    timelineScrollHolder: TimelineScrollHolder,
    messagesState: MessagesTabState,
    profileListState: LazyListState,
    searchExpanded: Boolean,
    panelCovering: Boolean,
    bounds: TopBarBounds,
    onMenuOpened: () -> Unit,
    onCenterSelected: (FeedTabSelection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
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
    val messagesWeight = animateFloatAsState(
        targetValue = if (tab == 1) 1f else 0f,
        animationSpec = tween(MainTabFadeMillis),
        label = "mainBarMessagesWeight",
    )
    val meWeight = animateFloatAsState(
        targetValue = if (tab == 2) 1f else 0f,
        animationSpec = tween(MainTabFadeMillis),
        label = "mainBarMeWeight",
    )
    val feedScrollAlpha = remember(feedPager, feedSlotKeys, timelineScrollHolder, density, feedHasTabs) {
        {
            val slotKey = feedSlotKeys.getOrElse(feedPager.currentPage) { feedPager.currentPage.toString() }
            val listState = timelineScrollHolder.listStateFor(slotKey)
            if (feedHasTabs) {
                twoRowListScrollAlpha(listState, density, contentGap = 16.dp)
            } else {
                listScrollAlpha(listState, density)
            }
        }
    }
    val messagesScrollAlpha = remember(messagesState, guest, density) {
        {
            if (guest) 0f else {
                val listState = messagesState.listState
                val scrolled = if (listState.firstVisibleItemIndex > 0) Float.MAX_VALUE
                else listState.firstVisibleItemScrollOffset.toFloat()
                AppBarBlurOverlayDefaults.scrollAlpha(scrolled, density)
            }
        }
    }
    val profileScrollAlpha = remember(profileListState, density) {
        {
            val scrolled = if (profileListState.firstVisibleItemIndex > 0) Float.MAX_VALUE
            else profileListState.firstVisibleItemScrollOffset.toFloat()
            AppBarBlurOverlayDefaults.scrollAlpha(scrolled, density)
        }
    }
    val materialShift = { (collapsed() * titleHeightPx * feedWeight.value).roundToInt() }
    // AppBarSurface 隐藏时仅上移分段标签行高度，使底边停在状态栏下沿，
    // 借由 COUI AppBarBlurOverlay 自带 24dp 渐隐过渡作为状态栏模糊蒙版。
    val barSurfaceHideDistancePx = with(density) {
        if (feedHasTabs) TopAppBarDefaults.SmallTopAppBarCenterHeight.toPx() else 0f
    }
    val feedTabsHideDistancePx = with(density) {
        if (feedHasTabs) {
            (statusBarTop + TopAppBarDefaults.SmallTopAppBarCenterHeight).toPx()
        } else {
            statusBarTop.toPx()
        }
    }
    val barSurfaceHideShift = {
        (hideProgress.value.coerceIn(0f, 1f) * barSurfaceHideDistancePx * feedWeight.value).roundToInt()
    }
    val materialHeight = animateDpAsState(
        targetValue = if (tab == 0) feedBarHeight else pageBarHeight,
        animationSpec = tween(MainTabFadeMillis),
        label = "mainBarMaterialHeight",
    )
    LaunchedEffect(hideProgress) {
        snapshotFlow { hideProgress.value > 0.5f }
            .distinctUntilChanged()
            .collect { shouldClose ->
                if (shouldClose) onFilterMenuExpandedChange(false)
            }
    }
    // 打开菜单时补一次分组列表：设置里改过分组、或者在网页版新加了分组，这里跟着更新。
    LaunchedEffect(filterMenuExpanded) {
        if (filterMenuExpanded) onMenuOpened()
    }

    val feedTabsHidden by remember(hideProgress) {
        derivedStateOf { hideProgress.value >= FloatingBarNonInteractiveThreshold }
    }

    Box(modifier) {
        AppBarSurface(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, -(materialShift() + barSurfaceHideShift())) }
                .reportTopBarBottom(bounds),
            containerColor = pageContainerColor(),
            scrollFraction = {
                (feedWeight.value * feedScrollAlpha() +
                    messagesWeight.value * messagesScrollAlpha() +
                    meWeight.value * profileScrollAlpha()).coerceIn(0f, 1f)
            },
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
            visible = tab == 0 && feedHasTabs && !searchExpanded,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(150)),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = statusBarTop)
                    .then(if (panelCovering || feedTabsHidden) Modifier.clearAndSetSemantics {} else Modifier),
            ) {
                Spacer(Modifier.height(FeedTitleRowHeight))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(TopAppBarDefaults.SmallTopAppBarCenterHeight)
                        .graphicsLayer {
                            val hp = hideProgress.value.coerceIn(0f, 1f)
                            translationY = -collapsed() * titleHeightPx - hp * feedTabsHideDistancePx
                            alpha = (1f - hp * 1.25f).coerceIn(0f, 1f)
                        },
                ) {
                    FeedTabs(
                        pager = feedPager,
                        feedTabs = feedTabs,
                        filterMenu = !guest,
                        centerTab = centerTab,
                        centerOptions = centerOptions,
                        filterMenuExpanded = filterMenuExpanded,
                        onFilterMenuDismiss = { onFilterMenuExpandedChange(false) },
                        onCenterSelected = {
                            onFilterMenuExpandedChange(false)
                            onCenterSelected(it)
                        },
                        onSelect = { page -> if (!feedTabsHidden) scope.launch { feedPager.animateScrollToPage(page) } },
                        onFilterClick = { if (!feedTabsHidden) onFilterMenuExpandedChange(true) },
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }
    }
}

private const val SearchPanelFadeMillis = 220

private const val SearchSpringDamping = 0.8f
private val SearchSpringStiffnessExpand = ((2.0 * kotlin.math.PI / 0.55) * (2.0 * kotlin.math.PI / 0.55)).toFloat()
private val SearchSpringStiffnessCollapse = ((2.0 * kotlin.math.PI / 0.45) * (2.0 * kotlin.math.PI / 0.45)).toFloat()

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
    transitionState: SearchResultsTransitionState,
    listState: LazyListState,
    nav: NavState,
    scope: CoroutineScope,
    onSelectKeyword: (String) -> Unit,
    onRetryHot: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val surfaceColor = pageContainerColor()
    BindScrollToTop(listState)
    val horizontalPadding = searchHorizontalPadding()
    val density = LocalDensity.current
    val topBarHeightPx = with(density) { topBarHeight.toPx() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(listState, resultsState.submitted, resultsState.tab, resultsState.stateFor(resultsState.tab).count) {
        snapshotFlow {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: -1) to info.totalItemsCount
        }.collect { (last, total) ->
            if (resultsState.submitted.isBlank()) return@collect
            val tabState = resultsState.stateFor(resultsState.tab)
            if (!tabState.started || tabState.end || tabState.count == 0) return@collect
            if (total > 0 && last >= total - 2) resultsState.load(resultsState.tab, reset = false, force = false)
        }
    }

    val suggesting = searchQuery.isNotBlank() && !showingResults

    LaunchedEffect(showingResults) {
        if (showingResults) {
            keyboard?.hide()
            focusManager.clearFocus(force = true)
        }
    }

    LaunchedEffect(transitionState.shownResults, resultsState.submitted, suggesting, resultsState.tab, discoverState.hotBoard) {
        // 淡出过程中旧内容还在画，先把滚动位置钉住；换完内容再回到顶部。
        if (transitionState.shownResults != showingResults) return@LaunchedEffect
        listState.scrollToItem(0)
    }

    val activeSelectKeyword: (String) -> Unit = { keyword ->
        if (searchExpanded) onSelectKeyword(keyword)
    }

    Box(modifier) {
        val history = App.settings.searchHistory
        // 外层负责淡入/裁切动画，内层始终挂载 layerBackdrop(searchBackdrop)，保证首帧即发布坐标并持续录制，
        // 避免搜索结果页顶栏采样不到 backdrop 而退化为实心底。
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
                },
        ) {
            Box(
                Modifier
                    .fillMaxSize()
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
                // 结果态列表顶沿与加载球顶沿要给第二行筛选胶囊让出位置；
                // 列表内容读的是 shownResults，避免提交瞬间发现页在淡出前往下跳一截。
                val baseTopPadding = glassBarContentTop(topBarHeight, 8.dp)
                val resultsBarTopPadding = baseTopPadding + pinnedRowsHeight(
                    listOf(
                        SearchChipRowGap,
                        ChipTabDefaults.Height,
                        TwoRowTopAppBarDefaults.SecondRowBottom,
                    ),
                )
                val listTopPadding = if (transitionState.shownResults) resultsBarTopPadding else baseTopPadding
                SearchResultContainer(
                    state = transitionState,
                    modifier = Modifier.fillMaxSize(),
                    loadingTopPadding = resultsBarTopPadding,
                    loadingContent = { LoadingState() },
                ) {
                    val filterRules = com.cobo.app.data.ContentFilters.rules
                    val selfUid = App.repo.session?.uid
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().scrollEndHaptic().overScrollVertical(),
                        contentPadding = PaddingValues(
                            start = 0.dp,
                            top = listTopPadding,
                            end = 0.dp,
                            bottom = 16.dp + navBottomPadding(),
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (!shownResults) {
                            searchDiscoverItems(
                                state = discoverState,
                                suggesting = suggesting,
                                history = history.toList(),
                                onSelectKeyword = activeSelectKeyword,
                                onRetryHot = onRetryHot,
                                onSelectUser = { user -> nav.push(Route.Profile(user.id)) },
                                filterRules = filterRules,
                            )
                        } else if (resultsVisible) {
                            searchResultsItems(
                                state = resultsState,
                                nav = nav,
                                horizontalPadding = horizontalPadding,
                                scope = scope,
                                onSelectKeyword = activeSelectKeyword,
                                filterRules = filterRules,
                                selfUid = selfUid,
                            )
                        }
                    }
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
    meTitleColor: Color,
    collapsed: () -> Float,
    titleHeightPx: Float,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchExpanded: Boolean,
    onSearchOpen: () -> Unit,
    onSearchCancel: () -> Unit,
    onSearchSubmit: (String) -> Unit,
    filtersVisible: Boolean,
    filtersAlpha: () -> Float,
    resultsState: SearchResultsState,
    searchListState: LazyListState,
    panelAlpha: () -> Float,
    onFilterMenuOpen: () -> Unit,
    onEditProfile: () -> Unit = {},
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
                filtersVisible = filtersVisible,
                filtersAlpha = filtersAlpha,
                resultsState = resultsState,
                searchListState = searchListState,
                panelAlpha = panelAlpha,
                onFilterMenuOpen = onFilterMenuOpen,
            )

            1 -> PageTitleBar(
                title = "消息",
                actions = {
                    if (!guest) {
                        SearchBarActionButton(
                            text = "通知",
                            onClick = { nav.push(Route.Notifications()) },
                            modifier = Modifier.testTag("top_notice"),
                        )
                    }
                },
            )

            else -> PageTitleBar(
                title = "我",
                titleColor = meTitleColor,
                actions = {
                    if (guest) {
                        HighlightedIconButton(
                            onClick = { nav.push(Route.Settings) },
                            modifier = Modifier.couiTopAppBarActionItem().testTag("top_settings"),
                        ) {
                            Icon(COUIIcons.Settings, "设置", Modifier.size(24.dp))
                        }
                    } else {
                        var meMenuExpanded by remember { mutableStateOf(false) }
                        val menuScheme = COUITheme.colorScheme
                        Box {
                            HighlightedIconButton(
                                onClick = { meMenuExpanded = true },
                                modifier = Modifier.couiTopAppBarActionItem().testTag("top_me_menu"),
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(3.dp),
                                    modifier = Modifier.semantics { contentDescription = "更多" },
                                ) {
                                    repeat(3) {
                                        Box(
                                            Modifier
                                                .size(3.5.dp)
                                                .clip(CircleShape)
                                                .background(LocalContentColor.current),
                                        )
                                    }
                                }
                            }
                            OverlayCascadingListPopup(
                                show = meMenuExpanded,
                                entries = listOf(
                                    DropdownEntry(
                                        items = listOf(
                                            DropdownItem(
                                                text = "设置",
                                                icon = { iconModifier ->
                                                    Box(iconModifier, contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = COUIIcons.Settings,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(18.dp),
                                                            tint = menuScheme.onSurfaceContainer,
                                                        )
                                                    }
                                                },
                                                onClick = { nav.push(Route.Settings) },
                                            ),
                                            DropdownItem(
                                                text = "编辑资料",
                                                icon = { iconModifier ->
                                                    Box(iconModifier, contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = COUIIcons.Edit,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(18.dp),
                                                            tint = menuScheme.onSurfaceContainer,
                                                        )
                                                    }
                                                },
                                                onClick = onEditProfile,
                                            ),
                                        ),
                                    ),
                                ),
                                popupPositionProvider = ListPopupDefaults.DropdownPositionProvider,
                                onDismissRequest = { meMenuExpanded = false },
                            )
                        }
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
    titleColor: Color = COUITheme.colorScheme.onSurface,
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
                color = titleColor,
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
    collapsed: () -> Float,
    titleHeightPx: Float,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchExpanded: Boolean,
    onSearchOpen: () -> Unit,
    onSearchCancel: () -> Unit,
    onSearchSubmit: (String) -> Unit,
    filtersVisible: Boolean,
    filtersAlpha: () -> Float,
    resultsState: SearchResultsState,
    searchListState: LazyListState,
    panelAlpha: () -> Float,
    onFilterMenuOpen: () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    val blur = blurEffectsEnabled()
    val barDensity = LocalDensity.current
    val searchBarEnabled by remember(searchExpanded) {
        derivedStateOf { searchExpanded || collapsed() < 0.95f }
    }
    val showSearchSemantics by remember(searchExpanded) {
        derivedStateOf { !searchExpanded && collapsed() < 0.95f }
    }

    val searchFilterTabs = rememberSearchFilterTabs()
    CoboSearchFilterTopAppBar(
        filters = if (filtersVisible) searchFilterTabs else null,
        selectedFilter = resultsState.tab.ordinal,
        onFilterSelected = { index ->
            SearchTab.entries.getOrNull(index)?.let(resultsState::selectTab)
        },
        modifier = Modifier.fillMaxWidth(),
        firstRowHeight = FeedTitleRowHeight,
        rowGap = SearchChipRowGap,
        leadingIcon = if (filtersVisible) COUIIcons.GridView else null,
        onLeadingIconClick = onFilterMenuOpen,
        filterContentPadding = PaddingValues(horizontal = searchHorizontalPadding()),
        containerColor = pageContainerColor(),
        scrollFraction = {
            val base = panelAlpha()
            if (base <= 0f) {
                0f
            } else {
                val scroll = if (filtersVisible) {
                    twoRowListScrollAlpha(searchListState, barDensity)
                } else {
                    listScrollAlpha(searchListState, barDensity)
                }
                base * scroll
            }
        },
        filtersAlpha = filtersAlpha,
    ) {
        Box(Modifier.fillMaxWidth().height(FeedTitleRowHeight)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        // 按钮居中在标题行里，行高不变时折一半才是原来那条轨迹。
                        val c = collapsed()
                        translationY = -c * titleHeightPx / 2f
                        alpha = 1f - c
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
                    placeholder = "搜索微博 / 图片 / 视频 / 用户 / 超话",
                    cancelText = "取消",
                    onSearch = onSearchSubmit,
                    onCancel = onSearchCancel,
                    enabled = searchBarEnabled,
                    searchButtonContentDescription = "搜索",
                    backdrop = LocalBackdrop.current,
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
                                "Cobo",
                                color = scheme.onSurface,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(bottom = 6.dp)
                                    .graphicsLayer {
                                        // 外层已移动 -collapsed * titleHeightPx / 2f，这里补齐另一半以保持原轨迹。
                                        translationY = -collapsed() * titleHeightPx / 2f
                                    },
                            )
                        }
                    },
                )
            }
            if (showSearchSemantics) {
                val endInset = if (blur) 20.dp else 16.dp
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = endInset)
                        .graphicsLayer { translationY = -collapsed() * titleHeightPx / 2f },
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
    val glass = backdrop != null && isBlurEnabled()
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
                    // 内置流留在一级菜单；关注分组根据设置选择「收纳进二级菜单」或「直接平铺展开在一级菜单」。
                    val collapseFollowGroups = App.settings.collapseFollowGroups
                    OverlayCascadingListPopup(
                        show = filterMenuExpanded,
                        entries = buildCenterMenuEntries(
                            centerOptions = centerOptions,
                            centerTab = centerTab,
                            collapseFollowGroups = collapseFollowGroups,
                            onCenterSelected = onCenterSelected,
                        ),
                        popupPositionProvider = ListPopupDefaults.DropdownPositionProvider,
                        onDismissRequest = onFilterMenuDismiss,
                    )
                }
            }
        },
    )
}

/**
 * 构建首页中间栏下拉菜单分段：第一段为「全部」，第二段为其余内置流，
 * 第三段按 [collapseFollowGroups] 将关注分组收进二级子菜单或直接全部平铺展开。
 */
internal fun buildCenterMenuEntries(
    centerOptions: List<FeedTabSelection>,
    centerTab: FeedTabSelection,
    collapseFollowGroups: Boolean,
    onCenterSelected: (FeedTabSelection) -> Unit,
): List<DropdownEntry> {
    val builtinOptions = centerOptions.filterNot { it.isGroup }
    val groupOptions = centerOptions.filter { it.isGroup }
    val currentGroupTitle = centerTab.group
        ?.takeIf { centerTab.isGroup }
        ?.title
        ?.takeIf { it.isNotBlank() }
    return buildList {
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
            if (collapseFollowGroups) {
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
            } else {
                add(
                    DropdownEntry(
                        items = groupOptions.map { option ->
                            DropdownItem(
                                text = option.label,
                                summary = FeedTabItem.Group.label,
                                selected = option.key == centerTab.key,
                                onClick = { onCenterSelected(option) },
                            )
                        }
                    )
                )
            }
        }
    }
}

@Composable
private fun FloatingBottomBar(
    tab: Int,
    itemOrder: List<BottomBarItem>,
    onTab: (Int) -> Unit,
    tapToTopAndReturn: Boolean,
    bottomLeftAction: BottomLeftAction,
    onBottomLeftClick: () -> Unit,
    onBottomLeftActionTrigger: (BottomLeftAction) -> Unit,
    onBottomLeftPinSelect: (BottomLeftAction) -> Unit,
    onOpenSettings: () -> Unit,
    menuEnabled: Boolean,
    onFeedAction: (FeedTopAction) -> Unit,
    onPublish: () -> Unit,
    onMenuExpandedChange: (Boolean) -> Unit = {},
    profileAvatarUrl: String? = null,
    profileOverflowMenu: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val normalizedOrder = remember(itemOrder) { BottomBarItem.normalize(itemOrder) }
    val primaryItems = normalizedOrder.take(3)
    val trailingItem = normalizedOrder[3]
    val tapScope = rememberCoroutineScope()
    var lastFeedTapAt by remember { mutableLongStateOf(0L) }
    var pendingSingleTapJob by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(tab, tapToTopAndReturn, menuEnabled) {
        if (tab != 0 || !tapToTopAndReturn || !menuEnabled) {
            pendingSingleTapJob?.cancel()
            pendingSingleTapJob = null
        }
        if (!menuEnabled) lastFeedTapAt = 0L
    }
    val onFeedTap: () -> Unit = {
        if (!tapToTopAndReturn) {
            // 默认关闭增强手势：保持原有「单击切页、320ms 内双击回顶/刷新」逻辑。
            val now = uptimeMillis()
            if (now - lastFeedTapAt <= 320L) {
                lastFeedTapAt = 0L
                onFeedAction(FeedTopAction.LegacyDoubleTap)
            } else {
                lastFeedTapAt = now
            }
            onTab(0)
        } else if (tab != 0) {
            pendingSingleTapJob?.cancel()
            pendingSingleTapJob = null
            onTab(0)
        } else {
            val activePending = pendingSingleTapJob
            if (activePending?.isActive == true) {
                activePending.cancel()
                pendingSingleTapJob = null
                onFeedAction(FeedTopAction.RefreshFromTab)
            } else {
                pendingSingleTapJob = tapScope.launch {
                    delay(240L)
                    pendingSingleTapJob = null
                    onFeedAction(FeedTopAction.ToggleTopOrReturn)
                }
            }
        }
    }
    var leftMenuExpanded by remember { mutableStateOf(false) }
    var profileMenuExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(leftMenuExpanded, profileMenuExpanded) {
        onMenuExpandedChange(leftMenuExpanded || profileMenuExpanded)
    }
    LaunchedEffect(menuEnabled) {
        if (!menuEnabled) leftMenuExpanded = false
    }
    val leftPopupPositionProvider = remember { bottomLeftPopupPositionProvider() }
    Box(
        modifier
            .fillMaxWidth()
            .padding(bottom = BottomFloatingNavigationBarDefaults.navigationBarPadding())
    ) {
        // 尺寸不再在这里手改：整棵树已在 CoboRoot 钉到相册密度，
        // 底栏条 56dp / 项 48dp / 圆钮 56dp 由组件自己的 token 决定。
        run {
            BottomFloatingAccessoryNavigationBar(
                modifier = if (menuEnabled) Modifier.popupBottomBarrier() else Modifier,
                startAccessory = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("bottom_refresh")
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Button,
                                onLongClick = if (menuEnabled) {
                                    { leftMenuExpanded = true }
                                } else {
                                    null
                                },
                                onClick = onBottomLeftClick,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = bottomLeftActionIcon(bottomLeftAction),
                            contentDescription = bottomLeftAction.label,
                            tint = COUITheme.colorScheme.onSurface,
                        )
                        if (menuEnabled) {
                            val iconTint = COUITheme.colorScheme.onSurfaceContainer
                            OverlayCascadingListPopup(
                                show = leftMenuExpanded,
                                entries = listOf(
                                    DropdownEntry(
                                        items = listOf(
                                            DropdownItem(
                                                text = "融合球功能",
                                                summary = bottomLeftAction.label,
                                                icon = { iconModifier ->
                                                    Box(iconModifier, contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = bottomLeftActionIcon(bottomLeftAction),
                                                            contentDescription = null,
                                                            modifier = Modifier.size(20.dp),
                                                            tint = iconTint,
                                                        )
                                                    }
                                                },
                                                children = BottomLeftAction.entries.map { option ->
                                                    DropdownItem(
                                                        text = option.label,
                                                        selected = option == bottomLeftAction,
                                                        icon = { iconModifier ->
                                                            Box(iconModifier, contentAlignment = Alignment.Center) {
                                                                Icon(
                                                                    imageVector = bottomLeftActionIcon(option),
                                                                    contentDescription = null,
                                                                    modifier = Modifier.size(20.dp),
                                                                    tint = iconTint,
                                                                )
                                                            }
                                                        },
                                                        onClick = { onBottomLeftPinSelect(option) },
                                                    )
                                                },
                                            ),
                                        ),
                                    ),
                                    DropdownEntry(
                                        items = BottomLeftAction.entries.map { option ->
                                            DropdownItem(
                                                text = option.label,
                                                icon = { iconModifier ->
                                                    Box(iconModifier, contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = bottomLeftActionIcon(option),
                                                            contentDescription = null,
                                                            modifier = Modifier.size(20.dp),
                                                            tint = iconTint,
                                                        )
                                                    }
                                                },
                                                onClick = { onBottomLeftActionTrigger(option) },
                                            )
                                        },
                                    ),
                                    DropdownEntry(
                                        items = listOf(
                                            DropdownItem(
                                                text = "设置",
                                                icon = { iconModifier ->
                                                    Box(iconModifier, contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = COUIIcons.Settings,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(20.dp),
                                                            tint = iconTint,
                                                        )
                                                    }
                                                },
                                                onClick = onOpenSettings,
                                            ),
                                        ),
                                    ),
                                ),
                                onDismissRequest = { leftMenuExpanded = false },
                                popupPositionProvider = leftPopupPositionProvider,
                                alignment = PopupPositionProvider.Align.BottomStart,
                                maxHeight = NavigationPopupMenuDefaults.MaxHeight,
                                minWidth = NavigationPopupMenuDefaults.MinWidth,
                                collapseOnSelection = true,
                            )
                        }
                    }
                },
                endAccessory = {
                    val scheme = COUITheme.colorScheme
                    when (trailingItem) {
                        BottomBarItem.Publish -> {
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
                                    tint = scheme.onSurface,
                                )
                            }
                        }
                        BottomBarItem.Messages -> {
                            val selected = tab == 1
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("bottom_tab_messages")
                                    .clickable(
                                        interactionSource = null,
                                        indication = null,
                                        role = Role.Tab,
                                        onClick = { onTab(1) },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = if (selected) CoboIcons.MessagesFilled else CoboIcons.MessagesOutline,
                                    contentDescription = "消息",
                                    tint = if (selected) scheme.primary else scheme.onSurface,
                                )
                            }
                        }
                        BottomBarItem.Feed -> {
                            val selected = tab == 0
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("bottom_tab_feed")
                                    .clickable(
                                        interactionSource = null,
                                        indication = null,
                                        role = Role.Tab,
                                        onClick = { onFeedTap() },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = if (selected) CoboIcons.FeedFilled else CoboIcons.FeedOutline,
                                    contentDescription = "微博",
                                    tint = if (selected) scheme.primary else scheme.onSurface,
                                )
                            }
                        }
                        BottomBarItem.Profile -> {
                            val selected = tab == 2
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("bottom_tab_profile")
                                    .combinedClickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        role = Role.Tab,
                                        onClick = { onTab(2) },
                                        onLongClick = if (profileOverflowMenu != null) {
                                            { profileMenuExpanded = true }
                                        } else {
                                            null
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (!profileAvatarUrl.isNullOrBlank()) {
                                    Avatar(
                                        url = profileAvatarUrl,
                                        size = BottomFloatingNavigationBarDefaults.IconSize,
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (selected) CoboIcons.ProfileFilled else CoboIcons.ProfileOutline,
                                        contentDescription = "我",
                                        tint = if (selected) scheme.primary else scheme.onSurface,
                                    )
                                }
                                if (profileOverflowMenu != null) {
                                    OverlayListPopup(
                                        show = profileMenuExpanded,
                                        popupPositionProvider = ListPopupDefaults.DropdownPositionProvider,
                                        onDismissRequest = { profileMenuExpanded = false },
                                    ) {
                                        profileOverflowMenu()
                                    }
                                }
                            }
                        }
                    }
                },
            ) {
                // 主胶囊内按用户自定义的前 3 项顺序排布（默认左「消息」、中「微博」、右「我」）；
                // tab 值始终保持 0=微博 / 1=消息 / 2=我，不改变持久化语义。
                primaryItems.forEach { slot ->
                    when (slot) {
                        BottomBarItem.Messages -> BottomFloatingNavigationBarItem(
                            selected = tab == 1,
                            onClick = { onTab(1) },
                            icon = CoboIcons.MessagesOutline,
                            selectedIcon = CoboIcons.MessagesFilled,
                            label = "消息",
                            modifier = Modifier.testTag("bottom_tab_messages"),
                        )
                        BottomBarItem.Feed -> BottomFloatingNavigationBarItem(
                            selected = tab == 0,
                            onClick = { onFeedTap() },
                            icon = CoboIcons.FeedOutline,
                            selectedIcon = CoboIcons.FeedFilled,
                            label = "微博",
                            modifier = Modifier.testTag("bottom_tab_feed"),
                        )
                        BottomBarItem.Profile -> ProfileFloatingNavItem(
                            selected = tab == 2,
                            onClick = { onTab(2) },
                            avatarUrl = profileAvatarUrl,
                            overflowMenu = profileOverflowMenu,
                            onMenuExpandedChange = { profileMenuExpanded = it },
                            modifier = Modifier.testTag("bottom_tab_profile"),
                        )
                        BottomBarItem.Publish -> BottomFloatingNavigationBarItem(
                            selected = false,
                            onClick = onPublish,
                            icon = COUIIcons.Add,
                            selectedIcon = COUIIcons.Add,
                            label = "发微博",
                            modifier = Modifier.testTag("bottom_publish"),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 悬浮底栏的「我」Tab：支持把图标切换为当前账号头像，并用更靠右的锚点对齐方式弹出切换账号菜单，
 * 避免 COUI 默认按导航项右缘减边距后整张弹窗偏到屏幕左侧。
 */
@Composable
private fun RowScope.ProfileFloatingNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    avatarUrl: String?,
    overflowMenu: (@Composable () -> Unit)?,
    onMenuExpandedChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colorScheme = COUITheme.colorScheme
    val tint = if (selected) colorScheme.onSurface else colorScheme.labelPrimaryVariant
    var menuExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(overflowMenu == null) {
        if (overflowMenu == null) menuExpanded = false
    }
    LaunchedEffect(menuExpanded) {
        onMenuExpandedChange(menuExpanded)
    }
    val density = LocalDensity.current
    val extraRightShiftPx = with(density) { 68.dp.roundToPx() }
    val popupPositionProvider = remember(extraRightShiftPx) {
        profilePopupPositionProvider(extraRightShiftPx)
    }

    Box(
        modifier = modifier
            .height(BottomFloatingNavigationBarDefaults.ItemHeight)
            .widthIn(max = BottomFloatingNavigationBarDefaults.ItemMaxWidth)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onLongClick = if (overflowMenu == null) null else {
                    { menuExpanded = true }
                },
                onClick = onClick,
            )
            .semantics { this.selected = selected },
    ) {
        Column(
            modifier = Modifier.align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(BottomFloatingNavigationBarDefaults.IconTopMargin))
            if (!avatarUrl.isNullOrBlank()) {
                val ringModifier = if (selected) {
                    Modifier.border(1.5.dp, colorScheme.onSurface, CircleShape)
                } else {
                    Modifier
                }
                Avatar(
                    url = avatarUrl,
                    size = BottomFloatingNavigationBarDefaults.IconSize,
                    modifier = ringModifier,
                )
            } else {
                Image(
                    modifier = Modifier.size(BottomFloatingNavigationBarDefaults.IconSize),
                    imageVector = if (selected) CoboIcons.ProfileFilled else CoboIcons.ProfileOutline,
                    contentDescription = "我",
                    colorFilter = ColorFilter.tint(tint),
                )
            }
            Spacer(Modifier.height(BottomFloatingNavigationBarDefaults.IconTitleGapVertical))
            Text(
                text = "我",
                color = tint,
                fontSize = with(density) { BottomFloatingNavigationBarDefaults.LabelFontSize.value.dp.toSp() },
                lineHeight = with(density) { 13.6.dp.toSp() },
                style = COUITheme.textStyles.main.copy(
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Proportional,
                        trim = LineHeightStyle.Trim.None,
                    ),
                ),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
        if (overflowMenu != null) {
            OverlayListPopup(
                show = menuExpanded,
                popupPositionProvider = popupPositionProvider,
                alignment = PopupPositionProvider.Align.BottomEnd,
                maxHeight = NavigationPopupMenuDefaults.MaxHeight,
                minWidth = NavigationPopupMenuDefaults.MinWidth,
                onDismissRequest = { menuExpanded = false },
            ) {
                ListPopupColumn {
                    overflowMenu()
                }
            }
        }
    }
}

private fun profilePopupPositionProvider(extraRightShiftPx: Int): PopupPositionProvider =
    object : PopupPositionProvider {
        private val margins = PaddingValues(
            horizontal = NavigationPopupMenuDefaults.HorizontalMargin,
            vertical = NavigationPopupMenuDefaults.VerticalMargin,
        )

        override fun calculatePosition(
            anchorBounds: IntRect,
            windowBounds: IntRect,
            layoutDirection: LayoutDirection,
            popupContentSize: IntSize,
            popupMargin: IntRect,
            alignment: PopupPositionProvider.Align,
        ): IntOffset {
            val minX = windowBounds.left + popupMargin.left
            val maxX = (windowBounds.right - popupContentSize.width - popupMargin.right)
                .coerceAtLeast(minX)
            val desiredX = anchorBounds.right + extraRightShiftPx - popupContentSize.width
            val minY = windowBounds.top + popupMargin.top
            val maxY = (windowBounds.bottom - popupContentSize.height - popupMargin.bottom)
                .coerceAtLeast(minY)
            val offsetY = (anchorBounds.top - popupContentSize.height - popupMargin.bottom)
                .coerceIn(minY, maxY)
            return IntOffset(
                x = desiredX.coerceIn(minX, maxX),
                y = offsetY,
            )
        }

        override fun getMargins(): PaddingValues = margins
    }

/** 长按悬浮底栏「我」弹出的快捷切换账号菜单。 */
@Composable
private fun ProfileQuickSwitchMenu(
    guest: Boolean,
    onSwitchAccount: (Account) -> Unit,
    onAddAccount: () -> Unit,
    onManageAccounts: () -> Unit,
) {
    val accounts = remember { App.store.accounts() }
    val currentUid = remember { App.repo.session?.uid }
    val currentCookie = remember { App.repo.cookie() }
    val dismiss = LocalDismissState.current
    val showManage = accounts.isNotEmpty()
    val totalCount = accounts.size + 1 + if (showManage) 1 else 0
    val actionIconTint = COUITheme.colorScheme.onSurfaceContainer

    accounts.forEachIndexed { index, account ->
        val isCurrent = !guest && (account.uid == currentUid || account.cookie == currentCookie)
        DropdownImpl(
            item = DropdownItem(
                text = account.name.ifBlank { "微博账号" },
                summary = "UID ${account.uid}",
                icon = { iconModifier ->
                    Box(iconModifier, contentAlignment = Alignment.Center) {
                        Avatar(account.avatar, 28.dp)
                    }
                },
            ),
            optionSize = totalCount,
            isSelected = isCurrent,
            index = index,
            groupKey = "accounts",
            modifier = Modifier.testTag("quick_switch_account_$index"),
            onSelectedIndexChange = {
                dismiss?.invoke()
                if (!isCurrent) onSwitchAccount(account)
            },
        )
    }

    val addIndex = accounts.size
    DropdownImpl(
        item = DropdownItem(
            text = if (guest) "登录 / 添加账号" else "添加账号",
            icon = { iconModifier ->
                Box(iconModifier, contentAlignment = Alignment.Center) {
                    Icon(COUIIcons.Add, null, Modifier.size(20.dp), tint = actionIconTint)
                }
            },
        ),
        optionSize = totalCount,
        isSelected = false,
        index = addIndex,
        groupKey = "actions",
        modifier = Modifier.testTag("quick_switch_add_account"),
        onSelectedIndexChange = {
            dismiss?.invoke()
            onAddAccount()
        },
    )

    if (showManage) {
        DropdownImpl(
            item = DropdownItem(
                text = "账号管理",
                icon = { iconModifier ->
                    Box(iconModifier, contentAlignment = Alignment.Center) {
                        Icon(COUIIcons.Settings, null, Modifier.size(20.dp), tint = actionIconTint)
                    }
                },
            ),
            optionSize = totalCount,
            isSelected = false,
            index = addIndex + 1,
            groupKey = "actions",
            modifier = Modifier.testTag("quick_switch_manage_accounts"),
            onSelectedIndexChange = {
                dismiss?.invoke()
                onManageAccounts()
            },
        )
    }
}

private fun bottomLeftActionIcon(action: BottomLeftAction): ImageVector = when (action) {
    BottomLeftAction.Refresh -> COUIIcons.Refresh
    BottomLeftAction.Search -> COUIIcons.Search
    BottomLeftAction.Favorites -> CoboIcons.Star
}

private fun bottomLeftPopupPositionProvider(): PopupPositionProvider =
    object : PopupPositionProvider {
        private val margins = PaddingValues(
            horizontal = NavigationPopupMenuDefaults.HorizontalMargin,
            vertical = NavigationPopupMenuDefaults.VerticalMargin,
        )

        override fun calculatePosition(
            anchorBounds: IntRect,
            windowBounds: IntRect,
            layoutDirection: LayoutDirection,
            popupContentSize: IntSize,
            popupMargin: IntRect,
            alignment: PopupPositionProvider.Align,
        ): IntOffset {
            val minX = windowBounds.left + popupMargin.left
            val maxX = (windowBounds.right - popupContentSize.width - popupMargin.right)
                .coerceAtLeast(minX)
            val minY = windowBounds.top + popupMargin.top
            val maxY = (windowBounds.bottom - popupContentSize.height - popupMargin.bottom)
                .coerceAtLeast(minY)
            val offsetY = (anchorBounds.top - popupContentSize.height - popupMargin.bottom)
                .coerceIn(minY, maxY)
            return IntOffset(
                x = anchorBounds.left.coerceIn(minX, maxX),
                y = offsetY,
            )
        }

        override fun getMargins(): PaddingValues = margins
    }
