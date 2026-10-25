package com.cobo.app.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cobo.app.App
import com.cobo.app.data.Account
import com.cobo.app.data.Draft
import com.cobo.app.data.HistoryItem
import com.cobo.app.ui.NavState
import com.cobo.app.ui.pageContainerColor
import com.cobo.app.ui.Route
import com.cobo.app.ui.components.Avatar
import com.cobo.app.ui.components.BackIconButton
import com.cobo.app.ui.components.ConfirmDialog
import com.cobo.app.ui.components.EmptyState
import com.cobo.app.ui.components.fmtCount
import com.cobo.app.ui.components.navBottomPadding
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Add
import io.github.suqi8.coui.kmp.icon.extended.Delete
import io.github.suqi8.coui.kmp.theme.COUITheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun formatStamp(millis: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(millis))

@Composable
fun DraftsScreen(nav: NavState) {
    var drafts by remember { mutableStateOf(App.store.drafts()) }
    val current = nav.current

    LaunchedEffect(current) {
        if (current is Route.Drafts) drafts = App.store.drafts()
    }

    Scaffold(
        containerColor = pageContainerColor(),
        topBar = {
            SmallTopAppBar(
                color = pageContainerColor(),
                title = "草稿箱",
                navigationIcon = { BackIconButton { nav.pop() } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 12.dp + navBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (drafts.isEmpty()) {
                item { EmptyState("没有草稿") }
            }
            itemsIndexed(drafts, key = { _, draft -> draft.time }) { index, draft ->
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("draft_row_$index"),
                    onClick = {
                        App.store.removeDraft(draft.time)
                        drafts = App.store.drafts()
                        nav.push(Route.Publish(draft.text))
                    },
                ) {
                    Text(draft.text, fontSize = 14.sp, maxLines = 4)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            formatStamp(draft.time),
                            fontSize = 11.sp,
                            color = COUITheme.colorScheme.onSurfaceSecondary,
                            modifier = Modifier.weight(1f),
                        )
                        HighlightedIconButton(
                            onClick = {
                                App.store.removeDraft(draft.time)
                                drafts = App.store.drafts()
                            },
                        ) {
                            Icon(COUIIcons.Delete, "删除", Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(nav: NavState) {
    var history by remember { mutableStateOf(App.store.history()) }
    var confirm by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = pageContainerColor(),
        topBar = {
            SmallTopAppBar(
                color = pageContainerColor(),
                title = "浏览历史",
                navigationIcon = { BackIconButton { nav.pop() } },
                actions = {
                    if (history.isNotEmpty()) {
                        TextButton(text = "清空", onClick = { confirm = true })
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(top = 6.dp, bottom = 6.dp + navBottomPadding()),
        ) {
            if (history.isEmpty()) {
                item { EmptyState("还没有浏览记录") }
            }
            itemsIndexed(history, key = { _, item -> item.id }) { index, item: HistoryItem ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("history_row_$index")
                        .clickable { nav.push(Route.Detail(item.id, item.mid)) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(item.text.ifBlank { "（无正文）" }, fontSize = 14.sp, maxLines = 2)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${item.user}  ${formatStamp(item.time)}",
                        fontSize = 11.sp,
                        color = COUITheme.colorScheme.onSurfaceSecondary,
                    )
                }
            }
        }
    }

    if (confirm) {
        ConfirmDialog(
            title = "清空浏览历史",
            message = "会删除本地保存的全部浏览记录。",
            confirmText = "清空",
            onDismiss = { confirm = false },
            onConfirm = {
                App.store.clearHistory()
                history = emptyList()
                confirm = false
            },
        )
    }
}

@Composable
fun AccountsScreen(nav: NavState) {
    var accounts by remember { mutableStateOf(App.store.accounts()) }
    var currentCookie by remember { mutableStateOf(App.repo.cookie()) }
    var pendingDelete by remember { mutableStateOf<Account?>(null) }

    Scaffold(
        containerColor = pageContainerColor(),
        topBar = {
            SmallTopAppBar(
                color = pageContainerColor(),
                title = "账号管理",
                navigationIcon = { BackIconButton { nav.pop() } },
                actions = {
                    HighlightedIconButton(onClick = {
                        nav.push(Route.Login(addAccount = true, fresh = true))
                    }) {
                        Icon(COUIIcons.Add, "添加账号", Modifier.size(24.dp))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 12.dp + navBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (accounts.isEmpty()) {
                item { EmptyState("还没有保存的账号") }
            }
            itemsIndexed(accounts, key = { _, account -> account.uid }) { index, account ->
                val current = account.cookie == currentCookie
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("account_row_$index"),
                    insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    onClick = {
                        App.repo.updateSession(account.cookie, account.uid)
                        currentCookie = account.cookie
                        nav.reset(Route.Main)
                    },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(account.avatar, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    account.name.ifBlank { "微博账号" },
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                if (current) {
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "当前",
                                        fontSize = 10.sp,
                                        color = COUITheme.colorScheme.onPrimary,
                                        modifier = Modifier
                                            .background(COUITheme.colorScheme.primary, CircleShape)
                                            .padding(horizontal = 7.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            Text(
                                "UID ${account.uid}",
                                fontSize = 11.sp,
                                color = COUITheme.colorScheme.onSurfaceSecondary,
                            )
                        }
                        HighlightedIconButton(onClick = { pendingDelete = account }) {
                            Icon(COUIIcons.Delete, "删除", Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { account ->
        ConfirmDialog(
            title = "移除账号",
            message = "只删除本地保存的登录信息，不会影响微博账号本身。",
            confirmText = "移除",
            onDismiss = { pendingDelete = null },
            onConfirm = {
                App.store.removeAccount(account.uid)
                accounts = App.store.accounts()
                pendingDelete = null
            },
        )
    }
}
