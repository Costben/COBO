package com.cobo.app.data

import com.cobo.app.json.JSONArray
import com.cobo.app.json.JSONObject
import com.cobo.app.platform.PlatformContext
import com.cobo.app.platform.PlatformFile
import com.cobo.app.platform.PlatformHttp
import com.cobo.app.platform.PlatformPrefs
import com.cobo.app.platform.currentAppVersionName
import com.cobo.app.platform.ioDispatcher
import com.cobo.app.platform.openExternalUrl
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
        val cachedBody = sp.getString(CacheBodyKey, null)
        val cachedEtag = sp.getString(CacheEtagKey, null)
        val headers = linkedMapOf(
            "Accept" to "application/vnd.github+json",
            "User-Agent" to "Cobo-iOS",
        )
        if (!cachedEtag.isNullOrBlank() && !cachedBody.isNullOrBlank()) {
            headers["If-None-Match"] = cachedEtag
        }
        val resp = runCatching { PlatformHttp.get(ReleasesUrl, headers = headers) }.getOrNull()
        if (resp == null) {
            if (!cachedBody.isNullOrBlank()) return Fetched(cachedBody, stale = true)
            throw ApiException("检查更新失败，请检查网络")
        }
        if (resp.code == 304 && !cachedBody.isNullOrBlank()) {
            return Fetched(cachedBody, stale = false)
        }
        if (resp.isSuccessful && resp.bodyText.isNotBlank()) {
            sp.edit().apply {
                putString(CacheBodyKey, resp.bodyText)
                resp.header("ETag")?.let { putString(CacheEtagKey, it) }
            }.apply()
            return Fetched(resp.bodyText, stale = false)
        }
        if (!cachedBody.isNullOrBlank()) return Fetched(cachedBody, stale = true)
        throw ApiException("检查更新失败（HTTP ${resp.code}）")
    }

    fun parseReleases(body: String, includePrerelease: Boolean = true): List<UpdateInfo> {
        val array = runCatching { JSONArray(body) }.getOrNull() ?: return emptyList()
        val out = ArrayList<UpdateInfo>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            if (obj.optBoolean("draft", false)) continue
            val prerelease = obj.optBoolean("prerelease", false)
            if (!includePrerelease && prerelease) continue
            val tag = obj.optString("tag_name").trim()
            val version = tag.removePrefix("v").removePrefix("V")
            if (version.isBlank()) continue
            val pageUrl = obj.optString("html_url").trim()
            val notes = obj.optString("body").trim()
            val publishedAt = obj.optString("published_at").trim()
            val assets = obj.optJSONArray("assets")
            var ipaOrApkUrl: String? = null
            if (assets != null) {
                for (j in 0 until assets.length()) {
                    val asset = assets.optJSONObject(j) ?: continue
                    val name = asset.optString("name").lowercase()
                    val dl = asset.optString("browser_download_url").trim()
                    if (name.endsWith(".ipa") && dl.isNotBlank()) {
                        ipaOrApkUrl = dl
                        break
                    }
                    if (ipaOrApkUrl == null && name.endsWith(".apk") && dl.isNotBlank()) {
                        ipaOrApkUrl = dl
                    }
                }
            }
            out.add(
                UpdateInfo(
                    version = version,
                    notes = notes,
                    apkUrl = ipaOrApkUrl,
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
        return all.filter { isNewer(it.version, current) }
            .maxWithOrNull { a, b -> compareVersions(a.version, b.version) }
    }

    fun isNewer(candidate: String, current: String): Boolean = compareVersions(candidate, current) > 0

    fun compareVersions(a: String, b: String): Int {
        val (aCore, aPre) = splitVersion(a)
        val (bCore, bPre) = splitVersion(b)
        val len = maxOf(aCore.size, bCore.size)
        for (i in 0 until len) {
            val av = aCore.getOrElse(i) { 0 }
            val bv = bCore.getOrElse(i) { 0 }
            if (av != bv) return av.compareTo(bv)
        }
        if (aPre.isEmpty() && bPre.isNotEmpty()) return 1
        if (aPre.isNotEmpty() && bPre.isEmpty()) return -1
        return aPre.compareTo(bPre)
    }

    private fun splitVersion(raw: String): Pair<List<Int>, String> {
        val clean = raw.trim().removePrefix("v").removePrefix("V")
        val dash = clean.indexOf('-')
        val core = if (dash >= 0) clean.substring(0, dash) else clean
        val pre = if (dash >= 0) clean.substring(dash + 1) else ""
        val parts = core.split('.').map { it.toIntOrNull() ?: 0 }
        return parts to pre
    }

    /** iOS 端不支持应用内静默安装 APK，直接跳转 GitHub Release 页面。 */
    suspend fun download(
        @Suppress("UNUSED_PARAMETER") context: PlatformContext,
        info: UpdateInfo,
        @Suppress("UNUSED_PARAMETER") onProgress: (DownloadProgress) -> Unit = {},
    ): PlatformFile? {
        openExternalUrl(info.pageUrl.ifBlank { "https://github.com/Costben/COBO/releases" })
        return null
    }

    suspend fun downloadApk(
        @Suppress("UNUSED_PARAMETER") context: PlatformContext,
        url: String,
        @Suppress("UNUSED_PARAMETER") version: String = "",
        @Suppress("UNUSED_PARAMETER") force: Boolean = false,
        @Suppress("UNUSED_PARAMETER") onProgress: (Float, Long, Long) -> Unit = { _, _, _ -> },
    ): PlatformFile {
        openExternalUrl(url.ifBlank { "https://github.com/Costben/COBO/releases" })
        throw ApiException("iOS 请前往 GitHub Releases 获取 IPA 安装")
    }

    fun installApk(@Suppress("UNUSED_PARAMETER") context: PlatformContext, @Suppress("UNUSED_PARAMETER") file: PlatformFile) {
        openExternalUrl("https://github.com/Costben/COBO/releases")
    }

    fun openDownload(info: UpdateInfo) {
        openExternalUrl(info.pageUrl.ifBlank { "https://github.com/Costben/COBO/releases" })
    }

    fun openDownload(@Suppress("UNUSED_PARAMETER") context: PlatformContext, info: UpdateInfo) {
        openExternalUrl(info.pageUrl.ifBlank { "https://github.com/Costben/COBO/releases" })
    }

    fun install(@Suppress("UNUSED_PARAMETER") context: PlatformContext, @Suppress("UNUSED_PARAMETER") file: PlatformFile?) {
        openExternalUrl("https://github.com/Costben/COBO/releases")
    }
}
