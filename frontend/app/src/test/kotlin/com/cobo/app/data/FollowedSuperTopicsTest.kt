package com.cobo.app.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FollowedSuperTopicsTest {
    private val first = "100808" + "1".repeat(32)
    private val second = "100808" + "2".repeat(32)

    private fun card(
        id: String,
        name: Any? = "测试超话",
        icon: Any? = "//example.test/avatar.jpg",
    ): JSONObject = JSONObject()
        .put("oid", "1022:$id")
        .put("topic_name", name ?: JSONObject.NULL)
        .put("icon", icon ?: JSONObject.NULL)

    private fun pageJson(maxPage: Any, vararg cards: JSONObject): JSONObject =
        JSONObject().put("ok", 1).put(
            "data",
            JSONObject().put("max_page", maxPage).put("list", JSONArray(cards.toList())),
        )

    @Test
    fun parsesPageDeduplicatesAndNormalizesAvatarUrls() {
        val httpSinaCard = card(second, "测试乙", "http://tvax1.sinaimg.cn/crop.0.0.100.100/avatar.jpg")
        val root = pageJson(
            2,
            card(first, "测试甲", "//wx1.sinaimg.cn/thumb.jpg"),
            card(first, "重复项", "//wx1.sinaimg.cn/dup.jpg"),
            httpSinaCard,
        )

        val page = parseFollowedSuperTopicPage(root, page = 1)
        assertEquals(listOf(first, second), page.items.map { it.id })
        assertEquals(listOf("测试甲", "测试乙"), page.items.map { it.name })
        assertEquals(
            listOf(
                "https://wx1.sinaimg.cn/thumb.jpg",
                "https://tvax1.sinaimg.cn/crop.0.0.100.100/avatar.jpg",
            ),
            page.items.map { it.avatarUrl },
        )
        assertTrue(page.hasMore)
        assertEquals(2, page.maxPage)
    }

    @Test
    fun skipsIsolatedMalformedCardButRejectsAllInvalidPage() {
        val mixed = pageJson(
            1,
            card("not-a-supertopic", "坏卡片"),
            card(first, JSONObject.NULL),
            card(second, "正常超话"),
        )
        val parsed = parseFollowedSuperTopicPage(mixed, page = 1)
        assertEquals(listOf(second), parsed.items.map { it.id })
        assertEquals(listOf("正常超话"), parsed.items.map { it.name })
        assertFalse(parsed.hasMore)

        val allInvalid = pageJson(1, card("123", "错误 ID"), card(first, JSONObject.NULL))
        val err = runCatching { parseFollowedSuperTopicPage(allInvalid, page = 1) }.exceptionOrNull()
        assertEquals(ApiException::class.java, err?.javaClass)
    }

    @Test
    fun handlesConfirmedEmptyPagesAndUidValidation() {
        val emptyZero = parseFollowedSuperTopicPage(pageJson(0), page = 1)
        assertTrue(emptyZero.items.isEmpty())
        assertFalse(emptyZero.hasMore)

        val emptyOne = parseFollowedSuperTopicPage(pageJson(1), page = 1)
        assertTrue(emptyOne.items.isEmpty())
        assertFalse(emptyOne.hasMore)

        val shrunkLast = parseFollowedSuperTopicPage(pageJson(1), page = 2)
        assertTrue(shrunkLast.items.isEmpty())
        assertFalse(shrunkLast.hasMore)

        assertEquals(
            "https://weibo.com/u/page/follow/12345/231093_-_chaohua",
            followedSuperTopicsWebUrl("12345"),
        )
        assertNull(followedSuperTopicsWebUrl("../456"))
        assertNull(followedSuperTopicsWebUrl(""))
    }

    @Test
    fun capsHasMoreAtPageLimitInsteadOfFailing() {
        val page50 = parseFollowedSuperTopicPage(
            pageJson(FOLLOWED_SUPERTOPICS_PAGE_LIMIT + 5, card(first, "第50页")),
            page = FOLLOWED_SUPERTOPICS_PAGE_LIMIT,
        )
        assertEquals(1, page50.items.size)
        assertFalse(page50.hasMore)
    }

    @Test
    fun authRejectionAndIncompletePagesThrowApiException() {
        val authErr = runCatching {
            parseFollowedSuperTopicPage(JSONObject().put("ok", -100), page = 1)
        }.exceptionOrNull() as? ApiException
        assertEquals("-100", authErr?.errno)

        val badPayloads = listOf(
            JSONObject().put("error", "Forbidden") to 1,
            JSONObject().put("ok", 1) to 1,
            pageJson(0, card(first, "矛盾页数")) to 1,
            pageJson(2) to 1,
            pageJson(2) to 2,
        )
        for ((payload, page) in badPayloads) {
            val failure = runCatching { parseFollowedSuperTopicPage(payload, page) }.exceptionOrNull()
            assertEquals(ApiException::class.java, failure?.javaClass)
        }
    }

    @Test
    fun parsesSuperTopicFollowStateFromExtendPayload() {
        assertTrue(parseSuperTopicFollowState(JSONObject().put("is_followed", true)))
        assertFalse(parseSuperTopicFollowState(JSONObject().put("is_followed", false)))
        assertTrue(parseSuperTopicFollowState(JSONObject().put("is_followed", 1)))
        assertTrue(parseSuperTopicFollowState(JSONObject().put("follow_btn_text", "已关注")))
        assertFalse(parseSuperTopicFollowState(JSONObject().put("follow_btn_text", "关注")))

        val authErr = runCatching {
            parseSuperTopicFollowState(JSONObject().put("code", 100003).put("msg", "请先登录"))
        }.exceptionOrNull() as? ApiException
        assertEquals("-100", authErr?.errno)

        val unknownErr = runCatching { parseSuperTopicFollowState(JSONObject()) }.exceptionOrNull()
        assertEquals(ApiException::class.java, unknownErr?.javaClass)
    }

    @Test
    fun parsesSuperTopicFollowAndUnfollowMutations() {
        assertTrue(
            parseSuperTopicFollowMutation(
                JSONObject().put("code", 100000).put("follow_btn_text", "已关注"),
                targetFollow = true,
            ),
        )
        assertFalse(
            parseSuperTopicFollowMutation(
                JSONObject().put("code", 10000).put("follow_btn_text", "关注"),
                targetFollow = false,
            ),
        )

        val redirectErr = runCatching {
            parseSuperTopicFollowMutation(
                JSONObject().put("code", 100000).put("scheme", "https://passport.weibo.com/verify"),
                targetFollow = true,
            )
        }.exceptionOrNull()
        assertEquals(ApiException::class.java, redirectErr?.javaClass)

        val rejectedFollow = runCatching {
            parseSuperTopicFollowMutation(
                JSONObject().put("code", 100000).put("follow_btn_text", "关注").put("msg", "操作过于频繁"),
                targetFollow = true,
            )
        }.exceptionOrNull()
        assertEquals("操作过于频繁", rejectedFollow?.message)

        val authErr = runCatching {
            parseSuperTopicFollowMutation(
                JSONObject().put("code", 100003).put("msg", "请先登录"),
                targetFollow = false,
            )
        }.exceptionOrNull() as? ApiException
        assertEquals("-100", authErr?.errno)
    }
}
