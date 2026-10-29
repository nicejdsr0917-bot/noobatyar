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
import androidx.compose.material.icons.outlined.LocationCity
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import proqueue.composeapp.generated.resources.Res
import proqueue.composeapp.generated.resources.main_icon
import xyz.sattar.javid.proqueue.feature.discovery.Radius
import xyz.sattar.javid.proqueue.feature.discovery.Space

/**
 * One-time introduction. The app is city-first, like Divar: "انتخاب شهر" leads
 * to the explore screen, which opens the city picker by itself on first launch.
 */
@Composable
fun WelcomeScreen(onDone: () -> Unit) {
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
                    Feature(Icons.Outlined.LocationCity, "شهر شما", "مراکز نوبت‌یار در شهری که انتخاب می‌کنید")
                    Feature(Icons.Outlined.HealthAndSafety, "بیمه تکمیلی", "مراکزی که بیمه شما را می‌پذیرند")
                    Feature(Icons.Outlined.Bolt, "رزرو آنلاین", "در مراکز نوبت‌یار، بدون تماس تلفنی")
                }
            }
        }
        Spacer(Modifier.weight(1.4f))

        Button(
            onClick = onDone,
            shape = Radius.md,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text("انتخاب شهر", fontWeight = FontWeight.Bold)
        }
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
