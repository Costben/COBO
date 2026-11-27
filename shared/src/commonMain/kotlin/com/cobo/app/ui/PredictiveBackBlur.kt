package com.cobo.app.ui

import io.github.suqi8.coui.kmp.utils.PredictiveBackMotionDefaults

/**
 * 计算预测性返回或退场过渡期间底层页面（`belowTop`）应叠加的高斯模糊半径（px）。
 *
 * 保持最上层返回卡片/页面本身完全清晰，仅对紧邻下方的背景层在手势起手到落位之间
 * 施加类 iPhone duo 纵深虚化：起手阶段模糊，随着返回推进或归位平滑减弱至完全清晰。
 */
internal fun computePredictiveBackBlurRadiusPx(
    belowTop: Boolean,
    blurEnabled: Boolean,
    topPreviewing: Boolean,
    topExiting: Boolean,
    topExpand: Boolean,
    topFollowFlingActive: Boolean,
    topProgress: Float,
    topOffsetDistancePx: Float,
    maxBlurRadiusPx: Float,
    blurOffsetSpanPx: Float,
): Float {
    if (!belowTop || !blurEnabled || maxBlurRadiusPx <= 0f) return 0f
    if (!topPreviewing && !topExiting) return 0f

    val base = if (topExpand) {
        ((topProgress - LandedProgress) / (1f - LandedProgress)).coerceIn(0f, 1f)
    } else {
        topProgress.coerceIn(0f, 1f)
    }
    // 使用 smoothstep 曲线，起手保持饱满虚化并在临近落位时顺滑转清。
    val easedProgress = base * base * (3f - 2f * base)

    val offsetFraction = if (topFollowFlingActive && blurOffsetSpanPx > 0f && topOffsetDistancePx > 0f) {
        (topOffsetDistancePx / blurOffsetSpanPx).coerceIn(0f, 1f) * 0.72f
    } else {
        0f
    }

    val radius = maxBlurRadiusPx * maxOf(easedProgress, offsetFraction)
    return if (radius <= PredictiveBackMotionDefaults.BACK_BLUR_THRESHOLD_PX) 0f else radius
}
