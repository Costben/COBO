package com.cobo.app.data

import com.cobo.app.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class DetailSectionJumpTest {

    @Test
    fun detailSectionHeaderIndexAccountsForEditHistory() {
        assertEquals(1, detailSectionHeaderIndex(hasEditHistory = false))
        assertEquals(2, detailSectionHeaderIndex(hasEditHistory = true))
    }

    @Test
    fun computeDetailSectionFillerHeightKeepsHeaderAnchoredOnSparseTabs() {
        // 未启用稳锚模式时返回 0
        assertEquals(
            0,
            computeDetailSectionFillerHeightPx(
                usableViewportPx = 1800,
                activeItemCount = 0,
                preservePosition = false,
                headerAndFooterPx = 200,
                minItemHeightPx = 160,
            ),
        )
        // 空列表或仅剩极少条目时填满余量，防止 LazyColumn 拉回正文顶部
        assertEquals(
            1600,
            computeDetailSectionFillerHeightPx(
                usableViewportPx = 1800,
                activeItemCount = 0,
                preservePosition = true,
                headerAndFooterPx = 200,
                minItemHeightPx = 160,
            ),
        )
        assertEquals(
            1280,
            computeDetailSectionFillerHeightPx(
                usableViewportPx = 1800,
                activeItemCount = 2,
                preservePosition = true,
                headerAndFooterPx = 200,
                minItemHeightPx = 160,
            ),
        )
        // 当列表条目总预估高度足够撑满视口时不再增加额外尾部占位
        assertEquals(
            0,
            computeDetailSectionFillerHeightPx(
                usableViewportPx = 1800,
                activeItemCount = 12,
                preservePosition = true,
                headerAndFooterPx = 200,
                minItemHeightPx = 160,
            ),
        )
    }

    @Test
    fun parseRepostsClientExtractsItemsAndNextPageByTotalAndCursor() {
        val jsonMoreByTotal = JSONObject(
            """
            {
              "reposts": [
                {
                  "idstr": "9001",
                  "mid": "9001",
                  "text": "支持一下",
                  "user": {"id": "101", "idstr": "101", "screen_name": "用户甲"}
                }
              ],
              "total_number": 45,
              "next_cursor": 0
            }
            """.trimIndent(),
        )
        val first = parseRepostsClient(jsonMoreByTotal, curPage = 1)
        assertEquals(1, first.items.size)
        assertEquals("9001", first.items[0].id)
        assertEquals("用户甲", first.items[0].user?.name)
        assertEquals(2, first.nextPage)

        val lastPage = parseRepostsClient(jsonMoreByTotal, curPage = 3)
        assertEquals(0, lastPage.nextPage)
    }

    @Test
    fun parseRepostsWebHandlesPaginationAndEmptyData() {
        val json = JSONObject(
            """
            {
              "ok": 1,
              "data": {
                "max": 3,
                "data": [
                  {
                    "idstr": "9101",
                    "mid": "9101",
                    "text": "转发这条",
                    "user": {"id": "202", "idstr": "202", "screen_name": "用户乙"}
                  }
                ]
              }
            }
            """.trimIndent(),
        )
        val p1 = parseRepostsWeb(json, curPage = 1)
        assertEquals(1, p1.items.size)
        assertEquals(2, p1.nextPage)

        val p3 = parseRepostsWeb(json, curPage = 3)
        assertEquals(0, p3.nextPage)

        val empty = parseRepostsWeb(JSONObject("""{"ok": 0}"""), curPage = 1)
        assertEquals(0, empty.items.size)
        assertEquals(0, empty.nextPage)
    }

    @Test
    fun parseRepostsDesktopHandlesOkAndMaxPage() {
        val json = JSONObject(
            """
            {
              "ok": 1,
              "max_page": 2,
              "data": [
                {
                  "idstr": "9201",
                  "mid": "9201",
                  "text": "桌面端转发",
                  "user": {"id": "303", "idstr": "303", "screen_name": "用户丙"}
                }
              ]
            }
            """.trimIndent(),
        )
        val p1 = parseRepostsDesktop(json, curPage = 1)
        assertEquals(1, p1.items.size)
        assertEquals(2, p1.nextPage)

        val p2 = parseRepostsDesktop(json, curPage = 2)
        assertEquals(0, p2.nextPage)
    }
}
