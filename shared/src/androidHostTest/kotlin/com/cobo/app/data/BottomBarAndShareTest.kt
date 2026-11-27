package com.cobo.app.data

import com.cobo.app.screen.CachedProfileStatuses
import com.cobo.app.screen.ProfileStatusesCache
import com.cobo.app.ui.components.buildWeiboShareUrl
import com.cobo.app.ui.components.formatShareBodyText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BottomBarAndShareTest {

    @Test
    fun bottomBarOrderNormalizesMissingAndDuplicateKeys() {
        assertEquals(
            listOf(BottomBarItem.Messages, BottomBarItem.Feed, BottomBarItem.Profile, BottomBarItem.Publish),
            BottomBarItem.parse(null),
        )
        assertEquals(
            listOf(BottomBarItem.Feed, BottomBarItem.Publish, BottomBarItem.Messages, BottomBarItem.Profile),
            BottomBarItem.parse("feed,publish,feed,unknown"),
        )
        val custom = listOf(BottomBarItem.Publish, BottomBarItem.Profile, BottomBarItem.Feed, BottomBarItem.Messages)
        assertEquals(custom, BottomBarItem.parse(BottomBarItem.encode(custom)))
        assertEquals("发微博 · 我 · 微博 · 消息", BottomBarItem.summary(custom))
    }

    @Test
    fun profileStatusesCacheRespectsTtlAndLruEviction() {
        val cache = ProfileStatusesCache(maxEntries = 2, ttlMillis = 10_000L)
        val sample = CachedProfileStatuses(
            user = User(id = "u1", name = "测试", avatar = ""),
            items = listOf(Status(id = "s1", mid = "m1", textHtml = "hi", user = null, createdAt = "", source = "", reposts = 0, comments = 0, attitudes = 0)),
            sinceId = "cursor_1",
            end = false,
            cachedAtMs = 1_000L,
        )
        cache.put("v:u1", sample)
        cache.put("v:u2", sample.copy(sinceId = "cursor_2"))
        // Touch u1 so u2 becomes the eldest entry
        assertEquals("cursor_1", cache.get("v:u1", nowMs = 2_000L)?.sinceId)
        cache.put("v:u3", sample.copy(sinceId = "cursor_3"))

        assertNull(cache.get("v:u2", nowMs = 3_000L))
        assertEquals("cursor_1", cache.get("v:u1", nowMs = 3_000L)?.sinceId)
        assertEquals("cursor_3", cache.get("v:u3", nowMs = 3_000L)?.sinceId)
        // Expired after TTL
        assertNull(cache.get("v:u1", nowMs = 20_000L))
    }

    @Test
    fun shareHelpersFormatUrlAndParagraphBreaks() {
        val status = Status(
            id = "1001",
            mid = "5001",
            textHtml = "第一段<br/>第二段<p>第三段 <a href=\"https://weibo.com\">#话题#</a></p>",
            user = User(id = "u1", name = "作者", avatar = ""),
            createdAt = "",
            source = "",
            reposts = 0,
            comments = 0,
            attitudes = 0,
        )
        assertEquals("https://m.weibo.cn/detail/5001", buildWeiboShareUrl(status))
        assertEquals("https://m.weibo.cn/detail/1001", buildWeiboShareUrl(status.copy(mid = "")))
        assertEquals("第一段\n第二段\n第三段 #话题#", formatShareBodyText(status.textHtml))
    }
}
