package com.cobo.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingBarScrollControllerTest {

    private fun newTracker() = FloatingBarHideTracker(
        hideDeadzonePx = 10f,
        hideRangePx = 100f,
        showDeadzonePx = 20f,
        showRangePx = 100f,
        commitThreshold = 0.45f,
        flingVelocityThresholdPxPerSec = 900f,
    )

    @Test
    fun downScrollAbsorbsDeadzoneThenProgressesLinearlyToHidden() {
        val tracker = newTracker()

        assertEquals(0f, tracker.onScrollDelta(deltaY = -10f, isNearTop = false, enabled = true), 1e-4f)
        assertEquals(0.25f, tracker.onScrollDelta(deltaY = -25f, isNearTop = false, enabled = true), 1e-4f)
        assertEquals(1f, tracker.onScrollDelta(deltaY = -90f, isNearTop = false, enabled = true), 1e-4f)
    }

    @Test
    fun topSafeZoneResetsProgressAndBlocksHiding() {
        val tracker = newTracker()
        assertEquals(1f, tracker.onScrollDelta(deltaY = -150f, isNearTop = false, enabled = true), 1e-4f)

        assertEquals(0f, tracker.onScrollDelta(deltaY = -40f, isNearTop = true, enabled = true), 1e-4f)
        assertEquals(0f, tracker.progress, 1e-4f)

        // 离开保护区后仍需重新走完向下死区
        assertEquals(0f, tracker.onScrollDelta(deltaY = -10f, isNearTop = false, enabled = true), 1e-4f)
        assertEquals(0.2f, tracker.onScrollDelta(deltaY = -20f, isNearTop = false, enabled = true), 1e-4f)
    }

    @Test
    fun upScrollFiltersSmallJitterAndRevealsProgressively() {
        val tracker = newTracker()
        assertEquals(1f, tracker.onScrollDelta(deltaY = -150f, isNearTop = false, enabled = true), 1e-4f)

        // 微小上蹭落入 20px 死区，不立即弹出
        assertEquals(1f, tracker.onScrollDelta(deltaY = 15f, isNearTop = false, enabled = true), 1e-4f)
        assertEquals(0.75f, tracker.onScrollDelta(deltaY = 30f, isNearTop = false, enabled = true), 1e-4f)
        assertEquals(0f, tracker.onScrollDelta(deltaY = 80f, isNearTop = false, enabled = true), 1e-4f)
    }

    @Test
    fun directionReversalRestoresOppositeDeadzone() {
        val tracker = newTracker()
        // 先隐藏到 1f
        assertEquals(1f, tracker.onScrollDelta(deltaY = -120f, isNearTop = false, enabled = true), 1e-4f)
        // 消耗掉 15px 向上死区后反向向下抖动 5px，应当把向上死区重置回 20px
        assertEquals(1f, tracker.onScrollDelta(deltaY = 15f, isNearTop = false, enabled = true), 1e-4f)
        assertEquals(1f, tracker.onScrollDelta(deltaY = -5f, isNearTop = false, enabled = true), 1e-4f)
        // 重新上拉 18px 仍不越过完整 20px 死区
        assertEquals(1f, tracker.onScrollDelta(deltaY = 18f, isNearTop = false, enabled = true), 1e-4f)
        assertEquals(0.88f, tracker.onScrollDelta(deltaY = 14f, isNearTop = false, enabled = true), 1e-4f)
    }

    @Test
    fun resolveSettleTargetRespectsAnchorThresholdAndFlingVelocity() {
        val fromShown = newTracker()
        fromShown.onScrollDelta(deltaY = -50f, isNearTop = false, enabled = true) // progress = 0.40
        assertEquals(0f, fromShown.resolveSettleTarget(velocityY = 0f, isNearTop = false, enabled = true), 1e-4f)
        assertEquals(1f, fromShown.resolveSettleTarget(velocityY = -1000f, isNearTop = false, enabled = true), 1e-4f)

        fromShown.onScrollDelta(deltaY = -10f, isNearTop = false, enabled = true) // progress = 0.50 >= 0.45
        assertEquals(1f, fromShown.resolveSettleTarget(velocityY = 0f, isNearTop = false, enabled = true), 1e-4f)

        val fromHidden = newTracker()
        fromHidden.onScrollDelta(deltaY = -120f, isNearTop = false, enabled = true) // progress = 1.0, anchorHidden = true
        fromHidden.onScrollDelta(deltaY = 50f, isNearTop = false, enabled = true) // 吸收 20px 死区后拉出 0.30 -> 0.70
        // 仅拉出 30%（progress 0.70 > 0.55），松手缩回隐藏
        assertEquals(1f, fromHidden.resolveSettleTarget(velocityY = 0f, isNearTop = false, enabled = true), 1e-4f)
        // 快速上甩直接展开
        assertEquals(0f, fromHidden.resolveSettleTarget(velocityY = 950f, isNearTop = false, enabled = true), 1e-4f)

        fromHidden.onScrollDelta(deltaY = 20f, isNearTop = false, enabled = true) // progress = 0.50 <= 0.55
        assertEquals(0f, fromHidden.resolveSettleTarget(velocityY = 0f, isNearTop = false, enabled = true), 1e-4f)
    }

    @Test
    fun disablingRevealsImmediatelyAndIgnoresScroll() {
        val tracker = newTracker()
        assertEquals(1f, tracker.onScrollDelta(deltaY = -120f, isNearTop = false, enabled = true), 1e-4f)

        assertEquals(0f, tracker.onScrollDelta(deltaY = -100f, isNearTop = false, enabled = false), 1e-4f)
        assertEquals(0f, tracker.progress, 1e-4f)
    }
}
