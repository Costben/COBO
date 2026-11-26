package com.cobo.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** GitHub 的匿名配额只有 60 次/小时，撞上 403 时要让用户看懂该等多久，而不是只看到一串状态码。 */
class UpdateRateLimitTest {

    @Test
    fun reportsRemainingMinutesWhenResetIsKnown() {
        val now = System.currentTimeMillis() / 1000
        val message = Updater.failureMessage(403, (now + 600).toString())
        assertTrue(message, message.startsWith("GitHub 接口已限流"))
        assertTrue(message, Regex("约 \\d+ 分钟后恢复").containsMatchIn(message))
    }

    @Test
    fun fallsBackToPlainHintWhenResetHasPassed() {
        val now = System.currentTimeMillis() / 1000
        assertEquals("GitHub 接口已限流，请稍后再试", Updater.failureMessage(403, (now - 60).toString()))
        assertEquals("GitHub 接口已限流，请稍后再试", Updater.failureMessage(403, null))
    }

    @Test
    fun keepsStatusCodeForOtherFailures() {
        assertEquals("检查更新失败：HTTP 500", Updater.failureMessage(500, null))
    }
}
