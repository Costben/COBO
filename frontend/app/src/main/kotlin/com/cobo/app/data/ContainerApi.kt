package com.cobo.app.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Share 3.9.6 客户端容器协议通道（`https://api.weibo.cn/2/`）。
 *
 * 所有客户端登录账号（`LOGIN_TYPE_CLIENT`）的业务读/写请求优先走这一层：
 * 用 `gsid` + `s` + `i` + `aid` + `cum` 签名认证，不依赖 `m.weibo.cn` 或 `weibo.com`
 * 的网页 `SUB` Cookie，因此网页侧会话被风控/踢出时仍可稳定使用。
 */
object ContainerApi {

    val AUTH_ERRNOS = setOf("-100", "21327", "21315", "21301", "50113002")

    private fun buildQuery(
        path: String,
        uid: String,
        gsid: String,
        aid: String,
        extra: Map<String, String?>,
    ): LinkedHashMap<String, String?> {
        val query = linkedMapOf<String, String?>()
        ClientLogin.putCommonParams(query, ClientLogin.FROM)
        query["s"] = ClientLogin.signInfo(uid)
        val iVal = ClientLogin.iValue()
        if (iVal.isNotBlank()) query["i"] = iVal
        query["gsid"] = gsid
        extra.forEach { (k, v) -> if (v != null) query[k] = v }
        ClientLogin.signQuery(path, query, aid)
        return query
    }

    private fun checkSuccess(root: JSONObject): JSONObject {
        val errno = ClientLogin.errorCode(root)
        if (errno != null) {
            throw ApiException(ClientLogin.errorMessage(root), errno)
        }
        return root
    }

    suspend fun get(
        path: String,
        uid: String,
        gsid: String,
        aid: String = "",
        extra: Map<String, String?> = emptyMap(),
    ): JSONObject {
        val resolvedAid = aid.ifBlank { ClientLogin.ensureAid() }
        val query = buildQuery(path, uid, gsid, resolvedAid, extra)
        val resp = Api.containerGetShare(path, query, ClientLogin.validatorHeaders(uid))
        ClientLogin.recordValidator(resp.xValidator)
        return checkSuccess(resp.json)
    }

    suspend fun getRaw(
        path: String,
        uid: String,
        gsid: String,
        aid: String = "",
        extra: Map<String, String?> = emptyMap(),
    ): String {
        val resolvedAid = aid.ifBlank { ClientLogin.ensureAid() }
        val query = buildQuery(path, uid, gsid, resolvedAid, extra)
        val (text, validator) = Api.containerGetRawShare(path, query, ClientLogin.validatorHeaders(uid))
        ClientLogin.recordValidator(validator)
        return text
    }

    suspend fun post(
        path: String,
        uid: String,
        gsid: String,
        aid: String = "",
        form: Map<String, String> = emptyMap(),
        queryExtra: Map<String, String?> = emptyMap(),
    ): JSONObject {
        val resolvedAid = aid.ifBlank { ClientLogin.ensureAid() }
        val query = buildQuery(path, uid, gsid, resolvedAid, queryExtra)
        val resp = Api.containerPostShare(path, query, form, ClientLogin.validatorHeaders(uid))
        ClientLogin.recordValidator(resp.xValidator)
        return checkSuccess(resp.json)
    }

    /** `page/get_search_suggest` 返回顶层数组：`["关键词", [{"card": {"desc_dark": "..."}}, ...]]`。 */
    fun parseSearchSuggest(rawText: String): List<String> {
        val root = runCatching { JSONArray(rawText) }.getOrNull() ?: return emptyList()
        val arr = root.optJSONArray(1) ?: return emptyList()
        val out = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) {
            val card = arr.optJSONObject(i)?.optJSONObject("card") ?: continue
            val word = card.str("desc_dark").ifBlank { card.str("desc") }.trim()
            if (word.isNotBlank() && word !in out) out.add(word)
        }
        return out
    }
}
