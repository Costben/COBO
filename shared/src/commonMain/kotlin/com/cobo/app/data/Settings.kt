package com.cobo.app.data

import com.cobo.app.platform.PlatformContext
import com.cobo.app.platform.PlatformPrefs
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cobo.app.json.JSONArray
import kotlin.math.roundToInt

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

/** 看图页默认加载画质。 */
enum class ImageBrowseQuality(val key: String, val label: String) {
    Default("default", "默认"),
    Original("original", "原图"),
    ;

    companion object {
        fun fromKey(key: String?): ImageBrowseQuality {
            return entries.firstOrNull { it.key == key } ?: Default
        }
    }
}

/** 底栏左下角融合球按钮的功能。 */
enum class BottomLeftAction(val key: String, val label: String) {
    Refresh("refresh", "回到顶部刷新"),
    Search("search", "搜索"),
    Favorites("favorites", "打开收藏夹"),
    ;

    companion object {
        fun fromKey(key: String?): BottomLeftAction {
            return entries.firstOrNull { it.key == key } ?: Refresh
        }
    }
}

/** 「我」页入口与一级设置选项左侧图标的配色样式，默认使用 ColorOS 17 语义彩色。 */
enum class MeIconStyle(val key: String, val label: String) {
    Colorful("colorful", "默认"),
    Monochrome("monochrome", "黑白"),
    Accent("accent", "强调色"),
    ;

    companion object {
        fun fromKey(key: String?): MeIconStyle {
            return entries.firstOrNull { it.key == key } ?: Colorful
        }
    }
}

/** 页面切换与卡片展开/返回动画的播放速度（三档）。 */
enum class AnimationSpeed(
    val key: String,
    val label: String,
    val durationScale: Float,
) {
    Fast("fast", "快速", 0.75f),
    Standard("standard", "标准", 1.0f),
    Slow("slow", "舒缓", 1.35f),
    ;

    fun scaleDuration(baseMillis: Int): Int =
        (baseMillis * durationScale).roundToInt().coerceAtLeast(1)

    companion object {
        fun fromKey(key: String?): AnimationSpeed {
            return entries.firstOrNull { it.key == key } ?: Standard
        }
    }
}

class Settings(@Suppress("UNUSED_PARAMETER") context: PlatformContext = PlatformContext()) {
    private val sp = PlatformPrefs("app")

    private companion object {
        const val SearchHistoryLimit = 10

        /** 续看位置在 SharedPreferences 里的前缀，后面接流的 key（旧版不分账号）。 */
        const val ScrollKeyPrefix = "scroll_"

        /** 按账号区分的续看位置前缀，格式 `scroll_v2_<uid>_<key>`。 */
        const val ScopedScrollKeyPrefix = "scroll_v2_"

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

    /** 自定义强调色（ARGB 整型）；`0` 表示使用 COUI 默认蓝。 */
    var accentColor by mutableIntStateOf(sp.getInt("accent_color", 0))
        private set

    /** 「我」页入口及一级设置选项图标配色样式。 */
    var meIconStyle by mutableStateOf(MeIconStyle.fromKey(sp.getString("me_icon_style", null)))
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
     * 动态卡片返回跟手轨迹：卡片详情预测性返回时跟随手指偏移，并顺着甩出方向回缩。默认关。
     */
    var predictiveBackCardFling by mutableStateOf(sp.getBoolean("predictive_back_card_fling", false))
        private set

    /**
     * 图片返回跟手轨迹：看图页预测性返回时跟随手指偏移，并顺着甩出方向缩回原图。默认关。
     */
    var predictiveBackImageFling by mutableStateOf(sp.getBoolean("predictive_back_image_fling", false))
        private set

    /**
     * 返回背景模糊：预测性返回时保持最上层卡片清晰，将背景层高斯模糊并随返回过程逐渐变清晰。默认开。
     */
    var predictiveBackBlur by mutableStateOf(sp.getBoolean("predictive_back_blur", true))
        private set

    /**
     * 模糊效果：顶栏的渐变玻璃和页面里的玻璃面都要每帧采样背后的内容。
     * 关掉之后改为纯色，页面也不再录制 backdrop。默认开，和以前的外观一致。
     */
    var blurEffects by mutableStateOf(sp.getBoolean("blur_effects", true))
        private set

    /**
     * 滑动自动隐藏浮动栏：开启后向下浏览时自动收起顶部分段栏、悬浮底栏与详情页底部工具栏，
     * 向上滑动或回到顶部时恢复。默认关。
     */
    var autoHideFloatingBars by mutableStateOf(sp.getBoolean("auto_hide_floating_bars", false))
        private set

    /**
     * 底栏「微博」单击回顶与返回：开启后在微博页单击底栏「微博」可在顶部与上次浏览位置间切换，
     * 双击触发刷新；关闭时保留原有双击回顶手势。默认关。
     */
    var feedTabTapToTopAndReturn by mutableStateOf(sp.getBoolean("feed_tab_tap_to_top_and_return", false))
        private set

    /**
     * 允许横屏：关掉之后 Activity 钉死竖屏，也就不会进入左右双页。默认开，跟随系统方向。
     */
    var allowLandscape by mutableStateOf(sp.getBoolean("allow_landscape", true))
        private set

    /** 点赞前确认：点爱心之后先弹窗问一句，避免手滑。默认关，和以前一样点一下就赞；取消赞不拦。 */
    var confirmLike by mutableStateOf(sp.getBoolean("confirm_like", false))
        private set

    /** 使用内置浏览器打开外部网页：开启后在应用内 WebView 打开，关闭后直接跳转系统浏览器。默认开。 */
    var inAppBrowser by mutableStateOf(sp.getBoolean("in_app_browser", true))
        private set

    /** 切回应用时识别剪贴板里的微博链接并提示打开，默认开。 */
    var clipboardLinkDetect by mutableStateOf(sp.getBoolean("clipboard_link_detect", true))
        private set

    /** 长按悬浮底栏的「我」是否显示快捷切换账号菜单。默认开。 */
    var quickSwitchAccount by mutableStateOf(sp.getBoolean("quick_switch_account", true))
        private set

    /** 悬浮底栏的「我」是否显示当前账号头像（代替默认图标）。默认开。 */
    var profileTabAvatar by mutableStateOf(sp.getBoolean("profile_tab_avatar", true))
        private set

    /** 「我」页面是否直接展示自己的全部微博；关闭时改造成个人互动中心与设置一级页面。 */
    var profileShowStatuses by mutableStateOf(sp.getBoolean("profile_show_statuses", false))
        private set

    /**
     * 首页信息流的微博是否保留最外层卡片外框。关掉后卡片背景与圆角去掉，正文横向撑到与顶栏
     * 按钮对齐；卡片内部的区块（转发块、投票卡、媒体、楼中楼）仍保留各自的外框。默认保留。
     */
    var feedCardFrame by mutableStateOf(sp.getBoolean("feed_card_frame", true))
        private set

    /** 详情页主贴与热门评论是否保留最外层卡片外框。默认保留，与首页分开设置。 */
    var detailCardFrame by mutableStateOf(sp.getBoolean("detail_card_frame", true))
        private set

    /**
     * 信息流长文折叠：按官方规则在 140 字（280 个半角宽度）处截断，点「展开全文」再看完整内容。
     * 详情页始终显示全文。默认开。
     */
    var foldLongText by mutableStateOf(sp.getBoolean("fold_long_text", true))
        private set

    /** 昵称旁是否显示 VIP / SVIP / VVIP 等级标识。默认开启。 */
    var showVipLevel by mutableStateOf(sp.getBoolean("show_vip_level", true))
        private set

    /** 评论区昵称旁是否显示铁粉 / 金粉 / 钻粉等粉丝等级标识。默认开启。 */
    var showFanLevel by mutableStateOf(sp.getBoolean("show_fan_level", true))
        private set

    /** 认证用户头像右下角是否显示金 V / 蓝 V 角标。默认开启。 */
    var showVerifiedBadge by mutableStateOf(sp.getBoolean("show_verified_badge", true))
        private set

    /** 倒序浏览：刷新时停在原阅读位置，将新微博插入上方并支持断点补拉。默认关。 */
    var reverseBrowse by mutableStateOf(sp.getBoolean("reverse_browse", false))
        private set

    /** 收纳关注分组：开启后首页中间栏菜单将关注分组收进二级菜单，关闭后直接平铺展开在一级菜单中。默认开。 */
    var collapseFollowGroups by mutableStateOf(sp.getBoolean("collapse_follow_groups", true))
        private set

    /** 首页搜索面板的展开方式：上下扩展、淡入淡出、从搜索按钮展开。默认淡入淡出。 */
    var searchPanelAnimation by mutableStateOf(
        SearchPanelAnimation.fromKey(sp.getString("search_panel_animation", null))
    )
        private set

    /** 页面切换、卡片展开/返回与看图动画速度，默认标准档。 */
    var animationSpeed by mutableStateOf(
        AnimationSpeed.fromKey(sp.getString("animation_speed", null))
    )
        private set

    /** 底栏左下角融合球按钮的作用：回到顶部刷新 / 搜索 / 打开收藏夹。 */
    var bottomLeftAction by mutableStateOf(
        BottomLeftAction.fromKey(sp.getString("bottom_left_action", null))
    )
        private set

    /** 看图页默认加载画质：默认（适配屏幕尺寸） / 原图。 */
    var imageBrowseQuality by mutableStateOf(
        ImageBrowseQuality.fromKey(sp.getString("image_browse_quality", null))
    )
        private set

    /** 实况图滑动自动播放：开启后进入或滑动到实况图时自动播放一次，结束后仅支持长按重播。默认开。 */
    var livePhotoAutoPlay by mutableStateOf(sp.getBoolean("live_photo_auto_play", true))
        private set

    /** 实况图声音播放：开启后播放实况图附带的视频声音，关闭时静音播放。默认开。 */
    var livePhotoSound by mutableStateOf(sp.getBoolean("live_photo_sound", true))
        private set

    /** Wi-Fi 下信息流短视频静音自动播放：仅在 Wi-Fi 环境静音预览屏幕中的非直播短视频，默认关。 */
    var feedVideoAutoPlay by mutableStateOf(sp.getBoolean("feed_video_auto_play", false))
        private set

    /**
     * 视频播放时返回自动悬浮：开启后播放视频时按返回自动转入应用内悬浮小窗，
     * 切至后台自动进入系统画中画；关闭时按返回直接退出播放。默认关。
     */
    var videoAutoFloatOnBack by mutableStateOf(sp.getBoolean("video_auto_float_on_back", false))
        private set

    /** 视频播完自动返回：非直播视频播放结束后自动关闭并返回上一页，默认开。 */
    var videoAutoCloseOnComplete by mutableStateOf(sp.getBoolean("video_auto_close_on_complete", true))
        private set

    /** 视频中区上下滑手势：开启后播放视频时在屏幕中间区域上滑可呼出应用内小窗与系统画中画，下滑可关闭视频；关闭后恢复左半调亮度、右半调音量。默认开。 */
    var videoCenterSwipeActions by mutableStateOf(sp.getBoolean("video_center_swipe_actions", true))
        private set

    /** 视频播放页顶栏右侧按钮与顺序（空列表表示全部隐藏）。 */
    var videoTopBarButtons by mutableStateOf(VideoTopBarButton.parse(sp.getString("video_top_bar_buttons", null)))
        private set

    /** 看图页顶栏右侧按钮与顺序（空列表表示全部隐藏）。 */
    var imageTopBarButtons by mutableStateOf(ImageTopBarButton.parse(sp.getString("image_top_bar_buttons", null)))
        private set

    /** 看图页顶部居中页码胶囊（仅多图时显示，单图不显示 1/1）。 */
    var imageTopBarShowCounter by mutableStateOf(sp.getBoolean("image_top_bar_show_counter", true))
        private set

    /**
     * 搜索面板里显示哪些热搜榜，按 [HotBoard] 的声明顺序保存。
     *
     * 没存过键时是全部五个榜；存成空串表示一个都不选，此时整个热搜区块都不显示。
     */
    var hotBoards by mutableStateOf(parseHotBoards(sp.getString("hot_boards", null)))
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

    internal fun replaceSearchHistory(items: List<String>) {
        searchHistory.clear()
        searchHistory.addAll(items.take(SearchHistoryLimit))
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
     * 某个账号的首页栏上次停在哪儿：卡片下标与偏了几像素。按账号和流分开记，
     * 切换账号后各保持各自的阅读位置，不会互相带跑。
     */
    fun feedScroll(owner: String, key: String): Pair<Int, Int>? {
        val raw = sp.getString(scopedFeedScrollPrefKey(owner, key), null)
            ?: sp.getString(ScrollKeyPrefix + key, null)
            ?: return null
        val parts = raw.split(',')
        if (parts.size != 2) return null
        val index = parts[0].toIntOrNull() ?: return null
        val offset = parts[1].toIntOrNull() ?: return null
        return index.coerceAtLeast(0) to offset.coerceAtLeast(0)
    }

    fun applyFeedScroll(owner: String, key: String, index: Int, offset: Int) {
        val editor = sp.edit()
            .putString(
                scopedFeedScrollPrefKey(owner, key),
                "${index.coerceAtLeast(0)},${offset.coerceAtLeast(0)}",
            )
        val legacy = ScrollKeyPrefix + key
        if (sp.contains(legacy)) editor.remove(legacy)
        editor.apply()
    }

    /** 切号时只清旧版未按账号区分的滚动位置，避免串到新账号，保留各账号自己的位置。 */
    fun clearLegacyFeedScrolls() {
        val editor = sp.edit()
        sp.allStrings().keys
            .filter { it.startsWith(ScrollKeyPrefix) && !it.startsWith(ScopedScrollKeyPrefix) }
            .forEach(editor::remove)
        editor.apply()
    }

    /** 退出登录时清掉所有账号的续看位置。 */
    fun clearFeedScrolls() {
        val editor = sp.edit()
        sp.allStrings().keys.filter { it.startsWith(ScrollKeyPrefix) }.forEach(editor::remove)
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

    fun applyPredictiveBackCardFling(value: Boolean) {
        predictiveBackCardFling = value
        sp.edit().putBoolean("predictive_back_card_fling", value).apply()
    }

    fun applyPredictiveBackImageFling(value: Boolean) {
        predictiveBackImageFling = value
        sp.edit().putBoolean("predictive_back_image_fling", value).apply()
    }

    fun applyPredictiveBackBlur(value: Boolean) {
        predictiveBackBlur = value
        sp.edit().putBoolean("predictive_back_blur", value).apply()
    }

    fun applyBlurEffects(value: Boolean) {
        blurEffects = value
        sp.edit().putBoolean("blur_effects", value).apply()
    }

    fun applyAutoHideFloatingBars(value: Boolean) {
        autoHideFloatingBars = value
        sp.edit().putBoolean("auto_hide_floating_bars", value).apply()
    }

    fun applyFeedTabTapToTopAndReturn(value: Boolean) {
        feedTabTapToTopAndReturn = value
        sp.edit().putBoolean("feed_tab_tap_to_top_and_return", value).apply()
    }

    fun applyAllowLandscape(value: Boolean) {
        allowLandscape = value
        sp.edit().putBoolean("allow_landscape", value).apply()
    }

    fun applyConfirmLike(value: Boolean) {
        confirmLike = value
        sp.edit().putBoolean("confirm_like", value).apply()
    }

    fun applyInAppBrowser(value: Boolean) {
        inAppBrowser = value
        sp.edit().putBoolean("in_app_browser", value).apply()
    }

    fun applyClipboardLinkDetect(value: Boolean) {
        clipboardLinkDetect = value
        sp.edit().putBoolean("clipboard_link_detect", value).apply()
    }

    fun applyQuickSwitchAccount(value: Boolean) {
        quickSwitchAccount = value
        sp.edit().putBoolean("quick_switch_account", value).apply()
    }

    fun applyProfileTabAvatar(value: Boolean) {
        profileTabAvatar = value
        sp.edit().putBoolean("profile_tab_avatar", value).apply()
    }

    fun applyProfileShowStatuses(value: Boolean) {
        profileShowStatuses = value
        sp.edit().putBoolean("profile_show_statuses", value).apply()
    }

    fun applyFeedCardFrame(value: Boolean) {
        feedCardFrame = value
        sp.edit().putBoolean("feed_card_frame", value).apply()
    }

    fun applyDetailCardFrame(value: Boolean) {
        detailCardFrame = value
        sp.edit().putBoolean("detail_card_frame", value).apply()
    }

    fun applyFoldLongText(value: Boolean) {
        foldLongText = value
        sp.edit().putBoolean("fold_long_text", value).apply()
    }

    fun applyShowVipLevel(value: Boolean) {
        showVipLevel = value
        sp.edit().putBoolean("show_vip_level", value).apply()
    }

    fun applyShowFanLevel(value: Boolean) {
        showFanLevel = value
        sp.edit().putBoolean("show_fan_level", value).apply()
    }

    fun applyShowVerifiedBadge(value: Boolean) {
        showVerifiedBadge = value
        sp.edit().putBoolean("show_verified_badge", value).apply()
    }

    fun applyReverseBrowse(value: Boolean) {
        reverseBrowse = value
        sp.edit().putBoolean("reverse_browse", value).apply()
    }

    fun applyCollapseFollowGroups(value: Boolean) {
        collapseFollowGroups = value
        sp.edit().putBoolean("collapse_follow_groups", value).apply()
    }

    fun applySearchPanelAnimation(value: SearchPanelAnimation) {
        searchPanelAnimation = value
        sp.edit().putString("search_panel_animation", value.key).apply()
    }

    fun applyAnimationSpeed(value: AnimationSpeed) {
        animationSpeed = value
        sp.edit().putString("animation_speed", value.key).apply()
    }

    fun applyBottomLeftAction(value: BottomLeftAction) {
        bottomLeftAction = value
        sp.edit().putString("bottom_left_action", value.key).apply()
    }

    fun applyImageBrowseQuality(value: ImageBrowseQuality) {
        imageBrowseQuality = value
        sp.edit().putString("image_browse_quality", value.key).apply()
    }

    fun applyLivePhotoAutoPlay(value: Boolean) {
        livePhotoAutoPlay = value
        sp.edit().putBoolean("live_photo_auto_play", value).apply()
    }

    fun applyLivePhotoSound(value: Boolean) {
        livePhotoSound = value
        sp.edit().putBoolean("live_photo_sound", value).apply()
    }

    fun applyFeedVideoAutoPlay(value: Boolean) {
        feedVideoAutoPlay = value
        sp.edit().putBoolean("feed_video_auto_play", value).apply()
    }

    fun applyVideoAutoFloatOnBack(value: Boolean) {
        videoAutoFloatOnBack = value
        sp.edit().putBoolean("video_auto_float_on_back", value).apply()
    }

    fun applyVideoAutoCloseOnComplete(value: Boolean) {
        videoAutoCloseOnComplete = value
        sp.edit().putBoolean("video_auto_close_on_complete", value).apply()
    }

    fun applyVideoCenterSwipeActions(value: Boolean) {
        videoCenterSwipeActions = value
        sp.edit().putBoolean("video_center_swipe_actions", value).apply()
    }

    fun applyVideoTopBarButtons(value: List<VideoTopBarButton>) {
        val normalized = value.distinct()
        videoTopBarButtons = normalized
        sp.edit().putString("video_top_bar_buttons", VideoTopBarButton.encode(normalized)).apply()
    }

    fun applyImageTopBarButtons(value: List<ImageTopBarButton>) {
        val normalized = value.distinct()
        imageTopBarButtons = normalized
        sp.edit().putString("image_top_bar_buttons", ImageTopBarButton.encode(normalized)).apply()
    }

    fun applyImageTopBarShowCounter(value: Boolean) {
        imageTopBarShowCounter = value
        sp.edit().putBoolean("image_top_bar_show_counter", value).apply()
    }

    fun resetMediaTopBarSettings() {
        applyVideoTopBarButtons(VideoTopBarButton.DefaultOrder)
        applyImageTopBarButtons(ImageTopBarButton.DefaultOrder)
        applyImageTopBarShowCounter(true)
    }

    /** [value] 为空时热搜区块整个隐藏，所以空集合也要落盘（存成空串），不能靠删键。 */
    fun applyHotBoards(value: Set<HotBoard>) {
        val ordered = HotBoard.entries.filter { it in value }.toSet()
        hotBoards = ordered
        sp.edit().putString("hot_boards", ordered.joinToString(",") { it.filterType }).apply()
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

    fun applyAccentColor(argb: Int) {
        val normalized = when (argb) {
            0, com.cobo.app.ui.AccentHueCycle -> argb
            else -> argb or 0xFF000000.toInt()
        }
        accentColor = normalized
        sp.edit().putInt("accent_color", normalized).apply()
    }

    fun applyMeIconStyle(value: MeIconStyle) {
        meIconStyle = value
        sp.edit().putString("me_icon_style", value.key).apply()
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

    /** 导出显式白名单内的偏好设置；关注分组绑定账号 gid，不写入跨账号备份。 */
    internal fun exportBackup(): SettingsBackup = SettingsBackup(
        darkMode = darkMode,
        fontScale = fontScale,
        accentColor = accentColor,
        meIconStyleKey = meIconStyle.key,
        blurEffects = blurEffects,
        allowLandscape = allowLandscape,
        animationSpeedKey = animationSpeed.key,
        searchPanelAnimationKey = searchPanelAnimation.key,
        predictiveBack = predictiveBack,
        predictiveBackCardFling = predictiveBackCardFling,
        predictiveBackImageFling = predictiveBackImageFling,
        predictiveBackBlur = predictiveBackBlur,
        autoHideFloatingBars = autoHideFloatingBars,
        feedTabTapToTopAndReturn = feedTabTapToTopAndReturn,
        bottomLeftActionKey = bottomLeftAction.key,
        feedLeftTabKey = feedLeftTab.takeIf { !it.isGroup }?.item?.key,
        feedCenterTabKey = feedCenterTab.takeIf { !it.isGroup }?.item?.key,
        feedRightTabKey = feedRightTab.takeIf { !it.isGroup }?.item?.key,
        collapseFollowGroups = collapseFollowGroups,
        hotBoardKeys = HotBoard.entries.filter { it in hotBoards }.map { it.filterType },
        reverseBrowse = reverseBrowse,
        feedCardFrame = feedCardFrame,
        detailCardFrame = detailCardFrame,
        foldLongText = foldLongText,
        showVipLevel = showVipLevel,
        showFanLevel = showFanLevel,
        showVerifiedBadge = showVerifiedBadge,
        confirmLike = confirmLike,
        inAppBrowser = inAppBrowser,
        quickSwitchAccount = quickSwitchAccount,
        profileTabAvatar = profileTabAvatar,
        profileShowStatuses = profileShowStatuses,
        imageBrowseQualityKey = imageBrowseQuality.key,
        livePhotoAutoPlay = livePhotoAutoPlay,
        livePhotoSound = livePhotoSound,
        videoAutoFloatOnBack = videoAutoFloatOnBack,
        videoAutoCloseOnComplete = videoAutoCloseOnComplete,
        videoCenterSwipeActions = videoCenterSwipeActions,
        localMode = localMode,
        localFollowUid = localFollowUid,
        nearbyCustom = nearbyCustom,
        nearbyPlaceName = nearbyPlaceName,
        nearbyPlaceLat = nearbyPlaceLat,
        nearbyPlaceLon = nearbyPlaceLon,
        autoCheckUpdate = autoCheckUpdate,
        acceptPrerelease = acceptPrerelease,
        inAppUpdate = inAppUpdate,
    )

    /** 仅恢复备份中存在且合法的偏好键，未知或非法字段自动跳过。 */
    internal fun applyBackup(backup: SettingsBackup) {
        backup.darkMode?.let { applyDarkMode(it.coerceIn(0, 2)) }
        backup.fontScale?.takeIf { it.isFinite() && it in 0.5f..2.0f }?.let(::applyFontScale)
        backup.accentColor?.let(::applyAccentColor)
        backup.meIconStyleKey?.let { key ->
            MeIconStyle.entries.firstOrNull { it.key == key }?.let(::applyMeIconStyle)
        }
        backup.blurEffects?.let(::applyBlurEffects)
        backup.allowLandscape?.let(::applyAllowLandscape)
        backup.animationSpeedKey?.let { key ->
            AnimationSpeed.entries.firstOrNull { it.key == key }?.let(::applyAnimationSpeed)
        }
        backup.searchPanelAnimationKey?.let { key ->
            SearchPanelAnimation.entries.firstOrNull { it.key == key }?.let(::applySearchPanelAnimation)
        }
        backup.predictiveBack?.let(::applyPredictiveBack)
        backup.predictiveBackCardFling?.let(::applyPredictiveBackCardFling)
        backup.predictiveBackImageFling?.let(::applyPredictiveBackImageFling)
        backup.predictiveBackBlur?.let(::applyPredictiveBackBlur)
        backup.autoHideFloatingBars?.let(::applyAutoHideFloatingBars)
        backup.feedTabTapToTopAndReturn?.let(::applyFeedTabTapToTopAndReturn)
        backup.bottomLeftActionKey?.let { key ->
            BottomLeftAction.entries.firstOrNull { it.key == key }?.let(::applyBottomLeftAction)
        }
        backup.feedLeftTabKey?.let { key ->
            FeedTabItem.entries.firstOrNull { it.key == key && it != FeedTabItem.Group }
                ?.let { applyFeedLeftTab(FeedTabSelection(it)) }
        }
        backup.feedCenterTabKey?.let { key ->
            FeedTabItem.entries.firstOrNull {
                it.key == key && it != FeedTabItem.Group && it != FeedTabItem.None
            }?.let { applyFeedCenterTab(FeedTabSelection(it)) }
        }
        backup.feedRightTabKey?.let { key ->
            FeedTabItem.entries.firstOrNull { it.key == key && it != FeedTabItem.Group }
                ?.let { applyFeedRightTab(FeedTabSelection(it)) }
        }
        backup.collapseFollowGroups?.let(::applyCollapseFollowGroups)
        backup.hotBoardKeys?.let { keys ->
            val keySet = keys.toSet()
            applyHotBoards(HotBoard.entries.filter { it.filterType in keySet }.toSet())
        }
        backup.reverseBrowse?.let(::applyReverseBrowse)
        backup.feedCardFrame?.let(::applyFeedCardFrame)
        backup.detailCardFrame?.let(::applyDetailCardFrame)
        backup.foldLongText?.let(::applyFoldLongText)
        backup.showVipLevel?.let(::applyShowVipLevel)
        backup.showFanLevel?.let(::applyShowFanLevel)
        backup.showVerifiedBadge?.let(::applyShowVerifiedBadge)
        backup.confirmLike?.let(::applyConfirmLike)
        backup.inAppBrowser?.let(::applyInAppBrowser)
        backup.quickSwitchAccount?.let(::applyQuickSwitchAccount)
        backup.profileTabAvatar?.let(::applyProfileTabAvatar)
        backup.profileShowStatuses?.let(::applyProfileShowStatuses)
        backup.imageBrowseQualityKey?.let { key ->
            ImageBrowseQuality.entries.firstOrNull { it.key == key }?.let(::applyImageBrowseQuality)
        }
        backup.livePhotoAutoPlay?.let(::applyLivePhotoAutoPlay)
        backup.livePhotoSound?.let(::applyLivePhotoSound)
        backup.videoAutoFloatOnBack?.let(::applyVideoAutoFloatOnBack)
        backup.videoAutoCloseOnComplete?.let(::applyVideoAutoCloseOnComplete)
        backup.videoCenterSwipeActions?.let(::applyVideoCenterSwipeActions)
        backup.localMode?.let(::applyLocalMode)
        backup.localFollowUid?.let(::applyLocalFollowUid)
        if (backup.nearbyCustom == false) {
            applyNearbyDevice()
        } else if (backup.nearbyCustom == true) {
            val place = backup.nearbyPlaceName
            val lat = backup.nearbyPlaceLat
            val lon = backup.nearbyPlaceLon
            if (!place.isNullOrBlank() && lat != null && lon != null && lat.isFinite() && lon.isFinite()) {
                applyNearbyPlace(place, lat.toDouble(), lon.toDouble())
            }
        }
        backup.autoCheckUpdate?.let(::applyAutoCheckUpdate)
        backup.acceptPrerelease?.let(::applyAcceptPrerelease)
        backup.inAppUpdate?.let(::applyInAppUpdate)
    }
}

/** `null` 表示从没设置过（默认全部显示）；空串表示用户明确全不勾，热搜区块隐藏。 */
internal fun parseHotBoards(raw: String?): Set<HotBoard> {
    if (raw == null) return HotBoard.entries.toSet()
    if (raw.isBlank()) return emptySet()
    val keys = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    return HotBoard.entries.filter { it.filterType in keys }.toSet()
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

internal fun scopedFeedScrollPrefKey(owner: String, key: String): String =
    "scroll_v2_${snapshotOwnerKey(owner)}_$key"

enum class VideoTopBarButton(val key: String, val label: String) {
    Speed("speed", "倍速"),
    Download("download", "下载视频"),
    FloatingWindow("floating", "小窗播放"),
    PictureInPicture("pip", "画中画"),
    Browser("browser", "浏览器打开");

    companion object {
        val DefaultOrder: List<VideoTopBarButton> = listOf(
            Speed,
            Download,
            FloatingWindow,
            PictureInPicture,
            Browser,
        )

        fun parse(raw: String?): List<VideoTopBarButton> {
            if (raw == null) return DefaultOrder
            if (raw.isBlank()) return emptyList()
            val byKey = entries.associateBy { it.key }
            return raw.split(',').mapNotNull { byKey[it.trim()] }.distinct()
        }

        fun encode(list: List<VideoTopBarButton>): String =
            list.distinct().joinToString(",") { it.key }
    }
}

enum class ImageTopBarButton(val key: String, val label: String) {
    Original("original", "原图"),
    Save("save", "保存"),
    Share("share", "分享");

    companion object {
        val DefaultOrder: List<ImageTopBarButton> = listOf(
            Original,
            Save,
        )

        fun parse(raw: String?): List<ImageTopBarButton> {
            if (raw == null) return DefaultOrder
            if (raw.isBlank()) return emptyList()
            val byKey = entries.associateBy { it.key }
            return raw.split(',').mapNotNull { byKey[it.trim()] }.distinct()
        }

        fun encode(list: List<ImageTopBarButton>): String =
            list.distinct().joinToString(",") { it.key }
    }
}
