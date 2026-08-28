package uk.co.tripassistant.core.pipeline

import uk.co.tripassistant.core.format.Formats
import uk.co.tripassistant.core.model.OfferEvaluation
import uk.co.tripassistant.core.model.ParseNote
import uk.co.tripassistant.core.model.RawOffer
import uk.co.tripassistant.core.model.RuleProfile
import uk.co.tripassistant.core.model.UnreadableReason
import uk.co.tripassistant.core.parser.ParserRegistry
import uk.co.tripassistant.core.parser.ScreenClassification
import uk.co.tripassistant.core.parser.ScreenClassifier
import uk.co.tripassistant.core.parser.ScreenType
import uk.co.tripassistant.core.rules.RuleEngine
import uk.co.tripassistant.core.text.OcrText
import uk.co.tripassistant.core.text.Rect01
import uk.co.tripassistant.core.validation.OfferValidator
import uk.co.tripassistant.core.validation.ValidationOutcome

/** One row of the diagnostics screen (spec section 44). */
data class FieldReport(val label: String, val value: String?, val found: Boolean)

/** Everything the diagnostics and support screens need about one analysed frame. */
data class AnalysisDiagnostics(
    val screenType: ScreenType,
    val screenScore: Int,
    val signals: List<String>,
    val parserVersion: String?,
    val fields: List<FieldReport>,
    val notes: List<ParseNote>,
    val validationIssues: List<String>
)

/**
 * The result of looking at one frame.
 *
 * [evaluation] is null only when the screen was not an offer at all — in that case the assistant
 * stays quiet instead of flashing UNKNOWN at a driver who is simply looking at the map
 * (spec sections 21 and 49).
 */
data class AnalysisResult(
    val evaluation: OfferEvaluation?,
    val diagnostics: AnalysisDiagnostics
) {
    val isOfferScreen: Boolean get() = evaluation != null
}

/**
 * Observe -> Read -> Validate -> Calculate -> Evaluate, as one pure function (spec section 63).
 *
 * The Android side hands in recognised text and gets back the finished recommendation. Nothing in
 * this class touches a framework type, which is why the whole decision path can be exercised by
 * unit tests against recorded screen text (spec section 55).
 */
class OfferAnalyzer(
    private val registry: ParserRegistry = ParserRegistry()
) {

    fun analyze(
        text: OcrText,
        profile: RuleProfile,
        excludedRegions: List<Rect01> = emptyList()
    ): AnalysisResult {
        // Cut our own overlay out of the frame first, so the assistant can never read its own
        // figures back and score them as a fresh offer (spec section 27).
        val visible = text.excluding(excludedRegions)
        val classification = ScreenClassifier.classify(visible)

        if (classification.type == ScreenType.NOT_OFFER) {
            return AnalysisResult(
                evaluation = null,
                diagnostics = diagnostics(classification, null, null, emptyList())
            )
        }

        val candidate = registry.parse(visible)
            ?: return AnalysisResult(
                evaluation = RuleEngine.unknown(UnreadableReason.NOT_AN_OFFER_SCREEN, profile),
                diagnostics = diagnostics(classification, null, null, emptyList())
            )

        val raw = candidate.offer
        return when (val validation = OfferValidator.validate(raw)) {
            is ValidationOutcome.Invalid -> AnalysisResult(
                evaluation = RuleEngine.unknown(
                    reason = validation.reason,
                    profile = profile,
                    parserVersion = candidate.parserVersion,
                    notes = raw.notes
                ),
                diagnostics = diagnostics(classification, raw, candidate.parserVersion, validation.issues)
            )

            is ValidationOutcome.Valid -> AnalysisResult(
                evaluation = RuleEngine.evaluate(validation.offer, profile),
                diagnostics = diagnostics(classification, raw, candidate.parserVersion, emptyList())
            )
        }
    }

    private fun diagnostics(
        classification: ScreenClassification,
        raw: RawOffer?,
        parserVersion: String?,
        issues: List<String>
    ) = AnalysisDiagnostics(
        screenType = classification.type,
        screenScore = classification.score,
        signals = classification.signals,
        parserVersion = parserVersion,
        fields = fieldReports(raw),
        notes = raw?.notes.orEmpty(),
        validationIssues = issues
    )

    private fun fieldReports(raw: RawOffer?): List<FieldReport> = listOf(
        report("Fare", raw?.fareGbp) { Formats.money(it) },
        report("Pickup distance", raw?.pickupMiles) { Formats.miles(it) },
        report("Pickup time", raw?.pickupMinutes) { Formats.minutes(it) },
        report("Trip distance", raw?.tripMiles) { Formats.miles(it) },
        report("Trip time", raw?.tripMinutes) { Formats.minutes(it) },
        report("Rider rating", raw?.riderRating) { Formats.rating(it) }
    )

    private fun report(label: String, value: Double?, format: (Double) -> String) =
        FieldReport(label = label, value = value?.let(format), found = value != null)
}
