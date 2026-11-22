package com.cobo.app.screen

import com.cobo.app.data.FeedGap
import com.cobo.app.data.FeedPage
import com.cobo.app.data.Status
import com.cobo.app.data.User
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReverseBrowseFeedStoreTest {

    @Test
    fun overlappingRefreshPrependsNewItemsWithoutGapAndRestoresAnchor() = runBlocking {
        var call = 0
        val store = FeedStore(
            key = "friends",
            supportsReverseBrowse = true,
            reverseBrowseEnabled = { true },
        ) { _, _ ->
            call++
            when (call) {
                1 -> FeedPage(listOf(status("10"), status("9"), status("8")), sinceId = "bottom_7")
                else -> FeedPage(listOf(status("12"), status("11"), status("10"), status("9")), sinceId = "c_8")
            }
        }
        val controller = RecordingAnchorController(prependAnchor = PrependAnchor("9", 120))
        store.scrollAnchorController = controller

        store.loadInitial()
        val initialGen = store.generation
        store.refresh()

        assertEquals(listOf("12", "11", "10", "9", "8"), store.items.map { it.id })
        assertTrue(store.gaps.isEmpty())
        assertEquals("10", store.lastReadBeforeStatusId)
        assertEquals("bottom_7", store.sinceId)
        assertEquals("倒序增量刷新不该触发回顶 generation", initialGen, store.generation)
        assertEquals(listOf("12", "11", "read_marker:10", "10", "9", "8"), store.entryKeys())
        assertEquals(PrependAnchor("9", 120), controller.restoredPrepend)
    }

    @Test
    fun autoBridgeConnectsAcrossUpToTwoExtraPagesWithoutLeavingGap() = runBlocking {
        var refreshRound = 0
        val store = FeedStore(
            key = "friends",
            supportsReverseBrowse = true,
            reverseBrowseEnabled = { true },
        ) { cursor, _ ->
            if (refreshRound == 0) {
                refreshRound++
                FeedPage(listOf(status("10"), status("9")), sinceId = "bottom_8")
            } else {
                when (cursor) {
                    null -> FeedPage(listOf(status("30"), status("29")), sinceId = "c_28")
                    "c_28" -> FeedPage(listOf(status("20"), status("19")), sinceId = "c_18")
                    "c_18" -> FeedPage(listOf(status("12"), status("10")), sinceId = "c_9")
                    else -> error("unexpected cursor: $cursor")
                }
            }
        }

        store.loadInitial()
        store.refresh()

        assertEquals(listOf("30", "29", "20", "19", "12", "10", "9"), store.items.map { it.id })
        assertTrue(store.gaps.isEmpty())
        assertEquals("bottom_8", store.sinceId)
    }

    @Test
    fun largeGapInsertsGapCardAndLoadGapBridgesToOldHead() = runBlocking {
        var initialDone = false
        var failOnce = false
        val store = FeedStore(
            key = "friends",
            supportsReverseBrowse = true,
            reverseBrowseEnabled = { true },
        ) { cursor, _ ->
            if (!initialDone) {
                initialDone = true
                FeedPage(listOf(status("10"), status("9")), sinceId = "bottom_8")
            } else {
                when (cursor) {
                    null -> FeedPage(listOf(status("50"), status("49")), sinceId = "c_48")
                    "c_48" -> FeedPage(listOf(status("40"), status("39")), sinceId = "c_38")
                    "c_38" -> FeedPage(listOf(status("30"), status("29")), sinceId = "c_28")
                    "c_28" -> {
                        if (failOnce) {
                            failOnce = false
                            throw IllegalStateException("网络超时")
                        }
                        FeedPage(listOf(status("20"), status("19")), sinceId = "c_18")
                    }
                    "c_18" -> FeedPage(listOf(status("12"), status("10"), status("9")), sinceId = "c_8")
                    else -> error("unexpected cursor: $cursor")
                }
            }
        }

        store.loadInitial()
        store.refresh()

        assertEquals(1, store.gaps.size)
        val gap = store.gaps.first()
        assertEquals("c_28", gap.cursor)
        assertEquals("10", gap.beforeStatusId)
        assertEquals(
            listOf("50", "49", "40", "39", "30", "29", "gap:${gap.id}", "10", "9"),
            store.entryKeys(),
        )

        // 模拟点击断点失败后可重试
        failOnce = true
        store.loadGap(gap.id)
        assertFalse(store.gaps.first().loading)
        assertEquals("网络超时", store.gaps.first().error)

        // 第一次补拉中间段：拉回 20、19，尚未接上 10，断点更新游标与已补条数
        store.loadGap(gap.id)
        assertEquals(1, store.gaps.size)
        val updatedGap = store.gaps.first()
        assertEquals("c_18", updatedGap.cursor)
        assertEquals(2, updatedGap.loadedCount)
        assertNull(updatedGap.error)
        assertEquals(
            listOf("50", "49", "40", "39", "30", "29", "20", "19", "gap:${gap.id}", "10", "9"),
            store.entryKeys(),
        )

        // 第二次补拉接上 10：插入 12 并移除断点卡片，原边界转为「上次阅读到这里」标记
        store.loadGap(gap.id)
        assertTrue(store.gaps.isEmpty())
        assertEquals("10", store.lastReadBeforeStatusId)
        assertEquals(
            listOf("50", "49", "40", "39", "30", "29", "20", "19", "12", "read_marker:10", "10", "9"),
            store.entryKeys(),
        )
        assertEquals("bottom_8", store.sinceId)
    }

    @Test
    fun pinnedOldPostAtTopOfNewPageDoesNotHideGap() = runBlocking {
        var round = 0
        val store = FeedStore(
            key = "friends",
            supportsReverseBrowse = true,
            reverseBrowseEnabled = { true },
        ) { cursor, _ ->
            if (round == 0) {
                round++
                FeedPage(listOf(status("10"), status("9")), sinceId = "bottom_8")
            } else {
                when (cursor) {
                    null -> FeedPage(listOf(status("10"), status("50"), status("49")), sinceId = "c_48")
                    "c_48" -> FeedPage(listOf(status("40")), sinceId = "c_39")
                    "c_39" -> FeedPage(listOf(status("30")), sinceId = "c_29")
                    else -> error("unexpected cursor: $cursor")
                }
            }
        }

        store.loadInitial()
        store.refresh()

        assertEquals(listOf("50", "49", "40", "30", "10", "9"), store.items.map { it.id })
        assertEquals(1, store.gaps.size)
        assertEquals("c_29", store.gaps.first().cursor)
        assertEquals("10", store.gaps.first().beforeStatusId)
    }

    @Test
    fun multipleRefreshesCanMaintainIndependentGapsAndReverseToggleRevertsToReplace() = runBlocking {
        var enabled = true
        var round = 0
        val store = FeedStore(
            key = "friends",
            supportsReverseBrowse = true,
            reverseBrowseEnabled = { enabled },
        ) { cursor, _ ->
            when (round) {
                0 -> {
                    round++
                    FeedPage(listOf(status("10")), sinceId = "bottom")
                }
                1 -> when (cursor) {
                    null -> FeedPage(listOf(status("30")), sinceId = "c_29")
                    "c_29" -> FeedPage(listOf(status("25")), sinceId = "c_24")
                    "c_24" -> FeedPage(listOf(status("22")), sinceId = "c_21")
                    else -> error("unexpected cursor: $cursor")
                }
                2 -> when (cursor) {
                    null -> FeedPage(listOf(status("60")), sinceId = "c_59")
                    "c_59" -> FeedPage(listOf(status("55")), sinceId = "c_54")
                    "c_54" -> FeedPage(listOf(status("52")), sinceId = "c_51")
                    else -> error("unexpected cursor: $cursor")
                }
                else -> FeedPage(listOf(status("100")), sinceId = "fresh_bottom")
            }
        }

        store.loadInitial()
        store.refresh()
        round = 2
        store.refresh()

        assertEquals(2, store.gaps.size)
        val topGap = store.gaps[0]
        val lowerGap = store.gaps[1]
        assertEquals("30", topGap.beforeStatusId)
        assertEquals("10", lowerGap.beforeStatusId)

        // 关闭开关后再刷新：回退为整表替换并清空断点
        enabled = false
        round = 3
        val genBefore = store.generation
        store.refresh()

        assertTrue(store.gaps.isEmpty())
        assertEquals(listOf("100"), store.items.map { it.id })
        assertEquals("fresh_bottom", store.sinceId)
        assertTrue(store.generation > genBefore)
    }

    @Test
    fun stalledCursorDuringLoadGapRemovesGapSafely() = runBlocking {
        var round = 0
        val store = FeedStore(
            key = "friends",
            supportsReverseBrowse = true,
            reverseBrowseEnabled = { true },
        ) { cursor, _ ->
            if (round == 0) {
                round++
                FeedPage(listOf(status("10")), sinceId = "bottom")
            } else {
                when (cursor) {
                    null -> FeedPage(listOf(status("30")), sinceId = "c_29")
                    "c_29" -> FeedPage(listOf(status("25")), sinceId = "c_24")
                    "c_24" -> FeedPage(listOf(status("22")), sinceId = "c_21")
                    "c_21" -> FeedPage(listOf(status("21")), sinceId = "c_21")
                    else -> FeedPage(emptyList(), null)
                }
            }
        }

        store.loadInitial()
        store.refresh()
        assertEquals(1, store.gaps.size)
        val gapId = store.gaps.first().id

        store.loadGap(gapId)
        assertTrue("游标停滞时必须自动消除断点，避免死循环", store.gaps.isEmpty())
        assertEquals(listOf("30", "25", "22", "21", "10"), store.items.map { it.id })
    }

    @Test
    fun normalRefreshMarksPreviousHeadAndSurvivesZeroNewRefresh() = runBlocking {
        var round = 0
        val store = FeedStore(
            key = "friends",
            supportsReverseBrowse = true,
            reverseBrowseEnabled = { false },
        ) { _, _ ->
            when (round++) {
                0 -> FeedPage(listOf(status("10"), status("9"), status("8")), sinceId = "c_7")
                1 -> FeedPage(listOf(status("12"), status("11"), status("10"), status("9")), sinceId = "c_8")
                2 -> FeedPage(listOf(status("12"), status("11"), status("10"), status("9")), sinceId = "c_8")
                else -> FeedPage(listOf(status("20"), status("19")), sinceId = "c_18")
            }
        }

        store.loadInitial()
        assertNull(store.lastReadBeforeStatusId)

        // 刷出 2 条新微博：10 退到第 3 位，在它前面挂「上次阅读到这里」
        store.refresh()
        assertEquals("10", store.lastReadBeforeStatusId)
        assertEquals(listOf("12", "11", "read_marker:10", "10", "9"), store.entryKeys())

        // 再刷一次没有新微博：标记保留
        store.refresh()
        assertEquals("10", store.lastReadBeforeStatusId)

        // 删除被标记的那条微博：标记顺延到下一条存活微博 9
        store.removeDeleted(mapOf("10" to true))
        assertEquals("9", store.lastReadBeforeStatusId)
        assertEquals(listOf("12", "11", "read_marker:9", "9"), store.entryKeys())

        // 刷出跨一整页的新微博（旧头部完全不在首页内）：清空标记
        store.refresh()
        assertNull(store.lastReadBeforeStatusId)
        assertEquals(listOf("20", "19"), store.entryKeys())
    }

    private fun status(id: String) = Status(
        id = id,
        mid = id,
        textHtml = "第 $id 条",
        createdAt = "",
        source = "",
        user = User(id = "123", name = "测试", avatar = ""),
    )

    private class RecordingAnchorController(
        private val prependAnchor: PrependAnchor? = null,
    ) : ScrollAnchorController {
        var restoredPrepend: PrependAnchor? = null

        override fun captureForPrepend(): PrependAnchor? = prependAnchor

        override fun restoreAfterPrepend(anchor: PrependAnchor?) {
            restoredPrepend = anchor
        }

        override fun captureForGap(gap: FeedGap): GapAnchor? = null

        override fun restoreAfterGap(anchor: GapAnchor?, beforeStatusId: String, gapId: String) = Unit
    }
}
