package com.cobo.app.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cobo.app.App
import com.cobo.app.data.Updater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end checks against the live mobile web API. Read-only: every test asserts
 * that the parser can turn a real response into the models the UI consumes.
 */
@RunWith(AndroidJUnit4::class)
class ApiContractTest : DataE2eTest() {

    @Test
    fun config_reportsLoggedInAccount() {
        val config = net { App.repo.config(force = true) }
        assertTrue("config 未返回登录态", config.loggedIn)
        assertTrue("config 未返回 uid", config.uid.isNotBlank())
        assertTrue("config 未返回 st", config.st.isNotBlank())
        assertEquals("session 里的 uid 与 config 不一致", config.uid, App.repo.session?.uid?.ifBlank { config.uid })
    }

    @Test
    fun recommendFeed_parsesStatuses() {
        val page = net { App.repo.container("102803", force = true) }
        assertTrue("推荐流为空", page.items.isNotEmpty())
        page.items.take(5).forEach { status ->
            assertTrue("微博 id 为空", status.id.isNotBlank())
            assertTrue("微博 mid 为空", status.mid.isNotBlank())
            assertNotNull("微博缺少作者", status.user)
            assertTrue("作者名为空", status.user!!.name.isNotBlank())
            assertTrue(
                "微博没有正文、图片或视频",
                status.plainText.isNotBlank() || status.pics.isNotEmpty() || status.video != null
            )
        }
    }

    @Test
    fun followingFeed_parsesStatuses() {
        val uid = net { App.repo.ownUid() }
        val own = net { App.repo.profile(uid, force = true) }
        val page = net { App.repo.friends(force = true) }
        if ((own?.friends ?: 0) == 0) {
            assertTrue("没有关注任何人时关注流应为空", page.items.isEmpty())
            return
        }
        assertTrue("关注流为空", page.items.isNotEmpty())
        page.items.take(5).forEach { status ->
            assertTrue("关注流微博 id 为空", status.id.isNotBlank())
            assertNotNull("关注流微博缺少作者", status.user)
        }
    }

    @Test
    fun circleFeed_doesNotFail() {
        val page = net { App.repo.circle(force = true) }
        page.items.forEach { status ->
            assertTrue("好友圈微博 id 为空", status.id.isNotBlank())
            assertNotNull("好友圈微博缺少作者", status.user)
        }
    }

    @Test
    fun specialFeed_doesNotFail() {
        val page = net { App.repo.special(force = true) }
        page.items.forEach { status ->
            assertTrue("特别关注流微博 id 为空", status.id.isNotBlank())
            assertNotNull("特别关注流微博缺少作者", status.user)
        }
    }

    @Test
    fun detail_matchesFeedItem() {
        val first = net { App.repo.container("102803") }.items.first()
        val detail = net { App.repo.detail(first.id, force = true) }
        assertNotNull("详情接口未返回数据", detail)
        assertEquals("详情 id 与列表不一致", first.id, detail!!.id)
        assertNotNull("详情缺少作者", detail.user)
    }

    @Test
    fun comments_returnParsedPage() {
        val first = net { App.repo.container("102803") }.items.first()
        val page = net { App.repo.comments(first.id, first.mid, force = false) }
        page.items.forEach { comment ->
            assertTrue("评论 id 为空", comment.id.isNotBlank())
            assertNotNull("评论缺少作者", comment.user)
            assertTrue("评论作者名为空", comment.user!!.name.isNotBlank())
        }
        assertTrue("评论分页字段缺失", page.maxId.isNotBlank() || page.items.isEmpty())
    }

    @Test
    fun hotSearch_returnsRankedItems() {
        val hot = net { App.repo.hot(force = false) }
        assertTrue("热搜为空", hot.isNotEmpty())
        hot.forEachIndexed { index, item ->
            assertTrue("热搜标题为空", item.title.isNotBlank())
            assertEquals("热搜排名不连续", index + 1, item.rank)
        }
    }

    @Test
    fun messages_includeSenderAvatar() {
        val list = net { App.repo.messages(1, force = false) }
        list.forEach { item ->
            assertNotNull("私信缺少发送者", item.user)
            assertTrue("私信发送者名为空", item.user!!.name.isNotBlank())
            assertTrue(
                "私信发送者头像为空（回归：avatar_large 未读取）",
                item.user!!.avatar.isNotBlank()
            )
        }
    }

    @Test
    fun ownProfile_hasIdentity() {
        val uid = net { App.repo.ownUid() }
        assertTrue("无法解析当前账号 uid", uid.isNotBlank())
        val user = net { App.repo.profile(uid, force = false) }
        assertNotNull("个人主页接口未返回用户", user)
        assertEquals("个人主页 uid 不一致", uid, user!!.id)
        assertTrue("个人主页缺少昵称", user.name.isNotBlank())
    }

    @Test
    fun userStatuses_parsePage() {
        val uid = net { App.repo.ownUid() }
        val page = net { App.repo.userStatuses(uid, force = false) }
        page.items.forEach { assertTrue("用户微博 id 为空", it.id.isNotBlank()) }
    }

    @Test
    fun search_returnsResults() {
        val page = net { App.repo.search("微博", force = true) }
        assertTrue("搜索“微博”没有返回结果", page.items.isNotEmpty())
        page.items.take(3).forEach { assertTrue("搜索结果 id 为空", it.id.isNotBlank()) }
    }

    @Test
    fun favorites_doesNotFail() {
        val page = net { App.repo.favorites(force = false) }
        page.items.forEach { assertTrue("收藏微博 id 为空", it.id.isNotBlank()) }
    }

    @Test
    fun update_versionCompare_ordersCorrectly() {
        assertTrue("1.0.10 应高于 1.0.9", Updater.compareVersion("1.0.10", "1.0.9") > 0)
        assertTrue("1.1.0-pre 应高于 1.0.9", Updater.compareVersion("1.1.0-pre", "1.0.9") > 0)
        assertTrue("正式版应高于同号预发布", Updater.compareVersion("1.0.0", "1.0.0-pre") > 0)
        assertEquals(0, Updater.compareVersion("v1.0.0", "1.0.0"))
    }

    @Test
    fun update_releaseFeedIsUsable() {
        val current = Updater.currentVersion(App.context)
        assertTrue("读不到本地版本号", current.isNotBlank() && current != "0.0.0")
        val info = net { Updater.latest(current) }
        // 本地就是最新时返回 null；真发了新版就必须比本地新，且带 APK 资产。
        info?.let {
            assertTrue("返回的版本不比本地新", Updater.compareVersion(it.version, current) > 0)
            assertTrue("release 没有 APK 资产", !it.apkUrl.isNullOrBlank())
            assertTrue("release 没有页面地址", it.pageUrl.startsWith("https://github.com/"))
        }
    }
}
