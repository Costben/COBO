package com.cobo.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.anim.COUIInEase
import io.github.suqi8.coui.kmp.anim.COUIOutEase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs

internal const val FloatingBarNonInteractiveThreshold = 0.78f
internal const val FloatingBarSettleDurationMillis = 200
internal const val FloatingBarDefaultCommitThreshold = 0.45f
internal const val FloatingBarDefaultFlingVelocityPxPerSec = 900f

/**
 * 浮动栏连续跟手隐藏的纯状态机（`0f` 为完全显示，`1f` 为完全收起）。
 *
 * 向下浏览（[deltaY] < 0）在越过 [hideDeadzonePx] 后，按 [hideRangePx] 线性累计进度至 `1f`；
 * 向上回滚（[deltaY] > 0）在越过 [showDeadzonePx] 后，按 [showRangePx] 线性回拉进度至 `0f`。
 * 方向反转会重新补满对向死区，避免轻微抖动立即触发跳变；停手或甩动时由 [resolveSettleTarget]
 * 结合起始锚态与速度决定展开或收回。
 */
internal class FloatingBarHideTracker(
    private val hideDeadzonePx: Float,
    private val hideRangePx: Float,
    private val showDeadzonePx: Float,
    private val showRangePx: Float,
    private val commitThreshold: Float = FloatingBarDefaultCommitThreshold,
    private val flingVelocityThresholdPxPerSec: Float = FloatingBarDefaultFlingVelocityPxPerSec,
) {
    var progress: Float = 0f
        private set

    private var anchorHidden: Boolean = false
    private var hideDeadzoneRemaining: Float = hideDeadzonePx
    private var showDeadzoneRemaining: Float = 0f

    fun reset(progress: Float = 0f) {
        val clamped = progress.coerceIn(0f, 1f)
        this.progress = clamped
        anchorHidden = clamped >= 0.5f
        hideDeadzoneRemaining = if (clamped <= 0f) hideDeadzonePx else 0f
        showDeadzoneRemaining = if (clamped >= 1f) showDeadzonePx else 0f
    }

    fun syncProgress(value: Float) {
        progress = value.coerceIn(0f, 1f)
    }

    fun onScrollDelta(deltaY: Float, isNearTop: Boolean, enabled: Boolean): Float {
        if (!enabled || isNearTop) {
            reset(0f)
            return 0f
        }
        if (deltaY < 0f) {
            showDeadzoneRemaining = showDeadzonePx
            var consume = -deltaY
            if (progress <= 0f && hideDeadzoneRemaining > 0f) {
                val absorbed = minOf(consume, hideDeadzoneRemaining)
                hideDeadzoneRemaining -= absorbed
                consume -= absorbed
            }
            if (consume > 0f && hideRangePx > 0f) {
                progress = (progress + consume / hideRangePx).coerceIn(0f, 1f)
                if (progress >= 1f) {
                    anchorHidden = true
                    showDeadzoneRemaining = showDeadzonePx
                }
            }
        } else if (deltaY > 0f) {
            hideDeadzoneRemaining = hideDeadzonePx
            var consume = deltaY
            if (progress >= 1f && showDeadzoneRemaining > 0f) {
                val absorbed = minOf(consume, showDeadzoneRemaining)
                showDeadzoneRemaining -= absorbed
                consume -= absorbed
            }
            if (consume > 0f && showRangePx > 0f) {
                progress = (progress - consume / showRangePx).coerceIn(0f, 1f)
                if (progress <= 0f) {
                    anchorHidden = false
                    hideDeadzoneRemaining = hideDeadzonePx
                }
            }
        }
        return progress
    }

    fun resolveSettleTarget(velocityY: Float, isNearTop: Boolean, enabled: Boolean): Float {
        if (!enabled || isNearTop || progress <= 0.001f) return 0f
        if (progress >= 0.999f) return 1f
        if (velocityY >= flingVelocityThresholdPxPerSec) return 0f
        if (velocityY <= -flingVelocityThresholdPxPerSec) return 1f
        return if (anchorHidden) {
            if (progress <= 1f - commitThreshold) 0f else 1f
        } else {
            if (progress >= commitThreshold) 1f else 0f
        }
    }
}

/**
 * 监听列表嵌套滚动的连续跟手浮动栏控制器，仅观测 [NestedScrollConnection.onPostScroll] 与
 * [NestedScrollConnection.onPostFling] 消费到的位移和速度，不拦截子列表或下拉刷新事件。
 */
@Stable
class FloatingBarScrollController internal constructor(
    private val scope: CoroutineScope,
    hideDeadzonePx: Float,
    hideRangePx: Float,
    showDeadzonePx: Float,
    showRangePx: Float,
) {
    private val tracker = FloatingBarHideTracker(
        hideDeadzonePx = hideDeadzonePx,
        hideRangePx = hideRangePx,
        showDeadzonePx = showDeadzonePx,
        showRangePx = showRangePx,
    )

    private val _hideProgress = mutableFloatStateOf(0f)

    /** 稳定的只读进度状态，在 draw / graphicsLayer / offset 阶段读取以避免整页重组。 */
    val hideProgress: State<Float> get() = _hideProgress

    private var settleJob: Job? = null

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
                if (_hideProgress.floatValue > 0f) show()
                return Offset.Zero
            }
            if (abs(consumed.y) < 0.5f || abs(consumed.x) > abs(consumed.y)) {
                return Offset.Zero
            }
            settleJob?.cancel()
            settleJob = null
            val next = tracker.onScrollDelta(
                deltaY = consumed.y,
                isNearTop = isNearTop(),
                enabled = true,
            )
            _hideProgress.floatValue = next
            return Offset.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            if (!enabled || forceShow) {
                if (_hideProgress.floatValue > 0f) show()
                return Velocity.Zero
            }
            settle(consumed.y)
            return Velocity.Zero
        }
    }

    fun settle(velocityY: Float = 0f) {
        val current = _hideProgress.floatValue
        if (current <= 0.001f || current >= 0.999f) {
            if (current <= 0.001f && current != 0f) {
                tracker.reset(0f)
                _hideProgress.floatValue = 0f
            } else if (current >= 0.999f && current != 1f) {
                tracker.reset(1f)
                _hideProgress.floatValue = 1f
            }
            return
        }
        val target = tracker.resolveSettleTarget(
            velocityY = velocityY,
            isNearTop = isNearTop(),
            enabled = enabled && !forceShow,
        )
        animateTo(target)
    }

    fun show(animate: Boolean = true) {
        if (!animate) {
            settleJob?.cancel()
            settleJob = null
            tracker.reset(0f)
            _hideProgress.floatValue = 0f
            return
        }
        if (_hideProgress.floatValue <= 0.001f) {
            settleJob?.cancel()
            settleJob = null
            tracker.reset(0f)
            _hideProgress.floatValue = 0f
            return
        }
        animateTo(0f)
    }

    private fun animateTo(target: Float) {
        settleJob?.cancel()
        val start = _hideProgress.floatValue.coerceIn(0f, 1f)
        settleJob = scope.launch {
            val anim = Animatable(start)
            anim.animateTo(
                targetValue = target,
                animationSpec = tween(
                    durationMillis = FloatingBarSettleDurationMillis,
                    easing = if (target > 0.5f) COUIOutEase else COUIInEase,
                ),
            ) {
                _hideProgress.floatValue = value
                tracker.syncProgress(value)
            }
            tracker.reset(target)
            _hideProgress.floatValue = target
        }
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
    hideDeadzone: Dp = 8.dp,
    hideRange: Dp = 64.dp,
    showDeadzone: Dp = 12.dp,
    showRange: Dp = 80.dp,
): FloatingBarScrollController {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val hideDeadzonePx = with(density) { hideDeadzone.toPx() }
    val hideRangePx = with(density) { hideRange.toPx() }
    val showDeadzonePx = with(density) { showDeadzone.toPx() }
    val showRangePx = with(density) { showRange.toPx() }
    val topSafeZonePx = with(density) { topSafeZone.toPx() }

    val controller = remember(hideDeadzonePx, hideRangePx, showDeadzonePx, showRangePx) {
        FloatingBarScrollController(
            scope = scope,
            hideDeadzonePx = hideDeadzonePx,
            hideRangePx = hideRangePx,
            showDeadzonePx = showDeadzonePx,
            showRangePx = showRangePx,
        )
    }

    SideEffect {
        controller.enabled = enabled
        controller.forceShow = forceShow
        controller.isNearTop = {
            listState == null ||
                (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset <= topSafeZonePx)
        }
    }

    LaunchedEffect(enabled, forceShow) {
        if (!enabled || forceShow) controller.show()
    }

    LaunchedEffect(listState, topSafeZonePx, enabled) {
        if (!enabled || listState == null) {
            controller.show()
            return@LaunchedEffect
        }
        var wasScrolling = listState.isScrollInProgress
        snapshotFlow {
            val nearTop = listState.firstVisibleItemIndex == 0 &&
                listState.firstVisibleItemScrollOffset <= topSafeZonePx
            nearTop to listState.isScrollInProgress
        }.distinctUntilChanged().collect { (nearTop, scrolling) ->
            if (nearTop) {
                controller.show()
            } else if (wasScrolling && !scrolling) {
                val p = controller.hideProgress.value
                if (p > 0.001f && p < 0.999f) {
                    controller.settle(0f)
                }
            }
            wasScrolling = scrolling
        }
    }

    return controller
}
