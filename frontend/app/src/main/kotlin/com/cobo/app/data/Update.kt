package com.cobo.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.cobo.app.App
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/** 自动检查的节流窗口：启动时距上次检查不到这个间隔就跳过，免得每次冷启动都打一次接口。 */
const val UpdateCheckInterval = 12 * 60 * 60 * 1000L

/**
 * 下载用的加速镜像前缀。GitHub 的 release 资产在部分网络上只有几十 KB/s，套一层代理能快一到
 * 两个数量级；正式下载前会并发探测，哪条快用哪条，全不通就退回直连。
 */
private val DownloadMirrors = listOf(
    "https://gh-proxy.com/",
    "https://ghfast.top/",
)

/** 并发分块数：跨境单连接会被限速，拆几路能叠加带宽，再多就开始互相抢。 */
private const val SegmentCount = 4

/** 单次读缓冲。16 KB 在 3 MB 的包上系统调用偏多，64 KB 是常见折中。 */
private const val DownloadBufferSize = 64 * 1024

/** 探测时每条线路最多读这么多，够分出快慢，也不会白费流量。 */
private const val ProbeBytes = 64 * 1024

/** 探测的总等待上限。所有线路都在这之内没读满，就退回直连。 */
private const val ProbeTimeoutMs = 1500L

/** 单段最多试几次，每次从已有长度续上。 */
private const val SegmentAttempts = 3

/** 一个比本地新的 release。 */
data class UpdateInfo(
    val version: String,
    val notes: String,
    val apkUrl: String?,
    val pageUrl: String,
    val prerelease: Boolean,
    /** 发布时间，GitHub 的 `published_at`；更新日志页拿它当日期。 */
    val publishedAt: String = "",
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
            parseLatest(fetchReleases(), current, includePrerelease)
        }

    /** 拉全部 release（新→旧），不和本地版本比，给更新日志页用。 */
    suspend fun releases(includePrerelease: Boolean = true): List<UpdateInfo> =
        withContext(Dispatchers.IO) {
            parseReleases(fetchReleases(), includePrerelease)
        }

    private fun fetchReleases(): String {
        val request = Request.Builder()
            .url(ReleasesUrl)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Cobo-Android")
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw ApiException("检查更新失败：HTTP ${response.code}")
            response.body?.string().orEmpty()
        }
    }

    /**
     * 解析 release 列表。[includePrerelease] 为 false 时跳过预发布，只留正式版：
     * GitHub 的 `prerelease` 标记和 `-pre` 后缀任一命中都算预发布。
     */
    internal fun parseLatest(body: String, current: String, includePrerelease: Boolean): UpdateInfo? =
        parseReleases(body, includePrerelease)
            .maxWithOrNull { a, b -> compareVersion(a.version, b.version) }
            ?.takeIf { compareVersion(it.version, current) > 0 }

    /** 解析 release 列表，保持 GitHub 返回的顺序（新的在前）。 */
    internal fun parseReleases(body: String, includePrerelease: Boolean): List<UpdateInfo> {
        // 限流或出错时 GitHub 回的是对象而不是数组，把它的 message 直接抛出去。
        val text = body.trim()
        if (!text.startsWith("[")) {
            val message = runCatching { JSONObject(text).str("message") }.getOrNull().orEmpty()
            throw ApiException(message.ifBlank { "检查更新失败：接口返回异常" })
        }
        val releases = JSONArray(text)
        val out = ArrayList<UpdateInfo>(releases.length())
        for (i in 0 until releases.length()) {
            val item = releases.optJSONObject(i) ?: continue
            if (item.optBoolean("draft", false)) continue
            val version = item.str("tag_name").removePrefix("v").removePrefix("V")
            if (version.isBlank()) continue
            val prerelease = item.optBoolean("prerelease", false) || version.contains('-')
            if (!includePrerelease && prerelease) continue
            out.add(
                UpdateInfo(
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
                    publishedAt = item.str("published_at"),
                )
            )
        }
        return out
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

    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    /** 探测用的短超时客户端：不通的线路要尽快放弃，别把准备阶段一起拖住。 */
    private val probeClient = downloadClient.newBuilder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    /**
     * 在应用内下载 APK 到本地缓存。
     *
     * GitHub 的 release 资产在部分网络上只有几十 KB/s，所以先并发探测几条线路（[DownloadMirrors]
     * 加直连）挑最快的一条；对端认 Range 就拆成 [SegmentCount] 路并发，各段写在自己的 `.partN`，
     * 中断后再下同一版本会从断点续传。
     *
     * [force] 为 true 时丢掉已有的安装包与分块重新下，给「预览更新弹窗」测真实速度用。
     *
     * [onProgress] 回调当前进度（0f..1f，无法获取总大小时为 -1f）、已下字节数与总字节数。
     */
    suspend fun downloadApk(
        context: Context,
        url: String,
        version: String,
        force: Boolean = false,
        onProgress: (progress: Float, currentBytes: Long, totalBytes: Long) -> Unit,
    ): File = downloadApk(File(context.cacheDir, "updates"), url, version, force, onProgress)

    /** 落盘目录由调用方给，测试就能拿临时目录把整条下载路径真跑一遍。 */
    internal suspend fun downloadApk(
        dir: File,
        url: String,
        version: String,
        force: Boolean = false,
        onProgress: (progress: Float, currentBytes: Long, totalBytes: Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val cleanVersion = version.trim().removePrefix("v").removePrefix("V")
        val targetFile = File(dir, "Cobo-$cleanVersion.apk")
        val parts = (0 until SegmentCount).map { File(dir, "Cobo-$cleanVersion.part$it") }

        // 清掉别的版本的残留（含上次没下完的分块）；本版本的分块要留着续传。
        dir.listFiles()?.forEach { file ->
            if (file != targetFile && !parts.contains(file)) file.delete()
        }

        if (force) {
            targetFile.delete()
            parts.forEach { it.delete() }
        }

        if (targetFile.exists() && targetFile.length() > 0) {
            onProgress(1f, targetFile.length(), targetFile.length())
            return@withContext targetFile
        }

        val source = pickFastestSource(url)
        val remote = inspect(source) ?: return@withContext singleStream(source, targetFile, onProgress)
        onProgress(0f, parts.sumOf { it.length() }.coerceAtMost(remote.total), remote.total)
        if (!remote.rangeSupported) {
            return@withContext singleStream(source, targetFile, onProgress)
        }
        try {
            downloadSegments(source, remote.total, parts, onProgress)
            mergeParts(parts, targetFile, remote.total)
        } catch (c: CancellationException) {
            // 用户取消：分块留着，下次接着下。
            throw c
        } catch (t: Throwable) {
            parts.forEach { it.delete() }
            throw t
        }
        parts.forEach { it.delete() }
        onProgress(1f, remote.total, remote.total)
        targetFile
    }

    /** 安装包的远端信息。 */
    private data class RemoteFile(val total: Long, val rangeSupported: Boolean)

    /** 一条线路的速度探测：读满 [ProbeBytes] 用了多久，折成字节/秒。 */
    private fun probeSpeed(candidate: String): Double {
        val request = Request.Builder()
            .url(candidate)
            .header("User-Agent", "Cobo-Android")
            .header("Range", "bytes=0-${ProbeBytes - 1}")
            .build()
        val started = System.nanoTime()
        val bytes = probeClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val stream = response.body?.byteStream() ?: throw IOException("响应内容为空")
            val buffer = ByteArray(8 * 1024)
            var read = 0
            while (read < ProbeBytes) {
                val n = stream.read(buffer, 0, minOf(buffer.size, ProbeBytes - read))
                if (n <= 0) break
                read += n
            }
            read
        }
        if (bytes <= 0) throw IOException("探测没有读到数据")
        return bytes / ((System.nanoTime() - started) / 1e9)
    }

    /**
     * 并发试每条线路：各读最多 [ProbeBytes]，先读完的那条就是最快的。[ProbeTimeoutMs] 内没人
     * 跑完说明都不通，退回直连，让正式下载自己去报错。
     */
    private suspend fun pickFastestSource(url: String): String {
        val candidates = DownloadMirrors.map { it + url } + url
        val results = Channel<Pair<String, Double>>(Channel.UNLIMITED)
        // 不能用 coroutineScope：阻塞的探测调用取消不掉，会把它一起等满超时。
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        candidates.forEach { candidate ->
            scope.launch {
                runCatching { probeSpeed(candidate) }
                    .getOrNull()
                    ?.let { speed -> runCatching { results.send(candidate to speed) } }
            }
        }
        val winner = withTimeoutOrNull(ProbeTimeoutMs) { results.receive() }?.first
        scope.cancel()
        results.close()
        return winner ?: url
    }

    /** 探一次远端：拿总长度，顺便看它认不认 Range。 */
    private fun inspect(source: String): RemoteFile? = runCatching {
        val request = Request.Builder()
            .url(source)
            .header("User-Agent", "Cobo-Android")
            .header("Range", "bytes=0-0")
            .build()
        probeClient.newCall(request).execute().use { response ->
            when {
                // 206 的 Content-Range 形如 `bytes 0-0/3159069`。
                response.code == 206 -> response.header("Content-Range")
                    ?.substringAfterLast('/')
                    ?.toLongOrNull()
                    ?.takeIf { it > 0 }
                    ?.let { RemoteFile(it, rangeSupported = true) }

                response.isSuccessful -> (response.body?.contentLength() ?: -1L)
                    .takeIf { it > 0 }
                    ?.let { RemoteFile(it, rangeSupported = false) }

                else -> null
            }
        }
    }.getOrNull()

    /**
     * 按 Range 切成 [SegmentCount] 段并发下，每段写进自己的 `.partN`。段内断线从已有长度续上，
     * 最多试 [SegmentAttempts] 次。
     */
    private suspend fun downloadSegments(
        source: String,
        total: Long,
        parts: List<File>,
        onProgress: (Float, Long, Long) -> Unit,
    ) = coroutineScope {
        val base = total / parts.size
        val ranges = parts.indices.map { index ->
            val start = index * base
            val end = if (index == parts.lastIndex) total - 1 else start + base - 1
            start to end
        }
        val done = LongArray(parts.size) { index ->
            parts[index].length().coerceIn(0, ranges[index].second - ranges[index].first + 1)
        }
        val lock = Any()
        var lastEmit = 0L

        fun emit(force: Boolean) {
            val now = System.currentTimeMillis()
            val sum = synchronized(lock) {
                if (!force && now - lastEmit < 100) return
                lastEmit = now
                var value = 0L
                for (each in done) value += each
                value
            }
            onProgress((sum.toDouble() / total).toFloat(), sum, total)
        }

        ranges.forEachIndexed { index, range ->
            launch(Dispatchers.IO) {
                val part = parts[index]
                val expected = range.second - range.first + 1
                var attempt = 0
                while (true) {
                    try {
                        downloadSegment(source, range, part, expected) { delta ->
                            synchronized(lock) { done[index] += delta }
                            emit(false)
                        }
                        return@launch
                    } catch (c: CancellationException) {
                        throw c
                    } catch (t: Throwable) {
                        coroutineContext.ensureActive()
                        if (++attempt >= SegmentAttempts) throw t
                        delay(300L * attempt)
                    }
                }
            }
        }
        emit(true)
    }

    /** 下一段：从 `.partN` 已有的长度接着要 Range，边读边写。 */
    private suspend fun downloadSegment(
        source: String,
        range: Pair<Long, Long>,
        part: File,
        expected: Long,
        onBytes: (Int) -> Unit,
    ) {
        val already = part.length().coerceAtMost(expected)
        if (already >= expected) return
        val request = Request.Builder()
            .url(source)
            .header("User-Agent", "Cobo-Android")
            .header("Range", "bytes=${range.first + already}-${range.second}")
            .build()
        downloadClient.newCall(request).execute().use { response ->
            if (response.code != 206) throw IOException("分块下载失败：HTTP ${response.code}")
            val body = response.body ?: throw IOException("分块下载失败：响应内容为空")
            FileOutputStream(part, true).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(DownloadBufferSize)
                    var remaining = expected - already
                    while (remaining > 0) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        remaining -= read
                        onBytes(read)
                    }
                    if (remaining > 0) throw IOException("分块提前结束，还差 $remaining 字节")
                }
            }
        }
    }

    /** 把各段按顺序拼成安装包；长度对不上说明有段没下全，清掉重来。 */
    private fun mergeParts(parts: List<File>, targetFile: File, total: Long) {
        val temp = File(targetFile.parentFile, targetFile.name + ".tmp")
        try {
            FileOutputStream(temp).use { output ->
                parts.forEach { part -> part.inputStream().use { it.copyTo(output, DownloadBufferSize) } }
            }
            if (temp.length() != total) throw IOException("下载不完整：${temp.length()} / $total 字节")
            if (targetFile.exists()) targetFile.delete()
            if (!temp.renameTo(targetFile)) {
                temp.copyTo(targetFile, overwrite = true)
                temp.delete()
            }
        } catch (t: Throwable) {
            temp.delete()
            parts.forEach { it.delete() }
            throw t
        }
    }

    /** 远端不认 Range 时的退路：一条流下完。 */
    private suspend fun singleStream(
        source: String,
        targetFile: File,
        onProgress: (Float, Long, Long) -> Unit,
    ): File {
        val temp = File(targetFile.parentFile, targetFile.name + ".tmp")
        val request = Request.Builder()
            .url(source)
            .header("User-Agent", "Cobo-Android")
            .build()
        downloadClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("下载失败：HTTP ${response.code}")
            val body = response.body ?: throw IOException("下载失败：响应内容为空")
            val total = body.contentLength()
            var downloaded = 0L
            var lastEmit = 0L
            try {
                FileOutputStream(temp).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(DownloadBufferSize)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            coroutineContext.ensureActive()
                            output.write(buffer, 0, read)
                            downloaded += read
                            val now = System.currentTimeMillis()
                            if (now - lastEmit > 100) {
                                lastEmit = now
                                val p = if (total > 0) downloaded.toFloat() / total else -1f
                                onProgress(p, downloaded, total)
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                temp.delete()
                throw t
            }
            if (targetFile.exists()) targetFile.delete()
            if (!temp.renameTo(targetFile)) {
                temp.copyTo(targetFile, overwrite = true)
                temp.delete()
            }
            onProgress(1f, downloaded, if (total > 0) total else downloaded)
        }
        return targetFile
    }

    /** 调起系统安装器发起 APK 安装请求。 */
    fun installApk(context: Context, file: File) {
        if (!file.exists()) {
            App.toast("安装包不存在")
            return
        }
        runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure { t ->
            App.toast("调起安装失败：${t.message}")
        }
    }
}
