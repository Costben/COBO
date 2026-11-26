package com.cobo.app.data

import com.cobo.app.json.JSONArray
import com.cobo.app.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComposerModelsTest {
    @Test
    fun detectsMentionAtEmptyAndWithKeyword() {
        assertEquals(
            ComposerTrigger.Mention(0, 1, ""),
            detectComposerTrigger("@", 1),
        )
        assertEquals(
            ComposerTrigger.Mention(1, 2, ""),
            detectComposerTrigger("@@", 2),
        )
        assertEquals(
            ComposerTrigger.Mention(6, 9, "小王"),
            detectComposerTrigger("hello @小王", 9),
        )
        assertNull(detectComposerTrigger("hello @小王 ", 10))
    }

    @Test
    fun detectsTopicWithAndWithoutClosingHashAndClosesOnEvenHashes() {
        assertEquals(
            ComposerTrigger.Topic(0, 1, "", hasClosingHash = false),
            detectComposerTrigger("#", 1),
        )
        assertEquals(
            ComposerTrigger.Topic(0, 2, "", hasClosingHash = true),
            detectComposerTrigger("##", 1),
        )
        assertEquals(
            ComposerTrigger.Topic(0, 4, "原神", hasClosingHash = true),
            detectComposerTrigger("#原神#", 3),
        )
        assertNull(detectComposerTrigger("#原神# ", 5))
        assertEquals(
            ComposerTrigger.Mention(5, 8, "小明"),
            detectComposerTrigger("#原神# @小明", 8),
        )
    }

    @Test
    fun appliesMentionAndTopicReplacementCorrectly() {
        val mentionTrigger = detectComposerTrigger("hi @原", 5)!!
        assertEquals("hi @原神 ", applyComposerReplacement("hi @原", mentionTrigger, "原神").first)

        val topicTrigger = detectComposerTrigger("聊聊 #原#", 5)!!
        assertEquals("聊聊 #原神# ", applyComposerReplacement("聊聊 #原#", topicTrigger, "原神").first)
    }

    @Test
    fun parsesHotspotTopicsAndVisibilityCodes() {
        val root = JSONObject()
            .put("ok", 1)
            .put(
                "data",
                JSONArray()
                    .put(JSONObject().put("title", "#原神#").put("description", "讨论10万"))
                    .put(JSONObject().put("title", "原神").put("description", "重复"))
                    .put(JSONObject().put("content", "#星穹铁道#").put("category_name", "话题")),
            )
        val parsed = parseHotspotTopics(root)
        assertEquals(listOf("原神", "星穹铁道"), parsed.map { it.title })
        assertEquals(listOf(0, 6, 1), WeiboVisibility.entries.map { it.code })
    }
}
