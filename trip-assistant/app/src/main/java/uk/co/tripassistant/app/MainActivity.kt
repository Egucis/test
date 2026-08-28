package uk.co.tripassistant.app

import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import uk.co.tripassistant.app.data.prefs.ThemeMode
import uk.co.tripassistant.app.service.AssistantService
import uk.co.tripassistant.app.ui.navigation.AppRootViewModel
import uk.co.tripassistant.app.ui.navigation.Destinations
import uk.co.tripassistant.app.ui.navigation.TripAssistantNavGraph
import uk.co.tripassistant.app.ui.theme.TripAssistantTheme
import uk.co.tripassistant.app.util.OverlayPermission

/**
 * The app's single Activity.
 *
 * It owns one thing the rest of the app cannot do for itself: asking Android for screen-capture
 * consent. That dialog belongs to the system and must be answered by the user for every capture
 * session — the result is handed straight to the foreground service and never stored
 * (spec section 9).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TripAssistantApp() }
    }

    @Composable
    private fun TripAssistantApp() {
        val viewModel: AppRootViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        val context = LocalContext.current

        val projectionManager = remember {
            context.getSystemService(MediaProjectionManager::class.java)
        }

        val projectionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val data = result.data
            if (result.resultCode == RESULT_OK && data != null) {
                ContextCompat.startForegroundService(
                    context,
                    AssistantService.startIntent(context, result.resultCode, data)
                )
            }
        }

        val notificationLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { /* Home re-reads the permission state when it resumes. */ }

        TripAssistantTheme(themeMode = settings?.themeMode ?: ThemeMode.SYSTEM) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                val current = settings
                if (current == null) {
                    // One frame at most, while DataStore is read.
                    Box(modifier = Modifier.fillMaxSize())
                } else {
                    val navController = rememberNavController()
                    TripAssistantNavGraph(
                        navController = navController,
                        startDestination = if (current.onboardingComplete) {
                            Destinations.HOME
                        } else {
                            Destinations.ONBOARDING
                        },
                        onRequestScreenCapture = {
                            val intent = projectionManager?.createScreenCaptureIntent()
                            if (intent != null) projectionLauncher.launch(intent)
                        },
                        onStopAssistant = {
                            context.startService(AssistantService.stopIntent(context))
                        },
                        onRequestNotificationPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        onOpenOverlaySettings = {
                            if (!OverlayPermission.openSettings(context)) {
                                Toast.makeText(
                                    context,
                                    "Could not open Android's settings. Find Trip Assistant under " +
                                        "Settings › Apps › Special app access › Display over other apps.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    )
                }
            }
        }
    }
}
