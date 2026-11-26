package com.cobo.app.platform

/**
 * Multiplatform replacement for `android.net.Uri` for picked local media & URL references.
 */
data class PlatformUri(val value: String) {
    val host: String?
        get() = parseUrlHost(value).ifBlank { null }

    val scheme: String?
        get() = value.substringBefore("://", "").takeIf { it.isNotBlank() && value.contains("://") }

    val path: String?
        get() = parseUrlPath(value).ifBlank { null }

    fun getQueryParameter(key: String): String? = parseUrlQueryParam(value, key)

    override fun toString(): String = value

    companion object {
        fun parse(uriString: String): PlatformUri = PlatformUri(uriString)
    }
}

/**
 * In-memory registry mapping `cobo-picked://<id>` URIs to [PickedMedia] payloads selected by PHPicker.
 */
object PickedMediaRegistry {
    private val store = SynchronizedMap<String, PickedMedia>()

    fun register(media: PickedMedia): PlatformUri {
        val uri = "cobo-picked://${media.id}"
        store[uri] = media
        return PlatformUri(uri)
    }

    fun get(uri: PlatformUri): PickedMedia? = store[uri.value]
    fun get(uriString: String): PickedMedia? = store[uriString]

    fun readBytes(uri: PlatformUri): ByteArray? {
        store[uri.value]?.let { return it.bytes }
        if (uri.value.startsWith("file://")) {
            return PlatformFile(uri.value.removePrefix("file://")).readBytesOrNull()
        }
        if (uri.value.startsWith("/")) {
            return PlatformFile(uri.value).readBytesOrNull()
        }
        return null
    }

    fun mimeType(uri: PlatformUri): String? = store[uri.value]?.mime

    fun fileName(uri: PlatformUri): String =
        store[uri.value]?.fileName ?: uri.value.substringAfterLast('/').ifBlank { "upload.jpg" }
}

/**
 * Thread-safe set backed by [SynchronizedMap] (replaces `ConcurrentHashMap.newKeySet()`).
 */
class SynchronizedSet<T> {
    private val map = SynchronizedMap<T, Boolean>()

    fun add(element: T): Boolean = map.putIfAbsent(element, true) == null
    fun remove(element: T): Boolean = map.remove(element) != null
    operator fun contains(element: T): Boolean = map.containsKey(element)
    fun clear() = map.clear()
    fun size(): Int = map.size()
    fun toSet(): Set<T> = map.toMap().keys
}

/**
 * SharedPreferences-compatible wrapper over [KeyValueStore] so [com.cobo.app.data.Settings]
 * and other preference consumers stay source-compatible across Android & iOS.
 */
class PlatformPrefs(name: String) {
    private val kv = KeyValueStore(name)

    fun contains(key: String): Boolean = kv.contains(key)
    fun getString(key: String, defValue: String?): String? = kv.getString(key) ?: defValue
    fun getInt(key: String, defValue: Int): Int = kv.getInt(key, defValue)
    fun getLong(key: String, defValue: Long): Long = kv.getLong(key, defValue)
    fun getFloat(key: String, defValue: Float): Float = kv.getFloat(key, defValue)
    fun getBoolean(key: String, defValue: Boolean): Boolean = kv.getBoolean(key, defValue)
    fun allStrings(): Map<String, String> = kv.allStrings()

    fun edit(): Editor = Editor(kv)

    class Editor(private val kv: KeyValueStore) {
        fun putString(key: String, value: String?): Editor = apply {
            if (value == null) kv.remove(key) else kv.putString(key, value)
        }
        fun putInt(key: String, value: Int): Editor = apply { kv.putInt(key, value) }
        fun putLong(key: String, value: Long): Editor = apply { kv.putLong(key, value) }
        fun putFloat(key: String, value: Float): Editor = apply { kv.putFloat(key, value) }
        fun putBoolean(key: String, value: Boolean): Editor = apply { kv.putBoolean(key, value) }
        fun remove(key: String): Editor = apply { kv.remove(key) }
        fun clear(): Editor = apply { kv.clear() }
        fun apply() {}
        fun commit(): Boolean = true
    }
}
