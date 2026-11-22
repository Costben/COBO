package com.cobo.app

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okhttp3.OkHttpClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cobo.app.data.CacheStore
import com.cobo.app.data.FeedSnapshotStore
import com.cobo.app.data.FollowTags
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import com.cobo.app.data.Repo
import com.cobo.app.data.Settings
import com.cobo.app.data.Store
import com.cobo.app.data.UpdateInfo
import com.cobo.app.data.Updater
import com.cobo.app.data.User
import com.cobo.app.data.WeiboEmojiBitmaps

class CoboApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        App.init(this)
        WeiboEmojiBitmaps.attach(this)
        // 表情表先有内置的一整份，这里只是顺手把接口里的新表情补进来；失败就继续用内置表。
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
            .launch { runCatching { App.repo.emotions() } }
        // 私信附件在 attachment.api.weibo.com 上，不带 cookie 直接 403，
        // 默认加载器拿不到图，气泡就只剩一圈内边距。只给 weibo 域名挂 cookie。
        SingletonImageLoader.setSafe { ctx ->
            ImageLoader.Builder(ctx)
                .components {
                    add(OkHttpNetworkFetcherFactory(callFactory = { App.imageClient }))
                }
                .build()
        }
    }
}

object App {
    lateinit var context: Context
        private set
    lateinit var store: Store
        private set
    lateinit var repo: Repo
        private set
    lateinit var settings: Settings
        private set
    lateinit var cache: CacheStore
        private set

    /** 首页时间线的续看快照，见 [FeedSnapshotStore]。 */
    lateinit var snapshots: FeedSnapshotStore
        private set

    fun isRepoReady(): Boolean = ::repo.isInitialized

    /** Coil 专用客户端：微博图床与附件域名补全 Referer/UA 及登录 cookie。 */
    val imageClient: OkHttpClient by lazy {
        OkHttpClient.Builder().addInterceptor { chain ->
            val req = chain.request()
            val host = req.url.host.lowercase()
            val isWeiboHost = IMAGE_REFERER_HOSTS.any { host == it || host.endsWith(".$it") }
            if (!isWeiboHost) {
                chain.proceed(req)
            } else {
                val referer = if (host == "upload.api.weibo.com" || host == "attachment.api.weibo.com") {
                    "https://api.weibo.com/chat/"
                } else {
                    "https://weibo.com/"
                }
                val builder = req.newBuilder()
                    .header("User-Agent", com.cobo.app.data.Api.WEB_UA)
                    .header("Referer", referer)
                if (host == "weibo.com" || host.endsWith(".weibo.com")) {
                    val cookie = runCatching { repo.desktopCookieOrNull() ?: repo.cookie() }.getOrNull()
                    if (!cookie.isNullOrBlank()) builder.header("Cookie", cookie)
                }
                chain.proceed(builder.build())
            }
        }.build()
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

    /** 临时：`am start --es cobo.route tablab` 直接进顶栏对比页，验完删。 */
    var startRoute: String? = null

    /** 待处理的外部深链（流体云/系统跳转传入），由根节点消费后清空。 */
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

    /** 正在检查更新：设置页那一行据此换成「正在检查…」。 */
    var checkingUpdate by mutableStateOf(false)
        private set

    /** 查到的新版本；非空时根节点弹更新提示，关掉就清空。 */
    var pendingUpdate by mutableStateOf<UpdateInfo?>(null)
        private set

    /**
     * 这次更新提示是「预览」：从更新日志页手动放一遍，不参与跳过记录，也不影响版本判断。
     */
    var previewingUpdate by mutableStateOf(false)
        private set

    fun dismissUpdate() {
        pendingUpdate = null
        previewingUpdate = false
    }

    /** 把任意一版的更新提示原样放一遍，用来核对弹窗界面与下载速度。 */
    fun previewUpdate(info: UpdateInfo) {
        previewingUpdate = true
        pendingUpdate = info
    }

    /**
     * 查一次更新。[manual] 为 true 时无论有没有结果都给反馈（toast），
     * 自动检查只在真的查到新版本时挂上 [pendingUpdate]。
     */
    suspend fun checkUpdate(manual: Boolean): UpdateInfo? {
        if (checkingUpdate) {
            if (manual) toast("正在检查更新…")
            return pendingUpdate
        }
        checkingUpdate = true
        try {
            val current = Updater.currentVersion(context)
            val info = Updater.latest(current, includePrerelease = settings.acceptPrerelease)
            settings.applyUpdateCheckedAt(System.currentTimeMillis())
            if (info == null) {
                pendingUpdate = null
                if (manual) toast("已是最新版本 $current")
                return null
            }
            // 用户明确跳过这个版本就别再拿自动检查烦他，手动检查照旧提示。
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
    val feedTopEvents = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
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

    fun requestFeedTop(scrollToTop: Boolean = true) {
        feedTopEvents.tryEmit(scrollToTop)
    }

    fun requestMessagesTop() {
        messagesTopEvents.tryEmit(Unit)
    }

    fun requestDetailRefresh(statusId: String) {
        if (statusId.isNotBlank()) detailRefreshEvents.tryEmit(statusId)
    }

    fun toast(message: String) {
        if (!this::context.isInitialized || message.isBlank()) return
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 正在等用户回答的点赞确认。设置里打开「点赞前确认」后 [confirmLike] 把它挂上，
     * 根节点看到非空就弹窗，答复完（或调用方被取消）再清掉。
     */
    var pendingLikeConfirm by mutableStateOf<CompletableDeferred<Boolean>?>(null)
        private set

    /**
     * 点赞前问一句。设置里没开就直接放行；开了就挂起，等根节点上的弹窗给答复。
     * 只有点赞要问：[cancelLike] 为 true 表示这次点的是已经赞过的爱心，取消赞直接生效，不打断。
     *
     * 放在动作这一层而不是 [Repo.like]：详情页的评论点赞是先改界面再发请求，
     * 取消时得在改界面之前就退出去，不能在数据层里悄悄吞掉。
     */
    suspend fun confirmLike(cancelLike: Boolean): Boolean {
        if (!settings.confirmLike || cancelLike) return true
        val gate = CompletableDeferred<Boolean>()
        // 上一次还挂着（例如弹窗正在收起）就先按「不赞」结掉，别让它永远等不到答复。
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

    fun init(context: Context) {
        if (this::store.isInitialized) return
        this.context = context.applicationContext
        store = Store(this.context)
        FollowTags.attach(store)
        cache = CacheStore(this.context)
        snapshots = FeedSnapshotStore(this.context)
        repo = Repo(this.context, store)
        settings = Settings(this.context)
    }
}
