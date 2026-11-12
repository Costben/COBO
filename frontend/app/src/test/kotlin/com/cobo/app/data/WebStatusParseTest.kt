package com.cobo.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * 关注分组那条流走的是 weibo.com 网页版，`page_info` 和 m.weibo.cn 长得不一样：
 * 视频是 `type=11` + `object_type=video`，不是 `type=video`。只认 type 的视频会掉成纯文字。
 */
class WebStatusParseTest {

    @Test
    fun parsesWebVideoByObjectType() {
        val status = JSONObject(
            """
            {
              "id": "5364298395682028",
              "mid": "5364298395682028",
              "text": "<img alt=\"[捂嘴哭]\" title=\"[捂嘴哭]\" src=\"https://face.t.sinajs.cn/x.png\" />一条视频",
              "created_at": "Thu Nov 12 05:17:44 +0800 2026",
              "source": "微博网页版",
              "region_name": "发布于 陕西",
              "user": {"id": "2213561393", "screen_name": "魔法师蛋小丁"},
              "page_info": {
                "type": "11",
                "object_type": "video",
                "page_title": "一段视频",
                "page_pic": {"url": "https://wx1.sinaimg.cn/orj480/cover.jpg"},
                "media_info": {
                  "stream_url": "https://f.video.weibocdn.com/o0/a.mp4?label=mp4_720p",
                  "stream_url_hd": "https://f.video.weibocdn.com/o0/b.mp4?label=mp4_hd"
                }
              }
            }
            """.trimIndent(),
        )

        val parsed = Status.from(status)!!

        assertEquals("魔法师蛋小丁", parsed.user?.name)
        assertEquals("发布于 陕西", parsed.region)
        assertEquals("微博网页版", parsed.source)
        val video = parsed.video
        assertNotNull(video)
        assertEquals("一段视频", video!!.title)
        assertEquals("https://f.video.weibocdn.com/o0/b.mp4?label=mp4_hd", video.url)
        assertEquals("https://wx1.sinaimg.cn/orj480/cover.jpg", video.cover)
    }

    @Test
    fun parsesWebPicturesFromPicInfos() {
        val status = JSONObject(
            """
            {
              "id": "1",
              "pic_ids": ["0041L2zSgy1a", "0041L2zSgy1b"],
              "pic_infos": {
                "0041L2zSgy1a": {
                  "largest": {"url": "https://wx1.sinaimg.cn/large/a.jpg", "geo": {"width": 1200, "height": 900}},
                  "thumbnail": {"url": "https://wx1.sinaimg.cn/thumb150/a.jpg"}
                },
                "0041L2zSgy1b": {
                  "largest": {"url": "https://wx1.sinaimg.cn/large/b.jpg"}
                }
              }
            }
            """.trimIndent(),
        )

        val pics = Status.from(status)!!.pics

        assertEquals(2, pics.size)
        assertEquals("https://wx1.sinaimg.cn/large/a.jpg", pics[0].large)
        assertEquals(1200, pics[0].width)
        assertEquals(900, pics[0].height)
        assertEquals("https://wx1.sinaimg.cn/large/b.jpg", pics[1].large)
    }
}
