package com.cobo.app.screen

import com.cobo.app.FeedTopAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedTopActionTest {

    @Test
    fun isFeedAtTopRespectsZeroIndexAndTolerance() {
        assertTrue(isFeedAtTop(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 0))
        assertTrue(isFeedAtTop(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 2))
        assertFalse(isFeedAtTop(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 3))
        assertFalse(isFeedAtTop(firstVisibleItemIndex = 1, firstVisibleItemScrollOffset = 0))
    }

    @Test
    fun feedTabTapScrollsToTopFirstThenRefreshesWhenAtTop() {
        assertEquals(
            FeedTopEffect.GlideToTopOnly,
            resolveFeedTopEffect(FeedTopAction.ScrollToTopOrRefresh, atTop = false, reverseBrowse = false),
        )
        assertEquals(
            FeedTopEffect.GlideToTopOnly,
            resolveFeedTopEffect(FeedTopAction.ScrollToTopOrRefresh, atTop = false, reverseBrowse = true),
        )
        assertEquals(
            FeedTopEffect.RefreshInPlace,
            resolveFeedTopEffect(FeedTopAction.ScrollToTopOrRefresh, atTop = true, reverseBrowse = false),
        )
        assertEquals(
            FeedTopEffect.RefreshInPlace,
            resolveFeedTopEffect(FeedTopAction.ScrollToTopOrRefresh, atTop = true, reverseBrowse = true),
        )
    }

    @Test
    fun bottomLeftAccessoryKeepsUpstreamBehavior() {
        assertEquals(
            FeedTopEffect.GlideToTopThenRefresh,
            resolveFeedTopEffect(FeedTopAction.RefreshFromAccessory, atTop = false, reverseBrowse = false),
        )
        assertEquals(
            FeedTopEffect.GlideToTopThenRefresh,
            resolveFeedTopEffect(FeedTopAction.RefreshFromAccessory, atTop = true, reverseBrowse = false),
        )
        assertEquals(
            FeedTopEffect.RefreshInPlace,
            resolveFeedTopEffect(FeedTopAction.RefreshFromAccessory, atTop = false, reverseBrowse = true),
        )
        assertEquals(
            FeedTopEffect.RefreshInPlace,
            resolveFeedTopEffect(FeedTopAction.RefreshFromAccessory, atTop = true, reverseBrowse = true),
        )
    }
}
