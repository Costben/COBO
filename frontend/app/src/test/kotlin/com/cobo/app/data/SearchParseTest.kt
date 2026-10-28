package com.cobo.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 用户 / 话题搜索的卡片结构和微博不一样，解析错了整页就是空的。 */
class SearchParseTest {

    @Test
    fun parsesUsersAndDropsDuplicates() {
        val root = JSONObject(
            """
            {
              "ok": 1,
              "data": {
                "cards": [
                  {
                    "card_type": 11,
                    "card_group": [
                      {
                        "card_type": 10,
                        "desc1": "卡片简介",
                        "desc2": "粉丝：58.8万",
                        "user": {
                          "id": 1234567890,
                          "screen_name": "示例用户",
                          "profile_image_url": "https://example.com/a.jpg",
                          "avatar_hd": "https://example.com/a_hd.jpg",
                          "followers_count": "12.3万",
                          "follow_count": 42,
                          "verified": true,
                          "description": "个人简介"
                        }
                      },
                      {
                        "card_type": 10,
                        "desc1": "第二个人简介",
                        "desc2": "粉丝：58.8万",
                        "user": {
                          "id": 987654321,
                          "screen_name": "第二个人",
                          "profile_image_url": "https://example.com/b.jpg"
                        }
                      }
                    ]
                  },
                  {
                    "card_type": 11,
                    "card_group": [
                      {
                        "card_type": 10,
                        "user": {
                          "id": 1234567890,
                          "screen_name": "示例用户",
                          "profile_image_url": "https://example.com/a.jpg"
                        }
                      }
                    ]
                  }
                ]
              }
            }
            """.trimIndent()
        )

        val users = parseUserCards(root)
        assertEquals(2, users.size)
        val first = users.first()
        assertEquals("1234567890", first.id)
        assertEquals("示例用户", first.name)
        assertEquals("https://example.com/a_hd.jpg", first.avatar)
        assertEquals(123000, first.followers)
        assertEquals(42, first.friends)
        assertTrue(first.verified)
        assertEquals("卡片简介", first.description)

        val second = users[1]
        assertEquals("第二个人简介", second.description)
        assertEquals(588000, second.followers)
    }

    @Test
    fun parsesTopicsAndStripsHashes() {
        val root = JSONObject(
            """
            {
              "ok": 1,
              "data": {
                "cards": [
                  {
                    "card_type": 11,
                    "card_group": [
                      {
                        "card_type": 8,
                        "title_sub": "#摄影#",
                        "desc1": "阅读 1234.5万",
                        "desc2": "讨论 5.6万"
                      },
                      {
                        "card_type": 8,
                        "title_sub": "#摄影#",
                        "desc1": "重复项"
                      },
                      {
                        "card_type": 8,
                        "title_sub": ""
                      }
                    ]
                  }
                ]
              }
            }
            """.trimIndent()
        )

        val topics = parseTopicCards(root)
        assertEquals(1, topics.size)
        assertEquals("摄影", topics.first().title)
        assertEquals("阅读 1234.5万", topics.first().desc1)
        assertEquals("讨论 5.6万", topics.first().desc2)
    }

    @Test
    fun emptyPayloadReturnsEmptyLists() {
        assertTrue(parseUserCards(JSONObject("""{"ok":0}""")).isEmpty())
        assertTrue(parseTopicCards(JSONObject("""{"ok":0}""")).isEmpty())
    }
}
