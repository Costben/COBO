package com.cobo.app.screen

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cobo.app.App
import com.cobo.app.data.ReadLater
import com.cobo.app.data.ReadLaterEntry
import com.cobo.app.data.ReadLaterFilter
import com.cobo.app.data.StatusMemory
import com.cobo.app.data.UserRemarks
import com.cobo.app.data.filterReadLaterEntries
import com.cobo.app.ui.NavState
import com.cobo.app.ui.Route
import com.cobo.app.ui.components.Avatar
import com.cobo.app.ui.components.EmptyState
import com.cobo.app.ui.components.WeiboRichText
import com.cobo.app.ui.components.navBottomPadding
import com.cobo.app.ui.glassBarContentTop
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.NotificationSwipeBox
import io.github.suqi8.coui.kmp.basic.TabRowDefaults
import io.github.suqi8.coui.kmp.basic.TabRowWithContour
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.rememberNotificationSwipeState
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.core.ExperimentalCOUIApi
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Delete
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic
import com.cobo.app.platform.formatEpochMillis
import kotlinx.coroutines.launch

private val ReadLaterPlacementSpec: FiniteAnimationSpec<IntOffset> =
    tween(durationMillis = 220, easing = FastOutSlowInEasing)

private fun formatReadLaterStamp(millis: Long): String {
    if (millis <= 0L) return ""
    return formatEpochMillis(millis, "MM-dd HH:mm")
}

/** 独立入口直接打开收藏页的「稍后再看」标签。 */
@Composable
fun ReadLaterScreen(nav: NavState) {
    FavoritesScreen(
        nav = nav,
        initialTab = if (App.repo.guest) 1 else 2,
    )
}

@OptIn(ExperimentalCOUIApi::class)
@Composable
internal fun ReadLaterTabContent(
    nav: NavState,
    listState: LazyListState,
    topInset: Dp,
    extraTopInset: Dp,
    query: String,
    filter: ReadLaterFilter,
    onFilterChange: (ReadLaterFilter) -> Unit,
) {
    val allEntries = ReadLater.all()
    var openId by remember { mutableStateOf<String?>(null) }
    val shown = filterReadLaterEntries(allEntries, filter, query)

    // 收藏页外层正处于 layerBackdrop 录制期间；此处必须隔离 LocalBackdrop 并关闭 NotificationSwipeBox 模糊，
    // 防止 SwipeRevealBox / MetaBallSurfaceWithRim 对录制中的 GraphicsLayer 发生递归自采样闪退。
    CompositionLocalProvider(LocalBackdrop provides null) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().scrollEndHaptic().testTag("read_later_page"),
            contentPadding = PaddingValues(
                start = 12.dp,
                top = glassBarContentTop(topInset, 8.dp) + extraTopInset,
                end = 12.dp,
                bottom = 16.dp + navBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (allEntries.isNotEmpty()) {
                item(key = "read_later_tabs") {
                    TabRowWithContour(
                        tabs = ReadLaterFilter.entries.map { it.label },
                        selectedTabIndex = filter.ordinal,
                        onTabSelected = { idx -> onFilterChange(ReadLaterFilter.entries[idx]) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 2.dp)
                            .testTag("read_later_tabs"),
                        height = TabRowDefaults.TabRowWithContourTinyHeight,
                        cornerRadius = TabRowDefaults.TabRowWithContourTinyCornerRadius,
                        contourPadding = TabRowDefaults.TabRowWithContourTinyPadding,
                        segmentPadding = 6.dp,
                    )
                }
            }

            when {
                allEntries.isEmpty() -> item(key = "empty") {
                    EmptyState("可在微博详情页收藏按钮或卡片右上角菜单加入稍后再看，离线也能查看正文与转发快照")
                }
                shown.isEmpty() -> item(key = "filtered_empty") {
                    EmptyState(
                        when {
                            query.isNotBlank() -> "没有匹配的离线快照"
                            filter == ReadLaterFilter.Unread -> "没有未读的稍后再看微博"
                            filter == ReadLaterFilter.Read -> "还没有已读记录"
                            else -> "暂无记录"
                        },
                    )
                }
                else -> {
                    itemsIndexed(shown, key = { _, e -> e.status.id }) { index, entry ->
                        val swipe = rememberNotificationSwipeState()
                        val scope = rememberCoroutineScope()
                        LaunchedEffect(swipe.isRevealed) {
                            if (swipe.isRevealed) openId = entry.status.id
                        }
                        LaunchedEffect(openId) {
                            if (openId != entry.status.id && swipe.isRevealed) swipe.close()
                        }
                        NotificationSwipeBox(
                            onSettings = null,
                            onDismiss = {
                                ReadLater.remove(entry.status.id)
                                App.toast("已移出稍后再看")
                            },
                            settingsIcon = null,
                            dismissIcon = null,
                            modifier = Modifier
                                .animateItem(
                                    fadeInSpec = null,
                                    placementSpec = ReadLaterPlacementSpec,
                                    fadeOutSpec = null,
                                )
                                .testTag("read_later_row_$index"),
                            state = swipe,
                            minHeight = 0.dp,
                            containerColor = CardDefaults.defaultColors().color,
                            dismissOnFullSwipe = true,
                            dismissDescription = "移出稍后再看",
                            enableBlur = false,
                        ) {
                            ReadLaterRowBody(
                                entry = entry,
                                onOpen = {
                                    if (swipe.isRevealed) {
                                        scope.launch { swipe.close() }
                                    } else {
                                        StatusMemory.remember(entry.status)
                                        ReadLater.setRead(entry.status.id, true)
                                        nav.push(Route.Detail(entry.status.id, entry.status.mid))
                                    }
                                },
                                onToggleRead = {
                                    ReadLater.setRead(entry.status.id, !entry.read)
                                },
                                onDelete = {
                                    ReadLater.remove(entry.status.id)
                                    App.toast("已移出稍后再看")
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadLaterRowBody(
    entry: ReadLaterEntry,
    onOpen: () -> Unit,
    onToggleRead: () -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    val status = entry.status
    val user = status.user
    val displayAuthor = UserRemarks.displayName(user?.id, user?.name.orEmpty()).ifBlank { "未知用户" }
    val stamp = formatReadLaterStamp(entry.savedAt)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(user?.avatar, 26.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = displayAuthor,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (stamp.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stamp,
                    fontSize = 11.sp,
                    color = scheme.onSurfaceSecondary,
                )
            }
            if (!entry.read) {
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(scheme.primary),
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        if (status.textHtml.isNotBlank()) {
            WeiboRichText(
                html = status.textHtml,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                maxLines = 4,
                selectable = false,
            )
        } else {
            Text(
                text = status.plainText.ifBlank { "（无正文）" },
                fontSize = 14.sp,
                color = scheme.onSurface,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }

        val quoted = status.retweeted
        if (quoted != null) {
            val qName = UserRemarks.displayName(quoted.user?.id, quoted.user?.name.orEmpty())
            val qText = quoted.plainText.ifBlank { if (quoted.pics.isNotEmpty()) "[图片]" else "[转发内容]" }
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (qName.isNotBlank()) "@$qName：$qText" else qText,
                fontSize = 12.sp,
                color = scheme.onSurfaceSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(scheme.surface)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }

        val mediaHint = buildList {
            if (status.pics.isNotEmpty()) add("${status.pics.size} 张图片")
            if (status.video != null) add("视频")
        }.joinToString(" / ")

        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (mediaHint.isNotEmpty()) {
                Text(
                    text = mediaHint,
                    fontSize = 11.sp,
                    color = scheme.primary,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            Text(
                text = if (entry.read) "标为未读" else "标为已读",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = scheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onToggleRead)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = COUIIcons.Delete,
                    contentDescription = "删除",
                    tint = scheme.onSurfaceSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
