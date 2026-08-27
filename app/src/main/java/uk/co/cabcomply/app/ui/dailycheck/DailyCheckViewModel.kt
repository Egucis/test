package uk.co.cabcomply.app.ui.dailycheck

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import uk.co.cabcomply.app.data.db.entity.DefectEntity
import uk.co.cabcomply.app.data.db.entity.InspectionEntity
import uk.co.cabcomply.app.data.db.entity.InspectionResultStatus
import uk.co.cabcomply.app.data.db.entity.VehicleEntity
import uk.co.cabcomply.app.data.files.PhotoStorage
import uk.co.cabcomply.app.data.repository.AuthorityRepository
import uk.co.cabcomply.app.data.repository.ChecklistItemOutcome
import uk.co.cabcomply.app.data.repository.ChecklistRepository
import uk.co.cabcomply.app.data.repository.DefectRepository
import uk.co.cabcomply.app.data.repository.DriverRepository
import uk.co.cabcomply.app.data.repository.InspectionRepository
import uk.co.cabcomply.app.data.repository.MileageRepository
import uk.co.cabcomply.app.data.repository.VehicleRepository
import uk.co.cabcomply.app.util.AppClock
import uk.co.cabcomply.app.util.Ids
import javax.inject.Inject

enum class DailyCheckStep { ODOMETER, CHECKLIST, REVIEW }

data class ChecklistItemUi(
    val id: String,
    val category: String,
    val name: String,
    val helpText: String?,
    val displayOrder: Int,
    val status: InspectionResultStatus? = null,
    val defectDescription: String = "",
    val defectPhotoPaths: List<String> = emptyList(),
    val defectThumbnailPaths: List<String> = emptyList()
)

data class DailyCheckUiState(
    val step: DailyCheckStep = DailyCheckStep.ODOMETER,
    val vehicle: VehicleEntity? = null,
    val checklistId: String = "",
    val checklistName: String = "",
    val checklistVersion: Int = 1,
    val items: List<ChecklistItemUi> = emptyList(),
    val odometerText: String = "",
    val notes: String = "",
    val driverConfirmed: Boolean = false,
    val isQuickCheck: Boolean = false,
    val existingCompletedInspection: InspectionEntity? = null,
    val duplicateWarningDismissed: Boolean = false,
    val openDefectsFromBefore: List<DefectEntity> = emptyList(),
    val validationError: String? = null,
    val focusedItemId: String? = null,
    val isSaving: Boolean = false,
    val isLoading: Boolean = true,
    val completedInspectionId: String? = null
) {
    val checkedCount: Int get() = items.count { it.status != null }
    val defectCount: Int get() = items.count { it.status == InspectionResultStatus.DEFECT }
    val allItemsChecked: Boolean get() = items.isNotEmpty() && items.all { it.status != null }
}

@HiltViewModel
class DailyCheckViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vehicleRepository: VehicleRepository,
    private val checklistRepository: ChecklistRepository,
    private val inspectionRepository: InspectionRepository,
    private val defectRepository: DefectRepository,
    private val driverRepository: DriverRepository,
    private val authorityRepository: AuthorityRepository,
    private val mileageRepository: MileageRepository,
    private val photoStorage: PhotoStorage,
    private val clock: AppClock
) : ViewModel() {

    private val vehicleId: String = savedStateHandle.get<String>("vehicleId").orEmpty()
    private val requestedQuickCheck: Boolean = savedStateHandle.get<String>("quick")?.toBoolean() ?: false

    private val _state = MutableStateFlow(DailyCheckUiState(isQuickCheck = requestedQuickCheck))
    val state: StateFlow<DailyCheckUiState> = _state

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val vehicle = vehicleRepository.getById(vehicleId) ?: return@launch
            val checklist = checklistRepository.getActiveChecklist(vehicle.licensingAuthorityId)
            val checklistItems = checklistRepository.getItems(checklist.id)
            val existingToday = inspectionRepository.getCompletedToday(vehicleId)
            val openDefects = defectRepository.observeOpenForVehicle(vehicleId)

            val lastOdometer = inspectionRepository.getLastKnownOdometer(vehicleId)

            // Carry forward each item's outcome from the most recent completed check so the
            // driver isn't re-judging the same vehicle from scratch every day: "Not applicable"
            // items (e.g. equipment this vehicle doesn't have) stay N/A on every check, and Quick
            // Check additionally pre-fills everything else as OK. A previous defect is never
            // silently repeated — it always requires the driver to notice and re-mark it, backed
            // by the open-defect banner shown before the checklist.
            val lastResultByItemId = inspectionRepository.getLatestCompleted(vehicleId)
                ?.let { inspectionRepository.getResults(it.id) }
                ?.associate { it.checklistItemId to it.status }
                .orEmpty()

            val items = checklistItems.map {
                val lastStatus = lastResultByItemId[it.id]
                val prefill = when {
                    lastStatus == InspectionResultStatus.NOT_APPLICABLE -> InspectionResultStatus.NOT_APPLICABLE
                    requestedQuickCheck -> InspectionResultStatus.OK
                    else -> null
                }
                ChecklistItemUi(
                    id = it.id,
                    category = it.category,
                    name = it.name,
                    helpText = it.helpText,
                    displayOrder = it.displayOrder,
                    status = prefill
                )
            }

            _state.value = _state.value.copy(
                vehicle = vehicle,
                checklistId = checklist.id,
                checklistName = checklist.name,
                checklistVersion = checklist.version,
                items = items,
                odometerText = lastOdometer?.toString() ?: "",
                existingCompletedInspection = existingToday,
                isLoading = false
            )

            openDefects.collect { defects ->
                _state.value = _state.value.copy(openDefectsFromBefore = defects)
            }
        }
    }

    fun dismissDuplicateWarning() { _state.value = _state.value.copy(duplicateWarningDismissed = true) }

    fun onOdometerChange(value: String) {
        _state.value = _state.value.copy(odometerText = value.filter { it.isDigit() }, validationError = null)
    }

    fun continueFromOdometer() {
        val odometer = _state.value.odometerText.toIntOrNull()
        if (odometer == null) {
            _state.value = _state.value.copy(validationError = "Enter the current odometer reading before continuing.")
            return
        }
        _state.value = _state.value.copy(step = DailyCheckStep.CHECKLIST, validationError = null)
    }

    fun backToOdometer() { _state.value = _state.value.copy(step = DailyCheckStep.ODOMETER) }
    fun backToChecklist() { _state.value = _state.value.copy(step = DailyCheckStep.CHECKLIST) }

    fun markItemOk(itemId: String) = updateItem(itemId) {
        it.copy(status = InspectionResultStatus.OK, defectDescription = "", defectPhotoPaths = emptyList(), defectThumbnailPaths = emptyList())
    }

    fun markItemDefect(itemId: String) = updateItem(itemId) { it.copy(status = InspectionResultStatus.DEFECT) }

    fun markItemNotApplicable(itemId: String) = updateItem(itemId) {
        it.copy(status = InspectionResultStatus.NOT_APPLICABLE, defectDescription = "", defectPhotoPaths = emptyList(), defectThumbnailPaths = emptyList())
    }

    fun onDefectDescriptionChange(itemId: String, value: String) =
        updateItem(itemId) { it.copy(defectDescription = value) }

    fun addDefectPhoto(itemId: String, uri: Uri) {
        viewModelScope.launch {
            val stored = runCatching { photoStorage.importPhoto(uri) }.getOrNull() ?: return@launch
            updateItem(itemId) {
                it.copy(
                    defectPhotoPaths = it.defectPhotoPaths + stored.relativePath,
                    defectThumbnailPaths = it.defectThumbnailPaths + stored.thumbnailRelativePath
                )
            }
        }
    }

    fun removeDefectPhoto(itemId: String, index: Int) = updateItem(itemId) {
        it.copy(
            defectPhotoPaths = it.defectPhotoPaths.filterIndexed { i, _ -> i != index },
            defectThumbnailPaths = it.defectThumbnailPaths.filterIndexed { i, _ -> i != index }
        )
    }

    private fun updateItem(itemId: String, transform: (ChecklistItemUi) -> ChecklistItemUi) {
        _state.value = _state.value.copy(
            items = _state.value.items.map { if (it.id == itemId) transform(it) else it },
            validationError = null
        )
    }

    fun continueFromChecklist() {
        val s = _state.value
        val unchecked = s.items.firstOrNull { it.status == null }
        if (unchecked != null) {
            _state.value = s.copy(
                validationError = "Mark \"${unchecked.name}\" as OK or Defect before continuing.",
                focusedItemId = unchecked.id
            )
            return
        }
        val missingDescription = s.items.firstOrNull { it.status == InspectionResultStatus.DEFECT && it.defectDescription.isBlank() }
        if (missingDescription != null) {
            _state.value = s.copy(
                validationError = "Please describe the defect found with \"${missingDescription.name}\".",
                focusedItemId = missingDescription.id
            )
            return
        }
        _state.value = s.copy(step = DailyCheckStep.REVIEW, validationError = null, focusedItemId = null)
    }

    fun onNotesChange(value: String) { _state.value = _state.value.copy(notes = value) }
    fun onConfirmChange(value: Boolean) { _state.value = _state.value.copy(driverConfirmed = value) }

    fun completeInspection() {
        val s = _state.value
        val vehicle = s.vehicle ?: return
        if (!s.driverConfirmed) {
            _state.value = s.copy(validationError = "Confirm that you have physically inspected the vehicle before saving.")
            return
        }
        val odometer = s.odometerText.toIntOrNull() ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true)
            val driver = driverRepository.getProfile()
            val authorityName = vehicle.licensingAuthorityId?.let { authorityRepository.getById(it)?.name }
            val now = clock.nowMillis()
            val inspection = InspectionEntity(
                id = Ids.newId(),
                vehicleId = vehicle.id,
                vehicleRegistrationSnapshot = vehicle.registration,
                driverProfileId = driver?.id.orEmpty(),
                driverNameSnapshot = driver?.name ?: "Unknown driver",
                licensingAuthorityId = vehicle.licensingAuthorityId,
                licensingAuthorityNameSnapshot = authorityName,
                checklistId = s.checklistId,
                checklistNameSnapshot = s.checklistName,
                checklistVersionSnapshot = s.checklistVersion,
                inspectionDate = clock.startOfDay(now),
                startedAt = now,
                completedAt = now,
                odometer = odometer,
                notes = s.notes.ifBlank { null },
                driverConfirmed = true,
                confirmationTimestamp = now,
                isQuickCheck = s.isQuickCheck,
                modifiedAt = null,
                modificationReason = null
            )
            val outcomes = s.items.map {
                ChecklistItemOutcome(
                    checklistItemId = it.id,
                    itemName = it.name,
                    category = it.category,
                    displayOrder = it.displayOrder,
                    status = it.status ?: InspectionResultStatus.OK,
                    defectDescription = it.defectDescription.ifBlank { null },
                    defectPhotoRelativePaths = it.defectPhotoPaths,
                    defectThumbnailRelativePaths = it.defectThumbnailPaths
                )
            }
            inspectionRepository.completeInspection(inspection, outcomes)
            mileageRepository.ensureDailyCheckStartEntry(vehicle.id, odometer, inspection.inspectionDate, now)
            _state.value = _state.value.copy(isSaving = false, completedInspectionId = inspection.id)
        }
    }
}
