package com.cobo.app.data

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import com.cobo.app.json.JSONArray
import com.cobo.app.json.JSONObject
import com.cobo.app.platform.currentTimeMillis

private const val MaxUserRemarkLength = 24
private const val MaxReadLaterItems = 200

/**
 * 归一化用户备注：压平换行、去首尾空白并截断至 24 字，空白串返回空表示清除备注。
 */
fun normalizeUserRemark(raw: String?): String {
    val flattened = raw.orEmpty()
        .replace('\r', ' ')
        .replace('\n', ' ')
        .trim()
    if (flattened.isEmpty()) return ""
    return if (flattened.length <= MaxUserRemarkLength) flattened else flattened.take(MaxUserRemarkLength)
}

fun encodeUserRemarks(remarks: Map<String, String>): JSONObject {
    val obj = JSONObject()
    remarks.forEach { (uid, rawRemark) ->
        val cleanUid = uid.trim()
        val cleanRemark = normalizeUserRemark(rawRemark)
        if (cleanUid.isNotEmpty() && cleanRemark.isNotEmpty()) {
            obj.put(cleanUid, cleanRemark)
        }
    }
    return obj
}

fun decodeUserRemarks(json: JSONObject?): Map<String, String> {
    if (json == null) return emptyMap()
    val out = LinkedHashMap<String, String>()
    val keys = json.keys()
    while (keys.hasNext()) {
        val uid = keys.next().trim()
        if (uid.isEmpty()) continue
        val remark = normalizeUserRemark(json.optString(uid, ""))
        if (remark.isNotEmpty()) {
            out[uid] = remark
        }
    }
    return out
}

/**
 * 本地自定义用户备注（按 uid 索引），只保存在本机 `user-remarks.json`。
 * 任何列表、详情或主页改完备注后，已挂载的所有界面立刻响应刷新。
 */
object UserRemarks {
    private val remarks = mutableStateMapOf<String, String>()
    private var store: Store? = null

    fun attach(store: Store) {
        this.store = store
        remarks.clear()
        remarks.putAll(store.userRemarks())
    }

    operator fun get(uid: String?): String? =
        uid?.trim()?.takeIf { it.isNotEmpty() }?.let { remarks[it] }

    fun displayName(uid: String?, originalName: String): String =
        get(uid)?.takeIf { it.isNotBlank() } ?: originalName

    fun set(uid: String, rawRemark: String?) {
        val cleanUid = uid.trim()
        if (cleanUid.isEmpty()) return
        val normalized = normalizeUserRemark(rawRemark)
        if (normalized.isEmpty()) {
            remarks.remove(cleanUid)
        } else {
            remarks[cleanUid] = normalized
        }
        store?.saveUserRemarks(remarks.toMap())
    }
}

data class ReadLaterEntry(
    val status: Status,
    val savedAt: Long,
    val read: Boolean = false,
)

enum class ReadLaterFilter(val label: String) {
    All("全部"),
    Unread("未读"),
    Read("已读"),
    ;
}

fun encodeReadLaterEntries(entries: List<ReadLaterEntry>): JSONArray {
    val arr = JSONArray()
    val seenIds = HashSet<String>()
    for (entry in entries) {
        if (entry.status.id.isBlank()) continue
        if (!seenIds.add(entry.status.id)) continue
        arr.put(
            JSONObject()
                .put("savedAt", entry.savedAt)
                .put("read", entry.read)
                .put("status", statusToSnapshot(entry.status)),
        )
        if (arr.length() >= MaxReadLaterItems) break
    }
    return arr
}

fun decodeReadLaterEntries(arr: JSONArray?): List<ReadLaterEntry> {
    if (arr == null || arr.length() == 0) return emptyList()
    val out = ArrayList<ReadLaterEntry>(arr.length().coerceAtMost(MaxReadLaterItems))
    val seenIds = HashSet<String>()
    for (i in 0 until arr.length()) {
        val item = arr.optJSONObject(i) ?: continue
        val status = statusFromSnapshot(item.optJSONObject("status")) ?: continue
        if (!seenIds.add(status.id)) continue
        val savedAt = item.optLong("savedAt", 0L).coerceAtLeast(0L)
        val read = item.optBoolean("read", false)
        out.add(ReadLaterEntry(status = status, savedAt = savedAt, read = read))
        if (out.size >= MaxReadLaterItems) break
    }
    return out
}

fun filterReadLaterEntries(
    entries: List<ReadLaterEntry>,
    filter: ReadLaterFilter,
    query: String,
    remarkProvider: (String?) -> String? = { UserRemarks[it] },
): List<ReadLaterEntry> {
    val keyword = query.trim()
    return entries.filter { entry ->
        val passTab = when (filter) {
            ReadLaterFilter.All -> true
            ReadLaterFilter.Unread -> !entry.read
            ReadLaterFilter.Read -> entry.read
        }
        if (!passTab) return@filter false
        if (keyword.isEmpty()) return@filter true

        val s = entry.status
        if (s.plainText.contains(keyword, ignoreCase = true)) return@filter true
        val author = s.user
        if (author != null) {
            if (author.name.contains(keyword, ignoreCase = true)) return@filter true
            if (remarkProvider(author.id)?.contains(keyword, ignoreCase = true) == true) return@filter true
        }
        val quoted = s.retweeted ?: return@filter false
        if (quoted.plainText.contains(keyword, ignoreCase = true)) return@filter true
        val qAuthor = quoted.user ?: return@filter false
        qAuthor.name.contains(keyword, ignoreCase = true) ||
            remarkProvider(qAuthor.id)?.contains(keyword, ignoreCase = true) == true
    }
}

/**
 * 稍后再看离线快照存储：保存完整 `Status` 结构到本地 `read-later.json`，弱网/离线时可直接从详情页阅读原文。
 */
object ReadLater {
    private val entries = mutableStateListOf<ReadLaterEntry>()
    private var store: Store? = null

    fun attach(store: Store) {
        this.store = store
        entries.clear()
        entries.addAll(store.readLaterEntries())
    }

    fun all(): List<ReadLaterEntry> = entries.toList()

    fun contains(statusId: String?): Boolean {
        if (statusId.isNullOrBlank()) return false
        return entries.any { it.status.id == statusId }
    }

    fun find(id: String, mid: String = ""): ReadLaterEntry? =
        entries.firstOrNull { entry ->
            entry.status.id == id || (mid.isNotBlank() && entry.status.mid == mid)
        }

    fun unreadCount(): Int = entries.count { !it.read }

    fun toggle(status: Status, nowMs: Long = currentTimeMillis()): Boolean {
        if (status.id.isBlank()) return false
        val idx = entries.indexOfFirst { it.status.id == status.id }
        return if (idx >= 0) {
            entries.removeAt(idx)
            persist()
            false
        } else {
            entries.add(0, ReadLaterEntry(status = status, savedAt = nowMs, read = false))
            if (entries.size > MaxReadLaterItems) {
                entries.removeRange(MaxReadLaterItems, entries.size)
            }
            persist()
            true
        }
    }

    fun setRead(statusId: String, read: Boolean) {
        if (statusId.isBlank()) return
        val idx = entries.indexOfFirst { it.status.id == statusId }
        if (idx < 0) return
        val current = entries[idx]
        if (current.read == read) return
        entries[idx] = current.copy(read = read)
        persist()
    }

    fun remove(statusId: String) {
        if (statusId.isBlank()) return
        val changed = entries.removeAll { it.status.id == statusId }
        if (changed) persist()
    }

    fun clearRead() {
        val changed = entries.removeAll { it.read }
        if (changed) persist()
    }

    fun clearAll() {
        if (entries.isEmpty()) return
        entries.clear()
        persist()
    }

    private fun persist() {
        store?.saveReadLaterEntries(entries.toList())
    }
}
