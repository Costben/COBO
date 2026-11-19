package com.cobo.app.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SuperTopicTest {
    private val id = "100808e1f868bf9980f09ab6908787d7eaf0f0"
    private val other = "100808fc439dedbb06ca5fd858848e521b8716"

    private fun response(): JSONObject = JSONObject(
        """{"ok":1,"data":{"pageInfo":{
            "containerid":"${id}_-_feed","page_title":"崩坏：星穹铁道",
            "desc":"超话简介","portrait":"https://example.com/avatar.jpg",
            "portrait_hd":"https://example.com/avatar-hd.jpg",
            "page_view":"super","total":4811642,"portrait_sub_text":"手游超话No.3",
            "desc_more":["阅读965.8亿　帖子481.2万　粉丝1276.2万"],
            "since_id":5184066029470123},"cards":[]}}"""
    )

    @Test
    fun parsesIdentityMetadataAndExactNumericCursor() {
        val result = parseSuperTopic(response(), id)
        assertEquals(id, result.info.containerId)
        assertEquals("崩坏：星穹铁道", result.info.title)
        assertEquals("超话简介", result.info.description)
        assertEquals("https://example.com/avatar-hd.jpg", result.info.avatar)
        assertEquals("965.8亿", result.info.readCount)
        assertEquals("481.2万", result.info.postCount)
        assertEquals("1276.2万", result.info.fanCount)
        assertEquals("5184066029470123", result.nextCursor)
        assertTrue(result.items.isEmpty())
    }

    @Test
    fun countsIgnorePageTypeAndRankAndUseOnlyNumericTotalFallback() {
        val root = response()
        val page = root.getJSONObject("data").getJSONObject("pageInfo")
        page.remove("desc_more")
        val info = parseSuperTopic(root, id).info
        assertEquals("", info.readCount)
        assertEquals("4811642", info.postCount)
        assertEquals("", info.fanCount)
        page.put("desc_more", JSONArray().put("未知格式").put("阅读未知　帖子未知　粉丝未知"))
        page.put("total", "未知")
        val unknown = parseSuperTopic(root, id).info
        assertEquals("", unknown.readCount)
        assertEquals("", unknown.postCount)
        assertEquals("", unknown.fanCount)
    }

    @Test
    fun labelledCountsHandleWhitespaceAndPreserveDisplayUnits() {
        val root = response()
        root.getJSONObject("data").getJSONObject("pageInfo")
            .put("desc_more", JSONArray().put(" 阅读 965.8亿 \t 帖子　481.2万　粉丝 1276.2万 "))
        val info = parseSuperTopic(root, id).info
        assertEquals("965.8亿", info.readCount)
        assertEquals("481.2万", info.postCount)
        assertEquals("1276.2万", info.fanCount)
    }

    @Test
    fun emptyFinalPagePreservesHeaderAndIgnoresGenericCursor() {
        val root = response()
        val data = root.getJSONObject("data")
        data.getJSONObject("pageInfo").remove("since_id")
        data.put("cardlistInfo", JSONObject().put("since_id", "999"))
        val result = parseSuperTopic(root, id)
        assertEquals(id, result.info.containerId)
        assertNull(result.nextCursor)
        assertTrue(result.items.isEmpty())
    }

    @Test
    fun zeroNullAndInvalidCursorsEndPagination() {
        for (cursor in listOf(0, "0", "000", JSONObject.NULL, "", "null", "1.5", "-1")) {
            val root = response()
            root.getJSONObject("data").getJSONObject("pageInfo").put("since_id", cursor)
            assertNull(parseSuperTopic(root, id).nextCursor)
        }
    }

    @Test
    fun recursivelyParsesStatusGroupsAndDeduplicates() {
        val root = response()
        fun statusCard(statusId: String) = JSONObject().put(
            "mblog", JSONObject().put("id", statusId).put("text", "正文")
                .put("user", JSONObject().put("id", "42").put("screen_name", "测试用户"))
        )
        val nested = JSONObject().put("card_group", JSONArray().put(statusCard("2")))
        val group = JSONObject().put("card_group", JSONArray().put(statusCard("1")).put(nested))
        root.getJSONObject("data").put("cards", JSONArray().put(group).put(statusCard("1")))
        assertEquals(listOf("1", "2"), parseSuperTopic(root, id).items.map { it.id })
    }

    @Test
    fun rejectsMissingWrongAndNonSupertopicIdentity() {
        val missing = response()
        missing.getJSONObject("data").remove("pageInfo")
        assertFailure(missing)
        for (identity in listOf(other, "100103type=1&q=星穹铁道", "", "${id}_-_other")) {
            val root = response()
            root.getJSONObject("data").getJSONObject("pageInfo").put("containerid", identity)
            assertFailure(root)
        }
    }

    @Test
    fun apiFailuresUseSanitizedMessagesAndLoginCode() {
        val login = failure(JSONObject().put("ok", -100).put("msg", "private server payload"))
        assertEquals("-100", login.errno)
        assertEquals("超话需要登录后查看", login.message)
        val error = failure(JSONObject().put("ok", 0).put("msg", "private server payload"))
        assertEquals("超话加载失败", error.message)
        assertFailure(JSONObject().put("ok", 1))
    }

    @Test
    fun recognizesAllowedRoutesAndDecodesQueryOnce() {
        val urls = listOf(
            id,
            "https://m.weibo.cn/p/index?containerid=$id",
            "http://m.weibo.cn/p/index?extparam=abc&containerid=$id",
            "https://m.weibo.cn/p/index?containerid=%31${id.drop(1)}",
            "https://m.weibo.cn/p/index?containerid=${id}_-_feed",
            "sinaweibo://pageinfo?containerid=$id&extparam=abc",
            "sinaweibo://pageinfo?pageid=$id&extparam=abc",
            "intent://pageinfo?containerid=$id#Intent;scheme=sinaweibo;package=com.sina.weibo;end",
            "https://weibo.com/page/$id",
            "https://weibo.com/p/$id",
            "https://weibo.com/p/$id/super_index",
            "https://www.weibo.com/p/$id/super_index?from=page_100808",
            "https://weibo.com/p/index?containerid=$id",
            "//weibo.com/p/$id/super_index",
        )
        for (url in urls) assertEquals(url, id, superTopicId(url))
    }

    @Test
    fun acceptsObservedDesktopRedirectAndMatchingIdentityOnly() {
        val observed = "https://weibo.com/page/$id?containerid=$id&launchid=10000360-page_H5" +
            "&luicode=10000011&lfid=${id}_-_feed&v_p=42"
        assertEquals(id, superTopicId(observed))
        assertEquals(id, superTopicId("https://weibo.com/page/$id?launchid=10000360-page_H5"))
        assertEquals(id, superTopicId("https://weibo.com/page/$id?containerid=${id}_-_feed"))
        assertNull(superTopicId("https://weibo.com/page/$id?containerid=$other"))
        assertNull(superTopicId("https://weibo.com/page/$id?containerid=$id&containerid=$id"))
        assertNull(superTopicId("https://weibo.com/page/$id?containerid=$id&containerid=$other"))
        assertNull(superTopicId("https://weibo.com/page/$id?containerid="))
        assertNull(superTopicId("https://weibo.com/page/$id?launchid=%"))
    }

    @Test
    fun rejectsUntrustedAmbiguousMalformedAndOrdinaryTopicLinks() {
        val urls = listOf(
            "prefix${id}suffix", "${id}_-_feed",
            "https://evil.example/p/index?containerid=$id",
            "https://m.weibo.cn.evil.example/p/index?containerid=$id",
            "https://m.weibo.cn@evil.example/p/index?containerid=$id",
            "https://evil.example@m.weibo.cn/p/index?containerid=$id",
            "https://m.weibo.cn:443/p/index?containerid=$id",
            "https://m.weibo.cn/p/index?containerid=$id&containerid=$other",
            "https://m.weibo.cn/p/index?containerid=$id%",
            "https://m.weibo.cn/p/index?containerid=%2531${id.drop(1)}",
            "https://m.weibo.cn/p/index?containerid=100103type%3D38%26q%3D原神",
            "https://m.weibo.cn/p/index?page_id=$id",
            "sinaweibo://browser?containerid=$id",
            "intent://pageinfo?containerid=$id#Intent;scheme=https;end",
            "javascript:pageinfo?containerid=$id",
            "https://weibo.com/page/$id/extra",
        )
        for (url in urls) assertNull(url, superTopicId(url))
    }

    @Test
    fun extractsChildTabsAndChannelListWithoutDuplicates() {
        val root = response()
        val data = root.getJSONObject("data")
        val page = data.getJSONObject("pageInfo")
        page.put(
            "cardlist_head_cards",
            JSONArray().put(
                JSONObject().put(
                    "channel_list",
                    JSONArray()
                        .put(JSONObject().put("id", "feed").put("name", "帖子").put("containerid", "${id}_-_feed"))
                        .put(JSONObject().put("id", "official").put("name", "官方").put("containerid", "${id}_-_official"))
                        .put(JSONObject().put("id", "video").put("name", "视频").put("containerid", "${id}_-_video"))
                        .put(JSONObject().put("id", "album").put("name", "相册").put("containerid", "${id}_-_album"))
                        .put(JSONObject().put("id", "4587798253404290").put("name", "攻略").put("containerid", "${id}__4587798253404290_-_tag_comment_sort")),
                ),
            ),
        )
        data.put(
            "cards",
            JSONArray().put(
                JSONObject().put(
                    "card_group",
                    JSONArray().put(
                        JSONObject()
                            .put("card_type", "121")
                            .put("itemid", "page_feed_child_tab")
                            .put(
                                "filter_group",
                                JSONArray()
                                    .put(JSONObject().put("name", "最新评论").put("containerid", "${id}_-_feed"))
                                    .put(JSONObject().put("name", "最新发帖").put("containerid", "${id}_-_sort_time"))
                                    .put(JSONObject().put("name", "热门").put("containerid", "${id}_-_recommend"))
                                    .put(JSONObject().put("name", "精华").put("containerid", "${id}_-_soul")),
                            ),
                    ),
                ),
            ),
        )
        val parsed = parseSuperTopic(root, id)
        assertEquals(
            listOf("最新评论", "最新发帖", "热门", "精华", "官方", "视频", "相册", "攻略"),
            parsed.tabs.map { it.label },
        )
        assertEquals(
            listOf(
                SuperTopicTabKind.Feed,
                SuperTopicTabKind.SortTime,
                SuperTopicTabKind.Recommend,
                SuperTopicTabKind.Soul,
                SuperTopicTabKind.Official,
                SuperTopicTabKind.Video,
                SuperTopicTabKind.Album,
                SuperTopicTabKind.Tag,
            ),
            parsed.tabs.map { it.kind },
        )
    }

    @Test
    fun parsesTabCursorsAlbumAndVideoCards() {
        val mblogObj = JSONObject().put("id", "101").put("text", "帖子正文")
            .put("user", JSONObject().put("id", "42").put("screen_name", "用户A"))

        val recommendRoot = response()
        recommendRoot.getJSONObject("data").apply {
            getJSONObject("pageInfo").put("containerid", "${id}_-_recommend").put("since_id", 1)
            put("cards", JSONArray().put(JSONObject().put("mblog", mblogObj)))
        }
        val recommendPage = parseSuperTopicTab(recommendRoot, id, "${id}_-_recommend", currentPage = 1)
        assertEquals("page:2", recommendPage.nextCursor)

        val soulRoot = response()
        soulRoot.getJSONObject("data").apply {
            getJSONObject("pageInfo").put("containerid", "${id}_-_soul").put("since_id", "1790918201.590245")
            put("cards", JSONArray().put(JSONObject().put("mblog", mblogObj)))
        }
        assertEquals("1790918201.590245", parseSuperTopicTab(soulRoot, id, "${id}_-_soul").nextCursor)

        val albumRoot = response()
        albumRoot.getJSONObject("data").apply {
            getJSONObject("pageInfo").put("containerid", "${id}_-_album").put("since_id", "5363#pic")
            put(
                "cards",
                JSONArray().put(
                    JSONObject().put("card_type", "47").put(
                        "pics",
                        JSONArray().put(JSONObject().put("mblog", mblogObj)),
                    ),
                ),
            )
        }
        val albumPage = parseSuperTopicTab(albumRoot, id, "${id}_-_album")
        assertEquals(listOf("101"), albumPage.items.map { it.id })
        assertEquals("5363#pic", albumPage.nextCursor)

        val videoRoot = response()
        videoRoot.getJSONObject("data").apply {
            getJSONObject("pageInfo").put("containerid", "${id}_-_video").put("since_id", "5365")
            put(
                "cards",
                JSONArray().put(
                    JSONObject().put("card_type", "52").put(
                        "items",
                        JSONArray().put(
                            JSONObject()
                                .put("scheme", "https://m.weibo.cn/feature/applink?scheme=sinaweibo%3A%2F%2Fvideo%2Fvvs%3Fmid%3D999888")
                                .put("title", "超话视频标题")
                                .put("desc1", "作者B")
                                .put("pic", "https://example.com/v.jpg")
                                .put("video_duration", 65.0),
                        ),
                    ),
                ),
            )
        }
        val videoPage = parseSuperTopicTab(videoRoot, id, "${id}_-_video")
        assertEquals(listOf("999888"), videoPage.items.map { it.id })
        assertEquals("超话视频标题", videoPage.items.first().textHtml)
        assertEquals("01:05", videoPage.items.first().createdAt)
    }

    @Test
    fun parsesDesktopChaohuaPageResponse() {
        val root = JSONObject(
            """{
              "header": {
                "type": "sgPage",
                "data": {
                  "nick": "崩坏星穹铁道",
                  "oid": "1022:$id",
                  "portrait_hd": "https://example.com/hd.jpg",
                  "desc_list": [{"text": "简介：愿此行，终抵群星！"}],
                  "desc_more": [
                    {"desc_num": "390万", "desc": "帖子"},
                    {"desc_num": "506万", "desc": "开拓者"}
                  ]
                }
              },
              "channelInfo": {
                "channels": [
                  {"flowId": "${id}_-_recommend", "title": "热门", "channelId": "recommend"},
                  {"flowId": "${id}_-_feed", "title": "最新", "channelId": "feed"}
                ]
              },
              "items": [
                {"category": "cell", "type": "span"},
                {"category": "feed", "data": {"id": "1001", "text": "超话帖", "user": {"id": "42", "screen_name": "开拓者"}}}
              ],
              "moreInfo": {"params": {"page": 2, "since_id": "123"}}
            }"""
        )
        val parsed = parseDesktopSuperTopic(root, id)
        assertEquals("崩坏星穹铁道", parsed.info.title)
        assertEquals("愿此行，终抵群星！", parsed.info.description)
        assertEquals("https://example.com/hd.jpg", parsed.info.avatar)
        assertEquals("390万", parsed.info.postCount)
        assertEquals("506万", parsed.info.fanCount)
        assertEquals(listOf("1001"), parsed.items.map { it.id })
        assertEquals(listOf("热门", "最新"), parsed.tabs.map { it.label })
        assertTrue(parsed.nextCursor?.startsWith("ch:") == true)
    }

    private fun assertFailure(root: JSONObject) {
        failure(root)
    }

    private fun failure(root: JSONObject): ApiException {
        try {
            parseSuperTopic(root, id)
            fail("Expected ApiException")
        } catch (error: ApiException) {
            return error
        }
        throw AssertionError("Expected ApiException")
    }
}
