// Copyright 2026, Cobo COUI contributors
// SPDX-License-Identifier: Apache-2.0
package com.cobo.app.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The continuous curvature capsule outline of ColorOS 17 (COUISegmentButtonLayout).
 *
 * Measured from screen pixels of COUISegmentButtonLayout on ColorOS 17.0 rather than
 * a plain circular arc. On screen the outline follows a circular arc for 39.2 degrees
 * from the tip, then eases into the straight edge, which ends 1.5 radii from the tip.
 *
 * The cubic joins the arc with the arc's curvature and reaches the edge with zero curvature,
 * eliminating the sharp curvature discontinuity at the junction.
 */
private const val TAIL_LENGTH = 1.5f
private const val ARC_HANDLE_1_Y = 0.7696933f
private const val ARC_HANDLE_2_X = 0.07949496f
private const val ARC_HANDLE_2_Y = 0.5464456f
private const val ARC_END_X = 0.2250555f
private const val ARC_END_Y = 0.3679707f
private const val EASE_HANDLE_1_X = 0.5251651f
private const val EASE_HANDLE_2_X = 0.9615864f

/**
 * Appends the COUI continuous curvature capsule filling [rect], whose radius is half the rect's height.
 */
fun Path.addSegmentCapsule(rect: Rect) {
    val radius = rect.height / 2f
    if (radius <= 0f || rect.width <= 0f) return
    val tail = min(TAIL_LENGTH, rect.width / (2f * radius))
    val tailPx = tail * radius
    moveTo(rect.left + tailPx, rect.top)
    lineTo(rect.right - tailPx, rect.top)
    addCapsuleCornerToTip(rect.right, rect.top, -1f, 1f, radius, tail)
    addCapsuleCornerFromTip(rect.right, rect.bottom, -1f, -1f, radius, tail)
    lineTo(rect.left + tailPx, rect.bottom)
    addCapsuleCornerToTip(rect.left, rect.bottom, 1f, -1f, radius, tail)
    addCapsuleCornerFromTip(rect.left, rect.top, 1f, 1f, radius, tail)
    close()
}

private fun Path.addCapsuleCornerFromTip(
    cornerX: Float,
    cornerY: Float,
    signX: Float,
    signY: Float,
    radius: Float,
    tail: Float,
) {
    val scaleX = signX * radius
    val scaleY = signY * radius
    cubicTo(
        cornerX,
        cornerY + scaleY * ARC_HANDLE_1_Y,
        cornerX + scaleX * ARC_HANDLE_2_X,
        cornerY + scaleY * ARC_HANDLE_2_Y,
        cornerX + scaleX * ARC_END_X,
        cornerY + scaleY * ARC_END_Y,
    )
    cubicTo(
        cornerX + scaleX * EASE_HANDLE_1_X,
        cornerY,
        cornerX + scaleX * min(EASE_HANDLE_2_X, tail),
        cornerY,
        cornerX + scaleX * tail,
        cornerY,
    )
}

private fun Path.addCapsuleCornerToTip(
    cornerX: Float,
    cornerY: Float,
    signX: Float,
    signY: Float,
    radius: Float,
    tail: Float,
) {
    val scaleX = signX * radius
    val scaleY = signY * radius
    cubicTo(
        cornerX + scaleX * min(EASE_HANDLE_2_X, tail),
        cornerY,
        cornerX + scaleX * EASE_HANDLE_1_X,
        cornerY,
        cornerX + scaleX * ARC_END_X,
        cornerY + scaleY * ARC_END_Y,
    )
    cubicTo(
        cornerX + scaleX * ARC_HANDLE_2_X,
        cornerY + scaleY * ARC_HANDLE_2_Y,
        cornerX,
        cornerY + scaleY * ARC_HANDLE_1_Y,
        cornerX,
        cornerY + scaleY,
    )
}

/**
 * Shape creating a continuous curvature capsule outline matching COUI 17.
 */
object CouiSegmentCapsuleShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path().apply { addSegmentCapsule(Rect(Offset.Zero, size)) }
        return Outline.Generic(path)
    }
}

private const val SPRING_VALUE_THRESHOLD = 0.75
private const val SPRING_VELOCITY_THRESHOLD = SPRING_VALUE_THRESHOLD * 62.5
private const val SPRING_UNBOUNDED = 2.14748365E9f

/**
 * A port of `COUISpringAnimation` over `COUISpringForce`, driven one frame at a time.
 * Clamps the value against bounds and keeps integrating the velocity so it rests against
 * the bound until the force turns it around.
 */
class CouiSpring(
    initialValue: Float,
    bounce: Float,
    response: Float,
) {
    var value: Float = initialValue
        private set
    var velocity: Float = 0f
    var isRunning: Boolean = false
        private set
    var minValue: Float = -SPRING_UNBOUNDED
    var maxValue: Float = SPRING_UNBOUNDED

    private var target: Float = initialValue
    private var pendingTarget: Float = Float.NaN
    private var dampingRatio: Double = 0.0
    private var naturalFrequency: Double = 0.0

    init {
        configure(bounce, response)
    }

    fun configure(bounce: Float, response: Float) {
        dampingRatio = (1f - bounce).toDouble()
        naturalFrequency = 2.0 * PI / response
    }

    fun setStart(start: Float) {
        value = start
    }

    fun animateTo(finalPosition: Float) {
        if (isRunning) {
            pendingTarget = finalPosition
        } else {
            target = finalPosition
            isRunning = true
        }
    }

    fun cancel() {
        isRunning = false
        pendingTarget = Float.NaN
    }

    fun snapTo(newValue: Float) {
        cancel()
        value = newValue
        target = newValue
        velocity = 0f
    }

    fun step(deltaSeconds: Double): Boolean {
        if (!isRunning) return false
        var position = value.toDouble()
        var speed = velocity.toDouble()
        if (!pendingTarget.isNaN()) {
            val half = deltaSeconds / 2.0
            integrate(position, speed, half) { p, v ->
                position = p
                speed = v
            }
            target = pendingTarget
            pendingTarget = Float.NaN
            integrate(position, speed, half) { p, v ->
                position = p
                speed = v
            }
        } else {
            integrate(position, speed, deltaSeconds) { p, v ->
                position = p
                speed = v
            }
        }
        value = min(max(position.toFloat(), minValue), maxValue)
        velocity = speed.toFloat()
        val atRest = abs(velocity.toDouble()) < SPRING_VELOCITY_THRESHOLD &&
            abs((value - target).toDouble()) < SPRING_VALUE_THRESHOLD
        if (atRest) {
            value = target
            velocity = 0f
            isRunning = false
        }
        return atRest
    }

    private inline fun integrate(
        position: Double,
        speed: Double,
        seconds: Double,
        result: (Double, Double) -> Unit,
    ) {
        val displacement = position - target
        val zeta = dampingRatio
        val omega = naturalFrequency
        if (zeta == 1.0) {
            val a = speed + omega * displacement
            val b = displacement + a * seconds
            val decay = exp(-omega * seconds)
            result(b * decay + target, a * decay - omega * b * decay)
        } else {
            val damped = omega * sqrt(1.0 - zeta * zeta)
            val a = (zeta * omega * displacement + speed) / damped
            val decay = exp(-zeta * omega * seconds)
            val oscillation = decay * (cos(damped * seconds) * displacement + sin(damped * seconds) * a)
            val nextSpeed = -omega * oscillation * zeta +
                decay * (-damped * displacement * sin(damped * seconds) + a * damped * cos(damped * seconds))
            result(oscillation + target, nextSpeed)
        }
    }
}

/** The indicator never squashes below this share of its height. */
private const val SEGMENT_SQUASH_RATIO = 0.9f

/** The width and height springs kick off with these velocities (px/s) like COUI 17 native view. */
private const val SEGMENT_WIDTH_KICK = 1000f
private const val SEGMENT_HEIGHT_KICK = -500f

/**
 * The sliding indicator motion controller for the segmented capsule tab.
 *
 * Implements the squash-and-stretch dynamic physics of ColorOS 17 COUISegmentButtonLayout:
 * On selection change the position spring carries the indicator over while the width spring
 * kicks outward and the height spring kicks inward. The capsule flattens (squash to 0.9h)
 * and stretches horizontally on its way, then recovers with a natural bounce upon arrival.
 */
@Stable
class CouiSegmentIndicatorMotion(
    center: Float,
    width: Float,
    height: Float,
) {
    var centerX: Float by mutableFloatStateOf(center)
        private set
    var width: Float by mutableFloatStateOf(width)
        private set
    var height: Float by mutableFloatStateOf(height)
        private set

    var active by mutableStateOf(false)
        private set

    private val position = CouiSpring(center, bounce = 0.1f, response = 0.4f)
    private val widthSpring = CouiSpring(width, bounce = 0.5f, response = 0.6f)
    private val heightSpring = CouiSpring(height, bounce = 0.5f, response = 0.6f)

    val isRunning: Boolean get() = position.isRunning || widthSpring.isRunning || heightSpring.isRunning

    fun snapTo(center: Float, width: Float, height: Float) {
        position.snapTo(center)
        widthSpring.snapTo(width)
        heightSpring.snapTo(height)
        centerX = center
        this.width = width
        this.height = height
        active = false
    }

    fun select(
        targetCenter: Float,
        targetWidth: Float,
        restHeight: Float,
        firstCenter: Float,
        lastCenter: Float,
        maxExtraWidth: Float = 27f,
    ) {
        position.configure(0.1f, 0.4f)
        position.minValue = min(firstCenter, lastCenter)
        position.maxValue = max(firstCenter, lastCenter)
        position.setStart(centerX.coerceIn(position.minValue, position.maxValue))
        position.animateTo(targetCenter.coerceIn(position.minValue, position.maxValue))

        val grows = targetWidth > width
        val widthBounce = if (grows) 0.5f else 0.3f
        widthSpring.maxValue = targetWidth + maxExtraWidth
        widthSpring.configure(widthBounce, 0.45f)
        widthSpring.setStart(width)
        widthSpring.velocity = SEGMENT_WIDTH_KICK
        widthSpring.animateTo(targetWidth)

        val squashed = restHeight * SEGMENT_SQUASH_RATIO
        heightSpring.minValue = squashed
        heightSpring.configure(0.5f, 0.45f)
        heightSpring.setStart(max(squashed, height))
        heightSpring.velocity = SEGMENT_HEIGHT_KICK
        heightSpring.animateTo(restHeight)

        active = true
    }

    suspend fun animate() {
        snapshotFlow { active }.collectLatest { running ->
            if (!running) return@collectLatest
            var lastFrame = -1L
            while (active) {
                withFrameNanos { frame ->
                    if (lastFrame >= 0L) {
                        val delta = (frame - lastFrame) / 1_000_000_000.0
                        advance(min(delta, 0.05))
                    }
                    lastFrame = frame
                }
            }
        }
    }

    fun advance(deltaSeconds: Double) {
        if (position.isRunning) {
            position.step(deltaSeconds)
            centerX = position.value
        }
        if (widthSpring.isRunning) {
            widthSpring.step(deltaSeconds)
            width = widthSpring.value
        }
        if (heightSpring.isRunning) {
            heightSpring.step(deltaSeconds)
            height = heightSpring.value
        }
        active = position.isRunning || widthSpring.isRunning || heightSpring.isRunning
    }

    fun updateDrag(center: Float, dynamicWidth: Float, dynamicHeight: Float) {
        if (position.isRunning || widthSpring.isRunning || heightSpring.isRunning) return
        centerX = center
        width = dynamicWidth
        height = dynamicHeight
    }
}
