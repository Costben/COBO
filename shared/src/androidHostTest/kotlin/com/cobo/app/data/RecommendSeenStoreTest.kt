package com.cobo.app.data

import com.cobo.app.platform.PlatformFile
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class RecommendSeenStoreTest {

    private lateinit var dir: File
    private var nowMillis = 1_700_000_000_000L
    private lateinit var store: RecommendSeenStore

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("recommend-seen-test").toFile()
        nowMillis = 1_700_000_000_000L
        store = RecommendSeenStore(PlatformFile(dir.path), clock = { nowMillis })
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun retentionEnumMapsKnownDaysAndFallsBackToSevenDays() {
        assertEquals(RecommendSeenRetention.Days3, RecommendSeenRetention.fromDays(3))
        assertEquals(RecommendSeenRetention.Days7, RecommendSeenRetention.fromDays(7))
        assertEquals(RecommendSeenRetention.Days14, RecommendSeenRetention.fromDays(14))
        assertEquals(RecommendSeenRetention.Days30, RecommendSeenRetention.fromDays(30))
        assertEquals(RecommendSeenRetention.Days7, RecommendSeenRetention.fromDays(99))
    }

    @Test
    fun pruneDropsExpiredInvalidAndOldestOverLimit() {
        val retention = RecommendSeenRetention.Days7.millis
        val input = linkedMapOf(
            "" to (nowMillis - 1000L),
            "bad_time" to 0L,
            "expired" to (nowMillis - retention - 1L),
            "oldest_valid" to (nowMillis - 3000L),
            "middle_valid" to (nowMillis - 2000L),
            "newest_valid" to (nowMillis - 1000L),
        )

        val pruned = pruneRecommendSeenEntries(
            entries = input,
            nowMillis = nowMillis,
            retentionMillis = retention,
            maxEntries = 2,
        )

        assertEquals(listOf("middle_valid", "newest_valid"), pruned.keys.toList())
    }

    @Test
    fun evaluateDwellRequiresMinimumStayBeforeMarkingSeen() {
        val tracked = HashMap<String, Long>()
        // 初次进入视口只记录起始时间
        assertTrue(evaluateDwellSeenIds(setOf("a", "b"), tracked, nowMillis = 1000L, dwellMillis = 750L).isEmpty())

        // 未满 750ms 就离开视口的 "a" 不应被标为已看
        val at500 = evaluateDwellSeenIds(setOf("b"), tracked, nowMillis = 1500L, dwellMillis = 750L)
        assertTrue(at500.isEmpty())
        assertFalse("a" in tracked)

        // "b" 在视口停留满 750ms 后被标记；此时刚进入的 "c" 暂不标记
        val at1800 = evaluateDwellSeenIds(setOf("b", "c"), tracked, nowMillis = 1800L, dwellMillis = 750L)
        assertEquals(listOf("b"), at1800)

        // "c" 在离开视口前已停留满 750ms；若上层结合已记录集合去重，此处新增的仅是 "c"
        val at2600 = evaluateDwellSeenIds(emptySet(), tracked, nowMillis = 2600L, dwellMillis = 750L)
        assertEquals(setOf("b", "c"), at2600.toSet())
        assertEquals(listOf("c"), at2600.filter { it !in at1800 })
        assertTrue(tracked.isEmpty())
    }

    @Test
    fun storePersistsIsolatesByAccountAndClears() {
        val retention = RecommendSeenRetention.Days7.millis
        store.markSeen("u1", listOf("1001", "1002"), retention)
        store.markSeen("u2", listOf("2001"), retention)

        val reloaded = RecommendSeenStore(PlatformFile(dir.path), clock = { nowMillis })
        assertTrue(reloaded.isSeen("u1", "1001", retention))
        assertTrue(reloaded.isSeen("u1", "1002", retention))
        assertFalse(reloaded.isSeen("u1", "2001", retention))
        assertEquals(2, reloaded.count("u1", retention))
        assertEquals(1, reloaded.count("u2", retention))

        // 超过保留期后自动失效
        nowMillis += retention + 10L
        assertFalse(reloaded.isSeen("u1", "1001", retention))
        assertEquals(0, reloaded.count("u1", retention))

        // 清理账号记录
        reloaded.markSeen("u1", listOf("1003"), retention)
        assertEquals(1, reloaded.count("u1", retention))
        reloaded.clear("u1")
        assertEquals(0, reloaded.count("u1", retention))
        assertFalse(reloaded.isSeen("u1", "1003", retention))
    }

    @Test
    fun fetchUnseenRecommendPagesBackfillsWithinMaxExtraPagesLimit() = runBlocking {
        val seen = setOf("1", "2", "3", "4")
        val requestedCursors = mutableListOf<String?>()
        val pages = mapOf(
            null to FeedPage(listOf(status("1"), status("2"), status("10")), sinceId = "c1"),
            "c1" to FeedPage(listOf(status("3"), status("11"), status("12")), sinceId = "c2"),
            "c2" to FeedPage(listOf(status("4"), status("13"), status("14"), status("15")), sinceId = "c3"),
            "c3" to FeedPage(listOf(status("16")), sinceId = "c4"),
        )

        // 目标 5 条，最多补拉 2 页（共 3 页），在 c2 凑齐 5 条（10..14）后停在 3 页内且返回 c3 游标
        val batch = fetchUnseenRecommendPages(
            initialCursor = null,
            forceFirst = true,
            existingIds = emptySet(),
            isDeleted = { false },
            isSeen = { it in seen },
            minFreshTarget = 5,
            maxExtraPages = 2,
        ) { cursor, _ ->
            requestedCursors.add(cursor)
            pages.getValue(cursor)
        }

        assertEquals(listOf(null, "c1", "c2"), requestedCursors)
        assertEquals(listOf("10", "11", "12", "13", "14", "15"), batch.freshItems.map { it.id })
        assertEquals(4, batch.filteredCount)
        assertEquals("c3", batch.nextCursor)
        assertEquals(3, batch.pagesFetched)
        assertFalse(batch.exhausted)
    }

    @Test
    fun fetchUnseenRecommendPagesStopsAfterMaxPagesWhenAllSeen() = runBlocking {
        val seen = setOf("1", "2", "3", "4", "5", "6")
        val requestedCursors = mutableListOf<String?>()
        val pages = mapOf(
            null to FeedPage(listOf(status("1"), status("2")), sinceId = "c1"),
            "c1" to FeedPage(listOf(status("3"), status("4")), sinceId = "c2"),
            "c2" to FeedPage(listOf(status("5"), status("6")), sinceId = "c3"),
            "c3" to FeedPage(listOf(status("7")), sinceId = "c4"),
        )

        val batch = fetchUnseenRecommendPages(
            initialCursor = null,
            forceFirst = true,
            existingIds = emptySet(),
            isDeleted = { false },
            isSeen = { it in seen },
            minFreshTarget = 5,
            maxExtraPages = 2,
        ) { cursor, _ ->
            requestedCursors.add(cursor)
            pages.getValue(cursor)
        }

        assertEquals(listOf(null, "c1", "c2"), requestedCursors)
        assertTrue(batch.freshItems.isEmpty())
        assertEquals(6, batch.filteredCount)
        assertEquals("c3", batch.nextCursor)
        assertFalse(batch.exhausted)
    }

    private fun status(id: String) = Status(
        id = id,
        mid = id,
        textHtml = "微博 $id",
        createdAt = "",
        source = "",
        user = null,
    )
}
