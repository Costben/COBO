package com.cobo.app.data

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FollowedSuperTopicsTest {
    private val first = "100808" + "1".repeat(32)
    private val second = "100808" + "2".repeat(32)

    private fun card(id: String, name: String) =
        JSONObject().put("oid", "1022:$id").put("topic_name", name)
            .put("icon", "//example.test/avatar.jpg")

    private fun page(max: Int, vararg cards: JSONObject) =
        JSONObject().put("ok", 1).put(
            "data",
            JSONObject().put("max_page", max).put("list", JSONArray(cards.toList())),
        )

    @Test fun allPagesAndDuplicates() = runBlocking {
        val seen = mutableListOf<Int>()
        val actual = loadFollowedSuperTopicPages {
            seen += it
            if (it == 1) page(2, card(first, "测试甲"))
            else page(2, card(first, "重复"), card(second, "测试乙"))
        }
        assertEquals(listOf(1, 2), seen)
        assertEquals(listOf(first, second), actual.map { it.id })
        assertEquals(listOf("测试甲", "测试乙"), actual.map { it.name })
        assertEquals("https://example.test/avatar.jpg", actual.first().avatarUrl)
    }

    @Test fun confirmedEmptyAndUidValidation() = runBlocking {
        assertEquals(emptyList<FollowedSuperTopic>(), loadFollowedSuperTopicPages { page(0) })
        assertEquals(
            "https://weibo.com/u/page/follow/123/231093_-_chaohua",
            followedSuperTopicsWebUrl("123"),
        )
        assertNull(followedSuperTopicsWebUrl("../456"))
    }

    @Test fun rejectionAndIncompletePagesDoNotLookEmpty() = runBlocking {
        val loaders: List<suspend (Int) -> JSONObject> = listOf(
            { JSONObject().put("error", "Forbidden") },
            { JSONObject().put("ok", 1) },
            { page(1, card("123", "错误 ID")) },
            { if (it == 1) page(2, card(first, "测试甲")) else page(2) },
            { page(2, card(first, "重复页")) },
        )
        for (loader in loaders) {
            val failure = runCatching { loadFollowedSuperTopicPages(loader) }.exceptionOrNull()
            assertEquals(ApiException::class.java, failure?.javaClass)
        }
    }
}
