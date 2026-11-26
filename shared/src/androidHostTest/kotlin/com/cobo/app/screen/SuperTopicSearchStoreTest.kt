package com.cobo.app.screen

import com.cobo.app.data.ApiException
import com.cobo.app.data.TopicItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SuperTopicSearchStoreTest {
    private fun topic(name: String, id: String = "100808fc439dedbb06ca5fd858848e521b8716") =
        TopicItem(name, containerId = id)

    @Test
    fun uniqueExactNameOpensEvenWithOtherPartialMatches() = runBlocking {
        val expected = topic("原神")
        val store = SuperTopicSearchStore { listOf(topic("原神攻略", "other"), expected) }
        assertEquals(expected, store.search(" #原神# "))
        assertFalse(store.loading)
        assertTrue(store.completed)
    }

    @Test
    fun duplicatedNamesAndPartialMatchRequireSelection() = runBlocking {
        val matches = listOf(topic("原神"), topic("原神超话", "other"))
        val store = SuperTopicSearchStore { matches }
        assertNull(store.search("原神"))
        assertEquals(matches, store.results)
        val partial = SuperTopicSearchStore { listOf(topic("原神攻略")) }
        assertNull(partial.search("原神"))
    }

    @Test
    fun acceptsSuperTopicDisplaySuffix() = runBlocking {
        val expected = topic("#原神[超话]#")
        val store = SuperTopicSearchStore { listOf(expected) }
        assertEquals(expected, store.search("原神"))
    }

    @Test
    fun blankInputDoesNotSearchAndEmptyResponseCompletes() = runBlocking {
        var calls = 0
        val store = SuperTopicSearchStore { calls++; emptyList() }
        assertNull(store.search(" # # "))
        assertEquals(0, calls)
        assertNull(store.search("不存在的名称"))
        assertEquals(1, calls)
        assertTrue(store.completed)
        assertTrue(store.results.isEmpty())
        assertNull(store.error)
    }

    @Test
    fun loginErrorIsDistinctAndRetryClearsIt() = runBlocking {
        var failed = true
        val store = SuperTopicSearchStore {
            if (failed) throw ApiException("private response", "432")
            listOf(topic("原神"))
        }
        assertNull(store.search("原神"))
        assertTrue(store.loginRequired)
        assertFalse(store.error!!.contains("private"))
        failed = false
        assertEquals(topic("原神"), store.search("原神"))
        assertFalse(store.loginRequired)
        assertNull(store.error)
    }

    @Test
    fun editingInputInvalidatesOldResultAndItsCompletion() = runBlocking {
        val response = CompletableDeferred<List<TopicItem>>()
        var selected: TopicItem? = null
        val store = SuperTopicSearchStore { response.await() }
        val job = launch { selected = store.search("原神") }
        yield()
        assertTrue(store.loading)
        store.reset()
        response.complete(listOf(topic("原神")))
        job.join()
        assertNull(selected)
        assertFalse(store.completed)
        assertTrue(store.results.isEmpty())
        assertFalse(store.loading)
    }

    @Test
    fun cancellationClearsLoadingWithoutAnError() = runBlocking {
        val store = SuperTopicSearchStore { CompletableDeferred<List<TopicItem>>().await() }
        val job = launch { store.search("原神") }
        yield()
        job.cancelAndJoin()
        assertFalse(store.loading)
        assertNull(store.error)
        assertFalse(store.completed)
    }
}
