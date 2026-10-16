package xyz.sattar.javid.proqueue.feature.login

import xyz.sattar.javid.proqueue.core.i18n.Str
import xyz.sattar.javid.proqueue.core.ui.components.AuthHeaderIllustrationSize
import xyz.sattar.javid.proqueue.core.ui.components.BrandIllustration
import xyz.sattar.javid.proqueue.core.ui.components.LoginIllustration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhoneIphone
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import xyz.sattar.javid.proqueue.core.ui.components.AuthWebLayout
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import xyz.sattar.javid.proqueue.core.ui.LocalWindowSize
import xyz.sattar.javid.proqueue.core.ui.WindowSize
import xyz.sattar.javid.proqueue.core.ui.collectWithLifecycleAware
import xyz.sattar.javid.proqueue.core.ui.components.AppButton
import xyz.sattar.javid.proqueue.core.ui.components.AppTextField
import xyz.sattar.javid.proqueue.ui.theme.AppTheme
import xyz.sattar.javid.proqueue.core.ui.components.ToastyHost


@Composable
fun LoginScreen(
    viewModel: LoginViewModel = koinViewModel(),
    onNavigateToHome: () -> Unit,
    onNavigateToForgetPassword: (phone:String) -> Unit,
    onNavigateToRegister: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    HandleLoginEvents(
        events = viewModel.events,
        snackbarHostState = snackbarHostState,
        onNavigateToHome = onNavigateToHome,
        onNavigateToForgetPassword = onNavigateToForgetPassword,
        onNavigateToRegister = onNavigateToRegister,
    )

    LoginScreenContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onIntent = viewModel::sendIntent
    )
}

/**
 * Picks the layout by window width. Compact keeps the existing phone screen
 * untouched; anything wider gets the web card.
 */
@Composable
fun LoginScreenContent(
    modifier: Modifier = Modifier,
    uiState: LoginState,
    snackbarHostState: SnackbarHostState,
    onIntent: (LoginIntent) -> Unit
) {
    if (LocalWindowSize.current == WindowSize.Compact) {
        LoginPhoneContent(modifier, uiState, snackbarHostState, onIntent)
    } else {
        LoginWebContent(modifier, uiState, snackbarHostState, onIntent)
    }
}

/**
 * Phone layout — unchanged. A Scaffold whose bottomBar pins the actions to the
 * bottom of the viewport, which is right on a phone (thumb reach) and wrong on
 * a desktop browser, where it becomes a full-width bar floating at the bottom
 * of an otherwise empty page. See [LoginWebContent] for that case.
 */
@Composable
private fun LoginPhoneContent(
    modifier: Modifier = Modifier,
    uiState: LoginState,
    snackbarHostState: SnackbarHostState,
    onIntent: (LoginIntent) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // Just the product name: "ورود" here told every new
                    // visitor they were expected to log in already.
                    Text(
                        text = Str.about_us_app_name,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { ToastyHost(hostState = snackbarHostState) },
        bottomBar = {
            if (!uiState.isLoading) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.background,
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                            .navigationBarsPadding(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Registration lives in NoAccountCard above the
                        // fields, where it is seen first; a second button here
                        // only competed with "ورود".
                        AppButton(
                            text = Str.sign_in,
                            onClick = { onIntent(LoginIntent.Submit) },
                            isLoading = uiState.isLoading
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                LoginFormFields(uiState = uiState, onIntent = onIntent)

                // Extra space so the "فراموشی رمز" link and the register
                // hint under a login error are never hidden behind the
                // pinned bottomBar.
                Spacer(modifier = Modifier.height(120.dp))

                // Buttons moved to bottomBar
            }
        }
    }
}



/**
 * The form itself — icon, heading, both fields, the forgot-password link and
 * the error line. Shared by [LoginPhoneContent] and [LoginWebContent] so the
 * two layouts can differ in *chrome* (app bar, action placement, card) without
 * the fields themselves drifting apart.
 */
@Composable
private fun ColumnScope.LoginFormFields(
    uiState: LoginState,
    onIntent: (LoginIntent) -> Unit
) {
    BrandIllustration(size = AuthHeaderIllustrationSize) {
        LoginIllustration()
    }

    Text(
        text = Str.sign_in_to_your_account,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground
    )

    // Above the fields, not only as the last button: new owners kept asking
    // which username and password to enter, because the only way to register
    // was the lowest element on the page — and on a phone browser the
    // keyboard covers exactly that part once a field is focused.
    NoAccountCard(
        enabled = !uiState.isLoading,
        onClick = { onIntent(LoginIntent.Register) }
    )

    AppTextField(
        value = uiState.phone,
        onValueChange = { onIntent(LoginIntent.PhoneChanged(it)) },
        label = Str.mobile_number,
        isError = uiState.phoneError != null,
        errorMessage = uiState.phoneError,
        enabled = !uiState.isLoading,
        keyboardType = KeyboardType.Phone,
        maxLength = 11,
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.PhoneIphone,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )

    AppTextField(
        value = uiState.password,
        onValueChange = { onIntent(LoginIntent.PasswordChanged(it)) },
        label = Str.password,
        isError = uiState.passwordError != null,
        errorMessage = uiState.passwordError,
        enabled = !uiState.isLoading,
        keyboardType = KeyboardType.Password,
        maxLength = 100,
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )

    Text(
        modifier = Modifier.clickable {
            onIntent(LoginIntent.ForgetPassword(uiState.phone))
        },
        text = Str.forgot_password,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    if (uiState.loginError != null) {
        Text(
            text = uiState.loginError,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.fillMaxWidth()
        )
        // A new owner trying any password ends up here; point them to the
        // way in. Worded for everyone, so it doesn't reveal whether the
        // number has an account.
        Text(
            text = Str.not_registered_yet_sign_up,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = !uiState.isLoading) { onIntent(LoginIntent.Register) }
                .padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}

/** The "no account yet?" entry to registration, shown above the login fields. */
@Composable
private fun NoAccountCard(
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.PersonAdd,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = Str.dont_have_account,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = Str.to_get_started_sign_up,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = Str.sign_up_3,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** Web/desktop layout: the shared auth shell (see [AuthWebLayout]) plus this
 *  screen's own fields and actions. */
@Composable
private fun LoginWebContent(
    modifier: Modifier = Modifier,
    uiState: LoginState,
    snackbarHostState: SnackbarHostState,
    onIntent: (LoginIntent) -> Unit
) {
    AuthWebLayout(snackbarHostState = snackbarHostState, modifier = modifier) {
        LoginFormFields(uiState = uiState, onIntent = onIntent)

        Spacer(modifier = Modifier.height(8.dp))

        AppButton(
            text = Str.sign_in,
            onClick = { onIntent(LoginIntent.Submit) },
            isLoading = uiState.isLoading
        )
    }
}

@Composable
fun HandleLoginEvents(
    events: Flow<LoginEvent>,
    snackbarHostState: SnackbarHostState,
    onNavigateToHome: () -> Unit,
    onNavigateToForgetPassword: (phone:String) -> Unit,
    onNavigateToRegister: () -> Unit,
) {
    events.collectWithLifecycleAware { event ->
        when (event) {
            LoginEvent.NavigateToHome -> onNavigateToHome()
            is LoginEvent.NavigateToForgetPassword -> onNavigateToForgetPassword(event.phone)
            LoginEvent.NavigateToRegister -> onNavigateToRegister()
            is LoginEvent.ShowToast -> snackbarHostState.showSnackbar(event.message)
        }
    }
}

@Preview
@Composable
fun PreviewLoginScreen() {
    AppTheme {
        LoginScreenContent(
            uiState = LoginState(),
            snackbarHostState = remember { SnackbarHostState() },
            onIntent = {}
        )
    }
}
