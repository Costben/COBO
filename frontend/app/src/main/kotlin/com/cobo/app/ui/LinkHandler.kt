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

private val ExternalVideoHosts = listOf(
    "iqiyi.com",
    "v.qq.com",
    "bilibili.com",
    "b23.tv",
    "youku.com",
    "mgtv.com",
    "ixigua.com",
    "miaopai.com",
    "pearvideo.com",
    "acfun.cn",
    "le.com",
    "tv.sohu.com",
)

/**
 * 判断链接是否为视频直链、微博视频页或主流第三方视频官链（爱奇艺、B站、腾讯视频、优酷等）。
 * 命中后交给内置视频解析/播放页处理，避免在普通 WebView 里跳端失败或无法下载。
 */
fun isVideoLink(raw: String): Boolean {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return false
    val lower = trimmed.lowercase()
    if (lower.startsWith("sinaweibo://video") || lower.startsWith("intent://gotovideo")) return true
    val path = lower.substringBefore('?').substringBefore('#')
    if (path.endsWith(".mp4") || path.endsWith(".m4v") || path.endsWith(".mov") ||
        path.endsWith(".webm") || path.endsWith(".m3u8")
    ) {
        return true
    }
    val uri = runCatching { java.net.URI(trimmed) }.getOrNull() ?: return false
    val host = uri.host?.lowercase() ?: return false
    val uriPath = uri.rawPath.orEmpty().lowercase()
    if (host == "video.weibo.com" || host == "h5.video.weibo.com") return true
    if ((host == "weibo.com" || host == "www.weibo.com") && uriPath.startsWith("/tv/")) return true
    if (host == "m.weibo.cn" && (uriPath.startsWith("/s/video/") || uriPath.startsWith("/z/video/"))) return true
    return ExternalVideoHosts.any { domain -> host == domain || host.endsWith(".$domain") }
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

/**
 * 普通 `#话题#` 搜索链接 → 话题文本。排除 `100808` 超话链接（超话走原生超话页）。
 * 支持 `m.weibo.cn/search?containerid=231522...|100103...`、`s.weibo.com/weibo?q=...`、
 * `s.weibo.com/weibo/<话题>`、`m.weibo.cn/k/<话题>` 及 `sinaweibo://searchall?...`。
 */
fun weiboTopicQuery(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    if (com.cobo.app.data.superTopicId(trimmed) != null) return null

    fun decode(value: String): String = try {
        java.net.URLDecoder.decode(value, "UTF-8")
    } catch (_: Throwable) {
        value
    }

    fun wrapHash(value: String): String =
        if (value.startsWith("#") && value.endsWith("#")) value else "#$value#"

    val path = trimmed.substringBefore('?')
    val lowerPath = path.lowercase()
    val kIdx = lowerPath.indexOf("/k/")
    if (kIdx >= 0 && (lowerPath.contains("weibo.cn") || lowerPath.contains("weibo.com") || lowerPath.startsWith("/k/"))) {
        val rawName = decode(path.substring(kIdx + 3).trim('/')).trim()
        if (rawName.isNotEmpty() && !rawName.contains('/')) {
            return wrapHash(rawName)
        }
    }

    val sWeiboMarker = "s.weibo.com/weibo/"
    val sWeiboIdx = lowerPath.indexOf(sWeiboMarker)
    if (sWeiboIdx >= 0) {
        val rawSeg = decode(path.substring(sWeiboIdx + sWeiboMarker.length).trim('/')).trim()
        if (rawSeg.isNotEmpty() && !rawSeg.contains('/')) {
            return wrapHash(rawSeg)
        }
    }

    // Topic URLs often put raw `#话题#` in query params (`q=#早安#`), so do not truncate at `#`
    // before extracting `&`-delimited query parameters.
    val rawQuery = trimmed.substringAfter('?', "").substringBefore("#Intent")
    if (rawQuery.isEmpty()) return null

    val cid = param(rawQuery, "containerid")?.let(::decode)
    if (cid != null && (cid.startsWith("100103") || cid.startsWith("231522"))) {
        val cidQuery = cid.substringAfter('?', cid)
        param(cidQuery, "q")?.let(::decode)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        param(cidQuery, "extparam")?.let(::decode)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        param(rawQuery, "q")?.let(::decode)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        param(rawQuery, "extparam")?.let(::decode)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    }

    val looksLikeSearchHost = lowerPath.contains("/search") ||
        lowerPath.contains("s.weibo.com/weibo") ||
        lowerPath.startsWith("sinaweibo://searchall")
    if (looksLikeSearchHost) {
        param(rawQuery, "q")?.let(::decode)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        param(rawQuery, "extparam")?.let(::decode)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    }
    return null
}

/** 是否是 t.cn 这类短链接，点击时先探测目标地址以防掉进通用网页 WebView。 */
fun isWeiboShortLink(raw: String): Boolean {
    val trimmed = raw.trim().lowercase()
    return trimmed.startsWith("http://t.cn/") || trimmed.startsWith("https://t.cn/")
}

/**
 * 微博小程序（wbox / 微博微服务）链接在网页端只会展示「打开微博客户端体验」引导页。
 */
fun isWeiboWboxLink(raw: String): Boolean {
    val uri = runCatching { java.net.URI(raw.trim()) }.getOrNull() ?: return false
    val scheme = uri.scheme?.lowercase() ?: return false
    val host = uri.host?.lowercase() ?: return false
    val path = uri.rawPath.orEmpty().lowercase()
    return when (scheme) {
        "https", "http" ->
            (host == "m.weibo.cn" && (path == "/c/wbox" || path.startsWith("/c/wbox/"))) ||
                host == "wbox.h5.weibo.cn"
        "sinaweibo" -> host == "wbox" || host == "mpdialog"
        else -> false
    }
}
