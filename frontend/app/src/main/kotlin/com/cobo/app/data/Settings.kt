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

    // Index into FeedFilter.entries.
    var feedFilter by mutableIntStateOf(sp.getInt("feed_filter", 0))
        private set

    /** 启动时自动查更新，默认开。 */
    var autoCheckUpdate by mutableStateOf(sp.getBoolean("auto_check_update", true))
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

    fun applyFeedFilter(value: Int) {
        feedFilter = value.coerceIn(0, FeedFilterCount - 1)
        sp.edit().putInt("feed_filter", feedFilter).apply()
    }

    fun applyAutoCheckUpdate(value: Boolean) {
        autoCheckUpdate = value
        sp.edit().putBoolean("auto_check_update", value).apply()
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
