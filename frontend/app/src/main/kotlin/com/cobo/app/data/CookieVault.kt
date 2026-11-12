package com.cobo.app.data

import android.webkit.CookieManager
import okhttp3.HttpUrl

/**
 * 登录态的存放方式，照 Share 的账号模型来：一张「域名 → 该域名下的 cookie」表，
 * 域名下的多条 cookie 用换行分隔（对应 Share 的 `Map<String, String>`，值里 `\n` 分段）。
 *
 * 之所以不做成「一个 Cookie 头字符串」，是因为同一个账号在 `m.weibo.cn` 和 `weibo.com`
 * 上各有一份 cookie，而且网页端会持续用 `Set-Cookie` 往回调。拆成表以后，
 * 既可以按域名拼出请求头，也可以整表灌回 WebView。
 */
object CookieVault {

    /** 网页端主域名。微博的 SUB/SUBP/ALF 都挂在这里。 */
    const val WEIBO_CN = ".weibo.cn"

    /** 桌面端主域名，部分接口只认这一份。 */
    const val WEIBO_COM = ".weibo.com"

    /** 单点登录页，`passport` 下发的 cookie 只在它自己的域上有效。 */
    const val PASSPORT_CN = "passport.weibo.cn"

    fun isWeibo(host: String): Boolean =
        host == "weibo.cn" || host.endsWith(".weibo.cn") ||
            host == "weibo.com" || host.endsWith(".weibo.com") ||
            host == "sina.cn" || host.endsWith(".sina.cn")

    /** 把 `Cookie` 头拆成「一行一条」，也就是表里每个域名的值。 */
    fun parseHeader(header: String): List<String> =
        header.split(';').map { it.trim() }.filter { it.contains('=') }

    fun joinHeader(lines: Collection<String>): String = lines.joinToString("; ")

    /** 只有一个域名的表：网页登录刚拿到的那一串先挂在 `.weibo.cn` 上。 */
    fun seed(header: String, domain: String = WEIBO_CN): Map<String, String> {
        val lines = parseHeader(header)
        if (lines.isEmpty()) return emptyMap()
        return linkedMapOf(domain to lines.joinToString("\n"))
    }

    /**
     * 把一次响应里的 `Set-Cookie` 合进表里。
     *
     * 与浏览器的规则一致：同名覆盖、空值或 `Max-Age<=0` 视为删除。域名取 `Domain` 属性，
     * 没有就落到请求的 host 上——`Set-Cookie` 不带 Domain 时本来就只对当前 host 有效。
     */
    fun absorb(
        cookies: Map<String, String>,
        url: HttpUrl?,
        setCookies: List<String>,
    ): Map<String, String> {
        if (setCookies.isEmpty()) return cookies
        val out = LinkedHashMap(cookies)
        for (raw in setCookies) {
            val parts = raw.split(';')
            val pair = parts.firstOrNull()?.trim().orEmpty()
            val index = pair.indexOf('=')
            if (index <= 0) continue
            val name = pair.substring(0, index).trim()
            val value = pair.substring(index + 1).trim()
            val domain = parts.drop(1)
                .map { it.trim() }
                .firstOrNull { it.startsWith("Domain=", ignoreCase = true) }
                ?.substringAfter('=')
                ?.trim()
                ?.trimStart('.')
                ?.let { if (it.isEmpty()) null else ".$it" }
                ?: url?.host?.let { ".$it" }
                ?: continue
            val expired = value.isEmpty() || parts.any {
                val attr = it.trim()
                (attr.startsWith("Max-Age=", ignoreCase = true) &&
                    (attr.substringAfter('=').toLongOrNull() ?: 1L) <= 0L) ||
                    (attr.startsWith("Expires=", ignoreCase = true) &&
                        attr.contains("1970"))
            }
            val lines = out[domain].orEmpty().split('\n').filter { it.isNotBlank() }.toMutableList()
            lines.removeAll { it.substringBefore('=').trim().equals(name, ignoreCase = true) }
            if (!expired) lines.add("$name=$value")
            if (lines.isEmpty()) out.remove(domain) else out[domain] = lines.joinToString("\n")
        }
        return out
    }

    /**
     * 拼出请求头。域名按「越具体越靠后」排，后出现的同名项覆盖前面的，
     * 这样 `m.weibo.cn` 自己的 cookie 会盖掉 `.weibo.cn` 里同名的。
     */
    fun header(cookies: Map<String, String>, host: String): String {
        if (cookies.isEmpty()) return ""
        val matched = cookies.keys
            .filter { matches(it, host) }
            .sortedBy { it.length }
        val merged = LinkedHashMap<String, String>()
        for (domain in matched) {
            for (line in cookies[domain].orEmpty().split('\n')) {
                val index = line.indexOf('=')
                if (index <= 0) continue
                merged[line.substring(0, index).trim()] = line.substring(index + 1).trim()
            }
        }
        return joinHeader(merged.map { "${it.key}=${it.value}" })
    }

    /**
     * 认证用请求头：先按 host 拼，缺 `SUB` 再从表里任意微博域补上。
     *
     * 网页登录时 `SUB` 有时只落在 `passport.weibo.cn` 这个域上，按 `m.weibo.cn` 拼出来
     * 就没有它。只按域名匹配的话，下一次把响应里的 cookie 收进表再重算一遍，登录态就
     * 变成一串没有 `SUB` 的空壳；而且登录完成那一刻写下的 cookie 也会缺 `SUB`。
     * 表里每个域都属于同一个账号，这里按 `SUB` / `SUBP` 兜底，不跨域名搬别的 cookie。
     */
    fun authHeader(cookies: Map<String, String>, host: String): String {
        if (cookies.isEmpty()) return ""
        val merged = LinkedHashMap<String, String>()
        fun absorbLines(domain: String) {
            for (line in cookies[domain].orEmpty().split('\n')) {
                val index = line.indexOf('=')
                if (index <= 0) continue
                merged[line.substring(0, index).trim()] = line.substring(index + 1).trim()
            }
        }
        for (domain in cookies.keys.filter { matches(it, host) }.sortedBy { it.length }) {
            absorbLines(domain)
        }
        for (name in listOf("SUB", "SUBP")) {
            if (!merged[name].isNullOrBlank()) continue
            val fallback = cookies.keys
                .filter { isWeibo(it) && matches(it, host).not() }
                .sortedByDescending { it.length }
                .firstOrNull { domain ->
                    cookies[domain].orEmpty().split('\n').any {
                        it.substringBefore('=').trim() == name && it.substringAfter('=', "").isNotBlank()
                    }
                } ?: continue
            absorbLines(fallback)
        }
        return joinHeader(merged.map { "${it.key}=${it.value}" })
    }

    private fun matches(domain: String, host: String): Boolean {
        val bare = domain.trimStart('.')
        return host.equals(bare, ignoreCase = true) || host.endsWith(domain, ignoreCase = true)
    }

    /**
     * 整表灌进 WebView，对应 Share 的 `C0353Gz.O000000o(account, true)`：
     * 先清空，再逐条 `setCookie`，最后 `flush` 落盘。必须在主线程调用。
     */
    fun inject(cookies: Map<String, String>) {
        val manager = CookieManager.getInstance()
        manager.setAcceptCookie(true)
        if (cookies.isEmpty()) {
            manager.removeExpiredCookie()
            manager.flush()
            return
        }
        manager.removeAllCookies(null)
        for ((domain, value) in cookies) {
            val lines = value.split('\n').filter { it.isNotBlank() }
            if (lines.isEmpty()) {
                manager.setCookie(domain, value)
            } else {
                for (line in lines) manager.setCookie(domain, line)
            }
        }
        manager.flush()
    }

    /**
     * 不动现有 cookie，只把表里的补进去。给每次新建 WebView 用——
     * 这里不能清空，否则会把登录页刚种下的 SSO cookie 一起抹掉。
     */
    fun plant(cookies: Map<String, String>) {
        if (cookies.isEmpty()) return
        val manager = CookieManager.getInstance()
        manager.setAcceptCookie(true)
        for ((domain, value) in cookies) {
            val lines = value.split('\n').filter { it.isNotBlank() }
            if (lines.isEmpty()) {
                manager.setCookie(domain, value)
            } else {
                for (line in lines) manager.setCookie(domain, line)
            }
        }
        manager.flush()
    }

    /**
     * 按域名把 WebView 现有的 cookie 读成表。网页登录完成时用它落一次，
     * 之后这份就是 App 的登录态真源，WebView 反过来由 [inject] 灌回去。
     */
    fun readDomains(): Map<String, String> {
        val manager = CookieManager.getInstance()
        val out = LinkedHashMap<String, String>()
        for ((url, domain) in listOf(
            "https://m.weibo.cn" to WEIBO_CN,
            "https://weibo.com" to WEIBO_COM,
            "https://passport.weibo.cn" to PASSPORT_CN,
        )) {
            val lines = parseHeader(manager.getCookie(url).orEmpty())
            if (lines.isNotEmpty()) out[domain] = lines.joinToString("\n")
        }
        return out
    }
}

/** `{"\.weibo.cn": "SUB=…\nSUBP=…"}` → 表。 */
internal fun org.json.JSONObject?.toStringMap(): Map<String, String> {
    if (this == null) return emptyMap()
    val out = LinkedHashMap<String, String>()
    for (key in keys()) {
        val value = optString(key)
        if (value.isNotBlank()) out[key] = value
    }
    return out
}

internal fun Map<String, String>.toJson(): org.json.JSONObject {
    val o = org.json.JSONObject()
    forEach { (domain, value) -> o.put(domain, value) }
    return o
}

