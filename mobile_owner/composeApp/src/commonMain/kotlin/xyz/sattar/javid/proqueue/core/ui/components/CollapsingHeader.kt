package xyz.sattar.javid.proqueue.core.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import kotlin.math.roundToInt

/**
 * State for a header that slides away while the list below it scrolls, and
 * slides back as soon as the list is scrolled the other way ("enter always").
 *
 * Hook [connection] onto a common ancestor of the header and the list with
 * `Modifier.nestedScroll`, and wrap the header in [CollapsingHeader].
 *
 * Unlike a hide/show toggle, the header tracks the finger pixel for pixel, so
 * the list never jumps; when the gesture ends half-way it settles to fully
 * shown or fully hidden, whichever is nearer.
 */
@Stable
class CollapsingHeaderState {
    /** Full height of the header content, in px. Measured by [CollapsingHeader]. */
    var maxHeight by mutableFloatStateOf(0f)
        internal set

    /** 0 when fully shown, down to -[maxHeight] when fully hidden. */
    var offset by mutableFloatStateOf(0f)
        private set

    /** 0 = fully shown, 1 = fully hidden. */
    val collapsedFraction: Float
        get() = if (maxHeight <= 0f) 0f else -offset / maxHeight

    /** Brings the header fully back, e.g. when the content under it changes. */
    fun expand() {
        offset = 0f
    }

    internal fun onMeasured(height: Float) {
        maxHeight = height
        // The header can shrink while hidden (a panel inside it closing), so
        // keep the offset inside the new range.
        offset = offset.coerceIn(-height, 0f)
    }

    val connection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            // Taken before the list sees it, in both directions: scrolling
            // down the list hides the header first, scrolling back up
            // reveals it first — wherever the list happens to be.
            val before = offset
            offset = (offset + available.y).coerceIn(-maxHeight, 0f)
            return Offset(0f, offset - before)
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            if (offset != 0f && offset != -maxHeight) {
                val target = if (collapsedFraction > 0.5f) -maxHeight else 0f
                animate(offset, target, animationSpec = spring(stiffness = 600f)) { value, _ ->
                    offset = value
                }
            }
            return Velocity.Zero
        }
    }
}

@Composable
fun rememberCollapsingHeaderState(): CollapsingHeaderState = remember { CollapsingHeaderState() }

/**
 * Lays [content] out at its natural height but reports only the part still on
 * screen, shifted up by the state's offset, so whatever sits below it in a
 * Column rises to fill the space it gives up.
 */
@Composable
fun CollapsingHeader(
    state: CollapsingHeaderState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clipToBounds()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(
                    constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
                )
                state.onMeasured(placeable.height.toFloat())
                val offset = state.offset.roundToInt()
                val visible = (placeable.height + offset).coerceAtLeast(0)
                layout(placeable.width, visible) {
                    placeable.place(0, offset)
                }
            }
    ) {
        content()
    }
}
