package com.cobo.app.ui.components

import android.text.Html
import android.text.style.URLSpan
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.cobo.app.data.WeiboEmojiBitmaps
import com.cobo.app.data.stripHtml
import com.cobo.app.ui.CoboIcons
import com.cobo.app.ui.LocalLinkHandler
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

private const val SuperTopicAlt = "◆"
private const val VideoGlyph = "▶"
private const val ImageGlyph = "[图]"
private const val WeiboGlyph = "[博]"

internal enum class LinkGlyph {
    SuperTopic,
    Video,
    Image,
    Weibo,
}

private class LinkGlyphSpec(
    val tag: String,
    val alt: String,
    val icon: ImageVector,
    val yOffset: Dp,
)

private fun glyphSpec(glyph: LinkGlyph): LinkGlyphSpec = when (glyph) {
    LinkGlyph.SuperTopic -> LinkGlyphSpec("st", SuperTopicAlt, CoboIcons.SuperTopic, 0.dp)
    LinkGlyph.Video -> LinkGlyphSpec("vid", VideoGlyph, CoboIcons.VideoCamera, (-0.5).dp)
    LinkGlyph.Image -> LinkGlyphSpec("img", ImageGlyph, CoboIcons.Image, (-0.5).dp)
    LinkGlyph.Weibo -> LinkGlyphSpec("wb", WeiboGlyph, CoboIcons.Weibo, (-0.5).dp)
}

/**
 * Picks the prefix glyph/icon that belongs in front of a link.
 *
 * - 超话: `https://m.weibo.cn/p/index?extparam=英超&containerid=1008086c62...`、`sinaweibo://pageinfo?...`
 *   以及 `m.weibo.cn/p/index?...`。普通话题链接走 `m.weibo.cn/search`，不加标记（跟微博一致）。
 * - 视频: `https://video.weibo.com/show?fid=...`、`m.weibo.cn/s/video/index?...`、`sinaweibo://video/...`。
 * - 查看图片 / 微博正文: 链接文案为「查看图片」或地址指向 `photo.weibo.com`，以及链接文案为「微博正文」。
 */
internal fun glyphFor(url: String, label: String): LinkGlyph? {
    val lower = url.lowercase()
    val trimmed = label.trim()
    return when {
        lower.contains("100808") ||
            lower.contains("pageinfo") ||
            lower.contains("m.weibo.cn/p/index") ||
            lower.contains("/p/index?") -> LinkGlyph.SuperTopic

        lower.contains("video.weibo.com") ||
            lower.contains("/s/video/") ||
            lower.contains("video/index?") ||
            lower.startsWith("sinaweibo://video") ||
            lower.startsWith("intent://gotovideo") -> LinkGlyph.Video

        trimmed == "查看图片" ||
            lower.contains("photo.weibo.com") -> LinkGlyph.Image

        trimmed == "微博正文" -> LinkGlyph.Weibo

        else -> null
    }
}

private class ParsedLink(
    val start: Int,
    val end: Int,
    val url: String,
    val glyph: LinkGlyph?,
)

private class ParsedWeiboContent(
    val plain: String,
    val links: List<ParsedLink>,
)

private fun parseWeiboContent(html: String): ParsedWeiboContent {
    if (html.isBlank()) return ParsedWeiboContent("", emptyList())
    return try {
        // Weibo sometimes double- or triple-escapes numeric entities (e.g. &amp;amp;#10;).
        // Peel every layer before the Html pass so they become newlines/emoji, not literal text.
        var normalized = html
        while (true) {
            val next = normalized.replace(Regex("&amp;#(\\d+);"), "&#$1;")
            if (next == normalized) break
            normalized = next
        }
        val spanned = Html.fromHtml(normalized, Html.FROM_HTML_MODE_LEGACY)
        val raw = spanned.toString()

        // Html.fromHtml turns <img> (emoji / video badge) into U+FFFC object replacements.
        // COUI's Text renders that as a boxed "OBJ" glyph, so drop them and shift link ranges.
        val removed = BooleanArray(raw.length)
        val plainBuilder = StringBuilder(raw.length)
        for (index in raw.indices) {
            val c = raw[index]
            if (c == '\uFFFC' || c == '\u200B' || c == '\uFEFF') {
                removed[index] = true
            } else {
                plainBuilder.append(c)
            }
        }
        val plain = plainBuilder.toString()
        val prefix = IntArray(raw.length + 1)
        for (index in raw.indices) {
            prefix[index + 1] = prefix[index] + if (removed[index]) 1 else 0
        }
        fun adjust(index: Int): Int {
            val safe = index.coerceIn(0, raw.length)
            return (safe - prefix[safe]).coerceIn(0, plain.length)
        }

        val links = spanned.getSpans(0, spanned.length, URLSpan::class.java)
            .mapNotNull { span ->
                val start = adjust(spanned.getSpanStart(span).coerceAtLeast(0))
                val end = adjust(spanned.getSpanEnd(span).coerceAtMost(raw.length))
                if (start >= end) null else ParsedLink(start, end, span.url, glyphFor(span.url, plain.substring(start, end)))
            }
            .sortedBy { it.start }
        ParsedWeiboContent(plain, links)
    } catch (_: Throwable) {
        ParsedWeiboContent(stripHtml(html).replace("\uFFFC", ""), emptyList())
    }
}

private fun linkStyles(color: Color?): TextLinkStyles? = color?.let {
    TextLinkStyles(
        style = SpanStyle(color = it, textDecoration = TextDecoration.None),
        pressedStyle = SpanStyle(
            color = it.copy(alpha = it.alpha * 0.3f),
            textDecoration = TextDecoration.None,
        ),
    )
}

fun parseWeiboHtml(
    html: String,
    onLink: ((String) -> Unit)? = null,
    glyphColor: Color? = null,
): AnnotatedString {
    val builder = AnnotatedString.Builder()
    appendParsedWeibo(
        builder = builder,
        inlineContent = null,
        parsed = parseWeiboContent(html),
        onLink = onLink,
        linkColor = glyphColor,
    )
    return builder.toAnnotatedString()
}

private fun appendParsedWeibo(
    builder: AnnotatedString.Builder,
    inlineContent: MutableMap<String, InlineTextContent>?,
    parsed: ParsedWeiboContent,
    onLink: ((String) -> Unit)?,
    linkColor: Color?,
    inlineIdPrefix: String = "",
) {
    val plain = parsed.plain
    if (plain.isEmpty()) return
    val styles = linkStyles(linkColor)
    var cursor = 0
    var inlineIndex = 0
    parsed.links.forEach { link ->
        // Nested / overlapping anchors: keep the outer one, skip the rest.
        if (link.start < cursor) return@forEach
        if (link.start > cursor) builder.append(plain.substring(cursor, link.start))

        val annotation = if (onLink != null) {
            LinkAnnotation.Url(
                url = link.url,
                styles = styles,
                linkInteractionListener = LinkInteractionListener { onLink(link.url) },
            )
        } else {
            LinkAnnotation.Url(url = link.url, styles = styles)
        }

        val spec = link.glyph?.let { glyphSpec(it) }
        if (spec != null) {
            // 图标作为独立 inline content 放在链接文本前，icon 自己接点击、文本接 addLink，
            // 避免把占位符和文字塞进同一段 addLink 导致占位符挤偏命中范围。
            if (inlineContent != null) {
                val id = "${inlineIdPrefix}${spec.tag}_${inlineIndex++}"
                builder.appendInlineContent(id, spec.alt)
                inlineContent[id] = linkPrefixInlineContent(
                    icon = spec.icon,
                    url = link.url,
                    linkColor = linkColor,
                    yOffset = spec.yOffset,
                    onLink = onLink,
                )
            } else {
                val glyphStart = builder.length
                builder.append(spec.alt)
                if (linkColor != null) {
                    builder.addStyle(SpanStyle(color = linkColor), glyphStart, builder.length)
                }
            }
            val textStart = builder.length
            builder.append(plain.substring(link.start, link.end))
            builder.addLink(annotation, textStart, builder.length)
        } else {
            val start = builder.length
            builder.append(plain.substring(link.start, link.end))
            builder.addLink(annotation, start, builder.length)
        }
        cursor = link.end
    }
    if (cursor < plain.length) builder.append(plain.substring(cursor))
}

private fun linkPrefixInlineContent(
    icon: ImageVector,
    url: String,
    linkColor: Color?,
    yOffset: Dp,
    onLink: ((String) -> Unit)?,
): InlineTextContent = InlineTextContent(
    Placeholder(1.18.em, 1.05.em, PlaceholderVerticalAlign.TextCenter),
) {
    val tint = linkColor ?: COUITheme.colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset(y = yOffset)
            .padding(end = 1.5.dp)
            .let { mod ->
                if (onLink != null) {
                    mod.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onLink(url) },
                    )
                } else {
                    mod
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * 微博富文本：链接可点、超话与视频带图标、`<img>` 表情内联成图片。
 *
 * 表情按 1.0em 内联，不会撑高行盒；同一段 html 的解析结果会记住，列表滚动不会反复重建。
 */
@Composable
fun WeiboText(
    html: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 15.sp,
    lineHeight: TextUnit = 21.sp,
    maxLines: Int = Int.MAX_VALUE,
    color: Color = COUITheme.colorScheme.onSurface,
    linkColor: Color = COUITheme.colorScheme.primary,
    selectable: Boolean = true,
) {
    val linkHandler = LocalLinkHandler.current
    // 表情表加载完后，正文里的 [表情] 要重新拆一遍，否则会一直停在方括号文字上。
    val emojiVersion = WeiboEmojiBitmaps.version
    val (text, inline) = remember(html, linkHandler, linkColor, emojiVersion) {
        buildWeiboAnnotated(html, onLink = linkHandler, linkColor = linkColor)
    }
    val body: @Composable () -> Unit = {
        Text(
            text = text,
            modifier = modifier,
            color = color,
            fontSize = fontSize,
            fontWeight = FontWeight.Normal,
            lineHeight = lineHeight,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            inlineContent = inline,
        )
    }
    if (selectable) SelectionContainer { body() } else body()
}

/**
 * 把微博 HTML 变成一段带内联内容的文本。
 *
 * 链接、超话、视频图标走 [appendParsedWeibo]；`<img>` 表情按 1.0em 内联成图片，
 * 占位一旦高过字体自身的行盒，带表情的那一行就会被撑高，同列的卡片会因此比邻居高一截。
 */
private fun buildWeiboAnnotated(
    html: String,
    onLink: (String) -> Unit,
    linkColor: Color,
): Pair<AnnotatedString, Map<String, InlineTextContent>> {
    val inlineContent = LinkedHashMap<String, InlineTextContent>()
    val builder = AnnotatedString.Builder()
    splitEmoji(html, WeiboEmojiBitmaps::urlOf).forEachIndexed { index, (url, segment) ->
        if (url == null) {
            appendParsedWeibo(
                builder = builder,
                inlineContent = inlineContent,
                parsed = parseWeiboContent(segment),
                onLink = onLink,
                linkColor = linkColor,
                inlineIdPrefix = "seg_${index}_",
            )
        } else {
            val value = segment
            val id = "emoji_$index"
            builder.appendInlineContent(id, value)
            inlineContent[id] = InlineTextContent(
                Placeholder(1.0.em, 1.0.em, PlaceholderVerticalAlign.Center),
            ) {
                AsyncImage(
                    model = url,
                    contentDescription = value,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
    return builder.toAnnotatedString() to inlineContent
}

private val ImgTag = Regex("<img\\b[^>]*>", RegexOption.IGNORE_CASE)
private val ImgAttr = Regex("([A-Za-z-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')")

/**
 * 把正文切成「文本 / 表情图」两类片段。
 *
 * 微博的 emoji 是 `<img alt="[可爱]" src="...">`，交给 Html.fromHtml 只会剩一个 U+FFFC 占位符，
 * [parseWeiboContent] 又把它删掉 —— 纯表情正文就成了空白。这里先把 src 抽出来内联成图片，
 * 其余片段仍走 [appendParsedWeibo]，链接和图标照旧。
 *
 * 属性顺序在不同接口里并不固定，地址也可能是 `//host/...` 这种省略协议的写法，
 * 所以逐个属性解析，而不是拿一条写死顺序的正则去匹配。
 */
internal fun splitEmoji(html: String, urlOf: (String) -> String?): List<Pair<String?, String>> {
    val out = ArrayList<Pair<String?, String>>()
    var cursor = 0
    ImgTag.findAll(html).forEach { match ->
        val attrs = ImgAttr.findAll(match.value).associate {
            it.groupValues[1].lowercase() to (it.groupValues[2].ifEmpty { it.groupValues[3] })
        }
        val alt = attrs["alt"].orEmpty()
        val src = attrs["src"].orEmpty()
        if (alt.isBlank() || src.isBlank()) return@forEach
        if (match.range.first > cursor) appendPlain(out, html.substring(cursor, match.range.first), urlOf)
        out.add(normalizeImageUrl(src) to alt)
        cursor = match.range.last + 1
    }
    if (cursor < html.length) appendPlain(out, html.substring(cursor), urlOf)
    return out
}

private val EmojiPhrase = Regex("\\[[^\\[\\]\\n]{1,12}\\]")

/**
 * 纯文字里出现的 `[表情]` 也拆成图。
 *
 * 走 H5 通道时正文里的表情本来就是 `<img>`；换成客户端通道以后同一个表情只剩 `[笑而不语]`
 * 这样的文字，正文就成了一串方括号。这里按表情表把认得的换成图，不认识的照旧当文字留着。
 */
private fun appendPlain(
    out: MutableList<Pair<String?, String>>,
    text: String,
    urlOf: (String) -> String?,
) {
    var cursor = 0
    EmojiPhrase.findAll(text).forEach { match ->
        val url = urlOf(match.value) ?: return@forEach
        if (match.range.first > cursor) out.add(null to text.substring(cursor, match.range.first))
        out.add(url to match.value)
        cursor = match.range.last + 1
    }
    if (cursor < text.length) out.add(null to text.substring(cursor))
}

/** 微博图床允许 https；省略协议和 http 的地址统一补成 https，免得被明文策略挡掉。 */
private fun normalizeImageUrl(url: String): String = when {
    url.startsWith("//") -> "https:$url"
    url.startsWith("http://") -> "https://" + url.removePrefix("http://")
    else -> url
}

/**
 * 带表情的微博富文本。现在 [WeiboText] 自己也渲染表情，这个重载只是保留原来的调用点与默认行高，
 * 并允许单独指定链接色和是否可选中。
 *
 * @param linkColor 超话图标 / 视频三角这些链接文字与前缀的色值，默认主题主色。
 * @param selectable 是否包一层 [SelectionContainer]。放在可左滑、可点进详情的列表行里时关掉，
 *   免得长按选中和行手势互相抢。
 */
@Composable
fun WeiboRichText(
    html: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 15.sp,
    lineHeight: TextUnit = 22.sp,
    maxLines: Int = Int.MAX_VALUE,
    color: Color = COUITheme.colorScheme.onSurface,
    linkColor: Color = COUITheme.colorScheme.primary,
    selectable: Boolean = true,
) {
    WeiboText(
        html = html,
        modifier = modifier,
        fontSize = fontSize,
        lineHeight = lineHeight,
        maxLines = maxLines,
        color = color,
        linkColor = linkColor,
        selectable = selectable,
    )
}
