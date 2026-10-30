package com.cobo.app.screen

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import com.cobo.app.App
import com.cobo.app.ui.NavState
import com.cobo.app.ui.PinnedBarDensity
import com.cobo.app.ui.pageContainerColor
import com.cobo.app.ui.CoboIcons
import com.cobo.app.ui.components.ActionIcon
import com.cobo.app.ui.components.BackIconButton
import com.cobo.app.ui.components.LoadingState
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.TextField
import io.github.suqi8.coui.kmp.basic.TextFieldMode
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Add
import io.github.suqi8.coui.kmp.icon.extended.Close
import io.github.suqi8.coui.kmp.icon.extended.Image
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.launch

private val EMOJIS = listOf(
    "😀", "😁", "😂", "🤣", "😃", "😄", "😅", "😆", "😉", "😊",
    "😋", "😎", "😍", "😘", "😗", "😙", "😚", "🙂", "🤗", "🤩",
    "🤔", "🤨", "😐", "😑", "😶", "🙄", "😏", "😣", "😥", "😮",
    "🤐", "😯", "😪", "😫", "🥱", "😴", "😌", "😛", "😜", "😝",
    "🤤", "😒", "😓", "😔", "😕", "🙃", "🤑", "😲", "🙁", "😖",
    "😞", "😟", "😤", "😢", "😭", "😦", "😧", "😨", "😩", "🤯",
    "😬", "😰", "😱", "🥵", "🥶", "😳", "🤪", "😵", "🥴", "😠",
    "😡", "🤬", "😷", "🤒", "🤕", "🤢", "🤮", "🤧", "😇", "🥳",
    "🥺", "🤠", "🤡", "🤥", "🤫", "🤭", "🧐", "🤓", "😈", "👿",
    "💀", "👻", "👽", "🤖", "💩", "😺", "😸", "😹", "😻", "😼",
)

@Composable
fun PublishScreen(nav: NavState, draft: String) {
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf(draft) }
    var images by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var publishing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var emojiOpen by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(9)
    ) { uris ->
        if (uris.isNotEmpty()) {
            images = (images + uris).distinct().take(9)
        }
    }

    fun leave() {
        if (text.isNotBlank()) App.store.addDraft(text)
        nav.pop()
    }

    BackHandler {
        if (!publishing) leave()
    }

    Scaffold(
        containerColor = pageContainerColor(),
        topBar = {
            PinnedBarDensity {
                SmallTopAppBar(
                    color = pageContainerColor(),
                    title = "发微博",
                    navigationIcon = { BackIconButton { leave() } },
                    actions = {
                        TextButton(
                            text = if (publishing) "发布中…" else "发布",
                            modifier = Modifier.testTag("publish_send"),
                            enabled = !publishing && (text.isNotBlank() || images.isNotEmpty()),
                            onClick = {
                                scope.launch {
                                    publishing = true
                                    error = null
                                    try {
                                        val ids = ArrayList<String>()
                                        images.forEachIndexed { index, uri ->
                                            progress = "上传图片 ${index + 1}/${images.size}"
                                            ids.add(App.repo.uploadImage(uri))
                                        }
                                        progress = "正在发布…"
                                        App.repo.publish(text.trim(), ids)
                                        App.notifyPublished()
                                        progress = ""
                                        nav.pop()
                                    } catch (t: Throwable) {
                                        error = t.message ?: "发布失败"
                                    } finally {
                                        publishing = false
                                    }
                                }
                            },
                        )
                    },
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().testTag("publish_input"),
                label = "分享新鲜事…",
                backgroundMode = TextFieldMode.Line,
                minLines = 7,
                maxLines = 14,
            )
            Spacer(Modifier.height(10.dp))
            if (images.isNotEmpty()) {
                ImagePickerGrid(images, onRemove = { uri -> images = images - uri }) {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(COUITheme.colorScheme.surfaceContainerHigh)
                            .clickable {
                                picker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(COUIIcons.Image, "添加图片", Modifier.size(28.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "最多 9 张图片",
                        fontSize = 12.sp,
                        color = COUITheme.colorScheme.onSurfaceSecondary,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ActionIcon(COUIIcons.Image, "", onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                })
                ActionIcon(CoboIcons.Emoji, "", onClick = { emojiOpen = true })
                ActionIcon(CoboIcons.At, "", onClick = { text += "@" })
                ActionIcon(CoboIcons.Topic, "", onClick = { text += "##" })
                Spacer(Modifier.weight(1f))
                Text(
                    "${text.length}",
                    fontSize = 12.sp,
                    color = COUITheme.colorScheme.onSurfaceSecondary,
                )
            }
            if (progress.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(progress, fontSize = 13.sp, color = COUITheme.colorScheme.primary)
            }
            if (publishing) {
                LoadingState()
            }
            if (error != null) {
                Spacer(Modifier.height(10.dp))
                Text(error!!, fontSize = 13.sp, color = COUITheme.colorScheme.error)
            }
        }
    }

    if (emojiOpen) {
        EmojiDialog(
            onDismiss = { emojiOpen = false },
            onPick = { emoji ->
                text += emoji
                emojiOpen = false
            },
        )
    }
}

@Composable
private fun ImagePickerGrid(
    images: List<Uri>,
    onRemove: (Uri) -> Unit,
    onAdd: () -> Unit,
) {
    val rows = (images.size + 2) / 3
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (row in 0 until rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (col in 0 until 3) {
                    val index = row * 3 + col
                    if (index < images.size) {
                        val uri = images[index]
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(COUITheme.colorScheme.surfaceContainerHigh),
                        ) {
                            AsyncImage(
                                model = uri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(COUITheme.colorScheme.background.copy(alpha = 0.85f))
                                    .clickable { onRemove(uri) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(COUIIcons.Close, "移除", Modifier.size(14.dp))
                            }
                        }
                    } else if (index == images.size && images.size < 9) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(COUITheme.colorScheme.surfaceContainerHigh)
                                .clickable(onClick = onAdd),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(COUIIcons.Add, "添加图片", Modifier.size(26.dp))
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun EmojiDialog(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 20.dp,
        ) {
            Text(
                "表情",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 18.dp, top = 16.dp, bottom = 8.dp),
            )
            Column(
                modifier = Modifier
                    .height(320.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                EMOJIS.chunked(8).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { emoji ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onPick(emoji) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(emoji, fontSize = 22.sp)
                            }
                        }
                        repeat(8 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(text = "关闭", onClick = onDismiss)
            }
        }
    }
}
