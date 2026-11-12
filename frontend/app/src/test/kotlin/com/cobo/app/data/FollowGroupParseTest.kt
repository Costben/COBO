package com.cobo.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 首页的「关注分组」列表来自网页版 `ajax/feed/allGroups`：只认「我的分组」里用户自建的组。
 * 「默认分组」放的是全部关注 / 最新微博 / 原创这类固定组（gid 传给 `groupstimeline` 会回
 * 「列表不存在」），特别关注 / 互相关注 / 悄悄关注首页也各有入口，都不该列进来。
 */
class FollowGroupParseTest {

    @Test
    fun parseFollowGroups_keepsCustomGroupsAndSkipsDefaultOnes() {
        val root = JSONObject(
            """
            {
              "ok": 1,
              "groups": [
                {
                  "title": "我的分组",
                  "group": [
                    {"gid": "4541216424989965", "title": "同事"},
                    {"gid": "4541216424989966", "title": "朋友"},
                    {"gid": "100056067178131", "title": "悄悄关注"}
                  ]
                },
                {
                  "title": "默认分组",
                  "group": [
                    {"gid": "100016067178131", "title": "全部关注"},
                    {"gid": "110006067178131", "title": "最新微博"},
                    {"gid": "4040233714204024", "title": "特别关注"},
                    {"gid": "100096067178131", "title": "互相关注"}
                  ]
                }
              ]
            }
            """.trimIndent()
        )

        val groups = parseFollowGroups(root)

        assertEquals(listOf("同事", "朋友"), groups.map { it.title })
        assertEquals("4541216424989965", groups.first().gid)
    }

    @Test
    fun parseFollowGroups_ignoresBrokenAndDuplicateEntries() {
        val root = JSONObject(
            """
            {
              "groups": [
                {
                  "title": "我的分组",
                  "group": [
                    {"gid": "", "title": "没有 id"},
                    {"gid": "42", "title": ""},
                    {"gid": "42", "title": "同学"},
                    {"gid": "42", "title": "重复的同一组"}
                  ]
                }
              ]
            }
            """.trimIndent()
        )

        val groups = parseFollowGroups(root)

        assertEquals(1, groups.size)
        assertEquals(FollowGroup("42", "同学"), groups.first())
    }

    @Test
    fun parseFollowGroups_returnsEmptyWhenNoCustomGroups() {
        val root = JSONObject(
            """
            {
              "groups": [
                {"title": "默认分组", "group": [{"gid": "100016067178131", "title": "全部关注"}]},
                {"title": "我的频道", "group": [{"gid": "102803", "title": "热门"}]}
              ]
            }
            """.trimIndent()
        )

        assertEquals(emptyList<FollowGroup>(), parseFollowGroups(root))
    }

    @Test
    fun parseFollowGroups_returnsEmptyOnMissingPayload() {
        assertEquals(emptyList<FollowGroup>(), parseFollowGroups(null))
        assertEquals(emptyList<FollowGroup>(), parseFollowGroups(JSONObject("{}")))
    }
}
