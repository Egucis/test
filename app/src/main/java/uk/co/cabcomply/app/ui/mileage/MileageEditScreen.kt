package uk.co.cabcomply.app.ui.mileage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import uk.co.cabcomply.app.data.db.entity.MileagePurpose
import uk.co.cabcomply.app.data.repository.MileageRepository
import uk.co.cabcomply.app.data.repository.VehicleRepository
import uk.co.cabcomply.app.ui.components.DateField
import uk.co.cabcomply.app.ui.components.PrimaryActionButton
import uk.co.cabcomply.app.ui.components.SecondaryActionButton
import uk.co.cabcomply.app.ui.components.SectionCard
import uk.co.cabcomply.app.util.AppClock
import javax.inject.Inject

data class MileageEditUiState(
    val entryId: String? = null,
    val vehicleId: String = "",
    val startMileage: String = "",
    val endMileage: String = "",
    val entryDate: Long = System.currentTimeMillis(),
    val purpose: MileagePurpose = MileagePurpose.BUSINESS,
    val notes: String = "",
    val error: String? = null,
    val flagWarning: String? = null,
    val isSaved: Boolean = false
)

@HiltViewModel
class MileageEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vehicleRepository: VehicleRepository,
    private val mileageRepository: MileageRepository,
    private val clock: AppClock
) : ViewModel() {

    private val entryIdArg: String? = savedStateHandle.get<String>("entryId")?.ifBlank { null }
    private val _state = MutableStateFlow(MileageEditUiState(entryId = entryIdArg))
    val state: StateFlow<MileageEditUiState> = _state

    init {
        viewModelScope.launch {
            val vehicle = vehicleRepository.getActiveVehicle() ?: return@launch
            if (entryIdArg != null) {
                val existing = mileageRepository.getById(entryIdArg)
                if (existing != null) {
                    _state.value = MileageEditUiState(
                        entryId = existing.id,
                        vehicleId = existing.vehicleId,
                        startMileage = existing.startMileage.toString(),
                        endMileage = existing.endMileage?.toString().orEmpty(),
                        entryDate = existing.entryDate,
                        purpose = existing.purpose,
                        notes = existing.notes.orEmpty(),
                        flagWarning = existing.flagReason
                    )
                    return@launch
                }
            }
            val suggestedStart = mileageRepository.getSuggestedStartMileage(vehicle.id)
            _state.value = _state.value.copy(
                vehicleId = vehicle.id,
                startMileage = suggestedStart?.toString().orEmpty(),
                entryDate = clock.nowMillis()
            )
        }
    }

    fun onStartChange(v: String) { _state.value = _state.value.copy(startMileage = v.filter { it.isDigit() }, error = null) }
    fun onEndChange(v: String) { _state.value = _state.value.copy(endMileage = v.filter { it.isDigit() }, error = null) }
    fun onDateChange(v: Long?) { v?.let { _state.value = _state.value.copy(entryDate = it) } }
    fun onPurposeChange(v: MileagePurpose) { _state.value = _state.value.copy(purpose = v) }
    fun onNotesChange(v: String) { _state.value = _state.value.copy(notes = v) }

    fun save() {
        val s = _state.value
        val start = s.startMileage.toIntOrNull()
        if (start == null) {
            _state.value = s.copy(error = "Enter the start mileage before saving.")
            return
        }
        val end = s.endMileage.toIntOrNull()
        viewModelScope.launch {
            val saved = mileageRepository.saveEntry(
                id = s.entryId,
                vehicleId = s.vehicleId,
                startMileage = start,
                endMileage = end,
                entryDate = s.entryDate,
                startedAt = s.entryDate,
                endedAt = if (end != null) clock.nowMillis() else null,
                purpose = s.purpose,
                notes = s.notes.ifBlank { null }
            )
            _state.value = _state.value.copy(isSaved = true, flagWarning = saved.flagReason)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MileageEditScreen(
    onDone: () -> Unit,
    onCancel: () -> Unit,
    viewModel: MileageEditViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var purposeExpanded by remember { mutableStateOf(false) }

    if (state.isSaved && state.flagWarning == null) {
        onDone()
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            if (state.entryId == null) "Add mileage" else "Edit mileage",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(Modifier.height(20.dp))

        SectionCard {
            OutlinedTextField(
                value = state.startMileage,
                onValueChange = viewModel::onStartChange,
                label = { Text("Start mileage") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.endMileage,
                onValueChange = viewModel::onEndChange,
                label = { Text("End mileage (optional if trip in progress)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            DateField(label = "Date", valueMillis = state.entryDate, onValueChange = viewModel::onDateChange)
            Spacer(Modifier.height(12.dp))
            ExposedDropdownMenuBox(expanded = purposeExpanded, onExpandedChange = { purposeExpanded = it }) {
                OutlinedTextField(
                    value = state.purpose.name.lowercase().replaceFirstChar { it.uppercase() },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Purpose") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = purposeExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                )
                androidx.compose.material3.ExposedDropdownMenu(expanded = purposeExpanded, onDismissRequest = { purposeExpanded = false }) {
                    MileagePurpose.entries.forEach { purpose ->
                        DropdownMenuItem(
                            text = { Text(purpose.name.lowercase().replaceFirstChar { it.uppercase() }) },
                            onClick = { viewModel.onPurposeChange(purpose); purposeExpanded = false }
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::onNotesChange,
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        state.error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        state.flagWarning?.let {
            Spacer(Modifier.height(12.dp))
            Text("Review: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(24.dp))
        if (state.isSaved) {
            Text("Saved — this entry has been flagged for review; you can correct it here at any time.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            PrimaryActionButton(text = "Done", onClick = onDone)
        } else {
            PrimaryActionButton(text = "Save", onClick = viewModel::save)
            Spacer(Modifier.height(8.dp))
            SecondaryActionButton(text = "Cancel", onClick = onCancel)
        }
    }
}
