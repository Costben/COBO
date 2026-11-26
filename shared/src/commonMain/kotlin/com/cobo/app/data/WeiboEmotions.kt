package com.cobo.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import com.cobo.app.json.JSONObject
import com.cobo.app.platform.PlatformContext
import com.cobo.app.platform.PlatformHttp
import com.cobo.app.platform.PlatformPrefs
import com.cobo.app.platform.SynchronizedMap
import com.cobo.app.platform.SynchronizedSet
import com.cobo.app.platform.decodeImageBitmap
import com.cobo.app.platform.ioDispatcher
import com.cobo.app.platform.readBundledAssetText
import kotlinx.coroutines.withContext

data class WeiboEmoji(
    val phrase: String,
    val url: String = "",
)

data class EmojiSection(
    val label: String,
    val items: List<WeiboEmoji>,
)

object WeiboEmojiBitmaps {
    private val urls = SynchronizedMap<String, String>()
    private val bitmaps = SynchronizedMap<String, ImageBitmap>()
    private val failed = SynchronizedSet<String>()
    private var prefs: PlatformPrefs? = null

    var version by mutableIntStateOf(0)
        private set

    fun attach(@Suppress("UNUSED_PARAMETER") context: PlatformContext = PlatformContext()) {
        if (prefs != null) return
        val store = PlatformPrefs("weibo_emoji_urls")
        prefs = store
        runCatching {
            val json = readBundledAssetText("weibo_emotions.json") ?: return@runCatching
            val obj = JSONObject(json)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val phrase = keys.next()
                val url = obj.optString(phrase, "")
                if (phrase.isNotBlank() && url.isNotBlank()) urls[phrase] = url
            }
        }
        store.allStrings().forEach { (phrase, value) ->
            if (value.isNotBlank()) urls[phrase] = value
        }
        version++
    }

    private val pending = LinkedHashMap<String, String>()

    fun register(phrase: String, url: String, notify: Boolean = true) {
        if (phrase.isBlank() || url.isBlank()) return
        val normalized = when {
            url.startsWith("//") -> "https:$url"
            url.startsWith("http://") -> "https://" + url.removePrefix("http://")
            else -> url
        }
        if (urls[phrase] == normalized) return
        urls[phrase] = normalized
        pending[phrase] = normalized
        if (notify) flush()
    }

    fun flush() {
        if (pending.isEmpty()) return
        prefs?.edit()?.apply {
            pending.forEach { (phrase, url) -> putString(phrase, url) }
        }?.apply()
        pending.clear()
        version++
    }

    fun urlOf(phrase: String): String? = urls[phrase]

    fun getCached(phrase: String): ImageBitmap? = bitmaps[phrase]

    fun hasUrl(phrase: String): Boolean = urls.containsKey(phrase)

    suspend fun ensureLoaded(phrase: String): Boolean {
        if (bitmaps.containsKey(phrase) || phrase in failed) return false
        val url = urls[phrase] ?: return false
        val decoded = withContext(ioDispatcher) {
            runCatching {
                val bytes = PlatformHttp.downloadBytes(
                    url = url,
                    headers = mapOf(
                        "User-Agent" to Api.WEB_UA,
                        "Referer" to "https://weibo.com/",
                    ),
                ) ?: return@runCatching null
                decodeImageBitmap(bytes)
            }.getOrNull()
        }
        if (decoded != null) {
            bitmaps[phrase] = decoded
            return true
        }
        failed.add(phrase)
        return false
    }
}

private val FallbackWeiboPhrases = listOf(
    "[微笑]", "[嘻嘻]", "[哈哈]", "[可爱]", "[可怜]", "[挖鼻]", "[吃惊]",
    "[害羞]", "[挤眼]", "[闭嘴]", "[鄙视]", "[爱你]", "[泪]", "[偷笑]",
    "[亲亲]", "[生病]", "[太开心]", "[白眼]", "[右哼哼]", "[左哼哼]", "[嘘]",
    "[衰]", "[委屈]", "[吐]", "[哈欠]", "[抱抱]", "[怒]", "[疑问]",
    "[馋嘴]", "[拜拜]", "[思考]", "[汗]", "[困]", "[睡]", "[钱]",
    "[失望]", "[酷]", "[色]", "[哼]", "[鼓掌]", "[晕]", "[悲伤]",
    "[抓狂]", "[黑线]", "[阴险]", "[怒骂]", "[互粉]", "[心]", "[伤心]",
    "[猪头]", "[熊猫]", "[兔子]", "[ok]", "[耶]", "[good]", "[NO]",
    "[赞]", "[来]", "[弱]", "[囧]", "[浮云]", "[给力]", "[围观]",
    "[威武]", "[礼物]", "[蜡烛]", "[蛋糕]", "[doge]", "[二哈]", "[允悲]",
    "[笑cry]", "[摊手]", "[抱拳]", "[吃瓜]", "[打call]", "[酸]", "[苦涩]",
    "[破防]", "[裂开]", "[求饶]", "[老师好]", "[打脸]", "[跪了]", "[并不简单]",
)

private val FallbackUnicodeEmojis = listOf(
    "😀", "😁", "😂", "🤣", "😃", "😄", "😅", "😆", "😉", "😊",
    "😋", "😎", "😍", "😘", "😗", "😙", "😚", "🙂", "🤗", "🤩",
    "🤔", "🤨", "😐", "😑", "😶", "🙄", "😏", "😣", "😥", "😮",
    "🤐", "😯", "😪", "😫", "🥱", "😴", "😌", "😛", "😜", "😝",
    "🤤", "😒", "😓", "😔", "😕", "🙃", "🤑", "😲", "🙁", "😖",
    "😞", "😟", "😤", "😢", "😭", "😦", "😧", "😨", "😩", "🤯",
    "😬", "😰", "😱", "🥵", "🥶", "😳", "🤪", "😵", "🥴", "😠",
    "😡", "🤬", "😷", "🤒", "🤕", "🤢", "🤮", "🤧", "😇", "🥳",
    "🥺", "🤠", "🤡", "🤥", "🤫", "🤭", "🧐", "🤓", "😈", "👿",
    "💀", "👻", "👽", "🤖", "💩", "😺", "😸", "😹", "😻", "😼",
)

val FallbackWeiboEmojiSections: List<EmojiSection> = listOf(
    EmojiSection("常用微博", FallbackWeiboPhrases.map { WeiboEmoji(it) }),
    EmojiSection("Emoji", FallbackUnicodeEmojis.map { WeiboEmoji(it) }),
)

fun parseWeiboEmotions(root: JSONObject?): List<EmojiSection> {
    val zhCn = root?.optJSONObject("data")
        ?.optJSONObject("emoticon")
        ?.optJSONObject("ZH_CN")
        ?: return emptyList()
    val sections = ArrayList<EmojiSection>()
    val keys = zhCn.keys()
    while (keys.hasNext()) {
        val label = keys.next().trim()
        val arr = zhCn.optJSONArray(label) ?: continue
        val seen = HashSet<String>()
        val items = ArrayList<WeiboEmoji>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val phrase = obj.optString("phrase", "").trim()
            if (phrase.isBlank() || !seen.add(phrase)) continue
            val rawUrl = obj.optString("url", "").trim()
            val url = when {
                rawUrl.startsWith("//") -> "https:$rawUrl"
                rawUrl.startsWith("http://") -> "https://" + rawUrl.removePrefix("http://")
                else -> rawUrl
            }
            WeiboEmojiBitmaps.register(phrase, url, notify = false)
            items.add(WeiboEmoji(phrase = phrase, url = url))
        }
        if (items.isNotEmpty()) {
            val displayLabel = when (label) {
                "PC热门表情" -> "热门"
                else -> label
            }
            sections.add(EmojiSection(label = displayLabel, items = items))
        }
    }
    if (sections.isNotEmpty()) {
        sections.add(EmojiSection("Emoji", FallbackUnicodeEmojis.map { WeiboEmoji(it) }))
    }
    WeiboEmojiBitmaps.flush()
    return sections
}
