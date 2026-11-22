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
            "settings_predictive_back_entry",
            "settings_feed_left_tab",
            "settings_feed_center_tab",
            "settings_feed_right_tab",
            "settings_collapse_follow_groups",
            "settings_show_vip_level",
            "settings_show_fan_level",
            "settings_show_verified_badge",
            "settings_in_app_browser",
            "settings_confirm_like",
            "settings_accounts",
            "settings_drafts",
            "settings_history",
            "settings_favorites",
            "settings_topic",
            "settings_nearby",
            "settings_update",
            "settings_changelog",
            "settings_auto_update",
            "settings_prerelease",
            "settings_in_app_update",
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
    fun predictiveBack_togglesAndRestores() {
        openSettings()
        tapTag("settings_predictive_back_entry")
        waitText("预测性返回手势设置")
        val before = App.settings.predictiveBack
        try {
            tapTag("settings_predictive_back")
            waitUntil("预测性返回开关没有切换") { App.settings.predictiveBack != before }
            tapTag("settings_predictive_back")
            waitUntil("预测性返回开关没有切回") { App.settings.predictiveBack == before }
        } finally {
            App.settings.applyPredictiveBack(before)
        }
    }

    @Test
    fun predictiveBackFlingToggles_toggleAndRestore() {
        openSettings()
        tapTag("settings_predictive_back_entry")
        waitText("预测性返回手势设置")
        val beforeMaster = App.settings.predictiveBack
        val beforeCard = App.settings.predictiveBackCardFling
        val beforeImage = App.settings.predictiveBackImageFling
        val beforeBlur = App.settings.predictiveBackBlur
        try {
            if (!App.settings.predictiveBack) {
                tapTag("settings_predictive_back")
                waitUntil("预测性返回主开关没有开启") { App.settings.predictiveBack }
            }
            waitTag("settings_predictive_back_card_fling")
            tapTag("settings_predictive_back_card_fling")
            waitUntil("返回跟手轨迹开关没有切换") { App.settings.predictiveBackCardFling != beforeCard }
            tapTag("settings_predictive_back_card_fling")
            waitUntil("返回跟手轨迹开关没有切回") { App.settings.predictiveBackCardFling == beforeCard }

            tapTag("settings_predictive_back_image_fling")
            waitUntil("图片返回跟手轨迹开关没有切换") { App.settings.predictiveBackImageFling != beforeImage }
            tapTag("settings_predictive_back_image_fling")
            waitUntil("图片返回跟手轨迹开关没有切回") { App.settings.predictiveBackImageFling == beforeImage }

            if (App.settings.blurEffects) {
                tapTag("settings_predictive_back_blur")
                waitUntil("返回跟手背景模糊开关没有切换") { App.settings.predictiveBackBlur != beforeBlur }
                tapTag("settings_predictive_back_blur")
                waitUntil("返回跟手背景模糊开关没有切回") { App.settings.predictiveBackBlur == beforeBlur }
            }
        } finally {
            App.settings.applyPredictiveBack(beforeMaster)
            App.settings.applyPredictiveBackCardFling(beforeCard)
            App.settings.applyPredictiveBackImageFling(beforeImage)
            App.settings.applyPredictiveBackBlur(beforeBlur)
        }
    }

    @Test
    fun confirmLike_togglesAndRestores() {
        openSettings()
        val before = App.settings.confirmLike
        try {
            scrollToTag("settings_confirm_like")
            tapTag("settings_confirm_like")
            waitUntil("点赞前确认开关没有切换") { App.settings.confirmLike != before }
            tapTag("settings_confirm_like")
            waitUntil("点赞前确认开关没有切回") { App.settings.confirmLike == before }
        } finally {
            App.settings.applyConfirmLike(before)
        }
    }

    @Test
    fun inAppBrowser_togglesAndRestores() {
        openSettings()
        val before = App.settings.inAppBrowser
        try {
            scrollToTag("settings_in_app_browser")
            tapTag("settings_in_app_browser")
            waitUntil("内置浏览器开关没有切换") { App.settings.inAppBrowser != before }
            tapTag("settings_in_app_browser")
            waitUntil("内置浏览器开关没有切回") { App.settings.inAppBrowser == before }
        } finally {
            App.settings.applyInAppBrowser(before)
        }
    }

    @Test
    fun collapseFollowGroups_togglesAndRestores() {
        openSettings()
        val before = App.settings.collapseFollowGroups
        try {
            scrollToTag("settings_collapse_follow_groups")
            tapTag("settings_collapse_follow_groups")
            waitUntil("收纳关注分组开关没有切换") { App.settings.collapseFollowGroups != before }
            tapTag("settings_collapse_follow_groups")
            waitUntil("收纳关注分组开关没有切回") { App.settings.collapseFollowGroups == before }
        } finally {
            App.settings.applyCollapseFollowGroups(before)
        }
    }

    @Test
    fun aboutScreen_opensAndBacks() {
        openSettings()
        scrollToTag("settings_about")
        tapTag("settings_about")
        waitTag("about_header")
        waitText("Cobo")
        waitText("前端界面库")
        back()
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

    @Test
    fun feedTabSettings_opensAndChanges() {
        openSettings()
        scrollToTag("settings_feed_left_tab")
        tapTag("settings_feed_left_tab")
        waitText("微博左侧栏内容")
        waitText("隐藏")
        tapTag("feed_tab_option_none")
        waitText("设置")
        assertEquals(
            com.cobo.app.data.FeedTabItem.None,
            App.settings.feedLeftTab.item,
        )

        // 恢复默认设置
        App.settings.applyFeedLeftTab(
            com.cobo.app.data.FeedTabSelection(com.cobo.app.data.FeedTabItem.Circle),
        )
    }
}
