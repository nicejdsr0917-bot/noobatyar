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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
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
import xyz.sattar.javid.proqueue.core.prefs.PreferencesManager
import xyz.sattar.javid.proqueue.feature.aboutUs.AboutUsScreen
import xyz.sattar.javid.proqueue.feature.auth.LoginScreen
import xyz.sattar.javid.proqueue.feature.appointments.AppointmentDetailScreen
import xyz.sattar.javid.proqueue.feature.appointments.MyAppointmentsScreen
import xyz.sattar.javid.proqueue.feature.booking.BookingScreen
import xyz.sattar.javid.proqueue.feature.businessDetail.BusinessMapScreen
import xyz.sattar.javid.proqueue.feature.businessDetail.ClientBusinessDetailScreen
import xyz.sattar.javid.proqueue.feature.discovery.Hairline
import xyz.sattar.javid.proqueue.feature.discovery.LocalMyInsuranceSlug
import xyz.sattar.javid.proqueue.feature.explore.ExploreScreen
import xyz.sattar.javid.proqueue.feature.profile.ProfileScreen
import xyz.sattar.javid.proqueue.feature.profile.ProfileTabIcon
import xyz.sattar.javid.proqueue.feature.reviews.ReviewPrompt
import xyz.sattar.javid.proqueue.feature.profile.ProfileViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.compose.koinInject
import xyz.sattar.javid.proqueue.feature.stories.StoryCenter
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import xyz.sattar.javid.proqueue.core.location.IranCities
import xyz.sattar.javid.proqueue.core.prefs.ALL_IRAN
import xyz.sattar.javid.proqueue.core.ui.components.map.GeoPoint
import xyz.sattar.javid.proqueue.core.ui.components.map.MapTiles
import xyz.sattar.javid.proqueue.core.ui.components.map.prefetchTilesAround
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import xyz.sattar.javid.proqueue.feature.saved.SavedScreen

private val tabs = listOf(MainTab.Explore, MainTab.Appointments, MainTab.Saved, MainTab.More)

@Composable
fun MainNavHost() {
    val navController = rememberNavController()
    val myInsurance by PreferencesManager.myInsuranceSlug.collectAsState(initial = null)
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination

    val selectedTab = tabs.firstOrNull { tab ->
        destination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true
    }
    // Warms the tile cache around the browsed city (again whenever it changes),
    // a moment after launch so it never competes with the first screen.
    val cityCode by remember { PreferencesManager.recentCityCodes.map { it.firstOrNull() } }.collectAsState(initial = null)
    LaunchedEffect(cityCode) {
        delay(3_000)
        val city = cityCode?.takeIf { it != ALL_IRAN }?.let { IranCities.city(it)?.first }
        val center = city?.let { GeoPoint(it.lat, it.lng) } ?: MapTiles.DEFAULT_CENTER
        withContext(Dispatchers.Default) { prefetchTilesAround(center) }
    }

    // Shared by the profile tab and the tab bar's avatar, so both follow sign-in and photo changes.
    val profile: ProfileViewModel = koinViewModel()
    val profileState by profile.state.collectAsState()
    val selectTab: (MainTab) -> Unit = { tab ->
        navController.navigate(tab.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    val openDetail: (Long) -> Unit = { navController.navigate(AppScreens.ClientBusinessDetail(it)) }
    val signIn: () -> Unit = { navController.navigate(AppScreens.Login()) }
    // A Noobatyar story's "business:<id>" button opens that business's page.
    val stories = koinInject<StoryCenter>()
    LaunchedEffect(Unit) {
        stories.openBusiness.collect { id ->
            // Opened from that business's own page: just close the viewer, don't stack a copy.
            val here = navController.currentBackStackEntry
                ?.takeIf { it.destination.hasRoute<AppScreens.ClientBusinessDetail>() }
                ?.toRoute<AppScreens.ClientBusinessDetail>()?.businessId
            if (here != id) openDetail(id)
        }
    }
    // Browsing is open to everyone; booking needs an account, so a guest signs
    // in first and lands in the booking afterwards, as on the web.
    val book: (Long) -> Unit = { id ->
        if (PreferencesManager.getVisitorToken() != null) navController.navigate(AppScreens.Booking(id))
        else navController.navigate(AppScreens.Login(bookBusinessId = id))
    }

    // Surface (not a bare background) so LocalContentColor follows the theme:
    // without it every Text that doesn't pick a colour renders black, which is
    // invisible in dark mode.
    CompositionLocalProvider(LocalMyInsuranceSlug provides myInsurance) {
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
                    ProfileScreen(
                        profile = profile,
                        onOpenAbout = { navController.navigate(AppScreens.AboutUs) },
                        onSignIn = signIn,
                        onOpenAppointments = { selectTab(MainTab.Appointments) },
                        onOpenSaved = { selectTab(MainTab.Saved) },
                        onOpenFollowing = { navController.navigate(AppScreens.Following) },
                    )
                }
                composable<AppScreens.ClientBusinessDetail> { entry ->
                    val route = entry.toRoute<AppScreens.ClientBusinessDetail>()
                    ClientBusinessDetailScreen(
                        businessId = route.businessId,
                        openReviewWith = route.rate,
                        onNavigateBack = { navController.popBackStack() },
                        onBook = book,
                        onBookSlot = { id, date, slotAt ->
                            // A guest signs in first; the picked time is not carried through sign-in.
                            if (PreferencesManager.getVisitorToken() != null) navController.navigate(AppScreens.Booking(id, date = date, slotAt = slotAt))
                            else book(id)
                        },
                        onSignIn = signIn,
                        onOpenMap = { navController.navigate(AppScreens.BusinessMap(it)) },
                    )
                }
                composable<AppScreens.BusinessMap> { entry ->
                    BusinessMapScreen(
                        businessId = entry.toRoute<AppScreens.BusinessMap>().businessId,
                        onNavigateBack = { navController.popBackStack() },
                        onOpenBusiness = openDetail,
                    )
                }
                composable<AppScreens.Booking> { entry ->
                    val route = entry.toRoute<AppScreens.Booking>()
                    BookingScreen(
                        businessId = route.businessId,
                        resumeAppointmentId = route.resumeAppointmentId,
                        preDate = route.date,
                        preSlotAt = route.slotAt,
                        onNavigateBack = { navController.popBackStack() },
                        onOpenMyAppointments = {
                            // Booked: drop the finished flow first. Otherwise it is
                            // saved into the explore tab's stack and reopens there.
                            navController.popBackStack()
                            navController.navigate(AppScreens.MyAppointments) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                            }
                        }
                    )
                }
                composable<AppScreens.Login> { entry ->
                    val bookBusinessId = entry.toRoute<AppScreens.Login>().bookBusinessId
                    LoginScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onSignedIn = {
                            if (bookBusinessId > 0) {
                                navController.navigate(AppScreens.Booking(bookBusinessId)) {
                                    popUpTo<AppScreens.Login> { inclusive = true }
                                }
                            } else {
                                navController.popBackStack()
                            }
                        },
                    )
                }
                composable<AppScreens.MyAppointments> {
                    MyAppointmentsScreen(
                        onOpenBusiness = openDetail,
                        onSignIn = signIn,
                        bottomPadding = 0.dp,
                        onOpenAppointment = { navController.navigate(AppScreens.AppointmentDetail(it)) },
                        onCompletePayment = { businessId, id -> navController.navigate(AppScreens.Booking(businessId, id)) },
                    )
                }
                composable<AppScreens.AppointmentDetail> { entry ->
                    AppointmentDetailScreen(
                        appointmentId = entry.toRoute<AppScreens.AppointmentDetail>().appointmentId,
                        onNavigateBack = { navController.popBackStack() },
                        onOpenBusiness = openDetail,
                        onCompletePayment = { businessId, id -> navController.navigate(AppScreens.Booking(businessId, id)) },
                    )
                }
                composable<AppScreens.Following> {
                    xyz.sattar.javid.proqueue.feature.following.FollowingScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onOpenBusiness = openDetail,
                    )
                }
                composable<AppScreens.AboutUs> {
                    AboutUsScreen(onNavigateBack = { navController.popBackStack() })
                }
            }

            if (selectedTab != null) {
                ReviewPrompt(onRate = { businessId, stars ->
                    navController.navigate(AppScreens.ClientBusinessDetail(businessId, rate = stars))
                })
                TabBar(
                    selected = selectedTab,
                    avatarUrl = profileState.me?.avatar,
                    onSelect = { tab -> if (tab != selectedTab) selectTab(tab) }
                )
            }
        }
    }
    }
}

/**
 * Standard bottom navigation: attached to the edge, hairline on top, every
 * label always visible. The selected tab is told apart by the accent colour
 * and a small bounce of its icon; the profile tab shows the visitor's photo,
 * ringed when open, like Instagram.
 */
@Composable
private fun TabBar(selected: MainTab, avatarUrl: String?, onSelect: (MainTab) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.navigationBarsPadding()) {
            Hairline()
            Row(Modifier.fillMaxWidth().height(60.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                tabs.forEach { tab ->
                    val isSelected = tab == selected
                    val color by animateColorAsState(
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        tween(180),
                    )
                    Surface(
                        onClick = { onSelect(tab) },
                        color = MaterialTheme.colorScheme.background,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            // Pops when its tab is picked: shrinks a touch and springs back.
                            // When left it dips briefly instead; nothing moves on first show.
                            val pop = remember { Animatable(1f) }
                            var shown by remember { mutableStateOf(false) }
                            LaunchedEffect(isSelected) {
                                if (isSelected) {
                                    pop.snapTo(0.78f)
                                    pop.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMedium))
                                } else if (shown) {
                                    pop.animateTo(0.86f, tween(90))
                                    pop.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium))
                                }
                                shown = true
                            }
                            val iconModifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                            if (tab == MainTab.More && avatarUrl != null) {
                                ProfileTabIcon(avatarUrl, isSelected, iconModifier)
                            } else {
                                // Outline when idle, solid the moment it is picked.
                                Crossfade(isSelected, iconModifier, tween(160), label = "tab-icon") { solid ->
                                    if (solid) {
                                        SolidTabIcon(tab, color, MaterialTheme.colorScheme.background)
                                    } else {
                                        Icon(tab.iconUnSelected, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                                    }
                                }
                            }
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
