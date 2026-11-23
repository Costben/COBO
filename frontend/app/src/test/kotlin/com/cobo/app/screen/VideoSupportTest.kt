package com.cobo.app.screen

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoSupportTest {

    @Test
    fun extractsWeiboFidAndBilibiliBvid() {
        assertEquals(
            "1034:5000123456",
            extractWeiboVideoFid("https://video.weibo.com/show?fid=1034:5000123456"),
        )
        assertEquals(
            "1034:5000123456",
            extractWeiboVideoFid("https://weibo.com/tv/show/1034:5000123456?from=old_pc"),
        )
        assertEquals(
            "BV1xx411c7mD",
            extractBilibiliBvid("https://www.bilibili.com/video/BV1xx411c7mD/?p=1"),
        )
        assertNull(extractBilibiliBvid("https://www.iqiyi.com/v_19rr7abcde.html"))
    }

    @Test
    fun extractsHtmlVideoUrlFromMetaAndVideoTag() {
        val html = """
            <html><head>
            <meta property="og:video:url" content="https://cdn.example.com/video/sample.mp4?a=1&amp;b=2" />
            </head><body></body></html>
        """.trimIndent()
        assertEquals(
            "https://cdn.example.com/video/sample.mp4?a=1&b=2",
            extractHtmlVideoUrl(html, "https://m.example.com/play"),
        )
    }

    @Test
    fun parsesM3u8MasterAndSegmentPlaylists() {
        val master = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=800000
            low/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2400000
            high/index.m3u8
        """.trimIndent()
        val parsedMaster = parseM3u8Playlist(master, "https://cdn.example.com/v/master.m3u8")
        assertEquals("https://cdn.example.com/v/high/index.m3u8", parsedMaster.variantUrl)

        val media = """
            #EXTM3U
            #EXT-X-KEY:METHOD=AES-128,URI="key.bin",IV=0x01020304
            seg0.ts
            seg1.ts
        """.trimIndent()
        val parsedMedia = parseM3u8Playlist(media, "https://cdn.example.com/v/high/index.m3u8")
        assertEquals("AES-128", parsedMedia.keyMethod)
        assertEquals("https://cdn.example.com/v/high/key.bin", parsedMedia.keyUri)
        assertEquals("01020304", parsedMedia.keyIvHex)
        assertEquals(
            listOf(
                "https://cdn.example.com/v/high/seg0.ts",
                "https://cdn.example.com/v/high/seg1.ts",
            ),
            parsedMedia.segments,
        )
    }

    @Test
    fun detectsIqiyiM3u8StreamAndWeiboReferer() {
        val iqiyiTmts = "https://cache.m.iqiyi.com/tmts/5764865138999700/abc/?type=m3u8&qdv=1"
        assertTrue(isM3u8Stream(iqiyiTmts))
        assertTrue(isSniffableMediaUrl(iqiyiTmts))
        assertFalse(isDirectVideoStream(iqiyiTmts))
        assertTrue(isDirectVideoStream(java.io.File("/tmp/sample.ts").toURI().toString()))
        assertEquals(
            "https://m.weibo.cn/",
            defaultRefererFor("https://f.video.weibocdn.com/o0/abc.mp4?Expires=1"),
        )
        assertNull(defaultRefererFor("https://cdn.example.com/video.mp4"))
    }

    @Test
    fun detectsCoverCropBandDarknessPerBand() {
        val darkArgb = 0xFF1E1E1E.toInt()
        val whiteArgb = 0xFFFFFFFF.toInt()
        val splitPixel: (Int, Int) -> Int = { _, y -> if (y < 50) whiteArgb else darkArgb }
        assertFalse(
            isCoverCropBandDark(
                width = 100,
                height = 100,
                pixelAt = splitPixel,
                containerWidthPx = 100f,
                containerHeightPx = 100f,
                bandTopPx = 0f,
                bandBottomPx = 30f,
            ),
        )
        assertTrue(
            isCoverCropBandDark(
                width = 100,
                height = 100,
                pixelAt = splitPixel,
                containerWidthPx = 100f,
                containerHeightPx = 100f,
                bandTopPx = 60f,
                bandBottomPx = 95f,
            ),
        )
    }

    @Test
    fun formatsVideoTimestamps() {
        assertEquals("00:00", formatVideoTime(-500L))
        assertEquals("00:05", formatVideoTime(5_400L))
        assertEquals("03:05", formatVideoTime(185_000L))
        assertEquals("1:01:05", formatVideoTime(3_665_000L))
    }

    @Test
    fun calculatesHorizontalScrubTargetWithinBounds() {
        // 60s video dragged halfway right (+500 / 1000) -> +30s
        assertEquals(40_000L, calculateHorizontalScrubTargetMs(10_000L, 60_000L, 500f, 1000f))
        // 300s video caps full-width span at 90s -> +45s for half screen
        assertEquals(55_000L, calculateHorizontalScrubTargetMs(10_000L, 300_000L, 500f, 1000f))
        // Clamps to [0, duration]
        assertEquals(0L, calculateHorizontalScrubTargetMs(5_000L, 60_000L, -500f, 1000f))
        assertEquals(60_000L, calculateHorizontalScrubTargetMs(50_000L, 60_000L, 500f, 1000f))
    }

    @Test
    fun parsesWeiboLiveRoomPayloadsAcrossStatuses() {
        val id = "1022:2321325160873118269706"
        val activeJson = JSONObject(
            """
            {
              "code": 100000,
              "data": {
                "item": {
                  "title": "晚间新闻直播",
                  "status": 1,
                  "watch_limit_info": {"type": 0},
                  "stream_info": {
                    "pull": {
                      "live_origin_hls_url": "http://al.pull.live.weibo.com/live/room.m3u8",
                      "live_origin_flv_url": "http://plwb02.live.weibo.com/alicdn/room.flv"
                    }
                  }
                }
              }
            }
            """.trimIndent(),
        )
        val active = parseWeiboLiveRoomJson(activeJson, id) as ResolvedVideo.DirectStream
        assertTrue(active.isLive)
        assertEquals("晚间新闻直播", active.title)
        assertEquals("https://al.pull.live.weibo.com/live/room.m3u8", active.url)
        assertEquals("https://plwb02.live.weibo.com/alicdn/room.flv", active.fallbackUrl)

        val replayJson = JSONObject(
            """
            {
              "code": 100000,
              "data": {
                "status": 3,
                "replay_origin_url": "http://live.video.weibocdn.com/replay_index.m3u8",
                "user_info": {"screen_name": "主播A"}
              }
            }
            """.trimIndent(),
        )
        val replay = parseWeiboLiveRoomJson(replayJson, id) as ResolvedVideo.DirectStream
        assertFalse(replay.isLive)
        assertEquals("主播A的直播", replay.title)
        assertEquals("https://live.video.weibocdn.com/replay_index.m3u8", replay.url)

        val notStartedJson = JSONObject(
            """
            {
              "code": 100000,
              "data": {
                "status": 0,
                "title": "预告直播"
              }
            }
            """.trimIndent(),
        )
        val unavailable = parseWeiboLiveRoomJson(notStartedJson, id) as ResolvedVideo.Unavailable
        assertEquals("直播未开始", unavailable.message)
        assertEquals("https://weibo.com/l/wblive/m/show/$id", unavailable.webUrl)

        val appOnlyJson = JSONObject(
            """
            {
              "code": 100000,
              "data": {
                "status": 1,
                "watch_limit_info": {"type": 1}
              }
            }
            """.trimIndent(),
        )
        val restricted = parseWeiboLiveRoomJson(appOnlyJson, id) as ResolvedVideo.Unavailable
        assertEquals("该直播需在微博官方客户端观看", restricted.message)

        val notExistsJson = JSONObject(
            """
            {
              "code": 999999,
              "msg": "LiveRoom does not exists! ($id).",
              "error_code": 27401,
              "data": []
            }
            """.trimIndent(),
        )
        val missing = parseWeiboLiveRoomJson(notExistsJson, id) as ResolvedVideo.Unavailable
        assertEquals("该直播已结束或直播间已关闭", missing.message)
    }
}
