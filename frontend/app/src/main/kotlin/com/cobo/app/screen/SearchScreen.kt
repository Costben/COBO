package com.cobo.app.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cobo.app.App
import com.cobo.app.data.HotItem
import com.cobo.app.data.Membership
import com.cobo.app.data.Status
import com.cobo.app.data.TopicItem
import com.cobo.app.data.User
import com.cobo.app.ui.NavState
import com.cobo.app.ui.CoboPredictiveBackHandler
import com.cobo.app.ui.PinnedBarDensity
import com.cobo.app.ui.pageContainerColor
import com.cobo.app.ui.rememberExpandOrigin
import com.cobo.app.ui.Route
import com.cobo.app.ui.expandOrigin
import com.cobo.app.ui.components.Avatar
import com.cobo.app.ui.components.EmptyState
import com.cobo.app.ui.components.ErrorState
import com.cobo.app.ui.components.LoadingState
import com.cobo.app.ui.components.MemberBadge
import com.cobo.app.ui.components.StatusCard
import com.cobo.app.ui.components.VerifiedBadge
import com.cobo.app.ui.components.fmtCount
import com.cobo.app.ui.components.navBottomPadding
import io.github.suqi8.coui.kmp.basic.AppBarBlurOverlay
import io.github.suqi8.coui.kmp.basic.DropdownImpl
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.InputField
import io.github.suqi8.coui.kmp.basic.ListPopupColumn
import io.github.suqi8.coui.kmp.basic.ListPopupDefaults
import io.github.suqi8.coui.kmp.basic.LocalEdgeToEdge
import io.github.suqi8.coui.kmp.basic.SearchBar
import io.github.suqi8.coui.kmp.basic.SearchBarActionButtonDefaults
import io.github.suqi8.coui.kmp.basic.SearchBarDefaults
import io.github.suqi8.coui.kmp.basic.SearchHistoryView
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.More
import io.github.suqi8.coui.kmp.overlay.OverlayListPopup
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.layerBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.LocalDismissState
import kotlinx.coroutines.launch

/**
 * 搜索结果的分类。containerid 里的 type 由 Repo 负责：
 * 微博 `type=1`、用户 `type=3`、话题 `type=38`。
 */
private enum class SearchTab(val label: String) {
    Weibo("微博"),
    Users("用户"),
    Topics("话题"),
}

/**
 * 一个分类自己的翻页状态。三个分类各存一份，来回切不用重新请求。
 * 用接口是为了在 [SearchScreen] 里统一做「还有没有下一页」的判断。
 */
private interface SearchTabState {
    val count: Int
    var page: Int
    var end: Boolean
    var started: Boolean
    fun reset()
}

@Stable
private class SearchTabItems<T> : SearchTabState {
    val items = mutableStateListOf<T>()
    override val count: Int get() = items.size
    override var page by mutableIntStateOf(1)
    override var end by mutableStateOf(false)
    override var started by mutableStateOf(false)

    override fun reset() {
        items.clear()
        page = 1
        end = false
        started = false
    }
}

@Composable
fun SearchScreen(nav: NavState) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(SearchTab.Weibo) }
    val hot = remember { mutableStateListOf<HotItem>() }
    val weibo = remember { SearchTabItems<Status>() }
    val users = remember { SearchTabItems<User>() }
    val topics = remember { SearchTabItems<TopicItem>() }
    var loading by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // The system search page opens in the edit state; SearchBar hides its content while collapsed.
    var fieldFocused by remember { mutableStateOf(true) }
    val expanded = fieldFocused || submitted.isNotBlank()
    val history = App.settings.searchHistory
    val listState = rememberLazyListState()

    fun stateFor(t: SearchTab): SearchTabState = when (t) {
        SearchTab.Weibo -> weibo
        SearchTab.Users -> users
        SearchTab.Topics -> topics
    }

    suspend fun loadHot(force: Boolean = false) {
        loading = true
        error = null
        try {
            hot.clear()
            hot.addAll(App.repo.hot(force))
        } catch (t: Throwable) {
            error = t.message ?: "热搜加载失败"
        } finally {
            loading = false
        }
    }

    suspend fun load(which: SearchTab, reset: Boolean, force: Boolean = true) {
        val keyword = submitted
        if (keyword.isBlank()) return
        // 翻页不叠加：上拉触发得比上一页回来快时，直接丢掉这一次。
        if (!reset && (loading || loadingMore)) return
        if (reset) {
            loading = true
            error = null
        } else {
            loadingMore = true
        }
        try {
            when (which) {
                SearchTab.Weibo -> {
                    val nextPage = if (reset) 1 else weibo.page
                    val pageItems = App.repo.search(keyword, nextPage, force = if (reset) force else false).items
                    if (reset) weibo.items.clear()
                    val known = weibo.items.mapTo(HashSet()) { it.id }
                    pageItems.forEach { if (known.add(it.id)) weibo.items.add(it) }
                    weibo.page = nextPage + 1
                    weibo.started = true
                    if (pageItems.isEmpty()) weibo.end = true
                }
                SearchTab.Users -> {
                    val nextPage = if (reset) 1 else users.page
                    val pageItems = App.repo.searchUsers(keyword, nextPage, force = if (reset) force else false)
                    if (reset) users.items.clear()
                    val known = users.items.mapTo(HashSet()) { it.id }
                    pageItems.forEach { if (known.add(it.id)) users.items.add(it) }
                    users.page = nextPage + 1
                    users.started = true
                    if (pageItems.isEmpty()) users.end = true
                }
                SearchTab.Topics -> {
                    val nextPage = if (reset) 1 else topics.page
                    val pageItems = App.repo.searchTopics(keyword, nextPage, force = if (reset) force else false)
                    if (reset) topics.items.clear()
                    val known = topics.items.mapTo(HashSet()) { it.title }
                    pageItems.forEach { if (known.add(it.title)) topics.items.add(it) }
                    topics.page = nextPage + 1
                    topics.started = true
                    if (pageItems.isEmpty()) topics.end = true
                }
            }
        } catch (t: Throwable) {
            error = t.message ?: "搜索失败"
        } finally {
            loading = false
            loadingMore = false
        }
    }

    suspend fun runSearch(keyword: String) {
        val q = keyword.trim()
        if (q.isEmpty()) return
        App.settings.pushSearchHistory(q)
        // 搜哪一类由顶栏菜单决定，搜索前后都跟着它走：先选了「用户」再搜，出来的就是用户。
        weibo.reset()
        users.reset()
        topics.reset()
        submitted = q
        load(tab, reset = true, force = true)
    }

    fun selectTab(next: SearchTab) {
        if (next == tab) return
        tab = next
        error = null
        loadingMore = false
        if (submitted.isNotBlank() && !stateFor(next).started) {
            scope.launch { load(next, reset = true) }
        }
    }

    // 话题结果点开就是该话题下的微博：换个关键词重新走一遍「微博」分类。
    fun openTopic(topic: TopicItem) {
        query = topic.title
        tab = SearchTab.Weibo
        scope.launch { runSearch(topic.title) }
    }

    LaunchedEffect(Unit) { loadHot(force = false) }
    LaunchedEffect(listState, submitted, tab, stateFor(tab).count) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { last ->
                if (submitted.isBlank()) return@collect
                val state = stateFor(tab)
                if (!state.started || state.end || state.count == 0) return@collect
                if (last >= state.count - 2) load(tab, reset = false, force = false)
            }
    }

    val backdrop = rememberLayerBackdrop()
    // 顶栏那一行实测的高度。结果列表按它让出顶部留白，内容才能从玻璃底下滚过去。
    var barHeight by remember { mutableIntStateOf(0) }
    val pageDensity = LocalDensity.current

    Box(Modifier.fillMaxSize().background(pageContainerColor())) {
        val scheme = COUITheme.colorScheme
        // 内容铺到顶栏底下，顶栏的渐变玻璃才有东西可采样。
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            Column(Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        top = with(pageDensity) { barHeight.toDp() } + 8.dp,
                        end = 12.dp,
                        bottom = 8.dp + navBottomPadding(),
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (submitted.isBlank() && history.isNotEmpty()) {
                        item {
                            SearchHistoryView(
                                items = history.toList(),
                                onItemClick = { keyword ->
                                    query = keyword
                                    scope.launch { runSearch(keyword) }
                                },
                                onDeleteClick = { App.settings.clearSearchHistory() },
                                title = "搜索历史",
                                deleteIconDescription = "清空搜索历史",
                            )
                        }
                    }
                    if (submitted.isBlank()) {
                        item {
                            Text(
                                "微博热搜",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                            )
                        }
                        if (hot.isEmpty()) {
                            item {
                                when {
                                    loading -> LoadingState()
                                    error != null -> ErrorState(error!!) { scope.launch { loadHot(force = true) } }
                                    else -> EmptyState("暂无热搜")
                                }
                            }
                        }
                        items(hot, key = { it.rank.toString() + it.title }) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        query = item.title
                                        scope.launch { runSearch(item.title) }
                                    }
                                    .padding(horizontal = 4.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    item.rank.toString(),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (item.rank <= 3) scheme.primary else scheme.onSurfaceSecondary,
                                    modifier = Modifier.width(28.dp),
                                )
                                Text(item.title, fontSize = 14.sp, maxLines = 1, modifier = Modifier.weight(1f))
                                if (item.label.isNotBlank()) {
                                    Text(item.label, fontSize = 10.sp, color = scheme.onSurfaceSecondary)
                                }
                            }
                        }
                    } else {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "“$submitted” 的结果",
                                    fontSize = 13.sp,
                                    color = scheme.onSurfaceSecondary,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(text = "返回热搜", onClick = { submitted = "" })
                            }
                        }
                        when (tab) {
                            SearchTab.Weibo -> {
                                if (weibo.items.isEmpty()) {
                                    item {
                                        ResultState(
                                            loading = loading || !weibo.started,
                                            error = error,
                                            emptyText = "没有找到相关微博",
                                        ) { scope.launch { load(tab, reset = true, force = true) } }
                                    }
                                } else {
                                    itemsIndexed(weibo.items, key = { _, s -> s.id }) { index, status ->
                                        val origin = rememberExpandOrigin()
                                        StatusCard(
                                            status = status,
                                            modifier = Modifier.expandOrigin(origin).testTag("search_card_$index"),
                                            onOpen = { nav.push(Route.Detail(status.id, status.mid), origin) },
                                            onOpenQuoted = { quoted, quotedOrigin ->
                                                nav.push(Route.Detail(quoted.id, quoted.mid), quotedOrigin)
                                            },
                                            onUser = { nav.push(Route.Profile(it.id)) },
                                            onImage = { urls, i -> nav.push(Route.Images(urls, i)) },
                                            onVideo = { nav.push(Route.Video(it.url, it.title)) },
                                            onLike = {
                                                scope.launch {
                                                    runCatching { App.repo.like(status.id, status.liked) }.onSuccess {
                                                        weibo.items[index] = status.copy(
                                                            liked = !status.liked,
                                                            attitudes = (status.attitudes + if (status.liked) -1 else 1)
                                                                .coerceAtLeast(0),
                                                        )
                                                    }
                                                }
                                            },
                                            onRepost = { nav.push(Route.Detail(status.id, status.mid), origin) },
                                            onComment = { nav.push(Route.Detail(status.id, status.mid), origin) },
                                        )
                                    }
                                }
                            }
                            SearchTab.Users -> {
                                if (users.items.isEmpty()) {
                                    item {
                                        ResultState(
                                            loading = loading || !users.started,
                                            error = error,
                                            emptyText = "没有找到相关用户",
                                        ) { scope.launch { load(tab, reset = true, force = true) } }
                                    }
                                } else {
                                    itemsIndexed(users.items, key = { _, u -> u.id }) { index, user ->
                                        UserRow(
                                            user = user,
                                            modifier = Modifier.testTag("search_user_$index"),
                                            onClick = { nav.push(Route.Profile(user.id)) },
                                        )
                                    }
                                }
                            }
                            SearchTab.Topics -> {
                                if (topics.items.isEmpty()) {
                                    item {
                                        ResultState(
                                            loading = loading || !topics.started,
                                            error = error,
                                            emptyText = "没有找到相关话题",
                                        ) { scope.launch { load(tab, reset = true, force = true) } }
                                    }
                                } else {
                                    itemsIndexed(topics.items, key = { _, t -> t.title }) { index, topic ->
                                        TopicRow(
                                            topic = topic,
                                            modifier = Modifier.testTag("search_topic_$index"),
                                            onClick = { openTopic(topic) },
                                        )
                                    }
                                }
                            }
                        }
                        item {
                            when {
                                loadingMore -> LoadingState()
                                stateFor(tab).end && stateFor(tab).count > 0 -> Text(
                                    "没有更多了",
                                    fontSize = 12.sp,
                                    color = scheme.onSurfaceSecondary,
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        // 顶栏材质：ColorOS 那一套从上到下渐隐的高斯模糊，内容从玻璃底下滚过去。
        CompositionLocalProvider(
            LocalBackdrop provides backdrop,
            LocalEdgeToEdge provides true,
        ) {
            PinnedBarDensity {
                AppBarBlurOverlay(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .onSizeChanged { barHeight = it.height },
                    containerColor = pageContainerColor(),
                    scrollFraction = { 1f },
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.statusBars),
                    ) {
                        SearchBar(
                            expanded = expanded,
                            onExpandedChange = { isExpanded ->
                                // Fallback if SearchBar's own back handler wins: leave the page
                                // instead of showing an empty collapsed bar.
                                if (isExpanded) fieldFocused = true else nav.pop()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            inputField = {
                                InputField(
                                    query = query,
                                    onQueryChange = { query = it },
                                    onSearch = { keyword ->
                                        scope.launch { runSearch(keyword) }
                                    },
                                    expanded = expanded,
                                    onExpandedChange = { fieldFocused = it },
                                    modifier = Modifier
                                        .testTag("search_input")
                                        // The IME search action fires onSearch; a hardware or
                                        // emulated Enter has to be caught here or it just moves
                                        // the cursor.
                                        .onPreviewKeyEvent { event ->
                                            val enter = event.key == Key.Enter ||
                                                event.key == Key.NumPadEnter
                                            if (event.type == KeyEventType.KeyDown && enter) {
                                                scope.launch { runSearch(query) }
                                                true
                                            } else {
                                                false
                                            }
                                        },
                                    label = "搜索微博 / 用户 / 话题",
                                )
                            },
                            // 原来的「取消」换成了三点：搜索哪一类由这个菜单决定。
                            outsideEndAction = {
                                SearchScopeAction(
                                    tab = tab,
                                    onPick = { next -> selectTab(next) },
                                )
                            },
                        ) {}
                    }
                }
            }
        }
    }

    // SearchBar installs its own back handler while expanded. Registering the navigator's
    // handler after it keeps the predictive gesture: the page follows the finger instead of
    // the bar collapsing without an animation.
    key(expanded) { CoboPredictiveBackHandler(nav) }
}

/**
 * 搜索框右边的三点按钮：点开选这一搜是搜微博、用户还是话题。
 * 原来是「取消」，现在入口挪到这里，搜索框底下那排分类胶囊也就撤掉了。
 */
@Composable
private fun SearchScopeAction(tab: SearchTab, onPick: (SearchTab) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val fieldColor = SearchBarDefaults.searchBarColors().backgroundColor
    Box {
        Box(
            modifier = Modifier
                .size(SearchBarActionButtonDefaults.Height)
                .clip(CircleShape)
                .background(fieldColor)
                .clickable { expanded = true }
                .testTag("search_scope"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(COUIIcons.More, "搜索范围", Modifier.size(22.dp))
        }
        OverlayListPopup(
            show = expanded,
            popupPositionProvider = ListPopupDefaults.DropdownPositionProvider,
            onDismissRequest = { expanded = false },
        ) {
            val dismiss = LocalDismissState.current
            ListPopupColumn {
                SearchTab.entries.forEachIndexed { index, item ->
                    DropdownImpl(
                        item = DropdownItem(text = item.label),
                        optionSize = SearchTab.entries.size,
                        isSelected = item == tab,
                        index = index,
                        groupKey = "search_scope",
                        onSelectedIndexChange = {
                            onPick(item)
                            dismiss?.invoke()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultState(
    loading: Boolean,
    error: String?,
    emptyText: String,
    onRetry: () -> Unit,
) {
    when {
        loading -> LoadingState()
        error != null -> ErrorState(error, onRetry = onRetry)
        else -> EmptyState(emptyText)
    }
}

@Composable
private fun UserRow(user: User, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = COUITheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(user.avatar, 46.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    user.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (user.membership != Membership.None) {
                    Spacer(Modifier.width(4.dp))
                    MemberBadge(user.membership, compact = true)
                }
                if (user.verified) {
                    Spacer(Modifier.width(4.dp))
                    VerifiedBadge()
                }
            }
            if (user.description.isNotBlank()) {
                Text(
                    user.description,
                    fontSize = 12.sp,
                    color = scheme.onSurfaceSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(
                "${fmtCount(user.followers)} 粉丝",
                fontSize = 11.sp,
                color = scheme.onSurfaceSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun TopicRow(topic: TopicItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = COUITheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Text(
            "#${topic.title}#",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val meta = listOf(topic.desc1, topic.desc2)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        if (meta.isNotBlank()) {
            Text(
                meta,
                fontSize = 11.sp,
                color = scheme.onSurfaceSecondary,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}
