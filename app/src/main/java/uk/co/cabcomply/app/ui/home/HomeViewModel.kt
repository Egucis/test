package uk.co.cabcomply.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uk.co.cabcomply.app.data.db.entity.DefectEntity
import uk.co.cabcomply.app.data.db.entity.DocumentEntity
import uk.co.cabcomply.app.data.db.entity.InspectionEntity
import uk.co.cabcomply.app.data.db.entity.VehicleEntity
import uk.co.cabcomply.app.data.repository.DefectRepository
import uk.co.cabcomply.app.data.repository.DocumentExpiryStatus
import uk.co.cabcomply.app.data.repository.DocumentRepository
import uk.co.cabcomply.app.data.repository.InspectionRepository
import uk.co.cabcomply.app.data.repository.VehicleRepository
import uk.co.cabcomply.app.data.repository.expiryStatusFor
import uk.co.cabcomply.app.util.AppClock
import javax.inject.Inject

enum class TodayCheckState { NOT_STARTED, COMPLETED_CLEAN, COMPLETED_WITH_DEFECT }

data class HomeUiState(
    val activeVehicle: VehicleEntity? = null,
    val otherVehicles: List<VehicleEntity> = emptyList(),
    val todayInspection: InspectionEntity? = null,
    val todayCheckState: TodayCheckState = TodayCheckState.NOT_STARTED,
    val openDefectCount: Int = 0,
    val documentsExpiringSoon: Int = 0,
    val documentsExpired: Int = 0,
    val documentsValid: Int = 0,
    val isLoading: Boolean = true
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val vehicleRepository: VehicleRepository,
    private val inspectionRepository: InspectionRepository,
    private val defectRepository: DefectRepository,
    private val documentRepository: DocumentRepository,
    private val clock: AppClock
) : ViewModel() {

    val state: StateFlow<HomeUiState> = combine(
        vehicleRepository.observeActiveVehicles(),
        vehicleRepository.observeActiveVehicle().flatMapLatest { vehicle ->
            if (vehicle == null) {
                flowOf(Triple<InspectionEntity?, List<DefectEntity>, VehicleEntity?>(null, emptyList(), null))
            } else {
                val dayStart = clock.startOfDay()
                val dayEnd = dayStart + 86_400_000L - 1
                combine(
                    inspectionRepository.observeHistory(vehicle.id, dayStart, dayEnd),
                    defectRepository.observeOpenForVehicle(vehicle.id)
                ) { inspections, defects ->
                    Triple(inspections.maxByOrNull { it.completedAt ?: 0L }, defects, vehicle)
                }
            }
        },
        documentRepository.observeAll()
    ) { allVehicles, (todayInspection, openDefects, activeVehicle), documents ->
        val now = clock.nowMillis()
        var expiringSoon = 0
        var expired = 0
        var valid = 0
        documents.forEach {
            when (expiryStatusFor(it.expiryDate, now)) {
                DocumentExpiryStatus.EXPIRING_SOON -> expiringSoon++
                DocumentExpiryStatus.EXPIRED -> expired++
                DocumentExpiryStatus.VALID -> valid++
                DocumentExpiryStatus.NO_EXPIRY -> Unit
            }
        }
        val checkState = when {
            todayInspection == null -> TodayCheckState.NOT_STARTED
            openDefects.any { it.inspectionId == todayInspection.id } -> TodayCheckState.COMPLETED_WITH_DEFECT
            else -> TodayCheckState.COMPLETED_CLEAN
        }
        HomeUiState(
            activeVehicle = activeVehicle,
            otherVehicles = allVehicles.filter { it.id != activeVehicle?.id },
            todayInspection = todayInspection,
            todayCheckState = checkState,
            openDefectCount = openDefects.size,
            documentsExpiringSoon = expiringSoon,
            documentsExpired = expired,
            documentsValid = valid,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun setActiveVehicle(vehicleId: String) {
        viewModelScope.launch { vehicleRepository.setActiveVehicle(vehicleId) }
    }
}
