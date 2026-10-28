package com.cobo.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 会员标识完全由接口字段决定（`mbtype` / `svip`），没有本地设置可覆盖，
 * 所以解析和归类要钉死，否则一次字段读错就会给普通用户挂上红标。
 */
class MembershipTest {

    @Test
    fun mbtypeZero_isNotMember() {
        assertEquals(Membership.None, User("1", "甲", "").membership)
    }

    @Test
    fun mbtypeTwo_isVip() {
        assertEquals(Membership.Vip, User("1", "甲", "", mbtype = 2).membership)
    }

    @Test
    fun mbtypeTwelve_isVip() {
        assertEquals(Membership.Vip, User("1", "甲", "", mbtype = 12, mbrank = 7).membership)
    }

    @Test
    fun svipWinsOverMbtype() {
        assertEquals(Membership.Svip, User("1", "甲", "", mbtype = 12, svip = true).membership)
        assertEquals(Membership.Svip, User("1", "甲", "", mbtype = 0, svip = true).membership)
    }

    @Test
    fun parseReadsVipFields() {
        val user = User.from(
            JSONObject(
                """{"id":1401527553,"screen_name":"tombkeeper","mbtype":12,"mbrank":4,"svip":1}""",
            ),
        )!!
        assertEquals(12, user.mbtype)
        assertEquals(4, user.mbrank)
        assertTrue(user.svip)
        assertEquals(Membership.Svip, user.membership)
    }

    @Test
    fun parseDefaultsWhenFieldsMissing() {
        val user = User.from(JSONObject("""{"id":42,"screen_name":"普通人"}"""))!!
        assertEquals(0, user.mbtype)
        assertEquals(0, user.mbrank)
        assertFalse(user.svip)
        assertEquals(Membership.None, user.membership)
    }
}
