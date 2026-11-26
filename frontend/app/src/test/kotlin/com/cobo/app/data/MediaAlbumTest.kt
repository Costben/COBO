package com.cobo.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
