package com.cobo.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictiveBackBlurTest {

    @Test
    fun topLayerAndIdleStateRemainCompletelySharp() {
        // 非背景层（例如最上层卡片自身）永远保持 0 模糊。
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
                maxBlurRadiusPx = 60f,
                blurOffsetSpanPx = 300f,
            ),
            0.001f,
        )

        // 未进入预览或退场动画时保持清晰，不增加 GPU RenderEffect 开销。
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
                maxBlurRadiusPx = 60f,
                blurOffsetSpanPx = 300f,
            ),
            0.001f,
        )

        // 关闭开关或平台不支持时恒为 0。
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
                maxBlurRadiusPx = 60f,
                blurOffsetSpanPx = 300f,
            ),
            0.001f,
        )
    }

    @Test
    fun expandBackGestureGraduallyClearsFromFullBlurToSharp() {
        val maxBlur = 84f
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
    fun slideAndFollowFlingAlsoDriveBackgroundBlurSmoothly() {
        val maxBlur = 72f
        val slideMid = computePredictiveBackBlurRadiusPx(
            belowTop = true,
            blurEnabled = true,
            topPreviewing = true,
            topExiting = false,
            topExpand = false,
            topFollowFlingActive = false,
            topProgress = 0.5f,
            topOffsetDistancePx = 0f,
            maxBlurRadiusPx = maxBlur,
            blurOffsetSpanPx = 400f,
        )
        assertEquals(maxBlur * 0.5f, slideMid, 0.01f)

        // 当跟手偏移距离拉得更远时，底层保持更充分的背景虚化。
        val followOffset = computePredictiveBackBlurRadiusPx(
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
        assertTrue(followOffset > maxBlur * 0.45f)

        // 低于最小可见阈值直接清零，避免残留无效模糊效果。
        val belowThreshold = computePredictiveBackBlurRadiusPx(
            belowTop = true,
            blurEnabled = true,
            topPreviewing = true,
            topExiting = false,
            topExpand = false,
            topFollowFlingActive = false,
            topProgress = 0.01f,
            topOffsetDistancePx = 0f,
            maxBlurRadiusPx = 40f,
            blurOffsetSpanPx = 400f,
        )
        assertEquals(0f, belowThreshold, 0.001f)
    }
}
