package com.cobo.app.ui

import com.cobo.app.platform.putIfAbsent

import com.cobo.app.platform.PlatformContext
import com.cobo.app.platform.openExternalUrl as platformOpenExternalUrl
import com.cobo.app.platform.parseUrlHost
import com.cobo.app.platform.parseUrlPath
import com.cobo.app.platform.urlDecode

import androidx.compose.runtime.staticCompositionLocalOf
import com.cobo.app.data.CookieVault
import com.cobo.app.data.Status

/**
 * Handles link taps inside Weibo rich text. The root provides an in-app WebView
 * route so a tap never leaves the app for the system browser.
 */
val LocalLinkHandler = staticCompositionLocalOf<(String) -> Unit> { {} }

/** 卡片菜单触发「编辑微博」时由根节点推入编辑页。 */
val LocalStatusEditor = staticCompositionLocalOf<(Status) -> Unit> { {} }

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
    val url = unwrapWeiboLink(raw)
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
private const val WeiboLiveWebShowPrefix = "https://weibo.com/l/wblive/m/show/"

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

private fun decodeUrlParam(value: String): String =
    runCatching { urlDecode(value) }.getOrDefault(value).trim()

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
        val liveId = extractWeiboLiveId(raw)
        if (liveId != null) return "$WeiboLiveWebShowPrefix$liveId"
        return when {
            path.contains("video", ignoreCase = true) || query.contains("fid=", ignoreCase = true) ->
                param(query, "fid")?.let { "https://video.weibo.com/show?fid=$it" }
                    ?: "$WeiboMobile/s/video/index?$query".takeIf { query.isNotEmpty() }
            path.contains("pageinfo", ignoreCase = true) ->
                "$WeiboMobile/p/index?$query".takeIf { query.isNotEmpty() }
            else -> {
                val intentTail = raw.substringAfter("#Intent;", "")
                intentTail.split(';')
                    .firstOrNull { it.startsWith("S.browser_fallback_url=", ignoreCase = true) }
                    ?.substringAfter('=')
                    ?.let(::decodeUrlParam)
                    ?.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
            }
        }
    }
    val isSinaWeibo = lower.startsWith("sinaweibo://")
    val isWeibo = lower.startsWith("weibo://")
    if (!isSinaWeibo && !isWeibo) return null
    val body = raw.substring(if (isSinaWeibo) "sinaweibo://".length else "weibo://".length)
    val path = body.substringBefore('?')
    val query = queryOf(body)
    extractWeiboLiveId(raw)?.let { return "$WeiboLiveWebShowPrefix$it" }
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
            id?.takeIf { it.isNotEmpty() && it.all(Char::isLetterOrDigit) }?.let { "$WeiboMobile/detail/$it" }
        }

        // 个人主页：sinaweibo://userinfo?uid=...
        path.startsWith("userinfo") -> {
            val uid = param(query, "uid")
            uid?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.let { "$WeiboMobile/u/$it" }
        }

        // 内置浏览器跳转：sinaweibo://browser?url=...
        path.startsWith("browser") -> {
            val target = param(query, "url")?.let(::decodeUrlParam)
            target?.takeIf {
                it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)
            }
        }

        else -> null
    }
}

/**
 * 拆掉 `sinaweibo://browser?url=<encoded>` / `weibo://browser?url=<encoded>` 外壳，
 * 还原真实目标链接，避免外部深链（如系统流体云下发的跳端地址）直接落入内置 WebView。
 */
fun unwrapWeiboBrowserUrl(raw: String): String? {
    val trimmed = raw.trim()
    val lower = trimmed.lowercase()
    if (!lower.startsWith("sinaweibo://browser") && !lower.startsWith("weibo://browser")) return null
    val target = param(queryOf(trimmed), "url") ?: return null
    val decoded = decodeUrlParam(target)
    return decoded.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
}

/** 拆掉 `m.weibo.cn/feature/openapp?scheme=<encoded>` 跳端包装，还原真实目标 scheme 或 URL。 */
fun unwrapWeiboOpenAppUrl(raw: String): String? {
    val trimmed = raw.trim()
    val path = trimmed.substringBefore('?').substringBefore('#').lowercase()
    if (!path.contains("/feature/openapp")) return null
    val query = queryOf(trimmed)
    val rawTarget = param(query, "scheme") ?: param(query, "url") ?: return null
    val decoded = decodeUrlParam(rawTarget)
    return decoded.takeIf { it.isNotBlank() && it != trimmed }
}

/** 剥除 `sinaweibo://browser`、`m.weibo.cn/feature/openapp` 与 `weibo.cn/sinaurl` 多层包装。 */
fun unwrapWeiboLink(raw: String): String {
    var current = raw.trim()
    repeat(4) {
        val next = unwrapWeiboBrowserUrl(current)
            ?: unwrapWeiboOpenAppUrl(current)
            ?: unwrapSinaUrl(current)
            ?: return current
        if (next == current || next.isBlank()) return current
        current = next.trim()
    }
    return current
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
            urlDecode(target)
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
        urlDecode(name)
    } catch (_: Throwable) {
        name
    }
}

/** `/u/<uid>`、`/profile/<uid>`、`m.weibo.cn/u/<uid>`、`weibo.com/<uid>`、`sinaweibo://userinfo?uid=<uid>` → uid。 */
fun weiboUserId(raw: String): String? {
    val trimmed = raw.trim()
    val lower = trimmed.lowercase()
    if (lower.startsWith("sinaweibo://userinfo") || lower.startsWith("weibo://userinfo")) {
        val uid = param(queryOf(trimmed), "uid")
        if (!uid.isNullOrEmpty() && uid.all(Char::isDigit)) return uid
    }
    val path = trimmed.substringBefore('?').substringBefore('#')
    for (prefix in listOf("/u/", "/profile/")) {
        val at = path.indexOf(prefix)
        if (at < 0) continue
        val id = path.substring(at + prefix.length).trim('/').substringBefore('/')
        if (id.isNotEmpty() && id.all(Char::isDigit)) return id
    }
    val uri = runCatching { ParsedUri(trimmed) }.getOrNull() ?: return null
    val host = uri.host?.lowercase() ?: return null
    if (host == "weibo.com" || host == "www.weibo.com" || host == "m.weibo.cn" || host == "weibo.cn") {
        val segments = uri.rawPath.orEmpty().split('/').filter { it.isNotBlank() }
        if (segments.size == 1 && segments[0].all(Char::isDigit)) {
            return segments[0]
        }
    }
    return null
}

/** 图片直链（sinaimg 的 large/orj360/bmiddle 等），点了应该开图而不是开网页。 */
fun isImageLink(raw: String): Boolean {
    val path = raw.trim().substringBefore('?').substringBefore('#').lowercase()
    if (!path.startsWith("http") && !path.startsWith("//")) return false
    return path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".png") ||
        path.endsWith(".gif") || path.endsWith(".webp")
}

private val PhotoPidPathRegex = Regex("""/pid/([A-Za-z0-9]{16,40})\b""", RegexOption.IGNORE_CASE)
private val HtmlPicPidJsonRegex = Regex("""["'](?:pic_id|pid)["']\s*:\s*["']([A-Za-z0-9]{16,40})["']""")
private val SinaImgUrlRegex = Regex(
    """(?:https?:)?//[a-z0-9.-]*sinaimg\.(?:cn|com)/(?:large|original|woriginal|oslarge|small|square|mw\d+|orj\d+|bmiddle|thumbnail|thumb\d+|wap\d+)/[A-Za-z0-9_-]+\.(?:jpg|jpeg|png|gif|webp)(?:\?[^"'\s<>\\]*)?""",
    RegexOption.IGNORE_CASE,
)
private val SinaImgSizeSegmentRegex = Regex(
    """/(?:original|woriginal|oslarge|small|square|mw\d+|orj\d+|bmiddle|thumbnail|thumb\d+|wap\d+)/""",
    RegexOption.IGNORE_CASE,
)
private val HtmlAnchorRegex = Regex(
    """<a\b([^>]*)>(.*?)</a>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)
private val AnchorHrefRegex = Regex(
    """\bhref\s*=\s*(?:"([^"]*)"|'([^']*)')""",
    RegexOption.IGNORE_CASE,
)
private val HtmlTagStripRegex = Regex("""<[^>]+>""")
private val InlinePhotoExactLabels = setOf("查看图片", "评论配图", "查看原图", "图片评论", "转发配图", "图片")

private fun isInlinePhotoLinkLabel(label: String): Boolean {
    val trimmed = label.trim()
    if (trimmed in InlinePhotoExactLabels) return true
    return trimmed.startsWith("查看图片") || trimmed.startsWith("评论配图")
}

private fun unescapeHtmlAttrUrl(raw: String): String = raw
    .replace("&amp;", "&")
    .replace("&quot;", "\"")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .trim()

private fun normalizeProtocolUrl(url: String): String = when {
    url.startsWith("//") -> "https:$url"
    url.startsWith("http://", ignoreCase = true) &&
        (url.contains("sinaimg.", ignoreCase = true) || url.contains("weibocdn.com", ignoreCase = true)) ->
        "https://" + url.substring("http://".length)
    else -> url
}

private fun forEachHtmlAnchor(html: String, block: (unwrappedHref: String, plainLabel: String) -> Unit) {
    if (html.isBlank()) return
    for (match in HtmlAnchorRegex.findAll(html)) {
        val attrs = match.groups[1]?.value.orEmpty()
        val hrefMatch = AnchorHrefRegex.find(attrs) ?: continue
        val rawHref = unescapeHtmlAttrUrl(hrefMatch.groupValues[1].ifBlank { hrefMatch.groupValues[2] })
        if (rawHref.isBlank()) continue
        val unwrapped = unwrapWeiboLink(rawHref)
        val plainLabel = HtmlTagStripRegex.replace(match.groups[2]?.value.orEmpty(), "").trim()
        block(unwrapped, plainLabel)
    }
}

/**
 * 从微博或评论 HTML 文本的 `<a>` 链接中直接提取「查看图片 / 评论配图」直链与带 `pid` 的相册地址，
 * 返回 `(thumbUrl, largeUrl)` 列表（已按大图去重），供卡片免点入网页直接在正文下方内联展示。
 */
fun extractDirectImageUrlsFromHtml(html: String): List<Pair<String, String>> {
    val result = LinkedHashMap<String, String>()
    forEachHtmlAnchor(html) { url, _ ->
        when {
            isImageLink(url) -> {
                val thumb = normalizeProtocolUrl(url)
                val large = toLargeSinaImageUrl(thumb)
                result.putIfAbsent(large, thumb)
            }
            else -> {
                val pid = if (isWeiboPhotoPageLink(url)) extractWeiboPhotoPid(url) else null
                if (!pid.isNullOrBlank()) {
                    val thumb = "https://wx1.sinaimg.cn/orj360/$pid.jpg"
                    val large = "https://wx1.sinaimg.cn/large/$pid.jpg"
                    result.putIfAbsent(large, thumb)
                }
            }
        }
    }
    return result.entries.map { (large, thumb) -> thumb to large }
}

/**
 * 从微博或评论 HTML 文本中提取尚需异步解析真实图床地址的内联图片链接
 * （无 `pid` 的 `photo.weibo.com` 链接，或文本明确为「查看图片 / 评论配图」的短链）。
 */
fun extractResolvablePhotoLinksFromHtml(html: String): List<String> {
    val out = LinkedHashSet<String>()
    forEachHtmlAnchor(html) { url, label ->
        if (url.isBlank()) return@forEachHtmlAnchor
        val isPhotoPage = isWeiboPhotoPageLink(url) && extractWeiboPhotoPid(url) == null
        val isPhotoShortLink = isWeiboShortLink(url) && isInlinePhotoLinkLabel(label)
        if (isPhotoPage || isPhotoShortLink) {
            out.add(normalizeProtocolUrl(url))
        }
    }
    return out.toList()
}

/** 微博相册与「评论配图 / 查看图片」跳转页（`photo.weibo.com`），需解出内部图片直链进原生看图页。 */
fun isWeiboPhotoPageLink(raw: String): Boolean {
    val unwrapped = unwrapWeiboLink(raw)
    if (unwrapped.isEmpty()) return false
    val uri = runCatching { ParsedUri(if (unwrapped.startsWith("//")) "https:$unwrapped" else unwrapped) }.getOrNull()
        ?: return false
    val host = uri.host?.lowercase() ?: return false
    return host == "photo.weibo.com" || host.endsWith(".photo.weibo.com")
}

/** 从 `photo.weibo.com/.../pid/<pid>` 或 `?pid=<pid>` 提取微博图床 pid。 */
fun extractWeiboPhotoPid(raw: String): String? {
    val unwrapped = unwrapWeiboLink(raw)
    if (unwrapped.isEmpty()) return null
    val queryPid = param(queryOf(unwrapped), "pid")?.trim()
    if (!queryPid.isNullOrBlank() && queryPid.length >= 16 && queryPid.all(Char::isLetterOrDigit)) {
        return queryPid
    }
    return PhotoPidPathRegex.find(unwrapped)?.groupValues?.getOrNull(1)
}

/** 将缩略图尺寸路径升级为微博大图尺寸，保留已有的扩展名与查询参数。 */
fun toLargeSinaImageUrl(url: String): String {
    val normalized = when {
        url.startsWith("//") -> "https:$url"
        url.startsWith("http://", ignoreCase = true) && url.contains("sinaimg.", ignoreCase = true) ->
            "https://" + url.substring("http://".length)
        else -> url
    }
    return SinaImgSizeSegmentRegex.replaceFirst(normalized, "/large/")
}

/** 从 `photo.weibo.com` 页面 HTML 中提取评论配图/相册图床直链，缺省时兜底 JSON 中的 `pic_id`/`pid`。 */
fun extractSinaImageUrlsFromHtml(html: String): List<String> {
    if (html.isBlank()) return emptyList()
    val unescaped = html.replace("\\/", "/").replace("&amp;", "&")
    val out = LinkedHashSet<String>()
    for (match in SinaImgUrlRegex.findAll(unescaped)) {
        val candidate = match.value.trim()
        val lower = candidate.lowercase()
        if (lower.contains("/upload/") || lower.contains("timeline_card_small_") ||
            lower.contains("avatar") || lower.contains("favicon") || lower.contains("default_")
        ) {
            continue
        }
        val normalized = when {
            candidate.startsWith("//") -> "https:$candidate"
            candidate.startsWith("http://", ignoreCase = true) -> "https://" + candidate.substring("http://".length)
            else -> candidate
        }
        out.add(normalized)
    }
    if (out.isEmpty()) {
        for (match in HtmlPicPidJsonRegex.findAll(unescaped)) {
            val pid = match.groupValues[1].trim()
            if (pid.isNotBlank()) {
                out.add("https://wx1.sinaimg.cn/orj360/$pid.jpg")
            }
        }
    }
    return out.toList()
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

private val LivePathIdRegex = Regex(
    """/(?:(?:l/)?(?:wblive|weibolive)/(?:m|p)/)?show/([0-9]+:[0-9a-zA-Z_-]+|[0-9]{10,})""",
    RegexOption.IGNORE_CASE,
)
private val LiveNumericCompoundRegex = Regex("""\b((?:1022|1042):232[0-9a-zA-Z_-]{6,})\b""")

private fun normalizeWeiboLiveId(rawId: String?): String? {
    val trimmed = rawId?.let(::decodeUrlParam)?.trim().orEmpty()
    if (trimmed.isBlank()) return null
    if (trimmed.contains(':')) return trimmed
    return if (trimmed.all(Char::isDigit) && trimmed.startsWith("232") && trimmed.length >= 12) {
        "1022:$trimmed"
    } else if (trimmed.all(Char::isDigit) && trimmed.length >= 10) {
        trimmed
    } else {
        trimmed.takeIf { it.any(Char::isLetterOrDigit) }
    }
}

private fun hasLiveContextHints(unwrapped: String): Boolean {
    val lower = unwrapped.lowercase()
    if (lower.startsWith("sinaweibo://chatroom") || lower.startsWith("weibo://chatroom") ||
        lower.startsWith("sinaweibo://liveloom") || lower.startsWith("weibo://liveloom") ||
        lower.startsWith("sinaweibo://wblive") || lower.startsWith("weibo://wblive") ||
        lower.startsWith("sinaweibo://live") || lower.startsWith("weibo://live")
    ) {
        return true
    }
    if (lower.startsWith("intent://") &&
        (lower.contains("chatroom") || lower.contains("liveloom") || lower.contains("wblive") || lower.contains("live_id="))
    ) {
        return true
    }
    if (lower.contains("/l/wblive") || lower.contains("/weibolive") ||
        lower.contains("/tv/live") || lower.contains("livetype=wblive") ||
        lower.contains("live_status=")
    ) {
        return true
    }
    val uri = runCatching { ParsedUri(unwrapped) }.getOrNull() ?: return false
    val host = uri.host?.lowercase().orEmpty()
    return host == "live.weibo.com" ||
        host == "live.media.weibo.com" ||
        host == "wblive.media.weibo.com" ||
        host == "wblive-out.api.weibo.com" ||
        host == "zhibo.weibo.com" ||
        host == "live.video.weibocdn.com" ||
        host.endsWith(".live.weibo.com")
}

/** 从微博直播链接、私有 scheme 或房间参数中提取 `live_id`（例如 `1022:232132...`）。 */
fun extractWeiboLiveId(raw: String): String? {
    val unwrapped = unwrapWeiboLink(raw)
    if (unwrapped.isEmpty()) return null
    val query = queryOf(unwrapped.substringBefore("#Intent"))
    val liveContext = hasLiveContextHints(unwrapped)

    param(query, "live_id")?.let(::normalizeWeiboLiveId)?.let { return it }
    if (liveContext) {
        val cidParam = param(query, "container_id")
            ?: param(query, "containerid")
            ?: param(query, "id")
            ?: param(query, "room_id")
            ?: param(query, "object_id")
            ?: param(query, "oid")
            ?: param(query, "fid")
        cidParam?.let(::normalizeWeiboLiveId)?.let { return it }
    } else {
        for (key in listOf("container_id", "containerid", "object_id", "oid", "fid", "id")) {
            val candidate = param(query, key)?.let(::decodeUrlParam).orEmpty()
            if (candidate.startsWith("1022:232")) return candidate
        }
    }

    val uriPath = runCatching { ParsedUri(unwrapped).rawPath }.getOrNull()
        ?: unwrapped.substringBefore('?').substringBefore('#')
    if (liveContext || uriPath.contains("wblive", ignoreCase = true) || uriPath.contains("weibolive", ignoreCase = true)) {
        LivePathIdRegex.find(uriPath)?.groupValues?.getOrNull(1)
            ?.let(::normalizeWeiboLiveId)
            ?.let { return it }
    }

    LiveNumericCompoundRegex.find(unwrapped)?.groupValues?.getOrNull(1)?.let { return it }
    return null
}

/** 判断链接或 scheme 是否指向微博直播间或直播流。 */
fun isWeiboLiveLink(raw: String): Boolean {
    val unwrapped = unwrapWeiboLink(raw)
    if (unwrapped.isEmpty()) return false
    if (hasLiveContextHints(unwrapped)) return true
    return extractWeiboLiveId(unwrapped) != null
}

/**
 * 判断链接是否为视频直链、微博视频页或主流第三方视频官链（爱奇艺、B站、腾讯视频、优酷等）。
 * 命中后交给内置视频解析/播放页处理，避免在普通 WebView 里跳端失败或无法下载。
 */
fun isVideoLink(raw: String): Boolean {
    val trimmed = unwrapWeiboLink(raw)
    if (trimmed.isEmpty()) return false
    if (isWeiboLiveLink(trimmed)) return false
    val lower = trimmed.lowercase()
    if (lower.startsWith("sinaweibo://video") || lower.startsWith("intent://gotovideo")) return true
    val path = lower.substringBefore('?').substringBefore('#')
    if (path.endsWith(".mp4") || path.endsWith(".m4v") || path.endsWith(".mov") ||
        path.endsWith(".webm") || path.endsWith(".m3u8") || path.endsWith(".flv")
    ) {
        return true
    }
    val uri = runCatching { ParsedUri(trimmed) }.getOrNull() ?: return false
    val host = uri.host?.lowercase() ?: return false
    val uriPath = uri.rawPath.orEmpty().lowercase()
    if (host == "video.weibo.com" || host == "h5.video.weibo.com") return true
    if ((host == "weibo.com" || host == "www.weibo.com") && uriPath.startsWith("/tv/")) return true
    if (host == "m.weibo.cn" && (uriPath.startsWith("/s/video/") || uriPath.startsWith("/z/video/"))) return true
    return ExternalVideoHosts.any { domain -> host == domain || host.endsWith(".$domain") }
}

/**
 * Extracts the status id (numeric id or Base62 mblogid) from a link that points at a single 微博,
 * so "全文" and shared status links open the in-app detail page instead of a WebView.
 */
fun weiboStatusId(raw: String): String? {
    val trimmed = raw.trim()
    val lower = trimmed.lowercase()
    if (lower.startsWith("sinaweibo://detail") || lower.startsWith("sinaweibo://status") ||
        lower.startsWith("weibo://detail") || lower.startsWith("weibo://status")
    ) {
        val query = queryOf(trimmed)
        val id = param(query, "mblogid") ?: param(query, "id")
        return id?.takeIf { it.isNotEmpty() && it.all(Char::isLetterOrDigit) }
    }
    var path = trimmed.substringBefore('?').substringBefore('#')
    val scheme = path.indexOf("://")
    var host = ""
    if (scheme >= 0) {
        path = path.substring(scheme + 3)
        host = path.substringBefore('/').lowercase()
    }
    if (path.startsWith("//")) path = path.substring(2)
    val segments = path.split('/').filter { it.isNotBlank() }.toMutableList()
    if (segments.isNotEmpty() && (segments[0].contains('.') || segments[0].contains(':'))) {
        host = segments.removeAt(0).lowercase()
    }
    val head = segments.firstOrNull() ?: return null
    val second = segments.getOrNull(1) ?: return null
    if (head == "status" || head == "detail") {
        return second.takeIf { it.isNotEmpty() && it.all(Char::isLetterOrDigit) }
    }
    val isWeiboHost = host == "weibo.com" || host == "www.weibo.com" || host == "weibo.cn" || host == "m.weibo.cn"
    if (isWeiboHost && segments.size == 2 && head.all(Char::isDigit) && second.isNotEmpty() && second.all(Char::isLetterOrDigit)) {
        return second
    }
    return null
}

/** 是否指向微博首页（外部深链只拉起主界面，不必再套一层 WebView）。 */
fun isWeiboHomeLink(raw: String): Boolean {
    val lower = raw.trim().lowercase().substringBefore('?').substringBefore('#').trimEnd('/')
    return lower == "https://weibo.com" ||
        lower == "http://weibo.com" ||
        lower == "https://www.weibo.com" ||
        lower == "http://www.weibo.com" ||
        lower == "https://m.weibo.cn" ||
        lower == "http://m.weibo.cn" ||
        lower == "https://weibo.cn" ||
        lower == "http://weibo.cn" ||
        lower == "sinaweibo://home" ||
        lower == "weibo://home"
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
        urlDecode(value)
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
    val uri = runCatching { ParsedUri(raw.trim()) }.getOrNull() ?: return false
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

/**
 * 判断解析后的链接是否为外部网页（非微博域的 http/https 链接）。
 * 微博站内页面（如头条文章、投票、超话网页）依赖内置 WebView 的登录态与 Cookie，仍保留在应用内打开。
 */
fun isExternalWebLink(raw: String): Boolean {
    val uri = runCatching { ParsedUri(raw.trim()) }.getOrNull() ?: return false
    val scheme = uri.scheme?.lowercase() ?: return false
    if (scheme != "http" && scheme != "https") return false
    val host = uri.host?.lowercase()?.takeIf { it.isNotEmpty() } ?: return false
    return !CookieVault.isWeibo(host)
}

/** 调用系统浏览器或外部应用打开链接。 */
fun openExternalUrl(@Suppress("UNUSED_PARAMETER") context: PlatformContext, url: String) {
    platformOpenExternalUrl(url)
}


private class ParsedUri(raw: String) {
    val host: String? = parseUrlHost(raw).ifBlank { null }
    val rawPath: String? = parseUrlPath(raw).ifBlank { null }
    val path: String? = rawPath
    val scheme: String? = raw.substringBefore("://", "").takeIf { it.isNotBlank() && raw.contains("://") }
    val rawQuery: String? = raw.substringAfter('?', "").substringBefore('#').ifBlank { null }
}
