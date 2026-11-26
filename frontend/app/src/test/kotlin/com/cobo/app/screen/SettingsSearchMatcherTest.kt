package com.cobo.app.screen

import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.SearchTextMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchMatcherTest {

    private fun entry(
        id: String,
        title: String,
        summary: String = "",
        keywords: String = "",
    ) = SettingsEntry(id = id, title = title, summary = summary, keywords = keywords) {}

    private val sampleGroups = listOf(
        SettingsGroupSection(
            key = "display",
            title = "显示与交互",
            entries = listOf(
                entry("dark_mode", "深色模式", "跟随系统", "暗黑 夜间 黑夜 主题 dark theme"),
                entry("accent_color", "强调色", "默认", "主题色 配色 颜色 色彩 accent color"),
                entry("predictive_back", "预测性返回手势设置", "已开启", "侧滑 返回 手势 轨迹 模糊 跟手 predictive back gesture"),
                entry("blur_effects", "模糊效果", "关闭后顶栏与玻璃面改画纯色，滑动更省电", "毛玻璃 高斯 透明 blur glass"),
                entry("auto_hide_floating_bars", "滑动自动隐藏浮动栏", "向下浏览时自动收起顶部分段栏、悬浮底栏与详情页底部工具栏", "顶栏 底栏 工具栏 沉浸 隐藏 float bar"),
                entry("image_browse_quality", "浏览图片", "高清", "画质 原图 高清 缩略图 流量 省流 image quality"),
                entry("live_photo_auto_play", "实况图自动播放", "进入或滑动到实况图时自动播放一次", "实况 动态图 播放 live photo livephoto"),
                entry("video_auto_float_on_back", "视频播放时返回自动悬浮", "播放中按返回自动切换应用内小窗，切至后台自动进入系统画中画", "小窗 画中画 悬浮窗 后台 播放 video pip"),
                entry("video_auto_close_on_complete", "视频播完自动返回", "非直播视频播放结束后自动关闭并返回上一页", "播放结束 自动关闭 退出 video"),
            ),
        ),
        SettingsGroupSection(
            key = "feed",
            title = "微博浏览",
            entries = listOf(
                entry("reverse_browse", "倒序浏览", "刷新不回顶，从当前位置往上补看新微博", "时间线 不回顶 reverse"),
                entry("fold_long_text", "长文折叠", "长微博及较长简介默认折叠", "展开全文 收纳 截断 fold long text"),
                entry("show_vip_level", "显示 VIP 等级", "开启后在昵称旁显示 VIP / SVIP / VVIP 及等级标识", "会员 svip vvip 标识 badge"),
            ),
        ),
        SettingsGroupSection(
            key = "account",
            title = "账号与内容",
            entries = listOf(
                entry("accounts", "账号管理", "", "切号 多账号 添加 登录 account"),
            ),
        ),
        SettingsGroupSection(
            key = "other",
            title = "其他",
            entries = listOf(
                entry("clear_cache", "清理缓存", "", "清除 图片缓存 磁盘 空间 cache clear"),
            ),
            headerGroupKey = "other",
            group = SettingsGroup.Other,
        ),
        SettingsGroupSection(
            key = "other_logout",
            title = "其他",
            entries = listOf(
                entry("logout", "退出登录", "清除本地会话", "登出 注销 退出 logout"),
            ),
            headerGroupKey = "other",
            group = SettingsGroup.Other,
        ),
    )

    private fun matchedIds(query: String): List<String> =
        filterSettingsGroups(sampleGroups, parseSettingsSearchTerms(query))
            .flatMap { it.entries }
            .map { it.id }

    @Test
    fun matchesFullPinyinInitialsAndHybridSyllables() {
        assertEquals("dark_mode", matchedIds("shensemoshi").first())
        assertEquals("dark_mode", matchedIds("ssms").first())
        assertEquals("dark_mode", matchedIds("shensm").first())
        assertEquals("fold_long_text", matchedIds("cwzd").first())
        assertEquals("fold_long_text", matchedIds("changwenzhedie").first())
        assertEquals("reverse_browse", matchedIds("dxll").first())
        assertEquals("live_photo_auto_play", matchedIds("skt").first())
        assertEquals("clear_cache", matchedIds("qlhc").first())
    }

    @Test
    fun matchesInitialsSubsequenceAndCjkSubsequence() {
        assertTrue("auto_hide_floating_bars" in matchedIds("ycfl"))
        assertEquals("dark_mode", matchedIds("深模").first())
        assertEquals("clear_cache", matchedIds("清缓").first())
    }

    @Test
    fun matchesTraditionalAndRegionalSynonyms() {
        assertEquals("clear_cache", matchedIds("快取").first())
        assertEquals("clear_cache", matchedIds("緩存").first())
        assertEquals("image_browse_quality", matchedIds("畫質").first())
        assertEquals("live_photo_auto_play", matchedIds("實況").first())
        assertEquals("accounts", matchedIds("帳號").first())
    }

    @Test
    fun matchesMixedAsciiAndTypoTolerance() {
        assertEquals("show_vip_level", matchedIds("vip").first())
        assertEquals("show_vip_level", matchedIds("xsvip").first())
        assertTrue("video_auto_float_on_back" in matchedIds("pip"))
        assertTrue("video_auto_float_on_back" in matchedIds("hzh"))
        assertTrue("live_photo_auto_play" in matchedIds("shikuagn"))
    }

    @Test
    fun supportsMultiTermAndFiltering() {
        val ids = matchedIds("sp fh")
        assertTrue("video_auto_float_on_back" in ids)
        assertTrue("video_auto_close_on_complete" in ids)
        assertTrue("dark_mode" !in ids)

        val cnIds = matchedIds("视频 返回")
        assertTrue("video_auto_float_on_back" in cnIds)
        assertTrue("video_auto_close_on_complete" in cnIds)
        assertEquals(2, cnIds.size)
    }

    @Test
    fun emptyQueryPreservesOriginalOrderAndCardPositions() {
        val unfiltered = filterSettingsGroups(sampleGroups, parseSettingsSearchTerms("   "))
        assertEquals(sampleGroups.map { it.key }, unfiltered.map { it.key })

        assertEquals(CardListPosition.Full, cardPositionFor(0, 1))
        assertEquals(CardListPosition.Head, cardPositionFor(0, 3))
        assertEquals(CardListPosition.Middle, cardPositionFor(1, 3))
        assertEquals(CardListPosition.Tail, cardPositionFor(2, 3))
    }

    @Test
    fun returnsEmptyWhenNoEntryMatches() {
        assertTrue(matchedIds("zzzznotfound").isEmpty())
    }

    @Test
    fun doesNotCrossMatchUnrelatedKeywords() {
        // 避免不同同义关键词拼接或 2 字母跨词跳字造成误召回
        val ssmsIds = matchedIds("ssms")
        assertEquals(listOf("dark_mode"), ssmsIds)

        val spIds = matchedIds("sp")
        assertTrue("video_auto_float_on_back" in spIds)
        assertTrue("video_auto_close_on_complete" in spIds)
        assertTrue("accent_color" !in spIds)
        assertTrue("predictive_back" !in spIds)
        assertTrue("live_photo_auto_play" !in spIds)
    }

    @Test
    fun mergesSectionsSharingHeaderGroupKeyInSearchResults() {
        // 查询为空时保持独立卡片；查询命中同一分组下多张卡片时按 ColorOS 风格合并为一张结果卡片
        val unfiltered = filterSettingsGroups(sampleGroups, emptyList())
        assertEquals(listOf("display", "feed", "account", "other", "other_logout"), unfiltered.map { it.key })

        val merged = filterSettingsGroups(sampleGroups, parseSettingsSearchTerms("清除"))
        assertEquals(1, merged.size)
        assertEquals("other", merged.first().headerGroupKey)
        assertEquals(setOf("clear_cache", "logout"), merged.first().entries.map { it.id }.toSet())
    }

    @Test
    fun computesSearchResultCardPositionsBelowEmbeddedHeader() {
        assertEquals(CardListPosition.Tail, searchResultCardPositionFor(0, 1))
        assertEquals(CardListPosition.Middle, searchResultCardPositionFor(0, 3))
        assertEquals(CardListPosition.Middle, searchResultCardPositionFor(1, 3))
        assertEquals(CardListPosition.Tail, searchResultCardPositionFor(2, 3))
    }

    @Test
    fun highlightsMatchedRangesAcrossChinesePinyinInitialsSubsequenceAndTypo() {
        assertEquals(
            listOf(0..3),
            SearchTextMatcher.findHighlightRanges("深色模式", listOf("ssms")),
        )
        assertEquals(
            listOf(0..1),
            SearchTextMatcher.findHighlightRanges("深色模式", listOf("shense")),
        )
        assertEquals(
            listOf(0..2),
            SearchTextMatcher.findHighlightRanges("深色模式", listOf("shensm")),
        )
        assertEquals(
            listOf(0..3),
            SearchTextMatcher.findHighlightRanges("深色模式", listOf("shensms")),
        )
        assertEquals(
            listOf(0..0, 2..2),
            SearchTextMatcher.findHighlightRanges("深色模式", listOf("深模")),
        )
        assertEquals(
            listOf(0..1, 3..5),
            SearchTextMatcher.findHighlightRanges("显示 VIP 等级", listOf("xsvip")),
        )
        assertEquals(
            listOf(4..6, 8..8),
            SearchTextMatcher.findHighlightRanges("滑动自动隐藏浮动栏", listOf("ycfl")),
        )
        assertEquals(
            listOf(2..3),
            SearchTextMatcher.findHighlightRanges("清理缓存", listOf("快取")),
        )
        assertEquals(
            listOf(0..1, 5..6),
            SearchTextMatcher.findHighlightRanges("视频播放时返回自动悬浮", listOf("sp", "fh")),
        )
        assertEquals(
            listOf(0..1),
            SearchTextMatcher.findHighlightRanges("实况图自动播放", listOf("shikuagn")),
        )
        assertEquals(
            listOf(1..1, 3..3),
            SearchTextMatcher.findHighlightRanges("「我」页图标样式", listOf("我页")),
        )
    }
}
