package com.cobo.app.screen

import com.cobo.app.platform.copyToClipboard
import com.cobo.app.platform.currentTimeMillis
import com.cobo.app.platform.ioDispatcher
import com.cobo.app.platform.urlEncode

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.DragInteraction
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.cobo.app.App
import com.cobo.app.data.Comment
import com.cobo.app.data.CommentBadge
import com.cobo.app.data.CommentFilterState
import com.cobo.app.data.CommentSort
import com.cobo.app.data.ContentFilters
import com.cobo.app.data.DetailInitialSection
import com.cobo.app.data.DetailSectionTab
import com.cobo.app.data.FilterSurface
import com.cobo.app.data.HistoryImageArchiver
import com.cobo.app.data.HistoryItem
import com.cobo.app.data.Membership
import com.cobo.app.data.MuteUserMode
import com.cobo.app.data.Pic
import com.cobo.app.data.ReadLater
import com.cobo.app.data.Status
import com.cobo.app.data.StatusMemory
import com.cobo.app.data.User
import com.cobo.app.data.UserRemarks
import com.cobo.app.data.VerifiedKind
import com.cobo.app.data.commentFilterEmptyMessage
import com.cobo.app.data.commentFilterTriggerLabel
import com.cobo.app.data.computeDetailSectionFillerHeightPx
import com.cobo.app.data.detailSectionHeaderIndex
import com.cobo.app.data.filterComments
import com.cobo.app.data.formatWeiboTime
import com.cobo.app.data.isDeletedStatusThrowable
import com.cobo.app.data.resolveHistoryFallbackStatus
import com.cobo.app.data.sanitizeStatusForHistory
import com.cobo.app.data.shouldFilterComment
import com.cobo.app.data.shouldFilterStatus
import com.cobo.app.data.stripHtml
import com.cobo.app.ui.NavState
import com.cobo.app.ui.AppBarSurface
import com.cobo.app.ui.LocalImageOrigins
import com.cobo.app.ui.LocalRouteTransition
import com.cobo.app.ui.RouteTransition
import com.cobo.app.ui.blurEffectsEnabled
import com.cobo.app.ui.extractResolvablePhotoLinksFromHtml
import com.cobo.app.ui.imageOrigin
import com.cobo.app.ui.FloatingBarNonInteractiveThreshold
import com.cobo.app.ui.layerBackdropIf
import com.cobo.app.ui.rememberFloatingBarScrollController
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
import com.cobo.app.ui.components.LongImageBadge
import com.cobo.app.ui.components.MemberBadge
import com.cobo.app.ui.components.StatusCard
import com.cobo.app.ui.components.WeiboText
import com.cobo.app.ui.components.fmtCount
import com.cobo.app.ui.isLongImageAspect
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
import io.github.suqi8.coui.kmp.basic.NavigationPopupMenuDefaults
import io.github.suqi8.coui.kmp.basic.PopupPositionProvider
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.SpotLightDefaults
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
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
import io.github.suqi8.coui.kmp.icon.extended.Close
import io.github.suqi8.coui.kmp.icon.extended.Copy
import io.github.suqi8.coui.kmp.icon.extended.Delete
import io.github.suqi8.coui.kmp.icon.extended.Edit
import io.github.suqi8.coui.kmp.icon.extended.Forward
import io.github.suqi8.coui.kmp.icon.extended.More
import io.github.suqi8.coui.kmp.icon.extended.Refresh
import io.github.suqi8.coui.kmp.icon.extended.Reply
import io.github.suqi8.coui.kmp.icon.extended.Report
import io.github.suqi8.coui.kmp.overlay.OverlayListPopup
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.squircle.isSquircleEnabled
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.LocalDismissState
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DetailScreen(
    nav: NavState,
    id: String,
    mid: String,
    readOnly: Boolean = false,
    initialSection: DetailInitialSection = DetailInitialSection.Body,
    fromHistory: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    val savedHistoryItem = remember(id, mid) { App.store.findHistory(id, mid) }
    val archivedHistoryStatus = remember(savedHistoryItem) {
        savedHistoryItem?.let { resolveHistoryFallbackStatus(it, App.store.historyImagesDir()) }
    }
    var isDeletedArchive by remember(id, mid) {
        mutableStateOf(
            savedHistoryItem?.deleted == true ||
                App.repo.isDeleted(id) ||
                (mid.isNotBlank() && App.repo.isDeleted(mid)),
        )
    }
    var offlineHistoryFallback by remember(id) { mutableStateOf(false) }
    // 列表里点进来的、稍后再看或浏览历史离线快照先顶上：正文与图片已经有了，先画出来，接口回来再换。
    var status by remember(id) {
        val initial = if (isDeletedArchive && archivedHistoryStatus != null) {
            archivedHistoryStatus
        } else {
            StatusMemory.find(id)
                ?: ReadLater.find(id, mid)?.status
                ?: archivedHistoryStatus
        }
        mutableStateOf(initial)
    }
    val effectiveReadOnly = readOnly || isDeletedArchive
    var detailError by remember(id) { mutableStateOf<String?>(null) }
    var activeTab by remember(id, initialSection) {
        mutableStateOf(
            if (initialSection == DetailInitialSection.Reposts) {
                DetailSectionTab.Reposts
            } else {
                DetailSectionTab.Comments
            },
        )
    }
    var displayedTab by remember(id, initialSection) { mutableStateOf(activeTab) }
    val sectionTabAlpha = remember(id) { Animatable(1f) }
    val comments = remember(id) { mutableStateListOf<Comment>() }
    var maxId by remember(id) { mutableStateOf<String?>(null) }
    var maxIdType by remember(id) { mutableStateOf(0) }
    var sort by remember(id) { mutableStateOf(CommentSort.Hot) }
    var displayedSort by remember(id) { mutableStateOf(CommentSort.Hot) }
    var sortSwitchSeq by remember(id) { mutableIntStateOf(0) }
    var sortSwitchJob by remember(id) { mutableStateOf<Job?>(null) }
    var nextCommentPage by remember(id) { mutableStateOf(1) }
    var loadingComments by remember(id) { mutableStateOf(false) }
    var commentsEnd by remember(id) { mutableStateOf(false) }
    var commentError by remember(id) { mutableStateOf<String?>(null) }
    val reposts = remember(id) { mutableStateListOf<Status>() }
    var nextRepostPage by remember(id) { mutableStateOf(1) }
    var loadingReposts by remember(id) { mutableStateOf(false) }
    var repostsEnd by remember(id) { mutableStateOf(false) }
    var repostError by remember(id) { mutableStateOf<String?>(null) }
    var repostsInitialized by remember(id) { mutableStateOf(false) }
    var switchingSort by remember(id) { mutableStateOf(false) }
    val sortContentAlpha = remember(id) { Animatable(1f) }
    var repostDialog by remember { mutableStateOf(false) }
    // 从评论菜单进来的转发：带上这条评论，composer 预填成引用它。
    var repostComment by remember { mutableStateOf<Comment?>(null) }
    var commentDialog by remember { mutableStateOf(false) }
    var activeCommentDetail by remember(id) { mutableStateOf<Comment?>(null) }
    var commentFilter by remember(id) { mutableStateOf(CommentFilterState()) }
    val listState = rememberLazyListState()
    BindScrollToTop(listState)
    var lazyViewportHeightPx by remember(id) { mutableIntStateOf(0) }
    var pinSectionMinHeight by remember(id, initialSection) {
        mutableStateOf(initialSection != DetailInitialSection.Body)
    }
    var userScrolled by remember(id) { mutableStateOf(false) }
    var pendingSectionJump by remember(id, initialSection) {
        mutableStateOf(initialSection != DetailInitialSection.Body)
    }

    // 切换评论筛选、排序或「评论 / 转发」标签前保持互动区高度兜底，防止列表因条目减少被拉回正文顶部。
    fun preserveSectionPositionBeforeSwitch() {
        if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) {
            pinSectionMinHeight = true
            val headerIdx = detailSectionHeaderIndex((status?.editCount ?: 0) > 0)
            if (listState.firstVisibleItemIndex > headerIdx) {
                scope.launch { listState.scrollToItem(headerIdx, 0) }
            }
        }
    }
    // The bar floats over this list too, so the quoted post inside the card below has to be able to
    // read where the bar ends when a page returns into it.
    val topBarBounds = remember { TopBarBounds() }
    val backdrop = rememberLayerBackdrop()
    val routeTransition = LocalRouteTransition.current

    suspend fun awaitEnterTransitionSettled() {
        val t = routeTransition ?: return
        while (!t.exiting() && t.progress() < 0.999f) {
            withFrameNanos { }
        }
    }

    // A link may carry a Base62 mblogid or numeric id; once loaded, prefer the canonical numeric id and mid.
    fun effectiveId(): String = status?.id?.takeIf { it.isNotBlank() } ?: id
    fun effectiveMid(): String = status?.mid?.takeIf { it.isNotBlank() } ?: mid

    suspend fun loadReposts(reset: Boolean, force: Boolean = false, waitEnterTransition: Boolean = false) {
        if (loadingReposts) return
        if (!reset && (repostsEnd || nextRepostPage <= 0)) return
        loadingReposts = true
        repostError = null
        try {
            val page = App.repo.reposts(
                id = effectiveId(),
                mid = effectiveMid(),
                page = if (reset) 1 else nextRepostPage,
                force = force,
            )
            if (waitEnterTransition) awaitEnterTransitionSettled()
            if (reset) reposts.clear()
            val before = reposts.size
            val known = reposts.mapTo(HashSet()) { it.id }
            page.items.forEach { if (known.add(it.id)) reposts.add(it) }
            nextRepostPage = page.nextPage
            repostsEnd = page.items.isEmpty() || page.nextPage == 0 || (!reset && reposts.size == before)
            repostsInitialized = true
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            repostError = t.message ?: "转发加载失败"
            repostsInitialized = true
        } finally {
            loadingReposts = false
        }
    }

    suspend fun loadComments(
        reset: Boolean,
        force: Boolean = false,
        waitEnterTransition: Boolean = false,
    ) {
        if (reset) {
            sortSwitchSeq++
            sortSwitchJob?.cancel()
            sortSwitchJob = null
            switchingSort = false
            if (sortContentAlpha.value < 1f) sortContentAlpha.snapTo(1f)
        }
        if (loadingComments) return
        if (!reset && (switchingSort || sort != displayedSort)) return
        loadingComments = true
        commentError = null
        val requestedSort = sort
        val seq = sortSwitchSeq
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
            // 若接口在卡片展开动画中途返回，先等展开落位再写入列表，避免动画中途大面积重组与排布掉帧。
            if (waitEnterTransition) awaitEnterTransitionSettled()
            // Another order took over while this page was on the wire; its rows are not ours.
            if (sort != requestedSort || sortSwitchSeq != seq) return
            if (reset) comments.clear()
            val known = comments.mapTo(HashSet()) { it.id }
            page.items.forEach { if (known.add(it.id)) comments.add(it) }
            maxId = page.maxId
            maxIdType = page.maxIdType
            nextCommentPage = page.nextPage
            displayedSort = requestedSort
            commentsEnd = if (reset) {
                page.items.isEmpty() || (requestedSort == CommentSort.Time && page.nextPage == 0)
            } else {
                commentsEnd || page.items.isEmpty() || (requestedSort == CommentSort.Time && page.nextPage == 0)
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            if (sortSwitchSeq == seq) {
                commentError = t.message ?: "评论加载失败"
            }
        } finally {
            loadingComments = false
        }
    }

    fun switchSort(next: CommentSort) {
        if (sort == next) return
        val seq = ++sortSwitchSeq
        sortSwitchJob?.cancel()
        sort = next
        // 如果在目标排序还没加载完前用户又点回当前已展示的排序，直接保留现成列表，不重复请求也不闪烁。
        if (next == displayedSort && (comments.isNotEmpty() || commentsEnd)) {
            switchingSort = false
            sortSwitchJob = if (sortContentAlpha.value < 1f) {
                scope.launch {
                    sortContentAlpha.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 160, easing = LinearOutSlowInEasing),
                    )
                }
            } else {
                null
            }
            return
        }
        switchingSort = true
        sortSwitchJob = scope.launch {
            // 若前一次切排序刚走到半透明就被打断，先平滑拉回满不透明度，保证等待新数据时旧列表始终清晰可见。
            val restoreJob = if (sortContentAlpha.value < 1f) {
                launch {
                    sortContentAlpha.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 140, easing = LinearOutSlowInEasing),
                    )
                }
            } else {
                null
            }
            // 先静默加载目标排序的第一页；数据没回来前绝不淡出旧列表，彻底避免淡出后的黑屏空窗。
            val fetched = try {
                App.repo.comments(
                    id = effectiveId(),
                    mid = effectiveMid(),
                    sort = next,
                    maxId = null,
                    maxIdType = 0,
                    page = 1,
                    force = true,
                )
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                restoreJob?.join()
                if (sortSwitchSeq == seq) {
                    switchingSort = false
                    sort = displayedSort
                    App.toast(t.message ?: "评论加载失败")
                }
                return@launch
            }
            restoreJob?.cancel()
            if (sortSwitchSeq != seq) return@launch
            // 目标列表已经就绪：旧列表淡出 -> 瞬时替换内存数据 -> 新列表淡入，全程无空白等待。
            sortContentAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 150, easing = FastOutLinearInEasing),
            )
            if (sortSwitchSeq != seq) return@launch
            comments.clear()
            val known = HashSet<String>(fetched.items.size)
            fetched.items.forEach { if (known.add(it.id)) comments.add(it) }
            maxId = fetched.maxId
            maxIdType = fetched.maxIdType
            nextCommentPage = fetched.nextPage
            commentsEnd = fetched.items.isEmpty() ||
                (next == CommentSort.Time && fetched.nextPage == 0)
            commentError = null
            displayedSort = next
            switchingSort = false
            sortContentAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 220, easing = LinearOutSlowInEasing),
            )
        }
    }

    suspend fun persistViewedStatus(s: Status) {
        val saved = withContext(ioDispatcher) {
            App.store.saveHistoryStatus(s)
        }
        HistoryImageArchiver.scheduleDownload(saved)
    }

    suspend fun switchToDeletedArchive(fallbackError: String?) {
        isDeletedArchive = true
        offlineHistoryFallback = false
        commentError = null
        repostError = null
        val archive = withContext(ioDispatcher) {
            val existing = App.store.findHistory(id, mid)
            if (existing != null) {
                App.store.markHistoryDeleted(id, mid)
                resolveHistoryFallbackStatus(existing.copy(deleted = true), App.store.historyImagesDir())
            } else {
                status?.let { current ->
                    val saved = App.store.saveHistoryStatus(current, deleted = true)
                    HistoryImageArchiver.scheduleDownload(saved)
                    resolveHistoryFallbackStatus(saved, App.store.historyImagesDir())
                }
            }
        }
        if (archive != null) {
            status = archive
            detailError = null
        } else if (status != null) {
            status = sanitizeStatusForHistory(status!!)
            detailError = null
        } else {
            detailError = fallbackError ?: "该微博已被删除"
        }
    }

    LaunchedEffect(id) {
        detailError = null
        val seeded = status
        if (isDeletedArchive && seeded != null) {
            return@LaunchedEffect
        }
        // 非历史回看且列表已带入正文时先落盘一次，防止刚点进就立刻返回丢失快照。
        if (!fromHistory && seeded != null) {
            scope.launch { persistViewedStatus(seeded) }
        }
        // 有列表带来的那条时，评论不必等正文回来再发：id/mid 路由里就有，两件事并行，
        // 但等进场展开落位后再写回 UI 状态，防止展开动画中途被评论重组打断。
        if (seeded != null && !isDeletedArchive) {
            scope.launch { loadComments(true, force = false, waitEnterTransition = true) }
        }
        try {
            val fetched = App.repo.detail(id, force = fromHistory)
            if (seeded != null) awaitEnterTransitionSettled()
            val merged = com.cobo.app.data.mergeDetailStatusWithSeeded(
                fetched,
                seeded,
            )
            if (merged != null) {
                status = merged
                isDeletedArchive = false
                offlineHistoryFallback = false
                persistViewedStatus(merged)
            } else if (status == null) {
                detailError = "正文加载失败"
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            if (isDeletedStatusThrowable(t)) {
                switchToDeletedArchive(t.message)
            } else if (status != null) {
                if (archivedHistoryStatus != null && seeded === archivedHistoryStatus) {
                    offlineHistoryFallback = true
                }
            } else {
                detailError = t.message ?: "正文加载失败"
            }
        }
        // 没有列表数据可顶时才等正文：那条路的 id 可能还是 Base62，评论要先拿到数字 id。
        if (seeded == null && !isDeletedArchive && status != null) {
            loadComments(true, force = false)
        }
    }

    LaunchedEffect(id, mid) {
        App.detailRefreshEvents.collect { targetId ->
            if (targetId == id || (mid.isNotBlank() && targetId == mid)) {
                runCatching { App.repo.detail(id, force = true) }.getOrNull()?.let { status = it }
            }
        }
    }

    val likeVersion = App.repo.likeVersion
    LaunchedEffect(id, likeVersion) {
        val current = status ?: return@LaunchedEffect
        val event = App.repo.statusLikeEvents[current.id] ?: return@LaunchedEffect
        if (current.liked != event.liked || current.attitudes != event.attitudes) {
            status = current.copy(liked = event.liked, attitudes = event.attitudes)
        }
    }

    val editedVersion = App.repo.editedVersion
    LaunchedEffect(id, mid, editedVersion) {
        val updated = App.repo.editedStatus(id, mid) ?: return@LaunchedEffect
        status = updated
    }

    // 仅在检测到用户主动拖拽时中止自动校准，避免程序内 scrollToItem 误触发 isScrollInProgress。
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) {
                userScrolled = true
                pendingSectionJump = false
            }
        }
    }

    // 外侧点击评论/转发图标进来时，自动定位到「评论 / 转发」分组头；若正文加载后新增编辑记录条则同步校准。
    LaunchedEffect(id, initialSection, status != null, (status?.editCount ?: 0) > 0) {
        if (!pendingSectionJump || userScrolled) return@LaunchedEffect
        val targetIdx = detailSectionHeaderIndex((status?.editCount ?: 0) > 0)
        withFrameNanos { }
        if (!pendingSectionJump || userScrolled) return@LaunchedEffect
        listState.scrollToItem(targetIdx)
        if (status != null) {
            pendingSectionJump = false
        }
    }

    var lastEditHistoryVisible by remember(id) { mutableStateOf((status?.editCount ?: 0) > 0) }
    LaunchedEffect((status?.editCount ?: 0) > 0) {
        val nowHasHistory = (status?.editCount ?: 0) > 0
        if (initialSection != DetailInitialSection.Body && nowHasHistory && !lastEditHistoryVisible && !userScrolled) {
            withFrameNanos { }
            if (!userScrolled) {
                listState.scrollToItem(detailSectionHeaderIndex(true))
            }
        }
        lastEditHistoryVisible = nowHasHistory
    }

    // 「评论 ↔ 转发」淡出淡入切换：若首次切到转发且尚未拉回数据，先等第一页转发加载完再播淡出淡入，避免中间空白。
    LaunchedEffect(activeTab) {
        if (activeTab == displayedTab) {
            if (sectionTabAlpha.value < 1f) {
                sectionTabAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 160, easing = LinearOutSlowInEasing),
                )
            }
            return@LaunchedEffect
        }
        if (activeTab == DetailSectionTab.Reposts && !repostsInitialized && (status != null || detailError != null)) {
            loadReposts(reset = true, force = false)
        }
        sectionTabAlpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 150, easing = FastOutLinearInEasing),
        )
        displayedTab = activeTab
        sectionTabAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 220, easing = LinearOutSlowInEasing),
        )
    }

    // 直接从外侧转发图标进入详情页（初始即处于转发态）时，等进场展开落位后懒加载第一页转发列表。
    LaunchedEffect(id, displayedTab, status?.id) {
        if (displayedTab == DetailSectionTab.Reposts && !repostsInitialized && (status != null || detailError != null)) {
            loadReposts(reset = true, force = false, waitEnterTransition = status != null)
        }
    }

    LaunchedEffect(listState, activeTab, comments.size, reposts.size) {
        snapshotFlow {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: -1) to info.totalItemsCount
        }.collect { (last, total) ->
            if (activeTab == DetailSectionTab.Comments) {
                if (!switchingSort && total > 0 && last >= total - 2 &&
                    comments.isNotEmpty() && !commentsEnd
                ) {
                    loadComments(false, force = false)
                }
            } else {
                if (!loadingReposts && total > 0 && last >= total - 2 &&
                    reposts.isNotEmpty() && !repostsEnd
                ) {
                    loadReposts(false, force = false)
                }
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
    val autoHideFloatingBars = App.settings.autoHideFloatingBars
    val detailBarScroll = rememberFloatingBarScrollController(
        enabled = autoHideFloatingBars,
        forceShow = repostDialog || commentDialog || activeCommentDetail != null || repostComment != null,
        listState = listState,
        topSafeZone = 16.dp,
    )
    val detailHideProgress = detailBarScroll.hideProgress
    val detailBarHidden by remember(detailHideProgress) {
        derivedStateOf { detailHideProgress.value >= FloatingBarNonInteractiveThreshold }
    }
    val layoutDensity = LocalDensity.current
    val fillerTopPadPx = with(layoutDensity) { (topBarHeight + 8.dp).roundToPx() }
    val fillerHeaderAndFooterPx = with(layoutDensity) { 104.dp.roundToPx() }
    val fillerMinItemHeightPx = with(layoutDensity) { 80.dp.roundToPx() }

    // 进场期间由根部 ExitSnapshotHost 冻结单帧快照，无需切换 liveBackdropEnabled，
    // 避免展开结束瞬间底部悬浮工具栏从纯色跳变到玻璃材质闪一下。
    val liveBackdropEnabled = blurEffectsEnabled()
    Box(Modifier.fillMaxSize().background(pageContainerColor())) {
        Box(
            Modifier
                .fillMaxSize()
                .nestedScroll(detailBarScroll.connection)
                .layerBackdropIf(liveBackdropEnabled, backdrop)
                .background(pageContainerColor())
        ) {
            CompositionLocalProvider(LocalBackdrop provides if (liveBackdropEnabled) backdrop else null) {
                LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { lazyViewportHeightPx = it.height }
                    .scrollEndHaptic(),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    top = topBarHeight + 8.dp,
                    end = 12.dp,
                    bottom = bottomBarHeight + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (status != null && isDeletedArchive) {
                    item(key = "deleted_archive_banner") {
                        DeletedArchiveNoticeBanner(
                            title = "已删除",
                            message = "该微博已被删除，当前显示本地保存的历史快照（仅保留文字与图片）",
                            isErrorTone = true,
                            modifier = Modifier.testTag("detail_deleted_banner"),
                        )
                    }
                } else if (status != null && offlineHistoryFallback) {
                    item(key = "offline_archive_banner") {
                        DeletedArchiveNoticeBanner(
                            title = "离线快照",
                            message = "当前无法连接微博，正在显示本地保存的历史快照（仅保留文字与图片）",
                            isErrorTone = false,
                            modifier = Modifier.testTag("detail_offline_banner"),
                        )
                    }
                }

                when {
                    status != null -> item(key = "status") {
                        StatusCard(
                            status = status!!,
                            onOpen = {},
                            onDeleted = { nav.pop() },
                            onUser = { if (it.id.isNotBlank()) nav.push(Route.Profile(it.id)) },
                            onImage = { pics, i -> nav.push(Route.Images(pics, i)) },
                            onVideo = { nav.push(Route.Video(it.url, it.title, it.isLive, it.fid, it.statusId)) },
                            onLike = {
                                val s = status ?: return@StatusCard
                                scope.launch {
                                    App.toggleStatusLike(s)?.let { status = it }
                                }
                            },
                            showActions = false,
                            actionsEnabled = !effectiveReadOnly,
                            onOpenQuoted = { quoted, quotedOrigin ->
                                quotedOrigin.attachToRow(listState, "status", topBarBounds)
                                nav.push(Route.Detail(quoted.id, quoted.mid, readOnly), quotedOrigin)
                            },
                            keepFrame = keepCardFrame,
                            foldLongText = false,
                            isDetail = true,
                            allowDeleted = isDeletedArchive,
                            deletedBadge = isDeletedArchive,
                            onExpandTruncated = {
                                val current = status
                                scope.launch {
                                    runCatching { App.repo.detail(id, force = true) }
                                        .onSuccess { reloaded ->
                                            val merged = com.cobo.app.data.mergeDetailStatusWithSeeded(reloaded, current)
                                            if (merged != null) {
                                                status = merged
                                                persistViewedStatus(merged)
                                            }
                                        }
                                        .onFailure { t ->
                                            if (isDeletedStatusThrowable(t)) {
                                                switchToDeletedArchive(t.message)
                                            } else {
                                                App.toast(t.message ?: "展开全文失败")
                                            }
                                        }
                                }
                            },
                        )
                    }

                    detailError != null -> item {
                        ErrorState(detailError!!) {
                            scope.launch {
                                detailError = null
                                runCatching { App.repo.detail(id, force = true) }
                                    .onSuccess { reloaded ->
                                        if (reloaded != null) {
                                            status = reloaded
                                            isDeletedArchive = false
                                            persistViewedStatus(reloaded)
                                        } else {
                                            detailError = "正文加载失败"
                                        }
                                    }
                                    .onFailure { t ->
                                        if (isDeletedStatusThrowable(t)) {
                                            switchToDeletedArchive(t.message)
                                        } else {
                                            detailError = t.message ?: "正文加载失败"
                                        }
                                    }
                            }
                        }
                    }

                    else -> item { LoadingState() }
                }

                if (!isDeletedArchive && (status?.editCount ?: 0) > 0) {
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

                val postAuthorUid = status?.user?.id
                val filterRules = ContentFilters.rules
                val selfUid = App.repo.session?.uid
                val unmutedComments = comments.filterNot {
                    shouldFilterComment(it, filterRules, selfUid)
                }
                val visibleComments = filterComments(unmutedComments, commentFilter, postAuthorUid, includeReplies = true)
                val visibleReposts = reposts.filterNot {
                    shouldFilterStatus(it, filterRules, FilterSurface.Feed, selfUid)
                }

                if (isDeletedArchive) {
                    item(key = "deleted_archive_footer") {
                        Text(
                            text = "该微博已被删除，历史快照仅保留文字与图片，不含评论、转发与视频",
                            fontSize = 12.sp,
                            color = scheme.onSurfaceSecondary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 20.dp),
                        )
                    }
                } else {
                item(key = "section_header") {
                    Box(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 32.dp)
                                .padding(
                                    start = if (keepCardFrame) 16.dp else FlatCommentInset,
                                    end = if (keepCardFrame) 16.dp else FlatCommentInset,
                                    top = 6.dp,
                                    bottom = 4.dp,
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val repostCount = status?.reposts ?: reposts.size
                            val commentCount = status?.comments ?: comments.size
                            CommentFilterDropdownTitle(
                                filter = commentFilter,
                                defaultLabel = detailSectionTabLabel("评论", commentCount),
                                showAuthorOption = !postAuthorUid.isNullOrBlank(),
                                groupKey = "detail_comment_filter_$id",
                                repostOptionLabel = detailSectionTabLabel("转发", repostCount),
                                showingReposts = activeTab == DetailSectionTab.Reposts,
                                onSelectReposts = {
                                    preserveSectionPositionBeforeSwitch()
                                    activeTab = DetailSectionTab.Reposts
                                },
                                onSelectAllComments = {
                                    preserveSectionPositionBeforeSwitch()
                                    activeTab = DetailSectionTab.Comments
                                    commentFilter = CommentFilterState()
                                },
                                onChange = {
                                    preserveSectionPositionBeforeSwitch()
                                    activeTab = DetailSectionTab.Comments
                                    commentFilter = it
                                },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            AnimatedVisibility(
                                visible = activeTab == DetailSectionTab.Comments,
                                enter = fadeIn(tween(180, easing = LinearOutSlowInEasing)) +
                                    scaleIn(tween(180, easing = LinearOutSlowInEasing), initialScale = 0.92f) +
                                    expandHorizontally(
                                        animationSpec = tween(180, easing = LinearOutSlowInEasing),
                                        expandFrom = Alignment.End,
                                    ),
                                exit = fadeOut(tween(130, easing = FastOutLinearInEasing)) +
                                    scaleOut(tween(130, easing = FastOutLinearInEasing), targetScale = 0.92f) +
                                    shrinkHorizontally(
                                        animationSpec = tween(130, easing = FastOutLinearInEasing),
                                        shrinkTowards = Alignment.End,
                                    ),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Spacer(Modifier.width(8.dp))
                                    // COUI's ChipTab is a 32dp, 14sp control that dwarfs a 13sp section label,
                                    // so the two orders share one compact track instead.
                                    SortSwitch(
                                        selected = sort,
                                        onSelect = {
                                            preserveSectionPositionBeforeSwitch()
                                            switchSort(it)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                if (displayedTab == DetailSectionTab.Comments) {
                    itemsIndexed(visibleComments, key = { index, comment -> "${comment.id}#$index" }) { index, comment ->
                        CommentCard(
                            comment = comment,
                            statusId = id,
                            mid = effectiveMid(),
                            modifier = Modifier.graphicsLayer {
                                alpha = sortContentAlpha.value * sectionTabAlpha.value
                            },
                            likeTag = Modifier.testTag("comment_like_$index"),
                            replyTag = Modifier.testTag("comment_reply_$index"),
                            readOnly = readOnly,
                            keepFrame = keepCardFrame,
                            onOpenDetail = { activeCommentDetail = comment },
                            onImage = { pics, index -> nav.push(Route.Images(pics, index)) },
                            onRepost = { repostComment = it },
                            onReport = { nav.push(Route.Web(commentReportUrl(effectiveMid(), it.id), "投诉")) },
                            onUser = { nav.push(Route.Profile(it.id)) },
                        ) {
                            val at = comments.indexOfFirst { it.id == comment.id }
                            if (at < 0) return@CommentCard
                            val current = comments[at]
                            if (App.repo.isCommentLikeInFlight(current.id)) return@CommentCard
                            scope.launch {
                                // 先问再改：确认框弹着的这段时间列表可能刷新过，回来重新找一遍位置。
                                if (!App.confirmLike(current.liked)) return@launch
                                if (App.repo.isCommentLikeInFlight(current.id)) return@launch
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

                    item(key = "section_footer") {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .graphicsLayer { alpha = sortContentAlpha.value * sectionTabAlpha.value },
                        ) {
                            when {
                                // An order switch keeps the old rows visible and reports progress in the
                                // header, so it must not also drop a spinner under the list.
                                switchingSort -> Unit
                                loadingComments -> LoadingState()
                                commentError != null -> ErrorState(commentError!!) {
                                    scope.launch { loadComments(true, force = true) }
                                }
                                commentFilter.isActive && unmutedComments.isNotEmpty() && visibleComments.isEmpty() -> Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        text = commentFilterEmptyMessage(commentFilter),
                                        fontSize = 13.sp,
                                        color = scheme.onSurfaceSecondary,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        if (!commentsEnd) {
                                            TextButton(
                                                text = "加载更多",
                                                onClick = { scope.launch { loadComments(false, force = false) } },
                                            )
                                        }
                                        TextButton(
                                            text = "显示全部评论",
                                            onClick = {
                                                preserveSectionPositionBeforeSwitch()
                                                commentFilter = CommentFilterState()
                                            },
                                        )
                                    }
                                }
                                commentsEnd -> Text(
                                    if (comments.isNotEmpty() && unmutedComments.isEmpty()) "当前评论已被屏蔽规则隐藏" else "没有更多评论",
                                    fontSize = 12.sp,
                                    color = scheme.onSurfaceSecondary,
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                )
                                comments.isNotEmpty() && unmutedComments.isEmpty() -> Text(
                                    "当前评论已被屏蔽规则隐藏",
                                    fontSize = 12.sp,
                                    color = scheme.onSurfaceSecondary,
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                )
                            }
                        }
                    }
                } else {
                    itemsIndexed(visibleReposts, key = { index, repost -> "repost_${repost.id}#$index" }) { _, repost ->
                        RepostRowCard(
                            repost = repost,
                            keepFrame = keepCardFrame,
                            modifier = Modifier.graphicsLayer { alpha = sectionTabAlpha.value },
                            onUser = { nav.push(Route.Profile(it.id)) },
                            onOpen = {
                                nav.push(Route.Detail(repost.id, repost.mid, readOnly))
                            },
                        )
                    }

                    item(key = "section_footer") {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .graphicsLayer { alpha = sectionTabAlpha.value },
                        ) {
                            when {
                                loadingReposts -> LoadingState()
                                repostError != null -> ErrorState(repostError!!) {
                                    scope.launch { loadReposts(true, force = true) }
                                }
                                repostsEnd -> Text(
                                    when {
                                        reposts.isEmpty() -> "暂无转发"
                                        visibleReposts.isEmpty() -> "当前转发已被屏蔽规则隐藏"
                                        else -> "没有更多转发"
                                    },
                                    fontSize = 12.sp,
                                    color = scheme.onSurfaceSecondary,
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                )
                                reposts.isNotEmpty() && visibleReposts.isEmpty() -> Text(
                                    "当前转发已被屏蔽规则隐藏",
                                    fontSize = 12.sp,
                                    color = scheme.onSurfaceSecondary,
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                )
                            }
                        }
                    }
                }

                val usableViewportPx = (lazyViewportHeightPx - fillerTopPadPx).coerceAtLeast(0)
                val activeItemCount = if (displayedTab == DetailSectionTab.Comments) {
                    visibleComments.size
                } else {
                    visibleReposts.size
                }
                val fillerPx = computeDetailSectionFillerHeightPx(
                    usableViewportPx = usableViewportPx,
                    activeItemCount = activeItemCount,
                    preservePosition = pinSectionMinHeight,
                    headerAndFooterPx = fillerHeaderAndFooterPx,
                    minItemHeightPx = fillerMinItemHeightPx,
                )
                if (fillerPx > 0) {
                    val fillerDp = with(layoutDensity) { fillerPx.toDp() }
                    item(key = "section_tail_filler") {
                        Spacer(Modifier.height(fillerDp))
                    }
                }
                }
            }
            }
        }

        CompositionLocalProvider(
            LocalBackdrop provides if (liveBackdropEnabled) backdrop else null,
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
                                        val previous = status
                                        try {
                                            val reloaded = App.repo.detail(id, force = true)
                                            if (reloaded != null) {
                                                val merged = com.cobo.app.data.mergeDetailStatusWithSeeded(reloaded, previous) ?: reloaded
                                                status = merged
                                                isDeletedArchive = false
                                                offlineHistoryFallback = false
                                                persistViewedStatus(merged)
                                                if (activeTab == DetailSectionTab.Comments) {
                                                    loadComments(true, force = true)
                                                } else {
                                                    loadReposts(true, force = true)
                                                }
                                            }
                                        } catch (c: CancellationException) {
                                            throw c
                                        } catch (t: Throwable) {
                                            if (isDeletedStatusThrowable(t)) {
                                                switchToDeletedArchive(t.message)
                                                App.toast("该微博已被删除")
                                            } else {
                                                App.toast(t.message ?: "刷新失败")
                                            }
                                        }
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
                val currentLocalFavorited = current?.let { App.repo.isFavorited(it) } == true
                val currentInReadLater = current?.let { ReadLater.contains(it.id) } == true
                val currentCloudFavorited = current?.let { App.repo.isCloudFavorited(it.id, it.favorited) } == true
                var favoriteMenuExpanded by remember(current?.id) { mutableStateOf(false) }
                val anySaved = currentLocalFavorited || currentCloudFavorited || currentInReadLater
                val favButtonIcon = when {
                    currentLocalFavorited || currentCloudFavorited -> CoboIcons.StarFill
                    currentInReadLater -> CoboIcons.BookmarkFill
                    else -> CoboIcons.Star
                }
                var toolbarHeightPx by remember { mutableIntStateOf(0) }
                val toolbarExtraClearancePx = with(LocalDensity.current) { 24.dp.toPx() }
                DetailFloatingToolbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .onSizeChanged { toolbarHeightPx = it.height }
                        .graphicsLayer {
                            val hp = detailHideProgress.value.coerceIn(0f, 1f)
                            val travel = if (toolbarHeightPx > 0) toolbarHeightPx.toFloat() else toolbarExtraClearancePx * 5f
                            translationY = hp * (travel + toolbarExtraClearancePx)
                            alpha = (1f - hp * 1.25f).coerceIn(0f, 1f)
                        }
                        .padding(bottom = BottomFloatingNavigationBarDefaults.navigationBarPadding())
                        .then(if (detailBarHidden) Modifier.clearAndSetSemantics {} else Modifier),
                    barrierEnabled = !detailBarHidden,
                ) {
                    DetailFloatingToolbarItem(
                        icon = CoboIcons.Repost,
                        count = fmtCount(current?.reposts ?: 0),
                        contentDescription = "转发",
                        onClick = { repostDialog = true },
                        modifier = Modifier.testTag("detail_repost"),
                        enabled = !effectiveReadOnly && !detailBarHidden,
                    )
                    DetailFloatingToolbarItem(
                        icon = CoboIcons.Comment,
                        count = fmtCount(current?.comments ?: 0),
                        contentDescription = "评论",
                        onClick = { commentDialog = true },
                        modifier = Modifier.testTag("detail_comment"),
                        enabled = !effectiveReadOnly && !detailBarHidden,
                    )
                    Box {
                        DetailFloatingToolbarItem(
                            icon = favButtonIcon,
                            count = "",
                            contentDescription = if (anySaved) "已收藏" else "收藏",
                            onClick = { if (current != null) favoriteMenuExpanded = true },
                            modifier = Modifier.testTag("detail_favorite"),
                            active = anySaved,
                            enabled = current != null && !detailBarHidden,
                        )
                        OverlayListPopup(
                            show = favoriteMenuExpanded && current != null,
                            popupPositionProvider = remember { detailFavoritePopupPositionProvider() },
                            onDismissRequest = { favoriteMenuExpanded = false },
                        ) {
                            val s = current ?: return@OverlayListPopup
                            val dismiss = LocalDismissState.current
                            val menuScheme = COUITheme.colorScheme
                            val showCloudFav = !effectiveReadOnly && !App.repo.guest
                            val optionSize = 2 + if (showCloudFav) 1 else 0
                            var idx = 0
                            ListPopupColumn {
                                val localIcon = if (currentLocalFavorited) CoboIcons.StarFill else CoboIcons.Star
                                val localTint = if (currentLocalFavorited) menuScheme.primary else menuScheme.onSurfaceContainer
                                DropdownImpl(
                                    item = DropdownItem(
                                        text = if (currentLocalFavorited) "取消本地收藏" else "收藏到本地",
                                        icon = { iconModifier ->
                                            Box(iconModifier, contentAlignment = Alignment.Center) {
                                                Icon(localIcon, null, Modifier.size(18.dp), tint = localTint)
                                            }
                                        },
                                    ),
                                    optionSize = optionSize,
                                    isSelected = false,
                                    index = idx++,
                                    groupKey = "detail_favorite_${s.id}",
                                    onSelectedIndexChange = {
                                        dismiss?.invoke()
                                        favoriteMenuExpanded = false
                                        val next = App.repo.toggleLocalFavorite(s)
                                        status = s
                                        App.toast(if (next) "已收藏到本地" else "已取消本地收藏")
                                    },
                                )
                                if (showCloudFav) {
                                    val cloudIcon = if (currentCloudFavorited) CoboIcons.CloudFill else CoboIcons.Cloud
                                    val cloudTint = if (currentCloudFavorited) menuScheme.primary else menuScheme.onSurfaceContainer
                                    DropdownImpl(
                                        item = DropdownItem(
                                            text = if (currentCloudFavorited) "取消微博账号收藏" else "收藏到微博账号",
                                            icon = { iconModifier ->
                                                Box(iconModifier, contentAlignment = Alignment.Center) {
                                                    Icon(cloudIcon, null, Modifier.size(18.dp), tint = cloudTint)
                                                }
                                            },
                                        ),
                                        optionSize = optionSize,
                                        isSelected = false,
                                        index = idx++,
                                        groupKey = "detail_favorite_${s.id}",
                                        onSelectedIndexChange = {
                                            dismiss?.invoke()
                                            favoriteMenuExpanded = false
                                            val beforeCloudFav = currentCloudFavorited
                                            scope.launch {
                                                runCatching { App.repo.favorite(s.id, beforeCloudFav) }
                                                    .onSuccess {
                                                        val next = !beforeCloudFav
                                                        status = s.copy(favorited = next)
                                                        App.toast(if (next) "已收藏到微博账号" else "已取消微博账号收藏")
                                                    }
                                                    .onFailure { App.toast(it.message ?: "操作失败") }
                                            }
                                        },
                                    )
                                }
                                val readLaterIcon = if (currentInReadLater) CoboIcons.BookmarkFill else CoboIcons.Bookmark
                                val readLaterTint = if (currentInReadLater) menuScheme.primary else menuScheme.onSurfaceContainer
                                DropdownImpl(
                                    item = DropdownItem(
                                        text = if (currentInReadLater) "移出稍后再看" else "稍后再看",
                                        icon = { iconModifier ->
                                            Box(iconModifier, contentAlignment = Alignment.Center) {
                                                Icon(readLaterIcon, null, Modifier.size(18.dp), tint = readLaterTint)
                                            }
                                        },
                                    ),
                                    optionSize = optionSize,
                                    isSelected = false,
                                    index = idx,
                                    groupKey = "detail_favorite_${s.id}",
                                    onSelectedIndexChange = {
                                        dismiss?.invoke()
                                        favoriteMenuExpanded = false
                                        val added = ReadLater.toggle(s)
                                        App.toast(if (added) "已加入稍后再看" else "已移出稍后再看")
                                    },
                                )
                            }
                        }
                    }
                    DetailFloatingToolbarItem(
                        icon = if (current?.liked == true) CoboIcons.HeartFill else CoboIcons.Heart,
                        count = fmtCount(current?.attitudes ?: 0),
                        contentDescription = if (current?.liked == true) "已赞" else "赞",
                        onClick = {
                            val s = status ?: return@DetailFloatingToolbarItem
                            scope.launch {
                                App.toggleStatusLike(s)?.let { status = it }
                            }
                        },
                        modifier = Modifier.testTag("detail_like"),
                        active = current?.liked == true,
                        enabled = !effectiveReadOnly && !detailBarHidden,
                    )
                }
            }
        }

        CommentDetailOverlay(
            root = activeCommentDetail,
            statusId = effectiveId(),
            mid = effectiveMid(),
            postAuthorUid = status?.user?.id,
            readOnly = readOnly,
            keepFrame = keepCardFrame,
            onImage = { pics, idx -> nav.push(Route.Images(pics, idx)) },
            onRepost = { repostComment = it },
            onReport = { nav.push(Route.Web(commentReportUrl(effectiveMid(), it.id), "投诉")) },
            onUser = { nav.push(Route.Profile(it.id)) },
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
                    maxImages = 1,
                    onDismiss = {
                        repostDialog = false
                        repostComment = null
                    },
                    onSubmit = { text, images, visibility ->
                        try {
                            val picIds = images.map { App.repo.uploadImage(it) }
                            App.repo.repost(id, effectiveMid(), text, visibility.code, picIds)
                            App.toast("转发成功")
                            if (activeTab == DetailSectionTab.Reposts) {
                                loadReposts(true, force = true)
                            } else {
                                repostsInitialized = false
                            }
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
                    maxImages = 1,
                    onDismiss = { commentDialog = false },
                    onSubmit = { text, images, _ ->
                        try {
                            val picIds = images.map { App.repo.uploadImage(it) }
                            App.repo.comment(id, effectiveMid(), text, picIds)
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

/** 生成详情页「转发 / 评论」标签文案，数量大于 0 时附带格式化计数。 */
internal fun detailSectionTabLabel(label: String, count: Int): String =
    if (count > 0) "$label ${fmtCount(count)}" else label

/** 详情页转发列表条目，显示转发人信息、时间来源与转发态度正文，点击整条进入该转发详情。 */
@Composable
private fun RepostRowCard(
    repost: Status,
    keepFrame: Boolean,
    onUser: (User) -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = COUITheme.colorScheme
    val author = repost.user
    val displayName = UserRemarks.displayName(author?.id, author?.name.orEmpty())
    val body: @Composable () -> Unit = {
        Row(
            modifier = (if (keepFrame) Modifier else modifier)
                .fillMaxWidth()
                .let { mod ->
                    if (keepFrame) mod else mod.clickable(onClick = onOpen)
                }
                .padding(
                    horizontal = if (keepFrame) 14.dp else FlatCommentInset,
                    vertical = 10.dp,
                ),
            verticalAlignment = Alignment.Top,
        ) {
            Avatar(
                url = author?.avatar,
                size = 34.dp,
                verifiedKind = author?.verifiedKind ?: VerifiedKind.None,
                modifier = Modifier.clickable { author?.let(onUser) },
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = displayName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clickable { author?.let(onUser) },
                    )
                    val membership = author?.membership ?: Membership.None
                    if (membership != Membership.None) {
                        Spacer(Modifier.width(4.dp))
                        MemberBadge(membership, compact = true, rank = author?.mbrank ?: 0, height = 14.dp)
                    }
                }
                val meta = buildString {
                    append(formatWeiboTime(repost.createdAt))
                    if (repost.source.isNotBlank()) {
                        if (isNotEmpty()) append(" 来自 ")
                        append(repost.source)
                    }
                }
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        fontSize = 11.sp,
                        color = scheme.onSurfaceSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (repost.textHtml.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    WeiboText(
                        html = repost.textHtml,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                }
            }
        }
    }
    if (keepFrame) {
        Card(
            modifier = modifier.fillMaxWidth(),
            cornerRadius = CardDefaults.GroupCornerRadius,
            insideMargin = PaddingValues(0.dp),
            weight = CardDefaults.GroupWeight,
            onClick = onOpen,
        ) { body() }
    } else {
        body()
    }
}

/** 网页版举报入口：`type=2` 是举报评论，`url` 指向它所在的那条微博。 */
private fun commentReportUrl(mid: String, cid: String): String {
    val target = urlEncode("/detail/$mid")
    return "https://service.account.weibo.com/reportspam" +
        "?rid=$cid&from=10106&type=2&url=$target&bottomnav=1&wvr=5"
}

private const val PreviewReplies = 1

/**
 * 评论配图渲染入口：优先使用评论自带 `pics`，为空时兜底解析正文里的「评论配图 / 查看图片 / photo.weibo.com」链接并直接出图。
 */
@Composable
internal fun CommentInlinePics(
    comment: Comment,
    height: Dp,
    onImage: (List<Pic>, Int) -> Unit,
    topGap: Dp = 6.dp,
) {
    val resolvableLinks = remember(comment.id, comment.textHtml, comment.pics) {
        if (comment.pics.isEmpty()) extractResolvablePhotoLinksFromHtml(comment.textHtml) else emptyList()
    }
    var resolvedPics by remember(comment.id, resolvableLinks) {
        mutableStateOf(
            if (resolvableLinks.isEmpty()) emptyList()
            else App.repo.cachedInlineImageLinks(resolvableLinks).orEmpty(),
        )
    }
    LaunchedEffect(comment.id, resolvableLinks) {
        if (resolvableLinks.isNotEmpty() && resolvedPics.isEmpty()) {
            resolvedPics = App.repo.resolveInlineImageLinks(resolvableLinks)
        }
    }
    val effectivePics = if (comment.pics.isNotEmpty()) comment.pics else resolvedPics
    if (effectivePics.isNotEmpty()) {
        Spacer(Modifier.height(topGap))
        CommentThumbnails(effectivePics, height, onImage)
    }
}

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
    val origins = LocalImageOrigins.current
    val single = pics.size == 1
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        pics.take(4).forEachIndexed { index, pic ->
            val declaredAspect = if (pic.width > 0 && pic.height > 0) {
                pic.width.toFloat() / pic.height
            } else {
                null
            }
            var measuredAspect by remember(pic.large) { mutableStateOf<Float?>(null) }
            val aspect = declaredAspect ?: measuredAspect
            val isLong = !pic.isVideo && isLongImageAspect(aspect)
            val width = if (single) {
                (height * (aspect ?: 1.4f)).coerceIn(72.dp, 200.dp)
            } else {
                height
            }
            val shown = pic.thumb.ifBlank { pic.large }
            Box(
                modifier = Modifier
                    .size(width, height)
                    .imageOrigin(pic.large, 8.dp, aspect = aspect, model = shown)
                    .clip(RoundedCornerShape(8.dp))
                    .background(scheme.surfaceContainerHigh)
                    .testTag("comment_pic_$index")
                    .clickable { onImage(pics, index) },
            ) {
                AsyncImage(
                    model = shown,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = if (isLong) Alignment.TopCenter else Alignment.Center,
                    onSuccess = { state ->
                        origins.putCacheKey(pic.large, state.result.memoryCacheKey)
                        if (declaredAspect == null) {
                            val s = state.painter.intrinsicSize
                            if (s.width > 0f && s.height > 0f) measuredAspect = s.width / s.height
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                if (isLong) {
                    LongImageBadge(pic.large, Modifier.align(Alignment.BottomEnd).padding(4.dp))
                }
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
        var expanded by remember(comment.id) { mutableStateOf(false) }

    fun copyText() {
        copyToClipboard(stripHtml(comment.textHtml), "comment")
        App.toast("已复制")
    }

    val author = comment.user
    val canMuteAuthor = author != null && author.id.isNotBlank() && author.id != App.repo.session?.uid
    val entries = buildList {
        add(CommentMenuEntry("复制", COUIIcons.Copy, true, ::copyText))
        add(CommentMenuEntry("转发", COUIIcons.Forward, !readOnly, onRepost))
        add(CommentMenuEntry("投诉", COUIIcons.Report, true, onReport))
        add(CommentMenuEntry("回复", COUIIcons.Reply, !readOnly, onReply))
        if (canMuteAuthor) {
            add(
                CommentMenuEntry("静音该用户", CoboIcons.Block, true) {
                    ContentFilters.setMutedUser(author.id, author.name, MuteUserMode.All)
                    App.toast("已静音「${author.name.ifBlank { author.id }}」")
                },
            )
        }
    }
    val buttonHeight = if (compact) 18.dp else 20.dp
    val iconSize = if (compact) 14.dp else 15.dp
    val squircleEnabled = isSquircleEnabled()
    val moreButtonShape = remember(squircleEnabled) { SquircleShape(6.dp, squircleEnabled = squircleEnabled) }

    Box {
        Box(
            modifier = Modifier
                .height(buttonHeight)
                .widthIn(min = 24.dp)
                .clip(moreButtonShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { expanded = true }
                .semantics { contentDescription = "更多" }
                .testTag(if (compact) "reply_more_${comment.id}" else "comment_more_${comment.id}"),
            contentAlignment = Alignment.CenterEnd,
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
    onUser: (User) -> Unit = {},
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
        if (App.repo.isCommentLikeInFlight(current.id)) return
        scope.launch {
            // 同上：先问再改，确认期间列表可能刷新，回来重新找一遍位置。
            if (!App.confirmLike(current.liked)) return@launch
            if (App.repo.isCommentLikeInFlight(current.id)) return@launch
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
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { comment.user?.let(onUser) },
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
                            UserRemarks.displayName(comment.user?.id, comment.user?.name.orEmpty()),
                            fontSize = 15.sp,
                            lineHeight = 20.sp,
                            // 跟楼中楼的 ID 同一档灰：评论正文才是主内容，ID 往后退一格。
                            color = scheme.onSurfaceSecondary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { comment.user?.let(onUser) },
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
                CommentInlinePics(comment, 96.dp, onImage)
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
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { reply.user?.let(onUser) },
                        )
                        WeiboText(
                            html = reply.textHtml,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        )
                        CommentInlinePics(reply, 72.dp, onImage)
                    }
                }
                if (!keepFrame) {
                    Spacer(Modifier.height(4.dp))
                    actions()
                }
                val replyFilterRules = ContentFilters.rules
                val replySelfUid = App.repo.session?.uid
                val visibleRepliesAll = replies.filterNot {
                    shouldFilterComment(it, replyFilterRules, replySelfUid)
                }
                val total = maxOf(replyTotal, replies.size)
                val visible = if (expanded) visibleRepliesAll else visibleRepliesAll.take(PreviewReplies)
                if (visible.isNotEmpty() || loadingReplies || (expanded && !repliesDone && replies.size < total)) {
                    Spacer(Modifier.height(6.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(nestedTone)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
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
                                onUser = onUser,
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
            modifier = modifier.fillMaxWidth(),
            cornerRadius = CardDefaults.GroupCornerRadius,
            weight = CardDefaults.GroupWeight,
        ) { body() }
    } else {
        Box(modifier.fillMaxWidth()) { body() }
    }

    replyTarget?.let { target ->
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
    /** 评论详情页中的楼中楼回复传 `false`，与主评论采用完全一致的字号、头像与按钮尺寸。 */
    compact: Boolean = true,
    onImage: (List<Pic>, Int) -> Unit = { _, _ -> },
    onRepost: (Comment) -> Unit = {},
    onReport: (Comment) -> Unit = {},
    onUser: (User) -> Unit = {},
) {
    val scheme = COUITheme.colorScheme
    val avatarSize = if (compact) 22.dp else 36.dp
    val avatarGap = if (compact) 8.dp else 10.dp
    val nameSize = if (compact) 14.sp else 15.sp
    val nameLineHeight = if (compact) 18.sp else 20.sp
    val nameWeight = if (compact) FontWeight.Medium else FontWeight.SemiBold
    val badgeSize = if (compact) 13.dp else 14.dp
    val bodyGap = if (compact) 2.dp else 4.dp
    val bodySize = if (compact) 13.sp else 15.sp
    val bodyLineHeight = if (compact) 18.sp else 21.sp
    val thumbGap = if (compact) 4.dp else 6.dp
    val thumbSize = if (compact) 64.dp else 96.dp
    val bottomGap = if (compact) 2.dp else 6.dp
    Row(if (compact) Modifier.padding(vertical = 3.dp) else Modifier) {
        Avatar(
            url = reply.user?.avatar,
            size = avatarSize,
            verifiedKind = reply.user?.verifiedKind ?: VerifiedKind.None,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { reply.user?.let(onUser) },
        )
        Spacer(Modifier.width(avatarGap))
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
                        UserRemarks.displayName(reply.user?.id, reply.user?.name.orEmpty()),
                        fontSize = nameSize,
                        lineHeight = nameLineHeight,
                        color = scheme.onSurfaceSecondary,
                        fontWeight = nameWeight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { reply.user?.let(onUser) },
                    )
                    CommentHeaderBadges(reply, badgeSize)
                }
                if (showActions) {
                    CommentMoreButton(
                        comment = reply,
                        readOnly = !enabled,
                        compact = compact,
                        onRepost = { onRepost(reply) },
                        onReport = { onReport(reply) },
                        onReply = onReply,
                    )
                }
            }
            Spacer(Modifier.height(bodyGap))
            WeiboText(
                html = reply.textHtml,
                fontSize = bodySize,
                lineHeight = bodyLineHeight,
            )
            CommentInlinePics(reply, thumbSize, onImage, topGap = thumbGap)
            Spacer(Modifier.height(bottomGap))
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
                        compact = compact,
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

/**
 * 评论区左侧标题下拉筛选器：未筛选时以无底色标题 + 向下箭头与右侧排序开关同行，
 * 启用条件后切为淡主色胶囊并附一键清除按钮，点击展开 COUI 原生多选下拉菜单。
 */
@Composable
internal fun CommentFilterDropdownTitle(
    filter: CommentFilterState,
    defaultLabel: String,
    showAuthorOption: Boolean,
    groupKey: String,
    onChange: (CommentFilterState) -> Unit,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 14.sp,
    fontWeight: FontWeight = FontWeight.SemiBold,
    repostOptionLabel: String? = null,
    showingReposts: Boolean = false,
    onSelectReposts: (() -> Unit)? = null,
    onSelectAllComments: (() -> Unit)? = null,
) {
    val scheme = COUITheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    var expanded by remember(groupKey) { mutableStateOf(false) }
    var displayedTopIsReposts by remember(groupKey) { mutableStateOf(showingReposts) }
    val topItemAlpha = remember(groupKey) { Animatable(1f) }
    LaunchedEffect(showingReposts, expanded) {
        if (!expanded) {
            displayedTopIsReposts = showingReposts
            if (topItemAlpha.value != 1f) topItemAlpha.snapTo(1f)
            return@LaunchedEffect
        }
        if (displayedTopIsReposts == showingReposts) return@LaunchedEffect
        topItemAlpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 120, easing = FastOutLinearInEasing),
        )
        displayedTopIsReposts = showingReposts
        topItemAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 180, easing = LinearOutSlowInEasing),
        )
    }

    val filterActive = !showingReposts && filter.isActive
    val triggerLabel = if (showingReposts && !repostOptionLabel.isNullOrBlank()) {
        repostOptionLabel
    } else {
        commentFilterTriggerLabel(filter, defaultLabel)
    }
    val triggerBg by animateColorAsState(
        targetValue = if (filterActive) {
            scheme.primary.copy(alpha = if (dark) 0.22f else 0.13f)
        } else {
            Color.Transparent
        },
        animationSpec = tween(durationMillis = 180),
        label = "commentFilterTriggerBg",
    )
    val textColor by animateColorAsState(
        targetValue = if (filterActive) scheme.primary else scheme.onSurface,
        animationSpec = tween(durationMillis = 180),
        label = "commentFilterTextColor",
    )
    val chevronColor by animateColorAsState(
        targetValue = if (filterActive) scheme.primary else scheme.onSurfaceSecondary,
        animationSpec = tween(durationMillis = 180),
        label = "commentFilterChevronColor",
    )
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "commentFilterChevron",
    )

    // 预先按菜单内所有可能出现的文案（含转发项与评论项）计算稳定最小宽度，
    // 保证菜单初始展开宽度与选中某项后完全一致，不会在点选瞬间突然加宽。
    val stableMenuMinWidth = remember(defaultLabel, repostOptionLabel, showAuthorOption, density) {
        val candidates = buildList {
            add(defaultLabel)
            if (!repostOptionLabel.isNullOrBlank()) add(repostOptionLabel)
            if (showAuthorOption) add("仅看博主")
            add("仅看带图")
        }
        val style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal)
        val maxTextPx = candidates.maxOfOrNull { textMeasurer.measure(it, style = style).size.width } ?: 0
        with(density) { maxTextPx.toDp() } + 104.dp
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(modifier = Modifier.weight(1f, fill = false)) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(triggerBg)
                    .clickable(role = Role.DropdownList) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        expanded = !expanded
                    }
                    .animateContentSize(animationSpec = tween(180, easing = LinearOutSlowInEasing))
                    .padding(
                        start = if (filterActive) 9.dp else 0.dp,
                        end = if (filterActive) 7.dp else 4.dp,
                        top = 4.dp,
                        bottom = 4.dp,
                    )
                    .testTag("comment_filter_trigger"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedContent(
                    targetState = triggerLabel,
                    transitionSpec = {
                        (fadeIn(tween(170, easing = LinearOutSlowInEasing)) togetherWith
                            fadeOut(tween(110, easing = FastOutLinearInEasing)))
                            .using(SizeTransform(clip = false))
                    },
                    label = "commentFilterTriggerLabel",
                    modifier = Modifier.weight(1f, fill = false),
                ) { label ->
                    Text(
                        text = label,
                        fontSize = fontSize,
                        fontWeight = if (filterActive) FontWeight.SemiBold else fontWeight,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(3.dp))
                Icon(
                    imageVector = CoboIcons.ChevronDown,
                    contentDescription = null,
                    tint = chevronColor,
                    modifier = Modifier
                        .size(14.dp)
                        .graphicsLayer { rotationZ = chevronRotation },
                )
            }

            OverlayListPopup(
                show = expanded,
                popupPositionProvider = ListPopupDefaults.DropdownPositionProvider,
                onDismissRequest = { expanded = false },
            ) {
                val dismiss = LocalDismissState.current
                val menuScheme = COUITheme.colorScheme
                val repostLabel = repostOptionLabel?.takeIf { it.isNotBlank() }
                val repostSelectHandler = onSelectReposts
                val hasSectionSwitchOption = repostLabel != null && repostSelectHandler != null
                val optionSize = (if (hasSectionSwitchOption) 1 else 0) +
                    (if (showAuthorOption) 1 else 0) +
                    1
                val itemWidthMod = Modifier.widthIn(min = stableMenuMinWidth)
                var idx = 0
                Box(
                    modifier = Modifier.animateContentSize(
                        animationSpec = tween(durationMillis = 180, easing = LinearOutSlowInEasing),
                    ),
                ) {
                    ListPopupColumn {
                        // 首项互切：必须作为 ListPopupColumn 的直接子节点，以保留 COUI PopupListItemHandle 手势与点击注册；
                        // 转发态点选「仅看博主 / 仅看带图」时菜单保持展开，首项通过 graphicsLayer 原地先淡出再淡入由「评论」切为「转发」。
                        if (hasSectionSwitchOption) {
                            val topLabel = if (displayedTopIsReposts) defaultLabel else repostLabel
                            val topIcon = if (displayedTopIsReposts) CoboIcons.Comment else CoboIcons.Repost
                            val topTag = if (displayedTopIsReposts) "detail_tab_comments" else "detail_tab_reposts"
                            DropdownImpl(
                                item = DropdownItem(
                                    text = topLabel,
                                    icon = { iconModifier ->
                                        Box(iconModifier, contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = topIcon,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = menuScheme.onSurfaceContainer,
                                            )
                                        }
                                    },
                                ),
                                optionSize = optionSize,
                                isSelected = false,
                                index = idx++,
                                modifier = itemWidthMod
                                    .graphicsLayer { alpha = topItemAlpha.value }
                                    .testTag(topTag),
                                groupKey = groupKey,
                                role = Role.Button,
                                onSelectedIndexChange = {
                                    haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                                    dismiss?.invoke()
                                    expanded = false
                                    if (showingReposts) {
                                        if (onSelectAllComments != null) {
                                            onSelectAllComments()
                                        } else {
                                            onChange(CommentFilterState())
                                        }
                                    } else {
                                        repostSelectHandler()
                                    }
                                },
                            )
                        }
                        if (showAuthorOption) {
                            val authorSelected = !showingReposts && filter.authorOnly
                            val authorTint = if (authorSelected) menuScheme.primary else menuScheme.onSurfaceContainer
                            DropdownImpl(
                                item = DropdownItem(
                                    text = "仅看博主",
                                    icon = { iconModifier ->
                                        Box(iconModifier, contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = CoboIcons.ProfileOutline,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = authorTint,
                                            )
                                        }
                                    },
                                ),
                                optionSize = optionSize,
                                isSelected = authorSelected,
                                index = idx++,
                                modifier = itemWidthMod.testTag("comment_filter_author"),
                                groupKey = groupKey,
                                role = Role.Checkbox,
                                onSelectedIndexChange = {
                                    val next = if (showingReposts) true else !filter.authorOnly
                                    haptic.performHapticFeedback(
                                        if (next) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                                    )
                                    if (showingReposts) {
                                        onChange(CommentFilterState(authorOnly = true))
                                    } else {
                                        onChange(filter.copy(authorOnly = next))
                                    }
                                },
                            )
                        }
                        val imageSelected = !showingReposts && filter.imagesOnly
                        val imageTint = if (imageSelected) menuScheme.primary else menuScheme.onSurfaceContainer
                        DropdownImpl(
                            item = DropdownItem(
                                text = "仅看带图",
                                icon = { iconModifier ->
                                    Box(iconModifier, contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = CoboIcons.Image,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = imageTint,
                                        )
                                    }
                                },
                            ),
                            optionSize = optionSize,
                            isSelected = imageSelected,
                            index = idx++,
                            modifier = itemWidthMod.testTag("comment_filter_images"),
                            groupKey = groupKey,
                            role = Role.Checkbox,
                            onSelectedIndexChange = {
                                val next = if (showingReposts) true else !filter.imagesOnly
                                haptic.performHapticFeedback(
                                    if (next) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                                )
                                if (showingReposts) {
                                    onChange(CommentFilterState(imagesOnly = true))
                                } else {
                                    onChange(filter.copy(imagesOnly = next))
                                }
                            },
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = filterActive,
            enter = fadeIn(tween(180, easing = LinearOutSlowInEasing)) +
                scaleIn(tween(180, easing = LinearOutSlowInEasing), initialScale = 0.72f) +
                expandHorizontally(tween(180, easing = LinearOutSlowInEasing)),
            exit = fadeOut(tween(130, easing = FastOutLinearInEasing)) +
                scaleOut(tween(130, easing = FastOutLinearInEasing), targetScale = 0.72f) +
                shrinkHorizontally(tween(130, easing = FastOutLinearInEasing)),
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(scheme.onSurface.copy(alpha = if (dark) 0.14f else 0.08f))
                    .clickable(role = Role.Button) {
                        haptic.performHapticFeedback(HapticFeedbackType.ToggleOff)
                        expanded = false
                        onChange(CommentFilterState())
                    }
                    .semantics { contentDescription = "清除筛选" }
                    .testTag("comment_filter_clear"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = COUIIcons.Close,
                    contentDescription = null,
                    tint = scheme.onSurfaceSecondary,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
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

/**
 * 详情页底部四按钮悬浮胶囊：直接复用 [MetaBallSurface] 渲染与顶栏 [HighlightedIconButton]
 * 同一套透明模糊材质、材质描边混色（`blendEdge`）以及深色焦散 / 浅色软投影。
 */
@Composable
private fun DetailFloatingToolbar(
    modifier: Modifier = Modifier,
    barrierEnabled: Boolean = true,
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
        Box(modifier = if (barrierEnabled) Modifier.popupBottomBarrier() else Modifier) {
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
                continuousCurve = squircleEnabled,
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

private fun detailFavoritePopupPositionProvider(): PopupPositionProvider =
    object : PopupPositionProvider {
        private val margins = PaddingValues(
            horizontal = NavigationPopupMenuDefaults.HorizontalMargin,
            vertical = NavigationPopupMenuDefaults.VerticalMargin,
        )

        override fun calculatePosition(
            anchorBounds: IntRect,
            windowBounds: IntRect,
            layoutDirection: LayoutDirection,
            popupContentSize: IntSize,
            popupMargin: IntRect,
            alignment: PopupPositionProvider.Align,
        ): IntOffset {
            val minX = windowBounds.left + popupMargin.left
            val maxX = (windowBounds.right - popupContentSize.width - popupMargin.right)
                .coerceAtLeast(minX)
            val desiredX = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
            val minY = windowBounds.top + popupMargin.top
            val maxY = (windowBounds.bottom - popupContentSize.height - popupMargin.bottom)
                .coerceAtLeast(minY)
            val offsetY = (anchorBounds.top - popupContentSize.height - popupMargin.bottom)
                .coerceIn(minY, maxY)
            return IntOffset(
                x = desiredX.coerceIn(minX, maxX),
                y = offsetY,
            )
        }

        override fun getMargins(): PaddingValues = margins
    }

@Composable
private fun DeletedArchiveNoticeBanner(
    title: String,
    message: String,
    isErrorTone: Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = COUITheme.colorScheme
    val accent = if (isErrorTone) scheme.error else scheme.primary
    val bg = accent.copy(alpha = 0.10f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = COUIIcons.Delete,
            contentDescription = title,
            tint = accent,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = accent,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = message,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = scheme.onSurfaceSecondary,
            )
        }
    }
}
