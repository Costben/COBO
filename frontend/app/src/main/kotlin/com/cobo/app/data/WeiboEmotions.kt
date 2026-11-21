package com.cobo.app.data

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.cobo.app.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

data class WeiboEmoji(
    val phrase: String,
    val url: String = "",
)

data class EmojiSection(
    val label: String,
    val items: List<WeiboEmoji>,
)

object WeiboEmojiBitmaps {
    private val urls = ConcurrentHashMap<String, String>()
    private val bitmaps = ConcurrentHashMap<String, Bitmap>()
    private val failed = ConcurrentHashMap.newKeySet<String>()
    private var prefs: SharedPreferences? = null

    /** 表情地址表的版本：表一变，正文里那些 `[表情]` 就得重新算一遍。 */
    var version by mutableIntStateOf(0)
        private set

    /**
     * 装上内置表情表，再把之前从接口学到的地址补回来。
     *
     * 客户端通道（api.weibo.com）返回的正文里表情是 `[笑而不语]` 这种纯文字，不带图，
     * 全靠这张表还原；H5 通道本来就带 `<img>`，表只是兜底。
     */
    fun attach(context: Context) {
        if (prefs != null) return
        prefs = context.getSharedPreferences("weibo_emoji_urls", Context.MODE_PRIVATE)
        runCatching {
            val json = context.assets.open("weibo_emotions.json")
                .bufferedReader()
                .use { it.readText() }
            val obj = JSONObject(json)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val phrase = keys.next()
                val url = obj.optString(phrase, "")
                if (phrase.isNotBlank() && url.isNotBlank()) urls[phrase] = url
            }
        }
        prefs?.all?.forEach { (phrase, value) ->
            if (value is String && value.isNotBlank()) urls[phrase] = value
        }
        version++
    }

    private val pending = LinkedHashMap<String, String>()

    /**
     * 记下一个表情地址。
     *
     * [notify] 为 false 时只改内存，等这一批灌完由 [flush] 一次通知。接口一次回几百个表情，
     * 逐条自增版本会让整屏富文本跟着重组几百次，逐条写盘也是几百次 apply。
     */
    fun register(phrase: String, url: String, notify: Boolean = true) {
        if (phrase.isBlank() || url.isBlank()) return
        val normalized = when {
            url.startsWith("//") -> "https:$url"
            url.startsWith("http://") -> "https://" + url.removePrefix("http://")
            else -> url
        }
        if (urls.put(phrase, normalized) == normalized) return
        pending[phrase] = normalized
        if (notify) flush()
    }

    /** 这批新地址收尾：版本只涨一次，磁盘也只写一次。 */
    fun flush() {
        if (pending.isEmpty()) return
        prefs?.edit()?.apply {
            pending.forEach { (phrase, url) -> putString(phrase, url) }
        }?.apply()
        pending.clear()
        version++
    }

    /** 正文里这个 `[表情]` 对应的图片地址，不认识就是 null。 */
    fun urlOf(phrase: String): String? = urls[phrase]

    fun getCached(phrase: String): Bitmap? = bitmaps[phrase]

    fun hasUrl(phrase: String): Boolean = urls.containsKey(phrase)

    suspend fun ensureLoaded(phrase: String): Boolean {
        if (bitmaps.containsKey(phrase) || phrase in failed) return false
        val url = urls[phrase] ?: return false
        val decoded = withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(url).get().build()
                App.imageClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val bytes = resp.body?.bytes() ?: return@use null
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
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
