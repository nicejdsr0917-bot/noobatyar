package xyz.sattar.javid.proqueue.feature.createBusiness

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import xyz.sattar.javid.proqueue.core.utils.toPersianDigits

/**
 * The business form's steps, in order.
 *
 * The form asks for fourteen different things — identity, schedule, service
 * menu, contact details — and used to ask for all of them in one unbroken
 * scroll, where nothing signalled that "ساعت پایان کار" and "آدرس" belong to
 * different decisions. Grouping them into four steps is the whole point; the
 * titles here are what the header renders, so a step's name and its content
 * cannot drift apart.
 */
enum class BusinessFormStep(val title: String) {
    IDENTITY("شناسنامه"),
    SCHEDULE("زمان‌بندی"),
    SERVICES("خدمات"),
    CONTACT("تماس");

    companion object {
        val count = entries.size
        fun at(index: Int): BusinessFormStep = entries[index.coerceIn(0, count - 1)]
    }
}

/**
 * Numbered step indicator: a circle per step joined by a rail, the current one
 * filled, the ones behind it showing a check.
 *
 * Steps already visited are tappable so a correction two steps back does not
 * mean walking forward through the whole form again. Steps *ahead* are not —
 * each step validates before it advances, and letting someone jump to the end
 * would skip that.
 */
@Composable
fun BusinessFormStepper(
    currentStep: Int,
    furthestStep: Int,
    onStepClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BusinessFormStep.entries.forEachIndexed { index, _ ->
                if (index > 0) {
                    StepRail(filled = index <= currentStep, modifier = Modifier.weight(1f))
                }
                StepDot(
                    index = index,
                    isCurrent = index == currentStep,
                    isDone = index < currentStep,
                    enabled = index <= furthestStep,
                    onClick = { onStepClick(index) },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            BusinessFormStep.entries.forEachIndexed { index, step ->
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    fontWeight = if (index == currentStep) FontWeight.Bold else FontWeight.Normal,
                    color = if (index == currentStep) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StepDot(
    index: Int,
    isCurrent: Boolean,
    isDone: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val container by animateColorAsState(
        targetValue = when {
            isCurrent || isDone -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = tween(250),
        label = "stepContainer",
    )
    val content by animateColorAsState(
        targetValue = when {
            isCurrent || isDone -> MaterialTheme.colorScheme.onPrimary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(250),
        label = "stepContent",
    )

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(container)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isDone) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(18.dp)
            )
        } else {
            Text(
                text = (index + 1).toString().toPersianDigits(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = content,
            )
        }
    }
}

@Composable
private fun StepRail(filled: Boolean, modifier: Modifier = Modifier) {
    val color by animateColorAsState(
        targetValue = if (filled) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = tween(250),
        label = "stepRail",
    )
    Box(
        modifier = modifier
            .padding(horizontal = 4.dp)
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(color)
    )
}
