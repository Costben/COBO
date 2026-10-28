package com.cobo.app.screen

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.cobo.app.App
import com.cobo.app.data.FollowTag
import com.cobo.app.data.FollowTags
import com.cobo.app.data.Membership
import com.cobo.app.data.Status
import com.cobo.app.data.User
import com.cobo.app.ui.NavState
import com.cobo.app.ui.pageContainerColor
import com.cobo.app.ui.LocalTopBarBounds
import com.cobo.app.ui.attachToRow
import com.cobo.app.ui.rememberExpandOrigin
import com.cobo.app.ui.Route
import com.cobo.app.ui.expandOrigin
import com.cobo.app.ui.components.Avatar
import com.cobo.app.ui.components.BackIconButton
import com.cobo.app.ui.components.EmptyState
import com.cobo.app.ui.components.ErrorState
import com.cobo.app.ui.components.FollowTagBadge
import com.cobo.app.ui.components.LoadingState
import com.cobo.app.ui.components.MemberBadge
import com.cobo.app.ui.components.StatusCard
import com.cobo.app.ui.components.FullPageRefreshHoldMillis
import com.cobo.app.ui.components.FullPageRefreshTexts
import com.cobo.app.ui.components.navBottomPadding
import com.cobo.app.ui.components.fmtCount
import io.github.suqi8.coui.kmp.basic.Button
import io.github.suqi8.coui.kmp.basic.DropdownImpl
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton
import io.github.suqi8.coui.kmp.basic.ListPopupColumn
import io.github.suqi8.coui.kmp.basic.ListPopupDefaults
import io.github.suqi8.coui.kmp.basic.FullPagePullToRefresh
import io.github.suqi8.coui.kmp.basic.fullPagePullToRefreshColors
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.couiTopAppBarActionItem
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.LocalDismissState
import io.github.suqi8.coui.kmp.window.WindowListPopup
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(nav: NavState, uid: String?) {
    val key = uid.orEmpty()
    val tag = FollowTags[key]
    Scaffold(
        containerColor = pageContainerColor(),
        topBar = {
            SmallTopAppBar(
                color = pageContainerColor(),
                title = "主页",
                navigationIcon = { BackIconButton { nav.pop() } },
                actions = {
                    ProfileMoreAction(tag = tag, onSelect = { picked -> FollowTags.set(key, picked) })
                },
            )
        },
    ) { padding ->
        ProfileContent(nav, uid, Modifier.fillMaxSize().padding(padding))
    }
}

/**
 * 主页右上角的三点菜单：给这个人挂一个关注标记，三种互斥，再点一次已选中的那项就取消。
 * 用 COUI 的 ListPopup 而不是 Material 的 DropdownMenu，和首页筛选菜单同一套观感。
 */
@Composable
private fun ProfileMoreAction(tag: FollowTag?, onSelect: (FollowTag?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        HighlightedIconButton(
            onClick = { expanded = true },
            modifier = Modifier.couiTopAppBarActionItem().testTag("profile_more"),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.semantics { contentDescription = "更多" },
            ) {
                repeat(3) {
                    Box(
                        Modifier
                            .size(3.5.dp)
                            .clip(CircleShape)
                            .background(COUITheme.colorScheme.onSurface)
                    )
                }
            }
        }
        WindowListPopup(
            show = expanded,
            popupPositionProvider = ListPopupDefaults.DropdownPositionProvider,
            onDismissRequest = { expanded = false },
        ) {
            val dismiss = LocalDismissState.current
            ListPopupColumn {
                FollowTag.entries.forEachIndexed { index, item ->
                    DropdownImpl(
                        item = DropdownItem(text = item.menuLabel),
                        optionSize = FollowTag.entries.size,
                        isSelected = item == tag,
                        index = index,
                        groupKey = "tags",
                        onSelectedIndexChange = {
                            onSelect(if (item == tag) null else item)
                            dismiss?.invoke()
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun ProfileContent(
    nav: NavState,
    uid: String?,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
    extraBottom: Dp = 0.dp,
) {
    val scope = rememberCoroutineScope()
    var user by remember(uid) { mutableStateOf<User?>(null) }
    val items = remember(uid) { mutableStateListOf<Status>() }
    var sinceId by remember(uid) { mutableStateOf<String?>(null) }
    var loading by remember(uid) { mutableStateOf(false) }
    var refreshing by remember(uid) { mutableStateOf(false) }
    var end by remember(uid) { mutableStateOf(false) }
    var error by remember(uid) { mutableStateOf<String?>(null) }
    var resolvedUid by remember(uid) { mutableStateOf<String?>(uid) }
    val listState = rememberLazyListState()

    // showRefresh 只给用户主动刷新用：进入页面时的静默加载不驱动下拉动画。
    suspend fun loadStatuses(reset: Boolean, force: Boolean = false, showRefresh: Boolean = false) {
        val target = resolvedUid ?: return
        if (loading) return
        loading = true
        if (reset && showRefresh) refreshing = true
        try {
            val page = App.repo.userStatuses(target, if (reset) null else sinceId, force = force)
            if (reset) items.clear()
            val known = items.mapTo(HashSet()) { it.id }
            page.items.forEach { if (known.add(it.id)) items.add(it) }
            sinceId = page.sinceId
            if (page.items.isEmpty()) end = true
            error = null
        } catch (t: Throwable) {
            error = t.message ?: "加载失败"
        } finally {
            loading = false
            refreshing = false
        }
    }

    LaunchedEffect(uid) {
        val target = uid ?: runCatching { App.repo.ownUid() }.getOrNull()
        if (target.isNullOrBlank()) {
            error = "未登录"
            return@LaunchedEffect
        }
        resolvedUid = target
        user = runCatching { App.repo.profile(target) }.getOrNull()
        loadStatuses(true, force = false)
    }

    // 服务端说这个人被设了特别关注、本机又没记过标记，就补一次初值；之后一律以本机为准。
    LaunchedEffect(user?.id, user?.specialFollow) {
        val loaded = user ?: return@LaunchedEffect
        if (loaded.specialFollow && !FollowTags.has(loaded.id)) {
            FollowTags.set(loaded.id, FollowTag.Special)
        }
    }

    LaunchedEffect(listState, items.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { last ->
                if (last >= items.size && items.isNotEmpty() && !end) loadStatuses(false)
            }
    }

    FullPagePullToRefresh(
        isRefreshing = refreshing,
        onRefresh = {
            scope.launch {
                resolvedUid?.let { user = runCatching { App.repo.profile(it, force = true) }.getOrNull() }
                loadStatuses(true, force = true, showRefresh = true)
            }
        },
        modifier = modifier,
        contentPadding = PaddingValues(top = topInset),
        colors = fullPagePullToRefreshColors(backgroundColor = pageContainerColor()),
        refreshTexts = FullPageRefreshTexts,
        completeHoldDurationMillis = FullPageRefreshHoldMillis,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = topInset + 8.dp,
                bottom = 16.dp + navBottomPadding() + extraBottom,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                ProfileHeader(
                    user = user,
                    tag = FollowTags[user?.id ?: uid],
                    onFollow = user?.let { target ->
                        if (target.id == App.repo.session?.uid) {
                            null
                        } else {
                            {
                                scope.launch {
                                    runCatching { App.repo.follow(target.id, !target.following) }
                                        .onSuccess {
                                            user = target.copy(
                                                following = !target.following,
                                                followers = (target.followers +
                                                    if (target.following) -1 else 1).coerceAtLeast(0),
                                            )
                                            App.notifyFollowChanged()
                                            App.toast(if (target.following) "已取消关注" else "已关注")
                                        }
                                        .onFailure { App.toast(it.message ?: "操作失败") }
                                }
                            }
                        }
                    },
                )
            }
            if (items.isEmpty()) {
                item {
                    when {
                        loading || refreshing -> LoadingState()
                        error != null -> ErrorState(error!!) {
                            scope.launch { loadStatuses(true, force = true, showRefresh = true) }
                        }
                        else -> EmptyState("还没有微博")
                    }
                }
            }
            itemsIndexed(items, key = { _, s -> s.id }) { index, status ->
                val origin = rememberExpandOrigin()
                val topBar = LocalTopBarBounds.current
                SideEffect {
                    origin.listState = listState
                    origin.rowKey = status.id
                    origin.topBar = topBar
                }
                StatusCard(
                    status = status,
                    modifier = Modifier.padding(horizontal = 12.dp).expandOrigin(origin),
                    onOpen = { nav.push(Route.Detail(status.id, status.mid), origin) },
                    onUser = { nav.push(Route.Profile(it.id)) },
                    onImage = { urls, i -> nav.push(Route.Images(urls, i)) },
                    onVideo = { nav.push(Route.Video(it.url, it.title)) },
                    onLike = {
                        scope.launch {
                            runCatching { App.repo.like(status.id, status.liked) }.onSuccess {
                                items[index] = status.copy(
                                    liked = !status.liked,
                                    attitudes = (status.attitudes + if (status.liked) -1 else 1)
                                        .coerceAtLeast(0),
                                )
                            }
                        }
                    },
                    onRepost = { nav.push(Route.Detail(status.id, status.mid), origin) },
                    onComment = { nav.push(Route.Detail(status.id, status.mid), origin) },
                    onOpenQuoted = { quoted, quotedOrigin ->
                        quotedOrigin.attachToRow(listState, status.id, topBar)
                        nav.push(Route.Detail(quoted.id, quoted.mid), quotedOrigin)
                    },
                )
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
private fun ProfileHeader(user: User?, onFollow: (() -> Unit)?, tag: FollowTag? = null) {
    val scheme = COUITheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
                .background(
                    if (!user?.cover.isNullOrBlank()) {
                        Brush.linearGradient(listOf(scheme.primaryContainer, scheme.background))
                    } else {
                        Brush.linearGradient(listOf(scheme.primaryContainer, scheme.surfaceContainer))
                    }
                ),
        ) {
            if (!user?.cover.isNullOrBlank()) {
                AsyncImage(
                    model = user.cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Box(
                    modifier = Modifier
                        .size(78.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(pageContainerColor())
                        .padding(3.dp),
                ) {
                    Avatar(user?.avatar, 72.dp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f).padding(bottom = 4.dp)) {
                    if (tag != null) {
                        FollowTagBadge(tag)
                        Spacer(Modifier.height(2.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            user?.name ?: "加载中…",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                        val membership = user?.membership ?: Membership.None
                        if (membership != Membership.None) {
                            Spacer(Modifier.width(6.dp))
                            MemberBadge(membership)
                        }
                    }
                    if (user?.verified == true) {
                        Text("已认证", fontSize = 11.sp, color = scheme.primary)
                    }
                }
                if (onFollow != null) {
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onFollow,
                        modifier = Modifier.height(36.dp).testTag("follow_button"),
                        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    ) {
                        Text(
                            text = if (user?.following == true) "已关注" else "关注",
                            fontSize = 13.sp,
                        )
                    }
                }
            }
            if (!user?.description.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    user.description,
                    fontSize = 13.sp,
                    color = scheme.onSurfaceSecondary,
                    lineHeight = 18.sp,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(26.dp),
            ) {
                Stat("微博", user?.statuses ?: 0)
                Stat("关注", user?.friends ?: 0)
                Stat("粉丝", user?.followers ?: 0)
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(fmtCount(value), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 12.sp, color = COUITheme.colorScheme.onSurfaceSecondary)
    }
}
