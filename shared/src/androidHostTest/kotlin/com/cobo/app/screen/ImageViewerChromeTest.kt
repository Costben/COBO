package com.cobo.app.screen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageViewerChromeTest {

    @Test
    fun autoHidesWhenEnabledVisibleAndMenuClosed() {
        assertTrue(
            shouldAutoHideImageViewerChrome(
                autoHideEnabled = true,
                chromeVisible = true,
                menuOpen = false,
            ),
        )
    }

    @Test
    fun skipsAutoHideWhenDisabledHiddenOrMenuOpen() {
        assertFalse(shouldAutoHideImageViewerChrome(autoHideEnabled = false, chromeVisible = true, menuOpen = false))
        assertFalse(shouldAutoHideImageViewerChrome(autoHideEnabled = true, chromeVisible = false, menuOpen = false))
        assertFalse(shouldAutoHideImageViewerChrome(autoHideEnabled = true, chromeVisible = true, menuOpen = true))
    }

    @Test
    fun hidesSystemBarsWithChromeUnlessOverlayOrExiting() {
        assertTrue(
            shouldShowImageViewerSystemBars(
                chromeVisible = true,
                menuOpen = false,
                avatarMoreOpen = false,
                cropDialogOpen = false,
                exiting = false,
            ),
        )
        assertFalse(
            shouldShowImageViewerSystemBars(
                chromeVisible = false,
                menuOpen = false,
                avatarMoreOpen = false,
                cropDialogOpen = false,
                exiting = false,
            ),
        )
        assertTrue(
            shouldShowImageViewerSystemBars(
                chromeVisible = false,
                menuOpen = true,
                avatarMoreOpen = false,
                cropDialogOpen = false,
                exiting = false,
            ),
        )
        assertTrue(
            shouldShowImageViewerSystemBars(
                chromeVisible = false,
                menuOpen = false,
                avatarMoreOpen = false,
                cropDialogOpen = false,
                exiting = true,
            ),
        )
    }

    @Test
    fun releasesSystemBarOwnershipImmediatelyWhenExitingOrScrimBelowHalf() {
        assertTrue(shouldImageViewerOwnSystemBars(exiting = false, bgAlpha = 1f))
        assertTrue(shouldImageViewerOwnSystemBars(exiting = false, bgAlpha = 0.5f))
        assertFalse(shouldImageViewerOwnSystemBars(exiting = false, bgAlpha = 0.49f))
        assertFalse(shouldImageViewerOwnSystemBars(exiting = true, bgAlpha = 1f))
    }
}
