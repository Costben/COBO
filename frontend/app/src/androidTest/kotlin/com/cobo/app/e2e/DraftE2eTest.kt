package com.cobo.app.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import com.cobo.app.App
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Publish screen and the local draft lifecycle. Nothing is sent to the server.
 */
@RunWith(AndroidJUnit4::class)
class DraftE2eTest : UiE2eTest() {

    /** Back may first dismiss the IME; press again when the publish screen is still up. */
    private fun leavePublish() {
        device.pressBack()
        device.waitForIdle(1_000)
        Thread.sleep(700)
        if (device.hasObject(By.res("publish_input"))) {
            device.pressBack()
            device.waitForIdle(1_000)
        }
        waitGone(By.res("publish_input"))
    }

    @Test
    fun draft_savedOnLeave_restoredAndDeleted() {
        val text = "E2E 草稿 ${System.currentTimeMillis()}"
        try {
            tapTag("bottom_publish")
            waitText("发微博")

            val input = waitTag("publish_input")
            input.text = text
            waitUntil("发布输入框没有接受文本") { waitTag("publish_input").text == text }

            leavePublish()
            waitTag("bottom_tab_feed")

            tapTag("bottom_tab_profile")
            tapTag("top_settings")
            waitText("设置")
            tapTag("settings_drafts")
            waitText("草稿箱")
            waitText(text)

            val row = waitTag("draft_row_0")
            row.click()
            device.waitForIdle(1_000)

            waitText("发微博")
            assertEquals("草稿没有回填到发布页", text, waitTag("publish_input").text)

            leavePublish()
            waitText("草稿箱")
            waitText(text)

            waitTag("draft_row_0")
            tapDesc("删除")
            waitGone(By.text(text))
        } finally {
            App.store.drafts().filter { it.text == text }.forEach { App.store.removeDraft(it.time) }
        }
    }
}
