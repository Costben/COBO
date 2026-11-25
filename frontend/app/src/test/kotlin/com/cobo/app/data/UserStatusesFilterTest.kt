package com.cobo.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserStatusesFilterTest {

    private fun statusOf(id: String, uid: String, retweetedUid: String? = null): Status {
        val retweeted = retweetedUid?.let { rUid ->
            Status(
                id = "${id}_rt",
                mid = "${id}_rt",
                textHtml = "原博",
                createdAt = "",
                source = "",
                user = User(id = rUid, name = "原博主$rUid", avatar = ""),
            )
        }
        return Status(
            id = id,
            mid = id,
            textHtml = "正文 $id",
            createdAt = "",
            source = "",
            user = User(id = uid, name = "用户$uid", avatar = ""),
            retweeted = retweeted,
        )
    }

    @Test
    fun keepsTargetUserPostsIncludingRepostsAndPreservesCursor() {
        val page = FeedPage(
            items = listOf(
                statusOf("101", "10001"),
                statusOf("102", "10001", retweetedUid = "20002"),
            ),
            sinceId = "cursor_2",
        )

        val filtered = filterUserStatusesPage("10001", page)

        assertEquals(listOf("101", "102"), filtered.items.map { it.id })
        assertEquals("cursor_2", filtered.sinceId)
    }

    @Test
    fun dropsTrailingRecommendedStatusesAndStopsPagination() {
        val page = FeedPage(
            items = listOf(
                statusOf("101", "10001"),
                statusOf("102", "10001"),
                statusOf("201", "20001"),
                statusOf("202", "20002"),
                statusOf("203", "20003"),
            ),
            sinceId = "rec_cursor",
        )

        val filtered = filterUserStatusesPage("10001", page)

        assertEquals(listOf("101", "102"), filtered.items.map { it.id })
        assertNull(filtered.sinceId)
    }

    @Test
    fun returnsEmptyAndNullCursorWhenPageHasOnlyRecommendedStatuses() {
        val page = FeedPage(
            items = listOf(
                statusOf("201", "20001"),
                statusOf("202", "20002"),
            ),
            sinceId = "rec_cursor_next",
        )

        val filtered = filterUserStatusesPage("10001", page)

        assertTrue(filtered.items.isEmpty())
        assertNull(filtered.sinceId)
    }

    @Test
    fun dropsSingleForeignStatusWithoutStoppingRemainingPages() {
        val page = FeedPage(
            items = listOf(
                statusOf("101", "10001"),
                statusOf("201", "20001"),
            ),
            sinceId = "cursor_2",
        )

        val filtered = filterUserStatusesPage("10001", page)

        assertEquals(listOf("101"), filtered.items.map { it.id })
        assertEquals("cursor_2", filtered.sinceId)
    }

    @Test
    fun userFollowingParsesBooleanAndNumericFlags() {
        val boolTrue = User.from(JSONObject("""{"id":"1","screen_name":"A","following":true}"""))!!
        val boolFalse = User.from(JSONObject("""{"id":"2","screen_name":"B","following":false}"""))!!
        val intTrue = User.from(JSONObject("""{"id":"3","screen_name":"C","following":1}"""))!!
        val intFalse = User.from(JSONObject("""{"id":"4","screen_name":"D","following":0}"""))!!
        val strTrue = User.from(JSONObject("""{"id":"5","screen_name":"E","following":"1"}"""))!!

        assertTrue(boolTrue.following)
        assertFalse(boolFalse.following)
        assertTrue(intTrue.following)
        assertFalse(intFalse.following)
        assertTrue(strTrue.following)
    }

    @Test
    fun statusReportUrlBuildsType1ReportEndpoint() {
        val url = com.cobo.app.ui.components.statusReportUrl("5123456789012345")
        assertEquals(
            "https://service.account.weibo.com/reportspam?rid=5123456789012345&from=10106&type=1&url=%2Fdetail%2F5123456789012345&bottomnav=1&wvr=5",
            url,
        )
    }
}
