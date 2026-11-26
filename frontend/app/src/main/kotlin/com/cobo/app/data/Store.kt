package com.cobo.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class Store(private val context: Context) {

    private fun file(name: String) = File(context.filesDir, name)

    /** 读文件。`secret` 的文件走 [SecretStore]，没有 `v1:` 前缀的旧明文照读。 */
    private fun readText(name: String, secret: Boolean = false): String? {
        val f = file(name)
        if (!f.exists()) return null
        val raw = runCatching { f.readText() }.getOrNull() ?: return null
        return if (secret) SecretStore.open(raw) else raw
    }

    private fun readObject(name: String, secret: Boolean = false): JSONObject? =
        readText(name, secret)?.let { runCatching { JSONObject(it) }.getOrNull() }

    private fun readArray(name: String, secret: Boolean = false): JSONArray {
        val text = readText(name, secret) ?: return JSONArray()
        return runCatching { JSONArray(text) }.getOrDefault(JSONArray())
    }

    private fun write(name: String, value: Any, secret: Boolean = false) {
        runCatching {
            val text = value.toString()
            val payload = if (secret) SecretStore.seal(text) else text
            val f = file(name)
            val tmp = File(f.parentFile, "${f.name}.tmp")
            tmp.writeText(payload)
            if (!tmp.renameTo(f)) {
                f.writeText(payload)
                tmp.delete()
            }
        }
    }

    fun session(): Session? {
        val text = readText("session.json", secret = true) ?: return null
        return runCatching {
            val o = JSONObject(text)
            val cookies = o.optJSONObject("cookies").toStringMap()
            val cookie = o.str("cookie").ifBlank { CookieVault.authHeader(cookies, "m.weibo.cn") }
            val gsid = o.optString("gsid")
            val loginType = o.optInt("loginType", if (gsid.isNotBlank()) 4 else 1)
            // 网页会话只有 SUB 在才算登录；客户端会话只要 gsid 还在，就可以靠续签换回新 SUB。
            if (!cookie.contains("SUB=") && !(loginType == 4 && gsid.isNotBlank())) null
            else Session(
                cookie = cookie,
                uid = o.str("uid"),
                savedAt = o.optLong("savedAt"),
                gsid = gsid,
                gsidAt = o.optLong("gsidAt"),
                accessToken = o.optString("accessToken"),
                expires = o.optLong("expires"),
                source = o.optString("source", "web"),
                loginType = loginType,
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
        write("session.json", o, secret = true)
    }

    /** 客户端登录的完整会话：cookie 之外还有能换新 cookie 的 gsid。 */
    fun saveClientSession(
        uid: String,
        gsid: String,
        cookie: String,
        accessToken: String,
        expires: Long,
        cookies: Map<String, String> = CookieVault.seed(cookie),
        gsidAt: Long = System.currentTimeMillis(),
    ) {
        val now = System.currentTimeMillis()
        val o = JSONObject()
            .put("source", "client")
            .put("cookie", cookie)
            .put("uid", uid)
            .put("gsid", gsid)
            .put("gsidAt", gsidAt)
            .put("accessToken", accessToken)
            .put("expires", expires)
            .put("savedAt", now)
            .put("loginType", 4)
            .put("cookies", cookies.toJson())
        write("session.json", o, secret = true)
    }

    /** 续期只换 gsid 与 cookie，账号、昵称这些字段原样留着。 */
    fun updateClientSession(
        gsid: String,
        cookie: String,
        accessToken: String,
        expires: Long,
        cookies: Map<String, String> = CookieVault.seed(cookie),
    ) {
        val o = readObject("session.json", secret = true) ?: JSONObject()
        o.put("source", "client")
        o.put("cookie", cookie)
        o.put("cookies", cookies.toJson())
        o.put("loginType", 4)
        if (gsid.isNotBlank()) o.put("gsid", gsid)
        o.put("gsidAt", System.currentTimeMillis())
        if (accessToken.isNotBlank()) o.put("accessToken", accessToken)
        if (expires > 0) o.put("expires", expires)
        write("session.json", o, secret = true)
    }

    /**
     * 只更新 cookie 表。网页端每次响应都可能滚动 `SUB` / `ALF`，
     * 收下来就直接落盘，不必等下一次登录。
     */
    fun updateCookies(cookies: Map<String, String>) {
        val o = readObject("session.json", secret = true) ?: return
        if (o.length() == 0) return
        val merged = LinkedHashMap(o.optJSONObject("cookies").toStringMap())
        merged.putAll(cookies)
        o.put("cookies", merged.toJson())
        o.put("cookie", CookieVault.header(merged, "m.weibo.cn"))
        o.put("savedAt", System.currentTimeMillis())
        write("session.json", o, secret = true)
    }

    /**
     * 容器接口的 `i` 参数，15 位设备号。第一次生成后一直复用：
     * 每次登录都换一个值会被当成新设备，反而更容易触发验证。
     */
    fun deviceId(): String {
        val f = file("device.json")
        val obj = if (f.exists()) {
            runCatching { JSONObject(f.readText()) }.getOrDefault(JSONObject())
        } else {
            JSONObject()
        }
        val saved = obj.optString("i")
        if (saved.isNotBlank()) return saved
        val id = "86" + (1_000_000_000_000L + (Math.random() * 8_999_999_999_999L).toLong())
        write("device.json", obj.put("i", id))
        return id
    }

    /** 持久化 Share 客户端通路的 `aid` 与 `did`，避免每次请求重领 `guest/login`。 */
    fun clientAid(): String {
        val f = file("device.json")
        if (!f.exists()) return ""
        return runCatching {
            val obj = JSONObject(f.readText())
            if (obj.optInt("aidV", 1) < 2) "" else obj.optString("aid")
        }.getOrDefault("")
    }

    fun saveClientAid(aid: String) {
        if (aid.isBlank()) return
        val f = file("device.json")
        val obj = if (f.exists()) {
            runCatching { JSONObject(f.readText()) }.getOrDefault(JSONObject())
        } else {
            JSONObject()
        }
        write("device.json", obj.put("aid", aid).put("aidV", 2))
    }

    fun clientDid(): String {
        val f = file("device.json")
        val obj = if (f.exists()) {
            runCatching { JSONObject(f.readText()) }.getOrDefault(JSONObject())
        } else {
            JSONObject()
        }
        val saved = obj.optString("did")
        if (saved.isNotBlank()) return saved
        val did = java.util.UUID.randomUUID().toString().replace("-", "")
        write("device.json", obj.put("did", did))
        return did
    }

    fun clientValidator(): String =
        readObject("validator.json", secret = true)?.str("validator").orEmpty()

    fun saveClientValidator(validator: String) {
        if (validator.isBlank()) return
        write("validator.json", JSONObject().put("validator", validator), secret = true)
    }

    fun clearSession() {
        file("session.json").delete()
        file("validator.json").delete()
    }

    /** 纯本地模式用的访客 cookie（SUB / SUBP），不对应任何账号，单独存一份免得每次启动重领。 */
    fun visitorCookie(): String? =
        readObject("visitor.json", secret = true)?.str("cookie")?.ifBlank { null }

    fun saveVisitorCookie(cookie: String) {
        write(
            "visitor.json",
            JSONObject().put("cookie", cookie).put("savedAt", System.currentTimeMillis()),
            secret = true,
        )
    }

    fun accounts(): List<Account> {
        val arr = readArray("accounts.json", secret = true)
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
        val arr = readArray("accounts.json", secret = true)
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
        write("accounts.json", out, secret = true)
    }

    fun removeAccount(uid: String) {
        val arr = readArray("accounts.json", secret = true)
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.str("uid") == uid) continue
            out.put(o)
        }
        write("accounts.json", out, secret = true)
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

    /**
     * 本地收藏：全局共用一份 `favorites.json`，跨多账号和纯本地模式共享，切号与退出登录都不清空。
     */
    fun favorites(): List<Status> {
        val arr = readArray("favorites.json")
        val out = ArrayList<Status>(arr.length())
        for (i in 0 until arr.length()) {
            val status = statusFromSnapshot(arr.optJSONObject(i)) ?: continue
            out.add(if (status.favorited) status else status.copy(favorited = true))
        }
        return out
    }

    fun isFavorite(id: String): Boolean {
        if (id.isBlank()) return false
        val arr = readArray("favorites.json")
        for (i in 0 until arr.length()) {
            if (arr.optJSONObject(i)?.str("id") == id) return true
        }
        return false
    }

    fun addFavorite(status: Status) {
        if (status.id.isBlank()) return
        val arr = readArray("favorites.json")
        val out = JSONArray()
        out.put(statusToSnapshot(if (status.favorited) status else status.copy(favorited = true)))
        for (i in 0 until arr.length()) {
            if (out.length() >= 500) break
            val o = arr.optJSONObject(i) ?: continue
            if (o.str("id") == status.id) continue
            out.put(o)
        }
        write("favorites.json", out)
    }

    fun removeFavorite(id: String) {
        if (id.isBlank()) return
        val arr = readArray("favorites.json")
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.str("id") == id) continue
            out.put(o)
        }
        write("favorites.json", out)
    }

    fun toggleFavorite(status: Status): Boolean {
        return if (isFavorite(status.id)) {
            removeFavorite(status.id)
            false
        } else {
            addFavorite(status)
            true
        }
    }

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

    private fun readProfileRenameMap(): JSONObject {
        val f = file("profile-rename.json")
        if (!f.exists()) return JSONObject()
        return runCatching { JSONObject(f.readText()) }.getOrDefault(JSONObject())
    }

    fun profileRenameRecord(uid: String): ProfileRenameRecord {
        if (uid.isBlank()) return ProfileRenameRecord()
        val item = readProfileRenameMap().optJSONObject(uid) ?: return ProfileRenameRecord()
        return ProfileRenameRecord(
            lastRenamedAtMs = item.optLong("lastRenamedAtMs", 0L),
            lastBlockedAtMs = item.optLong("lastBlockedAtMs", 0L),
            lastBlockedReason = item.optString("lastBlockedReason", ""),
        )
    }

    fun recordProfileRenamed(uid: String, atMs: Long = System.currentTimeMillis()) {
        if (uid.isBlank()) return
        val map = readProfileRenameMap()
        map.put(
            uid,
            JSONObject()
                .put("lastRenamedAtMs", atMs)
                .put("lastBlockedAtMs", 0L)
                .put("lastBlockedReason", ""),
        )
        write("profile-rename.json", map)
    }

    fun recordProfileRenameBlocked(
        uid: String,
        reason: String,
        atMs: Long = System.currentTimeMillis(),
    ) {
        if (uid.isBlank()) return
        val prev = profileRenameRecord(uid)
        val map = readProfileRenameMap()
        map.put(
            uid,
            JSONObject()
                .put("lastRenamedAtMs", prev.lastRenamedAtMs)
                .put("lastBlockedAtMs", atMs)
                .put("lastBlockedReason", reason.take(80)),
        )
        write("profile-rename.json", map)
    }

    fun userRemarks(): Map<String, String> =
        decodeUserRemarks(readObject("user-remarks.json"))

    fun saveUserRemarks(remarks: Map<String, String>) {
        write("user-remarks.json", encodeUserRemarks(remarks))
    }

    fun readLaterEntries(): List<ReadLaterEntry> =
        decodeReadLaterEntries(readArray("read-later.json"))

    fun saveReadLaterEntries(entries: List<ReadLaterEntry>) {
        write("read-later.json", encodeReadLaterEntries(entries))
    }
}

data class ProfileRenameRecord(
    val lastRenamedAtMs: Long = 0L,
    val lastBlockedAtMs: Long = 0L,
    val lastBlockedReason: String = "",
)

data class ProfileRenameStatus(
    val summary: String,
    val limited: Boolean,
)

private const val ONE_DAY_MS = 86_400_000L

internal fun computeProfileRenameStatus(
    record: ProfileRenameRecord,
    membership: Membership,
    nowMs: Long = System.currentTimeMillis(),
): ProfileRenameStatus {
    val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
    val lastTimeText = if (record.lastRenamedAtMs > 0L) {
        fmt.format(java.util.Date(record.lastRenamedAtMs))
    } else {
        "暂无本机记录"
    }
    val recentBlock = record.lastBlockedAtMs > 0L && record.lastBlockedAtMs >= record.lastRenamedAtMs
    if (recentBlock) {
        val reason = record.lastBlockedReason
            .removePrefix("修改失败：")
            .replace(Regex("\\s*（HTTP\\s*\\d+）"), "")
            .trim()
            .ifBlank { "未到改名间隔或次数已达上限" }
        return ProfileRenameStatus(
            summary = "上次改名：$lastTimeText · 当前暂不可改名（$reason）",
            limited = true,
        )
    }
    if (record.lastRenamedAtMs > 0L) {
        val elapsed = (nowMs - record.lastRenamedAtMs).coerceAtLeast(0L)
        return when (membership) {
            Membership.None -> {
                if (elapsed < 365L * ONE_DAY_MS) {
                    ProfileRenameStatus(
                        summary = "上次改名：$lastTimeText · 非会员每年限改 1 次，当前暂不可改",
                        limited = true,
                    )
                } else {
                    ProfileRenameStatus(
                        summary = "上次改名：$lastTimeText · 当前可尝试改名",
                        limited = false,
                    )
                }
            }
            Membership.Vip, Membership.Svip, Membership.Vvip -> {
                if (elapsed < 7L * ONE_DAY_MS) {
                    ProfileRenameStatus(
                        summary = "上次改名：$lastTimeText · 距上次不足 7 天，可能处于冷却期",
                        limited = true,
                    )
                } else {
                    ProfileRenameStatus(
                        summary = "上次改名：$lastTimeText · 当前可尝试改名（以年度次数为准）",
                        limited = false,
                    )
                }
            }
        }
    }
    return when (membership) {
        Membership.None -> ProfileRenameStatus(
            summary = "上次改名：暂无本机记录 · 非会员每年限改 1 次，可尝试提交",
            limited = false,
        )
        Membership.Vip, Membership.Svip, Membership.Vvip -> ProfileRenameStatus(
            summary = "上次改名：暂无本机记录 · 会员享年度多次改名（需满足间隔），可尝试提交",
            limited = false,
        )
    }
}
