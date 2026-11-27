@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    io.github.suqi8.coui.kmp.core.ExperimentalCOUIApi::class,
)

package com.cobo.app.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import com.cobo.app.platform.platformScreenSizePx
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.cobo.app.App
import com.cobo.app.data.ContentFilterRules
import com.cobo.app.data.ContentFilters
import com.cobo.app.data.DetailInitialSection
import com.cobo.app.data.FeedPage
import com.cobo.app.data.FilterSurface
import com.cobo.app.data.FollowTags
import com.cobo.app.data.Membership
import com.cobo.app.data.SearchFeaturedUser
import com.cobo.app.data.SearchSort
import com.cobo.app.data.Status
import com.cobo.app.data.TopicItem
import com.cobo.app.data.User
import com.cobo.app.data.shouldFilterStatus
import com.cobo.app.ui.CoboIcons
import com.cobo.app.ui.LocalLinkHandler
import com.cobo.app.ui.NavState
import com.cobo.app.ui.Route
import com.cobo.app.ui.components.Avatar
import com.cobo.app.ui.components.EmptyState
import com.cobo.app.ui.components.ErrorState
import com.cobo.app.ui.components.FoldableIntroText
import com.cobo.app.ui.components.FollowTagBadge
import com.cobo.app.ui.components.LoadingState
import com.cobo.app.ui.components.MemberBadge
import com.cobo.app.ui.components.StatusCard
import com.cobo.app.ui.components.UserRow
import com.cobo.app.ui.components.fmtCount
import com.cobo.app.ui.expandOrigin
import com.cobo.app.ui.rememberExpandOrigin
import com.cobo.app.ui.resolveWeiboLink
import io.github.suqi8.coui.kmp.basic.ButtonDefaults
import io.github.suqi8.coui.kmp.basic.ButtonSize
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.ChipTabData
import io.github.suqi8.coui.kmp.basic.ChipTabDefaults
import io.github.suqi8.coui.kmp.basic.DropdownEntry
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.ListPopupDefaults
import io.github.suqi8.coui.kmp.basic.PopupPositionProvider
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Community
import io.github.suqi8.coui.kmp.icon.extended.Contacts
import io.github.suqi8.coui.kmp.icon.extended.Image
import io.github.suqi8.coui.kmp.icon.extended.Location
import io.github.suqi8.coui.kmp.icon.extended.Notes
import io.github.suqi8.coui.kmp.icon.extended.Play
import io.github.suqi8.coui.kmp.overlay.OverlayCascadingListPopup
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.squircle.isSquircleEnabled
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 统一的水平内边距：以 COUI 搜索历史规范为准，手机端 16dp，w600dp 以上宽屏 20dp。
 */
@Composable
fun searchHorizontalPadding(): Dp {
    val widthDp = platformScreenSizePx().first / LocalDensity.current.density
    return if (widthDp >= 600f) 20.dp else 16.dp
}

/**
 * 搜索结果分类。前 3 项保持「微博 / 用户 / 话题」，其余分类在胶囊栏可横向滚动或点左侧菜单切换。
 */
enum class SearchTab(val label: String) {
    Weibo("微博"),
    Users("用户"),
    Topics("话题"),
    Pictures("图片"),
    Videos("视频"),
    Following("关注"),
    SuperTopics("超话"),
    Places("地点"),
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

    val featuredUsers = mutableStateListOf<SearchFeaturedUser>()
    val weibo = SearchTabItems<Status>()
    val users = SearchTabItems<User>()
    val topics = SearchTabItems<TopicItem>()
    val pictures = SearchTabItems<Status>()
    val videos = SearchTabItems<Status>()
    val following = SearchTabItems<Status>()
    val superTopics = SearchTabItems<TopicItem>()
    val places = SearchTabItems<TopicItem>()

    var loading by mutableStateOf(false)
    var loadingMore by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    private var requestToken = 0

    fun stateFor(t: SearchTab): SearchTabState = when (t) {
        SearchTab.Weibo -> weibo
        SearchTab.Users -> users
        SearchTab.Topics -> topics
        SearchTab.Pictures -> pictures
        SearchTab.Videos -> videos
        SearchTab.Following -> following
        SearchTab.SuperTopics -> superTopics
        SearchTab.Places -> places
    }

    private suspend fun loadFeedTab(
        target: SearchTabItems<Status>,
        reset: Boolean,
        token: Int,
        onResetFeatured: ((List<SearchFeaturedUser>) -> Unit)? = null,
        fetch: suspend (page: Int) -> FeedPage,
    ) {
        val nextPage = if (reset) 1 else target.page
        val pageResult = fetch(nextPage)
        if (token != requestToken) return
        if (reset) {
            target.items.clear()
            onResetFeatured?.invoke(pageResult.featuredUsers)
        }
        val known = target.items.mapTo(HashSet()) { it.id }
        pageResult.items.forEach { if (known.add(it.id)) target.items.add(it) }
        target.page = nextPage + 1
        target.started = true
        if (pageResult.items.isEmpty()) target.end = true
    }

    private suspend fun loadTopicTab(
        target: SearchTabItems<TopicItem>,
        reset: Boolean,
        token: Int,
        fetch: suspend (page: Int) -> List<TopicItem>,
    ) {
        val nextPage = if (reset) 1 else target.page
        val pageItems = fetch(nextPage)
        if (token != requestToken) return
        if (reset) target.items.clear()
        val known = target.items.mapTo(HashSet()) { it.containerId ?: it.scheme.ifBlank { it.title } }
        pageItems.forEach { item ->
            val key = item.containerId ?: item.scheme.ifBlank { item.title }
            if (known.add(key)) target.items.add(item)
        }
        target.page = nextPage + 1
        target.started = true
        if (pageItems.isEmpty()) target.end = true
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
            val f = if (reset) force else false
            when (which) {
                SearchTab.Weibo -> loadFeedTab(
                    target = weibo,
                    reset = reset,
                    token = token,
                    onResetFeatured = { list ->
                        featuredUsers.clear()
                        if (sort == SearchSort.Comprehensive) featuredUsers.addAll(list)
                    },
                ) { nextPage -> App.repo.search(keyword, nextPage, force = f, sort = sort) }

                SearchTab.Users -> {
                    val nextPage = if (reset) 1 else users.page
                    val pageItems = App.repo.searchUsers(keyword, nextPage, force = f)
                    if (token != requestToken) return
                    if (reset) users.items.clear()
                    val known = users.items.mapTo(HashSet()) { it.id }
                    pageItems.forEach { if (known.add(it.id)) users.items.add(it) }
                    users.page = nextPage + 1
                    users.started = true
                    if (pageItems.isEmpty()) users.end = true
                }

                SearchTab.Topics -> loadTopicTab(topics, reset, token) { nextPage ->
                    App.repo.searchTopics(keyword, nextPage, force = f)
                }

                SearchTab.Pictures -> loadFeedTab(pictures, reset, token) { nextPage ->
                    App.repo.searchPictures(keyword, nextPage, force = f)
                }

                SearchTab.Videos -> loadFeedTab(videos, reset, token) { nextPage ->
                    App.repo.searchVideos(keyword, nextPage, force = f)
                }

                SearchTab.Following -> loadFeedTab(following, reset, token) { nextPage ->
                    App.repo.searchFollowing(keyword, nextPage, force = f)
                }

                SearchTab.SuperTopics -> loadTopicTab(superTopics, reset, token) { nextPage ->
                    App.repo.searchSuperTopicCards(keyword, nextPage, force = f)
                }

                SearchTab.Places -> loadTopicTab(places, reset, token) { nextPage ->
                    App.repo.searchPlaces(keyword, nextPage, force = f)
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

    private fun resetAllTabs() {
        featuredUsers.clear()
        weibo.reset()
        users.reset()
        topics.reset()
        pictures.reset()
        videos.reset()
        following.reset()
        superTopics.reset()
        places.reset()
    }

    fun runSearch(keyword: String, discoverState: SearchDiscoverState? = null) {
        val q = keyword.trim()
        if (q.isEmpty()) return
        discoverState?.clearEditing()
        App.settings.pushSearchHistory(q)
        resetAllTabs()
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
        featuredUsers.clear()
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
        resetAllTabs()
    }
}

@Composable
fun rememberSearchResultsState(
    scope: CoroutineScope = rememberCoroutineScope(),
): SearchResultsState {
    val state = remember(scope) { SearchResultsState(scope) }
    SyncLikedStatuses(state.weibo.items, state.pictures.items, state.videos.items, state.following.items)
    return state
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
fun TopicRow(
    topic: TopicItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hashWrap: Boolean = true,
) {
    val scheme = COUITheme.colorScheme
    val squircleEnabled = isSquircleEnabled()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (topic.pic.isNotBlank()) {
            val shape = remember(squircleEnabled) { SquircleShape(10.dp, squircleEnabled = squircleEnabled) }
            AsyncImage(
                model = topic.pic,
                contentDescription = topic.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(44.dp)
                    .clip(shape)
                    .background(scheme.surfaceContainerHigh),
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                if (hashWrap) "#${topic.title}#" else topic.title,
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
                    maxLines = if (App.settings.foldLongText) 2 else Int.MAX_VALUE,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun SearchFeaturedUserCard(
    featured: SearchFeaturedUser,
    onOpenProfile: () -> Unit,
    onFollowClick: () -> Unit,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = COUITheme.colorScheme
    val user = featured.user
    val tag = FollowTags[user.id]
    val canFollow = !App.repo.guest && user.id != App.repo.session?.uid
    Card(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = CardDefaults.GroupCornerRadius,
        insideMargin = PaddingValues(14.dp),
        weight = CardDefaults.GroupWeight,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onOpenProfile),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(user.avatar, 52.dp, verifiedKind = user.verifiedKind)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text(
                            text = user.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (tag != null) {
                            FollowTagBadge(tag, compact = true)
                        }
                        if (user.membership != Membership.None) {
                            MemberBadge(user.membership, compact = true, rank = user.mbrank)
                        }
                    }
                    val subtitle = featured.desc1.ifBlank { user.description }
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            fontSize = 12.sp,
                            color = scheme.onSurfaceSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
                if (canFollow) {
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        text = if (user.following) "已关注" else "关注",
                        onClick = onFollowClick,
                        colors = if (user.following) ButtonDefaults.textButtonColors() else ButtonDefaults.textButtonColorsPrimary(),
                        size = ButtonSize.Small,
                        modifier = Modifier.testTag("search_featured_follow"),
                    )
                }
            }

            val stats = buildList {
                if (user.statuses > 0) add("微博 ${fmtCount(user.statuses)}")
                if (user.friends > 0) add("关注 ${fmtCount(user.friends)}")
                if (user.followers > 0) add("粉丝 ${fmtCount(user.followers)}")
            }.joinToString(" · ")
            if (stats.isNotBlank()) {
                Text(
                    text = stats,
                    fontSize = 12.sp,
                    color = scheme.onSurfaceSecondary,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            if (user.description.isNotBlank() && user.description != featured.desc1) {
                FoldableIntroText(
                    text = user.description,
                    budget = 100,
                    maxNewlines = 1,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = scheme.onSurface,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (featured.desc2.isNotBlank()) {
                Text(
                    text = featured.desc2,
                    fontSize = 12.sp,
                    color = scheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .then(
                            if (featured.desc2Scheme.isNotBlank()) {
                                Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onOpenLink(featured.desc2Scheme) }
                            } else {
                                Modifier
                            },
                        ),
                )
            }
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
        val topicQuery = com.cobo.app.ui.weiboTopicQuery(topic.scheme)
        if (topicQuery != null) {
            nav.push(Route.TopicFeed(topicQuery))
            return
        }
    }
    val cleanTitle = topic.title.trim()
    if (cleanTitle.isNotEmpty()) {
        val formatted = if (cleanTitle.startsWith("#") && cleanTitle.endsWith("#")) cleanTitle else "#$cleanTitle#"
        nav.push(Route.TopicFeed(formatted))
        return
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
            SearchTab.Pictures -> COUIIcons.Image
            SearchTab.Videos -> COUIIcons.Play
            SearchTab.Following -> COUIIcons.Community
            SearchTab.SuperTopics -> CoboIcons.SuperTopic
            SearchTab.Places -> COUIIcons.Location
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
    filterRules: ContentFilterRules = ContentFilters.rules,
    selfUid: String? = App.repo.session?.uid,
) {
    if (state.loading) return

    // 分类胶囊挂在顶栏的筛选行上（见 SearchFilterTopAppBar），不再跟着列表滚走。
    when (state.tab) {
        SearchTab.Weibo -> {
            if (state.sort == SearchSort.Comprehensive && state.featuredUsers.isNotEmpty()) {
                itemsIndexed(
                    state.featuredUsers,
                    key = { _, f -> "featured_${f.user.id}" },
                ) { idx, featured ->
                    val linkHandler = LocalLinkHandler.current
                    val effectiveUser = featured.user.copy(
                        following = App.repo.isFollowingUser(featured.user.id, featured.user.following),
                    )
                    Box(Modifier.padding(horizontal = 12.dp)) {
                        SearchFeaturedUserCard(
                            featured = featured.copy(user = effectiveUser),
                            onOpenProfile = { nav.push(Route.Profile(featured.user.id)) },
                            onFollowClick = {
                                val target = effectiveUser
                                if (!App.repo.isFollowInFlight(target.id)) {
                                    val targetFollow = !target.following
                                    scope.launch {
                                        runCatching { App.repo.follow(target.id, targetFollow) }
                                            .onSuccess { applied ->
                                                if (applied) {
                                                    val realIdx = state.featuredUsers.indexOfFirst { it.user.id == target.id }
                                                    if (realIdx >= 0) {
                                                        state.featuredUsers[realIdx] = state.featuredUsers[realIdx].copy(
                                                            user = target.copy(following = targetFollow),
                                                        )
                                                    }
                                                    App.notifyFollowChanged()
                                                    App.toast(if (targetFollow) "已关注" else "已取消关注")
                                                }
                                            }
                                            .onFailure { App.toast(it.message ?: "操作失败") }
                                    }
                                }
                            },
                            onOpenLink = { url -> linkHandler(url) },
                            modifier = Modifier.testTag("search_featured_user_$idx"),
                        )
                    }
                }
            }
            searchStatusTabItems(
                items = state.weibo.items,
                started = state.weibo.started,
                error = state.error,
                tabKey = "weibo",
                emptyText = "没有找到相关微博",
                horizontalPadding = horizontalPadding,
                nav = nav,
                scope = scope,
                filterRules = filterRules,
                selfUid = selfUid,
                onRetry = { scope.launch { state.load(SearchTab.Weibo, reset = true, force = true) } },
            )
        }

        SearchTab.Pictures -> searchStatusTabItems(
            items = state.pictures.items,
            started = state.pictures.started,
            error = state.error,
            tabKey = "pictures",
            emptyText = "没有找到相关图片微博",
            horizontalPadding = horizontalPadding,
            nav = nav,
            scope = scope,
            filterRules = filterRules,
            selfUid = selfUid,
            onRetry = { scope.launch { state.load(SearchTab.Pictures, reset = true, force = true) } },
        )

        SearchTab.Videos -> searchStatusTabItems(
            items = state.videos.items,
            started = state.videos.started,
            error = state.error,
            tabKey = "videos",
            emptyText = "没有找到相关视频",
            horizontalPadding = horizontalPadding,
            nav = nav,
            scope = scope,
            filterRules = filterRules,
            selfUid = selfUid,
            onRetry = { scope.launch { state.load(SearchTab.Videos, reset = true, force = true) } },
        )

        SearchTab.Following -> searchStatusTabItems(
            items = state.following.items,
            started = state.following.started,
            error = state.error,
            tabKey = "following",
            emptyText = if (App.repo.guest) "登录后可查看关注的人里的搜索结果" else "关注的人里没有找到相关微博",
            horizontalPadding = horizontalPadding,
            nav = nav,
            scope = scope,
            filterRules = filterRules,
            selfUid = selfUid,
            onRetry = { scope.launch { state.load(SearchTab.Following, reset = true, force = true) } },
        )

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

        SearchTab.Topics -> searchTopicTabItems(
            items = state.topics.items,
            started = state.topics.started,
            error = state.error,
            tabKey = "topic",
            emptyText = "没有找到相关话题",
            hashWrap = true,
            horizontalPadding = horizontalPadding,
            onRetry = { scope.launch { state.load(SearchTab.Topics, reset = true, force = true) } },
            onClick = { topic ->
                openTopicDestination(nav = nav, topic = topic, onSearchKeyword = onSelectKeyword)
            },
        )

        SearchTab.SuperTopics -> searchTopicTabItems(
            items = state.superTopics.items,
            started = state.superTopics.started,
            error = state.error,
            tabKey = "supertopic",
            emptyText = "没有找到相关超话",
            hashWrap = false,
            horizontalPadding = horizontalPadding,
            onRetry = { scope.launch { state.load(SearchTab.SuperTopics, reset = true, force = true) } },
            onClick = { topic ->
                openTopicDestination(nav = nav, topic = topic, onSearchKeyword = onSelectKeyword)
            },
        )

        SearchTab.Places -> searchTopicTabItems(
            items = state.places.items,
            started = state.places.started,
            error = state.error,
            tabKey = "place",
            emptyText = "没有找到相关地点",
            hashWrap = false,
            horizontalPadding = horizontalPadding,
            onRetry = { scope.launch { state.load(SearchTab.Places, reset = true, force = true) } },
            onClick = { place ->
                val url = resolveWeiboLink(place.scheme)
                if (url != null) {
                    nav.push(Route.Web(url, place.title))
                } else {
                    onSelectKeyword(place.title)
                }
            },
        )
    }

    item(key = "search_loading_more_or_end") {
        val tabState = state.stateFor(state.tab)
        val scheme = COUITheme.colorScheme
        when {
            state.loadingMore -> LoadingState()
            tabState.end && (tabState.count > 0 || (state.tab == SearchTab.Weibo && state.featuredUsers.isNotEmpty())) -> Text(
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

private fun LazyListScope.searchStatusTabItems(
    items: MutableList<Status>,
    started: Boolean,
    error: String?,
    tabKey: String,
    emptyText: String,
    horizontalPadding: Dp,
    nav: NavState,
    scope: CoroutineScope,
    filterRules: ContentFilterRules,
    selfUid: String?,
    onRetry: () -> Unit,
) {
    if (items.isEmpty()) {
        if (error != null || started) {
            item(key = "search_${tabKey}_empty") {
                Box(Modifier.padding(horizontal = horizontalPadding)) {
                    ResultState(
                        loading = false,
                        error = error,
                        emptyText = emptyText,
                        onRetry = onRetry,
                    )
                }
            }
        }
        return
    }
    val visibleItems = items.filterNot {
        shouldFilterStatus(it, filterRules, FilterSurface.SearchAndHot, selfUid)
    }
    if (visibleItems.isEmpty()) {
        item(key = "search_${tabKey}_filtered_empty") {
            Box(Modifier.padding(horizontal = horizontalPadding)) {
                EmptyState("当前结果均已被屏蔽规则隐藏")
            }
        }
        return
    }
    itemsIndexed(visibleItems, key = { _, s -> "${tabKey}_${s.id}" }) { idx, status ->
        val origin = rememberExpandOrigin()
        val feedCardFrame = App.settings.feedCardFrame
        val showDivider = !feedCardFrame && visibleItems.drop(idx + 1).any { !App.repo.isDeleted(it.id) }
        Box(Modifier.padding(horizontal = 12.dp)) {
            StatusCard(
                status = status,
                modifier = Modifier
                    .expandOrigin(origin)
                    .then(if (tabKey == "weibo") Modifier.testTag("search_card_$idx") else Modifier.testTag("search_${tabKey}_$idx")),
                keepFrame = feedCardFrame,
                showBottomDivider = showDivider,
                onOpen = { nav.push(Route.Detail(status.id, status.mid), origin) },
                onDeleted = { items.removeAll { it.id == status.id } },
                onOpenQuoted = { quoted, quotedOrigin ->
                    nav.push(Route.Detail(quoted.id, quoted.mid), quotedOrigin)
                },
                onUser = { nav.push(Route.Profile(it.id)) },
                onImage = { pics, i -> nav.push(Route.Images(pics, i)) },
                onVideo = { nav.push(Route.Video(it.url, it.title, it.isLive, it.fid, it.statusId)) },
                onLike = {
                    scope.launch {
                        val updated = App.toggleStatusLike(status) ?: return@launch
                        val realIdx = items.indexOfFirst { it.id == status.id }
                        if (realIdx >= 0) {
                            items[realIdx] = updated
                        }
                    }
                },
                onRepost = {
                    nav.push(
                        Route.Detail(status.id, status.mid, initialSection = DetailInitialSection.Reposts),
                        origin,
                    )
                },
                onComment = {
                    nav.push(
                        Route.Detail(status.id, status.mid, initialSection = DetailInitialSection.Comments),
                        origin,
                    )
                },
            )
        }
    }
}

private fun LazyListScope.searchTopicTabItems(
    items: List<TopicItem>,
    started: Boolean,
    error: String?,
    tabKey: String,
    emptyText: String,
    hashWrap: Boolean,
    horizontalPadding: Dp,
    onRetry: () -> Unit,
    onClick: (TopicItem) -> Unit,
) {
    if (items.isEmpty()) {
        if (error != null || started) {
            item(key = "search_${tabKey}_empty") {
                Box(Modifier.padding(horizontal = horizontalPadding)) {
                    ResultState(
                        loading = false,
                        error = error,
                        emptyText = emptyText,
                        onRetry = onRetry,
                    )
                }
            }
        }
        return
    }
    itemsIndexed(items, key = { idx, t -> "${tabKey}_${idx}_${t.containerId ?: t.title}" }) { index, topic ->
        Box(Modifier.padding(horizontal = horizontalPadding)) {
            TopicRow(
                topic = topic,
                hashWrap = hashWrap,
                modifier = Modifier.testTag("search_${tabKey}_$index"),
                onClick = { onClick(topic) },
            )
        }
    }
}
