package xyz.sattar.javid.proqueue.feature.createBusiness

import xyz.sattar.javid.proqueue.core.i18n.Str
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.EventSeat
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Payments
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
import org.jetbrains.compose.resources.painterResource
import proqueue.composeapp.generated.resources.Res
import proqueue.composeapp.generated.resources.coming_soon_badge
import proqueue.composeapp.generated.resources.coming_soon_hint
import xyz.sattar.javid.proqueue.core.ui.components.AppTextField
import xyz.sattar.javid.proqueue.core.ui.components.FeatureGate
import xyz.sattar.javid.proqueue.core.utils.detectIranianBank
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.EntitlementKeys
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.EntitlementsResponseDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.PlanDto

/**
 * Which slice of the advanced settings one screen shows. The profile tab
 * opens each setting as its own focused screen (one of these); [ALL] is the
 * original three-tab view, still used by the desktop sidebar; [SETUP] stacks
 * deposit, capacity and payment methods for the create-business form.
 */
object AdvancedSection {
    const val ALL = -1
    const val PAYMENT = 0
    const val CAPACITY = 1
    const val DEPOSIT = 2
    const val PROMO = 3
    const val REMINDERS = 4
    const val SETUP = 5
}

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
    onOpenPromoCampaign: () -> Unit,
    onOpenReminders: () -> Unit,
    section: Int = AdvancedSection.ALL,
) {
    var selectedTab by remember { mutableStateOf(0) }
    // "روزهای تعطیل" moved to the business create/edit form, next to work
    // hours — it is basic scheduling info, not a paid/advanced capability, and
    // living here made it easy to miss when setting up a new business.
    val tabs = listOf(
        Icons.Rounded.Payments to Str.payment,
        Icons.Rounded.EventSeat to Str.capacity_deposit,
        Icons.Rounded.Campaign to Str.sms_promotion,
    )

    @Composable
    fun payment() = PaymentTab(
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

    @Composable
    fun capacity(showCapacity: Boolean, showDeposit: Boolean, goToPayment: (() -> Unit)?) = CapacityTab(
        entitlements = entitlements,
        plans = plans,
        onUpgrade = onUpgrade,
        isLoading = isLoading,
        maxAppointmentsPerHour = maxAppointmentsPerHour,
        onMaxAppointmentsPerHour = onMaxAppointmentsPerHour,
        depositMode = depositMode,
        onDepositMode = onDepositMode,
        depositAmount = depositAmount,
        onDepositAmount = onDepositAmount,
        acceptedPaymentMethods = acceptedPaymentMethods,
        onGoToPayment = goToPayment,
        showCapacity = showCapacity,
        showDeposit = showDeposit
    )

    @Composable
    fun messaging(showPromo: Boolean, showReminders: Boolean) = MessagingTab(
        entitlements = entitlements,
        plans = plans,
        onUpgrade = onUpgrade,
        onOpenPromoCampaign = onOpenPromoCampaign,
        onOpenReminders = onOpenReminders,
        showPromo = showPromo,
        showReminders = showReminders
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        when (section) {
            AdvancedSection.ALL -> {
                SegmentedTabs(tabs = tabs, selected = selectedTab, onSelect = { selectedTab = it })
                Spacer(modifier = Modifier.height(16.dp))
                when (selectedTab) {
                    0 -> payment()
                    1 -> capacity(true, true) { selectedTab = 0 }
                    2 -> messaging(true, true)
                }
            }
            AdvancedSection.PAYMENT -> payment()
            AdvancedSection.CAPACITY -> capacity(true, false, null)
            AdvancedSection.DEPOSIT -> capacity(false, true) { /* the methods are on the screen above */ }
            AdvancedSection.PROMO -> messaging(true, false)
            AdvancedSection.REMINDERS -> messaging(false, true)
            // Create-business form: deposit and capacity first, then how the
            // money arrives, all on one step.
            AdvancedSection.SETUP -> Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                capacity(true, true, null)
                payment()
            }
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
            Str.choose_how_you_get_paid,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // پرداخت در محل — رایگان در همه‌ی پلن‌ها
        PaymentMethodCard(
            icon = Icons.Rounded.Storefront,
            title = Str.pay_on_site,
            description = Str.customer_pays_in_person_when,
            checked = acceptedPaymentMethods.contains("CASH"),
            onCheckedChange = { toggle("CASH", it) },
            enabled = !isLoading,
        )

        // کارت به کارت — قابلیت پلن تعهد/بیعانه (به‌صورت درجا قفل می‌شود)
        FeatureGate(
            entitlements = entitlements,
            plans = plans,
            featureKey = EntitlementKeys.DEPOSIT,
            title = Str.deposit_method_card,
            description = Str.receive_appointment_fee_by_card,
            onUpgrade = onUpgrade
        ) {
            PaymentMethodCard(
                icon = Icons.Rounded.CreditCard,
                title = Str.deposit_method_card,
                description = Str.customer_transfers_amount_to_your,
                checked = acceptedPaymentMethods.contains("CARD"),
                onCheckedChange = { toggle("CARD", it) },
                enabled = !isLoading,
            ) {
                // Detected live as the owner types — the same "yes, this is
                // really your bank" confirmation every Iranian
                // banking/payment app gives, and a cheap sanity check against
                // a fat-fingered first few digits before the rest even goes
                // in. Null (fewer than 6 digits, or a BIN outside the
                // deliberately-partial table in IranianBankDetector.kt) just
                // means no icon — never a wrong one.
                val detectedBank = detectIranianBank(cardNumber)
                AppTextField(
                    enabled = !isLoading,
                    value = cardNumber,
                    onValueChange = onCardNumber,
                    label = Str.card_number_16_digits,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardType = KeyboardType.Number,
                    maxLength = 16,
                    visualTransformation = xyz.sattar.javid.proqueue.core.ui.utils.CardNumberVisualTransformation(),
                    leadingIcon = detectedBank?.let { bank ->
                        {
                            Image(
                                painter = painterResource(bank.logo),
                                contentDescription = bank.name,
                                modifier = Modifier.size(28.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    },
                )
                Spacer(modifier = Modifier.height(12.dp))
                AppTextField(
                    enabled = !isLoading,
                    value = cardOwnerName,
                    onValueChange = onCardOwnerName,
                    label = Str.cardholder_name,
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
            title = Str.online_payment_gateway,
            description = Str.receive_appointment_fee_directly_through,
            onUpgrade = onUpgrade
        ) {
            PaymentMethodCard(
                icon = Icons.Rounded.AccountBalance,
                title = Str.online_payment_gateway,
                description = Str.customer_goes_straight_to_bank,
                checked = acceptedPaymentMethods.contains("ONLINE"),
                onCheckedChange = { toggle("ONLINE", it) },
                enabled = !isLoading,
            ) {
                AppTextField(
                    enabled = !isLoading,
                    value = merchantId,
                    onValueChange = onMerchantId,
                    label = Str.zibal_merchant_id,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardType = KeyboardType.Text
                )
                FieldHint(
                    Str.with_this_filled_in_deposits +
                        Str.appointment_is_confirmed_without_checking
                )
                Spacer(modifier = Modifier.height(12.dp))
                AppTextField(
                    enabled = !isLoading,
                    value = paymentLink,
                    onValueChange = onPaymentLink,
                    label = Str.payment_link_when_theres_no,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardType = KeyboardType.Uri
                )
                FieldHint(
                    Str.only_needed_if_you_dont +
                        Str.link_must_have_fixed_amount
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
enum class DepositMode(val value: String, private val labelRef: () -> String) {
    NONE("NONE", Str::no_deposit),
    OPTIONAL("OPTIONAL", Str::optional_2),
    MANDATORY("MANDATORY", Str::required);
    val label: String get() = labelRef()


    companion object {
        fun describe(value: String): String = when (value) {
            OPTIONAL.value -> Str.customer_can_pay_deposit_or
            MANDATORY.value -> Str.customer_must_pay_deposit_to
            else -> Str.appointment_is_booked_without_deposit
        }
    }
}

/** Highest deposit in toman; mirrors Business.MAX_DEPOSIT_AMOUNT on the server. */
const val MAX_DEPOSIT_AMOUNT = 1_000_000L

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
    acceptedPaymentMethods: Set<String>,
    onGoToPayment: (() -> Unit)?,
    showCapacity: Boolean = true,
    showDeposit: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (showCapacity) FeatureGate(
            entitlements = entitlements,
            plans = plans,
            featureKey = EntitlementKeys.CAPACITY_CONTROL,
            title = Str.hourly_capacity_control,
            description = Str.limit_maximum_number_of_appointments,
            onUpgrade = onUpgrade
        ) {
          SettingCard(
              icon = Icons.Rounded.EventSeat,
              title = Str.hourly_capacity_control,
              description = Str.prevent_overcrowding_no_more_than
          ) {
            AppTextField(
                enabled = !isLoading,
                value = maxAppointmentsPerHour,
                onValueChange = onMaxAppointmentsPerHour,
                label = Str.max_appointments_per_hour_empty,
                modifier = Modifier.fillMaxWidth(),
                keyboardType = KeyboardType.Number
            )
          }
        }

        if (showDeposit) FeatureGate(
            entitlements = entitlements,
            plans = plans,
            featureKey = EntitlementKeys.DEPOSIT,
            title = Str.take_deposit,
            description = Str.collect_deposit_from_customer_when,
            onUpgrade = onUpgrade
        ) {
          SettingCard(
              icon = Icons.Rounded.AccountBalanceWallet,
              title = Str.take_deposit,
              description = Str.cut_down_on_no_shows
          ) {
            Column {
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
                        label = Str.deposit_amount_toman,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardType = KeyboardType.Number,
                        visualTransformation = xyz.sattar.javid.proqueue.core.ui.utils.CurrencyVisualTransformation()
                    )
                    // The server refuses more (Business.MAX_DEPOSIT_AMOUNT); say
                    // so while typing rather than after «ذخیره».
                    if ((depositAmount.filter { it.isDigit() }.toLongOrNull() ?: 0L) > MAX_DEPOSIT_AMOUNT) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = Str.deposit_amount_max,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    // The deposit amount lives here but the *method* is chosen in
                    // the «پرداخت» tab. Without saying so, the two screens look
                    // unrelated and it is not obvious how the money arrives.
                    Text(
                        text = Str.deposit_is_collected_with_methods +
                                Str.card_transfer_or_online_gateway,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Cash is paid at the visit, so it cannot settle a deposit:
                    // a mandatory one with only cash left clients stuck on a
                    // payment step with nothing to pay to. The server refuses
                    // it too (BusinessSerializer.validate).
                    if (depositMode == DepositMode.MANDATORY.value &&
                        "CARD" !in acceptedPaymentMethods && "ONLINE" !in acceptedPaymentMethods
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = Str.mandatory_deposit_needs_online_method,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        if (onGoToPayment != null) {
                            TextButton(onClick = onGoToPayment, enabled = !isLoading) {
                                Text(Str.open_payment_tab, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
          }
        }
    }
}

/**
 * SMS the owner pays for: the promo campaign composer and automatic reminders.
 *
 * Both used to be bare "this is enabled" rows with nothing to do — the
 * campaign had no sending tool at all, and a "multi-channel reminder" card
 * advertised WhatsApp/Telegram delivery that no code path implements (it is
 * still marked coming-soon on the server, so it is simply not shown here).
 * Each capability now opens the screen where it is actually used.
 */
@Composable
private fun MessagingTab(
    entitlements: EntitlementsResponseDto?,
    plans: List<PlanDto>,
    onUpgrade: (Int) -> Unit,
    onOpenPromoCampaign: () -> Unit,
    onOpenReminders: () -> Unit,
    showPromo: Boolean = true,
    showReminders: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (showPromo) FeatureGate(
            entitlements = entitlements,
            plans = plans,
            featureKey = EntitlementKeys.PROMOTIONAL_SMS,
            title = Str.promotional_sms_campaign,
            description = Str.send_your_customers_discount_and,
            onUpgrade = onUpgrade
        ) {
            SettingCard(
                icon = Icons.Rounded.Campaign,
                title = Str.promotional_sms_campaign,
                description = Str.win_back_customers_who_havent +
                    Str.or_fill_empty_slots_before
            ) {
                IncludedFeatureRow(Str.sent_with_each_customers_name)
                Spacer(Modifier.height(6.dp))
                IncludedFeatureRow(Str.choose_audience_everyone_recent_visitors)
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = onOpenPromoCampaign,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Campaign, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(Str.create_new_campaign)
                }
            }
        }

        if (showReminders) FeatureGate(
            entitlements = entitlements,
            plans = plans,
            featureKey = EntitlementKeys.AUTO_REMINDER_SMS,
            title = Str.automatic_appointment_reminders,
            description = Str.reminder_sms_is_sent_to,
            onUpgrade = onUpgrade
        ) {
            SettingCard(
                icon = Icons.Rounded.NotificationsActive,
                title = Str.automatic_appointment_reminders,
                description = Str.before_each_appointment_reminder_sms +
                    Str.set_text_timing_and_delivery
            ) {
                OutlinedButton(
                    onClick = onOpenReminders,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(Str.set_up_reminder_message)
                }
            }
        }
    }
}

/** A capability card: icon, name, one-line why, then its controls. */
@Composable
private fun SettingCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}

/** Pill-shaped segmented tabs, each with an icon. */
@Composable
private fun SegmentedTabs(
    tabs: List<Pair<androidx.compose.ui.graphics.vector.ImageVector, String>>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        tabs.forEachIndexed { index, (icon, label) ->
            val isSelected = index == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
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
