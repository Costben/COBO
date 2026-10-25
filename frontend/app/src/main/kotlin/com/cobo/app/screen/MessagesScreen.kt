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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cobo.app.App
import com.cobo.app.data.MessageItem
import com.cobo.app.data.chatPlainText
import com.cobo.app.data.formatWeiboTime
import com.cobo.app.ui.NavState
import com.cobo.app.ui.pageContainerColor
import com.cobo.app.ui.Route
import com.cobo.app.ui.components.Avatar
import com.cobo.app.ui.components.EmptyState
import com.cobo.app.ui.components.ErrorState
import com.cobo.app.ui.components.FullPageRefreshHoldMillis
import com.cobo.app.ui.components.FullPageRefreshTexts
import com.cobo.app.ui.components.LoadingState
import com.cobo.app.ui.components.navBottomPadding
import io.github.suqi8.coui.kmp.basic.FullPagePullToRefresh
import io.github.suqi8.coui.kmp.basic.fullPagePullToRefreshColors
import io.github.suqi8.coui.kmp.basic.rememberFullPagePullToRefreshState
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.launch

@Composable
fun MessagesScreen(nav: NavState, topInset: Dp) {
    val scope = rememberCoroutineScope()
    val items = remember { mutableStateListOf<MessageItem>() }
    var page by remember { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var end by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val pullState = rememberFullPagePullToRefreshState()

    // showRefresh 只给用户主动刷新用：进入页面时的静默加载不驱动下拉动画。
    suspend fun load(reset: Boolean, force: Boolean = false, showRefresh: Boolean = false) {
        if (loading) return
        loading = true
        if (reset && showRefresh) refreshing = true
        error = null
        try {
            val nextPage = if (reset) 1 else page
            val list = App.repo.messages(nextPage, force)
            if (reset) items.clear()
            val known = items.mapTo(HashSet()) { it.id.ifBlank { it.scheme } }
            list.forEach { item ->
                if (known.add(item.id.ifBlank { item.scheme })) items.add(item)
            }
            page = nextPage + 1
            if (list.isEmpty()) end = true
        } catch (t: Throwable) {
            error = t.message ?: "加载失败"
        } finally {
            loading = false
            refreshing = false
        }
    }

    // 进入消息页静默取最新数据：只换内容，不播放下拉动画。
    LaunchedEffect(Unit) {
        load(true, force = true)
    }
    // 底栏刷新键属于用户主动触发，才播放与下拉一致的气泡动画。
    LaunchedEffect(Unit) {
        App.messagesTopEvents.collect {
            pullState.startRefresh { scope.launch { load(true, force = true, showRefresh = true) } }
        }
    }
    LaunchedEffect(listState, items.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { last ->
                if (last >= items.size - 2 && items.isNotEmpty() && !end) load(false)
            }
    }

    FullPagePullToRefresh(
        isRefreshing = refreshing,
        onRefresh = { scope.launch { load(true, force = true, showRefresh = true) } },
        modifier = Modifier.fillMaxSize(),
        state = pullState,
        contentPadding = PaddingValues(top = topInset),
        colors = fullPagePullToRefreshColors(backgroundColor = pageContainerColor()),
        refreshTexts = FullPageRefreshTexts,
        completeHoldDurationMillis = FullPageRefreshHoldMillis,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = topInset + 14.dp,
                bottom = 66.dp + navBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            if (items.isEmpty()) {
                item {
                    when {
                        loading || refreshing -> LoadingState()
                        error != null -> ErrorState(error!!) {
                            scope.launch { load(true, force = true, showRefresh = true) }
                        }
                        else -> EmptyState("还没有私信")
                    }
                }
            }
            itemsIndexed(items, key = { index, item -> "${item.id.ifBlank { item.scheme }}#$index" }) { index, item ->
                val uid = item.user?.id.orEmpty()
                MessageRow(
                    item = item,
                    // 服务端没有已读接口，只能拿「上次点进去时这条会话的预览文字」比：文字变了就是新消息。
                    seen = App.store.chatSeen(uid) == item.text,
                    modifier = Modifier.testTag("message_row_$index"),
                ) {
                    App.store.markChatSeen(uid, item.text)
                    nav.push(Route.Chat(uid, item.user?.name.orEmpty()))
                }
            }
            if (items.isNotEmpty()) {
                item {
                    if (loading) LoadingState()
                    else if (end) Text(
                        "没有更多了",
                        fontSize = 12.sp,
                        color = COUITheme.colorScheme.onSurfaceSecondary,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageRow(
    item: MessageItem,
    seen: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(pageContainerColor())
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(item.user?.avatar, 46.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.user?.name.orEmpty(),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                Text(
                    formatWeiboTime(item.createdAt),
                    fontSize = 11.sp,
                    color = scheme.onSurfaceSecondary,
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                chatPlainText(item.text),
                fontSize = 13.sp,
                color = scheme.onSurfaceSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (item.unread > 0 && !seen) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(8.dp)
                    .background(scheme.primary, CircleShape),
            )
        }
    }
}
