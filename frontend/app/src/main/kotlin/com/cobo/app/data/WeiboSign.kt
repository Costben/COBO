package com.cobo.app.data

import java.security.MessageDigest

/**
 * 容器端（`api.weibo.cn/2`）的 `s` 参数。
 *
 * 客户端里这一步在 native 库里（`newCalculateS`），这里按公开的逆向结果重写：
 * 三轮 MD5 取摘要，再做两次 SHA-512，用第二份摘要的低 4 位当索引，从第一份里挑 8 个字符。
 *
 * [SALT] 对应官方客户端初始化时传进去的那个串。换渠道号（`from`）理论上要换盐，
 * 目前用的是 11.6.3 的 `10EA095010`。
 */
object WeiboSign {
    private const val SALT = "10EA095010"
    private const val KEY = "5l0WXnhiY4pJ794KIJ7Rw5F45VXg9sjo"

    fun s(info: String): String {
        val first = sha512(md5(KEY) + md5(SALT) + md5(info)).toByteArray(Charsets.US_ASCII)
        val second = sha512(md5(SALT) + md5(info)).toByteArray(Charsets.US_ASCII)
        val out = CharArray(8)
        var index = 0
        for (i in 0 until 8) {
            index = second[15 * i + index].toInt() and 0xF
            out[i] = (first[index + 15 * i].toInt() and 0xFF).toChar()
        }
        return String(out)
    }

    internal fun md5(text: String): String = digest("MD5", text)

    internal fun sha512(text: String): String = digest("SHA-512", text)

    private fun digest(algorithm: String, text: String): String {
        val bytes = MessageDigest.getInstance(algorithm).digest(text.toByteArray(Charsets.UTF_8))
        val hex = StringBuilder(bytes.size * 2)
        for (byte in bytes) {
            val value = byte.toInt() and 0xFF
            if (value < 0x10) hex.append('0')
            hex.append(Integer.toHexString(value))
        }
        return hex.toString()
    }
}
