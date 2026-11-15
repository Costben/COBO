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
}
