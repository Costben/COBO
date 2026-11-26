package com.cobo.app.screen

import com.cobo.app.ui.LongImageAspect
import com.cobo.app.ui.isLongImageAspect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LongImageMappingTest {

    @Test
    fun isLongImageAspectMatchesThreshold() {
        assertFalse(isLongImageAspect(null))
        assertFalse(isLongImageAspect(LongImageAspect))
        assertFalse(isLongImageAspect(1f))
        assertTrue(isLongImageAspect(LongImageAspect - 0.01f))
        assertTrue(isLongImageAspect(9f / 30f))
    }

    @Test
    fun scrollLeadsAndReachesCenterBeforeShrinkFinishes() {
        assertEquals(0f, longImageScrollFactor(0f, needsScroll = true), 0.0001f)
        assertEquals(0f, longImageShrinkFactor(0f, needsScroll = true), 0.0001f)

        // Early exit: scrolls smoothly toward thumbnail center before shrink starts
        val scrollEarly = longImageScrollFactor(0.18f, needsScroll = true)
        val shrinkEarly = longImageShrinkFactor(0.18f, needsScroll = true)
        assertEquals(0.25f, scrollEarly, 0.0001f)
        assertEquals(0f, shrinkEarly, 0.0001f)

        // By 0.72, scroll is locked at thumbnail center while shrink continues cleanly
        assertEquals(1f, longImageScrollFactor(0.72f, needsScroll = true), 0.0001f)
        val shrinkAtScrollEnd = longImageShrinkFactor(0.72f, needsScroll = true)
        assertTrue(shrinkAtScrollEnd in 0.6f..0.7f)

        // At completion both reach 1.0
        assertEquals(1f, longImageScrollFactor(1f, needsScroll = true), 0.0001f)
        assertEquals(1f, longImageShrinkFactor(1f, needsScroll = true), 0.0001f)
    }

    @Test
    fun noScrollRequiredShrinksDirectlyWithProgress() {
        assertEquals(1f, longImageScrollFactor(0f, needsScroll = false), 0.0001f)
        assertEquals(0f, longImageShrinkFactor(0f, needsScroll = false), 0.0001f)
        assertEquals(0.4f, longImageShrinkFactor(0.4f, needsScroll = false), 0.0001f)
        assertEquals(1f, longImageShrinkFactor(1f, needsScroll = false), 0.0001f)
    }
}
