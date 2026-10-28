package com.cobo.app.screen

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cobo.app.App
import com.cobo.app.data.FeedPage
import com.cobo.app.data.Status
import com.cobo.app.ui.NavState
import com.cobo.app.ui.LocalTopBarBounds
import com.cobo.app.ui.attachToRow
import com.cobo.app.ui.rememberExpandOrigin
import com.cobo.app.ui.Route
import com.cobo.app.ui.expandOrigin
import com.cobo.app.ui.components.EmptyState
import com.cobo.app.ui.components.ErrorState
import com.cobo.app.ui.components.FullPageRefreshHoldMillis
import com.cobo.app.ui.components.FullPageRefreshTexts
import com.cobo.app.ui.components.LoadingState
import com.cobo.app.ui.components.StatusCard
import com.cobo.app.ui.components.navBottomPadding
import com.cobo.app.ui.pageContainerColor
import io.github.suqi8.coui.kmp.basic.FullPagePullToRefresh
import io.github.suqi8.coui.kmp.basic.FullPagePullToRefreshState
import io.github.suqi8.coui.kmp.basic.fullPagePullToRefreshColors
import io.github.suqi8.coui.kmp.basic.rememberFullPagePullToRefreshState
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class FeedFilter(val label: String, val emptyText: String) {
    All("全部", "这里还没有微博"),
    Following("关注", "这里还没有微博"),
    Original("原创微博", "还没有原创微博"),
    Unread("未读微博", "还没有未读微博"),
    Favorites("收藏", "还没有收藏的微博"),
    Special("特别关注", "特别关注还没有内容"),
    Circle("好友圈", "好友圈还没有内容"),
    Secret("悄悄关注", "还没有悄悄关注的人"),
    Local("本地关注", "还没有本地关注"),
}

class FeedStore(
    private val cid: String,
    /** 只读的流：卡片照常展示，但不给点赞 / 评论 / 转发。 */
    val readOnly: Boolean = false,
    private val loader: suspend (cursor: String?, force: Boolean) -> FeedPage = { cursor, force ->
        App.repo.container(cid, sinceId = cursor, force = force)
    },
) {
    val items = mutableStateListOf<Status>()
    var sinceId by mutableStateOf<String?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var refreshing by mutableStateOf(false)
        private set
    var end by mutableStateOf(false)
        private set
    var loaded by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /**
     * Bumped every time [fetch] swaps the whole list. The screen watches it to put the list back on
     * the first card: a keyed LazyColumn keeps the card that was on top glued to the viewport when
     * it reappears further down, so a refresh started mid-feed would otherwise leave the reader
     * staring at the same card with the new posts hidden above it.
     */
    var generation by mutableIntStateOf(0)
        private set

    // 静默加载与主动刷新分开：只有后者驱动下拉动画，切 tab 触发的首次加载不播。
    private var fetching = false

    /**
     * 所有取数排同一队。
     *
     * 下拉手势在松手那一刻就把刷新头拉下来了，接着调 [refresh]；如果这时恰好有一次加载在跑
     * （切筛选的首次加载、关注/取关触发的自动刷新、上拉翻页），老代码直接 return，刷新头就
     * 永远停在「刷新中」，页面再也不回落。现在改成排队等前一次收尾，再补一次强制刷新。
     */
    private val lock = Mutex()

    suspend fun loadInitial() {
        if (loaded) return
        lock.withLock {
            if (loaded) return@withLock
            fetch(force = false)
        }
    }

    suspend fun refresh() {
        // 已经有一次刷新在跑：它的 finally 会把 refreshing 降下来，这里直接跟它收尾。
        if (refreshing) return
        refreshing = true
        val startedAt = System.currentTimeMillis()
        try {
            lock.withLock { fetch(force = true) }
            // COUI 的刷新头只在观察到 isRefreshing 变化时才收尾。列表为空或命中缓存时取数
            // 瞬间返回，true→false 落在同一帧里，界面根本看不到中间那一跳，头就永远停在
            // 「刷新中」。这里给标志一个最短可见时间，保证至少有一帧读到 true。
            val left = MinRefreshVisibleMillis - (System.currentTimeMillis() - startedAt)
            if (left > 0) delay(left)
        } finally {
            refreshing = false
        }
    }

    private suspend fun fetch(force: Boolean) {
        fetching = true
        error = null
        try {
            val page = loader(null, force)
            val known = HashSet<String>()
            val unique = page.items.filter { known.add(it.id) }
            items.clear()
            items.addAll(unique)
            sinceId = page.sinceId
            end = unique.isEmpty()
            loaded = true
            generation++
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            error = t.message ?: "加载失败"
            if (items.isNotEmpty()) App.toast(error!!)
        } finally {
            fetching = false
        }
    }

    suspend fun loadMore() {
        if (loading || end || refreshing || fetching) return
        val cursor = sinceId ?: return
        loading = true
        try {
            lock.withLock {
                val page = loader(cursor, false)
                val known = items.mapTo(HashSet()) { it.id }
                page.items.forEach { if (known.add(it.id)) items.add(it) }
                sinceId = page.sinceId
                if (page.items.isEmpty()) end = true
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            error = t.message ?: "加载失败"
        } finally {
            loading = false
        }
    }

    suspend fun like(index: Int) {
        if (readOnly) return
        val status = items.getOrNull(index) ?: return
        runCatching { App.repo.like(status.id, status.liked) }
            .onSuccess {
                items[index] = status.copy(
                    liked = !status.liked,
                    attitudes = (status.attitudes + if (status.liked) -1 else 1).coerceAtLeast(0),
                )
            }
            .onFailure { App.toast(it.message ?: "操作失败") }
    }
}

@Composable
fun TimelineScreen(
    nav: NavState,
    pagerState: PagerState,
    /** One store per pager page, in the order of the header segments. */
    stores: List<FeedStore>,
    emptyTexts: List<String>,
    followStore: FeedStore,
    recommendStore: FeedStore,
    topInset: Dp,
    onScrollOffset: (Float) -> Unit,
) {
    // The list states live here so the bottom accessory can send every page back to the top.
    val listStates = remember { List(stores.size) { LazyListState() } }
    val refreshScope = rememberCoroutineScope()
    val pullStates = remember {
        mutableStateListOf<FullPagePullToRefreshState?>().apply { repeat(stores.size) { add(null) } }
    }
    // The header folds its title row away one pixel per pixel scrolled; past the first item the
    // row is already gone, so a large sentinel collapses it fully.
    LaunchedEffect(pagerState, listStates) {
        snapshotFlow {
            val state = listStates[pagerState.currentPage]
            if (state.firstVisibleItemIndex > 0) Float.MAX_VALUE
            else state.firstVisibleItemScrollOffset.toFloat()
        }.collect { onScrollOffset(it) }
    }
    val currentFollowStore by rememberUpdatedState(followStore)
    val currentStores by rememberUpdatedState(stores)
    LaunchedEffect(Unit) {
        App.feedRefreshEvents.collect { currentFollowStore.refresh() }
    }
    LaunchedEffect(Unit) {
        App.feedTopEvents.collect {
            val page = pagerState.currentPage
            val store = currentStores.getOrNull(page) ?: return@collect
            // Smooth ride back to the first card, then the same pull the finger would do, so the
            // page slides down and the list stays put while it reloads.
            listStates[page].glideToTop()
            val pull = pullStates.getOrNull(page)
            if (pull != null) {
                pull.startRefresh { refreshScope.launch { store.refresh() } }
            } else {
                store.refresh()
            }
        }
    }
    LaunchedEffect(Unit) {
        App.followEvents.collect { currentFollowStore.refresh() }
    }
    LaunchedEffect(App.feedVersion) {
        if (App.feedVersion > 0) recommendStore.refresh()
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
    ) { page ->
        TimelinePage(
            nav = nav,
            store = stores[page],
            page = page,
            topInset = topInset,
            emptyText = emptyTexts[page],
            listState = listStates[page],
            onPullState = { pullStates[page] = it },
        )
    }
}

@Composable
private fun TimelinePage(
    nav: NavState,
    store: FeedStore,
    page: Int,
    topInset: Dp,
    emptyText: String,
    listState: LazyListState,
    onPullState: (FullPagePullToRefreshState) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val pullState = rememberFullPagePullToRefreshState()

    LaunchedEffect(pullState) { onPullState(pullState) }

    LaunchedEffect(store) {
        listState.scrollToItem(0)
        if (!store.loaded) store.loadInitial()
    }
    // Every completed load of a fresh page lands the reader on the first card, including a
    // refresh started from the bottom accessory while scrolled deep in the list.
    LaunchedEffect(store.generation) {
        if (store.generation > 0) listState.glideToTop()
    }
    LaunchedEffect(listState, store) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { last ->
                if (last >= store.items.size - 2 && store.items.isNotEmpty()) store.loadMore()
            }
    }

    FullPagePullToRefresh(
        isRefreshing = store.refreshing,
        onRefresh = { scope.launch { store.refresh() } },
        modifier = Modifier.fillMaxSize().testTag("feed_page_$page"),
        state = pullState,
        contentPadding = PaddingValues(top = topInset),
        colors = fullPagePullToRefreshColors(backgroundColor = pageContainerColor()),
        refreshTexts = FullPageRefreshTexts,
        completeHoldDurationMillis = FullPageRefreshHoldMillis,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 12.dp,
                top = topInset + 16.dp,
                end = 12.dp,
                bottom = 66.dp + navBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (store.items.isEmpty()) {
                item {
                    when {
                        store.refreshing -> LoadingState()
                        store.error != null -> ErrorState(store.error!!) {
                            scope.launch { store.refresh() }
                        }
                        store.loaded -> EmptyState(emptyText)
                        else -> LoadingState()
                    }
                }
            }
            itemsIndexed(store.items, key = { _, status -> status.id }) { index, status ->
                val origin = rememberExpandOrigin()
                val topBar = LocalTopBarBounds.current
                // Hand the card's list and bar to the origin so a return can lift the row out from
                // under the bar instead of drawing the card over it.
                SideEffect {
                    origin.listState = listState
                    origin.rowKey = status.id
                    origin.topBar = topBar
                }
                StatusCard(
                    status = status,
                    modifier = Modifier.expandOrigin(origin).testTag("feed_card_$index"),
                    onOpen = { nav.push(Route.Detail(status.id, status.mid, store.readOnly), origin) },
                    onUser = { nav.push(Route.Profile(it.id)) },
                    onImage = { urls, i -> nav.push(Route.Images(urls, i)) },
                    onVideo = { nav.push(Route.Video(it.url, it.title)) },
                    onLike = { scope.launch { store.like(index) } },
                    onRepost = { nav.push(Route.Detail(status.id, status.mid, store.readOnly), origin) },
                    onComment = { nav.push(Route.Detail(status.id, status.mid, store.readOnly), origin) },
                    actionsEnabled = !store.readOnly,
                    onOpenQuoted = { quoted, quotedOrigin ->
                        quotedOrigin.attachToRow(listState, status.id, topBar)
                        nav.push(Route.Detail(quoted.id, quoted.mid, store.readOnly), quotedOrigin)
                    },
                )
            }
            if (store.items.isNotEmpty()) {
                item {
                    if (store.loading) {
                        LoadingState()
                    } else if (store.end) {
                        Text(
                            text = "没有更多了",
                            color = COUITheme.colorScheme.onSurfaceSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * `LazyListState.animateScrollToItem` teleports when the target is more than one item away, so a
 * "back to top" from deep in the feed looks like a cut. This drives the scroll frame by frame
 * instead, at a speed chosen so the whole trip takes roughly [GlideSeconds].
 */
private suspend fun LazyListState.glideToTop() {
    if (firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0) return

    // Item heights are only known once measured; the visible ones give a good average for the
    // off-screen stretch above the viewport.
    val visible = layoutInfo.visibleItemsInfo
    val averageHeight = visible.map { it.size }.average().toFloat().takeIf { it > 0f } ?: 800f
    val estimated = firstVisibleItemIndex * averageHeight + firstVisibleItemScrollOffset
    val speed = (estimated / GlideSeconds).coerceIn(MinGlideSpeed, MaxGlideSpeed)

    val started = withFrameNanos { it }
    var last = started
    while (true) {
        val now = withFrameNanos { it }
        val elapsed = (now - started) / 1_000_000_000f
        val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
        last = now

        val rampIn = (elapsed / 0.08f).coerceIn(0f, 1f)
        // Only the last few hundred pixels ease out; a long tail would make the trip feel slow.
        val settle = if (firstVisibleItemIndex == 0) {
            (0.55f + 0.45f * (firstVisibleItemScrollOffset / 300f)).coerceIn(0.55f, 1f)
        } else {
            1f
        }
        scrollBy(-speed * rampIn * settle * dt)

        if (firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0) return
        if (elapsed > GlideSeconds * 2f) {
            scrollToItem(0)
            return
        }
    }
}

/** 刷新头最短可见时间：空列表/命中缓存时也要让界面看到一次 `refreshing = true`。 */
private const val MinRefreshVisibleMillis = 320L

private const val GlideSeconds = 0.3f
// Short trips still get a visible ride: the floor is low enough that a one-card hop takes about
// GlideSeconds instead of finishing inside a couple of frames.
private const val MinGlideSpeed = 1_200f
private const val MaxGlideSpeed = 28_000f
