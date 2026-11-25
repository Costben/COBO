package com.cobo.app.data

import org.json.JSONObject

internal data class FollowedSuperTopic(val id: String, val name: String, val avatarUrl: String?)

internal const val FOLLOWED_SUPERTOPICS_TAB = "231093_-_chaohua"
internal const val FOLLOWED_SUPERTOPICS_PAGE_LIMIT = 50

internal fun followedSuperTopicsWebUrl(uid: String): String? =
    uid.takeIf { it.matches(Regex("[0-9]+")) }
        ?.let { "https://weibo.com/u/page/follow/$it/$FOLLOWED_SUPERTOPICS_TAB" }

internal suspend fun loadFollowedSuperTopicPages(
    load: suspend (Int) -> JSONObject,
): List<FollowedSuperTopic> {
    val topics = linkedMapOf<String, FollowedSuperTopic>()
    var finalPage = 1
    var page = 1
    while (page <= finalPage) {
        val root = load(page)
        if (root.optInt("ok") != 1) throw ApiException("微博没有返回已关注超话")
        val data = root.optJSONObject("data") ?: throw ApiException("已关注超话响应格式有变")
        val cards = data.optJSONArray("list") ?: throw ApiException("已关注超话响应格式有变")
        val maxPage = data.optInt("max_page", -1)
        if (page == 1) {
            if (maxPage !in 0..FOLLOWED_SUPERTOPICS_PAGE_LIMIT || (maxPage == 0 && cards.length() > 0)) {
                throw ApiException("无法确认已关注超话的完整页数")
            }
            finalPage = maxPage.coerceAtLeast(1)
        } else if (maxPage != finalPage || cards.length() == 0) {
            throw ApiException("已关注超话分页不完整，请重试")
        }
        if (page == 1 && finalPage > 1 && cards.length() == 0) {
            throw ApiException("已关注超话分页不完整，请重试")
        }
        val before = topics.size
        for (i in 0 until cards.length()) {
            val card = cards.optJSONObject(i) ?: throw ApiException("已关注超话条目格式有变")
            val id = superTopicId(card.optString("oid").removePrefix("1022:"))
                ?: throw ApiException("已关注超话缺少有效 ID")
            val name = card.optString("topic_name").trim()
            if (name.isEmpty()) throw ApiException("已关注超话缺少名称")
            val avatarUrl = sequenceOf("icon", "portrait", "pic", "pic_url", "avatar")
                .map { card.optString(it).let { raw -> if (raw.startsWith("//")) "https:$raw" else raw } }
                .firstOrNull { it.startsWith("https://") }
            topics.putIfAbsent(id, FollowedSuperTopic(id, name, avatarUrl))
        }
        if (page > 1 && before == topics.size) throw ApiException("已关注超话分页重复，请重试")
        page++
    }
    return topics.values.toList()
}

/** Desktop web session only. Errors must not turn an unknown list into an empty list. */
internal suspend fun Repo.followedSuperTopics(): List<FollowedSuperTopic> {
    if (!hasSession()) throw ApiException("登录微博后查看已关注超话", "-100")
    val auth = desktopCookieOrNull()?.takeIf { it.isNotBlank() }
        ?: throw ApiException("当前账号没有可用的微博网页登录态")
    return try {
        loadFollowedSuperTopicPages { page ->
            Api.getDesktop(
                Api.url(
                    "https://weibo.com/ajax/profile/topicContent",
                    mapOf("tabid" to FOLLOWED_SUPERTOPICS_TAB, "page" to page.toString()),
                ),
                auth,
            )
        }
    } catch (e: ApiHttpException) {
        throw ApiException("读取已关注超话失败（HTTP ${e.code}），可查看官网名单")
    }
}
