package uk.co.cabcomply.app.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import uk.co.cabcomply.app.data.backup.BackupManager
import uk.co.cabcomply.app.data.backup.BackupResult
import uk.co.cabcomply.app.data.backup.RestoreResult
import uk.co.cabcomply.app.ui.components.ConfirmDialog
import uk.co.cabcomply.app.ui.components.PrimaryActionButton
import uk.co.cabcomply.app.ui.components.SectionCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class BackupUiState(
    val isWorking: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
    val pendingRestoreUri: Uri? = null
)

@HiltViewModel
class SettingsBackupViewModel @Inject constructor(
    private val backupManager: BackupManager
) : ViewModel() {

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state

    fun createBackup(destination: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isWorking = true, message = null)
            when (val result = backupManager.createBackup(destination)) {
                is BackupResult.Success -> _state.value = BackupUiState(
                    message = "Backup created: ${result.summary.vehicleCount} vehicle(s), " +
                        "${result.summary.inspectionCount} check(s), ${result.summary.mileageCount} mileage entr(y/ies), " +
                        "${result.summary.documentCount} document(s), ${result.summary.photoCount} photo(s).",
                    isError = false
                )
                is BackupResult.Failure -> _state.value = BackupUiState(message = result.reason, isError = true)
            }
        }
    }

    fun requestRestore(source: Uri) { _state.value = _state.value.copy(pendingRestoreUri = source) }
    fun cancelRestore() { _state.value = _state.value.copy(pendingRestoreUri = null) }

    fun confirmRestore() {
        val uri = _state.value.pendingRestoreUri ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isWorking = true, message = null, pendingRestoreUri = null)
            when (val result = backupManager.restoreBackup(uri)) {
                is RestoreResult.Success -> _state.value = BackupUiState(
                    message = "Restore complete: ${result.summary.vehicleCount} vehicle(s), " +
                        "${result.summary.inspectionCount} check(s) restored.",
                    isError = false
                )
                is RestoreResult.Failure -> _state.value = BackupUiState(message = result.reason, isError = true)
            }
        }
    }
}

@Composable
fun SettingsBackupScreen(viewModel: SettingsBackupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val fileName = "cabcomply_backup_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.UK).format(Date())}.zip"

    val createLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { viewModel.createBackup(it) }
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.requestRestore(it) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("Backup & Data", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))

        SectionCard {
            Text("Create a backup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Includes your driver profile, vehicles, daily checks, defects, mileage, documents and photos. " +
                    "Save the file somewhere safe — CabComply does not upload it anywhere.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            PrimaryActionButton(text = "Create backup", onClick = { createLauncher.launch(fileName) }, enabled = !state.isWorking)
        }

        Spacer(Modifier.height(16.dp))

        SectionCard {
            Text("Restore from backup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Replaces all current CabComply data with the contents of the backup file. This cannot be undone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            PrimaryActionButton(text = "Choose backup file", onClick = { openLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }, enabled = !state.isWorking)
        }

        if (state.isWorking) {
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator()
        }
        state.message?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = if (state.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary)
        }
    }

    if (state.pendingRestoreUri != null) {
        ConfirmDialog(
            title = "Restore this backup?",
            message = "All current CabComply data on this device will be replaced with the contents of this backup. This cannot be undone.",
            confirmLabel = "Restore",
            isDestructive = true,
            onConfirm = viewModel::confirmRestore,
            onDismiss = viewModel::cancelRestore
        )
    }
}
