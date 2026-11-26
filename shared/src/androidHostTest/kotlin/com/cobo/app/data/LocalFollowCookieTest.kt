package com.cobo.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** 选不到人时要退回当前账号，不能把「本地关注」这条流打空。 */
class LocalFollowCookieTest {

    private val accounts = listOf(
        Account(uid = "1", name = "甲", avatar = "", cookie = "cookie-a"),
        Account(uid = "2", name = "乙", avatar = "", cookie = "cookie-b"),
    )

    @Test
    fun blankUidFollowsCurrent() {
        assertEquals("cookie-current", pickLocalFollowCookie("", accounts, "cookie-current"))
    }

    @Test
    fun matchedUidUsesThatAccount() {
        assertEquals("cookie-b", pickLocalFollowCookie("2", accounts, "cookie-current"))
    }

    @Test
    fun unknownUidFallsBackToCurrent() {
        assertEquals("cookie-current", pickLocalFollowCookie("404", accounts, "cookie-current"))
    }

    @Test
    fun blankAccountCookieFallsBackToCurrent() {
        val saved = listOf(Account(uid = "3", name = "丙", avatar = "", cookie = "  "))
        assertEquals("cookie-current", pickLocalFollowCookie("3", saved, "cookie-current"))
    }
}
