package xyz.sattar.javid.proqueue.feature.profile

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.UserRound
import xyz.sattar.javid.proqueue.feature.booking.LocalReducedMotion
import xyz.sattar.javid.proqueue.ui.theme.AccentSoft
import xyz.sattar.javid.proqueue.ui.theme.AccentSoftDark
import xyz.sattar.javid.proqueue.ui.theme.LocalIsDarkTheme

/** Brand ring, light violet through deep violet to the logo's amber dot, Instagram-style. */
private val RingColors = listOf(
    Color(0xFFA78BFA), Color(0xFF7C3AED), Color(0xFFFBBF24), Color(0xFFA78BFA),
)

/**
 * The visitor's photo in a circle, or a person outline on the soft accent
 * when there is none (no initials). Used at 26 dp in the tab bar and large
 * in the profile header.
 */
@Composable
fun ProfileAvatar(url: String?, size: Dp, modifier: Modifier = Modifier) {
    val soft = if (LocalIsDarkTheme.current) AccentSoftDark else AccentSoft
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(soft),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = "عکس پروفایل", contentScale = ContentScale.Crop, modifier = Modifier.size(size))
        } else {
            Icon(Lucide.UserRound, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(size * 0.5f))
        }
    }
}

/**
 * [ProfileAvatar] inside the brand-gradient ring, which draws itself in once
 * when the header first appears.
 */
@Composable
fun RingedAvatar(url: String?, size: Dp, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    val sweep = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) { sweep.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    val ring = 3.dp
    Box(
        modifier
            .size(size)
            .drawBehind {
                val w = ring.toPx()
                drawArc(
                    brush = Brush.sweepGradient(RingColors, center),
                    startAngle = -90f,
                    sweepAngle = 360f * sweep.value,
                    useCenter = false,
                    topLeft = Offset(w / 2, w / 2),
                    size = Size(this.size.width - w, this.size.height - w),
                    style = Stroke(width = w, cap = StrokeCap.Round),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        ProfileAvatar(
            url,
            size - ring * 2 - 6.dp,
            Modifier.border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
        )
    }
}

/** The tab bar's icon: the avatar, ringed in the accent while its tab is open. */
@Composable
fun ProfileTabIcon(url: String?, selected: Boolean, modifier: Modifier = Modifier) {
    val ringColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    Box(
        modifier
            .size(28.dp)
            .border(2.dp, ringColor, CircleShape)
            .padding(3.dp),
        contentAlignment = Alignment.Center,
    ) {
        ProfileAvatar(url, 22.dp)
    }
}
