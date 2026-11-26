package com.cobo.app.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cobo.app.App
import com.cobo.app.data.Comment
import com.cobo.app.data.Pic
import com.cobo.app.data.User
import com.cobo.app.data.UserRemarks
import com.cobo.app.data.VerifiedKind
import com.cobo.app.data.formatWeiboTime
import com.cobo.app.data.parseWeiboTimeMillis
import com.cobo.app.ui.AppBarSurface
import com.cobo.app.ui.CoboIcons
import com.cobo.app.ui.ExpandEasing
import com.cobo.app.ui.PinnedBarDensity
import com.cobo.app.ui.blurEffectsEnabled
import com.cobo.app.ui.components.Avatar
import com.cobo.app.ui.components.ComposerBottomSheet
import com.cobo.app.ui.components.EmptyState
import com.cobo.app.ui.components.ErrorState
import com.cobo.app.ui.components.LoadingState
import com.cobo.app.ui.components.WeiboText
import com.cobo.app.ui.components.fmtCount
import com.cobo.app.ui.components.navBottomPadding
import com.cobo.app.ui.layerBackdropIf
import com.cobo.app.ui.components.CoboRefreshSpinner
import com.cobo.app.ui.pageContainerColor
import com.cobo.app.ui.pinnedHeight
import io.github.suqi8.coui.kmp.basic.AppBarBlurOverlayDefaults
import io.github.suqi8.coui.kmp.basic.BindScrollToTop
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.LocalEdgeToEdge
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.basic.couiTopAppBarActionItem
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Close
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** 楼中楼排序方式：热度 / 正序（最早在前） / 倒序（最新在前）。 */
internal enum class SubReplySort(val label: String) {
    Hot("热度"),
    Old("正序"),
    New("倒序"),
}

internal fun orderSubReplies(list: List<Comment>, sort: SubReplySort): List<Comment> {
    val deduped = list.distinctBy { it.id }
    return when (sort) {
        SubReplySort.Hot -> deduped.sortedWith(
            compareByDescending<Comment> { it.likes }
                .thenBy { parseWeiboTimeMillis(it.createdAt) }
                .thenBy { it.id },
        )
        SubReplySort.Old -> deduped.sortedWith(
            compareBy<Comment> { parseWeiboTimeMillis(it.createdAt) }
                .thenBy { it.id },
        )
        SubReplySort.New -> deduped.sortedWith(
            compareByDescending<Comment> { parseWeiboTimeMillis(it.createdAt) }
                .thenByDescending { it.id },
        )
    }
}

/**
 * 评论详情与楼中楼覆盖层（参考 BiliCO `ReplyDetailOverlay` 实现）：
 * 点击任意评论即可从底部滑入展开完整根评论及其楼中楼列表，支持「热度 / 正序 / 倒序」切换与回复点赞同步。
 */
@Composable
internal fun CommentDetailOverlay(
    root: Comment?,
    statusId: String,
    mid: String,
    readOnly: Boolean,
    keepFrame: Boolean,
    onImage: (List<Pic>, Int) -> Unit,
    onRepost: (Comment) -> Unit,
    onReport: (Comment) -> Unit,
    onUser: (User) -> Unit,
    onSyncComment: (Comment) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var displayedRoot by remember { mutableStateOf<Comment?>(null) }
    if (root != null) displayedRoot = root

    BackHandler(enabled = root != null) { onClose() }

    AnimatedVisibility(
        visible = root != null,
        modifier = modifier.fillMaxSize(),
        enter = slideInVertically(
            animationSpec = tween(durationMillis = 280, easing = ExpandEasing),
            initialOffsetY = { it },
        ) + fadeIn(animationSpec = tween(durationMillis = 180)),
        exit = slideOutVertically(
            animationSpec = tween(durationMillis = 240, easing = ExpandEasing),
            targetOffsetY = { it },
        ) + fadeOut(animationSpec = tween(durationMillis = 160)),
    ) {
        val currentRoot = displayedRoot ?: return@AnimatedVisibility
        CommentDetailSheetContent(
            initialRoot = currentRoot,
            statusId = statusId,
            mid = mid,
            readOnly = readOnly,
            keepFrame = keepFrame,
            active = root != null,
            onImage = onImage,
            onRepost = onRepost,
            onReport = onReport,
            onUser = onUser,
            onSyncComment = onSyncComment,
            onClose = onClose,
        )
    }
}

@Composable
private fun CommentDetailSheetContent(
    initialRoot: Comment,
    statusId: String,
    mid: String,
    readOnly: Boolean,
    keepFrame: Boolean,
    active: Boolean,
    onImage: (List<Pic>, Int) -> Unit,
    onRepost: (Comment) -> Unit,
    onReport: (Comment) -> Unit,
    onUser: (User) -> Unit,
    onSyncComment: (Comment) -> Unit,
    onClose: () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    BindScrollToTop(listState, enabled = active)

    var rootComment by remember(initialRoot.id) { mutableStateOf(initialRoot) }
    LaunchedEffect(initialRoot) {
        if (initialRoot.id == rootComment.id) rootComment = initialRoot
    }

    val rawReplies = remember(initialRoot.id) {
        mutableStateListOf<Comment>().apply { addAll(initialRoot.replies) }
    }
    var replyTotal by remember(initialRoot.id) {
        mutableIntStateOf(maxOf(initialRoot.replyCount, initialRoot.replies.size))
    }
    var sort by remember(initialRoot.id) { mutableStateOf(SubReplySort.Hot) }
    var cursor by remember(initialRoot.id) { mutableStateOf<String?>(null) }
    var cursorType by remember(initialRoot.id) { mutableIntStateOf(0) }
    var loading by remember(initialRoot.id) { mutableStateOf(false) }
    var end by remember(initialRoot.id) { mutableStateOf(false) }
    var error by remember(initialRoot.id) { mutableStateOf<String?>(null) }
    var replyTarget by remember(initialRoot.id) { mutableStateOf<Comment?>(null) }

    suspend fun fetchNextPage(force: Boolean = false): Boolean {
        if (loading || end) return false
        loading = true
        error = null
        return try {
            val page = App.repo.commentReplies(
                cid = rootComment.id,
                maxId = cursor,
                maxIdType = cursorType,
                force = force,
            )
            val before = rawReplies.size
            val byId = rawReplies.associateByTo(LinkedHashMap()) { it.id }
            page.items.forEach { byId[it.id] = it }
            rawReplies.clear()
            rawReplies.addAll(byId.values)
            cursor = page.maxId
            cursorType = page.maxIdType
            if (page.nextPage > 0) replyTotal = maxOf(replyTotal, page.nextPage)
            replyTotal = maxOf(replyTotal, rawReplies.size)
            if (page.items.isEmpty() || rawReplies.size == before ||
                page.maxId.isBlank() || page.maxId == "0"
            ) {
                end = true
            }
            true
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            error = t.message ?: "回复加载失败"
            false
        } finally {
            loading = false
        }
    }

    LaunchedEffect(initialRoot.id) {
        listState.scrollToItem(0)
        cursor = null
        cursorType = 0
        end = false
        fetchNextPage(force = false)
    }

    // 切到正序或倒序时，如果当前线程还有少量未拉取分页，尽量拉齐前几页（最多 4 页）后再按时间排序，
    // 让「倒序」能立刻看到最新回复。
    LaunchedEffect(sort, initialRoot.id) {
        if (sort != SubReplySort.Hot && !end) {
            var extraPages = 0
            while (!end && extraPages < 4 && rawReplies.size < 100) {
                if (!fetchNextPage(force = false)) break
                extraPages++
            }
        }
    }

    LaunchedEffect(listState, rawReplies.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { lastVisible ->
                if (lastVisible != null && rawReplies.isNotEmpty() && lastVisible >= rawReplies.size + 1) {
                    fetchNextPage(force = false)
                }
            }
    }

    val sortedReplies = remember(rawReplies.toList(), sort) {
        orderSubReplies(rawReplies.toList(), sort)
    }

    fun toggleRootLike() {
        val current = rootComment
        if (App.repo.isCommentLikeInFlight(current.id)) return
        scope.launch {
            if (!App.confirmLike(current.liked)) return@launch
            if (App.repo.isCommentLikeInFlight(current.id)) return@launch
            val optimistic = current.copy(
                liked = !current.liked,
                likes = (current.likes + if (current.liked) -1 else 1).coerceAtLeast(0),
            )
            rootComment = optimistic
            onSyncComment(optimistic)
            runCatching { App.repo.likeComment(current.id, current.liked) }
                .onFailure {
                    rootComment = current
                    onSyncComment(current)
                    App.toast(it.message ?: "操作失败")
                }
        }
    }

    fun toggleSubLike(target: Comment) {
        val idx = rawReplies.indexOfFirst { it.id == target.id }
        if (idx < 0) return
        val current = rawReplies[idx]
        if (App.repo.isCommentLikeInFlight(current.id)) return
        scope.launch {
            if (!App.confirmLike(current.liked)) return@launch
            if (App.repo.isCommentLikeInFlight(current.id)) return@launch
            val now = rawReplies.indexOfFirst { it.id == current.id }
            if (now < 0) return@launch
            rawReplies[now] = current.copy(
                liked = !current.liked,
                likes = (current.likes + if (current.liked) -1 else 1).coerceAtLeast(0),
            )
            runCatching { App.repo.likeComment(current.id, current.liked) }
                .onFailure {
                    val back = rawReplies.indexOfFirst { it.id == current.id }
                    if (back >= 0) rawReplies[back] = current
                    App.toast(it.message ?: "操作失败")
                }
        }
    }

    val topBarHeight = pinnedHeight(TopAppBarDefaults.SmallTopAppBarCenterHeight)
    val backdrop = rememberLayerBackdrop()
    val overlayBackdrop = rememberLayerBackdrop()
    val nestedTone = if (keepFrame) pageContainerColor() else scheme.surfaceContainer

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pageContainerColor())
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdropIf(blurEffectsEnabled(), backdrop)
                .background(pageContainerColor()),
        ) {
            CompositionLocalProvider(LocalBackdrop provides if (blurEffectsEnabled()) backdrop else null) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().scrollEndHaptic(),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = topBarHeight + 8.dp,
                        bottom = navBottomPadding() + 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(if (keepFrame) 8.dp else 0.dp),
                ) {
                    item(key = "detail_root_comment") {
                        val rootBody: @Composable () -> Unit = {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = if (keepFrame) 14.dp else FlatCommentInset,
                                        vertical = 12.dp,
                                    ),
                            ) {
                                Avatar(
                                    url = rootComment.user?.avatar,
                                    size = 36.dp,
                                    verifiedKind = rootComment.user?.verifiedKind ?: VerifiedKind.None,
                                    modifier = Modifier.clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) { rootComment.user?.let(onUser) },
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
                                                text = UserRemarks.displayName(rootComment.user?.id, rootComment.user?.name.orEmpty()),
                                                fontSize = 15.sp,
                                                color = scheme.onSurfaceSecondary,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier
                                                    .weight(1f, fill = false)
                                                    .clickable(
                                                        interactionSource = remember { MutableInteractionSource() },
                                                        indication = null,
                                                    ) { rootComment.user?.let(onUser) },
                                            )
                                            CommentHeaderBadges(rootComment, 14.dp)
                                        }
                                        CommentMoreButton(
                                            comment = rootComment,
                                            readOnly = readOnly,
                                            onRepost = { onRepost(rootComment) },
                                            onReport = { onReport(rootComment) },
                                            onReply = { replyTarget = rootComment },
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    WeiboText(
                                        html = rootComment.textHtml,
                                        fontSize = 15.sp,
                                        lineHeight = 21.sp,
                                    )
                                    CommentInlinePics(rootComment, 96.dp, onImage)
                                    rootComment.reply?.let { quoted ->
                                        Spacer(Modifier.height(6.dp))
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(nestedTone)
                                                .padding(8.dp),
                                        ) {
                                            Text(
                                                "@${UserRemarks.displayName(quoted.user?.id, quoted.user?.name.orEmpty())}：",
                                                fontSize = 12.sp,
                                                color = scheme.primary,
                                                modifier = Modifier.clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null,
                                                ) { quoted.user?.let(onUser) },
                                            )
                                            WeiboText(
                                                html = quoted.textHtml,
                                                fontSize = 13.sp,
                                                lineHeight = 18.sp,
                                            )
                                            CommentInlinePics(quoted, 72.dp, onImage)
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = formatWeiboTime(rootComment.createdAt),
                                            fontSize = 11.sp,
                                            color = scheme.onSurfaceSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        CommentActionButtons(
                                            likes = rootComment.likes,
                                            liked = rootComment.liked,
                                            enabled = !readOnly,
                                            compact = false,
                                            replyModifier = Modifier.testTag("comment_detail_root_reply"),
                                            likeModifier = Modifier.testTag("comment_detail_root_like"),
                                            onReply = { replyTarget = rootComment },
                                            onLike = ::toggleRootLike,
                                        )
                                    }
                                }
                            }
                        }
                        if (keepFrame) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = CardDefaults.GroupCornerRadius,
                                weight = CardDefaults.GroupWeight,
                            ) { rootBody() }
                        } else {
                            Column {
                                rootBody()
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = FlatCommentInset),
                                    color = scheme.dividerLine.copy(alpha = 0.6f),
                                    thickness = 0.6.dp,
                                )
                            }
                        }
                    }

                    item(key = "detail_sub_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = if (keepFrame) 4.dp else FlatCommentInset,
                                    end = if (keepFrame) 4.dp else FlatCommentInset,
                                    top = 8.dp,
                                    bottom = 4.dp,
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "相关回复共 ${maxOf(replyTotal, sortedReplies.size)} 条",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = scheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(8.dp))
                            SubReplySortTabRow(
                                sort = sort,
                                onSelectSort = { sort = it },
                            )
                        }
                    }

                    when {
                        sortedReplies.isEmpty() && loading -> {
                            item(key = "sub_loading") { LoadingState() }
                        }
                        sortedReplies.isEmpty() && error != null -> {
                            item(key = "sub_error") {
                                ErrorState(error ?: "加载失败") {
                                    scope.launch { fetchNextPage(force = true) }
                                }
                            }
                        }
                        sortedReplies.isEmpty() -> {
                            item(key = "sub_empty") { EmptyState("暂无楼中楼回复") }
                        }
                        else -> {
                            itemsIndexed(sortedReplies, key = { _, reply -> "sub_${reply.id}" }) { index, reply ->
                                val itemBlock: @Composable () -> Unit = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .then(
                                                if (readOnly) Modifier
                                                else Modifier.clickable { replyTarget = reply }
                                            )
                                            .padding(
                                                horizontal = if (keepFrame) 14.dp else FlatCommentInset,
                                                vertical = 12.dp,
                                            ),
                                    ) {
                                        ReplyRow(
                                            reply = reply,
                                            onLike = { toggleSubLike(reply) },
                                            onReply = { replyTarget = reply },
                                            enabled = !readOnly,
                                            keepFrame = keepFrame,
                                            showActions = true,
                                            compact = false,
                                            onImage = onImage,
                                            onRepost = onRepost,
                                            onReport = onReport,
                                            onUser = onUser,
                                        )
                                    }
                                }
                                if (keepFrame) {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("sub_reply_card_$index"),
                                        cornerRadius = CardDefaults.GroupCornerRadius,
                                        weight = CardDefaults.GroupWeight,
                                    ) { itemBlock() }
                                } else {
                                    Column(Modifier.testTag("sub_reply_card_$index")) {
                                        itemBlock()
                                        HorizontalDivider(
                                            modifier = Modifier.padding(start = FlatCommentInset + 46.dp, end = FlatCommentInset),
                                            color = scheme.dividerLine.copy(alpha = 0.45f),
                                            thickness = 0.5.dp,
                                        )
                                    }
                                }
                            }
                            if (loading) {
                                item(key = "sub_loading_more") {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().height(44.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CoboRefreshSpinner(size = 18.dp, strokeWidth = 2.dp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        CompositionLocalProvider(
            LocalBackdrop provides if (blurEffectsEnabled()) backdrop else null,
            LocalEdgeToEdge provides true,
        ) {
            PinnedBarDensity {
                AppBarSurface(
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
                    containerColor = pageContainerColor(),
                    scrollFraction = {
                        val scrolled = if (listState.firstVisibleItemIndex > 0) Float.MAX_VALUE
                        else listState.firstVisibleItemScrollOffset.toFloat()
                        AppBarBlurOverlayDefaults.scrollAlpha(scrolled, density)
                    },
                ) {
                    SmallTopAppBar(
                        title = "评论详情",
                        color = Color.Transparent,
                        showDivider = false,
                        gradientGlass = false,
                        actions = {
                            HighlightedIconButton(
                                onClick = onClose,
                                modifier = Modifier.couiTopAppBarActionItem().testTag("comment_detail_close"),
                            ) {
                                Icon(COUIIcons.Close, "关闭", Modifier.size(22.dp))
                            }
                        },
                    )
                }
            }
        }
    }

    replyTarget?.let { target ->
        CompositionLocalProvider(LocalBackdrop provides if (blurEffectsEnabled()) overlayBackdrop else null) {
            ComposerBottomSheet(
                title = "回复 @${target.user?.name.orEmpty()}",
                hint = "写回复",
                submitText = "回复",
                maxImages = 1,
                onDismiss = { replyTarget = null },
                onSubmit = { text, images, _ ->
                    try {
                        val picIds = images.map { App.repo.uploadImage(it) }
                        val created = App.repo.replyComment(statusId, mid, target.id, text, picIds)
                        App.toast("回复成功")
                        val byId = rawReplies.associateByTo(LinkedHashMap()) { it.id }
                        byId[created.id] = created
                        rawReplies.clear()
                        rawReplies.addAll(byId.values)
                        replyTotal += 1
                        val updatedRoot = rootComment.copy(
                            replyCount = maxOf(rootComment.replyCount + 1, replyTotal),
                            replies = (listOf(created) + rootComment.replies).distinctBy { it.id },
                        )
                        rootComment = updatedRoot
                        onSyncComment(updatedRoot)
                        true
                    } catch (c: CancellationException) {
                        throw c
                    } catch (t: Throwable) {
                        App.toast(t.message ?: "回复失败")
                        false
                    }
                },
            )
        }
    }
}

private val SubReplySortEntries = SubReplySort.entries.map { it.label to it }

/** 楼中楼排序迷你分段胶囊（热度 / 正序 / 倒序），与微博正文「热度 / 时间」统一样式。 */
@Composable
private fun SubReplySortTabRow(
    sort: SubReplySort,
    onSelectSort: (SubReplySort) -> Unit,
    modifier: Modifier = Modifier,
) {
    CompactSortSwitch(
        entries = SubReplySortEntries,
        selected = sort,
        onSelect = onSelectSort,
        testTagOf = { "sub_reply_sort_${it.name.lowercase()}" },
        modifier = modifier,
    )
}
