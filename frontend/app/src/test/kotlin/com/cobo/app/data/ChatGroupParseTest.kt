package com.cobo.app.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 群聊会话的识别与读取。
 *
 * 消息列表把群聊和私信混在一起，列表项只靠 scheme 区分：`?gid=` 是群，`?uid=` 是人。
 * 把群聊当私信去调 `api/chat/list?uid=<群主>` 会拿到 20003「用户不存在」。
 */
class ChatGroupParseTest {

    @Test
    fun readsGidFromScheme() {
        assertEquals("4891234567", chatGidOf("/message/chat?gid=4891234567"))
        assertEquals("42", chatGidOf("https://m.weibo.cn/message/chat?gid=42&from=msglist"))
        assertEquals("", chatGidOf("/message/chat?uid=1234567890"))
        assertEquals("", chatGidOf("/message/chat"))
        assertEquals("", chatGidOf(""))
    }

    private fun message(id: String, sender: String, fromUser: String? = null): JSONObject =
        JSONObject()
            .put("id", id)
            .put("text", "消息 $id")
            .put("created_at", "Mon Nov 17 10:00:00 +0800 2026")
            .put("media_type", 0)
            .put("dm_type", 0)
            .apply {
                if (fromUser == null) put("sender_id", sender) else put("from_user", JSONObject().put("id", fromUser))
            }

    private fun page(vararg msgs: JSONObject) = JSONObject()
        .put("msgs", JSONArray().apply { msgs.forEach { put(it) } })
        .put("title", "测试群")
        .put("total_number", 0)

    @Test
    fun groupChatIsReversedToNewestFirst() {
        val page = parseChatPage(
            page(message("1", "a"), message("2", "b"), message("3", "c")),
            isGroup = true,
        )
        assertEquals(listOf("3", "2", "1"), page.messages.map { it.id })
    }

    @Test
    fun directChatKeepsServerOrder() {
        val page = parseChatPage(
            page(message("3", "a"), message("2", "b"), message("1", "c")),
            isGroup = false,
        )
        assertEquals(listOf("3", "2", "1"), page.messages.map { it.id })
    }

    @Test
    fun fallsBackToFromUserWhenSenderIdMissing() {
        val page = parseChatPage(page(message("1", "", fromUser = "9876543210")), isGroup = true)
        assertEquals("9876543210", page.messages.single().senderId)
    }

    @Test
    fun messageItemCarriesGroupId() {
        val item = MessageItem(
            id = "",
            text = "群消息",
            createdAt = "",
            user = null,
            unread = 0,
            scheme = "/message/chat?gid=4891234567",
            gid = chatGidOf("/message/chat?gid=4891234567"),
        )
        assertEquals("4891234567", item.gid)
    }
}
