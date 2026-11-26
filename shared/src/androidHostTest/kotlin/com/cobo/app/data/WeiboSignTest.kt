package com.cobo.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class WeiboSignTest {

    @Test
    fun `s 是 8 位十六进制`() {
        val value = WeiboSign.s("13000000000")
        assertEquals(8, value.length)
        assertEquals(value.lowercase(), value)
    }

    /** 固定输入的结果必须稳定：换设备、换账号都不能让同一个号算出不同的 s。 */
    @Test
    fun `s 对固定输入保持不变`() {
        assertEquals("a17486b9", WeiboSign.s("2015876998797"))
        assertEquals("0a806b7c", WeiboSign.s("13000000000"))
        assertEquals("067a07da", WeiboSign.s("1234567890"))
    }
}
