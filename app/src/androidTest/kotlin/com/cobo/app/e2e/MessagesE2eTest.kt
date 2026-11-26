package com.cobo.app.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.cobo.app.App
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Messages tab: list rendering, opening a chat, and the notification WebView entry.
 */
@RunWith(AndroidJUnit4::class)
class MessagesE2eTest : UiE2eTest() {

    @Test
    fun messageList_rendersRows() {
        tapTag("bottom_tab_messages")
        val hasRows = device.wait(Until.hasObject(By.res("message_row_0")), 20_000)
        if (!hasRows) {
            waitText("还没有私信")
            return
        }
        val name = io { App.repo.messages(1) }.firstOrNull()?.user?.name.orEmpty()
        assertTrue("私信发送者名为空", name.isNotBlank())
        waitText(name)
    }

    @Test
    fun messageRow_opensChatAndReturns() {
        tapTag("bottom_tab_messages")
        if (!device.wait(Until.hasObject(By.res("message_row_0")), 20_000)) {
            waitText("还没有私信")
            return
        }
        val name = io { App.repo.messages(1) }.firstOrNull()?.user?.name.orEmpty()
        assertTrue("私信发送者名为空", name.isNotBlank())

        tapTag("message_row_0")
        waitGone(By.res("message_row_0"))
        waitText(name)

        tapDesc("返回")
        waitTag("message_row_0")
    }

    @Test
    fun notificationButton_opensWebView() {
        tapTag("bottom_tab_messages")
        tapText("通知")
        waitGone(By.res("message_row_0"))
        waitText("通知")
        tapDesc("返回")
        tapTag("bottom_tab_messages")
    }
}
