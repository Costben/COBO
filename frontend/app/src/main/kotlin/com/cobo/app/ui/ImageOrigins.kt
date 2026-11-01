package com.cobo.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A thumbnail as the viewer sees it: where it sits in the viewer's own coordinates, how round its
 * corners are, the picture's true aspect ratio when the feed knows it, and the address the feed
 * itself drew — so the viewer can put that same already-decoded image on screen for the first
 * frame instead of an empty frame.
 */
internal class ThumbBounds(
    val rect: Rect,
    val cornerRadius: Float,
    val aspect: Float?,
    val model: String?,
)

internal class ThumbSlot {
    var coordinates: LayoutCoordinates? = null
    var cornerRadius: Dp = 0.dp
    var aspect: Float? = null
    var model: String? = null
}

/** 缩略图左下角实况标记的直径。 */
internal val LiveBadgeSize = 17.dp

/**
 * Where the thumbnails on screen sit, by picture URL, so the viewer can grow out of the one that
 * was tapped and shrink back into the one it ends on. Keyed by URL rather than by route argument
 * because a feed can show the same picture in several places and the viewer only knows its URLs.
 */
@Stable
class ImageOrigins {
    private val slots = HashMap<String, ThumbSlot>()

    /**
     * 看图页此刻正在显示的那张图，没有打开看图页时为空。
     *
     * 看图页是一整块照片落在缩略图上的，退场一路盖着那一格，直到最后一帧才整层撤掉，所以画在
     * 它上面的角标得等这一页走了再淡回来。只有这一张会这样，别的缩略图不受影响。
     */
    var viewing by mutableStateOf<String?>(null)

    internal fun put(url: String, slot: ThumbSlot) {
        slots[url] = slot
    }

    internal fun remove(url: String, slot: ThumbSlot) {
        if (slots[url] === slot) slots.remove(url)
    }

    /** The address the feed drew for [url], so the viewer can reuse that decoded image. */
    internal fun modelOf(url: String): String? = slots[url]?.model

    /** Read live, so a closing viewer shrinks into where the thumbnail is now. */
    internal fun boundsOf(url: String, host: LayoutCoordinates, density: Float): ThumbBounds? {
        val slot = slots[url] ?: return null
        val coordinates = slot.coordinates?.takeIf { it.isAttached } ?: return null
        if (!host.isAttached) return null
        val rect = host.localBoundingBoxOf(coordinates, clipBounds = false)
        if (rect.width <= 0f || rect.height <= 0f) return null
        // A circle asks for more radius than fits; the shorter side's half is as round as it gets.
        val radius = (slot.cornerRadius.value * density)
            .coerceAtMost(minOf(rect.width, rect.height) / 2f)
        return ThumbBounds(rect, radius, slot.aspect, slot.model)
    }
}

val LocalImageOrigins = staticCompositionLocalOf { ImageOrigins() }

/**
 * Marks this element as the thumbnail of [url]: tapping it opens the viewer, which grows out of it.
 *
 * [aspect] is the picture's own width over height when the feed already knows it — the viewer needs
 * it on the very first frame to reproduce the thumbnail's centre crop, before the full picture has
 * decoded. [model] is whatever the element actually draws, so the viewer can show that image while
 * the full one loads.
 */
@Composable
fun Modifier.imageOrigin(
    url: String,
    cornerRadius: Dp,
    aspect: Float? = null,
    model: String? = null,
): Modifier {
    val origins = LocalImageOrigins.current
    val slot = remember(url) { ThumbSlot() }
    SideEffect {
        slot.cornerRadius = cornerRadius
        slot.aspect = aspect
        slot.model = model
    }
    DisposableEffect(origins, url, slot) { onDispose { origins.remove(url, slot) } }
    return onPlaced {
        slot.coordinates = it
        origins.put(url, slot)
    }
}
