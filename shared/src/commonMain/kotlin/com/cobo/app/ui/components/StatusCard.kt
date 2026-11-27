package com.cobo.app.ui.components

import com.cobo.app.generated.resources.Res
import com.cobo.app.generated.resources.ic_live_photo
import com.cobo.app.platform.platformScreenSizePx
import com.cobo.app.platform.urlEncode
import org.jetbrains.compose.resources.painterResource as kmpPainterResource

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
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.cobo.app.App
import com.cobo.app.data.BigPicInfo
import com.cobo.app.data.ContentFilters
import com.cobo.app.data.FeedVideoCandidate
import com.cobo.app.data.FollowTag
import com.cobo.app.data.MuteUserMode
import com.cobo.app.data.Pic
import com.cobo.app.data.FollowTags
import com.cobo.app.data.Membership
import com.cobo.app.data.ReadLater
import com.cobo.app.data.Status
import com.cobo.app.data.StatusMemory
import com.cobo.app.data.StatusVisibility
import com.cobo.app.data.User
import com.cobo.app.data.UserRemarks
import com.cobo.app.data.VerifiedKind
import com.cobo.app.data.VideoInfo
import com.cobo.app.data.VoteInfo
import com.cobo.app.data.computeVisibleRatio
import com.cobo.app.data.extractTopics
import com.cobo.app.data.formatWeiboTime
import com.cobo.app.data.foldWeiboHtml
import com.cobo.app.data.isServerTruncatedHtml
import com.cobo.app.data.stripTrailingFullTextMarker
import com.cobo.app.data.superTopicId
import com.cobo.app.ui.CoboIcons
import com.cobo.app.ui.ExpandOrigin
import com.cobo.app.ui.FeedInlineVideoPreview
import com.cobo.app.ui.LocalFeedVideoAutoPlay
import com.cobo.app.ui.LocalFeedVideoRouteActive
import com.cobo.app.ui.LocalLinkHandler
import com.cobo.app.ui.LocalStatusEditor
import com.cobo.app.ui.LiveBadgeSize
import com.cobo.app.ui.LocalTopBarBounds
import com.cobo.app.ui.isLongImageAspect
import com.cobo.app.ui.LocalImageOrigins
import com.cobo.app.ui.expandOrigin
import com.cobo.app.ui.extractResolvablePhotoLinksFromHtml
import com.cobo.app.ui.imageOrigin
import com.cobo.app.ui.isImageLink
import com.cobo.app.ui.isWeiboPhotoPageLink
import com.cobo.app.ui.isWeiboWboxLink
import com.cobo.app.ui.components.LocalImageSaver
import com.cobo.app.ui.components.SaveImageItem
import com.cobo.app.ui.rememberExpandOrigin
import com.cobo.app.ui.resolveWeiboLink
import io.github.suqi8.coui.kmp.basic.ButtonDefaults
import io.github.suqi8.coui.kmp.basic.ButtonSize
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.Checkbox
import io.github.suqi8.coui.kmp.basic.DropdownImpl
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.ListPopupColumn
import io.github.suqi8.coui.kmp.basic.ListPopupDefaults
import io.github.suqi8.coui.kmp.basic.RadioButton
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor
import io.github.suqi8.coui.kmp.basic.rememberPreciseClickState
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Community
import io.github.suqi8.coui.kmp.icon.extended.Contacts
import io.github.suqi8.coui.kmp.icon.extended.Delete
import io.github.suqi8.coui.kmp.icon.extended.Forward
import io.github.suqi8.coui.kmp.icon.extended.Edit
import io.github.suqi8.coui.kmp.icon.extended.Lock
import io.github.suqi8.coui.kmp.icon.extended.More
import io.github.suqi8.coui.kmp.icon.extended.Play
import io.github.suqi8.coui.kmp.icon.extended.Report
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.overlay.OverlayListPopup
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.squircle.isSquircleEnabled
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.LocalDismissState
import kotlinx.coroutines.launch

/** 去掉外框后补在列表 12dp 内边距上的横向留白，合计 16dp，与顶栏按钮外框对齐。 */
private val FlatFrameInset = 4.dp

@Composable
fun StatusCard(
    status: Status,
    onOpen: () -> Unit,
    onUser: (User) -> Unit,
    onImage: (List<Pic>, Int) -> Unit,
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
    onDeleted: (() -> Unit)? = null,
    /** 是否保留最外层卡片外框。关掉后只去掉卡片底色与圆角，内容横向与顶栏按钮对齐。 */
    keepFrame: Boolean = true,
    /** 去掉卡片外框时，是否在两条微博之间画一条与正文同缩进的底部分割线。 */
    showBottomDivider: Boolean = false,
    /** 信息流按官方 140 字规则折叠长文；详情页传 false，始终显示全文。 */
    foldLongText: Boolean = App.settings.foldLongText,
    /** 是否处于详情页主帖：为 true 时剥离主帖正文末尾残留的「全文」跳转标签（不影响转发原博）。 */
    isDetail: Boolean = false,
    /** 详情页正文若仍处于服务端截断态，点击尾部「展开全文」时触发的重试补全回调。 */
    onExpandTruncated: (() -> Unit)? = null,
) {
    val displayStatus = App.repo.editedStatus(status.id, status.mid) ?: status
    if (App.repo.isDeleted(displayStatus.id)) return
    // 列表画过就记住这条，详情页第一帧直接用它，不用再等接口。
    remember(displayStatus) { StatusMemory.remember(displayStatus) }
    val scheme = COUITheme.colorScheme
    val scope = rememberCoroutineScope()
    val linkHandler = LocalLinkHandler.current
    val statusEditor = LocalStatusEditor.current
    val effectiveFavorited = App.repo.isFavorited(displayStatus.id)
    val inReadLater = ReadLater.contains(displayStatus.id)
    var cloudFavorited by remember(displayStatus.id) { mutableStateOf(displayStatus.favorited) }
    val author = displayStatus.user
    val authorId = author?.id.orEmpty()
    val isGuest = App.repo.guest
    val isSelf = !isGuest && authorId.isNotBlank() && authorId == App.repo.session?.uid
    val canEdit = isSelf
    val canDelete = isSelf
    val canFollow = author != null && authorId.isNotBlank() && !isSelf
    val canLocalMute = authorId.isNotBlank() && authorId != App.repo.session?.uid
    val reportMid = displayStatus.mid.ifBlank { displayStatus.id }
    val canReport = !isGuest && !isSelf && reportMid.isNotBlank()
    val effectiveFollowing = when {
        !canFollow -> false
        isGuest -> FollowTags[authorId] == FollowTag.Local
        else -> App.repo.isFollowingUser(authorId, author.following)
    }
    val followMenuLabel = when {
        isGuest && effectiveFollowing -> "取消本地关注"
        isGuest -> "本地关注"
        effectiveFollowing -> "取消关注"
        else -> "关注"
    }
    var confirmDelete by remember(displayStatus.id) { mutableStateOf(false) }
    var deleting by remember(displayStatus.id) { mutableStateOf(false) }
    var showMuteDialog by remember(displayStatus.id) { mutableStateOf(false) }
    var showShareSheet by remember(displayStatus.id) { mutableStateOf(false) }
    val handleFavorite: () -> Unit = {
        val nowFavorited = App.repo.toggleLocalFavorite(displayStatus)
        App.toast(if (nowFavorited) "已收藏到本地" else "已取消本地收藏")
    }
    val handleReadLater: () -> Unit = {
        val added = ReadLater.toggle(displayStatus)
        App.toast(if (added) "已加入稍后再看" else "已移出稍后再看")
    }
    val handleCloudFavorite: (() -> Unit)? = if (isGuest) {
        null
    } else {
        {
            scope.launch {
                runCatching { App.repo.favorite(displayStatus.id, cloudFavorited) }
                    .onSuccess {
                        cloudFavorited = !cloudFavorited
                        App.toast(if (cloudFavorited) "已收藏到微博账号" else "已取消微博账号收藏")
                    }
                    .onFailure { App.toast(it.message ?: "操作失败") }
            }
        }
    }
    val handleEdit: (() -> Unit)? = if (canEdit) {
        {
            if (displayStatus.video != null || displayStatus.pics.any { it.isVideo }) {
                App.toast("暂不支持编辑含视频的微博")
            } else {
                statusEditor(displayStatus)
            }
        }
    } else {
        null
    }
    val handleFollow: (() -> Unit)? = if (canFollow) {
        {
            if (isGuest) {
                val on = FollowTags[authorId] == FollowTag.Local
                FollowTags.set(authorId, if (on) null else FollowTag.Local)
                App.notifyFollowChanged()
                App.toast(if (on) "已取消本地关注" else "已加入本地关注")
            } else if (!App.repo.isFollowInFlight(authorId)) {
                val targetFollow = !effectiveFollowing
                scope.launch {
                    runCatching { App.repo.follow(authorId, targetFollow) }
                        .onSuccess { applied ->
                            if (applied) {
                                App.notifyFollowChanged()
                                App.toast(if (targetFollow) "已关注" else "已取消关注")
                            }
                        }
                        .onFailure { App.toast(it.message ?: "操作失败") }
                }
            }
        }
    } else {
        null
    }
    val content: @Composable ColumnScope.() -> Unit = {
        StatusHeader(
            status = displayStatus,
            onUser = onUser,
            compact = compact,
            favorited = effectiveFavorited,
            inReadLater = inReadLater,
            onToggleFavorite = handleFavorite,
            onToggleReadLater = handleReadLater,
            onShareCard = { showShareSheet = true },
            cloudFavorited = cloudFavorited,
            onToggleCloudFavorite = handleCloudFavorite,
            following = effectiveFollowing,
            followLabel = followMenuLabel,
            onToggleFollow = handleFollow,
            onLocalMute = if (canLocalMute) { { showMuteDialog = true } } else null,
            onReport = if (canReport) { { linkHandler(statusReportUrl(reportMid)) } } else null,
            onEdit = handleEdit,
            onDelete = if (canDelete) { { if (!deleting) confirmDelete = true } } else null,
        )
        if (displayStatus.textHtml.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            FoldableWeiboText(
                status = displayStatus,
                fold = foldLongText,
                fontSize = if (compact) 14.sp else 15.sp,
                lineHeight = if (compact) 20.sp else 22.sp,
                stripTrailingFullText = isDetail,
                onExpandTruncated = onExpandTruncated,
            )
        }
        val showOuterVote = displayStatus.isVote && (
            displayStatus.retweeted?.isVote != true ||
                (displayStatus.voteId != null && displayStatus.retweeted.voteId != null && displayStatus.voteId != displayStatus.retweeted.voteId)
            )
        if (showOuterVote) {
            Spacer(Modifier.height(8.dp))
            StatusVoteCard(status = displayStatus, actionsEnabled = actionsEnabled, onCard = keepFrame)
        }
        StatusMedia(displayStatus, compact, onImage, onVideo, onCard = keepFrame)
        if (displayStatus.retweeted != null) {
            Spacer(Modifier.height(8.dp))
            RetweetBlock(
                status = displayStatus.retweeted,
                parentStatusId = displayStatus.id,
                compact = compact,
                actionsEnabled = actionsEnabled,
                onOpen = onOpen,
                onOpenQuoted = onOpenQuoted,
                onUser = onUser,
                onImage = onImage,
                onVideo = onVideo,
                onCard = keepFrame,
                foldLongText = foldLongText,
            )
        }
        val region = displayStatus.region.trim()
        if (showActions || region.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (region.isNotEmpty()) {
                    Text(
                        text = region,
                        fontSize = 11.sp,
                        color = scheme.onSurfaceSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (showActions) {
                    Spacer(Modifier.width(8.dp))
                    ActionIcon(
                        icon = CoboIcons.Repost,
                        text = fmtCount(displayStatus.reposts),
                        onClick = onRepost,
                        enabled = actionsEnabled,
                    )
                    ActionIcon(
                        icon = CoboIcons.Comment,
                        text = fmtCount(displayStatus.comments),
                        onClick = onComment,
                        enabled = actionsEnabled,
                    )
                    ActionIcon(
                        icon = if (displayStatus.liked) CoboIcons.HeartFill else CoboIcons.Heart,
                        text = fmtCount(displayStatus.attitudes),
                        onClick = onLike,
                        tint = if (displayStatus.liked) scheme.primary else scheme.onSurfaceSecondary,
                        enabled = actionsEnabled,
                    )
                }
            }
        }
    }
    if (keepFrame) {
        Card(
            modifier = modifier.fillMaxWidth(),
            cornerRadius = CardDefaults.GroupCornerRadius,
            insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            weight = CardDefaults.GroupWeight,
            onClick = onOpen,
            content = content,
        )
    } else {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpen)
                    // 列表左右各留 12dp，这里再补 4dp，内容左缘正好落在 16dp 的顶栏按钮外框上，
                    // 不再比返回/搜索按钮更靠外。
                    .padding(horizontal = FlatFrameInset, vertical = 12.dp),
                content = content,
            )
            if (showBottomDivider) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = FlatFrameInset),
                    thickness = 0.5.dp,
                    color = scheme.dividerLine,
                )
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "删除微博",
            message = "删除后不可恢复，确定删除这条微博吗？",
            confirmText = "删除",
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                if (deleting) return@ConfirmDialog
                deleting = true
                scope.launch {
                    runCatching { App.repo.deleteStatus(status.id, status.mid) }
                        .onSuccess {
                            App.toast("已删除")
                            onDeleted?.invoke()
                        }
                        .onFailure {
                            App.toast(it.message ?: "删除失败")
                        }
                    deleting = false
                }
            },
        )
    }
    if (showMuteDialog) {
        QuickMuteDialog(
            status = displayStatus,
            onDismiss = { showMuteDialog = false },
        )
    }
    if (showShareSheet) {
        StatusShareSheet(
            status = displayStatus,
            onDismiss = { showShareSheet = false },
        )
    }
}

@Composable
fun StatusHeader(
    status: Status,
    onUser: (User) -> Unit,
    compact: Boolean,
    favorited: Boolean = App.repo.isFavorited(status.id),
    inReadLater: Boolean = false,
    cloudFavorited: Boolean = status.favorited,
    showMore: Boolean = true,
    onToggleFavorite: (() -> Unit)? = null,
    onToggleReadLater: (() -> Unit)? = null,
    onShareCard: (() -> Unit)? = null,
    onToggleCloudFavorite: (() -> Unit)? = null,
    following: Boolean = false,
    followLabel: String = if (following) "取消关注" else "关注",
    onToggleFollow: (() -> Unit)? = null,
    onLocalMute: (() -> Unit)? = null,
    onReport: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    val scheme = COUITheme.colorScheme
    val user = status.user
    Row(verticalAlignment = Alignment.CenterVertically) {
        Avatar(
            url = user?.avatar,
            size = if (compact) 38.dp else 42.dp,
            verifiedKind = user?.verifiedKind ?: VerifiedKind.None,
            modifier = Modifier.clickable { user?.let(onUser) },
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = UserRemarks.displayName(user?.id, user?.name.orEmpty()),
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
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
                        MemberBadge(membership, compact = true, rank = user?.mbrank ?: 0)
                    }
                    val tag = FollowTags[user?.id]
                    if (tag != null) {
                        Spacer(Modifier.width(4.dp))
                        FollowTagBadge(tag, compact = true)
                    }
                }
                if (showMore && (onShareCard != null || onToggleFavorite != null || onToggleReadLater != null || onToggleCloudFavorite != null || onToggleFollow != null || onLocalMute != null || onReport != null || onEdit != null || onDelete != null)) {
                    Spacer(Modifier.width(6.dp))
                    StatusMoreButton(
                        statusId = status.id,
                        favorited = favorited,
                        inReadLater = inReadLater,
                        cloudFavorited = cloudFavorited,
                        onShareCard = onShareCard,
                        onToggleFavorite = onToggleFavorite,
                        onToggleReadLater = onToggleReadLater,
                        onToggleCloudFavorite = onToggleCloudFavorite,
                        following = following,
                        followLabel = followLabel,
                        onToggleFollow = onToggleFollow,
                        onLocalMute = onLocalMute,
                        onReport = onReport,
                        onEdit = onEdit,
                        onDelete = onDelete,
                    )
                }
            }
            val timeAndSource = buildString {
                append(formatWeiboTime(status.createdAt))
                if (status.source.isNotBlank()) {
                    append(" 来自 ")
                    append(status.source)
                }
            }
            val visibility = status.visibility
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (timeAndSource.isNotBlank()) {
                    Text(
                        text = timeAndSource,
                        fontSize = 11.sp,
                        color = scheme.onSurfaceSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                if (visibility != null) {
                    if (timeAndSource.isNotBlank()) Spacer(Modifier.width(5.dp))
                    StatusVisibilityIcon(visibility = visibility)
                }
            }
        }
    }
}

@Composable
private fun StatusVisibilityIcon(
    visibility: StatusVisibility,
    compact: Boolean = false,
) {
    val scheme = COUITheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    val (icon, tint) = when (visibility.type) {
        0 -> COUIIcons.Community to scheme.onSurfaceSecondary
        6, 2 -> COUIIcons.Contacts to if (dark) Color(0xFF4ADE80) else Color(0xFF16A34A)
        1 -> COUIIcons.Lock to if (dark) Color(0xFFFBBF24) else Color(0xFFD97706)
        10 -> COUIIcons.Community to scheme.primary
        else -> COUIIcons.Lock to scheme.primary
    }
    Icon(
        imageVector = icon,
        contentDescription = visibility.label,
        tint = tint,
        modifier = Modifier
            .size(if (compact) 11.dp else 12.dp)
            .testTag("status_visibility_${visibility.type}"),
    )
}

/** 网页版举报入口：`type=1` 是举报微博，`url` 指向这条微博。 */
internal fun statusReportUrl(mid: String): String {
    val target = urlEncode("/detail/$mid")
    return "https://service.account.weibo.com/reportspam" +
        "?rid=$mid&from=10106&type=1&url=$target&bottomnav=1&wvr=5"
}

@Composable
private fun StatusMoreButton(
    statusId: String,
    favorited: Boolean,
    inReadLater: Boolean,
    cloudFavorited: Boolean,
    onShareCard: (() -> Unit)?,
    onToggleFavorite: (() -> Unit)?,
    onToggleReadLater: (() -> Unit)?,
    onToggleCloudFavorite: (() -> Unit)?,
    following: Boolean,
    followLabel: String,
    onToggleFollow: (() -> Unit)?,
    onLocalMute: (() -> Unit)?,
    onReport: (() -> Unit)?,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    val scheme = COUITheme.colorScheme
    var expanded by remember(statusId) { mutableStateOf(false) }
    val optionSize =
        (if (onShareCard != null) 1 else 0) +
            (if (onToggleCloudFavorite != null) 1 else 0) +
            (if (onToggleFavorite != null) 1 else 0) +
            (if (onToggleReadLater != null) 1 else 0) +
            (if (onToggleFollow != null) 1 else 0) +
            (if (onLocalMute != null) 1 else 0) +
            (if (onReport != null) 1 else 0) +
            (if (onEdit != null) 1 else 0) +
            (if (onDelete != null) 1 else 0)
    val squircleEnabled = isSquircleEnabled()
    val moreButtonShape = remember(squircleEnabled) { SquircleShape(6.dp, squircleEnabled = squircleEnabled) }
    Box {
        Box(
            modifier = Modifier
                .height(20.dp)
                .widthIn(min = 32.dp)
                .clip(moreButtonShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { expanded = true },
                )
                .padding(horizontal = 2.dp)
                .semantics { contentDescription = "更多" }
                .testTag("status_more"),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Icon(
                imageVector = COUIIcons.More,
                contentDescription = null,
                tint = scheme.onSurfaceSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
        OverlayListPopup(
            show = expanded,
            popupPositionProvider = ListPopupDefaults.DropdownPositionProvider,
            onDismissRequest = { expanded = false },
        ) {
            val dismiss = LocalDismissState.current
            ListPopupColumn {
                var idx = 0
                if (onShareCard != null) {
                    DropdownImpl(
                        item = DropdownItem(
                            text = "图文分享",
                            icon = { iconModifier ->
                                Box(iconModifier, contentAlignment = Alignment.Center) {
                                    Icon(COUIIcons.Forward, null, Modifier.size(18.dp), tint = scheme.onSurfaceContainer)
                                }
                            },
                        ),
                        optionSize = optionSize,
                        isSelected = false,
                        index = idx++,
                        modifier = Modifier.testTag("status_share_card"),
                        groupKey = "status_more_$statusId",
                        onSelectedIndexChange = {
                            dismiss?.invoke()
                            expanded = false
                            onShareCard()
                        },
                    )
                }
                if (onToggleFavorite != null) {
                    val favIcon = if (favorited) CoboIcons.StarFill else CoboIcons.Star
                    val favTint = if (favorited) scheme.primary else scheme.onSurfaceContainer
                    DropdownImpl(
                        item = DropdownItem(
                            text = if (favorited) "取消本地收藏" else "收藏到本地",
                            icon = { iconModifier ->
                                Box(iconModifier, contentAlignment = Alignment.Center) {
                                    Icon(favIcon, null, Modifier.size(18.dp), tint = favTint)
                                }
                            },
                        ),
                        optionSize = optionSize,
                        isSelected = false,
                        index = idx++,
                        groupKey = "status_more_$statusId",
                        onSelectedIndexChange = {
                            dismiss?.invoke()
                            expanded = false
                            onToggleFavorite()
                        },
                    )
                }
                if (onToggleCloudFavorite != null) {
                    val cloudIcon = if (cloudFavorited) CoboIcons.CloudFill else CoboIcons.Cloud
                    val cloudTint = if (cloudFavorited) scheme.primary else scheme.onSurfaceContainer
                    DropdownImpl(
                        item = DropdownItem(
                            text = if (cloudFavorited) "取消微博账号收藏" else "收藏到微博账号",
                            icon = { iconModifier ->
                                Box(iconModifier, contentAlignment = Alignment.Center) {
                                    Icon(cloudIcon, null, Modifier.size(18.dp), tint = cloudTint)
                                }
                            },
                        ),
                        optionSize = optionSize,
                        isSelected = false,
                        index = idx++,
                        groupKey = "status_more_$statusId",
                        onSelectedIndexChange = {
                            dismiss?.invoke()
                            expanded = false
                            onToggleCloudFavorite()
                        },
                    )
                }
                if (onToggleReadLater != null) {
                    val markIcon = if (inReadLater) CoboIcons.BookmarkFill else CoboIcons.Bookmark
                    val markTint = if (inReadLater) scheme.primary else scheme.onSurfaceContainer
                    DropdownImpl(
                        item = DropdownItem(
                            text = if (inReadLater) "移出稍后再看" else "稍后再看",
                            icon = { iconModifier ->
                                Box(iconModifier, contentAlignment = Alignment.Center) {
                                    Icon(markIcon, null, Modifier.size(18.dp), tint = markTint)
                                }
                            },
                        ),
                        optionSize = optionSize,
                        isSelected = false,
                        index = idx++,
                        groupKey = "status_more_$statusId",
                        onSelectedIndexChange = {
                            dismiss?.invoke()
                            expanded = false
                            onToggleReadLater()
                        },
                    )
                }
                if (onToggleFollow != null) {
                    val followIcon = if (following) CoboIcons.Unfollow else CoboIcons.Follow
                    val followTint = if (following) scheme.primary else scheme.onSurfaceContainer
                    DropdownImpl(
                        item = DropdownItem(
                            text = followLabel,
                            icon = { iconModifier ->
                                Box(iconModifier, contentAlignment = Alignment.Center) {
                                    Icon(followIcon, null, Modifier.size(18.dp), tint = followTint)
                                }
                            },
                        ),
                        optionSize = optionSize,
                        isSelected = false,
                        index = idx++,
                        groupKey = "status_more_$statusId",
                        onSelectedIndexChange = {
                            dismiss?.invoke()
                            expanded = false
                            onToggleFollow()
                        },
                    )
                }
                if (onLocalMute != null) {
                    DropdownImpl(
                        item = DropdownItem(
                            text = "本地屏蔽",
                            icon = { iconModifier ->
                                Box(iconModifier, contentAlignment = Alignment.Center) {
                                    Icon(CoboIcons.Block, null, Modifier.size(18.dp), tint = scheme.onSurfaceContainer)
                                }
                            },
                        ),
                        optionSize = optionSize,
                        isSelected = false,
                        index = idx++,
                        groupKey = "status_more_$statusId",
                        onSelectedIndexChange = {
                            dismiss?.invoke()
                            expanded = false
                            onLocalMute()
                        },
                    )
                }
                if (onReport != null) {
                    DropdownImpl(
                        item = DropdownItem(
                            text = "举报微博",
                            icon = { iconModifier ->
                                Box(iconModifier, contentAlignment = Alignment.Center) {
                                    Icon(COUIIcons.Report, null, Modifier.size(18.dp), tint = scheme.onSurfaceContainer)
                                }
                            },
                        ),
                        optionSize = optionSize,
                        isSelected = false,
                        index = idx++,
                        groupKey = "status_more_$statusId",
                        onSelectedIndexChange = {
                            dismiss?.invoke()
                            expanded = false
                            onReport()
                        },
                    )
                }
                if (onEdit != null) {
                    DropdownImpl(
                        item = DropdownItem(
                            text = "编辑微博",
                            icon = { iconModifier ->
                                Box(iconModifier, contentAlignment = Alignment.Center) {
                                    Icon(COUIIcons.Edit, null, Modifier.size(18.dp), tint = scheme.onSurfaceContainer)
                                }
                            },
                        ),
                        optionSize = optionSize,
                        isSelected = false,
                        index = idx++,
                        groupKey = "status_more_$statusId",
                        onSelectedIndexChange = {
                            dismiss?.invoke()
                            expanded = false
                            onEdit()
                        },
                    )
                }
                if (onDelete != null) {
                    DropdownImpl(
                        item = DropdownItem(
                            text = "删除微博",
                            alert = true,
                            icon = { iconModifier ->
                                Box(iconModifier, contentAlignment = Alignment.Center) {
                                    Icon(COUIIcons.Delete, null, Modifier.size(18.dp), tint = scheme.error)
                                }
                            },
                        ),
                        optionSize = optionSize,
                        isSelected = false,
                        index = idx,
                        groupKey = "status_more_$statusId",
                        onSelectedIndexChange = {
                            dismiss?.invoke()
                            expanded = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

private data class QuickMuteOption(
    val key: String,
    val title: String,
    val summary: String? = null,
    val selected: Boolean = false,
    val action: () -> Unit,
)

/**
 * 卡片三点菜单里的快捷本地屏蔽面板。
 * 选中项在弹窗收起动画完成后（`onDismissFinished`）执行，避免列表中被屏蔽卡片立刻销毁引发残影。
 */
@Composable
private fun QuickMuteDialog(
    status: Status,
    onDismiss: () -> Unit,
) {
    val rules = ContentFilters.rules
    val author = status.user
    val authorId = author?.id.orEmpty()
    val authorName = author?.name?.ifBlank { "该用户" } ?: "该用户"
    val currentMode = ContentFilters.muteMode(authorId)
    val selfUid = App.repo.session?.uid
    val quotedUser = status.retweeted?.user?.takeIf {
        it.id.isNotBlank() && it.id != authorId && it.id != selfUid
    }
    val topics = remember(status.id, status.textHtml, status.retweeted?.textHtml) {
        extractTopics(status).take(3)
    }

    val options = remember(authorId, authorName, currentMode, quotedUser, topics, rules) {
        buildList {
            if (authorId.isNotBlank()) {
                val allMuted = currentMode == MuteUserMode.All
                add(
                    QuickMuteOption(
                        key = "author_all_$authorId",
                        title = if (allMuted) "取消静音「$authorName」" else "静音「$authorName」",
                        summary = "隐藏其全部原创与转发微博",
                        selected = allMuted,
                        action = {
                            ContentFilters.setMutedUser(
                                uid = authorId,
                                name = author?.name.orEmpty(),
                                mode = if (allMuted) null else MuteUserMode.All,
                            )
                            App.toast(if (allMuted) "已取消静音" else "已静音「$authorName」")
                        },
                    ),
                )
                val repostsMuted = currentMode == MuteUserMode.RepostsOnly
                add(
                    QuickMuteOption(
                        key = "author_repost_$authorId",
                        title = if (repostsMuted) "取消屏蔽「$authorName」的转发" else "仅屏蔽「$authorName」的转发",
                        summary = "保留其原创微博",
                        selected = repostsMuted,
                        action = {
                            ContentFilters.setMutedUser(
                                uid = authorId,
                                name = author?.name.orEmpty(),
                                mode = if (repostsMuted) null else MuteUserMode.RepostsOnly,
                            )
                            App.toast(if (repostsMuted) "已恢复显示其转发" else "已屏蔽「$authorName」的转发")
                        },
                    ),
                )
            }
            if (quotedUser != null) {
                val qName = quotedUser.name.ifBlank { "原博作者" }
                val qMuted = ContentFilters.muteMode(quotedUser.id) == MuteUserMode.All
                add(
                    QuickMuteOption(
                        key = "quoted_all_${quotedUser.id}",
                        title = if (qMuted) "取消静音原博作者「$qName」" else "静音原博作者「$qName」",
                        summary = "隐藏引用或转发该用户的微博",
                        selected = qMuted,
                        action = {
                            ContentFilters.setMutedUser(
                                uid = quotedUser.id,
                                name = quotedUser.name,
                                mode = if (qMuted) null else MuteUserMode.All,
                            )
                            App.toast(if (qMuted) "已取消静音" else "已静音「$qName」")
                        },
                    ),
                )
            }
            topics.forEach { topic ->
                val muted = ContentFilters.hasTopic(topic)
                add(
                    QuickMuteOption(
                        key = "topic_$topic",
                        title = if (muted) "取消屏蔽话题 #$topic#" else "屏蔽话题 #$topic#",
                        selected = muted,
                        action = {
                            if (muted) {
                                ContentFilters.removeTopic(topic)
                                App.toast("已取消屏蔽话题 #$topic#")
                            } else {
                                val err = ContentFilters.addTopic(topic)
                                App.toast(err ?: "已屏蔽话题 #$topic#")
                            }
                        },
                    ),
                )
            }
        }
    }

    var show by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { show = true }

    OverlayDialog(
        show = show,
        title = "本地屏蔽",
        outsideMargin = DpSize(16.dp, 8.dp),
        onDismissRequest = { show = false },
        onDismissFinished = {
            if (!settled) {
                settled = true
                val act = pendingAction
                onDismiss()
                act?.invoke()
            }
        },
    ) {
        Column {
            Box(
                modifier = Modifier.fillMaxWidth(),
                propagateMinConstraints = true,
            ) {
                ListPopupColumn {
                    options.forEachIndexed { index, opt ->
                        DropdownImpl(
                            item = DropdownItem(text = opt.title, summary = opt.summary),
                            optionSize = options.size,
                            isSelected = opt.selected,
                            index = index,
                            groupKey = "quick_mute_${status.id}",
                            onSelectedIndexChange = {
                                pendingAction = options[it].action
                                show = false
                            },
                        )
                    }
                }
            }
            DialogButtonBar(
                negative = DialogButtonBarAction(text = "取消", onClick = { show = false }),
                positive = null,
            )
        }
    }
}

@Composable
fun StatusMedia(
    status: Status,
    compact: Boolean,
    onImage: (List<Pic>, Int) -> Unit,
    onVideo: (VideoInfo) -> Unit,
    onCard: Boolean = true,
) {
    val video = status.video?.let { info ->
        if (info.statusId.isBlank() && status.id.isNotBlank()) info.copy(statusId = status.id) else info
    }
    if (video != null && video.url.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        VideoCard(video, compact, onVideo)
    }
    val resolvableInlineLinks = remember(status.id, status.textHtml, status.pics) {
        if (status.pics.isEmpty()) extractResolvablePhotoLinksFromHtml(status.textHtml) else emptyList()
    }
    var resolvedInlinePics by remember(status.id, resolvableInlineLinks) {
        mutableStateOf(
            if (resolvableInlineLinks.isEmpty()) emptyList()
            else App.repo.cachedInlineImageLinks(resolvableInlineLinks).orEmpty(),
        )
    }
    LaunchedEffect(status.id, resolvableInlineLinks) {
        if (resolvableInlineLinks.isNotEmpty() && resolvedInlinePics.isEmpty()) {
            resolvedInlinePics = App.repo.resolveInlineImageLinks(resolvableInlineLinks)
        }
    }
    val rawPics = if (status.pics.isNotEmpty()) status.pics else resolvedInlinePics
    val effectivePics = remember(status.id, rawPics) {
        if (status.id.isNotBlank() && rawPics.any { it.isVideo && it.videoStatusId.isBlank() }) {
            rawPics.map { pic ->
                if (pic.isVideo && pic.videoStatusId.isBlank()) pic.copy(videoStatusId = status.id) else pic
            }
        } else {
            rawPics
        }
    }
    if (effectivePics.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        ImageGrid(effectivePics, compact, onImage, onVideo)
    }
    val bigPic = status.bigPic
    if (bigPic != null && bigPic.url.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        if (bigPic.isLarge) {
            BigPicCard(bigPic, onImage)
        } else {
            PageLinkCard(bigPic, compact = compact, onCard = onCard)
        }
    }
}

/** 普通网页 / 微博正文外链卡片：左侧小缩略图 + 右侧标题与摘要，不把缩略图放大成整行大图。 */
@Composable
private fun PageLinkCard(
    info: BigPicInfo,
    compact: Boolean,
    onCard: Boolean,
) {
    val scheme = COUITheme.colorScheme
    val linkHandler = LocalLinkHandler.current
    val hasTarget = info.scheme.isNotBlank()
    val thumbSize = if (compact) 52.dp else 60.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(nestedBlockTone(onCard))
            .then(if (hasTarget) Modifier.clickable { linkHandler(info.scheme) } else Modifier)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = info.url,
            contentDescription = info.title.ifBlank { null },
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(thumbSize)
                .clip(RoundedCornerShape(8.dp))
                .background(scheme.surfaceContainerHigh),
        )
        if (info.title.isNotBlank() || info.subtitle.isNotBlank()) {
            Spacer(Modifier.width(10.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                if (info.title.isNotBlank()) {
                    Text(
                        text = info.title,
                        fontSize = if (compact) 13.sp else 14.sp,
                        lineHeight = if (compact) 18.sp else 19.sp,
                        fontWeight = FontWeight.Medium,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (info.subtitle.isNotBlank()) {
                    Text(
                        text = info.subtitle,
                        fontSize = if (compact) 11.sp else 12.sp,
                        lineHeight = if (compact) 15.sp else 17.sp,
                        color = scheme.onSurfaceSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun BigPicCard(
    bigPic: BigPicInfo,
    onImage: (List<Pic>, Int) -> Unit,
) {
    val linkHandler = LocalLinkHandler.current
    val saver = LocalImageSaver.current
    val precise = rememberPreciseClickState()
    var ratio by remember(bigPic.url) { mutableFloatStateOf(16f / 9f) }
    val isWbox = remember(bigPic.scheme) { isWeiboWboxLink(bigPic.scheme) }
    val isSuperTopicScheme = remember(bigPic.scheme) { superTopicId(bigPic.scheme) != null }
    val targetUrl = remember(bigPic.scheme, isWbox) {
        if (isWbox) null else resolveWeiboLink(bigPic.scheme)
    }
    val openImageFirst = isWbox || isSuperTopicScheme ||
        isImageLink(bigPic.scheme) || isWeiboPhotoPageLink(bigPic.scheme)
    val singlePic = remember(bigPic.url) {
        listOf(Pic(thumb = bigPic.url, large = bigPic.url, preserveUrl = true))
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .imageOrigin(bigPic.url, 10.dp, aspect = ratio, model = bigPic.url)
            .clip(RoundedCornerShape(10.dp))
            .background(COUITheme.colorScheme.surfaceContainerHigh)
            .preciseClickAnchor(precise)
            .combinedClickable(
                onClick = {
                    if (!openImageFirst && targetUrl != null) linkHandler(bigPic.scheme) else onImage(singlePic, 0)
                },
                onLongClick = { saver?.show(listOf(bigPic.url), 0, precise, preserveUrls = true) },
            ),
    ) {
        val origins = LocalImageOrigins.current
        AsyncImage(
            model = bigPic.url,
            contentDescription = bigPic.title.ifBlank { null },
            contentScale = ContentScale.Crop,
            onSuccess = { state ->
                origins.putCacheKey(bigPic.url, state.result.memoryCacheKey)
                val size = state.painter.intrinsicSize
                if (size.width > 0f && size.height > 0f) {
                    ratio = (size.width / size.height).coerceIn(0.62f, 2.2f)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (isWbox || isSuperTopicScheme || bigPic.title.isNotBlank()) {
            val label = buildString {
                if (bigPic.title.isNotBlank()) append(bigPic.title)
                when {
                    isWbox -> {
                        if (isNotEmpty()) append(" · ")
                        append("小程序预览图")
                    }
                    isSuperTopicScheme && targetUrl != null -> {
                        if (isNotEmpty()) append(" · ")
                        append("进入超话")
                    }
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.56f))
                    .then(
                        if (!isWbox && targetUrl != null) {
                            Modifier.clickable { linkHandler(bigPic.scheme) }
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            ) {
                Text(
                    text = label,
                    fontSize = 10.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun ImageGrid(
    pics: List<Pic>,
    compact: Boolean,
    onImage: (List<Pic>, Int) -> Unit,
    onVideo: (VideoInfo) -> Unit = {},
) {
    val urls = pics.map { it.large }
    val imageItems = remember(pics) {
        pics.filterNot { it.isVideo }.map {
            SaveImageItem(url = it.large, liveUrl = it.live, preserveUrl = it.preserveUrl)
        }
    }
    // 长按任意一张图片缩略图就能弹出保存菜单（实况图按实况保存），视频条目不走图片保存。
    val saver = LocalImageSaver.current
    val origins = LocalImageOrigins.current
    if (pics.size == 1) {
        val pic = pics.first()
        val ratio = if (pic.width > 0 && pic.height > 0) {
            (pic.width.toFloat() / pic.height.toFloat()).coerceIn(0.62f, 1.9f)
        } else {
            1.45f
        }
        // 真实的宽高比（不是裁切后的格子比例）交给看图页，它才能把第一帧摆得和这张缩略图一模一样。
        val declaredAspect = if (pic.width > 0 && pic.height > 0) pic.width.toFloat() / pic.height else null
        var measuredAspect by remember(urls[0]) { mutableStateOf<Float?>(null) }
        val aspect = declaredAspect ?: measuredAspect
        val isLong = !pic.isVideo && isLongImageAspect(aspect)
        val precise = rememberPreciseClickState()
        Box(
            modifier = Modifier
                .fillMaxWidth(if (compact) 0.72f else 0.86f)
                .aspectRatio(ratio)
                .imageOrigin(urls[0], 10.dp, aspect = aspect, model = pic.large)
                .clip(RoundedCornerShape(10.dp))
                .background(COUITheme.colorScheme.surfaceContainerHigh)
                .preciseClickAnchor(precise)
                .combinedClickable(
                    onClick = {
                        if (pic.isVideo) {
                            onVideo(
                                VideoInfo(
                                    title = pic.videoTitle,
                                    cover = pic.large.ifBlank { pic.thumb },
                                    url = pic.videoUrl,
                                    durationSec = pic.durationSec,
                                    fid = pic.videoFid,
                                    statusId = pic.videoStatusId,
                                ),
                            )
                        } else {
                            onImage(pics, 0)
                        }
                    },
                    onLongClick = if (!pic.isVideo && imageItems.isNotEmpty()) {
                        { saver?.showItems(imageItems, 0, precise) }
                    } else {
                        null
                    },
                ),
        ) {
            AsyncImage(
                model = pic.large,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = if (isLong) Alignment.TopCenter else Alignment.Center,
                onSuccess = { state ->
                    origins.putCacheKey(urls[0], state.result.memoryCacheKey)
                    if (declaredAspect == null) {
                        val s = state.painter.intrinsicSize
                        if (s.width > 0f && s.height > 0f) measuredAspect = s.width / s.height
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            when {
                pic.isVideo -> {
                    GridPlayBadge(46.dp, 26.dp, Modifier.align(Alignment.Center))
                    if (pic.durationSec > 0) {
                        VideoDurationBadge(
                            seconds = pic.durationSec,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(SingleBadgeInset),
                        )
                    }
                }
                else -> {
                    if (pic.isLive) {
                        LiveBadge(urls[0], Modifier.align(Alignment.BottomStart).padding(SingleBadgeInset))
                    }
                    if (isLong) {
                        LongImageBadge(urls[0], Modifier.align(Alignment.BottomEnd).padding(SingleBadgeInset))
                    }
                }
            }
        }
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
                        val declaredAspect = if (pic.width > 0 && pic.height > 0) {
                            pic.width.toFloat() / pic.height
                        } else {
                            null
                        }
                        var measuredAspect by remember(urls[index]) { mutableStateOf<Float?>(null) }
                        val aspect = declaredAspect ?: measuredAspect
                        val isLong = !pic.isVideo && isLongImageAspect(aspect)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .imageOrigin(urls[index], 8.dp, aspect = aspect, model = shown)
                                .clip(RoundedCornerShape(8.dp))
                                .background(COUITheme.colorScheme.surfaceContainerHigh)
                                .preciseClickAnchor(precise)
                                .combinedClickable(
                                    onClick = {
                                        if (pic.isVideo) {
                                            onVideo(
                                                VideoInfo(
                                                    title = pic.videoTitle,
                                                    cover = pic.large.ifBlank { pic.thumb },
                                                    url = pic.videoUrl,
                                                    durationSec = pic.durationSec,
                                                    fid = pic.videoFid,
                                                    statusId = pic.videoStatusId,
                                                ),
                                            )
                                        } else {
                                            onImage(pics, index)
                                        }
                                    },
                                    onLongClick = if (!pic.isVideo && imageItems.isNotEmpty()) {
                                        {
                                            val saveIdx = pics.take(index).count { !it.isVideo }
                                            saver?.showItems(imageItems, saveIdx, precise)
                                        }
                                    } else {
                                        null
                                    },
                                ),
                        ) {
                            AsyncImage(
                                model = shown,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                alignment = if (isLong) Alignment.TopCenter else Alignment.Center,
                                onSuccess = { state ->
                                    origins.putCacheKey(urls[index], state.result.memoryCacheKey)
                                    if (declaredAspect == null) {
                                        val s = state.painter.intrinsicSize
                                        if (s.width > 0f && s.height > 0f) measuredAspect = s.width / s.height
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                            when {
                                pic.isVideo -> {
                                    GridPlayBadge(34.dp, 18.dp, Modifier.align(Alignment.Center))
                                    if (pic.durationSec > 0) {
                                        VideoDurationBadge(
                                            seconds = pic.durationSec,
                                            modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
                                        )
                                    }
                                }
                                else -> {
                                    if (pic.isLive) {
                                        LiveBadge(urls[index], Modifier.align(Alignment.BottomStart).padding(GridBadgeInset))
                                    }
                                    if (isLong) {
                                        LongImageBadge(urls[index], Modifier.align(Alignment.BottomEnd).padding(4.dp))
                                    }
                                }
                            }
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
private fun GridPlayBadge(
    size: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(COUIIcons.Play, "播放视频", Modifier.size(iconSize), tint = Color.White)
    }
}

internal fun formatVideoDuration(seconds: Int): String {
    val s = seconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) {
        "${h}:${(m).toString().padStart(2, '0')}:${(sec).toString().padStart(2, '0')}"
    } else {
        "${m}:${(sec).toString().padStart(2, '0')}"
    }
}

@Composable
private fun VideoDurationBadge(seconds: Int, modifier: Modifier = Modifier) {
    Text(
        text = formatVideoDuration(seconds),
        color = Color.White,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.58f))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

/** 缩略图右下角的「长图」角标，看图页覆盖当前图时先隐去，退场落位后再淡回。 */
@Composable
internal fun LongImageBadge(url: String, modifier: Modifier = Modifier) {
    val covered = LocalImageOrigins.current.viewing == url
    val fade = animateFloatAsState(
        targetValue = if (covered) 0f else 1f,
        animationSpec = tween(LiveBadgeFadeMillis),
        label = "longImageBadge",
    )
    Text(
        text = "长图",
        color = Color.White,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier
            .graphicsLayer { alpha = fade.value }
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.58f))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

/** 多图格子里、单张大图上，实况标记各自离左下角的距离。 */
private val GridBadgeInset = 2.dp
private val SingleBadgeInset = 4.dp

/**
 * 缩略图左下角的实况标记，点进看图页后按住才会播那段短视频。
 *
 * 看图页是整块照片盖在这一格上的，退场到最后一帧才整层撤掉；标记若一直在，就会在那一帧整块
 * 跳出来。所以被打开的那一张在页面开着时先让位，等页面走了再淡回来。别的缩略图照旧。
 */
@Composable
private fun LiveBadge(url: String, modifier: Modifier = Modifier) {
    val covered = LocalImageOrigins.current.viewing == url
    val fade = animateFloatAsState(
        targetValue = if (covered) 0f else 1f,
        animationSpec = tween(LiveBadgeFadeMillis),
        label = "liveBadge",
    )
    Box(
        modifier
            .graphicsLayer { alpha = fade.value }
            .size(LiveBadgeSize)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = kmpPainterResource(Res.drawable.ic_live_photo),
            contentDescription = "实况",
            modifier = Modifier.size(13.dp),
            tint = Color.White,
        )
    }
}

/** 看图页撤走之后标记淡回来的时长。 */
private const val LiveBadgeFadeMillis = 220

@Composable
fun VideoCard(video: VideoInfo, compact: Boolean, onVideo: (VideoInfo) -> Unit) {
    val autoPlay = LocalFeedVideoAutoPlay.current
    val routeActive = LocalFeedVideoRouteActive.current
    val topBarBounds = LocalTopBarBounds.current
    val settingEnabled = App.settings.feedVideoAutoPlay
    val eligible = autoPlay != null &&
        routeActive &&
        settingEnabled &&
        !video.isLive &&
        video.url.isNotBlank()
    val cardKey = remember(video.url, video.fid, video.statusId) {
        autoPlay?.nextKey().orEmpty()
    }
    var previewFailed by remember(video.url, video.fid, video.statusId) { mutableStateOf(false) }
    var firstFrameReady by remember(cardKey) { mutableStateOf(false) }
    var muted by remember(cardKey) { mutableStateOf(true) }

    DisposableEffect(autoPlay, cardKey, eligible, previewFailed) {
        if (autoPlay != null && cardKey.isNotEmpty() && (!eligible || previewFailed)) {
            autoPlay.unregister(cardKey)
        }
        onDispose {
            if (autoPlay != null && cardKey.isNotEmpty()) {
                autoPlay.unregister(cardKey)
            }
        }
    }

    val isPreviewActive = eligible && !previewFailed && cardKey.isNotEmpty() && autoPlay?.activeKey == cardKey
    LaunchedEffect(isPreviewActive) {
        if (!isPreviewActive) {
            firstFrameReady = false
            muted = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(if (compact) 2.1f else 1.78f)
            .then(
                if (autoPlay != null && cardKey.isNotEmpty() && eligible && !previewFailed) {
                    Modifier.onGloballyPositioned { coords ->
                        val pos = coords.positionInWindow()
                        val w = coords.size.width.toFloat()
                        val h = coords.size.height.toFloat()
                        val (screenW, screenH) = platformScreenSizePx()
                        val viewportTop = topBarBounds?.bottom ?: 0f
                        val viewportBottom = screenH.toFloat()
                        val viewportRight = screenW.toFloat()
                        val ratio = computeVisibleRatio(
                            leftPx = pos.x,
                            topPx = pos.y,
                            rightPx = pos.x + w,
                            bottomPx = pos.y + h,
                            viewportLeftPx = 0f,
                            viewportTopPx = viewportTop,
                            viewportRightPx = viewportRight,
                            viewportBottomPx = viewportBottom,
                        )
                        autoPlay.updateCandidate(
                            candidate = FeedVideoCandidate(
                                key = cardKey,
                                leftPx = pos.x,
                                topPx = pos.y,
                                rightPx = pos.x + w,
                                bottomPx = pos.y + h,
                                visibleRatio = ratio,
                            ),
                            viewportTop = viewportTop,
                            viewportBottom = viewportBottom,
                        )
                    }
                } else {
                    Modifier
                },
            )
            .clip(RoundedCornerShape(10.dp))
            .background(COUITheme.colorScheme.surfaceContainerHigh)
            .clickable {
                autoPlay?.pauseAll()
                onVideo(video)
            },
    ) {
        if (video.cover.isNotBlank()) {
            AsyncImage(
                model = video.cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (isPreviewActive) {
            FeedInlineVideoPreview(
                video = video,
                muted = muted,
                onFirstFrame = { firstFrameReady = true },
                onFailed = { previewFailed = true },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = if (firstFrameReady) 1f else 0f },
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
        val playBadgeAlpha by animateFloatAsState(
            targetValue = if (isPreviewActive && firstFrameReady) 0f else 1f,
            animationSpec = tween(180),
            label = "videoPlayBadgeAlpha",
        )
        if (playBadgeAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer { alpha = playBadgeAlpha }
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.42f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(COUIIcons.Play, "播放", Modifier.size(26.dp), tint = Color.White)
            }
        }
        if (video.isLive) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.56f))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF3B30)),
                )
                Text(
                    text = "直播",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        val showMuteButton = isPreviewActive && firstFrameReady
        if (video.title.isNotBlank()) {
            Text(
                text = video.title,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(
                        start = 12.dp,
                        top = 8.dp,
                        end = if (showMuteButton) 44.dp else 12.dp,
                        bottom = 8.dp,
                    ),
                color = Color.White,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showMuteButton) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.56f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { muted = !muted }
                    .testTag("feed_video_mute_toggle"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (muted) CoboIcons.VolumeOff else CoboIcons.VolumeOn,
                    contentDescription = if (muted) "取消静音" else "静音",
                    modifier = Modifier.size(16.dp),
                    tint = Color.White,
                )
            }
        }
    }
}

/**
 * 卡片正文。长微博在信息流里按官方规则折叠，点「展开全文」就地展开；详情页传 fold = false。
 * 展开状态只跟着这一条微博的 id 走，滑出屏幕再回来重新折叠。
 */
@Composable
private fun FoldableWeiboText(
    status: Status,
    fold: Boolean,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    stripTrailingFullText: Boolean = false,
    onExpandTruncated: (() -> Unit)? = null,
) {
    val serverTruncated = remember(status.textHtml, status.foldedTextHtml) {
        status.foldedTextHtml == null && isServerTruncatedHtml(status.textHtml)
    }
    val canRetryExpand = !fold && serverTruncated && onExpandTruncated != null
    val fullHtml = remember(status.textHtml, stripTrailingFullText, canRetryExpand) {
        if (stripTrailingFullText || canRetryExpand) stripTrailingFullTextMarker(status.textHtml) else status.textHtml
    }
    val folded = remember(status.id, fullHtml, status.foldedTextHtml, canRetryExpand) {
        if (canRetryExpand) {
            null
        } else {
            status.foldedTextHtml ?: fullHtml
                .takeIf { it.isNotBlank() && !isServerTruncatedHtml(it) }
                ?.let { foldWeiboHtml(it) }
        }
    }
    val canFold = fold && !folded.isNullOrBlank() && folded != fullHtml
    var expanded by remember(status.id) { mutableStateOf(false) }
    val folding = canFold && !expanded
    val displayHtml = when {
        folding -> folded.orEmpty()
        expanded -> stripTrailingFullTextMarker(fullHtml)
        else -> fullHtml
    }
    val showExpand = folding || canRetryExpand
    WeiboText(
        html = displayHtml,
        fontSize = fontSize,
        lineHeight = lineHeight,
        expandLabel = if (showExpand) "展开全文" else null,
        onExpand = when {
            folding -> ({ expanded = true })
            canRetryExpand -> onExpandTruncated
            else -> null
        },
    )
}

@Composable
fun RetweetBlock(
    status: Status,
    compact: Boolean,
    onOpen: () -> Unit,
    onUser: (User) -> Unit,
    onImage: (List<Pic>, Int) -> Unit,
    onVideo: (VideoInfo) -> Unit,
    onOpenQuoted: ((Status, ExpandOrigin) -> Unit)? = null,
    actionsEnabled: Boolean = true,
    /** 当前块是否直接落在卡片面上：决定嵌套块的底色，卡片去掉后要反过来才看得见。 */
    onCard: Boolean = true,
    /** 被转发的原博在信息流里是否折叠长文。 */
    foldLongText: Boolean = true,
    parentStatusId: String = "",
) {
    val scheme = COUITheme.colorScheme
    remember(status) { StatusMemory.remember(status) }
    // The quoted post is a page of its own: it opens from the block it is drawn in and comes back
    // into it, so the block carries the same expand origin a whole card does.
    val quotedOrigin = rememberExpandOrigin(cornerRadius = 10.dp)
    SideEffect {
        quotedOrigin.originKey = "quoted:$parentStatusId:${status.id}"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .expandOrigin(quotedOrigin)
            .clip(RoundedCornerShape(10.dp))
            .background(nestedBlockTone(onCard))
            .clickable {
                if (onOpenQuoted != null) onOpenQuoted(status, quotedOrigin) else onOpen()
            }
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "@${UserRemarks.displayName(status.user?.id, status.user?.name.orEmpty())}：",
                color = scheme.primary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            val retweetVisibility = status.visibility
            if (retweetVisibility != null && !retweetVisibility.isPublic) {
                Spacer(Modifier.width(5.dp))
                StatusVisibilityIcon(visibility = retweetVisibility, compact = true)
            }
        }
        if (status.textHtml.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            FoldableWeiboText(
                status = status,
                fold = foldLongText,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }
        if (status.isVote) {
            Spacer(Modifier.height(6.dp))
            StatusVoteCard(status = status, actionsEnabled = actionsEnabled, onCard = !onCard)
        }
        StatusMedia(status, true, onImage, onVideo, onCard = !onCard)
    }
}

@Composable
fun StatusVoteCard(
    status: Status,
    modifier: Modifier = Modifier,
    actionsEnabled: Boolean = true,
    /** 是否直接落在卡片面上，决定投票块的底色；卡片去掉后要反过来。 */
    onCard: Boolean = true,
) {
    if (!actionsEnabled) {
        Text(
            text = "投票内容请通过网页查看",
            modifier = modifier,
            color = COUITheme.colorScheme.onSurfaceSecondary,
            fontSize = 13.sp,
        )
        return
    }
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
            .background(nestedBlockTone(onCard))
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
 * as the card, so nested blocks vanished into it. Light takes the page grey on a card and the card
 * white on the page; dark keeps the translucent white that already reads as a layer.
 */
@Composable
private fun nestedBlockTone(onCard: Boolean): Color {
    val scheme = COUITheme.colorScheme
    return if (scheme.background.luminance() < 0.5f || !onCard) {
        scheme.surfaceContainer
    } else {
        scheme.background
    }
}
