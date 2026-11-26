package com.cobo.app.screen

import com.cobo.app.data.FeedPage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 下拉手势在松手那一刻就把刷新头拉下来了，随后才调 [FeedStore.refresh]；只要这个调用有可能
 * 提前 return 而没把 `refreshing` 抬起再落下，刷新头就会永远停在「刷新中」。这几个用例把
 * 「加载中再下拉」「刷新中再下拉」两种情况钉住。
 */
class FeedStoreRefreshTest {

    @Test
    fun pullDuringInitialLoadStillLowersTheFlag() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val store = FeedStore("test") { _, _ ->
            calls++
            if (calls == 1) gate.await()
            FeedPage(emptyList(), null)
        }

        val initial = launch { store.loadInitial() }
        while (calls == 0) yield()
        assertFalse("首次加载不该抬刷新头", store.refreshing)

        val pull = launch { store.refresh() }
        while (!store.refreshing) yield()
        assertTrue("下拉之后刷新头必须立刻抬起", store.refreshing)

        gate.complete(Unit)
        pull.join()
        initial.join()

        assertFalse("刷新必须落回，否则页面卡在刷新中", store.refreshing)
        assertTrue(store.loaded)
    }

    @Test
    fun pullDuringRefreshJoinsTheRunningOne() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val store = FeedStore("test") { _, _ ->
            calls++
            gate.await()
            FeedPage(emptyList(), null)
        }

        val first = launch { store.refresh() }
        while (!store.refreshing) yield()

        val second = launch { store.refresh() }
        second.join()
        assertTrue("第二次下拉直接跟第一次收尾，不该把标志提前降下来", store.refreshing)

        gate.complete(Unit)
        first.join()
        assertFalse(store.refreshing)
        assertTrue("同一时间只该有一次取数", calls == 1)
    }

    @Test
    fun loadMoreIsSkippedWhileRefreshing() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val store = FeedStore("test") { _, _ ->
            calls++
            gate.await()
            FeedPage(emptyList(), null)
        }

        val first = launch { store.refresh() }
        while (!store.refreshing) yield()

        store.loadMore()
        assertTrue("刷新期间不该再排一次翻页", calls == 1)

        gate.complete(Unit)
        first.join()
        assertFalse(store.refreshing)
    }

    @Test
    fun failedRefreshClearsTheFlag() = runBlocking {
        val store = FeedStore("test") { _, _ -> throw IllegalStateException("boom") }

        store.refresh()

        assertFalse(store.refreshing)
        assertTrue(store.error == "boom")
        assertTrue(store.loaded.not())
    }

    @Test
    fun instantLoaderStillExposesTheRaisedFlagToTheUi() = runBlocking {
        // 本地关注没有标记时 loader 立刻返回空页；如果 refresh 也在同一帧里把标志翻回去，
        // COUI 的刷新头看不到 true→false 这一跳，就会永远停在「刷新中」。
        val store = FeedStore("test") { _, _ -> FeedPage(emptyList(), null) }
        var sawRaised = 0
        val watcher = launch {
            while (true) {
                if (store.refreshing) sawRaised++
                yield()
            }
        }

        store.refresh()
        watcher.cancel()

        assertTrue("界面至少要能看到一次 refreshing = true", sawRaised > 0)
        assertFalse(store.refreshing)
        assertTrue(store.loaded)
    }
}
