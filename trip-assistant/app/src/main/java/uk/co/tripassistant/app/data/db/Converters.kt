package uk.co.tripassistant.app.data.db

import androidx.room.TypeConverter
import uk.co.tripassistant.core.model.OfferConfidence
import uk.co.tripassistant.core.model.OfferOutcome
import uk.co.tripassistant.core.model.Recommendation
import uk.co.tripassistant.core.model.RuleImportance

/**
 * Enums are stored by name, not ordinal: reordering an enum must never silently reinterpret
 * existing history.
 */
class Converters {

    @TypeConverter fun recommendationToString(value: Recommendation): String = value.name

    @TypeConverter
    fun stringToRecommendation(value: String): Recommendation =
        runCatching { Recommendation.valueOf(value) }.getOrDefault(Recommendation.UNKNOWN)

    @TypeConverter fun outcomeToString(value: OfferOutcome): String = value.name

    @TypeConverter
    fun stringToOutcome(value: String): OfferOutcome =
        runCatching { OfferOutcome.valueOf(value) }.getOrDefault(OfferOutcome.UNKNOWN_OUTCOME)

    @TypeConverter fun confidenceToString(value: OfferConfidence): String = value.name

    @TypeConverter
    fun stringToConfidence(value: String): OfferConfidence =
        runCatching { OfferConfidence.valueOf(value) }.getOrDefault(OfferConfidence.LOW)

    @TypeConverter fun importanceToString(value: RuleImportance): String = value.name

    @TypeConverter
    fun stringToImportance(value: String): RuleImportance =
        runCatching { RuleImportance.valueOf(value) }.getOrDefault(RuleImportance.OFF)
}
