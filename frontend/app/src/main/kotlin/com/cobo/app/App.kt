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
import com.cobo.app.data.FollowTags
import kotlinx.coroutines.flow.MutableSharedFlow
import com.cobo.app.data.Repo
import com.cobo.app.data.Settings
import com.cobo.app.data.Store
import com.cobo.app.data.UpdateInfo
import com.cobo.app.data.Updater

class CoboApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        App.init(this)
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

    /** Coil 专用客户端：微博附件域名要带登录 cookie。 */
    val imageClient: OkHttpClient by lazy {
        OkHttpClient.Builder().addInterceptor { chain ->
            val req = chain.request()
            val cookie = runCatching { repo.cookie() }.getOrNull()
            if (cookie.isNullOrBlank() || !req.url.host.endsWith("weibo.com")) {
                chain.proceed(req)
            } else {
                chain.proceed(
                    req.newBuilder()
                        .header("Cookie", cookie)
                        .header("User-Agent", com.cobo.app.data.Api.WEB_UA)
                        .build(),
                )
            }
        }.build()
    }

    var feedVersion by mutableIntStateOf(0)
        private set

    /** 正在检查更新：设置页那一行据此换成「正在检查…」。 */
    var checkingUpdate by mutableStateOf(false)
        private set

    /** 查到的新版本；非空时根节点弹更新提示，关掉就清空。 */
    var pendingUpdate by mutableStateOf<UpdateInfo?>(null)
        private set

    fun dismissUpdate() {
        pendingUpdate = null
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
    val feedTopEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val messagesTopEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val followEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun notifyFollowChanged() {
        followEvents.tryEmit(Unit)
    }

    fun notifyPublished() {
        feedVersion++
    }

    fun requestFeedRefresh() {
        feedRefreshEvents.tryEmit(Unit)
    }

    fun requestFeedTop() {
        feedTopEvents.tryEmit(Unit)
    }

    fun requestMessagesTop() {
        messagesTopEvents.tryEmit(Unit)
    }

    fun toast(message: String) {
        if (message.isBlank()) return
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun init(context: Context) {
        if (this::store.isInitialized) return
        this.context = context.applicationContext
        store = Store(this.context)
        FollowTags.attach(store)
        cache = CacheStore(this.context)
        repo = Repo(this.context, store)
        settings = Settings(this.context)
    }
}
