package com.cobo.app.data

import android.content.Context
import com.cobo.app.App
import com.sina.weibo.WeiboApplication
import com.sina.weibo.WeiboSpoofContext
import com.sina.weibo.utils.NetCheckUtils

/**
 * 打包进来的原版 `libwbutil.so` 的 Kotlin 包装，负责 `s` / `i` / `cum` 三个参数。
 *
 * 通过 [WeiboSpoofContext] 向未改动的 `libwbutil.so` 提供 `com.sina.weibo` 包名与官方签名摘要，
 * 确保 `init()` / `newCalculateS()` / `getIValue()` / `getParam()` 算出的密钥与原版完全一致。
 */
object WeiboNative {

    /** 渠道号。跟容器接口的 `from` 用同一个，换渠道要一起换。 */
    const val FROM = "10B6395010"

    /** `getIValue` 的固定输入，原版客户端也是这个 15 个 0。 */
    private const val I_INPUT = "000000000000000"

    private val spoofContext: Context? = runCatching {
        WeiboSpoofContext(App.context)
    }.getOrNull()

    private val app: WeiboApplication? = runCatching {
        val ctx = spoofContext ?: return@runCatching null
        WeiboApplication.create(ctx).also { it.init(FROM) }
    }.getOrNull()

    val available: Boolean get() = app != null

    fun s(uid: String): String? =
        app?.let { runCatching { it.newCalculateS(uid) }.getOrNull() }?.ifBlank { null }

    fun i(): String? =
        app?.let { runCatching { it.getIValue(I_INPUT) }.getOrNull() }?.ifBlank { null }

    /** [path] 不带 `/2/` 前缀，和接口路径写法一致。 */
    fun cum(path: String): String? = cumFrom("/2/" + path.trimStart('/'))

    /** [unsigned] 是含 query 的完整路径（`/2/xxx?a=b`）。容器接口按这个口径算。 */
    fun cumFrom(unsigned: String): String? =
        runCatching { NetCheckUtils.getParam(spoofContext ?: App.context, unsigned) }.getOrNull()?.ifBlank { null }
}
