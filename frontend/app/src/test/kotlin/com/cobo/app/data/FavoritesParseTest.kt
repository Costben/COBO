package com.cobo.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoritesParseTest {

    @Test
    fun statusSnapshotPreservesFavoritedAndFields() {
        val status = Status(
            id = "5201",
            mid = "5201",
            textHtml = "本地收藏内容",
            createdAt = "Mon Nov 16 09:00:00 +0800 2026",
            source = "微博",
            user = User(id = "1001", name = "测试用户", avatar = ""),
            pics = listOf(Pic(thumb = "https://example.com/a.jpg", large = "https://example.com/b.jpg")),
            favorited = true,
        )

        val json = statusToSnapshot(status)
        val restored = statusFromSnapshot(json)

        assertEquals("5201", restored?.id)
        assertEquals("测试用户", restored?.user?.name)
        assertTrue(restored?.favorited == true)
        assertEquals(1, restored?.pics?.size)
    }

    @Test
    fun statusFromWeiboJsonBuildsPicFallbackFromPicIds() {
        val obj = JSONObject(
            """
            {
              "id": "5202",
              "mid": "5202",
              "text": "带图收藏",
              "user": {"id": "1002", "screen_name": "用户B"},
              "pic_ids": ["pid001"]
            }
            """.trimIndent(),
        )

        val status = Status.from(obj)

        assertEquals("5202", status?.id)
        assertEquals(1, status?.pics?.size)
        assertEquals("https://wx1.sinaimg.cn/large/pid001.jpg", status?.pics?.firstOrNull()?.large)
        assertFalse(status?.favorited == true)
    }

    @Test
    fun bottomLeftActionFromKeyResolvesAllAndDefault() {
        assertEquals(BottomLeftAction.Refresh, BottomLeftAction.fromKey(null))
        assertEquals(BottomLeftAction.Refresh, BottomLeftAction.fromKey("unknown"))
        assertEquals(BottomLeftAction.Refresh, BottomLeftAction.fromKey("refresh"))
        assertEquals(BottomLeftAction.Search, BottomLeftAction.fromKey("search"))
        assertEquals(BottomLeftAction.Favorites, BottomLeftAction.fromKey("favorites"))
    }
}
