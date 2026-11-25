package com.cobo.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.anim.COUIInEase
import io.github.suqi8.coui.kmp.anim.COUIOutEase
import kotlin.math.abs

/**
 * 浮动栏自动隐藏的纯状态机。
 *
 * 按纵向滚动位移累计方向里程：向下浏览（[deltaY] < 0）累计超过 [hideThresholdPx] 后进入隐藏状态；
 * 向上回滚（[deltaY] > 0）累计超过 [showThresholdPx] 后重新展开。任一方向反转会清零反向累计，
 * 避免轻微抖动频繁切换。停留在顶部保护区或功能关闭时立即复位为可见。
 */
internal class FloatingBarHideTracker(
    private val hideThresholdPx: Float,
    private val showThresholdPx: Float,
) {
    var hidden: Boolean = false
        private set

    private var downAccumulatedPx = 0f
    private var upAccumulatedPx = 0f

    fun reset(hidden: Boolean = false) {
        this.hidden = hidden
        downAccumulatedPx = 0f
        upAccumulatedPx = 0f
    }

    fun onScrollDelta(deltaY: Float, isNearTop: Boolean, enabled: Boolean): Boolean {
        if (!enabled || isNearTop) {
            reset(hidden = false)
            return false
        }
        if (deltaY < 0f) {
            upAccumulatedPx = 0f
            if (!hidden) {
                downAccumulatedPx += -deltaY
                if (downAccumulatedPx >= hideThresholdPx) {
                    hidden = true
                    downAccumulatedPx = 0f
                }
            }
        } else if (deltaY > 0f) {
            downAccumulatedPx = 0f
            if (hidden) {
                upAccumulatedPx += deltaY
                if (upAccumulatedPx >= showThresholdPx) {
                    hidden = false
                    upAccumulatedPx = 0f
                }
            }
        }
        return hidden
    }
}

internal const val FloatingBarHideDurationMillis = 220
internal const val FloatingBarShowDurationMillis = 240

/**
 * 监听列表嵌套滚动的浮动栏控制器，仅观测 [NestedScrollConnection.onPostScroll] 消费到的位移，
 * 不拦截子列表或下拉刷新组件的滚动事件。
 */
@Stable
class FloatingBarScrollController internal constructor(
    hideThresholdPx: Float,
    showThresholdPx: Float,
) {
    private val tracker = FloatingBarHideTracker(hideThresholdPx, showThresholdPx)

    var hidden by mutableStateOf(false)
        private set

    internal var enabled: Boolean = false
    internal var forceShow: Boolean = false
    internal var isNearTop: () -> Boolean = { true }

    val connection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset {
            if (!enabled || forceShow) {
                if (hidden) show()
                return Offset.Zero
            }
            if (abs(consumed.y) < 0.5f || abs(consumed.x) > abs(consumed.y)) {
                return Offset.Zero
            }
            hidden = tracker.onScrollDelta(
                deltaY = consumed.y,
                isNearTop = isNearTop(),
                enabled = true,
            )
            return Offset.Zero
        }
    }

    fun show() {
        tracker.reset(hidden = false)
        hidden = false
    }
}

/**
 * 创建并与当前列表顶部状态、弹层强制显示状态联动的 [FloatingBarScrollController]。
 */
@Composable
fun rememberFloatingBarScrollController(
    enabled: Boolean,
    forceShow: Boolean,
    listState: LazyListState?,
    topSafeZone: Dp,
    hideThreshold: Dp = 24.dp,
    showThreshold: Dp = 16.dp,
): FloatingBarScrollController {
    val density = LocalDensity.current
    val hideThresholdPx = with(density) { hideThreshold.toPx() }
    val showThresholdPx = with(density) { showThreshold.toPx() }
    val topSafeZonePx = with(density) { topSafeZone.toPx() }

    val controller = remember(hideThresholdPx, showThresholdPx) {
        FloatingBarScrollController(hideThresholdPx, showThresholdPx)
    }

    controller.enabled = enabled
    controller.forceShow = forceShow
    controller.isNearTop = {
        listState == null ||
            (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset <= topSafeZonePx)
    }

    LaunchedEffect(enabled, forceShow) {
        if (!enabled || forceShow) controller.show()
    }

    LaunchedEffect(listState, topSafeZonePx, enabled) {
        if (!enabled || listState == null) {
            controller.show()
            return@LaunchedEffect
        }
        snapshotFlow {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset <= topSafeZonePx
        }.collect { nearTop ->
            if (nearTop) controller.show()
        }
    }

    return controller
}

/**
 * 浮动栏隐藏进度动画（`0f` 为完全显示，`1f` 为完全收起）。
 */
@Composable
fun rememberFloatingBarHideProgress(
    hidden: Boolean,
    label: String = "floatingBarHide",
): State<Float> = animateFloatAsState(
    targetValue = if (hidden) 1f else 0f,
    animationSpec = tween(
        durationMillis = if (hidden) FloatingBarHideDurationMillis else FloatingBarShowDurationMillis,
        easing = if (hidden) COUIOutEase else COUIInEase,
    ),
    label = label,
)
