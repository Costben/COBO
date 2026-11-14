package com.cobo.app.screen

import android.webkit.CookieManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.SingletonImageLoader
import com.cobo.app.App
import com.cobo.app.data.Account
import com.cobo.app.data.FeedTabSelection
import com.cobo.app.data.FollowGroup
import com.cobo.app.data.HotBoard
import com.cobo.app.data.SearchPanelAnimation
import com.cobo.app.data.Updater
import com.cobo.app.data.feedTabOptions
import com.cobo.app.ui.NavState
import com.cobo.app.ui.layerBackdropIf
import com.cobo.app.ui.PinnedBarDensity
import com.cobo.app.ui.glassBarContentTop
import com.cobo.app.ui.pageContainerColor
import com.cobo.app.ui.pinnedHeight
import com.cobo.app.ui.Route
import com.cobo.app.ui.components.BackIconButton
import com.cobo.app.ui.components.ConfirmDialog
import com.cobo.app.ui.components.PreferenceDivider
import com.cobo.app.ui.components.SectionTitle
import com.cobo.app.ui.components.navBottomPadding
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.Checkbox
import io.github.suqi8.coui.kmp.basic.DropdownImpl
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.ListPopupColumn
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.basic.LocalEdgeToEdge
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.layerBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(nav: NavState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val version = remember { Updater.currentVersion(context) }
    var darkMode by remember { mutableStateOf(App.settings.darkMode) }
    var fontScale by remember { mutableStateOf(App.settings.fontScale) }
    var autoCheckUpdate by remember { mutableStateOf(App.settings.autoCheckUpdate) }
    var acceptPrerelease by remember { mutableStateOf(App.settings.acceptPrerelease) }
    var inAppUpdate by remember { mutableStateOf(App.settings.inAppUpdate) }
    var predictiveBack by remember { mutableStateOf(App.settings.predictiveBack) }
    var blurEffects by remember { mutableStateOf(App.settings.blurEffects) }
    var searchPanelAnimation by remember { mutableStateOf(App.settings.searchPanelAnimation) }
    var confirmLike by remember { mutableStateOf(App.settings.confirmLike) }
    var message by remember { mutableStateOf("") }
    var logout by remember { mutableStateOf(false) }
    var localFollowUid by remember { mutableStateOf(App.settings.localFollowUid) }
    val guest = App.repo.guest
    var pickLocalFollow by remember { mutableStateOf(false) }
    var feedLeftTab by remember { mutableStateOf(App.settings.feedLeftTab) }
    var feedCenterTab by remember { mutableStateOf(App.settings.feedCenterTab) }
    var feedRightTab by remember { mutableStateOf(App.settings.feedRightTab) }
    var feedGroups by remember { mutableStateOf<List<FollowGroup>>(emptyList()) }
    var pickFeedLeft by remember { mutableStateOf(false) }
    var pickFeedCenter by remember { mutableStateOf(false) }
    var pickFeedRight by remember { mutableStateOf(false) }
    var pickSearchPanelAnimation by remember { mutableStateOf(false) }
    var pickHotBoards by remember { mutableStateOf(false) }

    // 关注分组是账号里的数据：只在真的要选栏位内容时才去网页版拉一次，拉不到就只列内置流。
    LaunchedEffect(pickFeedLeft, pickFeedCenter, pickFeedRight) {
        if (guest || !(pickFeedLeft || pickFeedCenter || pickFeedRight)) return@LaunchedEffect
        runCatching { App.repo.followGroups() }.onSuccess { feedGroups = it }
    }
    // 纯本地模式取不到分组，就别把上次存的分组当成一个可选但选不动的项列出来。
    val leftOptions = feedTabOptions(feedGroups, allowNone = true, current = if (guest) null else feedLeftTab)
    val centerOptions = feedTabOptions(feedGroups, allowNone = false, current = if (guest) null else feedCenterTab)
    val rightOptions = feedTabOptions(feedGroups, allowNone = true, current = if (guest) null else feedRightTab)

    // 只在选中的 uid 变化时读一次账号表，切到别的账号后摘要跟着换。
    val accounts = remember(localFollowUid) { App.store.accounts() }
    val localFollowAccount = accounts.firstOrNull { it.uid == localFollowUid }
    val localFollowSummary = if (localFollowAccount == null) {
        "跟随当前账号"
    } else {
        "用 ${localFollowAccount.name.ifBlank { "UID ${localFollowAccount.uid}" }} 的身份取数"
    }

    val hotBoardsSummary = App.settings.hotBoards.let { picked ->
        when {
            picked.isEmpty() -> "已隐藏"
            picked.size == HotBoard.entries.size -> "全部"
            else -> HotBoard.entries.filter { it in picked }.joinToString("、") { it.label }
        }
    }

    val pageColor = pageContainerColor()
    // 顶栏玻璃要有东西可采样：把列表录进 backdrop，顶栏浮在上面做渐变模糊。
    val backdrop = rememberLayerBackdrop()
    val topBarHeight = pinnedHeight(TopAppBarDefaults.SmallTopAppBarCenterHeight)

    Box(Modifier.fillMaxSize().background(pageColor)) {
        Box(Modifier.fillMaxSize().layerBackdropIf(blurEffects, backdrop).background(pageColor)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
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
            item { SectionTitle("显示与交互", topPadding = 4.dp) }

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
                        summary = when (darkMode) {
                            1 -> "浅色"
                            2 -> "深色"
                            else -> "跟随系统"
                        },
                        cardListPosition = CardListPosition.Head,
                        onClick = {
                            darkMode = App.settings.cycleDarkMode()
                        },
                    )
                    PreferenceDivider()
                    ArrowPreference(
                        title = "字号",
                        modifier = Modifier.testTag("settings_font"),
                        summary = "${(fontScale * 100).toInt()}%",
                        onClick = {
                            fontScale = App.settings.cycleFontScale()
                        },
                    )
                    PreferenceDivider()
                    SwitchPreference(
                        checked = predictiveBack,
                        onCheckedChange = {
                            predictiveBack = it
                            App.settings.applyPredictiveBack(it)
                        },
                        title = "预测性返回手势",
                        modifier = Modifier.testTag("settings_predictive_back"),
                        cardListPosition = CardListPosition.Middle,
                    )
                    PreferenceDivider()
                    SwitchPreference(
                        checked = blurEffects,
                        onCheckedChange = {
                            blurEffects = it
                            App.settings.applyBlurEffects(it)
                        },
                        title = "模糊效果",
                        summary = "关闭后顶栏与玻璃面改画纯色，滑动更省电",
                        modifier = Modifier.testTag("settings_blur"),
                        cardListPosition = CardListPosition.Middle,
                    )
                    PreferenceDivider()
                    ArrowPreference(
                        title = "搜索面板动画",
                        modifier = Modifier.testTag("settings_search_panel_anim"),
                        summary = searchPanelAnimation.label,
                        cardListPosition = CardListPosition.Middle,
                        onClick = { pickSearchPanelAnimation = true },
                    )
                    PreferenceDivider()
                    SwitchPreference(
                        checked = confirmLike,
                        onCheckedChange = {
                            confirmLike = it
                            App.settings.applyConfirmLike(it)
                        },
                        title = "点赞前确认",
                        modifier = Modifier.testTag("settings_confirm_like"),
                        cardListPosition = CardListPosition.Tail,
                    )
                }
            }

            item { SectionTitle("微博") }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = CardDefaults.GroupCornerRadius,
                    insideMargin = PaddingValues(0.dp),
                    weight = CardDefaults.GroupWeight,
                ) {
                    ArrowPreference(
                        title = "微博左侧栏",
                        modifier = Modifier.testTag("settings_feed_left_tab"),
                        summary = feedLeftTab.label,
                        cardListPosition = CardListPosition.Head,
                        onClick = { pickFeedLeft = true },
                    )
                    PreferenceDivider()
                    ArrowPreference(
                        title = "微博中间栏",
                        modifier = Modifier.testTag("settings_feed_center_tab"),
                        summary = feedCenterTab.label,
                        cardListPosition = CardListPosition.Middle,
                        onClick = { pickFeedCenter = true },
                    )
                    PreferenceDivider()
                    ArrowPreference(
                        title = "微博右侧栏",
                        modifier = Modifier.testTag("settings_feed_right_tab"),
                        summary = feedRightTab.label,
                        cardListPosition = CardListPosition.Middle,
                        onClick = { pickFeedRight = true },
                    )
                    PreferenceDivider()
                    ArrowPreference(
                        title = "搜索页热搜榜单",
                        modifier = Modifier.testTag("settings_hot_boards"),
                        summary = hotBoardsSummary,
                        cardListPosition = CardListPosition.Tail,
                        onClick = { pickHotBoards = true },
                    )
                }
            }

            item { SectionTitle("账号与内容") }

            item {
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
                    // 纯本地模式一律用访客身份取数，没有账号可选。
                    if (!guest) {
                        PreferenceDivider()
                        ArrowPreference(
                            title = "本地关注使用账号",
                            modifier = Modifier.testTag("settings_local_follow"),
                            summary = localFollowSummary,
                            onClick = { pickLocalFollow = true },
                        )
                    }
                    PreferenceDivider()
                    ArrowPreference(
                        title = "草稿箱",
                        modifier = Modifier.testTag("settings_drafts"),
                        summary = "${App.store.drafts().size} 条草稿",
                        onClick = { nav.push(Route.Drafts) },
                    )
                    PreferenceDivider()
                    ArrowPreference(
                        title = "浏览历史",
                        modifier = Modifier.testTag("settings_history"),
                        summary = "${App.store.history().size} 条记录",
                        onClick = { nav.push(Route.History) },
                    )
                    // 收藏和附近挂在账号上，访客身份拿不到，纯本地模式不给入口。
                    if (!guest) {
                        PreferenceDivider()
                        ArrowPreference(
                            title = "收藏",
                            modifier = Modifier.testTag("settings_favorites"),
                            onClick = { nav.push(Route.Container("230267", "收藏")) },
                        )
                    }
                    PreferenceDivider()
                    ArrowPreference(
                        title = "话题 / 超话",
                        modifier = Modifier.testTag("settings_topic"),
                        cardListPosition = if (guest) CardListPosition.Tail else CardListPosition.None,
                        onClick = { nav.push(Route.Topic()) },
                    )
                    if (!guest) {
                        PreferenceDivider()
                        ArrowPreference(
                            title = "附近",
                            modifier = Modifier.testTag("settings_nearby"),
                            cardListPosition = CardListPosition.Tail,
                            onClick = { nav.push(Route.Nearby) },
                        )
                    }
                }
            }

            item { SectionTitle("更新") }

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
                        summary = if (App.checkingUpdate) "正在检查…" else "当前版本 $version",
                        cardListPosition = CardListPosition.Head,
                        onClick = { scope.launch { App.checkUpdate(manual = true) } },
                    )
                    PreferenceDivider()
                    ArrowPreference(
                        title = "更新日志",
                        modifier = Modifier.testTag("settings_changelog"),
                        cardListPosition = CardListPosition.Middle,
                        onClick = { nav.push(Route.Changelog) },
                    )
                    PreferenceDivider()
                    SwitchPreference(
                        checked = autoCheckUpdate,
                        onCheckedChange = {
                            autoCheckUpdate = it
                            App.settings.applyAutoCheckUpdate(it)
                        },
                        title = "自动检查更新",
                        modifier = Modifier.testTag("settings_auto_update"),
                        cardListPosition = CardListPosition.Middle,
                    )
                    PreferenceDivider()
                    SwitchPreference(
                        checked = acceptPrerelease,
                        onCheckedChange = {
                            acceptPrerelease = it
                            App.settings.applyAcceptPrerelease(it)
                        },
                        title = "接收预发布版本",
                        modifier = Modifier.testTag("settings_prerelease"),
                        cardListPosition = CardListPosition.Middle,
                    )
                    PreferenceDivider()
                    SwitchPreference(
                        checked = inAppUpdate,
                        onCheckedChange = {
                            inAppUpdate = it
                            App.settings.applyInAppUpdate(it)
                        },
                        title = "应用内更新",
                        modifier = Modifier.testTag("settings_in_app_update"),
                        cardListPosition = CardListPosition.Tail,
                    )
                }
            }

            item { SectionTitle("其他") }

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
                            message = "已清理图片与内容缓存"
                        },
                    )
                    PreferenceDivider()
                    ArrowPreference(
                        title = "关于",
                        modifier = Modifier.testTag("settings_about"),
                        summary = "v$version · 上游库与开源致谢",
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
                    if (guest) {
                        ArrowPreference(
                            title = "退出纯本地模式",
                            modifier = Modifier.testTag("settings_leave_local"),
                            summary = "回到登录页，本地关注名单保留",
                            cardListPosition = CardListPosition.Full,
                            onClick = {
                                App.settings.applyLocalMode(false)
                                nav.reset(Route.Login())
                            },
                        )
                    } else {
                        ArrowPreference(
                            title = "退出登录",
                            modifier = Modifier.testTag("settings_logout"),
                            summary = "清除本地会话",
                            cardListPosition = CardListPosition.Full,
                            onClick = { logout = true },
                        )
                    }
                }
            }

            if (message.isNotBlank()) {
                item {
                    Text(
                        message,
                        fontSize = 12.sp,
                        color = COUITheme.colorScheme.onSurfaceSecondary,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                }
            }

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

        CompositionLocalProvider(
            LocalBackdrop provides if (blurEffects) backdrop else null,
            // 内容铺到状态栏下面，顶栏玻璃才有东西可采样（COUI 17 规范要求 edgeToEdge）。
            LocalEdgeToEdge provides true,
        ) {
            PinnedBarDensity {
                SmallTopAppBar(
                    title = "设置",
                    modifier = Modifier.align(Alignment.TopCenter),
                    color = pageColor,
                    showDivider = false,
                    gradientGlass = blurEffects,
                    navigationIcon = { BackIconButton { nav.pop() } },
                )
            }
        }
    }

    if (logout) {
        ConfirmDialog(
            title = "退出登录",
            message = "会清除本地保存的 Cookie，下次需要重新登录。",
            confirmText = "退出",
            onDismiss = { logout = false },
            onConfirm = {
                logout = false
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
                App.repo.logout()
                nav.reset(Route.Login())
            },
        )
    }

    if (pickLocalFollow) {
        LocalFollowPicker(
            accounts = accounts,
            selectedUid = localFollowUid,
            onDismiss = { pickLocalFollow = false },
            onPick = { uid ->
                App.settings.applyLocalFollowUid(uid)
                localFollowUid = uid
                pickLocalFollow = false
            },
        )
    }

    if (pickFeedLeft) {
        FeedTabPicker(
            title = "微博左侧栏内容",
            selected = feedLeftTab,
            options = leftOptions,
            onDismiss = { pickFeedLeft = false },
            onPick = { selection ->
                App.settings.applyFeedLeftTab(selection)
                feedLeftTab = selection
                pickFeedLeft = false
            },
        )
    }

    if (pickFeedCenter) {
        FeedTabPicker(
            title = "微博中间栏内容",
            selected = feedCenterTab,
            options = centerOptions,
            onDismiss = { pickFeedCenter = false },
            onPick = { selection ->
                App.settings.applyFeedCenterTab(selection)
                feedCenterTab = selection
                pickFeedCenter = false
            },
        )
    }

    if (pickFeedRight) {
        FeedTabPicker(
            title = "微博右侧栏内容",
            selected = feedRightTab,
            options = rightOptions,
            onDismiss = { pickFeedRight = false },
            onPick = { selection ->
                App.settings.applyFeedRightTab(selection)
                feedRightTab = selection
                pickFeedRight = false
            },
        )
    }

    if (pickSearchPanelAnimation) {
        OptionPicker(
            title = "搜索面板动画",
            options = SearchPanelAnimation.entries.map { it.key to it.label },
            selectedKey = searchPanelAnimation.key,
            tagPrefix = "search_panel_anim",
            groupKey = "search-panel-anim",
            onDismiss = { pickSearchPanelAnimation = false },
            onPick = { key ->
                val picked = SearchPanelAnimation.fromKey(key)
                App.settings.applySearchPanelAnimation(picked)
                searchPanelAnimation = picked
                pickSearchPanelAnimation = false
            },
        )
    }

    if (pickHotBoards) {
        HotBoardPicker(
            selected = App.settings.hotBoards,
            onDismiss = { pickHotBoards = false },
            onConfirm = { picked ->
                App.settings.applyHotBoards(picked)
                pickHotBoards = false
            },
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

