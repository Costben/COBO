package com.cobo.app.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContainerParseTest {

    @Test
    fun formatContainerTextPreservesExistingHtml() {
        val html = "<a href=\"/n/张三\">@张三</a> 你好<br />"
        assertEquals(html, formatContainerTextToHtml(html))
    }

    @Test
    fun formatContainerTextLeavesSimplePlainTextAlone() {
        assertEquals("视频", formatContainerTextToHtml("视频"))
        assertEquals("我也觉得", formatContainerTextToHtml("我也觉得"))
    }

    @Test
    fun formatContainerTextConvertsLinksTopicsMentionsAndNewlines() {
        val urlStruct = JSONArray(
            """
            [
              {
                "url_title": "曼城超话",
                "url_type_pic": "https://h5.sinaimg.cn/upload/100/959/2020/05/09/timeline_card_small_super.png",
                "ori_url": "sinaweibo://pageinfo?containerid=10080873b0cd20a108df1955d55cf6dc5da5f4&extparam=曼城"
              },
              {
                "short_url": "http://t.cn/AXTZ7XzB",
                "url_title": "微博视频",
                "ori_url": "https://video.weibo.com/show?fid=1034:5366"
              }
            ]
            """.trimIndent(),
        )
        val raw = "第一行 a < b\n@TestUser #普通话题# #曼城[超话]# http://t.cn/AXTZ7XzB"
        val out = formatContainerTextToHtml(raw, urlStruct, null)
        assertTrue(out.contains("a &lt; b<br/>"))
        assertTrue(out.contains("<a href=\"/n/TestUser\">@TestUser</a>"))
        assertTrue(out.contains(">@TestUser</a>"))
        assertTrue(out.contains(">#普通话题#</a>"))
        assertTrue(out.contains("10080873b0cd20a108df1955d55cf6dc5da5f4"))
        assertTrue(out.contains(">曼城</a>"))
        assertTrue(out.contains("https://video.weibo.com/show?fid=1034:5366"))
        assertTrue(out.contains(">微博视频</a>"))
        assertFalse(out.contains("[超话]"))
    }

    @Test
    fun parsesContainerNotificationsWithoutDataWrapper() {
        val root = JSONObject(
            """
            {
              "next_cursor": 5566,
              "comments": [
                {
                  "idstr": "7001",
                  "text": "回复@小明:同意",
                  "created_at": "Wed Nov 19 19:01:00 +0800 2026",
                  "user": {"id": 202, "screen_name": "李四"},
                  "status": {"idstr": "4001", "mid": "4001", "text": "原博"}
                }
              ]
            }
            """.trimIndent(),
        )
        val page = parseContainerNotifications(root, NotificationTab.Comment)
        assertEquals(1, page.notifications.size)
        assertEquals("5566", page.nextCursor)
        assertEquals("4001", page.notifications[0].targetStatus?.id)
        assertTrue(page.notifications[0].textHtml.contains("李四").not())
    }

    @Test
    fun parsesContainerHotItemsAndSearchSuggestAndSuperTopic() {
        val hotRoot = JSONObject(
            """
            {
              "cards": [
                {
                  "card_group": [
                    {"desc": "热搜第一", "scheme": "sinaweibo://searchall?q=1", "card_type_name": "热"},
                    {"itemid": "is_ad_pos:1|adid:9", "desc": "广告"}
                  ]
                }
              ]
            }
            """.trimIndent(),
        )
        val hot = parseContainerHotItems(hotRoot)
        assertEquals(1, hot.size)
        assertEquals("热搜第一", hot[0].title)

        val suggest = ContainerApi.parseSearchSuggest(
            """["微博",[{"card":{"desc_dark":"微博热搜"}},{"card":{"desc":"微博网页版"}}]]""",
        )
        assertEquals(listOf("微博热搜", "微博网页版"), suggest)

        val stRoot = JSONObject(
            """
            {
              "pageInfo": {
                "containerid": "100808fc439dedbb06ca5fd858848e521b8716_-_feed",
                "page_title": "原神",
                "since_id": "5367286659550316"
              },
              "cards": [
                {
                  "card_type": 9,
                  "mblog": {"idstr": "9001", "text": "抽卡了", "user": {"id": "1", "screen_name": "旅行者"}}
                }
              ]
            }
            """.trimIndent(),
        )
        val stPage = parseContainerSuperTopic(
            stRoot,
            "100808fc439dedbb06ca5fd858848e521b8716",
            "100808fc439dedbb06ca5fd858848e521b8716_-_feed",
        )
        assertEquals("原神", stPage.info.title)
        assertEquals(1, stPage.items.size)
        assertNotNull(stPage.nextCursor)
    }
}
