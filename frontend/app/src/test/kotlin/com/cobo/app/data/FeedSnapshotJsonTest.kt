package com.cobo.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 时间线快照要在关掉应用之后原样填回来，所以 [Status] 里的每个字段都得能走一遍 JSON。
 * 少存一个字段，恢复出来的卡片就少一块（图、视频、转发、会员标识都算）。
 */
class FeedSnapshotJsonTest {

    @Test
    fun statusRoundTripKeepsEverythingTheCardDraws() {
        val original = Status(
            id = "5123456789012345",
            mid = "5123456789012345",
            textHtml = "<a href=\"https://m.weibo.cn/u/1\">@小明</a> 今天天气不错 <img alt=\"[笑]\">",
            createdAt = "Wed Nov 12 09:30:00 +0800 2025",
            source = "微博网页版",
            user = User(
                id = "1234567",
                name = "小明",
                avatar = "https://tvax1.sinaimg.cn/large/avatar.jpg",
                verified = true,
                following = true,
                description = "简介",
                followers = 12345,
                friends = 67,
                statuses = 890,
                specialFollow = true,
                mbtype = 12,
                mbrank = 5,
                svip = true,
                vvip = true,
            ),
            pics = listOf(
                Pic(
                    thumb = "https://wx1.sinaimg.cn/thumb/a.jpg",
                    large = "https://wx1.sinaimg.cn/large/a.jpg",
                    width = 1080,
                    height = 1440,
                ),
                Pic(
                    thumb = "https://wx1.sinaimg.cn/thumb/live.jpg",
                    large = "https://wx1.sinaimg.cn/large/live.jpg",
                    width = 720,
                    height = 1280,
                    live = "https://video.weibo.com/media/play?livephoto=live.mov",
                ),
                Pic(
                    thumb = "https://wx1.sinaimg.cn/thumb/vcover.jpg",
                    large = "https://wx1.sinaimg.cn/large/vcover.jpg",
                    width = 720,
                    height = 1280,
                    videoUrl = "https://f.video.weibocdn.com/mix.mp4?Expires=1760000000",
                    videoTitle = "混排视频",
                    durationSec = 18,
                    videoFid = "1034:5000999888",
                    videoStatusId = "5123456789012345",
                ),
            ),
            video = VideoInfo(
                title = "视频标题",
                cover = "https://wx1.sinaimg.cn/large/cover.jpg",
                url = "https://f.video.weibocdn.com/1.mp4",
                fid = "1034:5000123456",
                statusId = "5123456789012345",
            ),
            bigPic = BigPicInfo(
                url = "https://mu1.sinaimg.cn/original/bigpic.png",
                scheme = "https://m.weibo.cn/c/wbox?id=test",
            ),
            retweeted = Status(
                id = "5000000000000001",
                mid = "5000000000000001",
                textHtml = "被转发的正文",
                createdAt = "Tue Nov 11 08:00:00 +0800 2025",
                source = "iPhone",
                user = User(id = "7654321", name = "小红", avatar = ""),
                reposts = 1,
                comments = 2,
                attitudes = 3,
            ),
            reposts = 11,
            comments = 22,
            attitudes = 33,
            liked = true,
            region = "发布于 上海",
            editCount = 2,
            isVote = true,
            voteId = "2317161234567890",
        )

        val restored = statusFromSnapshot(statusToSnapshot(original))

        assertEquals(original, restored)
        assertEquals(Membership.Vvip, restored?.user?.membership)
        assertEquals(5, restored?.user?.mbrank)
        assertTrue(restored?.pics?.get(1)?.isLive == true)
        assertTrue(restored?.video?.url == original.video?.url)
        assertEquals(original.bigPic, restored?.bigPic)
        assertTrue(restored?.retweeted?.user?.name == "小红")
    }

    @Test
    fun statusWithoutOptionalPartsRoundTrips() {
        val original = Status(
            id = "42",
            mid = "42",
            textHtml = "只有文字",
            createdAt = "",
            source = "",
            user = null,
        )

        val restored = statusFromSnapshot(statusToSnapshot(original))

        assertEquals(original, restored)
        assertNull(restored?.video)
        assertNull(restored?.retweeted)
        assertTrue(restored?.pics.isNullOrEmpty())
    }

    @Test
    fun statusFromSnapshotRejectsRowsWithoutId() {
        assertNull(statusFromSnapshot(null))
        assertNull(statusFromSnapshot(org.json.JSONObject("{\"text\":\"没有 id\"}")))
    }
}
