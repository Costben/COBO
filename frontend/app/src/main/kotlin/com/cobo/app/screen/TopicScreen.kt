package com.cobo.app.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cobo.app.App
import com.cobo.app.data.ApiException
import com.cobo.app.data.TopicItem
import com.cobo.app.data.FollowedSuperTopic
import com.cobo.app.data.followedSuperTopics
import com.cobo.app.data.followedSuperTopicsWebUrl
import com.cobo.app.data.superTopicId
import com.cobo.app.ui.NavState
import com.cobo.app.ui.PinnedBarDensity
import com.cobo.app.ui.Route
import com.cobo.app.ui.pageContainerColor
import com.cobo.app.ui.resolveWeiboLink
import com.cobo.app.ui.components.BackIconButton
import com.cobo.app.ui.components.Avatar
import com.cobo.app.ui.components.EmptyState
import com.cobo.app.ui.components.ErrorState
import com.cobo.app.ui.components.FoldableIntroText
import com.cobo.app.ui.components.LoadingState
import com.cobo.app.ui.components.navBottomPadding
import io.github.suqi8.coui.kmp.basic.BindScrollToTop
import io.github.suqi8.coui.kmp.basic.Button
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.TextField
import io.github.suqi8.coui.kmp.basic.TextFieldMode
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.net.URLEncoder

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

@Composable
fun TopicScreen(nav: NavState, initial: String = "") {
    var query by remember { mutableStateOf(initial) }
    val store = remember { SuperTopicSearchStore() }
    val scope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }

    val followedUid = App.repo.session?.takeUnless { App.repo.guest }?.uid.orEmpty()
    var followed by remember(followedUid) { mutableStateOf<List<FollowedSuperTopic>?>(null) }
    var followedError by remember(followedUid) { mutableStateOf<String?>(null) }
    var officialList by remember(followedUid) { mutableStateOf(followedSuperTopicsWebUrl(followedUid)) }
    var reloadFollowed by remember(followedUid) { mutableStateOf(0) }

    LaunchedEffect(followedUid, reloadFollowed) {
        if (App.repo.guest || App.repo.session == null) return@LaunchedEffect
        followedError = null
        try {
            if (officialList == null) officialList = followedSuperTopicsWebUrl(App.repo.ownUid())
            followed = App.repo.followedSuperTopics()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            followedError = e.message ?: "读取已关注超话失败"
        } catch (_: Exception) {
            followedError = "读取已关注超话失败，可查看官网名单"
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
        val url = "https://m.weibo.cn/search?containerid=" + URLEncoder.encode(cid, "UTF-8")
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
                item { Text("已关注超话", fontSize = 16.sp) }
                when {
                    App.repo.guest || App.repo.session == null -> item { EmptyState("登录微博后查看已关注超话") }
                    followedError != null -> item {
                        ErrorState(followedError!!, onRetry = { reloadFollowed++ })
                    }
                    followed == null -> item { LoadingState() }
                    followed.isNullOrEmpty() -> item { EmptyState("还没有关注超话") }
                }
                items(followed.orEmpty(), key = { "followed-${it.id}" }) { topic ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = CardDefaults.GroupCornerRadius,
                        insideMargin = PaddingValues(0.dp),
                        weight = CardDefaults.GroupWeight,
                    ) {
                        ArrowPreference(
                            title = topic.name,
                            startAction = { Avatar(topic.avatarUrl.orEmpty(), 32.dp) },
                            centerStartAction = true,
                            onClick = { openTopic(TopicItem(topic.name, containerId = topic.id)) },
                        )
                    }
                }
                if (!App.repo.guest && App.repo.session != null) item {
                    if (followed != null && followedError == null) {
                        TextButton(text = "刷新关注列表", onClick = { reloadFollowed++ })
                    }
                    officialList?.let { url ->
                        TextButton(text = "查看官网关注列表", onClick = { nav.push(Route.Web(url, "我关注的超话")) })
                    }
                }
            }
        }
    }
}
