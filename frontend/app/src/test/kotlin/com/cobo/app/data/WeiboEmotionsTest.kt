package com.cobo.app.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeiboEmotionsTest {
    @Test
    fun parsesAndNormalizesWeiboEmotionSections() {
        val root = JSONObject().put(
            "data",
            JSONObject().put(
                "emoticon",
                JSONObject().put(
                    "ZH_CN",
                    JSONObject()
                        .put(
                            "PC热门表情",
                            JSONArray()
                                .put(JSONObject().put("phrase", "[doge]").put("url", "//face.t.sinajs.cn/doge.png"))
                                .put(JSONObject().put("phrase", "[doge]").put("url", "http://face.t.sinajs.cn/dup.png"))
                                .put(JSONObject().put("phrase", "[微笑]").put("url", "http://face.t.sinajs.cn/smile.png")),
                        )
                        .put("空分组", JSONArray()),
                ),
            ),
        )
        val sections = parseWeiboEmotions(root)
        assertEquals(listOf("热门", "Emoji"), sections.map { it.label })
        assertEquals(
            listOf(
                WeiboEmoji("[doge]", "https://face.t.sinajs.cn/doge.png"),
                WeiboEmoji("[微笑]", "https://face.t.sinajs.cn/smile.png"),
            ),
            sections.first().items,
        )
        assertTrue(sections.last().items.isNotEmpty())
        // 批量灌完之后要一次性落到地址表里，正文才能把 [doge] 换成图。
        assertEquals("https://face.t.sinajs.cn/doge.png", WeiboEmojiBitmaps.urlOf("[doge]"))
    }

    @Test
    fun returnsEmptyWhenEmoticonMissing() {
        assertTrue(parseWeiboEmotions(null).isEmpty())
        assertTrue(parseWeiboEmotions(JSONObject().put("data", JSONObject())).isEmpty())
    }
}
