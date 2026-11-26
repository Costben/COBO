package com.cobo.app.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import com.cobo.app.ui.AppBarSurface
import com.cobo.app.ui.PinnedBarDensity
import com.cobo.app.ui.components.BackIconButton
import com.cobo.app.ui.pageContainerColor
import com.cobo.app.ui.rememberListScrollFraction
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.basic.TopAppBarSearchBar
import io.github.suqi8.coui.kmp.theme.COUITheme

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
)

internal fun parseSettingsSearchTerms(query: String): List<String> =
    query.trim().split(SETTINGS_SEARCH_WHITESPACE_REGEX).filter { it.isNotEmpty() }

private val SETTINGS_SEARCH_WHITESPACE_REGEX = Regex("\\s+")

internal fun cardPositionFor(index: Int, total: Int): CardListPosition = when {
    total <= 1 -> CardListPosition.Full
    index == 0 -> CardListPosition.Head
    index == total - 1 -> CardListPosition.Tail
    else -> CardListPosition.Middle
}

/** 按相关性分值过滤并重排设置分组与条目；查询为空时原样返回。 */
internal fun filterSettingsGroups(
    groups: List<SettingsGroupSection>,
    terms: List<String>,
): List<SettingsGroupSection> {
    if (terms.isEmpty()) return groups
    return groups.mapNotNull { group ->
        val scored = group.entries
            .mapNotNull { entry ->
                val score = entry.matchScore(group.title, terms)
                if (score > 0) entry to score else null
            }
            .sortedByDescending { it.second }
        if (scored.isEmpty()) {
            null
        } else {
            group.copy(entries = scored.map { it.first }) to scored.first().second
        }
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
