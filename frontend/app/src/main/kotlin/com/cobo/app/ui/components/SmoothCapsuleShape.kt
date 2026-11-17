package com.cobo.app.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.min

/**
 * ColorOS 17 Continuous-Curvature Capsule (G2 Smooth Corner Squircle).
 * Based on ColorOS 17 cubic bezier easing formulation with zero curvature junction.
 */
open class COUISmoothCapsuleShape(
    private val cornerRadius: Dp? = null,
) : Shape {
    companion object : COUISmoothCapsuleShape()

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val halfMin = size.minDimension / 2f
        val radius = cornerRadius?.let { with(density) { it.toPx() } } ?: halfMin
        val rect = size.toRect()
        if (rect.height <= 0f || rect.width <= 0f) {
            return Outline.Generic(Path())
        }
        if (rect.height >= rect.width) {
            return Outline.Rounded(RoundRect(rect, CornerRadius(halfMin)))
        }
        val path = Path()
        val r = min(radius, halfMin)
        val tail = min(1.5f, rect.width / (2f * r))
        val tailPx = tail * r
        path.moveTo(rect.left + tailPx, rect.top)
        path.lineTo(rect.right - tailPx, rect.top)

        // Top-right to tip
        path.addCapsuleCornerToTip(rect.right, rect.top, -1f, 1f, r, tail)
        // Tip to bottom-right
        path.addCapsuleCornerFromTip(rect.right, rect.bottom, -1f, -1f, r, tail)
        // Bottom edge
        path.lineTo(rect.left + tailPx, rect.bottom)
        // Bottom-left to tip
        path.addCapsuleCornerToTip(rect.left, rect.bottom, 1f, -1f, r, tail)
        // Tip to top-left
        path.addCapsuleCornerFromTip(rect.left, rect.top, 1f, 1f, r, tail)
        path.close()
        return Outline.Generic(path)
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
            cornerY + scaleY * 0.7696933f,
            cornerX + scaleX * 0.07949496f,
            cornerY + scaleY * 0.5464456f,
            cornerX + scaleX * 0.2250555f,
            cornerY + scaleY * 0.3679707f,
        )
        cubicTo(
            cornerX + scaleX * 0.5251651f,
            cornerY,
            cornerX + scaleX * min(0.9615864f, tail),
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
            cornerX + scaleX * min(0.9615864f, tail),
            cornerY,
            cornerX + scaleX * 0.5251651f,
            cornerY,
            cornerX + scaleX * 0.2250555f,
            cornerY + scaleY * 0.3679707f,
        )
        cubicTo(
            cornerX + scaleX * 0.07949496f,
            cornerY + scaleY * 0.5464456f,
            cornerX,
            cornerY + scaleY * 0.7696933f,
            cornerX,
            cornerY + scaleY,
        )
    }

    override fun equals(other: Any?): Boolean =
        other is COUISmoothCapsuleShape && other.cornerRadius == cornerRadius

    override fun hashCode(): Int = cornerRadius?.hashCode() ?: 0
}
