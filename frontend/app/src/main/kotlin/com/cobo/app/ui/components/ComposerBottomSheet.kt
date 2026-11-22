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
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage
import com.cobo.app.App
import com.cobo.app.data.ComposerTrigger
import com.cobo.app.data.FallbackWeiboEmojiSections
import com.cobo.app.data.TopicSuggestion
import com.cobo.app.data.User
import com.cobo.app.data.WeiboEmoji
import com.cobo.app.data.WeiboEmojiBitmaps
import com.cobo.app.data.WeiboVisibility
import com.cobo.app.data.applyComposerReplacement
import com.cobo.app.data.detectComposerTrigger
import com.cobo.app.ui.CoboIcons
import io.github.suqi8.coui.kmp.basic.CapsuleOutlineShape
import io.github.suqi8.coui.kmp.basic.ChipTabData
import io.github.suqi8.coui.kmp.basic.ChipTabGroup
import io.github.suqi8.coui.kmp.basic.GradientStrokeStyle
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.InnerShadowStyle
import io.github.suqi8.coui.kmp.basic.MaterialStrokeParams
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.materialShadowEdge
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Community
import io.github.suqi8.coui.kmp.icon.extended.Contacts
import io.github.suqi8.coui.kmp.icon.extended.Lock
import io.github.suqi8.coui.kmp.icon.extended.Ok
import io.github.suqi8.coui.kmp.overlay.OverlayBottomSheet
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.squircle.isSquircleEnabled
import io.github.suqi8.coui.kmp.squircle.squircleSurface
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val emojiCodePattern = Regex("\\[[^\\]]+\\]")

@Stable
class ComposerInputState internal constructor(initialText: String) {
    internal var editText: EmojiSpanEditText? = null

    var text by mutableStateOf(initialText)
        internal set

    var cursor by mutableIntStateOf(initialText.length)
        internal set

    fun insert(code: String, cursorBack: Int = 0) {
        val edit = editText
        if (edit == null) {
            text += code
            cursor = (text.length - cursorBack).coerceIn(0, text.length)
            return
        }
        val editable = edit.text ?: return
        val start = edit.selectionStart.coerceIn(0, editable.length)
        val end = edit.selectionEnd.coerceIn(start, editable.length)
        editable.replace(start, end, code)
        val nextPos = (start + code.length - cursorBack).coerceIn(0, editable.length)
        edit.setSelection(nextPos)
        cursor = nextPos
    }

    fun replaceTrigger(trigger: ComposerTrigger, chosenValue: String) {
        val edit = editText
        if (edit == null) {
            val (next, nextCursor) = applyComposerReplacement(text, trigger, chosenValue)
            text = next
            cursor = nextCursor
            return
        }
        val editable = edit.text ?: return
        val start = trigger.replaceStart.coerceIn(0, editable.length)
        val end = trigger.replaceEnd.coerceIn(start, editable.length)
        val token = when (trigger) {
            is ComposerTrigger.Mention -> "@${chosenValue.trim().removePrefix("@")} "
            is ComposerTrigger.Topic -> "#${chosenValue.trim().trim('#')}# "
        }
        editable.replace(start, end, token)
        val nextPos = (start + token.length).coerceIn(0, editable.length)
        edit.setSelection(nextPos)
        cursor = nextPos
    }

    fun focus() {
        val edit = editText ?: return
        edit.requestFocus()
        edit.post {
            val imm = edit.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            @Suppress("DEPRECATION")
            imm?.showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    fun clearFocus() {
        val edit = editText ?: return
        edit.clearFocus()
        val imm = edit.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(edit.windowToken, 0)
    }
}

@Composable
fun rememberComposerInputState(initialText: String = ""): ComposerInputState =
    remember { ComposerInputState(initialText) }

@Composable
fun ComposerBottomSheet(
    title: String,
    hint: String,
    submitText: String = "发布",
    initialText: String = "",
    allowEmpty: Boolean = false,
    showVisibility: Boolean = false,
    maxLength: Int = 500,
    onDismiss: () -> Unit,
    onSubmit: suspend (String, WeiboVisibility) -> Boolean,
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val input = rememberComposerInputState(initialText)
    var emojiPanel by remember { mutableStateOf(false) }
    var visibility by remember { mutableStateOf(WeiboVisibility.Public) }
    var visibilityDialog by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }

    var shown by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var afterExit by remember { mutableStateOf<(() -> Unit)?>(null) }

    LaunchedEffect(Unit) {
        shown = true
        delay(80)
        if (!closing && !emojiPanel) input.focus()
    }

    fun leave(next: () -> Unit) {
        if (closing) return
        closing = true
        input.clearFocus()
        focusManager.clearFocus()
        afterExit = next
        shown = false
    }

    val activeTrigger = remember(input.text, input.cursor, emojiPanel) {
        if (emojiPanel) null else detectComposerTrigger(input.text, input.cursor)
    }

    val density = LocalDensity.current
    val keyboard = with(density) { WindowInsets.ime.getBottom(density).toDp() }
    val keyboardGapProgress = (keyboard / ComposerPanel.KeyboardGapRange).coerceIn(0f, 1f)
    val bottomGap = ComposerPanel.BottomInset +
        (ComposerPanel.KeyboardBottomInset - ComposerPanel.BottomInset) * keyboardGapProgress

    val canSubmit = !submitting && (allowEmpty || input.text.isNotBlank())

    OverlayBottomSheet(
        show = shown,
        showDragHandle = false,
        backgroundColor = ComposerPanel.panelColor(),
        insideMargin = DpSize(ComposerPanel.SideInset, bottomGap),
        panelPaddingBottom = 0.dp,
        allowDismiss = !submitting,
        onDismissRequest = { if (!submitting) leave(onDismiss) },
        onDismissFinished = { afterExit?.invoke() },
    ) {
        BackHandler(enabled = shown && !closing) {
            when {
                visibilityDialog -> visibilityDialog = false
                emojiPanel -> emojiPanel = false
                !submitting -> leave(onDismiss)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ComposerPanel.ToolbarHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CloseCapsule(onClick = { if (!submitting) leave(onDismiss) })
            Text(
                text = title,
                style = COUITheme.textStyles.body1,
                fontWeight = FontWeight.Bold,
                color = COUITheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            )
            Spacer(Modifier.width(60.dp))
        }
        Spacer(Modifier.height(ComposerPanel.ToolbarToBodyGap))

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val belowField = (if (activeTrigger != null) ComposerPanel.SuggestHeight else 0.dp) +
                (if (emojiPanel) ComposerPanel.EmojiHeight else 0.dp) +
                ComposerPanel.ToolsGap + ComposerPanel.ToolsHeight +
                ComposerPanel.ActionGap + ComposerPanel.ActionHeight
            val editorMax = (maxHeight - belowField).coerceAtLeast(ComposerPanel.MinEditorHeight)
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = editorMax)
                    .squircleSurface(
                        color = ComposerPanel.surfaceColor(),
                        cornerRadius = ComposerPanel.EditorRadius,
                    ),
            ) {
                ComposerEditText(
                    state = input,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .padding(horizontal = 12.dp),
                    hint = hint,
                    maxCount = maxLength,
                    minLines = 2,
                    maxLines = 5,
                    textStyle = COUITheme.textStyles.body1,
                    onFocused = { emojiPanel = false },
                )
                Text(
                    text = "${input.text.length}/$maxLength",
                    style = trimmedLineHeight,
                    fontSize = 12.sp,
                    lineHeight = 12.sp,
                    color = COUITheme.colorScheme.onSurfaceSecondary,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                )
            }
        }

        if (activeTrigger != null) {
            ComposerSuggestPanel(
                trigger = activeTrigger,
                modifier = Modifier.padding(top = 8.dp),
                onPickMention = { user -> input.replaceTrigger(activeTrigger, user.name) },
                onPickTopic = { topic -> input.replaceTrigger(activeTrigger, topic.title) },
            )
        }

        if (emojiPanel) {
            WeiboEmojiPanel(
                modifier = Modifier
                    .height(212.dp)
                    .padding(top = 8.dp),
                onPick = { emoji -> input.insert(emoji.phrase) },
            )
        }

        Spacer(Modifier.height(ComposerPanel.ToolsGap))
        Row(
            Modifier
                .fillMaxWidth()
                .height(ComposerPanel.ToolsHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolbarIcon(CoboIcons.Emoji, selected = emojiPanel) {
                emojiPanel = !emojiPanel
                if (emojiPanel) {
                    input.clearFocus()
                    focusManager.clearFocus()
                } else {
                    input.focus()
                }
            }
            Spacer(Modifier.width(8.dp))
            ToolbarIcon(CoboIcons.At, selected = activeTrigger is ComposerTrigger.Mention) {
                emojiPanel = false
                input.insert("@")
                input.focus()
            }
            Spacer(Modifier.width(8.dp))
            ToolbarIcon(CoboIcons.Topic, selected = activeTrigger is ComposerTrigger.Topic) {
                emojiPanel = false
                input.insert("##", cursorBack = 1)
                input.focus()
            }
            Spacer(Modifier.weight(1f))
            if (showVisibility) {
                VisibilityPill(visibility = visibility) {
                    input.clearFocus()
                    focusManager.clearFocus()
                    visibilityDialog = true
                }
            }
        }

        Spacer(Modifier.height(ComposerPanel.ActionGap))
        ActionCapsule(
            text = if (submitting) "发送中…" else submitText,
            enabled = canSubmit,
            onClick = {
                if (submitting) return@ActionCapsule
                submitting = true
                scope.launch {
                    val ok = onSubmit(input.text.trim(), visibility)
                    submitting = false
                    if (ok) leave(onDismiss)
                }
            },
        )
    }

    if (visibilityDialog) {
        VisibilityPickerDialog(
            selected = visibility,
            onPick = {
                visibility = it
                visibilityDialog = false
                if (!emojiPanel) input.focus()
            },
            onDismiss = {
                visibilityDialog = false
                if (!emojiPanel) input.focus()
            },
        )
    }
}

@Composable
fun ComposerSuggestPanel(
    trigger: ComposerTrigger,
    modifier: Modifier = Modifier,
    onPickMention: (User) -> Unit,
    onPickTopic: (TopicSuggestion) -> Unit,
) {
    val scheme = COUITheme.colorScheme
    var loading by remember { mutableStateOf(false) }
    var users by remember { mutableStateOf<List<User>>(emptyList()) }
    var topics by remember { mutableStateOf<List<TopicSuggestion>>(emptyList()) }

    LaunchedEffect(trigger) {
        loading = true
        if (trigger.query.isNotBlank()) delay(150)
        try {
            when (trigger) {
                is ComposerTrigger.Mention -> {
                    users = App.repo.mentionCandidates(trigger.query)
                    topics = emptyList()
                }
                is ComposerTrigger.Topic -> {
                    topics = App.repo.topicCandidates(trigger.query)
                    users = emptyList()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            users = emptyList()
            topics = emptyList()
        } finally {
            loading = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 160.dp)
            .squircleSurface(
                color = ComposerPanel.surfaceColor(),
                cornerRadius = 16.dp,
            )
            .scrollEndHaptic()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
    ) {
        when (trigger) {
            is ComposerTrigger.Mention -> {
                if (loading && users.isEmpty()) {
                    SuggestHintRow("正在查找好友…")
                } else if (users.isEmpty()) {
                    SuggestHintRow(if (trigger.query.isBlank()) "暂无关注好友，输入昵称搜索" else "没有找到「${trigger.query}」")
                } else {
                    users.forEach { user ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPickMention(user) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(user.avatar, 28.dp, verifiedKind = user.verifiedKind)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = user.name,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = scheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (user.description.isNotBlank()) {
                                    Text(
                                        text = user.description,
                                        fontSize = 11.sp,
                                        color = scheme.onSurfaceSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            is ComposerTrigger.Topic -> {
                if (loading && topics.isEmpty()) {
                    SuggestHintRow("正在搜索话题…")
                } else if (topics.isEmpty()) {
                    SuggestHintRow("输入关键词添加 #话题#")
                } else {
                    topics.forEach { topic ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPickTopic(topic) }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = CoboIcons.Topic,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = scheme.primary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "#${topic.title}#",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = scheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (topic.desc.isNotBlank()) {
                                    Text(
                                        text = topic.desc,
                                        fontSize = 11.sp,
                                        color = scheme.onSurfaceSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestHintRow(message: String) {
    Text(
        text = message,
        fontSize = 12.sp,
        color = COUITheme.colorScheme.onSurfaceSecondary,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@Composable
fun VisibilityPill(
    visibility: WeiboVisibility,
    onClick: () -> Unit,
) {
    val capsuleShape = rememberComposerCapsuleShape()
    val scheme = COUITheme.colorScheme
    val active = visibility != WeiboVisibility.Public
    val icon = when (visibility) {
        WeiboVisibility.Public -> COUIIcons.Community
        WeiboVisibility.Circle -> COUIIcons.Contacts
        WeiboVisibility.Self -> COUIIcons.Lock
    }
    Row(
        modifier = Modifier
            .clip(capsuleShape)
            .background(if (active) scheme.secondaryContainer else ComposerPanel.surfaceColor())
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "可见范围",
            modifier = Modifier.size(15.dp),
            tint = if (active) scheme.onSecondaryContainer else scheme.onSurfaceSecondary,
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = visibility.label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (active) scheme.onSecondaryContainer else scheme.onSurfaceSecondary,
            maxLines = 1,
        )
    }
}

@Composable
fun VisibilityPickerDialog(
    selected: WeiboVisibility,
    onPick: (WeiboVisibility) -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    val squircleEnabled = isSquircleEnabled()
    val rowShape = remember(squircleEnabled) { SquircleShape(14.dp, squircleEnabled = squircleEnabled) }
    OverlayDialog(show = true, title = "谁可以看", onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
            WeiboVisibility.entries.forEach { option ->
                val icon = when (option) {
                    WeiboVisibility.Public -> COUIIcons.Community
                    WeiboVisibility.Circle -> COUIIcons.Contacts
                    WeiboVisibility.Self -> COUIIcons.Lock
                }
                val isSelected = option == selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(rowShape)
                        .clickable { onPick(option) }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = if (isSelected) scheme.primary else scheme.onSurface,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = option.label,
                            style = COUITheme.textStyles.body1,
                            color = scheme.onSurface,
                        )
                        Text(
                            text = option.summary,
                            fontSize = 12.sp,
                            color = scheme.onSurfaceSecondary,
                        )
                    }
                    if (isSelected) {
                        Icon(
                            imageVector = COUIIcons.Ok,
                            contentDescription = "已选择",
                            modifier = Modifier.size(20.dp),
                            tint = scheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WeiboEmojiPanel(
    modifier: Modifier = Modifier,
    onPick: (WeiboEmoji) -> Unit,
) {
    var sections by remember { mutableStateOf(FallbackWeiboEmojiSections) }
    var selectedSection by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        val loaded = App.repo.emotions()
        if (loaded.isNotEmpty()) {
            sections = loaded
            selectedSection = 0
        }
    }

    val safeIndex = selectedSection.coerceIn(0, (sections.size - 1).coerceAtLeast(0))
    val tabs = remember(sections) { sections.map { ChipTabData(it.label) } }
    val items = sections.getOrNull(safeIndex)?.items.orEmpty()
    val hasImages = remember(items) { items.any { it.url.isNotBlank() } }
    val isPureUnicode = remember(items) { items.isNotEmpty() && !hasImages && !items.first().phrase.startsWith("[") }
    val columns = if (hasImages || isPureUnicode) 7 else 4

    Column(modifier.fillMaxWidth()) {
        ChipTabGroup(
            tabs = tabs,
            selectedIndex = safeIndex,
            onSelectedChange = { selectedSection = it },
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(6.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .scrollEndHaptic()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items.chunked(columns).forEach { row ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    row.forEach { emoji ->
                        if (emoji.url.isNotBlank()) {
                            Box(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onPick(emoji) }
                                    .padding(vertical = 5.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                AsyncImage(
                                    model = emoji.url,
                                    contentDescription = emoji.phrase,
                                    modifier = Modifier.size(30.dp),
                                )
                            }
                        } else if (isPureUnicode) {
                            Box(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onPick(emoji) }
                                    .padding(vertical = 5.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(emoji.phrase, fontSize = 22.sp)
                            }
                        } else {
                            Box(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(ComposerPanel.surfaceColor())
                                    .clickable { onPick(emoji) }
                                    .padding(horizontal = 4.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = emoji.phrase.removeSurrounding("[", "]"),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = COUITheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun ComposerEditText(
    state: ComposerInputState,
    modifier: Modifier = Modifier,
    hint: String = "",
    maxCount: Int = 500,
    minLines: Int = 2,
    maxLines: Int = 5,
    textStyle: TextStyle = COUITheme.textStyles.body1,
    onFocused: (() -> Unit)? = null,
) {
    val textColor = COUITheme.colorScheme.onSurface.toArgb()
    val hintColor = COUITheme.colorScheme.onSurfaceSecondary.toArgb()
    val fontSizeSp = textStyle.fontSize.let { if (it.type == TextUnitType.Sp) it.value else 16f }
    val emojiSizePx = with(LocalDensity.current) { (fontSizeSp * 1.2f).sp.toPx() }.toInt().coerceAtLeast(16)
    val currentOnFocused by rememberUpdatedState(onFocused)

    LaunchedEffect(state.text, emojiSizePx) {
        val codes = emojiCodePattern.findAll(state.text).map { it.value }.distinct().toList()
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
            state.editText?.reRenderEmoji(emojiSizePx)
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
                state.editText = this
            }
        },
        update = { view ->
            view.setTextColor(textColor)
            view.setHintTextColor(hintColor)
            if (view.text.toString() != state.text) {
                val caret = view.selectionStart.coerceAtLeast(0)
                view.setText(state.text)
                view.setSelection(caret.coerceAtMost(view.text.length))
                view.reRenderEmoji(emojiSizePx)
            }
            state.editText = view
        },
    )

    DisposableEffect(state) {
        onDispose { state.editText = null }
    }
}

internal fun EditText.renderEmojiSpans(editable: Editable?, sizePx: Int) {
    if (editable == null) return
    runCatching {
        editable.getSpans(0, editable.length, ImageSpan::class.java).forEach(editable::removeSpan)
        if (editable.isEmpty()) return
        for (match in emojiCodePattern.findAll(editable)) {
            val bitmap = WeiboEmojiBitmaps.getCached(match.value) ?: continue
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
    for (match in emojiCodePattern.findAll(text)) {
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

private object ComposerPanel {
    val SideInset = 10.dp
    val BottomInset = 30.dp
    val KeyboardBottomInset = 4.dp
    val KeyboardGapRange = 80.dp
    val ToolbarHeight = 50.dp
    val ToolbarButtonHeight = 40.dp
    val ToolbarToBodyGap = 8.dp
    val EditorRadius = 20.dp
    val MinEditorHeight = 76.dp
    val SuggestHeight = 168.dp
    val EmojiHeight = 220.dp
    val ToolsHeight = 36.dp
    val ToolsGap = 8.dp
    val ActionGap = 8.dp
    val ActionHeight = 44.dp
    val ToolbarButtonStroke = MaterialStrokeParams(
        edgeAlpha = 0.5f,
        edgeWidth = 2.dp,
        shadowFadeIn = 0.03f,
    )
    val ToolbarFillDark = Color(0x20FFFFFF)
    val ToolbarHaloLight = Shadow(
        radius = 16.dp,
        color = Color.Black,
        offset = DpOffset(0.dp, 6.dp),
        alpha = 0.12f,
    )
    val ToolbarHaloDark = Shadow(
        radius = 16.dp,
        color = Color.White,
        offset = DpOffset(0.dp, 6.dp),
        alpha = 0.05f,
    )
    val ActionEdgeGradient = listOf(
        Color.White.copy(alpha = 0.18f),
        Color.Transparent,
        Color.White.copy(alpha = 0.15f),
    )

    /**
     * 面板底色。跟顶栏那个玻璃按钮同一档：深色下比页面再压暗一档，浅色下比页面提亮一档，
     * 展开来不会是一整片跟页面同色的灰/白。
     */
    @Composable
    fun panelColor(): Color {
        val bg = COUITheme.colorScheme.background
        return if (bg.luminance() < 0.5f) lerp(bg, Color.Black, 0.2f) else lerp(bg, Color.White, 0.4f)
    }

    /** 面板里的输入框、候选条、表情格：深色下退到页面底色，浅色下用卡片白，避免整块纯白/浅灰。 */
    @Composable
    fun surfaceColor(): Color {
        val scheme = COUITheme.colorScheme
        return if (scheme.background.luminance() < 0.5f) scheme.background else scheme.surfaceContainer
    }
}

@Composable
private fun rememberComposerCapsuleShape(): Shape {
    val enabled = isSquircleEnabled()
    return remember(enabled) { CapsuleOutlineShape(squircleEnabled = enabled) }
}

@Composable
private fun PanelToolbarButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val isDark = COUITheme.colorScheme.background.luminance() < 0.5f
    val fill = if (isDark) ComposerPanel.ToolbarFillDark else Color.Transparent
    val halo = if (isDark) ComposerPanel.ToolbarHaloDark else ComposerPanel.ToolbarHaloLight
    val capsuleShape = rememberComposerCapsuleShape()
    Box {
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawWithContent {
                    val capsule = Path().apply {
                        addOutline(capsuleShape.createOutline(size, layoutDirection, this@drawWithContent))
                    }
                    clipPath(capsule, ClipOp.Difference) { this@drawWithContent.drawContent() }
                }
                .dropShadow(capsuleShape, halo),
        )
        Box(
            modifier = Modifier
                .clip(capsuleShape)
                .background(fill)
                .materialShadowEdge(
                    stroke = ComposerPanel.ToolbarButtonStroke,
                    edgeStyle = GradientStrokeStyle.EDGE_STYLE_1,
                    shadowStyle = InnerShadowStyle.SHADOW_STYLE_1,
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@Composable
private fun CloseCapsule(onClick: () -> Unit) {
    PanelToolbarButton(onClick = onClick) {
        Box(
            modifier = Modifier
                .height(ComposerPanel.ToolbarButtonHeight)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "关闭",
                style = trimmedLineHeight,
                fontSize = 14.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Medium,
                color = COUITheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
private fun ActionCapsule(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val capsuleShape = rememberComposerCapsuleShape()
    val scheme = COUITheme.colorScheme
    val actionGradient = listOf(scheme.primary, lerp(scheme.primary, Color.White, 0.14f))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ComposerPanel.ActionHeight)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .clip(capsuleShape)
            .background(Brush.verticalGradient(actionGradient))
            .border(
                BorderStroke(1.dp, Brush.verticalGradient(ComposerPanel.ActionEdgeGradient)),
                capsuleShape,
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = trimmedLineHeight,
            fontSize = 16.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
            color = scheme.onPrimary,
        )
    }
}

@Composable
private fun ToolbarIcon(
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = if (pressed) {
            tween(durationMillis = 70)
        } else {
            spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium)
        },
        label = "composerToolbarIconPressScale",
    )
    val squircleEnabled = isSquircleEnabled()
    val shape = remember(squircleEnabled) { SquircleShape(8.dp, squircleEnabled = squircleEnabled) }
    Box(
        modifier = Modifier
            .size(36.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(shape)
            .background(if (selected) COUITheme.colorScheme.secondaryContainer else Color.Transparent)
            .clickable(
                role = Role.Button,
                interactionSource = interactionSource,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = if (selected) {
                COUITheme.colorScheme.onSecondaryContainer
            } else {
                COUITheme.colorScheme.onSurfaceSecondary
            },
        )
    }
}

private val trimmedLineHeight: TextStyle = TextStyle(
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both,
    ),
)
