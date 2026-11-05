package com.cobo.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import com.cobo.app.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ApiException(message: String, val errno: String? = null) : Exception(message)

/**
 * 非 2xx 响应。微博的 CSRF 校验失败就是 `HTTP 403` + `errno:100006 / 请求非法`，
 * 而 [ApiException] 只带一句 "HTTP 403"，调用方没法判断该不该刷新 st 重试。
 */
class ApiHttpException(
    val code: Int,
    val body: String,
) : Exception("HTTP $code")

object Api {
    const val WEB_UA =
        "Mozilla/5.0 (Linux; Android 13; MI 9) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    const val DESKTOP_UA =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    /**
     * 每收到一次微博响应，就把里面的 `Set-Cookie` 交出去。由 [Repo] 挂上，
     * 用来把网页端滚动下发的 `SUB` / `ALF` 收进 cookie 表。
     */
    @Volatile
    var cookieSink: ((HttpUrl, List<String>) -> Unit)? = null

    private val capture = okhttp3.Interceptor { chain ->
        val response = chain.proceed(chain.request())
        val headers = response.headers.values("Set-Cookie")
        if (headers.isNotEmpty() && CookieVault.isWeibo(response.request.url.host)) {
            runCatching { cookieSink?.invoke(response.request.url, headers) }
        }
        response
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(capture)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    fun url(base: String, params: Map<String, String?> = emptyMap()): String {
        val builder = base.toHttpUrl().newBuilder()
        params.forEach { (key, value) -> if (value != null) builder.addQueryParameter(key, value) }
        return builder.build().toString()
    }

    private fun Request.Builder.weiboHeaders(cookie: String?, st: String? = null) = apply {
        header("User-Agent", WEB_UA)
        header("Accept", "application/json")
        header("Referer", "https://m.weibo.cn/")
        header("X-Requested-With", "XMLHttpRequest")
        header("MWeibo-Pwa", "1")
        if (!cookie.isNullOrBlank()) header("Cookie", cookie)
        if (!st.isNullOrBlank()) header("X-XSRF-TOKEN", st)
    }

    private fun execute(request: Request): JSONObject = executeFull(request).first

    /** 同 [execute]，另外带回 `Set-Cookie`。客户端登录要靠它接住服务端下发的会话 cookie。 */
    private fun executeFull(request: Request): Pair<JSONObject, List<String>> {
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw ApiHttpException(response.code, text)
            }
            if (text.isBlank()) throw ApiException("空响应")
            val json = try {
                JSONObject(text)
            } catch (t: Throwable) {
                throw ApiException("响应不是 JSON")
            }
            return json to response.headers.values("Set-Cookie")
        }
    }

    suspend fun get(url: String, cookie: String?, force: Boolean = false): JSONObject =
        withContext(Dispatchers.IO) {
            if (!force) {
                App.cache.read(url, cookie)?.let { return@withContext it }
            }
            val json = execute(Request.Builder().url(url).weiboHeaders(cookie).get().build())
            App.cache.write(url, cookie, json)
            json
        }

    /**
     * 带 XSRF 令牌的读请求，且不进缓存：搜索联想这类随输入变化的结果，缓存下来只会拿到旧词。
     */
    suspend fun getWithSt(url: String, cookie: String?, st: String?): JSONObject =
        withContext(Dispatchers.IO) {
            execute(Request.Builder().url(url).weiboHeaders(cookie, st).get().build())
        }

    /**
     * Some weibo.com ajax endpoints reject the mobile referer, so this variant signs the request
     * the way a desktop browser does and skips the cache.
     */
    suspend fun getDesktop(url: String, cookie: String?): JSONObject =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", DESKTOP_UA)
                .header("Accept", "application/json")
                .header("Referer", "https://weibo.com/")
                .header("X-Requested-With", "XMLHttpRequest")
                .apply { if (!cookie.isNullOrBlank()) header("Cookie", cookie) }
                .get()
                .build()
            execute(request)
        }

    suspend fun post(
        url: String,
        form: Map<String, String>,
        cookie: String?,
        st: String?,
    ): JSONObject = withContext(Dispatchers.IO) {
        val body = FormBody.Builder().apply {
            form.forEach { (key, value) -> add(key, value) }
        }.build()
        execute(Request.Builder().url(url).weiboHeaders(cookie, st).post(body).build())
    }

    suspend fun getHtml(url: String, cookie: String?): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", WEB_UA)
            .header("Referer", "https://m.weibo.cn/")
            .apply { if (!cookie.isNullOrBlank()) header("Cookie", cookie) }
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw ApiHttpException(response.code, response.body?.string().orEmpty())
            response.body?.string().orEmpty()
        }
    }

    suspend fun resolveRedirect(url: String, cookie: String?): String = withContext(Dispatchers.IO) {
        val noRedirectClient = client.newBuilder().followRedirects(false).build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", WEB_UA)
            .apply { if (!cookie.isNullOrBlank()) header("Cookie", cookie) }
            .get()
            .build()
        noRedirectClient.newCall(request).execute().use { response ->
            response.header("Location") ?: url
        }
    }

    suspend fun postVote(
        url: String,
        form: Map<String, String>,
        cookie: String?,
        referer: String,
    ): JSONObject = withContext(Dispatchers.IO) {
        val body = FormBody.Builder().apply {
            form.forEach { (k, v) -> add(k, v) }
        }.build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", WEB_UA)
            .header("Referer", referer)
            .header("X-Requested-With", "XMLHttpRequest")
            .apply { if (!cookie.isNullOrBlank()) header("Cookie", cookie) }
            .post(body)
            .build()
        execute(request)
    }

    suspend fun upload(
        url: String,
        bytes: ByteArray,
        fileName: String,
        mime: String,
        cookie: String?,
        st: String?,
        /** 文件字段名。发微博图是 `pic`，私信图是 `file`。 */
        field: String = "pic",
        /** 附带的其他表单字段，比如私信上传要的 `tuid`。 */
        extra: Map<String, String> = emptyMap(),
    ): JSONObject = withContext(Dispatchers.IO) {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("st", st.orEmpty())
            .apply { extra.forEach { (k, v) -> addFormDataPart(k, v) } }
            .addFormDataPart(field, fileName, bytes.toRequestBody(mime.toMediaType()))
            .build()
        execute(Request.Builder().url(url).weiboHeaders(cookie, st).post(body).build())
    }

    /**
     * 向微博领一个访客身份，返回可直接塞进 Cookie 头的 `SUB=…; SUBP=…`。
     * m.weibo.cn 的只读接口不带 cookie 一律 HTTP 432，带上它就能像未登录的网页版一样读。
     */
    suspend fun genVisitor(): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://visitor.passport.weibo.cn/visitor/genvisitor2")
            .header("User-Agent", WEB_UA)
            .header("Referer", "https://m.weibo.cn/")
            .post(
                FormBody.Builder()
                    .add("cb", "visitor_gray_callback")
                    .add("tid", "")
                    .add("from", "weibo")
                    .build()
            )
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ApiHttpException(response.code, text)
            // 回的是 JSONP：visitor_gray_callback({...})
            val json = text.substringAfter('(', "").substringBeforeLast(')', "")
            val data = runCatching { JSONObject(json).optJSONObject("data") }.getOrNull()
            val sub = data?.str("sub").orEmpty()
            val subp = data?.str("subp").orEmpty()
            if (sub.isBlank()) throw ApiException("领取访客身份失败")
            if (subp.isBlank()) "SUB=$sub" else "SUB=$sub; SUBP=$subp"
        }
    }

    fun jsonRequestUrl(base: String, params: Map<String, String?> = emptyMap()) = url(base, params)

    // ---- 容器端（api.weibo.cn/2）：客户端登录与登录态续期 ----

    /** 容器接口自报的客户端标识，格式与客户端 UA 一致。 */
    fun clientUa(): String =
        "${Build.MANUFACTURER}-${Build.MODEL}__weibo__11.6.3__android__android${Build.VERSION.RELEASE}"

    fun oldWm(): String = when (Build.MANUFACTURER.uppercase()) {
        "ONEPLUS" -> "14038_0004"
        "MEIZU" -> "9848_0009"
        "OPPO" -> "9847_0002"
        "VIVO" -> "9856_0004"
        "SMARTISAN" -> "14010_0013"
        "ZUK" -> "20003_0002"
        "SONY" -> "9982_90002"
        "XIAOMI" -> "20005_0002"
        else -> "3333_1001"
    }

    fun androidId(): String = runCatching {
        Settings.Secure.getString(App.context.contentResolver, Settings.Secure.ANDROID_ID)
    }.getOrNull().orEmpty()

    fun networkType(): String = runCatching {
        val manager = App.context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = manager.getNetworkCapabilities(manager.activeNetwork)
        when {
            caps == null -> "none"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "4g"
            else -> "none"
        }
    }.getOrDefault("none")

    suspend fun containerPost(
        path: String,
        query: Map<String, String?>,
        form: Map<String, String>,
        cookie: String? = null,
        ua: String? = null,
    ): JSONObject = containerPostFull(path, query, form, cookie, ua).first

    /** 同 [containerPost]，额外带回响应里的 `Set-Cookie`。 */
    suspend fun containerPostFull(
        path: String,
        query: Map<String, String?>,
        form: Map<String, String>,
        cookie: String? = null,
        ua: String? = null,
    ): Pair<JSONObject, List<String>> = withContext(Dispatchers.IO) {
        val target = url("https://api.weibo.cn/2/$path", query)
        val body = FormBody.Builder().apply {
            form.forEach { (key, value) -> add(key, value) }
        }.build()
        val request = Request.Builder()
            .url(target)
            .header("Referer", "https://m.weibo.cn/")
            .apply { if (!ua.isNullOrBlank()) header("User-Agent", ua) }
            .apply { if (!cookie.isNullOrBlank()) header("Cookie", cookie) }
            .post(body)
            .build()
        executeFull(request)
    }

    @Suppress("unused")
    private val jsonMedia = JSON
}
