package com.cobo.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareIngressTest {

    @Test
    fun combineSharedTextAvoidsDuplication() {
        assertEquals("正文", combineSharedText(null, "  正文  "))
        assertEquals("标题", combineSharedText("  标题 ", ""))
        assertEquals("标题\n正文", combineSharedText("标题", "正文"))
        assertEquals("完整标题和正文", combineSharedText("标题", "完整标题和正文"))
        assertEquals("", combineSharedText("   ", null))
    }

    @Test
    fun extractWeiboLinkSupportsAllWeiboTargetsAndIgnoresOthers() {
        val statusHit = extractWeiboLinkFromText("快来看这条微博 https://m.weibo.cn/detail/5012345678901234?luicode=10000011，太好笑了！")
        assertNotNull(statusHit)
        assertEquals(WeiboLinkKind.Status, statusHit?.kind)
        assertEquals("https://m.weibo.cn/detail/5012345678901234?luicode=10000011", statusHit?.rawUrl)

        val pcStatusHit = extractWeiboLinkFromText("推荐 https://weibo.com/12345678/P1a2B3c4D。")
        assertEquals(WeiboLinkKind.Status, pcStatusHit?.kind)

        val schemeStatusHit = extractWeiboLinkFromText("打开 sinaweibo://detail?mblogid=P1a2B3c4D")
        assertEquals(WeiboLinkKind.Status, schemeStatusHit?.kind)

        val userHit = extractWeiboLinkFromText("主页 https://weibo.com/u/1669879400")
        assertEquals(WeiboLinkKind.User, userHit?.kind)

        val superTopicHit = extractWeiboLinkFromText("超话 https://m.weibo.cn/p/index?containerid=100808fc439dedbb06ca5fd858848e521b8716")
        assertEquals(WeiboLinkKind.SuperTopic, superTopicHit?.kind)

        val topicHit = extractWeiboLinkFromText("话题 https://s.weibo.com/weibo?q=%23早安%23")
        assertEquals(WeiboLinkKind.Topic, topicHit?.kind)

        val videoHit = extractWeiboLinkFromText("视频 https://video.weibo.com/show?fid=1034:501234567890")
        assertEquals(WeiboLinkKind.Video, videoHit?.kind)

        val liveHit = extractWeiboLinkFromText("直播 https://weibo.com/l/wblive/m/show/1022:2321325012345678")
        assertEquals(WeiboLinkKind.Live, liveHit?.kind)

        val voteHit = extractWeiboLinkFromText("投票 https://vote.weibo.com/h5/index/index?vote_id=2026_12345")
        assertEquals(WeiboLinkKind.Vote, voteHit?.kind)

        val shortHit = extractWeiboLinkFromText("看看 t.cn/A6x12345 分享")
        assertEquals(WeiboLinkKind.ShortLink, shortHit?.kind)

        // 首页、第三方普通网址、纯中文文本均不提示
        assertNull(extractWeiboLinkFromText("https://weibo.com/"))
        assertNull(extractWeiboLinkFromText("https://github.com/Costben/COBO"))
        assertNull(extractWeiboLinkFromText("https://www.bilibili.com/video/BV1xx411c7mD"))
        assertNull(extractWeiboLinkFromText("一段没有任何链接的文字"))
    }

    @Test
    fun decideShareActionRoutesImagesLinksMixedTextAndGuestCorrectly() {
        val withImages = decideShareAction(
            IncomingShare(
                text = "看图 https://m.weibo.cn/detail/5012345678901234",
                imageUris = List(12) { "file:///cache/img_$it.jpg" },
            ),
            isGuest = false,
        )
        assertTrue(withImages is ShareAction.Compose)
        assertEquals(9, (withImages as ShareAction.Compose).imageUris.size)

        val pureUrl = decideShareAction(
            IncomingShare(text = "  https://m.weibo.cn/detail/5012345678901234  "),
            isGuest = false,
        )
        assertTrue(pureUrl is ShareAction.OpenWeiboLink)

        val mixedLoggedIn = decideShareAction(
            IncomingShare(text = "分享给你：https://m.weibo.cn/detail/5012345678901234"),
            isGuest = false,
        )
        assertTrue(mixedLoggedIn is ShareAction.AskWeiboLinkOrCompose)

        val mixedGuest = decideShareAction(
            IncomingShare(text = "分享给你：https://m.weibo.cn/detail/5012345678901234"),
            isGuest = true,
        )
        assertTrue(mixedGuest is ShareAction.OpenWeiboLink)

        val plainText = decideShareAction(IncomingShare(text = "记录今天的心情"), isGuest = false)
        assertEquals(ShareAction.Compose("记录今天的心情", emptyList()), plainText)

        assertEquals(ShareAction.Empty, decideShareAction(IncomingShare(text = "   "), isGuest = false))
    }
}
