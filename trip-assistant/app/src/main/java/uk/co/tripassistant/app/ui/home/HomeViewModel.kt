package uk.co.tripassistant.app.ui.home

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uk.co.tripassistant.app.data.billing.EntitlementRepository
import uk.co.tripassistant.app.data.prefs.AppSettings
import uk.co.tripassistant.app.data.prefs.SettingsRepository
import uk.co.tripassistant.app.data.repository.HistoryRepository
import uk.co.tripassistant.app.data.repository.OfferStats
import uk.co.tripassistant.app.data.repository.ProfileRepository
import uk.co.tripassistant.app.service.AssistantStateHolder
import uk.co.tripassistant.app.service.AssistantStatus
import uk.co.tripassistant.app.service.AssistantStoppedReason
import uk.co.tripassistant.app.util.DayRange
import uk.co.tripassistant.app.util.UberDriverLauncher
import uk.co.tripassistant.core.entitlement.AccessDecision
import uk.co.tripassistant.core.entitlement.AccessLevel
import uk.co.tripassistant.core.format.Formats
import uk.co.tripassistant.core.model.RuleId
import uk.co.tripassistant.core.model.RuleProfile
import javax.inject.Inject

/** One thing standing between the driver and a running assistant (spec sections 36 and 38). */
enum class StartBlocker(val title: String, val detail: String, val action: String) {
    ENTITLEMENT(
        title = "Subscription needed",
        detail = "Your free trial has finished, or your subscription could not be confirmed.",
        action = "See subscription"
    ),
    PRIVACY_ACKNOWLEDGEMENT(
        title = "Screen reading",
        detail = "Read how screen reading works before the assistant starts.",
        action = "Read and continue"
    ),
    OVERLAY_PERMISSION(
        title = "Display over other apps",
        detail = "The recommendation is shown in a small floating window above Uber Driver.",
        action = "Allow"
    ),
    NOTIFICATION_PERMISSION(
        title = "Notifications",
        detail = "Android shows a notice while the screen is being read. It is also how you stop the assistant without leaving Uber.",
        action = "Allow"
    )
}

data class HomeUiState(
    val status: AssistantStatus = AssistantStatus.Stopped(AssistantStoppedReason.NOT_STARTED),
    val profile: RuleProfile? = null,
    val thresholds: List<String> = emptyList(),
    val stats: OfferStats = OfferStats(),
    val access: AccessDecision? = null,
    val blockers: List<StartBlocker> = emptyList(),
    val uberDriverInstalled: Boolean = false,
    val historyEnabled: Boolean = true
) {
    val isRunning: Boolean get() = status is AssistantStatus.Running
    val isStarting: Boolean get() = status is AssistantStatus.Starting
    val canStart: Boolean get() = blockers.isEmpty()
    val stoppedReason: AssistantStoppedReason?
        get() = (status as? AssistantStatus.Stopped)?.reason
}

/**
 * Home answers four questions at a glance (spec section 35): is the assistant running, which
 * profile is active, what are the main thresholds, and how many offers has it seen today.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val entitlement: EntitlementRepository,
    profiles: ProfileRepository,
    history: HistoryRepository,
    assistantState: AssistantStateHolder
) : ViewModel() {

    /** Bumped when Home resumes, because Android permissions change outside this app. */
    private val refreshTrigger = MutableStateFlow(0)

    // Re-reading the day range on each refresh keeps "today" correct across midnight.
    private val todayStats = refreshTrigger.flatMapLatest {
        val today = DayRange.today()
        history.observeStatsBetween(today.first, today.last)
    }

    val state: StateFlow<HomeUiState> = combine(
        assistantState.status,
        profiles.observeActiveProfile(),
        todayStats,
        entitlement.observeAccess(),
        combine(settings.settings, refreshTrigger) { appSettings, _ -> appSettings }
    ) { status, profile, stats, access, appSettings ->
        HomeUiState(
            status = status,
            profile = profile,
            thresholds = profile?.let(::thresholdsOf).orEmpty(),
            stats = stats,
            access = access,
            blockers = blockers(access, appSettings),
            uberDriverInstalled = UberDriverLauncher.isInstalled(context),
            historyEnabled = appSettings.historyEnabled
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun refresh() {
        refreshTrigger.value = refreshTrigger.value + 1
    }

    fun openUberDriver() {
        UberDriverLauncher.open(context)
    }

    fun acknowledgePrivacy() {
        viewModelScope.launch { settings.acknowledgePrivacy() }
    }

    fun retryEntitlementVerification() {
        viewModelScope.launch { runCatching { entitlement.refresh() } }
    }

    /** Everything that must be true before capture can start, in the order it is worth asking. */
    private fun blockers(access: AccessDecision, appSettings: AppSettings): List<StartBlocker> =
        buildList {
            if (access.level != AccessLevel.FULL) add(StartBlocker.ENTITLEMENT)
            if (appSettings.privacyAckVersion < AppSettings.CURRENT_PRIVACY_VERSION) {
                add(StartBlocker.PRIVACY_ACKNOWLEDGEMENT)
            }
            if (!canDrawOverlay()) add(StartBlocker.OVERLAY_PERMISSION)
            if (!notificationsGranted()) add(StartBlocker.NOTIFICATION_PERMISSION)
        }

    private fun canDrawOverlay(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    /**
     * The capture notification is not optional — Android shows it while a projection runs — so a
     * driver who denied notifications would have no way to stop the assistant without leaving
     * Uber (spec section 37).
     */
    private fun notificationsGranted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    private fun thresholdsOf(profile: RuleProfile): List<String> = buildList {
        profile.rule(RuleId.MIN_POUNDS_PER_MILE)?.takeIf { it.isActive }?.let {
            add("${Formats.moneyCompact(it.target)} / mile")
        }
        profile.rule(RuleId.MIN_POUNDS_PER_HOUR)?.takeIf { it.isActive }?.let {
            add("${Formats.moneyCompact(it.target)} / hour")
        }
    }
}
