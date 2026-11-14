package com.cobo.app.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import com.cobo.app.App
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Home inline search: top-bar search expansion, hot list from the API, keyword search,
 * history single-item deletion, ChipTabGroup category/sort switching and cascading menu.
 */
@RunWith(AndroidJUnit4::class)
class SearchE2eTest : UiE2eTest() {

    @Test
    fun homeTopSearch_expandsCancelsBacksAndSubmits() {
        tapTag("top_search")
        waitText("取消")
        val input = waitDesc("搜索微博 / 用户 / 话题")
        input.text = "测试"
        tapText("取消")
        waitTag("top_search")
        waitGone(By.text("取消"))

        tapTag("top_search")
        waitText("取消")
        back()
        waitTag("top_search")
        waitGone(By.text("取消"))

        tapTag("top_search")
        waitText("取消")
        val feedInput = waitDesc("搜索微博 / 用户 / 话题")
        feedInput.text = "微博"
        waitUntil("首页搜索框没有接受输入") { waitDesc("搜索微博 / 用户 / 话题").text == "微博" }
        device.executeShellCommand("input keyevent 111")
        device.pressEnter()
        waitTag("search_tabs")
        assertNull("结果顶部不应显示“的结果”", device.findObject(By.textContains("的结果")))
        assertNull("结果顶部不应显示“返回热搜”", device.findObject(By.text("返回热搜")))
        waitTag("search_card_0")

        back()
        waitText("全部热搜")
        back()
        waitTag("top_search")
    }

    @Test
    fun homeSearchOverlay_coversBottomBarAndPreservesResultAfterDetail() {
        openFeedSearchPanel()
        waitGone(By.res("bottom_tab_messages"))
        device.click(device.displayWidth / 3, device.displayHeight - 140)
        device.waitForIdle(400)
        waitText("全部热搜")

        submitQuery("微博")
        waitTag("search_tabs")
        waitTag("search_card_0")

        tapTag("search_card_0")
        waitText("微博正文")
        back()

        waitTag("search_tabs")
        waitTag("search_card_0")
        tapText("取消")
        waitTag("top_search")
        waitGone(By.text("取消"))
    }

    @Test
    fun hotList_rendersItems() {
        openFeedSearchPanel()
        val hot = io { App.repo.hot() }
        assertTrue("热搜为空", hot.isNotEmpty())
        waitText(hot.first().title)
    }

    @Test
    fun keywordSearch_showsResultsAndBackToHot() {
        openFeedSearchPanel()

        submitQuery("微博")
        waitTag("search_tabs")
        waitTag("search_card_0")
        assertNull("结果顶部不应显示“返回热搜”", device.findObject(By.text("返回热搜")))

        back()
        waitText("全部热搜")
    }

    @Test
    fun searchHistory_recordsAndClears() {
        io { App.settings.clearSearchHistory() }
        openFeedSearchPanel()

        submitQuery("微博")
        waitTag("search_tabs")
        waitTag("search_card_0")

        back()
        waitText("搜索历史")
        waitText("微博")

        io { App.settings.clearSearchHistory() }
    }

    @Test
    fun searchHistory_longPressDeletesSingleItemAndDismissesOnBlank() {
        io {
            App.settings.clearSearchHistory()
            App.settings.pushSearchHistory("科技")
            App.settings.pushSearchHistory("微博")
        }
        openFeedSearchPanel()
        waitText("搜索历史")

        val chip = waitText("微博")
        val bounds = chip.visibleBounds
        device.swipe(bounds.centerX(), bounds.centerY(), bounds.centerX(), bounds.centerY(), 80)
        waitTag("search_history_delete")
        waitDesc("删除 微博")

        tapText("搜索历史")
        waitGone(By.res("search_history_delete"))
        waitText("微博")

        val chipAgain = waitText("微博").visibleBounds
        device.swipe(chipAgain.centerX(), chipAgain.centerY(), chipAgain.centerX(), chipAgain.centerY(), 80)
        waitTag("search_history_delete")
        tapTag("search_history_delete")
        waitGone(By.text("微博"))
        waitText("科技")

        io { App.settings.clearSearchHistory() }
    }

    @Test
    fun userSearch_switchesToUserResults() {
        openFeedSearchPanel()

        submitQuery("微博")
        waitTag("search_tabs")

        tapSearchTab("用户")
        waitAny(By.res("search_user_0"), By.text("没有找到相关用户"))
    }

    @Test
    fun topicSearch_switchesToTopicResults() {
        openFeedSearchPanel()

        submitQuery("微博")
        waitTag("search_tabs")

        tapSearchTab("话题")
        waitAny(By.res("search_topic_0"), By.text("没有找到相关话题"))
    }

    @Test
    fun chipTabGroup_sortPopupAndLeadingButtonVisibility() {
        openFeedSearchPanel()
        submitQuery("微博")
        val tabs = waitTag("search_tabs")
        waitTag("search_card_0")

        val menuButton = findLeadingMenuButton(tabs)
        assertTrue("微博分类下应显示前置四宫格按钮", menuButton != null)
        menuButton!!.click()
        val sortTrigger = waitText("排序方式")
        assertTrue("微博分类下“排序方式”应可用", sortTrigger.isEnabled)
        sortTrigger.click()
        waitText("综合")
        waitText("最新")
        waitText("最热")
        tapText("最新")
        waitGone(By.text("最热"))
        waitAny(By.res("search_card_0"), By.text("没有找到相关微博"))

        val tabsAfterSort = waitTag("search_tabs")
        val menuButtonInWeibo = findLeadingMenuButton(tabsAfterSort)
        assertTrue("切排序后前置按钮仍应存在", menuButtonInWeibo != null)
        menuButtonInWeibo!!.click()
        waitText("排序方式")
        tapText("用户")
        waitGone(By.text("排序方式"))
        waitAny(By.res("search_user_0"), By.text("没有找到相关用户"))

        val tabsInUsers = waitTag("search_tabs")
        assertEquals(3, tabNodes(tabsInUsers).size)
        val menuButtonInUsers = findLeadingMenuButton(tabsInUsers)
        assertTrue("用户分类下仍应显示前置四宫格按钮", menuButtonInUsers != null)
        menuButtonInUsers!!.click()
        val disabledSort = waitText("排序方式")
        assertTrue("用户分类下“排序方式”应置灰禁用", !disabledSort.isEnabled)
        disabledSort.click()
        device.waitForIdle(300)
        assertNull("禁用的“排序方式”不应展开二级菜单", device.findObject(By.text("最热")))

        tapText("话题")
        waitGone(By.text("排序方式"))
        waitAny(By.res("search_topic_0"), By.text("没有找到相关话题"))
        val menuButtonInTopics = findLeadingMenuButton(waitTag("search_tabs"))
        assertTrue("话题分类下仍应显示前置四宫格按钮", menuButtonInTopics != null)
        menuButtonInTopics!!.click()
        assertTrue("话题分类下“排序方式”应置灰禁用", !waitText("排序方式").isEnabled)
        tapText("微博")
        waitGone(By.text("排序方式"))
        waitAny(By.res("search_card_0"), By.text("没有找到相关微博"))
    }

    private fun tapSearchTab(label: String) {
        val tabs = waitTag("search_tabs")
        val target = tabs.findObject(By.text(label))
            ?: throw AssertionError("search_tabs 下找不到分类 [$label]\n${dump()}")
        target.click()
        device.waitForIdle(1_000)
    }

    private fun tabNodes(tabs: UiObject2): List<UiObject2> {
        val hsv = tabs.findObject(By.clazz("android.widget.HorizontalScrollView"))
            ?: throw AssertionError("search_tabs 下找不到 HorizontalScrollView\n${dump()}")
        return hsv.children
    }

    /**
     * `ChipTabSurface` uses `Role.Tab` (`selectable`), which exposes `isSelected = true` and
     * `isClickable = false` while selected. The leading icon-only grid button is always selected,
     * has no `HorizontalScrollView` descendant, and stays visible across all categories.
     */
    private fun findLeadingMenuButton(tabs: UiObject2): UiObject2? =
        tabs.children.firstOrNull { child ->
            child.isSelected && child.findObject(By.clazz("android.widget.HorizontalScrollView")) == null
        }

    private fun openFeedSearchPanel() {
        tapTag("top_search")
        waitText("取消")
        waitText("全部热搜")
    }

    /**
     * Types into the COUI search field and submits the way the IME search key does. The IME is
     * dismissed first because it swallows the key event, while the field keeps focus.
     */
    private fun submitQuery(query: String) {
        val input = waitDesc("搜索微博 / 用户 / 话题")
        input.text = query
        waitUntil("搜索框没有接受输入") { waitDesc("搜索微博 / 用户 / 话题").text == query }
        device.executeShellCommand("input keyevent 111")
        device.pressEnter()
    }

    @Test
    fun hotItem_opensSearchResults() {
        openFeedSearchPanel()
        val hot = io { App.repo.hot() }
        assertTrue("热搜为空", hot.isNotEmpty())

        val title = hot.first().title
        tapText(title)
        waitTag("search_tabs")
        waitAny(By.res("search_card_0"), By.text("没有找到相关微博"))
    }
}
