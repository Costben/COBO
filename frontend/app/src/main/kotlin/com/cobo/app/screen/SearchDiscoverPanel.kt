@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    io.github.suqi8.coui.kmp.core.ExperimentalCOUIApi::class,
)

package com.cobo.app.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cobo.app.App
import com.cobo.app.data.HotItem
import com.cobo.app.ui.components.EmptyState
import com.cobo.app.ui.components.ErrorState
import com.cobo.app.ui.components.LoadingState
import io.github.suqi8.coui.kmp.anim.COUIInEase
import io.github.suqi8.coui.kmp.basic.CapsuleOutlineShape
import io.github.suqi8.coui.kmp.basic.ChipColors
import io.github.suqi8.coui.kmp.basic.ChipDefaults
import io.github.suqi8.coui.kmp.basic.FlowLayout
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.SearchHistoryDeleteIcon
import io.github.suqi8.coui.kmp.basic.SearchHistoryExpandIcon
import io.github.suqi8.coui.kmp.basic.SearchHistoryViewDefaults
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.basic.Close
import io.github.suqi8.coui.kmp.icon.extended.Search
import io.github.suqi8.coui.kmp.squircle.isSquircleEnabled
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Stable
class SearchDiscoverState {
    val hot = mutableStateListOf<HotItem>()
    var hotLoading by mutableStateOf(false)
        private set
    var hotError by mutableStateOf<String?>(null)
        private set
    var suggestions by mutableStateOf<List<String>>(emptyList())
        internal set
    var editingHistoryItem by mutableStateOf<String?>(null)

    fun clearEditing() {
        editingHistoryItem = null
    }

    suspend fun loadHot(force: Boolean = false) {
        if (hotLoading && !force) return
        hotLoading = true
        hotError = null
        try {
            val items = App.repo.hot(force)
            hot.clear()
            hot.addAll(items)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            hotError = t.message ?: "热搜加载失败"
        } finally {
            hotLoading = false
        }
    }

    suspend fun ensureHotLoaded() {
        if (hot.isEmpty() && !hotLoading) {
            loadHot(force = false)
        }
    }
}

@Composable
fun rememberSearchDiscoverState(): SearchDiscoverState = remember { SearchDiscoverState() }

@Composable
fun BindSearchSuggestionsEffect(
    state: SearchDiscoverState,
    query: String,
    active: Boolean,
    submitted: String = "",
) {
    LaunchedEffect(state, query, active, submitted) {
        val keyword = query.trim()
        if (!active || keyword.isBlank() || keyword == submitted) {
            state.suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(250)
        state.suggestions = try {
            App.repo.suggest(keyword)
        } catch (c: CancellationException) {
            throw c
        } catch (_: Throwable) {
            emptyList()
        }
    }
}

fun Modifier.dismissHistoryEditingOnTap(state: SearchDiscoverState): Modifier =
    this.pointerInput(state) {
        detectTapGestures(
            onTap = {
                if (state.editingHistoryItem != null) {
                    state.clearEditing()
                }
            },
        )
    }

fun LazyListScope.searchDiscoverItems(
    state: SearchDiscoverState,
    suggesting: Boolean,
    history: List<String>,
    onSelectKeyword: (String) -> Unit,
    onRetryHot: () -> Unit,
) {
    if (suggesting) {
        itemsIndexed(state.suggestions, key = { index, term -> "${index}_$term" }) { _, term ->
            SuggestionRow(term) {
                state.clearEditing()
                onSelectKeyword(term)
            }
        }
        return
    }
    if (history.isNotEmpty()) {
        item(key = "search_history_panel") {
            CoboSearchHistoryPanel(
                items = history,
                editingItem = state.editingHistoryItem,
                onItemClick = { keyword -> onSelectKeyword(keyword) },
                onStartEditing = { keyword -> state.editingHistoryItem = keyword },
                onCancelEditing = { state.clearEditing() },
                onRemoveItem = { keyword ->
                    if (state.editingHistoryItem == keyword) state.clearEditing()
                    App.settings.removeSearchHistory(keyword)
                },
                onDeleteAllClick = {
                    state.clearEditing()
                    App.settings.clearSearchHistory()
                },
                title = "搜索历史",
                deleteIconDescription = "清空搜索历史",
            )
        }
    }
    item(key = "hot_search_header") {
        val horizontalPadding = searchHorizontalPadding()
        Text(
            "微博热搜",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(
                start = horizontalPadding,
                end = horizontalPadding,
                top = 8.dp,
                bottom = 8.dp,
            ),
        )
    }
    if (state.hot.isEmpty()) {
        item(key = "hot_search_state") {
            val horizontalPadding = searchHorizontalPadding()
            Box(Modifier.padding(horizontal = horizontalPadding)) {
                when {
                    state.hotLoading -> LoadingState()
                    state.hotError != null -> ErrorState(state.hotError!!, onRetry = onRetryHot)
                    else -> EmptyState("暂无热搜")
                }
            }
        }
    } else {
        items(state.hot, key = { it.rank.toString() + it.title }) { item ->
            HotSearchRow(item) {
                state.clearEditing()
                onSelectKeyword(item.title)
            }
        }
    }
}

@Composable
fun HotSearchRow(
    item: HotItem,
    horizontalPadding: androidx.compose.ui.unit.Dp = searchHorizontalPadding(),
    onClick: () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = horizontalPadding, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            item.rank.toString(),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (item.rank <= 3) scheme.primary else scheme.onSurfaceSecondary,
            modifier = Modifier.width(28.dp),
        )
        Text(item.title, fontSize = 14.sp, maxLines = 1, modifier = Modifier.weight(1f))
        if (item.label.isNotBlank()) {
            Text(item.label, fontSize = 10.sp, color = scheme.onSurfaceSecondary)
        }
    }
}

@Composable
fun SuggestionRow(
    term: String,
    horizontalPadding: androidx.compose.ui.unit.Dp = searchHorizontalPadding(),
    onClick: () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = horizontalPadding, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            COUIIcons.Search,
            null,
            Modifier.size(16.dp),
            tint = scheme.onSurfaceSecondary,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            term,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

private val HistoryEase = CubicBezierEasing(0.3f, 0f, 0.1f, 1f)
private val HistoryPressSpring = spring<Float>(dampingRatio = 1f, stiffness = 438.65f)
private val HistoryChipPressedLight = Color(0x1F000000)
private val HistoryChipPressedDark = Color(0x33FFFFFF)

private fun Density.historyChipPressScale(size: Size): Float {
    val area = size.width.toDp().value * size.height.toDp().value
    val minArea = 48f * 48f
    val maxArea = 328f * 220f
    return when {
        area <= minArea -> 0.92f
        area >= maxArea -> 0.98f
        else -> {
            val t = COUIInEase.transform((area - minArea) / (maxArea - minArea))
            0.92f + (0.98f - 0.92f) * t
        }
    }
}

@Composable
fun CoboSearchHistoryPanel(
    items: List<String>,
    editingItem: String?,
    onItemClick: (String) -> Unit,
    onStartEditing: (String) -> Unit,
    onCancelEditing: () -> Unit,
    onRemoveItem: (String) -> Unit,
    onDeleteAllClick: () -> Unit,
    title: String,
    deleteIconDescription: String,
    expandable: Boolean = true,
    maxRowFolded: Int = SearchHistoryViewDefaults.MaxRowFolded,
    maxRowUnfolded: Int = SearchHistoryViewDefaults.MaxRowUnfolded,
) {
    val scope = rememberCoroutineScope()
    val viewAlpha = remember { Animatable(1f) }
    var clearingAll by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    val colors = SearchHistoryViewDefaults.colors()
    val canExpand = expandable && maxRowFolded < maxRowUnfolded
    val maxRows = if (canExpand && expanded) maxRowUnfolded else maxRowFolded
    val anyEditing = editingItem != null

    LaunchedEffect(items.isEmpty()) {
        if (items.isNotEmpty() && !clearingAll && viewAlpha.value < 1f) {
            viewAlpha.snapTo(1f)
        }
    }

    val horizontalPadding = searchHorizontalPadding()

    Column(
        modifier = Modifier
            .graphicsLayer { alpha = viewAlpha.value }
            .padding(
                start = horizontalPadding,
                top = SearchHistoryViewDefaults.PaddingTop,
                end = horizontalPadding,
                bottom = SearchHistoryViewDefaults.PaddingBottom,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = SearchHistoryViewDefaults.TitleMarginVertical),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                color = colors.titleColor,
                style = SearchHistoryViewDefaults.titleTextStyle(),
            )
            Box(
                modifier = Modifier
                    .padding(end = SearchHistoryViewDefaults.DeleteIconEndMargin)
                    .size(SearchHistoryViewDefaults.DeleteIconSize)
                    .clickable(enabled = !clearingAll) {
                        clearingAll = true
                        onCancelEditing()
                        scope.launch {
                            viewAlpha.animateTo(
                                targetValue = 0f,
                                animationSpec = tween(durationMillis = 300, easing = HistoryEase),
                            )
                            onDeleteAllClick()
                            clearingAll = false
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = SearchHistoryDeleteIcon,
                    contentDescription = deleteIconDescription,
                    modifier = Modifier.size(SearchHistoryViewDefaults.DeleteIconSize),
                    tint = colors.deleteIconColor,
                )
            }
        }

        FlowLayout(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = SearchHistoryViewDefaults.FlowMarginTop),
            itemSpacing = SearchHistoryViewDefaults.FlowItemSpacing,
            lineSpacing = SearchHistoryViewDefaults.FlowLineSpacing,
            maxRows = maxRows,
            expandable = false,
        ) {
            items.forEach { item ->
                key(item) {
                    SearchHistoryChip(
                        item = item,
                        isEditing = editingItem == item,
                        anyEditing = anyEditing,
                        onClick = { onItemClick(item) },
                        onStartEditing = { onStartEditing(item) },
                        onCancelEditing = onCancelEditing,
                        onCommitRemove = { onRemoveItem(item) },
                    )
                }
            }
        }

        if (canExpand) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(SearchHistoryViewDefaults.ExpandButtonSize)
                        .clickable { expanded = !expanded },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = SearchHistoryExpandIcon,
                        contentDescription = null,
                        modifier = Modifier
                            .size(SearchHistoryViewDefaults.ExpandIconSize)
                            .rotate(if (expanded) 180f else 0f),
                        tint = colors.expandIconColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchHistoryChip(
    item: String,
    isEditing: Boolean,
    anyEditing: Boolean,
    onClick: () -> Unit,
    onStartEditing: () -> Unit,
    onCancelEditing: () -> Unit,
    onCommitRemove: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val insideMargin = COUITheme.dimens.chip.insideMargin
    val chipColors: ChipColors = ChipDefaults.chipColors()
    val containerColor = chipColors.containerColor
    val contentColor = chipColors.contentColor

    var removing by remember(item) { mutableStateOf(false) }
    var committed by remember(item) { mutableStateOf(false) }
    val latestRemoving by rememberUpdatedState(removing)
    val latestCommitted by rememberUpdatedState(committed)
    val latestCommit by rememberUpdatedState(onCommitRemove)

    val removeProgress = remember(item) { Animatable(1f) }
    LaunchedEffect(removing) {
        if (removing) {
            removeProgress.animateTo(0f, tween(durationMillis = 250, easing = HistoryEase))
            committed = true
            latestCommit()
        }
    }
    DisposableEffect(item) {
        onDispose {
            if (latestRemoving && !latestCommitted) {
                latestCommit()
            }
        }
    }

    val reveal by animateFloatAsState(
        targetValue = if (isEditing) 1f else 0f,
        animationSpec = tween(durationMillis = 280, easing = HistoryEase),
        label = "historyChipDeleteReveal",
    )

    val interactionSource = remember { MutableInteractionSource() }
    val dismissInteractionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scaleProgress by animateFloatAsState(
        targetValue = if (!removing && isPressed) 1f else 0f,
        animationSpec = HistoryPressSpring,
        label = "historyChipPressScale",
    )
    val tintProgress = remember { Animatable(0f) }
    LaunchedEffect(removing, isPressed) {
        if (!removing && isPressed) {
            tintProgress.animateTo(1f, HistoryPressSpring)
        } else if (removing) {
            tintProgress.snapTo(0f)
        } else if (tintProgress.value > 0f) {
            tintProgress.animateTo(0f, HistoryPressSpring)
        }
    }

    val dark = COUITheme.colorScheme.background.luminance() < 0.5f
    val pressTint = if (dark) HistoryChipPressedDark else HistoryChipPressedLight
    val fillColor = if (tintProgress.value > 0f) {
        pressTint.copy(alpha = pressTint.alpha * tintProgress.value).compositeOver(containerColor)
    } else {
        containerColor
    }

    val squircleEnabled = isSquircleEnabled()
    val shape = remember(squircleEnabled) {
        CapsuleOutlineShape(cornerRadius = null, squircleEnabled = squircleEnabled)
    }
    var chipSize by remember { mutableStateOf(Size.Zero) }
    val pressEndRatio = remember(chipSize, density) {
        if (chipSize == Size.Zero) 0.94f else with(density) { historyChipPressScale(chipSize) }
    }

    val startPadding = insideMargin.calculateStartPadding(layoutDirection)
    val endPadding = insideMargin.calculateEndPadding(layoutDirection)
    val topPadding = insideMargin.calculateTopPadding()
    val bottomPadding = insideMargin.calculateBottomPadding()
    val dismissStartPadding = ChipDefaults.DismissIconStartPadding
    val dismissEndPadding = ChipDefaults.DismissIconEndPadding
    val dismissIconSize = ChipDefaults.DismissIconSize

    val removeScale = removeProgress.value
    Layout(
        content = {
            Box(
                modifier = Modifier
                    .onSizeChanged { chipSize = Size(it.width.toFloat(), it.height.toFloat()) }
                    .graphicsLayer {
                        val pressScale = 1f - (1f - pressEndRatio) * scaleProgress
                        val shrinkScale = 0.8f + 0.2f * removeScale
                        val scale = pressScale * shrinkScale
                        scaleX = scale
                        scaleY = scale
                        alpha = removeScale
                    }
                    .widthIn(max = ChipDefaults.MaxWidth)
                    .clip(shape)
                    .drawBehind {
                        drawOutline(shape.createOutline(size, layoutDirection, this), fillColor)
                    }
                    .combinedClickable(
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = !removing,
                        onClick = {
                            if (anyEditing) {
                                onCancelEditing()
                            } else {
                                onClick()
                            }
                        },
                        onLongClick = {
                            if (!removing) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onStartEditing()
                            }
                        },
                    ),
            ) {
                Row(
                    modifier = Modifier
                        .defaultMinSize(minHeight = SearchHistoryViewDefaults.ChipHeight)
                        .padding(start = startPadding, top = topPadding, bottom = bottomPadding),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item,
                        color = contentColor,
                        style = ChipDefaults.textStyle(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = ChipDefaults.TextMaxWidth),
                    )
                    Layout(
                        content = {
                            if (isEditing || reveal > 0.01f) {
                                Box(
                                    modifier = Modifier
                                        .size(dismissIconSize)
                                        .graphicsLayer {
                                            val f = reveal.coerceIn(0f, 1f)
                                            val iconScale = 0.3f + 0.7f * f
                                            scaleX = iconScale
                                            scaleY = iconScale
                                            alpha = f
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = COUIIcons.Basic.Close,
                                        contentDescription = null,
                                        modifier = Modifier.size(dismissIconSize),
                                        tint = contentColor,
                                    )
                                }
                            }
                            if (isEditing && !removing) {
                                Box(
                                    modifier = Modifier
                                        .size(ChipDefaults.DismissButtonSize)
                                        .testTag("search_history_delete")
                                        .semantics {
                                            contentDescription = "删除 $item"
                                        }
                                        .clickable(
                                            interactionSource = dismissInteractionSource,
                                            indication = null,
                                            role = Role.Button,
                                            onClick = { removing = true },
                                        ),
                                )
                            }
                        },
                    ) { measurables, constraints ->
                        val endPx = endPadding.roundToPx()
                        val fullSlotPx = dismissStartPadding.roundToPx() +
                            dismissIconSize.roundToPx() +
                            dismissEndPadding.roundToPx()
                        val slotWidth = (endPx + (fullSlotPx - endPx) * reveal)
                            .roundToInt()
                            .coerceIn(0, constraints.maxWidth)
                        val slotHeight = SearchHistoryViewDefaults.ChipHeight.roundToPx()
                        val loose = Constraints(
                            minWidth = 0,
                            maxWidth = constraints.maxWidth,
                            minHeight = 0,
                            maxHeight = constraints.maxHeight,
                        )
                        val iconPlaceable = if (isEditing || reveal > 0.01f) {
                            measurables.firstOrNull()?.measure(loose)
                        } else {
                            null
                        }
                        val hitPlaceable = if (isEditing && !removing) {
                            val hitIdx = if (iconPlaceable != null) 1 else 0
                            measurables.getOrNull(hitIdx)?.measure(loose)
                        } else {
                            null
                        }
                        layout(slotWidth, slotHeight) {
                            val iconX = (dismissStartPadding.toPx() * reveal).roundToInt()
                            iconPlaceable?.let { icon ->
                                val iconY = (slotHeight - icon.height) / 2
                                icon.place(iconX, iconY)
                                hitPlaceable?.let { hit ->
                                    val hitX = iconX + (icon.width - hit.width) / 2
                                    val hitY = (slotHeight - hit.height) / 2
                                    hit.place(hitX, hitY)
                                }
                            }
                        }
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val placeable = measurables.single().measure(constraints)
        val width = if (removeScale >= 0.999f) {
            placeable.width
        } else {
            (placeable.width * removeScale).roundToInt().coerceAtLeast(0)
        }
        layout(width, placeable.height) {
            placeable.place((width - placeable.width) / 2, 0)
        }
    }
}
