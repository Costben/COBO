package com.cobo.app.screen

import com.cobo.app.generated.resources.Res
import com.cobo.app.generated.resources.ic_live_photo
import com.cobo.app.platform.PickedMediaRegistry
import com.cobo.app.platform.PlatformBackHandler
import com.cobo.app.platform.PlatformUri
import com.cobo.app.platform.ioDispatcher
import com.cobo.app.platform.rememberPhotoPicker
import org.jetbrains.compose.resources.painterResource as kmpPainterResource

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.cobo.app.App
import com.cobo.app.data.MotionPhotoParser
import com.cobo.app.data.Pic
import com.cobo.app.data.Repo
import com.cobo.app.data.StatusMemory
import com.cobo.app.data.WeiboVisibility
import com.cobo.app.data.applyComposerReplacement
import com.cobo.app.data.detectComposerTrigger
import com.cobo.app.data.editableStatusText
import com.cobo.app.data.toWeiboVisibility
import com.cobo.app.ui.CoboIcons
import com.cobo.app.ui.NavState
import com.cobo.app.ui.PinnedBarDensity
import com.cobo.app.ui.components.ActionIcon
import com.cobo.app.ui.components.BackIconButton
import com.cobo.app.ui.components.ComposerSuggestPanel
import com.cobo.app.ui.components.LoadingState
import com.cobo.app.ui.components.VisibilityPickerDialog
import com.cobo.app.ui.components.VisibilityPill
import com.cobo.app.ui.components.WeiboEmojiPanel
import com.cobo.app.ui.pageContainerColor
import io.github.suqi8.coui.kmp.basic.BindScrollToTop
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
import io.github.suqi8.coui.kmp.overlay.OverlayBottomSheet
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface ComposerMediaItem {
    val key: String

    data class Existing(val pic: Pic, override val key: String) : ComposerMediaItem
    data class Local(val uri: PlatformUri) : ComposerMediaItem {
        override val key: String get() = "local:$uri"
    }
}

private fun List<Pic>.toExistingComposerMedia(): List<ComposerMediaItem.Existing> =
    filter { !it.isVideo }.mapIndexed { index, pic ->
        val token = pic.resolvedPicId().ifBlank { pic.thumb.ifBlank { pic.large } }
        ComposerMediaItem.Existing(pic = pic, key = "existing:$index:$token")
    }

@Composable
fun PublishScreen(
    nav: NavState,
    draft: String = "",
    editStatusId: String? = null,
    editStatusMid: String = "",
    initialMediaUris: List<String> = emptyList(),
) {
    val isEditMode = !editStatusId.isNullOrBlank()
    val initialStatus = remember(editStatusId, editStatusMid) {
        if (editStatusId.isNullOrBlank()) {
            null
        } else {
            App.repo.editedStatus(editStatusId, editStatusMid)
                ?: StatusMemory.find(editStatusId)
                ?: StatusMemory.find(editStatusMid)
        }
    }
    val initialText = remember(draft, editStatusId, initialStatus) {
        if (isEditMode) editableStatusText(initialStatus?.textHtml) else draft
    }

    val scope = rememberCoroutineScope()
    val pageScrollState = rememberScrollState()
    BindScrollToTop(pageScrollState)

    var editingStatus by remember(editStatusId, editStatusMid) { mutableStateOf(initialStatus) }
    var loadingEditTarget by remember(editStatusId) { mutableStateOf(isEditMode && initialStatus == null) }
    var userEditedText by remember(editStatusId) { mutableStateOf(false) }
    var userEditedMedia by remember(editStatusId) { mutableStateOf(false) }
    var userEditedVisibility by remember(editStatusId) { mutableStateOf(false) }

    var field by rememberSaveable(editStatusId, draft, initialMediaUris, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialText, TextRange(initialText.length)))
    }
    var mediaItems by remember(editStatusId, initialMediaUris) {
        mutableStateOf<List<ComposerMediaItem>>(
            if (isEditMode) {
                initialStatus?.pics.orEmpty().toExistingComposerMedia()
            } else {
                initialMediaUris.take(9).map { ComposerMediaItem.Local(PlatformUri(it)) }
            },
        )
    }
    var liveDetected by remember(editStatusId) { mutableStateOf<Map<PlatformUri, Boolean>>(emptyMap()) }
    var liveEnabled by remember(editStatusId) {
        mutableStateOf(
            if (isEditMode) {
                initialStatus?.pics.orEmpty()
                    .toExistingComposerMedia()
                    .filter { it.pic.isLive }
                    .associate { it.key to true }
            } else {
                emptyMap()
            },
        )
    }
    var publishing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var emojiOpen by remember { mutableStateOf(false) }
    var visibility by remember(editStatusId) {
        mutableStateOf(
            if (isEditMode) initialStatus?.visibility.toWeiboVisibility() else WeiboVisibility.Public,
        )
    }
    var visibilityDialog by remember { mutableStateOf(false) }
    val supportsLivePhoto = remember(App.repo.session) { App.repo.supportsLivePhotoPublish() }
    val supportsEdit = remember(App.repo.session) { App.repo.supportsEditStatus() }
    val maxMediaCount = if (isEditMode && editingStatus?.retweeted != null) 1 else 9

    LaunchedEffect(editStatusId, editStatusMid) {
        if (!isEditMode || editStatusId.isNullOrBlank()) return@LaunchedEffect
        try {
            val fresh = App.repo.detail(editStatusId, force = true)
            if (fresh != null) {
                editingStatus = fresh
                if (!userEditedText) {
                    val freshText = editableStatusText(fresh.textHtml)
                    field = TextFieldValue(freshText, TextRange(freshText.length))
                }
                if (!userEditedMedia) {
                    val existing = fresh.pics.toExistingComposerMedia()
                    mediaItems = existing
                    liveEnabled = existing
                        .filter { it.pic.isLive }
                        .associate { it.key to true } + liveEnabled
                }
                if (!userEditedVisibility) {
                    visibility = fresh.visibility.toWeiboVisibility()
                }
            } else if (editingStatus == null) {
                error = "未获取到微博详情"
            }
        } catch (t: Throwable) {
            if (editingStatus == null) {
                error = t.message ?: "加载微博详情失败"
            }
        } finally {
            loadingEditTarget = false
        }
    }

    LaunchedEffect(mediaItems, supportsLivePhoto) {
        val currentLocalUris = mediaItems.filterIsInstance<ComposerMediaItem.Local>().map { it.uri }.toSet()
        val currentKeys = mediaItems.map { it.key }.toSet()
        if (liveDetected.keys.any { it !in currentLocalUris }) {
            liveDetected = liveDetected.filterKeys { it in currentLocalUris }
        }
        if (liveEnabled.keys.any { it !in currentKeys }) {
            liveEnabled = liveEnabled.filterKeys { it in currentKeys }
        }
        val pending = currentLocalUris.filter { it !in liveDetected }
        if (pending.isNotEmpty()) {
            val inspected = withContext(ioDispatcher) {
                pending.associateWith { uri ->
                    MotionPhotoParser.inspect(App.context, uri).hasMotionPhoto
                }
            }
            liveDetected = liveDetected + inspected
            val newEnabled = inspected
                .filterValues { it }
                .entries
                .associate { (uri, _) -> ComposerMediaItem.Local(uri).key to supportsLivePhoto }
            if (newEnabled.isNotEmpty()) {
                liveEnabled = newEnabled + liveEnabled
            }
        }
    }

    val activeTrigger = remember(field.text, field.selection) {
        detectComposerTrigger(field.text, field.selection.end)
    }

    fun insertAtSelection(snippet: String, cursorBack: Int = 0) {
        val current = field.text
        val start = field.selection.min.coerceIn(0, current.length)
        val end = field.selection.max.coerceIn(start, current.length)
        val next = buildString {
            append(current, 0, start)
            append(snippet)
            append(current, end, current.length)
        }
        val nextPos = (start + snippet.length - cursorBack).coerceIn(0, next.length)
        userEditedText = true
        field = TextFieldValue(next, TextRange(nextPos))
    }

    val picker = rememberPhotoPicker(maxSelection = 9) { picked ->
        val uris = picked.map { PickedMediaRegistry.register(it) }
        if (uris.isNotEmpty()) {
            userEditedMedia = true
            val existingLocal = mediaItems.filterIsInstance<ComposerMediaItem.Local>().map { it.uri }.toSet()
            val additions = uris.distinct().filter { it !in existingLocal }.map { ComposerMediaItem.Local(it) }
            mediaItems = (mediaItems + additions).take(maxMediaCount)
        }
    }

    fun leave() {
        if (!isEditMode && field.text.isNotBlank()) App.store.addDraft(field.text)
        nav.pop()
    }

    PlatformBackHandler {
        if (!publishing) leave()
    }

    val canSubmit = !publishing &&
        !loadingEditTarget &&
        (!isEditMode || (supportsEdit && editingStatus != null)) &&
        (field.text.isNotBlank() || mediaItems.isNotEmpty() || (isEditMode && editingStatus?.retweeted != null))

    Scaffold(
        containerColor = pageContainerColor(),
        topBar = {
            PinnedBarDensity {
                SmallTopAppBar(
                    color = pageContainerColor(),
                    title = if (isEditMode) "编辑微博" else "发微博",
                    navigationIcon = { BackIconButton { leave() } },
                    actions = {
                        TextButton(
                            text = when {
                                isEditMode && publishing -> "保存中…"
                                isEditMode -> "保存"
                                publishing -> "发布中…"
                                else -> "发布"
                            },
                            modifier = Modifier.testTag("publish_send"),
                            enabled = canSubmit,
                            onClick = {
                                scope.launch {
                                    publishing = true
                                    error = null
                                    try {
                                        val localItems = mediaItems.filterIsInstance<ComposerMediaItem.Local>()
                                        val uploadedByKey = HashMap<String, String>(localItems.size)
                                        localItems.forEachIndexed { index, item ->
                                            val isMotionPhoto = liveDetected[item.uri] ?: withContext(ioDispatcher) {
                                                MotionPhotoParser.inspect(App.context, item.uri).hasMotionPhoto
                                            }
                                            val sendAsLive = isMotionPhoto &&
                                                supportsLivePhoto &&
                                                (liveEnabled[item.key] ?: true)
                                            progress = if (sendAsLive) {
                                                "上传实况图 ${index + 1}/${localItems.size}"
                                            } else {
                                                "上传图片 ${index + 1}/${localItems.size}"
                                            }
                                            uploadedByKey[item.key] = App.repo.uploadImage(item.uri, asLivePhoto = sendAsLive)
                                        }
                                        if (isEditMode) {
                                            val target = editingStatus ?: throw IllegalStateException("未获取到微博详情")
                                            val entries = mediaItems.mapNotNull { item ->
                                                when (item) {
                                                    is ComposerMediaItem.Existing -> Repo.EditableStatusMedia.Existing(
                                                        pic = item.pic,
                                                        keepLive = item.pic.isLive && (liveEnabled[item.key] ?: true),
                                                    )
                                                    is ComposerMediaItem.Local -> uploadedByKey[item.key]?.let {
                                                        Repo.EditableStatusMedia.Uploaded(it)
                                                    }
                                                }
                                            }
                                            progress = "正在保存…"
                                            App.repo.editStatus(
                                                status = target,
                                                text = field.text.trim(),
                                                mediaEntries = entries,
                                                visible = visibility.code,
                                            )
                                            App.toast("已保存修改")
                                            nav.pop()
                                        } else {
                                            val ids = mediaItems.mapNotNull { uploadedByKey[it.key] }
                                            progress = "正在发布…"
                                            App.repo.publish(field.text.trim(), ids, visibility.code)
                                            App.toast("发布成功")
                                            App.notifyPublished()
                                            nav.pop()
                                        }
                                    } catch (t: Throwable) {
                                        val msg = t.message ?: if (isEditMode) "保存失败" else "发布失败"
                                        error = msg
                                        App.toast(msg)
                                    } finally {
                                        progress = ""
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
                .scrollEndHaptic()
                .verticalScroll(pageScrollState)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            if (loadingEditTarget) {
                LoadingState()
            }
            if (isEditMode && !supportsEdit) {
                Text(
                    "网页登录暂不支持编辑微博，请使用客户端登录后再试",
                    fontSize = 12.sp,
                    color = COUITheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            TextField(
                value = field,
                onValueChange = {
                    userEditedText = true
                    field = it
                },
                modifier = Modifier.fillMaxWidth().testTag("publish_input"),
                label = if (isEditMode) "修改微博内容…" else "分享新鲜事…",
                backgroundMode = TextFieldMode.Line,
                minLines = 7,
                maxLines = 14,
            )
            if (activeTrigger != null) {
                Spacer(Modifier.height(8.dp))
                ComposerSuggestPanel(
                    trigger = activeTrigger,
                    onPickMention = { user ->
                        val (next, nextCursor) = applyComposerReplacement(field.text, activeTrigger, user.name)
                        userEditedText = true
                        field = TextFieldValue(next, TextRange(nextCursor))
                    },
                    onPickTopic = { topic ->
                        val (next, nextCursor) = applyComposerReplacement(field.text, activeTrigger, topic.title)
                        userEditedText = true
                        field = TextFieldValue(next, TextRange(nextCursor))
                    },
                )
            }
            val quoted = editingStatus?.retweeted
            if (isEditMode && quoted != null) {
                Spacer(Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(COUITheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    val quotedAuthor = quoted.user?.name.orEmpty()
                    val quotedBody = editableStatusText(quoted.textHtml).ifBlank { "原微博" }
                    Text(
                        text = if (quotedAuthor.isNotBlank()) "原微博 @$quotedAuthor" else "原微博",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = COUITheme.colorScheme.onSurfaceSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = quotedBody,
                        fontSize = 13.sp,
                        color = COUITheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            if (mediaItems.isNotEmpty()) {
                ImagePickerGrid(
                    items = mediaItems,
                    maxCount = maxMediaCount,
                    liveDetected = liveDetected,
                    liveEnabled = liveEnabled,
                    supportsLivePhoto = supportsLivePhoto,
                    onToggleLive = { item ->
                        if (supportsLivePhoto) {
                            userEditedMedia = true
                            val current = liveEnabled[item.key] ?: true
                            liveEnabled = liveEnabled + (item.key to !current)
                        }
                    },
                    onRemove = { item ->
                        userEditedMedia = true
                        mediaItems = mediaItems.filterNot { it.key == item.key }
                    },
                    onAdd = {
                        picker()
                    },
                )
                val hasLocalMotion = mediaItems.any { it is ComposerMediaItem.Local && liveDetected[it.uri] == true }
                if (!supportsLivePhoto && hasLocalMotion) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "网页登录暂不支持发布实况图，已自动按静态图上传",
                        fontSize = 12.sp,
                        color = COUITheme.colorScheme.onSurfaceSecondary,
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(COUITheme.colorScheme.surfaceContainerHigh)
                            .clickable {
                                picker()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(COUIIcons.Image, "添加图片", Modifier.size(28.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (maxMediaCount == 1) "最多 1 张图片" else "最多 9 张图片，支持实况图",
                        fontSize = 12.sp,
                        color = COUITheme.colorScheme.onSurfaceSecondary,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ActionIcon(COUIIcons.Image, "", onClick = {
                    picker()
                })
                ActionIcon(CoboIcons.Emoji, "", onClick = { emojiOpen = true })
                ActionIcon(CoboIcons.At, "", onClick = { insertAtSelection("@") })
                ActionIcon(CoboIcons.Topic, "", onClick = { insertAtSelection("##", cursorBack = 1) })
                Spacer(Modifier.weight(1f))
                VisibilityPill(visibility = visibility) {
                    visibilityDialog = true
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "${field.text.length}",
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

    OverlayBottomSheet(
        show = emojiOpen,
        title = "表情",
        onDismissRequest = { emojiOpen = false },
    ) {
        WeiboEmojiPanel(
            modifier = Modifier.height(260.dp),
            onPick = { emoji -> insertAtSelection(emoji.phrase) },
        )
    }

    if (visibilityDialog) {
        VisibilityPickerDialog(
            selected = visibility,
            onPick = {
                userEditedVisibility = true
                visibility = it
                visibilityDialog = false
            },
            onDismiss = { visibilityDialog = false },
        )
    }
}

@Composable
private fun ImagePickerGrid(
    items: List<ComposerMediaItem>,
    maxCount: Int,
    liveDetected: Map<PlatformUri, Boolean>,
    liveEnabled: Map<String, Boolean>,
    supportsLivePhoto: Boolean,
    onToggleLive: (ComposerMediaItem) -> Unit,
    onRemove: (ComposerMediaItem) -> Unit,
    onAdd: () -> Unit,
) {
    val slotCount = if (items.size < maxCount) items.size + 1 else items.size
    val rows = (slotCount + 2) / 3
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (row in 0 until rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (col in 0 until 3) {
                    val index = row * 3 + col
                    if (index < items.size) {
                        val item = items[index]
                        val isLivePhoto = when (item) {
                            is ComposerMediaItem.Existing -> item.pic.isLive
                            is ComposerMediaItem.Local -> liveDetected[item.uri] == true
                        }
                        val isLiveActive = isLivePhoto && supportsLivePhoto && (liveEnabled[item.key] ?: true)
                        val previewModel: Any = when (item) {
                            is ComposerMediaItem.Existing -> item.pic.thumb.ifBlank { item.pic.large }
                            is ComposerMediaItem.Local -> item.uri
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(COUITheme.colorScheme.surfaceContainerHigh),
                        ) {
                            AsyncImage(
                                model = previewModel,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            if (isLivePhoto) {
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(5.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.Black.copy(alpha = if (isLiveActive) 0.62f else 0.42f))
                                        .then(
                                            if (supportsLivePhoto) {
                                                Modifier.clickable { onToggleLive(item) }
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                        .testTag("publish_live_toggle_$index"),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        painter = kmpPainterResource(Res.drawable.ic_live_photo),
                                        contentDescription = if (isLiveActive) "实况已开启" else "实况已关闭",
                                        modifier = Modifier.size(12.dp),
                                        tint = if (isLiveActive) Color.White else Color.White.copy(alpha = 0.62f),
                                    )
                                    Spacer(Modifier.width(3.dp))
                                    Text(
                                        text = if (isLiveActive) "实况" else "静图",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isLiveActive) Color.White else Color.White.copy(alpha = 0.68f),
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(COUITheme.colorScheme.background.copy(alpha = 0.85f))
                                    .clickable { onRemove(item) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(COUIIcons.Close, "移除", Modifier.size(14.dp))
                            }
                        }
                    } else if (index == items.size && items.size < maxCount) {
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
