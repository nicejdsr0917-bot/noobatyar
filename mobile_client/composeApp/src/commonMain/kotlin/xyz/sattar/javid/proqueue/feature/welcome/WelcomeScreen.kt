package xyz.sattar.javid.proqueue.feature.welcome

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import proqueue.composeapp.generated.resources.Res
import proqueue.composeapp.generated.resources.main_icon
import xyz.sattar.javid.proqueue.core.location.rememberLocationRequester
import xyz.sattar.javid.proqueue.feature.discovery.Radius
import xyz.sattar.javid.proqueue.feature.discovery.Space

/**
 * One-time introduction. Asks for location here, with the reason on screen,
 * rather than as a cold system dialog on the explore screen — a permission
 * asked in context is granted far more often.
 */
@Composable
fun WelcomeScreen(onDone: () -> Unit) {
    // Explore re-checks the permission and locates on its own, so the result
    // itself isn't needed here — only that the dialog has closed.
    val locator = rememberLocationRequester { onDone() }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = Space.xl, vertical = Space.xl)
    ) {
        Spacer(Modifier.weight(1f))
        AnimatedVisibility(shown, enter = fadeIn(tween(400))) {
            Column {
                Image(
                    painterResource(Res.drawable.main_icon),
                    contentDescription = "نوبت‌یار",
                    modifier = Modifier.size(64.dp)
                )
                Spacer(Modifier.height(Space.xl))
                Text("نوبت‌یار", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(Space.sm))
                Text(
                    "پزشک، سالن و مشاور نزدیک خود را پیدا کنید و بدون تماس نوبت بگیرید.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(40.dp))
                Column(verticalArrangement = Arrangement.spacedBy(Space.xl)) {
                    Feature(Icons.Outlined.NearMe, "نزدیک‌ترین‌ها", "مراکز اطراف شما، به ترتیب فاصله")
                    Feature(Icons.Outlined.HealthAndSafety, "بیمه تکمیلی", "مراکزی که بیمه شما را می‌پذیرند")
                    Feature(Icons.Outlined.Bolt, "رزرو آنلاین", "در مراکز نوبت‌یار، بدون تماس تلفنی")
                }
            }
        }
        Spacer(Modifier.weight(1.4f))

        Button(
            onClick = { locator.request() },
            shape = Radius.md,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text("استفاده از موقعیت من", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(Space.sm))
        TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text("بعداً", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            "موقعیت فقط برای همان جستجو استفاده می‌شود و ذخیره نمی‌شود.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = Space.sm)
        )
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, body: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(Space.lg))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
