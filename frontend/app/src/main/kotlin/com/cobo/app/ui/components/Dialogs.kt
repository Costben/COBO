package com.cobo.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cobo.app.data.UpdateInfo
import com.cobo.app.data.User
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.TextField
import io.github.suqi8.coui.kmp.basic.TextFieldMode
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
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
 * 更新提示。release notes 可能有几十行，正文自己滚动、封顶 220dp，别把按钮顶出屏幕。
 * 三个出口分开记：稍后 / 跳过此版本 / 下载更新，等窗口收完再分发，避免和关闭动画抢时序。
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
                Text(
                    info.notes,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = COUITheme.colorScheme.onSurfaceSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                )
            }
            TextButton(
                text = "跳过此版本",
                modifier = Modifier.padding(start = 8.dp),
                onClick = {
                    skip = true
                    show = false
                },
            )
            DialogButtonBar(
                negative = DialogButtonBarAction(text = "稍后", onClick = { show = false }),
                positive = DialogButtonBarAction(
                    text = "下载更新",
                    onClick = {
                        download = true
                        show = false
                    },
                ),
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
