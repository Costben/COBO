package com.cobo.app.data

import org.json.JSONArray
import org.json.JSONObject
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
}
