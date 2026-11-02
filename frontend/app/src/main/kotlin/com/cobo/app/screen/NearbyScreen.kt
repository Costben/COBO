package com.cobo.app.screen

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cobo.app.App
import com.cobo.app.ui.NavState
import com.cobo.app.ui.PinnedBarDensity
import com.cobo.app.ui.pinnedHeight
import com.cobo.app.ui.pageContainerColor
import com.cobo.app.ui.rememberExpandOrigin
import com.cobo.app.ui.Route
import com.cobo.app.ui.expandOrigin
import com.cobo.app.ui.components.BackIconButton
import com.cobo.app.ui.components.EmptyState
import com.cobo.app.ui.components.ErrorState
import com.cobo.app.ui.components.LoadingState
import com.cobo.app.ui.components.StatusCard
import com.cobo.app.ui.components.navBottomPadding
import io.github.suqi8.coui.kmp.basic.CircularProgressIndicator
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.LocalEdgeToEdge
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.basic.couiTopAppBarActionItem
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.layerBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Location
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun NearbyScreen(nav: NavState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var coords by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var locating by remember { mutableStateOf(false) }
    var locateError by remember { mutableStateOf<String?>(null) }
    var needPermission by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val backdrop = rememberLayerBackdrop()
    val topBarHeight = pinnedHeight(TopAppBarDefaults.SmallTopAppBarCenterHeight)

    // 换一次坐标换一个流：同城页的翻页游标里带着本次定位的经纬度，跨坐标续爬会串城。
    val store = remember(coords) {
        coords?.let { (lat, lon) ->
            FeedStore("nearby") { cursor, force ->
                App.repo.nearby(lat.toString(), lon.toString(), cursor, force)
            }
        }
    }

    fun locate() {
        scope.launch {
            locating = true
            locateError = null
            try {
                val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val location = withContext(Dispatchers.IO) {
                    listOf(
                        LocationManager.GPS_PROVIDER,
                        LocationManager.NETWORK_PROVIDER,
                        LocationManager.PASSIVE_PROVIDER,
                    ).mapNotNull { provider ->
                        runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
                    }.maxByOrNull { it.time }
                }
                if (location == null) {
                    locateError = "暂时拿不到位置，请到系统设置里打开定位后再试"
                } else {
                    val pair = location.latitude to location.longitude
                    // 坐标没变也要给个反应：同一个位置再点一次就是「重新拉一遍这条流」。
                    if (pair == coords) store?.refresh() else coords = pair
                }
            } catch (t: Throwable) {
                locateError = t.message ?: "定位失败"
            } finally {
                locating = false
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { it }) {
            needPermission = false
            locate()
        } else {
            needPermission = true
        }
    }

    LaunchedEffect(Unit) {
        val granted = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            locate()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            )
        }
    }

    LaunchedEffect(store) { store?.loadInitial() }

    LaunchedEffect(listState, store) {
        val current = store ?: return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { last ->
                if (last >= current.items.size - 2 && current.items.isNotEmpty()) current.loadMore()
            }
    }

    Box(Modifier.fillMaxSize().background(pageContainerColor())) {
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .background(pageContainerColor())
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().testTag("nearby_list"),
                // 内容从顶栏玻璃下面滚过去，顶栏才有东西可采样。
                contentPadding = PaddingValues(
                    start = 12.dp,
                    top = topBarHeight + 8.dp,
                    end = 12.dp,
                    bottom = 8.dp + navBottomPadding(),
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val feed = store
                if (feed == null) {
                    item {
                        when {
                            locateError != null -> ErrorState(locateError!!) { locate() }
                            needPermission -> EmptyState("需要定位权限才能看附近")
                            else -> LoadingState()
                        }
                    }
                } else {
                    if (feed.items.isEmpty()) {
                        item {
                            when {
                                feed.refreshing || feed.loading -> LoadingState()
                                feed.error != null -> ErrorState(feed.error!!) {
                                    scope.launch { feed.refresh() }
                                }
                                else -> EmptyState("附近暂时没有内容")
                            }
                        }
                    }
                    itemsIndexed(feed.items, key = { _, s -> s.id }) { index, status ->
                        val origin = rememberExpandOrigin()
                        StatusCard(
                            status = status,
                            modifier = Modifier.expandOrigin(origin),
                            onOpen = { nav.push(Route.Detail(status.id, status.mid), origin) },
                            onOpenQuoted = { quoted, quotedOrigin ->
                                nav.push(Route.Detail(quoted.id, quoted.mid), quotedOrigin)
                            },
                            onUser = { nav.push(Route.Profile(it.id)) },
                            onImage = { pics, i -> nav.push(Route.Images(pics, i)) },
                            onVideo = { nav.push(Route.Video(it.url, it.title)) },
                            onLike = { scope.launch { feed.like(index) } },
                            onRepost = { nav.push(Route.Detail(status.id, status.mid), origin) },
                            onComment = { nav.push(Route.Detail(status.id, status.mid), origin) },
                        )
                    }
                    if (feed.items.isNotEmpty()) {
                        item { if (feed.loading) LoadingState() }
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
                    title = "附近",
                    modifier = Modifier.align(Alignment.TopCenter),
                    color = pageContainerColor(),
                    showDivider = false,
                    gradientGlass = true,
                    navigationIcon = { BackIconButton { nav.pop() } },
                    actions = {
                        val busy = locating || store?.refreshing == true
                        HighlightedIconButton(
                            onClick = { locate() },
                            modifier = Modifier.couiTopAppBarActionItem().testTag("nearby_relocate"),
                        ) {
                            // 定位期间把针换成转圈：同一个位置再点一次是静默重拉，不给点反馈就看不出来。
                            if (busy) {
                                Box(Modifier.semantics { contentDescription = "重新定位" }) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                    )
                                }
                            } else {
                                Icon(COUIIcons.Location, "重新定位", Modifier.size(24.dp))
                            }
                        }
                    },
                )
            }
        }
    }
}
