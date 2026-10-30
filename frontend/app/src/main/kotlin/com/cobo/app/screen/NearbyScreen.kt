package com.cobo.app.screen

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cobo.app.App
import com.cobo.app.data.Status
import com.cobo.app.ui.NavState
import com.cobo.app.ui.PinnedBarDensity
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
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun NearbyScreen(nav: NavState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items = remember { mutableStateListOf<Status>() }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var coords by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    val listState = rememberLazyListState()

    fun locate() {
        scope.launch {
            loading = true
            error = null
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
                    error = "暂时拿不到位置，请到系统设置里打开定位后再试"
                } else {
                    coords = location.latitude to location.longitude
                }
            } catch (t: Throwable) {
                error = t.message ?: "定位失败"
            } finally {
                loading = false
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { it }) locate()
        else error = "需要定位权限才能看附近"
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

    LaunchedEffect(coords) {
        val pair = coords ?: return@LaunchedEffect
        loading = true
        error = null
        try {
            val page = App.repo.nearby(
                lat = pair.first.toString(),
                lon = pair.second.toString(),
                force = true,
            )
            val known = HashSet<String>()
            items.clear()
            items.addAll(page.items.filter { known.add(it.id) })
            if (items.isEmpty()) error = "附近暂时没有内容"
        } catch (t: Throwable) {
            error = t.message ?: "附近加载失败"
        } finally {
            loading = false
        }
    }

    Scaffold(
        containerColor = pageContainerColor(),
        topBar = {
            PinnedBarDensity {
                SmallTopAppBar(
                    color = pageContainerColor(),
                    title = "附近",
                    navigationIcon = { BackIconButton { nav.pop() } },
                    actions = { TextButton(text = "重新定位", onClick = { locate() }) },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 8.dp + navBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (items.isEmpty()) {
                item {
                    when {
                        loading -> LoadingState()
                        error != null -> ErrorState(error!!) { locate() }
                        else -> EmptyState("正在等待定位")
                    }
                }
            }
            itemsIndexed(items, key = { _, s -> s.id }) { index, status ->
                val origin = rememberExpandOrigin()
                StatusCard(
                    status = status,
                    modifier = Modifier.expandOrigin(origin),
                    onOpen = { nav.push(Route.Detail(status.id, status.mid), origin) },
                    onOpenQuoted = { quoted, quotedOrigin ->
                        nav.push(Route.Detail(quoted.id, quoted.mid), quotedOrigin)
                    },
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
                )
            }
        }
    }
}
