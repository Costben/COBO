package com.cobo.app.platform

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.lang.ref.WeakReference
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.KeyFactory
import java.security.KeyStore
import java.security.MessageDigest
import java.security.spec.X509EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object AndroidPlatformHolder {
    @Volatile
    var appContext: Context? = null
        private set

    @Volatile
    private var currentActivityRef: WeakReference<ComponentActivity>? = null

    @Volatile
    var onRequestSystemPip: ((Float) -> Boolean)? = null

    val systemPipActiveState = mutableStateOf(false)

    private val pipListeners = CopyOnWriteListenerSet<((Boolean) -> Unit)>()

    fun initApplication(context: Context) {
        appContext = context.applicationContext ?: context
    }

    fun attachActivity(activity: ComponentActivity) {
        if (appContext == null) {
            appContext = activity.applicationContext
        }
        currentActivityRef = WeakReference(activity)
    }

    fun detachActivity(activity: ComponentActivity) {
        if (currentActivityRef?.get() === activity) {
            currentActivityRef = null
        }
    }

    fun currentActivity(): ComponentActivity? = currentActivityRef?.get()

    fun requireContext(): Context =
        appContext ?: currentActivityRef?.get()?.applicationContext
            ?: error("AndroidPlatformHolder.initApplication() has not been called")

    fun addPipListener(listener: (Boolean) -> Unit) {
        pipListeners.add(listener)
    }

    fun removePipListener(listener: (Boolean) -> Unit) {
        pipListeners.remove(listener)
    }

    fun notifySystemPipChanged(active: Boolean) {
        systemPipActiveState.value = active
        pipListeners.forEach { runCatching { it(active) } }
    }
}

private class CopyOnWriteListenerSet<T> {
    private val items = java.util.concurrent.CopyOnWriteArraySet<T>()
    fun add(item: T) { items.add(item) }
    fun remove(item: T) { items.remove(item) }
    fun forEach(block: (T) -> Unit) { items.forEach(block) }
}

actual class PlatformContext actual constructor() {
    val androidContext: Context?
        get() = AndroidPlatformHolder.appContext ?: AndroidPlatformHolder.currentActivity()?.applicationContext
}

actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun nanoTime(): Long = System.nanoTime()

actual fun uptimeMillis(): Long = runCatching { SystemClock.uptimeMillis() }.getOrElse { System.currentTimeMillis() }

actual fun randomUuid(): String = UUID.randomUUID().toString()

actual fun formatEpochMillis(epochMillis: Long, pattern: String): String =
    SimpleDateFormat(pattern, Locale.US).format(Date(epochMillis))

actual fun parseWeiboDateMillis(raw: String): Long? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    val formats = listOf(
        "EEE MMM dd HH:mm:ss Z yyyy",
        "yyyy-MM-dd'T'HH:mm:ssZ",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd HH:mm",
    )
    for (fmt in formats) {
        val parsed = runCatching { SimpleDateFormat(fmt, Locale.US).parse(trimmed)?.time }.getOrNull()
        if (parsed != null) return parsed
    }
    return null
}

actual fun epochYear(epochMillis: Long): Int =
    Calendar.getInstance().apply { timeInMillis = epochMillis }.get(Calendar.YEAR)

actual fun epochDayOfYear(epochMillis: Long): Int =
    Calendar.getInstance().apply { timeInMillis = epochMillis }.get(Calendar.DAY_OF_YEAR)

actual fun urlEncode(value: String): String =
    URLEncoder.encode(value, "UTF-8").replace("+", "%20")

actual fun urlDecode(value: String): String =
    runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)

actual fun parseUrlHost(url: String): String {
    url.toHttpUrlOrNull()?.let { return it.host.lowercase(Locale.US) }
    return runCatching { URI(url).host?.lowercase(Locale.US).orEmpty() }.getOrDefault("")
}

actual fun parseUrlPath(url: String): String {
    url.toHttpUrlOrNull()?.let { return it.encodedPath }
    return runCatching { URI(url).path.orEmpty() }.getOrDefault("")
}

actual fun parseUrlQueryParam(url: String, key: String): String? {
    url.toHttpUrlOrNull()?.let { return it.queryParameter(key) }
    val query = url.substringAfter('?', "").substringBefore('#')
    if (query.isBlank()) return null
    for (pair in query.split('&')) {
        if (pair.isEmpty()) continue
        val eq = pair.indexOf('=')
        val k = if (eq >= 0) urlDecode(pair.substring(0, eq)) else urlDecode(pair)
        if (k == key) {
            return if (eq >= 0) urlDecode(pair.substring(eq + 1)) else ""
        }
    }
    return null
}

actual fun parseUrlQueryParams(url: String): Map<String, String> {
    val out = LinkedHashMap<String, String>()
    url.toHttpUrlOrNull()?.let { parsed ->
        for (i in 0 until parsed.querySize) {
            val name = parsed.queryParameterName(i)
            val value = parsed.queryParameterValue(i) ?: ""
            out[name] = value
        }
        return out
    }
    val query = url.substringAfter('?', "").substringBefore('#')
    if (query.isBlank()) return out
    for (pair in query.split('&')) {
        if (pair.isEmpty()) continue
        val eq = pair.indexOf('=')
        if (eq > 0) {
            out[urlDecode(pair.substring(0, eq))] = urlDecode(pair.substring(eq + 1))
        } else if (eq < 0) {
            out[urlDecode(pair)] = ""
        }
    }
    return out
}

actual fun buildUrl(base: String, params: Map<String, String?>): String {
    if (params.isEmpty()) return base
    val parsed = base.toHttpUrlOrNull()
    if (parsed != null) {
        val builder = parsed.newBuilder()
        params.forEach { (k, v) -> if (v != null) builder.addQueryParameter(k, v) }
        return builder.build().toString()
    }
    val encoded = params.entries
        .filter { it.value != null }
        .joinToString("&") { (k, v) -> "${urlEncode(k)}=${urlEncode(v.orEmpty())}" }
    if (encoded.isEmpty()) return base
    val sep = if ('?' in base) "&" else "?"
    return "$base$sep$encoded"
}

actual fun resolveRelativeUrl(baseUrl: String, relative: String): String {
    if (relative.startsWith("http://", true) || relative.startsWith("https://", true) || relative.contains("://")) {
        return relative
    }
    baseUrl.toHttpUrlOrNull()?.resolve(relative)?.toString()?.let { return it }
    return runCatching { URI(baseUrl).resolve(relative).toString() }.getOrDefault(relative)
}

private val HEX_CHARS = "0123456789abcdef".toCharArray()

private fun bytesToHex(bytes: ByteArray): String {
    val out = StringBuilder(bytes.size * 2)
    for (b in bytes) {
        val v = b.toInt() and 0xFF
        out.append(HEX_CHARS[v ushr 4]).append(HEX_CHARS[v and 0x0F])
    }
    return out.toString()
}

actual fun md5Hex(bytes: ByteArray): String =
    bytesToHex(MessageDigest.getInstance("MD5").digest(bytes))

actual fun sha256Bytes(bytes: ByteArray): ByteArray =
    MessageDigest.getInstance("SHA-256").digest(bytes)

actual fun sha256Hex(bytes: ByteArray): String =
    bytesToHex(sha256Bytes(bytes))

actual fun sha512Hex(text: String): String =
    bytesToHex(MessageDigest.getInstance("SHA-512").digest(text.toByteArray(Charsets.UTF_8)))

actual fun hmacSha256Hex(key: ByteArray, data: ByteArray): String {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(key, "HmacSHA256"))
    return bytesToHex(mac.doFinal(data))
}

actual fun desEcbEncryptBlock(key8: ByteArray, block8: ByteArray): ByteArray {
    require(key8.size == 8 && block8.size == 8) { "DES-ECB single block requires 8-byte key and block" }
    val cipher = Cipher.getInstance("DES/ECB/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key8, "DES"))
    return cipher.doFinal(block8)
}

actual fun base64Encode(bytes: ByteArray): String =
    Base64.getEncoder().encodeToString(bytes)

actual fun base64Decode(text: String): ByteArray {
    val cleaned = text.filterNot { it.isWhitespace() }
    if (cleaned.isEmpty()) return ByteArray(0)
    return runCatching { Base64.getDecoder().decode(cleaned) }
        .getOrElse {
            runCatching { Base64.getUrlDecoder().decode(cleaned) }.getOrDefault(ByteArray(0))
        }
}

actual fun rsaEncryptPkcs1Base64(plain: ByteArray, publicKeyBase64: String): String {
    val der = base64Decode(publicKeyBase64)
    val pubKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(der))
    val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
    cipher.init(Cipher.ENCRYPT_MODE, pubKey)
    return base64Encode(cipher.doFinal(plain))
}

actual class PlatformFile actual constructor(path: String) {
    internal val file: File = File(path)

    actual val path: String
        get() = file.path

    actual val name: String
        get() = file.name

    actual fun child(childName: String): PlatformFile = PlatformFile(File(file, childName).path)

    actual fun mkdirs(): Boolean = file.exists() || file.mkdirs()

    actual fun exists(): Boolean = file.exists()

    actual fun delete(): Boolean = file.delete()

    actual fun deleteRecursively(): Boolean = file.deleteRecursively()

    actual fun length(): Long = if (file.exists()) file.length() else 0L

    actual fun lastModified(): Long = if (file.exists()) file.lastModified() else 0L

    actual fun listFiles(): List<PlatformFile> =
        file.listFiles()?.map { PlatformFile(it.path) }.orEmpty()

    actual fun readTextOrNull(): String? = runCatching {
        if (!file.exists()) null else file.readText(Charsets.UTF_8)
    }.getOrNull()

    actual fun writeTextAtomic(text: String): Boolean = runCatching {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile ?: File("."), "${file.name}.${System.nanoTime()}.tmp")
        tmp.writeText(text, Charsets.UTF_8)
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
        true
    }.getOrDefault(false)

    actual fun readBytesOrNull(): ByteArray? = runCatching {
        if (!file.exists()) null else file.readBytes()
    }.getOrNull()

    actual fun writeBytes(bytes: ByteArray): Boolean = runCatching {
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
        true
    }.getOrDefault(false)
}

actual fun appFilesDir(): PlatformFile {
    val ctx = AndroidPlatformHolder.appContext
    val dir = ctx?.filesDir ?: File(System.getProperty("java.io.tmpdir") ?: "/tmp", "cobo-files").apply { mkdirs() }
    return PlatformFile(dir.path)
}

actual fun appCacheDir(): PlatformFile {
    val ctx = AndroidPlatformHolder.appContext
    val dir = ctx?.cacheDir ?: File(System.getProperty("java.io.tmpdir") ?: "/tmp", "cobo-cache").apply { mkdirs() }
    return PlatformFile(dir.path)
}

private val fallbackPrefsStore = ConcurrentHashMap<String, ConcurrentHashMap<String, Any>>()

actual class KeyValueStore actual constructor(private val name: String) {
    private val prefs: SharedPreferences?
        get() = AndroidPlatformHolder.appContext?.getSharedPreferences(name, Context.MODE_PRIVATE)

    private val memory: ConcurrentHashMap<String, Any>
        get() = fallbackPrefsStore.getOrPut(name) { ConcurrentHashMap() }

    actual fun contains(key: String): Boolean =
        prefs?.contains(key) ?: memory.containsKey(key)

    actual fun getString(key: String): String? =
        prefs?.getString(key, null) ?: (memory[key] as? String)

    actual fun putString(key: String, value: String) {
        val sp = prefs
        if (sp != null) sp.edit().putString(key, value).apply() else memory[key] = value
    }

    actual fun getInt(key: String, default: Int): Int =
        prefs?.getInt(key, default) ?: (memory[key] as? Int ?: default)

    actual fun putInt(key: String, value: Int) {
        val sp = prefs
        if (sp != null) sp.edit().putInt(key, value).apply() else memory[key] = value
    }

    actual fun getLong(key: String, default: Long): Long =
        prefs?.getLong(key, default) ?: (memory[key] as? Long ?: default)

    actual fun putLong(key: String, value: Long) {
        val sp = prefs
        if (sp != null) sp.edit().putLong(key, value).apply() else memory[key] = value
    }

    actual fun getFloat(key: String, default: Float): Float =
        prefs?.getFloat(key, default) ?: (memory[key] as? Float ?: default)

    actual fun putFloat(key: String, value: Float) {
        val sp = prefs
        if (sp != null) sp.edit().putFloat(key, value).apply() else memory[key] = value
    }

    actual fun getBoolean(key: String, default: Boolean): Boolean =
        prefs?.getBoolean(key, default) ?: (memory[key] as? Boolean ?: default)

    actual fun putBoolean(key: String, value: Boolean) {
        val sp = prefs
        if (sp != null) sp.edit().putBoolean(key, value).apply() else memory[key] = value
    }

    actual fun remove(key: String) {
        val sp = prefs
        if (sp != null) sp.edit().remove(key).apply() else memory.remove(key)
    }

    actual fun clear() {
        val sp = prefs
        if (sp != null) sp.edit().clear().apply() else memory.clear()
    }

    actual fun allStrings(): Map<String, String> {
        val sp = prefs
        val out = LinkedHashMap<String, String>()
        if (sp != null) {
            sp.all.forEach { (k, v) -> if (v is String) out[k] = v }
        } else {
            memory.forEach { (k, v) -> if (v is String) out[k] = v }
        }
        return out
    }
}

actual class SynchronizedMap<K, V> actual constructor() {
    private val map = LinkedHashMap<K, V>()

    actual val size: Int
        get() = synchronized(map) { map.size }

    actual operator fun get(key: K): V? = synchronized(map) { map[key] }

    actual operator fun set(key: K, value: V) {
        synchronized(map) { map[key] = value }
    }

    actual fun putIfAbsent(key: K, value: V): V? = synchronized(map) {
        val existing = map[key]
        if (existing != null) existing else {
            map[key] = value
            null
        }
    }

    actual fun containsKey(key: K): Boolean = synchronized(map) { map.containsKey(key) }

    actual fun size(): Int = synchronized(map) { map.size }

    actual fun remove(key: K): V? = synchronized(map) { map.remove(key) }

    actual fun clear() {
        synchronized(map) { map.clear() }
    }

    actual fun toMap(): Map<K, V> = synchronized(map) { LinkedHashMap(map) }

    actual fun putAll(from: Map<K, V>) {
        synchronized(map) { map.putAll(from) }
    }
}

/**
 * 与原 Android 版 `SecretStore` 完全兼容（同 alias `cobo_local_secrets`、同 `v1:` 前缀、
 * 同 AES-256-GCM 格式），保证覆盖安装后既有登录凭据无缝解密。
 */
actual object PlatformSecretStore {
    private const val TAG = "CoboSecretStore"
    private const val PREFIX = "v1:"
    private const val KEY_ALIAS = "cobo_local_secrets"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val KEY_BITS = 256

    actual fun seal(plain: String): String = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        PREFIX + base64Encode(iv + body)
    }.onFailure {
        logWarn(TAG, "seal failed, writing plaintext", it)
    }.getOrDefault(plain)

    actual fun open(raw: String): String? {
        if (!raw.startsWith(PREFIX)) return raw
        return runCatching {
            val bytes = base64Decode(raw.substring(PREFIX.length))
            require(bytes.size > IV_BYTES) { "ciphertext too short" }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key(),
                GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES),
            )
            String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
        }.onFailure {
            logWarn(TAG, "open failed; the file is treated as unreadable", it)
        }.getOrNull()
    }

    @Synchronized
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .build(),
        )
        return generator.generateKey()
    }
}

actual fun readBundledAssetText(fileName: String): String? {
    val ctx = AndroidPlatformHolder.appContext
    if (ctx != null) {
        val assetPaths = listOf(
            fileName,
            "composeResources/com.cobo.app.generated.resources/files/$fileName",
            "files/$fileName",
        )
        for (p in assetPaths) {
            val text = runCatching { ctx.assets.open(p).bufferedReader(Charsets.UTF_8).use { it.readText() } }.getOrNull()
            if (text != null) return text
        }
    }
    val clPaths = listOf(
        "composeResources/com.cobo.app.generated.resources/files/$fileName",
        "files/$fileName",
        fileName,
    )
    val cl = AndroidPlatformHolder::class.java.classLoader
    for (p in clPaths) {
        val text = runCatching {
            cl?.getResourceAsStream(p)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        }.getOrNull()
        if (text != null) return text
    }
    return null
}

actual fun platformDeviceModel(): String = Build.MODEL.orEmpty().ifBlank { "Android" }

actual fun platformDeviceManufacturer(): String = Build.MANUFACTURER.orEmpty().ifBlank { "Android" }

actual fun platformOsVersion(): String = Build.VERSION.RELEASE.orEmpty().ifBlank { "14" }

@SuppressLint("MissingPermission")
actual fun platformNetworkType(): String {
    val ctx = AndroidPlatformHolder.appContext ?: return "wifi"
    val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return "wifi"
    val caps = runCatching { cm.getNetworkCapabilities(cm.activeNetwork) }.getOrNull() ?: return "wifi"
    return when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "4g"
        else -> "wifi"
    }
}

@SuppressLint("HardwareIds")
actual fun platformDeviceId(): String {
    val ctx = AndroidPlatformHolder.appContext
    val androidId = ctx?.let {
        runCatching { Settings.Secure.getString(it.contentResolver, Settings.Secure.ANDROID_ID) }.getOrNull()
    }
    return androidId?.ifBlank { null } ?: "cobo-android"
}

actual fun platformScreenSizePx(): Pair<Int, Int> {
    val ctx = AndroidPlatformHolder.currentActivity() ?: AndroidPlatformHolder.appContext ?: return 1080 to 2400
    val dm = ctx.resources.displayMetrics
    return dm.widthPixels to dm.heightPixels
}

@Suppress("DEPRECATION")
actual fun currentAppVersionCode(): Long {
    val ctx = AndroidPlatformHolder.appContext ?: return 10146L
    return runCatching {
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
    }.getOrDefault(10146L)
}

actual fun currentAppVersionName(): String {
    val ctx = AndroidPlatformHolder.appContext ?: return "1.2.9-pre"
    return runCatching {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName.orEmpty().ifBlank { "1.2.9-pre" }
    }.getOrDefault("1.2.9-pre")
}

actual fun logDebug(tag: String, message: String) {
    runCatching { Log.d(tag, message) }.onFailure { println("[$tag] $message") }
}

actual fun logWarn(tag: String, message: String, throwable: Throwable?) {
    runCatching {
        if (throwable != null) Log.w(tag, message, throwable) else Log.w(tag, message)
    }.onFailure {
        println("[$tag] WARN: $message ${throwable?.message.orEmpty()}")
    }
}
