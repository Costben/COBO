package com.cobo.app.json

import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

private val compactJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

private val prettyJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * Drop-in KMP replacement for `org.json.JSONObject`, backed by `kotlinx.serialization.json`.
 */
class JSONObject {
    private val map = LinkedHashMap<String, Any?>()

    constructor()

    constructor(json: String) {
        val trimmed = json.trim()
        val element = compactJson.parseToJsonElement(trimmed)
        if (element !is JsonObject) {
            throw IllegalArgumentException("Value is not a JSONObject")
        }
        element.forEach { (k, v) -> map[k] = fromJsonElement(v) }
    }

    constructor(source: Map<*, *>) {
        source.forEach { (k, v) ->
            if (k != null) map[k.toString()] = wrap(v)
        }
    }

    internal constructor(element: JsonObject) {
        element.forEach { (k, v) -> map[k] = fromJsonElement(v) }
    }

    fun length(): Int = map.size

    fun has(name: String): Boolean = map.containsKey(name)

    fun isNull(name: String): Boolean {
        val value = map[name]
        return value == null || value === NULL
    }

    fun keys(): Iterator<String> = map.keys.toList().iterator()

    fun opt(name: String): Any? = map[name]

    fun get(name: String): Any {
        if (!map.containsKey(name)) throw NoSuchElementException("No value for $name")
        return map[name] ?: NULL
    }

    fun optString(name: String, fallback: String = ""): String {
        val v = map[name]
        if (v == null || v === NULL) return fallback
        return v.toString()
    }

    fun getString(name: String): String {
        val v = get(name)
        if (v === NULL) throw IllegalArgumentException("Null value for $name")
        return v.toString()
    }

    fun optInt(name: String, fallback: Int = 0): Int = toIntOrNull(map[name]) ?: fallback

    fun getInt(name: String): Int = toIntOrNull(get(name))
        ?: throw IllegalArgumentException("Value for $name is not an Int")

    fun optLong(name: String, fallback: Long = 0L): Long = toLongOrNull(map[name]) ?: fallback

    fun getLong(name: String): Long = toLongOrNull(get(name))
        ?: throw IllegalArgumentException("Value for $name is not a Long")

    fun optDouble(name: String, fallback: Double = Double.NaN): Double = toDoubleOrNull(map[name]) ?: fallback

    fun getDouble(name: String): Double = toDoubleOrNull(get(name))
        ?: throw IllegalArgumentException("Value for $name is not a Double")

    fun optBoolean(name: String, fallback: Boolean = false): Boolean = toBooleanOrNull(map[name]) ?: fallback

    fun getBoolean(name: String): Boolean = toBooleanOrNull(get(name))
        ?: throw IllegalArgumentException("Value for $name is not a Boolean")

    fun optJSONObject(name: String): JSONObject? = map[name] as? JSONObject

    fun getJSONObject(name: String): JSONObject = (get(name) as? JSONObject)
        ?: throw IllegalArgumentException("Value for $name is not a JSONObject")

    fun optJSONArray(name: String): JSONArray? = map[name] as? JSONArray

    fun getJSONArray(name: String): JSONArray = (get(name) as? JSONArray)
        ?: throw IllegalArgumentException("Value for $name is not a JSONArray")

    fun put(name: String, value: Boolean): JSONObject = apply { map[name] = value }
    fun put(name: String, value: Int): JSONObject = apply { map[name] = value }
    fun put(name: String, value: Long): JSONObject = apply { map[name] = value }
    fun put(name: String, value: Double): JSONObject = apply { map[name] = value }
    fun put(name: String, value: Float): JSONObject = apply { map[name] = value.toDouble() }
    fun put(name: String, value: Any?): JSONObject = apply {
        if (value == null) {
            map.remove(name)
        } else {
            map[name] = wrap(value)
        }
    }

    fun remove(name: String): Any? = map.remove(name)

    internal fun toJsonElement(): JsonObject = JsonObject(
        map.mapValues { (_, v) -> toElement(v) },
    )

    override fun toString(): String = compactJson.encodeToString(JsonObject.serializer(), toJsonElement())

    fun toString(indentSpaces: Int): String =
        if (indentSpaces > 0) prettyJson.encodeToString(JsonObject.serializer(), toJsonElement()) else toString()

    override fun equals(other: Any?): Boolean = other is JSONObject && map == other.map
    override fun hashCode(): Int = map.hashCode()

    companion object {
        @OptIn(ExperimentalObjCRefinement::class)
        @HiddenFromObjC
        val NULL: Any = object {
            override fun equals(other: Any?): Boolean = other == null || other === this
            override fun hashCode(): Int = 0
            override fun toString(): String = "null"
        }

        fun quote(data: String?): String =
            compactJson.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(data ?: ""))
    }
}

/**
 * Drop-in KMP replacement for `org.json.JSONArray`, backed by `kotlinx.serialization.json`.
 */
class JSONArray {
    private val list = ArrayList<Any?>()

    constructor()

    constructor(json: String) {
        val trimmed = json.trim()
        val element = compactJson.parseToJsonElement(trimmed)
        if (element !is JsonArray) {
            throw IllegalArgumentException("Value is not a JSONArray")
        }
        element.forEach { list.add(fromJsonElement(it)) }
    }

    constructor(collection: Collection<*>) {
        collection.forEach { list.add(wrap(it)) }
    }

    internal constructor(element: JsonArray) {
        element.forEach { list.add(fromJsonElement(it)) }
    }

    fun length(): Int = list.size

    fun isNull(index: Int): Boolean {
        val value = opt(index)
        return value == null || value === JSONObject.NULL
    }

    fun opt(index: Int): Any? = if (index in list.indices) list[index] else null

    fun get(index: Int): Any = opt(index) ?: throw IndexOutOfBoundsException("Index $index out of bounds")

    fun optString(index: Int, fallback: String = ""): String {
        val v = opt(index)
        if (v == null || v === JSONObject.NULL) return fallback
        return v.toString()
    }

    fun getString(index: Int): String {
        val v = get(index)
        if (v === JSONObject.NULL) throw IllegalArgumentException("Null at $index")
        return v.toString()
    }

    fun optInt(index: Int, fallback: Int = 0): Int = toIntOrNull(opt(index)) ?: fallback
    fun getInt(index: Int): Int = toIntOrNull(get(index)) ?: throw IllegalArgumentException("Not Int at $index")

    fun optLong(index: Int, fallback: Long = 0L): Long = toLongOrNull(opt(index)) ?: fallback
    fun getLong(index: Int): Long = toLongOrNull(get(index)) ?: throw IllegalArgumentException("Not Long at $index")

    fun optDouble(index: Int, fallback: Double = Double.NaN): Double = toDoubleOrNull(opt(index)) ?: fallback
    fun getDouble(index: Int): Double = toDoubleOrNull(get(index)) ?: throw IllegalArgumentException("Not Double at $index")

    fun optBoolean(index: Int, fallback: Boolean = false): Boolean = toBooleanOrNull(opt(index)) ?: fallback
    fun getBoolean(index: Int): Boolean = toBooleanOrNull(get(index)) ?: throw IllegalArgumentException("Not Boolean at $index")

    fun optJSONObject(index: Int): JSONObject? = opt(index) as? JSONObject
    fun getJSONObject(index: Int): JSONObject = (get(index) as? JSONObject)
        ?: throw IllegalArgumentException("Not JSONObject at $index")

    fun optJSONArray(index: Int): JSONArray? = opt(index) as? JSONArray
    fun getJSONArray(index: Int): JSONArray = (get(index) as? JSONArray)
        ?: throw IllegalArgumentException("Not JSONArray at $index")

    fun put(value: Boolean): JSONArray = apply { list.add(value) }
    fun put(value: Int): JSONArray = apply { list.add(value) }
    fun put(value: Long): JSONArray = apply { list.add(value) }
    fun put(value: Double): JSONArray = apply { list.add(value) }
    fun put(value: Any?): JSONArray = apply { list.add(wrap(value)) }

    fun put(index: Int, value: Any?): JSONArray = apply {
        while (list.size <= index) list.add(JSONObject.NULL)
        list[index] = wrap(value)
    }

    fun remove(index: Int): Any? = if (index in list.indices) list.removeAt(index) else null

    internal fun toJsonElement(): JsonArray = JsonArray(list.map { toElement(it) })

    override fun toString(): String = compactJson.encodeToString(JsonArray.serializer(), toJsonElement())

    fun toString(indentSpaces: Int): String =
        if (indentSpaces > 0) prettyJson.encodeToString(JsonArray.serializer(), toJsonElement()) else toString()

    override fun equals(other: Any?): Boolean = other is JSONArray && list == other.list
    override fun hashCode(): Int = list.hashCode()
}

private fun fromJsonElement(element: JsonElement): Any? = when (element) {
    is JsonNull -> JSONObject.NULL
    is JsonObject -> JSONObject(element)
    is JsonArray -> JSONArray(element)
    is JsonPrimitive -> {
        if (element.isString) {
            element.content
        } else {
            element.booleanOrNull
                ?: element.longOrNull?.let { if (it in Int.MIN_VALUE..Int.MAX_VALUE) it.toInt() else it }
                ?: element.doubleOrNull
                ?: element.contentOrNull
                ?: JSONObject.NULL
        }
    }
}

private fun wrap(value: Any?): Any? = when (value) {
    null -> JSONObject.NULL
    JSONObject.NULL, is JSONObject, is JSONArray, is String, is Boolean, is Int, is Long, is Double -> value
    is Float -> value.toDouble()
    is Short -> value.toInt()
    is Byte -> value.toInt()
    is Map<*, *> -> JSONObject(value)
    is Collection<*> -> JSONArray(value)
    else -> value.toString()
}

private fun toElement(value: Any?): JsonElement = when (value) {
    null, JSONObject.NULL -> JsonNull
    is JSONObject -> value.toJsonElement()
    is JSONArray -> value.toJsonElement()
    is Boolean -> JsonPrimitive(value)
    is Int -> JsonPrimitive(value)
    is Long -> JsonPrimitive(value)
    is Double -> JsonPrimitive(value)
    is Float -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value)
    else -> JsonPrimitive(value.toString())
}

private fun toIntOrNull(value: Any?): Int? = when (value) {
    is Int -> value
    is Long -> value.toInt()
    is Double -> if (value.isFinite()) value.toInt() else null
    is Float -> if (value.isFinite()) value.toInt() else null
    is Number -> value.toInt()
    is String -> value.trim().let { it.toIntOrNull() ?: it.toDoubleOrNull()?.toInt() }
    else -> null
}

private fun toLongOrNull(value: Any?): Long? = when (value) {
    is Long -> value
    is Int -> value.toLong()
    is Double -> if (value.isFinite()) value.toLong() else null
    is Float -> if (value.isFinite()) value.toLong() else null
    is Number -> value.toLong()
    is String -> value.trim().let { it.toLongOrNull() ?: it.toDoubleOrNull()?.toLong() }
    else -> null
}

private fun toDoubleOrNull(value: Any?): Double? = when (value) {
    is Double -> value
    is Float -> value.toDouble()
    is Number -> value.toDouble()
    is String -> value.trim().toDoubleOrNull()
    else -> null
}

private fun toBooleanOrNull(value: Any?): Boolean? = when (value) {
    is Boolean -> value
    is Number -> value.toInt() != 0
    is String -> when (value.trim().lowercase()) {
        "true", "1" -> true
        "false", "0" -> false
        else -> null
    }
    else -> null
}
