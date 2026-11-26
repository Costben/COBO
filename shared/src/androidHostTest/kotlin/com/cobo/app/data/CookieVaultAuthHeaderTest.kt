package com.cobo.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 认证请求头必须带上 SUB。
 *
 * 网页登录时 SUB 有时只落在 `passport.weibo.cn` 上，按 `m.weibo.cn` 拼域名匹配就会把它漏掉；
 * 曾经因此在下一次收下响应里的 cookie、重算请求头时把登录态写成空壳，重启后直接掉登录。
 */
class CookieVaultAuthHeaderTest {

    private val table = mapOf(
        ".weibo.cn" to "SCF=scf-value\n_T_WM=21620658152\nMLOGIN=0",
        "passport.weibo.cn" to "SUB=sub-value\nSUBP=subp-value\nMLOGIN=1",
        ".m.weibo.cn" to "XSRF-TOKEN=xsrf",
        ".weibo.com" to "WBPSESS=wbpsess",
    )

    @Test
    fun `falls back to passport sub when m weibo cn has none`() {
        val header = CookieVault.authHeader(table, "m.weibo.cn")
        assertTrue(header.contains("SUB=sub-value"))
        assertTrue(header.contains("SUBP=subp-value"))
        assertTrue(header.contains("_T_WM=21620658152"))
        assertTrue(header.contains("XSRF-TOKEN=xsrf"))
        // 别的域名下的 cookie 不往请求头里搬。
        assertTrue(!header.contains("WBPSESS"))
    }

    @Test
    fun `keeps matched sub and does not override it`() {
        val own = table + (".weibo.cn" to "SUB=own-value\nSUBP=own-subp\nALF=1")
        val header = CookieVault.authHeader(own, "m.weibo.cn")
        assertTrue(header.contains("SUB=own-value"))
        assertTrue(header.contains("SUBP=own-subp"))
        assertTrue(!header.contains("SUB=sub-value"))
    }

    @Test
    fun `empty table gives empty header`() {
        assertEquals("", CookieVault.authHeader(emptyMap(), "m.weibo.cn"))
    }

    @Test
    fun `header without any sub stays without sub`() {
        val noSub = mapOf(".weibo.cn" to "SCF=scf-value", ".weibo.com" to "WBPSESS=wbpsess")
        val header = CookieVault.authHeader(noSub, "m.weibo.cn")
        assertTrue(!header.contains("SUB="))
        assertEquals(header, CookieVault.header(noSub, "m.weibo.cn"))
    }
}
