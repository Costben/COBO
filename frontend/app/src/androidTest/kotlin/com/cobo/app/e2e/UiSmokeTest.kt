package com.cobo.app.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.StaleObjectException
import com.cobo.app.App
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers the main shell: feed rendering, scrolling, the floating bottom bar and the
 * manual refresh action.
 */
@RunWith(AndroidJUnit4::class)
class UiSmokeTest : UiE2eTest() {

    @Test
    fun shell_hasAllChrome() {
        waitText("推荐")
        waitText("关注")
        assertTrue("缺少搜索入口", existsTag("top_search"))
        assertTrue("缺少刷新入口", existsTag("bottom_refresh"))
        assertTrue("缺少微博 Tab", existsTag("bottom_tab_feed"))
        assertTrue("缺少消息 Tab", existsTag("bottom_tab_messages"))
        assertTrue("缺少我 Tab", existsTag("bottom_tab_profile"))
        assertTrue("缺少发布悬浮球", existsTag("bottom_publish"))
        waitTag("feed_card_0")
    }

    @Test
    fun feed_scrolls() {
        waitTag("feed_card_0")
        assertTrue("首屏没有卡片", device.hasObject(By.res("feed_card_0")))
        repeat(3) { swipeUp() }
        assertFalse("信息流没有滚动，第一张卡片仍在屏幕上", device.hasObject(By.res("feed_card_0")))
        assertTrue("滚动后没有任何卡片", hasAnyFeedCard())
        repeat(4) { swipeDown() }
        waitTag("feed_card_0")
    }

    @Test
    fun bottomTabs_switchAndRestore() {
        tapTag("bottom_tab_messages")
        waitAny(By.res("message_row_0"), By.text("还没有私信"), By.text("私信"))

        tapTag("bottom_tab_profile")
        waitAny(By.text("粉丝"), By.text("加载中…"))

        tapTag("bottom_tab_feed")
        waitTag("feed_card_0")
    }

    @Test
    fun followingTab_loadsFeed() {
        val uid = io { App.repo.ownUid() }
        val own = io { App.repo.profile(uid) }
        assumeTrue("账号没有关注任何人", (own?.friends ?: 0) > 0)

        tapTag("feed_tab_follow")
        // The 关注 segment opens its filter dropdown on tap; close it to see the feed.
        device.pressBack()
        device.waitForIdle(500)
        waitTag("feed_card_0")

        tapTag("feed_tab_recommend")
        waitTag("feed_page_2")
        waitTag("feed_card_0")
    }

    @Test
    fun feedSwipesBetweenCircleAndFollowing() {
        waitTag("feed_page_1")

        swipeRight()
        waitTag("feed_page_0")

        swipeLeft()
        waitTag("feed_page_1")
    }

    @Test
    fun friendsCircleTab_showsPage() {
        tapTag("feed_tab_circle")
        waitTag("feed_page_0")

        // Tapping 关注 also opens its filter menu; close it to see the feed.
        tapTag("feed_tab_follow")
        device.pressBack()
        device.waitForIdle(500)
        waitTag("feed_page_1")

        tapTag("feed_tab_recommend")
        waitTag("feed_page_2")
    }

    @Test
    fun refreshButton_keepsFeedUsable() {
        waitTag("feed_card_0")
        tapTag("bottom_refresh")
        waitTag("feed_card_0")
    }

    @Test
    fun bottomBall_scrollsBackToTopAndRefreshes() {
        waitTag("feed_card_0")
        // Go deep enough that the ride home cannot finish inside one frame or two.
        repeat(12) { swipeUp(0.2f) }
        device.waitForIdle(1_000)
        assertFalse("应当已经滚离顶部", existsTag("feed_card_0", 1_000))

        val ball = waitTag("bottom_refresh")
        ball.click()
        // The ride home is animated: a moment after the tap the first card is still off screen.
        Thread.sleep(150)
        assertFalse("回顶没有动画，直接跳到了顶部", existsTag("feed_card_0", 0))

        // After the glide the list pulls itself down: the refresh header pushes the first card
        // down by roughly the height of the ball. Sample the card, then compare with where it
        // settles once the pull is over.
        var lowestTop = -1
        val deadline = System.currentTimeMillis() + 6_000
        while (System.currentTimeMillis() < deadline) {
            val top = feedCardTop()
            if (top != null && top > lowestTop) lowestTop = top
            Thread.sleep(30)
        }
        device.waitForIdle(1_500)
        val settledTop = feedCardTop() ?: 0
        assertTrue(
            "回顶后没有拉出刷新球（卡片最高被推到 $lowestTop，静止在 $settledTop）",
            lowestTop - settledTop > 80,
        )
    }

    @Test
    fun systemBack_returnsToFeedFromDetail() {
        openFirstFeedCard()
        waitText("微博正文")
        device.pressBack()
        device.waitForIdle()
        waitTag("feed_card_0")
    }

    /** Top of the first card, or null while it is off screen or being recomposed. */
    private fun feedCardTop(): Int? = try {
        device.findObject(By.res("feed_card_0"))?.visibleBounds?.top
    } catch (e: StaleObjectException) {
        null
    }
}
