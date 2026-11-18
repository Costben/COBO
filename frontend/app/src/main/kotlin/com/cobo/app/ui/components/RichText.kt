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
import com.cobo.app.data.stripHtml
import com.cobo.app.ui.CoboIcons
import com.cobo.app.ui.LocalLinkHandler
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

private const val SuperTopicAlt = "◆"
private const val VideoGlyph = "▶"

private enum class LinkGlyph {
    SuperTopic,
    Video,
}

/**
 * Picks the prefix glyph/icon that belongs in front of a link.
 *
 * - 超话: `https://m.weibo.cn/p/index?extparam=英超&containerid=1008086c62...`、`sinaweibo://pageinfo?...`
 *   以及 `m.weibo.cn/p/index?...`。普通话题链接走 `m.weibo.cn/search`，不加标记（跟微博一致）。
 * - 视频: `https://video.weibo.com/show?fid=...`、`m.weibo.cn/s/video/index?...`、`sinaweibo://video/...`。
 */
private fun glyphFor(url: String): LinkGlyph? {
    val lower = url.lowercase()
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
                if (start >= end) null else ParsedLink(start, end, span.url, glyphFor(span.url))
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

        when (link.glyph) {
            LinkGlyph.SuperTopic, LinkGlyph.Video -> {
                val isSuperTopic = link.glyph == LinkGlyph.SuperTopic
                val altText = if (isSuperTopic) SuperTopicAlt else VideoGlyph
                val iconVector = if (isSuperTopic) CoboIcons.SuperTopic else CoboIcons.VideoCamera
                val prefixTag = if (isSuperTopic) "st" else "vid"
                // 图标作为独立 inline content 放在链接文本前，icon 自己接点击、文本接 addLink，
                // 避免把占位符和文字塞进同一段 addLink 导致占位符挤偏命中范围。
                if (inlineContent != null) {
                    val id = "${inlineIdPrefix}${prefixTag}_${inlineIndex++}"
                    builder.appendInlineContent(id, altText)
                    inlineContent[id] = linkPrefixInlineContent(
                        icon = iconVector,
                        url = link.url,
                        linkColor = linkColor,
                        // 摄影机纵向上下对称，-0.5dp 刚好与文字中线重合；超话上宽下尖、视觉重心偏上，不额外上移。
                        yOffset = if (isSuperTopic) 0.dp else (-0.5).dp,
                        onLink = onLink,
                    )
                } else {
                    val glyphStart = builder.length
                    builder.append(altText)
                    if (linkColor != null) {
                        builder.addStyle(SpanStyle(color = linkColor), glyphStart, builder.length)
                    }
                }
                val textStart = builder.length
                builder.append(plain.substring(link.start, link.end))
                builder.addLink(annotation, textStart, builder.length)
            }

            null -> {
                val start = builder.length
                builder.append(plain.substring(link.start, link.end))
                builder.addLink(annotation, start, builder.length)
            }
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

@Composable
fun WeiboText(
    html: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 15.sp,
    lineHeight: TextUnit = 21.sp,
    maxLines: Int = Int.MAX_VALUE,
    color: Color = COUITheme.colorScheme.onSurface,
) {
    val linkHandler = LocalLinkHandler.current
    val linkColor = COUITheme.colorScheme.primary
    val parsed = remember(html) { parseWeiboContent(html) }
    val (text, inline) = remember(parsed, linkHandler, linkColor) {
        val inlineContent = LinkedHashMap<String, InlineTextContent>()
        val builder = AnnotatedString.Builder()
        appendParsedWeibo(
            builder = builder,
            inlineContent = inlineContent,
            parsed = parsed,
            onLink = { linkHandler(it) },
            linkColor = linkColor,
        )
        builder.toAnnotatedString() to inlineContent
    }
    SelectionContainer {
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
}

private val EmojiImg = Regex("<img[^>]*?alt=\"([^\"]*)\"[^>]*?src=\"([^\"]*)\"[^>]*?>")

/**
 * 把正文切成「文本 / 表情图」两类片段。
 *
 * 微博的 emoji 是 `<img alt="[可爱]" src="...">`，交给 Html.fromHtml 只会剩一个 U+FFFC 占位符，
 * [parseWeiboHtml] 又把它删掉 —— 纯表情正文就成了空白。这里先把 src 抽出来，按 1.0em 内联成图片，
 * 其余片段仍走 [appendParsedWeibo]，链接和图标照旧。
 */
private fun splitEmoji(html: String): List<Pair<String?, String>> {
    val out = ArrayList<Pair<String?, String>>()
    var cursor = 0
    EmojiImg.findAll(html).forEach { match ->
        if (match.range.first > cursor) out.add(null to html.substring(cursor, match.range.first))
        out.add(match.groupValues[2] to match.groupValues[1])
        cursor = match.range.last + 1
    }
    if (cursor < html.length) out.add(null to html.substring(cursor))
    return out
}

/**
 * 带表情的微博富文本：链接可点，`<img>` 表情按行高比例内联成图片。
 *
 * 与 [WeiboText] 的区别只有表情：信息流那种「一屏几十条、每条都可能带十几张表情」的地方仍用
 * [WeiboText]（表情直接丢掉），聊天和浏览历史这种单条正文用这个重载，表情才会真的显示出来。
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
    val linkHandler = LocalLinkHandler.current
    val segments = remember(html) {
        splitEmoji(html).map { (url, value) ->
            if (url == null) null to parseWeiboContent(value) else (url to value) to null
        }
    }
    val (text, inline) = remember(segments, linkHandler, linkColor) {
        val inlineContent = LinkedHashMap<String, InlineTextContent>()
        val builder = AnnotatedString.Builder()
        var index = 0
        segments.forEachIndexed { segIndex, (emoji, parsed) ->
            if (parsed != null) {
                appendParsedWeibo(
                    builder = builder,
                    inlineContent = inlineContent,
                    parsed = parsed,
                    onLink = linkHandler,
                    linkColor = linkColor,
                    inlineIdPrefix = "seg_${segIndex}_",
                )
            } else if (emoji != null) {
                val (url, value) = emoji
                val id = "emoji_${index++}"
                builder.appendInlineContent(id, value)
                inlineContent[id] = InlineTextContent(
                    // 一个 em 见方，不要更大：内联占位一旦高过字体自身的行盒，带表情的那一行就会
                    // 被撑高，同列的卡片会因此比邻居高一截。
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
        builder.toAnnotatedString() to inlineContent
    }
    val body: @Composable () -> Unit = {
        Text(
            text = text,
            modifier = modifier,
            color = color,
            fontSize = fontSize,
            lineHeight = lineHeight,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            inlineContent = inline,
        )
    }
    if (selectable) SelectionContainer { body() } else body()
}
