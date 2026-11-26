package com.cobo.app.data

import com.cobo.app.json.JSONObject
import com.cobo.app.platform.PlatformContext
import com.cobo.app.platform.appFilesDir
import com.cobo.app.platform.sha256Hex

class CacheStore(@Suppress("UNUSED_PARAMETER") context: PlatformContext = PlatformContext()) {
    private val dir = appFilesDir().child("http-cache").apply { mkdirs() }
    private val maxBytes = 16L * 1024 * 1024

    fun key(url: String, cookie: String?): String =
        sha256Hex(((cookie ?: "") + "\n" + url).encodeToByteArray())

    fun read(url: String, cookie: String?): JSONObject? {
        val file = dir.child(key(url, cookie) + ".json")
        if (!file.exists()) return null
        val text = file.readTextOrNull() ?: return null
        return runCatching { JSONObject(text) }.getOrNull()
    }

    fun write(url: String, cookie: String?, json: JSONObject) {
        runCatching {
            dir.child(key(url, cookie) + ".json").writeTextAtomic(json.toString())
            trim()
        }
    }

    fun invalidate(url: String, cookie: String?) {
        runCatching { dir.child(key(url, cookie) + ".json").delete() }
    }

    fun clear() {
        runCatching { dir.listFiles().forEach { it.delete() } }
    }

    private fun trim() {
        val files = dir.listFiles()
        var total = files.sumOf { it.length() }
        if (total <= maxBytes) return
        files.sortedBy { it.lastModified() }.forEach { file ->
            if (total <= maxBytes) return
            total -= file.length()
            file.delete()
        }
    }
}
