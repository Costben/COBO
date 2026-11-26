package com.cobo.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.suqi8.coui.kmp.basic.TabRowDefaults
import io.github.suqi8.coui.kmp.basic.TabRowWithContour
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.isBlurEnabled
import io.github.suqi8.coui.kmp.blur.layerBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * 临时对比页：同一套分段标签栏，分别压在系统「与电脑互联」的白顶栏和首页的灰顶栏上。
 *
 * 只为看清「灰底把胶囊吃掉」这件事，验完即删，不作为正式界面。
 */

/** 系统「与电脑互联」实测：顶栏纯白，下方列表 #EFF0F2。 */
private val LinkerBar = Color(0xFFFFFFFF)
private val LinkerPage = Color(0xFFEFF0F2)

/** Cobo 首页现状：顶栏与页面同为 #F0F1F2，下方是微博白卡片流。 */
private val FeedBar = Color(0xFFF0F1F2)
private val FeedPage = Color(0xFFFFFFFF)

private val LabLabels = listOf("好友圈", "全部", "推荐")

@Composable
fun TabBarLabScreen() {
    val scheme = COUITheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF15161A))
            .testTag("tabBarLab"),
    ) {
        LabPanel(
            panel = "A",
            title = "系统「与电脑互联」",
            note = "顶栏 #FFFFFF · 两态同色",
            barColor = LinkerBar,
            pageColor = LinkerPage,
            content = FeedContent.LinkerList,
            contentColor = scheme.onSurface,
            selectedContentColor = scheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Divider()
        LabPanel(
            panel = "B",
            title = "Cobo 现状",
            note = "顶栏 #F0F1F2 · 未选中 onSurfaceSecondary",
            barColor = FeedBar,
            pageColor = FeedPage,
            content = FeedContent.WeiboCards,
            contentColor = scheme.onSurfaceSecondary,
            selectedContentColor = scheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Divider()
        LabPanel(
            panel = "C",
            title = "修正：白顶栏 + 两态同色",
            note = "顶栏 #FFFFFF · 两态 onSurface",
            barColor = LinkerBar,
            pageColor = FeedPage,
            content = FeedContent.WeiboCards,
            contentColor = scheme.onSurface,
            selectedContentColor = scheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

private enum class FeedContent { LinkerList, WeiboCards }

/** 两段之间的一道黑条，只用来把上下两段切开。 */
@Composable
private fun Divider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .background(Color(0xFF15161A)),
    )
}

@Composable
private fun LabPanel(
    panel: String,
    title: String,
    note: String,
    barColor: Color,
    pageColor: Color,
    content: FeedContent,
    contentColor: Color,
    selectedContentColor: Color,
    modifier: Modifier = Modifier,
) {
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val blur = isBlurEnabled()
    val backdrop = rememberLayerBackdrop()
    var selected by remember { mutableIntStateOf(1) }
    val scheme = COUITheme.colorScheme

    val density = LocalDensity.current
    val segmentWidthPx = with(density) { ((4 * 13).sp.toDp() + 2.dp + 10.dp + 22.dp).roundToPx() }
    val contourPadPx = with(density) { TabRowDefaults.TabRowWithContourPadding.roundToPx() }
    val segmentWidth = with(density) { segmentWidthPx.toDp() }
    val containerWidth = with(density) { (segmentWidthPx * LabLabels.size + contourPadPx * 2).toDp() }
    val tabTop = statusBar + 44.dp
    val headerHeight = tabTop + TopAppBarDefaults.SmallTopAppBarCenterHeight

    Box(modifier.clipToBounds()) {
        // 底层：顶栏底色 + 下方内容。标签栏的材质就采样这一层。
        Column(
            Modifier
                .fillMaxSize()
                .layerBackdropIf(blur, backdrop)
                .background(pageColor),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(headerHeight)
                    .background(barColor),
            ) {
                Column(Modifier.padding(top = statusBar, start = 16.dp, end = 16.dp)) {
                    Row(
                        Modifier.height(44.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "$panel · $title",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.onSurface,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            note,
                            fontSize = 10.sp,
                            color = scheme.onSurfaceSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            when (content) {
                FeedContent.LinkerList -> LinkerList(pageColor)
                FeedContent.WeiboCards -> WeiboCards()
            }
        }

        // 上层：分段标签栏，位置与首页顶栏一致（标题行下方 52dp 居中）。
        CompositionLocalProvider(LocalBackdrop provides if (blur) backdrop else null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = tabTop)
                    .height(TopAppBarDefaults.SmallTopAppBarCenterHeight),
                contentAlignment = Alignment.Center,
            ) {
                TabRowWithContour(
                    tabs = LabLabels,
                    selectedTabIndex = selected,
                    onTabSelected = { selected = it },
                    modifier = Modifier.width(containerWidth).testTag("labPanel$panel"),
                    colors = TabRowDefaults.tabRowColors(
                        contentColor = contentColor,
                        selectedContentColor = selectedContentColor,
                    ),
                    minWidth = segmentWidth,
                    maxWidth = segmentWidth,
                    segmentPadding = 8.dp,
                    backdrop = backdrop,
                    commitOnRelease = true,
                    dragSelectionEnabled = true,
                    touchMotionEnabled = true,
                    tabModifier = { index -> Modifier.testTag("labTab$panel$index") },
                    tabContent = { _, label, active ->
                        Text(
                            label,
                            fontSize = 13.sp,
                            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                            color = if (active) selectedContentColor else contentColor,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }
    }
}

/** 「与电脑互联」下方：灰底 + 白圆角列表块。 */
@Composable
private fun LinkerList(pageColor: Color) {
    val row = Color(0xFFFFFFFF)
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        repeat(3) { index ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(row)
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF3A6DF0).copy(alpha = 0.22f)),
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Box(
                        Modifier
                            .width((120 + index * 24).dp)
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0xFF1A1A1A).copy(alpha = 0.82f)),
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .width((72 + index * 18).dp)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(pageColor.darken(0.28f)),
                    )
                }
            }
        }
    }
}

/** 微博首页下方：白底 + 帖子块。 */
@Composable
private fun WeiboCards() {
    Column(Modifier.fillMaxWidth()) {
        repeat(2) { index ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFB9C7E6)),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Box(
                            Modifier
                                .width((86 + index * 20).dp)
                                .height(9.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFED7A2B).copy(alpha = 0.75f)),
                        )
                        Spacer(Modifier.height(7.dp))
                        Box(
                            Modifier
                                .width((58 + index * 12).dp)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF9AA0A6).copy(alpha = 0.7f)),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xFF2B2B2B).copy(alpha = 0.86f)),
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth(0.72f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xFF2B2B2B).copy(alpha = 0.86f)),
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth()) {
                    repeat(3) {
                        Box(
                            Modifier
                                .weight(1f)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF9AA0A6).copy(alpha = 0.55f)),
                        )
                        if (it < 2) Spacer(Modifier.width(18.dp))
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(Color(0xFFF2F3F5)),
            )
        }
    }
}

/** 压暗一点当占位条用，不引第三方。 */
private fun Color.darken(amount: Float): Color = Color(
    red = red * (1f - amount),
    green = green * (1f - amount),
    blue = blue * (1f - amount),
    alpha = alpha,
)
