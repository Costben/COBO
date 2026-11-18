package com.cobo.app.screen

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.cobo.app.App
import com.cobo.app.data.Api
import com.cobo.app.ui.isWeiboShortLink
import com.cobo.app.ui.resolveWeiboLink
import com.cobo.app.ui.unwrapSinaUrl
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
    ) : ResolvedVideo

    data class WebPage(
        val url: String,
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
    if (path.endsWith(".mp4") || path.endsWith(".m4v") || path.endsWith(".mov") || path.endsWith(".webm")) {
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
        path.endsWith(".mov") || trimmed.contains(".mp4?", ignoreCase = true) ||
        trimmed.contains(".m3u8?", ignoreCase = true)
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

internal suspend fun resolvePlayableVideo(
    rawUrl: String,
    cacheDir: File? = null,
): ResolvedVideo = withContext(Dispatchers.IO) {
    val unwrapped = unwrapSinaUrl(rawUrl) ?: rawUrl
    val normalized = resolveWeiboLink(unwrapped) ?: unwrapped
    val finalUrl = if (isWeiboShortLink(normalized) || normalized.contains("b23.tv/", ignoreCase = true)) {
        val cookie = runCatching { App.repo.cookie() }.getOrNull()
        val redirected = runCatching { Api.resolveRedirect(normalized, cookie) }.getOrDefault(normalized)
        unwrapSinaUrl(redirected) ?: redirected
    } else {
        normalized
    }

    if (isM3u8Stream(finalUrl)) {
        if (cacheDir != null) {
            resolveM3u8ToCachedTs(cacheDir, finalUrl, defaultRefererFor(finalUrl))?.let { file ->
                return@withContext ResolvedVideo.DirectStream(file.toURI().toString())
            }
        }
    }

    if (isDirectVideoStream(finalUrl)) {
        return@withContext ResolvedVideo.DirectStream(finalUrl, referer = defaultRefererFor(finalUrl))
    }

    extractWeiboVideoFid(finalUrl)?.let { fid ->
        resolveWeiboVideoByFid(fid)?.let { stream ->
            return@withContext normalizeResolvedStream(stream, cacheDir)
        }
    }

    extractBilibiliBvid(finalUrl)?.let { bvid ->
        resolveBilibiliVideo(bvid)?.let { stream ->
            return@withContext normalizeResolvedStream(stream, cacheDir)
        }
    }

    val htmlResolved = resolveHtmlDirectVideo(finalUrl)
    if (htmlResolved != null) {
        return@withContext normalizeResolvedStream(htmlResolved, cacheDir)
    }

    ResolvedVideo.WebPage(finalUrl)
}

private fun normalizeResolvedStream(
    stream: ResolvedVideo.DirectStream,
    cacheDir: File?,
): ResolvedVideo {
    val effectiveReferer = stream.referer?.takeIf { it.isNotBlank() } ?: defaultRefererFor(stream.url)
    if (cacheDir != null && (isM3u8Stream(stream.url) || checkIsM3u8(stream.url, effectiveReferer))) {
        resolveM3u8ToCachedTs(cacheDir, stream.url, effectiveReferer)?.let { cached ->
            return stream.copy(url = cached.toURI().toString(), referer = null)
        }
    }
    return stream.copy(referer = effectiveReferer)
}

private fun resolveWeiboVideoByFid(fid: String): ResolvedVideo.DirectStream? {
    val cookie = runCatching { App.repo.cookie() }.getOrNull()
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
            val urls = playInfo.optJSONObject("urls")
            val picked = pickBestQualityUrl(urls)
                ?: playInfo.optString("stream_url_hd").takeIf { it.isNotBlank() }
                ?: playInfo.optString("stream_url").takeIf { it.isNotBlank() }
            if (!picked.isNullOrBlank()) {
                val url = if (picked.startsWith("//")) "https:$picked" else picked
                return ResolvedVideo.DirectStream(url, referer = "https://weibo.com/", title = title)
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
            val obj = JSONObject(response.body?.string().orEmpty())
                .optJSONObject("data")?.optJSONObject("object") ?: return@use
            val stream = obj.optJSONObject("stream") ?: return@use
            val picked = stream.optString("hd_url")
                .ifBlank { stream.optString("url") }
                .ifBlank { stream.optString("ld_url") }
            if (picked.isNotBlank()) {
                val url = if (picked.startsWith("//")) "https:$picked" else picked
                val title = obj.optJSONObject("summary")?.toString()?.takeIf { it.isNotBlank() }
                return ResolvedVideo.DirectStream(url, referer = "https://m.weibo.cn/", title = title)
            }
        }
    }
    return null
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
