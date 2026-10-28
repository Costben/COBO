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
            val cookie = o.str("cookie")
            if (cookie.isBlank()) null
            else Session(cookie, o.str("uid"), o.optLong("savedAt"))
        }.getOrNull()
    }

    fun saveSession(cookie: String, uid: String = "") {
        val o = JSONObject()
            .put("source", "web")
            .put("cookie", cookie)
            .put("uid", uid)
            .put("savedAt", System.currentTimeMillis())
        write("session.json", o)
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
            out.add(Account(o.str("uid"), o.str("name"), o.str("avatar"), cookie))
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
            out.add(HistoryItem(o.str("id"), o.str("mid"), o.str("text"), o.str("user"), o.optLong("time")))
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
