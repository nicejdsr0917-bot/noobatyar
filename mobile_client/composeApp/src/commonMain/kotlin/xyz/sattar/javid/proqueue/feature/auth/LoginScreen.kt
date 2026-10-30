package xyz.sattar.javid.proqueue.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import org.koin.compose.viewmodel.koinViewModel
import xyz.sattar.javid.proqueue.core.ui.components.ToastyHost
import xyz.sattar.javid.proqueue.core.ui.components.UiMessage
import xyz.sattar.javid.proqueue.core.ui.components.showToasty
import xyz.sattar.javid.proqueue.core.utils.SystemBackHandler
import xyz.sattar.javid.proqueue.core.utils.rememberReducedMotion
import xyz.sattar.javid.proqueue.feature.booking.LocalReducedMotion
import xyz.sattar.javid.proqueue.feature.booking.StepScaffold
import xyz.sattar.javid.proqueue.feature.booking.StepTitle
import xyz.sattar.javid.proqueue.feature.booking.enterStagger
import xyz.sattar.javid.proqueue.feature.booking.ltrInput
import xyz.sattar.javid.proqueue.feature.booking.rememberAutoFocus
import xyz.sattar.javid.proqueue.feature.businessDetail.TopBar
import xyz.sattar.javid.proqueue.feature.discovery.Radius
import xyz.sattar.javid.proqueue.feature.discovery.toPersianDigits

/**
 * Sign in or sign up. Browsing needs no account; booking, and the phone and
 * address of centres that keep them for signed-in customers, do. [onSignedIn]
 * runs once the session is stored.
 */
@Composable
fun LoginScreen(
    onNavigateBack: () -> Unit,
    onSignedIn: () -> Unit,
    viewModel: LoginViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(state.signedIn) { if (state.signedIn) onSignedIn() }
    LaunchedEffect(Unit) {
        viewModel.errors.collect {
            haptic.performHapticFeedback(HapticFeedbackType.Reject)
            snackbar.showToasty(UiMessage.error(it))
        }
    }
    val goBack = { if (!viewModel.back()) onNavigateBack() }
    SystemBackHandler(onBack = goBack)

    CompositionLocalProvider(LocalReducedMotion provides rememberReducedMotion()) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Column(Modifier.fillMaxSize()) {
                TopBar(title = "ورود یا ثبت‌نام", showDivider = false, onBack = goBack)
                when (state.step) {
                    LoginStep.PHONE -> PhoneStep(state, viewModel)
                    LoginStep.CODE -> CodeStep(state, viewModel)
                    LoginStep.NAME -> NameStep(state, viewModel)
                }
            }
            ToastyHost(hostState = snackbar)
        }
    }
}

@Composable
private fun PhoneStep(state: LoginState, vm: LoginViewModel) {
    val focus = rememberAutoFocus()
    val wrong = state.phone.length == 11 && !vm.isPhoneValid
    StepScaffold(action = "دریافت کد تأیید", actionEnabled = vm.isPhoneValid, busy = state.busy, onAction = vm::sendCode) {
        StepTitle("شمارهٔ موبایل خود را وارد کنید", "کد تأیید برایتان پیامک می‌شود؛ اگر حساب ندارید، همین‌جا ساخته می‌شود")
        OutlinedTextField(
            value = state.phone,
            onValueChange = vm::setPhone,
            placeholder = { Text("۰۹۱۲ ۰۰۰ ۰۰۰۰", style = ltrInput, modifier = Modifier.fillMaxWidth()) },
            singleLine = true,
            isError = wrong,
            supportingText = if (wrong) ({ Text("شماره باید با ۰۹ شروع شود و ۱۱ رقم باشد") }) else null,
            textStyle = ltrInput,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { vm.sendCode() }),
            shape = Radius.md,
            modifier = Modifier.fillMaxWidth().focusRequester(focus).enterStagger(1),
        )
        Text(
            "با ادامه دادن، شرایط استفاده و حریم خصوصی نوبت‌یار را می‌پذیرید.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().enterStagger(2),
        )
    }
}

@Composable
private fun CodeStep(state: LoginState, vm: LoginViewModel) {
    val focus = rememberAutoFocus()
    StepScaffold(
        action = "تأیید و ورود",
        actionEnabled = state.code.length == OTP_LENGTH,
        busy = state.busy,
        onAction = vm::verifyCode,
    ) {
        StepTitle("کد تأیید", "کد ${OTP_LENGTH.toString().toPersianDigits()} رقمی که به ${state.phone.toPersianDigits()} پیامک شد")
        OutlinedTextField(
            value = state.code,
            onValueChange = vm::setCode,
            singleLine = true,
            textStyle = ltrInput.copy(fontSize = 26.sp, letterSpacing = 10.sp, fontWeight = FontWeight.Bold),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { vm.verifyCode() }),
            shape = Radius.md,
            modifier = Modifier.fillMaxWidth().focusRequester(focus).enterStagger(1),
        )
        Row(Modifier.enterStagger(2), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = vm::resend, enabled = !state.busy && state.resendIn == 0) {
                Text(
                    if (state.resendIn > 0) "ارسال دوباره تا ${state.resendIn.toString().toPersianDigits()} ثانیه دیگر"
                    else "ارسال دوبارهٔ کد"
                )
            }
            TextButton(onClick = { vm.back() }, enabled = !state.busy) { Text("تغییر شماره") }
        }
    }
}

@Composable
private fun NameStep(state: LoginState, vm: LoginViewModel) {
    val focus = rememberAutoFocus()
    StepScaffold(action = "ثبت‌نام و ورود", actionEnabled = state.name.isNotBlank(), busy = state.busy, onAction = vm::register) {
        StepTitle("خوش آمدید", "نام خود را بنویسید؛ مرکز با این نام نوبت شما را می‌بیند")
        OutlinedTextField(
            value = state.name,
            onValueChange = vm::setName,
            singleLine = true,
            placeholder = { Text("نام و نام خانوادگی") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { vm.register() }),
            shape = Radius.md,
            modifier = Modifier.fillMaxWidth().focusRequester(focus).enterStagger(1),
        )
    }
}
