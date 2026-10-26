package xyz.sattar.javid.proqueue.core.navigation.navHost

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import xyz.sattar.javid.proqueue.feature.businessDetail.ClientBusinessDetailScreen
import xyz.sattar.javid.proqueue.feature.discovery.Brand
import xyz.sattar.javid.proqueue.feature.explore.ExploreScreen
import xyz.sattar.javid.proqueue.feature.more.MoreScreen
import xyz.sattar.javid.proqueue.feature.saved.SavedScreen
import xyz.sattar.javid.proqueue.ui.theme.LocalIsDarkTheme

private val tabs = listOf(MainTab.Explore, MainTab.Saved, MainTab.More)

/** Height the floating bar occupies, so tab screens can pad their last item clear of it. */
private val BottomBarClearance = 96.dp

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
    Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = AppScreens.Explore,
            modifier = Modifier.fillMaxSize()
        ) {
            composable<AppScreens.Explore> {
                ExploreScreen(onNavigateToDetail = openDetail, bottomPadding = BottomBarClearance)
            }
            composable<AppScreens.Saved> {
                SavedScreen(onNavigateToDetail = openDetail, bottomPadding = BottomBarClearance)
            }
            composable<AppScreens.More> {
                MoreScreen(
                    onOpenAbout = { navController.navigate(AppScreens.AboutUs) },
                    bottomPadding = BottomBarClearance
                )
            }
            composable<AppScreens.ClientBusinessDetail> { entry ->
                ClientBusinessDetailScreen(
                    businessId = entry.toRoute<AppScreens.ClientBusinessDetail>().businessId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable<AppScreens.AboutUs> {
                AboutUsScreen(onNavigateBack = { navController.popBackStack() })
            }
        }

        AnimatedVisibility(
            visible = selectedTab != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            FloatingTabBar(
                selected = selectedTab ?: MainTab.Explore,
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

@Composable
private fun FloatingTabBar(selected: MainTab, onSelect: (MainTab) -> Unit) {
    val isDark = LocalIsDarkTheme.current
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = if (isDark) Color(0xFF1F1B2E) else Color.White,
        shadowElevation = 12.dp,
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .fillMaxWidth()
            .height(64.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                val isSelected = tab == selected
                val bg by animateColorAsState(if (isSelected) Brand.Violet500.copy(alpha = 0.14f) else Color.Transparent)
                val fg by animateColorAsState(
                    if (isSelected) Brand.Violet500 else MaterialTheme.colorScheme.onSurfaceVariant
                )
                val spacing by animateDpAsState(if (isSelected) 6.dp else 0.dp)
                Surface(
                    onClick = { onSelect(tab) },
                    shape = RoundedCornerShape(20.dp),
                    color = bg,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (isSelected) tab.iconSelected else tab.iconUnSelected,
                            contentDescription = tab.title,
                            tint = fg,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(spacing))
                        AnimatedVisibility(visible = isSelected) {
                            Text(tab.title, color = fg, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
