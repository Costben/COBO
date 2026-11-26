package com.cobo.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlin.math.abs

/**
 * 视口内参与自动播放评选的视频卡片几何快照。
 */
data class FeedVideoCandidate(
    val key: String,
    val leftPx: Float,
    val topPx: Float,
    val rightPx: Float,
    val bottomPx: Float,
    val visibleRatio: Float,
)

/**
 * 判断当前是否允许触发信息流短视频静音自动播放。
 */
fun shouldAutoPlayFeedVideo(
    settingEnabled: Boolean,
    isWifi: Boolean,
    isForeground: Boolean,
    isRouteActive: Boolean,
    isLive: Boolean,
    url: String,
    hasFloatingVideo: Boolean,
): Boolean =
    settingEnabled &&
        isWifi &&
        isForeground &&
        isRouteActive &&
        !isLive &&
        url.isNotBlank() &&
        !hasFloatingVideo

/**
 * 计算视频卡片与可见视口交集占自身面积的比例（0f..1f）。
 * 同时校验水平与垂直边界，天然过滤掉 HorizontalPager 离屏页或滑出视口外的卡片。
 */
fun computeVisibleRatio(
    leftPx: Float,
    topPx: Float,
    rightPx: Float,
    bottomPx: Float,
    viewportLeftPx: Float,
    viewportTopPx: Float,
    viewportRightPx: Float,
    viewportBottomPx: Float,
): Float {
    val width = rightPx - leftPx
    val height = bottomPx - topPx
    if (width <= 0f || height <= 0f) return 0f
    val interW = (minOf(rightPx, viewportRightPx) - maxOf(leftPx, viewportLeftPx)).coerceAtLeast(0f)
    val interH = (minOf(bottomPx, viewportBottomPx) - maxOf(topPx, viewportTopPx)).coerceAtLeast(0f)
    if (interW <= 0f || interH <= 0f) return 0f
    return ((interW * interH) / (width * height)).coerceIn(0f, 1f)
}

/**
 * 从当前可见候选中选出最合适静音预览的唯一视频卡片 Key。
 * 对正在播放的 [currentActiveKey] 应用滞回区间，防止轻微滑动时来回切换抖动。
 */
fun pickBestFeedVideoCandidate(
    candidates: Collection<FeedVideoCandidate>,
    viewportTopPx: Float,
    viewportBottomPx: Float,
    minVisibleRatio: Float = 0.6f,
    currentActiveKey: String? = null,
    keepVisibleRatio: Float = 0.45f,
): String? {
    val viewportCenter = (viewportTopPx + viewportBottomPx) / 2f
    val eligible = candidates.filter { c ->
        val threshold = if (c.key == currentActiveKey) keepVisibleRatio else minVisibleRatio
        c.visibleRatio >= threshold
    }
    if (eligible.isEmpty()) return null

    fun distanceToCenter(c: FeedVideoCandidate): Float =
        abs((c.topPx + c.bottomPx) / 2f - viewportCenter)

    val best = eligible.minByOrNull(::distanceToCenter) ?: return null
    val active = currentActiveKey?.let { key -> eligible.firstOrNull { it.key == key } }
    if (active != null && active.key != best.key) {
        val band = maxOf(1f, (viewportBottomPx - viewportTopPx) * 0.12f)
        if (distanceToCenter(active) - distanceToCenter(best) <= band) {
            return active.key
        }
    }
    return best.key
}

/**
 * 判断设备当前默认网络是否为 Wi-Fi。
 */
fun isWifiNetwork(context: Context): Boolean = runCatching {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return@runCatching false
    val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return@runCatching false
    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
}.getOrDefault(false)
