package com.cobo.app.ui

import android.os.Build
import android.view.RoundedCorner
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cobo.app.App
import io.github.suqi8.coui.kmp.utils.PredictiveBackDragTracker
import io.github.suqi8.coui.kmp.utils.PredictiveBackMotionDefaults
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal actual val platformUsesCustomEdgeSwipeBack: Boolean = false

internal actual val platformSupportsPredictiveBackBlur: Boolean = true

internal actual fun Modifier.platformRootSemantics(): Modifier =
    this.semantics { testTagsAsResourceId = true }

@Composable
internal actual fun rememberScreenCornerRadius(): Dp {
    val context = LocalContext.current
    val density = LocalDensity.current
    return remember(context, density) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                val radius = context.display
                    ?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)
                    ?.radius ?: 0
                with(density) { radius.toDp() }
            }.getOrDefault(0.dp)
        } else {
            0.dp
        }
    }
}

@Composable
actual fun CoboPredictiveBackHandler(nav: NavState) {
    val hasPage = nav.stack.size > 1
    if (!App.settings.predictiveBack || nav.paneEntry != null) {
        BackHandler(enabled = hasPage) {
            if (nav.stack.lastOrNull()?.exiting != true) nav.pop()
        }
        return
    }
    val maxFlingSpeedPx = with(LocalDensity.current) { PredictiveBackMotionDefaults.MaxFlingSpeed.toPx() }
    PredictiveBackHandler(enabled = hasPage) { events ->
        val entry = nav.stack.lastOrNull()
        if (entry == null || nav.stack.size <= 1) {
            events.collect { }
            return@PredictiveBackHandler
        }
        if (entry.exiting) {
            events.collect { }
            return@PredictiveBackHandler
        }

        val trackFinger = when {
            entry.growsFromImage -> App.settings.predictiveBackImageFling
            entry.expand -> App.settings.predictiveBackCardFling
            else -> false
        }
        val dragTracker = if (trackFinger) PredictiveBackDragTracker() else null
        var lastProgress = 1f
        var moved = false
        val token = ++entry.previewGeneration
        entry.previewing = true
        entry.followFlingActive = trackFinger
        entry.origin?.let { it.refreshKey++ }
        try {
            events.collect { event ->
                val value = (1f - event.progress).coerceIn(0f, 1f)
                if (value < 0.98f) moved = true
                lastProgress = value
                entry.progress.snapTo(value)
                if (entry.expand) entry.fade.snapTo(value)
                if (dragTracker != null) {
                    entry.offset.snapTo(dragTracker.record(event.touchX, event.touchY, event.frameTimeMillis))
                }
            }
            val commitVelocity = dragTracker?.velocityPxPerSec(maxFlingSpeedPx) ?: Offset.Zero
            if (lastProgress <= 0.02f && moved && entry.offset.value == Offset.Zero && commitVelocity == Offset.Zero) {
                nav.completePredictivePop(entry)
            } else {
                nav.pop(initialVelocity = commitVelocity)
            }
        } catch (cancelled: CancellationException) {
            if (lastProgress <= LandedProgress && moved) {
                val cancelVelocity = dragTracker?.velocityPxPerSec(maxFlingSpeedPx) ?: Offset.Zero
                if (entry.offset.value == Offset.Zero && cancelVelocity == Offset.Zero) {
                    nav.completePredictivePop(entry)
                } else {
                    nav.pop(initialVelocity = cancelVelocity)
                }
                return@PredictiveBackHandler
            }
            withContext(NonCancellable) {
                coroutineScope {
                    launch {
                        entry.progress.animateTo(1f, tween(180, easing = ExpandEasing))
                    }
                    if (entry.expand) {
                        launch {
                            entry.fade.animateTo(1f, tween(180, easing = LinearEasing))
                        }
                    }
                    if (entry.offset.value != Offset.Zero) {
                        launch {
                            entry.offset.animateTo(Offset.Zero, tween(180, easing = ExpandEasing))
                        }
                    }
                }
                if (entry.previewGeneration == token && !entry.exiting) {
                    entry.previewing = false
                    entry.followFlingActive = false
                }
            }
            throw cancelled
        }
    }
}
