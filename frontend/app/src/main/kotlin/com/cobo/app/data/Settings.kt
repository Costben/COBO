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
    Nearby("nearby", "附近", "feed_tab_nearby"),

    /**
     * 自建的关注分组。分组本身是账号里的数据，页面上按分组逐个列出，选中哪个由
     * [FeedTabSelection.group] 决定；单独留下这一项只用来表示「分组」这一类。
     */
    Group("group", "关注分组", "feed_tab_group"),
    ;

    companion object {
        fun fromKey(key: String?, default: FeedTabItem): FeedTabItem {
            return entries.firstOrNull { it.key == key } ?: default
        }
    }
}

/**
 * 首页某一栏看什么。内置流直接用 [item]；关注分组是 [item] 为 [FeedTabItem.Group]
 * 再加上具体那一组的 gid 与名字 —— 分组是账号里的数据，不能写进枚举。
 */
data class FeedTabSelection(
    val item: FeedTabItem,
    val group: FollowGroup? = null,
) {
    /** 分组跟着 gid 走：同一栏里换个分组就是换了一条流。 */
    val key: String
        get() = when {
            item != FeedTabItem.Group -> item.key
            group != null -> "group_${group.gid}"
            else -> FeedTabItem.Group.key
        }

    val label: String
        get() = when {
            item != FeedTabItem.Group -> item.label
            group != null -> group.title.ifBlank { FeedTabItem.Group.label }
            else -> FeedTabItem.Group.label
        }

    val isGroup: Boolean get() = item == FeedTabItem.Group && group != null
}

/**
 * 设置与顶栏「更多」菜单共用的一份选项：内置流在前，关注分组跟在后。
 *
 * 当前选中的分组即使这次没拉到（分组被删了、或者还没加载完）也留在列表里，
 * 否则那一栏会突然没有对应项、而且没法改回去。
 */
fun feedTabOptions(
    groups: List<FollowGroup>,
    allowNone: Boolean,
    current: FeedTabSelection? = null,
): List<FeedTabSelection> = buildList {
    if (allowNone) add(FeedTabSelection(FeedTabItem.None))
    FeedTabItem.entries
        .filter { it != FeedTabItem.None && it != FeedTabItem.Group }
        .forEach { add(FeedTabSelection(it)) }
    groups.forEach { add(FeedTabSelection(FeedTabItem.Group, it)) }
    current?.takeIf { it.isGroup && groups.none { group -> group.gid == it.group?.gid } }
        ?.let { add(it) }
}

/** 首页搜索面板展开、收回的方式。 */
enum class SearchPanelAnimation(val key: String, val label: String) {
    Vertical("vertical", "上下扩展"),
    Fade("fade", "淡入淡出"),
    ;

    companion object {
        fun fromKey(key: String?): SearchPanelAnimation {
            return entries.firstOrNull { it.key == key } ?: Fade
        }
    }
}

class Settings(context: Context) {
    private val sp = context.getSharedPreferences("app", Context.MODE_PRIVATE)

    private companion object {
        const val SearchHistoryLimit = 10

        /** 续看位置在 SharedPreferences 里的前缀，后面接流的 key。 */
        const val ScrollKeyPrefix = "scroll_"

        /** 首页停在某个关注分组时 [feedTabKey] 里的标记，见 [FeedTabSelection.key]。 */
        const val GroupKeyMarker = "group_"
    }

    /** Recent queries, newest first. Backed by `search_history` as a JSON array. */
    val searchHistory = mutableStateListOf<String>().also { list ->
        list.addAll(parseSearchHistoryJson(sp.getString("search_history", null)))
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

    /** 自定义微博页左侧栏浏览内容（默认好友圈，可设为隐藏、内置流或某个关注分组）。 */
    var feedLeftTab by mutableStateOf(loadFeedSelection("feed_left", FeedTabItem.Circle))
        private set

    /** 自定义微博页中间栏浏览内容（默认关注，不可设为隐藏）。 */
    var feedCenterTab by mutableStateOf(loadFeedSelection("feed_center", FeedTabItem.Follow))
        private set

    /** 自定义微博页右侧栏浏览内容（默认推荐，可设为隐藏、内置流或某个关注分组）。 */
    var feedRightTab by mutableStateOf(loadFeedSelection("feed_right", FeedTabItem.Recommend))
        private set

    /** 记录当前停留的微博页栏标识（例如 "left_circle", "follow", "right_recommend"）。 */
    var feedTabKey by mutableStateOf(sp.getString("feed_tab_key", "follow").orEmpty())
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

    /**
     * 附近看哪座城：默认跟着设备定位走；[nearbyCustom] 打开后固定看 [nearbyPlaceName]，
     * 坐标存在下面两项里。微博的同城流只认坐标，选城市也就是换一组坐标。
     */
    var nearbyCustom by mutableStateOf(sp.getBoolean("nearby_custom", false))
        private set

    var nearbyPlaceName by mutableStateOf(sp.getString("nearby_place_name", "").orEmpty())
        private set

    var nearbyPlaceLat by mutableFloatStateOf(sp.getFloat("nearby_place_lat", 0f))
        private set

    var nearbyPlaceLon by mutableFloatStateOf(sp.getFloat("nearby_place_lon", 0f))
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

    /**
     * 模糊效果：顶栏的渐变玻璃和页面里的玻璃面都要每帧采样背后的内容。
     * 关掉之后改为纯色，页面也不再录制 backdrop。默认开，和以前的外观一致。
     */
    var blurEffects by mutableStateOf(sp.getBoolean("blur_effects", true))
        private set

    /** 点赞前确认：点爱心之后先弹窗问一句，避免手滑。默认关，和以前一样点一下就赞；取消赞不拦。 */
    var confirmLike by mutableStateOf(sp.getBoolean("confirm_like", false))
        private set

    /** 首页搜索面板的展开方式：上下扩展、淡入淡出、从搜索按钮展开。默认淡入淡出。 */
    var searchPanelAnimation by mutableStateOf(
        SearchPanelAnimation.fromKey(sp.getString("search_panel_animation", null))
    )
        private set

    /** 上次检查更新的时间戳。 */
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
        if (!pushSearchHistoryItem(searchHistory, keyword, SearchHistoryLimit)) return
        saveSearchHistory()
    }

    /** Removes a single entry from [searchHistory] and persists the change. */
    fun removeSearchHistory(keyword: String) {
        if (!removeSearchHistoryItem(searchHistory, keyword)) return
        saveSearchHistory()
    }

    fun clearSearchHistory() {
        searchHistory.clear()
        saveSearchHistory()
    }

    private fun saveSearchHistory() {
        sp.edit().putString("search_history", encodeSearchHistoryJson(searchHistory)).apply()
    }

    fun applyFeedTab(value: Int) {
        feedTab = value.coerceIn(0, 2)
        sp.edit().putInt("feed_tab_v3", feedTab).apply()
    }

    fun applyFeedLeftTab(selection: FeedTabSelection) {
        feedLeftTab = selection
        saveFeedSelection("feed_left", selection)
    }

    fun applyFeedCenterTab(selection: FeedTabSelection) {
        if (selection.item == FeedTabItem.None) return
        feedCenterTab = selection
        saveFeedSelection("feed_center", selection)
    }

    fun applyFeedRightTab(selection: FeedTabSelection) {
        feedRightTab = selection
        saveFeedSelection("feed_right", selection)
    }

    /**
     * 某个首页栏上次停在哪儿：卡片下标与偏了几像素。按流分开记，
     * 「关注」和「推荐」各记各的，切栏不会互相带跑。
     */
    fun feedScroll(key: String): Pair<Int, Int>? {
        val raw = sp.getString(ScrollKeyPrefix + key, null) ?: return null
        val parts = raw.split(',')
        if (parts.size != 2) return null
        val index = parts[0].toIntOrNull() ?: return null
        val offset = parts[1].toIntOrNull() ?: return null
        return index.coerceAtLeast(0) to offset.coerceAtLeast(0)
    }

    fun applyFeedScroll(key: String, index: Int, offset: Int) {
        sp.edit()
            .putString(ScrollKeyPrefix + key, "${index.coerceAtLeast(0)},${offset.coerceAtLeast(0)}")
            .apply()
    }

    /**
     * 清掉所有栏的续看位置。滚动位置是按流记的、不分账号，换账号后必须一起清：
     * 否则新账号进来会按上一个人的卡片下标落位，第 3 栏可能直接跳到第 80 条。
     */
    fun clearFeedScrolls() {
        val editor = sp.edit()
        sp.all.keys.filter { it.startsWith(ScrollKeyPrefix) }.forEach(editor::remove)
        editor.apply()
    }

    /**
     * 换账号 / 退出登录时把三栏内容复位。关注分组是账号里的数据，gid 换了账号就不存在，
     * 留着只会让首页出现一条打不开的空栏（中间栏不可隐藏，还会顶掉「关注」）。
     */
    fun clearGroupFeedTabs() {
        if (feedLeftTab.isGroup) applyFeedLeftTab(FeedTabSelection(FeedTabItem.Circle))
        if (feedCenterTab.isGroup) applyFeedCenterTab(FeedTabSelection(FeedTabItem.Follow))
        if (feedRightTab.isGroup) applyFeedRightTab(FeedTabSelection(FeedTabItem.Recommend))
        // 栏标识形如 "center_group_5012345"：带上位置前缀，用 contains 才兜得住。
        if (feedTabKey.isEmpty() || feedTabKey.contains(GroupKeyMarker)) {
            applyFeedTabKey(FeedTabItem.Follow.key)
        }
    }

    private fun loadFeedSelection(prefix: String, default: FeedTabItem): FeedTabSelection {
        val item = FeedTabItem.fromKey(sp.getString("${prefix}_tab", null), default)
        if (item != FeedTabItem.Group) return FeedTabSelection(item)
        val gid = sp.getString("${prefix}_group_gid", "").orEmpty()
        val title = sp.getString("${prefix}_group_title", "").orEmpty()
        // 只存到「分组」这一档但没有具体组时退回内置的默认流，免得首页出现一条空栏。
        return if (gid.isBlank()) FeedTabSelection(default) else FeedTabSelection(item, FollowGroup(gid, title))
    }

    private fun saveFeedSelection(prefix: String, selection: FeedTabSelection) {
        sp.edit()
            .putString("${prefix}_tab", selection.item.key)
            .putString("${prefix}_group_gid", selection.group?.gid.orEmpty())
            .putString("${prefix}_group_title", selection.group?.title.orEmpty())
            .apply()
    }

    /** 附近改回跟设备定位走。 */
    fun applyNearbyDevice() {
        nearbyCustom = false
        sp.edit().putBoolean("nearby_custom", false).apply()
    }

    /** 附近固定看某座城：名字只用来在界面上说明现在看的是哪儿。 */
    fun applyNearbyPlace(name: String, lat: Double, lon: Double) {
        nearbyCustom = true
        nearbyPlaceName = name
        nearbyPlaceLat = lat.toFloat()
        nearbyPlaceLon = lon.toFloat()
        sp.edit()
            .putBoolean("nearby_custom", true)
            .putString("nearby_place_name", name)
            .putFloat("nearby_place_lat", lat.toFloat())
            .putFloat("nearby_place_lon", lon.toFloat())
            .apply()
    }

    fun applyFeedTabKey(key: String) {
        feedTabKey = key
        sp.edit().putString("feed_tab_key", key).apply()
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

    fun applyBlurEffects(value: Boolean) {
        blurEffects = value
        sp.edit().putBoolean("blur_effects", value).apply()
    }

    fun applyConfirmLike(value: Boolean) {
        confirmLike = value
        sp.edit().putBoolean("confirm_like", value).apply()
    }

    fun applySearchPanelAnimation(value: SearchPanelAnimation) {
        searchPanelAnimation = value
        sp.edit().putString("search_panel_animation", value.key).apply()
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

internal fun parseSearchHistoryJson(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        val arr = JSONArray(raw)
        buildList(arr.length()) {
            for (i in 0 until arr.length()) {
                val item = arr.optString(i).trim()
                if (item.isNotEmpty() && item !in this) add(item)
            }
        }
    }.getOrDefault(emptyList())
}

internal fun encodeSearchHistoryJson(history: List<String>): String {
    val arr = JSONArray()
    history.forEach(arr::put)
    return arr.toString()
}

internal fun pushSearchHistoryItem(
    history: MutableList<String>,
    keyword: String,
    limit: Int = 10,
): Boolean {
    val query = keyword.trim()
    if (query.isEmpty()) return false
    history.remove(query)
    history.add(0, query)
    while (history.size > limit) {
        history.removeAt(history.lastIndex)
    }
    return true
}

internal fun removeSearchHistoryItem(
    history: MutableList<String>,
    keyword: String,
): Boolean = history.remove(keyword)
