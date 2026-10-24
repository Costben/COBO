package com.cobo.app.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import com.cobo.app.App
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Search screen: hot list from the API, keyword search, result cards and returning
 * to the hot list.
 */
@RunWith(AndroidJUnit4::class)
class SearchE2eTest : UiE2eTest() {

    @Test
    fun hotList_rendersItems() {
        tapTag("top_search")
        waitText("微博热搜")
        val hot = io { App.repo.hot() }
        assertTrue("热搜为空", hot.isNotEmpty())
        waitText(hot.first().title)
    }

    @Test
    fun keywordSearch_showsResultsAndBackToHot() {
        tapTag("top_search")
        waitText("微博热搜")

        submitQuery("微博")
        waitText("“微博” 的结果")
        waitTag("search_card_0")

        tapText("返回热搜")
        waitText("微博热搜")
    }

    @Test
    fun searchHistory_recordsAndClears() {
        io { App.settings.clearSearchHistory() }
        tapTag("top_search")
        waitText("微博热搜")

        submitQuery("微博")
        waitText("“微博” 的结果")
        waitTag("search_card_0")

        tapText("返回热搜")
        waitText("搜索历史")
        waitText("微博")

        io { App.settings.clearSearchHistory() }
    }

    /**
     * Types into the COUI search field and submits the way the IME search key does. The IME is
     * dismissed first because it swallows the key event, while the field keeps focus.
     */
    private fun submitQuery(query: String) {
        tapTag("search_input")
        val input = waitTag("search_input")
        input.text = query
        waitUntil("搜索框没有接受输入") { waitTag("search_input").text == query }
        device.executeShellCommand("input keyevent 111")
        device.pressEnter()
    }

    @Test
    fun hotItem_opensSearchResults() {
        tapTag("top_search")
        waitText("微博热搜")
        val hot = io { App.repo.hot() }
        assertTrue("热搜为空", hot.isNotEmpty())

        val title = hot.first().title
        tapText(title)
        waitText("“$title” 的结果")
        waitAny(By.res("search_card_0"), By.text("没有找到相关微博"))
    }
}
