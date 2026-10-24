package com.cobo.app.e2e

import android.content.Context
import android.graphics.Rect
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.cobo.app.App
import com.cobo.app.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import java.io.File
import java.util.regex.Pattern

const val APP_PACKAGE = "com.cobo.app"
const val UI_WAIT = 30_000L

/**
 * Base class for tests that drive the real UI. The activity is launched through
 * ActivityScenario instead of `am start`, because the instrumentation runs inside the
 * app process and force-stopping the package would kill the test runner itself.
 */
abstract class UiE2eTest {
    protected val device: UiDevice =
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private var scenario: ActivityScenario<MainActivity>? = null

    protected val destructive: Boolean
        get() = InstrumentationRegistry.getArguments().getString("destructive") == "true"

    @Before
    fun launchApp() {
        timing("begin")
        App.init(ApplicationProvider.getApplicationContext<Context>())
        assumeTrue("设备上没有登录会话（session.json），跳过 UI 测试", App.repo.hasSession())
        device.executeShellCommand("logcat -c")
        timing("launch")
        scenario = ActivityScenario.launch(MainActivity::class.java)
        timing("launched")
        device.waitForIdle(1_000)
        waitTag("bottom_tab_feed", UI_WAIT).click()
        device.waitForIdle(1_000)
        waitAny(By.text("推荐"), By.text("关注"))
        // Tests need a deterministic starting page; the app itself restores the last one.
        waitTag("feed_tab_recommend", UI_WAIT).click()
        waitTag("feed_page_2", UI_WAIT)
        timing("shell-ready")
    }

    @After
    fun closeApp() {
        timing("close-begin")
        assertNoAppCrash()
        timing("crash-checked")
        // ActivityScenario.close() waits for the activity to become fully idle, which
        // never happens while an indeterminate progress animation is on screen.
        runCatching { scenario?.onActivity { it.finish() } }
        scenario = null
        timing("closed")
    }

    private fun timing(step: String) {
        android.util.Log.i("E2E_TIMING", "$step ${System.currentTimeMillis()}")
    }

    private fun assertNoAppCrash() {
        val log = device.executeShellCommand("logcat -d -t 800")
        val index = log.indexOf("FATAL EXCEPTION")
        if (index >= 0) {
            val block = log.substring(index, minOf(log.length, index + 4_000))
            assertFalse("应用发生崩溃：\n$block", block.contains(APP_PACKAGE))
        }
    }

    protected fun waitTag(tag: String, timeout: Long = UI_WAIT): UiObject2 =
        device.wait(Until.findObject(By.res(tag)), timeout)
            ?: throw AssertionError("找不到节点 [$tag]\n${dump()}")

    protected fun waitText(text: String, timeout: Long = UI_WAIT): UiObject2 =
        device.wait(Until.findObject(By.text(text)), timeout)
            ?: throw AssertionError("找不到文本 [$text]\n${dump()}")

    protected fun waitTextContains(text: String, timeout: Long = UI_WAIT): UiObject2 =
        device.wait(Until.findObject(By.textContains(text)), timeout)
            ?: throw AssertionError("找不到包含 [$text] 的文本\n${dump()}")

    protected fun waitTextStartsWith(prefix: String, timeout: Long = UI_WAIT): UiObject2 =
        device.wait(Until.findObject(By.textStartsWith(prefix)), timeout)
            ?: throw AssertionError("找不到以 [$prefix] 开头的文本\n${dump()}")

    protected fun waitDesc(desc: String, timeout: Long = UI_WAIT): UiObject2 =
        device.wait(Until.findObject(By.desc(desc)), timeout)
            ?: throw AssertionError("找不到描述 [$desc]\n${dump()}")

    protected fun waitAny(vararg selectors: BySelector): UiObject2 {
        val deadline = System.currentTimeMillis() + UI_WAIT
        while (System.currentTimeMillis() < deadline) {
            selectors.forEach { selector ->
                device.findObject(selector)?.let { return it }
            }
            Thread.sleep(150)
        }
        throw AssertionError("等待任一节点超时：${selectors.joinToString()}\n${dump()}")
    }

    protected fun existsTag(tag: String, timeout: Long = 6_000): Boolean =
        device.wait(Until.hasObject(By.res(tag)), timeout)

    protected fun existsText(text: String, timeout: Long = 6_000): Boolean =
        device.wait(Until.hasObject(By.text(text)), timeout)

    protected fun tapTag(tag: String) = clickSelector(By.res(tag), "节点 [$tag]")

    protected fun tapText(text: String) = clickSelector(By.text(text), "文本 [$text]")

    protected fun clickText(text: String, timeout: Long = UI_WAIT) =
        clickSelector(By.text(text), "文本 [$text]", timeout)

    protected fun tapDesc(desc: String) = clickSelector(By.desc(desc), "描述 [$desc]")

    /** Clicks a node, retrying when the hierarchy changes under the selector. */
    private fun clickSelector(selector: BySelector, label: String, timeout: Long = UI_WAIT) {
        val deadline = System.currentTimeMillis() + timeout
        while (System.currentTimeMillis() < deadline) {
            val node = device.findObject(selector)
            if (node != null) {
                val clicked = runCatching {
                    node.click()
                    device.waitForIdle(1_000)
                }.isSuccess
                if (clicked) return
            }
            Thread.sleep(150)
        }
        throw AssertionError("点击 $label 失败\n${dump()}")
    }

    protected fun scrollToTag(tag: String, maxSwipes: Int = 12): UiObject2 {
        device.wait(Until.hasObject(By.res(tag)), 2_000)
        repeat(maxSwipes + 1) {
            val node = device.findObject(By.res(tag))
            if (node != null && runCatching { isFullyVisible(node) }.getOrDefault(false)) {
                return settledTag(tag)
            }
            swipeUp(0.18f)
            device.waitForIdle(1_000)
            Thread.sleep(300)
        }
        throw AssertionError("滚动后仍找不到节点 [$tag]\n${dump()}")
    }

    protected fun scrollToText(text: String, maxSwipes: Int = 12): UiObject2 {
        device.wait(Until.hasObject(By.text(text)), 2_000)
        repeat(maxSwipes + 1) {
            val node = device.findObject(By.text(text))
            if (node != null && runCatching { isFullyVisible(node) }.getOrDefault(false)) {
                return settledText(text)
            }
            swipeUp(0.18f)
            device.waitForIdle(1_000)
            Thread.sleep(300)
        }
        throw AssertionError("滚动后仍找不到文本 [$text]\n${dump()}")
    }

    private fun isFullyVisible(node: UiObject2): Boolean {
        val full = Rect()
        node.accessibilityNodeInfo.getBoundsInScreen(full)
        val visible = node.visibleBounds
        return full == visible &&
            visible.top >= 0 &&
            visible.bottom <= device.displayHeight
    }

    private fun settledTag(tag: String): UiObject2 {
        device.waitForIdle(1_000)
        Thread.sleep(250)
        return waitTag(tag)
    }

    private fun settledText(text: String): UiObject2 {
        device.waitForIdle(1_000)
        Thread.sleep(250)
        return waitText(text)
    }

    protected fun back() {
        device.pressBack()
        device.waitForIdle(1_000)
    }

    protected fun swipeUp(distance: Float = 0.45f) {
        val width = device.displayWidth
        val height = device.displayHeight
        val bottom = (height * 0.72f).toInt()
        val top = (height * (0.72f - distance)).toInt()
        device.swipe(width / 2, bottom, width / 2, top, 60)
        device.waitForIdle(1_000)
    }

    protected fun swipeDown(distance: Float = 0.45f) {
        val width = device.displayWidth
        val height = device.displayHeight
        val top = (height * 0.28f).toInt()
        val bottom = (height * (0.28f + distance)).toInt()
        device.swipe(width / 2, top, width / 2, bottom, 60)
        device.waitForIdle(1_000)
    }

    protected fun swipeLeft() {
        val width = device.displayWidth
        val height = device.displayHeight
        device.swipe((width * 0.85f).toInt(), height / 2, (width * 0.15f).toInt(), height / 2, 50)
        device.waitForIdle(1_000)
    }

    protected fun swipeRight() {
        val width = device.displayWidth
        val height = device.displayHeight
        device.swipe((width * 0.15f).toInt(), height / 2, (width * 0.85f).toInt(), height / 2, 50)
        device.waitForIdle(1_000)
    }

    protected fun textOf(tag: String): String = waitTag(tag).text.orEmpty()

    protected fun waitTextChange(tag: String, previous: String, timeout: Long = UI_WAIT): String {
        val deadline = System.currentTimeMillis() + timeout
        while (System.currentTimeMillis() < deadline) {
            val current = device.findObject(By.res(tag))?.text.orEmpty()
            if (current.isNotEmpty() && current != previous) return current
            Thread.sleep(150)
        }
        throw AssertionError("节点 [$tag] 文本未变化，仍为 [$previous]\n${dump()}")
    }

    protected fun waitGone(selector: BySelector, timeout: Long = UI_WAIT) {
        if (!device.wait(Until.gone(selector), timeout)) {
            throw AssertionError("节点未消失：$selector\n${dump()}")
        }
    }

    protected fun waitUntil(message: String, timeout: Long = UI_WAIT, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeout
        while (System.currentTimeMillis() < deadline) {
            if (runCatching(condition).getOrDefault(false)) return
            Thread.sleep(150)
        }
        throw AssertionError("$message\n${dump()}")
    }

    protected fun dump(): String =
        device.executeShellCommand("uiautomator dump /dev/tty").take(6_000)

    protected fun cacheFile(url: String, cookie: String?): File =
        File(App.context.filesDir, "http-cache/${App.cache.key(url, cookie)}.json")

    protected fun hasAnyFeedCard(): Boolean =
        device.findObjects(By.res(Pattern.compile("feed_card_\\d+"))).isNotEmpty()

    /**
     * Opens the top feed card by tapping its metadata row. The row is never clickable
     * itself, so the tap reaches the card's onClick, while the card center may land on
     * an image, video or action icon.
     */
    protected fun openFirstFeedCard() {
        val card = waitTag("feed_card_0")
        val bounds = card.visibleBounds
        val y = (bounds.bottom - 60).coerceAtMost(device.displayHeight - 300)
        device.click(bounds.left + 60, y)
        device.waitForIdle(1_000)
    }

    protected fun <T> io(block: suspend () -> T): T = runBlocking { block() }
}
