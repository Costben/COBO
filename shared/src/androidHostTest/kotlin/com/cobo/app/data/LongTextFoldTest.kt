package com.cobo.app.data

import com.cobo.app.json.JSONObject
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

    @Test
    fun detailFullTextDoesNotAppendFullTextLinkAndKeepsAuthorEllipsis() {
        val fullBody = "完整正文内容，".repeat(30) + "未完待续……"
        val json = JSONObject(
            """
            {
              "id": "5370000000001001",
              "mid": "5370000000001001",
              "text": ${JSONObject.quote(fullBody)},
              "created_at": "Mon Nov 24 10:00:00 +0800 2026",
              "source": "微博网页版",
              "isLongText": true
            }
            """.trimIndent(),
        )

        val status = Status.from(json, fullText = true)!!
        assertFalse(status.textHtml.contains(">全文</a>"))
        assertFalse(isServerTruncatedHtml(status.textHtml))
        assertTrue(status.plainText.endsWith("未完待续……"))
        assertNotNull(status.foldedTextHtml)
    }

    @Test
    fun detailFullTextStripsResidualTrailingFullTextLinkWithoutBreakingEntitiesOrRetweet() {
        val detailHtml = "A &amp; B 完整正文内容，文中有「全文」二字，结尾在这里...<a href=\"/status/5370000000001002\">全文</a> "
        val json = JSONObject(
            """
            {
              "id": "5370000000001002",
              "mid": "5370000000001002",
              "text": ${JSONObject.quote(detailHtml)},
              "created_at": "Mon Nov 24 10:00:00 +0800 2026",
              "source": "微博网页版",
              "isLongText": true,
              "retweeted_status": {
                "id": "5370000000001003",
                "mid": "5370000000001003",
                "text": "被转发的原博被服务端截断了",
                "created_at": "Mon Nov 24 09:00:00 +0800 2026",
                "source": "微博网页版",
                "isLongText": true
              }
            }
            """.trimIndent(),
        )

        val status = Status.from(json, fullText = true)!!
        assertFalse(status.textHtml.contains(">全文</a>"))
        assertFalse(status.textHtml.contains("&amp;amp;"))
        assertTrue(status.textHtml.contains("A &amp; B"))
        assertTrue(status.textHtml.contains("文中有「全文」二字，结尾在这里"))
        assertTrue(status.textHtml.endsWith("结尾在这里"))
        // 详情接口不保证被转发原博返回全文，原博缺省 longText 时仍需保留跳往原博详情页的「全文」入口。
        val retweeted = status.retweeted!!
        assertTrue(retweeted.textHtml.endsWith("...<a href=\"/status/5370000000001003\">全文</a>"))
    }

    @Test
    fun longTextContentStripsResidualTrailingFullTextMarker() {
        val longHtml = "长文正文内容".repeat(40) + "…<a href=\"https://m.weibo.cn/status/5370000000001004\"> 全文 </a><br/>"
        val json = JSONObject(
            """
            {
              "id": "5370000000001004",
              "mid": "5370000000001004",
              "text": "截断版本",
              "created_at": "Mon Nov 24 10:00:00 +0800 2026",
              "source": "微博网页版",
              "isLongText": true,
              "longText": {"longTextContent": ${JSONObject.quote(longHtml)}}
            }
            """.trimIndent(),
        )

        val status = Status.from(json)!!
        assertFalse(status.textHtml.contains("全文"))
        assertFalse(isServerTruncatedHtml(status.textHtml))
        assertNotNull(status.foldedTextHtml)
    }

    @Test
    fun truncatedFeedStatusStillAppendsLinkWhenBodyMentionsFullTextWord() {
        val json = JSONObject(
            """
            {
              "id": "5370000000001005",
              "mid": "5370000000001005",
              "text": "这篇文章我读了全文，下面总结要点...",
              "created_at": "Mon Nov 24 10:00:00 +0800 2026",
              "source": "微博网页版",
              "isLongText": true
            }
            """.trimIndent(),
        )

        val status = Status.from(json)!!
        assertTrue(status.textHtml.endsWith("...<a href=\"/status/5370000000001005\">全文</a>"))
        assertNull(status.foldedTextHtml)
    }

    @Test
    fun truncatedFeedStatusPreservesExistingFullTextLinkAndHtmlEntities() {
        val json = JSONObject(
            """
            {
              "id": "5370000000001006",
              "mid": "5370000000001006",
              "text": "A &amp; B 截断内容...<a href=\"/status/5370000000001006\">全文</a>",
              "created_at": "Mon Nov 24 10:00:00 +0800 2026",
              "source": "微博网页版",
              "isLongText": true
            }
            """.trimIndent(),
        )

        val status = Status.from(json)!!
        assertEquals("A &amp; B 截断内容...<a href=\"/status/5370000000001006\">全文</a>", status.textHtml)
        assertNull(status.foldedTextHtml)
    }

    @Test
    fun parseReadsLongTextContentFieldForRepostDetailWithHasExtraText() {
        val truncated = "根据美国司法部文件（1:26-cv-04150）目前的情况是被盗的币被冻结，Circle 表示自愿发行和被盗数额等量的新币，并在收到法院的没收令后将这些币转移给美国政府。//@tombkeeper:昨天币安被入侵，然后 Circle、Tether 等币圈公司紧急冻结了价值几十亿美元的加密货币。如果真的“匿名”“去中心化”，怎么"
        val full = truncated + "还能冻结呢？如果真的“匿名”“去中心化”，那这钱丢了就是丢了。"
        val json = JSONObject(
            """
            {
              "id": "5369483103309260",
              "mid": "5369483103309260",
              "text": ${JSONObject.quote(truncated)},
              "created_at": "Thu Nov 26 12:39:55 +0800 2026",
              "source": "微博网页版",
              "isLongText": false,
              "truncated": false,
              "has_extra_text": true,
              "longText": {
                "content": ${JSONObject.quote(full)}
              }
            }
            """.trimIndent(),
        )

        val detailStatus = Status.from(json, fullText = true)!!
        assertTrue(detailStatus.isLongText)
        assertEquals(full, detailStatus.plainText)
        assertEquals(truncated, stripHtml(detailStatus.foldedTextHtml))
        assertFalse(isServerTruncatedHtml(detailStatus.textHtml))
    }

    @Test
    fun parseMarksHasExtraTextWithoutLongTextAsServerTruncatedWhenNotFullText() {
        val json = JSONObject(
            """
            {
              "id": "5369483103309261",
              "mid": "5369483103309261",
              "text": "转发超长链截断正文",
              "created_at": "Thu Nov 26 12:39:55 +0800 2026",
              "source": "微博网页版",
              "isLongText": false,
              "truncated": false,
              "has_extra_text": true
            }
            """.trimIndent(),
        )

        val status = Status.from(json, fullText = false)!!
        assertTrue(status.isLongText)
        assertNull(status.foldedTextHtml)
        assertTrue(isServerTruncatedHtml(status.textHtml))
        assertTrue(status.textHtml.endsWith("...<a href=\"/status/5369483103309261\">全文</a>"))
    }

    @Test
    fun mergeDetailStatusWithSeededKeepsFullSeededTextWhenDetailRemainsTruncated() {
        val fullBody = "完整正文内容".repeat(40)
        val seeded = Status.from(
            JSONObject(
                """
                {
                  "id": "5370000000001009",
                  "mid": "5370000000001009",
                  "text": "截断版本",
                  "isLongText": true,
                  "longText": {"longTextContent": ${JSONObject.quote(fullBody)}}
                }
                """.trimIndent(),
            ),
        )!!
        val truncatedDetail = Status.from(
            JSONObject(
                """
                {
                  "id": "5370000000001009",
                  "mid": "5370000000001009",
                  "text": "截断版本",
                  "has_extra_text": true
                }
                """.trimIndent(),
            ),
            fullText = false,
        )!!
        val merged = mergeDetailStatusWithSeeded(truncatedDetail, seeded)!!
        assertEquals(fullBody, merged.plainText)
        assertNotNull(merged.foldedTextHtml)

        val shorterEditedDetail = Status.from(
            JSONObject(
                """
                {
                  "id": "5370000000001009",
                  "mid": "5370000000001009",
                  "text": "作者编辑后的短正文",
                  "isLongText": false
                }
                """.trimIndent(),
            ),
            fullText = true,
        )!!
        val mergedEdited = mergeDetailStatusWithSeeded(shorterEditedDetail, seeded)!!
        assertEquals("作者编辑后的短正文", mergedEdited.plainText)
    }
}
