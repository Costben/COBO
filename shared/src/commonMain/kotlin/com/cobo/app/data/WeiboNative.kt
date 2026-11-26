package com.cobo.app.data

import com.cobo.app.platform.desEcbEncryptBlock
import com.cobo.app.platform.md5Hex
import com.cobo.app.platform.sha256Bytes
import com.cobo.app.platform.sha256Hex
import com.cobo.app.platform.sha512Hex
import kotlin.concurrent.Volatile

/**
 * 原生签名后端抽象：Android 宿主可在启动时注入原版 `libwbutil.so` 实现；
 * 未注入时（包括 iOS 与 JVM 单测）默认走纯 Kotlin 的 [WeiboNativePure]。
 */
interface WeiboNativeBackend {
    val available: Boolean
    fun s(uid: String): String?
    fun i(): String?
    fun cumFrom(unsigned: String): String?
}

/**
 * 原版 `libwbutil.so`（Share 3.9.6 / `10B6395010`）签名算法的跨平台纯 Kotlin 实现，
 * 与 Android 端 `WeiboApplication.newCalculateS`、`WeiboApplication.getIValue` 及
 * `NetCheckUtils.getParam`（`wb_mapi_getparam`）逐字节对齐。
 */
object WeiboNativePure : WeiboNativeBackend {
    const val FROM = "10B6395010"

    private const val PIN = "5l0WXnhiY4pJ794KIJ7Rw5F45VXg9sjo"
    private const val I_INPUT = "000000000000000"

    private val sIndices: IntArray by lazy { computeSIndices(FROM) }
    private val cachedI: String by lazy { computeIValue(I_INPUT) }

    override val available: Boolean get() = true

    override fun s(uid: String): String {
        val orig = sha512Hex(PIN + uid + FROM)
        val out = CharArray(sIndices.size)
        for (i in sIndices.indices) {
            out[i] = orig[sIndices[i]]
        }
        return out.concatToString()
    }

    override fun i(): String = cachedI

    fun cum(path: String): String? = cumFrom("/2/" + path.trimStart('/'))

    override fun cumFrom(unsigned: String): String? {
        if (unsigned.length < 4) return null
        val existingIdx = unsigned.indexOf("&cum=")
        if (existingIdx >= 0) {
            val end = existingIdx + 13
            if (end >= unsigned.length || unsigned[end] == '&') return null
        }
        val bytes = unsigned.encodeToByteArray()
        val h1 = sha256Bytes(bytes)
        val offset = 8 - (h1[0].toInt() and 0x7)
        if (bytes.size <= offset) return null

        val key = ByteArray(8)
        val copyLen = minOf(8, bytes.size - offset)
        bytes.copyInto(key, destinationOffset = 0, startIndex = offset, endIndex = offset + copyLen)
        if (copyLen < 8) {
            val pad = (8 - copyLen).toByte()
            for (k in copyLen until 8) key[k] = pad
        }

        // fill_cpdata: last 4 bytes + first 4 bytes
        val cpdata = ByteArray(8)
        bytes.copyInto(cpdata, destinationOffset = 0, startIndex = bytes.size - 4, endIndex = bytes.size)
        bytes.copyInto(cpdata, destinationOffset = 4, startIndex = 0, endIndex = 4)

        val encrypted = desEcbEncryptBlock(key, cpdata)
        val combined = ByteArray(40)
        h1.copyInto(combined, destinationOffset = 0, startIndex = 0, endIndex = 32)
        encrypted.copyInto(combined, destinationOffset = 32, startIndex = 0, endIndex = 8)

        val h2Upper = sha256Hex(combined).uppercase()
        return h2Upper.substring(offset, offset + 8)
    }

    private fun computeSIndices(from: String): IntArray {
        val hash = sha512Hex(from)
        val result = IntArray(8)
        var cursor = hexNibble(hash[0])
        result[0] = cursor
        for (i in 1 until 8) {
            cursor += hexNibble(hash[cursor])
            result[i] = cursor
        }
        return result
    }

    private fun hexNibble(ch: Char): Int = when (ch) {
        in '0'..'9' -> ch.code - '0'.code
        in 'A'..'F' -> ch.code - 'A'.code + 10
        else -> ch.code - 'a'.code + 10
    }

    private fun computeIValue(input: String): String {
        val first = md5Hex(input)
        val tail6 = first.substring(first.length - 6)
        val second = md5Hex(tail6 + tail6.substring(0, 4))
        return tail6 + second.last()
    }
}

object WeiboNative {
    const val FROM = WeiboNativePure.FROM

    @Volatile
    var backend: WeiboNativeBackend? = null

    private fun active(): WeiboNativeBackend = backend?.takeIf { it.available } ?: WeiboNativePure

    val available: Boolean get() = active().available

    fun s(uid: String): String? = active().s(uid)

    fun i(): String? = active().i()

    /** [path] 不带 `/2/` 前缀，和接口路径写法一致。 */
    fun cum(path: String): String? = cumFrom("/2/" + path.trimStart('/'))

    /** [unsigned] 是含 query 的完整路径（`/2/xxx?a=b`）。容器接口按这个口径算。 */
    fun cumFrom(unsigned: String): String? = active().cumFrom(unsigned)
}
