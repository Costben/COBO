package com.cobo.app.platform

import coil3.ImageLoader

data class PlatformHttpResponse(
    val code: Int,
    val bodyText: String,
    val bodyBytes: ByteArray = ByteArray(0),
    val headers: Map<String, List<String>> = emptyMap(),
    val setCookies: List<String> = emptyList(),
    val location: String? = null,
) {
    val isSuccessful: Boolean get() = code in 200..299
    fun header(name: String): String? =
        headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.firstOrNull()
}

expect object PlatformHttp {
    var cookieSink: ((url: String, host: String, setCookies: List<String>, hadAuthCookie: Boolean) -> Unit)?

    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        followRedirects: Boolean = true,
    ): PlatformHttpResponse

    suspend fun postForm(
        url: String,
        form: Map<String, String>,
        headers: Map<String, String> = emptyMap(),
    ): PlatformHttpResponse

    suspend fun postJson(
        url: String,
        jsonBody: String,
        headers: Map<String, String> = emptyMap(),
    ): PlatformHttpResponse

    suspend fun postBytes(
        url: String,
        bytes: ByteArray,
        contentType: String,
        headers: Map<String, String> = emptyMap(),
    ): PlatformHttpResponse

    suspend fun postMultipart(
        url: String,
        fields: Map<String, String>,
        fileField: String? = null,
        fileName: String? = null,
        fileBytes: ByteArray? = null,
        fileMime: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): PlatformHttpResponse

    suspend fun downloadBytes(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): ByteArray?

    fun installCoilImageLoader(
        weiboHeadersProvider: (host: String) -> Map<String, String>,
    )
}
