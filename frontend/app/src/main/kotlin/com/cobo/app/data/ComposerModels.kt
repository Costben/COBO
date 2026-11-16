package com.cobo.app.data

import org.json.JSONObject

enum class WeiboVisibility(
    val code: Int,
    val label: String,
    val summary: String,
) {
    Public(0, "公开", "所有人可见"),
    Circle(6, "好友圈", "仅互关好友可见"),
    Self(1, "私密", "仅自己可见"),
}

data class TopicSuggestion(
    val title: String,
    val desc: String = "",
    val icon: String = "",
)

sealed interface ComposerTrigger {
    val replaceStart: Int
    val replaceEnd: Int
    val query: String

    data class Mention(
        override val replaceStart: Int,
        override val replaceEnd: Int,
        override val query: String,
    ) : ComposerTrigger

    data class Topic(
        override val replaceStart: Int,
        override val replaceEnd: Int,
        override val query: String,
        val hasClosingHash: Boolean,
    ) : ComposerTrigger
}

fun detectComposerTrigger(text: String, cursor: Int): ComposerTrigger? {
    val safeCursor = cursor.coerceIn(0, text.length)
    if (safeCursor == 0) return null

    var i = safeCursor - 1
    while (i >= 0) {
        val ch = text[i]
        if (ch == '@') {
            val query = text.substring(i + 1, safeCursor)
            if (query.length <= 30 && !query.any { it.isWhitespace() }) {
                return ComposerTrigger.Mention(
                    replaceStart = i,
                    replaceEnd = safeCursor,
                    query = query,
                )
            }
            break
        }
        if (ch.isWhitespace() || ch == '#' || ch == '[' || ch == ']') break
        if (safeCursor - i > 30) break
        i--
    }

    val lineStart = text.lastIndexOf('\n', safeCursor - 1).let { if (it < 0) 0 else it + 1 }
    var hashCountBefore = 0
    var lastHashIndex = -1
    for (pos in lineStart until safeCursor) {
        if (text[pos] == '#') {
            hashCountBefore++
            lastHashIndex = pos
        }
    }
    if (hashCountBefore % 2 == 1 && lastHashIndex >= 0) {
        val query = text.substring(lastHashIndex + 1, safeCursor)
        if (query.length <= 40 && !query.contains('@')) {
            val hasClosingHash = safeCursor < text.length && text[safeCursor] == '#'
            val replaceEnd = if (hasClosingHash) safeCursor + 1 else safeCursor
            return ComposerTrigger.Topic(
                replaceStart = lastHashIndex,
                replaceEnd = replaceEnd,
                query = query,
                hasClosingHash = hasClosingHash,
            )
        }
    }

    return null
}

fun applyComposerReplacement(
    text: String,
    trigger: ComposerTrigger,
    chosenValue: String,
): Pair<String, Int> {
    val start = trigger.replaceStart.coerceIn(0, text.length)
    val end = trigger.replaceEnd.coerceIn(start, text.length)
    val token = when (trigger) {
        is ComposerTrigger.Mention -> "@${chosenValue.trim().removePrefix("@")} "
        is ComposerTrigger.Topic -> "#${chosenValue.trim().trim('#')}# "
    }
    val next = buildString {
        append(text, 0, start)
        append(token)
        append(text, end, text.length)
    }
    return next to (start + token.length)
}

fun parseHotspotTopics(root: JSONObject?): List<TopicSuggestion> {
    if (root == null || root.optInt("ok", 0) != 1) return emptyList()
    val arr = root.optJSONArray("data") ?: return emptyList()
    val seen = HashSet<String>()
    val out = ArrayList<TopicSuggestion>(arr.length())
    for (i in 0 until arr.length()) {
        val obj = arr.optJSONObject(i) ?: continue
        val title = obj.optString("title", "").trim().trim('#')
            .ifBlank { obj.optString("content", "").trim().trim('#') }
        if (title.isBlank() || !seen.add(title)) continue
        val desc = obj.optString("description", "").trim()
            .ifBlank { obj.optString("category_name", "").trim() }
        val icon = obj.optString("image", "").trim()
            .ifBlank { obj.optString("icon", "").trim() }
        out.add(TopicSuggestion(title = title, desc = desc, icon = icon))
    }
    return out
}
