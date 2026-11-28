package com.cobo.app.data

import com.cobo.app.json.JSONObject
import com.cobo.app.platform.PlatformContext
import com.cobo.app.ui.components.ImageSaveState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MyContentParseTest {

    @Test
    fun extractLikeTabContainerId_findsLikeTabAcrossShapes() {
        val clientProfile = JSONObject(
            """
            {
              "tabsInfo": {
                "tabs": [
                  {"tabKey": "weibo", "title": "微博", "containerid": "10760312345"},
                  {"tabKey": "like", "title": "赞", "containerid": "23086912345_-_mix"}
                ]
              }
            }
            """.trimIndent()
        )
        assertEquals("23086912345_-_mix", extractLikeTabContainerId(clientProfile))

        val webProfile = JSONObject(
            """
            {
              "ok": 1,
              "data": {
                "tabsInfo": {
                  "tabs": [
                    {"tab_type": "weibo", "title": "微博", "containerid": "10760312345"},
                    {"tab_type": "like", "title": "赞", "containerid": "23086912345"}
                  ]
                }
              }
            }
            """.trimIndent()
        )
        assertEquals("23086912345", extractLikeTabContainerId(webProfile))
        assertNull(extractLikeTabContainerId(JSONObject("{}")))
    }

    @Test
    fun parseCloudFavoritesPayload_parsesClientAndDesktopFormats() {
        val clientRoot = JSONObject(
            """
            {
              "favorites": [
                {"favorited_time": "2026-11-22", "status": {"id": "5001", "text": "第一条收藏"}},
                {"favorited_time": "2026-11-21", "status": {"id": "5002", "text": "第二条收藏"}}
              ],
              "total_number": 25
            }
            """.trimIndent()
        )
        val clientPage = parseCloudFavoritesPayload(clientRoot, page = 1, pageSize = 20)
        assertEquals(listOf("5001", "5002"), clientPage.items.map { it.id })
        assertTrue(clientPage.items.all { it.favorited })
        assertEquals("2", clientPage.sinceId)

        // weibo.com/ajax/favorites/all_fav 不带 with_total 时返回 data 数组
        val desktopArrayRoot = JSONObject(
            """
            {
              "ok": 1,
              "count": 20,
              "data": [
                {"id": "5010", "text": "数组格式收藏一"},
                {"id": "5011", "text": "数组格式收藏二"}
              ]
            }
            """.trimIndent()
        )
        val desktopArrayPage = parseCloudFavoritesPayload(desktopArrayRoot, page = 1, pageSize = 20)
        assertEquals(listOf("5010", "5011"), desktopArrayPage.items.map { it.id })
        assertTrue(desktopArrayPage.items.all { it.favorited })
        assertNull(desktopArrayPage.sinceId)

        // weibo.com/ajax/favorites/all_fav 带 with_total=true 时返回 data.status 数组 + total_page
        val desktopWithTotalPage1 = JSONObject(
            """
            {
              "ok": 1,
              "count": 20,
              "total_page": 2,
              "data": {
                "status": [
                  {"id": "5020", "text": "with_total 第一页"}
                ],
                "total_number": 21
              }
            }
            """.trimIndent()
        )
        val withTotal1 = parseCloudFavoritesPayload(desktopWithTotalPage1, page = 1, pageSize = 20)
        assertEquals(listOf("5020"), withTotal1.items.map { it.id })
        assertTrue(withTotal1.items.all { it.favorited })
        assertEquals("2", withTotal1.sinceId)

        val desktopWithTotalPage2 = JSONObject(
            """
            {
              "ok": 1,
              "count": 20,
              "total_page": 2,
              "data": {
                "status": [
                  {"id": "5021", "text": "with_total 第二页"}
                ],
                "total_number": 21
              }
            }
            """.trimIndent()
        )
        val withTotal2 = parseCloudFavoritesPayload(desktopWithTotalPage2, page = 2, pageSize = 20)
        assertEquals(listOf("5021"), withTotal2.items.map { it.id })
        assertNull(withTotal2.sinceId)

        // 兼容旧版 data.list 结构
        val desktopLastPage = JSONObject(
            """
            {
              "ok": 1,
              "data": {
                "list": [
                  {"id": "5003", "text": "最后一条收藏"}
                ],
                "total_number": 21
              }
            }
            """.trimIndent()
        )
        val page2 = parseCloudFavoritesPayload(desktopLastPage, page = 2, pageSize = 20)
        assertEquals(listOf("5003"), page2.items.map { it.id })
        assertNull(page2.sinceId)
    }

    @Test
    fun parseCloudFavoritesPayload_throwsOnErrorOk() {
        val expiredRoot = JSONObject("""{"ok": -100, "msg": "登录已过期"}""")
        try {
            parseCloudFavoritesPayload(expiredRoot, page = 1)
            fail("Expected ApiException when ok != 1")
        } catch (e: ApiException) {
            assertEquals("-100", e.errno)
            assertEquals("登录已过期", e.message)
        }
    }

    @Test
    fun favoriteErrorHelpers_handleIdempotentAndReadableErrors() {
        assertTrue(
            isAlreadyMatchingFavoriteState(
                ApiException("you have collected this status!", errno = "20704"),
                targetFavorited = true,
            ),
        )
        assertTrue(
            isAlreadyMatchingFavoriteState(
                ApiHttpException(400, """{"ok":0,"message":"you have collected this status!"}"""),
                targetFavorited = true,
            ),
        )
        assertTrue(
            isAlreadyMatchingFavoriteState(
                ApiException("you have not collected this status!", errno = "20705"),
                targetFavorited = false,
            ),
        )
        assertFalse(
            isAlreadyMatchingFavoriteState(
                ApiException("permission denied", errno = "10008"),
                targetFavorited = true,
            ),
        )
        assertEquals(
            "收藏数量已达上限",
            formatFavoriteErrorMessage(
                ApiHttpException(400, """{"ok":0,"message":"收藏数量已达上限"}"""),
            ),
        )
    }

    @Test
    fun imageSaveState_flashNotifiesToastSink() {
        val messages = mutableListOf<String>()
        val state = ImageSaveState(
            context = PlatformContext(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            onNotify = { messages.add(it) },
        )
        state.flash("已保存到相册")
        state.flash("   ")
        assertEquals(listOf("已保存到相册"), messages)
        assertEquals("已保存到相册", state.hud)
        assertEquals(1, state.tick)
    }

    @Test
    fun imageSaveState_saveOneEmitsSingleResultToast() {
        val messages = mutableListOf<String>()
        val state = ImageSaveState(
            context = PlatformContext(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            onNotify = { messages.add(it) },
            saveStill = { Result.success(Unit) },
            saveLive = { Result.success(Unit) },
        )
        state.saveOne("https://wx1.sinaimg.cn/large/demo.jpg")
        assertEquals(listOf("已保存到相册"), messages)

        messages.clear()
        state.saveOne(
            com.cobo.app.ui.components.SaveImageItem(
                url = "https://wx1.sinaimg.cn/large/demo.jpg",
                liveUrl = "https://video.weibo.com/live.mov",
            ),
        )
        assertEquals(listOf("已保存实况图到相册"), messages)
    }

    @Test
    fun imageSaveState_saveAllEmitsSingleSummaryToast() {
        val messages = mutableListOf<String>()
        val state = ImageSaveState(
            context = PlatformContext(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            onNotify = { messages.add(it) },
            saveStill = { Result.success(Unit) },
        )
        state.saveAll(
            listOf(
                com.cobo.app.ui.components.SaveImageItem("https://wx1.sinaimg.cn/large/a.jpg"),
                com.cobo.app.ui.components.SaveImageItem("https://wx1.sinaimg.cn/large/b.jpg"),
            ),
        )
        assertEquals(listOf("已保存 2 张到相册"), messages)
    }
}
