package com.cobo.app.screen

import com.cobo.app.platform.PlatformBackHandler
import com.cobo.app.platform.PlatformContext
import com.cobo.app.platform.WebCookieBridge
import com.cobo.app.platform.currentTimeMillis
import com.cobo.app.platform.appCacheDir
import com.cobo.app.platform.hsvToArgb
import coil3.compose.LocalPlatformContext

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.SingletonImageLoader
import com.cobo.app.App
import com.cobo.app.data.Account
import com.cobo.app.data.BottomLeftAction
import com.cobo.app.data.ContentFilters
import com.cobo.app.data.FeedTabSelection
import com.cobo.app.data.FollowGroup
import com.cobo.app.data.HotBoard
import com.cobo.app.data.ImageBrowseQuality
import com.cobo.app.data.ImageTopBarButton
import com.cobo.app.data.AnimationSpeed
import com.cobo.app.data.LocalBackupBundle
import com.cobo.app.data.MeIconStyle
import com.cobo.app.data.ReadLater
import com.cobo.app.data.RecommendSeenRetention
import com.cobo.app.data.SearchPanelAnimation
import com.cobo.app.data.Updater
import com.cobo.app.data.VideoTopBarButton
import com.cobo.app.data.applyLocalBackup
import com.cobo.app.data.createLocalBackup
import com.cobo.app.data.decodeLocalBackupJson
import com.cobo.app.data.defaultBackupFileName
import com.cobo.app.data.encodeLocalBackupJson
import com.cobo.app.data.feedTabOptions
import com.cobo.app.data.formatBackupSummary
import com.cobo.app.data.formatContentFilterSummary
import com.cobo.app.data.summary
import com.cobo.app.platform.currentTimeMillis
import com.cobo.app.platform.ioDispatcher
import com.cobo.app.platform.rememberJsonExportLauncher
import com.cobo.app.platform.rememberJsonImportLauncher
import com.cobo.app.ui.AccentHueCycle
import com.cobo.app.ui.AccentPresets
import com.cobo.app.ui.AppBarSurface
import com.cobo.app.ui.CoboIcons
import com.cobo.app.ui.DefaultAccentSwatchColor
import com.cobo.app.ui.NavState
import com.cobo.app.ui.PinnedBarDensity
import com.cobo.app.ui.Route
import com.cobo.app.ui.accentColorSummary
import com.cobo.app.ui.blurEffectsEnabled
import com.cobo.app.ui.components.BackIconButton
import com.cobo.app.ui.components.ConfirmDialog
import com.cobo.app.ui.components.SectionTitle
import com.cobo.app.ui.components.navBottomPadding
import com.cobo.app.ui.formatHexColor
import com.cobo.app.ui.glassBarContentTop
import com.cobo.app.ui.hueCycleArgb
import com.cobo.app.ui.isDarkTheme
import com.cobo.app.ui.layerBackdropIf
import com.cobo.app.ui.pageContainerColor
import com.cobo.app.ui.parseHexColor
import com.cobo.app.ui.pinnedHeight
import com.cobo.app.ui.rememberListScrollFraction
import io.github.suqi8.coui.kmp.basic.BindScrollToTop
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.Checkbox
import io.github.suqi8.coui.kmp.basic.DropdownImpl
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.ListPopupColumn
import io.github.suqi8.coui.kmp.basic.LocalEdgeToEdge
import io.github.suqi8.coui.kmp.basic.Slider
import io.github.suqi8.coui.kmp.basic.SliderDefaults
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.TextField
import io.github.suqi8.coui.kmp.basic.TextFieldDefaults
import io.github.suqi8.coui.kmp.basic.TextFieldMode
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.basic.rememberTopAppBarSearchState
import io.github.suqi8.coui.kmp.basic.topAppBarSearchContent
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.layerBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Undo
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.layout.DialogDefaults
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MaxBackupImportBytes = 20L * 1024L * 1024L

/**
 * 设置页分组定义，用于在「我」一级页面展示分组目录，并跳转到对应的二级设置页。
 */
enum class SettingsGroup(val title: String, val summary: String) {
    Display("显示与交互", "外观主题、栏位导航、手势动效与媒体播放"),
    Feed("微博浏览", "主页分栏、卡片样式、用户标识与热搜榜"),
    AccountAndContent("账号与内容", "账号管理、切号展示与本地数据同步"),
    Update("更新", "检查更新、更新日志与自动更新"),
    Other("其他", "清理缓存、本地备份、关于与账号退出"),
}

/**
 * 设置项二级专题页：把原本平铺在一级分组里的同类设置收纳进独立页面，保持主列表清爽。
 */
enum class SettingsSubPage(val title: String, val group: SettingsGroup) {
    AppearanceLayout("外观与界面", SettingsGroup.Display),
    BarsAndNavigation("栏位与导航", SettingsGroup.Display),
    GesturesAndMotion("手势与动效", SettingsGroup.Display),
    MediaPlayback("图片与视频", SettingsGroup.Display),
    FeedCardStyle("卡片与正文样式", SettingsGroup.Feed),
    UserBadges("用户标识显示", SettingsGroup.Feed),
    AccountBehavior("账号切换与展示", SettingsGroup.AccountAndContent),
    LocalContentData("本地数据与同步", SettingsGroup.AccountAndContent),
    ContentLibrary("内容与记录", SettingsGroup.AccountAndContent),
}

@Stable
private class SettingsUiState(context: PlatformContext) {
    val version: String = Updater.currentVersion(context)
    val guest: Boolean = App.repo.guest

    var darkMode by mutableIntStateOf(App.settings.darkMode)
    var accentColor by mutableIntStateOf(App.settings.accentColor)
    var meIconStyle by mutableStateOf(App.settings.meIconStyle)
    var fontScale by mutableStateOf(App.settings.fontScale)
    var autoCheckUpdate by mutableStateOf(App.settings.autoCheckUpdate)
    var acceptPrerelease by mutableStateOf(App.settings.acceptPrerelease)
    var inAppUpdate by mutableStateOf(App.settings.inAppUpdate)
    var blurEffects by mutableStateOf(App.settings.blurEffects)
    var autoHideFloatingBars by mutableStateOf(App.settings.autoHideFloatingBars)
    var feedTabTapToTopAndReturn by mutableStateOf(App.settings.feedTabTapToTopAndReturn)
    var allowLandscape by mutableStateOf(App.settings.allowLandscape)
    var searchPanelAnimation by mutableStateOf(App.settings.searchPanelAnimation)
    var confirmLike by mutableStateOf(App.settings.confirmLike)
    var inAppBrowser by mutableStateOf(App.settings.inAppBrowser)
    var clipboardLinkDetect by mutableStateOf(App.settings.clipboardLinkDetect)
    var quickSwitchAccount by mutableStateOf(App.settings.quickSwitchAccount)
    var profileTabAvatar by mutableStateOf(App.settings.profileTabAvatar)
    var profileShowStatuses by mutableStateOf(App.settings.profileShowStatuses)
    var reverseBrowse by mutableStateOf(App.settings.reverseBrowse)
    var filterSeenRecommend by mutableStateOf(App.settings.filterSeenRecommend)
    var recommendSeenRetentionDays by mutableIntStateOf(App.settings.recommendSeenRetentionDays)
    var collapseFollowGroups by mutableStateOf(App.settings.collapseFollowGroups)
    var feedCardFrame by mutableStateOf(App.settings.feedCardFrame)
    var detailCardFrame by mutableStateOf(App.settings.detailCardFrame)
    var foldLongText by mutableStateOf(App.settings.foldLongText)
    var showVipLevel by mutableStateOf(App.settings.showVipLevel)
    var showFanLevel by mutableStateOf(App.settings.showFanLevel)
    var showVerifiedBadge by mutableStateOf(App.settings.showVerifiedBadge)
    var message by mutableStateOf("")
    var logout by mutableStateOf(false)
    var localFollowUid by mutableStateOf(App.settings.localFollowUid)
    var feedLeftTab by mutableStateOf(App.settings.feedLeftTab)
    var feedCenterTab by mutableStateOf(App.settings.feedCenterTab)
    var feedRightTab by mutableStateOf(App.settings.feedRightTab)
    var feedGroups by mutableStateOf<List<FollowGroup>>(emptyList())
    var pickLocalFollow by mutableStateOf(false)
    var pickFeedLeft by mutableStateOf(false)
    var pickFeedCenter by mutableStateOf(false)
    var pickFeedRight by mutableStateOf(false)
    var pickSearchPanelAnimation by mutableStateOf(false)
    var pickBottomLeftAction by mutableStateOf(false)
    var pickImageBrowseQuality by mutableStateOf(false)
    var pickRecommendSeenRetention by mutableStateOf(false)
    var pickHotBoards by mutableStateOf(false)
    var pickAccentColor by mutableStateOf(false)
    var syncingLocalFavorites by mutableStateOf(false)
    var backupBusy by mutableStateOf(false)
    var pendingImportBackup by mutableStateOf<LocalBackupBundle?>(null)
    var requestExportBackup by mutableStateOf(false)
    var requestImportBackup by mutableStateOf(false)

    fun syncFromSettings() {
        darkMode = App.settings.darkMode
        accentColor = App.settings.accentColor
        meIconStyle = App.settings.meIconStyle
        fontScale = App.settings.fontScale
        autoCheckUpdate = App.settings.autoCheckUpdate
        acceptPrerelease = App.settings.acceptPrerelease
        inAppUpdate = App.settings.inAppUpdate
        blurEffects = App.settings.blurEffects
        autoHideFloatingBars = App.settings.autoHideFloatingBars
        feedTabTapToTopAndReturn = App.settings.feedTabTapToTopAndReturn
        allowLandscape = App.settings.allowLandscape
        searchPanelAnimation = App.settings.searchPanelAnimation
        confirmLike = App.settings.confirmLike
        inAppBrowser = App.settings.inAppBrowser
        clipboardLinkDetect = App.settings.clipboardLinkDetect
        quickSwitchAccount = App.settings.quickSwitchAccount
        profileTabAvatar = App.settings.profileTabAvatar
        profileShowStatuses = App.settings.profileShowStatuses
        reverseBrowse = App.settings.reverseBrowse
        filterSeenRecommend = App.settings.filterSeenRecommend
        recommendSeenRetentionDays = App.settings.recommendSeenRetentionDays
        collapseFollowGroups = App.settings.collapseFollowGroups
        feedCardFrame = App.settings.feedCardFrame
        detailCardFrame = App.settings.detailCardFrame
        foldLongText = App.settings.foldLongText
        showVipLevel = App.settings.showVipLevel
        showFanLevel = App.settings.showFanLevel
        showVerifiedBadge = App.settings.showVerifiedBadge
        localFollowUid = App.settings.localFollowUid
        feedLeftTab = App.settings.feedLeftTab
        feedCenterTab = App.settings.feedCenterTab
        feedRightTab = App.settings.feedRightTab
    }
}

@Composable
private fun rememberSettingsUiState(): SettingsUiState {
    val context = App.context
    val state = remember { SettingsUiState(context) }
    // 关注分组是账号里的数据：只在真的要选栏位内容时才去网页版拉一次，拉不到就只列内置流。
    LaunchedEffect(state.pickFeedLeft, state.pickFeedCenter, state.pickFeedRight) {
        if (state.guest || !(state.pickFeedLeft || state.pickFeedCenter || state.pickFeedRight)) {
            return@LaunchedEffect
        }
        runCatching { App.repo.followGroups() }.onSuccess { state.feedGroups = it }
    }
    return state
}

@Composable
fun SettingsScreen(nav: NavState) {
    val context = App.context
    val scope = rememberCoroutineScope()
    val state = rememberSettingsUiState()

    val groups = buildAllSettingsGroups(
        nav = nav,
        state = state,
        scope = scope,
        context = context,
        includeLibraryShortcuts = true,
    )
    val searchGroups = buildAllSearchableSettingsGroups(
        nav = nav,
        state = state,
        scope = scope,
        context = context,
        includeLibraryShortcuts = true,
    )

    SettingsScaffold(
        nav = nav,
        title = "设置",
        state = state,
        groups = groups,
        searchGroups = searchGroups,
        showGroupHeaders = true,
        footerContent = {
            if (state.message.isNotBlank()) {
                item(key = "settings-message") {
                    Text(
                        state.message,
                        fontSize = 12.sp,
                        color = COUITheme.colorScheme.onSurfaceSecondary,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                }
            }
            item(key = "settings-disclaimer") {
                Text(
                    "基于 m.weibo.cn Web API 的第三方微博客户端。\n仅用于个人学习与研究。",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = COUITheme.colorScheme.onSurfaceSecondary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                )
            }
        },
    )
}

/**
 * 分组二级设置页：从「我」一级页面点进具体设置分组时使用，复用同一套设置项与弹窗。
 */
@Composable
fun SettingsGroupScreen(nav: NavState, group: SettingsGroup) {
    val context = App.context
    val scope = rememberCoroutineScope()
    val state = rememberSettingsUiState()

    val groups = when (group) {
        SettingsGroup.Display -> displayOverviewSections(nav = nav, state = state)
        SettingsGroup.Feed -> feedOverviewSections(nav = nav, state = state)
        SettingsGroup.AccountAndContent -> listOf(
            SettingsGroupSection(
                key = "account",
                title = group.title,
                entries = accountOverviewEntries(
                    nav = nav,
                    state = state,
                    includeLibraryShortcuts = false,
                ),
                group = group,
            ),
        )
        SettingsGroup.Update -> listOf(
            SettingsGroupSection(
                key = "update",
                title = group.title,
                entries = updateSettingsEntries(nav = nav, state = state, scope = scope),
                group = group,
            ),
        )
        SettingsGroup.Other -> listOf(
            SettingsGroupSection(
                key = "other",
                title = group.title,
                entries = otherSettingsEntries(nav = nav, state = state, context = context),
                group = group,
            ),
            SettingsGroupSection(
                key = "other_logout",
                title = group.title,
                entries = logoutSettingsEntries(nav = nav, state = state),
                headerGroupKey = "other",
                group = group,
            ),
        )
    }

    val searchGroups = when (group) {
        SettingsGroup.Display -> listOf(
            SettingsGroupSection(
                key = "display_all",
                title = group.title,
                entries = displayAllSearchableEntries(nav = nav, state = state),
                group = group,
            ),
        )
        SettingsGroup.Feed -> listOf(
            SettingsGroupSection(
                key = "feed_all",
                title = group.title,
                entries = feedAllSearchableEntries(nav = nav, state = state),
                group = group,
            ),
        )
        SettingsGroup.AccountAndContent -> listOf(
            SettingsGroupSection(
                key = "account_all",
                title = group.title,
                entries = accountAllSearchableEntries(
                    nav = nav,
                    state = state,
                    scope = scope,
                    includeLibraryShortcuts = false,
                ),
                group = group,
            ),
        )
        SettingsGroup.Update, SettingsGroup.Other -> groups
    }

    SettingsScaffold(
        nav = nav,
        title = group.title,
        state = state,
        groups = groups,
        searchGroups = searchGroups,
        showGroupHeaders = false,
        footerContent = {
            if (group == SettingsGroup.Other && state.message.isNotBlank()) {
                item(key = "settings-message") {
                    Text(
                        state.message,
                        fontSize = 12.sp,
                        color = COUITheme.colorScheme.onSurfaceSecondary,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                }
            }
        },
    )
}

/**
 * 专题子设置页：承接从「显示与交互」「微博浏览」「账号与内容」拆出的二级设置页面。
 */
@Composable
fun SettingsSubPageScreen(nav: NavState, page: SettingsSubPage) {
    val scope = rememberCoroutineScope()
    val state = rememberSettingsUiState()

    val entries = when (page) {
        SettingsSubPage.AppearanceLayout -> displayAppearanceSubEntries(state = state)
        SettingsSubPage.BarsAndNavigation -> displayBarsSubEntries(state = state)
        SettingsSubPage.GesturesAndMotion -> displayGesturesSubEntries(nav = nav, state = state)
        SettingsSubPage.MediaPlayback -> displayMediaSubEntries(nav = nav, state = state)
        SettingsSubPage.FeedCardStyle -> feedCardStyleSubEntries(state = state)
        SettingsSubPage.UserBadges -> feedUserBadgesSubEntries(state = state)
        SettingsSubPage.AccountBehavior -> accountBehaviorSubEntries(state = state)
        SettingsSubPage.LocalContentData -> localContentDataSubEntries(state = state, scope = scope)
        SettingsSubPage.ContentLibrary -> contentLibrarySubEntries(nav = nav, state = state)
    }

    val groups = listOf(
        SettingsGroupSection(
            key = "sub_${page.name}",
            title = page.title,
            entries = entries,
            group = page.group,
        ),
    )

    SettingsScaffold(
        nav = nav,
        title = page.title,
        state = state,
        groups = groups,
        showGroupHeaders = false,
    )
}

@Composable
private fun buildAllSettingsGroups(
    nav: NavState,
    state: SettingsUiState,
    scope: CoroutineScope,
    context: PlatformContext,
    includeLibraryShortcuts: Boolean,
): List<SettingsGroupSection> = buildList {
    addAll(displayOverviewSections(nav = nav, state = state))
    addAll(feedOverviewSections(nav = nav, state = state))
    add(
        SettingsGroupSection(
            key = "account",
            title = SettingsGroup.AccountAndContent.title,
            entries = accountOverviewEntries(
                nav = nav,
                state = state,
                includeLibraryShortcuts = includeLibraryShortcuts,
            ),
            group = SettingsGroup.AccountAndContent,
        ),
    )
    add(
        SettingsGroupSection(
            key = "update",
            title = SettingsGroup.Update.title,
            entries = updateSettingsEntries(nav = nav, state = state, scope = scope),
            group = SettingsGroup.Update,
        ),
    )
    add(
        SettingsGroupSection(
            key = "other",
            title = SettingsGroup.Other.title,
            entries = otherSettingsEntries(nav = nav, state = state, context = context),
            group = SettingsGroup.Other,
        ),
    )
    // 退出是破坏性操作，单独一张卡，不和其他条目挤在一起；搜索结果态按 headerGroupKey 合并到「其他」卡片。
    add(
        SettingsGroupSection(
            key = "other_logout",
            title = SettingsGroup.Other.title,
            entries = logoutSettingsEntries(nav = nav, state = state),
            headerGroupKey = "other",
            group = SettingsGroup.Other,
        ),
    )
}

@Composable
private fun buildAllSearchableSettingsGroups(
    nav: NavState,
    state: SettingsUiState,
    scope: CoroutineScope,
    context: PlatformContext,
    includeLibraryShortcuts: Boolean,
): List<SettingsGroupSection> = listOf(
    SettingsGroupSection(
        key = "display",
        title = SettingsGroup.Display.title,
        entries = displayAllSearchableEntries(nav = nav, state = state),
        group = SettingsGroup.Display,
    ),
    SettingsGroupSection(
        key = "feed",
        title = SettingsGroup.Feed.title,
        entries = feedAllSearchableEntries(nav = nav, state = state),
        group = SettingsGroup.Feed,
    ),
    SettingsGroupSection(
        key = "account",
        title = SettingsGroup.AccountAndContent.title,
        entries = accountAllSearchableEntries(
            nav = nav,
            state = state,
            scope = scope,
            includeLibraryShortcuts = includeLibraryShortcuts,
        ),
        group = SettingsGroup.AccountAndContent,
    ),
    SettingsGroupSection(
        key = "update",
        title = SettingsGroup.Update.title,
        entries = updateSettingsEntries(nav = nav, state = state, scope = scope),
        group = SettingsGroup.Update,
    ),
    SettingsGroupSection(
        key = "other",
        title = SettingsGroup.Other.title,
        entries = otherSettingsEntries(nav = nav, state = state, context = context),
        group = SettingsGroup.Other,
    ),
    SettingsGroupSection(
        key = "other_logout",
        title = SettingsGroup.Other.title,
        entries = logoutSettingsEntries(nav = nav, state = state),
        headerGroupKey = "other",
        group = SettingsGroup.Other,
    ),
)

@Composable
private fun SettingsScaffold(
    nav: NavState,
    title: String,
    state: SettingsUiState,
    groups: List<SettingsGroupSection>,
    searchGroups: List<SettingsGroupSection> = groups,
    showGroupHeaders: Boolean,
    footerContent: LazyListScope.() -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val exportLauncher = rememberJsonExportLauncher { result ->
        state.backupBusy = false
        result
            ?.onSuccess { detail -> App.toast("已导出本地备份（$detail）") }
            ?.onFailure { err -> App.toast(err.message ?: "导出备份失败") }
    }
    val importLauncher = rememberJsonImportLauncher(maxBytes = MaxBackupImportBytes) { result ->
        if (result == null) {
            state.backupBusy = false
            return@rememberJsonImportLauncher
        }
        scope.launch {
            runCatching {
                val raw = result.getOrThrow()
                withContext(ioDispatcher) { decodeLocalBackupJson(raw) }
            }.onSuccess { bundle ->
                state.pendingImportBackup = bundle
            }.onFailure { err ->
                App.toast(err.message ?: "解析备份文件失败")
            }
            state.backupBusy = false
        }
    }
    LaunchedEffect(state.requestExportBackup) {
        if (!state.requestExportBackup) return@LaunchedEffect
        state.requestExportBackup = false
        state.backupBusy = true
        runCatching {
            withContext(ioDispatcher) {
                val bundle = createLocalBackup(App.settings, App.store, state.version)
                Triple(
                    defaultBackupFileName(currentTimeMillis()),
                    encodeLocalBackupJson(bundle),
                    formatBackupSummary(bundle.summary()),
                )
            }
        }.onSuccess { (fileName, encoded, detail) ->
            exportLauncher(fileName, encoded, detail)
        }.onFailure { err ->
            state.backupBusy = false
            App.toast(err.message ?: "导出备份失败")
        }
    }
    LaunchedEffect(state.requestImportBackup) {
        if (!state.requestImportBackup) return@LaunchedEffect
        state.requestImportBackup = false
        state.backupBusy = true
        importLauncher()
    }

    val pageColor = pageContainerColor()
    // 顶栏玻璃要有东西可采样：把列表录进 backdrop，顶栏浮在上面做渐变模糊。
    val backdrop = rememberLayerBackdrop()
    val topBarHeight = pinnedHeight(TopAppBarDefaults.SmallTopAppBarCenterHeight)
    val mainListState = rememberLazyListState()
    val searchListState = rememberLazyListState()

    var searchQuery by rememberSaveable(title) { mutableStateOf("") }
    var searchExpanded by rememberSaveable(title) { mutableStateOf(false) }
    val trimmedQuery = searchQuery.trim()
    val inSearchResults = trimmedQuery.isNotEmpty()
    val searchTerms = remember(trimmedQuery) { parseSettingsSearchTerms(trimmedQuery) }
    val filteredGroups = filterSettingsGroups(searchGroups, searchTerms)
    val searchTransition = rememberTopAppBarSearchState(active = inSearchResults)

    val activeListState = if (inSearchResults) searchListState else mainListState
    BindScrollToTop(activeListState)

    LaunchedEffect(trimmedQuery) {
        if (inSearchResults && (searchListState.firstVisibleItemIndex != 0 || searchListState.firstVisibleItemScrollOffset != 0)) {
            searchListState.scrollToItem(0)
        }
    }

    PlatformBackHandler(enabled = searchExpanded) {
        searchQuery = ""
        searchExpanded = false
    }

    Box(Modifier.fillMaxSize().background(pageColor)) {
        Box(Modifier.fillMaxSize().layerBackdropIf(state.blurEffects, backdrop).background(pageColor)) {
            if (searchTransition.isContentVisible) {
                LazyColumn(
                    state = mainListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .topAppBarSearchContent(searchTransition)
                        .scrollEndHaptic()
                        .overScrollVertical(),
                    // 列表铺到屏幕最底，滚到末页时卡片从系统导航条下方穿过（沉浸），
                    // 末项靠 contentPadding 抬到导航条之上，不会被挡住。
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        top = glassBarContentTop(topBarHeight, 10.dp),
                        end = 12.dp,
                        bottom = 24.dp + navBottomPadding(),
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val shownHeaders = HashSet<String>()
                    groups.forEach { group ->
                        if (showGroupHeaders && shownHeaders.add(group.headerGroupKey)) {
                            val isFirstHeader = shownHeaders.size == 1
                            item(key = "${group.headerGroupKey}-title") {
                                SectionTitle(
                                    text = group.title,
                                    topPadding = if (isFirstHeader) 4.dp else 14.dp,
                                )
                            }
                        }
                        item(key = group.key) {
                            SettingsGroupCard(entries = group.entries)
                        }
                    }
                    footerContent()
                }
            }

            SettingsSearchResultsLayer(
                visible = inSearchResults,
                query = trimmedQuery,
                terms = searchTerms,
                filteredGroups = filteredGroups,
                parentTitle = title,
                listState = searchListState,
                topBarHeight = topBarHeight,
            )
        }

        CompositionLocalProvider(
            LocalBackdrop provides if (state.blurEffects) backdrop else null,
            // 内容铺到状态栏下面，顶栏玻璃才有东西可采样（COUI 17 规范要求 edgeToEdge）。
            LocalEdgeToEdge provides true,
        ) {
            SettingsSearchTopBar(
                title = title,
                query = searchQuery,
                expanded = searchExpanded,
                onQueryChange = { searchQuery = it },
                onExpandedChange = { searchExpanded = it },
                onBack = nav::pop,
                listState = activeListState,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }

    SettingsDialogs(nav = nav, state = state)
}

@Composable
private fun displayCoreEntries(
    state: SettingsUiState,
): List<SettingsEntry> {
    val darkSummary = when (state.darkMode) {
        1 -> "浅色"
        2 -> "深色"
        else -> "跟随系统"
    }
    val accentSummary = accentColorSummary(state.accentColor)
    val fontSummary = "${(state.fontScale * 100).toInt()}%"
    return listOf(
        SettingsEntry(
            id = "dark_mode",
            title = "深色模式",
            summary = darkSummary,
            keywords = "暗黑 夜间 黑夜 主题 浅色 日间 跟随系统 dark theme night",
        ) { pos ->
            SettingsArrowRow(
                title = "深色模式",
                modifier = Modifier.testTag("settings_dark"),
                summary = darkSummary,
                cardListPosition = pos,
                onClick = { state.darkMode = App.settings.cycleDarkMode() },
            )
        },
        SettingsEntry(
            id = "accent_color",
            title = "强调色",
            summary = accentSummary,
            keywords = "主题色 配色 颜色 色彩 accent color",
        ) { pos ->
            SettingsArrowRow(
                title = "强调色",
                modifier = Modifier.testTag("settings_accent_color"),
                summary = accentSummary,
                endActions = {
                    Box(
                        Modifier
                            .padding(end = 6.dp)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(COUITheme.colorScheme.primary)
                            .border(1.dp, COUITheme.colorScheme.outline, CircleShape),
                    )
                },
                cardListPosition = pos,
                onClick = { state.pickAccentColor = true },
            )
        },
        SettingsEntry(
            id = "font_scale",
            title = "字号",
            summary = fontSummary,
            keywords = "字体 大小 缩放 font size scale",
        ) { pos ->
            SettingsArrowRow(
                title = "字号",
                modifier = Modifier.testTag("settings_font"),
                summary = fontSummary,
                cardListPosition = pos,
                onClick = { state.fontScale = App.settings.cycleFontScale() },
            )
        },
    )
}

@Composable
private fun displayAppearanceSubEntries(
    state: SettingsUiState,
): List<SettingsEntry> {
    val profileStatusesSummary = if (state.profileShowStatuses) {
        "开启后在「我」标签页直接展开我的全部微博"
    } else {
        "关闭后「我」标签页作为互动中心与设置一级入口"
    }
    return listOf(
        SettingsEntry(
            id = "me_icon_style",
            title = "「我」页图标样式",
            summary = state.meIconStyle.label,
            keywords = "彩色 单色 主题色 图标 icon",
        ) { pos ->
            SettingsDropdownRow(
                items = MeIconStyle.entries.map { it.label },
                selectedIndex = state.meIconStyle.ordinal,
                title = "「我」页图标样式",
                modifier = Modifier.testTag("settings_me_icon_style"),
                cardListPosition = pos,
                onSelectedIndexChange = { index ->
                    val picked = MeIconStyle.entries.getOrElse(index) { MeIconStyle.Colorful }
                    state.meIconStyle = picked
                    App.settings.applyMeIconStyle(picked)
                },
            )
        },
        SettingsEntry(
            id = "blur_effects",
            title = "模糊效果",
            summary = "关闭后顶栏与玻璃面改画纯色，滑动更省电",
            keywords = "毛玻璃 高斯 透明 玻璃 省电 blur glass",
        ) { pos ->
            SettingsSwitchRow(
                checked = state.blurEffects,
                onCheckedChange = {
                    state.blurEffects = it
                    App.settings.applyBlurEffects(it)
                },
                title = "模糊效果",
                summary = "关闭后顶栏与玻璃面改画纯色，滑动更省电",
                modifier = Modifier.testTag("settings_blur"),
                cardListPosition = pos,
            )
        },
        SettingsEntry(
            id = "allow_landscape",
            title = "允许横屏",
            summary = "关闭后固定竖屏，不再进入左右双页",
            keywords = "旋转 竖屏 双页 分栏 平板 landscape rotate",
        ) { pos ->
            SettingsSwitchRow(
                checked = state.allowLandscape,
                onCheckedChange = {
                    state.allowLandscape = it
                    App.settings.applyAllowLandscape(it)
                },
                title = "允许横屏",
                summary = "关闭后固定竖屏，不再进入左右双页",
                modifier = Modifier.testTag("settings_allow_landscape"),
                cardListPosition = pos,
            )
        },
        SettingsEntry(
            id = "profile_show_statuses",
            title = "「我」页展示我的微博",
            summary = profileStatusesSummary,
            keywords = "个人主页 互动中心 设置入口 布局 profile",
        ) { pos ->
            SettingsSwitchRow(
                checked = state.profileShowStatuses,
                onCheckedChange = {
                    state.profileShowStatuses = it
                    App.settings.applyProfileShowStatuses(it)
                },
                title = "「我」页展示我的微博",
                summary = profileStatusesSummary,
                modifier = Modifier.testTag("settings_profile_show_statuses"),
                cardListPosition = pos,
            )
        },
    )
}

@Composable
private fun displayBarsSubEntries(
    state: SettingsUiState,
): List<SettingsEntry> = listOf(
    SettingsEntry(
        id = "bottom_left_action",
        title = "底栏左下角按钮",
        summary = App.settings.bottomLeftAction.label,
        keywords = "底部 悬浮栏 快捷 按钮 搜索 刷新 bottom",
    ) { pos ->
        SettingsArrowRow(
            title = "底栏左下角按钮",
            modifier = Modifier.testTag("settings_bottom_left_action"),
            summary = App.settings.bottomLeftAction.label,
            cardListPosition = pos,
            onClick = { state.pickBottomLeftAction = true },
        )
    },
    SettingsEntry(
        id = "auto_hide_floating_bars",
        title = "滑动自动隐藏浮动栏",
        summary = "向下浏览时自动收起顶部分段栏、悬浮底栏与详情页底部工具栏，向上滑动或回到顶部时恢复",
        keywords = "顶栏 底栏 工具栏 沉浸 隐藏 收起 float bar",
    ) { pos ->
        SettingsSwitchRow(
            checked = state.autoHideFloatingBars,
            onCheckedChange = {
                state.autoHideFloatingBars = it
                App.settings.applyAutoHideFloatingBars(it)
            },
            title = "滑动自动隐藏浮动栏",
            summary = "向下浏览时自动收起顶部分段栏、悬浮底栏与详情页底部工具栏，向上滑动或回到顶部时恢复",
            modifier = Modifier.testTag("settings_auto_hide_floating_bars"),
            cardListPosition = pos,
        )
    },
    SettingsEntry(
        id = "feed_tab_tap_to_top_and_return",
        title = "底栏「微博」单击回顶与返回",
        summary = "开启后在微博页单击回顶，再点返回原浏览位置，双击刷新；关闭时保持双击回顶",
        keywords = "回到顶部 刷新 原位置 双击 top return",
    ) { pos ->
        SettingsSwitchRow(
            checked = state.feedTabTapToTopAndReturn,
            onCheckedChange = {
                state.feedTabTapToTopAndReturn = it
                App.settings.applyFeedTabTapToTopAndReturn(it)
            },
            title = "底栏「微博」单击回顶与返回",
            summary = "开启后在微博页单击回顶，再点返回原浏览位置，双击刷新；关闭时保持双击回顶",
            modifier = Modifier.testTag("settings_feed_tab_tap_to_top_and_return"),
            cardListPosition = pos,
        )
    },
)

@Composable
private fun displayGesturesSubEntries(
    nav: NavState,
    state: SettingsUiState,
): List<SettingsEntry> {
    val predictiveSummary = if (App.settings.predictiveBack) "已开启" else "已关闭"
    return listOf(
        SettingsEntry(
            id = "predictive_back",
            title = "预测性返回手势设置",
            summary = predictiveSummary,
            keywords = "侧滑 返回 手势 轨迹 模糊 跟手 predictive back gesture",
        ) { pos ->
            SettingsArrowRow(
                title = "预测性返回手势设置",
                summary = predictiveSummary,
                modifier = Modifier.testTag("settings_predictive_back_entry"),
                cardListPosition = pos,
                onClick = { nav.push(Route.PredictiveBackSettings) },
            )
        },
        SettingsEntry(
            id = "animation_speed",
            title = "动画速度",
            summary = "调节微博展开、看图与页面切换的动画快慢",
            keywords = "快慢 动效 展开 切换 速度 animation speed",
        ) { pos ->
            SettingsDropdownRow(
                items = AnimationSpeed.entries.map { it.label },
                selectedIndex = App.settings.animationSpeed.ordinal,
                title = "动画速度",
                summary = "调节微博展开、看图与页面切换的动画快慢",
                modifier = Modifier.testTag("settings_animation_speed"),
                cardListPosition = pos,
                onSelectedIndexChange = { index ->
                    val picked = AnimationSpeed.entries.getOrElse(index) { AnimationSpeed.Standard }
                    App.settings.applyAnimationSpeed(picked)
                },
            )
        },
        SettingsEntry(
            id = "search_panel_animation",
            title = "搜索面板动画",
            summary = state.searchPanelAnimation.label,
            keywords = "搜索 弹出 动效 样式 search animation",
        ) { pos ->
            SettingsArrowRow(
                title = "搜索面板动画",
                modifier = Modifier.testTag("settings_search_panel_anim"),
                summary = state.searchPanelAnimation.label,
                cardListPosition = pos,
                onClick = { state.pickSearchPanelAnimation = true },
            )
        },
    )
}

@Composable
private fun displayMediaSubEntries(
    nav: NavState,
    state: SettingsUiState,
): List<SettingsEntry> = listOf(
    SettingsEntry(
        id = "media_top_bar",
        title = "媒体顶栏",
        summary = "自定义视频播放与看图页顶栏按钮显示与排序",
        keywords = "媒体顶栏 视频顶栏 顶栏 按钮 排序 视频 看图 原图 保存 分享 小窗 画中画 倍速 浏览器 topbar",
    ) { pos ->
        SettingsArrowRow(
            title = "媒体顶栏",
            modifier = Modifier.testTag("settings_media_top_bar"),
            summary = "视频 ${App.settings.videoTopBarButtons.size} 项 · 看图 ${App.settings.imageTopBarButtons.size} 项",
            cardListPosition = pos,
            onClick = { nav.push(Route.MediaTopBarSettings) },
        )
    },
    SettingsEntry(
        id = "image_browse_quality",
        title = "浏览图片画质选择",
        summary = App.settings.imageBrowseQuality.label,
        keywords = "浏览图片 画质 原图 高清 缩略图 流量 省流 看图 image quality",
    ) { pos ->
        SettingsArrowRow(
            title = "浏览图片画质选择",
            modifier = Modifier.testTag("settings_image_browse_quality"),
            summary = App.settings.imageBrowseQuality.label,
            cardListPosition = pos,
            onClick = { state.pickImageBrowseQuality = true },
        )
    },
    SettingsEntry(
        id = "live_photo_auto_play",
        title = "实况图自动播放",
        summary = "进入或滑动到实况图时自动播放一次，结束后仅支持长按重播",
        keywords = "实况 动态图 播放 live photo livephoto",
    ) { pos ->
        SettingsSwitchRow(
            checked = App.settings.livePhotoAutoPlay,
            onCheckedChange = { App.settings.applyLivePhotoAutoPlay(it) },
            title = "实况图自动播放",
            summary = "进入或滑动到实况图时自动播放一次，结束后仅支持长按重播",
            modifier = Modifier.testTag("settings_live_photo_auto_play"),
            cardListPosition = pos,
        )
    },
    SettingsEntry(
        id = "live_photo_sound",
        title = "实况图播放声音",
        summary = "播放实况图时同步播放附带的视频声音",
        keywords = "实况 静音 音频 音量 声音 live photo sound",
    ) { pos ->
        SettingsSwitchRow(
            checked = App.settings.livePhotoSound,
            onCheckedChange = { App.settings.applyLivePhotoSound(it) },
            title = "实况图播放声音",
            summary = "播放实况图时同步播放附带的视频声音",
            modifier = Modifier.testTag("settings_live_photo_sound"),
            cardListPosition = pos,
        )
    },
    SettingsEntry(
        id = "feed_video_auto_play",
        title = "信息流视频自动播放",
        summary = "仅在 Wi-Fi 下静音预览屏幕中的短视频，点击可进入全屏播放",
        keywords = "视频 自动播放 静音 wifi 预览 video autoplay",
    ) { pos ->
        SettingsSwitchRow(
            checked = App.settings.feedVideoAutoPlay,
            onCheckedChange = { App.settings.applyFeedVideoAutoPlay(it) },
            title = "信息流视频自动播放",
            summary = "仅在 Wi-Fi 下静音预览屏幕中的短视频，点击可进入全屏播放",
            modifier = Modifier.testTag("settings_feed_video_auto_play"),
            cardListPosition = pos,
        )
    },
    SettingsEntry(
        id = "video_auto_float_on_back",
        title = "视频播放时返回自动悬浮",
        summary = "播放中按返回自动切换应用内小窗，切至后台自动进入系统画中画",
        keywords = "小窗 画中画 悬浮窗 后台 播放 video pip",
    ) { pos ->
        SettingsSwitchRow(
            checked = App.settings.videoAutoFloatOnBack,
            onCheckedChange = { App.settings.applyVideoAutoFloatOnBack(it) },
            title = "视频播放时返回自动悬浮",
            summary = "播放中按返回自动切换应用内小窗，切至后台自动进入系统画中画",
            modifier = Modifier.testTag("settings_video_auto_float_on_back"),
            cardListPosition = pos,
        )
    },
    SettingsEntry(
        id = "video_auto_close_on_complete",
        title = "视频播完自动返回",
        summary = "非直播视频播放结束后自动关闭并返回上一页",
        keywords = "播放结束 自动关闭 退出 video",
    ) { pos ->
        SettingsSwitchRow(
            checked = App.settings.videoAutoCloseOnComplete,
            onCheckedChange = { App.settings.applyVideoAutoCloseOnComplete(it) },
            title = "视频播完自动返回",
            summary = "非直播视频播放结束后自动关闭并返回上一页",
            modifier = Modifier.testTag("settings_video_auto_close_on_complete"),
            cardListPosition = pos,
        )
    },
    SettingsEntry(
        id = "video_center_swipe_actions",
        title = "视频中区滑动手势",
        summary = "开启后中区上滑呼出小窗与画中画、下滑关闭；顶栏按钮可在「媒体顶栏」中自定义",
        keywords = "小窗 画中画 上滑 下滑 手势 video pip gesture",
    ) { pos ->
        SettingsSwitchRow(
            checked = App.settings.videoCenterSwipeActions,
            onCheckedChange = { App.settings.applyVideoCenterSwipeActions(it) },
            title = "视频中区滑动手势",
            summary = "开启后中区上滑呼出小窗与画中画、下滑关闭；顶栏按钮可在「媒体顶栏」中自定义",
            modifier = Modifier.testTag("settings_video_center_swipe_actions"),
            cardListPosition = pos,
        )
    },
)

@Composable
private fun displayGeneralInteractionEntries(
    state: SettingsUiState,
): List<SettingsEntry> = listOf(
    SettingsEntry(
        id = "in_app_browser",
        title = "使用内置浏览器打开外部网页",
        summary = "关闭后点击外部链接将直接调用系统浏览器打开",
        keywords = "外链 网页 浏览器 系统浏览器 web browser",
    ) { pos ->
        SettingsSwitchRow(
            checked = state.inAppBrowser,
            onCheckedChange = {
                state.inAppBrowser = it
                App.settings.applyInAppBrowser(it)
            },
            title = "使用内置浏览器打开外部网页",
            summary = "关闭后点击外部链接将直接调用系统浏览器打开",
            modifier = Modifier.testTag("settings_in_app_browser"),
            cardListPosition = pos,
        )
    },
    SettingsEntry(
        id = "clipboard_link_detect",
        title = "识别剪贴板微博链接",
        summary = "切回应用时检测剪贴板中的微博链接并提示打开",
        keywords = "剪贴板 链接 自动识别 跳转 复制 clipboard",
    ) { pos ->
        SettingsSwitchRow(
            checked = state.clipboardLinkDetect,
            onCheckedChange = {
                state.clipboardLinkDetect = it
                App.settings.applyClipboardLinkDetect(it)
                if (!it) App.dismissClipboardLink()
            },
            title = "识别剪贴板微博链接",
            summary = "切回应用时检测剪贴板中的微博链接并提示打开",
            modifier = Modifier.testTag("settings_clipboard_link_detect"),
            cardListPosition = pos,
        )
    },
    SettingsEntry(
        id = "confirm_like",
        title = "点赞前确认",
        keywords = "误触 确认 弹窗 红心 喜欢 like confirm",
    ) { pos ->
        SettingsSwitchRow(
            checked = state.confirmLike,
            onCheckedChange = {
                state.confirmLike = it
                App.settings.applyConfirmLike(it)
            },
            title = "点赞前确认",
            modifier = Modifier.testTag("settings_confirm_like"),
            cardListPosition = pos,
        )
    },
)

@Composable
private fun displayOverviewSections(
    nav: NavState,
    state: SettingsUiState,
): List<SettingsGroupSection> {
    val visualEntries = displayCoreEntries(state) + SettingsEntry(
        id = "sub_appearance_layout",
        title = SettingsSubPage.AppearanceLayout.title,
        summary = "图标样式、模糊效果、横屏与「我」页布局",
        keywords = "外观 图标 模糊 毛玻璃 横屏 布局",
    ) { pos ->
        SettingsArrowRow(
            title = SettingsSubPage.AppearanceLayout.title,
            summary = "图标样式、模糊效果、横屏与「我」页布局",
            modifier = Modifier.testTag("settings_sub_appearance_layout"),
            cardListPosition = pos,
            onClick = { nav.push(Route.SettingsSubDetail(SettingsSubPage.AppearanceLayout)) },
        )
    }
    val interactionEntries = listOf(
        SettingsEntry(
            id = "sub_bars_navigation",
            title = SettingsSubPage.BarsAndNavigation.title,
            summary = "底栏按钮、自动隐藏浮动栏与单击回顶",
            keywords = "底栏 导航 浮动栏 回顶",
        ) { pos ->
            SettingsArrowRow(
                title = SettingsSubPage.BarsAndNavigation.title,
                summary = "底栏按钮、自动隐藏浮动栏与单击回顶",
                modifier = Modifier.testTag("settings_sub_bars_navigation"),
                cardListPosition = pos,
                onClick = { nav.push(Route.SettingsSubDetail(SettingsSubPage.BarsAndNavigation)) },
            )
        },
        SettingsEntry(
            id = "sub_gestures_motion",
            title = SettingsSubPage.GesturesAndMotion.title,
            summary = "预测性返回、动画速度与搜索面板动画",
            keywords = "手势 返回 动画 速度 搜索",
        ) { pos ->
            SettingsArrowRow(
                title = SettingsSubPage.GesturesAndMotion.title,
                summary = "预测性返回、动画速度与搜索面板动画",
                modifier = Modifier.testTag("settings_sub_gestures_motion"),
                cardListPosition = pos,
                onClick = { nav.push(Route.SettingsSubDetail(SettingsSubPage.GesturesAndMotion)) },
            )
        },
        SettingsEntry(
            id = "sub_media_playback",
            title = SettingsSubPage.MediaPlayback.title,
            summary = "媒体顶栏、图片画质、实况图与视频播放交互",
            keywords = "媒体顶栏 图片 画质 原图 实况 视频 小窗 画中画",
        ) { pos ->
            SettingsArrowRow(
                title = SettingsSubPage.MediaPlayback.title,
                summary = "媒体顶栏、图片画质、实况图与视频播放交互",
                modifier = Modifier.testTag("settings_sub_media_playback"),
                cardListPosition = pos,
                onClick = { nav.push(Route.SettingsSubDetail(SettingsSubPage.MediaPlayback)) },
            )
        },
    ) + displayGeneralInteractionEntries(state)

    return listOf(
        SettingsGroupSection(
            key = "display_visual",
            title = SettingsGroup.Display.title,
            entries = visualEntries,
            headerGroupKey = "display",
            group = SettingsGroup.Display,
        ),
        SettingsGroupSection(
            key = "display_interaction",
            title = SettingsGroup.Display.title,
            entries = interactionEntries,
            headerGroupKey = "display",
            group = SettingsGroup.Display,
        ),
    )
}

@Composable
private fun displayAllSearchableEntries(
    nav: NavState,
    state: SettingsUiState,
): List<SettingsEntry> =
    displayCoreEntries(state) +
        displayAppearanceSubEntries(state) +
        displayBarsSubEntries(state) +
        displayGesturesSubEntries(nav, state) +
        displayMediaSubEntries(nav, state) +
        displayGeneralInteractionEntries(state)

@Composable
private fun feedTabsSubEntries(
    state: SettingsUiState,
): List<SettingsEntry> = listOf(
    SettingsEntry(
        id = "feed_left_tab",
        title = "微博左侧栏",
        summary = state.feedLeftTab.label,
        keywords = "主页 分栏 顶栏 标签 关注 推荐 好友圈 tab",
    ) { pos ->
        SettingsArrowRow(
            title = "微博左侧栏",
            modifier = Modifier.testTag("settings_feed_left_tab"),
            summary = state.feedLeftTab.label,
            cardListPosition = pos,
            onClick = { state.pickFeedLeft = true },
        )
    },
    SettingsEntry(
        id = "feed_center_tab",
        title = "微博中间栏",
        summary = state.feedCenterTab.label,
        keywords = "主页 分栏 顶栏 标签 关注 推荐 全部 tab",
    ) { pos ->
        SettingsArrowRow(
            title = "微博中间栏",
            modifier = Modifier.testTag("settings_feed_center_tab"),
            summary = state.feedCenterTab.label,
            cardListPosition = pos,
            onClick = { state.pickFeedCenter = true },
        )
    },
    SettingsEntry(
        id = "feed_right_tab",
        title = "微博右侧栏",
        summary = state.feedRightTab.label,
        keywords = "主页 分栏 顶栏 标签 关注 推荐 附近 tab",
    ) { pos ->
        SettingsArrowRow(
            title = "微博右侧栏",
            modifier = Modifier.testTag("settings_feed_right_tab"),
            summary = state.feedRightTab.label,
            cardListPosition = pos,
            onClick = { state.pickFeedRight = true },
        )
    },
    SettingsEntry(
        id = "collapse_follow_groups",
        title = "收纳关注分组",
        summary = "开启后收进中间栏二级菜单，关闭后直接全部展开",
        keywords = "分组 折叠 二级菜单 菜单 group",
    ) { pos ->
        SettingsSwitchRow(
            checked = state.collapseFollowGroups,
            onCheckedChange = {
                state.collapseFollowGroups = it
                App.settings.applyCollapseFollowGroups(it)
            },
            title = "收纳关注分组",
            summary = "开启后收进中间栏二级菜单，关闭后直接全部展开",
            modifier = Modifier.testTag("settings_collapse_follow_groups"),
            cardListPosition = pos,
        )
    },
)

@Composable
private fun feedCardStyleSubEntries(
    state: SettingsUiState,
): List<SettingsEntry> {
    val feedFrameSummary = if (state.feedCardFrame) {
        "关闭后去掉卡片底色与圆角，正文横向与顶栏按钮对齐"
    } else {
        "已去掉卡片外框，正文与顶栏按钮对齐"
    }
    val detailFrameSummary = if (state.detailCardFrame) {
        "关闭后主贴与热门评论去掉外框，正文与顶栏按钮对齐；楼中楼等内部块保留"
    } else {
        "已去掉主贴与热门评论外框，正文与顶栏按钮对齐"
    }
    val foldLongTextSummary = if (state.foldLongText) {
        "信息流、搜索结果、热搜/话题与超话里的长微博及较长简介默认折叠，点「展开」查看完整内容"
    } else {
        "信息流、搜索结果与超话/话题里的长微博和简介直接展开显示"
    }
    return listOf(
        SettingsEntry(
            id = "feed_card_frame",
            title = "首页卡片外框",
            summary = feedFrameSummary,
            keywords = "卡片 边框 圆角 扁平 对齐 card frame",
        ) { pos ->
            SettingsSwitchRow(
                checked = state.feedCardFrame,
                onCheckedChange = {
                    state.feedCardFrame = it
                    App.settings.applyFeedCardFrame(it)
                },
                title = "首页卡片外框",
                summary = feedFrameSummary,
                modifier = Modifier.testTag("settings_feed_card_frame"),
                cardListPosition = pos,
            )
        },
        SettingsEntry(
            id = "detail_card_frame",
            title = "详情页卡片外框",
            summary = detailFrameSummary,
            keywords = "正文 评论 边框 圆角 扁平 card frame",
        ) { pos ->
            SettingsSwitchRow(
                checked = state.detailCardFrame,
                onCheckedChange = {
                    state.detailCardFrame = it
                    App.settings.applyDetailCardFrame(it)
                },
                title = "详情页卡片外框",
                summary = detailFrameSummary,
                modifier = Modifier.testTag("settings_detail_card_frame"),
                cardListPosition = pos,
            )
        },
        SettingsEntry(
            id = "fold_long_text",
            title = "长文折叠",
            summary = foldLongTextSummary,
            keywords = "展开全文 收纳 截断 简介 超话 话题 fold long text",
        ) { pos ->
            SettingsSwitchRow(
                checked = state.foldLongText,
                onCheckedChange = {
                    state.foldLongText = it
                    App.settings.applyFoldLongText(it)
                },
                title = "长文折叠",
                summary = foldLongTextSummary,
                modifier = Modifier.testTag("settings_fold_long_text"),
                cardListPosition = pos,
            )
        },
    )
}

@Composable
private fun feedUserBadgesSubEntries(
    state: SettingsUiState,
): List<SettingsEntry> = listOf(
    SettingsEntry(
        id = "show_vip_level",
        title = "显示 VIP 等级",
        summary = "开启后在昵称旁显示 VIP / SVIP / VVIP 及等级标识",
        keywords = "会员 svip vvip 标识 勋章 badge",
    ) { pos ->
        SettingsSwitchRow(
            checked = state.showVipLevel,
            onCheckedChange = {
                state.showVipLevel = it
                App.settings.applyShowVipLevel(it)
            },
            title = "显示 VIP 等级",
            summary = "开启后在昵称旁显示 VIP / SVIP / VVIP 及等级标识",
            modifier = Modifier.testTag("settings_show_vip_level"),
            cardListPosition = pos,
        )
    },
    SettingsEntry(
        id = "show_fan_level",
        title = "显示粉丝等级",
        summary = "开启后在评论区昵称旁显示铁粉、金粉、钻粉等粉丝标识",
        keywords = "铁粉 金粉 钻粉 挚爱粉 评论 标识 fan",
    ) { pos ->
        SettingsSwitchRow(
            checked = state.showFanLevel,
            onCheckedChange = {
                state.showFanLevel = it
                App.settings.applyShowFanLevel(it)
            },
            title = "显示粉丝等级",
            summary = "开启后在评论区昵称旁显示铁粉、金粉、钻粉等粉丝标识",
            modifier = Modifier.testTag("settings_show_fan_level"),
            cardListPosition = pos,
        )
    },
    SettingsEntry(
        id = "show_verified_badge",
        title = "显示认证 V 标",
        summary = "开启后在认证用户头像右下角显示金 V / 蓝 V 标识",
        keywords = "金v 蓝v 黄v 认证 头像 verified",
    ) { pos ->
        SettingsSwitchRow(
            checked = state.showVerifiedBadge,
            onCheckedChange = {
                state.showVerifiedBadge = it
                App.settings.applyShowVerifiedBadge(it)
            },
            title = "显示认证 V 标",
            summary = "开启后在认证用户头像右下角显示金 V / 蓝 V 标识",
            modifier = Modifier.testTag("settings_show_verified_badge"),
            cardListPosition = pos,
        )
    },
)

@Composable
private fun feedGeneralEntries(
    nav: NavState,
    state: SettingsUiState,
): List<SettingsEntry> {
    val filterSummary = formatContentFilterSummary(ContentFilters.rules)
    val hotBoardsSummary = App.settings.hotBoards.let { picked ->
        when {
            picked.isEmpty() -> "已隐藏"
            picked.size == HotBoard.entries.size -> "全部"
            else -> HotBoard.entries.filter { it in picked }.joinToString("、") { it.label }
        }
    }
    return listOf(
        SettingsEntry(
            id = "content_filter",
            title = "内容屏蔽",
            summary = filterSummary,
            keywords = "屏蔽 过滤 关键词 正则 话题 静音 拉黑 filter mute",
        ) { pos ->
            SettingsArrowRow(
                title = "内容屏蔽",
                modifier = Modifier.testTag("settings_content_filter"),
                summary = filterSummary,
                cardListPosition = pos,
                onClick = { nav.push(Route.ContentFilterSettings) },
            )
        },
        SettingsEntry(
            id = "hot_boards",
            title = "搜索页热搜榜单",
            summary = hotBoardsSummary,
            keywords = "热搜 榜单 实时 文娱 隐藏 hot board",
        ) { pos ->
            SettingsArrowRow(
                title = "搜索页热搜榜单",
                modifier = Modifier.testTag("settings_hot_boards"),
                summary = hotBoardsSummary,
                cardListPosition = pos,
                onClick = { state.pickHotBoards = true },
            )
        },
        SettingsEntry(
            id = "reverse_browse",
            title = "倒序浏览",
            summary = "刷新不回顶，从当前位置往上补看新微博",
            keywords = "时间线 不回顶 补看 新微博 reverse",
        ) { pos ->
            SettingsSwitchRow(
                checked = state.reverseBrowse,
                onCheckedChange = {
                    state.reverseBrowse = it
                    App.settings.applyReverseBrowse(it)
                },
                title = "倒序浏览",
                summary = "刷新不回顶，从当前位置往上补看新微博",
                modifier = Modifier.testTag("settings_reverse_browse"),
                cardListPosition = pos,
            )
        },
        SettingsEntry(
            id = "filter_seen_recommend",
            title = "过滤看过的推荐微博",
            summary = "实验性功能，按微博 ID 在本地自动过滤已看过的推荐内容",
            keywords = "推荐 过滤 去重 看过 已看 重复 实验 recommend seen filter",
        ) { pos ->
            SettingsSwitchRow(
                checked = state.filterSeenRecommend,
                onCheckedChange = {
                    state.filterSeenRecommend = it
                    App.settings.applyFilterSeenRecommend(it)
                },
                title = "过滤看过的推荐微博",
                summary = "实验性功能，按微博 ID 在本地自动过滤已看过的推荐内容",
                modifier = Modifier.testTag("settings_filter_seen_recommend"),
                cardListPosition = pos,
            )
        },
    ) + run {
        val retention = RecommendSeenRetention.fromDays(state.recommendSeenRetentionDays)
        val seenOwner = App.repo.session?.uid.orEmpty()
        val seenCount = if (App.isRecommendSeenReady()) {
            // 读取 version 使清空或后台写入后设置摘要中的条数能即时刷新。
            @Suppress("UNUSED_VARIABLE")
            val v = App.recommendSeen.version
            App.recommendSeen.count(seenOwner, retention.millis)
        } else {
            0
        }
        buildList {
            if (state.filterSeenRecommend) {
                add(
                    SettingsEntry(
                        id = "recommend_seen_retention",
                        title = "推荐已看记录保留时长",
                        summary = "自动清理超过 ${retention.label} 的本地已看记录",
                        keywords = "推荐 已看 保留 时长 天数 recommend seen retention",
                    ) { pos ->
                        SettingsArrowRow(
                            title = "推荐已看记录保留时长",
                            summary = "自动清理超过 ${retention.label} 的本地已看记录",
                            modifier = Modifier.testTag("settings_recommend_seen_retention"),
                            cardListPosition = pos,
                            onClick = { state.pickRecommendSeenRetention = true },
                        )
                    },
                )
            }
            if (state.filterSeenRecommend || seenCount > 0) {
                val clearSummary = if (seenCount > 0) {
                    "当前账号已记录 $seenCount 条，清空后将重新展示"
                } else {
                    "当前暂无已看推荐记录"
                }
                add(
                    SettingsEntry(
                        id = "clear_recommend_seen",
                        title = "清空已看推荐记录",
                        summary = clearSummary,
                        keywords = "清空 重置 推荐 已看 记录 recommend seen clear",
                    ) { pos ->
                        SettingsArrowRow(
                            title = "清空已看推荐记录",
                            summary = clearSummary,
                            modifier = Modifier.testTag("settings_clear_recommend_seen"),
                            cardListPosition = pos,
                            onClick = {
                                if (App.isRecommendSeenReady() && seenCount > 0) {
                                    App.recommendSeen.clear(seenOwner)
                                    App.toast("已清空推荐已看记录")
                                } else {
                                    App.toast("当前没有可清空的已看记录")
                                }
                            },
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun feedOverviewSections(
    nav: NavState,
    state: SettingsUiState,
): List<SettingsGroupSection> {
    val badgeCount = listOf(
        App.settings.showVipLevel,
        App.settings.showFanLevel,
        App.settings.showVerifiedBadge,
    ).count { it }
    val otherEntries = listOf(
        SettingsEntry(
            id = "sub_feed_card_style",
            title = SettingsSubPage.FeedCardStyle.title,
            summary = "首页卡片外框、详情页外框与长文折叠",
            keywords = "卡片 外框 长文 折叠 样式",
        ) { pos ->
            SettingsArrowRow(
                title = SettingsSubPage.FeedCardStyle.title,
                summary = "首页卡片外框、详情页外框与长文折叠",
                modifier = Modifier.testTag("settings_sub_feed_card_style"),
                cardListPosition = pos,
                onClick = { nav.push(Route.SettingsSubDetail(SettingsSubPage.FeedCardStyle)) },
            )
        },
        SettingsEntry(
            id = "sub_user_badges",
            title = SettingsSubPage.UserBadges.title,
            summary = "VIP、粉丝等级与认证 V 标（已开启 $badgeCount/3）",
            keywords = "VIP 会员 铁粉 认证 V标 标识",
        ) { pos ->
            SettingsArrowRow(
                title = SettingsSubPage.UserBadges.title,
                summary = "VIP、粉丝等级与认证 V 标（已开启 $badgeCount/3）",
                modifier = Modifier.testTag("settings_sub_user_badges"),
                cardListPosition = pos,
                onClick = { nav.push(Route.SettingsSubDetail(SettingsSubPage.UserBadges)) },
            )
        },
    ) + feedGeneralEntries(nav = nav, state = state)

    return listOf(
        SettingsGroupSection(
            key = "feed_tabs",
            title = SettingsGroup.Feed.title,
            entries = feedTabsSubEntries(state),
            headerGroupKey = "feed",
            group = SettingsGroup.Feed,
        ),
        SettingsGroupSection(
            key = "feed_other",
            title = SettingsGroup.Feed.title,
            entries = otherEntries,
            headerGroupKey = "feed",
            group = SettingsGroup.Feed,
        ),
    )
}

@Composable
private fun feedAllSearchableEntries(
    nav: NavState,
    state: SettingsUiState,
): List<SettingsEntry> =
    feedTabsSubEntries(state) +
        feedGeneralEntries(nav = nav, state = state) +
        feedCardStyleSubEntries(state) +
        feedUserBadgesSubEntries(state)

@Composable
private fun accountBehaviorSubEntries(
    state: SettingsUiState,
): List<SettingsEntry> = listOf(
    SettingsEntry(
        id = "quick_switch_account",
        title = "长按「我」切换账号",
        summary = "长按悬浮底栏的「我」快速切换已保存账号",
        keywords = "快捷 切号 多账号 switch account",
    ) { pos ->
        SettingsSwitchRow(
            checked = state.quickSwitchAccount,
            onCheckedChange = {
                state.quickSwitchAccount = it
                App.settings.applyQuickSwitchAccount(it)
            },
            title = "长按「我」切换账号",
            summary = "长按悬浮底栏的「我」快速切换已保存账号",
            modifier = Modifier.testTag("settings_quick_switch_account"),
            cardListPosition = pos,
        )
    },
    SettingsEntry(
        id = "profile_tab_avatar",
        title = "底栏「我」显示头像",
        summary = "在悬浮底栏的「我」位置显示当前账号头像",
        keywords = "头像 底栏 个人 avatar",
    ) { pos ->
        SettingsSwitchRow(
            checked = state.profileTabAvatar,
            onCheckedChange = {
                state.profileTabAvatar = it
                App.settings.applyProfileTabAvatar(it)
            },
            title = "底栏「我」显示头像",
            summary = "在悬浮底栏的「我」位置显示当前账号头像",
            modifier = Modifier.testTag("settings_profile_tab_avatar"),
            cardListPosition = pos,
        )
    },
)

@Composable
private fun localContentDataSubEntries(
    state: SettingsUiState,
    scope: CoroutineScope,
): List<SettingsEntry> {
    val accounts = remember(state.localFollowUid) { App.store.accounts() }
    val localFollowAccount = accounts.firstOrNull { it.uid == state.localFollowUid }
    val localFollowSummary = if (localFollowAccount == null) {
        "跟随当前账号"
    } else {
        "用 ${localFollowAccount.name.ifBlank { "UID ${localFollowAccount.uid}" }} 的身份取数"
    }
    val syncFavSummary = if (state.syncingLocalFavorites) {
        "正在同步微博收藏"
    } else {
        "将微博收藏复制到本地收藏"
    }
    if (state.guest) return emptyList()
    return listOf(
        SettingsEntry(
            id = "local_follow",
            title = "本地关注使用账号",
            summary = localFollowSummary,
            keywords = "本地关注 特别关注 悄悄关注 取数 follow",
        ) { pos ->
            SettingsArrowRow(
                title = "本地关注使用账号",
                modifier = Modifier.testTag("settings_local_follow"),
                summary = localFollowSummary,
                cardListPosition = pos,
                onClick = { state.pickLocalFollow = true },
            )
        },
        SettingsEntry(
            id = "sync_local_favorites",
            title = "同步本地收藏",
            summary = syncFavSummary,
            keywords = "云端收藏 微博收藏 复制 同步 favorite sync",
        ) { pos ->
            SettingsArrowRow(
                title = "同步本地收藏",
                summary = syncFavSummary,
                modifier = Modifier.testTag("settings_sync_local_favorites"),
                cardListPosition = pos,
                onClick = {
                    if (!state.syncingLocalFavorites) {
                        state.syncingLocalFavorites = true
                        scope.launch {
                            runCatching { App.repo.syncLocalFavorites() }
                                .onSuccess { count -> App.toast("已同步 $count 条微博到本地收藏") }
                                .onFailure { App.toast(it.message ?: "同步失败") }
                            state.syncingLocalFavorites = false
                        }
                    }
                },
            )
        },
    )
}

@Composable
private fun contentLibrarySubEntries(
    nav: NavState,
    state: SettingsUiState,
): List<SettingsEntry> {
    val draftsSummary = "${App.store.drafts().size} 条草稿"
    val historySummary = "${App.store.history().size} 条记录"
    val readLaterEntries = ReadLater.all()
    val readLaterUnread = readLaterEntries.count { !it.read }
    val readLaterSummary = if (readLaterUnread > 0) {
        "${readLaterEntries.size} 条（${readLaterUnread} 未读）"
    } else {
        "${readLaterEntries.size} 条快照"
    }
    val favoritesSummary = "${App.repo.localFavoriteIds.size} 条收藏"
    return buildList {
        add(
            SettingsEntry(
                id = "drafts",
                title = "草稿箱",
                summary = draftsSummary,
                keywords = "草稿 未发布 draft",
            ) { pos ->
                SettingsArrowRow(
                    title = "草稿箱",
                    modifier = Modifier.testTag("settings_drafts"),
                    summary = draftsSummary,
                    cardListPosition = pos,
                    onClick = { nav.push(Route.Drafts) },
                )
            },
        )
        add(
            SettingsEntry(
                id = "history",
                title = "浏览历史",
                summary = historySummary,
                keywords = "历史记录 看过 history",
            ) { pos ->
                SettingsArrowRow(
                    title = "浏览历史",
                    modifier = Modifier.testTag("settings_history"),
                    summary = historySummary,
                    cardListPosition = pos,
                    onClick = { nav.push(Route.History) },
                )
            },
        )
        add(
            SettingsEntry(
                id = "read_later",
                title = "稍后再看",
                summary = readLaterSummary,
                keywords = "稍后再看 离线 快照 书签 阅读 read later",
            ) { pos ->
                SettingsArrowRow(
                    title = "稍后再看",
                    modifier = Modifier.testTag("settings_read_later"),
                    summary = readLaterSummary,
                    cardListPosition = pos,
                    onClick = { nav.push(Route.ReadLater) },
                )
            },
        )
        // 本地收藏全局共用，纯本地模式下同样可直接打开。
        add(
            SettingsEntry(
                id = "favorites",
                title = "收藏",
                summary = favoritesSummary,
                keywords = "本地收藏 书签 favorite",
            ) { pos ->
                SettingsArrowRow(
                    title = "收藏",
                    modifier = Modifier.testTag("settings_favorites"),
                    summary = favoritesSummary,
                    cardListPosition = pos,
                    onClick = { nav.push(Route.Favorites) },
                )
            },
        )
        add(
            SettingsEntry(
                id = "topic",
                title = "话题 / 超话",
                keywords = "超话 话题 广场 topic supertopic",
            ) { pos ->
                SettingsArrowRow(
                    title = "话题 / 超话",
                    modifier = Modifier.testTag("settings_topic"),
                    cardListPosition = pos,
                    onClick = { nav.push(Route.Topic()) },
                )
            },
        )
        if (!state.guest) {
            add(
                SettingsEntry(
                    id = "nearby",
                    title = "附近",
                    keywords = "同城 定位 位置 nearby location",
                ) { pos ->
                    SettingsArrowRow(
                        title = "附近",
                        modifier = Modifier.testTag("settings_nearby"),
                        cardListPosition = pos,
                        onClick = { nav.push(Route.Nearby) },
                    )
                },
            )
        }
    }
}

@Composable
private fun accountOverviewEntries(
    nav: NavState,
    state: SettingsUiState,
    includeLibraryShortcuts: Boolean,
): List<SettingsEntry> = buildList {
    add(
        SettingsEntry(
            id = "accounts",
            title = "账号管理",
            keywords = "切号 多账号 添加 登录 account",
        ) { pos ->
            SettingsArrowRow(
                title = "账号管理",
                modifier = Modifier.testTag("settings_accounts"),
                cardListPosition = pos,
                onClick = { nav.push(Route.Accounts) },
            )
        },
    )
    add(
        SettingsEntry(
            id = "sub_account_behavior",
            title = SettingsSubPage.AccountBehavior.title,
            summary = "长按切号与底栏「我」头像显示",
            keywords = "切号 长按 头像 底栏",
        ) { pos ->
            SettingsArrowRow(
                title = SettingsSubPage.AccountBehavior.title,
                summary = "长按切号与底栏「我」头像显示",
                modifier = Modifier.testTag("settings_sub_account_behavior"),
                cardListPosition = pos,
                onClick = { nav.push(Route.SettingsSubDetail(SettingsSubPage.AccountBehavior)) },
            )
        },
    )
    if (!state.guest) {
        add(
            SettingsEntry(
                id = "sub_local_content_data",
                title = SettingsSubPage.LocalContentData.title,
                summary = "本地关注取数账号与本地收藏同步",
                keywords = "本地关注 同步 本地收藏 取数",
            ) { pos ->
                SettingsArrowRow(
                    title = SettingsSubPage.LocalContentData.title,
                    summary = "本地关注取数账号与本地收藏同步",
                    modifier = Modifier.testTag("settings_sub_local_content_data"),
                    cardListPosition = pos,
                    onClick = { nav.push(Route.SettingsSubDetail(SettingsSubPage.LocalContentData)) },
                )
            },
        )
    }
    if (includeLibraryShortcuts) {
        add(
            SettingsEntry(
                id = "sub_content_library",
                title = SettingsSubPage.ContentLibrary.title,
                summary = "草稿箱、浏览历史、收藏与话题广场",
                keywords = "草稿 历史 收藏 话题 超话 附近",
            ) { pos ->
                SettingsArrowRow(
                    title = SettingsSubPage.ContentLibrary.title,
                    summary = "草稿箱、浏览历史、收藏与话题广场",
                    modifier = Modifier.testTag("settings_sub_content_library"),
                    cardListPosition = pos,
                    onClick = { nav.push(Route.SettingsSubDetail(SettingsSubPage.ContentLibrary)) },
                )
            },
        )
    }
}

@Composable
private fun accountAllSearchableEntries(
    nav: NavState,
    state: SettingsUiState,
    scope: CoroutineScope,
    includeLibraryShortcuts: Boolean,
): List<SettingsEntry> = buildList {
    add(
        SettingsEntry(
            id = "accounts",
            title = "账号管理",
            keywords = "切号 多账号 添加 登录 account",
        ) { pos ->
            SettingsArrowRow(
                title = "账号管理",
                modifier = Modifier.testTag("settings_accounts"),
                cardListPosition = pos,
                onClick = { nav.push(Route.Accounts) },
            )
        },
    )
    addAll(accountBehaviorSubEntries(state))
    addAll(localContentDataSubEntries(state, scope))
    if (includeLibraryShortcuts) {
        addAll(contentLibrarySubEntries(nav, state))
    }
}

@Composable
private fun updateSettingsEntries(
    nav: NavState,
    state: SettingsUiState,
    scope: CoroutineScope,
): List<SettingsEntry> {
    val updateSummary = if (App.checkingUpdate) "正在检查…" else "当前版本 ${state.version}"
    return listOf(
        SettingsEntry(
            id = "check_update",
            title = "检查更新",
            summary = updateSummary,
            keywords = "新版本 升级 版本号 update version",
        ) { pos ->
            SettingsArrowRow(
                title = "检查更新",
                modifier = Modifier.testTag("settings_update"),
                summary = updateSummary,
                cardListPosition = pos,
                onClick = { scope.launch { App.checkUpdate(manual = true) } },
            )
        },
        SettingsEntry(
            id = "changelog",
            title = "更新日志",
            keywords = "版本历史 改动 changelog",
        ) { pos ->
            SettingsArrowRow(
                title = "更新日志",
                modifier = Modifier.testTag("settings_changelog"),
                cardListPosition = pos,
                onClick = { nav.push(Route.Changelog) },
            )
        },
        SettingsEntry(
            id = "auto_update",
            title = "自动检查更新",
            keywords = "启动 检查 升级 auto update",
        ) { pos ->
            SettingsSwitchRow(
                checked = state.autoCheckUpdate,
                onCheckedChange = {
                    state.autoCheckUpdate = it
                    App.settings.applyAutoCheckUpdate(it)
                },
                title = "自动检查更新",
                modifier = Modifier.testTag("settings_auto_update"),
                cardListPosition = pos,
            )
        },
        SettingsEntry(
            id = "prerelease",
            title = "接收预发布版本",
            keywords = "测试版 预览版 pre beta prerelease",
        ) { pos ->
            SettingsSwitchRow(
                checked = state.acceptPrerelease,
                onCheckedChange = {
                    state.acceptPrerelease = it
                    App.settings.applyAcceptPrerelease(it)
                },
                title = "接收预发布版本",
                modifier = Modifier.testTag("settings_prerelease"),
                cardListPosition = pos,
            )
        },
        SettingsEntry(
            id = "in_app_update",
            title = "应用内更新",
            keywords = "下载 安装 apk 升级 in app update",
        ) { pos ->
            SettingsSwitchRow(
                checked = state.inAppUpdate,
                onCheckedChange = {
                    state.inAppUpdate = it
                    App.settings.applyInAppUpdate(it)
                },
                title = "应用内更新",
                modifier = Modifier.testTag("settings_in_app_update"),
                cardListPosition = pos,
            )
        },
    )
}

@Composable
private fun otherSettingsEntries(
    nav: NavState,
    state: SettingsUiState,
    context: PlatformContext,
): List<SettingsEntry> {
    val aboutSummary = "v${state.version} · 上游库与开源致谢"
    return listOf(
        SettingsEntry(
            id = "clear_cache",
            title = "清理缓存",
            summary = state.message,
            keywords = "清除 图片缓存 磁盘 空间 释放 cache clear storage",
        ) { pos ->
            val coilCtx = LocalPlatformContext.current
            SettingsArrowRow(
                title = "清理缓存",
                modifier = Modifier.testTag("settings_clear_cache"),
                cardListPosition = pos,
                onClick = {
                    runCatching {
                        val loader = SingletonImageLoader.get(coilCtx)
                        loader.memoryCache?.clear()
                        loader.diskCache?.clear()
                        appCacheDir().child("live").deleteRecursively()
                    }
                    App.cache.clear()
                    state.message = "已清理图片与内容缓存"
                },
            )
        },
        SettingsEntry(
            id = "backup_export",
            title = "导出本地备份",
            summary = "导出偏好设置、本地收藏、草稿、历史与关注标记（不含登录凭据）",
            keywords = "备份 导出 json 数据 迁移 backup export",
        ) { pos ->
            SettingsArrowRow(
                title = "导出本地备份",
                modifier = Modifier.testTag("settings_backup_export"),
                summary = "导出偏好设置、本地收藏、草稿、历史与关注标记（不含登录凭据）",
                cardListPosition = pos,
                onClick = { if (!state.backupBusy) state.requestExportBackup = true },
            )
        },
        SettingsEntry(
            id = "backup_import",
            title = "导入本地备份",
            summary = "从 JSON 备份恢复偏好设置并合并本地数据",
            keywords = "恢复 导入 json 数据 迁移 restore import",
        ) { pos ->
            SettingsArrowRow(
                title = "导入本地备份",
                modifier = Modifier.testTag("settings_backup_import"),
                summary = "从 JSON 备份恢复偏好设置并合并本地数据",
                cardListPosition = pos,
                onClick = { if (!state.backupBusy) state.requestImportBackup = true },
            )
        },
        SettingsEntry(
            id = "about",
            title = "关于",
            summary = aboutSummary,
            keywords = "版本 开源 致谢 协议 许可证 about",
        ) { pos ->
            SettingsArrowRow(
                title = "关于",
                modifier = Modifier.testTag("settings_about"),
                summary = aboutSummary,
                cardListPosition = pos,
                onClick = { nav.push(Route.About) },
            )
        },
    )
}

@Composable
private fun logoutSettingsEntries(
    nav: NavState,
    state: SettingsUiState,
): List<SettingsEntry> = if (state.guest) {
    listOf(
        SettingsEntry(
            id = "leave_local",
            title = "退出纯本地模式",
            summary = "回到登录页，本地关注名单保留",
            keywords = "登出 注销 登录 退出 logout exit",
        ) { pos ->
            SettingsArrowRow(
                title = "退出纯本地模式",
                modifier = Modifier.testTag("settings_leave_local"),
                summary = "回到登录页，本地关注名单保留",
                cardListPosition = pos,
                onClick = {
                    App.settings.applyLocalMode(false)
                    nav.reset(Route.SmsLogin())
                },
            )
        },
    )
} else {
    listOf(
        SettingsEntry(
            id = "logout",
            title = "退出登录",
            summary = "清除本地会话",
            keywords = "登出 注销 退出 账号 logout exit",
        ) { pos ->
            SettingsArrowRow(
                title = "退出登录",
                modifier = Modifier.testTag("settings_logout"),
                summary = "清除本地会话",
                cardListPosition = pos,
                onClick = { state.logout = true },
            )
        },
    )
}

@Composable
private fun SettingsDialogs(
    nav: NavState,
    state: SettingsUiState,
) {
    val accounts = remember(state.localFollowUid) { App.store.accounts() }
    val leftOptions = feedTabOptions(state.feedGroups, allowNone = true, current = if (state.guest) null else state.feedLeftTab)
    val centerOptions = feedTabOptions(state.feedGroups, allowNone = false, current = if (state.guest) null else state.feedCenterTab)
    val rightOptions = feedTabOptions(state.feedGroups, allowNone = true, current = if (state.guest) null else state.feedRightTab)

    state.pendingImportBackup?.let { bundle ->
        ConfirmDialog(
            title = "导入本地备份",
            message = "检测到 ${formatBackupSummary(bundle.summary())}。导入将覆盖对应偏好设置并合并本地收藏、草稿、历史与关注标记，不影响当前登录账号。是否继续？",
            confirmText = "确认导入",
            onDismiss = { state.pendingImportBackup = null },
            onConfirm = {
                applyLocalBackup(bundle, App.settings, App.store, App.repo)
                state.syncFromSettings()
                state.pendingImportBackup = null
                App.toast("已恢复本地备份")
            },
        )
    }

    if (state.logout) {
        ConfirmDialog(
            title = "退出登录",
            message = "会清除本地保存的 Cookie，下次需要重新登录。",
            confirmText = "退出",
            onDismiss = { state.logout = false },
            onConfirm = {
                state.logout = false
                WebCookieBridge.clearAll()
                App.repo.logout()
                nav.reset(Route.SmsLogin())
            },
        )
    }

    if (state.pickLocalFollow) {
        LocalFollowPicker(
            accounts = accounts,
            selectedUid = state.localFollowUid,
            onDismiss = { state.pickLocalFollow = false },
            onPick = { uid ->
                App.settings.applyLocalFollowUid(uid)
                state.localFollowUid = uid
                state.pickLocalFollow = false
            },
        )
    }

    if (state.pickFeedLeft) {
        FeedTabPicker(
            title = "微博左侧栏内容",
            selected = state.feedLeftTab,
            options = leftOptions,
            onDismiss = { state.pickFeedLeft = false },
            onPick = { selection ->
                App.settings.applyFeedLeftTab(selection)
                state.feedLeftTab = selection
                state.pickFeedLeft = false
            },
        )
    }

    if (state.pickFeedCenter) {
        FeedTabPicker(
            title = "微博中间栏内容",
            selected = state.feedCenterTab,
            options = centerOptions,
            onDismiss = { state.pickFeedCenter = false },
            onPick = { selection ->
                App.settings.applyFeedCenterTab(selection)
                state.feedCenterTab = selection
                state.pickFeedCenter = false
            },
        )
    }

    if (state.pickFeedRight) {
        FeedTabPicker(
            title = "微博右侧栏内容",
            selected = state.feedRightTab,
            options = rightOptions,
            onDismiss = { state.pickFeedRight = false },
            onPick = { selection ->
                App.settings.applyFeedRightTab(selection)
                state.feedRightTab = selection
                state.pickFeedRight = false
            },
        )
    }

    if (state.pickSearchPanelAnimation) {
        OptionPicker(
            title = "搜索面板动画",
            options = SearchPanelAnimation.entries.map { it.key to it.label },
            selectedKey = state.searchPanelAnimation.key,
            tagPrefix = "search_panel_anim",
            groupKey = "search-panel-anim",
            onDismiss = { state.pickSearchPanelAnimation = false },
            onPick = { key ->
                val picked = SearchPanelAnimation.fromKey(key)
                App.settings.applySearchPanelAnimation(picked)
                state.searchPanelAnimation = picked
                state.pickSearchPanelAnimation = false
            },
        )
    }

    if (state.pickBottomLeftAction) {
        OptionPicker(
            title = "底栏左下角按钮",
            options = BottomLeftAction.entries.map { it.key to it.label },
            selectedKey = App.settings.bottomLeftAction.key,
            tagPrefix = "bottom_left_action",
            groupKey = "bottom-left-action",
            onDismiss = { state.pickBottomLeftAction = false },
            onPick = { key ->
                val picked = BottomLeftAction.fromKey(key)
                App.settings.applyBottomLeftAction(picked)
                state.pickBottomLeftAction = false
            },
        )
    }

    if (state.pickImageBrowseQuality) {
        OptionPicker(
            title = "浏览图片画质选择",
            options = ImageBrowseQuality.entries.map { it.key to it.label },
            selectedKey = App.settings.imageBrowseQuality.key,
            tagPrefix = "image_browse_quality",
            groupKey = "image-browse-quality",
            onDismiss = { state.pickImageBrowseQuality = false },
            onPick = { key ->
                val picked = ImageBrowseQuality.fromKey(key)
                App.settings.applyImageBrowseQuality(picked)
                state.pickImageBrowseQuality = false
            },
        )
    }

    if (state.pickRecommendSeenRetention) {
        OptionPicker(
            title = "推荐已看记录保留时长",
            options = RecommendSeenRetention.entries.map { it.days.toString() to it.label },
            selectedKey = RecommendSeenRetention.fromDays(state.recommendSeenRetentionDays).days.toString(),
            tagPrefix = "recommend_seen_retention",
            groupKey = "recommend-seen-retention",
            onDismiss = { state.pickRecommendSeenRetention = false },
            onPick = { key ->
                val picked = RecommendSeenRetention.fromDays(key.toIntOrNull() ?: RecommendSeenRetention.Days7.days)
                App.settings.applyRecommendSeenRetentionDays(picked.days)
                state.recommendSeenRetentionDays = picked.days
                state.pickRecommendSeenRetention = false
            },
        )
    }

    if (state.pickHotBoards) {
        HotBoardPicker(
            selected = App.settings.hotBoards,
            onDismiss = { state.pickHotBoards = false },
            onConfirm = { picked ->
                App.settings.applyHotBoards(picked)
                state.pickHotBoards = false
            },
        )
    }

    if (state.pickAccentColor) {
        AccentColorPickerDialog(
            currentArgb = state.accentColor,
            onDismiss = { state.pickAccentColor = false },
            onConfirm = { picked ->
                App.settings.applyAccentColor(picked)
                state.accentColor = picked
                state.pickAccentColor = false
            },
        )
    }
}

private val RainbowSwatchColors = listOf(
    Color(0xFFFF3B30),
    Color(0xFFFF9500),
    Color(0xFFFFCC00),
    Color(0xFF34C759),
    Color(0xFF00C7BE),
    Color(0xFF007AFF),
    Color(0xFFAF52DE),
    Color(0xFFFF3B30),
)

/**
 * 自定义强调色弹窗：支持常用色板快选（含「Jeb_」动态循环）、HSV 三通道微调与十六进制色号输入。
 */
@Composable
private fun AccentColorPickerDialog(
    currentArgb: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var show by remember { mutableStateOf(false) }
    var apply by remember { mutableStateOf(false) }
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { show = true }

    val dark = isDarkTheme()
    val themePrimary = COUITheme.colorScheme.primary
    var pickedArgb by remember { mutableIntStateOf(currentArgb) }
    val initialColor = when (currentArgb) {
        0 -> DefaultAccentSwatchColor
        AccentHueCycle -> themePrimary
        else -> Color(currentArgb)
    }
    val initialHsv = remember {
        FloatArray(3).also { argbToHsv(initialColor.toArgb(), it) }
    }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var brightness by remember { mutableFloatStateOf(initialHsv[2]) }
    var hexInput by remember { mutableStateOf(formatHexColor(initialColor.toArgb())) }

    val isHueCycle = pickedArgb == AccentHueCycle
    LaunchedEffect(isHueCycle, dark) {
        if (!isHueCycle) return@LaunchedEffect
        val startMs = currentTimeMillis()
        val startHue = hue
        while (true) {
            val elapsed = currentTimeMillis() - startMs
            val nextHue = (startHue + elapsed / 100f) % 360f
            val cycleRgb = hueCycleArgb(nextHue, dark)
            val hsv = FloatArray(3)
            argbToHsv(cycleRgb, hsv)
            hue = hsv[0]
            saturation = hsv[1]
            brightness = hsv[2]
            hexInput = formatHexColor(cycleRgb)
            kotlinx.coroutines.delay(120L)
        }
    }

    val previewColor = when (pickedArgb) {
        0 -> DefaultAccentSwatchColor
        AccentHueCycle -> Color(hueCycleArgb(hue, dark))
        else -> Color(pickedArgb)
    }

    fun selectArgb(argb: Int) {
        pickedArgb = argb
        if (argb == AccentHueCycle) return
        val displayArgb = if (argb == 0) DefaultAccentSwatchColor.toArgb() else argb
        val hsv = FloatArray(3)
        argbToHsv(displayArgb, hsv)
        hue = hsv[0]
        saturation = hsv[1]
        brightness = hsv[2]
        hexInput = formatHexColor(displayArgb)
    }

    fun updateFromHsv() {
        val c = hsvToArgb(hue, saturation, brightness)
        pickedArgb = c
        hexInput = formatHexColor(c)
    }

    OverlayDialog(
        show = show,
        title = null,
        outsideMargin = DpSize(16.dp, 8.dp),
        onDismissRequest = { show = false },
        onDismissFinished = {
            if (!settled) {
                settled = true
                if (apply) onConfirm(pickedArgb) else onDismiss()
            }
        },
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
            ) {
                Text(
                    text = "强调色",
                    fontSize = COUITheme.textStyles.title4.fontSize,
                    fontWeight = FontWeight.SemiBold,
                    color = DialogDefaults.titleColor(),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center),
                )
                val canReset = pickedArgb != 0
                HighlightedIconButton(
                    onClick = { selectArgb(0) },
                    size = 36.dp,
                    enabled = canReset,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .alpha(if (canReset) 1f else 0.36f)
                        .testTag("accent_reset_default"),
                ) {
                    Icon(
                        imageVector = COUIIcons.Undo,
                        contentDescription = "恢复默认",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(previewColor)
                            .border(1.dp, COUITheme.colorScheme.outline, CircleShape),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = accentColorSummary(pickedArgb),
                            fontSize = 14.sp,
                            color = COUITheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Icon(
                                imageVector = CoboIcons.SuperTopic,
                                contentDescription = null,
                                tint = previewColor,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = "超话  #话题#  @用户",
                                fontSize = 12.sp,
                                color = previewColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AccentPresets.chunked(5).forEachIndexed { rowIdx, rowPresets ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            rowPresets.forEachIndexed { colIdx, preset ->
                                val presetIndex = rowIdx * 5 + colIdx
                                val isRainbowPreset = preset.argb == AccentHueCycle
                                val swatchColor = when (preset.argb) {
                                    0 -> DefaultAccentSwatchColor
                                    AccentHueCycle -> previewColor
                                    else -> Color(preset.argb)
                                }
                                val selected = when (preset.argb) {
                                    0 -> pickedArgb == 0
                                    AccentHueCycle -> pickedArgb == AccentHueCycle
                                    else -> pickedArgb != 0 && pickedArgb != AccentHueCycle &&
                                        (pickedArgb or 0xFF000000.toInt()) == preset.argb
                                }
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { selectArgb(preset.argb) }
                                        .padding(horizontal = 2.dp, vertical = 4.dp)
                                        .testTag("accent_preset_$presetIndex"),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(CircleShape)
                                            .then(
                                                if (isRainbowPreset) {
                                                    Modifier.background(Brush.sweepGradient(RainbowSwatchColors))
                                                } else {
                                                    Modifier.background(swatchColor)
                                                },
                                            )
                                            .then(
                                                if (selected) {
                                                    Modifier.border(2.dp, COUITheme.colorScheme.onSurface, CircleShape)
                                                } else {
                                                    Modifier.border(1.dp, COUITheme.colorScheme.outline, CircleShape)
                                                },
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (selected) {
                                            Icon(
                                                imageVector = CoboIcons.Check,
                                                contentDescription = "已选",
                                                tint = if (!isRainbowPreset && swatchColor.luminance() > 0.55f) {
                                                    Color(0xE6000000)
                                                } else {
                                                    Color.White
                                                },
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        text = preset.label,
                                        fontSize = 10.sp,
                                        color = if (selected) {
                                            COUITheme.colorScheme.onSurface
                                        } else {
                                            COUITheme.colorScheme.onSurfaceSecondary
                                        },
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }

                TextField(
                    value = hexInput,
                    onValueChange = { raw ->
                        val cleaned = raw.uppercase().take(7)
                        hexInput = cleaned
                        parseHexColor(cleaned)?.let { parsed ->
                            pickedArgb = parsed
                            val hsv = FloatArray(3)
                            argbToHsv(parsed, hsv)
                            hue = hsv[0]
                            saturation = hsv[1]
                            brightness = hsv[2]
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("accent_hex_input"),
                    label = "十六进制色号 (#RRGGBB)",
                    backgroundMode = TextFieldMode.Line,
                    insideMargin = DpSize(0.dp, 8.dp),
                    colors = TextFieldDefaults.textFieldColors(
                        borderColor = previewColor,
                        unfocusedBorderColor = COUITheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.45f),
                    ),
                    singleLine = true,
                )

                AccentSliderRow(
                    label = "色相",
                    valueText = "${hue.toInt()}°",
                    value = hue,
                    valueRange = 0f..360f,
                    accentColor = previewColor,
                    onValueChange = {
                        hue = it
                        updateFromHsv()
                    },
                )
                AccentSliderRow(
                    label = "饱和度",
                    valueText = "${(saturation * 100).toInt()}%",
                    value = saturation,
                    valueRange = 0f..1f,
                    accentColor = previewColor,
                    onValueChange = {
                        saturation = it
                        updateFromHsv()
                    },
                )
                AccentSliderRow(
                    label = "明度",
                    valueText = "${(brightness * 100).toInt()}%",
                    value = brightness,
                    valueRange = 0f..1f,
                    accentColor = previewColor,
                    onValueChange = {
                        brightness = it
                        updateFromHsv()
                    },
                )
            }
            Spacer(Modifier.height(6.dp))
            DialogButtonBar(
                negative = DialogButtonBarAction(text = "取消", onClick = { show = false }),
                positive = DialogButtonBarAction(
                    text = "确定",
                    onClick = {
                        apply = true
                        show = false
                    },
                ),
            )
        }
    }
}

@Composable
private fun AccentSliderRow(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    accentColor: Color,
    onValueChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, fontSize = 12.sp, color = COUITheme.colorScheme.onSurfaceSecondary)
            Text(valueText, fontSize = 12.sp, color = COUITheme.colorScheme.onSurfaceSecondary)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.sliderColors(foregroundColor = accentColor),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * 搜索页热搜的榜单多选。五个榜可任意勾选，一个都不勾就把整个热搜区块从搜索面板里去掉。
 *
 * 和单选那套弹窗一样，选中结果先留在本地，等窗口收完再交回去，避免和关闭动画抢时序；
 * 取消或点外部关闭都只丢弃当前改动，不落盘。
 */
@Composable
private fun HotBoardPicker(
    selected: Set<HotBoard>,
    onDismiss: () -> Unit,
    onConfirm: (Set<HotBoard>) -> Unit,
) {
    var show by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf(selected) }
    var apply by remember { mutableStateOf(false) }
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { show = true }
    OverlayDialog(
        show = show,
        title = "热搜榜单",
        outsideMargin = DpSize(16.dp, 8.dp),
        onDismissRequest = { show = false },
        onDismissFinished = {
            if (!settled) {
                settled = true
                if (apply) onConfirm(picked) else onDismiss()
            }
        },
    ) {
        Column {
            HotBoard.entries.forEach { board ->
                val checked = board in picked
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            picked = if (checked) picked - board else picked + board
                        }
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .testTag("hot_board_${board.name.lowercase()}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        state = if (checked) ToggleableState.On else ToggleableState.Off,
                        onClick = null,
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(board.label)
                }
            }
            DialogButtonBar(
                negative = DialogButtonBarAction(text = "取消", onClick = { show = false }),
                positive = DialogButtonBarAction(
                    text = "确定",
                    onClick = {
                        apply = true
                        show = false
                    },
                ),
            )
        }
    }
}

/**
 * 「本地关注」取数账号选择。和 [FeedTabPicker] 同一套弹窗：胶囊高亮能按住上下拖着换项，
 * 选中项先记下来，等窗口收完再交出去，避免和关闭动画抢时序。
 */
@Composable
private fun LocalFollowPicker(
    accounts: List<Account>,
    selectedUid: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    var show by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { show = true }
    OverlayDialog(
        show = show,
        title = "本地关注使用账号",
        outsideMargin = DpSize(16.dp, 8.dp),
        onDismissRequest = { show = false },
        onDismissFinished = {
            if (!settled) {
                settled = true
                val uid = result
                if (uid != null) onPick(uid) else onDismiss()
            }
        },
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth(),
                propagateMinConstraints = true,
            ) {
                ListPopupColumn {
                    val optionCount = accounts.size + 1
                    DropdownImpl(
                        item = DropdownItem(text = "跟随当前账号"),
                        optionSize = optionCount,
                        isSelected = selectedUid.isBlank(),
                        index = 0,
                        modifier = Modifier.testTag("local_follow_follow_current"),
                        groupKey = "local-follow-accounts",
                        onSelectedIndexChange = {
                            result = ""
                            show = false
                        },
                    )
                    accounts.forEachIndexed { i, account ->
                        DropdownImpl(
                            item = DropdownItem(
                                text = account.name.ifBlank { "UID ${account.uid}" },
                                summary = if (account.name.isBlank()) null else "UID ${account.uid}",
                            ),
                            optionSize = optionCount,
                            isSelected = account.uid == selectedUid,
                            index = i + 1,
                            modifier = Modifier.testTag("local_follow_option_${account.uid}"),
                            groupKey = "local-follow-accounts",
                            onSelectedIndexChange = {
                                result = accounts[it - 1].uid
                                show = false
                            },
                        )
                    }
                }
            }
            DialogButtonBar(
                negative = DialogButtonBarAction(text = "取消", onClick = { show = false }),
                positive = null,
            )
        }
    }
}

@Composable
private fun FeedTabPicker(
    title: String,
    selected: FeedTabSelection,
    options: List<FeedTabSelection>,
    onDismiss: () -> Unit,
    onPick: (FeedTabSelection) -> Unit,
) {
    OptionPicker(
        title = title,
        options = options.map { it.key to it.label },
        selectedKey = selected.key,
        tagPrefix = "feed_tab",
        groupKey = "feed-tabs",
        summaryOf = { key -> if (options.firstOrNull { it.key == key }?.isGroup == true) "关注分组" else null },
        onDismiss = onDismiss,
        onPick = { key -> options.firstOrNull { it.key == key }?.let(onPick) ?: onDismiss() },
    )
}

/** 单列选项弹窗：[options] 是 key 到显示文案，选中项等窗口收完再交出去。 */
@Composable
private fun OptionPicker(
    title: String,
    options: List<Pair<String, String>>,
    selectedKey: String,
    tagPrefix: String,
    groupKey: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    /** 第二行小字，没有就不显示。 */
    summaryOf: (String) -> String? = { null },
) {
    var show by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { show = true }

    OverlayDialog(
        show = show,
        title = title,
        outsideMargin = DpSize(16.dp, 8.dp),
        onDismissRequest = { show = false },
        onDismissFinished = {
            if (!settled) {
                settled = true
                val picked = result
                if (picked != null) onPick(picked) else onDismiss()
            }
        },
    ) {
        Column {
            // 和三点菜单同一套：ListPopupColumn 自己铺胶囊高亮和上下拖选的手势，
            // DropdownImpl 负责选中态的勾和蓝字。propagateMinConstraints 把对话框的宽度
            // 传给列，否则列会缩到弹层的最小宽度（约 168dp）。
            // 不给高度设上限：设了就会变成可滚动，而滚动和「按住上下拖选」抢同一个手势，
            // 拖不动。整张表一口气排完才拖得起来。
            Box(
                Modifier
                    .fillMaxWidth(),
                propagateMinConstraints = true,
            ) {
                ListPopupColumn {
                    options.forEachIndexed { index, (key, label) ->
                        DropdownImpl(
                            item = DropdownItem(text = label, summary = summaryOf(key)),
                            optionSize = options.size,
                            isSelected = key == selectedKey,
                            index = index,
                            modifier = Modifier.testTag("${tagPrefix}_option_$key"),
                            groupKey = groupKey,
                            onSelectedIndexChange = {
                                result = options[it].first
                                show = false
                            },
                        )
                    }
                }
            }
            DialogButtonBar(
                negative = DialogButtonBarAction(text = "取消", onClick = { show = false }),
                positive = null,
            )
        }
    }
}

@Composable
fun PredictiveBackSettingsScreen(nav: NavState) {
    var predictiveBack by remember { mutableStateOf(App.settings.predictiveBack) }
    var predictiveBackCardFling by remember { mutableStateOf(App.settings.predictiveBackCardFling) }
    var predictiveBackImageFling by remember { mutableStateOf(App.settings.predictiveBackImageFling) }
    var predictiveBackBlur by remember { mutableStateOf(App.settings.predictiveBackBlur) }
    val blurEffects = blurEffectsEnabled()

    val pageColor = pageContainerColor()
    val backdrop = rememberLayerBackdrop()
    val topBarHeight = pinnedHeight(TopAppBarDefaults.SmallTopAppBarCenterHeight)
    val listState = rememberLazyListState()
    BindScrollToTop(listState)

    Box(Modifier.fillMaxSize().background(pageColor)) {
        Box(Modifier.fillMaxSize().layerBackdropIf(blurEffects, backdrop).background(pageColor)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().scrollEndHaptic().overScrollVertical(),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    top = glassBarContentTop(topBarHeight, 10.dp),
                    end = 12.dp,
                    bottom = 24.dp + navBottomPadding(),
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Text(
                        text = "配置侧滑返回时的页面跟手预览、轨迹甩出与背景模糊效果。",
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = COUITheme.colorScheme.onSurfaceSecondary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = CardDefaults.GroupCornerRadius,
                        insideMargin = PaddingValues(0.dp),
                        weight = CardDefaults.GroupWeight,
                    ) {
                        SwitchPreference(
                            checked = predictiveBack,
                            onCheckedChange = {
                                predictiveBack = it
                                App.settings.applyPredictiveBack(it)
                            },
                            title = "预测性返回手势",
                            summary = "开启后返回时页面跟随手指往回收；关闭后直接返回",
                            modifier = Modifier.testTag("settings_predictive_back"),
                            cardListPosition = CardListPosition.Full,
                        )
                    }
                }

                item {
                    AnimatedVisibility(visible = predictiveBack) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = CardDefaults.GroupCornerRadius,
                            insideMargin = PaddingValues(0.dp),
                            weight = CardDefaults.GroupWeight,
                        ) {
                            SwitchPreference(
                                checked = predictiveBackCardFling,
                                onCheckedChange = {
                                    predictiveBackCardFling = it
                                    App.settings.applyPredictiveBackCardFling(it)
                                },
                                title = "返回跟手轨迹",
                                summary = "卡片详情预测性返回时跟随手指偏移，并顺着甩出方向回缩",
                                modifier = Modifier.testTag("settings_predictive_back_card_fling"),
                                cardListPosition = CardListPosition.Head,
                            )
                            SwitchPreference(
                                checked = predictiveBackImageFling,
                                onCheckedChange = {
                                    predictiveBackImageFling = it
                                    App.settings.applyPredictiveBackImageFling(it)
                                },
                                title = "图片返回跟手轨迹",
                                summary = "看图页预测性返回时跟随手指偏移，并顺着甩出方向缩回原图",
                                modifier = Modifier.testTag("settings_predictive_back_image_fling"),
                                cardListPosition = CardListPosition.Middle,
                            )
                            SwitchPreference(
                                checked = predictiveBackBlur,
                                onCheckedChange = {
                                    predictiveBackBlur = it
                                    App.settings.applyPredictiveBackBlur(it)
                                },
                                title = "返回跟手背景模糊",
                                summary = if (blurEffects) {
                                    "走跟手轨迹返回时模糊上一级页面，随归位逐渐变清晰"
                                } else {
                                    "需先在设置中开启「模糊效果」"
                                },
                                modifier = Modifier.testTag("settings_predictive_back_blur"),
                                cardListPosition = CardListPosition.Tail,
                                enabled = blurEffects,
                            )
                        }
                    }
                }
            }
        }

        CompositionLocalProvider(
            LocalBackdrop provides if (blurEffects) backdrop else null,
            LocalEdgeToEdge provides true,
        ) {
            PinnedBarDensity {
                AppBarSurface(
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
                    containerColor = pageColor,
                    scrollFraction = rememberListScrollFraction(listState),
                ) {
                    SmallTopAppBar(
                        title = "预测性返回手势设置",
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
fun MediaTopBarSettingsScreen(nav: NavState) {
    val blurEffects = blurEffectsEnabled()
    val pageColor = pageContainerColor()
    val backdrop = rememberLayerBackdrop()
    val topBarHeight = pinnedHeight(TopAppBarDefaults.SmallTopAppBarCenterHeight)
    val listState = rememberLazyListState()
    BindScrollToTop(listState)

    val videoButtons = App.settings.videoTopBarButtons
    val imageButtons = App.settings.imageTopBarButtons
    val showCounter = App.settings.imageTopBarShowCounter
    var draggingAny by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(pageColor)) {
        Box(Modifier.fillMaxSize().layerBackdropIf(blurEffects, backdrop).background(pageColor)) {
            LazyColumn(
                state = listState,
                userScrollEnabled = !draggingAny,
                modifier = Modifier.fillMaxSize().scrollEndHaptic().overScrollVertical(),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    top = glassBarContentTop(topBarHeight, 10.dp),
                    end = 12.dp,
                    bottom = 24.dp + navBottomPadding(),
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        text = "勾选需要显示的按钮，长按已勾选项目并上下拖动即可调整从左到右的排列顺序。",
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = COUITheme.colorScheme.onSurfaceSecondary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }

                item {
                    SectionTitle("视频播放顶栏")
                }

                item {
                    TopBarOrderEditorCard(
                        allItems = VideoTopBarButton.entries,
                        enabledItems = videoButtons,
                        labelOf = { it.label },
                        onUpdate = { App.settings.applyVideoTopBarButtons(it) },
                        onDraggingChange = { draggingAny = it },
                    )
                }

                item {
                    SectionTitle("看图页顶栏")
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = CardDefaults.GroupCornerRadius,
                        insideMargin = PaddingValues(0.dp),
                        weight = CardDefaults.GroupWeight,
                    ) {
                        SwitchPreference(
                            checked = showCounter,
                            onCheckedChange = { App.settings.applyImageTopBarShowCounter(it) },
                            title = "多图显示页码",
                            summary = "开启后多图顶部居中显示页码，单图自动隐藏",
                            cardListPosition = CardListPosition.Full,
                        )
                    }
                }

                item {
                    TopBarOrderEditorCard(
                        allItems = ImageTopBarButton.entries,
                        enabledItems = imageButtons,
                        labelOf = { it.label },
                        onUpdate = { App.settings.applyImageTopBarButtons(it) },
                        onDraggingChange = { draggingAny = it },
                    )
                }
            }
        }

        CompositionLocalProvider(
            LocalBackdrop provides if (blurEffects) backdrop else null,
            LocalEdgeToEdge provides true,
        ) {
            PinnedBarDensity {
                AppBarSurface(
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
                    containerColor = pageColor,
                    scrollFraction = rememberListScrollFraction(listState),
                ) {
                    SmallTopAppBar(
                        title = "媒体顶栏",
                        color = Color.Transparent,
                        showDivider = false,
                        gradientGlass = false,
                        navigationIcon = { BackIconButton { nav.pop() } },
                        actions = {
                            TextButton(
                                text = "重置",
                                onClick = { App.settings.resetMediaTopBarSettings() },
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun <T : Any> TopBarOrderEditorCard(
    allItems: List<T>,
    enabledItems: List<T>,
    labelOf: (T) -> String,
    onUpdate: (List<T>) -> Unit,
    onDraggingChange: (Boolean) -> Unit = {},
) {
    val haptic = LocalHapticFeedback.current
    val currentEnabled by rememberUpdatedState(enabledItems)
    val currentOnUpdate by rememberUpdatedState(onUpdate)
    val currentOnDraggingChange by rememberUpdatedState(onDraggingChange)

    var draggingItem by remember { mutableStateOf<T?>(null) }
    var dragOrder by remember { mutableStateOf<List<T>?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val rowHeights = remember { mutableStateMapOf<T, Float>() }

    val activeEnabled = dragOrder ?: enabledItems
    val orderedRows = activeEnabled + allItems.filter { it !in activeEnabled }

    fun finishDrag(commit: Boolean) {
        val result = dragOrder
        draggingItem = null
        dragOrder = null
        dragOffsetY = 0f
        currentOnDraggingChange(false)
        if (commit && result != null && result != currentEnabled) {
            currentOnUpdate(result)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = CardDefaults.GroupCornerRadius,
        insideMargin = PaddingValues(vertical = 4.dp),
        weight = CardDefaults.GroupWeight,
    ) {
        orderedRows.forEach { item ->
            key(item) {
                val enabledIndex = activeEnabled.indexOf(item)
                val checked = enabledIndex >= 0
                val isDragging = draggingItem == item
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { rowHeights[item] = it.size.height.toFloat() }
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer {
                            if (isDragging) {
                                translationY = dragOffsetY
                                scaleX = 1.02f
                                scaleY = 1.02f
                                alpha = 0.94f
                            }
                        }
                        .then(
                            if (isDragging) {
                                Modifier
                                    .padding(horizontal = 6.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(COUITheme.colorScheme.primary.copy(alpha = 0.08f))
                            } else {
                                Modifier
                            },
                        )
                        .then(
                            if (checked) {
                                Modifier.pointerInput(item) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            if (item !in currentEnabled) return@detectDragGesturesAfterLongPress
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            draggingItem = item
                                            dragOrder = currentEnabled
                                            dragOffsetY = 0f
                                            currentOnDraggingChange(true)
                                        },
                                        onDrag = { change, dragAmount ->
                                            if (draggingItem != item) return@detectDragGesturesAfterLongPress
                                            change.consume()
                                            dragOffsetY += dragAmount.y
                                            var cur = dragOrder ?: currentEnabled
                                            var idx = cur.indexOf(item)
                                            while (idx in 0 until cur.lastIndex) {
                                                val nextH = rowHeights[cur[idx + 1]] ?: break
                                                if (dragOffsetY > nextH * 0.5f) {
                                                    cur = cur.toMutableList().apply { add(idx + 1, removeAt(idx)) }
                                                    dragOrder = cur
                                                    dragOffsetY -= nextH
                                                    idx++
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                } else {
                                                    break
                                                }
                                            }
                                            while (idx > 0) {
                                                val prevH = rowHeights[cur[idx - 1]] ?: break
                                                if (dragOffsetY < -prevH * 0.5f) {
                                                    cur = cur.toMutableList().apply { add(idx - 1, removeAt(idx)) }
                                                    dragOrder = cur
                                                    dragOffsetY += prevH
                                                    idx--
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                } else {
                                                    break
                                                }
                                            }
                                        },
                                        onDragEnd = { finishDrag(commit = true) },
                                        onDragCancel = { finishDrag(commit = false) },
                                    )
                                }
                            } else {
                                Modifier
                            },
                        )
                        .clickable(enabled = draggingItem == null) {
                            val next = if (checked) enabledItems - item else enabledItems + item
                            onUpdate(next)
                        }
                        .padding(
                            horizontal = if (isDragging) 10.dp else 16.dp,
                            vertical = 12.dp,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        state = if (checked) ToggleableState.On else ToggleableState.Off,
                        onClick = null,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = labelOf(item),
                        fontSize = 15.sp,
                        color = if (checked) COUITheme.colorScheme.onSurface else COUITheme.colorScheme.onSurfaceSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    if (checked) {
                        DragHandleIndicator(active = isDragging)
                    } else {
                        Text(
                            text = "已隐藏",
                            fontSize = 12.sp,
                            color = COUITheme.colorScheme.onSurfaceSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DragHandleIndicator(active: Boolean) {
    val tint = if (active) {
        COUITheme.colorScheme.primary
    } else {
        COUITheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.45f)
    }
    Column(
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        repeat(3) {
            Box(
                Modifier
                    .width(16.dp)
                    .height(1.8.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(tint),
            )
        }
    }
}

private fun argbToHsv(argb: Int, out: FloatArray) {
    val r = ((argb ushr 16) and 0xFF) / 255f
    val g = ((argb ushr 8) and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val h = when {
        delta == 0f -> 0f
        max == r -> 60f * (((g - b) / delta) % 6f)
        max == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }.let { if (it < 0f) it + 360f else it }
    val s = if (max == 0f) 0f else delta / max
    out[0] = h
    out[1] = s
    out[2] = max
}
