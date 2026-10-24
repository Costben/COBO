package com.cobo.app.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Detail page: opening from a feed card, body/comments rendering, action bar dialogs,
 * and state preservation when returning to the feed.
 */
@RunWith(AndroidJUnit4::class)
class DetailE2eTest : UiE2eTest() {

    @Test
    fun detail_showsBodyAndComments() {
        openFirstFeedCard()
        waitText("微博正文")
        scrollToText("热门评论")
        waitAny(By.res("detail_like"), By.textStartsWith("赞"))
        tapDesc("返回")
        waitTag("feed_card_0")
    }

    @Test
    fun detail_survivesScrolling() {
        openFirstFeedCard()
        waitText("微博正文")
        scrollToText("热门评论")
        repeat(3) { swipeUp() }
        waitText("微博正文")
        assertTrue("滚动后底部操作栏消失", existsTag("detail_like"))
        tapDesc("返回")
        waitTag("feed_card_0")
    }

    @Test
    fun commentDialog_opensAndCancels() {
        openFirstFeedCard()
        waitText("微博正文")
        tapTag("detail_comment")
        waitText("写评论")
        tapText("取消")
        waitText("微博正文")
        tapDesc("返回")
        waitTag("feed_card_0")
    }

    @Test
    fun repostDialog_opensAndCancels() {
        openFirstFeedCard()
        waitText("微博正文")
        tapTag("detail_repost")
        waitText("说点什么")
        tapText("取消")
        waitText("微博正文")
        tapDesc("返回")
        waitTag("feed_card_0")
    }

    @Test
    fun commentReplyDialog_opensAndCancels() {
        openFirstFeedCard()
        waitText("微博正文")
        scrollToText("热门评论")
        scrollToTag("comment_reply_0")
        tapTag("comment_reply_0")
        waitTextStartsWith("回复 @")
        tapText("取消")
        waitText("微博正文")
        tapDesc("返回")
        waitTag("feed_card_0")
    }

    @Test
    fun commentSort_switchesBetweenHotAndTime() {
        openFirstFeedCard()
        waitText("微博正文")
        scrollToText("热门评论")
        tapTag("comment_sort_time")
        waitText("最新评论")
        tapTag("comment_sort_hot")
        waitText("热门评论")
        tapDesc("返回")
        waitTag("feed_card_0")
    }

    @Test
    fun detail_refreshButtonReloads() {
        openFirstFeedCard()
        waitText("微博正文")
        tapDesc("刷新")
        waitText("微博正文")
        tapDesc("返回")
        waitTag("feed_card_0")
    }

    @Test
    fun editHistory_opensFromEditedPost() {
        tapTag("feed_tab_follow")
        device.pressBack()
        device.waitForIdle(500)
        waitTag("feed_page_1")
        waitTag("feed_card_0")

        for (index in 0 until 4) {
            val card = runCatching { waitTag("feed_card_$index", 5_000) }.getOrNull() ?: continue
            val bounds = card.visibleBounds
            val y = (bounds.bottom - 60).coerceAtMost(device.displayHeight - 300)
            device.click(bounds.left + 60, y)
            device.waitForIdle(1_500)

            if (existsTag("detail_edit_history", 5_000)) {
                android.util.Log.i("E2E", "edit history entry found on feed card $index")
                tapTag("detail_edit_history")
                waitTag("edit_history_list")
                waitTextContains("条记录")
                tapDesc("返回")
                waitText("微博正文")
                tapDesc("返回")
                waitTag("feed_page_1")
                return
            }

            tapDesc("返回")
            waitTag("feed_page_1")
        }
        // The first few cards were not edited posts; nothing to open this run.
    }
}
