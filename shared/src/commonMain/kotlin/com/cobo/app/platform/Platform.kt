package com.cobo.app.platform

import kotlinx.coroutines.CoroutineDispatcher

expect class PlatformContext()

expect val ioDispatcher: CoroutineDispatcher

expect fun currentTimeMillis(): Long
expect fun nanoTime(): Long
expect fun uptimeMillis(): Long
expect fun randomUuid(): String

expect fun formatEpochMillis(epochMillis: Long, pattern: String): String
expect fun parseWeiboDateMillis(raw: String): Long?
expect fun epochYear(epochMillis: Long): Int
expect fun epochDayOfYear(epochMillis: Long): Int

expect fun urlEncode(value: String): String
expect fun urlDecode(value: String): String
expect fun parseUrlHost(url: String): String
expect fun parseUrlPath(url: String): String
expect fun parseUrlQueryParam(url: String, key: String): String?
expect fun parseUrlQueryParams(url: String): Map<String, String>
expect fun buildUrl(base: String, params: Map<String, String?> = emptyMap()): String
expect fun resolveRelativeUrl(baseUrl: String, relative: String): String

expect fun md5Hex(bytes: ByteArray): String
expect fun sha256Bytes(bytes: ByteArray): ByteArray
expect fun sha256Hex(bytes: ByteArray): String
expect fun sha512Hex(text: String): String
expect fun hmacSha256Hex(key: ByteArray, data: ByteArray): String
expect fun desEcbEncryptBlock(key8: ByteArray, block8: ByteArray): ByteArray
expect fun base64Encode(bytes: ByteArray): String
expect fun base64Decode(text: String): ByteArray
expect fun rsaEncryptPkcs1Base64(plain: ByteArray, publicKeyBase64: String): String

fun md5Hex(text: String): String = md5Hex(text.encodeToByteArray())
fun sha256Hex(text: String): String = sha256Hex(text.encodeToByteArray())

/** Pure Kotlin IEEE 802.3 CRC32 (matches java.util.zip.CRC32). */
fun crc32Value(bytes: ByteArray): Long {
    var crc = 0xFFFFFFFFu
    for (b in bytes) {
        crc = crc xor (b.toUInt() and 0xFFu)
        repeat(8) {
            crc = if ((crc and 1u) != 0u) (crc shr 1) xor 0xEDB88320u else crc shr 1
        }
    }
    return (crc xor 0xFFFFFFFFu).toLong() and 0xFFFFFFFFL
}

fun formatDecimal(value: Double, decimals: Int): String {
    if (!value.isFinite()) return "0"
    if (decimals <= 0) return kotlin.math.round(value).toLong().toString()
    var factor = 1L
    repeat(decimals) { factor *= 10L }
    val scaled = kotlin.math.round(value * factor).toLong()
    val sign = if (scaled < 0) "-" else ""
    val absScaled = kotlin.math.abs(scaled)
    val whole = absScaled / factor
    val frac = (absScaled % factor).toString().padStart(decimals, '0')
    return "$sign$whole.$frac"
}

fun formatHex6(argb: Int): String {
    val hex = (argb and 0xFFFFFF).toString(16).uppercase().padStart(6, '0')
    return "#$hex"
}

/** Pure Kotlin HTML entity decoder (covers named & numeric entities used in Weibo HTML). */
fun decodeHtmlEntities(input: String): String {
    if ('&' !in input) return input
    val sb = StringBuilder(input.length)
    var i = 0
    while (i < input.length) {
        val ch = input[i]
        if (ch == '&') {
            val semi = input.indexOf(';', i + 1)
            if (semi > i + 1 && semi - i <= 12) {
                val entity = input.substring(i + 1, semi)
                val decoded = when {
                    entity.startsWith("#x", ignoreCase = true) ->
                        entity.substring(2).toIntOrNull(16)?.let { codePointToString(it) }
                    entity.startsWith("#") ->
                        entity.substring(1).toIntOrNull()?.let { codePointToString(it) }
                    else -> HTML_ENTITIES[entity]
                }
                if (decoded != null) {
                    sb.append(decoded)
                    i = semi + 1
                    continue
                }
            }
        }
        sb.append(ch)
        i++
    }
    return sb.toString()
}

private fun codePointToString(cp: Int): String? {
    if (cp <= 0 || cp > 0x10FFFF) return null
    return if (cp <= 0xFFFF) {
        cp.toChar().toString()
    } else {
        val v = cp - 0x10000
        val high = (0xD800 + (v shr 10)).toChar()
        val low = (0xDC00 + (v and 0x3FF)).toChar()
        "$high$low"
    }
}

private val HTML_ENTITIES = mapOf(
    "amp" to "&",
    "lt" to "<",
    "gt" to ">",
    "quot" to "\"",
    "apos" to "'",
    "nbsp" to " ",
    "ensp" to " ",
    "emsp" to " ",
    "thinsp" to " ",
    "middot" to "·",
    "bull" to "•",
    "hellip" to "…",
    "mdash" to "—",
    "ndash" to "–",
    "laquo" to "«",
    "raquo" to "»",
    "ldquo" to "“",
    "rdquo" to "”",
    "lsquo" to "‘",
    "rsquo" to "’",
    "copy" to "©",
    "reg" to "®",
    "trade" to "™",
    "times" to "×",
    "divide" to "÷",
    "yen" to "¥",
    "euro" to "€",
    "pound" to "£",
    "deg" to "°",
    "hearts" to "♥",
)

/** Strip HTML tags (preserving `<br>` as newline) and decode HTML entities. */
fun stripHtmlToPlainText(html: String): String {
    if (html.isEmpty()) return ""
    val withNewlines = html
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("</p\\s*>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]+>"), "")
    return decodeHtmlEntities(withNewlines)
}

expect class PlatformFile(path: String) {
    val path: String
    val name: String
    fun child(childName: String): PlatformFile
    fun mkdirs(): Boolean
    fun exists(): Boolean
    fun delete(): Boolean
    fun deleteRecursively(): Boolean
    fun length(): Long
    fun lastModified(): Long
    fun listFiles(): List<PlatformFile>
    fun readTextOrNull(): String?
    fun writeTextAtomic(text: String): Boolean
    fun readBytesOrNull(): ByteArray?
    fun writeBytes(bytes: ByteArray): Boolean
}

expect fun appFilesDir(): PlatformFile
expect fun appCacheDir(): PlatformFile

expect class KeyValueStore(name: String) {
    fun contains(key: String): Boolean
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun getInt(key: String, default: Int): Int
    fun putInt(key: String, value: Int)
    fun getLong(key: String, default: Long): Long
    fun putLong(key: String, value: Long)
    fun getFloat(key: String, default: Float): Float
    fun putFloat(key: String, value: Float)
    fun getBoolean(key: String, default: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun remove(key: String)
    fun clear()
    fun allStrings(): Map<String, String>
}

expect class SynchronizedMap<K, V>() {
    val size: Int
    operator fun get(key: K): V?
    operator fun set(key: K, value: V)
    fun putIfAbsent(key: K, value: V): V?
    fun containsKey(key: K): Boolean
    fun size(): Int
    fun remove(key: K): V?
    fun clear()
    fun toMap(): Map<K, V>
    fun putAll(from: Map<K, V>)
}

fun <K, V> MutableMap<K, V>.putIfAbsent(key: K, value: V): V? {
    val existing = this[key]
    if (existing != null) return existing
    this[key] = value
    return null
}

expect object PlatformSecretStore {
    fun seal(plain: String): String
    fun open(raw: String): String?
}

expect fun readBundledAssetText(fileName: String): String?

expect fun platformDeviceModel(): String
expect fun platformDeviceManufacturer(): String
expect fun platformOsVersion(): String
expect fun platformNetworkType(): String
expect fun platformDeviceId(): String
expect fun platformScreenSizePx(): Pair<Int, Int>
expect fun currentAppVersionCode(): Long
expect fun currentAppVersionName(): String
expect fun logDebug(tag: String, message: String)
expect fun logWarn(tag: String, message: String, throwable: Throwable? = null)
