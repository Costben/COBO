package com.cobo.app.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cobo.app.App
import com.cobo.app.data.EditVersion
import com.cobo.app.data.Pic
import com.cobo.app.data.formatWeiboTime
import com.cobo.app.ui.NavState
import com.cobo.app.ui.pageContainerColor
import com.cobo.app.ui.Route
import com.cobo.app.ui.components.BackIconButton
import com.cobo.app.ui.components.EmptyState
import com.cobo.app.ui.components.ErrorState
import com.cobo.app.ui.components.ImageGrid
import com.cobo.app.ui.components.LoadingState
import com.cobo.app.ui.components.WeiboText
import com.cobo.app.ui.components.navBottomPadding
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.layerBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.launch

@Composable
fun EditHistoryScreen(nav: NavState, mid: String) {
    val scope = rememberCoroutineScope()
    var versions by remember(mid) { mutableStateOf<List<EditVersion>?>(null) }
    var error by remember(mid) { mutableStateOf<String?>(null) }
    val backdrop = rememberLayerBackdrop()

    suspend fun load() {
        error = null
        runCatching { App.repo.editHistory(mid) }
            .onSuccess { versions = it }
            .onFailure {
                error = it.message ?: "加载失败"
                versions = emptyList()
            }
    }

    LaunchedEffect(mid) { load() }

    val scheme = COUITheme.colorScheme
    val topBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
        TopAppBarDefaults.SmallTopAppBarCenterHeight

    Box(Modifier.fillMaxSize().background(pageContainerColor())) {
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .background(pageContainerColor())
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("edit_history_list"),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    top = topBarHeight + 8.dp,
                    end = 12.dp,
                    bottom = navBottomPadding() + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val list = versions
                if (list == null) {
                    item { LoadingState() }
                } else if (error != null) {
                    item {
                        ErrorState(error!!) {
                            versions = null
                            scope.launch { load() }
                        }
                    }
                } else if (list.isEmpty()) {
                    item { EmptyState("这条微博没有编辑记录") }
                } else {
                    item {
                        Text(
                            "共 ${list.size} 条记录，最新版本在最上方",
                            fontSize = 12.sp,
                            color = scheme.onSurfaceSecondary,
                            modifier = Modifier.padding(start = 6.dp, bottom = 2.dp),
                        )
                    }
                    itemsIndexed(list) { index, version ->
                        VersionCard(
                            version = version,
                            current = index == 0,
                            onImage = { urls, i -> nav.push(Route.Images(urls, i)) },
                        )
                    }
                }
            }
        }

        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            SmallTopAppBar(
                title = "编辑记录",
                modifier = Modifier.align(Alignment.TopCenter),
                color = pageContainerColor(),
                showDivider = false,
                gradientGlass = true,
                navigationIcon = { BackIconButton { nav.pop() } },
            )
        }
    }
}

@Composable
private fun VersionCard(
    version: EditVersion,
    current: Boolean,
    onImage: (List<String>, Int) -> Unit,
) {
    val scheme = COUITheme.colorScheme
    Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (current) {
                Text(
                    "当前版本",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = scheme.onPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(scheme.primary)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Text(
                formatWeiboTime(version.createdAt),
                fontSize = 12.sp,
                color = scheme.onSurfaceSecondary,
                modifier = Modifier.padding(start = if (current) 8.dp else 0.dp),
            )
        }
        if (version.note.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                version.note,
                fontSize = 14.sp,
                color = scheme.onSurfaceSecondary,
            )
        } else {
            if (version.textHtml.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                WeiboText(
                    html = version.textHtml,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                )
            }
            if (version.pics.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                val pics = version.pics.map { Pic(thumb = it, large = it, width = 0, height = 0) }
                ImageGrid(pics, compact = true, onImage = onImage)
            }
        }
    }
}
