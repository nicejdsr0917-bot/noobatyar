package xyz.sattar.javid.proqueue.core.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import xyz.sattar.javid.proqueue.core.ui.LocalWindowSize
import xyz.sattar.javid.proqueue.core.ui.WindowSize

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NumberPicker(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    range: IntRange,
    initialValue: Int,
    onValueChange: (Int) -> Unit,
    itemHeight: Dp = 48.dp,
    visibleItems: Int = 3, // Must be an odd number for centering
    itemFormatter: (Int) -> String = { it.toString() }
) {
    val count = range.last - range.first + 1
    val centerOffset = visibleItems / 2
    
    // Scroll to initial value
    LaunchedEffect(Unit) {
        val index = initialValue - range.first
        if (index in 0 until count) {
            state.scrollToItem(index)
        }
    }

    // Detect changes
    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index ->
                val value = range.first + index
                onValueChange(value)
            }
    }

    // A spin wheel is a touch gesture. On a desktop panel there is nothing to
    // drag with, and a mouse wheel over a snapping LazyColumn inside a dialog
    // is not a reliable way through either — so pointer sizes get explicit
    // step buttons above and below. Compact is untouched: the drag is the
    // right interaction there, and two extra buttons would only eat height.
    val scope = rememberCoroutineScope()
    val showSteppers = LocalWindowSize.current != WindowSize.Compact

    fun stepBy(delta: Int) {
        val target = (state.firstVisibleItemIndex + delta).coerceIn(0, count - 1)
        scope.launch { state.animateScrollToItem(target) }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
    if (showSteppers) {
        IconButton(onClick = { stepBy(-1) }, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowUp,
                contentDescription = "قبلی",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }

    Box(
        modifier = Modifier.fillMaxWidth().height(itemHeight * visibleItems),
        contentAlignment = Alignment.Center
    ) {
        LazyColumn(
            state = state,
            flingBehavior = rememberSnapFlingBehavior(lazyListState = state),
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(vertical = itemHeight * centerOffset)
        ) {
            items(count) { index ->
                val value = range.first + index
                Box(
                    modifier = Modifier
                        .height(itemHeight)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = itemFormatter(value),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        
        // Dividers
        Column(Modifier.fillMaxWidth()) {
             Box(Modifier.weight(1f))
             HorizontalDivider(
                 modifier = Modifier.fillMaxWidth(),
                 color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                 thickness = 1.dp
             )
             Box(Modifier.height(itemHeight))
             HorizontalDivider(
                 modifier = Modifier.fillMaxWidth(),
                 color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                 thickness = 1.dp
             )
             Box(Modifier.weight(1f))
        }
    }

    if (showSteppers) {
        IconButton(onClick = { stepBy(1) }, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = "بعدی",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
    }
}
