package uk.co.cabcomply.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import uk.co.cabcomply.app.data.db.entity.ChecklistEntity
import uk.co.cabcomply.app.data.repository.ChecklistRepository
import uk.co.cabcomply.app.data.repository.VehicleRepository
import uk.co.cabcomply.app.ui.components.SectionCard
import uk.co.cabcomply.app.util.DateFormatting
import javax.inject.Inject

@HiltViewModel
class SettingsDailyChecksViewModel @Inject constructor(
    private val vehicleRepository: VehicleRepository,
    private val checklistRepository: ChecklistRepository
) : ViewModel() {

    private val _checklist = MutableStateFlow<ChecklistEntity?>(null)
    val checklist: StateFlow<ChecklistEntity?> = _checklist

    init {
        viewModelScope.launch {
            val vehicle = vehicleRepository.getActiveVehicle()
            _checklist.value = checklistRepository.getActiveChecklist(vehicle?.licensingAuthorityId)
        }
    }
}

@Composable
fun SettingsDailyChecksScreen(viewModel: SettingsDailyChecksViewModel = hiltViewModel()) {
    val checklist by viewModel.checklist.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("Daily Checks", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        SectionCard {
            Text("Active checklist", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            checklist?.let {
                Text("${it.name} · Version ${it.version}", style = MaterialTheme.typography.bodyMedium)
                if (it.isCustom) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "This is a custom checklist and is not officially issued by any licensing authority.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Checklists are versioned — updating requirements never changes checks you've already completed; " +
                    "new checks always use the current version.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(16.dp))
        SectionCard {
            Text("Quick Check", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Quick Check pre-fills the odometer and marks items OK, but you must still confirm you've " +
                    "physically inspected the vehicle before it can be saved.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
