package com.cobo.app.data

import com.cobo.app.json.JSONArray
import com.cobo.app.json.JSONObject
import com.cobo.app.platform.currentTimeMillis
import com.cobo.app.platform.formatEpochMillis

internal const val LocalBackupFormatId = "cobo-local-backup"
internal const val LocalBackupSchemaVersion = 1
internal const val MaxBackupFavoritesLimit = 500
internal const val MaxBackupDraftsLimit = 50
internal const val MaxBackupHistoryLimit = 100
internal const val MaxBackupSearchHistoryLimit = 10

/**
 * 显式白名单化的用户偏好设置快照。
 *
 * 所有字段均允许缺省（`null`），导入时仅覆盖备份中实际出现的键值，确保版本演进兼容性；
 * 严格排除任何 Cookie、Token、gsid、会话及设备标识字段。
 */
data class SettingsBackup(
    val darkMode: Int? = null,
    val fontScale: Float? = null,
    val accentColor: Int? = null,
    val meIconStyleKey: String? = null,
    val blurEffects: Boolean? = null,
    val allowLandscape: Boolean? = null,
    val animationSpeedKey: String? = null,
    val searchPanelAnimationKey: String? = null,
    val predictiveBack: Boolean? = null,
    val predictiveBackCardFling: Boolean? = null,
    val predictiveBackImageFling: Boolean? = null,
    val predictiveBackBlur: Boolean? = null,
    val autoHideFloatingBars: Boolean? = null,
    val feedTabTapToTopAndReturn: Boolean? = null,
    val bottomLeftActionKey: String? = null,
    val bottomBarOrderKeys: List<String>? = null,
    val feedLeftTabKey: String? = null,
    val feedCenterTabKey: String? = null,
    val feedRightTabKey: String? = null,
    val collapseFollowGroups: Boolean? = null,
    val hotBoardKeys: List<String>? = null,
    val reverseBrowse: Boolean? = null,
    val feedCardFrame: Boolean? = null,
    val detailCardFrame: Boolean? = null,
    val foldLongText: Boolean? = null,
    val showVipLevel: Boolean? = null,
    val showFanLevel: Boolean? = null,
    val showVerifiedBadge: Boolean? = null,
    val confirmLike: Boolean? = null,
    val inAppBrowser: Boolean? = null,
    val quickSwitchAccount: Boolean? = null,
    val profileTabAvatar: Boolean? = null,
    val profileShowStatuses: Boolean? = null,
    val imageBrowseQualityKey: String? = null,
    val livePhotoAutoPlay: Boolean? = null,
    val livePhotoSound: Boolean? = null,
    val videoAutoFloatOnBack: Boolean? = null,
    val videoAutoCloseOnComplete: Boolean? = null,
    val videoCenterSwipeActions: Boolean? = null,
    val localMode: Boolean? = null,
    val localFollowUid: String? = null,
    val nearbyCustom: Boolean? = null,
    val nearbyPlaceName: String? = null,
    val nearbyPlaceLat: Float? = null,
    val nearbyPlaceLon: Float? = null,
    val autoCheckUpdate: Boolean? = null,
    val acceptPrerelease: Boolean? = null,
    val inAppUpdate: Boolean? = null,
)

data class LocalBackupBundle(
    val version: Int = LocalBackupSchemaVersion,
    val exportedAt: Long,
    val appVersion: String,
    val settings: SettingsBackup? = null,
    val searchHistory: List<String>? = null,
    val drafts: List<Draft>? = null,
    val history: List<HistoryItem>? = null,
    val favorites: List<Status>? = null,
    val followTags: Map<String, FollowTag>? = null,
)

data class LocalBackupSummary(
    val hasSettings: Boolean,
    val searchHistoryCount: Int,
    val draftCount: Int,
    val historyCount: Int,
    val favoriteCount: Int,
    val followTagCount: Int,
)

data class LocalBackupImportResult(
    val settingsRestored: Boolean,
    val searchHistoryCount: Int,
    val draftCount: Int,
    val historyCount: Int,
    val favoriteCount: Int,
    val followTagCount: Int,
)

internal fun LocalBackupBundle.summary(): LocalBackupSummary = LocalBackupSummary(
    hasSettings = settings != null,
    searchHistoryCount = searchHistory?.size ?: 0,
    draftCount = drafts?.size ?: 0,
    historyCount = history?.size ?: 0,
    favoriteCount = favorites?.size ?: 0,
    followTagCount = followTags?.size ?: 0,
)

internal fun formatBackupSummary(summary: LocalBackupSummary): String {
    val parts = buildList {
        if (summary.hasSettings) add("偏好设置")
        if (summary.favoriteCount > 0) add("${summary.favoriteCount} 条收藏")
        if (summary.draftCount > 0) add("${summary.draftCount} 条草稿")
        if (summary.historyCount > 0) add("${summary.historyCount} 条历史")
        if (summary.searchHistoryCount > 0) add("${summary.searchHistoryCount} 条搜索记录")
        if (summary.followTagCount > 0) add("${summary.followTagCount} 条关注标记")
    }
    return if (parts.isEmpty()) "空备份" else parts.joinToString("、")
}

internal fun defaultBackupFileName(nowMs: Long): String =
    "cobo-backup-${formatEpochMillis(nowMs, "yyyyMMdd-HHmm")}.json"

internal fun encodeLocalBackupJson(bundle: LocalBackupBundle): String {
    val root = JSONObject()
        .put("format", LocalBackupFormatId)
        .put("version", bundle.version)
        .put("exportedAt", bundle.exportedAt)
        .put("appVersion", bundle.appVersion)

    bundle.settings?.let { root.put("settings", encodeSettingsBackup(it)) }

    bundle.searchHistory?.let { items ->
        val arr = JSONArray()
        items.forEach(arr::put)
        root.put("searchHistory", arr)
    }

    bundle.drafts?.let { drafts ->
        val arr = JSONArray()
        drafts.forEach { d ->
            arr.put(JSONObject().put("text", d.text).put("time", d.time))
        }
        root.put("drafts", arr)
    }

    bundle.history?.let { history ->
        val arr = JSONArray()
        history.forEach { h ->
            arr.put(
                JSONObject()
                    .put("id", h.id)
                    .put("mid", h.mid)
                    .put("text", h.text)
                    .put("user", h.user)
                    .put("time", h.time)
                    .put("html", h.html),
            )
        }
        root.put("history", arr)
    }

    bundle.favorites?.let { favs ->
        val arr = JSONArray()
        favs.forEach { arr.put(statusToSnapshot(it)) }
        root.put("favorites", arr)
    }

    bundle.followTags?.let { tags ->
        val obj = JSONObject()
        tags.forEach { (uid, tag) ->
            if (uid.isNotBlank()) obj.put(uid, tag.key)
        }
        root.put("followTags", obj)
    }

    return root.toString(2)
}

internal fun decodeLocalBackupJson(raw: String): LocalBackupBundle {
    val root = runCatching { JSONObject(raw) }.getOrNull()
        ?: throw IllegalArgumentException("备份文件不是有效的 JSON 格式")
    val format = root.str("format")
    if (format != LocalBackupFormatId) {
        throw IllegalArgumentException("这不是有效的 COBO 备份文件")
    }
    val version = root.optInt("version", -1)
    if (version !in 1..LocalBackupSchemaVersion) {
        throw IllegalArgumentException("备份版本不受支持（v$version），请更新 COBO 后重试")
    }

    val exportedAt = root.optLong("exportedAt", 0L)
    val appVersion = root.str("appVersion")
    val settings = root.optJSONObject("settings")?.let(::decodeSettingsBackup)

    val searchHistory = root.optJSONArray("searchHistory")?.let { arr ->
        buildList<String>(arr.length()) {
            for (i in 0 until arr.length()) {
                val item = arr.optString(i).takeIf { arr.opt(i) != JSONObject.NULL }?.trim().orEmpty()
                if (item.isNotEmpty() && item !in this) add(item)
            }
        }
    }

    val drafts = root.optJSONArray("drafts")?.let { arr ->
        buildList<Draft>(arr.length()) {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val text = o.str("text")
                if (text.isBlank()) continue
                add(Draft(text = text, time = o.optLong("time", 0L)))
            }
        }
    }

    val history = root.optJSONArray("history")?.let { arr ->
        buildList<HistoryItem>(arr.length()) {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.str("id")
                if (id.isBlank()) continue
                add(
                    HistoryItem(
                        id = id,
                        mid = o.str("mid").ifBlank { id },
                        text = o.str("text"),
                        user = o.str("user"),
                        time = o.optLong("time", 0L),
                        html = o.str("html"),
                    ),
                )
            }
        }
    }

    val favorites = root.optJSONArray("favorites")?.let { arr ->
        buildList<Status>(arr.length()) {
            for (i in 0 until arr.length()) {
                val parsed = statusFromSnapshot(arr.optJSONObject(i)) ?: continue
                add(if (parsed.favorited) parsed else parsed.copy(favorited = true))
            }
        }
    }

    val followTags = root.optJSONObject("followTags")?.let { obj ->
        val map = LinkedHashMap<String, FollowTag>()
        obj.keys().forEach { uid ->
            val tag = FollowTag.of(obj.str(uid))
            if (uid.isNotBlank() && tag != null) {
                map[uid] = tag
            }
        }
        map
    }

    return LocalBackupBundle(
        version = version,
        exportedAt = exportedAt,
        appVersion = appVersion,
        settings = settings,
        searchHistory = searchHistory,
        drafts = drafts,
        history = history,
        favorites = favorites,
        followTags = followTags,
    )
}

internal fun mergeBackupSearchHistory(
    existing: List<String>,
    incoming: List<String>,
    limit: Int = MaxBackupSearchHistoryLimit,
): List<String> {
    if (limit <= 0) return emptyList()
    val out = ArrayList<String>(limit)
    for (item in existing) {
        val cleaned = item.trim()
        if (cleaned.isNotEmpty() && cleaned !in out) {
            out.add(cleaned)
            if (out.size >= limit) return out
        }
    }
    for (item in incoming) {
        val cleaned = item.trim()
        if (cleaned.isNotEmpty() && cleaned !in out) {
            out.add(cleaned)
            if (out.size >= limit) return out
        }
    }
    return out
}

internal fun mergeBackupDrafts(
    existing: List<Draft>,
    incoming: List<Draft>,
    limit: Int = MaxBackupDraftsLimit,
): List<Draft> {
    if (limit <= 0) return emptyList()
    val byText = LinkedHashMap<String, Draft>()
    for (d in existing + incoming) {
        if (d.text.isBlank()) continue
        val prev = byText[d.text]
        if (prev == null || d.time > prev.time) {
            byText[d.text] = d
        }
    }
    return byText.values.sortedByDescending { it.time }.take(limit)
}

internal fun mergeBackupHistory(
    existing: List<HistoryItem>,
    incoming: List<HistoryItem>,
    limit: Int = MaxBackupHistoryLimit,
): List<HistoryItem> {
    if (limit <= 0) return emptyList()
    val byId = LinkedHashMap<String, HistoryItem>()
    for (h in existing + incoming) {
        if (h.id.isBlank()) continue
        val prev = byId[h.id]
        if (prev == null || h.time > prev.time) {
            byId[h.id] = h
        }
    }
    return byId.values.sortedByDescending { it.time }.take(limit)
}

internal fun mergeBackupFavorites(
    existing: List<Status>,
    incoming: List<Status>,
    limit: Int = MaxBackupFavoritesLimit,
): List<Status> {
    if (limit <= 0) return emptyList()
    val byId = LinkedHashMap<String, Status>()
    for (s in existing) {
        if (s.id.isBlank() || byId.containsKey(s.id)) continue
        byId[s.id] = if (s.favorited) s else s.copy(favorited = true)
        if (byId.size >= limit) return byId.values.toList()
    }
    for (s in incoming) {
        if (s.id.isBlank() || byId.containsKey(s.id)) continue
        byId[s.id] = if (s.favorited) s else s.copy(favorited = true)
        if (byId.size >= limit) return byId.values.toList()
    }
    return byId.values.toList()
}

internal fun mergeBackupFollowTags(
    existing: Map<String, FollowTag>,
    incoming: Map<String, FollowTag>,
): Map<String, FollowTag> {
    val out = LinkedHashMap<String, FollowTag>(existing.size + incoming.size)
    out.putAll(existing)
    incoming.forEach { (uid, tag) ->
        if (uid.isNotBlank()) out[uid] = tag
    }
    return out
}

internal fun createLocalBackup(
    settings: Settings,
    store: Store,
    appVersion: String,
    nowMs: Long = currentTimeMillis(),
): LocalBackupBundle = LocalBackupBundle(
    version = LocalBackupSchemaVersion,
    exportedAt = nowMs,
    appVersion = appVersion,
    settings = settings.exportBackup(),
    searchHistory = settings.searchHistory.toList(),
    drafts = store.drafts(),
    history = store.history(),
    favorites = store.favorites(),
    followTags = store.followTags(),
)

internal fun applyLocalBackup(
    bundle: LocalBackupBundle,
    settings: Settings,
    store: Store,
    repo: Repo,
): LocalBackupImportResult {
    var settingsRestored = false
    bundle.settings?.let {
        settings.applyBackup(it)
        settingsRestored = true
    }
    val searchCount = bundle.searchHistory?.let { incoming ->
        val merged = mergeBackupSearchHistory(settings.searchHistory.toList(), incoming)
        settings.replaceSearchHistory(merged)
        merged.size
    } ?: 0
    val draftCount = bundle.drafts?.let { store.mergeDrafts(it) } ?: 0
    val historyCount = bundle.history?.let { store.mergeHistory(it) } ?: 0
    val favoriteCount = bundle.favorites?.let { incoming ->
        val count = store.mergeFavorites(incoming)
        repo.reloadLocalFavorites()
        count
    } ?: 0
    val followTagCount = bundle.followTags?.let { incoming ->
        val count = store.mergeFollowTags(incoming)
        FollowTags.attach(store)
        count
    } ?: 0

    return LocalBackupImportResult(
        settingsRestored = settingsRestored,
        searchHistoryCount = searchCount,
        draftCount = draftCount,
        historyCount = historyCount,
        favoriteCount = favoriteCount,
        followTagCount = followTagCount,
    )
}

private fun encodeSettingsBackup(s: SettingsBackup): JSONObject = JSONObject().apply {
    s.darkMode?.let { put("darkMode", it) }
    s.fontScale?.let { put("fontScale", it.toDouble()) }
    s.accentColor?.let { put("accentColor", it) }
    s.meIconStyleKey?.let { put("meIconStyleKey", it) }
    s.blurEffects?.let { put("blurEffects", it) }
    s.allowLandscape?.let { put("allowLandscape", it) }
    s.animationSpeedKey?.let { put("animationSpeedKey", it) }
    s.searchPanelAnimationKey?.let { put("searchPanelAnimationKey", it) }
    s.predictiveBack?.let { put("predictiveBack", it) }
    s.predictiveBackCardFling?.let { put("predictiveBackCardFling", it) }
    s.predictiveBackImageFling?.let { put("predictiveBackImageFling", it) }
    s.predictiveBackBlur?.let { put("predictiveBackBlur", it) }
    s.autoHideFloatingBars?.let { put("autoHideFloatingBars", it) }
    s.feedTabTapToTopAndReturn?.let { put("feedTabTapToTopAndReturn", it) }
    s.bottomLeftActionKey?.let { put("bottomLeftActionKey", it) }
    s.bottomBarOrderKeys?.let { keys ->
        put("bottomBarOrderKeys", JSONArray().apply { keys.forEach { put(it) } })
    }
    s.feedLeftTabKey?.let { put("feedLeftTabKey", it) }
    s.feedCenterTabKey?.let { put("feedCenterTabKey", it) }
    s.feedRightTabKey?.let { put("feedRightTabKey", it) }
    s.collapseFollowGroups?.let { put("collapseFollowGroups", it) }
    s.hotBoardKeys?.let { keys ->
        val arr = JSONArray()
        keys.forEach(arr::put)
        put("hotBoardKeys", arr)
    }
    s.reverseBrowse?.let { put("reverseBrowse", it) }
    s.feedCardFrame?.let { put("feedCardFrame", it) }
    s.detailCardFrame?.let { put("detailCardFrame", it) }
    s.foldLongText?.let { put("foldLongText", it) }
    s.showVipLevel?.let { put("showVipLevel", it) }
    s.showFanLevel?.let { put("showFanLevel", it) }
    s.showVerifiedBadge?.let { put("showVerifiedBadge", it) }
    s.confirmLike?.let { put("confirmLike", it) }
    s.inAppBrowser?.let { put("inAppBrowser", it) }
    s.quickSwitchAccount?.let { put("quickSwitchAccount", it) }
    s.profileTabAvatar?.let { put("profileTabAvatar", it) }
    s.profileShowStatuses?.let { put("profileShowStatuses", it) }
    s.imageBrowseQualityKey?.let { put("imageBrowseQualityKey", it) }
    s.livePhotoAutoPlay?.let { put("livePhotoAutoPlay", it) }
    s.livePhotoSound?.let { put("livePhotoSound", it) }
    s.videoAutoFloatOnBack?.let { put("videoAutoFloatOnBack", it) }
    s.videoAutoCloseOnComplete?.let { put("videoAutoCloseOnComplete", it) }
    s.videoCenterSwipeActions?.let { put("videoCenterSwipeActions", it) }
    s.localMode?.let { put("localMode", it) }
    s.localFollowUid?.let { put("localFollowUid", it) }
    s.nearbyCustom?.let { put("nearbyCustom", it) }
    s.nearbyPlaceName?.let { put("nearbyPlaceName", it) }
    s.nearbyPlaceLat?.let { put("nearbyPlaceLat", it.toDouble()) }
    s.nearbyPlaceLon?.let { put("nearbyPlaceLon", it.toDouble()) }
    s.autoCheckUpdate?.let { put("autoCheckUpdate", it) }
    s.acceptPrerelease?.let { put("acceptPrerelease", it) }
    s.inAppUpdate?.let { put("inAppUpdate", it) }
}

private fun decodeSettingsBackup(o: JSONObject): SettingsBackup {
    fun optIntOrNull(key: String): Int? =
        if (o.has(key) && !o.isNull(key)) o.optInt(key) else null

    fun optBoolOrNull(key: String): Boolean? =
        if (o.has(key) && !o.isNull(key)) o.optBoolean(key) else null

    fun optFloatOrNull(key: String): Float? {
        if (!o.has(key) || o.isNull(key)) return null
        val d = o.optDouble(key, Double.NaN)
        return if (d.isFinite()) d.toFloat() else null
    }

    fun optStrOrNull(key: String): String? =
        if (o.has(key) && !o.isNull(key)) o.str(key) else null

    val hotBoardKeys = if (o.has("hotBoardKeys") && !o.isNull("hotBoardKeys")) {
        o.optJSONArray("hotBoardKeys")?.let { arr ->
            buildList<String>(arr.length()) {
                for (i in 0 until arr.length()) {
                    val v = arr.optString(i).takeIf { arr.opt(i) != JSONObject.NULL }?.trim().orEmpty()
                    if (v.isNotEmpty()) add(v)
                }
            }
        }
    } else {
        null
    }

    val bottomBarOrderKeys = if (o.has("bottomBarOrderKeys") && !o.isNull("bottomBarOrderKeys")) {
        o.optJSONArray("bottomBarOrderKeys")?.let { arr ->
            buildList<String>(arr.length()) {
                for (i in 0 until arr.length()) {
                    val v = arr.optString(i).takeIf { arr.opt(i) != JSONObject.NULL }?.trim().orEmpty()
                    if (v.isNotEmpty()) add(v)
                }
            }
        }
    } else {
        null
    }

    return SettingsBackup(
        darkMode = optIntOrNull("darkMode"),
        fontScale = optFloatOrNull("fontScale"),
        accentColor = optIntOrNull("accentColor"),
        meIconStyleKey = optStrOrNull("meIconStyleKey"),
        blurEffects = optBoolOrNull("blurEffects"),
        allowLandscape = optBoolOrNull("allowLandscape"),
        animationSpeedKey = optStrOrNull("animationSpeedKey"),
        searchPanelAnimationKey = optStrOrNull("searchPanelAnimationKey"),
        predictiveBack = optBoolOrNull("predictiveBack"),
        predictiveBackCardFling = optBoolOrNull("predictiveBackCardFling"),
        predictiveBackImageFling = optBoolOrNull("predictiveBackImageFling"),
        predictiveBackBlur = optBoolOrNull("predictiveBackBlur"),
        autoHideFloatingBars = optBoolOrNull("autoHideFloatingBars"),
        feedTabTapToTopAndReturn = optBoolOrNull("feedTabTapToTopAndReturn"),
        bottomLeftActionKey = optStrOrNull("bottomLeftActionKey"),
        bottomBarOrderKeys = bottomBarOrderKeys,
        feedLeftTabKey = optStrOrNull("feedLeftTabKey"),
        feedCenterTabKey = optStrOrNull("feedCenterTabKey"),
        feedRightTabKey = optStrOrNull("feedRightTabKey"),
        collapseFollowGroups = optBoolOrNull("collapseFollowGroups"),
        hotBoardKeys = hotBoardKeys,
        reverseBrowse = optBoolOrNull("reverseBrowse"),
        feedCardFrame = optBoolOrNull("feedCardFrame"),
        detailCardFrame = optBoolOrNull("detailCardFrame"),
        foldLongText = optBoolOrNull("foldLongText"),
        showVipLevel = optBoolOrNull("showVipLevel"),
        showFanLevel = optBoolOrNull("showFanLevel"),
        showVerifiedBadge = optBoolOrNull("showVerifiedBadge"),
        confirmLike = optBoolOrNull("confirmLike"),
        inAppBrowser = optBoolOrNull("inAppBrowser"),
        quickSwitchAccount = optBoolOrNull("quickSwitchAccount"),
        profileTabAvatar = optBoolOrNull("profileTabAvatar"),
        profileShowStatuses = optBoolOrNull("profileShowStatuses"),
        imageBrowseQualityKey = optStrOrNull("imageBrowseQualityKey"),
        livePhotoAutoPlay = optBoolOrNull("livePhotoAutoPlay"),
        livePhotoSound = optBoolOrNull("livePhotoSound"),
        videoAutoFloatOnBack = optBoolOrNull("videoAutoFloatOnBack"),
        videoAutoCloseOnComplete = optBoolOrNull("videoAutoCloseOnComplete"),
        videoCenterSwipeActions = optBoolOrNull("videoCenterSwipeActions"),
        localMode = optBoolOrNull("localMode"),
        localFollowUid = optStrOrNull("localFollowUid"),
        nearbyCustom = optBoolOrNull("nearbyCustom"),
        nearbyPlaceName = optStrOrNull("nearbyPlaceName"),
        nearbyPlaceLat = optFloatOrNull("nearbyPlaceLat"),
        nearbyPlaceLon = optFloatOrNull("nearbyPlaceLon"),
        autoCheckUpdate = optBoolOrNull("autoCheckUpdate"),
        acceptPrerelease = optBoolOrNull("acceptPrerelease"),
        inAppUpdate = optBoolOrNull("inAppUpdate"),
    )
}
