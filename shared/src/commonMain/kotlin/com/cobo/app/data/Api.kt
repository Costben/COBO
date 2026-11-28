package com.cobo.app.data

import com.cobo.app.App
import com.cobo.app.json.JSONObject
import com.cobo.app.platform.PlatformHttp
import com.cobo.app.platform.PlatformHttpResponse
import com.cobo.app.platform.buildUrl
import com.cobo.app.platform.crc32Value
import com.cobo.app.platform.ioDispatcher
import com.cobo.app.platform.parseUrlHost
import com.cobo.app.platform.platformDeviceId
import com.cobo.app.platform.platformDeviceManufacturer
import com.cobo.app.platform.platformDeviceModel
import com.cobo.app.platform.platformNetworkType
import com.cobo.app.platform.platformOsVersion
import com.cobo.app.platform.resolveRelativeUrl
import kotlinx.coroutines.withContext

open class ApiException(message: String, val errno: String? = null) : Exception(message)

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

    var cookieSink: ((String, List<String>, Boolean) -> Unit)? = null
        set(value) {
            field = value
            PlatformHttp.cookieSink = if (value == null) {
                null
            } else {
                { url, host, setCookies, hadAuth ->
                    if (CookieVault.isWeibo(host)) value(url, setCookies, hadAuth)
                }
            }
        }

    fun url(base: String, params: Map<String, String?> = emptyMap()): String = buildUrl(base, params)

    private fun weiboHeaders(
        cookie: String?,
        st: String? = null,
        includeOrigin: Boolean = false,
    ): Map<String, String> = buildMap {
        put("User-Agent", WEB_UA)
        put("Accept", "application/json")
        put("Referer", "https://m.weibo.cn/")
        if (includeOrigin) put("Origin", "https://m.weibo.cn")
        put("X-Requested-With", "XMLHttpRequest")
        put("MWeibo-Pwa", "1")
        if (!cookie.isNullOrBlank()) put("Cookie", cookie)
        if (!st.isNullOrBlank()) put("X-XSRF-TOKEN", st)
    }

    private fun parseJsonResponse(resp: PlatformHttpResponse): Pair<JSONObject, List<String>> {
        val text = resp.bodyText
        if (!resp.isSuccessful) throw ApiHttpException(resp.code, text)
        if (text.isBlank()) throw ApiException("空响应")
        val json = runCatching { JSONObject(text) }.getOrElse { throw ApiException("响应不是 JSON") }
        return json to resp.setCookies
    }

    suspend fun get(url: String, cookie: String?, force: Boolean = false): JSONObject =
        withContext(ioDispatcher) {
            if (!force) {
                App.cache.read(url, cookie)?.let { return@withContext it }
            }
            val resp = PlatformHttp.get(url, headers = weiboHeaders(cookie))
            val json = parseJsonResponse(resp).first
            if (json.optInt("ok", 1) == 1 && !json.has("errno")) {
                App.cache.write(url, cookie, json)
            }
            json
        }

    suspend fun getWithSt(url: String, cookie: String?, st: String?): JSONObject =
        withContext(ioDispatcher) {
            parseJsonResponse(PlatformHttp.get(url, headers = weiboHeaders(cookie, st))).first
        }

    suspend fun getDesktop(url: String, cookie: String?): JSONObject =
        withContext(ioDispatcher) {
            val headers = buildMap {
                put("User-Agent", DESKTOP_UA)
                put("Accept", "application/json, text/plain, */*")
                put("Referer", "https://weibo.com/")
                put("X-Requested-With", "XMLHttpRequest")
                if (!cookie.isNullOrBlank()) put("Cookie", cookie)
            }
            parseJsonResponse(PlatformHttp.get(url, headers = headers)).first
        }

    suspend fun postDesktopJson(
        url: String,
        jsonBody: String,
        cookie: String?,
        xsrfToken: String?,
    ): JSONObject = withContext(ioDispatcher) {
        val headers = buildMap {
            put("User-Agent", DESKTOP_UA)
            put("Accept", "application/json, text/plain, */*")
            put("Referer", "https://weibo.com/")
            put("Origin", "https://weibo.com")
            put("X-Requested-With", "XMLHttpRequest")
            if (!cookie.isNullOrBlank()) put("Cookie", cookie)
            if (!xsrfToken.isNullOrBlank()) put("X-XSRF-TOKEN", xsrfToken)
        }
        parseJsonResponse(PlatformHttp.postJson(url, jsonBody, headers)).first
    }

    suspend fun postDesktopForm(
        url: String,
        form: Map<String, String>,
        cookie: String?,
        xsrfToken: String?,
    ): JSONObject = withContext(ioDispatcher) {
        val headers = buildMap {
            put("User-Agent", DESKTOP_UA)
            put("Accept", "application/json, text/plain, */*")
            put("Referer", "https://weibo.com/")
            put("Origin", "https://weibo.com")
            put("X-Requested-With", "XMLHttpRequest")
            if (!cookie.isNullOrBlank()) put("Cookie", cookie)
            if (!xsrfToken.isNullOrBlank()) put("X-XSRF-TOKEN", xsrfToken)
        }
        parseJsonResponse(PlatformHttp.postForm(url, form, headers)).first
    }

    suspend fun getWebIm(url: String, cookie: String?): JSONObject =
        withContext(ioDispatcher) {
            val headers = buildMap {
                put("User-Agent", DESKTOP_UA)
                put("Accept", "application/json, text/plain, */*")
                put("Referer", "https://api.weibo.com/chat/")
                put("Origin", "https://api.weibo.com")
                if (!cookie.isNullOrBlank()) put("Cookie", cookie)
            }
            parseJsonResponse(PlatformHttp.get(url, headers = headers)).first
        }

    suspend fun postWebIm(url: String, form: Map<String, String>, cookie: String?): JSONObject =
        withContext(ioDispatcher) {
            val headers = buildMap {
                put("User-Agent", DESKTOP_UA)
                put("Accept", "application/json, text/plain, */*")
                put("Referer", "https://api.weibo.com/chat/")
                put("Origin", "https://api.weibo.com")
                if (!cookie.isNullOrBlank()) put("Cookie", cookie)
            }
            parseJsonResponse(PlatformHttp.postForm(url, form, headers)).first
        }

    suspend fun uploadDesktopPic(
        bytes: ByteArray,
        uid: String,
        rawMd5: String,
        mime: String,
        cookie: String,
    ): JSONObject = withContext(ioDispatcher) {
        val target = url(
            "https://picupload.weibo.com/interface/upload.php",
            linkedMapOf(
                "file_source" to "1",
                "cs" to crc32Value(bytes).toString(),
                "ent" to "miniblog",
                "appid" to "339644097",
                "uid" to uid,
                "raw_md5" to rawMd5,
                "ori" to "1",
                "data" to "1",
            ),
        )
        val headers = mapOf(
            "User-Agent" to DESKTOP_UA,
            "Referer" to "https://weibo.com/",
            "Origin" to "https://weibo.com",
            "Cookie" to cookie,
        )
        parseJsonResponse(PlatformHttp.postBytes(target, bytes, mime, headers)).first
    }

    suspend fun post(
        url: String,
        form: Map<String, String>,
        cookie: String?,
        st: String?,
    ): JSONObject = withContext(ioDispatcher) {
        parseJsonResponse(PlatformHttp.postForm(url, form, weiboHeaders(cookie, st, includeOrigin = true))).first
    }

    suspend fun getHtml(url: String, cookie: String?, desktop: Boolean = false): String =
        withContext(ioDispatcher) {
            val headers = buildMap {
                put("User-Agent", if (desktop) DESKTOP_UA else WEB_UA)
                put("Referer", if (desktop) "https://weibo.com/" else "https://m.weibo.cn/")
                if (!cookie.isNullOrBlank()) put("Cookie", cookie)
            }
            val resp = PlatformHttp.get(url, headers = headers)
            if (!resp.isSuccessful) throw ApiHttpException(resp.code, resp.bodyText)
            resp.bodyText
        }

    suspend fun resolveRedirect(
        url: String,
        cookie: String?,
        cookieForHost: ((String) -> String?)? = null,
    ): String = withContext(ioDispatcher) {
        var current = com.cobo.app.ui.unwrapSinaUrl(url) ?: url
        repeat(6) {
            if (!current.startsWith("http://", ignoreCase = true) &&
                !current.startsWith("https://", ignoreCase = true)
            ) {
                return@withContext current
            }
            if (superTopicId(current) != null) return@withContext current
            val next = runCatching {
                val host = parseUrlHost(current)
                val isDesktopHost = host == "weibo.com" || host.endsWith(".weibo.com")
                val effectiveCookie = cookieForHost?.invoke(host) ?: cookie
                val headers = buildMap {
                    put("User-Agent", if (isDesktopHost) DESKTOP_UA else WEB_UA)
                    if (!effectiveCookie.isNullOrBlank() && CookieVault.isWeibo(host)) {
                        put("Cookie", effectiveCookie)
                    }
                }
                val resp = PlatformHttp.get(current, headers = headers, followRedirects = false)
                val location = resp.location?.trim().orEmpty()
                if (location.isEmpty()) return@runCatching null
                val resolvedLocation = resolveRelativeUrl(current, location)
                if (resolvedLocation.contains("passport.weibo.com/visitor/", ignoreCase = true) ||
                    resolvedLocation.contains("visitor.passport.weibo.cn/", ignoreCase = true)
                ) {
                    return@withContext current
                }
                com.cobo.app.ui.unwrapSinaUrl(resolvedLocation) ?: resolvedLocation
            }.getOrNull() ?: return@withContext current
            if (next == current) return@withContext current
            current = next
        }
        current
    }

    suspend fun postVote(
        url: String,
        form: Map<String, String>,
        cookie: String?,
        referer: String,
    ): JSONObject = withContext(ioDispatcher) {
        val headers = buildMap {
            put("User-Agent", WEB_UA)
            put("Referer", referer)
            put("X-Requested-With", "XMLHttpRequest")
            if (!cookie.isNullOrBlank()) put("Cookie", cookie)
        }
        parseJsonResponse(PlatformHttp.postForm(url, form, headers)).first
    }

    suspend fun upload(
        url: String,
        bytes: ByteArray,
        fileName: String,
        mime: String,
        cookie: String?,
        st: String?,
        field: String = "pic",
        extra: Map<String, String> = emptyMap(),
    ): JSONObject = withContext(ioDispatcher) {
        val fields = linkedMapOf("st" to st.orEmpty())
        fields.putAll(extra)
        parseJsonResponse(
            PlatformHttp.postMultipart(
                url = url,
                fields = fields,
                fileField = field,
                fileName = fileName,
                fileBytes = bytes,
                fileMime = mime,
                headers = weiboHeaders(cookie, st, includeOrigin = true),
            ),
        ).first
    }

    suspend fun genVisitor(): String = withContext(ioDispatcher) {
        val resp = PlatformHttp.postForm(
            url = "https://visitor.passport.weibo.cn/visitor/genvisitor2",
            form = mapOf(
                "cb" to "visitor_gray_callback",
                "tid" to "",
                "from" to "weibo",
            ),
            headers = mapOf(
                "User-Agent" to WEB_UA,
                "Referer" to "https://m.weibo.cn/",
            ),
        )
        if (!resp.isSuccessful) throw ApiHttpException(resp.code, resp.bodyText)
        val json = resp.bodyText.substringAfter('(', "").substringBeforeLast(')', "")
        val data = runCatching { JSONObject(json).optJSONObject("data") }.getOrNull()
        val sub = data?.str("sub").orEmpty()
        val subp = data?.str("subp").orEmpty()
        if (sub.isBlank()) throw ApiException("领取访客身份失败")
        if (subp.isBlank()) "SUB=$sub" else "SUB=$sub; SUBP=$subp"
    }

    fun jsonRequestUrl(base: String, params: Map<String, String?> = emptyMap()) = url(base, params)

    fun clientUa(): String =
        "${platformDeviceManufacturer()}-${platformDeviceModel()}__weibo__11.6.3__android__android13"

    fun clientHeaderUa(): String =
        "${platformDeviceModel()}_${platformOsVersion()}_weibo_11.6.3_android"

    fun deviceName(): String = "${platformDeviceManufacturer()}-${platformDeviceModel()}"

    fun oldWm(): String = "3333_1001"

    fun androidId(): String = platformDeviceId()

    fun networkType(): String = platformNetworkType()

    suspend fun containerPost(
        path: String,
        query: Map<String, String?>,
        form: Map<String, String>,
        cookie: String? = null,
        ua: String? = null,
    ): JSONObject = containerPostFull(path, query, form, cookie, ua).first

    suspend fun containerPostFull(
        path: String,
        query: Map<String, String?>,
        form: Map<String, String>,
        cookie: String? = null,
        ua: String? = null,
    ): Pair<JSONObject, List<String>> = withContext(ioDispatcher) {
        val target = url("https://api.weibo.cn/2/$path", query)
        val headers = buildMap {
            put("Referer", "https://m.weibo.cn/")
            if (!ua.isNullOrBlank()) put("User-Agent", ua)
            if (!cookie.isNullOrBlank()) put("Cookie", cookie)
        }
        parseJsonResponse(PlatformHttp.postForm(target, form, headers))
    }

    data class ContainerResponse(
        val json: JSONObject,
        val setCookie: List<String>,
        val xValidator: String? = null,
    )

    private fun containerHeaders(headers: Map<String, String>, ua: String): Map<String, String> = buildMap {
        if (ua.isNotBlank()) put("User-Agent", ua)
        headers.forEach { (k, v) -> if (v.isNotBlank()) put(k, v) }
    }

    private fun parseContainerShare(resp: PlatformHttpResponse): ContainerResponse {
        val text = resp.bodyText
        val xValidator = resp.header("X-Validator")?.takeIf { it.isNotBlank() }
        if (text.trimStart().startsWith("{")) {
            val json = runCatching { JSONObject(text) }.getOrNull()
            if (json != null) return ContainerResponse(json, resp.setCookies, xValidator)
        }
        if (!resp.isSuccessful) throw ApiHttpException(resp.code, text)
        if (text.isBlank()) throw ApiException("空响应")
        throw ApiException("响应不是 JSON")
    }

    suspend fun containerGetShare(
        path: String,
        query: Map<String, String?>,
        headers: Map<String, String> = emptyMap(),
        ua: String = clientHeaderUa(),
    ): ContainerResponse = withContext(ioDispatcher) {
        val target = url("https://api.weibo.cn/2/$path", query)
        parseContainerShare(PlatformHttp.get(target, headers = containerHeaders(headers, ua)))
    }

    suspend fun containerGetRawShare(
        path: String,
        query: Map<String, String?>,
        headers: Map<String, String> = emptyMap(),
        ua: String = clientHeaderUa(),
    ): Pair<String, String?> = withContext(ioDispatcher) {
        val target = url("https://api.weibo.cn/2/$path", query)
        val resp = PlatformHttp.get(target, headers = containerHeaders(headers, ua))
        val xValidator = resp.header("X-Validator")?.takeIf { it.isNotBlank() }
        if (!resp.isSuccessful) throw ApiHttpException(resp.code, resp.bodyText)
        resp.bodyText to xValidator
    }

    suspend fun containerPostShare(
        path: String,
        query: Map<String, String?>,
        form: Map<String, String>,
        headers: Map<String, String> = emptyMap(),
        ua: String = clientHeaderUa(),
        multipart: Boolean = false,
    ): ContainerResponse = withContext(ioDispatcher) {
        val target = url("https://api.weibo.cn/2/$path", query)
        val hdrs = containerHeaders(headers, ua)
        val resp = if (multipart) {
            PlatformHttp.postMultipart(target, fields = form, headers = hdrs)
        } else {
            PlatformHttp.postForm(target, form = form, headers = hdrs)
        }
        parseContainerShare(resp)
    }

    suspend fun containerPostFileShare(
        path: String,
        query: Map<String, String?>,
        form: Map<String, String>,
        fileField: String,
        fileName: String,
        bytes: ByteArray,
        mimeType: String = "image/jpeg",
        headers: Map<String, String> = emptyMap(),
        ua: String = clientHeaderUa(),
    ): ContainerResponse = withContext(ioDispatcher) {
        val target = url("https://api.weibo.cn/2/$path", query)
        val resp = PlatformHttp.postMultipart(
            url = target,
            fields = form,
            fileField = fileField,
            fileName = fileName,
            fileBytes = bytes,
            fileMime = mimeType,
            headers = containerHeaders(headers, ua),
        )
        parseContainerShare(resp)
    }

    suspend fun containerUrlGetShare(
        fullUrl: String,
        query: Map<String, String?>,
        headers: Map<String, String> = emptyMap(),
        ua: String = clientHeaderUa(),
    ): ContainerResponse = withContext(ioDispatcher) {
        val target = url(fullUrl, query)
        parseContainerShare(PlatformHttp.get(target, headers = containerHeaders(headers, ua)))
    }

    suspend fun containerUrlPostBytesShare(
        fullUrl: String,
        query: Map<String, String?>,
        bytes: ByteArray,
        contentType: String = "application/octet-stream",
        headers: Map<String, String> = emptyMap(),
        ua: String = clientHeaderUa(),
    ): ContainerResponse = withContext(ioDispatcher) {
        val target = url(fullUrl, query)
        parseContainerShare(PlatformHttp.postBytes(target, bytes, contentType, containerHeaders(headers, ua)))
    }
}
