package uk.co.tripassistant.app.pipeline

import uk.co.tripassistant.app.data.prefs.SettingsRepository
import uk.co.tripassistant.app.data.repository.HistoryRepository
import uk.co.tripassistant.app.data.repository.RecordResult
import uk.co.tripassistant.app.overlay.OverlayFeedback
import uk.co.tripassistant.app.overlay.OverlayState
import uk.co.tripassistant.app.service.AssistantStateHolder
import uk.co.tripassistant.core.dedupe.OfferFingerprint
import uk.co.tripassistant.core.model.OfferEvaluation
import uk.co.tripassistant.core.model.OfferOutcome
import uk.co.tripassistant.core.model.Recommendation
import uk.co.tripassistant.core.model.RuleProfile
import uk.co.tripassistant.core.outcome.OutcomeDetector
import uk.co.tripassistant.core.outcome.PostOfferSignal
import uk.co.tripassistant.core.pipeline.OfferAnalyzer
import uk.co.tripassistant.core.rules.DefaultProfiles
import uk.co.tripassistant.core.text.OcrText
import uk.co.tripassistant.core.text.Rect01
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One analysed frame, start to finish (spec section 62).
 *
 * The decision itself belongs to :core; this class is the part that has to know about time, the
 * database and the driver's settings:
 *
 *  * it holds the last result on screen for a few seconds after an offer disappears, so the
 *    overlay does not blink back to "waiting" between two frames of the same card;
 *  * it alerts once per offer, not once per frame (spec sections 28 and 29);
 *  * it records the offer through the de-duplicating repository (spec section 29);
 *  * it upgrades an offer to ACCEPTED only when the next screens carry real evidence, and has no
 *    way at all to record "declined" (spec section 30).
 */
@Singleton
class LiveOfferPipeline @Inject constructor(
    private val analyzer: OfferAnalyzer,
    private val history: HistoryRepository,
    private val settings: SettingsRepository,
    private val feedback: OverlayFeedback,
    private val state: AssistantStateHolder
) {

    /** Kept up to date by the service; read on every frame without touching the database. */
    @Volatile
    var activeProfile: RuleProfile = DefaultProfiles.normal(id = 0L)

    private var lastEvaluationAt = 0L
    private var lastAlertedFingerprint: String? = null
    private var lastRecordedOfferId: Long? = null
    private var lastRecordedAt = 0L

    /** Forgets everything about the previous session. Called when the assistant starts. */
    fun reset() {
        lastEvaluationAt = 0L
        lastAlertedFingerprint = null
        lastRecordedOfferId = null
        lastRecordedAt = 0L
        state.clearLastEvaluation()
    }

    /**
     * @param overlayBounds where this app's own overlay is, so it can be cut out of the frame
     *   before anything is read (spec section 27).
     * @return what the overlay should show, or null to leave it as it is.
     */
    suspend fun analyse(text: OcrText, overlayBounds: Rect01?, now: Long): OverlayState? {
        val result = analyzer.analyze(
            text = text,
            profile = activeProfile,
            excludedRegions = listOfNotNull(overlayBounds)
        )
        state.onAnalysis(result.evaluation, result.diagnostics)

        val evaluation = result.evaluation
            ?: return handleNonOfferScreen(text, now)

        lastEvaluationAt = now
        recordAndAlert(evaluation, now)
        return OverlayState.from(evaluation)
    }

    /**
     * The screen is not an offer. Two things can be true: the offer was accepted (and the next
     * screen says so), or there is simply nothing to show yet.
     */
    private suspend fun handleNonOfferScreen(text: OcrText, now: Long): OverlayState? {
        if (OutcomeDetector.signal(text) == PostOfferSignal.TRIP_ACCEPTED) {
            val id = lastRecordedOfferId
            if (id != null && now - lastRecordedAt <= OUTCOME_WINDOW_MILLIS) {
                history.markOutcome(id, OfferOutcome.ACCEPTED)
                lastRecordedOfferId = null
            }
        }

        // Hold the last recommendation briefly: an offer card redraws, and a flicker back to
        // "waiting" between frames would be worse than useless while driving.
        if (now - lastEvaluationAt < RESULT_HOLD_MILLIS) return null

        lastAlertedFingerprint = null
        return OverlayState.waiting(activeProfile.name)
    }

    private suspend fun recordAndAlert(evaluation: OfferEvaluation, now: Long) {
        val metrics = evaluation.metrics
        val fingerprint = metrics?.let { OfferFingerprint.of(it) }

        // Alert once per offer. An UNKNOWN never alerts: there is nothing to tell the driver
        // beyond what the overlay already says.
        if (fingerprint != null &&
            fingerprint != lastAlertedFingerprint &&
            evaluation.recommendation != Recommendation.UNKNOWN
        ) {
            lastAlertedFingerprint = fingerprint
            feedback.alert(evaluation.recommendation, settings.current())
        }

        when (val record = history.record(evaluation, now)) {
            is RecordResult.Inserted -> {
                lastRecordedOfferId = record.id
                lastRecordedAt = now
            }

            is RecordResult.UpdatedExisting -> {
                lastRecordedOfferId = record.id
                lastRecordedAt = now
            }

            RecordResult.Skipped -> Unit
        }
    }

    private companion object {
        /** How long a result stays on screen after the offer card goes away. */
        const val RESULT_HOLD_MILLIS = 12_000L

        /** How long after an offer an "accepted" screen still counts as that offer's outcome. */
        const val OUTCOME_WINDOW_MILLIS = 90_000L
    }
}
