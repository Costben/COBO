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

/** The bar over the page being composed, or null on a page whose bar does not float over its list. */
val LocalTopBarBounds = staticCompositionLocalOf<TopBarBounds?> { null }

/** Keeps [bounds] at this bar's bottom edge for as long as the bar is laid out. */
fun Modifier.reportTopBarBottom(bounds: TopBarBounds): Modifier =
    onGloballyPositioned {
        bounds.report(it.positionInRoot().y, it.size.height.toFloat())
    }
