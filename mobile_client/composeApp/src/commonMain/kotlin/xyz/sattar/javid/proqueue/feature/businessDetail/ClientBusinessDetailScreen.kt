package xyz.sattar.javid.proqueue.feature.businessDetail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.koin.compose.viewmodel.koinViewModel
import xyz.sattar.javid.proqueue.core.ui.components.BottomBarDefaults
import xyz.sattar.javid.proqueue.core.ui.components.UiMessage
import xyz.sattar.javid.proqueue.core.ui.components.showToasty
import xyz.sattar.javid.proqueue.core.utils.AppConfig
import xyz.sattar.javid.proqueue.core.utils.openPhoneDial
import xyz.sattar.javid.proqueue.domain.model.business.Business
import xyz.sattar.javid.proqueue.ui.theme.PrimaryPurple
import xyz.sattar.javid.proqueue.ui.theme.PrimaryPurpleDark

private val AmberAccent = Color(0xFFFBBF24)
private val GradientStart = Color(0xFF8B5CF6)
private val GradientEnd = Color(0xFF4C1D95)

private fun resolveLogoUrl(logoPath: String): String =
    if (logoPath.startsWith("http")) logoPath else "${AppConfig.baseUrl}$logoPath"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ClientBusinessDetailScreen(
    businessId: Long,
    onNavigateBack: () -> Unit,
    viewModel: ClientBusinessDetailViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val uriHandler = LocalUriHandler.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(businessId) {
        viewModel.sendIntent(ClientBusinessDetailIntent.LoadBusiness(businessId))
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ClientBusinessDetailEvent.ShowToast -> {
                    snackbarHostState.showToasty(UiMessage.success(event.message))
                }
            }
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = state.business?.title ?: "مشخصات مرکز",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "بازگشت"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                // If official claimed business, show sticky booking CTA
                val business = state.business
                if (business != null && business.isClaimed) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shadowElevation = 8.dp,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Button(
                                onClick = {
                                    val code = business.uniqueCode?.takeIf { it.isNotBlank() } ?: business.id.toString()
                                    val bookingUrl = "${AppConfig.bookingBaseUrl}/b/Noobatyar-$code"
                                    uriHandler.openUri(bookingUrl)
                                },
                                enabled = business.bookingEnabled,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PrimaryPurple
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = if (business.bookingEnabled) "⚡ رزرو آنلاین نوبت در نوبت‌یار"
                                        else "پذیرش نوبت آنلاین موقتاً غیرفعال است",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        ) { paddingValues ->
            if (state.isLoading && state.business == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = PrimaryPurple)
                }
            } else if (state.business == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.errorMessage ?: "مرکز مورد نظر یافت نشد",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val business = state.business!!
                val scrollState = rememberScrollState()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(scrollState)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = if (business.isClaimed) {
                            BorderStroke(1.5.dp, Brush.horizontalGradient(listOf(GradientStart, GradientEnd)))
                        } else {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        },
                        shape = RoundedCornerShape(20.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (business.logoPath.isNotEmpty()) {
                                    AsyncImage(
                                        model = resolveLogoUrl(business.logoPath),
                                        contentDescription = business.title,
                                        modifier = Modifier
                                            .size(68.dp)
                                            .clip(RoundedCornerShape(18.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(68.dp)
                                            .background(
                                                if (business.isClaimed) Brush.linearGradient(listOf(GradientStart, GradientEnd))
                                                else Brush.linearGradient(listOf(Color(0xFF6B7280), Color(0xFF4B5563))),
                                                RoundedCornerShape(18.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = business.title.take(1),
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = business.title,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = business.categoryLabel.ifEmpty { "مرکز تخصصی" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (business.isClaimed) {
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = Color(0xFF10B981).copy(alpha = 0.12f),
                                                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.3f))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Verified,
                                                        contentDescription = null,
                                                        tint = Color(0xFF10B981),
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "عضو رسمی نوبت‌یار",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF047857)
                                                    )
                                                }
                                            }
                                        } else {
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant
                                            ) {
                                                Text(
                                                    text = "بانک اطلاعات عمومی",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }

                                        if (business.distanceKm != null) {
                                            Text(
                                                text = "📍 ${formatDistance(business.distanceKm)} تا شما",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = PrimaryPurple,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Emergency Notice Banner (if enabled)
                    if (business.noticeEnabled && business.noticeMessage.isNotBlank()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = AmberAccent.copy(alpha = 0.15f)
                            ),
                            border = BorderStroke(1.dp, AmberAccent),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(text = "⚠️", fontSize = 18.sp)
                                Text(
                                    text = business.noticeMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // Supported Insurances Section
                    if (business.insurances.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Shield,
                                        contentDescription = null,
                                        tint = PrimaryPurple,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "بیمه‌های طرف قرارداد",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    business.insurances.forEach { ins ->
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = PrimaryPurple.copy(alpha = 0.1f),
                                            border = BorderStroke(1.dp, PrimaryPurple.copy(alpha = 0.25f))
                                        ) {
                                            Text(
                                                text = "🛡️ ${ins.name}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = PrimaryPurpleDark,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Contact & Actions Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "اطلاعات تماس و آدرس",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            // Address
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    imageVector = Icons.Rounded.LocationOn,
                                    contentDescription = null,
                                    tint = PrimaryPurple,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    val fullAddress = buildString {
                                        if (business.province.isNotEmpty() || business.city.isNotEmpty()) {
                                            append(listOf(business.province, business.city).filter { it.isNotEmpty() }.joinToString(" - "))
                                            if (business.neighborhood.isNotEmpty()) {
                                                append(" (محله ${business.neighborhood})")
                                            }
                                            if (business.address.isNotEmpty()) {
                                                append("\n")
                                            }
                                        }
                                        if (business.address.isNotEmpty()) {
                                            append(business.address)
                                        }
                                    }.trim()
                                    Text(
                                        text = fullAddress.ifEmpty {
                                            if (!business.allowAnonymousView) "آدرس پس از رزرو نوبت نمایش داده می‌شود"
                                            else "آدرس ثبت نشده است"
                                        },
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }

                            if (business.phone.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.Call,
                                        contentDescription = null,
                                        tint = PrimaryPurple,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    val allPhones = listOf(business.phone) + business.extraPhones
                                    Text(
                                        text = "شماره تماس: ${allPhones.joinToString(" ، ")}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Action Buttons: Call & Navigate
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (business.phone.isNotEmpty()) {
                                            openPhoneDial(business.phone)
                                        }
                                    },
                                    enabled = business.phone.isNotEmpty(),
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Call,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = "تماس تلفنی", style = MaterialTheme.typography.labelMedium)
                                }

                                if (business.latitude != null && business.longitude != null) {
                                    OutlinedButton(
                                        onClick = {
                                            val mapUrl = "https://maps.google.com/?q=${business.latitude},${business.longitude}"
                                            uriHandler.openUri(mapUrl)
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, PrimaryPurple)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Map,
                                            contentDescription = null,
                                            tint = PrimaryPurple,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "مسیریابی نقشه",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = PrimaryPurple
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Working Hours & Services Card
                    if (business.workStartHour > 0 || business.services.isNotEmpty() || business.bio.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                if (business.workStartHour > 0 && business.workEndHour > 0) {
                                    val startMin = business.workStartMinute.toString().padStart(2, '0')
                                    val endMin = business.workEndMinute.toString().padStart(2, '0')
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Rounded.Schedule,
                                            contentDescription = null,
                                            tint = PrimaryPurple,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "ساعت کاری: ${business.workStartHour}:$startMin الی ${business.workEndHour}:$endMin",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                }

                                if (business.services.isNotEmpty()) {
                                    Text(
                                        text = "خدمات قابل ارائه:",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        business.services.forEach { service ->
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant
                                            ) {
                                                Text(
                                                    text = service,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                }

                                if (business.bio.isNotEmpty()) {
                                    Text(
                                        text = "درباره مرکز:",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = business.bio,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // MODE B: Unclaimed Directory Listing Sections
                    if (!business.isClaimed) {
                        // Box 1: For Clients (Request Online Booking)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            ),
                            border = BorderStroke(1.dp, PrimaryPurple.copy(alpha = 0.25f)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.NotificationsActive,
                                        contentDescription = null,
                                        tint = PrimaryPurple,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Text(
                                        text = "نوبت‌دهی آنلاین در این مرکز فعال نیست",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "با ثبت درخواست شما، سیستم هوشمند نوبت‌یار به مدیریت این مرکز اطلاع می‌دهد تا رزرو آنلاین را برای مراجعین فعال نماید.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                if (state.hasRequestedBooking) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFF10B981).copy(alpha = 0.15f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = null,
                                                tint = Color(0xFF047857),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "✓ درخواست شما با موفقیت ثبت شد (${state.bookingRequestsCount} نفر درخواست داده‌اند)",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF047857)
                                            )
                                        }
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            viewModel.sendIntent(ClientBusinessDetailIntent.RequestBooking(business.id))
                                        },
                                        enabled = !state.isRequestingBooking,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        if (state.isRequestingBooking) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                color = Color.White,
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Text(
                                                text = "🔔 درخواست فعال‌سازی نوبت‌دهی آنلاین (${state.bookingRequestsCount} نفر تاکنون)",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Box 2: For Business Owners (Viral Acquisition Hook)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(1.5.dp, Brush.horizontalGradient(listOf(AmberAccent, GradientStart))),
                            shape = RoundedCornerShape(20.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(text = "👑", fontSize = 24.sp)
                                    Text(
                                        text = "صاحب این مطب یا سالن زیبایی هستید؟",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Black,
                                        color = PrimaryPurpleDark
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "با نصب اپلیکیشن مدیران نوبت‌یار، مدیریت این صفحه را رایگان تحویل بگیرید، تیک رسمی و جایگاه ویژه در صدر نتایج اطراف دریافت کنید و نوبت‌دهی آنلاین و پیامک یادآوری خودکار را فعال نمایید.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 22.sp
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Button(
                                    onClick = {
                                        // Open Bazaar or Noobatyar owner landing
                                        uriHandler.openUri("https://cafebazaar.ir/app/xyz.sattar.javid.proqueue")
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = PrimaryPurpleDark
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = "دریافت اپلیکیشن مدیران نوبت‌یار (Owner)",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(40.dp))
                }
            }
        }
    }
}

private fun formatDistance(km: Double): String {
    return if (km < 1.0) {
        val meters = (km * 1000).toInt()
        "$meters متر"
    } else {
        val rounded = (km * 10).toInt() / 10.0
        "$rounded کیلومتر"
    }
}
