package com.cobo.app.data

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import com.cobo.app.screen.CachedProfileStatuses
import com.cobo.app.screen.ProfileStatusesCache
import com.cobo.app.ui.components.ImageGridLayout
import com.cobo.app.ui.components.ShareWidthMaxDp
import com.cobo.app.ui.components.ShareWidthMinDp
import com.cobo.app.ui.components.buildShareRichText
import com.cobo.app.ui.components.buildWeiboShareUrl
import com.cobo.app.ui.components.formatShareBodyText
import com.cobo.app.ui.components.formatShareDateTime
import com.cobo.app.ui.components.formatShareMastheadDate
import com.cobo.app.ui.components.resolveImageGridLayout
import com.cobo.app.ui.components.resolveSharePosterDensity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun shareDateFormatterAlwaysEmitsAbsoluteDates() {
        // 2026-11-27 18:00:00 +0800
        val fixedNow = 1795687200000L
        val rfcDate = formatShareDateTime("Fri Nov 27 17:42:00 +0800 2026", fixedNow)
        assertTrue("Expected yyyy-MM-dd HH:mm, got $rfcDate", Regex("""^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$""").matches(rfcDate))
        assertFalse(rfcDate.contains("今天"))
        assertFalse(rfcDate.contains("分钟前"))

        val todayRel = formatShareDateTime("今天 09:05", fixedNow)
        assertTrue("Expected absolute date for 今天, got $todayRel", todayRel.endsWith(" 09:05"))
        assertFalse(todayRel.contains("今天"))

        val yesterdayRel = formatShareDateTime("昨天 22:15", fixedNow)
        assertTrue("Expected absolute date for 昨天, got $yesterdayRel", yesterdayRel.endsWith(" 22:15"))
        assertFalse(yesterdayRel.contains("昨天"))

        val justNow = formatShareDateTime("刚刚", fixedNow)
        assertTrue("Expected yyyy-MM-dd HH:mm for 刚刚, got $justNow", Regex("""^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$""").matches(justNow))

        val masthead = formatShareMastheadDate("2026-11-27 17:42:00", fixedNow)
        assertEquals("2026.11.27", masthead)

        val rich = buildShareRichText("晚霞很美 #秋日扫街# @Cobo客户端", Color.Blue)
        assertEquals(2, rich.spanStyles.size)
        assertEquals("#秋日扫街#", rich.text.substring(rich.spanStyles[0].start, rich.spanStyles[0].end))
        assertEquals("@Cobo客户端", rich.text.substring(rich.spanStyles[1].start, rich.spanStyles[1].end))

        val base = Density(density = 3.0f, fontScale = 1.1f)
        assertEquals(base, resolveSharePosterDensity(base, containerWidthPx = 960, targetWidthDp = null))
        val wider = resolveSharePosterDensity(base, containerWidthPx = 960, targetWidthDp = 480)
        assertEquals(2.0f, wider.density, 0.001f)
        assertEquals(1.1f, wider.fontScale, 0.001f)
        val clampedHigh = resolveSharePosterDensity(base, containerWidthPx = 960, targetWidthDp = 9999)
        assertEquals(960f / ShareWidthMaxDp, clampedHigh.density, 0.001f)
        val clampedLow = resolveSharePosterDensity(base, containerWidthPx = 960, targetWidthDp = 100)
        assertEquals(960f / ShareWidthMinDp, clampedLow.density, 0.001f)
    }

    @Test
    fun retweetImageCompactUsesNineGridTileSizing() {
        assertEquals(ImageGridLayout(totalSlotsPerRow = 3, itemsPerRow = 3), resolveImageGridLayout(1, true))
        assertEquals(ImageGridLayout(totalSlotsPerRow = 3, itemsPerRow = 3), resolveImageGridLayout(2, true))
        assertEquals(ImageGridLayout(totalSlotsPerRow = 3, itemsPerRow = 3), resolveImageGridLayout(3, true))
        assertEquals(ImageGridLayout(totalSlotsPerRow = 3, itemsPerRow = 2), resolveImageGridLayout(4, true))
        assertEquals(ImageGridLayout(totalSlotsPerRow = 3, itemsPerRow = 3), resolveImageGridLayout(9, true))

        assertEquals(ImageGridLayout(totalSlotsPerRow = 2, itemsPerRow = 2), resolveImageGridLayout(2, false))
        assertEquals(ImageGridLayout(totalSlotsPerRow = 2, itemsPerRow = 2), resolveImageGridLayout(4, false))
        assertEquals(ImageGridLayout(totalSlotsPerRow = 3, itemsPerRow = 3), resolveImageGridLayout(5, false))
    }

    @Test
    fun localBackupRoundTripsRetweetImageCompact() {
        val bundle = LocalBackupBundle(
            exportedAt = 1234L,
            appVersion = "1.2.10",
            settings = SettingsBackup(retweetImageCompact = true),
        )
        val decoded = decodeLocalBackupJson(encodeLocalBackupJson(bundle))
        assertEquals(true, decoded.settings?.retweetImageCompact)
    }
}
