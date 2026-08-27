package uk.co.cabcomply.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState

private data class BottomTab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val bottomTabs = listOf(
    BottomTab(Destinations.HOME, "Home", Icons.Filled.Home),
    BottomTab(Destinations.HISTORY, "History", Icons.Filled.CalendarMonth),
    BottomTab(Destinations.MILEAGE, "Mileage", Icons.Filled.DirectionsCar),
    BottomTab(Destinations.DOCUMENTS, "Documents", Icons.Filled.Folder),
    BottomTab(Destinations.SETTINGS, "Settings", Icons.Filled.Settings)
)

/** The five permanent bottom-navigation destinations (product spec section 6): everything else is reached from these. */
@Composable
fun CabComplyBottomBar(navController: NavController) {
    val backStackEntry = navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry.value?.destination

    NavigationBar {
        bottomTabs.forEach { tab ->
            val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(tab.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) }
            )
        }
    }
}

fun isBottomLevelRoute(route: String?): Boolean = bottomTabs.any { it.route == route }
