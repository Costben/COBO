package com.cobo.app.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

private const val FRAME_SECONDS = 1.0 / 120.0
private const val REST_HEIGHT = 32f
private const val SEGMENT_WIDTH = 120f

class CouiSegmentMotionTest {

    private fun createMotion() = CouiSegmentIndicatorMotion(
        center = 60f,
        width = SEGMENT_WIDTH,
        height = REST_HEIGHT,
    )

    @Test
    fun selectingSquashesTheIndicatorOnItsWayOver() {
        val motion = createMotion()
        motion.select(
            targetCenter = 180f,
            targetWidth = SEGMENT_WIDTH,
            restHeight = REST_HEIGHT,
            firstCenter = 60f,
            lastCenter = 180f,
            maxExtraWidth = 16f,
        )

        var minHeight = Float.MAX_VALUE
        var maxWidth = 0f
        repeat(240) {
            motion.advance(FRAME_SECONDS)
            minHeight = minOf(minHeight, motion.height)
            maxWidth = maxOf(maxWidth, motion.width)
        }

        // 高度必须压扁到 0.9h 左右（28.8f）
        assertEquals(REST_HEIGHT * 0.9f, minHeight, 0.05f)
        // 宽度在横向移动中明显拉伸
        assertTrue("宽度应该在移动中拉伸展开，实际: $maxWidth", maxWidth > SEGMENT_WIDTH)
    }

    @Test
    fun motionSettlesOnTarget() {
        val motion = createMotion()
        motion.select(
            targetCenter = 180f,
            targetWidth = SEGMENT_WIDTH,
            restHeight = REST_HEIGHT,
            firstCenter = 60f,
            lastCenter = 180f,
            maxExtraWidth = 16f,
        )

        repeat(600) { motion.advance(FRAME_SECONDS) }
        assertEquals(180f, motion.centerX, 0.5f)
        assertEquals(SEGMENT_WIDTH, motion.width, 0.5f)
        assertEquals(REST_HEIGHT, motion.height, 0.5f)
        assertFalse(motion.isRunning)
    }
}
