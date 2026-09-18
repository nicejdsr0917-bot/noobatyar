package xyz.sattar.javid.proqueue.feature.createBusiness

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import org.jetbrains.compose.resources.stringResource
import proqueue.composeapp.generated.resources.Res
import proqueue.composeapp.generated.resources.coming_soon_badge
import proqueue.composeapp.generated.resources.coming_soon_hint
import xyz.sattar.javid.proqueue.core.ui.components.AppTextField
import xyz.sattar.javid.proqueue.core.ui.components.FeatureGate
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.EntitlementKeys
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.EntitlementsResponseDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.PlanDto

/**
 * The business "advanced settings" area, organized by *topic* rather than by
 * pricing tier: پرداخت (payments) → ظرفیت (capacity & deposit) → یادآوری
 * (reminders & SMS). Within each topic, a capability that the user's plan does
 * not include is replaced inline by an upgrade card naming the cheapest plan
 * that unlocks it — so, e.g., all the ways of receiving money live together and
 * only the premium ones (online gateway) show a lock, instead of card-to-card
 * and the online gateway being split across separate tabs.
 *
 * Feature → capability mapping mirrors backend/accounting/entitlements.py
 * exactly, so nothing shown as "unlocked" here can be rejected by the server.
 */
@Composable
fun AdvancedSettingsTabs(
    entitlements: EntitlementsResponseDto?,
    plans: List<PlanDto>,
    onUpgrade: (Int) -> Unit,
    isLoading: Boolean,
    acceptedPaymentMethods: Set<String>,
    onAcceptedPaymentMethods: (Set<String>) -> Unit,
    cardNumber: String,
    onCardNumber: (String) -> Unit,
    cardOwnerName: String,
    onCardOwnerName: (String) -> Unit,
    maxAppointmentsPerHour: String,
    onMaxAppointmentsPerHour: (String) -> Unit,
    depositMode: String,
    onDepositMode: (String) -> Unit,
    depositAmount: String,
    onDepositAmount: (String) -> Unit,
    merchantId: String,
    onMerchantId: (String) -> Unit,
    paymentLink: String,
    onPaymentLink: (String) -> Unit,
) {
    var selectedTab by remember { mutableStateOf(0) }
    // "روزهای تعطیل" moved to the business create/edit form, next to work
    // hours — it is basic scheduling info, not a paid/advanced capability, and
    // living here made it easy to miss when setting up a new business.
    val tabs = listOf("پرداخت", "ظرفیت و بیعانه", "یادآوری")

    Column(modifier = Modifier.fillMaxWidth()) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = androidx.compose.ui.graphics.Color.Transparent
        ) {
            tabs.forEachIndexed { index, label ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(label, style = MaterialTheme.typography.labelLarge) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (selectedTab) {
            0 -> PaymentTab(
                entitlements = entitlements,
                plans = plans,
                onUpgrade = onUpgrade,
                isLoading = isLoading,
                acceptedPaymentMethods = acceptedPaymentMethods,
                onAcceptedPaymentMethods = onAcceptedPaymentMethods,
                cardNumber = cardNumber,
                onCardNumber = onCardNumber,
                cardOwnerName = cardOwnerName,
                onCardOwnerName = onCardOwnerName,
                merchantId = merchantId,
                onMerchantId = onMerchantId,
                paymentLink = paymentLink,
                onPaymentLink = onPaymentLink
            )
            1 -> CapacityTab(
                entitlements = entitlements,
                plans = plans,
                onUpgrade = onUpgrade,
                isLoading = isLoading,
                maxAppointmentsPerHour = maxAppointmentsPerHour,
                onMaxAppointmentsPerHour = onMaxAppointmentsPerHour,
                depositMode = depositMode,
                onDepositMode = onDepositMode,
                depositAmount = depositAmount,
                onDepositAmount = onDepositAmount
            )
            2 -> RemindersTab(entitlements = entitlements, plans = plans, onUpgrade = onUpgrade)
        }
    }
}

/**
 * All the ways of receiving money in one place. Only cash (pay in person) is
 * free on every plan; card-to-card and the online bank gateway are premium
 * capabilities and are gated inline (card-to-card lives in the deposit tier).
 */
@Composable
private fun PaymentTab(
    entitlements: EntitlementsResponseDto?,
    plans: List<PlanDto>,
    onUpgrade: (Int) -> Unit,
    isLoading: Boolean,
    acceptedPaymentMethods: Set<String>,
    onAcceptedPaymentMethods: (Set<String>) -> Unit,
    cardNumber: String,
    onCardNumber: (String) -> Unit,
    cardOwnerName: String,
    onCardOwnerName: (String) -> Unit,
    merchantId: String,
    onMerchantId: (String) -> Unit,
    paymentLink: String,
    onPaymentLink: (String) -> Unit,
) {
    fun toggle(method: String, on: Boolean) {
        val updated = acceptedPaymentMethods.toMutableSet()
        if (on) updated.add(method) else updated.remove(method)
        onAcceptedPaymentMethods(updated)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "روش‌های دریافت وجه را انتخاب کنید.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // پرداخت در محل — رایگان در همه‌ی پلن‌ها
        PaymentMethodCard(
            icon = Icons.Rounded.Storefront,
            title = "پرداخت در محل",
            description = "مشتری هنگام مراجعه، وجه را حضوری پرداخت می‌کند.",
            checked = acceptedPaymentMethods.contains("CASH"),
            onCheckedChange = { toggle("CASH", it) },
            enabled = !isLoading,
        )

        // کارت به کارت — قابلیت پلن تعهد/بیعانه (به‌صورت درجا قفل می‌شود)
        FeatureGate(
            entitlements = entitlements,
            plans = plans,
            featureKey = EntitlementKeys.DEPOSIT,
            title = "کارت به کارت",
            description = "دریافت مبلغ نوبت با انتقال کارت‌به‌کارت.",
            onUpgrade = onUpgrade
        ) {
            PaymentMethodCard(
                icon = Icons.Rounded.CreditCard,
                title = "کارت به کارت",
                description = "مشتری مبلغ را به کارت شما واریز و فیش را ارسال می‌کند.",
                checked = acceptedPaymentMethods.contains("CARD"),
                onCheckedChange = { toggle("CARD", it) },
                enabled = !isLoading,
            ) {
                AppTextField(
                    enabled = !isLoading,
                    value = cardNumber,
                    onValueChange = onCardNumber,
                    label = "شماره کارت (۱۶ رقم)",
                    modifier = Modifier.fillMaxWidth(),
                    keyboardType = KeyboardType.Number,
                    maxLength = 16,
                    visualTransformation = xyz.sattar.javid.proqueue.core.ui.utils.CardNumberVisualTransformation()
                )
                Spacer(modifier = Modifier.height(12.dp))
                AppTextField(
                    enabled = !isLoading,
                    value = cardOwnerName,
                    onValueChange = onCardOwnerName,
                    label = "نام صاحب کارت",
                    modifier = Modifier.fillMaxWidth(),
                    keyboardType = KeyboardType.Text
                )
            }
        }

        // درگاه پرداخت آنلاین — قابلیت پلن‌های ویژه (به‌صورت درجا قفل می‌شود)
        FeatureGate(
            entitlements = entitlements,
            plans = plans,
            featureKey = EntitlementKeys.ONLINE_GATEWAY,
            title = "درگاه پرداخت آنلاین",
            description = "دریافت مبلغ نوبت مستقیم از طریق درگاه بانکی.",
            onUpgrade = onUpgrade
        ) {
            PaymentMethodCard(
                icon = Icons.Rounded.AccountBalance,
                title = "درگاه پرداخت آنلاین",
                description = "مشتری مستقیم به درگاه بانکی می‌رود و نوبت بلافاصله قطعی می‌شود.",
                checked = acceptedPaymentMethods.contains("ONLINE"),
                onCheckedChange = { toggle("ONLINE", it) },
                enabled = !isLoading,
            ) {
                AppTextField(
                    enabled = !isLoading,
                    value = merchantId,
                    onValueChange = onMerchantId,
                    label = "مرچنت آیدی زیبال (Merchant ID)",
                    modifier = Modifier.fillMaxWidth(),
                    keyboardType = KeyboardType.Text
                )
                FieldHint(
                    "با پر کردن این فیلد، بیعانه به حساب زیبالِ شما واریز می‌شود و " +
                        "نوبت بدون بررسی فیش قطعی می‌شود. مرچنت آیدی را از پنل zibal.ir بردارید."
                )
                Spacer(modifier = Modifier.height(12.dp))
                AppTextField(
                    enabled = !isLoading,
                    value = paymentLink,
                    onValueChange = onPaymentLink,
                    label = "لینک پرداخت (در نبودِ مرچنت آیدی)",
                    modifier = Modifier.fillMaxWidth(),
                    keyboardType = KeyboardType.Uri
                )
                FieldHint(
                    "فقط وقتی لازم است که حساب زیبال ندارید. چون مبلغ به لینک فرستاده " +
                        "نمی‌شود، لینک باید مبلغِ ثابتِ برابر با بیعانه داشته باشد."
                )
            }
        }
    }
}

/** Small explanatory line under a field, in the tab's muted style. */
@Composable
private fun FieldHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp)
    )
}

/**
 * One payment method: a card carrying its own icon, name, one-line
 * explanation and switch, with its configuration fields folded inside the
 * same card once it is on.
 *
 * Replaces a bare `Checkbox` + label row per method. Those had no visual
 * grouping at all, so a method's own settings (card number, merchant id) read
 * as loose fields belonging to the page rather than to the method above them —
 * and only the *locked* states got cards, which left the unlocked ones looking
 * unfinished next to them. Matches the card idiom in FeatureGate and the
 * icon-badge + Switch row used on the notifications screen.
 */
@Composable
private fun PaymentMethodCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean,
    content: @Composable (ColumnScope.() -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (checked) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
            }
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(
                if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // The whole row toggles, not just the switch — a 20dp checkbox was
            // the only target on the old layout.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled) { onCheckedChange(!checked) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    enabled = enabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }

            if (checked && content != null) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(16.dp))
                content()
            }
        }
    }
}

/**
 * Deposit modes as the backend defines them (Business.DEPOSIT_MODE_CHOICES).
 * OPTIONAL is a real third state: the client may pay the deposit or choose to
 * settle in person, so it must not collapse into a boolean.
 */
enum class DepositMode(val value: String, val label: String) {
    NONE("NONE", "بدون بیعانه"),
    OPTIONAL("OPTIONAL", "اختیاری"),
    MANDATORY("MANDATORY", "اجباری");

    companion object {
        fun describe(value: String): String = when (value) {
            OPTIONAL.value -> "مشتری می‌تواند بیعانه را پرداخت کند یا «پرداخت در محل» را انتخاب کند."
            MANDATORY.value -> "مشتری برای نهایی شدن نوبت باید بیعانه را پرداخت کند."
            else -> "نوبت بدون دریافت بیعانه ثبت می‌شود."
        }
    }
}

/** Capacity control and deposit — both gated capabilities, grouped together. */
@Composable
private fun CapacityTab(
    entitlements: EntitlementsResponseDto?,
    plans: List<PlanDto>,
    onUpgrade: (Int) -> Unit,
    isLoading: Boolean,
    maxAppointmentsPerHour: String,
    onMaxAppointmentsPerHour: (String) -> Unit,
    depositMode: String,
    onDepositMode: (String) -> Unit,
    depositAmount: String,
    onDepositAmount: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FeatureGate(
            entitlements = entitlements,
            plans = plans,
            featureKey = EntitlementKeys.CAPACITY_CONTROL,
            title = "کنترل ظرفیت ساعتی",
            description = "محدود کردن حداکثر تعداد نوبت در هر ساعت.",
            onUpgrade = onUpgrade
        ) {
            AppTextField(
                enabled = !isLoading,
                value = maxAppointmentsPerHour,
                onValueChange = onMaxAppointmentsPerHour,
                label = "حداکثر نوبت در هر ساعت (خالی = نامحدود)",
                modifier = Modifier.fillMaxWidth(),
                keyboardType = KeyboardType.Number
            )
        }

        FeatureGate(
            entitlements = entitlements,
            plans = plans,
            featureKey = EntitlementKeys.DEPOSIT,
            title = "دریافت بیعانه",
            description = "از مشتری هنگام رزرو بیعانه دریافت کنید.",
            onUpgrade = onUpgrade
        ) {
            Column {
                Text("دریافت بیعانه", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(8.dp))
                // Three real modes, not a switch: OPTIONAL is a distinct
                // behaviour (client may pay the deposit or choose to pay in
                // person), and a boolean silently rewrote it to MANDATORY.
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    DepositMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = depositMode == mode.value,
                            onClick = { onDepositMode(mode.value) },
                            enabled = !isLoading,
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = DepositMode.entries.size
                            )
                        ) {
                            Text(mode.label, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = DepositMode.describe(depositMode),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (depositMode != DepositMode.NONE.value) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AppTextField(
                        enabled = !isLoading,
                        value = depositAmount,
                        onValueChange = onDepositAmount,
                        label = "مبلغ بیعانه (تومان)",
                        modifier = Modifier.fillMaxWidth(),
                        keyboardType = KeyboardType.Number,
                        visualTransformation = xyz.sattar.javid.proqueue.core.ui.utils.CurrencyVisualTransformation()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    // The deposit amount lives here but the *method* is chosen in
                    // the «پرداخت» tab. Without saying so, the two screens look
                    // unrelated and it is not obvious how the money arrives.
                    Text(
                        text = "بیعانه از همان روش‌هایی گرفته می‌شود که در تب «پرداخت» " +
                                "فعال کرده‌اید — کارت به کارت یا درگاه آنلاین.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** Messaging capabilities — promotional SMS and multi-channel reminders. */
@Composable
private fun RemindersTab(
    entitlements: EntitlementsResponseDto?,
    plans: List<PlanDto>,
    onUpgrade: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FeatureGate(
            entitlements = entitlements,
            plans = plans,
            featureKey = EntitlementKeys.PROMOTIONAL_SMS,
            title = "پیامک تبلیغاتی",
            description = "ارسال پیامک کمپین و تبلیغات به مشتریان.",
            onUpgrade = onUpgrade
        ) {
            IncludedFeatureRow("امکان ارسال پیامک تبلیغاتی برای این کسب‌وکار فعال است.")
        }

        FeatureGate(
            entitlements = entitlements,
            plans = plans,
            featureKey = EntitlementKeys.MULTI_CHANNEL,
            title = "یادآوری چندکاناله",
            description = "یادآوری خودکار نوبت از طریق واتساپ و تلگرام، علاوه بر پیامک.",
            onUpgrade = onUpgrade
        ) {
            IncludedFeatureRow("یادآوری خودکار از طریق پیامک، واتساپ و تلگرام برای این کسب‌وکار فعال است.")
        }
    }
}

@Composable
private fun IncludedFeatureRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
