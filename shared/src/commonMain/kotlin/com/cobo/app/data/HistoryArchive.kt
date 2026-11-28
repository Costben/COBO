package com.cobo.app.data

import com.cobo.app.App
import com.cobo.app.json.JSONArray
import com.cobo.app.json.JSONObject
import com.cobo.app.platform.PlatformFile
import com.cobo.app.platform.PlatformHttp
import com.cobo.app.platform.SynchronizedMap
import com.cobo.app.platform.appFilesDir
import com.cobo.app.platform.currentTimeMillis
import com.cobo.app.platform.ioDispatcher
import com.cobo.app.platform.parseUrlHost
import com.cobo.app.platform.sha256Hex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** 浏览历史最多保存最近 100 条看过的微博。 */
internal const val MaxHistoryItems = 100

/** 浏览历史持久化图片存放子目录（位于 appFilesDir 下，不随清理缓存丢失，仅随历史淘汰/删除清理）。 */
internal const val HistoryImagesDirName = "history-images"

/** 服务端明确返回微博已被删除或不存在时抛出的异常。 */
class StatusDeletedException(
    message: String = "该微博已被删除",
    errno: String? = null,
) : ApiException(message, errno)

private val DeletedStatusErrnos = setOf("20101", "20130")

private val DeletedStatusKeywords = listOf(
    "已被作者删除",
    "此微博已被删除",
    "该微博已被删除",
    "微博已删除",
    "已被删除",
    "已删除",
    "微博不存在",
    "target weibo does not exist",
    "weibo does not exist",
    "status does not exist",
)

private val NonDeletedErrorKeywords = listOf(
    "timeout",
    "timed out",
    "unable to resolve host",
    "failed to connect",
    "connection",
    "network",
    "登录",
    "cookie",
    "token",
    "频繁",
    "限流",
)

internal fun isDeletedStatusMessage(message: String?): Boolean {
    val raw = message?.trim().orEmpty()
    if (raw.isEmpty()) return false
    val lower = raw.lowercase()
    return DeletedStatusKeywords.any { lower.contains(it) }
}

/**
 * 判断微博详情接口返回的 JSON 是否表示该微博已被删除。
 * 仅对显式 `deleted` 标志、已删除错误码或明确删除文案返回 true，避免误判网络/鉴权异常。
 */
internal fun isDeletedStatusJson(o: JSONObject?): Boolean {
    if (o == null) return false
    val deletedRaw = o.opt("deleted")
    if (deletedRaw == true || deletedRaw?.toString() == "1") return true

    val errno = ClientLogin.errorCode(o)
    if (errno != null && errno in DeletedStatusErrnos) return true

    val msg = o.str("msg").ifBlank { o.str("errmsg") }.ifBlank { o.str("error") }
    if (o.has("ok") && o.optInt("ok", 1) == 0 && isDeletedStatusMessage(msg)) return true
    if (errno != null && isDeletedStatusMessage(msg)) return true

    val hasUser = o.optJSONObject("user") != null
    if (!hasUser) {
        val plain = stripHtml(o.str("text"))
        if (plain.isNotBlank() && isDeletedStatusMessage(plain)) return true
    }
    return false
}

internal fun deletedStatusReasonOf(o: JSONObject?): String {
    if (o == null) return "该微博已被删除"
    val msg = o.str("msg").ifBlank { o.str("errmsg") }.ifBlank { o.str("error") }
    if (isDeletedStatusMessage(msg)) return msg
    val plain = stripHtml(o.str("text"))
    if (isDeletedStatusMessage(plain)) return plain.take(80)
    return "该微博已被删除"
}

internal fun isDeletedStatusThrowable(t: Throwable?): Boolean {
    if (t == null) return false
    if (t is StatusDeletedException) return true
    if (t is ApiException) {
        if (t.errno != null && t.errno in DeletedStatusErrnos) return true
        if (isDeletedStatusMessage(t.message)) return true
        return false
    }
    if (t is ApiHttpException) {
        if (t.code == 404) return true
        if (t.code in 400..499) {
            val bodyJson = runCatching { JSONObject(t.body) }.getOrNull()
            if (isDeletedStatusJson(bodyJson)) return true
            if (isDeletedStatusMessage(t.body)) return true
            if (t.body.contains("20101")) return true
        }
        return false
    }
    val msg = t.message.orEmpty()
    val lower = msg.lowercase()
    if (NonDeletedErrorKeywords.any { lower.contains(it) }) return false
    return isDeletedStatusMessage(msg)
}

/**
 * 裁剪微博为历史快照：仅保留文字、作者基础信息与静态图片；
 * 严格剔除视频、混排内嵌视频、实况图视频轨、转发原博、外链卡片、投票以及评论/转发/点赞计数。
 */
internal fun sanitizeStatusForHistory(status: Status): Status {
    val cleanUser = status.user?.let { u ->
        User(
            id = u.id,
            name = u.name,
            avatar = u.avatar,
            verified = u.verified,
            verifiedType = u.verifiedType,
            mbtype = u.mbtype,
            mbrank = u.mbrank,
            svip = u.svip,
            vvip = u.vvip,
        )
    }
    val cleanPics = status.pics.mapNotNull { pic ->
        if (pic.isVideo) return@mapNotNull null
        val thumb = pic.thumb.trim()
        val large = pic.large.trim().ifBlank { thumb }
        if (thumb.isBlank() && large.isBlank()) return@mapNotNull null
        Pic(
            thumb = thumb.ifBlank { large },
            large = large,
            width = pic.width,
            height = pic.height,
            live = "",
            videoUrl = "",
            videoTitle = "",
            durationSec = 0,
            preserveUrl = pic.preserveUrl,
            pid = pic.resolvedPicId(),
            videoFid = "",
            videoStatusId = "",
        )
    }
    return Status(
        id = status.id,
        mid = status.mid.ifBlank { status.id },
        textHtml = status.textHtml,
        createdAt = status.createdAt,
        source = status.source,
        user = cleanUser,
        pics = cleanPics,
        video = null,
        bigPic = null,
        retweeted = null,
        reposts = 0,
        comments = 0,
        attitudes = 0,
        liked = false,
        favorited = false,
        region = status.region,
        editCount = 0,
        isVote = false,
        voteId = null,
        visibility = status.visibility,
        isLongText = false,
        foldedTextHtml = null,
    )
}

private fun historyImageExtension(url: String): String {
    val path = url.substringBefore('?').substringBefore('#').lowercase()
    return when {
        path.endsWith(".png") -> "png"
        path.endsWith(".gif") -> "gif"
        path.endsWith(".webp") -> "webp"
        path.endsWith(".heic") -> "heic"
        else -> "jpg"
    }
}

/** 为单张历史快照图片生成稳定的本地文件名，相同图片跨条目自动去重复用。 */
internal fun historyImageFileName(pic: Pic): String {
    val sourceUrl = pic.large.ifBlank { pic.thumb }
    val canonicalKey = pic.resolvedPicId().ifBlank { sourceUrl.substringBefore('?').substringBefore('#') }
    val ext = historyImageExtension(sourceUrl)
    return "${sha256Hex(canonicalKey)}.$ext"
}

/** 从完整微博构造历史记录条目（含裁剪后的仅文字+图片快照与确定性本地图片文件名表）。 */
internal fun buildHistoryItem(
    status: Status,
    nowMs: Long = currentTimeMillis(),
    deleted: Boolean = false,
): HistoryItem {
    val sanitized = sanitizeStatusForHistory(status)
    val localImages = sanitized.pics.map(::historyImageFileName)
    return HistoryItem(
        id = sanitized.id,
        mid = sanitized.mid,
        text = sanitized.plainText.take(120),
        user = sanitized.user?.name.orEmpty(),
        time = nowMs,
        html = sanitized.textHtml.take(1000),
        snapshot = sanitized,
        localImages = localImages,
        deleted = deleted,
    )
}

/**
 * 将历史记录解析为可直接渲染的 [Status]：若本地持久化图片存在则优先替换为本地文件路径
 * 并标记 `preserveUrl = true`，否则平滑回退到快照里的远端图片地址。
 */
internal fun resolveHistoryStatus(
    item: HistoryItem,
    imagesDir: PlatformFile = appFilesDir().child(HistoryImagesDirName),
): Status? {
    val snap = item.snapshot ?: return null
    if (snap.pics.isEmpty()) return snap
    val resolvedPics = snap.pics.mapIndexed { idx, pic ->
        val fileName = item.localImages.getOrNull(idx)?.takeIf { it.isNotBlank() }
            ?: historyImageFileName(pic)
        val localFile = imagesDir.child(fileName)
        if (localFile.exists() && localFile.length() > 0L) {
            val localPath = localFile.path
            pic.copy(
                thumb = localPath,
                large = localPath,
                live = "",
                videoUrl = "",
                preserveUrl = true,
            )
        } else {
            pic
        }
    }
    return snap.copy(pics = resolvedPics)
}

/** 旧版本历史记录没有完整 snapshot 时，用留存的作者、正文 HTML/纯文本兜底生成可阅读的快照。 */
internal fun resolveHistoryFallbackStatus(
    item: HistoryItem,
    imagesDir: PlatformFile = appFilesDir().child(HistoryImagesDirName),
): Status {
    resolveHistoryStatus(item, imagesDir)?.let { return it }
    val fallbackHtml = item.html.ifBlank { item.text }
    return Status(
        id = item.id,
        mid = item.mid.ifBlank { item.id },
        textHtml = fallbackHtml,
        createdAt = "",
        source = "",
        user = item.user.takeIf { it.isNotBlank() }?.let { name ->
            User(id = "", name = name, avatar = "")
        },
        pics = emptyList(),
    )
}

internal fun encodeHistoryItemJson(
    item: HistoryItem,
    includeLocalImages: Boolean = true,
): JSONObject = JSONObject().apply {
    put("id", item.id)
    put("mid", item.mid)
    put("text", item.text)
    put("user", item.user)
    put("html", item.html)
    put("time", item.time)
    if (item.deleted) put("deleted", true)
    item.snapshot?.let { snap ->
        put("snapshot", statusToSnapshot(sanitizeStatusForHistory(snap)))
    }
    if (includeLocalImages && item.localImages.isNotEmpty()) {
        put("localImages", JSONArray().apply { item.localImages.forEach { put(it) } })
    }
}

internal fun decodeHistoryItemJson(o: JSONObject?): HistoryItem? {
    if (o == null) return null
    val id = o.str("id")
    if (id.isBlank()) return null
    val mid = o.str("mid").ifBlank { id }
    val rawSnapshot = o.optJSONObject("snapshot")?.let(::statusFromSnapshot)?.let(::sanitizeStatusForHistory)
    val localImages = o.optJSONArray("localImages")?.let { arr ->
        buildList<String>(arr.length()) {
            for (i in 0 until arr.length()) {
                val name = arr.optString(i).orEmpty().trim()
                if (name.isNotEmpty() && !name.contains('/') && !name.contains("..")) add(name)
            }
        }
    }.orEmpty().ifEmpty {
        rawSnapshot?.pics?.map(::historyImageFileName).orEmpty()
    }
    return HistoryItem(
        id = id,
        mid = mid,
        text = o.str("text"),
        user = o.str("user"),
        time = o.optLong("time", 0L),
        html = o.str("html"),
        snapshot = rawSnapshot,
        localImages = localImages,
        deleted = o.optBoolean("deleted", false),
    )
}

/**
 * 浏览历史图片本地持久化调度器：在后台将看过微博的配图写入 `appFilesDir()/history-images/`，
 * 并在历史淘汰超过 100 条、单条删除或清空时同步回收孤立图片文件。
 */
object HistoryImageArchiver {
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val downloadSemaphore = Semaphore(3)
    private val inFlightFiles = SynchronizedMap<String, Boolean>()

    fun scheduleDownload(
        item: HistoryItem,
        imagesDir: PlatformFile = App.store.historyImagesDir(),
        isFileRetained: (String) -> Boolean = { fileName -> App.store.isHistoryImageReferenced(fileName) },
    ) {
        val snapshot = item.snapshot ?: return
        if (snapshot.pics.isEmpty()) return
        imagesDir.mkdirs()
        snapshot.pics.forEachIndexed { idx, pic ->
            val fileName = item.localImages.getOrNull(idx)?.takeIf { it.isNotBlank() }
                ?: historyImageFileName(pic)
            val target = imagesDir.child(fileName)
            if (target.exists() && target.length() > 0L) return@forEachIndexed
            if (inFlightFiles.putIfAbsent(fileName, true) != null) return@forEachIndexed
            scope.launch {
                try {
                    downloadSemaphore.withPermit {
                        if (!isFileRetained(fileName)) return@withPermit
                        if (target.exists() && target.length() > 0L) return@withPermit
                        val bytes = downloadPicBytes(pic) ?: return@withPermit
                        if (bytes.isEmpty() || !isFileRetained(fileName)) return@withPermit
                        target.writeBytes(bytes)
                        if (!isFileRetained(fileName)) {
                            runCatching { target.delete() }
                        }
                    }
                } finally {
                    inFlightFiles.remove(fileName)
                }
            }
        }
    }

    internal fun pruneOrphanFiles(
        imagesDir: PlatformFile,
        retainedItems: List<HistoryItem>,
    ) {
        if (!imagesDir.exists()) return
        val keep = HashSet<String>()
        for (item in retainedItems) {
            item.localImages.forEach { if (it.isNotBlank()) keep.add(it) }
            item.snapshot?.pics?.forEach { pic -> keep.add(historyImageFileName(pic)) }
        }
        runCatching {
            imagesDir.listFiles().forEach { file ->
                val name = file.name
                if (name !in keep && !inFlightFiles.containsKey(name)) {
                    file.delete()
                }
            }
        }
    }

    internal fun clearAllFiles(imagesDir: PlatformFile) {
        if (!imagesDir.exists()) return
        runCatching {
            imagesDir.listFiles().forEach { file -> file.delete() }
        }
    }

    private suspend fun downloadPicBytes(pic: Pic): ByteArray? {
        val candidates = listOf(pic.large, pic.thumb)
            .map { it.trim() }
            .filter { it.startsWith("http://", true) || it.startsWith("https://", true) }
            .distinct()
        for (url in candidates) {
            val host = parseUrlHost(url)
            val headers = App.imageHeadersForHost(host)
            val bytes = PlatformHttp.downloadBytes(url, headers)
            if (bytes != null && bytes.isNotEmpty()) return bytes
        }
        return null
    }
}
