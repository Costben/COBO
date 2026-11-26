package com.cobo.app.data

import com.cobo.app.json.JSONArray
import com.cobo.app.json.JSONObject
import com.cobo.app.platform.PlatformContext
import com.cobo.app.platform.PlatformFile
import com.cobo.app.platform.PlatformHttp
import com.cobo.app.platform.PlatformHttpResponse
import com.cobo.app.platform.PlatformPrefs
import com.cobo.app.platform.currentAppVersionName
import com.cobo.app.platform.currentTimeMillis
import com.cobo.app.platform.ioDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

const val UpdateCheckInterval = 12 * 60 * 60 * 1000L

data class UpdateInfo(
    val version: String,
    val notes: String,
    val apkUrl: String?,
    val pageUrl: String,
    val prerelease: Boolean,
    val publishedAt: String = "",
)

data class ReleasesResult(
    val items: List<UpdateInfo>,
    val stale: Boolean,
)

data class DownloadProgress(
    val downloaded: Long,
    val total: Long,
    val speedBytesPerSec: Long = 0L,
) {
    val fraction: Float?
        get() = if (total > 0L) (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f) else null
}

internal expect object PlatformUpdater {
    val userAgent: String
    val preferIpaAsset: Boolean

    fun openDownload(info: UpdateInfo)

    suspend fun downloadPackage(
        context: PlatformContext,
        url: String,
        version: String,
        force: Boolean,
        onProgress: (progress: Float, currentBytes: Long, totalBytes: Long) -> Unit,
    ): PlatformFile

    fun installPackage(context: PlatformContext, file: PlatformFile)
}

object Updater {
    private const val ReleasesUrl = "https://api.github.com/repos/Costben/COBO/releases?per_page=20"
    private const val CachePrefs = "updater"
    private const val CacheBodyKey = "releases_body"
    private const val CacheEtagKey = "releases_etag"

    fun currentVersion(@Suppress("UNUSED_PARAMETER") context: PlatformContext = PlatformContext()): String =
        currentAppVersionName()

    suspend fun latest(current: String, includePrerelease: Boolean = true): UpdateInfo? =
        withContext(ioDispatcher) {
            parseLatest(fetchReleases().body, current, includePrerelease)
        }

    suspend fun releases(includePrerelease: Boolean = true): List<UpdateInfo> =
        releasesResult(includePrerelease).items

    suspend fun releasesResult(includePrerelease: Boolean = true): ReleasesResult =
        withContext(ioDispatcher) {
            val fetched = fetchReleases()
            ReleasesResult(parseReleases(fetched.body, includePrerelease), fetched.stale)
        }

    private class Fetched(val body: String, val stale: Boolean)

    private suspend fun fetchReleases(): Fetched {
        val sp = PlatformPrefs(CachePrefs)
        val cachedBody = sp.getString(CacheBodyKey, null)?.takeIf { it.isNotBlank() }
        val cachedEtag = sp.getString(CacheEtagKey, null)
        val headers = linkedMapOf(
            "Accept" to "application/vnd.github+json",
            "User-Agent" to PlatformUpdater.userAgent,
        )
        if (!cachedEtag.isNullOrBlank() && cachedBody != null) {
            headers["If-None-Match"] = cachedEtag
        }
        val resp = try {
            PlatformHttp.get(ReleasesUrl, headers = headers)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            if (cachedBody != null) return Fetched(cachedBody, stale = true)
            throw ApiException("检查更新失败，请检查网络")
        }
        if (resp.code == 304 && cachedBody != null) {
            return Fetched(cachedBody, stale = false)
        }
        if (resp.isSuccessful && resp.bodyText.isNotBlank()) {
            sp.edit().apply {
                putString(CacheBodyKey, resp.bodyText)
                resp.header("ETag")?.let { putString(CacheEtagKey, it) }
            }.apply()
            return Fetched(resp.bodyText, stale = false)
        }
        if (cachedBody != null) return Fetched(cachedBody, stale = true)
        throw ApiException(failureMessage(resp))
    }

    internal fun failureMessage(response: PlatformHttpResponse): String =
        failureMessage(response.code, response.header("X-RateLimit-Reset"))

    internal fun failureMessage(code: Int, rateLimitResetHeader: String?): String {
        if (code == 403 || code == 429) {
            val resetAt = rateLimitResetHeader?.toLongOrNull()
            val now = currentTimeMillis() / 1000
            if (resetAt != null && resetAt > now) {
                val minutes = ((resetAt - now) / 60).coerceAtLeast(1)
                return "GitHub 接口已限流，约 $minutes 分钟后恢复"
            }
            return "GitHub 接口已限流，请稍后再试"
        }
        return "检查更新失败：HTTP $code"
    }

    fun parseReleases(body: String, includePrerelease: Boolean = true): List<UpdateInfo> {
        val text = body.trim()
        if (!text.startsWith("[")) {
            val message = runCatching { JSONObject(text).optString("message") }.getOrNull().orEmpty()
            throw ApiException(message.ifBlank { "检查更新失败：接口返回异常" })
        }
        val array = JSONArray(text)
        val out = ArrayList<UpdateInfo>(array.length())
        val preferIpa = PlatformUpdater.preferIpaAsset
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            if (obj.optBoolean("draft", false)) continue
            val tag = obj.optString("tag_name").trim()
            val version = tag.removePrefix("v").removePrefix("V")
            if (version.isBlank()) continue
            val prerelease = obj.optBoolean("prerelease", false) || version.contains('-')
            if (!includePrerelease && prerelease) continue
            val pageUrl = obj.optString("html_url").trim()
            val notes = obj.optString("body").trim()
            val publishedAt = obj.optString("published_at").trim()
            val assets = obj.optJSONArray("assets")
            var primaryUrl: String? = null
            var fallbackUrl: String? = null
            if (assets != null) {
                for (j in 0 until assets.length()) {
                    val asset = assets.optJSONObject(j) ?: continue
                    val name = asset.optString("name").lowercase()
                    val dl = asset.optString("browser_download_url").trim()
                    if (dl.isBlank()) continue
                    if (preferIpa) {
                        if (name.endsWith(".ipa")) {
                            primaryUrl = dl
                            break
                        }
                        if (fallbackUrl == null && name.endsWith(".apk")) fallbackUrl = dl
                    } else {
                        if (name.endsWith(".apk")) {
                            primaryUrl = dl
                            break
                        }
                    }
                }
            }
            out.add(
                UpdateInfo(
                    version = version,
                    notes = notes,
                    apkUrl = primaryUrl ?: fallbackUrl,
                    pageUrl = pageUrl,
                    prerelease = prerelease,
                    publishedAt = publishedAt,
                ),
            )
        }
        return out
    }

    fun parseLatest(body: String, current: String, includePrerelease: Boolean = true): UpdateInfo? {
        val all = parseReleases(body, includePrerelease)
        return all.maxWithOrNull { a, b -> compareVersion(a.version, b.version) }
            ?.takeIf { compareVersion(it.version, current) > 0 }
    }

    fun isNewer(candidate: String, current: String): Boolean = compareVersion(candidate, current) > 0

    fun compareVersion(a: String, b: String): Int = compareVersions(a, b)

    fun compareVersions(a: String, b: String): Int {
        val (aCore, aPre) = splitVersion(a)
        val (bCore, bPre) = splitVersion(b)
        val len = maxOf(aCore.size, bCore.size)
        for (i in 0 until len) {
            val av = aCore.getOrElse(i) { 0 }
            val bv = bCore.getOrElse(i) { 0 }
            if (av != bv) return av.compareTo(bv)
        }
        return when {
            aPre == bPre -> 0
            aPre.isEmpty() -> 1
            bPre.isEmpty() -> -1
            else -> aPre.compareTo(bPre)
        }
    }

    private fun splitVersion(raw: String): Pair<List<Int>, String> {
        val clean = raw.trim().removePrefix("v").removePrefix("V")
        val dash = clean.indexOf('-')
        val core = if (dash >= 0) clean.substring(0, dash) else clean
        val pre = if (dash >= 0) clean.substring(dash + 1) else ""
        val parts = core.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        return parts to pre
    }

    suspend fun download(
        context: PlatformContext,
        info: UpdateInfo,
        onProgress: (DownloadProgress) -> Unit = {},
    ): PlatformFile? {
        val url = info.apkUrl ?: run {
            PlatformUpdater.openDownload(info)
            return null
        }
        return downloadApk(context, url, info.version, force = false) { _, current, total ->
            onProgress(DownloadProgress(current, total))
        }
    }

    suspend fun downloadApk(
        context: PlatformContext,
        url: String,
        version: String = "",
        force: Boolean = false,
        onProgress: (Float, Long, Long) -> Unit = { _, _, _ -> },
    ): PlatformFile = PlatformUpdater.downloadPackage(context, url, version, force, onProgress)

    fun installApk(context: PlatformContext, file: PlatformFile) {
        PlatformUpdater.installPackage(context, file)
    }

    fun openDownload(info: UpdateInfo) {
        PlatformUpdater.openDownload(info)
    }

    fun openDownload(@Suppress("UNUSED_PARAMETER") context: PlatformContext, info: UpdateInfo) {
        PlatformUpdater.openDownload(info)
    }

    fun install(context: PlatformContext, file: PlatformFile?) {
        if (file != null) PlatformUpdater.installPackage(context, file)
    }
}
