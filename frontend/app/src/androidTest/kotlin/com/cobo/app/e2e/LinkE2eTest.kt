package com.cobo.app.e2e

import android.graphics.Rect
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.cobo.app.App
import java.util.regex.Pattern
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 正文里的链接是微博返回的站内相对路径（`/status/<id>`、`/n/<name>`）。这条用例真的去点
 * 「…全文」，确认它进的是应用内详情，而不是被 WebView 当成 `file:///` 打开成空白网页。
 */
class LinkE2eTest : UiE2eTest() {

    @Test
    fun fullTextLink_opensDetailInsteadOfWebPage() {
        // 点热搜会写一条搜索历史，跑完要还原成测试前的样子。
        val historyBefore = App.settings.searchHistory.toList()
        try {
            checkFullTextLink()
        } finally {
            App.settings.clearSearchHistory()
            historyBefore.reversed().forEach { App.settings.pushSearchHistory(it) }
        }
    }

    private fun checkFullTextLink() {
        val node = scrollToFullTextCard()
        assumeTrue("信息流里没有带「全文」的长微博，跳过", node != null)

        val bounds = Rect()
        node!!.accessibilityNodeInfo.getBoundsInScreen(bounds)
        val density = App.context.resources.displayMetrics.density
        val lineHeight = (22f * density).toInt().coerceAtLeast(1)
        val y = (bounds.bottom - lineHeight / 2).coerceIn(bounds.top + 1, bounds.bottom - 1)
        // 「全文」可能缩在末行行尾，也可能被折到末行行首，所以末行从左到右都试一次。
        val probes = listOf(
            bounds.left + (bounds.width() * 0.06f).toInt(),
            bounds.left + bounds.width() / 2,
            bounds.right - (bounds.width() * 0.06f).toInt(),
        )

        android.util.Log.i("E2E_LINK", "card=${bounds.toShortString()} probes=$probes y=$y")
        var openedDetail = false
        probes.forEach { x ->
            device.click(x, y)
            device.waitForIdle(1_000)
            val detail = device.wait(Until.hasObject(By.text("微博正文")), 4_000)
            android.util.Log.i("E2E_LINK", "x=$x detail=$detail")
            if (detail) {
                openedDetail = true
                back()
                waitBackOnList()
            } else if (device.wait(Until.hasObject(By.text("网页")), 2_500)) {
                assertFalse(
                    "点「全文」打开了 file:// 网页\n${dump()}",
                    device.wait(Until.hasObject(By.textContains("file:///")), 3_000) ||
                        device.wait(Until.hasObject(By.textContains("ERR_ACCESS_DENIED")), 500),
                )
                // 命中的是正文里的 @用户 之类正常外链，返回后继续试下一个位置。
                back()
                waitBackOnList()
            }
        }
        assertTrue("点「全文」没有进入微博详情\n${dump()}", openedDetail)
    }

    /** 找到一张正文以「全文」结尾、并且整块可见的卡片。 */
    private fun scrollToFullTextCard(): UiObject2? {
        findFullTextCard(12)?.let { return it }
        // 推荐流当天可能全是短视频，没有长文；热搜第一条的结果页基本都有「全文」。
        openFirstHotSearch() ?: return null
        return findFullTextCard(12)
    }

    private fun findFullTextCard(scrolls: Int): UiObject2? {
        repeat(scrolls) {
            device.findObjects(By.textContains("全文"))
                .firstOrNull { it.text.orEmpty().endsWith("全文") }
                ?.let { node ->
                    val full = Rect()
                    node.accessibilityNodeInfo.getBoundsInScreen(full)
                    if (full.top >= 0 && full.bottom <= device.displayHeight) return node
                }
            swipeUp(0.18f)
            device.waitForIdle(1_000)
        }
        return null
    }

    /** 返回后可能落在信息流，也可能落在热搜结果页；滚动过之后首屏卡片已经被回收。 */
    private fun waitBackOnList() {
        val anyCard = By.res(Pattern.compile("(?:feed|search)_card_\\d+"))
        repeat(2) {
            if (device.wait(Until.hasObject(anyCard), 8_000)) return
            back()
        }
        throw AssertionError("返回后没有回到卡片列表\n${dump()}")
    }

    private fun openFirstHotSearch(): Unit? {
        runCatching {
            tapTag("top_search")
            val header = waitText("微博热搜")
            val bounds = header.visibleBounds
            device.click(bounds.left + 300, bounds.bottom + 90)
            device.waitForIdle(1_500)
        }.onFailure { return null }
        return Unit
    }
}
