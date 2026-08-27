package uk.co.cabcomply.app.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import uk.co.cabcomply.app.data.db.entity.InspectionEntity
import uk.co.cabcomply.app.data.db.entity.VehicleEntity
import uk.co.cabcomply.app.data.repository.DefectRepository
import uk.co.cabcomply.app.data.repository.InspectionRepository
import uk.co.cabcomply.app.data.repository.VehicleRepository
import uk.co.cabcomply.app.ui.components.EmptyState
import uk.co.cabcomply.app.ui.components.SectionCard
import uk.co.cabcomply.app.ui.components.StatusChip
import uk.co.cabcomply.app.ui.components.StatusTone
import uk.co.cabcomply.app.util.AppClock
import uk.co.cabcomply.app.util.DateFormatting
import java.util.concurrent.TimeUnit
import javax.inject.Inject

enum class HistoryRangeFilter(val label: String) { ALL("All time"), LAST_7("Last 7 days"), LAST_30("Last 30 days") }

data class HistoryUiState(
    val vehicles: List<VehicleEntity> = emptyList(),
    val selectedVehicleId: String? = null,
    val range: HistoryRangeFilter = HistoryRangeFilter.ALL,
    val inspections: List<InspectionEntity> = emptyList(),
    val defectInspectionIds: Set<String> = emptySet()
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val vehicleRepository: VehicleRepository,
    private val inspectionRepository: InspectionRepository,
    private val defectRepository: DefectRepository,
    private val clock: AppClock
) : ViewModel() {

    private val selectedVehicleId = MutableStateFlow<String?>(null)
    private val range = MutableStateFlow(HistoryRangeFilter.ALL)

    val state: StateFlow<HistoryUiState> = combine(
        vehicleRepository.observeAllVehicles(),
        selectedVehicleId,
        range
    ) { vehicles, vehicleId, r -> Triple(vehicles, vehicleId, r) }
        .flatMapLatest { (vehicles, vehicleId, r) ->
            val toDate = clock.nowMillis()
            val fromDate = when (r) {
                HistoryRangeFilter.ALL -> null
                HistoryRangeFilter.LAST_7 -> toDate - TimeUnit.DAYS.toMillis(7)
                HistoryRangeFilter.LAST_30 -> toDate - TimeUnit.DAYS.toMillis(30)
            }
            inspectionRepository.observeHistory(vehicleId, fromDate, toDate).let { flow ->
                kotlinx.coroutines.flow.combine(flow, defectRepository.observeFiltered(null, null)) { inspections, defects ->
                    HistoryUiState(
                        vehicles = vehicles,
                        selectedVehicleId = vehicleId,
                        range = r,
                        inspections = inspections,
                        defectInspectionIds = defects.map { it.inspectionId }.toSet()
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun selectVehicle(id: String?) { selectedVehicleId.value = id }
    fun selectRange(r: HistoryRangeFilter) { range.value = r }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onOpenInspection: (String) -> Unit, viewModel: HistoryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var vehicleMenuExpanded by remember { mutableStateOf(false) }
    var rangeMenuExpanded by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ExposedDropdownMenuBox(expanded = vehicleMenuExpanded, onExpandedChange = { vehicleMenuExpanded = it }, modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = state.vehicles.firstOrNull { it.id == state.selectedVehicleId }?.registration ?: "All vehicles",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Vehicle") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = vehicleMenuExpanded) },
                    modifier = Modifier.menuAnchor()
                )
                androidx.compose.material3.ExposedDropdownMenu(expanded = vehicleMenuExpanded, onDismissRequest = { vehicleMenuExpanded = false }) {
                    DropdownMenuItem(text = { Text("All vehicles") }, onClick = { viewModel.selectVehicle(null); vehicleMenuExpanded = false })
                    state.vehicles.forEach { vehicle ->
                        DropdownMenuItem(
                            text = { Text(vehicle.registration) },
                            onClick = { viewModel.selectVehicle(vehicle.id); vehicleMenuExpanded = false }
                        )
                    }
                }
            }
            ExposedDropdownMenuBox(expanded = rangeMenuExpanded, onExpandedChange = { rangeMenuExpanded = it }, modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = state.range.label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Range") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = rangeMenuExpanded) },
                    modifier = Modifier.menuAnchor()
                )
                androidx.compose.material3.ExposedDropdownMenu(expanded = rangeMenuExpanded, onDismissRequest = { rangeMenuExpanded = false }) {
                    HistoryRangeFilter.entries.forEach { r ->
                        DropdownMenuItem(text = { Text(r.label) }, onClick = { viewModel.selectRange(r); rangeMenuExpanded = false })
                    }
                }
            }
        }

        if (state.inspections.isEmpty()) {
            EmptyState(
                title = "No vehicle checks recorded yet",
                message = "Completed daily checks will appear here.",
                modifier = Modifier.fillMaxSize()
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.inspections, key = { it.id }) { inspection ->
                    val hasDefect = state.defectInspectionIds.contains(inspection.id)
                    SectionCard(modifier = Modifier.clickable { onOpenInspection(inspection.id) }) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Column {
                                Text(
                                    DateFormatting.formatDate(inspection.inspectionDate),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "${inspection.vehicleRegistrationSnapshot} · ${inspection.odometer} miles",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            StatusChip(
                                text = if (hasDefect) "Defect" else "OK",
                                tone = if (hasDefect) StatusTone.DANGER else StatusTone.SUCCESS
                            )
                        }
                    }
                }
            }
        }
    }
}
