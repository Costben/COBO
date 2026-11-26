package com.cobo.app.data

import com.cobo.app.json.JSONArray
import com.cobo.app.json.JSONObject
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

        val cardRoot = JSONObject(
            """
            {
              "pageInfo": {
                "containerid": "100808fc439dedbb06ca5fd858848e521b8716_-_feed",
                "page_title": "兵哥厉害",
                "since_id": "0"
              },
              "cards": [
                {
                  "card_type": 11,
                  "card_group": [
                    {
                      "card_type": 8,
                      "itemid": "card_bin_1",
                      "title_sub": "兵哥冠卡",
                      "pic": "http://wx2.sinaimg.cn/large/bin_card.jpg",
                      "scheme": "sinaweibo://wbox?id=c0p9sg9tw9"
                    }
                  ]
                }
              ]
            }
            """.trimIndent(),
        )
        val cardPage = parseContainerSuperTopic(
            cardRoot,
            "100808fc439dedbb06ca5fd858848e521b8716",
            "100808fc439dedbb06ca5fd858848e521b8716_-_feed",
        )
        assertEquals(1, cardPage.items.size)
        assertEquals("https://wx2.sinaimg.cn/large/bin_card.jpg", cardPage.items[0].bigPic?.url)
        assertEquals("兵哥冠卡", cardPage.items[0].bigPic?.title)
    }

    @Test
    fun parsesSearchFeaturedBigVCardAndSuperTopicAndPlaceItems() {
        val searchRoot = JSONObject(
            """
            {
              "cards": [
                {
                  "card_type": 11,
                  "card_group": [
                    {
                      "card_type": 10,
                      "desc1": "粉丝：175.9万 | BLG电子竞技俱乐部英雄联盟选手",
                      "desc2": "V指数 电竞领域 85.49分",
                      "desc2_struct": [{"name": "V指数 电竞领域 85.49分", "scheme": "https://v6.bang.weibo.com/newczv/1052"}],
                      "user": {
                        "id": 7374758152,
                        "screen_name": "Bin哥厉害",
                        "profile_image_url": "https://tvax2.sinaimg.cn/avatar.jpg",
                        "followers_count": "175.9万",
                        "follow_count": 13,
                        "statuses_count": 192,
                        "verified": true,
                        "verified_type": 0,
                        "mbtype": 11,
                        "svip": 1,
                        "mbrank": 2
                      }
                    }
                  ]
                },
                {
                  "card_type": 9,
                  "mblog": {"idstr": "8001", "text": "Bin赛后采访", "user": {"id": "1", "screen_name": "路人"}}
                }
              ]
            }
            """.trimIndent(),
        )
        val page = parseContainerCards(searchRoot)
        assertEquals(1, page.featuredUsers.size)
        val featured = page.featuredUsers[0]
        assertEquals("Bin哥厉害", featured.user.name)
        assertEquals(192, featured.user.statuses)
        assertEquals("V指数 电竞领域 85.49分", featured.desc2)
        assertEquals("https://v6.bang.weibo.com/newczv/1052", featured.desc2Scheme)

        val placeRoot = JSONObject(
            """
            {
              "data": {
                "cards": [
                  {
                    "card_type": 11,
                    "card_group": [
                      {
                        "card_type": 8,
                        "title_sub": "北京",
                        "pic": "http://ww1.sinaimg.cn/wap180/beijing.jpg",
                        "desc1": "5296.1万人去过",
                        "scheme": "https://m.weibo.cn/p/index?containerid=100808692e92669c0ca340eff4fdcef32896ee_-_lbs"
                      }
                    ]
                  }
                ]
              }
            }
            """.trimIndent(),
        )
        val places = parseTopicCards(placeRoot)
        assertEquals(1, places.size)
        assertEquals("北京", places[0].title)
        assertEquals("https://ww1.sinaimg.cn/wap180/beijing.jpg", places[0].pic)
        assertEquals("5296.1万人去过", places[0].desc1)
    }

    @Test
    fun linkifiesReplyAndRepostMentionsWithoutBreakingEmails() {
        val raw = "回复@日天不淡定:上次那个Q8B的外壳是练手。//@日天不淡定:怎么又做机箱 email: a.b@example.com"
        val out = formatContainerTextToHtml(raw)
        val encoded = java.net.URLEncoder.encode("日天不淡定", "UTF-8")
        assertTrue(out.contains("回复<a href=\"/n/$encoded\">@日天不淡定</a>:上次那个Q8B"))
        assertTrue(out.contains("//<a href=\"/n/$encoded\">@日天不淡定</a>:怎么又做机箱"))
        assertTrue(out.contains("a.b@example.com"))
        assertTrue(!out.contains("href=\"/n/example\""))

        val notifRoot = JSONObject(
            """
            {
              "comments": [
                {
                  "idstr": "9001",
                  "text": "回复@小明:同意",
                  "created_at": "Wed Nov 19 19:01:00 +0800 2026",
                  "user": {"id": 202, "screen_name": "李四"}
                }
              ]
            }
            """.trimIndent(),
        )
        val notifPage = parseContainerNotifications(notifRoot, NotificationTab.Comment)
        assertEquals(1, notifPage.notifications.size)
        val encodedXiaoMing = java.net.URLEncoder.encode("小明", "UTF-8")
        assertEquals(
            "回复<a href=\"/n/$encodedXiaoMing\">@小明</a>:同意",
            notifPage.notifications[0].textHtml,
        )
    }

    @Test
    fun originalSizePreservesSignedUrlsAndRewritesUnsignedThumbs() {
        val unsigned = "https://wx1.sinaimg.cn/orj360/006abcdegy1abc.jpg"
        assertEquals(
            "https://wx1.sinaimg.cn/large/006abcdegy1abc.jpg",
            com.cobo.app.ui.components.originalSize(unsigned),
        )
        val signed = "https://tvax3.sinaimg.cn/crop.0.0.1080.1080.180/6a80b4ee.jpg?KID=imgbed,tva&Expires=1795365627&ssig=abc"
        assertEquals(signed, com.cobo.app.ui.components.originalSize(signed))
    }
}
