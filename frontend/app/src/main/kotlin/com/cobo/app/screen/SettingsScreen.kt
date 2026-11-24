package com.cobo.app.screen

import android.content.Context
import android.graphics.Color as AndroidColor
import android.webkit.CookieManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.SingletonImageLoader
import com.cobo.app.App
import com.cobo.app.data.Account
import com.cobo.app.data.BottomLeftAction
import com.cobo.app.data.FeedTabSelection
import com.cobo.app.data.FollowGroup
import com.cobo.app.data.HotBoard
import com.cobo.app.data.ImageBrowseQuality
import com.cobo.app.data.MeIconStyle
import com.cobo.app.data.SearchPanelAnimation
import com.cobo.app.data.Updater
import com.cobo.app.data.feedTabOptions
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
import io.github.suqi8.coui.kmp.basic.TextField
import io.github.suqi8.coui.kmp.basic.TextFieldDefaults
import io.github.suqi8.coui.kmp.basic.TextFieldMode
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.layerBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Undo
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.layout.DialogDefaults
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.preference.OverlayDropdownPreference
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 设置页分组定义，用于在「我」一级页面展示分组目录，并跳转到对应的二级设置页。
 */
enum class SettingsGroup(val title: String, val summary: String) {
    Display("显示与交互", "深色模式、强调色、字号、手势与「我」页布局"),
    Feed("微博浏览", "主页分栏、卡片外框、倒序浏览与热搜榜"),
    AccountAndContent("账号与内容", "账号管理、快捷切号、底栏头像与本地关注"),
    Update("更新", "检查更新、更新日志与自动更新"),
    Other("其他", "清理缓存、关于与账号退出"),
}

@Stable
private class SettingsUiState(context: Context) {
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
    var allowLandscape by mutableStateOf(App.settings.allowLandscape)
    var searchPanelAnimation by mutableStateOf(App.settings.searchPanelAnimation)
    var confirmLike by mutableStateOf(App.settings.confirmLike)
    var inAppBrowser by mutableStateOf(App.settings.inAppBrowser)
    var quickSwitchAccount by mutableStateOf(App.settings.quickSwitchAccount)
    var profileTabAvatar by mutableStateOf(App.settings.profileTabAvatar)
    var profileShowStatuses by mutableStateOf(App.settings.profileShowStatuses)
    var reverseBrowse by mutableStateOf(App.settings.reverseBrowse)
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
    var pickHotBoards by mutableStateOf(false)
    var pickAccentColor by mutableStateOf(false)
    var syncingLocalFavorites by mutableStateOf(false)
}

@Composable
private fun rememberSettingsUiState(): SettingsUiState {
    val context = LocalContext.current
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = rememberSettingsUiState()

    SettingsScaffold(
        nav = nav,
        title = "设置",
        state = state,
    ) {
        displaySettingsItems(nav = nav, state = state, showHeader = true)
        feedSettingsItems(state = state, showHeader = true)
        accountSettingsItems(
            nav = nav,
            state = state,
            scope = scope,
            showHeader = true,
            includeLibraryShortcuts = true,
        )
        updateSettingsItems(nav = nav, state = state, scope = scope, showHeader = true)
        otherSettingsItems(nav = nav, state = state, context = context, showHeader = true)

        item {
            Text(
                "基于 m.weibo.cn Web API 的第三方微博客户端。\n仅用于个人学习与研究。",
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = COUITheme.colorScheme.onSurfaceSecondary,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            )
        }
    }
}

/**
 * 分组二级设置页：从「我」一级页面点进具体设置分组时使用，复用同一套设置项与弹窗。
 */
@Composable
fun SettingsGroupScreen(nav: NavState, group: SettingsGroup) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = rememberSettingsUiState()

    SettingsScaffold(
        nav = nav,
        title = group.title,
        state = state,
    ) {
        when (group) {
            SettingsGroup.Display -> displaySettingsItems(nav = nav, state = state, showHeader = false)
            SettingsGroup.Feed -> feedSettingsItems(state = state, showHeader = false)
            SettingsGroup.AccountAndContent -> accountSettingsItems(
                nav = nav,
                state = state,
                scope = scope,
                showHeader = false,
                includeLibraryShortcuts = false,
            )
            SettingsGroup.Update -> updateSettingsItems(nav = nav, state = state, scope = scope, showHeader = false)
            SettingsGroup.Other -> otherSettingsItems(nav = nav, state = state, context = context, showHeader = false)
        }
    }
}

@Composable
private fun SettingsScaffold(
    nav: NavState,
    title: String,
    state: SettingsUiState,
    content: LazyListScope.() -> Unit,
) {
    val pageColor = pageContainerColor()
    // 顶栏玻璃要有东西可采样：把列表录进 backdrop，顶栏浮在上面做渐变模糊。
    val backdrop = rememberLayerBackdrop()
    val topBarHeight = pinnedHeight(TopAppBarDefaults.SmallTopAppBarCenterHeight)
    val listState = rememberLazyListState()
    BindScrollToTop(listState)

    Box(Modifier.fillMaxSize().background(pageColor)) {
        Box(Modifier.fillMaxSize().layerBackdropIf(state.blurEffects, backdrop).background(pageColor)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().scrollEndHaptic(),
                // 列表铺到屏幕最底，滚到末页时卡片从系统导航条下方穿过（沉浸），
                // 末项靠 contentPadding 抬到导航条之上，不会被挡住。
                contentPadding = PaddingValues(
                    start = 12.dp,
                    top = glassBarContentTop(topBarHeight, 10.dp),
                    end = 12.dp,
                    bottom = 24.dp + navBottomPadding(),
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        }

        CompositionLocalProvider(
            LocalBackdrop provides if (state.blurEffects) backdrop else null,
            // 内容铺到状态栏下面，顶栏玻璃才有东西可采样（COUI 17 规范要求 edgeToEdge）。
            LocalEdgeToEdge provides true,
        ) {
            PinnedBarDensity {
                AppBarSurface(
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
                    containerColor = pageColor,
                    scrollFraction = rememberListScrollFraction(listState),
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

    SettingsDialogs(nav = nav, state = state)
}

private fun LazyListScope.displaySettingsItems(
    nav: NavState,
    state: SettingsUiState,
    showHeader: Boolean,
) {
    if (showHeader) {
        item { SectionTitle("显示与交互", topPadding = 4.dp) }
    }
    item {
        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = CardDefaults.GroupCornerRadius,
            insideMargin = PaddingValues(0.dp),
            weight = CardDefaults.GroupWeight,
        ) {
            ArrowPreference(
                title = "深色模式",
                modifier = Modifier.testTag("settings_dark"),
                summary = when (state.darkMode) {
                    1 -> "浅色"
                    2 -> "深色"
                    else -> "跟随系统"
                },
                cardListPosition = CardListPosition.Head,
                onClick = { state.darkMode = App.settings.cycleDarkMode() },
            )
            ArrowPreference(
                title = "强调色",
                modifier = Modifier.testTag("settings_accent_color"),
                summary = accentColorSummary(state.accentColor),
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
                cardListPosition = CardListPosition.Middle,
                onClick = { state.pickAccentColor = true },
            )
            OverlayDropdownPreference(
                items = MeIconStyle.entries.map { it.label },
                selectedIndex = state.meIconStyle.ordinal,
                title = "「我」页图标样式",
                modifier = Modifier.testTag("settings_me_icon_style"),
                cardListPosition = CardListPosition.Middle,
                onSelectedIndexChange = { index ->
                    val picked = MeIconStyle.entries.getOrElse(index) { MeIconStyle.Colorful }
                    state.meIconStyle = picked
                    App.settings.applyMeIconStyle(picked)
                },
            )
            ArrowPreference(
                title = "字号",
                modifier = Modifier.testTag("settings_font"),
                summary = "${(state.fontScale * 100).toInt()}%",
                cardListPosition = CardListPosition.Middle,
                onClick = { state.fontScale = App.settings.cycleFontScale() },
            )
            ArrowPreference(
                title = "预测性返回手势设置",
                summary = if (App.settings.predictiveBack) "已开启" else "已关闭",
                modifier = Modifier.testTag("settings_predictive_back_entry"),
                cardListPosition = CardListPosition.Middle,
                onClick = { nav.push(Route.PredictiveBackSettings) },
            )
            SwitchPreference(
                checked = state.blurEffects,
                onCheckedChange = {
                    state.blurEffects = it
                    App.settings.applyBlurEffects(it)
                },
                title = "模糊效果",
                summary = "关闭后顶栏与玻璃面改画纯色，滑动更省电",
                modifier = Modifier.testTag("settings_blur"),
                cardListPosition = CardListPosition.Middle,
            )
            SwitchPreference(
                checked = state.allowLandscape,
                onCheckedChange = {
                    state.allowLandscape = it
                    App.settings.applyAllowLandscape(it)
                },
                title = "允许横屏",
                summary = "关闭后固定竖屏，不再进入左右双页",
                modifier = Modifier.testTag("settings_allow_landscape"),
                cardListPosition = CardListPosition.Middle,
            )
            SwitchPreference(
                checked = state.profileShowStatuses,
                onCheckedChange = {
                    state.profileShowStatuses = it
                    App.settings.applyProfileShowStatuses(it)
                },
                title = "「我」页展示我的微博",
                summary = if (state.profileShowStatuses) {
                    "开启后在「我」标签页直接展开我的全部微博"
                } else {
                    "关闭后「我」标签页作为互动中心与设置一级入口"
                },
                modifier = Modifier.testTag("settings_profile_show_statuses"),
                cardListPosition = CardListPosition.Middle,
            )
            ArrowPreference(
                title = "搜索面板动画",
                modifier = Modifier.testTag("settings_search_panel_anim"),
                summary = state.searchPanelAnimation.label,
                cardListPosition = CardListPosition.Middle,
                onClick = { state.pickSearchPanelAnimation = true },
            )
            ArrowPreference(
                title = "底栏左下角按钮",
                modifier = Modifier.testTag("settings_bottom_left_action"),
                summary = App.settings.bottomLeftAction.label,
                cardListPosition = CardListPosition.Middle,
                onClick = { state.pickBottomLeftAction = true },
            )
            ArrowPreference(
                title = "浏览图片",
                modifier = Modifier.testTag("settings_image_browse_quality"),
                summary = App.settings.imageBrowseQuality.label,
                cardListPosition = CardListPosition.Middle,
                onClick = { state.pickImageBrowseQuality = true },
            )
            SwitchPreference(
                checked = App.settings.livePhotoAutoPlay,
                onCheckedChange = { App.settings.applyLivePhotoAutoPlay(it) },
                title = "实况图自动播放",
                summary = "进入或滑动到实况图时自动播放一次，结束后仅支持长按重播",
                modifier = Modifier.testTag("settings_live_photo_auto_play"),
                cardListPosition = CardListPosition.Middle,
            )
            SwitchPreference(
                checked = App.settings.livePhotoSound,
                onCheckedChange = { App.settings.applyLivePhotoSound(it) },
                title = "实况图播放声音",
                summary = "播放实况图时同步播放附带的视频声音",
                modifier = Modifier.testTag("settings_live_photo_sound"),
                cardListPosition = CardListPosition.Middle,
            )
            SwitchPreference(
                checked = state.inAppBrowser,
                onCheckedChange = {
                    state.inAppBrowser = it
                    App.settings.applyInAppBrowser(it)
                },
                title = "使用内置浏览器打开外部网页",
                summary = "关闭后点击外部链接将直接调用系统浏览器打开",
                modifier = Modifier.testTag("settings_in_app_browser"),
                cardListPosition = CardListPosition.Middle,
            )
            SwitchPreference(
                checked = state.confirmLike,
                onCheckedChange = {
                    state.confirmLike = it
                    App.settings.applyConfirmLike(it)
                },
                title = "点赞前确认",
                modifier = Modifier.testTag("settings_confirm_like"),
                cardListPosition = CardListPosition.Tail,
            )
        }
    }
}

private fun LazyListScope.feedSettingsItems(
    state: SettingsUiState,
    showHeader: Boolean,
) {
    if (showHeader) {
        item { SectionTitle("微博浏览") }
    }
    item {
        val hotBoardsSummary = App.settings.hotBoards.let { picked ->
            when {
                picked.isEmpty() -> "已隐藏"
                picked.size == HotBoard.entries.size -> "全部"
                else -> HotBoard.entries.filter { it in picked }.joinToString("、") { it.label }
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = CardDefaults.GroupCornerRadius,
            insideMargin = PaddingValues(0.dp),
            weight = CardDefaults.GroupWeight,
        ) {
            ArrowPreference(
                title = "微博左侧栏",
                modifier = Modifier.testTag("settings_feed_left_tab"),
                summary = state.feedLeftTab.label,
                cardListPosition = CardListPosition.Head,
                onClick = { state.pickFeedLeft = true },
            )
            ArrowPreference(
                title = "微博中间栏",
                modifier = Modifier.testTag("settings_feed_center_tab"),
                summary = state.feedCenterTab.label,
                cardListPosition = CardListPosition.Middle,
                onClick = { state.pickFeedCenter = true },
            )
            ArrowPreference(
                title = "微博右侧栏",
                modifier = Modifier.testTag("settings_feed_right_tab"),
                summary = state.feedRightTab.label,
                cardListPosition = CardListPosition.Middle,
                onClick = { state.pickFeedRight = true },
            )
            SwitchPreference(
                checked = state.collapseFollowGroups,
                onCheckedChange = {
                    state.collapseFollowGroups = it
                    App.settings.applyCollapseFollowGroups(it)
                },
                title = "收纳关注分组",
                summary = "开启后收进中间栏二级菜单，关闭后直接全部展开",
                modifier = Modifier.testTag("settings_collapse_follow_groups"),
                cardListPosition = CardListPosition.Middle,
            )
            ArrowPreference(
                title = "搜索页热搜榜单",
                modifier = Modifier.testTag("settings_hot_boards"),
                summary = hotBoardsSummary,
                cardListPosition = CardListPosition.Middle,
                onClick = { state.pickHotBoards = true },
            )
            SwitchPreference(
                checked = state.reverseBrowse,
                onCheckedChange = {
                    state.reverseBrowse = it
                    App.settings.applyReverseBrowse(it)
                },
                title = "倒序浏览",
                summary = "刷新不回顶，从当前位置往上补看新微博",
                modifier = Modifier.testTag("settings_reverse_browse"),
                cardListPosition = CardListPosition.Middle,
            )
            SwitchPreference(
                checked = state.feedCardFrame,
                onCheckedChange = {
                    state.feedCardFrame = it
                    App.settings.applyFeedCardFrame(it)
                },
                title = "首页卡片外框",
                summary = if (state.feedCardFrame) {
                    "关闭后去掉卡片底色与圆角，正文横向与顶栏按钮对齐"
                } else {
                    "已去掉卡片外框，正文与顶栏按钮对齐"
                },
                modifier = Modifier.testTag("settings_feed_card_frame"),
                cardListPosition = CardListPosition.Middle,
            )
            SwitchPreference(
                checked = state.detailCardFrame,
                onCheckedChange = {
                    state.detailCardFrame = it
                    App.settings.applyDetailCardFrame(it)
                },
                title = "详情页卡片外框",
                summary = if (state.detailCardFrame) {
                    "关闭后主贴与热门评论去掉外框，正文与顶栏按钮对齐；楼中楼等内部块保留"
                } else {
                    "已去掉主贴与热门评论外框，正文与顶栏按钮对齐"
                },
                modifier = Modifier.testTag("settings_detail_card_frame"),
                cardListPosition = CardListPosition.Middle,
            )
            SwitchPreference(
                checked = state.foldLongText,
                onCheckedChange = {
                    state.foldLongText = it
                    App.settings.applyFoldLongText(it)
                },
                title = "长文折叠",
                summary = if (state.foldLongText) {
                    "信息流里超过 140 字的长微博只显示开头，点「展开全文」再看完整内容"
                } else {
                    "长微博在信息流里直接展开显示全文"
                },
                modifier = Modifier.testTag("settings_fold_long_text"),
                cardListPosition = CardListPosition.Middle,
            )
            SwitchPreference(
                checked = state.showVipLevel,
                onCheckedChange = {
                    state.showVipLevel = it
                    App.settings.applyShowVipLevel(it)
                },
                title = "显示 VIP 等级",
                summary = "开启后在昵称旁显示 VIP / SVIP / VVIP 及等级标识",
                modifier = Modifier.testTag("settings_show_vip_level"),
                cardListPosition = CardListPosition.Middle,
            )
            SwitchPreference(
                checked = state.showFanLevel,
                onCheckedChange = {
                    state.showFanLevel = it
                    App.settings.applyShowFanLevel(it)
                },
                title = "显示粉丝等级",
                summary = "开启后在评论区昵称旁显示铁粉、金粉、钻粉等粉丝标识",
                modifier = Modifier.testTag("settings_show_fan_level"),
                cardListPosition = CardListPosition.Middle,
            )
            SwitchPreference(
                checked = state.showVerifiedBadge,
                onCheckedChange = {
                    state.showVerifiedBadge = it
                    App.settings.applyShowVerifiedBadge(it)
                },
                title = "显示认证 V 标",
                summary = "开启后在认证用户头像右下角显示金 V / 蓝 V 标识",
                modifier = Modifier.testTag("settings_show_verified_badge"),
                cardListPosition = CardListPosition.Tail,
            )
        }
    }
}

private fun LazyListScope.accountSettingsItems(
    nav: NavState,
    state: SettingsUiState,
    scope: CoroutineScope,
    showHeader: Boolean,
    includeLibraryShortcuts: Boolean,
) {
    if (showHeader) {
        item { SectionTitle("账号与内容") }
    }
    item {
        val accounts = remember(state.localFollowUid) { App.store.accounts() }
        val localFollowAccount = accounts.firstOrNull { it.uid == state.localFollowUid }
        val localFollowSummary = if (localFollowAccount == null) {
            "跟随当前账号"
        } else {
            "用 ${localFollowAccount.name.ifBlank { "UID ${localFollowAccount.uid}" }} 的身份取数"
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = CardDefaults.GroupCornerRadius,
            insideMargin = PaddingValues(0.dp),
            weight = CardDefaults.GroupWeight,
        ) {
            ArrowPreference(
                title = "账号管理",
                modifier = Modifier.testTag("settings_accounts"),
                cardListPosition = CardListPosition.Head,
                onClick = { nav.push(Route.Accounts) },
            )
            SwitchPreference(
                checked = state.quickSwitchAccount,
                onCheckedChange = {
                    state.quickSwitchAccount = it
                    App.settings.applyQuickSwitchAccount(it)
                },
                title = "长按「我」切换账号",
                summary = "长按悬浮底栏的「我」快速切换已保存账号",
                modifier = Modifier.testTag("settings_quick_switch_account"),
                cardListPosition = CardListPosition.Middle,
            )
            SwitchPreference(
                checked = state.profileTabAvatar,
                onCheckedChange = {
                    state.profileTabAvatar = it
                    App.settings.applyProfileTabAvatar(it)
                },
                title = "底栏「我」显示头像",
                summary = "在悬浮底栏的「我」位置显示当前账号头像",
                modifier = Modifier.testTag("settings_profile_tab_avatar"),
                cardListPosition = if (!includeLibraryShortcuts && state.guest) CardListPosition.Tail else CardListPosition.Middle,
            )
            // 纯本地模式一律用访客身份取数，没有账号可选。
            if (!state.guest) {
                ArrowPreference(
                    title = "本地关注使用账号",
                    modifier = Modifier.testTag("settings_local_follow"),
                    summary = localFollowSummary,
                    cardListPosition = CardListPosition.Middle,
                    onClick = { state.pickLocalFollow = true },
                )
                ArrowPreference(
                    title = "同步本地收藏",
                    summary = if (state.syncingLocalFavorites) {
                        "正在同步微博收藏"
                    } else {
                        "将微博收藏复制到本地收藏"
                    },
                    modifier = Modifier.testTag("settings_sync_local_favorites"),
                    cardListPosition = if (includeLibraryShortcuts) CardListPosition.Middle else CardListPosition.Tail,
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
            }
            if (includeLibraryShortcuts) {
                ArrowPreference(
                    title = "草稿箱",
                    modifier = Modifier.testTag("settings_drafts"),
                    summary = "${App.store.drafts().size} 条草稿",
                    cardListPosition = CardListPosition.Middle,
                    onClick = { nav.push(Route.Drafts) },
                )
                ArrowPreference(
                    title = "浏览历史",
                    modifier = Modifier.testTag("settings_history"),
                    summary = "${App.store.history().size} 条记录",
                    cardListPosition = CardListPosition.Middle,
                    onClick = { nav.push(Route.History) },
                )
                // 本地收藏全局共用，纯本地模式下同样可直接打开。
                ArrowPreference(
                    title = "收藏",
                    modifier = Modifier.testTag("settings_favorites"),
                    summary = "${App.repo.localFavoriteIds.size} 条收藏",
                    cardListPosition = CardListPosition.Middle,
                    onClick = { nav.push(Route.Favorites) },
                )
                ArrowPreference(
                    title = "话题 / 超话",
                    modifier = Modifier.testTag("settings_topic"),
                    cardListPosition = if (state.guest) CardListPosition.Tail else CardListPosition.Middle,
                    onClick = { nav.push(Route.Topic()) },
                )
                if (!state.guest) {
                    ArrowPreference(
                        title = "附近",
                        modifier = Modifier.testTag("settings_nearby"),
                        cardListPosition = CardListPosition.Tail,
                        onClick = { nav.push(Route.Nearby) },
                    )
                }
            }
        }
    }
}

private fun LazyListScope.updateSettingsItems(
    nav: NavState,
    state: SettingsUiState,
    scope: CoroutineScope,
    showHeader: Boolean,
) {
    if (showHeader) {
        item { SectionTitle("更新") }
    }
    item {
        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = CardDefaults.GroupCornerRadius,
            insideMargin = PaddingValues(0.dp),
            weight = CardDefaults.GroupWeight,
        ) {
            ArrowPreference(
                title = "检查更新",
                modifier = Modifier.testTag("settings_update"),
                summary = if (App.checkingUpdate) "正在检查…" else "当前版本 ${state.version}",
                cardListPosition = CardListPosition.Head,
                onClick = { scope.launch { App.checkUpdate(manual = true) } },
            )
            ArrowPreference(
                title = "更新日志",
                modifier = Modifier.testTag("settings_changelog"),
                cardListPosition = CardListPosition.Middle,
                onClick = { nav.push(Route.Changelog) },
            )
            SwitchPreference(
                checked = state.autoCheckUpdate,
                onCheckedChange = {
                    state.autoCheckUpdate = it
                    App.settings.applyAutoCheckUpdate(it)
                },
                title = "自动检查更新",
                modifier = Modifier.testTag("settings_auto_update"),
                cardListPosition = CardListPosition.Middle,
            )
            SwitchPreference(
                checked = state.acceptPrerelease,
                onCheckedChange = {
                    state.acceptPrerelease = it
                    App.settings.applyAcceptPrerelease(it)
                },
                title = "接收预发布版本",
                modifier = Modifier.testTag("settings_prerelease"),
                cardListPosition = CardListPosition.Middle,
            )
            SwitchPreference(
                checked = state.inAppUpdate,
                onCheckedChange = {
                    state.inAppUpdate = it
                    App.settings.applyInAppUpdate(it)
                },
                title = "应用内更新",
                modifier = Modifier.testTag("settings_in_app_update"),
                cardListPosition = CardListPosition.Tail,
            )
        }
    }
}

private fun LazyListScope.otherSettingsItems(
    nav: NavState,
    state: SettingsUiState,
    context: Context,
    showHeader: Boolean,
) {
    if (showHeader) {
        item { SectionTitle("其他") }
    }
    item {
        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = CardDefaults.GroupCornerRadius,
            insideMargin = PaddingValues(0.dp),
            weight = CardDefaults.GroupWeight,
        ) {
            ArrowPreference(
                title = "清理缓存",
                modifier = Modifier.testTag("settings_clear_cache"),
                cardListPosition = CardListPosition.Head,
                onClick = {
                    runCatching {
                        val loader = SingletonImageLoader.get(context)
                        loader.memoryCache?.clear()
                        loader.diskCache?.clear()
                        context.cacheDir.resolve("live").deleteRecursively()
                    }
                    App.cache.clear()
                    state.message = "已清理图片与内容缓存"
                },
            )
            ArrowPreference(
                title = "关于",
                modifier = Modifier.testTag("settings_about"),
                summary = "v${state.version} · 上游库与开源致谢",
                cardListPosition = CardListPosition.Tail,
                onClick = { nav.push(Route.About) },
            )
        }
    }

    // 退出是破坏性操作，单独一张卡，不和其他条目挤在一起。
    item {
        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = CardDefaults.GroupCornerRadius,
            insideMargin = PaddingValues(0.dp),
            weight = CardDefaults.GroupWeight,
        ) {
            if (state.guest) {
                ArrowPreference(
                    title = "退出纯本地模式",
                    modifier = Modifier.testTag("settings_leave_local"),
                    summary = "回到登录页，本地关注名单保留",
                    cardListPosition = CardListPosition.Full,
                    onClick = {
                        App.settings.applyLocalMode(false)
                        nav.reset(Route.SmsLogin())
                    },
                )
            } else {
                ArrowPreference(
                    title = "退出登录",
                    modifier = Modifier.testTag("settings_logout"),
                    summary = "清除本地会话",
                    cardListPosition = CardListPosition.Full,
                    onClick = { state.logout = true },
                )
            }
        }
    }

    if (state.message.isNotBlank()) {
        item {
            Text(
                state.message,
                fontSize = 12.sp,
                color = COUITheme.colorScheme.onSurfaceSecondary,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
    }
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

    if (state.logout) {
        ConfirmDialog(
            title = "退出登录",
            message = "会清除本地保存的 Cookie，下次需要重新登录。",
            confirmText = "退出",
            onDismiss = { state.logout = false },
            onConfirm = {
                state.logout = false
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
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
            title = "浏览图片",
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
        FloatArray(3).also { AndroidColor.colorToHSV(initialColor.toArgb(), it) }
    }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var brightness by remember { mutableFloatStateOf(initialHsv[2]) }
    var hexInput by remember { mutableStateOf(formatHexColor(initialColor.toArgb())) }

    val isHueCycle = pickedArgb == AccentHueCycle
    LaunchedEffect(isHueCycle, dark) {
        if (!isHueCycle) return@LaunchedEffect
        val startMs = System.currentTimeMillis()
        val startHue = hue
        while (true) {
            val elapsed = System.currentTimeMillis() - startMs
            val nextHue = (startHue + elapsed / 100f) % 360f
            val cycleRgb = hueCycleArgb(nextHue, dark)
            val hsv = FloatArray(3)
            AndroidColor.colorToHSV(cycleRgb, hsv)
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
        AndroidColor.colorToHSV(displayArgb, hsv)
        hue = hsv[0]
        saturation = hsv[1]
        brightness = hsv[2]
        hexInput = formatHexColor(displayArgb)
    }

    fun updateFromHsv() {
        val c = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, brightness))
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
                            AndroidColor.colorToHSV(parsed, hsv)
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
                modifier = Modifier.fillMaxSize().scrollEndHaptic(),
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
