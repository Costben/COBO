package com.cobo.app.ui

import com.cobo.app.data.TopicChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TopicLinkTest {

    @Test
    fun parsesMobileSearchTopicQuery() {
        val url100103 = "https://m.weibo.cn/search?containerid=100103type%3D1%26q%3D%23%E6%97%A9%E5%AE%89%23"
        assertEquals("#早安#", weiboTopicQuery(url100103))

        val url231522 =
            "https://m.weibo.cn/search?containerid=231522type%3D1%26t%3D10%26q%3D%23%E5%BE%AE%E5%8D%9A%23&extparam=%23%E5%BE%AE%E5%8D%9A%23&luicode=10000011"
        assertEquals("#微博#", weiboTopicQuery(url231522))

        val unencodedHash = "https://m.weibo.cn/search?containerid=231522type=1&t=10&q=#今日话题#&luicode=10000011"
        assertEquals("#今日话题#", weiboTopicQuery(unencodedHash))

        val extparamOnly = "https://m.weibo.cn/p/index?containerid=231522type%3D1&extparam=%23%E7%A7%91%E6%8A%80%23"
        assertEquals("#科技#", weiboTopicQuery(extparamOnly))
    }

    @Test
    fun parsesLegacyKPathAndSWeiboTopic() {
        assertEquals("#今日话题#", weiboTopicQuery("https://m.weibo.cn/k/%E4%BB%8A%E6%97%A5%E8%AF%9D%E9%A2%98"))
        assertEquals("#科技#", weiboTopicQuery("https://s.weibo.com/weibo?q=%23%E7%A7%91%E6%8A%80%23"))
        assertEquals("#科技#", weiboTopicQuery("https://s.weibo.com/weibo/%23%E7%A7%91%E6%8A%80%23"))
        assertEquals("#话题#", weiboTopicQuery("sinaweibo://searchall?q=%23%E8%AF%9D%E9%A2%98%23"))
    }

    @Test
    fun ignoresSuperTopicLinks() {
        val superTopicUrl = "https://m.weibo.cn/p/index?extparam=%E5%8E%9F%E7%A5%9E&containerid=100808fc439dedbb06ca5fd858848e521b8716"
        assertNull(weiboTopicQuery(superTopicUrl))
    }

    @Test
    fun topicChannelsMatchWeiboOrderAndTypeCodes() {
        val pairs = TopicChannel.entries.map { it.label to it.type }
        assertEquals(
            listOf(
                "综合" to 1,
                "实时" to 61,
                "图片" to 63,
                "视频" to 64,
                "关注" to 62,
                "热门" to 60,
            ),
            pairs,
        )
    }
}
