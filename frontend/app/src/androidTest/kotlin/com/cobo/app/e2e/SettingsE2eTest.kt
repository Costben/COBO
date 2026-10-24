package com.cobo.app.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import com.cobo.app.App
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * Settings screen: every entry is present, dialogs open and cancel, appearance
 * settings cycle and land back on their original values, and cache clearing reports
 * success.
 */
@RunWith(AndroidJUnit4::class)
class SettingsE2eTest : UiE2eTest() {

    private fun openSettings() {
        tapTag("bottom_tab_profile")
        tapTag("top_settings")
        waitText("设置")
        waitTag("settings_dark")
    }

    @Test
    fun settings_hasAllEntries() {
        openSettings()
        listOf(
            "settings_dark",
            "settings_font",
            "settings_accounts",
            "settings_drafts",
            "settings_history",
            "settings_favorites",
            "settings_topic",
            "settings_nearby",
            "settings_clear_cache",
            "settings_about",
            "settings_logout",
        ).forEach { scrollToTag(it) }
    }

    @Test
    fun darkMode_cyclesBackToOriginal() {
        openSettings()
        val before = App.settings.darkMode
        try {
            repeat(3) {
                val current = App.settings.darkMode
                tapTag("settings_dark")
                waitUntil("深色模式没有切换") { App.settings.darkMode != current }
            }
            assertEquals("深色模式没有回到初始值", before, App.settings.darkMode)
        } finally {
            App.settings.applyDarkMode(before)
        }
    }

    @Test
    fun fontScale_cyclesBackToOriginal() {
        openSettings()
        val before = App.settings.fontScale
        try {
            repeat(4) {
                val current = App.settings.fontScale
                tapTag("settings_font")
                waitUntil("字号没有切换") { abs(App.settings.fontScale - current) > 0.001f }
            }
            assertEquals("字号没有回到初始值", before, App.settings.fontScale, 0.001f)
        } finally {
            App.settings.applyFontScale(before)
        }
    }

    @Test
    fun aboutDialog_opensAndCloses() {
        openSettings()
        scrollToTag("settings_about")
        tapTag("settings_about")
        waitText("关于 Cobo")
        waitText("知道了")
        tapText("知道了")
        waitText("设置")
    }

    @Test
    fun logoutDialog_cancelsWithoutClearingSession() {
        openSettings()
        val cookie = App.repo.cookie()
        assertTrue("测试开始时没有会话", !cookie.isNullOrBlank())

        scrollToTag("settings_logout")
        tapTag("settings_logout")
        waitText("会清除本地保存的 Cookie，下次需要重新登录。")
        tapText("取消")
        waitText("设置")

        assertEquals("取消退出登录后会话被清掉了", cookie, App.repo.cookie())
    }

    @Test
    fun clearCache_reportsSuccess() {
        openSettings()
        scrollToTag("settings_clear_cache")
        tapTag("settings_clear_cache")
        scrollToText("已清理图片与内容缓存")
    }

    @Test
    fun accountManagement_opens() {
        openSettings()
        scrollToTag("settings_accounts")
        tapTag("settings_accounts")
        waitText("账号管理")
        waitAny(By.res("account_row_0"), By.text("还没有保存的账号"))
        back()
        waitText("设置")
    }
}
