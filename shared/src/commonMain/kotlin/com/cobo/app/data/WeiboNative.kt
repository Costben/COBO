package com.cobo.app.data

import com.cobo.app.platform.desEcbEncryptBlock
import com.cobo.app.platform.md5Hex
import com.cobo.app.platform.sha256Bytes
import com.cobo.app.platform.sha256Hex
import com.cobo.app.platform.sha512Hex

/**
 * 原版 `libwbutil.so`（Share 3.9.6 / `10B6395010`）签名算法的跨平台纯 Kotlin + CommonCrypto 实现，
 * 与 Android 端 `WeiboApplication.newCalculateS`、`WeiboApplication.getIValue` 及
 * `NetCheckUtils.getParam`（`wb_mapi_getparam`）逐字节一致，使 iOS 完整支持容器协议认证。
 */
object WeiboNative {
    const val FROM = "10B6395010"

    private const val PIN = "5l0WXnhiY4pJ794KIJ7Rw5F45VXg9sjo"
    private const val I_INPUT = "000000000000000"

    /** `getIndex(sha512(FROM))` 对固定渠道号 `10B6395010` 恒定，启动时算一次并复用。 */
    private val sIndices: IntArray by lazy { computeSIndices(FROM) }

    /** `getIValue("000000000000000")` 对固定输入恒定（`f0cd624`）。 */
    private val cachedI: String by lazy { computeIValue(I_INPUT) }

    val available: Boolean get() = true

    fun s(uid: String): String {
        val orig = sha512Hex(PIN + uid + FROM)
        val out = CharArray(sIndices.size)
        for (i in sIndices.indices) {
            out[i] = orig[sIndices[i]]
        }
        return out.concatToString()
    }

    fun i(): String = cachedI

    /** [path] 不带 `/2/` 前缀，和接口路径写法一致。 */
    fun cum(path: String): String? = cumFrom("/2/" + path.trimStart('/'))

    /** [unsigned] 是含 query 的完整路径（`/2/xxx?a=b`），严格对齐 `wb_mapi_getparam` / `_getparam`。 */
    fun cumFrom(unsigned: String): String? {
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
