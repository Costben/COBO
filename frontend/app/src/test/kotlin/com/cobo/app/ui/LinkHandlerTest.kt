package com.cobo.app.ui

import org.junit.Assert.assertFalse
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
}
