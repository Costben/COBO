package com.cobo.app.data

import com.cobo.app.screen.shouldShowCommentMemberBadge
import com.cobo.app.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentPicsTest {

    @Test
    fun readsPicIdsWithPicInfos() {
        val comment = Comment.from(
            JSONObject(
                """
                {
                  "id": "5000000000000001",
                  "text": "看这个",
                  "user": {"id": 1, "screen_name": "a"},
                  "created_at": "Thu Nov 20 12:00:00 +0800 2025",
                  "like_counts": 3,
                  "pic_ids": ["006ABCdefgy1h", "006ABCdefgy1i"],
                  "pic_infos": {
                    "006ABCdefgy1h": {
                      "bmiddle": {"url": "https://wx1.sinaimg.cn/wap360/006ABCdefgy1h.jpg"},
                      "large": {
                        "url": "https://wx1.sinaimg.cn/large/006ABCdefgy1h.jpg",
                        "geo": {"width": 1600, "height": 1200}
                      }
                    }
                  }
                }
                """.trimIndent(),
            ),
        )!!

        assertEquals(2, comment.pics.size)
        assertEquals("https://wx1.sinaimg.cn/wap360/006ABCdefgy1h.jpg", comment.pics[0].thumb)
        assertEquals("https://wx1.sinaimg.cn/large/006ABCdefgy1h.jpg", comment.pics[0].large)
        assertEquals(1600, comment.pics[0].width)
        assertEquals(1200, comment.pics[0].height)
        // pic_ids 里没有对应 info 的那张按 id 拼地址，别整条丢掉。
        assertEquals("https://wx1.sinaimg.cn/orj360/006ABCdefgy1i.jpg", comment.pics[1].thumb)
    }

    @Test
    fun readsSinglePicString() {
        val comment = Comment.from(
            JSONObject(
                """
                {
                  "id": "5000000000000002",
                  "text": "图评",
                  "created_at": "Thu Nov 20 12:00:00 +0800 2025",
                  "pic": "//wx1.sinaimg.cn/large/006ABCdefgy1j.jpg"
                }
                """.trimIndent(),
            ),
        )!!

        assertEquals(1, comment.pics.size)
        assertEquals("https://wx1.sinaimg.cn/large/006ABCdefgy1j.jpg", comment.pics[0].thumb)
        assertEquals("https://wx1.sinaimg.cn/large/006ABCdefgy1j.jpg", comment.pics[0].large)
    }

    @Test
    fun readsPicObjectWithoutTurningJsonIntoUrl() {
        val comment = Comment.from(
            JSONObject(
                """
                {
                  "id": "5000000000000007",
                  "text": "图片评论",
                  "created_at": "Thu Nov 20 12:00:00 +0800 2025",
                  "pic": {
                    "pid": "006ABCdefgy1k",
                    "url": "http://wx1.sinaimg.cn/orj360/006ABCdefgy1k.jpg",
                    "geo": {"width": 360, "height": 480},
                    "large": {
                      "url": "http://wx1.sinaimg.cn/large/006ABCdefgy1k.jpg",
                      "geo": {"width": 1080, "height": 1440}
                    }
                  },
                  "reply": {
                    "id": "5000000000000008",
                    "text": "被回复图",
                    "created_at": "Thu Nov 20 11:50:00 +0800 2025",
                    "pic": {
                      "pid": "006ABCdefgy1m"
                    }
                  }
                }
                """.trimIndent(),
            ),
        )!!

        assertEquals(1, comment.pics.size)
        assertEquals("https://wx1.sinaimg.cn/orj360/006ABCdefgy1k.jpg", comment.pics[0].thumb)
        assertEquals("https://wx1.sinaimg.cn/large/006ABCdefgy1k.jpg", comment.pics[0].large)
        assertEquals(1080, comment.pics[0].width)
        assertEquals(1440, comment.pics[0].height)

        val replyPics = comment.reply?.pics.orEmpty()
        assertEquals(1, replyPics.size)
        assertEquals("https://wx1.sinaimg.cn/orj360/006ABCdefgy1m.jpg", replyPics[0].thumb)
        assertEquals("https://wx1.sinaimg.cn/large/006ABCdefgy1m.jpg", replyPics[0].large)
    }

    @Test
    fun readsPicInfosWithoutPicIds() {
        val comment = Comment.from(
            JSONObject(
                """
                {
                  "id": "5000000000000009",
                  "text": "只有 pic_infos",
                  "created_at": "Thu Nov 20 12:00:00 +0800 2025",
                  "pic_infos": {
                    "006ABCdefgy1n": {
                      "bmiddle": {"url": "//wx2.sinaimg.cn/wap360/006ABCdefgy1n.jpg"},
                      "large": {"url": "//wx2.sinaimg.cn/large/006ABCdefgy1n.jpg"}
                    }
                  }
                }
                """.trimIndent(),
            ),
        )!!

        assertEquals(1, comment.pics.size)
        assertEquals("https://wx2.sinaimg.cn/wap360/006ABCdefgy1n.jpg", comment.pics[0].thumb)
        assertEquals("https://wx2.sinaimg.cn/large/006ABCdefgy1n.jpg", comment.pics[0].large)
    }

    @Test
    fun skipsClientPlaceholderPicsAndFallsBackToPicObject() {
        val comment = Comment.from(
            JSONObject(
                """
                {
                  "idstr": "5368541841990139",
                  "text": "图片评论",
                  "created_at": "Mon Nov 23 22:40:00 +0800 2026",
                  "like_count": 150,
                  "pic_num": 1,
                  "pic": {
                    "pid": "007I2iyygy1ijchtupaxrj30u50gzn0s",
                    "url": "https://wx4.sinaimg.cn/orj360/007I2iyygy1ijchtupaxrj30u50gzn0s.jpg",
                    "size": "orj360",
                    "geo": {"width": 479, "height": 270, "croped": false},
                    "large": {
                      "size": "large",
                      "url": "https://wx4.sinaimg.cn/large/007I2iyygy1ijchtupaxrj30u50gzn0s.jpg",
                      "geo": {"width": "1085", "height": "611", "croped": false}
                    }
                  },
                  "pics": [
                    {
                      "pid": null,
                      "url": "https://ss1.sinaimg.cn/orj360/&690",
                      "size": "orj360",
                      "geo": false,
                      "large": {"size": "large", "url": "https://ss1.sinaimg.cn/large/&690", "geo": false},
                      "duration": 0,
                      "type": "video"
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )!!

        assertEquals(1, comment.pics.size)
        assertEquals("https://wx4.sinaimg.cn/orj360/007I2iyygy1ijchtupaxrj30u50gzn0s.jpg", comment.pics[0].thumb)
        assertEquals("https://wx4.sinaimg.cn/large/007I2iyygy1ijchtupaxrj30u50gzn0s.jpg", comment.pics[0].large)
        assertEquals(1085, comment.pics[0].width)
        assertEquals(611, comment.pics[0].height)
    }

    @Test
    fun commentWithoutPictureHasNoPics() {        val comment = Comment.from(
            JSONObject(
                """
                {
                  "id": "5000000000000003",
                  "text": "纯文字",
                  "created_at": "Thu Nov 20 12:00:00 +0800 2025"
                }
                """.trimIndent(),
            ),
        )!!

        assertTrue(comment.pics.isEmpty())
    }

    @Test
    fun readsFanBadgeImage() {
        val comment = Comment.from(
            JSONObject(
                """
                {
                  "id": "5000000000000004",
                  "text": "铁粉的评论",
                  "created_at": "Thu Nov 20 12:00:00 +0800 2025",
                  "comment_badge": [
                    {
                      "pic_url": "https://h5.sinaimg.cn/upload/114/962/2022/07/08/member_7_bigfan2_4.png",
                      "name": "loyal_fans",
                      "type_and_level": "2_4"
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )!!

        assertEquals(1, comment.badges.size)
        assertEquals(
            "https://h5.sinaimg.cn/upload/114/962/2022/07/08/member_7_bigfan2_4.png",
            comment.badges[0].pic,
        )
    }

    @Test
    fun readsFanBadgeFromUserAndFanTierFallback() {
        val comment = Comment.from(
            JSONObject(
                """
                {
                  "id": "5000000000000005",
                  "text": "支持",
                  "created_at": "Thu Nov 20 12:00:00 +0800 2025",
                  "user": {
                    "id": 2,
                    "screen_name": "铁粉用户",
                    "comment_badge": [
                      {
                        "pic_url": "//h5.sinaimg.cn/upload/114/962/2022/07/08/member_0_bigfan2_3.png",
                        "name": "loyal_fans"
                      }
                    ]
                  },
                  "comment_bubble": {
                    "id": "tie_0001",
                    "name": "铁粉专属"
                  }
                }
                """.trimIndent(),
            ),
        )!!

        assertEquals(1, comment.badges.size)
        assertEquals(
            "https://h5.sinaimg.cn/upload/114/962/2022/07/08/member_0_bigfan2_3.png",
            comment.badges[0].pic,
        )
        assertEquals("铁粉", comment.fanTier)
    }

    @Test
    fun readsUserIconsAndCustomFanTierNames() {
        val comment = Comment.from(
            JSONObject(
                """
                {
                  "id": "5000000000000006",
                  "text": "支持",
                  "created_at": "Thu Nov 20 12:00:00 +0800 2025",
                  "user": {
                    "id": 3,
                    "screen_name": "粉丝用户",
                    "icons": [
                      {
                        "type": "loyal_fans",
                        "hide": 0,
                        "url": "https://h5.sinaimg.cn/upload/114/962/2022/07/08/member_4_bigfan3_1.png",
                        "actionlog": {"ext": "status:fans"}
                      },
                      {
                        "type": "gongyi",
                        "hide": 0,
                        "url": "https://h5.sinaimg.cn/upload/other.png"
                      }
                    ]
                  },
                  "comment_bubble": {
                    "id": "custom_0001",
                    "obtain_type": 3,
                    "name": "挚爱粉专属"
                  }
                }
                """.trimIndent(),
            ),
        )!!

        assertEquals(1, comment.badges.size)
        assertEquals(
            "https://h5.sinaimg.cn/upload/114/962/2022/07/08/member_4_bigfan3_1.png",
            comment.badges[0].pic,
        )
        assertEquals("挚爱粉", comment.fanTier)
    }

    @Test
    fun hidesSeparateMemberBadgeWhenCrownFanBadgeIsShown() {
        val crownBadge = listOf(
            CommentBadge("https://h5.sinaimg.cn/upload/114/962/2022/07/08/member_5_bigfan3_2.png"),
        )
        val nonVipBadge = listOf(
            CommentBadge("https://h5.sinaimg.cn/upload/114/962/2022/07/08/member_0_bigfan2_3.png"),
        )

        assertFalse(shouldShowCommentMemberBadge(Membership.Vip, crownBadge, showFanLevel = true))
        assertFalse(shouldShowCommentMemberBadge(Membership.Svip, crownBadge, showFanLevel = true))
        assertTrue(shouldShowCommentMemberBadge(Membership.Svip, crownBadge, showFanLevel = false))
        assertTrue(shouldShowCommentMemberBadge(Membership.Vip, emptyList(), showFanLevel = true))
        assertTrue(shouldShowCommentMemberBadge(Membership.Vip, nonVipBadge, showFanLevel = true))
        assertFalse(shouldShowCommentMemberBadge(Membership.None, emptyList(), showFanLevel = true))
    }

    @Test
    fun parsesClientReplyCommentPicIdAndUrlStructShortUrlFallback() {
        val comment = Comment.from(
            JSONObject(
                """
                {
                  "idstr": "5000000000000010",
                  "text": "看看图 http://t.cn/A6clientImg",
                  "created_at": "Thu Nov 20 12:00:00 +0800 2025",
                  "url_struct": [
                    {
                      "short_url": "http://t.cn/A6clientImg",
                      "ori_url": "sinaweibo://commentimage?compic=1",
                      "url_title": "网页链接",
                      "url_type_pic": "https://h5.sinaimg.cn/upload/2015/09/25/3/timeline_card_small_photo_default.png"
                    }
                  ],
                  "reply_comment": {
                    "idstr": "5000000000000011",
                    "text": "被引用图评",
                    "created_at": "Thu Nov 20 11:55:00 +0800 2025",
                    "pic": "http://wx2.sinaimg.cn/thumb180/006ABCdefgy1p.jpg"
                  }
                }
                """.trimIndent(),
            ),
        )!!

        assertEquals(
            listOf("http://t.cn/A6clientImg"),
            com.cobo.app.ui.extractResolvablePhotoLinksFromHtml(comment.textHtml),
        )
        val quoted = comment.reply!!
        assertEquals("5000000000000011", quoted.id)
        assertEquals("https://wx2.sinaimg.cn/thumb180/006ABCdefgy1p.jpg", quoted.pics[0].thumb)
        assertEquals("https://wx2.sinaimg.cn/large/006ABCdefgy1p.jpg", quoted.pics[0].large)
    }

    @Test
    fun extractsInlineUrlStructPicInfosOnRepostAndComment() {
        val status = Status.from(
            JSONObject(
                """
                {
                  "id": "5000000000000020",
                  "text": "转发配图 http://t.cn/A6repostImg",
                  "created_at": "Thu Nov 20 12:00:00 +0800 2025",
                  "url_struct": [
                    {
                      "short_url": "http://t.cn/A6repostImg",
                      "url_title": "查看图片",
                      "pic_infos": {
                        "006ABCdefgy1q": {
                          "bmiddle": {"url": "https://wx3.sinaimg.cn/orj360/006ABCdefgy1q.jpg"},
                          "large": {"url": "https://wx3.sinaimg.cn/large/006ABCdefgy1q.jpg"}
                        }
                      }
                    }
                  ],
                  "retweeted_status": {
                    "id": "5000000000000019",
                    "text": "原博",
                    "created_at": "Thu Nov 20 11:00:00 +0800 2025"
                  }
                }
                """.trimIndent(),
            ),
        )!!

        assertEquals(1, status.pics.size)
        assertEquals("https://wx3.sinaimg.cn/orj360/006ABCdefgy1q.jpg", status.pics[0].thumb)
        assertEquals("https://wx3.sinaimg.cn/large/006ABCdefgy1q.jpg", status.pics[0].large)
    }
}
