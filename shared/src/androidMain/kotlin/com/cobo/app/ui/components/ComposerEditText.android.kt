package com.cobo.app.ui.components

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.drawable.BitmapDrawable
import android.text.Editable
import android.text.InputFilter
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.DynamicDrawableSpan
import android.text.style.ImageSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.cobo.app.App
import com.cobo.app.data.WeiboEmojiBitmaps
import io.github.suqi8.coui.kmp.theme.COUITheme
import java.util.WeakHashMap

private val androidEmojiPattern = Regex("\\[[^\\]]+\\]")
private val editTextRegistry = WeakHashMap<ComposerInputState, EmojiSpanEditText>()

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
    val textColor = COUITheme.colorScheme.onSurface.toArgb()
    val hintColor = COUITheme.colorScheme.onSurfaceSecondary.toArgb()
    val fontSizeSp = textStyle.fontSize.let { if (it.type == TextUnitType.Sp) it.value else 16f }
    val emojiSizePx = with(LocalDensity.current) { (fontSizeSp * 1.2f).sp.toPx() }.toInt().coerceAtLeast(16)
    val currentOnFocused by rememberUpdatedState(onFocused)

    LaunchedEffect(state.text, emojiSizePx) {
        val codes = androidEmojiPattern.findAll(state.text).map { it.value }.distinct().toList()
        if (codes.isEmpty()) return@LaunchedEffect
        if (codes.any { !WeiboEmojiBitmaps.hasUrl(it) }) {
            App.repo.emotions()
        }
        var loadedAny = false
        for (code in codes) {
            if (WeiboEmojiBitmaps.getCached(code) == null && WeiboEmojiBitmaps.ensureLoaded(code)) {
                loadedAny = true
            }
        }
        if (loadedAny) {
            editTextRegistry[state]?.reRenderEmoji(emojiSizePx)
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            EmojiSpanEditText(context).apply {
                val verticalPadding = (10 * resources.displayMetrics.density).toInt()
                setBackgroundColor(AndroidColor.TRANSPARENT)
                gravity = Gravity.TOP or Gravity.START
                inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                imeOptions = EditorInfo.IME_FLAG_NO_ENTER_ACTION
                setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp)
                setLineSpacing(0f, 1.158f)
                setTextColor(textColor)
                setHintTextColor(hintColor)
                this.hint = hint
                this.minLines = minLines
                this.maxLines = maxLines
                filters = arrayOf(InputFilter.LengthFilter(maxCount))
                setPadding(0, verticalPadding, 0, verticalPadding)
                isFocusableInTouchMode = true
                includeFontPadding = false
                setText(state.text)
                setSelection(state.text.length)
                reRenderEmoji(emojiSizePx)
                onCursorMoved = { pos -> state.cursor = pos }
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                    override fun afterTextChanged(s: Editable?) {
                        if (s == null) return
                        state.text = s.toString()
                        state.cursor = selectionStart.coerceIn(0, s.length)
                        renderEmojiSpans(s, emojiSizePx)
                    }
                })
                setOnFocusChangeListener { _, focused ->
                    if (focused) currentOnFocused?.invoke()
                }
                editTextRegistry[state] = this
                state.clearFocusAction = {
                    clearFocus()
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    imm?.hideSoftInputFromWindow(windowToken, 0)
                }
            }
        },
        update = { view ->
            view.setTextColor(textColor)
            view.setHintTextColor(hintColor)
            if (view.text.toString() != state.text) {
                val caret = state.cursor.coerceIn(0, state.text.length)
                view.setText(state.text)
                view.setSelection(caret.coerceAtMost(view.text.length))
                view.reRenderEmoji(emojiSizePx)
            }
            editTextRegistry[state] = view
        },
    )

    DisposableEffect(state) {
        onDispose {
            editTextRegistry.remove(state)
            state.clearFocusAction = null
        }
    }
}

internal fun EditText.renderEmojiSpans(editable: Editable?, sizePx: Int) {
    if (editable == null) return
    runCatching {
        editable.getSpans(0, editable.length, ImageSpan::class.java).forEach(editable::removeSpan)
        if (editable.isEmpty()) return
        for (match in androidEmojiPattern.findAll(editable)) {
            val bitmap = WeiboEmojiBitmaps.getCached(match.value)?.asAndroidBitmap() ?: continue
            val drawable = BitmapDrawable(resources, bitmap).apply {
                setBounds(0, 0, sizePx, sizePx)
            }
            editable.setSpan(
                ImageSpan(drawable, DynamicDrawableSpan.ALIGN_CENTER),
                match.range.first,
                match.range.last + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }
}

private fun emojiRangeAt(text: CharSequence, offset: Int): IntRange? {
    for (match in androidEmojiPattern.findAll(text)) {
        if (WeiboEmojiBitmaps.getCached(match.value) == null && !WeiboEmojiBitmaps.hasUrl(match.value)) continue
        if (offset > match.range.first && offset <= match.range.last + 1) return match.range
    }
    return null
}

internal class EmojiSpanEditText(context: Context) : EditText(context) {
    var onCursorMoved: ((Int) -> Unit)? = null

    fun reRenderEmoji(sizePx: Int) {
        renderEmojiSpans(text, sizePx)
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        if (selStart >= 0) onCursorMoved?.invoke(selStart)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DEL && selectionStart == selectionEnd && selectionStart > 0) {
            val range = emojiRangeAt(text ?: return super.onKeyDown(keyCode, event), selectionStart)
            if (range != null) {
                text?.delete(range.first, range.last + 1)
                setSelection(range.first.coerceAtMost(text?.length ?: 0))
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val base = super.onCreateInputConnection(outAttrs) ?: return null
        return object : InputConnectionWrapper(base, true) {
            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (deleteWholeEmoji(beforeLength)) return true
                return super.deleteSurroundingText(beforeLength, afterLength)
            }

            override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
                if (deleteWholeEmoji(beforeLength)) return true
                return super.deleteSurroundingTextInCodePoints(beforeLength, afterLength)
            }
        }
    }

    private fun deleteWholeEmoji(beforeLength: Int): Boolean {
        if (beforeLength <= 0 || selectionStart != selectionEnd || selectionStart <= 0) return false
        val editable = text ?: return false
        val caret = selectionStart
        val range = emojiRangeAt(editable, caret) ?: return false
        if (caret - beforeLength > range.first) {
            editable.delete(range.first, caret)
            setSelection(range.first.coerceAtMost(editable.length))
            return true
        }
        return false
    }
}
