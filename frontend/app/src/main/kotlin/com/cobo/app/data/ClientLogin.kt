package com.cobo.app.data

import org.json.JSONObject

/**
 * 客户端登录：`account/login_sendcode` 发短信，`account/login` 校验并换 `gsid`。
 *
 * 与网页登录的区别是服务端会额外下发 `gsid`。cookie 过期以后拿 `gsid` 调
 * `account/getoauth` 就能换一份新的，不需要用户再输验证码——这是登录态能长期不掉的原因。
 *
 * 请求刻意保持极简：只带 `from` / `area` / `phone`（外加 `getuser/getcookie/getoauth`）。
 * 早期版本塞了完整的机型、UA、设备指纹等参数，服务端一律回 `8522 系统出了点问题`；
 * 去掉以后就正常下发短信了。
 */
object ClientLogin {

    /** 发短信按「渠道号」限流（8513 / 8517）。一条走不通就换下一条，并记住用的是哪条。 */
    private val CHANNELS = listOf("10B6395010", "1885396090", "10EA095060")

    @Volatile
    private var channel: String = CHANNELS.first()

    data class Result(
        val uid: String,
        val gsid: String,
        val sut: String,
        val cookie: String,
        val accessToken: String,
        val expires: Long,
        /** 服务端按域名下发的 cookie 表，和 Share 存进账号的形状一致。 */
        val cookies: Map<String, String> = emptyMap(),
    )

    suspend fun sendCode(phone: String, area: String) {
        var failure: Exception? = null
        for (from in CHANNELS) {
            val query = linkedMapOf<String, String?>(
                "from" to from,
                "area" to area,
                "phone" to phone,
            )
            val root = try {
                Api.containerPost("account/login_sendcode", query, emptyMap())
            } catch (e: Exception) {
                failure = e
                continue
            }
            if (root.optBoolean("sendsms", false)) {
                channel = from
                return
            }
            val errno = root.optInt("errno", 0)
            if (errno != 8513 && errno != 8517) throw ApiException(message(root))
            failure = ApiException(message(root))
        }
        throw failure ?: ApiException("验证码发送失败")
    }

    suspend fun verify(phone: String, area: String, code: String): Result {
        val query = linkedMapOf<String, String?>(
            "from" to channel,
            "area" to area,
            "phone" to phone,
            "getuser" to "1",
            "getcookie" to "1",
            "getoauth" to "1",
        )
        val form = linkedMapOf(
            "phone" to phone,
            "smscode" to code,
            "getuser" to "1",
            "getcookie" to "1",
            "getoauth" to "1",
        )
        val (root, setCookie) = Api.containerPostFull("account/login", query, form)
        return parse(root, setCookie) ?: throw ApiException(message(root))
    }

    /** 用现存 gsid 换一份新 cookie。返回的 gsid 可能已经滚动，取新的。 */
    suspend fun refresh(uid: String, gsid: String): Result {
        val query = linkedMapOf<String, String?>(
            "from" to channel,
            "uid" to uid,
            "gsid" to gsid,
            "s" to (WeiboNative.s(uid) ?: WeiboSign.s(uid)),
            "getcookie" to "1",
            "getuser" to "1",
            "getoauth" to "1",
        )
        val (root, setCookie) = Api.containerPostFull("account/getoauth", query, emptyMap())
        return parse(root, setCookie) ?: throw ApiException(message(root))
    }

    private fun parse(root: JSONObject, setCookie: List<String> = emptyList()): Result? {
        val gsid = root.optString("gsid")
        val cookies = domainMap(root.opt("cookie"), setCookie)
        val cookie = CookieVault.header(cookies, "m.weibo.cn")
        if (gsid.isBlank() && cookie.isBlank()) return null
        val oauth = root.optJSONObject("oauth2.0")
        return Result(
            uid = root.optString("uid"),
            gsid = gsid,
            sut = root.optString("sut"),
            cookie = cookie,
            accessToken = oauth?.optString("access_token").orEmpty(),
            expires = oauth?.optLong("expires") ?: 0L,
            cookies = cookies,
        )
    }

    /** `Set-Cookie: SUB=…; Path=/; Domain=.weibo.cn; HttpOnly` 只要第一个分号之前那段。 */
    private fun setCookiePairs(headers: List<String>): List<String> = headers.mapNotNull { header ->
        header.substringBefore(';').trim().takeIf { it.contains('=') }
    }

    /**
     * 登录响应的 `cookie` 与 Share 的模型对齐：`{cookie: {".weibo.cn": "SUB=…; SUBP=…"}, expire, uid}`。
     * 另外兼容两种退化形状——直接给 `{域名: "…"}` 的表，以及整串 `"SUB=…; SUBP=…"`。
     */
    private fun domainMap(raw: Any?, setCookie: List<String>): Map<String, String> {
        val out = LinkedHashMap<String, String>()

        fun put(domain: String, pairs: List<String>) {
            if (pairs.isEmpty()) return
            val lines = out[domain].orEmpty().split('\n').filter { it.isNotBlank() }.toMutableList()
            for (pair in pairs) {
                val name = pair.substringBefore('=').trim()
                lines.removeAll { it.substringBefore('=').trim().equals(name, ignoreCase = true) }
                lines.add(pair)
            }
            out[domain] = lines.joinToString("\n")
        }

        when (raw) {
            is String -> put(CookieVault.WEIBO_CN, splitPairs(raw))
            is JSONObject -> {
                val inner = raw.opt("cookie")
                val table = when (inner) {
                    is String -> return mapOf(CookieVault.WEIBO_CN to splitPairs(inner).joinToString("\n"))
                    is JSONObject -> inner
                    else -> raw
                }
                for (key in table.keys()) {
                    val value = table.optString(key)
                    if (value.isBlank()) continue
                    if (key.contains('.')) {
                        put(key, splitPairs(value))
                    } else if (value.contains('=')) {
                        put(CookieVault.WEIBO_CN, splitPairs(value))
                    } else {
                        put(CookieVault.WEIBO_CN, listOf("$key=$value"))
                    }
                }
            }
        }
        if (setCookie.isNotEmpty()) put(CookieVault.WEIBO_CN, setCookiePairs(setCookie))
        return out
    }

    private fun splitPairs(text: String): List<String> =
        text.split(';').map { it.trim() }.filter { it.contains('=') }

    private fun message(root: JSONObject): String {
        val text = root.optString("errmsg").ifBlank { root.optString("msg") }
        if (text.isNotBlank()) return text
        val errno = root.optInt("errno", 0)
        return if (errno != 0) "登录失败（$errno）" else "登录失败"
    }
}
