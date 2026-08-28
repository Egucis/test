package uk.co.tripassistant.app.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import uk.co.tripassistant.core.pipeline.AnalysisDiagnostics
import uk.co.tripassistant.core.model.OfferEvaluation
import javax.inject.Inject
import javax.inject.Singleton

/** Why the assistant is not running, when it is not running. */
enum class AssistantStoppedReason {
    NOT_STARTED,
    STOPPED_BY_DRIVER,

    /** Android ended the projection — the user revoked it, or another app took the capture. */
    PROJECTION_REVOKED,

    /** Overlay permission was withdrawn while running. */
    OVERLAY_PERMISSION_LOST,

    /** Trial over and nothing bought, or offline past the allowance (spec sections 3 and 5). */
    ENTITLEMENT_REQUIRED
}

/** What the assistant is doing right now. */
sealed interface AssistantStatus {
    data class Stopped(val reason: AssistantStoppedReason) : AssistantStatus
    data object Starting : AssistantStatus
    data object Running : AssistantStatus
}

/**
 * The live state of the assistant, shared between the foreground service and the UI.
 *
 * A singleton rather than a bound service: Home only ever reads this, and a process death takes
 * both the service and the state with it, so the two cannot disagree.
 */
@Singleton
class AssistantStateHolder @Inject constructor() {

    private val _status = MutableStateFlow<AssistantStatus>(
        AssistantStatus.Stopped(AssistantStoppedReason.NOT_STARTED)
    )
    val status: StateFlow<AssistantStatus> = _status.asStateFlow()

    private val _lastEvaluation = MutableStateFlow<OfferEvaluation?>(null)
    val lastEvaluation: StateFlow<OfferEvaluation?> = _lastEvaluation.asStateFlow()

    /** Feeds the diagnostics screen (spec section 44). Never contains a frame or full screen text. */
    private val _lastDiagnostics = MutableStateFlow<AnalysisDiagnostics?>(null)
    val lastDiagnostics: StateFlow<AnalysisDiagnostics?> = _lastDiagnostics.asStateFlow()

    private val _framesAnalysed = MutableStateFlow(0L)
    val framesAnalysed: StateFlow<Long> = _framesAnalysed.asStateFlow()

    val isRunning: Boolean get() = _status.value is AssistantStatus.Running

    fun setStatus(status: AssistantStatus) {
        _status.value = status
    }

    fun onAnalysis(evaluation: OfferEvaluation?, diagnostics: AnalysisDiagnostics) {
        _framesAnalysed.value = _framesAnalysed.value + 1
        _lastDiagnostics.value = diagnostics
        if (evaluation != null) _lastEvaluation.value = evaluation
    }

    fun clearLastEvaluation() {
        _lastEvaluation.value = null
    }
}
