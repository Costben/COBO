package com.cobo.app

import android.app.Application
import android.content.Context
import com.cobo.app.data.WeiboNative
import com.cobo.app.data.WeiboNativeBackend
import com.cobo.app.platform.AndroidPlatformHolder
import com.cobo.app.platform.PlatformContext
import com.sina.weibo.WeiboApplication
import com.sina.weibo.WeiboSpoofContext
import com.sina.weibo.utils.NetCheckUtils

/**
 * 原版 `libwbutil.so` 的 Android 签名后端。
 * 保留为可插拔后端以便在真机上与 [com.cobo.app.data.WeiboNativePure] 做逐字节对拍验证。
 */
internal class AndroidJniWeiboNativeBackend(private val appContext: Context) : WeiboNativeBackend {
    private val spoofContext: Context? = runCatching { WeiboSpoofContext(appContext) }.getOrNull()

    private val weiboApp: WeiboApplication? = runCatching {
        val ctx = spoofContext ?: return@runCatching null
        WeiboApplication.create(ctx).also { it.init(WeiboNative.FROM) }
    }.getOrNull()

    override val available: Boolean get() = weiboApp != null

    override fun s(uid: String): String? =
        weiboApp?.let { runCatching { it.newCalculateS(uid) }.getOrNull() }?.ifBlank { null }

    override fun i(): String? =
        weiboApp?.let { runCatching { it.getIValue("000000000000000") }.getOrNull() }?.ifBlank { null }

    override fun cumFrom(unsigned: String): String? =
        runCatching { NetCheckUtils.getParam(spoofContext ?: appContext, unsigned) }.getOrNull()?.ifBlank { null }
}

class CoboApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidPlatformHolder.initApplication(this)
        WeiboNative.backend = AndroidJniWeiboNativeBackend(this)
        App.init(PlatformContext())
    }
}
