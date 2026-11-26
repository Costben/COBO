package com.cobo.app.data

import com.cobo.app.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        assertEquals("2", clientPage.sinceId)

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
}
