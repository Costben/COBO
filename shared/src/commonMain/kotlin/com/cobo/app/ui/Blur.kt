package com.cobo.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cobo.app.App
import io.github.suqi8.coui.kmp.basic.AppBarBlurOverlay
import io.github.suqi8.coui.kmp.basic.AppBarBlurOverlayDefaults
import io.github.suqi8.coui.kmp.basic.TwoRowTopAppBarDefaults
import io.github.suqi8.coui.kmp.blur.LayerBackdrop
import io.github.suqi8.coui.kmp.blur.layerBackdrop

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
