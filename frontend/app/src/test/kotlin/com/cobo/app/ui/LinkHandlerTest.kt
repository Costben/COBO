package com.cobo.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkHandlerTest {

    @Test
    fun detectsWeiboWboxMiniProgramLinks() {
        assertTrue(
            isWeiboWboxLink(
                "https://m.weibo.cn/c/wbox?id=c0p9sg9tw9&page=pages%2Ffieldscore%2Fplayerdetail",
            ),
        )
        assertTrue(isWeiboWboxLink("https://wbox.h5.weibo.cn/c0p9sg9tw9?id=c0p9sg9tw9"))
        assertTrue(isWeiboWboxLink("sinaweibo://wbox?id=c0p9sg9tw9"))
        assertFalse(isWeiboWboxLink("https://m.weibo.cn/p/index?containerid=100808abc"))
        assertFalse(isWeiboWboxLink("https://weibo.com/12345/abcdef"))
        assertFalse(isWeiboWboxLink(""))
    }

    @Test
    fun detectsExternalAndWeiboVideoLinks() {
        assertTrue(isVideoLink("https://www.iqiyi.com/v_19rr7abcde.html"))
        assertTrue(isVideoLink("https://m.iqiyi.com/v_19rr7abcde.html?vfrm=pcw"))
        assertTrue(isVideoLink("https://www.bilibili.com/video/BV1xx411c7mD"))
        assertTrue(isVideoLink("https://v.qq.com/x/cover/mzc00200/a001.html"))
        assertTrue(isVideoLink("https://video.weibo.com/show?fid=1034:5000000001"))
        assertTrue(isVideoLink("https://weibo.com/tv/show/1034:5000000001"))
        assertTrue(isVideoLink("https://cdn.example.com/clip.mp4?token=1"))
        assertTrue(isVideoLink("sinaweibo://video/play?fid=1034:5000000001"))
        assertFalse(isVideoLink("https://m.weibo.cn/detail/123456"))
        assertFalse(isVideoLink("https://www.example.com/article/1"))
    }

    @Test
    fun parsesExternalWeiboDeepLinks() {
        assertEquals("P1234abcd", weiboStatusId("https://weibo.com/1669879400/P1234abcd"))
        assertEquals("5012345678901234", weiboStatusId("https://www.weibo.com/1669879400/5012345678901234?from=share"))
        assertEquals("O1a2b3c4d", weiboStatusId("https://m.weibo.cn/status/O1a2b3c4d"))
        assertEquals("P1234abcd", weiboStatusId("sinaweibo://detail?mblogid=P1234abcd"))
        assertNull(weiboStatusId("https://weibo.com/1669879400"))

        assertEquals("1669879400", weiboUserId("https://weibo.com/1669879400"))
        assertEquals("1669879400", weiboUserId("sinaweibo://userinfo?uid=1669879400"))

        assertTrue(isWeiboHomeLink("https://weibo.com/"))
        assertTrue(isWeiboHomeLink("sinaweibo://home"))
        assertFalse(isWeiboHomeLink("https://weibo.com/1669879400/P1234abcd"))

        assertEquals(
            "https://weibo.com/1669879400",
            unwrapWeiboBrowserUrl("sinaweibo://browser?url=https%3A%2F%2Fweibo.com%2F1669879400"),
        )
        assertNull(unwrapWeiboBrowserUrl("https://weibo.com/1669879400"))
    }

    @Test
    fun detectsExternalWebLinks() {
        assertTrue(isExternalWebLink("https://pan.quark.cn/s/abcdef123456"))
        assertTrue(isExternalWebLink("https://github.com/Costben/COBO"))
        assertTrue(isExternalWebLink("http://example.com/path?a=1"))
        assertFalse(isExternalWebLink("https://m.weibo.cn/status/123456"))
        assertFalse(isExternalWebLink("https://weibo.com/ttarticle/p/show?id=1"))
        assertFalse(isExternalWebLink("https://card.weibo.com/article/m/show/id/1"))
        assertFalse(isExternalWebLink("https://vote.weibo.com/h5/index/index?vote_id=1"))
        assertFalse(isExternalWebLink("sinaweibo://detail?id=1"))
        assertFalse(isExternalWebLink(""))
    }

    @Test
    fun parsesAndUnifiesWeiboLiveLinksAndIds() {
        val id = "1022:2321325160873118269706"
        val expectedWeb = "https://weibo.com/l/wblive/m/show/$id"

        val variants = listOf(
            "https://weibo.com/l/wblive/m/show/$id",
            "https://weibo.com/l/wblive/p/show/$id",
            "https://weibo.com/l/wblive/app/h5_compatible?live_id=$id",
            "https://live.weibo.com/show?id=$id",
            "https://wblive-out.api.weibo.com/2/wblive/room/play?id=$id",
            "sinaweibo://chatroom?container_id=2321325160873118269706&live_id=$id&livetype=wblive",
            "sinaweibo://chatroom?container_id=2321325160873118269706&livetype=wblive",
            "sinaweibo://liveloom?live_id=$id",
            "sinaweibo://browser?url=https%3A%2F%2Fweibo.com%2Fl%2Fwblive%2Fm%2Fshow%2F1022%3A2321325160873118269706",
            "https://m.weibo.cn/feature/openapp?scheme=sinaweibo%3A%2F%2Fchatroom%3Fcontainer_id%3D2321325160873118269706%26live_id%3D1022%253A2321325160873118269706%26livetype%3Dwblive",
            "https://weibo.cn/sinaurl?u=https%3A%2F%2Flive.weibo.com%2Fshow%3Fid%3D1022%253A2321325160873118269706",
            "intent://chatroom?live_id=$id#Intent;scheme=sinaweibo;package=com.sina.weibo;end",
        )
        for (url in variants) {
            assertTrue("Expected live link: $url", isWeiboLiveLink(url))
            assertFalse("Live link should not be flagged as plain VOD: $url", isVideoLink(url))
            assertEquals("Failed live_id for: $url", id, extractWeiboLiveId(url))
        }
        assertEquals(
            expectedWeb,
            resolveWeiboLink("sinaweibo://chatroom?container_id=2321325160873118269706&live_id=$id&livetype=wblive"),
        )
        assertEquals(
            expectedWeb,
            resolveWeiboLink("https://m.weibo.cn/feature/openapp?scheme=sinaweibo%3A%2F%2Fliveloom%3Flive_id%3D1022%253A2321325160873118269706"),
        )

        // Normal VODs and SuperTopics must never be misclassified as live
        assertFalse(isWeiboLiveLink("https://video.weibo.com/show?fid=1034:5000000001"))
        assertFalse(isWeiboLiveLink("https://m.weibo.cn/p/index?containerid=1008086c62abc"))
        assertNull(extractWeiboLiveId("https://video.weibo.com/show?fid=1034:5000000001"))
        assertNull(extractWeiboLiveId("https://m.weibo.cn/p/index?containerid=1008086c62abc"))
    }
}
