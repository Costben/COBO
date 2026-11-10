package com.cobo.app.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Includes the observed comprehensive-search group shape and synthetic boundary fixtures. */
class SuperTopicSearchTest {
    private val firstId = "100808e1f868bf9980f09ab6908787d7eaf0f0"
    private val secondId = "100808fc439dedbb06ca5fd858848e521b8716"

    private fun root(cards: JSONArray): JSONObject = JSONObject().put("ok", 1)
        .put("data", JSONObject().put("cards", cards))

    private fun card(id: String, title: String): JSONObject = JSONObject()
        .put("title_sub", title)
        .put("scheme", "https://m.weibo.cn/p/index?containerid=$id")

    @Test
    fun parsesObservedComprehensiveSupertopicGroupWithoutInventingAName() {
        val response = JSONObject(
            """{"ok":1,"data":{"cards":[{"card_type":236,"card_group":[
                {"card_type":17,"group":[{
                    "pic":"https://example.com/avatar.jpg",
                    "scheme":"sinaweibo://pageinfo?containerid=$secondId&extparam=example",
                    "title_sub":"超话","item_title":"超话","item_desc":"1276万旅行者"
                }]}
            ]}]}}"""
        )
        val result = parseSuperTopicSearch(response)
        assertEquals(1, result.size)
        assertEquals(secondId, result.single().containerId)
        assertEquals("超话", result.single().title)
        assertEquals("1276万旅行者", result.single().desc1)
        assertEquals("sinaweibo://pageinfo?containerid=$secondId&extparam=example", result.single().scheme)
    }

    @Test
    fun supportsItemTitleAndItemDescriptionFallbacksInsideGroups() {
        val topic = JSONObject().put("containerid", firstId)
            .put("item_title", "<em>星穹</em>铁道").put("item_desc", "旅行者")
        val nested = JSONObject().put("group", JSONArray().put(topic))
        val result = parseSuperTopicSearch(root(JSONArray().put(nested)))
        assertEquals("星穹铁道", result.single().title)
        assertEquals("旅行者", result.single().desc1)
        assertEquals(firstId, result.single().containerId)
    }

    @Test
    fun parsesNestedCardsAndPreservesIdentitySchemeAndDescriptions() {
        val topic = card(firstId, "#<em>星穹</em>铁道#").put("desc1", "阅读123亿").put("desc2", "粉丝123万")
        val group = JSONObject().put("card_group", JSONArray().put(topic))
        val outer = JSONObject().put("card_group", JSONArray().put(group))
        val result = parseSuperTopicSearch(root(JSONArray().put(outer)))
        assertEquals(1, result.size)
        assertEquals(firstId, result.single().containerId)
        assertEquals("星穹铁道", result.single().title)
        assertEquals(topic.getString("scheme"), result.single().scheme)
        assertEquals("阅读123亿", result.single().desc1)
        assertEquals("粉丝123万", result.single().desc2)
    }

    @Test
    fun supportsContainerIdAndStringTitleFallback() {
        val topic = JSONObject().put("containerid", firstId).put("title_sub", JSONObject())
            .put("title", "原神").put("desc1", JSONObject())
        val result = parseSuperTopicSearch(root(JSONArray().put(topic)))
        assertEquals(firstId, result.single().containerId)
        assertEquals("原神", result.single().title)
        assertEquals("", result.single().desc1)
        assertEquals("", result.single().scheme)
    }

    @Test
    fun sameNamesRetainDistinctIdsAndRepeatedIdsKeepFirstCard() {
        val result = parseSuperTopicSearch(root(JSONArray()
            .put(card(firstId, "共同名称"))
            .put(card(secondId, "共同名称"))
            .put(card(firstId, "重复卡片"))))
        assertEquals(listOf(firstId, secondId), result.map { it.containerId })
        assertEquals(listOf("共同名称", "共同名称"), result.map { it.title })
    }

    @Test
    fun omitsOrdinaryTopicsWrongHostsBlankAndMalformedCards() {
        val cards = JSONArray()
            .put(JSONObject().put("title_sub", "#原神#").put("scheme", "https://m.weibo.cn/p/index?containerid=100103type%3D38%26q%3D原神"))
            .put(JSONObject().put("title_sub", "原神").put("scheme", "https://evil.example/p/index?containerid=$firstId"))
            .put(card(firstId, "<em> </em>"))
            .put(JSONObject().put("containerid", secondId).put("title", 123))
            .put(JSONObject().put("title", "原神"))
            .put(JSONObject.NULL).put(42).put("malformed")
        assertTrue(parseSuperTopicSearch(root(cards)).isEmpty())
    }

    @Test
    fun ignoresStatusTreesAndUnrelatedJsonObjects() {
        val mention = card(firstId, "正文提及超话")
        val status = card(firstId, "微博卡片").put("mblog", JSONObject().put("page_info", mention))
            .put("card_group", JSONArray().put(mention)).put("group", JSONArray().put(mention))
        val unrelated = JSONObject().put("extra", JSONObject().put("group", JSONArray()
            .put(card(secondId, "无关嵌套对象"))))
        assertTrue(parseSuperTopicSearch(root(JSONArray().put(status).put(unrelated))).isEmpty())
    }

    @Test
    fun emptyAndFailedPayloadsReturnEmpty() {
        assertTrue(parseSuperTopicSearch(null).isEmpty())
        assertTrue(parseSuperTopicSearch(JSONObject()).isEmpty())
        assertTrue(parseSuperTopicSearch(JSONObject().put("ok", 0).put("data", JSONObject()
            .put("cards", JSONArray().put(card(firstId, "应忽略"))))).isEmpty())
        assertTrue(parseSuperTopicSearch(root(JSONArray())).isEmpty())
        assertTrue(parseSuperTopicSearch(JSONObject().put("ok", 1)).isEmpty())
    }

    @Test
    fun loginDenialHasSanitizedMessageAndSpecificCode() {
        try {
            parseSuperTopicSearch(JSONObject().put("ok", -100).put("msg", "private payload"))
            fail("Expected login denial")
        } catch (error: ApiException) {
            assertEquals("-100", error.errno)
            assertEquals("超话需要登录后查看", error.message)
        }
    }
}
