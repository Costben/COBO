package com.cobo.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.cobo.app.App
import com.cobo.app.data.ProfileDetail
import com.cobo.app.data.ProfileGroup
import com.cobo.app.data.ProfileGroupSelection
import com.cobo.app.data.User
import io.github.suqi8.coui.kmp.basic.Checkbox
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextField
import io.github.suqi8.coui.kmp.basic.TextFieldMode
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Delete
import io.github.suqi8.coui.kmp.icon.extended.Rename
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.layout.DialogDefaults
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 与原型一致的曲线：起步快、收尾稳，长度变化时才不会显得「弹」。 */
private val GroupEase = CubicBezierEasing(0.22f, 0.9f, 0.24f, 1f)

/** 底部按钮栏的四种形态，切换时交叉淡入。 */
private enum class GroupBar { Select, Manage, Create, Rename }

/**
 * 「关注分组」对话框。
 *
 * 「管理分组」不是另一页，而是同一页里的形变：列表位置不动，行尾的勾选框原地缩掉、
 * 四个操作图标依次淡入；标题、副标题、底部那行和按钮栏都交叉淡入。新建和改名共用
 * 同一个输入区，展开收起都走高度动画，整块面板靠 animateContentSize 跟着长。
 *
 * 确认时把「现在勾的」和「原来在的」一起交给 `ajax/profile/setGroup` 做整组替换；
 * 新建出来的分组会自动勾上。
 */
@Composable
fun ProfileGroupDialog(
    selection: ProfileGroupSelection,
    onDismiss: () -> Unit,
    onCreate: suspend (String) -> ProfileGroup,
    onRename: suspend (ProfileGroup, String) -> ProfileGroup,
    onDelete: suspend (ProfileGroup) -> Unit,
    onReorder: suspend (List<String>) -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    var groups by remember(selection) { mutableStateOf(selection.groups) }
    var selected by remember(selection) {
        mutableStateOf<Set<String>>(groups.filter { it.joined }.map { it.gid }.toSet())
    }
    var managing by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<ProfileGroup?>(null) }
    var renameDraft by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<ProfileGroup?>(null) }
    var freshGid by remember { mutableStateOf<String?>(null) }
    var freshVisible by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var draggingGid by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var dragStartOrder by remember { mutableStateOf<List<ProfileGroup>?>(null) }
    val rowHeights = remember { mutableStateMapOf<String, Float>() }
    val listScroll = rememberScrollState()
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val inputFocus = remember { FocusRequester() }
    var show by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Set<String>?>(null) }
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { show = true }

    LaunchedEffect(managing) {
        if (!managing) {
            draggingGid = null
            dragOffsetY = 0f
            dragStartOrder = null
        }
    }

    val editing = creating || renaming != null

    /** 新行先以 0 高组合，下一帧再打开，[AnimatedVisibility] 才会真的播入场动画。 */
    LaunchedEffect(freshGid) {
        if (freshGid == null) return@LaunchedEffect
        freshVisible = false
        withFrameNanos { }
        freshVisible = true
    }

    /** 展开动画起帧之后再要焦点，太早会被这一帧的布局变化打断，键盘不会弹。 */
    LaunchedEffect(editing) {
        if (!editing) return@LaunchedEffect
        delay(90)
        runCatching { inputFocus.requestFocus() }
    }

    fun submitNew() {
        val name = draft.trim()
        if (name.isEmpty() || busy) return
        scope.launch {
            busy = true
            runCatching { onCreate(name) }
                .onSuccess { group ->
                    groups = groups + group
                    selected = selected + group.gid
                    freshGid = group.gid
                    draft = ""
                    creating = false
                }
                .onFailure { App.toast(it.message ?: "新建分组失败") }
            busy = false
        }
    }

    fun submitRename() {
        val target = renaming ?: return
        val name = renameDraft.trim()
        if (name.isEmpty() || busy) return
        scope.launch {
            busy = true
            runCatching { onRename(target, name) }
                .onSuccess { updated ->
                    groups = groups.map { if (it.gid == updated.gid) it.copy(name = updated.name) else it }
                    renaming = null
                    renameDraft = ""
                }
                .onFailure { App.toast(it.message ?: "重命名分组失败") }
            busy = false
        }
    }

    /** 拖放松手后把新顺序发给服务端；失败回滚，别让界面和服务端各说各话。 */
    fun finishDrag() {
        val before = dragStartOrder
        val after = groups
        draggingGid = null
        dragOffsetY = 0f
        dragStartOrder = null
        if (before == null || before.map { it.gid } == after.map { it.gid }) return
        scope.launch {
            busy = true
            runCatching { onReorder(after.map { it.gid }) }
                .onFailure {
                    groups = before
                    App.toast(it.message ?: "调整分组顺序失败")
                }
            busy = false
        }
    }

    fun cancelDrag() {
        dragStartOrder?.let { groups = it }
        draggingGid = null
        dragOffsetY = 0f
        dragStartOrder = null
    }

    val bar = when {
        creating -> GroupBar.Create
        renaming != null -> GroupBar.Rename
        managing -> GroupBar.Manage
        else -> GroupBar.Select
    }

    OverlayDialog(
        show = show,
        // 标题和副标题自己画，才能跟着状态交叉淡入；内置的那两个只能整段换字符串。
        title = null,
        summary = null,
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
        Column(
            Modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = tween(320, easing = GroupEase)),
        ) {
            GroupDialogHeader(managing)

            if (groups.isEmpty()) {
                GroupEmptyHint(managing)
            } else {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .scrollEndHaptic()
                        .verticalScroll(listScroll, enabled = draggingGid == null)
                        .padding(horizontal = 8.dp),
                ) {
                    groups.forEach { group ->
                        key(group.gid) {
                            val dragging = draggingGid == group.gid
                            val rowModifier = Modifier
                                .onGloballyPositioned { rowHeights[group.gid] = it.size.height.toFloat() }
                                .zIndex(if (dragging) 1f else 0f)
                                .graphicsLayer {
                                    if (dragging) {
                                        translationY = dragOffsetY
                                        scaleX = 1.02f
                                        scaleY = 1.02f
                                        alpha = 0.94f
                                    }
                                }
                                .then(
                                    if (dragging) {
                                        Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(COUITheme.colorScheme.primary.copy(alpha = 0.08f))
                                    } else {
                                        Modifier
                                    },
                                )
                                .then(
                                    if (managing) {
                                        Modifier.pointerInput(managing, group.gid) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    if (busy) return@detectDragGesturesAfterLongPress
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    draggingGid = group.gid
                                                    dragOffsetY = 0f
                                                    dragStartOrder = groups
                                                },
                                                onDrag = { change, dragAmount ->
                                                    if (draggingGid != group.gid) return@detectDragGesturesAfterLongPress
                                                    change.consume()
                                                    dragOffsetY += dragAmount.y
                                                    var cur = groups
                                                    var idx = cur.indexOfFirst { it.gid == group.gid }
                                                    while (idx in 0 until cur.lastIndex) {
                                                        val nextH = rowHeights[cur[idx + 1].gid] ?: break
                                                        if (dragOffsetY > nextH * 0.5f) {
                                                            cur = cur.toMutableList().apply { add(idx + 1, removeAt(idx)) }
                                                            groups = cur
                                                            dragOffsetY -= nextH
                                                            idx++
                                                        } else break
                                                    }
                                                    while (idx > 0) {
                                                        val prevH = rowHeights[cur[idx - 1].gid] ?: break
                                                        if (dragOffsetY < -prevH * 0.5f) {
                                                            cur = cur.toMutableList().apply { add(idx - 1, removeAt(idx)) }
                                                            groups = cur
                                                            dragOffsetY += prevH
                                                            idx--
                                                        } else break
                                                    }
                                                },
                                                onDragEnd = { finishDrag() },
                                                onDragCancel = { cancelDrag() },
                                            )
                                        }
                                    } else {
                                        Modifier
                                    },
                                )
                            val row: @Composable () -> Unit = {
                                GroupRow(
                                    group = group,
                                    checked = group.gid in selected,
                                    managing = managing,
                                    busy = busy,
                                    modifier = rowModifier,
                                    onToggle = { on ->
                                        selected = if (on) selected + group.gid else selected - group.gid
                                    },
                                    onRename = {
                                        creating = false
                                        renaming = group
                                        renameDraft = group.name
                                    },
                                    onDelete = { deleting = group },
                                )
                            }
                            if (group.gid == freshGid) {
                                AnimatedVisibility(
                                    visible = freshVisible,
                                    enter = expandVertically(tween(320, easing = GroupEase)) + fadeIn(tween(220)),
                                ) {
                                    row()
                                }
                            } else {
                                row()
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = editing,
                enter = expandVertically(tween(320, easing = GroupEase)) + fadeIn(tween(200)),
                exit = shrinkVertically(tween(260, easing = GroupEase)) + fadeOut(tween(140)),
            ) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 2.dp)) {
                    TextField(
                        value = if (creating) draft else renameDraft,
                        onValueChange = { value ->
                            if (creating) draft = value.take(20) else renameDraft = value.take(20)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(inputFocus),
                        label = "分组名称",
                        backgroundMode = TextFieldMode.Line,
                        singleLine = true,
                        showClearButton = true,
                    )
                }
            }

            AnimatedVisibility(
                visible = !editing,
                enter = expandVertically(tween(300, easing = GroupEase)) + fadeIn(tween(180, delayMillis = 60)),
                exit = shrinkVertically(tween(220, easing = GroupEase)) + fadeOut(tween(120)),
            ) {
                AnimatedContent(
                    targetState = managing,
                    transitionSpec = {
                        (fadeIn(tween(200)) +
                            slideInVertically(tween(300, easing = GroupEase)) { it / 3 }) togetherWith
                            (fadeOut(tween(150)) +
                                slideOutVertically(tween(300, easing = GroupEase)) { -it / 3 })
                    },
                    label = "groupBottomRow",
                ) { mgr ->
                    Text(
                        text = if (mgr) "＋ 新建分组" else "管理分组",
                        fontSize = 15.sp,
                        color = COUITheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !busy) {
                                if (mgr) {
                                    renaming = null
                                    draft = ""
                                    creating = true
                                } else {
                                    managing = true
                                }
                            }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            }

            AnimatedContent(
                targetState = bar,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(150)) },
                label = "groupBar",
            ) { state ->
                when (state) {
                    GroupBar.Select -> DialogButtonBar(
                        negative = DialogButtonBarAction(text = "取消", onClick = { show = false }),
                        positive = DialogButtonBarAction(
                            text = "保存",
                            enabled = !busy,
                            onClick = {
                                result = selected
                                show = false
                            },
                        ),
                    )
                    GroupBar.Manage -> DialogButtonBar(
                        negative = null,
                        positive = DialogButtonBarAction(
                            text = "完成",
                            recommended = true,
                            onClick = { managing = false },
                        ),
                    )
                    GroupBar.Create -> DialogButtonBar(
                        negative = DialogButtonBarAction(
                            text = "取消",
                            onClick = {
                                creating = false
                                draft = ""
                            },
                        ),
                        positive = DialogButtonBarAction(
                            text = "新建",
                            enabled = draft.isNotBlank() && !busy,
                            onClick = ::submitNew,
                        ),
                    )
                    GroupBar.Rename -> DialogButtonBar(
                        negative = DialogButtonBarAction(
                            text = "取消",
                            onClick = {
                                renaming = null
                                renameDraft = ""
                            },
                        ),
                        positive = DialogButtonBarAction(
                            text = "保存",
                            enabled = renameDraft.isNotBlank() && !busy,
                            onClick = ::submitRename,
                        ),
                    )
                }
            }
        }
    }

    deleting?.let { target ->
        ConfirmDialog(
            title = "删除分组",
            message = "确定删除「${target.name}」吗？组里的关注会回到未分组。",
            confirmText = "删除",
            onDismiss = { deleting = null },
            onConfirm = {
                deleting = null
                scope.launch {
                    busy = true
                    runCatching { onDelete(target) }
                        .onSuccess {
                            groups = groups.filterNot { it.gid == target.gid }
                            selected = selected - target.gid
                        }
                        .onFailure { App.toast(it.message ?: "删除分组失败") }
                    busy = false
                }
            },
        )
    }
}

/** 标题与副标题：字号、行高、颜色对齐 COUI 内置标题，只是换成可以交叉淡入的实现。 */
@Composable
private fun GroupDialogHeader(managing: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
    ) {
        AnimatedContent(
            targetState = managing,
            transitionSpec = {
                (fadeIn(tween(200)) +
                    slideInVertically(tween(320, easing = GroupEase)) { -it / 8 }) togetherWith
                    (fadeOut(tween(150)) +
                        slideOutVertically(tween(320, easing = GroupEase)) { it / 8 })
            },
            label = "groupTitle",
        ) { mgr ->
            Text(
                text = if (mgr) "管理分组" else "关注分组",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                fontSize = COUITheme.textStyles.title4.fontSize,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 1.37.em,
                textAlign = TextAlign.Center,
                color = DialogDefaults.titleColor(),
            )
        }
        AnimatedContent(
            targetState = managing,
            transitionSpec = {
                (fadeIn(tween(200, delayMillis = 50)) +
                    slideInVertically(tween(320, easing = GroupEase)) { it / 8 }) togetherWith
                    (fadeOut(tween(150)) +
                        slideOutVertically(tween(320, easing = GroupEase)) { -it / 8 })
            },
            label = "groupSummary",
        ) { mgr ->
            Text(
                text = if (mgr) {
                    "长按上下拖动调整顺序，改名和删除只影响分组本身"
                } else {
                    "勾选要加入的分组，取消勾选即移出"
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 2.dp, bottom = 12.dp),
                fontSize = COUITheme.textStyles.footnote1.fontSize,
                lineHeight = 1.37.em,
                textAlign = TextAlign.Center,
                color = DialogDefaults.summaryColor(),
            )
        }
    }
}

@Composable
private fun GroupEmptyHint(managing: Boolean) {
    AnimatedContent(
        targetState = managing,
        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
        label = "groupEmpty",
    ) { mgr ->
        Text(
            text = if (mgr) "还没有自建关注分组" else "这个账号还没有自建关注分组，可以在「管理分组」里新建",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            fontSize = 14.sp,
            color = COUITheme.colorScheme.onSurfaceSecondary,
        )
    }
}

/**
 * 一行分组。勾选态和管理态共用同一行：行尾槽位宽度从 24dp 过渡到 68dp，
 * 勾选框原地缩掉，重命名与删除两个图标依次淡入，所以列表位置不会跳。
 */
@Composable
private fun GroupRow(
    group: ProfileGroup,
    checked: Boolean,
    managing: Boolean,
    busy: Boolean,
    modifier: Modifier = Modifier,
    onToggle: (Boolean) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val slotWidth by animateDpAsState(
        targetValue = if (managing) 68.dp else 24.dp,
        animationSpec = tween(durationMillis = 340, easing = GroupEase),
        label = "groupSlotWidth",
    )
    val checkAlpha by animateFloatAsState(
        targetValue = if (managing) 0f else 1f,
        animationSpec = tween(160, easing = GroupEase),
        label = "groupCheckAlpha",
    )
    val checkScale by animateFloatAsState(
        targetValue = if (managing) 0.72f else 1f,
        animationSpec = tween(300, easing = GroupEase),
        label = "groupCheckScale",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (managing) {
                    Modifier
                } else {
                    Modifier.toggleable(
                        value = checked,
                        interactionSource = null,
                        indication = null,
                        role = Role.Checkbox,
                        onValueChange = onToggle,
                    )
                },
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(group.name, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (group.memberCount > 0) {
                Text(
                    "${group.memberCount} 人",
                    fontSize = 12.sp,
                    color = COUITheme.colorScheme.onSurfaceSecondary,
                )
            }
        }
        Box(
            Modifier
                .width(slotWidth)
                .height(34.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(
                Modifier.graphicsLayer {
                    alpha = checkAlpha
                    scaleX = checkScale
                    scaleY = checkScale
                },
            ) {
                Checkbox(
                    state = if (checked) ToggleableState.On else ToggleableState.Off,
                    onClick = null,
                )
            }
            Row {
                GroupActionButton(
                    icon = COUIIcons.Rename,
                    label = "重命名",
                    shown = managing,
                    enabled = managing && !busy,
                    order = 0,
                    onClick = onRename,
                )
                GroupActionButton(
                    icon = COUIIcons.Delete,
                    label = "删除",
                    shown = managing,
                    enabled = managing && !busy,
                    order = 1,
                    onClick = onDelete,
                )
            }
        }
    }
}

/**
 * 管理页每行右侧那颗小图标按钮：默认 48dp 的点击框在四连排里太占地方，收到 34dp；
 * 进出管理态时按 [order] 级联淡入，动画结束（alpha 归零）后整颗按钮从组合里移除。
 */
@Composable
private fun GroupActionButton(
    icon: ImageVector,
    label: String,
    shown: Boolean,
    enabled: Boolean,
    order: Int,
    onClick: () -> Unit,
) {
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(
            durationMillis = 220,
            delayMillis = if (shown) order * 22 else 0,
            easing = GroupEase,
        ),
        label = "groupIcon$order",
    )
    if (!shown && progress <= 0.001f) return
    IconButton(
        onClick = onClick,
        enabled = enabled,
        minWidth = 34.dp,
        minHeight = 34.dp,
        modifier = Modifier.graphicsLayer {
            alpha = progress
            translationX = (1f - progress) * 6.dp.toPx()
        },
    ) {
        Icon(icon, label, Modifier.size(18.dp))
    }
}

/** `ajax/profile/detail` 的白名单字段 + 本地已有的基础信息，拼成一张只读表。 */
@Composable
fun ProfileInfoDialog(
    user: User?,
    detail: ProfileDetail?,
    loading: Boolean,
    onDismiss: () -> Unit,
) {
    var show by remember { mutableStateOf(false) }
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { show = true }
    OverlayDialog(
        show = show,
        title = "基本资料",
        outsideMargin = DpSize(16.dp, 8.dp),
        onDismissRequest = { show = false },
        onDismissFinished = {
            if (!settled) {
                settled = true
                onDismiss()
            }
        },
    ) {
        Column {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .scrollEndHaptic()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                InfoRow("昵称", user?.name.orEmpty())
                InfoRow("微博 UID", user?.id.orEmpty())
                if (user?.verified == true) InfoRow("认证", "已认证")
                InfoRow("微博", fmtCount(user?.statuses ?: 0))
                InfoRow("关注", fmtCount(user?.friends ?: 0))
                InfoRow("粉丝", fmtCount(user?.followers ?: 0))
                if (detail != null) {
                    InfoRow("性别", detail.gender)
                    InfoRow("生日", detail.birthday)
                    InfoRow("所在地", detail.location)
                    InfoRow("IP 属地", detail.ipLocation)
                    InfoRow("注册时间", detail.createdAt)
                    InfoRow("教育", detail.education)
                    InfoRow("公司", detail.company)
                    InfoRow("阳光信用", detail.credit)
                    InfoRow("标签", detail.labels.joinToString("、"))
                    InfoRow("简介", detail.description.ifBlank { user?.description.orEmpty() })
                } else if (loading) {
                    Text(
                        "正在读取…",
                        fontSize = 14.sp,
                        color = COUITheme.colorScheme.onSurfaceSecondary,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                } else {
                    InfoRow("简介", user?.description.orEmpty())
                }
            }
            DialogButtonBar(
                negative = null,
                positive = DialogButtonBarAction(
                    text = "完成",
                    recommended = true,
                    onClick = { show = false },
                ),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** 个人主页二维码：内容是 `https://weibo.com/u/<uid>`，官方客户端扫出来就是同一个人。 */
@Composable
fun ProfileQrDialog(user: User?, url: String, onDismiss: () -> Unit) {
    var show by remember { mutableStateOf(false) }
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { show = true }
    OverlayDialog(
        show = show,
        title = "二维码",
        summary = user?.name?.takeIf { it.isNotBlank() },
        outsideMargin = DpSize(16.dp, 8.dp),
        onDismissRequest = { show = false },
        onDismissFinished = {
            if (!settled) {
                settled = true
                onDismiss()
            }
        },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            QrCodeImage(url, 190.dp, Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(8.dp))
            Text(
                url,
                fontSize = 12.sp,
                color = COUITheme.colorScheme.onSurfaceSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            DialogButtonBar(
                negative = null,
                positive = DialogButtonBarAction(
                    text = "完成",
                    recommended = true,
                    onClick = { show = false },
                ),
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    if (value.isBlank()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Text(
            label,
            fontSize = 13.sp,
            color = COUITheme.colorScheme.onSurfaceSecondary,
            modifier = Modifier.padding(end = 12.dp),
        )
        Text(value, fontSize = 14.sp, modifier = Modifier.weight(1f))
    }
}
