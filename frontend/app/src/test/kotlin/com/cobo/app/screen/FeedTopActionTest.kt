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
    fun toggleTopOrReturnHandlesAllStates() {
        assertEquals(
            FeedTopEffect.GlideToTopAndSaveAnchor,
            resolveFeedTopEffect(
                action = FeedTopAction.ToggleTopOrReturn,
                atTop = false,
                hasSavedAnchor = false,
                reverseBrowse = false,
            ),
        )
        assertEquals(
            FeedTopEffect.GlideToTopAndSaveAnchor,
            resolveFeedTopEffect(
                action = FeedTopAction.ToggleTopOrReturn,
                atTop = false,
                hasSavedAnchor = true,
                reverseBrowse = true,
            ),
        )
        assertEquals(
            FeedTopEffect.ReturnToSavedAnchor,
            resolveFeedTopEffect(
                action = FeedTopAction.ToggleTopOrReturn,
                atTop = true,
                hasSavedAnchor = true,
                reverseBrowse = false,
            ),
        )
        assertEquals(
            FeedTopEffect.NoOp,
            resolveFeedTopEffect(
                action = FeedTopAction.ToggleTopOrReturn,
                atTop = true,
                hasSavedAnchor = false,
                reverseBrowse = false,
            ),
        )
    }

    @Test
    fun refreshFromTabRespectsReverseBrowse() {
        assertEquals(
            FeedTopEffect.GlideToTopThenRefresh,
            resolveFeedTopEffect(
                action = FeedTopAction.RefreshFromTab,
                atTop = false,
                hasSavedAnchor = true,
                reverseBrowse = false,
            ),
        )
        assertEquals(
            FeedTopEffect.RefreshInPlace,
            resolveFeedTopEffect(
                action = FeedTopAction.RefreshFromTab,
                atTop = false,
                hasSavedAnchor = true,
                reverseBrowse = true,
            ),
        )
    }

    @Test
    fun legacyDoubleTapKeepsUpstreamBehavior() {
        assertEquals(
            FeedTopEffect.GlideToTopThenRefresh,
            resolveFeedTopEffect(
                action = FeedTopAction.LegacyDoubleTap,
                atTop = false,
                hasSavedAnchor = false,
                reverseBrowse = false,
            ),
        )
        assertEquals(
            FeedTopEffect.GlideToTopOnly,
            resolveFeedTopEffect(
                action = FeedTopAction.LegacyDoubleTap,
                atTop = false,
                hasSavedAnchor = false,
                reverseBrowse = true,
            ),
        )
        assertEquals(
            FeedTopEffect.RefreshInPlace,
            resolveFeedTopEffect(
                action = FeedTopAction.LegacyDoubleTap,
                atTop = true,
                hasSavedAnchor = false,
                reverseBrowse = true,
            ),
        )
    }

    @Test
    fun bottomLeftAccessoryKeepsUpstreamBehavior() {
        assertEquals(
            FeedTopEffect.GlideToTopThenRefresh,
            resolveFeedTopEffect(
                action = FeedTopAction.RefreshFromAccessory,
                atTop = false,
                hasSavedAnchor = false,
                reverseBrowse = false,
            ),
        )
        assertEquals(
            FeedTopEffect.GlideToTopThenRefresh,
            resolveFeedTopEffect(
                action = FeedTopAction.RefreshFromAccessory,
                atTop = true,
                hasSavedAnchor = false,
                reverseBrowse = false,
            ),
        )
        assertEquals(
            FeedTopEffect.RefreshInPlace,
            resolveFeedTopEffect(
                action = FeedTopAction.RefreshFromAccessory,
                atTop = false,
                hasSavedAnchor = false,
                reverseBrowse = true,
            ),
        )
        assertEquals(
            FeedTopEffect.RefreshInPlace,
            resolveFeedTopEffect(
                action = FeedTopAction.RefreshFromAccessory,
                atTop = true,
                hasSavedAnchor = false,
                reverseBrowse = true,
            ),
        )
    }
}
