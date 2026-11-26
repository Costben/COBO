package com.cobo.app.data

import com.cobo.app.platform.md5Hex
import com.cobo.app.platform.sha512Hex

object WeiboSign {
    private const val SALT = "10EA095010"
    private const val KEY = "5l0WXnhiY4pJ794KIJ7Rw5F45VXg9sjo"

    fun s(info: String): String {
        val first = sha512(md5(KEY) + md5(SALT) + md5(info)).encodeToByteArray()
        val second = sha512(md5(SALT) + md5(info)).encodeToByteArray()
        val out = CharArray(8)
        var index = 0
        for (i in 0 until 8) {
            index = second[15 * i + index].toInt() and 0xF
            out[i] = (first[index + 15 * i].toInt() and 0xFF).toChar()
        }
        return out.concatToString()
    }

    internal fun md5(text: String): String = md5Hex(text)
    internal fun sha512(text: String): String = sha512Hex(text)
}
