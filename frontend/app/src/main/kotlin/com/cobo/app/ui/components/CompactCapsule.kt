package com.cobo.app.ui.components

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cobo.app.ui.blurEffectsEnabled
import io.github.suqi8.coui.kmp.anim.folmeSpring
import io.github.suqi8.coui.kmp.basic.CapsuleOutlineShape
import io.github.suqi8.coui.kmp.basic.ChipTabDefaults
import io.github.suqi8.coui.kmp.basic.GlassChipDefaults
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.blur.Backdrop
import io.github.suqi8.coui.kmp.blur.isBlurEnabled
import io.github.suqi8.coui.kmp.blur.textureBlur
import io.github.suqi8.coui.kmp.squircle.isSquircleEnabled
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlin.math.max

private val ChipTabCheckedSurfaceLight = Color(0xFFF5F6F7)
private val ChipTabCheckedSurfaceDark = Color(0xFF4B4B4B)

private val ChipTabSurfaceStopsLight: Array<Pair<Float, Color>> = arrayOf(
    0.00f to Color(0xFFF7F8F9),
    0.05f to Color(0xFFF6F7F8),
    0.20f to ChipTabCheckedSurfaceLight,
    0.80f to ChipTabCheckedSurfaceLight,
    0.88f to Color(0xFFF6F7F8),
    0.96f to Color(0xFFF8F9F9),
    1.00f to Color(0xFFF8F9F9),
)

private val ChipTabSurfaceStopsDark: Array<Pair<Float, Color>> = arrayOf(
    0.00f to Color(0xFF4C4C4C),
    0.20f to ChipTabCheckedSurfaceDark,
    0.85f to ChipTabCheckedSurfaceDark,
    1.00f to Color(0xFF4D4D4D),
)

private val ChipTabRimWidthLight = 0.8.dp
private val ChipTabRimWidthDark = 0.55.dp
private val ChipTabRimBleedWidth = 0.52.dp
private const val CHIP_TAB_RIM_BLEED = 0.15f
private const val CHIP_TAB_RIM_BLEED_DARK = 0.05f
private val ChipTabBottomRampLight = 1.03.dp
private val ChipTabBottomSolidLight = 1.03.dp
private val ChipTabBottomRampDark = 0.78.dp
private const val CHIP_TAB_BOTTOM_ALPHA_DARK = 0.15f
private const val CHIP_TAB_BOTTOM_ALPHA_LIGHT = 0.72f
private val ChipTabSideShadeWidth = 15.5.dp
private const val CHIP_TAB_SIDE_SHADE_ALPHA = 0.016f
private const val CHIP_TAB_SIDE_LIGHT_ALPHA = 0.15f

private val ChipTabRimStopsLight: Array<Pair<Float, Float>> = arrayOf(
    0.000f to 1.00f,
    0.048f to 1.00f,
    0.097f to 0.95f,
    0.145f to 0.78f,
    0.194f to 0.55f,
    0.242f to 0.32f,
    0.290f to 0.12f,
    0.355f to 0.02f,
    0.420f to 0.00f,
    0.560f to 0.00f,
    0.620f to 0.05f,
    0.680f to 0.15f,
    0.740f to 0.40f,
    0.800f to 0.66f,
    0.870f to 0.90f,
    0.930f to 1.00f,
    1.000f to 1.00f,
)

private val ChipTabRimStopsDark: Array<Pair<Float, Float>> = arrayOf(
    0.000f to 0.270f,
    0.016f to 0.270f,
    0.032f to 0.160f,
    0.048f to 0.125f,
    0.065f to 0.100f,
    0.081f to 0.078f,
    0.097f to 0.055f,
    0.113f to 0.050f,
    0.161f to 0.028f,
    0.226f to 0.011f,
    0.274f to 0.000f,
    0.440f to 0.000f,
    0.540f to 0.006f,
    0.605f to 0.011f,
    0.669f to 0.022f,
    0.734f to 0.033f,
    0.798f to 0.050f,
    0.831f to 0.061f,
    0.895f to 0.083f,
    0.927f to 0.111f,
    0.976f to 0.167f,
    1.000f to 0.206f,
)

/** 绘制与 COUI 选中态 `ChipTab` 一致的上下高光边缘、侧边微阴影及底部光晕。 */
private fun DrawScope.drawChipTabCheckedRim(silhouette: Path, isDark: Boolean) {
    val width = size.width
    val height = size.height
    if (width <= 0f || height <= 0f) return

    if (!isDark) {
        val side = ChipTabSideShadeWidth.toPx().coerceAtMost(width / 2f)
        if (side > 0f) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Black.copy(alpha = CHIP_TAB_SIDE_SHADE_ALPHA), Color.Transparent),
                    startX = 0f,
                    endX = side,
                ),
                size = Size(side, height),
            )
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, Color.White.copy(alpha = CHIP_TAB_SIDE_LIGHT_ALPHA)),
                    startX = width - side,
                    endX = width,
                ),
                topLeft = Offset(width - side, 0f),
                size = Size(side, height),
            )
        }
    }

    val rim = (if (isDark) ChipTabRimWidthDark else ChipTabRimWidthLight).toPx().coerceAtMost(height / 4f)
    if (rim > 0f) {
        val stops = if (isDark) ChipTabRimStopsDark else ChipTabRimStopsLight
        fun rimBrush(scale: (Float) -> Float) = Brush.verticalGradient(
            colorStops = Array(stops.size) { index ->
                stops[index].first to Color.White.copy(alpha = scale(stops[index].second))
            },
            startY = 0f,
            endY = height,
        )
        val bleed = if (isDark) CHIP_TAB_RIM_BLEED_DARK else CHIP_TAB_RIM_BLEED
        drawPath(
            path = silhouette,
            brush = rimBrush { a -> a * bleed },
            style = Stroke(width = rim * 2f + ChipTabRimBleedWidth.toPx() * 2f),
        )
        drawPath(
            path = silhouette,
            brush = rimBrush { a -> a * (1f - bleed) / (1f - a * bleed) },
            style = Stroke(width = rim * 2f),
        )
    }

    val solid = (if (isDark) 0.dp else ChipTabBottomSolidLight).toPx()
    val base = max(solid, rim)
    val ramp = (if (isDark) ChipTabBottomRampDark else ChipTabBottomRampLight).toPx()
        .coerceAtMost((height - base).coerceAtLeast(0f))
    val rampAlpha = if (isDark) CHIP_TAB_BOTTOM_ALPHA_DARK else CHIP_TAB_BOTTOM_ALPHA_LIGHT
    if (ramp > 0f) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.White.copy(alpha = rampAlpha)),
                startY = height - base - ramp,
                endY = height - base,
            ),
            topLeft = Offset(0f, height - base - ramp),
            size = Size(width, ramp),
        )
    }
    if (solid > 0f) {
        drawRect(
            color = Color.White,
            topLeft = Offset(0f, height - solid),
            size = Size(width, solid),
        )
    }
}

/**
 * 胶囊材质描边（用于无 backdrop 的纯平面输入容器兜底）。
 */
internal fun Modifier.capsuleMaterial(
    isDark: Boolean,
    shape: Shape = COUISmoothCapsuleShape,
): Modifier = border(
    width = 0.5.dp,
    color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.45f),
    shape = shape,
)

private val ChipTabCausticDark = Color(0x33FFFFFF)
private val ChipTabCausticBottomOffset = 4.299988.dp
private val ChipTabCausticSdfWidth = 9.22998.dp

private const val CHIP_TAB_CAUSTIC_SHADER = """
uniform float2 in_half;
uniform float in_corner;
uniform float in_bottom;
uniform float in_sdf;
uniform float4 in_color;
uniform float3 in_background;
float RBox(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0))) - r;
}
half4 main(float2 pos) {
    float2 pos_optics = pos - in_half - float2(0.0, in_bottom * 0.5);
    float2 halfSizeOptics = in_half + float2(0.0, in_bottom * 0.5);
    float sdf = min(in_sdf, min(in_half.x, in_half.y));
    if (in_color.a <= 0.001 || sdf <= 0.0 || any(greaterThan(abs(pos_optics), halfSizeOptics))) {
        return half4(0.0);
    }
    halfSizeOptics = max(halfSizeOptics - sdf, float2(0.0));
    float cornerOptics = min(in_corner, min(halfSizeOptics.x, halfSizeOptics.y));
    float opticsSdf = RBox(pos_optics, halfSizeOptics, cornerOptics);
    float value = smoothstep(sdf, 0.0, opticsSdf);
    value *= pow(smoothstep(halfSizeOptics.y + sdf, -halfSizeOptics.y - sdf, -pos_optics.y), 18.0);
    value = pow(value, 0.6) * 0.6 + 0.4 * value;
    float gray = clamp((dot(float3(0.299, 0.587, 0.114), in_background) - 0.45) * 0.6, 0.0, 1.0);
    float alpha = in_color.a * value * (1.0 - gray) * (1.0 - gray);
    return half4(in_color.rgb * alpha, alpha);
}
"""

/** 为深色模式胶囊补充与 COUI 选中态 `ChipTab` 一致的底部焦散光晕（向下溢出约 4.3dp）。 */
@Composable
private fun Modifier.chipTabCausticLight(): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return this
    val shaderState = remember {
        runCatching {
            val shader = RuntimeShader(CHIP_TAB_CAUSTIC_SHADER)
            shader to ShaderBrush(shader)
        }.getOrNull()
    } ?: return this
    val background = COUITheme.colorScheme.background
    return drawBehind {
        val (shader, brush) = shaderState
        val halfW = size.width / 2f
        val halfH = size.height / 2f
        if (halfW <= 0f || halfH <= 0f) return@drawBehind
        val bottom = ChipTabCausticBottomOffset.toPx().toInt().toFloat()
        val sdf = ChipTabCausticSdfWidth.toPx().toInt().toFloat()
        val cornerPx = (size.minDimension / 2f) * 1.41421356f
        shader.setFloatUniform("in_half", halfW, halfH)
        shader.setFloatUniform("in_corner", cornerPx)
        shader.setFloatUniform("in_bottom", bottom)
        shader.setFloatUniform("in_sdf", sdf)
        shader.setFloatUniform(
            "in_color",
            ChipTabCausticDark.red,
            ChipTabCausticDark.green,
            ChipTabCausticDark.blue,
            ChipTabCausticDark.alpha,
        )
        shader.setFloatUniform("in_background", background.red, background.green, background.blue)
        drawRect(
            brush = brush,
            size = Size(size.width, size.height + bottom),
            blendMode = BlendMode.Plus,
        )
    }
}

/**
 * 与 COUI 选中态 `ChipTab` 完全一致的透明磨砂胶囊材质：
 * 复用 `GlassChipDefaults` 的混合模糊色、`ChipTabDefaults.CheckedShadow` 投影、深色底部焦散发光以及上下边缘高光描边。
 */
@Composable
internal fun Modifier.inputCapsuleGlass(
    backdrop: Backdrop?,
    isDark: Boolean,
    fallbackColor: Color = COUITheme.colorScheme.surfaceContainer,
): Modifier {
    val squircleEnabled = isSquircleEnabled()
    val pillShape = remember(squircleEnabled) {
        CapsuleOutlineShape(squircleEnabled = squircleEnabled)
    }
    val density = LocalDensity.current
    val blurActive = blurEffectsEnabled() && isBlurEnabled() && backdrop != null
    val blurRadiusDp = remember(density) { with(density) { GlassChipDefaults.BlurRadiusPx.toDp().value } }
    val blurColors = remember(isDark, blurActive) {
        if (blurActive) GlassChipDefaults.blurColors(isDark, fraction = 1f) else null
    }
    val surfaceStops = if (isDark) ChipTabSurfaceStopsDark else ChipTabSurfaceStopsLight

    return this
        .dropShadow(pillShape) {
            val shadow = ChipTabDefaults.CheckedShadow
            radius = shadow.radius.toPx()
            color = shadow.color
            offset = Offset(shadow.offset.x.toPx(), shadow.offset.y.toPx())
            alpha = shadow.alpha
        }
        .then(if (isDark) Modifier.chipTabCausticLight() else Modifier)
        .then(
            if (blurColors != null) {
                val targetBackdrop = backdrop!!
                Modifier.textureBlur(
                    backdrop = targetBackdrop,
                    shape = pillShape,
                    blurRadius = blurRadiusDp,
                    colors = blurColors,
                )
            } else {
                Modifier
            },
        )
        .drawWithCache {
            val outline = pillShape.createOutline(size, layoutDirection, this@drawWithCache)
            val silhouette = Path().apply { addOutline(outline) }
            val surfaceBrush = Brush.verticalGradient(colorStops = surfaceStops, startY = 0f, endY = size.height)
            onDrawWithContent {
                if (blurColors == null) {
                    drawPath(path = silhouette, brush = surfaceBrush)
                }
                clipPath(silhouette) {
                    drawChipTabCheckedRim(silhouette = silhouette, isDark = isDark)
                }
                clipPath(silhouette) { this@onDrawWithContent.drawContent() }
            }
        }
}

/**
 * 紧凑胶囊「发送」按钮：高度由调用方传入，用来与同一行的输入框严格对齐。
 * 按下缩到 0.89，连续曲率胶囊配微光材质，和发弹幕那一行是同一套观感。
 */
@Composable
internal fun CompactSendButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    height: Dp,
    containerColor: Color,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressProgress by animateFloatAsState(
        targetValue = if (enabled && isPressed) 1f else 0f,
        animationSpec = remember {
            folmeSpring(damping = 1f, response = 0.3f, visibilityThreshold = 0.001f)
        },
        label = "compactSendPress",
    )
    val scale = 1f - 0.11f * pressProgress
    val contentColor = if (enabled) {
        COUITheme.colorScheme.onSurface
    } else {
        COUITheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    Box(
        modifier = modifier
            .height(height)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .inputCapsuleGlass(backdrop = backdrop, isDark = isDark, fallbackColor = containerColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
            color = contentColor,
            maxLines = 1,
            softWrap = false,
        )
    }
}
