package com.cobo.app.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cobo.app.App
import com.cobo.app.data.FollowTag
import com.cobo.app.data.FollowTags
import com.cobo.app.data.User
import com.cobo.app.ui.NavState
import com.cobo.app.ui.Route
import com.cobo.app.ui.components.Avatar
import com.cobo.app.ui.components.EmptyState
import com.cobo.app.ui.components.PreferenceDivider
import com.cobo.app.ui.components.navBottomPadding
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.cardListPositionOf
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** 纯本地模式下的「消息」页：私信和通知都挂在账号上，访客身份拿不到。 */
@Composable
fun LocalMessagesPlaceholder(nav: NavState, topInset: Dp) {
    Column(
        modifier = Modifier.fillMaxSize().padding(top = topInset),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EmptyState("纯本地模式没有私信和通知\n登录微博账号后可用")
        TextButton(
            text = "登录微博账号",
            onClick = { nav.push(Route.Login()) },
            modifier = Modifier.testTag("local_login"),
        )
    }
}

/**
 * 纯本地模式下的「我」页：没有自己的主页可看，换成本机记下的东西——
 * 本地关注的人、浏览历史，以及登录入口。
 */
@Composable
fun LocalHome(nav: NavState, topInset: Dp, extraBottom: Dp) {
    val scheme = COUITheme.colorScheme
    val uids = FollowTags.uids(FollowTag.Local)
    // 标记表只有 uid，昵称和头像现取；主页接口有缓存，来回切页不会重复打。
    val users = remember { mutableStateMapOf<String, User>() }
    LaunchedEffect(uids) {
        coroutineScope {
            uids.filter { it !in users }.map { uid ->
                async { runCatching { App.repo.profile(uid) }.getOrNull()?.let { users[uid] = it } }
            }.awaitAll()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 12.dp,
            top = topInset + 14.dp,
            end = 12.dp,
            bottom = 16.dp + navBottomPadding() + extraBottom,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Text("纯本地模式", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "没有登录任何账号，内容以微博访客身份读取，只能看不能赞、评、转。" +
                        "在别人主页点「本地关注」，他的微博就会出现在首页的「本地关注」里，名单只存在这台手机上。",
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = scheme.onSurfaceSecondary,
                )
                Spacer(Modifier.height(4.dp))
                TextButton(
                    text = "登录微博账号",
                    onClick = { nav.push(Route.Login()) },
                    modifier = Modifier.testTag("local_login"),
                )
            }
        }

        item {
            Text(
                "本地关注 · ${uids.size} 人",
                fontSize = 13.sp,
                color = scheme.onSurfaceSecondary,
                modifier = Modifier.padding(start = 6.dp, top = 6.dp),
            )
        }
        if (uids.isEmpty()) {
            item { EmptyState("还没有本地关注的人\n去搜索里找到他，在主页点「本地关注」") }
        } else {
            item {
                Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                    uids.forEachIndexed { index, uid ->
                        if (index > 0) PreferenceDivider()
                        LocalFollowRow(
                            user = users[uid],
                            uid = uid,
                            position = cardListPositionOf(index, uids.size),
                            onClick = { nav.push(Route.Profile(uid)) },
                        )
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
                ArrowPreference(
                    title = "浏览历史",
                    modifier = Modifier.testTag("local_history"),
                    summary = "${App.store.history().size} 条记录",
                    cardListPosition = CardListPosition.Head,
                    onClick = { nav.push(Route.History) },
                )
                PreferenceDivider()
                ArrowPreference(
                    title = "话题 / 超话",
                    modifier = Modifier.testTag("local_topic"),
                    summary = "按话题浏览",
                    cardListPosition = CardListPosition.Tail,
                    onClick = { nav.push(Route.Topic()) },
                )
            }
        }
    }
}

@Composable
private fun LocalFollowRow(user: User?, uid: String, position: CardListPosition, onClick: () -> Unit) {
    ArrowPreference(
        title = user?.name ?: "UID $uid",
        modifier = Modifier.testTag("local_follow_$uid"),
        summary = user?.description?.takeIf { it.isNotBlank() },
        startAction = {
            Avatar(user?.avatar, 36.dp)
            Spacer(Modifier.width(12.dp))
        },
        cardListPosition = position,
        onClick = onClick,
    )
}
