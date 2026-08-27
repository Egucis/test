package uk.co.cabcomply.app.ui.vehicles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uk.co.cabcomply.app.data.billing.EntitlementManager
import uk.co.cabcomply.app.data.billing.FeatureGate
import uk.co.cabcomply.app.data.db.entity.VehicleEntity
import uk.co.cabcomply.app.data.repository.VehicleRepository
import uk.co.cabcomply.app.ui.components.ConfirmDialog
import uk.co.cabcomply.app.ui.components.EmptyState
import uk.co.cabcomply.app.ui.components.SectionCard
import uk.co.cabcomply.app.ui.components.StatusChip
import uk.co.cabcomply.app.ui.components.StatusTone
import javax.inject.Inject

data class VehiclesUiState(
    val activeVehicles: List<VehicleEntity> = emptyList(),
    val archivedVehicles: List<VehicleEntity> = emptyList(),
    val activeVehicleId: String? = null,
    val canAddAnotherVehicle: Boolean = true
)

@HiltViewModel
class VehiclesViewModel @Inject constructor(
    private val vehicleRepository: VehicleRepository,
    private val entitlementManager: EntitlementManager
) : ViewModel() {

    val state: StateFlow<VehiclesUiState> = combine(
        vehicleRepository.observeActiveVehicles(),
        vehicleRepository.observeArchivedVehicles(),
        vehicleRepository.observeActiveVehicle(),
        entitlementManager.entitlement
    ) { active, archived, activeVehicle, entitlement ->
        VehiclesUiState(
            activeVehicles = active,
            archivedVehicles = archived,
            activeVehicleId = activeVehicle?.id,
            canAddAnotherVehicle = active.size < FeatureGate.maxActiveVehicles(entitlement)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VehiclesUiState())

    fun setActive(id: String) { viewModelScope.launch { vehicleRepository.setActiveVehicle(id) } }
    fun archive(id: String) { viewModelScope.launch { vehicleRepository.archiveVehicle(id) } }
}

@Composable
fun VehiclesScreen(
    onAddVehicle: () -> Unit,
    onEditVehicle: (String) -> Unit,
    onUpgradeToPro: () -> Unit,
    viewModel: VehiclesViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var archiveTargetId by remember { mutableStateOf<String?>(null) }
    var showPaywall by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        if (state.activeVehicles.isEmpty()) {
            EmptyState(
                title = "No vehicle added yet",
                message = "Add a vehicle to start recording daily checks.",
                actionLabel = "Add vehicle",
                onAction = onAddVehicle,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.activeVehicles, key = { it.id }) { vehicle ->
                    SectionCard(modifier = Modifier.clickable { onEditVehicle(vehicle.id) }) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(vehicle.registration, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text("${vehicle.make} ${vehicle.model}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (vehicle.id == state.activeVehicleId) {
                                StatusChip("Active", StatusTone.SUCCESS)
                            } else {
                                OutlinedButton(onClick = { viewModel.setActive(vehicle.id) }) { Text("Make active") }
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                            androidx.compose.material3.TextButton(onClick = { archiveTargetId = vehicle.id }) {
                                Text("Archive", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                if (state.archivedVehicles.isNotEmpty()) {
                    item {
                        Text(
                            "Archived vehicles",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                    items(state.archivedVehicles, key = { it.id }) { vehicle ->
                        SectionCard(modifier = Modifier.clickable { onEditVehicle(vehicle.id) }) {
                            Text(vehicle.registration, style = MaterialTheme.typography.titleMedium)
                            Text("${vehicle.make} ${vehicle.model} · Archived", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { if (state.canAddAnotherVehicle) onAddVehicle() else showPaywall = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add vehicle")
        }
    }

    archiveTargetId?.let { id ->
        ConfirmDialog(
            title = "Archive this vehicle?",
            message = "Archived vehicles are removed from your active list but all their history, mileage and documents are kept and remain viewable.",
            confirmLabel = "Archive",
            isDestructive = true,
            onConfirm = { viewModel.archive(id); archiveTargetId = null },
            onDismiss = { archiveTargetId = null }
        )
    }

    if (showPaywall) {
        ConfirmDialog(
            title = "Multiple vehicles is a Pro feature",
            message = "CabComply Basic supports one active vehicle. Upgrade to Pro to track more than one vehicle's checks, mileage and documents.",
            confirmLabel = "See Pro",
            onConfirm = { showPaywall = false; onUpgradeToPro() },
            onDismiss = { showPaywall = false }
        )
    }
}
