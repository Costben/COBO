package com.cobo.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun parsesBigPicPageInfo() {
        val status = JSONObject(
            """
            {
              "id": "5365441585220046",
              "mid": "5365441585220046",
              "text": " ",
              "source": "knight卓定超话",
              "page_info": {
                "type": "bigPic",
                "page_pic": {
                  "url": "https://mu1.sinaimg.cn/original/test.png",
                  "scheme": "https://m.weibo.cn/c/wbox?id=c0p9sg9tw9"
                }
              }
            }
            """.trimIndent(),
        )

        val bigPic = Status.from(status)!!.bigPic
        assertNotNull(bigPic)
        assertEquals("https://mu1.sinaimg.cn/original/test.png", bigPic!!.url)
        assertEquals("https://m.weibo.cn/c/wbox?id=c0p9sg9tw9", bigPic.scheme)
    }

    @Test
    fun parsesVideoFromUrlsMapAndExternalWebpagePageInfo() {
        val urlsStatus = JSONObject(
            """
            {
              "id": "1001",
              "text": "视频",
              "page_info": {
                "type": "video",
                "page_title": "高清视频",
                "urls": {
                  "mp4_720p_mp4": "https://f.video.weibocdn.com/hd720.mp4"
                }
              }
            }
            """.trimIndent(),
        )
        val parsedUrls = Status.from(urlsStatus)!!
        assertEquals("https://f.video.weibocdn.com/hd720.mp4", parsedUrls.video?.url)

        val externalStatus = JSONObject(
            """
            {
              "id": "1002",
              "text": "爱奇艺官链",
              "page_info": {
                "type": "webpage",
                "page_title": "爱奇艺视频",
                "page_url": "https://www.iqiyi.com/v_19rr7abcde.html",
                "page_pic": { "url": "https://wx1.sinaimg.cn/large/iqiyi_cover.jpg" }
              }
            }
            """.trimIndent(),
        )
        val parsedExternal = Status.from(externalStatus)!!
        val externalVideo = parsedExternal.video
        assertNotNull(externalVideo)
        assertEquals("爱奇艺视频", externalVideo!!.title)
        assertEquals("https://www.iqiyi.com/v_19rr7abcde.html", externalVideo.url)
        assertEquals("https://wx1.sinaimg.cn/large/iqiyi_cover.jpg", externalVideo.cover)
    }

    @Test
    fun imageBrowseQualityParsesDefaultAndOriginal() {
        assertEquals(ImageBrowseQuality.Default, ImageBrowseQuality.fromKey(null))
        assertEquals(ImageBrowseQuality.Default, ImageBrowseQuality.fromKey("default"))
        assertEquals(ImageBrowseQuality.Original, ImageBrowseQuality.fromKey("original"))
    }

    @Test
    fun repostMovesQuotedVideoInsideRetweetedStatus() {
        val status = JSONObject(
            """
            {
              "id": "5200000000000001",
              "text": "我修过仙，这个我熟。//@卤煮疫苗:太离谱了",
              "page_info": {
                "type": "video",
                "page_title": "涨知识boss的微博视频",
                "page_pic": { "url": "https://wx1.sinaimg.cn/orj480/video_cover.jpg" },
                "media_info": {
                  "stream_url": "https://f.video.weibocdn.com/o0/quoted.mp4"
                }
              },
              "retweeted_status": {
                "id": "5199999999999999",
                "text": "中医鬼才：如何把精气神... http://t.cn/AXW1yiVP",
                "user": { "id": "99", "screen_name": "阿南的茅草屋" }
              }
            }
            """.trimIndent(),
        )
        val parsed = Status.from(status)!!
        assertNull(parsed.video)
        assertNotNull(parsed.retweeted?.video)
        assertEquals("涨知识boss的微博视频", parsed.retweeted?.video?.title)
        assertEquals("https://f.video.weibocdn.com/o0/quoted.mp4", parsed.retweeted?.video?.url)
    }

    @Test
    fun parsesClientWboxCardPageInfoAndIgnoresPlainSuperTopicTag() {
        val wboxStatus = JSONObject(
            """
            {
              "id": "5369999000000001",
              "mid": "5369999000000001",
              "text": "#Bin哥厉害[超话]# 兵出征卡 http://t.cn/A6xXxXxX",
              "source": "Bin哥厉害超话",
              "url_struct": [
                {
                  "short_url": "http://t.cn/A6xXxXxX",
                  "ori_url": "sinaweibo://wbox?id=abc123&page=card",
                  "url_title": "兵出征卡"
                }
              ],
              "page_info": {
                "type": "36",
                "object_type": "webpage",
                "page_title": "兵出征卡",
                "page_url": "http://t.cn/A6xXxXxX",
                "page_pic": {
                  "url": "http://mu1.sinaimg.cn/original/bingge_card.png"
                }
              }
            }
            """.trimIndent(),
        )
        val parsedCard = Status.from(wboxStatus)!!
        val bigPic = parsedCard.bigPic
        assertNotNull(bigPic)
        assertEquals("https://mu1.sinaimg.cn/original/bingge_card.png", bigPic!!.url)
        assertEquals("sinaweibo://wbox?id=abc123&page=card", bigPic.scheme)
        assertEquals("兵出征卡", bigPic.title)

        // 实测 Bin哥厉害「bin出征卡」结构：type=23 + page_id=231601_... + card_info(card_type=107, pic_url) + page_url 指回超话
        val card107Status = JSONObject(
            """
            {
              "id": "5310045241737774",
              "mid": "5310045241737774",
              "text": "http://t.cn/AXaaX34L",
              "page_info": {
                "type": "23",
                "page_id": "231601_7374758152_3_178149690382",
                "object_type": "webpage",
                "page_title": "bin出征卡",
                "page_url": "sinaweibo://pageinfo?containerid=100808b57c4153faa6a778c2b7e4c3268182f6",
                "card_info": {
                  "card_type": "107",
                  "pic_url": "https://mu1.sinaimg.cn/original/phpnNIPOo1781494581hanlin6.png",
                  "scheme": "sinaweibo://pageinfo?pageid=100808b57c4153faa6a778c2b7e4c3268182f6"
                }
              }
            }
            """.trimIndent(),
        )
        val parsed107 = Status.from(card107Status)!!
        assertNotNull(parsed107.bigPic)
        assertEquals("https://mu1.sinaimg.cn/original/phpnNIPOo1781494581hanlin6.png", parsed107.bigPic!!.url)
        assertEquals("bin出征卡", parsed107.bigPic!!.title)

        // 仅含微博灰底小图标 timeline_card_small_* 时，不把小图标放大当大图卡片
        val placeholderIconOnlyStatus = JSONObject(
            """
            {
              "id": "5369999000000003",
              "mid": "5369999000000003",
              "text": "仅有默认小图标",
              "page_info": {
                "type": "23",
                "object_type": "wbox",
                "page_title": "小程序卡片",
                "page_pic": {
                  "url": "https://h5.sinaimg.cn/upload/2015/09/25/3/timeline_card_small_web_default.png"
                },
                "page_url": "https://m.weibo.cn/c/wbox?id=abc"
              }
            }
            """.trimIndent(),
        )
        assertNull(Status.from(placeholderIconOnlyStatus)!!.bigPic)

        val plainSuperTopicStatus = JSONObject(
            """
            {
              "id": "5369999000000002",
              "mid": "5369999000000002",
              "text": "普通超话发帖",
              "page_info": {
                "type": "23",
                "object_type": "webpage",
                "page_id": "100808abcdef123456",
                "page_pic": { "url": "https://wx1.sinaimg.cn/thumb/super_avatar.jpg" },
                "page_url": "https://m.weibo.cn/p/index?containerid=100808abcdef123456"
              }
            }
            """.trimIndent(),
        )
        val parsedPlain = Status.from(plainSuperTopicStatus)!!
        assertNull(parsedPlain.bigPic)
    }

    @Test
    fun resolvesProfileRenameHttp400AndComputesCooldownHint() {
        val json400 = ApiHttpException(400, """{"ok":0,"msg":"非会员一年只可修改一次昵称"}""")
        assertEquals(
            "非会员一年只可修改一次昵称",
            resolveProfileUpdateErrorMessage(json400, renaming = true, membership = Membership.None),
        )

        val html400 = ApiHttpException(400, "<html>Bad Request</html>")
        val fallbackMsg = resolveProfileUpdateErrorMessage(
            html400,
            renaming = true,
            membership = Membership.None,
        )
        assertTrue(fallbackMsg.contains("改名") && fallbackMsg.contains("HTTP 400"))

        val now = 1_795_000_000_000L
        val nonVipRecent = computeProfileRenameStatus(
            ProfileRenameRecord(lastRenamedAtMs = now - 3_600_000L),
            Membership.None,
            nowMs = now,
        )
        assertTrue(nonVipRecent.limited)
        assertTrue(nonVipRecent.summary.contains("上次改名：") && nonVipRecent.summary.contains("暂不可改"))

        val vipEligible = computeProfileRenameStatus(
            ProfileRenameRecord(lastRenamedAtMs = now - 10L * 86_400_000L),
            Membership.Svip,
            nowMs = now,
        )
        assertFalse(vipEligible.limited)
        assertTrue(vipEligible.summary.contains("可尝试改名"))
    }

    @Test
    fun formatsShortWeiboLinksAndLongTextWithoutRawTcnTruncation() {
        val longStatus = JSONObject(
            """
            {
              "id": "5370000000000001",
              "mid": "5370000000000001",
              "isLongText": true,
              "text": "之前介绍过的 Strata（http://t.cn/AXWV0FO1），专门找",
              "longText": {
                "longTextContent": "之前介绍过的 Strata（http://t.cn/AXWV0FO1），最新版已经支持 Inkling-2-Mini 了（http://t.cn/AXTaF7i9）。\n完整正文结尾。",
                "url_struct": [
                  {
                    "short_url": "https://t.cn/AXWV0FO1",
                    "ori_url": "sinaweibo://detail?mblogid=Q12345",
                    "url_title": "微博正文"
                  },
                  {
                    "short_url": "http://t.cn/AXTaF7i9",
                    "ori_url": "https://m.weibo.cn/status/5369999999999999",
                    "url_title": "",
                    "url_type_pic": "https://h5.sinaimg.cn/upload/2015/09/25/3/timeline_card_small_weibo_default.png"
                  }
                ]
              }
            }
            """.trimIndent(),
        )

        val parsedLong = Status.from(longStatus)!!
        assertFalse(parsedLong.textHtml.contains("t.cn/AXWV0FO1"))
        assertFalse(parsedLong.textHtml.contains("t.cn/AXTaF7i9"))
        assertTrue(parsedLong.textHtml.contains(">微博正文</a>），最新版"))
        assertTrue(parsedLong.textHtml.contains("完整正文结尾。"))
        assertFalse(parsedLong.textHtml.contains(">全文</a>"))

        val stillTruncated = JSONObject(
            """
            {
              "id": "5370000000000002",
              "mid": "5370000000000002",
              "isLongText": true,
              "text": "被截断的正文内容"
            }
            """.trimIndent(),
        )
        val parsedTruncated = Status.from(stillTruncated)!!
        assertTrue(parsedTruncated.textHtml.contains("被截断的正文内容...<a href=\"/status/5370000000000002\">全文</a>"))
    }

    @Test
    fun parsesNormalPageInfoAsCompactLinkCardNotLargePic() {
        val status = JSONObject(
            """
            {
              "id": "5370000000000003",
              "mid": "5370000000000003",
              "text": "正文",
              "page_info": {
                "type": "webpage",
                "object_type": "webpage",
                "page_title": "tombkeeper 的微博",
                "content1": "本地跑大模型的同学，可以试一下新出的推理引擎 Strata",
                "page_url": "https://m.weibo.cn/status/5369999999999999",
                "page_pic": {
                  "url": "https://wx1.sinaimg.cn/thumbnail/thumb.jpg"
                }
              }
            }
            """.trimIndent(),
        )
        val parsed = Status.from(status)!!
        val linkCard = parsed.bigPic
        assertNotNull(linkCard)
        assertFalse(linkCard!!.isLarge)
        assertEquals("tombkeeper 的微博", linkCard.title)
        assertEquals("本地跑大模型的同学，可以试一下新出的推理引擎 Strata", linkCard.subtitle)
        assertEquals("https://wx1.sinaimg.cn/thumbnail/thumb.jpg", linkCard.url)
    }

    @Test
    fun parsesWeiboLivePageInfoAsLiveAndReplayRespectively() {
        val liveId = "1022:2321325160873118269706"
        val ongoingStatus = JSONObject(
            """
            {
              "id": "5370000000000010",
              "mid": "5370000000000010",
              "text": "正在直播 http://t.cn/A6live",
              "url_struct": [
                {
                  "short_url": "http://t.cn/A6live",
                  "ori_url": "sinaweibo://chatroom?live_id=$liveId&livetype=wblive",
                  "url_title": "网页链接",
                  "url_type_pic": "https://h5.sinaimg.cn/upload/2015/09/25/3/timeline_card_small_live_default.png"
                }
              ],
              "page_info": {
                "type": "11",
                "object_type": "live",
                "object_id": "$liveId",
                "page_title": "现场直播中",
                "page_pic": {"url": "https://wblive.sinaimg.cn/live_cover.jpg"},
                "media_info": {
                  "live_id": "$liveId",
                  "live_status": 1,
                  "stream_url": "https://wblive-out.api.weibo.com/2/wblive/room/play?id=$liveId",
                  "open_scheme": "sinaweibo://chatroom?container_id=2321325160873118269706&live_id=$liveId&livetype=wblive"
                }
              }
            }
            """.trimIndent(),
        )
        val parsedOngoing = Status.from(ongoingStatus)!!
        assertNotNull(parsedOngoing.video)
        assertTrue(parsedOngoing.video!!.isLive)
        assertEquals("https://weibo.com/l/wblive/m/show/$liveId", parsedOngoing.video!!.url)
        assertEquals("现场直播中", parsedOngoing.video!!.title)
        assertTrue(parsedOngoing.textHtml.contains(">微博直播</a>"))
        assertNull(parsedOngoing.bigPic)

        val replayStatus = JSONObject(
            """
            {
              "id": "5370000000000011",
              "mid": "5370000000000011",
              "text": "直播回放",
              "page_info": {
                "type": "live",
                "object_type": "live",
                "page_title": "直播回放标题",
                "media_info": {
                  "live_id": "$liveId",
                  "live_status": 3,
                  "replay_hd": "https://live.video.weibocdn.com/replay_index.m3u8"
                }
              }
            }
            """.trimIndent(),
        )
        val parsedReplay = Status.from(replayStatus)!!
        assertNotNull(parsedReplay.video)
        assertFalse(parsedReplay.video!!.isLive)
        assertEquals("https://live.video.weibocdn.com/replay_index.m3u8", parsedReplay.video!!.url)
    }
}
