package xyz.sattar.javid.proqueue.feature.explore

import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Where the results sheet rests: low for a near-full map, the middle split, or all list. */
internal enum class SheetStop { Collapsed, Half, Expanded }

/**
 * A results sheet with three stops, like a maps app. Material's sheet only has
 * two, so the low "show me the map" stop wasn't reachable.
 */
@Stable
internal class ExploreSheetState {
    val drag = AnchoredDraggableState(SheetStop.Half)

    /** Distance of the sheet's top edge from the top of the area, in px (NaN before layout). */
    val offset: Float get() = drag.offset

    val settled: SheetStop get() = drag.settledValue

    suspend fun moveTo(stop: SheetStop) {
        if (drag.anchors.hasPositionFor(stop)) drag.animateTo(stop, SheetSpring)
    }
}

private val SheetSpring = spring<Float>(dampingRatio = 0.9f, stiffness = 500f)

@Composable
internal fun rememberExploreSheetState() = remember { ExploreSheetState() }

/**
 * [body] fills the area; the sheet slides over it. [collapsedHeight] and
 * [halfHeight] are how much of the sheet shows at those stops; expanded fills
 * the area. The list inside drags the sheet first and scrolls only once the
 * sheet is at the top, the same hand-off Material's sheet does.
 */
@Composable
internal fun ExploreSheetLayout(
    state: ExploreSheetState,
    collapsedHeight: Dp,
    halfHeight: Dp,
    shape: Shape,
    color: Color,
    shadowElevation: Dp,
    modifier: Modifier = Modifier,
    handle: @Composable () -> Unit,
    sheetContent: @Composable () -> Unit,
    body: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val full = constraints.maxHeight.toFloat()
        val collapsedPx = with(density) { collapsedHeight.toPx() }
        val halfPx = with(density) { halfHeight.toPx() }
        SideEffect {
            state.drag.updateAnchors(
                DraggableAnchors {
                    SheetStop.Expanded at 0f
                    SheetStop.Half at (full - halfPx).coerceAtLeast(0f)
                    SheetStop.Collapsed at (full - collapsedPx).coerceAtLeast(0f)
                }
            )
        }

        body()

        val fling = AnchoredDraggableDefaults.flingBehavior(
            state = state.drag,
            positionalThreshold = { distance -> distance * 0.4f },
            animationSpec = SheetSpring,
        )
        val nested = remember(state) { SheetNestedScroll(state.drag) }
        Surface(
            shape = shape,
            color = color,
            shadowElevation = shadowElevation,
            modifier = Modifier
                .fillMaxSize()
                .offset {
                    val y = state.offset.takeUnless { it.isNaN() } ?: (full - halfPx)
                    IntOffset(0, y.roundToInt())
                }
                .nestedScroll(nested)
                .anchoredDraggable(state.drag, Orientation.Vertical, flingBehavior = fling)
        ) {
            Column(Modifier.fillMaxWidth()) {
                handle()
                Box(Modifier.weight(1f)) { sheetContent() }
            }
        }
    }
}

/** Upward drags lift the sheet before the list scrolls; downward ones scroll the list back first. */
private class SheetNestedScroll(private val drag: AnchoredDraggableState<SheetStop>) : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val delta = available.y
        return if (delta < 0 && source == NestedScrollSource.UserInput) {
            Offset(0f, drag.dispatchRawDelta(delta))
        } else Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        if (source == NestedScrollSource.UserInput) Offset(0f, drag.dispatchRawDelta(available.y)) else Offset.Zero

    override suspend fun onPreFling(available: Velocity): Velocity {
        val top = drag.anchors.minPosition()
        return if (available.y < 0 && drag.requireOffset() > top) {
            settle(available.y)
            available
        } else Velocity.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        settle(available.y)
        return available
    }

    /**
     * A flick goes to the next stop in its direction, a slow release to the
     * nearest one. (The state's own velocity `settle` refuses a state built
     * without thresholds, which is how the drag modifier wants it built.)
     */
    private suspend fun settle(velocity: Float) {
        val offset = drag.offset.takeUnless { it.isNaN() } ?: return
        val anchors = drag.anchors
        val target = when {
            velocity < -FLICK_VELOCITY -> anchors.closestAnchor(offset, searchUpwards = false)
            velocity > FLICK_VELOCITY -> anchors.closestAnchor(offset, searchUpwards = true)
            else -> anchors.closestAnchor(offset)
        } ?: return
        drag.animateTo(target, SheetSpring)
    }
}

/** px/s past which a release counts as a flick rather than a drop. */
private const val FLICK_VELOCITY = 600f

/** How much list shows under the handle at the low stop: the results title and its count, nothing more. */
internal val COLLAPSED_LIST_PEEK = 52.dp
