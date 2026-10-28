package com.cobo.app.data

import android.text.Html
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun JSONObject.str(key: String, def: String = ""): String {
    val v = opt(key) ?: return def
    if (v == JSONObject.NULL) return def
    return v.toString()
}

fun JSONObject.int(key: String, def: Int = 0): Int {
    val v = opt(key) ?: return def
    if (v == JSONObject.NULL) return def
    return when (v) {
        is Number -> v.toInt()
        is Boolean -> if (v) 1 else 0
        else -> v.toString().toIntOrNull() ?: def
    }
}

/**
 * m.weibo.cn 把「粉丝」这类可能上亿的计数按中文习惯压缩成展示字符串下发，
 * 例如 `"followers_count": "72.3万"` / `"1.2亿"` / `"26"`，直接走 [int] 会得到 0
 * （主页就变成「0 粉丝」）。这里按 万/亿/千 还原成数值。
 */
fun parseCount(raw: String?): Int? {
    val text = raw?.trim()?.replace(",", "")?.replace("+", "") ?: return null
    if (text.isEmpty()) return null
    val unit = when {
        text.endsWith("亿") -> 100_000_000.0
        text.endsWith("万") -> 10_000.0
        text.endsWith("千") -> 1_000.0
        text.endsWith("w", true) -> 10_000.0
        text.endsWith("k", true) -> 1_000.0
        else -> 1.0
    }
    val digits = text.dropLastWhile { it == '亿' || it == '万' || it == '千' || it == 'w' || it == 'W' || it == 'k' || it == 'K' }
    val value = digits.trim().toDoubleOrNull() ?: return null
    return (value * unit).toInt()
}

fun JSONObject.count(key: String, def: Int = 0): Int {
    val v = opt(key) ?: return def
    if (v == JSONObject.NULL) return def
    return when (v) {
        is Number -> v.toInt()
        is Boolean -> if (v) 1 else 0
        else -> parseCount(v.toString()) ?: def
    }
}

fun stripHtml(html: String?): String {
    if (html.isNullOrBlank()) return ""
    return try {
        Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString().trim()
    } catch (_: Throwable) {
        html.replace(Regex("<[^>]+>"), "").trim()
    }
}

/** 私信正文里的 emoji 是 `<img alt="[可爱]">`，[stripHtml] 会把它变成 U+FFFC 方块，这里换成 alt 文本。 */
fun chatPlainText(html: String?): String {
    if (html.isNullOrBlank()) return ""
    val withAlt = html.replace(Regex("<img[^>]*alt=\"([^\"]*)\"[^>]*>")) { it.groupValues[1] }
    return stripHtml(withAlt).replace("\uFFFC", "").trim()
}

data class User(
    val id: String,
    val name: String,
    val avatar: String,
    val verified: Boolean = false,
    val following: Boolean = false,
    val description: String = "",
    val followers: Int = 0,
    val friends: Int = 0,
    val statuses: Int = 0,
    val cover: String = "",
    /** 当前登录者是否把这个用户设成了「特别关注」；m.weibo.cn 每条 status 的 user 都带这个字段。 */
    val specialFollow: Boolean = false,
) {
    companion object {
        fun from(o: JSONObject?): User? {
            if (o == null) return null
            val id = o.str("id")
            if (id.isBlank()) return null
            return User(
                id = id,
                name = o.str("screen_name"),
                avatar = o.str("avatar_hd")
                    .ifBlank { o.str("avatar_large") }
                    .ifBlank { o.str("profile_image_url") }
                    .ifBlank { o.str("avatar") },
                verified = o.optBoolean("verified", false),
                following = o.optBoolean("following", false),
                description = o.str("description"),
                followers = o.count("followers_count", o.count("followers_count_str")),
                friends = o.int("follow_count", o.int("friends_count", o.count("friends_count_str"))),
                statuses = o.count("statuses_count"),
                cover = o.str("cover_image_phone"),
                specialFollow = o.optBoolean("special_follow", false),
            )
        }
    }
}

data class Pic(
    val thumb: String,
    val large: String,
    val width: Int = 0,
    val height: Int = 0,
)

data class VideoInfo(
    val title: String,
    val cover: String,
    val url: String,
)

data class Status(
    val id: String,
    val mid: String,
    val textHtml: String,
    val createdAt: String,
    val source: String,
    val user: User?,
    val pics: List<Pic> = emptyList(),
    val video: VideoInfo? = null,
    val retweeted: Status? = null,
    val reposts: Int = 0,
    val comments: Int = 0,
    val attitudes: Int = 0,
    val liked: Boolean = false,
    val region: String = "",
    val editCount: Int = 0,
) {
    val plainText: String get() = stripHtml(textHtml)

    companion object {
        fun from(o: JSONObject?): Status? = parse(o, 0)

        private fun parse(o: JSONObject?, depth: Int): Status? {
            if (o == null) return null
            val id = o.str("id")
            if (id.isBlank()) return null

            val pics = ArrayList<Pic>()
            val arr = o.optJSONArray("pics")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val p = arr.optJSONObject(i) ?: continue
                    val large = p.optJSONObject("large")
                    val url = p.str("url")
                    pics.add(
                        Pic(
                            thumb = url,
                            large = large?.str("url")?.ifBlank { url } ?: url,
                            width = large?.int("width") ?: p.int("width"),
                            height = large?.int("height") ?: p.int("height"),
                        )
                    )
                }
            }

            var video: VideoInfo? = null
            val page = o.optJSONObject("page_info")
            if (page != null && page.str("type") == "video") {
                val media = page.optJSONObject("media_info")
                val url = media?.str("stream_url_hd")?.ifBlank { media.str("stream_url") }
                    ?.ifBlank { media.str("mp4_720p_mp4") }
                    ?.ifBlank { media.str("mp4_hd_url") }
                    ?.ifBlank { media.str("mp4_sd_url") }
                    ?: ""
                val cover = page.optJSONObject("page_pic")?.str("url") ?: ""
                video = VideoInfo(
                    title = page.str("page_title"),
                    cover = cover.ifBlank { pics.firstOrNull()?.thumb ?: "" },
                    url = url,
                )
            }

            val retweeted = if (depth < 2) parse(o.optJSONObject("retweeted_status"), depth + 1) else null

            return Status(
                id = id,
                mid = o.str("mid").ifBlank { id },
                textHtml = o.str("text"),
                createdAt = o.str("created_at"),
                source = stripHtml(o.str("source")),
                user = User.from(o.optJSONObject("user")),
                pics = pics,
                video = video,
                retweeted = retweeted,
                reposts = o.int("reposts_count"),
                comments = o.int("comments_count"),
                attitudes = o.int("attitudes_count"),
                liked = o.optBoolean("liked", false),
                region = o.str("region_name"),
                editCount = o.int("edit_count"),
            )
        }
    }
}

data class EditVersion(
    val createdAt: String,
    val textHtml: String,
    val pics: List<String> = emptyList(),
    /** Visibility-only history rows have no body; this holds their one-line description. */
    val note: String = "",
) {
    companion object {
        fun from(o: JSONObject?): EditVersion? {
            if (o == null) return null
            val note = if (o.has("history_type")) stripHtml(o.str("text")) else ""
            val text = if (note.isBlank()) o.str("text") else ""
            val ids = o.optJSONArray("pic_ids")
            val pics = if (ids == null) {
                emptyList()
            } else {
                buildList {
                    for (i in 0 until ids.length()) {
                        val id = ids.optString(i).orEmpty()
                        if (id.isNotBlank()) add("https://wx1.sinaimg.cn/large/$id.jpg")
                    }
                }
            }
            val created = o.str("created_at")
            if (note.isBlank() && text.isBlank() && pics.isEmpty()) return null
            return EditVersion(created, text, pics, note)
        }
    }
}

data class Comment(
    val id: String,
    val textHtml: String,
    val user: User?,
    val createdAt: String,
    val likes: Int,
    val liked: Boolean = false,
    val reply: Comment? = null,
    /** The replies Weibo inlines under a comment, oldest first. */
    val replies: List<Comment> = emptyList(),
    /** How many replies the comment has in total; [replies] is only a preview. */
    val replyCount: Int = 0,
) {
    companion object {
        fun from(o: JSONObject?, depth: Int = 0): Comment? {
            if (o == null) return null
            val id = o.str("id")
            if (id.isBlank()) return null
            val children = if (depth < 1) o.optJSONArray("comments") else null
            val replies = if (children == null) {
                emptyList()
            } else {
                buildList {
                    for (i in 0 until children.length()) {
                        from(children.optJSONObject(i), depth + 1)?.let(::add)
                    }
                }
            }
            return Comment(
                id = id,
                textHtml = o.str("text"),
                user = User.from(o.optJSONObject("user")),
                createdAt = o.str("created_at"),
                // The hot list spells it `like_count`, the chronological one `like_counts`.
                likes = if (o.has("like_count")) o.int("like_count") else o.int("like_counts"),
                liked = o.optBoolean("liked"),
                reply = if (depth < 1) from(o.optJSONObject("reply"), depth + 1) else null,
                replies = replies,
                replyCount = o.optInt("total_number", replies.size),
            )
        }
    }
}

data class FeedPage(val items: List<Status>, val sinceId: String?)

/** How the detail page orders its comments: the hot list, or every comment in time order. */
enum class CommentSort { Hot, Time }

/**
 * One page of comments. [maxId]/[maxIdType] carry the hot list's cursor, [nextPage] the
 * chronological list's next page number (0 when there is none left).
 */
data class CommentPage(
    val items: List<Comment>,
    val maxId: String,
    val maxIdType: Int,
    val nextPage: Int = 0,
)

data class HotItem(val title: String, val scheme: String, val label: String = "", val rank: Int = 0)

/** 搜索「话题」分类的一条结果。标题去掉了两侧的 #，[desc1]/[desc2] 是阅读、讨论两项计数。 */
data class TopicItem(val title: String, val desc1: String = "", val desc2: String = "")

data class MessageItem(
    val id: String,
    val text: String,
    val createdAt: String,
    val user: User?,
    val unread: Int,
    val scheme: String,
)

/**
 * 一条私信。
 *
 * - [mediaType] 1 是图片，正文在 [imageUrl] / [thumbUrl]，[text] 只是占位；
 * - [dmType] 9 是系统提示（居中的灰条，不是气泡）；
 * - [pending] / [failed] 只用于本地乐观发送的那条，服务端回来的消息两者都是 false。
 */
data class ChatMessage(
    val id: String,
    val text: String,
    val senderId: String,
    val recipientId: String,
    val createdAt: String,
    val mediaType: Int = 0,
    val dmType: Int = 0,
    val imageUrl: String = "",
    val thumbUrl: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val pending: Boolean = false,
    val failed: Boolean = false,
) {
    val isImage: Boolean get() = mediaType == 1 && (imageUrl.isNotBlank() || thumbUrl.isNotBlank())
    val isNotice: Boolean get() = dmType == 9

    companion object {
        fun from(o: JSONObject?): ChatMessage? {
            if (o == null) return null
            val id = o.str("id")
            if (id.isBlank()) return null
            val attachment = o.optJSONObject("attachment")
            val original = attachment?.optJSONObject("original_image")
            return ChatMessage(
                id = id,
                text = o.str("text"),
                senderId = o.str("sender_id"),
                recipientId = o.str("recipient_id"),
                createdAt = o.str("created_at"),
                mediaType = o.int("media_type"),
                dmType = o.int("dm_type"),
                imageUrl = original?.str("url").orEmpty(),
                thumbUrl = attachment?.optJSONObject("thumbnail")?.str("url").orEmpty(),
                width = original?.int("width") ?: 0,
                height = original?.int("height") ?: 0,
            )
        }
    }
}

/** 一次 `api/chat/list` 的结果。列表本身是最新在前，界面按 reverseLayout 铺。 */
data class ChatPage(
    val messages: List<ChatMessage>,
    val users: Map<String, User>,
    val title: String,
    val total: Int,
)

data class Account(
    val uid: String,
    val name: String,
    val avatar: String,
    val cookie: String,
)

data class Draft(val text: String, val time: Long)

data class HistoryItem(val id: String, val mid: String, val text: String, val user: String, val time: Long)

data class Session(val cookie: String, val uid: String, val savedAt: Long)

data class Config(val uid: String, val st: String, val loggedIn: Boolean)

fun formatWeiboTime(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    val millis = runCatching {
        SimpleDateFormat("EEE MMM dd HH:mm:ss Z yyyy", Locale.US).parse(raw)?.time
    }.getOrNull() ?: raw.toLongOrNull() ?: return raw

    val diff = System.currentTimeMillis() - millis
    if (diff < 60_000L) return "刚刚"
    if (diff < 3_600_000L) return "${diff / 60_000L}分钟前"
    if (diff < 86_400_000L) return "${diff / 3_600_000L}小时前"
    val date = Date(millis)
    val now = Date()
    val pattern = if (SimpleDateFormat("yyyy", Locale.US).format(date) ==
        SimpleDateFormat("yyyy", Locale.US).format(now)
    ) "MM-dd" else "yyyy-MM-dd"
    return SimpleDateFormat(pattern, Locale.US).format(date)
}

fun parseCards(root: JSONObject?): FeedPage {
    val data = root?.optJSONObject("data")
    val items = ArrayList<Status>()
    val cards: JSONArray? = data?.optJSONArray("cards")
    if (cards != null) {
        for (i in 0 until cards.length()) {
            val card = cards.optJSONObject(i) ?: continue
            card.optJSONObject("mblog")?.let { Status.from(it)?.let(items::add) }
            val group = card.optJSONArray("card_group")
            if (group != null) {
                for (j in 0 until group.length()) {
                    group.optJSONObject(j)?.optJSONObject("mblog")
                        ?.let { Status.from(it)?.let(items::add) }
                }
            }
        }
    }
    val since = data?.optJSONObject("cardlistInfo")?.str("since_id")?.ifBlank { null }
    return FeedPage(items, since)
}

/**
 * 用户搜索（`100103type=3`）的卡片。用户挂在 `cards[].card_group[].user` 上，
 * 首页还可能多一张 `card_type` 不是 11 的筛选卡，所以逐张扫、只认带 user 的项。
 *
 * 搜索结果里的简介和粉丝数不总在 user 对象里：卡片项的 `desc1` 是简介、`desc2` 是
 * 「粉丝：58.8万」这种展示串，user 上没有对应字段时用它们补。
 */
fun parseUserCards(root: JSONObject?): List<User> {
    val data = root?.optJSONObject("data") ?: return emptyList()
    val users = LinkedHashMap<String, User>()
    val cards = data.optJSONArray("cards") ?: return emptyList()
    for (i in 0 until cards.length()) {
        val card = cards.optJSONObject(i) ?: continue
        searchUserOf(card)?.let { if (it.id !in users) users[it.id] = it }
        val group = card.optJSONArray("card_group") ?: continue
        for (j in 0 until group.length()) {
            val item = group.optJSONObject(j) ?: continue
            searchUserOf(item)?.let { if (it.id !in users) users[it.id] = it }
        }
    }
    return users.values.toList()
}

private fun searchUserOf(item: JSONObject): User? {
    val user = User.from(item.optJSONObject("user")) ?: return null
    val description = item.str("desc1").ifBlank { user.description }
    val followers = if (user.followers > 0) {
        user.followers
    } else {
        val raw = item.str("desc2").substringAfter("粉丝", "").trim('：', ':', ' ')
        parseCount(raw) ?: 0
    }
    return user.copy(description = description, followers = followers)
}

/**
 * 话题搜索（`100103type=38`）的卡片：`card_type` 11 的卡里，`card_type` 8 的项才是话题，
 * 名字在 `title_sub`（形如 `#话题#`），阅读/讨论数在 `desc1` / `desc2`。
 */
fun parseTopicCards(root: JSONObject?): List<TopicItem> {
    val data = root?.optJSONObject("data") ?: return emptyList()
    val topics = LinkedHashMap<String, TopicItem>()
    val cards = data.optJSONArray("cards") ?: return emptyList()
    for (i in 0 until cards.length()) {
        val card = cards.optJSONObject(i) ?: continue
        val group = card.optJSONArray("card_group") ?: continue
        for (j in 0 until group.length()) {
            val item = group.optJSONObject(j) ?: continue
            val title = item.str("title_sub").trim('#', ' ', '\u00a0')
            if (title.isBlank()) continue
            if (title in topics) continue
            topics[title] = TopicItem(
                title = title,
                desc1 = item.str("desc1"),
                desc2 = item.str("desc2"),
            )
        }
    }
    return topics.values.toList()
}
