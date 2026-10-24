package com.cobo.app.screen

import android.webkit.CookieManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.SingletonImageLoader
import com.cobo.app.App
import com.cobo.app.ui.NavState
import com.cobo.app.ui.pageContainerColor
import com.cobo.app.ui.Route
import com.cobo.app.ui.components.BackIconButton
import com.cobo.app.ui.components.ConfirmDialog
import com.cobo.app.ui.components.navBottomPadding
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import io.github.suqi8.coui.kmp.theme.COUITheme

@Composable
fun SettingsScreen(nav: NavState) {
    val context = LocalContext.current
    var darkMode by remember { mutableStateOf(App.settings.darkMode) }
    var fontScale by remember { mutableStateOf(App.settings.fontScale) }
    var message by remember { mutableStateOf("") }
    var about by remember { mutableStateOf(false) }
    var logout by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = pageContainerColor(),
        topBar = {
            SmallTopAppBar(
                color = pageContainerColor(),
                title = "设置",
                navigationIcon = { BackIconButton { nav.pop() } },
            )
        },
    ) { padding ->
        val bottomInset = 24.dp + navBottomPadding()
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 12.dp, top = 10.dp, end = 12.dp, bottom = bottomInset),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    ArrowPreference(
                        title = "深色模式",
                        modifier = Modifier.testTag("settings_dark"),
                        summary = when (darkMode) {
                            1 -> "浅色"
                            2 -> "深色"
                            else -> "跟随系统"
                        },
                        cardListPosition = CardListPosition.Head,
                        onClick = {
                            darkMode = App.settings.cycleDarkMode()
                        },
                    )
                    HorizontalDivider()
                    ArrowPreference(
                        title = "字号",
                        modifier = Modifier.testTag("settings_font"),
                        summary = "${(fontScale * 100).toInt()}%",
                        cardListPosition = CardListPosition.Tail,
                        onClick = {
                            fontScale = App.settings.cycleFontScale()
                        },
                    )
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    ArrowPreference(
                        title = "账号管理",
                        modifier = Modifier.testTag("settings_accounts"),
                        summary = "多账号切换",
                        cardListPosition = CardListPosition.Head,
                        onClick = { nav.push(Route.Accounts) },
                    )
                    HorizontalDivider()
                    ArrowPreference(
                        title = "草稿箱",
                        modifier = Modifier.testTag("settings_drafts"),
                        summary = "${App.store.drafts().size} 条草稿",
                        onClick = { nav.push(Route.Drafts) },
                    )
                    HorizontalDivider()
                    ArrowPreference(
                        title = "浏览历史",
                        modifier = Modifier.testTag("settings_history"),
                        summary = "${App.store.history().size} 条记录",
                        onClick = { nav.push(Route.History) },
                    )
                    HorizontalDivider()
                    ArrowPreference(
                        title = "收藏",
                        modifier = Modifier.testTag("settings_favorites"),
                        summary = "查看收藏的微博",
                        onClick = { nav.push(Route.Container("230267", "收藏")) },
                    )
                    HorizontalDivider()
                    ArrowPreference(
                        title = "话题 / 超话",
                        modifier = Modifier.testTag("settings_topic"),
                        summary = "按话题浏览",
                        onClick = { nav.push(Route.Topic()) },
                    )
                    HorizontalDivider()
                    ArrowPreference(
                        title = "附近",
                        modifier = Modifier.testTag("settings_nearby"),
                        summary = "基于当前位置",
                        cardListPosition = CardListPosition.Tail,
                        onClick = { nav.push(Route.Nearby) },
                    )
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    ArrowPreference(
                        title = "清理缓存",
                        modifier = Modifier.testTag("settings_clear_cache"),
                        summary = "图片与已保存的内容",
                        cardListPosition = CardListPosition.Head,
                        onClick = {
                            runCatching {
                                val loader = SingletonImageLoader.get(context)
                                loader.memoryCache?.clear()
                                loader.diskCache?.clear()
                            }
                            App.cache.clear()
                            message = "已清理图片与内容缓存"
                        },
                    )
                    HorizontalDivider()
                    ArrowPreference(
                        title = "关于",
                        modifier = Modifier.testTag("settings_about"),
                        summary = "Cobo · COUI 前端",
                        onClick = { about = true },
                    )
                    HorizontalDivider()
                    ArrowPreference(
                        title = "退出登录",
                        modifier = Modifier.testTag("settings_logout"),
                        summary = "清除本地会话",
                        cardListPosition = CardListPosition.Tail,
                        onClick = { logout = true },
                    )
                }
            }

            if (message.isNotBlank()) {
                item {
                    Text(
                        message,
                        fontSize = 12.sp,
                        color = COUITheme.colorScheme.onSurfaceSecondary,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                }
            }

            item {
                Text(
                    "基于 m.weibo.cn Web API 的第三方微博客户端。\n仅用于个人学习与研究。",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = COUITheme.colorScheme.onSurfaceSecondary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                )
            }
        }
    }

    if (about) {
        ConfirmDialog(
            title = "关于 Cobo",
            message = "Cobo · COUI 前端\n\n使用 COUI（ColorOS 17 设计语言）重建的第三方微博客户端。\n仅用于个人学习与研究。",
            confirmText = "知道了",
            onDismiss = { about = false },
            onConfirm = { about = false },
        )
    }

    if (logout) {
        ConfirmDialog(
            title = "退出登录",
            message = "会清除本地保存的 Cookie，下次需要重新登录。",
            confirmText = "退出",
            onDismiss = { logout = false },
            onConfirm = {
                logout = false
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
                App.repo.logout()
                nav.reset(Route.Login())
            },
        )
    }
}
