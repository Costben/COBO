package com.cobo.app.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cobo.app.App
import com.cobo.app.data.Api
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Verifies the disk cache contract: account isolation, invalidation, corrupt files,
 * and that Api.get really serves a cached body without touching the network.
 */
@RunWith(AndroidJUnit4::class)
class CacheStoreTest : DataE2eTest() {

    private val stamp = System.nanoTime()
    private val url = "https://e2e.invalid/cache-probe/$stamp"
    private val body = JSONObject().put("ok", 1).put("marker", stamp)

    @Test
    fun writeReadInvalidate_roundTrip() {
        App.cache.write(url, cookie, body)
        val cached = App.cache.read(url, cookie)
        assertNotNull("写入后读不到缓存", cached)
        assertEquals(stamp, cached!!.optLong("marker"))

        App.cache.invalidate(url, cookie)
        assertNull("invalidate 后缓存仍存在", App.cache.read(url, cookie))
    }

    @Test
    fun keys_areIsolatedByCookieAndUrl() {
        val byCookieA = App.cache.key(url, "cookie-a")
        val byCookieB = App.cache.key(url, "cookie-b")
        val byUrl = App.cache.key("$url?v=2", "cookie-a")
        assertNotEquals("不同账号共用了缓存 key", byCookieA, byCookieB)
        assertNotEquals("不同 URL 共用了缓存 key", byCookieA, byUrl)
    }

    @Test
    fun corruptFile_isIgnored() {
        App.cache.write(url, cookie, body)
        val file = File(App.context.filesDir, "http-cache/${App.cache.key(url, cookie)}.json")
        file.writeText("{not-json")
        assertNull("损坏的缓存文件应被忽略", App.cache.read(url, cookie))
        file.delete()
    }

    @Test
    fun apiGet_servesCacheWithoutNetwork() {
        App.cache.write(url, cookie, body)
        val served = io { Api.get(url, cookie, force = false) }
        assertEquals("Api.get 没有命中缓存", stamp, served.optLong("marker"))

        App.cache.invalidate(url, cookie)
        val error = runCatching { io { Api.get(url, cookie, force = false) } }.exceptionOrNull()
        assertNotNull("缓存失效后仍返回了数据", error)
    }

    @Test
    fun apiGet_doesNotLeakAcrossAccounts() {
        App.cache.write(url, "another-account", body)
        val error = runCatching { io { Api.get(url, cookie, force = false) } }.exceptionOrNull()
        assertNotNull("缓存没有按账号隔离", error)
        App.cache.invalidate(url, "another-account")
    }
}
