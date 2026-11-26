package com.cobo.app.data

import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.cobo.app.App
import com.cobo.app.platform.AndroidPlatformHolder
import com.cobo.app.platform.PlatformContext
import com.cobo.app.platform.PlatformFile
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
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

private val DownloadMirrors = listOf(
    "https://gh-proxy.com/",
    "https://ghfast.top/",
)
private const val SegmentCount = 4
private const val DownloadBufferSize = 64 * 1024
private const val ProbeBytes = 64 * 1024
private const val ProbeTimeoutMs = 1500L
private const val SegmentAttempts = 3

internal actual object PlatformUpdater {
    actual val userAgent: String = "Cobo-Android"
    actual val preferIpaAsset: Boolean = false

    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    private val probeClient = downloadClient.newBuilder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    actual fun openDownload(info: UpdateInfo) {
        val url = info.apkUrl ?: info.pageUrl
        if (url.isBlank()) return
        val ctx = AndroidPlatformHolder.currentActivity() ?: AndroidPlatformHolder.appContext ?: return
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                if (ctx !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
        }.onFailure { App.toast("没有可用的浏览器") }
    }

    actual suspend fun downloadPackage(
        context: PlatformContext,
        url: String,
        version: String,
        force: Boolean,
        onProgress: (progress: Float, currentBytes: Long, totalBytes: Long) -> Unit,
    ): PlatformFile {
        val cacheDir = context.androidContext?.cacheDir ?: File(com.cobo.app.platform.appCacheDir().path)
        val file = downloadApkToDir(File(cacheDir, "updates"), url, version, force, onProgress)
        return PlatformFile(file.path)
    }

    internal suspend fun downloadApkToDir(
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
            throw c
        } catch (t: Throwable) {
            parts.forEach { it.delete() }
            throw t
        }
        parts.forEach { it.delete() }
        onProgress(1f, remote.total, remote.total)
        targetFile
    }

    actual fun installPackage(context: PlatformContext, file: PlatformFile) {
        val ctx = context.androidContext ?: AndroidPlatformHolder.appContext ?: return
        val realFile = File(file.path)
        if (!realFile.exists()) {
            App.toast("安装包不存在")
            return
        }
        runCatching {
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", realFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
        }.onFailure { t ->
            App.toast("调起安装失败：${t.message}")
        }
    }

    private data class RemoteFile(val total: Long, val rangeSupported: Boolean)

    private fun probeSpeed(candidate: String): Double {
        val request = Request.Builder()
            .url(candidate)
            .header("User-Agent", userAgent)
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

    private suspend fun pickFastestSource(url: String): String {
        val candidates = DownloadMirrors.map { it + url } + url
        val results = Channel<Pair<String, Double>>(Channel.UNLIMITED)
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

    private fun inspect(source: String): RemoteFile? = runCatching {
        val request = Request.Builder()
            .url(source)
            .header("User-Agent", userAgent)
            .header("Range", "bytes=0-0")
            .build()
        probeClient.newCall(request).execute().use { response ->
            when {
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
            .header("User-Agent", userAgent)
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

    private suspend fun singleStream(
        source: String,
        targetFile: File,
        onProgress: (Float, Long, Long) -> Unit,
    ): File {
        val temp = File(targetFile.parentFile, targetFile.name + ".tmp")
        val request = Request.Builder()
            .url(source)
            .header("User-Agent", userAgent)
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
}
