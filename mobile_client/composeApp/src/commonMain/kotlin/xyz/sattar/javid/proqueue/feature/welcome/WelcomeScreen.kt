package xyz.sattar.javid.proqueue.feature.welcome

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import proqueue.composeapp.generated.resources.Res
import proqueue.composeapp.generated.resources.main_icon
import xyz.sattar.javid.proqueue.core.location.rememberLocationRequester
import xyz.sattar.javid.proqueue.feature.discovery.Brand

/**
 * One-time introduction. Asks for location here, with the reason on screen,
 * rather than as a cold system dialog on the explore screen — a permission
 * asked in context is granted far more often.
 */
@Composable
fun WelcomeScreen(onDone: () -> Unit) {
    // Explore re-checks the permission and locates on its own, so the
    // result itself isn't needed here — only that the dialog has closed.
    val locator = rememberLocationRequester { onDone() }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brand.HeroGradient)
    ) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-160).dp, y = 40.dp)
                .requiredSize(260.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.06f))
        )
        Box(
            Modifier
                .padding(top = 360.dp)
                .size(180.dp)
                .clip(CircleShape)
                .background(Color(0xFFEC4899).copy(alpha = 0.14f))
        )

        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            Spacer(Modifier.weight(0.6f))
            AnimatedVisibility(shown, enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { it / 4 }) {
                Column {
                    Surface(shape = RoundedCornerShape(24.dp), color = Color.White, modifier = Modifier.size(84.dp)) {
                        Image(
                            painterResource(Res.drawable.main_icon),
                            contentDescription = "نوبت‌یار",
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                    Text("نوبت‌یار", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "نزدیک‌ترین پزشک، سالن و مشاور را پیدا کن\nو بدون تماس و انتظار نوبت بگیر.",
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.titleMedium,
                        lineHeight = 28.sp
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
            AnimatedVisibility(shown, enter = fadeIn(tween(600, delayMillis = 200))) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Feature(Icons.Rounded.MyLocation, "نزدیک‌ترین‌ها", "مراکز اطرافت به ترتیب فاصله")
                    Feature(Icons.Rounded.HealthAndSafety, "بیمه تکمیلی", "ببین کدام مراکز بیمه‌ات را قبول می‌کنند")
                    Feature(Icons.Rounded.Bolt, "رزرو آنی", "در مراکز نوبت‌یار، همین حالا نوبت بگیر")
                }
            }
            Spacer(Modifier.weight(1f))

            Surface(
                onClick = { locator.request() },
                shape = RoundedCornerShape(18.dp),
                color = Color.White,
                modifier = Modifier.fillMaxWidth().height(58.dp)
            ) {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.MyLocation, contentDescription = null, tint = Brand.Violet700)
                    Spacer(Modifier.width(8.dp))
                    Text("شروع با موقعیت من", color = Brand.Violet700, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text("فعلاً بدون موقعیت", color = Color.White.copy(alpha = 0.85f))
            }
            Text(
                "موقعیت شما فقط برای همین جستجو استفاده می‌شود و ذخیره نمی‌شود.",
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, body: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, contentDescription = null, tint = Color.White) }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Text(body, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall)
        }
    }
}
