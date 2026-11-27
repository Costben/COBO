package com.cobo.app.screen

import com.cobo.app.platform.currentTimeMillis
import com.cobo.app.platform.ioDispatcher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cobo.app.App
import com.cobo.app.FeedTopAction
import com.cobo.app.data.ContentFilters
import com.cobo.app.data.DetailInitialSection
import com.cobo.app.data.FeedGap
import com.cobo.app.data.FeedPage
import com.cobo.app.data.FilterSurface
import com.cobo.app.data.RecommendFilterMaxExtraPages
import com.cobo.app.data.RecommendLoadMoreMinFreshTarget
import com.cobo.app.data.RecommendRefreshMinFreshTarget
import com.cobo.app.data.RecommendSeenDwellMillis
import com.cobo.app.data.Status
import com.cobo.app.data.evaluateDwellSeenIds
import com.cobo.app.data.fetchUnseenRecommendPages
import com.cobo.app.data.shouldFilterStatus
import com.cobo.app.ui.NavState
import com.cobo.app.ui.LocalBottomBarBounds
import com.cobo.app.ui.LocalTopBarBounds
import com.cobo.app.ui.FeedTitleRowHeight
import com.cobo.app.ui.attachToRow
import com.cobo.app.ui.rememberExpandOrigin
import com.cobo.app.ui.Route
import com.cobo.app.ui.expandOrigin
import com.cobo.app.ui.components.EmptyState
import com.cobo.app.ui.components.CoboRefreshSpinner
import com.cobo.app.ui.components.ErrorState
import com.cobo.app.ui.components.FullPageRefreshHoldMillis
import com.cobo.app.ui.components.FullPageRefreshTexts
import com.cobo.app.ui.components.LoadingState
import com.cobo.app.ui.components.StatusCard
import com.cobo.app.ui.components.navBottomPadding
import com.cobo.app.ui.pageContainerColor
import io.github.suqi8.coui.kmp.basic.BindScrollToTop
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.FullPagePullToRefresh
import io.github.suqi8.coui.kmp.basic.FullPagePullToRefreshState
import io.github.suqi8.coui.kmp.basic.fullPagePullToRefreshColors
import io.github.suqi8.coui.kmp.basic.rememberFullPagePullToRefreshState
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

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

internal sealed interface FeedEntry {
    val key: String

    data class Post(val status: Status, val statusIndex: Int) : FeedEntry {
        override val key: String get() = status.id
    }

    data class Gap(val gap: FeedGap) : FeedEntry {
        override val key: String get() = gapEntryKey(gap.id)
    }

    data class ReadMarker(val beforeStatusId: String) : FeedEntry {
        override val key: String get() = readMarkerEntryKey(beforeStatusId)
    }
}

private fun gapEntryKey(gapId: String): String = "gap:$gapId"

internal fun readMarkerEntryKey(beforeStatusId: String): String = "read_marker:$beforeStatusId"

internal data class PrependAnchor(val key: String, val scrollOffset: Int)

internal sealed interface GapAnchor {
    data class TopItem(val key: String, val scrollOffset: Int) : GapAnchor
    data class ViewportItem(val key: String, val itemOffset: Int) : GapAnchor
}

internal data class PendingGapRestore(val targetIndex: Int, val itemOffset: Int)

internal interface ScrollAnchorController {
    fun captureForPrepend(): PrependAnchor?
    fun restoreAfterPrepend(anchor: PrependAnchor?)
    fun captureForGap(gap: FeedGap): GapAnchor?
    fun restoreAfterGap(anchor: GapAnchor?, beforeStatusId: String, gapId: String)
}

class FeedStore(
    /** 这条流的标识：默认取数容器用它，快照与滚动位置也按它分文件。 */
    val key: String,
    /** 只读的流：卡片照常展示，但不给点赞 / 评论 / 转发。 */
    val readOnly: Boolean = false,
    /**
     * 把已经拉到的列表和游标存盘，冷启动时接着上次看到的地方继续。
     * 只给首页那几栏开：别的地方进来应该是一条新流，不该沿用上次的列表。
     */
    val resumable: Boolean = false,
    /** 时间排序流才支持「倒序浏览」（刷新时在原位置上方插新微博与断点）。 */
    val supportsReverseBrowse: Boolean = false,
    /** 是否追踪并持久化信息流曝光记录（仅推荐流开启）。 */
    val tracksSeen: Boolean = false,
    private val reverseBrowseEnabled: () -> Boolean = { App.isReverseBrowseEnabled() },
    private val snapshotOwner: () -> String = { App.repo.session?.uid.orEmpty() },
    private val isStatusDeleted: (String) -> Boolean = { id -> App.isRepoReady() && App.repo.isDeleted(id) },
    private val filterSeenEnabled: () -> Boolean = { false },
    private val isStatusSeen: (owner: String, id: String) -> Boolean = { _, _ -> false },
    private val onMarkSeen: ((owner: String, ids: Collection<String>) -> Unit)? = null,
    private val onAllSeenFiltered: ((filteredCount: Int) -> Unit)? = null,
    private val loader: suspend (cursor: String?, force: Boolean) -> FeedPage = { cursor, force ->
        App.repo.container(key, sinceId = cursor, force = force)
    },
) {
    val items = mutableStateListOf<Status>()
    val gaps = mutableStateListOf<FeedGap>()
    var lastReadBeforeStatusId by mutableStateOf<String?>(null)
        private set
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

    internal var scrollAnchorController: ScrollAnchorController? = null
    internal var pendingGapRestore: PendingGapRestore? = null
    internal var gapRestoreGeneration by mutableIntStateOf(0)
        private set

    private var nextGapSeq = 0L

    /** 这一份列表是从上次的快照填回来的，不是这次新拉的。 */
    var resumed by mutableStateOf(false)
        private set

    /** 快照恢复后要落到的位置，由界面取走一次。 */
    private var pendingScroll: Pair<Int, Int>? = null

    fun takePendingScroll(): Pair<Int, Int>? = pendingScroll.also { pendingScroll = null }

    internal fun takePendingGapRestore(): PendingGapRestore? =
        pendingGapRestore.also { pendingGapRestore = null }

    internal fun requestGapRestore(targetIndex: Int, itemOffset: Int) {
        pendingGapRestore = PendingGapRestore(targetIndex, itemOffset)
        gapRestoreGeneration++
    }

    internal fun entries(): List<FeedEntry> = buildList(items.size + gaps.size + 1) {
        val gapByBefore = gaps.associateBy { it.beforeStatusId }
        val markerBefore = lastReadBeforeStatusId
        items.forEachIndexed { index, status ->
            val gap = gapByBefore[status.id]
            if (gap != null) {
                add(FeedEntry.Gap(gap))
            } else if (index > 0 && status.id == markerBefore) {
                add(FeedEntry.ReadMarker(status.id))
            }
            add(FeedEntry.Post(status, index))
        }
    }

    internal fun entryKeys(): List<String> = buildList(items.size + gaps.size + 1) {
        val gapByBefore = gaps.associateBy { it.beforeStatusId }
        val markerBefore = lastReadBeforeStatusId
        items.forEachIndexed { index, status ->
            val gap = gapByBefore[status.id]
            if (gap != null) {
                add(gapEntryKey(gap.id))
            } else if (index > 0 && status.id == markerBefore) {
                add(readMarkerEntryKey(status.id))
            }
            add(status.id)
        }
    }

    internal fun entryCount(): Int {
        val markerBefore = lastReadBeforeStatusId
        val hasMarker = markerBefore != null &&
            gaps.none { it.beforeStatusId == markerBefore } &&
            items.indexOfFirst { it.id == markerBefore } > 0
        return items.size + gaps.size + if (hasMarker) 1 else 0
    }

    internal fun owner(): String = snapshotOwner()

    private fun isReverseBrowse(): Boolean = supportsReverseBrowse && reverseBrowseEnabled()

    private fun nextGapId(): String = "${currentTimeMillis()}_${++nextGapSeq}"

    // 静默加载与主动刷新分开：只有后者驱动下拉动画，切 tab 触发的首次加载不播。
    private var fetching = false
    internal var lastAppliedLikeSeq: Long = if (App.isRepoReady()) App.repo.currentLikeSeq() else 0L

    /**
     * 所有取数排同一队。
     *
     * 下拉手势在松手那一刻就把刷新头拉下来了，接着调 [refresh]；如果这时恰好有一次加载在跑
     * （切筛选的首次加载、关注/取关触发的自动刷新、上拉翻页），老代码直接 return，刷新头就
     * 永远停在「刷新中」，页面再也不回落。现在改成排队等前一次收尾，再补一次强制刷新。
     */
    private val lock = Mutex()
    private val seenWriteScope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val seenLock = Mutex()

    internal fun isSeenFilterActive(): Boolean = filterSeenEnabled()

    internal suspend fun markSeenBatch(ids: Collection<String>) {
        val handler = onMarkSeen ?: return
        val distinct = ids.asSequence().filter { it.isNotBlank() }.distinct().toList()
        if (distinct.isEmpty()) return
        val ownerId = snapshotOwner()
        seenLock.withLock {
            withContext(ioDispatcher) {
                handler(ownerId, distinct)
            }
        }
    }

    internal fun markSeenDetached(ids: Collection<String>) {
        if (onMarkSeen == null || ids.isEmpty()) return
        val batch = ids.toList()
        seenWriteScope.launch { markSeenBatch(batch) }
    }

    suspend fun loadInitial() {
        if (loaded) return
        lock.withLock {
            if (loaded) return@withLock
            if (resumable) resume()
            if (!loaded) fetch(force = false)
        }
    }

    /**
     * 冷启动把上一次的列表填回来：翻过几页、游标在哪、有没有到底，全都跟关掉之前一样，
     * 不再重发请求；位置由 [App.settings] 里记的那一条补上。
     */
    private suspend fun resume() {
        val ownerId = snapshotOwner()
        val snapshot = withContext(ioDispatcher) {
            runCatching { App.snapshots.load(ownerId, key) }.getOrNull()
        } ?: return
        if (snapshot.items.isEmpty()) return
        items.clear()
        items.addAll(snapshot.items)
        gaps.clear()
        gaps.addAll(snapshot.gaps)
        lastReadBeforeStatusId = snapshot.lastReadBeforeStatusId
        sinceId = snapshot.sinceId
        end = snapshot.end
        loaded = true
        resumed = true
        pendingScroll = App.settings.feedScroll(ownerId, key)
    }

    private suspend fun persist() {
        if (!resumable || items.isEmpty()) return
        val ownerId = snapshotOwner()
        val snapshotItems = items.toList()
        val snapshotGaps = gaps.toList()
        val snapshotMarker = lastReadBeforeStatusId
        val cursor = sinceId
        val atEnd = end
        withContext(ioDispatcher) {
            runCatching {
                App.snapshots.save(
                    ownerId,
                    key,
                    snapshotItems,
                    cursor,
                    atEnd,
                    snapshotGaps,
                    snapshotMarker,
                )
            }
        }
    }

    fun removeDeleted(deletedIds: Map<String, Boolean>) {
        if (deletedIds.isEmpty()) return
        val removedIds = items.asSequence()
            .filter { it.id in deletedIds || (it.mid.isNotBlank() && it.mid in deletedIds) }
            .mapTo(HashSet()) { it.id }
        if (removedIds.isEmpty()) return
        val marker = lastReadBeforeStatusId
        if (marker != null && marker in removedIds) {
            val markerIdx = items.indexOfFirst { it.id == marker }
            lastReadBeforeStatusId = if (markerIdx >= 0) {
                items.subList(markerIdx + 1, items.size)
                    .firstOrNull { it.id !in removedIds }
                    ?.id
            } else {
                null
            }
        }
        items.removeAll { it.id in removedIds }
        gaps.removeAll { it.beforeStatusId in removedIds }
    }

    suspend fun refresh() {
        // 已经有一次刷新在跑：它的 finally 会把 refreshing 降下来，这里直接跟它收尾。
        if (refreshing) return
        refreshing = true
        val startedAt = currentTimeMillis()
        try {
            lock.withLock { fetch(force = true) }
            // COUI 的刷新头只在观察到 isRefreshing 变化时才收尾。列表为空或命中缓存时取数
            // 瞬间返回，true→false 落在同一帧里，界面根本看不到中间那一跳，头就永远停在
            // 「刷新中」。这里给标志一个最短可见时间，保证至少有一帧读到 true。
            val left = MinRefreshVisibleMillis - (currentTimeMillis() - startedAt)
            if (left > 0) delay(left)
        } finally {
            refreshing = false
        }
    }

    private suspend fun fetch(force: Boolean) {
        fetching = true
        error = null
        try {
            if (isReverseBrowse() && items.isNotEmpty()) {
                fetchReverse(force)
                return
            }
            val previousHeadId = items.firstOrNull()?.id
            if (filterSeenEnabled()) {
                val ownerId = snapshotOwner()
                val batch = fetchUnseenRecommendPages(
                    initialCursor = null,
                    forceFirst = force,
                    existingIds = emptySet(),
                    isDeleted = isStatusDeleted,
                    isSeen = { id -> isStatusSeen(ownerId, id) },
                    minFreshTarget = RecommendRefreshMinFreshTarget,
                    maxExtraPages = RecommendFilterMaxExtraPages,
                    loader = loader,
                )
                if (batch.freshItems.isEmpty() && batch.filteredCount > 0) {
                    if (items.isNotEmpty()) {
                        // 候选池暂全部命中已看：保留原有推荐列表与位置不动，避免整屏刷新成空白。
                        onAllSeenFiltered?.invoke(batch.filteredCount)
                        return
                    }
                    sinceId = batch.nextCursor
                    end = batch.exhausted && batch.nextCursor == null
                    lastAppliedLikeSeq = if (App.isRepoReady()) App.repo.currentLikeSeq() else 0L
                    loaded = true
                    resumed = false
                    persist()
                    onAllSeenFiltered?.invoke(batch.filteredCount)
                    return
                }
                gaps.clear()
                items.clear()
                items.addAll(batch.freshItems)
                lastReadBeforeStatusId = if (
                    previousHeadId != null && batch.freshItems.indexOfFirst { it.id == previousHeadId } > 0
                ) {
                    previousHeadId
                } else {
                    null
                }
                sinceId = batch.nextCursor
                end = batch.freshItems.isEmpty() || (batch.exhausted && batch.nextCursor == null)
                lastAppliedLikeSeq = if (App.isRepoReady()) App.repo.currentLikeSeq() else 0L
                loaded = true
                // 这次是真的拉回来了，列表不再代表「上次的快照」，后面的刷新该照常回顶。
                resumed = false
                generation++
                persist()
                return
            }
            val page = loader(null, force)
            val known = HashSet<String>()
            val unique = page.items.filter { !isStatusDeleted(it.id) && known.add(it.id) }
            gaps.clear()
            items.clear()
            items.addAll(unique)
            lastReadBeforeStatusId = if (
                previousHeadId != null && unique.indexOfFirst { it.id == previousHeadId } > 0
            ) {
                previousHeadId
            } else {
                null
            }
            sinceId = page.sinceId
            end = unique.isEmpty()
            lastAppliedLikeSeq = if (App.isRepoReady()) App.repo.currentLikeSeq() else 0L
            loaded = true
            // 这次是真的拉回来了，列表不再代表「上次的快照」，后面的刷新该照常回顶。
            resumed = false
            generation++
            persist()
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            error = t.message ?: "加载失败"
            if (items.isNotEmpty()) App.toast(error!!)
        } finally {
            fetching = false
        }
    }

    /**
     * 倒序浏览模式下的增量刷新：把最新拉到的微博插在当前列表上方并锚定原来的可视卡片。
     * 若新微博跨越多页导致与旧头部暂时脱节，自动补拉最多 [AutoBridgeMaxExtraPages] 页；
     * 仍未接上时在交界处插入 [FeedGap]，供用户在需要时继续补拉中间未读微博。
     */
    private suspend fun fetchReverse(force: Boolean) {
        val firstPage = loader(null, force)
        val firstUnique = firstPage.items.distinctBy { it.id }
        if (firstUnique.isEmpty()) {
            lastReadBeforeStatusId = null
            loaded = true
            resumed = false
            persist()
            return
        }

        val existingIds = items.mapTo(HashSet()) { it.id }
        val firstGapIndex = gaps.firstOrNull()
            ?.let { gap -> items.indexOfFirst { it.id == gap.beforeStatusId } }
            ?.takeIf { it >= 0 }
            ?: items.size
        val topBlockHeadIds = items.subList(0, firstGapIndex)
            .take(HeadOverlapWindow)
            .mapTo(HashSet()) { it.id }

        fun overlapsHead(pageItems: List<Status>): Boolean {
            if (pageItems.isEmpty()) return false
            if (pageItems.size == 1) return pageItems[0].id in topBlockHeadIds
            // 首条可能是服务端置顶/推广插队，跳过首条再判衔接更稳妥。
            return pageItems.drop(1).any { it.id in topBlockHeadIds }
        }

        val collected = ArrayList<Status>()
        val seenNew = HashSet<String>(existingIds)
        for (s in firstUnique) {
            if (seenNew.add(s.id)) collected.add(s)
        }
        if (collected.isEmpty()) {
            lastReadBeforeStatusId = null
            loaded = true
            resumed = false
            persist()
            return
        }

        var bridged = overlapsHead(firstUnique)
        var gapCursor = firstPage.sinceId?.takeIf { it.isNotBlank() }
        var extraPages = 0
        while (!bridged && gapCursor != null && extraPages < AutoBridgeMaxExtraPages) {
            extraPages++
            val nextPage = runCatching { loader(gapCursor, false) }.getOrNull() ?: break
            val nextUnique = nextPage.items.distinctBy { it.id }
            if (nextUnique.isEmpty()) {
                gapCursor = null
                break
            }
            for (s in nextUnique) {
                if (seenNew.add(s.id)) collected.add(s)
            }
            if (overlapsHead(nextUnique)) {
                bridged = true
            }
            val nextCursor = nextPage.sinceId?.takeIf { it.isNotBlank() }
            if (nextCursor == gapCursor) {
                gapCursor = null
                break
            }
            gapCursor = nextCursor
        }

        val anchor = scrollAnchorController?.captureForPrepend()
        val oldHeadId = items.first().id
        items.addAll(0, collected)
        lastReadBeforeStatusId = oldHeadId
        if (!bridged && gapCursor != null) {
            gaps.add(0, FeedGap(id = nextGapId(), cursor = gapCursor, beforeStatusId = oldHeadId))
        }
        loaded = true
        resumed = false
        scrollAnchorController?.restoreAfterPrepend(anchor)
        persist()
    }

    /** 点击中间断点卡片继续补拉未读区间，与下面已有微博接上后自动消除断点。 */
    suspend fun loadGap(gapId: String) {
        val currentIdx = gaps.indexOfFirst { it.id == gapId }
        if (currentIdx < 0) return
        val currentGap = gaps[currentIdx]
        if (currentGap.loading) return
        gaps[currentIdx] = currentGap.copy(loading = true, error = null)
        try {
            lock.withLock {
                val lockedIdx = gaps.indexOfFirst { it.id == gapId }
                val gap = gaps.getOrNull(lockedIdx) ?: return@withLock
                val page = loader(gap.cursor, false)
                val anchorIndex = items.indexOfFirst { it.id == gap.beforeStatusId }
                if (anchorIndex < 0) {
                    gaps.removeAt(lockedIdx)
                    persist()
                    return@withLock
                }

                val belowIds = items.subList(anchorIndex, items.size)
                    .take(HeadOverlapWindow)
                    .mapTo(HashSet()) { it.id }
                val hitIdx = page.items.indexOfFirst { it.id in belowIds }
                val slice = if (hitIdx >= 0) page.items.take(hitIdx) else page.items
                val existingIds = items.mapTo(HashSet()) { it.id }
                val newItems = slice.distinctBy { it.id }.filter { it.id !in existingIds }
                val nextCursor = page.sinceId?.takeIf { it.isNotBlank() }
                val exhausted = hitIdx >= 0 || nextCursor == null || nextCursor == gap.cursor

                val anchor = scrollAnchorController?.captureForGap(gap)
                if (newItems.isNotEmpty()) {
                    items.addAll(anchorIndex, newItems)
                }
                if (exhausted) {
                    gaps.removeAt(lockedIdx)
                } else {
                    gaps[lockedIdx] = gap.copy(
                        cursor = nextCursor,
                        loading = false,
                        error = null,
                        loadedCount = gap.loadedCount + newItems.size,
                    )
                }
                scrollAnchorController?.restoreAfterGap(anchor, gap.beforeStatusId, gapId)
                persist()
            }
        } catch (c: CancellationException) {
            val idx = gaps.indexOfFirst { it.id == gapId }
            if (idx >= 0) gaps[idx] = gaps[idx].copy(loading = false)
            throw c
        } catch (t: Throwable) {
            val idx = gaps.indexOfFirst { it.id == gapId }
            if (idx >= 0) {
                gaps[idx] = gaps[idx].copy(loading = false, error = t.message ?: "加载失败")
            }
        }
    }

    suspend fun loadMore() {
        if (loading || end || refreshing || fetching) return
        val cursor = sinceId ?: return
        loading = true
        error = null
        try {
            lock.withLock {
                if (filterSeenEnabled()) {
                    val ownerId = snapshotOwner()
                    val batch = fetchUnseenRecommendPages(
                        initialCursor = cursor,
                        forceFirst = false,
                        existingIds = items.mapTo(HashSet()) { it.id },
                        isDeleted = isStatusDeleted,
                        isSeen = { id -> isStatusSeen(ownerId, id) },
                        minFreshTarget = RecommendLoadMoreMinFreshTarget,
                        maxExtraPages = RecommendFilterMaxExtraPages,
                        loader = loader,
                    )
                    if (batch.freshItems.isNotEmpty()) {
                        items.addAll(batch.freshItems)
                        sinceId = batch.nextCursor
                        if (batch.exhausted && batch.nextCursor == null) end = true
                        persist()
                    } else {
                        // 即使本批均被过滤也要推进游标，防止停留在旧游标死循环。
                        sinceId = batch.nextCursor
                        if (batch.nextCursor == null || batch.exhausted) end = true
                        persist()
                        if (batch.filteredCount > 0) {
                            onAllSeenFiltered?.invoke(batch.filteredCount)
                        }
                    }
                    return@withLock
                }
                val page = loader(cursor, false)
                val known = items.mapTo(HashSet()) { it.id }
                page.items.forEach { if (known.add(it.id)) items.add(it) }
                sinceId = page.sinceId
                if (page.items.isEmpty()) end = true
                persist()
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
        val updated = App.toggleStatusLike(status) ?: return
        val currentIdx = items.indexOfFirst { it.id == status.id }
        if (currentIdx >= 0) {
            items[currentIdx] = updated
        }
        lastAppliedLikeSeq = if (App.isRepoReady()) App.repo.currentLikeSeq() else 0L
    }

    internal fun syncLikedStatuses(events: Map<String, com.cobo.app.data.Repo.StatusLikeEvent>) {
        if (events.isEmpty()) return
        val maxSeq = applyLikeEventsToList(items, events, lastAppliedLikeSeq)
        if (maxSeq > lastAppliedLikeSeq) lastAppliedLikeSeq = maxSeq
    }
}

/**
 * 每一页的位置记忆。左右滑会把整页拆掉重装，滚动位置本身记在 `LazyListState` 里；这里只记
 * 这一格上一次交给的是哪个流，以及已经为哪一次刷新回到过顶部。
 */
internal data class FeedScrollAnchor(val index: Int, val offset: Int)

internal class PageMemory {
    var store: FeedStore? = null
    var generation = -1
    /** 这一格是不是已经装载过一次；第一次装载不算刷新，不该把人拉回顶部。 */
    var seen = false
    /** 单击底栏「微博」回顶前记录的浏览位置，再点一次时返回；手动滚动或刷新后清空。 */
    var returnAnchor: FeedScrollAnchor? = null
}

/**
 * 首页信息流各栏的滚动位置与页面记忆，提升到主壳里持有，切去「消息」「我」再切回主页时
 * 保留原来的浏览位置，不再重新建在顶部。
 */
@Stable
class TimelineScrollHolder {
    private val listStates = HashMap<String, LazyListState>()
    private val pageMemories = HashMap<String, PageMemory>()

    internal fun listStateFor(slotKey: String): LazyListState =
        listStates.getOrPut(slotKey) { LazyListState() }

    internal fun memoryFor(slotKey: String): PageMemory =
        pageMemories.getOrPut(slotKey) { PageMemory() }
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
    slotKeys: List<String> = stores.indices.map { it.toString() },
    scrollHolder: TimelineScrollHolder = remember { TimelineScrollHolder() },
) {
    // The list states live in scrollHolder so bottom-tab switches keep each page's position.
    val listStates = stores.indices.map { index ->
        scrollHolder.listStateFor(slotKeys.getOrElse(index) { index.toString() })
    }
    val refreshScope = rememberCoroutineScope()
    val pullStates = remember(stores.size) {
        mutableStateListOf<FullPagePullToRefreshState?>().apply { repeat(stores.size) { add(null) } }
    }
    val pageMemory = stores.indices.map { index ->
        scrollHolder.memoryFor(slotKeys.getOrElse(index) { index.toString() })
    }
    // The header folds its title row away one pixel per pixel scrolled; past the first item the
    // row is already gone, so a large sentinel collapses it fully.
    // 左右那几页跟着当前页一起走，但只补第一行收进去的那一段：滑到多深，两边也只停在收满一行
    // 的位置。翻页前两边就已经在各自该在的地方，滑过去不会跳，也就用不着动画。回到顶部时
    // 两边一起回去。
    val foldPx = with(LocalDensity.current) { FeedTitleRowHeight.toPx() }
    LaunchedEffect(pagerState, listStates, foldPx) {
        snapshotFlow {
            val current = pagerState.currentPage
            val state = listStates.getOrNull(current)
            val offset = when {
                state == null -> 0f
                state.firstVisibleItemIndex > 0 -> Float.MAX_VALUE
                else -> state.firstVisibleItemScrollOffset.toFloat()
            }
            current to offset
        }.collect { (current, offset) ->
            val synced = offset.coerceAtMost(foldPx).roundToInt()
            listStates.forEachIndexed { index, state ->
                if (index == current) return@forEachIndexed
                // 只动还停在顶部那一段的页；已经滚到深处的那几页有自己的位置，不能碰。
                if (state.firstVisibleItemIndex != 0 || state.firstVisibleItemScrollOffset > foldPx) {
                    return@forEachIndexed
                }
                if (state.firstVisibleItemScrollOffset != synced) state.requestScrollToItem(0, synced)
            }
            onScrollOffset(offset)
        }
    }
    val currentFollowStore by rememberUpdatedState(followStore)
    val currentStores by rememberUpdatedState(stores)
    DisposableEffect(stores, listStates, pagerState.currentPage) {
        val bindings = ArrayList<Pair<FeedStore, ScrollAnchorController>>()
        fun attach(index: Int) {
            val store = stores.getOrNull(index) ?: return
            val listState = listStates.getOrNull(index) ?: return
            val controller = object : ScrollAnchorController {
                override fun captureForPrepend(): PrependAnchor? {
                    if (store.items.isEmpty()) return null
                    val keys = store.entryKeys()
                    val idx = listState.firstVisibleItemIndex.coerceIn(0, (keys.size - 1).coerceAtLeast(0))
                    val key = keys.getOrNull(idx) ?: return null
                    return PrependAnchor(key, listState.firstVisibleItemScrollOffset)
                }

                override fun restoreAfterPrepend(anchor: PrependAnchor?) {
                    if (anchor == null) return
                    val newIndex = store.entryKeys().indexOf(anchor.key)
                    if (newIndex >= 0) {
                        listState.requestScrollToItem(newIndex, anchor.scrollOffset)
                    }
                }

                override fun captureForGap(gap: FeedGap): GapAnchor? {
                    val layout = listState.layoutInfo
                    val visible = layout.visibleItemsInfo
                    val gapKey = gapEntryKey(gap.id)
                    val gapInfo = visible.firstOrNull { it.key == gapKey }
                    val viewportH = layout.viewportSize.height
                    if (gapInfo != null && viewportH > 0 && gapInfo.offset > viewportH * 0.55f) {
                        val keys = store.entryKeys()
                        val firstKey = keys.getOrNull(listState.firstVisibleItemIndex) ?: return null
                        return GapAnchor.TopItem(firstKey, listState.firstVisibleItemScrollOffset)
                    }
                    val beforeInfo = visible.firstOrNull { it.key == gap.beforeStatusId }
                    if (beforeInfo != null) {
                        return GapAnchor.ViewportItem(gap.beforeStatusId, beforeInfo.offset)
                    }
                    if (gapInfo != null) {
                        return GapAnchor.ViewportItem(gapKey, gapInfo.offset)
                    }
                    val keys = store.entryKeys()
                    val firstKey = keys.getOrNull(listState.firstVisibleItemIndex) ?: return null
                    return GapAnchor.TopItem(firstKey, listState.firstVisibleItemScrollOffset)
                }

                override fun restoreAfterGap(
                    anchor: GapAnchor?,
                    beforeStatusId: String,
                    gapId: String,
                ) {
                    if (anchor == null) return
                    val newKeys = store.entryKeys()
                    when (anchor) {
                        is GapAnchor.TopItem -> {
                            val newIndex = newKeys.indexOf(anchor.key)
                            if (newIndex >= 0) {
                                listState.requestScrollToItem(newIndex, anchor.scrollOffset)
                            }
                        }
                        is GapAnchor.ViewportItem -> {
                            val targetKey = anchor.key.takeIf { it in newKeys } ?: beforeStatusId
                            val targetIndex = newKeys.indexOf(targetKey)
                            if (targetIndex >= 0) {
                                if (anchor.itemOffset <= 0) {
                                    listState.requestScrollToItem(targetIndex, -anchor.itemOffset)
                                } else {
                                    store.requestGapRestore(targetIndex, anchor.itemOffset)
                                }
                            }
                        }
                    }
                }
            }
            store.scrollAnchorController = controller
            bindings.add(store to controller)
        }
        val activePage = pagerState.currentPage
        stores.indices.forEach { idx -> if (idx != activePage) attach(idx) }
        attach(activePage)
        onDispose {
            for ((store, controller) in bindings) {
                if (store.scrollAnchorController === controller) {
                    store.scrollAnchorController = null
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        App.feedRefreshEvents.collect { currentFollowStore.refresh() }
    }
    var topActionJob by remember { mutableStateOf<Job?>(null) }
    var programmaticScrollDepth by remember { mutableIntStateOf(0) }
    LaunchedEffect(pagerState, listStates, pageMemory) {
        snapshotFlow {
            val current = pagerState.currentPage
            val state = listStates.getOrNull(current)
            if (state == null) {
                Triple(current, false, true)
            } else {
                Triple(
                    current,
                    state.isScrollInProgress,
                    isFeedAtTop(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset),
                )
            }
        }.collect { (current, scrolling, atTop) ->
            if (scrolling && programmaticScrollDepth == 0 && !atTop) {
                pageMemory.getOrNull(current)?.returnAnchor = null
            }
        }
    }
    LaunchedEffect(Unit) {
        suspend fun runProgrammaticScroll(block: suspend () -> Unit) {
            programmaticScrollDepth++
            try {
                block()
            } finally {
                programmaticScrollDepth = (programmaticScrollDepth - 1).coerceAtLeast(0)
            }
        }
        App.feedTopEvents.collect { action ->
            val running = topActionJob?.isActive == true
            if (running && (action == FeedTopAction.ToggleTopOrReturn || action == FeedTopAction.LegacyDoubleTap)) {
                // 回顶或返回原位动画进行中时忽略重入点击，避免事件缓冲并在完成后误触发下一步或刷新。
                return@collect
            }
            if (running) {
                topActionJob?.cancel()
            }
            val page = pagerState.currentPage
            val store = currentStores.getOrNull(page) ?: return@collect
            val listState = listStates.getOrNull(page)
            val memory = pageMemory.getOrNull(page)
            val reverse = App.isReverseBrowseEnabled() && store.supportsReverseBrowse
            val atTop = listState == null ||
                isFeedAtTop(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
            val effect = resolveFeedTopEffect(
                action = action,
                atTop = atTop,
                hasSavedAnchor = memory?.returnAnchor != null,
                reverseBrowse = reverse,
            )
            if (effect == FeedTopEffect.NoOp) return@collect
            topActionJob = refreshScope.launch {
                when (effect) {
                    FeedTopEffect.NoOp -> Unit
                    FeedTopEffect.GlideToTopOnly -> {
                        runProgrammaticScroll { listState?.glideToTop() }
                    }
                    FeedTopEffect.GlideToTopAndSaveAnchor -> {
                        if (listState != null && memory != null) {
                            memory.returnAnchor = FeedScrollAnchor(
                                index = listState.firstVisibleItemIndex,
                                offset = listState.firstVisibleItemScrollOffset,
                            )
                        }
                        runProgrammaticScroll { listState?.glideToTop() }
                    }
                    FeedTopEffect.ReturnToSavedAnchor -> {
                        val target = memory?.returnAnchor
                        memory?.returnAnchor = null
                        if (target != null && listState != null && store.entryCount() > 0) {
                            val clampedIndex = target.index.coerceIn(0, store.entryCount() - 1)
                            runProgrammaticScroll {
                                listState.animateScrollToItem(clampedIndex, target.offset.coerceAtLeast(0))
                            }
                        }
                    }
                    FeedTopEffect.RefreshInPlace -> {
                        memory?.returnAnchor = null
                        if (!store.refreshing) {
                            val pull = pullStates.getOrNull(page)
                            if (pull != null) {
                                pull.startRefresh { refreshScope.launch { store.refresh() } }
                            } else {
                                store.refresh()
                            }
                        }
                    }
                    FeedTopEffect.GlideToTopThenRefresh -> {
                        memory?.returnAnchor = null
                        runProgrammaticScroll { listState?.glideToTop() }
                        if (!store.refreshing) {
                            val pull = pullStates.getOrNull(page)
                            if (pull != null) {
                                pull.startRefresh { refreshScope.launch { store.refresh() } }
                            } else {
                                store.refresh()
                            }
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        App.followEvents.collect { currentFollowStore.refresh() }
    }
    var lastFeedVersion by remember { mutableIntStateOf(App.feedVersion) }
    LaunchedEffect(App.feedVersion) {
        if (App.feedVersion > lastFeedVersion) {
            lastFeedVersion = App.feedVersion
            recommendStore.refresh()
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
    ) { page ->
        val store = stores.getOrNull(page) ?: return@HorizontalPager
        val emptyText = emptyTexts.getOrNull(page).orEmpty()
        val listState = listStates.getOrNull(page) ?: return@HorizontalPager
        BindScrollToTop(listState, enabled = page == pagerState.settledPage)
        TimelinePage(
            nav = nav,
            store = store,
            page = page,
            topInset = topInset,
            emptyText = emptyText,
            listState = listState,
            memory = pageMemory.getOrNull(page) ?: return@HorizontalPager,
            onPullState = { if (page < pullStates.size) pullStates[page] = it },
            isActivePage = page == pagerState.settledPage,
        )
    }
}

@Composable
fun SyncDeletedStatuses(store: FeedStore) {
    val version = App.repo.deletedVersion
    LaunchedEffect(store, version) {
        if (App.repo.deletedStatusIds.isNotEmpty()) {
            store.removeDeleted(App.repo.deletedStatusIds)
        }
    }
    SyncLikedStatuses(store)
}

@Composable
fun SyncDeletedStatuses(items: SnapshotStateList<Status>) {
    val version = App.repo.deletedVersion
    LaunchedEffect(items, version) {
        val deleted = App.repo.deletedStatusIds
        if (deleted.isNotEmpty()) {
            items.removeAll { it.id in deleted || (it.mid.isNotBlank() && it.mid in deleted) }
        }
    }
    SyncLikedStatuses(items)
}

@Composable
fun SyncLikedStatuses(store: FeedStore) {
    val version = App.repo.likeVersion
    LaunchedEffect(store, version) {
        store.syncLikedStatuses(App.repo.statusLikeEvents)
    }
}

@Composable
fun SyncLikedStatuses(vararg lists: SnapshotStateList<Status>) {
    val version = App.repo.likeVersion
    var appliedSeq by remember(*lists) { mutableLongStateOf(App.repo.currentLikeSeq()) }
    LaunchedEffect(*lists, version) {
        val events = App.repo.statusLikeEvents
        if (events.isEmpty()) return@LaunchedEffect
        var maxSeq = appliedSeq
        for (items in lists) {
            maxSeq = maxOf(maxSeq, applyLikeEventsToList(items, events, appliedSeq))
        }
        if (maxSeq > appliedSeq) appliedSeq = maxSeq
    }
}

private fun applyLikeEventsToList(
    items: MutableList<Status>,
    events: Map<String, com.cobo.app.data.Repo.StatusLikeEvent>,
    sinceSeq: Long,
): Long {
    var maxSeq = sinceSeq
    for (idx in items.indices) {
        val current = items[idx]
        val event = events[current.id]?.takeIf { it.seq > sinceSeq }
        val retweet = current.retweeted
        val retweetEvent = retweet?.let { rt -> events[rt.id]?.takeIf { it.seq > sinceSeq } }
        if (event == null && retweetEvent == null) continue
        if (event != null && event.seq > maxSeq) maxSeq = event.seq
        if (retweetEvent != null && retweetEvent.seq > maxSeq) maxSeq = retweetEvent.seq
        val nextRetweeted = if (retweetEvent != null) {
            retweet.copy(
                liked = retweetEvent.liked,
                attitudes = retweetEvent.attitudes,
            )
        } else {
            retweet
        }
        items[idx] = current.copy(
            liked = event?.liked ?: current.liked,
            attitudes = event?.attitudes ?: current.attitudes,
            retweeted = nextRetweeted,
        )
    }
    return maxOf(maxSeq, events.values.maxOfOrNull { it.seq } ?: maxSeq)
}

@Composable
private fun TimelinePage(
    nav: NavState,
    store: FeedStore,
    page: Int,
    topInset: Dp,
    emptyText: String,
    listState: LazyListState,
    memory: PageMemory,
    onPullState: (FullPagePullToRefreshState) -> Unit,
    isActivePage: Boolean = true,
) {
    val scope = rememberCoroutineScope()
    val pullState = rememberFullPagePullToRefreshState()

    LaunchedEffect(pullState) { onPullState(pullState) }

    LaunchedEffect(store) {
        val previous = memory.store
        memory.store = store
        // 滑走会把整页拆掉，滑回来是重新组合；位置记在 LazyListState 里，只有这一格换成了别的
        // 流（换筛选）才该从头来。
        if (previous !== store) {
            memory.generation = store.generation
            memory.seen = store.loaded
            memory.returnAnchor = null
            if (previous != null) listState.scrollToItem(0)
        }
        if (!store.loaded) store.loadInitial()
    }
    // Every completed load of a fresh page lands the reader on the first card, including a
    // refresh started from the bottom accessory while scrolled deep in the list. 滑回来时
    // generation 没变，位置要留着，不能跟着再回一次顶。
    LaunchedEffect(store.generation) {
        if (store.generation > memory.generation) {
            memory.generation = store.generation
            memory.returnAnchor = null
            // 第一次装载不算刷新：这一页要么本来就停在第一条，要么刚被顶到模糊的下沿，
            // 两种位置都不该再被拉回顶部。
            if (memory.seen) listState.glideToTop()
            memory.seen = true
        }
    }
    SyncDeletedStatuses(store)
    LaunchedEffect(store.gapRestoreGeneration) {
        val restore = store.takePendingGapRestore() ?: return@LaunchedEffect
        val total = store.entryCount()
        if (restore.targetIndex in 0 until total) {
            listState.scrollToItem(restore.targetIndex, 0)
            listState.scrollBy(-restore.itemOffset.toFloat())
        }
    }
    LaunchedEffect(listState, store) {
        snapshotFlow {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: -1) to info.totalItemsCount
        }.collect { (last, total) ->
            if (total > 0 && last >= total - 2 && store.items.isNotEmpty()) store.loadMore()
        }
    }
    // 快照恢复之后落回上次停下的那张卡片。只落一次：之后的位置归 LazyListState 自己管。
    // 落位之前不能开始记位置：那一刻列表还是空的，(0,0) 会把存档里的位置直接盖掉。
    var scrollReady by remember(store) { mutableStateOf(false) }
    LaunchedEffect(store, store.loaded) {
        if (!store.loaded) return@LaunchedEffect
        // 从快照填回来的列表算「已经看过」：下一次刷新要回到顶部，不能把人留在旧位置上。
        if (store.resumed) memory.seen = true
        val target = store.takePendingScroll()
        if (target != null) {
            val last = store.entryCount() - 1
            if (last >= 0) listState.scrollToItem(target.first.coerceIn(0, last), target.second)
        }
        scrollReady = true
    }
    // 停在哪要记住：进程被杀或切换账号后再回来，快照填好就直接落到这里。滚动中不写，停稳一会儿再写，
    // 离开页面或切换账号时立刻补写一次，避免刚停下来就切号丢失位置。
    val scrollOwner = remember(store) { store.owner() }
    val seenOwner = store.owner()
    val seenClearVersion = if (App.isRecommendSeenReady()) App.recommendSeen.clearVersion else 0
    val seenTrackingActive = store.tracksSeen && store.isSeenFilterActive() && isActivePage
    val trackedEnterAt = remember(store, seenOwner, seenClearVersion) { HashMap<String, Long>() }
    val recordedSeenIds = remember(store, seenOwner, seenClearVersion) { HashSet<String>() }
    val pendingSeenFlush = remember(store, seenOwner, seenClearVersion) { LinkedHashSet<String>() }
    val currentPostIds by remember(store) {
        derivedStateOf { store.items.mapTo(HashSet()) { it.id } }
    }
    LaunchedEffect(listState, store, seenOwner, seenClearVersion, seenTrackingActive) {
        if (!seenTrackingActive) {
            trackedEnterAt.clear()
            return@LaunchedEffect
        }
        snapshotFlow {
            val postIds = currentPostIds
            listState.layoutInfo.visibleItemsInfo
                .mapNotNull { info -> (info.key as? String)?.takeIf { it in postIds } }
                .toSet()
        }.collectLatest { visibleIds ->
            val now = currentTimeMillis()
            val newlyReady = evaluateDwellSeenIds(visibleIds, trackedEnterAt, now, RecommendSeenDwellMillis)
                .filter { recordedSeenIds.add(it) }
            pendingSeenFlush.addAll(newlyReady)
            if (visibleIds.isNotEmpty()) {
                delay(RecommendSeenDwellMillis)
                val afterDelay = evaluateDwellSeenIds(
                    visibleIds,
                    trackedEnterAt,
                    currentTimeMillis(),
                    RecommendSeenDwellMillis,
                ).filter { recordedSeenIds.add(it) }
                pendingSeenFlush.addAll(afterDelay)
            }
            if (pendingSeenFlush.isNotEmpty()) {
                val batch = pendingSeenFlush.toList()
                pendingSeenFlush.clear()
                store.markSeenBatch(batch)
            }
        }
    }
    DisposableEffect(store, seenOwner, seenClearVersion, seenTrackingActive) {
        onDispose {
            // 直接使用进入本 Effect 时捕获的 seenTrackingActive（旧值），
            // 避免切走推荐页时被 rememberUpdatedState 提前改写为 false 而跳过补写。
            val now = currentTimeMillis()
            if (seenTrackingActive && trackedEnterAt.isNotEmpty()) {
                val flushedOnExit = evaluateDwellSeenIds(emptySet(), trackedEnterAt, now, RecommendSeenDwellMillis)
                    .filter { recordedSeenIds.add(it) }
                pendingSeenFlush.addAll(flushedOnExit)
            }
            if (pendingSeenFlush.isNotEmpty()) {
                val batch = pendingSeenFlush.toList()
                pendingSeenFlush.clear()
                store.markSeenDetached(batch)
            }
        }
    }
    LaunchedEffect(listState, store, scrollReady, scrollOwner) {
        if (!store.resumable || !scrollReady) return@LaunchedEffect
        snapshotFlow {
            Triple(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
                listState.isScrollInProgress,
            )
        }.collectLatest { (index, offset, scrolling) ->
            if (scrolling) return@collectLatest
            delay(ScrollSaveDelayMillis)
            App.settings.applyFeedScroll(scrollOwner, store.key, index, offset)
        }
    }
    DisposableEffect(listState, store, scrollReady, scrollOwner) {
        onDispose {
            if (store.resumable && scrollReady) {
                App.settings.applyFeedScroll(
                    scrollOwner,
                    store.key,
                    listState.firstVisibleItemIndex,
                    listState.firstVisibleItemScrollOffset,
                )
            }
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
        val feedCardFrame = App.settings.feedCardFrame
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().scrollEndHaptic().overScrollVertical(),
            contentPadding = PaddingValues(
                start = 12.dp,
                top = topInset + 16.dp,
                end = 12.dp,
                bottom = 96.dp + navBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(if (feedCardFrame) 8.dp else 0.dp),
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
            val filterRules = ContentFilters.rules
            val selfUid = App.repo.session?.uid
            val rawEntries = store.entries()
            val entries = rawEntries.filter { entry ->
                entry !is FeedEntry.Post ||
                    !shouldFilterStatus(entry.status, filterRules, FilterSurface.Feed, selfUid)
            }
            if (rawEntries.any { it is FeedEntry.Post } && entries.none { it is FeedEntry.Post }) {
                item(key = "filtered_empty") { EmptyState("当前页微博均已被屏蔽规则隐藏") }
            }
            itemsIndexed(
                items = entries,
                key = { _, entry -> entry.key },
                contentType = { _, entry ->
                    when (entry) {
                        is FeedEntry.Gap -> "gap"
                        is FeedEntry.ReadMarker -> "read_marker"
                        is FeedEntry.Post -> "post"
                    }
                },
            ) { entryIndex, entry ->
                when (entry) {
                    is FeedEntry.Gap -> {
                        FeedGapCard(
                            gap = entry.gap,
                            modifier = if (feedCardFrame) Modifier else Modifier.padding(vertical = 6.dp),
                            onClick = { scope.launch { store.loadGap(entry.gap.id) } },
                        )
                    }
                    is FeedEntry.ReadMarker -> {
                        FeedReadMarker(
                            keepFrame = feedCardFrame,
                            refreshing = store.refreshing,
                            onClick = { scope.launch { store.refresh() } },
                        )
                    }
                    is FeedEntry.Post -> {
                        val status = entry.status
                        val index = entry.statusIndex
                        val origin = rememberExpandOrigin()
                        val topBar = LocalTopBarBounds.current
                        val bottomBar = LocalBottomBarBounds.current
                        val showDivider = !feedCardFrame && entries.drop(entryIndex + 1).firstOrNull {
                            it !is FeedEntry.Post || !App.repo.isDeleted(it.status.id)
                        } is FeedEntry.Post
                        // Hand the card's list and bars to the origin so a return can lift the row out from
                        // under either the top bar or the floating bottom bar instead of drawing over them.
                        SideEffect {
                            origin.listState = listState
                            origin.rowKey = status.id
                            origin.originKey = "card:${status.id}"
                            origin.topBar = topBar
                            origin.bottomBar = bottomBar
                        }
                        val recordInteractionSeen: () -> Unit = {
                            if (store.tracksSeen && store.isSeenFilterActive() && recordedSeenIds.add(status.id)) {
                                store.markSeenDetached(listOf(status.id))
                            }
                        }
                        StatusCard(
                            status = status,
                            modifier = Modifier.expandOrigin(origin).testTag("feed_card_$index"),
                            onOpen = {
                                recordInteractionSeen()
                                nav.push(Route.Detail(status.id, status.mid, store.readOnly), origin)
                            },
                            onUser = { nav.push(Route.Profile(it.id)) },
                            onImage = { pics, i ->
                                recordInteractionSeen()
                                nav.push(Route.Images(pics, i))
                            },
                            onVideo = {
                                recordInteractionSeen()
                                nav.push(Route.Video(it.url, it.title, it.isLive, it.fid, it.statusId))
                            },
                            onLike = {
                                recordInteractionSeen()
                                scope.launch { store.like(index) }
                            },
                            onRepost = {
                                recordInteractionSeen()
                                nav.push(
                                    Route.Detail(status.id, status.mid, store.readOnly, DetailInitialSection.Reposts),
                                    origin,
                                )
                            },
                            onComment = {
                                recordInteractionSeen()
                                nav.push(
                                    Route.Detail(status.id, status.mid, store.readOnly, DetailInitialSection.Comments),
                                    origin,
                                )
                            },
                            actionsEnabled = !store.readOnly,
                            onOpenQuoted = { quoted, quotedOrigin ->
                                recordInteractionSeen()
                                quotedOrigin.attachToRow(listState, status.id, topBar, bottomBar)
                                nav.push(Route.Detail(quoted.id, quoted.mid, store.readOnly), quotedOrigin)
                            },
                            keepFrame = feedCardFrame,
                            showBottomDivider = showDivider,
                        )
                    }
                }
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

@Composable
private fun FeedGapCard(
    gap: FeedGap,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth().testTag("feed_gap_${gap.id}"),
        cornerRadius = CardDefaults.GroupCornerRadius,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        weight = CardDefaults.GroupWeight,
        onClick = if (gap.loading) null else onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (gap.loading) {
                CoboRefreshSpinner(size = 16.dp, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "正在加载中间未读微博…",
                    color = scheme.onSurfaceSecondary,
                    fontSize = 13.sp,
                )
            } else if (gap.error != null) {
                Text(
                    text = "加载失败（${gap.error}），点击重试",
                    color = scheme.error,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            } else {
                val label = if (gap.loadedCount > 0) {
                    "已补拉 ${gap.loadedCount} 条 · 点击继续加载未读微博"
                } else {
                    "点击加载中间未读微博"
                }
                Text(
                    text = label,
                    color = scheme.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/**
 * 「上次阅读到这里，点击刷新」分割标记：
 * - 关闭首页卡片外框时：复刻 Share 的直线分割样式（左右横线 + 居中文字，无外框）。
 * - 开启首页卡片外框时：套一层单行 Card 外框，仅居中显示文字。
 */
@Composable
private fun FeedReadMarker(
    keepFrame: Boolean,
    refreshing: Boolean,
    onClick: () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    val markerModifier = Modifier.fillMaxWidth().testTag("feed_read_marker")
    if (keepFrame) {
        Card(
            modifier = markerModifier,
            cornerRadius = CardDefaults.GroupCornerRadius,
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            weight = CardDefaults.GroupWeight,
            onClick = if (refreshing) null else onClick,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (refreshing) {
                    CoboRefreshSpinner(size = 16.dp, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "正在刷新…",
                        color = scheme.onSurfaceSecondary,
                        fontSize = 13.sp,
                    )
                } else {
                    Text(
                        text = "上次阅读到这里，点击刷新",
                        color = scheme.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    } else {
        val lineColor = scheme.primary.copy(alpha = 0.65f)
        Row(
            modifier = markerModifier
                .clickable(enabled = !refreshing, onClick = onClick)
                .padding(horizontal = 4.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f).height(1.dp).background(lineColor))
            Spacer(Modifier.width(12.dp))
            if (refreshing) {
                CoboRefreshSpinner(size = 14.dp, strokeWidth = 1.8.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "正在刷新…",
                    color = scheme.onSurfaceSecondary,
                    fontSize = 13.sp,
                )
            } else {
                Text(
                    text = "上次阅读到这里，点击刷新",
                    color = scheme.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f).height(1.dp).background(lineColor))
        }
    }
}

/** 刷新头最短可见时间：空列表/命中缓存时也要让界面看到一次 `refreshing = true`。 */
private const val MinRefreshVisibleMillis = 320L

/** 列表停稳多久才记位置：一路划过去只留最后一处。 */
private const val ScrollSaveDelayMillis = 260L

/** 倒序浏览刷新遇断层时自动顺着游标补拉的最多额外页数，避免一直转圈。 */
private const val AutoBridgeMaxExtraPages = 2

/** 判断新页是否与旧块接上的头部样本窗口大小（容忍前几条偶发被删）。 */
private const val HeadOverlapWindow = 15

internal const val FeedAtTopTolerancePx = 2

internal fun isFeedAtTop(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
): Boolean = firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset <= FeedAtTopTolerancePx

internal enum class FeedTopEffect {
    NoOp,
    GlideToTopOnly,
    GlideToTopAndSaveAnchor,
    ReturnToSavedAnchor,
    GlideToTopThenRefresh,
    RefreshInPlace,
}

internal fun resolveFeedTopEffect(
    action: FeedTopAction,
    atTop: Boolean,
    hasSavedAnchor: Boolean,
    reverseBrowse: Boolean,
): FeedTopEffect = when (action) {
    FeedTopAction.ToggleTopOrReturn -> when {
        !atTop -> FeedTopEffect.GlideToTopAndSaveAnchor
        hasSavedAnchor -> FeedTopEffect.ReturnToSavedAnchor
        else -> FeedTopEffect.NoOp
    }
    FeedTopAction.RefreshFromTab -> {
        if (reverseBrowse) FeedTopEffect.RefreshInPlace else FeedTopEffect.GlideToTopThenRefresh
    }
    FeedTopAction.LegacyDoubleTap -> when {
        reverseBrowse && !atTop -> FeedTopEffect.GlideToTopOnly
        reverseBrowse -> FeedTopEffect.RefreshInPlace
        else -> FeedTopEffect.GlideToTopThenRefresh
    }
    FeedTopAction.RefreshFromAccessory -> {
        if (reverseBrowse) FeedTopEffect.RefreshInPlace else FeedTopEffect.GlideToTopThenRefresh
    }
}

private const val GlideSeconds = 0.3f
// Short trips still get a visible ride: the floor is low enough that a one-card hop takes about
// GlideSeconds instead of finishing inside a couple of frames.
private const val MinGlideSpeed = 1_200f
private const val MaxGlideSpeed = 28_000f
