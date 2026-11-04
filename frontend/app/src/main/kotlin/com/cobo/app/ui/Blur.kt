package com.cobo.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cobo.app.App
import io.github.suqi8.coui.kmp.blur.LayerBackdrop
import io.github.suqi8.coui.kmp.blur.layerBackdrop

/** 设置里的「模糊效果」。关掉后顶栏与玻璃面改画纯色，滑动时不再重采样背后的内容。 */
@Composable
fun blurEffectsEnabled(): Boolean = App.settings.blurEffects

/** 模糊关掉时连 backdrop 的录制一起省掉：没人采样它，这个图层每帧重录就是纯浪费。 */
fun Modifier.layerBackdropIf(enabled: Boolean, backdrop: LayerBackdrop): Modifier =
    if (enabled) layerBackdrop(backdrop) else this
