package com.cobo.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * 只认 release notes 里用得到的那部分 Markdown：`#` 标题、`-`/`*`/`1.` 列表、`>` 引用、
 * ``` 代码块，行内 `**粗**`、`*斜*`、`` `代码` ``、`[文字](链接)`。其余原样当正文显示。
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = COUITheme.colorScheme.onBackgroundVariant,
    headingColor: Color = COUITheme.colorScheme.onBackground,
) {
    val blocks = remember(markdown) { parseBlocks(markdown) }
    val linkColor = COUITheme.colorScheme.primary
    val codeBackground = headingColor.copy(alpha = 0.08f)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEachIndexed { index, block ->
            when (block) {
                is MdBlock.Heading -> Text(
                    inline(block.text, linkColor, codeBackground),
                    fontSize = if (block.level <= 2) 15.sp else 14.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Medium,
                    color = headingColor,
                    modifier = Modifier.padding(top = if (index == 0) 0.dp else 6.dp),
                )
                is MdBlock.Paragraph -> Text(
                    inline(block.text, linkColor, codeBackground),
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = color,
                )
                is MdBlock.Item -> Row(Modifier.padding(start = (block.depth * 12).dp)) {
                    Text(
                        block.marker,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = color,
                        modifier = Modifier.widthIn(min = 14.dp),
                    )
                    Text(
                        inline(block.text, linkColor, codeBackground),
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = color,
                        modifier = Modifier.weight(1f),
                    )
                }
                is MdBlock.Quote -> Text(
                    inline(block.text, linkColor, codeBackground),
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    fontStyle = FontStyle.Italic,
                    color = color.copy(alpha = color.alpha * 0.8f),
                    modifier = Modifier.padding(start = 10.dp),
                )
                is MdBlock.Code -> Text(
                    block.text,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    fontFamily = FontFamily.Monospace,
                    color = color,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Item(val marker: String, val text: String, val depth: Int) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Code(val text: String) : MdBlock
}

private val headingRegex = Regex("^(#{1,6})\\s+(.*?)\\s*#*$")
private val bulletRegex = Regex("^(\\s*)[-*+]\\s+(.*)$")
private val orderedRegex = Regex("^(\\s*)(\\d+)[.)]\\s+(.*)$")
private val ruleRegex = Regex("^\\s*([-*_])(\\s*\\1){2,}\\s*$")

private fun parseBlocks(markdown: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val paragraph = StringBuilder()
    fun flush() {
        if (paragraph.isNotBlank()) blocks += MdBlock.Paragraph(paragraph.toString().trim())
        paragraph.clear()
    }
    val lines = markdown.replace("\r\n", "\n").lines()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()
        when {
            trimmed.startsWith("```") -> {
                flush()
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    if (code.isNotEmpty()) code.append('\n')
                    code.append(lines[i])
                    i++
                }
                blocks += MdBlock.Code(code.toString())
            }
            trimmed.isEmpty() || ruleRegex.matches(line) -> flush()
            headingRegex.matches(trimmed) -> {
                flush()
                val m = headingRegex.find(trimmed)!!
                blocks += MdBlock.Heading(m.groupValues[1].length, m.groupValues[2])
            }
            bulletRegex.matches(line) -> {
                flush()
                val m = bulletRegex.find(line)!!
                blocks += MdBlock.Item("•", m.groupValues[2], m.groupValues[1].length / 2)
            }
            orderedRegex.matches(line) -> {
                flush()
                val m = orderedRegex.find(line)!!
                blocks += MdBlock.Item("${m.groupValues[2]}.", m.groupValues[3], m.groupValues[1].length / 2)
            }
            trimmed.startsWith(">") -> {
                flush()
                blocks += MdBlock.Quote(trimmed.removePrefix(">").trim())
            }
            // 列表项的续行直接接在上一项后面，不另起段落。
            paragraph.isEmpty() && blocks.lastOrNull() is MdBlock.Item && line.startsWith(" ") -> {
                val last = blocks.removeAt(blocks.lastIndex) as MdBlock.Item
                blocks += last.copy(text = last.text + " " + trimmed)
            }
            else -> {
                if (paragraph.isNotEmpty()) paragraph.append('\n')
                paragraph.append(trimmed)
            }
        }
        i++
    }
    flush()
    return blocks
}

/** 行内语法：粗体、斜体、行内代码、链接。没配对的标记原样留着。 */
private fun inline(text: String, linkColor: Color, codeBackground: Color): AnnotatedString =
    buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '\\' && i + 1 < text.length -> {
                    append(text[i + 1])
                    i += 2
                }
                c == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end < 0) { append(c); i++ } else {
                        withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    }
                }
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end < 0) { append("**"); i += 2 } else {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                            append(inline(text.substring(i + 2, end), linkColor, codeBackground))
                        }
                        i = end + 2
                    }
                }
                // 下划线不当强调：版本说明里常有 snake_case 之类的标识符。
                c == '*' && i + 1 < text.length && !text[i + 1].isWhitespace() -> {
                    val end = text.indexOf(c, i + 1)
                    if (end < 0) { append(c); i++ } else {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(inline(text.substring(i + 1, end), linkColor, codeBackground))
                        }
                        i = end + 1
                    }
                }
                c == '[' -> {
                    val close = text.indexOf("](", i + 1)
                    val end = if (close < 0) -1 else text.indexOf(')', close + 2)
                    if (end < 0) { append(c); i++ } else {
                        val label = text.substring(i + 1, close)
                        val url = text.substring(close + 2, end).trim()
                        withLink(
                            LinkAnnotation.Url(
                                url,
                                TextLinkStyles(
                                    style = SpanStyle(color = linkColor),
                                    pressedStyle = SpanStyle(color = linkColor.copy(alpha = linkColor.alpha * 0.3f)),
                                ),
                            ),
                        ) { append(inline(label, linkColor, codeBackground)) }
                        i = end + 1
                    }
                }
                else -> {
                    append(c)
                    i++
                }
            }
        }
    }
