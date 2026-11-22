package com.cobo.app.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import io.github.suqi8.coui.kmp.basic.AppBarBlurOverlayDefaults
import io.github.suqi8.coui.kmp.utils.rememberGalleryDensity

private const val PhoneMaxShortEdgeDp = 600f
/** 与 COUI `GalleryDensityDefaults.ShortEdgeDp` 对齐，不直接引用其 JVM 类以便在 JDK 17 单测下运行。 */
private const val TargetBarShortEdgeDp = 360f
private const val BaselineDpi = 160f

/**
 * 顶栏、底栏按 ColorOS 自家应用那样钉在 360dp 短边，不受系统「显示大小」影响；
 * 页面内容仍然跟着系统走。只把这一层套在栏上，别套到页面上。
 */
@Composable
fun PinnedBarDensity(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDensity provides rememberBarDensity(), content = content)
}

/**
 * 计算顶栏和底栏使用的密度。
 *
 * 在非 ColorOS 或没有 `oplus.software.display.resolution_switch_support` 的高分屏上，
 * [rememberGalleryDensity] 会回退到 `ro.sf.lcd_density`（部分 2K 机为 440dpi = 2.75x），
 * 导致短边变成 500+ dp、顶栏与底栏按钮偏小；在虚拟屏上也会误读主屏参数。
 * 对手机尺寸的显示器，统一以当前显示器的实际短边按 360dp 换算作为下限兜底。
 */
@Composable
fun rememberBarDensity(): Density {
    val gallery = rememberGalleryDensity()
    val platform = LocalDensity.current
    val context = LocalContext.current
    val config = LocalConfiguration.current
    return remember(gallery, platform, context, config) {
        val info = readCurrentDisplayInfo(context, config)
        computeBarDensity(
            galleryDensity = gallery,
            platformDensity = platform,
            shortEdgePx = info.shortEdgePx,
            displayDensity = info.density,
            smallestWidthDp = info.smallestWidthDp,
            isDefaultDisplay = info.isDefaultDisplay,
        )
    }
}

private data class CurrentDisplayInfo(
    val shortEdgePx: Int,
    val density: Float,
    val smallestWidthDp: Float,
    val isDefaultDisplay: Boolean,
)

@Suppress("DEPRECATION")
private fun readCurrentDisplayInfo(context: Context, config: Configuration): CurrentDisplayInfo {
    val display = if (Build.VERSION.SDK_INT >= 30) {
        runCatching { context.display }.getOrNull()
    } else {
        null
    } ?: (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay

    val metrics = DisplayMetrics()
    if (display != null) {
        display.getRealMetrics(metrics)
    } else {
        metrics.setTo(context.resources.displayMetrics)
    }
    val shortEdgePx = minOf(metrics.widthPixels, metrics.heightPixels)
    val displayDensity = metrics.density
    val smallestWidthDp = if (shortEdgePx > 0 && displayDensity > 0f) {
        shortEdgePx / displayDensity
    } else {
        config.smallestScreenWidthDp.toFloat()
    }
    val isDefaultDisplay = display == null || display.displayId == Display.DEFAULT_DISPLAY
    return CurrentDisplayInfo(
        shortEdgePx = shortEdgePx,
        density = displayDensity,
        smallestWidthDp = smallestWidthDp,
        isDefaultDisplay = isDefaultDisplay,
    )
}

internal fun computeBarDensity(
    galleryDensity: Density,
    platformDensity: Density,
    shortEdgePx: Int,
    displayDensity: Float,
    smallestWidthDp: Float,
    isDefaultDisplay: Boolean,
): Density {
    if (shortEdgePx <= 0 || smallestWidthDp >= PhoneMaxShortEdgeDp) {
        val target = if (isDefaultDisplay) {
            galleryDensity.density
        } else {
            platformDensity.density
        }
        return Density(density = target, fontScale = platformDensity.fontScale)
    }
    val shortEdge360Dpi = (shortEdgePx / TargetBarShortEdgeDp * BaselineDpi).toInt()
    val shortEdge360Density = shortEdge360Dpi / BaselineDpi
    val targetDensity = if (isDefaultDisplay) {
        maxOf(galleryDensity.density, shortEdge360Density)
    } else {
        // 虚拟屏等非主屏直接按该屏自身短边换算，避免被主屏物理密度拉偏。
        shortEdge360Density
    }
    return Density(density = targetDensity, fontScale = platformDensity.fontScale)
}

/**
 * 状态栏高度加上若干「按钉住密度量出来的行高」，换算成当前密度下的 dp。
 * 栏和页面用两套密度，内容留白必须按像素换算，否则会和栏错位。
 */
@Composable
fun pinnedHeight(row: Dp): Dp = pinnedHeight(listOf(row))

/** 两行的顶栏：标题行 + 分段行。 */
@Composable
fun pinnedHeight(row1: Dp, row2: Dp): Dp = pinnedHeight(listOf(row1, row2))

/**
 * 若干「按钉住密度量出来的行高」之和，不含状态栏：给栏下多出来的那几行算留白用，
 * 例如搜索结果顶栏的间距 + 胶囊行 + 栏底留白。
 */
@Composable
fun pinnedRowsHeight(rows: List<Dp>): Dp {
    val local = LocalDensity.current
    val rowsPx = with(rememberBarDensity()) { rows.fold(0f) { acc, row -> acc + row.toPx() } }
    return with(local) { rowsPx.toDp() }
}

@Composable
private fun pinnedHeight(rows: List<Dp>): Dp {
    val local = LocalDensity.current
    val statusPx = WindowInsets.statusBars.getTop(local)
    val rowsPx = with(rememberBarDensity()) { rows.fold(0f) { acc, row -> acc + row.toPx() } }
    return with(local) { (statusPx + rowsPx).toDp() }
}

/**
 * 玻璃顶栏下首屏内容的上沿：栏高加上材质的渐隐模糊带，再留出 [gap]。
 * 材质会往栏底以下多画 [AppBarBlurOverlayDefaults.ExtraHeight]，首行要让过这段才不会被糊住。
 */
fun glassBarContentTop(topBarHeight: Dp, gap: Dp): Dp =
    topBarHeight + AppBarBlurOverlayDefaults.ExtraHeight + gap
