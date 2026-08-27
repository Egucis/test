package uk.co.cabcomply.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uk.co.cabcomply.app.data.backup.BackupManager
import uk.co.cabcomply.app.data.backup.BackupResult
import uk.co.cabcomply.app.data.backup.CloudBackupPrefs
import uk.co.cabcomply.app.data.backup.CloudBackupScheduler
import uk.co.cabcomply.app.data.backup.CloudBackupSettings
import uk.co.cabcomply.app.data.backup.RestoreResult
import uk.co.cabcomply.app.ui.components.ConfirmDialog
import uk.co.cabcomply.app.ui.components.PrimaryActionButton
import uk.co.cabcomply.app.ui.components.SectionCard
import uk.co.cabcomply.app.util.DateFormatting
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
    private val backupManager: BackupManager,
    private val cloudBackupPrefs: CloudBackupPrefs,
    private val cloudBackupScheduler: CloudBackupScheduler
) : ViewModel() {

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state

    val cloudState: StateFlow<CloudBackupSettings> = cloudBackupPrefs.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CloudBackupSettings())

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

    /** The driver just granted access to a folder via the system picker - turn automatic backup on for it. */
    fun onFolderChosen(treeUri: Uri) {
        viewModelScope.launch {
            cloudBackupPrefs.setFolder(enabled = true, treeUriString = treeUri.toString())
            cloudBackupScheduler.ensureScheduled()
        }
    }

    fun setAutomaticBackupEnabled(enabled: Boolean) {
        viewModelScope.launch {
            cloudBackupPrefs.setEnabled(enabled)
            if (enabled) cloudBackupScheduler.ensureScheduled() else cloudBackupScheduler.cancel()
        }
    }

    fun backUpNow() = cloudBackupScheduler.runOnce()
}

@Composable
fun SettingsBackupScreen(viewModel: SettingsBackupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val cloudState by viewModel.cloudState.collectAsState()
    val context = LocalContext.current
    val fileName = "cabcomply_backup_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.UK).format(Date())}.zip"

    val createLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { viewModel.createBackup(it) }
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.requestRestore(it) }
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            viewModel.onFolderChosen(it)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
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
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Automatic backup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Switch(
                    checked = cloudState.enabled,
                    onCheckedChange = { enabled ->
                        if (enabled && cloudState.treeUri == null) {
                            folderLauncher.launch(null)
                        } else {
                            viewModel.setAutomaticBackupEnabled(enabled)
                        }
                    }
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Once a day, CabComply writes a dated backup into a folder you choose (e.g. a synced Drive/OneDrive " +
                    "folder), on top of any manual backups you create yourself.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (cloudState.enabled) {
                Spacer(Modifier.height(10.dp))
                Text(
                    cloudState.lastSuccessAtMillis?.let { "Last automatic backup: ${DateFormatting.formatDateTime(it)}" }
                        ?: "No automatic backup has run yet.",
                    style = MaterialTheme.typography.bodySmall
                )
                cloudState.lastError?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryActionButton(
                        text = "Change folder",
                        onClick = { folderLauncher.launch(null) },
                        modifier = Modifier.weight(1f)
                    )
                    PrimaryActionButton(
                        text = "Back up now",
                        onClick = viewModel::backUpNow,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
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
