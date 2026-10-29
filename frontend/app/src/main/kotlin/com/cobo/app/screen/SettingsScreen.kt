package com.cobo.app.screen

import android.webkit.CookieManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.SingletonImageLoader
import com.cobo.app.App
import com.cobo.app.data.Account
import com.cobo.app.data.FeedTabItem
import com.cobo.app.data.Updater
import com.cobo.app.ui.NavState
import com.cobo.app.ui.pageContainerColor
import com.cobo.app.ui.Route
import com.cobo.app.ui.components.BackIconButton
import com.cobo.app.ui.components.ConfirmDialog
import com.cobo.app.ui.components.navBottomPadding
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.basic.LocalEdgeToEdge
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.layerBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.window.WindowDialog
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
    var message by remember { mutableStateOf("") }
    var about by remember { mutableStateOf(false) }
    var logout by remember { mutableStateOf(false) }
    var localFollowUid by remember { mutableStateOf(App.settings.localFollowUid) }
    val guest = App.repo.guest
    var pickLocalFollow by remember { mutableStateOf(false) }
    var feedLeftTab by remember { mutableStateOf(App.settings.feedLeftTab) }
    var feedRightTab by remember { mutableStateOf(App.settings.feedRightTab) }
    var pickFeedLeft by remember { mutableStateOf(false) }
    var pickFeedRight by remember { mutableStateOf(false) }

    // 只在选中的 uid 变化时读一次账号表，切到别的账号后摘要跟着换。
    val accounts = remember(localFollowUid) { App.store.accounts() }
    val localFollowAccount = accounts.firstOrNull { it.uid == localFollowUid }
    val localFollowSummary = if (localFollowAccount == null) {
        "跟随当前账号"
    } else {
        "用 ${localFollowAccount.name.ifBlank { "UID ${localFollowAccount.uid}" }} 的身份取数"
    }

    val pageColor = pageContainerColor()
    // 顶栏玻璃要有东西可采样：把列表录进 backdrop，顶栏浮在上面做渐变模糊。
    val backdrop = rememberLayerBackdrop()
    val topBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
        TopAppBarDefaults.SmallTopAppBarCenterHeight

    Box(Modifier.fillMaxSize().background(pageColor)) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(pageColor)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // 列表铺到屏幕最底，滚到末页时卡片从系统导航条下方穿过（沉浸），
            // 末项靠 contentPadding 抬到导航条之上，不会被挡住。
            contentPadding = PaddingValues(
                start = 12.dp,
                top = topBarHeight + 10.dp,
                end = 12.dp,
                bottom = 24.dp + navBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
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
                    HorizontalDivider()
                    ArrowPreference(
                        title = "字号",
                        modifier = Modifier.testTag("settings_font"),
                        summary = "${(fontScale * 100).toInt()}%",
                        cardListPosition = CardListPosition.Tail,
                        onClick = {
                            fontScale = App.settings.cycleFontScale()
                        },
                    )
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    ArrowPreference(
                        title = "微博左侧栏",
                        modifier = Modifier.testTag("settings_feed_left_tab"),
                        summary = feedLeftTab.label,
                        cardListPosition = CardListPosition.Head,
                        onClick = { pickFeedLeft = true },
                    )
                    HorizontalDivider()
                    ArrowPreference(
                        title = "微博右侧栏",
                        modifier = Modifier.testTag("settings_feed_right_tab"),
                        summary = feedRightTab.label,
                        cardListPosition = CardListPosition.Tail,
                        onClick = { pickFeedRight = true },
                    )
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    ArrowPreference(
                        title = "账号管理",
                        modifier = Modifier.testTag("settings_accounts"),
                        summary = "多账号切换",
                        cardListPosition = CardListPosition.Head,
                        onClick = { nav.push(Route.Accounts) },
                    )
                    HorizontalDivider()
                    ArrowPreference(
                        title = "草稿箱",
                        modifier = Modifier.testTag("settings_drafts"),
                        summary = "${App.store.drafts().size} 条草稿",
                        onClick = { nav.push(Route.Drafts) },
                    )
                    HorizontalDivider()
                    ArrowPreference(
                        title = "浏览历史",
                        modifier = Modifier.testTag("settings_history"),
                        summary = "${App.store.history().size} 条记录",
                        onClick = { nav.push(Route.History) },
                    )
                    // 收藏和附近挂在账号上，访客身份拿不到，纯本地模式不给入口。
                    if (!guest) {
                        HorizontalDivider()
                        ArrowPreference(
                            title = "收藏",
                            modifier = Modifier.testTag("settings_favorites"),
                            summary = "查看收藏的微博",
                            onClick = { nav.push(Route.Container("230267", "收藏")) },
                        )
                    }
                    HorizontalDivider()
                    ArrowPreference(
                        title = "话题 / 超话",
                        modifier = Modifier.testTag("settings_topic"),
                        summary = "按话题浏览",
                        cardListPosition = if (guest) CardListPosition.Tail else CardListPosition.None,
                        onClick = { nav.push(Route.Topic()) },
                    )
                    if (!guest) {
                        HorizontalDivider()
                        ArrowPreference(
                            title = "附近",
                            modifier = Modifier.testTag("settings_nearby"),
                            summary = "基于当前位置",
                            cardListPosition = CardListPosition.Tail,
                            onClick = { nav.push(Route.Nearby) },
                        )
                    }
                }
            }

            // 纯本地模式一律用访客身份取数，没有账号可选。
            if (!guest) item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    ArrowPreference(
                        title = "本地关注使用账号",
                        modifier = Modifier.testTag("settings_local_follow"),
                        summary = localFollowSummary,
                        cardListPosition = CardListPosition.Full,
                        onClick = { pickLocalFollow = true },
                    )
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    ArrowPreference(
                        title = "检查更新",
                        modifier = Modifier.testTag("settings_update"),
                        summary = if (App.checkingUpdate) "正在检查…" else "当前版本 $version",
                        cardListPosition = CardListPosition.Head,
                        onClick = { scope.launch { App.checkUpdate(manual = true) } },
                    )
                    HorizontalDivider()
                    SwitchPreference(
                        checked = autoCheckUpdate,
                        onCheckedChange = {
                            autoCheckUpdate = it
                            App.settings.applyAutoCheckUpdate(it)
                        },
                        title = "自动检查更新",
                        modifier = Modifier.testTag("settings_auto_update"),
                        summary = "启动时检查，有新版本弹窗提示",
                        cardListPosition = CardListPosition.Middle,
                    )
                    HorizontalDivider()
                    SwitchPreference(
                        checked = acceptPrerelease,
                        onCheckedChange = {
                            acceptPrerelease = it
                            App.settings.applyAcceptPrerelease(it)
                        },
                        title = "接收预发布版本",
                        modifier = Modifier.testTag("settings_prerelease"),
                        summary = "关闭后只提示正式版本",
                        cardListPosition = CardListPosition.Tail,
                    )
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    ArrowPreference(
                        title = "清理缓存",
                        modifier = Modifier.testTag("settings_clear_cache"),
                        summary = "图片与已保存的内容",
                        cardListPosition = CardListPosition.Head,
                        onClick = {
                            runCatching {
                                val loader = SingletonImageLoader.get(context)
                                loader.memoryCache?.clear()
                                loader.diskCache?.clear()
                            }
                            App.cache.clear()
                            message = "已清理图片与内容缓存"
                        },
                    )
                    HorizontalDivider()
                    ArrowPreference(
                        title = "关于",
                        modifier = Modifier.testTag("settings_about"),
                        summary = "Cobo · COUI 前端",
                        onClick = { about = true },
                    )
                    HorizontalDivider()
                    if (guest) {
                        ArrowPreference(
                            title = "退出纯本地模式",
                            modifier = Modifier.testTag("settings_leave_local"),
                            summary = "回到登录页，本地关注名单保留",
                            cardListPosition = CardListPosition.Tail,
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
                            cardListPosition = CardListPosition.Tail,
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
            LocalBackdrop provides backdrop,
            // 内容铺到状态栏下面，顶栏玻璃才有东西可采样（COUI 17 规范要求 edgeToEdge）。
            LocalEdgeToEdge provides true,
        ) {
            SmallTopAppBar(
                title = "设置",
                modifier = Modifier.align(Alignment.TopCenter),
                color = pageColor,
                showDivider = false,
                gradientGlass = true,
                navigationIcon = { BackIconButton { nav.pop() } },
            )
        }
    }

    if (about) {
        ConfirmDialog(
            title = "关于 Cobo",
            message = "Cobo · COUI 前端 v$version\n\n使用 COUI（ColorOS 17 设计语言）重建的第三方微博客户端。\n仅用于个人学习与研究。",
            confirmText = "知道了",
            onDismiss = { about = false },
            onConfirm = { about = false },
        )
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
            onDismiss = { pickFeedLeft = false },
            onPick = { item ->
                App.settings.applyFeedLeftTab(item)
                feedLeftTab = item
                pickFeedLeft = false
            },
        )
    }

    if (pickFeedRight) {
        FeedTabPicker(
            title = "微博右侧栏内容",
            selected = feedRightTab,
            onDismiss = { pickFeedRight = false },
            onPick = { item ->
                App.settings.applyFeedRightTab(item)
                feedRightTab = item
                pickFeedRight = false
            },
        )
    }
}

/**
 * 「本地关注」取数账号选择。COUI 没有现成的列表选择弹窗，这里用 [WindowDialog] 自己铺一列：
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
    WindowDialog(
        show = show,
        title = "本地关注使用账号",
        summary = "本地关注的名单存在这台设备上，不跟账号走；这里只决定用谁的身份去拉那批人的内容。",
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
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                LocalFollowOption(
                    title = "跟随当前账号",
                    subtitle = "切账号后跟着变",
                    selected = selectedUid.isBlank(),
                    tag = "local_follow_follow_current",
                    onClick = {
                        result = ""
                        show = false
                    },
                )
                accounts.forEach { account ->
                    LocalFollowOption(
                        title = account.name.ifBlank { "UID ${account.uid}" },
                        subtitle = "UID ${account.uid}",
                        selected = account.uid == selectedUid,
                        tag = "local_follow_option_${account.uid}",
                        onClick = {
                            result = account.uid
                            show = false
                        },
                    )
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
private fun LocalFollowOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 15.sp,
                color = if (selected) COUITheme.colorScheme.primary else COUITheme.colorScheme.onBackground,
            )
            Text(subtitle, fontSize = 12.sp, color = COUITheme.colorScheme.onSurfaceSecondary)
        }
        if (selected) {
            Text("已选", fontSize = 13.sp, color = COUITheme.colorScheme.primary)
        }
    }
}

private data class FeedTabOptionItem(
    val item: FeedTabItem,
    val subtitle: String,
)

@Composable
private fun FeedTabPicker(
    title: String,
    selected: FeedTabItem,
    onDismiss: () -> Unit,
    onPick: (FeedTabItem) -> Unit,
) {
    var show by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<FeedTabItem?>(null) }
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { show = true }

    WindowDialog(
        show = show,
        title = title,
        summary = "中间栏固定为全部关注。左右两侧可设置不同或相同内容，也可设为隐藏；左右均隐藏时直接单页浏览。",
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
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                val options = listOf(
                    FeedTabOptionItem(FeedTabItem.None, "不显示此栏"),
                    FeedTabOptionItem(FeedTabItem.Circle, "互相关注的好友微博"),
                    FeedTabOptionItem(FeedTabItem.Recommend, "热门推荐微博"),
                    FeedTabOptionItem(FeedTabItem.Local, "按本机关注名单拉取"),
                    FeedTabOptionItem(FeedTabItem.Original, "过滤转发，仅看原创"),
                    FeedTabOptionItem(FeedTabItem.Special, "特别关注分组"),
                    FeedTabOptionItem(FeedTabItem.Secret, "悄悄关注的人"),
                    FeedTabOptionItem(FeedTabItem.Favorites, "已收藏的微博"),
                )
                options.forEach { opt ->
                    LocalFollowOption(
                        title = opt.item.label,
                        subtitle = opt.subtitle,
                        selected = opt.item == selected,
                        tag = "feed_tab_option_${opt.item.key}",
                        onClick = {
                            result = opt.item
                            show = false
                        },
                    )
                }
            }
            DialogButtonBar(
                negative = DialogButtonBarAction(text = "取消", onClick = { show = false }),
                positive = null,
            )
        }
    }
}
