package com.cobo.app.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LivePhotoPlaybackStateTest {

    @Test
    fun autoPlayPlaysOnceEndsAndRequiresLongPressAfterwards() {
        val state = LivePhotoPlaybackState()

        assertTrue(state.tryAutoPlay(enabled = true))
        assertTrue(state.autoPlaying)
        assertTrue(state.isPlaying)
        assertTrue(state.autoPlayedForVisit)

        // Cannot auto-play a second time while staying on the same page
        assertFalse(state.tryAutoPlay(enabled = true))

        // Playback finishes: stops and does not loop
        state.onPlaybackEnded()
        assertFalse(state.autoPlaying)
        assertFalse(state.isPlaying)
        assertTrue(state.autoPlayedForVisit)
        assertFalse(state.tryAutoPlay(enabled = true))

        // Subsequent playback on the same visit requires long press
        state.onLongPressStart()
        assertTrue(state.holding)
        assertTrue(state.isPlaying)
        assertEquals(1, state.holdSession)

        state.onLongPressEnd()
        assertFalse(state.holding)
        assertFalse(state.isPlaying)
    }

    @Test
    fun leavingAndReturningResetsAutoPlayForNewSwipeVisit() {
        val state = LivePhotoPlaybackState()

        assertTrue(state.tryAutoPlay(enabled = true))
        state.onPlaybackEnded()
        assertFalse(state.tryAutoPlay(enabled = true))

        // Swiping away resets visit state
        state.onLeftPage()
        assertFalse(state.autoPlayedForVisit)
        assertFalse(state.isPlaying)

        // Swiping back auto-plays once again
        assertTrue(state.tryAutoPlay(enabled = true))
        assertTrue(state.autoPlaying)
        assertTrue(state.isPlaying)
    }

    @Test
    fun disabledAutoPlayNeverAutoPlaysButAllowsLongPress() {
        val state = LivePhotoPlaybackState()

        assertFalse(state.tryAutoPlay(enabled = false))
        assertFalse(state.isPlaying)
        assertFalse(state.autoPlayedForVisit)

        state.onLongPressStart()
        assertTrue(state.holding)
        assertTrue(state.isPlaying)

        state.onLongPressEnd()
        assertFalse(state.isPlaying)
    }

    @Test
    fun longPressDuringAutoPlayTransfersControlToHold() {
        val state = LivePhotoPlaybackState()

        assertTrue(state.tryAutoPlay(enabled = true))
        assertTrue(state.autoPlaying)

        state.onLongPressStart()
        assertFalse(state.autoPlaying)
        assertTrue(state.holding)
        assertTrue(state.isPlaying)
        assertEquals(1, state.holdSession)

        // Releasing finger stops playback instead of falling back to auto-play
        state.onLongPressEnd()
        assertFalse(state.isPlaying)
    }

    @Test
    fun interruptionStopsPlaybackWithoutResettingVisitFlag() {
        val state = LivePhotoPlaybackState()

        assertTrue(state.tryAutoPlay(enabled = true))
        state.onInterrupted()

        assertFalse(state.isPlaying)
        assertFalse(state.autoPlaying)
        assertFalse(state.holding)
        assertTrue(state.autoPlayedForVisit)
        assertFalse(state.tryAutoPlay(enabled = true))
    }
}
