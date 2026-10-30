package com.cobo.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cobo.app.App
import com.cobo.app.data.UpdateInfo
import com.cobo.app.data.Updater
import com.cobo.app.data.User
import io.github.suqi8.coui.kmp.basic.Checkbox
import io.github.suqi8.coui.kmp.basic.CircularProgressIndicator
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.TextField
import io.github.suqi8.coui.kmp.basic.TextFieldMode
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.layout.DialogDefaults
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * 输入弹窗。和 COUI 示例里那张单行输入框一样用 [OverlayDialog]：面板是磨砂玻璃，
 * 按钮栏是浮在面板上的半透明胶囊。用独立窗口的 WindowDialog 时采不到页面 backdrop，
 * 玻璃会退回不透明底色，胶囊直接糊在面板上。
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
    OverlayDialog(
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
    OverlayDialog(
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

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, 3)
    val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
    return "%.1f %s".format(Locale.US, value, units[digitGroups])
}

/**
 * 更新提示。release notes 按 Markdown 渲染，可能有几十行，正文自己滚动、封顶 260dp，别把按钮顶出屏幕。
 * 「跳过本次更新」是个勾选框：勾上后不管点稍后还是点外面关掉，都记成跳过这个版本；点下载更新则照常下载。
 * 开启「应用内更新」时直接在弹窗内拉取 release 的 APK 并调起安装请求。
 *
 * [preview] 为 true 表示这是从更新日志页手动放的一遍：不写跳过记录，只用来核对界面与下载速度。
 */
@Composable
fun UpdateDialog(
    info: UpdateInfo,
    onDismiss: () -> Unit,
    onSkip: () -> Unit,
    onDownload: () -> Unit,
    preview: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var show by remember { mutableStateOf(false) }
    var download by remember { mutableStateOf(false) }
    var skip by remember { mutableStateOf(false) }
    var settled by remember { mutableStateOf(false) }

    val inApp = App.settings.inAppUpdate && !info.apkUrl.isNullOrBlank()
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var progressText by remember { mutableStateOf("") }
    var downloadError by remember { mutableStateOf<String?>(null) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(Unit) { show = true }
    val haptic = LocalHapticFeedback.current
    val textMargin = DialogDefaults.insideMargin.width

    fun startInAppDownload() {
        val apkUrl = info.apkUrl ?: return
        downloading = true
        downloadError = null
        progress = 0f
        progressText = "正在准备下载…"
        // 速度按相邻两次回调的差值算，下载中自己看得到实际带宽。
        var lastBytes = 0L
        var lastAt = 0L
        var speed = 0L
        downloadJob = scope.launch {
            try {
                val file = Updater.downloadApk(
                    context = context,
                    url = apkUrl,
                    version = info.version,
                    // 预览就是来量速度的，缓存里有同版本也重下一遍。
                    force = preview,
                    onProgress = { p, current, total ->
                        val now = System.currentTimeMillis()
                        if (lastAt != 0L && now > lastAt) {
                            speed = ((current - lastBytes) * 1000 / (now - lastAt)).coerceAtLeast(0L)
                        }
                        lastBytes = current
                        lastAt = now
                        progress = p
                        val size = if (total > 0) {
                            "${(p * 100).toInt()}% (${formatSize(current)} / ${formatSize(total)})"
                        } else {
                            formatSize(current)
                        }
                        progressText = if (speed > 0) "$size · ${formatSize(speed)}/s" else size
                    },
                )
                progressText = "下载完成，正在调起安装…"
                delay(300)
                settled = true
                show = false
                App.dismissUpdate()
                Updater.installApk(context, file)
            } catch (c: CancellationException) {
                // 用户取消
            } catch (t: Throwable) {
                downloading = false
                downloadError = t.message ?: "下载失败"
            }
        }
    }

    OverlayDialog(
        show = show,
        title = when {
            downloading -> "正在下载新版本"
            downloadError != null -> "更新下载失败"
            else -> "发现新版本 ${info.version}"
        },
        summary = when {
            downloading -> "正在拉取安装包…"
            downloadError != null -> "请重试或前往浏览器下载"
            info.prerelease -> "预发布版本"
            else -> "正式版本"
        },
        outsideMargin = DpSize(16.dp, 8.dp),
        onDismissRequest = {
            if (downloading) {
                downloadJob?.cancel()
                downloading = false
            }
            show = false
        },
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
            when {
                downloading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = textMargin, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(44.dp),
                            strokeWidth = 3.dp,
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            progressText,
                            fontSize = 14.sp,
                            color = COUITheme.colorScheme.onBackground,
                        )
                    }
                    DialogButtonBar(
                        negative = DialogButtonBarAction(
                            text = "取消",
                            onClick = {
                                downloadJob?.cancel()
                                downloading = false
                                show = false
                            },
                        ),
                        positive = null,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                downloadError != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = textMargin, vertical = 12.dp),
                    ) {
                        Text(
                            downloadError ?: "下载遇到错误",
                            fontSize = 14.sp,
                            color = COUITheme.colorScheme.error,
                        )
                    }
                    DialogButtonBar(
                        negative = DialogButtonBarAction(
                            text = "浏览器下载",
                            onClick = {
                                download = true
                                show = false
                            },
                        ),
                        positive = DialogButtonBarAction(
                            text = "重试",
                            recommended = true,
                            onClick = { startInAppDownload() },
                        ),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                else -> {
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
                    if (!preview) {
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
                    }
                    DialogButtonBar(
                        negative = DialogButtonBarAction(text = "稍后", onClick = { show = false }),
                        positive = DialogButtonBarAction(
                            text = if (inApp) "立即安装" else "下载更新",
                            recommended = true,
                            onClick = {
                                if (inApp) {
                                    startInAppDownload()
                                } else {
                                    download = true
                                    show = false
                                }
                            },
                        ),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
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
