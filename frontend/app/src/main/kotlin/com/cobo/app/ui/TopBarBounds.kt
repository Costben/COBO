package com.cobo.app.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot

/**
 * The bottom edge of the bar floating over a list, in root coordinates.
 *
 * The feed header is two rows and folds its title row away as the list scrolls, so the line it ends
 * at moves while the list moves. Reporting the edge from the bar's own layout keeps that in step:
 * a card that has to be placed clear of the bar reads where the bar actually ends instead of
 * guessing from the inset the list was built with.
 */
@Stable
class TopBarBounds {
    var bottom by mutableFloatStateOf(0f)
        private set

    fun report(top: Float, height: Float) {
        bottom = top + height
    }
}

/**
 * The top edge of a floating bottom bar over a list, in root coordinates.
 *
 * Mirrors [TopBarBounds] for the bottom floating bar so cards returning from detail and
 * bottom-anchored overlays (like COUI [io.github.suqi8.coui.kmp.basic.SnackbarHost]) can stay
 * clear of the bar without hardcoding its height.
 */
@Stable
class BottomBarBounds {
    var top by mutableFloatStateOf(0f)
        private set

    var height by mutableFloatStateOf(0f)
        private set

    fun report(topY: Float, barHeight: Float) {
        top = topY
        height = barHeight
    }

    fun clear() {
        top = 0f
        height = 0f
    }
}

/** The bar over the page being composed, or null on a page whose bar does not float over its list. */
val LocalTopBarBounds = staticCompositionLocalOf<TopBarBounds?> { null }

/** The floating bottom bar over the page being composed, or null when the page has none. */
val LocalBottomBarBounds = staticCompositionLocalOf<BottomBarBounds?> { null }

/** Keeps [bounds] at this bar's bottom edge for as long as the bar is laid out. */
fun Modifier.reportTopBarBottom(bounds: TopBarBounds): Modifier =
    onGloballyPositioned {
        bounds.report(it.positionInRoot().y, it.size.height.toFloat())
    }

/** Keeps [bounds] at this floating bottom bar's top edge while active; pass [active]=false when covered. */
fun Modifier.reportBottomBarTop(bounds: BottomBarBounds, active: Boolean = true): Modifier =
    onGloballyPositioned {
        if (!active || it.size.height <= 0) {
            bounds.clear()
        } else {
            bounds.report(it.positionInRoot().y, it.size.height.toFloat())
        }
    }
