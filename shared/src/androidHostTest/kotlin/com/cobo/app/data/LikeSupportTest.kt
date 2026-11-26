package com.cobo.app.data

import com.cobo.app.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LikeSupportTest {

    @Test
    fun statusFromReadsAttitudesStatusWhenLikedMissing() {
        val clientStatus = Status.from(
            JSONObject(
                """
                {
                  "id": "5364298395682028",
                  "text": "hello",
                  "attitudes_count": 12,
                  "attitudes_status": 1
                }
                """.trimIndent(),
            ),
        )!!
        assertTrue(clientStatus.liked)

        val unliked = Status.from(
            JSONObject(
                """
                {
                  "id": "5364298395682029",
                  "text": "hello",
                  "attitudes_count": 0,
                  "attitudes_status": 0
                }
                """.trimIndent(),
            ),
        )!!
        assertFalse(unliked.liked)
    }

    @Test
    fun commentFromReadsLikeStateAndNumericLiked() {
        val byLikeState = Comment.from(
            JSONObject(
                """
                {
                  "id": "1001",
                  "text": "nice",
                  "like_count": 3,
                  "like_state": 1
                }
                """.trimIndent(),
            ),
        )!!
        assertTrue(byLikeState.liked)

        val byNumericLiked = Comment.from(
            JSONObject(
                """
                {
                  "id": "1002",
                  "text": "nice",
                  "like_counts": 1,
                  "liked": 1
                }
                """.trimIndent(),
            ),
        )!!
        assertTrue(byNumericLiked.liked)
    }

    @Test
    fun shouldFallbackLikeToDesktopFiltersAuthErrors() {
        assertFalse(shouldFallbackLikeToDesktop(ApiException("纯本地模式不能这样操作，登录后可用", errno = "-100")))
        assertFalse(shouldFallbackLikeToDesktop(ApiException("未登录")))
        assertTrue(shouldFallbackLikeToDesktop(ApiException("操作频繁，请稍后再试", errno = "100005")))
        assertTrue(shouldFallbackLikeToDesktop(ApiException("update weibo too fast!", errno = "20016")))
        assertTrue(shouldFallbackLikeToDesktop(ApiHttpException(403, "")))
        assertTrue(shouldFallbackLikeToDesktop(ApiHttpException(429, "")))
    }

    @Test
    fun formatLikeErrorMessageMapsRateLimitCodes() {
        assertEquals(
            "点赞太频繁了，请稍后再试",
            formatLikeErrorMessage(ApiException("update weibo too fast!", errno = "20016")),
        )
        assertEquals(
            "请勿重复操作",
            formatLikeErrorMessage(ApiException("Don't be too greedy", errno = "20019")),
        )
        assertEquals(
            "点赞太频繁了，请稍后再试",
            formatLikeErrorMessage(ApiHttpException(429, "")),
        )
    }
}
