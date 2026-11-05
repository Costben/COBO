package com.cobo.app.data

import com.cobo.app.App
import com.sina.weibo.WeiboApplication
import com.sina.weibo.utils.NetCheckUtils

/**
 * 打包进来的 `libwbutil.so` 的 Kotlin 包装，负责 `s` / `i` / `cum` 三个参数。
 *
 * 三个值都来自 native，但只有 `init()` 之后才有意义；没有 `init` 时 `s` 会退化成
 * `00000000` 这类重复字符，看起来「算出了东西」其实是废值，所以这里把初始化放进
 * 单例构造，失败就把 [available] 置 false，调用方自己退回 Web 路由。
 */
object WeiboNative {

    /** 渠道号。跟容器接口的 `from` 用同一个，换渠道要一起换。 */
    const val FROM = "10B6395010"

    /** `getIValue` 的固定输入，原版客户端也是这个 15 个 0。 */
    private const val I_INPUT = "000000000000000"

    private val app: WeiboApplication? = runCatching {
        // 先碰一下类：这一步会触发 System.loadLibrary("wbutil") 与 JNI_OnLoad，
        // 之后再把手里的 context 递给 native 侧回调。
        WeiboApplication.attach(App.context)
        WeiboApplication().also { it.init(FROM) }
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
        runCatching { NetCheckUtils.getParam(App.context, unsigned) }.getOrNull()?.ifBlank { null }
}
