package com.cobo.app.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cobo.app.App
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opt-in tests that really mutate the signed-in account. Run with
 * `-e destructive true`; each test restores the original state in a finally block.
 */
@RunWith(AndroidJUnit4::class)
class WriteActionsE2eTest : UiE2eTest() {

    @Test
    fun like_togglesAndRestores() {
        assumeTrue("写入测试未启用：加 -e destructive true", destructive)

        waitTag("feed_card_0")
        openFirstFeedCard()
        waitText("微博正文")

        val like = waitTag("detail_like")
        val label = like.text.orEmpty()
        assumeFalse("该微博已点赞，跳过以免改动既有数据：$label", label.startsWith("已赞"))

        val id = App.store.history().firstOrNull()?.id
            ?: throw AssertionError("详情页没有写入浏览历史，无法确认微博 id")

        tapTag("detail_like")
        waitText("已赞")

        try {
            tapTag("detail_like")
            waitTextStartsWith("赞 ")
        } finally {
            // 无论断言是否通过，都尽量把服务端状态还原
            val current = io { App.repo.detail(id, force = true) }
            if (current?.liked == true) {
                runCatching { io { App.repo.like(id, true) } }
            }
        }

        val finalState = io { App.repo.detail(id, force = true) }
        assertFalse("点赞状态没有还原", finalState?.liked == true)
    }

    @Test
    fun follow_togglesAndRestores() {
        assumeTrue("写入测试未启用：加 -e destructive true", destructive)

        val ownUid = io { App.repo.ownUid() }
        val target = io { App.repo.container("102803") }
            .items.firstNotNullOfOrNull { it.user?.takeIf { user -> user.id != ownUid } }
            ?: throw AssertionError("推荐流里没有其他用户")

        val before = io { App.repo.profile(target.id, force = true) }
            ?: throw AssertionError("无法读取目标用户主页")
        assumeFalse("已关注该用户，跳过以免改动既有数据", before.following)

        try {
            assertTrue("关注接口返回失败", io { App.repo.follow(target.id, true) })
            val after = io { App.repo.profile(target.id, force = true) }
            assertTrue("关注后主页仍显示未关注", after?.following == true)
        } finally {
            runCatching { io { App.repo.follow(target.id, false) } }
        }

        val restored = io { App.repo.profile(target.id, force = true) }
        assertFalse("取消关注后主页仍显示已关注", restored?.following == true)
    }
}
