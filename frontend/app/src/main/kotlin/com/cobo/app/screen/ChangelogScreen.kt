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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import com.cobo.app.ui.blurEffectsEnabled
import com.cobo.app.ui.layerBackdropIf
import com.cobo.app.ui.PinnedBarDensity
import com.cobo.app.ui.pinnedHeight
import com.cobo.app.ui.components.BackIconButton
import com.cobo.app.ui.components.ErrorState
import com.cobo.app.ui.components.LoadingState
import com.cobo.app.ui.components.MarkdownText
import com.cobo.app.ui.components.navBottomPadding
import com.cobo.app.ui.glassBarContentTop
import com.cobo.app.ui.pageContainerColor
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.LocalEdgeToEdge
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.basic.couiTopAppBarActionItem
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.layerBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
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

    val backdrop = rememberLayerBackdrop()
    val topBarHeight = pinnedHeight(TopAppBarDefaults.SmallTopAppBarCenterHeight)

    Box(Modifier.fillMaxSize().background(pageContainerColor())) {
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdropIf(blurEffectsEnabled(), backdrop)
                .background(pageContainerColor()),
        ) {
            val list = releases
            when {
                error != null -> Box(Modifier.fillMaxSize().padding(top = topBarHeight)) {
                    ErrorState(error!!) { reload++ }
                }

                list == null -> Box(Modifier.fillMaxSize().padding(top = topBarHeight)) {
                    LoadingState()
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = glassBarContentTop(topBarHeight, 10.dp),
                        end = 16.dp,
                        bottom = 12.dp + navBottomPadding(),
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(list, key = { it.version }) { release ->
                        ReleaseCard(release, isCurrent = release.version == current)
                    }
                }
            }
        }

        CompositionLocalProvider(
            LocalBackdrop provides if (blurEffectsEnabled()) backdrop else null,
            // 内容铺到状态栏下面，顶栏玻璃才有东西可采样（COUI 17 规范要求 edgeToEdge）。
            LocalEdgeToEdge provides true,
        ) {
            PinnedBarDensity {
                SmallTopAppBar(
                    title = "更新日志",
                    modifier = Modifier.align(Alignment.TopCenter),
                    color = pageContainerColor(),
                    showDivider = false,
                    gradientGlass = blurEffectsEnabled(),
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
        }
    }
}

/** 一版一张卡：版本号、日期、正文，末尾放一个把更新弹窗再播一遍的按钮。 */
@Composable
private fun ReleaseCard(release: UpdateInfo, isCurrent: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("changelog_${release.version}"),
        cornerRadius = CardDefaults.GroupCornerRadius,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        weight = CardDefaults.GroupWeight,
    ) {
        Column(Modifier.fillMaxWidth()) {
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
