package com.cobo.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 关注 / 粉丝列表和搜索共用一类卡片，但同一页里还夹着推荐卡，别把人混进来。 */
class RelationParseTest {

    @Test
    fun parsesOnlyTheRelationCardAndKeepsOrder() {
        val root = JSONObject(
            """
            {
              "ok": 1,
              "data": {
                "cards": [
                  {
                    "card_style": 1,
                    "card_type": 11,
                    "card_group": [
                      {
                        "card_type": 24,
                        "card_type_name": "相关用户",
                        "users": [
                          {"id": 111, "screen_name": "推荐的人"}
                        ]
                      }
                    ]
                  },
                  {
                    "card_type": 11,
                    "title": "他的全部粉丝",
                    "card_group": [
                      {
                        "card_type": 10,
                        "desc1": "第一个人的简介",
                        "user": {
                          "id": 222,
                          "screen_name": "第一个人",
                          "avatar_hd": "https://example.com/a.jpg",
                          "followers_count": "69.9万",
                          "follow_count": 6,
                          "verified": true,
                          "description": "接口自带的简介"
                        }
                      },
                      {
                        "card_type": 10,
                        "user": {
                          "id": 333,
                          "screen_name": "第二个人",
                          "profile_image_url": "https://example.com/b.jpg",
                          "followers_count": 12
                        }
                      },
                      {
                        "card_type": 10,
                        "user": {
                          "id": 222,
                          "screen_name": "重复出现的第一个人"
                        }
                      }
                    ]
                  }
                ]
              }
            }
            """.trimIndent()
        )

        val users = parseRelationUsers(root)
        assertEquals(2, users.size)
        val first = users.first()
        assertEquals("222", first.id)
        assertEquals("第一个人", first.name)
        assertEquals("https://example.com/a.jpg", first.avatar)
        assertEquals(699000, first.followers)
        assertEquals(6, first.friends)
        assertTrue(first.verified)
        assertEquals("第一个人的简介", first.description)

        assertEquals("333", users[1].id)
        assertEquals("https://example.com/b.jpg", users[1].avatar)
        assertEquals(12, users[1].followers)
    }

    @Test
    fun ignoresPagesWithoutRelationCard() {
        val root = JSONObject(
            """
            {
              "ok": 1,
              "data": {
                "cards": [
                  {"card_style": 1, "card_type": 11, "card_group": []},
                  {"card_type": 42, "desc": "推荐关注"}
                ]
              }
            }
            """.trimIndent()
        )
        assertTrue(parseRelationUsers(root).isEmpty())
    }

    @Test
    fun emptyPayloadReturnsEmptyList() {
        assertTrue(parseRelationUsers(JSONObject("""{"ok":0}""")).isEmpty())
        assertTrue(parseRelationUsers(null).isEmpty())
    }
}
