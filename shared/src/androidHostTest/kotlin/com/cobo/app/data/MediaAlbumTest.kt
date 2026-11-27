package com.cobo.app.data

import com.cobo.app.json.JSONArray
import com.cobo.app.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaAlbumTest {

    private fun status(
        id: String,
        pics: List<Pic> = emptyList(),
        video: VideoInfo? = null,
        bigPic: BigPicInfo? = null,
        retweeted: Status? = null,
    ) = Status(
        id = id,
        mid = id,
        textHtml = "微博 $id",
        createdAt = "Mon Nov 17 10:00:00 +0800 2026",
        source = "",
        user = User(id = "u1", name = "测试", avatar = ""),
        pics = pics,
        video = video,
        bigPic = bigPic,
        retweeted = retweeted,
    )

    @Test
    fun extractsPhotosLivePhotosVideosAndMixMediaInOrder() {
        val posts = listOf(
            status(
                id = "10",
                pics = listOf(
                    Pic("t1", "l1", pid = "p1"),
                    Pic("t2", "l2", live = "https://video.weibo.com/media/play?livephoto=1.mov", pid = "p2"),
                    Pic("vt", "vl", videoUrl = "https://v.example.com/1.mp4", durationSec = 42, videoFid = "1034:1"),
                ),
            ),
            status(
                id = "20",
                video = VideoInfo(title = "独立视频", cover = "c1", url = "https://v.example.com/2.mp4", durationSec = 125),
            ),
            status(
                id = "30",
                bigPic = BigPicInfo(url = "https://img.example.com/big.jpg"),
            ),
        )

        val all = extractMediaWallItems(posts, includeRetweeted = false, filter = MediaWallFilter.All)
        assertEquals(5, all.size)
        assertFalse(all[0].isVideo)
        assertFalse(all[0].isLivePhoto)
        assertTrue(all[1].isLivePhoto)
        assertTrue(all[2].isVideo)
        assertEquals(42, all[2].durationSec)
        assertEquals("10", (all[2] as MediaWallItem.Video).video.statusId)
        assertTrue(all[3].isVideo)
        assertEquals("20", (all[3] as MediaWallItem.Video).video.statusId)
        assertEquals("https://img.example.com/big.jpg", all[4].thumbUrl)

        val imagesOnly = extractMediaWallItems(posts, includeRetweeted = false, filter = MediaWallFilter.Images)
        assertEquals(3, imagesOnly.size)
        assertTrue(imagesOnly.none { it.isVideo })

        val videosOnly = extractMediaWallItems(posts, includeRetweeted = false, filter = MediaWallFilter.Videos)
        assertEquals(2, videosOnly.size)
        assertTrue(videosOnly.all { it.isVideo })
    }

    @Test
    fun includeRetweetedControlsQuotedMediaAndDedupesSameUrl() {
        val origin = status(
            id = "origin",
            pics = listOf(
                Pic("qt", "ql", pid = "same_pid"),
                Pic("qt2", "ql2", pid = "other_pid"),
            ),
        )
        val wrapper = status(
            id = "wrapper",
            pics = listOf(Pic("wt", "wl", pid = "same_pid")),
            retweeted = origin,
        )

        val profileWall = extractMediaWallItems(listOf(wrapper), includeRetweeted = false)
        assertEquals(1, profileWall.size)

        val favoritesWall = extractMediaWallItems(listOf(wrapper), includeRetweeted = true)
        assertEquals(2, favoritesWall.size)
    }

    @Test
    fun galleryMappingAndDurationFormattingWorkCleanly() {
        val posts = listOf(
            status(
                id = "1",
                pics = listOf(
                    Pic("t1", "l1", pid = "a"),
                    Pic("vt", "vl", videoUrl = "https://v.example.com/v.mp4", videoFid = "1034:9"),
                    Pic("t2", "l2", pid = "b"),
                ),
            ),
        )
        val wall = extractMediaWallItems(posts, includeRetweeted = false)
        val (gallery, indexMap) = mediaWallPhotoGallery(wall)
        assertEquals(2, gallery.size)
        assertEquals(0, indexMap[wall[0].key])
        assertEquals(null, indexMap[wall[1].key])
        assertEquals(1, indexMap[wall[2].key])

        assertEquals("", formatAlbumDuration(0))
        assertEquals("0:05", formatAlbumDuration(5))
        assertEquals("1:05", formatAlbumDuration(65))
        assertEquals("1:01:01", formatAlbumDuration(3661))
    }

    @Test
    fun duplicateStatusIdsProduceUniqueGridKeys() {
        val dup = status(id = "99", pics = listOf(Pic("t", "l", pid = "x")))
        val wall = extractMediaWallItems(listOf(dup, dup), includeRetweeted = false)
        assertEquals(2, wall.size)
        assertEquals(2, wall.map { it.key }.distinct().size)
    }

    @Test
    fun parseProfilePhotoAllPageHandlesCard47AndLivePhotoMetadata() {
        val root = JSONObject().put(
            "data",
            JSONObject()
                .put("cardlistInfo", JSONObject().put("page", 2))
                .put(
                    "cards",
                    JSONArray().put(
                        JSONObject()
                            .put("card_type", 47)
                            .put(
                                "pics",
                                JSONArray()
                                    .put(
                                        JSONObject()
                                            .put("pic_id", "pid_live_1")
                                            .put("pic_small", "https://wx1.sinaimg.cn/orj480/pid_live_1.jpg")
                                            .put("pic_big", "https://wx1.sinaimg.cn/woriginal/pid_live_1.jpg")
                                            .put(
                                                "mblog",
                                                JSONObject()
                                                    .put("id", "5001")
                                                    .put("mid", "5001")
                                                    .put("text", "实况图微博")
                                                    .put("pic_ids", JSONArray().put("pid_live_1"))
                                                    .put(
                                                        "pic_infos",
                                                        JSONObject().put(
                                                            "pid_live_1",
                                                            JSONObject()
                                                                .put("type", "livephoto")
                                                                .put("video", "https://video.weibo.com/media/play?livephoto=live1.mov")
                                                                .put("bmiddle", JSONObject().put("url", "https://wx1.sinaimg.cn/orj360/pid_live_1.jpg"))
                                                                .put("large", JSONObject().put("url", "https://wx1.sinaimg.cn/large/pid_live_1.jpg")),
                                                        ),
                                                    ),
                                            ),
                                    )
                                    .put(
                                        JSONObject()
                                            .put("pic_id", "pid_plain_2")
                                            .put("pic_small", "https://wx2.sinaimg.cn/orj480/pid_plain_2.jpg")
                                            .put("pic_big", "https://wx2.sinaimg.cn/woriginal/pid_plain_2.jpg")
                                            .put("mid", "5002"),
                                    ),
                            ),
                    ),
                ),
        )

        val page = parseProfilePhotoAllPage(root, currentPage = 1, cursorPrefix = "mpa")
        assertEquals(2, page.items.size)
        assertEquals("mpa_p:2", page.nextCursor)
        val first = page.items[0] as MediaWallItem.Photo
        assertTrue(first.isLivePhoto)
        assertEquals("5001", first.status.id)
        val second = page.items[1] as MediaWallItem.Photo
        assertFalse(second.isLivePhoto)
        assertEquals("5002", second.status.id)
        assertEquals("https://wx2.sinaimg.cn/woriginal/pid_plain_2.jpg", second.pic.large)
    }

    @Test
    fun parseProfilePhotoAllPageSupportsSinceIdAndPicItems() {
        val root = JSONObject()
            .put("cardlistInfo", JSONObject().put("since_id", "5367000590843952"))
            .put(
                "cards",
                JSONArray().put(
                    JSONObject()
                        .put("card_type", 47)
                        .put(
                            "pic_items",
                            JSONArray().put(
                                JSONObject()
                                    .put("pic_id", "pid_client_1")
                                    .put("pic_small", "https://wx1.sinaimg.cn/orj480/pid_client_1.jpg")
                                    .put("pic_big", "https://wx1.sinaimg.cn/large/pid_client_1.jpg")
                                    .put("mid", "6001"),
                            ),
                        ),
                ),
            )

        val page = parseProfilePhotoAllPage(root, currentPage = 1, cursorPrefix = "cpa")
        assertEquals(1, page.items.size)
        assertEquals("cpa_s:5367000590843952", page.nextCursor)
        assertEquals("6001", page.items[0].status.id)
    }

    @Test
    fun multiVideoMixMediaStatusExtractsEveryVideoInPost() {
        val raw = JSONObject()
            .put("idstr", "7001")
            .put("text", "双视频混排微博")
            .put("user", JSONObject().put("idstr", "u1").put("screen_name", "测试"))
            .put(
                "mix_media_info",
                JSONObject().put(
                    "items",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("type", "video")
                                .put("id", "1034:7001001")
                                .put(
                                    "data",
                                    JSONObject()
                                        .put("title", "视频一")
                                        .put("page_pic", JSONObject().put("url", "https://img/v1.jpg"))
                                        .put("media_info", JSONObject().put("stream_url", "https://video/v1.mp4").put("duration", 15)),
                                ),
                        )
                        .put(
                            JSONObject()
                                .put("type", "video")
                                .put("id", "1034:7001002")
                                .put(
                                    "data",
                                    JSONObject()
                                        .put("title", "视频二")
                                        .put("page_pic", JSONObject().put("url", "https://img/v2.jpg")),
                                ),
                        ),
                ),
            )
        val parsed = Status.from(raw)!!
        val (videos, _) = extractProfileVideoWallItems("u1", FeedPage(listOf(parsed), "next"))
        assertEquals(2, videos.size)
        assertEquals("1034:7001001", (videos[0] as MediaWallItem.Video).video.fid)
        assertEquals("1034:7001002", (videos[1] as MediaWallItem.Video).video.fid)
    }

    @Test
    fun parseProfileImageWallPageExtractsPidsAndSinceId() {
        val root = JSONObject().put(
            "data",
            JSONObject()
                .put("since_id", "5361_cursor")
                .put(
                    "list",
                    JSONArray()
                        .put(JSONObject().put("pid", "001abc").put("mid", "9001").put("type", "pic"))
                        .put(JSONObject().put("pid", "001abc").put("mid", "9001").put("type", "pic"))
                        .put(JSONObject().put("pid", "002def").put("mid", "9002").put("type", "livephoto").put("video", "https://v.example.com/live.mov")),
                ),
        )

        val page = parseProfileImageWallPage(root)
        assertEquals(2, page.items.size)
        assertEquals("iw:5361_cursor", page.nextCursor)
        assertEquals("9001", page.items[0].status.id)
        assertTrue(page.items[1].isLivePhoto)
    }

    @Test
    fun extractProfileVideoWallItemsDropsForeignRecommendations() {
        val ownerVideo = status(
            id = "100",
            video = VideoInfo(title = "本人视频", cover = "c1", url = "https://v.example.com/1.mp4", fid = "1034:1", statusId = "100"),
        )
        val foreign1 = ownerVideo.copy(id = "101", mid = "101", user = User(id = "other1", name = "路人1", avatar = ""))
        val foreign2 = ownerVideo.copy(id = "102", mid = "102", user = User(id = "other2", name = "路人2", avatar = ""))

        val (items, next) = extractProfileVideoWallItems("u1", FeedPage(listOf(ownerVideo, foreign1, foreign2), "next_cursor"))
        assertEquals(1, items.size)
        assertEquals("100", items[0].status.id)
        assertNull(next)
    }

    @Test
    fun dedupTokenIgnoresChangingSignatureParamsAndCdnHosts() {
        val v1 = MediaWallItem.Video(
            key = "v1",
            status = status("1"),
            video = VideoInfo(
                title = "t",
                cover = "https://wx1.sinaimg.cn/orj480/c1.jpg?Expires=111",
                url = "https://f.video.weibocdn.com/o0/Wc2V7n5Ilx08srikZZyE01041200kjRU0E010.mp4?Expires=111&ssig=AAA",
            ),
        )
        val v2 = MediaWallItem.Video(
            key = "v2",
            status = status("1"),
            video = VideoInfo(
                title = "t",
                cover = "https://wx2.sinaimg.cn/orj480/c1.jpg?Expires=222",
                url = "https://g.us.sinaimg.cn/o0/Wc2V7n5Ilx08srikZZyE01041200kjRU0E010.mp4?Expires=222&ssig=BBB",
            ),
        )
        assertEquals(mediaWallDedupToken(v1), mediaWallDedupToken(v2))
    }

    @Test
    fun downgradedClientMultiVideoPostPromotesUrlStructVideosIntoGrid() {
        val raw = JSONObject()
            .put("idstr", "5225411539636791")
            .put("text", "帮我选一个发抖音 http://t.cn/v1 http://t.cn/v2")
            .put("user", JSONObject().put("idstr", "7420226325").put("screen_name", "2026接utd"))
            .put(
                "page_info",
                JSONObject()
                    .put("type", "video")
                    .put("object_id", "1034:5225411265888289")
                    .put("page_pic", JSONObject().put("url", "https://img/v1.jpg"))
                    .put("media_info", JSONObject().put("stream_url", "https://f.video.weibocdn.com/o0/v1.mp4").put("duration", 25)),
            )
            .put(
                "url_struct",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("url_title", "视频一")
                            .put("short_url", "http://t.cn/v1")
                            .put("ori_url", "sinaweibo://video/vvs?object_id=1034%3A5225411265888289"),
                    )
                    .put(
                        JSONObject()
                            .put("url_title", "视频二")
                            .put("short_url", "http://t.cn/v2")
                            .put("ori_url", "sinaweibo://video/vvs?object_id=1034%3A5225411366551579"),
                    ),
            )

        val parsed = Status.from(raw)!!
        assertNull(parsed.video)
        assertEquals(2, parsed.pics.size)
        assertTrue(parsed.pics.all { it.isVideo })
        assertEquals("1034:5225411265888289", parsed.pics[0].videoFid)
        assertEquals("1034:5225411366551579", parsed.pics[1].videoFid)
    }

    @Test
    fun mobileMultiVideoPicsDoNotDuplicatePrimaryPageInfoVideo() {
        val raw = JSONObject()
            .put("idstr", "5225411539636791")
            .put("text", "帮我选一个发抖音")
            .put(
                "pics",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("pid", "p1")
                            .put("url", "https://img/p1.jpg")
                            .put("type", "video")
                            .put("duration", 25)
                            .put("videoSrc", "https://f.video.weibocdn.com/o0/v1.mp4?Expires=1&ssig=a"),
                    )
                    .put(
                        JSONObject()
                            .put("pid", "p2")
                            .put("url", "https://img/p2.jpg")
                            .put("type", "video")
                            .put("duration", 26)
                            .put("videoSrc", "https://f.video.weibocdn.com/o0/v2.mp4?Expires=1&ssig=b"),
                    ),
            )
            .put(
                "page_info",
                JSONObject()
                    .put("type", "video")
                    .put("object_id", "1034:5225411265888289")
                    .put("page_pic", JSONObject().put("url", "https://img/p1.jpg"))
                    .put("media_info", JSONObject().put("stream_url", "https://f.video.weibocdn.com/o0/v1.mp4?Expires=2&ssig=c").put("duration", 25)),
            )

        val parsed = Status.from(raw)!!
        assertNull(parsed.video)
        assertEquals(2, parsed.pics.size)
        assertEquals("1034:5225411265888289", parsed.pics[0].videoFid)
    }
}
