package com.cobo.app.ui

import androidx.media3.common.Player
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

    @Test
    fun classifyVideoVerticalZoneSplitsIntoBrightnessCenterAndVolume() {
        assertEquals(VideoVerticalZone.Brightness, classifyVideoVerticalZone(xPx = 0f, containerWidthPx = 1000f))
        assertEquals(VideoVerticalZone.Brightness, classifyVideoVerticalZone(xPx = 299f, containerWidthPx = 1000f))
        assertEquals(VideoVerticalZone.CenterActions, classifyVideoVerticalZone(xPx = 300f, containerWidthPx = 1000f))
        assertEquals(VideoVerticalZone.CenterActions, classifyVideoVerticalZone(xPx = 500f, containerWidthPx = 1000f))
        assertEquals(VideoVerticalZone.CenterActions, classifyVideoVerticalZone(xPx = 700f, containerWidthPx = 1000f))
        assertEquals(VideoVerticalZone.Volume, classifyVideoVerticalZone(xPx = 701f, containerWidthPx = 1000f))
        assertEquals(VideoVerticalZone.Volume, classifyVideoVerticalZone(xPx = 1000f, containerWidthPx = 1000f))
        assertEquals(VideoVerticalZone.CenterActions, classifyVideoVerticalZone(xPx = 50f, containerWidthPx = 0f))

        assertEquals(
            VideoVerticalZone.Brightness,
            classifyVideoVerticalZone(xPx = 499f, containerWidthPx = 1000f, centerSwipeEnabled = false),
        )
        assertEquals(
            VideoVerticalZone.Volume,
            classifyVideoVerticalZone(xPx = 500f, containerWidthPx = 1000f, centerSwipeEnabled = false),
        )
    }

    @Test
    fun evaluateVideoCenterGestureRevealsButtonsSequentiallyAndRequiresThresholdToArm() {
        val dead = evaluateVideoCenterGesture(draggedYPx = -16f, density = 1f)
        assertEquals(VideoCenterGestureDirection.None, dead.direction)
        assertEquals(VideoCenterGestureAction.None, dead.armedAction)

        val earlyUp = evaluateVideoCenterGesture(draggedYPx = -55f, density = 1f)
        assertEquals(VideoCenterGestureDirection.Up, earlyUp.direction)
        assertTrue(earlyUp.inAppReveal > 0f)
        assertEquals(0f, earlyUp.pipReveal, 1e-4f)
        assertEquals(VideoCenterGestureAction.None, earlyUp.armedAction)

        val midUp = evaluateVideoCenterGesture(draggedYPx = -115f, density = 1f)
        assertEquals(VideoCenterGestureDirection.Up, midUp.direction)
        assertEquals(1f, midUp.inAppReveal, 1e-4f)
        assertTrue(midUp.pipReveal in 0.01f..0.99f)
        assertEquals(VideoCenterGestureAction.InAppFloating, midUp.armedAction)

        val highUp = evaluateVideoCenterGesture(draggedYPx = -195f, density = 1f)
        assertEquals(VideoCenterGestureDirection.Up, highUp.direction)
        assertEquals(1f, highUp.inAppReveal, 1e-4f)
        assertEquals(1f, highUp.pipReveal, 1e-4f)
        assertEquals(VideoCenterGestureAction.SystemPip, highUp.armedAction)

        val earlyDown = evaluateVideoCenterGesture(draggedYPx = 60f, density = 1f)
        assertEquals(VideoCenterGestureDirection.Down, earlyDown.direction)
        assertTrue(earlyDown.closeReveal in 0.01f..0.99f)
        assertEquals(VideoCenterGestureAction.None, earlyDown.armedAction)

        val fullDown = evaluateVideoCenterGesture(draggedYPx = 115f, density = 1f)
        assertEquals(VideoCenterGestureDirection.Down, fullDown.direction)
        assertEquals(1f, fullDown.closeReveal, 1e-4f)
        assertEquals(VideoCenterGestureAction.Close, fullDown.armedAction)
    }

    @Test
    fun videoCenterGestureHintTextMatchesStateWithoutMiddleDot() {
        val texts = listOf(
            videoCenterGestureHintText(VideoCenterGestureDirection.Up, VideoCenterGestureAction.None),
            videoCenterGestureHintText(VideoCenterGestureDirection.Up, VideoCenterGestureAction.InAppFloating),
            videoCenterGestureHintText(VideoCenterGestureDirection.Up, VideoCenterGestureAction.SystemPip),
            videoCenterGestureHintText(VideoCenterGestureDirection.Down, VideoCenterGestureAction.None),
            videoCenterGestureHintText(VideoCenterGestureDirection.Down, VideoCenterGestureAction.Close),
        )
        texts.forEach { text ->
            assertTrue(text.isNotEmpty())
            assertFalse(text.contains('\u00B7'))
        }
        assertEquals("", videoCenterGestureHintText(VideoCenterGestureDirection.None, VideoCenterGestureAction.None))
    }

    @Test
    fun shouldAutoCloseVideoOnCompleteGuardsLiveScrubbingAndFloating() {
        assertTrue(
            shouldAutoCloseVideoOnComplete(
                autoCloseEnabled = true,
                playbackState = Player.STATE_ENDED,
                hasStartedPlaying = true,
                isLive = false,
                isScrubbing = false,
                handedOffToFloating = false,
            ),
        )
        assertFalse(
            shouldAutoCloseVideoOnComplete(
                autoCloseEnabled = false,
                playbackState = Player.STATE_ENDED,
                hasStartedPlaying = true,
                isLive = false,
                isScrubbing = false,
                handedOffToFloating = false,
            ),
        )
        assertFalse(
            shouldAutoCloseVideoOnComplete(
                autoCloseEnabled = true,
                playbackState = Player.STATE_READY,
                hasStartedPlaying = true,
                isLive = false,
                isScrubbing = false,
                handedOffToFloating = false,
            ),
        )
        assertFalse(
            shouldAutoCloseVideoOnComplete(
                autoCloseEnabled = true,
                playbackState = Player.STATE_ENDED,
                hasStartedPlaying = false,
                isLive = false,
                isScrubbing = false,
                handedOffToFloating = false,
            ),
        )
        assertFalse(
            shouldAutoCloseVideoOnComplete(
                autoCloseEnabled = true,
                playbackState = Player.STATE_ENDED,
                hasStartedPlaying = true,
                isLive = true,
                isScrubbing = false,
                handedOffToFloating = false,
            ),
        )
        assertFalse(
            shouldAutoCloseVideoOnComplete(
                autoCloseEnabled = true,
                playbackState = Player.STATE_ENDED,
                hasStartedPlaying = true,
                isLive = false,
                isScrubbing = true,
                handedOffToFloating = false,
            ),
        )
        assertFalse(
            shouldAutoCloseVideoOnComplete(
                autoCloseEnabled = true,
                playbackState = Player.STATE_ENDED,
                hasStartedPlaying = true,
                isLive = false,
                isScrubbing = false,
                handedOffToFloating = true,
            ),
        )
    }
}
