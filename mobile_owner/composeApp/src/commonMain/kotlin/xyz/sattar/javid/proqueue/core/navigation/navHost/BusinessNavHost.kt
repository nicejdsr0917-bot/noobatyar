package xyz.sattar.javid.proqueue.core.navigation.navHost

import xyz.sattar.javid.proqueue.core.navigation.BrowserBackBinding
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import xyz.sattar.javid.proqueue.core.navigation.AppScreens
import xyz.sattar.javid.proqueue.core.ui.components.AppScaffold
import xyz.sattar.javid.proqueue.core.ui.components.ContentWidth
import xyz.sattar.javid.proqueue.domain.model.business.Business
import xyz.sattar.javid.proqueue.feature.businessList.BusinessListScreen
import xyz.sattar.javid.proqueue.feature.createBusiness.CreateBusinessRoute
import xyz.sattar.javid.proqueue.feature.claimBusiness.ClaimBusinessScreen

import androidx.navigation.toRoute

@Composable
fun BusinessNavHost(
    onBusinessSelected: (Business) -> Unit,
    onNavigateToAuth: () -> Unit
) {
    val navController = rememberNavController()
    // Web: browser back pops a screen here instead of leaving the site.
    BrowserBackBinding(navController)

    // Capped at the host for the same reason as AuthNavHost: both screens
    // here are card lists / forms, not data-dense views.
    AppScaffold(maxWidth = ContentWidth.List) {
    NavHost(
        navController = navController,
        startDestination = AppScreens.BusinessList
    ) {
        composable<AppScreens.BusinessList> {
            BusinessListScreen(
                onNavigateToMain = { business ->
                    onBusinessSelected(business)
                },
                onNavigateToCreateBusiness = {
                    navController.navigate(AppScreens.CreateBusiness())
                },
                onNavigateToEditBusiness = { businessId ->
                    navController.navigate(AppScreens.CreateBusiness(businessId = businessId))
                },
                onNavigateToLogin = {
                    onNavigateToAuth()
                },
                onNavigateToClaim = {
                    navController.navigate(AppScreens.ClaimBusiness)
                }
            )
        }

        composable<AppScreens.ClaimBusiness> {
            ClaimBusinessScreen(
                onNavigateBack = { navController.popBackStack() },
                // Returning re-enters BusinessListScreen, whose ObserveBusinesses
                // refetch picks up the newly owned business.
                onFinished = { navController.popBackStack() }
            )
        }

        composable<AppScreens.CreateBusiness> { backStackEntry ->
            val args = backStackEntry.toRoute<AppScreens.CreateBusiness>()
            CreateBusinessRoute(
                businessId = args.businessId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onContinue = {
                    navController.popBackStack()
                }
            )
        }
    }
    }
}
