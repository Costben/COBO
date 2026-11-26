package com.cobo.app.screen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageViewerChromeTest {

    @Test
    fun autoHidesWhenChromeVisibleAndNoBlockingState() {
        assertTrue(
            shouldAutoHideImageViewerChrome(
                chromeVisible = true,
                menuOpen = false,
                avatarMoreOpen = false,
                cropDialogOpen = false,
                fallbackDialogOpen = false,
                uploadingAvatar = false,
                saving = false,
                dismissDragging = false,
                dismissExiting = false,
            ),
        )
    }

    @Test
    fun skipsAutoHideWhenAlreadyHidden() {
        assertFalse(
            shouldAutoHideImageViewerChrome(
                chromeVisible = false,
                menuOpen = false,
                avatarMoreOpen = false,
                cropDialogOpen = false,
                fallbackDialogOpen = false,
                uploadingAvatar = false,
                saving = false,
                dismissDragging = false,
                dismissExiting = false,
            ),
        )
    }

    @Test
    fun pausesAutoHideWhileAnyModalSaveOrDismissIsActive() {
        val blockers = listOf(
            shouldAutoHideImageViewerChrome(true, menuOpen = true, false, false, false, false, false, false, false),
            shouldAutoHideImageViewerChrome(true, false, avatarMoreOpen = true, false, false, false, false, false, false),
            shouldAutoHideImageViewerChrome(true, false, false, cropDialogOpen = true, false, false, false, false, false),
            shouldAutoHideImageViewerChrome(true, false, false, false, fallbackDialogOpen = true, false, false, false, false),
            shouldAutoHideImageViewerChrome(true, false, false, false, false, uploadingAvatar = true, false, false, false),
            shouldAutoHideImageViewerChrome(true, false, false, false, false, false, saving = true, false, false),
            shouldAutoHideImageViewerChrome(true, false, false, false, false, false, false, dismissDragging = true, false),
            shouldAutoHideImageViewerChrome(true, false, false, false, false, false, false, false, dismissExiting = true),
        )
        blockers.forEach { assertFalse(it) }
    }
}
