package com.cobo.app.ui.components

import android.text.Html
import android.text.style.URLSpan
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.cobo.app.data.stripHtml
import com.cobo.app.ui.LocalLinkHandler
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * 链接前缀字形。超话用实心菱形（微博自己的样式），视频用播放三角。
 *
 * 这里是**文本字符**，不是 inline content：inline content 会往 AnnotatedString 里插一个占位符，
 * 链接的 addLink 范围跟着占位符走，点文字时命中不到链接（超话、视频两种链接都点不动）。
 * 字符版没有占位符，字形和文字落在同一个链接范围里，点字形也能跳。
 */
private const val SuperTopicGlyph = "◆"
private const val VideoGlyph = "▶"

/**
 * Picks the glyph that belongs in front of a link.
 *
 * - 超话: `https://m.weibo.cn/p/index?extparam=英超&containerid=1008086c62...`、`sinaweibo://pageinfo?...`
 *   以及 `m.weibo.cn/p/index?...`。普通话题链接走 `m.weibo.cn/search`，不加标记（跟微博一致）。
 * - 视频: `https://video.weibo.com/show?fid=...`、`m.weibo.cn/s/video/index?...`、`sinaweibo://video/...`。
 */
private fun glyphFor(url: String): String? {
    val lower = url.lowercase()
    return when {
        lower.contains("100808") ||
            lower.contains("pageinfo") ||
            lower.contains("m.weibo.cn/p/index") ||
            lower.contains("/p/index?") -> SuperTopicGlyph

        lower.contains("video.weibo.com") ||
            lower.contains("/s/video/") ||
            lower.contains("video/index?") ||
            lower.startsWith("sinaweibo://video") ||
            lower.startsWith("intent://gotovideo") -> VideoGlyph

        else -> null
    }
}

private class ParsedLink(
    val start: Int,
    val end: Int,
    val url: String,
    val glyph: String?,
)

fun parseWeiboHtml(
    html: String,
    onLink: ((String) -> Unit)? = null,
    glyphColor: Color? = null,
): AnnotatedString {
    if (html.isBlank()) return AnnotatedString("")
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

        buildAnnotatedString {
            var cursor = 0
            links.forEach { link ->
                // Nested / overlapping anchors: keep the outer one, skip the rest.
                if (link.start < cursor) return@forEach
                if (link.start > cursor) append(plain.substring(cursor, link.start))
                val start = length
                val glyph = link.glyph
                if (glyph != null) {
                    val glyphStart = length
                    append(glyph)
                    if (glyphColor != null) addStyle(SpanStyle(color = glyphColor), glyphStart, length)
                }
                append(plain.substring(link.start, link.end))
                val annotation = if (onLink != null) {
                    LinkAnnotation.Url(
                        url = link.url,
                        linkInteractionListener = LinkInteractionListener { onLink(link.url) },
                    )
                } else {
                    LinkAnnotation.Url(link.url)
                }
                // 字形和文字一起进链接范围，点菱形/三角也能跳。
                addLink(annotation, start, length)
                cursor = link.end
            }
            if (cursor < plain.length) append(plain.substring(cursor))
        }
    } catch (_: Throwable) {
        AnnotatedString(stripHtml(html).replace("\uFFFC", ""))
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
    val glyphColor = COUITheme.colorScheme.primary
    val text = remember(html, linkHandler, glyphColor) {
        parseWeiboHtml(html, onLink = { linkHandler(it) }, glyphColor = glyphColor)
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
        )
    }
}
