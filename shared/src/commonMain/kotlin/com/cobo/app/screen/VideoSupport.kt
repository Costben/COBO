package com.cobo.app.screen

import com.cobo.app.App
import com.cobo.app.data.Api
import com.cobo.app.data.Status
import com.cobo.app.json.JSONObject
import com.cobo.app.platform.PlatformContext
import com.cobo.app.platform.PlatformHttp
import com.cobo.app.platform.currentTimeMillis
import com.cobo.app.platform.ioDispatcher
import com.cobo.app.platform.parseUrlHost
import com.cobo.app.platform.resolveRelativeUrl
import com.cobo.app.platform.saveVideoBytesToGallery
import com.cobo.app.platform.urlDecode
import com.cobo.app.platform.urlEncode
import com.cobo.app.ui.extractWeiboLiveId
import com.cobo.app.ui.isWeiboLiveLink
import com.cobo.app.ui.resolveWeiboLink
import com.cobo.app.ui.unwrapWeiboLink
import kotlinx.coroutines.withContext

sealed interface ResolvedVideo {
    data class DirectStream(
        val url: String,
        val fallbackUrl: String = "",
        val referer: String = "",
        val title: String = "",
        val isLive: Boolean = false,
    ) : ResolvedVideo

    data class WebPage(
        val url: String,
        val title: String = "",
    ) : ResolvedVideo

    data class LiveUnavailable(
        val message: String,
        val title: String = "",
        val webUrl: String = "",
    ) : ResolvedVideo
}

private val WeiboFidPattern = Regex("""\b(\d{4}:[0-9a-fA-F]{6,})\b""")
private val BiliBvidPattern = Regex("""\b(BV[0-9A-Za-z]{10})\b""")
private val VideoUrlExtPattern = Regex("""\.(?:mp4|m3u8|flv|mov|webm)(?:\?|$)""", RegexOption.IGNORE_CASE)

internal fun defaultRefererFor(url: String): String {
    val host = parseUrlHost(url)
    return when {
        host.contains("bilivideo") || host.contains("bilibili") -> "https://www.bilibili.com/"
        host.contains("weibo") || host.contains("weibocdn") || host.contains("sina") -> "https://weibo.com/"
        else -> ""
    }
}

internal fun isDirectVideoStream(url: String): Boolean {
    val lower = url.trim().lowercase()
    if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false
    if (VideoUrlExtPattern.containsMatchIn(lower)) return true
    if (lower.contains("f.video.weibocdn.com") || lower.contains("live.video.weibocdn.com")) return true
    return false
}

internal fun isM3u8Stream(url: String): Boolean {
    val lower = url.trim().lowercase()
    return lower.substringBefore('?').substringBefore('#').endsWith(".m3u8") ||
        lower.contains("type=m3u8") ||
        lower.contains("format=m3u8")
}

internal fun isSniffableMediaUrl(url: String): Boolean = isDirectVideoStream(url) || isM3u8Stream(url)

internal fun extractWeiboVideoFid(raw: String): String? {
    val decoded = runCatching { urlDecode(raw) }.getOrDefault(raw)
    return WeiboFidPattern.find(decoded)?.groupValues?.getOrNull(1)
        ?: WeiboFidPattern.find(raw)?.groupValues?.getOrNull(1)
}

internal fun extractBilibiliBvid(raw: String): String? =
    BiliBvidPattern.find(raw)?.groupValues?.getOrNull(1)

internal fun extractHtmlVideoUrl(html: String, baseUrl: String): String? {
    val patterns = listOf(
        Regex("""["'](https?:\\?/\\?/[^"'\s<>]+\.mp4[^"'\s<>]*)["']""", RegexOption.IGNORE_CASE),
        Regex("""["'](https?:\\?/\\?/[^"'\s<>]+\.m3u8[^"'\s<>]*)["']""", RegexOption.IGNORE_CASE),
        Regex("""<video[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE),
        Regex("""<source[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE),
    )
    for (p in patterns) {
        val m = p.find(html)?.groupValues?.getOrNull(1) ?: continue
        val cleaned = m.replace("\\/", "/").replace("&amp;", "&").trim()
        val resolved = resolveRelativeUrl(baseUrl, cleaned)
        if (resolved.startsWith("http://", true) || resolved.startsWith("https://", true)) {
            return resolved
        }
    }
    return null
}

private fun normalizeStreamHttps(raw: String): String = when {
    raw.startsWith("//") -> "https:$raw"
    raw.startsWith("http://", ignoreCase = true) -> "https://" + raw.substring(7)
    else -> raw
}

internal fun parseWeiboLiveRoomJson(root: JSONObject, fallbackUrl: String = ""): ResolvedVideo? {
    val defaultWebUrl = when {
        fallbackUrl.startsWith("http://", true) || fallbackUrl.startsWith("https://", true) -> fallbackUrl
        fallbackUrl.isNotBlank() -> "https://weibo.com/l/wblive/m/show/$fallbackUrl"
        else -> ""
    }
    val code = root.optInt("code", 100000)
    if (code != 100000 && root.optJSONObject("data") == null) {
        return ResolvedVideo.LiveUnavailable(
            message = "该直播已结束或直播间已关闭",
            webUrl = defaultWebUrl,
        )
    }
    val data = root.optJSONObject("data") ?: root
    val item = data.optJSONObject("item") ?: data
    val screenName = item.optJSONObject("user_info")?.optString("screen_name").orEmpty()
        .ifBlank { data.optJSONObject("user_info")?.optString("screen_name").orEmpty() }
    val title = item.optString("title")
        .ifBlank { data.optString("title") }
        .ifBlank { if (screenName.isNotBlank()) "${screenName}的直播" else "" }
    val status = item.optInt("status", data.optInt("status", -1))
    val limitType = item.optJSONObject("watch_limit_info")?.optInt("type", 0)
        ?: data.optJSONObject("watch_limit_info")?.optInt("type", 0)
        ?: 0
    if (limitType > 0) {
        return ResolvedVideo.LiveUnavailable(
            message = "该直播需在微博官方客户端观看",
            title = title,
            webUrl = defaultWebUrl,
        )
    }
    if (status == 0) {
        return ResolvedVideo.LiveUnavailable(
            message = "直播未开始",
            title = title,
            webUrl = defaultWebUrl,
        )
    }
    val streamInfo = item.optJSONObject("stream_info") ?: data.optJSONObject("stream_info")
    val pull = streamInfo?.optJSONObject("pull")
    val candidates = listOfNotNull(
        pull?.optString("live_origin_hls_url")?.takeIf { it.isNotBlank() },
        pull?.optString("live_hd_hls_url")?.takeIf { it.isNotBlank() },
        pull?.optString("live_ld_hls_url")?.takeIf { it.isNotBlank() },
        pull?.optString("live_origin_flv_url")?.takeIf { it.isNotBlank() },
        item.optString("replay_origin_url").takeIf { it.isNotBlank() },
        data.optString("replay_origin_url").takeIf { it.isNotBlank() },
        item.optString("replay_url").takeIf { it.isNotBlank() },
        item.optString("replay_ld_url").takeIf { it.isNotBlank() },
        item.optString("hls").takeIf { it.isNotBlank() },
    )
    val primary = candidates.firstOrNull()
    if (!primary.isNullOrBlank()) {
        val normalized = normalizeStreamHttps(primary)
        val fallback = candidates.getOrNull(1)?.let(::normalizeStreamHttps).orEmpty()
        val isReplay = status == 2 || status == 3
        return ResolvedVideo.DirectStream(
            url = normalized,
            fallbackUrl = fallback,
            referer = "https://weibo.com/",
            title = title,
            isLive = !isReplay && (status == 1 || normalized.contains("live")),
        )
    }
    if (status == 2 || status == 3) {
        return ResolvedVideo.LiveUnavailable(
            message = "直播已结束，暂无回放",
            title = title,
            webUrl = defaultWebUrl,
        )
    }
    return null
}

internal fun extractSignedUrlExpireEpochSec(url: String): Long? {
    if (url.startsWith("file:", ignoreCase = true)) return null
    val patterns = listOf(
        Regex("""[?&]Expires=(\d{9,13})\b""", RegexOption.IGNORE_CASE),
        Regex("""[?&]exp=(\d{9,13})\b""", RegexOption.IGNORE_CASE),
        Regex("""[?&]deadline=(\d{9,13})\b""", RegexOption.IGNORE_CASE),
    )
    for (p in patterns) {
        val v = p.find(url)?.groupValues?.getOrNull(1)?.toLongOrNull()
        if (v != null && v > 0L) {
            return if (v > 9_999_999_999L) v / 1000L else v
        }
    }
    return null
}

internal fun isSignedVideoUrlExpired(
    url: String,
    nowEpochSec: Long = currentTimeMillis() / 1000L,
): Boolean {
    val exp = extractSignedUrlExpireEpochSec(url) ?: return false
    return exp <= nowEpochSec + 30L
}

internal fun canRefreshVideoSource(fid: String, statusId: String, isLive: Boolean): Boolean =
    fid.isNotBlank() || statusId.isNotBlank() || isLive

internal fun shouldRefreshDirectStreamBeforePlay(
    url: String,
    fid: String = "",
    statusId: String = "",
    isLive: Boolean = false,
    nowEpochSec: Long = currentTimeMillis() / 1000L,
): Boolean = isSignedVideoUrlExpired(url, nowEpochSec) && canRefreshVideoSource(fid, statusId, isLive)

internal fun shouldAttemptStreamRefreshOnError(
    errorCount: Int,
    fid: String,
    statusId: String,
    isLive: Boolean,
): Boolean = errorCount <= 1 && canRefreshVideoSource(fid, statusId, isLive)

internal fun shouldKeepVideoScreenOn(isPlaying: Boolean, isBuffering: Boolean): Boolean =
    isPlaying || isBuffering

internal fun videoStreamPathKey(url: String): String =
    com.cobo.app.platform.parseUrlPath(url).ifBlank { url.substringBefore('?').substringBefore('#') }

internal fun pickRefreshedVideoFromStatus(
    status: Status,
    oldUrl: String = "",
    fid: String = "",
): ResolvedVideo.DirectStream? {
    val all = buildList {
        status.video?.let {
            if (it.url.isNotBlank()) add(Triple(it.url, it.fid, ResolvedVideo.DirectStream(it.url, referer = defaultRefererFor(it.url), title = it.title, isLive = it.isLive)))
        }
        status.pics.forEach { pic ->
            if (pic.videoUrl.isNotBlank()) {
                add(Triple(pic.videoUrl, pic.videoFid, ResolvedVideo.DirectStream(pic.videoUrl, referer = defaultRefererFor(pic.videoUrl), title = pic.videoTitle, isLive = false)))
            }
        }
        status.retweeted?.video?.let {
            if (it.url.isNotBlank()) add(Triple(it.url, it.fid, ResolvedVideo.DirectStream(it.url, referer = defaultRefererFor(it.url), title = it.title, isLive = it.isLive)))
        }
        status.retweeted?.pics?.forEach { pic ->
            if (pic.videoUrl.isNotBlank()) {
                add(Triple(pic.videoUrl, pic.videoFid, ResolvedVideo.DirectStream(pic.videoUrl, referer = defaultRefererFor(pic.videoUrl), title = pic.videoTitle, isLive = false)))
            }
        }
    }.filter { isDirectVideoStream(it.first) }
    if (all.isEmpty()) return null
    if (fid.isNotBlank()) {
        all.firstOrNull { it.second == fid }?.let { return it.third }
    }
    if (oldUrl.isNotBlank()) {
        val targetPath = videoStreamPathKey(oldUrl)
        all.firstOrNull { videoStreamPathKey(it.first) == targetPath }?.let { return it.third }
        return if (all.size == 1) all.first().third else null
    }
    return all.first().third
}

private suspend fun resolveWeiboFid(fid: String): ResolvedVideo? {
    val cookie = App.repo.desktopCookieOrNull() ?: App.repo.cookie()
    val payload = JSONObject()
        .put("Component_Play_Playinfo", JSONObject().put("oid", fid))
        .toString()
    val url = "https://weibo.com/tv/api/component?page=${urlEncode("/tv/show/$fid")}"
    val headers = buildMap {
        put("User-Agent", Api.DESKTOP_UA)
        put("Referer", "https://weibo.com/tv/show/$fid")
        put("Origin", "https://weibo.com")
        put("X-Requested-With", "XMLHttpRequest")
        if (!cookie.isNullOrBlank()) put("Cookie", cookie)
    }
    val resp = runCatching { PlatformHttp.postForm(url, mapOf("data" to payload), headers) }.getOrNull() ?: return null
    val root = runCatching { JSONObject(resp.bodyText) }.getOrNull() ?: return null
    val info = root.optJSONObject("data")?.optJSONObject("Component_Play_Playinfo") ?: return null
    val title = info.optString("title")
    val urls = info.optJSONObject("urls")
    val preferred = ArrayList<String>()
    if (urls != null) {
        for (k in urls.keys()) {
            val v = urls.optString(k).trim()
            if (v.isNotBlank()) preferred.add(if (v.startsWith("//")) "https:$v" else v)
        }
    }
    val single = info.optString("stream_url_hd").ifBlank { info.optString("stream_url") }.trim()
    if (single.isNotBlank()) preferred.add(if (single.startsWith("//")) "https:$single" else single)
    val best = preferred.firstOrNull { it.startsWith("http", true) } ?: return null
    val fallback = preferred.drop(1).firstOrNull { it.startsWith("http", true) }.orEmpty()
    return ResolvedVideo.DirectStream(
        url = best,
        fallbackUrl = fallback,
        referer = "https://weibo.com/",
        title = title,
        isLive = false,
    )
}

private suspend fun resolveWeiboLive(rawUrl: String): ResolvedVideo? {
    val liveId = extractWeiboLiveId(rawUrl) ?: return null
    val cookie = App.repo.desktopCookieOrNull() ?: App.repo.cookie()
    val apiUrl = "https://weibo.com/l/pc/live/detail?live_id=${urlEncode(liveId)}"
    val headers = buildMap {
        put("User-Agent", Api.DESKTOP_UA)
        put("Referer", "https://weibo.com/")
        put("X-Requested-With", "XMLHttpRequest")
        if (!cookie.isNullOrBlank()) put("Cookie", cookie)
    }
    val resp = runCatching { PlatformHttp.get(apiUrl, headers) }.getOrNull() ?: return null
    val root = runCatching { JSONObject(resp.bodyText) }.getOrNull() ?: return null
    return parseWeiboLiveRoomJson(root, rawUrl)
}

private suspend fun resolveBilibiliVideo(bvid: String): ResolvedVideo? {
    val headers = mapOf(
        "User-Agent" to Api.DESKTOP_UA,
        "Referer" to "https://www.bilibili.com/video/$bvid",
    )
    val viewResp = runCatching {
        PlatformHttp.get("https://api.bilibili.com/x/web-interface/view?bvid=$bvid", headers)
    }.getOrNull() ?: return null
    val viewRoot = runCatching { JSONObject(viewResp.bodyText) }.getOrNull() ?: return null
    val data = viewRoot.optJSONObject("data") ?: return null
    val cid = data.optLong("cid")
    val title = data.optString("title")
    if (cid <= 0L) return null
    val playResp = runCatching {
        PlatformHttp.get(
            "https://api.bilibili.com/x/player/playurl?bvid=$bvid&cid=$cid&qn=64&fnval=1&platform=html5&high_quality=1",
            headers,
        )
    }.getOrNull() ?: return null
    val playRoot = runCatching { JSONObject(playResp.bodyText) }.getOrNull() ?: return null
    val durl = playRoot.optJSONObject("data")?.optJSONArray("durl") ?: return null
    val streamUrl = durl.optJSONObject(0)?.optString("url").orEmpty()
    if (streamUrl.isBlank()) return null
    return ResolvedVideo.DirectStream(
        url = streamUrl,
        referer = "https://www.bilibili.com/",
        title = title,
        isLive = false,
    )
}

suspend fun refreshPlayableVideo(
    url: String,
    fid: String = "",
    statusId: String = "",
    isLive: Boolean = false,
): ResolvedVideo? = withContext(ioDispatcher) {
    if (isLive || isWeiboLiveLink(url)) {
        resolveWeiboLive(url)?.let { return@withContext it }
    }
    val effectiveFid = fid.ifBlank { extractWeiboVideoFid(url).orEmpty() }
    if (effectiveFid.isNotBlank()) {
        resolveWeiboFid(effectiveFid)?.let { return@withContext it }
    }
    if (statusId.isNotBlank()) {
        val status = runCatching { App.repo.detail(statusId) }.getOrNull()
        if (status != null) {
            pickRefreshedVideoFromStatus(status)?.let { return@withContext it }
        }
    }
    null
}

suspend fun resolvePlayableVideo(
    rawUrl: String,
    isLive: Boolean = false,
    fid: String = "",
    statusId: String = "",
): ResolvedVideo = withContext(ioDispatcher) {
    val unwrapped = unwrapWeiboLink(rawUrl)
    val target = resolveWeiboLink(unwrapped) ?: unwrapped
    if (isLive || isWeiboLiveLink(target)) {
        resolveWeiboLive(target)?.let { return@withContext it }
    }
    if (isDirectVideoStream(target)) {
        if (shouldRefreshDirectStreamBeforePlay(target, fid, statusId, isLive)) {
            refreshPlayableVideo(target, fid, statusId, isLive)?.let { return@withContext it }
        }
        return@withContext ResolvedVideo.DirectStream(
            url = target,
            referer = defaultRefererFor(target),
            isLive = isLive,
        )
    }
    val effectiveFid = fid.ifBlank { extractWeiboVideoFid(target).orEmpty() }
    if (effectiveFid.isNotBlank()) {
        resolveWeiboFid(effectiveFid)?.let { return@withContext it }
    }
    extractBilibiliBvid(target)?.let { bvid ->
        resolveBilibiliVideo(bvid)?.let { return@withContext it }
    }
    if (statusId.isNotBlank()) {
        val status = runCatching { App.repo.detail(statusId) }.getOrNull()
        if (status != null) {
            pickRefreshedVideoFromStatus(status)?.let { return@withContext it }
        }
    }
    val finalUrl = runCatching { Api.resolveRedirect(target, App.repo.cookie()) }.getOrDefault(target)
    if (isDirectVideoStream(finalUrl)) {
        return@withContext ResolvedVideo.DirectStream(
            url = finalUrl,
            referer = defaultRefererFor(finalUrl),
            isLive = isLive,
        )
    }
    extractWeiboVideoFid(finalUrl)?.let { resolvedFid ->
        resolveWeiboFid(resolvedFid)?.let { return@withContext it }
    }
    extractBilibiliBvid(finalUrl)?.let { bvid ->
        resolveBilibiliVideo(bvid)?.let { return@withContext it }
    }
    val html = runCatching {
        val cookie = App.repo.cookie()
        Api.getHtml(finalUrl, cookie, desktop = parseUrlHost(finalUrl).contains("weibo.com"))
    }.getOrNull()
    if (!html.isNullOrBlank()) {
        extractHtmlVideoUrl(html, finalUrl)?.let { extracted ->
            return@withContext ResolvedVideo.DirectStream(
                url = extracted,
                referer = defaultRefererFor(extracted).ifBlank { finalUrl },
                isLive = isLive,
            )
        }
    }
    ResolvedVideo.WebPage(url = finalUrl)
}

internal data class M3u8Playlist(
    val variantUrl: String? = null,
    val segments: List<String> = emptyList(),
    val keyMethod: String? = null,
    val keyUri: String? = null,
    val keyIvHex: String? = null,
)

internal fun parseM3u8Playlist(content: String, baseUrl: String): M3u8Playlist {
    fun resolve(ref: String): String =
        if (ref.startsWith("http://", true) || ref.startsWith("https://", true)) ref
        else resolveRelativeUrl(baseUrl, ref)

    val lines = content.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
    var bestVariant: String? = null
    var bestBandwidth = -1L
    val segments = ArrayList<String>()
    var keyMethod: String? = null
    var keyUri: String? = null
    var keyIvHex: String? = null

    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        if (line.startsWith("#EXT-X-STREAM-INF:")) {
            val bw = Regex("""BANDWIDTH=(\d+)""").find(line)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
            val next = lines.getOrNull(i + 1)
            if (next != null && !next.startsWith("#") && bw >= bestBandwidth) {
                bestBandwidth = bw
                bestVariant = resolve(next)
            }
        } else if (line.startsWith("#EXT-X-KEY:")) {
            keyMethod = Regex("""METHOD=([^,]+)""").find(line)?.groupValues?.getOrNull(1)?.trim()
            val rawUri = Regex("""URI="([^"]+)"""").find(line)?.groupValues?.getOrNull(1)
            if (!rawUri.isNullOrBlank()) keyUri = resolve(rawUri)
            keyIvHex = Regex("""IV=0x([0-9a-fA-F]+)""").find(line)?.groupValues?.getOrNull(1)
        } else if (!line.startsWith("#")) {
            segments.add(resolve(line))
        }
        i++
    }
    return M3u8Playlist(bestVariant, segments, keyMethod, keyUri, keyIvHex)
}

suspend fun downloadVideoToGallery(
    @Suppress("UNUSED_PARAMETER") context: PlatformContext,
    videoUrl: String,
    referer: String = "",
    onProgress: (Float?) -> Unit = {},
): Result<Unit> = withContext(ioDispatcher) {
    runCatching {
        val effectiveReferer = referer.ifBlank { defaultRefererFor(videoUrl) }
        val headers = buildMap {
            put("User-Agent", Api.WEB_UA)
            if (effectiveReferer.isNotBlank()) put("Referer", effectiveReferer)
        }
        if (isM3u8Stream(videoUrl)) {
            onProgress(0.05f)
            var targetUrl = videoUrl
            val firstResp = PlatformHttp.get(targetUrl, headers)
            if (!firstResp.isSuccessful) error("获取播放列表失败（HTTP ${firstResp.code}）")
            var parsed = parseM3u8Playlist(firstResp.bodyText, targetUrl)
            val variant = parsed.variantUrl
            if (variant != null && parsed.segments.isEmpty()) {
                targetUrl = variant
                val varResp = PlatformHttp.get(targetUrl, headers)
                if (!varResp.isSuccessful) error("获取清晰度列表失败（HTTP ${varResp.code}）")
                parsed = parseM3u8Playlist(varResp.bodyText, targetUrl)
            }
            if (parsed.segments.isEmpty()) error("未解析到视频分片")
            val method = parsed.keyMethod?.uppercase()
            if (method != null && method != "NONE") {
                error("暂不支持直接保存加密 HLS 流视频")
            }
            val chunks = ArrayList<ByteArray>(parsed.segments.size)
            var totalBytes = 0
            parsed.segments.forEachIndexed { idx, segUrl ->
                val segBytes = PlatformHttp.downloadBytes(segUrl, headers) ?: error("视频分片下载失败")
                chunks.add(segBytes)
                totalBytes += segBytes.size
                onProgress(0.05f + 0.85f * ((idx + 1).toFloat() / parsed.segments.size.toFloat()))
            }
            val merged = ByteArray(totalBytes)
            var offset = 0
            for (c in chunks) {
                c.copyInto(merged, destinationOffset = offset)
                offset += c.size
            }
            val name = "cobo_video_${currentTimeMillis()}.ts"
            saveVideoBytesToGallery(merged, name).getOrThrow()
            onProgress(1f)
            return@runCatching
        }
        onProgress(0.15f)
        val bytes = PlatformHttp.downloadBytes(videoUrl, headers) ?: error("视频下载失败")
        onProgress(0.85f)
        val name = "cobo_video_${currentTimeMillis()}.mp4"
        saveVideoBytesToGallery(bytes, name).getOrThrow()
        onProgress(1f)
    }
}
