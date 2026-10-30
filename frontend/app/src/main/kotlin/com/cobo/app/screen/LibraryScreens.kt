package com.cobo.app.screen

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cobo.app.App
import com.cobo.app.data.Account
import com.cobo.app.data.Draft
import com.cobo.app.data.HistoryItem
import com.cobo.app.ui.NavState
import com.cobo.app.ui.PinnedBarDensity
import com.cobo.app.ui.pinnedHeight
import com.cobo.app.ui.Route
import com.cobo.app.ui.components.Avatar
import com.cobo.app.ui.components.BackIconButton
import com.cobo.app.ui.components.ConfirmDialog
import com.cobo.app.ui.components.EmptyState
import com.cobo.app.ui.components.TopBarTextButton
import com.cobo.app.ui.components.WeiboRichText
import com.cobo.app.ui.components.fmtCount
import com.cobo.app.ui.components.navBottomPadding
import com.cobo.app.ui.pageContainerColor
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton
import io.github.suqi8.coui.kmp.basic.InputField
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.LocalEdgeToEdge
import io.github.suqi8.coui.kmp.basic.NotificationSwipeBox
import io.github.suqi8.coui.kmp.basic.PinnedScrollBehavior
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.basic.couiTopAppBarActionItem
import io.github.suqi8.coui.kmp.basic.rememberNotificationSwipeState
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.layerBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.core.ExperimentalCOUIApi
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Add
import io.github.suqi8.coui.kmp.icon.extended.Delete
import io.github.suqi8.coui.kmp.icon.extended.Filter
import io.github.suqi8.coui.kmp.icon.extended.Refresh
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun formatStamp(millis: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(millis))

@Composable
fun DraftsScreen(nav: NavState) {
    var drafts by remember { mutableStateOf(App.store.drafts()) }
    val current = nav.current

    LaunchedEffect(current) {
        if (current is Route.Drafts) drafts = App.store.drafts()
    }

    Scaffold(
        containerColor = pageContainerColor(),
        topBar = {
            PinnedBarDensity {
                SmallTopAppBar(
                    color = pageContainerColor(),
                    title = "草稿箱",
                    navigationIcon = { BackIconButton { nav.pop() } },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 12.dp + navBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (drafts.isEmpty()) {
                item { EmptyState("没有草稿") }
            }
            itemsIndexed(drafts, key = { _, draft -> draft.time }) { index, draft ->
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("draft_row_$index"),
                    onClick = {
                        App.store.removeDraft(draft.time)
                        drafts = App.store.drafts()
                        nav.push(Route.Publish(draft.text))
                    },
                ) {
                    Text(draft.text, fontSize = 14.sp, maxLines = 4)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            formatStamp(draft.time),
                            fontSize = 11.sp,
                            color = COUITheme.colorScheme.onSurfaceSecondary,
                            modifier = Modifier.weight(1f),
                        )
                        HighlightedIconButton(
                            onClick = {
                                App.store.removeDraft(draft.time)
                                drafts = App.store.drafts()
                            },
                        ) {
                            Icon(COUIIcons.Delete, "删除", Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}

/** 补位的位移：定长缓出，到位置就停，不回弹。 */
private val HistoryPlacementSpec: FiniteAnimationSpec<IntOffset> =
    tween(durationMillis = 220, easing = FastOutSlowInEasing)

@OptIn(ExperimentalCOUIApi::class)
@Composable
fun HistoryScreen(nav: NavState) {
    var history by remember { mutableStateOf(App.store.history()) }
    var confirm by remember { mutableStateOf(false) }
    // 同一时间只展开一行，和通知栏一样：拉开新的一行，上一行自己收回去。
    var openId by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    // 筛选：点漏斗展开搜索框，输入即过滤；再点一次收起，同时清掉关键词和过滤结果。
    var filterOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var filterRowPx by remember { mutableIntStateOf(0) }
    val filterRowHeight = with(LocalDensity.current) { filterRowPx.toDp() }
    val filterInset by animateDpAsState(
        targetValue = if (filterOpen) filterRowHeight else 0.dp,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "historyFilterInset",
    )
    val shown = remember(history, query) {
        val key = query.trim()
        if (key.isEmpty()) history else history.filter { it.matches(key) }
    }

    // 从详情页回来时不重排：看过的记录只把浏览时间写回文件，顺序要等离开本页再进来才生效。
    // 否则刚看完的那一条会当着人的面被抬到最前面，整个列表跟着错位。
    LaunchedEffect(nav.current) {
        if (nav.current is Route.History) openId = null
    }

    val pageColor = pageContainerColor()
    // 顶栏玻璃要有东西可采样：把列表录进 backdrop，顶栏浮在上面做渐变模糊。
    val backdrop = rememberLayerBackdrop()
    val topBarHeight = pinnedHeight(TopAppBarDefaults.SmallTopAppBarCenterHeight)
    // 顶栏玻璃按滚动量渐显：停在这页顶上看不到玻璃带，往下滑 30dp 后才是完整的 24dp 距离。
    // 顶部那条“左滑记录可删除”提示正好落在带内，常显会被糊掉。
    val scrollBehavior = PinnedScrollBehavior()

    Box(Modifier.fillMaxSize().background(pageColor)) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(pageColor)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
                state = listState,
                contentPadding = PaddingValues(
                    start = 12.dp,
                    top = topBarHeight + filterInset + 8.dp,
                    end = 12.dp,
                    bottom = 12.dp + navBottomPadding(),
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when {
                    history.isEmpty() -> item { EmptyState("还没有浏览记录") }
                    shown.isEmpty() -> item { EmptyState("没有匹配的记录") }
                    else -> item {
                        Text(
                            "左滑记录可删除",
                            fontSize = 12.sp,
                            color = COUITheme.colorScheme.onSurfaceSecondary,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }
                itemsIndexed(shown, key = { _, item -> item.id }) { index, item: HistoryItem ->
                    val swipe = rememberNotificationSwipeState()
                    val scope = rememberCoroutineScope()
                    LaunchedEffect(swipe.isRevealed) {
                        if (swipe.isRevealed) openId = item.id
                    }
                    LaunchedEffect(openId) {
                        if (openId != item.id && swipe.isRevealed) swipe.close()
                    }
                    NotificationSwipeBox(
                        onSettings = null,
                        onDismiss = {
                            App.store.removeHistory(item.id)
                            history = history.filterNot { it.id == item.id }
                        },
                        settingsIcon = null,
                        dismissIcon = null,
                        // 删掉的一行已经滑走淡出，不再要淡出动画；下面的条目按 animateItem 补位。
                        modifier = Modifier
                            .animateItem(fadeInSpec = null, placementSpec = HistoryPlacementSpec, fadeOutSpec = null)
                            .testTag("history_row_$index"),
                        state = swipe,
                        minHeight = 0.dp,
                        containerColor = CardDefaults.defaultColors().color,
                        dismissOnFullSwipe = true,
                        dismissDescription = "删除这条记录",
                    ) {
                        HistoryRowBody(
                            item = item,
                            modifier = Modifier.clickable {
                                if (swipe.isRevealed) {
                                    scope.launch { swipe.close() }
                                } else {
                                    nav.push(Route.Detail(item.id, item.mid))
                                }
                            },
                        )
                    }
                }
            }
        }

        CompositionLocalProvider(
            LocalBackdrop provides backdrop,
            // 内容铺到状态栏下面，顶栏玻璃才有东西可采样（COUI 17 规范要求 edgeToEdge）。
            LocalEdgeToEdge provides true,
        ) {
            PinnedBarDensity {
            Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
                SmallTopAppBar(
                    title = "浏览历史",
                    color = pageColor,
                    showDivider = false,
                    gradientGlass = true,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = { BackIconButton { nav.pop() } },
                    actions = {
                        if (history.isNotEmpty()) {
                            HighlightedIconButton(
                                onClick = {
                                    filterOpen = !filterOpen
                                    if (!filterOpen) query = ""
                                },
                                modifier = Modifier.couiTopAppBarActionItem().testTag("history_filter"),
                            ) {
                                Icon(COUIIcons.Filter, "筛选", Modifier.size(24.dp))
                            }
                            TopBarTextButton("清空", onClick = { confirm = true })
                        }
                    },
                )
                // 搜索框从返回键的左沿铺到「清空」的右沿，也就是顶栏两端的 16dp 内边距之间。
                // 外层高度跟着动画走，内层用 unbounded 量到自然高度，列表顶部留白也读同一个值，
                // 这样下拉和列表让位是同一条曲线。
                // 这里不能铺底色：顶栏的渐变玻璃画在自己下方一条 extra band 上，铺一层实心底正好把它
                // 从顶栏下沿切断。留空之后搜索框就浮在那条渐变上，和 COUI 的搜索框一样是半透明的。
                if (filterOpen || filterInset > 0.dp) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(filterInset)
                            .clipToBounds(),
                    ) {
                        Column(Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true)) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .onSizeChanged { filterRowPx = it.height }
                                    .padding(
                                        start = TopAppBarDefaults.NavigationIconPadding,
                                        end = TopAppBarDefaults.ActionIconPadding,
                                        top = 6.dp,
                                        bottom = 6.dp,
                                    ),
                            ) {
                                InputField(
                                    query = query,
                                    onQueryChange = { query = it },
                                    onSearch = {},
                                    expanded = filterOpen,
                                    onExpandedChange = {},
                                    modifier = Modifier.fillMaxWidth().testTag("history_filter_input"),
                                    label = "搜索浏览历史",
                                )
                            }
                        }
                    }
                }
            }
            }
        }
    }

    if (confirm) {
        ConfirmDialog(
            title = "清空浏览历史",
            message = "会删除本地保存的全部浏览记录。",
            confirmText = "清空",
            onDismiss = { confirm = false },
            onConfirm = {
                App.store.clearHistory()
                history = emptyList()
                // 清空之后没有东西可筛，搜索框一并收回去。
                filterOpen = false
                query = ""
                confirm = false
            },
        )
    }
}

/** 筛选只匹配看得见的字：作者名和正文纯文本。 */
private fun HistoryItem.matches(key: String): Boolean =
    user.contains(key, ignoreCase = true) || text.contains(key, ignoreCase = true)

/** 一条历史记录的正文。 */
@Composable
private fun HistoryRowBody(item: HistoryItem, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
        if (item.html.isNotBlank()) {
            WeiboRichText(
                html = item.html,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                maxLines = 2,
                selectable = false,
            )
        } else {
            // 旧记录没存 HTML，只能按当初裁剪下来的纯文本兜底。
            Text(
                item.text.ifBlank { "（无正文）" },
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "${item.user}  ${formatStamp(item.time)}",
            fontSize = 11.sp,
            color = COUITheme.colorScheme.onSurfaceSecondary,
        )
    }
}

@OptIn(ExperimentalCOUIApi::class)
@Composable
fun AccountsScreen(nav: NavState) {
    var accounts by remember { mutableStateOf(App.store.accounts()) }
    var currentCookie by remember { mutableStateOf(App.repo.cookie()) }
    var pendingDelete by remember { mutableStateOf<Account?>(null) }
    // 同一时间只展开一行，和通知栏一样：拉开新的一行，上一行自己收回去。
    var openUid by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = pageContainerColor(),
        topBar = {
            PinnedBarDensity {
                SmallTopAppBar(
                    color = pageContainerColor(),
                    title = "账号管理",
                    navigationIcon = { BackIconButton { nav.pop() } },
                    actions = {
                        HighlightedIconButton(onClick = {
                            nav.push(Route.Login(addAccount = true, fresh = true))
                        }) {
                            Icon(COUIIcons.Add, "添加账号", Modifier.size(24.dp))
                        }
                    },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 12.dp + navBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (accounts.isEmpty()) {
                item { EmptyState("还没有保存的账号") }
            } else {
                item {
                    Text(
                        "左滑账号可重新登录或删除",
                        fontSize = 12.sp,
                        color = COUITheme.colorScheme.onSurfaceSecondary,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
            itemsIndexed(accounts, key = { _, account -> account.uid }) { index, account ->
                val current = account.cookie == currentCookie
                val name = account.name.ifBlank { "微博账号" }
                val swipe = rememberNotificationSwipeState()
                val scope = rememberCoroutineScope()
                LaunchedEffect(swipe.isRevealed) {
                    if (swipe.isRevealed) openUid = account.uid
                }
                LaunchedEffect(openUid) {
                    if (openUid != account.uid && swipe.isRevealed) swipe.close()
                }
                NotificationSwipeBox(
                    onSettings = {
                        openUid = null
                        nav.push(Route.Login(addAccount = true, fresh = true, relogin = name))
                    },
                    onDismiss = { pendingDelete = account },
                    settingsIcon = COUIIcons.Refresh,
                    dismissIcon = null,
                    modifier = Modifier
                        .animateItem(fadeInSpec = null, placementSpec = HistoryPlacementSpec, fadeOutSpec = null)
                        .testTag("account_row_$index"),
                    state = swipe,
                    minHeight = 0.dp,
                    containerColor = CardDefaults.defaultColors().color,
                    dismissOnFullSwipe = true,
                    settingsDescription = "重新登录",
                    dismissDescription = "删除账号",
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (swipe.isRevealed) {
                                    scope.launch { swipe.close() }
                                } else {
                                    App.repo.updateSession(account.cookie, account.uid)
                                    currentCookie = account.cookie
                                    nav.reset(Route.Main)
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(account.avatar, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                if (current) {
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "当前",
                                        fontSize = 10.sp,
                                        color = COUITheme.colorScheme.onPrimary,
                                        modifier = Modifier
                                            .background(COUITheme.colorScheme.primary, CircleShape)
                                            .padding(horizontal = 7.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            Text(
                                "UID ${account.uid}",
                                fontSize = 11.sp,
                                color = COUITheme.colorScheme.onSurfaceSecondary,
                            )
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { account ->
        ConfirmDialog(
            title = "移除账号",
            message = "只删除本地保存的登录信息，不会影响微博账号本身。",
            confirmText = "移除",
            onDismiss = {
                pendingDelete = null
                openUid = null
            },
            onConfirm = {
                App.store.removeAccount(account.uid)
                accounts = App.store.accounts()
                pendingDelete = null
            },
        )
    }
}
