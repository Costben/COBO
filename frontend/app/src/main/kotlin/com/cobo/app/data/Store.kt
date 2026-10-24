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
}
