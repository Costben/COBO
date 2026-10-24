package com.cobo.app.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cobo.app.App
import com.cobo.app.data.HotItem
import com.cobo.app.data.Status
import com.cobo.app.ui.NavState
import com.cobo.app.ui.CoboPredictiveBackHandler
import com.cobo.app.ui.pageContainerColor
import com.cobo.app.ui.rememberExpandOrigin
import com.cobo.app.ui.Route
import com.cobo.app.ui.expandOrigin
import com.cobo.app.ui.components.EmptyState
import com.cobo.app.ui.components.ErrorState
import com.cobo.app.ui.components.LoadingState
import com.cobo.app.ui.components.StatusCard
import com.cobo.app.ui.components.navBottomPadding
import io.github.suqi8.coui.kmp.basic.InputField
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.SearchBar
import io.github.suqi8.coui.kmp.basic.SearchBarActionButton
import io.github.suqi8.coui.kmp.basic.SearchHistoryView
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(nav: NavState) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf("") }
    val hot = remember { mutableStateListOf<HotItem>() }
    val results = remember { mutableStateListOf<Status>() }
    var sinceId by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var end by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // The system search page opens in the edit state; SearchBar hides its content while collapsed.
    var fieldFocused by remember { mutableStateOf(true) }
    val expanded = fieldFocused || submitted.isNotBlank()
    val history = App.settings.searchHistory
    val listState = rememberLazyListState()

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

    suspend fun search(keyword: String, reset: Boolean, force: Boolean = true) {
        if (keyword.isBlank()) return
        if (reset) {
            loading = true
            results.clear()
            sinceId = null
            end = false
            submitted = keyword
            error = null
        } else {
            loadingMore = true
        }
        try {
            val nextPage = if (reset) 1 else page
            val pageData = App.repo.search(keyword, nextPage, force = if (reset) force else false)
            val known = results.mapTo(HashSet()) { it.id }
            pageData.items.forEach { if (known.add(it.id)) results.add(it) }
            sinceId = pageData.sinceId
            page = nextPage + 1
            if (pageData.items.isEmpty()) end = true
        } catch (t: Throwable) {
            error = t.message ?: "搜索失败"
        } finally {
            loading = false
            loadingMore = false
        }
    }

    suspend fun runSearch(keyword: String) {
        val query = keyword.trim()
        if (query.isEmpty()) return
        App.settings.pushSearchHistory(query)
        search(query, true, force = true)
    }

    LaunchedEffect(Unit) { loadHot(force = false) }
    LaunchedEffect(listState, results.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { last ->
                if (submitted.isNotBlank() && last >= results.size - 2 && results.isNotEmpty() && !end) {
                    search(submitted, false, force = false)
                }
            }
    }

    Scaffold(containerColor = pageContainerColor()) { padding ->
        val scheme = COUITheme.colorScheme
        SearchBar(
            expanded = expanded,
            onExpandedChange = { isExpanded ->
                // Fallback if SearchBar's own back handler wins: leave the page instead of
                // showing an empty collapsed bar.
                if (isExpanded) fieldFocused = true else nav.pop()
            },
            modifier = Modifier.padding(padding).fillMaxSize(),
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
                        // The IME search action fires onSearch; a hardware or emulated
                        // Enter has to be caught here or it just moves the cursor.
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
            outsideEndAction = {
                SearchBarActionButton(
                    text = "取消",
                    onClick = {
                        query = ""
                        submitted = ""
                        fieldFocused = false
                        nav.pop()
                    },
                )
            },
        ) {
            Column(Modifier.fillMaxSize()) {
                if (submitted.isBlank() && history.isNotEmpty()) {
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
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 8.dp + navBottomPadding()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
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
                        if (results.isEmpty()) {
                            item {
                                when {
                                    loading -> LoadingState()
                                    error != null -> ErrorState(error!!) {
                                        scope.launch { search(submitted, true, force = true) }
                                    }
                                    else -> EmptyState("没有找到相关微博")
                                }
                            }
                        }
                        itemsIndexed(results, key = { _, s -> s.id }) { index, status ->
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
                                            results[index] = status.copy(
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
                        item {
                            when {
                                loadingMore -> LoadingState()
                                end -> Text(
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
    }

    // SearchBar installs its own back handler while expanded. Registering the navigator's
    // handler after it keeps the predictive gesture: the page follows the finger instead of
    // the bar collapsing without an animation.
    key(expanded) { CoboPredictiveBackHandler(nav) }
}
