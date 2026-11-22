package com.cobo.app.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MixMediaParseTest {
    @Test
    fun parsesMixMediaInfoWithPicsAndVideosIntoGrid() {
        val json = JSONObject().apply {
            put("idstr", "5001")
            put("text", "混排测试")
            put("mix_media_info", JSONObject().apply {
                put("items", JSONArray().apply {
                    put(JSONObject().apply {
                        put("type", "pic")
                        put("data", JSONObject().apply {
                            put("pic_id", "p1")
                            put("thumbnail", JSONObject().apply {
                                put("url", "https://img/p1_thumb.jpg")
                                put("width", 360)
                                put("height", 360)
                            })
                            put("large", JSONObject().apply {
                                put("url", "https://img/p1_large.jpg")
                                put("width", 1080)
                                put("height", 1080)
                            })
                        })
                    })
                    put(JSONObject().apply {
                        put("type", "video")
                        put("data", JSONObject().apply {
                            put("title", "测试视频")
                            put("page_pic", JSONObject().apply {
                                put("url", "https://img/v1_cover.jpg")
                                put("width", 720)
                                put("height", 1280)
                            })
                            put("media_info", JSONObject().apply {
                                put("mp4_720p_mp4", "https://video/v1_720.mp4")
                                put("duration", 125)
                            })
                        })
                    })
                })
            })
        }

        val status = Status.from(json)
        assertNotNull(status)
        assertNull(status!!.video)
        assertEquals(2, status.pics.size)

        assertFalse(status.pics[0].isVideo)
        assertEquals("https://img/p1_thumb.jpg", status.pics[0].thumb)
        assertEquals("https://img/p1_large.jpg", status.pics[0].large)

        assertTrue(status.pics[1].isVideo)
        assertEquals("https://img/v1_cover.jpg", status.pics[1].thumb)
        assertEquals("https://video/v1_720.mp4", status.pics[1].videoUrl)
        assertEquals("测试视频", status.pics[1].videoTitle)
        assertEquals(125, status.pics[1].durationSec)
        assertEquals("2:05", com.cobo.app.ui.components.formatVideoDuration(status.pics[1].durationSec))
        assertEquals("1:01:01", com.cobo.app.ui.components.formatVideoDuration(3661))
    }

    @Test
    fun singleVideoInMixMediaInfoRendersAsStandaloneVideo() {
        val json = JSONObject().apply {
            put("idstr", "5002")
            put("text", "单视频 mix_media_info")
            put("mix_media_info", JSONObject().apply {
                put("items", JSONArray().apply {
                    put(JSONObject().apply {
                        put("type", "video")
                        put("data", JSONObject().apply {
                            put("title", "单独视频")
                            put("page_pic", JSONObject().apply {
                                put("url", "https://img/single_cover.jpg")
                            })
                            put("media_info", JSONObject().apply {
                                put("stream_url", "https://video/single.mp4")
                            })
                        })
                    })
                })
            })
        }

        val status = Status.from(json)
        assertNotNull(status)
        assertTrue(status!!.pics.isEmpty())
        val video = status.video
        assertNotNull(video)
        assertEquals("https://video/single.mp4", video!!.url)
        assertEquals("https://img/single_cover.jpg", video.cover)
        assertEquals("单独视频", video.title)
    }

    @Test
    fun mergesPicsAndPageInfoVideoIntoMixedGrid() {
        val json = JSONObject().apply {
            put("idstr", "5003")
            put("text", "pics + page_info 视频")
            put("pics", JSONArray().apply {
                put(JSONObject().apply {
                    put("url", "https://img/p1.jpg")
                    put("large", JSONObject().apply {
                        put("url", "https://img/p1_l.jpg")
                    })
                })
            })
            put("page_info", JSONObject().apply {
                put("type", "video")
                put("title", "附带视频")
                put("page_pic", JSONObject().apply {
                    put("url", "https://img/page_cover.jpg")
                })
                put("media_info", JSONObject().apply {
                    put("mp4_hd_url", "https://video/page.mp4")
                })
            })
        }

        val status = Status.from(json)
        assertNotNull(status)
        assertNull(status!!.video)
        assertEquals(2, status.pics.size)
        assertFalse(status.pics[0].isVideo)
        assertTrue(status.pics[1].isVideo)
        assertEquals("https://img/page_cover.jpg", status.pics[1].thumb)
        assertEquals("https://video/page.mp4", status.pics[1].videoUrl)
        assertEquals("附带视频", status.pics[1].videoTitle)
    }
}
