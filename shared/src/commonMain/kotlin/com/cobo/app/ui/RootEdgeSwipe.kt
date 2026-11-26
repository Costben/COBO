package com.cobo.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.cobo.app.App
import com.cobo.app.platform.BackDispatcher
import io.github.suqi8.coui.kmp.utils.PredictiveBackDragTracker
import io.github.suqi8.coui.kmp.utils.PredictiveBackMotionDefaults
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private val EdgeSwipeStripWidth = 28.dp

/**
 * 双侧边缘（左边缘向右滑、右边缘向左滑）交互式跟手返回浮层。
 *
 * - 优先派发给栈顶页内已启用的 [BackDispatcher] 处理器（如关闭搜索面板、评论详情、底部编辑框、WebView 后退）；
 * - 若页内未消费且导航栈深度 > 1，则按当前设置驱动预测性跟手返回（卡片缩放回原位、图片缩放、或页面横滑退场）。
 */
@Composable
internal fun BoxScope.EdgeSwipeBackOverlay(
    widthPx: Float,
    nav: NavState,
    backDispatcher: BackDispatcher,
    scope: CoroutineScope,
) {
    EdgeSwipeStrip(
        fromRight = false,
        widthPx = widthPx,
        nav = nav,
        backDispatcher = backDispatcher,
        scope = scope,
        modifier = Modifier
            .align(Alignment.CenterStart)
            .fillMaxHeight()
            .width(EdgeSwipeStripWidth)
            .zIndex(10f),
    )
    EdgeSwipeStrip(
        fromRight = true,
        widthPx = widthPx,
        nav = nav,
        backDispatcher = backDispatcher,
        scope = scope,
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .width(EdgeSwipeStripWidth)
            .zIndex(10f),
    )
}

@Composable
private fun EdgeSwipeStrip(
    fromRight: Boolean,
    widthPx: Float,
    nav: NavState,
    backDispatcher: BackDispatcher,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val maxFlingSpeedPx = with(density) { PredictiveBackMotionDefaults.MaxFlingSpeed.toPx() }
    val flingCommitVelocityPx = with(density) { 450.dp.toPx() }

    Box(
        modifier = modifier.pointerInput(fromRight, nav, widthPx, maxFlingSpeedPx) {
            val touchSlop = viewConfiguration.touchSlop
            val stripWidthPx = size.width.toFloat()
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val pointerId = down.id
                val startPos = down.position
                var started = false
                var lastChange = down

                while (!started) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == pointerId } ?: return@awaitEachGesture
                    if (!change.pressed) return@awaitEachGesture
                    lastChange = change
                    val dx = change.position.x - startPos.x
                    val dy = change.position.y - startPos.y
                    val inwardDx = if (fromRight) -dx else dx
                    if (abs(dy) > touchSlop && abs(dy) > abs(dx) * 1.15f) {
                        return@awaitEachGesture
                    }
                    if (inwardDx < -touchSlop) {
                        return@awaitEachGesture
                    }
                    if (inwardDx > touchSlop && inwardDx >= abs(dy) * 0.7f) {
                        started = true
                        change.consume()
                    }
                }

                // 优先交给页内返回处理器（例如关闭搜索、关闭评论详情浮层、关闭底部弹窗、WebView 后退）。
                if (backDispatcher.dispatch()) return@awaitEachGesture

                val entry = nav.stack.lastOrNull { !it.exiting } ?: return@awaitEachGesture
                if (nav.stack.count { !it.exiting } <= 1) return@awaitEachGesture
                if (entry === nav.paneEntry) {
                    nav.dismissPane()
                    return@awaitEachGesture
                }

                if (!App.settings.predictiveBack) {
                    nav.pop()
                    return@awaitEachGesture
                }

                val gestureGen = ++entry.previewGeneration
                var lastProgress = 1f
                var moved = false
                entry.previewing = true
                nav.preparePredictivePop(entry)

                val trackFinger = (entry.expand && App.settings.predictiveBackCardFling) ||
                    (entry.growsFromImage && App.settings.predictiveBackImageFling)
                entry.followFlingActive = trackFinger
                val dragTracker = if (trackFinger) PredictiveBackDragTracker() else null

                val screenStartX = if (fromRight) (widthPx - stripWidthPx + startPos.x) else startPos.x
                dragTracker?.record(screenStartX, startPos.y, down.uptimeMillis)

                var prevX = lastChange.position.x
                var prevT = lastChange.uptimeMillis
                var lastInwardVx = 0f

                fun applyPointer(pos: Offset, uptimeMs: Long) {
                    val dt = (uptimeMs - prevT).coerceAtLeast(1L)
                    val rawVx = (pos.x - prevX) * 1000f / dt
                    val inwardVx = if (fromRight) -rawVx else rawVx
                    lastInwardVx = lastInwardVx * 0.4f + inwardVx * 0.6f
                    prevX = pos.x
                    prevT = uptimeMs

                    val inwardDelta = (if (fromRight) startPos.x - pos.x else pos.x - startPos.x).coerceAtLeast(0f)
                    val span = (widthPx * 0.65f).coerceAtLeast(1f)
                    val raw = (inwardDelta / span).coerceIn(0f, 1f)
                    val value = if (entry.expand) {
                        (1f - raw / (1f - LandedProgress)).coerceIn(0f, 1f)
                    } else {
                        (1f - raw).coerceIn(0f, 1f)
                    }
                    if (value < 0.98f) moved = true
                    lastProgress = value
                    val screenX = if (fromRight) (widthPx - stripWidthPx + pos.x) else pos.x
                    val followOffset = dragTracker?.record(screenX, pos.y, uptimeMs)
                    scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        entry.progress.snapTo(value)
                        if (entry.expand) entry.fade.snapTo(value)
                        if (followOffset != null) entry.offset.snapTo(followOffset)
                    }
                }

                applyPointer(lastChange.position, lastChange.uptimeMillis)

                var canceled = false
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == pointerId }
                    if (change == null) {
                        canceled = true
                        break
                    }
                    if (!change.pressed) {
                        if (change.positionChanged()) {
                            applyPointer(change.position, change.uptimeMillis)
                            change.consume()
                        }
                        break
                    }
                    if (change.positionChanged()) {
                        applyPointer(change.position, change.uptimeMillis)
                        change.consume()
                    }
                }

                val commitVelocity = dragTracker?.velocityPxPerSec(maxFlingSpeedPx) ?: Offset.Zero
                val shouldCommit = !canceled && (
                    lastProgress <= 0.68f ||
                        (moved && lastInwardVx >= flingCommitVelocityPx)
                    )
                val landed = if (entry.expand) lastProgress <= 0.05f else lastProgress <= LandedProgress
                if (shouldCommit || (landed && moved)) {
                    finishPredictivePop(nav, entry, lastProgress, moved, commitVelocity)
                } else {
                    scope.launch {
                        coroutineScope {
                            launch { entry.progress.animateTo(1f, ExpandCancelSpringSpec) }
                            if (entry.expand) {
                                launch { entry.fade.animateTo(1f, tween(200, easing = LinearEasing)) }
                            }
                            if (entry.offset.value != Offset.Zero) {
                                launch { entry.returnFlingOffset() }
                            }
                        }
                        if (entry.previewGeneration == gestureGen) {
                            nav.cancelPredictivePop(entry)
                        }
                    }
                }
            }
        },
    )
}

internal fun finishPredictivePop(
    nav: NavState,
    entry: NavEntry,
    lastProgress: Float,
    moved: Boolean,
    offsetVelocity: Offset = Offset.Zero,
) {
    val flinging = entry.offset.value != Offset.Zero || offsetVelocity != Offset.Zero
    if (entry.expand) {
        nav.pop(initialVelocity = offsetVelocity)
    } else if (lastProgress <= 0.02f && moved && !flinging) {
        nav.completePredictivePop(entry)
    } else {
        nav.pop(initialVelocity = offsetVelocity)
    }
}

/**
 * 退场或跟手返回预览期间将页面冻结为单张 GPU 离屏快照，
 * 避免缩回动画每帧重绘 LazyColumn、重录 LayerBackdrop 与执行玻璃模糊导致掉帧。
 */
@Composable
internal fun ExitSnapshotHost(
    exiting: Boolean,
    hasNativeView: Boolean = false,
    content: @Composable () -> Unit,
) {
    val exitLayer = rememberGraphicsLayer()
    val recordedRef = remember { BooleanArray(1) }
    val active = exiting && !hasNativeView
    if (!active) {
        recordedRef[0] = false
    }
    Box(
        Modifier
            .fillMaxSize()
            .then(
                if (active) {
                    Modifier.drawWithContent {
                        if (!recordedRef[0]) {
                            exitLayer.record { this@drawWithContent.drawContent() }
                            recordedRef[0] = true
                        }
                        drawLayer(exitLayer)
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        content()
    }
}

@Stable
internal class LayerPipelineWarmup {
    var active by mutableStateOf(false)
        internal set
}

/** 启动后短暂预热 Metal 离屏圆角裁切与快照图层管线，避免首次进出微博卡片时临时编译着色器掉帧。 */
@Composable
internal fun rememberLayerPipelineWarmup(nav: NavState): LayerPipelineWarmup {
    val warmup = remember { LayerPipelineWarmup() }
    LaunchedEffect(warmup) {
        delay(450L)
        if (nav.stack.size != 1) return@LaunchedEffect
        warmup.active = true
        repeat(2) {
            withFrameNanos { }
            if (nav.stack.size != 1) {
                warmup.active = false
                return@LaunchedEffect
            }
        }
        warmup.active = false
    }
    return warmup
}
