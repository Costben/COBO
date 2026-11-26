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
    fun autoFloatOnBackRequiresTogglePlayingAndNoError() {
        assertFalse(shouldAutoFloatVideoOnBack(settingEnabled = false, isPlaying = true, hasError = false))
        assertFalse(shouldAutoFloatVideoOnBack(settingEnabled = true, isPlaying = false, hasError = false))
        assertFalse(shouldAutoFloatVideoOnBack(settingEnabled = true, isPlaying = true, hasError = true))
        assertTrue(shouldAutoFloatVideoOnBack(settingEnabled = true, isPlaying = true, hasError = false))
    }

    @Test
    fun autoEnterSystemPipRequiresActiveNonLivePlayingVideo() {
        assertFalse(shouldAutoEnterSystemPip(settingEnabled = true, hasActiveVideo = false, isPlaying = true, isLive = false))
        assertFalse(shouldAutoEnterSystemPip(settingEnabled = false, hasActiveVideo = true, isPlaying = true, isLive = false))
        assertFalse(shouldAutoEnterSystemPip(settingEnabled = true, hasActiveVideo = true, isPlaying = false, isLive = false))
        assertFalse(shouldAutoEnterSystemPip(settingEnabled = true, hasActiveVideo = true, isPlaying = true, isLive = true))
        assertTrue(shouldAutoEnterSystemPip(settingEnabled = true, hasActiveVideo = true, isPlaying = true, isLive = false))
    }

    @Test
    fun videoControlsAutoHidePausesWhenDisabledHiddenPausedOrScrubbing() {
        assertTrue(shouldAutoHideVideoControls(autoHideEnabled = true, controlsVisible = true, isPlaying = true, scrubbing = false))
        assertFalse(shouldAutoHideVideoControls(autoHideEnabled = false, controlsVisible = true, isPlaying = true, scrubbing = false))
        assertFalse(shouldAutoHideVideoControls(autoHideEnabled = true, controlsVisible = false, isPlaying = true, scrubbing = false))
        assertFalse(shouldAutoHideVideoControls(autoHideEnabled = true, controlsVisible = true, isPlaying = false, scrubbing = false))
        assertFalse(shouldAutoHideVideoControls(autoHideEnabled = true, controlsVisible = true, isPlaying = true, scrubbing = true))
    }

    @Test
    fun computeFloatingMiniSizeRespectsLandscapeAndPortraitBounds() {
        val (landW, landH) = computeFloatingMiniSizeDp(16f / 9f)
        assertEquals(186f, landW, 1e-3f)
        assertTrue(landH in 98f..142f)

        val (portW, portH) = computeFloatingMiniSizeDp(9f / 16f)
        assertEquals(124f, portW, 1e-3f)
        assertTrue(portH in 148f..210f)
    }

    @Test
    fun snapFloatingWindowXSnapsToNearestHorizontalEdge() {
        assertEquals(20f, snapFloatingWindowX(currentX = 150f, minX = 20f, maxX = 780f), 1e-3f)
        assertEquals(780f, snapFloatingWindowX(currentX = 600f, minX = 20f, maxX = 780f), 1e-3f)
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
    fun shouldAutoCloseVideoOnCompleteGuardsLiveAndSetting() {
        assertTrue(shouldAutoCloseVideoOnComplete(isLive = false, autoCloseSetting = true, hasPlayed = true))
        assertFalse(shouldAutoCloseVideoOnComplete(isLive = false, autoCloseSetting = false, hasPlayed = true))
        assertFalse(shouldAutoCloseVideoOnComplete(isLive = false, autoCloseSetting = true, hasPlayed = false))
        assertFalse(shouldAutoCloseVideoOnComplete(isLive = true, autoCloseSetting = true, hasPlayed = true))
    }
}
