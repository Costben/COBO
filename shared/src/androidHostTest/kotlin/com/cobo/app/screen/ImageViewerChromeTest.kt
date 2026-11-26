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
}
