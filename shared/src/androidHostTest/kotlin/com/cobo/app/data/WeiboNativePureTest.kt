package com.cobo.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 固化 `WeiboNativePure` 与真机 ARM64 `libwbutil.so` 对拍后的黄金向量，
 * 确保 `i()`、`s(uid)` 与全 0 IV SHA-256 的 `cumFrom()` 永不回归。
 */
class WeiboNativePureTest {

    @Test
    fun matchesLibwbutilGoldenVectors() {
        assertEquals("f0cd624", WeiboNativePure.i())
        assertEquals(
            "775B12DB",
            WeiboNativePure.cumFrom("/2/statuses/friends_timeline?from=10B6395010&c=android"),
        )
        assertNull(WeiboNativePure.cumFrom("/2/statuses/show?id=5000000000000000&cum=ABCD1234"))
        assertNull(WeiboNativePure.cumFrom("/2/statuses/show?id=5000000000000000&cum=ABCD1234&extra=1"))
        assertNull(WeiboNativePure.cumFrom("/2/"))
    }
}
