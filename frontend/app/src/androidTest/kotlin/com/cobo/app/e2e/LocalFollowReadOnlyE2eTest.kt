package com.cobo.app.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cobo.app.data.FollowTag
import com.cobo.app.data.FollowTags
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 「本地关注」用选定账号的 Cookie 取数，写操作却会落到当前账号上，所以这条流锁成只读：
 * 卡片和从卡片点进去的详情页都不给转发 / 评论 / 赞，评论和楼中楼的赞与回复同样关掉。
 * 对照组跑推荐流的详情页——那边三项必须还能点，否则这个断言只是「找不到节点」而已。
 */
@RunWith(AndroidJUnit4::class)
class LocalFollowReadOnlyE2eTest : UiE2eTest() {

    @Test
    fun localFollowDetail_isReadOnly_otherFeedsAreNot() {
        assumeTrue("本机没有打「本地」标记", FollowTags.uids(FollowTag.Local).isNotEmpty())

        tapTag("feed_tab_follow")
        clickText("本地关注")
        waitTag("feed_page_1")
        waitTag("feed_card_0")

        openFirstFeedCard()
        waitText("微博正文")
        assertDisabled("detail_repost")
        assertDisabled("detail_comment")
        assertDisabled("detail_like")

        back()
        waitTag("feed_card_0")
        tapTag("feed_tab_recommend")
        waitTag("feed_page_2")

        openFirstFeedCard()
        waitText("微博正文")
        assertTrue("对照：推荐流详情页的转发不可点，说明这里本来就点不了", waitTag("detail_repost").isClickable)
        assertTrue("对照：推荐流详情页的评论不可点", waitTag("detail_comment").isClickable)
        assertTrue("对照：推荐流详情页的赞不可点", waitTag("detail_like").isClickable)
    }

    private fun assertDisabled(tag: String) {
        assertFalse("只读详情页的 [$tag] 仍然可以点击", waitTag(tag).isClickable)
    }
}
