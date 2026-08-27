package uk.co.cabcomply.app.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import uk.co.cabcomply.app.data.billing.EntitlementManager
import uk.co.cabcomply.app.data.notifications.NotificationPreferences
import uk.co.cabcomply.app.data.notifications.NotificationScheduler
import uk.co.cabcomply.app.data.repository.DriverRepository
import uk.co.cabcomply.app.data.repository.VehicleRepository
import uk.co.cabcomply.app.data.security.AppLockManager
import javax.inject.Inject

/** Decides whether a fresh install needs onboarding, and kicks off startup work (product spec section 7). */
@HiltViewModel
class AppRootViewModel @Inject constructor(
    private val driverRepository: DriverRepository,
    private val vehicleRepository: VehicleRepository,
    val appLockManager: AppLockManager,
    private val entitlementManager: EntitlementManager,
    private val notificationPreferences: NotificationPreferences,
    private val notificationScheduler: NotificationScheduler
) : ViewModel() {

    private val _needsOnboarding = MutableStateFlow<Boolean?>(null)
    val needsOnboarding: StateFlow<Boolean?> = _needsOnboarding

    init {
        viewModelScope.launch {
            val hasDriver = driverRepository.getProfile() != null
            val hasVehicle = vehicleRepository.getActiveVehicle() != null
            _needsOnboarding.value = !(hasDriver && hasVehicle)

            entitlementManager.refresh()
            if (notificationPreferences.remindersEnabled.first()) {
                notificationScheduler.ensureScheduled()
            }
        }
    }
}
