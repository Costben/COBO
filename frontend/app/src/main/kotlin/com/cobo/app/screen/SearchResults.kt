@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    io.github.suqi8.coui.kmp.core.ExperimentalCOUIApi::class,
)

package com.cobo.app.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cobo.app.App
import com.cobo.app.data.SearchSort
import com.cobo.app.data.Status
import com.cobo.app.data.TopicItem
import com.cobo.app.data.User
import com.cobo.app.ui.CoboIcons
import com.cobo.app.ui.NavState
import com.cobo.app.ui.Route
import com.cobo.app.ui.components.EmptyState
import com.cobo.app.ui.components.ErrorState
import com.cobo.app.ui.components.LoadingState
import com.cobo.app.ui.components.StatusCard
import com.cobo.app.ui.components.UserRow
import com.cobo.app.ui.expandOrigin
import com.cobo.app.ui.rememberExpandOrigin
import com.cobo.app.ui.resolveWeiboLink
import io.github.suqi8.coui.kmp.basic.ChipTabData
import io.github.suqi8.coui.kmp.basic.ChipTabDefaults
import io.github.suqi8.coui.kmp.basic.DropdownEntry
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.ListPopupDefaults
import io.github.suqi8.coui.kmp.basic.PopupPositionProvider
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Contacts
import io.github.suqi8.coui.kmp.icon.extended.Notes
import io.github.suqi8.coui.kmp.overlay.OverlayCascadingListPopup
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 统一的水平内边距：以 COUI 搜索历史规范为准，手机端 16dp，w600dp 以上宽屏 20dp。
 */
@Composable
fun searchHorizontalPadding(): Dp {
    val configuration = LocalConfiguration.current
    return if (configuration.screenWidthDp >= 600) 20.dp else 16.dp
}

/**
 * 搜索结果分类。
 */
enum class SearchTab(val label: String) {
    Weibo("微博"),
    Users("用户"),
    Topics("话题"),
}

/**
 * 一个分类自己的翻页状态。
 */
interface SearchTabState {
    val count: Int
    var page: Int
    var end: Boolean
    var started: Boolean
    fun reset()
}

@Stable
class SearchTabItems<T> : SearchTabState {
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

/**
 * 首页搜索面板的搜索结果状态持有器。
 */
@Stable
class SearchResultsState(
    private val scope: CoroutineScope,
) {
    var submitted by mutableStateOf("")
    var tab by mutableStateOf(SearchTab.Weibo)
    var sort by mutableStateOf(SearchSort.Comprehensive)

    val weibo = SearchTabItems<Status>()
    val users = SearchTabItems<User>()
    val topics = SearchTabItems<TopicItem>()

    var loading by mutableStateOf(false)
    var loadingMore by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    private var requestToken = 0

    fun stateFor(t: SearchTab): SearchTabState = when (t) {
        SearchTab.Weibo -> weibo
        SearchTab.Users -> users
        SearchTab.Topics -> topics
    }

    suspend fun load(which: SearchTab, reset: Boolean, force: Boolean = true) {
        val keyword = submitted
        if (keyword.isBlank()) return
        if (!reset && (loading || loadingMore)) return
        val token = if (reset) ++requestToken else requestToken
        if (reset) {
            loading = true
            error = null
            loadingMore = false
        } else {
            loadingMore = true
        }
        try {
            when (which) {
                SearchTab.Weibo -> {
                    val nextPage = if (reset) 1 else weibo.page
                    val pageItems = App.repo.search(
                        keyword,
                        nextPage,
                        force = if (reset) force else false,
                        sort = sort,
                    ).items
                    if (token != requestToken) return
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
                    if (token != requestToken) return
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
                    if (token != requestToken) return
                    if (reset) topics.items.clear()
                    val known = topics.items.mapTo(HashSet()) { it.title }
                    pageItems.forEach { if (known.add(it.title)) topics.items.add(it) }
                    topics.page = nextPage + 1
                    topics.started = true
                    if (pageItems.isEmpty()) topics.end = true
                }
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            if (token == requestToken && (!reset || which == tab)) {
                error = t.message ?: "搜索失败"
            }
        } finally {
            if (token == requestToken) {
                if (reset) {
                    if (which == tab) loading = false
                } else {
                    loadingMore = false
                }
            }
        }
    }

    fun runSearch(keyword: String, discoverState: SearchDiscoverState? = null) {
        val q = keyword.trim()
        if (q.isEmpty()) return
        discoverState?.clearEditing()
        App.settings.pushSearchHistory(q)
        weibo.reset()
        users.reset()
        topics.reset()
        submitted = q
        // 「加载中」要在这一次组合里就摆上：结果容器下一帧才读得到 loading，
        // 否则会先按「已有数据」把结果推上来，再被转圈打断。
        loading = true
        scope.launch {
            load(tab, reset = true, force = true)
        }
    }

    fun selectTab(next: SearchTab) {
        if (next == tab) return
        tab = next
        error = null
        loadingMore = false
        if (submitted.isNotBlank() && !stateFor(next).started) {
            loading = true
            scope.launch { load(next, reset = true) }
        } else {
            ++requestToken
            loading = false
        }
    }

    fun selectSort(next: SearchSort) {
        if (next == sort) return
        sort = next
        error = null
        if (submitted.isBlank()) return
        tab = SearchTab.Weibo
        weibo.reset()
        loading = true
        scope.launch { load(SearchTab.Weibo, reset = true, force = true) }
    }

    fun clearResults() {
        ++requestToken
        submitted = ""
        error = null
        loading = false
        loadingMore = false
        weibo.reset()
        users.reset()
        topics.reset()
    }
}

@Composable
fun rememberSearchResultsState(
    scope: CoroutineScope = rememberCoroutineScope(),
): SearchResultsState {
    return remember(scope) { SearchResultsState(scope) }
}

@Composable
fun ResultState(
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
fun TopicRow(topic: TopicItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
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

fun openTopicDestination(
    nav: NavState,
    topic: TopicItem,
    onSearchKeyword: (String) -> Unit,
) {
    val id = topic.containerId
    if (id != null) {
        nav.push(
            Route.SuperTopic(
                id,
                resolveWeiboLink(topic.scheme) ?: "https://m.weibo.cn/p/index?containerid=$id",
            ),
        )
        return
    }
    if (topic.scheme.isNotBlank()) {
        resolveWeiboLink(topic.scheme)?.let {
            nav.push(Route.Web(it, topic.title))
            return
        }
    }
    onSearchKeyword(topic.title)
}

/** 搜索结果的分类胶囊：分类名 + 图标，交给顶栏的筛选行画。 */
@Composable
fun rememberSearchFilterTabs(): List<ChipTabData> = remember {
    SearchTab.entries.map { tabItem ->
        val icon = when (tabItem) {
            SearchTab.Weibo -> COUIIcons.Notes
            SearchTab.Users -> COUIIcons.Contacts
            SearchTab.Topics -> CoboIcons.Topic
        }
        ChipTabData(label = tabItem.label, icon = icon)
    }
}

/**
 * 分类胶囊左侧四宫格圆钮弹出的级联菜单：分类与排序方式。
 *
 * 胶囊由顶栏组件画、菜单由调用方挂，所以锚点由调用方给：把这块放在那颗
 * [ChipTabDefaults.Height] 见方的圆钮所在的位置即可。
 */
@Composable
fun SearchFilterMenu(
    state: SearchResultsState,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    horizontalPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val dropdownPositionProvider = remember(horizontalPadding) {
        ListPopupDefaults.dropdownPositionProvider(horizontalMargin = horizontalPadding)
    }
    val menuEntries = remember(state.tab, state.sort, hapticFeedback) {
        val categoryItems = SearchTab.entries.map { tabItem ->
            DropdownItem(
                text = tabItem.label,
                selected = state.tab == tabItem,
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    state.selectTab(tabItem)
                },
            )
        }
        val sortChildren = SearchSort.entries.map { sortItem ->
            DropdownItem(
                text = sortItem.label,
                selected = sortItem == state.sort,
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    state.selectSort(sortItem)
                },
            )
        }
        val sortEntry = DropdownItem(
            text = "排序方式",
            enabled = state.tab == SearchTab.Weibo,
            children = sortChildren,
        )
        listOf(
            DropdownEntry(items = categoryItems),
            DropdownEntry(items = listOf(sortEntry)),
        )
    }

    Box(modifier) {
        OverlayCascadingListPopup(
            show = expanded,
            entries = menuEntries,
            onDismissRequest = { onExpandedChange(false) },
            popupPositionProvider = dropdownPositionProvider,
            alignment = PopupPositionProvider.Align.Start,
            collapseOnSelection = true,
        )
    }
}

/**
 * 在 LazyListScope 中添加搜索结果各项。
 */
fun LazyListScope.searchResultsItems(
    state: SearchResultsState,
    nav: NavState,
    horizontalPadding: Dp,
    scope: CoroutineScope,
    onSelectKeyword: (String) -> Unit,
) {
    if (state.loading) return

    // 分类胶囊挂在顶栏的筛选行上（见 SearchFilterTopAppBar），不再跟着列表滚走。
    when (state.tab) {
        SearchTab.Weibo -> {
            if (state.weibo.items.isEmpty()) {
                if (state.error != null || state.weibo.started) {
                    item(key = "search_weibo_empty") {
                        Box(Modifier.padding(horizontal = horizontalPadding)) {
                            ResultState(
                                loading = false,
                                error = state.error,
                                emptyText = "没有找到相关微博",
                            ) { scope.launch { state.load(SearchTab.Weibo, reset = true, force = true) } }
                        }
                    }
                }
            } else {
                itemsIndexed(state.weibo.items, key = { _, s -> "weibo_${s.id}" }) { index, status ->
                    val origin = rememberExpandOrigin()
                    Box(Modifier.padding(horizontal = 12.dp)) {
                        StatusCard(
                            status = status,
                            modifier = Modifier
                                .expandOrigin(origin)
                                .testTag("search_card_$index"),
                            onOpen = { nav.push(Route.Detail(status.id, status.mid), origin) },
                            onOpenQuoted = { quoted, quotedOrigin ->
                                nav.push(Route.Detail(quoted.id, quoted.mid), quotedOrigin)
                            },
                            onUser = { nav.push(Route.Profile(it.id)) },
                            onImage = { pics, i -> nav.push(Route.Images(pics, i)) },
                            onVideo = { nav.push(Route.Video(it.url, it.title)) },
                            onLike = {
                                scope.launch {
                                    if (!App.confirmLike(status.liked)) return@launch
                                    runCatching { App.repo.like(status.id, status.liked) }.onSuccess {
                                        state.weibo.items[index] = status.copy(
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
        }
        SearchTab.Users -> {
            if (state.users.items.isEmpty()) {
                if (state.error != null || state.users.started) {
                    item(key = "search_users_empty") {
                        Box(Modifier.padding(horizontal = horizontalPadding)) {
                            ResultState(
                                loading = false,
                                error = state.error,
                                emptyText = "没有找到相关用户",
                            ) { scope.launch { state.load(SearchTab.Users, reset = true, force = true) } }
                        }
                    }
                }
            } else {
                itemsIndexed(state.users.items, key = { _, u -> "user_${u.id}" }) { index, user ->
                    Box(Modifier.padding(horizontal = horizontalPadding)) {
                        UserRow(
                            user = user,
                            modifier = Modifier.testTag("search_user_$index"),
                            onClick = { nav.push(Route.Profile(user.id)) },
                        )
                    }
                }
            }
        }
        SearchTab.Topics -> {
            if (state.topics.items.isEmpty()) {
                if (state.error != null || state.topics.started) {
                    item(key = "search_topics_empty") {
                        Box(Modifier.padding(horizontal = horizontalPadding)) {
                            ResultState(
                                loading = false,
                                error = state.error,
                                emptyText = "没有找到相关话题",
                            ) { scope.launch { state.load(SearchTab.Topics, reset = true, force = true) } }
                        }
                    }
                }
            } else {
                itemsIndexed(state.topics.items, key = { _, t -> "topic_${t.title}" }) { index, topic ->
                    Box(Modifier.padding(horizontal = horizontalPadding)) {
                        TopicRow(
                            topic = topic,
                            modifier = Modifier.testTag("search_topic_$index"),
                            onClick = {
                                openTopicDestination(
                                    nav = nav,
                                    topic = topic,
                                    onSearchKeyword = onSelectKeyword,
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    item(key = "search_loading_more_or_end") {
        val tabState = state.stateFor(state.tab)
        val scheme = COUITheme.colorScheme
        when {
            state.loadingMore -> LoadingState()
            tabState.end && tabState.count > 0 -> Text(
                "没有更多了",
                fontSize = 12.sp,
                color = scheme.onSurfaceSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding, vertical = 16.dp),
            )
        }
    }
}
