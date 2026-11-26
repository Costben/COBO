package com.cobo.app.data

import android.text.Html
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * 静音用户的作用模式，只保存在本机 `content-filters.json`。
 */
enum class MuteUserMode(val key: String, val label: String) {
    /** 隐藏该用户的全部微博（原创与转发）；在开启「应用于评论区」时同步隐藏其评论。 */
    All("all", "静音全部"),

    /** 仅隐藏该用户发出的转发微博，保留其原创微博与评论。 */
    RepostsOnly("reposts_only", "仅屏蔽转发"),
    ;

    companion object {
        fun of(key: String?): MuteUserMode? = entries.firstOrNull { it.key == key }
    }
}

/**
 * 过滤引擎校验的目标页面类别，对应管理页里的三个作用域开关。
 */
enum class FilterSurface {
    Feed,
    SearchAndHot,
    Comment,
}

data class KeywordRule(
    val pattern: String,
    val isRegex: Boolean = false,
)

data class MutedUser(
    val uid: String,
    val name: String = "",
    val mode: MuteUserMode = MuteUserMode.All,
)

data class ContentFilterRules(
    val keywords: List<KeywordRule> = emptyList(),
    val topics: List<String> = emptyList(),
    val mutedUsers: List<MutedUser> = emptyList(),
    val applyToFeeds: Boolean = true,
    val applyToSearchAndHot: Boolean = true,
    val applyToComments: Boolean = true,
) {
    val hasAnyRule: Boolean
        get() = keywords.isNotEmpty() || topics.isNotEmpty() || mutedUsers.isNotEmpty()

    val totalRuleCount: Int
        get() = keywords.size + topics.size + mutedUsers.size
}

private val TopicHashRegex = Regex("""[#＃]([^#＃\r\n]{1,60})[#＃]""")
private val HtmlTagRegex = Regex("""<[^>]+>""")
private val HtmlBrRegex = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
private val MultiSpaceRegex = Regex("""[ \t\u00A0]+""")
private val CompiledRegexCache = ConcurrentHashMap<String, Result<Regex>>()

/**
 * 统一清理话题规则首尾的 `#` / `＃` 与空白。
 */
fun normalizeTopicRule(raw: String): String =
    raw.trim().trim { it == '#' || it == '＃' || it.isWhitespace() }

/**
 * 校验关键词输入是否合法；返回错误信息，合法时返回 `null`。
 */
fun validateKeywordRule(rawPattern: String, isRegex: Boolean): String? {
    val pattern = rawPattern.trim()
    if (pattern.isEmpty()) return "请输入关键词"
    if (isRegex) {
        val result = compileCaseInsensitiveRegex(pattern)
        if (result.isFailure) {
            return "正则表达式格式有误"
        }
    }
    return null
}

private fun compileCaseInsensitiveRegex(pattern: String): Result<Regex> =
    CompiledRegexCache.getOrPut(pattern) {
        runCatching { Regex(pattern, RegexOption.IGNORE_CASE) }
    }

/**
 * 提取适合关键词/话题匹配的纯文本，同时兼容 Android 运行时与无 Robolectric 的 JVM 单元测试。
 */
internal fun filterPlainText(html: String?): String {
    if (html.isNullOrBlank()) return ""
    val fromAndroid = runCatching {
        @Suppress("USELESS_ELVIS")
        Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)?.toString()
    }.getOrNull()
    val raw = if (!fromAndroid.isNullOrBlank()) {
        fromAndroid
    } else {
        html.replace(HtmlBrRegex, "\n")
            .replace(HtmlTagRegex, "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
    }
    return raw
        .replace("\uFFFC", "")
        .replace("\u200B", "")
        .replace(MultiSpaceRegex, " ")
        .trim()
}

private fun extractTopicsFromText(text: String): List<String> {
    if (text.isBlank()) return emptyList()
    val seen = LinkedHashSet<String>()
    val result = ArrayList<String>()
    for (match in TopicHashRegex.findAll(text)) {
        val topic = normalizeTopicRule(match.groupValues[1])
        if (topic.isEmpty()) continue
        val key = topic.lowercase()
        if (seen.add(key)) result.add(topic)
    }
    return result
}

/**
 * 从微博正文及引用原博中提取去重后的 `#话题#` 列表（保留出现顺序）。
 */
fun extractTopics(status: Status): List<String> {
    val seen = LinkedHashSet<String>()
    val result = ArrayList<String>()
    for (sourceHtml in listOf(status.textHtml, status.retweeted?.textHtml)) {
        for (topic in extractTopicsFromText(filterPlainText(sourceHtml))) {
            val key = topic.lowercase()
            if (seen.add(key)) result.add(topic)
        }
    }
    return result
}

private fun matchesKeywordList(text: String, keywords: List<KeywordRule>): Boolean {
    if (text.isBlank() || keywords.isEmpty()) return false
    for (rule in keywords) {
        val pattern = rule.pattern
        if (pattern.isEmpty()) continue
        if (rule.isRegex) {
            val regex = compileCaseInsensitiveRegex(pattern).getOrNull() ?: continue
            if (regex.containsMatchIn(text)) return true
        } else {
            if (text.contains(pattern, ignoreCase = true)) return true
        }
    }
    return false
}

private fun matchesTopicInStatusText(
    text: String,
    extractedTopics: List<String>,
    topics: List<String>,
): Boolean {
    if (topics.isEmpty() || (text.isBlank() && extractedTopics.isEmpty())) return false
    for (ruleTopic in topics) {
        if (ruleTopic.isEmpty()) continue
        if (text.contains("#$ruleTopic#", ignoreCase = true) ||
            text.contains("＃$ruleTopic＃", ignoreCase = true)
        ) {
            return true
        }
        val matchedExtracted = extractedTopics.any { found ->
            found.equals(ruleTopic, ignoreCase = true) ||
                found.removeSuffix("[超话]").trim().equals(ruleTopic, ignoreCase = true)
        }
        if (matchedExtracted) return true
    }
    return false
}

private fun matchesTopicInPlainTitle(title: String, topics: List<String>): Boolean {
    if (title.isBlank() || topics.isEmpty()) return false
    return topics.any { topic -> topic.isNotEmpty() && title.contains(topic, ignoreCase = true) }
}

private fun isSurfaceEnabled(rules: ContentFilterRules, surface: FilterSurface): Boolean = when (surface) {
    FilterSurface.Feed -> rules.applyToFeeds
    FilterSurface.SearchAndHot -> rules.applyToSearchAndHot
    FilterSurface.Comment -> rules.applyToComments
}

/**
 * 判断一条微博是否应被本地屏蔽。
 *
 * 规则：
 * 1. 本人发布的微博永不隐藏，避免误屏蔽自己。
 * 2. 作者被「静音全部」时直接隐藏；被「仅屏蔽转发」时仅在该微博含转发原博时隐藏。
 * 3. 转发微博级联检查原博：原博作者被「静音全部」或原博正文命中关键词/话题时，整条转发一并隐藏。
 */
fun shouldFilterStatus(
    status: Status,
    rules: ContentFilterRules,
    surface: FilterSurface,
    selfUid: String? = null,
): Boolean {
    if (!rules.hasAnyRule || !isSurfaceEnabled(rules, surface)) return false
    val authorId = status.user?.id.orEmpty()
    if (!selfUid.isNullOrBlank() && authorId == selfUid) return false

    if (authorId.isNotBlank() && rules.mutedUsers.isNotEmpty()) {
        val authorRule = rules.mutedUsers.firstOrNull { it.uid == authorId }
        if (authorRule != null) {
            when (authorRule.mode) {
                MuteUserMode.All -> return true
                MuteUserMode.RepostsOnly -> if (status.retweeted != null) return true
            }
        }
    }

    val quoted = status.retweeted
    if (quoted != null) {
        val quotedAuthorId = quoted.user?.id.orEmpty()
        if (quotedAuthorId.isNotBlank() &&
            (selfUid.isNullOrBlank() || quotedAuthorId != selfUid) &&
            rules.mutedUsers.any { it.uid == quotedAuthorId && it.mode == MuteUserMode.All }
        ) {
            return true
        }
    }

    if (rules.keywords.isNotEmpty() || rules.topics.isNotEmpty()) {
        val mainText = filterPlainText(status.textHtml)
        val quotedText = filterPlainText(quoted?.textHtml)
        if (matchesKeywordList(mainText, rules.keywords) || matchesKeywordList(quotedText, rules.keywords)) {
            return true
        }
        if (rules.topics.isNotEmpty()) {
            val mainTopics = extractTopicsFromText(mainText)
            if (matchesTopicInStatusText(mainText, mainTopics, rules.topics)) return true
            if (quotedText.isNotBlank()) {
                val quotedTopics = extractTopicsFromText(quotedText)
                if (matchesTopicInStatusText(quotedText, quotedTopics, rules.topics)) return true
            }
        }
    }
    return false
}

/**
 * 判断一条评论或楼中楼回复是否应被本地屏蔽（受「应用于评论区」开关控制）。
 * 被设为「静音全部」的用户评论会同步隐藏；「仅屏蔽转发」不影响评论区；自己的评论始终保留。
 */
fun shouldFilterComment(
    comment: Comment,
    rules: ContentFilterRules,
    selfUid: String? = null,
): Boolean {
    if (!rules.applyToComments || !rules.hasAnyRule) return false
    val authorId = comment.user?.id.orEmpty()
    if (!selfUid.isNullOrBlank() && authorId == selfUid) return false
    if (authorId.isNotBlank() &&
        rules.mutedUsers.any { it.uid == authorId && it.mode == MuteUserMode.All }
    ) {
        return true
    }
    if (rules.keywords.isNotEmpty() || rules.topics.isNotEmpty()) {
        val text = filterPlainText(comment.textHtml)
        if (matchesKeywordList(text, rules.keywords)) return true
        if (rules.topics.isNotEmpty()) {
            val extracted = extractTopicsFromText(text)
            if (matchesTopicInStatusText(text, extracted, rules.topics)) return true
        }
    }
    return false
}

/**
 * 判断热搜榜条目是否应被本地屏蔽（受「应用于搜索与热搜」开关控制）。
 */
fun shouldFilterHotItem(
    item: HotItem,
    rules: ContentFilterRules,
): Boolean {
    if (!rules.applyToSearchAndHot || !rules.hasAnyRule) return false
    if (matchesKeywordList(item.title, rules.keywords)) return true
    return matchesTopicInPlainTitle(item.title, rules.topics)
}

fun encodeContentFilterRules(rules: ContentFilterRules): JSONObject {
    val keywordsJson = JSONArray()
    rules.keywords.forEach { k ->
        if (k.pattern.isNotBlank()) {
            keywordsJson.put(
                JSONObject()
                    .put("pattern", k.pattern)
                    .put("isRegex", k.isRegex),
            )
        }
    }
    val topicsJson = JSONArray()
    rules.topics.forEach { t ->
        val normalized = normalizeTopicRule(t)
        if (normalized.isNotEmpty()) topicsJson.put(normalized)
    }
    val usersJson = JSONArray()
    rules.mutedUsers.forEach { u ->
        if (u.uid.isNotBlank()) {
            usersJson.put(
                JSONObject()
                    .put("uid", u.uid)
                    .put("name", u.name)
                    .put("mode", u.mode.key),
            )
        }
    }
    return JSONObject()
        .put("version", 1)
        .put("keywords", keywordsJson)
        .put("topics", topicsJson)
        .put("mutedUsers", usersJson)
        .put("applyToFeeds", rules.applyToFeeds)
        .put("applyToSearchAndHot", rules.applyToSearchAndHot)
        .put("applyToComments", rules.applyToComments)
}

fun decodeContentFilterRules(json: JSONObject?): ContentFilterRules {
    if (json == null) return ContentFilterRules()

    val keywordSeen = HashSet<Pair<String, Boolean>>()
    val keywords = buildList<KeywordRule> {
        val arr = json.optJSONArray("keywords") ?: return@buildList
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val pattern = item.optString("pattern", "").trim()
            if (pattern.isEmpty()) continue
            val isRegex = item.optBoolean("isRegex", false)
            val dedupKey = (if (isRegex) pattern else pattern.lowercase()) to isRegex
            if (keywordSeen.add(dedupKey)) {
                add(KeywordRule(pattern = pattern, isRegex = isRegex))
            }
        }
    }

    val topicSeen = HashSet<String>()
    val topics = buildList<String> {
        val arr = json.optJSONArray("topics") ?: return@buildList
        for (i in 0 until arr.length()) {
            val normalized = normalizeTopicRule(arr.optString(i, ""))
            if (normalized.isEmpty()) continue
            if (topicSeen.add(normalized.lowercase())) {
                add(normalized)
            }
        }
    }

    val userMap = LinkedHashMap<String, MutedUser>()
    json.optJSONArray("mutedUsers")?.let { arr ->
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val uid = item.optString("uid", "").trim()
            if (uid.isEmpty()) continue
            val name = item.optString("name", "").trim()
            val mode = MuteUserMode.of(item.optString("mode", "")) ?: MuteUserMode.All
            userMap[uid] = MutedUser(uid = uid, name = name, mode = mode)
        }
    }

    return ContentFilterRules(
        keywords = keywords,
        topics = topics,
        mutedUsers = userMap.values.toList(),
        applyToFeeds = json.optBoolean("applyToFeeds", true),
        applyToSearchAndHot = json.optBoolean("applyToSearchAndHot", true),
        applyToComments = json.optBoolean("applyToComments", true),
    )
}

fun formatContentFilterSummary(rules: ContentFilterRules): String {
    if (!rules.hasAnyRule) return "按关键词、话题或用户隐藏不想看的内容"
    val parts = buildList {
        if (rules.keywords.isNotEmpty()) add("${rules.keywords.size} 条关键词")
        if (rules.topics.isNotEmpty()) add("${rules.topics.size} 个话题")
        if (rules.mutedUsers.isNotEmpty()) add("${rules.mutedUsers.size} 位用户")
    }
    return "已启用 ${parts.joinToString("、")}"
}

/**
 * 进程内全局屏蔽规则状态容器：变更立即反映到所有已打开的信息流、热搜与详情页评论。
 */
object ContentFilters {
    var rules by mutableStateOf(ContentFilterRules())
        private set

    private var store: Store? = null

    fun attach(store: Store) {
        this.store = store
        rules = store.contentFilterRules()
    }

    private fun update(transform: (ContentFilterRules) -> ContentFilterRules) {
        val next = transform(rules)
        rules = next
        store?.saveContentFilterRules(next)
    }

    fun muteMode(uid: String?): MuteUserMode? {
        if (uid.isNullOrBlank()) return null
        return rules.mutedUsers.firstOrNull { it.uid == uid }?.mode
    }

    fun hasTopic(rawTopic: String): Boolean {
        val normalized = normalizeTopicRule(rawTopic)
        if (normalized.isEmpty()) return false
        return rules.topics.any { it.equals(normalized, ignoreCase = true) }
    }

    fun addKeyword(rawPattern: String, isRegex: Boolean): String? {
        val error = validateKeywordRule(rawPattern, isRegex)
        if (error != null) return error
        val pattern = rawPattern.trim()
        val exists = rules.keywords.any { existing ->
            existing.isRegex == isRegex && if (isRegex) {
                existing.pattern == pattern
            } else {
                existing.pattern.equals(pattern, ignoreCase = true)
            }
        }
        if (exists) return "该关键词规则已存在"
        update { current ->
            current.copy(keywords = listOf(KeywordRule(pattern, isRegex)) + current.keywords)
        }
        return null
    }

    fun removeKeyword(rule: KeywordRule) {
        update { current ->
            current.copy(keywords = current.keywords.filterNot { it == rule })
        }
    }

    fun addTopic(rawTopic: String): String? {
        val normalized = normalizeTopicRule(rawTopic)
        if (normalized.isEmpty()) return "请输入话题名称"
        if (rules.topics.any { it.equals(normalized, ignoreCase = true) }) {
            return "该话题已在屏蔽列表"
        }
        update { current ->
            current.copy(topics = listOf(normalized) + current.topics)
        }
        return null
    }

    fun removeTopic(topic: String) {
        update { current ->
            current.copy(topics = current.topics.filterNot { it.equals(topic, ignoreCase = true) })
        }
    }

    fun setMutedUser(uid: String, name: String, mode: MuteUserMode?) {
        val cleanUid = uid.trim()
        if (cleanUid.isEmpty()) return
        val cleanName = name.trim()
        update { current ->
            if (mode == null) {
                current.copy(mutedUsers = current.mutedUsers.filterNot { it.uid == cleanUid })
            } else {
                val existing = current.mutedUsers.firstOrNull { it.uid == cleanUid }
                val finalName = cleanName.ifBlank { existing?.name.orEmpty() }
                val entry = MutedUser(uid = cleanUid, name = finalName, mode = mode)
                current.copy(
                    mutedUsers = listOf(entry) + current.mutedUsers.filterNot { it.uid == cleanUid },
                )
            }
        }
    }

    fun updateScopes(
        applyToFeeds: Boolean = rules.applyToFeeds,
        applyToSearchAndHot: Boolean = rules.applyToSearchAndHot,
        applyToComments: Boolean = rules.applyToComments,
    ) {
        update { current ->
            current.copy(
                applyToFeeds = applyToFeeds,
                applyToSearchAndHot = applyToSearchAndHot,
                applyToComments = applyToComments,
            )
        }
    }

    fun clearAll() {
        update { current ->
            current.copy(
                keywords = emptyList(),
                topics = emptyList(),
                mutedUsers = emptyList(),
            )
        }
    }
}
