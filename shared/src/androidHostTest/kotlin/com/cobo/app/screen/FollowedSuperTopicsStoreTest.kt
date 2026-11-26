package com.cobo.app.screen

import com.cobo.app.data.ApiException
import com.cobo.app.data.FollowedSuperTopic
import com.cobo.app.data.FollowedSuperTopicPage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FollowedSuperTopicsStoreTest {
    private val id1 = "100808" + "1".repeat(32)
    private val id2 = "100808" + "2".repeat(32)
    private val id3 = "100808" + "3".repeat(32)

    private fun topic(id: String, name: String) = FollowedSuperTopic(id, name, "https://example.test/$name.jpg")

    @Test
    fun paginatesAndDeduplicatesAcrossPages() = runBlocking {
        val requested = mutableListOf<Int>()
        val store = FollowedSuperTopicsStore { page ->
            requested += page
            when (page) {
                1 -> FollowedSuperTopicPage(listOf(topic(id1, "甲")), page = 1, maxPage = 2, hasMore = true)
                else -> FollowedSuperTopicPage(
                    listOf(topic(id1, "重复甲"), topic(id2, "乙")),
                    page = 2,
                    maxPage = 2,
                    hasMore = false,
                )
            }
        }

        store.refresh()
        assertEquals(listOf(1), requested)
        assertEquals(listOf(id1), store.items.map { it.id })
        assertTrue(store.hasLoaded)
        assertTrue(store.hasMore)

        store.loadMore()
        assertEquals(listOf(1, 2), requested)
        assertEquals(listOf(id1, id2), store.items.map { it.id })
        assertEquals(listOf("甲", "乙"), store.items.map { it.name })
        assertFalse(store.hasMore)
        assertNull(store.loadMoreError)
    }

    @Test
    fun failureNeverMarksLoadedAndAuthErrorSetsLoginRequired() = runBlocking {
        val store = FollowedSuperTopicsStore {
            throw ApiException("登录态已失效", "-100")
        }

        store.refresh()
        assertFalse(store.hasLoaded)
        assertTrue(store.items.isEmpty())
        assertTrue(store.loginRequired)
        assertEquals("登录态已失效", store.error)
    }

    @Test
    fun emptyRetryEntersLoadingStateInsteadOfShowingEmpty() = runBlocking {
        var calls = 0
        val secondAttempt = CompletableDeferred<FollowedSuperTopicPage>()
        val store = FollowedSuperTopicsStore {
            calls++
            when (calls) {
                1 -> FollowedSuperTopicPage(emptyList(), page = 1, maxPage = 0, hasMore = false)
                2 -> throw ApiException("网络波动")
                else -> secondAttempt.await()
            }
        }

        store.refresh()
        assertTrue(store.hasLoaded)
        assertTrue(store.items.isEmpty())

        store.refresh()
        assertNotNull(store.error)

        val retryJob = launch { store.refresh() }
        yield()
        assertTrue(store.loading)
        assertNull(store.error)

        secondAttempt.complete(FollowedSuperTopicPage(listOf(topic(id3, "丙")), page = 1, maxPage = 1, hasMore = false))
        retryJob.join()
        assertFalse(store.loading)
        assertEquals(listOf(id3), store.items.map { it.id })
    }

    @Test
    fun loadMoreFailureAndDuplicatePagePreserveLoadedItems() = runBlocking {
        var failPage2 = true
        val store = FollowedSuperTopicsStore { page ->
            when (page) {
                1 -> FollowedSuperTopicPage(listOf(topic(id1, "甲")), page = 1, maxPage = 2, hasMore = true)
                else -> if (failPage2) {
                    throw ApiException("分页超时")
                } else {
                    FollowedSuperTopicPage(listOf(topic(id1, "重复甲")), page = 2, maxPage = 2, hasMore = false)
                }
            }
        }

        store.refresh()
        store.loadMore()
        assertEquals(listOf(id1), store.items.map { it.id })
        assertEquals("分页超时", store.loadMoreError)
        assertTrue(store.hasMore)

        failPage2 = false
        store.loadMore()
        assertEquals(listOf(id1), store.items.map { it.id })
        assertEquals("已关注超话分页重复，请重试", store.loadMoreError)
    }
}
