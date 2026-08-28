package uk.co.tripassistant.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import uk.co.tripassistant.app.ui.diagnostics.DiagnosticsScreen
import uk.co.tripassistant.app.ui.history.HistoryDetailScreen
import uk.co.tripassistant.app.ui.history.HistoryScreen
import uk.co.tripassistant.app.ui.home.HomeScreen
import uk.co.tripassistant.app.ui.onboarding.OnboardingScreen
import uk.co.tripassistant.app.ui.profiles.ProfileEditScreen
import uk.co.tripassistant.app.ui.profiles.ProfilesScreen
import uk.co.tripassistant.app.ui.settings.PrivacyScreen
import uk.co.tripassistant.app.ui.settings.SettingsScreen
import uk.co.tripassistant.app.ui.subscription.SubscriptionScreen
import uk.co.tripassistant.app.ui.tester.RuleTesterScreen

@Composable
fun TripAssistantNavGraph(
    navController: NavHostController,
    startDestination: String,
    onRequestScreenCapture: () -> Unit,
    onStopAssistant: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenOverlaySettings: () -> Unit
) {
    NavHost(navController = navController, startDestination = startDestination) {

        composable(Destinations.ONBOARDING) {
            OnboardingScreen(
                onFinished = {
                    navController.navigate(Destinations.HOME) {
                        popUpTo(Destinations.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        composable(Destinations.HOME) {
            HomeScreen(
                onNavigate = { navController.navigate(it) },
                onStartAssistant = onRequestScreenCapture,
                onStopAssistant = onStopAssistant,
                onRequestNotificationPermission = onRequestNotificationPermission,
                onOpenOverlaySettings = onOpenOverlaySettings
            )
        }

        composable(Destinations.SUBSCRIPTION) {
            SubscriptionScreen(onBack = { navController.popBackStack() })
        }

        composable(Destinations.PROFILES) {
            ProfilesScreen(
                onBack = { navController.popBackStack() },
                onEditProfile = { id -> navController.navigate(Destinations.profileEdit(id)) },
                onOpenRuleTester = { navController.navigate(Destinations.RULE_TESTER) }
            )
        }

        composable(
            route = Destinations.PROFILE_EDIT,
            arguments = listOf(navArgument("profileId") { type = NavType.StringType })
        ) {
            ProfileEditScreen(onBack = { navController.popBackStack() })
        }

        composable(Destinations.RULE_TESTER) {
            RuleTesterScreen(onBack = { navController.popBackStack() })
        }

        composable(Destinations.HISTORY) {
            HistoryScreen(
                onBack = { navController.popBackStack() },
                onOpenOffer = { id -> navController.navigate(Destinations.historyDetail(id)) }
            )
        }

        composable(
            route = Destinations.HISTORY_DETAIL,
            arguments = listOf(navArgument("offerId") { type = NavType.StringType })
        ) {
            HistoryDetailScreen(onBack = { navController.popBackStack() })
        }

        composable(Destinations.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenPrivacy = { navController.navigate(Destinations.PRIVACY) },
                onOpenDiagnostics = { navController.navigate(Destinations.DIAGNOSTICS) },
                onOpenSubscription = { navController.navigate(Destinations.SUBSCRIPTION) }
            )
        }

        composable(Destinations.PRIVACY) {
            PrivacyScreen(onDone = { navController.popBackStack() })
        }

        composable(Destinations.DIAGNOSTICS) {
            DiagnosticsScreen(onBack = { navController.popBackStack() })
        }
    }
}
