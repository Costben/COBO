package com.cobo.app.screen

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.media3.common.PlaybackException
import com.cobo.app.App
import com.cobo.app.data.Api
import com.cobo.app.data.Status
import com.cobo.app.data.VideoInfo
import com.cobo.app.ui.extractWeiboLiveId
import com.cobo.app.ui.isWeiboLiveLink
import com.cobo.app.ui.isWeiboShortLink
import com.cobo.app.ui.resolveWeiboLink
import com.cobo.app.ui.unwrapWeiboLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

internal sealed interface ResolvedVideo {
    data class DirectStream(
        val url: String,
        val referer: String? = null,
        val title: String? = null,
        val isLive: Boolean = false,
        val fallbackUrl: String? = null,
    ) : ResolvedVideo

    data class WebPage(
        val url: String,
        val title: String? = null,
        val isLive: Boolean = false,
    ) : ResolvedVideo

    data class Unavailable(
        val message: String,
        val webUrl: String? = null,
        val title: String? = null,
    ) : ResolvedVideo
}

private val VideoHttpClient by lazy {
    OkHttpClient.Builder()
        .followRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
}

internal fun defaultRefererFor(url: String): String? {
    val host = runCatching { URI(url).host?.lowercase().orEmpty() }.getOrDefault("")
    return when {
        host.contains("weibocdn.com") || host.contains("sinaimg.cn") ||
            host.contains("weibo.com") || host.contains("weibo.cn") || host.contains("miaopai.com") ->
            "https://m.weibo.cn/"
        else -> null
    }
}

internal fun isDirectVideoStream(url: String): Boolean {
    val trimmed = url.trim()
    if (trimmed.startsWith("file:", ignoreCase = true)) return true
    val path = trimmed.substringBefore('?').substringBefore('#').lowercase()
    if (!path.startsWith("http://") && !path.startsWith("https://")) return false
    if (path.endsWith(".mp4") || path.endsWith(".m4v") || path.endsWith(".mov") ||
        path.endsWith(".webm") || path.endsWith(".flv")
    ) {
        return true
    }
    val host = runCatching { URI(trimmed).host?.lowercase().orEmpty() }.getOrDefault("")
    return (host.contains("weibocdn.com") && !isM3u8Stream(trimmed)) ||
        (host.contains("miaopai.com") && path.contains(".mp4"))
}

internal fun isM3u8Stream(url: String): Boolean {
    val trimmed = url.trim()
    val path = trimmed.substringBefore('?').substringBefore('#').lowercase()
    if (path.endsWith(".m3u8")) return true
    val query = trimmed.substringAfter('?', "").substringBefore('#').lowercase()
    return query.split('&').any { it == "type=m3u8" || it == "format=m3u8" }
}

internal fun isSniffableMediaUrl(url: String): Boolean {
    val trimmed = url.trim()
    if (!trimmed.startsWith("http://", true) && !trimmed.startsWith("https://", true)) return false
    val path = trimmed.substringBefore('?').substringBefore('#').lowercase()
    if (path.endsWith(".ts") || path.endsWith(".m4s") || path.endsWith(".jpg") ||
        path.endsWith(".png") || path.endsWith(".webp") || path.endsWith(".js") || path.endsWith(".css")
    ) {
        return false
    }
    return isM3u8Stream(trimmed) || path.endsWith(".mp4") || path.endsWith(".m4v") ||
        path.endsWith(".mov") || path.endsWith(".flv") || trimmed.contains(".mp4?", ignoreCase = true) ||
        trimmed.contains(".m3u8?", ignoreCase = true) || trimmed.contains(".flv?", ignoreCase = true)
}

internal fun extractWeiboVideoFid(rawUrl: String): String? {
    val trimmed = rawUrl.trim()
    val fidQuery = Regex("""[?&](?:fid|object_id)=([0-9]+:[0-9a-zA-Z_-]+)""").find(trimmed)
    if (fidQuery != null) return fidQuery.groupValues[1]
    val tvShow = Regex("""(?:/tv/show/|/show/)([0-9]+:[0-9a-zA-Z_-]+)""").find(trimmed)
    if (tvShow != null) return tvShow.groupValues[1]
    return null
}

internal fun extractBilibiliBvid(rawUrl: String): String? {
    val match = Regex("""\b(BV[0-9A-Za-z]{10})\b""").find(rawUrl)
    return match?.groupValues?.getOrNull(1)
}

internal fun extractHtmlVideoUrl(html: String, baseUrl: String): String? {
    if (html.isBlank()) return null
    val patterns = listOf(
        Regex("""<meta[^>]+property=["']og:video:secure_url["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE),
        Regex("""<meta[^>]+content=["']([^"']+)["'][^>]+property=["']og:video:secure_url["']""", RegexOption.IGNORE_CASE),
        Regex("""<meta[^>]+property=["']og:video:url["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE),
        Regex("""<meta[^>]+content=["']([^"']+)["'][^>]+property=["']og:video:url["']""", RegexOption.IGNORE_CASE),
        Regex("""<meta[^>]+property=["']og:video["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE),
        Regex("""<meta[^>]+content=["']([^"']+)["'][^>]+property=["']og:video["']""", RegexOption.IGNORE_CASE),
        Regex("""<video[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE),
        Regex("""<source[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE),
        Regex(""""contentUrl"\s*:\s*"([^"]+)""""),
        Regex(""""(?:mp4_720p_mp4|stream_url_hd|stream_url|mp4_hd_mp4|mp4_hd_url)"\s*:\s*"(https?:\\?/\\?/[^"]+)""""),
    )
    for (regex in patterns) {
        val raw = regex.find(html)?.groupValues?.getOrNull(1)
            ?.replace("\\/", "/")
            ?.replace("&amp;", "&")
            ?.trim()
            ?: continue
        if (raw.startsWith("blob:", true) || raw.isEmpty()) continue
        val resolved = when {
            raw.startsWith("http://", true) || raw.startsWith("https://", true) -> raw
            raw.startsWith("//") -> "https:$raw"
            else -> baseUrl.toHttpUrlOrNull()?.resolve(raw)?.toString()
        } ?: continue
        if (isDirectVideoStream(resolved) || isM3u8Stream(resolved)) return resolved
    }
    return null
}

private fun normalizeWeiboStreamUrl(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return ""
    val absolute = if (trimmed.startsWith("//")) "https:$trimmed" else trimmed
    return if (absolute.startsWith("http://", ignoreCase = true) &&
        (absolute.contains("weibocdn.com", ignoreCase = true) ||
            absolute.contains("live.weibo.com", ignoreCase = true) ||
            absolute.contains("alicdn.com", ignoreCase = true))
    ) {
        "https://" + absolute.substring("http://".length)
    } else {
        absolute
    }
}

private fun isLiveM3u8Playlist(url: String, referer: String?): Boolean = runCatching {
    val content = fetchText(url, referer)
    if (content.contains("#EXT-X-STREAM-INF:")) return@runCatching false
    !content.contains("#EXT-X-ENDLIST")
}.getOrDefault(false)

internal fun parseWeiboLiveRoomJson(
    root: JSONObject,
    liveId: String,
): ResolvedVideo? {
    val webUrl = "https://weibo.com/l/wblive/m/show/$liveId"
    val code = root.optInt("code", -1)
    val errorCode = root.optInt("error_code", 0)
    if (errorCode == 27401 || (code != -1 && code != 100000 && root.optString("msg").contains("not exists", ignoreCase = true))) {
        return ResolvedVideo.Unavailable(
            message = "该直播已结束或直播间已关闭",
            webUrl = webUrl,
        )
    }
    if (code != -1 && code != 100000) return null
    val data = root.optJSONObject("data") ?: return null
    val item = data.optJSONObject("item") ?: data
    val userInfo = data.optJSONObject("user_info")
        ?: data.optJSONObject("anchor_info")
        ?: item.optJSONObject("user_info")
    val authorName = userInfo?.optString("screen_name").orEmpty()
        .ifBlank { userInfo?.optString("name").orEmpty() }
    val title = item.optString("title")
        .ifBlank { item.optString("desc") }
        .ifBlank { data.optString("title") }
        .ifBlank { data.optString("desc") }
        .ifBlank { authorName.takeIf { it.isNotBlank() }?.let { "${it}的直播" }.orEmpty() }
        .takeIf { it.isNotBlank() }

    val watchLimit = item.optJSONObject("watch_limit_info")?.optInt("type", 0)
        ?: data.optJSONObject("watch_limit_info")?.optInt("type", 0)
        ?: 0
    if (watchLimit != 0) {
        return ResolvedVideo.Unavailable(
            message = "该直播需在微博官方客户端观看",
            webUrl = webUrl,
            title = title,
        )
    }

    val status = item.optInt("status", -1).takeIf { it >= 0 }
        ?: item.optInt("live_status", -1).takeIf { it >= 0 }
        ?: data.optInt("status", -1)
    val streamInfo = item.optJSONObject("stream_info")
    val pull = streamInfo?.optJSONObject("pull")

    if (status == 1) {
        val hls = listOf(
            item.optString("live_origin_hls_url"),
            pull?.optString("live_origin_hls_url").orEmpty(),
            item.optString("live_hd"),
            item.optString("live_ld"),
            pull?.optString("live_hls").orEmpty(),
        ).map(::normalizeWeiboStreamUrl).firstOrNull { it.isNotBlank() }
        val flv = listOf(
            item.optString("live_origin_flv_url"),
            pull?.optString("live_origin_flv_url").orEmpty(),
            pull?.optString("live_flv").orEmpty(),
        ).map(::normalizeWeiboStreamUrl).firstOrNull { it.isNotBlank() }
        val primary = hls ?: flv
        val fallback = if (hls != null && flv != null && flv != hls) flv else null
        if (!primary.isNullOrBlank()) {
            return ResolvedVideo.DirectStream(
                url = primary,
                referer = "https://weibo.com/",
                title = title,
                isLive = true,
                fallbackUrl = fallback,
            )
        }
    }

    if (status == 3) {
        val replayObj = streamInfo?.optJSONObject("replay_url")
        val replay = listOf(
            item.optString("replay_origin_url"),
            replayObj?.optString("origin").orEmpty(),
            item.optString("replay_hd"),
            item.optString("replay_ld"),
            replayObj?.optString("hd").orEmpty(),
            replayObj?.optString("ld").orEmpty(),
        ).map(::normalizeWeiboStreamUrl).firstOrNull { it.isNotBlank() }
        if (!replay.isNullOrBlank()) {
            return ResolvedVideo.DirectStream(
                url = replay,
                referer = "https://weibo.com/",
                title = title,
                isLive = false,
            )
        }
        return ResolvedVideo.Unavailable(
            message = "直播已结束，暂无回放",
            webUrl = webUrl,
            title = title,
        )
    }

    if (status == 0) {
        return ResolvedVideo.Unavailable(
            message = "直播未开始",
            webUrl = webUrl,
            title = title,
        )
    }
    if (status >= 2) {
        return ResolvedVideo.Unavailable(
            message = "直播已结束",
            webUrl = webUrl,
            title = title,
        )
    }
    return null
}

private fun resolveWeiboLiveRoom(
    liveId: String,
    cacheDir: File?,
): ResolvedVideo? {
    val cookie = runCatching { App.repo.cookie() }.getOrNull()
    val encodedId = java.net.URLEncoder.encode(liveId, "UTF-8")

    fun finishLiveResult(result: ResolvedVideo): ResolvedVideo = when (result) {
        is ResolvedVideo.DirectStream -> if (result.isLive) {
            result
        } else {
            normalizeResolvedStream(result, cacheDir)
        }
        else -> result
    }

    val endpoints = listOf(
        Triple(
            "https://weibo.com/l/!/2/wblive/room/show_pc_live.json?live_id=$encodedId",
            Api.DESKTOP_UA,
            "https://weibo.com/l/wblive/p/show/$encodedId",
        ),
        Triple(
            "https://weibo.com/l/pc/anchor/live?live_id=$encodedId",
            Api.WEB_UA,
            "https://weibo.com/l/wblive/m/show/$encodedId",
        ),
    )

    var unavailableFallback: ResolvedVideo.Unavailable? = null
    for ((endpoint, ua, referer) in endpoints) {
        runCatching {
            val request = Request.Builder()
                .url(endpoint)
                .header("User-Agent", ua)
                .header("Referer", referer)
                .header("X-Requested-With", "XMLHttpRequest")
                .apply { if (!cookie.isNullOrBlank()) header("Cookie", cookie) }
                .get()
                .build()
            VideoHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (body.startsWith("{")) {
                    val parsed = parseWeiboLiveRoomJson(JSONObject(body), liveId)
                    when (parsed) {
                        is ResolvedVideo.Unavailable -> if (unavailableFallback == null) unavailableFallback = parsed
                        null -> Unit
                        else -> return finishLiveResult(parsed)
                    }
                }
            }
        }
    }

    // 兜底调 room/play（部分帖子仅给出 live_id 或 stream_url 指到这里，返回 302 或 media_info JSON）。
    runCatching {
        val noRedirectClient = VideoHttpClient.newBuilder().followRedirects(false).build()
        val request = Request.Builder()
            .url("https://wblive-out.api.weibo.com/2/wblive/room/play?id=$encodedId")
            .header("User-Agent", Api.WEB_UA)
            .header("Referer", "https://weibo.com/")
            .apply { if (!cookie.isNullOrBlank()) header("Cookie", cookie) }
            .get()
            .build()
        noRedirectClient.newCall(request).execute().use { response ->
            val loc = response.header("Location")?.let(::normalizeWeiboStreamUrl).orEmpty()
            if (loc.isNotBlank() && (isDirectVideoStream(loc) || isM3u8Stream(loc))) {
                return ResolvedVideo.DirectStream(
                    url = loc,
                    referer = "https://weibo.com/",
                    isLive = true,
                )
            }
            if (response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                if (body.startsWith("{")) {
                    val root = JSONObject(body)
                    val media = root.optJSONObject("media_info") ?: root
                    val liveUrl = listOf(
                        media.optString("live_hd"),
                        media.optString("live_ld"),
                        media.optString("replay_hd"),
                        media.optString("replay_ld"),
                        media.optString("stream_url"),
                    ).map(::normalizeWeiboStreamUrl).firstOrNull {
                        it.isNotBlank() && (isDirectVideoStream(it) || isM3u8Stream(it))
                    }
                    if (!liveUrl.isNullOrBlank()) {
                        val isReplay = media.optInt("live_status", -1) == 3 || media.optInt("real_status", -1) == 3
                        val raw = ResolvedVideo.DirectStream(
                            url = liveUrl,
                            referer = "https://weibo.com/",
                            isLive = !isReplay,
                        )
                        return finishLiveResult(raw)
                    }
                }
            }
        }
    }

    return unavailableFallback
}

private val SignedExpireQueryRegex = Regex(
    """[?&](?:expires|deadline)=(\d{10,13})(?:&|#|$)""",
    RegexOption.IGNORE_CASE,
)

/**
 * 提取微博/CDN 签名直链里的过期时间戳（Unix 秒）。
 * 支持 `Expires=` / `expires=` / `deadline=`（10 位秒或 13 位毫秒）；无签名过期参数时返回 null。
 */
internal fun extractSignedUrlExpireEpochSec(url: String): Long? {
    val trimmed = url.trim()
    if (trimmed.isBlank() || trimmed.startsWith("file:", ignoreCase = true)) return null
    fun parseFrom(candidate: String): Long? {
        val raw = SignedExpireQueryRegex.find(candidate)?.groupValues?.getOrNull(1)?.toLongOrNull()
            ?: return null
        val epochSec = if (raw >= 1_000_000_000_000L) raw / 1000L else raw
        return epochSec.takeIf { it >= 1_000_000_000L }
    }
    parseFrom(trimmed)?.let { return it }
    if (trimmed.contains("%")) {
        val decoded = runCatching { URLDecoder.decode(trimmed, "UTF-8") }.getOrNull()
        if (!decoded.isNullOrBlank() && decoded != trimmed) {
            return parseFrom(decoded)
        }
    }
    return null
}

/**
 * 判断带防盗链签名的视频直链是否已过期（或将在 [skewSec] 秒内过期）。
 * 不含过期时间戳参数的普通直链返回 false，交由播放器实际请求并在出错时兜底刷新。
 */
internal fun isSignedVideoUrlExpired(
    url: String,
    nowEpochSec: Long = System.currentTimeMillis() / 1000L,
    skewSec: Long = 60L,
): Boolean {
    val exp = extractSignedUrlExpireEpochSec(url) ?: return false
    return exp <= nowEpochSec + skewSec
}

internal fun canRefreshVideoSource(
    url: String,
    fid: String = "",
    statusId: String = "",
    isLiveHint: Boolean = false,
): Boolean = fid.isNotBlank() ||
    statusId.isNotBlank() ||
    extractWeiboVideoFid(url) != null ||
    extractWeiboLiveId(url) != null ||
    isLiveHint

internal fun shouldRefreshDirectStreamBeforePlay(
    url: String,
    fid: String = "",
    statusId: String = "",
    isLiveHint: Boolean = false,
    nowEpochSec: Long = System.currentTimeMillis() / 1000L,
): Boolean = isDirectVideoStream(url) &&
    isSignedVideoUrlExpired(url, nowEpochSec = nowEpochSec) &&
    canRefreshVideoSource(url, fid, statusId, isLiveHint)

/**
 * 网络/HTTP/数据源及容器解析失败（如 CDN 返回 403 错误页 HTML）时允许尝试自动刷新一次直链；
 * 纯本地解码器或音频输出异常不无谓重拉。
 */
internal fun shouldAttemptStreamRefreshOnError(errorCode: Int): Boolean =
    errorCode == PlaybackException.ERROR_CODE_UNSPECIFIED ||
        errorCode == PlaybackException.ERROR_CODE_REMOTE_ERROR ||
        errorCode in 2000..3999

internal fun videoStreamPathKey(url: String): String {
    val clean = url.trim().substringBefore('?').substringBefore('#')
    if (clean.isBlank()) return ""
    val path = runCatching { URI(clean).path.orEmpty() }.getOrDefault("")
    val fileName = path.substringAfterLast('/').trim().lowercase()
    return fileName.ifBlank { path.trim('/').lowercase() }
}

/**
 * 从最新拉取的 [Status] 中匹配对应的视频条目（支持普通单视频、转发视频及混排图集视频）。
 */
internal fun pickRefreshedVideoFromStatus(
    status: Status,
    oldUrl: String,
    fid: String = "",
    title: String = "",
): VideoInfo? {
    val candidates = buildList {
        fun collect(s: Status) {
            s.video?.takeIf { it.url.isNotBlank() }?.let(::add)
            s.pics.forEach { pic ->
                if (pic.isVideo) {
                    add(
                        VideoInfo(
                            title = pic.videoTitle,
                            cover = pic.large.ifBlank { pic.thumb },
                            url = pic.videoUrl,
                            durationSec = pic.durationSec,
                            fid = pic.videoFid,
                            statusId = pic.videoStatusId.ifBlank { s.id },
                        ),
                    )
                }
            }
        }
        collect(status)
        status.retweeted?.let(::collect)
    }
    if (candidates.isEmpty()) return null
    if (fid.isNotBlank()) {
        candidates.firstOrNull { it.fid == fid }?.let { return it }
    }
    val oldKey = videoStreamPathKey(oldUrl)
    if (oldKey.isNotBlank()) {
        candidates.firstOrNull { videoStreamPathKey(it.url) == oldKey }?.let { return it }
    }
    if (title.isNotBlank()) {
        candidates.firstOrNull { it.title == title }?.let { return it }
    }
    return if (candidates.size == 1) candidates.first() else null
}

/**
 * 为已过期或播放报错（如 403）的视频重新换取最新签名直链：
 * 1. 优先用稳定标识 [fid] 直接向微博视频接口换取最新高清流；
 * 2. 缺少 [fid] 或接口未命中时，按 [statusId] 强制拉取单条微博（跳过磁盘缓存）刷新视频信息；
 * 3. 直播链接按 `liveId` 重新解析。
 */
internal suspend fun refreshPlayableVideo(
    rawUrl: String,
    fid: String = "",
    statusId: String = "",
    titleHint: String = "",
    cacheDir: File? = null,
    isLiveHint: Boolean = false,
): ResolvedVideo? = withContext(Dispatchers.IO) {
    val unwrapped = unwrapWeiboLink(rawUrl)
    val normalized = resolveWeiboLink(unwrapped) ?: unwrapped
    val liveId = extractWeiboLiveId(normalized) ?: extractWeiboLiveId(rawUrl)
    val isLiveTarget = isLiveHint || liveId != null || isWeiboLiveLink(normalized) || isWeiboLiveLink(rawUrl)

    if (liveId != null) {
        when (val liveRes = resolveWeiboLiveRoom(liveId, cacheDir)) {
            is ResolvedVideo.DirectStream -> return@withContext liveRes
            else -> Unit
        }
    }

    val effectiveFid = fid.trim().ifBlank { extractWeiboVideoFid(normalized).orEmpty() }
    if (effectiveFid.isNotBlank()) {
        when (val fidRes = resolveWeiboVideoByFid(effectiveFid, cacheDir)) {
            is ResolvedVideo.DirectStream -> return@withContext fidRes
            else -> Unit
        }
    }

    if (statusId.isNotBlank() && App.isRepoReady()) {
        val freshStatus = runCatching { App.repo.detail(statusId, force = true) }.getOrNull()
        val freshVideo = freshStatus?.let {
            pickRefreshedVideoFromStatus(it, oldUrl = normalized, fid = effectiveFid, title = titleHint)
        }
        if (freshVideo != null) {
            val freshFid = freshVideo.fid.trim()
            if (freshFid.isNotBlank() && freshFid != effectiveFid && isSignedVideoUrlExpired(freshVideo.url)) {
                when (val fidRes = resolveWeiboVideoByFid(freshFid, cacheDir)) {
                    is ResolvedVideo.DirectStream -> return@withContext fidRes
                    else -> Unit
                }
            }
            if (isDirectVideoStream(freshVideo.url) && !isSignedVideoUrlExpired(freshVideo.url)) {
                return@withContext ResolvedVideo.DirectStream(
                    url = normalizeWeiboStreamUrl(freshVideo.url),
                    referer = defaultRefererFor(freshVideo.url),
                    title = freshVideo.title.takeIf { it.isNotBlank() },
                    isLive = freshVideo.isLive,
                )
            }
            if (freshVideo.url.isNotBlank() && freshVideo.url != rawUrl && freshVideo.url != normalized) {
                val resolved = resolvePlayableVideo(
                    rawUrl = freshVideo.url,
                    cacheDir = cacheDir,
                    isLiveHint = freshVideo.isLive || isLiveTarget,
                    fid = freshFid,
                    statusId = "",
                    titleHint = freshVideo.title.ifBlank { titleHint },
                )
                if (resolved is ResolvedVideo.DirectStream) return@withContext resolved
            }
            if (freshFid.isNotBlank() && freshFid != effectiveFid) {
                when (val fidRes = resolveWeiboVideoByFid(freshFid, cacheDir)) {
                    is ResolvedVideo.DirectStream -> return@withContext fidRes
                    else -> Unit
                }
            }
        }
    }

    null
}

internal suspend fun resolvePlayableVideo(
    rawUrl: String,
    cacheDir: File? = null,
    isLiveHint: Boolean = false,
    fid: String = "",
    statusId: String = "",
    titleHint: String = "",
): ResolvedVideo = withContext(Dispatchers.IO) {
    val unwrapped = unwrapWeiboLink(rawUrl)
    val normalized = resolveWeiboLink(unwrapped) ?: unwrapped
    val initialLiveId = extractWeiboLiveId(rawUrl) ?: extractWeiboLiveId(unwrapped)
    val finalUrl = if (isWeiboShortLink(normalized) || normalized.contains("b23.tv/", ignoreCase = true)) {
        val cookie = runCatching { App.repo.cookie() }.getOrNull()
        val redirected = runCatching { Api.resolveRedirect(normalized, cookie) }.getOrDefault(normalized)
        unwrapWeiboLink(redirected)
    } else {
        normalized
    }
    val liveId = extractWeiboLiveId(finalUrl) ?: initialLiveId
    val isLiveTarget = isLiveHint || liveId != null || isWeiboLiveLink(finalUrl) || isWeiboLiveLink(rawUrl)

    var liveUnavailable: ResolvedVideo.Unavailable? = null
    if (liveId != null) {
        when (val liveResult = resolveWeiboLiveRoom(liveId, cacheDir)) {
            is ResolvedVideo.Unavailable -> liveUnavailable = liveResult
            null -> Unit
            else -> return@withContext liveResult
        }
    }

    if (isM3u8Stream(finalUrl)) {
        if (!isLiveTarget && isSignedVideoUrlExpired(finalUrl) && canRefreshVideoSource(finalUrl, fid, statusId, false)) {
            refreshPlayableVideo(
                rawUrl = finalUrl,
                fid = fid,
                statusId = statusId,
                titleHint = titleHint,
                cacheDir = cacheDir,
                isLiveHint = false,
            )?.let { refreshed ->
                if (refreshed is ResolvedVideo.DirectStream) return@withContext refreshed
            }
        }
        val referer = defaultRefererFor(finalUrl)
        val liveM3u8 = isLiveTarget || isLiveM3u8Playlist(finalUrl, referer)
        if (!liveM3u8 && cacheDir != null) {
            resolveM3u8ToCachedTs(cacheDir, finalUrl, referer)?.let { file ->
                return@withContext ResolvedVideo.DirectStream(file.toURI().toString(), isLive = false)
            }
        }
        return@withContext ResolvedVideo.DirectStream(
            url = normalizeWeiboStreamUrl(finalUrl),
            referer = referer,
            isLive = liveM3u8,
        )
    }

    if (isDirectVideoStream(finalUrl)) {
        if (isSignedVideoUrlExpired(finalUrl) && canRefreshVideoSource(finalUrl, fid, statusId, isLiveTarget)) {
            refreshPlayableVideo(
                rawUrl = finalUrl,
                fid = fid,
                statusId = statusId,
                titleHint = titleHint,
                cacheDir = cacheDir,
                isLiveHint = isLiveTarget,
            )?.let { refreshed ->
                if (refreshed is ResolvedVideo.DirectStream) return@withContext refreshed
            }
        }
        return@withContext ResolvedVideo.DirectStream(
            url = normalizeWeiboStreamUrl(finalUrl),
            referer = defaultRefererFor(finalUrl),
            isLive = isLiveTarget,
        )
    }

    val effectiveFid = fid.trim().ifBlank { extractWeiboVideoFid(finalUrl).orEmpty() }
    if (effectiveFid.isNotBlank()) {
        when (val fidResult = resolveWeiboVideoByFid(effectiveFid, cacheDir)) {
            is ResolvedVideo.DirectStream -> return@withContext fidResult
            is ResolvedVideo.Unavailable -> if (liveUnavailable == null) liveUnavailable = fidResult
            is ResolvedVideo.WebPage -> return@withContext fidResult
            null -> Unit
        }
    }

    if (liveUnavailable != null) return@withContext liveUnavailable

    extractBilibiliBvid(finalUrl)?.let { bvid ->
        resolveBilibiliVideo(bvid)?.let { stream ->
            return@withContext normalizeResolvedStream(stream, cacheDir)
        }
    }

    val htmlResolved = resolveHtmlDirectVideo(finalUrl)
    if (htmlResolved != null) {
        val tagged = htmlResolved.copy(isLive = isLiveTarget || htmlResolved.isLive)
        return@withContext if (tagged.isLive) tagged else normalizeResolvedStream(tagged, cacheDir)
    }

    val webTarget = resolveWeiboLink(finalUrl) ?: finalUrl
    ResolvedVideo.WebPage(webTarget, isLive = isLiveTarget)
}

private fun normalizeResolvedStream(
    stream: ResolvedVideo.DirectStream,
    cacheDir: File?,
): ResolvedVideo {
    if (stream.isLive) return stream
    val effectiveReferer = stream.referer?.takeIf { it.isNotBlank() } ?: defaultRefererFor(stream.url)
    if (isM3u8Stream(stream.url) || checkIsM3u8(stream.url, effectiveReferer)) {
        if (isLiveM3u8Playlist(stream.url, effectiveReferer)) {
            return stream.copy(
                url = normalizeWeiboStreamUrl(stream.url),
                referer = effectiveReferer,
                isLive = true,
            )
        }
        if (cacheDir != null) {
            resolveM3u8ToCachedTs(cacheDir, stream.url, effectiveReferer)?.let { cached ->
                return stream.copy(url = cached.toURI().toString(), referer = null)
            }
        }
    }
    return stream.copy(referer = effectiveReferer)
}

private fun firstPlayInfoPlaybackUrl(playInfo: JSONObject): String {
    val arr = playInfo.optJSONArray("playback_list") ?: return ""
    var fallback = ""
    for (i in 0 until arr.length()) {
        val play = arr.optJSONObject(i)?.optJSONObject("play_info") ?: continue
        val url = play.optString("url").trim()
        if (url.isBlank()) continue
        if (fallback.isBlank()) fallback = url
        val mime = play.optString("mime")
        if (mime.contains("mp4", ignoreCase = true) || url.contains(".mp4", ignoreCase = true)) {
            return url
        }
    }
    return fallback
}

private fun resolveWeiboVideoByFid(fid: String, cacheDir: File? = null): ResolvedVideo? {
    val cookie = runCatching { App.repo.cookie() }.getOrNull()
    var confirmedUnavailable: ResolvedVideo.Unavailable? = null
    runCatching {
        val payload = JSONObject().put("Component_Play_Playinfo", JSONObject().put("oid", fid)).toString()
        val request = Request.Builder()
            .url("https://h5.video.weibo.com/api/component?page=/show/$fid")
            .header("User-Agent", Api.WEB_UA)
            .header("Referer", "https://h5.video.weibo.com/show/$fid")
            .header("Origin", "https://h5.video.weibo.com")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .apply { if (!cookie.isNullOrBlank()) header("Cookie", cookie) }
            .post(FormBody.Builder().add("data", payload).build())
            .build()
        VideoHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use
            val root = JSONObject(response.body?.string().orEmpty())
            val playInfo = root.optJSONObject("data")?.optJSONObject("Component_Play_Playinfo") ?: return@use
            val title = playInfo.optString("title").takeIf { it.isNotBlank() }

            val liveIdFromPlay = playInfo.optString("live_id").takeIf { it.isNotBlank() }
                ?: playInfo.optJSONObject("live_info")?.optString("live_id")?.takeIf { it.isNotBlank() }
            if (liveIdFromPlay != null) {
                resolveWeiboLiveRoom(liveIdFromPlay, cacheDir)?.let { return it }
            }

            val isLivePlay = playInfo.optInt("is_live", 0) == 1 ||
                playInfo.optBoolean("is_live", false) ||
                playInfo.optString("object_type").equals("live", ignoreCase = true)
            val liveStatus = playInfo.optInt("live_status", -1)

            val urls = playInfo.optJSONObject("urls")
            val picked = pickBestQualityUrl(urls)
                ?: playInfo.optString("replay_hd").takeIf { it.isNotBlank() }
                ?: playInfo.optString("replay_ld").takeIf { it.isNotBlank() }
                ?: playInfo.optString("live_hd").takeIf { it.isNotBlank() }
                ?: playInfo.optString("live_ld").takeIf { it.isNotBlank() }
                ?: firstPlayInfoPlaybackUrl(playInfo).takeIf { it.isNotBlank() }
                ?: playInfo.optString("stream_url_hd").takeIf { it.isNotBlank() }
                ?: playInfo.optString("stream_url").takeIf { it.isNotBlank() }
            if (!picked.isNullOrBlank()) {
                val url = normalizeWeiboStreamUrl(picked)
                val isLive = (isLivePlay || liveStatus == 1) && liveStatus != 3
                val stream = ResolvedVideo.DirectStream(url, referer = "https://weibo.com/", title = title, isLive = isLive)
                return if (isLive) stream else normalizeResolvedStream(stream, cacheDir)
            }
            if (isLivePlay || liveStatus >= 0) {
                confirmedUnavailable = ResolvedVideo.Unavailable(
                    message = if (liveStatus == 0) "直播未开始" else "该直播已结束或暂无回放",
                    webUrl = "https://video.weibo.com/show?fid=$fid",
                    title = title,
                )
            }
        }
    }

    runCatching {
        val request = Request.Builder()
            .url("https://m.weibo.cn/s/video/object?object_id=$fid")
            .header("User-Agent", Api.WEB_UA)
            .header("Referer", "https://m.weibo.cn/")
            .apply { if (!cookie.isNullOrBlank()) header("Cookie", cookie) }
            .get()
            .build()
        VideoHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use
            val root = JSONObject(response.body?.string().orEmpty())
            val obj = root.optJSONObject("data")?.optJSONObject("object")
            if (obj == null) {
                if (confirmedUnavailable == null && root.optInt("ok", 1) == 0) {
                    val msg = root.optString("msg").ifBlank { "视频或直播已失效" }
                    confirmedUnavailable = ResolvedVideo.Unavailable(
                        message = msg,
                        webUrl = "https://video.weibo.com/show?fid=$fid",
                    )
                }
                return@use
            }
            val objLiveId = obj.optString("live_id").takeIf { it.isNotBlank() }
            if (objLiveId != null) {
                resolveWeiboLiveRoom(objLiveId, cacheDir)?.let { return it }
            }
            val stream = obj.optJSONObject("stream") ?: return@use
            val isLiveObj = obj.optString("object_type").equals("live", ignoreCase = true) ||
                stream.optInt("live_status", -1) == 1
            val picked = stream.optString("hd_url")
                .ifBlank { stream.optString("url") }
                .ifBlank { stream.optString("ld_url") }
            if (picked.isNotBlank()) {
                val url = normalizeWeiboStreamUrl(picked)
                val title = obj.optString("summary").takeIf { it.isNotBlank() }
                val res = ResolvedVideo.DirectStream(url, referer = "https://m.weibo.cn/", title = title, isLive = isLiveObj)
                return if (isLiveObj) res else normalizeResolvedStream(res, cacheDir)
            }
        }
    }
    return confirmedUnavailable
}

private fun pickBestQualityUrl(urls: JSONObject?): String? {
    if (urls == null) return null
    val preferred = listOf("超清 1080P", "高清 720P", "标清 480P", "流畅 360P")
    for (label in preferred) {
        val u = urls.optString(label)
        if (u.isNotBlank()) return u
    }
    val keys = urls.keys()
    while (keys.hasNext()) {
        val u = urls.optString(keys.next())
        if (u.isNotBlank()) return u
    }
    return null
}

private fun resolveBilibiliVideo(bvid: String): ResolvedVideo.DirectStream? = runCatching {
    val viewReq = Request.Builder()
        .url("https://api.bilibili.com/x/web-interface/view?bvid=$bvid")
        .header("User-Agent", Api.DESKTOP_UA)
        .header("Referer", "https://www.bilibili.com/")
        .get()
        .build()
    var cid = 0L
    var title: String? = null
    VideoHttpClient.newCall(viewReq).execute().use { response ->
        if (!response.isSuccessful) return null
        val data = JSONObject(response.body?.string().orEmpty()).optJSONObject("data") ?: return null
        cid = data.optLong("cid", 0L)
        title = data.optString("title").takeIf { it.isNotBlank() }
    }
    if (cid <= 0L) return null
    val playReq = Request.Builder()
        .url("https://api.bilibili.com/x/player/playurl?bvid=$bvid&cid=$cid&qn=64&fnval=1&platform=html5&high_quality=1")
        .header("User-Agent", Api.WEB_UA)
        .header("Referer", "https://m.bilibili.com/video/$bvid")
        .get()
        .build()
    VideoHttpClient.newCall(playReq).execute().use { response ->
        if (!response.isSuccessful) return null
        val durl = JSONObject(response.body?.string().orEmpty())
            .optJSONObject("data")?.optJSONArray("durl") ?: return null
        val url = durl.optJSONObject(0)?.optString("url").orEmpty()
        if (url.isBlank()) return null
        ResolvedVideo.DirectStream(url, referer = "https://www.bilibili.com/", title = title)
    }
}.getOrNull()

private fun resolveHtmlDirectVideo(url: String): ResolvedVideo.DirectStream? = runCatching {
    val request = Request.Builder()
        .url(url)
        .header("User-Agent", Api.WEB_UA)
        .get()
        .build()
    VideoHttpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) return null
        val contentType = response.header("Content-Type").orEmpty().lowercase()
        if (contentType.startsWith("video/") || contentType.contains("mpegurl") || contentType.contains("m3u8")) {
            return ResolvedVideo.DirectStream(response.request.url.toString(), referer = url)
        }
        if (!contentType.contains("html") && contentType.isNotEmpty()) return null
        val body = response.body?.string().orEmpty().take(512 * 1024)
        val extracted = extractHtmlVideoUrl(body, response.request.url.toString()) ?: return null
        ResolvedVideo.DirectStream(extracted, referer = response.request.url.toString())
    }
}.getOrNull()

internal suspend fun downloadVideoToGallery(
    context: Context,
    videoUrl: String,
    referer: String? = null,
    onProgress: (String) -> Unit,
): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
        if (videoUrl.startsWith("file:", ignoreCase = true)) {
            val localPath = URI(videoUrl).path ?: error("本地缓存路径无效")
            val source = File(localPath)
            if (!source.isFile || source.length() <= 0L) error("本地缓存视频为空")
            val isTs = source.name.endsWith(".ts", ignoreCase = true)
            onProgress("正在保存到相册…")
            saveVideoFileToMediaStore(
                context = context,
                source = source,
                ext = if (isTs) "ts" else "mp4",
                mime = if (isTs) "video/mp2t" else "video/mp4",
            )
            return@runCatching
        }
        val effectiveReferer = referer?.takeIf { it.isNotBlank() } ?: defaultRefererFor(videoUrl)
        val tempFile = File(context.cacheDir, "cobo_dl_${System.currentTimeMillis()}.tmp")
        try {
            val isM3u8 = isM3u8Stream(videoUrl) || checkIsM3u8(videoUrl, effectiveReferer)
            val ext: String
            val mime: String
            if (isM3u8) {
                onProgress("正在解析视频分片…")
                downloadM3u8ToFile(videoUrl, effectiveReferer, tempFile, onProgress)
                ext = "ts"
                mime = "video/mp2t"
            } else {
                onProgress("正在下载视频…")
                downloadDirectToFile(videoUrl, effectiveReferer, tempFile, onProgress)
                ext = "mp4"
                mime = "video/mp4"
            }
            if (tempFile.length() <= 0L) error("视频内容为空")
            onProgress("正在保存到相册…")
            saveVideoFileToMediaStore(context, tempFile, ext, mime)
        } finally {
            tempFile.delete()
        }
    }
}

private fun resolveM3u8ToCachedTs(
    cacheDir: File,
    playlistUrl: String,
    referer: String?,
): File? = runCatching {
    val dir = File(cacheDir, "video_cache").apply { mkdirs() }
    val digest = MessageDigest.getInstance("SHA-256").digest(playlistUrl.toByteArray())
    val hash = digest.joinToString("") { "%02x".format(it) }.take(32)
    val cached = File(dir, "$hash.ts")
    if (cached.isFile && cached.length() > 0L) return cached

    // Trim stale video cache entries when accumulated size exceeds 150 MB.
    val existing = dir.listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() }.orEmpty()
    var totalSize = existing.sumOf { it.length() }
    for (old in existing) {
        if (totalSize <= 150L * 1024 * 1024) break
        val len = old.length()
        if (old.delete()) totalSize -= len
    }

    val tmp = File(dir, "$hash.part")
    try {
        downloadM3u8ToFile(playlistUrl, referer, tmp) {}
        if (!tmp.isFile || tmp.length() <= 0L) {
            tmp.delete()
            return null
        }
        if (!tmp.renameTo(cached)) {
            tmp.copyTo(cached, overwrite = true)
            tmp.delete()
        }
        cached
    } catch (_: Throwable) {
        tmp.delete()
        null
    }
}.getOrNull()

private fun checkIsM3u8(url: String, referer: String?): Boolean = runCatching {
    val req = Request.Builder()
        .url(url)
        .header("User-Agent", Api.WEB_UA)
        .apply { if (!referer.isNullOrBlank()) header("Referer", referer) }
        .head()
        .build()
    VideoHttpClient.newCall(req).execute().use { res ->
        val ct = res.header("Content-Type").orEmpty().lowercase()
        ct.contains("mpegurl") || ct.contains("m3u8")
    }
}.getOrDefault(false)

private fun downloadDirectToFile(
    url: String,
    referer: String?,
    dest: File,
    onProgress: (String) -> Unit,
) {
    val request = Request.Builder()
        .url(url)
        .header("User-Agent", Api.WEB_UA)
        .apply { if (!referer.isNullOrBlank()) header("Referer", referer) }
        .get()
        .build()
    VideoHttpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) error("下载失败（HTTP ${response.code}）")
        val body = response.body ?: error("空响应")
        val total = body.contentLength()
        val buffer = ByteArray(16 * 1024)
        var downloaded = 0L
        var lastPct = -1
        FileOutputStream(dest).use { out ->
            body.byteStream().use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    out.write(buffer, 0, read)
                    downloaded += read
                    if (total > 0L) {
                        val pct = ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
                        if (pct != lastPct && (pct % 5 == 0 || pct == 100)) {
                            lastPct = pct
                            onProgress("正在下载视频 $pct%…")
                        }
                    }
                }
            }
        }
    }
}

internal data class M3u8Playlist(
    val variantUrl: String? = null,
    val segments: List<String> = emptyList(),
    val keyMethod: String? = null,
    val keyUri: String? = null,
    val keyIvHex: String? = null,
)

internal fun parseM3u8Playlist(content: String, baseUrl: String): M3u8Playlist {
    val base = baseUrl.toHttpUrlOrNull()
    fun resolve(ref: String): String =
        if (ref.startsWith("http://", true) || ref.startsWith("https://", true)) ref
        else base?.resolve(ref)?.toString() ?: ref

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

private fun fetchText(url: String, referer: String?): String {
    val request = Request.Builder()
        .url(url)
        .header("User-Agent", Api.WEB_UA)
        .apply { if (!referer.isNullOrBlank()) header("Referer", referer) }
        .get()
        .build()
    return VideoHttpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) error("获取列表失败（HTTP ${response.code}）")
        response.body?.string().orEmpty()
    }
}

private fun fetchBytes(url: String, referer: String?): ByteArray {
    val request = Request.Builder()
        .url(url)
        .header("User-Agent", Api.WEB_UA)
        .apply { if (!referer.isNullOrBlank()) header("Referer", referer) }
        .get()
        .build()
    return VideoHttpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) error("分片下载失败（HTTP ${response.code}）")
        val out = ByteArrayOutputStream()
        response.body?.byteStream()?.copyTo(out)
        out.toByteArray()
    }
}

private fun downloadM3u8ToFile(
    playlistUrl: String,
    referer: String?,
    dest: File,
    onProgress: (String) -> Unit,
) {
    var targetUrl = playlistUrl
    var parsed = parseM3u8Playlist(fetchText(targetUrl, referer), targetUrl)
    val variant = parsed.variantUrl
    if (variant != null && parsed.segments.isEmpty()) {
        targetUrl = variant
        parsed = parseM3u8Playlist(fetchText(targetUrl, referer), targetUrl)
    }
    if (parsed.segments.isEmpty()) error("未解析到视频分片")

    val method = parsed.keyMethod?.uppercase()
    if (method != null && method != "NONE" && method != "AES-128") {
        error("该视频受版权保护，暂不支持下载")
    }
    val keyUri = parsed.keyUri
    val aesKey = if (method == "AES-128" && !keyUri.isNullOrBlank()) {
        fetchBytes(keyUri, referer)
    } else null

    val ivBytes = parsed.keyIvHex?.let { hex ->
        val padded = hex.padStart(32, '0').takeLast(32)
        ByteArray(16) { idx -> padded.substring(idx * 2, idx * 2 + 2).toInt(16).toByte() }
    }

    val pool = Executors.newFixedThreadPool(4)
    try {
        FileOutputStream(dest).use { out ->
            val total = parsed.segments.size
            val batchSize = 4
            var start = 0
            while (start < total) {
                val end = (start + batchSize).coerceAtMost(total)
                val futures = (start until end).map { idx ->
                    val segUrl = parsed.segments[idx]
                    pool.submit(Callable {
                        val raw = fetchBytes(segUrl, referer)
                        if (aesKey != null) {
                            val iv = ivBytes ?: ByteArray(16).apply {
                                val seq = idx.toLong()
                                for (b in 0..7) this[15 - b] = ((seq ushr (b * 8)) and 0xFF).toByte()
                            }
                            Cipher.getInstance("AES/CBC/PKCS5Padding").run {
                                init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"), IvParameterSpec(iv))
                                doFinal(raw)
                            }
                        } else {
                            raw
                        }
                    })
                }
                for (future in futures) {
                    out.write(future.get())
                }
                start = end
                val pct = (end * 100 / total).coerceIn(0, 100)
                onProgress("正在下载视频 $pct%…")
            }
        }
    } finally {
        pool.shutdownNow()
    }
}

private fun saveVideoFileToMediaStore(
    context: Context,
    source: File,
    ext: String,
    mime: String,
) {
    val name = "cobo_video_${System.currentTimeMillis()}.$ext"
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, name)
        put(MediaStore.Video.Media.MIME_TYPE, mime)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Cobo")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
    }
    val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        ?: error("无法创建相册文件")
    resolver.openOutputStream(uri)?.use { out ->
        source.inputStream().use { input -> input.copyTo(out) }
    } ?: error("无法写入相册文件")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        values.clear()
        values.put(MediaStore.Video.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    }
}
