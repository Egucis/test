package uk.co.cabcomply.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import uk.co.cabcomply.app.data.notifications.NotificationPreferences
import uk.co.cabcomply.app.data.notifications.NotificationScheduler
import uk.co.cabcomply.app.ui.components.SectionCard
import javax.inject.Inject

@HiltViewModel
class SettingsDocumentsRemindersViewModel @Inject constructor(
    private val notificationPreferences: NotificationPreferences,
    private val notificationScheduler: NotificationScheduler
) : ViewModel() {

    val remindersEnabled: StateFlow<Boolean> = notificationPreferences.remindersEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch {
            notificationPreferences.setRemindersEnabled(enabled)
            if (enabled) notificationScheduler.ensureScheduled() else notificationScheduler.cancel()
        }
    }
}

@Composable
fun SettingsDocumentsRemindersScreen(viewModel: SettingsDocumentsRemindersViewModel = hiltViewModel()) {
    val enabled by viewModel.remindersEnabled.collectAsState()

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { /* If denied, reminders are simply not shown; no crash and no repeated prompting. */ }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("Documents & Reminders", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Expiry reminders", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Get notified 30, 14, 7 and 1 day(s) before a document with reminders turned on expires.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = enabled, onCheckedChange = { checked ->
                    viewModel.setRemindersEnabled(checked)
                    if (checked && android.os.Build.VERSION.SDK_INT >= 33) {
                        permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                })
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "You can also turn reminders on or off for each individual document.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
