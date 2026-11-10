package com.cobo.app.screen

import com.cobo.app.data.Status
import com.cobo.app.data.SuperTopicInfo
import com.cobo.app.data.SuperTopicPage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuperTopicStoreTest {
    private val id = "100808e1f868bf9980f09ab6908787d7eaf0f0"
    private val info = SuperTopicInfo(id, "示例超话", "简介")

    private fun page(ids: List<String>, next: String?) = SuperTopicPage(
        info,
        ids.map { Status(it, it, "正文", "", "", null) },
        next,
    )

    @Test
    fun continuousPagesKeepIdentityAndDropDuplicatePosts() = runBlocking {
        val requests = mutableListOf<String?>()
        val store = SuperTopicStore(id) { cursor ->
            requests.add(cursor)
            when (cursor) {
                null -> page(listOf("1", "2"), "a")
                "a" -> page(listOf("2", "3"), "b")
                else -> page(listOf("4"), null)
            }
        }
        store.feed.loadInitial()
        store.feed.loadMore()
        store.feed.loadMore()
        store.feed.loadMore()
        assertEquals(listOf(null, "a", "b"), requests)
        assertEquals(listOf("1", "2", "3", "4"), store.feed.items.map { it.id })
        assertEquals(info, store.info)
        assertTrue(store.feed.readOnly)
    }

    @Test
    fun cyclingCursorShowsErrorWithoutClaimingTheFeedEnded() = runBlocking {
        val requests = mutableListOf<String?>()
        val store = SuperTopicStore(id) { cursor ->
            requests.add(cursor)
            when (cursor) {
                null -> page(listOf("1"), "a")
                "a" -> page(listOf("2"), "b")
                else -> page(listOf("3"), "a")
            }
        }
        store.feed.loadInitial()
        repeat(2) { store.feed.loadMore() }
        assertEquals(listOf(null, "a", "b"), requests)
        assertEquals("b", store.feed.sinceId)
        assertTrue(store.feed.error!!.contains("游标重复"))
        assertFalse(store.feed.end)
    }

    @Test
    fun failedNextPageKeepsCursorAndRetryClearsError() = runBlocking {
        var fail = true
        val store = SuperTopicStore(id) { cursor ->
            if (cursor == null) page(listOf("1"), "a")
            else if (fail) throw IllegalStateException("网络错误")
            else page(listOf("2"), null)
        }
        store.feed.loadInitial()
        store.feed.loadMore()
        assertEquals("a", store.feed.sinceId)
        assertEquals(listOf("1"), store.feed.items.map { it.id })
        assertEquals("网络错误", store.feed.error)
        fail = false
        store.feed.loadMore()
        assertEquals(listOf("1", "2"), store.feed.items.map { it.id })
        assertEquals(null, store.feed.error)
        assertFalse(store.feed.loading)
    }

    @Test
    fun refreshWaitsForPaginationThenReplacesPostsAndCursorHistory() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        var refreshes = 0
        var paging = false
        val store = SuperTopicStore(id) { cursor ->
            if (cursor == null) {
                refreshes++
                page(listOf("fresh-$refreshes"), "a")
            } else {
                paging = true
                gate.await()
                page(listOf("old"), null)
            }
        }
        store.feed.loadInitial()
        val more = launch { store.feed.loadMore() }
        while (!paging) yield()
        val refresh = launch { store.feed.refresh() }
        while (!store.feed.refreshing) yield()
        gate.complete(Unit)
        more.join()
        refresh.join()
        assertEquals(listOf("fresh-2"), store.feed.items.map { it.id })
        assertEquals("a", store.feed.sinceId)
        assertFalse(store.feed.refreshing)
    }

    @Test
    fun cancellationReleasesLoadingFlags() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val store = SuperTopicStore(id) { gate.await(); page(emptyList(), null) }
        val job = launch { store.feed.refresh() }
        while (!store.feed.refreshing) yield()
        job.cancel()
        job.join()
        assertFalse(store.feed.refreshing)
        assertFalse(store.feed.loading)
        assertEquals(null, store.feed.error)
    }
}
