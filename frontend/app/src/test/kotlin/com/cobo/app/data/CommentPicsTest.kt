package com.cobo.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
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
}
