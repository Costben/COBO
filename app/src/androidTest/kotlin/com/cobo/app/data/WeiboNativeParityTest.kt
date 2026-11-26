package com.cobo.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cobo.app.AndroidJniWeiboNativeBackend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 真机逐字节对拍：对比原版 `libwbutil.so`（[AndroidJniWeiboNativeBackend]）与纯 Kotlin 实现
 * （[WeiboNativePure]）的 `i()`、`s(uid)`、`cum(path)` 与 `cumFrom(unsigned)` 输出。
 */
@RunWith(AndroidJUnit4::class)
class WeiboNativeParityTest {

    @Test
    fun pureKotlinMatchesLibwbutilByteForByte() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val jni = AndroidJniWeiboNativeBackend(ctx)
        assertTrue("libwbutil.so JNI backend must be available on ARM64 device", jni.available)

        // 1. i()
        assertEquals("i() mismatch", jni.i(), WeiboNativePure.i())

        // 2. s(uid) across diverse UIDs
        val uids = listOf(
            "",
            "0",
            "1",
            "1669879400",
            "2656274875",
            "5678901234",
            "7712345678",
            "9999999999",
            "1234567890123456",
        )
        for (uid in uids) {
            assertEquals("s($uid) mismatch", jni.s(uid), WeiboNativePure.s(uid))
        }

        // 3. cumFrom(unsigned) covering all 8 possible hash offsets (1..8), short strings, special chars, and existing &cum=
        val samples = mutableListOf(
            "",
            "/2/",
            "/2/a",
            "/2/ab",
            "/2/statuses/friends_timeline?from=10B6395010&c=android",
            "/2/statuses/friends_timeline?from=10B6395010&c=android&gsid=_2A25abc&s=12345678&count=20",
            "/2/cardlist?containerid=102803&from=10B6395010&c=android&page=1",
            "/2/comments/build_comments?id=5123456789012345&is_show_bulletin=2&from=10B6395010",
            "/2/profile/statuses/tab?containerid=2304131669879400_-_WEIBO_SECOND_PROFILE_WEIBO&page=2",
            "/2/searchall?q=%E5%BE%AE%E5%8D%9A&page=1&count=20",
            "/2/statuses/show?id=5000000000000000&cum=ABCD1234",
            "/2/statuses/show?id=5000000000000000&cum=ABCD1234&extra=1",
        )
        // Generate 64 synthetic paths to guarantee full coverage of offsets 1..8 and key padding lengths.
        for (k in 0 until 64) {
            samples += "/2/test/path_$k?from=${WeiboNative.FROM}&seq=$k&payload=${"x".repeat(k % 17)}"
        }

        val seenOffsets = mutableSetOf<Int>()
        for (unsigned in samples) {
            val expected = jni.cumFrom(unsigned)
            val actual = WeiboNativePure.cumFrom(unsigned)
            assertEquals("cumFrom($unsigned) mismatch", expected, actual)
            if (unsigned.length >= 4) {
                val h1 = WeiboNativePure.sha256ZeroIv(unsigned.encodeToByteArray())
                seenOffsets += (8 - (h1[0].toInt() and 0x7))
            }
        }
        assertEquals("Expected all 8 offsets (1..8) covered", (1..8).toSet(), seenOffsets)
    }
}
