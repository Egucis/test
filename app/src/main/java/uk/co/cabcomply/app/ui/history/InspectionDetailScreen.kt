package uk.co.cabcomply.app.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import uk.co.cabcomply.app.data.db.entity.DefectEntity
import uk.co.cabcomply.app.data.db.entity.DefectStatus
import uk.co.cabcomply.app.data.db.entity.InspectionEntity
import uk.co.cabcomply.app.data.db.entity.InspectionResultEntity
import uk.co.cabcomply.app.data.db.entity.InspectionResultStatus
import uk.co.cabcomply.app.data.repository.DefectRepository
import uk.co.cabcomply.app.data.repository.InspectionRepository
import uk.co.cabcomply.app.data.security.PinManager
import uk.co.cabcomply.app.ui.components.PinChallengeDialog
import uk.co.cabcomply.app.ui.components.SectionCard
import uk.co.cabcomply.app.ui.components.StatusChip
import uk.co.cabcomply.app.ui.components.StatusTone
import uk.co.cabcomply.app.util.DateFormatting
import javax.inject.Inject

data class InspectionDetailUiState(
    val inspection: InspectionEntity? = null,
    val results: List<InspectionResultEntity> = emptyList(),
    val defects: List<DefectEntity> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class InspectionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val inspectionRepository: InspectionRepository,
    private val defectRepository: DefectRepository,
    val pinManager: PinManager
) : ViewModel() {

    private val inspectionId: String = savedStateHandle.get<String>("inspectionId").orEmpty()
    private val _state = MutableStateFlow(InspectionDetailUiState())
    val state: StateFlow<InspectionDetailUiState> = _state

    init {
        viewModelScope.launch {
            val inspection = inspectionRepository.getById(inspectionId)
            val results = inspectionRepository.getResults(inspectionId)
            _state.value = _state.value.copy(inspection = inspection, results = results)
        }
        // Reactive so resolving a defect (from here or the separate Defects screen) shows up
        // immediately without needing to leave and re-enter this screen.
        viewModelScope.launch {
            defectRepository.observeForInspection(inspectionId).collect { defects ->
                _state.value = _state.value.copy(defects = defects, isLoading = false)
            }
        }
    }

    fun amend(notes: String, reason: String, onDone: () -> Unit) {
        viewModelScope.launch {
            inspectionRepository.amendCompletedInspection(inspectionId, notes.ifBlank { null }, reason)
            _state.value = _state.value.copy(inspection = inspectionRepository.getById(inspectionId))
            onDone()
        }
    }
}

@Composable
fun InspectionDetailScreen(
    onBack: () -> Unit,
    onOpenDefect: (String) -> Unit,
    viewModel: InspectionDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showAmendDialog by remember { mutableStateOf(false) }
    var pinChallenge by remember { mutableStateOf(false) }

    val inspection = state.inspection
    if (state.isLoading || inspection == null) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Loading…")
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Vehicle Check Record", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(DateFormatting.formatDate(inspection.inspectionDate), style = MaterialTheme.typography.bodyMedium)
                }
                IconButton(onClick = {
                    if (viewModel.pinManager.recordProtectionEnabled) pinChallenge = true else showAmendDialog = true
                }) {
                    Icon(Icons.Filled.Edit, contentDescription = "Amend record")
                }
            }
        }
        item {
            SectionCard {
                DetailRow("Vehicle", "${inspection.vehicleRegistrationSnapshot}")
                DetailRow("Driver", inspection.driverNameSnapshot)
                DetailRow("Licensing authority", inspection.licensingAuthorityNameSnapshot ?: "Not set")
                DetailRow("Checklist", "${inspection.checklistNameSnapshot} (v${inspection.checklistVersionSnapshot})")
                DetailRow("Completed", inspection.completedAt?.let { DateFormatting.formatDateTime(it) } ?: "—")
                DetailRow("Mileage", "${inspection.odometer} miles")
                if (inspection.isQuickCheck) DetailRow("Method", "Quick Check")
                if (inspection.modifiedAt != null) {
                    DetailRow("Amended", "${DateFormatting.formatDateTime(inspection.modifiedAt)} — ${inspection.modificationReason ?: ""}")
                }
            }
        }
        item {
            SectionCard {
                Text("Checklist results", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                state.results.groupBy { it.categorySnapshot }.forEach { (category, items) ->
                    Text(category, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    items.forEach { result ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                            when (result.status) {
                                InspectionResultStatus.DEFECT -> Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                InspectionResultStatus.NOT_APPLICABLE -> Icon(Icons.Filled.RemoveCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                InspectionResultStatus.OK -> Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(result.itemNameSnapshot, style = MaterialTheme.typography.bodyMedium)
                            if (result.status == InspectionResultStatus.NOT_APPLICABLE) {
                                Spacer(Modifier.width(6.dp))
                                Text("(N/A)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        if (state.defects.isNotEmpty()) {
            item {
                SectionCard {
                    Text("Defects", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Tap a defect to add a resolution note or mark it fixed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    state.defects.forEach { defect ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenDefect(defect.id) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(defect.checklistItemNameSnapshot, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(defect.description, style = MaterialTheme.typography.bodyMedium)
                            }
                            Spacer(Modifier.width(8.dp))
                            StatusChip(
                                text = if (defect.status == DefectStatus.RESOLVED) "Resolved" else "Open",
                                tone = if (defect.status == DefectStatus.RESOLVED) StatusTone.SUCCESS else StatusTone.DANGER
                            )
                        }
                    }
                }
            }
        }
        item {
            SectionCard {
                Text("Driver confirmation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Confirmed by ${inspection.driverNameSnapshot} at " +
                        (inspection.confirmationTimestamp?.let { DateFormatting.formatDateTime(it) } ?: "—"),
                    style = MaterialTheme.typography.bodyMedium
                )
                inspection.notes?.let {
                    Spacer(Modifier.height(8.dp))
                    Text("Notes: $it", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }

    if (pinChallenge) {
        PinChallengeDialog(
            pinManager = viewModel.pinManager,
            onSuccess = { pinChallenge = false; showAmendDialog = true },
            onCancel = { pinChallenge = false }
        )
    }

    if (showAmendDialog) {
        AmendDialog(
            initialNotes = inspection.notes.orEmpty(),
            onDismiss = { showAmendDialog = false },
            onConfirm = { notes, reason -> viewModel.amend(notes, reason) { showAmendDialog = false } }
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun AmendDialog(initialNotes: String, onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var notes by remember { mutableStateOf(initialNotes) }
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Amend this record") },
        text = {
            Column {
                Text("This is a completed compliance record. Amending it is logged with a timestamp and reason.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Reason for amending") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(notes, reason) }, enabled = reason.isNotBlank()) { Text("Save amendment") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
