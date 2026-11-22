package com.cobo.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class AvatarEditorTest {

    @Test
    fun clampCropOffsetKeepsWidePhotoWithinSquareFrame() {
        // 2000x1000 image in a 500x500 crop box: baseScale = 0.5 -> scaled 1000x500 -> maxDx = 250, maxDy = 0
        val (x1, y1) = clampCropOffset(
            offsetX = 400f,
            offsetY = 80f,
            bmpWidth = 2000,
            bmpHeight = 1000,
            cropWidthPx = 500f,
            cropHeightPx = 500f,
            scale = 1f,
        )
        assertEquals(250f, x1, 0.01f)
        assertEquals(0f, y1, 0.01f)

        // Zoomed 2x -> scaled 2000x1000 -> maxDx = 750, maxDy = 250
        val (x2, y2) = clampCropOffset(
            offsetX = -900f,
            offsetY = 300f,
            bmpWidth = 2000,
            bmpHeight = 1000,
            cropWidthPx = 500f,
            cropHeightPx = 500f,
            scale = 2f,
        )
        assertEquals(-750f, x2, 0.01f)
        assertEquals(250f, y2, 0.01f)
    }

    @Test
    fun clampCropOffsetSupportsSquareCoverFrame() {
        // 1600x1000 photo inside 1080x1080 (1:1) cover crop box:
        // baseScale = 1080 / 1000 = 1.08 -> scaled 1728x1080 -> maxDx = (1728 - 1080)/2 = 324, maxDy = 0
        val (x, y) = clampCropOffset(
            offsetX = -500f,
            offsetY = 120f,
            bmpWidth = 1600,
            bmpHeight = 1000,
            cropWidthPx = 1080f,
            cropHeightPx = 1080f,
            scale = 1f,
        )
        assertEquals(-324f, x, 0.01f)
        assertEquals(0f, y, 0.01f)
    }
}
