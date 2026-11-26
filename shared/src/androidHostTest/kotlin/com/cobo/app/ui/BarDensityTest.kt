package com.cobo.app.ui

import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Test

class BarDensityTest {

    @Test
    fun keepsColorOsReferenceDevicesUnchanged() {
        val pkt110 = computeBarDensity(
            galleryDensity = Density(560f / 160f, 1f),
            platformDensity = Density(620f / 160f, 1.1f),
            shortEdgePx = 1240,
            displayDensity = 620f / 160f,
            smallestWidthDp = 1240f / (620f / 160f),
            isDefaultDisplay = true,
        )
        assertEquals(560f / 160f, pkt110.density, 0.0001f)
        assertEquals(1.1f, pkt110.fontScale, 0.0001f)

        val plk110 = computeBarDensity(
            galleryDensity = Density(565f / 160f, 1f),
            platformDensity = Density(620f / 160f, 1f),
            shortEdgePx = 1272,
            displayDensity = 620f / 160f,
            smallestWidthDp = 1272f / (620f / 160f),
            isDefaultDisplay = true,
        )
        assertEquals(565f / 160f, plk110.density, 0.0001f)
    }

    @Test
    fun lifts2kNonColorOsPhoneFromLowLcdDensityTo360dpShortEdge() {
        val resolved = computeBarDensity(
            galleryDensity = Density(440f / 160f, 1f),
            platformDensity = Density(560f / 160f, 1f),
            shortEdgePx = 1440,
            displayDensity = 440f / 160f,
            smallestWidthDp = 1440f / (440f / 160f),
            isDefaultDisplay = true,
        )
        assertEquals(640f / 160f, resolved.density, 0.0001f)
    }

    @Test
    fun usesVirtualDisplayOwnShortEdgeForSecondaryDisplays() {
        val qhdVirtual = computeBarDensity(
            galleryDensity = Density(560f / 160f, 1f),
            platformDensity = Density(440f / 160f, 1f),
            shortEdgePx = 1440,
            displayDensity = 440f / 160f,
            smallestWidthDp = 1440f / (440f / 160f),
            isDefaultDisplay = false,
        )
        assertEquals(4.0f, qhdVirtual.density, 0.0001f)

        val fhdVirtual = computeBarDensity(
            galleryDensity = Density(560f / 160f, 1f),
            platformDensity = Density(420f / 160f, 1f),
            shortEdgePx = 1080,
            displayDensity = 420f / 160f,
            smallestWidthDp = 1080f / (420f / 160f),
            isDefaultDisplay = false,
        )
        assertEquals(3.0f, fhdVirtual.density, 0.0001f)
    }

    @Test
    fun doesNotForcePhoneScaleOnTablets() {
        val tablet = computeBarDensity(
            galleryDensity = Density(320f / 160f, 1f),
            platformDensity = Density(320f / 160f, 1f),
            shortEdgePx = 1600,
            displayDensity = 320f / 160f,
            smallestWidthDp = 800f,
            isDefaultDisplay = true,
        )
        assertEquals(2.0f, tablet.density, 0.0001f)
    }
}
