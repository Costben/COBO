package com.cobo.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cobo.app.data.Api
import com.cobo.app.data.CacheStore
import com.cobo.app.data.ContentFilters
import com.cobo.app.data.DetectedWeiboLink
import com.cobo.app.data.FeedSnapshotStore
import com.cobo.app.data.FollowTags
import com.cobo.app.data.IncomingShare
import com.cobo.app.data.ReadLater
import com.cobo.app.data.RecommendSeenStore
import com.cobo.app.data.Repo
import com.cobo.app.data.Settings
import com.cobo.app.data.Status
import com.cobo.app.data.Store
import com.cobo.app.data.UpdateInfo
import com.cobo.app.data.Updater
import com.cobo.app.data.User
import com.cobo.app.data.UserRemarks
import com.cobo.app.data.WeiboEmojiBitmaps
import com.cobo.app.platform.PlatformContext
import com.cobo.app.platform.PlatformHttp
import com.cobo.app.platform.currentTimeMillis
import com.cobo.app.platform.ioDispatcher
import com.cobo.app.platform.setStatusBarDarkIcons
import com.cobo.app.ui.CoboRoot
import com.cobo.app.ui.CoboTheme
import com.cobo.app.ui.isDarkTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

enum class FeedTopAction {
    ToggleTopOrReturn,
    RefreshFromTab,
    LegacyDoubleTap,
    RefreshFromAccessory,
}

@Composable
fun CoboAppRoot() {
    App.ensureInitialized()
    CoboTheme {
        val dark = isDarkTheme()
        LaunchedEffect(dark) {
            setStatusBarDarkIcons(!dark)
        }
        CoboRoot()
    }
}

object App {
    var context: PlatformContext = PlatformContext()
        private set
    lateinit var store: Store
        private set
    lateinit var repo: Repo
        private set
    lateinit var settings: Settings
        private set
    lateinit var cache: CacheStore
        private set
    lateinit var snapshots: FeedSnapshotStore
        private set
    lateinit var recommendSeen: RecommendSeenStore
        private set

    fun isRepoReady(): Boolean = ::repo.isInitialized

    fun isRecommendSeenReady(): Boolean = ::recommendSeen.isInitialized

    fun imageHeadersForHost(host: String): Map<String, String> {
        val lower = host.lowercase()
        val isWeiboHost = IMAGE_REFERER_HOSTS.any { lower == it || lower.endsWith(".$it") }
        if (!isWeiboHost) return emptyMap()
        val referer = if (lower == "upload.api.weibo.com" || lower == "attachment.api.weibo.com") {
            "https://api.weibo.com/chat/"
        } else {
            "https://weibo.com/"
        }
        return buildMap {
            put("User-Agent", Api.WEB_UA)
            put("Referer", referer)
            if (lower == "weibo.com" || lower.endsWith(".weibo.com")) {
                val cookie = runCatching { repo.desktopCookieOrNull() ?: repo.cookie() }.getOrNull()
                if (!cookie.isNullOrBlank()) put("Cookie", cookie)
            }
        }
    }

    private val IMAGE_REFERER_HOSTS = listOf(
        "sinaimg.cn",
        "sinaimg.com",
        "weibocdn.com",
        "weibo.com",
        "weibo.cn",
    )

    var feedVersion by mutableIntStateOf(0)
        private set

    var startRoute: String? = null

    var pendingDeepLink by mutableStateOf<String?>(null)
        private set
    var deepLinkVersion by mutableIntStateOf(0)
        private set

    fun enqueueDeepLink(rawUrl: String?) {
        val trimmed = rawUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return
        pendingDeepLink = trimmed
        deepLinkVersion++
    }

    fun consumeDeepLink(): String? {
        val target = pendingDeepLink
        pendingDeepLink = null
        return target
    }

    /** 待处理的系统分享内容（文字/链接/图片），由根节点消费后清空。 */
    var pendingShare by mutableStateOf<IncomingShare?>(null)
        private set
    var shareVersion by mutableIntStateOf(0)
        private set

    fun enqueueShare(share: IncomingShare) {
        if (share.text.isBlank() && share.imageUris.isEmpty()) return
        pendingShare = share
        shareVersion++
    }

    fun consumeShare(): IncomingShare? {
        val target = pendingShare
        pendingShare = null
        return target
    }

    /** 从剪贴板识别到的微博链接提示；同一次剪贴内容只提示一次。 */
    var pendingClipboardLink by mutableStateOf<DetectedWeiboLink?>(null)
        private set
    private var lastHandledClipboardSignature: String? = null

    fun offerClipboardLink(signature: String, link: DetectedWeiboLink) {
        if (signature.isBlank() || signature == lastHandledClipboardSignature) return
        lastHandledClipboardSignature = signature
        pendingClipboardLink = link
    }

    fun dismissClipboardLink() {
        pendingClipboardLink = null
    }

    var checkingUpdate by mutableStateOf(false)
        private set

    var pendingUpdate by mutableStateOf<UpdateInfo?>(null)
        private set

    var previewingUpdate by mutableStateOf(false)
        private set

    fun dismissUpdate() {
        pendingUpdate = null
        previewingUpdate = false
    }

    fun previewUpdate(info: UpdateInfo) {
        previewingUpdate = true
        pendingUpdate = info
    }

    suspend fun checkUpdate(manual: Boolean): UpdateInfo? {
        if (checkingUpdate) {
            if (manual) toast("正在检查更新…")
            return pendingUpdate
        }
        checkingUpdate = true
        try {
            val current = Updater.currentVersion(context)
            val info = Updater.latest(current, includePrerelease = settings.acceptPrerelease)
            settings.applyUpdateCheckedAt(currentTimeMillis())
            if (info == null) {
                pendingUpdate = null
                if (manual) toast("已是最新版本 $current")
                return null
            }
            if (!manual && info.version == settings.skippedUpdate) return info
            pendingUpdate = info
            return info
        } catch (t: Throwable) {
            if (manual) toast(t.message ?: "检查更新失败")
            return null
        } finally {
            checkingUpdate = false
        }
    }

    val feedRefreshEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val feedTopEvents = MutableSharedFlow<FeedTopAction>(extraBufferCapacity = 1)
    val messagesTopEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val detailRefreshEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val followEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val profileUpdatedEvents = MutableSharedFlow<User>(extraBufferCapacity = 4)
    var profileRenameVersion by mutableIntStateOf(0)
        private set

    fun isReverseBrowseEnabled(): Boolean = this::settings.isInitialized && settings.reverseBrowse

    fun notifyFollowChanged() {
        followEvents.tryEmit(Unit)
    }

    fun notifyProfileUpdated(user: User) {
        profileUpdatedEvents.tryEmit(user)
    }

    fun notifyProfileRenameUpdated() {
        profileRenameVersion++
    }

    fun notifyPublished() {
        feedVersion++
    }

    fun requestFeedRefresh() {
        feedRefreshEvents.tryEmit(Unit)
    }

    fun requestFeedTop(action: FeedTopAction = FeedTopAction.LegacyDoubleTap) {
        feedTopEvents.tryEmit(action)
    }

    fun requestMessagesTop() {
        messagesTopEvents.tryEmit(Unit)
    }

    fun requestDetailRefresh(statusId: String) {
        if (statusId.isNotBlank()) detailRefreshEvents.tryEmit(statusId)
    }

    val toastEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)

    fun toast(message: String) {
        val text = message.trim()
        if (text.isEmpty()) return
        toastEvents.tryEmit(text)
    }

    var pendingLikeConfirm by mutableStateOf<CompletableDeferred<Boolean>?>(null)
        private set

    suspend fun confirmLike(cancelLike: Boolean): Boolean {
        if (!settings.confirmLike || cancelLike) return true
        val gate = CompletableDeferred<Boolean>()
        pendingLikeConfirm?.complete(false)
        pendingLikeConfirm = gate
        return try {
            gate.await()
        } finally {
            if (pendingLikeConfirm === gate) pendingLikeConfirm = null
        }
    }

    fun answerLikeConfirm(confirmed: Boolean) {
        pendingLikeConfirm?.complete(confirmed)
    }

    suspend fun toggleStatusLike(status: Status): Status? {
        if (status.id.isBlank() || repo.isLikeInFlight(status.id)) return null
        if (!confirmLike(status.liked)) return null
        if (repo.isLikeInFlight(status.id)) return null
        return try {
            repo.like(status.id, status.liked)
            val nextLiked = !status.liked
            val nextAttitudes = (status.attitudes + if (status.liked) -1 else 1).coerceAtLeast(0)
            repo.recordStatusLike(status.id, nextLiked, nextAttitudes)
            status.copy(liked = nextLiked, attitudes = nextAttitudes)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            toast(t.message ?: "操作失败")
            null
        }
    }

    fun ensureInitialized() {
        if (this::store.isInitialized) return
        init(PlatformContext())
    }

    fun init(context: PlatformContext) {
        if (this::store.isInitialized) return
        this.context = context
        store = Store(this.context)
        FollowTags.attach(store)
        ContentFilters.attach(store)
        UserRemarks.attach(store)
        ReadLater.attach(store)
        cache = CacheStore(this.context)
        snapshots = FeedSnapshotStore(this.context)
        recommendSeen = RecommendSeenStore(this.context)
        repo = Repo(this.context, store)
        settings = Settings(this.context)
        WeiboEmojiBitmaps.attach(this.context)
        PlatformHttp.installCoilImageLoader { host -> imageHeadersForHost(host) }
        CoroutineScope(SupervisorJob() + ioDispatcher).launch {
            runCatching { repo.emotions() }
        }
    }
}
