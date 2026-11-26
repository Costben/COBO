package com.cobo.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RichTextEmojiTest {

    private val urls = mapOf(
        "[笑而不语]" to "https://face.t.sinajs.cn/heiheihei.png",
        "[doge]" to "https://face.t.sinajs.cn/doge.png",
        "[春游家族]" to "https://face.t.sinajs.cn/youngfamily.png",
    )

    private fun urlOf(phrase: String): String? = urls[phrase]

    private fun List<Pair<String?, String>>.texts() = filter { it.first == null }.map { it.second }
    private fun List<Pair<String?, String>>.emojis() = filter { it.first != null }

    @Test
    fun splitsImageEmojiWhicheverOrderAttributesComeIn() {
        val altFirst = splitEmoji(
            "a<img alt=\"[doge]\" src=\"https://face.t.sinajs.cn/doge.png\" style=\"width:1em;\">b",
            ::urlOf,
        )
        val srcFirst = splitEmoji(
            "a<img style='width: 1rem;height: 1rem' src='https://face.t.sinajs.cn/doge.png' alt='[doge]'>b",
            ::urlOf,
        )
        listOf(altFirst, srcFirst).forEach { segments ->
            assertEquals(listOf("a", "b"), segments.texts())
            assertEquals(1, segments.emojis().size)
            assertEquals("[doge]", segments.emojis().single().second)
        }
    }

    @Test
    fun splitsLiteralShortcodesIntoImages() {
        val segments = splitEmoji("笑死[笑而不语]<br/>真的", ::urlOf)
        assertEquals(listOf("笑死", "<br/>真的"), segments.texts())
        assertEquals("https://face.t.sinajs.cn/heiheihei.png", segments.emojis().single().first)
    }

    @Test
    fun keepsBracketsTheTableDoesNotKnow() {
        val segments = splitEmoji("高达TR-1[海兹尔改] 解说", ::urlOf)
        assertEquals(listOf("高达TR-1[海兹尔改] 解说"), segments.texts())
        assertEquals(0, segments.emojis().size)
    }

    @Test
    fun normalisesProtocolRelativeAndPlainHttpSources() {
        val segments = splitEmoji(
            "<img alt=\"[春游家族]\" src=\"//face.t.sinajs.cn/youngfamily.png\">" +
                "<img alt=\"[doge]\" src=\"http://face.t.sinajs.cn/doge.png\">",
            ::urlOf,
        )
        assertEquals(
            listOf(
                "https://face.t.sinajs.cn/youngfamily.png",
                "https://face.t.sinajs.cn/doge.png",
            ),
            segments.emojis().map { it.first },
        )
    }

    @Test
    fun selectsImageAndWeiboGlyphsForLinks() {
        assertEquals(LinkGlyph.Image, glyphFor("https://m.weibo.cn/status/123", "查看图片"))
        assertEquals(LinkGlyph.Weibo, glyphFor("https://m.weibo.cn/detail/123", "微博正文"))
        assertNull(glyphFor("https://example.com", "网页链接"))
    }
}
