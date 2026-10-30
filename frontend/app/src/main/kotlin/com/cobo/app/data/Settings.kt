package com.cobo.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray

enum class FeedTabItem(val key: String, val label: String, val tag: String) {
    None("none", "隐藏", "feed_tab_none"),
    Follow("follow", "关注", "feed_tab_follow"),
    Circle("circle", "好友圈", "feed_tab_circle"),
    Recommend("recommend", "推荐", "feed_tab_recommend"),
    Local("local", "本地关注", "feed_tab_local"),
    Original("original", "原创微博", "feed_tab_original"),
    Special("special", "特别关注", "feed_tab_special"),
    Secret("secret", "悄悄关注", "feed_tab_secret"),
    Favorites("favorites", "收藏", "feed_tab_favorites"),
    ;

    companion object {
        fun fromKey(key: String?, default: FeedTabItem): FeedTabItem {
            return entries.firstOrNull { it.key == key } ?: default
        }
    }
}

class Settings(context: Context) {
    private val sp = context.getSharedPreferences("app", Context.MODE_PRIVATE)

    private companion object {
        const val SearchHistoryLimit = 10
        const val FeedFilterCount = 9
    }

    /** Recent queries, newest first. Backed by `search_history` as a JSON array. */
    val searchHistory = mutableStateListOf<String>().also { list ->
        val raw = sp.getString("search_history", null)
        if (!raw.isNullOrBlank()) {
            runCatching {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val item = arr.optString(i).trim()
                    if (item.isNotEmpty() && item !in list) list.add(item)
                }
            }
        }
    }

    // 0 = follow system, 1 = light, 2 = dark
    var darkMode by mutableIntStateOf(
        if (sp.contains("dark_mode")) sp.getInt("dark_mode", 0)
        else if (sp.contains("dark")) if (sp.getBoolean("dark", false)) 2 else 1 else 0
    )
        private set

    var fontScale by mutableFloatStateOf(sp.getFloat("font_scale", 1f))
        private set

    var mainTab by mutableIntStateOf(sp.getInt("main_tab", 0))
        private set

    // 0 = 好友圈, 1 = 关注, 2 = 推荐. New installs open on 关注; the v2 key stored 0 = 关注 and
    // 1 = 推荐, so its value shifts up by one when no v3 value exists yet.
    var feedTab by mutableIntStateOf(
        sp.getInt("feed_tab_v3", (sp.getInt("feed_tab_v2", 0) + 1).coerceIn(0, 2))
    )
        private set

    /** 自定义微博页左侧栏浏览内容（默认好友圈，可设为隐藏或其他内容）。 */
    var feedLeftTab by mutableStateOf(
        FeedTabItem.fromKey(sp.getString("feed_left_tab", null), FeedTabItem.Circle)
    )
        private set

    /** 自定义微博页中间栏浏览内容（默认关注，不可设为隐藏）。 */
    var feedCenterTab by mutableStateOf(
        FeedTabItem.fromKey(sp.getString("feed_center_tab", null), FeedTabItem.Follow)
    )
        private set

    /** 自定义微博页右侧栏浏览内容（默认推荐，可设为隐藏或其他内容）。 */
    var feedRightTab by mutableStateOf(
        FeedTabItem.fromKey(sp.getString("feed_right_tab", null), FeedTabItem.Recommend)
    )
        private set

    /** 记录当前停留的微博页栏标识（例如 "left_circle", "follow", "right_recommend"）。 */
    var feedTabKey by mutableStateOf(sp.getString("feed_tab_key", "follow").orEmpty())
        private set

    // Index into FeedFilter.entries.
    var feedFilter by mutableIntStateOf(sp.getInt("feed_filter", 0))
        private set

    /**
     * 「本地关注」固定用这个账号的 cookie 取数；空串表示跟随当前登录账号。
     * 标记表本身与账号无关，这条只决定拿谁的身份去拉那批人的内容。
     */
    var localFollowUid by mutableStateOf(sp.getString("local_follow_uid", "").orEmpty())
        private set

    /** 用户在登录页选了「不登录，纯本地浏览」；有登录账号时以账号为准，这条不起作用。 */
    var localMode by mutableStateOf(sp.getBoolean("local_mode", false))
        private set

    /** 启动时自动查更新，默认开。 */
    var autoCheckUpdate by mutableStateOf(sp.getBoolean("auto_check_update", true))
        private set

    /** 接收预发布版本；关闭后更新检查只认正式版。默认开，和以前的更新行为一致。 */
    var acceptPrerelease by mutableStateOf(sp.getBoolean("accept_prerelease", true))
        private set

    /** 应用内更新：开启后在应用内直接拉取 release 中的 APK 并发起安装请求。 */
    var inAppUpdate by mutableStateOf(sp.getBoolean("in_app_update", false))
        private set

    /**
     * 预测性返回手势：返回时页面跟着手指往回收。关掉之后退回普通返回——按一下才走，
     * 没有跟手的预览动画。默认开。
     */
    var predictiveBack by mutableStateOf(sp.getBoolean("predictive_back", true))
        private set

    /** 上次检查更新的时间戳，用来做 [UpdateCheckInterval] 节流。 */
    var updateCheckedAt by mutableLongStateOf(sp.getLong("update_checked_at", 0L))
        private set

    /** 点过「跳过此版本」的版本号：自动检查时不再拿它打扰。 */
    var skippedUpdate by mutableStateOf(sp.getString("skipped_update", "").orEmpty())
        private set

    fun applyMainTab(value: Int) {
        mainTab = value
        sp.edit().putInt("main_tab", value).apply()
    }

    /** Records a submitted query, newest first, keeping at most [SearchHistoryLimit] entries. */
    fun pushSearchHistory(keyword: String) {
        val query = keyword.trim()
        if (query.isEmpty()) return
        searchHistory.remove(query)
        searchHistory.add(0, query)
        while (searchHistory.size > SearchHistoryLimit) {
            searchHistory.removeAt(searchHistory.lastIndex)
        }
        saveSearchHistory()
    }

    fun clearSearchHistory() {
        searchHistory.clear()
        saveSearchHistory()
    }

    private fun saveSearchHistory() {
        val arr = JSONArray()
        searchHistory.forEach(arr::put)
        sp.edit().putString("search_history", arr.toString()).apply()
    }

    fun applyFeedTab(value: Int) {
        feedTab = value.coerceIn(0, 2)
        sp.edit().putInt("feed_tab_v3", feedTab).apply()
    }

    fun applyFeedLeftTab(item: FeedTabItem) {
        feedLeftTab = item
        sp.edit().putString("feed_left_tab", item.key).apply()
    }

    fun applyFeedCenterTab(item: FeedTabItem) {
        if (item == FeedTabItem.None) return
        feedCenterTab = item
        sp.edit().putString("feed_center_tab", item.key).apply()
    }

    fun applyFeedRightTab(item: FeedTabItem) {
        feedRightTab = item
        sp.edit().putString("feed_right_tab", item.key).apply()
    }

    fun applyFeedTabKey(key: String) {
        feedTabKey = key
        sp.edit().putString("feed_tab_key", key).apply()
    }

    fun applyFeedFilter(value: Int) {
        feedFilter = value.coerceIn(0, FeedFilterCount - 1)
        sp.edit().putInt("feed_filter", feedFilter).apply()
    }

    fun applyAutoCheckUpdate(value: Boolean) {
        autoCheckUpdate = value
        sp.edit().putBoolean("auto_check_update", value).apply()
    }

    fun applyAcceptPrerelease(value: Boolean) {
        acceptPrerelease = value
        sp.edit().putBoolean("accept_prerelease", value).apply()
    }

    fun applyInAppUpdate(value: Boolean) {
        inAppUpdate = value
        sp.edit().putBoolean("in_app_update", value).apply()
    }

    fun applyPredictiveBack(value: Boolean) {
        predictiveBack = value
        sp.edit().putBoolean("predictive_back", value).apply()
    }

    fun applyLocalFollowUid(uid: String) {
        localFollowUid = uid
        sp.edit().putString("local_follow_uid", uid).apply()
    }

    fun applyLocalMode(value: Boolean) {
        localMode = value
        sp.edit().putBoolean("local_mode", value).apply()
    }

    fun applyUpdateCheckedAt(value: Long) {
        updateCheckedAt = value
        sp.edit().putLong("update_checked_at", value).apply()
    }

    fun applySkippedUpdate(value: String) {
        skippedUpdate = value
        sp.edit().putString("skipped_update", value).apply()
    }

    fun applyDarkMode(value: Int) {
        darkMode = value
        sp.edit().putInt("dark_mode", value).apply()
    }

    fun applyFontScale(value: Float) {
        fontScale = value
        sp.edit().putFloat("font_scale", value).apply()
    }

    fun cycleFontScale(): Float {
        val scales = listOf(0.85f, 1f, 1.15f, 1.3f)
        val index = scales.indexOfFirst { kotlin.math.abs(it - fontScale) < 0.01f }
        val next = scales[(if (index < 0) 1 else index + 1) % scales.size]
        applyFontScale(next)
        return next
    }

    fun cycleDarkMode(): Int {
        val next = (darkMode + 1) % 3
        applyDarkMode(next)
        return next
    }
}
