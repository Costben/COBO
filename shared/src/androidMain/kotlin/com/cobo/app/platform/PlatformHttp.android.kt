package com.cobo.app.platform

import android.net.Uri
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.map.Mapper
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.Options
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import java.util.concurrent.TimeUnit

actual object PlatformHttp {
    @Volatile
    actual var cookieSink: ((url: String, host: String, setCookies: List<String>, hadAuthCookie: Boolean) -> Unit)? = null

    private val cookieCaptureInterceptor = Interceptor { chain ->
        val response = chain.proceed(chain.request())
        val setCookies = response.headers.values("Set-Cookie")
        if (setCookies.isNotEmpty()) {
            val reqUrl = response.request.url
            val hadAuth = response.request.header("Cookie")?.contains("SUB=") == true
            runCatching { cookieSink?.invoke(reqUrl.toString(), reqUrl.host, setCookies, hadAuth) }
        }
        response
    }

    internal val sharedOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(cookieCaptureInterceptor)
            .build()
    }

    private val noRedirectClient: OkHttpClient by lazy {
        sharedOkHttpClient.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    private fun execute(client: OkHttpClient, request: Request): PlatformHttpResponse {
        client.newCall(request).execute().use { resp: Response ->
            val rawBytes = resp.body?.bytes() ?: ByteArray(0)
            val text = rawBytes.decodeToString()
            val headersMap = LinkedHashMap<String, List<String>>()
            for (name in resp.headers.names()) {
                headersMap[name] = resp.headers.values(name)
            }
            val setCookies = resp.headers.values("Set-Cookie")
            return PlatformHttpResponse(
                code = resp.code,
                bodyText = text,
                bodyBytes = rawBytes,
                headers = headersMap,
                setCookies = setCookies,
                location = resp.header("Location"),
            )
        }
    }

    private fun Request.Builder.applyHeaders(headers: Map<String, String>): Request.Builder = apply {
        headers.forEach { (k, v) -> header(k, v) }
    }

    actual suspend fun get(
        url: String,
        headers: Map<String, String>,
        followRedirects: Boolean,
    ): PlatformHttpResponse = withContext(ioDispatcher) {
        val targetClient = if (followRedirects) sharedOkHttpClient else noRedirectClient
        val request = Request.Builder().url(url).applyHeaders(headers).get().build()
        execute(targetClient, request)
    }

    actual suspend fun postForm(
        url: String,
        form: Map<String, String>,
        headers: Map<String, String>,
    ): PlatformHttpResponse = withContext(ioDispatcher) {
        val body = FormBody.Builder().apply {
            form.forEach { (k, v) -> add(k, v) }
        }.build()
        val request = Request.Builder().url(url).applyHeaders(headers).post(body).build()
        execute(sharedOkHttpClient, request)
    }

    actual suspend fun postJson(
        url: String,
        jsonBody: String,
        headers: Map<String, String>,
    ): PlatformHttpResponse = withContext(ioDispatcher) {
        val request = Request.Builder()
            .url(url)
            .applyHeaders(headers)
            .post(jsonBody.toRequestBody(JSON_MEDIA))
            .build()
        execute(sharedOkHttpClient, request)
    }

    actual suspend fun postBytes(
        url: String,
        bytes: ByteArray,
        contentType: String,
        headers: Map<String, String>,
    ): PlatformHttpResponse = withContext(ioDispatcher) {
        val media = contentType.toMediaTypeOrNull() ?: "application/octet-stream".toMediaType()
        val request = Request.Builder()
            .url(url)
            .applyHeaders(headers)
            .post(bytes.toRequestBody(media))
            .build()
        execute(sharedOkHttpClient, request)
    }

    actual suspend fun postMultipart(
        url: String,
        fields: Map<String, String>,
        fileField: String?,
        fileName: String?,
        fileBytes: ByteArray?,
        fileMime: String?,
        headers: Map<String, String>,
    ): PlatformHttpResponse = withContext(ioDispatcher) {
        val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
        fields.forEach { (k, v) -> multipart.addFormDataPart(k, v) }
        if (!fileField.isNullOrBlank() && fileBytes != null) {
            val media = (fileMime ?: "application/octet-stream").toMediaTypeOrNull()
                ?: "application/octet-stream".toMediaType()
            multipart.addFormDataPart(
                fileField,
                fileName ?: "file",
                fileBytes.toRequestBody(media),
            )
        }
        val request = Request.Builder()
            .url(url)
            .applyHeaders(headers)
            .post(multipart.build())
            .build()
        execute(sharedOkHttpClient, request)
    }

    actual suspend fun downloadBytes(
        url: String,
        headers: Map<String, String>,
    ): ByteArray? = withContext(ioDispatcher) {
        runCatching {
            val request = Request.Builder().url(url).applyHeaders(headers).get().build()
            sharedOkHttpClient.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.bytes() else null
            }
        }.getOrNull()
    }

    @Volatile
    internal var coilOkHttpClient: OkHttpClient? = null
        private set

    actual fun installCoilImageLoader(
        weiboHeadersProvider: (host: String) -> Map<String, String>,
    ) {
        val imageClient = OkHttpClient.Builder()
            .followRedirects(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request()
                val host = req.url.host.lowercase()
                val extra = weiboHeadersProvider(host)
                if (extra.isEmpty()) {
                    chain.proceed(req)
                } else {
                    val builder = req.newBuilder()
                    extra.forEach { (k, v) ->
                        if (req.header(k) == null && v.isNotBlank()) {
                            builder.header(k, v)
                        }
                    }
                    chain.proceed(builder.build())
                }
            }
            .build()
        coilOkHttpClient = imageClient
        SingletonImageLoader.setSafe { ctx ->
            ImageLoader.Builder(ctx)
                .components {
                    add(
                        object : Mapper<PlatformUri, Any> {
                            override fun map(data: PlatformUri, options: Options): Any? {
                                PickedMediaRegistry.readBytes(data)?.let { return it }
                                val raw = data.value
                                return when {
                                    raw.startsWith("content://") -> Uri.parse(raw)
                                    raw.startsWith("file://") -> File(raw.removePrefix("file://"))
                                    raw.startsWith("/") -> File(raw)
                                    else -> raw
                                }
                            }
                        },
                    )
                    add(OkHttpNetworkFetcherFactory(callFactory = { imageClient }))
                }
                .build()
        }
    }
}
