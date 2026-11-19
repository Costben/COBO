package com.cobo.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationsParseTest {

    @Test
    fun parsesAtWeiboMentionsAndCursor() {
        val root = JSONObject(
            """
            {
              "ok": 1,
              "data": {
                "next_cursor": 998877,
                "statuses": [
                  {
                    "idstr": "5001",
                    "mid": "5001",
                    "text": "@测试号 你好",
                    "created_at": "Wed Nov 19 19:00:00 +0800 2026",
                    "user": {"id": 101, "screen_name": "张三"}
                  }
                ]
              }
            }
            """.trimIndent(),
        )

        val page = parseDesktopNotifications(root, NotificationTab.AtWeibo)
        assertEquals(1, page.statuses.size)
        assertEquals("5001", page.statuses[0].id)
        assertEquals("张三", page.statuses[0].user?.name)
        assertEquals("998877", page.nextCursor)
    }

    @Test
    fun parsesCommentsAndReplyContext() {
        val root = JSONObject(
            """
            {
              "ok": 1,
              "data": {
                "next_cursor": 0,
                "comments": [
                  {
                    "idstr": "7001",
                    "text": "我也觉得",
                    "created_at": "Wed Nov 19 19:01:00 +0800 2026",
                    "user": {"id": 202, "screen_name": "李四"},
                    "reply_comment": {
                      "idstr": "6999",
                      "text": "原评论内容",
                      "user": {"id": 100, "screen_name": "我自己"}
                    },
                    "status": {
                      "idstr": "4001",
                      "mid": "4001",
                      "text": "主贴正文",
                      "user": {"id": 300, "screen_name": "博主A"}
                    }
                  }
                ]
              }
            }
            """.trimIndent(),
        )

        val page = parseDesktopNotifications(root, NotificationTab.Comment)
        assertEquals(1, page.notifications.size)
        val item = page.notifications[0]
        assertEquals("回复了你的评论", item.actionLabel)
        assertEquals("我也觉得", item.textHtml)
        assertEquals("原评论内容", item.replyTextHtml)
        assertEquals("我自己", item.replyUserName)
        assertEquals("4001", item.targetStatus?.id)
        assertEquals("7001", item.commentId)
        assertNull(page.nextCursor)
    }

    @Test
    fun parsesLikeForCommentAndStatus() {
        val root = JSONObject(
            """
            {
              "ok": 1,
              "data": {
                "next_cursor": 12345,
                "attitudes": [
                  {
                    "idstr": "8001",
                    "created_at": "Wed Nov 19 19:02:00 +0800 2026",
                    "user": {"id": 203, "screen_name": "王五"},
                    "comment": {
                      "idstr": "7002",
                      "text": "我写的评论",
                      "user": {"id": 100, "screen_name": "我自己"},
                      "status": {
                        "idstr": "4002",
                        "mid": "4002",
                        "text": "原微博内容"
                      }
                    }
                  },
                  {
                    "idstr": "8002",
                    "created_at": "Wed Nov 19 19:03:00 +0800 2026",
                    "user": {"id": 204, "screen_name": "赵六"},
                    "status": {
                      "idstr": "4003",
                      "mid": "4003",
                      "text": "我发的微博"
                    }
                  }
                ]
              }
            }
            """.trimIndent(),
        )

        val page = parseDesktopNotifications(root, NotificationTab.Like)
        assertEquals(2, page.notifications.size)
        assertEquals("赞了你的评论", page.notifications[0].actionLabel)
        assertEquals("我写的评论", page.notifications[0].replyTextHtml)
        assertEquals("4002", page.notifications[0].targetStatus?.id)
        assertEquals("赞了你的微博", page.notifications[1].actionLabel)
        assertEquals("4003", page.notifications[1].targetStatus?.id)
        assertEquals("12345", page.nextCursor)
    }
}
