package com.cobo.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cobo.app.App
import io.github.suqi8.coui.kmp.basic.AppBarBlurOverlayDefaults
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

val ExpandEasing = CubicBezierEasing(0.3f, 0f, 0.1f, 1f)
const val ExpandEnterMillis = 420
const val ExpandExitMillis = 220
const val RouteSlideMillis = 320
const val RouteParallaxDivisor = 4f

/**
 * The bounds of the card a detail page grows from. Captured by [expandOrigin] on the
 * feed card and handed to [NavState.push] so the detail page can animate from the card
 * rect instead of sliding in from the right.
 */
@Stable
class ExpandOrigin(
    val layer: GraphicsLayer,
    val cornerRadius: Dp = 17.dp,
) {
    var bounds by mutableStateOf<Rect?>(null)

    /**
     * Bumped to force the card to redraw and re-record [layer]. The snapshot is taken at the
     * moment the card is tapped, so a page leaving later refreshes it to hand back the card as
     * it looks then instead of as it looked under the finger.
     */
    var refreshKey by mutableIntStateOf(0)

    /**
     * The list the card lives in. A card that is half scrolled under the floating bar hands the
     * page a rect that reaches under that bar, and the frame the detail page grows from — and
     * shrinks back into — is drawn above the bar. Rather than layering the frame differently, the
     * list is scrolled just far enough to bring the card out from under the bar, so the return
     * lands on a card the bar cannot cover.
     */
    var listState: LazyListState? = null

    /** The bar floating over [listState]; its bottom edge is the line the card has to clear. */
    var topBar: TopBarBounds? = null

    /** The card's key in [listState], used to check the card is still on screen before moving it. */
    var rowKey: Any? = null

    /** How far past the bar's own edge its material keeps drawing, in pixels. */
    var clearancePx: Float = 0f
}

@Composable
fun rememberExpandOrigin(cornerRadius: Dp = 17.dp): ExpandOrigin {
    val layer = rememberGraphicsLayer()
    val clearance = with(LocalDensity.current) { AppBarBlurOverlayDefaults.ExtraHeight.toPx() }
    return remember(layer, cornerRadius, clearance) {
        ExpandOrigin(layer, cornerRadius).apply { clearancePx = clearance }
    }
}

fun Modifier.expandOrigin(origin: ExpandOrigin): Modifier =
    // The full rect, not the part left visible by the list viewport: a card scrolled half off
    // the screen would otherwise hand the page a shorter frame than the card it grows from.
    onGloballyPositioned {
        origin.bounds = Rect(
            it.positionInRoot(),
            Size(it.size.width.toFloat(), it.size.height.toFloat()),
        )
    }
        .drawWithContent {
            // Reading the key makes a refresh request invalidate this draw, which re-records
            // the snapshot with the card as it looks now.
            origin.refreshKey
            origin.layer.record { this@drawWithContent.drawContent() }
            drawLayer(origin.layer)
        }

sealed interface Route {
    data object Main : Route
    data class Login(val addAccount: Boolean = false, val fresh: Boolean = false) : Route
    data class Detail(val id: String, val mid: String, val readOnly: Boolean = false) : Route
    data class EditHistory(val mid: String) : Route
    data class Profile(val uid: String?) : Route
    data object Search : Route
    data class Publish(val draft: String = "") : Route
    data class Chat(val uid: String, val name: String) : Route
    data class Web(val url: String, val title: String) : Route
    data class Container(val cid: String, val title: String) : Route
    data object Drafts : Route
    data object History : Route
    data object Accounts : Route
    data object Settings : Route
    data object Nearby : Route
    data class Topic(val name: String = "") : Route
    data class Images(val urls: List<String>, val index: Int) : Route
    data class Video(val url: String, val title: String) : Route
}

class NavEntry(
    val route: Route,
    val origin: ExpandOrigin? = null,
    initialProgress: Float = 1f,
    initialFade: Float = 1f,
) {
    val id = nextId++
    val progress = Animatable(initialProgress)
    val fade = Animatable(initialFade)
    var exiting by mutableStateOf(false)

    /** True while a back gesture is pulling this entry away, before it is actually popped. */
    var previewing by mutableStateOf(false)

    val expand: Boolean get() = route is Route.Detail && origin?.bounds != null

    private companion object {
        var nextId = 0
    }
}

@Stable
class NavState {
    val stack = mutableStateListOf<NavEntry>(
        NavEntry(if (App.repo.hasSession() || App.settings.localMode) Route.Main else Route.Login())
    )

    private var host: CoroutineScope? = null

    var mainTab by mutableIntStateOf(App.settings.mainTab)
        private set

    val current: Route get() = stack.last().route

    fun attach(scope: CoroutineScope) {
        host = scope
    }

    fun setTab(index: Int) {
        mainTab = index
        App.settings.applyMainTab(index)
    }

    fun push(route: Route, origin: ExpandOrigin? = null) {
        val entry = NavEntry(route, origin, initialProgress = 0f, initialFade = 0f)
        stack.add(entry)
        val scope = host ?: return
        scope.launch {
            withFrameNanos { }
            val expand = entry.expand
            if (!expand) entry.fade.snapTo(1f)
            coroutineScope {
                launch {
                    entry.progress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            if (expand) ExpandEnterMillis else RouteSlideMillis,
                            easing = ExpandEasing,
                        ),
                    )
                }
                if (expand) {
                    launch {
                        entry.fade.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(ExpandEnterMillis, easing = LinearEasing),
                        )
                    }
                }
            }
            // The opening page covers the list by now, so moving the row out from under the bar
            // cannot be seen, and the row is already clear when the page shrinks back into it.
            alignCardBelowBar(entry.origin)
        }
    }

    fun pop() {
        if (stack.size <= 1) return
        val entry = stack.last()
        if (entry.exiting) return
        entry.exiting = true
        entry.origin?.let { it.refreshKey++ }
        val scope = host
        if (scope == null) {
            stack.remove(entry)
            return
        }
        scope.launch {
            // A row that ended up under the bar while the page was open still has to be clear of it
            // before the frame starts shrinking, or the card would come back drawn over the bar.
            alignCardBelowBar(entry.origin)
            val expand = entry.expand
            coroutineScope {
                launch {
                    entry.progress.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(
                            if (expand) ExpandExitMillis else RouteSlideMillis,
                            easing = if (expand) LinearEasing else ExpandEasing,
                        ),
                    )
                }
                if (expand) {
                    launch {
                        entry.fade.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(ExpandExitMillis, easing = LinearEasing),
                        )
                    }
                }
            }
            stack.remove(entry)
        }
    }

    fun reset(route: Route) {
        stack.clear()
        stack.add(NavEntry(route))
    }

    /** Removes an entry whose progress was already driven to zero by a back gesture. */
    fun completePredictivePop(entry: NavEntry) {
        if (stack.lastOrNull() === entry) {
            entry.exiting = true
            stack.remove(entry)
        }
    }
}

/**
 * Points [origin] at the row it is drawn in, so a page returning into it can move that row clear of
 * the bar. A whole card is composed knowing its row; a quoted block inside a card lives in the same
 * row as that card, so it borrows the card's row when it is tapped.
 */
fun ExpandOrigin.attachToRow(listState: LazyListState?, rowKey: Any?, topBar: TopBarBounds?) {
    this.listState = listState
    this.rowKey = rowKey
    this.topBar = topBar
}

/**
 * Scrolls the list that owns [origin] just far enough to bring the card's top edge below the bar.
 *
 * The frame a detail page grows from and shrinks back into is drawn on the page's own layer, above
 * the bar the covered list keeps underneath it, so a card sitting half under the bar comes back
 * drawn over it. Moving the row instead of restacking the frame leaves the bar where it is: the
 * card ends up somewhere the bar cannot cover it, and nothing has to be raised or reordered.
 *
 * Scrolling the list back down can bring the bar's collapsing title row back, which moves the line
 * the bar ends at, so the move is measured again until the two agree.
 */
internal suspend fun alignCardBelowBar(origin: ExpandOrigin?) {
    if (origin == null) return
    val state = origin.listState ?: return
    val bar = origin.topBar ?: return
    repeat(4) {
        val line = bar.bottom
        if (line <= 0f) return
        // The row may have been dropped by a refresh while the page was open; a stale rect would
        // scroll the list to nowhere.
        val onScreen = origin.rowKey?.let { key ->
            state.layoutInfo.visibleItemsInfo.any { it.key == key }
        } ?: false
        if (!onScreen) return
        val top = origin.bounds?.top ?: return
        val delta = line + origin.clearancePx - top
        if (delta <= 0.5f) return
        // A list already at its start cannot move any further up; the row is as clear as it gets.
        if (state.scrollBy(-delta) == 0f) return
        // One frame for the list to lay out again, one for the bar to report its new edge.
        withFrameNanos { }
        withFrameNanos { }
    }
}
