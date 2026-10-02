package xyz.sattar.javid.proqueue.core.navigation.navHost

import xyz.sattar.javid.proqueue.core.navigation.BrowserBackBinding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import xyz.sattar.javid.proqueue.core.navigation.AppScreens
import xyz.sattar.javid.proqueue.core.prefs.PreferencesManager
import xyz.sattar.javid.proqueue.core.ui.LocalWindowSize
import xyz.sattar.javid.proqueue.core.ui.WindowSize
import xyz.sattar.javid.proqueue.feature.onboarding.OnboardingScreen
import xyz.sattar.javid.proqueue.feature.forgetPassword.resetPassword.ResetPasswordScreen
import xyz.sattar.javid.proqueue.feature.forgetPassword.sendOTP.SendOTPScreen
import xyz.sattar.javid.proqueue.feature.login.LoginScreen
import xyz.sattar.javid.proqueue.feature.register.RegisterScreen

@Composable
fun AuthNavHost(
    onNavigateToHome: () -> Unit,
    onRegisterComplete: () -> Unit
) {
    val navController = rememberNavController()
    // Web: browser back pops a screen here instead of leaving the site.
    BrowserBackBinding(navController)
    // The introduction is a phone-width screen only: a desktop browser goes
    // straight to the login. Read once — finishing the introduction navigates
    // away from it, and a recomposition (or a resized window) must not swap
    // the start destination underneath the graph.
    val isPhoneWidth = LocalWindowSize.current == WindowSize.Compact
    val startDestination: AppScreens = remember {
        if (isPhoneWidth && !PreferencesManager.hasSeenOnboarding()) AppScreens.OnBoarding else AppScreens.Login
    }
    // No width cap anywhere in this host. All four auth screens now build
    // their own full-viewport layout on web via AuthWebLayout; a cap here —
    // or per-destination, as three of them briefly had — squeezes that
    // two-column split into a 420dp strip pinned to the RTL start edge.
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable<AppScreens.OnBoarding> {
            OnboardingScreen(
                // Login goes on the stack under Register: RegisterScreen's
                // back press pops to Login, which would otherwise not exist.
                onRegister = {
                    navController.navigate(AppScreens.Login) {
                        popUpTo(AppScreens.OnBoarding) { inclusive = true }
                    }
                    navController.navigate(AppScreens.Register)
                },
                onLogin = {
                    navController.navigate(AppScreens.Login) {
                        popUpTo(AppScreens.OnBoarding) { inclusive = true }
                    }
                }
            )
        }
        composable<AppScreens.Register> {
            RegisterScreen(
                onRegisterComplete = onRegisterComplete,
                onBackPress = {
                    navController.popBackStack(
                        route = AppScreens.Login,
                        inclusive = false
                    )
                },
            )
        }
        composable<AppScreens.Login> {
            LoginScreen(
                onNavigateToHome = onNavigateToHome,
                onNavigateToForgetPassword = { phone ->
                    navController.navigate(AppScreens.SendOTP(phone))
                },
                onNavigateToRegister = {
                    navController.navigate(AppScreens.Register)
                },
            )
        }
        composable<AppScreens.SendOTP> {
            val args = it.toRoute<AppScreens.SendOTP>()
            SendOTPScreen(
                phone = args.phone,
                onNavigateToResetPassword = { phone, resetToken ->
                    navController.navigate(AppScreens.ResetPassword(phone, resetToken))
                },
                onNavigateToLogin = {
                    navController.popBackStack(
                        route = AppScreens.Login,
                        inclusive = false
                    )
                }
            )
        }

        composable<AppScreens.ResetPassword> {
            val args = it.toRoute<AppScreens.ResetPassword>()
            ResetPasswordScreen(phone = args.phone, resetToken = args.resetToken) {
                navController.popBackStack(
                    route = AppScreens.Login,
                    inclusive = false
                )
            }
        }
    }
}
