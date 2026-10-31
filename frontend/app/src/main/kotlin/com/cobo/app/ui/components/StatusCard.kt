package com.cobo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.layout.ContentScale
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
import com.cobo.app.data.formatWeiboTime
import com.cobo.app.ui.CoboIcons
import com.cobo.app.ui.ExpandOrigin
import com.cobo.app.ui.LocalLinkHandler
import com.cobo.app.ui.expandOrigin
import com.cobo.app.ui.imageOrigin
import com.cobo.app.ui.components.LocalImageSaver
import com.cobo.app.ui.rememberExpandOrigin
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor
import io.github.suqi8.coui.kmp.basic.rememberPreciseClickState
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Play
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
    // COUI's light surfaceContainer is the same white as the card, so the quoted post vanished into
    // it. Light takes the page grey; dark keeps the translucent white that already reads as a layer.
    val quotedTone =
        if (scheme.background.luminance() < 0.5f) scheme.surfaceContainer else scheme.background
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .expandOrigin(quotedOrigin)
            .clip(RoundedCornerShape(10.dp))
            .background(quotedTone)
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
    var voteId by remember(status.id) { mutableStateOf(status.voteId) }
    var resolving by remember(status.id) { mutableStateOf(false) }

    val onClick = {
        val currentId = voteId
        if (currentId != null) {
            linkHandler("https://vote.weibo.com/h5/index/index?vote_id=$currentId")
        } else if (!resolving) {
            resolving = true
            scope.launch {
                val resolved = App.repo.resolveStatusVoteId(status.id, status.textHtml)
                if (resolved != null) {
                    voteId = resolved
                    linkHandler("https://vote.weibo.com/h5/index/index?vote_id=$resolved")
                } else {
                    App.toast("未找到投票信息")
                }
                resolving = false
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.surfaceContainerHigh.copy(alpha = 0.65f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(scheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = CoboIcons.Poll,
                    contentDescription = "投票",
                    tint = scheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(scheme.primary.copy(alpha = 0.14f))
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "投票",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.primary,
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "微博投票",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = scheme.onSurface,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "点击进入原生页面参与投票与查看统计",
                    fontSize = 12.sp,
                    color = scheme.onSurfaceSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(scheme.primary)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = if (resolving) "加载中…" else "去投票",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = scheme.onPrimary,
                )
            }
        }
    }
}
