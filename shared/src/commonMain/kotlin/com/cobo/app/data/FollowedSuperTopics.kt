package com.cobo.app.data

import com.cobo.app.platform.putIfAbsent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.cobo.app.json.JSONObject

data class FollowedSuperTopic(
    val id: String,
    val name: String,
    val avatarUrl: String?,
)

data class FollowedSuperTopicPage(
    val items: List<FollowedSuperTopic>,
    val page: Int,
    val maxPage: Int,
    val hasMore: Boolean,
)

internal const val FOLLOWED_SUPERTOPICS_TAB = "231093_-_chaohua"
internal const val FOLLOWED_SUPERTOPICS_PAGE_LIMIT = 50

internal fun followedSuperTopicsWebUrl(uid: String): String? =
    uid.takeIf { it.matches(Regex("[0-9]+")) }
        ?.let { "https://weibo.com/u/page/follow/$it/$FOLLOWED_SUPERTOPICS_TAB" }

/**
 * 解析桌面端 `weibo.com/ajax/profile/topicContent` 单页响应。
 *
 * - 明确区分「已确认空列表」与「鉴权失败 / 结构变化 / 分页不完整」，错误绝不静默返回空列表。
 * - 忽略混入列表的个别非超话/残缺条目；但若当页非空却全部不可解析，则判定格式变化并抛错。
 */
internal fun parseFollowedSuperTopicPage(root: JSONObject, page: Int): FollowedSuperTopicPage {
    val ok = root.int("ok", 0)
    if (ok == -100) throw ApiException("登录态已失效，请重新登录后查看", "-100")
    if (ok != 1) throw ApiException("微博没有返回已关注超话")

    val data = root.optJSONObject("data") ?: throw ApiException("已关注超话响应格式有变")
    val cards = data.optJSONArray("list") ?: throw ApiException("已关注超话响应格式有变")
    val maxPage = data.int("max_page", -1)
    if (maxPage < 0 || (maxPage == 0 && cards.length() > 0)) {
        throw ApiException("无法确认已关注超话的分页信息")
    }
    if (page == 1 && maxPage > 1 && cards.length() == 0) {
        throw ApiException("已关注超话分页不完整，请重试")
    }
    if (page > 1 && page <= maxPage && cards.length() == 0) {
        throw ApiException("已关注超话分页不完整，请重试")
    }
    if (page > maxPage && cards.length() == 0) {
        return FollowedSuperTopicPage(
            items = emptyList(),
            page = page,
            maxPage = maxPage,
            hasMore = false,
        )
    }

    val topics = linkedMapOf<String, FollowedSuperTopic>()
    for (i in 0 until cards.length()) {
        val card = cards.optJSONObject(i) ?: continue
        val rawOid = card.str("oid").removePrefix("1022:").trim()
        val id = superTopicId(rawOid)
            ?: superTopicId(card.str("scheme"))
            ?: superTopicId(card.str("containerid"))
            ?: continue
        val name = card.str("topic_name").trim().ifEmpty { card.str("title_sub").trim() }
        if (name.isEmpty()) continue
        val avatarUrl = sequenceOf("icon", "portrait", "pic", "pic_url", "avatar")
            .map { normalizeWeiboMediaUrl(card.str(it)) }
            .firstOrNull { it.startsWith("https://") || it.startsWith("http://") }
        topics.putIfAbsent(id, FollowedSuperTopic(id, name, avatarUrl))
    }

    if (cards.length() > 0 && topics.isEmpty()) {
        throw ApiException("已关注超话条目格式有变")
    }

    val hasMore = page < maxPage && page < FOLLOWED_SUPERTOPICS_PAGE_LIMIT && cards.length() > 0
    return FollowedSuperTopicPage(
        items = topics.values.toList(),
        page = page,
        maxPage = maxPage,
        hasMore = hasMore,
    )
}

/** 读取已关注超话的指定页（1-based）。鉴权失败或结构异常一律抛 [ApiException]，不伪装为空列表。 */
internal suspend fun Repo.followedSuperTopicsPage(page: Int = 1): FollowedSuperTopicPage {
    val safePage = page.coerceAtLeast(1)
    if (!hasSession()) throw ApiException("登录微博后查看已关注超话", "-100")
    val url = Api.url(
        "https://weibo.com/ajax/profile/topicContent",
        mapOf("tabid" to FOLLOWED_SUPERTOPICS_TAB, "page" to safePage.toString()),
    )
    val root = try {
        desktopGetAuthenticated(url)
    } catch (e: ApiHttpException) {
        if (e.code == 401 || e.code == 403 || e.code == 432) {
            throw ApiException("登录态已失效，请重新登录或查看官网名单（HTTP ${e.code}）", e.code.toString())
        }
        throw ApiException("读取已关注超话失败（HTTP ${e.code}），可查看官网名单")
    }
    return parseFollowedSuperTopicPage(root, safePage)
}

/** 解析桌面端超话扩展接口（`/ajax_proxy/chaohua/page/extend`）中的当前关注状态。 */
internal fun parseSuperTopicFollowState(root: JSONObject): Boolean {
    val code = root.int("code", 0)
    val ok = root.int("ok", 1)
    if (code == 100003 || ok == -100) {
        throw ApiException("登录态已失效，请重新登录", "-100")
    }
    if (root.has("is_followed") && root.opt("is_followed") != JSONObject.NULL) {
        return root.bool("is_followed")
    }
    if (root.has("is_follow") && root.opt("is_follow") != JSONObject.NULL) {
        return root.bool("is_follow")
    }
    val btnText = root.str("follow_btn_text").trim()
    if (btnText == "已关注") return true
    if (btnText == "关注") return false
    throw ApiException("无法确认超话关注状态")
}

/**
 * 解析桌面端超话关注 / 取消关注响应：
 * - 关注（`/ajax_proxy/chaohua/page/follow`）：成功返回 `code == 100000` 且无 `scheme` 拦截、文案为 `已关注`（或未下发兼容文案）。
 * - 取消关注（`/ajax_proxy/chaohua/page/unfollow`）：成功返回 `code == 10000`。
 * - 未登录 / 会话失效：`code == 100003` 或 `ok == -100`。
 */
internal fun parseSuperTopicFollowMutation(root: JSONObject, targetFollow: Boolean): Boolean {
    val code = root.int("code", 0)
    val ok = root.int("ok", 0)
    val msg = root.str("msg").ifBlank { root.str("message") }.trim()
    if (code == 100003 || ok == -100 || msg == "请先登录") {
        throw ApiException("登录态已失效，请重新登录", "-100")
    }
    if (targetFollow) {
        if (code == 100000) {
            if (root.str("scheme").isNotBlank()) {
                throw ApiException(msg.ifBlank { "需要在微博网页完成验证后再关注" })
            }
            val btnText = root.str("follow_btn_text").trim()
            if (btnText.isEmpty() || btnText == "已关注" || root.bool("is_followed")) {
                return true
            }
            throw ApiException(msg.ifBlank { "关注超话失败" })
        }
        if (code == 10000 && (root.bool("is_followed") || root.str("follow_btn_text").trim() == "已关注")) {
            return true
        }
        throw ApiException(msg.ifBlank { "关注超话失败" })
    } else {
        if (code == 10000) return false
        if (code == 100000 && root.str("scheme").isBlank()) {
            val btnText = root.str("follow_btn_text").trim()
            if (btnText == "关注" || (root.has("is_followed") && !root.bool("is_followed"))) {
                return false
            }
        }
        throw ApiException(msg.ifBlank { "取消关注超话失败" })
    }
}

/** 查询当前账号是否已关注指定超话，并同步到本地内存覆盖表。 */
internal suspend fun Repo.superTopicFollowState(containerId: String): Boolean {
    val id = superTopicId(containerId) ?: throw ApiException("无效的超话地址")
    if (!hasSession()) throw ApiException("请先登录微博", "-100")
    val url = Api.url(
        "https://weibo.com/ajax_proxy/chaohua/page/extend",
        mapOf("page_id" to id),
    )
    val root = try {
        desktopGetAuthenticated(url)
    } catch (e: ApiHttpException) {
        if (e.code == 401 || e.code == 403 || e.code == 432) {
            throw ApiException("登录态已失效，请重新登录（HTTP ${e.code}）", e.code.toString())
        }
        throw ApiException("查询超话关注状态失败（HTTP ${e.code}）")
    }
    val followed = parseSuperTopicFollowState(root)
    withContext(Dispatchers.Main.immediate) {
        recordSuperTopicFollowState(id, followed, bumpVersion = false)
    }
    return followed
}

/** 关注或取消关注指定超话。成功时更新本地覆盖表并递增版本号，触发已关注列表自动刷新。 */
internal suspend fun Repo.setSuperTopicFollow(containerId: String, follow: Boolean): Boolean {
    val id = superTopicId(containerId) ?: throw ApiException("无效的超话地址")
    if (!hasSession()) throw ApiException("请先登录微博", "-100")
    if (!superTopicFollowInFlight.add(id)) return false
    try {
        val path = if (follow) {
            "/ajax_proxy/chaohua/page/follow"
        } else {
            "/ajax_proxy/chaohua/page/unfollow"
        }
        val root = try {
            desktopPostFormAuthenticated(path, form = mapOf("page_id" to id))
        } catch (e: ApiHttpException) {
            if (e.code == 401 || e.code == 403 || e.code == 432) {
                throw ApiException("登录态已失效，请重新登录（HTTP ${e.code}）", e.code.toString())
            }
            throw ApiException("${if (follow) "关注" else "取消关注"}超话失败（HTTP ${e.code}）")
        }
        val actual = parseSuperTopicFollowMutation(root, targetFollow = follow)
        withContext(Dispatchers.Main.immediate) {
            recordSuperTopicFollowState(id, actual, bumpVersion = true)
        }
        return true
    } finally {
        superTopicFollowInFlight.remove(id)
    }
}
