package com.cobo.app.ui

import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.utils.PredictiveBackMotionDefaults
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 预测性返回背景模糊最大半径：控制在细腻通透的 18dp 光学区间，
 * 避免过高半径触发 Skia/HWUI 多级高斯降采样切层导致缓慢松手时肉眼抖动。
 */
internal val PredictiveBackBlurMaxRadius = 18.dp

/** 拖拽预览阶段对微幅手指抖动（<= 1.2px）的静区阈值。 */
internal const val PredictiveBackBlurDeadzonePx = 1.2f

/** 拖拽期间模糊半径的一阶低通阻尼系数，滤除采样毛刺并保持线性跟手。 */
internal const val PredictiveBackBlurSmoothingAlpha = 0.35f

/**
 * 计算预测性返回或退场过渡期间底层页面（`belowTop`）应叠加的目标高斯模糊半径（px）。
 *
 * 保持最上层返回卡片/页面本身完全清晰，仅对紧邻下方的背景层施加由模糊渐清的景深虚化。
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
    // 采用柔和单调幂函数，避免 smoothstep 中段高斜率放大缓慢手势末尾的微幅波动。
    val easedProgress = base.pow(1.25f)

    // 仅在拖拽悬停预览阶段把手指偏移量融入虚化权重；松手进入 exiting 后不再由偏移回拉推高模糊。
    val offsetFraction = if (!topExiting && topPreviewing && topFollowFlingActive && blurOffsetSpanPx > 0f && topOffsetDistancePx > 0f) {
        (topOffsetDistancePx / blurOffsetSpanPx).coerceIn(0f, 1f) * 0.55f
    } else {
        0f
    }

    val radius = maxBlurRadiusPx * maxOf(easedProgress, offsetFraction)
    return if (radius <= PredictiveBackMotionDefaults.BACK_BLUR_THRESHOLD_PX) 0f else radius
}

/**
 * 预测性返回背景模糊逐帧滤波器：
 * - 拖拽预览阶段用微幅死区 + 一阶低通滤掉手指停顿/缓慢抬起时的锯齿回弹；
 * - 松手进入退出动画后锁定单调不增上界，确保模糊半径只减不增、平滑转清直至归零。
 */
internal class PredictiveBackBlurFilter {
    private var activeGestureId: Long = Long.MIN_VALUE
    private var activeGeneration: Int = Int.MIN_VALUE
    private var initialized: Boolean = false
    private var smoothedRadiusPx: Float = 0f
    private var exitCeilingPx: Float = Float.POSITIVE_INFINITY

    fun reset() {
        activeGestureId = Long.MIN_VALUE
        activeGeneration = Int.MIN_VALUE
        initialized = false
        smoothedRadiusPx = 0f
        exitCeilingPx = Float.POSITIVE_INFINITY
    }

    fun update(
        gestureId: Long,
        generation: Int,
        active: Boolean,
        exiting: Boolean,
        targetRadiusPx: Float,
    ): Float {
        if (!active) {
            reset()
            return 0f
        }
        val cleanTarget = targetRadiusPx.coerceAtLeast(0f)
        val sameGesture = initialized && activeGestureId == gestureId && activeGeneration == generation
        if (!sameGesture) {
            activeGestureId = gestureId
            activeGeneration = generation
            initialized = true
            smoothedRadiusPx = cleanTarget
            exitCeilingPx = if (exiting) cleanTarget else Float.POSITIVE_INFINITY
        } else if (exiting) {
            exitCeilingPx = minOf(exitCeilingPx, smoothedRadiusPx)
            val boundedTarget = minOf(cleanTarget, exitCeilingPx)
            val delta = boundedTarget - smoothedRadiusPx
            smoothedRadiusPx = if (abs(delta) <= 0.35f) {
                boundedTarget
            } else {
                (smoothedRadiusPx + delta * 0.45f).coerceIn(0f, exitCeilingPx)
            }
            exitCeilingPx = minOf(exitCeilingPx, smoothedRadiusPx)
        } else {
            val delta = cleanTarget - smoothedRadiusPx
            if (abs(delta) > PredictiveBackBlurDeadzonePx) {
                smoothedRadiusPx = (smoothedRadiusPx + delta * PredictiveBackBlurSmoothingAlpha).coerceAtLeast(0f)
            } else if (cleanTarget <= PredictiveBackMotionDefaults.BACK_BLUR_THRESHOLD_PX) {
                smoothedRadiusPx = 0f
            }
        }

        if (smoothedRadiusPx <= PredictiveBackMotionDefaults.BACK_BLUR_THRESHOLD_PX) return 0f
        return (smoothedRadiusPx * 2f).roundToInt() / 2f
    }
}
