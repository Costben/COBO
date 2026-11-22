package com.cobo.app.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.cobo.app.App
import com.cobo.app.data.Comment
import com.cobo.app.data.CommentBadge
import com.cobo.app.data.CommentSort
import com.cobo.app.data.HistoryItem
import com.cobo.app.data.Membership
import com.cobo.app.data.Pic
import com.cobo.app.data.Status
import com.cobo.app.data.StatusMemory
import com.cobo.app.data.VerifiedKind
import com.cobo.app.data.formatWeiboTime
import com.cobo.app.data.stripHtml
import com.cobo.app.ui.NavState
import com.cobo.app.ui.AppBarSurface
import com.cobo.app.ui.blurEffectsEnabled
import com.cobo.app.ui.layerBackdropIf
import com.cobo.app.ui.PinnedBarDensity
import com.cobo.app.ui.pinnedHeight
import com.cobo.app.ui.pinnedRowsHeight
import com.cobo.app.ui.pageContainerColor
import com.cobo.app.ui.Route
import com.cobo.app.ui.TopBarBounds
import com.cobo.app.ui.attachToRow
import com.cobo.app.ui.reportTopBarBottom
import com.cobo.app.ui.CoboIcons
import com.cobo.app.ui.components.Avatar
import com.cobo.app.ui.components.BackIconButton
import com.cobo.app.ui.components.ComposerBottomSheet
import com.cobo.app.ui.components.ErrorState
import com.cobo.app.ui.components.LoadingState
import com.cobo.app.ui.components.MemberBadge
import com.cobo.app.ui.components.StatusCard
import com.cobo.app.ui.components.WeiboText
import com.cobo.app.ui.components.fmtCount
import io.github.suqi8.coui.kmp.basic.AppBarBlurOverlayDefaults
import io.github.suqi8.coui.kmp.basic.BindScrollToTop
import io.github.suqi8.coui.kmp.basic.BottomFloatingNavigationBarDefaults
import io.github.suqi8.coui.kmp.basic.BottomFloatingToolbarDefaults
import io.github.suqi8.coui.kmp.basic.CapsuleOutlineShape
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.DropdownImpl
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton
import io.github.suqi8.coui.kmp.basic.HighlightedIconButtonDefaults
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.ListPopupColumn
import io.github.suqi8.coui.kmp.basic.ListPopupDefaults
import io.github.suqi8.coui.kmp.basic.MetaBallParticle
import io.github.suqi8.coui.kmp.basic.MetaBallSurface
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.SpotLightDefaults
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.basic.couiTopAppBarActionItem
import io.github.suqi8.coui.kmp.basic.couiTouchMotion
import io.github.suqi8.coui.kmp.basic.LocalEdgeToEdge
import io.github.suqi8.coui.kmp.basic.popupBottomBarrier
import io.github.suqi8.coui.kmp.basic.rememberCOUITouchMotionState
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.layerBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Copy
import io.github.suqi8.coui.kmp.icon.extended.Edit
import io.github.suqi8.coui.kmp.icon.extended.Forward
import io.github.suqi8.coui.kmp.icon.extended.More
import io.github.suqi8.coui.kmp.icon.extended.Refresh
import io.github.suqi8.coui.kmp.icon.extended.Reply
import io.github.suqi8.coui.kmp.icon.extended.Report
import io.github.suqi8.coui.kmp.overlay.OverlayListPopup
import io.github.suqi8.coui.kmp.squircle.isSquircleEnabled
import kotlinx.coroutines.CancellationException
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.LocalDismissState
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic
import java.net.URLEncoder
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DetailScreen(nav: NavState, id: String, mid: String, readOnly: Boolean = false) {
    val scope = rememberCoroutineScope()
    // 列表里点进来的那条先顶上：正文、图片、互动数都已经有了，先画出来，接口回来再换。
    var status by remember(id) { mutableStateOf(StatusMemory.find(id)) }
    var detailError by remember(id) { mutableStateOf<String?>(null) }
    val comments = remember(id) { mutableStateListOf<Comment>() }
    var maxId by remember(id) { mutableStateOf<String?>(null) }
    var maxIdType by remember(id) { mutableStateOf(0) }
    var sort by remember(id) { mutableStateOf(CommentSort.Hot) }
    var nextCommentPage by remember(id) { mutableStateOf(1) }
    var loadingComments by remember(id) { mutableStateOf(false) }
    var commentsEnd by remember(id) { mutableStateOf(false) }
    var commentError by remember(id) { mutableStateOf<String?>(null) }
    var switchingSort by remember(id) { mutableStateOf(false) }
    var showSwitchProgress by remember(id) { mutableStateOf(false) }
    var repostDialog by remember { mutableStateOf(false) }
    // 从评论菜单进来的转发：带上这条评论，composer 预填成引用它。
    var repostComment by remember { mutableStateOf<Comment?>(null) }
    var commentDialog by remember { mutableStateOf(false) }
    var activeCommentDetail by remember(id) { mutableStateOf<Comment?>(null) }
    val listState = rememberLazyListState()
    BindScrollToTop(listState)
    // The bar floats over this list too, so the quoted post inside the card below has to be able to
    // read where the bar ends when a page returns into it.
    val topBarBounds = remember { TopBarBounds() }
    val backdrop = rememberLayerBackdrop()

    // A link may carry a Base62 mblogid or numeric id; once loaded, prefer the canonical numeric id and mid.
    fun effectiveId(): String = status?.id?.takeIf { it.isNotBlank() } ?: id
    fun effectiveMid(): String = status?.mid?.takeIf { it.isNotBlank() } ?: mid

    suspend fun loadComments(reset: Boolean, force: Boolean = false) {
        if (loadingComments) return
        if (!reset && switchingSort) return
        loadingComments = true
        commentError = null
        val requestedSort = sort
        try {
            val page = App.repo.comments(
                id = effectiveId(),
                mid = effectiveMid(),
                sort = requestedSort,
                maxId = if (reset) null else maxId,
                maxIdType = if (reset) 0 else maxIdType,
                page = if (reset) 1 else nextCommentPage,
                force = force,
            )
            // Another order took over while this page was on the wire; its rows are not ours.
            if (sort != requestedSort) return
            if (reset) comments.clear()
            val known = comments.mapTo(HashSet()) { it.id }
            page.items.forEach { if (known.add(it.id)) comments.add(it) }
            maxId = page.maxId
            maxIdType = page.maxIdType
            nextCommentPage = page.nextPage
            if (page.items.isEmpty() ||
                (sort == CommentSort.Time && page.nextPage == 0)
            ) {
                commentsEnd = true
            }
        } catch (t: Throwable) {
            commentError = t.message ?: "评论加载失败"
        } finally {
            loadingComments = false
        }
    }

    fun switchSort(next: CommentSort) {
        if (sort == next) return
        val previous = sort
        val previousMaxId = maxId
        val previousMaxIdType = maxIdType
        val previousPage = nextCommentPage
        val previousEnd = commentsEnd
        sort = next
        switchingSort = true
        maxId = null
        maxIdType = 0
        nextCommentPage = 1
        commentsEnd = false
        commentError = null
        // Keep the current rows on screen until the other order arrives; the header shows a
        // slim progress line only if the wait is long enough to notice.
        scope.launch {
            // Let a page already on the wire for the old order land and be dropped first.
            while (loadingComments) delay(30)
            try {
                loadComments(true, force = true)
            } finally {
                switchingSort = false
            }
            // The old rows are still on screen, so a failed switch must also put the control back
            // instead of labelling hot comments as the newest ones.
            val failure = commentError
            if (failure != null && sort == next) {
                sort = previous
                maxId = previousMaxId
                maxIdType = previousMaxIdType
                nextCommentPage = previousPage
                commentsEnd = previousEnd
                commentError = null
                App.toast(failure)
            }
        }
    }

    LaunchedEffect(switchingSort) {
        if (!switchingSort) {
            showSwitchProgress = false
        } else {
            delay(180)
            showSwitchProgress = true
        }
    }

    LaunchedEffect(id) {
        detailError = null
        // 有列表带来的那条时，评论不必等正文回来再发：id/mid 路由里就有，两件事并行。
        val seeded = status
        if (seeded != null) scope.launch { loadComments(true, force = false) }
        try {
            status = App.repo.detail(id, force = false)
        } catch (t: Throwable) {
            detailError = t.message ?: "正文加载失败"
        }
        status?.let { s ->
            App.store.addHistory(
                HistoryItem(
                    s.id,
                    s.mid,
                    s.plainText.take(120),
                    s.user?.name.orEmpty(),
                    System.currentTimeMillis(),
                    // 列表里要按原样还原 emoji 与链接，纯文本留作兜底与裁剪后的短标题。
                    s.textHtml.take(1000),
                )
            )
        }
        // 没有列表数据可顶时才等正文：那条路的 id 可能还是 Base62，评论要先拿到数字 id。
        if (seeded == null) loadComments(true, force = false)
    }

    LaunchedEffect(id, mid) {
        App.detailRefreshEvents.collect { targetId ->
            if (targetId == id || (mid.isNotBlank() && targetId == mid)) {
                runCatching { App.repo.detail(id, force = true) }.getOrNull()?.let { status = it }
            }
        }
    }

    LaunchedEffect(listState, comments.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { last ->
                if (!switchingSort && last >= comments.size + 2 &&
                    comments.isNotEmpty() && !commentsEnd
                ) {
                    loadComments(false, force = false)
                }
            }
    }

    val scheme = COUITheme.colorScheme
    // 详情页去掉外框时，主贴、评论与评论头一起铺满整行，左右与顶栏按钮对齐。
    val keepCardFrame = App.settings.detailCardFrame
    // The bar floats over the list the same way it does on the feed: the first card starts below it
    // and everything scrolled past runs underneath the glass.
    val topBarHeight = pinnedHeight(TopAppBarDefaults.SmallTopAppBarCenterHeight)
    val bottomBarHeight = pinnedRowsHeight(
        listOf(
            BottomFloatingToolbarDefaults.MinHeight,
            BottomFloatingNavigationBarDefaults.navigationBarPadding(),
        ),
    )
    // Rows dim only when the wait is long enough to show the header progress line; a cached or
    // quick response swaps the list with no visible flash at all.
    val commentsAlpha by animateFloatAsState(
        targetValue = if (showSwitchProgress) 0.62f else 1f,
        animationSpec = tween(durationMillis = 160),
        label = "commentsAlpha",
    )

    Box(Modifier.fillMaxSize().background(pageContainerColor())) {
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdropIf(blurEffectsEnabled(), backdrop)
                .background(pageContainerColor())
        ) {
            CompositionLocalProvider(LocalBackdrop provides if (blurEffectsEnabled()) backdrop else null) {
                LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().scrollEndHaptic(),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    top = topBarHeight + 8.dp,
                    end = 12.dp,
                    bottom = bottomBarHeight + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when {
                    status != null -> item(key = "status") {
                        StatusCard(
                            status = status!!,
                            onOpen = {},
                            onDeleted = { nav.pop() },
                            onUser = { nav.push(Route.Profile(it.id)) },
                            onImage = { pics, i -> nav.push(Route.Images(pics, i)) },
                            onVideo = { nav.push(Route.Video(it.url, it.title)) },
                            onLike = {
                                val s = status ?: return@StatusCard
                                scope.launch {
                                    if (!App.confirmLike(s.liked)) return@launch
                                    runCatching { App.repo.like(s.id, s.liked) }.onSuccess {
                                        status = s.copy(
                                            liked = !s.liked,
                                            attitudes = (s.attitudes + if (s.liked) -1 else 1).coerceAtLeast(0),
                                        )
                                    }.onFailure { App.toast(it.message ?: "操作失败") }
                                }
                            },
                            showActions = false,
                            actionsEnabled = !readOnly,
                            onOpenQuoted = { quoted, quotedOrigin ->
                                quotedOrigin.attachToRow(listState, "status", topBarBounds)
                                nav.push(Route.Detail(quoted.id, quoted.mid, readOnly), quotedOrigin)
                            },
                            keepFrame = keepCardFrame,
                        )
                    }

                    detailError != null -> item {
                        ErrorState(detailError!!) {
                            scope.launch {
                                detailError = null
                                status = runCatching { App.repo.detail(id, force = true) }.getOrNull()
                            }
                        }
                    }

                    else -> item { LoadingState() }
                }

                if ((status?.editCount ?: 0) > 0) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("detail_edit_history"),
                            insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                            onClick = { nav.push(Route.EditHistory(effectiveMid())) },
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    COUIIcons.Edit,
                                    null,
                                    Modifier.size(16.dp),
                                    tint = scheme.primary,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "已编辑 ${status!!.editCount} 次",
                                    fontSize = 13.sp,
                                    color = scheme.onSurface,
                                )
                                Text(
                                    " · 查看编辑记录",
                                    fontSize = 13.sp,
                                    color = scheme.primary,
                                )
                            }
                        }
                    }
                }

                item {
                    Box(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = if (keepCardFrame) 16.dp else FlatCommentInset,
                                    end = if (keepCardFrame) 16.dp else FlatCommentInset,
                                    top = 6.dp,
                                    bottom = 2.dp,
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (sort == CommentSort.Hot) "热门评论" else "最新评论",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = scheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            // COUI's ChipTab is a 32dp, 14sp control that dwarfs a 13sp section label,
                            // so the two orders share one compact track instead.
                            SortSwitch(
                                selected = sort,
                                onSelect = ::switchSort,
                            )
                        }
                        // A hairline progress bar sits in the header's bottom padding, so a slow
                        // order switch reads as "refreshing in place" instead of a spinner flash.
                        SortProgressLine(
                            visible = showSwitchProgress,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                // Drop into the list's 8dp item gap so the line is not glued to
                                // the sort control's bottom edge.
                                .offset(y = 2.dp)
                                .padding(horizontal = if (keepCardFrame) 16.dp else FlatCommentInset)
                                .fillMaxWidth()
                                .height(2.dp),
                        )
                    }
                }

                itemsIndexed(comments, key = { index, comment -> "${comment.id}#$index" }) { index, comment ->
                    CommentCard(
                        comment = comment,
                        statusId = id,
                        mid = effectiveMid(),
                        modifier = Modifier.graphicsLayer { alpha = commentsAlpha },
                        likeTag = Modifier.testTag("comment_like_$index"),
                        replyTag = Modifier.testTag("comment_reply_$index"),
                        readOnly = readOnly,
                        keepFrame = keepCardFrame,
                        onOpenDetail = { activeCommentDetail = comment },
                        onImage = { pics, index -> nav.push(Route.Images(pics, index)) },
                        onRepost = { repostComment = it },
                        onReport = { nav.push(Route.Web(commentReportUrl(effectiveMid(), it.id), "投诉")) },
                    ) {
                        val at = comments.indexOfFirst { it.id == comment.id }
                        if (at < 0) return@CommentCard
                        val current = comments[at]
                        scope.launch {
                            // 先问再改：确认框弹着的这段时间列表可能刷新过，回来重新找一遍位置。
                            if (!App.confirmLike(current.liked)) return@launch
                            val now = comments.indexOfFirst { it.id == current.id }
                            if (now < 0) return@launch
                            comments[now] = current.copy(
                                liked = !current.liked,
                                likes = (current.likes + if (current.liked) -1 else 1).coerceAtLeast(0),
                            )
                            // Comment likes have their own endpoints; api/attitudes/* is statuses only.
                            runCatching { App.repo.likeComment(current.id, current.liked) }
                                .onFailure {
                                    val back = comments.indexOfFirst { it.id == current.id }
                                    if (back >= 0) comments[back] = current
                                    App.toast(it.message ?: "操作失败")
                                }
                        }
                    }
                }

                item {
                    when {
                        // An order switch keeps the old rows visible and reports progress in the
                        // header, so it must not also drop a spinner under the list.
                        switchingSort -> Unit
                        loadingComments -> LoadingState()
                        commentError != null -> ErrorState(commentError!!) {
                            scope.launch { loadComments(true, force = true) }
                        }
                        commentsEnd -> Text(
                            "没有更多评论",
                            fontSize = 12.sp,
                            color = scheme.onSurfaceSecondary,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        )
                    }
                }
            }
            }
        }

        CompositionLocalProvider(
            LocalBackdrop provides if (blurEffectsEnabled()) backdrop else null,
            // 内容铺到状态栏下面，顶栏玻璃才有东西可采样（COUI 17 规范要求 edgeToEdge）。
            LocalEdgeToEdge provides true,
        ) {
            PinnedBarDensity {
                // 玻璃比顶栏往下多画 24dp 做渐隐，而正文卡片只在顶栏下留 8dp：常亮的话静止时卡片顶端
                // 就被糊住。改成 COUI 原生的随滚动出现 —— 在顶部时透明，内容滚进顶栏底下 30dp 内淡入；
                // 模糊关掉时是同一条淡入曲线，只是淡进来的是实色而不是虚化。
                val density = LocalDensity.current
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
                        title = "微博正文",
                        modifier = Modifier.reportTopBarBottom(topBarBounds),
                        color = Color.Transparent,
                        showDivider = false,
                        gradientGlass = false,
                        navigationIcon = { BackIconButton { nav.pop() } },
                        actions = {
                            HighlightedIconButton(
                                onClick = {
                                    scope.launch {
                                        status = runCatching { App.repo.detail(id, force = true) }.getOrNull()
                                        loadComments(true, force = true)
                                    }
                                },
                                modifier = Modifier.couiTopAppBarActionItem(),
                            ) {
                                Icon(COUIIcons.Refresh, "刷新", Modifier.size(24.dp))
                            }
                        },
                    )
                }
            }
            PinnedBarDensity {
                val current = status
                val currentFavorited = current?.let { App.repo.isFavorited(it) } == true
                DetailFloatingToolbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = BottomFloatingNavigationBarDefaults.navigationBarPadding()),
                ) {
                    DetailFloatingToolbarItem(
                        icon = CoboIcons.Repost,
                        count = fmtCount(current?.reposts ?: 0),
                        contentDescription = "转发",
                        onClick = { repostDialog = true },
                        modifier = Modifier.testTag("detail_repost"),
                        enabled = !readOnly,
                    )
                    DetailFloatingToolbarItem(
                        icon = CoboIcons.Comment,
                        count = fmtCount(current?.comments ?: 0),
                        contentDescription = "评论",
                        onClick = { commentDialog = true },
                        modifier = Modifier.testTag("detail_comment"),
                        enabled = !readOnly,
                    )
                    DetailFloatingToolbarItem(
                        icon = if (currentFavorited) CoboIcons.StarFill else CoboIcons.Star,
                        count = "",
                        contentDescription = if (currentFavorited) "已收藏" else "收藏",
                        onClick = {
                            val s = status ?: return@DetailFloatingToolbarItem
                            val next = App.repo.toggleLocalFavorite(s)
                            status = s.copy(favorited = next)
                            App.toast(if (next) "已收藏" else "已取消收藏")
                        },
                        modifier = Modifier.testTag("detail_favorite"),
                        active = currentFavorited,
                        enabled = current != null,
                    )
                    DetailFloatingToolbarItem(
                        icon = if (current?.liked == true) CoboIcons.HeartFill else CoboIcons.Heart,
                        count = fmtCount(current?.attitudes ?: 0),
                        contentDescription = if (current?.liked == true) "已赞" else "赞",
                        onClick = {
                            val s = status ?: return@DetailFloatingToolbarItem
                            scope.launch {
                                if (!App.confirmLike(s.liked)) return@launch
                                runCatching { App.repo.like(s.id, s.liked) }.onSuccess {
                                    status = s.copy(
                                        liked = !s.liked,
                                        attitudes = (s.attitudes + if (s.liked) -1 else 1).coerceAtLeast(0),
                                    )
                                }.onFailure { App.toast(it.message ?: "操作失败") }
                            }
                        },
                        modifier = Modifier.testTag("detail_like"),
                        active = current?.liked == true,
                        enabled = !readOnly,
                    )
                }
            }
        }

        CommentDetailOverlay(
            root = activeCommentDetail,
            statusId = effectiveId(),
            mid = effectiveMid(),
            readOnly = readOnly,
            keepFrame = keepCardFrame,
            onImage = { pics, idx -> nav.push(Route.Images(pics, idx)) },
            onRepost = { repostComment = it },
            onReport = { nav.push(Route.Web(commentReportUrl(effectiveMid(), it.id), "投诉")) },
            onSyncComment = { updated ->
                val idx = comments.indexOfFirst { it.id == updated.id }
                if (idx >= 0) comments[idx] = updated
                if (activeCommentDetail?.id == updated.id) activeCommentDetail = updated
            },
            onClose = { activeCommentDetail = null },
        )
    }

    if (repostDialog || repostComment != null || commentDialog) {
        CompositionLocalProvider(LocalBackdrop provides if (blurEffectsEnabled()) backdrop else null) {
            if (repostDialog || repostComment != null) {
                val quoting = repostComment
                ComposerBottomSheet(
                    title = "转发",
                    hint = "说点什么",
                    submitText = "转发",
                    allowEmpty = true,
                    // 评论的「转发」= 转发这条微博并把该评论引用在正文里，跟官方客户端一致。
                    initialText = quoting?.let {
                        "//@${it.user?.name.orEmpty()}：${stripHtml(it.textHtml)}"
                    }.orEmpty(),
                    showVisibility = true,
                    onDismiss = {
                        repostDialog = false
                        repostComment = null
                    },
                    onSubmit = { text, visibility ->
                        try {
                            App.repo.repost(id, effectiveMid(), text, visibility.code)
                            App.toast("已转发")
                            true
                        } catch (e: CancellationException) {
                            throw e
                        } catch (t: Throwable) {
                            App.toast(t.message ?: "转发失败")
                            false
                        }
                    },
                )
            }
            if (commentDialog) {
                ComposerBottomSheet(
                    title = "评论",
                    hint = "写评论",
                    submitText = "发布",
                    onDismiss = { commentDialog = false },
                    onSubmit = { text, _ ->
                        try {
                            App.repo.comment(id, effectiveMid(), text)
                            App.toast("评论成功")
                            loadComments(true, force = true)
                            true
                        } catch (e: CancellationException) {
                            throw e
                        } catch (t: Throwable) {
                            App.toast(t.message ?: "评论失败")
                            false
                        }
                    },
                )
            }
        }
    }
}

/** 去掉外框后补在列表 12dp 内边距上的横向留白，合计 16dp，与顶栏按钮外框对齐。 */
internal val FlatCommentInset = 4.dp

/** 网页版举报入口：`type=2` 是举报评论，`url` 指向它所在的那条微博。 */
private fun commentReportUrl(mid: String, cid: String): String {
    val target = URLEncoder.encode("/detail/$mid", "UTF-8")
    return "https://service.account.weibo.com/reportspam" +
        "?rid=$cid&from=10106&type=2&url=$target&bottomnav=1&wvr=5"
}

private const val PreviewReplies = 1

/**
 * 评论配图。放在正文下面、同一个文字列里，所以天然与文字左对齐，而不是与头像对齐。
 * 高度固定，单图按原比例收在 72–200dp；多图一律正方形，跟信息流的九宫格区分开。
 */
@Composable
internal fun CommentThumbnails(
    pics: List<Pic>,
    height: Dp,
    onImage: (List<Pic>, Int) -> Unit,
) {
    val scheme = COUITheme.colorScheme
    val single = pics.size == 1
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        pics.take(4).forEachIndexed { index, pic ->
            val aspect = if (pic.width > 0 && pic.height > 0) {
                pic.width.toFloat() / pic.height
            } else {
                null
            }
            val width = if (single) {
                (height * (aspect ?: 1.4f)).coerceIn(72.dp, 200.dp)
            } else {
                height
            }
            Box(
                modifier = Modifier
                    .size(width, height)
                    .clip(RoundedCornerShape(8.dp))
                    .background(scheme.surfaceContainerHigh)
                    .testTag("comment_pic_$index")
                    .clickable { onImage(pics, index) },
            ) {
                AsyncImage(
                    model = pic.thumb.ifBlank { pic.large },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** 评论 ID 后面那串粉丝标识（铁粉 / 钻粉…）：接口给小图时按比例铺开；没给小图但带粉丝气泡时用简洁胶囊兜底。 */
@Composable
internal fun CommentBadges(
    badges: List<CommentBadge>,
    height: Dp,
    fanTier: String = "",
    showFanLevel: Boolean = runCatching { App.settings.showFanLevel }.getOrDefault(true),
) {
    if (!showFanLevel) return
    if (badges.isEmpty() && fanTier.isNotBlank()) {
        Spacer(Modifier.width(4.dp))
        val (bg, fg) = when {
            fanTier.contains("钻") -> Color(0xFFEEE6FF) to Color(0xFF6B3FD9)
            fanTier.contains("金") -> Color(0xFFFFF1D6) to Color(0xFFB45309)
            fanTier.contains("挚爱") || fanTier.contains("真爱") -> Color(0xFFFFE6EE) to Color(0xFFC2185B)
            else -> Color(0xFFE0F0FF) to Color(0xFF1D6FD8)
        }
        Box(
            modifier = Modifier
                .height(height)
                .clip(com.cobo.app.ui.components.badgeShape(compact = true))
                .background(bg)
                .padding(horizontal = 4.dp)
                .testTag("comment_badge"),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = fanTier,
                fontSize = 8.sp,
                lineHeight = 8.sp,
                fontWeight = FontWeight.SemiBold,
                color = fg,
                maxLines = 1,
                softWrap = false,
            )
        }
        return
    }
    badges.forEach { badge ->
        Spacer(Modifier.width(3.dp))
        // AsyncImage 在 Row 里仅限高、不设宽时首帧可能为 0 宽；按图集原比例（110x42≈2.62）预留并在成功回调校准。
        var ratio by remember(badge.pic) { mutableFloatStateOf(2.62f) }
        AsyncImage(
            model = badge.pic,
            contentDescription = "粉丝标识",
            contentScale = ContentScale.Fit,
            onSuccess = { state ->
                val size = state.painter.intrinsicSize
                if (size.width > 0f && size.height > 0f) {
                    ratio = (size.width / size.height).coerceIn(0.8f, 5.0f)
                }
            },
            modifier = Modifier
                .width(height * ratio)
                .height(height)
                .testTag("comment_badge"),
        )
    }
}

/** 评论原生粉丝小图已带皇冠与 VIP 等级数字时，不再重复叠一颗独立的 VIP/SVIP/VVIP 胶囊。 */
internal fun shouldShowCommentMemberBadge(
    membership: Membership,
    badges: List<CommentBadge>,
    showFanLevel: Boolean,
): Boolean {
    if (membership == Membership.None) return false
    if (showFanLevel && badges.any { !it.pic.contains("member_0_") }) return false
    return true
}

@Composable
internal fun CommentHeaderBadges(
    comment: Comment,
    height: Dp,
) {
    val showFan = runCatching { App.settings.showFanLevel }.getOrDefault(true)
    CommentBadges(comment.badges, height, fanTier = comment.fanTier, showFanLevel = showFan)
    val user = comment.user
    val membership = user?.membership ?: Membership.None
    if (shouldShowCommentMemberBadge(membership, comment.badges, showFan)) {
        Spacer(Modifier.width(4.dp))
        MemberBadge(membership, compact = true, rank = user?.mbrank ?: 0, height = height)
    }
}

/** 评论三点菜单的一项。只读流里「转发」「回复」照常列出，只是点不动，和评论上那两个胶囊一致。 */
private data class CommentMenuEntry(
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean,
    val action: () -> Unit,
)

/**
 * 评论 ID 那一行右端的三点菜单：复制、转发、投诉、回复，跟官方客户端长按评论给的四项一致。
 * 只读流（本地关注、纯本地模式）没有账号可写，「转发」「回复」变灰不可点，复制和投诉照常。
 */
@Composable
internal fun CommentMoreButton(
    comment: Comment,
    readOnly: Boolean,
    compact: Boolean = false,
    onRepost: () -> Unit,
    onReport: () -> Unit,
    onReply: () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    val context = LocalContext.current
    var expanded by remember(comment.id) { mutableStateOf(false) }

    fun copyText() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(
            ClipData.newPlainText("comment", stripHtml(comment.textHtml)),
        )
        App.toast("已复制")
    }

    val entries = listOf(
        CommentMenuEntry("复制", COUIIcons.Copy, true, ::copyText),
        CommentMenuEntry("转发", COUIIcons.Forward, !readOnly, onRepost),
        CommentMenuEntry("投诉", COUIIcons.Report, true, onReport),
        CommentMenuEntry("回复", COUIIcons.Reply, !readOnly, onReply),
    )
    val buttonSize = if (compact) 18.dp else 20.dp
    val iconSize = if (compact) 16.dp else 18.dp

    Box {
        Box(
            modifier = Modifier
                .size(buttonSize)
                .clip(RoundedCornerShape(6.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { expanded = true }
                .semantics { contentDescription = "更多" }
                .testTag(if (compact) "reply_more_${comment.id}" else "comment_more_${comment.id}"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = COUIIcons.More,
                contentDescription = null,
                tint = scheme.onSurfaceSecondary,
                modifier = Modifier.size(iconSize),
            )
        }
        OverlayListPopup(
            show = expanded,
            popupPositionProvider = ListPopupDefaults.DropdownPositionProvider,
            onDismissRequest = { expanded = false },
        ) {
            val dismiss = LocalDismissState.current
            ListPopupColumn {
                entries.forEachIndexed { index, entry ->
                    DropdownImpl(
                        item = DropdownItem(
                            text = entry.label,
                            enabled = entry.enabled,
                            icon = { iconModifier ->
                                Box(iconModifier, contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = entry.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = scheme.onSurfaceContainer,
                                    )
                                }
                            },
                        ),
                        optionSize = entries.size,
                        isSelected = false,
                        index = index,
                        groupKey = "comment_more_${comment.id}",
                        onSelectedIndexChange = {
                            dismiss?.invoke()
                            expanded = false
                            if (entry.enabled) entry.action()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CommentCard(    comment: Comment,
    statusId: String,
    mid: String,
    modifier: Modifier = Modifier,
    likeTag: Modifier = Modifier,
    replyTag: Modifier = Modifier,
    readOnly: Boolean = false,
    /** 是否保留评论卡片外框；详情页设为去掉外框时，评论同样铺满整行。 */
    keepFrame: Boolean = true,
    onOpenDetail: () -> Unit = {},
    onImage: (List<Pic>, Int) -> Unit,
    onRepost: (Comment) -> Unit,
    onReport: (Comment) -> Unit,
    onLike: () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    // 楼中楼、引用回复这些嵌套块的底色：在卡片面上用页面灰，卡片拆掉后要用卡片白，
    // 否则和外框一起消失，看不出还是一个独立的块。
    val nestedTone = if (keepFrame) pageContainerColor() else scheme.surfaceContainer
    val scope = rememberCoroutineScope()
    val replies = remember(comment.id) {
        mutableStateListOf<Comment>().apply { addAll(comment.replies) }
    }
    var replyTotal by remember(comment.id) {
        mutableStateOf(maxOf(comment.replyCount, comment.replies.size))
    }
    var expanded by remember(comment.id) { mutableStateOf(false) }
    var loadingReplies by remember(comment.id) { mutableStateOf(false) }
    var cursor by remember(comment.id) { mutableStateOf<String?>(null) }
    var cursorType by remember(comment.id) { mutableStateOf(0) }
    var repliesDone by remember(comment.id) { mutableStateOf(false) }
    // The comment or nested reply being answered; it owns the dialog so a finished reply can
    // reload this thread instead of the whole comment list.
    var replyTarget by remember(comment.id) { mutableStateOf<Comment?>(null) }

    fun loadMoreReplies() {
        if (loadingReplies || repliesDone) return
        loadingReplies = true
        scope.launch {
            runCatching { App.repo.commentReplies(comment.id, cursor, cursorType) }
                .onSuccess { page ->
                    val before = replies.size
                    val known = replies.mapTo(HashSet()) { it.id }
                    page.items.forEach { if (known.add(it.id)) replies.add(it) }
                    cursor = page.maxId
                    cursorType = page.maxIdType
                    // `max_id` 0 means the thread is exhausted; a page that adds nothing means the
                    // same, and stops a looping "load more" when the count and reality disagree.
                    if (page.items.isEmpty() || replies.size == before ||
                        page.maxId.isBlank() || page.maxId == "0"
                    ) {
                        repliesDone = true
                    }
                    if (page.nextPage > 0) replyTotal = maxOf(replyTotal, page.nextPage)
                }
                .onFailure { App.toast(it.message ?: "回复加载失败") }
            loadingReplies = false
        }
    }

    fun showNewReply(reply: Comment) {
        expanded = true
        replyTotal += 1
        val at = replies.indexOfFirst { it.id == reply.id }
        if (at >= 0) replies[at] = reply else replies.add(0, reply)
    }

    fun likeReply(reply: Comment) {
        val at = replies.indexOfFirst { it.id == reply.id }
        if (at < 0) return
        val current = replies[at]
        scope.launch {
            // 同上：先问再改，确认期间列表可能刷新，回来重新找一遍位置。
            if (!App.confirmLike(current.liked)) return@launch
            val now = replies.indexOfFirst { it.id == current.id }
            if (now < 0) return@launch
            replies[now] = current.copy(
                liked = !current.liked,
                likes = (current.likes + if (current.liked) -1 else 1).coerceAtLeast(0),
            )
            runCatching { App.repo.likeComment(current.id, current.liked) }
                .onFailure {
                    val back = replies.indexOfFirst { it.id == current.id }
                    if (back >= 0) replies[back] = current
                    App.toast(it.message ?: "操作失败")
                }
        }
    }

    // 评论的时间与回复/点赞：有外框时压在卡片底部；拆掉外框时贴到正文下面，
    // 免得被中间的楼中楼块隔开、看着像不属于这条评论。
    val actions: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatWeiboTime(comment.createdAt),
                fontSize = 11.sp,
                color = scheme.onSurfaceSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            CommentActionButtons(
                likes = comment.likes,
                liked = comment.liked,
                enabled = !readOnly,
                compact = false,
                replyModifier = replyTag,
                likeModifier = likeTag,
                onReply = { replyTarget = comment },
                onLike = onLike,
            )
        }
    }

    val body: @Composable () -> Unit = {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOpenDetail,
                )
                .padding(
                    horizontal = if (keepFrame) 14.dp else FlatCommentInset,
                    vertical = 12.dp,
                )
        ) {
            Avatar(
                url = comment.user?.avatar,
                size = 34.dp,
                verifiedKind = comment.user?.verifiedKind ?: VerifiedKind.None,
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
                            comment.user?.name.orEmpty(),
                            fontSize = 15.sp,
                            // 跟楼中楼的 ID 同一档灰：评论正文才是主内容，ID 往后退一格。
                            color = scheme.onSurfaceSecondary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        CommentHeaderBadges(comment, 14.dp)
                    }
                    CommentMoreButton(
                        comment = comment,
                        readOnly = readOnly,
                        onRepost = { onRepost(comment) },
                        onReport = { onReport(comment) },
                        onReply = { replyTarget = comment },
                    )
                }
                Spacer(Modifier.height(3.dp))
                WeiboText(
                    html = comment.textHtml,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
                if (comment.pics.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    CommentThumbnails(comment.pics, 96.dp, onImage)
                }
                comment.reply?.let { reply ->
                    Spacer(Modifier.height(6.dp))
                    // The quoted reply is the page tone inside a card, so the app keeps exactly one
                    // background colour and one card colour instead of inventing a third grey.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(nestedTone)
                            .padding(8.dp),
                    ) {
                        Text(
                            "@${reply.user?.name.orEmpty()}：",
                            fontSize = 12.sp,
                            color = scheme.primary,
                        )
                        WeiboText(
                            html = reply.textHtml,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        )
                    }
                }
                if (!keepFrame) {
                    Spacer(Modifier.height(4.dp))
                    actions()
                }
                val total = maxOf(replyTotal, replies.size)
                if (total > 0) {
                    Spacer(Modifier.height(6.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(nestedTone)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        val visible = if (expanded) replies else replies.take(PreviewReplies)
                        visible.forEach { reply ->
                            ReplyRow(
                                reply = reply,
                                onLike = { likeReply(reply) },
                                onReply = { replyTarget = reply },
                                enabled = !readOnly,
                                keepFrame = keepFrame,
                                showActions = expanded,
                                onImage = onImage,
                                onRepost = onRepost,
                                onReport = onReport,
                            )
                        }
                        when {
                            loadingReplies -> Text(
                                "加载中…",
                                fontSize = 12.sp,
                                color = scheme.onSurfaceSecondary,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                            expanded && !repliesDone && replies.size < total -> Text(
                                "展开更多回复",
                                fontSize = 12.sp,
                                color = scheme.primary,
                                modifier = Modifier
                                    .clickable { loadMoreReplies() }
                                    .padding(vertical = 4.dp),
                            )
                            expanded -> Text(
                                "收起",
                                fontSize = 12.sp,
                                color = scheme.onSurfaceSecondary,
                                modifier = Modifier
                                    .clickable { expanded = false }
                                    .padding(vertical = 4.dp),
                            )
                            total > visible.size -> Text(
                                "共 $total 条回复",
                                fontSize = 12.sp,
                                color = scheme.primary,
                                modifier = Modifier
                                    .testTag("comment_replies")
                                    .clickable(onClick = onOpenDetail)
                                    .padding(vertical = 4.dp),
                            )
                        }
                    }
                }
                if (keepFrame) {
                    Spacer(Modifier.height(4.dp))
                    actions()
                }
            }
        }
    }
    if (keepFrame) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = CardDefaults.GroupCornerRadius,
            weight = CardDefaults.GroupWeight,
        ) { body() }
    } else {
        body()
    }

    replyTarget?.let { target ->
        ComposerBottomSheet(
            title = "回复 @${target.user?.name.orEmpty()}",
            hint = "写回复",
            submitText = "回复",
            onDismiss = { replyTarget = null },
            onSubmit = { text, _ ->
                try {
                    val created = App.repo.replyComment(statusId, mid, target.id, text)
                    App.toast("回复成功")
                    showNewReply(created)
                    true
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    App.toast(t.message ?: "回复失败")
                    false
                }
            },
        )
    }
}

@Composable
internal fun ReplyRow(
    reply: Comment,
    onLike: () -> Unit,
    onReply: () -> Unit,
    enabled: Boolean = true,
    keepFrame: Boolean = true,
    /** 楼中楼收起、只做预览时不渲染「回复」「点赞」两个按钮。 */
    showActions: Boolean = true,
    onImage: (List<Pic>, Int) -> Unit = { _, _ -> },
    onRepost: (Comment) -> Unit = {},
    onReport: (Comment) -> Unit = {},
) {
    val scheme = COUITheme.colorScheme
    Row(Modifier.padding(vertical = 3.dp)) {
        Avatar(
            url = reply.user?.avatar,
            size = 22.dp,
            verifiedKind = reply.user?.verifiedKind ?: VerifiedKind.None,
        )
        Spacer(Modifier.width(8.dp))
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
                        reply.user?.name.orEmpty(),
                        fontSize = 14.sp,
                        color = scheme.onSurfaceSecondary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    CommentHeaderBadges(reply, 13.dp)
                }
                if (showActions) {
                    CommentMoreButton(
                        comment = reply,
                        readOnly = !enabled,
                        compact = true,
                        onRepost = { onRepost(reply) },
                        onReport = { onReport(reply) },
                        onReply = onReply,
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            WeiboText(
                html = reply.textHtml,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            if (reply.pics.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                CommentThumbnails(reply.pics, 64.dp, onImage)
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatWeiboTime(reply.createdAt),
                    fontSize = 11.sp,
                    color = scheme.onSurfaceSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (showActions) {
                    CommentActionButtons(
                        likes = reply.likes,
                        liked = reply.liked,
                        enabled = enabled,
                        compact = true,
                        replyModifier = Modifier.testTag("reply_reply"),
                        likeModifier = Modifier.testTag("reply_like"),
                        onReply = onReply,
                        onLike = onLike,
                    )
                }
            }
        }
    }
}

/** 评论 / 楼中楼右侧的「回复」与「点赞」动作组：统一纯图标无底框，点赞数为 0 时仅保留心形图标。 */
@Composable
internal fun CommentActionButtons(
    likes: Int,
    liked: Boolean,
    enabled: Boolean,
    compact: Boolean = false,
    replyModifier: Modifier = Modifier,
    likeModifier: Modifier = Modifier,
    onReply: () -> Unit,
    onLike: () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    val cornerRadius = if (compact) 10.dp else 11.dp
    val iconSize = if (compact) 12.dp else 14.dp
    val fontSize = if (compact) 11.sp else 12.sp
    val iconGap = if (compact) 3.dp else 4.dp
    val verticalPad = if (compact) 2.dp else 4.dp
    val likeTint = if (liked) scheme.primary else scheme.onSurfaceSecondary

    Box(
        modifier = replyModifier
            .clip(RoundedCornerShape(cornerRadius))
            .then(
                if (enabled) Modifier.clickable(onClick = onReply)
                else Modifier.alpha(0.38f),
            )
            .padding(horizontal = 4.dp, vertical = verticalPad),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = CoboIcons.Comment,
            contentDescription = "回复",
            modifier = Modifier.size(iconSize),
            tint = scheme.onSurfaceSecondary,
        )
    }
    Spacer(Modifier.width(if (compact) 4.dp else 6.dp))
    Row(
        modifier = likeModifier
            .clip(RoundedCornerShape(cornerRadius))
            .then(
                if (enabled) Modifier.clickable(onClick = onLike)
                else Modifier.alpha(0.38f),
            )
            .padding(horizontal = 4.dp, vertical = verticalPad),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (liked) CoboIcons.HeartFill else CoboIcons.Heart,
            contentDescription = if (liked) "取消点赞" else "点赞",
            modifier = Modifier.size(iconSize),
            tint = likeTint,
        )
        if (likes > 0) {
            Spacer(Modifier.width(iconGap))
            Text(
                text = fmtCount(likes),
                fontSize = fontSize,
                fontWeight = FontWeight.Medium,
                color = likeTint,
            )
        }
    }
}

private val SortEntries = listOf("热度" to CommentSort.Hot, "时间" to CommentSort.Time)
private val SortSpring = spring<Float>(dampingRatio = 0.82f, stiffness = 420f)
private val SortPressSpring = spring<Float>(dampingRatio = 1f, stiffness = 438.65f)

@Composable
private fun SortSwitch(
    selected: CommentSort,
    onSelect: (CommentSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    CompactSortSwitch(
        entries = SortEntries,
        selected = selected,
        onSelect = onSelect,
        testTagOf = { if (it == CommentSort.Hot) "comment_sort_hot" else "comment_sort_time" },
        modifier = modifier,
    )
}

/** 微博正文 / 楼中楼详情共用的紧凑分段排序胶囊：点击或横向拖动切换，带弹性滑块与文字渐变。 */
@Composable
internal fun <T> CompactSortSwitch(
    entries: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    testTagOf: ((T) -> String)? = null,
) {
    if (entries.isEmpty()) return
    val scheme = COUITheme.colorScheme
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val labelStyle = remember { TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium) }
    // Both labels are measured at the heavier weight so the pill never clips a label as the
    // selected side swaps in, and so the control grows with the user's font scale.
    val labelSize = remember(entries, measurer, labelStyle) {
        val sizes = entries.map { measurer.measure(it.first, labelStyle).size }
        sizes.maxOf { it.width } to sizes.maxOf { it.height }
    }
    val segmentWidth = with(density) { labelSize.first.toDp() } + 20.dp
    val segmentHeight = with(density) { labelSize.second.toDp() } + 8.dp
    val segmentWidthPx = with(density) { segmentWidth.toPx() }
    val selectedIndex = entries.indexOfFirst { it.second == selected }.coerceAtLeast(0)
    val indicator = remember { Animatable(selectedIndex.toFloat()) }
    var dragValue by remember { mutableStateOf<Float?>(null) }
    val scope = rememberCoroutineScope()
    val interactions = remember(entries.size) { List(entries.size) { MutableInteractionSource() } }
    val pressed = interactions.map { it.collectIsPressedAsState() }
    val dark = scheme.background.luminance() < 0.5f

    LaunchedEffect(selectedIndex) {
        if (dragValue == null) indicator.animateTo(selectedIndex.toFloat(), SortSpring)
    }

    val value = dragValue ?: indicator.value

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(scheme.onSurface.copy(alpha = if (dark) 0.16f else 0.11f))
            .selectableGroup()
            .padding(2.dp)
            .pointerInput(entries, segmentWidthPx, selectedIndex) {
                detectHorizontalDragGestures(
                    onDragStart = { dragValue = indicator.value },
                    onHorizontalDrag = { change, delta ->
                        change.consume()
                        dragValue = ((dragValue ?: indicator.value) + delta / segmentWidthPx)
                            .coerceIn(0f, (entries.size - 1).toFloat())
                    },
                    onDragEnd = {
                        val start = dragValue ?: indicator.value
                        val target = start.roundToInt().coerceIn(0, entries.size - 1)
                        scope.launch {
                            indicator.snapTo(start)
                            dragValue = null
                            if (target != selectedIndex) onSelect(entries[target].second)
                            else indicator.animateTo(target.toFloat(), SortSpring)
                        }
                    },
                    onDragCancel = {
                        val start = dragValue ?: indicator.value
                        scope.launch {
                            indicator.snapTo(start)
                            dragValue = null
                            indicator.animateTo(selectedIndex.toFloat(), SortSpring)
                        }
                    },
                )
            },
    ) {
        Box(
            Modifier
                .offset(x = segmentWidth * value)
                .width(segmentWidth)
                .height(segmentHeight)
                .clip(RoundedCornerShape(7.dp))
                .background(if (dark) scheme.surfaceContainerHigh else scheme.surfaceContainer),
        )
        Row(Modifier.height(segmentHeight)) {
            entries.forEachIndexed { index, (label, entrySort) ->
                val proximity = (1f - abs(value - index)).coerceIn(0f, 1f)
                val labelScale by animateFloatAsState(
                    targetValue = if (pressed[index].value) 0.94f else 1f,
                    animationSpec = SortPressSpring,
                    label = "sortLabelScale",
                )
                Box(
                    modifier = Modifier
                        .width(segmentWidth)
                        .fillMaxHeight()
                        .graphicsLayer {
                            scaleX = labelScale
                            scaleY = labelScale
                        }
                        .selectable(
                            selected = selected == entrySort,
                            interactionSource = interactions[index],
                            indication = null,
                            role = Role.RadioButton,
                            onClick = { onSelect(entrySort) },
                        )
                        .let { mod ->
                            val tag = testTagOf?.invoke(entrySort)
                            if (tag != null) mod.testTag(tag) else mod
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = if (proximity > 0.5f) FontWeight.Medium else FontWeight.Normal,
                        color = lerp(scheme.onSurfaceSecondary, scheme.onSurface, proximity),
                    )
                }
            }
        }
    }
}

@Composable
private fun SortProgressLine(visible: Boolean, modifier: Modifier = Modifier) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 160),
        label = "sortProgressAlpha",
    )
    if (alpha <= 0.01f) return
    val transition = rememberInfiniteTransition(label = "sortProgress")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sortProgressPhase",
    )
    val scheme = COUITheme.colorScheme
    val trackAlpha = if (scheme.background.luminance() < 0.5f) 0.10f else 0.045f
    Canvas(modifier.graphicsLayer { this.alpha = alpha }) {
        val radius = CornerRadius(size.height / 2f)
        drawRoundRect(
            color = scheme.onSurface.copy(alpha = trackAlpha),
            cornerRadius = radius,
        )
        val bar = size.width * 0.32f
        drawRoundRect(
            color = scheme.primary.copy(alpha = 0.48f),
            topLeft = Offset((size.width + bar) * phase - bar, 0f),
            size = Size(bar, size.height),
            cornerRadius = radius,
        )
    }
}

/**
 * 详情页底部四按钮悬浮胶囊：直接复用 [MetaBallSurface] 渲染与顶栏 [HighlightedIconButton]
 * 同一套透明模糊材质、材质描边混色（`blendEdge`）以及深色焦散 / 浅色软投影。
 */
@Composable
private fun DetailFloatingToolbar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val density = LocalDensity.current
    val scheme = COUITheme.colorScheme
    val isDark = scheme.background.luminance() < 0.5f
    val backdrop = LocalBackdrop.current
    val barHeight = BottomFloatingToolbarDefaults.MenuViewHeight
    val cornerRadius = BottomFloatingToolbarDefaults.capsuleCornerRadius(height = barHeight)
    val squircleEnabled = isSquircleEnabled()
    val shape = remember(cornerRadius, squircleEnabled) {
        CapsuleOutlineShape(cornerRadius = cornerRadius, squircleEnabled = squircleEnabled)
    }
    val touchState = rememberCOUITouchMotionState()
    var capsuleWidthPx by remember { mutableIntStateOf(0) }
    val capsuleWidth = with(density) { capsuleWidthPx.toDp() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = BottomFloatingToolbarDefaults.MinHeight),
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.popupBottomBarrier()) {
            if (!isDark && capsuleWidthPx > 0) {
                Box(
                    modifier = Modifier
                        .size(capsuleWidth, barHeight)
                        .graphicsLayer {
                            translationX = touchState.translation.x
                            translationY = touchState.translation.y
                            scaleX = touchState.scaleX
                            scaleY = touchState.scaleY
                        }
                        .dropShadow(shape = shape, shadow = BottomFloatingToolbarDefaults.Shadow),
                )
            }
            MetaBallSurface(
                particles = if (capsuleWidthPx > 0) {
                    listOf(
                        MetaBallParticle(
                            centerX = capsuleWidth / 2 + with(density) { touchState.translation.x.toDp() },
                            centerY = barHeight / 2 + with(density) { touchState.translation.y.toDp() },
                            width = capsuleWidth,
                            height = barHeight,
                            scaleX = touchState.scaleX,
                            scaleY = touchState.scaleY,
                            cornerRadius = cornerRadius,
                        ),
                    )
                } else {
                    emptyList()
                },
                modifier = Modifier.matchParentSize(),
                color = HighlightedIconButtonDefaults.fillCenter(isDark)
                    .copy(alpha = HighlightedIconButtonDefaults.FillAlpha),
                backdrop = backdrop,
                blurColors = BottomFloatingNavigationBarDefaults.blurColors(isDark),
                blurRadius = BottomFloatingNavigationBarDefaults.blurRadius(),
                rimColor = Color.White.copy(alpha = HighlightedIconButtonDefaults.stroke(isDark).edgeAlpha),
                rimBottomScale = 1f,
                bottomGlowColor = if (isDark) HighlightedIconButtonDefaults.DarkHaloColor else null,
                continuousCurve = true,
            )
            Row(
                modifier = Modifier
                    .height(barHeight)
                    .onSizeChanged { capsuleWidthPx = it.width }
                    .couiTouchMotion(
                        shape = shape,
                        lightParams = SpotLightDefaults.circleParams1(isDark),
                        motionState = touchState,
                    )
                    .padding(horizontal = BottomFloatingToolbarDefaults.MenuViewItemBgInsetHorizontal),
                horizontalArrangement = Arrangement.spacedBy(BottomFloatingToolbarDefaults.ItemViewSpace),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}

@Composable
private fun DetailFloatingToolbarItem(
    icon: ImageVector,
    count: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
) {
    val scheme = COUITheme.colorScheme
    val tint = when {
        !enabled -> scheme.onSurfaceVariantActions
        active -> scheme.primary
        else -> scheme.labelPrimaryVariant
    }
    Row(
        modifier = modifier
            .height(BottomFloatingToolbarDefaults.ItemHeight)
            .widthIn(min = BottomFloatingToolbarDefaults.ItemWidth)
            .then(
                if (enabled) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                }
            )
            .semantics { this.contentDescription = contentDescription }
            .padding(horizontal = if (count.isNotEmpty()) 8.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = tint,
        )
        if (count.isNotEmpty()) {
            Text(
                text = count,
                color = tint,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}
