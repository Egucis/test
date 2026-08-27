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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import uk.co.cabcomply.app.data.security.AppLockManager
import uk.co.cabcomply.app.data.security.PinManager
import uk.co.cabcomply.app.ui.components.SectionCard
import javax.inject.Inject

@HiltViewModel
class SettingsSecurityViewModel @Inject constructor(
    val pinManager: PinManager,
    private val appLockManager: AppLockManager
) : ViewModel() {

    private val _refreshTick = MutableStateFlow(0)
    val refreshTick: StateFlow<Int> = _refreshTick

    fun setAppLockEnabled(enabled: Boolean) {
        if (enabled && !pinManager.isPinSet()) return
        pinManager.appLockEnabled = enabled
        appLockManager.clearLockIfProtectionDisabled()
        _refreshTick.value++
    }

    fun setRecordProtectionEnabled(enabled: Boolean) {
        if (enabled && !pinManager.isPinSet()) return
        pinManager.recordProtectionEnabled = enabled
        _refreshTick.value++
    }

    fun disableAllProtection() {
        pinManager.disablePinProtection()
        appLockManager.clearLockIfProtectionDisabled()
        _refreshTick.value++
    }
}

@Composable
fun SettingsSecurityScreen(
    onSetUpPin: () -> Unit,
    onChangePin: () -> Unit,
    viewModel: SettingsSecurityViewModel = hiltViewModel()
) {
    // pinManager's flags live in SharedPreferences, not Compose state; collecting refreshTick
    // forces this composable to recompose (and re-read them) whenever a toggle below changes one.
    viewModel.refreshTick.collectAsState()
    var showDisableConfirm by remember { mutableStateOf(false) }
    val pinManager = viewModel.pinManager

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("Security", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))

        SectionCard {
            Text("PIN protection is optional and off by default.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))

            if (!pinManager.isPinSet()) {
                TextButton(onClick = onSetUpPin) { Text("Set up a PIN") }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("Protect app with PIN", fontWeight = FontWeight.SemiBold)
                        Text("Require your PIN when returning to CabComply.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = pinManager.appLockEnabled, onCheckedChange = viewModel::setAppLockEnabled)
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("Protect records & sensitive actions", fontWeight = FontWeight.SemiBold)
                        Text("Require your PIN to edit records, delete data or leave Officer Mode.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = pinManager.recordProtectionEnabled, onCheckedChange = viewModel::setRecordProtectionEnabled)
                }
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onChangePin) { Text("Change PIN") }
                TextButton(onClick = { showDisableConfirm = true }) {
                    Text("Turn off PIN protection", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showDisableConfirm) {
        uk.co.cabcomply.app.ui.components.ConfirmDialog(
            title = "Turn off PIN protection?",
            message = "Your PIN will be removed and app/record protection will both be turned off. Your records are not affected.",
            confirmLabel = "Turn off",
            isDestructive = true,
            onConfirm = { viewModel.disableAllProtection(); showDisableConfirm = false },
            onDismiss = { showDisableConfirm = false }
        )
    }
}
