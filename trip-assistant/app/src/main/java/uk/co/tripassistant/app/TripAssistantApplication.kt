package uk.co.tripassistant.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import uk.co.tripassistant.app.data.billing.EntitlementRepository
import uk.co.tripassistant.app.data.repository.HistoryRepository
import uk.co.tripassistant.app.data.repository.ProfileRepository
import javax.inject.Inject

@HiltAndroidApp
class TripAssistantApplication : Application() {

    @Inject lateinit var profiles: ProfileRepository
    @Inject lateinit var history: HistoryRepository
    @Inject lateinit var entitlement: EntitlementRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            // Starter profiles exist from the first launch, so the rule tester and the overlay
            // always have something to work with (spec section 18).
            profiles.seedIfEmpty()

            // The trial starts when the driver first opens the app, not when Play installed it
            // (spec section 3).
            entitlement.startTrialIfNeeded()

            // Retention is applied at launch rather than on a schedule: it is one indexed delete,
            // and it means the setting takes effect the next time the app is opened
            // (spec section 34).
            runCatching { history.applyRetention() }

            // Refreshing entitlement in the background keeps the offline allowance topped up so a
            // driver who has been offline for days is not stopped the moment they start a shift
            // (spec section 5).
            runCatching { entitlement.refresh() }
        }
    }
}
