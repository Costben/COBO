package com.cobo.app.ui

import com.cobo.app.data.AnimationSpeed
import com.cobo.app.ui.components.canonicalAvatarCacheKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpandAndAvatarTest {
    @Test
    fun canonicalAvatarCacheKeyNormalizesWeiboCdnHostAndRotatingSignatureQuery() {
        val timelineUrl = "https://tvax2.sinaimg.cn/crop.0.0.512.512.180/006abcde1234.jpg?KID=imgbed,tva&Expires=1795585000&ssig=AAA111"
        val detailUrl = "https://tvax4.sinaimg.cn/crop.0.0.512.512.180/006abcde1234.jpg?KID=imgbed,tva&Expires=1795589999&ssig=BBB222"
        val otherAvatar = "https://tvax4.sinaimg.cn/crop.0.0.512.512.180/006different.jpg?KID=imgbed,tva&Expires=1795589999&ssig=BBB222"

        assertEquals(
            canonicalAvatarCacheKey(timelineUrl),
            canonicalAvatarCacheKey(detailUrl),
        )
        assertNotEquals(
            canonicalAvatarCacheKey(timelineUrl),
            canonicalAvatarCacheKey(otherAvatar),
        )
    }

    @Test
    fun animationSpeedProvidesThreeOrderedTiers() {
        assertEquals(3, AnimationSpeed.entries.size)
        assertEquals(AnimationSpeed.Standard, AnimationSpeed.fromKey(null))
        assertEquals(AnimationSpeed.Slow, AnimationSpeed.fromKey("slow"))
        assertTrue(AnimationSpeed.Fast.scaleDuration(ExpandEnterMillis) < AnimationSpeed.Standard.scaleDuration(ExpandEnterMillis))
        assertTrue(AnimationSpeed.Standard.scaleDuration(ExpandEnterMillis) < AnimationSpeed.Slow.scaleDuration(ExpandEnterMillis))
    }
}
