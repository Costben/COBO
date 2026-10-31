package com.cobo.app.screen

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.cobo.app.App
import com.cobo.app.data.Membership
import com.cobo.app.data.VoteInfo
import com.cobo.app.data.VoteOption
import com.cobo.app.ui.CoboIcons
import com.cobo.app.ui.NavState
import com.cobo.app.ui.PinnedBarDensity
import com.cobo.app.ui.Route
import com.cobo.app.ui.components.Avatar
import com.cobo.app.ui.components.BackIconButton
import com.cobo.app.ui.components.ConfirmDialog
import com.cobo.app.ui.components.ErrorState
import com.cobo.app.ui.components.LoadingState
import com.cobo.app.ui.components.MemberBadge
import com.cobo.app.ui.components.navBottomPadding
import com.cobo.app.ui.pageContainerColor
import com.cobo.app.ui.pinnedHeight
import io.github.suqi8.coui.kmp.basic.Button
import io.github.suqi8.coui.kmp.basic.ButtonDefaults
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Checkbox
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.LocalEdgeToEdge
import io.github.suqi8.coui.kmp.basic.RadioButton
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.layerBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.launch

@Composable
fun VoteScreen(nav: NavState, voteId: String) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var vote by remember(voteId) { mutableStateOf<VoteInfo?>(null) }
    var loading by remember(voteId) { mutableStateOf(true) }
    var error by remember(voteId) { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<String>() }

    val backdrop = rememberLayerBackdrop()
    val scheme = COUITheme.colorScheme
    val topBarHeight = pinnedHeight(TopAppBarDefaults.SmallTopAppBarCenterHeight)

    suspend fun load() {
        loading = true
        error = null
        runCatching { App.repo.fetchVote(voteId) }
            .onSuccess {
                vote = it
                selectedIds.clear()
                it.options.filter { opt -> opt.selected }.forEach { opt -> selectedIds.add(opt.id) }
                loading = false
            }
            .onFailure {
                error = it.message ?: "加载投票失败"
                loading = false
            }
    }

    LaunchedEffect(voteId) { load() }

    Box(
        Modifier
            .fillMaxSize()
            .background(pageContainerColor())
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .background(pageContainerColor())
        ) {
            when {
                loading && vote == null -> LoadingState()

                error != null && vote == null -> ErrorState(error!!) {
                    scope.launch { load() }
                }

                vote != null -> {
                    val currentVote = vote!!
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("vote_screen_list"),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            top = topBarHeight + 12.dp,
                            end = 16.dp,
                            bottom = navBottomPadding() + if (currentVote.parted && currentVote.isExpired) 24.dp else 96.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // 1. 发起人与截止时间卡片
                        item(key = "author_header") {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    val author = currentVote.author
                                    Avatar(
                                        url = author?.avatar,
                                        size = 46.dp,
                                        modifier = Modifier.clickable {
                                            if (author?.id != null) nav.push(Route.Profile(author.id))
                                        },
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = author?.name ?: "微博用户",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = scheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            if (author?.membership != null && author.membership != Membership.None) {
                                                Spacer(Modifier.width(4.dp))
                                                MemberBadge(author.membership, compact = true)
                                            }
                                        }
                                        Spacer(Modifier.height(3.dp))
                                        Text(
                                            text = currentVote.endTimeStr,
                                            fontSize = 12.sp,
                                            color = scheme.onSurfaceSecondary,
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    // 参与人数胶囊徽章
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(scheme.primary.copy(alpha = 0.12f))
                                            .padding(horizontal = 10.dp, vertical = 5.dp),
                                    ) {
                                        Text(
                                            text = "${currentVote.partNum} 参与人数",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = scheme.primary,
                                        )
                                    }
                                }
                            }
                        }

                        // 2. 投票主题标题与规则说明
                        item(key = "title_section") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    text = currentVote.title,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = scheme.onSurface,
                                    lineHeight = 28.sp,
                                )
                                Spacer(Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (currentVote.isMultiSelect) {
                                            "投票选项（多选，最多可选 ${currentVote.maxSelect} 项）"
                                        } else {
                                            "投票选项（单选）"
                                        },
                                        fontSize = 13.sp,
                                        color = scheme.onSurfaceSecondary,
                                    )
                                    if (currentVote.isExpired) {
                                        Spacer(Modifier.width(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(scheme.onSurface.copy(alpha = 0.08f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                        ) {
                                            Text(
                                                text = "已截止",
                                                fontSize = 11.sp,
                                                color = scheme.onSurfaceSecondary,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 3. 选项列表
                        items(currentVote.options, key = { it.id }) { option ->
                            val canVote = !currentVote.parted && !currentVote.isExpired
                            if (canVote) {
                                val isSelected = selectedIds.contains(option.id)
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                                    onClick = {
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
                                    },
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        if (option.pic.isNotBlank()) {
                                            AsyncImage(
                                                model = option.pic,
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(RoundedCornerShape(8.dp)),
                                            )
                                            Spacer(Modifier.width(12.dp))
                                        }
                                        Text(
                                            text = option.title,
                                            fontSize = 15.sp,
                                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                            color = if (isSelected) scheme.primary else scheme.onSurface,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Spacer(Modifier.width(12.dp))
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
                            } else {
                                // 结果模式：连续曲率胶囊进度条与百分比
                                val progress = (option.partRatio / 100f).coerceIn(0f, 1f)
                                val animatedProgress by animateFloatAsState(
                                    targetValue = progress,
                                    animationSpec = tween(600, easing = CubicBezierEasing(0.3f, 0f, 0.1f, 1f)),
                                    label = "vote_progress_${option.id}",
                                )
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    insideMargin = PaddingValues(0.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(SquircleShape(16.dp)),
                                    ) {
                                        // 进度底色填充
                                        Box(
                                            modifier = Modifier
                                                .matchParentSize()
                                                .background(scheme.surface),
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .fillMaxWidth(animatedProgress)
                                                .background(
                                                    if (option.selected) scheme.primary.copy(alpha = 0.18f)
                                                    else scheme.onSurface.copy(alpha = 0.07f),
                                                ),
                                        )

                                        // 文字与数据呈现
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            if (option.pic.isNotBlank()) {
                                                AsyncImage(
                                                    model = option.pic,
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .clip(RoundedCornerShape(8.dp)),
                                                )
                                                Spacer(Modifier.width(12.dp))
                                            }
                                            Column(Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = option.title,
                                                        fontSize = 15.sp,
                                                        fontWeight = if (option.selected) FontWeight.SemiBold else FontWeight.Normal,
                                                        color = if (option.selected) scheme.primary else scheme.onSurface,
                                                        modifier = Modifier.weight(1f, fill = false),
                                                    )
                                                    if (option.selected) {
                                                        Spacer(Modifier.width(6.dp))
                                                        Icon(
                                                            imageVector = CoboIcons.Check,
                                                            contentDescription = "已投选项",
                                                            tint = scheme.primary,
                                                            modifier = Modifier.size(16.dp),
                                                        )
                                                    }
                                                }
                                                if (option.partNum.isNotBlank()) {
                                                    Spacer(Modifier.height(4.dp))
                                                    Text(
                                                        text = option.partNum,
                                                        fontSize = 12.sp,
                                                        color = scheme.onSurfaceSecondary,
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.width(12.dp))
                                            Text(
                                                text = "${option.partRatio}%",
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (option.selected) scheme.primary else scheme.onSurface,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 4. 底部固钉操作栏
                    if (!currentVote.parted && !currentVote.isExpired) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(pageContainerColor())
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Button(
                                onClick = {
                                    if (selectedIds.isEmpty()) return@Button
                                    scope.launch {
                                        submitting = true
                                        runCatching {
                                            App.repo.castVote(currentVote.id, selectedIds.toList(), currentVote.sk)
                                        }.onSuccess { updated ->
                                            vote = updated
                                            App.toast("投票成功")
                                        }.onFailure {
                                            App.toast(it.message ?: "投票失败")
                                        }
                                        submitting = false
                                    }
                                },
                                enabled = selectedIds.isNotEmpty() && !submitting,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                            ) {
                                Text(
                                    text = if (submitting) "正在提交…" else "投票",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    } else if (currentVote.parted && !currentVote.isExpired) {
                        // 已投票且未截止：底栏提供取消重新投票选项
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(pageContainerColor())
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = CoboIcons.Check,
                                        contentDescription = null,
                                        tint = scheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "已参与投票",
                                        fontSize = 14.sp,
                                        color = scheme.primary,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                                TextButton(
                                    text = "重新投票",
                                    onClick = { showCancelDialog = true },
                                )
                            }
                        }
                    }
                }
            }
        }

        // 顶栏玻璃
        CompositionLocalProvider(
            LocalBackdrop provides backdrop,
            LocalEdgeToEdge provides true,
        ) {
            PinnedBarDensity {
                SmallTopAppBar(
                    title = "微博投票",
                    modifier = Modifier.align(Alignment.TopCenter),
                    color = pageContainerColor(),
                    showDivider = false,
                    gradientGlass = true,
                    navigationIcon = { BackIconButton { nav.pop() } },
                )
            }
        }

        // 取消投票二次确认弹窗
        if (showCancelDialog && vote != null) {
            ConfirmDialog(
                title = "重新投票",
                message = "确定要撤销当前投票并重新选择吗？",
                confirmText = "撤销重选",
                onConfirm = {
                    showCancelDialog = false
                    val v = vote ?: return@ConfirmDialog
                    scope.launch {
                        submitting = true
                        runCatching {
                            App.repo.uncastVote(v.id, v.sk)
                        }.onSuccess { updated ->
                            vote = updated
                            selectedIds.clear()
                            App.toast("已取消，请重新选择")
                        }.onFailure {
                            App.toast(it.message ?: "操作失败")
                        }
                        submitting = false
                    }
                },
                onDismiss = { showCancelDialog = false },
            )
        }
    }
}
