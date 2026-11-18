package com.cobo.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.cobo.app.App
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.ColorSchemeMode
import io.github.suqi8.coui.kmp.theme.Colors
import io.github.suqi8.coui.kmp.theme.ThemeController
import io.github.suqi8.coui.kmp.theme.darkColorScheme
import io.github.suqi8.coui.kmp.theme.lightColorScheme
import kotlinx.coroutines.delay
import kotlin.math.cos

internal fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return ctx as? Activity
}

/**
 * The light page surface. COUI's light scheme pairs a white card with this grey page; leaving the
 * page white made every card invisible and the app look like one flat sheet.
 */
private val LightPage = Color(0xFFF0F1F2)

val DefaultAccentSwatchColor = Color(0xFF0080FF)

/** 非 0xFF 透明度的哨兵值：表示启用「Jeb_」色相缓慢循环强调色，与 0xFFRRGGBB 静态色互不冲突。 */
const val AccentHueCycle = 1

private const val HueCyclePeriodMs = 36_000L
private const val HueCycleTickMs = 120L

data class AccentPreset(val label: String, val argb: Int)

val AccentPresets = listOf(
    AccentPreset("默认", 0),
    AccentPreset("微博橙", 0xFFFF8200.toInt()),
    AccentPreset("珊瑚红", 0xFFE53935.toInt()),
    AccentPreset("樱花粉", 0xFFE91E63.toInt()),
    AccentPreset("紫罗兰", 0xFF8E24AA.toInt()),
    AccentPreset("靛青蓝", 0xFF3F51B5.toInt()),
    AccentPreset("天空青", 0xFF00ACC1.toInt()),
    AccentPreset("翡翠绿", 0xFF009688.toInt()),
    AccentPreset("生机绿", 0xFF34C759.toInt()),
    AccentPreset("Jeb_", AccentHueCycle),
)

fun hueCycleArgb(hue: Float, dark: Boolean): Int {
    val wrapped = ((hue % 360f) + 360f) % 360f
    val saturation = if (dark) 0.78f else 0.82f
    val value = if (dark) {
        0.94f
    } else {
        // 浅色模式下黄绿到青段天然亮度偏高，平滑压低明度保证白底文字与链接可读。
        val delta = (wrapped - 110f).coerceIn(-80f, 80f)
        val penalty = ((cos(Math.toRadians((delta / 80f * 180f).toDouble())) + 1.0) * 0.5).toFloat()
        0.88f - 0.18f * penalty
    }
    return AndroidColor.HSVToColor(floatArrayOf(wrapped, saturation, value))
}

fun formatHexColor(argb: Int): String =
    String.format(java.util.Locale.US, "#%06X", argb and 0xFFFFFF)

fun parseHexColor(input: String): Int? {
    val clean = input.trim().removePrefix("#")
    if (clean.length != 6 || clean.any { it.digitToIntOrNull(16) == null }) return null
    val rgb = clean.toIntOrNull(16) ?: return null
    return rgb or 0xFF000000.toInt()
}

fun accentColorSummary(argb: Int): String {
    if (argb == 0) return "默认"
    if (argb == AccentHueCycle) return "Jeb_ · 色相缓慢循环"
    val normalized = argb or 0xFF000000.toInt()
    val preset = AccentPresets.firstOrNull { it.argb != 0 && it.argb != AccentHueCycle && it.argb == normalized }
    val hex = formatHexColor(normalized)
    return if (preset != null) "${preset.label} ($hex)" else "自定义 ($hex)"
}

/**
 * 在保留 COUI 中性页面与卡片底色的前提下，仅把强调色家族替换成用户选定的颜色。
 *
 * 直接走 Monet 种子色会把中性灰一起染偏，且高饱和颜色会被 MD3 压暗；这里只替换
 * primary / containerTheme / focus 等强调色槽位，保证手选色与页面中性灰都准确。
 *
 * [keepFocusOutlineStatic] 在 Jeb_ 动态循环模式下置 true，保持 `LocalIndication` 引用稳定，
 * 避免每个色相帧触发 `staticCompositionLocalOf` 引起整棵页面树重组。
 */
internal fun Colors.withAccent(
    accentArgb: Int,
    dark: Boolean,
    keepFocusOutlineStatic: Boolean = false,
): Colors {
    if (accentArgb == 0) return this
    val raw = Color(accentArgb or 0xFF000000.toInt())
    val primaryColor = if (dark && raw.luminance() < 0.10f) lerp(raw, Color.White, 0.25f) else raw
    val onPrimaryColor = if (primaryColor.luminance() > 0.55f) Color(0xE6000000) else Color.White
    val container = if (dark) lerp(primaryColor, Color.White, 0.22f) else primaryColor
    return copy(
        primary = primaryColor,
        onPrimary = onPrimaryColor,
        primaryVariant = primaryColor,
        onPrimaryVariant = lerp(primaryColor, Color.White, if (dark) 0.55f else 0.68f),
        disabledPrimary = primaryColor.copy(alpha = 0.30f),
        disabledOnPrimary = if (dark) Color(0xFF404040) else onPrimaryColor,
        disabledPrimaryButton = primaryColor.copy(alpha = 0.30f),
        disabledOnPrimaryButton = onPrimaryColor.copy(alpha = if (dark) 0.16f else 0.54f),
        disabledPrimarySlider = primaryColor.copy(alpha = 0.30f),
        primaryContainer = container,
        onPrimaryContainer = onPrimaryColor,
        tertiaryContainer = primaryColor.copy(alpha = if (dark) 0.25f else 0.15f),
        onTertiaryContainer = primaryColor,
        tertiaryContainerVariant = primaryColor.copy(alpha = if (dark) 0.25f else 0.15f),
        sliderKeyPointForeground = lerp(primaryColor, Color.White, if (dark) 0.30f else 0.43f),
        containerTheme = primaryColor,
        focus = primaryColor.copy(alpha = if (dark) 0x33 / 255f else 0x1F / 255f),
        focusOutline = if (keepFocusOutlineStatic) focusOutline else container.copy(alpha = 0x66 / 255f),
    )
}

@Composable
fun isDarkTheme(): Boolean = when (App.settings.darkMode) {
    1 -> false
    2 -> true
    else -> isSystemInDarkTheme()
}

@Composable
fun CoboTheme(content: @Composable () -> Unit) {
    val mode = App.settings.darkMode
    val accentSetting = App.settings.accentColor
    val isHueCycle = accentSetting == AccentHueCycle
    var cycleHue by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isHueCycle) {
        if (!isHueCycle) return@LaunchedEffect
        val startMs = System.currentTimeMillis()
        while (true) {
            val elapsed = (System.currentTimeMillis() - startMs) % HueCyclePeriodMs
            cycleHue = elapsed.toFloat() / HueCyclePeriodMs * 360f
            delay(HueCycleTickMs)
        }
    }
    val lightAccent = if (isHueCycle) hueCycleArgb(cycleHue, dark = false) else accentSetting
    val darkAccent = if (isHueCycle) hueCycleArgb(cycleHue, dark = true) else accentSetting
    val controller = remember(mode, lightAccent, darkAccent, isHueCycle) {
        ThemeController(
            colorSchemeMode = when (mode) {
                1 -> ColorSchemeMode.Light
                2 -> ColorSchemeMode.Dark
                else -> ColorSchemeMode.System
            },
            lightColors = lightColorScheme(background = LightPage)
                .withAccent(lightAccent, dark = false, keepFocusOutlineStatic = isHueCycle),
            darkColors = darkColorScheme()
                .withAccent(darkAccent, dark = true, keepFocusOutlineStatic = isHueCycle),
        )
    }
    val base = LocalDensity.current
    val scale = App.settings.fontScale
    val density = remember(base, scale) { Density(base.density, base.fontScale * scale) }

    COUITheme(controller = controller) {
        CompositionLocalProvider(LocalDensity provides density) {
            content()
        }
    }
}
