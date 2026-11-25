package com.cobo.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VectorConverter
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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
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
import com.cobo.app.data.NotificationTab
import com.cobo.app.data.Pic
import com.cobo.app.data.RelationKind
import com.cobo.app.screen.SettingsGroup
import io.github.suqi8.coui.kmp.basic.AppBarBlurOverlayDefaults
import io.github.suqi8.coui.kmp.utils.PredictiveBackMotionDefaults
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

val ExpandEasing = CubicBezierEasing(0.3f, 0f, 0.1f, 1f)
const val ExpandEnterMillis = 480
const val ExpandExitMillis = 280
const val RouteSlideMillis = 340
const val RouteParallaxDivisor = 4f

/**
 * The viewer's own growth out of a thumbnail and back into it. Both ways run at the same rate:
 * a shorter way back read as the picture snapping shut instead of folding away.
 */
const val ImageZoomMillis = 440

/**
 * How far the route being shown is into its own entrance, for a page that draws that entrance
 * itself instead of being moved by the host. 0 is "not there yet", 1 is settled. Meant for layer
 * and draw blocks, so a running transition never recomposes the page.
 */
@Stable
class RouteTransition(
    val progress: () -> Float,
    val exiting: () -> Boolean,
    val offset: () -> Offset = { Offset.Zero },
    val onHasReturnTarget: (Boolean) -> Unit = {},
)

val LocalRouteTransition = staticCompositionLocalOf<RouteTransition?> { null }

/**
 * The bounds of the card a detail page grows from. Captured by [expandOrigin] on the
 * feed card and handed to [NavState.push] so the detail page can animate from the card
 * rect instead of sliding in from the right.
 */
@Stable
class ExpandOrigin(
    val layer: GraphicsLayer,
    val cornerRadius: Dp = 24.dp,
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

    /** The floating bottom bar over [listState]; its top edge is the lower line the card clears. */
    var bottomBar: BottomBarBounds? = null

    /** The card's key in [listState], used to check the card is still on screen before moving it. */
    var rowKey: Any? = null

    /**
     * Distinct identity for hiding the active origin during expand/return transitions.
     * Kept separate from [rowKey] because a retweet card and its nested quoted block share the
     * same LazyColumn [rowKey] but must never hide each other.
     */
    var originKey: Any? = null

    /** How far past the top bar's own edge its material keeps drawing, in pixels. */
    var clearancePx: Float = 0f

    /** Extra gap kept above [bottomBar]'s top edge, in pixels. */
    var bottomClearancePx: Float = 0f
}

@Composable
fun rememberExpandOrigin(cornerRadius: Dp = 24.dp): ExpandOrigin {
    val layer = rememberGraphicsLayer()
    val density = LocalDensity.current
    val clearance = with(density) { AppBarBlurOverlayDefaults.ExtraHeight.toPx() }
    val bottomClearance = with(density) { 8.dp.toPx() }
    return remember(layer, cornerRadius, clearance, bottomClearance) {
        ExpandOrigin(layer, cornerRadius).apply {
            clearancePx = clearance
            bottomClearancePx = bottomClearance
        }
    }
}

@Stable
internal class ExpandOriginVisibility(
    private val stack: List<NavEntry>,
    private val paneEntry: () -> NavEntry? = { null },
) {
    fun isHidden(origin: ExpandOrigin, currentEntry: NavEntry?): Boolean {
        val startIndex = if (currentEntry != null) {
            val idx = stack.indexOfFirst { it === currentEntry }
            if (idx >= 0) idx + 1 else 1
        } else {
            1
        }
        val pane = paneEntry()
        for (i in stack.lastIndex downTo startIndex) {
            val entry = stack[i]
            // A page in the right pane sits beside the list, not over it: the card it was opened
            // from stays visible, so its snapshot must keep drawing.
            if (entry === pane) continue
            if (!entry.expand) continue
            if (matches(entry.origin, origin)) return true
        }
        return false
    }

    private fun matches(active: ExpandOrigin?, candidate: ExpandOrigin): Boolean {
        if (active == null) return false
        if (active === candidate) return true
        val key = active.originKey
        return key != null && key == candidate.originKey
    }
}

internal val LocalExpandOriginVisibility = staticCompositionLocalOf<ExpandOriginVisibility?> { null }
internal val LocalCurrentNavEntry = staticCompositionLocalOf<NavEntry?> { null }

@Composable
internal fun rememberIsTopRoute(nav: NavState): Boolean {
    val current = LocalCurrentNavEntry.current ?: return false
    return nav.stack.lastOrNull { !it.exiting } === current
}

@Composable
fun Modifier.expandOrigin(origin: ExpandOrigin): Modifier {
    val visibility = LocalExpandOriginVisibility.current
    val currentEntry = LocalCurrentNavEntry.current
    // The full rect, not the part left visible by the list viewport: a card scrolled half off
    // the screen would otherwise hand the page a shorter frame than the card it grows from.
    return onGloballyPositioned {
        origin.bounds = Rect(
            it.positionInRoot(),
            Size(it.size.width.toFloat(), it.size.height.toFloat()),
        )
    }
        .drawWithContent {
            // Reading the key makes a refresh request invalidate this draw, which re-records
            // the snapshot with the card as it looks now. Even when hidden from the list below,
            // keep recording so the expanding/shrinking frame holds an up-to-date card snapshot.
            origin.refreshKey
            origin.layer.record { this@drawWithContent.drawContent() }
            if (visibility?.isHidden(origin, currentEntry) != true) {
                drawLayer(origin.layer)
            }
        }
}

sealed interface Route {
    data object Main : Route
    /** [relogin] 是要重新登录的那个已保存账号的名字，用来提示该登哪个号。 */
    data class Login(val addAccount: Boolean = false, val fresh: Boolean = false, val relogin: String? = null) : Route
    /** 手机号 + 短信验证码登录（Share 方案）：拿到 gsid 后可长期自动换取新 Cookie。 */
    data class SmsLogin(val addAccount: Boolean = false, val relogin: String? = null) : Route
    data class Detail(val id: String, val mid: String, val readOnly: Boolean = false) : Route
    data class EditHistory(val mid: String) : Route
    data class Profile(val uid: String?) : Route
    data class Relations(val uid: String, val kind: RelationKind) : Route
    data class Publish(val draft: String = "") : Route
    data class EditStatus(val id: String, val mid: String = "") : Route
    data class Chat(val uid: String = "", val name: String, val gid: String = "") : Route
    data class Notifications(val initialTab: NotificationTab = NotificationTab.AtWeibo) : Route
    data class Web(val url: String, val title: String) : Route
    data class Container(val cid: String, val title: String) : Route
    data class SuperTopic(val containerId: String, val fallbackUrl: String) : Route
    data class TopicFeed(val topic: String) : Route
    data object Drafts : Route
    data object History : Route
    data object Accounts : Route
    data object Favorites : Route
    data object MyLikes : Route
    data object Settings : Route
    data class SettingsDetail(val group: SettingsGroup) : Route
    data object PredictiveBackSettings : Route
    data object About : Route
    data object Changelog : Route
    data object Nearby : Route
    data class Topic(val name: String = "") : Route
    data class Images(
        val pics: List<Pic>,
        val index: Int,
        val editableAvatar: Boolean = false,
    ) : Route
    data class Video(
        val url: String,
        val title: String,
        val isLive: Boolean = false,
        val fid: String = "",
        val statusId: String = "",
    ) : Route
    data class Vote(val voteId: String) : Route
    /** 临时对比页，验完删。 */
    data object TabLab : Route
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
    val offset = Animatable(Offset.Zero, Offset.VectorConverter)
    var exiting by mutableStateOf(false)

    /** True while a back gesture is pulling this entry away, before it is actually popped. */
    var previewing by mutableStateOf(false)

    /** Bumped on each predictive-back attempt so an interrupted cancel ease-back won't clear a newer gesture. */
    var previewGeneration = 0

    /** Whether an image viewer entry has a thumbnail origin to return into when flinging back. */
    var hasReturnTarget = true

    /** True while predictive back is running with follow-finger trajectory enabled. */
    var followFlingActive by mutableStateOf(false)

    val expand: Boolean get() = route is Route.Detail && origin?.bounds != null

    /**
     * The viewer grows out of the thumbnail that was tapped rather than sliding in from the right.
     * It draws that growth itself, so it needs the entry's own progress (see [LocalRouteTransition])
     * and the host must leave the page where it is.
     */
    val growsFromImage: Boolean get() = route is Route.Images

    private companion object {
        var nextId = 0
    }
}

@Stable
class NavState {
    val stack = mutableStateListOf<NavEntry>(
        NavEntry(
            when {
                App.startRoute == "tablab" -> Route.TabLab
                App.startRoute == "main" -> Route.Main
                App.repo.hasSession() || App.settings.localMode -> Route.Main
                else -> Route.SmsLogin()
            }
        )
    )

    private var host: CoroutineScope? = null

    var mainTab by mutableIntStateOf(App.settings.mainTab)
        private set

    /** Bumped on every stack change, including a pane page being swapped for another. */
    var navigationVersion by mutableIntStateOf(0)
        private set

    /**
     * True while the window is wide enough to read a page beside the list. The host sets this from
     * the window configuration, so [push] knows whether a page takes the right pane instead of
     * covering the whole window.
     */
    var wide by mutableStateOf(false)

    /**
     * The detail page drawn beside the list. Only a detail opened straight from the main screen
     * takes the pane; a deeper page still covers the window, so the list on the left is always the
     * page the detail was opened from.
     */
    val paneEntry: NavEntry?
        get() {
            if (!wide || stack.size != 2) return null
            if (stack[0].route !is Route.Main) return null
            return stack[1].takeIf { it.route is Route.Detail }
        }

    val current: Route get() = stack.last().route

    fun attach(scope: CoroutineScope) {
        host = scope
    }

    fun setTab(index: Int) {
        mainTab = index
        App.settings.applyMainTab(index)
    }

    fun push(route: Route, origin: ExpandOrigin? = null) {
        navigationVersion++
        // Tapping another post while a detail sits in the pane swaps the pane's page. Stacking it
        // would cover the split with a third full-screen page the user never asked for.
        val pane = paneEntry
        if (pane != null && route is Route.Detail) {
            pane.exiting = true
            stack.remove(pane)
            stack.add(NavEntry(route, initialProgress = 1f, initialFade = 1f))
            return
        }
        // A page hosted in the pane is already on screen beside the list, so it arrives settled
        // instead of sliding in or growing out of its card over the list.
        val opensInPane = wide && route is Route.Detail && stack.size == 1 &&
            stack[0].route is Route.Main
        val entry = NavEntry(
            route,
            // The pane has no frame to grow from or return into; the card stays in the list.
            if (opensInPane) null else origin,
            initialProgress = if (opensInPane) 1f else 0f,
            initialFade = if (opensInPane) 1f else 0f,
        )
        stack.add(entry)
        if (opensInPane) return
        val scope = host ?: return
        scope.launch {
            withFrameNanos { }
            val expand = entry.expand
            if (!expand) entry.fade.snapTo(1f)
            val speed = App.settings.animationSpeed
            val millis = speed.scaleDuration(
                when {
                    expand -> ExpandEnterMillis
                    entry.growsFromImage -> ImageZoomMillis
                    else -> RouteSlideMillis
                },
            )
            coroutineScope {
                launch {
                    entry.progress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(millis, easing = ExpandEasing),
                    )
                }
                if (expand) {
                    launch {
                        entry.fade.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(millis, easing = LinearEasing),
                        )
                    }
                }
            }
            // The opening page covers the list by now, so moving the row out from under the bar
            // cannot be seen, and the row is already clear when the page shrinks back into it.
            alignCardBelowBar(entry.origin)
        }
    }

    /** Closes a page hosted in the right pane. It left the window without covering anything, so
     * there is no full-page exit to play. */
    fun dismissPane() {
        val entry = paneEntry ?: return
        if (entry.exiting) return
        entry.exiting = true
        stack.remove(entry)
        navigationVersion++
    }

    fun pop(initialVelocity: Offset = Offset.Zero) {
        if (stack.size <= 1) return
        val entry = stack.last()
        if (entry.exiting) return
        navigationVersion++
        // A pane page never covered the list, so it hands the window back at once instead of
        // shrinking into a card the user can still see.
        if (entry === paneEntry) {
            entry.exiting = true
            stack.remove(entry)
            return
        }
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
            val baseMillis = App.settings.animationSpeed.scaleDuration(
                when {
                    expand -> ExpandExitMillis
                    entry.growsFromImage -> ImageZoomMillis
                    else -> RouteSlideMillis
                },
            )
            val remaining = entry.progress.value.coerceIn(0f, 1f)
            val hasFlingOffset = entry.offset.value != Offset.Zero || initialVelocity != Offset.Zero
            val baseRemaining = (baseMillis * remaining).roundToInt().coerceAtLeast(1)
            val millis = when {
                expand && hasFlingOffset -> maxOf(baseRemaining, PredictiveBackMotionDefaults.MIN_CARD_FLING_EXIT_MILLIS)
                entry.growsFromImage && hasFlingOffset -> maxOf(baseRemaining, PredictiveBackMotionDefaults.MIN_IMAGE_FLING_EXIT_MILLIS)
                else -> baseRemaining
            }
            coroutineScope {
                launch {
                    entry.progress.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(
                            millis,
                            easing = if (expand) LinearEasing else ExpandEasing,
                        ),
                    )
                }
                if (expand) {
                    launch {
                        entry.fade.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(millis, easing = LinearEasing),
                        )
                    }
                }
                if (hasFlingOffset) {
                    launch {
                        if (entry.growsFromImage && !entry.hasReturnTarget) {
                            entry.offset.animateTo(
                                targetValue = entry.offset.value + initialVelocity * 0.14f,
                                animationSpec = PredictiveBackMotionDefaults.flingDecaySpec(millis),
                            )
                        } else {
                            entry.offset.animateTo(
                                targetValue = Offset.Zero,
                                animationSpec = PredictiveBackMotionDefaults.flingReturnSpringSpec(),
                                initialVelocity = initialVelocity,
                            )
                        }
                    }
                }
            }
            stack.remove(entry)
        }
    }

    fun reset(route: Route) {
        stack.clear()
        stack.add(NavEntry(route))
        navigationVersion++
    }

    /** Removes an entry whose progress was already driven to zero by a back gesture. */
    fun completePredictivePop(entry: NavEntry) {
        if (stack.lastOrNull() === entry) {
            entry.exiting = true
            stack.remove(entry)
            navigationVersion++
        }
    }

    /** Removes the top entry immediately after a self-driven exit animation (e.g. drag-to-dismiss). */
    fun popImmediate() {
        if (stack.size <= 1) return
        val entry = stack.last()
        if (entry.exiting) return
        entry.exiting = true
        stack.remove(entry)
        navigationVersion++
    }
}

/**
 * Points [origin] at the row it is drawn in, so a page returning into it can move that row clear of
 * the bar. A whole card is composed knowing its row; a quoted block inside a card lives in the same
 * row as that card, so it borrows the card's row when it is tapped.
 */
fun ExpandOrigin.attachToRow(
    listState: LazyListState?,
    rowKey: Any?,
    topBar: TopBarBounds?,
    bottomBar: BottomBarBounds? = null,
) {
    this.listState = listState
    this.rowKey = rowKey
    this.topBar = topBar
    this.bottomBar = bottomBar
}

/**
 * Scrolls the list that owns [origin] just far enough to bring the card clear of both the top bar
 * and any floating bottom bar.
 *
 * The frame a detail page grows from and shrinks back into is drawn on the page's own layer, above
 * the bars the covered list keeps underneath it, so a card sitting half under the top or bottom bar
 * comes back drawn over that bar. Moving the row instead of restacking the frame leaves both bars
 * where they are: the card ends up clear of them and nothing has to be raised or reordered.
 *
 * Scrolling the list can bring the top bar's collapsing title row back, which moves the line the
 * top bar ends at, so the move is measured again until the bars and card agree.
 */
internal suspend fun alignCardBelowBar(origin: ExpandOrigin?) {
    if (origin == null) return
    val state = origin.listState ?: return
    val topBar = origin.topBar
    val bottomBar = origin.bottomBar
    if (topBar == null && bottomBar == null) return
    repeat(4) {
        val topLine = topBar?.bottom?.takeIf { it > 0f }?.let { it + origin.clearancePx } ?: 0f
        val bottomLine = bottomBar?.top?.takeIf { it > 0f }?.let { it - origin.bottomClearancePx } ?: 0f
        if (topLine <= 0f && bottomLine <= 0f) return
        // The row may have been dropped by a refresh while the page was open; a stale rect would
        // scroll the list to nowhere.
        val onScreen = origin.rowKey?.let { key ->
            state.layoutInfo.visibleItemsInfo.any { it.key == key }
        } ?: false
        if (!onScreen) return
        val rect = origin.bounds ?: return
        val scrollDelta = when {
            topLine > 0f && rect.top < topLine - 0.5f -> -(topLine - rect.top)
            bottomLine > 0f && rect.bottom > bottomLine + 0.5f -> {
                val need = rect.bottom - bottomLine
                // Never push the card's top edge back under the top bar when lifting it clear of the bottom bar.
                val maxLift = if (topLine > 0f) (rect.top - topLine).coerceAtLeast(0f) else need
                minOf(need, maxLift)
            }
            else -> 0f
        }
        if (kotlin.math.abs(scrollDelta) <= 0.5f) return
        if (state.scrollBy(scrollDelta) == 0f) return
        // One frame for the list to lay out again, one for the bar to report its new edge.
        withFrameNanos { }
        withFrameNanos { }
    }
}
