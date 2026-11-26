package com.cobo.app.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextField
import io.github.suqi8.coui.kmp.basic.TextFieldDefaults
import io.github.suqi8.coui.kmp.basic.TextFieldMode
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlin.math.roundToInt

private val EmoteTokenRegex = Regex("\\[[^\\[\\]\\n]{1,64}\\]")
// 用非空白全角字符占位，避免连续表情被排版器当作行尾空白挂在行外而不换行。
private const val EMOTE_SLOT_CHAR = '\u3007'
private val EDITOR_INSIDE_MARGIN = DpSize(12.dp, 10.dp)

internal data class ParsedEmoteSpan(
    val origStart: Int,
    val origEnd: Int,
    val transOffset: Int,
    val url: String,
    val label: String,
)

internal data class EmoteTransformResult(
    val transformed: AnnotatedString,
    val spans: List<ParsedEmoteSpan>,
    val offsetMapping: OffsetMapping,
)

/**
 * 把正文里命中的 `[表情]` 替换成单个全角透明占位符并记录原/新坐标映射，
 * 以便叠加层按 `TextLayoutResult` 的占位格精确贴上表情图片。
 */
internal fun parseEmoteTransform(
    text: String,
    emotes: Map<String, String>,
    slotFontSize: TextUnit,
): EmoteTransformResult {
    if (text.isEmpty() || emotes.isEmpty() || '[' !in text) {
        return EmoteTransformResult(
            transformed = AnnotatedString(text),
            spans = emptyList(),
            offsetMapping = OffsetMapping.Identity,
        )
    }

    val spans = ArrayList<ParsedEmoteSpan>()
    val builder = AnnotatedString.Builder()
    var cursor = 0
    EmoteTokenRegex.findAll(text).forEach { match ->
        val url = emotes[match.value]?.takeIf { it.isNotBlank() } ?: return@forEach
        if (match.range.first > cursor) {
            builder.append(text.substring(cursor, match.range.first))
        }
        val transOffset = builder.length
        builder.pushStyle(SpanStyle(color = Color.Transparent, fontSize = slotFontSize))
        builder.append(EMOTE_SLOT_CHAR)
        builder.pop()
        spans += ParsedEmoteSpan(
            origStart = match.range.first,
            origEnd = match.range.last + 1,
            transOffset = transOffset,
            url = url,
            label = match.value,
        )
        cursor = match.range.last + 1
    }
    if (spans.isEmpty()) {
        return EmoteTransformResult(
            transformed = AnnotatedString(text),
            spans = emptyList(),
            offsetMapping = OffsetMapping.Identity,
        )
    }
    if (cursor < text.length) {
        builder.append(text.substring(cursor))
    }

    val transformed = builder.toAnnotatedString()
    val origLen = text.length
    val transLen = transformed.length
    val mapping = object : OffsetMapping {
        override fun originalToTransformed(offset: Int): Int {
            val o = offset.coerceIn(0, origLen)
            var delta = 0
            for (span in spans) {
                if (o <= span.origStart) break
                if (o < span.origEnd) return (span.transOffset + 1).coerceIn(0, transLen)
                delta += (span.origEnd - span.origStart) - 1
            }
            return (o - delta).coerceIn(0, transLen)
        }

        override fun transformedToOriginal(offset: Int): Int {
            val t = offset.coerceIn(0, transLen)
            var delta = 0
            for (span in spans) {
                if (t <= span.transOffset) break
                delta += (span.origEnd - span.origStart) - 1
            }
            return (t + delta).coerceIn(0, origLen)
        }
    }
    return EmoteTransformResult(transformed, spans, mapping)
}

/**
 * 退格刚好删掉某条 `[表情]` 末尾的 `]` 时，整段移除该表情标记，避免残留半截文本。
 */
internal fun deleteWholeEmoteOnBackspace(
    oldValue: TextFieldValue,
    newValue: TextFieldValue,
    emotes: Map<String, String>,
): TextFieldValue {
    if (emotes.isEmpty()) return newValue
    if (!oldValue.selection.collapsed || !newValue.selection.collapsed) return newValue
    if (newValue.text.length != oldValue.text.length - 1) return newValue
    val delIdx = newValue.selection.start
    if (delIdx !in oldValue.text.indices || oldValue.text[delIdx] != ']') return newValue
    if (oldValue.text.substring(0, delIdx) != newValue.text.substring(0, delIdx)) return newValue
    if (oldValue.text.substring(delIdx + 1) != newValue.text.substring(delIdx)) return newValue

    val searchStart = (delIdx - 63).coerceAtLeast(0)
    val openIdx = oldValue.text.lastIndexOf('[', startIndex = delIdx - 1)
    if (openIdx < searchStart || openIdx < 0) return newValue
    val token = oldValue.text.substring(openIdx, delIdx + 1)
    if (token !in emotes) return newValue
    return TextFieldValue(
        text = oldValue.text.removeRange(openIdx, delIdx + 1),
        selection = TextRange(openIdx),
    )
}

/**
 * 支持把 `[表情]` 内联渲染为表情图的私信输入框；外层统一滚动以保证文本与贴图同步位移。
 *
 * 表情表是 `[表情标记] -> 图片地址`，服务端渲染气泡时用的也是同一套标记。
 */
@Composable
internal fun ComposerEmoteTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    emotes: Map<String, String>,
    maxHeight: Dp,
    focusRequester: FocusRequester,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
    label: String = "说点什么…",
    minLines: Int = 3,
    insideMargin: DpSize = EDITOR_INSIDE_MARGIN,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    compact: Boolean = false,
) {
    val density = LocalDensity.current
    val scheme = COUITheme.colorScheme
    val baseStyle = TextFieldDefaults.textStyle(TextFieldMode.None)
    val inputStyle = remember(baseStyle, scheme.onSurface, compact) {
        baseStyle.copy(color = scheme.onSurface, lineHeight = if (compact) 20.sp else 24.sp)
    }
    val slotFontSize = 21.sp
    val parsed = remember(value.text, emotes, slotFontSize) {
        parseEmoteTransform(value.text, emotes, slotFontSize)
    }
    val visualTransformation = remember(emotes, slotFontSize) {
        VisualTransformation { annotated ->
            val result = parseEmoteTransform(annotated.text, emotes, slotFontSize)
            TransformedText(result.transformed, result.offsetMapping)
        }
    }

    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val scrollState = rememberScrollState()
    var viewportHeightPx by remember { mutableIntStateOf(0) }

    LaunchedEffect(value.selection, textLayout, viewportHeightPx) {
        val layout = textLayout ?: return@LaunchedEffect
        if (viewportHeightPx <= 0) return@LaunchedEffect
        val transLen = layout.layoutInput.text.length
        val transCursor = parsed.offsetMapping.originalToTransformed(value.selection.end)
            .coerceIn(0, transLen)
        val cursorRect = layout.getCursorRect(transCursor)
        val topPadPx = with(density) { insideMargin.height.roundToPx() }
        val bottomPadPx = topPadPx
        val visibleTop = scrollState.value
        val visibleBottom = visibleTop + viewportHeightPx
        val targetTop = (cursorRect.top + topPadPx - 4).toInt()
        val targetBottom = (cursorRect.bottom + topPadPx + bottomPadPx + 4).toInt()
        when {
            targetTop < visibleTop -> scrollState.scrollTo(targetTop.coerceAtLeast(0))
            targetBottom > visibleBottom -> scrollState.scrollTo((targetBottom - viewportHeightPx).coerceAtLeast(0))
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .onSizeChanged { viewportHeightPx = it.height }
            .verticalScroll(scrollState),
    ) {
        if (compact) {
            BasicTextField(
                value = value,
                onValueChange = { next ->
                    onValueChange(deleteWholeEmoteOnBackspace(value, next, emotes))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                textStyle = inputStyle,
                cursorBrush = SolidColor(scheme.primary),
                minLines = minLines,
                maxLines = Int.MAX_VALUE,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                visualTransformation = visualTransformation,
                onTextLayout = { textLayout = it },
                interactionSource = interactionSource,
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = insideMargin.width, vertical = insideMargin.height),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (value.text.isEmpty() && label.isNotEmpty()) {
                            Text(
                                text = label,
                                style = inputStyle,
                                color = scheme.onSurfaceSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                },
            )
        } else {
            TextField(
                value = value,
                onValueChange = { next ->
                    onValueChange(deleteWholeEmoteOnBackspace(value, next, emotes))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                label = label,
                backgroundMode = TextFieldMode.None,
                insideMargin = insideMargin,
                textStyle = inputStyle,
                minLines = minLines,
                maxLines = Int.MAX_VALUE,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                visualTransformation = visualTransformation,
                onTextLayout = { textLayout = it },
                interactionSource = interactionSource,
            )
        }

        val layout = textLayout
        if (parsed.spans.isNotEmpty() &&
            layout != null &&
            layout.layoutInput.text.text == parsed.transformed.text
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(horizontal = insideMargin.width, vertical = insideMargin.height),
            ) {
                parsed.spans.forEach { span ->
                    if (span.transOffset in 0 until layout.layoutInput.text.length) {
                        val box = layout.getBoundingBox(span.transOffset)
                        val slotW = (box.right - box.left).coerceAtLeast(1f)
                        val slotH = (box.bottom - box.top).coerceAtLeast(1f)
                        val iconPx = (minOf(slotW, slotH) * 0.92f).roundToInt().coerceAtLeast(1)
                        val offsetX = (box.left + (slotW - iconPx) / 2f).roundToInt()
                        val offsetY = (box.top + (slotH - iconPx) / 2f).roundToInt()
                        val iconDp = with(density) { iconPx.toDp() }
                        AsyncImage(
                            model = span.url,
                            contentDescription = span.label,
                            modifier = Modifier
                                .offset { IntOffset(offsetX, offsetY) }
                                .size(iconDp),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
            }
        }
    }
}
