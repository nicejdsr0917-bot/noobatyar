package xyz.sattar.javid.proqueue.feature.promo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import xyz.sattar.javid.proqueue.core.ui.collectWithLifecycleAware
import xyz.sattar.javid.proqueue.core.ui.components.AppButton
import xyz.sattar.javid.proqueue.core.ui.components.SmsBubble
import xyz.sattar.javid.proqueue.core.ui.components.SmsCounter
import xyz.sattar.javid.proqueue.core.ui.components.ToastyHost
import xyz.sattar.javid.proqueue.core.ui.components.showToasty
import xyz.sattar.javid.proqueue.core.utils.DateTimeUtils
import xyz.sattar.javid.proqueue.core.utils.toPersianDigits
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.PromoCampaignDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.PromoPreviewDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.PromoSmsOverviewDto
import xyz.sattar.javid.proqueue.domain.model.business.PanelSmsSuffix
import xyz.sattar.javid.proqueue.domain.model.business.SmsSegments

private val Green = Color(0xFF10B981)
private val Amber = Color(0xFFF59E0B)
private val HeroViolets = listOf(Color(0xFF6D28D9), Color(0xFF8B5CF6), Color(0xFFA855F7))

private fun Int.fa() = toString().toPersianDigits()

@Composable
fun PromoCampaignScreen(
    viewModel: PromoCampaignViewModel = koinViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToAddons: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    viewModel.events.collectWithLifecycleAware { event ->
        when (event) {
            PromoCampaignEvent.NavigateToAddons -> onNavigateToAddons()
        }
    }
    LaunchedEffect(Unit) { viewModel.sendIntent(PromoCampaignIntent.Load) }
    LaunchedEffect(state.toast) {
        state.toast?.let {
            snackbar.showToasty(it)
            viewModel.sendIntent(PromoCampaignIntent.ToastShown)
        }
    }

    val overview = state.overview
    // What one recipient's message costs, with a sample name; the exact total
    // comes from the server in the confirm dialog.
    val sample = PanelSmsSuffix.sign(
        state.message
            .replace(PromoCampaignViewModel.NAME_TOKEN, "مریم")
            .replace(PromoCampaignViewModel.BUSINESS_TOKEN, state.businessTitle)
    )
    val selectedCount = overview?.audiences?.firstOrNull { it.key == state.audience }?.count ?: 0
    val estimatedParts = SmsSegments.parts(sample) * selectedCount

    Scaffold(
        snackbarHost = { ToastyHost(hostState = snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text("پیامک تبلیغاتی", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.sendIntent(PromoCampaignIntent.Load) }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "بروزرسانی")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            if (overview?.entitled == true) {
                Surface(color = MaterialTheme.colorScheme.background, shadowElevation = 8.dp) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .navigationBarsPadding()
                    ) {
                        Text(
                            text = "حدود ${estimatedParts.fa()} پیامک برای ${selectedCount.fa()} نفر",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(6.dp))
                        AppButton(
                            text = "بررسی هزینه و ارسال",
                            onClick = { viewModel.sendIntent(PromoCampaignIntent.RequestSend) },
                            isLoading = state.isPreviewing,
                            enabled = state.message.isNotBlank() && selectedCount > 0 && overview.withinSendWindow,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (overview == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (state.isLoading) CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            Hero(overview)

            if (!overview.entitled) {
                LockedCard(onUpgrade = { viewModel.sendIntent(PromoCampaignIntent.GetCredit) })
            } else {
                if (!overview.withinSendWindow) {
                    Banner(
                        icon = Icons.Rounded.Schedule,
                        tint = Amber,
                        text = "خط پیامک تبلیغاتی فقط بین ساعت ${overview.sendWindowStart.fa()} تا " +
                            "${overview.sendWindowEnd.fa()} پیامک تحویل می‌دهد؛ می‌توانید متن را آماده کنید و در این بازه ارسال کنید."
                    )
                }

                // ── 1. Audience ──
                Section(title = "۱. به چه کسانی بفرستیم؟", icon = Icons.Rounded.Groups) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        overview.audiences.forEach { audience ->
                            AudienceOption(
                                label = audience.label,
                                hint = audienceHint(audience.key),
                                count = audience.count,
                                selected = state.audience == audience.key,
                                onClick = { viewModel.sendIntent(PromoCampaignIntent.SelectAudience(audience.key)) }
                            )
                        }
                    }
                }

                // ── 2. Message ──
                Section(title = "۲. متن پیامک", icon = Icons.Rounded.Sms) {
                    Text(
                        "با یکی از الگوها شروع کنید یا متن خودتان را بنویسید. «نام مشتری» برای هر نفر با اسم خودش پر می‌شود.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    ) {
                        overview.readyTexts.forEachIndexed { i, text ->
                            SuggestionChip(
                                onClick = { viewModel.sendIntent(PromoCampaignIntent.ApplyReadyText(text)) },
                                label = { Text(readyLabel(i)) },
                                icon = { Icon(Icons.Rounded.Star, null, modifier = Modifier.size(16.dp)) },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.message,
                        onValueChange = { viewModel.sendIntent(PromoCampaignIntent.UpdateMessage(it)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        maxLines = 8,
                        shape = RoundedCornerShape(16.dp),
                        supportingText = {
                            Text("${state.message.length.fa()} / ${overview.maxMessageChars.fa()}")
                        }
                    )
                    AssistChip(
                        onClick = { viewModel.sendIntent(PromoCampaignIntent.InsertName) },
                        label = { Text("+ نام مشتری") },
                        shape = RoundedCornerShape(12.dp),
                        colors = AssistChipDefaults.assistChipColors(labelColor = MaterialTheme.colorScheme.primary)
                    )
                }

                // ── 3. Preview & cost ──
                Section(title = "۳. پیش‌نمایش و هزینه", icon = Icons.Rounded.Campaign) {
                    SmsBubble(
                        body = sample.removeSuffix(PanelSmsSuffix.TEXT),
                        lockedSuffix = true
                    )
                    Spacer(Modifier.height(12.dp))
                    SmsCounter(
                        text = sample,
                        footnote = "هر پیامک فارسی ۷۰ کاراکتر است (در پیام چندبخشی ۶۷). نقطه حذف می‌شود چون خط " +
                            "پیامکی آن را لینک حساب می‌کند. هر پیامک از اعتبار پیامک شما کسر می‌شود."
                    )
                }
            }

            // ── History ──
            if (overview.campaigns.isNotEmpty()) {
                Section(title = "کمپین‌های قبلی", icon = Icons.Rounded.History) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        overview.campaigns.forEach { c ->
                            CampaignRow(c) { viewModel.sendIntent(PromoCampaignIntent.OpenCampaign(c.id)) }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    state.confirm?.let { preview ->
        ConfirmDialog(
            preview = preview,
            sending = state.isSending,
            onConfirm = { viewModel.sendIntent(PromoCampaignIntent.ConfirmSend) },
            onDismiss = { viewModel.sendIntent(PromoCampaignIntent.DismissConfirm) },
            onGetCredit = { viewModel.sendIntent(PromoCampaignIntent.GetCredit) }
        )
    }

    state.openedCampaign?.let { campaign ->
        CampaignSheet(campaign) { viewModel.sendIntent(PromoCampaignIntent.CloseCampaign) }
    }
}

private fun audienceHint(key: String) = when (key) {
    "recent" -> "مشتری‌هایی که در ماه اخیر آمده‌اند؛ برای معرفی خدمت جدید یا دعوت به مراجعه‌ی بعدی"
    "inactive" -> "مشتری‌هایی که مدتی است نیامده‌اند؛ بهترین گروه برای پیشنهاد تخفیف و برگرداندنشان"
    else -> "همه‌ی مشتری‌های فهرست شما (به جز بایگانی‌شده‌ها)"
}

private fun readyLabel(index: Int) = when (index) {
    0 -> "دلتنگی"
    1 -> "تخفیف ویژه"
    2 -> "خدمت جدید"
    3 -> "رزرو زودهنگام"
    else -> "الگوی ${(index + 1).fa()}"
}

@Composable
private fun Hero(overview: PromoSmsOverviewDto) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(HeroViolets))
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.Campaign, null, tint = Color.White) }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("مشتری‌هایتان را برگردانید", color = Color.White, fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium)
                    Text("با یک پیامک، تخفیف یا خبر تازه را به همه‌شان برسانید",
                        color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeroStat(
                    label = "اعتبار پیامک",
                    value = overview.availableCredit?.fa() ?: "نامحدود",
                    modifier = Modifier.weight(1f)
                )
                HeroStat(
                    label = "ساعت ارسال",
                    value = "${overview.sendWindowStart.fa()} تا ${overview.sendWindowEnd.fa()}",
                    dot = if (overview.withinSendWindow) Green else Amber,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier = Modifier, dot: Color? = null) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(label, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (dot != null) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
                Spacer(Modifier.width(6.dp))
            }
            Text(value, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun LockedCard(onUpgrade: () -> Unit) {
    Section(title = "این قابلیت در پلن فعلی شما نیست", icon = Icons.Rounded.Lock) {
        Text(
            "با پیامک تبلیغاتی می‌توانید به مشتری‌هایی که مدتی نیامده‌اند پیشنهاد تخفیف بدهید، خدمت تازه را معرفی کنید " +
                "و نوبت‌های خالی را پر کنید — هر پیامک با نام خود مشتری و بدون نیاز به پنل پیامکی جداگانه.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        AppButton(text = "مشاهده‌ی پلن‌ها و ارتقا", onClick = onUpgrade, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun Section(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(28.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)) }
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun Banner(icon: ImageVector, tint: Color, text: String) {
    Surface(shape = RoundedCornerShape(14.dp), color = tint.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AudienceOption(label: String, hint: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface)
            .border(
                if (selected) 1.5.dp else 1.dp,
                if (selected) primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                color = if (selected) primary else MaterialTheme.colorScheme.onSurface)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(count.fa(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                color = if (selected) primary else MaterialTheme.colorScheme.onSurface)
            Text("نفر", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CampaignRow(c: PromoCampaignDto, onClick: () -> Unit) {
    val sending = c.status == "SENDING"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(c.message, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(
                "${c.audienceLabel} · ${c.createdAt?.let { formatIso(it) }.orEmpty()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            StatusChip(
                text = if (sending) "در حال ارسال" else "ارسال شد",
                tint = if (sending) Amber else Green
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${c.sentCount.fa()} از ${c.recipientCount.fa()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatusChip(text: String, tint: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = tint,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

private fun formatIso(iso: String): String = runCatching {
    DateTimeUtils.formatDateTime(DateTimeUtils.parseIsoToEpochMillis(iso))
}.getOrDefault("")

@Composable
private fun ConfirmDialog(
    preview: PromoPreviewDto,
    sending: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onGetCredit: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        icon = { Icon(Icons.Rounded.Campaign, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("ارسال کمپین", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ConfirmLine("گیرندگان", "${preview.recipientCount.fa()} نفر")
                ConfirmLine("هر پیامک", "${preview.partsPerMessage.fa()} بخش")
                ConfirmLine("مجموع هزینه", "${preview.totalParts.fa()} پیامک", bold = true)
                ConfirmLine("اعتبار فعلی شما", preview.availableCredit?.let { "${it.fa()} پیامک" } ?: "نامحدود")
                when {
                    !preview.enoughCredit -> Banner(
                        Icons.Rounded.ErrorOutline, MaterialTheme.colorScheme.error,
                        "اعتبار شما برای این کمپین کافی نیست؛ ابتدا بسته‌ی پیامک تهیه کنید."
                    )
                    !preview.withinSendWindow -> Banner(
                        Icons.Rounded.Schedule, Amber,
                        "الان خارج از ساعت مجاز ارسال است؛ در بازه‌ی مجاز دوباره تلاش کنید."
                    )
                    else -> Text(
                        "پس از تأیید، ارسال بلافاصله شروع می‌شود و قابل لغو نیست.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            if (!preview.enoughCredit) {
                TextButton(onClick = onGetCredit) { Text("خرید اعتبار پیامک") }
            } else {
                TextButton(onClick = onConfirm, enabled = !sending && preview.withinSendWindow) {
                    if (sending) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("تأیید و ارسال", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !sending) { Text("انصراف") }
        }
    )
}

@Composable
private fun ConfirmLine(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
private fun CampaignSheet(campaign: PromoCampaignDto, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text("جزئیات کمپین", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(campaign.message, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip("${campaign.sentCount.fa()} ارسال‌شده", Green)
                if (campaign.failedCount > 0) StatusChip("${campaign.failedCount.fa()} ناموفق", MaterialTheme.colorScheme.error)
                if (campaign.skippedCount > 0) StatusChip("${campaign.skippedCount.fa()} بدون اعتبار", Amber)
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                items(campaign.recipients) { r ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        val (icon, tint) = when (r.status) {
                            "SENT" -> Icons.Rounded.CheckCircle to Green
                            "FAILED" -> Icons.Rounded.ErrorOutline to MaterialTheme.colorScheme.error
                            "SKIPPED_QUOTA" -> Icons.Rounded.WarningAmber to Amber
                            else -> Icons.Rounded.AccessTime to MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.name.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium)
                            if (r.errorDetail.isNotBlank()) {
                                Text(r.errorDetail, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Text("⁦${r.phone}⁩", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
