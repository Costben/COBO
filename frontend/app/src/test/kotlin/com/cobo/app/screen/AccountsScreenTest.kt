package com.cobo.app.screen

import com.cobo.app.data.Account
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountsScreenTest {

    @Test
    fun matchesCurrentAccountByUidEvenAfterCookieRotation() {
        val active = Account(
            uid = "1001",
            name = "当前账号",
            avatar = "",
            cookie = "SUB=stale_cookie",
            gsid = "gsid_1001",
        )
        val other = Account(
            uid = "1002",
            name = "备用账号",
            avatar = "",
            cookie = "SUB=other_cookie",
        )

        assertTrue(isCurrentAccount(active, currentUid = "1001", currentCookie = "SUB=rotated_cookie", guest = false))
        assertFalse(isCurrentAccount(other, currentUid = "1001", currentCookie = "SUB=rotated_cookie", guest = false))
        assertFalse(isCurrentAccount(active, currentUid = "1001", currentCookie = "SUB=stale_cookie", guest = true))
    }

    @Test
    fun fallsBackToCookieOnlyWhenUidUnavailable() {
        val account = Account(
            uid = "1001",
            name = "当前账号",
            avatar = "",
            cookie = "SUB=matched_cookie",
        )

        assertTrue(isCurrentAccount(account, currentUid = null, currentCookie = "SUB=matched_cookie", guest = false))
        assertFalse(isCurrentAccount(account, currentUid = "", currentCookie = "SUB=other_cookie", guest = false))
    }
}
