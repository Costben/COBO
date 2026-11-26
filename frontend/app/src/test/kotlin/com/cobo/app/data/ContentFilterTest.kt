package com.cobo.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentFilterTest {

    private fun user(id: String, name: String = "用户$id") = User(
        id = id,
        name = name,
        avatar = "",
    )

    private fun status(
        id: String = "1",
        uid: String = "100",
        name: String = "作者",
        html: String = "普通微博正文",
        retweeted: Status? = null,
    ) = Status(
        id = id,
        mid = id,
        textHtml = html,
        createdAt = "Mon Nov 17 10:00:00 +0800 2026",
        source = "",
        user = user(uid, name),
        retweeted = retweeted,
    )

    private fun comment(
        id: String = "c1",
        uid: String = "200",
        html: String = "普通评论",
    ) = Comment(
        id = id,
        textHtml = html,
        user = user(uid, "评论者"),
        createdAt = "Mon Nov 17 10:05:00 +0800 2026",
        likes = 0,
    )

    @Test
    fun plainKeywordMatchesCaseInsensitivelyAndStripsHtml() {
        val rules = ContentFilterRules(
            keywords = listOf(KeywordRule("kpop", isRegex = false)),
        )
        val matched = status(html = "<p>今天看 <a href=\"https://example.com\">KPOP</a> 新舞台</p>")
        val unmatched = status(html = "今天天气真好")

        assertTrue(shouldFilterStatus(matched, rules, FilterSurface.Feed))
        assertFalse(shouldFilterStatus(unmatched, rules, FilterSurface.Feed))
    }

    @Test
    fun regexValidationRejectsBrokenPatternsAndEngineNeverCrashes() {
        assertNotNull(validateKeywordRule("   ", isRegex = false))
        assertNotNull(validateKeywordRule("[unclosed(", isRegex = true))
        assertNull(validateKeywordRule("(剧透|spoiler)\\s*\\d+", isRegex = true))

        val rules = ContentFilterRules(
            keywords = listOf(
                KeywordRule("[broken(", isRegex = true),
                KeywordRule("广告\\s*#\\d+", isRegex = true),
            ),
        )
        assertTrue(shouldFilterStatus(status(html = "推广 广告 #42"), rules, FilterSurface.Feed))
        assertFalse(shouldFilterStatus(status(html = "正常分享日常"), rules, FilterSurface.Feed))
    }

    @Test
    fun topicNormalizationExtractionAndMatchingWorkAcrossMainAndRetweet() {
        assertEquals("早安", normalizeTopicRule("  ##早安＃  "))
        assertEquals("", normalizeTopicRule(" ### "))

        val post = status(
            html = "参与 #日常分享# 和 #早安#",
            retweeted = status(id = "2", uid = "101", html = "原博带 #日常分享# 与 #周末计划[超话]#"),
        )
        assertEquals(listOf("日常分享", "早安", "周末计划[超话]"), extractTopics(post))

        val rules = ContentFilterRules(topics = listOf("周末计划"))
        assertTrue(shouldFilterStatus(post, rules, FilterSurface.Feed))
        // 纯正文包含词但不带话题井号时，不被话题规则误杀
        assertFalse(
            shouldFilterStatus(status(html = "我写了一份周末计划表"), rules, FilterSurface.Feed),
        )
    }

    @Test
    fun muteUserModesAndSelfProtection() {
        val rules = ContentFilterRules(
            mutedUsers = listOf(
                MutedUser("u_all", "全静音", MuteUserMode.All),
                MutedUser("u_repost", "只盾转发", MuteUserMode.RepostsOnly),
                MutedUser("self_uid", "我自己", MuteUserMode.All),
            ),
            keywords = listOf(KeywordRule("自己")),
        )
        val quoted = status(id = "q1", uid = "999", html = "被转发的原博")

        // MuteUserMode.All 屏蔽原创与转发
        assertTrue(shouldFilterStatus(status(uid = "u_all", html = "原创"), rules, FilterSurface.Feed))
        assertTrue(shouldFilterStatus(status(uid = "u_all", retweeted = quoted), rules, FilterSurface.Feed))

        // MuteUserMode.RepostsOnly 保留原创，仅屏蔽其发出的转发
        assertFalse(shouldFilterStatus(status(uid = "u_repost", html = "纯原创"), rules, FilterSurface.Feed))
        assertTrue(shouldFilterStatus(status(uid = "u_repost", retweeted = quoted), rules, FilterSurface.Feed))

        // 他人转发了 RepostsOnly 用户的原创，不隐藏
        assertFalse(
            shouldFilterStatus(
                status(uid = "other", retweeted = status(id = "q2", uid = "u_repost", html = "他的原创")),
                rules,
                FilterSurface.Feed,
            ),
        )

        // 本人发布的微博不受关键词或静音名单影响
        assertFalse(
            shouldFilterStatus(
                status(uid = "self_uid", html = "我自己发的内容"),
                rules,
                FilterSurface.Feed,
                selfUid = "self_uid",
            ),
        )
    }

    @Test
    fun retweetCascadesKeywordTopicAndMutedOriginalAuthor() {
        val rules = ContentFilterRules(
            keywords = listOf(KeywordRule("剧透")),
            mutedUsers = listOf(MutedUser("bad_origin", "原博号", MuteUserMode.All)),
        )
        val repostHitKeyword = status(
            uid = "friend",
            html = "转发",
            retweeted = status(id = "r1", uid = "someone", html = "大结局剧透慎入"),
        )
        val repostHitMutedOrigin = status(
            uid = "friend",
            html = "看看",
            retweeted = status(id = "r2", uid = "bad_origin", html = "完全正常的字"),
        )

        assertTrue(shouldFilterStatus(repostHitKeyword, rules, FilterSurface.Feed))
        assertTrue(shouldFilterStatus(repostHitMutedOrigin, rules, FilterSurface.Feed))
    }

    @Test
    fun scopesControlFeedSearchHotAndCommentsIndependently() {
        val basePost = status(uid = "100", html = "包含测试关键词")
        val baseComment = comment(uid = "100", html = "包含测试关键词")
        val hotItem = HotItem(title = "今天的热搜测试关键词", scheme = "", rank = 1)

        val feedOff = ContentFilterRules(
            keywords = listOf(KeywordRule("测试关键词")),
            applyToFeeds = false,
            applyToSearchAndHot = true,
            applyToComments = true,
        )
        assertFalse(shouldFilterStatus(basePost, feedOff, FilterSurface.Feed))
        assertTrue(shouldFilterStatus(basePost, feedOff, FilterSurface.SearchAndHot))
        assertTrue(shouldFilterHotItem(hotItem, feedOff))
        assertTrue(shouldFilterComment(baseComment, feedOff))

        val searchOff = feedOff.copy(applyToFeeds = true, applyToSearchAndHot = false, applyToComments = false)
        assertTrue(shouldFilterStatus(basePost, searchOff, FilterSurface.Feed))
        assertFalse(shouldFilterStatus(basePost, searchOff, FilterSurface.SearchAndHot))
        assertFalse(shouldFilterHotItem(hotItem, searchOff))
        assertFalse(shouldFilterComment(baseComment, searchOff))
    }

    @Test
    fun commentFilteringHonorsAllMuteAndIgnoresRepostsOnly() {
        val rules = ContentFilterRules(
            keywords = listOf(KeywordRule("引战")),
            topics = listOf("抽奖"),
            mutedUsers = listOf(
                MutedUser("u_all", "全静音", MuteUserMode.All),
                MutedUser("u_repost", "只盾转发", MuteUserMode.RepostsOnly),
            ),
        )
        assertTrue(shouldFilterComment(comment(uid = "u_all", html = "正常发言"), rules))
        assertFalse(shouldFilterComment(comment(uid = "u_repost", html = "正常发言"), rules))
        assertTrue(shouldFilterComment(comment(uid = "other", html = "这就是引战"), rules))
        assertTrue(shouldFilterComment(comment(uid = "other", html = "转发 #抽奖#"), rules))
        assertFalse(shouldFilterComment(comment(uid = "self", html = "引战词"), rules, selfUid = "self"))
    }

    @Test
    fun hotItemFilteringSupportsKeywordsRegexAndTopics() {
        val rules = ContentFilterRules(
            keywords = listOf(KeywordRule("热播\\d+", isRegex = true)),
            topics = listOf("明星八卦"),
        )
        assertTrue(shouldFilterHotItem(HotItem("某剧热播10天", "", rank = 1), rules))
        assertTrue(shouldFilterHotItem(HotItem("今日明星八卦盘点", "", rank = 2), rules))
        assertFalse(shouldFilterHotItem(HotItem("开源客户端更新日志", "", rank = 3), rules))
    }

    @Test
    fun jsonRoundtripAndMalformedPayloadResilience() {
        val original = ContentFilterRules(
            keywords = listOf(
                KeywordRule("推广", isRegex = false),
                KeywordRule("ad_\\d+", isRegex = true),
            ),
            topics = listOf("热榜", "广告话题"),
            mutedUsers = listOf(
                MutedUser("123", "测试号", MuteUserMode.RepostsOnly),
                MutedUser("456", "静音号", MuteUserMode.All),
            ),
            applyToFeeds = true,
            applyToSearchAndHot = false,
            applyToComments = true,
        )
        val decoded = decodeContentFilterRules(encodeContentFilterRules(original))
        assertEquals(original, decoded)

        val corruptJson = JSONObject(
            """
            {
              "keywords": [{"pattern": "  ", "isRegex": false}, {"pattern": "ok", "isRegex": false}, {"pattern": "OK", "isRegex": false}],
              "topics": ["##", "  #Topic# ", "#topic#"],
              "mutedUsers": [{"uid": "", "name": "空"}, {"uid": "789", "name": "未知模式", "mode": "bad"}],
              "applyToFeeds": false
            }
            """.trimIndent(),
        )
        val recovered = decodeContentFilterRules(corruptJson)
        assertEquals(listOf(KeywordRule("ok", false)), recovered.keywords)
        assertEquals(listOf("Topic"), recovered.topics)
        assertEquals(listOf(MutedUser("789", "未知模式", MuteUserMode.All)), recovered.mutedUsers)
        assertFalse(recovered.applyToFeeds)
        assertTrue(recovered.applyToSearchAndHot)
        assertTrue(recovered.applyToComments)
    }

    @Test
    fun summaryFormattingNeverUsesMiddleDot() {
        assertFalse(formatContentFilterSummary(ContentFilterRules()).contains('\u00B7'))
        val populated = ContentFilterRules(
            keywords = listOf(KeywordRule("a")),
            topics = listOf("b", "c"),
            mutedUsers = listOf(MutedUser("1")),
        )
        val summary = formatContentFilterSummary(populated)
        assertEquals("已启用 1 条关键词、2 个话题、1 位用户", summary)
        assertFalse(summary.contains('\u00B7'))
    }
}
