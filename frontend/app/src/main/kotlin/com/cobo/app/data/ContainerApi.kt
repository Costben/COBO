package com.cobo.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 容器端（`api.weibo.cn/2` 下的那套私有接口）客户端。
 *
 * 跟网页端的 m.weibo.cn/api 那套是两回事：容器端认客户端会话（`gsid`），并且每个请求都要带
 * `i`（设备指纹）、`s`（按 uid 算的签名）、`cum`（按路径算的校验参数）。三个值都来自打包进来的
 * `libwbutil.so`，见 [WeiboNative]。
 *
 * `cum` 的输入是「`/2/` + path + `?` + 全部其它参数的编码串」，算完再塞进 query，所以这里
 * 自己拼编码而不是交给 OkHttp 的 HttpUrl——它会把参数重新排一遍，`cum` 就对不上了。
 */
object ContainerApi {

    /**
     * 服务端下发的 `X-Validator`。Share 的拦截器把它存成静态字段，
     * 之后每个请求都带回去；第一次请求时它还是空的。
     */
    @Volatile
    var validator: String = ""

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /** 通用参数。顺序即 `cum` 的输入顺序，别动。 */
    private fun common(session: Session?): LinkedHashMap<String, String?> = linkedMapOf(
        "c" to "android",
        "from" to WeiboNative.FROM,
        "wm" to "2468_1001",
        "oldwm" to Api.oldWm(),
        "ua" to Api.clientUa(),
        "v_p" to "89",
        "wb_version" to "5005",
        "skin" to "default",
        "v_f" to "2",
        "lang" to "zh_CN",
        "aid" to "7501641714",
        "networktype" to Api.networkType(),
        "android_id" to Api.androidId(),
        "gsid" to session?.gsid?.ifBlank { null },
        "i" to WeiboNative.i(),
        "s" to session?.uid?.ifBlank { null }?.let { WeiboNative.s(it) },
    )

    suspend fun get(
        path: String,
        params: Map<String, String?> = emptyMap(),
        session: Session,
    ): JSONObject = call(path, params, emptyMap(), session, post = false)

    suspend fun post(
        path: String,
        params: Map<String, String?> = emptyMap(),
        form: Map<String, String> = emptyMap(),
        session: Session,
    ): JSONObject = call(path, params, form, session, post = true)

    private suspend fun call(
        path: String,
        params: Map<String, String?>,
        form: Map<String, String>,
        session: Session,
        post: Boolean,
    ): JSONObject = withContext(Dispatchers.IO) {
        val query = common(session)
        params.forEach { (key, value) -> query[key] = value }
        val unsigned = "/2/${path.trimStart('/')}?" + encode(query)
        WeiboNative.cumFrom(unsigned)?.let { query["cum"] = it }

        val url = "https://api.weibo.cn/2/${path.trimStart('/')}?" + encode(query)
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", Api.clientUa())
            .header("Accept", "application/json")
            .header("Referer", "https://m.weibo.cn/")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("MWeibo-Pwa", "1")
            // 与 Share 的拦截器一致：每个容器请求都带登录 uid 和一次性的会话 id。
            .header("X-Log-Uid", session.uid)
            .header("X-Sessionid", java.util.UUID.randomUUID().toString())
            .apply { if (validator.isNotBlank()) header("X-Validator", validator) }
        if (session.cookie.isNotBlank()) builder.header("Cookie", session.cookie)
        if (post) {
            val body = FormBody.Builder().apply {
                form.forEach { (key, value) -> add(key, value) }
            }.build()
            builder.post(body)
        }
        client.newCall(builder.build()).execute().use { response ->
            response.header("X-Validator")?.takeIf { it.isNotBlank() }?.let { validator = it }
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ApiHttpException(response.code, text)
            if (text.isBlank()) throw ApiException("空响应")
            val json = runCatching { JSONObject(text) }.getOrElse { throw ApiException("响应不是 JSON") }
            val errno = json.optInt("errno", 0)
            if (errno != 0) {
                throw ApiException(json.optString("errmsg").ifBlank { "接口错误（$errno）" }, errno.toString())
            }
            json
        }
    }

    private fun encode(map: Map<String, String?>): String = map.entries
        .filter { it.value != null }
        .joinToString("&") { (key, value) ->
            URLEncoder.encode(key, "UTF-8") + "=" + URLEncoder.encode(value.orEmpty(), "UTF-8")
        }
}
