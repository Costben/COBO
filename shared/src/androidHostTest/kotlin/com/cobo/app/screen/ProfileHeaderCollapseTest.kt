package com.cobo.app.screen

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileHeaderCollapseTest {

    @Test
    fun collapseDeltaConsumesUpwardScrollAndClampsAtMaxCollapse() {
        val (c1, d1) = ProfileHeaderCollapseMath.collapseDelta(
            currentCollapsePx = 0f,
            maxCollapsePx = 300f,
            deltaY = -120f,
        )
        assertEquals(120f, c1, 0.001f)
        assertEquals(-120f, d1, 0.001f)

        val (c2, d2) = ProfileHeaderCollapseMath.collapseDelta(
            currentCollapsePx = 240f,
            maxCollapsePx = 300f,
            deltaY = -150f,
        )
        assertEquals(300f, c2, 0.001f)
        assertEquals(-60f, d2, 0.001f)

        val (cNoop, dNoop) = ProfileHeaderCollapseMath.collapseDelta(
            currentCollapsePx = 100f,
            maxCollapsePx = 300f,
            deltaY = 50f,
        )
        assertEquals(100f, cNoop, 0.001f)
        assertEquals(0f, dNoop, 0.001f)
    }

    @Test
    fun expandDeltaRestoresHeaderAndHandsRemainingPullToRefresh() {
        val (e1, d1) = ProfileHeaderCollapseMath.expandDelta(
            currentCollapsePx = 100f,
            deltaY = 140f,
        )
        assertEquals(0f, e1, 0.001f)
        assertEquals(100f, d1, 0.001f)

        val (eZero, dZero) = ProfileHeaderCollapseMath.expandDelta(
            currentCollapsePx = 0f,
            deltaY = 80f,
        )
        assertEquals(0f, eZero, 0.001f)
        assertEquals(0f, dZero, 0.001f)
    }
}
