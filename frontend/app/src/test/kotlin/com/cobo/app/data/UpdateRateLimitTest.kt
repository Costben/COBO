package com.cobo.app.data

import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** GitHub 的匿名配额只有 60 次/小时，撞上 403 时要让用户看懂该等多久，而不是只看到一串状态码。 */
class UpdateRateLimitTest {

    private fun response(code: Int, resetAt: Long? = null): Response =
        Response.Builder()
            .request(Request.Builder().url("https://api.github.com/").build())
            .protocol(Protocol.HTTP_2)
            .code(code)
            .message("")
            .apply { if (resetAt != null) header("X-RateLimit-Reset", resetAt.toString()) }
            .build()

    @Test
    fun reportsRemainingMinutesWhenResetIsKnown() {
        val now = System.currentTimeMillis() / 1000
        val message = Updater.failureMessage(response(403, now + 600))
        assertTrue(message, message.startsWith("GitHub 接口已限流"))
        assertTrue(message, Regex("约 \\d+ 分钟后恢复").containsMatchIn(message))
    }

    @Test
    fun fallsBackToPlainHintWhenResetHasPassed() {
        val now = System.currentTimeMillis() / 1000
        assertEquals("GitHub 接口已限流，请稍后再试", Updater.failureMessage(response(403, now - 60)))
        assertEquals("GitHub 接口已限流，请稍后再试", Updater.failureMessage(response(403)))
    }

    @Test
    fun keepsStatusCodeForOtherFailures() {
        assertEquals("检查更新失败：HTTP 500", Updater.failureMessage(response(500)))
    }
}
