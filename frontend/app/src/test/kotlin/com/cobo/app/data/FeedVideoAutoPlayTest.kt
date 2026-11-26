package com.cobo.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedVideoAutoPlayTest {

    @Test
    fun shouldAutoPlayRequiresAllGuards() {
        assertTrue(
            shouldAutoPlayFeedVideo(
                settingEnabled = true,
                isWifi = true,
                isForeground = true,
                isRouteActive = true,
                isLive = false,
                url = "https://f.video.weibocdn.com/sample.mp4",
                hasFloatingVideo = false,
            ),
        )
        assertFalse(
            shouldAutoPlayFeedVideo(
                settingEnabled = false,
                isWifi = true,
                isForeground = true,
                isRouteActive = true,
                isLive = false,
                url = "https://f.video.weibocdn.com/sample.mp4",
                hasFloatingVideo = false,
            ),
        )
        assertFalse(
            shouldAutoPlayFeedVideo(
                settingEnabled = true,
                isWifi = false,
                isForeground = true,
                isRouteActive = true,
                isLive = false,
                url = "https://f.video.weibocdn.com/sample.mp4",
                hasFloatingVideo = false,
            ),
        )
        assertFalse(
            shouldAutoPlayFeedVideo(
                settingEnabled = true,
                isWifi = true,
                isForeground = false,
                isRouteActive = true,
                isLive = false,
                url = "https://f.video.weibocdn.com/sample.mp4",
                hasFloatingVideo = false,
            ),
        )
        assertFalse(
            shouldAutoPlayFeedVideo(
                settingEnabled = true,
                isWifi = true,
                isForeground = true,
                isRouteActive = false,
                isLive = false,
                url = "https://f.video.weibocdn.com/sample.mp4",
                hasFloatingVideo = false,
            ),
        )
        assertFalse(
            shouldAutoPlayFeedVideo(
                settingEnabled = true,
                isWifi = true,
                isForeground = true,
                isRouteActive = true,
                isLive = true,
                url = "https://weibo.com/l/wblive/m/show/123",
                hasFloatingVideo = false,
            ),
        )
        assertFalse(
            shouldAutoPlayFeedVideo(
                settingEnabled = true,
                isWifi = true,
                isForeground = true,
                isRouteActive = true,
                isLive = false,
                url = " ",
                hasFloatingVideo = false,
            ),
        )
        assertFalse(
            shouldAutoPlayFeedVideo(
                settingEnabled = true,
                isWifi = true,
                isForeground = true,
                isRouteActive = true,
                isLive = false,
                url = "https://f.video.weibocdn.com/sample.mp4",
                hasFloatingVideo = true,
            ),
        )
    }

    @Test
    fun computeVisibleRatioHandlesVerticalAndHorizontalClipping() {
        assertEquals(
            1f,
            computeVisibleRatio(
                leftPx = 20f,
                topPx = 300f,
                rightPx = 340f,
                bottomPx = 480f,
                viewportLeftPx = 0f,
                viewportTopPx = 100f,
                viewportRightPx = 360f,
                viewportBottomPx = 800f,
            ),
            0.001f,
        )
        assertEquals(
            0.5f,
            computeVisibleRatio(
                leftPx = 20f,
                topPx = 0f,
                rightPx = 340f,
                bottomPx = 200f,
                viewportLeftPx = 0f,
                viewportTopPx = 100f,
                viewportRightPx = 360f,
                viewportBottomPx = 800f,
            ),
            0.001f,
        )
        assertEquals(
            0f,
            computeVisibleRatio(
                leftPx = 400f,
                topPx = 300f,
                rightPx = 720f,
                bottomPx = 480f,
                viewportLeftPx = 0f,
                viewportTopPx = 100f,
                viewportRightPx = 360f,
                viewportBottomPx = 800f,
            ),
            0.001f,
        )
    }

    @Test
    fun pickBestCandidateSelectsMostCenteredEligibleCard() {
        val candidates = listOf(
            FeedVideoCandidate("top_barely", 0f, 100f, 360f, 300f, visibleRatio = 0.55f),
            FeedVideoCandidate("upper", 0f, 180f, 360f, 340f, visibleRatio = 1f),
            FeedVideoCandidate("center", 0f, 360f, 360f, 520f, visibleRatio = 0.95f),
        )
        assertEquals(
            "center",
            pickBestFeedVideoCandidate(candidates, viewportTopPx = 100f, viewportBottomPx = 800f),
        )
        assertNull(
            pickBestFeedVideoCandidate(
                listOf(FeedVideoCandidate("low", 0f, 360f, 360f, 520f, visibleRatio = 0.4f)),
                viewportTopPx = 100f,
                viewportBottomPx = 800f,
            ),
        )
    }

    @Test
    fun pickBestCandidateAppliesHysteresisForActiveCard() {
        val candidates = listOf(
            FeedVideoCandidate("active", 0f, 310f, 360f, 470f, visibleRatio = 0.50f),
            FeedVideoCandidate("next", 0f, 360f, 360f, 520f, visibleRatio = 1f),
        )
        assertEquals(
            "active",
            pickBestFeedVideoCandidate(
                candidates = candidates,
                viewportTopPx = 100f,
                viewportBottomPx = 800f,
                currentActiveKey = "active",
            ),
        )

        val scrolledFurther = listOf(
            FeedVideoCandidate("active", 0f, 60f, 360f, 220f, visibleRatio = 0.30f),
            FeedVideoCandidate("next", 0f, 360f, 360f, 520f, visibleRatio = 1f),
        )
        assertEquals(
            "next",
            pickBestFeedVideoCandidate(
                candidates = scrolledFurther,
                viewportTopPx = 100f,
                viewportBottomPx = 800f,
                currentActiveKey = "active",
            ),
        )
    }
}
