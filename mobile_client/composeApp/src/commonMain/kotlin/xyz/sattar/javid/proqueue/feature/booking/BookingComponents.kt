package xyz.sattar.javid.proqueue.feature.booking

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import xyz.sattar.javid.proqueue.core.ui.components.shimmer
import xyz.sattar.javid.proqueue.core.utils.DateTimeUtils
import xyz.sattar.javid.proqueue.feature.discovery.toPersianDigits
import xyz.sattar.javid.proqueue.data.remoteDataSource.booking.model.TimeSlotDto
import xyz.sattar.javid.proqueue.feature.discovery.Radius
import xyz.sattar.javid.proqueue.feature.discovery.Space

// ── Step indicator ──

private val DotSize = 24.dp

/**
 * Numbered dots joined by a track that fills toward the current stage. Done
 * stages turn into a check; the current one carries a soft halo.
 */
@Composable
internal fun StepIndicator(phases: List<BookingPhase>, current: Int, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    Row(
        modifier.fillMaxWidth().padding(horizontal = Space.page, vertical = Space.md),
        verticalAlignment = Alignment.Top,
    ) {
        phases.forEachIndexed { i, phase ->
            StepDot(index = i, label = phase.label, done = i < current, active = i == current)
            if (i < phases.lastIndex) {
                val fill by animateFloatAsState(
                    if (i < current) 1f else 0f,
                    if (reduced) tween(0) else tween(420, easing = FastOutSlowInEasing),
                    label = "stepTrack",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .padding(top = DotSize / 2 - 1.dp, start = Space.xs, end = Space.xs)
                        .height(2.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                ) {
                    // fillMaxWidth starts at the layout's start edge, so it grows
                    // right-to-left in RTL: the direction the visitor reads.
                    Box(Modifier.fillMaxHeight().fillMaxWidth(fill).background(MaterialTheme.colorScheme.primary))
                }
            }
        }
    }
}

@Composable
private fun StepDot(index: Int, label: String, done: Boolean, active: Boolean) {
    val reduced = LocalReducedMotion.current
    val primary = MaterialTheme.colorScheme.primary
    val filled = done || active
    val bg by animateColorAsState(if (filled) primary else MaterialTheme.colorScheme.surface, label = "dotBg")
    val halo by animateFloatAsState(
        if (active) 1f else 0f,
        if (reduced) tween(0) else spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
        label = "dotHalo",
    )
    val labelColor by animateColorAsState(
        if (filled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "dotLabel",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.semantics {
            contentDescription = "مرحلهٔ ${(index + 1).toPersianDigits()}: $label" + if (done) "، انجام شد" else ""
            selected = active
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(DotSize)
                    .graphicsLayer { scaleX = 1f + 0.45f * halo; scaleY = 1f + 0.45f * halo; alpha = halo }
                    .background(primary.copy(alpha = 0.16f), CircleShape)
            )
            Box(
                Modifier
                    .size(DotSize)
                    .background(bg, CircleShape)
                    .then(if (filled) Modifier else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = done,
                    transitionSpec = { (scaleIn(initialScale = 0.4f) + fadeIn()) togetherWith (scaleOut(targetScale = 0.4f) + fadeOut()) },
                    label = "dotContent",
                ) { isDone ->
                    if (isDone) {
                        Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                    } else {
                        Text(
                            (index + 1).toPersianDigits(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(Space.xs))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            color = labelColor,
        )
    }
}

// ── Primary action ──

/** The pinned button: shrinks under the finger, ticks on tap, swaps to a spinner while busy. */
@Composable
internal fun PrimaryAction(
    text: String,
    enabled: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    Button(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            onClick()
        },
        enabled = enabled && !busy,
        shape = Radius.md,
        interactionSource = interaction,
        modifier = modifier.fillMaxWidth().height(52.dp).pressScale(interaction, 0.97f),
    ) {
        AnimatedContent(
            targetState = if (busy) null else text,
            transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
            label = "actionLabel",
        ) { label ->
            if (label == null) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
            } else {
                Text(label, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── Services ──

/** One service, picked by tapping the whole row; the check springs in. */
@Composable
internal fun ServiceRow(name: String, selected: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val primary = MaterialTheme.colorScheme.primary
    val borderColor by animateColorAsState(if (selected) primary else MaterialTheme.colorScheme.outlineVariant, label = "serviceBorder")
    val borderWidth by animateDpAsState(if (selected) 1.5.dp else 1.dp, label = "serviceBorderWidth")
    val tint by animateColorAsState(if (selected) primary.copy(alpha = 0.06f) else MaterialTheme.colorScheme.surface, label = "serviceTint")
    Surface(
        onClick = {
            haptic.performHapticFeedback(if (selected) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
            onToggle()
        },
        shape = Radius.md,
        color = tint,
        border = BorderStroke(borderWidth, borderColor),
        interactionSource = interaction,
        modifier = modifier.fillMaxWidth().pressScale(interaction, 0.98f).semantics { this.selected = selected },
    ) {
        Row(Modifier.padding(horizontal = Space.lg, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            CheckCircle(selected, reduced)
        }
    }
}

@Composable
private fun CheckCircle(checked: Boolean, reduced: Boolean) {
    val primary = MaterialTheme.colorScheme.primary
    val bg by animateColorAsState(if (checked) primary else Color.Transparent, label = "checkBg")
    val iconScale by animateFloatAsState(if (checked) 1f else 0f, selectSpring(reduced), label = "checkScale")
    Box(
        Modifier
            .size(22.dp)
            .background(bg, CircleShape)
            .then(if (checked) Modifier else Modifier.border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp).scale(iconScale))
    }
}

// ── Day strip ──

internal fun BookingDay.jalaliDayOfMonth(): String =
    DateTimeUtils.getJalaliDateParts(startMillis).dayOfMonth.toPersianDigits()

internal fun BookingDay.jalaliMonthName(): String =
    jalaliMonths[DateTimeUtils.getJalaliDateParts(startMillis).month - 1]

/** Two weeks of day cards; the picked one fills violet with a spring and scrolls into view. */
@Composable
internal fun DayStrip(days: List<BookingDay>, selected: Int, onSelect: (Int) -> Unit) {
    val listState = rememberLazyListState()
    val reduced = LocalReducedMotion.current
    LaunchedEffect(selected) {
        val target = (selected - 1).coerceAtLeast(0)
        if (reduced) listState.scrollToItem(target) else listState.animateScrollToItem(target)
    }
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = Space.page),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        itemsIndexed(days, key = { _, d -> d.date.toString() }) { index, day ->
            DayCard(day, index, index == selected, Modifier.enterStagger(index, stepMillis = 30)) { onSelect(index) }
        }
    }
}

@Composable
private fun DayCard(day: BookingDay, index: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val reduced = LocalReducedMotion.current
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (selected) scheme.primary else scheme.surface, selectSpring(reduced), label = "dayBg")
    val fg by animateColorAsState(if (selected) scheme.onPrimary else scheme.onSurface, label = "dayFg")
    val sub by animateColorAsState(if (selected) scheme.onPrimary.copy(alpha = 0.8f) else scheme.onSurfaceVariant, label = "daySub")
    val scale by animateFloatAsState(if (selected) 1f else 0.94f, selectSpring(reduced), label = "dayScale")
    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
            onClick()
        },
        shape = Radius.md,
        color = bg,
        border = if (selected) null else BorderStroke(1.dp, scheme.outlineVariant),
        interactionSource = interaction,
        modifier = modifier
            .width(64.dp)
            .height(84.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (day.closed && !selected) 0.55f else 1f }
            .pressScale(interaction, 0.92f)
            .semantics { this.selected = selected },
    ) {
        Column(
            Modifier.padding(vertical = Space.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(day.weekdayLabel(index), style = MaterialTheme.typography.labelSmall, color = sub, maxLines = 1)
            Text(
                day.jalaliDayOfMonth(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = fg,
                textDecoration = if (day.closed) TextDecoration.LineThrough else null,
            )
            Text(if (day.closed) "تعطیل" else day.jalaliMonthName(), style = MaterialTheme.typography.labelSmall, color = sub, maxLines = 1)
        }
    }
}

// ── Time slots ──

private const val SLOT_COLUMNS = 4

/** Free times grouped by part of the day, four to a row; cells cascade in. */
@Composable
internal fun SlotGrid(
    slots: List<TimeSlotDto>,
    selected: TimeSlotDto?,
    onSelect: (TimeSlotDto) -> Unit,
    animationKey: Any,
) {
    val groups = remember(slots) { groupByDayPart(slots) }
    var cell = 0
    Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
        groups.forEach { (part, partSlots) ->
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(part.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(Space.sm))
                    val free = partSlots.count { it.available }
                    Text(
                        if (free == 0) "پر" else "${free.toPersianDigits()} نوبت خالی",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                partSlots.chunked(SLOT_COLUMNS).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        row.forEach { slot ->
                            SlotCell(
                                slot,
                                selected = slot == selected,
                                modifier = Modifier.weight(1f).enterStagger(cell++, animationKey, stepMillis = 18),
                            ) { onSelect(slot) }
                        }
                        repeat(SLOT_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SlotCell(slot: TimeSlotDto, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val reduced = LocalReducedMotion.current
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(
        when {
            selected -> scheme.primary
            slot.available -> scheme.surface
            else -> scheme.surfaceContainerHigh
        },
        label = "slotBg",
    )
    val fg by animateColorAsState(
        when {
            selected -> scheme.onPrimary
            slot.available -> scheme.onSurface
            else -> scheme.onSurfaceVariant.copy(alpha = 0.6f)
        },
        label = "slotFg",
    )
    val pop = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected && !reduced) {
            pop.snapTo(0.88f)
            pop.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
        }
    }
    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
            onClick()
        },
        enabled = slot.available,
        shape = Radius.md,
        color = bg,
        border = if (selected || !slot.available) null else BorderStroke(1.dp, scheme.outlineVariant),
        interactionSource = interaction,
        modifier = modifier
            .height(44.dp)
            .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
            .pressScale(interaction, 0.92f)
            .semantics {
                this.selected = selected
                if (!slot.available) contentDescription = "${slot.time.toPersianDigits()}، پر شده"
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                slot.time.toPersianDigits(),
                color = fg,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                textDecoration = if (slot.available) null else TextDecoration.LineThrough,
            )
        }
    }
}

/** The shape of the grid while slots load, so the layout does not jump when they arrive. */
@Composable
internal fun SlotGridShimmer() {
    val reduced = LocalReducedMotion.current
    val placeholder = if (reduced) {
        Modifier.clip(Radius.md).background(MaterialTheme.colorScheme.surfaceContainerHigh)
    } else {
        Modifier.shimmer(Radius.md)
    }
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        Box(Modifier.width(72.dp).height(14.dp).then(placeholder))
        repeat(3) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                repeat(SLOT_COLUMNS) { Box(Modifier.weight(1f).height(44.dp).then(placeholder)) }
            }
        }
    }
}

// ── Payment ──

/** A radio-like method card; the picked one expands to show its details. */
@Composable
internal fun PaymentOptionCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    details: (@Composable () -> Unit)? = null,
) {
    val reduced = LocalReducedMotion.current
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val primary = MaterialTheme.colorScheme.primary
    val borderColor by animateColorAsState(if (selected) primary else MaterialTheme.colorScheme.outlineVariant, label = "payBorder")
    val borderWidth by animateDpAsState(if (selected) 1.5.dp else 1.dp, label = "payBorderWidth")
    Surface(
        onClick = {
            if (!selected) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
            onSelect()
        },
        shape = Radius.md,
        color = if (selected) primary.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(borderWidth, borderColor),
        interactionSource = interaction,
        modifier = modifier.fillMaxWidth().pressScale(interaction, 0.98f).semantics(mergeDescendants = false) { this.selected = selected },
    ) {
        Column(Modifier.padding(Space.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioDot(selected, reduced)
                Spacer(Modifier.width(Space.md))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (details != null) {
                AnimatedVisibility(
                    visible = selected,
                    enter = if (reduced) fadeIn(tween(0)) else expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                    exit = if (reduced) fadeOut(tween(0)) else shrinkVertically(spring(stiffness = Spring.StiffnessMedium)) + fadeOut(),
                ) {
                    Column(Modifier.padding(top = Space.md), verticalArrangement = Arrangement.spacedBy(Space.md)) { details() }
                }
            }
        }
    }
}

@Composable
private fun RadioDot(selected: Boolean, reduced: Boolean) {
    val primary = MaterialTheme.colorScheme.primary
    val ring by animateColorAsState(if (selected) primary else MaterialTheme.colorScheme.outline, label = "radioRing")
    val inner by animateFloatAsState(if (selected) 1f else 0f, selectSpring(reduced), label = "radioInner")
    Box(Modifier.size(22.dp).border(2.dp, ring, CircleShape), contentAlignment = Alignment.Center) {
        Box(Modifier.size(10.dp).scale(inner).background(primary, CircleShape))
    }
}

// ── Success ──

/**
 * The booked mark: a violet disc springs up, a ring ripples out once, then
 * the check draws itself stroke by stroke. Static under reduced motion.
 */
@Composable
internal fun AnimatedCheck(diameter: Dp = 96.dp, onSettled: () -> Unit = {}) {
    val reduced = LocalReducedMotion.current
    val disc = remember { Animatable(if (reduced) 1f else 0f) }
    val ring = remember { Animatable(if (reduced) 1f else 0f) }
    val stroke = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (reduced) { onSettled(); return@LaunchedEffect }
        launch { disc.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }
        launch {
            delay(120)
            ring.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        }
        delay(220)
        stroke.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
        onSettled()
    }
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val path = remember { Path() }
    val segment = remember { Path() }
    val measure = remember { PathMeasure() }
    Canvas(Modifier.size(diameter * 1.5f).semantics { contentDescription = "نوبت ثبت شد" }) {
        val r = diameter.toPx() / 2
        val c = center
        if (!reduced && ring.value < 1f) {
            drawCircle(
                color = primary.copy(alpha = 0.35f * (1f - ring.value)),
                radius = r * (1f + 0.45f * ring.value),
                center = c,
                style = Stroke(width = 3.dp.toPx()),
            )
        }
        drawCircle(color = primary.copy(alpha = 0.12f), radius = r * 1.18f * disc.value, center = c)
        drawCircle(color = primary, radius = r * disc.value, center = c)
        if (stroke.value > 0f) {
            path.reset()
            path.moveTo(c.x - r * 0.42f, c.y + r * 0.02f)
            path.lineTo(c.x - r * 0.12f, c.y + r * 0.32f)
            path.lineTo(c.x + r * 0.44f, c.y - r * 0.28f)
            measure.setPath(path, false)
            segment.reset()
            measure.getSegment(0f, measure.length * stroke.value, segment, true)
            drawPath(
                segment,
                color = onPrimary,
                style = Stroke(width = r * 0.14f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

/** A labelled value inside the summary card. */
@Composable
internal fun SummaryTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(Radius.md)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = Space.md, vertical = Space.md),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Space.xs))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Start)
    }
}
