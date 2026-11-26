package com.cobo.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

@Composable
internal actual fun PlatformComposerEditText(
    state: ComposerInputState,
    modifier: Modifier,
    hint: String,
    maxCount: Int,
    minLines: Int,
    maxLines: Int,
    textStyle: TextStyle,
    onFocused: (() -> Unit)?,
) {
    val focusManager = LocalFocusManager.current
    val textColor = COUITheme.colorScheme.onSurface
    val hintColor = COUITheme.colorScheme.onSurfaceSecondary
    val primaryColor = COUITheme.colorScheme.primary
    val currentOnFocused by rememberUpdatedState(onFocused)

    DisposableEffect(state, focusManager) {
        state.clearFocusAction = { focusManager.clearFocus() }
        onDispose { state.clearFocusAction = null }
    }

    LaunchedEffect(state.text) {
        if (state.value.text != state.text) {
            state.value = TextFieldValue(state.text, TextRange(state.cursor.coerceIn(0, state.text.length)))
        }
    }

    BasicTextField(
        value = state.value,
        onValueChange = { next ->
            val clampedText = if (next.text.length > maxCount) next.text.take(maxCount) else next.text
            val clampedSel = TextRange(
                next.selection.start.coerceIn(0, clampedText.length),
                next.selection.end.coerceIn(0, clampedText.length),
            )
            state.value = next.copy(text = clampedText, selection = clampedSel)
            state.text = clampedText
            state.cursor = clampedSel.end
        },
        modifier = modifier
            .focusRequester(state.focusRequester)
            .onFocusChanged { if (it.isFocused) currentOnFocused?.invoke() }
            .padding(vertical = 10.dp),
        textStyle = textStyle.copy(color = textColor),
        cursorBrush = SolidColor(primaryColor),
        minLines = minLines,
        maxLines = maxLines,
        decorationBox = { innerTextField ->
            Box {
                if (state.text.isEmpty() && hint.isNotEmpty()) {
                    Text(
                        text = hint,
                        style = textStyle,
                        color = hintColor,
                    )
                }
                innerTextField()
            }
        },
    )
}
