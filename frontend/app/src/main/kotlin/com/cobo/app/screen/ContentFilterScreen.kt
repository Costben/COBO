package com.cobo.app.screen

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cobo.app.App
import com.cobo.app.data.ContentFilters
import com.cobo.app.data.KeywordRule
import com.cobo.app.data.MuteUserMode
import com.cobo.app.data.MutedUser
import com.cobo.app.data.normalizeTopicRule
import com.cobo.app.data.validateKeywordRule
import com.cobo.app.ui.AppBarSurface
import com.cobo.app.ui.NavState
import com.cobo.app.ui.PinnedBarDensity
import com.cobo.app.ui.Route
import com.cobo.app.ui.blurEffectsEnabled
import com.cobo.app.ui.components.BackIconButton
import com.cobo.app.ui.components.ConfirmDialog
import com.cobo.app.ui.components.navBottomPadding
import com.cobo.app.ui.glassBarContentTop
import com.cobo.app.ui.layerBackdropIf
import com.cobo.app.ui.pageContainerColor
import com.cobo.app.ui.pinnedHeight
import com.cobo.app.ui.rememberListScrollFraction
import io.github.suqi8.coui.kmp.basic.BindScrollToTop
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.Checkbox
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.LocalEdgeToEdge
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextField
import io.github.suqi8.coui.kmp.basic.TextFieldMode
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Delete
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic

@Composable
fun ContentFilterSettingsScreen(nav: NavState) {
    val rules = ContentFilters.rules
    val scheme = COUITheme.colorScheme
    val blurEffects = blurEffectsEnabled()
    val pageColor = pageContainerColor()
    val backdrop = rememberLayerBackdrop()
    val topBarHeight = pinnedHeight(TopAppBarDefaults.SmallTopAppBarCenterHeight)
    val listState = rememberLazyListState()
    BindScrollToTop(listState)

    var showAddKeyword by remember { mutableStateOf(false) }
    var showAddTopic by remember { mutableStateOf(false) }
    var confirmClearAll by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(pageColor)) {
        Box(Modifier.fillMaxSize().layerBackdropIf(blurEffects, backdrop).background(pageColor)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().scrollEndHaptic(),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    top = glassBarContentTop(topBarHeight, 10.dp),
                    end = 12.dp,
                    bottom = 24.dp + navBottomPadding(),
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "intro") {
                    Text(
                        text = "仅保存在本机，命中的内容会在对应列表自动隐藏，本人发布的内容不受影响。",
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = scheme.onSurfaceSecondary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }

                item(key = "scopes") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = CardDefaults.GroupCornerRadius,
                        insideMargin = PaddingValues(0.dp),
                        weight = CardDefaults.GroupWeight,
                    ) {
                        SwitchPreference(
                            checked = rules.applyToFeeds,
                            onCheckedChange = { ContentFilters.updateScopes(applyToFeeds = it) },
                            title = "应用于信息流",
                            summary = "首页关注流、超话、附近与容器信息流",
                            modifier = Modifier.testTag("filter_scope_feeds"),
                            cardListPosition = CardListPosition.Head,
                        )
                        SwitchPreference(
                            checked = rules.applyToSearchAndHot,
                            onCheckedChange = { ContentFilters.updateScopes(applyToSearchAndHot = it) },
                            title = "应用于搜索与热搜",
                            summary = "搜索结果微博、热搜榜与话题流",
                            modifier = Modifier.testTag("filter_scope_search"),
                            cardListPosition = CardListPosition.Middle,
                        )
                        SwitchPreference(
                            checked = rules.applyToComments,
                            onCheckedChange = { ContentFilters.updateScopes(applyToComments = it) },
                            title = "应用于评论区",
                            summary = "详情页评论与楼中楼回复（同步生效静音用户与关键词）",
                            modifier = Modifier.testTag("filter_scope_comments"),
                            cardListPosition = CardListPosition.Tail,
                        )
                    }
                }

                item(key = "keywords_header") {
                    FilterSectionHeader(
                        title = "关键词",
                        count = rules.keywords.size,
                        actionText = "添加",
                        actionTag = "filter_add_keyword",
                        onAction = { showAddKeyword = true },
                    )
                }

                item(key = "keywords_card") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = CardDefaults.GroupCornerRadius,
                        insideMargin = PaddingValues(0.dp),
                        weight = CardDefaults.GroupWeight,
                    ) {
                        if (rules.keywords.isEmpty()) {
                            EmptyFilterRow("暂未添加关键词")
                        } else {
                            rules.keywords.forEachIndexed { index, rule ->
                                KeywordRuleRow(
                                    rule = rule,
                                    onDelete = {
                                        ContentFilters.removeKeyword(rule)
                                        App.toast("已移除关键词")
                                    },
                                )
                                if (index < rules.keywords.lastIndex) {
                                    FilterInnerDivider()
                                }
                            }
                        }
                    }
                }

                item(key = "topics_header") {
                    FilterSectionHeader(
                        title = "话题",
                        count = rules.topics.size,
                        actionText = "添加",
                        actionTag = "filter_add_topic",
                        onAction = { showAddTopic = true },
                    )
                }

                item(key = "topics_card") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = CardDefaults.GroupCornerRadius,
                        insideMargin = PaddingValues(0.dp),
                        weight = CardDefaults.GroupWeight,
                    ) {
                        if (rules.topics.isEmpty()) {
                            EmptyFilterRow("暂未添加话题")
                        } else {
                            rules.topics.forEachIndexed { index, topic ->
                                TopicRuleRow(
                                    topic = topic,
                                    onDelete = {
                                        ContentFilters.removeTopic(topic)
                                        App.toast("已取消屏蔽话题 #$topic#")
                                    },
                                )
                                if (index < rules.topics.lastIndex) {
                                    FilterInnerDivider()
                                }
                            }
                        }
                    }
                }

                item(key = "users_header") {
                    FilterSectionHeader(
                        title = "已静音用户",
                        count = rules.mutedUsers.size,
                    )
                }

                item(key = "users_card") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = CardDefaults.GroupCornerRadius,
                        insideMargin = PaddingValues(0.dp),
                        weight = CardDefaults.GroupWeight,
                    ) {
                        if (rules.mutedUsers.isEmpty()) {
                            EmptyFilterRow("可在微博卡片或用户主页菜单中静音用户")
                        } else {
                            rules.mutedUsers.forEachIndexed { index, muted ->
                                MutedUserRow(
                                    user = muted,
                                    onOpenProfile = { nav.push(Route.Profile(muted.uid)) },
                                    onToggleMode = {
                                        val nextMode = if (muted.mode == MuteUserMode.All) {
                                            MuteUserMode.RepostsOnly
                                        } else {
                                            MuteUserMode.All
                                        }
                                        ContentFilters.setMutedUser(muted.uid, muted.name, nextMode)
                                        App.toast(
                                            if (nextMode == MuteUserMode.All) "已切换为静音全部内容"
                                            else "已切换为仅屏蔽转发",
                                        )
                                    },
                                    onDelete = {
                                        ContentFilters.setMutedUser(muted.uid, muted.name, null)
                                        App.toast("已取消静音")
                                    },
                                )
                                if (index < rules.mutedUsers.lastIndex) {
                                    FilterInnerDivider()
                                }
                            }
                        }
                    }
                }

                if (rules.hasAnyRule) {
                    item(key = "clear_all") {
                        Spacer(Modifier.height(4.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = CardDefaults.GroupCornerRadius,
                            insideMargin = PaddingValues(0.dp),
                            weight = CardDefaults.GroupWeight,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { confirmClearAll = true }
                                    .padding(vertical = 15.dp)
                                    .testTag("filter_clear_all"),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "清空全部屏蔽规则",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = scheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }

        CompositionLocalProvider(
            LocalBackdrop provides if (blurEffects) backdrop else null,
            LocalEdgeToEdge provides true,
        ) {
            PinnedBarDensity {
                AppBarSurface(
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
                    containerColor = pageColor,
                    scrollFraction = rememberListScrollFraction(listState),
                ) {
                    SmallTopAppBar(
                        title = "内容屏蔽与过滤",
                        color = Color.Transparent,
                        showDivider = false,
                        gradientGlass = false,
                        navigationIcon = { BackIconButton { nav.pop() } },
                    )
                }
            }
        }
    }

    if (showAddKeyword) {
        AddKeywordDialog(
            onDismiss = { showAddKeyword = false },
            onConfirm = { pattern, isRegex ->
                showAddKeyword = false
                val err = ContentFilters.addKeyword(pattern, isRegex)
                App.toast(err ?: "已添加屏蔽关键词")
            },
        )
    }

    if (showAddTopic) {
        AddTopicDialog(
            onDismiss = { showAddTopic = false },
            onConfirm = { topic ->
                showAddTopic = false
                val err = ContentFilters.addTopic(topic)
                val clean = normalizeTopicRule(topic)
                App.toast(err ?: "已屏蔽话题 #$clean#")
            },
        )
    }

    if (confirmClearAll) {
        ConfirmDialog(
            title = "清空屏蔽规则",
            message = "将移除所有关键词、话题与静音用户，生效范围开关保持不变。",
            confirmText = "清空",
            onDismiss = { confirmClearAll = false },
            onConfirm = {
                confirmClearAll = false
                ContentFilters.clearAll()
                App.toast("已清空全部屏蔽规则")
            },
        )
    }
}

@Composable
private fun FilterSectionHeader(
    title: String,
    count: Int,
    actionText: String? = null,
    actionTag: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val scheme = COUITheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 4.dp, top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val heading = if (count > 0) "$title ($count)" else title
        Text(
            text = heading,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = scheme.onSurfaceSecondary,
            modifier = Modifier.weight(1f),
        )
        if (actionText != null && onAction != null) {
            Text(
                text = actionText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = scheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .let { if (actionTag != null) it.testTag(actionTag) else it },
            )
        }
    }
}

@Composable
private fun EmptyFilterRow(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        color = COUITheme.colorScheme.onSurfaceSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
    )
}

@Composable
private fun FilterInnerDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        thickness = 0.5.dp,
        color = COUITheme.colorScheme.dividerLine,
    )
}

@Composable
private fun KeywordRuleRow(
    rule: KeywordRule,
    onDelete: () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = rule.pattern,
                fontSize = 15.sp,
                color = scheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (rule.isRegex) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "正则表达式",
                    fontSize = 12.sp,
                    color = scheme.primary,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        DeleteRuleButton(onClick = onDelete)
    }
}

@Composable
private fun TopicRuleRow(
    topic: String,
    onDelete: () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "#$topic#",
            fontSize = 15.sp,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        DeleteRuleButton(onClick = onDelete)
    }
}

@Composable
private fun MutedUserRow(
    user: MutedUser,
    onOpenProfile: () -> Unit,
    onToggleMode: () -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    val displayName = user.name.ifBlank { "UID ${user.uid}" }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onOpenProfile)
                .padding(vertical = 2.dp),
        ) {
            Text(
                text = displayName,
                fontSize = 15.sp,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "UID ${user.uid}",
                fontSize = 12.sp,
                color = scheme.onSurfaceSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = user.mode.label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = scheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(scheme.primary.copy(alpha = 0.12f))
                .clickable(onClick = onToggleMode)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
        Spacer(Modifier.width(4.dp))
        DeleteRuleButton(onClick = onDelete)
    }
}

@Composable
private fun DeleteRuleButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = COUIIcons.Delete,
            contentDescription = "删除规则",
            tint = COUITheme.colorScheme.onSurfaceSecondary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun AddKeywordDialog(
    onDismiss: () -> Unit,
    onConfirm: (pattern: String, isRegex: Boolean) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var isRegex by remember { mutableStateOf(false) }
    var show by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var settled by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val validationError = remember(text, isRegex) {
        if (text.isBlank()) null else validateKeywordRule(text, isRegex)
    }
    val canConfirm = text.isNotBlank() && validationError == null

    LaunchedEffect(Unit) { show = true }

    OverlayDialog(
        show = show,
        title = "添加屏蔽关键词",
        outsideMargin = DpSize(16.dp, 8.dp),
        onDismissRequest = { show = false },
        onDismissFinished = {
            if (!settled) {
                settled = true
                val result = submitted
                if (result != null) onConfirm(result.first, result.second) else onDismiss()
            }
        },
    ) {
        Column {
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 4.dp),
                label = "输入关键词或正则表达式",
                backgroundMode = TextFieldMode.Rectangle,
                singleLine = true,
            )
            if (validationError != null) {
                Text(
                    text = validationError,
                    fontSize = 12.sp,
                    color = COUITheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 16.dp, top = 10.dp)
                    .toggleable(
                        value = isRegex,
                        interactionSource = null,
                        indication = null,
                        role = Role.Checkbox,
                        onValueChange = {
                            isRegex = it
                            haptic.performHapticFeedback(
                                if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                            )
                        },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(state = ToggleableState(isRegex), onClick = null)
                Text(
                    text = "使用正则表达式（忽略大小写）",
                    fontSize = 14.sp,
                    color = COUITheme.colorScheme.onBackgroundVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            DialogButtonBar(
                negative = DialogButtonBarAction(text = "取消", onClick = { show = false }),
                positive = DialogButtonBarAction(
                    text = "添加",
                    enabled = canConfirm,
                    onClick = {
                        if (canConfirm) {
                            submitted = text.trim() to isRegex
                            show = false
                        }
                    },
                ),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun AddTopicDialog(
    onDismiss: () -> Unit,
    onConfirm: (topic: String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf<String?>(null) }
    var settled by remember { mutableStateOf(false) }
    val normalized = remember(text) { normalizeTopicRule(text) }

    LaunchedEffect(Unit) { show = true }

    OverlayDialog(
        show = show,
        title = "添加屏蔽话题",
        outsideMargin = DpSize(16.dp, 8.dp),
        onDismissRequest = { show = false },
        onDismissFinished = {
            if (!settled) {
                settled = true
                val result = submitted
                if (result != null) onConfirm(result) else onDismiss()
            }
        },
    ) {
        Column {
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 4.dp),
                label = "输入话题名（无需带 #）",
                backgroundMode = TextFieldMode.Rectangle,
                singleLine = true,
            )
            DialogButtonBar(
                negative = DialogButtonBarAction(text = "取消", onClick = { show = false }),
                positive = DialogButtonBarAction(
                    text = "添加",
                    enabled = normalized.isNotEmpty(),
                    onClick = {
                        if (normalized.isNotEmpty()) {
                            submitted = normalized
                            show = false
                        }
                    },
                ),
            )
        }
    }
}
