package com.cobo.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.cobo.app.json.JSONObject
import com.cobo.app.platform.PlatformContext
import com.cobo.app.platform.PlatformFile
import com.cobo.app.platform.SynchronizedMap
import com.cobo.app.platform.appFilesDir
import com.cobo.app.platform.currentTimeMillis

/** 推荐微博本地已看记录的保留时长选项。 */
enum class RecommendSeenRetention(val days: Int, val label: String) {
    Days3(3, "3 天"),
    Days7(7, "7 天"),
    Days14(14, "14 天"),
    Days30(30, "30 天"),
    ;

    val millis: Long get() = days * 24L * 60L * 60L * 1000L

    companion object {
        fun fromDays(days: Int): RecommendSeenRetention =
            entries.firstOrNull { it.days == days } ?: Days7
    }
}

internal const val RecommendSeenMaxEntries = 5000
internal const val RecommendSeenDwellMillis = 750L
internal const val RecommendRefreshMinFreshTarget = 5
internal const val RecommendLoadMoreMinFreshTarget = 3
internal const val RecommendFilterMaxExtraPages = 2

/**
 * 清理过期的本地已看推荐微博 ID，并在超出容量上限时按最早看过时间淘汰旧条目。
 */
internal fun pruneRecommendSeenEntries(
    entries: Map<String, Long>,
    nowMillis: Long,
    retentionMillis: Long,
    maxEntries: Int = RecommendSeenMaxEntries,
): LinkedHashMap<String, Long> {
    if (entries.isEmpty() || maxEntries <= 0 || retentionMillis <= 0L) {
        return LinkedHashMap()
    }
    val valid = entries.entries.filter { (id, seenAt) ->
        id.isNotBlank() && seenAt > 0L && (nowMillis - seenAt) <= retentionMillis
    }
    if (valid.isEmpty()) return LinkedHashMap()
    val kept = if (valid.size <= maxEntries) {
        valid
    } else {
        // 超出上限时优先淘汰最早记录的条目，保留最近看过的微博。
        valid.sortedByDescending { it.value }.take(maxEntries).reversed()
    }
    return LinkedHashMap<String, Long>(kept.size).apply {
        kept.forEach { (id, seenAt) -> put(id, seenAt) }
    }
}

/**
 * 根据当前视口可见卡片集合更新停留计时，返回已满足最短阅读时长且可标记为已看的微博 ID。
 * 快速滑出视口的卡片会清除计时而不记为已看，避免误过滤尚未阅读的内容。
 */
internal fun evaluateDwellSeenIds(
    visibleIds: Set<String>,
    trackedEnterAt: MutableMap<String, Long>,
    nowMillis: Long,
    dwellMillis: Long = RecommendSeenDwellMillis,
): List<String> {
    val ready = LinkedHashSet<String>()
    val iterator = trackedEnterAt.entries.iterator()
    while (iterator.hasNext()) {
        val (id, enterAt) = iterator.next()
        if (id !in visibleIds) {
            iterator.remove()
            if (nowMillis - enterAt >= dwellMillis) {
                ready.add(id)
            }
        }
    }
    for (id in visibleIds) {
        if (id.isBlank()) continue
        val enterAt = trackedEnterAt[id]
        if (enterAt == null) {
            trackedEnterAt[id] = nowMillis
            if (dwellMillis <= 0L) ready.add(id)
        } else if (nowMillis - enterAt >= dwellMillis) {
            ready.add(id)
        }
    }
    return ready.toList()
}

internal data class FilteredRecommendBatch(
    val freshItems: List<Status>,
    val filteredCount: Int,
    val nextCursor: String?,
    val exhausted: Boolean,
    val pagesFetched: Int,
)

/**
 * 拉取并过滤推荐微博分页：按 `status.id` 跳过已看过或已删除条目，
 * 若本页剩余未看微博不足目标数量且上游仍有新游标，最多顺延补拉 [maxExtraPages] 页。
 */
internal suspend fun fetchUnseenRecommendPages(
    initialCursor: String?,
    forceFirst: Boolean,
    existingIds: Set<String>,
    isDeleted: (String) -> Boolean,
    isSeen: (String) -> Boolean,
    minFreshTarget: Int,
    maxExtraPages: Int,
    loader: suspend (cursor: String?, force: Boolean) -> FeedPage,
): FilteredRecommendBatch {
    val seenInBatch = HashSet<String>(existingIds)
    val freshItems = ArrayList<Status>()
    var filteredCount = 0
    var cursor = initialCursor
    var lastCursor: String? = null
    var exhausted = false
    var pagesFetched = 0

    val limitExtra = maxExtraPages.coerceAtLeast(0)
    for (pageIndex in 0..limitExtra) {
        val page = loader(cursor, forceFirst && pageIndex == 0)
        pagesFetched++
        for (status in page.items) {
            val id = status.id
            if (id.isBlank() || isDeleted(id)) continue
            if (!seenInBatch.add(id)) continue
            if (isSeen(id)) {
                filteredCount++
            } else {
                freshItems.add(status)
            }
        }
        val nextCursor = page.sinceId?.takeIf { it.isNotBlank() && it != cursor }
        lastCursor = nextCursor
        if (page.items.isEmpty()) {
            exhausted = true
            break
        }
        if (freshItems.size >= minFreshTarget) {
            break
        }
        if (nextCursor == null) {
            exhausted = true
            break
        }
        cursor = nextCursor
    }

    return FilteredRecommendBatch(
        freshItems = freshItems,
        filteredCount = filteredCount,
        nextCursor = lastCursor,
        exhausted = exhausted,
        pagesFetched = pagesFetched,
    )
}

/**
 * 推荐流已看微博的本机持久化存储：按账号隔离存放于 `recommend-seen/<owner>.json`，
 * 仅记录 `status.id -> seenAtMillis`，支持按保留天数自动清理与一键清空。
 */
class RecommendSeenStore(
    private val root: PlatformFile,
    private val clock: () -> Long = { currentTimeMillis() },
) {
    constructor(@Suppress("UNUSED_PARAMETER") context: PlatformContext = PlatformContext()) :
        this(appFilesDir().child("recommend-seen"))

    private val cache = SynchronizedMap<String, SynchronizedMap<String, Long>>()

    var version by mutableIntStateOf(0)
        private set

    /** 仅在显式清空记录时递增，供前端同步清空内存去重集合。 */
    var clearVersion by mutableIntStateOf(0)
        private set

    init {
        root.mkdirs()
    }

    /** 仅在对应账号缓存已加载进内存时返回有效条数，避免在组合阶段触发主线程同步读盘。 */
    fun countLoadedOrNull(owner: String, retentionMillis: Long): Int? {
        if (retentionMillis <= 0L) return 0
        val cached = cache[ownerKey(owner)] ?: return null
        val now = clock()
        return cached.toMap().count { (id, seenAt) ->
            id.isNotBlank() && seenAt > 0L && (now - seenAt) <= retentionMillis
        }
    }

    fun isSeen(owner: String, statusId: String, retentionMillis: Long): Boolean {
        if (statusId.isBlank() || retentionMillis <= 0L) return false
        val map = ensureLoaded(owner)
        val seenAt = map[statusId] ?: return false
        val now = clock()
        if (seenAt <= 0L || (now - seenAt) > retentionMillis) {
            map.remove(statusId)
            return false
        }
        return true
    }

    fun count(owner: String, retentionMillis: Long): Int {
        if (retentionMillis <= 0L) return 0
        val now = clock()
        val snapshot = ensureLoaded(owner).toMap()
        return snapshot.count { (id, seenAt) ->
            id.isNotBlank() && seenAt > 0L && (now - seenAt) <= retentionMillis
        }
    }

    fun markSeen(owner: String, statusIds: Collection<String>, retentionMillis: Long): Int {
        val count = markSeenInternal(owner, statusIds, retentionMillis)
        if (count > 0) version++
        return count
    }

    fun clear(owner: String) {
        val key = ownerKey(owner)
        cache.remove(key)
        runCatching { fileForKey(key).delete() }
        clearVersion++
        version++
    }

    private fun markSeenInternal(owner: String, statusIds: Collection<String>, retentionMillis: Long): Int {
        if (statusIds.isEmpty() || retentionMillis <= 0L) return 0
        val now = clock()
        val map = ensureLoaded(owner)
        var touched = 0
        for (id in statusIds) {
            if (id.isBlank()) continue
            val previous = map[id]
            if (previous == null || (now - previous) >= ReRecordIntervalMillis) {
                map[id] = now
                touched++
            }
        }
        if (touched == 0) return 0

        val snapshot = map.toMap()
        val pruned = pruneRecommendSeenEntries(snapshot, now, retentionMillis)
        if (pruned.size < snapshot.size) {
            for (key in snapshot.keys) {
                if (key !in pruned) map.remove(key)
            }
        }
        writeOwnerFile(owner, pruned)
        return touched
    }

    private fun ensureLoaded(owner: String): SynchronizedMap<String, Long> {
        val key = ownerKey(owner)
        cache[key]?.let { return it }
        val loaded = SynchronizedMap<String, Long>()
        val file = fileForKey(key)
        if (file.exists()) {
            runCatching {
                val json = JSONObject(file.readTextOrNull().orEmpty())
                if (json.optInt("v") == FormatVersion) {
                    val items = json.optJSONObject("items")
                    if (items != null) {
                        val keys = items.keys()
                        while (keys.hasNext()) {
                            val id = keys.next()
                            val ts = items.optLong(id, 0L)
                            if (id.isNotBlank() && ts > 0L) {
                                loaded[id] = ts
                            }
                        }
                    }
                }
            }
        }
        return cache.putIfAbsent(key, loaded) ?: loaded
    }

    private fun writeOwnerFile(owner: String, entries: Map<String, Long>) {
        val itemsObj = JSONObject()
        entries.forEach { (id, ts) -> itemsObj.put(id, ts) }
        val rootObj = JSONObject()
            .put("v", FormatVersion)
            .put("items", itemsObj)
        runCatching {
            fileForKey(ownerKey(owner)).writeTextAtomic(rootObj.toString())
        }
    }

    private fun ownerKey(owner: String): String =
        owner.ifBlank { "local" }.replace(OwnerKeySanitizeRegex, "_")

    private fun fileForKey(key: String): PlatformFile = root.child("$key.json")

    private companion object {
        const val FormatVersion = 1
        // 避免同一次停留在列表顶部被反复刷新时间戳而频繁写盘。
        const val ReRecordIntervalMillis = 60_000L
        val OwnerKeySanitizeRegex = Regex("[^A-Za-z0-9_-]")
    }
}
