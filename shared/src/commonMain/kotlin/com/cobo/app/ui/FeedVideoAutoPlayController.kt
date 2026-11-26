package com.cobo.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.cobo.app.App
import com.cobo.app.data.FeedVideoCandidate
import com.cobo.app.data.VideoInfo
import com.cobo.app.data.pickBestFeedVideoCandidate
import com.cobo.app.data.shouldAutoPlayFeedVideo
import com.cobo.app.platform.appCacheDir
import com.cobo.app.screen.ResolvedVideo
import com.cobo.app.screen.defaultRefererFor
import com.cobo.app.screen.isDirectVideoStream
import com.cobo.app.screen.resolvePlayableVideo
import com.cobo.app.screen.shouldRefreshDirectStreamBeforePlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val LocalFeedVideoAutoPlay = staticCompositionLocalOf<FeedVideoAutoPlayController?> { null }
val LocalFeedVideoRouteActive = staticCompositionLocalOf { false }

private const val AutoPlaySettleDelayMillis = 220L
private const val ActiveKeepVisibleRatio = 0.45f

/**
 * 全局信息流短视频内联预览调度器：同一时刻只允许视口中最居中的一张非直播视频卡片播放。
 */
@Stable
class FeedVideoAutoPlayController {
    private var seq = 0L
    private val candidates = LinkedHashMap<String, FeedVideoCandidate>()
    private var viewportTopPx = 0f
    private var viewportBottomPx = 0f
    private var scope: CoroutineScope? = null
    private var pendingJob: Job? = null

    var activeKey by mutableStateOf<String?>(null)
        private set

    var environmentAllowed by mutableStateOf(false)
        private set

    fun nextKey(): String {
        seq += 1L
        return "fv_$seq"
    }

    fun attach(scope: CoroutineScope) {
        this.scope = scope
    }

    fun updateEnvironment(allowed: Boolean) {
        environmentAllowed = allowed
        if (!allowed) {
            pendingJob?.cancel()
            pendingJob = null
            activeKey = null
        } else {
            scheduleRecompute(immediate = false)
        }
    }

    fun updateCandidate(
        candidate: FeedVideoCandidate,
        viewportTop: Float,
        viewportBottom: Float,
    ) {
        viewportTopPx = viewportTop
        viewportBottomPx = viewportBottom
        candidates[candidate.key] = candidate
        if (!environmentAllowed) {
            if (activeKey != null) activeKey = null
            return
        }
        if (candidate.key == activeKey && candidate.visibleRatio < ActiveKeepVisibleRatio) {
            activeKey = null
        }
        scheduleRecompute(immediate = false)
    }

    fun unregister(key: String) {
        candidates.remove(key)
        if (activeKey == key) {
            pendingJob?.cancel()
            pendingJob = null
            activeKey = null
        }
        scheduleRecompute(immediate = false)
    }

    fun pauseAll() {
        pendingJob?.cancel()
        pendingJob = null
        activeKey = null
    }

    private fun scheduleRecompute(immediate: Boolean) {
        if (!environmentAllowed) {
            pendingJob?.cancel()
            pendingJob = null
            activeKey = null
            return
        }
        val desired = pickBestFeedVideoCandidate(
            candidates = candidates.values,
            viewportTopPx = viewportTopPx,
            viewportBottomPx = viewportBottomPx,
            currentActiveKey = activeKey,
        )
        if (desired == activeKey) {
            pendingJob?.cancel()
            pendingJob = null
            return
        }
        if (desired == null || immediate || scope == null) {
            pendingJob?.cancel()
            pendingJob = null
            activeKey = desired
            return
        }
        pendingJob?.cancel()
        pendingJob = scope?.launch {
            delay(AutoPlaySettleDelayMillis)
            if (!environmentAllowed) {
                activeKey = null
                return@launch
            }
            activeKey = pickBestFeedVideoCandidate(
                candidates = candidates.values,
                viewportTopPx = viewportTopPx,
                viewportBottomPx = viewportBottomPx,
                currentActiveKey = activeKey,
            )
        }
    }
}

/**
 * 监听 Wi-Fi 状态、前后台生命周期、路由焦点与悬浮窗冲突，并同步到 [controller]。
 */
@Composable
fun rememberFeedVideoAutoPlayEnvironment(
    controller: FeedVideoAutoPlayController,
    nav: NavState,
    floatingVideoController: FloatingVideoController,
) {
    val scope = rememberCoroutineScope()
    SideEffect { controller.attach(scope) }

    val (isWifi, foreground) = rememberPlatformAutoPlayEnvironmentState()

    val topRoute = nav.stack.lastOrNull { !it.exiting }?.route
    val routeEligible = when (topRoute) {
        Route.Main -> nav.mainTab == 0
        is Route.Profile,
        Route.Favorites,
        is Route.TopicFeed,
        is Route.SuperTopic,
        Route.Nearby,
        is Route.Container,
        Route.MyLikes,
        is Route.Detail,
        -> true
        else -> false
    }
    val hasFloating = floatingVideoController.activeSession != null ||
        floatingVideoController.fullscreenSession != null
    val settingEnabled = App.settings.feedVideoAutoPlay

    LaunchedEffect(settingEnabled, isWifi, foreground, routeEligible, hasFloating) {
        controller.updateEnvironment(
            shouldAutoPlayFeedVideo(
                settingEnabled = settingEnabled,
                isWifi = isWifi,
                isForeground = foreground,
                isRouteActive = routeEligible,
                isLive = false,
                url = "eligible",
                hasFloatingVideo = hasFloating,
            ),
        )
    }
}

/**
 * 信息流卡片内的轻量短视频预览层：播放前自动补拉过期直链，渲染交给各平台原生轻量播放器。
 */
@Composable
internal fun FeedInlineVideoPreview(
    video: VideoInfo,
    muted: Boolean,
    onFirstFrame: () -> Unit,
    onFailed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var resolved by remember(video.url, video.fid, video.statusId) {
        mutableStateOf<ResolvedVideo.DirectStream?>(
            if (
                isDirectVideoStream(video.url) &&
                !shouldRefreshDirectStreamBeforePlay(video.url, video.fid, video.statusId, false)
            ) {
                ResolvedVideo.DirectStream(
                    url = video.url,
                    referer = defaultRefererFor(video.url),
                    isLive = false,
                )
            } else {
                null
            },
        )
    }

    LaunchedEffect(video.url, video.fid, video.statusId) {
        if (resolved != null) return@LaunchedEffect
        val result = resolvePlayableVideo(
            rawUrl = video.url,
            cacheDir = appCacheDir(),
            isLiveHint = false,
            fid = video.fid,
            statusId = video.statusId,
            titleHint = video.title,
        )
        if (result is ResolvedVideo.DirectStream && !result.isLive && result.url.isNotBlank()) {
            resolved = result
        } else {
            onFailed()
        }
    }

    val initialUrl = resolved?.url.orEmpty()
    val referer = resolved?.referer
    val fallbackUrl = resolved?.fallbackUrl
    if (initialUrl.isBlank()) return

    PlatformInlineVideoPreview(
        initialUrl = initialUrl,
        referer = referer,
        fallbackUrl = fallbackUrl,
        muted = muted,
        onFirstFrame = onFirstFrame,
        onFailed = onFailed,
        modifier = modifier,
    )
}

@Composable
internal expect fun rememberPlatformAutoPlayEnvironmentState(): Pair<Boolean, Boolean>

@Composable
internal expect fun PlatformInlineVideoPreview(
    initialUrl: String,
    referer: String?,
    fallbackUrl: String?,
    muted: Boolean,
    onFirstFrame: () -> Unit,
    onFailed: () -> Unit,
    modifier: Modifier = Modifier,
)
