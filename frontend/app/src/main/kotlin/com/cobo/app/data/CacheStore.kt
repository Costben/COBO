package com.cobo.app.data

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Disk cache for GET responses. Lists are intentionally allowed to go stale: the app
 * shows the cached page on entry and only hits the network when the user pulls to
 * refresh or explicitly asks for a reload. Keys include the session cookie so accounts
 * never see each other's pages.
 */
class CacheStore(context: Context) {
    private val dir = File(context.filesDir, "http-cache").apply { mkdirs() }
    private val maxBytes = 16L * 1024 * 1024

    fun key(url: String, cookie: String?): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(((cookie ?: "") + "\n" + url).toByteArray())
        val out = StringBuilder(digest.size * 2)
        digest.forEach { byte ->
            val value = byte.toInt() and 0xff
            out.append(HEX[value ushr 4]).append(HEX[value and 0x0f])
        }
        return out.toString()
    }

    fun read(url: String, cookie: String?): JSONObject? {
        val file = File(dir, key(url, cookie) + ".json")
        if (!file.exists()) return null
        return runCatching { JSONObject(file.readText()) }.getOrNull()
    }

    fun write(url: String, cookie: String?, json: JSONObject) {
        runCatching {
            File(dir, key(url, cookie) + ".json").writeText(json.toString())
            trim()
        }
    }

    fun invalidate(url: String, cookie: String?) {
        runCatching { File(dir, key(url, cookie) + ".json").delete() }
    }

    fun clear() {
        runCatching { dir.listFiles()?.forEach { it.delete() } }
    }

    private fun trim() {
        val files = dir.listFiles() ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxBytes) return
        files.sortedBy { it.lastModified() }.forEach { file ->
            if (total <= maxBytes) return
            total -= file.length()
            file.delete()
        }
    }

    private companion object {
        val HEX = "0123456789abcdef".toCharArray()
    }
}
