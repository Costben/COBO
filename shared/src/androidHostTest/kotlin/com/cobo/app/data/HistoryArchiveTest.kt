package com.cobo.app.data

import com.cobo.app.json.JSONObject
import com.cobo.app.platform.PlatformContext
import com.cobo.app.platform.PlatformFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HistoryArchiveTest {

    private fun tempDir(prefix: String): PlatformFile {
        val dir = File.createTempFile(prefix, "").apply {
            delete()
            mkdirs()
        }
        return PlatformFile(dir.absolutePath)
    }

    private fun sampleStatus(
        id: String,
        text: String = "这是第 $id 条微博正文",
        picId: String = "006abc1234567890abcdef${id.padStart(4, '0')}",
    ): Status = Status(
        id = id,
        mid = id,
        textHtml = "<p>$text</p>",
        createdAt = "Sat Nov 28 19:00:00 +0800 2026",
        source = "iPhone 16 Pro",
        user = User(
            id = "u_$id",
            name = "作者$id",
            avatar = "https://tvax1.sinaimg.cn/crop.0.0.180.180/avatar_$id.jpg",
            verified = true,
            verifiedType = 0,
            following = true,
            followers = 12345,
        ),
        pics = listOf(
            Pic(
                thumb = "https://wx1.sinaimg.cn/orj360/$picId.jpg",
                large = "https://wx1.sinaimg.cn/large/$picId.jpg",
                width = 1080,
                height = 1440,
                live = "https://video.weibo.com/media/play?livephoto=https://livephoto.us.sinaimg.cn/live_$id.mov",
                pid = picId,
            ),
            Pic(
                thumb = "https://wx1.sinaimg.cn/orj360/vidcover_$id.jpg",
                large = "https://wx1.sinaimg.cn/large/vidcover_$id.jpg",
                videoUrl = "https://f.video.weibocdn.com/embed_$id.mp4",
                videoTitle = "混排视频",
                durationSec = 18,
                videoFid = "1034:$id",
            ),
        ),
        video = VideoInfo(
            title = "主视频",
            cover = "https://wx1.sinaimg.cn/large/vcover_$id.jpg",
            url = "https://f.video.weibocdn.com/main_$id.mp4",
            durationSec = 42,
        ),
        bigPic = BigPicInfo(
            url = "https://wx1.sinaimg.cn/large/big_$id.jpg",
            scheme = "https://weibo.com/article/$id",
            title = "外链文章",
        ),
        retweeted = Status(
            id = "rt_$id",
            mid = "rt_$id",
            textHtml = "被转发的原博内容",
            createdAt = "",
            source = "",
            user = User(id = "rt_u", name = "原博作者", avatar = ""),
        ),
        reposts = 88,
        comments = 99,
        attitudes = 123,
        liked = true,
        favorited = true,
        region = "发布于 四川",
        editCount = 2,
        isVote = true,
        voteId = "vote_$id",
    )

    @Test
    fun sanitizeStatusForHistory_keepsOnlyTextAndStillImages() {
        val original = sampleStatus("1001")
        val sanitized = sanitizeStatusForHistory(original)

        assertEquals("1001", sanitized.id)
        assertEquals("<p>这是第 1001 条微博正文</p>", sanitized.textHtml)
        assertEquals("作者1001", sanitized.user?.name)
        assertEquals("发布于 四川", sanitized.region)

        // 只保留静态图，剔除混排视频与实况视频轨
        assertEquals(1, sanitized.pics.size)
        val pic = sanitized.pics.first()
        assertFalse(pic.isVideo)
        assertFalse(pic.isLive)
        assertEquals("", pic.live)
        assertEquals("", pic.videoUrl)
        assertEquals(0, pic.durationSec)
        assertEquals(1080, pic.width)
        assertEquals(1440, pic.height)

        // 严禁保留视频、转发原博、评论/转发计数、外链卡片和投票
        assertNull(sanitized.video)
        assertNull(sanitized.retweeted)
        assertNull(sanitized.bigPic)
        assertEquals(0, sanitized.comments)
        assertEquals(0, sanitized.reposts)
        assertEquals(0, sanitized.attitudes)
        assertFalse(sanitized.liked)
        assertFalse(sanitized.favorited)
        assertEquals(0, sanitized.editCount)
        assertFalse(sanitized.isVote)
        assertNull(sanitized.voteId)
    }

    @Test
    fun store_capsAt100Items_andPrunesEvictedLocalImages() {
        val root = tempDir("cobo-history-cap-")
        try {
            val store = Store(PlatformContext(), baseDir = root)
            val imagesDir = store.historyImagesDir().apply { mkdirs() }

            // 写入 105 条看过记录，并为每条写入模拟的本地图片文件
            val fileNames = LinkedHashMap<String, String>()
            for (i in 1..105) {
                val id = i.toString()
                val saved = store.saveHistoryStatus(sampleStatus(id), nowMs = 1_000_000L + i)
                val fileName = saved.localImages.single()
                fileNames[id] = fileName
                assertTrue(imagesDir.child(fileName).writeBytes(byteArrayOf(1, 2, 3, 4)))
            }

            // 再新增第 106 条，确保触发一次清理，保留 7..106 共 100 条
            val saved106 = store.saveHistoryStatus(sampleStatus("106"), nowMs = 2_000_000L)
            val file106 = saved106.localImages.single()
            assertTrue(imagesDir.child(file106).writeBytes(byteArrayOf(9, 9)))

            val history = store.history()
            assertEquals(100, history.size)
            assertEquals("106", history.first().id)
            assertEquals("7", history.last().id)

            // 被淘汰的 1..6 对应本地图片已被回收，保留的 7..106 依然存在
            for (i in 1..6) {
                val evictedFile = imagesDir.child(fileNames[i.toString()]!!)
                assertFalse("Evicted item $i image should be pruned", evictedFile.exists())
            }
            for (i in 7..105) {
                val keptFile = imagesDir.child(fileNames[i.toString()]!!)
                assertTrue("Retained item $i image should remain", keptFile.exists())
            }
            assertTrue(imagesDir.child(file106).exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun store_markHistoryDeleted_preservesOrderSnapshotAndLocalImages() {
        val root = tempDir("cobo-history-del-")
        try {
            val store = Store(PlatformContext(), baseDir = root)
            val imagesDir = store.historyImagesDir().apply { mkdirs() }

            val item1 = store.saveHistoryStatus(sampleStatus("101"), nowMs = 1000L)
            val item2 = store.saveHistoryStatus(sampleStatus("102"), nowMs = 2000L)
            val img1 = imagesDir.child(item1.localImages.single())
            assertTrue(img1.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())))

            assertTrue(store.markHistoryDeleted("101"))
            val list = store.history()
            // 顺序不跳动：102 仍在前，101 仍在后
            assertEquals(listOf("102", "101"), list.map { it.id })
            assertFalse(list[0].deleted)
            assertTrue(list[1].deleted)
            assertNotNull(list[1].snapshot)

            val resolved = resolveHistoryStatus(list[1], imagesDir)
            assertNotNull(resolved)
            assertEquals(1, resolved!!.pics.size)
            assertEquals(img1.path, resolved.pics.first().large)
            assertEquals(img1.path, resolved.pics.first().thumb)
            assertTrue(resolved.pics.first().preserveUrl)
            assertNull(resolved.video)
            assertNull(resolved.retweeted)

            // 单条删除与清空时同步清理本地图片
            store.removeHistory(item2.id)
            assertTrue(img1.exists())
            store.clearHistory()
            assertTrue(store.history().isEmpty())
            assertFalse(img1.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun resolveHistoryStatus_fallsBackToRemoteUrlWhenLocalImageMissing() {
        val root = tempDir("cobo-history-fallback-")
        try {
            val item = buildHistoryItem(sampleStatus("201"), nowMs = 5000L)
            val resolved = resolveHistoryStatus(item, root)
            assertNotNull(resolved)
            val pic = resolved!!.pics.single()
            assertTrue(pic.large.startsWith("https://"))
            assertFalse(pic.preserveUrl)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun deletedStatusDetection_recognizesWeiboDeletedSignalsAndIgnoresNetworkErrors() {
        assertTrue(isDeletedStatusThrowable(StatusDeletedException("该微博已被删除")))
        assertTrue(isDeletedStatusThrowable(ApiException("target weibo does not exist", "20101")))
        assertTrue(isDeletedStatusThrowable(ApiException("抱歉，此微博已被作者删除", "20130")))
        assertTrue(isDeletedStatusThrowable(ApiException("微博不存在或暂无查看权限!")))
        assertTrue(isDeletedStatusThrowable(ApiHttpException(404, "Not Found")))
        assertTrue(isDeletedStatusThrowable(ApiHttpException(400, """{"errno":20101,"errmsg":"target weibo does not exist"}""")))

        assertTrue(isDeletedStatusJson(JSONObject("""{"deleted":"1","id":"123"}""")))
        assertTrue(isDeletedStatusJson(JSONObject("""{"ok":0,"msg":"微博不存在或暂无查看权限!"}""")))
        assertTrue(isDeletedStatusJson(JSONObject("""{"errno":"20101","errmsg":"target weibo does not exist"}""")))
        assertTrue(isDeletedStatusJson(JSONObject("""{"id":"123","text":"抱歉，此微博已被作者删除。"}""")))

        // 普通网络/鉴权错误绝不能误判为已删除
        assertFalse(isDeletedStatusThrowable(ApiException("请求过于频繁，请稍后重试", "20016")))
        assertFalse(isDeletedStatusThrowable(ApiException("登录已过期", "-100")))
        assertFalse(isDeletedStatusThrowable(ApiHttpException(502, "Bad Gateway")))
        assertFalse(isDeletedStatusThrowable(IllegalStateException("timeout")))
        assertFalse(
            isDeletedStatusJson(
                JSONObject(
                    """{"id":"123","text":"讨论一下已删除微博的话题","user":{"id":"9","screen_name":"Alice"}}""",
                ),
            ),
        )
    }

    @Test
    fun localBackup_roundTripsEnrichedHistorySnapshotAndDeletedFlag() {
        val original = buildHistoryItem(sampleStatus("301"), nowMs = 9_999L, deleted = true)
        val bundle = LocalBackupBundle(
            exportedAt = 10_000L,
            appVersion = "1.2.12-pre",
            history = listOf(original),
        )
        val encoded = encodeLocalBackupJson(bundle)
        val decoded = decodeLocalBackupJson(encoded)
        val restored = decoded.history?.single()
        assertNotNull(restored)
        assertEquals("301", restored!!.id)
        assertTrue(restored.deleted)
        assertNotNull(restored.snapshot)
        assertEquals(1, restored.snapshot!!.pics.size)
        assertNull(restored.snapshot!!.video)
        assertNull(restored.snapshot!!.retweeted)
    }
}
