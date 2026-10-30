package com.cobo.app.screen

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cobo.app.App
import com.cobo.app.data.UpdateInfo
import com.cobo.app.data.Updater
import com.cobo.app.ui.NavState
import com.cobo.app.ui.PinnedBarDensity
import com.cobo.app.ui.components.BackIconButton
import com.cobo.app.ui.components.ErrorState
import com.cobo.app.ui.components.LoadingState
import com.cobo.app.ui.components.MarkdownText
import com.cobo.app.ui.components.navBottomPadding
import com.cobo.app.ui.pageContainerColor
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.couiTopAppBarActionItem
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Refresh
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * 更新日志。把 release 列表按版本从新到旧铺开，任意一版都能把更新弹窗原样放一遍 ——
 * 用来核对弹窗界面、release notes 的 Markdown 排版，以及应用内下载的实际速度。
 */
@Composable
fun ChangelogScreen(nav: NavState) {
    val context = LocalContext.current
    val current = remember { Updater.currentVersion(context) }
    var releases by remember { mutableStateOf<List<UpdateInfo>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload) {
        error = null
        runCatching { Updater.releases(App.settings.acceptPrerelease) }
            .onSuccess { releases = it }
            .onFailure {
                releases = null
                error = it.message ?: "获取更新日志失败"
            }
    }

    Scaffold(
        containerColor = pageContainerColor(),
        topBar = {
            PinnedBarDensity {
                SmallTopAppBar(
                    color = pageContainerColor(),
                    title = "更新日志",
                    navigationIcon = { BackIconButton { nav.pop() } },
                    actions = {
                        HighlightedIconButton(
                            onClick = { reload++ },
                            modifier = Modifier.couiTopAppBarActionItem().testTag("changelog_reload"),
                        ) {
                            Icon(COUIIcons.Refresh, "刷新", Modifier.size(24.dp))
                        }
                    },
                )
            }
        },
    ) { padding ->
        val list = releases
        when {
            error != null -> Box(Modifier.fillMaxSize().padding(padding)) {
                ErrorState(error!!) { reload++ }
            }

            list == null -> Box(Modifier.fillMaxSize().padding(padding)) { LoadingState() }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    top = 10.dp,
                    end = 12.dp,
                    bottom = 12.dp + navBottomPadding(),
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(list, key = { it.version }) { release ->
                    ReleaseCard(release, isCurrent = release.version == current)
                }
            }
        }
    }
}

/** 一版一张卡：版本号、日期、正文，末尾放一个把更新弹窗再播一遍的按钮。 */
@Composable
private fun ReleaseCard(release: UpdateInfo, isCurrent: Boolean) {
    Card(modifier = Modifier.fillMaxWidth().testTag("changelog_${release.version}")) {
        // COUI 的 Card 默认 insideMargin 是 0，内容会顶着卡片边，这里自己留一圈。
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("v${release.version}", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                if (isCurrent) Tag("当前版本")
                if (release.prerelease) Tag("预发布")
                Spacer(Modifier.weight(1f))
                Text(
                    release.publishedAt.take(10),
                    fontSize = 11.sp,
                    color = COUITheme.colorScheme.onSurfaceSecondary,
                )
            }
            if (release.notes.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                MarkdownText(release.notes)
            }
            Spacer(Modifier.height(6.dp))
            TextButton(
                text = "预览更新弹窗",
                onClick = { App.previewUpdate(release) },
                modifier = Modifier.testTag("changelog_preview_${release.version}"),
            )
        }
    }
}

/** 版本号后面的小标签。 */
@Composable
private fun Tag(text: String) {
    Text(
        text,
        fontSize = 11.sp,
        color = COUITheme.colorScheme.primary,
        modifier = Modifier.padding(start = 6.dp),
    )
}
