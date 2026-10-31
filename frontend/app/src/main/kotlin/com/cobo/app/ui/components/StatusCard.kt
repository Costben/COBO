package com.cobo.app.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.cobo.app.App
import com.cobo.app.data.Pic
import com.cobo.app.data.FollowTags
import com.cobo.app.data.Membership
import com.cobo.app.data.Status
import com.cobo.app.data.User
import com.cobo.app.data.VideoInfo
import com.cobo.app.data.VoteInfo
import com.cobo.app.data.formatWeiboTime
import com.cobo.app.ui.CoboIcons
import com.cobo.app.ui.ExpandOrigin
import com.cobo.app.ui.LocalLinkHandler
import com.cobo.app.ui.expandOrigin
import com.cobo.app.ui.imageOrigin
import com.cobo.app.ui.components.LocalImageSaver
import com.cobo.app.ui.rememberExpandOrigin
import io.github.suqi8.coui.kmp.basic.ButtonDefaults
import io.github.suqi8.coui.kmp.basic.ButtonSize
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Checkbox
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.RadioButton
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor
import io.github.suqi8.coui.kmp.basic.rememberPreciseClickState
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Play
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.launch

@Composable
fun StatusCard(
    status: Status,
    onOpen: () -> Unit,
    onUser: (User) -> Unit,
    onImage: (List<String>, Int) -> Unit,
    onVideo: (VideoInfo) -> Unit,
    onLike: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    showActions: Boolean = true,
    /** 只读流（本地关注、纯本地模式）：计数照常显示，但点赞 / 评论 / 转发都不响应。 */
    actionsEnabled: Boolean = !App.repo.guest,
    onRepost: () -> Unit = {},
    onComment: () -> Unit = {},
    /** Opens the quoted post when it is tapped; the frame grows from and returns into that block. */
    onOpenQuoted: ((Status, ExpandOrigin) -> Unit)? = null,
) {
    val scheme = COUITheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        onClick = onOpen,
    ) {
        StatusHeader(status, onUser, compact)
        if (status.textHtml.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            WeiboText(
                html = status.textHtml,
                fontSize = if (compact) 14.sp else 15.sp,
                lineHeight = if (compact) 20.sp else 22.sp,
            )
        }
        if (status.isVote) {
            Spacer(Modifier.height(8.dp))
            StatusVoteCard(status = status)
        }
        StatusMedia(status, compact, onImage, onVideo)
        if (status.retweeted != null) {
            Spacer(Modifier.height(8.dp))
            RetweetBlock(
                status = status.retweeted,
                compact = compact,
                onOpen = onOpen,
                onOpenQuoted = onOpenQuoted,
                onUser = onUser,
                onImage = onImage,
                onVideo = onVideo,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val meta = buildString {
                append(formatWeiboTime(status.createdAt))
                if (status.source.isNotBlank()) {
                    if (isNotEmpty()) append("  ")
                    append(status.source)
                }
                if (status.region.isNotBlank()) {
                    if (isNotEmpty()) append("  ")
                    append(status.region)
                }
            }
            Text(
                text = meta,
                fontSize = 11.sp,
                color = scheme.onSurfaceSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (showActions) {
                Spacer(Modifier.width(8.dp))
                ActionIcon(
                    icon = CoboIcons.Repost,
                    text = fmtCount(status.reposts),
                    onClick = onRepost,
                    enabled = actionsEnabled,
                )
                ActionIcon(
                    icon = CoboIcons.Comment,
                    text = fmtCount(status.comments),
                    onClick = onComment,
                    enabled = actionsEnabled,
                )
                ActionIcon(
                    icon = if (status.liked) CoboIcons.HeartFill else CoboIcons.Heart,
                    text = fmtCount(status.attitudes),
                    onClick = onLike,
                    tint = if (status.liked) scheme.primary else scheme.onSurfaceSecondary,
                    enabled = actionsEnabled,
                )
            }
        }
    }
}

@Composable
fun StatusHeader(status: Status, onUser: (User) -> Unit, compact: Boolean) {
    val scheme = COUITheme.colorScheme
    val user = status.user
    Row(verticalAlignment = Alignment.CenterVertically) {
        Avatar(
            url = user?.avatar,
            size = if (compact) 38.dp else 42.dp,
            modifier = Modifier.clickable { user?.let(onUser) },
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = user?.name.orEmpty(),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // 昵称太长时先压缩昵称，别把后面的徽标和认证勾挤出去。
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clickable { user?.let(onUser) },
                )
                val membership = user?.membership ?: Membership.None
                if (membership != Membership.None) {
                    Spacer(Modifier.width(4.dp))
                    MemberBadge(membership, compact = true)
                }
                val tag = FollowTags[user?.id]
                if (tag != null) {
                    Spacer(Modifier.width(4.dp))
                    FollowTagBadge(tag, compact = true)
                }
                if (user?.verified == true) {
                    Spacer(Modifier.width(4.dp))
                    VerifiedBadge()
                }
            }
            Text(
                text = buildString {
                    append(formatWeiboTime(status.createdAt))
                    if (status.source.isNotBlank()) {
                        append(" 来自 ")
                        append(status.source)
                    }
                },
                fontSize = 11.sp,
                color = scheme.onSurfaceSecondary,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun StatusMedia(
    status: Status,
    compact: Boolean,
    onImage: (List<String>, Int) -> Unit,
    onVideo: (VideoInfo) -> Unit,
) {
    val video = status.video
    if (video != null && video.url.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        VideoCard(video, compact, onVideo)
        return
    }
    if (status.pics.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        ImageGrid(status.pics, compact, onImage)
    }
}

@Composable
fun ImageGrid(
    pics: List<Pic>,
    compact: Boolean,
    onImage: (List<String>, Int) -> Unit,
) {
    val urls = pics.map { it.large }
    // 长按任意一张缩略图就能弹出「保存原图 / 一键保存原图」，菜单由根节点统一承载。
    val saver = LocalImageSaver.current
    if (pics.size == 1) {
        val pic = pics.first()
        val ratio = if (pic.width > 0 && pic.height > 0) {
            (pic.width.toFloat() / pic.height.toFloat()).coerceIn(0.62f, 1.9f)
        } else {
            1.45f
        }
        // 真实的宽高比（不是裁切后的格子比例）交给看图页，它才能把第一帧摆得和这张缩略图一模一样。
        val aspect = if (pic.width > 0 && pic.height > 0) pic.width.toFloat() / pic.height else null
        val precise = rememberPreciseClickState()
        AsyncImage(
            model = pic.large,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth(if (compact) 0.72f else 0.86f)
                .aspectRatio(ratio)
                .imageOrigin(urls[0], 10.dp, aspect = aspect, model = pic.large)
                .clip(RoundedCornerShape(10.dp))
                .background(COUITheme.colorScheme.surfaceContainerHigh)
                .preciseClickAnchor(precise)
                .combinedClickable(
                    onClick = { onImage(urls, 0) },
                    onLongClick = { saver?.show(urls, 0, precise) },
                ),
        )
        return
    }

    val columns = if (pics.size <= 4) 2 else 3
    val rows = (pics.size + columns - 1) / columns
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (row in 0 until rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (col in 0 until columns) {
                    val index = row * columns + col
                    if (index < pics.size) {
                        val precise = rememberPreciseClickState()
                        val pic = pics[index]
                        val shown = pic.thumb.ifBlank { pic.large }
                        val aspect = if (pic.width > 0 && pic.height > 0) {
                            pic.width.toFloat() / pic.height
                        } else {
                            null
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .imageOrigin(urls[index], 8.dp, aspect = aspect, model = shown)
                                .clip(RoundedCornerShape(8.dp))
                                .background(COUITheme.colorScheme.surfaceContainerHigh)
                                .preciseClickAnchor(precise)
                                .combinedClickable(
                                    onClick = { onImage(urls, index) },
                                    onLongClick = { saver?.show(urls, index, precise) },
                                ),
                        ) {
                            AsyncImage(
                                model = shown,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
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
fun VideoCard(video: VideoInfo, compact: Boolean, onVideo: (VideoInfo) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(if (compact) 2.1f else 1.78f)
            .clip(RoundedCornerShape(10.dp))
            .background(COUITheme.colorScheme.surfaceContainerHigh)
            .clickable { onVideo(video) },
    ) {
        if (video.cover.isNotBlank()) {
            AsyncImage(
                model = video.cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.5f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.8f),
                    )
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(46.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.42f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(COUIIcons.Play, "播放", Modifier.size(26.dp), tint = Color.White)
        }
        if (video.title.isNotBlank()) {
            Text(
                text = video.title,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                color = Color.White,
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun RetweetBlock(
    status: Status,
    compact: Boolean,
    onOpen: () -> Unit,
    onUser: (User) -> Unit,
    onImage: (List<String>, Int) -> Unit,
    onVideo: (VideoInfo) -> Unit,
    onOpenQuoted: ((Status, ExpandOrigin) -> Unit)? = null,
) {
    val scheme = COUITheme.colorScheme
    // The quoted post is a page of its own: it opens from the block it is drawn in and comes back
    // into it, so the block carries the same expand origin a whole card does.
    val quotedOrigin = rememberExpandOrigin(cornerRadius = 10.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .expandOrigin(quotedOrigin)
            .clip(RoundedCornerShape(10.dp))
            .background(nestedBlockTone())
            .clickable {
                if (onOpenQuoted != null) onOpenQuoted(status, quotedOrigin) else onOpen()
            }
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            text = "@${status.user?.name.orEmpty()}：",
            color = scheme.primary,
            fontSize = 13.sp,
            maxLines = 1,
        )
        if (status.textHtml.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            WeiboText(
                html = status.textHtml,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }
        if (status.isVote) {
            Spacer(Modifier.height(6.dp))
            StatusVoteCard(status = status)
        }
        StatusMedia(status, true, onImage, onVideo)
    }
}

@Composable
fun StatusVoteCard(
    status: Status,
    modifier: Modifier = Modifier,
) {
    val linkHandler = LocalLinkHandler.current
    val scope = rememberCoroutineScope()
    val scheme = COUITheme.colorScheme
    val haptic = LocalHapticFeedback.current

    var voteId by remember(status.id) { mutableStateOf(status.voteId) }
    var resolving by remember(status.id) { mutableStateOf(false) }
    var vote by remember(status.id, voteId) { mutableStateOf<VoteInfo?>(null) }
    var loading by remember(status.id, voteId) { mutableStateOf(false) }
    var error by remember(status.id, voteId) { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var showRevoteConfirm by remember { mutableStateOf(false) }
    val selectedIds = remember(status.id, vote?.id) { mutableStateListOf<String>() }

    // 1. 如果没有直接拿到 voteId，异步尝试从正文短链或接口解析
    LaunchedEffect(status.id, status.textHtml) {
        if (voteId == null && !resolving) {
            resolving = true
            val resolved = App.repo.resolveStatusVoteId(status.id, status.textHtml)
            if (resolved != null) {
                voteId = resolved
            }
            resolving = false
        }
    }

    // 2. 拿到 voteId 后，拉取详细投票数据并填充选项
    fun loadVote(id: String) {
        loading = true
        error = null
        scope.launch {
            runCatching { App.repo.fetchVote(id) }
                .onSuccess { info ->
                    vote = info
                    selectedIds.clear()
                    info.options.filter { it.selected }.forEach { selectedIds.add(it.id) }
                    loading = false
                }
                .onFailure { err ->
                    error = err.message ?: "加载投票失败"
                    loading = false
                }
        }
    }

    LaunchedEffect(voteId) {
        val id = voteId
        if (id != null) {
            loadVote(id)
        }
    }

    // 重新投票确认弹窗
    if (showRevoteConfirm && vote != null) {
        val currentVote = vote!!
        ConfirmDialog(
            title = "重新投票",
            message = "确定要撤回之前的投票并重新选择吗？",
            confirmText = "重新投票",
            onConfirm = {
                showRevoteConfirm = false
                submitting = true
                scope.launch {
                    runCatching {
                        App.repo.uncastVote(currentVote.id, currentVote.sk)
                    }.onSuccess { updated ->
                        vote = updated
                        selectedIds.clear()
                        App.toast("已撤回投票，请重新选择")
                    }.onFailure { err ->
                        App.toast(err.message ?: "撤回失败")
                    }
                    submitting = false
                }
            },
            onDismiss = { showRevoteConfirm = false },
        )
    }

    val currentVote = vote
    val currentId = voteId
    val buttonText = COUITheme.textStyles.button.copy(fontSize = 13.sp)

    // 底色与引用微博同层；不能对主题色再 copy(alpha)：暗色的层次色本身就是半透明白，
    // copy 会覆盖而不是叠乘原透明度，卡片会被提亮成一整块浅灰。
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(SquircleShape(12.dp))
            .background(nestedBlockTone())
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { /* 消费点击事件，防止冒泡到外部卡片的详情跳转 */ }
            .padding(12.dp),
    ) {
        // 头部：类型与参与人数、投票标题，点击进入全屏投票页
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(SquircleShape(8.dp))
                .clickable {
                    val vid = currentId ?: currentVote?.id
                    if (vid != null) {
                        linkHandler("https://vote.weibo.com/h5/index/index?vote_id=$vid")
                    }
                },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = CoboIcons.Poll,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = listOfNotNull(
                        "投票",
                        currentVote?.let {
                            when {
                                it.isExpired -> "已截止"
                                it.isMultiSelect -> "多选（最多${it.maxSelect}项）"
                                else -> "单选"
                            }
                        },
                        currentVote?.partNum?.takeIf { it > 0 }?.let { "${fmtCount(it)}人参与" },
                    ).joinToString(" · "),
                    fontSize = 12.sp,
                    color = scheme.onSurfaceSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (currentVote != null && currentVote.title.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = currentVote.title,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = scheme.onSurface,
                )
            }
        }

        // 内容区域
        when {
            // 正在解析短链或拉取数据
            resolving || (loading && currentVote == null) -> {
                Text(
                    text = if (resolving) "正在识别投票…" else "正在加载投票选项…",
                    fontSize = 13.sp,
                    color = scheme.onSurfaceSecondary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            // 加载失败或未找到投票 ID
            currentVote == null -> {
                VoteFooter(
                    text = error ?: "暂未获取到投票详情",
                    color = if (error != null) scheme.error else scheme.onSurfaceSecondary,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    TextButton(
                        text = if (currentId != null) "重试" else "网页查看",
                        onClick = {
                            val vid = currentId
                            if (vid != null) {
                                loadVote(vid)
                            } else {
                                linkHandler("https://m.weibo.cn/status/${status.id}")
                            }
                        },
                        textStyle = buttonText,
                        size = ButtonSize.Small,
                    )
                }
            }

            // 模式 1：尚未参与且进行中 -> 原生可交互投票选项
            !currentVote.parted && !currentVote.isExpired -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    currentVote.options.forEach { option ->
                        val isSelected = selectedIds.contains(option.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(SquircleShape(10.dp))
                                .background(
                                    if (isSelected) scheme.primary.copy(alpha = 0.12f)
                                    else scheme.surfaceContainer
                                )
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (currentVote.isMultiSelect) {
                                        if (isSelected) {
                                            selectedIds.remove(option.id)
                                        } else if (selectedIds.size < currentVote.maxSelect) {
                                            selectedIds.add(option.id)
                                        } else {
                                            App.toast("最多选择 ${currentVote.maxSelect} 项")
                                        }
                                    } else {
                                        selectedIds.clear()
                                        selectedIds.add(option.id)
                                    }
                                }
                                .heightIn(min = 44.dp)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (option.pic.isNotBlank()) {
                                AsyncImage(
                                    model = option.pic,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(SquircleShape(6.dp)),
                                )
                                Spacer(Modifier.width(10.dp))
                            }
                            Text(
                                text = option.title,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                color = scheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(8.dp))
                            if (currentVote.isMultiSelect) {
                                Checkbox(
                                    state = ToggleableState(isSelected),
                                    onClick = null,
                                )
                            } else {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = null,
                                )
                            }
                        }
                    }

                    VoteFooter(
                        text = currentVote.deadlineLabel,
                        modifier = Modifier.padding(top = 2.dp),
                    ) {
                        TextButton(
                            text = if (submitting) "提交中…" else "投票",
                            onClick = {
                                submitting = true
                                scope.launch {
                                    runCatching {
                                        App.repo.castVote(currentVote.id, selectedIds.toList(), currentVote.sk)
                                    }.onSuccess { updated ->
                                        vote = updated
                                        App.toast("投票成功")
                                    }.onFailure { err ->
                                        App.toast(err.message ?: "投票失败")
                                    }
                                    submitting = false
                                }
                            },
                            enabled = selectedIds.isNotEmpty() && !submitting,
                            // COUI 的禁用蓝叠在嵌套层上文字几乎不可读，未选中时退回中性灰
                            colors = ButtonDefaults.textButtonColorsPrimary(
                                disabledColor = scheme.surfaceContainer,
                                disabledTextColor = scheme.onSurfaceSecondary,
                            ),
                            textStyle = buttonText,
                            size = ButtonSize.Small,
                        )
                    }
                }
            }

            // 模式 2：已完成投票或已截止 -> 原生连续曲率百分比胶囊进度条与统计
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    currentVote.options.forEach { option ->
                        val progress = (option.partRatio / 100f).coerceIn(0f, 1f)
                        val animatedProgress by animateFloatAsState(
                            targetValue = progress,
                            animationSpec = tween(600, easing = CubicBezierEasing(0.3f, 0f, 0.1f, 1f)),
                            label = "inline_vote_progress_${option.id}",
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(SquircleShape(10.dp))
                                .background(scheme.surfaceContainer),
                        ) {
                            // 连续曲率填充进度条
                            Box(Modifier.matchParentSize()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(animatedProgress)
                                        .background(
                                            if (option.selected) scheme.primary.copy(alpha = 0.2f)
                                            else scheme.onSurface.copy(alpha = 0.08f),
                                        ),
                                )
                            }
                            // 文本与比例，长选项换行而不是被截断，行高与未投票时一致
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 44.dp)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (option.pic.isNotBlank()) {
                                    AsyncImage(
                                        model = option.pic,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(SquircleShape(6.dp)),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = option.title,
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp,
                                        fontWeight = if (option.selected) FontWeight.Medium else FontWeight.Normal,
                                        color = if (option.selected) scheme.primary else scheme.onSurface,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                    if (option.selected) {
                                        Spacer(Modifier.width(4.dp))
                                        Icon(
                                            imageVector = CoboIcons.Check,
                                            contentDescription = "已投",
                                            tint = scheme.primary,
                                            modifier = Modifier.size(15.dp),
                                        )
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                if (option.partNum.isNotBlank()) {
                                    Text(
                                        text = option.partNum,
                                        fontSize = 11.sp,
                                        color = scheme.onSurfaceSecondary,
                                    )
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(
                                    text = option.ratioLabel,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (option.selected) scheme.primary else scheme.onSurface,
                                )
                            }
                        }
                    }

                    VoteFooter(
                        text = listOf(
                            if (currentVote.parted) "已参与" else "",
                            if (currentVote.isExpired) "已截止" else currentVote.deadlineLabel,
                        ).filter { it.isNotBlank() }.joinToString(" · "),
                        modifier = Modifier.padding(top = 2.dp),
                    ) {
                        if (currentVote.parted && !currentVote.isExpired) {
                            TextButton(
                                text = if (submitting) "撤回中…" else "重新投票",
                                onClick = { showRevoteConfirm = true },
                                enabled = !submitting,
                                textStyle = buttonText,
                                size = ButtonSize.Small,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 投票卡片底部一行：左侧说明文字可截断，右侧操作按钮始终完整，两种投票状态下行高一致。 */
@Composable
private fun VoteFooter(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = COUITheme.colorScheme.onSurfaceSecondary,
    action: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        action()
    }
}

/**
 * The tone of a block nested inside a status card. COUI's light surfaceContainer is the same white
 * as the card, so nested blocks vanished into it. Light takes the page grey; dark keeps the
 * translucent white that already reads as a layer.
 */
@Composable
private fun nestedBlockTone(): Color {
    val scheme = COUITheme.colorScheme
    return if (scheme.background.luminance() < 0.5f) scheme.surfaceContainer else scheme.background
}
