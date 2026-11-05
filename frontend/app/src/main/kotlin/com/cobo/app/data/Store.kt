package com.cobo.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class Store(private val context: Context) {

    private fun file(name: String) = File(context.filesDir, name)

    private fun readArray(name: String): JSONArray {
        val f = file(name)
        if (!f.exists()) return JSONArray()
        return runCatching { JSONArray(f.readText()) }.getOrDefault(JSONArray())
    }

    private fun write(name: String, value: Any) {
        runCatching {
            val f = file(name)
            val tmp = File(f.parentFile, "${f.name}.tmp")
            tmp.writeText(value.toString())
            if (!tmp.renameTo(f)) {
                f.writeText(value.toString())
                tmp.delete()
            }
        }
    }

    fun session(): Session? {
        val f = file("session.json")
        if (!f.exists()) return null
        return runCatching {
            val o = JSONObject(f.readText())
            val cookies = o.optJSONObject("cookies").toStringMap()
            val cookie = o.str("cookie").ifBlank { CookieVault.header(cookies, "m.weibo.cn") }
            // 只有 SUB 在才算登录：服务端删掉 SUB 以后剩下的都是没用的空壳，
            // 留着它会让 App 以为还登录着，实际每个请求都回 -100。
            if (!cookie.contains("SUB=")) null
            else Session(
                cookie = cookie,
                uid = o.str("uid"),
                savedAt = o.optLong("savedAt"),
                gsid = o.optString("gsid"),
                gsidAt = o.optLong("gsidAt"),
                accessToken = o.optString("accessToken"),
                expires = o.optLong("expires"),
                source = o.optString("source", "web"),
                loginType = o.optInt("loginType", if (o.optString("gsid").isNotBlank()) 4 else 1),
                cookies = cookies,
            )
        }.getOrNull()
    }

    fun saveSession(
        cookie: String,
        uid: String = "",
        cookies: Map<String, String> = CookieVault.seed(cookie),
    ) {
        val o = JSONObject()
            .put("source", "web")
            .put("cookie", cookie)
            .put("uid", uid)
            .put("savedAt", System.currentTimeMillis())
            .put("loginType", 1)
            .put("cookies", cookies.toJson())
        write("session.json", o)
    }

    /** 客户端登录的完整会话：cookie 之外还有能换新 cookie 的 gsid。 */
    fun saveClientSession(
        uid: String,
        gsid: String,
        cookie: String,
        accessToken: String,
        expires: Long,
        cookies: Map<String, String> = CookieVault.seed(cookie),
    ) {
        val now = System.currentTimeMillis()
        val o = JSONObject()
            .put("source", "client")
            .put("cookie", cookie)
            .put("uid", uid)
            .put("gsid", gsid)
            .put("gsidAt", now)
            .put("accessToken", accessToken)
            .put("expires", expires)
            .put("savedAt", now)
            .put("loginType", 4)
            .put("cookies", cookies.toJson())
        write("session.json", o)
    }

    /** 续期只换 gsid 与 cookie，账号、昵称这些字段原样留着。 */
    fun updateClientSession(
        gsid: String,
        cookie: String,
        accessToken: String,
        expires: Long,
        cookies: Map<String, String> = CookieVault.seed(cookie),
    ) {
        val f = file("session.json")
        val o = if (f.exists()) {
            runCatching { JSONObject(f.readText()) }.getOrDefault(JSONObject())
        } else {
            JSONObject()
        }
        o.put("source", "client")
        o.put("cookie", cookie)
        o.put("cookies", cookies.toJson())
        o.put("loginType", 4)
        if (gsid.isNotBlank()) o.put("gsid", gsid)
        o.put("gsidAt", System.currentTimeMillis())
        if (accessToken.isNotBlank()) o.put("accessToken", accessToken)
        if (expires > 0) o.put("expires", expires)
        write("session.json", o)
    }

    /**
     * 只更新 cookie 表。网页端每次响应都可能滚动 `SUB` / `ALF`，
     * 收下来就直接落盘，不必等下一次登录。
     */
    fun updateCookies(cookies: Map<String, String>) {
        val f = file("session.json")
        if (!f.exists()) return
        val o = runCatching { JSONObject(f.readText()) }.getOrDefault(JSONObject())
        if (o.length() == 0) return
        val merged = LinkedHashMap(o.optJSONObject("cookies").toStringMap())
        merged.putAll(cookies)
        o.put("cookies", merged.toJson())
        o.put("cookie", CookieVault.header(merged, "m.weibo.cn"))
        o.put("savedAt", System.currentTimeMillis())
        write("session.json", o)
    }

    /**
     * 容器接口的 `i` 参数，15 位设备号。第一次生成后一直复用：
     * 每次登录都换一个值会被当成新设备，反而更容易触发验证。
     */
    fun deviceId(): String {
        val f = file("device.json")
        val saved = if (f.exists()) {
            runCatching { JSONObject(f.readText()).optString("i") }.getOrNull()
        } else {
            null
        }
        if (!saved.isNullOrBlank()) return saved
        val id = "86" + (1_000_000_000_000L + (Math.random() * 8_999_999_999_999L).toLong())
        write("device.json", JSONObject().put("i", id))
        return id
    }

    fun clearSession() = file("session.json").delete()

    /** 纯本地模式用的访客 cookie（SUB / SUBP），不对应任何账号，单独存一份免得每次启动重领。 */
    fun visitorCookie(): String? {
        val f = file("visitor.json")
        if (!f.exists()) return null
        return runCatching { JSONObject(f.readText()).str("cookie") }.getOrNull()?.ifBlank { null }
    }

    fun saveVisitorCookie(cookie: String) {
        write("visitor.json", JSONObject().put("cookie", cookie).put("savedAt", System.currentTimeMillis()))
    }

    fun accounts(): List<Account> {
        val arr = readArray("accounts.json")
        val out = ArrayList<Account>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val cookie = o.str("cookie")
            if (cookie.isBlank()) continue
            out.add(
                Account(
                    uid = o.str("uid"),
                    name = o.str("name"),
                    avatar = o.str("avatar"),
                    cookie = cookie,
                    gsid = o.optString("gsid"),
                    gsidAt = o.optLong("gsidAt"),
                )
            )
        }
        return out
    }

    fun saveAccount(account: Account) {
        val arr = readArray("accounts.json")
        val out = JSONArray()
        val o = JSONObject()
            .put("uid", account.uid)
            .put("name", account.name)
            .put("avatar", account.avatar)
            .put("cookie", account.cookie)
            .put("gsid", account.gsid)
            .put("gsidAt", account.gsidAt)
        out.put(o)
        for (i in 0 until arr.length()) {
            val old = arr.optJSONObject(i) ?: continue
            if (old.str("uid") == account.uid) continue
            if (out.length() >= 10) break
            out.put(old)
        }
        write("accounts.json", out)
    }

    fun removeAccount(uid: String) {
        val arr = readArray("accounts.json")
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.str("uid") == uid) continue
            out.put(o)
        }
        write("accounts.json", out)
    }

    fun drafts(): List<Draft> {
        val arr = readArray("drafts.json")
        val out = ArrayList<Draft>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(Draft(o.str("text"), o.optLong("time")))
        }
        return out
    }

    fun addDraft(text: String) {
        if (text.isBlank()) return
        val arr = readArray("drafts.json")
        val out = JSONArray()
        out.put(JSONObject().put("text", text).put("time", System.currentTimeMillis()))
        for (i in 0 until arr.length()) {
            if (out.length() >= 50) break
            val o = arr.optJSONObject(i) ?: continue
            if (o.str("text") == text) continue
            out.put(o)
        }
        write("drafts.json", out)
    }

    fun removeDraft(time: Long) {
        val arr = readArray("drafts.json")
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optLong("time") == time) continue
            out.put(o)
        }
        write("drafts.json", out)
    }

    fun history(): List<HistoryItem> {
        val arr = readArray("history.json")
        val out = ArrayList<HistoryItem>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(HistoryItem(o.str("id"), o.str("mid"), o.str("text"), o.str("user"), o.optLong("time"), o.str("html")))
        }
        return out
    }

    fun addHistory(item: HistoryItem) {
        val arr = readArray("history.json")
        val out = JSONArray()
        out.put(
            JSONObject()
                .put("id", item.id)
                .put("mid", item.mid)
                .put("text", item.text)
                .put("user", item.user)
                .put("html", item.html)
                .put("time", System.currentTimeMillis())
        )
        for (i in 0 until arr.length()) {
            if (out.length() >= 100) break
            val o = arr.optJSONObject(i) ?: continue
            if (o.str("id") == item.id) continue
            out.put(o)
        }
        write("history.json", out)
    }

    fun removeHistory(id: String) {
        val arr = readArray("history.json")
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.str("id") == id) continue
            out.put(o)
        }
        write("history.json", out)
    }

    fun clearHistory() = write("history.json", JSONArray())

    /** uid -> 关注标记 key。存空串表示「明确清掉」，和「没记过」要分开，否则服务端值会一直补回来。 */
    private fun readFollowTags(): JSONObject {
        val f = file("follows.json")
        if (!f.exists()) return JSONObject()
        return runCatching { JSONObject(f.readText()) }.getOrDefault(JSONObject())
    }

    fun hasFollowTag(uid: String): Boolean = readFollowTags().has(uid)

    fun followTag(uid: String): FollowTag? = FollowTag.of(readFollowTags().optString(uid))

    fun followTags(): Map<String, FollowTag> {
        val o = readFollowTags()
        val out = LinkedHashMap<String, FollowTag>()
        o.keys().forEach { uid -> FollowTag.of(o.optString(uid))?.let { out[uid] = it } }
        return out
    }

    fun setFollowTag(uid: String, tag: FollowTag?) {
        val o = readFollowTags()
        o.put(uid, tag?.key ?: "")
        write("follows.json", o)
    }

    /**
     * uid -> 读到过的最后一条私信 id。
     *
     * 微博的 H5 私信没有暴露「标记已读」的接口，`msglist` 的 unread 会一直是 1。
     * 这里只记本地已读：列表里的未读点只在「最新一条 id 变过」时重新亮起，
     * 和服务端语义一致，也不会因为刷新又把点顶回来。
     */
    private fun readChatSeen(): JSONObject {
        val f = file("chat-seen.json")
        if (!f.exists()) return JSONObject()
        return runCatching { JSONObject(f.readText()) }.getOrDefault(JSONObject())
    }

    fun chatSeen(uid: String): String = readChatSeen().optString(uid)

    /** token 用会话列表里的预览文字：服务端没有已读接口，只能靠「预览是否变过」判断有没有新消息。 */
    fun markChatSeen(uid: String, token: String) {
        if (uid.isBlank() || token.isBlank()) return
        val o = readChatSeen()
        if (o.optString(uid) == token) return
        o.put(uid, token)
        write("chat-seen.json", o)
    }
}
