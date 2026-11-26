package com.cobo.app.ui

import com.cobo.app.screen.shouldAutoHideVideoControls
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingVideoControllerTest {

    @Test
    fun clampPipAspectRatioBoundsExtremesAndInvalidValues() {
        assertEquals(16f / 9f, clampPipAspectRatio(0f), 1e-4f)
        assertEquals(16f / 9f, clampPipAspectRatio(-1.5f), 1e-4f)
        assertEquals(16f / 9f, clampPipAspectRatio(Float.NaN), 1e-4f)
        assertEquals(16f / 9f, clampPipAspectRatio(Float.POSITIVE_INFINITY), 1e-4f)
        assertEquals(MinPipAspectRatio, clampPipAspectRatio(0.1f), 1e-4f)
        assertEquals(MaxPipAspectRatio, clampPipAspectRatio(5.0f), 1e-4f)
        assertEquals(1.5f, clampPipAspectRatio(1.5f), 1e-4f)
    }

    @Test
    fun autoFloatOnBackRequiresToggleAndPlayingOrLive() {
        assertFalse(shouldAutoFloatVideoOnBack(autoFloatEnabled = false, isPlaying = true, isLive = true))
        assertFalse(shouldAutoFloatVideoOnBack(autoFloatEnabled = true, isPlaying = false, isLive = false))
        assertTrue(shouldAutoFloatVideoOnBack(autoFloatEnabled = true, isPlaying = true, isLive = false))
        assertTrue(shouldAutoFloatVideoOnBack(autoFloatEnabled = true, isPlaying = false, isLive = true))
    }

    @Test
    fun autoEnterSystemPipRequiresActiveVideoAndPlayingOrLive() {
        assertFalse(shouldAutoEnterSystemPip(autoFloatEnabled = true, hasActiveVideo = false, isPlaying = true, isLive = true))
        assertFalse(shouldAutoEnterSystemPip(autoFloatEnabled = false, hasActiveVideo = true, isPlaying = true, isLive = true))
        assertFalse(shouldAutoEnterSystemPip(autoFloatEnabled = true, hasActiveVideo = true, isPlaying = false, isLive = false))
        assertTrue(shouldAutoEnterSystemPip(autoFloatEnabled = true, hasActiveVideo = true, isPlaying = true, isLive = false))
        assertTrue(shouldAutoEnterSystemPip(autoFloatEnabled = true, hasActiveVideo = true, isPlaying = false, isLive = true))
    }

    @Test
    fun videoControlsAutoHidePausesWhenPausedMenuOpenScrubbingOrInPip() {
        assertTrue(shouldAutoHideVideoControls(controlsVisible = true, isPlaying = true, speedMenu = false, isScrubbing = false, systemPipActive = false))
        assertFalse(shouldAutoHideVideoControls(controlsVisible = false, isPlaying = true, speedMenu = false, isScrubbing = false, systemPipActive = false))
        assertFalse(shouldAutoHideVideoControls(controlsVisible = true, isPlaying = false, speedMenu = false, isScrubbing = false, systemPipActive = false))
        assertFalse(shouldAutoHideVideoControls(controlsVisible = true, isPlaying = true, speedMenu = true, isScrubbing = false, systemPipActive = false))
        assertFalse(shouldAutoHideVideoControls(controlsVisible = true, isPlaying = true, speedMenu = false, isScrubbing = true, systemPipActive = false))
        assertFalse(shouldAutoHideVideoControls(controlsVisible = true, isPlaying = true, speedMenu = false, isScrubbing = false, systemPipActive = true))
    }

    @Test
    fun computeFloatingMiniSizeRespectsLandscapeAndPortraitBounds() {
        val (landW, landH) = computeFloatingMiniSizeDp(16f / 9f)
        assertEquals(204f, landW, 1e-3f)
        assertTrue(landH in 112f..148f)

        val (portW, portH) = computeFloatingMiniSizeDp(9f / 16f)
        assertEquals(124f, portW, 1e-3f)
        assertTrue(portH in 156f..210f)
    }

    @Test
    fun snapFloatingWindowXSnapsToNearestHorizontalEdge() {
        val margin = 20f
        val winW = 200f
        val containerW = 1000f
        assertEquals(20f, snapFloatingWindowX(currentX = 150f, windowWidthPx = winW, containerWidthPx = containerW, marginPx = margin), 1e-3f)
        assertEquals(780f, snapFloatingWindowX(currentX = 600f, windowWidthPx = winW, containerWidthPx = containerW, marginPx = margin), 1e-3f)
    }
}
