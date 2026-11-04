package com.cobo.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import com.cobo.app.App
import io.github.suqi8.coui.kmp.basic.AppBarBlurOverlay
import io.github.suqi8.coui.kmp.blur.LayerBackdrop
import io.github.suqi8.coui.kmp.blur.layerBackdrop

/** 设置里的「模糊效果」。关掉后顶栏与玻璃面改画纯色，滑动时不再重采样背后的内容。 */
@Composable
fun blurEffectsEnabled(): Boolean = App.settings.blurEffects

/** 模糊关掉时连 backdrop 的录制一起省掉：没人采样它，这个图层每帧重录就是纯浪费。 */
fun Modifier.layerBackdropIf(enabled: Boolean, backdrop: LayerBackdrop): Modifier =
    if (enabled) layerBackdrop(backdrop) else this

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
