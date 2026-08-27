package uk.co.cabcomply.app.data.security

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Re-locks CabComply when the driver genuinely leaves the app (registered against the process
 * lifecycle, so switching between CabComply's own screens never triggers a lock — product spec
 * section 46). Officer Mode's own hold-to-exit + PIN gate is separate and unaffected by this.
 */
@Singleton
class AppLockManager @Inject constructor(
    private val pinManager: PinManager
) : DefaultLifecycleObserver {

    private val _isLocked = MutableStateFlow(pinManager.appLockEnabled)
    val isLocked: StateFlow<Boolean> = _isLocked

    override fun onStop(owner: LifecycleOwner) {
        if (pinManager.appLockEnabled) {
            _isLocked.value = true
        }
    }

    fun unlock() {
        _isLocked.value = false
    }

    /** Called after the driver disables app-lock from Settings so no stale lock lingers this session. */
    fun clearLockIfProtectionDisabled() {
        if (!pinManager.appLockEnabled) {
            _isLocked.value = false
        }
    }
}
