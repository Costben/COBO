package com.cobo.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.cobo.app.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** 自动检查的节流窗口：启动时距上次检查不到这个间隔就跳过，免得每次冷启动都打一次接口。 */
const val UpdateCheckInterval = 12 * 60 * 60 * 1000L

/** 一个比本地新的 release。 */
data class UpdateInfo(
    val version: String,
    val notes: String,
    val apkUrl: String?,
    val pageUrl: String,
    val prerelease: Boolean,
)

/**
 * 更新检查。发版走 GitHub Releases，这里只认 release 列表：`/releases/latest` 会跳过
 * pre-release，而本项目的日常版本全是 `-pre`，所以自己拉列表按版本号挑最大的一个。
 */
object Updater {
    private const val ReleasesUrl = "https://api.github.com/repos/Costben/COBO/releases?per_page=20"

    // 更新检查和微博是两套身份：绝不能把微博 Cookie / UA 带给 GitHub。
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** 本地版本号；`BuildConfig` 没开，直接问 PackageManager。 */
    fun currentVersion(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: "0.0.0"

    /** 拉 release 列表，返回比 [current] 新的最高版本；已经是最新时返回 null。 */
    suspend fun latest(current: String, includePrerelease: Boolean = true): UpdateInfo? =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(ReleasesUrl)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Cobo-Android")
                .build()
            val body = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw ApiException("检查更新失败：HTTP ${response.code}")
                response.body?.string().orEmpty()
            }
            parseLatest(body, current, includePrerelease)
        }

    /**
     * 解析 release 列表。[includePrerelease] 为 false 时跳过预发布，只留正式版：
     * GitHub 的 `prerelease` 标记和 `-pre` 后缀任一命中都算预发布。
     */
    internal fun parseLatest(body: String, current: String, includePrerelease: Boolean): UpdateInfo? {
        // 限流或出错时 GitHub 回的是对象而不是数组，把它的 message 直接抛出去。
        val text = body.trim()
        if (!text.startsWith("[")) {
            val message = runCatching { JSONObject(text).str("message") }.getOrNull().orEmpty()
            throw ApiException(message.ifBlank { "检查更新失败：接口返回异常" })
        }
        val releases = JSONArray(text)
        var best: UpdateInfo? = null
        for (i in 0 until releases.length()) {
            val item = releases.optJSONObject(i) ?: continue
            if (item.optBoolean("draft", false)) continue
            val version = item.str("tag_name").removePrefix("v").removePrefix("V")
            if (version.isBlank()) continue
            val prerelease = item.optBoolean("prerelease", false) || version.contains('-')
            if (!includePrerelease && prerelease) continue
            val info = UpdateInfo(
                version = version,
                notes = item.str("body").trim(),
                apkUrl = item.optJSONArray("assets")
                    ?.let { assets ->
                        (0 until assets.length())
                            .mapNotNull { assets.optJSONObject(it) }
                            .firstOrNull { it.str("name").endsWith(".apk", ignoreCase = true) }
                            ?.str("browser_download_url")
                    }
                    ?.takeIf { it.isNotBlank() },
                pageUrl = item.str("html_url"),
                prerelease = prerelease,
            )
            if (best == null || compareVersion(info.version, best.version) > 0) best = info
        }
        return best?.takeIf { compareVersion(it.version, current) > 0 }
    }

    /**
     * 版本号比较，返回正数表示 [a] 更新。按段比数字，`1.0.10 > 1.0.9`；
     * 同号时正式版大于预发布版（`1.0.0 > 1.0.0-pre`），预发布之间按后缀字典序。
     */
    fun compareVersion(a: String, b: String): Int {
        val (aNumbers, aSuffix) = splitVersion(a)
        val (bNumbers, bSuffix) = splitVersion(b)
        for (i in 0 until maxOf(aNumbers.size, bNumbers.size)) {
            val left = aNumbers.getOrElse(i) { 0 }
            val right = bNumbers.getOrElse(i) { 0 }
            if (left != right) return left.compareTo(right)
        }
        return when {
            aSuffix == bSuffix -> 0
            aSuffix.isEmpty() -> 1
            bSuffix.isEmpty() -> -1
            else -> aSuffix.compareTo(bSuffix)
        }
    }

    private fun splitVersion(version: String): Pair<List<Int>, String> {
        val core = version.trim().removePrefix("v").removePrefix("V")
        val dash = core.indexOf('-')
        val numbers = (if (dash >= 0) core.substring(0, dash) else core)
            .split('.')
            .map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val suffix = if (dash >= 0) core.substring(dash + 1) else ""
        return numbers to suffix
    }

    /** 交给系统浏览器下载 APK：下载完点一下就能装，比在应用内做下载器省事。 */
    fun openDownload(info: UpdateInfo) {
        val url = info.apkUrl ?: info.pageUrl
        if (url.isBlank()) return
        runCatching {
            App.context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure { App.toast("没有可用的浏览器") }
    }
}
