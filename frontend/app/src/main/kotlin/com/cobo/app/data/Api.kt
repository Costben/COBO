package com.cobo.app.data

import com.cobo.app.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
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

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
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

    private fun execute(request: Request): JSONObject {
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw ApiHttpException(response.code, text)
            }
            if (text.isBlank()) throw ApiException("空响应")
            return try {
                JSONObject(text)
            } catch (t: Throwable) {
                throw ApiException("响应不是 JSON")
            }
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

    @Suppress("unused")
    private val jsonMedia = JSON
}
