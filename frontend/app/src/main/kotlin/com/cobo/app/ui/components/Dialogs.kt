package com.cobo.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cobo.app.data.UpdateInfo
import com.cobo.app.data.User
import io.github.suqi8.coui.kmp.basic.Checkbox
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.TextField
import io.github.suqi8.coui.kmp.basic.TextFieldMode
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.layout.DialogDefaults
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.window.WindowDialog

/**
 * 输入弹窗。用 COUI 的 [WindowDialog]，面板是不透明背景，带窗口遮罩，
 * 避免深色下用半透明 surfaceContainer 拼卡片导致整块透明。
 */
@Composable
fun InputDialog(
    title: String,
    hint: String,
    initial: String = "",
    confirmText: String = "确定",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    var show by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { show = true }
    WindowDialog(
        show = show,
        title = title,
        outsideMargin = DpSize(16.dp, 8.dp),
        onDismissRequest = { show = false },
        onDismissFinished = {
            if (!settled) {
                settled = true
                val value = result
                if (value != null) onConfirm(value) else onDismiss()
            }
        },
    ) {
        Column {
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 4.dp),
                label = hint,
                backgroundMode = TextFieldMode.Rectangle,
                maxLines = 6,
                minLines = 3,
            )
            DialogButtonBar(
                negative = DialogButtonBarAction(text = "取消", onClick = { show = false }),
                positive = DialogButtonBarAction(
                    text = confirmText,
                    enabled = text.isNotBlank(),
                    onClick = {
                        if (text.isNotBlank()) {
                            result = text.trim()
                            show = false
                        }
                    },
                ),
            )
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "确定",
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    var show by remember { mutableStateOf(false) }
    var confirmed by remember { mutableStateOf(false) }
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { show = true }
    WindowDialog(
        show = show,
        title = title,
        summary = message,
        outsideMargin = DpSize(16.dp, 8.dp),
        onDismissRequest = { show = false },
        onDismissFinished = {
            if (!settled) {
                settled = true
                if (confirmed) onConfirm() else onDismiss()
            }
        },
    ) {
        DialogButtonBar(
            negative = DialogButtonBarAction(text = "取消", onClick = { show = false }),
            positive = DialogButtonBarAction(
                text = confirmText,
                onClick = {
                    confirmed = true
                    show = false
                },
            ),
        )
    }
}

/**
 * 更新提示。release notes 按 Markdown 渲染，可能有几十行，正文自己滚动、封顶 260dp，别把按钮顶出屏幕。
 * 「跳过本次更新」是个勾选框：勾上后不管点稍后还是点外面关掉，都记成跳过这个版本；点下载更新则照常下载。
 * 三个出口等窗口收完再分发，避免和关闭动画抢时序。
 */
@Composable
fun UpdateDialog(
    info: UpdateInfo,
    onDismiss: () -> Unit,
    onSkip: () -> Unit,
    onDownload: () -> Unit,
) {
    var show by remember { mutableStateOf(false) }
    var download by remember { mutableStateOf(false) }
    var skip by remember { mutableStateOf(false) }
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { show = true }
    val haptic = LocalHapticFeedback.current
    val textMargin = DialogDefaults.insideMargin.width
    WindowDialog(
        show = show,
        title = "发现新版本 ${info.version}",
        summary = if (info.prerelease) "预发布版本" else "正式版本",
        outsideMargin = DpSize(16.dp, 8.dp),
        onDismissRequest = { show = false },
        onDismissFinished = {
            if (!settled) {
                settled = true
                when {
                    download -> onDownload()
                    skip -> onSkip()
                    else -> onDismiss()
                }
            }
        },
    ) {
        Column {
            if (info.notes.isNotBlank()) {
                MarkdownText(
                    info.notes,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = textMargin),
                )
            }
            Row(
                // 和 COUI 安全对话框的勾选行一致：起点让圆圈的内环对齐上面正文的左缘。
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = textMargin, top = 12.dp)
                    .toggleable(
                        value = skip,
                        interactionSource = null,
                        indication = null,
                        role = Role.Checkbox,
                        onValueChange = {
                            skip = it
                            haptic.performHapticFeedback(
                                if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                            )
                        },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(state = ToggleableState(skip), onClick = null)
                Text(
                    "跳过本次更新",
                    fontSize = 14.sp,
                    color = COUITheme.colorScheme.onBackgroundVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            DialogButtonBar(
                negative = DialogButtonBarAction(text = "稍后", onClick = { show = false }),
                positive = DialogButtonBarAction(
                    text = "下载更新",
                    recommended = true,
                    onClick = {
                        download = true
                        show = false
                    },
                ),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
fun UserRow(user: User, onClick: () -> Unit, subtitle: String = "") {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Avatar(user.avatar, 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(user.name, fontSize = 15.sp)
            if (subtitle.isNotBlank()) {
                Text(subtitle, fontSize = 12.sp, color = COUITheme.colorScheme.onSurfaceSecondary)
            }
        }
        TextButton(text = "查看", onClick = onClick)
    }
}
