package com.cobo.app.data

import com.cobo.app.json.JSONArray
import com.cobo.app.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ContainerPublishTest {

    @Test
    fun buildPublishPartsUsesContentVisibleAndOptionalMedia() {
        val textOnly = ContainerApi.buildPublishParts("hello world", visible = 1)
        assertEquals("hello world", textOnly["content"])
        assertEquals("1", textOnly["visible"])
        assertFalse(textOnly.containsKey("status"))
        assertFalse(textOnly.containsKey("pic_id"))
        assertFalse(textOnly.containsKey("media"))

        val withMedia = ContainerApi.buildPublishParts(
            text = "带图微博",
            visible = 0,
            media = listOf(
                ContainerApi.UploadedMedia(
                    fid = "1042018:abc123",
                    md5 = "d41d8cd98f00b204e9800998ecf8427e",
                    bypass = "unistore.image",
                ),
            ),
        )
        assertEquals("带图微博", withMedia["content"])
        assertEquals("0", withMedia["visible"])
        val mediaArr = JSONArray(withMedia.getValue("media"))
        assertEquals(1, mediaArr.length())
        val first = mediaArr.getJSONObject(0)
        assertEquals("unistore.image", first.getString("bypass"))
        assertEquals("localfile", first.getString("createtype"))
        assertEquals("1042018:abc123", first.getString("fid"))
        assertEquals("pic", first.getString("type"))
        assertEquals("0", first.getString("pic_source"))
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", first.getString("pic_raw_md5"))
        assertEquals("0", first.getString("picStatus"))
        assertFalse(first.has("subtype"))
        assertFalse(first.has("video"))
    }

    @Test
    fun buildPublishPartsSerializesLivePhotoSubtypeAndVideo() {
        val mixed = ContainerApi.buildPublishParts(
            text = "带实况图微博",
            visible = 1,
            media = listOf(
                ContainerApi.UploadedMedia(
                    fid = "0096n9LBgy1livecover",
                    md5 = "11111111111111111111111111111111",
                    bypass = "unistore.pic",
                    subtype = "livephoto",
                    videoFid = "0030cVQlgx08CDPtjNDp0f0f0100osh10k01",
                ),
                ContainerApi.UploadedMedia(
                    fid = "0096n9LBgy1staticpic",
                    md5 = "22222222222222222222222222222222",
                    bypass = "unistore.pic",
                ),
            ),
        )
        val mediaArr = JSONArray(mixed.getValue("media"))
        assertEquals(2, mediaArr.length())

        val liveItem = mediaArr.getJSONObject(0)
        assertEquals("0096n9LBgy1livecover", liveItem.getString("fid"))
        assertEquals("pic", liveItem.getString("type"))
        assertEquals("livephoto", liveItem.getString("subtype"))
        assertEquals("0030cVQlgx08CDPtjNDp0f0f0100osh10k01", liveItem.getString("video"))
        assertEquals("unistore.pic", liveItem.getString("bypass"))
        assertEquals("11111111111111111111111111111111", liveItem.getString("pic_raw_md5"))

        val staticItem = mediaArr.getJSONObject(1)
        assertEquals("0096n9LBgy1staticpic", staticItem.getString("fid"))
        assertEquals("pic", staticItem.getString("type"))
        assertFalse(staticItem.has("subtype"))
        assertFalse(staticItem.has("video"))
    }

    @Test
    fun parseMediaDiscoveryReadsPicAndLivePhotoSections() {
        val discovery = ContainerApi.parseMediaDiscovery(
            JSONObject(
                """
                {
                  "pic": {
                    "bypass": "unistore.pic",
                    "init_url": "https://unistore.weibo.cn/2/statuses/upload_file?act=init&need_https=1",
                    "upload_url": "https://unistore.weibo.cn/2/statuses/upload_file?act=send"
                  },
                  "livephoto": {
                    "bypass": "unistore.video",
                    "init_url": "https://uniupload.weibo.com/2/statuses/upload_file?act=init&need_https=1",
                    "upload_url": "https://uniupload.weibo.com/2/statuses/upload_file?act=send"
                  }
                }
                """.trimIndent(),
            ),
        )
        assertEquals("unistore.pic", discovery.pic.bypass)
        assertEquals("https://unistore.weibo.cn/2/statuses/upload_file?act=init&need_https=1", discovery.pic.initUrl)
        assertEquals("unistore.video", discovery.livePhoto?.bypass)
        assertEquals("https://uniupload.weibo.com/2/statuses/upload_file?act=init&need_https=1", discovery.livePhoto?.initUrl)
        assertEquals("https://uniupload.weibo.com/2/statuses/upload_file?act=send", discovery.livePhoto?.uploadUrl)
    }

    @Test
    fun buildRepostPartsUsesIdStatusVisibleAndOptionalMedia() {
        val textOnly = ContainerApi.buildRepostParts(id = "500123", text = "转发看看", visible = 0)
        assertEquals("500123", textOnly["id"])
        assertEquals("转发看看", textOnly["status"])
        assertEquals("0", textOnly["visible"])
        assertFalse(textOnly.containsKey("media"))

        val withMedia = ContainerApi.buildRepostParts(
            id = "500123",
            text = "带图转发",
            visible = 1,
            media = listOf(
                ContainerApi.UploadedMedia(
                    fid = "1042018:repost999",
                    md5 = "0123456789abcdef0123456789abcdef",
                    bypass = "unistore.image",
                ),
            ),
        )
        assertEquals("500123", withMedia["id"])
        assertEquals("带图转发", withMedia["status"])
        assertEquals("1", withMedia["visible"])
        val mediaArr = JSONArray(withMedia.getValue("media"))
        assertEquals(1, mediaArr.length())
        val first = mediaArr.getJSONObject(0)
        assertEquals("1042018:repost999", first.getString("fid"))
        assertEquals("pic", first.getString("type"))
        assertEquals("0123456789abcdef0123456789abcdef", first.getString("pic_raw_md5"))
    }

    @Test
    fun buildCommentAndReplyPartsAttachMediaAndDefaultBlankText() {
        val textOnly = ContainerApi.buildCommentParts(id = "500123", text = "纯文字评论")
        assertEquals("500123", textOnly["id"])
        assertEquals("纯文字评论", textOnly["comment"])
        assertFalse(textOnly.containsKey("media"))

        val picOnly = ContainerApi.buildCommentParts(
            id = "500123",
            text = "   ",
            media = listOf(ContainerApi.UploadedMedia(fid = "0096n9LBgy1test", md5 = "abc", bypass = "unistore.pic")),
        )
        assertEquals("500123", picOnly["id"])
        assertEquals("图片评论", picOnly["comment"])
        val commentMedia = JSONArray(picOnly.getValue("media"))
        assertEquals(1, commentMedia.length())
        assertEquals("0096n9LBgy1test", commentMedia.getJSONObject(0).getString("fid"))

        val replyWithPic = ContainerApi.buildReplyCommentParts(
            id = "500123",
            cid = "900456",
            text = "",
            media = listOf(ContainerApi.UploadedMedia(fid = "0096n9LBgy1reply", md5 = "def", bypass = "unistore.pic")),
        )
        assertEquals("500123", replyWithPic["id"])
        assertEquals("900456", replyWithPic["cid"])
        assertEquals("图片评论", replyWithPic["comment"])
        val replyMedia = JSONArray(replyWithPic.getValue("media"))
        assertEquals(1, replyMedia.length())
        assertEquals("0096n9LBgy1reply", replyMedia.getJSONObject(0).getString("fid"))
    }

    @Test
    fun parsePicDiscoveryAndUploadInitSupportMssAndLegacyPlans() {
        val discovery = ContainerApi.parsePicDiscovery(
            JSONObject(
                """
                {
                  "pic": {
                    "bypass": "unistore.image",
                    "init_url": "https://upload.api.weibo.com/2/mss/init.json",
                    "upload_url": "https://upload.api.weibo.com/2/mss/upload.json"
                  }
                }
                """.trimIndent(),
            ),
        )
        assertEquals("https://upload.api.weibo.com/2/mss/init.json", discovery.initUrl)
        assertEquals("https://upload.api.weibo.com/2/mss/upload.json", discovery.uploadUrl)
        assertEquals("unistore.image", discovery.bypass)

        val mss = ContainerApi.parseUploadInit(
            JSONObject(
                """
                {
                  "upload_id": "up_1",
                  "media_id": "1042018:m1",
                  "auth": "auth_tok",
                  "strategy": {
                    "chunk_size": 2048,
                    "upload_protocol": "binary"
                  }
                }
                """.trimIndent(),
            ),
            discovery.uploadUrl,
        ) as ContainerApi.PicUploadInit.Mss
        assertEquals("up_1", mss.uploadId)
        assertEquals("1042018:m1", mss.mediaId)
        assertEquals("binary", mss.uploadProtocol)
        assertEquals("auth_tok", mss.auth)
        assertEquals(2048 * 1024, mss.chunkBytes)

        val legacy = ContainerApi.parseUploadInit(
            JSONObject(
                """
                {
                  "fileToken": "ft_9",
                  "urlTag": "tag_1",
                  "length": 1024,
                  "upload_url": "https://picupload.weibo.com/chunk"
                }
                """.trimIndent(),
            ),
            discovery.uploadUrl,
        ) as ContainerApi.PicUploadInit.Legacy
        assertEquals("ft_9", legacy.fileToken)
        assertEquals("tag_1", legacy.urlTag)
        assertEquals("https://picupload.weibo.com/chunk", legacy.uploadUrl)
        assertEquals(1024 * 1024, legacy.chunkBytes)
    }

    @Test
    fun checkSuccessRejectsErrnoAndZeroOk() {
        val good = ContainerApi.checkSuccess(JSONObject("""{"id": "123456"}"""))
        assertEquals("123456", good.getString("id"))

        try {
            ContainerApi.checkSuccess(JSONObject("""{"errno": -200, "errmsg": "接口请求失败"}"""))
            fail("expected ApiException for errno")
        } catch (e: ApiException) {
            assertEquals("-200", e.errno)
            assertEquals("接口请求失败", e.message)
        }

        try {
            ContainerApi.checkSuccess(JSONObject("""{"ok": 0, "msg": "发送失败"}"""))
            fail("expected ApiException for ok=0")
        } catch (e: ApiException) {
            assertTrue(e.message == "发送失败")
        }
    }

    @Test
    fun buildEditWeiboAndRepostPartsIncludeMidAndPreserveMedia() {
        val weiboEdit = ContainerApi.buildEditWeiboParts(
            mid = "5369129924297726",
            text = "修改后的正文",
            visible = 1,
            media = listOf(
                ContainerApi.UploadedMedia(
                    fid = "0096n9LBgy1existinglive",
                    subtype = "livephoto",
                    videoFid = "0030cVQlgx08CDPtjNDp0f0f0100osh10k01",
                ),
            ),
        )
        assertEquals("5369129924297726", weiboEdit["mid"])
        assertEquals("修改后的正文", weiboEdit["content"])
        assertEquals("1", weiboEdit["visible"])
        val mediaArr = JSONArray(weiboEdit.getValue("media"))
        assertEquals(1, mediaArr.length())
        assertEquals("0096n9LBgy1existinglive", mediaArr.getJSONObject(0).getString("fid"))
        assertEquals("livephoto", mediaArr.getJSONObject(0).getString("subtype"))
        assertEquals("0030cVQlgx08CDPtjNDp0f0f0100osh10k01", mediaArr.getJSONObject(0).getString("video"))

        val repostEdit = ContainerApi.buildEditRepostParts(
            mid = "5369129924297727",
            originalId = "500123",
            text = "修改后的转发语",
            visible = 0,
        )
        assertEquals("5369129924297727", repostEdit["mid"])
        assertEquals("500123", repostEdit["id"])
        assertEquals("修改后的转发语", repostEdit["status"])
        assertEquals("0", repostEdit["visible"])
        assertFalse(repostEdit.containsKey("media"))
    }

    @Test
    fun editableStatusTextAndPicMetadataExtractionRoundTripCleanly() {
        val html = """第一行<br/>第二行 <img alt="[微笑]" src="x.png"/> <a href="https://t.cn/A6abc123">网页链接</a> <a href="https://t.cn/A6pic">查看图片</a> ​"""
        assertEquals("第一行\n第二行 [微笑] https://t.cn/A6abc123", editableStatusText(html))

        val pic = Pic(
            thumb = "https://wx1.sinaimg.cn/orj360/0096n9LBgy1ijed612gorj300g00g07x.jpg",
            large = "https://wx1.sinaimg.cn/large/0096n9LBgy1ijed612gorj300g00g07x.jpg",
            live = "https://video.weibo.com/media/play?livephoto=https%3A%2F%2Fus.sinaimg.cn%2F0030cVQlgx08CDPtjNDp0f0f0100osh10k01.mov",
        )
        assertEquals("0096n9LBgy1ijed612gorj300g00g07x", pic.resolvedPicId())
        assertEquals("0030cVQlgx08CDPtjNDp0f0f0100osh10k01", pic.resolvedLiveVideoUsid())
    }
}
