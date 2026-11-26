package com.cobo.app.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cobo.app.App
import com.cobo.app.data.MeIconStyle
import com.cobo.app.ui.AppBarSurface
import com.cobo.app.ui.PinnedBarDensity
import com.cobo.app.ui.components.BackIconButton
import com.cobo.app.ui.components.EmptyState
import com.cobo.app.ui.components.navBottomPadding
import com.cobo.app.ui.glassBarContentTop
import com.cobo.app.ui.isDarkTheme
import com.cobo.app.ui.pageContainerColor
import com.cobo.app.ui.rememberListScrollFraction
import io.github.suqi8.coui.kmp.anim.COUIEase
import io.github.suqi8.coui.kmp.anim.COUIMoveEase
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.BasicComponentDefaults
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.DropdownArrowEndAction
import io.github.suqi8.coui.kmp.basic.DropdownDefaults
import io.github.suqi8.coui.kmp.basic.DropdownEntry
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Switch
import io.github.suqi8.coui.kmp.basic.SwitchDefaults
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.basic.TopAppBarSearchBar
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor
import io.github.suqi8.coui.kmp.basic.rememberPreciseClickState
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.basic.ArrowRight
import io.github.suqi8.coui.kmp.popup.OverlayDropdownPopup
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.preference.ArrowPreferenceDefaults
import io.github.suqi8.coui.kmp.preference.OverlayDropdownPreference
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic

/** 一条支持搜索过滤的设置项，渲染时按过滤后的实际位置动态分配 [CardListPosition]。 */
internal class SettingsEntry(
    val id: String,
    val title: String,
    val summary: String = "",
    val keywords: String = "",
    val content: @Composable (CardListPosition) -> Unit,
) {
    fun matchScore(groupTitle: String, terms: List<String>): Int =
        SettingsSearchMatcher.scoreEntry(this, groupTitle, terms)
}

internal data class SettingsGroupSection(
    val key: String,
    val title: String,
    val entries: List<SettingsEntry>,
    /** 与前一个卡片共享分组标题（例如「其他」下独立的退出卡片）时传入同一 headerGroupKey，避免重复渲染分组标题。 */
    val headerGroupKey: String = key,
    /** 关联的一级分组枚举，用于在 ColorOS 风格搜索结果卡片顶部绘制对应分组图标。 */
    val group: SettingsGroup? = null,
)

/** 搜索结果渲染上下文：为空时走普通设置项渲染，非空时启用 ColorOS 风格关键词高亮与路径副标题。 */
internal data class SettingsSearchRenderContext(
    val terms: List<String> = emptyList(),
    val breadcrumb: String = "",
) {
    val isSearchResult: Boolean get() = terms.isNotEmpty()
}

internal val LocalSettingsSearchRenderContext = compositionLocalOf { SettingsSearchRenderContext() }

internal fun parseSettingsSearchTerms(query: String): List<String> =
    query.trim().split(SETTINGS_SEARCH_WHITESPACE_REGEX).filter { it.isNotEmpty() }

private val SETTINGS_SEARCH_WHITESPACE_REGEX = Regex("\\s+")

/** ColorOS 17 系统设置搜索结果面板动效参数（对齐 `HomepageSearchFeature.startShowHideAnimation`）。 */
private val SearchResultRiseDistance = 15.dp
private const val SearchResultEnterDurationMillis = 300
private const val SearchResultExitDurationMillis = 150
private const val SearchResultItemFadeInMillis = 220

internal fun cardPositionFor(index: Int, total: Int): CardListPosition = when {
    total <= 1 -> CardListPosition.Full
    index == 0 -> CardListPosition.Head
    index == total - 1 -> CardListPosition.Tail
    else -> CardListPosition.Middle
}

/**
 * 搜索结果卡片顶部已有内嵌「图标 + 分组名」头部行，因此第一条不再追加 Head 顶部圆角边距，
 * 仅由末条承担 Tail 底部圆角边距，且条目之间按 COUI 规范保留细分隔线。
 */
internal fun searchResultCardPositionFor(index: Int, total: Int): CardListPosition = when {
    index == total - 1 -> CardListPosition.Tail
    else -> CardListPosition.Middle
}

/**
 * 按相关性分值过滤并重排设置分组与条目；查询为空时原样返回。
 *
 * 非空查询时对齐 ColorOS 系统设置搜索：共享同一 [SettingsGroupSection.headerGroupKey] 的子卡片
 * （例如「其他」和「退出登录」）会合并到同一张分组结果卡片下，统一由组内最高命中分排序。
 */
internal fun filterSettingsGroups(
    groups: List<SettingsGroupSection>,
    terms: List<String>,
): List<SettingsGroupSection> {
    if (terms.isEmpty()) return groups

    class GroupBucket(
        val template: SettingsGroupSection,
        val scoredEntries: MutableList<Pair<SettingsEntry, Int>> = ArrayList(),
    )

    val buckets = LinkedHashMap<String, GroupBucket>()
    for (section in groups) {
        for (entry in section.entries) {
            val score = entry.matchScore(section.title, terms)
            if (score > 0) {
                val bucket = buckets.getOrPut(section.headerGroupKey) {
                    GroupBucket(template = section.copy(key = section.headerGroupKey))
                }
                bucket.scoredEntries.add(entry to score)
            }
        }
    }

    return buckets.values
        .map { bucket ->
            val sorted = bucket.scoredEntries.sortedByDescending { it.second }
            bucket.template.copy(entries = sorted.map { it.first }) to sorted.first().second
        }
        .sortedByDescending { it.second }
        .map { it.first }
}

/** 将命中的字符区间按 ColorOS 主强调色着色，支持原文、全拼、首字母、跳字与轻度纠错反推高亮。 */
internal fun buildHighlightedText(
    text: String,
    terms: List<String>,
    highlightColor: Color,
): AnnotatedString {
    val ranges = SettingsSearchMatcher.findHighlightRanges(text, terms)
    if (ranges.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        val style = SpanStyle(color = highlightColor)
        for (range in ranges) {
            addStyle(style, start = range.first, end = range.last + 1)
        }
    }
}

@Composable
internal fun SettingsGroupCard(
    entries: List<SettingsEntry>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = CardDefaults.GroupCornerRadius,
        insideMargin = PaddingValues(0.dp),
        weight = CardDefaults.GroupWeight,
    ) {
        entries.forEachIndexed { index, entry ->
            entry.content(cardPositionFor(index, entries.size))
        }
    }
}

/**
 * ColorOS 17 设置搜索结果分组卡片（对齐 `SearchResultsGroupAdapter` + `search_intent_item_head/body/foot/full.xml`）：
 * 卡片顶部内嵌「16dp 分组图标 + 8dp 间距 + 12sp 次要色分组名」头部，紧接该分组命中的设置项，
 * 并通过 [LocalSettingsSearchRenderContext] 自动为每条结果的标题和副标题提供主题色关键词高亮。
 */
@Composable
internal fun SettingsSearchResultGroupCard(
    section: SettingsGroupSection,
    terms: List<String>,
    parentTitle: String,
    modifier: Modifier = Modifier,
) {
    val primaryColor = COUITheme.colorScheme.primary
    val headerText = remember(section.title, terms, primaryColor) {
        buildHighlightedText(section.title, terms, primaryColor)
    }
    val breadcrumb = remember(parentTitle, section.title) {
        if (parentTitle.isNotBlank() && parentTitle != section.title) {
            "$parentTitle > ${section.title}"
        } else {
            section.title
        }
    }
    val renderContext = remember(terms, breadcrumb) {
        SettingsSearchRenderContext(terms = terms, breadcrumb = breadcrumb)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("settings_search_group_${section.headerGroupKey}"),
        cornerRadius = CardDefaults.GroupCornerRadius,
        insideMargin = PaddingValues(0.dp),
        weight = CardDefaults.GroupWeight,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            section.group?.let { settingsGroup ->
                SettingsSearchGroupIcon(settingsGroup)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = headerText,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
                color = COUITheme.colorScheme.onSurfaceSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        CompositionLocalProvider(LocalSettingsSearchRenderContext provides renderContext) {
            section.entries.forEachIndexed { index, entry ->
                entry.content(searchResultCardPositionFor(index, section.entries.size))
            }
        }
    }
}

@Composable
private fun SettingsSearchGroupIcon(group: SettingsGroup) {
    val scheme = COUITheme.colorScheme
    val tint = when (App.settings.meIconStyle) {
        MeIconStyle.Monochrome -> scheme.onBackground
        MeIconStyle.Colorful -> group.hubTint().resolve(isDarkTheme())
        MeIconStyle.Accent -> scheme.primary
    }
    Icon(
        imageVector = group.hubIcon(),
        contentDescription = null,
        modifier = Modifier.size(16.dp),
        tint = tint,
    )
}

/**
 * ColorOS 17 设置搜索结果层：
 * - 进场采用 `HomepageSearchFeature.startShowHideAnimation` 精确曲线（15dp 上浮 + 300ms COUIMoveEase 淡入）；
 * - 退场采用 150ms COUIEase 淡出，并冻结退场前的最后一次结果快照防止清空时闪烁；
 * - 结果列表 ↔ 空态之间同样以 300ms COUIMoveEase / 150ms COUIEase 交叉过渡；
 * - 连续输入实时过滤时，分组卡片使用 `animateItem` 平滑重排，组间 16dp、首组顶距 12dp（对齐 `SearchResultsAdapter`）。
 */
@Composable
internal fun SettingsSearchResultsLayer(
    visible: Boolean,
    query: String,
    terms: List<String>,
    filteredGroups: List<SettingsGroupSection>,
    parentTitle: String,
    listState: LazyListState,
    topBarHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val risePx = with(density) { SearchResultRiseDistance.roundToPx() }

    // 退场动画期间保持最后一次非空查询的快照，避免清空关键词瞬间内容跳变。
    val snapshotQuery = remember { mutableStateOf(query) }
    val snapshotTerms = remember { mutableStateOf(terms) }
    val snapshotGroups = remember { mutableStateOf(filteredGroups) }
    if (visible) {
        snapshotQuery.value = query
        snapshotTerms.value = terms
        snapshotGroups.value = filteredGroups
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxSize(),
        enter = fadeIn(
            animationSpec = tween(SearchResultEnterDurationMillis, easing = COUIMoveEase),
        ) + slideInVertically(
            animationSpec = tween(SearchResultEnterDurationMillis, easing = COUIMoveEase),
            initialOffsetY = { risePx },
        ),
        exit = fadeOut(
            animationSpec = tween(SearchResultExitDurationMillis, easing = COUIEase),
        ),
    ) {
        val displayQuery = if (visible) query else snapshotQuery.value
        val displayTerms = if (visible) terms else snapshotTerms.value
        val displayGroups = if (visible) filteredGroups else snapshotGroups.value
        val hasResults = displayGroups.isNotEmpty()

        AnimatedContent(
            targetState = hasResults,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                val enterSpec = if (targetState) {
                    fadeIn(tween(SearchResultEnterDurationMillis, easing = COUIMoveEase)) +
                        slideInVertically(
                            animationSpec = tween(SearchResultEnterDurationMillis, easing = COUIMoveEase),
                            initialOffsetY = { risePx },
                        )
                } else {
                    fadeIn(tween(SearchResultEnterDurationMillis, easing = COUIMoveEase))
                }
                enterSpec togetherWith fadeOut(tween(SearchResultExitDurationMillis, easing = COUIEase))
            },
            label = "settings_search_panel_switch",
        ) { showingResults ->
            if (!showingResults) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            top = glassBarContentTop(topBarHeight, 12.dp),
                            bottom = 24.dp + navBottomPadding(),
                        ),
                ) {
                    EmptyState("未找到与「$displayQuery」相关的设置")
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .scrollEndHaptic()
                        .testTag("settings_search_results_list"),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        top = glassBarContentTop(topBarHeight, 12.dp),
                        end = 12.dp,
                        bottom = 24.dp + navBottomPadding(),
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    displayGroups.forEach { group ->
                        item(key = "search-group-${group.headerGroupKey}") {
                            SettingsSearchResultGroupCard(
                                section = group,
                                terms = displayTerms,
                                parentTitle = parentTitle,
                                modifier = Modifier.animateItem(
                                    fadeInSpec = tween(SearchResultItemFadeInMillis, easing = COUIMoveEase),
                                    placementSpec = spring<IntOffset>(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow,
                                    ),
                                    fadeOutSpec = tween(SearchResultExitDurationMillis, easing = COUIEase),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 支持搜索高亮的箭头设置项：
 * 普通浏览态直通原生 [ArrowPreference]；搜索结果态使用 COUI [BasicComponent] 渲染带强调色高亮的标题与摘要/路径。
 */
@Composable
internal fun SettingsArrowRow(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    endActions: @Composable RowScope.() -> Unit = {},
    cardListPosition: CardListPosition = CardListPosition.None,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val searchCtx = LocalSettingsSearchRenderContext.current
    if (!searchCtx.isSearchResult) {
        ArrowPreference(
            title = title,
            modifier = modifier,
            summary = summary,
            endActions = endActions,
            cardListPosition = cardListPosition,
            onClick = onClick,
            enabled = enabled,
        )
        return
    }

    val primaryColor = COUITheme.colorScheme.primary
    val titleColor = BasicComponentDefaults.titleColor()
    val summaryColor = BasicComponentDefaults.summaryColor()
    val resolvedSummary = summary?.takeIf { it.isNotBlank() } ?: searchCtx.breadcrumb.takeIf { it.isNotBlank() }
    val highlightedTitle = remember(title, searchCtx.terms, primaryColor) {
        buildHighlightedText(title, searchCtx.terms, primaryColor)
    }
    val highlightedSummary = remember(resolvedSummary, searchCtx.terms, primaryColor) {
        resolvedSummary?.let { buildHighlightedText(it, searchCtx.terms, primaryColor) }
    }
    val arrowTint = if (enabled) {
        COUITheme.colorScheme.onSurfaceVariantActions
    } else {
        COUITheme.colorScheme.disabledSecondaryVariant
    }

    BasicComponent(
        modifier = modifier,
        cardListPosition = cardListPosition,
        onClick = onClick,
        enabled = enabled,
        endActions = {
            Row(
                modifier = Modifier
                    .padding(end = 4.dp)
                    .align(Alignment.CenterVertically)
                    .weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically,
                content = endActions,
            )
            Image(
                modifier = Modifier
                    .size(width = 12.dp, height = 24.dp)
                    .align(Alignment.CenterVertically),
                imageVector = COUIIcons.Basic.ArrowRight,
                contentDescription = null,
                colorFilter = remember(arrowTint) { ColorFilter.tint(arrowTint) },
            )
        },
    ) {
        Text(
            text = highlightedTitle,
            style = COUITheme.textStyles.headline1,
            fontWeight = FontWeight.Medium,
            color = if (enabled) titleColor.color else titleColor.disabledColor,
        )
        if (highlightedSummary != null) {
            Text(
                text = highlightedSummary,
                style = COUITheme.textStyles.footnote1,
                color = if (enabled) summaryColor.color else summaryColor.disabledColor,
            )
        }
    }
}

/**
 * 支持搜索高亮的开关设置项：
 * 普通浏览态直通原生 [SwitchPreference]；搜索结果态使用 COUI [BasicComponent] + [Switch] 保留直接切换能力与关键词高亮。
 */
@Composable
internal fun SettingsSwitchRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    cardListPosition: CardListPosition = CardListPosition.None,
    enabled: Boolean = true,
) {
    val searchCtx = LocalSettingsSearchRenderContext.current
    if (!searchCtx.isSearchResult) {
        SwitchPreference(
            checked = checked,
            onCheckedChange = onCheckedChange,
            title = title,
            summary = summary,
            modifier = modifier,
            cardListPosition = cardListPosition,
            enabled = enabled,
        )
        return
    }

    val currentOnCheckedChange by rememberUpdatedState(onCheckedChange)
    val primaryColor = COUITheme.colorScheme.primary
    val titleColor = BasicComponentDefaults.titleColor()
    val summaryColor = BasicComponentDefaults.summaryColor()
    val resolvedSummary = summary?.takeIf { it.isNotBlank() } ?: searchCtx.breadcrumb.takeIf { it.isNotBlank() }
    val highlightedTitle = remember(title, searchCtx.terms, primaryColor) {
        buildHighlightedText(title, searchCtx.terms, primaryColor)
    }
    val highlightedSummary = remember(resolvedSummary, searchCtx.terms, primaryColor) {
        resolvedSummary?.let { buildHighlightedText(it, searchCtx.terms, primaryColor) }
    }

    BasicComponent(
        modifier = modifier,
        cardListPosition = cardListPosition,
        role = Role.Switch,
        enabled = enabled,
        onClick = {
            if (enabled) currentOnCheckedChange(!checked)
        },
        endActions = {
            Switch(
                checked = checked,
                onCheckedChange = { currentOnCheckedChange(it) },
                enabled = enabled,
                colors = SwitchDefaults.switchColors(),
            )
        },
    ) {
        Text(
            text = highlightedTitle,
            style = COUITheme.textStyles.headline1,
            fontWeight = FontWeight.Medium,
            color = if (enabled) titleColor.color else titleColor.disabledColor,
        )
        if (highlightedSummary != null) {
            Text(
                text = highlightedSummary,
                style = COUITheme.textStyles.footnote1,
                color = if (enabled) summaryColor.color else summaryColor.disabledColor,
            )
        }
    }
}

/**
 * 支持搜索高亮的下拉选择设置项：
 * 普通浏览态直通原生 [OverlayDropdownPreference]；搜索结果态保留原生弹窗与定位并高亮标题/摘要。
 */
@Composable
internal fun SettingsDropdownRow(
    items: List<String>,
    selectedIndex: Int,
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    cardListPosition: CardListPosition = CardListPosition.None,
    enabled: Boolean = true,
    onSelectedIndexChange: (Int) -> Unit,
) {
    val searchCtx = LocalSettingsSearchRenderContext.current
    if (!searchCtx.isSearchResult) {
        OverlayDropdownPreference(
            items = items,
            selectedIndex = selectedIndex,
            title = title,
            summary = summary,
            modifier = modifier,
            cardListPosition = cardListPosition,
            enabled = enabled,
            onSelectedIndexChange = onSelectedIndexChange,
        )
        return
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isDropdownExpanded = remember { mutableStateOf(false) }
    val isHoldDown = remember { mutableStateOf(false) }
    val preciseClickState = rememberPreciseClickState()
    val hapticFeedback = LocalHapticFeedback.current
    val currentHapticFeedback by rememberUpdatedState(hapticFeedback)
    val entry = remember(items, selectedIndex, onSelectedIndexChange) {
        DropdownEntry(
            items.mapIndexed { index, item ->
                DropdownItem(
                    text = item,
                    selected = index == selectedIndex,
                    onClick = { onSelectedIndexChange(index) },
                )
            },
        )
    }

    val actualEnabled = enabled && items.isNotEmpty()
    val primaryColor = COUITheme.colorScheme.primary
    val titleColor = BasicComponentDefaults.titleColor()
    val summaryColor = BasicComponentDefaults.summaryColor()
    val resolvedSummary = summary?.takeIf { it.isNotBlank() } ?: searchCtx.breadcrumb.takeIf { it.isNotBlank() }
    val highlightedTitle = remember(title, searchCtx.terms, primaryColor) {
        buildHighlightedText(title, searchCtx.terms, primaryColor)
    }
    val highlightedSummary = remember(resolvedSummary, searchCtx.terms, primaryColor) {
        resolvedSummary?.let { buildHighlightedText(it, searchCtx.terms, primaryColor) }
    }
    val actionColor = if (actualEnabled) {
        COUITheme.colorScheme.onSurfaceVariantActions
    } else {
        COUITheme.colorScheme.disabledOnSecondaryVariant
    }
    val valueColor = if (actualEnabled) {
        COUITheme.colorScheme.onSurfaceSecondary
    } else {
        COUITheme.colorScheme.disabledOnSecondaryVariant
    }

    BasicComponent(
        modifier = modifier.preciseClickAnchor(preciseClickState),
        interactionSource = interactionSource,
        cardListPosition = cardListPosition,
        role = Role.DropdownList,
        holdDownState = isHoldDown.value,
        enabled = actualEnabled,
        onClick = {
            if (actualEnabled) {
                val next = !isDropdownExpanded.value
                isDropdownExpanded.value = next
                if (next) {
                    isHoldDown.value = true
                    currentHapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                } else {
                    isHoldDown.value = false
                }
            }
        },
        endActions = {
            val selectedText = entry.items.firstOrNull { it.selected }?.text
            if (!selectedText.isNullOrEmpty()) {
                Text(
                    text = selectedText,
                    modifier = Modifier
                        .widthIn(max = ArrowPreferenceDefaults.StatusTextMaxWidth)
                        .padding(start = 8.dp, end = 4.dp)
                        .align(Alignment.CenterVertically)
                        .weight(1f, fill = false),
                    fontSize = COUITheme.textStyles.body2.fontSize,
                    color = valueColor,
                    textAlign = TextAlign.End,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DropdownArrowEndAction(actionColor = actionColor)
            if (items.isNotEmpty()) {
                OverlayDropdownPopup(
                    entry = entry,
                    show = isDropdownExpanded.value,
                    onDismiss = {
                        isDropdownExpanded.value = false
                        isHoldDown.value = false
                    },
                    onDismissFinished = { isHoldDown.value = false },
                    maxHeight = null,
                    dropdownColors = DropdownDefaults.dropdownColors(),
                    renderInRootScaffold = true,
                    collapseOnSelection = true,
                    preciseClickState = preciseClickState,
                )
            }
        },
    ) {
        Text(
            text = highlightedTitle,
            style = COUITheme.textStyles.headline1,
            fontWeight = FontWeight.Medium,
            color = if (actualEnabled) titleColor.color else titleColor.disabledColor,
        )
        if (highlightedSummary != null) {
            Text(
                text = highlightedSummary,
                style = COUITheme.textStyles.footnote1,
                color = if (actualEnabled) summaryColor.color else summaryColor.disabledColor,
            )
        }
    }
}

/**
 * 设置页顶部栏：默认展示返回与标题、右上角搜索按钮，点击后展开 COUI 搜索栏实时过滤设置项。
 */
@Composable
internal fun SettingsSearchTopBar(
    title: String,
    query: String,
    expanded: Boolean,
    onQueryChange: (String) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val pageColor = pageContainerColor()
    PinnedBarDensity {
        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val barRowHeight = TopAppBarDefaults.SmallTopAppBarCenterHeight
        val pageBarHeight = statusBarTop + barRowHeight
        Box(modifier.fillMaxWidth()) {
            AppBarSurface(
                modifier = Modifier.fillMaxWidth(),
                containerColor = pageColor,
                scrollFraction = rememberListScrollFraction(listState),
            ) {
                Box(Modifier.fillMaxWidth().height(pageBarHeight))
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = statusBarTop)
                    .height(barRowHeight),
                contentAlignment = Alignment.Center,
            ) {
                TopAppBarSearchBar(
                    query = query,
                    onQueryChange = onQueryChange,
                    expanded = expanded,
                    onExpandedChange = { active ->
                        if (!active) onQueryChange("")
                        onExpandedChange(active)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_search_bar"),
                    placeholder = "搜索设置、拼音或首字母",
                    cancelText = "取消",
                    onSearch = { /* 输入即实时过滤，无需额外提交动作 */ },
                    onCancel = {
                        onQueryChange("")
                        onExpandedChange(false)
                    },
                    searchButtonContentDescription = "搜索设置",
                    gradientColor = Color.Transparent,
                    titleContent = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BackIconButton(onClick = onBack)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = title,
                                fontSize = with(LocalDensity.current) { TopAppBarDefaults.ToolbarTitleTextSize.toSp() },
                                fontWeight = FontWeight(550),
                                color = COUITheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = false,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            SettingsSearchClearButton(onClick = { onQueryChange("") })
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsSearchClearButton(onClick: () -> Unit) {
    val clearTint = COUITheme.colorScheme.onSurface.copy(alpha = 0.28f)
    Canvas(
        modifier = Modifier
            .size(36.dp)
            .semantics { contentDescription = "清除" }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
    ) {
        val unit = size.width / 36f
        drawCircle(clearTint, radius = 9f * unit)
        val path = Path().apply {
            moveTo(18f, 16.868f)
            lineTo(15.031f, 13.898f)
            lineTo(13.899f, 15.03f)
            lineTo(16.869f, 18f)
            lineTo(13.898f, 20.97f)
            lineTo(15.03f, 22.101f)
            lineTo(18f, 19.131f)
            lineTo(20.97f, 22.101f)
            lineTo(22.102f, 20.969f)
            lineTo(19.132f, 18f)
            lineTo(22.101f, 15.03f)
            lineTo(20.969f, 13.899f)
            close()
        }
        withTransform({ scale(unit, unit, Offset.Zero) }) {
            drawPath(path, Color.White)
        }
    }
}
