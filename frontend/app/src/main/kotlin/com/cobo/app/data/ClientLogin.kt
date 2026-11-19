package com.cobo.app.data

import android.os.Build
import com.cobo.app.App
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher

/**
 * Share 3.9.6 客户端登录与长期续签实现：
 * - 第一步：`u` + RSA(`p`) + native `s` 调 `POST /2/account/login`；
 * - 若触发登录保护（`-1007`）：调 `account/login_smsverify` 下发一次短信，再用 `smsverifycode` 复提 `account/login`；
 * - 也兼容 `-1005` 图形验证码与 `errurl`/`error_url`（`loginWithALT` JSBridge -> `alt`），以及直接手机号短信登录；
 * - 成功后持久化 `gsid` 与四域 Cookie 表，后台每 6 小时静默换新 Cookie。
 */
object ClientLogin {

    const val FROM = WeiboNative.FROM
    const val FALLBACK_SMS_FROM = "1885396090"
    private val SEND_CHANNELS = listOf(FROM, FALLBACK_SMS_FROM, "10EA095060")
    private val REFRESH_CHANNELS = listOf(FROM, FALLBACK_SMS_FROM)
    private val RETRYABLE_CHANNEL_ERRNOS = setOf(8513, 8517, 8599, -105)

    private const val WM = "2468_1001"
    private const val V_P = "89"
    private const val WB_VERSION = "5005"
    private const val SKIN = "default"
    private const val V_F = "2"
    private const val SHARE_APPKEY = "7501641714"
    private const val SHARE_KEY_HASH = "18da2bf10352443a00a5e046d9fca6bd"

    internal const val RSA_LOGIN_PUBKEY =
        "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQC46y69c1rmEk6btBLCPgxJ" +
            "kCxdDcAH9k7kBLffgG1KWqUErjdv+aMkEZmBaprEW846YEwBn60gyBih3KU518fL3F+sv2b6xEeOxgjWO+NPgSWmT3q1up95HmmLHlgVwqTKqRUHd8+Tr43D5h+J8T69etX0YNdT5ACvm+Ar0HdarwIDAQAB"

    private const val RSA_AID_PUBKEY =
        "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDHHM0Fi2Z6+QYKXqFUX2Cy6AaW" +
            "q3cPi+GSn9oeAwQbPZR75JB7Netm0HtBVVbtPhzT7UO2p1JhFUKWqrqoYuAjkgMVPmA0sFrQohns5EE44Y86XQopD4ZO+dE5KjUZFE6vrPO3rWW3np2BqlgKpjnYZri6TJApmIpGcQg9/G/3zQIDAQAB"

    @Volatile
    private var smsChannel: String = FALLBACK_SMS_FROM

    @Volatile
    private var xValidator: String = ""

    data class Result(
        val uid: String,
        val gsid: String,
        val sut: String,
        val cookie: String,
        val accessToken: String,
        val expires: Long,
        /** 服务端按域名下发的 cookie 表，与 Share 账号表对齐。 */
        val cookies: Map<String, String> = emptyMap(),
    )

    data class VerifyPhone(
        val phone: String,
        val code: String,
    )

    data class CaptchaChallenge(
        val picUrl: String,
        val cpt: String,
        val entry: String,
        val message: String,
    )

    data class SafetyChallenge(
        val retcode: String,
        val phones: List<VerifyPhone>,
        val message: String,
    )

    data class SafetySmsSent(
        val number: String,
        val message: String,
    )

    sealed interface Outcome {
        data class Success(val result: Result) : Outcome
        data class Captcha(val challenge: CaptchaChallenge) : Outcome
        data class SafetyVerify(val challenge: SafetyChallenge) : Outcome
        data class WebVerify(val url: String, val message: String) : Outcome
    }

    internal sealed interface ParsedResponse {
        data class Done(val result: Result) : ParsedResponse
        data class Challenge(val outcome: Outcome) : ParsedResponse
    }

    /** 账号（手机号/邮箱）+ 密码首步登录，完整复刻 Share `WeiboWebAuthorizeActivity`。 */
    suspend fun loginWithPassword(
        account: String,
        password: String,
        cpt: String? = null,
        cptCode: String? = null,
        alt: String? = null,
    ): Outcome {
        val aid = ensureAid()
        val query = linkedMapOf<String, String?>()
        if (!alt.isNullOrBlank()) {
            query["alt"] = alt
        } else {
            query["s"] = signInfo(account + password)
            query["u"] = account
            query["p"] = encryptPassword(password)
        }
        val iVal = iValue()
        if (iVal.isNotBlank()) query["i"] = iVal
        if (!cpt.isNullOrBlank() && !cptCode.isNullOrBlank()) {
            query["cpt"] = cpt
            query["cptcode"] = cptCode
        }
        putCommonParams(query, FROM)
        val resp = signedPost("account/login", query, commonFormParams(), aid)
        val parsed = parseResponse(resp.json, resp.setCookie)
        return resolveOutcome(parsed)
    }

    /** 登录保护（`-1007`）发送短信验证码，对应 Share `WeiboSafetyVerifyActivity` -> `account/login_smsverify`。 */
    suspend fun sendSafetyVerifyCode(phone: VerifyPhone, retcode: String): SafetySmsSent {
        val aid = ensureAid()
        val i = iValue()
        val query = linkedMapOf<String, String?>(
            "c" to "android",
            "from" to FROM,
            "i" to i,
            "phone" to phone.phone,
            "retcode" to retcode,
            "code" to phone.code,
        )
        val form = linkedMapOf(
            "phone" to phone.phone,
            "retcode" to retcode,
            "code" to phone.code,
        )
        val resp = signedPost("account/login_smsverify", query, form, aid)
        val errno = errorCode(resp.json)
        if (errno != null && errno != "0") {
            throw ApiException(errorMessage(resp.json), errno)
        }
        val number = resp.json.optString("number").ifBlank { phone.phone }
        val msg = resp.json.optString("msg").ifBlank { "验证码已发送" }
        return SafetySmsSent(number = number, message = msg)
    }

    /** 登录保护（`-1007`）回填短信验证码，对应 Share `WeiboSmsVerifyActivity`。 */
    suspend fun submitSafetyVerifyCode(
        account: String,
        password: String,
        phone: VerifyPhone,
        retcode: String,
        number: String,
        smsCode: String,
    ): Outcome {
        val aid = ensureAid()
        val query = linkedMapOf<String, String?>(
            "c" to "android",
            "from" to FROM,
            "i" to iValue(),
            "u" to account,
            "p" to encryptPassword(password),
            "s" to signInfo(account + password),
            "phone" to phone.phone,
            "number" to number,
            "code" to phone.code,
            "retcode" to retcode,
            "smsverifycode" to smsCode,
        )
        putCommonParams(query, FROM)
        val resp = signedPost("account/login", query, commonFormParams(), aid)
        val parsed = parseResponse(resp.json, resp.setCookie)
        return resolveOutcome(parsed)
    }

    /** 兜底通道：直接发手机号短信验证码（不输密码）。 */
    suspend fun sendCode(phone: String, area: String = "") {
        val aid = ensureAid()
        var failure: Exception? = null
        val reqPhone = if (area.isBlank() || area == "86" || area == "0086") phone else "$area$phone"
        val reqArea = if (area == "86") "" else area
        for (from in SEND_CHANNELS) {
            val query = linkedMapOf<String, String?>(
                "c" to "android",
                "from" to from,
            )
            putCommonParams(query, from)
            query["phone"] = reqPhone
            query["area"] = reqArea
            query["getuser"] = "1"
            query["getoauth"] = "1"
            query["getcookie"] = "1"
            val form = linkedMapOf(
                "aid" to aid,
                "pwd" to "",
                "flag" to "1",
                "phone" to reqPhone,
            )
            val resp = try {
                signedPost("account/login_sendcode", query, form, aid)
            } catch (e: Exception) {
                failure = e
                continue
            }
            val root = resp.json
            val sendsms = root.optBoolean("sendsms", false)
            val errno = errorCode(root)?.toIntOrNull() ?: 0
            if (sendsms) {
                smsChannel = from
                return
            }
            if (errno !in RETRYABLE_CHANNEL_ERRNOS) throw ApiException(errorMessage(root), errno.toString())
            failure = ApiException(errorMessage(root), errno.toString())
        }
        throw failure ?: ApiException("验证码发送失败")
    }

    /** 兜底通道：直接用短信验证码完成登录。 */
    suspend fun verify(phone: String, area: String = "", code: String): Result {
        val aid = ensureAid()
        val reqPhone = if (area.isBlank() || area == "86" || area == "0086") phone else "$area$phone"
        val reqArea = if (area == "86") "" else area
        val query = linkedMapOf<String, String?>(
            "c" to "android",
            "from" to smsChannel,
            "i" to iValue(),
            "phone" to reqPhone,
            "number" to reqPhone,
            "code" to reqArea,
            "smscode" to code,
        )
        putCommonParams(query, smsChannel)
        val resp = signedPost("account/login", query, commonFormParams(), aid)
        val parsed = parseResponse(resp.json, resp.setCookie)
        return when (val out = resolveOutcome(parsed)) {
            is Outcome.Success -> out.result
            is Outcome.Captcha -> throw ApiException(out.challenge.message)
            is Outcome.SafetyVerify -> throw ApiException(out.challenge.message)
            is Outcome.WebVerify -> throw ApiException(out.message)
        }
    }

    /**
     * 用现存 `gsid` 换一份全新四域 Cookie（对应 Share `C0484Lf.O00000oO()`）。
     */
    suspend fun refresh(
        uid: String,
        gsid: String,
        channels: List<String> = REFRESH_CHANNELS,
    ): Result {
        val aid = ensureAid()
        var failure: Exception? = null
        for (from in channels) {
            val s = if (from == FROM) signInfo(uid) else WeiboSign.s(uid)
            val query = linkedMapOf<String, String?>(
                "c" to "android",
                "s" to s,
                "i" to iValue(),
                "gsid" to gsid,
                "uid" to uid,
            )
            putCommonParams(query, from)
            val resp = signedPost("account/login", query, commonFormParams(), aid)
            val root = resp.json
            val errno = errorCode(root)?.toIntOrNull() ?: 0
            val parsed = if (errno == 0) parse(root, resp.setCookie) else null
            if (errno != 0) {
                val err = ApiException(errorMessage(root), errno.toString())
                if (errno == -105 || errno == 8599) {
                    failure = err
                    continue
                }
                throw err
            }
            if (parsed != null && parsed.cookie.isNotBlank()) return parsed
            failure = ApiException("续签响应缺少 Cookie")
        }
        throw failure ?: ApiException("续签失败")
    }

    private suspend fun resolveOutcome(parsed: ParsedResponse): Outcome = when (parsed) {
        is ParsedResponse.Challenge -> parsed.outcome
        is ParsedResponse.Done -> {
            val res = parsed.result
            if (res.cookie.isBlank() && res.gsid.isNotBlank() && res.uid.isNotBlank()) {
                val refreshed = refresh(res.uid, res.gsid)
                Outcome.Success(
                    refreshed.copy(
                        uid = refreshed.uid.ifBlank { res.uid },
                        gsid = refreshed.gsid.ifBlank { res.gsid },
                        sut = refreshed.sut.ifBlank { res.sut },
                        accessToken = refreshed.accessToken.ifBlank { res.accessToken },
                        expires = if (refreshed.expires > 0L) refreshed.expires else res.expires,
                    ),
                )
            } else {
                Outcome.Success(res)
            }
        }
    }

    /** 对应 Share `OP.java`：用原版 `"1".."20"` 设备指纹向 `guest/login` 领一次 `aid` 并落盘复用。 */
    private suspend fun ensureAid(): String {
        val cached = App.store.clientAid()
        if (cached.isNotBlank()) return cached
        return runCatching {
            val did = App.store.clientDid()
            val checkToken = md5Hex("/$did/obiew")
            val dm = App.context.resources.displayMetrics
            val sdkUa = "${Build.MANUFACTURER}-${Build.MODEL}__weibosdk__0041005000__android__android${Build.VERSION.RELEASE}"
            val mfpJson = JSONObject()
                .put("1", "Android ${Build.VERSION.RELEASE}")
                .apply {
                    val aidStr = Api.androidId()
                    if (aidStr.isNotBlank()) put("10", aidStr)
                }
                .put("13", Build.SUPPORTED_ABIS.firstOrNull().orEmpty().ifBlank { "arm64-v8a" })
                .put("14", Build.MODEL)
                .put("15", "137438953472")
                .put("16", "${dm.widthPixels}*${dm.heightPixels}")
                .put("17", "<unknown ssid>")
                .put("18", Build.BRAND)
                .put("19", Api.networkType())
                .put("20", sdkUa)
                .toString()
            val mfp = "01" + encryptRsaBase64(mfpJson.toByteArray(Charsets.UTF_8), RSA_AID_PUBKEY)
            val query = linkedMapOf<String, String?>()
            putCommonParams(query, FROM)
            val form = linkedMapOf(
                "did" to did,
                "device_name" to Api.deviceName(),
                "checktoken" to checkToken,
                "key_hash" to SHARE_KEY_HASH,
                "appkey" to SHARE_APPKEY,
                "mfp" to mfp,
                "packagename" to "com.sina.weibo",
            )
            val resp = signedPost("guest/login", query, form, aid = "")
            val aid = resp.json.optString("aid")
            if (aid.isNotBlank()) App.store.saveClientAid(aid)
            aid
        }.getOrDefault("")
    }

    private suspend fun signedPost(
        path: String,
        query: LinkedHashMap<String, String?>,
        form: Map<String, String>,
        aid: String,
    ): Api.ContainerResponse {
        if (aid.isNotBlank() && !query.containsKey("aid")) {
            query["aid"] = aid
        }
        val unsignedUrl = Api.url("https://api.weibo.cn/2/$path", query)
        val slash = unsignedUrl.indexOf('/', 9)
        if (slash >= 0) {
            val cum = WeiboNative.cumFrom(unsignedUrl.substring(slash))
            if (!cum.isNullOrBlank()) query["cum"] = cum
        }
        val headers = LinkedHashMap<String, String>()
        val validator = xValidator
        if (validator.isNotBlank()) {
            headers["X-Log-Uid"] = App.repo.session?.uid.orEmpty()
            headers["X-Sessionid"] = UUID.randomUUID().toString()
            headers["X-Validator"] = validator
        }
        val resp = Api.containerPostShare(path, query, form, headers)
        if (!resp.xValidator.isNullOrBlank()) {
            xValidator = resp.xValidator
        }
        return resp
    }

    private fun putCommonParams(query: LinkedHashMap<String, String?>, from: String) {
        query.putIfAbsent("lang", "zh_CN")
        query.putIfAbsent("networktype", Api.networkType())
        query.putIfAbsent("c", "android")
        query["from"] = from
        query.putIfAbsent("wm", WM)
        query.putIfAbsent("oldwm", Api.oldWm())
        query.putIfAbsent("ua", Api.clientUa())
        query.putIfAbsent("v_p", V_P)
        query.putIfAbsent("android_id", Api.androidId())
        query.putIfAbsent("wb_version", WB_VERSION)
        query.putIfAbsent("skin", SKIN)
        query.putIfAbsent("v_f", V_F)
    }

    private fun commonFormParams(): Map<String, String> = linkedMapOf(
        "getuser" to "1",
        "getoauth" to "1",
        "getcookie" to "1",
        "device_name" to Api.deviceName(),
    )

    private fun signInfo(info: String): String = WeiboNative.s(info) ?: WeiboSign.s(info)

    private fun iValue(): String = WeiboNative.i().orEmpty()

    internal fun encryptPassword(password: String): String =
        encryptRsaBase64(password.toByteArray(Charsets.UTF_8), RSA_LOGIN_PUBKEY)

    internal fun encryptRsaBase64(plain: ByteArray, publicKeyBase64: String): String {
        val keyBytes = Base64.getDecoder().decode(publicKeyBase64)
        val pubKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(keyBytes))
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, pubKey)
        val out = ByteArrayOutputStream()
        var offset = 0
        while (offset < plain.size) {
            val len = minOf(117, plain.size - offset)
            val block = cipher.doFinal(plain, offset, len)
            out.write(block)
            offset += len
        }
        return Base64.getEncoder().encodeToString(out.toByteArray())
    }

    internal fun parseResponse(root: JSONObject, setCookie: List<String> = emptyList()): ParsedResponse {
        val code = errorCode(root)
        val errUrl = root.optString("errurl").ifBlank { root.optString("error_url") }
        val msg = errorMessage(root)
        if (code == null || code == "0") {
            val parsed = parse(root, setCookie)
            if (parsed != null) return ParsedResponse.Done(parsed)
            if (errUrl.isNotBlank()) return ParsedResponse.Challenge(Outcome.WebVerify(errUrl, msg))
            throw ApiException(msg)
        }
        if (errUrl.isNotBlank()) {
            return ParsedResponse.Challenge(Outcome.WebVerify(errUrl, msg))
        }
        if (code == "-1005") {
            val ann = root.optJSONObject("annotations")
            val pic = ann?.optString("pic").orEmpty()
            val cpt = ann?.optString("cpt").orEmpty()
            val entry = ann?.optString("entry").orEmpty()
            if (pic.isNotBlank() || cpt.isNotBlank()) {
                return ParsedResponse.Challenge(
                    Outcome.Captcha(CaptchaChallenge(picUrl = pic, cpt = cpt, entry = entry, message = msg)),
                )
            }
        }
        if (code == "-1007") {
            val phones = parseSafetyPhones(root)
            val retcode = root.optString("retcode")
            if (phones.isNotEmpty() && retcode.isNotBlank()) {
                return ParsedResponse.Challenge(
                    Outcome.SafetyVerify(SafetyChallenge(retcode = retcode, phones = phones, message = msg)),
                )
            }
        }
        throw ApiException(msg, code)
    }

    internal fun parseSafetyPhones(root: JSONObject): List<VerifyPhone> {
        val out = ArrayList<VerifyPhone>()
        val seen = HashSet<String>()
        fun add(phone: String, code: String) {
            if (phone.isBlank() || !seen.add("$code:$phone")) return
            out.add(VerifyPhone(phone = phone, code = code))
        }
        val list = root.optJSONArray("phone_list")
        if (list != null) {
            for (i in 0 until list.length()) {
                val item = list.optJSONObject(i) ?: continue
                add(item.optString("phone"), item.optString("code"))
            }
        }
        add(root.optString("phone"), root.optString("code"))
        return out
    }

    fun maskPhone(raw: String): String =
        if (raw.length >= 7) raw.take(3) + "*****" + raw.takeLast(3) else "*****"

    internal fun parse(root: JSONObject, setCookie: List<String> = emptyList()): Result? {
        val gsid = root.optString("gsid")
        val cookies = domainMap(root.opt("cookie"), setCookie)
        val cookie = CookieVault.authHeader(cookies, "m.weibo.cn")
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

    private fun setCookiePairs(headers: List<String>): List<String> = headers.mapNotNull { header ->
        header.substringBefore(';').trim().takeIf { it.contains('=') }
    }

    /**
     * 解析 `account/login` 返回的 `cookie`：典型结构为
     * `{uid: "...", cookie: {".sina.com.cn": "SUB=...; expires=...\nSUBP=...; ...", ".weibo.cn": "..."}}`。
     * 每条 cookie 之间以 `\n` 分隔，各自带着 `expires` / `path` / `domain` 等属性；
     * 也兼容旧式只用 `;` 拼接的键值对。
     */
    internal fun domainMap(raw: Any?, setCookie: List<String>): Map<String, String> {
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
                val table: JSONObject? = when (inner) {
                    is String -> {
                        put(CookieVault.WEIBO_CN, splitPairs(inner))
                        null
                    }
                    is JSONObject -> inner
                    else -> raw
                }
                if (table != null) {
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
            else -> Unit
        }
        if (setCookie.isNotEmpty()) put(CookieVault.WEIBO_CN, setCookiePairs(setCookie))
        return out
    }

    private val COOKIE_ATTRS = setOf("expires", "path", "domain", "max-age", "samesite", "priority", "partitioned")

    internal fun splitPairs(text: String): List<String> = buildList {
        for (line in text.split('\n')) {
            for (segment in line.split(';')) {
                val trimmed = segment.trim()
                val eq = trimmed.indexOf('=')
                if (eq <= 0) continue
                val name = trimmed.substring(0, eq).trim().lowercase()
                if (name in COOKIE_ATTRS) continue
                add(trimmed)
            }
        }
    }

    private fun md5Hex(text: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    internal fun errorCode(root: JSONObject): String? {
        val errno = root.optString("errno")
        if (errno.isNotBlank() && errno != "0") return errno
        val errorCode = root.optString("error_code")
        if (errorCode.isNotBlank() && errorCode != "0") return errorCode
        return null
    }

    internal fun errorMessage(root: JSONObject): String {
        val text = root.optString("errmsg")
            .ifBlank { root.optString("error") }
            .ifBlank { root.optString("msg") }
        if (text.isNotBlank()) return text
        val code = errorCode(root)
        return if (code != null) "请求失败（$code）" else "请求失败"
    }
}
