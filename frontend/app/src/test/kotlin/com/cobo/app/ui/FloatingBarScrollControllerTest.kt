package com.cobo.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingBarScrollControllerTest {

    @Test
    fun hidesOnlyAfterExceedingDownThresholdOutsideSafeZone() {
        val tracker = FloatingBarHideTracker(hideThresholdPx = 50f, showThresholdPx = 30f)

        assertFalse(tracker.onScrollDelta(deltaY = -20f, isNearTop = false, enabled = true))
        assertFalse(tracker.onScrollDelta(deltaY = -29f, isNearTop = false, enabled = true))
        assertTrue(tracker.onScrollDelta(deltaY = -1f, isNearTop = false, enabled = true))
    }

    @Test
    fun staysVisibleInsideTopSafeZoneDespiteDownwardDelta() {
        val tracker = FloatingBarHideTracker(hideThresholdPx = 50f, showThresholdPx = 30f)

        assertFalse(tracker.onScrollDelta(deltaY = -120f, isNearTop = true, enabled = true))
        assertFalse(tracker.onScrollDelta(deltaY = -40f, isNearTop = false, enabled = true))
        assertTrue(tracker.onScrollDelta(deltaY = -15f, isNearTop = false, enabled = true))
    }

    @Test
    fun revealsAfterExceedingUpThreshold() {
        val tracker = FloatingBarHideTracker(hideThresholdPx = 50f, showThresholdPx = 30f)
        assertTrue(tracker.onScrollDelta(deltaY = -60f, isNearTop = false, enabled = true))

        assertTrue(tracker.onScrollDelta(deltaY = 20f, isNearTop = false, enabled = true))
        assertTrue(tracker.onScrollDelta(deltaY = 9f, isNearTop = false, enabled = true))
        assertFalse(tracker.onScrollDelta(deltaY = 1f, isNearTop = false, enabled = true))
    }

    @Test
    fun smallJitterReversalsResetOppositeAccumulator() {
        val tracker = FloatingBarHideTracker(hideThresholdPx = 50f, showThresholdPx = 30f)

        assertFalse(tracker.onScrollDelta(deltaY = -40f, isNearTop = false, enabled = true))
        assertFalse(tracker.onScrollDelta(deltaY = 10f, isNearTop = false, enabled = true))
        // Opposite jitter cleared the downward accumulator, so -40f alone does not reach 50f yet.
        assertFalse(tracker.onScrollDelta(deltaY = -40f, isNearTop = false, enabled = true))
        assertTrue(tracker.onScrollDelta(deltaY = -10f, isNearTop = false, enabled = true))

        // While hidden, brief downward drag resets upward reveal progress.
        assertTrue(tracker.onScrollDelta(deltaY = 20f, isNearTop = false, enabled = true))
        assertTrue(tracker.onScrollDelta(deltaY = -5f, isNearTop = false, enabled = true))
        assertTrue(tracker.onScrollDelta(deltaY = 20f, isNearTop = false, enabled = true))
        assertFalse(tracker.onScrollDelta(deltaY = 10f, isNearTop = false, enabled = true))
    }

    @Test
    fun enteringNearTopWhileHiddenRevealsImmediately() {
        val tracker = FloatingBarHideTracker(hideThresholdPx = 50f, showThresholdPx = 30f)
        assertTrue(tracker.onScrollDelta(deltaY = -80f, isNearTop = false, enabled = true))

        assertFalse(tracker.onScrollDelta(deltaY = -10f, isNearTop = true, enabled = true))
        assertFalse(tracker.hidden)
    }

    @Test
    fun disablingForcesRevealAndIgnoresScroll() {
        val tracker = FloatingBarHideTracker(hideThresholdPx = 50f, showThresholdPx = 30f)
        assertTrue(tracker.onScrollDelta(deltaY = -80f, isNearTop = false, enabled = true))

        assertFalse(tracker.onScrollDelta(deltaY = -100f, isNearTop = false, enabled = false))
        assertFalse(tracker.hidden)
    }
}
