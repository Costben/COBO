package com.cobo.app.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cobo.app.App
import com.cobo.app.data.Updater
import com.cobo.app.ui.NavState
import com.cobo.app.ui.PinnedBarDensity
import com.cobo.app.ui.Route
import com.cobo.app.ui.blurEffectsEnabled
import com.cobo.app.ui.components.BackIconButton
import com.cobo.app.ui.components.PreferenceDivider
import com.cobo.app.ui.components.SectionTitle
import com.cobo.app.ui.components.navBottomPadding
import com.cobo.app.ui.glassBarContentTop
import com.cobo.app.ui.layerBackdropIf
import com.cobo.app.ui.pageContainerColor
import com.cobo.app.ui.pinnedHeight
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.LocalEdgeToEdge
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.preference.AppInfoPreference
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.launch

private data class UpstreamRef(
    val title: String,
    val summary: String,
    val status: String,
    val url: String,
)

private val UI_UPSTREAMS = listOf(
    UpstreamRef(
        title = "COUI 17",
        summary = "自维护 ColorOS 17 Compose 组件库 · Costben/coui17",
        status = "Apache-2.0",
        url = "https://github.com/Costben/coui17",
    ),
    UpstreamRef(
        title = "COUI Kit",
        summary = "io.github.itoseo:coui · Jetpack Compose 组件与视觉效果库",
        status = "MPL-2.0",
        url = "https://github.com/ItosEO/COUI-Kit",
    ),
    UpstreamRef(
        title = "COUI",
        summary = "suqi8/coui · ColorOS 16 跨平台 Compose UI 组件库上游",
        status = "Apache-2.0",
        url = "https://github.com/suqi8/coui",
    ),
    UpstreamRef(
        title = "Miuix",
        summary = "compose-miuix-ui/miuix · Compose Multiplatform 基础组件上游",
        status = "Apache-2.0",
        url = "https://github.com/compose-miuix-ui/miuix",
    ),
)

private val DESIGN_REFERENCES = listOf(
    UpstreamRef(
        title = "c001apk-coui",
        summary = "Costben/c001apk · COUI 组件接入与二级页面交互参考",
        status = "GitHub",
        url = "https://github.com/Costben/c001apk",
    ),
    UpstreamRef(
        title = "BiliCO",
        summary = "Costben/BiliCO · 同源 COUI 17 搜索过渡与界面骨架参考",
        status = "GitHub",
        url = "https://github.com/Costben/BiliCO",
    ),
)

private val CORE_LIBRARIES = listOf(
    UpstreamRef(
        title = "AndroidX Media3",
        summary = "ExoPlayer 视频流播放与媒体控制引擎",
        status = "Apache-2.0",
        url = "https://github.com/androidx/media",
    ),
    UpstreamRef(
        title = "Coil 3",
        summary = "Kotlin Multiplatform 头像、九宫格与动图加载",
        status = "Apache-2.0",
        url = "https://github.com/coil-kt/coil",
    ),
    UpstreamRef(
        title = "OkHttp",
        summary = "HTTP/2 网络连接池、Cookie 复用与媒体上传",
        status = "Apache-2.0",
        url = "https://github.com/square/okhttp",
    ),
    UpstreamRef(
        title = "Jetpack Compose",
        summary = "声明式 UI 框架与预测性返回手势转场",
        status = "Apache-2.0",
        url = "https://github.com/JetBrains/compose-multiplatform",
    ),
)

@Composable
fun AboutScreen(nav: NavState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appIcon = rememberAppIconPainter(context)
    val versionName = remember(context) { Updater.currentVersion(context) }
    val versionCode = remember(context) { currentVersionCode(context) }
    val versionText = if (versionCode > 0L) "v$versionName ($versionCode)" else "v$versionName"

    val pageColor = pageContainerColor()
    val blurEffects = blurEffectsEnabled()
    val backdrop = rememberLayerBackdrop()
    val topBarHeight = pinnedHeight(TopAppBarDefaults.SmallTopAppBarCenterHeight)

    Box(Modifier.fillMaxSize().background(pageColor)) {
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdropIf(blurEffects, backdrop)
                .background(pageColor),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    top = glassBarContentTop(topBarHeight, 10.dp),
                    end = 12.dp,
                    bottom = 24.dp + navBottomPadding(),
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "app-header") {
                    AppInfoPreference(
                        appName = "Cobo",
                        appVersion = versionText,
                        modifier = Modifier.testTag("about_header"),
                        icon = appIcon,
                        onVersionLongClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(ClipData.newPlainText("Cobo", "Cobo $versionText"))
                            Toast.makeText(context, "已复制版本信息", Toast.LENGTH_SHORT).show()
                        },
                    )
                }

                item(key = "project-title") { SectionTitle("项目", topPadding = 8.dp) }

                item(key = "project") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = CardDefaults.GroupCornerRadius,
                        insideMargin = PaddingValues(0.dp),
                        weight = CardDefaults.GroupWeight,
                    ) {
                        ArrowPreference(
                            title = "开源仓库",
                            summary = "https://github.com/Costben/COBO",
                            statusText = "GitHub",
                            modifier = Modifier.testTag("about_repo"),
                            cardListPosition = CardListPosition.Head,
                            onClick = { openExternalUrl(context, "https://github.com/Costben/COBO") },
                        )
                        PreferenceDivider()
                        ArrowPreference(
                            title = "检查更新",
                            summary = if (App.checkingUpdate) "正在检查…" else "当前版本 $versionName",
                            modifier = Modifier.testTag("about_check_update"),
                            cardListPosition = CardListPosition.Middle,
                            onClick = { scope.launch { App.checkUpdate(manual = true) } },
                        )
                        PreferenceDivider()
                        ArrowPreference(
                            title = "更新日志",
                            summary = "查看历史版本与发布说明",
                            modifier = Modifier.testTag("about_changelog"),
                            cardListPosition = CardListPosition.Tail,
                            onClick = { nav.push(Route.Changelog) },
                        )
                    }
                }

                item(key = "ui-upstreams-title") { SectionTitle("前端界面库") }

                item(key = "ui-upstreams") {
                    UpstreamGroupCard(
                        items = UI_UPSTREAMS,
                        tagPrefix = "about_ui",
                        onOpen = { url -> openExternalUrl(context, url) },
                    )
                }

                item(key = "design-refs-title") { SectionTitle("交互与样式参考") }

                item(key = "design-refs") {
                    UpstreamGroupCard(
                        items = DESIGN_REFERENCES,
                        tagPrefix = "about_ref",
                        onOpen = { url -> openExternalUrl(context, url) },
                    )
                }

                item(key = "core-libs-title") { SectionTitle("核心开源依赖") }

                item(key = "core-libs") {
                    UpstreamGroupCard(
                        items = CORE_LIBRARIES,
                        tagPrefix = "about_lib",
                        onOpen = { url -> openExternalUrl(context, url) },
                    )
                }

                item(key = "disclaimer") {
                    Text(
                        text = "第三方微博客户端，基于 m.weibo.cn Web API 构建，仅用于个人学习与研究。所有内容、商标与版权归原平台及对应开源项目作者所有。",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = COUITheme.colorScheme.onSurfaceSecondary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
        }

        CompositionLocalProvider(
            LocalBackdrop provides if (blurEffects) backdrop else null,
            LocalEdgeToEdge provides true,
        ) {
            PinnedBarDensity {
                SmallTopAppBar(
                    title = "关于",
                    modifier = Modifier.align(Alignment.TopCenter),
                    color = pageColor,
                    showDivider = false,
                    gradientGlass = blurEffects,
                    navigationIcon = { BackIconButton { nav.pop() } },
                )
            }
        }
    }
}

@Composable
private fun UpstreamGroupCard(
    items: List<UpstreamRef>,
    tagPrefix: String,
    onOpen: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = CardDefaults.GroupCornerRadius,
        insideMargin = PaddingValues(0.dp),
        weight = CardDefaults.GroupWeight,
    ) {
        items.forEachIndexed { index, item ->
            val position = when {
                items.size == 1 -> CardListPosition.None
                index == 0 -> CardListPosition.Head
                index == items.lastIndex -> CardListPosition.Tail
                else -> CardListPosition.Middle
            }
            if (index > 0) PreferenceDivider()
            ArrowPreference(
                title = item.title,
                summary = item.summary,
                statusText = item.status,
                modifier = Modifier.testTag("${tagPrefix}_$index"),
                cardListPosition = position,
                onClick = { onOpen(item.url) },
            )
        }
    }
}

@Composable
private fun rememberAppIconPainter(context: Context): Painter? = remember(context) {
    runCatching {
        val drawable = context.packageManager.getApplicationIcon(context.packageName)
        val sizePx = 192
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        BitmapPainter(bitmap.asImageBitmap())
    }.getOrNull()
}

private fun currentVersionCode(context: Context): Long = runCatching {
    val info: PackageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        info.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        info.versionCode.toLong()
    }
}.getOrDefault(0L)

private fun openExternalUrl(context: Context, url: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }.onFailure {
        Toast.makeText(context, "没有可用的浏览器", Toast.LENGTH_SHORT).show()
    }
}
