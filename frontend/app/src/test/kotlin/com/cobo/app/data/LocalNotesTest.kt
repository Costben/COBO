package com.cobo.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNotesTest {

    private fun status(
        id: String,
        uid: String = "u1",
        name: String = "原昵称$id",
        html: String = "正文 $id",
        pics: List<Pic> = emptyList(),
        video: VideoInfo? = null,
        retweeted: Status? = null,
    ) = Status(
        id = id,
        mid = id,
        textHtml = html,
        createdAt = "Mon Nov 17 10:00:00 +0800 2026",
        source = "微博",
        user = User(id = uid, name = name, avatar = "https://example.com/a.jpg"),
        pics = pics,
        video = video,
        retweeted = retweeted,
    )

    @Test
    fun userRemarkNormalizationAndJsonRoundtripCleanOutBlanks() {
        assertEquals("", normalizeUserRemark("  \r\n  "))
        assertEquals("备注 甲", normalizeUserRemark("  备注\n甲  "))
        assertEquals(24, normalizeUserRemark("一二三四五六七八九十一二三四五六七八九十一二三四五六").length)

        val encoded = encodeUserRemarks(
            mapOf(
                "100" to "同学",
                "" to "无效",
                "200" to "   ",
                "300" to "摄影博主\n推荐",
            ),
        )
        val decoded = decodeUserRemarks(encoded)
        assertEquals(mapOf("100" to "同学", "300" to "摄影博主 推荐"), decoded)
    }

    @Test
    fun readLaterSnapshotRoundtripPreservesMediaAndRetweetAndDedups() {
        val quoted = status(id = "q1", uid = "u2", name = "原作者", html = "原博内容")
        val fullPost = status(
            id = "s1",
            uid = "u1",
            html = "<b>离线正文</b>",
            pics = listOf(Pic("t1", "l1", width = 800, height = 600, live = "https://v/live.mov")),
            video = VideoInfo(title = "视频", cover = "c1", url = "https://v/1.mp4", durationSec = 90),
            retweeted = quoted,
        )
        val arr = encodeReadLaterEntries(
            listOf(
                ReadLaterEntry(fullPost, savedAt = 1_700_000_000_000L, read = true),
                ReadLaterEntry(fullPost, savedAt = 1_600_000_000_000L, read = false),
            ),
        )
        val decoded = decodeReadLaterEntries(arr)
        assertEquals(1, decoded.size)
        val restored = decoded.first()
        assertTrue(restored.read)
        assertEquals(1_700_000_000_000L, restored.savedAt)
        assertEquals("<b>离线正文</b>", restored.status.textHtml)
        assertEquals(1, restored.status.pics.size)
        assertEquals(90, restored.status.video?.durationSec)
        assertEquals("原博内容", restored.status.retweeted?.textHtml)
    }

    @Test
    fun filterReadLaterSupportsReadStateAndRemarkKeywordSearch() {
        val s1 = status("1", uid = "101", name = "张三", html = "讨论 Kotlin 协程")
        val s2 = status(
            "2",
            uid = "102",
            name = "李四",
            html = "转发推荐",
            retweeted = status("q2", uid = "103", name = "王五", html = "Compose 技巧"),
        )
        val entries = listOf(
            ReadLaterEntry(s1, savedAt = 200L, read = false),
            ReadLaterEntry(s2, savedAt = 100L, read = true),
        )

        val unread = filterReadLaterEntries(entries, ReadLaterFilter.Unread, "") { null }
        assertEquals(listOf("1"), unread.map { it.status.id })

        val readOnly = filterReadLaterEntries(entries, ReadLaterFilter.Read, "Compose") { null }
        assertEquals(listOf("2"), readOnly.map { it.status.id })

        val byRemark = filterReadLaterEntries(entries, ReadLaterFilter.All, "大牛") { uid ->
            if (uid == "101") "技术大牛" else null
        }
        assertEquals(listOf("1"), byRemark.map { it.status.id })
        assertFalse(filterReadLaterEntries(entries, ReadLaterFilter.Unread, "不存在") { null }.isNotEmpty())
    }
}
