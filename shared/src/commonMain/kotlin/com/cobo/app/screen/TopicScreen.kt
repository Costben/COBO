package com.cobo.app.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cobo.app.App
import com.cobo.app.data.ApiException
import com.cobo.app.data.FollowedSuperTopic
import com.cobo.app.data.FollowedSuperTopicPage
import com.cobo.app.data.TopicItem
import com.cobo.app.data.followedSuperTopicsPage
import com.cobo.app.data.followedSuperTopicsWebUrl
import com.cobo.app.data.superTopicId
import com.cobo.app.ui.NavState
import com.cobo.app.ui.PinnedBarDensity
import com.cobo.app.ui.Route
import com.cobo.app.ui.pageContainerColor
import com.cobo.app.ui.resolveWeiboLink
import com.cobo.app.ui.components.Avatar
import com.cobo.app.ui.components.BackIconButton
import com.cobo.app.ui.components.EmptyState
import com.cobo.app.ui.components.ErrorState
import com.cobo.app.ui.components.FoldableIntroText
import com.cobo.app.ui.components.LoadingState
import com.cobo.app.ui.components.SectionTitle
import com.cobo.app.ui.components.navBottomPadding
import io.github.suqi8.coui.kmp.basic.BindScrollToTop
import io.github.suqi8.coui.kmp.basic.Button
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.TextField
import io.github.suqi8.coui.kmp.basic.TextFieldMode
import io.github.suqi8.coui.kmp.basic.cardListPositionOf
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.cobo.app.platform.urlEncode

class SuperTopicSearchStore(
    private val loader: suspend (String) -> List<TopicItem> = { App.repo.searchSuperTopics(it) },
) {
    var results by mutableStateOf(emptyList<TopicItem>())
        private set
    var loading by mutableStateOf(false)
        private set
    var completed by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var loginRequired by mutableStateOf(false)
        private set
    private var generation = 0

    fun reset() {
        generation++
        results = emptyList()
        loading = false
        completed = false
        error = null
        loginRequired = false
    }

    suspend fun search(query: String): TopicItem? {
        reset()
        val name = query.trim().trim('#').trim()
        if (name.isBlank()) return null
        val request = generation
        loading = true
        try {
            val found = loader(name)
            if (request != generation) return null
            results = found
            completed = true
            val exact = found.filter { normalizedTopicName(it.title) == normalizedTopicName(name) }
            return exact.singleOrNull()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (request == generation) {
                loginRequired = e is ApiException && (e.errno == "-100" || e.errno == "432")
                error = if (loginRequired) "请先登录，再搜索超话" else "超话搜索失败，请重试或使用网页搜索"
            }
        } finally {
            if (request == generation) loading = false
        }
        return null
    }
}

private fun normalizedTopicName(name: String): String =
    name.trim().trim('#').trim().removeSuffix("[超话]").removeSuffix("超话").trim().lowercase()

class FollowedSuperTopicsStore(
    private val loader: suspend (Int) -> FollowedSuperTopicPage = { page -> App.repo.followedSuperTopicsPage(page) },
) {
    var items by mutableStateOf<List<FollowedSuperTopic>>(emptyList())
        private set
    var hasLoaded by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set
    var loadingMore by mutableStateOf(false)
        private set
    var hasMore by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var loadMoreError by mutableStateOf<String?>(null)
        private set
    var loginRequired by mutableStateOf(false)
        private set

    private var nextPage = 1
    private var generation = 0
    private val seenIds = LinkedHashSet<String>()

    fun reset() {
        generation++
        items = emptyList()
        hasLoaded = false
        loading = false
        loadingMore = false
        hasMore = false
        error = null
        loadMoreError = null
        loginRequired = false
        nextPage = 1
        seenIds.clear()
    }

    suspend fun refresh() {
        if (loading) return
        val request = ++generation
        loading = true
        loadingMore = false
        error = null
        loadMoreError = null
        loginRequired = false
        try {
            val page = loader(1)
            if (request != generation) return
            seenIds.clear()
            val deduped = page.items.filter { seenIds.add(it.id) }
            items = deduped
            hasLoaded = true
            hasMore = page.hasMore
            nextPage = 2
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (request == generation) {
                loginRequired = e is ApiException && e.errno in AUTH_ERRNOS
                error = (e as? ApiException)?.message ?: "读取已关注超话失败，可查看官网名单"
            }
        } finally {
            if (request == generation) loading = false
        }
    }

    suspend fun loadMore() {
        if (loading || loadingMore || !hasMore || !hasLoaded) return
        val request = generation
        val targetPage = nextPage
        loadingMore = true
        loadMoreError = null
        try {
            val page = loader(targetPage)
            if (request != generation) return
            val fresh = page.items.filter { seenIds.add(it.id) }
            if (page.items.isNotEmpty() && fresh.isEmpty()) {
                loadMoreError = "已关注超话分页重复，请重试"
                return
            }
            items = items + fresh
            hasMore = page.hasMore
            nextPage = targetPage + 1
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (request == generation) {
                loadMoreError = (e as? ApiException)?.message ?: "加载更多失败，请重试"
            }
        } finally {
            if (request == generation) loadingMore = false
        }
    }

    private companion object {
        val AUTH_ERRNOS = setOf("-100", "401", "403", "432")
    }
}

@Composable
fun TopicScreen(nav: NavState, initial: String = "") {
    var query by remember { mutableStateOf(initial) }
    val store = remember { SuperTopicSearchStore() }
    val scope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }

    // 监听当前路由变化，确保从登录页返回后重新读取 App.repo.session。
    val currentRoute = nav.current
    val activeSession = App.repo.session?.takeUnless { App.repo.guest }
    val loggedIn = activeSession != null
    val sessionKey = if (loggedIn) {
        activeSession.uid.ifBlank { "cookie:${activeSession.cookie.hashCode()}" }
    } else {
        ""
    }
    val followedStore = remember(sessionKey) { FollowedSuperTopicsStore() }
    var officialList by remember(sessionKey) {
        mutableStateOf(followedSuperTopicsWebUrl(activeSession?.uid.orEmpty()))
    }
    val superTopicFollowVersion = App.repo.superTopicFollowVersion
    var seenFollowVersion by remember(sessionKey) { mutableStateOf(superTopicFollowVersion) }

    LaunchedEffect(sessionKey, query.isBlank(), currentRoute is Route.Topic, superTopicFollowVersion) {
        if (!loggedIn || !query.isBlank() || currentRoute !is Route.Topic) return@LaunchedEffect
        if (officialList == null) {
            officialList = runCatching { App.repo.ownUid() }.getOrNull()?.let(::followedSuperTopicsWebUrl)
        }
        val followChanged = seenFollowVersion != superTopicFollowVersion
        if (followChanged) {
            seenFollowVersion = superTopicFollowVersion
            followedStore.refresh()
        } else if (!followedStore.hasLoaded && !followedStore.loading && followedStore.error == null) {
            followedStore.refresh()
        }
    }

    fun openTopic(topic: TopicItem) {
        val id = topic.containerId ?: return
        val original = topic.scheme.takeIf { it != id && superTopicId(it) == id }
        val fallback = original?.let(::resolveWeiboLink) ?: "https://m.weibo.cn/p/index?containerid=$id"
        nav.push(Route.SuperTopic(id, fallback))
    }

    fun submit() {
        val name = query.trim()
        if (name.isBlank()) return
        val id = superTopicId(name)
        if (id != null) {
            openTopic(TopicItem(name, scheme = name, containerId = id))
            return
        }
        if (name.startsWith("https://") || name.startsWith("http://") ||
            name.startsWith("sinaweibo://") || name.startsWith("intent://")) {
            resolveWeiboLink(name)?.let { nav.push(Route.Web(it, "网页")) }
            return
        }
        searchJob?.cancel()
        val entry = nav.stack.last()
        searchJob = scope.launch {
            val match = store.search(name)
            if (match != null && nav.stack.lastOrNull() === entry && !entry.exiting) openTopic(match)
        }
    }

    fun openWebSearch() {
        val name = query.trim()
        if (name.isBlank()) return
        val cid = "100103type=1&q=$name"
        val url = "https://m.weibo.cn/search?containerid=" + urlEncode(cid)
        nav.push(Route.Web(url, name))
    }

    Scaffold(
        containerColor = pageContainerColor(),
        topBar = {
            PinnedBarDensity {
                SmallTopAppBar(
                    color = pageContainerColor(),
                    title = "话题 / 超话",
                    navigationIcon = {
                        BackIconButton {
                            searchJob?.cancel()
                            store.reset()
                            nav.pop()
                        }
                    },
                )
            }
        },
    ) { padding ->
        val listState = rememberLazyListState()
        BindScrollToTop(listState)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().scrollEndHaptic().padding(padding),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp + navBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "输入超话名称，唯一同名结果会直接打开。也可以粘贴超话链接或 ID。",
                    fontSize = 13.sp,
                    color = COUITheme.colorScheme.onSurfaceSecondary,
                )
                Spacer(Modifier.height(14.dp))
                TextField(
                    value = query,
                    onValueChange = {
                        query = it
                        searchJob?.cancel()
                        store.reset()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = "超话名称",
                    backgroundMode = TextFieldMode.Line,
                    singleLine = true,
                    showClearButton = true,
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = ::submit,
                    enabled = query.isNotBlank() && !store.loading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (superTopicId(query.trim()) != null) "打开超话" else "搜索超话")
                }
                TextButton(text = "网页搜索", onClick = ::openWebSearch)
            }
            when {
                store.loading -> item { LoadingState() }
                store.error != null -> item {
                    Column {
                        ErrorState(store.error!!, onRetry = ::submit)
                        if (store.loginRequired) {
                            TextButton(text = "前往登录", onClick = { nav.push(Route.SmsLogin()) })
                        }
                    }
                }
                store.completed && store.results.isEmpty() -> item {
                    EmptyState("未找到对应超话，可以换个名称重试或使用网页搜索")
                }
                store.results.isNotEmpty() -> {
                    item { Text("请选择超话", fontSize = 14.sp) }
                    items(store.results, key = { it.containerId!! }) { topic ->
                        Column {
                            TextButton(text = topic.title, onClick = { openTopic(topic) }, modifier = Modifier.fillMaxWidth())
                            val description = listOf(topic.desc1, topic.desc2).filter { it.isNotBlank() }.joinToString(" · ")
                            if (description.isNotBlank()) {
                                FoldableIntroText(
                                    text = description,
                                    budget = 120,
                                    maxNewlines = 1,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    color = COUITheme.colorScheme.onSurfaceSecondary,
                                )
                            }
                        }
                    }
                }
            }
            if (query.isBlank()) {
                item(key = "followed_supertopics_header") {
                    SectionTitle(
                        text = if (followedStore.items.isNotEmpty()) {
                            "已关注超话 · ${followedStore.items.size}"
                        } else {
                            "已关注超话"
                        },
                        horizontalPadding = 6.dp,
                    )
                }
                when {
                    !loggedIn -> item(key = "followed_supertopics_guest") {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            EmptyState("登录微博后查看已关注超话")
                            TextButton(text = "前往登录", onClick = { nav.push(Route.SmsLogin()) })
                        }
                    }
                    followedStore.loading && followedStore.items.isEmpty() -> item(key = "followed_supertopics_loading") {
                        LoadingState()
                    }
                    followedStore.error != null && followedStore.items.isEmpty() -> item(key = "followed_supertopics_error") {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            ErrorState(
                                message = followedStore.error!!,
                                onRetry = { scope.launch { followedStore.refresh() } },
                            )
                            if (followedStore.loginRequired) {
                                TextButton(text = "前往登录", onClick = { nav.push(Route.SmsLogin()) })
                            }
                        }
                    }
                    !followedStore.hasLoaded -> item(key = "followed_supertopics_pending") {
                        LoadingState()
                    }
                    followedStore.items.isEmpty() -> item(key = "followed_supertopics_empty") {
                        EmptyState("还没有关注超话")
                    }
                    else -> {
                        if (followedStore.error != null) {
                            item(key = "followed_supertopics_refresh_error") {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = followedStore.error!!,
                                        fontSize = 12.sp,
                                        color = COUITheme.colorScheme.onSurfaceSecondary,
                                        modifier = Modifier.weight(1f),
                                    )
                                    TextButton(
                                        text = "重试",
                                        onClick = { scope.launch { followedStore.refresh() } },
                                    )
                                }
                            }
                        }
                        item(key = "followed_supertopics_card") {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = CardDefaults.GroupCornerRadius,
                                insideMargin = PaddingValues(0.dp),
                                weight = CardDefaults.GroupWeight,
                            ) {
                                val list = followedStore.items
                                list.forEachIndexed { index, topic ->
                                    ArrowPreference(
                                        title = topic.name,
                                        modifier = Modifier.testTag("followed_supertopic_${topic.id}"),
                                        startAction = {
                                            Avatar(topic.avatarUrl, 36.dp)
                                            Spacer(Modifier.width(12.dp))
                                        },
                                        centerStartAction = true,
                                        cardListPosition = cardListPositionOf(index, list.size),
                                        onClick = { openTopic(TopicItem(topic.name, containerId = topic.id)) },
                                    )
                                }
                            }
                        }
                        if (followedStore.hasMore || followedStore.loadingMore || followedStore.loadMoreError != null) {
                            item(key = "followed_supertopics_footer") {
                                LaunchedEffect(
                                    followedStore.items.size,
                                    followedStore.hasMore,
                                    followedStore.loadMoreError,
                                ) {
                                    if (followedStore.hasMore && !followedStore.loadingMore && followedStore.loadMoreError == null) {
                                        followedStore.loadMore()
                                    }
                                }
                                when {
                                    followedStore.loadingMore -> LoadingState()
                                    followedStore.loadMoreError != null -> ErrorState(
                                        message = followedStore.loadMoreError!!,
                                        onRetry = { scope.launch { followedStore.loadMore() } },
                                    )
                                    followedStore.hasMore -> Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                    ) {
                                        TextButton(
                                            text = "加载更多",
                                            onClick = { scope.launch { followedStore.loadMore() } },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (loggedIn) {
                    item(key = "followed_supertopics_actions") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (followedStore.hasLoaded || followedStore.items.isNotEmpty()) {
                                TextButton(
                                    text = if (followedStore.loading) "刷新中…" else "刷新关注列表",
                                    enabled = !followedStore.loading,
                                    onClick = { scope.launch { followedStore.refresh() } },
                                )
                            }
                            officialList?.let { url ->
                                TextButton(
                                    text = "查看官网关注列表",
                                    onClick = { nav.push(Route.Web(url, "我关注的超话")) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
