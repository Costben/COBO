package com.cobo.app.platform

import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.disk.DiskCache
import coil3.map.Mapper
import coil3.memory.MemoryCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.Options
import okio.Path.Companion.toPath
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.plugin
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.contentType
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile

actual object PlatformHttp {
    @Volatile
    actual var cookieSink: ((url: String, host: String, setCookies: List<String>, hadAuthCookie: Boolean) -> Unit)? = null

    private fun buildClient(followRedirects: Boolean): HttpClient = HttpClient(Darwin) {
        this.followRedirects = followRedirects
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            requestTimeoutMillis = 60_000
            socketTimeoutMillis = 25_000
        }
        engine {
            configureRequest {
                setAllowsCellularAccess(true)
            }
        }
    }

    private val client: HttpClient by lazy { buildClient(followRedirects = true) }
    private val noRedirectClient: HttpClient by lazy { buildClient(followRedirects = false) }

    private suspend fun toResponse(
        requestUrl: String,
        requestHeaders: Map<String, String>,
        resp: HttpResponse,
    ): PlatformHttpResponse {
        val rawBytes = resp.readRawBytes()
        val text = rawBytes.decodeToString()
        val allHeaders = LinkedHashMap<String, List<String>>()
        resp.headers.names().forEach { name ->
            allHeaders[name] = resp.headers.getAll(name).orEmpty()
        }
        val setCookies = resp.headers.getAll(HttpHeaders.SetCookie).orEmpty()
        if (setCookies.isNotEmpty()) {
            val host = parseUrlHost(requestUrl)
            val hadAuth = requestHeaders.entries.any { (k, v) ->
                k.equals("Cookie", ignoreCase = true) && v.contains("SUB=")
            }
            runCatching { cookieSink?.invoke(requestUrl, host, setCookies, hadAuth) }
        }
        val location = resp.headers[HttpHeaders.Location]
        return PlatformHttpResponse(
            code = resp.status.value,
            bodyText = text,
            bodyBytes = rawBytes,
            headers = allHeaders,
            setCookies = setCookies,
            location = location,
        )
    }

    actual suspend fun get(
        url: String,
        headers: Map<String, String>,
        followRedirects: Boolean,
    ): PlatformHttpResponse = withContext(ioDispatcher) {
        val targetClient = if (followRedirects) client else noRedirectClient
        val resp = targetClient.get(url) {
            headers.forEach { (k, v) -> header(k, v) }
        }
        toResponse(url, headers, resp)
    }

    actual suspend fun postForm(
        url: String,
        form: Map<String, String>,
        headers: Map<String, String>,
    ): PlatformHttpResponse = withContext(ioDispatcher) {
        val resp = client.post(url) {
            headers.forEach { (k, v) -> header(k, v) }
            setBody(
                FormDataContent(
                    Parameters.build {
                        form.forEach { (k, v) -> append(k, v) }
                    },
                ),
            )
        }
        toResponse(url, headers, resp)
    }

    actual suspend fun postJson(
        url: String,
        jsonBody: String,
        headers: Map<String, String>,
    ): PlatformHttpResponse = withContext(ioDispatcher) {
        val resp = client.post(url) {
            headers.forEach { (k, v) -> header(k, v) }
            contentType(ContentType.Application.Json)
            setBody(jsonBody)
        }
        toResponse(url, headers, resp)
    }

    actual suspend fun postBytes(
        url: String,
        bytes: ByteArray,
        contentType: String,
        headers: Map<String, String>,
    ): PlatformHttpResponse = withContext(ioDispatcher) {
        val parsedType = runCatching { ContentType.parse(contentType) }
            .getOrDefault(ContentType.Application.OctetStream)
        val resp = client.post(url) {
            headers.forEach { (k, v) -> header(k, v) }
            setBody(ByteArrayContent(bytes, parsedType))
        }
        toResponse(url, headers, resp)
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
        val resp = client.post(url) {
            headers.forEach { (k, v) -> header(k, v) }
            setBody(
                MultiPartFormDataContent(
                    formData {
                        fields.forEach { (k, v) -> append(k, v) }
                        if (!fileField.isNullOrBlank() && fileBytes != null) {
                            append(
                                key = fileField,
                                value = fileBytes,
                                headers = Headers.build {
                                    append(HttpHeaders.ContentType, fileMime ?: "application/octet-stream")
                                    append(HttpHeaders.ContentDisposition, "filename=\"${fileName ?: "file"}\"")
                                },
                            )
                        }
                    },
                ),
            )
        }
        toResponse(url, headers, resp)
    }

    actual suspend fun downloadBytes(
        url: String,
        headers: Map<String, String>,
    ): ByteArray? = withContext(ioDispatcher) {
        runCatching {
            val resp = client.get(url) {
                headers.forEach { (k, v) -> header(k, v) }
            }
            if (resp.status.value in 200..299) resp.readRawBytes() else null
        }.getOrNull()
    }

    @OptIn(ExperimentalCoilApi::class)
    actual fun installCoilImageLoader(
        weiboHeadersProvider: (host: String) -> Map<String, String>,
    ) {
        val imageHttpClient = HttpClient(Darwin) {
            install(HttpTimeout) {
                connectTimeoutMillis = 15_000
                requestTimeoutMillis = 30_000
                socketTimeoutMillis = 25_000
            }
        }
        imageHttpClient.plugin(HttpSend).intercept { request ->
            val host = request.url.host.lowercase()
            val extra = weiboHeadersProvider(host)
            extra.forEach { (k, v) ->
                if (!request.headers.contains(k) && v.isNotBlank()) {
                    request.headers.append(k, v)
                }
            }
            execute(request)
        }
        SingletonImageLoader.setSafe { ctx ->
            val cachePath = appCacheDir().child("coil_cache").apply { mkdirs() }.path.toPath()
            ImageLoader.Builder(ctx)
                .memoryCache {
                    MemoryCache.Builder()
                        .maxSizeBytes(128L * 1024L * 1024L)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(cachePath)
                        .maxSizeBytes(200L * 1024L * 1024L)
                        .build()
                }
                .components {
                    add(
                        object : Mapper<PlatformUri, ByteArray> {
                            override fun map(data: PlatformUri, options: Options): ByteArray? =
                                PickedMediaRegistry.readBytes(data)
                        },
                    )
                    add(KtorNetworkFetcherFactory(imageHttpClient))
                }
                .build()
        }
    }
}
