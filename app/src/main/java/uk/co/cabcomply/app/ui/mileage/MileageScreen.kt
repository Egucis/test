package uk.co.cabcomply.app.ui.mileage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import uk.co.cabcomply.app.data.db.entity.MileageEntryEntity
import uk.co.cabcomply.app.data.db.entity.MileagePurpose
import uk.co.cabcomply.app.data.repository.VehicleRepository
import uk.co.cabcomply.app.data.repository.MileageRepository
import uk.co.cabcomply.app.ui.components.EmptyState
import uk.co.cabcomply.app.ui.components.SectionCard
import uk.co.cabcomply.app.ui.components.StatusChip
import uk.co.cabcomply.app.ui.components.StatusTone
import uk.co.cabcomply.app.util.AppClock
import uk.co.cabcomply.app.util.DateFormatting
import uk.co.cabcomply.app.util.UkTaxYear
import javax.inject.Inject

data class MileageUiState(
    val activeVehicleId: String? = null,
    val entries: List<MileageEntryEntity> = emptyList(),
    val flagged: List<MileageEntryEntity> = emptyList()
) {
    val currentTaxYearTotal: Int get() {
        val tax = UkTaxYear.forDate(java.time.LocalDate.now())
        return entries.filter { inTaxYear(it, tax) && it.endMileage != null }.sumOf { it.endMileage!! - it.startMileage }
    }
    val currentTaxYearBusiness: Int get() {
        val tax = UkTaxYear.forDate(java.time.LocalDate.now())
        return entries.filter { inTaxYear(it, tax) && it.endMileage != null && it.purpose == MileagePurpose.BUSINESS }
            .sumOf { it.endMileage!! - it.startMileage }
    }
    private fun inTaxYear(entry: MileageEntryEntity, tax: UkTaxYear): Boolean {
        val zone = java.time.ZoneId.systemDefault()
        return entry.entryDate >= tax.startMillis(zone) && entry.entryDate < tax.endMillisExclusive(zone)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MileageViewModel @Inject constructor(
    vehicleRepository: VehicleRepository,
    mileageRepository: MileageRepository,
    clock: AppClock
) : ViewModel() {

    val state: StateFlow<MileageUiState> = vehicleRepository.observeActiveVehicle()
        .flatMapLatest { vehicle ->
            if (vehicle == null) {
                kotlinx.coroutines.flow.flowOf(MileageUiState())
            } else {
                combine(
                    mileageRepository.observeFiltered(vehicle.id, null, null),
                    mileageRepository.observeFlagged()
                ) { entries, flagged ->
                    MileageUiState(vehicle.id, entries, flagged.filter { it.vehicleId == vehicle.id })
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MileageUiState())
}

@Composable
fun MileageScreen(
    onAddEntry: () -> Unit,
    onOpenEntry: (String) -> Unit,
    viewModel: MileageViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var hmrcExpanded by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                val tax = UkTaxYear.forDate(java.time.LocalDate.now())
                SectionCard(modifier = Modifier.clickable { hmrcExpanded = !hmrcExpanded }) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "HMRC Mileage · ${tax.label} · ${state.currentTaxYearBusiness} miles",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Icon(if (hmrcExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
                    }
                    if (hmrcExpanded) {
                        Spacer(Modifier.height(10.dp))
                        Text("Total miles this tax year: ${state.currentTaxYearTotal}", style = MaterialTheme.typography.bodyMedium)
                        Text("Business miles this tax year: ${state.currentTaxYearBusiness}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (state.flagged.isNotEmpty()) {
                item {
                    SectionCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${state.flagged.size} mileage entr${if (state.flagged.size == 1) "y needs" else "ies need"} review",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        state.flagged.forEach { entry ->
                            Text(
                                "${DateFormatting.formatDate(entry.entryDate)}: ${entry.flagReason}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .clickable { onOpenEntry(entry.id) }
                                    .padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }
            if (state.entries.isEmpty()) {
                item {
                    EmptyState(
                        title = "No mileage recorded yet",
                        message = "Record your start and end mileage to keep accurate records.",
                        actionLabel = "Add mileage",
                        onAction = onAddEntry,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                items(state.entries, key = { it.id }) { entry ->
                    SectionCard(modifier = Modifier.clickable { onOpenEntry(entry.id) }) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Column {
                                Text(DateFormatting.formatDate(entry.entryDate), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${entry.startMileage} → ${entry.endMileage ?: "in progress"}" +
                                        (entry.endMileage?.let { " (${it - entry.startMileage} miles)" } ?: ""),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            if (entry.isFlagged) {
                                StatusChip("Review", StatusTone.WARNING)
                            } else {
                                StatusChip(entry.purpose.name.lowercase().replaceFirstChar { it.uppercase() }, StatusTone.NEUTRAL)
                            }
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = onAddEntry,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add mileage")
        }
    }
}
