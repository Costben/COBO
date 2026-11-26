package com.cobo.app.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import com.cobo.app.App
import com.cobo.app.data.Api
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The user-visible promise of the disk cache: entering a tab again shows the saved
 * page and does not touch the network. A network fetch rewrites the cache file, so an
 * unchanged mtime proves the page was served from disk. Pull-to-refresh must rewrite it.
 */
@RunWith(AndroidJUnit4::class)
class PersistenceE2eTest : UiE2eTest() {

    private fun feedUrl(): String =
        Api.url("https://m.weibo.cn/api/container/getIndex", mapOf("containerid" to "102803"))

    @Test
    fun reenteringFeed_usesDiskCache() {
        waitTag("feed_card_0")

        // 强制走一次网络，确保缓存文件是最新的
        io { App.repo.container("102803", force = true) }
        val file = cacheFile(feedUrl(), App.repo.cookie())
        assertTrue("信息流缓存文件不存在：${file.absolutePath}", file.exists())
        assertTrue("信息流缓存文件为空", file.length() > 0)
        val before = file.lastModified()

        Thread.sleep(1_100)

        tapTag("bottom_tab_messages")
        waitAny(By.res("message_row_0"), By.text("还没有私信"), By.text("私信"))
        tapTag("bottom_tab_feed")
        waitTag("feed_card_0")

        assertEquals(
            "重新进入推荐流时又请求了网络（缓存文件被重写）",
            before,
            file.lastModified()
        )
    }

    @Test
    fun manualRefresh_rewritesDiskCache() {
        waitTag("feed_card_0")
        io { App.repo.container("102803", force = true) }
        val file = cacheFile(feedUrl(), App.repo.cookie())
        assertTrue("信息流缓存文件不存在", file.exists())
        val before = file.lastModified()

        Thread.sleep(1_100)
        tapTag("bottom_refresh")
        waitUntil("手动刷新后缓存文件没有更新") { file.lastModified() > before }
    }
}
