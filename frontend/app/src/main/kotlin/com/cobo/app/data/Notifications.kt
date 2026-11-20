package com.cobo.app.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * 通知中心的五大分类：与桌面端 `/ajax/statuses/mentions` 等接口一一对应，
 * 客户端登录态与网页登录态共用同一套桌面 Ajax 解析。
 */
enum class NotificationTab(
    val label: String,
    val path: String,
    val listKey: String,
) {
    AtWeibo("@微博", "/ajax/statuses/mentions", "statuses"),
    AtComment("@评论", "/ajax/comments/mentions", "comments"),
    Comment("评论", "/ajax/message/cmt", "comments"),
    SentComment("发出", "/ajax/message/myCmt", "comments"),
    Like("赞", "/ajax/message/attitudes", "attitudes"),
}

data class NotificationItem(
    val id: String,
    val user: User?,
    val createdAt: String,
    val actionLabel: String,
    val textHtml: String,
    val replyTextHtml: String = "",
    val replyUserName: String = "",
    val targetStatus: Status? = null,
    val commentId: String = "",
    val likes: Int = 0,
    val liked: Boolean = false,
)

data class NotificationPage(
    val statuses: List<Status> = emptyList(),
    val notifications: List<NotificationItem> = emptyList(),
    val nextCursor: String? = null,
)

/**
 * 解析桌面端通知接口响应。
 *
 * - `@微博`（`statuses/mentions`）返回标准微博数组；
 * - `@评论`、`收到的评论`、`发出的评论` 返回带 `status` / `reply_comment` 的评论数组；
 * - `赞`（`message/attitudes`）返回带 `status` 或 `comment` 的赞数组。
 */
fun parseDesktopNotifications(root: JSONObject, tab: NotificationTab): NotificationPage {
    val ok = root.optInt("ok", 0)
    val data = root.optJSONObject("data")
    if (ok != 1 && data == null) {
        val message = root.str("msg").ifBlank { root.str("message") }
        if (message.contains("还没有") || message.contains("暂无")) {
            return NotificationPage()
        }
        val errno = if (ok == -100) "-100" else root.str("errno").ifBlank { null }
        val fallback = if (ok == -100) "请先登录微博账号" else "加载失败"
        throw ApiException(message.ifBlank { fallback }, errno = errno)
    }

    val container = data ?: root
    return parseNotificationPayload(container, tab)
}

/** 解析容器端（`api.weibo.cn/2/`）的通知接口响应，裸 JSON 无 `ok/data` 包装。 */
fun parseContainerNotifications(root: JSONObject, tab: NotificationTab): NotificationPage =
    parseNotificationPayload(root, tab)

private fun parseNotificationPayload(container: JSONObject, tab: NotificationTab): NotificationPage {
    val nextCursor = container.str("next_cursor")
        .ifBlank { container.str("max_id") }
        .takeIf { it.isNotBlank() && it != "0" }
    val arr: JSONArray? = container.optJSONArray(tab.listKey)

    if (tab == NotificationTab.AtWeibo) {
        return NotificationPage(
            statuses = if (arr == null) emptyList() else statusesOf(arr),
            nextCursor = nextCursor,
        )
    }

    if (arr == null || arr.length() == 0) {
        return NotificationPage(nextCursor = nextCursor)
    }

    val out = ArrayList<NotificationItem>(arr.length())
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        val user = User.from(o.optJSONObject("user"))
        val createdAt = o.str("created_at")
        val id = o.str("idstr").ifBlank { o.str("id") }.ifBlank { "${tab.name}-$i-$createdAt" }
        if (tab == NotificationTab.Like) {
            val likedComment = o.optJSONObject("comment")
            val statusObj = likedComment?.optJSONObject("status") ?: o.optJSONObject("status")
            val targetStatus = Status.from(statusObj)
            val actionLabel = if (likedComment != null) "赞了你的评论" else "赞了你的微博"
            val replyTextHtml = likedComment?.str("text").orEmpty()
                .ifBlank { likedComment?.str("text_raw").orEmpty() }
            val replyUserName = User.from(likedComment?.optJSONObject("user"))?.name.orEmpty()
            val commentId = likedComment?.str("idstr").orEmpty().ifBlank { likedComment?.str("id").orEmpty() }
            if (user == null && targetStatus == null && replyTextHtml.isBlank()) continue
            out.add(
                NotificationItem(
                    id = id,
                    user = user,
                    createdAt = createdAt,
                    actionLabel = actionLabel,
                    textHtml = "",
                    replyTextHtml = replyTextHtml,
                    replyUserName = replyUserName,
                    targetStatus = targetStatus,
                    commentId = commentId,
                ),
            )
        } else {
            val replyComment = o.optJSONObject("reply_comment") ?: o.optJSONObject("reply")
            val replyUserName = User.from(replyComment?.optJSONObject("user"))?.name.orEmpty()
            val targetStatus = Status.from(o.optJSONObject("status"))
            val textHtml = o.str("text").ifBlank { o.str("text_raw") }
            val replyTextHtml = replyComment?.str("text").orEmpty()
                .ifBlank { replyComment?.str("text_raw").orEmpty() }
            val actionLabel = when (tab) {
                NotificationTab.AtComment -> "在评论中提到了你"
                NotificationTab.SentComment -> "我发出的评论"
                else -> if (replyComment != null) "回复了你的评论" else "评论了你的微博"
            }
            val likes = if (o.has("like_counts")) o.int("like_counts") else o.int("like_count")
            val commentId = o.str("idstr").ifBlank { o.str("id") }
            if (user == null && textHtml.isBlank() && targetStatus == null) continue
            out.add(
                NotificationItem(
                    id = id,
                    user = user,
                    createdAt = createdAt,
                    actionLabel = actionLabel,
                    textHtml = textHtml,
                    replyTextHtml = replyTextHtml,
                    replyUserName = replyUserName,
                    targetStatus = targetStatus,
                    commentId = commentId,
                    likes = likes,
                    liked = o.optBoolean("liked", false),
                ),
            )
        }
    }
    return NotificationPage(
        notifications = out,
        nextCursor = nextCursor,
    )
}
