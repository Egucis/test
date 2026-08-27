package uk.co.cabcomply.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import uk.co.cabcomply.app.data.db.entity.LicensingAuthorityEntity
import uk.co.cabcomply.app.data.repository.AuthorityRepository
import uk.co.cabcomply.app.data.repository.DriverRepository
import uk.co.cabcomply.app.data.repository.VehicleRepository
import uk.co.cabcomply.app.data.security.PinManager
import uk.co.cabcomply.app.data.seed.AuthoritySeedData
import javax.inject.Inject

data class OnboardingUiState(
    val authorities: List<LicensingAuthorityEntity> = emptyList(),
    val driverName: String = "",
    val selectedAuthorityId: String? = null,
    val customAuthorityName: String = "",
    val badgeNumber: String = "",
    val driverError: String? = null,
    val vehicleId: String? = null,
    val registration: String = "",
    val make: String = "",
    val model: String = "",
    val plateNumber: String = "",
    val currentOdometer: String = "",
    val licenceExpiryDate: Long? = null,
    val vehicleError: String? = null,
    val pin: String = "",
    val pinConfirm: String = "",
    val pinEnabled: Boolean = false,
    val pinError: String? = null,
    val isSaving: Boolean = false
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val driverRepository: DriverRepository,
    private val vehicleRepository: VehicleRepository,
    private val authorityRepository: AuthorityRepository,
    private val pinManager: PinManager
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state

    init {
        viewModelScope.launch {
            authorityRepository.observeAuthorities().collect { list ->
                _state.value = _state.value.copy(authorities = list)
            }
        }
    }

    fun onDriverNameChange(value: String) { _state.value = _state.value.copy(driverName = value, driverError = null) }
    fun onAuthoritySelected(id: String) { _state.value = _state.value.copy(selectedAuthorityId = id) }
    fun onCustomAuthorityNameChange(value: String) { _state.value = _state.value.copy(customAuthorityName = value) }
    fun onBadgeNumberChange(value: String) { _state.value = _state.value.copy(badgeNumber = value) }

    fun onRegistrationChange(value: String) { _state.value = _state.value.copy(registration = value, vehicleError = null) }
    fun onMakeChange(value: String) { _state.value = _state.value.copy(make = value, vehicleError = null) }
    fun onModelChange(value: String) { _state.value = _state.value.copy(model = value, vehicleError = null) }
    fun onPlateNumberChange(value: String) { _state.value = _state.value.copy(plateNumber = value) }
    fun onOdometerChange(value: String) { _state.value = _state.value.copy(currentOdometer = value.filter { it.isDigit() }, vehicleError = null) }
    fun onLicenceExpiryChange(value: Long?) { _state.value = _state.value.copy(licenceExpiryDate = value) }

    fun onPinChange(value: String) { _state.value = _state.value.copy(pin = value.filter { it.isDigit() }.take(8), pinError = null) }
    fun onPinConfirmChange(value: String) { _state.value = _state.value.copy(pinConfirm = value.filter { it.isDigit() }.take(8), pinError = null) }
    fun onPinEnabledChange(value: Boolean) { _state.value = _state.value.copy(pinEnabled = value, pin = "", pinConfirm = "", pinError = null) }

    /** Returns true and persists the driver profile if valid; otherwise sets an inline error. */
    fun saveDriverStep(onSuccess: () -> Unit) {
        val s = _state.value
        if (s.driverName.isBlank()) {
            _state.value = s.copy(driverError = "Enter your name before continuing.")
            return
        }
        if (s.selectedAuthorityId == null) {
            _state.value = s.copy(driverError = "Choose your licensing authority before continuing.")
            return
        }
        if (isCustomAuthoritySelected(s) && s.customAuthorityName.isBlank()) {
            _state.value = s.copy(driverError = "Enter the name of your licensing authority.")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true)
            val authorityId = resolveAuthorityId(s)
            driverRepository.saveProfile(s.driverName, authorityId, s.badgeNumber.ifBlank { null })
            _state.value = _state.value.copy(isSaving = false, selectedAuthorityId = authorityId)
            onSuccess()
        }
    }

    fun saveVehicleStep(onSuccess: () -> Unit) {
        val s = _state.value
        if (s.registration.isBlank() || s.make.isBlank() || s.model.isBlank()) {
            _state.value = s.copy(vehicleError = "Enter registration, make and model before continuing.")
            return
        }
        val odometer = s.currentOdometer.toIntOrNull()
        if (odometer == null) {
            _state.value = s.copy(vehicleError = "Enter the current odometer reading before continuing.")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true)
            val vehicle = vehicleRepository.saveVehicle(
                id = s.vehicleId,
                registration = s.registration,
                make = s.make,
                model = s.model,
                licensingAuthorityId = s.selectedAuthorityId,
                plateNumber = s.plateNumber.ifBlank { null },
                licenceExpiryDate = s.licenceExpiryDate,
                currentOdometer = odometer,
                makeActive = true
            )
            _state.value = _state.value.copy(isSaving = false, vehicleId = vehicle.id)
            onSuccess()
        }
    }

    fun saveSecurityStep(onSuccess: () -> Unit) {
        val s = _state.value
        if (!s.pinEnabled) {
            pinManager.disablePinProtection()
            onSuccess()
            return
        }
        if (s.pin.length < 4) {
            _state.value = s.copy(pinError = "PIN must be at least 4 digits.")
            return
        }
        if (s.pin != s.pinConfirm) {
            _state.value = s.copy(pinError = "PINs do not match.")
            return
        }
        pinManager.setPin(s.pin)
        pinManager.appLockEnabled = true
        onSuccess()
    }

    private fun isCustomAuthoritySelected(s: OnboardingUiState): Boolean =
        s.selectedAuthorityId == AuthoritySeedData.CUSTOM_AUTHORITY_ID

    private suspend fun resolveAuthorityId(s: OnboardingUiState): String {
        return if (isCustomAuthoritySelected(s)) {
            authorityRepository.createCustomAuthority(s.customAuthorityName).id
        } else {
            s.selectedAuthorityId!!
        }
    }
}
