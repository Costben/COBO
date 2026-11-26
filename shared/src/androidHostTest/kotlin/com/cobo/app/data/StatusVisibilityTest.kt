package com.cobo.app.data

import com.cobo.app.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusVisibilityTest {

    @Test
    fun parsesPublicAndIgnoresNonVisibilityTitles() {
        val plain = Status.from(
            JSONObject("""{"id":"1","text":"hi","visible":{"type":0,"list_id":0}}""")
        )
        assertNotNull(plain?.visibility)
        assertEquals(0, plain?.visibility?.type)
        assertEquals("公开", plain?.visibility?.label)
        assertTrue(plain?.visibility?.isPublic == true)

        val pinned = Status.from(
            JSONObject("""{"id":"2","text":"hi","visible":{"type":0,"list_id":0},"title":{"text":"置顶"}}""")
        )
        assertEquals(0, pinned?.visibility?.type)
        assertEquals("公开", pinned?.visibility?.label)
    }

    @Test
    fun parsesOwnPrivateAndFriendsCircle() {
        val selfOnly = Status.from(
            JSONObject("""{"id":"3","text":"secret","visible":{"type":1,"list_id":0},"title":{"text":"仅自己可见"}}""")
        )
        assertEquals(1, selfOnly?.visibility?.type)
        assertEquals("仅自己可见", selfOnly?.visibility?.label)
        assertFalse(selfOnly?.visibility?.isPublic == true)

        val friendsOnly = Status.from(
            JSONObject("""{"id":"4","text":"circle","visible":{"type":6,"list_id":3,"list_idstr":"3"},"title":{"text":"仅好友可见"}}""")
        )
        assertEquals(6, friendsOnly?.visibility?.type)
        assertEquals("仅好友可见", friendsOnly?.visibility?.label)
        assertEquals("3", friendsOnly?.visibility?.listId)
        assertFalse(friendsOnly?.visibility?.isPublic == true)
    }

    @Test
    fun parsesFallbackLabelsWithoutTitle() {
        val closeFriends = parseStatusVisibility(JSONObject("""{"visible":{"type":2,"list_id":0}}"""))
        assertEquals(StatusVisibility(type = 2, label = "密友可见"), closeFriends)

        val group = parseStatusVisibility(JSONObject("""{"visible":{"type":3,"list_id":42}}"""))
        assertEquals(StatusVisibility(type = 3, label = "指定分组可见", listId = "42"), group)

        val fans = parseStatusVisibility(JSONObject("""{"visible":{"type":10,"list_id":0}}"""))
        assertEquals(StatusVisibility(type = 10, label = "仅粉丝可见"), fans)

        val titleOnly = parseStatusVisibility(JSONObject("""{"title":{"text":"仅好友可见"}}"""))
        assertEquals(StatusVisibility(type = 6, label = "仅好友可见"), titleOnly)

        val noVisibility = parseStatusVisibility(JSONObject("""{"title":{"text":"置顶"}}"""))
        assertNull(noVisibility)
    }

    @Test
    fun snapshotRoundTripsVisibilityAndKeepsLegacyNullSafe() {
        val original = Status(
            id = "99",
            mid = "99",
            textHtml = "hello",
            createdAt = "",
            source = "iPhone",
            user = User(id = "10", name = "测试", avatar = ""),
            visibility = StatusVisibility(type = 6, label = "仅好友可见", listId = "3"),
        )
        val restored = statusFromSnapshot(statusToSnapshot(original))
        assertEquals(original.visibility, restored?.visibility)

        val legacy = statusFromSnapshot(JSONObject("""{"id":"100","mid":"100","text":"old"}"""))
        assertNull(legacy?.visibility)
    }
}
