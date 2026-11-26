package com.cobo.app.screen

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.SearchFilterResultsContainer
import io.github.suqi8.coui.kmp.basic.SearchResultGroupCard
import io.github.suqi8.coui.kmp.basic.SearchResultGroupCardDefaults
import io.github.suqi8.coui.kmp.basic.SearchTextMatcher
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.basic.TopAppBarSearchBar
import io.github.suqi8.coui.kmp.preference.ArrowPreference
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
    fun matchScore(groupTitle: String, terms: List<String>): Int = SearchTextMatcher.scoreItem(
        title = title,
        terms = terms,
        summary = summary,
        keywords = keywords,
        groupTitle = groupTitle,
    )
}

internal data class SettingsGroupSection(
    val key: String,
    val title: String,
    val entries: List<SettingsEntry>,
    /** 与前一个卡片共享分组标题（例如「其他」下独立的退出卡片）时传入同一 headerGroupKey，避免重复渲染分组标题。 */
    val headerGroupKey: String = key,
    /** 关联的一级分组枚举，用于在 [SearchResultGroupCard] 顶部绘制对应分组图标。 */
    val group: SettingsGroup? = null,
)

/** 搜索结果渲染上下文：为空时走普通设置项渲染，非空时启用 COUI 关键词高亮与路径副标题。 */
internal data class SettingsSearchRenderContext(
    val terms: List<String> = emptyList(),
    val breadcrumb: String = "",
) {
    val isSearchResult: Boolean get() = terms.isNotEmpty()
}

internal val LocalSettingsSearchRenderContext = compositionLocalOf { SettingsSearchRenderContext() }

internal fun parseSettingsSearchTerms(query: String): List<String> = SearchTextMatcher.parseTerms(query)

internal fun cardPositionFor(index: Int, total: Int): CardListPosition = when {
    total <= 1 -> CardListPosition.Full
    index == 0 -> CardListPosition.Head
    index == total - 1 -> CardListPosition.Tail
    else -> CardListPosition.Middle
}

internal fun searchResultCardPositionFor(index: Int, total: Int): CardListPosition =
    SearchResultGroupCardDefaults.itemPositionFor(index, total)

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
 * 复用 COUI [SearchResultGroupCard] 展示 ColorOS 17 设置风格分组搜索结果卡片。
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
        SearchTextMatcher.highlightText(section.title, terms, primaryColor)
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

    SearchResultGroupCard(
        title = headerText,
        modifier = modifier.testTag("settings_search_group_${section.headerGroupKey}"),
        icon = section.group?.let { settingsGroup ->
            { SettingsSearchGroupIcon(settingsGroup) }
        },
    ) {
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
        modifier = Modifier.size(SearchResultGroupCardDefaults.HeaderIconSize),
        tint = tint,
    )
}

private data class SettingsSearchSnapshot(
    val query: String,
    val terms: List<String>,
    val groups: List<SettingsGroupSection>,
)

/**
 * 复用 COUI [SearchFilterResultsContainer] 驱动 ColorOS 17 设置搜索结果层进退场与空态过渡。
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
    val snapshot = remember(query, terms, filteredGroups) {
        SettingsSearchSnapshot(query = query, terms = terms, groups = filteredGroups)
    }

    SearchFilterResultsContainer(
        visible = visible,
        state = snapshot,
        isEmpty = { it.groups.isEmpty() },
        modifier = modifier,
        emptyContent = { current ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = glassBarContentTop(topBarHeight, SearchResultGroupCardDefaults.FirstCardTopSpacing),
                        bottom = 24.dp + navBottomPadding(),
                    ),
            ) {
                EmptyState("未找到与「${current.query}」相关的设置")
            }
        },
        resultsContent = { current ->
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .scrollEndHaptic()
                    .testTag("settings_search_results_list"),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    top = glassBarContentTop(topBarHeight, SearchResultGroupCardDefaults.FirstCardTopSpacing),
                    end = 12.dp,
                    bottom = 24.dp + navBottomPadding(),
                ),
                verticalArrangement = Arrangement.spacedBy(SearchResultGroupCardDefaults.CardSpacing),
            ) {
                current.groups.forEach { group ->
                    item(key = "search-group-${group.headerGroupKey}") {
                        SettingsSearchResultGroupCard(
                            section = group,
                            terms = current.terms,
                            parentTitle = parentTitle,
                            modifier = Modifier.animateItem(
                                fadeInSpec = SearchResultGroupCardDefaults.itemFadeInSpec(),
                                placementSpec = SearchResultGroupCardDefaults.itemPlacementSpec(),
                                fadeOutSpec = SearchResultGroupCardDefaults.itemFadeOutSpec(),
                            ),
                        )
                    }
                }
            }
        },
    )
}

/** 支持搜索高亮的箭头设置项，直接复用 COUI [ArrowPreference] 的 String / AnnotatedString 重载。 */
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
    val resolvedSummary = summary?.takeIf { it.isNotBlank() } ?: searchCtx.breadcrumb.takeIf { it.isNotBlank() }
    val highlightedTitle = remember(title, searchCtx.terms, primaryColor) {
        SearchTextMatcher.highlightText(title, searchCtx.terms, primaryColor)
    }
    val highlightedSummary = remember(resolvedSummary, searchCtx.terms, primaryColor) {
        resolvedSummary?.let { SearchTextMatcher.highlightText(it, searchCtx.terms, primaryColor) }
    }
    ArrowPreference(
        title = highlightedTitle,
        modifier = modifier,
        summary = highlightedSummary,
        endActions = endActions,
        cardListPosition = cardListPosition,
        onClick = onClick,
        enabled = enabled,
    )
}

/** 支持搜索高亮的开关设置项，直接复用 COUI [SwitchPreference] 的 String / AnnotatedString 重载。 */
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
    val primaryColor = COUITheme.colorScheme.primary
    val resolvedSummary = summary?.takeIf { it.isNotBlank() } ?: searchCtx.breadcrumb.takeIf { it.isNotBlank() }
    val highlightedTitle = remember(title, searchCtx.terms, primaryColor) {
        SearchTextMatcher.highlightText(title, searchCtx.terms, primaryColor)
    }
    val highlightedSummary = remember(resolvedSummary, searchCtx.terms, primaryColor) {
        resolvedSummary?.let { SearchTextMatcher.highlightText(it, searchCtx.terms, primaryColor) }
    }
    SwitchPreference(
        checked = checked,
        onCheckedChange = onCheckedChange,
        title = highlightedTitle,
        summary = highlightedSummary,
        modifier = modifier,
        cardListPosition = cardListPosition,
        enabled = enabled,
    )
}

/** 支持搜索高亮的下拉设置项，直接复用 COUI [OverlayDropdownPreference] 的 String / AnnotatedString 重载。 */
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
    val primaryColor = COUITheme.colorScheme.primary
    val resolvedSummary = summary?.takeIf { it.isNotBlank() } ?: searchCtx.breadcrumb.takeIf { it.isNotBlank() }
    val highlightedTitle = remember(title, searchCtx.terms, primaryColor) {
        SearchTextMatcher.highlightText(title, searchCtx.terms, primaryColor)
    }
    val highlightedSummary = remember(resolvedSummary, searchCtx.terms, primaryColor) {
        resolvedSummary?.let { SearchTextMatcher.highlightText(it, searchCtx.terms, primaryColor) }
    }
    OverlayDropdownPreference(
        items = items,
        selectedIndex = selectedIndex,
        title = highlightedTitle,
        summary = highlightedSummary,
        modifier = modifier,
        cardListPosition = cardListPosition,
        enabled = enabled,
        onSelectedIndexChange = onSelectedIndexChange,
    )
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
