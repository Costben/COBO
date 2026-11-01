package com.cobo.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LivePhotoParseTest {

    @Test
    fun unwrapsTheMovInsideThePlayPage() {
        val status = JSONObject(
            """
            {
              "id": "1",
              "pics": [
                {
                  "pid": "a",
                  "url": "https://wx1.sinaimg.cn/orj360/a.jpg",
                  "type": "livephoto",
                  "videoSrc": "https://video.weibo.com/media/play?livephoto=https%3A%2F%2Flivephoto.us.sinaimg.cn%2Fa.mov",
                  "large": { "url": "https://wx1.sinaimg.cn/large/a.jpg", "geo": { "width": 2048, "height": 1536 } }
                },
                {
                  "pid": "b",
                  "url": "https://wx1.sinaimg.cn/orj360/b.jpg",
                  "large": { "url": "https://wx1.sinaimg.cn/large/b.jpg" }
                }
              ]
            }
            """.trimIndent(),
        )
        val parsed = Status.from(status)!!
        assertEquals(2, parsed.pics.size)
        assertTrue(parsed.pics[0].isLive)
        assertEquals(
            "https://video.weibo.com/media/play?livephoto=https%3A%2F%2Flivephoto.us.sinaimg.cn%2Fa.mov",
            parsed.pics[0].live,
        )
        assertEquals("https://wx1.sinaimg.cn/large/a.jpg", parsed.pics[0].large)
        assertEquals(2048, parsed.pics[0].width)
        assertEquals(1536, parsed.pics[0].height)
        assertFalse(parsed.pics[1].isLive)
        assertEquals("", parsed.pics[1].live)
    }

    @Test
    fun readsPicInfosAndLeavesGifsStill() {
        val status = JSONObject(
            """
            {
              "id": "2",
              "pic_ids": ["live", "gif"],
              "pic_infos": {
                "live": {
                  "type": "livephoto",
                  "video": "https://livephoto.us.sinaimg.cn/c.mov",
                  "largest": { "url": "https://wx1.sinaimg.cn/large/c.jpg" }
                },
                "gif": {
                  "type": "gif",
                  "video": "https://wx1.sinaimg.cn/large/d.mp4",
                  "largest": { "url": "https://wx1.sinaimg.cn/large/d.gif" }
                }
              }
            }
            """.trimIndent(),
        )
        val pics = Status.from(status)!!.pics
        assertEquals(
            "https://video.weibo.com/media/play?livephoto=https%3A%2F%2Flivephoto.us.sinaimg.cn%2Fc.mov",
            pics[0].live,
        )
        assertEquals("https://wx1.sinaimg.cn/large/d.gif", pics[1].large)
        assertFalse(pics[1].isLive)
    }

    @Test
    fun keepsADirectMovAndAnHttpScheme() {
        val direct = JSONObject(
            """{"type":"livephoto","url":"https://wx1.sinaimg.cn/large/e.jpg","videoSrc":"//livephoto.us.sinaimg.cn/e.mov"}""",
        )
        assertEquals(
            "https://video.weibo.com/media/play?livephoto=https%3A%2F%2Flivephoto.us.sinaimg.cn%2Fe.mov",
            liveVideoUrl(direct),
        )
        val signed = JSONObject(
            """{"type":"livephoto","url":"https://wx1.sinaimg.cn/large/f.jpg","video":"https://livephoto.us.sinaimg.cn/f.mov?Expires=1&ssig=abc&KID=unistore,video"}""",
        )
        assertEquals(
            "https://livephoto.us.sinaimg.cn/f.mov?Expires=1&ssig=abc&KID=unistore,video",
            liveVideoUrl(signed),
        )
    }
}
