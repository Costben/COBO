package com.cobo.app.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import com.cobo.app.App
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Profile tab and other users' profiles. The follow button is only asserted to exist;
 * actually following is covered by the opt-in WriteActionsTest.
 */
@RunWith(AndroidJUnit4::class)
class ProfileE2eTest : UiE2eTest() {

    @Test
    fun ownProfile_showsIdentityAndStats() {
        tapTag("bottom_tab_profile")
        val uid = io { App.repo.ownUid() }
        val user = io { App.repo.profile(uid) }
        assertTrue("无法解析当前账号 uid", uid.isNotBlank())
        if (user != null && user.name.isNotBlank()) waitText(user.name)
        waitText("粉丝")
        waitText("微博")
        waitText("关注")
    }

    @Test
    fun otherProfile_showsFollowButton() {
        val status = io { App.repo.container("102803") }.items.first()
        val user = status.user ?: throw AssertionError("首条微博没有作者")
        val ownUid = io { App.repo.ownUid() }
        assumeTrue("首条微博作者就是当前账号，无法验证关注按钮", user.id != ownUid)

        waitTag("feed_card_0")
        clickText(user.name)
        device.waitForIdle(1_000)

        waitText("主页")
        waitTag("follow_button")
        waitAny(By.text("关注"), By.text("已关注"))

        back()
        waitGone(By.text("主页"))
        waitText("推荐")
        assertTrue("返回后信息流没有卡片", hasAnyFeedCard())
    }

    @Test
    fun profileOpensSettingsAndReturns() {
        tapTag("bottom_tab_profile")
        tapTag("top_settings")
        waitText("设置")
        back()
        waitTag("bottom_tab_profile")
    }
}
