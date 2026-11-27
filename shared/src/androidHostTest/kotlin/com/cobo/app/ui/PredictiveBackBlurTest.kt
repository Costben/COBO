package com.cobo.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictiveBackBlurTest {

    @Test
    fun topLayerAndIdleStateRemainCompletelySharp() {
        assertEquals(
            0f,
            computePredictiveBackBlurRadiusPx(
                belowTop = false,
                blurEnabled = true,
                topPreviewing = true,
                topExiting = true,
                topExpand = true,
                topFollowFlingActive = true,
                topProgress = 0.85f,
                topOffsetDistancePx = 120f,
                maxBlurRadiusPx = 54f,
                blurOffsetSpanPx = 300f,
            ),
            0.001f,
        )

        assertEquals(
            0f,
            computePredictiveBackBlurRadiusPx(
                belowTop = true,
                blurEnabled = true,
                topPreviewing = false,
                topExiting = false,
                topExpand = true,
                topFollowFlingActive = false,
                topProgress = 1f,
                topOffsetDistancePx = 0f,
                maxBlurRadiusPx = 54f,
                blurOffsetSpanPx = 300f,
            ),
            0.001f,
        )

        assertEquals(
            0f,
            computePredictiveBackBlurRadiusPx(
                belowTop = true,
                blurEnabled = false,
                topPreviewing = true,
                topExiting = false,
                topExpand = false,
                topFollowFlingActive = false,
                topProgress = 0.9f,
                topOffsetDistancePx = 0f,
                maxBlurRadiusPx = 54f,
                blurOffsetSpanPx = 300f,
            ),
            0.001f,
        )
    }

    @Test
    fun expandBackGestureGraduallyClearsFromFullBlurToSharp() {
        val maxBlur = 54f
        val start = computePredictiveBackBlurRadiusPx(
            belowTop = true,
            blurEnabled = true,
            topPreviewing = true,
            topExiting = false,
            topExpand = true,
            topFollowFlingActive = false,
            topProgress = 1f,
            topOffsetDistancePx = 0f,
            maxBlurRadiusPx = maxBlur,
            blurOffsetSpanPx = 480f,
        )
        val mid = computePredictiveBackBlurRadiusPx(
            belowTop = true,
            blurEnabled = true,
            topPreviewing = true,
            topExiting = false,
            topExpand = true,
            topFollowFlingActive = false,
            topProgress = 0.65f,
            topOffsetDistancePx = 0f,
            maxBlurRadiusPx = maxBlur,
            blurOffsetSpanPx = 480f,
        )
        val nearLand = computePredictiveBackBlurRadiusPx(
            belowTop = true,
            blurEnabled = true,
            topPreviewing = true,
            topExiting = false,
            topExpand = true,
            topFollowFlingActive = false,
            topProgress = 0.28f,
            topOffsetDistancePx = 0f,
            maxBlurRadiusPx = maxBlur,
            blurOffsetSpanPx = 480f,
        )
        val landed = computePredictiveBackBlurRadiusPx(
            belowTop = true,
            blurEnabled = true,
            topPreviewing = false,
            topExiting = true,
            topExpand = true,
            topFollowFlingActive = false,
            topProgress = LandedProgress,
            topOffsetDistancePx = 0f,
            maxBlurRadiusPx = maxBlur,
            blurOffsetSpanPx = 480f,
        )

        assertEquals(maxBlur, start, 0.01f)
        assertTrue(start > mid && mid > nearLand && nearLand > landed)
        assertEquals(0f, landed, 0.001f)
    }

    @Test
    fun exitingPhaseIgnoresOffsetReboundAndEnforcesMonotonicClear() {
        val maxBlur = 54f
        // 退出期即使有偏移距离，也不允许拉高模糊目标半径
        val exitAtLand = computePredictiveBackBlurRadiusPx(
            belowTop = true,
            blurEnabled = true,
            topPreviewing = false,
            topExiting = true,
            topExpand = true,
            topFollowFlingActive = true,
            topProgress = LandedProgress,
            topOffsetDistancePx = 300f,
            maxBlurRadiusPx = maxBlur,
            blurOffsetSpanPx = 400f,
        )
        assertEquals(0f, exitAtLand, 0.001f)

        val filter = PredictiveBackBlurFilter()
        val r0 = filter.update(gestureId = 1L, generation = 1, active = true, exiting = false, targetRadiusPx = 40f)
        // 拖拽死区内微幅回弹震荡（<= 1.2px）不应引起模糊半径抖动
        val rJitter1 = filter.update(gestureId = 1L, generation = 1, active = true, exiting = false, targetRadiusPx = 40.9f)
        val rJitter2 = filter.update(gestureId = 1L, generation = 1, active = true, exiting = false, targetRadiusPx = 39.2f)
        assertEquals(r0, rJitter1, 0.001f)
        assertEquals(r0, rJitter2, 0.001f)

        // 松手进入 exiting：先降到 28，随后即便上游给出 34 的回弹噪声，滤波器也不允许反向增大
        val e1 = filter.update(gestureId = 1L, generation = 1, active = true, exiting = true, targetRadiusPx = 28f)
        val eRebound = filter.update(gestureId = 1L, generation = 1, active = true, exiting = true, targetRadiusPx = 34f)
        val e2 = filter.update(gestureId = 1L, generation = 1, active = true, exiting = true, targetRadiusPx = 10f)
        val eEnd = filter.update(gestureId = 1L, generation = 1, active = false, exiting = false, targetRadiusPx = 0f)

        assertTrue(e1 <= r0)
        assertTrue(eRebound <= e1)
        assertTrue(e2 <= eRebound)
        assertEquals(0f, eEnd, 0.001f)
    }
}
