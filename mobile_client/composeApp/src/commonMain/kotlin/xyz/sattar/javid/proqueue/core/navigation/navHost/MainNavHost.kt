package xyz.sattar.javid.proqueue.core.navigation.navHost

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import xyz.sattar.javid.proqueue.core.navigation.AppScreens
import xyz.sattar.javid.proqueue.core.navigation.MainTab
import xyz.sattar.javid.proqueue.feature.aboutUs.AboutUsScreen
import xyz.sattar.javid.proqueue.feature.appointments.MyAppointmentsScreen
import xyz.sattar.javid.proqueue.feature.booking.BookingScreen
import xyz.sattar.javid.proqueue.feature.businessDetail.ClientBusinessDetailScreen
import xyz.sattar.javid.proqueue.feature.discovery.Hairline
import xyz.sattar.javid.proqueue.feature.explore.ExploreScreen
import xyz.sattar.javid.proqueue.feature.more.MoreScreen
import xyz.sattar.javid.proqueue.feature.saved.SavedScreen

private val tabs = listOf(MainTab.Explore, MainTab.Appointments, MainTab.Saved, MainTab.More)

@Composable
fun MainNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination

    val selectedTab = tabs.firstOrNull { tab ->
        destination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true
    }
    val openDetail: (Long) -> Unit = { navController.navigate(AppScreens.ClientBusinessDetail(it)) }

    // Surface (not a bare background) so LocalContentColor follows the theme:
    // without it every Text that doesn't pick a colour renders black, which is
    // invisible in dark mode.
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = AppScreens.Explore,
                modifier = Modifier.weight(1f)
            ) {
                composable<AppScreens.Explore> {
                    ExploreScreen(onNavigateToDetail = openDetail, bottomPadding = 0.dp)
                }
                composable<AppScreens.Saved> {
                    SavedScreen(onNavigateToDetail = openDetail, bottomPadding = 0.dp)
                }
                composable<AppScreens.More> {
                    MoreScreen(onOpenAbout = { navController.navigate(AppScreens.AboutUs) }, bottomPadding = 0.dp)
                }
                composable<AppScreens.ClientBusinessDetail> { entry ->
                    ClientBusinessDetailScreen(
                        businessId = entry.toRoute<AppScreens.ClientBusinessDetail>().businessId,
                        onNavigateBack = { navController.popBackStack() },
                        onBook = { navController.navigate(AppScreens.Booking(it)) }
                    )
                }
                composable<AppScreens.Booking> { entry ->
                    BookingScreen(
                        businessId = entry.toRoute<AppScreens.Booking>().businessId,
                        onNavigateBack = { navController.popBackStack() },
                        onOpenMyAppointments = {
                            // Booked: leave the flow for the tab, so back does not reopen it.
                            navController.navigate(AppScreens.MyAppointments) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                            }
                        }
                    )
                }
                composable<AppScreens.MyAppointments> {
                    MyAppointmentsScreen(onOpenBusiness = openDetail, bottomPadding = 0.dp)
                }
                composable<AppScreens.AboutUs> {
                    AboutUsScreen(onNavigateBack = { navController.popBackStack() })
                }
            }

            if (selectedTab != null) {
                TabBar(
                    selected = selectedTab,
                    onSelect = { tab ->
                        if (tab != selectedTab) {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
        }
    }
}

/**
 * Standard bottom navigation: attached to the edge, hairline on top, every
 * label always visible. The selected tab is told apart by the filled icon and
 * the accent colour — no pill, no shadow, nothing floating.
 */
@Composable
private fun TabBar(selected: MainTab, onSelect: (MainTab) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.navigationBarsPadding()) {
            Hairline()
            Row(Modifier.fillMaxWidth().height(60.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                tabs.forEach { tab ->
                    val isSelected = tab == selected
                    val color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    Surface(
                        onClick = { onSelect(tab) },
                        color = MaterialTheme.colorScheme.background,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                if (isSelected) tab.iconSelected else tab.iconUnSelected,
                                contentDescription = null,
                                tint = color,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                tab.title,
                                color = color,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}
