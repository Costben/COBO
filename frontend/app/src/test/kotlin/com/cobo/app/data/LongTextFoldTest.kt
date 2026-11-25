package com.cobo.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 官方信息流在 280 个半角宽度（约 140 个汉字）处截断长正文，再补「展开全文」。
 * 这里固定住这条规则，免得后面调排版时把截断点挪走。
 */
class LongTextFoldTest {

    @Test
    fun foldsAtOfficialHalfWidthBudget() {
        assertEquals("字".repeat(140), foldWeiboText("字".repeat(200)))
    }

    @Test
    fun leavesTextWithinBudgetAlone() {
        assertNull(foldWeiboText("字".repeat(140)))
        assertNull(foldWeiboText("a".repeat(280)))
        assertNull(foldWeiboText(""))
    }

    @Test
    fun countsHalfWidthAndFullWidthTogether() {
        val raw = "a".repeat(100) + "字".repeat(100)
        // 100 个半角 + 每个汉字 2：预算 280 还能放 90 个汉字。
        assertEquals("a".repeat(100) + "字".repeat(90), foldWeiboText(raw))
    }

    @Test
    fun countsLinkByItsUrlLength() {
        val url = "http://t.cn/AXTZ7XzB"
        val raw = "字".repeat(130) + url + "尾巴"
        // 260 + 链接 20 = 280，后面的字放不下。
        assertEquals("字".repeat(130) + url, foldWeiboText(raw))
    }

    @Test
    fun treatsEmojiTokenAsOneWideGlyph() {
        val raw = "字".repeat(139) + "[微笑]" + "字".repeat(10)
        assertEquals("字".repeat(139) + "[微笑]", foldWeiboText(raw))
    }

    @Test
    fun countsCurlyQuotesAsFullWidth() {
        // 中文语境下引号、破折号、省略号都按全角算：276 + 4 还能放下「“引」，再加一个就超了。
        val raw = "字".repeat(138) + "“引”"
        assertEquals("字".repeat(138) + "“引", foldWeiboText(raw))
        assertNull(foldWeiboText("字".repeat(137) + "“引"))
    }

    @Test
    fun parseKeepsFullTextAndStoresFold() {
        val long = "字".repeat(200)
        val json = JSONObject(
            """
            {
              "id": "5368732950202572",
              "mid": "5368732950202572",
              "text": "服务端截断版 \u200b ...",
              "created_at": "Mon Nov 24 10:00:00 +0800 2026",
              "source": "微博网页版",
              "isLongText": true,
              "longText": {"longTextContent": "$long"}
            }
            """.trimIndent(),
        )

        val status = Status.from(json)

        assertNotNull(status)
        assertTrue(status!!.isLongText)
        assertEquals(long, status.plainText)
        assertEquals("字".repeat(140), stripHtml(status.foldedTextHtml))
    }

    @Test
    fun snapshotKeepsFold() {
        val long = "字".repeat(200)
        val status = Status.from(
            JSONObject(
                """
                {
                  "id": "9",
                  "mid": "9",
                  "text": "服务端截断版",
                  "created_at": "Mon Nov 24 10:00:00 +0800 2026",
                  "source": "微博网页版",
                  "isLongText": true,
                  "longText": {"longTextContent": "$long"}
                }
                """.trimIndent(),
            ),
        )!!

        val restored = statusFromSnapshot(statusToSnapshot(status))!!

        assertTrue(restored.isLongText)
        assertEquals(status.foldedTextHtml, restored.foldedTextHtml)
        assertEquals(long, restored.plainText)
    }

    @Test
    fun parseFallsBackToServerTruncationWithoutFullText() {
        val json = JSONObject(
            """
            {
              "id": "1",
              "mid": "1",
              "text": "开头\u200b\u200b ...<span class=\"expand\">展开</span>",
              "created_at": "Mon Nov 24 10:00:00 +0800 2026",
              "source": "微博网页版",
              "isLongText": true
            }
            """.trimIndent(),
        )

        val status = Status.from(json)!!

        assertTrue(status.isLongText)
        assertNull(status.foldedTextHtml)
        // 服务端的「…展开」尾巴要清掉，只留自己的「全文」入口。
        assertFalse(status.plainText.contains("展开"))
        assertTrue(status.plainText.endsWith("全文"))
    }

    @Test
    fun foldsHtmlSearchResultAndBalancesOpenTags() {
        val html = "#<a href=\"/k/热搜\">热搜话题</a># " + "正文内容".repeat(40) + "<em>关键词尾段</em>后续文字"
        val folded = foldWeiboHtml(html)
        assertNotNull(folded)
        assertTrue(folded!!.contains("<a href=\"/k/热搜\">热搜话题</a>"))
        // 截断发生在 <em> 内部时必须补闭合标签，不破坏 HTML 结构。
        assertEquals(folded.split("<em").size, folded.split("</em>").size)
        assertEquals(folded.split("<a ").size, folded.split("</a>").size)
        assertFalse(folded.contains("后续文字"))
    }

    @Test
    fun parseFoldsSearchStatusContainingHtmlTags() {
        val longHtml = "<a href=\"/k/话题\">#热搜#</a> " + "<em>测试</em>" + "长内容".repeat(60)
        val json = JSONObject(
            """
            {
              "id": "5368732950999999",
              "mid": "5368732950999999",
              "text": ${JSONObject.quote(longHtml)},
              "created_at": "Mon Nov 24 10:00:00 +0800 2026",
              "source": "微博网页版",
              "isLongText": false
            }
            """.trimIndent(),
        )

        val status = Status.from(json)!!
        assertTrue(status.isLongText)
        assertNotNull(status.foldedTextHtml)
        assertTrue(stripHtml(status.foldedTextHtml).length < status.plainText.length)
    }

    @Test
    fun foldsMultiLineTextAndHtmlExceedingLineLimit() {
        val multiLine = (1..10).joinToString("\n") { "第${it}行短句" }
        val foldedPlain = foldWeiboText(multiLine)
        assertNotNull(foldedPlain)
        assertEquals(6, foldedPlain!!.count { it == '\n' })
        assertFalse(foldedPlain.contains("第8行"))

        val multiBr = (1..10).joinToString("<br/>") { "第${it}行短句" }
        val foldedHtml = foldWeiboHtml(multiBr)
        assertNotNull(foldedHtml)
        assertFalse(foldedHtml!!.contains("第8行"))
    }

    @Test
    fun foldsLongIntroWithIntroBudget() {
        val longIntro = "这是一个非常长的超话或者话题简介内容".repeat(6)
        val folded = foldWeiboText(longIntro, budget = WeiboIntroFoldWidth, maxNewlines = WeiboIntroFoldMaxNewlines)
        assertNotNull(folded)
        assertTrue(folded!!.length < longIntro.length)

        val shortIntro = "简短介绍"
        assertNull(foldWeiboText(shortIntro, budget = WeiboIntroFoldWidth, maxNewlines = WeiboIntroFoldMaxNewlines))
    }
}
