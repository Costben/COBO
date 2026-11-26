package com.cobo.app.data

import com.cobo.app.platform.desEcbEncryptBlock
import com.cobo.app.platform.md5Hex
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
 * `NetCheckUtils.getParam`（`wb_mapi_getparam`）逐字节一致。
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
        // 原版 libwbutil.so 的 _getparam 仅调用 mbedtls_sha256_init 而未调用 mbedtls_sha256_starts，
        // 因此两次 SHA-256 均使用全 0 初始向量（H0..H7 = 0）。
        val h1 = sha256ZeroIv(bytes)
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

        val h2Upper = bytesToUpperHex(sha256ZeroIv(combined))
        return h2Upper.substring(offset, offset + 8)
    }

    /** 与 `libwbutil.so` 中 `mbedtls_sha256_init`（全 0 state）+ `update` + `finish` 完全一致的 SHA-256。 */
    fun sha256ZeroIv(input: ByteArray): ByteArray {
        var h0 = 0
        var h1 = 0
        var h2 = 0
        var h3 = 0
        var h4 = 0
        var h5 = 0
        var h6 = 0
        var h7 = 0

        val bitLen = input.size.toLong() * 8L
        val paddedLen = ((input.size + 9 + 63) / 64) * 64
        val padded = ByteArray(paddedLen)
        input.copyInto(padded, 0, 0, input.size)
        padded[input.size] = 0x80.toByte()
        for (i in 0 until 8) {
            padded[paddedLen - 8 + i] = (bitLen ushr (56 - i * 8)).toByte()
        }

        val w = IntArray(64)
        var block = 0
        while (block < paddedLen) {
            for (i in 0 until 16) {
                val base = block + i * 4
                w[i] = ((padded[base].toInt() and 0xFF) shl 24) or
                    ((padded[base + 1].toInt() and 0xFF) shl 16) or
                    ((padded[base + 2].toInt() and 0xFF) shl 8) or
                    (padded[base + 3].toInt() and 0xFF)
            }
            for (i in 16 until 64) {
                val w15 = w[i - 15]
                val s0 = w15.rotateRight(7) xor w15.rotateRight(18) xor (w15 ushr 3)
                val w2 = w[i - 2]
                val s1 = w2.rotateRight(17) xor w2.rotateRight(19) xor (w2 ushr 10)
                w[i] = w[i - 16] + s0 + w[i - 7] + s1
            }

            var a = h0
            var b = h1
            var c = h2
            var d = h3
            var e = h4
            var f = h5
            var g = h6
            var h = h7

            for (i in 0 until 64) {
                val s1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
                val ch = (e and f) xor (e.inv() and g)
                val t1 = h + s1 + ch + SHA256_K[i] + w[i]
                val s0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
                val maj = (a and b) xor (a and c) xor (b and c)
                val t2 = s0 + maj

                h = g
                g = f
                f = e
                e = d + t1
                d = c
                c = b
                b = a
                a = t1 + t2
            }

            h0 += a
            h1 += b
            h2 += c
            h3 += d
            h4 += e
            h5 += f
            h6 += g
            h7 += h
            block += 64
        }

        val state = intArrayOf(h0, h1, h2, h3, h4, h5, h6, h7)
        val out = ByteArray(32)
        for (i in 0 until 8) {
            val v = state[i]
            out[i * 4] = (v ushr 24).toByte()
            out[i * 4 + 1] = (v ushr 16).toByte()
            out[i * 4 + 2] = (v ushr 8).toByte()
            out[i * 4 + 3] = v.toByte()
        }
        return out
    }

    private val UPPER_HEX = "0123456789ABCDEF".toCharArray()

    private fun bytesToUpperHex(bytes: ByteArray): String {
        val out = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            out[i * 2] = UPPER_HEX[v ushr 4]
            out[i * 2 + 1] = UPPER_HEX[v and 0x0F]
        }
        return out.concatToString()
    }

    private val SHA256_K = intArrayOf(
        0x428a2f98, 0x71374491, 0xb5c0fbcf.toInt(), 0xe9b5dba5.toInt(),
        0x3956c25b, 0x59f111f1, 0x923f82a4.toInt(), 0xab1c5ed5.toInt(),
        0xd807aa98.toInt(), 0x12835b01, 0x243185be, 0x550c7dc3,
        0x72be5d74, 0x80deb1fe.toInt(), 0x9bdc06a7.toInt(), 0xc19bf174.toInt(),
        0xe49b69c1.toInt(), 0xefbe4786.toInt(), 0x0fc19dc6, 0x240ca1cc,
        0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        0x983e5152.toInt(), 0xa831c66d.toInt(), 0xb00327c8.toInt(), 0xbf597fc7.toInt(),
        0xc6e00bf3.toInt(), 0xd5a79147.toInt(), 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
        0x650a7354, 0x766a0abb, 0x81c2c92e.toInt(), 0x92722c85.toInt(),
        0xa2bfe8a1.toInt(), 0xa81a664b.toInt(), 0xc24b8b70.toInt(), 0xc76c51a3.toInt(),
        0xd192e819.toInt(), 0xd6990624.toInt(), 0xf40e3585.toInt(), 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5,
        0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, 0x84c87814.toInt(), 0x8cc70208.toInt(),
        0x90befffa.toInt(), 0xa4506ceb.toInt(), 0xbef9a3f7.toInt(), 0xc67178f2.toInt(),
    )

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
