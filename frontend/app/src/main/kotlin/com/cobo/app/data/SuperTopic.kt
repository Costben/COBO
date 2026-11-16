package com.cobo.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder

enum class SuperTopicTabKind {
    Feed,
    SortTime,
    Recommend,
    Soul,
    Official,
    Album,
    Video,
    Detail,
    Tag,
}

data class SuperTopicTab(
    val id: String,
    val label: String,
    val containerId: String,
    val kind: SuperTopicTabKind,
)

data class SuperTopicInfo(
    val containerId: String,
    val title: String,
    val description: String,
    val avatar: String = "",
    val readCount: String = "",
    val postCount: String = "",
    val fanCount: String = "",
)

data class SuperTopicPage(
    val info: SuperTopicInfo,
    val items: List<Status>,
    val nextCursor: String?,
    val tabs: List<SuperTopicTab> = emptyList(),
)

private val superTopicContainer = Regex("100808[0-9a-fA-F]{32}")
private val superTopicCount = Regex("(阅读|帖子|粉丝)([0-9]+(?:\\.[0-9]+)?[万亿千]?\\+?)(?=阅读|帖子|粉丝|$)")
private val superTopicVideoMid = Regex("(?:mid=|status/)([0-9]+)")

private fun normalizedSuperTopicId(value: String): String? =
    value.removeSuffix("_-_feed").takeIf { superTopicContainer.matches(it) }?.lowercase()

fun defaultSuperTopicTabs(baseContainerId: String): List<SuperTopicTab> {
    val id = normalizedSuperTopicId(baseContainerId.trim()) ?: baseContainerId.trim().lowercase()
    return listOf(
        SuperTopicTab("feed", "最新评论", "${id}_-_feed", SuperTopicTabKind.Feed),
        SuperTopicTab("sort_time", "最新发帖", "${id}_-_sort_time", SuperTopicTabKind.SortTime),
        SuperTopicTab("recommend", "热门", "${id}_-_recommend", SuperTopicTabKind.Recommend),
        SuperTopicTab("soul", "精华", "${id}_-_soul", SuperTopicTabKind.Soul),
    )
}

internal fun superTopicTabKind(containerId: String, channelId: String = ""): SuperTopicTabKind {
    val lower = containerId.lowercase()
    return when {
        lower.endsWith("_-_feed") || channelId == "feed" -> SuperTopicTabKind.Feed
        lower.endsWith("_-_sort_time") -> SuperTopicTabKind.SortTime
        lower.endsWith("_-_recommend") -> SuperTopicTabKind.Recommend
        lower.endsWith("_-_soul") -> SuperTopicTabKind.Soul
        lower.endsWith("_-_official") || channelId == "official" -> SuperTopicTabKind.Official
        lower.endsWith("_-_album") || channelId == "album" -> SuperTopicTabKind.Album
        lower.endsWith("_-_video") || channelId == "video" -> SuperTopicTabKind.Video
        lower.endsWith("_-_detail") || channelId == "detail" -> SuperTopicTabKind.Detail
        else -> SuperTopicTabKind.Tag
    }
}

/** Resolve only known supertopic links. Ordinary topic/search links keep their existing route. */
fun superTopicId(raw: String): String? {
    val value = raw.trim()
    if (superTopicContainer.matches(value)) return value.lowercase()
    val uri = runCatching { URI(value) }.getOrNull() ?: return null
    if (uri.rawUserInfo != null || uri.port != -1) return null
    val scheme = uri.scheme?.lowercase() ?: return null
    val host = uri.host?.lowercase() ?: return null
    val path = uri.rawPath.orEmpty()
    val desktopPage = (scheme == "https" || scheme == "http") &&
        (host == "weibo.com" || host == "www.weibo.com")
    val desktopId = if (desktopPage) {
        if (!path.startsWith("/page/")) return null
        normalizedSuperTopicId(path.removePrefix("/page/")) ?: return null
    } else null
    when (scheme) {
        "https", "http" -> {
            if (uri.rawFragment != null) return null
            if (!desktopPage && (host != "m.weibo.cn" || path != "/p/index")) return null
        }
        "sinaweibo" -> {
            if (host != "pageinfo" || path.isNotEmpty() || uri.rawFragment != null) return null
        }
        "intent" -> {
            if (host != "pageinfo" || path.isNotEmpty()) return null
            val parts = uri.rawFragment?.split(';') ?: return null
            if (parts.firstOrNull() != "Intent" || parts.lastOrNull() != "end" ||
                parts.count { it == "scheme=sinaweibo" } != 1 ||
                parts.any { it.startsWith("scheme=") && it != "scheme=sinaweibo" }
            ) return null
        }
        else -> return null
    }
    val params = runCatching {
        uri.rawQuery.orEmpty().split('&').map { part ->
            val pair = part.split('=', limit = 2)
            URLDecoder.decode(pair[0], "UTF-8") to
                URLDecoder.decode(pair.getOrElse(1) { "" }, "UTF-8")
        }
    }.getOrNull() ?: return null
    val ids = params.filter { it.first == "containerid" }
    if (desktopId != null) {
        if (ids.size > 1) return null
        if (ids.isNotEmpty() && normalizedSuperTopicId(ids.single().second) != desktopId) return null
        return desktopId
    }
    if (ids.size != 1) return null
    return normalizedSuperTopicId(ids.single().second)
}

/** pageInfo identity is mandatory, including on an empty or final page. */
fun parseSuperTopic(root: JSONObject, expectedContainerId: String): SuperTopicPage =
    parseSuperTopicInternal(root, expectedContainerId, tabContainerId = null, currentPage = 1)

/** Parses any supertopic channel/sub-tab (`_-_feed`, `_-_sort_time`, `_-_recommend`, `_-_soul`, `_-_official`, `_-_album`, `_-_video`, tag channels). */
fun parseSuperTopicTab(
    root: JSONObject,
    expectedContainerId: String,
    tabContainerId: String,
    currentPage: Int = 1,
): SuperTopicPage =
    parseSuperTopicInternal(root, expectedContainerId, tabContainerId = tabContainerId, currentPage = currentPage)

private fun parseSuperTopicInternal(
    root: JSONObject,
    expectedContainerId: String,
    tabContainerId: String?,
    currentPage: Int,
): SuperTopicPage {
    val expected = normalizedSuperTopicId(expectedContainerId)
        ?: throw ApiException("无效的超话地址")
    if (root.optInt("ok", 0) == -100) throw ApiException("超话需要登录后查看", "-100")
    if (root.optInt("ok", 0) != 1) throw ApiException("超话加载失败")
    val data = root.optJSONObject("data") ?: throw ApiException("超话数据缺失")
    val page = data.optJSONObject("pageInfo") ?: throw ApiException("超话信息缺失")
    val actualCid = page.str("containerid").trim().lowercase()
    val validIdentity = if (tabContainerId == null) {
        normalizedSuperTopicId(actualCid) == expected
    } else {
        val expectedTab = tabContainerId.trim().lowercase()
        actualCid == expectedTab ||
            (expectedTab == "${expected}_-_feed" && actualCid == expected) ||
            actualCid.startsWith("${expected}_-_") ||
            actualCid.startsWith("${expected}__")
    }
    if (!validIdentity) throw ApiException("超话身份校验失败")
    val title = page.str("page_title").trim()
    if (title.isBlank()) throw ApiException("超话名称缺失")
    val items = LinkedHashMap<String, Status>()
    collectSuperTopicCards(data.optJSONArray("cards"), items)
    val rawSince = page.str("since_id").trim()
    val cursor = if (tabContainerId == null) {
        // Read as a string directly. These numeric cursors exceed Int and must never pass through Double.
        rawSince.takeIf { it.matches(Regex("[0-9]+")) && it.any { digit -> digit != '0' } }
    } else {
        resolveSuperTopicTabCursor(rawSince, tabContainerId, items.size, currentPage)
    }
    val counts = LinkedHashMap<String, String>()
    val descriptions = page.optJSONArray("desc_more")
    if (descriptions != null) {
        for (i in 0 until descriptions.length()) {
            val text = (descriptions.opt(i) as? String)?.replace(Regex("[\\s\u3000]+"), "") ?: continue
            for (match in superTopicCount.findAll(text)) {
                counts.putIfAbsent(match.groupValues[1], match.groupValues[2])
            }
        }
    }
    val tabs = extractSuperTopicTabs(data, page, expected)
    return SuperTopicPage(
        info = SuperTopicInfo(
            containerId = expected,
            title = title,
            description = page.str("desc"),
            avatar = page.str("portrait_hd").ifBlank { page.str("portrait") },
            readCount = counts["阅读"].orEmpty(),
            postCount = counts["帖子"] ?: page.str("total").takeIf { it.matches(Regex("[0-9]+")) }.orEmpty(),
            fanCount = counts["粉丝"].orEmpty(),
        ),
        items = items.values.toList(),
        nextCursor = cursor,
        tabs = tabs,
    )
}

private fun resolveSuperTopicTabCursor(
    rawSince: String,
    tabContainerId: String,
    itemCount: Int,
    currentPage: Int,
): String? {
    if (itemCount <= 0) return null
    if (rawSince.isBlank() || rawSince.equals("null", ignoreCase = true) || rawSince.startsWith("-")) return null
    if (rawSince.all { it == '0' }) return null
    if (tabContainerId.lowercase().endsWith("_-_recommend") && rawSince == "1") {
        return "page:${currentPage + 1}"
    }
    return rawSince
}

internal fun extractSuperTopicTabs(
    data: JSONObject,
    page: JSONObject,
    expectedContainerId: String,
): List<SuperTopicTab> {
    val result = LinkedHashMap<String, SuperTopicTab>()
    fun addTab(id: String, name: String, containerId: String) {
        val label = name.trim()
        val cid = containerId.trim()
        val lower = cid.lowercase()
        if (label.isBlank() || cid.isBlank()) return
        if (!lower.startsWith(expectedContainerId)) return
        result.putIfAbsent(
            lower,
            SuperTopicTab(
                id = id.ifBlank { cid },
                label = label,
                containerId = cid,
                kind = superTopicTabKind(lower, id),
            ),
        )
    }

    val cards = data.optJSONArray("cards")
    if (cards != null) {
        for (i in 0 until cards.length()) {
            val card = cards.optJSONObject(i) ?: continue
            val group = card.optJSONArray("card_group") ?: continue
            for (j in 0 until group.length()) {
                val sub = group.optJSONObject(j) ?: continue
                if (sub.str("itemid") != "page_feed_child_tab" && sub.str("card_type") != "121") continue
                val filters = sub.optJSONArray("filter_group") ?: continue
                for (k in 0 until filters.length()) {
                    val f = filters.optJSONObject(k) ?: continue
                    val cid = f.str("containerid")
                    addTab(cid.substringAfter("_-_", "feed"), f.str("name"), cid)
                }
            }
        }
    }

    if (result.isEmpty()) {
        for (tab in defaultSuperTopicTabs(expectedContainerId)) {
            result.putIfAbsent(tab.containerId.lowercase(), tab)
        }
    }

    val headCards = page.optJSONArray("cardlist_head_cards")
    if (headCards != null) {
        for (i in 0 until headCards.length()) {
            val hc = headCards.optJSONObject(i) ?: continue
            val channels = hc.optJSONArray("channel_list") ?: continue
            for (j in 0 until channels.length()) {
                val ch = channels.optJSONObject(j) ?: continue
                val chId = ch.str("id")
                val cid = ch.str("containerid")
                if (chId == "feed" || cid.lowercase() == "${expectedContainerId}_-_feed") continue
                addTab(chId, ch.str("name"), cid)
            }
        }
    }
    return result.values.toList()
}

private fun collectSuperTopicCards(cards: JSONArray?, items: MutableMap<String, Status>) {
    if (cards == null) return
    for (i in 0 until cards.length()) {
        val card = cards.optJSONObject(i) ?: continue
        Status.from(card.optJSONObject("mblog"))?.let { items.putIfAbsent(it.id, it) }
        val cardType = card.str("card_type")
        if (cardType == "47") {
            val pics = card.optJSONArray("pics")
            if (pics != null) {
                for (p in 0 until pics.length()) {
                    val picObj = pics.optJSONObject(p) ?: continue
                    Status.from(picObj.optJSONObject("mblog"))?.let { items.putIfAbsent(it.id, it) }
                }
            }
        } else if (cardType == "52") {
            val videoItems = card.optJSONArray("items")
            if (videoItems != null) {
                for (v in 0 until videoItems.length()) {
                    val item = videoItems.optJSONObject(v) ?: continue
                    superTopicVideoStatus(item)?.let { items.putIfAbsent(it.id, it) }
                }
            }
        }
        collectSuperTopicCards(card.optJSONArray("card_group"), items)
    }
}

private fun superTopicVideoStatus(item: JSONObject): Status? {
    val rawScheme = item.str("scheme").ifBlank { item.str("pic_scheme") }
    val decodedScheme = runCatching { URLDecoder.decode(rawScheme, "UTF-8") }.getOrDefault(rawScheme)
    val mid = superTopicVideoMid.find(decodedScheme)?.groupValues?.getOrNull(1).orEmpty()
    if (mid.isBlank()) return null
    val title = item.str("title").ifBlank { "超话视频" }
    val author = item.str("desc1").ifBlank { "超话视频" }
    val pic = item.str("pic")
    val duration = item.optDouble("video_duration", 0.0)
    val durationLabel = if (duration > 0.0) {
        val sec = duration.toInt()
        String.format("%02d:%02d", sec / 60, sec % 60)
    } else ""
    return Status(
        id = mid,
        mid = mid,
        textHtml = title,
        createdAt = durationLabel,
        source = "超话视频",
        user = User(id = "", name = author, avatar = ""),
        pics = if (pic.isNotBlank()) listOf(Pic(thumb = pic, large = pic)) else emptyList(),
    )
}

/** Comprehensive search may contain native supertopic cards alongside ordinary search results. */
fun parseSuperTopicSearch(root: JSONObject?): List<TopicItem> {
    if (root?.optInt("ok", 0) == -100) throw ApiException("超话需要登录后查看", "-100")
    if (root?.optInt("ok", 0) != 1) return emptyList()
    val topics = LinkedHashMap<String, TopicItem>()
    collectSuperTopicSearchCards(root.optJSONObject("data")?.optJSONArray("cards"), topics)
    return topics.values.toList()
}

private fun collectSuperTopicSearchCards(cards: JSONArray?, topics: MutableMap<String, TopicItem>) {
    if (cards == null) return
    for (i in 0 until cards.length()) {
        val card = cards.optJSONObject(i) ?: continue
        if (card.has("mblog")) continue
        val scheme = card.opt("scheme") as? String ?: ""
        val containerId = superTopicId(card.opt("containerid") as? String ?: "") ?: superTopicId(scheme)
        val title = stripHtml(card.opt("title_sub") as? String).trim('#', ' ', '\u00a0')
            .ifBlank { stripHtml(card.opt("title") as? String).trim('#', ' ', '\u00a0') }
            .ifBlank { stripHtml(card.opt("item_title") as? String).trim('#', ' ', '\u00a0') }
        if (containerId != null && title.isNotBlank()) {
            topics.putIfAbsent(
                containerId,
                TopicItem(
                    title = title,
                    desc1 = (card.opt("desc1") as? String).orEmpty()
                        .ifBlank { card.opt("item_desc") as? String ?: "" },
                    desc2 = card.opt("desc2") as? String ?: "",
                    scheme = scheme,
                    containerId = containerId,
                ),
            )
        }
        collectSuperTopicSearchCards(card.optJSONArray("card_group"), topics)
        collectSuperTopicSearchCards(card.optJSONArray("group"), topics)
    }
}
