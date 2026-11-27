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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
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

/**
 * 为第一行搜索栏胶囊（`TopAppBarSearchBar`）构造对齐 ColorOS 17 便签质感的采样层：
 * 直接采样底层页面并叠一层克制的浅/深色雾面（浅色最多 42% 页面底色、深色最多 20% 黑色），
 * 避免直接采样原图时彩色照片过透染脏，也避免采样顶栏 90% 实白遮罩导致搜索栏变纯白失透。
 */
private class VeiledBackdrop(
    private val upstream: Backdrop,
    private val baseColor: Color,
    private val veilColor: Color,
    private val veilAlpha: () -> Float,
) : Backdrop {
    override val isCoordinatesDependent: Boolean get() = upstream.isCoordinatesDependent
    override val offsetResidualX: Float get() = upstream.offsetResidualX
    override val offsetResidualY: Float get() = upstream.offsetResidualY

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
        downscaleFactor: Int,
    ) {
        val padX = size.width
        val padY = size.height
        val fullTopLeft = Offset(-padX, -padY)
        val fullSize = Size(size.width + padX * 2f, size.height + padY * 2f)
        drawRect(baseColor, topLeft = fullTopLeft, size = fullSize)
        with(upstream) {
            drawBackdrop(density, coordinates, layerBlock, downscaleFactor)
        }
        val a = veilAlpha().coerceIn(0f, 1f)
        if (a > 0f) {
            drawRect(veilColor, alpha = a, topLeft = fullTopLeft, size = fullSize)
        }
    }
}

internal const val SearchBarLightVeilMax = 0.52f
internal const val SearchBarDarkVeilMax = 0.22f

@Composable
fun rememberSearchBarBackdrop(
    upstream: Backdrop? = LocalBackdrop.current,
    containerColor: Color = pageContainerColor(),
    scrollFraction: () -> Float = { 1f },
): Backdrop? {
    if (!blurEffectsEnabled() || upstream == null) return null
    val dark = isDarkTheme()
    val veilColor = if (dark) Color.Black else containerColor
    val maxVeil = if (dark) SearchBarDarkVeilMax else SearchBarLightVeilMax
    val currentScroll by rememberUpdatedState(scrollFraction)
    return remember(upstream, containerColor, veilColor, maxVeil) {
        VeiledBackdrop(
            upstream = upstream,
            baseColor = containerColor,
            veilColor = veilColor,
            veilAlpha = { maxVeil * currentScroll().coerceIn(0f, 1f) },
        )
    }
}

/**
 * 对齐 ColorOS 17 便签的双行毛玻璃顶栏（搜索/标题行 + 筛选胶囊行）：
 * 1. 先把底层页面 + `AppBarBlurOverlay` 渐进模糊与提白层录进 `materialBackdrop`（不再在未滚满时涂纯白），
 *    供第二行 `ChipTabGroup` 采样：未选中胶囊带真实 40px 磨砂模糊且滚过黑图不发黑，选中胶囊保留半透奶白/深灰玻璃感；
 * 2. 第一行搜索栏通过 `rememberSearchBarBackdrop` 采样克制雾化的底层页面，既保留滚动内容的柔和透出，又不被高饱和照片染脏或洗成纯白。
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
        }
    } else {
        rememberLayerBackdrop {
            drawRect(containerColor)
            drawContent()
        }
    }

    val firstRowBackdrop = rememberSearchBarBackdrop(
        upstream = backdrop,
        containerColor = containerColor,
        scrollFraction = scrollFraction,
    )
    val dark = isDarkTheme()
    val chipVeilColor = if (dark) Color.Black else containerColor
    val chipVeilMax = if (dark) 0.12f else 0.35f
    val currentScrollForChips by rememberUpdatedState(scrollFraction)
    val chipBackdrop: Backdrop? = if (blur && backdrop != null) {
        remember(materialBackdrop, containerColor, chipVeilColor, chipVeilMax) {
            VeiledBackdrop(
                upstream = materialBackdrop,
                baseColor = containerColor,
                veilColor = chipVeilColor,
                veilAlpha = { chipVeilMax * currentScrollForChips().coerceIn(0f, 1f) },
            )
        }
    } else {
        null
    }

    CompositionLocalProvider(LocalEdgeToEdge provides true) {
        Layout(
            modifier = modifier,
            content = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(barHeight + extraHeight)
                        .onGloballyPositioned { materialCoords = it }
                        .layerBackdropIf(blur && backdrop != null, materialBackdrop),
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                        .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
                        .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top)),
                ) {
                    CompositionLocalProvider(LocalBackdrop provides firstRowBackdrop) {
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
                                backdrop = chipBackdrop,
                            )
                        }
                    }
                }
            },
        ) { measurables, constraints ->
            val material = measurables[0].measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
            val rows = measurables[1].measure(constraints.copy(minHeight = 0))
            layout(rows.width, rows.height) {
                material.placeRelative(0, 0)
                rows.placeRelative(0, 0)
            }
        }
    }
}
