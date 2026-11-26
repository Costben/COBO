@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.cobo.app.platform

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import platform.CoreCrypto.CCCrypt
import platform.CoreCrypto.CC_MD5
import platform.CoreCrypto.CC_MD5_DIGEST_LENGTH
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.CoreCrypto.CC_SHA512
import platform.CoreCrypto.CC_SHA512_DIGEST_LENGTH
import platform.CoreCrypto.CCHmac
import platform.CoreCrypto.kCCAlgorithmAES
import platform.CoreCrypto.kCCAlgorithmDES
import platform.CoreCrypto.kCCDecrypt
import platform.CoreCrypto.kCCEncrypt
import platform.CoreCrypto.kCCHmacAlgSHA256
import platform.CoreCrypto.kCCOptionECBMode
import platform.CoreCrypto.kCCOptionPKCS7Padding
import platform.CoreCrypto.kCCSuccess
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFErrorRefVar
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSBundle
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSCharacterSet
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSFileSize
import platform.Foundation.NSLocale
import platform.Foundation.NSLock
import platform.Foundation.NSMutableCharacterSet
import platform.Foundation.NSNumber
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLComponents
import platform.Foundation.NSURLQueryItem
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask
import platform.Foundation.URLQueryAllowedCharacterSet
import platform.Foundation.base64EncodedStringWithOptions
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.stringByAddingPercentEncodingWithAllowedCharacters
import platform.Foundation.stringByRemovingPercentEncoding
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile
import platform.Security.SecCopyErrorMessageString
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecKeyCreateEncryptedData
import platform.Security.SecKeyCreateWithData
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecDuplicateItem
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrKeyClass
import platform.Security.kSecAttrKeyClassPublic
import platform.Security.kSecAttrKeyType
import platform.Security.kSecAttrKeyTypeRSA
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecKeyAlgorithmRSAEncryptionPKCS1
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecRandomDefault
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.UIKit.UIDevice
import platform.UIKit.UIScreen
import platform.posix.memcpy
import platform.posix.size_tVar
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain

actual class PlatformContext actual constructor()

actual val ioDispatcher: CoroutineDispatcher = Dispatchers.Default

actual fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()

actual fun nanoTime(): Long = (NSProcessInfo.processInfo.systemUptime * 1_000_000_000.0).toLong()

actual fun uptimeMillis(): Long = (NSProcessInfo.processInfo.systemUptime * 1000.0).toLong()

actual fun randomUuid(): String = NSUUID().UUIDString()

actual fun formatEpochMillis(epochMillis: Long, pattern: String): String {
    val date = NSDate.dateWithTimeIntervalSince1970(epochMillis.toDouble() / 1000.0)
    val formatter = NSDateFormatter().apply {
        dateFormat = pattern
        locale = NSLocale("en_US_POSIX")
    }
    return formatter.stringFromDate(date)
}

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
        val formatter = NSDateFormatter().apply {
            dateFormat = fmt
            locale = NSLocale("en_US_POSIX")
        }
        val date = formatter.dateFromString(trimmed)
        if (date != null) return (date.timeIntervalSince1970 * 1000.0).toLong()
    }
    return null
}

actual fun epochYear(epochMillis: Long): Int {
    val date = NSDate.dateWithTimeIntervalSince1970(epochMillis.toDouble() / 1000.0)
    return NSCalendar.currentCalendar.component(NSCalendarUnitYear, fromDate = date).toInt()
}

actual fun epochDayOfYear(epochMillis: Long): Int {
    val date = NSDate.dateWithTimeIntervalSince1970(epochMillis.toDouble() / 1000.0)
    return NSCalendar.currentCalendar.ordinalityOfUnit(
        NSCalendarUnitDay,
        inUnit = NSCalendarUnitYear,
        forDate = date,
    ).toInt()
}

private val urlQueryValueAllowed: NSCharacterSet by lazy {
    val set = NSMutableCharacterSet.URLQueryAllowedCharacterSet().mutableCopy() as NSMutableCharacterSet
    set.removeCharactersInString(":/?#[]@!$&'()*+,;=")
    set
}

actual fun urlEncode(value: String): String {
    val ns = NSString.create(string = value)
    return ns.stringByAddingPercentEncodingWithAllowedCharacters(urlQueryValueAllowed) ?: value
}

actual fun urlDecode(value: String): String {
    val plusSpace = value.replace('+', ' ')
    val ns = NSString.create(string = plusSpace)
    return ns.stringByRemovingPercentEncoding ?: plusSpace
}

actual fun parseUrlHost(url: String): String =
    NSURLComponents.componentsWithString(url)?.host?.lowercase().orEmpty()

actual fun parseUrlPath(url: String): String =
    NSURLComponents.componentsWithString(url)?.path.orEmpty()

actual fun parseUrlQueryParam(url: String, key: String): String? {
    val comps = NSURLComponents.componentsWithString(url) ?: return null
    val items = comps.queryItems ?: return null
    for (raw in items) {
        val item = raw as? NSURLQueryItem ?: continue
        if (item.name == key) return item.value
    }
    return null
}

actual fun parseUrlQueryParams(url: String): Map<String, String> {
    val comps = NSURLComponents.componentsWithString(url) ?: return emptyMap()
    val out = LinkedHashMap<String, String>()
    comps.queryItems?.forEach { raw ->
        val item = raw as? NSURLQueryItem ?: return@forEach
        out[item.name] = item.value ?: ""
    }
    return out
}

actual fun buildUrl(base: String, params: Map<String, String?>): String {
    if (params.isEmpty()) return base
    val comps = NSURLComponents.componentsWithString(base)
    if (comps == null) {
        val encoded = params.entries
            .filter { it.value != null }
            .joinToString("&") { (k, v) -> "${urlEncode(k)}=${urlEncode(v.orEmpty())}" }
        if (encoded.isEmpty()) return base
        val sep = if ('?' in base) "&" else "?"
        return "$base$sep$encoded"
    }
    val existing = comps.queryItems?.filterIsInstance<NSURLQueryItem>()?.toMutableList() ?: mutableListOf()
    params.forEach { (k, v) ->
        if (v != null) {
            existing.add(NSURLQueryItem(name = k, value = v))
        }
    }
    comps.queryItems = existing
    return comps.URL?.absoluteString ?: base
}

actual fun resolveRelativeUrl(baseUrl: String, relative: String): String {
    if (relative.startsWith("http://", true) || relative.startsWith("https://", true) || relative.contains("://")) {
        return relative
    }
    val base = NSURL.URLWithString(baseUrl) ?: return relative
    return NSURL.URLWithString(relative, relativeToURL = base)?.absoluteURL?.absoluteString ?: relative
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

actual fun md5Hex(bytes: ByteArray): String {
    val digest = ByteArray(CC_MD5_DIGEST_LENGTH)
    digest.usePinned { outPinned ->
        if (bytes.isEmpty()) {
            CC_MD5(null, 0u, outPinned.addressOf(0).reinterpret())
        } else {
            bytes.usePinned { inPinned ->
                CC_MD5(inPinned.addressOf(0), bytes.size.convert(), outPinned.addressOf(0).reinterpret())
            }
        }
    }
    return bytesToHex(digest)
}

actual fun sha256Bytes(bytes: ByteArray): ByteArray {
    val digest = ByteArray(CC_SHA256_DIGEST_LENGTH)
    digest.usePinned { outPinned ->
        if (bytes.isEmpty()) {
            CC_SHA256(null, 0u, outPinned.addressOf(0).reinterpret())
        } else {
            bytes.usePinned { inPinned ->
                CC_SHA256(inPinned.addressOf(0), bytes.size.convert(), outPinned.addressOf(0).reinterpret())
            }
        }
    }
    return digest
}

actual fun sha256Hex(bytes: ByteArray): String = bytesToHex(sha256Bytes(bytes))

actual fun desEcbEncryptBlock(key8: ByteArray, block8: ByteArray): ByteArray {
    require(key8.size == 8 && block8.size == 8) { "DES-ECB single block requires 8-byte key and block" }
    val outBuf = ByteArray(8)
    memScoped {
        val outLen = alloc<size_tVar>()
        val status = outBuf.usePinned { outP ->
            key8.usePinned { kP ->
                block8.usePinned { inP ->
                    CCCrypt(
                        kCCEncrypt,
                        kCCAlgorithmDES,
                        kCCOptionECBMode,
                        kP.addressOf(0),
                        8u,
                        null,
                        inP.addressOf(0),
                        8u,
                        outP.addressOf(0),
                        8u,
                        outLen.ptr,
                    )
                }
            }
        }
        check(status == kCCSuccess && outLen.value.toInt() == 8) { "DES-ECB encrypt failed: status=$status" }
    }
    return outBuf
}

actual fun sha512Hex(text: String): String {
    val bytes = text.encodeToByteArray()
    val digest = ByteArray(CC_SHA512_DIGEST_LENGTH)
    digest.usePinned { outPinned ->
        if (bytes.isEmpty()) {
            CC_SHA512(null, 0u, outPinned.addressOf(0).reinterpret())
        } else {
            bytes.usePinned { inPinned ->
                CC_SHA512(inPinned.addressOf(0), bytes.size.convert(), outPinned.addressOf(0).reinterpret())
            }
        }
    }
    return bytesToHex(digest)
}

actual fun hmacSha256Hex(key: ByteArray, data: ByteArray): String {
    val digest = ByteArray(CC_SHA256_DIGEST_LENGTH)
    digest.usePinned { outPinned ->
        key.usePinned { kPinned ->
            if (data.isEmpty()) {
                CCHmac(
                    kCCHmacAlgSHA256,
                    if (key.isEmpty()) null else kPinned.addressOf(0),
                    key.size.convert(),
                    null,
                    0u,
                    outPinned.addressOf(0),
                )
            } else {
                data.usePinned { dPinned ->
                    CCHmac(
                        kCCHmacAlgSHA256,
                        if (key.isEmpty()) null else kPinned.addressOf(0),
                        key.size.convert(),
                        dPinned.addressOf(0),
                        data.size.convert(),
                        outPinned.addressOf(0),
                    )
                }
            }
        }
    }
    return bytesToHex(digest)
}

internal fun ByteArray.toNSData(): NSData =
    if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }

internal fun NSData.toByteArray(): ByteArray {
    val len = length.toInt()
    if (len <= 0) return ByteArray(0)
    val out = ByteArray(len)
    out.usePinned { pinned ->
        memcpy(pinned.addressOf(0), bytes, length)
    }
    return out
}

actual fun base64Encode(bytes: ByteArray): String =
    bytes.toNSData().base64EncodedStringWithOptions(0uL)

actual fun base64Decode(text: String): ByteArray {
    val cleaned = text.filterNot { it.isWhitespace() }
    val data = NSData.create(base64EncodedString = cleaned, options = 0uL) ?: return ByteArray(0)
    return data.toByteArray()
}

private fun stripX509RsaHeader(der: ByteArray): ByteArray {
    // Find BIT STRING (0x03) followed by length + 0x00 unused-bits + SEQUENCE (0x30)
    if (der.size > 26 && der[0].toInt() == 0x30) {
        for (i in 18 until minOf(der.size - 3, 32)) {
            if (der[i].toInt() == 0x03 && der[i + 1].toInt() and 0xFF == 0x81 && der[i + 3].toInt() == 0x00 && der[i + 4].toInt() == 0x30) {
                return der.copyOfRange(i + 4, der.size)
            }
            if (der[i].toInt() == 0x03 && der[i + 1].toInt() and 0xFF == 0x82 && der[i + 4].toInt() == 0x00 && der[i + 5].toInt() == 0x30) {
                return der.copyOfRange(i + 5, der.size)
            }
        }
    }
    return der
}

private inline fun <T> withCfDictionary(
    entries: List<Pair<CFTypeRef?, Any?>>,
    block: (CFDictionaryRef) -> T,
): T {
    val dict: CFMutableDictionaryRef = CFDictionaryCreateMutable(
        kCFAllocatorDefault,
        entries.size.convert(),
        kCFTypeDictionaryKeyCallBacks.ptr,
        kCFTypeDictionaryValueCallBacks.ptr,
    ) ?: error("CFDictionaryCreateMutable failed")
    try {
        for ((k, v) in entries) {
            if (k == null || v == null) continue
            when (v) {
                is Boolean -> CFDictionarySetValue(dict, k, if (v) kCFBooleanTrue else null)
                is String, is NSData, is NSNumber -> {
                    val bridged = CFBridgingRetain(v)
                    try {
                        CFDictionarySetValue(dict, k, bridged)
                    } finally {
                        if (bridged != null) CFRelease(bridged)
                    }
                }
                else -> {
                    @Suppress("UNCHECKED_CAST")
                    CFDictionarySetValue(dict, k, v as CFTypeRef)
                }
            }
        }
        return block(dict)
    } finally {
        CFRelease(dict)
    }
}

actual fun rsaEncryptPkcs1Base64(plain: ByteArray, publicKeyBase64: String): String {
    val rawKeyBytes = base64Decode(publicKeyBase64)
    val pkcs1Bytes = stripX509RsaHeader(rawKeyBytes)
    return withCfDictionary(
        listOf(
            kSecAttrKeyType to kSecAttrKeyTypeRSA,
            kSecAttrKeyClass to kSecAttrKeyClassPublic,
        ),
    ) { attrsRef ->
        memScoped {
            val errRef = alloc<CFErrorRefVar>()
            var secKey = run {
                val keyDataRef = CFBridgingRetain(pkcs1Bytes.toNSData()) as CFDataRef
                try {
                    SecKeyCreateWithData(keyDataRef, attrsRef, errRef.ptr)
                } finally {
                    CFRelease(keyDataRef)
                }
            }
            if (secKey == null) {
                val keyDataRef = CFBridgingRetain(rawKeyBytes.toNSData()) as CFDataRef
                try {
                    secKey = SecKeyCreateWithData(keyDataRef, attrsRef, errRef.ptr)
                } finally {
                    CFRelease(keyDataRef)
                }
            }
            val key = secKey ?: error("RSA public key import failed")
            try {
                val chunks = ArrayList<ByteArray>()
                var offset = 0
                while (offset < plain.size) {
                    val len = minOf(117, plain.size - offset)
                    val slice = plain.copyOfRange(offset, offset + len)
                    val plainRef = CFBridgingRetain(slice.toNSData()) as CFDataRef
                    val encryptedRef = try {
                        SecKeyCreateEncryptedData(key, kSecKeyAlgorithmRSAEncryptionPKCS1, plainRef, errRef.ptr)
                    } finally {
                        CFRelease(plainRef)
                    } ?: error("RSA encryption failed")
                    val nsEnc = CFBridgingRelease(encryptedRef) as NSData
                    chunks.add(nsEnc.toByteArray())
                    offset += len
                }
                val total = chunks.sumOf { it.size }
                val combined = ByteArray(total)
                var pos = 0
                for (c in chunks) {
                    c.copyInto(combined, pos)
                    pos += c.size
                }
                base64Encode(combined)
            } finally {
                CFRelease(key)
            }
        }
    }
}

actual class PlatformFile actual constructor(actual val path: String) {
    actual val name: String
        get() = path.substringAfterLast('/')

    actual fun child(childName: String): PlatformFile {
        val cleanBase = path.trimEnd('/')
        val cleanChild = childName.trimStart('/')
        return PlatformFile("$cleanBase/$cleanChild")
    }

    actual fun mkdirs(): Boolean =
        NSFileManager.defaultManager.createDirectoryAtPath(
            path,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )

    actual fun exists(): Boolean =
        NSFileManager.defaultManager.fileExistsAtPath(path)

    actual fun delete(): Boolean =
        NSFileManager.defaultManager.removeItemAtPath(path, error = null)

    actual fun deleteRecursively(): Boolean = delete()

    actual fun length(): Long {
        val attrs = NSFileManager.defaultManager.attributesOfItemAtPath(path, error = null) ?: return 0L
        return (attrs[NSFileSize] as? NSNumber)?.longLongValue ?: 0L
    }

    actual fun lastModified(): Long {
        val attrs = NSFileManager.defaultManager.attributesOfItemAtPath(path, error = null) ?: return 0L
        val date = attrs[NSFileModificationDate] as? NSDate ?: return 0L
        return (date.timeIntervalSince1970 * 1000.0).toLong()
    }

    actual fun listFiles(): List<PlatformFile> {
        val names = NSFileManager.defaultManager.contentsOfDirectoryAtPath(path, error = null)
            ?.filterIsInstance<String>()
            ?: return emptyList()
        return names.map { child(it) }
    }

    actual fun readTextOrNull(): String? {
        if (!exists()) return null
        return NSString.stringWithContentsOfFile(path, encoding = NSUTF8StringEncoding, error = null)
    }

    actual fun writeTextAtomic(text: String): Boolean {
        val parent = path.substringBeforeLast('/', "")
        if (parent.isNotEmpty()) PlatformFile(parent).mkdirs()
        val ns = NSString.create(string = text)
        return ns.writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
    }

    actual fun readBytesOrNull(): ByteArray? {
        if (!exists()) return null
        val data = NSData.dataWithContentsOfFile(path) ?: return null
        return data.toByteArray()
    }

    actual fun writeBytes(bytes: ByteArray): Boolean {
        val parent = path.substringBeforeLast('/', "")
        if (parent.isNotEmpty()) PlatformFile(parent).mkdirs()
        return bytes.toNSData().writeToFile(path, atomically = true)
    }
}

actual fun appFilesDir(): PlatformFile {
    val base = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true)
        .firstOrNull() as? String ?: "/tmp"
    return PlatformFile("$base/cobo").apply { mkdirs() }
}

actual fun appCacheDir(): PlatformFile {
    val base = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true)
        .firstOrNull() as? String ?: "/tmp"
    return PlatformFile("$base/cobo").apply { mkdirs() }
}

actual class KeyValueStore actual constructor(private val name: String) {
    private val defaults = NSUserDefaults.standardUserDefaults
    private val prefix = "$name."
    private fun fullKey(key: String) = "$prefix$key"

    actual fun contains(key: String): Boolean = defaults.objectForKey(fullKey(key)) != null

    actual fun getString(key: String): String? = defaults.stringForKey(fullKey(key))

    actual fun putString(key: String, value: String) {
        defaults.setObject(value, forKey = fullKey(key))
    }

    actual fun getInt(key: String, default: Int): Int {
        val k = fullKey(key)
        return if (defaults.objectForKey(k) == null) default else defaults.integerForKey(k).toInt()
    }

    actual fun putInt(key: String, value: Int) {
        defaults.setInteger(value.toLong(), forKey = fullKey(key))
    }

    actual fun getLong(key: String, default: Long): Long {
        val k = fullKey(key)
        return if (defaults.objectForKey(k) == null) default else defaults.integerForKey(k)
    }

    actual fun putLong(key: String, value: Long) {
        defaults.setInteger(value, forKey = fullKey(key))
    }

    actual fun getFloat(key: String, default: Float): Float {
        val k = fullKey(key)
        return if (defaults.objectForKey(k) == null) default else defaults.floatForKey(k)
    }

    actual fun putFloat(key: String, value: Float) {
        defaults.setFloat(value, forKey = fullKey(key))
    }

    actual fun getBoolean(key: String, default: Boolean): Boolean {
        val k = fullKey(key)
        return if (defaults.objectForKey(k) == null) default else defaults.boolForKey(k)
    }

    actual fun putBoolean(key: String, value: Boolean) {
        defaults.setBool(value, forKey = fullKey(key))
    }

    actual fun remove(key: String) {
        defaults.removeObjectForKey(fullKey(key))
    }

    actual fun clear() {
        val dict = defaults.dictionaryRepresentation()
        dict.keys.forEach { rawKey ->
            val k = rawKey as? String ?: return@forEach
            if (k.startsWith(prefix)) defaults.removeObjectForKey(k)
        }
    }

    actual fun allStrings(): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        val dict = defaults.dictionaryRepresentation()
        dict.forEach { (rawKey, rawVal) ->
            val k = rawKey as? String ?: return@forEach
            val v = rawVal as? String ?: return@forEach
            if (k.startsWith(prefix)) out[k.removePrefix(prefix)] = v
        }
        return out
    }
}

actual class SynchronizedMap<K, V> actual constructor() {
    private val lock = NSLock()
    private val map = LinkedHashMap<K, V>()

    actual val size: Int get() = size()

    actual operator fun get(key: K): V? {
        lock.lock()
        return try { map[key] } finally { lock.unlock() }
    }

    actual operator fun set(key: K, value: V) {
        lock.lock()
        try { map[key] = value } finally { lock.unlock() }
    }

    actual fun putIfAbsent(key: K, value: V): V? {
        lock.lock()
        return try {
            val prev = map[key]
            if (prev == null) map[key] = value
            prev
        } finally {
            lock.unlock()
        }
    }

    actual fun containsKey(key: K): Boolean {
        lock.lock()
        return try { map.containsKey(key) } finally { lock.unlock() }
    }

    actual fun size(): Int {
        lock.lock()
        return try { map.size } finally { lock.unlock() }
    }

    actual fun remove(key: K): V? {
        lock.lock()
        return try { map.remove(key) } finally { lock.unlock() }
    }

    actual fun clear() {
        lock.lock()
        try { map.clear() } finally { lock.unlock() }
    }

    actual fun toMap(): Map<K, V> {
        lock.lock()
        return try { map.toMap() } finally { lock.unlock() }
    }

    actual fun putAll(from: Map<K, V>) {
        lock.lock()
        try { map.putAll(from) } finally { lock.unlock() }
    }
}

actual object PlatformSecretStore {
    private const val PREFIX = "v1:"
    private const val SERVICE = "com.cobo.app.secretstore"
    private const val ACCOUNT = "cobo_local_aes_key"
    private const val KEY_BYTES = 32
    private const val IV_BYTES = 16
    private val lock = NSLock()
    private var cachedKey: ByteArray? = null

    actual fun seal(plain: String): String = runCatching {
        val key = getOrCreateKey()
        val iv = randomBytes(IV_BYTES)
        val input = plain.encodeToByteArray()
        val encrypted = aesCbc(kCCEncrypt, key, iv, input) ?: return plain
        PREFIX + base64Encode(iv + encrypted)
    }.onFailure {
        logWarn("CoboSecretStore", "seal failed, falling back to plaintext", it)
    }.getOrDefault(plain)

    actual fun open(raw: String): String? {
        if (!raw.startsWith(PREFIX)) return raw
        return runCatching {
            val payload = base64Decode(raw.substring(PREFIX.length))
            require(payload.size > IV_BYTES) { "ciphertext too short" }
            val iv = payload.copyOfRange(0, IV_BYTES)
            val body = payload.copyOfRange(IV_BYTES, payload.size)
            val key = getOrCreateKey()
            val decrypted = aesCbc(kCCDecrypt, key, iv, body) ?: return null
            decrypted.decodeToString()
        }.onFailure {
            logWarn("CoboSecretStore", "open failed", it)
        }.getOrNull()
    }

    private fun aesCbc(op: UInt, key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray? {
        val outBuf = ByteArray(data.size + 32)
        return memScoped {
            val outLen = alloc<size_tVar>()
            val status = outBuf.usePinned { outP ->
                key.usePinned { kP ->
                    iv.usePinned { ivP ->
                        if (data.isEmpty()) {
                            CCCrypt(
                                op,
                                kCCAlgorithmAES,
                                kCCOptionPKCS7Padding,
                                kP.addressOf(0),
                                key.size.convert(),
                                ivP.addressOf(0),
                                null,
                                0u,
                                outP.addressOf(0),
                                outBuf.size.convert(),
                                outLen.ptr,
                            )
                        } else {
                            data.usePinned { dP ->
                                CCCrypt(
                                    op,
                                    kCCAlgorithmAES,
                                    kCCOptionPKCS7Padding,
                                    kP.addressOf(0),
                                    key.size.convert(),
                                    ivP.addressOf(0),
                                    dP.addressOf(0),
                                    data.size.convert(),
                                    outP.addressOf(0),
                                    outBuf.size.convert(),
                                    outLen.ptr,
                                )
                            }
                        }
                    }
                }
            }
            if (status != kCCSuccess) null else outBuf.copyOf(outLen.value.toInt())
        }
    }

    private fun randomBytes(count: Int): ByteArray {
        val bytes = ByteArray(count)
        bytes.usePinned { pinned ->
            SecRandomCopyBytes(kSecRandomDefault, count.convert(), pinned.addressOf(0))
        }
        return bytes
    }

    private fun fallbackKeyFile(): PlatformFile = appFilesDir().child(".secret_key")

    private fun readFallbackKey(): ByteArray? = runCatching {
        val raw = fallbackKeyFile().readTextOrNull()?.trim().orEmpty()
        if (raw.isEmpty()) return null
        base64Decode(raw).takeIf { it.size == KEY_BYTES }
    }.getOrNull()

    private fun writeFallbackKey(key: ByteArray): Boolean = runCatching {
        fallbackKeyFile().writeTextAtomic(base64Encode(key))
    }.getOrDefault(false)

    private fun getOrCreateKey(): ByteArray {
        lock.lock()
        try {
            cachedKey?.let { return it }
            readKeychainKey()?.let {
                cachedKey = it
                return it
            }
            readFallbackKey()?.let { key ->
                writeKeychainKey(key)
                cachedKey = key
                return key
            }
            val generated = randomBytes(KEY_BYTES)
            val savedKeychain = writeKeychainKey(generated)
            val savedFallback = if (savedKeychain) true else writeFallbackKey(generated)
            check(savedKeychain || savedFallback) { "failed to persist secret key" }
            cachedKey = generated
            return generated
        } finally {
            lock.unlock()
        }
    }

    private fun readKeychainKey(): ByteArray? = withCfDictionary(
        listOf(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to SERVICE,
            kSecAttrAccount to ACCOUNT,
            kSecReturnData to true,
            kSecMatchLimit to kSecMatchLimitOne,
        ),
    ) { queryRef ->
        memScoped {
            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(queryRef, result.ptr)
            if (status != errSecSuccess) return@withCfDictionary null
            val data = CFBridgingRelease(result.value) as? NSData ?: return@withCfDictionary null
            data.toByteArray().takeIf { it.size == KEY_BYTES }
        }
    }

    private fun writeKeychainKey(key: ByteArray): Boolean = withCfDictionary(
        listOf(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to SERVICE,
            kSecAttrAccount to ACCOUNT,
            kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
            kSecValueData to key.toNSData(),
        ),
    ) { addQuery ->
        val status = SecItemAdd(addQuery, null)
        if (status == errSecSuccess) return@withCfDictionary true
        if (status == errSecDuplicateItem) {
            withCfDictionary(
                listOf(
                    kSecClass to kSecClassGenericPassword,
                    kSecAttrService to SERVICE,
                    kSecAttrAccount to ACCOUNT,
                ),
            ) { delQuery ->
                SecItemDelete(delQuery)
            }
            return@withCfDictionary SecItemAdd(addQuery, null) == errSecSuccess
        }
        logWarn("CoboSecretStore", "SecItemAdd failed with status=$status")
        false
    }
}

actual fun readBundledAssetText(fileName: String): String? {
    val resourceRoot = NSBundle.mainBundle.resourcePath ?: return null
    val candidates = listOf(
        "$resourceRoot/compose-resources/composeResources/com.cobo.app.generated.resources/files/$fileName",
        "$resourceRoot/composeResources/com.cobo.app.generated.resources/files/$fileName",
        "$resourceRoot/files/$fileName",
        "$resourceRoot/$fileName",
    )
    for (p in candidates) {
        val text = PlatformFile(p).readTextOrNull()
        if (text != null) return text
    }
    return null
}

actual fun platformDeviceModel(): String = UIDevice.currentDevice.model

actual fun platformDeviceManufacturer(): String = "Apple"

actual fun platformOsVersion(): String = UIDevice.currentDevice.systemVersion

actual fun platformNetworkType(): String = "wifi"

actual fun platformDeviceId(): String =
    UIDevice.currentDevice.identifierForVendor?.UUIDString ?: "cobo-ios"

actual fun platformScreenSizePx(): Pair<Int, Int> {
    val scale = UIScreen.mainScreen.scale
    return UIScreen.mainScreen.bounds.useContents {
        (size.width * scale).toInt() to (size.height * scale).toInt()
    }
}

actual fun currentAppVersionCode(): Long {
    val raw = NSBundle.mainBundle.infoDictionary?.get("CFBundleVersion") as? String
    return raw?.toLongOrNull() ?: 10146L
}

actual fun currentAppVersionName(): String {
    val raw = NSBundle.mainBundle.infoDictionary?.get("CFBundleShortVersionString") as? String
    return raw?.ifBlank { null } ?: "1.2.9-pre"
}

actual fun logDebug(tag: String, message: String) {
    println("[$tag] $message")
}

actual fun logWarn(tag: String, message: String, throwable: Throwable?) {
    println("[$tag] WARN: $message ${throwable?.message.orEmpty()}")
}
