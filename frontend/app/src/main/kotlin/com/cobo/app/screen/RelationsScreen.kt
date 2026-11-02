package com.cobo.app.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cobo.app.App
import com.cobo.app.data.RelationKind
import com.cobo.app.data.User
import com.cobo.app.ui.NavState
import com.cobo.app.ui.PinnedBarDensity
import com.cobo.app.ui.Route
import com.cobo.app.ui.components.BackIconButton
import com.cobo.app.ui.components.EmptyState
import com.cobo.app.ui.components.ErrorState
import com.cobo.app.ui.components.FullPageRefreshHoldMillis
import com.cobo.app.ui.components.FullPageRefreshTexts
import com.cobo.app.ui.components.LoadingState
import com.cobo.app.ui.components.UserRow
import com.cobo.app.ui.components.navBottomPadding
import com.cobo.app.ui.pageContainerColor
import com.cobo.app.ui.pinnedHeight
import io.github.suqi8.coui.kmp.basic.FullPagePullToRefresh
import io.github.suqi8.coui.kmp.basic.LocalEdgeToEdge
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.basic.fullPagePullToRefreshColors
import io.github.suqi8.coui.kmp.basic.rememberFullPagePullToRefreshState
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.layerBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.launch

/**
 * 主页上点「关注 / 粉丝」进来的关系列表：每行一个用户，点进他的主页。
 * 关注按页码翻，粉丝按条数偏移翻，两种都在 [App.repo.relations] 里收口。
 */
@Composable
fun RelationsScreen(nav: NavState, uid: String, kind: RelationKind) {
    val scope = rememberCoroutineScope()
    val items = remember(uid, kind) { mutableStateListOf<User>() }
    var page by remember(uid, kind) { mutableIntStateOf(1) }
    var loading by remember(uid, kind) { mutableStateOf(false) }
    var refreshing by remember(uid, kind) { mutableStateOf(false) }
    var end by remember(uid, kind) { mutableStateOf(false) }
    var error by remember(uid, kind) { mutableStateOf<String?>(null) }
    var moreError by remember(uid, kind) { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val pullState = rememberFullPagePullToRefreshState()
    val backdrop = rememberLayerBackdrop()
    val topBarHeight = pinnedHeight(TopAppBarDefaults.SmallTopAppBarCenterHeight)

    // showRefresh 只给用户主动刷新用：进入页面时的静默加载不驱动下拉动画。
    suspend fun load(reset: Boolean, force: Boolean = false, showRefresh: Boolean = false) {
        if (loading) return
        loading = true
        if (reset && showRefresh) refreshing = true
        val nextPage = if (reset) 1 else page
        try {
            val list = App.repo.relations(uid, kind, nextPage, force)
            if (reset) {
                items.clear()
                error = null
            }
            moreError = null
            val known = items.mapTo(HashSet()) { it.id }
            list.forEach { if (known.add(it.id)) items.add(it) }
            page = nextPage + 1
            if (list.isEmpty()) end = true
        } catch (t: Throwable) {
            val message = t.message ?: "加载失败"
            // 第一页失败整页报错；后面几页失败只在底部留一个重试，已经看到的人不动。
            if (items.isEmpty()) error = message else moreError = message
        } finally {
            loading = false
            refreshing = false
        }
    }

    LaunchedEffect(uid, kind) { load(true) }

    LaunchedEffect(listState, items.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { last ->
                if (last >= items.size - 2 && items.isNotEmpty() && !end && moreError == null) load(false)
            }
    }

    Box(Modifier.fillMaxSize().background(pageContainerColor())) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            FullPagePullToRefresh(
                isRefreshing = refreshing,
                onRefresh = { scope.launch { load(true, force = true, showRefresh = true) } },
                modifier = Modifier.fillMaxSize(),
                state = pullState,
                contentPadding = PaddingValues(top = topBarHeight),
                colors = fullPagePullToRefreshColors(backgroundColor = pageContainerColor()),
                refreshTexts = FullPageRefreshTexts,
                completeHoldDurationMillis = FullPageRefreshHoldMillis,
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().testTag("relations_list"),
                    contentPadding = PaddingValues(
                        top = topBarHeight + 8.dp,
                        bottom = navBottomPadding() + 16.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (items.isEmpty()) {
                        item {
                            when {
                                loading || refreshing -> LoadingState()
                                error != null -> ErrorState(error!!) {
                                    scope.launch { load(true, force = true, showRefresh = true) }
                                }
                                else -> EmptyState(
                                    if (kind == RelationKind.Following) "还没有关注任何人" else "还没有粉丝"
                                )
                            }
                        }
                    }
                    itemsIndexed(items, key = { _, user -> user.id }) { index, user ->
                        UserRow(
                            user = user,
                            modifier = Modifier.testTag("relations_row_$index"),
                            onClick = { nav.push(Route.Profile(user.id)) },
                        )
                    }
                    if (items.isNotEmpty()) {
                        item {
                            when {
                                loading -> LoadingState()
                                moreError != null -> TextButton(
                                    text = "加载失败，点此重试",
                                    onClick = { scope.launch { load(false) } },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                end -> Text(
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
        }

        CompositionLocalProvider(
            LocalBackdrop provides backdrop,
            LocalEdgeToEdge provides true,
        ) {
            PinnedBarDensity {
                SmallTopAppBar(
                    title = if (kind == RelationKind.Following) "关注" else "粉丝",
                    modifier = Modifier.align(Alignment.TopCenter),
                    color = pageContainerColor(),
                    showDivider = false,
                    gradientGlass = true,
                    navigationIcon = { BackIconButton { nav.pop() } },
                )
            }
        }
    }
}
