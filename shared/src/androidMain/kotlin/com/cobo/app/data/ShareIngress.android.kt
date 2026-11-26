package com.cobo.app.data

import android.content.Context
import android.net.Uri
import java.io.File

private const val MaxSharedMediaAgeMs = 24L * 60L * 60L * 1000L

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
