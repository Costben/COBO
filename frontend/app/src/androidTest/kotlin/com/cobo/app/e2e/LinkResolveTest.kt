package com.cobo.app.e2e

import com.cobo.app.ui.resolveWeiboLink
import com.cobo.app.ui.weiboStatusId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Weibo's HTML mixes absolute URLs with site-relative paths. A relative one handed to the
 * WebView resolves against `file:///` and dies with ERR_ACCESS_DENIED, so both the resolver
 * and the status-link detection are pinned here.
 */
class LinkResolveTest {

    @Test
    fun statusLinksResolveToStatusId() {
        assertEquals("5356220451915389", weiboStatusId("/status/5356220451915389"))
        assertEquals("5356220451915389", weiboStatusId("status/5356220451915389"))
        assertEquals("5356220451915389", weiboStatusId("https://m.weibo.cn/status/5356220451915389"))
        assertEquals(
            "5356220451915389",
            weiboStatusId("https://m.weibo.cn/status/5356220451915389?mblogid=abc&luicode=20000174"),
        )
        assertEquals("5356220451915389", weiboStatusId("https://m.weibo.cn/detail/5356220451915389"))
        assertNull(weiboStatusId("https://m.weibo.cn/n/tombkeeper"))
        assertNull(weiboStatusId("http://t.cn/AXldEQjN"))
    }

    @Test
    fun relativeLinksBecomeAbsolute() {
        assertEquals("https://m.weibo.cn/n/tombkeeper", resolveWeiboLink("/n/tombkeeper"))
        assertEquals("https://m.weibo.cn/n/tombkeeper", resolveWeiboLink("n/tombkeeper"))
        assertEquals("https://m.weibo.cn/status/1", resolveWeiboLink("/status/1"))
        assertEquals("https://h5.sinaimg.cn/a", resolveWeiboLink("//h5.sinaimg.cn/a"))
        assertEquals("http://t.cn/AXldEQjN", resolveWeiboLink("http://t.cn/AXldEQjN"))
        assertNull(resolveWeiboLink("sinaweibo://video/vvs?mid=1"))
        assertNull(resolveWeiboLink("intent://gotovideo#Intent;scheme=sinaweibo;end"))
        assertNull(resolveWeiboLink("   "))
    }
}
