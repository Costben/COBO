package com.cobo.app.data

import android.content.Context
import android.net.Uri
import com.cobo.app.ui.isWeiboHomeLink
import com.cobo.app.ui.isWeiboLiveLink
import com.cobo.app.ui.isWeiboShortLink
import com.cobo.app.ui.resolveWeiboLink
import com.cobo.app.ui.unwrapWeiboLink
import com.cobo.app.ui.weiboStatusId
import com.cobo.app.ui.weiboTopicQuery
import com.cobo.app.ui.weiboUserId
import com.cobo.app.ui.weiboVoteId
import java.io.File

enum class WeiboLinkKind(val label: String) {
    Status("微博正文"),
    User("微博用户"),
    Topic("微博话题"),
    SuperTopic("微博超话"),
    Video("微博视频"),
    Live("微博直播"),
    Vote("微博投票"),
    ShortLink("微博短链"),
}

data class DetectedWeiboLink(
    val rawUrl: String,
    val resolvedUrl: String,
    val kind: WeiboLinkKind,
    val displayText: String,
)

data class IncomingShare(
    val text: String = "",
    val imageUris: List<String> = emptyList(),
)

sealed interface ShareAction {
    data class OpenWeiboLink(val link: DetectedWeiboLink) : ShareAction
    data class AskWeiboLinkOrCompose(val link: DetectedWeiboLink, val text: String) : ShareAction
    data class Compose(val text: String, val imageUris: List<String>) : ShareAction
    data object Empty : ShareAction
}

private val SchemeUrlRegex = Regex(
    """(?:https?://|sinaweibo://|weibo://)[^\s<>"'\]）】》〉」』，。！？；：、]+""",
    RegexOption.IGNORE_CASE,
)

private val BareWeiboHostRegex = Regex(
    """(?:^|[\s(（【《「『])((?:t\.cn|(?:www\.|m\.)?weibo\.(?:com|cn))/[^\s<>"'\]）】》〉」』，。！？；：、]+)""",
    RegexOption.IGNORE_CASE,
)

private val TrailingUrlPunctuation = charArrayOf(
    '.', ',', ';', ':', '!', '?', ')', ']', '}', '"', '\'',
    '）', '］', '】', '》', '〉', '」', '』', '，', '。', '！', '？', '；', '：', '、',
)

private const val MaxSharedMediaAgeMs = 24L * 60L * 60L * 1000L

/**
 * 合并系统分享中的标题（`EXTRA_SUBJECT`）与正文（`EXTRA_TEXT`），避免重复拼接相同内容。
 */
fun combineSharedText(subject: String?, text: String?): String {
    val cleanSubject = subject?.trim().orEmpty()
    val cleanText = text?.trim().orEmpty()
    return when {
        cleanSubject.isEmpty() -> cleanText
        cleanText.isEmpty() -> cleanSubject
        cleanText.contains(cleanSubject, ignoreCase = true) -> cleanText
        else -> "$cleanSubject\n$cleanText"
    }
}

private fun isWeiboVideoHostUrl(unwrapped: String): Boolean {
    val lower = unwrapped.lowercase()
    if (lower.startsWith("sinaweibo://video") ||
        lower.startsWith("weibo://video") ||
        lower.startsWith("intent://gotovideo")
    ) {
        return true
    }
    val uri = runCatching { java.net.URI(unwrapped) }.getOrNull() ?: return false
    val host = uri.host?.lowercase().orEmpty()
    val path = uri.rawPath.orEmpty().lowercase()
    return when {
        host == "video.weibo.com" || host == "h5.video.weibo.com" -> true
        (host == "weibo.com" || host == "www.weibo.com") && path.startsWith("/tv/") -> true
        host == "m.weibo.cn" && (path.startsWith("/s/video/") || path.startsWith("/z/video/")) -> true
        else -> false
    }
}

private fun formatLinkPreview(rawUrl: String): String {
    val clean = rawUrl.trim()
        .removePrefix("https://")
        .removePrefix("http://")
    return if (clean.length <= 48) clean else clean.take(47) + "…"
}

private fun classifyWeiboCandidate(candidate: String): DetectedWeiboLink? {
    val cleaned = candidate.trim().trimEnd(*TrailingUrlPunctuation)
    if (cleaned.isEmpty()) return null
    val unwrapped = unwrapWeiboLink(cleaned)
    if (unwrapped.isBlank() || isWeiboHomeLink(unwrapped)) return null

    val kind = when {
        superTopicId(unwrapped) != null -> WeiboLinkKind.SuperTopic
        weiboTopicQuery(unwrapped) != null -> WeiboLinkKind.Topic
        weiboVoteId(unwrapped) != null -> WeiboLinkKind.Vote
        isWeiboLiveLink(unwrapped) -> WeiboLinkKind.Live
        isWeiboVideoHostUrl(unwrapped) -> WeiboLinkKind.Video
        weiboStatusId(unwrapped) != null -> WeiboLinkKind.Status
        weiboUserId(unwrapped) != null -> WeiboLinkKind.User
        isWeiboShortLink(unwrapped) -> WeiboLinkKind.ShortLink
        else -> null
    } ?: return null

    val resolved = resolveWeiboLink(unwrapped) ?: unwrapped
    return DetectedWeiboLink(
        rawUrl = cleaned,
        resolvedUrl = resolved,
        kind = kind,
        displayText = formatLinkPreview(cleaned),
    )
}

/**
 * 从任意文本或分享文案中识别第一条可直达的微博链接；纯首页或第三方普通网址返回 `null`。
 */
fun extractWeiboLinkFromText(rawText: String?): DetectedWeiboLink? {
    val text = rawText?.trim().orEmpty()
    if (text.isEmpty()) return null

    for (match in SchemeUrlRegex.findAll(text)) {
        classifyWeiboCandidate(match.value)?.let { return it }
    }
    for (match in BareWeiboHostRegex.findAll(text)) {
        val bare = match.groupValues.getOrNull(1).orEmpty()
        if (bare.isNotEmpty()) {
            classifyWeiboCandidate("https://$bare")?.let { return it }
        }
    }
    return null
}

/**
 * 决策系统分享的落点：
 * - 带图片一律进入发微博并预填充图文；
 * - 纯文字包含微博链接时，纯链接或访客模式直接打开链接，混合文案则弹窗询问「打开链接 / 作为发微博草稿」；
 * - 普通文本直接进入发微博草稿。
 */
fun decideShareAction(share: IncomingShare, isGuest: Boolean): ShareAction {
    val cleanText = share.text.trim()
    val images = share.imageUris.filter { it.isNotBlank() }.take(9)
    if (images.isNotEmpty()) {
        return ShareAction.Compose(text = cleanText, imageUris = images)
    }
    if (cleanText.isEmpty()) return ShareAction.Empty

    val detected = extractWeiboLinkFromText(cleanText)
    if (detected != null) {
        val pureUrl = cleanText.equals(detected.rawUrl, ignoreCase = true)
        return if (isGuest || pureUrl) {
            ShareAction.OpenWeiboLink(detected)
        } else {
            ShareAction.AskWeiboLinkOrCompose(detected, cleanText)
        }
    }
    return ShareAction.Compose(text = cleanText, imageUris = emptyList())
}

private fun shareExtensionForMime(mimeType: String?): String = when (mimeType?.lowercase()) {
    "image/png" -> "png"
    "image/webp" -> "webp"
    "image/gif" -> "gif"
    else -> "jpg"
}

/**
 * 将系统分享传入的临时 `content://` 图片流拷贝到应用私有缓存目录，避免进程重建或切后台后读取权限失效，
 * 同时保留原字节流以便实况图探测正常工作。每次暂存会顺手清理超过 24 小时的旧缓存文件。
 */
fun stageSharedImageUris(
    context: Context,
    uris: List<Uri>,
    maxCount: Int = 9,
): List<String> {
    if (uris.isEmpty()) return emptyList()
    val dir = File(context.cacheDir, "shared_media").apply { mkdirs() }
    val cutoff = System.currentTimeMillis() - MaxSharedMediaAgeMs
    dir.listFiles()?.forEach { file ->
        if (file.isFile && file.lastModified() < cutoff) {
            runCatching { file.delete() }
        }
    }

    val now = System.currentTimeMillis()
    val uniqueUris = uris.distinct().take(maxCount)
    return uniqueUris.mapIndexedNotNull { index, uri ->
        val ext = shareExtensionForMime(runCatching { context.contentResolver.getType(uri) }.getOrNull())
        val dest = File(dir, "share_${now}_$index.$ext")
        val copied = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
                true
            } ?: false
        }.getOrDefault(false)
        if (copied && dest.length() > 0L) {
            Uri.fromFile(dest).toString()
        } else {
            runCatching { dest.delete() }
            uri.toString().takeIf { it.isNotBlank() }
        }
    }
}
