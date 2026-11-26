package com.cobo.app.data

import com.cobo.app.json.JSONObject
import com.cobo.app.platform.WebCookieBridge
import com.cobo.app.platform.parseUrlHost

object CookieVault {
    const val WEIBO_CN = ".weibo.cn"
    const val WEIBO_COM = ".weibo.com"
    const val PASSPORT_CN = "passport.weibo.cn"
    const val PASSPORT_COM = "passport.weibo.com"
    const val SINA_COM_CN = ".sina.com.cn"

    fun isWeibo(host: String): Boolean =
        host == "weibo.cn" || host.endsWith(".weibo.cn") ||
            host == "weibo.com" || host.endsWith(".weibo.com") ||
            host == "sina.cn" || host.endsWith(".sina.cn") ||
            host == "sina.com.cn" || host.endsWith(".sina.com.cn")

    fun parseHeader(header: String): List<String> =
        header.split(';').map { it.trim() }.filter { it.contains('=') }

    fun joinHeader(lines: Collection<String>): String = lines.joinToString("; ")

    fun seed(header: String, domain: String = WEIBO_CN): Map<String, String> {
        val lines = parseHeader(header)
        if (lines.isEmpty()) return emptyMap()
        return linkedMapOf(domain to lines.joinToString("\n"))
    }

    fun absorb(
        cookies: Map<String, String>,
        url: String?,
        setCookies: List<String>,
    ): Map<String, String> {
        if (setCookies.isEmpty()) return cookies
        val host = url?.let { parseUrlHost(it) }?.ifBlank { null }
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
                ?: host?.let { ".$it" }
                ?: continue
            val expired = value.isEmpty() || parts.any {
                val attr = it.trim()
                (attr.startsWith("Max-Age=", ignoreCase = true) &&
                    (attr.substringAfter('=').toLongOrNull() ?: 1L) <= 0L) ||
                    (attr.startsWith("Expires=", ignoreCase = true) && attr.contains("1970"))
            }
            val lines = out[domain].orEmpty().split('\n').filter { it.isNotBlank() }.toMutableList()
            lines.removeAll { it.substringBefore('=').trim().equals(name, ignoreCase = true) }
            if (!expired) lines.add("$name=$value")
            if (lines.isEmpty()) out.remove(domain) else out[domain] = lines.joinToString("\n")
        }
        return out
    }

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

    fun desktopAuthHeader(cookies: Map<String, String>, host: String): String {
        if (cookies.isEmpty()) return ""
        val merged = LinkedHashMap<String, String>()
        fun absorbDomain(domain: String, onlyIfMissing: Set<String>? = null) {
            for (line in cookies[domain].orEmpty().split('\n')) {
                val index = line.indexOf('=')
                if (index <= 0) continue
                val key = line.substring(0, index).trim()
                val value = line.substring(index + 1).trim()
                if (onlyIfMissing != null && (key !in onlyIfMissing || !merged[key].isNullOrBlank())) {
                    continue
                }
                merged[key] = value
            }
        }
        for (domain in cookies.keys.filter { matches(it, host) }.sortedBy { it.length }) {
            absorbDomain(domain)
        }
        val authKeys = setOf("SUB", "SUBP", "ALF")
        for (domain in cookies.keys.filter { isWeibo(it) && !matches(it, host) }.sortedByDescending { it.length }) {
            absorbDomain(domain, onlyIfMissing = authKeys)
        }
        return joinHeader(merged.map { "${it.key}=${it.value}" })
    }

    fun cookieValue(cookies: Map<String, String>, host: String, name: String): String? {
        if (cookies.isEmpty()) return null
        var found: String? = null
        for (domain in cookies.keys.filter { matches(it, host) }.sortedBy { it.length }) {
            for (line in cookies[domain].orEmpty().split('\n')) {
                val index = line.indexOf('=')
                if (index <= 0) continue
                if (line.substring(0, index).trim().equals(name, ignoreCase = true)) {
                    found = line.substring(index + 1).trim().ifBlank { null }
                }
            }
        }
        return found
    }

    private fun matches(domain: String, host: String): Boolean {
        val bare = domain.trimStart('.')
        return host.equals(bare, ignoreCase = true) || host.endsWith(domain, ignoreCase = true)
    }

    fun inject(cookies: Map<String, String>) = WebCookieBridge.inject(cookies)

    fun plant(cookies: Map<String, String>) = WebCookieBridge.plant(cookies)

    fun readDomains(): Map<String, String> = WebCookieBridge.readDomains()
}

internal fun JSONObject?.toStringMap(): Map<String, String> {
    if (this == null) return emptyMap()
    val out = LinkedHashMap<String, String>()
    for (key in keys()) {
        val value = optString(key)
        if (value.isNotBlank()) out[key] = value
    }
    return out
}

internal fun Map<String, String>.toJson(): JSONObject {
    val o = JSONObject()
    forEach { (domain, value) -> o.put(domain, value) }
    return o
}
