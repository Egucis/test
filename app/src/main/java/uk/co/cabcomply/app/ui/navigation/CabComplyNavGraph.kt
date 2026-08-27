package uk.co.cabcomply.app.ui.navigation

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import uk.co.cabcomply.app.ui.dailycheck.DailyCheckScreen
import uk.co.cabcomply.app.ui.defects.DefectDetailScreen
import uk.co.cabcomply.app.ui.defects.DefectsScreen
import uk.co.cabcomply.app.ui.documents.DocumentEditScreen
import uk.co.cabcomply.app.ui.documents.DocumentsScreen
import uk.co.cabcomply.app.ui.history.HistoryScreen
import uk.co.cabcomply.app.ui.history.InspectionDetailScreen
import uk.co.cabcomply.app.ui.home.HomeScreen
import uk.co.cabcomply.app.ui.lock.PinChangeScreen
import uk.co.cabcomply.app.ui.lock.PinLockScreen
import uk.co.cabcomply.app.ui.lock.PinSetupScreen
import uk.co.cabcomply.app.ui.mileage.MileageEditScreen
import uk.co.cabcomply.app.ui.mileage.MileageScreen
import uk.co.cabcomply.app.ui.officer.OfficerModeScreen
import uk.co.cabcomply.app.ui.onboarding.OnboardingDriverScreen
import uk.co.cabcomply.app.ui.onboarding.OnboardingFinishScreen
import uk.co.cabcomply.app.ui.onboarding.OnboardingSecurityScreen
import uk.co.cabcomply.app.ui.onboarding.OnboardingVehicleScreen
import uk.co.cabcomply.app.ui.onboarding.OnboardingWelcomeScreen
import uk.co.cabcomply.app.ui.reports.WeeklyReportScreen
import uk.co.cabcomply.app.ui.settings.SettingsAboutScreen
import uk.co.cabcomply.app.ui.settings.SettingsBackupScreen
import uk.co.cabcomply.app.ui.settings.SettingsDailyChecksScreen
import uk.co.cabcomply.app.ui.settings.SettingsDocumentsRemindersScreen
import uk.co.cabcomply.app.ui.settings.SettingsDriverScreen
import uk.co.cabcomply.app.ui.settings.SettingsScreen
import uk.co.cabcomply.app.ui.settings.SettingsSecurityScreen
import uk.co.cabcomply.app.ui.settings.SettingsSubscriptionScreen
import uk.co.cabcomply.app.ui.vehicles.VehicleEditScreen
import uk.co.cabcomply.app.ui.vehicles.VehiclesScreen

@Composable
fun CabComplyAppRoot(rootViewModel: AppRootViewModel = hiltViewModel()) {
    val needsOnboarding by rootViewModel.needsOnboarding.collectAsState()
    val isLocked by rootViewModel.appLockManager.isLocked.collectAsState()

    Box(Modifier.fillMaxSize()) {
        when (needsOnboarding) {
            null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            true -> CabComplyNavGraph(startDestination = Destinations.ONBOARDING_WELCOME)
            false -> CabComplyNavGraph(startDestination = Destinations.HOME)
        }
        if (isLocked) {
            PinLockScreen()
        }
    }
}

@Composable
private fun CabComplyNavGraph(startDestination: String) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val context = LocalContext.current

    Scaffold(
        bottomBar = {
            if (isBottomLevelRoute(currentRoute)) CabComplyBottomBar(navController)
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding).fillMaxSize()
        ) {
            // Onboarding
            composable(Destinations.ONBOARDING_WELCOME) {
                OnboardingWelcomeScreen(onContinue = { navController.navigate(Destinations.ONBOARDING_DRIVER) })
            }
            composable(Destinations.ONBOARDING_DRIVER) {
                OnboardingDriverScreen(
                    onNext = { navController.navigate(Destinations.ONBOARDING_VEHICLE) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Destinations.ONBOARDING_VEHICLE) {
                OnboardingVehicleScreen(
                    onNext = { navController.navigate(Destinations.ONBOARDING_SECURITY) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Destinations.ONBOARDING_SECURITY) {
                OnboardingSecurityScreen(
                    onNext = { navController.navigate(Destinations.ONBOARDING_FINISH) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Destinations.ONBOARDING_FINISH) {
                OnboardingFinishScreen(onFinish = {
                    navController.navigate(Destinations.HOME) { popUpTo(0) }
                })
            }

            // Home
            composable(Destinations.HOME) {
                HomeScreen(
                    onStartDailyCheck = { vehicleId -> navController.navigate(Destinations.dailyCheck(vehicleId)) },
                    onQuickCheck = { vehicleId -> navController.navigate(Destinations.dailyCheck(vehicleId, quick = true)) },
                    onOpenTodayCheck = { inspectionId -> navController.navigate(Destinations.inspectionDetail(inspectionId)) },
                    onNavigateMileage = { navController.navigate(Destinations.MILEAGE) },
                    onNavigateDefects = { navController.navigate(Destinations.DEFECTS) },
                    onNavigateDocuments = { navController.navigate(Destinations.DOCUMENTS) },
                    onNavigateHistory = { navController.navigate(Destinations.HISTORY) },
                    onNavigateOfficerMode = { navController.navigate(Destinations.OFFICER_MODE) },
                    onAddVehicle = { navController.navigate(Destinations.vehicleEdit()) }
                )
            }

            // Daily Check
            composable(
                Destinations.DAILY_CHECK,
                arguments = listOf(
                    navArgument("vehicleId") { type = NavType.StringType },
                    navArgument("quick") { type = NavType.StringType; defaultValue = "false" }
                )
            ) {
                DailyCheckScreen(
                    onDone = { inspectionId ->
                        Toast.makeText(context, "✓ Vehicle check completed", Toast.LENGTH_SHORT).show()
                        navController.popBackStack(Destinations.HOME, inclusive = false)
                    },
                    onCancel = { navController.popBackStack() },
                    onViewExisting = { inspectionId ->
                        navController.popBackStack()
                        navController.navigate(Destinations.inspectionDetail(inspectionId))
                    }
                )
            }

            // History
            composable(Destinations.HISTORY) {
                HistoryScreen(onOpenInspection = { navController.navigate(Destinations.inspectionDetail(it)) })
            }
            composable(
                Destinations.INSPECTION_DETAIL,
                arguments = listOf(navArgument("inspectionId") { type = NavType.StringType })
            ) {
                InspectionDetailScreen(
                    onBack = { navController.popBackStack() },
                    onOpenDefect = { navController.navigate(Destinations.defectDetail(it)) }
                )
            }

            // Mileage
            composable(Destinations.MILEAGE) {
                MileageScreen(
                    onAddEntry = { navController.navigate(Destinations.mileageEdit()) },
                    onOpenEntry = { navController.navigate(Destinations.mileageEdit(it)) }
                )
            }
            composable(
                Destinations.MILEAGE_EDIT,
                arguments = listOf(navArgument("entryId") { type = NavType.StringType; defaultValue = "" })
            ) {
                MileageEditScreen(onDone = { navController.popBackStack() }, onCancel = { navController.popBackStack() })
            }

            // Defects
            composable(Destinations.DEFECTS) {
                DefectsScreen(onOpenDefect = { navController.navigate(Destinations.defectDetail(it)) })
            }
            composable(
                Destinations.DEFECT_DETAIL,
                arguments = listOf(navArgument("defectId") { type = NavType.StringType })
            ) {
                DefectDetailScreen(onBack = { navController.popBackStack() })
            }

            // Documents
            composable(Destinations.DOCUMENTS) {
                DocumentsScreen(
                    onAddDocument = { ownerType, ownerId -> navController.navigate(Destinations.documentEdit(null, ownerType, ownerId)) },
                    onEditDocument = { documentId, ownerType, ownerId -> navController.navigate(Destinations.documentEdit(documentId, ownerType, ownerId)) }
                )
            }
            composable(
                Destinations.DOCUMENT_EDIT,
                arguments = listOf(
                    navArgument("documentId") { type = NavType.StringType; defaultValue = "" },
                    navArgument("ownerType") { type = NavType.StringType },
                    navArgument("ownerId") { type = NavType.StringType }
                )
            ) {
                DocumentEditScreen(onDone = { navController.popBackStack() }, onCancel = { navController.popBackStack() })
            }

            // Vehicles
            composable(Destinations.VEHICLES) {
                VehiclesScreen(
                    onAddVehicle = { navController.navigate(Destinations.vehicleEdit()) },
                    onEditVehicle = { navController.navigate(Destinations.vehicleEdit(it)) },
                    onUpgradeToPro = { navController.navigate(Destinations.SETTINGS_SUBSCRIPTION) }
                )
            }
            composable(
                Destinations.VEHICLE_EDIT,
                arguments = listOf(navArgument("vehicleId") { type = NavType.StringType; defaultValue = "" })
            ) {
                VehicleEditScreen(onDone = { navController.popBackStack() }, onCancel = { navController.popBackStack() })
            }

            // Weekly report
            composable(
                Destinations.WEEKLY_REPORT,
                arguments = listOf(navArgument("vehicleId") { type = NavType.StringType })
            ) {
                WeeklyReportScreen(onBack = { navController.popBackStack() })
            }

            // Officer mode
            composable(Destinations.OFFICER_MODE) {
                OfficerModeScreen(
                    onGenerateReport = { vehicleId -> navController.navigate(Destinations.weeklyReport(vehicleId)) },
                    onExit = { navController.popBackStack() }
                )
            }

            // Settings
            composable(Destinations.SETTINGS) {
                SettingsScreen(
                    onOpenDriver = { navController.navigate(Destinations.SETTINGS_DRIVER) },
                    onOpenVehicles = { navController.navigate(Destinations.VEHICLES) },
                    onOpenDailyChecks = { navController.navigate(Destinations.SETTINGS_DAILY_CHECKS) },
                    onOpenDocumentsReminders = { navController.navigate(Destinations.SETTINGS_DOCUMENTS_REMINDERS) },
                    onOpenSecurity = { navController.navigate(Destinations.SETTINGS_SECURITY) },
                    onOpenBackup = { navController.navigate(Destinations.SETTINGS_BACKUP) },
                    onOpenSubscription = { navController.navigate(Destinations.SETTINGS_SUBSCRIPTION) },
                    onOpenAbout = { navController.navigate(Destinations.SETTINGS_ABOUT) }
                )
            }
            composable(Destinations.SETTINGS_DRIVER) {
                SettingsDriverScreen(onSaved = { navController.popBackStack() })
            }
            composable(Destinations.SETTINGS_DAILY_CHECKS) { SettingsDailyChecksScreen() }
            composable(Destinations.SETTINGS_DOCUMENTS_REMINDERS) { SettingsDocumentsRemindersScreen() }
            composable(Destinations.SETTINGS_SECURITY) {
                SettingsSecurityScreen(
                    onSetUpPin = { navController.navigate(Destinations.PIN_SETUP) },
                    onChangePin = { navController.navigate(Destinations.PIN_CHANGE) }
                )
            }
            composable(Destinations.SETTINGS_BACKUP) { SettingsBackupScreen() }
            composable(Destinations.SETTINGS_SUBSCRIPTION) { SettingsSubscriptionScreen() }
            composable(Destinations.SETTINGS_ABOUT) { SettingsAboutScreen() }

            composable(Destinations.PIN_SETUP) {
                PinSetupScreen(onDone = { navController.popBackStack() }, onCancel = { navController.popBackStack() })
            }
            composable(Destinations.PIN_CHANGE) {
                PinChangeScreen(onDone = { navController.popBackStack() }, onCancel = { navController.popBackStack() })
            }
        }
    }
}
