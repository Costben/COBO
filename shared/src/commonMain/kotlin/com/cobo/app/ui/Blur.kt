package com.cobo.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cobo.app.App
import io.github.suqi8.coui.kmp.basic.AppBarBlurOverlay
import io.github.suqi8.coui.kmp.basic.AppBarBlurOverlayDefaults
import io.github.suqi8.coui.kmp.basic.ChipTabData
import io.github.suqi8.coui.kmp.basic.ChipTabGroup
import io.github.suqi8.coui.kmp.basic.LocalEdgeToEdge
import io.github.suqi8.coui.kmp.basic.TwoRowTopAppBarDefaults
import io.github.suqi8.coui.kmp.blur.Backdrop
import io.github.suqi8.coui.kmp.blur.LayerBackdrop
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.layerBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop

/** 设置里的「模糊效果」。关掉后顶栏与玻璃面改画纯色，滑动时不再重采样背后的内容。 */
@Composable
fun blurEffectsEnabled(): Boolean = App.settings.blurEffects

/** 模糊关掉时连 backdrop 的录制一起省掉：没人采样它，这个图层每帧重录就是纯浪费。 */
fun Modifier.layerBackdropIf(enabled: Boolean, backdrop: LayerBackdrop): Modifier =
    if (enabled) layerBackdrop(backdrop) else this

/**
 * 根据 [LazyListState] 的纵向滚动偏移换算顶栏材质透明度：
 * 位于列表顶部（或尚未越过 [startOffset] 留白）时为 `0f`（不绘制顶部阴影/遮罩），
 * 越过 [startOffset] 后在 [rampDistance] 内平滑渐显至 `1f`。
 */
fun listScrollAlpha(
    listState: LazyListState,
    density: Density,
    startOffset: Dp = 0.dp,
    rampDistance: Dp = AppBarBlurOverlayDefaults.ScrollAlphaThresholdDp.dp,
): Float {
    val scrolled = if (listState.firstVisibleItemIndex > 0) Float.MAX_VALUE
    else listState.firstVisibleItemScrollOffset.toFloat()
    return AppBarBlurOverlayDefaults.scrollAlpha(
        scrollPx = scrolled,
        density = density,
        startOffset = startOffset,
        rampDistance = rampDistance,
    )
}

/**
 * 双行顶栏（`TwoRowTopAppBar` / `SearchFilterTopAppBar` / 首页双行顶栏）的滚动淡入透明度：
 * 经 [TwoRowTopAppBarDefaults.scrollAlpha] 先跳过标签行下方留白，再在 `56dp` 内平滑淡入。
 */
fun twoRowListScrollAlpha(
    listState: LazyListState,
    density: Density,
    contentGap: Dp = AppBarBlurOverlayDefaults.ExtraHeight + 8.dp,
): Float {
    val scrolled = if (listState.firstVisibleItemIndex > 0) Float.MAX_VALUE
    else listState.firstVisibleItemScrollOffset.toFloat()
    return TwoRowTopAppBarDefaults.scrollAlpha(
        scrollPx = scrolled,
        density = density,
        contentGap = contentGap,
    )
}

/** [listScrollAlpha] 的 draw 阶段 producer 封装，供 [AppBarSurface] / `TwoRowTopAppBar` 等复用。 */
@Composable
fun rememberListScrollFraction(listState: LazyListState): () -> Float {
    val density = LocalDensity.current
    return remember(listState, density) {
        { listScrollAlpha(listState, density) }
    }
}

/**
 * 自定义顶栏的材质层：模糊开着走 [AppBarBlurOverlay]，关掉就画整块实色。
 *
 * [AppBarBlurOverlay] 在没有 backdrop 时会退回一条从 100% 淡到 0 的渐变，标题栏看上去仍是半透明；
 * 而 [io.github.suqi8.coui.kmp.basic.SmallTopAppBar]`(gradientGlass = false)` 那条路退回的是实心底。
 * 两条路在关掉模糊后观感对不上，所以这里统一成实色。
 *
 * [scrollFraction] 仍然在绘制阶段读，详情页那种「滚动后在 30dp 内淡入」的行为照旧。
 */
@Composable
fun AppBarSurface(
    modifier: Modifier = Modifier,
    containerColor: Color = pageContainerColor(),
    scrollFraction: () -> Float = { 1f },
    content: @Composable BoxScope.() -> Unit = {},
) {
    if (blurEffectsEnabled()) {
        AppBarBlurOverlay(
            modifier = modifier,
            containerColor = containerColor,
            scrollFraction = scrollFraction,
            platformRenderer = true,
            content = content,
        )
        return
    }
    Box(
        modifier.drawBehind {
            val alpha = scrollFraction().coerceIn(0f, 1f)
            if (alpha > 0f) drawRect(containerColor, alpha = alpha)
        },
        content = content,
    )
}

internal const val SearchBarLightVeilMax = 0.48f
internal const val SearchBarDarkVeilMax = 0.20f

/**
 * 为独立单行搜索顶栏（如设置页搜索）提供一层真正的 [LayerBackdrop] 采样源：
 * 只把底层页面 + 克制雾面录进离屏层（自身不向屏幕多画像素），保持 `LayerBackdrop`
 * 的按需消费计数链路完整，避免搜索栏既过透发脏又被实心遮罩洗成纯白。
 */
@Composable
fun SearchBarBackdropHost(
    modifier: Modifier = Modifier,
    containerColor: Color = pageContainerColor(),
    upstream: Backdrop? = LocalBackdrop.current,
    scrollFraction: () -> Float = { 1f },
    content: @Composable (searchBarBackdrop: Backdrop?) -> Unit,
) {
    val blur = blurEffectsEnabled()
    val dark = isDarkTheme()
    val upstreamLayer = upstream as? LayerBackdrop
    val veilColor = if (dark) Color.Black else containerColor
    val veilMax = if (dark) SearchBarDarkVeilMax else SearchBarLightVeilMax
    val currentScroll by rememberUpdatedState(scrollFraction)
    var recorderCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val searchBackdrop = if (upstreamLayer != null) {
        rememberLayerBackdrop(upstream = upstreamLayer) {
            drawRect(containerColor)
            val coords = recorderCoords
            if (coords != null && coords.isAttached) {
                with(upstreamLayer) {
                    drawBackdrop(
                        density = this@rememberLayerBackdrop,
                        coordinates = coords,
                        layerBlock = null,
                        downscaleFactor = 1,
                    )
                }
            }
            val a = veilMax * currentScroll().coerceIn(0f, 1f)
            if (a > 0f) drawRect(veilColor, alpha = a)
        }
    } else {
        null
    }

    Box(modifier) {
        if (blur && searchBackdrop != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .onGloballyPositioned { recorderCoords = it }
                    .layerBackdrop(searchBackdrop),
            )
        }
        content(if (blur) (searchBackdrop ?: upstream) else null)
    }
}

/**
 * 对齐 ColorOS 17 便签的双行毛玻璃顶栏（搜索/标题行 + 筛选胶囊行）：
 * 1. 所有采样源均使用真正的 [LayerBackdrop]（严禁用自定义包装类包在外部导致 `acquireConsumer` 失效、采样退化成纯色实心）；
 * 2. 第二行 `ChipTabGroup` 直接采样录有「底层实时页面 + 顶栏渐进模糊与轻雾提亮」的 `materialBackdrop`：
 *    未选中胶囊带真实 40px 磨砂模糊且滚过暗图不发黑，选中胶囊与四宫格圆钮保持半透奶白/深灰玻璃高光；
 * 3. 第一行搜索栏采样独立的 `searchRowBackdrop`（仅向离屏层录制底层页面 + 克制雾面，不向屏幕多画像素），
 *    避免直接采原图过透染脏，也避免采顶栏顶部 ~90% 遮罩而变纯白失透。
 */
@Composable
fun CoboSearchFilterTopAppBar(
    filters: List<ChipTabData>? = null,
    selectedFilter: Int = 0,
    onFilterSelected: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
    firstRowHeight: Dp = TwoRowTopAppBarDefaults.FirstRowHeight,
    filterRowHeight: Dp = TwoRowTopAppBarDefaults.SecondRowHeight,
    rowGap: Dp = TwoRowTopAppBarDefaults.SecondRowGap,
    leadingIcon: ImageVector? = null,
    onLeadingIconClick: () -> Unit = {},
    filterContentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    containerColor: Color = pageContainerColor(),
    backdrop: Backdrop? = LocalBackdrop.current,
    scrollFraction: () -> Float = { 1f },
    filtersAlpha: () -> Float = { 1f },
    firstRow: @Composable () -> Unit,
) {
    val blur = blurEffectsEnabled()
    val dark = isDarkTheme()
    val density = LocalDensity.current
    val statusBarTop = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val hasFilters = filters != null
    val extraHeight = if (hasFilters) {
        TwoRowTopAppBarDefaults.ExtraHeight
    } else {
        AppBarBlurOverlayDefaults.ExtraHeight
    }
    val rowsHeight = firstRowHeight + if (hasFilters) {
        rowGap + filterRowHeight + TwoRowTopAppBarDefaults.SecondRowBottom
    } else {
        0.dp
    }
    val barHeight = statusBarTop + rowsHeight

    val upstreamLayer = backdrop as? LayerBackdrop
    val currentScroll by rememberUpdatedState(scrollFraction)
    val veilColor = if (dark) Color.Black else containerColor
    val searchVeilMax = if (dark) SearchBarDarkVeilMax else SearchBarLightVeilMax
    val chipExtraVeilMax = if (dark) 0.08f else 0.22f

    var materialCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val materialBackdrop = if (upstreamLayer != null) {
        rememberLayerBackdrop(upstream = upstreamLayer) {
            drawRect(containerColor)
            val coords = materialCoords
            if (coords != null && coords.isAttached) {
                with(upstreamLayer) {
                    drawBackdrop(
                        density = this@rememberLayerBackdrop,
                        coordinates = coords,
                        layerBlock = null,
                        downscaleFactor = 1,
                    )
                }
            }
            drawContent()
            // 仅在录制层给第二行下缘补一层极轻雾面（不影响屏幕上的渐隐底边），防止黑图滚到胶囊底边时发暗。
            val extra = chipExtraVeilMax * currentScroll().coerceIn(0f, 1f)
            if (extra > 0f) drawRect(veilColor, alpha = extra)
        }
    } else {
        null
    }

    var searchCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val searchRowBackdrop = if (upstreamLayer != null) {
        rememberLayerBackdrop(upstream = upstreamLayer) {
            drawRect(containerColor)
            val coords = searchCoords
            if (coords != null && coords.isAttached) {
                with(upstreamLayer) {
                    drawBackdrop(
                        density = this@rememberLayerBackdrop,
                        coordinates = coords,
                        layerBlock = null,
                        downscaleFactor = 1,
                    )
                }
            }
            val a = searchVeilMax * currentScroll().coerceIn(0f, 1f)
            if (a > 0f) drawRect(veilColor, alpha = a)
        }
    } else {
        null
    }

    val effectiveFirstRowBackdrop: Backdrop? = if (blur) (searchRowBackdrop ?: backdrop) else null
    val effectiveChipBackdrop: Backdrop? = if (blur) (materialBackdrop ?: backdrop) else null

    CompositionLocalProvider(LocalEdgeToEdge provides true) {
        Layout(
            modifier = modifier,
            content = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(barHeight + extraHeight)
                        .onGloballyPositioned { materialCoords = it }
                        .then(
                            if (blur && materialBackdrop != null) {
                                Modifier.layerBackdrop(materialBackdrop)
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    if (blur) {
                        AppBarBlurOverlay(
                            scrollFraction = scrollFraction,
                            modifier = Modifier.fillMaxWidth().height(barHeight),
                            containerColor = containerColor,
                            backdrop = backdrop,
                            extraHeight = extraHeight,
                            platformRenderer = false,
                        ) {}
                    } else {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(barHeight)
                                .drawWithContent {
                                    val a = scrollFraction().coerceIn(0f, 1f)
                                    if (a > 0f) drawRect(containerColor, alpha = a)
                                },
                        )
                    }
                }
                // 仅录离屏搜索栏背景层（空节点不向屏幕输出像素，避免叠加或遮挡 AppBarBlurOverlay）。
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(barHeight + extraHeight)
                        .onGloballyPositioned { searchCoords = it }
                        .then(
                            if (blur && searchRowBackdrop != null) {
                                Modifier.layerBackdrop(searchRowBackdrop)
                            } else {
                                Modifier
                            },
                        ),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                        .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
                        .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top)),
                ) {
                    CompositionLocalProvider(LocalBackdrop provides effectiveFirstRowBackdrop) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(firstRowHeight),
                            contentAlignment = Alignment.Center,
                        ) {
                            firstRow()
                        }
                    }
                    if (filters != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    top = rowGap,
                                    bottom = TwoRowTopAppBarDefaults.SecondRowBottom,
                                )
                                .height(filterRowHeight)
                                .graphicsLayer {
                                    alpha = filtersAlpha().coerceIn(0f, 1f)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            ChipTabGroup(
                                tabs = filters,
                                selectedIndex = selectedFilter,
                                onSelectedChange = onFilterSelected,
                                modifier = Modifier.fillMaxWidth(),
                                leadingIcon = leadingIcon,
                                onLeadingIconClick = onLeadingIconClick,
                                contentPadding = filterContentPadding,
                                backdrop = effectiveChipBackdrop,
                            )
                        }
                    }
                }
            },
        ) { measurables, constraints ->
            val material = measurables[0].measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
            val searchLayer = measurables[1].measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
            val rows = measurables[2].measure(constraints.copy(minHeight = 0))
            layout(rows.width, rows.height) {
                material.placeRelative(0, 0)
                searchLayer.placeRelative(0, 0)
                rows.placeRelative(0, 0)
            }
        }
    }
}
