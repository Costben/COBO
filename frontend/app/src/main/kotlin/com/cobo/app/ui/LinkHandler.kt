package com.cobo.app.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Handles link taps inside Weibo rich text. The root provides an in-app WebView
 * route so a tap never leaves the app for the system browser.
 */
val LocalLinkHandler = staticCompositionLocalOf<(String) -> Unit> { {} }

/**
 * Weibo's HTML mixes absolute URLs with site-relative paths (`/status/<id>` for the
 * "全文" link, `/n/<name>` for a mention). A relative path handed straight to a WebView
 * resolves against its `file:///` base and fails with ERR_ACCESS_DENIED, so every link
 * is turned into an absolute URL first.
 *
 * `sinaweibo://` / `intent://` 不算死链：超话（`sinaweibo://pageinfo?containerid=100808...`）和
 * 视频（`sinaweibo://video/...`）在微博正文里都是这两种私有 scheme，直接丢掉就是“点了没反应”。
 * 这里把它们翻成 m.weibo.cn / video.weibo.com 的网页地址，交给内置 WebView。
 */
fun resolveWeiboLink(raw: String): String? {
    val url = raw.trim()
    if (url.isEmpty()) return null
    weiboPrivateLink(url)?.let { return it }
    return when {
        url.startsWith("http://", ignoreCase = true) -> url
        url.startsWith("https://", ignoreCase = true) -> url
        url.startsWith("//") -> "https:$url"
        url.startsWith("/") -> "https://m.weibo.cn$url"
        url.contains("://") -> null
        else -> "https://m.weibo.cn/$url"
    }
}

private const val WeiboMobile = "https://m.weibo.cn"

/** `?a=1&b=2` → `a=1&b=2`，没有查询串时返回空串。 */
private fun queryOf(link: String): String {
    val rest = link.substringAfter('?', "")
    return rest.substringBefore('#')
}

private fun param(query: String, name: String): String? =
    query.split('&')
        .firstOrNull { it.substringBefore('=').equals(name, ignoreCase = true) }
        ?.substringAfter('=', "")
        ?.takeIf { it.isNotEmpty() }

/**
 * 微博私有 scheme → 网页地址。认不出来就返回 null，交给上层按普通链接处理。
 */
private fun weiboPrivateLink(raw: String): String? {
    val lower = raw.lowercase()
    if (lower.startsWith("intent://")) {
        // intent://gotovideo?fid=...#Intent;scheme=sinaweibo;package=...;end
        val body = raw.substring("intent://".length).substringBefore("#Intent")
        val path = body.substringBefore('?')
        val query = queryOf(body)
        return when {
            path.contains("video", ignoreCase = true) || query.contains("fid=", ignoreCase = true) ->
                param(query, "fid")?.let { "https://video.weibo.com/show?fid=$it" }
                    ?: "$WeiboMobile/s/video/index?$query".takeIf { query.isNotEmpty() }
            path.contains("pageinfo", ignoreCase = true) ->
                "$WeiboMobile/p/index?$query".takeIf { query.isNotEmpty() }
            else -> null
        }
    }
    if (!lower.startsWith("sinaweibo://")) return null
    val body = raw.substring("sinaweibo://".length)
    val path = body.substringBefore('?')
    val query = queryOf(body)
    return when {
        // 超话：sinaweibo://pageinfo?containerid=100808...&extparam=...
        path.startsWith("pageinfo") || query.contains("containerid=100808", ignoreCase = true) ->
            "$WeiboMobile/p/index?$query".takeIf { query.isNotEmpty() }

        // 视频：sinaweibo://video/play?fid=...、sinaweibo://video?fid=...
        path.startsWith("video") -> {
            val fid = param(query, "fid")
            when {
                fid != null -> "https://video.weibo.com/show?fid=$fid"
                query.isNotEmpty() -> "$WeiboMobile/s/video/index?$query"
                else -> null
            }
        }

        // 单条微博：sinaweibo://detail?mblogid=...
        path.startsWith("detail") || path.startsWith("status") -> {
            val id = param(query, "mblogid") ?: param(query, "id")
            id?.takeIf { it.all(Char::isDigit) }?.let { "$WeiboMobile/detail/$it" }
        }

        else -> null
    }
}

/**
 * 微博把「评论配图」这类链接包成 `https://weibo.cn/sinaurl?u=<真实地址>`，正文里的 @
 * 则是站内相对路径 `/n/<昵称>`。这两种都只有拆开才知道该去哪个页面，否则一律掉进 WebView。
 */
fun unwrapSinaUrl(raw: String): String? {
    var url = raw.trim()
    var changed = false
    // sinaurl 可能套两层（转发里再转发），拆到没有为止。
    repeat(3) {
        val marker = "sinaurl?"
        val at = url.indexOf(marker, ignoreCase = true)
        if (at < 0) return@repeat
        val query = url.substring(at + marker.length)
        val target = query.split('&')
            .firstOrNull { it.substringBefore('=') == "u" }
            ?.substringAfter('=', "")
            ?: return@repeat
        if (target.isEmpty()) return@repeat
        val decoded = try {
            java.net.URLDecoder.decode(target, "UTF-8")
        } catch (_: Throwable) {
            target
        }
        if (decoded.isBlank() || decoded == url) return@repeat
        url = decoded
        changed = true
    }
    return url.takeIf { changed }
}

/** `m.weibo.cn/n/<昵称>`、`/n/<昵称>` → 昵称（已解码）。 */
fun weiboMentionName(raw: String): String? {
    val path = raw.trim().substringBefore('?').substringBefore('#')
    val at = path.indexOf("/n/")
    if (at < 0) return null
    val name = path.substring(at + 3).trim('/')
    if (name.isEmpty() || name.contains('/')) return null
    return try {
        java.net.URLDecoder.decode(name, "UTF-8")
    } catch (_: Throwable) {
        name
    }
}

/** `/u/<uid>`、`/profile/<uid>`、`m.weibo.cn/u/<uid>` → uid。 */
fun weiboUserId(raw: String): String? {
    val path = raw.trim().substringBefore('?').substringBefore('#')
    for (prefix in listOf("/u/", "/profile/")) {
        val at = path.indexOf(prefix)
        if (at < 0) continue
        val id = path.substring(at + prefix.length).trim('/').substringBefore('/')
        if (id.isNotEmpty() && id.all(Char::isDigit)) return id
    }
    return null
}

/** 图片直链（sinaimg 的 large/orj360/bmiddle 等），点了应该开图而不是开网页。 */
fun isImageLink(raw: String): Boolean {
    val path = raw.trim().substringBefore('?').substringBefore('#').lowercase()
    if (!path.startsWith("http")) return false
    return path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".png") ||
        path.endsWith(".gif") || path.endsWith(".webp")
}

/**
 * Extracts the status id from a link that points at a single 微博, so "全文" and
 * shared status links open the in-app detail page instead of a WebView.
 */
fun weiboStatusId(raw: String): String? {
    var path = raw.trim().substringBefore('?').substringBefore('#')
    val scheme = path.indexOf("://")
    if (scheme >= 0) path = path.substring(scheme + 3)
    if (path.startsWith("//")) path = path.substring(2)
    val segments = path.split('/').filter { it.isNotBlank() }.toMutableList()
    if (segments.isNotEmpty() && (segments[0].contains('.') || segments[0].contains(':'))) {
        segments.removeAt(0)
    }
    val head = segments.firstOrNull() ?: return null
    val id = segments.getOrNull(1) ?: return null
    if (head != "status" && head != "detail") return null
    return id.takeIf { it.all(Char::isDigit) }
}

/** 投票直链（vote.weibo.com 或带有 vote_id 参数的页面）。 */
fun weiboVoteId(raw: String): String? = com.cobo.app.data.extractVoteId(raw)

/** 是否是 t.cn 这类短链接，点击时先探测目标地址以防掉进通用网页 WebView。 */
fun isWeiboShortLink(raw: String): Boolean {
    val trimmed = raw.trim().lowercase()
    return trimmed.startsWith("http://t.cn/") || trimmed.startsWith("https://t.cn/")
}
